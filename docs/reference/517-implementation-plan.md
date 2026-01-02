# Implementation Plan for Issue #517: Structural Type Schema Support

## Overview

Issue #517 requires extending `Schema[A]` to support structural types, enabling schema derivation for types defined by their structure rather than their nominal identity. This builds on top of the Into/As implementation and can reuse significant portions of its macro infrastructure.

---

## What Can Be Reused from Into Macro

### 1. **Structural Type Detection & Member Extraction**

**Reusable from Into (Scala 3):**
```scala
private def isStructuralType(tpe: TypeRepr): Boolean
private def isSelectableType(tpe: TypeRepr): Boolean
private def getStructuralMembers(tpe: TypeRepr): List[(String, TypeRepr)]
```

**Reusable from Into (Scala 2):**
```scala
def isStructuralType(tpe: Type): Boolean
def isDynamicType(tpe: Type): Boolean  
def getStructuralMembers(tpe: Type): List[(String, Type)]
```

**Usage in #517:** These will be used to:
- Detect when `Schema.derived[StructuralType]` is called
- Extract member definitions from structural types
- Check if structural types extend Selectable/Dynamic

### 2. **Product Type Handling**

**Reusable from Into:**
- `ProductInfo` class (field extraction from case classes)
- `FieldInfo` class (field name, type, index, getter, default value)
- `primaryConstructor` / constructor extraction logic
- Default value detection and extraction

**Usage in #517:** 
- Extracting fields from nominal types for `schema.structural` conversion
- Generating structural type representations from product field lists

### 3. **Tuple Handling**

**Reusable from Into:**
- `isTupleType(tpe)` detection
- `getTupleTypeArgs(tpe)` - extracts element types
- Tuple size handling (<=22 vs >22)
- `tupleElement` accessor generation
- `buildTuple` / `buildTupleFromExprs` construction

**Usage in #517:**
- Converting `Schema[(A, B, C)]` → `Schema[{ def _1: A; def _2: B; def _3: C }]`

### 4. **Coproduct/Sealed Trait Handling**

**Reusable from Into:**
- `isCoproductType(tpe)` / `isSealedTraitOrAbstractClass(tpe)`
- `isEnum(tpe)` (Scala 3)
- `directSubTypes(tpe)` - gets all case classes/objects of a sealed trait
- Case matching by name and signature

**Usage in #517:**
- Converting sealed traits to union types (Scala 3 only)
- Generating `{ type Tag = "CaseName"; ... } | { type Tag = ... }` structures

### 5. **Selectable/Dynamic Construction**

**Reusable from Into:**
- `getSelectableBaseClass(tpe)` - finds base Selectable class
- `findMapConstructorOrApply(tpe)` - finds Map constructor
- `deriveProductToSelectableViaMap` - generates construction code
- `deriveSelectableToProduct` - generates field access via `selectDynamic`

**Usage in #517:**
- Generating bindings that construct/deconstruct structural type values
- Schema bindings will use these for `construct` and `deconstruct`

### 6. **Opaque Type / Newtype Support**

**Reusable from Into:**
- `isOpaqueType(tpe)` / `getOpaqueUnderlying(tpe)` (Scala 3)
- `isZIONewtype(tpe)` / `getNewtypeUnderlying(tpe)`

**Usage in #517:**
- Unwrapping opaque types in `.structural` conversion
- `Schema[{ def id: UserId }].structural` → `Schema[{ def id: String }]`

### 7. **Platform Detection**

**Reusable from Into:**
- `Platform.supportsReflection` / `Platform.isJVM` / etc.
- Error messages for non-JVM platforms

**Usage in #517:**
- Same platform constraints apply for structural types

### 8. **Error Message Templates**

**Reusable from Into:**
- Error formatting patterns
- Field matching failure messages
- Platform-specific error messages

---

## New Components Required for #517

### 1. **ToStructural[A] Type Class & Macro**

```scala
trait ToStructural[A] {
  type StructuralType
  def apply(schema: Schema[A]): Schema[StructuralType]
}
```

**New macro logic needed:**
- Generate the `StructuralType` type member as a refinement type
- Transform Schema bindings from nominal to structural
- Handle recursive type detection (must fail with clear error)

### 2. **Structural Type Generation**

**New functionality:**
- **Build refinement types programmatically**: 
  - Scala 3: Use `Refinement(parent, name, info)` from Quotes API
  - Scala 2: Use `internal.refinedType` or `RefinementTypeTree`
- **Union type generation** (Scala 3 only):
  - For sealed traits: generate `{ type Tag = "A"; ... } | { type Tag = "B"; ... }`

### 3. **Recursive Type Detection**

**New functionality needed:**
- Detect self-referential types: `case class Tree(children: List[Tree])`
- Detect mutual recursion: `Node` → `Edge` → `Node`
- Produce helpful compile-time error

**Implementation approach:**
- Track visited types during structural type generation
- If a type is seen twice in the same branch, fail with error

### 4. **Type Name Normalization**

**New functionality:**
- Generate deterministic string representation of structural types
- Alphabetical field ordering
- Consistent formatting: `{age:Int,name:String}`

**Implementation:**
```scala
def normalizeTypeName(structuralType: TypeRepr): String = {
  val members = getStructuralMembers(structuralType)
  val sorted = members.sortBy(_._1)  // alphabetical by field name
  sorted.map { case (name, tpe) => s"$name:${normalizeType(tpe)}" }
        .mkString("{", ",", "}")
}
```

### 5. **Schema Binding Transformation**

**New functionality:**
- Transform `Schema[A].bindings` from nominal to structural
- For `construct`: Use Selectable/Dynamic construction
- For `deconstruct`: Use `selectDynamic` access

### 6. **Direct Structural Schema Derivation**

**New functionality:**
- `Schema.derived[{ def name: String; def age: Int }]`
- Must generate appropriate bindings using Selectable/Dynamic
- Needs integration with existing Schema derivation macro

---

## Implementation Steps

### Phase 1: Foundation ✅ COMPLETE

1. **Extract shared utilities into common trait/object** ✅
   - Structural type helpers integrated into Schema macro
   - Works for both Scala 2 and Scala 3

2. **Implement recursive type detection** ✅
   - Detects direct recursion (e.g., `case class Tree(children: List[Tree])`)
   - Detects mutual recursion (e.g., `Parent` ↔ `Child`)
   - Clear compile-time error messages
   - Tests: `RecursiveTypeErrorSpec`, `MutualRecursionErrorSpec`

3. **Implement type name normalization** ✅
   - Deterministic type name generation
   - Alphabetically sorted fields
   - Tests: `TypeNameNormalizationSpec`

### Phase 2: ToStructural Macro ✅ COMPLETE

4. **Create ToStructural trait and companion** ✅
   - Scala 3: Transparent inline given with macro
   - Scala 2: Implicit macro materialization
   - Tests: `SimpleProductSpec`, `NestedProductSpec`, `LargeProductSpec`

5. **Implement product → structural conversion** ✅
   - Case classes convert correctly
   - Nested types handled
   - Large products (>22 fields) supported
   - Tests: All `*ProductSpec` tests pass

6. **Implement tuple → structural conversion** ✅
   - Generates `{ def _1: A; def _2: B; ... }`
   - Tests: `TuplesSpec`

7. **Implement sum type → union type conversion** ✅ (Scala 3 only)
   - Sealed traits convert to DynamicValue.Variant
   - Enums convert correctly
   - Tests: `SealedTraitToUnionSpec`, `EnumToUnionSpec`
   - Note: Direct Scala 3 union type schema derivation is deferred (see `UnionTypesSpec`)

8. **Sum type error handling (Scala 2)** ✅
   - Clear compile-time error for sealed traits in Scala 2
   - Tests: `SumTypeErrorSpec`

### Phase 3: Schema Integration ✅ COMPLETE

9. **Add `.structural` method to Schema** ✅
   - Takes implicit `ToStructural[A]`
   - Returns `Schema[toStructural.StructuralType]`
   - Works on JVM for Scala 2 and 3

10. **Implement Schema binding transformation** ✅
    - Transform construct/deconstruct to use Selectable/Dynamic
    - Tests: `SelectableImplementationSpec`, `DynamicImplementationSpec`

### Phase 4: Direct Derivation ✅ COMPLETE

11. **Extend Schema.derived to support structural types** ✅
    - **Scala 2**: Pure structural types (`{ def name: String }`) derive correctly using Dynamic
    - **Scala 3**: Selectable subtypes with Map constructor work cross-platform
    - **Scala 3 JVM only**: Pure structural types via reflection
    - Tests: `PureStructuralTypeSpec`, `SelectableStructuralTypeSpec`

### Phase 5: Testing ✅ COMPLETE

12. **Comprehensive test suite** ✅
    - All major cases covered
    - JVM tests pass for both Scala 2 and Scala 3
    - Error case tests for recursive types, sum types (Scala 2)
    - Tests organized in `structural/` package with subdirectories

### Phase 6: Documentation 🔜 TODO

13. **Documentation**
    - Add section to schema-evolution.md
    - Examples for all supported conversions
    - Platform-specific limitations documented

---

## Current Status Summary

| Feature | Scala 2 JVM | Scala 3 JVM | Scala 3 JS/Native |
|---------|-------------|-------------|-------------------|
| Product → Structural | ✅ | ✅ | ✅ |
| Tuple → Structural | ✅ | ✅ | ✅ |
| Nested Products | ✅ | ✅ | ✅ |
| Large Products (>22) | ✅ | ✅ | ✅ |
| Collections (List, Set, etc.) | ✅ | ✅ | ✅ |
| Option/Either | ✅ | ✅ | ✅ |
| Recursive Type Detection | ✅ | ✅ | ✅ |
| Mutual Recursion Detection | ✅ | ✅ | ✅ |
| Sum Type Error (Scala 2) | ✅ | N/A | N/A |
| Sealed Trait → Variant | ✅ | ✅ | ✅ |
| Enum → Variant (Scala 3) | N/A | ✅ | ✅ |
| Pure Structural Derivation | ✅ (Dynamic) | ✅ (JVM only) | ❌ (needs Selectable) |
| Selectable Structural | N/A | ✅ | ✅ |
| Direct Union Type Schema | N/A | 🔜 Deferred | 🔜 Deferred |

---

## Estimated Reuse Percentage

| Component | Reuse from Into | New Code Needed |
|-----------|-----------------|-----------------|
| Structural type detection | 100% | 0% |
| Member extraction | 100% | 0% |
| Product field handling | 90% | 10% (type generation) |
| Tuple handling | 95% | 5% (structural output) |
| Selectable/Dynamic | 80% | 20% (binding generation) |
| Coproduct handling | 70% | 30% (union type generation) |
| Platform handling | 100% | 0% |
| Error messages | 80% | 20% (new errors) |
| Recursive detection | 0% | 100% |
| Type name normalization | 0% | 100% |
| ToStructural macro | 0% | 100% |
| Schema integration | 0% | 100% |

**Overall:** ~50-60% of low-level macro utilities can be directly reused. The high-level orchestration (ToStructural macro, Schema integration) is new.

---

## Risk Areas

1. **Refinement type generation at compile time**
   - Scala 3 Quotes API for building types is different from runtime reflection
   - May require careful use of `TypeRepr` construction

2. **Type inference for `.structural` result**
   - The issue explicitly states: "Inferrable types are a must-have"
   - Transparent inline + type member should work, but needs testing

3. **Union type generation (Scala 3)**
   - Building union types programmatically in macros
   - Ensuring the generated union is usable

4. **Schema binding transformation**
   - Understanding current Schema binding structure
   - Ensuring transformed bindings work correctly

---

## Dependencies

- **Must complete first:** Into/As implementation ✅ (done)
- **Helpful to have:** Issue #471 (TypeId) - but can work around with normalized strings
- **Schema understanding:** Need to review Schema, Reflect, and Binding structures

