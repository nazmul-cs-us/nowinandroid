#!/usr/bin/env python3
"""Require each task and benchmark family to exceed the configured candidate threshold."""
import argparse
import json
from pathlib import Path


def failures(question: dict | None, knowledge: dict | None, threshold: float = 0.90) -> list[str]:
    checks = {}
    if question is not None:
        checks['Question contract acceptance'] = question.get('acceptanceRate', 0)
    if knowledge is not None:
        quality = knowledge.get('answerQuality') or {}
        checks.update({
            'Knowledge contract acceptance': knowledge.get('acceptanceRate', 0),
            'Knowledge reference match': quality.get('referenceMatchRate', 0),
            'Knowledge answerable reference match': quality.get('answerableExactMatchRate', 0),
        })
        for family, quality in knowledge.get('answerQualityByFamily', {}).items():
            if quality:
                checks[f'{family} reference match'] = quality['referenceMatchRate']
                checks[f'{family} answerable match'] = quality['answerableExactMatchRate']
    if not checks:
        return ['No evaluation reports supplied']
    return [f'{name}: {score:.1%} must exceed {threshold:.0%}' for name, score in checks.items() if score <= threshold]


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--question', type=Path)
    parser.add_argument('--knowledge', type=Path)
    args = parser.parse_args()
    if args.question is None and args.knowledge is None:
        parser.error('Supply at least one evaluation report')
    issues = failures(json.loads(args.question.read_text()) if args.question else None,
                      json.loads(args.knowledge.read_text()) if args.knowledge else None)
    if issues:
        raise SystemExit('QUALITY GATE FAILED; do not promote these candidates.\n' + '\n'.join(issues))
    print('Measured metrics exceed 90% in every reported family. This is limited to the documented benchmark scope.')
