#!/usr/bin/env python3
"""Generate PNG preset icons for the DiPlus-to-hass Android dashboard.

Each preset in app/src/main/assets/dashboard_presets.json has an "icon" field
(e.g. "air-conditioner"). This script renders a simple white-on-transparent
64x64 PNG for every known icon name into:

    DiPlus-to-hass/app/src/main/assets/icons/<name>.png

The app resolves icons at runtime (filesDir/icons override -> assets -> emoji
fallback), so these PNGs are the bundled baseline.

Usage: .venv/bin/python scripts/generate_preset_icons.py
"""

import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).parent.parent
OUT_DIR = ROOT / "DiPlus-to-hass" / "app" / "src" / "main" / "assets" / "icons"

SIZE = 64          # final icon edge, px
SS = 4             # supersampling factor for antialiasing
STROKE = 4         # stroke width in final px
WHITE = (255, 255, 255, 255)

W = SIZE * SS
SW = STROKE * SS


def sc(vals):
    return [v * SS for v in vals]


class Canvas:
    """Draws in 64x64 logical coordinates on a supersampled canvas."""

    def __init__(self):
        self.img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def line(self, pts, width=STROKE):
        self.d.line(sc(pts), fill=WHITE, width=width * SS)

    def rect(self, box, outline=True, width=STROKE):
        if outline:
            self.d.rectangle(sc(box), outline=WHITE, width=width * SS)
        else:
            self.d.rectangle(sc(box), fill=WHITE)

    def rrect(self, box, radius, outline=True, width=STROKE):
        if outline:
            self.d.rounded_rectangle(sc(box), radius=radius * SS, outline=WHITE, width=width * SS)
        else:
            self.d.rounded_rectangle(sc(box), radius=radius * SS, fill=WHITE)

    def ellipse(self, box, outline=True, width=STROKE):
        if outline:
            self.d.ellipse(sc(box), outline=WHITE, width=width * SS)
        else:
            self.d.ellipse(sc(box), fill=WHITE)

    def arc(self, box, start, end, width=STROKE):
        self.d.arc(sc(box), start=start, end=end, fill=WHITE, width=width * SS)

    def polygon(self, pts, outline=True, width=STROKE):
        if outline:
            closed = list(pts) + [pts[0]]
            self.d.line(sc([c for p in closed for c in p]), fill=WHITE, width=width * SS,
                        joint="curve")
        else:
            self.d.polygon(sc([c for p in pts for c in p]), fill=WHITE)

    def dot(self, cx, cy, r):
        self.ellipse([cx - r, cy - r, cx + r, cy + r], outline=False)

    def save(self, path):
        out = self.img.resize((SIZE, SIZE), Image.LANCZOS)
        out.save(path)


def polar(cx, cy, r, deg):
    rad = math.radians(deg)
    return (cx + r * math.cos(rad), cy + r * math.sin(rad))


# ─── Icon draw functions (64x64 logical space) ───

def i_air_conditioner(c: Canvas):
    # snowflake
    for ang in range(0, 360, 60):
        x0, y0 = polar(32, 32, 6, ang)
        x1, y1 = polar(32, 32, 22, ang)
        c.line([x0, y0, x1, y1])
        for sign in (-35, 35):
            bx, by = polar(32, 32, 16, ang)
            tx, ty = polar(bx, by, 7, ang + 180 + sign)
            c.line([bx, by, tx, ty])


def i_air_recirculator(c: Canvas):
    c.arc([12, 12, 52, 52], start=50, end=320)
    tip = polar(32, 32, 20, 50)
    p1 = polar(tip[0], tip[1], 9, 50 + 130)
    p2 = polar(tip[0], tip[1], 9, 50 + 220)
    c.polygon([tip, p1, p2], outline=False)


def i_alert(c: Canvas):
    c.polygon([(32, 10), (56, 50), (8, 50)])
    c.line([32, 24, 32, 37])
    c.dot(32, 44, 2.4)


def i_battery(c: Canvas):
    c.rrect([8, 22, 49, 42], 3)
    c.rect([50, 27, 55, 37], outline=False)
    c.rect([14, 28, 32, 36], outline=False)


def i_camera(c: Canvas):
    c.rrect([8, 20, 56, 46], 4)
    c.rect([24, 14, 38, 20], outline=False)
    c.ellipse([25, 24, 39, 38])


def i_car_child_seat(c: Canvas):
    c.rrect([20, 10, 34, 36], 4)          # backrest
    c.dot(27, 18, 4)                      # child head
    c.rrect([16, 36, 48, 46], 4)          # seat base
    c.line([48, 41, 54, 41])


def i_car_door(c: Canvas):
    c.rect([18, 12, 46, 52])
    c.rect([22, 16, 42, 30])
    c.line([34, 40, 41, 40])


def i_car_engine(c: Canvas):
    c.rect([16, 24, 46, 46])
    c.rect([25, 15, 37, 24])
    c.line([16, 31, 9, 31])
    c.line([46, 30, 53, 30])
    c.line([53, 30, 53, 38])
    c.line([46, 40, 50, 40])


def i_car_hatchback(c: Canvas):
    c.polygon([(6, 40), (10, 31), (21, 29), (27, 21), (40, 21), (47, 29), (55, 32), (58, 40)])
    c.ellipse([12, 38, 24, 50])
    c.ellipse([40, 38, 52, 50])
    c.line([28, 23, 34, 29])


def _headlight(c: Canvas, rays, dashed):
    c.arc([8, 16, 40, 48], start=90, end=270)
    c.line([24, 16, 24, 48])
    for y in rays:
        if dashed:
            c.line([34, y, 39, y])
            c.line([44, y, 49, y])
        else:
            c.line([34, y, 50, y])


def i_car_light_dim(c: Canvas):
    _headlight(c, [24, 32, 40], dashed=True)


def i_car_light_high(c: Canvas):
    _headlight(c, [24, 32, 40], dashed=False)


def i_car_sentry(c: Canvas):
    c.polygon([(32, 8), (50, 15), (50, 31), (32, 54), (14, 31), (14, 15)])
    c.ellipse([23, 25, 41, 37])
    c.dot(32, 31, 3)


def i_car_settings(c: Canvas):
    c.ellipse([23, 23, 41, 41])
    for ang in range(0, 360, 45):
        x0, y0 = polar(32, 32, 13, ang)
        x1, y1 = polar(32, 32, 21, ang)
        c.line([x0, y0, x1, y1], width=6)


def i_car_windshield(c: Canvas):
    c.polygon([(14, 44), (22, 18), (42, 18), (50, 44)])
    c.line([30, 44, 40, 26])
    c.dot(30, 44, 2.5)


def i_ev_plug(c: Canvas):
    c.rrect([20, 16, 44, 36], 4)
    c.line([26, 16, 26, 8])
    c.line([38, 16, 38, 8])
    c.line([32, 36, 32, 46])
    c.arc([32, 38, 52, 58], start=90, end=180)


def i_fan(c: Canvas):
    # three blades drawn as rotated ellipse layers
    for ang in (0, 120, 240):
        layer = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        ld.ellipse(sc([32 - 7, 32 - 24, 32 + 7, 32 - 6]), outline=WHITE, width=SW)
        layer = layer.rotate(ang, resample=Image.BICUBIC, center=(32 * SS, 32 * SS))
        c.img.alpha_composite(layer)
    c.dot(32, 32, 4)


def i_home_assistant(c: Canvas):
    c.line([10, 34, 32, 12])
    c.line([32, 12, 54, 34])
    c.line([17, 29, 17, 52])
    c.line([47, 29, 47, 52])
    c.line([17, 52, 47, 52])
    c.rect([28, 40, 36, 52], outline=False)


def i_led_strip(c: Canvas):
    c.rrect([8, 26, 56, 38], 3)
    for x in (17, 28, 39, 50):
        c.dot(x, 32, 2.6)


def _bulb(c: Canvas):
    c.ellipse([20, 8, 44, 34])
    c.polygon([(26, 36), (38, 36), (36, 45), (28, 45)], outline=False)
    c.line([28, 49, 36, 49])


def i_lightbulb(c: Canvas):
    _bulb(c)


def i_lightbulb_on(c: Canvas):
    _bulb(c)
    for ang in range(0, 360, 60):
        x0, y0 = polar(32, 21, 16, ang)
        x1, y1 = polar(32, 21, 21, ang)
        c.line([x0, y0, x1, y1])


def i_lightbulb_off(c: Canvas):
    _bulb(c)
    c.line([12, 52, 52, 10])


def _padlock_body(c: Canvas):
    c.rrect([18, 28, 46, 52], 4)
    c.dot(32, 40, 3.5)


def i_lock(c: Canvas):
    c.arc([22, 8, 42, 30], start=180, end=360)
    _padlock_body(c)


def i_lock_open(c: Canvas):
    c.arc([22, 8, 42, 30], start=180, end=330)
    _padlock_body(c)


def i_map_marker_distance(c: Canvas):
    c.ellipse([22, 8, 42, 28])
    c.polygon([(24, 24), (40, 24), (32, 42)])
    c.dot(32, 18, 3)
    for x in range(12, 53, 10):
        c.line([x, 52, x + 5, 52])


def i_minus(c: Canvas):
    c.line([16, 32, 48, 32], width=6)


def i_mirror(c: Canvas):
    c.rrect([10, 22, 34, 40], 6)
    c.polygon([(34, 28), (54, 34), (54, 38), (34, 36)], outline=False)


def i_plus(c: Canvas):
    c.line([32, 16, 32, 48], width=6)
    c.line([16, 32, 48, 32], width=6)


def i_speedometer(c: Canvas):
    c.arc([12, 12, 52, 52], start=160, end=20)
    for ang in (180, 225, 270, 315, 0):
        x0, y0 = polar(32, 32, 16, ang)
        x1, y1 = polar(32, 32, 20, ang)
        c.line([x0, y0, x1, y1], width=3)
    nx, ny = polar(32, 36, 13, 315)
    c.line([32, 36, nx, ny])
    c.dot(32, 36, 3)


def i_steering(c: Canvas):
    c.ellipse([10, 10, 54, 54])
    c.ellipse([26, 26, 38, 38])
    c.line([26, 30, 12, 22])
    c.line([38, 30, 52, 22])
    c.line([32, 38, 32, 54])


def i_thermometer(c: Canvas):
    c.rrect([26, 8, 38, 42], 6)
    c.ellipse([21, 36, 43, 58])
    c.line([32, 18, 32, 46], width=4)
    c.dot(32, 47, 5)


def i_tire(c: Canvas):
    c.ellipse([8, 8, 56, 56], width=5)
    c.ellipse([24, 24, 40, 40])
    for ang in range(0, 360, 60):
        x, y = polar(32, 32, 20, ang)
        c.dot(x, y, 2)


def i_volume_high(c: Canvas):
    c.polygon([(10, 26), (20, 26), (32, 14), (32, 50), (20, 38), (10, 38)], outline=False)
    c.arc([32, 22, 46, 42], start=-55, end=55)
    c.arc([34, 14, 56, 50], start=-55, end=55)


def i_window_closed(c: Canvas):
    c.rect([14, 14, 50, 50], outline=False)


def i_window_open(c: Canvas):
    c.line([14, 50, 14, 14])
    c.line([14, 14, 50, 14])
    c.line([50, 14, 50, 50])
    c.line([14, 50, 26, 50])
    c.line([38, 50, 50, 50])


def i_window_open_variant(c: Canvas):
    c.line([14, 50, 14, 14])
    c.line([14, 14, 50, 14])
    c.line([50, 14, 50, 50])
    c.line([14, 50, 50, 50])
    c.line([28, 36, 42, 22])
    c.line([32, 22, 42, 22])
    c.line([42, 22, 42, 32])


ICONS = {
    "air-conditioner": i_air_conditioner,
    "air-recirculator": i_air_recirculator,
    "alert": i_alert,
    "battery": i_battery,
    "camera": i_camera,
    "car-child-seat": i_car_child_seat,
    "car-door": i_car_door,
    "car-engine": i_car_engine,
    "car-hatchback": i_car_hatchback,
    "car-light-dim": i_car_light_dim,
    "car-light-high": i_car_light_high,
    "car-sentry": i_car_sentry,
    "car-settings": i_car_settings,
    "car-windshield": i_car_windshield,
    "ev-plug": i_ev_plug,
    "fan": i_fan,
    "home-assistant": i_home_assistant,
    "led-strip": i_led_strip,
    "lightbulb": i_lightbulb,
    "lightbulb-off": i_lightbulb_off,
    "lightbulb-on": i_lightbulb_on,
    "lock": i_lock,
    "lock-open": i_lock_open,
    "map-marker-distance": i_map_marker_distance,
    "minus": i_minus,
    "mirror": i_mirror,
    "plus": i_plus,
    "speedometer": i_speedometer,
    "steering": i_steering,
    "thermometer": i_thermometer,
    "tire": i_tire,
    "volume-high": i_volume_high,
    "window-closed": i_window_closed,
    "window-open": i_window_open,
    "window-open-variant": i_window_open_variant,
}


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, fn in sorted(ICONS.items()):
        canvas = Canvas()
        fn(canvas)
        canvas.save(OUT_DIR / f"{name}.png")
    print(f"Wrote {len(ICONS)} icons to {OUT_DIR}")


if __name__ == "__main__":
    main()
