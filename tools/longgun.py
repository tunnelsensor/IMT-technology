"""
Gedeelde bouwstenen voor de vuurwapens van hoofdstuk 103 (de MP5, de AK-47, de AWP en de Remington 870), "zoals de
Glock": elk wapen is een rijtje OBJ-modellen uit tools/gunmesh.py, met de omtrek nagemeten van een foto van het echte
wapen (tools/trace_outline.py) en een texture die het script zelf schildert.

Wat hier staat is voor alle wapens hetzelfde:
- Photo: van de pixels van een foto naar de pixels van het model. Alle wapens staan op dezelfde schaal als de Glock: één
  pixel van het model is 12,8 mm. Zo is een patroon, een red dot of een suppressor op elk wapen even groot.
- doorsneden (een afgeronde rechthoek, een ovaal, een buis met een gat erin voor de uitwerpopening);
- kleine onderdelen (een pen, een ring, een loop met een boring, een veer);
- de verf: staal dat je in twee materialen kunt kiezen (staal en titanium), zwart polymeer, hout met nerven, rubber;
- wegschrijven: de plaatjes per materiaal, elk onderdeel als OBJ, en het hele wapen in één OBJ voor je inventaris.

Assen zoals altijd in deze mod: de loop wijst naar -z, de kolf naar +z, rechts is +x (de uitwerpopening), midden x = 8.
"""
import math

import numpy as np

import gunmesh as gm
from gunmesh import vec

PX_MM = 12.8  # één pixel van het model = 12,8 mm van het echte wapen (de schaal van de Glock, hoofdstuk 74)


# ---------------------------------------------------------------- Van de foto naar het model

class Photo:
    """
    Rekent punten van een foto (x naar rechts, y naar beneden) om naar het model. {mm_per_px}: hoeveel millimeter één
    pixel van de foto is (de echte lengte van het wapen gedeeld door zijn lengte op de foto). Het punt ({px_ref},
    {py_ref}) van de foto komt op (z = {z_ref}, y = {y_ref}) in het model. {muzzle_right}: wijst de loop op de foto naar
    rechts (de rechterkant van het wapen) of naar links?
    """

    def __init__(self, mm_per_px, px_ref, z_ref, py_ref, y_ref, muzzle_right=True):
        self.s = mm_per_px / PX_MM
        self.px_ref, self.z_ref, self.py_ref, self.y_ref = px_ref, z_ref, py_ref, y_ref
        self.sign = 1.0 if muzzle_right else -1.0

    def Z(self, px):
        return round(self.z_ref + (self.px_ref - px) * self.s * self.sign, 4)

    def Y(self, py):
        return round(self.y_ref - (py - self.py_ref) * self.s, 4)

    def zy(self, points):
        return [(self.Z(x), self.Y(y)) for x, y in points]

    def size(self, pixels):
        """Een lengte op de foto in pixels van het model."""
        return pixels * self.s


def mm(value):
    """Millimeters van het echte wapen in pixels van het model."""
    return value / PX_MM


# ---------------------------------------------------------------- Doorsneden (voor prism langs de loop)

def rounded_rect(x0, y0, x1, y1, r, steps=2, r_top=None, r_bottom=None):
    """Een rechthoek met afgeronde hoeken (tegen de klok in), als doorsnede. Onder {r_bottom}, boven {r_top}."""
    rt = r if r_top is None else r_top
    rb = r if r_bottom is None else r_bottom
    out = []

    def corner(cx, cy, radius, a0):
        if radius <= 0:
            out.append((cx, cy))
            return
        for i in range(steps + 1):
            a = math.radians(a0 + 90.0 * i / steps)
            out.append((cx + radius * math.cos(a), cy + radius * math.sin(a)))
    corner(x1 - rb, y0 + rb, rb, -90)   # rechtsonder
    corner(x1 - rt, y1 - rt, rt, 0)     # rechtsboven
    corner(x0 + rt, y1 - rt, rt, 90)    # linksboven
    corner(x0 + rb, y0 + rb, rb, 180)   # linksonder
    return out


def oval(cx, cy, rx, ry, n=16, start=0.0):
    """Een ovaal (of cirkel) als veelhoek met {n} hoeken, tegen de klok in."""
    return [(cx + rx * math.cos(start + 2 * math.pi * i / n), cy + ry * math.sin(start + 2 * math.pi * i / n))
            for i in range(n)]


def c_shell(outer, thickness, edge, a, b):
    """
    Een holle doorsnede met een gat in de wand (de uitwerpopening): {outer} is de buitenkant (tegen de klok in), de wand
    is {thickness} dik, en op rand {edge} (van outer[edge] naar het volgende punt) zit een opening van fractie {a} tot
    {b}. Geeft één veelhoek terug: buitenom van de bovenlip naar de onderlip, en binnendoor terug.
    """
    outer = gm.ccw(outer)
    n = len(outer)
    inner = gm.inset(outer, thickness)
    p0, p1 = outer[edge], outer[(edge + 1) % n]
    nx, ny = gm.edge_normals(outer)[edge]

    def on_edge(t):
        return (p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t)
    lip_a, lip_b = on_edge(a), on_edge(b)
    in_a = (lip_a[0] - nx * thickness, lip_a[1] - ny * thickness)
    in_b = (lip_b[0] - nx * thickness, lip_b[1] - ny * thickness)
    poly = [lip_b]
    for k in range(1, n + 1):
        poly.append(outer[(edge + k) % n])
    poly += [lip_a, in_a]
    for k in range(n):
        poly.append(inner[(edge - k) % n])
    poly.append(in_b)
    return poly


# ---------------------------------------------------------------- Kleine onderdelen

def pin(mesh, z, y, x0, x1, r, kind="pin", segments=10, density=16):
    """Een pen dwars door het wapen (langs x), met zijn twee koppen net buiten de wand."""
    gm.cylinder(mesh, (x0 - 0.015, y, z), (x1 + 0.015, y, z), r, segments, kind, density=density,
                data={"base": (x0, y, z), "axis": (1, 0, 0)})


def ring(mesh, centre, axis, length, r_out, r_in, kind, segments=16, density=16, data=None):
    """Een korte buis (een ring) rond {axis} vanaf {centre}: buitenwand, binnenwand en de twee randen."""
    profile = [(0.0, r_in), (0.0, r_out), (length, r_out), (length, r_in), (0.0, r_in)]
    info = {"base": tuple(centre), "axis": tuple(axis), "bore": r_in}
    info.update(data or {})
    gm.lathe(mesh, centre, axis, profile, segments, kind, density=density, data=info, smooth=20.0)


def barrel(mesh, base, axis, profile, bore, kind="barrel", segments=14, density=8, recess=0.35):
    """
    Een loop: {profile} = [(afstand langs de as, straal), ...] van de kamer naar de monding; aan de monding komt een
    boring van straal {bore}, {recess} diep. De schilder maakt de boring donker (data "bore").
    """
    end = profile[-1][0]
    full = [(0.0, 0.0)] + list(profile) + [(end, bore), (end - recess, bore), (end - recess, 0.0)]
    gm.lathe(mesh, base, axis, full, segments, kind, density=density,
             data={"base": tuple(base), "axis": tuple(axis), "bore": bore, "length": end})


def spring(mesh, p0, p1, r, kind="coil", rod=0.0, rod_kind="rod", segments=12, density=16):
    """Een veer (een buis met geschilderde windingen) met eventueel een geleidestang erdoor."""
    p0v, p1v = np.asarray(p0, dtype=float), np.asarray(p1, dtype=float)
    gm.cylinder(mesh, p0, p1, r, segments, kind, caps=False, density=density,
                data={"base": tuple(p0), "axis": tuple(gm.unit(p1v - p0v))})
    if rod > 0:
        gm.cylinder(mesh, p0, p1, rod, 8, rod_kind, density=density)


# ---------------------------------------------------------------- Verf

def noise(p, scale=16.0, seed=0):
    """Een vast "toeval" per plek op het model (0..1), zodat twee vlakken die elkaar raken hetzelfde ruisje hebben."""
    ix, iy, iz = (int(math.floor(c * scale)) for c in p)
    h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ (seed * 2654435761)
    h = (h * 2246822519) & 0xFFFFFFFF
    h ^= h >> 13
    h = (h * 3266489917) & 0xFFFFFFFF
    return (h & 0xFFFF) / 65535.0


def add(c, v):
    return tuple(max(0, min(255, int(round(x + v)))) for x in c[:3])


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def edge_amount(isl, n):
    """Hoeveel een texel op een afgeronde rand zit (0 = plat vlak, 1 = midden van de rand), langs de as van zijn prism."""
    axis = isl.data.get("axis")
    if not isinstance(axis, str):
        return 0.0
    a = abs(float(n[{"x": 0, "y": 1, "z": 2}[axis]]))
    return max(0.0, 4 * a * (1 - a))


# Geblauwd staal (bijna zwart) of titanium (lichtgrijs-blauw): de buitenkant van het wapen.
GUN_METAL = {"steel": (60, 62, 68), "titanium": (134, 140, 149)}
# Blank staal of titanium: pennen, veren, de grendel.
BRIGHT = {"steel": (152, 156, 163), "titanium": (150, 162, 178)}
POLYMER = (42, 42, 45)
RUBBER = (30, 30, 32)
DARK = (19, 19, 21)
WOOD = (146, 84, 44)
WOOD_DARK = (96, 50, 24)


def lit(col, isl, p, n, grain=5.0, edge=28.0, seed=0):
    """De basiskleur met een korreltje, lichtere afgeronde randen, een iets lichter bovenvlak en een donkerder ondervlak."""
    g = noise(p, 16.0, seed) - 0.5
    ny = float(n[1])
    return add(col, g * grain + edge * edge_amount(isl, n) + (5 if ny > 0.8 else 0) - (6 if ny < -0.8 else 0))


def wood(isl, p, n, base=WOOD, dark=WOOD_DARK, along="z"):
    """Hout met nerven die langs het wapen lopen: lange donkere strepen, met een korreltje en lichtere randen."""
    x, y, z = (float(c) for c in p)
    u = z if along == "z" else y
    # Nerven: het ruisje verandert snel dwars op de nerf en heel traag erlangs.
    across = y if along == "z" else z
    band = noise((x * 0.6, across * 3.1 + 0.35 * math.sin(u * 0.55), u * 0.09), 4.0, 11)
    fine = noise((x * 2.0, across * 9.0, u * 0.4), 6.0, 12)
    t = 0.55 * band + 0.25 * fine
    col = mix(base, dark, max(0.0, min(1.0, (t - 0.25) * 1.6)))
    return add(col, (noise(p, 16.0, 13) - 0.5) * 7 + 22 * edge_amount(isl, n) + (5 if n[1] > 0.8 else 0))


def bore_dark(col, isl, p, n):
    """De boring van een loop (zie barrel): donker binnenin, een lichte rand om de monding."""
    data = isl.data
    if "bore" not in data or "length" not in data:
        return col
    base = np.array(data["base"], dtype=float)
    axis = gm.unit(np.array(data["axis"], dtype=float))
    rel = np.array([float(c) for c in p]) - base
    h = float(np.dot(rel, axis))
    r = float(np.linalg.norm(rel - axis * h))
    along = float(np.dot(np.array([float(c) for c in n]), axis))
    if r < data["bore"] + 0.02 and h > data["length"] - 0.6:
        return add(DARK, 5 if r > data["bore"] - 0.03 else 0)
    if along > 0.6 and h > data["length"] - 0.05:
        return add(col, 30)
    return col


def coil(col, isl, p, pitch=0.19):
    """De windingen van een veer: schuine lichte en donkere banden rond de as (zie spring)."""
    base = np.array(isl.data["base"], dtype=float)
    axis = gm.unit(np.array(isl.data["axis"], dtype=float))
    rel = np.array([float(c) for c in p]) - base
    h = float(np.dot(rel, axis))
    side = rel - axis * h
    u, v = gm._plane_basis(axis)
    ang = math.atan2(float(np.dot(side, v)), float(np.dot(side, u))) / (2 * math.pi)
    phase = (h / pitch + ang) % 1.0
    return add(col, 10 if phase < 0.25 else (-58 if phase > 0.7 else -10))


def round_paint(isl, p, n, kind, grain):
    """Een patroon of huls (messing) en zijn kogel (koper), met het slaghoedje in de bodem (zoals bij de Glock)."""
    base = np.array(isl.data["base"], dtype=float)
    axis = gm.unit(np.array(isl.data["axis"], dtype=float))
    rel = np.array([float(c) for c in p]) - base
    h = float(np.dot(rel, axis))
    r = float(np.linalg.norm(rel - axis * h))
    along = float(np.dot(np.array([float(c) for c in n]), axis))
    rim = float(isl.data.get("rim", 0.4))
    if kind == "brass":
        col = add((204, 162, 82), -18 + 30 * min(1.0, h / max(0.5, float(isl.data.get("case", 1.5)))) + grain * 6)
        if along < -0.9:
            col = (196, 158, 92) if r < rim * 0.33 else ((150, 116, 52) if r < rim * 0.4 else (188, 150, 76))
            if rim * 0.6 < r < rim * 0.68:
                col = add(col, -18)
        if isl.data.get("open") and along > 0.5 and r < float(isl.data.get("mouth", 0.35)):
            col = (46, 36, 22)
        return col
    return add((190, 114, 64), 22 * max(0.0, along) + grain * 6)


# ---------------------------------------------------------------- Wegschrijven

def auto_pack(islands, sizes=((256, 256), (512, 256), (512, 512), (1024, 512), (1024, 1024), (2048, 1024), (2048, 2048))):
    """Zet de eilanden op het kleinste plaatje waar ze op passen; geeft (breedte, hoogte)."""
    for size in sizes:
        try:
            gm.pack(islands, *size)
            return size
        except ValueError:
            continue
    raise ValueError("de texture past niet op 2048 x 2048")


def mtl(gun_id, skins):
    """De materialen van één wapen: "#gun" enz. zijn namen uit de json ("textures"). Ka 1 1 1 = gloeit."""
    lines = [f"# De materialen van {gun_id} (hoofdstuk 103). Ka 1 1 1 = gloeit (lichtgevende vizierpunten).",
             "newmtl gun", "map_Kd #gun", "newmtl glow", "map_Kd #gun", "Ka 1 1 1"]
    for skin in skins:
        lines += [f"newmtl {skin}", f"map_Kd #{skin}", f"newmtl {skin}_glow", f"map_Kd #{skin}", "Ka 1 1 1"]
    lines += ["newmtl mag", "map_Kd #mag"]
    return "\n".join(lines) + "\n"


def write_mixed(path, mtllib, groups, note):
    """
    Eén OBJ met meer dan één texture-indeling: {groups} = [(naam, mesh, {"gun": mtl, "glow": mtl}, (breedte, hoogte))].
    Voor het hele wapen in je inventaris: de onderdelen op het plaatje van het wapen, het magazijn op zijn eigen plaatje.
    """
    lines = [f"# {note}", f"mtllib {mtllib}"]
    vs, vts, vns = {}, {}, {}
    out_v, out_vt, out_vn = [], [], []

    def key_of(store, out, value):
        k = tuple(round(float(c), 5) for c in value)
        if k not in store:
            store[k] = len(store) + 1
            out.append(k)
        return store[k]
    body = []
    total = 0
    for name, mesh, materials, (width, height) in groups:
        body.append(f"o {name}")
        by_material = {}
        for material, verts in mesh.faces:
            by_material.setdefault(material, []).append(verts)
        for material, faces in by_material.items():
            body.append(f"usemtl {materials[material]}")
            for verts in faces:
                refs = []
                for p, nrm, isl, tx, ty in verts:
                    refs.append(f"{key_of(vs, out_v, p / 16.0)}"
                                f"/{key_of(vts, out_vt, ((isl.x + tx) / width, (isl.y + ty) / height))}"
                                f"/{key_of(vns, out_vn, gm.unit(nrm))}")
                body.append("f " + " ".join(refs))
                total += 1
    lines += [f"v {x:.5f} {y:.5f} {z:.5f}" for x, y, z in out_v]
    lines += [f"vt {u:.6f} {v:.6f}" for u, v in out_vt]
    lines += [f"vn {x:.4f} {y:.4f} {z:.4f}" for x, y, z in out_vn]
    lines += body
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    return total


class Gun:
    """
    Alles van één wapen bij elkaar, klaar om weg te schrijven. {parts}: de zes onderdelen en hun losse bewegende stukken
    ("slide", "slide_handle"...) als meshes; {mags}: de magazijnen ("magazine", "magazine_empty"...); {whole}: welke
    onderdelen samen het hele wapen zijn (voor je inventaris), {whole_mag}: met welk magazijn erin.
    """

    def __init__(self, gun_id, parts, mags, skins, painter, mag_painter, whole, whole_mag="magazine", classic="steel",
                 extra_whole=()):
        self.id = gun_id
        self.parts = parts
        self.mags = mags
        self.skins = skins
        self.painter = painter
        self.mag_painter = mag_painter
        self.whole = whole
        self.whole_mag = whole_mag
        self.classic = classic
        self.extra_whole = extra_whole
        self.gun_islands = [i for m in parts.values() for i in m.islands]
        self.gun_size = auto_pack(self.gun_islands)
        self.mag_islands = [i for m in mags.values() for i in m.islands]
        self.mag_size = auto_pack(self.mag_islands) if self.mag_islands else (16, 16)

    def write(self, assets):
        """De plaatjes en de OBJ-bestanden. Geeft het aantal vlakken per model."""
        note = (f"Gemaakt door tools/generate_gun_models.py (tools/{self.id}_model.py), hoofdstuk 103. "
                "Niet met de hand aanpassen.")
        tex = assets / "textures" / "item" / self.id
        tex.mkdir(parents=True, exist_ok=True)
        for skin in self.skins:
            gm.paint(self.gun_islands, *self.gun_size, self.painter, skin).save(tex / f"{skin}.png")
        if self.mag_islands:
            gm.paint(self.mag_islands, *self.mag_size, self.mag_painter, "mag").save(tex / "magazine.png")
        folder = assets / "models" / "firearm" / self.id
        folder.mkdir(parents=True, exist_ok=True)
        (folder / f"{self.id}.mtl").write_text(mtl(self.id, self.skins), encoding="utf-8", newline="\n")
        counts = {}
        plain = {"gun": "gun", "glow": "glow"}
        for name, mesh in self.parts.items():
            counts[name] = gm.write_obj(folder / f"{name}.obj", [(name, mesh, plain)], f"{self.id}.mtl", *self.gun_size,
                                        note)
        mag_map = {"gun": "mag", "glow": "mag"}
        for name, mesh in self.mags.items():
            counts[name] = gm.write_obj(folder / f"{name}.obj", [(name, mesh, mag_map)], f"{self.id}.mtl", *self.mag_size,
                                        note)
        skin_map = {"gun": self.classic, "glow": f"{self.classic}_glow"}
        groups = [(name, self.parts[name], skin_map, self.gun_size) for name in self.whole]
        if self.whole_mag:
            groups.append((self.whole_mag, self.mags[self.whole_mag], mag_map, self.mag_size))
        counts["whole"] = write_mixed(folder / "whole.obj", f"{self.id}.mtl", groups, note)
        return counts

    def whole_points(self):
        pts = [p for name in self.whole for p in self.parts[name].points()]
        if self.whole_mag:
            pts += self.mags[self.whole_mag].points()
        return pts

    def bounds(self):
        """De doos om elk model (voor client/guns/FirearmRig): {naam: (laag, hoog)}."""
        out = {}
        for name, mesh in list(self.parts.items()) + list(self.mags.items()):
            lo, hi = mesh.bounds()
            out[name] = ([round(float(v), 2) for v in lo], [round(float(v), 2) for v in hi])
        return out
