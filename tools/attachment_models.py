"""
De attachments van de grote update (hoofdstuk 103, stap 5) als 3D-model, op dezelfde schaal als de wapens (1 pixel van het
model = 12,8 mm): de Red Dot Sight en de Sniper Scope.

Elk attachment is getekend rond zijn eigen nulpunt: het midden van de onderkant van zijn voet, daar waar hij op het wapen
staat ("de montage"). Het wapen zegt waar dat punt op hem ligt (client/guns/FirearmRig: sightMount), en de mod schuift
het model daarheen. De loop van het wapen wijst naar -z: de voorkant van een kijker dus ook.

- De Red Dot Sight: een open kijkertje (een raampje in een beugel, met achteraan het kastje van het lampje) zoals je ze op
  pistolen en geweren ziet. Het rode stipje zweeft in het midden van het raampje en geeft zelf licht.
- De Sniper Scope: een lange kijker van 1x tot 10x, met de maten van de Schmidt & Bender die op de foto van de AWP zit
  (417 mm lang, een buis van 34 mm, een objectief van 62 mm): de klok vooraan, de buis met de drie draaiknoppen, het
  oculair achteraan, en twee ringen op een voet. Het glas achteraan en het dradenkruis zijn losse modellen: als je richt,
  tekent de mod het glas niet (je kijkt erdoor) en het dradenkruis wel.
"""
import math

import numpy as np

import gunmesh as gm
import longgun as lg

D = 16

# ---------------------------------------------------------------- De Red Dot Sight

# De maten (pixels): het raampje, en waar het stipje zweeft. Java (client/guns/Optics) gebruikt dezelfde getallen.
DOT_Y = 1.46
DOT_Z = -1.2
RED_DOT_EYE = (0.0, DOT_Y, DOT_Z)


def red_dot():
    """De Red Dot Sight: de voet, het kastje van het lampje achteraan, de beugel met het raampje vooraan, twee knopjes."""
    m = gm.Mesh()
    gm.box(m, (-0.85, 0.0, -1.8), (0.85, 0.3, 1.8), "dot_body", bevel=0.06, density=D)
    gm.box(m, (-0.7, 0.3, 0.6), (0.7, 0.62, 1.7), "dot_body", bevel=0.08, density=D)
    # De beugel: twee staanders en een dak, met eronder de drempel van het raampje.
    z0, z1 = -1.6, -0.85
    for side in (-1, 1):
        gm.box(m, (side * 1.0 - 0.09 if side > 0 else -1.18, 0.3, z0), (1.18 if side > 0 else side * 1.0 + 0.09, 2.3, z1), "dot_body",
               bevel=0.05, density=D)
    gm.box(m, (-1.18, 2.2, z0), (1.18, 2.42, z1), "dot_body", bevel=0.06, density=D)
    gm.box(m, (-1.0, 0.3, z0), (1.0, 0.72, z1), "dot_body", bevel=0.03, density=D)
    # De knopjes voor de helderheid (links) en de stelschroef (boven op het kastje).
    gm.cylinder(m, (-0.7, 0.46, 1.15), (-0.86, 0.46, 1.15), 0.14, 8, "dot_knob", density=D)
    gm.cylinder(m, (0.0, 0.62, 1.2), (0.0, 0.7, 1.2), 0.2, 8, "dot_knob", density=D)
    # Het stipje: een plat rood schijfje in het raampje, dat zelf licht geeft. Het kijkt naar je oog (+z).
    r = 0.075
    ring = [(r * math.cos(2 * math.pi * i / 8), DOT_Y + r * math.sin(2 * math.pi * i / 8)) for i in range(8)]
    gm.prism(m, ring, "z", DOT_Z - 0.01, DOT_Z + 0.01, "dot", material="glow", density=128)
    return m


def riser():
    """
    Een verhoging onder een kijker: een blokje van 1 pixel hoog. De mod rekt het uit tot de hoogte die het wapen nodig
    heeft (de MP5: zo kijk je over zijn eigen vizier heen).
    """
    m = gm.Mesh()
    gm.box(m, (-0.7, 0.0, -2.0), (0.7, 1.0, 2.0), "dot_body", bevel=0.05, density=D)
    return m


# ---------------------------------------------------------------- De Sniper Scope

SCOPE_LENGTH = 417.0 / 12.8      # 32,6 pixels
SCOPE_FRONT = -SCOPE_LENGTH / 2  # de voorkant van het objectief (het nulpunt ligt in het midden)
SCOPE_AXIS = 2.75                # de as van de buis boven de voet
TUBE = 17.0 / 12.8               # de straal van de buis (34 mm)
BELL = 31.0 / 12.8               # het objectief (62 mm)
EYE_R = 1.75                     # het oculair (45 mm)
EYE_RIM = 1.85
EYE_HOLE = 1.55                  # de opening waar je in kijkt
ROUND = 24                       # zoveel vlakjes heeft de buis rondom (Java, client/guns/Optics, vult dezelfde opening)
SCOPE_REAR = SCOPE_FRONT + SCOPE_LENGTH
# Waar je oog op de as ligt als je richt: het midden van het achterste vlak van het oculair.
SCOPE_EYE = (0.0, SCOPE_AXIS, SCOPE_REAR)


def at(d):
    """De z van een punt op {d} pixels achter de voorkant van de kijker."""
    return SCOPE_FRONT + d


def sniper_scope():
    """De kijker zonder zijn achterste glas: de buis, de knoppen, de ringen en de voet."""
    m = gm.Mesh()
    base = (0.0, SCOPE_AXIS, SCOPE_FRONT)
    axis = (0, 0, 1)
    data = {"base": base, "axis": axis}
    # Van voor naar achter: de klok van het objectief, de buis, het oculair met zijn ring, en achteraan de open kom
    # waar je in kijkt (de rand, en een stukje binnenwand).
    profile = [(0.35, BELL - 0.25), (0.0, BELL - 0.25), (0.0, BELL), (0.3, BELL + 0.06), (0.6, BELL), (7.4, BELL - 0.08),
               (10.4, TUBE), (22.4, TUBE), (24.2, EYE_R), (25.0, EYE_R), (25.0, EYE_RIM), (26.6, EYE_RIM), (26.6, EYE_R),
               (31.7, EYE_R), (31.7, EYE_RIM), (SCOPE_LENGTH, EYE_RIM), (SCOPE_LENGTH, EYE_HOLE), (SCOPE_LENGTH - 0.45, EYE_HOLE)]
    gm.lathe(m, base, axis, profile, ROUND, "scope_tube", density=8, data=data)
    # Het glas vooraan, een eindje in de klok.
    gm.cylinder(m, (0, SCOPE_AXIS, at(0.36)), (0, SCOPE_AXIS, at(0.4)), BELL - 0.25, ROUND, "lens_front", density=8)
    # Het blok met de drie knoppen: hoogte (boven), opzij (rechts) en scherpte (links).
    mid = at(16.6)
    gm.box(m, (-TUBE - 0.12, SCOPE_AXIS - TUBE - 0.1, mid - 1.5), (TUBE + 0.12, SCOPE_AXIS + TUBE + 0.12, mid + 1.5), "scope_block",
           bevel=0.3, density=8)
    knob = [(0.0, 0.0), (0.0, 0.95), (0.25, 0.95), (0.25, 1.05), (1.35, 1.05), (1.35, 0.92), (1.6, 0.92), (1.6, 0.0)]
    top = SCOPE_AXIS + TUBE + 0.1
    gm.lathe(m, (0, top, mid), (0, 1, 0), knob, 12, "scope_knob", density=D, data={"base": (0, top, mid), "axis": (0, 1, 0)})
    side = [(0.0, 0.0), (0.0, 0.85), (0.2, 0.85), (0.2, 0.95), (1.1, 0.95), (1.1, 0.8), (1.3, 0.8), (1.3, 0.0)]
    for sx in (-1, 1):
        start = (sx * (TUBE + 0.1), SCOPE_AXIS, mid)
        gm.lathe(m, start, (sx, 0, 0), side, 12, "scope_knob", density=D, data={"base": start, "axis": (sx, 0, 0)})
    # Twee ringen om de buis, elk op een pootje, en de voet die op de rail van het wapen klemt.
    for d in (12.4, 21.0):
        z = at(d)
        lg.ring(m, (0, SCOPE_AXIS, z - 0.55), axis, 1.1, TUBE + 0.32, TUBE - 0.02, "scope_ring", density=D)
        gm.box(m, (-0.75, 0.4, z - 0.55), (0.75, SCOPE_AXIS - TUBE + 0.12, z + 0.55), "scope_ring", bevel=0.06, density=D)
    gm.box(m, (-0.9, 0.0, at(11.2)), (0.9, 0.45, at(22.2)), "scope_ring", bevel=0.06, density=D)
    return m


def scope_lens():
    """Het glas achteraan in het oculair. De mod tekent het alleen als je niet door de kijker richt."""
    m = gm.Mesh()
    z = SCOPE_REAR - 0.4
    gm.cylinder(m, (0, SCOPE_AXIS, z - 0.04), (0, SCOPE_AXIS, z), EYE_HOLE + 0.02, ROUND, "lens_rear", density=8)
    return m


def scope_reticle():
    """
    Het dradenkruis: vier dikke balkjes van de rand naar binnen, dunne draden naar het midden, en een puntje. Het ligt in
    het achterste vlak van het oculair, een haar naar je oog toe; de mod tekent het alleen als je richt.
    """
    m = gm.Mesh()
    z0, z1 = SCOPE_REAR + 0.004, SCOPE_REAR + 0.008
    y = SCOPE_AXIS
    edge, post, gap = EYE_HOLE + 0.02, EYE_HOLE * 0.5, 0.045
    thick, thin = 0.018, 0.006
    for sx, sy in ((1, 0), (-1, 0), (0, -1)):
        # Dik van de rand tot halverwege (niet van boven: daar blijft je zicht vrij), dun tot vlak bij het midden.
        if sx:
            gm.box(m, (min(sx * post, sx * edge), y - thick, z0), (max(sx * post, sx * edge), y + thick, z1), "reticle", density=64)
            gm.box(m, (min(sx * gap, sx * post), y - thin, z0), (max(sx * gap, sx * post), y + thin, z1), "reticle", density=64)
        else:
            gm.box(m, (-thick, y + min(sy * post, sy * edge), z0), (thick, y + max(sy * post, sy * edge), z1), "reticle", density=64)
            gm.box(m, (-thin, y + min(sy * gap, sy * post), z0), (thin, y + max(sy * gap, sy * post), z1), "reticle", density=64)
    gm.box(m, (-thin, y + gap, z0), (thin, y + edge, z1), "reticle", density=64)
    gm.box(m, (-0.012, y - 0.012, z0), (0.012, y + 0.012, z1), "reticle", density=64)
    return m


# ---------------------------------------------------------------- Schilderen

# Zwart geanodiseerd aluminium. Niet te donker: op de donkere Gunsmith Table en als icoontje moet je het nog zien.
BLACK = (50, 52, 58)


def painter(skin, isl, p, n, tx, ty):
    kind = isl.kind
    x, y, z = (float(c) for c in p)
    nx, ny, nz = (float(c) for c in n)
    if kind == "dot":
        return (255, 46, 34, 255)
    if kind == "reticle":
        return (8, 8, 10, 255)
    if kind == "lens_front":
        # Gecoat glas: donker met een paarsblauwe weerschijn, lichter naar de rand.
        r = math.hypot(x, y - SCOPE_AXIS) / BELL
        return (*lg.add(lg.mix((30, 26, 52), (74, 96, 150), min(1.0, r) ** 2), (lg.noise(p, 9, 2) - 0.5) * 10), 255)
    if kind == "lens_rear":
        r = math.hypot(x, y - SCOPE_AXIS) / EYE_HOLE
        return (*lg.add(lg.mix((16, 18, 26), (44, 54, 78), min(1.0, r) ** 3), (lg.noise(p, 9, 3) - 0.5) * 6), 255)
    # Een korreltje, lichtere randen, en een bovenkant die het licht vangt (zoals bij de wapens: lg.lit).
    grain = (lg.noise(p, 20, 5) - 0.5) * 7 + (7 if ny > 0.8 else 0) - (7 if ny < -0.8 else 0)
    if kind in ("dot_body", "scope_tube", "scope_block", "scope_ring"):
        col = lg.add(BLACK if kind != "scope_ring" else lg.add(BLACK, 10), grain + 30 * lg.edge_amount(isl, n))
        if kind == "scope_tube":
            base = np.array(isl.data["base"])
            d = z - float(base[2])
            r = math.hypot(x, y - SCOPE_AXIS)
            angle = math.atan2(y - SCOPE_AXIS, x)
            if 25.0 <= d <= 26.6 and r > EYE_R and int(angle * 16 / math.pi) % 2 == 0:
                col = lg.add(col, -12)                         # de ribbels van de zoomring
            if nz > 0.5 and r < EYE_HOLE + 0.02:
                col = (12, 12, 14)                             # de binnenwand van het oculair
            if nz < -0.5 and r < BELL - 0.2:
                col = (12, 12, 14)
        return (*col, 255)
    if kind in ("dot_knob", "scope_knob"):
        col = lg.add(BLACK, 12 + grain + 30 * lg.edge_amount(isl, n))
        if kind == "scope_knob":
            base = np.array(isl.data["base"])
            axis = np.array(isl.data["axis"], dtype=float)
            rel = np.array([x, y, z]) - base
            h = float(np.dot(rel, axis))
            side = rel - axis * h
            u, v = gm._plane_basis(gm.unit(axis))
            angle = math.atan2(float(np.dot(side, v)), float(np.dot(side, u)))
            if 0.3 < h < 1.2 and int(angle * 14 / math.pi) % 2 == 0:
                col = lg.add(col, -12)                         # de kartels van de knop
            if 0.05 < h < 0.2 and int(angle * 24 / math.pi) % 3 == 0:
                col = (210, 210, 205)                          # de witte streepjes van de schaal
        return (*col, 255)
    return (255, 0, 255, 255)


# ---------------------------------------------------------------- Alles bij elkaar

# naam van het attachment -> zijn modellen: {naam van het model: mesh}. Het eerste is het attachment zelf.
ATTACHMENTS = {
    "red_dot_sight": lambda: {"red_dot_sight": red_dot(), "sight_riser": riser()},
    "sniper_scope": lambda: {"sniper_scope": sniper_scope(), "sniper_scope_lens": scope_lens(),
                             "sniper_scope_reticle": scope_reticle()},
}
# Het model van het item in je inventaris: het attachment met alles erop en eraan wat je dan ziet.
ITEM_PARTS = {"red_dot_sight": ("red_dot_sight",), "sniper_scope": ("sniper_scope", "sniper_scope_lens")}
