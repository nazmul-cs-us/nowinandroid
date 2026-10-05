import csv
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


class TrainRankerTest(unittest.TestCase):
    def test_exports_versioned_weights(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            input_path = root / "train.csv"
            output_path = root / "model.json"
            fields = [
                "base_priority",
                "context_match",
                "unfinished_progress",
                "user_affinity",
                "freshness",
                "expected_completion",
                "impressions_today",
                "was_recent_action",
                "reward",
            ]
            with input_path.open("w", newline="", encoding="utf-8") as target:
                writer = csv.DictWriter(target, fieldnames=fields)
                writer.writeheader()
                writer.writerow(dict.fromkeys(fields, "1"))
                writer.writerow(dict.fromkeys(fields, "0"))

            script = Path(__file__).with_name("train_ranker.py")
            subprocess.run(
                [
                    sys.executable,
                    str(script),
                    "--input",
                    str(input_path),
                    "--output",
                    str(output_path),
                    "--model-version",
                    "test-v1",
                    "--epochs",
                    "2",
                ],
                check=True,
            )

            model = json.loads(output_path.read_text(encoding="utf-8"))
            self.assertEqual(1, model["schemaVersion"])
            self.assertEqual("test-v1", model["modelVersion"])
            self.assertEqual(2, model["trainingExampleCount"])
            self.assertEqual(
                {
                    "intercept",
                    "basePriority",
                    "contextMatch",
                    "unfinishedProgress",
                    "userAffinity",
                    "freshness",
                    "expectedCompletion",
                    "repeatImpressionPenalty",
                    "recentActionPenalty",
                },
                set(model["weights"]),
            )


if __name__ == "__main__":
    unittest.main()
