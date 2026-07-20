"""
Export trained salah detector model to TFLite format for on-device inference.

Usage:
    python export_tflite.py --model_dir ../output
    python export_tflite.py --model_dir ../output --quantize
"""

import argparse
import json
import os
import sys
from pathlib import Path

import numpy as np
import tensorflow as tf
from tensorflow import keras


def export_tflite(
    model_path: str,
    output_path: str,
    quantize: bool = False,
    representative_data: np.ndarray = None
) -> str:
    """
    Convert Keras model to TFLite format.

    Args:
        model_path: Path to saved Keras model
        output_path: Output .tflite file path
        quantize: Whether to apply int8 quantization
        representative_data: Sample data for quantization calibration

    Returns:
        Path to the exported .tflite file
    """
    print(f"Loading model from {model_path}...")
    model = keras.models.load_model(model_path)

    # Convert via a concrete function: from_keras_model on a re-loaded Keras 3
    # model loses batch-norm variable values ("missing attribute 'value'" /
    # "Failed to infer result type(s)" in the MLIR converter).
    input_shape = list(model.inputs[0].shape)
    input_shape[0] = 1
    run_model = tf.function(lambda x: model(x))
    concrete_func = run_model.get_concrete_function(
        tf.TensorSpec(input_shape, tf.float32)
    )
    converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete_func], run_model)

    if quantize:
        print("Applying int8 quantization...")
        converter.optimizations = [tf.lite.Optimize.DEFAULT]

        if representative_data is not None:
            def representative_dataset():
                for i in range(min(100, len(representative_data))):
                    sample = representative_data[i:i+1].astype(np.float32)
                    yield [sample]

            converter.representative_dataset = representative_dataset
            converter.target_spec.supported_ops = [
                tf.lite.OpsSet.TFLITE_BUILTINS_INT8
            ]
            converter.inference_input_type = tf.float32  # Keep float I/O for easier integration
            converter.inference_output_type = tf.float32
            print("Using full int8 quantization with representative dataset")
        else:
            print("Using dynamic range quantization (no representative data)")
    else:
        print("No quantization (float32)")

    tflite_model = converter.convert()

    # Save
    with open(output_path, 'wb') as f:
        f.write(tflite_model)

    size_mb = len(tflite_model) / (1024 * 1024)
    print(f"\nExported TFLite model: {output_path}")
    print(f"Model size: {size_mb:.2f} MB")

    return output_path


def verify_tflite(tflite_path: str, test_data: np.ndarray = None):
    """Verify the TFLite model works correctly."""
    print(f"\nVerifying TFLite model: {tflite_path}")

    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print(f"Input shape:  {input_details[0]['shape']}")
    print(f"Input dtype:  {input_details[0]['dtype']}")
    print(f"Output shape: {output_details[0]['shape']}")
    print(f"Output dtype: {output_details[0]['dtype']}")

    if test_data is not None and len(test_data) > 0:
        # Run inference on a sample
        sample = test_data[0:1].astype(np.float32)
        interpreter.set_tensor(input_details[0]['index'], sample)
        interpreter.invoke()
        output = interpreter.get_tensor(output_details[0]['index'])

        from feature_engineering import POSTURE_LABELS
        predicted_idx = output[0].argmax()
        confidence = output[0][predicted_idx]
        print(f"\nTest inference:")
        print(f"  Predicted: {POSTURE_LABELS[predicted_idx]} ({confidence:.2%})")
        print(f"  All probabilities: {dict(zip(POSTURE_LABELS, [f'{p:.2%}' for p in output[0]]))}")

        # Benchmark inference time
        import time
        times = []
        for _ in range(100):
            start = time.perf_counter()
            interpreter.set_tensor(input_details[0]['index'], sample)
            interpreter.invoke()
            interpreter.get_tensor(output_details[0]['index'])
            times.append((time.perf_counter() - start) * 1000)

        print(f"\nInference benchmark (100 runs):")
        print(f"  Mean: {np.mean(times):.2f} ms")
        print(f"  Median: {np.median(times):.2f} ms")
        print(f"  P95: {np.percentile(times, 95):.2f} ms")

    print("\nTFLite model verified successfully!")


def main():
    parser = argparse.ArgumentParser(description="Export salah detector to TFLite")
    parser.add_argument("--model_dir", type=str, default="../output",
                        help="Directory containing trained model")
    parser.add_argument("--output", type=str, default=None,
                        help="Output .tflite path (default: model_dir/salah_detector.tflite)")
    parser.add_argument("--quantize", action="store_true",
                        help="Apply int8 quantization")
    parser.add_argument("--data_dir", type=str, default="../data",
                        help="Data directory for representative dataset (quantization)")
    parser.add_argument("--deploy", action="store_true",
                        help="Copy TFLite model to Android assets directory")

    args = parser.parse_args()

    model_dir = Path(args.model_dir)
    model_path = model_dir / "salah_detector.keras"

    if not model_path.exists():
        # Try best_model
        model_path = model_dir / "best_model.keras"
        if not model_path.exists():
            print(f"Model not found in {model_dir}")
            sys.exit(1)

    output_path = args.output or str(model_dir / "salah_detector.tflite")

    # Load test data for verification if available
    processed_dir = Path(args.data_dir) / "processed"
    test_data = None
    if (processed_dir / "X_sequences.npy").exists():
        X = np.load(processed_dir / "X_sequences.npy")
        # Load normalization params
        norm_path = model_dir / "norm_params.json"
        if norm_path.exists():
            with open(norm_path) as f:
                norm = json.load(f)
            mean = np.array(norm["mean"])
            std = np.array(norm["std"])
            std[std < 1e-7] = 1.0
            X = (X - mean) / std
        test_data = X

    # Export
    representative_data = test_data if args.quantize else None
    export_tflite(str(model_path), output_path, args.quantize, representative_data)

    # Verify
    verify_tflite(output_path, test_data)

    # Deploy to Android assets
    if args.deploy:
        android_assets = Path(__file__).parent.parent.parent / "app" / "src" / "main" / "assets"
        if android_assets.exists():
            import shutil
            dest = android_assets / "salah_detector.tflite"
            shutil.copy2(output_path, dest)
            print(f"\nDeployed to: {dest}")

            # Also copy norm params
            norm_src = model_dir / "norm_params.json"
            if norm_src.exists():
                shutil.copy2(norm_src, android_assets / "salah_norm_params.json")
                print(f"Deployed norm params to: {android_assets / 'salah_norm_params.json'}")

            # And the training quality report, so the app can show how good
            # the deployed model actually is.
            report_src = model_dir / "dataset_report.json"
            if report_src.exists():
                shutil.copy2(report_src, android_assets / "last_training_report.json")
                print(f"Deployed training report to: {android_assets / 'last_training_report.json'}")
        else:
            print(f"\nAndroid assets directory not found: {android_assets}")
            print("Copy the .tflite file manually to app/src/main/assets/")

    print("\nDone!")


if __name__ == "__main__":
    main()
