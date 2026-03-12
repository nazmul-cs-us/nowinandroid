"""
Data augmentation for salah posture sensor data.

Augmentation strategies:
1. Rotation - simulate different pocket orientations
2. Time warping - simulate different prayer speeds
3. Gaussian noise - simulate sensor noise
4. Magnitude scaling - simulate different phone weights/pocket depths
5. Jittering - small random perturbations
"""

import numpy as np
from typing import Tuple


def augment_rotation(X: np.ndarray, max_angle_deg: float = 15.0) -> np.ndarray:
    """
    Apply random 3D rotation to accelerometer and gyroscope data.
    Simulates the phone being at slightly different angles in the pocket.

    Args:
        X: (N, seq_len, 30) feature array
        max_angle_deg: Maximum rotation angle in degrees

    Returns:
        Augmented copy of X
    """
    X_aug = X.copy()
    max_angle = np.radians(max_angle_deg)

    for i in range(len(X_aug)):
        # Random rotation angles
        alpha = np.random.uniform(-max_angle, max_angle)
        beta = np.random.uniform(-max_angle, max_angle)
        gamma = np.random.uniform(-max_angle, max_angle)

        # Rotation matrix (simplified - apply small rotations to mean values)
        ca, sa = np.cos(alpha), np.sin(alpha)
        cb, sb = np.cos(beta), np.sin(beta)
        cg, sg = np.cos(gamma), np.sin(gamma)

        R = np.array([
            [ca*cb, ca*sb*sg - sa*cg, ca*sb*cg + sa*sg],
            [sa*cb, sa*sb*sg + ca*cg, sa*sb*cg - ca*sg],
            [-sb,   cb*sg,            cb*cg]
        ])

        for t in range(X_aug.shape[1]):
            # Rotate accel means (features 0-2)
            accel_mean = X_aug[i, t, 0:3]
            X_aug[i, t, 0:3] = R @ accel_mean

            # Rotate gyro means (features 8-10)
            gyro_mean = X_aug[i, t, 8:11]
            X_aug[i, t, 8:11] = R @ gyro_mean

            # Recompute pitch and roll from rotated accel
            ax, ay, az = X_aug[i, t, 0], X_aug[i, t, 1], X_aug[i, t, 2]
            X_aug[i, t, 16] = np.degrees(np.arctan2(ay, az))  # pitch
            X_aug[i, t, 18] = np.degrees(np.arctan2(ax, az))  # roll
            X_aug[i, t, 24] = X_aug[i, t, 16]  # pitch copy
            X_aug[i, t, 25] = X_aug[i, t, 18]  # roll copy

    return X_aug


def augment_time_warp(X: np.ndarray, sigma: float = 0.2) -> np.ndarray:
    """
    Apply time warping by stretching/compressing the time axis.
    Simulates different prayer speeds.

    Args:
        X: (N, seq_len, 30) feature array
        sigma: Standard deviation of warping

    Returns:
        Augmented copy of X
    """
    X_aug = X.copy()
    seq_len = X.shape[1]

    for i in range(len(X_aug)):
        # Generate smooth warping path
        warp_steps = np.random.normal(loc=1.0, scale=sigma, size=seq_len)
        warp_steps = np.cumsum(warp_steps)
        warp_steps = warp_steps / warp_steps[-1] * (seq_len - 1)
        warp_steps = np.clip(warp_steps, 0, seq_len - 1)

        # Interpolate features along warped time axis
        original_steps = np.arange(seq_len)
        for f in range(X.shape[2]):
            X_aug[i, :, f] = np.interp(warp_steps, original_steps, X[i, :, f])

    return X_aug


def augment_noise(X: np.ndarray, sigma: float = 0.05) -> np.ndarray:
    """
    Add Gaussian noise to features.
    Simulates sensor noise variations.

    Args:
        X: (N, seq_len, 30) feature array
        sigma: Standard deviation of noise (relative to feature std)

    Returns:
        Augmented copy of X
    """
    X_aug = X.copy()

    # Compute per-feature std from the data
    if X.ndim == 3:
        feat_std = X.reshape(-1, X.shape[-1]).std(axis=0)
    else:
        feat_std = X.std(axis=0)

    feat_std[feat_std < 1e-7] = 1.0

    noise = np.random.normal(0, sigma, X_aug.shape) * feat_std
    X_aug += noise.astype(np.float32)

    return X_aug


def augment_magnitude_scaling(X: np.ndarray, sigma: float = 0.1) -> np.ndarray:
    """
    Scale sensor magnitudes randomly.
    Simulates different pocket depths or phone weights.

    Args:
        X: (N, seq_len, 30) feature array
        sigma: Standard deviation of scaling factor

    Returns:
        Augmented copy of X
    """
    X_aug = X.copy()

    for i in range(len(X_aug)):
        # Random scale factor close to 1.0
        scale = np.random.normal(1.0, sigma)
        scale = max(0.7, min(1.3, scale))  # Clamp to reasonable range

        # Scale accelerometer features (0-7, 20-21, 26, 28)
        accel_indices = [0, 1, 2, 3, 4, 5, 6, 7, 20, 21, 26, 28]
        for idx in accel_indices:
            X_aug[i, :, idx] *= scale

        # Scale gyroscope features separately (8-15, 22-23, 27, 29)
        gyro_scale = np.random.normal(1.0, sigma)
        gyro_scale = max(0.7, min(1.3, gyro_scale))
        gyro_indices = [8, 9, 10, 11, 12, 13, 14, 15, 22, 23, 27, 29]
        for idx in gyro_indices:
            X_aug[i, :, idx] *= gyro_scale

    return X_aug


def augment_dataset(
    X: np.ndarray,
    y: np.ndarray,
    augmentation_factor: int = 4,
    include_original: bool = True
) -> Tuple[np.ndarray, np.ndarray]:
    """
    Apply multiple augmentations to create an expanded dataset.

    Args:
        X: (N, seq_len, features) or (N, features)
        y: (N,) labels
        augmentation_factor: How many augmented copies per original
        include_original: Whether to include original data in output

    Returns:
        X_aug: Augmented feature array
        y_aug: Corresponding labels
    """
    X_list = []
    y_list = []

    if include_original:
        X_list.append(X)
        y_list.append(y)

    for _ in range(augmentation_factor):
        # Randomly select augmentation strategy
        aug_type = np.random.choice(['rotation', 'time_warp', 'noise', 'scaling'])

        if aug_type == 'rotation':
            X_aug = augment_rotation(X, max_angle_deg=np.random.uniform(5, 20))
        elif aug_type == 'time_warp':
            X_aug = augment_time_warp(X, sigma=np.random.uniform(0.1, 0.3))
        elif aug_type == 'noise':
            X_aug = augment_noise(X, sigma=np.random.uniform(0.02, 0.08))
        else:
            X_aug = augment_magnitude_scaling(X, sigma=np.random.uniform(0.05, 0.15))

        X_list.append(X_aug)
        y_list.append(y.copy())

    X_combined = np.concatenate(X_list, axis=0)
    y_combined = np.concatenate(y_list, axis=0)

    # Shuffle
    indices = np.random.permutation(len(X_combined))
    X_combined = X_combined[indices]
    y_combined = y_combined[indices]

    print(f"Augmented dataset: {X.shape[0]} -> {X_combined.shape[0]} samples "
          f"({augmentation_factor}x augmentation)")

    return X_combined, y_combined


def balance_classes(
    X: np.ndarray,
    y: np.ndarray,
    strategy: str = "oversample"
) -> Tuple[np.ndarray, np.ndarray]:
    """
    Balance class distribution by oversampling minority classes.

    Args:
        X: Feature array
        y: Labels
        strategy: "oversample" minority or "undersample" majority

    Returns:
        Balanced X, y
    """
    unique, counts = np.unique(y, return_counts=True)
    max_count = counts.max()
    min_count = counts.min()

    print(f"Before balancing: {dict(zip(unique, counts))}")

    if strategy == "oversample":
        target_count = max_count
    else:
        target_count = min_count

    X_list = []
    y_list = []

    for cls in unique:
        mask = y == cls
        X_cls = X[mask]
        y_cls = y[mask]

        if len(X_cls) < target_count:
            # Oversample with replacement
            indices = np.random.choice(len(X_cls), target_count, replace=True)
            X_list.append(X_cls[indices])
            y_list.append(y_cls[indices])
        elif len(X_cls) > target_count and strategy == "undersample":
            indices = np.random.choice(len(X_cls), target_count, replace=False)
            X_list.append(X_cls[indices])
            y_list.append(y_cls[indices])
        else:
            X_list.append(X_cls)
            y_list.append(y_cls)

    X_balanced = np.concatenate(X_list)
    y_balanced = np.concatenate(y_list)

    # Shuffle
    indices = np.random.permutation(len(X_balanced))
    X_balanced = X_balanced[indices]
    y_balanced = y_balanced[indices]

    unique2, counts2 = np.unique(y_balanced, return_counts=True)
    print(f"After balancing: {dict(zip(unique2, counts2))}")

    return X_balanced, y_balanced
