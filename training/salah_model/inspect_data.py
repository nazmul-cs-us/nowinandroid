"""
Dataset inspector — the first tool to run when training data looks wrong.

Answers, before you spend time training:
  * How many windows does each posture class have (vs the 500 target)?
  * What does each file contribute, and how long is it?
  * Which files look broken (too short, unreviewed live recordings,
    suspicious label churn, sensor dropouts)?
  * Optionally: plot a file's sensor timeline so you can SEE the postures
    and check they match the labels.

Usage (from training/salah_model/):
    python inspect_data.py                                   # summary of data/salah_training_data
    python inspect_data.py --data_dir data/salah_training_data
    python inspect_data.py --file data/salah_training_data/salah_live_XXXX.jsonl --plot
    python inspect_data.py --plot                            # per-class feature distribution plots

No dependencies beyond the training requirements (matplotlib only if --plot).
"""

import argparse
import json
import math
from collections import Counter, defaultdict
from pathlib import Path

TARGET_WINDOWS_PER_CLASS = 500       # UI dataset target (100ms windows)
MIN_USEFUL_WINDOWS = 20              # below this a file can't yield one sequence
POSTURE_LABELS = [
    "QIYAM", "RUKU", "GOING_TO_SUJUD", "SUJUD", "JALSA", "TASHAHHUD", "QIYAM_RISING",
]


def load_file(path: Path):
    samples = []
    bad_lines = 0
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                samples.append(json.loads(line))
            except json.JSONDecodeError:
                bad_lines += 1
    return samples, bad_lines


def label_runs(samples):
    """Consecutive same-label segments: [(label, n_windows), ...]."""
    runs = []
    for s in samples:
        p = s.get("posture", "?")
        if runs and runs[-1][0] == p:
            runs[-1][1] += 1
        else:
            runs.append([p, 1])
    return runs


def analyze_file(path: Path):
    samples, bad_lines = load_file(path)
    n = len(samples)
    counts = Counter(s.get("posture", "?") for s in samples)
    runs = label_runs(samples)
    manually = sum(1 for s in samples if s.get("manually_labeled"))

    duration_s = 0.0
    gaps = 0
    if n >= 2:
        ts = [s["timestamp"] for s in samples]
        duration_s = (ts[-1] - ts[0]) / 1000.0
        # windows are 100ms; a gap of >400ms means dropped sensor data
        gaps = sum(1 for a, b in zip(ts, ts[1:]) if b - a > 400)

    warnings = []
    if n < MIN_USEFUL_WINDOWS:
        warnings.append(f"TOO SHORT ({n} windows < {MIN_USEFUL_WINDOWS}) — outlier, won't yield a full sequence")
    if bad_lines:
        warnings.append(f"{bad_lines} unparseable lines")
    if path.name.startswith("salah_live_") and len(counts) == 1 and "QIYAM" in counts and not manually:
        warnings.append("UNREVIEWED LIVE RECORDING — every label is the QIYAM placeholder; review & label it in the app or delete it")
    short_runs = [r for r in runs if r[1] < 5 and n > 50]
    if len(short_runs) > len(runs) / 2 and len(runs) > 4:
        warnings.append(f"LABEL CHURN — {len(short_runs)}/{len(runs)} segments shorter than 0.5s; labels may be corrupted")
    if gaps:
        warnings.append(f"{gaps} sensor gaps >400ms (recording was interrupted?)")

    return {
        "name": path.name,
        "windows": n,
        "duration_s": duration_s,
        "counts": counts,
        "segments": len(runs),
        "manually_labeled": manually,
        "warnings": warnings,
    }


def print_report(data_dir: Path):
    files = sorted(data_dir.glob("*.jsonl"))
    if not files:
        print(f"No .jsonl files in {data_dir}")
        return []

    reports = [analyze_file(p) for p in files]

    print(f"\n{'='*78}\nDATASET: {data_dir}  ({len(files)} files)\n{'='*78}")

    # Per-file table
    print(f"\n{'file':<44} {'windows':>7} {'dur(s)':>7} {'segs':>5}  labels")
    print("-" * 96)
    for r in reports:
        labels = ",".join(f"{k}:{v}" for k, v in sorted(r["counts"].items()))
        print(f"{r['name']:<44} {r['windows']:>7} {r['duration_s']:>7.0f} {r['segments']:>5}  {labels}")
        for w in r["warnings"]:
            print(f"    ⚠️  {w}")

    # Class totals vs target
    totals = Counter()
    for r in reports:
        totals.update(r["counts"])
    print(f"\n{'-'*40}\nCLASS TOTALS (target {TARGET_WINDOWS_PER_CLASS} windows each)\n{'-'*40}")
    for label in POSTURE_LABELS:
        c = totals.get(label, 0)
        bar = "█" * int(30 * min(c, TARGET_WINDOWS_PER_CLASS) / TARGET_WINDOWS_PER_CLASS)
        flag = " ✓" if c >= TARGET_WINDOWS_PER_CLASS else (" ← COLLECT MORE" if c < TARGET_WINDOWS_PER_CLASS * 0.3 else "")
        print(f"{label:<16} {c:>6}  {bar:<30}{flag}")
    unknown = {k: v for k, v in totals.items() if k not in POSTURE_LABELS}
    if unknown:
        print(f"⚠️  Unknown labels present (will be skipped in training): {unknown}")

    known = [totals.get(l, 0) for l in POSTURE_LABELS if totals.get(l, 0) > 0]
    if len(known) >= 2:
        ratio = max(known) / max(1, min(known))
        print(f"\nClass balance ratio (max/min): {ratio:.1f}" + ("  ⚠️ >3 — collect the smallest class first" if ratio > 3 else "  ✓"))
    missing = [l for l in POSTURE_LABELS if totals.get(l, 0) == 0]
    if missing:
        print(f"⚠️  Classes with ZERO data: {', '.join(missing)}")
    return reports


def plot_file(path: Path):
    import matplotlib.pyplot as plt

    samples, _ = load_file(path)
    if not samples:
        print("empty file")
        return
    t = [(s["timestamp"] - samples[0]["timestamp"]) / 1000.0 for s in samples]
    accel_mag = [s["accel_magnitude"] for s in samples]
    gyro_mag = [s["gyro_magnitude"] for s in samples]
    pitch = [s["pitch"] for s in samples]

    fig, axes = plt.subplots(3, 1, figsize=(14, 8), sharex=True)
    axes[0].plot(t, accel_mag, lw=0.7)
    axes[0].set_ylabel("accel magnitude (m/s²)")
    axes[1].plot(t, gyro_mag, lw=0.7, color="tab:orange")
    axes[1].set_ylabel("gyro magnitude (rad/s)")
    axes[2].plot(t, pitch, lw=0.7, color="tab:green")
    axes[2].set_ylabel("pitch (°)")
    axes[2].set_xlabel("time (s)")

    # Shade label segments so you can eyeball label-vs-motion alignment
    colors = {l: c for l, c in zip(POSTURE_LABELS, plt.cm.tab10.colors)}
    start_i = 0
    for i in range(1, len(samples) + 1):
        if i == len(samples) or samples[i]["posture"] != samples[start_i]["posture"]:
            label = samples[start_i]["posture"]
            for ax in axes:
                ax.axvspan(t[start_i], t[min(i, len(t) - 1)], alpha=0.12,
                           color=colors.get(label, "gray"))
            axes[0].text((t[start_i] + t[min(i, len(t) - 1)]) / 2, max(accel_mag) * 0.95,
                         label, fontsize=6, ha="center", rotation=90)
            start_i = i
    fig.suptitle(f"{path.name} — shaded by label; motion spikes should sit at segment BOUNDARIES,\n"
                 f"flat regions inside segments. A spike mid-segment usually means a wrong label boundary.")
    plt.tight_layout()
    out = path.with_suffix(".png")
    plt.savefig(out, dpi=130)
    print(f"wrote {out}")


def plot_class_distributions(data_dir: Path):
    import matplotlib.pyplot as plt

    per_class_pitch = defaultdict(list)
    per_class_amag = defaultdict(list)
    for p in sorted(data_dir.glob("*.jsonl")):
        samples, _ = load_file(p)
        for s in samples:
            if s.get("posture") in POSTURE_LABELS:
                per_class_pitch[s["posture"]].append(s["pitch"])
                per_class_amag[s["posture"]].append(s["accel_magnitude"])

    fig, axes = plt.subplots(1, 2, figsize=(14, 5))
    present = [l for l in POSTURE_LABELS if per_class_pitch[l]]
    axes[0].boxplot([per_class_pitch[l] for l in present], labels=present)
    axes[0].set_title("pitch (°) by class — static postures should form distinct, tight boxes;\nheavy overlap = the model can't tell them apart from orientation alone")
    axes[0].tick_params(axis="x", rotation=45)
    axes[1].boxplot([per_class_amag[l] for l in present], labels=present)
    axes[1].set_title("accel magnitude by class — transitions should be wider/higher than statics")
    axes[1].tick_params(axis="x", rotation=45)
    plt.tight_layout()
    out = data_dir / "class_distributions.png"
    plt.savefig(out, dpi=130)
    print(f"wrote {out}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data_dir", default="data/salah_training_data",
                    help="directory containing the .jsonl files")
    ap.add_argument("--file", default=None, help="inspect/plot a single file")
    ap.add_argument("--plot", action="store_true",
                    help="write PNG plots (file timeline with --file, else class distributions)")
    args = ap.parse_args()

    if args.file:
        p = Path(args.file)
        r = analyze_file(p)
        print(json.dumps({k: (dict(v) if isinstance(v, Counter) else v) for k, v in r.items()}, indent=2))
        if args.plot:
            plot_file(p)
    else:
        d = Path(args.data_dir)
        print_report(d)
        if args.plot:
            plot_class_distributions(d)
