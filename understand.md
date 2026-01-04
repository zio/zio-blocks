# Understanding ZIO-Blocks TypeId Implementation

> **Purpose**: This document captures all learnings from implementing the TypeId system in zio-blocks. It serves as a knowledge base for any LLM or developer continuing this work.

---

## 📖 Repository Overview

### What is ZIO-Blocks?

ZIO-Blocks is a **zero-allocation, box-free schema library** for Scala. It provides:
- Type-safe data serialization/deserialization
- Reflective optics (lenses, prisms)
- Automatic type class derivation
- Support for Scala 2.13 and Scala 3.5+

### Key Innovation: Register System

The core innovation is the **Registers** system that avoids boxing/unboxing:
- Uses separate `ByteArray` for primitives and `ObjectArray` for references
- Enables zero-allocation construction and deconstruction
- Critical for high-performance codecs

---

## 🏗️ Core Architecture

### Module Structure

```
zio-blocks/
├── typeid/          # NEW - Type identity (created in this PR)
│   └── shared/src/main/scala/zio/blocks/typeid/
│       ├── Owner.scala
│       ├── TypeParam.scala
│       ├── TypeId.scala
│       └── TypeRepr.scala
├── schema/          # Core schema library
│   └── shared/src/main/scala/zio/blocks/schema/
│       ├── Reflect.scala      # Central type metadata
│       ├── Schema.scala       # User-facing API
│       ├── TypeName.scala     # OLD - Being replaced
│       ├── PrimitiveType.scala
│       └── derive/
│           └── Deriver.scala
├── schema-avro/     # Avro codec
├── streams/         # Streaming support
└── benchmarks/      # Performance tests
```

### Dependency Graph

```
typeid (no deps)
    ↓
schema (depends on typeid)
    ↓
schema-avro (depends on schema)
```

---

## 🎯 The TypeId Bounty (#471)

### Problem Statement

The old `TypeName` system was insufficient:
```scala
// Old: Simple, flat structure
final case class TypeName[A](
  namespace: Namespace,
  name: String,
  params: Seq[TypeName[?]]
)
```

### Solution: TypeId

```scala
// New: Rich, ADT-based structure
sealed trait TypeId[A] {
  def name: String
  def owner: Owner           // Package/Term/Type segments
  def typeParams: List[TypeParam]
  def arity: Int             // Type constructor arity
  def fullName: String
}

object TypeId {
  object Nominal { ... }  // case class, trait
  object Alias { ... }    // type alias
  object Opaque { ... }   // opaque type (Scala 3)
}
```

### Why TypeId Matters

TypeId is a **foundational dependency** for:
- **#179 TypeRegistry**: Type lookup by identity
- **#380 JSON Schema**: Lossless type serialization
- **#451 transformOrFail**: Primitive type lookup
- **#463 wrap methods**: Move to TypeId
- **#517 Structural Schemas**: Future structural type support

---

## 🚨 Critical Lessons Learned

### 1. Circular Dependency Trap

**Problem**: If TypeId (in typeid) returns Schema[A], and Schema (in schema) uses TypeId, you get a circular dependency.

**Solution**: 
- Keep TypeId as **pure data** with zero schema dependencies
- Use **extension methods** in schema module:

```scala
// In schema module (NOT typeid)
object TypeIdOps {
  implicit class TypeIdSchemaOps[A](val typeId: TypeId[A]) extends AnyVal {
    def wrap[B](f: B => Either[String, A], g: A => B)(implicit s: Schema[B]): Schema[A] = ...
  }
}
```

### 2. PrimitiveType Cannot Move

**Original Plan**: Move PrimitiveType to typeid module.

**Reality**: PrimitiveType depends on:
- `DynamicValue` (schema-specific)
- `SchemaError` (schema-specific)
- `DynamicOptic` (schema-specific)

**Solution**: Keep PrimitiveType in schema, add `typeId` method alongside existing `typeName`.

### 3. Scala 3 Name Collision

**Problem**: Our `TypeRepr` clashes with `quotes.reflect.TypeRepr` in Scala 3 macros.

**Solution**: Use import alias:
```scala
import zio.blocks.typeid.{TypeRepr => ZTypeRepr}

// In macro code
'{ ZTypeRepr.Applied(${tyconExpr}, ${argsExpr}) }
```

### 4. No Arity-Based Constructors

**Bad Idea** (from #179 suggestion):
```scala
object TypeId {
  case class Constructor1[F[_]](...) // For List, Option
  case class Constructor2[F[_, _]](...) // For Map, Either
  case class Constructor3[F[_, _, _]](...) // And so on...
}
```

**Problem**: Combinatorial explosion up to Tuple22.

**Good Solution**: Use `arity: Int` field:
```scala
sealed trait TypeId[A] {
  def typeParams: List[TypeParam]
  final def arity: Int = typeParams.size
}
// arity == 0 → proper type (Int, String)
// arity == 1 → type constructor (List, Option)
// arity == 2 → binary type constructor (Map, Either)
```

### 5. MVP TypeRepr

**Full Scala Type System** (way too complex):
- Structural types
- Intersection types (`A & B`)
- Union types (`A | B`)
- Singleton types
- Refinement types
- Match types (Scala 3)

**MVP Implementation** (what we built):
```scala
sealed trait TypeRepr
object TypeRepr {
  case class Ref(id: TypeId[_]) extends TypeRepr
  case class ParamRef(param: TypeParam) extends TypeRepr
  case class Applied(tycon: TypeRepr, args: List[TypeRepr]) extends TypeRepr
}
```

Keep it extensible for future (#517).

---

## 🔧 Macro Patterns

### Scala 2.13 Pattern

```scala
import scala.reflect.macros.blackbox

object TypeIdMacros {
  def deriveMacro[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] = {
    import c.universe._
    
    val tpe = weakTypeOf[A]
    val symbol = tpe.typeSymbol
    val name = symbol.name.decodedName.toString
    
    // Build owner from symbol.owner hierarchy
    // Build typeParams from typeParams list
    
    c.Expr[TypeId[A]](q"""
      _root_.zio.blocks.typeid.TypeId.nominal[$tpe](
        $name,
        $ownerExpr,
        $typeParamsExpr
      )
    """)
  }
}
```

### Scala 3 Pattern

```scala
import scala.quoted._

object TypeIdMacros {
  inline def derive[A]: TypeId[A] = ${ deriveMacroImpl[A] }
  
  private def deriveMacroImpl[A: Type](using Quotes): Expr[TypeId[A]] = {
    import quotes.reflect._
    
    val tpe = TypeRepr.of[A]  // Note: This is quotes.reflect.TypeRepr
    val symbol = tpe.typeSymbol
    
    // Detect opaque types
    val isOpaque = symbol.flags.is(Flags.Opaque)
    
    // Build expression
    '{ TypeId.nominal[A](${Expr(name)}, ${ownerExpr}, ${typeParamsExpr}) }
  }
}
```

---

## 📁 File-by-File Reference

### typeid/ Module Files

| File | Purpose | Key Types |
|------|---------|-----------|
| `Owner.scala` | Type location path | `Owner`, `Segment` (Package/Term/Type) |
| `TypeParam.scala` | Type parameter info | `TypeParam(name, index)` |
| `TypeId.scala` | Core type identity | `TypeId[A]`, `Nominal`, `Alias`, `Opaque` |
| `TypeRepr.scala` | Type expressions | `Ref`, `ParamRef`, `Applied` |
| `TypeIdMacros.scala` | Derivation macros | `TypeId.derive[A]` |

### schema/ Module Key Files

| File | Purpose | TypeName Usages |
|------|---------|-----------------|
| `Reflect.scala` | Type metadata hierarchy | ~50 locations |
| `PrimitiveType.scala` | Primitive definitions | ~30 `typeName` methods |
| `Deriver.scala` | Type class derivation | ~10 method signatures |
| `TypeIdOps.scala` | Extension methods | Bridge code |
| `SchemaVersionSpecific.scala` | Macros | TypeName construction |

---

## 🧪 Testing Strategy

### Unit Tests Needed for TypeId

```scala
// Test nominal type
TypeId.derive[Int] shouldBe TypeId.int

// Test custom class
TypeId.derive[Person].fullName shouldBe "com.example.Person"

// Test type constructor
TypeId.derive[List[_]].arity shouldBe 1

// Test opaque type (Scala 3)
TypeId.derive[Email].isInstanceOf[TypeId.Opaque] shouldBe true
```

### Regression Testing

All 623 existing schema tests must pass after migration.

---

## 📝 Commands Reference

```bash
# Compile typeid module
sbt "typeidJVM/compile"

# Compile schema module
sbt "schemaJVM/compile"

# Run schema tests
sbt "schemaJVM/test"

# Cross-platform compile
sbt "typeidJS/compile"
sbt "typeidNative/compile"

# Full build
sbt build
```

---

## 🔗 Related GitHub Issues

| Issue | Title | Relationship |
|-------|-------|--------------|
| #471 | TypeId & Macro Derivation | **This bounty** |
| #179 | TypeRegistry | Uses TypeId for lookup |
| #380 | JSON Schema Serialization | Needs TypeId |
| #451 | Schema#transformOrFail | Uses TypeId.primitiveType |
| #463 | Move wrap to TypeId | Done via extension methods |
| #517 | Structural Schemas | Future TypeRepr extension |

---

## 💡 Tips for Future Agents

1. **Read Reflect.scala first** - It's the heart of the library
2. **Don't break the bridge** - Keep TypeName compatibility until Phase 3
3. **Compile frequently** - `sbt schemaJVM/compile` after every change
4. **Check cross-platform** - JS/Native must also work
5. **Grep is your friend** - `grep -rn "typeName" schema/` to find usages
6. **Follow the pattern** - Each Reflect subclass follows same structure
