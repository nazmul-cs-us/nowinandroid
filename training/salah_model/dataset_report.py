"""
Dataset + training quality report.

Builds a single JSON artifact (output/dataset_report.json) describing what the
model was trained on and how well it did, so accuracy regressions and data
problems are visible without re-reading console logs. `export_tflite.py
--deploy` copies it into the app assets as last_training_report.json so the
app can display the last training run's quality.

Standalone usage (dataset stats only, no metrics):
    python dataset_report.py --data_dir ../data
"""

import argparse
import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

import numpy as np

from feature_engineering import POSTURE_LABELS, load_jsonl_files

# Files shorter than this many 100ms windows are too short to yield a single
# 2-second sequence and are flagged as outliers.
MIN_USEFUL_WINDOWS = 20

# A class with fewer sequences than this is flagged as under-collected.
LOW_SEQUENCE_THRESHOLD = 30


def dataset_stats(samples: list) -> dict:
    """Per-class and per-file window counts from raw loaded samples."""
    per_class = Counter(s["posture"] for s in samples)
    per_file: dict = defaultdict(lambda: {"windows": 0, "per_posture": Counter()})
    for s in samples:
        f = per_file[s.get("_source_file", "unknown")]
        f["windows"] += 1
        f["per_posture"][s["posture"]] += 1

    files = {
        name: {
            "windows": info["windows"],
            "duration_seconds": round(info["windows"] * 0.1, 1),
            "per_posture": dict(info["per_posture"]),
        }
        for name, info in sorted(per_file.items())
    }

    outlier_files = [
        name for name, info in files.items() if info["windows"] < MIN_USEFUL_WINDOWS
    ]

    counts = [per_class.get(label, 0) for label in POSTURE_LABELS]
    max_count, min_count = max(counts), min(counts)
    return {
        "total_windows": len(samples),
        "windows_per_class": {label: per_class.get(label, 0) for label in POSTURE_LABELS},
        "class_balance_ratio": round(min_count / max_count, 3) if max_count else 0.0,
        "files": files,
        "outlier_files": outlier_files,
    }


def _split_counts(y: np.ndarray) -> dict:
    counts = Counter(int(v) for v in y)
    return {POSTURE_LABELS[i]: counts.get(i, 0) for i in range(len(POSTURE_LABELS))}


def build_report(
    samples: list,
    y_train: np.ndarray,
    y_val: np.ndarray,
    y_test: np.ndarray,
    val_accuracy: float,
    test_accuracy: float,
    cm_val: np.ndarray,
    cm_test: np.ndarray,
    val_report: dict,
    test_report: dict,
    model_version: int,
) -> dict:
    """Assemble the full report. `*_report` are sklearn classification_report
    dicts (output_dict=True)."""
    stats = dataset_stats(samples)

    def per_class_f1(report: dict) -> dict:
        # sklearn omits classes with zero samples from output_dict — report those
        # as explicit 0.0 so a missing posture is visible, not silently absent.
        return {
            label: round(report.get(label, {}).get("f1-score", 0.0), 4)
            for label in POSTURE_LABELS
        }

    train_counts = _split_counts(y_train)
    low_sequence_classes = [
        label
        for label in POSTURE_LABELS
        if train_counts.get(label, 0) < LOW_SEQUENCE_THRESHOLD
    ]

    return {
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "model_version": model_version,
        "dataset": {
            **stats,
            "sequences": {
                "train": _split_counts(y_train),
                "val": _split_counts(y_val),
                "test": _split_counts(y_test),
            },
        },
        "metrics": {
            "val_accuracy": round(float(val_accuracy), 4),
            "test_accuracy": round(float(test_accuracy), 4),
            "per_class_f1_val": per_class_f1(val_report),
            "per_class_f1_test": per_class_f1(test_report),
            "confusion_matrix_val": cm_val.tolist(),
            "confusion_matrix_test": cm_test.tolist(),
            "confusion_labels": POSTURE_LABELS,
        },
        "quality_flags": {
            "class_balance_ratio": stats["class_balance_ratio"],
            "low_sequence_classes": low_sequence_classes,
            "outlier_files": stats["outlier_files"],
        },
    }


def write_report(report: dict, output_dir: Path) -> Path:
    path = Path(output_dir) / "dataset_report.json"
    with open(path, "w") as f:
        json.dump(report, f, indent=2)
    print(f"Dataset report saved to {path}")

    flags = report["quality_flags"]
    if flags["low_sequence_classes"]:
        print(f"  ⚠ Under-collected classes: {', '.join(flags['low_sequence_classes'])}")
    if flags["outlier_files"]:
        print(f"  ⚠ Outlier (too-short) files: {', '.join(flags['outlier_files'])}")
    print(f"  Class balance ratio (min/max windows): {flags['class_balance_ratio']}")
    return path


def main():
    parser = argparse.ArgumentParser(description="Dataset stats (no training metrics)")
    parser.add_argument("--data_dir", type=str, default="../data")
    parser.add_argument("--output_dir", type=str, default="../output")
    args = parser.parse_args()

    samples = load_jsonl_files(args.data_dir)
    stats = dataset_stats(samples)
    out = Path(args.output_dir)
    out.mkdir(parents=True, exist_ok=True)
    path = out / "dataset_report.json"
    with open(path, "w") as f:
        json.dump(
            {
                "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                "dataset": stats,
                "metrics": None,
            },
            f,
            indent=2,
        )
    print(f"Dataset-only report saved to {path}")


if __name__ == "__main__":
    main()
