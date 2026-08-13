# -*- coding: utf-8 -*-
"""Generate GoWorkBro icon concept variants: tomato-red rounded rect,
green dot top-right, TODO-style checkmark center."""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = r"D:\Workspace\GoWorkBro\assets\icons\concepts"
os.makedirs(OUT, exist_ok=True)

SS = 4                 # supersample factor
S = 1024 * SS          # working canvas 4096
OUT_S = 1024

# (name, bg, radius_frac, check_w_frac, dot_r_frac, dot_color, dot_stroke_w_frac, shadow_alpha, dot_center)
VARIANTS = [
    # V1 经典均衡: 中等圆角, 白描边绿点, 标准粗对勾, 柔和阴影
    dict(name="v1_classic",     bg=(229, 57, 53),  radius=0.22, cw=0.088, dr=0.145,
         dot=(34, 197, 94),  stroke=0.012, shadow=110, dot_center=(0.780, 0.220)),
    # V2 徽章圆润: 更圆角, 粗白描边绿点(徽章感), 更粗对勾, 深阴影
    dict(name="v2_badge",       bg=(224, 53, 47),  radius=0.28, cw=0.095, dr=0.145,
         dot=(34, 197, 94),  stroke=0.018, shadow=150, dot_center=(0.755, 0.225)),
    # V3 鲜艳番茄: 经典 tomato 色, 扁平无阴影, 细描边绿点, 标准对勾
    dict(name="v3_tomato",      bg=(255, 99, 71),  radius=0.20, cw=0.082, dr=0.140,
         dot=(74, 222, 128), stroke=0.008, shadow=0,   dot_center=(0.785, 0.215)),
    # V4 深红精致: 深番茄红, 小圆角, 细对勾, 小绿点, 弱阴影
    dict(name="v4_deep",        bg=(198, 40, 40),  radius=0.18, cw=0.070, dr=0.115,
         dot=(46, 204, 113), stroke=0.010, shadow=70,  dot_center=(0.790, 0.210)),
    # V5 圆润可爱: 超大圆角, 超粗圆头对勾, 大绿点粗描边(Q版)
    dict(name="v5_cute",        bg=(240, 68, 56),  radius=0.32, cw=0.105, dr=0.150,
         dot=(52, 211, 153), stroke=0.020, shadow=130, dot_center=(0.755, 0.215)),
    # V6 极简扁平: 明亮番茄红, 无阴影, 中等对勾, 小绿点
    dict(name="v6_minimal",     bg=(229, 72, 77),  radius=0.25, cw=0.075, dr=0.125,
         dot=(16, 185, 129), stroke=0.000, shadow=0,   dot_center=(0.785, 0.215)),
]

CHECK_COLOR = (255, 255, 255)


def draw_check(d, cx, cy, size, width, color):
    """TODO-style checkmark: thick line, round caps and round joint."""
    x0 = cx - 0.185 * size
    y0 = cy + 0.075 * size
    x1 = cx - 0.055 * size
    y1 = cy + 0.205 * size
    x2 = cx + 0.165 * size
    y2 = cy - 0.070 * size
    pts = [(x0, y0), (x1, y1), (x2, y2)]
    d.line(pts, fill=color, width=int(width), joint="curve")
    r = width / 2.0
    for px, py in pts:
        d.ellipse([px - r, py - r, px + r, py + r], fill=color)


def render(v):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bg = v["bg"]
    radius = int(v["radius"] * S)

    # --- shadow layer ---
    if v["shadow"]:
        sh = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        sd = ImageDraw.Draw(sh)
        off = int(0.014 * S)
        sd.rounded_rectangle([off, off + int(0.022 * S), S + off, S + int(0.022 * S)],
                             radius=radius, fill=(0, 0, 0, v["shadow"]))
        sh = sh.filter(ImageFilter.GaussianBlur(int(0.030 * S)))
        img.alpha_composite(sh)

    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, S - 1, S - 1], radius=radius, fill=bg + (255,))

    # subtle top-left highlight for depth
    hl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    hd = ImageDraw.Draw(hl)
    hd.rounded_rectangle([0, 0, S - 1, int(0.52 * S)], radius=radius,
                         fill=(255, 255, 255, 26))
    img.alpha_composite(hl)

    # --- green dot (top-right) ---
    dcx, dcy = v["dot_center"][0] * S, v["dot_center"][1] * S
    dr = v["dr"] * S
    d.ellipse([dcx - dr, dcy - dr, dcx + dr, dcy + dr], fill=v["dot"] + (255,))
    if v["stroke"]:
        sw = v["stroke"] * S
        d.ellipse([dcx - dr, dcy - dr, dcx + dr, dcy + dr],
                  outline=(255, 255, 255, 255), width=int(sw))

    # --- checkmark ---
    draw_check(d, 0.5 * S, 0.5 * S, 1.0 * S, v["cw"] * S, CHECK_COLOR)

    return img.resize((OUT_S, OUT_S), Image.LANCZOS)


def make_sheet():
    cols, rows = 3, 2
    cell, pad, label_h = 340, 36, 74
    W = cols * cell + (cols + 1) * pad
    H = rows * (cell + label_h) + (rows + 1) * pad
    sheet = Image.new("RGBA", (W, H), (246, 247, 249, 255))
    sd = ImageDraw.Draw(sheet)
    font = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 34)
    names = ["V1 经典均衡", "V2 徽章圆润", "V3 鲜艳番茄", "V4 深红精致", "V5 圆润可爱", "V6 极简扁平"]
    for i, v in enumerate(VARIANTS):
        r, c = divmod(i, cols)
        x = pad + c * (cell + pad)
        y = pad + r * (cell + label_h + pad)
        thumb = render(v).resize((cell, cell), Image.LANCZOS)
        sheet.alpha_composite(thumb, (x, y))
        tw = sd.textlength(names[i], font=font)
        sd.text((x + (cell - tw) / 2, y + cell + 14), names[i], font=font,
                fill=(40, 44, 52, 255))
    sheet.convert("RGB").save(os.path.join(OUT, "_sheet.png"))


for v in VARIANTS:
    render(v).convert("RGB").save(os.path.join(OUT, v["name"] + ".png"))
    print("saved", v["name"])

make_sheet()
print("sheet saved")
