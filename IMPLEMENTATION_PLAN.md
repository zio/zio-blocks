# Implementation Plan: Json ADT for ZIO Blocks (#679)

**Bounty**: $2000  
**Issue**: https://github.com/zio/zio-blocks/issues/679  
**Status**: In Progress

---

## 🎯 Goal

Implement a complete, production-ready `Json` data type for ZIO Blocks that follows the detailed specification in issue #679.

---

## 📊 Analysis of Previous Failures

### PR #746 (Rejected - No Feedback)
**Problems Identified:**
1. ❌ **Incomplete Implementation** - Only 4 files, ~50 lines total
2. ❌ **Missing Core Functionality** - No navigation, transformation, or manipulation methods
3. ❌ **No Tests** - Zero test coverage
4. ❌ **Wrong Package Structure** - Files in wrong locations
5. ❌ **Stub Implementation** - Methods returned `Right(value)` without logic
6. ❌ **Missing Integration** - No DynamicValue conversion, no Schema integration
7. ❌ **No Documentation** - No scaladoc, no examples

### Other Submitted PRs (#2370, #724, #732)
- All still under review
- Need to differentiate our approach

---

## 🏗️ Architecture Strategy

### Why We'll Win:
1. **Complete Implementation** - Every method from the sketch
2. **Comprehensive Tests** - Property-based + unit tests
3. **Perfect Integration** - DynamicValue, Schema, existing codebase
4. **Professional Quality** - Scaladoc, examples, clean code
5. **Incremental Commits** - Clear, reviewable history

---

## 📋 Implementation Stages

### Stage 1: Core Json ADT ✅
**Goal**: Implement the basic Json type hierarchy  
**Files**:
- `schema/shared/src/main/scala/zio/blocks/schema/json/Json.scala`
- `schema/shared/src/main/scala/zio/blocks/schema/json/JsonError.scala`

**Success Criteria**:
- [ ] All 6 Json cases (Object, Array, String, Number, Boolean, Null)
- [ ] Type testing methods (isObject, isArray, etc.)
- [ ] Direct accessors (fields, elements, stringValue, etc.)
- [ ] Comparison and equality
- [ ] Compiles without errors

---

### Stage 2: Navigation & Selection ✅
**Goal**: Implement JsonSelection and navigation methods  
**Files**:
- `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSelection.scala`

**Success Criteria**:
- [ ] JsonSelection with Either-based error handling
- [ ] Navigation methods (get, apply with path/index/key)
- [ ] Type filtering (objects, arrays, strings, etc.)
- [ ] Transformations (map, flatMap, filter, collect)
- [ ] Terminal operations (one, first, toArray)
- [ ] Tests pass

---

### Stage 3: Codecs & Type Classes ✅
**Goal**: Implement JsonEncoder/JsonDecoder with implicit priority  
**Files**:
- `schema/shared/src/main/scala/zio/blocks/schema/json/JsonEncoder.scala`
- `schema/shared/src/main/scala/zio/blocks/schema/json/JsonDecoder.scala`

**Success Criteria**:
- [ ] JsonEncoder/JsonDecoder traits
- [ ] Implicit priority resolution (codec > schema)
- [ ] Integration with JsonBinaryCodec
- [ ] Schema-based derivation
- [ ] Tests for encoding/decoding

---

### Stage 4: Manipulation & Transformation ✅
**Goal**: Implement modification, merging, and transformation methods  
**Files**:
- `schema/shared/src/main/scala/zio/blocks/schema/json/MergeStrategy.scala`

**Success Criteria**:
- [ ] modify, modifyOrFail, set, setOrFail
- [ ] delete, deleteOrFail, insert, insertOrFail
- [ ] merge with MergeStrategy
- [ ] transformUp, transformDown, transformKeys
- [ ] filter, filterNot, partition
- [ ] Tests for all operations

---

### Stage 5: Parsing & Serialization ✅
**Goal**: Implement JSON parsing and encoding  
**Files**:
- Update Json.scala with parse/encode methods

**Success Criteria**:
- [ ] parse from String, CharSequence, bytes, Reader
- [ ] encode to String with WriterConfig
- [ ] encodeToBytes, encodeToChunk
- [ ] Error handling with JsonError
- [ ] Tests for round-trip serialization

---

### Stage 6: DynamicValue Integration ✅
**Goal**: Bidirectional conversion with DynamicValue  
**Files**:
- Update Json.scala with toDynamicValue/fromDynamicValue

**Success Criteria**:
- [ ] toDynamicValue (lossless)
- [ ] fromDynamicValue (with type conversions)
- [ ] fromPrimitiveValue helper
- [ ] Tests for all PrimitiveValue types

---

### Stage 7: Advanced Features ✅
**Goal**: Implement remaining advanced methods  
**Files**:
- Update Json.scala

**Success Criteria**:
- [ ] Folding (foldDown, foldUp, foldDownOrFail, foldUpOrFail)
- [ ] Querying (query with predicate)
- [ ] Projection (project with paths)
- [ ] Normalization (normalize, sortKeys, dropNulls, dropEmpty)
- [ ] KV representation (toKV, fromKV)
- [ ] Tests for all features

---

### Stage 8: String Interpolators (Optional) ⏸️
**Goal**: Implement p and j interpolators  
**Files**:
- `schema/shared/src/main/scala/zio/blocks/schema/json/interpolators.scala`
- `schema/shared/src/main/scala/zio/blocks/schema/json/PathMacros.scala`

**Success Criteria**:
- [ ] p"path" interpolator for DynamicOptic
- [ ] j"json" interpolator for Json literals
- [ ] Macro implementations for Scala 2 and 3
- [ ] Tests

**Note**: This is complex and may be out of scope. Will implement if time permits.

---

## 🧪 Testing Strategy

1. **Unit Tests** - Test each method individually
2. **Property-Based Tests** - Use ScalaCheck for invariants
3. **Integration Tests** - Test with DynamicValue, Schema
4. **Round-Trip Tests** - Ensure serialization/deserialization works
5. **Edge Cases** - Empty objects/arrays, null values, nested structures

---

## 📝 Documentation Requirements

1. **Scaladoc** - Every public method and class
2. **Examples** - Code examples in scaladoc
3. **README** - Usage guide (if needed)
4. **PR Description** - Clear explanation of implementation

---

## ⏱️ Time Estimate

- **Stage 1-2**: 4-6 hours
- **Stage 3-4**: 4-6 hours  
- **Stage 5-6**: 3-4 hours
- **Stage 7**: 2-3 hours
- **Testing**: 4-6 hours
- **Documentation**: 2-3 hours
- **Total**: 19-28 hours

---

## 🚀 Next Steps

1. ✅ Clone repo and study codebase
2. ✅ Analyze rejected PRs
3. ✅ Study existing Schema, DynamicValue, DynamicOptic implementations
4. ⏳ Implement Stage 1
5. ⏳ Continue through stages incrementally

---

## 📚 Key Learnings from Codebase

### Existing Architecture:
- **DynamicValue**: 5 cases (Primitive, Record, Variant, Sequence, Map)
- **DynamicOptic**: Path navigation with nodes (Field, Case, AtIndex, etc.)
- **Schema**: Core type class with `toDynamicValue` and `fromDynamicValue`
- **JsonBinaryCodec**: Existing JSON codec infrastructure (39KB file!)
- **PrimitiveValue**: All primitive types (String, Int, Boolean, etc.)

### Integration Points:
1. Json ↔ DynamicValue conversion (bidirectional)
2. JsonEncoder/JsonDecoder using Schema derivation
3. JsonBinaryCodec for actual serialization
4. DynamicOptic for path-based navigation

### Code Style:
- Scala 2 compatible (no Scala 3-only features)
- Performance-focused (while loops, manual iteration)
- Immutable data structures (Vector, not List)
- Comprehensive scaladoc

---

**Last Updated**: 2026-01-20 05:15 PST

