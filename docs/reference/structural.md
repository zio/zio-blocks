# Structural Schemas

This document describes the `structural` API and macro-based derivation for structural schemas.

Overview
- `Schema[A].structural` converts a nominal schema into a schema for a structural representation.
- The runtime representation uses `DynamicValue` to preserve existing behavior across Scala 2 and Scala 3.
- On Scala 3 we aim to provide a compile-time typed structural trait (work in progress).

Motivation
- Structural schemas allow treating different nominal types with the same shape as the same structural type.
- Useful when working with data that is structurally compatible but not nominally the same (e.g., different case classes with same fields).

Usage (runtime — works on Scala 2 and Scala 3)

```scala
case class Person(name: String, age: Int)
case class User(age: Int, name: String)

val s1 = Schema.derived[Person].structural
val s2 = Schema.derived[User].structural

assert(s1.reflect.typeName == s2.reflect.typeName)
```

Scala 3: typed structural trait (status)
- The Scala 3 macro now attempts to materialize a structural type with per-field types by constructing a refinement type from the case class constructor fields. When the macro cannot safely produce a typed refinement it falls back to the dynamic-backed implementation.
- The macro includes recursion detection to avoid generating structural types for recursive types (these will fail with a macro error).
- Named, compile-time top-level trait generation is still a planned improvement — at present the macro will produce anonymous refinements or fall back to `Schema.dynamic` depending on type complexity and safety.

Example (current, runtime-safe usage):

```scala
case class Person(name: String, age: Int)
val s: Schema[DynamicValue] = Schema.derived[Person].structural
```

Example (future — stronger compile-time typing when available):

```scala
// expected in a future iteration when named compile-time trait generation is implemented
// type PersonStructural = Schema.derived[Person].StructuralType
// def useStruct(s: PersonStructural): String = s.name
```

Design notes
- Normalized names are deterministic and stable across platform versions.
- Runtime accessors are provided: `StructuralRuntime.fromDynamicValue` (Scala 3 `Selectable`) and a Scala 2 `Dynamic` wrapper.

Limitations & Errors
- Recursive types are not supported for typed structural generation and will produce an error from the macro.
- Sum-type (sealed traits / enums) typed structural generation is planned for Scala 3 only.

Next steps
- Implement compile-time trait generation for Scala 3 product and sum types.
- Add user-facing examples and migration guidance.
