"""
Maakt de 3D-blokmodellen (JSON) van het zonnepaneel en de warmtegenerator.

Een blokmodel in Minecraft is een lijstje doosjes ("elements"), elk met een begin- en eindpunt
in pixels (een blok is 16 x 16 x 16) en een plaatje per kant. Met de hand is zo'n bestand lang
en foutgevoelig, dus bouwen we het hier op met een paar hulpjes.

De plaatjes zelf maakt tools/generate_textures.py.

Draaien: python tools/generate_block_models.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "starktech"
SIDES = ("north", "south", "east", "west", "up", "down")


def box(frm, to, textures, rotation=None, glow=False, skip=()):
    """
    Eén doosje van {@code frm} tot {@code to} (in pixels).

    textures: één plaatje voor alle kanten ("#metal"), of een dict per kant; een kant die er
    niet in staat, krijgt "rest" (of wordt weggelaten als die er ook niet is).
    rotation: (as, hoek, middelpunt), bijvoorbeeld ("x", 22.5, [8, 5, 8]).
    glow: dit doosje geeft zelf licht (vol licht, geen schaduw), voor een gloeiend rooster.
    skip: kanten die je nooit ziet (bijvoorbeeld omdat ze tegen een ander doosje zitten).
    """
    if isinstance(textures, str):
        textures = {"rest": textures}
    faces = {}
    for side in SIDES:
        if side in skip:
            continue
        texture = textures.get(side, textures.get("rest"))
        if texture is None:
            continue
        face = {"texture": texture}
        cull = cullface(side, frm, to) if rotation is None else None
        if cull:
            face["cullface"] = cull
        faces[side] = face
    element = {"from": list(frm), "to": list(to), "faces": faces}
    if rotation:
        axis, angle, origin = rotation
        element["rotation"] = {"origin": list(origin), "axis": axis, "angle": angle}
    if glow:
        element["shade"] = False
        element["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    return element


def cullface(side, frm, to):
    """Een kant die precies op de rand van het blok ligt, hoeft niet getekend als daar een blok staat."""
    edge = {"north": frm[2] == 0, "south": to[2] == 16, "west": frm[0] == 0,
            "east": to[0] == 16, "down": frm[1] == 0, "up": to[1] == 16}
    return side if edge[side] else None


def save(name, textures, elements):
    model = {"parent": "block/block", "textures": textures, "elements": elements}
    path = ROOT / "models" / "block" / (name + ".json")
    path.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("geschreven:", path.relative_to(ROOT.parent.parent.parent.parent.parent))


def blockstate(name, variants):
    path = ROOT / "blockstates" / (name + ".json")
    path.write_text(json.dumps({"variants": variants}, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("geschreven:", path.relative_to(ROOT.parent.parent.parent.parent.parent))


# ================================================================ Zonnepaneel

def solar_panel():
    """
    Een voetplaat, een paaltje, en daarop een schuin paneel (22,5 graden, naar het zuiden)
    met de cellen bovenop, een aluminium rand en een geribde achterkant.
    Het hoogste punt zit rond 9 pixels: zie de vorm in SolarPanelBlock.
    """
    tilt = ("x", 22.5, [8, 5.25, 8])
    elements = [
        box([2, 0, 2], [14, 1, 14], "#side"),                       # voetplaat
        box([7, 1, 7], [9, 5, 9], "#side", skip=("up", "down")),    # paal
        box([5, 4, 7.5], [11, 5, 8.5], "#side"),                    # scharnier
        box([0, 4.5, 0], [16, 6, 16],
            {"up": "#top", "down": "#under", "rest": "#frame"}, rotation=tilt),
    ]
    save("solar_panel", {
        "particle": "starktech:block/solar_panel_side",
        "top": "starktech:block/solar_panel_top",
        "frame": "starktech:block/solar_panel_frame",
        "under": "starktech:block/solar_panel_under",
        "side": "starktech:block/solar_panel_side",
    }, elements)


# ================================================================ Warmtegenerator

def heat_generator(lit):
    """
    Een kast op een sokkel met een deksel, met de voorkant naar het noorden (de blockstate
    draait hem naar waar hij moet kijken):
    - voorin een rooster dat een halve pixel naar voren steekt; met lit gloeit het (eigen licht),
    - aan beide zijkanten vier koelribben,
    - achterop twee koperen pijpen,
    - bovenop een luchtrooster.
    """
    grill = "#grill_on" if lit else "#grill"
    elements = [
        box([0, 0, 0], [16, 2, 16], "#base"),                                          # sokkel
        box([1, 2, 1], [15, 13, 15],
            {"north": "#front", "south": "#back", "east": "#side", "west": "#side"}),  # kast
        box([0, 13, 0], [16, 15, 16], {"up": "#top", "rest": "#base"}),                # deksel
        box([4, 15, 4], [12, 16, 12], {"up": "#vent", "rest": "#base"}, skip=("down",)),
        box([3, 4, 0.5], [13, 11, 1], {"north": grill, "rest": "#base"}, glow=lit, skip=("south",)),
    ]
    for i in range(4):
        z = 2.5 + i * 3
        elements.append(box([15, 3, z], [16, 12, z + 1], "#fin", skip=("west",)))
        elements.append(box([0, 3, z], [1, 12, z + 1], "#fin", skip=("east",)))
    for x in (3, 11):
        elements.append(box([x, 2, 15], [x + 2, 13, 16], "#pipe", skip=("north",)))
    save("heat_generator_on" if lit else "heat_generator", {
        "particle": "starktech:block/heat_generator_side",
        "front": "starktech:block/heat_generator_front",
        "side": "starktech:block/heat_generator_side",
        "back": "starktech:block/heat_generator_back",
        "top": "starktech:block/heat_generator_top",
        "vent": "starktech:block/heat_generator_vent",
        "base": "starktech:block/heat_generator_base",
        "fin": "starktech:block/heat_generator_fin",
        "pipe": "starktech:block/heat_generator_pipe",
        "grill": "starktech:block/heat_generator_grill",
        "grill_on": "starktech:block/heat_generator_grill_on",
    }, elements)


def heat_generator_states():
    """Vier richtingen, aan of uit: 8 varianten."""
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for facing, y in turn.items():
        for lit in (False, True):
            model = {"model": "starktech:block/heat_generator" + ("_on" if lit else "")}
            if y:
                model["y"] = y
            variants["facing=%s,lit=%s" % (facing, str(lit).lower())] = model
    blockstate("heat_generator", variants)


if __name__ == "__main__":
    solar_panel()
    heat_generator(False)
    heat_generator(True)
    heat_generator_states()
