"""
De Remington 870 als echt 3D-model (hoofdstuk 103, stap 4, jouw keuze: "Remington 870, pomp").

Dit is de klassieke 870 met een houten kolf en een houten voorhout (de "pomp") en een loop van 20 inch. De omtrek komt
van een foto van een echte 870 Wingmaster (de rechterkant, van Wikimedia Commons); de punten hieronder staan in pixels van
die foto en zijn met het oog van een raster afgelezen (tools/trace_outline.py grid): het wapen ligt daar op een houten
vloer, dus de omtrek er vanzelf afhalen ging niet. Het model staat op de schaal van de Glock (1 pixel = 12,8 mm): de 870
is 102 cm lang, dus bijna 80 pixels.

Een 870 heeft geen magazijn: de patronen zitten in een buis onder de loop (6 stuks), en je duwt ze er een voor een in
langs de onderkant van de behuizing. Na elk schot trek je het voorhout naar achteren en duw je het weer naar voren: de
lege huls springt eruit en de volgende patroon gaat de kamer in.

De zes onderdelen (dezelfde rollen als bij de Glock, met de namen van een pompgeweer):
- frame   = Receiver: de stalen behuizing met de uitwerpopening rechts en de laadopening onderaan, de houten kolf met
            zijn rubberen kolfkap, de buis onder de loop met haar dop, en de trekkerbeugel;
- slide   = Forend: het houten voorhout dat je pompt, met zijn twee stalen stangen en de grendel die eraan vastzit;
- barrel  = Barrel: de loop met de kraal (het bolletje waarover je richt);
- springs = Magazine Spring (de veer in de buis, die de patronen naar achteren duwt); striker = Firing Pin;
  trigger = Trigger Group (de trekker).

Assen zoals altijd: de loop naar -z, de kolf naar +z, rechts is +x, midden x = 8. De trekker staat op z = 8, y = 9.
"""
import math

import numpy as np

import gunmesh as gm
import longgun as lg

# ---------------------------------------------------------------- Van de foto naar het model

# Een 870 met een loop van 20 inch is 1018 mm lang; op de foto is dat 1898 pixels, van de kolfkap tot de monding. De foto
# toont de rechterkant: de loop wijst naar rechts.
P = lg.Photo(1018.0 / 1898.0, 697, 8.0, 145, 9.0, muzzle_right=True)
Z, Y, zy = P.Z, P.Y, P.zy
D = 8

BORE_Y = Y(39)                   # de as van de loop
TUBE_Y = Y(97)                   # de as van de buis onder de loop
TUBE_R = P.size(22)
REC_Y0, REC_Y1 = Y(124), Y(19)   # de behuizing: onderkant en bovenkant
REC_Z0, REC_Z1 = Z(997), Z(642)  # voor en achter
PORT_Z0, PORT_Z1 = Z(972), Z(838)    # de uitwerpopening (rechts)
LOAD_Z0, LOAD_Z1 = Z(965), Z(835)    # de laadopening (onderaan)
MUZZLE = 1913
PUMP = 7.0                       # zo ver gaat het voorhout naar achteren (89 mm)

REC_W = 1.5        # de behuizing is 38 mm breed
STOCK_W = 1.15

# De omtrekken (foto-pixels).
STOCK = [(642, 44), (612, 54), (585, 66), (560, 77), (520, 88), (470, 87), (430, 90), (350, 98), (250, 108), (150, 118),
         (68, 125), (68, 338), (150, 318), (250, 282), (350, 245), (420, 215), (450, 206), (480, 205), (505, 212), (522, 224),
         (535, 212), (550, 188), (572, 160), (600, 141), (640, 128), (655, 123)]
PAD = [(68, 124), (40, 126), (22, 136), (16, 160), (15, 330), (22, 352), (40, 356), (68, 340)]
# De behuizing achter de uitwerpopening (met haar schuine rug).
RECEIVER_REAR = [(642, 42), (665, 33), (700, 22), (725, 19), (838, 19), (838, 124), (655, 124), (653, 100)]
# De trekkerbeugel als een C: buitenom van achter naar voor, en langs het gat terug.
GUARD = [(655, 124), (659, 150), (670, 164), (690, 171), (742, 171), (762, 161), (795, 134), (835, 124), (792, 126), (776, 135),
         (752, 157), (738, 164), (692, 164), (678, 159), (668, 148), (668, 126)]
TRIGGER = [(690, 126), (700, 126), (706, 140), (704, 152), (697, 160), (693, 158), (698, 150), (698, 140), (692, 132)]
FOREND = [(1168, 47), (1505, 44), (1518, 52), (1522, 70), (1522, 120), (1512, 134), (1480, 137), (1250, 141), (1175, 141),
          (1168, 135)]
PINS = [(675, 88), (767, 88)]


def section(w, y0, y1):
    """Een rechthoekige doorsnede (tegen de klok in): rand 1 is de rechterwand."""
    return [(8 - w, y0), (8 + w, y0), (8 + w, y1), (8 - w, y1)]


# ---------------------------------------------------------------- De onderdelen

def frame():
    """
    De Receiver: de stalen behuizing (de uitwerpopening rechts, de laadopening onderaan), de houten kolf met de rubberen
    kolfkap, de buis onder de loop met haar geribbelde dop, en de trekkerbeugel.
    """
    m = gm.Mesh()
    gm.prism(m, zy(RECEIVER_REAR), "x", 8 - REC_W, 8 + REC_W, "receiver", bevel=0.14, density=D, smooth=40)
    # De uitwerpopening: de behuizing is daar hol, met een gat in de rechterwand.
    box = section(REC_W, REC_Y0, REC_Y1)
    lo = (Y(62) - REC_Y0) / (REC_Y1 - REC_Y0)
    hi = (Y(25) - REC_Y0) / (REC_Y1 - REC_Y0)
    gm.prism(m, lg.c_shell(box, 0.18, 1, lo, hi), "z", PORT_Z0, PORT_Z1, "receiver_shell", caps=(False, False), density=D,
             smooth=30)
    gm.prism(m, box, "z", REC_Z0, PORT_Z0, "receiver", bevel_lo=0.1, bevel_hi=0.0, density=D)
    # De kolf en de kolfkap.
    gm.prism(m, zy(STOCK), "x", 8 - STOCK_W, 8 + STOCK_W, "wood", bevel=0.5, density=D, smooth=50)
    gm.prism(m, zy(PAD), "x", 8 - STOCK_W - 0.03, 8 + STOCK_W + 0.03, "rubber", bevel=0.4, density=D, smooth=50)
    # De buis onder de loop (daar zitten de patronen in), met haar dop.
    axis = {"base": (8, TUBE_Y, REC_Z0), "axis": (0, 0, -1)}
    gm.cylinder(m, (8, TUBE_Y, REC_Z0), (8, TUBE_Y, Z(1525)), TUBE_R, 12, "tube", density=D, data=axis)
    gm.lathe(m, (8, TUBE_Y, Z(1525)), (0, 0, -1),
             [(0, 0), (0, TUBE_R + 0.2), (Z(1525) - Z(1546), TUBE_R + 0.2), (Z(1525) - Z(1553), TUBE_R - 0.25),
              (Z(1525) - Z(1553), 0)], 12, "cap", density=16, data=axis)
    # De trekkerbeugel en de pennen in de behuizing.
    gm.prism(m, zy(GUARD), "x", 8 - 0.5, 8 + 0.5, "guard", bevel=0.08, density=16, smooth=50)
    for px, py in PINS:
        lg.pin(m, Z(px), Y(py), 8 - REC_W, 8 + REC_W, 0.16)
    return m


def slide():
    """
    De Forend: het houten voorhout om de buis, de twee stalen stangen die van het voorhout naar de grendel lopen, en de
    blanke grendel zelf (je ziet hem in de uitwerpopening). Pompen schuift het hele stuk 7 pixels naar achteren.
    """
    m = gm.Mesh()
    gm.prism(m, zy(FOREND), "x", 8 - 1.5, 8 + 1.5, "forend", bevel=0.6, density=D, smooth=50)
    # De stangen links en rechts van de buis, van het voorhout tot in de behuizing.
    for side in (-1, 1):
        gm.box(m, (8 + side * (TUBE_R + 0.02), TUBE_Y - 0.28, Z(1175)), (8 + side * (TUBE_R + 0.2), TUBE_Y + 0.28, Z(905)),
               "bar", density=D)
    # De grendel: een blank stalen blok in de behuizing, achter de kamer.
    gm.prism(m, lg.rounded_rect(8 - 1.25, Y(60), 8 + 1.25, Y(27), 0.3), "z", Z(968), Z(842), "bolt", density=D,
             data={"base": (8, BORE_Y, Z(968)), "axis": (0, 0, 1)})
    return m


def barrel():
    """De Barrel: de loop (dikker bij de kamer), de ring waarmee hij op de buis vastzit, en vooraan de kraal."""
    m = gm.Mesh()
    base_z = Z(985)

    def h(px):
        return base_z - Z(px)
    thick, thin = P.size(25), P.size(20)
    profile = [(0.0, thick), (h(1050), thick), (h(1160), thin), (h(MUZZLE - 2), thin), (h(MUZZLE), thin - 0.1)]
    lg.barrel(m, (8, BORE_Y, base_z), (0, 0, -1), profile, lg.mm(9.25), density=D)
    # De ring onder de loop waar de dop van de buis in schroeft.
    gm.box(m, (8 - 0.5, TUBE_Y + TUBE_R - 0.1, Z(1548)), (8 + 0.5, BORE_Y - thin + 0.15, Z(1526)), "lug", bevel=0.06, density=16)
    # De kraal: een bolletje op de loop, vlak achter de monding.
    bead = (8, BORE_Y + thin - 0.04, Z(1884))
    ball = [(0.0, 0.0)] + [(0.2 * (1 - math.cos(math.pi * i / 6)), 0.2 * math.sin(math.pi * i / 6)) for i in range(1, 6)] \
        + [(0.4, 0.0)]
    gm.lathe(m, bead, (0, 1, 0), ball, 8, "bead", density=16)
    return m


def springs():
    """De Magazine Spring: de lange veer in de buis, die de patronen naar de behuizing duwt."""
    m = gm.Mesh()
    lg.spring(m, (8, TUBE_Y, Z(1500)), (8, TUBE_Y, Z(1030)), TUBE_R - 0.3, rod=0.0)
    return m


def striker():
    """De Firing Pin: de slagpin door de grendel."""
    m = gm.Mesh()
    z0 = Z(968)
    length = Z(850) - z0
    gm.lathe(m, (8, BORE_Y, z0), (0, 0, 1), [(0, 0), (0, 0.04), (0.3, 0.07), (0.45, 0.13), (length - 0.5, 0.13), (length - 0.5, 0.22),
                                             (length, 0.22), (length, 0)], 8, "striker", density=16)
    return m


def trigger():
    """De trekker."""
    m = gm.Mesh()
    gm.prism(m, zy(TRIGGER), "x", 8 - 0.22, 8 + 0.22, "trigger", bevel=0.05, density=16, smooth=50)
    return m


def sling():
    """De Gun Sling aan de 870: een riem langs de linkerkant, van de dop van de buis in een boog naar de kolf."""
    m = gm.Mesh()
    start, rear = (Z(1535), Y(128)), (Z(200), Y(300))
    low = Y(400)
    top, bottom = [], []
    steps = 18
    for i in range(steps + 1):
        t = i / steps
        z = start[0] + (rear[0] - start[0]) * t
        y = start[1] + (rear[1] - start[1]) * t - (start[1] * (1 - t) + rear[1] * t - low) * 4 * t * (1 - t)
        top.append((z, y + 0.3))
        bottom.append((z, y - 0.3))
    gm.prism(m, top + bottom[::-1], "x", 8 - REC_W - 0.2, 8 - REC_W - 0.06, "sling", density=D, smooth=60)
    return m


# ---------------------------------------------------------------- Schilderen

METAL = {"steel": (38, 40, 46), "titanium": (120, 126, 135)}
WOOD = (158, 92, 44)
WOOD_DARK = (96, 50, 22)


def worn(col, p, amount=10):
    """Slijtplekken: grote, zachte vlekken die lichter of donkerder zijn."""
    v = lg.noise((p[0] * 0.2, p[1] * 0.45, p[2] * 0.3), 4.0, 41)
    return lg.add(col, (v - 0.5) * amount)


def painter(skin, isl, p, n, tx, ty):
    kind = isl.kind
    x, y, z = (float(c) for c in p)
    nx, ny, nz = (float(c) for c in n)
    metal = METAL[skin]
    if kind in ("receiver", "receiver_shell"):
        col = worn(lg.lit(lg.add(metal, 8), isl, p, n, grain=5, edge=34), p)
        if kind == "receiver_shell" and nx * (x - 8) + ny * (y - (REC_Y0 + REC_Y1) / 2) < 0:
            return (*lg.add(lg.DARK, 4), 255)                 # de binnenkant van de behuizing, door de uitwerpopening
        if kind == "receiver" and abs(nz) > 0.9 and PORT_Z0 - 0.05 < z < PORT_Z1 + 0.05:
            return (*lg.add(lg.DARK, 8), 255)
        if ny < -0.8 and abs(x - 8) < 1.1 and LOAD_Z0 < z < LOAD_Z1:
            # De laadopening onderaan: donker, met de blanke lepel die de patroon optilt.
            col = (96, 98, 104) if abs(x - 8) < 0.75 and z < LOAD_Z1 - 0.6 else lg.DARK
        return (*col, 255)
    if kind in ("tube", "lug", "guard", "bar"):
        return (*worn(lg.lit(lg.add(metal, 0 if kind != "bar" else 16), isl, p, n, grain=5, edge=30), p), 255)
    if kind == "cap":
        col = lg.lit(lg.add(metal, 6), isl, p, n, grain=4, edge=26)
        base = np.array(isl.data["base"])
        angle = math.atan2(y - base[1], x - base[0])
        if abs(nz) < 0.5 and int(angle * 14 / math.pi) % 2 == 0:
            col = lg.add(col, -14)                              # de ribbels op de dop
        return (*col, 255)
    if kind == "wood":
        return (*lg.wood(isl, p, n, base=WOOD, dark=WOOD_DARK, along="z"), 255)
    if kind == "forend":
        col = lg.wood(isl, p, n, base=WOOD, dark=WOOD_DARK, along="z")
        # De groeven in het voorhout: om de 7 mm een donkere streep over de zijkanten en de onderkant.
        if Z(1492) < z < Z(1210) and y < Y(66) and (Z(1210) - z) % 0.56 < 0.17:
            col = lg.add(col, -46)
        return (*col, 255)
    if kind == "rubber":
        col = lg.add((30, 29, 30), (lg.noise(p, 20, 4) - 0.5) * 6 + 12 * lg.edge_amount(isl, n))
        if abs(nx) > 0.9 and z > Z(60):
            # Het ruitjespatroon in de kolfkap.
            if (int((y - Y(356)) * 3.0) + int((z - Z(68)) * 3.0)) % 2 == 0:
                col = lg.add(col, 9)
        return (*col, 255)
    if kind == "barrel":
        col = worn(lg.lit(lg.add(metal, 2), isl, p, n, grain=5, edge=28), p)
        return (*lg.bore_dark(col, isl, p, n), 255)
    if kind == "bead":
        return (*lg.add((206, 176, 96), (lg.noise(p) - 0.5) * 8), 255)
    if kind == "bolt":
        col = lg.add(lg.BRIGHT[skin], 6 + (lg.noise(p, 16, 3) - 0.5) * 9)
        if nx > 0.9 and Z(968) < z < Z(954) and abs(y - Y(38)) < 0.22:
            col = lg.add(col, -60)                              # de uittrekker voor op de grendel
        return (*col, 255)
    if kind == "coil":
        return (*lg.coil(lg.BRIGHT[skin], isl, p, pitch=0.3), 255)
    if kind == "rod":
        return (*lg.add((42, 42, 45), (lg.noise(p) - 0.5) * 4), 255)
    if kind == "striker":
        return (*lg.add(lg.BRIGHT[skin], (lg.noise(p) - 0.5) * 6), 255)
    if kind == "trigger":
        return (*lg.lit(lg.add(lg.BRIGHT[skin], -30), isl, p, n, grain=4, edge=24), 255)
    if kind == "sling":
        return (*lg.add((120, 80, 46), (lg.noise(p, 16, 5) - 0.5) * 10), 255)
    if kind == "pin":
        base = np.array(isl.data["base"])
        r = math.hypot(y - base[1], z - base[2])
        col = (84, 86, 92) if r < 0.09 else (52, 54, 60)
        return (*lg.add(col, (lg.noise(p) - 0.5) * 6), 255)
    return (255, 0, 255, 255)


# ---------------------------------------------------------------- Het hele wapen

ID = "remington_870"
SKINS = ("steel", "titanium")
# Waar je rechterhand de greep vasthoudt (derde persoon), en hoe groot het wapen in je hand is.
GRIP = (8, Y(170), Z(585))
HAND_SCALE = lg.GUN_SCALE  # hoofdstuk 105: dezelfde maat voor elk vuurwapen (zie longgun.GUN_SCALE)


def build():
    parts = {"frame": frame(), "slide": slide(), "barrel": barrel(), "springs": springs(), "striker": striker(),
             "trigger": trigger(), "sling": sling()}
    return lg.Gun(ID, parts, {}, SKINS, painter, None, whole=("frame", "slide", "barrel", "trigger"), whole_mag=None)
