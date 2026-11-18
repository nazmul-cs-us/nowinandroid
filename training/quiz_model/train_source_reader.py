#!/usr/bin/env python3
"""Fine-tune extractive start/end heads using retired development data only."""
from __future__ import annotations
import argparse
import hashlib
import json
import random
from pathlib import Path


def encode_qa(tokenizer,row,max_length=384):
    question=row['messages'][-2]['content'].split('User question: ',1)[1].split('\nSource text:',1)[0]
    encoded=tokenizer(question,row['sourceText'],return_offsets_mapping=True,truncation=False,
                      padding='max_length',max_length=max_length)
    if len(encoded['input_ids'])>max_length:raise ValueError('Source overflow; no truncation allowed')
    offsets=encoded.pop('offset_mapping');sequences=encoded.sequence_ids()
    cls=encoded['input_ids'].index(tokenizer.cls_token_id)
    expected=json.loads(row['messages'][-1]['content'])
    start=end=cls
    if expected['contentType']=='answer':
        answer=expected['answer'];begin=row['sourceText'].find(answer)
        if begin<0:raise ValueError('Training answer is not a source span')
        finish=begin+len(answer)
        starts=[i for i,(a,b) in enumerate(offsets) if sequences[i]==1 and a<=begin<b]
        ends=[i for i,(a,b) in enumerate(offsets) if sequences[i]==1 and a<finish<=b]
        if not starts or not ends:raise ValueError('Answer offsets do not map to source tokens')
        start,end=starts[0],ends[-1]
    return dict(encoded,start_positions=start,end_positions=end)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--development',type=Path,required=True);p.add_argument('--synthetic',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True);p.add_argument('--model',default='deepset/roberta-base-squad2')
    p.add_argument('--revision',required=True)
    a=p.parse_args()
    if a.development.name!='development.jsonl' or a.synthetic.name!='train.jsonl':
        p.error('Only retired development examples and training synthetics are allowed')
    if (a.output/'training_manifest.json').exists():
        raise SystemExit('Training output exists; preserve it and use a new version')
    import torch
    from transformers import AutoTokenizer,AutoModelForQuestionAnswering,TrainingArguments,Trainer,set_seed,default_data_collator
    set_seed(20261119);torch.set_num_threads(4)
    old=[json.loads(l) for l in a.development.read_text().splitlines()]
    synthetic=[json.loads(l) for l in a.synthetic.read_text().splitlines() if json.loads(l).get('benchmarkFamily')=='synthetic_contract']
    random.Random(20261119).shuffle(synthetic)
    rows=old*4+synthetic[:400]
    tokenizer=AutoTokenizer.from_pretrained(a.model,revision=a.revision)
    encoded=[encode_qa(tokenizer,r) for r in rows]
    class Dataset(torch.utils.data.Dataset):
        def __len__(self):return len(encoded)
        def __getitem__(self,i):return encoded[i]
    model=AutoModelForQuestionAnswering.from_pretrained(a.model,revision=a.revision)
    a.output.mkdir(parents=True,exist_ok=True)
    training=TrainingArguments(output_dir=str(a.output/'checkpoints'),num_train_epochs=2,
        per_device_train_batch_size=8,learning_rate=1e-5,warmup_ratio=.1,weight_decay=.01,
        logging_steps=10,save_strategy='no',eval_strategy='no',report_to=[],dataloader_pin_memory=False,seed=20261119)
    trainer=Trainer(model=model,args=training,train_dataset=Dataset(),data_collator=default_data_collator)
    result=trainer.train()
    model.save_pretrained(a.output,safe_serialization=True);tokenizer.save_pretrained(a.output)
    manifest={'baseModel':a.model,'baseRevision':a.revision,'seed':20261119,'epochs':2,'learningRate':1e-5,
        'domainUniqueCases':len(old),'domainWeight':4,'syntheticCases':min(400,len(synthetic)),
        'developmentSha256':hashlib.sha256(a.development.read_bytes()).hexdigest(),
        'syntheticTrainingSha256':hashlib.sha256(a.synthetic.read_bytes()).hexdigest(),
        'role':'Retired benchmark is training/development; fresh validation/test are not loaded.',
        'trainingLoss':result.training_loss,'trainingRuntimeSeconds':result.metrics.get('train_runtime')}
    (a.output/'training_manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(manifest,indent=2))


if __name__=='__main__':main()
