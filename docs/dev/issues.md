# ZIO Blocks: library issues found while writing docs

Internal list of library bugs found while writing documentation. Each entry is written so it can be filed on GitHub as is. When an entry is filed, add the issue link under its heading.

## `Schema.fromJsonSchema` treats every property as required, ignoring `"required"`

Found 2026-10-20 while writing the how-to guide `docs/guides/generate-json-schema-and-validate-payloads.md` (backlog H-05), on `main` at `32866f71`. The code is the same at tag `v0.0.56`.

**Summary.** The JSON decoder of the `Schema[Json]` returned by `Schema.fromJsonSchema` rejects any object that leaves out a property listed in `"properties"`, even when `"required"` does not list it. A payload that conforms to the JSON Schema (`JsonSchema#check` returns `None`) fails to decode with `missing required field`.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-schema` 0.0.56):

```scala
import zio.blocks.schema._
import zio.blocks.schema.json._

val jsonSchema: JsonSchema = JsonSchema
  .parse("""{
    "type": "object",
    "properties": {
      "title": { "type": "string" },
      "isbn":  { "type": "string" },
      "tags":  { "type": "array", "items": { "type": "string" } }
    },
    "required": ["title"]
  }""")
  .fold(e => throw e, identity)

val payload = """{"title":"Zionomicon"}"""

println(jsonSchema.check(Json.parse(payload).toOption.get))
// None  (the payload conforms)

println(Schema.fromJsonSchema(jsonSchema).jsonCodec.decode(payload))
// Left(zio.blocks.schema.SchemaError: missing required field "isbn" at: .wrapped)

println(Schema.fromJsonSchema(jsonSchema).jsonCodec.decode("""{"title":"Zionomicon","isbn":"x"}"""))
// Left(zio.blocks.schema.SchemaError: missing required field "tags" at: .wrapped)
```

**Expected.** `decode(payload)` returns `Right(json)`: properties that `"required"` does not list may be absent. Only `title` should be required.

**Root cause.** `JsonSchemaToReflect.buildRecord` receives the `required` set but never uses it — the parameter is marked `@scala.annotation.unused` (`schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchemaToReflect.scala:311`). Every property becomes a plain `Term` with no default value and no optional wrapper (`:316-318`), so the derived JSON codec treats each one as a required field. The record constructor already turns a missing register into `DynamicValue.Null` (`:327-328`), but the codec fails with `missing required field` before the constructor runs.

**Impact.** `Schema.fromJsonSchema` is unusable for most real-world schemas, which almost always have optional properties. Its Scaladoc says values are validated against the JSON Schema, but valid values are rejected before that validation runs.

**Test gap.** `SchemaFromJsonSchemaSpec` only decodes objects that contain every listed property, so it never covers an absent optional property.

**Suggested fix.** In `buildRecord`, make each property outside `required` optional, so that the codec accepts its absence: give the term a default value, or wrap it as an optional field. Leave it out of the rebuilt `Json` object when it was absent, so that `Json.fromDynamicValue` does not produce `"isbn": null` and fail `check` against `{"type":"string"}`. Add a `SchemaFromJsonSchemaSpec` test that decodes an object without an optional property.

**Related (minor, same code path).** Errors raised by the `JsonSchema#check` inside `fromJsonSchema` end with ` at: .wrapped`, which exposes the internal wrapper's name in the path (for example `String length 0 is less than minLength 1 at: .title at: .wrapped`).

## A per-derivation `DerivationBuilder#instance` override silently loses to a deriver-level `Deriver#withInstance` override

Found 2026-10-20 while writing the how-to guide `docs/guides/override-codec-for-one-type.md` (backlog H-03), on `main` at `64b2f666`. The code is the same at tag `v0.0.56`. Verified by running against the published 0.0.56.

**Summary.** When a deriver already carries a `withInstance` override for a type, a `DerivationBuilder#instance` override for the same type on one `.derive` call is ignored without any warning. The narrower, more explicit request loses to the broader one, which is the opposite of what users expect from every other override mechanism (optic beats field name beats type).

**Reproduction** (Scala 3.9.0, `zio-blocks-next-schema` 0.0.56):

```scala
import zio.blocks.schema._
import zio.blocks.schema.json._
import zio.blocks.typeid.TypeId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

def dateCodec(f: DateTimeFormatter): JsonCodec[LocalDate] = new JsonCodec[LocalDate] {
  def decodeValue(in: JsonReader): LocalDate           = LocalDate.parse(in.readString(), f)
  def encodeValue(x: LocalDate, out: JsonWriter): Unit = out.writeVal(f.format(x))
}

case class Order(title: String, orderedOn: LocalDate)
object Order { implicit val schema: Schema[Order] = Schema.derived }

val order = Order("Zionomicon", LocalDate.of(2025, 3, 14))

// The application-wide deriver writes dd/MM/yyyy
val deriver = JsonCodecDeriver.withInstance[LocalDate](dateCodec(DateTimeFormatter.ofPattern("dd/MM/yyyy")))

// One endpoint asks for yyyyMMdd for this single codec
val codec = Schema[Order]
  .deriving(deriver)
  .instance(TypeId.of[LocalDate], dateCodec(DateTimeFormatter.BASIC_ISO_DATE))
  .derive

println(codec.encodeToString(order))
// {"title":"Zionomicon","orderedOn":"14/03/2025"}
```

**Expected.** `{"title":"Zionomicon","orderedOn":"20250314"}`: the override given for this one derivation should win over the deriver's default, or the conflict should at least be reported.

**Root cause.** `DerivationBuilder.derive` merges the two lists as `instanceOverrides ++ deriver.instanceOverrides` and then builds maps with `.toMap`, so for the same key the later entry, the deriver's, wins (`schema/shared/src/main/scala/zio/blocks/schema/derive/DerivationBuilder.scala:125-130`; the same applies to modifiers at `:126`). The Scaladoc of `Deriver#withInstance` documents this order (`schema/shared/src/main/scala/zio/blocks/schema/derive/Deriver.scala:108-113`), so it is intended, but nothing tells the user at the call site.

**Impact.** A team that configures one shared deriver cannot make an exception for a single codec with `DerivationBuilder#instance`; the call compiles and silently does nothing. The only workaround is to build a second deriver without the conflicting override.

**Test gap.** `DeriverWithInstanceSpec` tests a deriver-level override and a builder-level override separately (`schema/shared/src/test/scala/zio/blocks/schema/derive/DeriverWithInstanceSpec.scala:111-240`), but never both for the same type, so the precedence is untested.

**Suggested fix.** Swap the merge order to `deriver.instanceOverrides ++ instanceOverrides` (and the same for `modifierOverrides`) so that per-derivation overrides refine the deriver's defaults, update the Scaladoc at `Deriver.scala:108-113`, and add a `DeriverWithInstanceSpec` test that combines both levels for the same type. If the current order must stay, document it next to `DerivationBuilder#instance` as well, since that is where users look.

## A custom codec is silently not used when given as an implicit or through a configured deriver plus `toJsonString`

Found 2026-10-20 while writing the how-to guide `docs/guides/override-codec-for-one-type.md` (backlog H-03), on `main` at `64b2f666`. The code is the same at tag `v0.0.56`. Verified by running against the published 0.0.56.

**Summary.** Users coming from circe or zio-json expect an `implicit JsonCodec[LocalDate]` in scope to change how derived codecs encode `LocalDate`. In ZIO Blocks it has no effect, and no warning is emitted. Separately, after a user registers an override with `Deriver#withInstance`, the `toJsonString` and `fromJson` extension methods still use the default codec, again silently.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-schema` 0.0.56):

```scala
import zio.blocks.schema._
import zio.blocks.schema.json._
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

implicit val europeanDate: JsonCodec[LocalDate] = new JsonCodec[LocalDate] {
  def decodeValue(in: JsonReader): LocalDate           = LocalDate.parse(in.readString(), fmt)
  def encodeValue(x: LocalDate, out: JsonWriter): Unit = out.writeVal(fmt.format(x))
}

case class Order(title: String, orderedOn: LocalDate)
object Order { implicit val schema: Schema[Order] = Schema.derived }

val order = Order("Zionomicon", LocalDate.of(2025, 3, 14))

// 1. The implicit codec in scope is ignored by derivation
println(order.toJsonString)
// {"title":"Zionomicon","orderedOn":"2025-03-14"}
println(Schema[Order].derive(JsonFormat).encodeToString(order))
// {"title":"Zionomicon","orderedOn":"2025-03-14"}

// 2. A configured deriver works, but toJsonString does not use it
val codec = Schema[Order].deriving(JsonCodecDeriver.withInstance[LocalDate](europeanDate)).derive
println(codec.encodeToString(order))
// {"title":"Zionomicon","orderedOn":"14/03/2025"}
println(order.toJsonString)
// {"title":"Zionomicon","orderedOn":"2025-03-14"}
```

**Expected.** Either the implicit codec is picked up, or the user is told it will not be. Users reasonably assume `toJsonString` uses "the" JSON codec of their type.

**Root cause.** Derivation is driven by the schema, never by implicit search: `JsonCodecDeriver` maps every primitive to a built-in codec, for example `case _: PrimitiveType.LocalDate => localDateCodec` (`schema/shared/src/main/scala/zio/blocks/schema/json/JsonCodecDeriver.scala:310`), and overrides only come from `Deriver#withInstance` or `DerivationBuilder#instance`. `toJsonString` is `schema.jsonCodec.encodeToString(self)` (`schema/shared/src/main/scala-3/zio/blocks/schema/syntax.scala:34`, same in `scala-2/`), and `Schema#jsonCodec` is `getInstance(JsonFormat)`, which always uses the default `JsonFormat` deriver (`schema/shared/src/main/scala/zio/blocks/schema/Schema.scala:38-40`).

**Impact.** The most common first attempt at a custom date format (an implicit codec) compiles and produces the default format, so users conclude the feature is missing (see zio-json issue #1036 for how often this is asked). After finding `withInstance`, the convenience methods from the getting-started tutorial quietly bypass it.

**Suggested fix.** Both behaviours follow from the design, so the cheapest fix is documentation: state both points in the Scaladoc of `syntax.toJsonString`/`fromJson` and `Deriver#withInstance` (the H-03 guide and `codec.md` now do). A library-level option is a `toJsonString(codec)`/`fromJson(codec)` overload, or a way to attach a configured deriver to a `Schema` so that `Schema#jsonCodec` uses it.
