#!/usr/bin/env python3
"""Package the validated reference reader without changing any active app model."""
from __future__ import annotations
import argparse
import gzip
import hashlib
import json
import re
import shutil
import tarfile
from pathlib import Path

MODEL_FILES=('config.json','model.safetensors','merges.txt','vocab.json','tokenizer.json','tokenizer_config.json','special_tokens_map.json')


def sha256(path:Path)->str:
    digest=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(1024*1024),b''):digest.update(block)
    return digest.hexdigest()


def package(model:Path,calibration:Path,assessment:Path,report:Path,output:Path,version:str)->dict:
    if not re.fullmatch(r'[a-z0-9][a-z0-9_-]*',version):raise ValueError('Invalid version')
    config=json.loads(calibration.read_text());qualified=json.loads(assessment.read_text());evaluation=json.loads(report.read_text())
    selected=config.get('selected') or {}
    architecture=json.loads((model/'config.json').read_text()).get('architectures')
    if architecture!=['RobertaForQuestionAnswering']:
        raise ValueError('Expected the validated RoBERTa extractive QA architecture')
    if selected.get('predictionMethod')!='span-margin' or not qualified.get('readerEvidenceGatePassed'):
        raise ValueError('Only a qualified span-margin evidence reader can be packaged')
    if selected.get('revision')!='local-sha256:'+sha256(model/'model.safetensors'):
        raise ValueError('Weights do not match frozen calibration')
    if evaluation.get('calibrationSha256')!=sha256(calibration) or evaluation.get('testDataSha256')!=config['holdoutDataSha256']:
        raise ValueError('Evaluation does not match frozen calibration/holdout')
    if qualified.get('holdoutReportSha256')!=sha256(report):raise ValueError('Assessment report checksum mismatch')
    bundle=output/version
    if bundle.exists():raise ValueError('Version output already exists; preserve it and select a new version')
    (bundle/'model').mkdir(parents=True)
    for name in MODEL_FILES:
        if not (model/name).is_file():raise ValueError(f'Missing model file: {name}')
        shutil.copyfile(model/name,bundle/'model'/name)
    for name,path in [('calibration.json',calibration),('assessment.json',assessment),('evaluation.json',report),('training_manifest.json',model/'training_manifest.json')]:
        shutil.copyfile(path,bundle/name)
    inference={'schemaVersion':1,'modelVersion':version,'architecture':'RobertaForQuestionAnswering',
        'runtime':'pytorch_reference','modelDirectory':'model','predictionMethod':'span-margin',
        'answerabilityThreshold':selected['threshold'],'maximumSequenceTokens':384,'maximumAnswerTokens':64,
        'outputPolicy':'original_source_quote_and_citation_only','standaloneShortAnswerApproved':False,
        'androidIntegrationStatus':'pending_incompatible_with_gguf_loader'}
    (bundle/'reader_config.json').write_text(json.dumps(inference,indent=2)+'\n')
    (bundle/'NOTICE.md').write_text('# Source reader attribution\n\nBase: deepset/roberta-base-squad2, published by deepset.\nModel card: https://huggingface.co/deepset/roberta-base-squad2\nLicense: CC BY 4.0 — https://creativecommons.org/licenses/by/4.0/\n\nModified by extractive fine-tuning on the project development dataset. See training_manifest.json and evaluation.json.\nThis is an evidence-quote reference artifact, not a GGUF model or an Android deployment.\n')
    files={str(p.relative_to(bundle)):{'sizeBytes':p.stat().st_size,'sha256':sha256(p)} for p in sorted(bundle.rglob('*')) if p.is_file()}
    (bundle/'files.json').write_text(json.dumps(files,indent=2)+'\n')
    archive=output/f'{version}.tar.gz'
    with archive.open('wb') as raw,gzip.GzipFile(fileobj=raw,mode='wb',filename='',mtime=0,compresslevel=1) as compressed,tarfile.open(fileobj=compressed,mode='w') as tar:
        for path in sorted(bundle.rglob('*')):
            if not path.is_file():continue
            info=tar.gettarinfo(str(path),arcname=str(Path(version)/path.relative_to(bundle)))
            info.mtime=0;info.uid=info.gid=0;info.uname=info.gname=''
            with path.open('rb') as stream:tar.addfile(info,stream)
    return {'schemaVersion':1,'modelVersion':version,'status':'validated_reference_artifact_android_integration_pending',
        'architecture':inference['architecture'],'runtime':inference['runtime'],'outputPolicy':inference['outputPolicy'],
        'artifact':{'fileName':archive.name,'sizeBytes':archive.stat().st_size,'sha256':sha256(archive)},
        'modelWeightsSha256':sha256(model/'model.safetensors'),'calibrationSha256':sha256(calibration),
        'predictionMethod':selected['predictionMethod'],'answerabilityThreshold':selected['threshold'],
        'evaluation':{'scope':'given_source_evidence_selection','cases':evaluation['summary']['samples'],
            'evidenceDecisionAccuracy':evaluation['summary']['evidenceDecisionAccuracy'],
            'answerPrecision':evaluation['summary']['answerPrecision'],'answerableRecall':evaluation['summary']['answerableEvidenceRecall'],
            'normalizedShortAnswerAccuracy':evaluation['normalizedShortAnswerAccuracy'],'holdoutSha256':evaluation['testDataSha256']},
        'delivery':{'activeInApp':False,'published':False,'requires':'A compatible extractive-reader runtime and tokenizer; cannot replace GGUF weights.'},
        'limitations':['Small agent-checked source-reading pilot; no end-to-end retrieval or Android results.',
                       'Raw extracted short answers are not approved as standalone facts.']}


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['model','calibration','assessment','report','output','release-record']:
        p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--version',required=True);a=p.parse_args()
    release=package(a.model,a.calibration,a.assessment,a.report,a.output,a.version)
    a.release_record.parent.mkdir(parents=True,exist_ok=True)
    a.release_record.write_text(json.dumps(release,indent=2)+'\n')
    print(json.dumps(release,indent=2))
