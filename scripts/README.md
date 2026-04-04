# Scripts de Geração de Áudio

Scripts Python usados para gerar os arquivos de áudio do Atmosfera.

## Arquivos

- `generate_pad.py` — Gera os pads de cada nota musical
- `generate_click.py` — Gera os sons de click do metrônomo

## Uso

```bash
# Gerar pad para uma nota específica
python generate_pad.py

# Gerar click
python generate_click.py
```

## Notas geradas

| Arquivo        | Nota |
|----------------|------|
| `pad_c.wav`    | C    |
| `pad_cs.wav`   | C#   |
| `pad_d.wav`    | D    |
| `pad_ds.wav`   | D#   |
| `pad_e.wav`    | E    |
| `pad_f.wav`    | F    |
| `pad_fs.wav`   | F#   |
| `pad_g.wav`    | G    |
| `pad_gs.wav`   | G#   |
| `pad_a.wav`    | A    |
| `pad_as.wav`   | A#   |
| `pad_b.wav`    | B    |

Os arquivos gerados devem ser colocados em `app/src/main/res/raw/`.
