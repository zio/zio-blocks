# BSON Support Implementation

This document describes the implementation of BSON support for ZIO Blocks Schema.

## Overview

This implementation ports BSON (Binary JSON) support to ZIO Blocks (ZIO Schema 2), following the architecture established by the existing Avro format implementation.

## Architecture

### Core Components

1. **BsonBinaryCodec** (`BsonBinaryCodec.scala`)
   - Abstract base class for all BSON codecs
   - Extends `BinaryCodec[A]` from ZIO Blocks
   - Handles encoding/decoding with BSON readers/writers
   - Provides error handling and type safety

2. **BsonFormat** (`BsonFormat.scala`)
   - Main format implementation extending `BinaryFormat`
   - Implements the `Deriver` pattern for automatic codec derivation
   - Contains codecs for all primitive types
   - Handles complex types (records, variants, sequences, maps)

3. **Package Object** (`package.scala`)
   - Convenience utilities for encoding/decoding
   - Type aliases and helper methods

## Key Design Decisions

### 1. Following ZIO Blocks Architecture

Unlike the old ZIO Schema 1.x BSON implementation which used a different architecture, this implementation follows the ZIO Blocks pattern:

- Uses `Deriver` for automatic codec generation
- Leverages `Binding` for type-safe construction/deconstruction
- Implements `Reflect` for schema reflection
- Uses `Registers` for efficient value storage

### 2. BSON Library Choice

Uses MongoDB's official BSON library (`org.mongodb:bson:5.3.0`):
- Industry standard implementation
- Well-tested and maintained
- Full BSON specification support
- Compatible with MongoDB drivers

### 3. Type Mapping

#### Primitive Types
- Scala primitives → BSON primitives (Int32, Int64, Double, Boolean, String)
- BigDecimal → BSON Decimal128
- BigInt → BSON Binary
- UUID → BSON Binary (16 bytes)
- Currency → BSON String

#### Time Types
- Instant → BSON DateTime (milliseconds since epoch)
- LocalDate, LocalTime, LocalDateTime → BSON Document with fields
- Duration, Period → BSON Document with component fields
- ZonedDateTime → BSON Document with datetime + zone ID
- Other time types → Appropriate BSON representations

#### Complex Types
- Case classes → BSON Documents
- Sealed traits → BSON Documents with `$type` and `$value` fields
- Collections → BSON Arrays
- Maps → BSON Documents (keys as field names)
- Options → Value or BSON Null

### 4. Variant Encoding Strategy

Sealed traits (variants) are encoded as:
```json
{
  "$type": "CaseName",
  "$value": { /* case data */ }
}
```

This approach:
- Clearly identifies the variant case
- Preserves type information
- Allows for easy pattern matching on decode
- Is compatible with common BSON practices

## Implementation Details

### Codec Derivation

The `deriveCodec` method pattern matches on `Reflect` types:

```scala
private[this] def deriveCodec[A](reflect: Reflect[Binding, A]): BsonBinaryCodec[A] = {
  reflect match {
    case r: Reflect.Primitive[Binding, A] => derivePrimitiveCodec(r)
    case r: Reflect.Record[Binding, A]    => deriveRecordCodec(r)
    case r: Reflect.Variant[Binding, A]   => deriveVariantCodec(r)
    case r: Reflect.Sequence[Binding, c, a] => deriveSequenceCodec(r)
    case r: Reflect.Map[Binding, m, k, v] => deriveMapCodec(r)
    case r: Reflect.Wrapper[Binding, A, b] => deriveWrapperCodec(r)
    case r: Reflect.Dynamic[Binding] => deriveDynamicCodec(r)
  }
}
```

### Record Encoding/Decoding

Records (case classes) are encoded as BSON documents:

**Encoding:**
1. Start BSON document
2. For each field: write field name, encode field value
3. End BSON document

**Decoding:**
1. Read BSON document start
2. Allocate registers for field values
3. For each field: read name, decode value, store in register
4. Read document end
5. Construct instance from registers

### Variant Encoding/Decoding

Variants (sealed traits) use discriminated union encoding:

**Encoding:**
1. Determine which case the value belongs to
2. Write document with `$type` (case name) and `$value` (case data)

**Decoding:**
1. Read `$type` to determine the case
2. Read `$value` and decode using the appropriate case codec
3. Construct the variant instance

### Sequence Encoding/Decoding

Sequences (List, Vector, etc.) are encoded as BSON arrays:

**Encoding:**
1. Start BSON array
2. For each element: encode element
3. End BSON array

**Decoding:**
1. Read array start
2. Create builder for the collection type
3. While not at array end: decode element, add to builder
4. Read array end
5. Build final collection

### Map Encoding/Decoding

Maps are encoded as BSON documents:

**Encoding:**
1. Start BSON document
2. For each (key, value): write key as field name, encode value
3. End BSON document

**Decoding:**
1. Read document start
2. Create builder for the map type
3. While not at document end: read field name as key, decode value
4. Read document end
5. Build final map

## Testing Strategy

### Test Coverage

The test suite (`BsonFormatSpec.scala`) covers:

1. **Primitive Types**: All Scala primitives and standard library types
2. **Case Classes**: Simple and nested structures
3. **Sealed Traits**: Variants with multiple cases
4. **Collections**: Lists, Vectors, Sets, Maps
5. **Optional Values**: Some and None cases
6. **Time Types**: All java.time types
7. **Edge Cases**: Empty collections, special values, large numbers
8. **Error Handling**: Invalid data, decode failures

### Round-Trip Testing

All tests use round-trip verification:
```scala
encode(value) -> bytes -> decode(bytes) -> value'
assert(value == value')
```

This ensures:
- Encoding produces valid BSON
- Decoding correctly reconstructs the original value
- No data loss in the encoding/decoding process

## Performance Considerations

### Efficient Binary Format

BSON is designed for performance:
- Binary encoding is faster than text-based formats
- Length prefixes allow for quick traversal
- Compact representation reduces memory usage

### Register-Based Construction

Uses ZIO Blocks' register system:
- Efficient value storage during encoding/decoding
- Minimal allocations
- Type-safe access

### Lazy Codec Derivation

Codecs are derived lazily:
- Only created when needed
- Cached for reuse
- Supports recursive types

## Comparison with Old Implementation

### Old ZIO Schema 1.x BSON

The old implementation:
- Used `Schema[A]` directly
- Had `BsonEncoder` and `BsonDecoder` traits
- Supported configuration for sum type handling
- Had extensive annotation support

### New ZIO Blocks BSON

This implementation:
- Uses `Reflect[F, A]` and `Binding`
- Has unified `BsonBinaryCodec[A]`
- Follows ZIO Blocks architecture patterns
- Simpler, more consistent API

### Migration Path

For users migrating from old to new:
1. Replace `Schema.derive` with `Reflect.derive`
2. Use `BsonFormat.TypeClass[A]` instead of `BsonCodec[A]`
3. Update imports to `zio.blocks.schema.bson`
4. Adjust for new variant encoding format if needed

## Future Enhancements

Potential improvements:

1. **Configuration Support**
   - Custom field name mapping
   - Alternative variant encoding strategies
   - Configurable null handling

2. **Performance Optimizations**
   - Specialized codecs for common patterns
   - Buffer pooling
   - Streaming support

3. **Additional Features**
   - MongoDB-specific types (ObjectId, Decimal128)
   - Custom codec registration
   - Schema evolution support

4. **Integration**
   - MongoDB driver integration
   - Reactive streams support
   - ZIO integration utilities

## Dependencies

```scala
"org.mongodb" % "bson" % "5.3.0"
```

Minimal dependencies:
- Only requires MongoDB's BSON library
- No additional codec libraries needed
- Compatible with existing ZIO Blocks dependencies

## Build Configuration

Added to `build.sbt`:
```scala
lazy val `schema-bson` = project
  .settings(stdSettings("zio-blocks-schema-bson"))
  .dependsOn(schema.jvm % "compile->compile;test->test")
  .settings(buildInfoSettings("zio.blocks.schema.bson"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "org.mongodb" % "bson" % "5.3.0",
      "dev.zio" %% "zio-test" % "2.1.24" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.24" % Test
    )
  )
```

## Documentation

Comprehensive documentation provided:
- README.md: User-facing documentation with examples
- IMPLEMENTATION.md: This file, technical details
- Scaladoc: Inline documentation in source code
- BsonExamples.scala: Runnable examples

## Conclusion

This implementation provides full BSON support for ZIO Blocks, following the established architecture patterns and providing a clean, type-safe API for encoding and decoding Scala types to/from BSON format.

The implementation is:
- ✅ Complete: Supports all required types
- ✅ Tested: Comprehensive test coverage
- ✅ Documented: README, examples, and inline docs
- ✅ Consistent: Follows ZIO Blocks patterns
- ✅ Performant: Efficient binary encoding
- ✅ Type-safe: Leverages Scala's type system

Ready for review and integration into ZIO Blocks!
