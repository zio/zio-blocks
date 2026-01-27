# PR 2: MediaType Module

## Summary

Extract `MediaType` from zio-http and add auto-generation from IANA MIME types registry.

## What MediaType Is

A pure data type representing MIME/media types with metadata.

```scala
final case class MediaType(
  mainType: String,
  subType: String,
  compressible: Boolean = false,
  binary: Boolean = false,
  fileExtensions: List[String] = Nil,
  extensions: Map[String, String] = Map.empty
)
```

## Why It Fits zio-blocks

- Pure data, no effects
- Useful for any codec/serialization library
- Already needed by schema codecs (JSON, Avro, MessagePack, etc.)
- Can be auto-generated from authoritative online sources

## Source Files

| File | Source Location |
|------|-----------------|
| MediaType.scala | `zio-http/zio-http/shared/src/main/scala/zio/http/MediaType.scala` |
| MediaTypes.scala | `zio-http/zio-http/shared/src/main/scala/zio/http/MediaTypes.scala` (generated registry) |

## Target Location

```
zio-blocks/
├── mediatype/
│   └── shared/
│       └── src/
│           └── main/
│               └── scala/
│                   └── zio/
│                       └── blocks/
│                           └── mediatype/
│                               ├── MediaType.scala
│                               └── MediaTypes.scala (generated)
```

## Auto-Generation Strategy

### Data Sources

1. **IANA Media Types Registry**: https://www.iana.org/assignments/media-types/media-types.xhtml
2. **mime-db** (npm package with comprehensive data): https://github.com/jshttp/mime-db
   - JSON format, well-maintained
   - Includes compressible, charset, extensions metadata

### Generator Tool

Create a build-time generator:

```
zio-blocks/
├── project/
│   └── MediaTypeGenerator.scala   # sbt plugin/task
```

Or standalone tool:

```
zio-blocks/
├── tools/
│   └── mediatype-gen/
│       └── src/main/scala/
│           └── GenerateMediaTypes.scala
```

### Generation Process

1. Fetch mime-db JSON (or bundle a version)
2. Parse and transform to MediaType case class instances
3. Generate `MediaTypes.scala` with all known types organized by category:
   - `MediaTypes.application.json`
   - `MediaTypes.text.html`
   - `MediaTypes.image.png`
   - etc.

### Regeneration Command

```bash
sbt mediatype/generate   # or similar
```

## Required Changes from zio-http

### MediaType.scala
1. Change package: `zio.http` → `zio.blocks.mediatype`
2. Remove any ZIO-specific imports
3. Keep parsing logic (`parse`, `forContentType`, etc.)
4. Keep extension methods and utilities

### MediaTypes.scala
1. Regenerate using new generator tool
2. Update package references

## Build Configuration

Add to `build.sbt`:

```scala
lazy val mediatype = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .in(file("mediatype"))
  .settings(stdSettings("zio-blocks-mediatype"))
  .settings(
    // No runtime dependencies
  )
```

## Testing

- Test parsing of media type strings
- Test lookup by extension
- Test common media types exist in registry
- Cross-platform tests

## Estimated Effort

**Low** - Core extraction is simple, generator adds some work.

- MediaType core: 2-4 hours
- Generator tool: 4-8 hours
- Total: 1-2 days

## Acceptance Criteria

- [ ] MediaType case class works standalone
- [ ] Registry includes all common media types
- [ ] Generator tool can regenerate from online source
- [ ] Cross-platform build works (JVM/JS/Native)
- [ ] Tests pass
- [ ] No ZIO dependencies

## Future Enhancements

- Periodic CI job to check for registry updates
- Contribution workflow for adding custom media types
