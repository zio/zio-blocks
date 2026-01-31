# Draft: Rename Markdown Module to Docs + Add Documentation

## Requirements (confirmed)
- **Keep directory as `markdown/`** - no directory rename (avoids conflict with docs/ site)
- **Rename artifact**: `zio-blocks-markdown` → `zio-blocks-docs`
- **Rename package**: `zio.blocks.markdown` → `zio.blocks.docs`
- **Add documentation**: README with usage examples, purpose, limitations

## Decisions Made
1. Directory stays `markdown/` to avoid conflict with `docs/` website directory
2. Package and artifact rename to `zio.blocks.docs` / `zio-blocks-docs`
3. This is a breaking change (acceptable for pre-1.0 library)

## Scope of Changes

### Package Rename Required
1. **Source files** (`markdown/shared/src/main/scala/zio/blocks/markdown/*.scala`)
   - All `package zio.blocks.markdown` → `package zio.blocks.docs`
   - All internal imports
   
2. **Test files** (`markdown/shared/src/test/scala/zio/blocks/markdown/*.scala`)
   - All package declarations
   - All imports
   
3. **Scala 2/3 macro files**
   - `markdown/shared/src/main/scala-2/zio/blocks/markdown/MdInterpolator.scala`
   - `markdown/shared/src/main/scala-3/zio/blocks/markdown/MdInterpolator.scala`

4. **Directory structure** (move files)
   - `markdown/shared/src/main/scala/zio/blocks/markdown/` → `markdown/shared/src/main/scala/zio/blocks/docs/`
   - `markdown/shared/src/test/scala/zio/blocks/markdown/` → `markdown/shared/src/test/scala/zio/blocks/docs/`
   - Same for scala-2/scala-3 directories

### Build.sbt Changes
- `stdSettings("zio-blocks-markdown")` → `stdSettings("zio-blocks-docs")`
- `buildInfoSettings("zio.blocks.markdown")` → `buildInfoSettings("zio.blocks.docs")`

### Documentation to Add
1. `markdown/README.md` - Module-level documentation
   - Purpose: What is zio-blocks-docs?
   - Installation: libraryDependencies snippet
   - Quick Start: Parse, render, interpolate
   - Usage Examples: Common patterns
   - API Overview: Core types (Doc, Block, Inline, Parser, Renderer)
   - GFM Support: What's supported, what's not
   - Limitations: Known constraints

## File Count Estimate
- ~16 source files in main
- ~12 test files
- 2 macro files (scala-2, scala-3)
- 1 build.sbt update
- 1 README.md creation
- Total: ~32 file changes
