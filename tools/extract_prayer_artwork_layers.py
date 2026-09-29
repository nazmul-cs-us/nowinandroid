#!/usr/bin/env python3
"""Extract controllable sun and mosque-light layers from prayer widget artwork."""

from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
DRAWABLES = ROOT / "app/src/main/res/drawable-nodpi"


def smoothstep(edge0: float, edge1: float, value: np.ndarray) -> np.ndarray:
    amount = np.clip((value - edge0) / (edge1 - edge0), 0.0, 1.0)
    return amount * amount * (3.0 - 2.0 * amount)


def scanline_fill(image: np.ndarray, mask: np.ndarray) -> np.ndarray:
    """Bridge each masked row from its untouched left/right sky boundaries."""
    result = image.copy()
    for y in np.where(mask.any(axis=1))[0]:
        masked_x = np.where(mask[y])[0]
        first, last = int(masked_x[0]), int(masked_x[-1])
        left = image[y, max(0, first - 1)]
        right = image[y, min(image.shape[1] - 1, last + 1)]
        amount = np.linspace(0.0, 1.0, last - first + 1, dtype=np.float32)[:, None]
        result[y, first : last + 1] = left * (1.0 - amount) + right * amount
    return result


def save_webp(array: np.ndarray, path: Path) -> None:
    mode = "RGBA" if array.shape[2] == 4 else "RGB"
    Image.fromarray(np.rint(array).astype(np.uint8), mode).save(
        path,
        "WEBP",
        lossless=True,
        method=6,
    )


def extract_day() -> None:
    source_path = DRAWABLES / "prayer_widget_poster_day_v1.webp"
    original = np.asarray(Image.open(source_path).convert("RGB"), dtype=np.float32)
    height, width = original.shape[:2]
    yy, xx = np.mgrid[:height, :width]

    # Measured directly from the existing 900x1200 artwork.
    center_x, center_y = 245.0, 681.0
    distance = np.sqrt((xx - center_x) ** 2 + (yy - center_y) ** 2)
    removal_mask = distance <= 35.0
    base = scanline_fill(original, removal_mask)

    # The opaque core restores the original disk exactly; the feather only retains its
    # existing antialiased edge. Ambient sunset light remains baked into the clean base.
    alpha = 1.0 - smoothstep(31.0, 53.0, distance)
    layer = np.dstack((original, alpha * 255.0))

    save_webp(base, DRAWABLES / "prayer_widget_poster_day_no_sun_v2.webp")
    save_webp(layer, DRAWABLES / "prayer_widget_day_sun_layer_v1.webp")


def extract_night() -> None:
    source_path = DRAWABLES / "prayer_widget_poster_night_v1.webp"
    original = np.asarray(Image.open(source_path).convert("RGB"), dtype=np.float32)
    height, width = original.shape[:2]
    yy, xx = np.mgrid[:height, :width]
    red, green, blue = original[..., 0], original[..., 1], original[..., 2]
    luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue

    mosque = (xx >= 300) & (xx <= 790) & (yy >= 555) & (yy <= 845)
    water = (xx >= 300) & (xx <= 800) & (yy > 845) & (yy <= 1010)
    warm = (red > blue * 1.22) & (red > green * 1.03) & (red - blue > 28)
    bright = luminance > 92
    reflection_bright = luminance > 105
    core = (mosque & warm & bright) | (water & warm & reflection_bright)

    core_image = Image.fromarray((core * 255).astype(np.uint8), "L")
    expanded = core_image.filter(ImageFilter.MaxFilter(13))
    feathered = expanded.filter(ImageFilter.GaussianBlur(7.0))
    alpha = np.asarray(feathered, dtype=np.float32) / 255.0
    alpha[core] = 1.0

    # Retain architectural detail while replacing warm emission with moonlit blue-gray.
    target = np.stack(
        (
            7.0 + luminance * 0.055,
            15.0 + luminance * 0.085,
            27.0 + luminance * 0.125,
        ),
        axis=-1,
    )
    strength = (alpha * 0.94)[..., None]
    base = original * (1.0 - strength) + target * strength
    layer = np.dstack((original, alpha * 255.0))

    save_webp(base, DRAWABLES / "prayer_widget_poster_night_unlit_v2.webp")
    save_webp(layer, DRAWABLES / "prayer_widget_night_mosque_lights_v1.webp")


def main() -> None:
    extract_day()
    extract_night()


if __name__ == "__main__":
    main()
