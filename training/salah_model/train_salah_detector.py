"""
Train a salah posture detection model.

Architecture: 1D CNN (TFLite-compatible, no LSTM/RNN ops)
Input: sequences of 20 windows x 30 features (2 seconds of sensor data)
Output: 7-class classification (QIYAM, RUKU, GOING_TO_SUJUD, SUJUD, JALSA, TASHAHHUD, QIYAM_RISING)

Usage:
    python train_salah_detector.py --data_dir ../data
    python train_salah_detector.py --data_dir ../data --epochs 100 --batch_size 32
"""

import argparse
import json
import os
import sys
from pathlib import Path

import numpy as np
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, confusion_matrix

from feature_engineering import (
    load_jsonl_files, create_sequences, normalize_features,
    POSTURE_LABELS, NUM_CLASSES, SEQUENCE_LENGTH, FEATURES_PER_WINDOW
)
from data_augmentation import augment_dataset, balance_classes


def build_model(
    seq_length: int = SEQUENCE_LENGTH,
    n_features: int = FEATURES_PER_WINDOW,
    n_classes: int = NUM_CLASSES
) -> keras.Model:
    """
    Build a pure 1D CNN model for salah posture classification.
    Uses only TFLite-native ops (no LSTM/RNN) for clean conversion.

    Architecture:
        Conv1D(32, k=3) -> ReLU -> BatchNorm -> Dropout(0.3)
        Conv1D(64, k=3) -> ReLU -> BatchNorm -> Dropout(0.3)
        Conv1D(128, k=3) -> ReLU -> BatchNorm -> Dropout(0.3)
        GlobalAveragePooling1D
        Dense(64, ReLU) -> Dropout(0.4)
        Dense(n_classes, Softmax)
    """
    inputs = keras.Input(shape=(seq_length, n_features), name="sensor_input")

    # CNN feature extraction - multi-scale temporal patterns
    x = layers.Conv1D(32, kernel_size=3, padding='same', activation='relu')(inputs)
    x = layers.BatchNormalization()(x)
    x = layers.Dropout(0.3)(x)

    x = layers.Conv1D(64, kernel_size=3, padding='same', activation='relu')(x)
    x = layers.BatchNormalization()(x)
    x = layers.Dropout(0.3)(x)

    x = layers.Conv1D(128, kernel_size=3, padding='same', activation='relu')(x)
    x = layers.BatchNormalization()(x)
    x = layers.Dropout(0.3)(x)

    # Global pooling to aggregate temporal info
    x = layers.GlobalAveragePooling1D()(x)

    # Classification head
    x = layers.Dense(64, activation='relu')(x)
    x = layers.Dropout(0.4)(x)
    outputs = layers.Dense(n_classes, activation='softmax', name="posture_output")(x)

    model = keras.Model(inputs=inputs, outputs=outputs, name="salah_detector")
    return model


def train(args):
    print("=" * 60)
    print("Salah Posture Detection - Model Training")
    print("=" * 60)

    # Load data
    data_dir = Path(args.data_dir)
    samples = load_jsonl_files(str(data_dir))

    if len(samples) < 100:
        print(f"\nOnly {len(samples)} samples found. Recommend at least 500+ for training.")
        if len(samples) < 20:
            print("Too few samples. Collect more data first.")
            return

    # Create sequences
    X, y = create_sequences(samples, sequence_length=args.seq_length, stride=args.stride)

    if len(X) == 0:
        print("Could not create sequences. Need longer continuous recordings per posture.")
        return

    # Split data
    X_train, X_val, y_train, y_val = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    print(f"\nTrain: {len(X_train)}, Validation: {len(X_val)}")

    # Balance classes
    X_train, y_train = balance_classes(X_train, y_train, strategy="oversample")

    # Data augmentation
    if args.augment:
        X_train, y_train = augment_dataset(
            X_train, y_train,
            augmentation_factor=args.augment_factor,
            include_original=True
        )

    # Normalize
    X_train, X_val, feat_mean, feat_std = normalize_features(X_train, X_val)

    print(f"\nFinal training set: {X_train.shape}")
    print(f"Final validation set: {X_val.shape}")

    # Build model
    model = build_model(
        seq_length=args.seq_length,
        n_features=FEATURES_PER_WINDOW,
        n_classes=NUM_CLASSES
    )
    model.summary()

    # Compile
    model.compile(
        optimizer=keras.optimizers.Adam(learning_rate=args.lr),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )

    # Callbacks
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    callbacks = [
        keras.callbacks.EarlyStopping(
            monitor='val_accuracy',
            patience=15,
            restore_best_weights=True,
            verbose=1
        ),
        keras.callbacks.ReduceLROnPlateau(
            monitor='val_loss',
            factor=0.5,
            patience=5,
            min_lr=1e-6,
            verbose=1
        ),
        keras.callbacks.ModelCheckpoint(
            str(output_dir / "best_model.keras"),
            monitor='val_accuracy',
            save_best_only=True,
            verbose=1
        )
    ]

    # Train
    print(f"\nTraining for up to {args.epochs} epochs...")
    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=args.epochs,
        batch_size=args.batch_size,
        callbacks=callbacks,
        verbose=1
    )

    # Evaluate
    print("\n" + "=" * 60)
    print("Evaluation Results")
    print("=" * 60)

    val_loss, val_acc = model.evaluate(X_val, y_val, verbose=0)
    print(f"Validation Accuracy: {val_acc:.4f}")
    print(f"Validation Loss: {val_loss:.4f}")

    # Classification report
    y_pred = model.predict(X_val, verbose=0).argmax(axis=1)
    print("\nClassification Report:")
    print(classification_report(y_val, y_pred, target_names=POSTURE_LABELS))

    print("Confusion Matrix:")
    cm = confusion_matrix(y_val, y_pred)
    print(cm)

    # Save normalization parameters
    norm_params = {
        "mean": feat_mean.tolist(),
        "std": feat_std.tolist(),
        "posture_labels": POSTURE_LABELS,
        "sequence_length": args.seq_length,
        "features_per_window": FEATURES_PER_WINDOW
    }
    with open(output_dir / "norm_params.json", 'w') as f:
        json.dump(norm_params, f, indent=2)

    # Save training history
    hist_dict = {k: [float(v) for v in vals] for k, vals in history.history.items()}
    with open(output_dir / "training_history.json", 'w') as f:
        json.dump(hist_dict, f, indent=2)

    # Save model
    model.save(str(output_dir / "salah_detector.keras"))
    print(f"\nModel saved to {output_dir / 'salah_detector.keras'}")
    print(f"Normalization params saved to {output_dir / 'norm_params.json'}")

    # Model size info
    model_size_mb = os.path.getsize(output_dir / "salah_detector.keras") / (1024 * 1024)
    print(f"Model size: {model_size_mb:.2f} MB")

    return model, norm_params


def main():
    parser = argparse.ArgumentParser(description="Train salah posture detector")
    parser.add_argument("--data_dir", type=str, default="../data",
                        help="Directory containing JSONL training data")
    parser.add_argument("--output_dir", type=str, default="../output",
                        help="Directory to save model and artifacts")
    parser.add_argument("--epochs", type=int, default=100,
                        help="Maximum training epochs")
    parser.add_argument("--batch_size", type=int, default=32,
                        help="Training batch size")
    parser.add_argument("--lr", type=float, default=0.001,
                        help="Initial learning rate")
    parser.add_argument("--seq_length", type=int, default=SEQUENCE_LENGTH,
                        help="Sequence length (number of 100ms windows)")
    parser.add_argument("--stride", type=int, default=10,
                        help="Stride for sequence creation")
    parser.add_argument("--augment", action="store_true", default=True,
                        help="Apply data augmentation")
    parser.add_argument("--no_augment", dest="augment", action="store_false",
                        help="Disable data augmentation")
    parser.add_argument("--augment_factor", type=int, default=4,
                        help="Number of augmented copies per original")

    args = parser.parse_args()
    train(args)


if __name__ == "__main__":
    main()
