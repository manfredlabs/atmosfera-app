import sys
import numpy as np
from scipy.io.wavfile import write
from scipy.signal import butter, sosfilt

# ─── Configurações ───────────────────────────────────────────
SAMPLE_RATE = 44100
DURATION = 30
CROSSFADE_SEC = 2.0

# ─── Frequências base (oitava 3 — corpo principal) ───────────
NOTE_FREQS = {
    "c": 130.81, "cs": 138.59, "d": 146.83, "ds": 155.56,
    "e": 164.81, "f": 174.61, "fs": 185.00, "g": 196.00,
    "gs": 207.65, "a": 220.00, "as": 233.08, "b": 246.94,
}

CHORD_INTERVALS = {
    "neu": [0, 7],
    "maj": [0, 4, 7],
    "min": [0, 3, 7],
}

# Base octave layers — adjusted per note in get_chord_freqs()
BASE_OCTAVE_LAYERS = [
    (0.5, 0.02),  # oitava -1 — sub
    (1,   0.26),  # oitava 0 — corpo
    (2,   0.40),  # oitava +1 — presença
    (4,   0.26),  # oitava +2 — ar
    (8,   0.06),  # oitava +3 — shimmer
]

# Reference frequency for calibration (C3 = 130.81 Hz)
REF_FREQ = 130.81

# Detuning wider — mais stereo
VOICE_DETUNES_L = [-13.2, -7.1, -1.8, +3.7, +9.5]
VOICE_DETUNES_R = [-10.7, -4.3, +1.5, +6.8, +12.9]

NOTE_NAMES = list(NOTE_FREQS.keys())


def freq_from_semitone_offset(base_freq: float, semitones: int) -> float:
    return base_freq * (2 ** (semitones / 12.0))


def cents_to_ratio(cents: float) -> float:
    return 2 ** (cents / 1200.0)


def make_triangle(freq: float, t: np.ndarray) -> np.ndarray:
    """Triangle wave — softer harmonics than saw, string-like."""
    sig = np.zeros_like(t)
    for h in range(0, 5):
        n = 2 * h + 1  # odd harmonics only
        sig += ((-1) ** h) * np.sin(2 * np.pi * freq * n * t) / (n ** 2)
    return sig * (8 / (np.pi ** 2))


def lowpass(signal: np.ndarray, cutoff: float, sr: int, order: int = 4) -> np.ndarray:
    nyq = sr / 2
    cutoff = min(cutoff, nyq * 0.95)
    sos = butter(order, cutoff / nyq, btype='low', output='sos')
    return sosfilt(sos, signal)


def multi_tap_reverb(signal_l: np.ndarray, signal_r: np.ndarray, sr: int):
    """Dense multi-tap reverb for lush, diffuse atmosphere."""
    taps_l = [
        (0.023, 0.25), (0.047, 0.22), (0.073, 0.19), (0.109, 0.16),
        (0.151, 0.13), (0.199, 0.10), (0.263, 0.07), (0.337, 0.05),
        (0.421, 0.03), (0.509, 0.02),
    ]
    taps_r = [
        (0.029, 0.24), (0.059, 0.21), (0.089, 0.18), (0.131, 0.15),
        (0.173, 0.12), (0.227, 0.09), (0.293, 0.06), (0.367, 0.04),
        (0.449, 0.03), (0.541, 0.02),
    ]

    out_l = signal_l.copy()
    out_r = signal_r.copy()

    # Cross-feed: decorrelated reverb tails for width
    for delay_sec, gain in taps_l:
        d = int(delay_sec * sr)
        out_l[d:] += signal_l[:-d] * gain
        out_r[d:] += signal_l[:-d] * gain * 0.25

    for delay_sec, gain in taps_r:
        d = int(delay_sec * sr)
        out_r[d:] += signal_r[:-d] * gain
        out_l[d:] += signal_r[:-d] * gain * 0.25

    return out_l, out_r


def get_chord_freqs(root_name: str, chord_type: str):
    base_freq = NOTE_FREQS[root_name]
    intervals = CHORD_INTERVALS[chord_type]

    # Shift octave weights: higher notes get more weight on lower octaves
    freq_ratio = base_freq / REF_FREQ  # 1.0 for C, ~1.9 for B
    shift = np.log2(freq_ratio)  # 0 for C, ~0.92 for B

    adjusted_layers = []
    for oct_mult, oct_vol in BASE_OCTAVE_LAYERS:
        # Shift weight toward lower octaves for higher notes
        effective_mult = oct_mult
        # Reduce volume of upper octaves proportionally to how high the note is
        if oct_mult >= 2:
            oct_vol *= max(0.15, 1.0 - shift * 0.7)
        elif oct_mult <= 0.5:
            oct_vol *= (1.0 + shift * 0.3)
        else:  # fundamental
            oct_vol *= (1.0 + shift * 0.4)
        adjusted_layers.append((effective_mult, oct_vol))

    # Normalize layer volumes
    total_vol = sum(v for _, v in adjusted_layers)
    adjusted_layers = [(m, v / total_vol) for m, v in adjusted_layers]

    freqs = []
    for semitone in intervals:
        note_freq = freq_from_semitone_offset(base_freq, semitone)
        note_vol = 1.0 if semitone == 0 else 0.7
        for oct_mult, oct_vol in adjusted_layers:
            freqs.append((note_freq * oct_mult, oct_vol * note_vol))
    return freqs


def generate_pad(note: str, chord_type: str = "maj"):
    layers = get_chord_freqs(note, chord_type)
    cf_samples = int(CROSSFADE_SEC * SAMPLE_RATE)
    num_samples = SAMPLE_RATE * DURATION
    total_samples = num_samples + cf_samples
    t = np.arange(total_samples) / SAMPLE_RATE

    pad_l = np.zeros(total_samples, dtype=np.float64)
    pad_r = np.zeros(total_samples, dtype=np.float64)

    rng = np.random.default_rng(42)

    for freq, vol in layers:
        cycles = int(freq * DURATION)
        corrected_freq = cycles / DURATION

        # Left channel voices
        for detune_cents in VOICE_DETUNES_L:
            voice_freq = corrected_freq * cents_to_ratio(detune_cents)
            phase = rng.uniform(0, 2 * np.pi)

            # Random pitch wandering (simulates natural vibrato of a string player)
            # Brownian motion filtered to ~4-6 Hz vibrato rate, ±3 cents depth
            noise_raw = rng.normal(0, 1, len(t))
            vibrato = lowpass(noise_raw, 5.0, SAMPLE_RATE, order=2)
            vibrato = vibrato / (np.max(np.abs(vibrato)) + 1e-12) * 0.002  # ±0.2% freq variation
            freq_mod = voice_freq * (1 + vibrato)
            # Integrate frequency to get phase
            inst_phase = np.cumsum(2 * np.pi * freq_mod / SAMPLE_RATE) + phase

            tri = make_triangle(voice_freq, t)
            sine = np.sin(inst_phase)
            voice = 0.35 * tri + 0.65 * sine

            # Slow amplitude breathing per voice (random, not periodic)
            breath = lowpass(rng.normal(0, 1, len(t)), 0.3, SAMPLE_RATE, order=2)
            breath = 0.85 + 0.15 * (breath / (np.max(np.abs(breath)) + 1e-12))
            pad_l += voice * vol / len(VOICE_DETUNES_L) * breath

        # Right channel voices
        for detune_cents in VOICE_DETUNES_R:
            voice_freq = corrected_freq * cents_to_ratio(detune_cents)
            phase = rng.uniform(0, 2 * np.pi)

            noise_raw = rng.normal(0, 1, len(t))
            vibrato = lowpass(noise_raw, 5.0, SAMPLE_RATE, order=2)
            vibrato = vibrato / (np.max(np.abs(vibrato)) + 1e-12) * 0.002
            freq_mod = voice_freq * (1 + vibrato)
            inst_phase = np.cumsum(2 * np.pi * freq_mod / SAMPLE_RATE) + phase

            tri = make_triangle(voice_freq, t)
            sine = np.sin(inst_phase)
            voice = 0.35 * tri + 0.65 * sine

            breath = lowpass(rng.normal(0, 1, len(t)), 0.3, SAMPLE_RATE, order=2)
            breath = 0.85 + 0.15 * (breath / (np.max(np.abs(breath)) + 1e-12))
            pad_r += voice * vol / len(VOICE_DETUNES_R) * breath

    # Adaptive low-pass — lower cutoff for higher notes to keep warmth
    base_freq = NOTE_FREQS[note]
    freq_ratio = base_freq / REF_FREQ
    cutoff = min(base_freq * (8 / max(freq_ratio, 1.0)), 4500)
    pad_l = lowpass(pad_l, cutoff, SAMPLE_RATE)
    pad_r = lowpass(pad_r, cutoff, SAMPLE_RATE)

    # NO LFO — let the detuning and reverb create all the movement

    # Subtle filtered noise — rosin/bow texture
    noise_l = rng.normal(0, 1, len(t))
    noise_r = rng.normal(0, 1, len(t))
    noise_l = lowpass(noise_l, 800, SAMPLE_RATE, order=4)
    noise_r = lowpass(noise_r, 800, SAMPLE_RATE, order=4)
    noise_level = 0.025
    pad_l += noise_l * noise_level * np.max(np.abs(pad_l))
    pad_r += noise_r * noise_level * np.max(np.abs(pad_r))

    # Multi-tap reverb
    pad_l, pad_r = multi_tap_reverb(pad_l, pad_r, SAMPLE_RATE)

    # Normalize with headroom (-1.5 dB)
    peak = max(np.max(np.abs(pad_l)), np.max(np.abs(pad_r)))
    gain = 0.85 / peak
    pad_l *= gain
    pad_r *= gain

    # Crossfade for seamless loop
    fade_in = np.linspace(0, 1, cf_samples)
    fade_out = np.linspace(1, 0, cf_samples)
    pad_l[:cf_samples] = pad_l[:cf_samples] * fade_in + pad_l[num_samples:] * fade_out
    pad_r[:cf_samples] = pad_r[:cf_samples] * fade_in + pad_r[num_samples:] * fade_out
    pad_l = pad_l[:num_samples]
    pad_r = pad_r[:num_samples]

    # Interleave stereo and write
    stereo = np.column_stack((pad_l, pad_r))
    audio = np.int16(stereo * 32767)

    filename = f"pad_{note}_{chord_type}.wav"
    write(filename, SAMPLE_RATE, audio)
    chord_labels = {"neu": "NEUTRO", "maj": "MAIOR", "min": "MENOR"}
    chord_label = chord_labels[chord_type]
    intervals_str = "+".join([f"{s}st" for s in CHORD_INTERVALS[chord_type]])
    size_mb = audio.nbytes / (1024 * 1024)
    print(f"✅ {filename} ({note.upper()} {chord_label}, {intervals_str}, {DURATION}s, stereo, {size_mb:.1f}MB)")


# ─── CLI ─────────────────────────────────────────────────────
if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Uso: python generate_pad.py <nota|all> [neu|maj|min|all]")
        print("  Notas: c, cs, d, ds, e, f, fs, g, gs, a, as, b")
        print("  Tipo:  neu, maj, min, all (padrão)")
        print("  Exemplo: python generate_pad.py c neu")
        print("           python generate_pad.py all all")
        sys.exit(1)

    arg = sys.argv[1].lower()
    chord_type = sys.argv[2].lower() if len(sys.argv) > 2 else "all"

    types = ["neu", "maj", "min"] if chord_type == "all" else [chord_type]
    notes = NOTE_NAMES if arg == "all" else [arg]

    if arg != "all" and arg not in NOTE_FREQS:
        print(f"❌ Nota '{arg}' não reconhecida.")
        print(f"   Notas válidas: {', '.join(NOTE_NAMES)}")
        sys.exit(1)

    for note in notes:
        for ct in types:
            generate_pad(note, ct)

    total = len(notes) * len(types)
    print(f"\n🎹 {total} pads gerados!")
