#!/usr/bin/env python3
"""
Synthesises the fork's own elevator chimes into src/main/resources/assets/movingelevators/sounds/.

The "Modern" scheme wants clean electronic chimes, and nothing in the vanilla sound set is one --
every candidate is a struck acoustic instrument with a long tail. These are additive: a strong
fundamental, a couple of quiet harmonics for body, and one deliberately inharmonic partial so the
tone reads as a chime rather than a test tone.

Minecraft only registers the JOrbis codec and hardcodes the .ogg extension, so the output has to be
Ogg Vorbis specifically -- a WAV renamed .ogg will not load, and Opus will not either.

That needs an encoder this script cannot supply. macOS's afconvert lists 'vorb' under the Ogg
container but only decodes it; every encode attempt fails with 'fmt?'. So: brew install ffmpeg (or
vorbis-tools for oggenc), then run this from the repo root:

    python3 tools/generate-sounds.py

Until then the Modern scheme uses vanilla samples, which is why it is written as pitch offsets
against one sound rather than against these files.
"""

import math
import os
import struct
import subprocess
import sys
import tempfile
import wave

RATE = 44100
OUT_DIR = os.path.join("src", "main", "resources", "assets", "movingelevators", "sounds")

# (relative frequency, amplitude, decay seconds). The 4.98 partial is detuned off the harmonic
# series on purpose -- exact harmonics sound like an organ, a slight offset sounds like metal.
CHIME_PARTIALS = [(1.0, 1.0, 0.55), (2.0, 0.35, 0.32), (3.0, 0.12, 0.22), (4.98, 0.07, 0.15)]
BLIP_PARTIALS = [(1.0, 1.0, 0.09), (2.0, 0.18, 0.05)]

ATTACK = 0.004  # a hard start clicks; 4ms is inaudible as an attack but removes it


def render(freq, seconds, partials, peak=0.85):
    frames = int(RATE * seconds)
    samples = [0.0] * frames
    for ratio, amp, decay in partials:
        w = 2 * math.pi * freq * ratio
        for i in range(frames):
            t = i / RATE
            samples[i] += amp * math.exp(-t / decay) * math.sin(w * t)

    attack_frames = max(1, int(RATE * ATTACK))
    release_frames = max(1, int(RATE * 0.01))
    for i in range(frames):
        if i < attack_frames:
            samples[i] *= i / attack_frames
        # Fade the last few ms to true zero, or the file ends on a discontinuity and clicks.
        if i > frames - release_frames:
            samples[i] *= (frames - i) / release_frames

    loudest = max(abs(s) for s in samples) or 1.0
    return [s / loudest * peak for s in samples]


def encoder(src, dst):
    """Whichever Vorbis encoder is installed. afconvert is deliberately not tried -- it decodes
    Vorbis but cannot encode it, and fails only once it has already created the output file."""
    import shutil
    if shutil.which("ffmpeg"):
        return ["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-c:a", "libvorbis", "-q:a", "6", dst]
    if shutil.which("oggenc"):
        return ["oggenc", "-Q", "-q", "6", "-o", dst, src]
    sys.exit("no Ogg Vorbis encoder found -- brew install ffmpeg")


def write_ogg(name, samples):
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    try:
        with wave.open(wav_path, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(RATE)
            wav.writeframes(b"".join(struct.pack("<h", int(s * 32767)) for s in samples))

        os.makedirs(OUT_DIR, exist_ok=True)
        ogg_path = os.path.join(OUT_DIR, name + ".ogg")
        subprocess.run(encoder(wav_path, ogg_path), check=True)

        with open(ogg_path, "rb") as f:
            head = f.read(64)
        if not head.startswith(b"OggS") or b"\x01vorbis" not in head:
            sys.exit(name + ": not Ogg Vorbis -- Minecraft's JOrbis codec will not read it")
        print("%-28s %6d bytes" % (name + ".ogg", os.path.getsize(ogg_path)))
    finally:
        os.unlink(wav_path)


# A perfect fourth apart: wide enough that the pair reads as two notes, close enough to sound
# like one chime unit rather than two unrelated beeps.
write_ogg("modern_chime_high", render(1046.50, 1.0, CHIME_PARTIALS))          # C6
write_ogg("modern_chime_low", render(783.99, 1.0, CHIME_PARTIALS))            # G5
write_ogg("modern_passing_floor", render(1567.98, 0.2, BLIP_PARTIALS, 0.7))   # G6, short and soft
