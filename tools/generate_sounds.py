"""
Maakt de eigen geluiden van Stark Tech (.ogg) uit golven en ruis, zonder opnames.

Gebruik (eenmalig: pip install numpy scipy soundfile):
    python tools/generate_sounds.py               # alle geluiden
    python tools/generate_sounds.py edith_boot    # alleen deze

De bestanden komen in src/main/resources/assets/starktech/sounds/. Minecraft wil mono
Ogg Vorbis: alleen mono-geluiden klinken van een plek in de wereld (links/rechts, ver/dichtbij).

Elk geluid is een functie die een numpy-array teruggeeft met waarden tussen -1 en 1.
"""
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy import signal

RATE = 44100
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/starktech/sounds"
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
    Het typische "gezoem" van een repulsor: een toon met een paar boventonen erbij, en
    eventueel een snelle trilling (wobble) zodat het elektrisch klinkt.
    """
    phase = sweep(start_hz, end_hz, duration, curve)
    tone = np.sin(phase) + 0.35 * np.sin(2 * phase) + 0.15 * np.sin(3 * phase + 0.5)
    if wobble:
        t = seconds(duration)
        tone *= 1.0 + wobble * np.sin(2 * np.pi * 38 * t)
    return tone


# ---------------------------------------------------------------- de geluiden

def repulsor_arm():
    """
    De repulsor gaat aan: een klik in de handschoen, dan een gezoem dat omhoog loopt tot een
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


def repulsor_disarm():
    """De repulsor gaat uit: het gezoem zakt weg en de handschoen klikt dicht."""
    total = np.zeros(int(RATE * 0.6))
    fall = whine(2100, 260, 0.5, curve=0.6, wobble=0.12) * decay(0.5, 4.5)
    click = bandpass(noise(0.025), 1200, 6000) * decay(0.025, 180) * 0.7
    place(total, fade(fall * 0.55, 0.005, 0.1), 0.0)
    place(total, click, 0.42)
    return finish(total, peak=0.75)


def repulsor_fire():
    """
    Een repulsorschot zoals in de films: een heel kort gezoem dat omhoog schiet, en dan de
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


def edith_boot():
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


def edith_blip():
    """Een klein elektrisch zapje: bij elke stap van de laadbalk."""
    t = seconds(0.05)
    zap = np.sin(sweep(3000, 1200, 0.05)) * decay(0.05, 50) + bandpass(noise(0.05), 4000, 10000) * decay(0.05, 90) * 0.4
    return finish(fade(zap, 0.001, 0.01), peak=0.35)


def edith_confirm():
    """Identiteit bevestigd: een diepe dreun, een ping die omhoog schiet en een heldere nagalm."""
    total = np.zeros(int(RATE * 1.3))
    place(total, fade(np.sin(sweep(120, 50, 0.35)) * decay(0.35, 9) * 1.1, 0.001, 0.05), 0.0)
    ping = np.sin(sweep(1046.5, 2093, 0.15, curve=0.5))
    place(total, fade(ping * 0.4, 0.002, 0.01), 0.05)
    place(total, fade(chime(2093, 1.0, 4) * 0.35, 0.002, 0.2), 0.2)
    return finish(total, peak=0.6)


# ---------------------------------------------------------------- meldingen en menu
# Alles in dezelfde toonsoort als het opstartgeluid (C, E, G, B), met zachte, glazige tonen:
# E.D.I.T.H. mag je niet laten schrikken, behalve als er echt iets mis is.

def glass(freq, duration, decay_speed=8.0, bright=0.25):
    """Een zachte, glazige toon: een sinus met een vleugje FM, zodat hij niet "piept"."""
    t = seconds(duration)
    tone = np.sin(2 * np.pi * freq * t + bright * np.sin(2 * np.pi * freq * 2.0 * t))
    tone += 0.18 * np.sin(2 * np.pi * freq * 3.0 * t)
    return tone * decay(duration, decay_speed)


def edith_notify():
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


def edith_alert():
    """Een waarschuwing: twee korte, iets scherpere tonen op dezelfde hoogte."""
    total = np.zeros(int(RATE * 0.45))
    for i in range(2):
        t = seconds(0.12)
        tone = np.sin(2 * np.pi * 987.77 * t) + 0.4 * np.sin(2 * np.pi * 987.77 * 3 * t) * decay(0.12, 30)
        place(total, fade(tone * decay(0.12, 14) * 0.5, 0.003, 0.03), i * 0.16)
    return finish(total, peak=0.5)


def edith_denied():
    """Geweigerd of mislukt: twee tonen omlaag, laag en wat brommerig."""
    total = np.zeros(int(RATE * 0.5))
    for i, freq in enumerate((392.0, 261.63)):
        t = seconds(0.2)
        tone = signal.sawtooth(2 * np.pi * freq * t) * 0.5 + np.sin(2 * np.pi * freq * t)
        tone = lowpass(tone, 1800) * decay(0.2, 9)
        place(total, fade(tone * 0.5, 0.004, 0.05), i * 0.17)
    return finish(total, peak=0.5)


def edith_scan():
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


def edith_menu_open():
    """Het G-menu gaat open: een korte, luchtige zoef omhoog met een glinstertje."""
    total = np.zeros(int(RATE * 0.35))
    air = bandpass(noise(0.2), 2000, 9000, order=2) * np.sin(np.linspace(0, np.pi, int(RATE * 0.2))) * 0.25
    rise = np.sin(sweep(700, 1600, 0.16, curve=0.7)) * decay(0.16, 10) * 0.3
    place(total, fade(mix(air, rise), 0.005, 0.05), 0.0)
    place(total, fade(glass(2093.0, 0.18, 18, bright=0.1) * 0.15, 0.003, 0.05), 0.1)
    return finish(total, peak=0.4)


def edith_select():
    """Een optie kiezen: een heel kort, zacht tikje, zoals een aanraakscherm."""
    t = seconds(0.045)
    tick = np.sin(2 * np.pi * 2600 * t) * decay(0.045, 110)
    tick += bandpass(noise(0.045), 3000, 8000) * decay(0.045, 180) * 0.3
    return finish(fade(tick, 0.001, 0.01), peak=0.35)


# ---------------------------------------------------------------- laadstation

def station_upgrade():
    """
    Een upgrade wordt ingebouwd: een servo die zoemt, een klik als het onderdeel vastzit, en
    dan het "klaar"-akkoord van E.D.I.T.H.
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
    """Een bericht van een andere E.D.I.T.H.-drager: drie snelle, heldere tonen omlaag en weer op."""
    total = np.zeros(int(RATE * 0.6))
    for i, freq in enumerate((1318.5, 1046.5, 1567.98)):
        place(total, fade(glass(freq, 0.25, 12) * 0.4, 0.003, 0.05), i * 0.09)
    return finish(total, peak=0.5)


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


# ---------------------------------------------------------------- C4 (hoofdstuk 10)

def short_beep(freq, duration):
    """Een kort, hoog, een beetje schel piepje (een sinus met wat blokgolf erin)."""
    t = seconds(duration)
    tone = np.sin(2 * np.pi * freq * t) + 0.35 * np.sign(np.sin(2 * np.pi * freq * t))
    envelope = np.minimum(1, t / 0.003) * np.minimum(1, (duration - t) / 0.012)
    return lowpass(tone, 7000) * envelope


def c4_beep():
    """Het piepje van C4 die aftelt (zoals in films): kort en hoog."""
    return finish(short_beep(2600, 0.09), peak=0.55)


def c4_arm():
    """C4 plakken en aanzetten: een klik (het kapje gaat dicht), dan twee oplopende piepjes."""
    total = np.zeros(int(RATE * 0.42))
    click = bandpass(noise(0.02), 1500, 6000) * decay(0.02, 300)
    place(total, click * 1.6, 0.0)
    place(total, short_beep(2200, 0.07) * 0.8, 0.12)
    place(total, short_beep(2900, 0.07) * 0.8, 0.25)
    return finish(total, peak=0.6)


def c4_defuse():
    """Een draad doorknippen: een metalen knip, en een dalend toontje (het schermpje gaat uit)."""
    total = np.zeros(int(RATE * 0.6))
    snip = bandpass(noise(0.03), 2500, 9000) * decay(0.03, 200)
    place(total, snip * 2.0, 0.0)
    place(total, snip * 1.2, 0.05)
    tone = np.sin(sweep(1800, 450, 0.35)) * decay(0.35, 4)
    place(total, tone * 0.6, 0.13)
    return finish(total, peak=0.6)


def detonator_click():
    """De knop van de detonator: een stevige klik, dan een kort radiotoontje met wat ruis (het signaal)."""
    total = np.zeros(int(RATE * 0.35))
    click = bandpass(noise(0.025), 800, 5000) * decay(0.025, 250)
    place(total, click * 2.0, 0.0)
    chirp = np.sin(sweep(1200, 2600, 0.12)) * fade(np.ones(int(RATE * 0.12)), 0.01, 0.03)
    place(total, chirp * 0.5, 0.05)
    hiss = bandpass(noise(0.18), 2000, 6000) * decay(0.18, 12) * 0.25
    place(total, hiss, 0.05)
    return finish(total, peak=0.6)


SOUNDS = {
    "edith_boot": edith_boot,
    "edith_blip": edith_blip,
    "edith_confirm": edith_confirm,
    "edith_notify": edith_notify,
    "edith_alert": edith_alert,
    "edith_denied": edith_denied,
    "edith_scan": edith_scan,
    "edith_menu_open": edith_menu_open,
    "edith_select": edith_select,
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
    "repulsor_arm": repulsor_arm,
    "repulsor_disarm": repulsor_disarm,
    "repulsor_fire": repulsor_fire,
    "suit_thruster": suit_thruster,
    "rayshield_hum": rayshield_hum,
    "rayshield_on": rayshield_on,
    "rayshield_off": rayshield_off,
    "rayshield_hit": rayshield_hit,
    "c4_beep": c4_beep,
    "c4_arm": c4_arm,
    "c4_defuse": c4_defuse,
    "detonator_click": detonator_click,
}


def main():
    """
    Zonder namen: alle geluiden. Met namen (python tools/generate_sounds.py edith_boot) alleen
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
        sf.write(path, make(), RATE, format="OGG", subtype="VORBIS")
        print(f"{path.relative_to(OUT.parent.parent.parent.parent.parent.parent)}  ({path.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
