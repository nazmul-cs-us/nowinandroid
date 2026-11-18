import hashlib
import json
import tarfile
import tempfile
import unittest
from pathlib import Path
from training.quiz_model.package_source_reader import MODEL_FILES,package,sha256


class PackageReaderTest(unittest.TestCase):
    def fixture(self,root):
        model=root/'model';model.mkdir()
        for name in MODEL_FILES:(model/name).write_bytes(b'fixture')
        (model/'config.json').write_text(json.dumps({'architectures':['RobertaForQuestionAnswering']}))
        (model/'training_manifest.json').write_text('{}')
        calibration=root/'calibration.json'
        calibration.write_text(json.dumps({'selected':{'predictionMethod':'span-margin','revision':'local-sha256:'+sha256(model/'model.safetensors'),'threshold':.2},'holdoutDataSha256':'held-out'}))
        report=root/'report.json'
        report.write_text(json.dumps({'calibrationSha256':sha256(calibration),'testDataSha256':'held-out',
            'summary':{'samples':60,'evidenceDecisionAccuracy':.96,'answerPrecision':.97,'answerableEvidenceRecall':.97},
            'normalizedShortAnswerAccuracy':.76}))
        assessment=root/'assessment.json'
        assessment.write_text(json.dumps({'readerEvidenceGatePassed':True,'holdoutReportSha256':sha256(report)}))
        return model,calibration,assessment,report

    def test_reference_package_is_hash_verified_and_never_marked_active(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);args=self.fixture(root)
            release=package(*args,root/'output','reader-v1')
            self.assertFalse(release['delivery']['activeInApp']);self.assertFalse(release['delivery']['published'])
            archive=root/'output'/release['artifact']['fileName']
            self.assertEqual(release['artifact']['sha256'],sha256(archive))
            with tarfile.open(archive) as tar:
                config=json.load(tar.extractfile('reader-v1/reader_config.json'))
                self.assertEqual('original_source_quote_and_citation_only',config['outputPolicy'])
                self.assertFalse(config['standaloneShortAnswerApproved'])
            with self.assertRaisesRegex(ValueError,'already exists'):
                package(*args,root/'output','reader-v1')

    def test_changed_weights_cannot_be_released_as_validated(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);args=self.fixture(root)
            (args[0]/'model.safetensors').write_bytes(b'changed weights')
            with self.assertRaisesRegex(ValueError,'Weights do not match'):
                package(*args,root/'output','reader-v1')


if __name__=='__main__':unittest.main()
