# Plan: TypeId Applied Types & Variance-Aware Subtyping Implementation

**Related Issue**: #471
**Date**: 2026-01-24
**Status**: In Progress

## Objective

Complete the TypeId implementation to support:
1. Applied type inequality (`List[Int] != List[String]`)
2. Variance-aware subtyping (`List[Dog].isSubtypeOf(List[Animal])` for covariant types)
3. Predefined TypeId reuse (same instances verified with `eq`)
4. Comprehensive subtyping tests per spec

## Current State

### Completed
- [x] Added `typeArgs: List[TypeRepr]` to `TypeId.nominal`, `TypeId.alias`, and `TypeId.opaque` constructors
- [x] Updated macro calls to include `typeArgs` parameter
- [x] Fixed `structurallyEqual` and `structuralHash` to compare `aliasedTo` for union/intersection types
- [x] Fixed test for unapplied type constructor (`List` vs `List[_]`)
- [x] Implemented "shallow" defKind builders to embed base types without infinite recursion
- [x] Verified that `Dog` TypeId now includes base types `Mammal` and `Animal` in defKind

### In Progress
- [ ] Fix variance-aware subtyping test assertion (currently reversed - test expects FALSE but subtyping works)

## Tasks

### Phase 1: Fix Current Tests

#### 1.1 Restore Variance-Aware Subtyping Tests
**Priority**: High
**Status**: Pending

The test "List[Dog] is NOT subtype of List[Animal] (requires runtime registry)" has incorrect assertion.
Need to change it to expect TRUE (the subtyping IS working now).

```scala
test("List[Dog] is subtype of List[Animal] (covariant)") {
  val listDog    = TypeId.derived[List[Dog]]
  val listAnimal = TypeId.derived[List[Animal]]
  assertTrue(
    listDog.isSubtypeOf(listAnimal),
    !listAnimal.isSubtypeOf(listDog)
  )
}
```

#### 1.2 Add Predefined TypeId Reuse Tests
**Priority**: High
**Status**: Pending

Verify that `TypeId.derived[X]` returns the SAME INSTANCE as `TypeId.x` using `eq`:

```scala
test("derived primitive TypeIds are the same instances as predefined") {
  assertTrue(
    TypeId.derived[Int] eq TypeId.int,
    TypeId.derived[String] eq TypeId.string,
    TypeId.derived[Boolean] eq TypeId.boolean,
    TypeId.derived[Long] eq TypeId.long,
    TypeId.derived[Double] eq TypeId.double,
    TypeId.derived[Float] eq TypeId.float,
    TypeId.derived[Short] eq TypeId.short,
    TypeId.derived[Byte] eq TypeId.byte,
    TypeId.derived[Char] eq TypeId.char,
    TypeId.derived[Unit] eq TypeId.unit
  )
}
```

### Phase 2: Add Comprehensive Subtyping Tests (Per Spec)

#### 2.1 Basic Subtyping Tests
**Priority**: High
**Status**: Pending

```scala
suite("Basic Subtyping")(
  test("Nothing is subtype of everything") {
    val nothingId = TypeId.derived[Nothing]
    val intId     = TypeId.derived[Int]
    val stringId  = TypeId.derived[String]
    val anyId     = TypeId.derived[Any]
    assertTrue(
      nothingId.isSubtypeOf(intId),
      nothingId.isSubtypeOf(stringId),
      nothingId.isSubtypeOf(anyId)
    )
  },
  test("everything is subtype of Any") {
    val intId    = TypeId.derived[Int]
    val stringId = TypeId.derived[String]
    val listId   = TypeId.derived[List[Int]]
    val anyId    = TypeId.derived[Any]
    assertTrue(
      intId.isSubtypeOf(anyId),
      stringId.isSubtypeOf(anyId),
      listId.isSubtypeOf(anyId)
    )
  },
  test("reflexivity - type is subtype of itself") {
    val stringId = TypeId.derived[String]
    val listId   = TypeId.derived[List[Int]]
    assertTrue(
      stringId.isSubtypeOf(stringId),
      listId.isSubtypeOf(listId)
    )
  }
)
```

#### 2.2 Variance-Aware Subtyping Tests
**Priority**: High
**Status**: Pending

```scala
suite("Variance-Aware Subtyping")(
  test("covariant type parameter subtyping - List[Dog] <: List[Animal]") {
    val listDog    = TypeId.derived[List[Dog]]
    val listAnimal = TypeId.derived[List[Animal]]
    assertTrue(
      listDog.isSubtypeOf(listAnimal),
      !listAnimal.isSubtypeOf(listDog)
    )
  },
  test("contravariant type parameter subtyping - (Animal => Int) <: (Dog => Int)") {
    val fnAnimal = TypeId.derived[Animal => Int]
    val fnDog    = TypeId.derived[Dog => Int]
    assertTrue(
      fnAnimal.isSubtypeOf(fnDog),
      !fnDog.isSubtypeOf(fnAnimal)
    )
  },
  test("invariant type parameter subtyping - Array is invariant") {
    val arrayDog    = TypeId.derived[Array[Dog]]
    val arrayAnimal = TypeId.derived[Array[Animal]]
    assertTrue(
      !arrayDog.isSubtypeOf(arrayAnimal),
      !arrayAnimal.isSubtypeOf(arrayDog)
    )
  },
  test("List[Cat] is NOT subtype of List[Dog] - siblings") {
    val listCat = TypeId.derived[List[Cat]]
    val listDog = TypeId.derived[List[Dog]]
    assertTrue(
      !listCat.isSubtypeOf(listDog),
      !listDog.isSubtypeOf(listCat)
    )
  }
)
```

#### 2.3 Union and Intersection Type Subtyping Tests (Scala 3 Only)
**Priority**: High
**Status**: Pending

```scala
suite("Union Type Subtyping")(
  test("A <: A | B and B <: A | B") {
    val intId         = TypeId.derived[Int]
    val stringId      = TypeId.derived[String]
    val unionId       = TypeId.derived[Int | String]
    assertTrue(
      intId.isSubtypeOf(unionId),
      stringId.isSubtypeOf(unionId)
    )
  },
  test("A | B is NOT subtype of just A") {
    val intId   = TypeId.derived[Int]
    val unionId = TypeId.derived[Int | String]
    assertTrue(
      !unionId.isSubtypeOf(intId)
    )
  }
),
suite("Intersection Type Subtyping")(
  test("A & B <: A and A & B <: B") {
    val serializableId   = TypeId.derived[java.io.Serializable]
    val intersectionId   = TypeId.derived[java.io.Serializable & Comparable[String]]
    assertTrue(
      intersectionId.isSubtypeOf(serializableId)
    )
  }
)
```

#### 2.4 Nominal Hierarchy Subtyping Tests
**Priority**: High
**Status**: Pending

```scala
suite("Nominal Hierarchy Subtyping")(
  test("class hierarchy - Dog <: Animal") {
    val dogId    = TypeId.derived[Dog]
    val animalId = TypeId.derived[Animal]
    assertTrue(
      dogId.isSubtypeOf(animalId),
      !animalId.isSubtypeOf(dogId)
    )
  },
  test("String <: CharSequence") {
    val stringId       = TypeId.derived[String]
    val charSequenceId = TypeId.derived[CharSequence]
    assertTrue(
      stringId.isSubtypeOf(charSequenceId),
      !charSequenceId.isSubtypeOf(stringId)
    )
  }
)
```

#### 2.5 Type Alias Transparency Tests
**Priority**: Medium
**Status**: Pending

```scala
suite("Type Alias Transparency")(
  test("type aliases are transparent for subtyping") {
    type Age = Int
    val ageId = TypeId.derived[Age]
    val intId = TypeId.derived[Int]
    assertTrue(
      ageId.isSubtypeOf(intId),
      intId.isSubtypeOf(ageId),
      ageId == intId
    )
  }
)
```

#### 2.6 Opaque Type Nominality Tests (Scala 3 Only)
**Priority**: Medium
**Status**: Pending

```scala
suite("Opaque Type Nominality")(
  test("opaque types are nominally distinct from underlying") {
    // Email defined as opaque type Email = String in test file
    val emailId  = TypeId.derived[Email]
    val stringId = TypeId.derived[String]
    assertTrue(
      !emailId.isSubtypeOf(stringId),
      emailId != stringId
    )
  },
  test("different opaque types are distinct") {
    // Email and UserId both opaque type = String
    val emailId  = TypeId.derived[Email]
    val userIdId = TypeId.derived[UserId]
    assertTrue(
      !emailId.isSubtypeOf(userIdId),
      !userIdId.isSubtypeOf(emailId),
      emailId != userIdId
    )
  }
)
```

### Phase 3: Edge Cases and Regression Tests

#### 3.1 Recursive Types
**Priority**: Medium
**Status**: Pending

```scala
suite("Edge Cases")(
  test("handles recursive types without stack overflow") {
    enum Tree[+A] {
      case Leaf(value: A)
      case Branch(left: Tree[A], right: Tree[A])
    }
    val treeId = TypeId.derived[Tree[Int]]
    val hash   = treeId.hashCode  // Must terminate
    assertTrue(treeId == treeId)  // Must terminate
  },
  test("handles deeply nested generics") {
    val id = TypeId.derived[List[Map[String, Option[Either[Int, List[String]]]]]]
    val hash = id.hashCode
    assertTrue(
      id == TypeId.derived[List[Map[String, Option[Either[Int, List[String]]]]]],
      id != TypeId.derived[List[Map[String, Option[Either[Int, List[Int]]]]]]
    )
  },
  test("handles Java types") {
    val id = TypeId.derived[java.util.ArrayList[String]]
    assertTrue(
      id.name == "ArrayList",
      id.owner == Owner(List(Owner.Segment.Package("java"), Owner.Segment.Package("util")))
    )
  }
)
```

### Phase 4: Scala 2 Compatibility

#### 4.1 Update Scala 2 Macros
**Priority**: High
**Status**: Pending

Ensure the Scala 2 macros have the same shallow defKind builders as Scala 3.

### Phase 5: Format and Verify

#### 5.1 Run `sbt fmt`
**Priority**: High
**Status**: Pending

#### 5.2 Run All Tests for Scala 3
**Priority**: High
**Status**: Pending

```bash
sbt "++ 3.3.7; typeidJVM/test"
```

#### 5.3 Run All Tests for Scala 2
**Priority**: High
**Status**: Pending

```bash
sbt "++ 2.13.18; typeidJVM/test"
```

## Test File Locations

- **Cross-platform tests**: `typeid/shared/src/test/scala/zio/blocks/typeid/TypeIdDerivationSpec.scala`
- **Scala 3 specific tests**: `typeid/shared/src/test/scala-3/zio/blocks/typeid/Scala3DerivationSpec.scala`

## Key Implementation Files

- `typeid/shared/src/main/scala-3/zio/blocks/typeid/TypeId.scala`
- `typeid/shared/src/main/scala-2/zio/blocks/typeid/TypeId.scala`
- `typeid/shared/src/main/scala-3/zio/blocks/typeid/TypeIdMacros.scala`
- `typeid/shared/src/main/scala-2/zio/blocks/typeid/TypeIdMacros.scala`

## Notes

### Constraint: No Runtime Lookups
Everything must be compile-time. The macro embeds base types directly into the TypeId structure.

### Constraint: Predefined TypeId Reuse
`TypeId.derived[String]` should return the SAME INSTANCE as `TypeId.string` (verified with `eq`, not `==`).

### Shallow DefKind Strategy
To avoid infinite recursion when embedding base types in TypeRepr TypeIds:
- `buildDefKindShallow` - builds defKind with base types but stops at 1 level
- `buildClassDefKindShallow` - builds Class defKind with minimal base types
- `buildTraitDefKindShallow` - builds Trait defKind with minimal base types
- `buildBaseTypesMinimal` - builds base types without their own defKind
- `buildTypeReprMinimal` - builds TypeRepr without recursive defKind
