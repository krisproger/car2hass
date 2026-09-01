#!/usr/bin/env python3
"""Generate the top-view car image for the dashboard card.

Output: custom_components/cartelemetry/www/car_top.png (512x1024).
Silhouette: body, roof/glass, mirrors, lights — neutral sedan, top view.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).parent.parent
OUT = ROOT / "custom_components" / "cartelemetry" / "www" / "car_top.png"

BODY = (21, 101, 192, 255)      # blue 800
BODY_DARK = (13, 71, 161, 255)  # blue 900
GLASS = (144, 202, 249, 255)    # pale blue
DETAIL = (100, 181, 246, 255)   # light blue


def create(width: int = 512, height: int = 1024) -> Image.Image:
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    cx = width // 2
    body_w = int(width * 0.52)
    x0, x1 = cx - body_w // 2, cx + body_w // 2

    # Body: rounded rectangle with tapered nose/trunk
    d.rounded_rectangle([x0, int(height * 0.06), x1, int(height * 0.94)],
                        radius=int(body_w * 0.38), fill=BODY)

    # Windshield and rear glass (top = front)
    d.rounded_rectangle([x0 + int(body_w * 0.10), int(height * 0.20),
                         x1 - int(body_w * 0.10), int(height * 0.34)],
                        radius=int(body_w * 0.12), fill=GLASS)
    d.rounded_rectangle([x0 + int(body_w * 0.10), int(height * 0.62),
                         x1 - int(body_w * 0.10), int(height * 0.76)],
                        radius=int(body_w * 0.12), fill=GLASS)

    # Roof panel between the glasses
    d.rectangle([x0 + int(body_w * 0.10), int(height * 0.34),
                 x1 - int(body_w * 0.10), int(height * 0.62)], fill=BODY_DARK)

    # Side mirrors
    for mx in (x0, x1):
        d.rounded_rectangle([mx - int(width * 0.045) if mx == x0 else mx,
                             int(height * 0.235),
                             mx if mx == x0 else mx + int(width * 0.045),
                             int(height * 0.285)], radius=8, fill=BODY_DARK)

    # Windshield wipers hint
    d.line([cx - int(body_w * 0.16), int(height * 0.215),
            cx - int(body_w * 0.02), int(height * 0.195)], fill=DETAIL, width=6)

    # Headlights / tail lights
    d.rounded_rectangle([x0 + int(body_w * 0.08), int(height * 0.075),
                         x1 - int(body_w * 0.08), int(height * 0.10)],
                        radius=10, fill=(255, 241, 118, 255))
    d.rounded_rectangle([x0 + int(body_w * 0.08), int(height * 0.90),
                         x1 - int(body_w * 0.08), int(height * 0.925)],
                        radius=10, fill=(239, 83, 80, 255))

    # Subtle outline
    d.rounded_rectangle([x0, int(height * 0.06), x1, int(height * 0.94)],
                        radius=int(body_w * 0.38),
                        outline=BODY_DARK, width=6)

    return img


def main():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img = create()
    # Slight softening so overlays blend nicely
    img = img.filter(ImageFilter.SMOOTH)
    img.save(OUT, "PNG")
    print(f"Saved {OUT}")


if __name__ == "__main__":
    main()
