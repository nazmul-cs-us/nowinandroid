# Salah ML Posture Detection System

> **Working the collect→train→deploy loop?** Use
> [`SALAH_TRAINING_PLAYBOOK.md`](SALAH_TRAINING_PLAYBOOK.md) — it has the
> verified step-by-step commands (some command examples below are outdated).
> This guide remains the architecture deep-dive.

Complete guide for the on-device machine learning system that detects Islamic prayer (salah) postures using phone sensors.

## System Overview

The system uses accelerometer and gyroscope data from a phone placed in the user's pocket to classify 7 prayer postures in real-time. The pipeline includes:

1. **Data Collection** - Record sensor data during prayer postures via the app
2. **Training Pipeline** - Python scripts to train a 1D CNN model
3. **On-Device Inference** - TFLite model running on Android at ~10 Hz
4. **Sequence Validation** - State machine to filter false positives and count rak'ahs

## Posture Classes

| Posture | Arabic | Description |
|---------|--------|-------------|
| QIYAM | قيام | Standing upright |
| RUKU | ركوع | Bowing (back ~90 degrees) |
| GOING_TO_SUJUD | هوي | Transitioning down to prostration |
| SUJUD | سجود | Prostration (face on ground) |
| JALSA | جلسة | Sitting between prostrations |
| TASHAHHUD | تشهد | Final sitting position |
| QIYAM_RISING | اعتدال | Rising back to standing (i'tidal after ruku) |


---

## Data Collection

### App Interface
Location: `app/src/main/kotlin/com/starception/submission/feature/salah/datacollection/`

- **SalahDataCollectionScreen.kt** - Material 3 UI with recording controls, posture selector, quality feedback, training progress
- **SalahDataCollectionViewModel.kt** - State management for recording sessions
- **SalahDataCollectionNavigation.kt** - Navigation integration

### Recording Flow
1. Select a posture from the grid
2. Tap "Start Recording" - 5-second countdown starts
3. Place phone in pocket and hold the posture
4. Tap "Stop Recording" when done
5. Last 3 seconds are auto-trimmed (removes phone-grab noise)

### Guided Recording Contract
- The countdown and spoken preparation are not written to the dataset.
- Static capture begins after the user has moved into position and settled.
- `QIYAM_RISING` and both `GOING_TO_SUJUD` movements use focused 4-second
  captures, starting on the spoken "move now" cue.
- Every label segment is at least 20 windows (2 seconds), so it can produce a
  model input sequence.
- One guided file contributes usable training sequences, but at least three
  complete guided sessions are required to give every class an independent
  train, validation, and test session.
- Pending `salah_live_*` files are skipped by the Python loader until the
  in-app review marks every row `human_reviewed` and renames them to
  `salah_reviewed_*`.
- New rows include `collection_mode` and `label_source`, preventing renamed
  model predictions from silently entering training as ground truth.
- Legacy rows without `collection_mode` fail closed. Open the file in the app
  and use **Confirm Review & Save** to migrate it, or leave it excluded.

### Sensor Service
Location: `app/src/main/kotlin/com/starception/submission/sensor/SalahDataCollectionService.kt`

- Records accelerometer + gyroscope at **50 Hz** (20ms between samples)
- Groups into **100ms windows** (5 samples per window)
- Exports to JSONL files in external storage
- Background sensor thread for efficiency

### Data Format (JSONL)
Each line in the JSONL file is a JSON object:
```json
{
  "timestamp": 1709123456789,
  "session_id": "salah_20240228_143025",
  "posture": "QIYAM",
  "accel_x": [0.12, 0.13, 0.11, 0.12, 0.14],
  "accel_y": [9.78, 9.79, 9.77, 9.80, 9.78],
  "accel_z": [0.45, 0.44, 0.46, 0.43, 0.45],
  "gyro_x": [0.001, 0.002, -0.001, 0.000, 0.001],
  "gyro_y": [0.003, 0.002, 0.003, 0.001, 0.002],
  "gyro_z": [-0.001, 0.000, -0.002, 0.001, 0.000],
  "pitch": -89.3,
  "roll": 87.3,
  "accel_magnitude": 9.81,
  "gyro_magnitude": 0.004
}
```

### Quality Feedback
During recording, the app provides real-time quality indicators:
- **Steadiness**: Based on gyroscope magnitude (< 0.3 = stable)
- **Orientation**: Posture-specific pitch/roll checks
- **Overall Quality**: GREAT / OK / BAD with color-coded display

### Training Progress
- Target: **500 samples per posture**
- Progress bars per posture with animated indicators
- Global statistics across all recording sessions

---

## Training Pipeline

Location: `training/salah_model/`

### Files
| File | Purpose |
|------|---------|
| `train_salah_detector.py` | Main training script |
| `feature_engineering.py` | Feature extraction from raw sensor windows |
| `data_augmentation.py` | Data augmentation strategies |
| `export_tflite.py` | Convert Keras model to TFLite |
| `dataset_report.py` | Dataset + training quality report (`output/dataset_report.json`, deployed to app assets as `last_training_report.json`) |
| `data/salah_training_data/*.jsonl` | Collected training data |

### Feature Engineering (`feature_engineering.py`)
Extracts **30 statistical features** from each 100ms sensor window. The exact
index layout below matches both `extract_window_features()` (Python) and
`SalahFeatureExtractor.kt` (on-device) — they must stay in lockstep:

| Index | Feature |
|-------|---------|
| 0–2   | accel_mean_x, accel_mean_y, accel_mean_z |
| 3–5   | accel_std_x, accel_std_y, accel_std_z |
| 6–7   | accel_mag_mean, accel_mag_var |
| 8–10  | gyro_mean_x, gyro_mean_y, gyro_mean_z |
| 11–13 | gyro_std_x, gyro_std_y, gyro_std_z |
| 14–15 | gyro_mag_mean, gyro_mag_var |
| 16–17 | pitch (precomputed), pitch_var |
| 18–19 | roll (precomputed), roll_var |
| 20–21 | accel_mag_min, accel_mag_max |
| 22–23 | gyro_mag_min, gyro_mag_max |
| 24–25 | pitch_range, roll_range (max − min across window) |
| 26–27 | accel_magnitude, gyro_magnitude (precomputed by Android) |
| 28–29 | accel_energy, gyro_energy (mean of sum of squares) |

> Note: features 24–25 are **ranges**, not angle values. The rotation
> augmentation must not overwrite them (this was a real bug, fixed in
> `data_augmentation.py` — ranges are invariant under a fixed rotation).

### Sequence Creation
- Groups consecutive windows into sequences of **20 windows** (2 seconds of data)
- Input tensor shape: `[batch, 20, 30]`
- Sequences grouped by session and posture for proper temporal ordering
- Whole sessions—not individual segments—are assigned to train, validation,
  or test, so one recording can never leak into multiple partitions
- For a population model, also collect multiple participants; session isolation
  alone does not make two recordings from the same person independent by person

### Normalization
- **Z-score standardization**: `(x - mean) / std` per feature
- Parameters saved to `norm_params.json` for inference

### Data Augmentation (`data_augmentation.py`)
5 augmentation strategies:
1. **Rotation** (simulates different pocket orientations, +/- 15 degrees)
2. **Time warping** (simulates different prayer speeds)
3. **Gaussian noise** (sensor noise variations)
4. **Magnitude scaling** (different phone weights/pocket depths)
5. **Jittering** (small random perturbations)

Class balancing via oversampling of minority classes.

### Model Architecture
1D CNN optimized for TFLite compatibility (no LSTM/RNN ops):

```
Input: [batch, 20, 30]
  -> Conv1D(32, kernel=3, ReLU) + BatchNorm + Dropout(0.3)
  -> Conv1D(64, kernel=3, ReLU) + BatchNorm + Dropout(0.3)
  -> Conv1D(128, kernel=3, ReLU) + BatchNorm + Dropout(0.3)
  -> GlobalAveragePooling1D
  -> Dense(64, ReLU) + Dropout(0.4)
  -> Dense(7, Softmax)
Output: [batch, 7]
```

### Training Configuration
```bash
python3 train_salah_detector.py \
  --data_dir ./data/salah_training_data/ \
  --output_dir ./output \
  --epochs 150 \
  --batch_size 16 \
  --lr 0.0005 \
  --stride 3 \
  --augment \
  --augment_factor 8
```

**Callbacks:**
- Early stopping (patience 10, monitor val_loss)
- Learning rate reduction (patience 5, factor 0.5)
- Model checkpointing (save best)

### Evaluation Safety
- Sequences are highly overlapping, so random sequence-level train/test splits can leak near-duplicate windows across partitions.
- The training pipeline splits by whole recording session before augmentation and normalization.
- Expect validation/test accuracy to drop after this fix; that lower number is more trustworthy and closer to production behavior.

### Training Results
Treat `output/dataset_report.json` as the result for each run; do not rely on
historical headline numbers. Deployment is blocked unless evaluation is
session-isolated, test accuracy is at least 80%, every class reaches at least
60% test F1, and every class has at least 10 held-out test sequences.

### TFLite Export (`export_tflite.py`)
```bash
python3 export_tflite.py \
  --model_path ./output/salah_detector.keras \
  --output_path ./output/salah_detector.tflite \
  --deploy_to ../../app/src/main/assets/
```

Options:
- Int8 quantization for smaller model size
- Automatic verification and benchmarking
- Auto-deployment to Android assets directory

---

## On-Device Inference

### ML Files
Location: `app/src/main/kotlin/com/starception/submission/ml/`

| File | Purpose |
|------|---------|
| `SalahPosture.kt` | Posture enum (9 values with display/Arabic names) |
| `SalahDataSample.kt` | Data class for sensor windows + JSON serialization |
| `SalahFeatureExtractor.kt` | Extracts 30 features matching Python pipeline |
| `SalahDetectionEngine.kt` | TFLite inference engine |
| `SalahSequenceValidator.kt` | State machine for posture validation |

### SalahFeatureExtractor
- Extracts identical 30 features as Python training pipeline
- Uses population std/variance (ddof=0) matching NumPy defaults
- Called on each 100ms sensor window

### SalahDetectionEngine
- Loads TFLite model from assets (`salah_detector.tflite`)
- Loads normalization parameters from `salah_norm_params.json`
- Maintains circular buffer of **20 sensor windows** (2 seconds)
- Z-score normalization using training statistics
- Minimum confidence threshold: **60%**
- Returns: predicted posture, confidence, all class probabilities
- Thread-safe with proper lifecycle management (close on dispose)

### SalahSequenceValidator
State machine that validates posture transitions to reduce false positives:

**States:** IDLE -> DETECTING -> CONFIRMED -> COMPLETED

**Valid Transitions (relaxed for ML uncertainty):**
- QIYAM -> RUKU, GOING_TO_SUJUD
- RUKU -> QIYAM_RISING, GOING_TO_SUJUD, SUJUD
- GOING_TO_SUJUD -> SUJUD, JALSA
- SUJUD -> JALSA, QIYAM_RISING
- JALSA -> SUJUD, GOING_TO_SUJUD, QIYAM_RISING, TASHAHHUD
- TASHAHHUD -> QIYAM_RISING, QIYAM
- QIYAM_RISING -> QIYAM, RUKU

**Parameters:**
- Minimum posture duration: **400ms** (2 stable readings)
- Minimum stable count: **2 consecutive detections**
- High-confidence override: **90%+** (bypasses transition rules)

**Features:**
- Rak'ah counting with milestone tracking
- Prayer state tracking (detecting, confirmed, completed)
- Real-time posture smoothing

### Deployed Model Files
```
app/src/main/assets/
  salah_detector.tflite      # 177 KB TFLite model
  salah_norm_params.json     # Normalization parameters (30 features)
```

---

## Debug Logging

```bash
# View ML detection logs
adb logcat -s "SalahDetection" "SalahSequenceValidator" -v time

# View data collection logs
adb logcat -s "SalahDataCollection" -v time

# View all salah-related logs
adb logcat -s "SalahDetection" "SalahSequenceValidator" "SalahDataCollection" "ActivityTracker" -v time
```

---

## Key Design Decisions

1. **1D CNN over LSTM/RNN**: TFLite has limited RNN support; Conv1D achieves comparable accuracy with better mobile performance
2. **100ms windows at 50Hz**: 5 samples per window provides enough temporal resolution without excessive compute
3. **2-second sequences**: 20 windows captures posture transitions while keeping latency low
4. **60% confidence threshold**: Balanced between responsiveness and false positive reduction
5. **Sequence validator**: State machine catches impossible transitions that the CNN alone might predict
6. **3-second auto-trim**: Removes phone-grab noise at end of recording for cleaner training data
7. **Phone-in-pocket design**: Uses gravity vector orientation (pitch/roll) as primary signal, which is stable in pocket
