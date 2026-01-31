# Issues - docs-gfm

## Problems Encountered

### 2026-01-30: Name Conflict with Existing `docs` Project
- **Problem**: build.sbt already has a `docs` project (lines 357-370) for website documentation using WebsitePlugin
- **Impact**: Cannot use `docs` as the module name
- **Resolution**: Use `markdown` as the module name instead (e.g., `zio-blocks-markdown`)
  - Directory: `markdown/` instead of `docs/`
  - Package: `zio.blocks.markdown` instead of `zio.blocks.docs`
  - SBT project: `markdown` instead of `docs`

## Resolutions

