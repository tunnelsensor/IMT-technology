"""
Testbestanden voor de Alarm Speaker (hoofdstuk 126): kleine geluidsbestanden in elk formaat dat je kunt uploaden,
zodat de game tests echt nakijken dat het inlezen werkt (AlarmAudioGameTests).

Wat er gemaakt wordt (in src/main/resources/imttech_test_audio/):
  - alarm_test.mp4  : een filmpje (16x16 beeld + AAC-geluid, stereo 44,1 kHz, 1,5 s), zoals een video van je gsm.
                      De "moov" staat achteraan (zoals ffmpeg het standaard doet), dus de lezer moet kunnen springen.
  - alarm_test.m4a  : alleen AAC-geluid (mono, 48 kHz, 1,0 s).
  - alarm_test_fragmented.m4a : "gefragmenteerd" (stereo, 44,1 kHz, 1,0 s), zoals een m4a van YouTube.
  - alarm_test.mov  : een QuickTime-filmpje (beeld + AAC mono, 44,1 kHz, 1,0 s), zoals van een iPhone.
  - alarm_test.mp3  : mp3 (stereo, 44,1 kHz, 1,0 s) met een ID3-label ervoor, met daarin bytes die op het begin van een
                      mp3-frame lijken (0xFF 0xFB): zo testen we dat het label echt overgeslagen wordt.
  - alarm_test.wav  : gewone wav (16 bit, stereo, 48 kHz, 0,2 s).
  - alarm_test_float.wav : wav met float-getallen (mono, 8 kHz, 0,5 s), om het omhoog omrekenen te testen.
  - alarm_test.aac   : een los AAC-bestand (ADTS, mono, 44,1 kHz, 1,0 s).
  - alarm_test_id3.aac : hetzelfde met een ID3-label ervoor (dat moet naar de AAC-lezer, niet naar mp3).
  - alarm_test_22khz.mp3 : mp3 van 22,05 kHz (MPEG-2) met een ID3-label: JavaMP3 kent dat niet, dus het spel moet
                      netjes "mp3_low_rate" zeggen.

Het geluid is overal een sirene die van 600 naar 1200 Hz glijdt (links iets anders dan rechts).

Draaien (heeft PyAV, numpy en soundfile nodig; PyAV zit niet in de gewone Python):
    py -3.12 -m venv venv-av
    venv-av\\Scripts\\python.exe -m pip install av numpy soundfile
    venv-av\\Scripts\\python.exe tools/generate_alarm_test_audio.py
Alleen de drie bestanden van de nalezing (de rest laten staan):
    venv-av\\Scripts\\python.exe tools/generate_alarm_test_audio.py extra
"""

import os
import struct
import sys

import numpy as np

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "imttech_test_audio")


def siren(seconds, rate, stereo):
    """Een sirene: 600 -> 1200 Hz in de helft van de tijd, en weer terug. Rechts een kwart toon hoger."""
    t = np.arange(int(seconds * rate)) / rate
    sweep = 600.0 + 600.0 * (1.0 - np.abs(2.0 * (t / seconds) - 1.0))
    phase = 2.0 * np.pi * np.cumsum(sweep) / rate
    left = 0.5 * np.sin(phase)
    if not stereo:
        return left.astype(np.float32)
    right = 0.5 * np.sin(phase * 1.03)
    return np.stack([left, right], axis=1).astype(np.float32)


def write_aac(path, seconds, rate, stereo, with_video, fmt="mp4", options=None):
    import av

    container = av.open(path, mode="w", format=fmt, options=options or {})
    if with_video:
        video = container.add_stream("mpeg4", rate=10)
        video.width = 16
        video.height = 16
        video.pix_fmt = "yuv420p"
    audio = container.add_stream("aac", rate=rate)
    audio.layout = "stereo" if stereo else "mono"
    samples = siren(seconds, rate, stereo)
    if stereo:
        planar = np.ascontiguousarray(samples.T)
    else:
        planar = samples.reshape(1, -1)
    frame_size = 1024
    pts = 0
    for start in range(0, planar.shape[1], frame_size):
        chunk = planar[:, start:start + frame_size]
        frame = av.AudioFrame.from_ndarray(np.ascontiguousarray(chunk), format="fltp", layout=audio.layout.name)
        frame.sample_rate = rate
        frame.pts = pts
        pts += chunk.shape[1]
        for packet in audio.encode(frame):
            container.mux(packet)
    for packet in audio.encode(None):
        container.mux(packet)
    if with_video:
        for i in range(int(seconds * 10)):
            image = np.full((16, 16, 3), (i * 17) % 255, dtype=np.uint8)
            frame = av.VideoFrame.from_ndarray(image, format="rgb24")
            frame.pts = i
            for packet in video.encode(frame):
                container.mux(packet)
        for packet in video.encode(None):
            container.mux(packet)
    container.close()


def id3_tag():
    """Een ID3v2.3-label met een titel en een "plaatje" vol bytes die op een mp3-frame lijken."""

    def frame(frame_id, payload):
        return frame_id.encode("ascii") + struct.pack(">I", len(payload)) + b"\x00\x00" + payload

    title = frame("TIT2", b"\x00" + "Alarm test".encode("latin-1"))
    fake_picture = frame("PRIV", b"imttech\x00" + bytes([0xFF, 0xFB, 0x90, 0x64]) * 64)
    body = title + fake_picture
    size = len(body)
    syncsafe = bytes([(size >> 21) & 0x7F, (size >> 14) & 0x7F, (size >> 7) & 0x7F, size & 0x7F])
    return b"ID3\x03\x00\x00" + syncsafe + body


def extra():
    """De bestanden van de nalezing: los AAC (met en zonder ID3-label) en een mp3 met een lage snelheid."""
    import soundfile

    os.makedirs(OUT, exist_ok=True)
    aac = os.path.join(OUT, "alarm_test.aac")
    write_aac(aac, 1.0, 44100, False, False, fmt="adts")
    with open(aac, "rb") as source:
        data = source.read()
    with open(os.path.join(OUT, "alarm_test_id3.aac"), "wb") as target:
        target.write(id3_tag() + data)

    low = os.path.join(OUT, "alarm_test_22khz_plain.mp3")
    soundfile.write(low, siren(1.0, 22050, False), 22050, format="MP3", subtype="MPEG_LAYER_III")
    with open(low, "rb") as source:
        data = source.read()
    os.remove(low)
    with open(os.path.join(OUT, "alarm_test_22khz.mp3"), "wb") as target:
        target.write(id3_tag() + data)

    for name in ("alarm_test.aac", "alarm_test_id3.aac", "alarm_test_22khz.mp3"):
        print(name, os.path.getsize(os.path.join(OUT, name)), "bytes")


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "extra":
        return extra()
    import soundfile

    os.makedirs(OUT, exist_ok=True)
    write_aac(os.path.join(OUT, "alarm_test.mp4"), 1.5, 44100, True, True)
    write_aac(os.path.join(OUT, "alarm_test.m4a"), 1.0, 48000, False, False)
    # Gefragmenteerd (zoals een m4a van YouTube): de stukjes staan in losse moof-doosjes, niet in de moov.
    write_aac(os.path.join(OUT, "alarm_test_fragmented.m4a"), 1.0, 44100, True, False,
              options={"movflags": "frag_keyframe+empty_moov+default_base_moof", "frag_duration": "200000"})
    # QuickTime (.mov, zoals van een iPhone): de AAC-instellingen staan daar in een extra "wave"-doosje.
    write_aac(os.path.join(OUT, "alarm_test.mov"), 1.0, 44100, False, True, fmt="mov")

    mp3_plain = os.path.join(OUT, "alarm_test_plain.mp3")
    soundfile.write(mp3_plain, siren(1.0, 44100, True), 44100, format="MP3", subtype="MPEG_LAYER_III")
    with open(mp3_plain, "rb") as source:
        data = source.read()
    os.remove(mp3_plain)
    with open(os.path.join(OUT, "alarm_test.mp3"), "wb") as target:
        target.write(id3_tag() + data)

    soundfile.write(os.path.join(OUT, "alarm_test.wav"), siren(0.2, 48000, True), 48000, subtype="PCM_16")
    soundfile.write(os.path.join(OUT, "alarm_test_float.wav"), siren(0.5, 8000, False), 8000, subtype="FLOAT")
    extra()

    for name in sorted(os.listdir(OUT)):
        print(name, os.path.getsize(os.path.join(OUT, name)), "bytes")


if __name__ == "__main__":
    sys.exit(main())
