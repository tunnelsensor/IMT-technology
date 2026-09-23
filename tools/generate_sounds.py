"""
Maakt de eigen geluiden van Stark Tech (.ogg) uit golven en ruis, zonder opnames.

Gebruik (eenmalig: pip install numpy scipy soundfile):
    python tools/generate_sounds.py

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


SOUNDS = {
    "repulsor_arm": repulsor_arm,
    "repulsor_disarm": repulsor_disarm,
    "repulsor_fire": repulsor_fire,
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, make in SOUNDS.items():
        path = OUT / f"{name}.ogg"
        sf.write(path, make(), RATE, format="OGG", subtype="VORBIS")
        print(f"{path.relative_to(OUT.parent.parent.parent.parent.parent.parent)}  ({path.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
