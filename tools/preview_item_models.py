"""
Een snelle preview van 3D-itemmodellen zoals ze in een vakje van je inventaris staan, zonder het spel te starten:
dezelfde wiskunde als Minecraft (zie model_display.py), platte kleuren per kant (het gemiddelde van het stukje plaatje
van die kant, met Minecrafts schaduw per richting: boven 1,0, zijkanten 0,8 en 0,6, onder 0,5), achterste kanten eerst.
Goed genoeg om te zien of iets in het midden staat, groot genoeg is en herkenbaar is; voor de echte plaatjes blijft
`gradlew runVisualCheck -Ponly=creative` er.

Draaien (vanuit de hoofdmap):
    python tools/preview_item_models.py --out preview.png "gun_*" "laser_pistol_*"
    python tools/preview_item_models.py --out preview.png --rotation 30,135,0 "gun_*"
Zonder --rotation tekent hij elk model met de "gui"-houding uit zijn json (zoals het spel). Met --rotation rekent hij
voor elk model een nieuwe, passende houding uit (model_display.fit), om een kijkhoek te kiezen voordat je die in een
generator zet. Elk vakje is 16 model-pixels, hier 8 keer vergroot.
"""
import argparse
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import model_display as md  # noqa: E402

ASSETS = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "imttech"
ZOOM = 8
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}
# De vier hoeken (als index in model_display.corners: x-bit 4, y-bit 2, z-bit 1) en de normaal van elke kant.
FACES = {
    "down": ((0, 1, 5, 4), (0, -1, 0)), "up": ((2, 6, 7, 3), (0, 1, 0)),
    "north": ((0, 4, 6, 2), (0, 0, -1)), "south": ((1, 3, 7, 5), (0, 0, 1)),
    "west": ((0, 2, 3, 1), (-1, 0, 0)), "east": ((4, 5, 7, 6), (1, 0, 0)),
}

_textures = {}


def texture(name):
    """Het plaatje van een materiaal-texture ("imttech:item/firearms")."""
    if name not in _textures:
        ns, path = name.split(":")
        _textures[name] = Image.open(ASSETS.parent / ns / "textures" / f"{path}.png").convert("RGBA")
    return _textures[name]


def face_colour(model, face):
    """De gemiddelde kleur van het stukje plaatje van een kant."""
    tex = texture(model["textures"][face["texture"].lstrip("#")])
    k = tex.width / 16
    u0, v0, u1, v1 = face["uv"]
    box = (int(min(u0, u1) * k), int(min(v0, v1) * k), max(int(max(u0, u1) * k), int(min(u0, u1) * k) + 1),
           max(int(max(v0, v1) * k), int(min(v0, v1) * k) + 1))
    region = tex.crop(box)
    pixels = [p for p in region.get_flattened_data() if p[3] > 0]
    if not pixels:
        return (255, 0, 255)
    return tuple(sum(p[i] for p in pixels) // len(pixels) for i in range(3))


def render(model, gui, zoom=ZOOM):
    """Eén vakje van 16 x 16 model-pixels, {zoom} keer vergroot."""
    size = 16 * zoom
    im = Image.new("RGBA", (size, size), (139, 139, 139, 255))
    draw = ImageDraw.Draw(im)
    rot = md.display_matrix(gui["rotation"])
    scale = gui["scale"][0]
    shift = gui["translation"]
    polys = []
    for element in model["elements"]:
        pts = md.corners(element)
        # Normaal na de eigen draaiing van het doosje.
        el_rot = element.get("rotation")
        for side, (idx, normal) in FACES.items():
            face = element["faces"].get(side)
            if not face:
                continue
            n = normal
            if el_rot:
                n = md._apply(md._rotation(el_rot["axis"], el_rot["angle"]), n)
            n = md._apply(rot, n)
            if n[2] <= 0:
                continue  # achterkant: Minecraft tekent hem niet
            quad = []
            depth = 0
            for i in idx:
                p = md._apply(rot, tuple((pts[i][k] - 8) * scale for k in range(3)))
                x, y, z = p[0] + shift[0], p[1] + shift[1], p[2] + shift[2]
                quad.append(((8 + x) * zoom, (8 - y) * zoom))
                depth += z
            colour = face_colour(model, face)
            factor = 1.0 if element.get("shade") is False else SHADE[side]
            colour = tuple(min(255, int(c * factor)) for c in colour)
            polys.append((depth / 4, quad, colour))
    for _, quad, colour in sorted(polys, key=lambda p: p[0]):
        draw.polygon(quad, fill=colour)
    draw.rectangle([0, 0, size - 1, size - 1], outline=(55, 55, 55, 255))
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--rotation", help="bijvoorbeeld 30,135,0: een nieuwe houding uitrekenen in plaats van die uit de json")
    ap.add_argument("--size", type=float, default=14.0)
    ap.add_argument("--max-scale", type=float, default=4.5)
    ap.add_argument("--columns", type=int, default=8)
    ap.add_argument("names", nargs="+", help="namen of patronen van models/item/*.json")
    args = ap.parse_args()
    folder = ASSETS / "models" / "item"
    names = []
    for pattern in args.names:
        found = sorted(p.stem for p in folder.glob(pattern + ".json"))
        if not found:
            print("niets gevonden voor", pattern)
        names += [n for n in found if n not in names]
    rotation = tuple(float(v) for v in args.rotation.split(",")) if args.rotation else None
    tiles = []
    for name in names:
        model = json.loads((folder / f"{name}.json").read_text(encoding="utf-8"))
        if "elements" not in model:
            continue
        gui = md.gui(model["elements"], rotation, size=args.size, max_scale=args.max_scale) if rotation \
            else model["display"]["gui"]
        tiles.append((name, render(model, gui)))
    cell = 16 * ZOOM
    label = 12
    cols = min(args.columns, max(1, len(tiles)))
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * (cell + 4), rows * (cell + label + 4)), (198, 198, 198, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (name, tile) in enumerate(tiles):
        x, y = (i % cols) * (cell + 4) + 2, (i // cols) * (cell + label + 4) + 2
        sheet.paste(tile, (x, y))
        draw.text((x + 2, y + cell), name[:26], fill=(20, 20, 20, 255))
    sheet.save(args.out)
    print("geschreven:", args.out, f"({len(tiles)} modellen)")


if __name__ == "__main__":
    main()
