#!/usr/bin/env python3
"""Generate Android launcher icons for the DiPlus-to-hass app.

Design: blue house with "D+" text on top.
Outputs to DiPlus-to-hass/app/src/main/res/mipmap-*/ic_launcher.png.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).parent.parent
OUT_BASE = ROOT / "DiPlus-to-hass" / "app" / "src" / "main" / "res"

# Density -> size in px
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

HOUSE_COLOR = (25, 118, 210, 255)    # blue 600
PLUS_COLOR = (255, 255, 255, 255)    # white
TEXT_COLOR = (255, 255, 255, 255)    # white


def load_font(size: int):
    return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", size)


def text_size(draw, text, font):
    bbox = draw.textbbox((0, 0), text, font=font)
    return bbox[2] - bbox[0], bbox[3] - bbox[1]


def draw_house(draw, bbox):
    """Draw a simple house inside bbox."""
    x0, y0, x1, y1 = bbox
    w = x1 - x0
    h = y1 - y0

    # Walls (rectangle)
    wall_top = y0 + int(h * 0.42)
    wall_margin = int(w * 0.10)
    draw.rectangle(
        [x0 + wall_margin, wall_top, x1 - wall_margin, y1 - int(h * 0.05)],
        fill=HOUSE_COLOR,
    )

    # Roof (triangle)
    roof_points = [
        (x0 + wall_margin - int(w * 0.05), wall_top),
        (x1 - wall_margin + int(w * 0.05), wall_top),
        ((x0 + x1) // 2, y0 + int(h * 0.08)),
    ]
    draw.polygon(roof_points, fill=HOUSE_COLOR)

    # Chimney
    chimney_w = int(w * 0.12)
    chimney_h = int(h * 0.22)
    chimney_x = x1 - wall_margin - int(w * 0.18)
    chimney_y = y0 + int(h * 0.18)
    draw.rectangle(
        [chimney_x, chimney_y, chimney_x + chimney_w, chimney_y + chimney_h],
        fill=HOUSE_COLOR,
    )


def create_icon(size: int):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Transparent background with optional subtle shadow under the house
    shadow_offset = max(1, size // 40)
    house_margin = size // 8
    house_bbox = (
        house_margin + shadow_offset,
        house_margin + shadow_offset,
        size - house_margin + shadow_offset,
        size - house_margin + shadow_offset,
    )
    draw_house(draw, house_bbox)

    # Main house (no shadow)
    house_bbox = (
        house_margin,
        house_margin,
        size - house_margin,
        size - house_margin,
    )
    draw_house(draw, house_bbox)

    # "D+" text centered on the house
    font_size = int(size * 0.38)
    font = load_font(font_size)
    text = "D+"
    text_w, text_h = text_size(draw, text, font)
    text_x = (size - text_w) // 2
    text_y = (size - text_h) // 2 + int(size * 0.02)
    draw.text((text_x, text_y), text, font=font, fill=TEXT_COLOR)

    return img


def main():
    for density, size in DENSITIES.items():
        out_dir = OUT_BASE / f"mipmap-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)
        icon = create_icon(size)
        icon.save(out_dir / "ic_launcher.png", "PNG")
        print(f"Saved {out_dir}/ic_launcher.png ({size}x{size})")


if __name__ == "__main__":
    main()
