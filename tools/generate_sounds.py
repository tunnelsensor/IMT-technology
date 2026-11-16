"""
Maakt de eigen geluiden van I.M.T Technology (.ogg) uit golven en ruis, zonder opnames.

Gebruik (eenmalig: pip install numpy scipy soundfile):
    python tools/generate_sounds.py               # alle geluiden
    python tools/generate_sounds.py imt_boot    # alleen deze

De bestanden komen in src/main/resources/assets/imttech/sounds/. Minecraft wil mono
Ogg Vorbis: alleen mono-geluiden klinken van een plek in de wereld (links/rechts, ver/dichtbij).

Elk geluid is een functie die een numpy-array teruggeeft met waarden tussen -1 en 1.
"""
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy import signal

RATE = 44100
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/imttech/sounds"
rng = np.random.default_rng(1234)  # vaste "toevalsgenerator": elke keer hetzelfde geluid


# ---------------------------------------------------------------- bouwstenen

def seconds(duration):
    """De tijdas: een rij tijdstippen van 0 tot duration."""
    return np.arange(int(RATE * duration)) / RATE


def sweep(start_hz, end_hz, duration, curve=1.0):
    """
    Een toon die van start_hz naar end_hz glijdt. We tellen de fase op in plaats van
    sin(2*pi*f*t) te nemen; anders krijg je rare sprongen als de toonhoogte verandert.
    curve > 1: eerst langzaam, dan snel.
    """
    t = seconds(duration)
    progress = (t / duration) ** curve
    freq = start_hz * (end_hz / start_hz) ** progress  # exponentieel klinkt natuurlijker
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    return phase


def noise(duration):
    return rng.uniform(-1.0, 1.0, int(RATE * duration))


def bandpass(sound, low, high, order=4):
    sos = signal.butter(order, [low, high], btype="bandpass", fs=RATE, output="sos")
    return signal.sosfilt(sos, sound)


def lowpass(sound, cutoff, order=4):
    sos = signal.butter(order, cutoff, btype="lowpass", fs=RATE, output="sos")
    return signal.sosfilt(sos, sound)


def decay(duration, speed):
    """Een omhullende die vanaf 1 uitsterft; hoe groter speed, hoe sneller."""
    return np.exp(-seconds(duration) * speed)


def fade(sound, fade_in=0.005, fade_out=0.02):
    """Zachte begin- en eindrand, anders hoor je een tik."""
    sound = sound.copy()
    n_in = max(1, int(RATE * fade_in))
    n_out = max(1, int(RATE * fade_out))
    sound[:n_in] *= np.linspace(0, 1, n_in)
    sound[-n_out:] *= np.linspace(1, 0, n_out)
    return sound


def place(total, part, at):
    """Zet `part` in `total` vanaf tijdstip `at` (in seconden), opgeteld bij wat er al is."""
    start = int(RATE * at)
    end = min(len(total), start + len(part))
    total[start:end] += part[: end - start]
    return total


def mix(*parts):
    """Telt geluiden van verschillende lengte bij elkaar op (de kortere vullen we aan met stilte)."""
    total = np.zeros(max(len(part) for part in parts))
    for part in parts:
        total[:len(part)] += part
    return total


def finish(sound, peak=0.8):
    """Zacht afkappen (geen harde vervorming) en op een vaste luidheid zetten."""
    sound = np.tanh(sound * 1.4)
    sound = sound / np.max(np.abs(sound)) * peak
    return fade(sound).astype(np.float32)


def whine(start_hz, end_hz, duration, curve=1.0, wobble=0.0):
    """
    Het typische "gezoem" van een laser cannon: een toon met een paar boventonen erbij, en
    eventueel een snelle trilling (wobble) zodat het elektrisch klinkt.
    """
    phase = sweep(start_hz, end_hz, duration, curve)
    tone = np.sin(phase) + 0.35 * np.sin(2 * phase) + 0.15 * np.sin(3 * phase + 0.5)
    if wobble:
        t = seconds(duration)
        tone *= 1.0 + wobble * np.sin(2 * np.pi * 38 * t)
    return tone


# ---------------------------------------------------------------- de geluiden

def laser_cannon_arm():
    """
    De laser cannon gaat aan: een klik in de handschoen, dan een gezoem dat omhoog loopt tot een
    hoge, trillende toon ("klaar om te vuren").
    """
    total = np.zeros(int(RATE * 0.9))
    click = bandpass(noise(0.03), 1500, 7000) * decay(0.03, 160) * 0.9
    thump = np.sin(sweep(160, 60, 0.12)) * decay(0.12, 35) * 0.8
    rise = whine(320, 2300, 0.75, curve=0.7, wobble=0.18)
    rise *= np.linspace(0.15, 1.0, len(rise)) ** 1.5 * np.exp(-seconds(0.75) * 0.6)
    hiss = bandpass(noise(0.75), 3000, 9000) * np.linspace(0, 0.18, int(RATE * 0.75))
    place(total, click, 0.0)
    place(total, thump, 0.0)
    place(total, fade(rise * 0.55 + hiss, 0.01, 0.12), 0.04)
    return finish(total)


def laser_cannon_disarm():
    """De laser cannon gaat uit: het gezoem zakt weg en de handschoen klikt dicht."""
    total = np.zeros(int(RATE * 0.6))
    fall = whine(2100, 260, 0.5, curve=0.6, wobble=0.12) * decay(0.5, 4.5)
    click = bandpass(noise(0.025), 1200, 6000) * decay(0.025, 180) * 0.7
    place(total, fade(fall * 0.55, 0.005, 0.1), 0.0)
    place(total, click, 0.42)
    return finish(total, peak=0.75)


def laser_cannon_fire():
    """
    Een laserkanonschot zoals in de films: een heel kort gezoem dat omhoog schiet, en dan de
    knal - een felle laser-"zap", een dreun die je in je buik voelt en een naklank.
    """
    total = np.zeros(int(RATE * 1.2))
    # 1. Het oplaadgezoem vlak voor het schot (0,12 s).
    charge = whine(1400, 3600, 0.12, curve=1.6) * np.linspace(0.3, 1.0, int(RATE * 0.12))
    place(total, fade(charge * 0.45, 0.005, 0.005), 0.0)
    shot = 0.11
    # 2. De zap: een toon die razendsnel omlaag glijdt, met frequentiemodulatie voor de "bite".
    t = seconds(0.32)
    zap_phase = sweep(3200, 280, 0.32, curve=0.45)
    zap = np.sin(zap_phase + 2.2 * np.sin(2 * np.pi * 90 * t)) * decay(0.32, 9)
    place(total, fade(zap * 0.7, 0.002, 0.05), shot)
    # 3. De knal: een fel stukje ruis, het "psjoew".
    crack = bandpass(noise(0.2), 1800, 9000) * decay(0.2, 28)
    place(total, fade(crack * 1.1, 0.002, 0.03), shot)
    # 4. De dreun: een lage toon die omlaag zakt.
    boom = np.sin(sweep(140, 38, 0.45, curve=0.7)) * decay(0.45, 8)
    place(total, fade(boom * 1.3, 0.002, 0.08), shot)
    # 5. De naklank: dof gerommel dat uitsterft.
    rumble = lowpass(noise(1.0), 700) * decay(1.0, 4.2)
    place(total, fade(rumble * 0.9, 0.01, 0.2), shot + 0.02)
    # 6. Een hoge, elektrische nagalm: het gezoem van de handschoen die weer oplaadt.
    tail = whine(2400, 1500, 0.6, wobble=0.25) * decay(0.6, 6) * 0.12
    place(total, fade(tail, 0.02, 0.1), shot + 0.12)
    # Wat stiller dan de rest: na het omzetten naar .ogg schoot de piek anders boven 1 uit.
    return finish(total, peak=0.7)


def suit_thruster():
    """
    De stuwraketten van het harnas. Dit geluid speelt als lus zolang je vliegt: een diep
    gebrul van straalmotoren, gesis, en een hoog elektrisch gezoem erdoor, met een langzaam
    flakkeren zoals een vlam.

    Een lus mag geen naad hebben. Daarom maken we hem wat langer dan nodig en laten we het
    stuk ná het einde over het begin heen vloeien: waar het einde terugspringt naar het begin,
    klinkt het begin dan precies als het vervolg van het einde.
    """
    length, overlap = 2.0, 0.4
    total = length + overlap
    t = seconds(total)
    roar = bandpass(noise(total), 90, 2400, order=2)
    hiss = bandpass(noise(total), 2500, 8000) * 0.25
    rumble = lowpass(noise(total), 140) * 2.5
    flutter = 1.0 + 0.12 * np.sin(2 * np.pi * 3.0 * t) + 0.06 * np.sin(2 * np.pi * 7.3 * t)
    buzz = 0.06 * np.sin(2 * np.pi * 880 * t + 0.8 * np.sin(2 * np.pi * 5 * t)) + 0.02 * np.sin(2 * np.pi * 1760 * t)
    sound = (roar + hiss + rumble) * flutter + buzz

    n, m = int(RATE * length), int(RATE * overlap)
    loop = sound[:n].copy()
    ramp = np.linspace(0.0, 1.0, m)
    loop[:m] = sound[n:n + m] * (1.0 - ramp) + loop[:m] * ramp
    # Geen fade() hier: een zachte rand zou je bij elke herhaling horen als een hikje.
    loop = np.tanh(loop * 1.2)
    return (loop / np.max(np.abs(loop)) * 0.7).astype(np.float32)


def chime(freq, duration, decay_speed=5.0):
    """Een zuivere, glinsterende toon met een boventoon: een belletje."""
    t = seconds(duration)
    tone = np.sin(2 * np.pi * freq * t) + 0.3 * np.sin(2 * np.pi * freq * 2 * t)
    shimmer = 1.0 + 0.15 * np.sin(2 * np.pi * 6 * t)
    return tone * shimmer * decay(duration, decay_speed)


def imt_boot():
    """
    De bril gaat aan, als een arc reactor: een elektrisch gejank dat oplaadt zoals een
    condensator (steeds hoger en luider, met geknetter), dan een diepe klap als de reactor
    aanslaat en een warme brom die blijft hangen. (Jouw keuze "D" uit de voorbeelden.)
    """
    total = np.zeros(int(RATE * 3.2))
    whir = whine(180, 3000, 1.5, curve=1.4, wobble=0.35) * np.linspace(0.1, 1, int(RATE * 1.5)) ** 1.5 * 0.35
    crackle = bandpass(noise(1.5), 3000, 10000) * np.linspace(0, 1, int(RATE * 1.5)) ** 3 * 0.25
    place(total, fade(whir + crackle, 0.05, 0.01), 0.0)
    thunk = np.sin(sweep(140, 45, 0.5, curve=0.6)) * decay(0.5, 7) * 1.3
    t = seconds(1.6)
    hum = (np.sin(2 * np.pi * 98 * t) + 0.5 * np.sin(2 * np.pi * 196 * t) + 0.25 * np.sin(2 * np.pi * 392 * t))         * decay(1.6, 1.8) * (1 + 0.15 * np.sin(2 * np.pi * 6 * t)) * 0.35
    ring = np.sin(2 * np.pi * 1568 * t) * decay(1.6, 3) * 0.12
    place(total, fade(thunk, 0.001, 0.1), 1.5)
    place(total, fade(hum + ring, 0.005, 0.4), 1.5)
    return finish(total, peak=0.7)


def imt_shutdown():
    """
    Hoofdstuk 96: de batterij van de bril is leeg en hij valt uit. Het omgekeerde van imt_boot, maar zacht: de warme brom
    zakt weg (zoals een condensator die leegloopt), met een klein tikje als het beeld uitgaat. Geen geknetter: het moet
    rustig klinken, niet als een EMP (dat is emp_power_down).
    """
    duration = 1.1
    t = seconds(duration)
    drop = sweep(196, 55, duration, curve=0.6)
    hum = (np.sin(drop) + 0.45 * np.sin(2 * drop) + 0.2 * np.sin(3 * drop)) * decay(duration, 2.6)
    whine_down = whine(1400, 180, 0.6, curve=0.7) * decay(0.6, 5) * 0.18
    tick = bandpass(noise(0.03), 1500, 5000) * decay(0.03, 120) * 0.35
    total = np.zeros(int(RATE * (duration + 0.1)))
    place(total, fade(hum * (1 + 0.1 * np.sin(2 * np.pi * 5 * t)) * 0.5, 0.01, 0.3), 0.0)
    place(total, fade(whine_down, 0.01, 0.2), 0.0)
    place(total, tick, 0.75)
    return finish(lowpass(total, 6000), peak=0.45)


def imt_blip():
    """Een klein elektrisch zapje: bij elke stap van de laadbalk."""
    t = seconds(0.05)
    zap = np.sin(sweep(3000, 1200, 0.05)) * decay(0.05, 50) + bandpass(noise(0.05), 4000, 10000) * decay(0.05, 90) * 0.4
    return finish(fade(zap, 0.001, 0.01), peak=0.35)


def imt_confirm():
    """Identiteit bevestigd: een diepe dreun, een ping die omhoog schiet en een heldere nagalm."""
    total = np.zeros(int(RATE * 1.3))
    place(total, fade(np.sin(sweep(120, 50, 0.35)) * decay(0.35, 9) * 1.1, 0.001, 0.05), 0.0)
    ping = np.sin(sweep(1046.5, 2093, 0.15, curve=0.5))
    place(total, fade(ping * 0.4, 0.002, 0.01), 0.05)
    place(total, fade(chime(2093, 1.0, 4) * 0.35, 0.002, 0.2), 0.2)
    return finish(total, peak=0.6)


# ---------------------------------------------------------------- meldingen en menu
# Alles in dezelfde toonsoort als het opstartgeluid (C, E, G, B), met zachte, glazige tonen:
# I.M.T mag je niet laten schrikken, behalve als er echt iets mis is.

def glass(freq, duration, decay_speed=8.0, bright=0.25):
    """Een zachte, glazige toon: een sinus met een vleugje FM, zodat hij niet "piept"."""
    t = seconds(duration)
    tone = np.sin(2 * np.pi * freq * t + bright * np.sin(2 * np.pi * freq * 2.0 * t))
    tone += 0.18 * np.sin(2 * np.pi * freq * 3.0 * t)
    return tone * decay(duration, decay_speed)


def imt_notify():
    """
    Een gewone melding (bijvoorbeeld "aangekomen op je bestemming"): twee zachte tonen omhoog,
    de tweede met een nagalmpje. Kort en rustig, want dit hoor je vaak.
    """
    total = np.zeros(int(RATE * 0.55))
    place(total, fade(glass(783.99, 0.22, 16) * 0.45, 0.004, 0.04), 0.0)
    place(total, fade(glass(1174.66, 0.45, 7) * 0.5, 0.004, 0.08), 0.085)
    # Een hele zachte echo, een octaaf hoger: dat maakt het "glazig".
    place(total, fade(glass(2349.3, 0.3, 12, bright=0.1) * 0.08, 0.01, 0.08), 0.1)
    return finish(total, peak=0.5)


def imt_alert():
    """Een waarschuwing: twee korte, iets scherpere tonen op dezelfde hoogte."""
    total = np.zeros(int(RATE * 0.45))
    for i in range(2):
        t = seconds(0.12)
        tone = np.sin(2 * np.pi * 987.77 * t) + 0.4 * np.sin(2 * np.pi * 987.77 * 3 * t) * decay(0.12, 30)
        place(total, fade(tone * decay(0.12, 14) * 0.5, 0.003, 0.03), i * 0.16)
    return finish(total, peak=0.5)


def imt_denied():
    """Geweigerd of mislukt: twee tonen omlaag, laag en wat brommerig."""
    total = np.zeros(int(RATE * 0.5))
    for i, freq in enumerate((392.0, 261.63)):
        t = seconds(0.2)
        tone = signal.sawtooth(2 * np.pi * freq * t) * 0.5 + np.sin(2 * np.pi * freq * t)
        tone = lowpass(tone, 1800) * decay(0.2, 9)
        place(total, fade(tone * 0.5, 0.004, 0.05), i * 0.17)
    return finish(total, peak=0.5)


def imt_scan():
    """
    De ertsen-scan: een zachte "zoef" omhoog (de scan gaat de grond in), dan een sonar-ping
    die twee keer zachter terugkaatst.
    """
    total = np.zeros(int(RATE * 1.4))
    whoosh = bandpass(noise(0.45), 400, 6000, order=2)
    whoosh *= np.sin(np.linspace(0, np.pi, len(whoosh))) ** 2
    rise = np.sin(sweep(300, 1400, 0.45, curve=0.8)) * np.linspace(0, 1, int(RATE * 0.45)) ** 2 * 0.25
    place(total, fade(whoosh * 0.35 + rise, 0.02, 0.05), 0.0)
    for i, loudness in enumerate((0.6, 0.25, 0.1)):
        place(total, fade(glass(1567.98, 0.5, 9, bright=0.4) * loudness, 0.003, 0.1), 0.42 + i * 0.24)
    return finish(total, peak=0.5)


def imt_menu_open():
    """Het G-menu gaat open: een korte, luchtige zoef omhoog met een glinstertje."""
    total = np.zeros(int(RATE * 0.35))
    air = bandpass(noise(0.2), 2000, 9000, order=2) * np.sin(np.linspace(0, np.pi, int(RATE * 0.2))) * 0.25
    rise = np.sin(sweep(700, 1600, 0.16, curve=0.7)) * decay(0.16, 10) * 0.3
    place(total, fade(mix(air, rise), 0.005, 0.05), 0.0)
    place(total, fade(glass(2093.0, 0.18, 18, bright=0.1) * 0.15, 0.003, 0.05), 0.1)
    return finish(total, peak=0.4)


def imt_select():
    """Een optie kiezen: een heel kort, zacht tikje, zoals een aanraakscherm."""
    t = seconds(0.045)
    tick = np.sin(2 * np.pi * 2600 * t) * decay(0.045, 110)
    tick += bandpass(noise(0.045), 3000, 8000) * decay(0.045, 180) * 0.3
    return finish(fade(tick, 0.001, 0.01), peak=0.35)


# ---------------------------------------------------------------- laadstation

def station_upgrade():
    """
    Een upgrade wordt ingebouwd: een servo die zoemt, een klik als het onderdeel vastzit, en
    dan het "klaar"-akkoord van I.M.T
    """
    total = np.zeros(int(RATE * 1.2))
    servo = whine(180, 420, 0.35, curve=0.8, wobble=0.25) * np.sin(np.linspace(0, np.pi, int(RATE * 0.35)))
    place(total, fade(lowpass(servo, 2500) * 0.35, 0.02, 0.05), 0.0)
    clunk = mix(np.sin(sweep(220, 90, 0.1)) * decay(0.1, 30), bandpass(noise(0.04), 1500, 6000) * decay(0.04, 120) * 0.6)
    place(total, fade(clunk * 0.7, 0.001, 0.02), 0.36)
    for freq in (523.25, 659.25, 783.99):
        place(total, fade(glass(freq, 0.7, 5) * 0.2, 0.005, 0.1), 0.48)
    return finish(total, peak=0.55)


# ---------------------------------------------------------------- drones

def rotor(duration, start_hz, end_hz):
    """Het gezoem van rotors: een snel "brrr" (een toon die heel snel aan en uit gaat)."""
    t = seconds(duration)
    speed = np.linspace(start_hz, end_hz, len(t))
    chop = 0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(speed) / RATE)
    body = bandpass(noise(duration), 150, 2500, order=2) * 0.6 + np.sin(2 * np.pi * np.cumsum(speed * 4) / RATE) * 0.2
    return body * chop


def drone_deploy():
    """Een drone vertrekt: rotors die opspinnen en een zoef omhoog."""
    total = np.zeros(int(RATE * 0.9))
    spin = rotor(0.8, 12, 45) * np.linspace(0.2, 1, int(RATE * 0.8)) * decay(0.8, 1.5)
    place(total, fade(spin * 0.6, 0.02, 0.15), 0.0)
    whoosh = bandpass(noise(0.5), 600, 5000, order=2) * np.sin(np.linspace(0, np.pi, int(RATE * 0.5))) ** 2
    place(total, fade(whoosh * 0.35, 0.02, 0.1), 0.3)
    return finish(total, peak=0.55)


def drone_laser():
    """Een droneschot: een korte, heldere "pew" die snel omlaag glijdt."""
    phase = sweep(2600, 700, 0.18, curve=0.5)
    zap = (np.sin(phase) + 0.3 * np.sin(2 * phase)) * decay(0.18, 18)
    crack = bandpass(noise(0.05), 3000, 9000) * decay(0.05, 80) * 0.4
    total = np.zeros(int(RATE * 0.22))
    place(total, fade(zap * 0.6, 0.001, 0.03), 0.0)
    place(total, crack, 0.0)
    return finish(total, peak=0.5)


def drone_recall():
    """Een drone gaat terug naar zijn baan: rotors die wegzakken en een zacht blipje omlaag."""
    total = np.zeros(int(RATE * 0.8))
    spin = rotor(0.7, 40, 14) * decay(0.7, 3)
    place(total, fade(spin * 0.5, 0.01, 0.15), 0.0)
    place(total, fade(np.sin(sweep(1318.5, 783.99, 0.25)) * decay(0.25, 8) * 0.3, 0.003, 0.05), 0.08)
    return finish(total, peak=0.5)


def drone_hurt():
    """Een drone wordt geraakt: een metalen tik en wat knetterende vonken."""
    total = np.zeros(int(RATE * 0.35))
    t = seconds(0.2)
    clank = sum(np.sin(2 * np.pi * f * t) * decay(0.2, d) for f, d in ((820, 25), (1370, 30), (2250, 40)))
    place(total, fade(clank * 0.35, 0.001, 0.03), 0.0)
    for i in range(5):
        at = 0.03 + rng.uniform(0, 0.25)
        spark = bandpass(noise(0.015), 3000, 10000) * decay(0.015, 200) * rng.uniform(0.2, 0.5)
        place(total, spark, at)
    return finish(total, peak=0.55)


def drone_destroyed():
    """Een drone ontploft: een doffe knal, rondvliegende metalen brokjes en elektrisch gesis."""
    total = np.zeros(int(RATE * 1.6))
    boom = np.sin(sweep(120, 35, 0.6, curve=0.6)) * decay(0.6, 6)
    blast = lowpass(noise(1.0), 1200) * decay(1.0, 5)
    crack = bandpass(noise(0.2), 1500, 8000) * decay(0.2, 25)
    place(total, fade(mix(boom * 1.2, crack * 0.8), 0.001, 0.1), 0.0)
    place(total, fade(blast * 0.8, 0.001, 0.2), 0.0)
    for i in range(7):
        t = seconds(0.12)
        f = rng.uniform(900, 2600)
        ping = np.sin(2 * np.pi * f * t) * decay(0.12, 35) * rng.uniform(0.08, 0.2)
        place(total, fade(ping, 0.001, 0.02), 0.15 + rng.uniform(0, 0.9))
    fizz = bandpass(noise(0.8), 4000, 10000) * decay(0.8, 4) * np.abs(np.sin(np.linspace(0, 40, int(RATE * 0.8))))
    place(total, fade(fizz * 0.25, 0.05, 0.2), 0.3)
    return finish(total, peak=0.75)


# ---------------------------------------------------------------- satelliet

def satellite_lock():
    """Doel vergrendeld: drie piepjes die steeds sneller komen, en dan een lange, hoge toon."""
    total = np.zeros(int(RATE * 0.9))
    for at in (0.0, 0.16, 0.28):
        t = seconds(0.07)
        place(total, fade(np.sin(2 * np.pi * 1760 * t) * decay(0.07, 25) * 0.45, 0.002, 0.02), at)
    t = seconds(0.5)
    hold = np.sin(2 * np.pi * 2093 * t) * 0.4 + np.sin(2 * np.pi * 2093 * 1.5 * t) * 0.12
    place(total, fade(hold * decay(0.5, 3), 0.005, 0.12), 0.38)
    return finish(total, peak=0.5)


def satellite_charge():
    """
    De satelliet laadt op: een diepe brom die aanzwelt en steeds hoger gaat zingen, met een
    snelle trilling erin. Klinkt op de plek van de inslag, dus iedereen daar hoort hem komen.
    """
    duration = 2.8
    t = seconds(duration)
    build = np.linspace(0, 1, len(t)) ** 1.6
    hum = np.sin(sweep(55, 110, duration)) * 0.8
    sing = whine(220, 1760, duration, curve=1.8, wobble=0.3) * build * 0.45
    crackle = bandpass(noise(duration), 2500, 9000) * build ** 2 * 0.25
    tremolo = 1.0 + 0.25 * np.sin(2 * np.pi * (4 + 14 * build) * t)
    return finish(fade((hum + sing + crackle) * tremolo * (0.3 + 0.7 * build), 0.3, 0.05), peak=0.55)


def satellite_fire():
    """
    De inslag: een enorme dreun, een felle straal die omlaag zingt, en een lang gerommel dat
    wegtrekt. Dit is het grootste geluid van de mod.
    """
    total = np.zeros(int(RATE * 3.0))
    beam_phase = sweep(4200, 180, 0.7, curve=0.5)
    beam = (np.sin(beam_phase) + 0.4 * np.sin(1.5 * beam_phase)) * decay(0.7, 4)
    place(total, fade(beam * 0.6, 0.002, 0.1), 0.0)
    boom = np.sin(sweep(90, 28, 1.2, curve=0.5)) * decay(1.2, 3.2)
    place(total, fade(boom * 1.5, 0.002, 0.2), 0.02)
    crack = bandpass(noise(0.3), 1000, 9000) * decay(0.3, 14)
    place(total, fade(crack * 1.2, 0.001, 0.05), 0.0)
    rumble = lowpass(noise(2.8), 400) * decay(2.8, 1.4)
    place(total, fade(rumble * 1.4, 0.05, 0.5), 0.05)
    # Niet voller dan 0,65: na het omzetten naar .ogg schiet de piek anders boven 1 uit (kraken).
    return finish(total, peak=0.65)


def satellite_missile():
    """
    Een raket uit de ruimte komt neer: een fluittoon die zakt (zoals iets dat op je af valt),
    met aanzwellend geraas van lucht en een brullende motor eronder. Duurt ongeveer zo lang als
    de raket valt; klinkt op de plek van het doel, dus daar hoor je hem aankomen.
    """
    duration = 1.7
    t = seconds(duration)
    grow = np.linspace(0.12, 1, len(t)) ** 1.6
    whistle = np.sin(sweep(2600, 650, duration, curve=1.4)) * 0.3
    rush = bandpass(noise(duration), 700, 5000) * 0.55
    roar = lowpass(noise(duration), 220) * 1.1
    return finish(fade((whistle + rush + roar) * grow, 0.25, 0.02), peak=0.6)


def satellite_impact():
    """
    De raket slaat in: een scherpe knal, een diepe dreun, een drukgolf van ruis en een lang
    gerommel met wat neervallend puin. Zonder de zingende straal van satellite_fire.
    """
    total = np.zeros(int(RATE * 3.2))
    crack = bandpass(noise(0.25), 800, 9000) * decay(0.25, 16)
    place(total, fade(crack * 1.3, 0.001, 0.05), 0.0)
    boom = np.sin(sweep(80, 25, 1.4, curve=0.5)) * decay(1.4, 2.8)
    place(total, fade(boom * 1.7, 0.002, 0.2), 0.01)
    blast = lowpass(noise(0.8), 1200) * decay(0.8, 5)
    place(total, fade(blast * 1.2, 0.002, 0.1), 0.0)
    rumble = lowpass(noise(3.0), 300) * decay(3.0, 1.2)
    place(total, fade(rumble * 1.4, 0.05, 0.5), 0.05)
    debris = bandpass(noise(1.5), 2000, 7000) * decay(1.5, 3) * (rng.uniform(0, 1, int(RATE * 1.5)) > 0.97)
    place(total, debris * 0.8, 0.3)
    return finish(total, peak=0.65)


# ---------------------------------------------------------------- comms

def comms_message():
    """Een bericht van een andere I.M.T-drager: drie snelle, heldere tonen omlaag en weer op."""
    total = np.zeros(int(RATE * 0.6))
    for i, freq in enumerate((1318.5, 1046.5, 1567.98)):
        place(total, fade(glass(freq, 0.25, 12) * 0.4, 0.003, 0.05), i * 0.09)
    return finish(total, peak=0.5)


def raven_radio():
    """
    De radio van een Raven-soldaat (hoofdstuk 60): een klik, een korte squelch van ruis en een piepje: er begint een
    gesprek. Je hoort hem bij de soldaat die roept, en zachtjes bij jezelf als de coms iets zeggen.
    """
    total = np.zeros(int(RATE * 0.7))
    place(total, fade(crackle(0.3, 1.0, 1200, 5000) * 1.6, 0.003, 0.05), 0.0)
    place(total, fade(bandpass(noise(0.12), 900, 3500) * 0.35, 0.005, 0.04), 0.3)
    place(total, fade(short_beep(1760, 0.08) * 0.35, 0.003, 0.03), 0.47)
    return finish(total, peak=0.45)


def comms_ping():
    """Een ping op de kaart: een sonar-"ping" met een echo die wegsterft."""
    total = np.zeros(int(RATE * 1.0))
    for i, loudness in enumerate((0.6, 0.3, 0.12)):
        place(total, fade(glass(1046.5, 0.45, 8, bright=0.6) * loudness, 0.002, 0.1), i * 0.2)
    return finish(total, peak=0.5)


# ---------------------------------------------------------------- rayshield (Star Wars-achtig)
#
# Jouw wens (7 oktober): de rayshields moeten klinken zoals de laserpoorten in Star Wars. Echte
# Star Wars-geluiden mogen niet in de mod (auteursrecht), dus maken we het na: een brommende,
# elektrische zoemtoon (twee zaagtanden die net iets anders gestemd zijn, zodat ze tegen elkaar
# "zweven"), met af en toe een knetter erdoor.

def ray_buzz(phase):
    """
    De zoemtoon van een rayshield bij een gegeven fase (zie sweep): een zaagtand met een sinus
    eronder, en een tweede laag een fractie hoger. Afgezwakt boven 1600 Hz, anders snerpt het.
    """
    tone = signal.sawtooth(phase) * 0.55 + np.sin(phase)
    tone += signal.sawtooth(phase * 1.011 + 1.3) * 0.45
    return lowpass(tone, 1600, order=2)


def crackle(duration, density, low=1800, high=7000):
    """Knetteren: korte tikjes ruis op willekeurige momenten ({density} = hoe vaak, 0 tot 1)."""
    gate = rng.uniform(0, 1, int(RATE * duration)) < density / 400
    clicks = np.convolve(gate.astype(float), np.exp(-np.arange(int(RATE * 0.006)) / (RATE * 0.0015)))
    return bandpass(noise(duration), low, high) * clicks[: int(RATE * duration)]


def rayshield_hum():
    """
    Het zoemen van een laserwand of koepel. Het spel speelt het af en toe een paar seconden (zie
    RayshieldHum), als lus, dus net als de stuwraketten (suit_thruster) zonder naad: het stuk na het
    einde vloeit over het begin heen.

    Jouw wens (7 oktober): zachter. Daarom een rustige brom: 92 Hz en 92,5 Hz zweven maar één keer
    per twee seconden tegen elkaar (een trage "wom"), bijna geen knetter, en de hoge tonen eraf.
    """
    length, overlap = 2.0, 0.4
    total = length + overlap
    t = seconds(total)
    buzz = ray_buzz(2 * np.pi * 92 * t) + 0.8 * ray_buzz(2 * np.pi * 92.5 * t + 0.7)
    sub = 0.6 * np.sin(2 * np.pi * 46 * t)
    shimmer = 1.0 + 0.04 * np.sin(2 * np.pi * 6 * t)
    sound = (buzz + sub) * shimmer + crackle(total, 0.02) * 1.2
    sound = lowpass(sound, 1000, order=2)

    n, m = int(RATE * length), int(RATE * overlap)
    loop = sound[:n].copy()
    ramp = np.linspace(0.0, 1.0, m)
    loop[:m] = sound[n:n + m] * (1.0 - ramp) + loop[:m] * ramp
    loop = np.tanh(loop * 0.9)
    return (loop / np.max(np.abs(loop)) * 0.4).astype(np.float32)


def alarm_siren():
    """
    De ingebouwde sirene van de Alarm Speaker (hoofdstuk 126): wat hij laat horen zolang niemand een eigen geluid uploadde.
    Een elektronische "wail": de toon glijdt in 0,85 s van 650 naar 1400 Hz omhoog en valt in 0,15 s terug, twee keer
    in een lus van 2 seconden. Een blokgolf (oneven boventonen) met de scherpste randjes eraf, zoals een echte sirene.

    Het spel speelt hem als lus. Geen naad: we schuiven de toonhoogte een heel klein beetje zodat er precies een heel
    aantal golven in de lus past; dan sluit het einde van de golf naadloos aan op het begin.
    """
    length = 2.0
    t = seconds(length)
    part = (t % 1.0)
    rise = np.clip(part / 0.85, 0.0, 1.0)
    fall = np.clip((part - 0.85) / 0.15, 0.0, 1.0)
    shape = np.where(part < 0.85, np.sin(rise * np.pi / 2) ** 1.5, np.cos(fall * np.pi / 2) ** 2)
    freq = 650.0 + 750.0 * shape
    cycles = np.sum(freq) / RATE
    freq *= np.round(cycles) / cycles  # precies een heel aantal golven in de lus
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    phase -= phase[0]
    tone = np.sin(phase) + 0.33 * np.sin(3 * phase) + 0.2 * np.sin(5 * phase) + 0.12 * np.sin(7 * phase)
    # Het filter "onthoudt" wat ervoor kwam: filter drie lussen achter elkaar en houd de middelste, dan klopt ook de naad.
    n = len(tone)
    tone = lowpass(np.tile(tone, 3), 4500, order=2)[n:2 * n]
    tone = np.tanh(tone * 1.2)
    return (tone / np.max(np.abs(tone)) * 0.8).astype(np.float32)


def rayshield_on():
    """
    Het schild gaat aan: "vwoem". Een knetter, dan schiet de zoemtoon van heel laag omhoog naar
    zijn gewone toon, met een zucht van ruis erbij.
    """
    duration = 0.9
    total = np.zeros(int(RATE * duration))
    phase = sweep(35, 92, duration, curve=0.3)
    swell = np.minimum(1.0, seconds(duration) / 0.1) * (1.0 - 0.45 * seconds(duration) / duration)
    place(total, ray_buzz(phase) * swell, 0.0)
    whoosh = bandpass(noise(0.5), 250, 2500) * np.sin(np.linspace(0, np.pi, int(RATE * 0.5))) ** 2
    place(total, whoosh * 1.4, 0.0)
    place(total, crackle(0.15, 6) * 4, 0.0)
    return finish(total, peak=0.45)


def rayshield_off():
    """Het schild gaat uit: de zoemtoon zakt weg naar beneden en sterft uit, met een laatste knetter."""
    duration = 0.7
    total = np.zeros(int(RATE * duration))
    phase = sweep(92, 28, duration, curve=0.7)
    place(total, ray_buzz(phase) * decay(duration, 4.5), 0.0)
    place(total, crackle(0.12, 4) * 3, 0.0)
    return finish(total, peak=0.45)


def rayshield_hit():
    """
    Iets raakt het schild (of jij loopt tegen een laserwand): een elektrische knal, een korte
    brom en een regen van vonkjes die wegsterft.
    """
    duration = 0.5
    total = np.zeros(int(RATE * duration))
    zap = bandpass(noise(0.05), 1200, 8000) * decay(0.05, 60)
    place(total, zap * 1.6, 0.0)
    thump = ray_buzz(sweep(150, 60, 0.25, curve=0.6)) * decay(0.25, 14)
    place(total, thump * 1.2, 0.0)
    sparks = crackle(duration, 10) * decay(duration, 7) * 4
    place(total, sparks, 0.01)
    return finish(total, peak=0.5)


# ---------------------------------------------------------------- De Remote Frag en de triggers (eerst van C4)

def short_beep(freq, duration):
    """Een kort, hoog, een beetje schel piepje (een sinus met wat blokgolf erin)."""
    t = seconds(duration)
    tone = np.sin(2 * np.pi * freq * t) + 0.35 * np.sign(np.sin(2 * np.pi * freq * t))
    envelope = np.minimum(1, t / 0.003) * np.minimum(1, (duration - t) / 0.012)
    return lowpass(tone, 7000) * envelope


def remote_frag_defuse():
    """De draad van een Remote Frag doorknippen: een metalen knip, en een dalend toontje (de lampjes gaan uit)."""
    total = np.zeros(int(RATE * 0.6))
    snip = bandpass(noise(0.03), 2500, 9000) * decay(0.03, 200)
    place(total, snip * 2.0, 0.0)
    place(total, snip * 1.2, 0.05)
    tone = np.sin(sweep(1800, 450, 0.35)) * decay(0.35, 4)
    place(total, tone * 0.6, 0.13)
    return finish(total, peak=0.6)


def trigger_click():
    """De knop van een trigger: een stevige klik, dan een kort radiotoontje met wat ruis (het signaal)."""
    total = np.zeros(int(RATE * 0.35))
    click = bandpass(noise(0.025), 800, 5000) * decay(0.025, 250)
    place(total, click * 2.0, 0.0)
    chirp = np.sin(sweep(1200, 2600, 0.12)) * fade(np.ones(int(RATE * 0.12)), 0.01, 0.03)
    place(total, chirp * 0.5, 0.05)
    hiss = bandpass(noise(0.18), 2000, 6000) * decay(0.18, 12) * 0.25
    place(total, hiss, 0.05)
    return finish(total, peak=0.6)


# ---------------------------------------------------------------- Guard robots (hoofdstuk 21)

def servo(duration, start_hz, end_hz):
    """Een servomotortje: een zoemende zaagtand die van toon verandert, met de hoge tonen eraf (mechanisch)."""
    phase = sweep(start_hz, end_hz, duration)
    tone = signal.sawtooth(phase) * 0.6 + np.sin(phase * 2) * 0.3
    return lowpass(tone, 2200)


def metal_tap(duration, freqs, speed):
    """Een tikje op metaal: een paar ongelijke tonen die snel uitsterven, met een randje ruis."""
    t = seconds(duration)
    ring = sum(np.sin(2 * np.pi * f * t) / (i + 1) for i, f in enumerate(freqs))
    hit = bandpass(noise(duration), 1500, 7000) * decay(duration, speed * 3)
    return (ring * 0.6 + hit) * decay(duration, speed)


def robot_step():
    """Een stap: een kort servo-ritje (het been zwaait naar voren) en een licht metalen tikje (de voet)."""
    total = np.zeros(int(RATE * 0.3))
    swing = servo(0.16, 260, 340) * np.sin(np.linspace(0, np.pi, int(RATE * 0.16)))
    place(total, fade(swing * 0.35, 0.01, 0.03), 0.0)
    place(total, metal_tap(0.14, [1350, 2210, 3170], 38) * 0.9, 0.15)
    return finish(total, peak=0.5)


def robot_power_on():
    """Aanzetten: de servo's lopen op (een stijgende zoem) en twee oplopende piepjes: "klaar"."""
    total = np.zeros(int(RATE * 0.85))
    rise = servo(0.5, 160, 720) * np.minimum(1, seconds(0.5) / 0.08)
    place(total, fade(rise * 0.5, 0.01, 0.06), 0.0)
    place(total, fade(np.sin(2 * np.pi * 880 * seconds(0.08)) * 0.35, 0.003, 0.02), 0.5)
    place(total, fade(np.sin(2 * np.pi * 1320 * seconds(0.12)) * 0.35, 0.003, 0.04), 0.62)
    return finish(total, peak=0.55)


def robot_power_off():
    """Uitzetten: de zoem zakt weg naar beneden, en een doffe klonk (hij zakt in zijn stand)."""
    total = np.zeros(int(RATE * 0.8))
    fall = servo(0.55, 720, 110) * decay(0.55, 2.5)
    place(total, fade(fall * 0.5, 0.005, 0.08), 0.0)
    clunk = np.sin(sweep(140, 60, 0.2)) * decay(0.2, 18)
    place(total, clunk * 0.9 + metal_tap(0.2, [420, 980], 25)[: len(clunk)] * 0.4, 0.52)
    return finish(total, peak=0.55)


def robot_hurt():
    """Geraakt: een harde metalen klang en een paar vonkjes."""
    total = np.zeros(int(RATE * 0.5))
    place(total, metal_tap(0.4, [520, 1310, 2470, 3610], 12) * 1.2, 0.0)
    place(total, crackle(0.35, 8) * decay(0.35, 9) * 3, 0.02)
    return finish(total, peak=0.6)


def robot_charge_hum():
    """
    Een robot die op zijn dock laadt: een heel zachte, rustige brom (100 en 200 Hz, die één keer per
    twee seconden aanzwelt) met een dun hoog toontje van de lader. Een lus zonder naad, net als
    rayshield_hum; in het spel speelt hij heel stil (zie RobotChargeHum).
    """
    length, overlap = 2.0, 0.4
    total = length + overlap
    t = seconds(total)
    hum = np.sin(2 * np.pi * 100 * t) + 0.45 * np.sin(2 * np.pi * 200 * t + 0.5) + 0.25 * signal.sawtooth(2 * np.pi * 50 * t)
    swell = 1.0 + 0.15 * np.sin(2 * np.pi * 0.5 * t)
    whine_tone = 0.05 * np.sin(2 * np.pi * 3000 * t)
    sound = lowpass(hum * swell, 900, order=2) + whine_tone

    n, m = int(RATE * length), int(RATE * overlap)
    loop = sound[:n].copy()
    ramp = np.linspace(0.0, 1.0, m)
    loop[:m] = sound[n:n + m] * (1.0 - ramp) + loop[:m] * ramp
    loop = np.tanh(loop * 0.9)
    return (loop / np.max(np.abs(loop)) * 0.4).astype(np.float32)


def drone_rotor():
    """
    De rotors van een security drone die vliegt (19 oktober; tot dan vloog hij zonder geluid): een zacht, snel "brrr".
    Vier rotors die net niet even snel draaien (40 en 41,5 keer per seconde), dus ze zweven traag tegen elkaar, met
    een lage toon van de bladen eronder. Een lus zonder naad, net als robot_charge_hum: elke toon past een heel aantal
    keer in 2 seconden, dus het einde loopt precies over in het begin. In het spel speelt hij heel stil, en iets hoger
    als de drone vliegt of stijgt (zie DroneRotorSound).
    """
    length, overlap = 2.0, 0.4
    total = length + overlap
    t = seconds(total)
    chop_a = 0.5 + 0.5 * np.sin(2 * np.pi * 40 * t)
    chop_b = 0.5 + 0.5 * np.sin(2 * np.pi * 41.5 * t + 1.1)
    air = bandpass(noise(total), 180, 2200, order=2)
    blades = 0.25 * np.sin(2 * np.pi * 160 * t) + 0.2 * np.sin(2 * np.pi * 166 * t + 0.4) + 0.15 * np.sin(2 * np.pi * 80 * t)
    sound = lowpass(air * (0.55 * chop_a + 0.45 * chop_b) + blades, 1400, order=2)

    n, m = int(RATE * length), int(RATE * overlap)
    loop = sound[:n].copy()
    ramp = np.linspace(0.0, 1.0, m)
    loop[:m] = sound[n:n + m] * (1.0 - ramp) + loop[:m] * ramp
    loop = np.tanh(loop * 0.9)
    return (loop / np.max(np.abs(loop)) * 0.4).astype(np.float32)


def robot_laser():
    """Het laserschot van een robot: een vollere, lagere "pew" dan die van een drone, met een zoemende staart."""
    phase = sweep(1800, 420, 0.24, curve=0.6)
    zap = (np.sin(phase) + 0.45 * np.sin(2 * phase) + 0.2 * signal.square(phase)) * decay(0.24, 12)
    crack = bandpass(noise(0.06), 2500, 8000) * decay(0.06, 70) * 0.5
    buzz = np.sin(2 * np.pi * 110 * seconds(0.2)) * decay(0.2, 14) * 0.25
    total = np.zeros(int(RATE * 0.3))
    place(total, fade(zap * 0.6, 0.001, 0.04), 0.0)
    place(total, crack, 0.0)
    place(total, buzz, 0.02)
    return finish(total, peak=0.55)


def robot_gun():
    """Het geweer van een robot: een droge knal (ruis met een korte dreun eronder) en een metalen klikje."""
    total = np.zeros(int(RATE * 0.35))
    bang = bandpass(noise(0.12), 300, 6000) * decay(0.12, 35)
    thump = np.sin(sweep(150, 55, 0.15)) * decay(0.15, 22)
    place(total, bang * 0.9, 0.0)
    place(total, thump * 0.8, 0.0)
    place(total, metal_tap(0.08, [2400, 3900], 60) * 0.25, 0.14)
    return finish(total, peak=0.6)


def ion_blaster():
    """De Ion Blaster: een knetterende, wiebelende "fzzzt" die snel wegzakt, met een hoge elektrische sis erover."""
    duration = 0.45
    t = seconds(duration)
    wobble = np.sin(2 * np.pi * 34 * t) * 0.35
    phase = sweep(1400, 260, duration, curve=0.7) + wobble
    buzz = signal.sawtooth(phase) * 0.5 + np.sin(phase) * 0.5
    hiss = bandpass(noise(duration), 4000, 11000) * 0.35
    crackle_burst = crackle(duration, 30) * 0.6
    total = (buzz + hiss + crackle_burst) * decay(duration, 7)
    return finish(fade(total, 0.002, 0.08), peak=0.55)



# ---------------------------------------------------------------- Wapens (hoofdstuk 35)

def emp_blast():
    """
    Een EMP-granaat gaat af: een diepe "whumpf" (een toon die naar beneden valt), een korte witte knal, een elektrische
    zoem die eruit wegtrekt, en een staart van geknetter.
    """
    duration = 1.4
    total = np.zeros(int(RATE * duration))
    thump = np.sin(sweep(110, 32, 0.6, curve=0.6)) * decay(0.6, 5)
    crack = bandpass(noise(0.08), 900, 9000) * decay(0.08, 45)
    zap = whine(900, 120, 0.7, curve=0.5, wobble=0.6) * decay(0.7, 4) * 0.35
    tail = crackle(1.2, 25, 2000, 9000) * np.linspace(1, 0, int(RATE * 1.2)) ** 1.5 * 0.9
    place(total, thump * 1.2, 0.0)
    place(total, crack * 0.9, 0.0)
    place(total, zap, 0.02)
    place(total, tail, 0.12)
    return finish(lowpass(total, 9000), peak=0.85)


def emp_power_down():
    """Een apparaat dat uitvalt: een zoemtoon die wegzakt (zoals een computer die uitvalt), met een paar vonkjes."""
    duration = 0.8
    hum = whine(520, 60, duration, curve=0.7, wobble=0.25) * decay(duration, 3.5)
    sparks = crackle(0.35, 40) * 0.6
    total = np.zeros(int(RATE * duration))
    place(total, hum * 0.7, 0.0)
    place(total, sparks, 0.0)
    return finish(lowpass(total, 5000), peak=0.5)


def emp_grenade_beep():
    """Het piepje van een EMP-granaat die gaat afgaan: kort, hoog, een beetje elektronisch."""
    return finish(short_beep(3100, 0.07), peak=0.5)


def emp_grenade_stick():
    """Een Sticky EMP Grenade plakt vast: een doffe klets, en een klikje als hij zich vastzet."""
    total = np.zeros(int(RATE * 0.3))
    splat = lowpass(noise(0.07), 1200) * decay(0.07, 40)
    click = bandpass(noise(0.015), 2500, 8000) * decay(0.015, 350)
    place(total, splat * 1.4, 0.0)
    place(total, click * 1.2, 0.08)
    place(total, short_beep(2200, 0.05) * 0.4, 0.14)
    return finish(total, peak=0.6)


def emp_grenade_bounce():
    """De bol stuitert: een korte metalen tik (hol, een beetje zoals een blikje)."""
    return finish(metal_tap(0.18, [1700, 2650, 4100], 28), peak=0.5)



def smoke_grenade_hiss():
    """
    De smokebom begint te roken: een korte plof (het dopje springt eraf) en dan een lang, luid gesis dat langzaam
    zachter wordt, met wat geruis erin (rook die eruit spuit).
    """
    duration = 3.0
    total = np.zeros(int(RATE * duration))
    pop = lowpass(noise(0.06), 900) * decay(0.06, 50)
    hiss = bandpass(noise(2.9), 2500, 9000) * np.minimum(1, seconds(2.9) / 0.08) * np.exp(-seconds(2.9) * 0.6)
    rumble = lowpass(noise(2.9), 400) * np.exp(-seconds(2.9) * 0.9) * 0.6
    place(total, pop * 1.5, 0.0)
    place(total, hiss * 0.8, 0.05)
    place(total, rumble, 0.05)
    return finish(fade(total, 0.002, 0.4), peak=0.6)


def flashbang_bang():
    """
    De flashbang: een snoeiharde, droge knal (veel hoge tonen, heel kort) met een korte echo erachter. Geen dreun zoals
    bij een explosie: het is geen bom.
    """
    total = np.zeros(int(RATE * 1.2))
    crack = bandpass(noise(0.05), 1200, 12000) * decay(0.05, 60)
    body = bandpass(noise(0.25), 300, 4000) * decay(0.25, 18)
    echo = bandpass(noise(0.6), 500, 3000) * decay(0.6, 6) * 0.25
    place(total, crack * 2.2, 0.0)
    place(total, body * 1.0, 0.0)
    place(total, echo, 0.12)
    return finish(total, peak=0.95)


def flashbang_ring():
    """
    De piep in je oren na een flashbang: een hoge, zuivere toon (iets zwevend) die 4 seconden blijft en langzaam
    wegebt, zoals tinnitus.
    """
    duration = 4.5
    t = seconds(duration)
    tone = np.sin(2 * np.pi * 3520 * t) + 0.35 * np.sin(2 * np.pi * 3528 * t)
    envelope = np.minimum(1, t / 0.05) * np.exp(-t * 0.55)
    return finish(fade(tone * envelope, 0.01, 0.5), peak=0.35)



def laser_shot(start_hz, end_hz, duration, crack, thump, bright):
    """
    Een laserschot: een toon die snel naar beneden glijdt (de "pew"), met boventonen ({bright}: hoe schel), een korte
    knetter ruis aan het begin ({crack}) en een kleine dreun eronder ({thump}).
    """
    phase = sweep(start_hz, end_hz, duration, curve=0.55)
    tone = np.sin(phase) + bright * (0.5 * np.sin(2 * phase) + 0.25 * signal.square(phase))
    total = np.zeros(int(RATE * (duration + 0.05)))
    place(total, fade(tone * decay(duration, 9 / duration * 0.25), 0.001, 0.03) * 0.7, 0.0)
    place(total, bandpass(noise(0.04), 2500, 10000) * decay(0.04, 80) * crack, 0.0)
    place(total, np.sin(sweep(160, 50, 0.12)) * decay(0.12, 25) * thump, 0.0)
    return total


def laser_pistol_fire():
    """Het laserpistool: een heldere, korte "pew"."""
    return finish(laser_shot(2600, 650, 0.16, 0.6, 0.4, 0.5), peak=0.6)


def laser_rifle_fire():
    """Het lasergeweer: een kortere, vollere "zap" (hij schiet snel achter elkaar, dus niet te lang)."""
    return finish(laser_shot(1900, 480, 0.11, 0.8, 0.7, 0.7), peak=0.6)


def laser_sniper_fire():
    """
    De lasersniper: eerst een razendsnelle oplaadtoon omhoog, dan een harde knal met een diepe dreun, en een lange
    knetterende staart (de lucht die nog natrilt).
    """
    total = np.zeros(int(RATE * 1.1))
    charge = whine(600, 3200, 0.07, curve=2.0) * np.linspace(0.2, 1, int(RATE * 0.07)) * 0.4
    place(total, charge, 0.0)
    place(total, laser_shot(3200, 260, 0.35, 1.2, 1.4, 0.9), 0.06)
    boom = np.sin(sweep(120, 35, 0.5)) * decay(0.5, 6) * 0.8
    place(total, boom, 0.06)
    place(total, crackle(0.8, 18, 1500, 8000) * np.linspace(1, 0, int(RATE * 0.8)) ** 2 * 0.6, 0.2)
    return finish(lowpass(total, 11000), peak=0.8)


def laser_shotgun_fire():
    """De laser-shotgun: een brede "wham": een paar stralen tegelijk (net verschillende tonen), een ruisstoot en een dreun."""
    total = np.zeros(int(RATE * 0.45))
    for start, end in [(1500, 300), (1700, 360), (1350, 280), (1900, 420)]:
        place(total, laser_shot(start, end, 0.22, 0.3, 0.0, 0.6) * 0.45, 0.0)
    place(total, bandpass(noise(0.18), 400, 7000) * decay(0.18, 22) * 0.9, 0.0)
    place(total, np.sin(sweep(110, 40, 0.3)) * decay(0.3, 12) * 1.0, 0.0)
    return finish(total, peak=0.75)


def laser_gun_empty():
    """Een leeg geweer: een droge klik en een piepklein zoempje dat wegvalt."""
    total = np.zeros(int(RATE * 0.25))
    place(total, metal_tap(0.06, [2800, 4300], 90) * 0.8, 0.0)
    place(total, whine(900, 300, 0.15, wobble=0.2) * decay(0.15, 15) * 0.15, 0.02)
    return finish(total, peak=0.45)


def laser_gun_cell_out():
    """De cel eruit: een klik (de klem gaat open), een korte sis, en het geschuif van de cel."""
    total = np.zeros(int(RATE * 0.5))
    place(total, metal_tap(0.07, [1900, 3100], 70), 0.0)
    place(total, bandpass(noise(0.12), 3000, 9000) * decay(0.12, 20) * 0.35, 0.03)
    place(total, bandpass(noise(0.2), 600, 2500) * np.sin(np.linspace(0, np.pi, int(RATE * 0.2))) * 0.4, 0.12)
    return finish(total, peak=0.55)


def laser_gun_cell_in():
    """De nieuwe cel erin: een schuif, een stevige klak, en een oplopende zoem (het geweer laadt op)."""
    total = np.zeros(int(RATE * 0.75))
    place(total, bandpass(noise(0.12), 600, 2500) * np.sin(np.linspace(0, np.pi, int(RATE * 0.12))) * 0.35, 0.0)
    place(total, metal_tap(0.08, [1400, 2300, 3600], 60) * 1.1, 0.12)
    up = whine(350, 1700, 0.45, curve=0.8, wobble=0.15) * np.linspace(0.3, 1, int(RATE * 0.45)) * 0.3
    place(total, fade(up, 0.01, 0.12), 0.2)
    return finish(total, peak=0.55)


def laser_gun_cycle():
    """De grendel van de sniper of de pomp van de shotgun: klik naar achteren, een kort gezoem, klik naar voren."""
    total = np.zeros(int(RATE * 0.4))
    place(total, metal_tap(0.06, [1600, 2600], 80), 0.0)
    place(total, servo(0.12, 300, 520) * 0.25, 0.04)
    place(total, metal_tap(0.07, [1300, 2200], 70) * 1.1, 0.2)
    return finish(total, peak=0.5)


def laser_gun_aim():
    """Richten door het vizier: een zacht klikje en een heel kort servootje."""
    total = np.zeros(int(RATE * 0.15))
    place(total, metal_tap(0.04, [3200, 4800], 120) * 0.6, 0.0)
    place(total, servo(0.08, 600, 800) * 0.12, 0.02)
    return finish(total, peak=0.3)



# ---------------------------------------------------------------- De vuurwapens (hoofdstuk 52, opnieuw in hoofdstuk 74)
#
# Hoofdstuk 74 (jouw wens: "die stijl van realisme en ook de sounds wil ik", zoals TACZ). Echte opnames hebben we niet,
# dus bouwen we de geluiden na zoals ze in het echt ontstaan. Wat ik van de TACZ-geluiden van hun Glock 17 heb gemeten
# (alleen gemeten, niets gekopieerd): een schot is een cluster harde knallen in de eerste 80 ms over het hele spectrum
# (veel laag-midden rond 150-500 Hz en scherp gesis boven 6 kHz), na 35 ms al 20 dB zachter, met daarna een lage galm van
# een seconde; de klikken van het mechaniek zijn helder (vooral 2 tot 16 kHz) en heel kort (20 dB weg in 20 tot 40 ms).
# De oude geluiden van hoofdstuk 52 zaten rond 1 tot 4 kHz en klonken daardoor dof.
#
# De bouwstenen:
# - impact: een tik op metaal of polymeer (een ruisstootje van 1 ms dat een paar trillingen van het onderdeel aanslaat);
# - scrape: schuiven (de slede, een magazijn in de greep): gefilterde ruis met kleine haakjes erin;
# - room: galm (vroege echo's van de grond en een muur, en een staart waarin de hoge tonen eerst wegsterven);
# - gunshot: een schot uit al die stukken.
# Elk geluid heeft zijn eigen vaste "toeval" (seed), zodat het elke keer precies hetzelfde wordt, ook als je maar één
# geluid opnieuw maakt.

def highpass(sound, cutoff, order=2):
    sos = signal.butter(order, cutoff, btype="highpass", fs=RATE, output="sos")
    return signal.sosfilt(sos, sound)


def impact(r, duration, modes, snap=1.0, snap_from=2500, snap_speed=1400, thud=0.0, thud_hz=500, thud_speed=70):
    """
    Een tik: een ruisstootje van een milliseconde (de "snap", alles boven snap_from Hz) dat een paar trillingen van het
    onderdeel aanslaat. modes = [(frequentie in Hz, hoe snel hij wegsterft, sterkte)]: staal zingt lang en hoog,
    polymeer kort en lager. thud: een doffe klap eronder (het lijf van het pistool, je hand).
    """
    n = int(RATE * duration)
    t = np.arange(n) / RATE
    burst = r.uniform(-1, 1, n) * np.exp(-t * snap_speed)
    out = highpass(burst, snap_from) * snap
    attack = 1 - np.exp(-t * 6000)
    # Een echt stuk metaal heeft niet één zuivere toon: elke trilling krijgt twee buren die iets anders klinken en
    # sneller wegsterven, en een beetje ruis op zijn sterkte. Anders klinkt een klik als een belletje.
    shimmer = 1 + 0.35 * lowpass(r.uniform(-1, 1, n), 900)
    for f, speed, amp in modes:
        for k, (df, ds, da) in enumerate(((1.0, 1.6, 1.0), (r.uniform(1.04, 1.08), 2.6, 0.45),
                                          (r.uniform(0.91, 0.95), 3.2, 0.35))):
            g = f * df * r.uniform(0.97, 1.03)
            out = out + amp * da * np.sin(2 * np.pi * g * t + r.uniform(0, 2 * np.pi)) * np.exp(-t * speed * ds) \
                * attack * shimmer
    if thud:
        body = lowpass(r.uniform(-1, 1, n), thud_hz) * np.exp(-t * thud_speed)
        out = out + body * thud * 2.5
    return out


def scrape(r, duration, low, high, ticks=350, grit=0.6, shape=None):
    """
    Schuiven: ruis door een bandfilter (low..high Hz), met kleine haakjes erin (het blijft even hangen en schiet dan
    door: "stick-slip"), en een omhullende shape(0..1) (standaard zacht op en zacht af).
    """
    n = int(RATE * duration)
    t = np.arange(n) / RATE
    hiss = bandpass(r.uniform(-1, 1, n), low, high)
    clicks = np.zeros(n)
    count = int(r.poisson(ticks * duration))
    clicks[r.integers(0, n, count)] = r.uniform(-1, 1, count)
    clicks = bandpass(clicks, low, min(high * 1.3, RATE / 2 - 500)) * 6
    env = shape(t / duration) if shape else np.sin(np.pi * t / duration) ** 0.7
    return (hiss * (1 - grit) + clicks * grit) * env


def room(sound, r, length=1.2, mix=0.35, early=((0.009, 0.45), (0.017, 0.3), (0.031, 0.22), (0.048, 0.15)),
         bright=2500, low=1200):
    """
    Galm: een paar vroege echo's (de grond, een muur dichtbij; elk een beetje doffer) en een staart die in {length}
    seconden wegsterft, waarin de hoge tonen sneller verdwijnen dan de lage (zoals buiten of in een grote hal).
    """
    n = int(RATE * length)
    t = np.arange(n) / RATE
    base = r.uniform(-1, 1, n)
    low_tail = lowpass(base, low) * np.exp(-t * 6.9 / length)
    high_tail = bandpass(base, low, bright) * np.exp(-t * 6.9 / (length * 0.35))
    ir = (low_tail * 1.2 + high_tail * 0.8) * 0.06 * (1 - np.exp(-t * 400))
    for delay, gain in early:
        k = int(delay * RATE)
        echo = lowpass(r.uniform(-1, 1, 64) * np.exp(-np.arange(64) / 9.0), 6000)
        echo[0] += 1.0
        ir[k:k + 64] += gain * echo[: max(0, min(64, n - k))]
    wet = signal.fftconvolve(sound, ir)
    out = np.zeros(len(sound) + n)
    out[:len(sound)] += sound
    out[:len(wet)] += wet[:len(out)] * mix
    return out


def finish_hard(sound, peak=0.9, fade_out=0.04):
    """Zoals finish, maar zonder de zachte aanzet van 5 ms: die zou de krak van een schot of een klik afvlakken."""
    sound = np.tanh(sound * 1.4)
    sound = sound / np.max(np.abs(sound)) * peak
    n_out = max(1, int(RATE * fade_out))
    sound[-n_out:] *= np.linspace(1, 0, n_out)
    sound[:8] *= np.linspace(0, 1, 8)
    return sound.astype(np.float32)


def gunshot(seed, far=False):
    """
    Een 9mm-schot, uit vijf stukken:
    1. de krak: de drukgolf uit de loop (een paar samples heel hard, daarna gesis boven 1 kHz, weg in 3 ms);
    2. de klap: het lijf van de knal, laag-midden met een resonantie rond 200 Hz, weg in 30 ms;
    3. de dreun: een lage toon die van 110 naar 40 Hz zakt (wat je in je borst voelt);
    4. het mechaniek: de slede die na 5 ms tegen zijn aanslag klapt en na 55 ms weer naar voren slaat;
    5. de galm: echo's van de grond en een staart van ruim een seconde.
    Ver weg (far=True, zoals de "3p"-geluiden van TACZ) hoor je alleen de lage tonen, later en met veel meer galm.
    """
    r = np.random.default_rng(seed)
    total = np.zeros(int(RATE * 0.6))
    cn = int(RATE * 0.03)
    tc = np.arange(cn) / RATE
    crack = r.uniform(-1, 1, cn) * np.exp(-tc * 420)
    crack[:4] += np.array([1.0, -0.85, 0.55, -0.3]) * 3.0
    crack = highpass(crack, 900)
    bn = int(RATE * 0.35)
    tb = np.arange(bn) / RATE
    wide = r.uniform(-1, 1, bn)
    # De klap over het hele spectrum, met het meeste laag-midden (zoals een opname: 150-500 Hz is het lijf).
    # De verhoudingen zoals in een echte opname (gemeten aan TACZ): ongeveer 40 % dreun onder 150 Hz, 10 % lijf
    # (150-500), 13 % midden, 19 % 2-6 kHz en 14 % gesis daarboven.
    blast = (bandpass(wide, 150, 500) * 4.0 + bandpass(wide, 500, 2000) * 2.0 + bandpass(wide, 2000, 6000) * 1.5
             + bandpass(wide, 6000, 16000) * 0.8) * np.exp(-tb * 42) * (1 - np.exp(-tb * 4000))
    sizzle = highpass(r.uniform(-1, 1, bn), 6000) * np.exp(-tb * 75)
    b, a = signal.iirpeak(200 + r.uniform(-20, 20), 3.0, fs=RATE)
    body = signal.lfilter(b, a, r.uniform(-1, 1, bn) * np.exp(-tb * 55))
    thump = np.sin(sweep(95, 45, 0.25)) * np.exp(-seconds(0.25) * 30)
    if not far:
        place(total, crack * 1.0, 0.0005)
        place(total, blast * 1.0, 0.0008)
        place(total, sizzle * 0.5, 0.0008)
        place(total, body * 3.0, 0.0008)
        place(total, thump * 0.85, 0.0005)
        place(total, impact(r, 0.07, [(3300, 110, 0.5), (5200, 150, 0.35), (7900, 190, 0.25)], snap=0.6) * 0.3, 0.005)
        place(total, impact(r, 0.1, [(2800, 85, 0.6), (4400, 110, 0.4), (8100, 160, 0.25)], snap=0.8, thud=0.2) * 0.35,
              0.055 + r.uniform(-0.004, 0.004))
        # Vroege echo's maken de "tros" knallen in de eerste 80 ms; de staart is zacht, kort en vrij helder (TACZ: 40 dB
        # weg na een kwart seconde).
        out = room(total, r, length=0.9, mix=0.14, bright=6000, low=2000,
                   early=((0.006, 0.55), (0.014, 0.42), (0.026, 0.33), (0.041, 0.24), (0.063, 0.17)))
        out = highpass(out, 35)
        return finish_hard(out / np.max(np.abs(out)) * 1.2, peak=0.95, fade_out=0.15)
    # Van ver (TACZ "3p": 63 % dreun, 20 % lijf, weinig hoog): geen krak en geen mechaniek, de dreun langer, en meer
    # galm met late echo's.
    place(total, lowpass(blast, 2500) * 1.0, 0.0)
    place(total, body * 4.0, 0.0)
    place(total, np.sin(sweep(85, 40, 0.4)) * np.exp(-seconds(0.4) * 18) * 0.6, 0.0)
    out = room(total, r, length=1.6, mix=0.3, bright=2400, low=900,
               early=((0.04, 0.5), (0.09, 0.38), (0.16, 0.28), (0.25, 0.2)))
    out = highpass(lowpass(out, 3500), 30)
    return finish_hard(out / np.max(np.abs(out)) * 1.2, peak=0.75, fade_out=0.3)


def glock_fire():
    """Een schot met de Glock. Drie varianten (glock_fire, _2, _3): Minecraft kiest er telkens één."""
    return gunshot(7401)


def glock_fire_2():
    return gunshot(7402)


def glock_fire_3():
    return gunshot(7403)


def glock_fire_far():
    """Een schot van ver weg (spelers op meer dan 12 blokken horen dit, tot 128 blokken ver)."""
    return gunshot(7404, far=True)


def glock_dry():
    """
    Een droge klik: de trekker gaat door (een zacht polymeer tikje), en de slagpin schiet naar voren en klapt (een
    helder stalen klikje met een korte naklank), maar er knalt niets.
    """
    r = np.random.default_rng(7410)
    total = np.zeros(int(RATE * 0.2))
    place(total, impact(r, 0.04, [(1900, 260, 0.3), (3700, 300, 0.2)], snap=0.35, snap_from=1500), 0.0)
    place(total, impact(r, 0.12, [(4300, 90, 0.45), (6900, 120, 0.35), (9800, 170, 0.2)], snap=1.0, thud=0.15,
                        thud_hz=900), 0.022)
    return finish_hard(total, peak=0.55)


def slam(r, gain=1.0):
    """De slede die naar voren slaat: de hardste, helderste klap van het mechaniek, met een doffe tik van het frame."""
    return impact(r, 0.16, [(3100, 70, 0.6), (5300, 90, 0.5), (8200, 120, 0.4), (11800, 170, 0.3)], snap=1.6,
                  snap_from=3000, thud=0.3, thud_hz=700) * gain


def rack(r, total, at):
    """
    De slede met de hand naar achteren trekken en loslaten (de "charge" van TACZ heeft er ook vier tikken in): je hand
    pakt de slede, hij schuift naar achteren (schuren), tikt tegen zijn aanslag, en als je loslaat schiet hij terug en
    slaat hij met een harde, heldere klap naar voren, met een klein natikje.
    """
    place(total, impact(r, 0.03, [(2400, 300, 0.2)], snap=0.25, snap_from=1500), at)
    back = scrape(r, 0.085, 2800, 14000, ticks=500, grit=0.55, shape=lambda x: np.sin(np.pi * x) ** 0.5 * (0.4 + 0.6 * x))
    place(total, back * 0.5, at + 0.02)
    place(total, impact(r, 0.07, [(3100, 120, 0.45), (5600, 160, 0.3), (8800, 220, 0.2)], snap=0.8) * 0.7, at + 0.105)
    place(total, scrape(r, 0.035, 2500, 12000, ticks=700, grit=0.6) * 0.35, at + 0.19)
    place(total, slam(r), at + 0.225)
    place(total, impact(r, 0.05, [(5100, 200, 0.2)], snap=0.3) * 0.4, at + 0.29)
    return total


def glock_slide():
    """De slede naar achteren en weer naar voren (een patroon in de kamer, een patroon eruit)."""
    r = np.random.default_rng(7420)
    total = np.zeros(int(RATE * 0.5))
    rack(r, total, 0.0)
    return finish_hard(total, peak=0.7)


def glock_slide_release():
    """De slide stop omlaag (de slede stond open na het laatste schot): het tikje van de hendel en de klap naar voren."""
    r = np.random.default_rng(7425)
    total = np.zeros(int(RATE * 0.35))
    place(total, impact(r, 0.03, [(3500, 260, 0.3)], snap=0.4), 0.0)
    place(total, scrape(r, 0.03, 2500, 12000, ticks=700, grit=0.6) * 0.35, 0.025)
    place(total, slam(r), 0.05)
    return finish_hard(total, peak=0.7)


def glock_mag_out():
    """
    Het magazijn eruit: de knop klikt (staal en polymeer), het magazijn glijdt uit de greep (polymeer langs polymeer,
    met een paar haakjes) en tikt tegen je hand.
    """
    r = np.random.default_rng(7430)
    total = np.zeros(int(RATE * 0.45))
    place(total, impact(r, 0.05, [(2900, 180, 0.4), (5300, 240, 0.25)], snap=0.9, thud=0.15), 0.0)
    out = scrape(r, 0.14, 900, 9000, ticks=260, grit=0.5, shape=lambda x: (1 - x) ** 1.5 * np.minimum(1, x * 12))
    place(total, out * 0.55, 0.03)
    place(total, impact(r, 0.08, [(1300, 140, 0.4), (2600, 180, 0.3)], snap=0.4, snap_from=1200, thud=0.35,
                        thud_hz=600) * 0.6, 0.21)
    return finish_hard(total, peak=0.6)


def glock_mag_in():
    """
    Het magazijn erin: het glijdt de greep in (schuren dat harder wordt) en klikt vast: een harde, heldere klik van de
    magazijnknop met een doffe tik van het polymeer, en een klein natikje.
    """
    r = np.random.default_rng(7440)
    total = np.zeros(int(RATE * 0.4))
    place(total, scrape(r, 0.11, 900, 8000, ticks=300, grit=0.5, shape=lambda x: np.minimum(1, x * 1.4) ** 2) * 0.45, 0.0)
    place(total, impact(r, 0.12, [(3400, 95, 0.55), (5800, 130, 0.4), (8600, 190, 0.25)], snap=1.4, thud=0.5,
                        thud_hz=650), 0.11)
    place(total, impact(r, 0.05, [(4700, 220, 0.2)], snap=0.35) * 0.35, 0.17)
    return finish_hard(total, peak=0.72)


def glock_round_load():
    """Een patroon in het magazijn duwen: messing schuift over de stalen lipjes en klikt eronder."""
    r = np.random.default_rng(7450)
    total = np.zeros(int(RATE * 0.16))
    place(total, scrape(r, 0.05, 2500, 12000, ticks=600, grit=0.6) * 0.35, 0.0)
    place(total, impact(r, 0.09, [(5200, 120, 0.45), (7900, 160, 0.3), (11200, 220, 0.2)], snap=0.9, thud=0.1,
                        thud_hz=1200), 0.045)
    return finish_hard(total, peak=0.45)


def glock_tap_rack():
    """Een tap-rack: een klap met je handpalm tegen de bodemplaat (dof, met een polymeer klik), dan de slede."""
    r = np.random.default_rng(7460)
    total = np.zeros(int(RATE * 0.8))
    place(total, impact(r, 0.08, [(900, 90, 0.3), (2100, 160, 0.3)], snap=0.6, snap_from=1000, thud=1.0, thud_hz=500,
                        thud_speed=45), 0.0)
    rack(r, total, 0.26)
    return finish_hard(total, peak=0.7)


def casing(seed, bounces):
    """
    Een lege huls die op de grond valt: een messing buisje zingt hoog (een paar ongelijke tonen tussen 3 en 12 kHz) bij
    elke stuiter, steeds zachter en sneller na elkaar, met op het eind wat gerammel.
    """
    r = np.random.default_rng(seed)
    total = np.zeros(int(RATE * 0.6))
    at, gain, gap = 0.0, 1.0, r.uniform(0.09, 0.13)
    pitch = r.uniform(0.92, 1.08)
    for _ in range(bounces):
        modes = [(3150 * pitch, 26, 0.35), (5900 * pitch, 34, 0.5), (8700 * pitch, 48, 0.4), (11800 * pitch, 70, 0.25)]
        place(total, impact(r, 0.3, modes, snap=0.6) * gain, at)
        at += gap
        gap *= r.uniform(0.55, 0.7)
        gain *= r.uniform(0.45, 0.6)
    place(total, scrape(r, 0.06, 3000, 12000, ticks=500, grit=0.7) * 0.12, at)
    return finish_hard(total, peak=0.45, fade_out=0.08)


def firearm_shot(seed, far=False, body_hz=200.0, thump=(95, 45), boom=1.0, crack=1.0, tail=0.9, far_tail=1.6,
                 mech=((0.005, 0.3), (0.055, 0.35))):
    """
    Een schot van een van de wapens van hoofdstuk 103 (de MP5, de AK-47, de AWP, de Remington 870): dezelfde opbouw als
    gunshot (de Glock: de krak, de klap, de dreun, het mechaniek en de galm), met knoppen per wapen. {body_hz}: de toon
    van de klap (lager = een groter kaliber), {thump}: van waar naar waar de dreun zakt, {boom}: hoeveel langer en
    zwaarder de knal is (1 = een 9mm-pistool), {crack}: hoe scherp de krak, {tail}: hoe lang de galm, {mech}: wanneer
    het mechaniek klapt en hoe hard (de grendel tegen zijn aanslag, en weer naar voren; leeg voor een grendelgeweer).
    """
    r = np.random.default_rng(seed)
    total = np.zeros(int(RATE * 0.8))
    cn = int(RATE * 0.03)
    tc = np.arange(cn) / RATE
    snap = r.uniform(-1, 1, cn) * np.exp(-tc * 420)
    snap[:4] += np.array([1.0, -0.85, 0.55, -0.3]) * 3.0
    snap = highpass(snap, 900)
    bn = int(RATE * 0.45)
    tb = np.arange(bn) / RATE
    wide = r.uniform(-1, 1, bn)
    blast = (bandpass(wide, 150, 500) * 4.0 + bandpass(wide, 500, 2000) * 2.0 + bandpass(wide, 2000, 6000) * 1.5
             + bandpass(wide, 6000, 16000) * 0.8) * np.exp(-tb * 42 / boom) * (1 - np.exp(-tb * 4000))
    sizzle = highpass(r.uniform(-1, 1, bn), 6000) * np.exp(-tb * 75)
    b, a = signal.iirpeak(body_hz + r.uniform(-15, 15), 3.0, fs=RATE)
    body = signal.lfilter(b, a, r.uniform(-1, 1, bn) * np.exp(-tb * 55 / boom))
    low = np.sin(sweep(thump[0], thump[1], 0.25 * boom)) * np.exp(-seconds(0.25 * boom) * 30 / boom)
    if not far:
        place(total, snap * crack, 0.0005)
        place(total, blast * 1.0, 0.0008)
        place(total, sizzle * 0.5 * crack, 0.0008)
        place(total, body * 3.0, 0.0008)
        place(total, low * 0.85 * boom, 0.0005)
        for at, gain in mech:
            place(total, impact(r, 0.09, [(2600, 90, 0.6), (4300, 120, 0.4), (7600, 170, 0.25)], snap=0.8, thud=0.25) * gain,
                  at + r.uniform(-0.003, 0.003))
        out = room(total, r, length=tail, mix=0.14, bright=6000, low=2000,
                   early=((0.006, 0.55), (0.014, 0.42), (0.026, 0.33), (0.041, 0.24), (0.063, 0.17)))
        out = highpass(out, 35)
        return finish_hard(out / np.max(np.abs(out)) * 1.2, peak=0.95, fade_out=0.15)
    place(total, lowpass(blast, 2500) * 1.0, 0.0)
    place(total, body * 4.0, 0.0)
    place(total, np.sin(sweep(thump[0] * 0.9, thump[1] * 0.9, 0.4 * boom)) * np.exp(-seconds(0.4 * boom) * 18 / boom) * 0.6, 0.0)
    out = room(total, r, length=far_tail, mix=0.3, bright=2400, low=900,
               early=((0.04, 0.5), (0.09, 0.38), (0.16, 0.28), (0.25, 0.2)))
    out = highpass(lowpass(out, 3500), 30)
    return finish_hard(out / np.max(np.abs(out)) * 1.2, peak=0.75, fade_out=0.3)


MP5_SHOT = dict(body_hz=235.0, thump=(105, 52), boom=0.9, crack=0.9, mech=((0.004, 0.35), (0.05, 0.45)))


def mp5_fire():
    """Een schot met de MP5 (hoofdstuk 103): 9mm uit een langere loop, met de zware grendel die heen en weer klapt."""
    return firearm_shot(7501, **MP5_SHOT)


def mp5_fire_2():
    return firearm_shot(7502, **MP5_SHOT)


def mp5_fire_3():
    return firearm_shot(7503, **MP5_SHOT)


def mp5_fire_far():
    return firearm_shot(7504, far=True, **MP5_SHOT)


def mp5_charge():
    """
    De spanhendel van de MP5: naar achteren trekken (staal schuurt in de spanbuis), een tik tegen de aanslag, en
    loslaten: de grendel slaat met een harde klap naar voren.
    """
    r = np.random.default_rng(7510)
    total = np.zeros(int(RATE * 0.55))
    place(total, impact(r, 0.03, [(2200, 280, 0.25)], snap=0.3, snap_from=1400), 0.0)
    back = scrape(r, 0.12, 2200, 12000, ticks=420, grit=0.55, shape=lambda x: np.sin(np.pi * x) ** 0.5 * (0.4 + 0.6 * x))
    place(total, back * 0.55, 0.02)
    place(total, impact(r, 0.07, [(2600, 110, 0.5), (4700, 150, 0.35), (7900, 210, 0.2)], snap=0.8) * 0.7, 0.14)
    place(total, scrape(r, 0.04, 2200, 11000, ticks=700, grit=0.6) * 0.35, 0.25)
    place(total, slam(r, 1.15), 0.29)
    place(total, impact(r, 0.06, [(1800, 120, 0.4), (3600, 180, 0.3)], snap=0.4, thud=0.4, thud_hz=500) * 0.5, 0.3)
    return finish_hard(total, peak=0.78)


AK47_SHOT = dict(body_hz=165.0, thump=(82, 36), boom=1.35, crack=1.15, tail=1.1, far_tail=2.0,
                 mech=((0.006, 0.45), (0.062, 0.55)))


def ak47_fire():
    """
    Een schot met de AK-47 (hoofdstuk 103, stap 2): 7.62x39mm. Lager, langer en zwaarder dan de 9mm van de MP5, met de
    zware grendeldrager die hoorbaar tegen zijn aanslag en weer naar voren klapt.
    """
    return firearm_shot(7601, **AK47_SHOT)


def ak47_fire_2():
    return firearm_shot(7602, **AK47_SHOT)


def ak47_fire_3():
    return firearm_shot(7603, **AK47_SHOT)


def ak47_fire_far():
    return firearm_shot(7604, far=True, **AK47_SHOT)


def ak47_charge():
    """
    De spanhendel van de AK-47: de grendeldrager in een lange haal naar achteren (staal over staal), een tik tegen de
    aanslag, en loslaten: hij slaat met een doffe, zware klap naar voren. Meer staal dan bij de MP5, dus lager.
    """
    r = np.random.default_rng(7610)
    total = np.zeros(int(RATE * 0.62))
    place(total, impact(r, 0.03, [(1700, 240, 0.3)], snap=0.3, snap_from=1100), 0.0)
    back = scrape(r, 0.16, 1500, 10000, ticks=360, grit=0.65, shape=lambda x: np.sin(np.pi * x) ** 0.5 * (0.4 + 0.6 * x))
    place(total, back * 0.6, 0.02)
    place(total, impact(r, 0.08, [(1900, 100, 0.55), (3600, 140, 0.4), (6400, 200, 0.2)], snap=0.8, thud=0.3,
                        thud_hz=420) * 0.75, 0.18)
    place(total, scrape(r, 0.05, 1600, 9500, ticks=620, grit=0.65) * 0.4, 0.29)
    place(total, slam(r, 1.3), 0.34)
    place(total, impact(r, 0.08, [(1300, 110, 0.5), (2700, 160, 0.35)], snap=0.4, thud=0.6, thud_hz=380) * 0.6, 0.35)
    return finish_hard(total, peak=0.8)


AWP_SHOT = dict(body_hz=118.0, thump=(68, 26), boom=1.9, crack=1.35, tail=1.5, far_tail=2.8, mech=())


def awp_fire():
    """
    Een schot met de AWP (hoofdstuk 103, stap 3): .338 Lapua Magnum. De zwaarste knal van allemaal: laag, lang en met
    een lange galm. Er klapt geen mechaniek: de grendel bedien je zelf.
    """
    return firearm_shot(7701, **AWP_SHOT)


def awp_fire_2():
    return firearm_shot(7702, **AWP_SHOT)


def awp_fire_far():
    return firearm_shot(7704, far=True, **AWP_SHOT)


def awp_bolt():
    """
    De grendel van de AWP: de knop omhoog (een klik), de grendel naar achteren (staal glijdt door de behuizing, een tik
    tegen de aanslag), weer naar voren, en de knop omlaag (hij vergrendelt met een zware klik). Ruim een seconde.
    """
    r = np.random.default_rng(7710)
    total = np.zeros(int(RATE * 1.25))
    # Omhoog.
    place(total, impact(r, 0.06, [(2100, 160, 0.5), (3900, 210, 0.35)], snap=0.9, thud=0.25, thud_hz=600) * 0.7, 0.13)
    # Naar achteren.
    back = scrape(r, 0.2, 1400, 9000, ticks=300, grit=0.6, shape=lambda x: np.sin(np.pi * x) ** 0.5 * (0.5 + 0.5 * x))
    place(total, back * 0.55, 0.26)
    place(total, impact(r, 0.08, [(1700, 110, 0.55), (3300, 150, 0.4)], snap=0.7, thud=0.4, thud_hz=420) * 0.75, 0.47)
    # Naar voren.
    forward = scrape(r, 0.22, 1500, 9500, ticks=340, grit=0.6, shape=lambda x: np.sin(np.pi * x) ** 0.5 * (1.0 - 0.4 * x))
    place(total, forward * 0.55, 0.64)
    place(total, impact(r, 0.07, [(1900, 120, 0.5), (3700, 170, 0.35)], snap=0.6, thud=0.3, thud_hz=480) * 0.6, 0.87)
    # Omlaag: vergrendeld.
    place(total, impact(r, 0.1, [(2400, 100, 0.6), (4400, 140, 0.45), (7200, 190, 0.25)], snap=1.2, thud=0.55, thud_hz=520) * 0.95,
          0.97)
    return finish_hard(total, peak=0.8)


SHOTGUN_SHOT = dict(body_hz=138.0, thump=(74, 30), boom=1.6, crack=0.85, tail=1.25, far_tail=2.2, mech=())


def shotgun_fire():
    """
    Een schot met de Remington 870 (hoofdstuk 103, stap 4): 12 Gauge. Een brede, doffe dreun, minder scherp dan een
    geweer. Er klapt geen mechaniek: de pomp bedien je zelf.
    """
    return firearm_shot(7801, **SHOTGUN_SHOT)


def shotgun_fire_2():
    return firearm_shot(7802, **SHOTGUN_SHOT)


def shotgun_fire_far():
    return firearm_shot(7804, far=True, **SHOTGUN_SHOT)


def suppressed_shot(seed, body_hz=420.0, weight=1.0, thump=(140, 70), hiss=1.0, mech=((0.004, 0.5), (0.05, 0.6))):
    """
    Een schot met een demper erop (hoofdstuk 103, stap 6): geen knal en geen krak, maar een korte doffe plof (het gas dat
    in de demper uitzet), een zacht gesis, en het mechaniek, dat je nu juist goed hoort. {body_hz}: de toon van de plof
    (lager = een groter kaliber), {weight}: hoe lang en zwaar hij is (1 = een 9mm), {thump}: van waar naar waar de dreun
    zakt, {mech}: wanneer het mechaniek klapt en hoe hard (leeg voor een grendel of een pomp).
    """
    r = np.random.default_rng(seed)
    total = np.zeros(int(RATE * 0.55))
    n = int(RATE * 0.25)
    t = np.arange(n) / RATE
    wide = r.uniform(-1, 1, n)
    puff = (bandpass(wide, 250, 1400) * 3.2 + bandpass(wide, 1400, 3600) * 0.9) * np.exp(-t * 60 / weight) \
        * (1 - np.exp(-t * 2500))
    gas = bandpass(r.uniform(-1, 1, n), 3000, 7000) * np.exp(-t * 45) * 0.3 * hiss
    b, a = signal.iirpeak(body_hz + r.uniform(-20, 20), 2.5, fs=RATE)
    body = signal.lfilter(b, a, r.uniform(-1, 1, n) * np.exp(-t * 90 / weight))
    low = np.sin(sweep(thump[0], thump[1], 0.12 * weight)) * np.exp(-seconds(0.12 * weight) * 55 / weight)
    place(total, puff, 0.0005)
    place(total, gas, 0.001)
    place(total, body * 2.6, 0.0008)
    place(total, low * 0.7 * weight, 0.0005)
    for at, gain in mech:
        # Het mechaniek: een doffere tik dan bij een gewoon schot (daar overstemt de knal hem).
        place(total, impact(r, 0.09, [(1900, 90, 0.7), (3200, 120, 0.4), (5200, 170, 0.15)], snap=0.35, thud=0.45)
              * gain * 0.6, at + r.uniform(-0.003, 0.003))
    out = room(total, r, length=0.35, mix=0.08, bright=3000, low=1200, early=((0.006, 0.4), (0.014, 0.3), (0.026, 0.2)))
    out = highpass(lowpass(out, 6500), 60)
    return finish_hard(out / np.max(np.abs(out)), peak=0.7, fade_out=0.12)


def suppressed_9mm():
    """Een gedempt 9mm-schot (de Glock, de MP5): een droge plof en de slede of de grendel. Twee varianten."""
    return suppressed_shot(7901, body_hz=430.0, weight=0.85)


def suppressed_9mm_2():
    return suppressed_shot(7902, body_hz=430.0, weight=0.85)


def suppressed_rifle():
    """Een gedempt geweerschot (de AK-47, de AWP): zwaarder en lager, met meer gas. Twee varianten."""
    return suppressed_shot(7911, body_hz=300.0, weight=1.35, thump=(120, 55), hiss=1.4, mech=((0.006, 0.35),))


def suppressed_rifle_2():
    return suppressed_shot(7912, body_hz=300.0, weight=1.35, thump=(120, 55), hiss=1.4, mech=((0.006, 0.35),))


def suppressed_shotgun():
    """Een gedempt hagelschot (de Remington 870): een diepe, brede plof; het mechaniek zwijgt (je pompt zelf)."""
    return suppressed_shot(7921, body_hz=215.0, weight=1.7, thump=(100, 45), hiss=1.2, mech=())


def suppressed_shotgun_2():
    return suppressed_shot(7922, body_hz=215.0, weight=1.7, thump=(100, 45), hiss=1.2, mech=())


def shotgun_pump():
    """
    De pomp van de Remington 870: het voorhout naar achteren (de stangen glijden, de grendel klapt open) en meteen weer
    naar voren (hij vergrendelt): het bekende "sjik-sjak". Een halve seconde.
    """
    r = np.random.default_rng(7810)
    total = np.zeros(int(RATE * 0.62))
    back = scrape(r, 0.11, 900, 8000, ticks=260, grit=0.7, shape=lambda x: np.sin(np.pi * x) ** 0.5 * (0.5 + 0.5 * x))
    place(total, back * 0.7, 0.1)
    place(total, impact(r, 0.09, [(1500, 100, 0.6), (2900, 140, 0.45), (5200, 190, 0.25)], snap=0.9, thud=0.55, thud_hz=380) * 0.9,
          0.2)
    forward = scrape(r, 0.1, 1000, 8500, ticks=300, grit=0.7, shape=lambda x: np.sin(np.pi * x) ** 0.5 * (1.0 - 0.4 * x))
    place(total, forward * 0.7, 0.3)
    place(total, impact(r, 0.11, [(1700, 90, 0.65), (3300, 130, 0.5), (6100, 180, 0.3)], snap=1.2, thud=0.7, thud_hz=430), 0.4)
    return finish_hard(total, peak=0.82)


def shotgun_shell():
    """Een hagelpatroon die de buis in gaat: kunststof schuift langs staal, en de pal klikt erachter."""
    r = np.random.default_rng(7820)
    total = np.zeros(int(RATE * 0.3))
    place(total, scrape(r, 0.1, 600, 5000, ticks=180, grit=0.45, shape=lambda x: np.minimum(1, x * 1.6) ** 2) * 0.5, 0.0)
    place(total, impact(r, 0.09, [(1900, 120, 0.5), (3600, 170, 0.35)], snap=0.9, thud=0.5, thud_hz=520) * 0.85, 0.11)
    place(total, impact(r, 0.05, [(4200, 220, 0.25)], snap=0.4) * 0.35, 0.17)
    return finish_hard(total, peak=0.7)


def rifle_mag_out():
    """Het magazijn van een lang wapen eruit: de pal klikt, het stalen magazijn glijdt uit de schacht en tikt in je hand."""
    r = np.random.default_rng(7520)
    total = np.zeros(int(RATE * 0.5))
    place(total, impact(r, 0.06, [(2300, 150, 0.45), (4100, 200, 0.3)], snap=1.0, thud=0.25, thud_hz=700), 0.0)
    out = scrape(r, 0.16, 700, 7000, ticks=240, grit=0.5, shape=lambda x: (1 - x) ** 1.5 * np.minimum(1, x * 12))
    place(total, out * 0.6, 0.035)
    place(total, impact(r, 0.1, [(1000, 120, 0.45), (2100, 160, 0.3)], snap=0.4, snap_from=1000, thud=0.5,
                        thud_hz=450) * 0.65, 0.23)
    return finish_hard(total, peak=0.65)


def rifle_mag_in():
    """Het magazijn van een lang wapen erin: het schuift de schacht in en klikt vast, zwaarder dan dat van een pistool."""
    r = np.random.default_rng(7530)
    total = np.zeros(int(RATE * 0.45))
    place(total, scrape(r, 0.12, 700, 7000, ticks=280, grit=0.5, shape=lambda x: np.minimum(1, x * 1.4) ** 2) * 0.5, 0.0)
    place(total, impact(r, 0.14, [(2700, 85, 0.6), (4600, 120, 0.45), (7400, 180, 0.25)], snap=1.5, thud=0.7,
                        thud_hz=520), 0.12)
    place(total, impact(r, 0.06, [(3900, 200, 0.25)], snap=0.4) * 0.4, 0.19)
    return finish_hard(total, peak=0.78)


def gun_selector():
    """De keuzeschakelaar (safe, semi, auto): een droge, dubbele klik van staal dat in zijn stand valt."""
    r = np.random.default_rng(7540)
    total = np.zeros(int(RATE * 0.12))
    place(total, impact(r, 0.03, [(3800, 320, 0.3), (6400, 380, 0.2)], snap=0.5, snap_from=2500), 0.0)
    place(total, impact(r, 0.07, [(4900, 140, 0.5), (8200, 190, 0.35)], snap=1.0, thud=0.12, thud_hz=1100), 0.018)
    return finish_hard(total, peak=0.5)


def casing_tink():
    """Een huls die op de grond valt. Drie varianten (casing_tink, _2, _3) met een andere toon en aantal stuiters."""
    return casing(7470, 3)


def casing_tink_2():
    return casing(7471, 4)


def casing_tink_3():
    return casing(7472, 2)


def bullet_impact():
    """Een kogel die in een blok slaat: een krak, een doffe klap en wat rondspattend gruis."""
    total = np.zeros(int(RATE * 0.3))
    place(total, bandpass(noise(0.03), 2000, 9000) * decay(0.03, 120) * 1.2, 0.0)
    place(total, bandpass(noise(0.08), 200, 2500) * decay(0.08, 40) * 0.9, 0.0)
    place(total, np.sin(sweep(180, 60, 0.1)) * decay(0.1, 25) * 0.6, 0.0)
    place(total, crackle(0.2, 10, 1500, 6000) * np.linspace(1, 0, int(RATE * 0.2)) * 0.4, 0.03)
    return finish(total, peak=0.6)


def plate_hit():
    """
    Hoofdstuk 109: een treffer op een plate van een vest. Een harde tik met een korte metalen klank (de plate), meteen
    gedempt door de stof van het vest eromheen, en een doffe dreun in je borst. Kort, zodat een salvo een rij tikken wordt.
    """
    r = np.random.default_rng(10901)
    total = np.zeros(int(RATE * 0.22))
    place(total, impact(r, 0.09, [(2350, 55, 0.5), (3900, 80, 0.32), (6100, 120, 0.18)], snap=0.9, snap_from=2200,
                        thud=0.55, thud_hz=170), 0.0)
    place(total, bandpass(noise(0.05), 300, 1800) * decay(0.05, 70) * 0.35, 0.004)
    return finish_hard(total, peak=0.6)


def plate_crack():
    """Hoofdstuk 109: een plate barst. Een droge krak en wat knisperend gruis, alsof keramiek of metaal scheurt."""
    r = np.random.default_rng(10902)
    total = np.zeros(int(RATE * 0.5))
    place(total, impact(r, 0.06, [(1800, 90, 0.35), (3200, 130, 0.25)], snap=1.0, snap_from=1500, thud=0.4, thud_hz=240), 0.0)
    place(total, crackle(0.32, 14, 1200, 7000) * np.linspace(1, 0, int(RATE * 0.32)) * 0.55, 0.02)
    place(total, bandpass(noise(0.12), 2500, 9000) * decay(0.12, 35) * 0.3, 0.05)
    return finish_hard(total, peak=0.6)


def holster_draw():
    """
    Een pistool uit de holster trekken (hoofdstuk 74, opnieuw): leer of kunststof dat langs het pistool schuurt, een
    klikje als het loskomt, en je hand die de greep goed vastpakt.
    """
    r = np.random.default_rng(7480)
    total = np.zeros(int(RATE * 0.45))
    place(total, scrape(r, 0.2, 500, 6000, ticks=180, grit=0.35, shape=lambda x: np.sin(np.pi * x) ** 1.5) * 0.5, 0.0)
    place(total, impact(r, 0.05, [(2600, 200, 0.3), (4800, 260, 0.2)], snap=0.6, thud=0.2), 0.16)
    place(total, impact(r, 0.06, [(1100, 150, 0.2)], snap=0.2, snap_from=900, thud=0.4, thud_hz=400), 0.27)
    return finish_hard(total, peak=0.5)


def backpack_zip():
    """
    Hoofdstuk 72: een rugzak openritsen. Een rits is een rij tandjes die een voor een loskomen: korte tikjes ruis,
    steeds sneller na elkaar en iets hoger, met een zacht gesis van de stof eronder. Ruim een seconde, net zo lang als
    de rits duurt (de mod laat hem 1,5 s lopen).
    """
    duration = 1.25
    total = np.zeros(int(RATE * duration))
    t = 0.04
    gap = 0.034
    while t < duration - 0.08:
        tick = bandpass(noise(0.012), 2200, 7000) * decay(0.012, 260)
        place(total, tick * (0.55 + 0.25 * rng.random()), t)
        t += gap * (0.75 + 0.5 * rng.random())
        gap = max(0.012, gap * 0.985)
    cloth = bandpass(noise(duration), 500, 2400) * np.sin(np.linspace(0, np.pi, int(RATE * duration))) * 0.18
    total = mix(total, cloth)
    return finish(total, peak=0.5)


SOUNDS = {
    "imt_boot": imt_boot,
    "imt_shutdown": imt_shutdown,
    "imt_blip": imt_blip,
    "imt_confirm": imt_confirm,
    "imt_notify": imt_notify,
    "imt_alert": imt_alert,
    "imt_denied": imt_denied,
    "imt_scan": imt_scan,
    "imt_menu_open": imt_menu_open,
    "imt_select": imt_select,
    "station_upgrade": station_upgrade,
    "drone_deploy": drone_deploy,
    "drone_laser": drone_laser,
    "drone_recall": drone_recall,
    "drone_hurt": drone_hurt,
    "drone_destroyed": drone_destroyed,
    "satellite_lock": satellite_lock,
    "satellite_charge": satellite_charge,
    "satellite_fire": satellite_fire,
    "satellite_missile": satellite_missile,
    "satellite_impact": satellite_impact,
    "comms_message": comms_message,
    "comms_ping": comms_ping,
    "raven_radio": raven_radio,
    "laser_cannon_arm": laser_cannon_arm,
    "laser_cannon_disarm": laser_cannon_disarm,
    "laser_cannon_fire": laser_cannon_fire,
    "suit_thruster": suit_thruster,
    "rayshield_hum": rayshield_hum,
    "alarm_siren": alarm_siren,
    "rayshield_on": rayshield_on,
    "rayshield_off": rayshield_off,
    "rayshield_hit": rayshield_hit,
    "remote_frag_defuse": remote_frag_defuse,
    "trigger_click": trigger_click,
    "robot_step": robot_step,
    "robot_power_on": robot_power_on,
    "robot_power_off": robot_power_off,
    "robot_hurt": robot_hurt,
    "robot_charge_hum": robot_charge_hum,
    "drone_rotor": drone_rotor,
    "robot_laser": robot_laser,
    "robot_gun": robot_gun,
    "ion_blaster": ion_blaster,
    "emp_blast": emp_blast,
    "emp_power_down": emp_power_down,
    "emp_grenade_beep": emp_grenade_beep,
    "emp_grenade_stick": emp_grenade_stick,
    "emp_grenade_bounce": emp_grenade_bounce,
    "smoke_grenade_hiss": smoke_grenade_hiss,
    "flashbang_bang": flashbang_bang,
    "flashbang_ring": flashbang_ring,
    "laser_pistol_fire": laser_pistol_fire,
    "laser_rifle_fire": laser_rifle_fire,
    "laser_sniper_fire": laser_sniper_fire,
    "laser_shotgun_fire": laser_shotgun_fire,
    "laser_gun_empty": laser_gun_empty,
    "laser_gun_cell_out": laser_gun_cell_out,
    "laser_gun_cell_in": laser_gun_cell_in,
    "laser_gun_cycle": laser_gun_cycle,
    "laser_gun_aim": laser_gun_aim,
    "glock_fire": glock_fire,
    "glock_fire_2": glock_fire_2,
    "glock_fire_3": glock_fire_3,
    "glock_fire_far": glock_fire_far,
    "glock_dry": glock_dry,
    "glock_slide": glock_slide,
    "glock_slide_release": glock_slide_release,
    "glock_mag_out": glock_mag_out,
    "glock_mag_in": glock_mag_in,
    "glock_round_load": glock_round_load,
    "glock_tap_rack": glock_tap_rack,
    "mp5_fire": mp5_fire,
    "mp5_fire_2": mp5_fire_2,
    "mp5_fire_3": mp5_fire_3,
    "mp5_fire_far": mp5_fire_far,
    "mp5_charge": mp5_charge,
    "ak47_fire": ak47_fire,
    "ak47_fire_2": ak47_fire_2,
    "ak47_fire_3": ak47_fire_3,
    "ak47_fire_far": ak47_fire_far,
    "ak47_charge": ak47_charge,
    "awp_fire": awp_fire,
    "awp_fire_2": awp_fire_2,
    "awp_fire_far": awp_fire_far,
    "awp_bolt": awp_bolt,
    "shotgun_fire": shotgun_fire,
    "shotgun_fire_2": shotgun_fire_2,
    "shotgun_fire_far": shotgun_fire_far,
    "shotgun_pump": shotgun_pump,
    "suppressed_9mm": suppressed_9mm,
    "suppressed_9mm_2": suppressed_9mm_2,
    "suppressed_rifle": suppressed_rifle,
    "suppressed_rifle_2": suppressed_rifle_2,
    "suppressed_shotgun": suppressed_shotgun,
    "suppressed_shotgun_2": suppressed_shotgun_2,
    "shotgun_shell": shotgun_shell,
    "rifle_mag_out": rifle_mag_out,
    "rifle_mag_in": rifle_mag_in,
    "gun_selector": gun_selector,
    "casing_tink": casing_tink,
    "casing_tink_2": casing_tink_2,
    "casing_tink_3": casing_tink_3,
    "bullet_impact": bullet_impact,
    "holster_draw": holster_draw,
    "backpack_zip": backpack_zip,
    "plate_hit": plate_hit,
    "plate_crack": plate_crack,
}


def main():
    """
    Zonder namen: alle geluiden. Met namen (python tools/generate_sounds.py imt_boot) alleen
    die. Handig, want een .ogg komt er elke keer net iets anders uit, en dan lijkt het in git
    alsof ook de geluiden die je niet aanraakte veranderd zijn.
    """
    import sys
    wanted = sys.argv[1:] or list(SOUNDS)
    unknown = [name for name in wanted if name not in SOUNDS]
    if unknown:
        print("Onbekend:", ", ".join(unknown), "- kies uit:", ", ".join(SOUNDS))
        return
    OUT.mkdir(parents=True, exist_ok=True)
    for name in wanted:
        make = SOUNDS[name]
        path = OUT / f"{name}.ogg"
        # Hoofdstuk 74: de vuurwapens met een hogere kwaliteit (compression_level 0,15 = Vorbis-kwaliteit 0,85), anders
        # vervaagt de scherpe aanzet van een schot of een klik; de rest zoals altijd (dezelfde bestanden als vroeger).
        hifi = name.startswith(("glock_", "casing_", "holster_", "mp5_", "rifle_", "gun_", "ak47_", "awp_", "shotgun_",
                                "suppressed_"))
        sf.write(path, make(), RATE, format="OGG", subtype="VORBIS", compression_level=0.15 if hifi else None)
        print(f"{path.relative_to(OUT.parent.parent.parent.parent.parent.parent)}  ({path.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
