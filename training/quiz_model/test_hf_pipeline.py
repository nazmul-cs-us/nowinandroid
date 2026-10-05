import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from training.quiz_model.evaluate_gguf_model import balanced_sample, chatml_prompt
from training.quiz_model.prepare_hf_dataset import build_example, prepare, split_for
from training.quiz_model.validate_generated_content import validate_output


def candidate(content_type="knowledge"):
    text = "  exact source\ntext  "
    result = {
        "id": f"quran-1-1-{content_type}",
        "contentType": content_type,
        "sourceCollection": "quran",
        "sourceReference": "Quran 1:1",
        "sourceText": text,
        "sourceTextSha256": hashlib.sha256(text.encode()).hexdigest(),
        "sourceArabicSha256": hashlib.sha256("Arabic".encode()).hexdigest(),
        "topic": "Opening",
    }
    if content_type == "knowledge":
        result.update(title="From Al-Fatihah", body=text)
    else:
        result.update(
            prompt=f"Which surah?\n{text}",
            options=["One", "Two", "Three", "Four"],
            correctOption=0,
            explanation="Quran 1:1 is in One.",
        )
    return result


class HuggingFacePipelineTest(unittest.TestCase):
    def test_reference_pairs_never_leak_between_splits(self):
        self.assertEqual(split_for(candidate("knowledge")), split_for(candidate("question")))

    def test_prepares_manifest_and_preserves_exact_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "candidates.jsonl"
            source.write_text(json.dumps(candidate()) + "\n", encoding="utf-8")
            manifest = prepare(source, root / "dataset", max_source_chars=10)
            self.assertEqual(1, sum(manifest["counts"].values()))
            split = split_for(candidate())
            item = json.loads((root / "dataset" / f"{split}.jsonl").read_text())
            answer = json.loads(item["messages"][-1]["content"])
            self.assertNotIn("body", answer)
            self.assertNotIn("contentId", answer)
            self.assertNotIn("sourceReference", answer)
            self.assertNotIn("sourceArabic", answer)

    def test_validator_rejects_changed_quran_and_generated_arabic(self):
        example = build_example(candidate(), max_source_chars=100)
        valid = example["messages"][-1]["content"]
        self.assertEqual((True, "accepted"), validate_output(example, valid))

        changed = json.loads(valid)
        changed["body"] = "generated source text"
        self.assertEqual(
            "model_generated_source_text_forbidden",
            validate_output(example, json.dumps(changed))[1],
        )

        changed = json.loads(valid)
        changed["arabicText"] = "generated"
        self.assertEqual(
            "model_generated_arabic_forbidden",
            validate_output(example, json.dumps(changed))[1],
        )

    def test_gguf_prompt_excludes_expected_answer_and_sampling_is_balanced(self):
        knowledge = build_example(candidate("knowledge"), max_source_chars=100)
        question_candidate = candidate("question")
        question_candidate["sourceCollection"] = "sahih_muslim"
        question = build_example(question_candidate, max_source_chars=100)

        prompt = chatml_prompt(knowledge)
        self.assertTrue(prompt.endswith("<|im_start|>assistant\n"))
        self.assertNotIn(knowledge["messages"][-1]["content"], prompt)

        selected = balanced_sample([knowledge] * 4 + [question] * 4, count=2, seed=7)
        self.assertEqual({"quran", "sahih_muslim"}, {item["sourceCollection"] for item in selected})


if __name__ == "__main__":
    unittest.main()
