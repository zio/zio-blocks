# Phase 2: TypeName → TypeId Migration Guide

> **For LLM Agents**: This document provides complete context for continuing the TypeId migration in zio-blocks. Read this entire document before making any changes.

---

## 🎯 Objective

Replace all usages of `TypeName[A]` with `TypeId[A]` in the `schema` module, enabling the new type identity system created in Phase 1.

---

## 📚 Essential Context

### What is TypeId?

`TypeId[A]` is a phantom-typed representation of a Scala type's identity. It lives in the new `typeid/` module and has three variants:

```scala
// In typeid/shared/src/main/scala/zio/blocks/typeid/TypeId.scala
sealed trait TypeId[A] {
  def name: String           // e.g., "Person", "List"
  def owner: Owner           // e.g., zio.blocks.schema
  def typeParams: List[TypeParam]  // e.g., [A] for List[A]
  def arity: Int             // 0 for proper types, 1+ for type constructors
  def fullName: String       // e.g., "zio.blocks.schema.Person"
}

object TypeId {
  // Pattern matching extractors
  object Nominal { def unapply(...) }  // case classes, traits
  object Alias { def unapply(...) }    // type aliases
  object Opaque { def unapply(...) }   // opaque types (Scala 3)
}
```

### What is TypeName (being replaced)?

```scala
// In schema/shared/src/main/scala/zio/blocks/schema/TypeName.scala
final case class TypeName[A](
  namespace: Namespace,
  name: String,
  params: Seq[TypeName[?]]
)
```

### Why the change?

1. `TypeId` is more structured (ADT vs flat case class)
2. Supports opaque types natively
3. `Owner` provides richer location info (Package/Term/Type segments)
4. Foundation for TypeRegistry (#179) and JSON Schema serialization (#380)

---

## 🚨 Critical Architectural Constraints

### 1. No Circular Dependencies
- `typeid` module has **zero dependencies** on `schema`
- All Schema-specific operations on TypeId use extension methods in `TypeIdOps.scala`
- Never import schema types into typeid

### 2. Backward Compatibility Bridge
During migration, `TypeIdOps` provides `toTypeName` conversion:
```scala
// In schema/shared/src/main/scala/zio/blocks/schema/TypeIdOps.scala
implicit class TypeIdSchemaOps[A](private val typeId: TypeId[A]) {
  def toTypeName: TypeName[A] = { /* conversion logic */ }
}
```

### 3. The Reflect Hierarchy
`Reflect` is a sealed trait with these implementations (all need updating):

| Class | Location | `typeName` field line |
|-------|----------|----------------------|
| `Record` | Reflect.scala:275 | Line 277 |
| `Variant` | Reflect.scala:488 | Line 490 |
| `Sequence` | Reflect.scala:596 | Line 598 |
| `Map` | Reflect.scala:803 | Line 808 |
| `Dynamic` | Reflect.scala:958 | Line 961 |
| `Primitive` | Reflect.scala:1014 | Line 1017 |
| `Wrapper` | Reflect.scala:1081 | Line 1084 |
| `Deferred` | Reflect.scala:1154 | Line 1157 |

---

## 📋 Step-by-Step Migration Tasks

### Task 2.1: Add TypeId Import to Reflect.scala

**File**: `schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala`

**Change**: Add import at top of file
```scala
// Add after existing imports (around line 6)
import zio.blocks.typeid.TypeId
```

**Verification**: File should still compile (no usage yet)

---

### Task 2.2: Update Reflect Trait Base

**File**: `schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala`

**Find** (around line 144):
```scala
def typeName: TypeName[A]
def typeName(value: TypeName[A]): Reflect[F, A]
```

**Replace with**:
```scala
def typeId: TypeId[A]
def typeId(value: TypeId[A]): Reflect[F, A]

// Compatibility bridge (remove after full migration)
final def typeName: TypeName[A] = {
  import TypeIdOps._
  typeId.toTypeName
}
```

---

### Task 2.3: Update Reflect.Record

**File**: `schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala`

**Find** (around line 275-277):
```scala
case class Record[F[_, _], A](
  fields: IndexedSeq[Term[F, A, ?]],
  typeName: TypeName[A],
  recordBinding: F[BindingType.Record, A],
  ...
)
```

**Replace with**:
```scala
case class Record[F[_, _], A](
  fields: IndexedSeq[Term[F, A, ?]],
  typeId: TypeId[A],
  recordBinding: F[BindingType.Record, A],
  ...
)
```

**Also update**:
1. `protected def inner` (line ~292): Change `typeName` to `typeId`
2. `def typeName(value: TypeName[A])` (line ~420): Change to `def typeId(value: TypeId[A]): Record[F, A] = copy(typeId = value)`
3. `transform` method (line ~408-412): Change `typeName` parameter in transformer call

---

### Task 2.4: Update Reflect.Variant

**Pattern**: Same as Record
- Change constructor parameter `typeName: TypeName[A]` → `typeId: TypeId[A]`
- Update `inner`, `typeName` setter, `transform` method

---

### Task 2.5: Update Reflect.Sequence

**Pattern**: Same as above, but note the type is `TypeName[C[A]]` → `TypeId[C[A]]`

---

### Task 2.6: Update Reflect.Map

**Pattern**: Same, type is `TypeName[M[K, V]]` → `TypeId[M[K, V]]`

---

### Task 2.7: Update Reflect.Dynamic

**Note**: Dynamic has `typeName = TypeName.dynamic` hardcoded
**Change to**: `typeId = TypeId.nominal[DynamicValue]("DynamicValue", Owner.zioBlocksSchema)`

---

### Task 2.8: Update Reflect.Primitive

```scala
// Current
case class Primitive[F[_, _], A](
  primitiveType: PrimitiveType[A],
  primitiveBinding: F[BindingType.Primitive, A],
  ...
) {
  def typeName: TypeName[A] = primitiveType.typeName
}
```

**Change to**:
```scala
case class Primitive[F[_, _], A](
  primitiveType: PrimitiveType[A],
  primitiveBinding: F[BindingType.Primitive, A],
  ...
) {
  def typeId: TypeId[A] = primitiveType.typeId  // Requires PrimitiveType update
}
```

**Dependency**: This requires updating PrimitiveType.scala first (Task 2.11)

---

### Task 2.9: Update Reflect.Wrapper

**Current** (line ~1081-1087):
```scala
case class Wrapper[F[_, _], A, Wrapped](
  wrapped: Reflect[F, Wrapped],
  typeName: TypeName[A],
  wrapperPrimitiveType: Option[PrimitiveType[A]],
  wrapperBinding: F[BindingType.Wrapper[A, Wrapped], A],
  ...
)
```

**Change to**:
```scala
case class Wrapper[F[_, _], A, Wrapped](
  wrapped: Reflect[F, Wrapped],
  typeId: TypeId[A],
  wrapperPrimitiveType: Option[PrimitiveType[A]],  // Keep for now
  wrapperBinding: F[BindingType.Wrapper[A, Wrapped], A],
  ...
)
```

---

### Task 2.10: Update Reflect.Deferred

**Pattern**: Same as others

---

### Task 2.11: Update PrimitiveType.scala

**File**: `schema/shared/src/main/scala/zio/blocks/schema/PrimitiveType.scala`

**Current** (each primitive case):
```scala
def typeName: TypeName[scala.Int] = TypeName.int
```

**Change to**:
```scala
def typeId: TypeId[scala.Int] = TypeId.int
```

**Repeat for all 30 primitive types** (Unit, Boolean, Byte, Short, Int, Long, Float, Double, Char, String, BigInt, BigDecimal, DayOfWeek, Duration, Instant, LocalDate, LocalDateTime, LocalTime, Month, MonthDay, OffsetDateTime, OffsetTime, Period, Year, YearMonth, ZoneId, ZoneOffset, ZonedDateTime, UUID, Currency)

---

### Task 2.12: Update Deriver.scala

**File**: `schema/shared/src/main/scala/zio/blocks/schema/derive/Deriver.scala`

**Current signatures**:
```scala
def derivePrimitive[F[_, _], A](
  primitiveType: PrimitiveType[A],
  typeName: TypeName[A],  // ← Change this
  ...
)

def deriveRecord[F[_, _], A](
  fields: IndexedSeq[Term[F, A, ?]],
  typeName: TypeName[A],  // ← Change this
  ...
)
```

**Change all** `typeName: TypeName[A]` to `typeId: TypeId[A]`

---

### Task 2.13: Update ReflectTransformer.scala

**File**: `schema/shared/src/main/scala/zio/blocks/schema/ReflectTransformer.scala`

Same pattern - all `typeName` parameters become `typeId`

---

### Task 2.14: Update DerivationBuilder.scala

Same pattern

---

### Task 2.15: Update SchemaVersionSpecific.scala (Scala 2)

**File**: `schema/shared/src/main/scala-2/zio/blocks/schema/SchemaVersionSpecific.scala`

**Current** (around line 145):
```scala
private def typeName[A: c.WeakTypeTag]: c.Expr[TypeName[A]] = {
  // Manual reflection logic to build TypeName
}
```

**Replace with**:
```scala
private def typeId[A: c.WeakTypeTag]: c.Expr[TypeId[A]] = {
  import zio.blocks.typeid._
  c.Expr[TypeId[A]](q"_root_.zio.blocks.typeid.TypeId.derive[$A]")
}
```

---

### Task 2.16: Update SchemaVersionSpecific.scala (Scala 3)

**File**: `schema/shared/src/main/scala-3/zio/blocks/schema/SchemaVersionSpecific.scala`

Same pattern - replace manual TypeName construction with `TypeId.derive[A]`

---

### Task 2.17: Update Format Files

**Files**:
- `schema/shared/src/main/scala/zio/blocks/schema/codec/json/JsonFormat.scala`
- `schema-avro/shared/src/main/scala/zio/blocks/schema/codec/avro/AvroFormat.scala`

These call `deriveWrapper` which takes `typeName` - update to `typeId`

---

## ✅ Verification Checklist

After each task, run:
```bash
sbt "schemaJVM/compile"
```

After all tasks complete:
```bash
sbt "schemaJVM/test"           # 623 tests
sbt "schemaJS/compile"         # Cross-platform
sbt "schemaNative/compile"     # Cross-platform
sbt "schema-avro/test"         # Avro integration
```

---

## 🗑️ Phase 3: Cleanup (After Phase 2)

Only after all tests pass:
1. Delete `schema/shared/src/main/scala/zio/blocks/schema/TypeName.scala`
2. Delete `schema/shared/src/main/scala/zio/blocks/schema/Namespace.scala`
3. Remove `toTypeName` bridge from `TypeIdOps.scala`
4. Remove compatibility `typeName` method from `Reflect` trait

---

## 🔗 Related Files Reference

| File | Purpose | Lines to Change |
|------|---------|-----------------|
| `Reflect.scala` | Core type metadata | ~50 locations |
| `PrimitiveType.scala` | Primitive type definitions | ~30 locations |
| `Deriver.scala` | Type class derivation | ~10 signatures |
| `ReflectTransformer.scala` | Schema transformation | ~8 signatures |
| `DerivationBuilder.scala` | Builder pattern | ~5 signatures |
| `SchemaVersionSpecific.scala` (2) | Scala 2 macros | ~15 locations |
| `SchemaVersionSpecific.scala` (3) | Scala 3 macros | ~15 locations |
| `JsonFormat.scala` | JSON codec | ~3 locations |
| `AvroFormat.scala` | Avro codec | ~3 locations |

---

## 💡 Tips for LLM Agents

1. **Work incrementally**: Complete one Task at a time, compile after each
2. **Use grep**: `grep -n "typeName" Reflect.scala` to find all occurrences
3. **Pattern matching**: When you see `TypeName.Nominal(...)`, use `TypeId.Nominal(...)`
4. **Don't delete TypeName yet**: Keep compatibility bridge until Phase 3
5. **Test frequently**: Run `sbt schemaJVM/compile` after every change
