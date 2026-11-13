"""
De attachments van de grote update (hoofdstuk 103, stap 5 en 6) als 3D-model, op dezelfde schaal als de wapens (1 pixel
van het model = 12,8 mm): de Red Dot Sight en de Sniper Scope, en (stap 6) de drie dempers, de twee grips, de tweepoot
en de verlengbuis van de Remington 870.

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


# ---------------------------------------------------------------- De dempers (stap 6)

# De ronde dempers: lengte, straal en boring (pixels). Het nulpunt is het midden van het achterste vlak, op de as van de
# loop; de demper wijst naar -z. Het wapen zegt hoe ver hij over de monding schuift (client/guns/FirearmRig: muzzleSeat):
# de demper van een geweer is breed genoeg om over de mondingsrem van de AWP te passen.
CANS = {
    "suppressor_9mm": dict(length=11.5, radius=1.2, bore=0.42),      # 147 mm lang, 31 mm dik
    "suppressor_rifle": dict(length=15.5, radius=1.5, bore=0.42),    # 198 mm lang, 38 mm dik
}
# De demper van een hagelgeweer is een platte doos (zoals de SilencerCo Salvo): de boring zit bovenin, zodat de doos onder
# de vizierlijn blijft en je de kraal nog ziet.
BOX_CAN = dict(length=14.0, half_width=1.45, top=1.0, bottom=-2.7, bore=0.78)


def suppressor(name):
    """Een ronde demper: de kraag met kartels achteraan, de buis met haar groeven, en de kap met de boring vooraan."""
    c = CANS[name]
    length, radius, bore = c["length"], c["radius"], c["bore"]
    m = gm.Mesh()
    base, axis = (0.0, 0.0, 0.0), (0, 0, -1)
    profile = [(0.0, 0.0), (0.0, radius - 0.15), (0.15, radius), (1.8, radius), (1.9, radius - 0.1),
               (length - 0.5, radius - 0.1), (length - 0.25, radius - 0.18), (length, radius - 0.42), (length, bore),
               (length - 0.7, bore), (length - 0.7, 0.0)]
    gm.lathe(m, base, axis, profile, ROUND, "can", density=D, data={"base": base, "axis": axis, **c})
    return m


def shotgun_suppressor():
    """De demper van een hagelgeweer: een platte doos met afgeronde hoeken, de boring bovenin (geschilderd)."""
    c = BOX_CAN
    m = gm.Mesh()
    section = lg.rounded_rect(-c["half_width"], c["bottom"], c["half_width"], c["top"], 0.55, steps=3)
    gm.prism(m, section, "z", -c["length"], 0.0, "can_box", bevel=0.22, density=D, smooth=50, data=dict(c))
    # De klem om de monding, achteraan.
    lg.ring(m, (0.0, 0.0, 0.25), (0, 0, -1), 0.9, 1.08, 0.8, "can_clamp", segments=ROUND, density=D)
    return m


# ---------------------------------------------------------------- De grips (stap 6)

# Het nulpunt van een grip is het midden van de bovenkant van zijn klem: daar raakt hij de onderkant van de
# handbeschermer (FirearmRig: underMount). Waar je linkerhand hem vasthoudt: zie client/guns/AddOns.
GRIP_LENGTH = 6.1


def vertical_grip():
    """De Vertical Grip: een klem met een rechte greep eronder (78 mm), iets dikker onderaan, met een dop."""
    m = gm.Mesh()
    gm.box(m, (-0.8, -0.6, -1.35), (0.8, 0.0, 1.35), "grip_clamp", bevel=0.1, density=D)
    for z in (-0.9, 0.9):
        gm.cylinder(m, (-0.86, -0.3, z), (0.86, -0.3, z), 0.16, 8, "grip_screw", density=D)
    base, axis = (0.0, -0.5, 0.0), (0, -1, 0)
    profile = [(0.0, 0.0), (0.0, 0.78), (0.35, 0.92), (4.6, 0.8), (5.0, 0.98), (5.45, 0.98), (5.6, 0.82), (5.6, 0.0)]
    gm.lathe(m, base, axis, profile, 14, "grip", density=D, data={"base": base, "axis": axis})
    return m


def angled_grip():
    """De Angled Grip: een wig onder de handbeschermer; je hand ligt tegen het lange schuine vlak achteraan."""
    m = gm.Mesh()
    outline = [(-3.5, 0.0), (3.1, 0.0), (3.1, -0.55), (2.6, -0.8), (-0.3, -2.85), (-0.95, -3.05), (-1.6, -2.8),
               (-2.3, -1.55), (-3.5, -0.6)]
    gm.prism(m, outline, "x", -0.8, 0.8, "grip_wedge", bevel=0.16, density=D, smooth=50, data={"outline": outline},
             max_strip=12.0)
    return m


# ---------------------------------------------------------------- De tweepoot (stap 6)

# De voet klemt om de voorkant van de handbeschermer (het nulpunt: het midden van zijn bovenkant). De twee poten
# scharnieren aan het juk eronder; de mod tekent ze zelf, naar voren geklapt of schuin omlaag (client/guns/AddOns).
BIPOD_HINGE = (0.95, -0.75)      # x (links en rechts) en y van de scharnieren
BIPOD_LEG = 15.0                 # 19 cm


def bipod():
    """De voet van de tweepoot: de klem, het juk en de twee scharnierpennen."""
    m = gm.Mesh()
    gm.box(m, (-1.15, -0.5, -1.4), (1.15, 0.25, 1.4), "bipod", bevel=0.1, density=D)
    gm.box(m, (-1.3, -1.1, -0.5), (1.3, -0.4, 0.5), "bipod", bevel=0.08, density=D)
    for side in (-1, 1):
        gm.cylinder(m, (side * BIPOD_HINGE[0], BIPOD_HINGE[1], -0.62), (side * BIPOD_HINGE[0], BIPOD_HINGE[1], 0.62), 0.3,
                    8, "bipod_pin", density=D)
    return m


def bipod_leg():
    """Eén poot, recht omlaag vanaf zijn scharnier (het nulpunt): de buis, de uitschuifbare binnenpoot en het voetje."""
    m = gm.Mesh()
    gm.cylinder(m, (0, 0.2, 0), (0, -9.2, 0), 0.27, 8, "bipod_leg", density=D)
    gm.cylinder(m, (0, -8.6, 0), (0, -(BIPOD_LEG - 0.5), 0), 0.19, 8, "bipod_inner", density=D)
    gm.box(m, (-0.42, -BIPOD_LEG, -0.6), (0.42, -(BIPOD_LEG - 0.55), 0.6), "bipod_foot", bevel=0.14, density=D)
    return m


def bipod_item(leg):
    """De tweepoot zoals je hem als item ziet: de voet met de twee poten uitgeklapt (18 graden opzij)."""
    m = bipod()
    for side in (-1, 1):
        m.add(leg.moved(rot=gm.rot_z(side * 18), move=(side * BIPOD_HINGE[0], BIPOD_HINGE[1], 0.0)))
    return m


# ---------------------------------------------------------------- De verlengbuis (stap 6)

# Voor de Remington 870: het nulpunt is het midden van de voorkant van zijn buis (FirearmRig: tubeMount). De maten van
# de buis en de loop komen uit tools/r870_model.py.
TUBE_R = 0.9218585
TUBE_EXTRA = 11.0                # twee patronen van 70 mm
BARREL_UP = 2.4304               # zo ver ligt de as van de loop boven die van de buis
BARREL_R = 0.82


def tube_extension():
    """De Tube Extension: een buis die over de dop van de oude buis schuift, een dop vooraan, en een klem aan de loop."""
    m = gm.Mesh()
    base, axis = (0.0, 0.0, 0.0), (0, 0, -1)
    r = TUBE_R + 0.05
    length = TUBE_EXTRA
    # Achteraan een open kom (daar schuift de oude buis in): de bodem, de binnenwand, en dan de rand en de buitenkant.
    profile = [(2.0, 0.0), (2.0, TUBE_R - 0.02), (-0.9, TUBE_R - 0.02), (-0.9, r + 0.1), (0.5, r + 0.1), (0.6, r),
               (length - 1.0, r), (length - 1.0, r + 0.09), (length - 0.2, r + 0.09), (length, r - 0.12), (length, 0.0)]
    gm.lathe(m, base, axis, profile, 16, "tube", density=D, data={"base": base, "axis": axis, "length": length, "radius": r})
    # De klem: een ring om de buis, een ring om de loop, en het brugje ertussen.
    z = -(length - 3.2)
    lg.ring(m, (0.0, 0.0, z + 0.45), axis, 0.9, r + 0.26, r - 0.02, "tube_clamp", density=D)
    lg.ring(m, (0.0, BARREL_UP, z + 0.45), axis, 0.9, BARREL_R + 0.26, BARREL_R - 0.02, "tube_clamp", density=D)
    gm.box(m, (-0.32, r + 0.1, z - 0.4), (0.32, BARREL_UP - BARREL_R - 0.1, z + 0.4), "tube_clamp", bevel=0.05, density=D)
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
    if kind in ("can", "can_box", "can_clamp"):
        # Een demper: dof zwart, met ondiepe groeven rondom en een donkere boring vooraan.
        col = lg.add(lg.add(BLACK, -4), grain + 28 * lg.edge_amount(isl, n))
        if kind == "can":
            h, r = -z, math.hypot(x, y)
            length, radius, bore = isl.data["length"], isl.data["radius"], isl.data["bore"]
            if nz < -0.5 and r < bore + 0.03 or (nz > 0.5 and h > 1.0):
                return (12, 12, 14, 255)                       # de boring
            if h < 1.8 and abs(nz) < 0.5 and int(math.atan2(y, x) * 18 / math.pi) % 2 == 0:
                col = lg.add(col, -12)                         # de kartels van de kraag
            elif 2.6 < h < length - 1.2 and abs(nz) < 0.5 and (h - 2.6) % 2.2 < 0.16:
                col = lg.add(col, -14)                         # de groeven
        elif kind == "can_box":
            if nz < -0.5 and math.hypot(x, y) < isl.data["bore"]:
                return (12, 12, 14, 255)                       # de boring, bovenin
            if abs(nz) < 0.5 and (-z - 1.2) % 2.6 < 0.18 and 1.0 < -z < isl.data["length"] - 0.8:
                col = lg.add(col, -14)                         # de naden tussen de modules
        else:
            col = lg.add(col, 10)
        return (*col, 255)
    if kind in ("grip", "grip_clamp", "grip_screw", "grip_wedge"):
        # Polymeer met een korreltje; de greep heeft ribbels rondom, de wig groeven dwars op het schuine vlak.
        col = lg.add(lg.add(BLACK, -6), grain + 26 * lg.edge_amount(isl, n))
        if kind == "grip":
            h = -0.5 - y
            if 0.6 < h < 4.5 and abs(ny) < 0.5 and (h * 1.7) % 1.0 < 0.3:
                col = lg.add(col, -11)
        elif kind == "grip_wedge" and abs(nx) < 0.5 and ny < -0.2 and nz > 0.2 and (z * 2.2) % 1.0 < 0.3:
            col = lg.add(col, -11)
        elif kind == "grip_screw":
            col = lg.add(col, 30)
        return (*col, 255)
    if kind in ("bipod", "bipod_pin", "bipod_leg", "bipod_inner", "bipod_foot"):
        col = lg.add(BLACK, grain + 28 * lg.edge_amount(isl, n))
        if kind == "bipod_inner":
            col = lg.add((120, 124, 132), grain)               # de blanke binnenpoot
        elif kind == "bipod_foot":
            col = lg.add((30, 30, 32), grain * 0.6)            # rubber
        elif kind == "bipod_pin":
            col = lg.add(col, 26)
        return (*col, 255)
    if kind in ("tube", "tube_clamp"):
        # Geblauwd staal, zoals de buis van de 870 zelf.
        col = lg.lit(lg.GUN_METAL["steel"], isl, p, n, grain=5, edge=28)
        if kind == "tube":
            h = -z
            if math.hypot(x, y) < TUBE_R and h < 2.05 and (nz > 0.5 or nx * x + ny * y < 0):
                return (14, 14, 16, 255)                       # binnenin de open kom achteraan
            if h > isl.data["length"] - 1.0 and abs(nz) < 0.5 and int(math.atan2(y, x) * 16 / math.pi) % 2 == 0:
                col = lg.add(col, -14)                         # de kartels van de dop
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
def _bipod():
    leg = bipod_leg()
    return {"bipod": bipod(), "bipod_leg": leg, "bipod_item": bipod_item(leg)}


ATTACHMENTS = {
    "red_dot_sight": lambda: {"red_dot_sight": red_dot(), "sight_riser": riser()},
    "sniper_scope": lambda: {"sniper_scope": sniper_scope(), "sniper_scope_lens": scope_lens(),
                             "sniper_scope_reticle": scope_reticle()},
    "suppressor_9mm": lambda: {"suppressor_9mm": suppressor("suppressor_9mm")},
    "suppressor_rifle": lambda: {"suppressor_rifle": suppressor("suppressor_rifle")},
    "suppressor_shotgun": lambda: {"suppressor_shotgun": shotgun_suppressor()},
    "vertical_grip": lambda: {"vertical_grip": vertical_grip()},
    "angled_grip": lambda: {"angled_grip": angled_grip()},
    "bipod": _bipod,
    "tube_extension": lambda: {"tube_extension": tube_extension()},
}
# Het model van het item in je inventaris: het attachment met alles erop en eraan wat je dan ziet.
ITEM_PARTS = {"red_dot_sight": ("red_dot_sight",), "sniper_scope": ("sniper_scope", "sniper_scope_lens"),
              "bipod": ("bipod_item",)}
