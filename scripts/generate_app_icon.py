#!/usr/bin/env python3
"""Generate Android launcher icons for the Car2Hass app.

Design: car in front of a house ("car + home" = Car2Hass).
Outputs to Car2Hass/app/src/main/res/mipmap-*/ic_launcher.png
and docs/cartelemetry/assets/app-icon.png (512).
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).parent.parent
OUT_BASE = ROOT / "Car2Hass" / "app" / "src" / "main" / "res"
SITE_ASSET = ROOT / "docs" / "cartelemetry" / "assets"

# Density -> size in px
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

HOUSE_WALL = (100, 181, 246, 255)   # light blue
HOUSE_ROOF = (25, 118, 210, 255)    # blue 600
CAR_BODY = (13, 71, 161, 255)       # dark blue
CAR_CABIN = (69, 90, 100, 255)      # blue gray
CAR_WINDOW = (144, 202, 249, 255)   # pale blue
WHEEL = (38, 50, 56, 255)           # near black
HUBCAP = (176, 190, 197, 255)       # gray


def draw_house(draw, bbox):
    """Background house: walls, roof, chimney, door."""
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
    # chimney
    cw = max(1, int(w * 0.12))
    ch = int(h * 0.20)
    cx = x1 - margin - int(w * 0.20)
    draw.rectangle([cx, y0 + int(h * 0.14), cx + cw, wall_top], fill=HOUSE_ROOF)
    # door
    dw = int(w * 0.18)
    dh = int(h * 0.30)
    draw.rectangle([(x0 + x1) // 2 - dw // 2, y1 - dh,
                    (x0 + x1) // 2 + dw // 2, y1], fill=HOUSE_ROOF)


def draw_car(draw, bbox):
    """Foreground car, side view: body, cabin, window, two wheels."""
    x0, y0, x1, y1 = bbox
    w = x1 - x0
    h = y1 - y0
    body_top = y0 + int(h * 0.35)

    # cabin (trapezoid above the body)
    draw.polygon([
        (x0 + int(w * 0.22), body_top),
        (x0 + int(w * 0.32), y0),
        (x0 + int(w * 0.70), y0),
        (x0 + int(w * 0.80), body_top),
    ], fill=CAR_CABIN)
    # window inside the cabin
    draw.polygon([
        (x0 + int(w * 0.30), body_top - int(h * 0.06)),
        (x0 + int(w * 0.37), y0 + int(h * 0.10)),
        (x0 + int(w * 0.65), y0 + int(h * 0.10)),
        (x0 + int(w * 0.73), body_top - int(h * 0.06)),
    ], fill=CAR_WINDOW)

    # body with rounded ends
    draw.rounded_rectangle([x0, body_top, x1, y0 + int(h * 0.78)],
                           radius=int(h * 0.12), fill=CAR_BODY)

    # wheels
    wheel_r = int(h * 0.16)
    wheel_cy = y0 + int(h * 0.80)
    for cx in (x0 + int(w * 0.26), x1 - int(w * 0.26)):
        draw.ellipse([cx - wheel_r, wheel_cy - wheel_r,
                      cx + wheel_r, wheel_cy + wheel_r], fill=WHEEL)
        hub = int(wheel_r * 0.45)
        draw.ellipse([cx - hub, wheel_cy - hub, cx + hub, wheel_cy + hub],
                     fill=HUBCAP)


def create_icon(size: int):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # house in the background, upper part
    house_bbox = (
        int(size * 0.16), int(size * 0.06),
        int(size * 0.84), int(size * 0.52),
    )
    draw_house(draw, house_bbox)

    # car in the foreground, lower part
    car_bbox = (
        int(size * 0.08), int(size * 0.42),
        int(size * 0.92), int(size * 0.94),
    )
    draw_car(draw, car_bbox)
    return img


def main():
    for density, size in DENSITIES.items():
        out_dir = OUT_BASE / f"mipmap-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)
        icon = create_icon(size)
        icon.save(out_dir / "ic_launcher.png", "PNG")
        print(f"Saved {out_dir}/ic_launcher.png ({size}x{size})")

    SITE_ASSET.mkdir(parents=True, exist_ok=True)
    create_icon(512).save(SITE_ASSET / "app-icon.png", "PNG")
    print(f"Saved {SITE_ASSET / 'app-icon.png'} (512x512)")


if __name__ == "__main__":
    main()
