"""Contract tests for Android JSONL output and the Salah training loader."""

import json
import tempfile
import unittest
from pathlib import Path

import numpy as np

from feature_engineering import (
    POSTURE_LABELS,
    assign_sessions_to_splits,
    create_sequences,
    load_jsonl_files,
    session_ids_from_groups,
    validate_training_sample,
)
from deployment_quality import POSTURE_LABELS as DEPLOY_LABELS, deployment_quality_issues


GUIDED_LABEL_ORDER = [
    "QIYAM",
    "RUKU",
    "QIYAM_RISING",
    "GOING_TO_SUJUD",
    "SUJUD",
    "JALSA",
    "GOING_TO_SUJUD",
    "SUJUD",
    "TASHAHHUD",
    "RISING_TO_QIYAM",
]


def make_sample(session_id: str, posture: str, timestamp: int) -> dict:
    return {
        "timestamp": timestamp,
        "session_id": session_id,
        "posture": posture,
        "schema_version": 2,
        "collection_mode": "guided",
        "label_source": "guided_instruction",
        "accel_x": [0.1, 0.2, 0.3, 0.2, 0.1],
        "accel_y": [9.7, 9.8, 9.9, 9.8, 9.7],
        "accel_z": [0.4, 0.5, 0.6, 0.5, 0.4],
        "gyro_x": [0.01, 0.02, 0.03, 0.02, 0.01],
        "gyro_y": [0.03, 0.02, 0.01, 0.02, 0.03],
        "gyro_z": [0.01, 0.01, 0.02, 0.01, 0.01],
        "pitch": 87.0,
        "roll": 14.0,
        "accel_magnitude": 9.81,
        "gyro_magnitude": 0.03,
    }


class TrainingContractTest(unittest.TestCase):
    def test_three_guided_sessions_create_split_groups_for_every_class(self):
        samples = []
        timestamp = 1_000_000
        for session_number in range(3):
            for posture in GUIDED_LABEL_ORDER:
                # More than the model's 20-window (2 second) sequence length.
                for _ in range(25):
                    sample = make_sample(f"guided-{session_number}", posture, timestamp)
                    self.assertIsNone(validate_training_sample(sample))
                    samples.append(sample)
                    timestamp += 100
            # A guided run only ever contains prayer. NOT_PRAYING has to come from its own
            # takes, so the split needs separate negative sessions to reach every class —
            # asserting otherwise would demand a guided run contain something it cannot.
            for _ in range(25):
                sample = make_sample(f"negative-{session_number}", "NOT_PRAYING", timestamp)
                self.assertIsNone(validate_training_sample(sample))
                samples.append(sample)
                timestamp += 100

        X, y, groups = create_sequences(samples, return_groups=True)

        self.assertEqual(X.shape[1:], (20, 30))
        for class_index, posture in enumerate(POSTURE_LABELS):
            group_count = len(np.unique(groups[y == class_index]))
            self.assertGreaterEqual(group_count, 3, posture)

        train_sessions, val_sessions, test_sessions = assign_sessions_to_splits(y, groups)
        self.assertFalse(train_sessions & val_sessions)
        self.assertFalse(train_sessions & test_sessions)
        self.assertFalse(val_sessions & test_sessions)
        self.assertEqual(
            train_sessions | val_sessions | test_sessions,
            set(session_ids_from_groups(groups)),
        )
        for selected in (train_sessions, val_sessions, test_sessions):
            mask = np.isin(session_ids_from_groups(groups), list(selected))
            self.assertEqual(set(y[mask]), set(range(len(POSTURE_LABELS))))

    def test_loader_skips_pending_live_and_malformed_rows(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            data_dir = Path(temp_dir)
            valid = make_sample("guided-1", "QIYAM", 1_000_000)
            malformed = {**valid, "timestamp": 1_000_100, "accel_x": [0.1, 0.2]}

            reviewed_file = data_dir / "salah_guided_valid.jsonl"
            reviewed_file.write_text(
                json.dumps(valid) + "\n" + json.dumps(malformed) + "\n",
                encoding="utf-8",
            )
            pending_file = data_dir / "salah_live_pending.jsonl"
            pending_file.write_text(json.dumps(valid) + "\n", encoding="utf-8")

            renamed_but_unreviewed = data_dir / "salah_reviewed_untrusted.jsonl"
            renamed_but_unreviewed.write_text(
                json.dumps({**valid, "collection_mode": "live", "human_reviewed": False}) + "\n",
                encoding="utf-8",
            )

            human_reviewed = data_dir / "salah_reviewed_valid.jsonl"
            human_reviewed.write_text(
                json.dumps({**valid, "collection_mode": "live", "human_reviewed": True}) + "\n",
                encoding="utf-8",
            )

            loaded = load_jsonl_files(str(data_dir))

        self.assertEqual(len(loaded), 2)
        self.assertEqual(
            {sample["_source_file"] for sample in loaded},
            {reviewed_file.name, human_reviewed.name},
        )

    def test_sequence_never_bridges_a_sensor_gap(self):
        samples = [make_sample("guided-gap", "QIYAM", 1_000_000 + i * 100) for i in range(10)]
        samples += [make_sample("guided-gap", "QIYAM", 1_010_000 + i * 100) for i in range(10)]

        X, y = create_sequences(samples)

        self.assertEqual(X.shape, (0, 20, 30))
        self.assertEqual(len(y), 0)

    def test_deployment_gate_rejects_current_model_and_accepts_qualified_report(self):
        current_report = Path(__file__).parent / "output" / "dataset_report.json"
        current_issues = deployment_quality_issues(current_report)
        self.assertTrue(any("test accuracy" in issue for issue in current_issues))

        qualified = {
            "dataset": {
                "split_strategy": "session_isolated",
                "sequences": {"test": {label: 10 for label in DEPLOY_LABELS}},
            },
            "metrics": {
                "test_accuracy": 0.9,
                "per_class_f1_test": {label: 0.8 for label in DEPLOY_LABELS},
            },
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            report_path = Path(temp_dir) / "dataset_report.json"
            report_path.write_text(json.dumps(qualified), encoding="utf-8")
            self.assertEqual(deployment_quality_issues(report_path), [])


if __name__ == "__main__":
    unittest.main()
