import sys
import numpy as np
from scipy.io.wavfile import write

# ─── Configurações ───────────────────────────────────────────
SAMPLE_RATE = 44100
DURATION = 15
CROSSFADE_SEC = 1.0

# ─── Frequências base (oitava 2 — referência grave) ──────────
NOTE_FREQS = {
    "c": 65.41, "cs": 69.30, "d": 73.42, "ds": 77.78,
    "e": 82.41, "f": 87.31, "fs": 92.50, "g": 98.00,
    "gs": 103.83, "a": 110.00, "as": 116.54, "b": 123.47,
}

# Intervalos em semitons relativos à raiz
CHORD_INTERVALS = {
    "neu": [0, 7],      # raiz, quinta (sem terça — neutro)
    "maj": [0, 4, 7],   # raiz, terça maior, quinta
    "min": [0, 3, 7],   # raiz, terça menor, quinta
}

# Camadas por oitava (cada nota do acorde é tocada nestas oitavas)
OCTAVE_LAYERS = [
    (1, 0.50),  # oitava 2 (base)
    (2, 0.35),  # oitava 3 (corpo)
    (4, 0.15),  # oitava 4 (presença)
]

NOTE_NAMES = list(NOTE_FREQS.keys())
ALL_NOTES_SEMITONE = ["c", "cs", "d", "ds", "e", "f", "fs", "g", "gs", "a", "as", "b"]


def freq_from_semitone_offset(base_freq: float, semitones: int) -> float:
    return base_freq * (2 ** (semitones / 12.0))


def get_chord_freqs(root_name: str, chord_type: str) -> list[tuple[float, float]]:
    """Retorna as frequências de todas as notas do acorde em múltiplas oitavas."""
    base_freq = NOTE_FREQS[root_name]
    intervals = CHORD_INTERVALS[chord_type]
    freqs = []
    for semitone in intervals:
        note_freq = freq_from_semitone_offset(base_freq, semitone)
        # Volume menor pra terça e quinta (raiz domina)
        note_vol = 1.0 if semitone == 0 else 0.7
        for oct_mult, oct_vol in OCTAVE_LAYERS:
            freqs.append((note_freq * oct_mult, oct_vol * note_vol))
    return freqs


def generate_pad(note: str, chord_type: str = "maj"):
    layers = get_chord_freqs(note, chord_type)
    cf_samples = int(CROSSFADE_SEC * SAMPLE_RATE)
    num_samples = SAMPLE_RATE * DURATION

    total_samples = num_samples + cf_samples
    t = np.arange(total_samples) / SAMPLE_RATE

    pad = np.zeros(total_samples, dtype=np.float64)

    for freq, vol in layers:
        cycles = int(freq * DURATION)
        corrected_freq = cycles / DURATION

        detune1 = np.sin(2 * np.pi * (corrected_freq * 0.995) * t)
        detune2 = np.sin(2 * np.pi * (corrected_freq * 1.005) * t)

        pad += vol * (0.5 * detune1 + 0.5 * detune2)

    # LFO
    lfo_cycles = int(0.1 * DURATION)
    lfo_corrected = lfo_cycles / DURATION
    lfo = 0.8 + 0.2 * np.sin(2 * np.pi * lfo_corrected * t)
    pad = pad * lfo

    # Reverb simples
    delay = int(0.03 * SAMPLE_RATE)
    reverb = np.zeros_like(pad)
    reverb[delay:] = pad[:-delay]
    pad = pad + 0.2 * reverb

    # Normaliza com headroom (-1dB)
    pad = pad / np.max(np.abs(pad)) * 0.9

    # Overlap-add pra loop seamless
    fade_in = np.linspace(0, 1, cf_samples)
    fade_out = np.linspace(1, 0, cf_samples)
    pad[:cf_samples] = pad[:cf_samples] * fade_in + pad[num_samples:] * fade_out

    pad = pad[:num_samples]

    audio = np.int16(pad * 32767)

    suffix = chord_type
    filename = f"pad_{note}_{suffix}.wav"
    write(filename, SAMPLE_RATE, audio)
    chord_labels = {"neu": "NEUTRO", "maj": "MAIOR", "min": "MENOR"}
    chord_label = chord_labels[chord_type]
    intervals_str = "+".join([f"{s}st" for s in CHORD_INTERVALS[chord_type]])
    print(f"✅ {filename} gerado! ({note.upper()} {chord_label}, {intervals_str}, {DURATION}s)")


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
        for t in types:
            generate_pad(note, t)

    total = len(notes) * len(types)
    print(f"\n🎹 {total} pads gerados!")
