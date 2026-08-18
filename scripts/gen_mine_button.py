#!/usr/bin/env python3
"""Generate the MineButton nine-patch backgrounds.

The button is a flat face framed by a bevel: light on the top and right edges,
dark on the bottom and left ones. Each of the four corners is a solid square of
its own shade. Nine-patch corners are never stretched, so those squares keep a
fixed size while the straight edges tile to any button size.

Run from the repository root:
    python scripts/gen_mine_button.py
"""
import os

from PIL import Image

RES_DIR = os.path.join("app_pojavlauncher", "src", "main", "res")

# Bevel thickness in dp, and the density buckets we ship it for.
BEVEL_DP = 5
DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

# Sampled from the reference artwork.
PALETTE = {
    "face": 0xFBAC18,
    "top": 0xFDC032,
    "right": 0xFDC032,
    "bottom": 0xA77A0E,
    "left": 0xA77A0E,
    "top_left": 0xDCAC2E,
    "top_right": 0xFEC83D,
    "bottom_left": 0x86660A,
    "bottom_right": 0xA88219,
}

# The pressed state reuses the same artwork dimmed by a constant factor.
PRESSED_FACTOR = 0.85

BLACK = (0, 0, 0, 255)
TRANSPARENT = (0, 0, 0, 0)


def rgba(value, factor=1.0):
    channels = ((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF)
    return tuple(round(channel * factor) for channel in channels) + (255,)


def palette(factor):
    return {name: rgba(value, factor) for name, value in PALETTE.items()}


def content_pixel(x, y, size, bevel, colors):
    """Colour of the button at (x, y) within a size x size content box."""
    on_left, on_top = x < bevel, y < bevel
    on_right, on_bottom = size - 1 - x < bevel, size - 1 - y < bevel
    if on_top:
        vertical = "top"
    elif on_bottom:
        vertical = "bottom"
    else:
        vertical = None
    if on_left:
        horizontal = "left"
    elif on_right:
        horizontal = "right"
    else:
        horizontal = None
    if vertical and horizontal:
        return colors[vertical + "_" + horizontal]
    return colors[vertical or horizontal or "face"]


def build_nine_patch(bevel, colors):
    # One stretchable pixel in the middle of each axis, plus the 1px marker frame.
    size = 2 * bevel + 1
    image = Image.new("RGBA", (size + 2, size + 2), TRANSPARENT)
    for y in range(size):
        for x in range(size):
            image.putpixel((x + 1, y + 1), content_pixel(x, y, size, bevel, colors))
    center = bevel + 1
    image.putpixel((center, 0), BLACK)             # stretchable columns
    image.putpixel((0, center), BLACK)             # stretchable rows
    image.putpixel((center, size + 1), BLACK)      # horizontal content padding
    image.putpixel((size + 1, center), BLACK)      # vertical content padding
    return image


def main():
    variants = {
        "mine_button_normal": palette(1.0),
        "mine_button_pressed": palette(PRESSED_FACTOR),
    }
    for density, scale in DENSITIES.items():
        bevel = round(BEVEL_DP * scale)
        directory = os.path.join(RES_DIR, "drawable-" + density)
        os.makedirs(directory, exist_ok=True)
        for name, colors in variants.items():
            path = os.path.join(directory, name + ".9.png")
            build_nine_patch(bevel, colors).save(path)
            print("%s (bevel %dpx)" % (path, bevel))


if __name__ == "__main__":
    main()
