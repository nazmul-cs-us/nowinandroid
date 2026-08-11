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

    NOT used by default — see [augment_dataset]. The intent (pocket placement varies) is
    real, but the method is wrong for this task: the classes are themselves orientations
    relative to gravity, so rotating a labelled window can move it onto another class's
    signature. Measured cost is large and consistent, and it grows with the angle
    (leave-one-session-out accuracy 0.31 at 0deg, 0.26 at 15deg, 0.18 at 45deg, 0.11 at
    180deg — same trend when rotating raw signals before feature extraction rather than
    these aggregates). Placement invariance needs calibration into a body frame, not
    randomisation.

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
            # Features 24-25 are pitch_range/roll_range (max - min across the
            # window), NOT pitch/roll values — a fixed rotation shifts angles but
            # leaves their within-window spread essentially unchanged, so the
            # ranges are intentionally left untouched.

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
    include_original: bool = True,
    include_rotation: bool = False
) -> Tuple[np.ndarray, np.ndarray]:
    """
    Apply multiple augmentations to create an expanded dataset.

    Args:
        X: (N, seq_len, features) or (N, features)
        y: (N,) labels
        augmentation_factor: How many augmented copies per original
        include_original: Whether to include original data in output
        include_rotation: Opt back into rotation augmentation. Off by default because it
            hurts these orientation-defined classes; see the note in the body.

    Returns:
        X_aug: Augmented feature array
        y_aug: Corresponding labels
    """
    X_list = []
    y_list = []

    if include_original:
        X_list.append(X)
        y_list.append(y)

    # Rotation is excluded by default: a salah posture IS an orientation relative to
    # gravity, so rotating a window can turn QIYAM into SUJUD's signature while keeping
    # the old label — it manufactures label noise rather than useful variety.
    # Measured on leave-one-session-out over data/train_ready (mean accuracy across the
    # three multi-posture sessions): with rotation 0.425, without it 0.620, and every
    # individual held-out session improved. See augment_rotation's own docstring.
    strategies = ['time_warp', 'noise', 'scaling']
    if include_rotation:
        strategies = ['rotation'] + strategies

    for _ in range(augmentation_factor):
        # Randomly select augmentation strategy
        aug_type = np.random.choice(strategies)

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
    strategy: str = "oversample",
) -> Tuple[np.ndarray, np.ndarray]:
    """
    Balance class distribution without allowing one outlier class to inflate the
    complete training set.

    Args:
        X: Feature array
        y: Labels
        strategy: ``oversample`` minorities to the largest class, ``undersample``
            majorities to the smallest class, or ``hybrid``. Hybrid normally uses
            the largest class, but when it is more than 3x the runner-up it caps the
            target at the runner-up and downsamples the outlier. This is important
            for long NOT_PRAYING captures: otherwise every prayer class is copied to
            the size of a single long negative recording before augmentation.

    Returns:
        Balanced X, y
    """
    unique, counts = np.unique(y, return_counts=True)
    max_count = counts.max()
    min_count = counts.min()

    print(f"Before balancing: {dict(zip(unique, counts))}")

    if strategy == "oversample":
        target_count = max_count
    elif strategy == "undersample":
        target_count = min_count
    elif strategy == "hybrid":
        descending = np.sort(counts)[::-1]
        runner_up = descending[1] if len(descending) > 1 else descending[0]
        target_count = runner_up if max_count > 3 * runner_up else max_count
    else:
        raise ValueError(f"Unknown balancing strategy: {strategy}")

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
        elif len(X_cls) > target_count and strategy in {"undersample", "hybrid"}:
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
