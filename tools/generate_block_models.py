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

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "imttech"
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
        "particle": "imttech:block/solar_panel_side",
        "top": "imttech:block/solar_panel_top",
        "frame": "imttech:block/solar_panel_frame",
        "under": "imttech:block/solar_panel_under",
        "side": "imttech:block/solar_panel_side",
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
        "particle": "imttech:block/heat_generator_side",
        "front": "imttech:block/heat_generator_front",
        "side": "imttech:block/heat_generator_side",
        "back": "imttech:block/heat_generator_back",
        "top": "imttech:block/heat_generator_top",
        "vent": "imttech:block/heat_generator_vent",
        "base": "imttech:block/heat_generator_base",
        "fin": "imttech:block/heat_generator_fin",
        "pipe": "imttech:block/heat_generator_pipe",
        "grill": "imttech:block/heat_generator_grill",
        "grill_on": "imttech:block/heat_generator_grill_on",
    }, elements)


def heat_generator_states():
    """Vier richtingen, aan of uit: 8 varianten."""
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for facing, y in turn.items():
        for lit in (False, True):
            model = {"model": "imttech:block/heat_generator" + ("_on" if lit else "")}
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
            texture = "imttech:block/" + name
            write(ROOT / "models" / "block" / (name + ".json"), {
                "parent": "imttech:block/laser_wall_field",
                "render_type": "minecraft:translucent",
                "textures": {"particle": texture, "field": texture},
            })

    # Hoe het vlak (staand, van west naar oost) gedraaid moet worden: (x, y) in graden.
    # Gewoon: noord/zuid een kwartslag om de y-as; oost/west en omhoog/omlaag zoals het is.
    # Gedraaid ("turned", 13 oktober: liggend in plaats van staand): opzij lopend plat neerleggen
    # (x = 90); omhoog lopend een kwartslag om de y-as (dan staat het noord-zuid).
    turn = {
        False: {"north": (0, 90), "south": (0, 90), "east": (0, 0), "west": (0, 0), "up": (0, 0), "down": (0, 0)},
        True: {"north": (90, 90), "south": (90, 90), "east": (90, 0), "west": (90, 0), "up": (0, 90), "down": (0, 90)},
    }
    variants = {}
    for color in RAYSHIELD_COLORS:
        for turned, facings in turn.items():
            for facing, (x, y) in facings.items():
                options = []
                for variant in range(RAYSHIELD_VARIANTS):
                    option = {"model": "imttech:block/" + rayshield_variant_name(color, variant)}
                    if x:
                        option["x"] = x
                    if y:
                        option["y"] = y
                    options.append(option)
                variants["facing=%s,color=%s,turned=%s" % (facing, color, str(turned).lower())] = options
    blockstate("laser_wall", variants)


# ================================================================ Laserbrug (hoofdstuk 34)

def laser_bridge_models():
    """
    De laserbrug: de zender en de brugplaten. De plaatjes maakt laser_bridge_textures() in tools/generate_textures.py.

    De zender is een vol blok dat naar het noorden kijkt (de blockstate draait hem). Je zet hem in de rand van de
    vloer, dus bovenop zie je twee pijlpunten naar de spleet. De spleet zit bovenin de voorkant (y 12 tot 15), want
    daar komt de plaat naar buiten (y 13 tot 14, zie LaserBridgePlateBlock.TOP). Achter de spleet zit de lichtstreep
    ("lens"); aan geeft die zelf licht.

    De brugplaat: een plaat van 1 pixel dik met de bovenkant op 14 pixels, die zelf licht geeft. Daarbij een felle
    rand (een randje van 1 pixel breed dat een halve pixel boven de plaat uitsteekt) aan elke kant waar de brug
    ophoudt. Dat doet de blockstate met "multipart": de plaat altijd, en per kant een rand als daar geen brug meer
    ligt (north=false enzovoort). De rand ligt in het model aan de noordkant; de blockstate draait hem naar de
    andere kanten.
    """
    for on in (False, True):
        name = "laser_bridge_on" if on else "laser_bridge"
        textures = {"particle": "imttech:block/emitter_side", "side": "imttech:block/emitter_side",
                    "back": "imttech:block/emitter_back", "recess": "imttech:block/emitter_recess",
                    "front": "imttech:block/laser_bridge_front",
                    "top": "imttech:block/laser_bridge_top" + ("_on" if on else ""),
                    "lens": "imttech:block/laser_bridge_lens" + ("_on" if on else "")}
        elements = [
            # de kast, met daarin (alleen door de spleet te zien) de donkere holte
            box([0, 0, 2], [16, 16, 16], {"north": "#recess", "south": "#back", "up": "#top", "rest": "#side"}),
            # de voorkant: onder de spleet, de rand erboven, en links en rechts een paaltje
            box([0, 0, 0], [16, 12, 2], {"north": "#front", "up": "#front", "rest": "#side"}, skip=("south",)),
            box([0, 15, 0], [16, 16, 2], {"north": "#front", "down": "#front", "up": "#top", "rest": "#side"},
                skip=("south",)),
            box([0, 12, 0], [1, 15, 2], {"north": "#front", "east": "#front", "rest": "#side"},
                skip=("south", "up", "down")),
            box([15, 12, 0], [16, 15, 2], {"north": "#front", "west": "#front", "rest": "#side"},
                skip=("south", "up", "down")),
            # de lichtstreep achterin de spleet
            box([1, 12, 1], [15, 15, 2], {"north": "#lens"}, glow=on),
        ]
        save(name, textures, elements)
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for facing, y in turn.items():
        for on in (False, True):
            option = {"model": "imttech:block/laser_bridge" + ("_on" if on else "")}
            if y:
                option["y"] = y
            variants["facing=%s,powered=%s" % (facing, str(on).lower())] = option
    blockstate("laser_bridge", variants)
    write(ROOT / "models" / "item" / "laser_bridge.json", {"parent": "imttech:block/laser_bridge"})

    # De brugplaat en de rand: één vorm, per kleur een klein modelletje met alleen het plaatje (zoals de laserwand).
    def glowing(element):
        element["shade"] = False
        element["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
        return element

    # De plaat heeft alleen een boven- en onderkant: opzij ligt er een andere plaat (die zijkant zie je niet), of er
    # zit een rand die de zijkant afdekt. De rand is iets dikker dan de plaat (van 12,75 tot 14,5), zodat hun vlakken
    # nergens precies op elkaar liggen (anders flikkert het).
    write(ROOT / "models" / "block" / "laser_bridge_plate_field.json", {
        "parent": "block/block", "render_type": "minecraft:translucent", "ambientocclusion": False,
        "elements": [glowing(box([0, 13, 0], [16, 14, 16], {"up": "#field", "down": "#field"}))],
    })
    write(ROOT / "models" / "block" / "laser_bridge_edge_field.json", {
        "parent": "block/block", "render_type": "minecraft:translucent", "ambientocclusion": False,
        "elements": [glowing(box([0, 12.75, 0], [16, 14.5, 1], "#field"))],
    })
    multipart = []
    for color in RAYSHIELD_COLORS:
        for part in ("plate", "edge"):
            texture = "imttech:block/laser_bridge_%s" % color if part == "plate" else "imttech:block/laser_bridge_edge_%s" % color
            write(ROOT / "models" / "block" / ("laser_bridge_%s_%s.json" % (part, color)), {
                "parent": "imttech:block/laser_bridge_%s_field" % part,
                "render_type": "minecraft:translucent",
                "textures": {"particle": texture, "field": texture},
            })
        multipart.append({"when": {"color": color}, "apply": {"model": "imttech:block/laser_bridge_plate_%s" % color}})
        for side, y in turn.items():
            apply = {"model": "imttech:block/laser_bridge_edge_%s" % color}
            if y:
                apply["y"] = y
            multipart.append({"when": {"color": color, side: "false"}, "apply": apply})
    write(ROOT / "blockstates" / "laser_bridge_plate.json", {"multipart": multipart})


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
    textures = {"particle": "imttech:block/security_camera_body", "body": "imttech:block/security_camera_body",
                "mount": "imttech:block/security_camera_mount"}
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
            lens_texture = "imttech:block/security_camera_lens" + ("_on" if on else "")
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
                option = {"model": "imttech:block/security_camera_" + kind + ("_on" if on else "")}
                if y:
                    option["y"] = y
                variants["face=%s,facing=%s,online=%s" % (kind, facing, "true" if on else "false")] = option
    blockstate("security_camera", variants)


def arc_lamp():
    """
    De Arc Lamp (9 oktober): een gewoon blok, met het glas aan vier kanten en de koelplaat boven en
    onder. Brandt hij ("_on"), dan gloeit het hele blok (vol licht, geen schaduw).
    """
    for lit in (False, True):
        name = "arc_lamp_on" if lit else "arc_lamp"
        textures = {"particle": "imttech:block/arc_lamp_side",
                    "side": "imttech:block/arc_lamp_side" + ("_on" if lit else ""),
                    "top": "imttech:block/arc_lamp_top"}
        save(name, textures, [box([0, 0, 0], [16, 16, 16], {"up": "#top", "down": "#top", "rest": "#side"}, glow=lit)])
    blockstate("arc_lamp", {"lit=false": {"model": "imttech:block/arc_lamp"},
                            "lit=true": {"model": "imttech:block/arc_lamp_on"}})


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
            textures = {"particle": "imttech:block/light_switch_plate",
                        "plate": "imttech:block/light_switch_plate",
                        "rocker": "imttech:block/light_switch_rocker_" + ("on" if on else "off"),
                        "led": "imttech:block/light_switch_led_" + ("on" if on else "off")}
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
                option = {"model": "imttech:block/light_switch_" + kind + ("_on" if on else "")}
                if y:
                    option["y"] = y
                variants["face=%s,facing=%s,on=%s" % (kind, facing, "true" if on else "false")] = option
    blockstate("light_switch", variants)


def network_controls():
    """
    De netwerkknop en de netwerkhendel (hoofdstuk 38), net als de lichtschakelaar in drie soorten: aan de muur, op de
    vloer en aan het plafond. Elk model kijkt naar het noorden (aan de muur zit hij dan tegen de zuidkant); de blockstate
    draait het. Een donker plaatje met een wit randje (de kleur van het netwerk), en daarop:
    - de knop: een wit kapje van 4 x 4 dat 2 pixels uitsteekt (ingedrukt: 1 pixel);
    - de hendel: een stalen steeltje met een wit knopje dat 45 graden kantelt (aan: omhoog, uit: omlaag), en een
      lampje op het plaatje (aan: groen, uit: donkerrood).
    Voor in je rugzak een eigen model: het muurmodel naar het midden van het blok geschoven.
    """
    plate_textures = {"particle": "imttech:block/network_control_plate", "plate": "imttech:block/network_control_plate"}
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}

    # ---- De knop: soort -> (plaat, kapje, kapje ingedrukt)
    buttons = {
        "wall": (([5, 5, 15], [11, 11, 16]), ([6, 6, 13], [10, 10, 15]), ([6, 6, 14], [10, 10, 15])),
        "floor": (([5, 0, 5], [11, 1, 11]), ([6, 1, 6], [10, 3, 10]), ([6, 1, 6], [10, 2, 10])),
        "ceiling": (([5, 15, 5], [11, 16, 11]), ([6, 13, 6], [10, 15, 10]), ([6, 14, 6], [10, 15, 10])),
    }
    for kind, (plate, cap, pressed_cap) in buttons.items():
        for pressed in (False, True):
            textures = dict(plate_textures, cap="imttech:block/network_button_cap")
            c = pressed_cap if pressed else cap
            save("network_button_" + kind + ("_pressed" if pressed else ""), textures,
                 [box(plate[0], plate[1], "#plate"), box(c[0], c[1], "#cap")])
    variants = {}
    for kind in buttons:
        for facing, y in turn.items():
            for pressed in (False, True):
                option = {"model": "imttech:block/network_button_" + kind + ("_pressed" if pressed else "")}
                if y:
                    option["y"] = y
                variants["face=%s,facing=%s,pressed=%s" % (kind, facing, "true" if pressed else "false")] = option
    blockstate("network_button", variants)
    save("network_button_inventory", dict(plate_textures, cap="imttech:block/network_button_cap"),
         [box([5, 5, 8], [11, 11, 9], "#plate"), box([6, 6, 6], [10, 10, 8], "#cap")])

    # ---- De hendel: soort -> (plaat, steel, knopje, draaipunt, lampje, hoek als hij aan staat)
    levers = {
        "wall": (([5, 4, 15], [11, 12, 16]), ([7.5, 7.5, 10], [8.5, 8.5, 15]), ([7, 7, 8.5], [9, 9, 10.5]), [8, 8, 15],
                 ([7.5, 4.5, 14.75], [8.5, 5.5, 15]), 45),
        "floor": (([5, 0, 4], [11, 1, 12]), ([7.5, 1, 7.5], [8.5, 6, 8.5]), ([7, 5.5, 7], [9, 7.5, 9]), [8, 1, 8],
                  ([7.5, 1, 10.5], [8.5, 1.25, 11.5]), -45),
        "ceiling": (([5, 15, 4], [11, 16, 12]), ([7.5, 10, 7.5], [8.5, 15, 8.5]), ([7, 8.5, 7], [9, 10.5, 9]), [8, 15, 8],
                    ([7.5, 14.75, 10.5], [8.5, 15, 11.5]), 45),
    }
    for kind, (plate, stick, knob, pivot, led, on_angle) in levers.items():
        for on in (False, True):
            textures = dict(plate_textures, stick="imttech:block/network_lever_stick",
                            knob="imttech:block/network_lever_knob",
                            led="imttech:block/network_lever_led_" + ("on" if on else "off"))
            angle = on_angle if on else -on_angle
            save("network_lever_" + kind + ("_on" if on else ""), textures, [
                box(plate[0], plate[1], "#plate"),
                box(stick[0], stick[1], "#stick", rotation=("x", angle, pivot)),
                box(knob[0], knob[1], "#knob", rotation=("x", angle, pivot)),
                box(led[0], led[1], "#led", glow=on),
            ])
    variants = {}
    for kind in levers:
        for facing, y in turn.items():
            for on in (False, True):
                option = {"model": "imttech:block/network_lever_" + kind + ("_on" if on else "")}
                if y:
                    option["y"] = y
                variants["face=%s,facing=%s,on=%s" % (kind, facing, "true" if on else "false")] = option
    blockstate("network_lever", variants)
    save("network_lever_inventory", dict(plate_textures, stick="imttech:block/network_lever_stick",
                                         knob="imttech:block/network_lever_knob", led="imttech:block/network_lever_led_on"), [
        box([5, 4, 9], [11, 12, 10], "#plate"),
        box([7.5, 7.5, 4], [8.5, 8.5, 9], "#stick", rotation=("x", 45, [8, 8, 9])),
        box([7, 7, 2.5], [9, 9, 4.5], "#knob", rotation=("x", 45, [8, 8, 9])),
    ])
    # In je rugzak groter dan een gewoon blok (het zijn kleine plaatjes), en recht van voren.
    display = {"gui": {"rotation": [20, 200, 0], "translation": [0, 0, 0], "scale": [1.5, 1.5, 1.5]}}
    write(ROOT / "models" / "item" / "network_button.json",
          {"parent": "imttech:block/network_button_inventory", "display": display})
    write(ROOT / "models" / "item" / "network_lever.json",
          {"parent": "imttech:block/network_lever_inventory", "display": display})


def robot_dock():
    """
    Het Robot Dock (hoofdstuk 21): een lage laadplaat waar de robot op staat, met achteraan een paal met een
    stekker die in zijn rug past en een cyaan lampje. Het model kijkt naar het noorden (de open kant); de
    paal staat dus aan de zuidkant. De blockstate draait het.
    """
    textures = {"particle": "imttech:block/robot_dock_side",
                "pad": "imttech:block/robot_dock_pad",
                "side": "imttech:block/robot_dock_side",
                "pole": "imttech:block/robot_dock_pole",
                "light": "imttech:block/robot_dock_light"}
    elements = [
        box([0, 0, 0], [16, 3, 16], {"up": "#pad", "rest": "#side"}),
        box([5, 3, 13], [11, 16, 16], "#pole", skip=("down",)),
        box([6, 9, 11], [10, 12, 13], "#pole", skip=("south",)),
        box([7, 13, 12.9], [9, 15, 13], "#light", glow=True),
    ]
    save("robot_dock", textures, elements)
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for facing, y in turn.items():
        option = {"model": "imttech:block/robot_dock"}
        if y:
            option["y"] = y
        variants["facing=" + facing] = option
    blockstate("robot_dock", variants)


def computer_and_monitor():
    """
    Hoofdstuk 40: de Computer (een volle pc-kast) en de Monitor met toetsenbord. Beide kijken naar het noorden: de
    voorkant van de kast (met de aan-knop) en het scherm van de monitor wijzen naar wie ze neerzette. De blockstate
    draait ze, en "on" kiest het model waarin de knop brandt en het scherm de desktop toont (en zelf licht geeft).
    - de monitor: een voetje en een steeltje achteraan, het scherm (14 x 10) erboven, en vooraan het toetsenbord.
    """
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    for on in (False, True):
        suffix = "_on" if on else ""
        textures = {"particle": "imttech:block/computer_side",
                    "front": "imttech:block/computer_front" + suffix,
                    "back": "imttech:block/computer_back",
                    "side": "imttech:block/computer_side",
                    "top": "imttech:block/computer_top"}
        save("computer" + suffix, textures, [
            box([0, 0, 0], [16, 16, 16], {"north": "#front", "south": "#back", "east": "#side", "west": "#side",
                                          "up": "#top", "down": "#top"}),
        ])
        textures = {"particle": "imttech:block/monitor_case",
                    "case": "imttech:block/monitor_case",
                    "screen": "imttech:block/monitor_screen" + suffix,
                    "keys": "imttech:block/monitor_keyboard"}
        screen = box([1, 3, 11], [15, 13, 12.5], {"north": "#screen", "rest": "#case"})
        if on:
            # Alleen de voorkant van het scherm geeft licht (de rest van de monitor niet).
            screen["faces"]["north"]["shade"] = False
            screen["faces"]["north"]["neoforge_data"] = {"block_light": 15, "sky_light": 15}
        save("monitor" + suffix, textures, [
            box([5, 0, 10], [11, 1, 14], "#case"),
            box([7, 1, 12.5], [9, 6, 13.5], "#case", skip=("down",)),
            screen,
            box([2, 0, 2], [14, 0.75, 7], {"up": "#keys", "rest": "#case"}),
        ])
    for name in ("computer", "monitor"):
        variants = {}
        for facing, y in turn.items():
            for on in (False, True):
                option = {"model": "imttech:block/" + name + ("_on" if on else "")}
                if y:
                    option["y"] = y
                variants["facing=%s,on=%s" % (facing, "true" if on else "false")] = option
        blockstate(name, variants)
        write(ROOT / "models" / "item" / (name + ".json"), {"parent": "imttech:block/" + name + "_on"})


def camera_monitor():
    """
    De Camera Monitor (19 oktober): een plat scherm van 3 pixels dik, tegen de achterkant van zijn blok (naar het noorden: het
    scherm op z = 13). Monitoren die tegen elkaar staan, zijn samen één scherm: daarom een randje per kant, en alleen aan de
    kanten waar geen monitor tegenaan zit ("up", "down", "left" en "right" in de blockstate). Het beeld zelf tekent
    CameraWallRenderer, net voor het scherm.
    """
    textures = {"particle": "imttech:block/camera_monitor_case", "case": "imttech:block/camera_monitor_case"}
    panel = dict(textures)
    for on in (False, True):
        panel["screen"] = "imttech:block/camera_monitor_screen" + ("_on" if on else "")
        element = box([0, 0, 13], [16, 16, 16], {"north": "#screen", "rest": "#case"})
        if on:
            element["faces"]["north"]["shade"] = False
            element["faces"]["north"]["neoforge_data"] = {"block_light": 15, "sky_light": 15}
        save("camera_monitor" + ("_on" if on else ""), dict(panel), [element])
    edges = {"up": ([0, 15, 12.5], [16, 16, 13]), "down": ([0, 0, 12.5], [16, 1, 13]),
             "left": ([15, 0, 12.5], [16, 16, 13]), "right": ([0, 0, 12.5], [1, 16, 13])}
    for side, (frm, to) in edges.items():
        save("camera_monitor_edge_" + side, textures, [box(frm, to, "#case", skip=("south",))])
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    parts = []

    def part(model, y, **when):
        apply = {"model": "imttech:block/" + model}
        if y:
            apply["y"] = y
        parts.append({"when": when, "apply": apply})

    for facing, y in turn.items():
        part("camera_monitor", y, facing=facing, on="false")
        part("camera_monitor_on", y, facing=facing, on="true")
        for side in edges:
            part("camera_monitor_edge_" + side, y, facing=facing, **{side: "false"})
    path = ROOT / "blockstates" / "camera_monitor.json"
    path.write_text(json.dumps({"multipart": parts}, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("geschreven:", path.relative_to(ROOT.parent.parent.parent.parent.parent))
    # Het item: één monitor met zijn hele rand, aan.
    item = dict(panel)
    element = box([0, 0, 13], [16, 16, 16], {"north": "#screen", "rest": "#case"})
    save("camera_monitor_item", item, [element] + [box(frm, to, "#case", skip=("south",)) for frm, to in edges.values()])
    write(ROOT / "models" / "item" / "camera_monitor.json", {"parent": "imttech:block/camera_monitor_item"})


def write(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("geschreven:", path.relative_to(ROOT.parent.parent.parent.parent.parent))


if __name__ == "__main__":
    solar_panel()
    heat_generator(False)
    heat_generator(True)
    heat_generator_states()
    laser_wall_models()
    laser_bridge_models()
    arc_lamp()
    light_switch()
    network_controls()
    robot_dock()
    computer_and_monitor()
    camera_monitor()
