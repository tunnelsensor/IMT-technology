"""
Een snelle voorvertoning van OBJ-modellen (hoofdstuk 74), zonder het spel te starten: een kleine "software-renderer"
met de belichting van Minecraft (twee lampen van boven, 40 % omgevingslicht), de texture zonder vervaging (zoals in het
spel), doorzichtige texels weg, en de achterkant van elk vlak niet getekend (zoals Minecraft).

Draaien (vanuit de hoofdmap), met een json die naar een OBJ wijst:
    python tools/preview_obj.py --out glock.png models/item/glock_19.json
    python tools/preview_obj.py --out slide.png --views left,top models/firearm/glock_19/slide_steel.json
    python tools/preview_obj.py --out hand.png --views fp models/item/glock_19.json models/firearm/glock_19/magazine.json

Meerdere json's worden samen getekend (bijvoorbeeld het pistool en zijn magazijn). Kijkhoeken: left (de linkerkant, de
loop naar links), right (de rechterkant, de loop naar rechts), top, bottom, front, rear, q1..q4 (schuin van boven), fp
(ongeveer zoals in je eigen beeld: van achter-links-boven).
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets"

VIEWS = {
    # (draaiing om y, dan om x), in graden. Zonder draaiing sta je achter het pistool (bij +z) en kijk je naar de loop
    # (-z): +x rechts, +y omhoog. q1/q2: de linkerkant van achter/voor, q3/q4: de rechterkant van achter/voor.
    "left": (90, 0), "right": (-90, 0), "top": (90, 90), "bottom": (90, -90), "front": (180, 0), "rear": (0, 0),
    "q1": (55, 25), "q2": (125, 25), "q3": (-55, 25), "q4": (-125, 25), "fp": (30, 18), "fp2": (18, 8),
}
LIGHT0 = np.array([0.2, 1.0, -0.7])
LIGHT1 = np.array([-0.2, 1.0, 0.7])
LIGHT0 /= np.linalg.norm(LIGHT0)
LIGHT1 /= np.linalg.norm(LIGHT1)


def resource(ref, kind, ext):
    ns, path = ref.split(":") if ":" in ref else ("minecraft", ref)
    return ASSETS / ns / kind / f"{path}{ext}"


def load_model(json_path):
    """Een json met "loader": "neoforge:obj" -> een lijst driehoeken (punten, uv, normalen, texture, gloeit)."""
    model = json.loads(Path(json_path).read_text(encoding="utf-8"))
    textures = model.get("textures", {})
    ns, path = model["model"].split(":")
    obj_path = ASSETS / ns / path
    mtl = {}
    v, vt, vn = [], [], []
    tris = []
    current = None
    for line in obj_path.read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if not parts:
            continue
        if parts[0] == "mtllib":
            mtl = load_mtl(obj_path.parent / parts[1])
        elif parts[0] == "usemtl":
            current = mtl[parts[1]]
        elif parts[0] == "v":
            v.append([float(c) * 16 for c in parts[1:4]])
        elif parts[0] == "vt":
            vt.append([float(c) for c in parts[1:3]])
        elif parts[0] == "vn":
            vn.append([float(c) for c in parts[1:4]])
        elif parts[0] == "f":
            refs = [[int(i) - 1 for i in p.split("/")] for p in parts[1:]]
            tex_ref = current["map"].lstrip("#")
            tex = textures.get(tex_ref, tex_ref)
            for k in range(1, len(refs) - 1):
                tri = [refs[0], refs[k], refs[k + 1]]
                tris.append((np.array([v[r[0]] for r in tri]), np.array([vt[r[1]] for r in tri]),
                             np.array([vn[r[2]] for r in tri]), tex, current["glow"]))
    return tris


def load_mtl(path):
    out, name = {}, None
    for line in path.read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if not parts:
            continue
        if parts[0] == "newmtl":
            name = parts[1]
            out[name] = {"map": "", "glow": False}
        elif parts[0] == "map_Kd":
            out[name]["map"] = parts[1]
        elif parts[0] == "Ka":
            out[name]["glow"] = sum(float(c) for c in parts[1:4]) > 0
    return out


_textures = {}


def texture(ref):
    if ref not in _textures:
        _textures[ref] = np.asarray(Image.open(resource(ref, "textures", ".png")).convert("RGBA")).astype(float)
    return _textures[ref]


def view_matrix(yaw, pitch):
    a, b = math.radians(yaw), math.radians(pitch)
    ry = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    rx = np.array([[1, 0, 0], [0, math.cos(b), -math.sin(b)], [0, math.sin(b), math.cos(b)]])
    return rx @ ry


def render(tris, view, size, centre, scale, background=(150, 150, 156)):
    """Eén beeld: orthografisch, {scale} schermpixels per model-pixel, {centre} in het midden."""
    w, h = size
    colour = np.zeros((h, w, 3))
    colour[:] = background
    depth = np.full((h, w), -1e9)
    m = view_matrix(*view)
    for pts, uv, nrm, tex_ref, glow in tris:
        p = (m @ (pts - centre).T).T
        sx = w / 2 + p[:, 0] * scale
        sy = h / 2 - p[:, 1] * scale
        area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0])
        if area >= 0:  # achterkant (op het scherm met de klok mee, want y wijst omlaag)
            continue
        x0, x1 = max(int(min(sx)), 0), min(int(max(sx)) + 1, w)
        y0, y1 = max(int(min(sy)), 0), min(int(max(sy)) + 1, h)
        if x0 >= x1 or y0 >= y1:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        l0 = ((sx[1] - gx) * (sy[2] - gy) - (sx[2] - gx) * (sy[1] - gy)) / area
        l1 = ((sx[2] - gx) * (sy[0] - gy) - (sx[0] - gx) * (sy[2] - gy)) / area
        l2 = 1 - l0 - l1
        inside_ = (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
        if not inside_.any():
            continue
        z = l0 * p[0, 2] + l1 * p[1, 2] + l2 * p[2, 2]
        u = l0 * uv[0, 0] + l1 * uv[1, 0] + l2 * uv[2, 0]
        v = l0 * uv[0, 1] + l1 * uv[1, 1] + l2 * uv[2, 1]
        tex = texture(tex_ref)
        tx = np.clip((u * tex.shape[1]).astype(int), 0, tex.shape[1] - 1)
        ty = np.clip((v * tex.shape[0]).astype(int), 0, tex.shape[0] - 1)
        texel = tex[ty, tx]
        sub = depth[y0:y1, x0:x1]
        ok = inside_ & (z > sub) & (texel[:, :, 3] >= 25)
        if not ok.any():
            continue
        if glow:
            shade = np.ones(z.shape)
        else:
            n = l0[..., None] * nrm[0] + l1[..., None] * nrm[1] + l2[..., None] * nrm[2]
            n /= np.linalg.norm(n, axis=-1, keepdims=True) + 1e-9
            shade = np.minimum(1.0, 0.4 + 0.6 * (np.maximum(0, n @ LIGHT0) + np.maximum(0, n @ LIGHT1)))
        rgb = texel[:, :, :3] * shade[..., None]
        sub[ok] = z[ok]
        colour[y0:y1, x0:x1][ok] = rgb[ok]
    return Image.fromarray(np.clip(colour, 0, 255).astype(np.uint8), "RGB")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--views", default="left,right,q1,q3,top,fp")
    ap.add_argument("--size", default="700,420")
    ap.add_argument("--scale", type=float, default=0, help="schermpixels per model-pixel (0 = passend)")
    ap.add_argument("--columns", type=int, default=2)
    ap.add_argument("--crop", default="", help="z0,y0,z1,y1 in model-pixels: inzoomen op dat stuk")
    ap.add_argument("--assets", default="", help="een andere assets-map (om een proefversie te bekijken)")
    ap.add_argument("models", nargs="+")
    args = ap.parse_args()
    global ASSETS
    if args.assets:
        ASSETS = Path(args.assets)
    root = ASSETS / "imttech" / "models"
    tris = []
    for name in args.models:
        path = Path(name)
        if not path.exists():
            path = root / name
        tris += load_model(path)
    pts = np.concatenate([t[0] for t in tris])
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    centre = (lo + hi) / 2
    w, h = (int(c) for c in args.size.split(","))
    scale = args.scale or 0.9 * min(w, h) / max(hi - lo)
    if args.crop:
        z0, y0, z1, y1 = (float(c) for c in args.crop.split(","))
        centre = np.array([centre[0], (y0 + y1) / 2, (z0 + z1) / 2])
        scale = min(w / abs(z1 - z0), h / abs(y1 - y0))
    views = args.views.split(",")
    cols = min(args.columns, len(views))
    rows = (len(views) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * w, rows * h), (40, 40, 40))
    draw = ImageDraw.Draw(sheet)
    for i, name in enumerate(views):
        im = render(tris, VIEWS[name], (w, h), centre, scale)
        x, y = (i % cols) * w, (i // cols) * h
        sheet.paste(im, (x, y))
        draw.text((x + 6, y + 4), name, fill=(255, 255, 255))
    sheet.save(args.out)
    print("geschreven:", args.out, f"({len(tris)} driehoeken)")


if __name__ == "__main__":
    sys.exit(main())
