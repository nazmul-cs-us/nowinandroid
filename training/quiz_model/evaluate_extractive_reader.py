#!/usr/bin/env python3
"""Benchmark a pretrained span reader as an evidence selector; no generated quotations."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import time
from dataclasses import asdict
from pathlib import Path

try:
    from .evidence_selector import Evidence, evidence_sentences
    from .evaluate_evidence_selector import question_of, summarize
    from .evaluate_gguf_model import normalized_answer
    from .source_margin_reader import SourceMarginReader
except ImportError:
    from evidence_selector import Evidence, evidence_sentences
    from evaluate_evidence_selector import question_of, summarize
    from evaluate_gguf_model import normalized_answer
    from source_margin_reader import SourceMarginReader


def resolve_span(source: str, reference: str, start: int, end: int) -> Evidence | None:
    if start == end == 0:
        return None
    if not (0 <= start < end <= len(source)):
        raise ValueError('Reader returned invalid character offsets')
    for sentence in evidence_sentences(source, reference):
        if sentence.start <= start and end <= sentence.end:
            return sentence
    return Evidence(hashlib.sha256(source.encode()).hexdigest(), reference, start, end, source[start:end])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data', type=Path, required=True)
    parser.add_argument('--model', default='deepset/minilm-uncased-squad2')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--revision')
    parser.add_argument('--prediction-method',choices=['pipeline','span-margin'],default='pipeline')
    parser.add_argument('--calibration', type=Path, help='Frozen validation-selected configuration; required for test evaluation')
    args = parser.parse_args()
    calibration, cutoff, selected_configuration = None, 0.0, None
    if args.calibration:
        calibration = json.loads(args.calibration.read_text())
        selected_configuration = calibration.get('selected')
        if not selected_configuration or calibration.get('selectionDataRole') != 'validation':
            parser.error('Calibration has no validation-qualified configuration')
        cutoff = float(selected_configuration['threshold'])
        if not math.isfinite(cutoff) or not 0 <= cutoff <= 1:
            parser.error('Calibration contains an invalid confidence threshold')
        args.model = selected_configuration['model']
        args.prediction_method = selected_configuration.get('predictionMethod', 'pipeline')
        expected_hash = calibration['holdoutDataSha256'] if args.data.name == 'test.jsonl' else calibration['validationDataSha256']
        if hashlib.sha256(args.data.read_bytes()).hexdigest() != expected_hash:
            parser.error('Data differs from the frozen calibration fingerprint')
    if args.data.name not in {'development.jsonl', 'validation.jsonl', 'test.jsonl'} or (args.data.name == 'test.jsonl' and not calibration):
        parser.error('Use validation data, or test data with a frozen calibration configuration')
    import torch
    from huggingface_hub import model_info
    from transformers import AutoModelForQuestionAnswering, AutoTokenizer, pipeline
    local=Path(args.model).is_dir()
    if local:
        weights=Path(args.model)/'model.safetensors'
        revision='local-sha256:'+hashlib.sha256(weights.read_bytes()).hexdigest()
        if selected_configuration and selected_configuration['revision']!=revision:
            parser.error('Local model weights differ from frozen calibration')
    else:
        revision=selected_configuration['revision'] if selected_configuration else args.revision or model_info(args.model).sha
    tokenizer = AutoTokenizer.from_pretrained(args.model, revision=None if local else revision)
    model = AutoModelForQuestionAnswering.from_pretrained(args.model, revision=None if local else revision)
    torch.set_num_threads(4)
    reader = SourceMarginReader(model,tokenizer) if args.prediction_method=='span-margin' else pipeline(
        'question-answering',model=model,tokenizer=tokenizer,device=-1,framework='pt')
    if not calibration and args.prediction_method=='span-margin':cutoff=.5
    rows = [json.loads(line) for line in args.data.read_text().splitlines()]
    results = []
    for i, row in enumerate(rows, 1):
        expected = json.loads(row['messages'][-1]['content'])
        answerable = expected['contentType'] == 'answer'
        started = time.monotonic()
        selected, error, predicted, candidate = None, None, None, None
        try:
            predicted = reader(question=question_of(row), context=row['sourceText'],
                               handle_impossible_answer=True, max_seq_len=384, doc_stride=128)
            start, end = int(predicted['start']), int(predicted['end'])
            if predicted['answer'] != row['sourceText'][start:end]:
                raise ValueError('Reader output disagrees with the original source offsets')
            candidate = resolve_span(row['sourceText'], row['sourceReference'], start, end)
            selected = candidate
            if selected is not None and float(predicted['score']) < cutoff:
                selected = None
        except (ValueError, KeyError, TypeError) as failure:
            error = str(failure)
        correct = error is None and ((selected is None and not answerable) or
            (selected is not None and answerable and expected['answer'] in selected.text))
        short_correct = error is None and ((selected is None and not answerable) or
            (selected is not None and answerable and normalized_answer(predicted['answer']) == normalized_answer(expected['answer'])))
        results.append({'id': row['id'], 'family': row.get('benchmarkFamily', 'legacy_unreviewed'),
            'answerable': answerable, 'correct': correct, 'selected': asdict(selected) if selected else None,
            'error': error, 'shortAnswerCorrect': short_correct, 'spanPrediction': predicted,
            'candidateSelected':asdict(candidate) if candidate else None,
            'candidateCorrect':candidate is not None and answerable and expected['answer'] in candidate.text,
            'candidateShortAnswerCorrect':candidate is not None and answerable and normalized_answer(predicted['answer'])==normalized_answer(expected['answer']),
            'durationMs': round((time.monotonic() - started) * 1000)})
        print(f'[{i}/{len(rows)}] {row["id"]}: {"correct" if correct else "incorrect"}', flush=True)
    report = {'model': args.model, 'revision': revision, 'parameterCount': sum(p.numel() for p in model.parameters()),
        'predictionMethod':args.prediction_method,
        'modelCard': f'https://huggingface.co/{args.model}' if not local else None, 'modelLicense': 'cc-by-4.0' if args.model in {'deepset/minilm-uncased-squad2', 'deepset/roberta-base-squad2', 'deepset/deberta-v3-base-squad2'} else 'consult_model_card',
        'testDataSha256': hashlib.sha256(args.data.read_bytes()).hexdigest(),
        'dataRole': args.data.stem, 'confidenceThreshold': cutoff,
        'calibrationSha256': hashlib.sha256(args.calibration.read_bytes()).hexdigest() if args.calibration else None,
        'experiment': 'known_context_pretrained_extractive_reader_as_evidence_selector',
        'summary': summarize(results),
        'normalizedShortAnswerAccuracy': sum(r['shortAnswerCorrect'] for r in results) / len(results),
        'byFamily': {f: dict(summarize([r for r in results if r['family'] == f]),
            normalizedShortAnswerAccuracy=sum(r['shortAnswerCorrect'] for r in results if r['family'] == f) / sum(r['family'] == f for r in results))
            for f in sorted({r['family'] for r in results})},
        'limitations': ['This is known-context reading, not end-to-end corpus search accuracy.',
            'Confidence threshold was frozen using validation only.' if calibration else 'No confidence threshold was tuned; the pretrained SQuAD2 no-answer decision is used.',
            'Evidence correctness and short-answer exact matching are reported separately.',
            'The source-reading subset is small and agent-checked, not a comprehensive factual benchmark.',
            'CPU fp32 results do not establish Android latency or quantized model quality.'],
        'results': results}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({k: v for k, v in report.items() if k != 'results'}, indent=2))


if __name__ == '__main__':
    main()
