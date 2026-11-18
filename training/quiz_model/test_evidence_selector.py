import hashlib
import json
import unittest
from dataclasses import replace

from training.quiz_model.evidence_selector import (
    EvidenceIndex, evidence_sentences, resolve_selection, selector_request, resolve_answerability,
)
from training.quiz_model.evaluate_evidence_selector import summarize, retrieval_hit
from training.quiz_model.evaluate_extractive_reader import resolve_span


class EvidenceSelectorTest(unittest.TestCase):
    def test_quotes_notes_and_offsets_are_preserved(self):
        source = '  He said, "Hire him." Indeed, he is [very] trustworthy.  '
        candidates = evidence_sentences(source, 'Quran example')
        self.assertEqual(2, len(candidates))
        self.assertEqual('He said, "Hire him."', candidates[0].text)
        for c in candidates:
            self.assertEqual(c.text, source[c.start:c.end])
        _, _, choices = selector_request('What was said?', candidates, 'fixed seed')
        identifier = next(key for key, value in choices.items() if value == candidates[0])
        selected = resolve_selection(json.dumps({'evidenceId': identifier}), choices,
            {candidates[0].source_hash: source})
        self.assertEqual('He said, "Hire him."', selected['text'])
        self.assertEqual('Quran example', selected['reference'])

    def test_abbreviations_and_final_initial_do_not_drop_source(self):
        for source in ['Dr. Malik carried a book.', 'His initial was A.', 'A. B. reported this.']:
            self.assertEqual(source, evidence_sentences(source, 'r')[0].text)

    def test_model_cannot_supply_text_or_an_unknown_reference(self):
        source = 'He carried bread.'
        candidate = evidence_sentences(source, 'Original reference')[0]
        _, _, choices = selector_request('What did he carry?', [candidate])
        for payload in [{'evidenceId': 'S99'}, {'evidenceId': 'S1', 'text': 'invented'},
                        {'evidenceId': []}, []]:
            with self.assertRaises(ValueError):
                resolve_selection(json.dumps(payload), choices, {candidate.source_hash: source})
        self.assertIsNone(resolve_selection('{"evidenceId":"NONE"}', choices, {candidate.source_hash: source}))

    def test_changed_source_or_offsets_are_rejected(self):
        source = 'The original text.'
        candidate = evidence_sentences(source, 'r')[0]
        choices = {'S1': candidate}
        with self.assertRaisesRegex(ValueError, 'hash'):
            resolve_selection('{"evidenceId":"S1"}', choices, {candidate.source_hash: 'The changed text.'})
        choices['S1'] = replace(candidate, text='A different quote')
        with self.assertRaisesRegex(ValueError, 'offsets'):
            resolve_selection('{"evidenceId":"S1"}', choices, {candidate.source_hash: source})

    def test_schema_only_allows_candidate_ids_and_none(self):
        candidates = evidence_sentences('First sentence. Second sentence.', 'r')
        prompt, schema, choices = selector_request('Which sentence?', candidates, 'seed')
        self.assertEqual({'NONE', *choices}, set(json.loads(schema)['properties']['evidenceId']['enum']))
        self.assertEqual(choices, selector_request('Which sentence?', candidates, 'seed')[2])
        self.assertNotIn('expected', json.loads(prompt))

    def test_retrieval_uses_text_not_reference_metadata(self):
        candidates = evidence_sentences('Amina carried a lantern. Yusuf baked bread.', 'not searchable')
        index = EvidenceIndex(candidates)
        self.assertEqual('Yusuf baked bread.', index.search('Who baked bread?', 1)[0].text)
        self.assertEqual([], index.search('unknownterm', 5))
        self.assertEqual([], index.search('not searchable', 5))

    def test_duplicate_contexts_do_not_fill_retrieval_slots_or_hide_correct_evidence(self):
        source = 'Amina carried bread. Yusuf carried dates.'
        other = 'Yusuf carried dates. Amina carried bread.'
        candidates = evidence_sentences(source, 'one') + evidence_sentences(other, 'two')
        index = EvidenceIndex(candidates)
        self.assertEqual(2, len(index.documents))
        row = {'sourceText': other, 'sourceTextSha256': hashlib.sha256(other.encode()).hexdigest(),
               'messages': [{'content': json.dumps({'contentType': 'answer', 'answer': 'bread'})}]}
        self.assertTrue(retrieval_hit(row, candidates[0]))
        irrelevant = evidence_sentences('Bread was sold at the market.', 'three')[0]
        self.assertFalse(retrieval_hit(row, irrelevant))

    def test_span_reader_uses_original_offsets_and_sentence(self):
        source = 'He said, "Hire him." Then he left.'
        start = source.index('Hire')
        selected = resolve_span(source, 'original citation', start, start + len('Hire him'))
        self.assertEqual('He said, "Hire him."', selected.text)
        self.assertEqual('original citation', selected.reference)
        self.assertIsNone(resolve_span(source, 'r', 0, 0))
        for bounds in [(-1, 2), (1, 500), (4, 3)]:
            with self.assertRaises(ValueError):
                resolve_span(source, 'r', *bounds)

    def test_answerability_is_an_explicit_decision(self):
        self.assertTrue(resolve_answerability('{"contentType":"answer"}'))
        self.assertFalse(resolve_answerability('{"contentType":"unanswered"}'))
        for raw in ['{}', '[]', '{"contentType":"maybe"}', '{"contentType":"answer","answer":"invented"}']:
            with self.assertRaises(ValueError):
                resolve_answerability(raw)

    def test_abstaining_on_everything_cannot_score_as_correct(self):
        report = summarize([
            {'answerable': True, 'correct': False, 'selected': None, 'error': None, 'durationMs': 1},
            {'answerable': False, 'correct': True, 'selected': None, 'error': None, 'durationMs': 1},
            {'answerable': False, 'correct': False, 'selected': None, 'error': 'invalid', 'durationMs': 1},
        ])
        self.assertEqual(1 / 3, report['evidenceDecisionAccuracy'])
        self.assertEqual(0, report['answerableEvidenceRecall'])
        self.assertEqual(1, report['invalidOutputs'])


if __name__ == '__main__':
    unittest.main()
