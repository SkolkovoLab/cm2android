import os
from PIL import Image, ImageDraw

SRC = r"D:\Home\Projects\cm2android\tmp\cm2icon.png"
RES = r"D:\Home\Projects\cm2android\app_pojavlauncher\src\main\res"

icon = Image.open(SRC).convert("RGBA")

# density multipliers relative to mdpi (baseline dp)
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
LEGACY_DP = 48   # legacy launcher icon base size
FG_DP = 108      # adaptive-icon foreground base size
FG_FILL = 0.92   # foreground occupies 92% of canvas, rest transparent (adaptive safe margin)


def circle_crop(img):
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, w, h), fill=255)
    out = img.copy()
    out.putalpha(mask)
    return out


def lanczos(img, size):
    return img.resize((size, size), Image.LANCZOS)


for dens, mult in DENSITIES.items():
    folder = os.path.join(RES, "mipmap-" + dens)
    legacy = round(LEGACY_DP * mult)
    fg = round(FG_DP * mult)

    # legacy square launcher icon
    lanczos(icon, legacy).save(os.path.join(folder, "ic_launcher.webp"), "WEBP", quality=90)
    # round launcher icon (circular mask)
    circle_crop(lanczos(icon, legacy)).save(os.path.join(folder, "ic_launcher_round.webp"), "WEBP", quality=90)
    # adaptive foreground: icon centered with transparent margin
    canvas = Image.new("RGBA", (fg, fg), (0, 0, 0, 0))
    inner = round(fg * FG_FILL)
    resized = lanczos(icon, inner)
    off = (fg - inner) // 2
    canvas.paste(resized, (off, off), resized)
    canvas.save(os.path.join(folder, "ic_launcher_foreground.webp"), "WEBP", quality=90)

    print(dens, "legacy=%d fg=%d" % (legacy, fg))

print("done")
