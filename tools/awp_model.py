"""
De AWP als echt 3D-model (hoofdstuk 103, stap 3, jouw wens: "meer guns, de models zijn ook zoals de Glock").

De AWP uit Counter-Strike is de Arctic Warfare van Accuracy International: een grendelgeweer met een kunststof kolf met
een duimgat. Dit is de versie in .338 Lapua Magnum. De omtrek komt van een foto van het echte wapen (de linkerkant, van
Wikimedia Commons: de L115A3 van het Britse leger); de punten hieronder staan in pixels van die foto en zijn opgemeten met
tools/trace_outline.py (de kolf op zijn kleur: "--colour"). Op die foto zitten een kijker, een tweepoot en een demper op
het wapen; die horen hier niet bij (dat worden attachments). Het model staat op de schaal van de Glock (1 pixel = 12,8
mm): de AWP is 125 cm lang, dus bijna 98 pixels.

Wat je op de foto niet ziet (de rechterkant) komt uit de maten van het echte wapen: de uitwerpopening in de behuizing en
de grendel met zijn knop, die je optilt en naar achteren trekt om door te laden.

De zes onderdelen (dezelfde rollen als bij de Glock, met de namen van een grendelgeweer):
- frame   = Receiver: de kolf (de handbeschermer, de greep met het duimgat, de kolf met wangstuk en kolfkap), de stalen
            behuizing met de rail, het scharnier van de kolf, en het ijzeren vizier (de keep achteraan op de rail);
- slide   = Bolt: de grendel met zijn knop rechts en zijn kap achteraan;
- barrel  = Barrel: de zware loop met de mondingsrem en de korrel;
- springs = Mainspring (de slagveer in de grendel); striker = Firing Pin; trigger = Trigger Group (de trekker).

Twee kleuren: met een stalen Receiver is de kolf groen (zoals de AWP uit Counter-Strike), met een van titanium zandkleurig
(zoals op de foto).

Assen zoals altijd: de loop naar -z, de kolf naar +z, rechts is +x, midden x = 8. De trekker staat op z = 8, y = 9.
"""
import math

import numpy as np

import ammo_models as ammo
import gunmesh as gm
import longgun as lg

# ---------------------------------------------------------------- Van de foto naar het model

# De kijker op de foto (een Schmidt & Bender 5-25x56) is 417 mm lang en op de foto 542 pixels: 0,77 mm per pixel. Dat klopt
# met de afstand van de trekker tot de kolfkap (36 cm). De foto toont de linkerkant: de loop wijst naar links.
P = lg.Photo(0.77, 1405, 8.0, 270, 9.0, muzzle_right=False)
Z, Y, zy = P.Z, P.Y, P.zy
D = 8

BORE_Y = Y(177.5)                # de as van de loop (en van de grendel)
RAIL_Y0, RAIL_Y1 = Y(160), Y(152)    # de rail op de behuizing: onderkant en bovenkant
SIGHT_Y = Y(144)                 # de vizierlijn van het ijzeren vizier, net boven de rail
REC_Z0, REC_Z1 = Z(1118), Z(1398)    # de stalen behuizing: voor en achter
PORT_Z0, PORT_Z1 = Z(1228), Z(1338)  # de uitwerpopening (rechts), boven het magazijn
MAG_TOP = Y(203)                 # zo ver steekt het magazijn in het wapen
MUZZLE = 250                     # de voorkant van de mondingsrem (foto-pixels)

STOCK_W = 1.7      # de kolf is vooraan 44 mm breed
BUTT_W = 1.55
REC_W = 1.0        # de behuizing is een stalen balk van 26 mm

# De omtrekken (foto-pixels). De kolf in twee stukken, met het scharnier (de kolf klapt weg) en het duimgat ertussen.
STOCK_FRONT = [(868, 183), (1125, 184), (1290, 184), (1340, 186), (1354, 191), (1422, 190), (1449, 205), (1465, 208),
               (1512, 209), (1512, 246), (1500, 248), (1492, 256), (1490, 266), (1493, 280), (1500, 292), (1512, 303),
               (1530, 310), (1548, 310), (1557, 307), (1570, 325), (1579, 348), (1565, 352), (1540, 355), (1520, 360),
               (1500, 367), (1480, 370), (1466, 369), (1462, 365), (1462, 350), (1458, 330), (1452, 310), (1447, 296),
               (1441, 288), (1434, 285), (1434, 259), (1428, 253), (1368, 253), (1361, 260), (1361, 283), (1352, 279),
               (1340, 277), (1326, 277), (1316, 267), (1305, 262), (1289, 264), (1250, 268), (1218, 266), (1186, 259),
               (1158, 252), (1125, 245), (1100, 243), (867, 241), (860, 238), (857, 231), (857, 195), (860, 187)]
STOCK_BUTT = [(1556, 208), (1607, 209), (1612, 204), (1628, 201), (1790, 201), (1790, 350), (1755, 350), (1750, 312),
              (1650, 310), (1620, 322), (1603, 331), (1591, 342), (1581, 349), (1570, 325), (1557, 307), (1566, 298),
              (1571, 280), (1566, 262), (1556, 249)]
# De trekkerbeugel als een C onder het gat: van voor naar achter, en binnendoor terug.
GUARD = [(1352, 279), (1358, 289), (1364, 295), (1374, 301), (1420, 301), (1432, 296), (1441, 288), (1434, 285), (1420, 295),
         (1376, 295), (1361, 283)]
CHEEK = [(1606, 197), (1606, 182), (1613, 174), (1780, 172), (1789, 177), (1790, 197)]
BUTT_PAD = [(1786, 190), (1796, 187), (1866, 187), (1872, 196), (1874, 300), (1877, 362), (1786, 362)]
TRIGGER = [(1402, 252), (1409, 252), (1412, 262), (1411, 272), (1405, 282), (1397, 288), (1395, 286), (1402, 278), (1406, 268),
           (1405, 258)]
# Het magazijn (vijf patronen op een rij): het stalen doosje dat onder de kolf uitsteekt, en erboven het stuk in het wapen.
# Op de foto is het 9 cm lang; een .338-patroon is 9,35 cm, dus hier is het vooraan een halve centimeter langer.
MAG = [(1211, 203), (1338, 203), (1338, 320), (1213, 311)]
MAG_ZY = zy(MAG)
# Het lange magazijn (de extended mag, hoofdstuk 103 stap 6b): 10 patronen, vijf erbij onder elkaar: 5,9 pixels (75 mm)
# dieper. De twee onderste hoekpunten zakken.
MAG_LONG_DROP = 5.86
MAG_LONG_ZY = lg.extend_straight(MAG_ZY, {2, 3}, (0.0, -MAG_LONG_DROP))
# De schroeven in de kolf.
SCREWS = [(911, 218), (1051, 219), (1196, 229), (1347, 228), (1480, 222), (1487, 332), (1635, 222), (1707, 222), (1660, 292),
          (1745, 292)]


def section(w, y0, y1):
    """Een rechthoekige doorsnede (tegen de klok in): rand 1 is de rechterwand."""
    return [(8 - w, y0), (8 + w, y0), (8 + w, y1), (8 - w, y1)]


# ---------------------------------------------------------------- De onderdelen

def frame():
    """
    De Receiver: de kolf in twee stukken (voor: de handbeschermer, het bed van de behuizing en de greep; achter: de kolf)
    met het scharnier ertussen, het wangstuk en de kolfkap; de stalen behuizing met de uitwerpopening rechts en de rail
    erop; de trekkerbeugel, de magazijnpal, en achteraan op de rail de keep van het ijzeren vizier.
    """
    m = gm.Mesh()
    gm.prism(m, zy(STOCK_FRONT), "x", 8 - STOCK_W, 8 + STOCK_W, "stock", bevel=0.45, density=D, smooth=50)
    gm.prism(m, zy(STOCK_BUTT), "x", 8 - BUTT_W, 8 + BUTT_W, "stock", bevel=0.42, density=D, smooth=50)
    gm.prism(m, zy(GUARD), "x", 8 - 0.5, 8 + 0.5, "stock", bevel=0.12, density=16, smooth=50)
    # Het scharnier van de kolf, het wangstuk (op twee pootjes) en de rubberen kolfkap.
    gm.box(m, (8 - STOCK_W - 0.05, Y(247), Z(1511)), (8 + STOCK_W + 0.05, Y(199), Z(1558)), "black", bevel=0.12, density=D)
    gm.prism(m, zy(CHEEK), "x", 8 - 1.3, 8 + 1.3, "black", bevel=0.35, density=D, smooth=50)
    for px in (1640, 1740):
        gm.box(m, (8 - 0.3, Y(203), Z(px)), (8 + 0.3, Y(195), Z(px + 14)), "black", density=16)
    gm.prism(m, zy(BUTT_PAD), "x", 8 - BUTT_W - 0.05, 8 + BUTT_W + 0.05, "rubber", bevel=0.4, density=D, smooth=50)
    # De stalen behuizing in drie stukken: voor de uitwerpopening, de opening zelf (hol, met een gat rechts), en erachter.
    y0, y1 = Y(192), RAIL_Y0
    box = section(REC_W, y0, y1)
    gm.prism(m, box, "z", REC_Z0, PORT_Z0, "receiver", bevel_lo=0.08, bevel_hi=0.0, density=D)
    port_lo = (Y(186) - y0) / (y1 - y0)
    port_hi = (Y(164) - y0) / (y1 - y0)
    gm.prism(m, lg.c_shell(box, 0.16, 1, port_lo, port_hi), "z", PORT_Z0, PORT_Z1, "receiver_shell", caps=(False, False),
             density=D, smooth=30)
    gm.prism(m, box, "z", PORT_Z1, REC_Z1, "receiver", bevel_lo=0.0, bevel_hi=0.06, density=D)
    # De rail voor een kijker, met zijn gleuven.
    gm.box(m, (8 - 0.82, RAIL_Y0, Z(1095)), (8 + 0.82, RAIL_Y1, Z(1412)), "rail", bevel=0.05, density=16)
    # Het ijzeren vizier: achteraan op de rail een plaatje met een keep (twee paaltjes met een gleuf ertussen).
    notch_z0, notch_z1 = Z(1396), Z(1404)
    for x0, x1 in ((7.2, 7.78), (8.22, 8.8)):
        gm.box(m, (x0, RAIL_Y1 - 0.02, notch_z0), (x1, SIGHT_Y + 0.32, notch_z1), "sight", bevel=0.03, density=16)
    gm.box(m, (7.74, RAIL_Y1 - 0.02, notch_z0), (8.26, SIGHT_Y - 0.1, notch_z1), "sight", bevel=0.0, density=16)
    # De magazijnpal achter het magazijn, en de schroeven in de kolf.
    gm.box(m, (8 - 0.3, Y(310), Z(1343)), (8 + 0.3, Y(274), Z(1350)), "black", bevel=0.04, density=16)
    for px, py in SCREWS:
        w = BUTT_W if px > 1556 else STOCK_W
        lg.pin(m, Z(px), Y(py), 8 - w, 8 + w, 0.24)
    return m


def slide():
    """
    De Bolt: de grendel in de behuizing (je ziet hem door de uitwerpopening), zijn kap achteraan met het spanstuk, en de
    grendelknop die rechts schuin omlaag steekt. Doorladen: de knop optillen, de grendel naar achteren trekken, weer naar
    voren duwen en de knop omlaag.
    """
    m = gm.Mesh()
    axis = {"base": (8, BORE_Y, REC_Z0), "axis": (0, 0, 1)}
    gm.cylinder(m, (8, BORE_Y, Z(1150)), (8, BORE_Y, REC_Z1), 0.62, 12, "bolt", density=D, data=axis)
    gm.cylinder(m, (8, BORE_Y, REC_Z1), (8, BORE_Y, Z(1438)), 0.9, 12, "shroud", density=D, data=axis)
    gm.cylinder(m, (8, BORE_Y, Z(1438)), (8, BORE_Y, Z(1448)), 0.3, 8, "bolt", density=16, data=axis)
    # De grendelknop: een steel die rechts schuin omlaag en wat naar achteren steekt, met een bol aan het eind.
    root = (8 + 0.75, BORE_Y - 0.1, Z(1385))
    knob = (8 + 3.5, BORE_Y - 1.9, Z(1392))
    gm.cylinder(m, root, knob, 0.2, 8, "handle", density=16)
    direction = gm.unit(np.asarray(knob) - np.asarray(root))
    ball = [(0.0, 0.0)] + [(0.45 * (1 - math.cos(math.pi * i / 6)), 0.45 * math.sin(math.pi * i / 6)) for i in range(1, 6)] \
        + [(0.9, 0.0)]
    gm.lathe(m, tuple(np.asarray(knob) - direction * 0.2), tuple(direction), ball, 10, "handle", density=16)
    return m


def barrel():
    """
    De Barrel: de zware loop (dikker bij de behuizing), de mondingsrem met zijn gleuven, en vlak achter de rem de korrel
    van het ijzeren vizier op zijn voetje.
    """
    m = gm.Mesh()
    base_z = Z(1140)

    def h(px):
        return base_z - Z(px)
    thick, thin, brake = P.size(18), P.size(13), P.size(17)
    profile = [(0.0, thick), (h(1080), thick), (h(330), thin), (h(316), thin), (h(314), brake), (h(MUZZLE + 2), brake),
               (h(MUZZLE), brake - 0.12)]
    lg.barrel(m, (8, BORE_Y, base_z), (0, 0, -1), profile, lg.mm(4.3), density=D)
    # De korrel: een klem om de loop, een voetje, en het paaltje tussen twee oortjes.
    top = BORE_Y + thin
    lg.ring(m, (8, BORE_Y, Z(352)), (0, 0, -1), Z(352) - Z(338), thin + 0.14, thin - 0.02, "sight", density=16)
    gm.box(m, (8 - 0.36, top - 0.05, Z(338)), (8 + 0.36, top + 0.55, Z(352)), "sight", bevel=0.05, density=16)
    for x0, x1 in ((8 - 0.5, 8 - 0.3), (8 + 0.3, 8 + 0.5)):
        gm.box(m, (x0, top + 0.5, Z(339)), (x1, SIGHT_Y + 0.16, Z(351)), "sight", bevel=0.03, density=16)
    gm.cylinder(m, (8, top + 0.5, Z(345)), (8, SIGHT_Y, Z(345)), 0.12, 8, "sight", density=16)
    return m


def springs():
    """De Mainspring: de slagveer in de grendel, om de slagpin."""
    m = gm.Mesh()
    lg.spring(m, (8, BORE_Y, Z(1290)), (8, BORE_Y, Z(1395)), 0.3, rod=0.0)
    return m


def striker():
    """De Firing Pin: de lange slagpin door de grendel, met zijn kop achteraan."""
    m = gm.Mesh()
    z0 = Z(1215)
    length = Z(1420) - z0
    gm.lathe(m, (8, BORE_Y, z0), (0, 0, 1), [(0, 0), (0, 0.03), (0.35, 0.06), (0.5, 0.11), (length - 0.5, 0.11), (length - 0.5, 0.2),
                                             (length, 0.2), (length, 0)], 8, "striker", density=16)
    return m


def trigger():
    """De trekker."""
    m = gm.Mesh()
    gm.prism(m, zy(TRIGGER), "x", 8 - 0.2, 8 + 0.2, "trigger", bevel=0.05, density=16, smooth=50)
    return m


def sling():
    """De Gun Sling aan de AWP: een riem langs de linkerkant, van de handbeschermer in een boog naar de kolf."""
    m = gm.Mesh()
    start, rear = (Z(905), Y(222)), (Z(1715), Y(250))
    low = Y(400)
    top, bottom = [], []
    steps = 18
    for i in range(steps + 1):
        t = i / steps
        z = start[0] + (rear[0] - start[0]) * t
        y = start[1] + (rear[1] - start[1]) * t - (start[1] * (1 - t) + rear[1] * t - low) * 4 * t * (1 - t)
        top.append((z, y + 0.3))
        bottom.append((z, y - 0.3))
    gm.prism(m, top + bottom[::-1], "x", 8 - STOCK_W - 0.2, 8 - STOCK_W - 0.06, "sling", density=D, smooth=60)
    return m


def magazine(loaded=True, extended=False):
    """
    Het stalen magazijn van vijf patronen: een recht doosje met twee ribben, de lipjes bovenaan, en bovenin een patroon
    .338 Lapua Magnum (of, leeg, de opvolger). {extended}: het lange magazijn van tien patronen.
    """
    m = gm.Mesh()
    outline = MAG_LONG_ZY if extended else MAG_ZY
    gm.prism(m, outline, "x", 8 - 0.9, 8 + 0.9, "mag_body", bevel=0.1, density=D, smooth=50, data={"loaded": loaded})
    z0, z1 = Z(1216), Z(1334)
    for side in (-1, 1):
        lip = [(8 + side * 0.88, MAG_TOP - 0.1), (8 + side * 0.72, MAG_TOP - 0.1), (8 + side * 0.72, MAG_TOP + 0.12),
               (8 + side * 0.5, MAG_TOP + 0.22), (8 + side * 0.5, MAG_TOP + 0.3), (8 + side * 0.88, MAG_TOP + 0.2)]
        gm.prism(m, lip, "z", z0, z1, "mag_lip", bevel=0.02, density=16)
    if loaded:
        m.add(ammo.round_mesh("338", (8.0, MAG_TOP + 0.42, z1 - 0.12), (0, 0, -1)))
    else:
        gm.box(m, (8 - 0.66, MAG_TOP - 0.06, z0 + 0.15), (8 + 0.66, MAG_TOP + 0.12, z1 - 0.15), "follower", bevel=0.04,
               density=16)
    return m


# ---------------------------------------------------------------- Schilderen

# De kolf: groen bij staal (de AWP uit Counter-Strike), zandkleurig bij titanium (zoals op de foto).
STOCK = {"steel": (94, 108, 64), "titanium": (172, 140, 92)}
METAL = {"steel": (46, 48, 54), "titanium": (116, 122, 131)}


def worn(col, p, amount=10):
    """Slijtplekken: grote, zachte vlekken die lichter of donkerder zijn."""
    v = lg.noise((p[0] * 0.2, p[1] * 0.45, p[2] * 0.3), 4.0, 31)
    return lg.add(col, (v - 0.5) * amount)


def painter(skin, isl, p, n, tx, ty):
    kind = isl.kind
    x, y, z = (float(c) for c in p)
    nx, ny, nz = (float(c) for c in n)
    metal = METAL[skin]
    if kind == "stock":
        # Kunststof: mat, een beetje korrelig, lichter op de afgeronde randen.
        col = lg.add(STOCK[skin], (lg.noise(p, 20, 7) - 0.5) * 9 + 20 * lg.edge_amount(isl, n))
        col = worn(col, p, 8)
        if abs(nx) > 0.9:
            # De lange groef in de zijkant van de handbeschermer.
            if Z(880) < z < Z(1110) and abs(y - Y(190)) < 0.07:
                col = lg.add(col, -22)
        return (*col, 255)
    if kind in ("receiver", "receiver_shell"):
        col = worn(lg.lit(lg.add(metal, 26), isl, p, n, grain=5, edge=30), p)
        if kind == "receiver_shell" and nx * (x - 8) + ny * (y - (Y(192) + RAIL_Y0) / 2) < 0:
            return (*lg.add(lg.DARK, 4), 255)                 # de binnenkant van de behuizing, door de uitwerpopening
        if kind == "receiver" and abs(nz) > 0.9 and PORT_Z0 - 0.05 < z < PORT_Z1 + 0.05:
            return (*lg.add(lg.DARK, 8), 255)
        if abs(nx) > 0.9 and abs(y - Y(176)) < 0.06 and Z(1160) < z < Z(1280) and int((z - Z(1160)) * 9) % 2 == 0:
            col = lg.add(col, -24)                              # de gegraveerde naam van de maker, als een rij streepjes
        return (*col, 255)
    if kind == "rail":
        col = lg.lit(metal, isl, p, n, grain=4, edge=26)
        # De gleuven van de rail: om de 10 mm een donkere streep over de bovenkant en de zijkanten.
        if y > RAIL_Y0 + 0.3 and (z - Z(1095)) % 0.78 < 0.3:
            col = lg.add(col, -20)
        return (*col, 255)
    if kind in ("black", "shroud", "handle"):
        return (*lg.lit(lg.add(metal, -6 if kind != "handle" else 4), isl, p, n, grain=4, edge=28), 255)
    if kind == "rubber":
        col = lg.add((27, 27, 29), (lg.noise(p, 20, 4) - 0.5) * 6 + 12 * lg.edge_amount(isl, n))
        if abs(nx) > 0.9 and int((y - Y(362)) * 3.2) % 2 == 0:
            col = lg.add(col, 7)                               # de ribbels in het rubber
        return (*col, 255)
    if kind == "sight":
        return (*lg.add((30, 31, 35), (lg.noise(p, 12, 9) - 0.5) * 8 + 14 * lg.edge_amount(isl, n)), 255)
    if kind == "barrel":
        col = worn(lg.lit(lg.add(metal, 2), isl, p, n, grain=5, edge=26), p)
        base = np.array(isl.data["base"])
        along = float(base[2] - z)
        r = math.hypot(x - base[0], y - base[1])
        brake_from = float(base[2] - Z(314))
        if along > brake_from and r > P.size(15):
            # De gleuven van de mondingsrem: drie donkere sleuven links en rechts.
            if abs(nx) > 0.5 and (along - brake_from) % 1.25 > 0.55 and abs(y - BORE_Y) < 0.55:
                col = lg.DARK
        elif along > 6.0 and r > P.size(12):
            # De groeven in de loop (hij is gecanneleerd): donkere strepen langs de loop.
            angle = math.atan2(y - base[1], x - base[0])
            if abs(math.sin(angle * 3)) > 0.93:
                col = lg.add(col, -16)
        return (*lg.bore_dark(col, isl, p, n), 255)
    if kind == "bolt":
        return (*lg.add(lg.BRIGHT[skin], -8 + (lg.noise(p, 16, 3) - 0.5) * 9), 255)
    if kind == "coil":
        return (*lg.coil(lg.BRIGHT[skin], isl, p), 255)
    if kind == "rod":
        return (*lg.add((42, 42, 45), (lg.noise(p) - 0.5) * 4), 255)
    if kind == "striker":
        return (*lg.add(lg.BRIGHT[skin], (lg.noise(p) - 0.5) * 6), 255)
    if kind == "trigger":
        return (*lg.lit(lg.add(lg.BRIGHT[skin], -40), isl, p, n, grain=4, edge=24), 255)
    if kind == "sling":
        return (*lg.add((70, 78, 52), (lg.noise(p, 16, 5) - 0.5) * 10), 255)
    if kind == "pin":
        base = np.array(isl.data["base"])
        r = math.hypot(y - base[1], z - base[2])
        col = (58, 60, 66) if r < 0.13 else (34, 35, 39)
        return (*lg.add(col, (lg.noise(p) - 0.5) * 6), 255)
    return (255, 0, 255, 255)


def mag_painter(skin, isl, p, n, tx, ty):
    kind = isl.kind
    x, y, z = (float(c) for c in p)
    nx, ny, nz = (float(c) for c in n)
    grain = lg.noise(p) - 0.5
    if kind == "mag_body":
        col = worn(lg.add((96, 98, 103), grain * 7 + 24 * lg.edge_amount(isl, n)), p, 18)
        if abs(nx) > 0.9 and y < Y(272):
            # Twee geperste ribben in de zijkant.
            for px in (1257, 1315):
                d = abs(z - Z(px))
                if d < 0.09:
                    col = lg.add(col, -34)
                elif d < 0.17:
                    col = lg.add(col, 14)
        if ny > 0.9 and y > MAG_TOP - 0.02:
            col = (26, 26, 28) if isl.data.get("loaded") else (60, 60, 64)
        return (*col, 255)
    if kind == "mag_lip":
        return (*lg.add((104, 105, 110), grain * 6 + 18 * lg.edge_amount(isl, n)), 255)
    if kind == "follower":
        return (*lg.add((66, 66, 70), grain * 4), 255)
    if kind in ("brass", "copper"):
        return (*lg.round_paint(isl, p, n, kind, grain), 255)
    return (255, 0, 255, 255)


# ---------------------------------------------------------------- Het hele wapen

ID = "awp"
SKINS = ("steel", "titanium")
# Waar je rechterhand de greep vasthoudt (derde persoon), en hoe groot het wapen in je hand is.
GRIP = (8, Y(322), Z(1505))
HAND_SCALE = 0.3
MAG_FOOT = (8, Y(316), Z(1276))
MAG_LONG_FOOT = (8, MAG_FOOT[1] - MAG_LONG_DROP, MAG_FOOT[2])


def build():
    parts = {"frame": frame(), "slide": slide(), "barrel": barrel(), "springs": springs(), "striker": striker(),
             "trigger": trigger(), "sling": sling()}
    mags = {"magazine": magazine(True), "magazine_empty": magazine(False),
            "magazine_extended": magazine(True, True), "magazine_extended_empty": magazine(False, True)}
    return lg.Gun(ID, parts, mags, SKINS, painter, mag_painter, whole=("frame", "slide", "barrel", "trigger"))
