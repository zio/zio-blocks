# toString Implementation Plan for Issue #802

**Issue**: [#802 - Enhance toString of core data types for debugging](https://github.com/zio/zio-blocks/issues/802)
**Date**: 2026-01-24
**Status**: Completed

---

## Summary Table

| # | Component | Status | Priority | Effort | Tests |
|---|-----------|--------|----------|--------|-------|
| 1 | TypeName | ✅ Correct | - | - | ✅ Good |
| 2 | DynamicOptic | ✅ Fixed | High | High | ✅ Updated |
| 3 | Optic (Lens/Prism/Optional/Traversal) | ✅ Fixed | Medium | Low | ✅ Updated |
| 4 | Reflect | ✅ Fixed | Low | Low | ✅ Updated |
| 5 | Schema | ✅ Fixed | Low | Low | ✅ Updated |
| 6 | DynamicValue | ✅ Fixed | Medium | Medium | ✅ Updated |
| 7 | DynamicPatch | ✅ Fixed | Medium | High | ✅ Updated |
| 8 | Term | ✅ Correct | Low | Low | ❌ None |
| 9 | Json | ✅ Correct | - | - | ❌ None in ToStringSpec |

---

## Detailed Findings

### 1. TypeName.toString ✅ CORRECT

**Spec**: Valid Scala type syntax
```
scala.Int
scala.Option[scala.String]
scala.collection.immutable.Map[scala.String, scala.Int]
```

**Implementation**: Correct - renders namespace.name with type params in brackets.

**Testing**: Good coverage with exact string matching.

---

### 2. DynamicOptic.toString ❌ NEEDS MAJOR CHANGES

**Spec**: Must match path interpolator `p"..."` syntax

| Node | Spec Syntax | Current Implementation | Status |
|------|-------------|------------------------|--------|
| `Field(name)` | `.name` | `.name` | ✅ |
| `Case(name)` | `<Name>` | `.when[Name]` | ❌ |
| `AtIndex(n)` | `[n]` | `.at(n)` | ❌ |
| `AtIndices(ns)` | `[n1,n2,n3]` | `.atIndices(n1, n2, n3)` | ❌ |
| `AtMapKey(k)` | `{"key"}` or `{42}` | `.atKey("key")` | ❌ |
| `AtMapKeys(ks)` | `{"k1", "k2"}` | `.atKeys("k1", "k2")` | ❌ |
| `Elements` | `[*]` | `.each` | ❌ |
| `MapKeys` | `{*:}` | `.eachKey` | ❌ |
| `MapValues` | `{*}` | `.eachValue` | ❌ |
| `Wrapped` | `.~` | `.wrapped` | ❌ |

**Files to modify:**
- `schema/shared/src/main/scala/zio/blocks/schema/DynamicOptic.scala`
- `schema/shared/src/test/scala/zio/blocks/schema/DynamicOpticSpec.scala`
- `schema/shared/src/test/scala/zio/blocks/schema/OpticSpec.scala` (error messages)
- `schema/shared/src/test/scala/zio/blocks/schema/json/JsonBinaryCodecDeriverSpec.scala` (error messages)
- `schema/shared/src/test/scala/zio/blocks/schema/ToStringSpec.scala`

---

### 3. Optic.toString ⚠️ NEEDS MINOR FIXES

**Issue**: Uses placeholders `{<key>}` instead of actual values

| Optic | Current Issue | Fix Needed |
|-------|---------------|------------|
| `Lens` | ✅ Correct | None |
| `Prism` | ✅ Correct | None |
| `Optional` | Uses `{<key>}` placeholder | Render actual key value |
| `Traversal` | Uses `{<key>}`, `{<keys>}` placeholders | Render actual key values |

**Files to modify:**
- `schema/shared/src/main/scala/zio/blocks/schema/Optic.scala` (lines ~1243, ~2950-2953)

---

### 4. Reflect.toString ⚠️ NEEDS MINOR FIX

**Issue**: Primitives use short name instead of FQN for java types

| Current | Spec |
|---------|------|
| `Instant` | `java.time.Instant` |

**Fix**: Change `p.typeName.name` to `p.typeName.toString` at line 274.

**File to modify:**
- `schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala`

---

### 5. Schema.toString ⚠️ NEEDS MINOR FIX

**Issue**: Uses single-line wrapper instead of multi-line indented format

**Current**:
```
Schema { record Person { name: String, age: Int } }
```

**Spec**:
```
Schema {
  record Person {
    name: String
    age: Int
  }
}
```

**File to modify:**
- `schema/shared/src/main/scala/zio/blocks/schema/Schema.scala` (line 80)

---

### 6. DynamicValue.toString ⚠️ NEEDS FIXES

**Issue**: Typed primitives missing `@ {type: "..."}` metadata

| Type | Current | Spec |
|------|---------|------|
| Instant | `"2024-01-15T10:30:00Z"` | `"2024-01-15T10:30:00Z" @ {type: "instant"}` |
| Duration | `"PT1H30M"` | `"PT1H30M" @ {type: "duration"}` |
| Period | `"P1Y2M3D"` | `"P1Y2M3D" @ {type: "period"}` |
| LocalDate | `"2024-01-15"` | `"2024-01-15" @ {type: "localDate"}` |

**What's correct:**
- Records with unquoted keys ✅
- Maps with quoted string keys ✅
- Maps with unquoted non-string keys ✅
- Variants with `@ {tag: "..."}` ✅
- Sequences ✅
- String escaping ✅

**File to modify:**
- `schema/shared/src/main/scala/zio/blocks/schema/DynamicValue.scala` (lines 263-280)

---

### 7. DynamicPatch.toString ❌ NEEDS SIGNIFICANT CHANGES

**Issue**: Format differs significantly from spec

| Feature | Spec Format | Current Implementation |
|---------|-------------|------------------------|
| Set | `.name = "John"` | `~ . = 42` |
| Delta | `.age += 5` | `~ .age +5` |
| Seq insert | `+ [0: "value"]` | `+ .items[0] = value` |
| Seq append | `+ "value"` | `+ .items[+] = value` |
| Seq delete | `- [0]` | `- .items[0]` |
| Map add | `+ {"key": "value"}` | `+ .config{"key"} = value` |
| Map remove | `- {"key"}` | `- .config{"key"}` |
| Grouping | Operations grouped under path | Each op has full path |

**Required Changes:**
1. Remove `~` prefix from Set operations
2. Change delta format: `~ .age +5` → `.age += 5`
3. Group operations by path with header
4. Change sequence syntax: `+ .items[0] = value` → `+ [0: "value"]`
5. Change map syntax: `+ .config{"key"} = value` → `+ {"key": "value"}`

**File to modify:**
- `schema/shared/src/main/scala/zio/blocks/schema/patch/DynamicPatch.scala` (lines 2016-2147)

---

### 8. Term.toString ✅ CORRECT (untested)

**Spec**:
```
name: String
address: record Address { street: String, city: String }
```

**Implementation**: `s"$name: ${value.toString}"` - correctly delegates to Reflect.toString

**Issue**: No tests exist for Term.toString

**File to modify:**
- `schema/shared/src/test/scala/zio/blocks/schema/ToStringSpec.scala` (add tests)

---

### 9. Json.toString ✅ CORRECT

**Spec**: `override def toString: String = print`

**Implementation**: Exactly matches spec - delegates to existing `print` method.

**Testing**: No dedicated tests in ToStringSpec, but `print` is extensively tested elsewhere.

---

## Implementation Order (Recommended)

| Priority | Component | Reason |
|----------|-----------|--------|
| 1 | DynamicOptic | High impact - breaks copy-paste to `p"..."` |
| 2 | DynamicValue typed primitives | Loses type information |
| 3 | Optic placeholders | Confusing in error messages |
| 4 | DynamicPatch | Format differs significantly |
| 5 | Reflect primitives | Cosmetic |
| 6 | Schema multi-line | Cosmetic |
| 7 | Term/Json tests | Just add tests |

---

## Test Updates Required

When fixing DynamicOptic, update error message expectations in:
- `OpticSpec.scala` - ~6 test assertions
- `JsonBinaryCodecDeriverSpec.scala` - ~1 test assertion
- `DynamicOpticSpec.scala` - ~3 test assertions
- `ToStringSpec.scala` - ~14 test assertions

---

## Notes

- All fixes should run `sbt fmt` after code changes
- Run full test suite on both Scala 2.13 and 3.x before pushing
- PR #807 contains partial implementation that needs to be updated
