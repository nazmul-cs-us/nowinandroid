import json
import math
import unittest
from training.quiz_model.source_margin_reader import decode_span,align_word_offsets
from training.quiz_model.train_source_reader import encode_qa
from training.quiz_model.calibrate_extractive_reader import apply_threshold


class MarginReaderTest(unittest.TestCase):
    def test_decoder_excludes_question_and_special_tokens(self):
        r=decode_span([0,100,2,8,1],[0,100,1,2,9],[(0,0),(0,4),(0,3),(4,7),(8,13)],[None,0,1,1,1],0)
        self.assertEqual((4,13),(r['start'],r['end']))
        self.assertGreater(r['score'],.5)

    def test_null_winner_retains_a_recoverable_source_candidate(self):
        r=decode_span([8,2],[8,3],[(0,0),(0,5)],[None,1],0)
        self.assertEqual((0,5),(r['start'],r['end']))
        self.assertLess(r['score'],.5)
        row={'selected':None,'candidateSelected':{'text':'bread'},'candidateCorrect':True,
             'candidateShortAnswerCorrect':True,'answerable':True,'correct':False,
             'shortAnswerCorrect':False,'spanPrediction':r,'error':None}
        calibrated=apply_threshold([row],0)[0]
        self.assertTrue(calibrated['correct'])
        self.assertIsNotNone(calibrated['selected'])

    def test_invalid_spans_and_nonfinite_logits_are_rejected(self):
        with self.assertRaises(ValueError):decode_span([0],[0],[(0,0)],[None],0)
        with self.assertRaises(ValueError):decode_span([math.nan,1],[0,1],[(0,0),(0,3)],[None,1],0)
        with self.assertRaises(ValueError):decode_span([0,1],[0],[(0,0)],[None],0)

    def test_word_alignment_accepts_tokenizers_tuple_api(self):
        class Encoding:
            def token_to_word(self,i):return i
            def word_to_chars(self,i,sequence_index=1):return (i*4,i*4+3)
        self.assertEqual((4,11),align_word_offsets(Encoding(),1,2,5,10))

    def test_qa_labels_are_context_positions_or_cls(self):
        class Encoded(dict):
            def sequence_ids(self):return [None,0,None,1,1,1]
        class Tokenizer:
            cls_token_id=0
            def __call__(self,*a,**k):
                return Encoded(input_ids=[0,5,2,10,11,12],attention_mask=[1]*6,
                    offset_mapping=[(0,0),(0,4),(0,0),(0,3),(4,7),(8,14)])
        row={'sourceText':'Ali ate bread.','messages':[{'content':"User question: What did Ali eat?\nSource text:\nAli ate bread."},
             {'content':json.dumps({'contentType':'answer','answer':'bread'})}]}
        encoded=encode_qa(Tokenizer(),row)
        self.assertEqual(5,encoded['start_positions']);self.assertEqual(5,encoded['end_positions'])
        row['messages'][-1]['content']=json.dumps({'contentType':'unanswered','answer':''})
        self.assertEqual(0,encode_qa(Tokenizer(),row)['start_positions'])


if __name__=='__main__':unittest.main()
