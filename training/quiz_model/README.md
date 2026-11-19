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

The promoted artifact is recorded in `releases/deenly-question-v2.json`. Cloudflare's authenticated
object uploader caps individual uploads at 300 MiB, so the 397,807,648-byte GGUF is delivered as
two independently hashed CDN parts in the `model_now_nudge` category. Android downloads both parts,
assembles them atomically, verifies the final GGUF SHA-256, and deletes the parts before activation.
The model remains optional so first launch is not blocked by a 398 MB download.

The current quantized held-out report accepts 46 of 48 samples after applying the same bounded
option repair used by Android. The remaining two outputs are rejected safely: one has insufficient
unique options and one has evidence that is not an exact source span.

Do not treat free-form generated answers as authoritative. The mobile model creates only bounded
learning metadata. It never emits Quran Arabic, IDs, citations, or replacement source bodies; a
question answer and its evidence are accepted only when both are exact spans of the verified
runtime source. New source-locked content is built and verified offline; clients keep using the
last valid deterministic content when validation fails.

## Source-checked knowledge retraining

The legacy topical answer generator is disabled. Keyword overlap does not establish
that a passage answers a question, and slicing a fixed-size answer window can cut
words or omit the meaning of the sentence. `build_answer_contract_dataset.py`
therefore refuses nonzero automatic corpus targets. Its synthetic-only mode remains
available. Do not reuse `answer-contract-v2` as a correctness benchmark.

The current knowledge pipeline uses explicit questions in
`datasets/answer-source-checks-v1/questions.json`. It fetches the app's databases
with size/SHA-256 verification, then resolves the specified rows without rewriting
their English text. Every answer must be an exact source span. The questions were
checked against the provided passages by the coding agent; they are a small
extractive-reading seed, not an expert-reviewed or comprehensive religious benchmark.
Do not automatically infer that another passage is unanswerable just because a
keyword is absent.

```bash
RUN_TASKS=knowledge \
BASE_MODEL=Qwen/Qwen2.5-1.5B-Instruct \
ANSWER_VERSION=deenly-knowledge-v6 \
bash training/quiz_model/run_remote_training.sh
```

Use a new version for different data, model, trainer, or budgets. The script records
those settings beside the checkpoints and refuses incompatible resumes. `RUN_TASKS`
also supports `question` and `both`; the question task retains its separate legacy
contract dataset and is not improved or factually certified by the knowledge run.

Generated data and artifacts stay under `build/knowledge-model/`. Source passages
and all variants of a synthetic target sentence stay within one split, including
when used as distractor context. Scarce source-checked training pairs are weighted
8x; repeats are recorded as weighting, not new independent observations. Validation
and test examples are never repeated for weighting. Prompt overflow fails explicitly
instead of silently dropping source text or the assistant boundary.

The evaluator reports exact reference matching, normalized matching, token F1,
incorrect abstentions, and separate source-reading/synthetic results. Source-span
contract acceptance remains distinct from correctness. An abstention on an
answerable question or an irrelevant copied answer does not count as correct.
Each report records the evaluated dataset hash. Exact matching is conservative and
may reject a semantically valid alternative; token F1 is supplemental rather than
proof of relevance or factual correctness.

Both f16 and Q4_K_M artifacts are evaluated on validation to expose quantization
regressions. The Q4 candidate must exceed 90% on contract acceptance, reference
matching, and answerable-only exact matching, including each benchmark family,
before test evaluation runs. A failed validation run stops without another look
at the test set. Passing this small benchmark does not establish 90% accuracy on
all app questions and never updates or publishes the app models automatically.
Historical scores on other datasets are not directly comparable. Compare an old
and new candidate on the same frozen benchmark, and disclose any prior exposure
the old model had to its source passages.

```bash
python3 -m unittest training.quiz_model.test_training_quality \
  training.quiz_model.test_hf_pipeline training.quiz_model.test_build_grounded_quiz_dataset
```

## Evidence-selection prototype

`evidence_selector.py` separates selection from text generation. The model receives
numbered source sentences and a question, and can return only a candidate ID or
`NONE`. The resolver reads the selected character range from the original text,
checks its SHA-256 and offsets, and supplies the stored citation. Model-written
quotes, additional JSON fields, unknown IDs, and changed sources are rejected.

The first experiment uses the existing v6 weights without another training run:

```bash
.venv-knowledge-model/bin/python training/quiz_model/evaluate_evidence_selector.py \
  --data build/knowledge-model/answer-source-checked-v1/validation.jsonl \
  --model build/knowledge-model/deenly-knowledge-v6-trained/deenly-knowledge-v6-q4_k_m.gguf \
  --runner build/llama.cpp/build-local/bin/llama-completion \
  --sources build/knowledge-model/verified-sources \
  --generation-report build/knowledge-model/deenly-knowledge-v6-trained/gguf_validation_q4_k_m_report.json \
  --output build/knowledge-model/evidence-selector-v1/validation_report.json
```

This deliberately measures two different components:

- **Known-context selection:** the selector sees sentences from the supplied
  passage. It must select a sentence containing the reference answer, or abstain
  for an unanswerable question. Report decision accuracy, answer precision,
  answerable evidence recall, coverage, and invalid outputs separately by family.
- **Global retrieval:** a simple BM25 baseline searches the verified Quran and
  Shama'il databases plus synthetic train/validation documents. Report evidence
  recall@8 on answerable queries only. Identical sentence text is deduplicated so
  overlapping contexts cannot fill the result list. Report original-passage recall
  separately: the same supporting sentence may legitimately occur in several
  contexts. Context-specific unanswerable labels cannot determine whether some
  other passage in the full corpus answers the question. Some questions depend on
  their original context or admit other valid corpus answers; this retrieval score
  is a target-snippet recovery diagnostic, not global answer correctness.

Candidate order is deterministically shuffled without using the target answer.
The test set is not opened. The supplied generation report must have the same
validation-data hash. Sentence selection and exact short-answer generation use
different output units, so their accuracy values are diagnostic rather than
interchangeable. These are component measurements, **not end-to-end search-and-answer
accuracy**. An end-to-end benchmark needs separately checked search queries,
relevant passages, and genuinely unanswerable corpus-wide queries. The Android app
continues using its existing implementation during this experiment.

```bash
python3 -m unittest training.quiz_model.test_evidence_selector
```

The optional `--answerability-gate` experiment first asks the same model for only
`contentType: answer|unanswered`; sentence selection runs only after an `answer`
decision. This tests whether a separate abstention decision helps. Gate decisions,
number of model calls, coverage, and overall correctness are all reported, so
abstaining on everything cannot improve the score artificially. Keep the ungated
and gated reports in separate output directories for comparison.

A purpose-trained span reader can also be measured against the same validation
cases without modifying or training the generative model:

```bash
HF_HOME=build/huggingface-cache .venv-knowledge-model/bin/python \
  training/quiz_model/evaluate_extractive_reader.py \
  --data build/knowledge-model/answer-source-checked-v1/validation.jsonl \
  --output build/knowledge-model/evidence-extractive-reader-v1/validation_report.json
```

The default model is `deepset/minilm-uncased-squad2`, a 33M-parameter English
extractive reader trained on SQuAD 2.0. The report pins its repository revision
and records its model-card attribution/license. It predicts character positions;
the resolver returns an original source sentence and citation. It is an offline
prototype, not an Android artifact. Its pretrained no-answer decision is used
without tuning a confidence threshold. Report normalized short-answer matching
separately from sentence-evidence correctness, and inspect answer precision as
well as overall accuracy. Timing excludes model loading. A good known-context
score does not certify corpus retrieval, domain-wide factual accuracy, or Android
performance. See the model card: https://huggingface.co/deepset/minilm-uncased-squad2.

## Calibrate precision without sacrificing useful answers

Install `requirements-qa.txt` to include the optional DeBERTa tokenizer dependency.
Evaluate candidate readers on validation, then freeze a reader/revision/cutoff:

```bash
python3 training/quiz_model/calibrate_extractive_reader.py \
  --reports build/knowledge-model/evidence-extractive-reader-v1/validation_report.json \
    build/knowledge-model/evidence-roberta-v1/validation_report.json \
    build/knowledge-model/evidence-deberta-v1/validation_report.json \
  --validation-data build/knowledge-model/answer-source-checked-v1/validation.jsonl \
  --holdout-data build/knowledge-model/answer-source-checked-v1/test.jsonl \
  --output build/knowledge-model/evidence-reader-calibrated-v1/calibration.json
```

Selection requires >=90% answer precision and evidence-decision accuracy overall
and in each family, >=90% answerable recall overall, and no loss of any family's
uncalibrated recall from applying the cutoff. The last condition does not mean
every family has >=90% recall: a model that already misses 1 of 9 source questions
still has 88.9% recall, which must be reported rather than rounded up. Among
qualified settings, choose maximum correct-answer recall, then decision accuracy,
precision, and smaller model size. The calibration artifact records all candidates,
validation/report hashes, pinned model revision, and a holdout fingerprint. It
hashes holdout bytes but never loads/scans its labels for selection.

Evaluate the selected configuration on the holdout once:

```bash
HF_HOME=build/huggingface-cache .venv-knowledge-model/bin/python \
  training/quiz_model/evaluate_extractive_reader.py \
  --data build/knowledge-model/answer-source-checked-v1/test.jsonl \
  --calibration build/knowledge-model/evidence-reader-calibrated-v1/calibration.json \
  --output build/knowledge-model/evidence-reader-calibrated-v1/test_report.json
```

Test evaluation requires a frozen configuration with the matching data hash.
Never revise a cutoff or choose another model using the holdout result and still
call the revised score held-out. Subsequent development needs a fresh benchmark.
A precision gain can coexist with poor source-question recall; inspect every
family and block promotion when the source-reading accuracy/recall gate fails.

```bash
python3 -m unittest training.quiz_model.test_reader_calibration
```

## Recovering false abstentions with a new source-only benchmark

The first calibrated reader's holdout has been consumed. Its 89 distinct
source-reading examples are now **development/training**, not held-out evidence.
`datasets/answer-source-checks-v2/questions.json` freezes 40 newly sampled source
references (24 Quran, 16 Shama'il; 80–280 characters), excluding previous answer
benchmark references before sampling. Its 20 validation and 20 test passages each
have two source-supported questions and one explicit missing-detail question.
Every source/database hash is pinned; text is never rewritten or truncated.

```bash
python3 training/quiz_model/build_reader_recovery_benchmark.py \
  --sources build/knowledge-model/verified-sources \
  --specification training/quiz_model/datasets/answer-source-checks-v2/questions.json \
  --previous build/knowledge-model/answer-source-checked-v1 \
  --output build/knowledge-model/reader-recovery-v2
```

`train_source_reader.py` adapts the start/end reader on retired development data
plus training-only synthetics. It never loads fresh validation/test passages.
Its fixed two epochs, base revision, seed, weighting, learning rate, data hashes,
and runtime are recorded beside the local safetensors weights.

```bash
HF_HOME=build/huggingface-cache .venv-knowledge-model/bin/python \
  training/quiz_model/train_source_reader.py \
  --development build/knowledge-model/reader-recovery-v2/development.jsonl \
  --synthetic build/knowledge-model/answer-source-checked-v1/train.jsonl \
  --model deepset/roberta-base-squad2 \
  --revision adc3b06f79f797d1c575d5479d6f5efe54a9e3b4 \
  --output build/knowledge-model/roberta-source-reader-v2
```

`--prediction-method span-margin` retains the best non-null source span even when
CLS initially wins. `source_margin_reader.py` masks question/special tokens,
keeps valid start/end ordering, aligns to original word offsets, and exposes a
monotonic sigmoid of `best_start_logit + best_end_logit - null_start_logit -
null_end_logit`. This score is **not a calibrated probability**. A single cutoff
is selected on fresh validation, allowing recovery of good low-absolute-confidence
spans without stacking two incompatible rejection rules. This follows the
[standard SQuAD2 answer-versus-null comparison](https://github.com/huggingface/transformers/blob/main/src/transformers/data/metrics/squad_metrics.py).

Evaluate candidates on `reader-recovery-v2/validation.jsonl`, run
`calibrate_extractive_reader.py` with that dataset's validation/test fingerprints,
then evaluate the selected configuration on `test.jsonl` once. The calibration
records both model revision (a local safetensors SHA-256 for adapted models) and
prediction method, so a margin cutoff cannot silently be applied to the old
pipeline confidence score. The previous fixed reader can be scored on the same
fresh test using inherited parameters and matching benchmark fingerprints.

The `reader-recovery-v2/results.md` artifact distinguishes source-sentence evidence
accuracy from normalized exact short-answer accuracy. **Display the original
source quote/citation; do not present the model's raw extracted phrase as a
standalone verified answer.** Correct sentence selection does not certify that
phrase, corpus retrieval, app-wide accuracy, or Android performance.

## Versioned reader release bundle

Published model artifacts use a `-vN` suffix immediately before the file extension
(for example, `deenly-source-reader-v1.tar.gz`) and a matching versioned CDN
directory. A new set of weights, tokenizer, or calibration gets a new version;
published versions are retained so installed clients keep their pinned files.

`package_source_reader.py` packages only a qualified, checksum-matched RoBERTa
source-quote reader. It binds the safetensors weights to the frozen calibration,
holdout report, and assessment; includes tokenizer files, attribution, and per-file
hashes; and produces a deterministic archive under the ignored build directory.
It does not upload anything or change an active app model.

```bash
.venv-knowledge-model/bin/python training/quiz_model/package_source_reader.py \
  --model build/knowledge-model/roberta-source-reader-v2 \
  --calibration build/knowledge-model/reader-recovery-v2/calibration.json \
  --assessment build/knowledge-model/reader-recovery-v2/assessment.json \
  --report build/knowledge-model/reader-recovery-v2/holdout_report.json \
  --output build/knowledge-model/releases \
  --version deenly-source-reader-v1 \
  --release-record training/quiz_model/releases/deenly-source-reader-v1.json
```

The release record explicitly marks this as a PyTorch reference artifact with
Android integration pending. Its weights cannot be substituted into llama.cpp's
GGUF loader. A compatible reader runtime/tokenizer must be integrated and tested
before publishing it as an active Android model. Large archives stay outside Git;
source, tests, and the versioned checksum/evaluation record belong in the repository.
