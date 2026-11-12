"""
De MP5 als echt 3D-model (hoofdstuk 103, jouw wens: "meer guns, de models zijn ook zoals de Glock").

Dit is de H&K MP5A3: de klassieke MP5 met de uitschuifbare kolf (uitgeschoven), de smalle handbeschermer en het ronde
magazijn van 30 patronen. De omtrek komt van een foto van een echte MP5A3 (de rechterkant, van Wikimedia Commons: "HK
MP5 noBG"); de punten hieronder staan in pixels van die foto en zijn opgemeten met tools/trace_outline.py. Het model
staat op de schaal van de Glock (1 pixel = 12,8 mm): de MP5 is 70 cm lang, dus bijna 55 pixels.

De zes onderdelen (dezelfde rollen als bij de Glock, met de namen van een MP5):
- frame   = Receiver: de stalen behuizing met de spanbuis, de handbeschermer, het onderstuk met de greep, de kolf en de
            vizieren;
- slide   = Bolt Carrier: de grendel (je ziet hem door de uitwerpopening) en, als los bewegend stuk, de spanhendel
            links vooraan ("slide_handle": die beweegt alleen als je doorlaadt, niet bij een schot);
- barrel  = Barrel: de loop met de drie nokken aan de monding;
- springs = Recoil Spring; striker = Firing Pin; trigger = Trigger Group (de trekker).

Assen zoals altijd: de loop naar -z, de kolf naar +z, rechts is +x, midden x = 8. De trekker staat op z = 8, y = 9, net
als bij de Glock, zodat je hand op dezelfde plek zit.
"""
import math

import numpy as np

import glock19_model as glock
import gunmesh as gm
import longgun as lg

# ---------------------------------------------------------------- Van de foto naar het model

# De MP5A3 met uitgeschoven kolf is 700 mm lang; op de foto is dat 1145 pixels (van de kolfplaat tot de monding).
P = lg.Photo(700.0 / 1145.0, 640, 8.0, 290, 9.0, muzzle_right=True)
Z, Y, zy = P.Z, P.Y, P.zy
D = 8  # texels per pixel voor de grote vlakken (kleine onderdelen krijgen 16)

BORE_Y = Y(162)                 # de as van de loop
REC_Y0, REC_Y1 = Y(182), Y(97)  # onderkant en bovenkant van de stalen behuizing
REC_Z0, REC_Z1 = Z(872), Z(437)  # voorkant (waar de handbeschermer begint) en achterkant
PORT_Z0, PORT_Z1 = Z(772), Z(705)  # de uitwerpopening, voor en achter
PORT_Y0, PORT_Y1 = Y(140), Y(113)
TUBE_Y = Y(110)                 # de as van de spanbuis boven de loop
BOLT_Y = 16.85                  # de as van de grendel in de behuizing
SIGHT_Y = Y(76)                 # de vizierlijn: het midden van de ring vooraan
REAR_Z = Z(540)                 # waar het achterste vizier staat
MAG_TOP = Y(205)                # zo ver steekt het magazijn in de schacht

# De omtrekken (foto-pixels), opgemeten met tools/trace_outline.py.
BUTT = [(81, 122), (92, 122), (105, 127), (133, 133), (135, 135), (142, 152), (142, 184), (128, 190), (110, 209),
        (103, 232), (93, 294), (86, 310), (77, 317), (69, 318), (60, 311), (57, 305), (56, 289), (63, 270), (68, 234),
        (66, 135), (72, 128), (80, 123)]
ENDCAP = [(417, 132), (497, 132), (497, 223), (489, 217), (436, 217), (429, 225), (424, 227), (412, 226), (406, 222),
          (404, 218), (403, 187), (401, 184), (398, 184), (398, 139), (414, 138), (417, 133)]
LOWER = [(497, 184), (745, 184), (745, 264), (607, 264), (605, 267), (596, 322), (587, 327), (582, 333), (548, 402),
         (545, 410), (547, 425), (544, 427), (534, 426), (481, 396), (462, 382), (463, 376), (528, 278), (533, 262),
         (532, 244), (528, 237), (517, 228), (497, 223)]
# De trekkerbeugel als een C: buitenom van voor naar de greep, en langs het gat terug.
GUARD = [(712, 266), (707, 280), (691, 323), (680, 333), (661, 334), (624, 333), (604, 323), (596, 321), (600, 310),
         (618, 323), (625, 325), (672, 326), (676, 325), (681, 321), (686, 312), (698, 276), (698, 266)]
PADDLE = [(730, 264), (741, 264), (740, 276), (736, 288), (734, 292), (729, 293), (727, 291), (727, 285), (732, 271)]
MAGWELL = [(745, 184), (822, 184), (822, 196), (814, 199), (811, 203), (812, 232), (818, 239), (810, 245), (812, 262),
           (745, 262)]
SIGHT_BASE = [(1086, 102), (1138, 100), (1145, 106), (1146, 127), (1140, 136), (1140, 150), (1150, 150), (1150, 175),
              (1143, 175), (1135, 182), (1114, 183), (1108, 189), (1086, 189)]
REAR_BASE = [(505, 99), (505, 95), (513, 90), (572, 90), (580, 97), (580, 99)]
SELECTOR = [(584, 194), (591, 190), (617, 231), (613, 240), (601, 236)]
TRIGGER = [(632, 268), (640, 268), (641, 285), (646, 300), (650, 312), (648, 317), (643, 316), (637, 304), (633, 288)]
# Het magazijn: het stuk dat je ziet (onder de schacht) en het rechte stuk dat in de schacht zit.
MAG = [(809, 205), (810, 245), (813, 273), (823, 316), (834, 349), (848, 386), (865, 424), (883, 459), (896, 479),
       (895, 484), (856, 509), (851, 508), (849, 506), (848, 498), (829, 468), (793, 391), (775, 335), (759, 268),
       (756, 264), (755, 205)]
MAG_BASE = [(893, 473), (899, 481), (898, 487), (857, 513), (849, 511), (845, 500), (847, 493)]
MAG_ZY = zy(MAG)

# Halve breedtes (pixels van het model).
REC_W = 1.5        # de behuizing is 38 mm breed
LOWER_W = 1.3      # het onderstuk en de greep 33 mm
HAND_W = 1.65      # de handbeschermer 42 mm
MAG_W = 0.9        # het magazijn 23 mm
RAIL_X = 1.75      # de twee stangen van de kolf lopen links en rechts langs de behuizing
SHELL = 0.18       # de wand van de behuizing bij de uitwerpopening


def receiver_section():
    """De doorsnede van de behuizing: onderaan recht, bovenaan rond (de buis waar de grendel in loopt)."""
    y0, y1 = REC_Y0, REC_Y1
    return [(8 - 1.3, y0), (8 + 1.3, y0), (8 + REC_W, y0 + 0.9), (8 + REC_W, y1 - 1.05), (8 + 1.15, y1 - 0.3),
            (8 + 0.55, y1), (8 - 0.55, y1), (8 - 1.15, y1 - 0.3), (8 - REC_W, y1 - 1.05), (8 - REC_W, y0 + 0.9)]


def handguard_section():
    """De doorsnede van de handbeschermer: breed in het midden, smaller onderaan."""
    y0, y1 = Y(188), Y(120)
    return [(8 - 0.95, y0), (8 + 0.95, y0), (8 + 1.55, y0 + 0.8), (8 + HAND_W, y0 + 1.9), (8 + 1.4, y1 - 0.3),
            (8 + 1.0, y1), (8 - 1.0, y1), (8 - 1.4, y1 - 0.3), (8 - HAND_W, y0 + 1.9), (8 - 1.55, y0 + 0.8)]


# ---------------------------------------------------------------- De onderdelen

def frame():
    """
    De Receiver: de stalen behuizing (met de uitwerpopening rechts), de spanbuis, de handbeschermer, de schacht van het
    magazijn, het zwarte onderstuk met de greep en de trekkerbeugel, de kolf (eindkap, twee stangen en de kolfplaat), en
    de vizieren (achter een trommel met een venster, voor een paaltje in een ring).
    """
    m = gm.Mesh()
    section = receiver_section()
    # De behuizing in drie stukken: achter de opening, de opening zelf (hol, met een gat rechts), en ervoor.
    gm.prism(m, section, "z", PORT_Z1, REC_Z1, "receiver", bevel_lo=0.0, bevel_hi=0.45, density=D)
    ya, yb = REC_Y0 + 0.9, REC_Y1 - 1.05
    a = (PORT_Y0 - ya) / (yb - ya)
    b = min(0.985, (PORT_Y1 - ya) / (yb - ya))
    gm.prism(m, lg.c_shell(section, SHELL, 2, a, b), "z", PORT_Z0, PORT_Z1, "receiver_shell", caps=(False, False),
             density=D, smooth=30)
    gm.prism(m, section, "z", REC_Z0, PORT_Z0, "receiver", density=D)
    # De twee nokken bovenop voor een kijkermontage.
    for px0, px1 in ((648, 700), (962, 990)):
        gm.box(m, (8 - 0.45, REC_Y1 - 0.05, Z(px1)), (8 + 0.45, REC_Y1 + 0.16, Z(px0)), "receiver", bevel=0.04, density=16)
    # De spanbuis boven de loop, met zijn smallere dop vooraan.
    gm.cylinder(m, (8, TUBE_Y, Z(1080)), (8, TUBE_Y, REC_Z0 + 0.3), 0.52, 14, "tube", density=D,
                data={"base": (8, TUBE_Y, Z(1080)), "axis": (0, 0, 1)})
    gm.cylinder(m, (8, TUBE_Y, Z(1110)), (8, TUBE_Y, Z(1080)), 0.42, 12, "tube", density=D,
                data={"base": (8, TUBE_Y, Z(1110)), "axis": (0, 0, 1)})
    # De handbeschermer.
    gm.prism(m, handguard_section(), "z", Z(1085), REC_Z0, "handguard", bevel=0.12, density=D, smooth=50)
    # De schacht van het magazijn (staal) met het pedaal van de magazijnvanger erachter.
    gm.prism(m, zy(MAGWELL), "x", 8 - LOWER_W - 0.02, 8 + LOWER_W + 0.02, "magwell", bevel=0.08, density=D)
    gm.prism(m, zy(PADDLE), "x", 8 - 0.5, 8 + 0.5, "lever", bevel=0.06, density=16)
    # Het onderstuk met de greep, en de trekkerbeugel.
    gm.prism(m, zy(LOWER), "x", 8 - LOWER_W, 8 + LOWER_W, "lower", bevel=0.2, density=D, smooth=50)
    gm.prism(m, zy(GUARD), "x", 8 - 0.5, 8 + 0.5, "guard", bevel=0.07, density=16, smooth=50)
    # De keuzeschakelaar (aan beide kanten een hendeltje) en zijn letters links: S wit, E en F rood.
    for x0, x1 in ((8 - LOWER_W - 0.14, 8 - LOWER_W + 0.02), (8 + LOWER_W - 0.02, 8 + LOWER_W + 0.14)):
        gm.prism(m, zy(SELECTOR), "x", x0, x1, "lever", bevel=0.04, density=16)
    for text, px, py, colour in (("S", 628, 196, (236, 236, 230, 255)), ("E", 640, 214, (214, 52, 44, 255)),
                                 ("F", 632, 236, (214, 52, 44, 255))):
        gm.decal(m, (8 - LOWER_W, Y(py) - 0.2, Z(px)), (0, 0, 1), (0, 1, 0), 0.34, 0.4,
                 glock.marking_text(0.34, 0.4, 96, text, colour), density=96)
    # Het opschrift links op de schacht.
    gm.decal(m, (8 - LOWER_W - 0.02, Y(236), Z(806)), (0, 0, 1), (0, 1, 0), 2.3, 0.42,
             glock.marking_text(2.3, 0.42, 96, "Kal. 9mm x 19"), density=96)
    gm.decal(m, (8 - LOWER_W - 0.02, Y(222), Z(800)), (0, 0, 1), (0, 1, 0), 1.2, 0.5,
             glock.marking_text(1.2, 0.5, 96, "MP5"), density=96)
    # De pennen: één door de eindkap, twee door het onderstuk.
    lg.pin(m, Z(466), Y(197), 8 - 1.82, 8 + 1.82, 0.2)
    lg.pin(m, Z(736), Y(223), 8 - LOWER_W, 8 + LOWER_W, 0.18)
    lg.pin(m, Z(736), Y(252), 8 - LOWER_W, 8 + LOWER_W, 0.16)
    # De kolf: de eindkap om de achterkant van de behuizing, twee stangen, en de kolfplaat.
    gm.prism(m, zy(ENDCAP), "x", 8 - 1.8, 8 + 1.8, "endcap", bevel=0.14, density=D)
    for side in (-1, 1):
        gm.cylinder(m, (8 + side * RAIL_X, Y(163), Z(452)), (8 + side * RAIL_X, Y(163), Z(112)), 0.4, 10, "stock_rail",
                    density=D)
    gm.prism(m, zy(BUTT), "x", 8 - 2.0, 8 + 2.0, "butt", bevel=0.4, density=D, smooth=50)
    # Het voorste vizier: de voet om de loop, de ring en het paaltje.
    gm.prism(m, zy(SIGHT_BASE), "x", 8 - 0.9, 8 + 0.9, "sight_base", bevel=0.1, density=D)
    lg.ring(m, (8, SIGHT_Y, Z(1104)), (0, 0, -1), P.size(33), P.size(25), P.size(25) - 0.24, "sight")
    gm.box(m, (7.9, Y(101), Z(1124)), (8.1, SIGHT_Y + 0.02, Z(1117)), "sight", bevel=0.02, density=16)
    # Het achterste vizier: de voet, de trommel, en erboven een venster waar je doorheen kijkt.
    gm.prism(m, zy(REAR_BASE), "x", 8 - 0.78, 8 + 0.78, "sight", bevel=0.05, density=16)
    # De trommel is in het echt een schuine cilinder met vier gaatjes; hier een lage trommel met erop een ring waar je
    # doorheen kijkt (groter dan het echte gaatje, zodat de ring van het voorste vizier erin past als je richt).
    gm.cylinder(m, (8, Y(92), REAR_Z), (8, SIGHT_Y - 0.7, REAR_Z), 0.66, 14, "sight", density=16)
    lg.ring(m, (8, SIGHT_Y, REAR_Z + 0.3), (0, 0, -1), 0.6, 0.78, 0.56, "sight")
    return m


def slide():
    """De Bolt Carrier: de grendel in de behuizing (je ziet hem door de uitwerpopening) met zijn buis naar voren."""
    m = gm.Mesh()
    gm.cylinder(m, (8, BOLT_Y, PORT_Z0 + 0.06), (8, BOLT_Y, 9.4), 1.1, 16, "bolt", density=D,
                data={"base": (8, BOLT_Y, PORT_Z0), "axis": (0, 0, 1)})
    gm.cylinder(m, (8, TUBE_Y, -8.5), (8, TUBE_Y, PORT_Z0 + 0.06), 0.3, 10, "bolt", density=D)
    return m


HANDLE_Z = Z(1046)   # waar de spanhendel staat als hij naar voren is


def handle():
    """De spanhendel links op de spanbuis: een steel met een knop. Hij beweegt alleen als je doorlaadt."""
    m = gm.Mesh()
    gm.cylinder(m, (8 - 0.4, TUBE_Y, HANDLE_Z), (8 - 1.45, TUBE_Y + 0.12, HANDLE_Z + 0.25), 0.17, 8, "handle", density=16)
    gm.lathe(m, (8 - 1.35, TUBE_Y + 0.11, HANDLE_Z + 0.23), (-1, 0.1, 0.24),
             [(0, 0), (0, 0.22), (0.12, 0.3), (0.62, 0.3), (0.74, 0.2), (0.74, 0)], 10, "handle", density=16)
    return m


def barrel():
    """De loop: van de kamer (achter de uitwerpopening) tot de monding met de drie nokken voor een demper."""
    m = gm.Mesh()
    base_z = PORT_Z0 - 0.1
    def h(px):
        return base_z - Z(px)
    profile = [(0.0, 0.62), (2.0, 0.62), (2.1, 0.5), (h(1141), 0.5), (h(1141), 0.6), (h(1147), 0.6), (h(1147), 0.66),
               (h(1160), 0.66), (h(1160), 0.55), (h(1197), 0.55), (h(1202), 0.47)]
    lg.barrel(m, (8, BORE_Y, base_z), (0, 0, -1), profile, lg.mm(4.5), density=D)
    return m


def springs():
    """De Recoil Spring: de sluitveer met zijn geleidestang, achter de grendel."""
    m = gm.Mesh()
    lg.spring(m, (8, BOLT_Y, 9.45), (8, BOLT_Y, 17.0), 0.3, rod=0.09)
    return m


def striker():
    """De Firing Pin: de slagpin met zijn veertje, in de grendel."""
    m = gm.Mesh()
    gm.lathe(m, (8, BOLT_Y, 2.0), (0, 0, 1), [(0, 0), (0, 0.03), (0.3, 0.05), (0.4, 0.1), (5.6, 0.1), (5.6, 0)], 8,
             "striker", density=16)
    lg.spring(m, (8, BOLT_Y, 3.2), (8, BOLT_Y, 6.4), 0.16)
    gm.lathe(m, (8, BOLT_Y, 7.2), (0, 0, 1), [(0, 0), (0, 0.2), (0.4, 0.2), (0.4, 0)], 10, "striker", density=16)
    return m


def trigger():
    """De trekker."""
    m = gm.Mesh()
    gm.prism(m, zy(TRIGGER), "x", 8 - 0.24, 8 + 0.24, "trigger", bevel=0.07, density=16, smooth=50)
    return m


def sling():
    """
    De Gun Sling aan de MP5 (je hangt hem eraan op de Gunsmith Table): een leren riem langs de linkerkant, van de voet
    van het voorste vizier in een boog onder het wapen door naar de eindkap van de kolf.
    """
    m = gm.Mesh()
    front, rear = (Z(1094), Y(150)), (Z(420), Y(160))
    low = Y(330)
    top, bottom = [], []
    steps = 14
    for i in range(steps + 1):
        t = i / steps
        z = front[0] + (rear[0] - front[0]) * t
        # Een doorhangende boog: het laagst in het midden.
        y = front[1] + (rear[1] - front[1]) * t - (front[1] * (1 - t) + rear[1] * t - low) * 4 * t * (1 - t)
        top.append((z, y + 0.28))
        bottom.append((z, y - 0.28))
    gm.prism(m, top + bottom[::-1], "x", 8 - 2.12, 8 - 1.98, "sling", density=D, smooth=60)
    return m


def magazine(loaded=True, extended=False):
    """
    Het gebogen magazijn van 30 patronen (staal): het lijf, de bodemplaat, de lipjes bovenaan, en bovenin een patroon
    (of, leeg, de opvolger).
    """
    m = gm.Mesh()
    gm.prism(m, MAG_ZY, "x", 8 - MAG_W, 8 + MAG_W, "mag_body", bevel=0.1, density=D, smooth=50, data={"loaded": loaded})
    gm.prism(m, zy(MAG_BASE), "x", 8 - MAG_W - 0.07, 8 + MAG_W + 0.07, "mag_base", bevel=0.08, density=16, smooth=50)
    z0, z1 = Z(806), Z(760)
    for side in (-1, 1):
        lip = [(8 + side * 0.88, MAG_TOP - 0.1), (8 + side * 0.72, MAG_TOP - 0.1), (8 + side * 0.72, MAG_TOP + 0.08),
               (8 + side * 0.5, MAG_TOP + 0.15), (8 + side * 0.5, MAG_TOP + 0.22), (8 + side * 0.88, MAG_TOP + 0.15)]
        gm.prism(m, lip, "z", z0, z1, "mag_lip", bevel=0.02, density=16)
    if loaded:
        m.add(glock.round_9mm((8.0, MAG_TOP + 0.3, z1 - 0.08), (0, 0, -1)))
    else:
        gm.box(m, (8 - 0.66, MAG_TOP - 0.06, z0 + 0.1), (8 + 0.66, MAG_TOP + 0.08, z1 - 0.1), "follower", bevel=0.04,
               density=16)
    return m


# ---------------------------------------------------------------- Schilderen

BLACK_STEEL = (48, 49, 53)


def painter(skin, isl, p, n, tx, ty):
    kind = isl.kind
    x, y, z = (float(c) for c in p)
    nx, ny, nz = (float(c) for c in n)
    metal = lg.GUN_METAL[skin]
    if kind in ("receiver", "receiver_shell", "magwell", "tube", "endcap", "stock_rail", "sight", "sight_base"):
        col = lg.lit(metal, isl, p, n, grain=5, edge=30)
        if kind == "receiver_shell":
            # De binnenkant van de holle behuizing (door de uitwerpopening te zien): donker.
            if nx * (x - 8) + ny * (y - (REC_Y0 + REC_Y1) / 2) < 0:
                return (*lg.add(lg.DARK, 4), 255)
        if kind in ("receiver", "receiver_shell"):
            if kind == "receiver" and abs(nz) > 0.9 and PORT_Z0 - 0.05 < z < PORT_Z1 + 0.05:
                return (*lg.add(lg.DARK, 8), 255)          # de wanden voor en achter de uitwerpopening
            if abs(nx) > 0.9 and Z(772) < z < Z(497) and Y(158) < y < Y(145):
                col = lg.add(col, -26)                      # de gleuf waar de stang van de kolf in schuift
            if abs(nx) > 0.9 and (abs(y - Y(141)) < 0.045 or abs(y - Y(162)) < 0.045):
                col = lg.add(col, 16)                       # de geperste richels langs de behuizing
        if kind == "tube" and nx < -0.55 and abs(y - TUBE_Y) < 0.13 and Z(1060) < z < Z(905):
            col = lg.DARK                                    # de gleuf van de spanhendel, links
        if kind == "magwell":
            if ny < -0.8:
                col = lg.DARK                                # de opening waar het magazijn in gaat
            elif abs(nx) > 0.9:
                # De geperste rechthoek op de zijkant.
                dz0, dz1, dy0, dy1 = z - Z(800), Z(752) - z, y - Y(240), Y(197) - y
                inside = min(dz0, dz1, dy0, dy1)
                if 0 <= inside < 0.07:
                    col = lg.add(col, 20)
        if kind == "endcap":
            col = lg.add(col, -5)
        if kind == "stock_rail":
            col = lg.add(col, 6)
        return (*col, 255)
    if kind in ("lower", "guard", "handguard", "butt"):
        col = lg.lit(lg.POLYMER, isl, p, n, grain=4, edge=30)
        side = abs(nx) > 0.9
        if kind == "lower" and y < Y(270):
            # De greep: een fijn korreltje op de zijkanten, groefjes voor en achter.
            if side:
                v = lg.noise(p, 16, 7)
                col = lg.add(col, 8 if v > 0.7 else (-6 if v < 0.25 else 0))
            elif (y * 4.0) % 1.0 < 0.3:
                col = lg.add(col, -9)
        if kind == "handguard" and abs(nx) > 0.45:
            if Z(1076) < z < Z(892) and Y(151) < y < Y(126):
                # Het ruitjespatroon bovenaan.
                u, v = (z * 6.5 + y * 6.5) % 1.0, (z * 6.5 - y * 6.5) % 1.0
                col = lg.add(col, 9 if (u < 0.5) != (v < 0.5) else -7)
            elif Z(1076) < z < Z(892) and Y(186) < y < Y(166):
                # De bolletjes onderaan.
                u, v = (z * 5.0) % 1.0 - 0.5, (y * 5.0 + (0.5 if int(z * 5.0) % 2 else 0.0)) % 1.0 - 0.5
                col = lg.add(col, 12 if u * u + v * v < 0.09 else -4)
            elif abs(y - Y(158)) < 0.1:
                col = lg.add(col, -10)                      # de groef tussen de twee vlakken
        if kind == "butt" and nz > 0.8 and (y * 2.6) % 1.0 < 0.28:
            col = lg.add(col, -10)                          # ribbels op de kolfplaat
        return (*col, 255)
    if kind == "sling":
        # Leer: bruin, met een stiksel langs beide randen.
        col = lg.add((124, 82, 48), (lg.noise(p, 16, 5) - 0.5) * 10)
        return (*col, 255)
    if kind in ("lever", "trigger", "handle"):
        return (*lg.lit(BLACK_STEEL, isl, p, n, grain=4, edge=26), 255)
    if kind == "barrel":
        col = lg.lit(lg.add(metal, 8), isl, p, n, grain=5, edge=26)
        return (*lg.bore_dark(col, isl, p, n), 255)
    if kind == "bolt":
        col = lg.add(lg.BRIGHT[skin], -26 + (lg.noise(p, 16, 3) - 0.5) * 8)
        if abs((z * 2.2) % 1.0 - 0.5) < 0.06:
            col = lg.add(col, -22)                          # ringen op de grendel
        return (*col, 255)
    if kind == "coil":
        return (*lg.coil(lg.BRIGHT[skin], isl, p), 255)
    if kind == "rod":
        return (*lg.add((42, 42, 45), (lg.noise(p) - 0.5) * 4), 255)
    if kind == "striker":
        return (*lg.add(lg.BRIGHT[skin], (lg.noise(p) - 0.5) * 6), 255)
    if kind == "pin":
        base = np.array(isl.data["base"])
        r = math.hypot(y - base[1], z - base[2])
        col = (158, 162, 169) if r < 0.07 else (96, 99, 105)
        return (*lg.add(col, (lg.noise(p) - 0.5) * 6), 255)
    return (255, 0, 255, 255)


def mag_painter(skin, isl, p, n, tx, ty):
    kind = isl.kind
    x, y, z = (float(c) for c in p)
    nx, ny, nz = (float(c) for c in n)
    grain = lg.noise(p) - 0.5
    if kind == "mag_body":
        col = lg.add((50, 52, 57), grain * 5 + 26 * lg.edge_amount(isl, n))
        if abs(nx) > 0.9:
            d = gm.distance_to_outline((z, y), MAG_ZY)
            if 0.3 < d < 0.4:
                col = lg.add(col, 16)                       # de geperste rib rondom
            elif 0.4 <= d < 0.46:
                col = lg.add(col, -8)
        if ny > 0.9 and y > MAG_TOP - 0.02:
            col = (26, 26, 28) if isl.data.get("loaded") else (60, 60, 64)
        return (*col, 255)
    if kind == "mag_base":
        return (*lg.add((44, 45, 48), grain * 4 + 28 * lg.edge_amount(isl, n)), 255)
    if kind == "mag_lip":
        return (*lg.add((84, 86, 92), grain * 6 + 18 * lg.edge_amount(isl, n)), 255)
    if kind == "follower":
        return (*lg.add((66, 66, 70), grain * 4), 255)
    if kind in ("brass", "copper"):
        return (*lg.round_paint(isl, p, n, kind, grain), 255)
    return (255, 0, 255, 255)


# ---------------------------------------------------------------- Het hele wapen

ID = "mp5"
SKINS = ("steel", "titanium")
# Waar je rechterhand de greep vasthoudt (derde persoon), en hoe groot het wapen in je hand is.
GRIP = (8, Y(330), Z(532))
HAND_SCALE = 0.36
# Waar de voet van het magazijn zit (voor in je hand en in de pouch), en hoe schuin het staat.
MAG_FOOT = (8, Y(496), Z(872))


def build():
    parts = {"frame": frame(), "slide": slide(), "slide_handle": handle(), "barrel": barrel(), "springs": springs(),
             "striker": striker(), "trigger": trigger(), "sling": sling()}
    mags = {"magazine": magazine(True), "magazine_empty": magazine(False)}
    return lg.Gun(ID, parts, mags, SKINS, painter, mag_painter,
                  whole=("frame", "slide", "slide_handle", "barrel", "trigger"))
