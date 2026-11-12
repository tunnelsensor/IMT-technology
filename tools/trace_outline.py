"""
Een hulpje om de omtrek van een wapen van een foto te halen (hoofdstuk 103: de MP5, AK-47, AWP en Remington 870 "zoals de
Glock"). Het model zelf gebruikt de foto niet: je meet hier één keer de punten op, en plakt die als getallen in het
modelbestand (tools/mp5_model.py enz.), net zoals de punten van de Glock in tools/glock19_model.py staan.

Wat het kan:
- grid: de foto (of een stuk ervan) met een raster en getallen erlangs, om met het oog punten af te lezen;
- outline: van een foto met een egale achtergrond (wit, of doorzichtig) de buitenrand van het wapen als veelhoek, of van
  een stuk ervan ("--box x0,y0,x1,y1" of "--poly x,y;x,y;..."), vereenvoudigd tot een paar tientallen punten;
- check: een of meer veelhoeken over de foto getekend, om te zien of ze kloppen.

Draaien (de foto's staan niet in de mod; geef het pad mee):
    python tools/trace_outline.py grid foto.png --out raster.png [--box 0,0,800,400] [--step 50] [--zoom 2]
    python tools/trace_outline.py outline foto.png [--box ...] [--tol 1.5] [--white 235] [--rotate -3.2]
    python tools/trace_outline.py outline foto.png --colour 176,134,74 --dist 55     (alleen wat die kleur heeft)
    python tools/trace_outline.py check foto.png --out check.png --poly "10,20;30,40;..." [--poly ...]
"""
import argparse
import math
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage


def load(path, rotate=0.0):
    """De foto als RGBA; {rotate} graden gedraaid (tegen de klok in) om hem recht te leggen."""
    im = Image.open(path).convert("RGBA")
    if rotate:
        im = im.rotate(rotate, resample=Image.BICUBIC, expand=True, fillcolor=(255, 255, 255, 0))
    return im


def mask_of(im, white=235, alpha=160, dark=None, colour=None, dist=60):
    """
    Waar het wapen is: niet doorzichtig en niet (bijna) wit. Met {dark}: alleen wat donkerder is dan dat. Met {colour}
    (r, g, b): alleen wat op die kleur lijkt (minder dan {dist} ervandaan), bijvoorbeeld alleen de zandkleurige kolf van
    een geweer waar ook een zwarte kijker en een tweepoot aan hangen.
    """
    a = np.asarray(im).astype(int)
    solid = a[:, :, 3] > alpha
    if colour is not None:
        away = np.sqrt(((a[:, :, :3] - np.array(colour)) ** 2).sum(axis=2))
        return solid & (away < dist)
    if dark is not None:
        return solid & (a[:, :, :3].max(axis=2) < dark)
    not_white = a[:, :, :3].min(axis=2) < white
    return solid & not_white


def largest(mask):
    """Alleen het grootste aaneengesloten stuk."""
    labels, count = ndimage.label(mask)
    if count == 0:
        return mask
    sizes = ndimage.sum(mask, labels, range(1, count + 1))
    return labels == (1 + int(np.argmax(sizes)))


def contour(mask):
    """
    De buitenrand van een masker als een rij punten (x, y), met de klok mee op het scherm ("Moore"): we lopen langs de
    rand van het stuk en houden het stuk steeds rechts van ons.
    """
    padded = np.pad(mask, 1)
    ys, xs = np.nonzero(padded)
    if len(xs) == 0:
        return []
    start = (int(xs[np.argmin(ys)]), int(ys.min()))
    # de acht buren, met de klok mee, te beginnen bij links
    nb = [(-1, 0), (-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1)]
    out = [start]
    current = start
    back = 0
    for _ in range(4 * padded.size):
        found = False
        for k in range(8):
            d = (back + k) % 8
            x, y = current[0] + nb[d][0], current[1] + nb[d][1]
            if padded[y, x]:
                current = (x, y)
                back = (d + 5) % 8
                found = True
                break
        if not found or current == start:
            break
        out.append(current)
    return [(x - 1, y - 1) for x, y in out]


def simplify(points, tol):
    """Ramer-Douglas-Peucker: laat punten weg die minder dan {tol} pixels van de lijn tussen hun buren liggen."""
    if len(points) < 3:
        return list(points)
    pts = np.array(points, dtype=float)

    def rdp(i0, i1, keep):
        if i1 <= i0 + 1:
            return
        a, b = pts[i0], pts[i1]
        d = b - a
        length = math.hypot(*d) or 1e-9
        rel = pts[i0 + 1:i1] - a
        dist = np.abs(rel[:, 0] * d[1] - rel[:, 1] * d[0]) / length
        k = int(np.argmax(dist))
        if dist[k] > tol:
            keep.add(i0 + 1 + k)
            rdp(i0, i0 + 1 + k, keep)
            rdp(i0 + 1 + k, i1, keep)
    # een gesloten rand: knip hem in twee op het verste punt van het begin
    far = int(np.argmax(np.hypot(*(pts - pts[0]).T)))
    keep = {0, far, len(pts) - 1}
    sys.setrecursionlimit(100000)
    rdp(0, far, keep)
    rdp(far, len(pts) - 1, keep)
    out = [tuple(int(round(c)) for c in pts[i]) for i in sorted(keep)]
    if len(out) > 1 and out[0] == out[-1]:
        out.pop()
    return out


def region_mask(size, box=None, poly=None):
    """Een masker van een stuk van de foto: een rechthoek of een veelhoek."""
    im = Image.new("L", size, 0)
    d = ImageDraw.Draw(im)
    if poly:
        d.polygon(poly, fill=255)
    elif box:
        d.rectangle(box, fill=255)
    else:
        d.rectangle((0, 0, size[0], size[1]), fill=255)
    return np.asarray(im) > 0


def outline(im, box=None, poly=None, tol=1.5, white=235, fill=True, dark=None, close=0, colour=None, dist=60):
    """De omtrek van het wapen binnen een stuk van de foto, als veelhoek in foto-pixels."""
    mask = mask_of(im, white=white, dark=dark, colour=colour, dist=dist)
    if close:
        mask = ndimage.binary_closing(mask, iterations=close)
    if fill:
        mask = ndimage.binary_fill_holes(mask)
    mask &= region_mask(im.size, box, poly)
    mask = largest(mask)
    return simplify(contour(mask), tol)


def parse_points(text):
    return [tuple(float(c) for c in p.split(",")) for p in text.split(";") if p.strip()]


def grid(im, out, box=None, step=50, zoom=2.0):
    """De foto met een raster en getallen (foto-pixels) langs de rand."""
    if box:
        x0, y0, x1, y1 = box
    else:
        x0, y0, x1, y1 = 0, 0, im.size[0], im.size[1]
    crop = im.crop((x0, y0, x1, y1))
    bg = Image.new("RGBA", crop.size, (255, 255, 255, 255))
    bg.alpha_composite(crop)
    big = bg.resize((int(crop.size[0] * zoom), int(crop.size[1] * zoom)), Image.LANCZOS)
    d = ImageDraw.Draw(big)
    first_x = (x0 // step + (1 if x0 % step else 0)) * step
    for x in range(first_x, x1 + 1, step):
        sx = (x - x0) * zoom
        strong = x % (step * 2) == 0
        d.line([(sx, 0), (sx, big.size[1])], fill=(255, 0, 0, 255) if strong else (255, 120, 120, 255), width=1)
        if strong:
            d.text((sx + 2, 2), str(x), fill=(200, 0, 0, 255))
            d.text((sx + 2, big.size[1] - 12), str(x), fill=(200, 0, 0, 255))
    first_y = (y0 // step + (1 if y0 % step else 0)) * step
    for y in range(first_y, y1 + 1, step):
        sy = (y - y0) * zoom
        strong = y % (step * 2) == 0
        d.line([(0, sy), (big.size[0], sy)], fill=(0, 0, 255, 255) if strong else (120, 120, 255, 255), width=1)
        if strong:
            d.text((2, sy + 2), str(y), fill=(0, 0, 200, 255))
            d.text((big.size[0] - 30, sy + 2), str(y), fill=(0, 0, 200, 255))
    big.convert("RGB").save(out)


def check(im, out, polys, box=None, zoom=2.0):
    """Veelhoeken over de foto, elk in zijn eigen kleur, met een stip op elk punt."""
    if box:
        x0, y0, x1, y1 = box
    else:
        x0, y0, x1, y1 = 0, 0, im.size[0], im.size[1]
    crop = im.crop((x0, y0, x1, y1))
    bg = Image.new("RGBA", crop.size, (255, 255, 255, 255))
    bg.alpha_composite(crop)
    big = bg.resize((int(crop.size[0] * zoom), int(crop.size[1] * zoom)), Image.LANCZOS)
    d = ImageDraw.Draw(big)
    colours = [(255, 0, 0), (0, 160, 0), (0, 80, 255), (255, 140, 0), (200, 0, 200), (0, 180, 180), (120, 120, 0)]
    for i, poly in enumerate(polys):
        c = colours[i % len(colours)]
        pts = [((x - x0) * zoom, (y - y0) * zoom) for x, y in poly]
        d.line(pts + [pts[0]], fill=c, width=2)
        for p in pts:
            d.ellipse((p[0] - 2, p[1] - 2, p[0] + 2, p[1] + 2), fill=c)
    big.convert("RGB").save(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=("grid", "outline", "check"))
    ap.add_argument("photo")
    ap.add_argument("--out", default="")
    ap.add_argument("--box", default="")
    ap.add_argument("--poly", action="append", default=[])
    ap.add_argument("--step", type=int, default=50)
    ap.add_argument("--zoom", type=float, default=2.0)
    ap.add_argument("--tol", type=float, default=1.5)
    ap.add_argument("--white", type=int, default=235)
    ap.add_argument("--dark", type=int, default=None)
    ap.add_argument("--close", type=int, default=0)
    ap.add_argument("--colour", default="", help="r,g,b: alleen wat op deze kleur lijkt")
    ap.add_argument("--dist", type=float, default=60.0, help="hoe ver een pixel van --colour af mag liggen")
    ap.add_argument("--rotate", type=float, default=0.0)
    ap.add_argument("--nofill", action="store_true")
    args = ap.parse_args()
    im = load(args.photo, args.rotate)
    box = tuple(int(float(c)) for c in args.box.split(",")) if args.box else None
    if args.mode == "grid":
        grid(im, args.out, box, args.step, args.zoom)
        print("geschreven:", args.out, im.size)
    elif args.mode == "outline":
        poly = parse_points(args.poly[0]) if args.poly else None
        colour = tuple(int(c) for c in args.colour.split(",")) if args.colour else None
        pts = outline(im, box if not poly else None, poly, args.tol, args.white, not args.nofill, args.dark, args.close,
                      colour, args.dist)
        print(len(pts), "punten:")
        print("[" + ", ".join(f"({x}, {y})" for x, y in pts) + "]")
    else:
        check(im, args.out, [parse_points(p) for p in args.poly], box, args.zoom)
        print("geschreven:", args.out)


if __name__ == "__main__":
    main()
