"""
De AK-47 als echt 3D-model (hoofdstuk 103, jouw wens: "meer guns, de models zijn ook zoals de Glock").

Dit is de klassieke AK-47 met houten kolf, greep en handbeschermer. De omtrek komt van een foto van een echte AK-47 (de
linkerkant, van Wikimedia Commons: "AK-47 type II noBG", 2,7 graden rechtgelegd); de punten hieronder staan in pixels van
die foto en zijn opgemeten met tools/trace_outline.py. Op die foto hangt de voorkant van het wapen wat omlaag; het model
zet hem recht (zie DROOP), zodat de korrel in de keep van het achterste vizier staat. Het model staat op de schaal van de Glock (1 pixel = 12,8 mm): de
AK-47 is 87 cm lang, dus 68 pixels.

Wat je op de foto niet ziet (de rechterkant) komt uit de maten van het echte wapen: de uitwerpopening in de kap, de
grendeldrager met zijn spanhendel (die steekt rechts uit en beweegt bij elk schot mee), en de lange keuzeschakelaar.

De zes onderdelen (dezelfde rollen als bij de Glock, met de namen van een AK):
- frame   = Receiver: de stalen behuizing met de kap, het achterste vizier, de gasbuis en het gasblok, de korreltoren,
            en al het hout (de kolf, de greep, de handbeschermer);
- slide   = Bolt Carrier: de grendeldrager met de zuigerstang en de spanhendel rechts;
- barrel  = Barrel; springs = Recoil Spring; striker = Firing Pin; trigger = Trigger Group (de trekker).

Assen zoals altijd: de loop naar -z, de kolf naar +z, rechts is +x, midden x = 8. De trekker staat op z = 8, y = 9.
"""
import math

import numpy as np

import ammo_models as ammo
import glock19_model as glock
import gunmesh as gm
import longgun as lg

# ---------------------------------------------------------------- Van de foto naar het model

# De AK-47 is 870 mm lang; op de (rechtgelegde) foto is dat 1758 pixels, van de monding tot de kolfplaat. De foto toont
# de linkerkant: de loop wijst naar links.
P = lg.Photo(870.0 / 1758.0, 1180, 8.0, 368, 9.0, muzzle_right=False)
Z, Y, zy = P.Z, P.Y, P.zy
D = 8

# Op de foto hangt de voorkant (de loop, de gasbuis met de bovenste handbeschermer, het gasblok, de korreltoren en de
# poetsstok) 2,7 graden omlaag tegenover de behuizing; de foto is rechtgelegd op de behuizing. Een echte AK is recht: de
# loop, de vizierlijn en de kap lopen evenwijdig, anders kun je niet over het vizier richten. Daarom tillen we elk punt
# van de voorkant terug op: meer naarmate het verder voor het vizierblok ligt (waar de loop de behuizing in gaat).
DROOP = math.tan(math.radians(2.7))
PIVOT = 742


def fy(px, py):
    """De hoogte (foto-pixels) van een punt van de voorkant, rechtgezet."""
    return py - max(0.0, PIVOT - px) * DROOP


def front(points):
    """Een omtrek van de voorkant (foto-pixels), rechtgezet, als (z, y)-punten van het model."""
    return zy([(px, fy(px, py)) for px, py in points])


BORE_Y = Y(293.5)                # de as van de loop (rechtgezet; op de foto 318 bij de monding, 307 bij de handbeschermer)
# De vizierlijn: de punt van de korrel, en de keep in het achterste vizier. Een paar millimeter hoger dan op de foto, zodat
# je bij het richten over de kap heen kijkt en het vizier er duidelijk boven uitsteekt.
SIGHT_Y = Y(190)
REC_Y0, REC_Y1 = Y(347), Y(262)  # de stalen behuizing: onderkant, en waar de kap erop ligt
COVER_Y = Y(204)                 # de bovenkant van de kap
COVER_Z0, COVER_Z1 = Z(870), Z(1292)
PORT_Z0, PORT_Z1 = Z(928), Z(1068)   # de uitwerpopening (rechts), voor en achter
GAS_Y = Y(244.5)                 # de as van de gasbuis (rechtgezet)
CARRIER_Y = Y(246)               # de as van de grendeldrager onder de kap
MAG_TOP = Y(326)                 # zo ver steekt het magazijn in de behuizing

REC_W = 1.28       # de behuizing is 33 mm breed
COVER_W = 1.2
SHELL = 0.14

# De omtrekken (foto-pixels), opgemeten met tools/trace_outline.py.
RECEIVER = [(830, 262), (1365, 262), (1365, 347), (1354, 346), (1345, 342), (1338, 349), (935, 349), (934, 345), (846, 346),
            (837, 345), (834, 342), (830, 345)]
COVER_REAR = [(1288, 204), (1300, 204), (1311, 209), (1328, 229), (1350, 238), (1353, 255), (1353, 263), (1288, 263)]
SIGHT_BLOCK = [(742, 214), (744, 207), (748, 203), (875, 203), (882, 215), (882, 272), (742, 272)]
SIGHT_LEAF = [(762, 203), (775, 199), (841, 198), (850, 196), (868, 197), (876, 201), (876, 205), (762, 205)]
# De bovenste handbeschermer zit op de gasbuis en hangt op de foto mee omlaag; deze punten zijn al rechtgezet. Tussen
# hem en de onderste handbeschermer blijft een smalle spleet, waar je de loop ziet.
UPPER_HANDGUARD = [(746, 207), (746, 272), (509, 272), (509, 223), (511, 223), (514, 217), (518, 214.5), (539, 212.5),
                   (709, 208.5), (730, 209.5), (735, 212.5), (741, 214)]
LOWER_HANDGUARD = [(509, 279), (836, 279), (836, 344), (831, 345), (822, 345), (818, 355), (808, 360), (794, 359), (756, 349),
                   (719, 348), (670, 350), (554, 346), (532, 343), (520, 345), (512, 337), (509, 338)]
GAS_BLOCK = [(390, 242), (394, 242), (394, 341), (365, 342), (360, 347), (348, 347), (345, 343), (293, 345), (293, 301),
             (302, 295), (311, 293), (320, 288), (331, 277), (337, 267), (341, 256), (352, 244)]
SIGHT_TOWER = [(116, 242), (148, 242), (177, 292), (178, 304), (181, 306), (181, 350), (150, 351), (147, 353), (128, 353),
               (123, 348), (121, 336), (119, 338), (108, 338), (104, 335), (104, 310), (109, 309), (110, 302), (117, 301)]
SIGHT_EARS = [(116, 242), (113, 231), (114, 227), (117, 224), (122, 221), (142, 221), (149, 225), (152, 230), (151, 238),
              (148, 242)]
STOCK = [(1361, 261), (1515, 280), (1527, 278), (1547, 267), (1562, 263), (1810, 293), (1840, 299), (1845, 298), (1851, 305),
         (1852, 317), (1848, 328), (1833, 425), (1827, 501), (1823, 512), (1814, 517), (1798, 514), (1679, 460), (1569, 418),
         (1508, 392), (1393, 354), (1388, 350), (1352, 345), (1352, 264)]
PISTOL_GRIP = [(1208, 349), (1339, 350), (1340, 357), (1338, 369), (1332, 389), (1333, 402), (1356, 456), (1371, 485), (1378, 504),
        (1377, 519), (1368, 535), (1360, 543), (1347, 550), (1333, 554), (1305, 553), (1298, 549), (1295, 545), (1291, 523),
        (1283, 502), (1243, 420), (1223, 396), (1217, 404), (1208, 392)]
# De trekkerbeugel als een C: langs het gat van voor naar achter, en buitenom terug.
GUARD = [(1108, 352), (1113, 352), (1113, 396), (1115, 404), (1208, 404), (1216, 394), (1223, 396), (1211, 411), (1199, 416),
         (1137, 416), (1117, 413), (1110, 409), (1108, 400)]
MAG_CATCH = [(1072, 353), (1073, 391), (1076, 397), (1083, 402), (1088, 421), (1090, 425), (1093, 425), (1090, 402), (1092, 400),
             (1099, 399), (1103, 402), (1108, 402), (1108, 352)]
TRIGGER = [(1196, 349), (1210, 349), (1207, 362), (1196, 378), (1180, 390), (1162, 396), (1156, 392), (1172, 384), (1186, 371),
           (1193, 358)]
# De keuzeschakelaar zit rechts: een lange hendel met zijn draaipunt achteraan. Hier in de stand "semi" (omlaag).
SELECTOR = [(1086, 312), (1096, 305), (1280, 291), (1298, 295), (1301, 306), (1284, 311), (1100, 327), (1089, 323)]
# Het magazijn: het stuk dat je ziet, en bovenaan het stuk dat in de behuizing zit.
MAG = [(938, 326), (1064, 326), (1063, 351), (1061, 371), (1056, 376), (1045, 426), (1028, 476), (1006, 524), (985, 560),
       (953, 604), (931, 629), (899, 660), (865, 686), (855, 696), (850, 697), (841, 689), (799, 631), (798, 623), (783, 600),
       (819, 573), (855, 537), (888, 492), (908, 457), (930, 400), (939, 356)]
MAG_ZY = zy(MAG)
# Het lange magazijn (de extended mag, hoofdstuk 103 stap 6b): 40 patronen, zoals dat van de RPK: 6 pixels (77 mm) langer
# langs de bocht. De voet draait om het middelpunt van de bocht; in de twee randen komt een stuk boog bij.
MAG_LONG_ZY, MAG_LONG_TURN = lg.extend_curved(MAG_ZY, [19, 20, 21, 22, 23], [4, 5, 6, 7, 8, 9, 10, 11, 12], (9, 20), 6.0)


def cover_section():
    """De doorsnede van de kap: recht aan de zijkanten, rond bovenaan."""
    y0, y1 = REC_Y1 - 0.05, COVER_Y
    return [(8 - COVER_W, y0), (8 + COVER_W, y0), (8 + COVER_W, y1 - 0.8), (8 + 0.95, y1 - 0.25), (8 + 0.45, y1),
            (8 - 0.45, y1), (8 - 0.95, y1 - 0.25), (8 - COVER_W, y1 - 0.8)]


# ---------------------------------------------------------------- De onderdelen

def frame():
    """
    De Receiver: de stalen behuizing met de kap (en de uitwerpopening rechts), het blok van het achterste vizier, de
    gasbuis, het gasblok en de korreltoren, de poetsstok onder de loop, de keuzeschakelaar, de trekkerbeugel en de
    magazijnvanger, en het hout: de kolf, de greep en de twee helften van de handbeschermer.
    """
    m = gm.Mesh()
    gm.prism(m, zy(RECEIVER), "x", 8 - REC_W, 8 + REC_W, "receiver", bevel=0.08, density=D)
    # De kap in drie stukken: voor de uitwerpopening, de opening zelf (hol, met een gat rechts), en erachter.
    section = cover_section()
    gm.prism(m, section, "z", COVER_Z0, PORT_Z0, "cover", bevel_lo=0.1, bevel_hi=0.0, density=D)
    ya, yb = REC_Y1 - 0.05, COVER_Y - 0.8
    gm.prism(m, lg.c_shell(section, SHELL, 1, 0.02, min(0.97, (Y(226) - ya) / (yb - ya))), "z", PORT_Z0, PORT_Z1,
             "cover_shell", caps=(False, False), density=D, smooth=30)
    gm.prism(m, section, "z", PORT_Z1, COVER_Z1, "cover", density=D)
    gm.prism(m, zy(COVER_REAR), "x", 8 - COVER_W, 8 + COVER_W, "cover", bevel=0.3, density=D, smooth=50)
    # Het blok van het achterste vizier, met het blad erop en achteraan het plaatje met de keep: twee paaltjes met een
    # gleuf ertussen. De bodem van de gleuf ligt net onder de vizierlijn, zodat je de korrel erin ziet staan.
    gm.prism(m, zy(SIGHT_BLOCK), "x", 8 - REC_W, 8 + REC_W, "receiver", bevel=0.08, density=D)
    gm.prism(m, zy(SIGHT_LEAF), "x", 8 - 0.4, 8 + 0.4, "sight", bevel=0.04, density=16)
    notch_z0, notch_z1 = Z(872), Z(880)
    for x0, x1 in ((7.25, 7.8), (8.2, 8.75)):
        gm.box(m, (x0, Y(205), notch_z0), (x1, SIGHT_Y + 0.3, notch_z1), "sight", bevel=0.03, density=16)
    gm.box(m, (7.76, Y(205), notch_z0), (8.24, SIGHT_Y - 0.1, notch_z1), "sight", bevel=0.0, density=16)
    # Het hout: de handbeschermer (onder en boven), de greep en de kolf.
    gm.prism(m, zy(LOWER_HANDGUARD), "x", 8 - 1.45, 8 + 1.45, "wood", bevel=0.4, density=D, smooth=50)
    gm.prism(m, zy(UPPER_HANDGUARD), "x", 8 - 1.15, 8 + 1.15, "wood", bevel=0.45, density=D, smooth=50)
    gm.prism(m, zy(PISTOL_GRIP), "x", 8 - 1.05, 8 + 1.05, "wood_grip", bevel=0.38, density=D, smooth=50)
    gm.prism(m, zy(STOCK), "x", 8 - 1.25, 8 + 1.25, "stock", bevel=0.45, density=D, smooth=50)
    # De stalen band voor de handbeschermer (om de loop).
    gm.box(m, (8 - 1.5, Y(337), Z(484)), (8 + 1.5, Y(272), Z(512)), "steel_part", bevel=0.08, density=D)
    # De gasbuis (met zijn ontluchtingsgaatjes), het gasblok en de korreltoren met zijn twee oren en de korrel ertussen:
    # de voorkant, rechtgezet (zie front). De punt van de korrel staat op de vizierlijn, net onder de toppen van de oren.
    gm.cylinder(m, (8, GAS_Y, Z(346)), (8, GAS_Y, Z(512)), P.size(18), 12, "gas_tube", density=D,
                data={"base": (8, GAS_Y, Z(346)), "axis": (0, 0, 1)})
    gm.prism(m, front(GAS_BLOCK), "x", 8 - 0.78, 8 + 0.78, "steel_part", bevel=0.12, density=D, smooth=45)
    gm.prism(m, front(SIGHT_TOWER), "x", 8 - 0.62, 8 + 0.62, "steel_part", bevel=0.1, density=16, smooth=45)
    # De oren zijn wat hoger dan op de foto (1,45 keer), zodat hun toppen boven de punt van de korrel blijven.
    ears = [(px, 242 - (242 - py) * 1.45) for px, py in SIGHT_EARS]
    for x0, x1 in ((8 - 0.62, 8 - 0.3), (8 + 0.3, 8 + 0.62)):
        gm.prism(m, front(ears), "x", x0, x1, "steel_part", bevel=0.06, density=16, smooth=45)
    gm.cylinder(m, (8, Y(fy(133, 244)), Z(133)), (8, SIGHT_Y, Z(133)), 0.13, 8, "sight", density=16)
    # De poetsstok onder de loop.
    rod_y = Y(320.3)
    gm.cylinder(m, (8, rod_y, Z(150)), (8, rod_y, Z(500)), 0.13, 8, "steel_part", density=D)
    # De trekkerbeugel, de magazijnvanger en (rechts) de keuzeschakelaar.
    gm.prism(m, zy(GUARD), "x", 8 - 0.45, 8 + 0.45, "steel_part", bevel=0.06, density=16, smooth=50)
    gm.prism(m, zy(MAG_CATCH), "x", 8 - 0.4, 8 + 0.4, "lever", bevel=0.05, density=16)
    gm.prism(m, zy(SELECTOR), "x", 8 + REC_W - 0.02, 8 + REC_W + 0.13, "lever", bevel=0.04, density=16)
    # De klinknagels en pennen.
    for px, py, r in ((1073, 327, 0.2), (1103, 318, 0.22), (1183, 330, 0.2), (1230, 286, 0.14), (1349, 290, 0.12)):
        lg.pin(m, Z(px), Y(py), 8 - REC_W, 8 + REC_W, r)
    return m


def slide():
    """
    De Bolt Carrier: de grendeldrager onder de kap (je ziet hem door de uitwerpopening), de zuigerstang die naar voren de
    gasbuis in loopt, en de spanhendel die rechts uitsteekt. Bij een AK beweegt de hendel bij elk schot mee.
    """
    m = gm.Mesh()
    gm.prism(m, lg.rounded_rect(8 - 0.98, REC_Y1 - 0.02, 8 + 0.98, COVER_Y - 0.25, 0.3), "z", PORT_Z0 + 0.1, PORT_Z1 + 1.4,
             "bolt", density=D, data={"base": (8, CARRIER_Y, PORT_Z0), "axis": (0, 0, 1)})
    gm.cylinder(m, (8, GAS_Y, Z(530)), (8, GAS_Y, PORT_Z0 + 0.1), 0.26, 10, "bolt", density=D)
    # De spanhendel: een gebogen steel naar rechts met een knop.
    hz, hy = Z(1004), Y(252)
    gm.cylinder(m, (8 + 0.9, hy, hz), (8 + 2.05, hy + 0.05, hz - 0.1), 0.2, 8, "handle", density=16)
    gm.lathe(m, (8 + 1.95, hy + 0.05, hz - 0.1), (1, 0.05, -0.1),
             [(0, 0), (0, 0.24), (0.12, 0.3), (0.5, 0.3), (0.62, 0.2), (0.62, 0)], 10, "handle", density=16)
    return m


def barrel():
    """De loop: van de kamer in het vizierblok tot de monding met zijn moer."""
    m = gm.Mesh()
    base_z = Z(835)

    def h(px):
        return base_z - Z(px)
    profile = [(0.0, 0.8), (h(512) - 0.05, 0.8), (h(512), 0.57), (h(121), 0.57), (h(121), 0.64), (h(98), 0.64), (h(92), 0.5)]
    lg.barrel(m, (8, BORE_Y, base_z), (0, 0, -1), profile, lg.mm(3.81), density=D)
    return m


def springs():
    """De Recoil Spring: de sluitveer met zijn geleidestang, achter de grendeldrager onder de kap."""
    m = gm.Mesh()
    lg.spring(m, (8, CARRIER_Y, PORT_Z1 + 1.45), (8, CARRIER_Y, Z(1285)), 0.3, rod=0.09)
    return m


def striker():
    """De Firing Pin: de slagpin met zijn veertje, in de grendel."""
    m = gm.Mesh()
    z0 = PORT_Z0 + 0.4
    gm.lathe(m, (8, CARRIER_Y, z0), (0, 0, 1), [(0, 0), (0, 0.03), (0.3, 0.05), (0.4, 0.1), (4.6, 0.1), (4.6, 0)], 8,
             "striker", density=16)
    lg.spring(m, (8, CARRIER_Y, z0 + 1.2), (8, CARRIER_Y, z0 + 3.6), 0.16)
    gm.lathe(m, (8, CARRIER_Y, z0 + 4.4), (0, 0, 1), [(0, 0), (0, 0.2), (0.4, 0.2), (0.4, 0)], 10, "striker", density=16)
    return m


def trigger():
    """De trekker."""
    m = gm.Mesh()
    gm.prism(m, zy(TRIGGER), "x", 8 - 0.22, 8 + 0.22, "trigger", bevel=0.06, density=16, smooth=50)
    return m


def sling():
    """De Gun Sling aan de AK-47: een leren riem langs de linkerkant, van het gasblok in een boog naar de kolf."""
    m = gm.Mesh()
    start, rear = (Z(330), Y(fy(330, 338))), (Z(1470), Y(318))
    low = Y(470)
    top, bottom = [], []
    steps = 16
    for i in range(steps + 1):
        t = i / steps
        z = start[0] + (rear[0] - start[0]) * t
        y = start[1] + (rear[1] - start[1]) * t - (start[1] * (1 - t) + rear[1] * t - low) * 4 * t * (1 - t)
        top.append((z, y + 0.28))
        bottom.append((z, y - 0.28))
    gm.prism(m, top + bottom[::-1], "x", 8 - 1.72, 8 - 1.58, "sling", density=D, smooth=60)
    return m


def magazine(loaded=True, extended=False):
    """
    Het gebogen stalen magazijn van 30 patronen ("de banaan"): het lijf met zijn ribben, de bodemplaat, de lipjes
    bovenaan, en bovenin een patroon 7.62x39 (of, leeg, de opvolger). {extended}: het lange magazijn van 40 patronen.
    """
    m = gm.Mesh()
    outline = MAG_LONG_ZY if extended else MAG_ZY
    gm.prism(m, outline, "x", 8 - 1.05, 8 + 1.05, "mag_body", bevel=0.12, density=D, smooth=50,
             data={"loaded": loaded, "outline": outline})
    z0, z1 = Z(945), Z(1056)
    for side in (-1, 1):
        lip = [(8 + side * 1.02, MAG_TOP - 0.1), (8 + side * 0.84, MAG_TOP - 0.1), (8 + side * 0.84, MAG_TOP + 0.1),
               (8 + side * 0.56, MAG_TOP + 0.18), (8 + side * 0.56, MAG_TOP + 0.26), (8 + side * 1.02, MAG_TOP + 0.18)]
        gm.prism(m, lip, "z", z0, z1, "mag_lip", bevel=0.02, density=16)
    if loaded:
        m.add(ammo.round_mesh("762", (8.0, MAG_TOP + 0.36, z1 - 0.1), (0, 0, -1)))
    else:
        gm.box(m, (8 - 0.76, MAG_TOP - 0.06, z0 + 0.15), (8 + 0.76, MAG_TOP + 0.1, z1 - 0.15), "follower", bevel=0.04,
               density=16)
    return m


# ---------------------------------------------------------------- Schilderen

# Een AK is gebruikt staal: grijzer en lichter dan het zwart van een Glock, met blanke plekken waar het sleet.
STEEL = {"steel": (88, 92, 100), "titanium": (142, 149, 158)}
WOOD = (150, 80, 40)
WOOD_DARK = (92, 44, 20)


def worn(col, p, amount=14):
    """Slijtplekken: grote, zachte vlekken die lichter of donkerder zijn."""
    v = lg.noise((p[0] * 0.2, p[1] * 0.45, p[2] * 0.35), 4.0, 21)
    return lg.add(col, (v - 0.5) * amount)


def painter(skin, isl, p, n, tx, ty):
    kind = isl.kind
    x, y, z = (float(c) for c in p)
    nx, ny, nz = (float(c) for c in n)
    metal = STEEL[skin]
    if kind in ("receiver", "cover", "cover_shell", "steel_part", "gas_tube", "sight"):
        col = worn(lg.lit(metal, isl, p, n, grain=6, edge=34), p)
        side = abs(nx) > 0.9
        if kind == "cover_shell" and nx * (x - 8) + ny * (y - (REC_Y1 + COVER_Y) / 2) < 0:
            return (*lg.add(lg.DARK, 4), 255)                 # de binnenkant van de kap, door de uitwerpopening
        if kind == "cover" and abs(nz) > 0.9 and PORT_Z0 - 0.05 < z < PORT_Z1 + 0.05:
            return (*lg.add(lg.DARK, 8), 255)
        if kind in ("cover", "cover_shell") and side and (abs(y - Y(226)) < 0.05 or abs(y - Y(246)) < 0.05):
            col = lg.add(col, 18)                              # de twee lange richels in de kap
        if kind == "receiver" and side:
            # De uitgefreesde gleuf boven het magazijn: donkerder, met een lichte rand onderaan.
            if Z(842) < z < Z(1004) and Y(336) < y < Y(302):
                col = lg.add(col, -20)
                if y < Y(332):
                    col = lg.add(col, 30)
        if kind == "receiver" and ny < -0.8 and abs(x - 8) < 1.0 and Z(938) < z < Z(1064):
            col = lg.DARK                                      # de opening waar het magazijn in gaat
        if kind == "gas_tube" and abs(nx) > 0.4 and abs(y - GAS_Y) < 0.2:
            # De ontluchtingsgaatjes aan beide kanten van de gasbuis.
            for px in (428, 446, 464, 482):
                if abs(z - Z(px)) < 0.14:
                    col = lg.DARK
        if kind == "sight":
            # Het blad, de keep en de korrel: bijna zwart, zodat je ze bij het richten ziet tegen de grijze kap.
            col = lg.add((34, 35, 39), (lg.noise(p, 12, 9) - 0.5) * 8 + 14 * lg.edge_amount(isl, n))
        return (*col, 255)
    if kind in ("wood", "wood_grip", "stock"):
        if kind == "stock" and z < Z(1396):
            return (*worn(lg.lit(metal, isl, p, n, grain=6, edge=34), p), 255)   # de stalen staart van de behuizing
        if kind == "stock" and nz > 0.8 and z > Z(1820):
            return (*lg.lit(lg.add(metal, -22), isl, p, n, grain=5, edge=30), 255)  # de stalen kolfplaat
        col = lg.wood(isl, p, n, base=WOOD, dark=WOOD_DARK, along="y" if kind == "wood_grip" else "z")
        if kind == "wood_grip":
            col = lg.add(col, -8)
        return (*col, 255)
    if kind == "sling":
        return (*lg.add((124, 82, 48), (lg.noise(p, 16, 5) - 0.5) * 10), 255)
    if kind in ("lever", "trigger", "handle"):
        return (*worn(lg.lit(lg.add(metal, -14), isl, p, n, grain=5, edge=30), p, 10), 255)
    if kind == "barrel":
        col = worn(lg.lit(lg.add(metal, 4), isl, p, n, grain=5, edge=28), p)
        return (*lg.bore_dark(col, isl, p, n), 255)
    if kind == "bolt":
        return (*lg.add(lg.BRIGHT[skin], -14 + (lg.noise(p, 16, 3) - 0.5) * 9), 255)
    if kind == "coil":
        return (*lg.coil(lg.BRIGHT[skin], isl, p), 255)
    if kind == "rod":
        return (*lg.add((42, 42, 45), (lg.noise(p) - 0.5) * 4), 255)
    if kind == "striker":
        return (*lg.add(lg.BRIGHT[skin], (lg.noise(p) - 0.5) * 6), 255)
    if kind == "pin":
        base = np.array(isl.data["base"])
        r = math.hypot(y - base[1], z - base[2])
        col = (176, 180, 186) if r < 0.12 else (110, 113, 120)
        return (*lg.add(col, (lg.noise(p) - 0.5) * 6), 255)
    return (255, 0, 255, 255)


def mag_painter(skin, isl, p, n, tx, ty):
    kind = isl.kind
    x, y, z = (float(c) for c in p)
    nx, ny, nz = (float(c) for c in n)
    grain = lg.noise(p) - 0.5
    if kind == "mag_body":
        col = worn(lg.add((70, 66, 64), grain * 6 + 26 * lg.edge_amount(isl, n)), p, 16)
        if abs(nx) > 0.9:
            # Drie geperste ribben die de bocht van het magazijn volgen.
            d = gm.distance_to_outline((z, y), isl.data.get("outline", MAG_ZY))
            for at in (0.42, 0.95, 1.5):
                if abs(d - at) < 0.07:
                    col = lg.add(col, 20)
                elif 0.07 <= d - at < 0.14:
                    col = lg.add(col, -10)
        if ny > 0.9 and y > MAG_TOP - 0.02:
            col = (26, 26, 28) if isl.data.get("loaded") else (60, 60, 64)
        return (*col, 255)
    if kind == "mag_lip":
        return (*lg.add((92, 92, 96), grain * 6 + 18 * lg.edge_amount(isl, n)), 255)
    if kind == "follower":
        return (*lg.add((66, 66, 70), grain * 4), 255)
    if kind in ("brass", "copper"):
        return (*lg.round_paint(isl, p, n, kind, grain), 255)
    return (255, 0, 255, 255)


# ---------------------------------------------------------------- Het hele wapen

ID = "ak_47"
SKINS = ("steel", "titanium")
# Waar je rechterhand de greep vasthoudt (derde persoon), en hoe groot het wapen in je hand is.
GRIP = (8, Y(450), Z(1300))
HAND_SCALE = lg.GUN_SCALE  # hoofdstuk 105: dezelfde maat voor elk vuurwapen (zie longgun.GUN_SCALE)
MAG_FOOT = (8, Y(648), Z(818))
MAG_LONG_FOOT = (8, *reversed(MAG_LONG_TURN([(MAG_FOOT[2], MAG_FOOT[1])])[0]))


def build():
    parts = {"frame": frame(), "slide": slide(), "barrel": barrel(), "springs": springs(), "striker": striker(),
             "trigger": trigger(), "sling": sling()}
    mags = {"magazine": magazine(True), "magazine_empty": magazine(False),
            "magazine_extended": magazine(True, True), "magazine_extended_empty": magazine(False, True)}
    return lg.Gun(ID, parts, mags, SKINS, painter, mag_painter, whole=("frame", "slide", "barrel", "trigger"))
