import hashlib
import json
import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path

from training.quiz_model.build_reader_recovery_benchmark import build


class RecoveryBenchmarkTest(unittest.TestCase):
    def test_fresh_sources_are_disjoint_and_old_test_is_explicitly_development(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);sources=root/'sources';previous=root/'previous'
            sources.mkdir();previous.mkdir();db_path=sources/'quran_en.db'
            texts={1:'The old passage contains bread.',2:'The new calibration passage contains dates.',3:'The new test passage contains honey.'}
            with closing(sqlite3.connect(db_path)) as db:
                db.execute('CREATE TABLE ayahs (surah_number INTEGER, number_in_surah INTEGER, text TEXT)')
                for n,text in texts.items():db.execute('INSERT INTO ayahs VALUES (1,?,?)',(n,text))
                db.commit()
            old={'id':'old-test','benchmarkFamily':'source_checked','sourceTextSha256':hashlib.sha256(texts[1].encode()).hexdigest(),
                 'sourceText':texts[1],'messages':[{'content':'old question'},{'content':'old answer'}]}
            for split in ['train','validation','test']:
                (previous/f'{split}.jsonl').write_text(json.dumps(old)+'\n' if split=='test' else '')
            key='databases/quran/quran_en.db'
            spec={'sourceFiles':{key:hashlib.sha256(db_path.read_bytes()).hexdigest()},'reviewStatus':'fixture',
                  'sampling':'fixed fixture','seed':1,'cases':[]}
            for n,split,answer in [(2,'validation','dates'),(3,'test','honey')]:
                spec['cases'].append({'sourceKey':key,'reference':f'1:{n}','split':split,
                    'sourceTextSha256':hashlib.sha256(texts[n].encode()).hexdigest(),
                    'questions':[{'question':'What food is present?','answer':answer}]})
            specification=root/'spec.json';specification.write_text(json.dumps(spec));output=root/'out'
            manifest=build(sources,specification,previous,output)
            self.assertEqual({'validation':1,'test':1},manifest['counts'])
            self.assertEqual('development_only_no_longer_held_out',manifest['oldBenchmarkRole'])
            self.assertEqual('old-test',json.loads((output/'development.jsonl').read_text())['id'])
            validation=json.loads((output/'validation.jsonl').read_text());test=json.loads((output/'test.jsonl').read_text())
            self.assertNotEqual(validation['sourceTextSha256'],test['sourceTextSha256'])
            spec['cases'][0].update(reference='1:1',sourceTextSha256=old['sourceTextSha256'])
            specification.write_text(json.dumps(spec))
            with self.assertRaisesRegex(ValueError,'previously evaluated'):
                build(sources,specification,previous,output)


if __name__=='__main__':unittest.main()
