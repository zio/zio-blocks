# TypeId Pretty-Print as Scala Code

## TL;DR

> **Quick Summary**: Replace debug-oriented `toString` on TypeId, TypeRepr, and TypeParam with idiomatic Scala type syntax. Create shared `TypeIdPrinter` utility for consistent rendering across the codebase.
> 
> **Deliverables**:
> - New `TypeIdPrinter.scala` with centralized pretty-print logic
> - Updated `TypeId.toString` (Scala 2 and 3)
> - Updated `TypeRepr.toString` for all variants
> - Updated `TypeParam.toString`
> - Updated tests to match new format
> 
> **Estimated Effort**: Medium
> **Parallel Execution**: YES - 2 waves
> **Critical Path**: Task 1 (TypeIdPrinter) → Tasks 2-4 (toString updates) → Task 5 (tests)

---

## Context

### Original Request
User wants TypeId rendered like pretty-printed Scala code instead of current debug format (`TypeId.nominal(scala.collection.immutable.List[A])`).

### Interview Summary
**Key Discussions**:
- Output format: Hybrid naming (short for scala.*/java.lang.*/custom, full for java.* except java.lang)
- Replace existing `toString` (no new methods)
- Show type args when applied (`List[Int]`), params when unapplied (`List[A]`)
- Complex types: `A & B` (both Scala versions), `A | B` (Scala 3), `(A, B) => C`, `(A, B, C)`, `[X] =>> F[X]`
- TypeParam: Update to `+A`, `F[_]` format (remove index)

**Research Findings**:
- `ReflectPrinter.sdlTypeName` already has hybrid naming logic to reuse
- Tests use substring checks, allowing flexibility
- `Reflect.Deferred.toString` uses TypeId.toString - will auto-improve
- TypeRepr fallback in ReflectPrinter uses .toString

### Metis Review
**Identified Gaps** (addressed):
- Edge case handling for nested/complex types → Include tests for deeply nested types
- Test tolerance → Substring checks acceptable, documented
- ReflectPrinter refactor → Deferred to future PR (out of scope)

---

## Work Objectives

### Core Objective
Replace debug-oriented toString with idiomatic Scala type syntax for TypeId, TypeRepr, and TypeParam.

### Concrete Deliverables
- `typeid/shared/src/main/scala/zio/blocks/typeid/TypeIdPrinter.scala` (NEW)
- Updated `TypeId.toString` in both Scala 2 and 3 versions
- Updated `TypeRepr` toString for all 24 variants (enumerated below)
- Updated `TypeParam.toString`
- Updated tests in TypeIdAdvancedSpec, Scala3DerivationSpec, TypeIdSpec

### Definition of Done
- [x] `TypeId.of[List[Int]].toString` returns `"List[Int]"` (not `"TypeId.nominal(...)"`
- [x] `TypeId.of[java.util.UUID].toString` returns `"java.util.UUID"`
- [x] `TypeRepr.Intersection(List(a, b)).toString` returns `"A & B"` format
- [x] `TypeRepr.Union(List(a, b)).toString` returns `"A | B"` format
- [x] `TypeParam("A", 0, Variance.Covariant).toString` returns `"+A"`
- [x] All typeid tests pass on both Scala 2 and Scala 3
- [x] All schema tests pass (ReflectPrinter uses improved toString)

### Must Have
- Hybrid naming: short for scala.*/java.lang.*/custom types
- Full qualification for java.* (except java.lang)
- Scala syntax for intersections, unions, functions, tuples, type lambdas
- TypeParam variance symbols (+/-) without index

### Must NOT Have (Guardrails)
- NO new public API methods
- NO changes to method signatures
- NO ReflectPrinter refactoring (defer to follow-up)
- NO changes outside toString/pretty-printing
- NO breaking of existing TypeId/TypeRepr equality semantics

---

## Hybrid Naming Logic (EXPLICIT RULES)

The hybrid naming logic determines when to use short names vs fully-qualified names:

```scala
def shouldUseFullName(owner: Owner): Boolean = {
  val packages = owner.segments.collect { case Owner.Package(name) => name }
  packages match {
    case "java" :: "lang" :: _ => false  // java.lang.String → "String"
    case "java" :: _           => true   // java.util.UUID → "java.util.UUID"
    case "scala" :: _          => false  // scala.Int → "Int"
    case _                     => false  // com.example.Foo → "Foo"
  }
}

def renderTypeName(typeId: TypeId[_]): String = {
  val baseName = if (shouldUseFullName(typeId.owner)) typeId.fullName else typeId.name
  
  // If applied type, show type arguments
  if (typeId.typeArgs.nonEmpty) {
    baseName + "[" + typeId.typeArgs.map(renderTypeRepr).mkString(", ") + "]"
  }
  // Else if type constructor (has params but no args), show params
  else if (typeId.typeParams.nonEmpty) {
    baseName + "[" + typeId.typeParams.map(renderTypeParam).mkString(", ") + "]"
  }
  else {
    baseName
  }
}
```

### Concrete Examples

| Input Type | Owner | Output |
|------------|-------|--------|
| `scala.Int` | `scala` | `Int` |
| `scala.collection.immutable.List` | `scala.collection.immutable` | `List` |
| `java.lang.String` | `java.lang` | `String` |
| `java.util.UUID` | `java.util` | `java.util.UUID` |
| `java.time.Instant` | `java.time` | `java.time.Instant` |
| `com.example.Person` | `com.example` | `Person` |
| `List[Int]` (applied) | - | `List[Int]` |
| `Map[String, Int]` (applied) | - | `Map[String, Int]` |
| `List` (type constructor) | - | `List[A]` |
| `Map` (type constructor) | - | `Map[K, V]` |

---

## TypeRepr Variant Rendering (COMPLETE ENUMERATION)

All 24 TypeRepr variants with expected rendering:

### Basic Type References
| Variant | Input Example | Expected Output |
|---------|---------------|-----------------|
| `Ref(typeId)` | `Ref(TypeId.of[Int])` | `Int` |
| `ParamRef(param, _)` | `ParamRef(TypeParam("A", 0))` | `A` |

### Type Application
| Variant | Input Example | Expected Output |
|---------|---------------|-----------------|
| `Applied(tycon, args)` | `Applied(Ref(listId), List(Ref(intId)))` | `List[Int]` |

### Structural Types
| Variant | Input Example | Expected Output |
|---------|---------------|-----------------|
| `Structural(parents, members)` | `Structural(Nil, List(defMember))` | `{ def foo: Int }` |

### Compound Types
| Variant | Input Example | Expected Output |
|---------|---------------|-----------------|
| `Intersection(types)` | `Intersection(List(Ref(a), Ref(b)))` | `A & B` |
| `Union(types)` | `Union(List(Ref(a), Ref(b)))` | `A \| B` |

### Tuple Types
| Variant | Input Example | Expected Output |
|---------|---------------|-----------------|
| `Tuple(elems)` unlabeled | `Tuple(List(TupleElement(None, Ref(int)), ...))` | `(Int, String)` |
| `Tuple(elems)` labeled | `Tuple(List(TupleElement(Some("name"), Ref(str)), ...))` | `(name: String, age: Int)` |

### Function Types
| Variant | Input Example | Expected Output |
|---------|---------------|-----------------|
| `Function(params, result)` | `Function(List(Ref(int), Ref(str)), Ref(bool))` | `(Int, String) => Boolean` |
| `Function(Nil, result)` | `Function(Nil, Ref(int))` | `() => Int` |
| `Function(List(single), result)` | `Function(List(Ref(int)), Ref(str))` | `Int => String` |
| `ContextFunction(params, result)` | `ContextFunction(List(Ref(ctx)), Ref(result))` | `Ctx ?=> Result` |
| `TypeLambda(params, body)` | `TypeLambda(List(TypeParam("X", 0)), Applied(...))` | `[X] =>> F[X]` |

### Parameter Modifiers
| Variant | Input Example | Expected Output |
|---------|---------------|-----------------|
| `ByName(underlying)` | `ByName(Ref(int))` | `=> Int` |
| `Repeated(element)` | `Repeated(Ref(str))` | `String*` |

### Wildcards and Bounds
| Variant | Input Example | Expected Output |
|---------|---------------|-----------------|
| `Wildcard(Unbounded)` | `Wildcard()` | `?` |
| `Wildcard(upper=Some)` | `Wildcard(TypeBounds(None, Some(Ref(any))))` | `? <: Any` |
| `Wildcard(lower=Some)` | `Wildcard(TypeBounds(Some(Ref(nothing)), None))` | `? >: Nothing` |
| `Wildcard(both)` | `Wildcard(TypeBounds(Some(lower), Some(upper)))` | `? >: Lower <: Upper` |

### Path-Dependent and Singleton Types
| Variant | Input Example | Expected Output |
|---------|---------------|-----------------|
| `Singleton(path)` | `Singleton(TermPath.fromString("x"))` | `x.type` |
| `ThisType(owner)` | `ThisType(Owner.fromPackagePath("com.example"))` | `this.type` |
| `TypeProjection(qualifier, name)` | `TypeProjection(Ref(outer), "Inner")` | `Outer#Inner` |
| `TypeSelect(qualifier, name)` | `TypeSelect(Ref(qualifier), "Member")` | `qualifier.Member` |

### Annotated Types
| Variant | Input Example | Expected Output |
|---------|---------------|-----------------|
| `Annotated(underlying, anns)` | `Annotated(Ref(int), List(ann))` | `Int @annotation` |

### Constant/Literal Types
| Variant | Input Example | Expected Output |
|---------|---------------|-----------------|
| `Constant.IntConst(42)` | - | `42` |
| `Constant.LongConst(42L)` | - | `42L` |
| `Constant.FloatConst(3.14f)` | - | `3.14f` |
| `Constant.DoubleConst(3.14)` | - | `3.14` |
| `Constant.BooleanConst(true)` | - | `true` |
| `Constant.CharConst('x')` | - | `'x'` |
| `Constant.StringConst("foo")` | - | `"foo"` |
| `Constant.NullConst` | - | `null` |
| `Constant.UnitConst` | - | `()` |
| `Constant.ClassOfConst(tpe)` | `ClassOfConst(Ref(str))` | `classOf[String]` |

### Special Types
| Variant | Input Example | Expected Output |
|---------|---------------|-----------------|
| `AnyType` | - | `Any` |
| `NothingType` | - | `Nothing` |
| `NullType` | - | `Null` |
| `UnitType` | - | `Unit` |
| `AnyKindType` | - | `AnyKind` |

---

## TypeParam Rendering (EXPLICIT RULES)

### Current Format (to be replaced)
```scala
// Current: variance symbol + name + optional arity + @ + index
"+A@0"      // covariant, proper kind
"-B@1"      // contravariant, proper kind
"F[1]@0"    // invariant, higher-kinded arity 1
"+G[2]@0"   // covariant, higher-kinded arity 2
```

### New Format
```scala
// New: variance symbol + name + optional underscore notation
"+A"        // covariant, proper kind
"-B"        // contravariant, proper kind
"F[_]"      // invariant, higher-kinded arity 1
"+G[_, _]"  // covariant, higher-kinded arity 2
"H[_[_]]"   // higher-kinded with nested kind (arity 1, where param is also HK)
```

### Concrete Examples

| Input | Output |
|-------|--------|
| `TypeParam("A", 0, Variance.Invariant)` | `A` |
| `TypeParam("A", 0, Variance.Covariant)` | `+A` |
| `TypeParam("A", 0, Variance.Contravariant)` | `-A` |
| `TypeParam.higherKinded("F", 0, 1)` | `F[_]` |
| `TypeParam.higherKinded("F", 0, 2)` | `F[_, _]` |
| `TypeParam.higherKinded("F", 0, 1, Variance.Covariant)` | `+F[_]` |

---

## Verification Strategy (MANDATORY)

### Test Decision
- **Infrastructure exists**: YES (ZIO Test, bun test pattern)
- **User wants tests**: YES - update existing tests
- **Framework**: ZIO Test

### Automated Verification

Each TODO includes executable verification:

```bash
# Run TypeId tests (Scala 3)
sbt typeidJVM/test

# Run TypeId tests (Scala 2)  
sbt "++2.13.18; typeidJVM/test"

# Run Schema tests (verify ReflectPrinter still works)
sbt schemaJVM/test
```

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately):
└── Task 1: Create TypeIdPrinter utility [no dependencies]

Wave 2 (After Wave 1):
├── Task 2: Update TypeId.toString (Scala 3) [depends: 1]
├── Task 3: Update TypeId.toString (Scala 2) [depends: 1]
└── Task 4: Update TypeRepr/TypeParam.toString [depends: 1]

Wave 3 (After Wave 2):
└── Task 5: Update tests [depends: 2, 3, 4]

Wave 4 (After Wave 3):
└── Task 6: Cross-version verification [depends: 5]
```

### Dependency Matrix

| Task | Depends On | Blocks | Can Parallelize With |
|------|------------|--------|---------------------|
| 1 | None | 2, 3, 4 | None (foundation) |
| 2 | 1 | 5 | 3, 4 |
| 3 | 1 | 5 | 2, 4 |
| 4 | 1 | 5 | 2, 3 |
| 5 | 2, 3, 4 | 6 | None |
| 6 | 5 | None | None (final) |

---

## TODOs

- [x] 1. Create TypeIdPrinter utility

  **What to do**:
  - Create new file `typeid/shared/src/main/scala/zio/blocks/typeid/TypeIdPrinter.scala`
  - Implement `object TypeIdPrinter` with three methods:
    - `def render(typeId: TypeId[_]): String` - main entry for TypeId
    - `def render(repr: TypeRepr): String` - for TypeRepr variants
    - `def render(param: TypeParam): String` - for TypeParam
  - Implement `shouldUseFullName(owner: Owner): Boolean` as private helper following the hybrid naming rules above
  - Handle ALL 24 TypeRepr variants with pattern matching as documented above

  **Implementation skeleton**:
  ```scala
  package zio.blocks.typeid

  object TypeIdPrinter {
    def render(typeId: TypeId[_]): String = {
      val baseName = if (shouldUseFullName(typeId.owner)) typeId.fullName else typeId.name
      if (typeId.typeArgs.nonEmpty)
        s"$baseName[${typeId.typeArgs.map(render).mkString(", ")}]"
      else if (typeId.typeParams.nonEmpty)
        s"$baseName[${typeId.typeParams.map(render).mkString(", ")}]"
      else
        baseName
    }

    def render(repr: TypeRepr): String = repr match {
      case TypeRepr.Ref(id)              => render(id)
      case TypeRepr.ParamRef(param, _)   => param.name
      case TypeRepr.Applied(tycon, args) => s"${render(tycon)}[${args.map(render).mkString(", ")}]"
      case TypeRepr.Intersection(types)  => types.map(render).mkString(" & ")
      case TypeRepr.Union(types)         => types.map(render).mkString(" | ")
      case TypeRepr.Tuple(elems)         => renderTuple(elems)
      case TypeRepr.Function(params, result) => renderFunction(params, result)
      // ... all 24 cases
    }

    def render(param: TypeParam): String = {
      val variance = param.variance match {
        case Variance.Covariant     => "+"
        case Variance.Contravariant => "-"
        case Variance.Invariant     => ""
      }
      val kindSuffix = if (param.kind.isProperType) "" else "[" + ("_" * param.kind.arity).mkString(", ") + "]"
      s"$variance${param.name}$kindSuffix"
    }

    private def shouldUseFullName(owner: Owner): Boolean = { /* as documented */ }
    private def renderTuple(elems: List[TupleElement]): String = { /* ... */ }
    private def renderFunction(params: List[TypeRepr], result: TypeRepr): String = { /* ... */ }
  }
  ```

  **Must NOT do**:
  - Don't add any public API beyond the render methods
  - Don't duplicate logic that can be shared

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Core utility creation requiring careful design of type rendering
  - **Skills**: []
    - No specific skills needed, pure Scala implementation

  **Parallelization**:
  - **Can Run In Parallel**: NO (foundation task)
  - **Parallel Group**: Wave 1 (solo)
  - **Blocks**: Tasks 2, 3, 4
  - **Blocked By**: None

  **References**:

  **Pattern References**:
  - `schema/shared/src/main/scala/zio/blocks/schema/ReflectPrinter.scala:37-57` - `sdlTypeName` for hybrid naming pattern
  - `schema/shared/src/main/scala/zio/blocks/schema/ReflectPrinter.scala:59-63` - `sdlTypeRepr` for TypeRepr handling

  **API/Type References**:
  - `typeid/shared/src/main/scala/zio/blocks/typeid/TypeRepr.scala` - All 24 TypeRepr variants
  - `typeid/shared/src/main/scala/zio/blocks/typeid/TypeParam.scala` - TypeParam, Kind, Variance
  - `typeid/shared/src/main/scala/zio/blocks/typeid/Owner.scala` - Owner.segments, Owner.Package

  **WHY Each Reference Matters**:
  - `ReflectPrinter.sdlTypeName`: Adapt the hybrid naming logic (lines 39-44 show the package matching)
  - `TypeRepr.scala`: Each case class/object in the sealed trait needs a render case
  - `Owner.scala`: `segments.collect { case Owner.Package(name) => name }` gives package path

  **Acceptance Criteria**:

  **Automated Verification**:
  ```bash
  # Compile check
  sbt typeidJVM/compile
  # Assert: Exit code 0, no compilation errors
  ```

  **Commit**: YES
  - Message: `feat(typeid): add TypeIdPrinter utility for idiomatic Scala type rendering`
  - Files: `typeid/shared/src/main/scala/zio/blocks/typeid/TypeIdPrinter.scala`
  - Pre-commit: `sbt typeidJVM/compile`

---

- [x] 2. Update TypeId.toString (Scala 3)

  **What to do**:
  - Open `typeid/shared/src/main/scala-3/zio/blocks/typeid/TypeId.scala`
  - Find lines 197-204 (current toString implementation)
  - Replace the body with: `TypeIdPrinter.render(this)`

  **Current code to replace** (lines 197-204):
  ```scala
  override def toString: String = {
    val paramStr = if (typeParams.isEmpty) "" else typeParams.map(_.name).mkString("[", ", ", "]")
    val kindStr  =
      if (aliasedTo.isDefined) "alias"
      else if (representation.isDefined) "opaque"
      else "nominal"
    s"TypeId.$kindStr($fullName$paramStr)"
  }
  ```

  **New code**:
  ```scala
  override def toString: String = TypeIdPrinter.render(this)
  ```

  **Must NOT do**:
  - Don't change TypeId.equals or TypeId.hashCode
  - Don't modify any other methods

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Simple delegation change, single file, ~5 lines
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Tasks 3, 4)
  - **Blocks**: Task 5
  - **Blocked By**: Task 1

  **References**:

  **Pattern References**:
  - `typeid/shared/src/main/scala-3/zio/blocks/typeid/TypeId.scala:197-204` - Current toString to replace

  **Acceptance Criteria**:

  **Automated Verification**:
  ```bash
  # Compile check
  sbt typeidJVM/compile
  # Assert: Exit code 0
  ```

  **Commit**: NO (groups with Task 3, 4)

---

- [x] 3. Update TypeId.toString (Scala 2)

  **What to do**:
  - Open `typeid/shared/src/main/scala-2/zio/blocks/typeid/TypeId.scala`
  - Find lines 131-138 (current toString implementation)
  - Replace the body with: `TypeIdPrinter.render(this)`

  **Current code to replace** (lines 131-138):
  ```scala
  override def toString: String = {
    val paramStr = if (typeParams.isEmpty) "" else typeParams.map(_.name).mkString("[", ", ", "]")
    val kindStr  =
      if (aliasedTo.isDefined) "alias"
      else if (representation.isDefined) "opaque"
      else "nominal"
    s"TypeId.$kindStr($fullName$paramStr)"
  }
  ```

  **New code**:
  ```scala
  override def toString: String = TypeIdPrinter.render(this)
  ```

  **Must NOT do**:
  - Don't change TypeId.equals or TypeId.hashCode
  - Don't modify any other methods

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Simple delegation change, single file, ~5 lines
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Tasks 2, 4)
  - **Blocks**: Task 5
  - **Blocked By**: Task 1

  **References**:

  **Pattern References**:
  - `typeid/shared/src/main/scala-2/zio/blocks/typeid/TypeId.scala:131-138` - Current toString to replace

  **Acceptance Criteria**:

  **Automated Verification**:
  ```bash
  # Compile Scala 2 version
  sbt "++2.13.18; typeidJVM/compile"
  # Assert: Exit code 0
  ```

  **Commit**: NO (groups with Task 2, 4)

---

- [x] 4. Update TypeRepr and TypeParam toString

  **What to do**:
  
  **In `typeid/shared/src/main/scala/zio/blocks/typeid/TypeRepr.scala`**:
  
  1. Update `Intersection.toString` (line 134):
     - Current: `override def toString: String = s"Intersection(${types.mkString(", ")})"`
     - New: `override def toString: String = TypeIdPrinter.render(this)`

  2. Update `Union.toString` (line 159):
     - Current: `override def toString: String = s"Union(${types.mkString(", ")})"`
     - New: `override def toString: String = TypeIdPrinter.render(this)`

  3. For case classes that use default toString (Ref, ParamRef, Applied, Tuple, Function, etc.), override toString:
     - Add `override def toString: String = TypeIdPrinter.render(this)` to each case class

  **In `typeid/shared/src/main/scala/zio/blocks/typeid/TypeParam.scala`**:
  
  Find and update the current toString (format like `"+A@0"`):
  - Current: Uses variance symbol + name + kind arity + "@" + index
  - New: `override def toString: String = TypeIdPrinter.render(this)`

  **Must NOT do**:
  - Don't change equals/hashCode on Union/Intersection (order-independent equality must remain)
  - Don't change any case class fields
  - Don't remove existing functionality

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
    - Reason: Straightforward updates across multiple case classes
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Tasks 2, 3)
  - **Blocks**: Task 5
  - **Blocked By**: Task 1

  **References**:

  **Pattern References**:
  - `typeid/shared/src/main/scala/zio/blocks/typeid/TypeRepr.scala:134` - Intersection.toString
  - `typeid/shared/src/main/scala/zio/blocks/typeid/TypeRepr.scala:159` - Union.toString
  - `typeid/shared/src/main/scala/zio/blocks/typeid/TypeParam.scala` - TypeParam.toString

  **Acceptance Criteria**:

  **Automated Verification**:
  ```bash
  # Compile check
  sbt typeidJVM/compile
  # Assert: Exit code 0
  ```

  **Commit**: YES (groups with Tasks 2, 3)
  - Message: `feat(typeid): update TypeId/TypeRepr/TypeParam toString to idiomatic Scala syntax`
  - Files: `TypeId.scala` (both versions), `TypeRepr.scala`, `TypeParam.scala`, `TypeIdPrinter.scala`
  - Pre-commit: `sbt typeidJVM/compile && sbt "++2.13.18; typeidJVM/compile"`

---

- [x] 5. Update tests to match new format

  **What to do**:
  
  **In `typeid/shared/src/test/scala/zio/blocks/typeid/TypeIdAdvancedSpec.scala`**:
  
  Find test with assertion (approximately):
  ```scala
  assertTrue(intId.toString.contains("TypeId."), intId.toString.contains("Int"))
  ```
  Change to:
  ```scala
  assertTrue(intId.toString == "Int")
  ```

  **In `typeid/shared/src/test/scala-3/zio/blocks/typeid/Scala3DerivationSpec.scala`**:
  
  Find union/intersection toString tests and update:
  - Old assertion pattern: `contains("String")` and `contains("Int")`
  - New assertion: Check for `" | "` separator for unions, `" & "` separator for intersections
  - Example: `assertTrue(union.toString.contains(" | "))`

  **In `typeid/shared/src/test/scala/zio/blocks/typeid/TypeIdSpec.scala`**:
  
  Find TypeParam.toString assertions and update:
  | Old Assertion | New Assertion |
  |---------------|---------------|
  | `== "X@5"` | `== "X"` |
  | `== "+A@0"` | `== "+A"` |
  | `== "-A@0"` | `== "-A"` |
  | `== "A@0"` | `== "A"` |
  | `== "F[1]@0"` | `== "F[_]"` |
  | `== "G[2]@0"` | `== "G[_, _]"` |

  **Add new test cases** to TypeIdSpec or TypeIdAdvancedSpec:
  ```scala
  test("TypeId.toString renders applied types") {
    val listInt = TypeId.of[List[Int]]
    assertTrue(listInt.toString == "List[Int]")
  }
  
  test("TypeId.toString renders java.util types with full path") {
    val uuid = TypeId.of[java.util.UUID]
    assertTrue(uuid.toString == "java.util.UUID")
  }
  
  test("TypeRepr.Function renders as arrow syntax") {
    val fn = TypeRepr.Function(List(TypeRepr.Ref(TypeId.int)), TypeRepr.Ref(TypeId.string))
    assertTrue(fn.toString == "Int => String")
  }
  
  test("TypeRepr.Tuple renders with parens") {
    val tuple = TypeRepr.tuple(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
    assertTrue(tuple.toString == "(Int, String)")
  }
  ```

  **Must NOT do**:
  - Don't weaken test coverage
  - Don't remove tests, only update assertions

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
    - Reason: Test updates are mechanical, following new format
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 3 (sequential after Wave 2)
  - **Blocks**: Task 6
  - **Blocked By**: Tasks 2, 3, 4

  **References**:

  **Pattern References**:
  - `typeid/shared/src/test/scala/zio/blocks/typeid/TypeIdAdvancedSpec.scala` - `toString.contains("TypeId.")` assertion
  - `typeid/shared/src/test/scala-3/zio/blocks/typeid/Scala3DerivationSpec.scala` - Union/Intersection checks
  - `typeid/shared/src/test/scala/zio/blocks/typeid/TypeIdSpec.scala` - TypeParam.toString assertions

  **Acceptance Criteria**:

  **Automated Verification**:
  ```bash
  # Run all TypeId tests (Scala 3)
  sbt typeidJVM/test
  # Assert: All tests pass, exit code 0
  ```

  **Commit**: YES
  - Message: `test(typeid): update tests for new idiomatic Scala toString format`
  - Files: `TypeIdAdvancedSpec.scala`, `Scala3DerivationSpec.scala`, `TypeIdSpec.scala`
  - Pre-commit: `sbt typeidJVM/test`

---

- [x] 6. Cross-version and cross-module verification

  **What to do**:
  - Run full test suite on Scala 2.13
  - Run schema module tests to verify ReflectPrinter still works
  - Format all modified files

  **Must NOT do**:
  - Don't skip any verification step

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Pure verification, no code changes
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 4 (final)
  - **Blocks**: None
  - **Blocked By**: Task 5

  **References**: None needed - verification only.

  **Acceptance Criteria**:

  **Automated Verification**:
  ```bash
  # Cross-Scala verification
  sbt "++2.13.18; typeidJVM/test"
  # Assert: All tests pass, exit code 0

  # Schema module verification  
  sbt schemaJVM/test
  # Assert: All tests pass (ReflectPrinter works with new toString), exit code 0

  # Format
  sbt typeidJVM/scalafmt
  sbt schemaJVM/scalafmt
  ```

  **Commit**: YES (if formatting changes)
  - Message: `chore: format typeid and schema modules`
  - Files: Any formatted files
  - Pre-commit: `sbt typeidJVM/test && sbt "++2.13.18; typeidJVM/test"`

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 1 | `feat(typeid): add TypeIdPrinter utility` | TypeIdPrinter.scala | compile |
| 2, 3, 4 | `feat(typeid): update toString to idiomatic Scala` | TypeId.scala (x2), TypeRepr.scala, TypeParam.scala | compile both Scala versions |
| 5 | `test(typeid): update tests for new format` | *Spec.scala files | typeidJVM/test |
| 6 | `chore: format and verify` | any formatted | full test suite |

---

## Success Criteria

### Verification Commands
```bash
# Primary verification
sbt typeidJVM/test           # Scala 3 TypeId tests
sbt "++2.13.18; typeidJVM/test"  # Scala 2 TypeId tests
sbt schemaJVM/test           # Schema tests (uses TypeId.toString)
```

### Expected Output Examples
```scala
TypeId.of[Int].toString                    // "Int"
TypeId.of[List[Int]].toString              // "List[Int]"
TypeId.of[java.util.UUID].toString         // "java.util.UUID"
TypeId.of[java.lang.String].toString       // "String"
TypeId.of[Map[String, Int]].toString       // "Map[String, Int]"

TypeRepr.Intersection(List(a, b)).toString // "A & B"
TypeRepr.Union(List(a, b)).toString        // "A | B"
TypeRepr.Function(List(int), str).toString // "Int => String"
TypeRepr.tuple(List(int, str)).toString    // "(Int, String)"

TypeParam("A", 0, Variance.Covariant).toString      // "+A"
TypeParam.higherKinded("F", 0, 1).toString          // "F[_]"
```

### Final Checklist
- [x] All TypeId tests pass (Scala 3)
- [x] All TypeId tests pass (Scala 2)
- [x] All Schema tests pass
- [x] TypeId.toString produces idiomatic Scala syntax
- [x] TypeRepr.toString produces idiomatic Scala syntax
- [x] TypeParam.toString produces clean format (no index)
- [x] Hybrid naming works (short for scala.*, full for java.*)
- [x] Code is formatted
