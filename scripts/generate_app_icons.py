"""Generate GoWorkBro launcher and tray icons for every supported target.

Design (V6, user-approved 2026-08): tomato-red rounded rectangle, a small
green dot in the top-right corner, and a TODO-style white checkmark centered.
Geometry/palette mirror tools/gen_icon_concepts.py VARIANTS[5] (v6_minimal).
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]

# --- V6 palette ---
BG = (229, 72, 77, 255)      # tomato red
DOT = (16, 185, 129, 255)    # green dot
WHITE = (255, 255, 255, 255)

# --- V6 geometry (fractions of icon size) ---
RADIUS = 0.25                     # rounded-rect corner radius
CHECK_W = 0.075                   # checkmark stroke width
DOT_R = 0.125                     # green dot radius
DOT_CX, DOT_CY = 0.785, 0.215     # green dot center (top-right)
CHECK_PTS = [                     # checkmark polyline (cx±, cy±)
    (0.315, 0.575),
    (0.445, 0.705),
    (0.665, 0.430),
]


def render(size: int, *, round_crop: bool = False) -> Image.Image:
    """Render the V6 icon at `size` px (supersampled).

    round_crop=True produces an Android round-icon: the full design is
    shrunk into the inscribed circle so the green dot survives the crop.
    """
    scale = 4
    S = size * scale
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)

    # Tomato-red rounded rectangle (full-bleed, transparent corners)
    draw.rounded_rectangle((0, 0, S - 1, S - 1), radius=round(RADIUS * S), fill=BG)

    # Green dot in the top-right corner
    dcx, dcy = DOT_CX * S, DOT_CY * S
    dr = DOT_R * S
    draw.ellipse((dcx - dr, dcy - dr, dcx + dr, dcy + dr), fill=DOT)

    # TODO-style checkmark: thick line, round caps and round joint
    w = round(CHECK_W * S)
    pts = [(x * S, y * S) for x, y in CHECK_PTS]
    draw.line(pts, fill=WHITE, width=w, joint="curve")
    r = w / 2.0
    for px, py in pts:
        draw.ellipse((px - r, py - r, px + r, py + r), fill=WHITE)

    img = canvas.resize((size, size), Image.Resampling.LANCZOS)

    if round_crop:
        target = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        inner = round(size * 0.78)
        resized = img.resize((inner, inner), Image.Resampling.LANCZOS)
        target.alpha_composite(resized, ((size - inner) // 2, (size - inner) // 2))
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        r_, g_, b_, a_ = target.split()
        target = Image.merge(
            "RGBA",
            (r_, g_, b_, Image.composite(a_, Image.new("L", (size, size), 0), mask)),
        )
        img = target

    return img


def main() -> None:
    icons = ROOT / "assets" / "icons"
    icons.mkdir(parents=True, exist_ok=True)
    app = render(1024)
    app.resize((256, 256), Image.Resampling.LANCZOS).save(icons / "app_icon.png")

    ico_sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    app.save(icons / "app_icon.ico", format="ICO", sizes=ico_sizes)
    app.save(
        ROOT / "windows" / "runner" / "resources" / "app_icon.ico",
        format="ICO",
        sizes=ico_sizes,
    )

    tray = render(64)
    tray.save(
        icons / "tray_icon.ico",
        format="ICO",
        sizes=[(16, 16), (20, 20), (24, 24), (32, 32), (48, 48), (64, 64)],
    )

    android_sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    res = ROOT / "android" / "app" / "src" / "main" / "res"
    for folder, size in android_sizes.items():
        destination = res / folder
        destination.mkdir(parents=True, exist_ok=True)
        render(size).save(destination / "ic_launcher.png")
        render(size, round_crop=True).save(destination / "ic_launcher_round.png")


if __name__ == "__main__":
    main()
