"""
Een kleine 3D-bouwdoos voor echte wapenmodellen (hoofdstuk 74: de Glock in de stijl van TACZ).

Waarom: Minecrafts eigen json-modellen kennen alleen doosjes die je 22,5 of 45 graden mag draaien, en dan nog om één as.
Een echte Glock heeft afgeronde randen, een ronde loop, een schuine trekkerbeugel en een greep vol vingergroeven.
NeoForge kan ook OBJ-modellen inladen (het gewone formaat van 3D-programma's zoals Blender): daarin mag elk vlak elke vorm
en elke hoek hebben, en Minecraft bakt het daarna gewoon tot een item-model, net als een json. Dit bestand bouwt zo een
model uit eenvoudige vormen:

- prism: een veelhoek (bijvoorbeeld de omtrek van de greep, nagetekend van een foto) die je uittrekt tot een plaat, met
  afgeronde of afgeschuinde randen;
- loft: een buis door een rij doorsneden (het magazijn wordt naar boven toe smaller);
- lathe: een omwentelingslichaam, zoals op een draaibank (een patroon, een pin, de loop);
- decal: een plaatje met tekst dat net boven een vlak zweeft (GLOCK, MADE IN AUSTRIA...).

De texture maakt het script zelf. Elk vlak krijgt zijn eigen stukje van het plaatje (een "eiland") op een vaste dichtheid
(16 texels per model-pixel; TACZ gebruikt er 8), en een schilder-functie kleurt elke texel naar de plek waar hij op het
wapen zit. Zo lopen patronen (het reliëf van de greep, de ribbels van de rail) door van het ene vlak naar het andere.

Eenheden: model-pixels (16 = één blok), net als in een json-model. In het OBJ-bestand zelf staat alles in blokken
(gedeeld door 16), want zo leest NeoForge het.
"""
import math

import numpy as np
from PIL import Image

DENSITY = 16  # texels per model-pixel


# ---------------------------------------------------------------- Kleine wiskunde

def vec(*a):
    return np.array(a, dtype=float)


def unit(v):
    n = float(np.linalg.norm(v))
    return v / n if n > 1e-12 else v


def rot_x(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]], dtype=float)


def rot_y(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], dtype=float)


def rot_z(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]], dtype=float)


def newell(points):
    """De normaal van een veelhoek in 3D (ook als hij niet helemaal plat is), niet genormaliseerd."""
    n = np.zeros(3)
    for i in range(len(points)):
        a, b = points[i], points[(i + 1) % len(points)]
        n[0] += (a[1] - b[1]) * (a[2] + b[2])
        n[1] += (a[2] - b[2]) * (a[0] + b[0])
        n[2] += (a[0] - b[0]) * (a[1] + b[1])
    return n


# ---------------------------------------------------------------- Veelhoeken in 2D

def area2(poly):
    """De oppervlakte met teken: positief = tegen de klok in."""
    s = 0.0
    for i in range(len(poly)):
        a, b = poly[i], poly[(i + 1) % len(poly)]
        s += a[0] * b[1] - b[0] * a[1]
    return s / 2


def ccw(poly):
    poly = [(float(a), float(b)) for a, b in poly]
    return poly if area2(poly) > 0 else poly[::-1]


def edge_normals(poly):
    """De naar buiten wijzende normaal van elke rand van een veelhoek die tegen de klok in loopt."""
    out = []
    for i in range(len(poly)):
        a, b = poly[i], poly[(i + 1) % len(poly)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        length = math.hypot(dx, dy) or 1.0
        out.append((dy / length, -dx / length))
    return out


def inset(poly, d):
    """Elk hoekpunt {d} naar binnen geschoven (met verstek), zodat elke rand evenwijdig blijft."""
    if d == 0:
        return list(poly)
    ns = edge_normals(poly)
    out = []
    for i in range(len(poly)):
        n1, n2 = ns[i - 1], ns[i]
        dot = n1[0] * n2[0] + n1[1] * n2[1]
        f = d / max(1.0 + dot, 0.35)
        out.append((poly[i][0] - (n1[0] + n2[0]) * f, poly[i][1] - (n1[1] + n2[1]) * f))
    return out


def _inside_triangle(p, a, b, c):
    def side(p1, p2, p3):
        return (p1[0] - p3[0]) * (p2[1] - p3[1]) - (p2[0] - p3[0]) * (p1[1] - p3[1])
    d1, d2, d3 = side(p, a, b), side(p, b, c), side(p, c, a)
    neg = d1 < -1e-12 or d2 < -1e-12 or d3 < -1e-12
    pos = d1 > 1e-12 or d2 > 1e-12 or d3 > 1e-12
    return not (neg and pos)


def triangulate(poly):
    """Een veelhoek (tegen de klok in, zonder gaten) in driehoeken ("ear clipping"). Geeft drietallen indexen."""
    idx = list(range(len(poly)))
    tris = []
    guard = 0
    while len(idx) > 3 and guard < 20000:
        guard += 1
        found = False
        for k in range(len(idx)):
            i0, i1, i2 = idx[k - 1], idx[k], idx[(k + 1) % len(idx)]
            a, b, c = poly[i0], poly[i1], poly[i2]
            cross = (b[0] - a[0]) * (c[1] - b[1]) - (b[1] - a[1]) * (c[0] - b[0])
            if cross <= 1e-12:
                continue
            if any(_inside_triangle(poly[j], a, b, c) for j in idx if j not in (i0, i1, i2)
                   and poly[j] not in (a, b, c)):
                continue
            tris.append((i0, i1, i2))
            idx.pop(k)
            found = True
            break
        if not found:
            tris.append((idx[-1], idx[0], idx[1]))
            idx.pop(0)
    if len(idx) == 3:
        tris.append(tuple(idx))
    return tris


def inside(p, poly):
    """Ligt punt p (2D) binnen de veelhoek?"""
    x, y = p
    hit = False
    for i in range(len(poly)):
        (x1, y1), (x2, y2) = poly[i], poly[(i + 1) % len(poly)]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / ((y2 - y1) or 1e-12) + x1:
            hit = not hit
    return hit


def distance_to_outline(p, poly):
    """De kortste afstand van punt p (2D) tot de rand van een veelhoek."""
    best = 1e9
    for i in range(len(poly)):
        a, b = poly[i], poly[(i + 1) % len(poly)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        t = max(0.0, min(1.0, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / ((dx * dx + dy * dy) or 1e-12)))
        best = min(best, math.hypot(p[0] - a[0] - t * dx, p[1] - a[1] - t * dy))
    return best


# ---------------------------------------------------------------- Het model

class Island:
    """
    Een stukje van de texture: {w} x {h} texels. {locate}(tx, ty) zegt waar die texel op het model zit (een punt en de
    normaal, in model-pixels), zodat de schilder weet wat hij moet schilderen. {kind} is een naam voor de schilder
    ("slide", "grip"...). Met data["image"] (een PIL-plaatje) wordt het eiland dat plaatje (tekst, een logo).
    """

    def __init__(self, w, h, locate, kind, data=None, alpha=False):
        self.w = max(1, int(math.ceil(w)))
        self.h = max(1, int(math.ceil(h)))
        self.locate = locate
        self.kind = kind
        self.data = data or {}
        self.alpha = alpha
        self.x = self.y = None


class Mesh:
    """Een lijst vlakken. Elk vlak: (materiaal, [(punt, normaal, eiland, tx, ty), ...]). Materiaal "gun" of "glow"."""

    def __init__(self):
        self.faces = []
        self.islands = []

    def island(self, w, h, locate, kind, data=None, alpha=False):
        isl = Island(w, h, locate, kind, data, alpha)
        self.islands.append(isl)
        return isl

    def face(self, verts, material="gun", want=None):
        """Een vlak; met {want} (de bedoelde normaal) draait hij de volgorde om als die verkeerd staat."""
        if want is not None and np.dot(newell([v[0] for v in verts]), want) < 0:
            verts = verts[::-1]
        self.faces.append((material, verts))

    def add(self, other):
        self.faces += other.faces
        self.islands += [i for i in other.islands if i not in self.islands]
        return self

    def moved(self, rot=None, pivot=(0, 0, 0), move=(0, 0, 0)):
        """Een gedraaide (om {pivot}) en verschoven kopie. De eilanden blijven dezelfde (ze schilderen in de oude stand)."""
        rot = np.eye(3) if rot is None else np.asarray(rot, dtype=float)
        pivot = np.asarray(pivot, dtype=float)
        move = np.asarray(move, dtype=float)
        out = Mesh()
        out.islands = list(self.islands)
        for material, verts in self.faces:
            out.faces.append((material, [(rot @ (p - pivot) + pivot + move, rot @ n, isl, tx, ty)
                                         for p, n, isl, tx, ty in verts]))
        return out

    def points(self):
        return [p for _, verts in self.faces for p, *_ in verts]

    def bounds(self):
        pts = np.array(self.points())
        return pts.min(axis=0), pts.max(axis=0)


# ---------------------------------------------------------------- Uittrekken (prism)

AXIS = {"x": vec(1, 0, 0), "y": vec(0, 1, 0), "z": vec(0, 0, 1)}


def _to3(axis, a, b, t):
    """Een punt (a, b) van de omtrek op hoogte t langs de as. As x: omtrek in (z, y); as y: (x, z); as z: (x, y)."""
    if axis == "x":
        return vec(t, b, a)
    if axis == "y":
        return vec(a, t, b)
    return vec(a, b, t)


def _cap_island(mesh, poly2, axis, t, normal, kind, density, data):
    xs = [p[0] for p in poly2]
    ys = [p[1] for p in poly2]
    a0, b0 = min(xs), min(ys)
    w, h = (max(xs) - a0) * density + 1, (max(ys) - b0) * density + 1

    def locate(tx, ty):
        return _to3(axis, a0 + tx / density, b0 + ty / density, t), normal
    isl = mesh.island(w, h, locate, kind, data)
    return isl, a0, b0


def _cap(mesh, poly2, axis, t, normal, kind, density, data, material):
    isl, a0, b0 = _cap_island(mesh, poly2, axis, t, normal, kind, density, data)
    for tri in triangulate(poly2):
        verts = [(_to3(axis, poly2[i][0], poly2[i][1], t), normal, isl, (poly2[i][0] - a0) * density,
                  (poly2[i][1] - b0) * density) for i in tri]
        mesh.face(verts, material, want=normal)


def prism(mesh, outline, axis, lo, hi, kind, bevel=0.0, bevel_lo=None, bevel_hi=None, rounded=True, smooth=40.0,
          caps=(True, True), skip=(), material="gun", density=DENSITY, data=None, max_strip=26.0):
    """
    Trek een veelhoek uit langs een as, van {lo} tot {hi}. {bevel}: hoe ver de randen aan beide uiteinden afgerond
    (rounded) of afgeschuind worden. {smooth}: hoeken in de omtrek kleiner dan zoveel graden worden glad belicht (een
    gebogen greep), grotere blijven scherp. {skip}: randen (index in de omtrek) zonder wand. Geeft niets terug; alles
    komt in {mesh}.
    """
    outline = ccw(outline)
    n = len(outline)
    axis_v = AXIS[axis]
    data = dict(data or {})
    data.setdefault("axis", axis)  # de schilder wil weten langs welke as de randen afgerond zijn
    b0 = bevel if bevel_lo is None else bevel_lo
    b1 = bevel if bevel_hi is None else bevel_hi
    r = math.sqrt(0.5)
    # De ringen: (hoe ver naar binnen, waar langs de as, aan welk uiteinde (-1, 0, +1), hoek: 0 = op de kap, 90 = op de wand)
    rings = []
    if b0 > 0:
        rings.append((b0, lo, -1, 0.0))
        if rounded:
            rings.append((b0 * (1 - r), lo + b0 * (1 - r), -1, 45.0))
    rings.append((0.0, lo + b0, 0, 90.0))
    rings.append((0.0, hi - b1, 0, 90.0))
    if b1 > 0:
        if rounded:
            rings.append((b1 * (1 - r), hi - b1 * (1 - r), 1, 45.0))
        rings.append((b1, hi, 1, 0.0))
    polys = [inset(outline, ins) for ins, *_ in rings]
    pts = [[_to3(axis, a, b, t) for a, b in poly] for poly, (_, t, _, _) in zip(polys, rings)]
    # De afstand langs de ringen (voor de texture): bogen van 45 graden, of de schuine kant, of de wand.
    ring_dist = [0.0]
    for j in range(len(rings) - 1):
        (i0, t0, s0, _), (i1, t1, s1, _) = rings[j], rings[j + 1]
        ring_dist.append(ring_dist[-1] + math.hypot(i1 - i0, t1 - t0))
    # De normalen van de wand per hoekpunt: glad (het gemiddelde) als de hoek klein is.
    ens = edge_normals(outline)
    smooth_at = []
    for i in range(n):
        n1, n2 = ens[i - 1], ens[i]
        angle = math.degrees(math.acos(max(-1.0, min(1.0, n1[0] * n2[0] + n1[1] * n2[1]))))
        smooth_at.append(angle < smooth)

    def wall_normal(edge, vertex):
        if smooth_at[vertex]:
            n1, n2 = ens[vertex - 1], ens[vertex]
            a, b = n1[0] + n2[0], n1[1] + n2[1]
        else:
            a, b = ens[edge]
        return unit(_to3(axis, a, b, 0.0))

    def ring_normal(j, wall):
        _, _, side, angle = rings[j]
        if side == 0:
            return wall
        c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
        return unit(axis_v * side * c + wall * s)

    # De wanden, in stroken van hoogstens {max_strip} pixels lang (één eiland per strook).
    edges = [e for e in range(n) if e not in skip]
    strips = []
    current, length = [], 0.0
    for e in edges:
        a, b = outline[e], outline[(e + 1) % n]
        el = math.hypot(b[0] - a[0], b[1] - a[1])
        if current and (length + el > max_strip or (current[-1] + 1) % n != e):
            strips.append(current)
            current, length = [], 0.0
        current.append(e)
        length += el
    if current:
        strips.append(current)
    for strip in strips:
        starts = [0.0]
        for e in strip:
            a, b = outline[e], outline[(e + 1) % n]
            starts.append(starts[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
        total = starts[-1]

        def locate(tx, ty, strip=strip, starts=starts, total=total):
            s = min(max(tx / density, 0.0), total)
            k = 0
            while k < len(strip) - 1 and starts[k + 1] < s:
                k += 1
            e = strip[k]
            f = (s - starts[k]) / ((starts[k + 1] - starts[k]) or 1e-9)
            rr = min(max(ty / density, 0.0), ring_dist[-1])
            j = 0
            while j < len(rings) - 2 and ring_dist[j + 1] < rr:
                j += 1
            g = (rr - ring_dist[j]) / ((ring_dist[j + 1] - ring_dist[j]) or 1e-9)
            i0, i1 = e, (e + 1) % n
            p0 = pts[j][i0] + (pts[j][i1] - pts[j][i0]) * f
            p1 = pts[j + 1][i0] + (pts[j + 1][i1] - pts[j + 1][i0]) * f
            wall = wall_normal(e, i0) * (1 - f) + wall_normal(e, i1) * f
            nrm = unit(ring_normal(j, unit(wall)) * (1 - g) + ring_normal(j + 1, unit(wall)) * g)
            return p0 + (p1 - p0) * g, nrm
        isl = mesh.island(total * density + 1, ring_dist[-1] * density + 1, locate, kind, data)
        for k, e in enumerate(strip):
            i0, i1 = e, (e + 1) % n
            for j in range(len(rings) - 1):
                # Een schuine kant (niet afgerond) is één plat vlak; een afgeronde rand en de wand worden glad belicht.
                flat = (not rounded) and (rings[j][3] == 0.0 or rings[j + 1][3] == 0.0)
                if flat:
                    side = rings[j][2] or rings[j + 1][2]
                    fn = unit(axis_v * side + unit(_to3(axis, *ens[e], 0.0)))
                    normals = [fn, fn, fn, fn]
                else:
                    w0, w1 = wall_normal(e, i0), wall_normal(e, i1)
                    normals = [ring_normal(j, w0), ring_normal(j, w1), ring_normal(j + 1, w1), ring_normal(j + 1, w0)]
                quad = [pts[j][i0], pts[j][i1], pts[j + 1][i1], pts[j + 1][i0]]
                tex = [(starts[k] * density, ring_dist[j] * density), (starts[k + 1] * density, ring_dist[j] * density),
                       (starts[k + 1] * density, ring_dist[j + 1] * density),
                       (starts[k] * density, ring_dist[j + 1] * density)]
                want = unit(_to3(axis, *ens[e], 0.0))
                if rings[j][2] != 0 or rings[j + 1][2] != 0:
                    side = rings[j][2] or rings[j + 1][2]
                    want = unit(want + axis_v * side * 0.5)
                mesh.face([(quad[q], normals[q], isl, tex[q][0], tex[q][1]) for q in range(4)], material, want=want)
    if caps[0]:
        _cap(mesh, polys[0], axis, rings[0][1], -axis_v, kind, density, data, material)
    if caps[1]:
        _cap(mesh, polys[-1], axis, rings[-1][1], axis_v, kind, density, data, material)


def chamfered_rect(a0, b0, a1, b1, c=0.0, c_top=None, c_bottom=None):
    """Een rechthoek met afgeschuinde hoeken (onder c_bottom, boven c_top), als omtrek voor prism."""
    ct = c if c_top is None else c_top
    cb = c if c_bottom is None else c_bottom
    pts = []
    pts += [(a0 + cb, b0), (a1 - cb, b0)] if cb > 0 else [(a0, b0), (a1, b0)]
    if cb > 0:
        pts += [(a1, b0 + cb)]
    pts += [(a1, b1 - ct), (a1 - ct, b1)] if ct > 0 else [(a1, b1)]
    pts += [(a0 + ct, b1), (a0, b1 - ct)] if ct > 0 else [(a0, b1)]
    if cb > 0:
        pts += [(a0, b0 + cb)]
    # dubbele punten weg (als een schuine kant 0 is)
    out = []
    for p in pts:
        if not out or (abs(out[-1][0] - p[0]) > 1e-9 or abs(out[-1][1] - p[1]) > 1e-9):
            out.append(p)
    if len(out) > 1 and abs(out[0][0] - out[-1][0]) < 1e-9 and abs(out[0][1] - out[-1][1]) < 1e-9:
        out.pop()
    return out


def box(mesh, lo, hi, kind, bevel=0.0, axis="z", rounded=False, **kw):
    """Een doos van {lo} tot {hi} (3D), met afgeschuinde randen rondom ({bevel})."""
    lo, hi = np.asarray(lo, dtype=float), np.asarray(hi, dtype=float)
    if axis == "z":
        outline, a, b = chamfered_rect(lo[0], lo[1], hi[0], hi[1], bevel), lo[2], hi[2]
    elif axis == "x":
        outline, a, b = chamfered_rect(lo[2], lo[1], hi[2], hi[1], bevel), lo[0], hi[0]
    else:
        outline, a, b = chamfered_rect(lo[0], lo[2], hi[0], hi[2], bevel), lo[1], hi[1]
    prism(mesh, outline, axis, a, b, kind, bevel=bevel, rounded=rounded, **kw)


# ---------------------------------------------------------------- Een buis door doorsneden (loft)

def _plane_basis(normal):
    n = unit(normal)
    helper = vec(0, 1, 0) if abs(n[1]) < 0.9 else vec(1, 0, 0)
    u = unit(np.cross(helper, n))
    v = np.cross(n, u)
    return u, v


def flat_face(mesh, points3, normal, kind, material="gun", density=DENSITY, data=None):
    """Een plat vlak door een rij 3D-punten (een veelhoek zonder gaten), met zijn eigen eiland."""
    normal = unit(np.asarray(normal, dtype=float))
    u, v = _plane_basis(normal)
    o = points3[0]
    poly2 = [(float(np.dot(p - o, u)), float(np.dot(p - o, v))) for p in points3]
    if area2(poly2) < 0:
        order = list(range(len(points3)))[::-1]
        poly2 = [poly2[i] for i in order]
        points3 = [points3[i] for i in order]
    xs, ys = [p[0] for p in poly2], [p[1] for p in poly2]
    a0, b0 = min(xs), min(ys)

    def locate(tx, ty):
        return o + u * (a0 + tx / density) + v * (b0 + ty / density), normal
    isl = mesh.island((max(xs) - a0) * density + 1, (max(ys) - b0) * density + 1, locate, kind, data)
    for tri in triangulate(poly2):
        mesh.face([(points3[i], normal, isl, (poly2[i][0] - a0) * density, (poly2[i][1] - b0) * density) for i in tri],
                  material, want=normal)


def loft(mesh, rings, kind, caps=(True, True), smooth=40.0, material="gun", density=DENSITY, data=None,
         outward=None):
    """
    Een buis door een rij 3D-ringen met evenveel punten (de doorsneden van een magazijn, van onder naar boven). De
    zijkanten worden glad belicht waar de hoek tussen twee buurvlakken kleiner is dan {smooth} graden. {outward}:
    een punt binnenin, om te weten welke kant buiten is.
    """
    rings = [[np.asarray(p, dtype=float) for p in ring] for ring in rings]
    m = len(rings[0])
    centre = np.mean([p for ring in rings for p in ring], axis=0) if outward is None else np.asarray(outward, dtype=float)
    # Normaal per vlakje, dan glad maken rond de ring.
    quad_n = [[None] * m for _ in range(len(rings) - 1)]
    for j in range(len(rings) - 1):
        for k in range(m):
            q = [rings[j][k], rings[j][(k + 1) % m], rings[j + 1][(k + 1) % m], rings[j + 1][k]]
            nn = unit(newell(q))
            mid = np.mean(q, axis=0)
            if np.dot(nn, mid - centre) < 0:
                nn = -nn
            quad_n[j][k] = nn
    perim = [0.0]
    for k in range(m):
        perim.append(perim[-1] + float(np.linalg.norm(rings[0][(k + 1) % m] - rings[0][k])))
    heights = [0.0]
    for j in range(len(rings) - 1):
        heights.append(heights[-1] + float(np.linalg.norm(np.mean(rings[j + 1], axis=0) - np.mean(rings[j], axis=0))))

    def locate(tx, ty):
        s = min(max(tx / density, 0.0), perim[-1])
        k = 0
        while k < m - 1 and perim[k + 1] < s:
            k += 1
        f = (s - perim[k]) / ((perim[k + 1] - perim[k]) or 1e-9)
        hh = min(max(ty / density, 0.0), heights[-1])
        j = 0
        while j < len(rings) - 2 and heights[j + 1] < hh:
            j += 1
        g = (hh - heights[j]) / ((heights[j + 1] - heights[j]) or 1e-9)
        a = rings[j][k] + (rings[j][(k + 1) % m] - rings[j][k]) * f
        b = rings[j + 1][k] + (rings[j + 1][(k + 1) % m] - rings[j + 1][k]) * f
        return a + (b - a) * g, quad_n[min(j, len(quad_n) - 1)][k]
    isl = mesh.island(perim[-1] * density + 1, heights[-1] * density + 1, locate, kind, data)

    def vnormal(j, k, own):
        prev = quad_n[j][(k - 1) % m]
        ang = math.degrees(math.acos(max(-1.0, min(1.0, float(np.dot(prev, own))))))
        return unit(prev + own) if ang < smooth else own

    for j in range(len(rings) - 1):
        for k in range(m):
            own = quad_n[j][k]
            nxt = quad_n[j][(k + 1) % m]
            ang_next = math.degrees(math.acos(max(-1.0, min(1.0, float(np.dot(own, nxt))))))
            n0 = vnormal(j, k, own)
            n1 = unit(own + nxt) if ang_next < smooth else own
            q = [rings[j][k], rings[j][(k + 1) % m], rings[j + 1][(k + 1) % m], rings[j + 1][k]]
            tex = [(perim[k], heights[j]), (perim[k + 1], heights[j]), (perim[k + 1], heights[j + 1]),
                   (perim[k], heights[j + 1])]
            ns = [n0, n1, n1, n0]
            mesh.face([(q[i], ns[i], isl, tex[i][0] * density, tex[i][1] * density) for i in range(4)], material,
                      want=own)
    for end, ring in ((0, rings[0]), (1, rings[-1])):
        if not caps[end]:
            continue
        nn = unit(newell(ring))
        other = np.mean(rings[1] if end == 0 else rings[-2], axis=0)
        if np.dot(nn, np.mean(ring, axis=0) - other) < 0:
            nn = -nn
        flat_face(mesh, ring, nn, kind, material, density, data)


# ---------------------------------------------------------------- Omwentelingslichamen (lathe)

def lathe(mesh, base, axis, profile, segments, kind, material="gun", density=DENSITY, data=None, smooth=50.0,
          start=0.0):
    """
    Een omwentelingslichaam rond een as door {base} in richting {axis}: {profile} = [(h, r), ...] van het ene eind naar
    het andere (h langs de as, r de straal). Een straal 0 is een punt op de as (een dichte kap).
    """
    base = np.asarray(base, dtype=float)
    ax = unit(np.asarray(axis, dtype=float))
    u, v = _plane_basis(ax)
    rmax = max(r for _, r in profile)
    circ = 2 * math.pi * max(rmax, 0.05)
    lengths = [0.0]
    for i in range(len(profile) - 1):
        (h0, r0), (h1, r1) = profile[i], profile[i + 1]
        lengths.append(lengths[-1] + math.hypot(h1 - h0, r1 - r0))
    # Normaal per profielstuk in het (radiaal, as)-vlak, en glad tussen buurstukken met een kleine hoek.
    seg_n = []
    for i in range(len(profile) - 1):
        (h0, r0), (h1, r1) = profile[i], profile[i + 1]
        dh, dr = h1 - h0, r1 - r0
        length = math.hypot(dh, dr) or 1.0
        seg_n.append((dh / length, -dr / length))  # (radiaal, as)

    def prof_normal(i, seg):
        """De normaal op profielpunt i, voor het stuk {seg}."""
        own = seg_n[seg]
        other = seg_n[seg - 1] if seg == i and i > 0 else (seg_n[seg + 1] if seg == i - 1 and i < len(seg_n) else None)
        if other is not None:
            ang = math.degrees(math.acos(max(-1.0, min(1.0, own[0] * other[0] + own[1] * other[1]))))
            if ang < smooth:
                a, b = own[0] + other[0], own[1] + other[1]
                length = math.hypot(a, b) or 1.0
                return a / length, b / length
        return own

    def ring_point(h, r, ang):
        return base + ax * h + (u * math.cos(ang) + v * math.sin(ang)) * r

    def locate(tx, ty):
        ang = start + (tx / density) / circ * 2 * math.pi
        s = min(max(ty / density, 0.0), lengths[-1])
        i = 0
        while i < len(profile) - 2 and lengths[i + 1] < s:
            i += 1
        f = (s - lengths[i]) / ((lengths[i + 1] - lengths[i]) or 1e-9)
        h = profile[i][0] + (profile[i + 1][0] - profile[i][0]) * f
        r = profile[i][1] + (profile[i + 1][1] - profile[i][1]) * f
        nr, na = seg_n[i]
        radial = u * math.cos(ang) + v * math.sin(ang)
        return ring_point(h, r, ang), unit(radial * nr + ax * na)
    isl = mesh.island(circ * density + 1, lengths[-1] * density + 1, locate, kind, data)
    for i in range(len(profile) - 1):
        (h0, r0), (h1, r1) = profile[i], profile[i + 1]
        if r0 == 0 and r1 == 0:
            continue
        n0 = prof_normal(i, i)
        n1 = prof_normal(i + 1, i)
        for k in range(segments):
            a0 = start + 2 * math.pi * k / segments
            a1 = start + 2 * math.pi * (k + 1) / segments
            rad0 = u * math.cos(a0) + v * math.sin(a0)
            rad1 = u * math.cos(a1) + v * math.sin(a1)
            p = [ring_point(h0, r0, a0), ring_point(h0, r0, a1), ring_point(h1, r1, a1), ring_point(h1, r1, a0)]
            ns = [unit(rad0 * n0[0] + ax * n0[1]), unit(rad1 * n0[0] + ax * n0[1]), unit(rad1 * n1[0] + ax * n1[1]),
                  unit(rad0 * n1[0] + ax * n1[1])]
            tex = [(circ * k / segments, lengths[i]), (circ * (k + 1) / segments, lengths[i]),
                   (circ * (k + 1) / segments, lengths[i + 1]), (circ * k / segments, lengths[i + 1])]
            verts = [(p[q], ns[q], isl, tex[q][0] * density, tex[q][1] * density) for q in range(4)]
            if r0 == 0:
                verts = [verts[0], verts[2], verts[3]]
            elif r1 == 0:
                verts = [verts[0], verts[1], verts[2]]
            want = unit((rad0 + rad1) * seg_n[i][0] + ax * seg_n[i][1])
            mesh.face(verts, material, want=want)


def cylinder(mesh, p0, p1, radius, segments, kind, caps=True, radius1=None, **kw):
    """Een cilinder (of afgeknotte kegel met {radius1}) van punt {p0} naar {p1}, met dichte uiteinden."""
    p0, p1 = np.asarray(p0, dtype=float), np.asarray(p1, dtype=float)
    length = float(np.linalg.norm(p1 - p0))
    r1 = radius if radius1 is None else radius1
    profile = [(0.0, radius), (length, r1)]
    if caps:
        profile = [(0.0, 0.0)] + profile + [(length, 0.0)]
    lathe(mesh, p0, p1 - p0, profile, segments, kind, **kw)


# ---------------------------------------------------------------- Plaatjes op een vlak (decal)

def decal(mesh, origin, right, up, w, h, image, kind="decal", material="gun", density=64, lift=0.008):
    """
    Een plat plaatje (tekst, een logo) op een vlak. {origin} = de hoek linksonder, {right} en {up} = eenheidsvectoren
    in het vlak, {w} x {h} model-pixels. {image}: een PIL-plaatje (RGBA, doorzichtig waar niets staat). Het zweeft
    {lift} pixel boven het vlak, zodat het er niet doorheen flikkert.
    """
    right = unit(np.asarray(right, dtype=float))
    up = unit(np.asarray(up, dtype=float))
    normal = unit(np.cross(right, up))
    o = np.asarray(origin, dtype=float) + normal * lift
    W, H = max(1, round(w * density)), max(1, round(h * density))

    def locate(tx, ty):
        return o + right * (tx / density) + up * (h - ty / density), normal
    isl = mesh.island(W, H, locate, kind, {"image": image.resize((W, H), Image.LANCZOS)}, alpha=True)
    corners = [o, o + right * w, o + right * w + up * h, o + up * h]
    tex = [(0, H), (W, H), (W, 0), (0, 0)]
    mesh.face([(corners[i], normal, isl, tex[i][0], tex[i][1]) for i in range(4)], material, want=normal)


# ---------------------------------------------------------------- De texture

def pack(islands, width, height, pad=3):
    """Zet alle eilanden naast elkaar op een plaatje van width x height ("planken": rij per rij, hoogste eerst)."""
    # dict.fromkeys: dubbele weg maar de volgorde houden, zodat de indeling elke run dezelfde is.
    order = sorted(dict.fromkeys(islands), key=lambda i: (-i.h, -i.w))
    x = y = pad
    row = 0
    for isl in order:
        if x + isl.w + pad > width:
            x = pad
            y += row + pad
            row = 0
        isl.x, isl.y = x, y
        x += isl.w + pad
        row = max(row, isl.h)
    if y + row + pad > height:
        raise ValueError(f"de texture ({width} x {height}) is te klein: {y + row + pad} rijen nodig")
    return y + row + pad


def paint(islands, width, height, painter, skin):
    """
    Schildert elk eiland: {painter}(skin, eiland, punt, normaal, tx, ty) geeft (r, g, b, a). Daarna "bloedt" de kleur
    een paar texels uit naar de lege rand rond elk eiland, zodat er van ver (mipmaps) geen zwarte randjes komen.
    """
    img = np.zeros((height, width, 4), dtype=np.uint8)
    unique = list(dict.fromkeys(islands))
    for isl in unique:
        if "image" in isl.data:
            img[isl.y:isl.y + isl.h, isl.x:isl.x + isl.w] = np.asarray(isl.data["image"].convert("RGBA"))
            continue
        for ty in range(isl.h):
            for tx in range(isl.w):
                p, nrm = isl.locate(tx + 0.5, ty + 0.5)
                img[isl.y + ty, isl.x + tx] = painter(skin, isl, p, nrm, tx, ty)
    # Uitbloeden: lege texels naast een ondoorzichtig eiland krijgen de kleur van hun buur (3 rondes). De plaatjes met
    # tekst (doorzichtig waar geen letter staat) doen niet mee en worden ook niet bijgekleurd.
    solid = img[:, :, 3] == 255
    protect = np.zeros(solid.shape, dtype=bool)
    for isl in unique:
        if isl.alpha:
            solid[isl.y:isl.y + isl.h, isl.x:isl.x + isl.w] = False
            protect[isl.y:isl.y + isl.h, isl.x:isl.x + isl.w] = True
    for _ in range(3):
        grown = solid.copy()
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            src = np.roll(np.roll(solid, dy, axis=0), dx, axis=1)
            take = src & ~grown & (img[:, :, 3] == 0) & ~protect
            rolled = np.roll(np.roll(img, dy, axis=0), dx, axis=1)
            img[take] = rolled[take]
            grown |= take
        solid = grown
    return Image.fromarray(img, "RGBA")


# ---------------------------------------------------------------- OBJ wegschrijven

def write_obj(path, parts, mtllib, width, height, comment=""):
    """
    Schrijft een OBJ-bestand. {parts} = [(groepsnaam, mesh, {"gun": "mtl-naam", "glow": "mtl-naam"})]. Alles wordt
    gedeeld door 16 (OBJ in blokken), de texture-coördinaten volgen de plaats van elk eiland op het plaatje.
    """
    lines = [f"# {line}" for line in comment.splitlines()] + [f"mtllib {mtllib}"]
    vs, vts, vns = {}, {}, {}
    out_v, out_vt, out_vn = [], [], []

    def key_of(store, out, value):
        k = tuple(round(float(c), 5) for c in value)
        if k not in store:
            store[k] = len(store) + 1
            out.append(k)
        return store[k]
    body = []
    for name, mesh, materials in parts:
        body.append(f"o {name}")
        by_material = {}
        for material, verts in mesh.faces:
            by_material.setdefault(material, []).append(verts)
        for material, faces in by_material.items():
            body.append(f"usemtl {materials[material]}")
            for verts in faces:
                refs = []
                for p, nrm, isl, tx, ty in verts:
                    vi = key_of(vs, out_v, p / 16.0)
                    ti = key_of(vts, out_vt, ((isl.x + tx) / width, (isl.y + ty) / height))
                    ni = key_of(vns, out_vn, unit(nrm))
                    refs.append(f"{vi}/{ti}/{ni}")
                body.append("f " + " ".join(refs))
    lines += [f"v {x:.5f} {y:.5f} {z:.5f}" for x, y, z in out_v]
    lines += [f"vt {u:.6f} {v:.6f}" for u, v in out_vt]
    lines += [f"vn {x:.4f} {y:.4f} {z:.4f}" for x, y, z in out_vn]
    lines += body
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    return sum(len(mesh.faces) for _, mesh, _ in parts)
