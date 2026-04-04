"""
Gera os ícones do Atmosfera para Android (adaptive icon).
Onda sonora / equalizer minimalista.
Fundo escuro (#1A1A2E), onda em âmbar (#FFB300).
"""
import math
from pathlib import Path

# Android adaptive icon sizes (foreground PNG)
SIZES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}

BG_COLOR = "#0A0A0A"
WAVE_COLOR = "#FFB300"
WAVE_GLOW = "#FFB300"


def generate_foreground_svg(size: int) -> str:
    """Generates SVG for the foreground layer with a sound wave / equalizer."""
    cx, cy = size / 2, size / 2
    safe_zone = size * 66 / 108  # safe zone is 66/108 of total
    safe_r = safe_zone / 2

    # Equalizer bars — 5 bars centered
    num_bars = 5
    bar_width = safe_r * 0.12
    gap = safe_r * 0.22
    total_w = num_bars * bar_width + (num_bars - 1) * gap

    # Heights (proportion of safe_r) — symmetric wave pattern
    heights = [0.35, 0.65, 1.0, 0.65, 0.35]

    bars_svg = []
    start_x = cx - total_w / 2

    for i, h in enumerate(heights):
        x = start_x + i * (bar_width + gap)
        bar_h = safe_r * h * 0.85
        y = cy - bar_h / 2
        rx = bar_width / 2  # rounded caps

        bars_svg.append(
            f'    <rect x="{x:.1f}" y="{y:.1f}" width="{bar_width:.1f}" '
            f'height="{bar_h:.1f}" rx="{rx:.1f}" fill="{WAVE_COLOR}" />'
        )

    bars_str = "\n".join(bars_svg)

    return f"""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">
  <defs>
    <filter id="glow">
      <feGaussianBlur stdDeviation="{size * 0.015:.1f}" result="blur"/>
      <feMerge>
        <feMergeNode in="blur"/>
        <feMergeNode in="SourceGraphic"/>
      </feMerge>
    </filter>
  </defs>
  <g filter="url(#glow)">
{bars_str}
  </g>
</svg>"""


def generate_background_svg(size: int) -> str:
    """Generates SVG for the background layer (solid dark color)."""
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">
  <rect width="{size}" height="{size}" fill="{BG_COLOR}" />
</svg>"""


def svg_to_png(svg_content: str, size: int, output_path: Path):
    """Convert SVG to PNG using cairosvg or Pillow fallback."""
    try:
        import cairosvg
        cairosvg.svg2png(
            bytestring=svg_content.encode(),
            write_to=str(output_path),
            output_width=size,
            output_height=size,
        )
        return True
    except ImportError:
        pass

    # Fallback: use Pillow to draw the icon programmatically
    try:
        from PIL import Image, ImageDraw
        return draw_with_pillow(size, output_path)
    except ImportError:
        print("⚠️  Instale cairosvg ou Pillow: pip install cairosvg   ou   pip install Pillow")
        return False


def draw_with_pillow(size: int, output_path: Path, is_foreground: bool = True):
    """Draw icon directly with Pillow (no SVG dependency)."""
    from PIL import Image, ImageDraw

    img = Image.new("RGBA", (size, size), (0, 0, 0, 0) if is_foreground else (10, 10, 10, 255))
    draw = ImageDraw.Draw(img)

    if not is_foreground:
        img.save(str(output_path), "PNG")
        return True

    cx, cy = size / 2, size / 2
    safe_r = (size * 66 / 108) / 2

    num_bars = 5
    bar_width = safe_r * 0.12
    gap = safe_r * 0.22
    total_w = num_bars * bar_width + (num_bars - 1) * gap
    heights = [0.35, 0.65, 1.0, 0.65, 0.35]

    start_x = cx - total_w / 2
    amber = (255, 179, 0, 255)

    for i, h in enumerate(heights):
        x = start_x + i * (bar_width + gap)
        bar_h = safe_r * h * 0.85
        y = cy - bar_h / 2
        rx = bar_width / 2

        draw.rounded_rectangle(
            [x, y, x + bar_width, y + bar_h],
            radius=rx,
            fill=amber,
        )

    img.save(str(output_path), "PNG")
    return True


def main():
    project_root = Path(__file__).parent.parent
    res_dir = project_root / "app" / "src" / "main" / "res"

    generated = 0

    for density, size in SIZES.items():
        mipmap_dir = res_dir / f"mipmap-{density}"
        mipmap_dir.mkdir(parents=True, exist_ok=True)

        # Foreground
        fg_path = mipmap_dir / "ic_launcher_foreground.png"
        success = draw_with_pillow(size, fg_path, is_foreground=True)
        if success:
            print(f"✅ {density}: foreground ({size}x{size})")
            generated += 1

        # Background
        bg_path = mipmap_dir / "ic_launcher_background.png"
        success = draw_with_pillow(size, bg_path, is_foreground=False)
        if success:
            print(f"✅ {density}: background ({size}x{size})")
            generated += 1

    # Generate the adaptive icon XML (v26+)
    mipmap_anydpi = res_dir / "mipmap-anydpi-v26"
    mipmap_anydpi.mkdir(parents=True, exist_ok=True)

    ic_launcher_xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""
    (mipmap_anydpi / "ic_launcher.xml").write_text(ic_launcher_xml)
    (mipmap_anydpi / "ic_launcher_round.xml").write_text(ic_launcher_xml)
    print("✅ adaptive icon XMLs gerados")

    # Legacy icon (for pre-API26) — just a square version
    for density, size in SIZES.items():
        mipmap_dir = res_dir / f"mipmap-{density}"
        legacy_size = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}[density]
        legacy_path = mipmap_dir / "ic_launcher.png"
        draw_legacy_icon(legacy_size, legacy_path)
        # Also round version
        round_path = mipmap_dir / "ic_launcher_round.png"
        draw_legacy_icon(legacy_size, round_path, round_mask=True)
        print(f"✅ {density}: legacy icon ({legacy_size}x{legacy_size})")
        generated += 2

    print(f"\n🎨 {generated} arquivos de ícone gerados!")


def draw_legacy_icon(size: int, output_path: Path, round_mask: bool = False):
    """Draw a legacy (non-adaptive) launcher icon."""
    from PIL import Image, ImageDraw

    img = Image.new("RGBA", (size, size), (10, 10, 10, 255))
    draw = ImageDraw.Draw(img)

    cx, cy = size / 2, size / 2
    safe_r = size * 0.4

    num_bars = 5
    bar_width = safe_r * 0.14
    gap = safe_r * 0.24
    total_w = num_bars * bar_width + (num_bars - 1) * gap
    heights = [0.35, 0.65, 1.0, 0.65, 0.35]
    start_x = cx - total_w / 2
    amber = (255, 179, 0, 255)

    for i, h in enumerate(heights):
        x = start_x + i * (bar_width + gap)
        bar_h = safe_r * h * 0.85
        y = cy - bar_h / 2
        rx = bar_width / 2
        draw.rounded_rectangle(
            [x, y, x + bar_width, y + bar_h],
            radius=rx,
            fill=amber,
        )

    if round_mask:
        mask = Image.new("L", (size, size), 0)
        mask_draw = ImageDraw.Draw(mask)
        mask_draw.ellipse([0, 0, size - 1, size - 1], fill=255)
        img.putalpha(mask)

    img.save(str(output_path), "PNG")


if __name__ == "__main__":
    main()
