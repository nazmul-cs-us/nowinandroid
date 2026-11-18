"""Extract source positions and calibrate answer-vs-null evidence, without stacked cutoffs."""
from __future__ import annotations
import math


def decode_span(start_logits, end_logits, offsets, sequence_ids, cls_index, max_answer_tokens=64):
    if not (len(start_logits)==len(end_logits)==len(offsets)==len(sequence_ids)):
        raise ValueError('Mismatched reader outputs')
    if not 0 <= cls_index < len(start_logits) or max_answer_tokens < 1:
        raise ValueError('Invalid decoder configuration')
    if not all(math.isfinite(float(x)) for x in [*start_logits,*end_logits]):
        raise ValueError('Non-finite reader logits')
    valid=[i for i,s in enumerate(sequence_ids) if s==1 and offsets[i][1]>offsets[i][0]]
    best=None
    for i in valid:
        for j in valid:
            if i<=j<i+max_answer_tokens:
                score=float(start_logits[i])+float(end_logits[j])
                if best is None or score>best[0]:best=(score,i,j)
    if best is None:raise ValueError('No valid source tokens')
    null=float(start_logits[cls_index])+float(end_logits[cls_index])
    margin=best[0]-null
    confidence=1/(1+math.exp(-margin)) if margin>=0 else math.exp(margin)/(1+math.exp(margin))
    return {'startToken':best[1],'endToken':best[2], 'start':offsets[best[1]][0],
            'end':offsets[best[2]][1], 'score':confidence,'answerNullMargin':margin,
            'scoreType':'sigmoid_answer_vs_null_logit_margin'}


def align_word_offsets(encoding,start_token,end_token,fallback_start,fallback_end):
    first=encoding.token_to_word(start_token);last=encoding.token_to_word(end_token)
    if first is not None and last is not None:
        start=encoding.word_to_chars(first,sequence_index=1);end=encoding.word_to_chars(last,sequence_index=1)
        if start is not None and end is not None:return start[0],end[1]
    return fallback_start,fallback_end


class SourceMarginReader:
    def __init__(self,model,tokenizer,max_length=384):
        self.model=model.eval();self.tokenizer=tokenizer;self.max_length=max_length

    def __call__(self,question,context,**unused):
        import torch
        encoded=self.tokenizer(question,context,return_offsets_mapping=True,return_tensors='pt',truncation=False)
        if encoded['input_ids'].shape[1]>self.max_length:
            raise ValueError('Source exceeds maximum context; do not silently truncate')
        offsets=encoded.pop('offset_mapping')[0].tolist()
        sequences=encoded.sequence_ids(0)
        ids=encoded['input_ids'][0].tolist()
        cls_index=ids.index(self.tokenizer.cls_token_id)
        with torch.inference_mode():output=self.model(**encoded)
        result=decode_span(output.start_logits[0].tolist(),output.end_logits[0].tolist(),offsets,sequences,cls_index)
        # Align partial BPE tokens to original word boundaries, never rewrite the quote.
        encoding=encoded.encodings[0]
        result['start'],result['end']=align_word_offsets(encoding,result['startToken'],result['endToken'],result['start'],result['end'])
        result['answer']=context[result['start']:result['end']]
        return result
