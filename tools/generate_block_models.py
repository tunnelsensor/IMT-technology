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


# ================================================================ Rayshield-laserwand

# Zelfde lijstjes als in tools/generate_textures.py (RAYSHIELD_COLORS en RAYSHIELD_VARIANTS):
# pas ze daar ook aan als je hier iets verandert.
RAYSHIELD_COLORS = ("blue", "cyan", "green", "yellow", "red", "purple")
RAYSHIELD_VARIANTS = 4


def rayshield_variant_name(color, variant):
    """laser_wall_blue, laser_wall_blue_2, laser_wall_blue_3, ... (de eerste heeft geen nummer)."""
    return "laser_wall_%s" % color + ("_%d" % (variant + 1) if variant else "")


def laser_wall_models():
    """
    De laserwand: één dun, doorzichtig vlak (1 pixel dik) midden in het blok.

    Het vlak zelf staat in laser_wall_field.json; per kleur en per variant is er een klein
    modelletje dat alleen zegt welk plaatje erop moet. De varianten verschillen alleen in
    waar en wanneer er een kronkeltje verschijnt. De blockstate geeft elke kleur een lijstje
    varianten: Minecraft kiest dan per blok (op basis van de plek) er één uit, zodat niet elk
    blok van de wand op hetzelfde moment op dezelfde plek een kronkeltje krijgt.
    """
    def face(uv):
        return {"uv": uv, "texture": "#field"}

    field = {
        "parent": "block/block",
        "render_type": "minecraft:translucent",
        "elements": [{
            "from": [0, 0, 7.5],
            "to": [16, 16, 8.5],
            "faces": {
                "north": face([0, 0, 16, 16]),
                "south": face([0, 0, 16, 16]),
                "east": face([7.5, 0, 8.5, 16]),
                "west": face([7.5, 0, 8.5, 16]),
                "up": face([0, 7.5, 16, 8.5]),
                "down": face([0, 7.5, 16, 8.5]),
            },
        }],
    }
    write(ROOT / "models" / "block" / "laser_wall_field.json", field)

    for color in RAYSHIELD_COLORS:
        for variant in range(RAYSHIELD_VARIANTS):
            name = rayshield_variant_name(color, variant)
            texture = "starktech:block/" + name
            write(ROOT / "models" / "block" / (name + ".json"), {
                "parent": "starktech:block/laser_wall_field",
                "render_type": "minecraft:translucent",
                "textures": {"particle": texture, "field": texture},
            })

    # Noord/zuid: het vlak een kwartslag gedraaid. Oost/west en omhoog/omlaag: zoals het is.
    turn = {"north": 90, "south": 90, "east": 0, "west": 0, "up": 0, "down": 0}
    variants = {}
    for color in RAYSHIELD_COLORS:
        for facing, y in turn.items():
            options = []
            for variant in range(RAYSHIELD_VARIANTS):
                option = {"model": "starktech:block/" + rayshield_variant_name(color, variant)}
                if y:
                    option["y"] = y
                options.append(option)
            variants["facing=%s,color=%s" % (facing, color)] = options
    blockstate("laser_wall", variants)


# ================================================================ Bewakingscamera

def security_camera():
    """
    De bewakingscamera, in drie soorten: aan de muur, aan het plafond en op de vloer. Elk model
    kijkt naar het noorden (de muur zit dan aan de zuidkant); de blockstate draait het naar de
    andere kanten. "_on" heeft een lens die licht geeft (de camera staat aan).

    Aan de muur en het plafond kantelt de behuizing 22,5 graden omlaag. Waar de lens dan precies
    zit, staat ook in CameraBlockEntity.eye (daar begint het camerabeeld): verander je hier de
    vorm, pas dat daar dan ook aan.
    """
    textures = {"particle": "starktech:block/security_camera_body", "body": "starktech:block/security_camera_body",
                "mount": "starktech:block/security_camera_mount"}
    tilt = -22.5
    shapes = {
        # soort: (montage, arm, behuizing, lens, draaipunt of None)
        "wall": (([5, 9, 15], [11, 15, 16]), ([7, 11, 12], [9, 13, 15]),
                 ([5.5, 9, 4], [10.5, 13, 12]), ([6.5, 9.5, 3], [9.5, 12.5, 4]), [8, 11, 12]),
        "ceiling": (([5, 15, 5], [11, 16, 11]), ([7, 11, 7], [9, 15, 9]),
                    ([5.5, 8, 4], [10.5, 12, 12]), ([6.5, 8.5, 3], [9.5, 11.5, 4]), [8, 10, 10]),
        "floor": (([5, 0, 5], [11, 1, 11]), ([7, 1, 7], [9, 5, 9]),
                  ([5.5, 5, 4], [10.5, 9, 12]), ([6.5, 5.5, 3], [9.5, 8.5, 4]), None),
    }
    for kind, (mount, arm, housing, lens, pivot) in shapes.items():
        rotation = ("x", tilt, pivot) if pivot else None
        for on in (False, True):
            lens_texture = "starktech:block/security_camera_lens" + ("_on" if on else "")
            elements = [
                box(mount[0], mount[1], "#mount"),
                box(arm[0], arm[1], "#mount"),
                box(housing[0], housing[1], "#body", rotation=rotation),
                box(lens[0], lens[1], {"north": "#lens", "rest": "#mount"}, rotation=rotation, glow=on),
            ]
            save("security_camera_" + kind + ("_on" if on else ""), dict(textures, lens=lens_texture), elements)
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for kind in shapes:
        for facing, y in turn.items():
            for on in (False, True):
                option = {"model": "starktech:block/security_camera_" + kind + ("_on" if on else "")}
                if y:
                    option["y"] = y
                variants["face=%s,facing=%s,online=%s" % (kind, facing, "true" if on else "false")] = option
    blockstate("security_camera", variants)


def c4_blocks():
    """
    C4 (hoofdstuk 10): een plat pakketje van 10 x 10 pixels en 3 dik, met een zwart kastje met een
    schermpje erop. Het model ligt op de vloer; de blockstate draait het naar de muur (x 90) of het
    plafond (x 180), net als een knop in Minecraft. De tijd tekent de mod er zelf voor (C4Renderer).
    """
    textures = {"particle": "starktech:block/c4_putty", "putty": "starktech:block/c4_putty",
                "device": "starktech:block/c4_device", "screen": "starktech:block/c4_screen"}
    elements = [
        box([3, 0, 3], [13, 3, 13], "#putty"),
        box([5, 3, 5], [11, 4.5, 10], {"up": "#screen", "rest": "#device"}, skip=("down",)),
    ]
    save("timed_c4", textures, elements)
    # De remote C4: hetzelfde, met een antenne naast het kastje.
    save("remote_c4", textures, elements + [box([11.5, 3, 10.5], [12.5, 9, 11.5], "#device", skip=("down",))])
    floor = {"north": 0, "east": 90, "south": 180, "west": 270}
    ceiling = {"north": 180, "east": 270, "south": 0, "west": 90}
    for name in ("timed_c4", "remote_c4"):
        variants = {}
        for face, x in (("floor", 0), ("wall", 90), ("ceiling", 180)):
            for facing in ("north", "east", "south", "west"):
                y = ceiling[facing] if face == "ceiling" else floor[facing]
                option = {"model": "starktech:block/" + name}
                if x:
                    option["x"] = x
                if y:
                    option["y"] = y
                variants["face=%s,facing=%s" % (face, facing)] = option
        blockstate(name, variants)


def arc_lamp():
    """
    De Arc Lamp (9 oktober): een gewoon blok, met het glas aan vier kanten en de koelplaat boven en
    onder. Brandt hij ("_on"), dan gloeit het hele blok (vol licht, geen schaduw).
    """
    for lit in (False, True):
        name = "arc_lamp_on" if lit else "arc_lamp"
        textures = {"particle": "starktech:block/arc_lamp_side",
                    "side": "starktech:block/arc_lamp_side" + ("_on" if lit else ""),
                    "top": "starktech:block/arc_lamp_top"}
        save(name, textures, [box([0, 0, 0], [16, 16, 16], {"up": "#top", "down": "#top", "rest": "#side"}, glow=lit)])
    blockstate("arc_lamp", {"lit=false": {"model": "starktech:block/arc_lamp"},
                            "lit=true": {"model": "starktech:block/arc_lamp_on"}})


def light_switch():
    """
    De lichtschakelaar (9 oktober), in drie soorten zoals de camera: aan de muur, op de vloer en aan het
    plafond. Elk model kijkt naar het noorden (aan de muur zit hij dan tegen de zuidkant); de blockstate
    draait het. Een plaatje, een wip die 22,5 graden kantelt (aan: bovenkant naar voren, uit: onderkant)
    en een lampje erboven dat blauw gloeit als hij aan staat.
    """
    shapes = {
        # soort: (plaat, wip, draaipunt van de wip, lampje)
        "wall": (([5, 3, 15], [11, 13, 16]), ([6, 5, 14], [10, 11, 15]), [8, 8, 15],
                 ([7.5, 11.5, 14.75], [8.5, 12.5, 15])),
        "floor": (([5, 0, 3], [11, 1, 13]), ([6, 1, 5], [10, 2, 11]), [8, 1, 8],
                  ([7.5, 1, 11.5], [8.5, 1.25, 12.5])),
        "ceiling": (([5, 15, 3], [11, 16, 13]), ([6, 14, 5], [10, 15, 11]), [8, 15, 8],
                    ([7.5, 14.75, 11.5], [8.5, 15, 12.5])),
    }
    for kind, (plate, rocker, pivot, led) in shapes.items():
        for on in (False, True):
            textures = {"particle": "starktech:block/light_switch_plate",
                        "plate": "starktech:block/light_switch_plate",
                        "rocker": "starktech:block/light_switch_rocker_" + ("on" if on else "off"),
                        "led": "starktech:block/light_switch_led_" + ("on" if on else "off")}
            tilt = -22.5 if on else 22.5
            if kind == "ceiling":
                tilt = -tilt
            elements = [
                box(plate[0], plate[1], "#plate"),
                box(rocker[0], rocker[1], "#rocker", rotation=("x", tilt, pivot)),
                box(led[0], led[1], "#led", glow=on),
            ]
            save("light_switch_" + kind + ("_on" if on else ""), textures, elements)
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for kind in shapes:
        for facing, y in turn.items():
            for on in (False, True):
                option = {"model": "starktech:block/light_switch_" + kind + ("_on" if on else "")}
                if y:
                    option["y"] = y
                variants["face=%s,facing=%s,on=%s" % (kind, facing, "true" if on else "false")] = option
    blockstate("light_switch", variants)


def write(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("geschreven:", path.relative_to(ROOT.parent.parent.parent.parent.parent))


if __name__ == "__main__":
    solar_panel()
    heat_generator(False)
    heat_generator(True)
    heat_generator_states()
    laser_wall_models()
    arc_lamp()
    light_switch()
