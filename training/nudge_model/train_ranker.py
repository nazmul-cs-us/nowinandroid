#!/usr/bin/env python3
"""Train the tiny Now Nudge linear ranker and export versioned weights.

The input is a CSV created from consented, privacy-minimal interaction events. The exported JSON
uses the same field names as DeenlyRankingWeights in :core:model. No TensorFlow runtime is needed
in the app; Android and iOS perform only a few multiplications per candidate.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import math
import random
from pathlib import Path


FEATURES = (
    "basePriority",
    "contextMatch",
    "unfinishedProgress",
    "userAffinity",
    "freshness",
    "expectedCompletion",
    "repeatImpressionPenalty",
    "recentActionPenalty",
)

STARTER_WEIGHTS = {
    "basePriority": 1.0,
    "contextMatch": 1.8,
    "unfinishedProgress": 1.4,
    "userAffinity": 1.0,
    "freshness": 0.6,
    "expectedCompletion": 1.2,
    "repeatImpressionPenalty": 0.35,
    "recentActionPenalty": 0.8,
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path, help="Training examples CSV")
    parser.add_argument("--output", required=True, type=Path, help="Versioned JSON output")
    parser.add_argument("--model-version", required=True, help="For example nudge-ltr-2026-10-05")
    parser.add_argument("--epochs", type=int, default=300)
    parser.add_argument("--learning-rate", type=float, default=0.03)
    parser.add_argument("--l2", type=float, default=0.001)
    parser.add_argument("--seed", type=int, default=7)
    return parser.parse_args()


def bounded(value: str, maximum: float = 1.0) -> float:
    return min(max(float(value), 0.0), maximum)


def load_examples(path: Path) -> list[tuple[list[float], float]]:
    examples: list[tuple[list[float], float]] = []
    with path.open(newline="", encoding="utf-8") as source:
        for row in csv.DictReader(source):
            # Penalty inputs are negative because the Kotlin scorer subtracts these terms.
            values = [
                bounded(row["base_priority"]),
                bounded(row["context_match"]),
                bounded(row["unfinished_progress"]),
                bounded(row["user_affinity"]),
                bounded(row["freshness"]),
                bounded(row["expected_completion"]),
                -bounded(row["impressions_today"], maximum=5.0),
                -bounded(row["was_recent_action"]),
            ]
            examples.append((values, bounded(row["reward"])))
    if not examples:
        raise ValueError("The training CSV contains no examples")
    return examples


def sigmoid(value: float) -> float:
    value = min(max(value, -30.0), 30.0)
    return 1.0 / (1.0 + math.exp(-value))


def train(
    examples: list[tuple[list[float], float]],
    epochs: int,
    learning_rate: float,
    l2: float,
    seed: int,
) -> tuple[float, list[float]]:
    rng = random.Random(seed)
    intercept = 0.0
    weights = [STARTER_WEIGHTS[name] for name in FEATURES]
    order = list(range(len(examples)))

    for _ in range(epochs):
        rng.shuffle(order)
        for index in order:
            inputs, target = examples[index]
            prediction = sigmoid(intercept + sum(w * x for w, x in zip(weights, inputs)))
            error = prediction - target
            intercept -= learning_rate * error
            for feature_index, value in enumerate(inputs):
                gradient = error * value + l2 * weights[feature_index]
                weights[feature_index] -= learning_rate * gradient

        # All scorer coefficients are positive; penalty features were supplied with a minus sign.
        weights = [max(weight, 0.0) for weight in weights]

    return intercept, weights


def main() -> None:
    args = parse_args()
    examples = load_examples(args.input)
    intercept, trained = train(
        examples=examples,
        epochs=args.epochs,
        learning_rate=args.learning_rate,
        l2=args.l2,
        seed=args.seed,
    )
    payload = {
        "schemaVersion": 1,
        "modelVersion": args.model_version,
        "trainedAtUtc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "trainingExampleCount": len(examples),
        "weights": {
            "intercept": intercept,
            **dict(zip(FEATURES, trained)),
        },
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
