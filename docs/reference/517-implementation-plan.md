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

### Phase 1: Foundation (Can leverage Into heavily)

1. **Extract shared utilities into common trait/object**
   - Move structural type helpers from Into macro to shared location
   - `StructuralMacroUtils` or similar

2. **Implement recursive type detection**
   - New utility function to detect cycles
   - Test with direct recursion and mutual recursion

3. **Implement type name normalization**
   - Simple string generation from structural type
   - Tests for determinism and alphabetical ordering

### Phase 2: ToStructural Macro (Scala 3)

4. **Create ToStructural trait and companion**
   - Define the type class shape
   - Transparent inline given with macro

5. **Implement product → structural conversion**
   - Generate refinement type from case class fields
   - Transform Schema fields to structural equivalent

6. **Implement tuple → structural conversion**
   - Generate `{ def _1: A; def _2: B; ... }`
   - Reuse tuple handling from Into

7. **Implement sum type → union type conversion (Scala 3 only)**
   - Generate union of structural representations
   - Add `type Tag = "CaseName"` to each variant

8. **Implement opaque type unwrapping**
   - Unwrap opaque types to underlying in structural representation

### Phase 3: ToStructural Macro (Scala 2)

9. **Port to Scala 2 blackbox macro**
   - Same logic, different macro API
   - No union types - fail for sum types with clear error

### Phase 4: Schema Integration

10. **Add `.structural` method to Schema**
    - Takes implicit `ToStructural[A]`
    - Returns `Schema[toStructural.StructuralType]`

11. **Implement Schema binding transformation**
    - Transform construct/deconstruct to use Selectable/Dynamic

### Phase 5: Direct Derivation

12. **Extend Schema.derived to support structural types**
    - Detect structural type input
    - Generate Schema with appropriate bindings

### Phase 6: Testing & Documentation

13. **Comprehensive test suite**
    - All cases from #517 test matrix
    - Cross-platform tests (JVM, JS, Native)
    - Error case tests (recursive types, sum types in Scala 2)

14. **Documentation**
    - Add section to schema-evolution.md
    - Examples for all supported conversions

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

