"""
Feature engineering for salah posture detection.

Extracts 30 statistical features from each 100ms sensor window (5 samples of accel + gyro).
Groups consecutive windows into sequences for temporal modeling.

Input:  JSONL files from phone data collection
Output: NumPy arrays ready for model training
"""

import json
import numpy as np
import os
from pathlib import Path
from typing import List, Tuple, Dict, Optional


# Posture labels matching the Android enum
POSTURE_LABELS = ["QIYAM", "RUKU", "SUJUD", "JALSA", "TASHAHHUD"]
POSTURE_TO_INDEX = {p: i for i, p in enumerate(POSTURE_LABELS)}
NUM_CLASSES = len(POSTURE_LABELS)

# Feature extraction constants
SEQUENCE_LENGTH = 20  # 20 windows x 100ms = 2 seconds
FEATURES_PER_WINDOW = 30


def load_jsonl_files(data_dir: str) -> List[dict]:
    """Load all JSONL files from a directory into a list of sample dicts."""
    samples = []
    data_path = Path(data_dir)

    jsonl_files = sorted(data_path.glob("*.jsonl"))
    if not jsonl_files:
        print(f"No JSONL files found in {data_dir}")
        return samples

    for filepath in jsonl_files:
        print(f"Loading {filepath.name}...")
        line_count = 0
        with open(filepath, 'r') as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    sample = json.loads(line)
                    # Skip non-classification postures
                    posture = sample.get("posture", "")
                    if posture in POSTURE_TO_INDEX:
                        samples.append(sample)
                        line_count += 1
                except json.JSONDecodeError:
                    continue
        print(f"  Loaded {line_count} samples")

    print(f"\nTotal samples: {len(samples)}")
    return samples


def extract_window_features(sample: dict) -> np.ndarray:
    """
    Extract 30 features from a single 100ms sensor window.

    Features:
     0-2:   accel_mean_x, accel_mean_y, accel_mean_z
     3-5:   accel_std_x, accel_std_y, accel_std_z
     6-7:   accel_mag_mean, accel_mag_var
     8-10:  gyro_mean_x, gyro_mean_y, gyro_mean_z
     11-13: gyro_std_x, gyro_std_y, gyro_std_z
     14-15: gyro_mag_mean, gyro_mag_var
     16-17: pitch_mean, pitch_var (from precomputed pitch)
     18-19: roll_mean, roll_var (from precomputed roll)
     20-21: accel_min, accel_max (magnitude range)
     22-23: gyro_min, gyro_max (magnitude range)
     24:    pitch (precomputed by Android)
     25:    roll (precomputed by Android)
     26:    accel_magnitude (precomputed by Android)
     27:    gyro_magnitude (precomputed by Android)
     28:    accel_energy (sum of squares / N)
     29:    gyro_energy (sum of squares / N)
    """
    ax = np.array(sample["accel_x"], dtype=np.float32)
    ay = np.array(sample["accel_y"], dtype=np.float32)
    az = np.array(sample["accel_z"], dtype=np.float32)
    gx = np.array(sample["gyro_x"], dtype=np.float32)
    gy = np.array(sample["gyro_y"], dtype=np.float32)
    gz = np.array(sample["gyro_z"], dtype=np.float32)

    # Accelerometer statistics
    accel_mean = np.array([ax.mean(), ay.mean(), az.mean()])
    accel_std = np.array([ax.std(), ay.std(), az.std()])

    # Accelerometer magnitude per sample
    accel_mag = np.sqrt(ax**2 + ay**2 + az**2)
    accel_mag_mean = accel_mag.mean()
    accel_mag_var = accel_mag.var()

    # Gyroscope statistics
    gyro_mean = np.array([gx.mean(), gy.mean(), gz.mean()])
    gyro_std = np.array([gx.std(), gy.std(), gz.std()])

    # Gyroscope magnitude per sample
    gyro_mag = np.sqrt(gx**2 + gy**2 + gz**2)
    gyro_mag_mean = gyro_mag.mean()
    gyro_mag_var = gyro_mag.var()

    # Pitch and roll (use precomputed values, compute variance from raw)
    pitch = float(sample.get("pitch", 0))
    roll = float(sample.get("roll", 0))

    # Per-sample pitch/roll for variance
    pitches = np.degrees(np.arctan2(ay, az))
    rolls = np.degrees(np.arctan2(ax, az))
    pitch_var = pitches.var()
    roll_var = rolls.var()

    # Min/max ranges
    accel_min = accel_mag.min()
    accel_max = accel_mag.max()
    gyro_min = gyro_mag.min()
    gyro_max = gyro_mag.max()

    # Precomputed values from Android
    accel_magnitude = float(sample.get("accel_magnitude", accel_mag_mean))
    gyro_magnitude = float(sample.get("gyro_magnitude", gyro_mag_mean))

    # Energy (sum of squares / N)
    accel_energy = (ax**2 + ay**2 + az**2).mean()
    gyro_energy = (gx**2 + gy**2 + gz**2).mean()

    features = np.array([
        accel_mean[0], accel_mean[1], accel_mean[2],   # 0-2
        accel_std[0], accel_std[1], accel_std[2],       # 3-5
        accel_mag_mean, accel_mag_var,                   # 6-7
        gyro_mean[0], gyro_mean[1], gyro_mean[2],       # 8-10
        gyro_std[0], gyro_std[1], gyro_std[2],           # 11-13
        gyro_mag_mean, gyro_mag_var,                     # 14-15
        pitch, pitch_var,                                 # 16-17
        roll, roll_var,                                   # 18-19
        accel_min, accel_max,                             # 20-21
        gyro_min, gyro_max,                               # 22-23
        pitch, roll,                                      # 24-25
        accel_magnitude, gyro_magnitude,                  # 26-27
        accel_energy, gyro_energy,                        # 28-29
    ], dtype=np.float32)

    return features


def create_sequences(
    samples: List[dict],
    sequence_length: int = SEQUENCE_LENGTH,
    stride: int = 10
) -> Tuple[np.ndarray, np.ndarray]:
    """
    Group consecutive windows into fixed-length sequences for temporal modeling.

    Args:
        samples: List of JSONL sample dicts (sorted by timestamp within session)
        sequence_length: Number of windows per sequence (default 20 = 2 seconds)
        stride: Step size between sequences (default 10 = 1 second, 50% overlap)

    Returns:
        X: (N, sequence_length, FEATURES_PER_WINDOW) feature sequences
        y: (N,) integer labels
    """
    # Group samples by session
    sessions: Dict[str, List[dict]] = {}
    for s in samples:
        sid = s.get("session_id", "unknown")
        sessions.setdefault(sid, []).append(s)

    X_list = []
    y_list = []

    for session_id, session_samples in sessions.items():
        # Sort by timestamp within session
        session_samples.sort(key=lambda x: x["timestamp"])

        # Group consecutive samples with the same posture
        i = 0
        while i < len(session_samples):
            posture = session_samples[i]["posture"]
            if posture not in POSTURE_TO_INDEX:
                i += 1
                continue

            # Collect consecutive samples with same posture
            group = []
            while i < len(session_samples) and session_samples[i]["posture"] == posture:
                group.append(session_samples[i])
                i += 1

            # Create sequences from this posture group
            if len(group) >= sequence_length:
                for start in range(0, len(group) - sequence_length + 1, stride):
                    window = group[start:start + sequence_length]
                    features = np.array([extract_window_features(w) for w in window])
                    X_list.append(features)
                    y_list.append(POSTURE_TO_INDEX[posture])

    if not X_list:
        print("Warning: No sequences could be created. Need more data.")
        return np.array([]).reshape(0, sequence_length, FEATURES_PER_WINDOW), np.array([])

    X = np.array(X_list, dtype=np.float32)
    y = np.array(y_list, dtype=np.int32)

    print(f"\nCreated {len(X)} sequences of length {sequence_length}")
    print(f"Feature shape: {X.shape}")
    print(f"Label distribution:")
    for label, name in enumerate(POSTURE_LABELS):
        count = (y == label).sum()
        print(f"  {name}: {count} ({100*count/len(y):.1f}%)")

    return X, y


def extract_single_window_features(samples: List[dict]) -> Tuple[np.ndarray, np.ndarray]:
    """
    Extract features from individual windows (no sequencing).
    Useful for simple classifiers or initial exploration.

    Returns:
        X: (N, FEATURES_PER_WINDOW) features
        y: (N,) integer labels
    """
    X_list = []
    y_list = []

    for sample in samples:
        posture = sample.get("posture", "")
        if posture not in POSTURE_TO_INDEX:
            continue
        features = extract_window_features(sample)
        X_list.append(features)
        y_list.append(POSTURE_TO_INDEX[posture])

    X = np.array(X_list, dtype=np.float32)
    y = np.array(y_list, dtype=np.int32)

    print(f"\nExtracted {len(X)} single-window features")
    print(f"Feature shape: {X.shape}")
    print(f"Label distribution:")
    for label, name in enumerate(POSTURE_LABELS):
        count = (y == label).sum()
        pct = 100 * count / len(y) if len(y) > 0 else 0
        print(f"  {name}: {count} ({pct:.1f}%)")

    return X, y


def normalize_features(X_train: np.ndarray, X_val: np.ndarray) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    """
    Z-score normalize features using training set statistics.

    Returns:
        X_train_norm, X_val_norm, mean, std
    """
    original_shape = X_train.shape

    if X_train.ndim == 3:
        # Sequence data: (N, seq_len, features)
        flat = X_train.reshape(-1, X_train.shape[-1])
    else:
        flat = X_train

    mean = flat.mean(axis=0)
    std = flat.std(axis=0)
    std[std < 1e-7] = 1.0  # Avoid division by zero

    if X_train.ndim == 3:
        X_train_norm = (X_train - mean) / std
        X_val_norm = (X_val - mean) / std
    else:
        X_train_norm = (X_train - mean) / std
        X_val_norm = (X_val - mean) / std

    return X_train_norm, X_val_norm, mean, std


if __name__ == "__main__":
    import sys

    data_dir = sys.argv[1] if len(sys.argv) > 1 else "../data"

    print("=" * 60)
    print("Salah Posture Detection - Feature Engineering")
    print("=" * 60)

    # Load data
    samples = load_jsonl_files(data_dir)
    if not samples:
        print("No data found. Collect data using the app first.")
        sys.exit(1)

    # Extract single-window features
    X_single, y_single = extract_single_window_features(samples)

    # Create sequences
    X_seq, y_seq = create_sequences(samples)

    # Save processed data
    output_dir = Path(data_dir) / "processed"
    output_dir.mkdir(exist_ok=True)

    np.save(output_dir / "X_single.npy", X_single)
    np.save(output_dir / "y_single.npy", y_single)
    np.save(output_dir / "X_sequences.npy", X_seq)
    np.save(output_dir / "y_sequences.npy", y_seq)

    print(f"\nSaved processed data to {output_dir}")
    print(f"  X_single: {X_single.shape}")
    print(f"  X_sequences: {X_seq.shape}")
