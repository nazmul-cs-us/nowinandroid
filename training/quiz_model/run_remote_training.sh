#!/usr/bin/env bash
#
# End-to-end Deenly knowledge-model training for a fresh checkout.
#
# Runs on Apple Silicon (tested on M4 Pro) with full GPU access:
#   1. Creates a Python venv and installs the pinned training deps
#   2. LoRA-trains deenly-question-v6 and deenly-knowledge-v4 on
#      Qwen/Qwen2.5-0.5B-Instruct (the smaller, faster on-device base)
#   3. Merges adapters, converts to GGUF, quantizes to Q4_K_M
#   4. Builds llama.cpp (pinned commit) and evaluates both GGUFs with
#      the exact Android runtime contract (system prompt, JSON schema,
#      single-turn, temp 0)
#   5. Writes a promotion summary with file sizes and SHA-256 checksums
#      ready to paste into DeenlyKnowledgeModel.kt / manifests
#
# Usage:
#   bash training/quiz_model/run_remote_training.sh
#
# Everything large (HF cache, checkpoints, GGUFs, llama.cpp build) stays
# under build/ (gitignored). Safe to re-run; training auto-resumes from
# the newest checkpoint, and each stage skips itself when its output
# already exists.

set -euo pipefail
cd "$(dirname "$0")/../.."

ROOT="$(pwd)"
KM="$ROOT/build/knowledge-model"
LLAMA_DIR="$ROOT/build/llama.cpp"
LLAMA_COMMIT="4d756bc"   # pinned: matches the shipped runner contract
export HF_HOME="$ROOT/build/huggingface-cache"

QUESTION_VERSION="deenly-question-v6"
ANSWER_VERSION="deenly-knowledge-v4"
QUESTION_DATA="$ROOT/training/quiz_model/datasets/question-combined-v4"
ANSWER_DATA="$ROOT/training/quiz_model/datasets/answer-contract-v2"
BASE_MODEL="Qwen/Qwen2.5-0.5B-Instruct"

mkdir -p "$KM"

# ---------------------------------------------------------------- venv ----
if [ ! -x ".venv-knowledge-model/bin/python" ]; then
  echo "==> Creating virtual environment"
  rm -rf .venv-knowledge-model
  PYTHON="$(command -v python3.11 || command -v python3.12 || command -v python3)"
  "$PYTHON" -m venv .venv-knowledge-model
  .venv-knowledge-model/bin/pip install --upgrade pip -q
fi
if ! .venv-knowledge-model/bin/pip show torch >/dev/null 2>&1; then
  echo "==> Installing training dependencies"
  .venv-knowledge-model/bin/pip install -r training/quiz_model/requirements-hf.txt
fi
PY=".venv-knowledge-model/bin/python"

# ------------------------------------------------------------ training ----
train() { # $1 output-dir  $2 version  $3 data-dir  $4 samples  $5 eval  $6 steps
  if [ -f "$1/training_manifest.json" ]; then
    echo "==> $2 already trained, skipping"
    return
  fi
  echo "==> Training $2"
  HF_HOME="$HF_HOME" "$PY" training/quiz_model/train_hf_model.py \
    --data-dir "$3" \
    --output-dir "$1" \
    --model "$BASE_MODEL" \
    --model-version "$2" \
    --max-train-samples "$4" --max-eval-samples "$5" --max-steps "$6" \
    --bf16 --fixed-length-padding
}

# Same datasets and budgets as the shipped 1.5B models (v5 / v3).
train "$KM/$QUESTION_VERSION-trained" "$QUESTION_VERSION" "$QUESTION_DATA" 3000 120 800
train "$KM/$ANSWER_VERSION-trained"   "$ANSWER_VERSION"   "$ANSWER_DATA"    544  70 300

# --------------------------------------------------- merge + GGUF + quant --
gguf() { # $1 trained-dir  $2 version
  if [ -f "$1/$2-q4_k_m.gguf" ]; then
    echo "==> $2 GGUF already built, skipping"
    return
  fi
  echo "==> Merging $2"
  HF_HOME="$HF_HOME" "$PY" training/quiz_model/merge_hf_adapter.py \
    --adapter "$1/adapter" --output-dir "$1/merged"

  echo "==> Converting $2 to GGUF f16"
  "$PY" "$LLAMA_DIR/convert_hf_to_gguf.py" "$1/merged" \
    --outfile "$1/$2-f16.gguf" --outtype f16

  echo "==> Quantizing $2 to Q4_K_M"
  "$LLAMA_DIR/build-local/bin/llama-quantize" \
    "$1/$2-f16.gguf" "$1/$2-q4_k_m.gguf" Q4_K_M
}

# llama.cpp at the pinned commit provides convert_hf_to_gguf.py,
# llama-quantize, and the llama-completion evaluator runner.
if [ ! -d "$LLAMA_DIR" ]; then
  echo "==> Cloning llama.cpp at $LLAMA_COMMIT"
  git clone https://github.com/ggml-org/llama.cpp "$LLAMA_DIR"
  git -C "$LLAMA_DIR" checkout "$LLAMA_COMMIT"
fi
if [ ! -x "$LLAMA_DIR/build-local/bin/llama-quantize" ]; then
  echo "==> Building llama.cpp (Metal)"
  cmake -S "$LLAMA_DIR" -B "$LLAMA_DIR/build-local" -DGGML_METAL=ON -DLLAMA_BUILD_TESTS=OFF
  cmake --build "$LLAMA_DIR/build-local" --target llama-quantize llama-completion -j
fi

gguf "$KM/$QUESTION_VERSION-trained" "$QUESTION_VERSION"
gguf "$KM/$ANSWER_VERSION-trained"   "$ANSWER_VERSION"

# ----------------------------------------------------------- evaluation ----
evaluate() { # $1 trained-dir  $2 version  $3 data-dir  $4 samples
  echo "==> Evaluating $2 with the Android runtime contract"
  HF_HOME="$HF_HOME" "$PY" training/quiz_model/evaluate_gguf_model.py \
    --data "$3/test.jsonl" \
    --model "$1/$2-q4_k_m.gguf" \
    --runner "$LLAMA_DIR/build-local/bin/llama-completion" \
    --output "$1/gguf_test_report_remote.json" \
    --samples "$4"
}

evaluate "$KM/$QUESTION_VERSION-trained" "$QUESTION_VERSION" "$QUESTION_DATA" 90
evaluate "$KM/$ANSWER_VERSION-trained"   "$ANSWER_VERSION"   "$ANSWER_DATA"   60

# --------------------------------------------------------------- summary ---
echo ""
echo "================ PROMOTION SUMMARY ================"
for version in "$QUESTION_VERSION" "$ANSWER_VERSION"; do
  file="$KM/$version-trained/$version-q4_k_m.gguf"
  report="$KM/$version-trained/gguf_test_report_remote.json"
  echo ""
  echo "$version"
  echo "  file:   $(basename "$file")"
  echo "  size:   $(stat -f%z "$file" 2>/dev/null || stat -c%s "$file")"
  echo "  sha256: $(shasum -a 256 "$file" | cut -d' ' -f1)"
  "$PY" - "$report" <<'EOF'
import json, sys
r = json.load(open(sys.argv[1]))
print(f"  eval:   {r['accepted']}/{r['samples']} accepted "
      f"({r['acceptanceRate']:.0%}), mean {r['meanDurationMs']} ms")
EOF
done
echo ""
echo "Baselines to beat: question-v5 (1.5B) 70% @ 5223 ms, knowledge-v3 (1.5B) 72% @ 3136 ms"
echo "Next: paste file/size/sha256 into DeenlyKnowledgeModel.kt and"
echo "AssetDownloadManager.kt, then run the on-device smoke test."
