#!/usr/bin/env python3
"""Generate icons for the diplus2hass Home Assistant integration.

Design: "BYD+" text with a cloud motif.
Outputs:
  - custom_components/diplus2hass/icon.png  (512x512, transparent)
  - custom_components/diplus2hass/logo.png  (1024x512, transparent)
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).parent.parent
OUT_DIR = ROOT / "custom_components" / "diplus2hass"
OUT_DIR.mkdir(parents=True, exist_ok=True)


CLOUD_FILL = (100, 181, 246, 255)      # light blue
CLOUD_OUTLINE = (13, 71, 161, 255)     # dark blue
TEXT_COLOR = (13, 71, 161, 255)        # dark blue
PLUS_COLOR = (25, 118, 210, 255)       # medium blue


def load_font(size: int):
    return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", size)


def text_size(draw, text, font):
    bbox = draw.textbbox((0, 0), text, font=font)
    return bbox[2] - bbox[0], bbox[3] - bbox[1]


def draw_cloud(draw, bbox, fill, outline, outline_width=2):
    """Draw a fluffy cloud from overlapping ellipses."""
    x0, y0, x1, y1 = bbox
    w = x1 - x0
    h = y1 - y0

    # Helper to draw one puff
    def puff(px0, py0, px1, py1):
        draw.ellipse([px0, py0, px1, py1], fill=fill)
        if outline:
            draw.ellipse([px0, py0, px1, py1], outline=outline, width=outline_width)

    # Bottom body
    puff(x0 + w * 0.15, y0 + h * 0.35, x0 + w * 0.85, y1)
    # Left puff
    puff(x0 + w * 0.05, y0 + h * 0.25, x0 + w * 0.40, y0 + h * 0.75)
    # Center/top puff
    puff(x0 + w * 0.28, y0, x0 + w * 0.72, y0 + h * 0.65)
    # Right puff
    puff(x0 + w * 0.60, y0 + h * 0.25, x0 + w * 0.95, y0 + h * 0.75)


def create_icon(size: int):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Cloud above center
    cloud_margin = int(size * 0.16)
    cloud_h = int(size * 0.30)
    cloud_y = int(size * 0.12)
    cloud_bbox = (cloud_margin, cloud_y, size - cloud_margin, cloud_y + cloud_h)
    draw_cloud(draw, cloud_bbox, CLOUD_FILL, CLOUD_OUTLINE, outline_width=max(2, size // 180))

    # Text "BYD"
    font_size = int(size * 0.26)
    font = load_font(font_size)
    text = "BYD"
    text_w, text_h = text_size(draw, text, font)
    text_x = (size - text_w) // 2 - int(size * 0.02)
    text_y = cloud_bbox[3] - int(size * 0.04)
    draw.text((text_x, text_y), text, font=font, fill=TEXT_COLOR)

    # Plus sign next to the D
    plus_size = int(font_size * 0.55)
    plus_thickness = max(3, size // 60)
    plus_x = text_x + text_w + int(size * 0.01)
    plus_y = text_y + int(text_h * 0.12)
    # Horizontal bar
    draw.rectangle(
        [plus_x, plus_y + plus_size // 2 - plus_thickness // 2,
         plus_x + plus_size, plus_y + plus_size // 2 + plus_thickness // 2],
        fill=PLUS_COLOR
    )
    # Vertical bar
    draw.rectangle(
        [plus_x + plus_size // 2 - plus_thickness // 2, plus_y,
         plus_x + plus_size // 2 + plus_thickness // 2, plus_y + plus_size],
        fill=PLUS_COLOR
    )

    return img


def create_logo(width: int, height: int):
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Cloud on the left
    cloud_size = int(height * 0.60)
    cloud_x = int(width * 0.08)
    cloud_y = (height - cloud_size) // 2
    cloud_bbox = (cloud_x, cloud_y, cloud_x + cloud_size, cloud_y + cloud_size)
    draw_cloud(draw, cloud_bbox, CLOUD_FILL, CLOUD_OUTLINE, outline_width=max(2, height // 90))

    # Text "BYD"
    font_size = int(height * 0.45)
    font = load_font(font_size)
    text = "BYD"
    text_w, text_h = text_size(draw, text, font)
    text_x = int(width * 0.48)
    text_y = (height - text_h) // 2
    draw.text((text_x, text_y), text, font=font, fill=TEXT_COLOR)

    # Plus sign
    plus_size = int(font_size * 0.55)
    plus_thickness = max(4, height // 70)
    plus_x = text_x + text_w + int(width * 0.015)
    plus_y = text_y + int(text_h * 0.10)
    draw.rectangle(
        [plus_x, plus_y + plus_size // 2 - plus_thickness // 2,
         plus_x + plus_size, plus_y + plus_size // 2 + plus_thickness // 2],
        fill=PLUS_COLOR
    )
    draw.rectangle(
        [plus_x + plus_size // 2 - plus_thickness // 2, plus_y,
         plus_x + plus_size // 2 + plus_thickness // 2, plus_y + plus_size],
        fill=PLUS_COLOR
    )

    return img


def main():
    icon = create_icon(512)
    logo = create_logo(1024, 512)

    icon_path = OUT_DIR / "icon.png"
    logo_path = OUT_DIR / "logo.png"

    icon.save(icon_path, "PNG")
    logo.save(logo_path, "PNG")

    print(f"Saved {icon_path}")
    print(f"Saved {logo_path}")


if __name__ == "__main__":
    main()
