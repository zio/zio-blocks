---
id: migration
title: "Migration"
---

`Migration[A, B]` is the typed, composable wrapper that turns a structural description of how
`Schema[A]` relates to `Schema[B]` into a total function `A => Either[MigrationError, B]`. It lives
in the `zio.blocks.schema.migration` subpackage of the core `schema` module, and is built entirely
on top of existing infrastructure — `Schema`, `DynamicValue`, `DynamicOptic`, `SchemaExpr`, and the
`CompanionOptics` selector macro — so there is no separate dependency, no code generation, and no
runtime reflection.

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.migration._

final case class PersonV0(firstName: String, lastName: String)
object PersonV0 extends CompanionOptics[PersonV0] {
  implicit val schema: Schema[PersonV0] = Schema.derived
}

final case class Person(firstName: String, familyName: String, fullName: String)
object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived
}

val concatNames: SchemaExpr[PersonV0, String] =
  SchemaExpr.StringConcat(
    SchemaExpr.StringConcat(
      SchemaExpr.Optic(PersonV0.optic(_.firstName)),
      SchemaExpr.Literal(" ", Schema[String])
    ),
    SchemaExpr.Optic(PersonV0.optic(_.lastName))
  )

val v0ToV1: Migration[PersonV0, Person] =
  Migration
    .builder[PersonV0, Person]
    .renameField(_.lastName, _.familyName)
    .join(_.fullName, Seq(_.firstName, _.lastName), concatNames)
    .build
```

## Entry Points

All migration construction starts from the `Migration` companion:

- `Migration.identity[A]` — the empty migration; `apply(a) == Right(a)` for every `a` whose schema
  round-trips through `toDynamicValue` / `fromDynamicValue`.
- `Migration.builder[A, B]` — the typed builder used to describe a source → target migration as a
  sequence of selector-based operations.

The `Migration` envelope itself is a `final case class` carrying the source schema, the target
schema, and a pure `DynamicMigration` (a serializable `Chunk[MigrationAction]`). Values are composed
with `++` / `andThen` and structurally inverted with `reverse` — `m.reverse.reverse == m` holds by
construction.

## Selector-Based Builder API

`MigrationBuilder[A, B]` is a pure data carrier whose methods each append exactly one
`MigrationAction`. Selectors are macro-analyzed against the same `CompanionOptics` machinery that
powers `optic(_.field.each.when[T])` — you point at fields the same way you would for a `Lens`, and
the builder extracts the corresponding `DynamicOptic` path at compile time.

| Operation            | Shape                                                                                        | Purpose                                                            |
| -------------------- | -------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `addField`           | `(_.targetField, default: SchemaExpr[B, F])`                                                 | Insert a new field, filled from `default`                          |
| `dropField`          | `(_.sourceField, defaultForReverse: SchemaExpr[A, F])`                                       | Remove a field; `reverse` uses the carried default                 |
| `renameField`        | `(_.sourceField, _.targetField)`                                                             | Rename a field in place (source and target must share a parent)    |
| `transformField`     | `(_.srcField, _.tgtField, transform: SchemaExpr[A, T])`                                      | Apply a same-type value transform                                  |
| `changeFieldType`    | `(_.srcField, _.tgtField, converter: SchemaExpr[A, T])`                                      | Apply a type-changing conversion                                   |
| `mandateField`       | `(_.sourceField, _.targetField, default: SchemaExpr[A, T])`                                  | Unwrap `Option[T]` → `T`, filling `None` with `default`            |
| `optionalizeField`   | `(_.sourceField, _.targetField)`                                                             | Wrap `T` → `Option[T]`                                             |
| `transformElements`  | `(_.collection, transform: SchemaExpr[A, E])`                                                | Map each element of a `Vector`/`Chunk`/`Set`                       |
| `transformKeys`      | `(_.map, transform: SchemaExpr[A, K])`                                                       | Inject-map a `Map[K, V]` by key                                    |
| `transformValues`    | `(_.map, transform: SchemaExpr[A, V])`                                                       | Map a `Map[K, V]` by value                                         |
| `renameCase`         | `(from: String, to: String)`                                                                 | Rename a sealed-trait case                                         |
| `transformCase`      | `[CaseA, CaseB](caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB])` | Apply a nested migration scoped to one case                    |
| `join`               | `(_.target, sources: Seq[_.source], combiner: SchemaExpr[A, T])`                             | Combine N source paths into one target via `combiner`              |
| `split`              | `(_.source, targets: Seq[_.target], splitter: SchemaExpr[A, T])`                             | Split one source path into N targets via `splitter`                |

Two terminal methods close the builder:

- `build` — returns the typed `Migration[A, B]`. In the current phase this is equivalent to
  `buildPartial`; a later iteration promotes `build` to a macro that rejects incomplete migrations
  at compile time.
- `buildPartial` — returns the typed `Migration[A, B]` without any completeness validation.

The selector syntax is identical across Scala 2.13 and Scala 3 — the macro implementation is split
between `src/main/scala-2` and `src/main/scala-3`, but the surface is unified. See
[`CompanionOptics`](./optics.md) for the full selector grammar, and [`DynamicOptic`](./dynamic-optic.md)
for the runtime path representation the builder produces.

## Worked Example: `PersonV0 -> Person`

The canonical teaching example renames `lastName` to `familyName`, then joins `firstName` and the
original `lastName` into a derived `fullName` field. Both Scala 2.13 and Scala 3 are shown below —
only the companion-optic accessor shape differs; the builder API is the same.

### Scala 3

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.migration._

final case class PersonV0(firstName: String, lastName: String)
object PersonV0 extends CompanionOptics[PersonV0] {
  implicit val schema: Schema[PersonV0] = Schema.derived
  val firstName: Lens[PersonV0, String] = optic(_.firstName)
  val lastName: Lens[PersonV0, String]  = optic(_.lastName)
}

final case class Person(firstName: String, familyName: String, fullName: String)
object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived
}

// firstName + " " + lastName — explicit Join with StringConcat.
val combiner: SchemaExpr[PersonV0, String] =
  SchemaExpr.StringConcat(
    SchemaExpr.StringConcat(
      SchemaExpr.Optic(PersonV0.firstName),
      SchemaExpr.Literal(" ", Schema[String])
    ),
    SchemaExpr.Optic(PersonV0.lastName)
  )

val v0ToV1: Migration[PersonV0, Person] =
  Migration
    .builder[PersonV0, Person]
    .renameField(_.lastName, _.familyName)
    .join(_.fullName, Seq(_.firstName, _.lastName), combiner)
    .build

val migrated: Either[MigrationError, Person] =
  v0ToV1(PersonV0("Ada", "Lovelace"))
// Right(Person("Ada", "Lovelace", "Ada Lovelace"))
```

### Scala 2.13

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.migration._

final case class PersonV0(firstName: String, lastName: String)
object PersonV0 {
  implicit val schema: Schema[PersonV0] = Schema.derived

  val firstName: Lens[PersonV0, String] =
    schema.reflect.asRecord.get.lensByName[String]("firstName").get
  val lastName: Lens[PersonV0, String]  =
    schema.reflect.asRecord.get.lensByName[String]("lastName").get
}

final case class Person(firstName: String, familyName: String, fullName: String)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val combiner: SchemaExpr[PersonV0, String] =
  SchemaExpr.StringConcat(
    SchemaExpr.StringConcat(
      SchemaExpr.Optic(PersonV0.firstName),
      SchemaExpr.Literal(" ", Schema[String])
    ),
    SchemaExpr.Optic(PersonV0.lastName)
  )

val v0ToV1: Migration[PersonV0, Person] =
  Migration
    .builder[PersonV0, Person]
    .renameField(_.lastName, _.familyName)
    .join(_.fullName, Seq(_.firstName, _.lastName), combiner)
    .build
```

The builder is identical on both lanes. The only thing that changes between versions is how the
companion carries its accessors — Scala 3 exposes the `optic(_)` macro via `CompanionOptics`, while
Scala 2.13 pulls `Lens` values out of the derived `Reflect.Record`. The selector syntax inside the
builder (`_.firstName`, `_.lastName`, `_.fullName`) resolves to the same `DynamicOptic` path in both
cases.

## Join Semantics: Derived `fullName`

Combining two fields into a third is always spelled explicitly. There is no implicit shortcut for
"concatenate firstName + lastName into fullName" — every migration that synthesizes a new field
provides a `SchemaExpr` combiner so the resulting `DynamicMigration` stays fully serializable.

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.migration._
import zio.blocks.chunk.Chunk

// The underlying Join action expanded out of the builder call:
val joinAction: MigrationAction =
  MigrationAction.Join(
    at           = DynamicOptic.root.field("fullName"),
    sourcePaths  = Chunk(
      DynamicOptic.root.field("firstName"),
      DynamicOptic.root.field("lastName")
    ),
    combiner     = SchemaExpr.StringConcat(
      SchemaExpr.StringConcat(
        SchemaExpr.Literal("Ada",     Schema[String]),
        SchemaExpr.Literal(" ",       Schema[String])
      ),
      SchemaExpr.Literal("Lovelace",  Schema[String])
    )
  )
```

`Join` carries (a) the target path, (b) the ordered source paths, and (c) the `SchemaExpr`
combiner. Its `reverse` is the symmetric `Split` with `targetPaths = sourcePaths` and
`splitter = combiner` — the same pair of paths and the same expression, read the other way round.

## Error Model

`apply` produces `Either[MigrationError, B]`. The error hierarchy is:

- `MigrationError.ActionFailed(path, actionName, cause)` — an action failed at `path`. Decoding
  the migrated `DynamicValue` back into `B` also goes through this arm, with `actionName = "decode"`
  and `path = DynamicOptic.root`.
- `MigrationError.SchemaMismatch(path, expected, actual)` — the source `DynamicValue` shape did
  not match what the action expected (e.g. a `RenameCase` where the runtime case does not match
  `from`).
- `MigrationError.Irreversible(path, reason)` — raised by `Split` when `splitter`'s output does
  not line up with the declared `targetPaths`.
- `MigrationError.KeyCollision(path, key)` — two distinct keys collapsed to the same target key
  under `transformKeys`.

Every error carries a `DynamicOptic` path so diagnostics can be reported in terms of the user's
original selector syntax (see [`DynamicOptic.toScalaString`](./dynamic-optic.md)).

## Serialization

`Migration[A, B]` itself is not serialized directly — the source and target `Schema` values are
part of the in-process typed envelope. The underlying `DynamicMigration` is serializable through
the standard `Codec` / `Format` infrastructure, because `MigrationAction` has a derived
`Schema[MigrationAction]` and `SchemaExpr`'s migration-used subset (`DefaultValue`, `Literal`,
`StringConcat`) has a bridge `Schema` via `SchemaExpr.migrationSchema`.

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.migration._
import zio.blocks.schema.json.JsonFormat
import java.nio.ByteBuffer

val migration: Migration[PersonV0, Person] = ???   // from above

val dynamic: DynamicMigration = migration.dynamic
val codec = Schema[DynamicMigration].derive(JsonFormat)

val buf = ByteBuffer.allocate(4096)
codec.encode(buf)(dynamic)
buf.flip()
val decoded: Either[SchemaError, DynamicMigration] = codec.decode(buf)
```

Round-tripping the `DynamicMigration` through a codec preserves the action sequence verbatim —
the same `Chunk[MigrationAction]` goes in and comes out, with every embedded `DynamicOptic`,
`SchemaRepr`, and `SchemaExpr` reconstructed structurally. Rehydrating back into a typed
`Migration[A, B]` is done by pairing the decoded `DynamicMigration` with the runtime `Schema[A]`
and `Schema[B]` instances.

## Runnable Example

A full cross-version compile-checked example lives in the `schema-examples` module at
`schema-examples/src/main/scala/ziosschemamigration/migration/MigrationExample.scala`. It exercises
`renameField`, `addField` with a default, an `optionalizeField` + `mandateField` round-trip,
`transformElements`, `renameCase`, and the explicit `Join` semantics described above, on a richer
domain than `Person`. Run it with:

```bash
sbt "schema-examples/runMain ziosschemamigration.migration.MigrationExample"
```

## Selector Syntax Pointer

The builder's selector form is the same macro surface as the rest of the optics system — see
[`CompanionOptics`](./optics.md) for what the selector supports (field access, `.each`, `.when[T]`,
and composition), and [`DynamicOptic`](./dynamic-optic.md) for how those selectors are represented
at runtime.

## Going Further

- [`DynamicOptic`](./dynamic-optic.md) — the runtime path representation produced by the builder.
- [`SchemaExpr`](./schema-expr.md) — the pure-data expression language used for defaults,
  transforms, and join combiners.
- [`DynamicValue`](./dynamic-value.md) — the format-agnostic intermediate the interpreter walks.
- [`Codec`](./codec.md) and [`Formats`](./formats.md) — serialization infrastructure that
  round-trips `DynamicMigration`.
- The [ZIO Schema migration guide](../../guides/zio-schema-migration.md) covers migrating a
  ZIO-Schema-1.x codebase to ZIO Blocks Schema — a separate concern from the `Migration[A, B]`
  API on this page.
