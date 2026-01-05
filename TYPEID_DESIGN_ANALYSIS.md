# TypeId Design Analysis: Representability Issue

## Current Design

### `TypeId[A]` - Top-level Type Identity
```scala
sealed trait TypeId[A <: AnyKind] {
  def name: String
  def owner: Owner
  def typeParams: List[TypeParam]
}

// Variants:
case class NominalImpl(name, owner, typeParams)                      // class, trait, object
case class AliasImpl(name, owner, typeParams, aliased: TypeRepr)     // type alias
case class OpaqueImpl(name, owner, typeParams, representation: TypeRepr)  // opaque type
```

### `TypeRepr` - Type Representation
```scala
sealed trait TypeRepr

object TypeRepr {
  case class Ref(id: TypeId[_])                                    // Reference to TypeId
  case class ParamRef(param: TypeParam)                            // Type parameter reference
  case class Applied(tycon: TypeRepr, args: List[TypeRepr])        // Applied type: List[Int]
  case class Structural(parents: List[TypeRepr], members: List[Member])  // { def foo: Int }
  case class Intersection(left: TypeRepr, right: TypeRepr)         // A & B
  case class Union(left: TypeRepr, right: TypeRepr)                // A | B
  case class Tuple(elems: List[TypeRepr])                          // (A, B, C)
  case class Function(params: List[TypeRepr], result: TypeRepr)    // (A, B) => C
  case class Singleton(path: TermPath)                             // x.type
  case class Constant(value: Any)                                  // 42, "foo"
  case object AnyType                                              // Any
  case object NothingType                                          // Nothing
}
```

---

## The Problem

There is a fundamental asymmetry: **`TypeRepr` is more expressive than `TypeId`**.

| Type Expression | As `TypeId`? | As `TypeRepr`? |
|-----------------|--------------|----------------|
| `class Foo` | ✅ Nominal | ✅ Ref |
| `type Alias = Int` | ✅ Alias | ✅ Ref |
| `opaque type Email = String` | ✅ Opaque | ✅ Ref |
| `List[Int]` (applied) | ❌ | ✅ Applied |
| `String \| Int` (union) | ❌ | ✅ Union |
| `A & B` (intersection) | ❌ | ✅ Intersection |
| `(Int, String)` (tuple) | ❌ | ✅ Tuple |
| `(A, B) => C` (function) | ❌ | ✅ Function |
| `{ def name: String }` (structural) | ❌ | ✅ Structural |
| `x.type` (singleton) | ❌ | ✅ Singleton |
| `42`, `"foo"` (literal) | ❌ | ✅ Constant |
| `Any` | ❌ | ✅ AnyType |
| `Nothing` | ❌ | ✅ NothingType |

### Core Issue

`TypeId[A]` can only represent **named types** (nominal, alias, opaque). But when you call:

```scala
TypeId.derived[List[Int]]      // Returns TypeId for List (unapplied), not List[Int]
TypeId.derived[String | Int]   // Returns alias "StringOrInt", loses union structure
TypeId.derived[(Int, String)]  // Returns TypeId for Tuple2, not the specific tuple
```

The problem: **`TypeId[A]` should represent the identity of type `A`**, but for composite types like `List[Int]`, it cannot capture that `A` is specifically `List` applied to `Int`.

---

## Possible Solutions

### Option 1: Merge `TypeId` and `TypeRepr` into Single Hierarchy

```scala
sealed trait TypeId[A <: AnyKind] {
  // Named types
  case Nominal(name, owner, typeParams)
  case Alias(name, owner, typeParams, aliased: TypeId[?])
  case Opaque(name, owner, typeParams, repr: TypeId[?])
  
  // Composite types
  case Applied(tycon: TypeId[?], args: List[TypeId[?]])
  case Structural(parents: List[TypeId[?]], members: List[Member])
  case Intersection(left: TypeId[?], right: TypeId[?])
  case Union(left: TypeId[?], right: TypeId[?])
  case Tuple(elems: List[TypeId[?]])
  case Function(params: List[TypeId[?]], result: TypeId[?])
  case Singleton(path: TermPath)
  case Constant(value: Any)
  case ParamRef(param: TypeParam)
  case AnyType, NothingType
}
```

**Pros:**
- Unified model - everything is a `TypeId`
- Can represent any type expression at top level
- Simpler - one concept instead of two

**Cons:**
- `name`, `owner`, `typeParams` don't make sense for `Applied`, `Union`, etc.
- `ParamRef` at top level is semantically strange
- Larger sealed hierarchy
- Breaking change - TypeRepr is removed

---

### Option 2: Make `TypeId` a Wrapper Around `TypeRepr`

```scala
// TypeRepr remains the "expression" language (as-is)
sealed trait TypeRepr { ... }

// TypeId becomes a thin typed wrapper
final case class TypeId[A <: AnyKind](repr: TypeRepr) {
  def name: Option[String] = repr match {
    case TypeRepr.Ref(id) => Some(id.name)
    case _ => None
  }
  // ...
}
```

**Pros:**
- `TypeRepr` does all the heavy lifting
- `TypeId[A]` is just a typed handle
- Full expressiveness with minimal change

**Cons:**
- Loss of semantic distinction (Nominal vs Alias vs Opaque at type level)
- The `[A]` phantom type becomes less meaningful
- `name`, `owner` return `Option` or throw for composite types

---

### Option 3: Introduce `TypeExpr[A]` as Universal Type

```scala
// TypeId: only for named/declared types (unchanged)
sealed trait TypeId[A] {
  case Nominal(name, owner, typeParams)
  case Alias(name, owner, typeParams, aliased: TypeExpr[?])
  case Opaque(name, owner, typeParams, repr: TypeExpr[?])
}

// TypeExpr: full type expression language (replaces TypeRepr)
sealed trait TypeExpr[A] {
  case Named(id: TypeId[A])
  case Applied(tycon: TypeExpr[?], args: List[TypeExpr[?]])
  case Structural(parents: List[TypeExpr[?]], members: List[Member])
  case Intersection(left: TypeExpr[?], right: TypeExpr[?])
  case Union(left: TypeExpr[?], right: TypeExpr[?])
  case Tuple(elems: List[TypeExpr[?]])
  case Function(params: List[TypeExpr[?]], result: TypeExpr[?])
  case Singleton(path: TermPath)
  case Constant(value: Any)
  case ParamRef(param: TypeParam)
  case AnyType, NothingType
}

// Derivation returns TypeExpr
object TypeExpr {
  inline def derived[A]: TypeExpr[A] = ...
}
```

**Pros:**
- Clear separation: `TypeId` = named declaration, `TypeExpr` = any type
- Full expressiveness via `TypeExpr.derived[A]`
- Named types retain special status
- `TypeExpr` has phantom type for safety

**Cons:**
- Breaking change (derivation returns different type)
- Two concepts to understand
- Users must choose: `TypeId` or `TypeExpr`?
- `TypeRepr` becomes `TypeExpr` (rename)

---

### Option 4: Add Composite Variants to `TypeId`

```scala
sealed trait TypeId[A <: AnyKind] {
  // Named types (have name, owner, typeParams)
  case Nominal(name, owner, typeParams)
  case Alias(name, owner, typeParams, aliased: TypeRepr)
  case Opaque(name, owner, typeParams, repr: TypeRepr)
  
  // Composite types (new - no name/owner, use TypeRepr internally)
  case Applied(tycon: TypeId[?], args: List[TypeId[?]])
  case Structural(parents: List[TypeId[?]], members: List[Member])
  case Intersection(left: TypeId[?], right: TypeId[?])
  case Union(left: TypeId[?], right: TypeId[?])
  case Tuple(elems: List[TypeId[?]])
  case Function(params: List[TypeId[?]], result: TypeId[?])
  case Singleton(path: TermPath)
  case Constant(value: Any)
}

// TypeRepr stays for internal representation within Alias/Opaque
sealed trait TypeRepr { ... }
```

**Pros:**
- Backward compatible for named types
- `TypeId` becomes fully expressive
- `TypeRepr` remains useful for alias/opaque internals
- Clear: named types have `name`/`owner`, composite types don't

**Cons:**
- Redundancy: `TypeId.Applied` vs `TypeRepr.Applied`
- `name`/`owner`/`typeParams` need to handle composite cases
- Larger API surface

---

### Option 5: Keep Current Design, Change Derivation Semantics

Keep `TypeId` and `TypeRepr` as-is, but:
- `TypeId.derived[List[Int]]` returns a **synthetic alias** `TypeId.alias("List[Int]", ..., Applied(Ref(list), [Ref(int)]))`
- Or: return `TypeId[List]` but with a new method `TypeId.appliedTo(args: TypeId[?]*): TypeRepr`

```scala
val listIntId = TypeId.derived[List[Int]]  // Returns TypeId for List
val fullType: TypeRepr = listIntId.appliedTo(TypeId.int)  // Returns Applied(Ref(list), [Ref(int)])
```

**Pros:**
- No structural changes
- Backward compatible
- TypeRepr already handles the full expression

**Cons:**
- Semantic mismatch: `TypeId[List[Int]]` doesn't actually represent `List[Int]`
- Requires manual composition for applied types
- Confusing API

---

## Comparison Matrix

| Criterion | Option 1 | Option 2 | Option 3 | Option 4 | Option 5 |
|-----------|----------|----------|----------|----------|----------|
| Full expressiveness | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| Backward compatible | ❌ | ❌ | ❌ | ✅ | ✅ |
| Simple mental model | ✅ | ✅ | ⚠️ | ⚠️ | ⚠️ |
| Clear named/composite distinction | ❌ | ❌ | ✅ | ✅ | ✅ |
| Phantom type safety | ✅ | ⚠️ | ✅ | ✅ | ⚠️ |
| Implementation complexity | Medium | Low | Medium | Medium | Low |

---

## Recommendation

**Option 4** (Add Composite Variants to `TypeId`) is recommended because:

1. **Backward compatible** - existing `Nominal`, `Alias`, `Opaque` unchanged
2. **Full expressiveness** - can represent any Scala type as `TypeId[A]`
3. **Type safety** - `TypeId[A]` correctly represents type `A`
4. **Keeps `TypeRepr`** - still useful for alias/opaque internals

### Key Insight

`TypeId[A]` should answer: **"What is the structure of type A?"**

- For `A = List`, answer: `Nominal("List", ...)`
- For `A = List[Int]`, answer: `Applied(Nominal("List"), [Nominal("Int")])`
- For `A = String | Int`, answer: `Union(Nominal("String"), Nominal("Int"))`
- For `A = { def name: String }`, answer: `Structural([...], [...])`

### Implementation Notes for Option 4

1. Add new sealed trait variants to `TypeId`:
   ```scala
   case class AppliedImpl[A](tycon: TypeId[?], args: List[TypeId[?]]) extends TypeId[A]
   case class StructuralImpl[A](parents: List[TypeId[?]], members: List[Member]) extends TypeId[A]
   case class IntersectionImpl[A](left: TypeId[?], right: TypeId[?]) extends TypeId[A]
   case class UnionImpl[A](left: TypeId[?], right: TypeId[?]) extends TypeId[A]
   case class TupleImpl[A](elements: List[TypeId[?]]) extends TypeId[A]
   case class FunctionImpl[A](params: List[TypeId[?]], result: TypeId[?]) extends TypeId[A]
   case class SingletonImpl[A](path: TermPath) extends TypeId[A]
   case class ConstantImpl[A](value: Any) extends TypeId[A]
   ```

2. Update `name`, `owner`, `typeParams`:
   - Return sensible defaults for composite types
   - Or change signature to `Option[String]`, `Option[Owner]`
   - Or synthesize names: `"List[Int]"`, `"String | Int"`

3. Add pattern matchers for new variants

4. Update macro derivation to return appropriate variant:
   - `TypeId.derived[List[Int]]` → `AppliedImpl(list, [int])`
   - `TypeId.derived[String | Int]` → `UnionImpl(string, int)`
   - `TypeId.derived[(Int, String)]` → `TupleImpl([int, string])`

