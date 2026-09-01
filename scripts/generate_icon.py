#!/usr/bin/env python3
"""Generate icons for the CARTelemetry Home Assistant integration.

Design: car in front of a house + gear (telemetry/control motif).
Outputs:
  - custom_components/cartelemetry/icon.png  (512x512, transparent)
  - custom_components/cartelemetry/logo.png  (1024x512, transparent)
Copies are also written to docs/cartelemetry/assets/ for the site.
"""

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).parent.parent
OUT_DIR = ROOT / "custom_components" / "cartelemetry"
SITE_ASSET = ROOT / "docs" / "cartelemetry" / "assets"

HOUSE_WALL = (100, 181, 246, 255)   # light blue
HOUSE_ROOF = (25, 118, 210, 255)    # blue 600
CAR_BODY = (13, 71, 161, 255)       # dark blue
CAR_CABIN = (69, 90, 100, 255)      # blue gray
CAR_WINDOW = (144, 202, 249, 255)   # pale blue
WHEEL = (38, 50, 56, 255)
HUBCAP = (176, 190, 197, 255)
GEAR_COLOR = (255, 143, 0, 255)     # amber
TEXT_COLOR = (13, 71, 161, 255)


def load_font(size: int):
    return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", size)


def draw_house(draw, bbox):
    x0, y0, x1, y1 = bbox
    w = x1 - x0
    h = y1 - y0
    wall_top = y0 + int(h * 0.40)
    margin = int(w * 0.08)

    draw.rectangle([x0 + margin, wall_top, x1 - margin, y1], fill=HOUSE_WALL)
    draw.polygon([
        (x0 + margin - int(w * 0.05), wall_top),
        (x1 - margin + int(w * 0.05), wall_top),
        ((x0 + x1) // 2, y0),
    ], fill=HOUSE_ROOF)
    cw = max(1, int(w * 0.12))
    cx = x1 - margin - int(w * 0.20)
    draw.rectangle([cx, y0 + int(h * 0.14), cx + cw, wall_top], fill=HOUSE_ROOF)
    dw = int(w * 0.18)
    dh = int(h * 0.30)
    draw.rectangle([(x0 + x1) // 2 - dw // 2, y1 - dh,
                    (x0 + x1) // 2 + dw // 2, y1], fill=HOUSE_ROOF)


def draw_car(draw, bbox):
    x0, y0, x1, y1 = bbox
    w = x1 - x0
    h = y1 - y0
    body_top = y0 + int(h * 0.35)

    draw.polygon([
        (x0 + int(w * 0.22), body_top),
        (x0 + int(w * 0.32), y0),
        (x0 + int(w * 0.70), y0),
        (x0 + int(w * 0.80), body_top),
    ], fill=CAR_CABIN)
    draw.polygon([
        (x0 + int(w * 0.30), body_top - int(h * 0.06)),
        (x0 + int(w * 0.37), y0 + int(h * 0.10)),
        (x0 + int(w * 0.65), y0 + int(h * 0.10)),
        (x0 + int(w * 0.73), body_top - int(h * 0.06)),
    ], fill=CAR_WINDOW)
    draw.rounded_rectangle([x0, body_top, x1, y0 + int(h * 0.78)],
                           radius=int(h * 0.12), fill=CAR_BODY)
    wheel_r = int(h * 0.16)
    wheel_cy = y0 + int(h * 0.80)
    for cx in (x0 + int(w * 0.26), x1 - int(w * 0.26)):
        draw.ellipse([cx - wheel_r, wheel_cy - wheel_r,
                      cx + wheel_r, wheel_cy + wheel_r], fill=WHEEL)
        hub = int(wheel_r * 0.45)
        draw.ellipse([cx - hub, wheel_cy - hub, cx + hub, wheel_cy + hub],
                     fill=HUBCAP)


def draw_gear(draw, cx, cy, radius, color):
    """Gear with 8 teeth and a center hole punched to transparency."""
    teeth = 8
    tooth_w = radius * 0.42   # half-width of a tooth at the rim
    tooth_h = radius * 0.30   # tooth length beyond the rim
    for i in range(teeth):
        angle = 2 * math.pi * i / teeth
        # tooth as a rotated rectangle: base points on the rim circle
        ca, sa = math.cos(angle), math.sin(angle)
        px, py = -sa, ca  # perpendicular
        bx, by = cx + ca * radius * 0.92, cy + sa * radius * 0.92
        tx, ty = cx + ca * (radius + tooth_h), cy + sa * (radius + tooth_h)
        draw.polygon([
            (bx + px * tooth_w, by + py * tooth_w),
            (tx + px * tooth_w * 0.7, ty + py * tooth_w * 0.7),
            (tx - px * tooth_w * 0.7, ty - py * tooth_w * 0.7),
            (bx - px * tooth_w, by - py * tooth_w),
        ], fill=color)
    draw.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=color)
    hole = radius * 0.40
    draw.ellipse([cx - hole, cy - hole, cx + hole, cy + hole], fill=(0, 0, 0, 0))


def create_icon(size: int) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    draw_house(draw, (int(size * 0.18), int(size * 0.06),
                      int(size * 0.86), int(size * 0.50)))
    draw_car(draw, (int(size * 0.08), int(size * 0.40),
                    int(size * 0.94), int(size * 0.88)))

    # gear in the lower-right corner over an outline ring for contrast
    gr = int(size * 0.17)
    gx, gy = size - gr - int(size * 0.06), size - gr - int(size * 0.06)
    ring = gr + int(size * 0.02)
    draw.ellipse([gx - ring, gy - ring, gx + ring, gy + ring], fill=(255, 255, 255, 230))
    draw_gear(draw, gx, gy, gr, GEAR_COLOR)
    return img


def create_logo(width: int = 1024, height: int = 512) -> Image.Image:
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    scene = create_icon(height)
    img.paste(scene, (0, 0))

    draw = ImageDraw.Draw(img)
    area_x0, area_x1 = height + int(width * 0.02), width - int(width * 0.03)
    area_w = area_x1 - area_x0

    def fit_font(text, base_px):
        px = base_px
        while px > 8:
            f = load_font(px)
            b = draw.textbbox((0, 0), text, font=f)
            if b[2] - b[0] <= area_w:
                return f
            px -= 4
        return load_font(px)

    title = "CARTelemetry"
    sub = "Vehicle Telemetry for Home Assistant"

    title_font = fit_font(title, int(height * 0.16))
    sub_font = fit_font(sub, int(height * 0.065))

    tb = draw.textbbox((0, 0), title, font=title_font)
    sb = draw.textbbox((0, 0), sub, font=sub_font)
    draw.text((area_x0 + (area_w - (tb[2] - tb[0])) // 2,
               int(height * 0.34)), title, font=title_font, fill=TEXT_COLOR)
    draw.text((area_x0 + (area_w - (sb[2] - sb[0])) // 2,
               int(height * 0.56)), sub, font=sub_font, fill=(69, 90, 100, 255))
    return img


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    create_icon(512).save(OUT_DIR / "icon.png", "PNG")
    print(f"Saved {OUT_DIR / 'icon.png'} (512x512)")
    create_logo().save(OUT_DIR / "logo.png", "PNG")
    print(f"Saved {OUT_DIR / 'logo.png'} (1024x512)")

    SITE_ASSET.mkdir(parents=True, exist_ok=True)
    create_icon(256).save(SITE_ASSET / "integration-icon.png", "PNG")
    create_logo(1024, 512).save(SITE_ASSET / "integration-logo.png", "PNG")
    print(f"Saved site assets in {SITE_ASSET}")


if __name__ == "__main__":
    main()
