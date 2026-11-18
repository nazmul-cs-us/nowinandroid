import hashlib
from contextlib import closing
import json
import tempfile
import unittest
from pathlib import Path

from training.quiz_model.check_training_quality import failures
from training.quiz_model.build_answer_contract_dataset import corpus_records, answer_clause, topic_for
from training.quiz_model.build_checked_answer_dataset import build
from training.quiz_model.train_hf_model import encode_example
from training.quiz_model.evaluate_gguf_model import answer_f1
from training.quiz_model.evaluate_gguf_model import reference_answer_score, summarize_answer_quality
from training.quiz_model.prepare_remote_training_data import prepare, rejection_reason
from training.quiz_model.validate_generated_content import validate_output


def example(source="Aisha carried bread. Hamza carried dates.", answer="bread"):
    return {"sourceText": source, "sourceTextSha256": hashlib.sha256(source.encode()).hexdigest(),
            "messages": [{"role": "system", "content": "Use the source."},
                         {"role": "user", "content": "What did Aisha carry? " + source},
                         {"role": "assistant", "content": json.dumps({"contentType": "answer", "answer": answer, "evidence": source})}]}


class TrainingQualityTest(unittest.TestCase):
    def test_safe_abstention_is_not_counted_as_a_correct_answer(self):
        row = example()
        generated = json.dumps({"contentType": "unanswered", "answer": "", "evidence": ""})
        accepted, _ = validate_output(row, generated)
        self.assertTrue(accepted)
        quality = summarize_answer_quality([{"answerQuality": reference_answer_score(row, generated, accepted)}])
        self.assertEqual(0, quality["answerableExactMatchRate"])
        self.assertEqual(1, quality["incorrectAbstentions"])

    def test_wrong_but_sourced_answer_fails_reference_matching(self):
        row = example()
        generated = json.dumps({"contentType": "answer", "answer": "dates", "evidence": row["sourceText"]})
        accepted, _ = validate_output(row, generated)
        self.assertTrue(accepted)
        self.assertFalse(reference_answer_score(row, generated, accepted)["referenceMatch"])
        correct = row["messages"][-1]["content"]
        self.assertTrue(reference_answer_score(row, correct, True)["referenceMatch"])

    def test_correct_abstention_and_invalid_json_are_distinguished(self):
        row = example()
        target = json.dumps({"contentType": "unanswered", "answer": "", "evidence": ""})
        row["messages"][-1]["content"] = target
        self.assertTrue(reference_answer_score(row, target, True)["referenceMatch"])
        self.assertFalse(reference_answer_score(row, "[]", False)["referenceMatch"])

    def test_gate_requires_more_than_ninety_and_answerable_accuracy(self):
        question = {"acceptanceRate": .95}
        knowledge = {"acceptanceRate": 1, "answerQuality": {"referenceMatchRate": .95, "answerableExactMatchRate": .90}}
        self.assertTrue(failures(question, knowledge))
        knowledge["answerQuality"]["answerableExactMatchRate"] = .95
        self.assertEqual([], failures(question, knowledge))
        self.assertTrue(failures(question, {"acceptanceRate": 1}))

    def test_synthetic_success_cannot_hide_domain_failure(self):
        quality = {"referenceMatchRate": .99, "answerableExactMatchRate": .99}
        report = {"acceptanceRate": .99, "answerQuality": quality,
                  "answerQualityByFamily": {"synthetic_contract": quality,
                      "source_checked": {"referenceMatchRate": .50, "answerableExactMatchRate": .50}}}
        self.assertTrue(any("source_checked" in issue for issue in failures(None, report)))
        self.assertTrue(failures(None, None))

    def test_preparation_excludes_placeholders_and_held_out_passages(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, output = root / "source", root / "output"
            source.mkdir()
            test = example("Aisha carried apples.", "apples")
            valid = example("Aisha carried oranges.", "oranges")
            validation = example("Aisha carried pears.", "pears")
            bad = example(answer="<english>")
            self.assertEqual("placeholder_target", rejection_reason(bad))
            for split, rows in {"test": [test], "validation": [validation], "train": [valid, bad, test]}.items():
                (source / f"{split}.jsonl").write_text("".join(json.dumps(r) + "\n" for r in rows))
            manifest = prepare(source, output)
            self.assertEqual({"train": 1, "validation": 1, "test": 1}, manifest["counts"])
            self.assertEqual(1, manifest["rejected"]["train"]["source_passage_in_held_out_split"])
            self.assertEqual(test, json.loads((output / "test.jsonl").read_text()))

    def test_topical_generation_and_arbitrary_answer_windows_are_disabled(self):
        with self.assertRaisesRegex(ValueError, "Automatic topical"):
            corpus_records([], 1, 0, 700)
        self.assertIsNone(topic_for("He gave you life."))
        self.assertIsNone(answer_clause("A very long sentence " * 20 + "patience " + "continues " * 20, "patience"))

    def test_training_never_silently_truncates_the_prompt(self):
        class Tokenizer:
            eos_token = "!"
            def apply_chat_template(self, messages, **kwargs):
                return messages[-1]["content"] + "<assistant>"
            def __call__(self, text, **kwargs):
                return {"input_ids": list(text.encode())}
        row = {"id": "too-long", "messages": [{"content": "x" * 600}, {"content": "{}"}]}
        with self.assertRaisesRegex(ValueError, "never truncate"):
            encode_example(Tokenizer(), row, 512)
        row["messages"][0]["content"] = "source"
        encoded = encode_example(Tokenizer(), row, 512)
        self.assertTrue(bytes(encoded["input_ids"]).decode().startswith("source<assistant>"))

    def test_f1_allows_punctuation_variants_but_not_irrelevant_answers(self):
        self.assertEqual(1, answer_f1("The bread.", "bread"))
        self.assertEqual(0, answer_f1("dates", "bread"))

    def test_checked_dataset_preserves_sources_and_groups_and_checks_hashes(self):
        import sqlite3
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            db_path = root / "quran_en.db"
            with closing(sqlite3.connect(db_path)) as db:
                db.execute("CREATE TABLE ayahs (surah_number INTEGER, number_in_surah INTEGER, text TEXT)")
                for number in [1, 2, 3]:
                    db.execute("INSERT INTO ayahs VALUES (1, ?, ?)", (number, f"  Source {number} has bread.\n"))
                db.commit()
            key = "databases/quran/quran_en.db"
            lock = {"sources": [{"key": key, "sha256": hashlib.sha256(db_path.read_bytes()).hexdigest()}]}
            (root / "sources.lock.json").write_text(json.dumps(lock))
            spec = {"scope": "test fixture", "reviewStatus": "fixture",
                    "sourceFiles": {key: lock["sources"][0]["sha256"]}, "cases": [
                {"sourceKey": key, "reference": f"1:{n}", "split": split,
                 "sourceTextSha256": hashlib.sha256(f"  Source {n} has bread.\n".encode()).hexdigest(),
                 "questions": [{"question": "What food is present?", "answer": "bread"}]}
                for n, split in enumerate(["train", "validation", "test"], 1)]}
            specification = root / "questions.json"
            specification.write_text(json.dumps(spec))
            out = root / "out"
            build(root, specification, out)
            source_sets = {}
            all_rows = {}
            for split in ["train", "validation", "test"]:
                rows = [json.loads(x) for x in (out / f"{split}.jsonl").read_text().splitlines()]
                all_rows[split] = rows
                source_sets[split] = {r["sourceTextSha256"] for r in rows}
                domain = next(r for r in rows if r["benchmarkFamily"] == "source_checked")
                self.assertTrue(domain["sourceText"].startswith("  Source"))
                self.assertTrue(domain["sourceText"].endswith("\n"))
            self.assertFalse(source_sets["train"] & source_sets["test"])
            self.assertFalse(source_sets["train"] & source_sets["validation"])
            # An underlying sentence cannot leak as a distractor in another split.
            for split, rows in all_rows.items():
                other_sources = "\n".join(r["sourceText"] for s, rr in all_rows.items() if s != split for r in rr)
                for row in rows:
                    if row["benchmarkFamily"] == "synthetic_contract":
                        payload = json.loads(row["messages"][-1]["content"])
                        if payload["contentType"] == "answer":
                            self.assertNotIn(payload["evidence"], other_sources)
            db_path.write_bytes(b"modified source")
            with self.assertRaisesRegex(ValueError, "checksum"):
                build(root, specification, out)


if __name__ == "__main__":
    unittest.main()
