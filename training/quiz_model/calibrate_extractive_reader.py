#!/usr/bin/env python3
"""Select and freeze a reader/cutoff using validation only, subject to accuracy/recall floors."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import math
from pathlib import Path

try:
    from .evaluate_evidence_selector import summarize
except ImportError:
    from evaluate_evidence_selector import summarize


def apply_threshold(results: list[dict], threshold: float) -> list[dict]:
    if not math.isfinite(threshold) or not 0 <= threshold <= 1:
        raise ValueError('Confidence threshold must be in [0, 1]')
    calibrated = copy.deepcopy(results)
    for row in calibrated:
        prediction = row.get('spanPrediction') or {}
        candidate=row.get('candidateSelected',row.get('selected'))
        if candidate is not None and row.get('error') is None:
            if prediction.get('score',0)>=threshold:
                row['selected']=candidate
                row['correct']=row.get('candidateCorrect',row['correct'])
                row['shortAnswerCorrect']=row.get('candidateShortAnswerCorrect',row['shortAnswerCorrect'])
            else:
                row['selected']=None;row['correct']=not row['answerable']
                row['shortAnswerCorrect']=row['correct'];row['abstainedByCalibration']=True
    return calibrated


def metrics(results: list[dict]) -> dict:
    return {'summary': summarize(results),
            'byFamily': {family: summarize([r for r in results if r['family'] == family])
                         for family in sorted({r['family'] for r in results})}}


def meets_targets(measured: dict, target: float, baseline: dict | None = None) -> bool:
    # Precision and decision accuracy apply to each family. Recall must remain
    # >= target overall, and calibration may not reduce any family's original
    # recall. Report small-family recall honestly rather than rounding 8/9 to 90%.
    if measured['summary']['answerableEvidenceRecall'] < target:
        return False
    for group in [measured['summary'], *measured['byFamily'].values()]:
        if any(group[name] < target for name in ('answerPrecision', 'evidenceDecisionAccuracy')):
            return False
    if baseline:
        for family, group in measured['byFamily'].items():
            if group['answerableEvidenceRecall'] < baseline['byFamily'][family]['answerableEvidenceRecall']:
                return False
    return True


def calibrate(report: dict, target: float = .90) -> dict:
    if not 0 < target <= 1:
        raise ValueError('Target must be in (0, 1]')
    thresholds = {0.0}
    for row in report['results']:
        if row.get('candidateSelected',row.get('selected')) is not None:
            score = float(row['spanPrediction']['score'])
            if not math.isfinite(score) or not 0 <= score <= 1:
                raise ValueError('Invalid prediction confidence')
            if score < 1:
                thresholds.add(math.nextafter(score, 1.0))
    baseline = metrics(report['results'])
    candidates = []
    for cutoff in sorted(thresholds):
        measured = metrics(apply_threshold(report['results'], cutoff))
        if meets_targets(measured, target, baseline):
            candidates.append({'threshold': cutoff, **measured})
    if not candidates:
        return {'eligible': False, 'target': target, 'reason': 'No cutoff meets precision/accuracy in every family while preserving recall',
                'uncalibrated': metrics(report['results'])}
    best = max(candidates, key=lambda c: (c['summary']['answerableEvidenceRecall'],
        c['summary']['evidenceDecisionAccuracy'], c['summary']['answerPrecision'], -c['threshold']))
    return {'eligible': True, 'target': target, 'uncalibrated': baseline, **best}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reports', nargs='+', type=Path, required=True)
    parser.add_argument('--validation-data', type=Path, required=True)
    parser.add_argument('--holdout-data', type=Path, required=True)
    parser.add_argument('--target', type=float, default=.90)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.validation_data.name != 'validation.jsonl' or args.holdout_data.name != 'test.jsonl':
        parser.error('Use validation.jsonl for selection and test.jsonl as the frozen holdout')
    validation_hash = hashlib.sha256(args.validation_data.read_bytes()).hexdigest()
    options = []
    for path in args.reports:
        report = json.loads(path.read_text())
        if report['testDataSha256'] != validation_hash:
            raise ValueError(f'Validation data mismatch: {path}')
        result = calibrate(report, args.target)
        result.update(model=report['model'], revision=report['revision'], parameterCount=report['parameterCount'],
                      predictionMethod=report.get('predictionMethod','pipeline'), validationReport=str(path), validationReportSha256=hashlib.sha256(path.read_bytes()).hexdigest())
        options.append(result)
    eligible = [r for r in options if r['eligible']]
    output = {'schemaVersion': 1, 'selectionDataRole': 'validation', 'target': args.target,
              'validationDataSha256': validation_hash,
              # Fingerprint only: never load or score holdout labels during calibration.
              'holdoutDataSha256': hashlib.sha256(args.holdout_data.read_bytes()).hexdigest(),
              'constraints': ['precision >= target overall and in each benchmark family',
                              'decision accuracy >= target overall and in each benchmark family',
                              'answerable recall >= target overall',
                              'calibration cannot reduce any family recall below that model baseline'],
              'candidates': options, 'selected': None}
    if eligible:
        chosen = max(eligible, key=lambda r: (r['summary']['answerableEvidenceRecall'],
            r['summary']['evidenceDecisionAccuracy'], r['summary']['answerPrecision'], -r['parameterCount']))
        output['selected'] = {k: chosen[k] for k in ['model', 'revision', 'threshold', 'predictionMethod', 'parameterCount', 'validationReportSha256']}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, indent=2) + '\n')
    print(json.dumps(output, indent=2))
    if not eligible:
        raise SystemExit('No validation-qualified model; do not evaluate the holdout')


if __name__ == '__main__':
    main()
