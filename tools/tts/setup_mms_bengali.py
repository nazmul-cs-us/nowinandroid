#!/usr/bin/env python3
"""Export Meta's pinned Bengali MMS checkpoint for a local, non-commercial preview.

Requires torch, numpy, scipy, onnx and sherpa-onnx in an isolated environment.
Weights and downloaded upstream source stay under ignored work/; nothing is uploaded.
"""

import argparse
import hashlib
import json
import subprocess
import sys
import time
import types
import wave
from pathlib import Path

MODEL_REVISION = "44cc7fb408064ef9ea6e7c59130d88cac1274671"
SOURCE_REVISION = "65d863f41196654aa8b8f3dc586474a4c8f30934"
CHECKPOINT_SHA256 = "a8c098eab2e5e378fc52bec57683839bbc641b2241033dab17174f6e37db29a4"
LICENSE_NOTICE = """Meta MMS Bengali text-to-speech — CC BY-NC 4.0
Copyright and attribution: Meta AI / MMS authors, including Vineel Pratap et al.
Source: https://huggingface.co/facebook/mms-tts-ben
Original checkpoints: https://huggingface.co/facebook/mms-tts
License: https://creativecommons.org/licenses/by-nc/4.0/

The weights are for non-commercial use under the published license. Commercial
use requires separate permission. ONNX conversion does not remove this restriction.
Converted to ONNX using the app's setup_mms_bengali.py exporter, including
dynamic relative-attention padding for variable-length inference.
Redistribution must retain this notice and comply with CC BY-NC 4.0.
No endorsement by Meta AI or the MMS authors is implied.
"""


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def fetch(url, destination, expected=None):
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists() and (expected is None or sha256(destination) == expected):
        return
    temporary = destination.with_suffix(destination.suffix + ".part")
    subprocess.run(
        [
            "curl",
            "--fail",
            "--location",
            "--retry",
            "2",
            "--output",
            str(temporary),
            url,
        ],
        check=True,
    )
    if expected and sha256(temporary) != expected:
        temporary.unlink()
        raise RuntimeError(f"Checksum mismatch: {destination.name}")
    temporary.replace(destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--accept-noncommercial-license", action="store_true")
    parser.add_argument("--work-dir", type=Path, default=Path("work/tts/mms-bengali"))
    args = parser.parse_args()
    if not args.accept_noncommercial_license:
        parser.error(
            "Read CC BY-NC 4.0 and pass --accept-noncommercial-license for local non-commercial evaluation only."
        )
    work = args.work_dir.resolve()
    upstream = work / "upstream-vits"
    checkpoint = work / "checkpoint"
    output = work / "assets/tts/vits-mms-ben"
    output.mkdir(parents=True, exist_ok=True)
    for name in (
        "models.py",
        "modules.py",
        "attentions.py",
        "commons.py",
        "transforms.py",
        "LICENSE",
    ):
        fetch(
            f"https://huggingface.co/spaces/mms-meta/MMS/resolve/{SOURCE_REVISION}/vits/{name}",
            upstream / name,
        )
    for name in ("G_100000.pth", "config.json", "vocab.txt"):
        fetch(
            f"https://huggingface.co/facebook/mms-tts/resolve/{MODEL_REVISION}/models/ben/{name}",
            checkpoint / name,
            CHECKPOINT_SHA256 if name.endswith(".pth") else None,
        )

    import numpy as np
    import onnx
    import torch

    # The alignment extension is training-only; inference/export must never call it.
    align = types.ModuleType("monotonic_align")

    def training_only(*_args, **_kwargs):
        raise RuntimeError(
            "Training-only alignment must not run during MMS inference/export"
        )

    align.maximum_path = training_only
    sys.modules["monotonic_align"] = align
    sys.path.insert(0, str(upstream))
    import attentions
    from models import SynthesizerTrn

    # The upstream Python max()/if freezes the relative-attention padding at
    # tracing time. Always pad by the runtime length, then take a centered slice;
    # this is equivalent for both short and long inputs and exports dynamically.
    def dynamic_relative_embeddings(self, embeddings, length):
        padded = torch.nn.functional.pad(embeddings, (0, 0, length, length))
        return padded[:, self.window_size + 1 : self.window_size + 2 * length]

    attentions.MultiHeadAttention._get_relative_embeddings = dynamic_relative_embeddings

    config = json.loads((checkpoint / "config.json").read_text())
    if config["data"]["training_files"].endswith("uroman"):
        raise RuntimeError(
            "This export intentionally requires the Bengali character frontend, not uroman"
        )
    symbols = (checkpoint / "vocab.txt").read_text().splitlines()
    network = SynthesizerTrn(
        len(symbols),
        config["data"]["filter_length"] // 2 + 1,
        config["train"]["segment_size"] // config["data"]["hop_length"],
        **config["model"],
    )
    weights = torch.load(
        checkpoint / "G_100000.pth", map_location="cpu", weights_only=True
    )
    network.load_state_dict(weights["model"], strict=True)
    network.eval()
    torch.set_num_threads(4)
    torch.manual_seed(1234)

    class Export(torch.nn.Module):
        def __init__(self):
            super().__init__()
            self.network = network

        def forward(self, x, x_length, noise_scale, length_scale, noise_scale_w):
            return self.network.infer(
                x,
                x_length,
                noise_scale=noise_scale,
                length_scale=length_scale,
                noise_scale_w=noise_scale_w,
            )[0]

    model_path = output / "model.onnx"
    with torch.no_grad():
        torch.onnx.export(
            Export(),
            (
                torch.tensor([[1, 2, 3, 4, 5]], dtype=torch.int64),
                torch.tensor([5], dtype=torch.int64),
                torch.tensor([0.667]),
                torch.tensor([1.0]),
                torch.tensor([0.8]),
            ),
            str(model_path),
            opset_version=13,
            dynamo=False,
            input_names=[
                "x",
                "x_length",
                "noise_scale",
                "length_scale",
                "noise_scale_w",
            ],
            output_names=["y"],
            dynamic_axes={
                "x": {0: "N", 1: "L"},
                "x_length": {0: "N"},
                "y": {0: "N", 2: "samples"},
            },
        )
    model = onnx.load(model_path)
    for key, value in {
        "model_type": "vits",
        "comment": "mms",
        "frontend": "characters",
        "language": "ben",
        "add_blank": int(config["data"]["add_blank"]),
        "n_speakers": config["data"]["n_speakers"],
        "sample_rate": config["data"]["sampling_rate"],
        "license": "CC-BY-NC-4.0",
        "url": "https://huggingface.co/facebook/mms-tts-ben",
    }.items():
        entry = model.metadata_props.add()
        entry.key, entry.value = key, str(value)
    onnx.checker.check_model(model)
    onnx.save(model, model_path)
    (output / "tokens.txt").write_text(
        "".join(f"{token} {index}\n" for index, token in enumerate(symbols))
    )
    (output / "LICENSE.txt").write_text(LICENSE_NOTICE)

    import sherpa_onnx

    tts = sherpa_onnx.OfflineTts(
        sherpa_onnx.OfflineTtsConfig(
            model=sherpa_onnx.OfflineTtsModelConfig(
                vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                    model=str(model_path), tokens=str(output / "tokens.txt")
                ),
                num_threads=4,
                debug=False,
            ),
            max_num_sentences=1,
        )
    )
    text = "এটি বাংলা তাফসির পড়ার জন্য একটি কণ্ঠস্বরের নমুনা।"
    start = time.perf_counter()
    audio = tts.generate(text, sid=0, speed=1.0)
    elapsed = time.perf_counter() - start
    samples = np.asarray(audio.samples)
    if samples.size == 0 or not np.isfinite(samples).all():
        raise RuntimeError("The converted model did not produce valid Bengali speech")
    sample = work / "bengali-preview.wav"
    with wave.open(str(sample), "wb") as destination:
        destination.setnchannels(1)
        destination.setsampwidth(2)
        destination.setframerate(audio.sample_rate)
        destination.writeframes(
            (np.clip(samples, -1, 1) * 32767).astype("<i2").tobytes()
        )
    metadata = {
        "source_model": "facebook/mms-tts-ben",
        "checkpoint_revision": MODEL_REVISION,
        "source_revision": SOURCE_REVISION,
        "license": "CC-BY-NC-4.0",
        "commercial_distribution": False,
        "sample_rate": audio.sample_rate,
        "sample_audio_seconds": samples.size / audio.sample_rate,
        "generation_seconds": elapsed,
        "model_bytes": model_path.stat().st_size,
        "sha256": {p.name: sha256(p) for p in (model_path, output / "tokens.txt")},
    }
    (output / "preview-metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
    print(
        json.dumps(
            {
                **metadata,
                "asset_directory": str(work / "assets"),
                "sample": str(sample),
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
