#!/usr/bin/env python3
"""Compare evidence selection with generation on validation, without changing the app."""
from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
import subprocess
import time
from contextlib import closing
from pathlib import Path

try:
    from .evidence_selector import EvidenceIndex, SELECTOR_SYSTEM_PROMPT, evidence_sentences, resolve_selection, selector_request, ANSWERABILITY_SYSTEM_PROMPT, ANSWERABILITY_SCHEMA, resolve_answerability
    from .evaluate_gguf_model import extract_json
except ImportError:
    from evidence_selector import EvidenceIndex, SELECTOR_SYSTEM_PROMPT, evidence_sentences, resolve_selection, selector_request, ANSWERABILITY_SYSTEM_PROMPT, ANSWERABILITY_SCHEMA, resolve_answerability
    from evaluate_gguf_model import extract_json


def question_of(record: dict) -> str:
    prompt = record['messages'][-2]['content']
    return prompt.split('User question: ', 1)[1].split('\nSource text:', 1)[0]


def corpus_texts(root: Path, dataset: Path) -> dict[str, tuple[str, str]]:
    texts = {}
    def add(text, reference):
        texts.setdefault(hashlib.sha256(text.encode()).hexdigest(), (text, reference))
    lock = json.loads((root / 'sources.lock.json').read_text())
    for item in lock['sources']:
        if item['key'] not in {'databases/quran/quran_en.db', 'databases/hadith/shamayele_tirmidhi_complete.db'}:
            continue
        path = root / Path(item['key']).name
        if hashlib.sha256(path.read_bytes()).hexdigest() != item['sha256']:
            raise ValueError(f'Source checksum mismatch: {path}')
        with closing(sqlite3.connect(f'file:{path}?mode=ro', uri=True)) as db:
            if path.name == 'quran_en.db':
                for surah, ayah, text in db.execute('SELECT surah_number, number_in_surah, text FROM ayahs'):
                    add(text, f'Quran {surah}:{ayah}')
            else:
                for identifier, original in db.execute('SELECT id, text_plain FROM hadiths WHERE text_plain IS NOT NULL'):
                    if original.startswith('English\n'):
                        text = original[len('English\n'):].split('\n\nবাংলা', 1)[0]
                        add(text, f"Shamai'l At-Tirmidhi {identifier}")
    # Include the synthetic passages as searchable documents, using text/metadata only.
    # Test questions and labels are never read for this validation experiment.
    for split in ['train', 'validation']:
        for line in (dataset.parent / f'{split}.jsonl').read_text().splitlines():
            row = json.loads(line)
            add(row['sourceText'], row['sourceReference'])
    return texts


def retrieval_hit(record: dict, candidate) -> bool:
    expected = json.loads(record['messages'][-1]['content'])
    # Identical supporting sentences can occur in several overlapping contexts.
    # Count the evidence text, and report original-passage recall separately.
    return (expected['answer'] in candidate.text and
            (candidate.source_hash == record['sourceTextSha256'] or candidate.text in record['sourceText']))


def summarize(results: list[dict]) -> dict:
    answered = [r for r in results if r.get('selected') is not None]
    positives = [r for r in results if r['answerable']]
    negatives = [r for r in results if not r['answerable']]
    return {'samples': len(results), 'correct': sum(r['correct'] for r in results),
        'evidenceDecisionAccuracy': sum(r['correct'] for r in results) / max(1, len(results)),
        'answerableEvidenceRecall': sum(r['correct'] for r in positives) / max(1, len(positives)),
        'answerPrecision': sum(r['correct'] for r in answered) / max(1, len(answered)),
        'coverage': len(answered) / max(1, len(results)),
        'unanswerableSamples': len(negatives),
        'correctAbstentions': sum(r['correct'] for r in negatives),
        'invalidOutputs': sum(r.get('error') is not None for r in results),
        'meanDurationMs': round(sum(r['durationMs'] for r in results) / max(1, len(results))),
        'meanModelCalls': sum(r.get('modelCalls', 1) for r in results) / max(1, len(results)),
        'gateDecisionAccuracy': sum(r.get('gateAnswerable') == r['answerable'] for r in results) / max(1, len(results)) if any(r.get('gateAnswerable') is not None for r in results) else None}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data', type=Path, required=True)
    parser.add_argument('--model', type=Path, required=True)
    parser.add_argument('--runner', type=Path, required=True)
    parser.add_argument('--sources', type=Path, required=True)
    parser.add_argument('--generation-report', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--top-k', type=int, default=8)
    parser.add_argument('--samples', type=int, default=100000)
    parser.add_argument('--answerability-gate', action='store_true', help='Classify answerability before selecting a sentence')
    args = parser.parse_args()
    if args.data.name != 'validation.jsonl':
        parser.error('This prototype is restricted to validation data; preserve the held-out test')
    records = [json.loads(line) for line in args.data.read_text().splitlines()][:args.samples]
    texts = corpus_texts(args.sources, args.data)
    index = EvidenceIndex([e for text, ref in texts.values() for e in evidence_sentences(text, ref)])
    results = []
    for i, row in enumerate(records, 1):
        expected = json.loads(row['messages'][-1]['content'])
        answerable = expected['contentType'] == 'answer'
        question = question_of(row)
        candidates = evidence_sentences(row['sourceText'], row['sourceReference'])
        prompt, schema, choices = selector_request(question, candidates, row['id'])
        started = time.monotonic()
        selected, error, raw = None, None, ""
        gate_raw, gate_answerable, calls = None, None, 0
        def infer(system, user, output_schema):
            nonlocal calls
            calls += 1
            completed = subprocess.run([str(args.runner), '--model', str(args.model), '-sys', system,
                '--prompt', user, '--single-turn', '--predict', '40', '--json-schema', output_schema, '--temp', '0',
                '--no-display-prompt', '--no-warmup', '--simple-io', '--no-conversation'],
                capture_output=True, text=True, check=True, timeout=60)
            return extract_json(completed.stdout)
        try:
            if args.answerability_gate:
                gate_raw = infer(ANSWERABILITY_SYSTEM_PROMPT, row['messages'][-2]['content'], ANSWERABILITY_SCHEMA)
                gate_answerable = resolve_answerability(gate_raw)
            raw = infer(SELECTOR_SYSTEM_PROMPT, prompt, schema) if gate_answerable is not False else '{"evidenceId":"NONE"}'
            selected = resolve_selection(raw, choices, {row['sourceTextSha256']: row['sourceText']})
        except (ValueError, KeyError, subprocess.SubprocessError) as failure:
            error = str(failure)
        correct = error is None and ((selected is None and not answerable) or
            (selected is not None and answerable and expected['answer'] in selected['text']))
        retrieved = index.search(question, args.top_k) if answerable else []
        # A negative label only applies to its supplied source, not to every database record.
        # Therefore global retrieval recall is scored on answerable questions only.
        retrieved_correct = any(retrieval_hit(row, e) for e in retrieved) if answerable else None
        original_hit = any(e.source_hash == row['sourceTextSha256'] and expected['answer'] in e.text for e in retrieved) if answerable else None
        result = {'id': row['id'], 'family': row.get('benchmarkFamily', 'legacy_unreviewed'),
            'answerable': answerable, 'correct': correct, 'selected': selected, 'raw': raw, 'error': error,
            'gateRaw': gate_raw, 'gateAnswerable': gate_answerable, 'modelCalls': calls,
            'durationMs': round((time.monotonic() - started) * 1000), 'retrievalHit': retrieved_correct, 'originalPassageHit': original_hit}
        results.append(result)
        print(f'[{i}/{len(records)}] {row["id"]}: {"correct" if correct else "incorrect"}; retrieval={retrieved_correct}', flush=True)
    positives = [r for r in results if r['answerable']]
    report = {'model': args.model.name, 'testDataSha256': hashlib.sha256(args.data.read_bytes()).hexdigest(),
        'experiment': 'known_context_evidence_selection_plus_separate_global_retrieval_recall',
        'answerabilityGate': args.answerability_gate,
        'limitations': ['Selector receives the known source, not retrieved candidates; this is not end-to-end QA accuracy.',
            'Evidence correctness requires the selected sentence to contain the reference span; alternate valid evidence may be rejected.',
            'The source-checked validation subset is small and the agent-checked seed is not a comprehensive factual benchmark.',
            'Global abstention and retrieval precision require separately annotated search queries.',
            'Some questions require the original context or admit other corpus answers; retrieval recall is a target-snippet recovery diagnostic.'],
        'summary': summarize(results),
        'byFamily': {f: summarize([r for r in results if r['family'] == f]) for f in sorted({r['family'] for r in results})},
        'retrieval': {'algorithm': 'BM25', 'sentences': len(index.documents), 'topK': args.top_k,
                      'answerableSamples': len(positives), 'evidenceRecallAtK': sum(r['retrievalHit'] for r in positives) / max(1, len(positives)),
                      'originalPassageRecallAtK': sum(r['originalPassageHit'] for r in positives) / max(1, len(positives))},
        'results': results}
    report['retrieval']['byFamily'] = {}
    for family in sorted({r['family'] for r in results}):
        cases = [r for r in positives if r['family'] == family]
        report['retrieval']['byFamily'][family] = {
            'answerableSamples': len(cases),
            'evidenceRecallAtK': sum(r['retrievalHit'] for r in cases) / max(1, len(cases)),
            'originalPassageRecallAtK': sum(r['originalPassageHit'] for r in cases) / max(1, len(cases))}
    if args.generation_report:
        baseline = json.loads(args.generation_report.read_text())
        if baseline.get('testDataSha256') != report['testDataSha256']:
            raise ValueError('Generation comparison uses a different benchmark')
        report['generationBaseline'] = {k: baseline.get(k) for k in ['model', 'acceptanceRate', 'answerQuality', 'answerQualityByFamily']}
        report['limitations'].append('Generation exact-answer accuracy and sentence-selection accuracy use different output units; scores are not interchangeable.')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({k: v for k, v in report.items() if k != 'results'}, indent=2))


if __name__ == '__main__':
    main()
