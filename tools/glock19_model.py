"""
De Glock 19 als echt 3D-model (hoofdstuk 74, jouw wens: "kijk eens hoe dat bij TACZ is, die stijl van realisme ... kopieer
niet letterlijk maar haal er inspiratie", en "het glock mag ... in dezelfde stijl als TACZ mags").

Wat ik van TACZ heb afgekeken (niets gekopieerd): hun Glock is opgebouwd uit honderden kleine vlakjes met afgeschuinde
randen, een texture met vlakke donkergrijze tinten en lichtere randjes (8 texels per pixel), en echte verhoudingen. Dit
model doet hetzelfde, maar is helemaal zelf gemaakt:

- de omtrek komt van een foto van een echte Glock 19 Gen 3 (de rechterkant, van Wikimedia Commons: "Glock 19 (transparent
  background)"). Elk punt hieronder staat in pixels van die foto (x naar rechts = naar de loop, y naar beneden); Z() en
  Y() rekenen ze om naar het model. 1 model-pixel is 48,8 foto-pixels, ongeveer 12,6 mm van het echte pistool;
- de breedtes (de slede 25,5 mm, de greep 30 mm...) komen uit de maten van Glock;
- de greep helt 22 graden naar achteren (zoals op de foto). In het oude model helde hij per ongeluk naar voren;
- de texture schildert het script zelf (16 texels per pixel), met tekst die je kunt lezen: GLOCK 19 AUSTRIA op de
  slede, MADE IN AUSTRIA op het frame, 9x19 op de loop en de getallen achterop het magazijn.

De onderdelen zijn losse modellen (models/firearm/glock_19/<onderdeel>.obj), want in je hand beweegt de slede, de trekker
en het magazijn. Elk onderdeel heeft dezelfde plek op drie plaatjes (textures/item/glock_19/polymer.png, steel.png en
titanium.png); het json-bestandje per materiaal kiest het plaatje.

Assen zoals altijd in deze mod: de loop wijst naar -z, de greep naar +z, rechts is +x (de uitwerpopening), midden x = 8.
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import gunmesh as gm
from gunmesh import vec

FONTS = Path("C:/Windows/Fonts")

# ---------------------------------------------------------------- Van de foto naar het model

S = 0.0205  # model-pixels per foto-pixel


def Z(px):
    """Een x van de foto (de loop rechts) -> z in het model (de loop naar -z)."""
    return round(0.70 + (912 - px) * S, 4)


def Y(py):
    """Een y van de foto (naar beneden) -> y in het model (omhoog). De bovenkant van de slede (foto 72) = 13."""
    return round(13.0 - (py - 72) * S, 4)


def zy(points):
    return [(Z(x), Y(y)) for x, y in points]


GRIP_ANGLE = 22.5
TAN = math.tan(math.radians(GRIP_ANGLE))

# ---------------------------------------------------------------- De maten

SLIDE_X0, SLIDE_X1 = 6.985, 9.015          # 25,5 mm breed
SLIDE_Y0, SLIDE_Y1 = Y(154), 13.0          # onderkant en bovenkant
SLIDE_Z0, SLIDE_Z1 = Z(912), Z(250)        # voorkant en achterkant (17,4 cm)
PORT_Z0, PORT_Z1 = Z(652), Z(520)          # de uitwerpopening, voor en achter (de achterkant = de stootbodem)
PORT_X = 7.25                              # de opening loopt bovenop van hier tot de rechterkant
PORT_Y = 12.25                             # de rechterwand onder de opening
BORE_Y = Y(114)                            # de as van de loop
ROD_Y = Y(146)                             # de as van de terugslagveer
CHAMFER = 0.15                             # de afgeschuinde bovenranden van de slede
SERR0, SERR1 = 10.9, 13.65                 # het stuk met de groeven achteraan
GROOVES = [Z(px) for px in (287, 306.7, 326.4, 346.1, 365.8, 385.5, 405.2)]
GROOVE_Y0, GROOVE_Y1 = Y(152), Y(80)

# Het frame in vijf stukken met hun eigen breedte (een echte Glock is boven de trekker smaller dan aan de greep, de
# stofkap met de rail nog smaller, de trekkerbeugel het smalst).
FRAME_X = (6.9, 9.1)       # het lijf boven de greep, met de beavertail
GRIP_X = (6.79, 9.21)      # de greep (30 mm)
DUST_X = (7.08, 8.92)      # de stofkap en de rail
GUARD_X = (7.4, 8.6)       # de trekkerbeugel

BODY = [(247, 154), (700, 154), (700, 214), (688, 220), (660, 223), (632, 223), (600, 224), (567, 225), (553, 229),
        (545, 240), (540, 256), (536, 276), (533, 300), (480, 302), (300, 284), (301, 256), (301, 236), (296, 220),
        (288, 208), (279, 200), (268, 195), (252, 192), (242, 188), (238, 180), (240, 169), (246, 160)]
GRIP = [(301, 240), (540, 240), (538, 256), (535, 276), (532, 298), (524, 309), (512, 316), (500, 319), (492, 324),
        (487, 333), (485, 341),
        # de voorkant met de drie vingergroeven
        (480, 347), (477, 356), (477, 366), (479, 377), (480, 386), (476, 392), (468, 396), (460, 403), (455, 412),
        (450, 421), (448, 430), (450, 441), (453, 450), (452, 456), (444, 461), (437, 467), (431, 473), (427, 480),
        (425, 488), (424, 496),
        # de onderkant (het magazijn zit ertussen) en de hiel
        (360, 492), (300, 490), (298, 511), (240, 505), (226, 501), (212, 493), (204, 484),
        # de achterkant omhoog
        (202, 472), (204, 456), (209, 441), (215, 428), (221, 414), (228, 400), (235, 388), (242, 377), (249, 365),
        (256, 353), (263, 343), (271, 331), (284, 306), (294, 281)]
GUARD = [(716, 210), (712, 218), (704, 226), (700, 234), (698, 246), (698, 290), (702, 302), (706, 312), (706, 320),
         (701, 326), (680, 329), (651, 330), (620, 332), (552, 332), (540, 329), (527, 321), (514, 316), (502, 316),
         (500, 306), (520, 306), (536, 308), (548, 311), (560, 311), (600, 309), (625, 308), (640, 309), (652, 306),
         (662, 300), (670, 292), (674, 279), (674, 264), (672, 250), (668, 239), (660, 230), (652, 212)]
DUST_TOP = [(690, 154), (911, 154), (913, 160), (913, 170), (912, 178), (911, 184), (690, 184)]
RAIL = [(690, 191), (910, 191), (910, 198), (905, 206), (897, 210), (876, 210), (874, 206), (866, 206), (864, 212),
        (826, 212), (770, 215), (747, 215), (742, 212), (727, 212), (716, 214), (690, 216)]
TRIGGER = [(553, 222), (567, 226), (566, 234), (562, 238), (558, 246), (555, 258), (557, 272), (562, 286), (566, 296),
           (566, 303), (563, 307), (557, 309), (552, 306), (548, 296), (545, 282), (544, 266), (545, 252), (548, 240),
           (551, 230)]
SLIDE_STOP = [(698, 156), (698, 161), (530, 162), (506, 168), (476, 169), (469, 163), (474, 156)]
SLIDE_LOCK = [(603, 170), (618, 168), (620, 198), (606, 200)]
PANEL = zy([(337, 282), (440, 288), (447, 303), (403, 407), (385, 477), (253, 478), (247, 472), (273, 387), (330, 290)])
PINS = [((557, 166), FRAME_X, 0.1), ((570, 193), FRAME_X, 0.1), ((325, 262), GRIP_X, 0.085)]
LOCK_RECESS = (Z(613), Y(184), 29 * S, 27 * S)   # de ovale uitsparing rond de slide lock (midden z, y, stralen)
MAG_CATCH = (Z(456), Y(282))                     # het midden van de magazijnknop

# Het magazijn: horizontaal gemeten 2,76 diep (33 mm loodrecht op zijn as), 1,75 breed (22 mm), schuin zoals de greep.
MAG_TOP = 10.9
MAG_FRONT_TOP = 7.84
MAG_DEPTH = 2.76
BASE_PLATE = [(424, 488), (426, 500), (428, 512), (427, 520), (421, 526), (396, 524), (381, 522), (300, 514),
              (296, 510), (296, 488)]


def mag_front_z(y):
    """De voorkant van het magazijn op hoogte y (het staat schuin: hoe lager, hoe verder naar achteren)."""
    return MAG_FRONT_TOP + (MAG_TOP - y) * TAN


# Een 9x19-patroon (Parabellum): rand 9,96 mm, huls 19,15 mm lang, kogel 9,01 mm, samen 29,7 mm. Profiel (afstand vanaf
# de bodem, straal) in model-pixels: de rand, de groef voor de extractor, de huls die heel licht smaller wordt.
CASE = [(0.0, 0.0), (0.0, 0.396), (0.1, 0.396), (0.12, 0.33), (0.17, 0.33), (0.2, 0.39), (1.524, 0.383)]
BULLET = [(1.524, 0.357), (1.70, 0.357), (1.88, 0.34), (2.03, 0.30), (2.17, 0.23), (2.28, 0.14), (2.34, 0.06),
          (2.364, 0.0)]
ROUND_LENGTH = 2.364


# ---------------------------------------------------------------- Tekst en logo's

def font(name, size):
    return ImageFont.truetype(str(FONTS / name), max(4, int(size)))


def crisp(im):
    """Letters scherp maken: halfdoorzichtige randjes worden heel of weg (Minecraft knipt alles onder 10 % weg)."""
    a = np.asarray(im).copy()
    a[:, :, 3] = np.where(a[:, :, 3] > 110, 255, 0)
    return Image.fromarray(a, "RGBA")


def canvas(w, h, density):
    return Image.new("RGBA", (max(1, round(w * density)), max(1, round(h * density))), (0, 0, 0, 0))


def text_fit(draw, box, text, name, colour, max_size):
    """Tekst zo groot als past in box = (x0, y0, x1, y1), gecentreerd."""
    x0, y0, x1, y1 = box
    size = max_size
    while size > 4:
        f = font(name, size)
        l, t, r, b = draw.textbbox((0, 0), text, font=f)
        if r - l <= x1 - x0 and b - t <= y1 - y0:
            draw.text(((x0 + x1 - (r - l)) / 2 - l, (y0 + y1 - (b - t)) / 2 - t), text, font=f, fill=colour)
            return
        size -= 1


def glock_logo(draw, box, colour, line):
    """Het GLOCK-logo: vette letters in een kader met afgeronde hoeken."""
    x0, y0, x1, y1 = box
    draw.rounded_rectangle(box, radius=max(1, (y1 - y0) // 5), outline=colour, width=line)
    text_fit(draw, (x0 + line * 2, y0 + line * 2, x1 - line * 2, y1 - line * 2), "GLOCK", "ariblk.ttf", colour, 200)


ENGRAVED = (150, 152, 157, 255)


def marking_slide_left(w, h, density):
    """Links op de slede, gegraveerd: [GLOCK] 19 en eronder AUSTRIA 9x19."""
    im = canvas(w, h, density)
    d = ImageDraw.Draw(im)
    W, H = im.size
    line = max(1, H // 28)
    glock_logo(d, (0, 0, int(W * 0.42), int(H * 0.5)), ENGRAVED, line)
    text_fit(d, (int(W * 0.45), 0, int(W * 0.62), int(H * 0.52)), "19", "arialbd.ttf", ENGRAVED, 200)
    text_fit(d, (0, int(H * 0.62), int(W * 0.40), H), "AUSTRIA", "arialbd.ttf", ENGRAVED, 200)
    text_fit(d, (int(W * 0.46), int(H * 0.62), int(W * 0.70), H), "9x19", "arialbd.ttf", ENGRAVED, 200)
    return crisp(im)


def marking_small_logo(w, h, density):
    im = canvas(w, h, density)
    d = ImageDraw.Draw(im)
    glock_logo(d, (0, 0, im.size[0] - 1, im.size[1] - 1), ENGRAVED, max(1, im.size[1] // 12))
    return crisp(im)


def marking_text(w, h, density, text, colour=ENGRAVED):
    im = canvas(w, h, density)
    d = ImageDraw.Draw(im)
    text_fit(d, (0, 0, im.size[0], im.size[1]), text, "arialbd.ttf", colour, 200)
    return crisp(im)


def plate(w, h, density, lines, background, ink):
    """Een plaatje met afgeronde hoeken en een of twee regels tekst (MADE IN AUSTRIA, de patenten)."""
    im = canvas(w, h, density)
    d = ImageDraw.Draw(im)
    W, H = im.size
    d.rounded_rectangle((0, 0, W - 1, H - 1), radius=H // 4, fill=background)
    rows = len(lines)
    for i, text in enumerate(lines):
        y0 = int(H * (0.1 + 0.8 * i / rows))
        y1 = int(H * (0.1 + 0.8 * (i + 1) / rows))
        text_fit(d, (int(W * 0.05), y0, int(W * 0.95), y1), text, "arial.ttf", ink, 200)
    return crisp(im)


def white_dot(w, h, density):
    im = canvas(w, h, density)
    d = ImageDraw.Draw(im)
    d.ellipse((0, 0, im.size[0] - 1, im.size[1] - 1), fill=(250, 250, 244, 255))
    return im


def white_u(w, h, density, notch_x0, notch_x1, notch_y):
    """De witte U achterop het achterste vizier, rond de keep (alles in model-pixels binnen het plaatje)."""
    im = canvas(w, h, density)
    d = ImageDraw.Draw(im)
    W, H = im.size
    k = density
    bar = int(0.065 * k)
    a, b = int(notch_x0 * k), int(notch_x1 * k)
    bottom = H - int(notch_y * k)
    d.rectangle((a - bar, 0, a - 1, H - 1), fill=(250, 250, 244, 255))
    d.rectangle((b, 0, b + bar - 1, H - 1), fill=(250, 250, 244, 255))
    d.rectangle((a - bar, bottom, b + bar - 1, H - 1), fill=(250, 250, 244, 255))
    return im


def witness_holes(w, h, density):
    """Achterop het magazijn: een rij gaatjes met getallen (hoeveel patronen er nog in zitten)."""
    im = canvas(w, h, density)
    d = ImageDraw.Draw(im)
    W, H = im.size
    marks = ["15", "12", "10", "8", "5"]
    for i, text in enumerate(marks):
        cy = int(H * (0.08 + 0.84 * i / (len(marks) - 1)))
        r = int(0.075 * density)
        cx = int(W * 0.30)
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(14, 14, 16, 255))
        text_fit(d, (int(W * 0.48), cy - int(0.11 * density), W, cy + int(0.11 * density)), text, "arialbd.ttf",
                 (196, 196, 190, 255), 200)
    return crisp(im)


# ---------------------------------------------------------------- De onderdelen

def slide():
    """
    De slede: vooraan met een afgeronde neus, dan de uitwerpopening (rechts en bovenop), dan zeven groeven achteraan aan
    beide kanten. Korrel met een wit stipje, keep met een witte U, de extractor rechts achter de opening.
    """
    m = gm.Mesh()
    section = gm.chamfered_rect(SLIDE_X0, SLIDE_Y0, SLIDE_X1, SLIDE_Y1, c_top=CHAMFER, c_bottom=0.03)
    gm.prism(m, section, "z", SLIDE_Z0, PORT_Z0, "slide", bevel_lo=0.1, bevel_hi=0.0)
    left = [(SLIDE_X0, SLIDE_Y0 + 0.03), (SLIDE_X0 + 0.03, SLIDE_Y0), (PORT_X, SLIDE_Y0), (PORT_X, SLIDE_Y1),
            (SLIDE_X0 + CHAMFER, SLIDE_Y1), (SLIDE_X0, SLIDE_Y1 - CHAMFER)]
    gm.prism(m, left, "z", PORT_Z0, PORT_Z1, "slide", caps=(False, False))
    right = [(8.75, SLIDE_Y0), (SLIDE_X1 - 0.03, SLIDE_Y0), (SLIDE_X1, SLIDE_Y0 + 0.03), (SLIDE_X1, PORT_Y - 0.03),
             (SLIDE_X1 - 0.03, PORT_Y), (8.75, PORT_Y)]
    gm.prism(m, right, "z", PORT_Z0, PORT_Z1, "slide", caps=(False, False))
    gm.prism(m, section, "z", PORT_Z1, SERR0, "slide", caps=(True, False))
    # De groeven: een onderband, een smallere kern, een bovenband, en de ribbels tussen de groeven.
    depth = 0.07
    bottom = [(SLIDE_X0, SLIDE_Y0 + 0.03), (SLIDE_X0 + 0.03, SLIDE_Y0), (SLIDE_X1 - 0.03, SLIDE_Y0),
              (SLIDE_X1, SLIDE_Y0 + 0.03), (SLIDE_X1, GROOVE_Y0), (SLIDE_X0, GROOVE_Y0)]
    gm.prism(m, bottom, "z", SERR0, SERR1, "slide", caps=(False, False))
    gm.box(m, (SLIDE_X0 + depth, GROOVE_Y0, SERR0), (SLIDE_X1 - depth, GROOVE_Y1, SERR1), "slide_groove",
           caps=(False, False))
    top = [(SLIDE_X0, GROOVE_Y1), (SLIDE_X1, GROOVE_Y1), (SLIDE_X1, SLIDE_Y1 - CHAMFER), (SLIDE_X1 - CHAMFER, SLIDE_Y1),
           (SLIDE_X0 + CHAMFER, SLIDE_Y1), (SLIDE_X0, SLIDE_Y1 - CHAMFER)]
    gm.prism(m, top, "z", SERR0, SERR1, "slide", caps=(False, False))
    half = 0.07
    edges = [SERR0] + [g for c in sorted(GROOVES) for g in (c - half, c + half)] + [SERR1]
    for i in range(0, len(edges), 2):
        for x0, x1 in ((SLIDE_X0, SLIDE_X0 + depth), (SLIDE_X1 - depth, SLIDE_X1)):
            gm.box(m, (x0, GROOVE_Y0, edges[i]), (x1, GROOVE_Y1, edges[i + 1]), "slide")
    gm.prism(m, section, "z", SERR1, SLIDE_Z1, "slide", bevel_lo=0.0, bevel_hi=0.08, caps=(False, True))
    # Korrel (zwart polymeer) met een wit stipje achterop.
    front = [(Z(894), 12.98), (Z(872), 12.98), (Z(872), Y(58)), (Z(882), Y(58)), (Z(894), 13.1)]
    gm.prism(m, front, "x", 7.86, 8.14, "sight", bevel=0.02)
    gm.decal(m, (7.94, 13.14, Z(872)), (1, 0, 0), (0, 1, 0), 0.12, 0.12, white_dot(0.12, 0.12, 128), "dot",
             material="glow", density=128)
    # Keep: twee palen en een voetje ertussen, met een witte U achterop.
    rear = [(Z(307), 12.98), (Z(285), 12.98), (Z(285), Y(56)), (Z(299), Y(56)), (Z(307), 13.1)]
    gm.prism(m, rear, "x", 7.125, 7.865, "sight", bevel=0.02)
    gm.prism(m, rear, "x", 8.135, 8.875, "sight", bevel=0.02)
    notch = [(Z(307), 12.98), (Z(285), 12.98), (Z(285), 13.18), (Z(303), 13.18)]
    gm.prism(m, notch, "x", 7.865, 8.135, "sight", caps=(False, False))
    u_w, u_h = 0.40, Y(56) - 13.12 - 0.02
    gm.decal(m, (7.80, 13.12, Z(285)), (1, 0, 0), (0, 1, 0), u_w, u_h, white_u(u_w, u_h, 128, 0.065, 0.335, 0.06),
             "dot", material="glow", density=128)
    # De extractor rechts, net achter de opening.
    gm.box(m, (SLIDE_X1 - 0.02, 12.02, PORT_Z1 + 0.02), (SLIDE_X1 + 0.03, 12.5, Z(482)), "slide_edge", bevel=0.015)
    # Opschriften.
    gm.decal(m, (SLIDE_X0, 11.66, 3.05), (0, 0, 1), (0, 1, 0), 3.3, 0.92, marking_slide_left(3.3, 0.92, 96),
             density=96)
    gm.decal(m, (SLIDE_X1, Y(129), Z(446)), (0, 0, -1), (0, 1, 0), Z(446) - Z(464), Y(115) - Y(129),
             marking_small_logo(Z(446) - Z(464), Y(115) - Y(129), 128), density=128)
    return m


def barrel():
    """De loop: een buis met een donkere boring vooraan, het blok met de kamer (vult de uitwerpopening) en de nok."""
    m = gm.Mesh()
    profile = [(0.25, 0.0), (0.25, 0.18), (0.0, 0.18), (0.0, 0.47), (0.03, 0.52), (5.62, 0.52)]
    gm.lathe(m, (8, BORE_Y, 0.49), (0, 0, 1), profile, 16, "barrel", data={"base": (8, BORE_Y, 0.49), "axis": (0, 0, 1)})
    # Het blok met de kamer vult de uitwerpopening, met een smal donker randje rondom (zo zie je de opening).
    gm.box(m, (PORT_X + 0.004, 11.62, PORT_Z0 + 0.05), (8.93, 12.975, PORT_Z1 - 0.04), "barrel_hood", bevel=0.03)
    gm.box(m, (7.6, 10.95, 7.3), (8.4, 11.62, 8.6), "barrel", bevel=0.03)
    gm.decal(m, (8.95, 12.4, 7.8), (0, 0, -1), (0, 1, 0), 0.95, 0.38, marking_text(0.95, 0.38, 96, "9x19"), density=96)
    return m


def springs():
    """De terugslagveer: een zwarte geleidestang (je ziet het puntje vooraan onder de loop) met een stalen veer erom."""
    m = gm.Mesh()
    gm.lathe(m, (8, ROD_Y, 0.69), (0, 0, 1), [(0, 0), (0, 0.07), (0.03, 0.085), (6.0, 0.085), (6.0, 0)], 10, "rod")
    gm.cylinder(m, (8, ROD_Y, 0.95), (8, ROD_Y, 6.25), 0.17, 12, "coil", caps=False)
    gm.lathe(m, (8, ROD_Y, 6.25), (0, 0, 1), [(0, 0), (0, 0.24), (0.12, 0.24), (0.12, 0)], 12, "rod")
    return m


def striker():
    """De slagpin: het puntje, het lijf, de veer met zijn kapjes, de huls, en het pootje achteraan."""
    m = gm.Mesh()
    gm.lathe(m, (8, BORE_Y, 8.75), (0, 0, 1), [(0, 0), (0, 0.025), (0.22, 0.04), (0.3, 0.085), (4.55, 0.085), (4.55, 0)],
             10, "striker")
    gm.cylinder(m, (8, BORE_Y, 9.55), (8, BORE_Y, 12.55), 0.13, 12, "coil", caps=False)
    gm.lathe(m, (8, BORE_Y, 12.55), (0, 0, 1), [(0, 0), (0, 0.16), (0.1, 0.16), (0.1, 0)], 12, "striker")
    gm.lathe(m, (8, BORE_Y, 9.3), (0, 0, 1), [(0, 0), (0, 0.15), (0.25, 0.15), (0.25, 0)], 12, "rod")
    gm.box(m, (7.93, 11.55, 12.95), (8.07, 12.06, 13.3), "striker", bevel=0.02)
    return m


def trigger():
    """De trekker: een gebogen blad in twee helften, met het veiligheidslipje ertussen dat een tikje naar voren steekt."""
    m = gm.Mesh()
    blade = zy(TRIGGER)
    gm.prism(m, blade, "x", 7.72, 7.95, "trigger", bevel=0.04)
    gm.prism(m, blade, "x", 8.05, 8.28, "trigger", bevel=0.04)
    gm.prism(m, zy([(x + 1.8, y) for x, y in TRIGGER]), "x", 7.95, 8.05, "trigger_safety")
    return m


def mag_button_outline():
    """De magazijnknop: een rechthoek die schuin staat zoals de greep."""
    cz, cy = MAG_CATCH
    a = math.radians(GRIP_ANGLE)
    down = (math.sin(a), -math.cos(a))
    back = (math.cos(a), math.sin(a))
    hd, hb = 0.40, 0.47
    return [(cz + sd * down[0] * hd + sb * back[0] * hb, cy + sd * down[1] * hd + sb * back[1] * hb)
            for sd, sb in ((-1, -1), (1, -1), (1, 1), (-1, 1))]


def frame():
    """
    Het frame (polymeer): het lijf met de beavertail, de greep (breder, met vingergroeven), de trekkerbeugel, de stofkap
    met de rail en zijn groef en dwarsgleuf. Erop: de slide stop (links), de slide lock (beide kanten), drie stalen
    pinnen, de magazijnknop (links), en rechts MADE IN AUSTRIA en de patenten.
    """
    m = gm.Mesh()
    gm.prism(m, zy(BODY), "x", *FRAME_X, "frame", bevel=0.12, smooth=45)
    gm.prism(m, zy(GRIP), "x", *GRIP_X, "grip", bevel=0.22, smooth=50)
    gm.prism(m, zy(GUARD), "x", *GUARD_X, "guard", bevel=0.14, smooth=45)
    gm.prism(m, zy(DUST_TOP), "x", *DUST_X, "frame", bevel=0.06, smooth=40)
    gm.box(m, (DUST_X[0] + 0.12, Y(191), Z(910)), (DUST_X[1] - 0.12, Y(184), Z(700)), "rail_groove")
    gm.prism(m, zy(RAIL), "x", *DUST_X, "rail", bevel=0.05, smooth=40)
    gm.prism(m, zy(SLIDE_STOP), "x", FRAME_X[0] - 0.035, FRAME_X[0] + 0.03, "lever", bevel=0.015)
    for x0, x1 in ((FRAME_X[0] - 0.025, FRAME_X[0] + 0.03), (FRAME_X[1] - 0.03, FRAME_X[1] + 0.025)):
        gm.prism(m, zy(SLIDE_LOCK), "x", x0, x1, "lever", bevel=0.012)
    for (px, py), (x0, x1), r in PINS:
        gm.cylinder(m, (x0 - 0.012, Y(py), Z(px)), (x1 + 0.012, Y(py), Z(px)), r, 10, "pin",
                    data={"base": (x0, Y(py), Z(px)), "axis": (1, 0, 0)})
    gm.prism(m, mag_button_outline(), "x", GRIP_X[0] - 0.13, GRIP_X[0] + 0.03, "button", bevel=0.035)
    w, h = Z(345) - Z(495), Y(175) - Y(200)
    gm.decal(m, (FRAME_X[1], Y(200), Z(345)), (0, 0, -1), (0, 1, 0), w, h,
             plate(w, h, 80, ["MADE IN AUSTRIA", "GLOCK, INC., SMYRNA, GA."], (150, 150, 146, 255), (34, 34, 36, 255)),
             density=80)
    w, h = Z(273) - Z(383), Y(440) - Y(457)
    gm.decal(m, (GRIP_X[1], Y(457), Z(273)), (0, 0, -1), (0, 1, 0), w, h,
             plate(w, h, 96, ["US Pat 4,539,889", "4,825,744 4,893,546"], (92, 92, 90, 255), (30, 30, 32, 255)),
             density=96)
    return m


def magazine(loaded=True):
    """
    Het magazijn, schuin in de greep zoals in het pistool: een polymeer lijf dat bovenaan smaller wordt (twee rijen
    patronen worden er één), een stalen rand met de lipjes, de bodemplaat met een lip vooraan, gaatjes met getallen
    achterop, de inkeping voor de magazijnknop vooraan, en bovenin een patroon (of, leeg, de veer-opvolger).
    """
    m = gm.Mesh()

    def ring(y, hw):
        zf = mag_front_z(y)
        pts = [(-hw, 0.45), (-hw * 0.88, 0.2), (-hw * 0.55, 0.05), (0.0, 0.0), (hw * 0.55, 0.05), (hw * 0.88, 0.2),
               (hw, 0.45), (hw, MAG_DEPTH - 0.12), (hw - 0.12, MAG_DEPTH), (-hw + 0.12, MAG_DEPTH),
               (-hw, MAG_DEPTH - 0.12)]
        return [vec(8 + x, y, zf + z) for x, z in pts]
    rings = [ring(4.42, 0.875), ring(8.6, 0.875), ring(10.2, 0.75), ring(MAG_TOP, 0.72)]
    centre = vec(8, 7.5, mag_front_z(7.5) + MAG_DEPTH / 2)
    gm.loft(m, rings, "mag_body", caps=(False, True), smooth=50, outward=centre, data={"loaded": loaded})
    gm.prism(m, zy(BASE_PLATE), "x", 6.95, 9.05, "mag_base", bevel=0.1, smooth=45)
    # De stalen lipjes bovenop, links en rechts, een beetje naar binnen gekruld.
    z0, z1 = mag_front_z(MAG_TOP) + 0.35, mag_front_z(MAG_TOP) + MAG_DEPTH - 0.1
    for side in (-1, 1):
        lip = [(-0.76, MAG_TOP - 0.12), (-0.64, MAG_TOP - 0.12), (-0.64, MAG_TOP + 0.06), (-0.44, MAG_TOP + 0.13),
               (-0.44, MAG_TOP + 0.19), (-0.76, MAG_TOP + 0.13)]
        lip = [(8 + side * x, y) for x, y in lip]
        gm.prism(m, lip, "z", z0, z1, "mag_lip", bevel=0.02)
    if loaded:
        rim_z = mag_front_z(MAG_TOP) + MAG_DEPTH - 0.16
        centre_y = MAG_TOP + 0.33
        base = (8.13, centre_y, rim_z)
        data = {"base": base, "axis": (0, 0, -1)}
        gm.lathe(m, base, (0, 0, -1), CASE, 14, "brass", data=data)
        gm.lathe(m, base, (0, 0, -1), BULLET, 14, "copper", data=data)
    else:
        # De opvolger (de plaat waar de veer onder duwt), schuin zoals de patronen erop zouden liggen.
        gm.box(m, (8 - 0.62, MAG_TOP - 0.05, mag_front_z(MAG_TOP) + 0.25),
               (8 + 0.62, MAG_TOP + 0.08, mag_front_z(MAG_TOP) + MAG_DEPTH - 0.2), "follower", bevel=0.04)
    # De gaatjes met getallen achterop (het vlak loopt schuin, evenwijdig aan de as van het magazijn).
    a = math.radians(GRIP_ANGLE)
    up = (0, math.cos(a), -math.sin(a))
    y_lo = 5.1
    origin = (8 - 0.45, y_lo, mag_front_z(y_lo) + MAG_DEPTH)
    length = (9.9 - y_lo) / math.cos(a)
    gm.decal(m, origin, (1, 0, 0), up, 0.9, length, witness_holes(0.9, length, 48), density=48)
    return m


def round_9mm(base, axis):
    """Een patroon (huls + kogel) vanaf zijn bodem in de richting {axis}."""
    m = gm.Mesh()
    data = {"base": base, "axis": axis}
    gm.lathe(m, base, axis, CASE, 14, "brass", data=data)
    gm.lathe(m, base, axis, BULLET, 14, "copper", data=data)
    return m


def casing_9mm(base, axis):
    """Een lege huls: open bovenaan, donker vanbinnen."""
    m = gm.Mesh()
    profile = CASE + [(1.524, 0.35), (1.42, 0.35), (1.42, 0.0)]
    gm.lathe(m, base, axis, profile, 14, "brass", data={"base": base, "axis": axis, "open": True})
    return m


def bullet_9mm(base, axis):
    m = gm.Mesh()
    profile = [(0.0, 0.0), (0.0, 0.345), (0.05, 0.357)] + [(h - 1.524 + 0.55, r) for h, r in BULLET[1:]]
    gm.lathe(m, base, axis, profile, 14, "copper", data={"base": base, "axis": axis, "bullet": True})
    return m


# ---------------------------------------------------------------- Schilderen

def noise(p, scale=16.0, seed=0):
    """Een vast "toeval" per texel op het model (0..1), zodat twee vlakken die elkaar raken hetzelfde ruisje hebben."""
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


FRAME_COL = {"polymer": (53, 53, 56), "steel": (92, 96, 103), "titanium": (132, 139, 149)}
SLIDE_COL = {"steel": (58, 59, 64), "titanium": (134, 140, 149)}
BARREL_COL = {"steel": (70, 72, 77), "titanium": (144, 150, 159)}
PART_COL = {"steel": (152, 156, 163), "titanium": (150, 162, 178)}
BLACK = (44, 44, 47)
DARK = (19, 19, 21)


def metal_of(skin):
    return "titanium" if skin == "titanium" else "steel"


def edge_amount(isl, n):
    """Hoeveel een texel op een afgeronde rand zit (0 = plat vlak, 1 = midden van de rand), langs de as van zijn prism."""
    axis = isl.data.get("axis")
    if not isinstance(axis, str):
        return 0.0
    a = abs(float(n[{"x": 0, "y": 1, "z": 2}[axis]]))
    return max(0.0, 4 * a * (1 - a))


def painter(skin, isl, p, n, tx, ty):
    kind = isl.kind
    x, y, z = (float(c) for c in p)
    nx, ny, nz = (float(c) for c in n)
    grain = noise(p) - 0.5
    edge = edge_amount(isl, n)
    if kind in ("frame", "grip", "guard", "rail", "rail_groove", "trigger", "trigger_safety", "button"):
        material = skin if kind not in ("button",) else "polymer"
        col = FRAME_COL[material]
        polymer = material == "polymer"
        # Lichtere randen en bovenvlakken (zoals TACZ: donkere vlakken, randjes rond 85-95), zodat de vorm ook op een
        # donkere achtergrond leesbaar blijft.
        col = add(col, grain * (5 if polymer else 7) + 30 * edge + (6 if ny > 0.7 else 0))
        if kind == "rail_groove":
            col = add(col, -12)
        if kind == "trigger_safety":
            col = add(col, -5)
        side = abs(nx) > 0.9
        if kind == "grip" and side and gm.inside((z, y), PANEL):
            # Het reliëf van de greep: een fijn, onregelmatig korreltje (lichte bultjes, donkere putjes).
            d = gm.distance_to_outline((z, y), PANEL)
            if d < 0.05:
                col = add(col, -11)
            else:
                v = noise(p, 16, 7)
                col = add(col, 9 if v > 0.68 else (-7 if v < 0.28 else 0))
        if kind in ("frame", "grip") and side:
            # De duimsteun: twee zachte, donkere banen boven de greep.
            if Z(470) < z < Z(330):
                for band in (Y(228), Y(255)):
                    t = abs(y - band) / 0.14
                    if t < 1:
                        col = add(col, -8 * (1 - t * t))
            cz, cy, rz, ry = LOCK_RECESS
            e = ((z - cz) / rz) ** 2 + ((y - cy) / ry) ** 2
            if e < 1:
                col = add(col, -7 if e < 0.8 else -14)
        if kind == "grip" and nx > 0.9:
            # Rechts: het platte uiteinde van de magazijnknop.
            if gm.inside((z, y), mag_button_outline()) and gm.distance_to_outline((z, y), mag_button_outline()) < 0.045:
                col = add(col, -14)
        if kind == "grip" and not side and ny > -0.8 and Y(492) < y < Y(338):
            # Ribbels op de voor- en achterkant van de greep (zoals een Gen 3: dwarse groefjes).
            if (y * 7.2) % 1.0 < 0.3:
                col = add(col, -10)
        if kind == "grip" and ny < -0.8:
            # De onderkant: het donkere gat waar het magazijn in gaat.
            if abs(x - 8) < 0.93 and mag_front_z(y) - 0.06 < z < mag_front_z(y) + MAG_DEPTH + 0.06:
                col = DARK
        if kind == "guard" and nz < -0.7 and Y(292) < y < Y(242):
            # Fijne groefjes op de voorkant van de trekkerbeugel.
            if (y * 9.0) % 1.0 < 0.35:
                col = add(col, -9)
        if kind == "frame" and ny > 0.9 and y > 11.2:
            col = add(col, 4)
        return (*col, 255)
    if kind in ("slide", "slide_groove", "slide_edge"):
        col = SLIDE_COL[metal_of(skin)]
        col = add(col, grain * 4)
        # Afgesleten randen: de schuine bovenkanten en de afgeronde neus zijn lichter.
        if abs(nx) > 0.3 and ny > 0.3:
            col = add(col, 30)
        col = add(col, 30 * edge + (5 if ny > 0.9 else 0))
        if kind == "slide_groove":
            col = add(col, -12)
        if kind == "slide_edge":
            col = add(col, 14 + 12 * edge)
        # Binnenin (door de uitwerpopening te zien): donker. De voor- en achterwand van de opening, en de zijwanden.
        if PORT_Z0 - 0.01 < z < PORT_Z1 + 0.01:
            if (abs(x - PORT_X) < 0.01 and nx > 0.9) or (abs(x - 8.75) < 0.01 and nx < -0.9) or (y < PORT_Y + 0.01 and ny > 0.9 and x > 8.74 and x < 8.99):
                col = DARK
        if abs(z - PORT_Z0) < 0.01 and nz > 0.9 and x > PORT_X - 0.01:
            col = add(DARK, 6)
        if abs(z - PORT_Z1) < 0.01 and nz < -0.9 and x > PORT_X - 0.01:
            # De stootbodem met het gaatje van de slagpin.
            col = (28, 28, 31)
            if math.hypot(x - 8, y - BORE_Y) < 0.06:
                col = DARK
        if ny < -0.9:
            col = add(col, -6)
        if nz > 0.9 and z > SLIDE_Z1 - 0.01:
            # Achterkant: het afdekplaatje.
            if 7.3 < x < 8.7 and 11.5 < y < 12.75:
                edge_d = min(x - 7.3, 8.7 - x, y - 11.5, 12.75 - y)
                col = add(col, -9 if edge_d < 0.04 else 3)
        if nz < -0.9 and z < SLIDE_Z0 + 0.01:
            # Voorkant: het gat rond de loop en het gaatje van de veerstang.
            r = math.hypot(x - 8, y - BORE_Y)
            if r < 0.57:
                col = DARK
            if math.hypot(x - 8, y - ROD_Y) < 0.115:
                col = DARK
        return (*col, 255)
    if kind == "sight":
        return (*add(BLACK, grain * 3 + 22 * edge), 255)
    if kind in ("barrel", "barrel_hood"):
        col = BARREL_COL[metal_of(skin)]
        col = add(col, grain * 5 + 26 * edge)
        if kind == "barrel":
            base = np.array(isl.data.get("base", (8, BORE_Y, 0.49)))
            r = math.hypot(x - 8, y - BORE_Y)
            if r < 0.19 and z < 1.0:
                col = add(DARK, 6 if r > 0.15 else 0)
            elif nz < -0.5 and z < 0.6:
                col = add(col, 32)
        else:
            if ny > 0.9:
                col = add(col, 10 + grain * 4)
            if nz > 0.9 and abs(z - (PORT_Z1 - 0.012)) < 0.02:
                rr = math.hypot(x - 8, y - BORE_Y)
                if rr < 0.21:
                    col = DARK
                elif rr < 0.26:
                    col = add(col, 25)
        return (*col, 255)
    if kind == "rod":
        return (*add((42, 42, 45), grain * 4), 255)
    if kind == "coil":
        col = PART_COL[metal_of(skin)]
        base_axis_y = ROD_Y if y < 11.9 else BORE_Y
        ang = math.atan2(y - base_axis_y, x - 8) / (2 * math.pi)
        phase = (z / 0.19 + ang) % 1.0
        col = add(col, 10 if phase < 0.25 else (-58 if phase > 0.7 else -10))
        return (*col, 255)
    if kind == "striker":
        return (*add(PART_COL[metal_of(skin)], grain * 6), 255)
    if kind == "lever":
        col = add((50, 51, 55), grain * 4 + 24 * edge)
        if abs(nx) > 0.9 and (z * 11) % 1.0 < 0.32 and Z(618) - 0.02 < z < Z(603) + 0.02:
            col = add(col, -12)
        return (*col, 255)
    if kind == "pin":
        base = np.array(isl.data["base"])
        r = math.hypot(y - base[1], z - base[2])
        col = (158, 162, 169) if r < 0.07 else (96, 99, 105)
        return (*add(col, grain * 6), 255)
    if kind in ("decal", "dot"):
        return (255, 0, 255, 255)
    return (255, 0, 255, 255)


def mag_painter(skin, isl, p, n, tx, ty):
    kind = isl.kind
    x, y, z = (float(c) for c in p)
    nx, ny, nz = (float(c) for c in n)
    grain = noise(p) - 0.5
    if kind == "mag_body":
        col = add((48, 48, 51), grain * 4)
        if y > MAG_TOP - 0.17:
            col = add((72, 74, 79), grain * 5)          # de stalen binnenkant komt bovenaan boven
        if ny > 0.9 and y > MAG_TOP - 0.01:
            if isl.data.get("loaded"):
                # Onder de bovenste patroon: schaduw, en aan de andere kant de volgende patroon.
                col = (180, 140, 66) if x < 7.98 else (26, 26, 28)
            else:
                col = (64, 64, 68)
        if nz < -0.6 and ny < 0.5 and 9.25 < y < 9.5 and abs(x - 8) < 0.3:
            col = (20, 20, 22)                          # de inkeping voor de magazijnknop
        return (*col, 255)
    if kind == "mag_base":
        col = add((50, 50, 53), grain * 4 + 28 * edge_amount(isl, n))
        return (*col, 255)
    if kind == "mag_lip":
        return (*add((84, 86, 92), grain * 6 + 18 * edge_amount(isl, n)), 255)
    if kind == "follower":
        return (*add((66, 66, 70), grain * 4), 255)
    if kind in ("brass", "copper"):
        base = np.array(isl.data["base"], dtype=float)
        axis = gm.unit(np.array(isl.data["axis"], dtype=float))
        rel = np.array([x, y, z]) - base
        h = float(np.dot(rel, axis))
        r = float(np.linalg.norm(rel - axis * h))
        along = float(np.dot(np.array([nx, ny, nz]), axis))
        if kind == "brass":
            col = (204, 162, 82)
            col = add(col, -18 + 30 * min(1.0, h / 1.5) + grain * 6)
            if along < -0.9:
                # De bodem: het slaghoedje in het midden en een rand.
                col = (196, 158, 92) if r < 0.13 else ((150, 116, 52) if r < 0.16 else (188, 150, 76))
                if 0.24 < r < 0.27:
                    col = add(col, -18)
            elif 0.09 < h < 0.2 and r < 0.36:
                col = add(col, -40)                     # de groef voor de extractor
            if isl.data.get("open") and along > 0.5 and r < 0.351:
                col = (46, 36, 22)                      # binnenin de lege huls
            return (*col, 255)
        col = (190, 114, 64)
        col = add(col, 22 * max(0.0, along) + grain * 6)
        return (*col, 255)
    return (255, 0, 255, 255)


# ---------------------------------------------------------------- Wegschrijven

PARTS = ("frame", "slide", "barrel", "springs", "striker", "trigger")
PART_MATERIALS = {
    "frame": ("polymer", "steel", "titanium"), "slide": ("steel", "titanium"), "barrel": ("steel", "titanium"),
    "springs": ("steel", "titanium"), "striker": ("steel", "titanium"), "trigger": ("polymer", "steel", "titanium"),
}
GUN_TEXTURE = (1024, 512)
MAG_TEXTURE = (512, 512)
MTL = """# De materialen van de Glock 19 (hoofdstuk 74). "#gun" enz. zijn namen uit de json ("textures"), zodat een
# onderdeel per materiaal een ander plaatje kan krijgen. Ka 1 1 1 = gloeit (de witte vizierpunten).
newmtl gun
map_Kd #gun
newmtl glow
map_Kd #gun
Ka 1 1 1
newmtl polymer
map_Kd #polymer
newmtl polymer_glow
map_Kd #polymer
Ka 1 1 1
newmtl steel
map_Kd #steel
newmtl steel_glow
map_Kd #steel
Ka 1 1 1
newmtl mag
map_Kd #mag
"""


def build():
    """Alle onderdelen als meshes, en de twee texture-indelingen."""
    parts = {"frame": frame(), "slide": slide(), "barrel": barrel(), "springs": springs(), "striker": striker(),
             "trigger": trigger()}
    mags = {"magazine": magazine(True), "magazine_empty": magazine(False)}
    ammo = {
        # De losse patroon/huls staan rechtop met hun midden op (8, 8,3, 8) en (8, 7,65, 8), zoals de oude modellen
        # (FirearmRenderer en CasingRenderer schuiven ze daarop).
        "round": round_9mm((8, 8.3 - ROUND_LENGTH / 2, 8), (0, 1, 0)),
        "casing": casing_9mm((8, 7.65 - 0.762, 8), (0, 1, 0)),
        "bullet": bullet_9mm((8, 8 - 0.65, 8), (0, 1, 0)),
    }
    gun_islands = [i for m in parts.values() for i in m.islands]
    gm.pack(gun_islands, *GUN_TEXTURE)
    mag_islands = [i for m in list(mags.values()) + list(ammo.values()) for i in m.islands]
    gm.pack(mag_islands, *MAG_TEXTURE)
    return parts, mags, ammo, gun_islands, mag_islands


def write_textures(assets, gun_islands, mag_islands):
    folder = assets / "textures" / "item" / "glock_19"
    folder.mkdir(parents=True, exist_ok=True)
    for skin in ("polymer", "steel", "titanium"):
        gm.paint(gun_islands, *GUN_TEXTURE, painter, skin).save(folder / f"{skin}.png")
    gm.paint(mag_islands, *MAG_TEXTURE, mag_painter, "mag").save(folder / "magazine.png")


def write_objs(assets, parts, mags, ammo):
    folder = assets / "models" / "firearm" / "glock_19"
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "glock_19.mtl").write_text(MTL, encoding="utf-8", newline="\n")
    note = "Gemaakt door tools/generate_gun_models.py (tools/glock19_model.py), hoofdstuk 74. Niet met de hand aanpassen."
    counts = {}
    plain = {"gun": "gun", "glow": "glow"}
    for name, mesh in parts.items():
        counts[name] = gm.write_obj(folder / f"{name}.obj", [(name, mesh, plain)], "glock_19.mtl", *GUN_TEXTURE, note)
    mag_map = {"gun": "mag", "glow": "mag"}
    for name, mesh in list(mags.items()) + list(ammo.items()):
        counts[name] = gm.write_obj(folder / f"{name}.obj", [(name, mesh, mag_map)], "glock_19.mtl", *MAG_TEXTURE, note)
    # Het hele pistool in één bestand (voor je inventaris, je hand in derde persoon en op de grond): een polymeer frame
    # en trekker, de rest staal, met een vol magazijn. Twee plaatjes, dus de texture-coördinaten per groep.
    whole = folder / "whole.obj"
    poly = {"gun": "polymer", "glow": "polymer_glow"}
    steel = {"gun": "steel", "glow": "steel_glow"}
    groups = [(name, parts[name], poly if name in ("frame", "trigger") else steel) for name in PARTS]
    counts["whole"] = write_obj_mixed(whole, groups, [("magazine", mags["magazine"], mag_map)], note)
    return counts


def write_obj_mixed(path, gun_groups, mag_groups, note):
    """Zoals gunmesh.write_obj, maar met twee texture-indelingen (het pistool en het magazijn) in één bestand."""
    lines = [f"# {note}", "mtllib glock_19.mtl"]
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
    for groups, (W, H) in ((gun_groups, GUN_TEXTURE), (mag_groups, MAG_TEXTURE)):
        for name, mesh, materials in groups:
            body.append(f"o {name}")
            by_material = {}
            for material, verts in mesh.faces:
                by_material.setdefault(material, []).append(verts)
            for material, faces in by_material.items():
                body.append(f"usemtl {materials[material]}")
                for verts in faces:
                    refs = []
                    for p, nrm, isl, tx, ty in verts:
                        refs.append(f"{key_of(vs, out_v, p / 16.0)}/{key_of(vts, out_vt, ((isl.x + tx) / W, (isl.y + ty) / H))}"
                                    f"/{key_of(vns, out_vn, gm.unit(nrm))}")
                    body.append("f " + " ".join(refs))
                    total += 1
    lines += [f"v {x:.5f} {y:.5f} {z:.5f}" for x, y, z in out_v]
    lines += [f"vt {u:.6f} {v:.6f}" for u, v in out_vt]
    lines += [f"vn {x:.4f} {y:.4f} {z:.4f}" for x, y, z in out_vn]
    lines += body
    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    return total


def points_as_elements(points):
    """De punten van een mesh als "doosjes" van niets, zodat model_display.fit er een houding voor kan uitrekenen."""
    return [{"from": [float(c) for c in p], "to": [float(c) for c in p]} for p in points]


def rig(parts, mags):
    """De getallen voor client/guns/FirearmRig (hoe het pistool in je hand hangt), afgeleid van het model."""
    out = {}
    for name, mesh in parts.items():
        lo, hi = mesh.bounds()
        out[name] = (lo, hi)
    lo, hi = mags["magazine"].bounds()
    out["magazine"] = (lo, hi)
    return out
