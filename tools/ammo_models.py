"""
De munitie van de wapens van hoofdstuk 103 als 3D-model: per kaliber een patroon, een lege huls en een kogelpunt, op
dezelfde schaal als de 9mm van de Glock (tools/glock19_model.py: 1 pixel = 12,8 mm). De maten zijn die van de echte
patronen:

- 7.62x39mm (de AK-47): een flesvormige huls van 38,7 mm met een rand van 11,35 mm, een kogel van 7,92 mm, samen 56 mm;
- .338 Lapua Magnum (de AWP): een huls van 69,2 mm met een rand van 14,93 mm, een kogel van 8,61 mm, samen 93,5 mm;
- 12 Gauge (de Remington 870): een rode kunststof huls van 70 mm met een messing bodem van 22,45 mm.

Elke vorm is een profiel voor gunmesh.lathe: (afstand vanaf de bodem, straal), in pixels van het model.
"""
import math

import gunmesh as gm

MM = 1 / 12.8


def bottleneck(length, rim, base, shoulder, shoulder_at, neck, neck_at):
    """Het profiel van een flesvormige huls: de rand, de groef voor de extractor, het lijf, de schouder en de hals."""
    return [(0.0, 0.0), (0.0, rim), (0.1, rim), (0.13, rim * 0.83), (0.2, rim * 0.83), (0.25, base), (shoulder_at, shoulder),
            (neck_at, neck), (length, neck)]


def ogive(start, end, radius, steps=5):
    """Het profiel van een spitse kogel: van de hals van de huls in een boog naar de punt."""
    out = [(start, radius * 0.92)]
    for i in range(1, steps + 1):
        t = i / steps
        out.append((start + (end - start) * (0.25 + 0.75 * t), radius * 0.92 * (1 - t ** 1.6)))
    return out[:-1] + [(end, 0.03), (end, 0.0)]


CALIBERS = {
    # 7.62x39: huls 38,7 mm, rand 11,35 mm, lijf 11,2 -> 10,0 mm bij de schouder, hals 8,6 mm; kogel 7,92 mm; 56 mm.
    "762": dict(
        case=bottleneck(38.7 * MM, 5.675 * MM, 5.6 * MM, 5.0 * MM, 30.5 * MM, 4.3 * MM, 33.0 * MM),
        bullet=ogive(38.7 * MM, 56.0 * MM, 3.96 * MM),
        length=56.0 * MM, case_length=38.7 * MM, rim=5.675 * MM, mouth=3.9 * MM),
    # .338 Lapua Magnum: huls 69,2 mm, rand 14,93 mm, lijf 14,9 -> 13,8 mm bij de schouder, hals 9,4 mm; kogel 8,61 mm;
    # samen 93,5 mm.
    "338": dict(
        case=bottleneck(69.2 * MM, 7.465 * MM, 7.45 * MM, 6.9 * MM, 54.9 * MM, 4.7 * MM, 60.9 * MM),
        bullet=ogive(69.2 * MM, 93.5 * MM, 4.305 * MM, steps=6),
        length=93.5 * MM, case_length=69.2 * MM, rim=7.465 * MM, mouth=4.3 * MM),
    # 12 Gauge (2 3/4 inch): een rode kunststof huls van 20,3 mm dik op een messing bodem (rand 22,45 mm, 12 mm hoog). Geladen
    # (de rand dichtgevouwen) is hij 60 mm lang, afgeschoten 70 mm. De hagel: korrels "00 buck" van 8,4 mm.
    "12g": dict(shell=True, length=60.0 * MM, case_length=70.0 * MM, rim=11.225 * MM, body=10.15 * MM, brass=12.0 * MM,
                mouth=9.3 * MM, pellet=4.2 * MM, shot_height=4.2 * MM * 5.5),
}


def shell_base(c):
    """Het profiel van de messing bodem van een hagelpatroon: de rand en het korte messing kapje."""
    rim, body, brass = c["rim"], c["body"], c["brass"]
    return [(0.0, 0.0), (0.0, rim), (0.09, rim), (0.12, body + 0.01), (brass, body + 0.01), (brass, body - 0.02)]


def round_mesh(caliber, base, axis):
    """Een hele patroon (huls + kogel) vanaf zijn bodem in de richting {axis}."""
    c = CALIBERS[caliber]
    m = gm.Mesh()
    if c.get("shell"):
        # Een hagelpatroon: de messing bodem, de rode kunststof huls, en vooraan de dichtgevouwen rand.
        data = {"base": base, "axis": axis, "rim": c["rim"], "case": c["brass"], "length": c["length"]}
        gm.lathe(m, base, axis, shell_base(c), 14, "brass", data=data)
        body, top = c["body"], c["length"]
        gm.lathe(m, base, axis, [(c["brass"], body), (top - 0.22, body), (top - 0.08, body * 0.93), (top, body * 0.72),
                                 (top, 0.0)], 14, "hull", data=data)
        return m
    data = {"base": base, "axis": axis, "rim": c["rim"], "case": c["case_length"]}
    gm.lathe(m, base, axis, c["case"], 14, "brass", data=data)
    gm.lathe(m, base, axis, c["bullet"], 14, "copper", data=data)
    return m


def casing_mesh(caliber, base, axis):
    """Een lege huls: open bovenaan, donker vanbinnen."""
    c = CALIBERS[caliber]
    m = gm.Mesh()
    top = c["case_length"]
    if c.get("shell"):
        # Een afgeschoten hagelpatroon: de rand is opengevouwen, dus de huls is langer en vooraan open.
        data = {"base": base, "axis": axis, "rim": c["rim"], "case": c["brass"], "length": top, "open": True,
                "mouth": c["mouth"] + 0.01}
        gm.lathe(m, base, axis, shell_base(c), 14, "brass", data=data)
        body = c["body"]
        gm.lathe(m, base, axis, [(c["brass"], body), (top, body), (top, c["mouth"]), (top - 0.6, c["mouth"]), (top - 0.6, 0.0)],
                 14, "hull", data=data)
        return m
    profile = c["case"] + [(top, c["mouth"]), (top - 0.25, c["mouth"]), (top - 0.25, 0.0)]
    gm.lathe(m, base, axis, profile, 14, "brass",
             data={"base": base, "axis": axis, "rim": c["rim"], "case": top, "open": True, "mouth": c["mouth"] + 0.01})
    return m


def bullet_mesh(caliber, base, axis):
    """Een losse kogelpunt (het stuk dat in de huls zit erbij)."""
    c = CALIBERS[caliber]
    m = gm.Mesh()
    if c.get("shell"):
        # De hagel: negen loden korrels, drie lagen van drie op elkaar (zoals ze in de huls zitten).
        r = c["pellet"]
        ball = [(0.0, 0.0)] + [(r * (1 - math.cos(math.pi * i / 6)), r * math.sin(math.pi * i / 6)) for i in range(1, 6)] \
            + [(2 * r, 0.0)]
        axis_v = gm.unit(gm.vec(*axis))
        u, v = gm._plane_basis(axis_v)
        for layer in range(3):
            for k in range(3):
                angle = 2 * math.pi * (k / 3 + layer / 6)
                centre = gm.vec(*base) + axis_v * (layer * r * 1.75) + (u * math.cos(angle) + v * math.sin(angle)) * (r * 1.16)
                gm.lathe(m, tuple(centre), axis, ball, 8, "lead", data={"base": tuple(centre), "axis": axis})
        return m
    seat = 0.55
    start = c["case_length"]
    profile = [(0.0, 0.0), (0.0, c["mouth"] * 0.94), (0.05, c["mouth"])] + [(h - start + seat, r) for h, r in c["bullet"][1:]]
    gm.lathe(m, base, axis, profile, 14, "copper", data={"base": base, "axis": axis, "bullet": True})
    return m
