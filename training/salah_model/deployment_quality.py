"""Fail-closed quality gates for deploying a Salah posture model."""

import json
from pathlib import Path


MIN_DEPLOY_TEST_ACCURACY = 0.80
MIN_DEPLOY_CLASS_F1 = 0.60
MIN_DEPLOY_TEST_SEQUENCES_PER_CLASS = 10
POSTURE_LABELS = (
    "QIYAM", "RUKU", "GOING_TO_SUJUD", "SUJUD",
    "JALSA", "TASHAHHUD", "QIYAM_RISING",
)


def deployment_quality_issues(report_path: Path) -> list[str]:
    """Return reasons a trained artifact is not trustworthy enough to ship."""
    if not report_path.exists():
        return ["dataset_report.json is missing"]

    with open(report_path) as report_file:
        report = json.load(report_file)

    issues = []
    dataset = report.get("dataset", {})
    metrics = report.get("metrics") or {}
    if dataset.get("split_strategy") != "session_isolated":
        issues.append("evaluation was not split by independent recording session")

    test_accuracy = float(metrics.get("test_accuracy", 0.0))
    if test_accuracy < MIN_DEPLOY_TEST_ACCURACY:
        issues.append(
            f"test accuracy {test_accuracy:.1%} is below {MIN_DEPLOY_TEST_ACCURACY:.0%}"
        )

    f1_scores = metrics.get("per_class_f1_test", {})
    for label in POSTURE_LABELS:
        f1 = float(f1_scores.get(label, 0.0))
        if f1 < MIN_DEPLOY_CLASS_F1:
            issues.append(f"{label} test F1 {f1:.1%} is below {MIN_DEPLOY_CLASS_F1:.0%}")

    test_counts = dataset.get("sequences", {}).get("test", {})
    for label in POSTURE_LABELS:
        count = int(test_counts.get(label, 0))
        if count < MIN_DEPLOY_TEST_SEQUENCES_PER_CLASS:
            issues.append(
                f"{label} has only {count}/{MIN_DEPLOY_TEST_SEQUENCES_PER_CLASS} test sequences"
            )
    return issues
