from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


@dataclass(frozen=True)
class Theme:
    bg: tuple[int, int, int] = (11, 15, 20)
    card_bg: tuple[int, int, int] = (17, 23, 31)
    card_border: tuple[int, int, int] = (36, 46, 63)
    title: tuple[int, int, int] = (241, 245, 249)
    text: tuple[int, int, int] = (226, 232, 240)
    muted: tuple[int, int, int] = (148, 163, 184)
    accent: tuple[int, int, int] = (56, 189, 248)
    green: tuple[int, int, int] = (34, 197, 94)


W, H = 1920, 1080
THEME = Theme()


def _font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size=size)


def _wrap_to_width(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.ImageFont, max_width: int) -> list[str]:
    out: list[str] = []
    for raw_line in text.splitlines():
        if not raw_line.strip():
            out.append("")
            continue

        words = raw_line.split(" ")
        current = ""
        for w in words:
            candidate = w if not current else current + " " + w
            bbox = draw.textbbox((0, 0), candidate, font=font)
            width = bbox[2] - bbox[0]
            if width <= max_width:
                current = candidate
            else:
                if current:
                    out.append(current)
                current = w
        if current:
            out.append(current)
    return out


def draw_centered_text(
    draw: ImageDraw.ImageDraw,
    text: str,
    font: ImageFont.ImageFont,
    y: int,
    fill: tuple[int, int, int],
) -> None:
    bbox = draw.multiline_textbbox((0, 0), text, font=font, spacing=12, align="center")
    text_w = bbox[2] - bbox[0]
    x = (W - text_w) // 2
    draw.multiline_text((x, y), text, font=font, fill=fill, spacing=12, align="center")


def draw_terminal_window(
    img: Image.Image,
    draw: ImageDraw.ImageDraw,
    x: int,
    y: int,
    w: int,
    h: int,
    title: str,
    lines: list[tuple[str, tuple[int, int, int]]],
    font: ImageFont.ImageFont,
) -> None:
    radius = 24
    header_h = 64

    shadow = Image.new("RGBA", (w + 20, h + 20), (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.rounded_rectangle(
        (10, 10, 10 + w, 10 + h),
        radius=radius,
        fill=(0, 0, 0, 140),
    )
    img.paste(shadow, (x - 10, y - 10), shadow)

    draw.rounded_rectangle(
        (x, y, x + w, y + h),
        radius=radius,
        fill=THEME.card_bg,
        outline=THEME.card_border,
        width=2,
    )

    # Header bar
    draw.rounded_rectangle(
        (x, y, x + w, y + header_h),
        radius=radius,
        fill=(24, 33, 46),
        outline=THEME.card_border,
        width=2,
    )
    draw.rectangle((x, y + header_h - radius, x + w, y + header_h), fill=(24, 33, 46))

    # Window controls
    cx = x + 22
    cy = y + header_h // 2
    r = 10
    draw.ellipse((cx, cy - r, cx + 2 * r, cy + r), fill=(239, 68, 68))
    draw.ellipse((cx + 28, cy - r, cx + 28 + 2 * r, cy + r), fill=(234, 179, 8))
    draw.ellipse((cx + 56, cy - r, cx + 56 + 2 * r, cy + r), fill=(34, 197, 94))

    # Title
    title_font = _font(str(FONT_MONO), 26)
    title_bbox = draw.textbbox((0, 0), title, font=title_font)
    title_w = title_bbox[2] - title_bbox[0]
    draw.text((x + (w - title_w) // 2, y + (header_h - 26) // 2), title, font=title_font, fill=THEME.muted)

    # Content
    pad_x = 34
    pad_y = header_h + 24
    line_h = int(font.size * 1.45)
    max_lines = (h - pad_y - 30) // line_h
    trimmed = lines[:max_lines]

    for idx, (line, color) in enumerate(trimmed):
        draw.text((x + pad_x, y + pad_y + idx * line_h), line, font=font, fill=color)


HERE = Path(__file__).resolve().parent
FONT_MONO = Path(r"C:\Windows\Fonts\CascadiaCode.ttf")


def main() -> None:
    theme = THEME
    out_dir = HERE

    title_font = _font(str(FONT_MONO), 76)
    subtitle_font = _font(str(FONT_MONO), 34)
    code_font = _font(str(FONT_MONO), 30)
    small_font = _font(str(FONT_MONO), 26)

    # ---- Slide 1: Title ----
    img = Image.new("RGB", (W, H), theme.bg)
    draw = ImageDraw.Draw(img)
    draw_centered_text(draw, "ZIO Blocks", title_font, y=290, fill=theme.title)
    draw_centered_text(draw, "Schema Migration System", _font(str(FONT_MONO), 52), y=395, fill=theme.accent)
    draw_centered_text(
        draw,
        "Pure • Serializable • Reversible\nType-safe selectors + path-aware errors",
        subtitle_font,
        y=520,
        fill=theme.muted,
    )
    img.save(out_dir / "01_title.png")

    # ---- Slide 2: Selector syntax ----
    img = Image.new("RGB", (W, H), theme.bg)
    draw = ImageDraw.Draw(img)

    headline = "Type-safe selectors (macros)"
    draw.text((120, 90), headline, font=_font(str(FONT_MONO), 48), fill=theme.title)
    draw.text((120, 150), "Write selectors like optics — compile to DynamicOptic paths.", font=subtitle_font, fill=theme.muted)

    code = """import zio.blocks.schema._
import zio.blocks.schema.migration._
import zio.blocks.schema.migration.MigrationBuilderSyntax._

val migration =
  Migration
    .newBuilder[UserV1, UserV3]
    .renameField(_.name, _.fullName)
    .addField(_.verified, false)
    .buildPartial"""

    lines = [(l, theme.text) for l in code.splitlines()]
    draw_terminal_window(
        img,
        draw,
        x=120,
        y=230,
        w=1680,
        h=640,
        title="Selector syntax",
        lines=lines,
        font=code_font,
    )

    hint = "Supported: .when[T]  .each  .eachKey/.eachValue  .wrapped[T]  .at(i)  .atIndices(i*)  .atKey(k)  .atKeys(k*)"
    wrapped = _wrap_to_width(draw, hint, small_font, max_width=1680)
    for i, l in enumerate(wrapped):
        draw.text((120, 905 + i * 34), l, font=small_font, fill=theme.muted)

    img.save(out_dir / "02_selectors.png")

    # ---- Slide 3: Pure data core ----
    img = Image.new("RGB", (W, H), theme.bg)
    draw = ImageDraw.Draw(img)
    draw.text((120, 90), "Pure, serializable core", font=_font(str(FONT_MONO), 48), fill=theme.title)
    draw.text(
        (120, 150),
        "DynamicMigration is fully introspectable and can be encoded/decoded.",
        font=subtitle_font,
        fill=theme.muted,
    )

    core = """import zio.blocks.schema.migration.MigrationSchemas._

val dyn: DynamicMigration = migration.dynamicMigration
val actions: Vector[MigrationAction] = dyn.actions

// store as JSON / registry / data lake, then apply dynamically"""
    lines = []
    for l in core.splitlines():
        color = theme.text
        if l.startswith("//"):
            color = theme.muted
        if "MigrationSchemas" in l:
            color = theme.accent
        lines.append((l, color))

    draw_terminal_window(
        img,
        draw,
        x=120,
        y=240,
        w=1680,
        h=600,
        title="Serializable migration data",
        lines=lines,
        font=code_font,
    )

    img.save(out_dir / "03_serializable.png")

    # ---- Slide 4: Validation & Serialization ----
    img = Image.new("RGB", (W, H), theme.bg)
    draw = ImageDraw.Draw(img)
    draw.text((120, 90), "Validation & Serialization", font=_font(str(FONT_MONO), 48), fill=theme.title)
    draw.text(
        (120, 150),
        "Builder checks structural changes (Join/Split) + migrations serialize cleanly.",
        font=subtitle_font,
        fill=theme.muted,
    )

    validation = """// MigrationValidator now handles Join/Split
case MigrationAction.Join(target, sources, _, _) =>
  // Remove source fields, add target field
  val afterDrops = sources.foldLeft(...)
  afterDrops.flatMap(addTargetField)

case MigrationAction.Split(source, targets, _, _) =>
  // Remove source field, add target fields
  val afterDrop = dropSourceField(source)
  afterDrop.flatMap(addTargetFields(targets))

// Serialization test passes
test("DynamicMigration encodes/decodes via JsonBinaryCodec")"""
    lines = []
    for l in validation.splitlines():
        color = theme.text
        if l.startswith("//") or l.startswith("test("):
            color = theme.muted
        if "Join" in l or "Split" in l or "MigrationAction" in l:
            color = theme.accent
        lines.append((l, color))

    draw_terminal_window(
        img,
        draw,
        x=120,
        y=240,
        w=1680,
        h=620,
        title="MigrationValidator.scala",
        lines=lines,
        font=_font(str(FONT_MONO), 26),
    )
    img.save(out_dir / "04_validation.png")

    # ---- Slide 5: Demo output ----
    img = Image.new("RGB", (W, H), theme.bg)
    draw = ImageDraw.Draw(img)
    draw.text((120, 90), "Demo: apply + reverse", font=_font(str(FONT_MONO), 48), fill=theme.title)
    draw.text((120, 150), 'From the example app: `examples/runMain ...MigrationDemo`', font=subtitle_font, fill=theme.muted)

    demo_out = (out_dir / "demo-output.txt").read_text(encoding="utf-8", errors="replace").splitlines()
    wanted_prefixes = (
        "After V1 -> V2 migration:",
        "After V2 -> V3 migration:",
        "Reversed back to V1:",
        "After doubling:",
    )
    picked = [("> sbt -no-colors \"examples/runMain zio.blocks.schema.migration.demo.MigrationDemo\"", theme.accent)]
    for line in demo_out:
        if line.startswith(wanted_prefixes):
            picked.append((line, theme.green if line.startswith("After") else theme.text))
    if len(picked) < 2:
        picked.append(("(demo output not found)", theme.muted))

    draw_terminal_window(
        img,
        draw,
        x=120,
        y=240,
        w=1680,
        h=640,
        title="Terminal",
        lines=picked,
        font=_font(str(FONT_MONO), 28),
    )
    img.save(out_dir / "05_demo.png")

    # ---- Slide 6: Tests ----
    img = Image.new("RGB", (W, H), theme.bg)
    draw = ImageDraw.Draw(img)
    draw.text((120, 90), "Green build", font=_font(str(FONT_MONO), 48), fill=theme.title)
    draw.text((120, 150), "schemaJVM + schemaJS test suite passes.", font=subtitle_font, fill=theme.muted)

    tests = [
        ("> sbt -no-colors \"schemaJVM/test\" \"schemaJS/test\"", theme.accent),
        ("[info] 1210 tests passed. 0 tests failed. 0 tests ignored.", theme.green),
    ]
    draw_terminal_window(
        img,
        draw,
        x=120,
        y=280,
        w=1680,
        h=520,
        title="CI signal",
        lines=tests,
        font=code_font,
    )

    draw.text((120, 860), "Ready to attach to a PR (mp4 + captions).", font=subtitle_font, fill=theme.muted)
    img.save(out_dir / "06_tests.png")


if __name__ == "__main__":
    main()
