# Salah Training Playbook — Collect → Train → Deploy → Repeat

This is the complete, self-contained manual for improving the salah posture
detection model yourself, offline. It covers the exact steps, commands, file
paths, and quality checks for every stage of the loop:

```
┌────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ 1. COLLECT │ →  │ 2. PULL DATA │ →  │ 3. TRAIN     │ →  │ 4. DEPLOY    │
│  (on phone)│    │  (adb pull)  │    │  (Python)    │    │  (--deploy)  │
└────────────┘    └──────────────┘    └──────────────┘    └──────┬───────┘
      ↑                                                          │
      └────────────── 5. VERIFY IN APP & REPEAT ←────────────────┘
```

Companion docs:

- `docs/SALAH_ML_LEARNING_GUIDE.md` — the classroom: every step explained
  from first principles, how to diagnose bad training data, how to validate
  test results, plus a hands-on experiments ladder.
- `docs/SALAH_ML_TRAINING_GUIDE.md` — architecture deep-dive.

Where docs disagree on commands, THIS playbook is correct — it was verified
against the scripts' actual argument parsers.

---

## 0. One-time setup

### Python environment

```bash
cd /Users/smarterai/Documents/GitHub/nowinandroid
python3 -m venv .venv-salah          # once
source .venv-salah/bin/activate      # every session
pip install -r training/requirements.txt
```

`training/requirements.txt` pins: `tensorflow>=2.14,<2.17`, `numpy>=1.24,<2.0`,
`scikit-learn>=1.3`, `pandas>=2.0`, `matplotlib`, `jupyter`.

### Guided-mode voice (optional, recommended)

Guided Recording speaks instructions through the on-device Kokoro TTS engine.
If the button says "Voice engine required", tap **Download TTS Engine
(~175 MB)** once (18 MB espeak + 158 MB kokoro). Manual and Live modes work
without it.

### Device

All examples use the Pixel 9 Pro (`adb -s 4B221FDAP002T6`). The app variant
determines the on-device data path — for `demoDebug` it is:

```
/storage/emulated/0/Android/data/com.starception.submission.demo.debug/files/salah_training_data/
```

---

## 1. The eight postures and how much data you need

The model classifies 8 postures. The JSONL label strings (and the model's
output order) are:

| # | Label            | UI name       | What you do                              |
|---|------------------|---------------|------------------------------------------|
| 0 | `QIYAM`          | Standing      | Standing recitation                       |
| 1 | `RUKU`           | Bowing        | Bowing, hands on knees                    |
| 2 | `GOING_TO_SUJUD` | Going Down    | The transition down to prostration        |
| 3 | `SUJUD`          | Prostration   | Forehead on ground                        |
| 4 | `JALSA`          | Sitting       | Sitting between the two sujud             |
| 5 | `TASHAHHUD`      | Final Sitting | The longer seated tashahhud               |
| 6 | `QIYAM_RISING`   | Rising Up     | The transition up to standing             |
| 7 | `RISING_TO_QIYAM`| Rise to Next Rak‘ah | Rising after sujud/tashahhud to continue |

**Targets and thresholds baked into the tooling:**

- **500 windows per posture** — the UI's dataset target (progress card and
  per-posture bars). One window = 100 ms, so 500 windows ≈ 50 s of clean
  in-posture data. Expect to need several sessions per posture.
- **< 30 sequences in a class** → flagged `low_sequence_classes` in the
  training report (under-collected).
- **< 20 windows in a file** → flagged as an `outlier_file` (too short to
  produce even one full sequence).
- Transitions (`GOING_TO_SUJUD`, `QIYAM_RISING`, `RISING_TO_QIYAM`) are the hardest classes —
  they are brief by nature, so they always need the most recordings.

The **"Record next"** card at the top of the Salah Data Collection screen
always points at the thinnest class — trust it.

---

## 2. Collecting data on the phone

Open **Settings → Salah Training → Start Data Collection**. Three modes,
each with a different trade-off:

### Mode A — Manual recording (one posture at a time)

Best for: bulk-filling one specific thin class.

1. Tap the posture in **Select Posture** (or tap the "Record next" card).
   Each posture tile shows its own progress bar and count toward the
   500-window target — a live "+N" while that posture is recording — and a
   check badge once the class is complete, so the grid doubles as your
   dataset-balance dashboard.
2. Tap **Start Recording** → 5-second countdown → put the phone in your
   pocket before it hits zero.
3. Hold the posture (or repeat the transition slowly for transition classes).
4. Take the phone out and tap **Stop Recording**.
5. The last **3 seconds are auto-trimmed** (removes the phone-grab noise).

Watch the **capture quality card** while recording: GREAT/OK/BAD reflects
steadiness + orientation sanity for the selected posture. If it shows BAD,
the phone is loose in the pocket or the posture doesn't match the label.

Files: `salah_data_{yyyyMMdd_HHmmss}_{8charId}.jsonl`

### Mode B — Guided recording (TTS walks you through a full rak'ah)

Best for: balanced data across all 8 classes in one go; no phone handling
mid-session.

1. Choose the hold duration per posture (10/15/20/30 s — 15 s default).
2. Tap **Start Guided Recording**, put the phone in your pocket.
3. TTS announces each posture; the sequence is a full rak'ah:
   `QIYAM → RUKU → QIYAM_RISING → GOING_TO_SUJUD → SUJUD → JALSA → GOING_TO_SUJUD → SUJUD → TASHAHHUD → RISING_TO_QIYAM`.
   Static postures use your chosen duration. The three transition classes use a
   focused 5 s capture; follow the "move now" cue and move smoothly for the
   full interval.
4. "Recording complete. You can take your phone out now." — **no trim is
   applied** (capture is paused before the completion message).

Labels are written live by the guide, so no relabeling is needed. Sensor
capture is paused during the initial countdown, spoken preparation, and
unmodelled posture boundaries; those movements cannot leak into a static
posture label.

Guided files are named `salah_guided_{yyyyMMdd_HHmmss}_{8charId}.jsonl`.
One complete guided recording contributes trainable 2-second sequences for
all eight classes. Record **at least 3 complete guided sessions** before
training so every class has an independent session for the train, validation,
and test splits. You still want 500 clean windows per class for a useful model.

### Mode C — Live prayer recording (most realistic data)

Best for: real transition dynamics and honest timing — the data the model
will actually face. **Requires a review pass afterwards.**

1. Tap **Record Live Prayer** → read the instruction card → **Start Prayer
   Recording** → pocket the phone → pray normally.
2. The screen shows live ML detection (posture, confidence, rak'ah count)
   while you pray. These predictions become provisional labels only.
3. Take the phone out, tap **Stop Recording** (auto-stops after 30 min).
   Last 3 s auto-trimmed.
4. You MUST tap **Review & Label** and correct the provisional segments.
   Pending `salah_live_*` files are excluded by the trainer; **Confirm Review
   & Save** marks all rows `human_reviewed` and renames the file to
   `salah_reviewed_*`. The trainer verifies both markers. Discard it if the
   recording went wrong.

Files: `salah_live_{yyyyMMdd_HHmmss}_{8charId}.jsonl`

### The review screen (for live recordings)

- The timeline shows colored segments (consecutive same-label windows). Tap
  a segment → pick the correct posture chip. Edited segments are saved with
  `"manually_labeled": true` and the `original_posture` retained.
- **Confirm Review & Save** marks every row `human_reviewed`, records label
  provenance, and can safely migrate legacy files that predate schema v2.
- **Analyze data quality** runs the deployed model over the file and shows
  model-vs-label agreement. Segments where the model disagrees confidently
  (≥ 3 consecutive windows at ≥ 0.5 confidence) get a red `!` marker — tap
  them, they usually mean YOUR LABEL is wrong there, not the model.
- Agreement color code: green ≥ 85 %, amber ≥ 70 %, red < 70 %.
- **Save Labels** rewrites the same JSONL in place. **Discard** deletes it.

### In-app dataset health checks (before pulling)

- **Recorded Files** list: each file shows per-posture window counts and an
  on-demand quality badge (agreement % from batch inference).
- **Per-posture progress**: bars embedded in every Select Posture tile
  (live during recording) plus the summary **Training Progress** card
  further down — both against the 500-window target.
- **3D Visualization**: scatter/humanoid/PCA views of all samples;
  "analyze predictions" highlights model disagreements across the dataset.
- Swipe a file row to delete a bad recording. Deleting from the phone is the
  only dataset curation step — do it before pulling.

---

## 3. Pulling data to the computer

```bash
cd /Users/smarterai/Documents/GitHub/nowinandroid

adb -s 4B221FDAP002T6 pull \
  /storage/emulated/0/Android/data/com.starception.submission.demo.debug/files/salah_training_data/. \
  training/salah_model/data/salah_training_data/
```

Notes:

- The trailing `/.` copies the *contents* into the target directory.
- Files accumulate — pulls are additive, and re-pulling an unchanged file is
  harmless (same name, same content). Files edited on-device by the review
  screen have new content under the same name and will overwrite correctly.
- To see what's on the device first:
  `adb -s 4B221FDAP002T6 shell ls -la /storage/emulated/0/Android/data/com.starception.submission.demo.debug/files/salah_training_data/`
- If you ever record on a `prodDebug` build, swap the package segment to
  `com.starception.submission.prod.debug`.

**Dataset health check — run this after every pull:**

```bash
cd training/salah_model
python3 inspect_data.py                # per-file table, class totals vs 500, warnings
python3 inspect_data.py --plot         # per-class distribution boxplots (PNG)
python3 inspect_data.py --file data/salah_training_data/<file>.jsonl --plot   # one file's timeline
```

(`python3` works without any venv for the summary; `--plot` needs
matplotlib from the training venv.)

It flags unreviewed live files (all-QIYAM), too-short outliers, label churn,
and sensor gaps — fix or delete those BEFORE training.

**JSONL sanity check** (each line = one 100 ms window; quote-wrapped `ls`
picks a single file — a bare glob would make `head` print `==>` headers that
break `json.tool`):

```bash
head -1 "$(ls training/salah_model/data/salah_training_data/*.jsonl | head -1)" | python3 -m json.tool
```

Expected keys: `timestamp`, `session_id`, `posture`, `accel_x/y/z` (5-float
arrays), `gyro_x/y/z` (5-float arrays), `pitch`, `roll`, `accel_magnitude`,
`gyro_magnitude` — plus `manually_labeled`/`original_posture` on reviewed
lines.

---

## 4. Training

All commands run from the pipeline directory:

```bash
cd training/salah_model
source ../../.venv-salah/bin/activate   # if not already active
```

### ⚠️ The one command-line trap

`--data_dir` must point at the directory that **directly contains the
`.jsonl` files**. The script globs `*.jsonl` in that exact directory — it
does NOT descend into subdirectories, and its documented default (`../data`)
does not match this repo's layout. Always pass it explicitly:

```bash
python train_salah_detector.py --data_dir data/salah_training_data --output_dir output
```

### Arguments (actual argparse definitions)

| Flag               | Default | Meaning                                                  |
|--------------------|---------|----------------------------------------------------------|
| `--data_dir`       | `../data` (wrong for this repo — always pass `data/salah_training_data`) | Directory containing the `.jsonl` files |
| `--output_dir`     | `../output` (pass `output`) | Where models + reports are written       |
| `--epochs`         | 100     | Max epochs (early stopping usually ends it ~20-40)       |
| `--batch_size`     | 32      | Batch size                                               |
| `--lr`             | 0.001   | Adam initial learning rate                               |
| `--seq_length`     | 20      | Windows per sequence (2 s) — matches the app; don't change |
| `--stride`         | 10      | Sequence stride (10 = 50 % overlap). Lower = more sequences from the same data (e.g. `--stride 3` when data is scarce) |
| `--augment`        | on      | Rotation/time-warp/noise/scaling augmentation             |
| `--no-augment`     | —       | Disable augmentation                                     |
| `--augment_factor` | 4       | Augmented copies per original sequence                   |

### What the script does (so you can trust the numbers)

1. Loads every JSONL window, drops non-classification labels.
2. Builds 20-window sequences grouped by contiguous posture segment, then
   splits train/val/test (approximately 70/15/15) **by whole recording
   session**. No posture, placement, or overlapping sequence from one session
   can appear in another partition.
3. Balances classes (oversamples minorities), augments (train split only),
   and Z-score normalizes using **train-set statistics only**.
4. Trains the 1D CNN (Conv1D 32→64→128 + GAP + Dense64 + softmax-7) with
   EarlyStopping (patience 15 on val_accuracy), ReduceLROnPlateau, and a
   best-checkpoint callback.
5. Evaluates on val and test, writes everything to `output/`.

### Outputs (in `training/salah_model/output/`)

| File                    | What it is                                                       |
|-------------------------|------------------------------------------------------------------|
| `salah_detector.keras`  | Final trained model                                              |
| `best_model.keras`      | Best-val-accuracy checkpoint (usually identical)                 |
| `norm_params.json`      | Mean/std (30 each) + labels + shape + `model_version` — the app needs this to preprocess identically |
| `training_history.json` | Per-epoch loss/accuracy curves                                   |
| `dataset_report.json`   | The full quality report (next section)                           |

### Reading `dataset_report.json`

- `dataset.windows_per_class` — your real (pre-balancing) class counts.
- `dataset.class_balance_ratio` — max/min class ratio; > 3 means collect
  more of the smallest class rather than train again.
- `quality_flags.low_sequence_classes` — classes with < 30 sequences.
- `quality_flags.outlier_files` — files with < 20 windows (delete or ignore).
- `metrics.val_accuracy` / `metrics.test_accuracy` — headline numbers.
  **Trust `test_accuracy`** — val is used for early stopping, test is never
  touched during training. Healthy runs land 90-97 %.
- `metrics.per_class_f1_test` — the two lowest classes here are literally
  what the app's "Deployed model" card will tell you to collect next.
- `metrics.confusion_matrix_test` — rows/cols in the label order from
  section 1; off-diagonal hotspots tell you which pairs confuse the model
  (classically `JALSA` ↔ `TASHAHHUD` and the movement classes).

---

## 5. Export and deploy to the app

```bash
python export_tflite.py --model_dir output --deploy
```

Deployment fails closed unless the report uses session-isolated splits, test
accuracy is ≥80%, every posture has test F1 ≥60%, and every posture has at
least 10 test sequences. `--force-deploy` exists only for explicitly
experimental builds.

Optionally add int8 quantization (smaller/faster; run the feature step first
so it has a representative dataset for calibration — and note `--data_dir`
must be passed to BOTH commands with the same value, because export reads
`{data_dir}/processed/X_sequences.npy` and its default `../data` does not
match this repo's layout):

```bash
python feature_engineering.py data/salah_training_data   # writes data/salah_training_data/processed/
python export_tflite.py --model_dir output --quantize --data_dir data/salah_training_data --deploy
```

`--deploy` copies three files into `app/src/main/assets/`:

| Asset                       | Source                       | Used by                                  |
|-----------------------------|------------------------------|-------------------------------------------|
| `salah_detector.tflite`     | converted model              | `SalahDetectionEngine` (inference)         |
| `salah_norm_params.json`    | `output/norm_params.json`    | `SalahDetectionEngine` (preprocessing)     |
| `last_training_report.json` | `output/dataset_report.json` | "Deployed model" card (version, accuracy, weakest classes) |

The script also verifies the converted model (input `[1, 20, 30]` float32,
output `[1, 7]`) and benchmarks it (expect ~1 ms mean on desktop; the phone
runs 2 CPU threads).

Then rebuild and install:

```bash
cd /Users/smarterai/Documents/GitHub/nowinandroid
./gradlew :app:assembleDemoDebug
adb -s 4B221FDAP002T6 install -r app/build/outputs/apk/demo/debug/app-demo-debug.apk
```

---

## 6. Verify the new model in the app

1. **Deployed model card** (top of Salah Data Collection): shows
   `v{model_version}`, test %, val %, and the two weakest classes — confirm
   the numbers match your `dataset_report.json`.
2. **Re-analyze a known file**: open a good recording's quality badge — the
   agreement % should be as high or higher than with the old model.
   (Quality results are cached per file+mtime, so previously analyzed files
   need a fresh tap on Analyze.)
3. **Live smoke test**: Record Live Prayer → pray one rak'ah → the live
   detection panel should track postures and count the rak'ah. The sequence
   validator requires: standing seen + (ruku or sujud or tashahhud) to
   confirm a prayer; rak'ah increments on rising after sujud.
4. If something regressed, the old model is one `git checkout -- app/src/main/assets/` away.

---

## 7. The iteration loop (make it perfect)

Each cycle, in order:

1. **Look at the weakest classes** (Deployed model card, or
   `per_class_f1_test`). Collect 3-5 new sessions of ONLY those postures
   (guided mode with a longer hold, or manual mode targeting the class).
2. **Prefer live recordings once statics are strong** — transitions only get
   better with real prayer dynamics, and the review screen's flagged
   segments make labeling fast.
3. **Fix labels before adding data.** If a file's agreement is red (< 70 %),
   open it in review (live files) or delete it (manual files with wrong
   posture selected). One badly labeled file hurts more than five good ones
   help.
4. **Keep balance.** If `class_balance_ratio` > 3, collect the floor, not
   the ceiling.
5. Retrain → deploy → verify. With `--stride 3` you can squeeze more
   sequences out of scarce data in early cycles; return to `--stride 10`
   once classes have 500+ windows.
6. When you change the feature extractor or window semantics (not just
   data), bump `MODEL_VERSION` in `SalahDetectionEngine.kt` AND the
   `model_version` written in `train_salah_detector.py` so mismatches are
   detectable.

---

## 8. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `No JSONL files found in …` | `--data_dir` must directly contain the `.jsonl` files: use `data/salah_training_data` |
| Quantize step says no representative dataset | Run `python feature_engineering.py data/salah_training_data` first (creates `processed/X_sequences.npy`) |
| Val accuracy 99 %+ but real-world detection poor | Not enough variety across people, pockets, or devices; collect more independent sessions and users |
| One class F1 near 0 | Check `windows_per_class` — likely < 30 sequences; collect more, or its recordings are mislabeled |
| App shows old model numbers after deploy | Assets are baked at build time — rebuild + reinstall the APK after `--deploy` |
| Guided recording button disabled | TTS engine not downloaded (Settings card shows the download button) |
| Live file 100 % QIYAM | You skipped Review & Label — open the file's review or delete it |
| `adb pull` permission denied / empty | Wrong package in path (demo vs prod build), or no recordings yet |
| TensorFlow install fails | Python 3.9-3.11 required for TF 2.14-2.16 on macOS; make sure the venv, not system Python, is active |

---

## 9. Reference

### On-device data location (demoDebug)

```
/storage/emulated/0/Android/data/com.starception.submission.demo.debug/files/salah_training_data/
├── salah_data_20260312_234318_56b7cf1a.jsonl     ← manual recording
├── salah_guided_20260724_101502_9f21ab7e.jsonl   ← guided recording
├── salah_reviewed_20260724_111502_0123abcd.jsonl ← reviewed live recording
└── salah_live_20260724_121502_4567efab.jsonl     ← pending live review
```

### Repo layout

```
training/
├── requirements.txt
└── salah_model/
    ├── feature_engineering.py    # 30-feature extraction + sequence building (also standalone)
    ├── data_augmentation.py      # rotation/time-warp/noise/scaling (imported by train)
    ├── train_salah_detector.py   # THE training entry point
    ├── export_tflite.py          # convert + verify + benchmark + --deploy
    ├── dataset_report.py         # report builder (also standalone: --data_dir/--output_dir)
    ├── data/salah_training_data/ # ← put/pull JSONL files here
    └── output/                   # ← models + reports land here
app/src/main/assets/
    ├── salah_detector.tflite
    ├── salah_norm_params.json
    └── last_training_report.json
```

### Recording pipeline (on device)

- 50 Hz accelerometer + gyroscope (20,000 µs sensor delay), synchronized to
  within 10 ms; 5 samples per 100 ms window.
- One JSONL line per window with the label current at write time.
- Manual: 5 s countdown, 3 s end-trim. Guided: no trim. Live: 3 s end-trim,
  30 min auto-stop, placeholder `QIYAM` labels until review.

### Inference (on device)

- Input: rolling buffer of 20 windows × 30 features, Z-scored with the
  deployed `salah_norm_params.json`; partial inference starts at 10 windows
  with confidence scaled by buffer fill.
- EMA smoothing α = 0.6 over class probabilities; per-class acceptance
  thresholds 0.35-0.40.
- `SalahSequenceValidator` gates transitions to phys­ically possible ones
  (e.g. `QIYAM → RUKU`, not `QIYAM → JALSA`), requires 200 ms stability, and
  counts rak'ahs (rising after ≥1 sujud, or entering tashahhud after sujud).

### The 30 features per window (order matters — Python and Kotlin must match)

```
 0-2  accel_mean_x/y/z         16-17 pitch_mean / pitch_var
 3-5  accel_std_x/y/z          18-19 roll_mean / roll_var
 6-7  accel_mag_mean / var     20-21 accel_mag_min / max
 8-10 gyro_mean_x/y/z          22-23 gyro_mag_min / max
11-13 gyro_std_x/y/z           24-25 pitch_range / roll_range
14-15 gyro_mag_mean / var      26-27 accel_magnitude / gyro_magnitude
                               28-29 accel_energy / gyro_energy
```

### Model

1D CNN (TFLite-friendly, no RNN): `Input(20,30) → [Conv1D(32,k3)+BN+Drop .3]
→ [Conv1D(64,k3)+BN+Drop .3] → [Conv1D(128,k3)+BN+Drop .3] → GAP →
Dense(64) → Drop .4 → Dense(8, softmax)`.
