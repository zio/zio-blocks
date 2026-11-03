# Reference page design (2026-11-03)

Approved design for the zioblocks.com Reference landing page: the dense directory (design A). Published as a Design artifact: https://claude.ai/artifact/KbvtWuh3iUqJfa37G68UHw (private).

- `project/canvas.json`: canvas index (frames, titles).
- `project/Main.dc.html`: the directory, light. It holds the block data and the theme variables.
- `project/A-Dark.dc.html`, `project/A-Phone.dc.html`: wrappers that show it dark and at 390 px.

Block data (names, artifacts, platforms, descriptions) is copied from `docs/index.md` and `docs/sidebars.js`; an implementation should generate it instead of copying.

Known gap: on desktop the groups flow down column 1 then column 2 (CSS columns), so the jump-link order does not match the visual order.
