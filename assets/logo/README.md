# ZIO Blocks — logo system

Five blocks cut out of a solid square: two unbroken bars top and bottom, and a
three-step stair falling from the top right to the bottom left. Together they
make a Z. Every block is the same colour and the same weight — nothing is
singled out.

## Files

| File | Use |
| --- | --- |
| `zio-blocks-logo.svg` | Primary horizontal lockup, light backgrounds |
| `zio-blocks-logo-on-dark.svg` | Same lockup, ground lifted to read on dark surfaces |
| `zio-blocks-logo-stacked.svg` | Square-ish spaces: cards, posters, slide title pages |
| `zio-blocks-logo-bare.svg` | No tile — for wide headers and footers |
| `zio-blocks-mark.svg` | Badge alone: GitHub org avatar, Discord icon, app icon |
| `zio-blocks-mark-favicon.svg` | Same grid, tighter ground — for 16–32 px |
| `zio-blocks-mark-knockout.svg` | One colour, Z genuinely transparent |
| `zio-blocks-logo-mono-black.svg` | Single colour, light background |
| `zio-blocks-logo-mono-white.svg` | Single colour, dark background |
| `zio-blocks-social-github.svg` / `.png` | GitHub repo social preview, 1280 × 640 |
| `zio-blocks-social-og.svg` / `.png` | Open Graph / Twitter card, 1200 × 630 |
| `zio-blocks-brand-sheet.svg` | Reference sheet: variants, palette, construction |

All type is converted to outlines, so nothing depends on a webfont loading. The
knockout and mono files use `fill-rule="evenodd"`, so the blocks are real holes —
whatever is behind the logo shows through the Z.

## Palette

| Name | Hex | Role |
| --- | --- | --- |
| Ultramarine | `#2D3F8F` | The ground |
| Paper | `#FFFFFF` | The blocks |
| Ink | `#141A2E` | Wordmark, dark surfaces |
| Lifted blue | `#4B5FC4` | Ground on dark |
| Paper ink | `#F3F5FC` | Wordmark on dark |

## Construction

Module 100, seam 6, no corner radius. The Z is four modules wide by five tall
(418 × 524); the bars run the full width unbroken, and the three stair modules
sit at columns 4, 3 and 2 on rows 2, 3 and 4.

The tile is 724 × 724 — one module of ground above and below, with the extra
width split evenly left and right. Clear space outside the tile is one further
module, 14% of its width. Scale it with the logo rather than fixing it in pixels.

## Sizing

The badge holds down to about 24 px. Below that the seams close up, so switch to
`zio-blocks-mark-favicon.svg` — same grid with a 40-unit ground, so the Z fills
more of the box.

In the horizontal lockup the wordmark cap height is 300 against a 724 badge, set
76 units clear of the tile edge and optically centred on it, not baseline
aligned. Don't re-space the two.

## Don't

- Break the bars back into separate cells, or add a fourth stair step.
- Recolour a single block. Every block is equal; picking one out breaks that.
- Round the corners of the tile, or set it on a circle.
- Add a gradient, shadow, stroke, or rotation.
- Fill the knockout blocks with a solid colour over a photo — that is what the
  opaque `zio-blocks-mark.svg` is for.
- Rebuild the wordmark by typing it in Manrope; the tracking is hand-set
  (ZIO +0.012 em, Blocks +0.004 em, word space 0.30 em).

## Social preview

Ink ground, headline upper left, with the module pattern enlarged and bleeding
off the right edge at low contrast.

The Z in "ZIO" is the badge itself, set inline: the Z's modules are scaled to the
cap height of the type, so the ultramarine ground overhangs the cap line and the
baseline by exactly one module. The tile's left edge — not the Z's first module —
sits on the text column, and the space after it is opened to 0.10 em so the "I"
clears the ground. No separate badge appears on these cards; the headline carries
the mark. Upload the PNG — GitHub's
**Settings → General → Social preview** field does not accept SVG, and neither do
most link unfurlers.

Both sizes are generated from one layout, so edit the copy in the SVG and
re-export if the tagline changes. Everything sits inside a 7.5% margin, which
keeps it clear of the crop Twitter and LinkedIn apply.

## Typeface

Manrope — ExtraBold for `ZIO`, Regular for `Blocks`. SIL Open Font License, so
it works for docs and site headings too.
