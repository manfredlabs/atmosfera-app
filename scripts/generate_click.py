import sys
import numpy as np
from scipy.io.wavfile import write

sample_rate = 44100

def generate_click(frequency, duration, amplitude, filename):
    t = np.linspace(0, duration, int(sample_rate * duration), False)
    click = amplitude * np.sin(2 * np.pi * frequency * t)
    fade = np.linspace(1, 0, len(click)) ** 2
    click = click * fade
    audio = np.int16(click * 32767)
    write(filename, sample_rate, audio)
    print(f"✅ {filename} gerado! ({frequency}Hz, {duration*1000:.0f}ms)")

if __name__ == "__main__":
    # Click normal — 600Hz, 30ms, mais suave
    generate_click(600, 0.03, 0.5, "click.wav")

    # Click accent — 900Hz, 35ms, mais presente
    generate_click(900, 0.035, 0.7, "click_accent.wav")

    print("\n🥁 Clicks gerados!")
