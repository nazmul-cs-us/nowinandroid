#!/usr/bin/env python3
"""Build a source-only fresh benchmark and explicitly retire the previous benchmark to development."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
try:
    from .build_checked_answer_dataset import source_text
    from .build_answer_contract_dataset import build_record, validate_record
except ImportError:
    from build_checked_answer_dataset import source_text
    from build_answer_contract_dataset import build_record, validate_record


def build(sources: Path, specification: Path, previous: Path, output: Path) -> dict:
    spec=json.loads(specification.read_text())
    for key, expected in spec['sourceFiles'].items():
        if hashlib.sha256((sources/Path(key).name).read_bytes()).hexdigest()!=expected:
            raise ValueError('Source database checksum mismatch')
    old=[];seen=set();old_sources=set()
    for split in ['train','validation','test']:
        for line in (previous/f'{split}.jsonl').read_text().splitlines():
            row=json.loads(line)
            if row.get('benchmarkFamily')!='source_checked':continue
            identity=(row['sourceTextSha256'],row['messages'][-2]['content'])
            if identity in seen:continue
            seen.add(identity);old_sources.add(row['sourceTextSha256']);old.append(row)
    splits={'validation':[],'test':[]};groups={}
    for case in spec['cases']:
        text,provenance=source_text(sources,case)
        sha=hashlib.sha256(text.encode()).hexdigest()
        if sha!=case['sourceTextSha256'] or sha in old_sources:
            raise ValueError('Changed or previously evaluated source in fresh benchmark')
        split=case['split']
        if groups.setdefault(sha,split)!=split:raise ValueError('Source crosses splits')
        for i,qa in enumerate(case['questions']):
            answer=qa['answer']
            if answer is not None and answer not in text:raise ValueError('Answer is not an original source span')
            row=build_record(record_id=f"fresh-v2-{Path(case['sourceKey']).stem}-{case['reference']}-{i}",
                collection='quran' if '/quran/' in case['sourceKey'] else "shamai'l_at-tirmidhi",
                reference=case['reference'],topic='Source reading',source_text=text,question=qa['question'],
                payload={'contentType':'answer' if answer is not None else 'unanswered',
                         'answer':answer or '', 'evidence':text if answer is not None else ''})
            row.update(benchmarkFamily='source_checked',reviewStatus=spec['reviewStatus'],provenance=provenance)
            validate_record(row);splits[split].append(row)
    output.mkdir(parents=True,exist_ok=True)
    hashes={}
    for split,rows in {**splits,'development':old}.items():
        body=''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in rows)
        (output/f'{split}.jsonl').write_text(body);hashes[split]=hashlib.sha256(body.encode()).hexdigest()
    manifest={'schemaVersion':2,'specificationSha256':hashlib.sha256(specification.read_bytes()).hexdigest(),
        'sampling':spec['sampling'],'seed':spec['seed'],'counts':{s:len(r) for s,r in splits.items()},
        'retiredDevelopmentCases':len(old),'splitSha256':hashes,
        'scope':'Supplied-source reading only. All fresh references excluded from previous answer benchmarks.',
        'reviewStatus':spec['reviewStatus'],'oldBenchmarkRole':'development_only_no_longer_held_out'}
    (output/'dataset_manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    return manifest


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sources',type=Path,required=True);p.add_argument('--specification',type=Path,required=True)
    p.add_argument('--previous',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();print(json.dumps(build(a.sources,a.specification,a.previous,a.output),indent=2))
