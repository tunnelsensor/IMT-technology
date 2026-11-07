"""
De wiskunde van Minecrafts "display" voor een 3D-item: hoe een lijstje doosjes in je inventaris komt te staan, en
vooral een houding die een los onderdeel PRECIES in het midden van zijn vakje zet, zo groot als er past.
Beide generatoren gebruiken dit (generate_gun_models.py voor de Glock, generate_weapon_models.py voor de lasergeweren).

Hoe Minecraft een item in een vakje tekent (GuiGraphics.renderItem, ItemRenderer.render, ItemTransform.apply):

    scherm = translation + draaiing(rotation) * (scale * (punt - 8))        (alles in pixels, 16 = één blok)

De verschuiving ("translation") komt dus NA de draaiing, in de richtingen van het scherm (x = naar rechts, y = omhoog,
z = naar jou toe). Een onderdeel dat in het geweer vooraan zit (z = 2) en van opzij bekeken wordt (rotation (0, 90, 0):
dan wijst de z-as van het model naar rechts op je scherm) moet je dus in x terugschuiven, niet in z. De oude
generatoren schoven in z, en zo stonden de loop, de veer en de capacitor naast of buiten hun vakje.

De draaiing is JOML's rotationXYZ: eerst om z, dan om y, dan om x, rechtshandig (om y met 90 graden gaat +z naar +x:
de kolf, die naar +z wijst, komt rechts). Een doosje met een eigen "rotation" (de schuine greep) draait eerst om zijn
eigen middelpunt, ook rechtshandig (FaceBakery.applyElementRotation).
"""
import math

# Hoe een los onderdeel in je inventaris staat: schuin van rechtsboven, zoals Minecraft zelf een blok laat zien (de
# loop naar rechts). Zo zie je de dikte van een loop of een veer, en de kleine kanten die er bij een plat zijaanzicht
# niet zijn: de uitwerpopening van de slede, het gloeiende venster van een capacitor, de lens van een emitter.
PART_VIEW = (30, 225, 0)


def _rotation(axis, degrees):
    """Een draaimatrix (3 x 3) om de as x, y of z, rechtshandig, zoals JOML."""
    c, s = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
    if axis == "x":
        return ((1, 0, 0), (0, c, -s), (0, s, c))
    if axis == "y":
        return ((c, 0, s), (0, 1, 0), (-s, 0, c))
    return ((c, -s, 0), (s, c, 0), (0, 0, 1))


def _apply(m, v):
    return tuple(sum(m[r][k] * v[k] for k in range(3)) for r in range(3))


def _rotated(v, axis, degrees, origin):
    m = _rotation(axis, degrees)
    d = _apply(m, tuple(v[i] - origin[i] for i in range(3)))
    return tuple(d[i] + origin[i] for i in range(3))


def display_matrix(rotation):
    """De draaiing van een display-houding: rotationXYZ(x, y, z) = Rx * Ry * Rz (een punt draait eerst om z)."""
    rx, ry, rz = rotation
    x, y, z = _rotation("x", rx), _rotation("y", ry), _rotation("z", rz)

    def mul(a, b):
        return tuple(tuple(sum(a[r][k] * b[k][c] for k in range(3)) for c in range(3)) for r in range(3))

    return mul(x, mul(y, z))


def corners(element):
    """De acht hoeken van een doosje, in model-pixels, met zijn eigen draaiing erin."""
    f, t = element["from"], element["to"]
    points = [(x, y, z) for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]
    rot = element.get("rotation")
    if rot:
        points = [_rotated(p, rot["axis"], rot["angle"], rot["origin"]) for p in points]
    return points


def project(elements, rotation):
    """Alle hoeken na de draaiing van de houding, ten opzichte van het midden van het model (8, 8, 8), op schaal 1."""
    m = display_matrix(rotation)
    return [_apply(m, (p[0] - 8, p[1] - 8, p[2] - 8)) for e in elements for p in corners(e)]


def bounds(elements, rotation):
    """((min x, min y, min z), (max x, max y, max z)) van het gedraaide model, op schaal 1."""
    pts = project(elements, rotation)
    return tuple(min(p[i] for p in pts) for i in range(3)), tuple(max(p[i] for p in pts) for i in range(3))


def fit(elements, rotation, size=14.0, max_scale=4.5, scale=None):
    """
    De schaal en de verschuiving die het model midden in een vakje van 16 pixels zetten. Zonder {scale} wordt het zo
    groot als er past ({size} pixels breed of hoog, nooit meer dan {max_scale} keer: een trekker blijft een trekker).
    Geeft (schaal, [x, y, z]) terug, in de eenheden van een model-json.
    """
    lo, hi = bounds(elements, rotation)
    if scale is None:
        widest = max(hi[0] - lo[0], hi[1] - lo[1], 0.5)
        scale = min(max_scale, size / widest)
    shift = [round(-(lo[i] + hi[i]) / 2 * scale, 3) for i in range(3)]
    return round(scale, 3), shift


def gui(elements, rotation, **kwargs):
    """De "gui"-houding (je inventaris, de werkbalk, een scherm) die het model midden in zijn vakje zet."""
    scale, shift = fit(elements, rotation, **kwargs)
    return {"rotation": list(rotation), "translation": shift, "scale": [scale] * 3}
