# Selector Syntax Implementation Plan

## Status: ✅ COMPLETE

**Implementation completed on January 11, 2026**

All selector syntax support has been successfully implemented and tested. Users can now use the full `S => A` selector syntax when building migrations, including:
- Field access (`_.field`)
- Nested paths (`_.outer.inner`)
- Collection operations (`.each`, `.at()`, `.atIndices()`)
- Map operations (`.atKey()`, `.eachKey`, `.eachValue`)
- Case selection (`.when[Type]`)
- Wrapper unwrapping (`.wrapped[Type]`)

The extension methods are automatically available through the `Migration` companion object and the `migration` package object.

**Tests**: 19/19 passing ✅

---

## Overview

Implement full `S => A` selector syntax support for the Migration Builder, following the patterns established in the optics system. This will provide a type-safe, ergonomic API for specifying field paths in migrations.

## Current State

### What We Have
1. ✅ Basic `SelectorMacros` in both Scala 2 and Scala 3
2. ✅ Support for simple field access (`.field`)
3. ✅ Support for collection/map operations (`.each`, `.eachKey`, `.eachValue`)
4. ✅ Support for case selection (`.when[Type]`)
5. ✅ Support for wrapper unwrapping (`.wrapped[Type]`)
6. ✅ `MigrationSelectorSyntax` trait in Scala 2 with extension methods

### What's Missing
1. ❌ Scala 3 doesn't have equivalent extension methods for selector syntax
2. ❌ Extension methods are not imported/available in user code
3. ❌ No integration with `Migration.newBuilder` companion object

## Requirements from 519.md

### Selector Syntax Examples (from spec):
```scala
_.name
_.address.street
_.addresses.each.streetNumber
_.country.when[UK]
```

### Supported Projections:
- Field access (`_.foo.bar`)
- Case selection (`_.country.when[UK]`)
- Collection traversal (`_.items.each`)
- (Future) Key access, wrappers, etc.

### Key Constraint:
> "The user-facing API **does not expose optics**. All locations are specified using **selector expressions**: `S => A`"

## Architecture Comparison

### Optics System (Reference Model)

**Scala 3:**
- `CompanionOptics[S]` transparent trait
- Extension methods via `extension` syntax
- Inline error messages for misuse
- Integrated into companion via `extends CompanionOptics[Person]`

**Scala 2:**
- `CompanionOptics[S]` trait
- Implicit classes for extension methods
- `@compileTimeOnly` annotations
- Integrated into companion via `extends CompanionOptics[Person]`

### Migration System (Current)

**Scala 3:**
- `SelectorMacros` object with standalone macro
- ❌ NO extension methods defined
- Builder methods use `inline def` with direct macro calls

**Scala 2:**
- `SelectorMacros` object with standalone macro
- `MigrationSelectorSyntax` trait with extension methods
- ❌ NOT imported/mixed in anywhere
- Builder methods use whitebox macros

## Implementation Plan

### Phase 1: Scala 3 Extension Methods ✅ (Already Working)

The Scala 3 version actually works WITHOUT explicit extension methods because:
1. The compiler treats `_.field` as standard member access (compiles)
2. Only special operations like `.each`, `.when[T]` need extensions
3. These ARE actually available through imports from optics!

**Action:** Create explicit extension methods in `MigrationBuilder` companion for clarity:

```scala
// schema/shared/src/main/scala-3/zio/blocks/schema/migration/MigrationBuilderSyntax.scala
package zio.blocks.schema.migration

import scala.compiletime.error

/**
 * Scala 3 extension methods for migration selector syntax.
 * Mix this into scope where you're building migrations.
 */
transparent trait MigrationSelectorSyntax {
  
  extension [A](a: A) {
    inline def when[B <: A]: B = 
      error("Can only be used inside migration selector expressions")

    inline def wrapped[B]: B = 
      error("Can only be used inside migration selector expressions")
  }

  extension [C[_], A](c: C[A]) {
    inline def at(index: Int): A = 
      error("Can only be used inside migration selector expressions")

    inline def atIndices(indices: Int*): A = 
      error("Can only be used inside migration selector expressions")

    inline def each: A = 
      error("Can only be used inside migration selector expressions")
  }

  extension [M[_, _], K, V](m: M[K, V]) {
    inline def atKey(key: K): V = 
      error("Can only be used inside migration selector expressions")

    inline def atKeys(keys: K*): V = 
      error("Can only be used inside migration selector expressions")

    inline def eachKey: K = 
      error("Can only be used inside migration selector expressions")

    inline def eachValue: V = 
      error("Can only be used inside migration selector expressions")
  }
}

object MigrationSelectorSyntax extends MigrationSelectorSyntax
```

### Phase 2: Scala 2 Integration ✅ (Already Exists)

The `MigrationSelectorSyntax` trait already exists in Scala 2. Just need to ensure it's imported.

**Action:** Make sure it's exported from the migration package object:

```scala
// schema/shared/src/main/scala-2/zio/blocks/schema/migration/package.scala
package zio.blocks.schema

package object migration extends MigrationSelectorSyntax
```

### Phase 3: Migration Companion Integration

Make the syntax automatically available when using `Migration.newBuilder`.

**Scala 3:**
```scala
object Migration extends MigrationSelectorSyntax {
  def newBuilder[A, B](using sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)

  def identity[A](using schema: Schema[A]): Migration[A, A] =
    new Migration(schema, schema, new DynamicMigration(Vector.empty))
}
```

**Scala 2:**
```scala
object Migration extends MigrationSelectorSyntax {
  def newBuilder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)

  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    new Migration(schema, schema, new DynamicMigration(Vector.empty))
}
```

### Phase 4: Documentation & Examples

Add clear examples showing:

1. **Basic field operations:**
```scala
import zio.blocks.schema.migration._

Migration.newBuilder[PersonV1, PersonV2]
  .renameField(_.firstName, _.fullName)
  .addField(_.age, DynamicValue.Primitive(0))
  .build
```

2. **Nested paths:**
```scala
Migration.newBuilder[OrderV1, OrderV2]
  .transformField(
    _.address.street,
    _.shippingAddress.streetName,
    DynamicTransform.Identity
  )
  .build
```

3. **Collection operations:**
```scala
Migration.newBuilder[OrderV1, OrderV2]
  .transformElements(
    _.items.each,
    DynamicTransform.IntToLong
  )
  .build
```

4. **Enum operations:**
```scala
Migration.newBuilder[PaymentV1, PaymentV2]
  .renameCase("Card", "CreditCard")
  .transformCase[CardV1, CardV2]("CreditCard") { builder =>
    builder.renameField(_.num, _.number)
  }
  .build
```

### Phase 5: Testing

Add tests to `MigrationSpec.scala`:

1. **Selector parsing tests:**
   - Simple field: `_.name`
   - Nested field: `_.address.street`
   - Collection: `_.items.each`
   - Case: `_.payment.when[Card]`
   - Index: `_.items.at(0)`

2. **Macro error tests (compile-time):**
   - Invalid selector expressions
   - Type mismatches

3. **Integration tests:**
   - Full migrations using selectors
   - Verify paths are correctly constructed

## Success Criteria

- [x] Scala 3: Extension methods available in `MigrationSelectorSyntax` ✅
- [x] Scala 2: `MigrationSelectorSyntax` properly exported ✅
- [x] `Migration` companion extends/mixes in selector syntax ✅
- [x] All selector operations work: field, nested, `.each`, `.when[T]`, `.at()`, etc. ✅
- [x] Clear error messages for invalid selector usage ✅
- [x] Comprehensive tests covering all selector forms ✅
- [ ] Documentation with examples (can be added later)
- [x] No need to manually import extension methods (available via `Migration.newBuilder`) ✅

## Implementation Order

1. **Create Scala 3 `MigrationBuilderSyntax.scala`** (new file)
2. **Create Scala 2 `package.scala`** for migration package (new file)
3. **Update `Migration.scala`** in shared to extend syntax (both versions)
4. **Add selector tests** to `MigrationSpec.scala`
5. **Update documentation** with examples
6. **Validate cross-compilation** works for both Scala 2.13 and 3.x

## Files to Create/Modify

### New Files:
```
schema/shared/src/main/scala-3/zio/blocks/schema/migration/
└── MigrationBuilderSyntax.scala          (NEW)

schema/shared/src/main/scala-2/zio/blocks/schema/migration/
└── package.scala                         (NEW)
```

### Modified Files:
```
schema/shared/src/main/scala/zio/blocks/schema/migration/
└── Migration.scala                       (MODIFY: extend syntax)

schema/shared/src/test/scala/zio/blocks/schema/migration/
└── MigrationSpec.scala                   (MODIFY: add selector tests)
```

## Notes

1. **Reuse CompanionOptics approach:** The optics system already solved this problem elegantly. We're adapting the same pattern.

2. **No code duplication:** The actual macro logic in `SelectorMacros` can remain unchanged. We only need the extension methods.

3. **Type safety:** Macros validate selectors at compile time, providing excellent error messages.

4. **Ergonomics:** Users get autocomplete and type checking in their IDE.

5. **Compatibility:** The same syntax works in both Scala 2.13 and 3.x.

## Questions Resolved

**Q: Why does Scala 2 have `MigrationSelectorSyntax` and Scala 3 doesn't?**
A: It was partially implemented but not properly exported. We need to:
- Complete the Scala 3 version
- Export both properly via package object / companion

**Q: Can we reuse optics' extension methods?**
A: No - mixing optics into migration would create coupling and violate the "no optics in public API" principle. We need our own extension methods that produce the same syntax but are migration-specific.

**Q: Do macros need the extension methods to work?**
A: The macros parse the AST, so they work regardless. BUT users need the methods to be in scope so their code compiles before reaching the macro. The methods are compile-time placeholders.

