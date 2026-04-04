# 🎵 Atmosfera

Android pad & metronome app for live worship musicians.

## Features

- **12-note pad grid** with NEU (neutral), MAJ (major) and MIN (minor) modes
- **Built-in metronome (click)** with configurable BPM and accent patterns
- **Playlist** — save songs with pad key, BPM, time signature and click settings
- **Drag-to-reorder** songs in your setlist
- **Playlist lock mode** — lock the screen during live performance
- **Independent audio routing** — send pad and click to different channels (L/M/R)
- **Smooth pad crossfade** — seamless transitions between notes
- **Offline** — no internet required, no data collected

## Tech Stack

- Kotlin + Jetpack Compose
- Room Database
- Media3 ExoPlayer
- Material 3

## Building

1. Clone the repo
2. Open in Android Studio
3. Build & run

## Audio Generation

Pad and click sounds are generated via Python scripts in the `scripts/` folder:

```bash
# Generate all pads (neutral + major + minor)
python scripts/generate_pad.py all all

# Generate click sounds
python scripts/generate_click.py
```

Requires `numpy` and `scipy`.

## Privacy Policy

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md)

## License

MIT License — see [LICENSE](LICENSE)
