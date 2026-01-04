# TypeId Implementation Tasks (#471)

## Phase 1: Create typeid Module

### Build Configuration
- [x] Add `typeid` cross-project to `build.sbt` (JVM/JS/Native)
- [x] Configure dependencies (no ZIO deps, pure Scala)
- [x] Set up scala-2 and scala-3 source directories for macros

### Core Types (shared)
- [x] Create `Owner.scala` with Segment ADT (Package, Term, Type)
- [x] Create `TypeParam.scala` (name, index)
- [x] Create `TypeId.scala` (sealed trait with Nominal, Alias, Opaque)
- [x] Create `TypeRepr.scala` (MVP: Ref, ParamRef, Applied only)

### Move PrimitiveType (avoid circular deps)
- [x] ~~Move PrimitiveType from schema to typeid~~ (Skip: too many schema deps)
- [x] ~~Move PrimitiveValue from schema to typeid~~ (Skip: circular with PrimitiveType)
- [x] ~~Move Validation from schema to typeid~~ (Skip: not needed)
- [x] Architectural decision: Keep PrimitiveType in schema, use TypeId lookup

### Macro Derivation
- [x] Create `TypeIdMacros.scala` for Scala 2.13 (blackbox.Context)
- [x] Create `TypeIdMacros.scala` for Scala 3.5+ (scala.quoted)
- [x] Support nominal types (case classes, traits)
- [x] Support type aliases  
- [x] Support opaque types (Scala 3)
- [ ] Cross-compile JVM/JS/Native (needs sbt)

### TypeId Tests
- [ ] Test `TypeId.derive[Int]` returns correct nominal
- [ ] Test `TypeId.derive[String]` returns correct nominal
- [ ] Test `TypeId.derive[CustomClass]` with correct owner
- [ ] Test `TypeId.derive[List[String]]` with type params
- [ ] Test `typeId.arity` for type constructors
- [ ] Test opaque type detection (Scala 3)
- [ ] Test primitive type lookup via `primitiveType`

---

## Phase 2: Migrate Schema Module

### Add typeid Dependency
- [x] Add `dependsOn(typeid)` etc. to schema project

### Extension Methods for wrap (avoid circular deps)
- [x] Create `TypeIdOps` implicit class in schema module
- [x] Implement `TypeIdOps.wrap` method
- [x] Implement `TypeIdOps.wrapTotal` method
- [ ] Remove `wrap`/`wrapTotal` from `Schema` class (after migration)

### Reflect.scala Migration
- [x] Replace `typeName: TypeName[A]` with `typeId: TypeId[A]`
- [x] Update `Record` class
- [x] Update `Variant` class
- [x] Update `Sequence` class
- [x] Update `Map` class
- [x] Update `Dynamic` class
- [x] Update `Primitive` class
- [x] Update `Wrapper` class (remove `wrapperPrimitiveType` field)
- [x] Update `Deferred` class

### Other Files Migration
- [ ] Update `PrimitiveType.scala` imports (now from typeid)
- [x] Update `Deriver.scala` signatures
- [x] Update `DerivationBuilder.scala` signatures
- [x] Update `ReflectTransformer.scala` signatures
- [ ] Update `InstanceOverride.scala`
- [ ] Update `ModifierOverride.scala`
- [ ] Update `JsonFormat.scala` (deriveWrapper)
- [ ] Update `AvroFormat.scala` (deriveWrapper)

### SchemaVersionSpecific (Macro Updates)
- [ ] Update Scala 2 macro to use `TypeId.derive`
- [ ] Update Scala 3 macro to use `TypeId.derive`
- [ ] Remove old `typeName` derivation logic

---

## Phase 3: Cleanup & Testing

### Phase 3A: Macro Migration (Current)
- [ ] Update `SchemaVersionSpecific.scala` (Scala 2) to use `TypeId.derive`
- [ ] Update `SchemaVersionSpecific.scala` (Scala 3) to use `TypeId.derive`
- [ ] Remove manual `TypeName` construction logic from macros
- [ ] Ensure generated code uses `typeId = ...` instead of `typeName = ...`

### Phase 3B: Cleanup & Verification
- [ ] Delete `TypeName.scala`
- [ ] Delete `Namespace.scala`
- [ ] Remove `Reflect.typeName` compatibility shim
- [ ] Run `sbt schemaJVM/test` (Regression)
- [ ] Run `sbt schemaJS/test` (Cross-platform)
- [ ] Run `sbt schemaNative/test` (Cross-platform)
- [ ] Run `sbt schema-avro/test` (Integration)
- [ ] Run `sbt build` (Full build)

### Documentation
- [ ] Update README if needed
- [ ] Create demo video for PR

---

## Architectural Notes

**Circular Dependency Avoidance:**
- `typeid` module is pure data, no deps on `schema`
- `PrimitiveType` lives in `typeid` (not `schema`)
- `wrap`/`wrapTotal` via extension methods in `schema`

**No Arity-Based Constructors:**
- Use `typeParams: List[TypeParam]` generically
- `arity == 1` → type constructor like `List`
- `arity == 0` → proper type like `Int`

**MVP TypeRepr:**
- Only `Ref`, `ParamRef`, `Applied`
- Extensible for future `Structural`, `Union`, `Intersection`
