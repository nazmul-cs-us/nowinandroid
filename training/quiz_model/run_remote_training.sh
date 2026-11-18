#!/usr/bin/env bash
# Train/evaluate source-grounded model candidates; never updates the app's models.
# RUN_TASKS=knowledge BASE_MODEL=Qwen/Qwen2.5-1.5B-Instruct \
#   ANSWER_VERSION=deenly-knowledge-v6 bash training/quiz_model/run_remote_training.sh
# Default: both tasks, with the 0.5B base. All large artifacts stay under build/.
set -euo pipefail
cd "$(dirname "$0")/../.."
ROOT="$(pwd)"
KM="$ROOT/build/knowledge-model"
LLAMA_DIR="$ROOT/build/llama.cpp"
LLAMA_COMMIT="4d756bc"
export HF_HOME="$ROOT/build/huggingface-cache"
RUN_TASKS="${RUN_TASKS:-both}"
BASE_MODEL="${BASE_MODEL:-Qwen/Qwen2.5-0.5B-Instruct}"
QUESTION_VERSION="${QUESTION_VERSION:-deenly-question-v6}"
ANSWER_VERSION="${ANSWER_VERSION:-deenly-knowledge-v5}"
ANSWER_STEPS="${ANSWER_STEPS:-600}"
QUESTION_DATA="$KM/question-combined-v4-quality-v1"
ANSWER_DATA="$KM/answer-source-checked-v1"
case "$RUN_TASKS" in both|question|knowledge) ;; *) echo "RUN_TASKS must be both, question, or knowledge" >&2; exit 2;; esac
mkdir -p "$KM"
if [ ! -x .venv-knowledge-model/bin/python ]; then
  PYTHON="$(command -v python3.11 || command -v python3.12 || command -v python3)"
  "$PYTHON" -m venv .venv-knowledge-model
  .venv-knowledge-model/bin/pip install --upgrade pip -q
fi
PY=".venv-knowledge-model/bin/python"
if ! "$PY" -c 'import torch, transformers, peft, accelerate, sentencepiece' >/dev/null 2>&1; then
  .venv-knowledge-model/bin/pip install -r training/quiz_model/requirements-hf.txt
fi
versions=()
if [ "$RUN_TASKS" != knowledge ]; then
  "$PY" training/quiz_model/prepare_remote_training_data.py \
    --source "$ROOT/training/quiz_model/datasets/question-combined-v4" --output "$QUESTION_DATA"
  versions+=("$QUESTION_VERSION")
fi
if [ "$RUN_TASKS" != question ]; then
  "$PY" training/quiz_model/fetch_sources.py --core-only --output-dir "$KM/verified-sources"
  "$PY" training/quiz_model/build_checked_answer_dataset.py \
    --sources "$KM/verified-sources" --output "$ANSWER_DATA"
  versions+=("$ANSWER_VERSION")
fi
train() { # output, version, data, samples, evaluation samples, steps
  mkdir -p "$1"
  "$PY" - "$1" "$2" "$3" "$BASE_MODEL" "$4" "$5" "$6" "$7" <<'PY'
import hashlib, json, sys
from pathlib import Path
out, version, data, base, train, evaluate, steps, max_length = sys.argv[1:]
out, data = Path(out), Path(data)
identity = dict(version=version, base=base, trainSamples=int(train), evalSamples=int(evaluate), steps=int(steps), maxLength=int(max_length),
                dataset=hashlib.sha256((data / 'dataset_manifest.json').read_bytes()).hexdigest(),
                trainer=hashlib.sha256(Path('training/quiz_model/train_hf_model.py').read_bytes()).hexdigest())
p = out / 'run_identity.json'
if p.exists() and json.loads(p.read_text()) != identity:
    raise SystemExit('Run configuration changed; select a new model version. Existing artifacts are preserved.')
if not p.exists() and (out / 'checkpoints').exists():
    raise SystemExit('Untracked checkpoints: select a new model version instead of resuming unknown training.')
if (out / 'training_manifest.json').exists():
    manifest = json.loads((out / 'training_manifest.json').read_text())
    if manifest.get('datasetManifestSha256') != identity['dataset'] or manifest.get('baseModel') != base:
        raise SystemExit('Completed model uses different data/base; select a new model version.')
p.write_text(json.dumps(identity, indent=2) + '\n')
PY
  if [ -f "$1/training_manifest.json" ]; then
    echo "==> $2 training already complete for this configuration"
    return
  fi
  echo "==> Training $2 on $BASE_MODEL"
  "$PY" training/quiz_model/train_hf_model.py --data-dir "$3" --output-dir "$1" \
    --model "$BASE_MODEL" --model-version "$2" --max-train-samples "$4" \
    --max-eval-samples "$5" --max-steps "$6" --max-length "$7" --bf16 --fixed-length-padding
}
if [ "$RUN_TASKS" != knowledge ]; then
  train "$KM/$QUESTION_VERSION-trained" "$QUESTION_VERSION" "$QUESTION_DATA" 3000 120 800 768
fi
if [ "$RUN_TASKS" != question ]; then
  train "$KM/$ANSWER_VERSION-trained" "$ANSWER_VERSION" "$ANSWER_DATA" 100000 100000 "$ANSWER_STEPS" 384
fi
if [ ! -d "$LLAMA_DIR" ]; then
  git clone https://github.com/ggml-org/llama.cpp "$LLAMA_DIR"
  git -C "$LLAMA_DIR" checkout "$LLAMA_COMMIT"
fi
if [ "$(git -C "$LLAMA_DIR" rev-parse HEAD)" != "$(git -C "$LLAMA_DIR" rev-parse "$LLAMA_COMMIT")" ]; then
  echo "llama.cpp must be at the pinned commit $LLAMA_COMMIT; existing checkout preserved" >&2
  exit 2
fi
if [ ! -x "$LLAMA_DIR/build-local/bin/llama-quantize" ] || [ ! -x "$LLAMA_DIR/build-local/bin/llama-completion" ]; then
  cmake -S "$LLAMA_DIR" -B "$LLAMA_DIR/build-local" -DGGML_METAL=ON -DLLAMA_BUILD_TESTS=OFF
  cmake --build "$LLAMA_DIR/build-local" --target llama-quantize llama-completion -j 4
fi
gguf() {
  if [ -f "$1/$2-q4_k_m.gguf" ]; then return; fi
  "$PY" training/quiz_model/merge_hf_adapter.py --adapter "$1/adapter" --output-dir "$1/merged"
  "$PY" "$LLAMA_DIR/convert_hf_to_gguf.py" "$1/merged" --outfile "$1/$2-f16.gguf" --outtype f16
  "$LLAMA_DIR/build-local/bin/llama-quantize" "$1/$2-f16.gguf" "$1/$2-q4_k_m.gguf" Q4_K_M
}
evaluate() { # version, data, split, precision
  echo "==> Evaluating $1 ($4) on $3"
  "$PY" training/quiz_model/evaluate_gguf_model.py --data "$2/$3.jsonl" \
    --model "$KM/$1-trained/$1-$4.gguf" --runner "$LLAMA_DIR/build-local/bin/llama-completion" \
    --output "$KM/$1-trained/gguf_${3}_${4}_report.json" --samples 100000
}
if [ "$RUN_TASKS" != knowledge ]; then gguf "$KM/$QUESTION_VERSION-trained" "$QUESTION_VERSION"; fi
if [ "$RUN_TASKS" != question ]; then gguf "$KM/$ANSWER_VERSION-trained" "$ANSWER_VERSION"; fi
# Validation selects a candidate. Failed validation never triggers another test-set look.
quality_args=()
if [ "$RUN_TASKS" != knowledge ]; then
  evaluate "$QUESTION_VERSION" "$QUESTION_DATA" validation q4_k_m
  quality_args+=(--question "$KM/$QUESTION_VERSION-trained/gguf_validation_q4_k_m_report.json")
fi
if [ "$RUN_TASKS" != question ]; then
  evaluate "$ANSWER_VERSION" "$ANSWER_DATA" validation f16
  evaluate "$ANSWER_VERSION" "$ANSWER_DATA" validation q4_k_m
  quality_args+=(--knowledge "$KM/$ANSWER_VERSION-trained/gguf_validation_q4_k_m_report.json")
fi
"$PY" training/quiz_model/check_training_quality.py "${quality_args[@]}"
quality_args=()
if [ "$RUN_TASKS" != knowledge ]; then
  evaluate "$QUESTION_VERSION" "$QUESTION_DATA" test q4_k_m
  quality_args+=(--question "$KM/$QUESTION_VERSION-trained/gguf_test_q4_k_m_report.json")
fi
if [ "$RUN_TASKS" != question ]; then
  evaluate "$ANSWER_VERSION" "$ANSWER_DATA" test q4_k_m
  quality_args+=(--knowledge "$KM/$ANSWER_VERSION-trained/gguf_test_q4_k_m_report.json")
fi
"$PY" training/quiz_model/check_training_quality.py "${quality_args[@]}"
for version in "${versions[@]}"; do
  file="$KM/$version-trained/$version-q4_k_m.gguf"
  ls -lh "$file"
  shasum -a 256 "$file"
done
echo 'Candidate gate passed on this benchmark. Review its scope and run on-device checks before promotion.'
