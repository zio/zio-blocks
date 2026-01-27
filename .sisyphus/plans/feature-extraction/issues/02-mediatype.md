# GitHub Issue Draft: MediaType Module

## Title

Add MediaType with auto-generated IANA registry

## Labels

- `enhancement`
- `new module`

## Body

### Summary

Extract `MediaType` from zio-http and add tooling to auto-generate the media type registry from the IANA database or mime-db.

### Motivation

`MediaType` is a pure data type representing MIME types. It's useful for:
- Codec content-type negotiation
- File extension mapping
- Schema format identification

Currently it lives in zio-http, but it has no HTTP-specific dependencies and would benefit zio-blocks schema and codec modules.

### What to Extract

#### MediaType
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

#### MediaTypes Registry
Pre-defined instances for all common media types:
```scala
object MediaTypes {
  object application {
    val json: MediaType = ...
    val xml: MediaType = ...
    val `octet-stream`: MediaType = ...
  }
  object text {
    val html: MediaType = ...
    val plain: MediaType = ...
  }
  object image {
    val png: MediaType = ...
    val jpeg: MediaType = ...
  }
  // etc.
}
```

### Auto-Generation

The registry should be auto-generated from an authoritative source:

**Option A: IANA Registry**
- https://www.iana.org/assignments/media-types/media-types.xhtml
- Authoritative but requires parsing

**Option B: mime-db (Recommended)**
- https://github.com/jshttp/mime-db
- JSON format, well-maintained
- Includes compressible, charset, extensions metadata

### Tasks

- [ ] Create `mediatype` module with cross-platform setup
- [ ] Extract `MediaType.scala` core type
- [ ] Create generator tool to produce `MediaTypes.scala` from mime-db
- [ ] Generate initial registry
- [ ] Add parsing utilities (`MediaType.parse`, `forExtension`, etc.)
- [ ] Add tests
- [ ] Document regeneration process

### Acceptance Criteria

- MediaType case class works standalone
- Registry includes all common media types (JSON, HTML, PNG, etc.)
- Generator tool can regenerate from online source
- Cross-platform support (JVM/JS/Native)
- No ZIO dependencies

### Future Work

- CI job to check for registry updates
- Custom media type registration
