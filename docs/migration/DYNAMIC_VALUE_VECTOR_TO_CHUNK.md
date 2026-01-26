# DynamicValue: Vector → Chunk Migration Guide

## Executive Summary

This guide describes how to migrate `DynamicValue` and related types from using `scala.collection.immutable.Vector` to `zio.blocks.chunk.Chunk`. The `Json` type in this codebase already uses `Chunk` consistently; this migration brings `DynamicValue` into alignment.

**Scope**: ~15 source files, ~10 test files, ~200 individual changes  
**Risk**: Medium (type signature changes propagate through the API)  
**Estimated effort**: 2-4 hours with careful verification

---

## Rationale

1. **Consistency**: `Json.scala` uses `Chunk` for `Array` and `Object` contents. `DynamicValue` predates `Chunk` being added to ZIO Blocks and still uses `Vector`.

2. **Performance**: `Chunk` is optimized for the access patterns common in schema operations (sequential building, indexed access, iteration).

3. **API cohesion**: Converting between `Json` and `DynamicValue` currently requires `.toVector` calls. Using `Chunk` throughout eliminates this friction.

---

## Key Type Mappings

| Current (Vector) | Target (Chunk) |
|------------------|----------------|
| `Vector[(String, DynamicValue)]` | `Chunk[(String, DynamicValue)]` |
| `Vector[DynamicValue]` | `Chunk[DynamicValue]` |
| `Vector[(DynamicValue, DynamicValue)]` | `Chunk[(DynamicValue, DynamicValue)]` |
| `Vector[(DynamicOptic, DynamicValue)]` | `Chunk[(DynamicOptic, DynamicValue)]` |
| `Vector.empty` | `Chunk.empty` |
| `Vector.newBuilder[A]` | `ChunkBuilder.make[A]()` |
| `VectorBuilder[A]` | `ChunkBuilder[A]` |
| `seq.toVector` | `Chunk.from(seq)` or `seq.toChunk` |
| `Vector(a, b, c)` | `Chunk(a, b, c)` |

**Import to add**: `import zio.blocks.chunk.{Chunk, ChunkBuilder}`

---

## Files Requiring Changes

### Tier 1: Core Type Definitions (Must change first)

These define the fundamental types. Changes here propagate everywhere.

#### 1. `schema/shared/src/main/scala/zio/blocks/schema/DynamicValue.scala`

**What changes**:
- Add import for `Chunk` and `ChunkBuilder`
- Case class `Record(fields: Vector[...])` → `Chunk[...]`
- Case class `Sequence(elements: Vector[...])` → `Chunk[...]`  
- Case class `Map(entries: Vector[...])` → `Chunk[...]`
- Base trait accessor methods returning `Vector.empty` → `Chunk.empty`
- Companion object `apply` methods using `.toVector` → `Chunk.from(...)`
- Method `toKV` return type
- All internal implementations using `Vector.empty`, `Vector(...)`, `.toVector`

**Strategy**: Manual editing. This is the most critical file. Read through carefully and update each occurrence. Pay special attention to:
- Lines ~99-105: accessor method return types and defaults
- Lines ~471, ~579, ~641: case class definitions
- Lines ~528, ~628, ~698: companion object `empty` vals and `apply` methods
- Lines ~787-836: pattern match branches returning `Vector.empty`
- Lines ~1087: `patch` call with `Vector.empty`
- Lines ~1392, ~1410, ~1430: `.toVector)` at end of expressions
- Lines ~1831, ~1987: method return types

**Verification**: After changing, the file should have zero occurrences of `Vector[` related to DynamicValue types.

#### 2. `schema/shared/src/main/scala/zio/blocks/schema/DynamicValueType.scala`

**What changes**:
- `Unwrap` type members for `Record`, `Sequence`, `Map` cases

**Lines to check** (approximate):
- Line ~45: `type Unwrap = Vector[(String, DynamicValue)]` → `Chunk[...]`
- Line ~57: `type Unwrap = Vector[DynamicValue]` → `Chunk[...]`
- Line ~63: `type Unwrap = Vector[(DynamicValue, DynamicValue)]` → `Chunk[...]`

**Strategy**: Manual editing. Small file, straightforward changes.

#### 3. `schema/shared/src/main/scala/zio/blocks/schema/DynamicValueSelection.scala`

**What changes**:
- Core wrapper type: `Either[DynamicValueError, Vector[DynamicValue]]` → `Chunk[DynamicValue]`
- Method return types: `values`, `toVector` (rename to `toChunk`?), `getOrElse`, `collect`
- Companion object `succeedMany` parameter type
- All `Vector.empty` references

**Strategy**: Manual editing. This is a value class wrapping the Either—ensure the parameter type changes correctly.

**Design decision**: The method `toVector` could be renamed to `toChunk`, or kept as `toChunk` with `toVector` deprecated/removed. Recommend renaming to `toChunk` for consistency.

---

### Tier 2: Internal Implementation Files

These use DynamicValue internally and need type alignment.

#### 4. `schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala`

**What changes**:
- `toDynamicValue` methods use `Vector.newBuilder` → `ChunkBuilder.make()`
- Builder pattern: `builder.addOne(...)` works the same with ChunkBuilder
- `builder.result()` returns `Chunk` instead of `Vector`

**Lines to check**:
- ~409: `Vector.newBuilder[(String, DynamicValue)]`
- ~789: `Vector.newBuilder[DynamicValue]`
- ~903: `Vector.newBuilder[(DynamicValue, DynamicValue)]`

**Strategy**: 
```bash
# After adding import, these sed replacements should work:
sed -i '' 's/Vector\.newBuilder/ChunkBuilder.make/g' Reflect.scala
```
Then manually verify the import is present.

#### 5. `schema/shared/src/main/scala/zio/blocks/schema/patch/Differ.scala`

**What changes**:
- Method parameter types containing `Vector[DynamicValue]`
- Method parameter types containing `Vector[(String, DynamicValue)]`
- Method parameter types containing `Vector[(DynamicValue, DynamicValue)]`
- Internal `Vector.newBuilder` calls (for non-DynamicValue types too—be careful)

**Lines to check**:
- ~261-262: `oldFields: Vector[(String, DynamicValue)]`, `newFields: ...`
- ~328-329: `oldElems: Vector[DynamicValue]`, `newElems: ...`
- ~346-347, ~362, ~388-389: similar sequence diff methods
- ~430-431: `oldEntries: Vector[(DynamicValue, DynamicValue)]`, `newEntries: ...`

**Strategy**: Manual editing for method signatures. The file also uses `Vector.newBuilder` for non-DynamicValue types (e.g., `Patch.StringOp`, `Patch.DynamicPatchOp`)—leave those as Vector unless Chunk is preferred project-wide.

**Decision point**: This file has `Vector.newBuilder` for things like `Vector[Patch.StringOp]`. Decide whether to convert ALL Vector usage to Chunk, or only DynamicValue-related. Recommend: only DynamicValue-related for now to minimize scope.

#### 6. `schema/shared/src/main/scala/zio/blocks/schema/patch/DynamicPatch.scala`

**What changes**:
- `SeqOp.Insert(index: Int, values: Vector[DynamicValue])` → `Chunk[DynamicValue]`
- `SeqOp.Append(values: Vector[DynamicValue])` → `Chunk[DynamicValue]`
- Various private method signatures and return types
- Pattern matches on `DynamicValue.Record(fields)`, `DynamicValue.Sequence(elements)`, etc. should work unchanged since we're destructuring

**Lines to check**:
- ~403-407, ~433-439: method signatures with `Vector[DynamicValue]`
- ~639-643, ~665-669: more method signatures
- ~760-764, ~786-790: map entry signatures
- ~921, ~924: `SeqOp.Insert` and `SeqOp.Append` case class definitions

**Strategy**: Manual editing for case class definitions. Method signatures need type updates. Pattern matching should remain compatible.

#### 7. `schema/shared/src/main/scala/zio/blocks/schema/PathParser.scala`

**What changes**:
- Uses `Vector.newBuilder[DynamicValue]` for parsing map keys
- Return type includes `Vector[Node]` but that's unrelated to DynamicValue

**Lines to check**:
- ~281: `val keys = Vector.newBuilder[DynamicValue]`

**Strategy**: Update this one builder. The `Vector[Node]` return types are for path parsing, not DynamicValue storage—leave those unless broader Chunk adoption is desired.

---

### Tier 3: JSON Integration

#### 8. `schema/shared/src/main/scala/zio/blocks/schema/json/Json.scala`

**What changes**:
- `toDynamicValue` method creates DynamicValue using `.toVector` → adjust for Chunk
- Lines ~845-848: converts `Json.Array` and `Json.Object` to DynamicValue

**Current code** (approximate):
```scala
case arr: Array  => new DynamicValue.Sequence(arr.value.toVector.map(toDynamicValue))
case obj: Object =>
  new DynamicValue.Record(
    obj.value.toVector.map { case (k, v) => (k, toDynamicValue(v)) }
  )
```

**Target code**:
```scala
case arr: Array  => new DynamicValue.Sequence(arr.value.map(toDynamicValue))
case obj: Object =>
  new DynamicValue.Record(
    obj.value.map { case (k, v) => (k, toDynamicValue(v)) }
  )
```

**Rationale**: `arr.value` is already `Chunk[Json]`, and `Chunk.map` returns `Chunk`. No `.toVector` needed—just remove it.

**Strategy**: Manual editing. Verify that `Chunk.map` preserves the `Chunk` type (it does).

#### 9. `schema/shared/src/main/scala/zio/blocks/schema/json/JsonBinaryCodec.scala`

**What changes**:
- Import: `VectorBuilder` → `ChunkBuilder`
- ~735-736: `new DynamicValue.Sequence(Vector.empty)` → `Chunk.empty`
- ~762: `new VectorBuilder[DynamicValue]` → `ChunkBuilder.make[DynamicValue]()`
- ~774: `new VectorBuilder[(String, DynamicValue)]` → `ChunkBuilder.make[...]`

**Strategy**: 
```bash
sed -i '' 's/VectorBuilder/ChunkBuilder/g' JsonBinaryCodec.scala
sed -i '' 's/new ChunkBuilder/ChunkBuilder.make/g' JsonBinaryCodec.scala
sed -i '' 's/Vector\.empty/Chunk.empty/g' JsonBinaryCodec.scala
```
Then fix import line manually.

---

### Tier 4: Downstream Modules

#### 10. `schema-toon/src/main/scala/zio/blocks/schema/toon/ToonBinaryCodec.scala`

**What changes**:
- Import: add Chunk, change VectorBuilder → ChunkBuilder
- ~837, ~844, ~859, ~872, ~931, ~963, ~971: `VectorBuilder` usage
- ~887, ~896: `Vector.empty` in DynamicValue construction
- ~1470, ~1474, ~1477: `Vector[String]` and `Vector[DynamicValue]` in classification logic

**Strategy**: Similar sed approach, then manual verification:
```bash
sed -i '' 's/VectorBuilder/ChunkBuilder/g' ToonBinaryCodec.scala
sed -i '' 's/new ChunkBuilder/ChunkBuilder.make/g' ToonBinaryCodec.scala
```

**Note**: Line ~1470 has `Vector[String]` for record key classification—decide if this should also become Chunk or remain Vector (it's internal, not exposed in DynamicValue API). Recommend: change to Chunk for consistency.

---

### Tier 5: Test Files

Test files extensively use `Vector(...)` to construct DynamicValue instances.

**Files** (non-exhaustive):
- `schema/shared/src/test/scala/zio/blocks/schema/DynamicValueSpec.scala` (~83 occurrences)
- `schema/shared/src/test/scala/zio/blocks/schema/DynamicValueSelectionSpec.scala`
- `schema/shared/src/test/scala/zio/blocks/schema/DynamicValueRegressionSpec.scala`
- `schema/shared/src/test/scala/zio/blocks/schema/SchemaSpec.scala`
- `schema/shared/src/test/scala/zio/blocks/schema/patch/DynamicPatchSpec.scala`
- `schema/shared/src/test/scala/zio/blocks/schema/patch/TypedPatchExtendedSpec.scala`
- `schema/shared/src/test/scala/zio/blocks/schema/patch/OperationSpec.scala`
- `schema/shared/src/test/scala/zio/blocks/schema/tostring/DynamicValueToString.scala`
- `schema/shared/src/test/scala/zio/blocks/schema/json/JsonBinaryCodecDeriverSpec.scala`

**Common patterns to replace**:
```scala
DynamicValue.Record(Vector("field" -> value))     → DynamicValue.Record(Chunk("field" -> value))
DynamicValue.Sequence(Vector(a, b, c))            → DynamicValue.Sequence(Chunk(a, b, c))
DynamicValue.Map(Vector((k1, v1), (k2, v2)))      → DynamicValue.Map(Chunk((k1, v1), (k2, v2)))
result.fields.map(_._1) == Vector("a", "b")       → ... == Chunk("a", "b")
result.elements == Vector(...)                     → ... == Chunk(...)
```

**Strategy**: Bulk sed after core files compile:
```bash
# In test directories:
find schema/shared/src/test -name "*.scala" -exec sed -i '' \
  -e 's/DynamicValue\.Record(Vector(/DynamicValue.Record(Chunk(/g' \
  -e 's/DynamicValue\.Sequence(Vector(/DynamicValue.Sequence(Chunk(/g' \
  -e 's/DynamicValue\.Map(Vector(/DynamicValue.Map(Chunk(/g' \
  {} \;
```

**Manual review needed**: Some tests compare against `Vector(...)` directly:
```scala
assertTrue(result.fields.map(_._1) == Vector("a", "m", "z"))
```
These need `Vector` → `Chunk` on the right-hand side too.

**Import additions**: Each test file using `Chunk(...)` directly needs:
```scala
import zio.blocks.chunk.Chunk
```

---

## Execution Checklist

### Phase 1: Core Types
- [ ] Update `DynamicValue.scala` (manual)
- [ ] Update `DynamicValueType.scala` (manual)
- [ ] Update `DynamicValueSelection.scala` (manual)
- [ ] Compile schema project: `sbt schemaJVM/compile`
- [ ] Fix any compilation errors

### Phase 2: Internal Implementation
- [ ] Update `Reflect.scala`
- [ ] Update `patch/Differ.scala`
- [ ] Update `patch/DynamicPatch.scala`
- [ ] Update `PathParser.scala`
- [ ] Compile: `sbt schemaJVM/compile`

### Phase 3: JSON Integration
- [ ] Update `json/Json.scala`
- [ ] Update `json/JsonBinaryCodec.scala`
- [ ] Compile: `sbt schemaJVM/compile`

### Phase 4: Downstream Modules
- [ ] Update `schema-toon/.../ToonBinaryCodec.scala`
- [ ] Compile: `sbt schemaToonJVM/compile`

### Phase 5: Tests
- [ ] Bulk sed replacements in test files
- [ ] Add missing imports
- [ ] Run tests: `sbt schemaJVM/test`
- [ ] Fix any failures

### Phase 6: Cross-compilation
- [ ] Test Scala 2.13: `sbt "++2.13.18; schemaJVM/test"`
- [ ] Test JS platform (if sources touched): `sbt schemaJS/test`
- [ ] Test Native platform (if sources touched): `sbt schemaNative/test`

### Phase 7: Format
- [ ] Format modified files: `sbt schemaJVM/scalafmt schemaJVM/Test/scalafmt`

---

## Potential Issues & Mitigations

### Issue 1: ChunkBuilder API differences

`Vector.newBuilder[A]` vs `ChunkBuilder.make[A]()` — note the parentheses.

**Mitigation**: After sed, search for `ChunkBuilder.make[` without `()` and fix.

### Issue 2: .toVector vs .toChunk availability

Not all types have `.toChunk`. `Chunk.from(iterable)` is the universal converter.

**Mitigation**: If `.toChunk` doesn't compile, use `Chunk.from(...)`.

### Issue 3: Chunk doesn't extend Vector

Code that explicitly types as `Vector[A]` won't accept `Chunk[A]`.

**Mitigation**: Update all type annotations. Search for `: Vector[` after changes.

### Issue 4: Pattern matching changes

`case Vector(a, b, c) =>` won't work with Chunk.

**Mitigation**: Search for `case Vector(` patterns. Use `case Chunk(a, b, c)` or `case seq if seq.length == 3 =>`.

### Issue 5: Binary compatibility

If this is a published library, changing case class fields from Vector to Chunk is binary-incompatible.

**Mitigation**: This appears to be pre-1.0. Verify with maintainers if binary compatibility is a concern.

---

## Verification Commands

```bash
# Full compile check
sbt schemaJVM/compile schemaToonJVM/compile

# Run tests with coverage
sbt "project schemaJVM; coverage; test; coverageReport"

# Cross-Scala verification
sbt "++2.13.18; schemaJVM/test"
sbt "++3.3.7; schemaJVM/test"

# Check for leftover Vector references (should find only unrelated usages)
grep -r "Vector\[.*DynamicValue" schema/shared/src/main/
grep -r "Vector\.empty" schema/shared/src/main/scala/zio/blocks/schema/DynamicValue.scala

# Check imports are present
grep -l "ChunkBuilder" schema/shared/src/main/ | xargs grep -L "import.*ChunkBuilder"
```

---

## Rollback Plan

If issues are discovered after merging:

1. `git revert <commit>` the migration commit
2. Or selectively revert specific files if partial rollback needed

Keep the migration as a single commit (or squashed PR) for easy rollback.

---

## Post-Migration Cleanup

After successful migration:

1. Update any documentation mentioning `Vector` in DynamicValue context
2. Update CHANGELOG if maintained
3. Consider adding migration notes for downstream users if this is a published API
4. Delete this migration guide or move to `docs/archive/`
