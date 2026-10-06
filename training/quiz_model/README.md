# Grounded Quran and Hadith learning pipeline

This pipeline creates source-linked questions and knowledge-card metadata from every eligible
English Quran ayah and every Hadith collection listed in the app asset manifest, including Sahih
al-Bukhari, Sahih Muslim, Musnad Ahmad, Muwatta Malik, the four Sunan collections, Sunan ad-Darimi,
and Shama'il At-Tirmidhi. Training and evaluation run locally. The final quantized model performs
inference on the phone; databases and training dependencies are not bundled into the model.

The output is marked `source_locked`. Quran Arabic and translations are copied verbatim—without
trimming, whitespace normalization, truncation, rewriting, or correction—and each receives a
SHA-256 hash. Validation fails if a Quran question does not contain the complete source translation,
if a knowledge card differs from its source text, or if canonical Arabic is missing. Question
records contain four unique options and answers derived from database metadata. Both content types
retain the exact source collection, reference, and topic.

```bash
python3 training/quiz_model/build_grounded_quiz_dataset.py \
  --quran /path/to/quran.db \
  --quran-translation /path/to/quran_en.db \
  --bukhari-json /path/to/sahih_bukhari.json \
  --shamayel /path/to/shamayele_tirmidhi_complete.db \
  --output training/quiz_model/output/candidates.jsonl
```

The Arabic Quran database is required as a provenance input even though this first English quiz
pass reads translated prompts from `quran_en.db`.

## Local Hugging Face training

The v1 edge model is `HuggingFaceTB/SmolLM2-135M-Instruct`, trained with LoRA. The generated
payload intentionally excludes the content ID, citation, source body, and Arabic. Those values stay
in a validated request envelope and are resolved directly from the immutable database row after
inference. A model therefore cannot replace or rewrite an ayah or narration.

```bash
python3 training/quiz_model/fetch_sources.py \
  --output-dir build/knowledge-model/all-sources

python3 training/quiz_model/build_grounded_quiz_dataset.py \
  --quran build/knowledge-model/all-sources/databases/quran/quran.db \
  --quran-translation build/knowledge-model/all-sources/databases/quran/quran_en.db \
  --bukhari-json build/knowledge-model/all-sources/databases/hadith/sahih_bukhari.json \
  --shamayel build/knowledge-model/all-sources/databases/hadith/shamayele_tirmidhi_complete.db \
  --hadith-db build/knowledge-model/all-sources/databases/hadith/musnad_ahmad.db \
  --hadith-db build/knowledge-model/all-sources/databases/hadith/muwatta_malik.db \
  --hadith-db build/knowledge-model/all-sources/databases/hadith/sahih_bukhari.db \
  --hadith-db build/knowledge-model/all-sources/databases/hadith/sahih_muslim.db \
  --hadith-db build/knowledge-model/all-sources/databases/hadith/sunan_abu_dawud.db \
  --hadith-db build/knowledge-model/all-sources/databases/hadith/sunan_darimi.db \
  --hadith-db build/knowledge-model/all-sources/databases/hadith/sunan_ibn_majah.db \
  --hadith-db build/knowledge-model/all-sources/databases/hadith/sunan_nasai.db \
  --hadith-db build/knowledge-model/all-sources/databases/hadith/sunan_tirmidhi.db \
  --output build/knowledge-model/all-candidates.jsonl

python3 training/quiz_model/prepare_hf_dataset.py \
  --input build/knowledge-model/all-candidates.jsonl \
  --output-dir build/knowledge-model/all-dataset

python3 -m venv .venv-knowledge-model
.venv-knowledge-model/bin/pip install -r training/quiz_model/requirements-hf.txt

HF_HOME=build/huggingface-cache .venv-knowledge-model/bin/python \
  training/quiz_model/train_hf_model.py \
  --data-dir build/knowledge-model/all-dataset \
  --output-dir build/knowledge-model/deenly-knowledge-v1 \
  --model-version deenly-knowledge-v1

HF_HOME=build/huggingface-cache .venv-knowledge-model/bin/python \
  training/quiz_model/evaluate_hf_model.py \
  --data build/knowledge-model/all-dataset/test.jsonl \
  --adapter build/knowledge-model/deenly-knowledge-v1/adapter \
  --output build/knowledge-model/deenly-knowledge-v1/test_report.json
```

Merge the adapter, convert to GGUF, and quantize for llama.cpp:

```bash
HF_HOME=build/huggingface-cache .venv-knowledge-model/bin/python \
  training/quiz_model/merge_hf_adapter.py \
  --adapter build/knowledge-model/deenly-knowledge-v1/adapter \
  --output-dir build/knowledge-model/deenly-knowledge-v1/merged

.venv-knowledge-model/bin/python build/llama.cpp/convert_hf_to_gguf.py \
  build/knowledge-model/deenly-knowledge-v1/merged \
  --outfile build/knowledge-model/deenly-knowledge-v1/deenly-knowledge-v1-f16.gguf \
  --outtype f16

build/llama.cpp/build-local/bin/llama-quantize \
  build/knowledge-model/deenly-knowledge-v1/deenly-knowledge-v1-f16.gguf \
  build/knowledge-model/deenly-knowledge-v1/deenly-knowledge-v1-q4_k_m.gguf \
  Q4_K_M
```

Then validate the exact quantized artifact:

```bash
.venv-knowledge-model/bin/python training/quiz_model/evaluate_gguf_model.py \
  --data build/knowledge-model/all-dataset/test.jsonl \
  --model build/knowledge-model/deenly-knowledge-v1/deenly-knowledge-v1-q4_k_m.gguf \
  --runner build/llama.cpp/build-local/bin/llama-completion \
  --output build/knowledge-model/deenly-knowledge-v1/gguf_test_report.json \
  --samples 30
```

The promoted v1 release record is in `releases/deenly-knowledge-v1.json`. Large model binaries and
downloaded source databases stay under the ignored `build/` directory. Production delivery should
publish the GGUF through the existing versioned asset CDN, verify its SHA-256 before activation,
and retain the previous model until the new artifact passes its device smoke test.

Android packages a stripped arm64 llama.cpp runner as `libdeenly_completion.so`. The
`DeenlyKnowledgeModel` wrapper verifies the full source-text hash and model hash, executes locally,
accepts only the bounded JSON schema, and returns null to the deterministic content bank on any
missing artifact, timeout, runtime failure, or validation error. A debug-only broadcast receiver
provides an app-process smoke test without exposing unreviewed generated content in the UI.

Each new version reuses the fixed held-out split and should mix prior reviewed examples with newly
approved examples. Never fine-tune only on the newest batch. Promote a version only when its test
report passes the source-lock validator; otherwise the app continues using its prior model and
deterministic content bank.

## V2 self-contained question model

V2 uses `Qwen/Qwen2.5-0.5B-Instruct`. Its small semantic-contract dataset contains ordinary,
non-religious sentences and teaches only the output contract: self-contained wording, exact answer
and evidence spans, and four unique options. Quran and Hadith knowledge is supplied at runtime from
the verified local database envelope and is never learned from generated replacements.

```bash
python3 training/quiz_model/build_semantic_contract_dataset.py \
  --output-dir build/knowledge-model/semantic-contract-v2

HF_HOME=build/huggingface-cache .venv-knowledge-model/bin/python \
  training/quiz_model/train_hf_model.py \
  --data-dir build/knowledge-model/semantic-contract-v2 \
  --output-dir build/knowledge-model/deenly-question-v2-trained \
  --model-version deenly-question-v2 \
  --max-train-samples 313 \
  --max-eval-samples 35 \
  --max-steps 120

HF_HOME=build/huggingface-cache .venv-knowledge-model/bin/python \
  training/quiz_model/merge_hf_adapter.py \
  --adapter build/knowledge-model/deenly-question-v2-trained/adapter \
  --output-dir build/knowledge-model/deenly-question-v2-trained/merged

.venv-knowledge-model/bin/python build/llama.cpp/convert_hf_to_gguf.py \
  build/knowledge-model/deenly-question-v2-trained/merged \
  --outfile build/knowledge-model/deenly-question-v2-trained/deenly-question-v2-f16.gguf \
  --outtype f16

build/llama.cpp/build-local/bin/llama-quantize \
  build/knowledge-model/deenly-question-v2-trained/deenly-question-v2-f16.gguf \
  build/knowledge-model/deenly-question-v2-trained/deenly-question-v2-q4_k_m.gguf \
  Q4_K_M

.venv-knowledge-model/bin/python training/quiz_model/evaluate_gguf_model.py \
  --data build/knowledge-model/semantic-contract-v2/test.jsonl \
  --model build/knowledge-model/deenly-question-v2-trained/deenly-question-v2-q4_k_m.gguf \
  --runner build/llama.cpp/build-local/bin/llama-completion \
  --output build/knowledge-model/deenly-question-v2-trained/semantic_contract_report.json \
  --samples 48
```

The GGUF evaluator mirrors the Android invocation: the same system prompt, task prompt, JSON
schema, single-turn mode, token limit, and temperature. This prevents a raw prompt test from being
mistaken for deployable-runtime behavior.

Do not treat free-form generated answers as authoritative. The mobile model creates only bounded
learning metadata. It never emits Quran Arabic, IDs, citations, or replacement source bodies; a
question answer and its evidence are accepted only when both are exact spans of the verified
runtime source. New source-locked content is built and verified offline; clients keep using the
last valid deterministic content when validation fails.
