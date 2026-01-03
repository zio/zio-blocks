# TypeId Implementation Plan

## Issue Summary

**Issue #471 / Bounty: $3000**

Implement `TypeId`, a replacement for `TypeName` that provides:
- Rich type identity representation (nominal, alias, opaque types)
- Macro derivation support for Scala 2.13 and Scala 3.5+
- Complete test suite
- Migration of existing code from `TypeName` to `TypeId`
- Removal of `TypeName` once fully replaced

## Requirements Analysis

### What TypeId Provides Over TypeName

| Feature | TypeName (Current) | TypeId (Proposed) |
|---------|-------------------|-------------------|
| Type identity | Simple name + namespace | Full type identity with owner path |
| Type parameters | Stored as nested TypeNames | First-class TypeParam with index |
| Type kinds | Not distinguished | Distinguishes nominal, alias, opaque |
| Higher-kinded types | Limited support | Full support via `AnyKind` |
| Type expressions | Not supported | Full TypeRepr for complex types |
| Structural types | Not supported | Full member representation |
| Phantom typing | None | TypeId[A <: AnyKind] for type safety |

### Core Components (from 471.md)

1. **Owner**: Where a type is defined (packages, terms, types)
2. **TypeParam**: Type parameter specification (name, index)
3. **TypeId[A <: AnyKind]**: Identity of a type or type constructor
4. **TypeRepr**: Type expressions (Ref, Applied, Structural, Union, Intersection, etc.)
5. **Member**: Structural type members (Val, Def, TypeMember)
6. **TermPath**: For singleton types

## Project Structure

As suggested, `TypeId` should be a **separate top-level module** within ZIO Blocks:

```
zio-blocks/
├── typeid/                           # NEW: TypeId module
│   ├── shared/
│   │   └── src/
│   │       ├── main/
│   │       │   ├── scala/            # Cross-version code
│   │       │   │   └── zio/blocks/typeid/
│   │       │   │       ├── Owner.scala
│   │       │   │       ├── TypeParam.scala
│   │       │   │       ├── TypeId.scala
│   │       │   │       ├── TypeRepr.scala
│   │       │   │       ├── Member.scala
│   │       │   │       ├── TermPath.scala
│   │       │   │       └── package.scala
│   │       │   ├── scala-2/          # Scala 2.13 specific
│   │       │   │   └── zio/blocks/typeid/
│   │       │   │       └── TypeIdMacros.scala
│   │       │   └── scala-3/          # Scala 3.5+ specific
│   │       │       └── zio/blocks/typeid/
│   │       │           └── TypeIdMacros.scala
│   │       └── test/
│   │           ├── scala/
│   │           │   └── zio/blocks/typeid/
│   │           │       ├── TypeIdSpec.scala
│   │           │       ├── TypeReprSpec.scala
│   │           │       ├── OwnerSpec.scala
│   │           │       └── DerivationSpec.scala
│   │           ├── scala-2/
│   │           │   └── zio/blocks/typeid/
│   │           │       └── Scala2DerivationSpec.scala
│   │           └── scala-3/
│   │               └── zio/blocks/typeid/
│   │                   └── Scala3DerivationSpec.scala
│   ├── jvm/
│   ├── js/
│   └── native/
├── schema/                           # Existing: will depend on typeid
├── ...
```

## Implementation Phases

### Phase 1: Core Data Structures (No Macros)

**Estimated Effort: 2-3 days**

Create the fundamental ADTs in `typeid/shared/src/main/scala`:

#### 1.1 Owner.scala
```scala
package zio.blocks.typeid

final case class Owner(segments: List[Owner.Segment]) {
  def asString: String = segments.map(_.name).mkString(".")
}

object Owner {
  sealed trait Segment { def name: String }
  final case class Package(name: String) extends Segment
  final case class Term(name: String)    extends Segment
  final case class Type(name: String)    extends Segment
  val Root: Owner = Owner(Nil)
}
```

#### 1.2 TypeParam.scala
```scala
package zio.blocks.typeid

final case class TypeParam(
  name: String,
  index: Int
  // Future: variance, bounds, kind
)
```

#### 1.3 TypeId.scala
- Sealed trait with phantom type parameter `A <: AnyKind`
- Private implementations: `NominalImpl`, `AliasImpl`, `OpaqueImpl`
- Factory methods: `nominal`, `alias`, `opaque`
- Pattern matching extractors: `Nominal`, `Alias`, `Opaque`
- Placeholder for `derive[A <: AnyKind]` macro

#### 1.4 TypeRepr.scala
- Complete ADT for type expressions
- All variants from 471.md (Ref, ParamRef, Applied, Structural, etc.)

#### 1.5 Member.scala
- Structural type members (Val, Def, TypeMember)
- Param for method parameters

#### 1.6 TermPath.scala
- For singleton types

### Phase 2: Scala 3 Macro Derivation

**Estimated Effort: 3-4 days**

In `typeid/shared/src/main/scala-3/zio/blocks/typeid/TypeIdMacros.scala`:

#### 2.1 Basic Type Information Extraction
- Extract type name, owner path (packages, enclosing objects/classes)
- Handle type parameters

#### 2.2 Type Classification
- Detect nominal types vs type aliases vs opaque types
- Use Scala 3's `Flags` API to distinguish

#### 2.3 TypeRepr Construction
- Build TypeRepr from `quotes.reflect.TypeRepr`
- Handle applied types, type parameters, etc.

#### 2.4 Special Cases
- Handle Scala primitives (Int, String, etc.)
- Handle built-in collections (List, Option, Map, etc.)
- Handle union/intersection types
- Handle structural/refinement types
- Handle higher-kinded types

### Phase 3: Scala 2.13 Macro Derivation

**Estimated Effort: 3-4 days**

In `typeid/shared/src/main/scala-2/zio/blocks/typeid/TypeIdMacros.scala`:

#### 3.1 Mirror Scala 3 Functionality
- Use `scala.reflect.macros.blackbox.Context`
- Extract owner path using symbol ownership chain
- Detect type aliases (symbol.isAliasType)
- Note: Opaque types don't exist in Scala 2.13

#### 3.2 Type Classification Differences
- No opaque types in 2.13 - treat as nominal
- Different APIs for extracting type information

### Phase 4: Test Suite

**Estimated Effort: 2-3 days**

#### 4.1 Unit Tests for Data Structures
- Owner construction and string representation
- TypeParam equality and indexing
- TypeId construction and pattern matching
- TypeRepr building and traversal

#### 4.2 Macro Derivation Tests
- Primitives: `TypeId.derive[Int]`, `TypeId.derive[String]`
- Standard library: `TypeId.derive[List]`, `TypeId.derive[Option]`
- User-defined classes: case classes, sealed traits
- Type aliases (Scala 3): `type Age = Int`
- Opaque types (Scala 3): `opaque type Email = String`
- Higher-kinded types: `TypeId.derive[List]` (arity 1)
- Nested types: types defined inside objects/classes
- Generic types: `TypeId.derive[Either]` (arity 2)

#### 4.3 Edge Cases
- Recursive types
- Types with complex ownership chains
- Types with variance annotations
- Local types
- Anonymous types

### Phase 5: Integration with Schema

**Estimated Effort: 3-4 days**

#### 5.1 Add TypeId Dependency
Update `build.sbt`:
```scala
lazy val schema = crossProject(...)
  .dependsOn(typeid)
  // ...
```

#### 5.2 Create Compatibility Layer
Option A: Gradual Migration
```scala
// In schema module
type TypeName[A] = TypeId[A]  // Temporary alias
object TypeName {
  def apply[A](namespace: Namespace, name: String, params: Seq[TypeName[?]]): TypeId[A] = 
    // Convert to TypeId
}
```

Option B: Direct Replacement
- Replace all TypeName usages with TypeId
- Update Reflect trait and all implementations
- Update Schema derivation macros

#### 5.3 Update Reflect.scala
- Change `def typeName: TypeName[A]` to `def typeId: TypeId[A]`
- Update all subclasses: Record, Variant, Sequence, Map, Dynamic, Primitive, Wrapper, Deferred

#### 5.4 Update Schema Derivation
- Scala 3: `SchemaVersionSpecific.scala` - replace `typeName` method
- Scala 2: Update corresponding macro code

#### 5.5 Update Dependent Modules
- `schema-avro/`: Update AvroFormat.scala
- `schema/json/`: Update JsonBinaryCodecDeriver.scala
- Any other modules using TypeName

### Phase 6: TypeName Removal

**Estimated Effort: 1-2 days**

#### 6.1 Final Migration
- Search for any remaining TypeName references
- Replace with TypeId equivalents
- Remove TypeName.scala and Namespace.scala

#### 6.2 Documentation Update
- Update README.md
- Update docs/index.md
- Add migration guide

### Phase 7: Clean-up and Polish

**Estimated Effort: 1-2 days**

- Code review and refactoring
- Performance optimization
- API documentation (ScalaDoc)
- Final test pass

## Affected Files

### New Files (typeid module)
- `typeid/shared/src/main/scala/zio/blocks/typeid/Owner.scala`
- `typeid/shared/src/main/scala/zio/blocks/typeid/TypeParam.scala`
- `typeid/shared/src/main/scala/zio/blocks/typeid/TypeId.scala`
- `typeid/shared/src/main/scala/zio/blocks/typeid/TypeRepr.scala`
- `typeid/shared/src/main/scala/zio/blocks/typeid/Member.scala`
- `typeid/shared/src/main/scala/zio/blocks/typeid/TermPath.scala`
- `typeid/shared/src/main/scala-2/zio/blocks/typeid/TypeIdMacros.scala`
- `typeid/shared/src/main/scala-3/zio/blocks/typeid/TypeIdMacros.scala`
- Test files (multiple)

### Modified Files (existing)
- `build.sbt` - Add typeid module, update schema dependency
- `schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala`
- `schema/shared/src/main/scala/zio/blocks/schema/PrimitiveType.scala`
- `schema/shared/src/main/scala-3/zio/blocks/schema/SchemaVersionSpecific.scala`
- `schema/shared/src/main/scala-2/zio/blocks/schema/SchemaVersionSpecific.scala`
- `schema-avro/src/main/scala/zio/blocks/schema/avro/AvroFormat.scala`
- `schema/shared/src/main/scala/zio/blocks/schema/json/JsonBinaryCodecDeriver.scala`
- Various test files

### Deleted Files
- `schema/shared/src/main/scala/zio/blocks/schema/TypeName.scala`
- `schema/shared/src/main/scala/zio/blocks/schema/Namespace.scala` (if not used elsewhere)

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Binary compatibility | High | Use MiMa to verify |
| Cross-compilation issues | Medium | Test on all platforms early |
| Macro differences 2.13 vs 3 | Medium | Comprehensive test suite |
| Performance regression | Medium | Benchmark critical paths |
| Missing edge cases | Low | Extensive test coverage |

## Timeline Estimate

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Phase 1: Core Data Structures | 2-3 days | None |
| Phase 2: Scala 3 Macros | 3-4 days | Phase 1 |
| Phase 3: Scala 2.13 Macros | 3-4 days | Phase 1 |
| Phase 4: Test Suite | 2-3 days | Phases 2, 3 |
| Phase 5: Schema Integration | 3-4 days | Phase 4 |
| Phase 6: TypeName Removal | 1-2 days | Phase 5 |
| Phase 7: Polish | 1-2 days | Phase 6 |

**Total: ~15-22 days** (assuming focused work)

## Open Questions

1. **Namespace compatibility**: Should `Owner` be compatible with the existing `Namespace` class for migration ease?

2. **TypeRepr complexity**: Do we need all TypeRepr variants (Union, Intersection, Structural) in v1, or can some be added later?

3. **Caching strategy**: The existing code caches TypeName derivations. Should TypeId follow the same pattern?

4. **Opaque type handling**: How should Scala 2.13 handle types that are opaque in Scala 3 companion libraries?

5. **Variance tracking**: Should TypeParam include variance information (`+A`, `-A`) in v1?

## Success Criteria

1. ✅ All existing tests pass after migration
2. ✅ New TypeId tests provide > 90% coverage
3. ✅ No binary compatibility breaks (MiMa clean)
4. ✅ Performance is not degraded (benchmark)
5. ✅ TypeName fully removed from codebase
6. ✅ Documentation updated
7. ✅ Works on JVM, JS, and Native platforms

## References

- Issue description: `471.md`
- Current TypeName: `schema/shared/src/main/scala/zio/blocks/schema/TypeName.scala`
- Current Namespace: `schema/shared/src/main/scala/zio/blocks/schema/Namespace.scala`
- Schema derivation (Scala 3): `schema/shared/src/main/scala-3/zio/blocks/schema/SchemaVersionSpecific.scala`
- Reflect trait: `schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala`

