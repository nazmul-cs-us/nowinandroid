import copy
import unittest

from training.quiz_model.calibrate_extractive_reader import apply_threshold, calibrate, metrics, meets_targets


def row(score, answerable=True, correct=True, family='source_checked', error=None):
    return {'id': 'case', 'family': family, 'answerable': answerable, 'correct': correct,
            'selected': {'text': 'original quote'}, 'spanPrediction': {'score': score},
            'error': error, 'shortAnswerCorrect': correct, 'durationMs': 1}


class ReaderCalibrationTest(unittest.TestCase):
    def test_threshold_fixes_false_answers_without_mutating_raw_predictions(self):
        rows = [row(.95), row(.20, False, False)]
        original = copy.deepcopy(rows)
        result = apply_threshold(rows, .5)
        self.assertIsNone(result[1]['selected'])
        self.assertTrue(result[1]['correct'])
        self.assertEqual(original, rows)

    def test_invalid_predictions_do_not_become_correct_abstentions(self):
        result = apply_threshold([row(.1, False, False, error='bad offsets')], .5)
        self.assertFalse(result[0]['correct'])

    def test_cannot_reach_target_by_abstaining_on_all_positives(self):
        report = {'results': [row(.1), row(.9, False, False)]}
        self.assertFalse(calibrate(report)['eligible'])

    def test_synthetic_success_cannot_hide_domain_regression(self):
        rows = [row(.99, family='synthetic_contract') for _ in range(100)] + [row(.8, False, False)]
        self.assertFalse(calibrate({'results': rows})['eligible'])

    def test_eligible_cutoff_preserves_correct_recall(self):
        result = calibrate({'results': [row(.9), row(.2, False, False)]})
        self.assertTrue(result['eligible'])
        self.assertGreater(result['threshold'], .2)
        self.assertLess(result['threshold'], .9)
        self.assertEqual(1, result['summary']['answerableEvidenceRecall'])

    def test_small_family_recall_is_reported_and_cannot_be_reduced_by_calibration(self):
        rows = [row(.99, family='synthetic_contract') for _ in range(100)]
        rows += [row(.8) for _ in range(8)]
        missed = row(.95, True, False)
        missed['selected'] = None
        rows.append(missed)
        for _ in range(5):
            rejected = row(.95, False, True)
            rejected['selected'] = None
            rows.append(rejected)
        result = calibrate({'results': rows})
        self.assertTrue(result['eligible'])
        self.assertEqual(8 / 9, result['byFamily']['source_checked']['answerableEvidenceRecall'])
        self.assertEqual(8 / 9, result['uncalibrated']['byFamily']['source_checked']['answerableEvidenceRecall'])

    def test_invalid_threshold_and_scores_fail_closed(self):
        for threshold in [-1, 2, float('nan')]:
            with self.assertRaises(ValueError):
                apply_threshold([], threshold)
        with self.assertRaises(ValueError):
            calibrate({'results': [row(float('nan'))]})


if __name__ == '__main__':
    unittest.main()
