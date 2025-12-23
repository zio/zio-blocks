# ZIO Blocks Schema - Documentation

This directory contains documentation for the `Into[A, B]` and `As[A, B]` type classes.

## Documentation Index

- **[API Reference](API.md)** - Complete API documentation for Into and As type classes
- **[Into Usage Guide](INTO_USAGE.md)** - Comprehensive guide for using `Into[A, B]`
- **[As Usage Guide](AS_USAGE.md)** - Guide for bidirectional conversions with `As[A, B]`
- **[Migration Guide](MIGRATION_GUIDE.md)** - Complete guide for schema evolution and migration patterns
- **[Advanced Examples](ADVANCED_EXAMPLES.md)** - Complex conversion scenarios and advanced patterns
- **[Performance Guide](PERFORMANCE.md)** - Performance characteristics, benchmarks, and optimization tips
- **[Best Practices](BEST_PRACTICES.md)** - Best practices and patterns for using Into and As
- **[Project Summary](SUMMARY.md)** - High-level project status and completion summary
- **[Structural Types Technical Report](structural-types-technical-report.md)** - Detailed technical analysis of structural types implementation and limitations

## Quick Start

### Basic Conversion

```scala
import zio.blocks.schema.Into

case class UserV1(name: String, age: Int)
case class UserV2(name: String, age: Long)

val into = Into.derived[UserV1, UserV2]
val result = into.into(UserV1("Alice", 30)) // Right(UserV2("Alice", 30L))
```

### Bidirectional Conversion

```scala
import zio.blocks.schema.As

val as = As.derived[UserV1, UserV2]
val forward = as.into(UserV1("Alice", 30)) // Right(UserV2("Alice", 30L))
val backward = as.from(forward.getOrElse(???)) // Right(UserV1("Alice", 30))
```

## Features

- ✅ Type-safe conversions with compile-time derivation
- ✅ Runtime validation with detailed error messages
- ✅ Schema evolution support (add/remove fields, reorder, rename)
- ✅ Numeric coercions (widening and narrowing)
- ✅ Collection conversions with element coercion
- ✅ Coproduct matching (by name and signature)
- ✅ Opaque types support (Scala 3)
- ✅ Structural types support (Scala 3)
- ✅ Nested conversions

## Platform Support

| Feature | Scala 2.13 | Scala 3 |
|---------|-----------|---------|
| Core functionality | ✅ | ✅ |
| Opaque types | ❌ | ✅ |
| Structural types | ❌ | ✅ |
| ZIO Prelude newtypes | ❌ | ⚠️ |

## Examples

See the usage guides for detailed examples:
- [Into Usage Examples](INTO_USAGE.md#examples)
- [As Usage Examples](AS_USAGE.md#examples)

## Project Status

See [Project Summary](SUMMARY.md) for detailed implementation status.

**Current Completion: ~96%**

