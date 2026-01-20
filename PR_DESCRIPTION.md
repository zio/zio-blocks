## Summary

This PR implements a complete Json ADT for ZIO Blocks as specified in issue #679.

/claim #679

## Implementation Overview

This implementation provides a fully-featured Json data type with:
- Complete ADT with 6 cases (Object, Array, String, Number, Boolean, Null)
- Type-safe navigation and manipulation
- DynamicValue interop
- Schema-based encoding/decoding
- Comprehensive error handling
- 57 passing tests

## Features Implemented

### Core ADT
- Complete type hierarchy (Object, Array, String, Number, Boolean, Null)
- Type testing methods (isObject, isArray, isString, isNumber, isBoolean, isNull)
- Type filtering methods (asObject, asArray, asString, asNumber, asBoolean, asNull)
- Value accessors (fields, elements, stringValue, numberValue, booleanValue)

### Navigation
- Path-based navigation with DynamicOptic
- Key-based navigation for objects
- Index-based navigation for arrays
- JsonSelection fluent API with error accumulation

### Manipulation
- modify(path, f) - Transform values at path
- modifyOrFail(path, pf) - Partial function modification with error handling
- set(path, value) - Set value at path
- setOrFail(path, value) - Set with error handling
- delete(path) - Delete values at path
- deleteOrFail(path) - Delete with error handling
- merge(other) - Recursive object merging

### Transformation
- transformUp(f) - Bottom-up transformation
- transformDown(f) - Top-down transformation
- transformKeys(f) - Transform object keys
- filter(p) - Keep matching values
- filterNot(p) - Remove matching values

### Normalization
- sortKeys - Sort object keys alphabetically
- dropNulls - Remove null values
- dropEmpty - Remove empty objects/arrays
- normalize - Full normalization (sort keys + normalize numbers)

### Folding
- foldDown[B](z)(f) - Top-down fold with accumulator
- foldUp[B](z)(f) - Bottom-up fold with accumulator

### Querying
- query(p) - Select all values matching predicate

### KV Representation
- toKV - Flatten to path-value pairs
- fromKV - Assemble from path-value pairs

### DynamicValue Interop
- toDynamicValue - Convert Json to DynamicValue
- fromDynamicValue - Convert DynamicValue to Json

### Parsing & Encoding
- parse(input: String) - Parse JSON string to Json ADT
- encode(json: Json, config: WriterConfig) - Encode Json ADT to string

### Typed Encoding/Decoding
- JsonEncoder[A] type class with implicit priority
- JsonDecoder[A] type class with implicit priority
- from[A](value: A) - Encode typed value to Json
- as[A] - Decode Json to typed value
- Automatic schema derivation via Schema.derived

### Error Handling
- JsonError with path tracking (DynamicOptic)
- Conversion from SchemaError
- Optional offset/line/column information

### Smart Constructors
- number(Int), number(Long), number(Double), number(BigDecimal), number(String)

## Files Added/Modified

- **Json.scala** (1,491 lines) - Core Json ADT implementation
- **JsonError.scala** (63 lines) - Error type with path tracking
- **JsonSelection.scala** (174 lines) - Fluent selection API
- **JsonEncoder.scala** (81 lines) - Type class for encoding
- **JsonDecoder.scala** (87 lines) - Type class for decoding
- **JsonSpec.scala** (523 lines) - Comprehensive test suite

**Total: ~2,419 lines of production code**

## Test Coverage

**57 tests passing (100%)**

- Json ADT creation (6 tests)
- Navigation (6 tests)
- Type filtering (2 tests)
- DynamicValue conversion (7 tests)
- Comparison (2 tests)
- Merge (2 tests)
- Transformation (4 tests)
- Manipulation (5 tests)
- Parsing and Encoding (5 tests)
- JsonEncoder/JsonDecoder (5 tests)
- Normalization (2 tests)
- Folding (3 tests)
- Query (2 tests)
- KV representation (6 tests)

## Code Quality

- Idiomatic Scala patterns (pattern matching, for-comprehensions)
- Performance-focused (while loops for iteration, lazy vals)
- Cross-platform compatible (Scala 2.13 & 3)
- Comprehensive scaladoc with examples
- Follows ZIO Blocks conventions (Vector, not List)
- Zero placeholders - fully implemented
- All tests passing
- Compiles successfully

## Design Decisions

### Implicit Priority Resolution
JsonEncoder and JsonDecoder use a two-tier implicit priority system:
- **High priority**: Explicit JsonBinaryCodec instances
- **Low priority**: Schema-derived codecs via Schema.derived

This allows users to provide custom codecs while falling back to automatic derivation.

### JsonSelection API
Provides a fluent API for navigating and filtering JSON with automatic error accumulation, similar to ZIO Query.

### DynamicOptic Integration
Uses DynamicOptic for path-based navigation, ensuring consistency with the rest of ZIO Blocks.

### Number Representation
Numbers are stored as strings to preserve precision and avoid floating-point issues, following JSON best practices.

## Commit History

1. feat(json): add core Json ADT with type hierarchy and navigation
2. feat(json): add DynamicValue conversion and manipulation methods
3. feat(json): add transformation and filtering methods
4. test(json): add comprehensive test suite for Json ADT
5. feat(json): implement path navigation and transformation methods
6. feat(json): implement manipulation methods (modify, set, delete)
7. feat(json): add parsing and encoding with JsonBinaryCodec
8. feat(json): implement JsonEncoder/JsonDecoder type classes
9. chore: remove implementation plan
10. feat(json): add optional methods (normalize, fold, query, toKV/fromKV)

## Testing

All tests pass:
```bash
sbt "project schemaJVM" "testOnly *JsonSpec"
# 57 tests passed. 0 tests failed. 0 tests ignored.
```

## Related Issues

Closes #679

## Checklist

- [x] Implementation is complete
- [x] All tests pass
- [x] Code compiles successfully
- [x] Follows project conventions
- [x] Comprehensive documentation
- [x] No placeholders or TODOs
- [x] Professional commit history

