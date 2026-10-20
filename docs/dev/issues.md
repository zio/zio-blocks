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

## config: Rollout buckets depend on the key alone, so every percentage rollout picks the same users

Found 2026-10-20 while writing the how-to guide `docs/guides/roll-out-feature-gradually.md` (backlog H-32), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`.

**Summary.** `DynamicFlag` buckets a call with `Rollout.bucketFor(key)`, which hashes only the key. The flag's name is not part of the hash. Two features that are both rolled out to 10% of users, keyed by the same user id, reach exactly the same users. A user in a low bucket gets every early rollout at once, and a bug report from that group cannot be attributed to one feature. Rollout tools hash the flag key together with the user key so that rollouts are independent.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config` 0.0.56, run with scala-cli):

```scala
import zio.blocks.config._

object RecommendationsPanel extends DynamicFlag[Boolean](false, "true@*/10%; false")
object WishlistSharing      extends DynamicFlag[Boolean](false, "true@*/10%; false")

@main def repro(): Unit = {
  val readers  = (1 to 1000).map(i => s"reader-$i")
  val panel    = readers.filter(RecommendationsPanel.evaluate(_))
  val wishlist = readers.filter(WishlistSharing.evaluate(_))
  println(s"${panel.size} ${wishlist.size} ${panel.intersect(wishlist).size}")
  // 97 97 97
}
```

**Expected.** About 10% of each rollout's users are also in the other one (roughly 10 of 97), because the two decisions are independent.

**Root cause.** `Rollout.bucketFor` is `math.abs(MurmurHash3.stringHash(key) % 100)` (`config/shared/src/main/scala/zio/blocks/config/Rollout.scala:93-94`), and `DynamicFlag#evaluateInternal` passes the raw key (`DynamicFlag.scala:146`).

**Impact.** Rollouts are correlated across all flags in a process. Users must salt the key by hand (`flag(s"recommendations:$id")`, which the guide shows), and must use the same salt at every call site of the flag.

**Test gap.** `RolloutSpec` checks that `bucketFor` is stable and spread out, but nothing checks that two flags bucket the same key independently.

**Suggested fix.** Bucket on `name + ":" + key` inside `DynamicFlag` (keep `Rollout.bucketFor(key)` as is for direct use), or add an optional salt parameter that defaults to the flag name. This moves existing users between buckets, so mention it in the release notes.

## config: `Rollout.validate` assumes percentages are slices, but evaluation treats them as thresholds

Found 2026-10-20 while writing the how-to guide `docs/guides/roll-out-feature-gradually.md` (backlog H-32), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`.

**Summary.** Each targeted choice matches when `bucket < percentage`, and the first match wins, so percentages on one pattern are upper bounds on the same 0–99 bucket, not shares that add up. `Rollout.validate` instead sums the percentages per pattern and warns when the sum exceeds 100%. Under the real semantics this warning fires on a correct split and stays silent on a choice that can never match.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config` 0.0.56, run with scala-cli):

```scala
import zio.blocks.config._

def share(expr: String, value: String): Int = {
  val choices = Rollout.parseChoices(expr).toOption.get
  (0 until 100).count(b => Rollout.evaluateIndex(choices, "k", b).contains(value))
}

@main def repro(): Unit = {
  val split = "a@*/40%; b@*/80%; c"
  println(s"$split -> ${Rollout.validate(split)}  a=${share(split, "a")} b=${share(split, "b")} c=${share(split, "c")}")
  // a@*/40%; b@*/80%; c -> Right(List(Cumulative percentage for pattern '*' is 120% (exceeds 100%)))  a=40 b=40 c=20

  val shadow = "a@*/30%; b@*/20%; c"
  println(s"$shadow -> ${Rollout.validate(shadow)}  a=${share(shadow, "a")} b=${share(shadow, "b")} c=${share(shadow, "c")}")
  // a@*/30%; b@*/20%; c -> Right(List())  a=30 b=0 c=70
}
```

**Expected.** No warning for `a@*/40%; b@*/80%; c` (a valid 40/40/20 split). A warning for `a@*/30%; b@*/20%; c`, whose `b` can never match because every bucket below 20 already matched `a`.

**Root cause.** `Rollout.validate` folds the percentages of all targeted choices with the same pattern into a sum (`config/shared/src/main/scala/zio/blocks/config/Rollout.scala:104-110`), while `Rollout.matches` compares each choice independently with `bucket < pct.get` (`Rollout.scala:187`).

**Impact.** Operators who follow the warning rewrite correct splits into wrong ones, and real dead rules pass validation. `docs/reference/config/rollout.md` repeats the slice reading ("a percentage behaves as the literal share of keys it names"; "a pattern whose percentages sum above 100 ... usually means someone added a rule instead of adjusting one"), which holds only for the first choice on a pattern.

**Test gap.** `RolloutSpec` asserts the sum-based warning for `a@path/60%; b@path/60%` (`config/shared/src/test/scala/zio/blocks/config/RolloutSpec.scala:211`) but never compares a warning with what `evaluateIndex` actually returns.

**Suggested fix.** Pick one meaning. Either keep thresholds and make `validate` warn when a choice's percentage is less than or equal to an earlier choice's on the same pattern (it is shadowed), dropping the sum check; or switch evaluation to slices (each choice takes the next `n` buckets) so the sum check is correct. Update `rollout.md` to match.

## config: `Flag.dump` prints `StaticFlag[String]` values even when the flag's name marks it as sensitive

Found 2026-10-20 while writing the how-to guide `docs/guides/roll-out-feature-gradually.md` (backlog H-32), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`.

**Summary.** `Flag.dump()` masks a value only when the flag's type is `Secret`. A `StaticFlag[String]` named `PaymentToken`, `DbPassword` or `ApiKey` prints its value in clear, although the module already has a name-based check (`Sensitive.isSensitive`) and uses it to hide the same value in `FlagValueParseException` messages and in the config provenance dump.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config` 0.0.56, run with scala-cli; the flag is a top-level object, so its name is `PaymentToken`):

```scala
import zio.blocks.config._

object PaymentToken extends StaticFlag[String]("none")
object FreeShipping extends StaticFlag[Int](35)

@main def repro(): Unit = {
  FlagSource.Registry.register(FlagSource.fromMap(Map("PaymentToken" -> "tok-live-123"), "ops"))
  PaymentToken()
  FreeShipping()
  println(Flag.dump())
  // ┌──────────────┬──────────────────────┬──────────────┬────────────────┐
  // │ Name         │ Type                 │ Value        │ Source         │
  // ├──────────────┼──────────────────────┼──────────────┼────────────────┤
  // │ FreeShipping │ Default              │ 35           │ schema-default │
  // │ PaymentToken │ FlagSourceValue(ops) │ tok-live-123 │ ops            │
  // └──────────────┴──────────────────────┴──────────────┴────────────────┘
}
```

**Expected.** `PaymentToken` shows `<secret>`, as a parse failure for the same flag would (`FlagException.FlagValueParseException` prints `'<secret>'` for sensitive names).

**Root cause.** `Flag.dump` takes `sf.displayValue` as is (`config/shared/src/main/scala/zio/blocks/config/FlagReader.scala:182`); only `FlagException.FlagValueParseException#getMessage` consults `Sensitive.isSensitive(flagName)` (`FlagException.scala:67`).

**Impact.** Startup reports, which are usually logged, can leak credentials kept in string flags.

**Related (minor).** In the same table, a static flag that fell back to its default shows the source `schema-default` (`Provenance.Default.sourceId`, `Provenance.scala:43-45`), although no schema is involved; `default` would be clearer.

**Test gap.** `FlagDumpSpec` checks masking only for a `StaticFlag[Secret]`.

**Suggested fix.** In `Flag.dump`, render `<secret>` when `Sensitive.isSensitive(name)` holds, whatever the type, and apply the same rule to a dynamic flag's expression.

## config: `DynamicFlag` accepts expressions whose values its reader cannot parse, and a selector without `%` silently becomes a path

Found 2026-10-20 while writing the how-to guide `docs/guides/roll-out-feature-gradually.md` (backlog H-32), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`.

**Summary.** `DynamicFlag#update`, `reload` and the initial expression check only the rollout grammar. Two typos therefore pass with `Right(())` and switch the feature off for everyone. First, `maybe` for a `DynamicFlag[Boolean]` is accepted; every call then falls back to the default, and only `parseErrorCount` shows it. Second, `true@*/50` (missing `%`) parses as the two-segment path `*` / `50`, which never matches a one-segment key.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config` 0.0.56, run with scala-cli):

```scala
import zio.blocks.config._

object RecommendationsPanel extends DynamicFlag[Boolean](false, "true@*/10%; false")

@main def repro(): Unit = {
  val readers = (1 to 1000).map(i => s"reader-$i")
  println(RecommendationsPanel.update("maybe"))
  // Right(())
  println(s"${RecommendationsPanel("reader-1")} parseErrors=${RecommendationsPanel.parseErrorCount}")
  // false parseErrors=1
  println(RecommendationsPanel.update("true@*/50"))
  // Right(())
  println(s"${readers.count(RecommendationsPanel.evaluate(_))} of 1000")
  // 0 of 1000
}
```

**Expected.** `update("maybe")` returns `Left(ConfigError.InvalidValue(..., "Boolean ..."))`, because the flag already holds the `Flag.Reader` that will reject the value. `true@*/50` is at least reported by `Rollout.validate` as a likely missing `%`.

**Root cause.** `update` and `reload` call `Rollout.parseChoices` only (`config/shared/src/main/scala/zio/blocks/config/DynamicFlag.scala:82-93`, `:96-113`); values are parsed lazily per call in `evaluateInternal`, which swallows the error into `parseErrorCount`. `Rollout.parseSelector` treats a selector that does not end in `%` as a plain path (`Rollout.scala:168-169`).

**Impact.** A mistyped push to a flag service is accepted, recorded in `updateHistory` as a successful change, and turns the feature off without an error.

**Test gap.** `DynamicFlagSpec` checks `parseErrorCount` after an unparsable value but does not expect `update` to reject it.

**Suggested fix.** In `update`, `reload` and initialization, parse every choice's value with the flag's reader and return the first failure as `Left` (or `Flag.ReloadResult.Failed`). In `Rollout.validate`, warn when a selector's last segment is all digits.

**Related (documentation).** The `DynamicFlag` Scaladoc example `DynamicFlag[Boolean](false, "true@beta/50%; false")` (`DynamicFlag.scala:32`) matches only the key `beta`, because the key is the first path segment; `true@*/50%; false` is what the example means.

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

## `Resource.from` never reports a dependency cycle (E10); the macro crashes instead

Filed: [zio/zio-blocks#1767](https://github.com/zio/zio-blocks/issues/1767).

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31), on `main` at `64b2f666`. Reproduced against the published `0.0.56`.

**Summary.** `ErrorMessages.renderDependencyCycle` (E10) draws a cycle diagram, but `Resource.from` can never reach it. A constructor cycle crashes the macro with `java.util.NoSuchElementException: key not found` and a long compiler stack trace instead.

**Reproduction** (`scala-cli run .`; Scala 3.9.0, `zio-blocks-next-scope` 0.0.56):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-scope:0.0.56
//> using options -experimental

import zio.blocks.scope.*

class InventoryRepo(prices: PriceService)
class PriceService(inventory: InventoryRepo)
class Catalog(inventory: InventoryRepo)

@main def cycle(): Unit = {
  val catalog = Resource.from[Catalog]()
  println(catalog)
}
// -- Error: Cycle.scala:12:38
// 12 |  val catalog = Resource.from[Catalog]()
//    |                ^^^^^^^^^^^^^^^^^^^^^^^^
//    |Exception occurred while executing macro expansion.
//    |java.util.NoSuchElementException: key not found: InventoryRepo
//    |	at scala.collection.immutable.Map$EmptyMap$.apply(Map.scala:270)
//    |	at scala.collection.immutable.Map$EmptyMap$.apply(Map.scala:270)
//    |	at zio.blocks.scope.ResourceMacros$.buildDepAcquisition$1(ResourceMacros.scala:515)
//    |	at zio.blocks.scope.ResourceMacros$.createResourceExpr$1$$anonfun$5$$anonfun$1(ResourceMacros.scala:529)
//    |	... (about 40 more compiler frames)
```

**Expected.** The E10 error: `Dependency cycle detected`, with a diagram of `InventoryRepo ──► PriceService` and the hints "Introducing an interface/trait", "Using lazy initialization" and "Restructuring dependencies".

**Root cause.** `resolveWire` runs two checks in this order: `if (aliasMap.contains(requiredKey)) return`, then `if (chain.contains(requiredKey)) abortDependencyCycle(...)`. It records a type in `aliasMap` *before* it recurses into that type's dependencies, so every key on `chain` is already in `aliasMap`, and the early return always wins. The cycle check is dead code. Resolution then "succeeds" with a cyclic wire map, the topological sort visits each node once, and code generation looks up a dependency value that was never produced. Both macros have the bug: Scala 3 `scope/shared/src/main/scala-3/zio/blocks/scope/ResourceMacros.scala:248-259`, Scala 2 `scope/shared/src/main/scala-2/zio/blocks/scope/ResourceVersionSpecific.scala:350-361`.

**Impact.** Every dependency cycle, the most common wiring mistake, produces an unreadable macro crash. `scope-examples/.../CircularDependencyDemoExample.scala`, `docs/reference/resource-management/wire.md` and `index.md` all promise compile-time cycle detection.

**Test gap.** No test triggers E10 through the macro. `ErrorMessagesSpec` only tests the string renderer.

**Suggested fix.** Run the cycle check before the `aliasMap` early return, in both macros. Diamonds still work, because a shared dependency reached through two branches is never on the current `chain`. Add a `typeCheck` test to `WireMacroErrorSpec`.

## A dependency on a Java class with constructor parameters crashes the `Resource.from` / `Wire.shared` macro

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31), on `main` at `64b2f666`. Reproduced against the published `0.0.56`. The same crash happens for `Wire.shared[java.util.ArrayList[String]]` (seen in mdoc).

**Summary.** When `Resource.from` has to auto-create a Java class whose constructors take parameters, or `Wire.shared` derives one, the macro throws an internal compiler `AssertionError` instead of reporting a Scope error.

**Reproduction** (`scala-cli run .`; Scala 3.9.0, `zio-blocks-next-scope` 0.0.56):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-scope:0.0.56
//> using options -experimental

import zio.blocks.scope.*

class Catalog(titles: java.util.ArrayList[String])

@main def javaDep(): Unit = {
  val catalog = Resource.from[Catalog]()
  println(catalog)
}
// -- Error: JavaDep.scala:10:38
// 10 |  val catalog = Resource.from[Catalog]()
//    |                ^^^^^^^^^^^^^^^^^^^^^^^^
//    |Exception occurred while executing macro expansion.
//    |java.lang.AssertionError: assertion failed: TypeBounds(TypeRef(ThisType(TypeRef(NoPrefix,module class scala)),class Nothing),TypeRef(ThisType(TypeRef(NoPrefix,module class <special-ops>)),type <FromJavaObject>)) in in intersection  & Int
//    |	at scala.runtime.Scala3RunTime$.assertFailed(Scala3RunTime.scala:10)
//    |	at dotty.tools.dotc.core.Types$.expectValueTypeOrWildcard(Types.scala:3777)
//    |	at dotty.tools.dotc.core.Types$AndType$.apply(Types.scala:3587)
//    |	at scala.quoted.runtime.impl.QuotesImpl$reflect$AndType$.apply(QuotesImpl.scala:2175)
//    |	at scala.quoted.runtime.impl.QuotesImpl$reflect$AndType$.apply(QuotesImpl.scala:2175)
//    |	at zio.blocks.scope.internal.MacroCore$.computeInType$$anonfun$1(MacroCore.scala:101)
//    |	at scala.collection.AbstractIterable.reduceLeftOption(Iterable.scala:979)
//    |	at zio.blocks.scope.internal.MacroCore$.computeInType(MacroCore.scala:101)
//    |	at zio.blocks.scope.internal.WireCodeGen$.deriveWire(WireCodeGen.scala:78)
//    |	at zio.blocks.scope.ResourceMacros$.autoCreateWire$1(ResourceMacros.scala:236)
//    |	... (more frames)
```

**Expected.** A Scope error that says the Java class can't be derived and that you should provide its wire, for example `Wire(new java.util.ArrayList[String]())`.

**Root cause** (from the stack trace and source, not debugged further). `WireCodeGen.deriveWire` takes the parameter types of the class's "primary constructor" (`WireCodeGen.scala:66-70`). For a Java class, Scala 3 hands back constructor parameters whose types are not value types: here a `TypeBounds` built from Java's `Object` (`<FromJavaObject>`). `MacroCore.computeInType` (`scala-3/.../internal/MacroCore.scala:101`) passes every dependency type to `AndType(_, _)`, which asserts that its arguments are value types.

**Impact.** Any graph that contains a JDK or other Java class with constructor parameters fails with a compiler crash that doesn't name the cause.

**Test gap.** No test derives a wire for a Java-defined class.

**Suggested fix.** In `deriveWire` and `autoCreateWire`, detect Java-defined classes (`sym.flags.is(Flags.JavaDefined)`) and abort with a Scope error asking for an explicit wire. Alternatively, reject any dependency type that is not a value type before calling `AndType`.

## Duplicate constructor parameter types (E12) are never reported in Scala 3; both parameters get the same instance

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31), on `main` at `64b2f666`. Reproduced against the published `0.0.56`.

**Summary.** `ErrorMessages.renderDuplicateParamType` (E12) says a constructor can't take two parameters of the same type, because the graph holds one value per type. In Scala 3 the check never fires: the code compiles, and both parameters silently receive the same instance.

**Reproduction** (`scala-cli run .`; Scala 3.9.0, `zio-blocks-next-scope` 0.0.56):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-scope:0.0.56
//> using options -experimental

import zio.blocks.scope.*

class Db
class Catalog(val primary: Db, val replica: Db)

@main def dupParam(): Unit = {
  val sameDb: Boolean = Scope.global.scoped { scope =>
    import scope.*
    val catalog = allocate(Resource.from[Catalog]())
    $(catalog)(c => c.primary.eq(c.replica))
  }
  println(s"primary eq replica: $sameDb")
}
// primary eq replica: true
```

`Wire.shared[Catalog]` also compiles (seen in mdoc).

**Expected.** E12: `Constructor of Catalog has multiple parameters of type Db`, with the hint to wrap one parameter in a distinct type.

**Root cause** (from source). `MacroCore.computeInType` builds `Db & Db` (`scala-3/.../internal/MacroCore.scala:99-102`). `MacroCore.flattenIntersection` calls `.dealias.simplified` on it (`:113`), which collapses `Db & Db` to `Db`. So the wire's `inTypes` is `List(Db)`, and the duplicate check in `ResourceMacros.scala:306-312` never sees two entries. `WireCodeGen.deriveWire` (used by `Wire.shared`) has no duplicate check at all. The Scala 2 macro was not checked.

**Impact.** A class that needs two different values of one type, such as a primary and a replica database, silently gets one instance in both places. The bug shows up only at run time.

**Test gap.** No test triggers E12 through the macro. `ErrorMessagesSpec` only tests the string renderer.

**Suggested fix.** Run the duplicate check on the constructor's parameter types before they are combined into an intersection (in `WireCodeGen.deriveWire` and in `ResourceMacros`), and abort with `abortDuplicateParamType`. Add `typeCheck` tests for `Wire.shared` and `Resource.from`.

## The E3 subtype-conflict hint suggests code that isn't valid Scala

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31), on `main` at `64b2f666`. Reproduced against the published `0.0.56`.

**Summary.** The hint of the E3 "Dependency type conflict" error builds a wrapper name by pasting `Wrapped` in front of the fully qualified type name. For any type in a package, the suggested `case class` and `opaque type` don't compile.

**Reproduction** (`scala-cli run .`; Scala 3.9.0, `zio-blocks-next-scope` 0.0.56):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-scope:0.0.56
//> using options -experimental

package com.example.bookstore

import zio.blocks.scope.*

class InventoryRepo
class SqlInventoryRepo extends InventoryRepo
class Catalog(inventory: InventoryRepo, sql: SqlInventoryRepo)

@main def subtype(): Unit = {
  val wire = Wire.shared[Catalog]
  println(wire)
}
// -- Error: Subtype.scala:14:24
// 14 |  val wire = Wire.shared[Catalog]
//    |             ^^^^^^^^^^^^^^^^^^^^
//    |── Scope Error ─────────────────────────────────────────────────────────────────
//    |
//    |  Dependency type conflict in com.example.bookstore.Catalog
//    |
//    |  com.example.bookstore.SqlInventoryRepo is a subtype of com.example.bookstore.InventoryRepo.
//    |
//    |  When both types are dependencies, Context cannot reliably distinguish
//    |  them. The more specific type may be retrieved when the more general
//    |  type is requested.
//    |
//    |  To fix this, wrap one or both types in a distinct wrapper:
//    |
//    |    case class Wrappedcom.example.bookstore.InventoryRepo(value: com.example.bookstore.InventoryRepo)
//    |    or
//    |    opaque type Wrappedcom.example.bookstore.InventoryRepo = com.example.bookstore.InventoryRepo
//    |
//    |────────────────────────────────────────────────────────────────────────────────
```

**Expected.** `case class WrappedInventoryRepo(value: com.example.bookstore.InventoryRepo)`, and the same for the `opaque type`.

**Root cause.** `ErrorMessages.renderSubtypeConflict` interpolates the full `supertype` string as the identifier: `s"case class Wrapped$supertype(value: $supertype)"` and `s"opaque type Wrapped$supertype = $supertype"` (`scope/shared/src/main/scala/zio/blocks/scope/internal/ErrorMessages.scala:311-313`). The macros pass `tpe.show` / `toString`, which is fully qualified. E12's hint (`:559-561`) has the same pattern with `paramType`.

**Impact.** Copying the hint gives a syntax error. The hint also suggests an `opaque type` to Scala 2 users.

**Test gap.** `ErrorMessagesSpec` tests the renderer only with unqualified names.

**Suggested fix.** Build the identifier from the simple name (the text after the last `.`, with type arguments removed) and keep the full name only on the right-hand side. Show the `opaque type` alternative only in Scala 3.

## Four Scope error codes can't be reached, and `renderMissingDependency` is dead code

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31), on `main` at `64b2f666`. Reproduced against the published `0.0.56`.

**Summary.** `ErrorMessages` defines messages that user code can't trigger, or can trigger only with nonsense input:

- **E5 (unsupported implicit parameter)** sits behind the E4 check in both macros, so an implicit `Clock` parameter is reported as an ordinary E4 dependency.
- **E2 (no primary constructor) and E9 (no constructor for auto-create)** need a class without a primary constructor. Every Scala class has one, and Java classes either compile or crash the macro (see the Java-class entry above).
- **E6 (cannot extract wire types)** fires only for an argument whose static type isn't `Wire[In, Out]`, which in practice means `???` or `null`.
- **`renderMissingDependency`** (`ErrorMessages.scala:687`), the most elaborate renderer (resolution stack, dependency tree, hints), is never called by either macro. Only `ErrorMessagesSpec` calls it. Missing dependencies are auto-created instead, or fail as E7/E8.

**Reproduction** (`scala-cli run .` per file; Scala 3.9.0, `zio-blocks-next-scope` 0.0.56; each file starts with `//> using scala 3.9.0`, `//> using dep dev.zio::zio-blocks-next-scope:0.0.56` and `//> using options -experimental`):

```scala
// E5.scala: expected E5, got E4
import zio.blocks.scope.*

class Clock
class Catalog()(using Finalizer, Clock)

@main def e5(): Unit = println(Resource.from[Catalog])
// -- Error: E5.scala:10:44
//    |── Scope Error ─────────────────────────────────────────────────────────────────
//    |
//    |  Resource.from[Catalog] cannot be derived.
//    |
//    |  Catalog has dependencies that must be provided:
//    |    • Clock
```

```scala
// E2.scala: no E2 or E9, both lines compile
import zio.blocks.scope.*

class Catalog(owner: java.lang.Object)

@main def e2(): Unit = {
  println(Resource.from[java.lang.Object])
  println(Resource.from[Catalog]())
  println("both compiled")
}
// zio.blocks.scope.Resource$Shared@34340fab
// zio.blocks.scope.Resource$Shared@2eee9593
// both compiled
```

```scala
// E6.scala: the only trigger found for E6
import zio.blocks.scope.*

class Catalog

@main def e6(): Unit = println(Resource.from[Catalog](???))
// -- Error: E6.scala:9:53
//    |── Scope Error ─────────────────────────────────────────────────────────────────
//    |
//    |  Cannot extract types from wire expression.
//    |
//    |  Wire type: scala.Nothing
```

**Expected.** Each defined error is reachable by the mistake it describes, or is removed.

**Root cause** (from source). E5: `ResourceMacros.deriveResourceImpl` aborts with E4 (`scala-3/.../ResourceMacros.scala:75-77`) for any non-`Finalizer` parameter before it builds the constructor call where E5 lives (`:99`). Scala 2 has the same order (`scala-2/.../ResourceVersionSpecific.scala:111-112` before `:129-134`). E2/E9: the `primaryConstructor == Symbol.noSymbol` checks never hold for a class definition. `renderMissingDependency` has no call site in `scope/shared/src/main`.

**Impact.** The error catalog and `ErrorMessagesSpec` suggest coverage that the macros don't have. The best-designed message, missing dependency, is never shown.

**Test gap.** `ErrorMessagesSpec` tests the renderers in isolation, so dead renderers stay green.

**Suggested fix.** Either wire E5 into the dependency loop (check implicit parameter lists before E4) and call `renderMissingDependency` where auto-creation is not wanted, or delete the unreachable renderers and their tests. Add `typeCheck` tests through the macros for each code that stays.

## A failing `scoped` body adds a confusing "Ambiguous given instances" error

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31), on `main` at `64b2f666`. Reproduced against the published `0.0.56`.

**Summary.** When the last expression of a `scoped` block fails to type-check, the compiler can't infer the block's result type, and the `Unscoped[A]` search for that type then fails with a second error that names two unrelated built-in instances. The real mistake (calling a method directly on `$[Db]`) is the first error, but the second one points the reader at `Unscoped` instances for `Int` and `FiniteDuration`.

**Reproduction** (`scala-cli run .`; Scala 3.9.0, `zio-blocks-next-scope` 0.0.56):

```scala
//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-scope:0.0.56
//> using options -experimental

import zio.blocks.scope.*

class Db extends AutoCloseable {
  def countBooks(): Int = 42
  def close(): Unit     = ()
}

@main def ambig(): Unit = {
  val n = Scope.global.scoped { scope =>
    import scope.*
    val db = allocate(Resource(new Db))
    db.countBooks()
  }
  println(n)
}
// -- [E008] Not Found Error: Ambig.scala:16:7
// 16 |    db.countBooks()
//    |    ^^^^^^^^^^^^^
//    |    value countBooks is not a member of scope.$[Db]
// -- [E172] Type Error: Ambig.scala:17:3
// 17 |  }
//    |   ^
//    |Ambiguous given instances: both given instance given_Unscoped_FiniteDuration in object Unscoped and given instance given_Unscoped_Int in object Unscoped match type zio.blocks.scope.Unscoped[A] of parameter ev of method scoped in trait ScopeVersionSpecific
// 2 errors found
```

**Expected.** Only the first error.

**Root cause** (from the output). `scoped[A](f: ... => A)(using ev: Unscoped[A])`: when `f`'s body is an error, `A` stays uninstantiated, and implicit search for `Unscoped[A]` finds several candidates (`scala-3/.../ScopeVersionSpecific.scala:55`). In mdoc, with `A` inferred as `Any`, the message instead names `given_Unscoped_BigInt` and `given_Unscoped_FiniteDuration`.

**Impact.** A reader fixing the second error first goes looking for an `Unscoped` problem that doesn't exist. This is a usability trap rather than a wrong result.

**Suggested fix.** Hard to avoid fully, because the compiler drives this. Consider `@implicitNotFound` / `@implicitAmbiguous` messages on `Unscoped` that say "if another error is reported inside the block, fix that first". `@implicitAmbiguous` on the low-priority instances would also make the real escape error (no instance) read better.

## Scope tutorial, reference pages and example contradict the source

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31), on `main` at `64b2f666`. Reproduced against the published `0.0.56`. Listed here and not fixed in that PR.

**Wrong statements:**

- `docs/guides/compile-time-resource-safety-with-scope.md:726` says `defer` throws `IllegalStateException` on a closed scope. `defer` returns a no-op `DeferHandle` and ignores the finalizer (`Scope.scala:202-211`).
- The same line quotes the exception as `Scope is closed`. The real messages are `Cannot allocate resource: scope is already closed.` (allocate), `Cannot open child scope: scope is already closed.` (open) and `Cannot access scoped value: scope is already closed.` (`$`).
- The same file at `:820` says `Wire.shared[Database]`, where `Database(url: String)`, fails with "no Wire[String] for url". It compiles. The error only appears later, from `Resource.from`, as E7 `Cannot auto-create java.lang.String`.
- The same file uses the fence `scala mdoc:compile-only mdoc:silent:reset` at `:27` and `:728`. That fence mixes two modifier lists, `compile-only` and `silent:reset`, so it isn't clear which one the author meant mdoc to apply.
- `docs/reference/resource-management/scope.md:943` quotes `Cannot acquire resource: scope has already been closed.` The real text is `Cannot allocate resource: scope is already closed.`
- `docs/reference/resource-management/wire.md:85` and `:506`, and `index.md:18`, promise compile-time cycle detection and "missing dependency" errors. Cycles crash the macro (#1767), and `renderMissingDependency` is never called (see the entry above).
- `scope-examples/src/main/scala/scope/examples/CircularDependencyDemoExample.scala:28` and `:41` say that uncommenting the cycle "would produce a compile error" with a cycle diagram. It crashes the macro instead. `scope.md:1258` presents that example as cycle detection.

**Reproduction** (`scala-cli run .` per file; same header as above):

```scala
// Closed.scala: defer is a no-op, and the allocate message
import zio.blocks.scope.*

class Db extends AutoCloseable { def close(): Unit = () }

@main def closed(): Unit = {
  val shop  = Scope.global.open()
  val scope = shop.scope
  shop.close()
  val handle = scope.defer(println("never runs"))
  println(s"defer on a closed scope returned: $handle")
  try scope.allocate(Resource(new Db))
  catch { case e: IllegalStateException => println(e.getMessage.linesIterator.take(3).mkString("\n")) }
}
// defer on a closed scope returned: zio.blocks.scope.DeferHandle$Noop$@2f7a2457
// ── Scope Error ─────────────────────────────────────────────────────────────────
//
//   Cannot allocate resource: scope is already closed.
```

```scala
// WireDb.scala: Wire.shared[Database] compiles
import zio.blocks.scope.*

case class Database(url: String) extends AutoCloseable {
  override def close(): Unit = ()
}

@main def wireDb(): Unit = {
  val dbWire = Wire.shared[Database]
  println(s"Wire.shared[Database] compiled: $dbWire")
}
// Wire.shared[Database] compiled: Shared(WireDb$package$$$Lambda/0x000070e180002900@45c8e616)
```

**Suggested fix.** Correct each statement to match the source, and point section 12 of the tutorial and the error sections of `scope.md` at `docs/guides/fix-scope-and-wire-compile-errors.md` instead of keeping a second list of messages. Fix the example's comment, and the cycle claims, once #1767 is fixed.

## mdoc: `mdoc:fail` and `mdoc:warn` also compile earlier `compile-only` blocks

Found 2026-10-20 while writing the how-to guide `docs/guides/fix-scope-and-wire-compile-errors.md` (backlog H-31). This is docs tooling (mdoc), not a library bug. Reproduced with this repo's `docs/mdoc`.

**Summary.** A `mdoc:fail` or `mdoc:warn` block is checked by compiling a program made of every earlier evaluated block *plus every earlier `compile-only` block*, pasted in without their own scope. A `compile-only` fix that redefines a name from the same section compiles fine on its own, but a later `mdoc:warn` then fails with no reason given. Later `mdoc:fail` blocks can fail for the clash instead of their intended error.

**Reproduction** (`sbt "++3.9.0; docs/mdoc --in docs/guides/zz-trap.md --out /tmp/zz-trap.md"`):

````markdown
```scala mdoc:silent:reset
class Book(val title: String)
```

```scala mdoc:compile-only
final case class Book(title: String)
```

```scala mdoc:warn
val two = { 1; 2 }
```
````

```
error: zz-trap.md:15:1: Expected compile warnings but program failed to compile
val two = { 1; 2 }
^^^^^^^^^^^^^^^^^^
info: Compiled in 17.99s (1 error)
```

Changing the second fence to `scala mdoc:silent:reset` makes the run pass.

**Root cause.** mdoc's fail/warn instrumentation builds one program from the earlier sections and inlines `compile-only` code into its section's class, so the two `Book` definitions clash. Run `docs/mdoc --verbose` to see it: the hidden program is the `FailSection("...")` string in the instrumented output.

**Workaround.** Start any fix block that redefines a name with `mdoc:silent:reset`, not `compile-only`. Always read the rendered `// error:` lines of every `mdoc:fail` block.
## `Table#createTable` declares no `PRIMARY KEY`, so derived tables accept duplicate IDs and reject `Repo#insertOrUpdate`

Found 2026-10-20 while writing the how-to guide `docs/guides/test-database-code-against-sqlite.md` (backlog H-47), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. This is a usability trap rather than a crash: `docs/reference/sql/repo.md`, `table.md` and `ddl.md` already document it, but nothing in the API steers users away from it.

**Summary.** `Table.derived[A].createTable(dialect)` renders every column as `name type [NOT NULL]` and never adds `PRIMARY KEY`, even when the table backs a `Repo` that knows its ID column. A table created this way silently stores two rows with the same ID, and `Repo#insertOrUpdate` / `Repo#insertOrUpdateBatch` fail on it, because their `ON CONFLICT (id)` clause needs a primary key or unique constraint. Tests suffer most: a test that checks that a duplicate insert is rejected can never pass on a derived schema, so users have to hand-write the DDL and keep it in sync with the case class.

**Reproduction** (Verified; Scala 3.9.0, `zio-blocks-next-sql` 0.0.56, `sqlite-jdbc` 3.53.4.0):

```scala
import java.nio.file.Files
import scala.util.Try
import zio.blocks.schema.Schema
import zio.blocks.sql.*

case class Book(id: Long, title: String, stock: Int)
object Book { given Schema[Book] = Schema.derived }

val books = Repo.derived[Book, Long]
val file  = Files.createTempFile("pk-repro", ".db")
val xa    = JdbcTransactor.fromUrl(s"jdbc:sqlite:$file", SqlDialect.SQLite)

xa.connect {
  Table.derived[Book].createTable(SqlDialect.SQLite).update
  books.insert(Book(1L, "Zionomicon", 5))
  books.insert(Book(1L, "Zionomicon", 5))
  println(books.count)
  // 2
  println(Try(books.insertOrUpdate(Book(1L, "Zionomicon", 7))))
  // Failure(org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (ON CONFLICT clause does not match any PRIMARY KEY or UNIQUE constraint))
}
```

**Expected.** A table created for a `Repo` declares its ID column as the primary key, so the second insert fails with `SQLITE_CONSTRAINT_PRIMARYKEY` and `insertOrUpdate` updates the row.

**Root cause.** `Ddl.createTable` (`sql/shared/src/main/scala/zio/blocks/sql/Ddl.scala:38-47`) only knows column names, types and nullability; `ColumnDef` has no key flag, and `Table#createTable` (`sql/shared/src/main/scala/zio/blocks/sql/Table.scala:54-60`) has no way to pass one. The ID column is known only to `Repo` (`Repo#validatedIdColumn`), which has no DDL method of its own.

**Suggested fix.** Add a `primaryKey: Option[String]` (or a `ColumnDef#primaryKey` flag) to `Ddl.createTable` and an overload `Table#createTable(dialect, primaryKey: String)`, plus a `Repo#createTable(dialect)` that passes its ID column. Keep the current output for callers that pass nothing. Add a `RepoUpsertSpec` test that runs `insertOrUpdate` on a table created by `Repo#createTable`.

**Related (testing ergonomics).** `JdbcTransactor` has no way to hold one connection open across blocks, so `jdbc:sqlite::memory:` loses its tables between `connect`/`transact` calls, and there is no rollback-only `transact` for tests. The repo's own `RepoIntegrationSpec.withFreshDb` (`sql/jvm/src/test/scala/zio/blocks/sql/RepoIntegrationSpec.scala:58-84`) works around the first by subclassing `JdbcTransactor` and overriding `connect` with the `private[sql]` `JdbcConnection`, which users can't copy; its `transact` (not overridden) would close the shared connection. The H-47 guide uses a temporary file per test and a sentinel-exception `rollingBack` helper instead. A public single-connection transactor for tests (whose `close` is a no-op until the test ends) would make both unnecessary.
## `YamlReader` accepts unterminated flow collections and quoted strings, and drops their last character

Found 2026-10-20 while writing the migration guide `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `zio.blocks.schema.yaml.YamlReader` does not check that a flow sequence (`[...`), a flow mapping (`{...`) or a quoted scalar (`"...`, `'...`) is closed. An unterminated one parses without an error, and the reader strips the closing delimiter that isn't there, so the last real character of the value is lost. A typo in a YAML file therefore loads as different data instead of failing: a database port of `5432` becomes `543`. Everything built on `YamlReader` is affected, including `ConfigSource.fromYaml` in `config-yaml`.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config-yaml` 0.0.56, which brings in `schema-yaml`):

```scala
import zio.blocks.schema.yaml.YamlReader
import zio.blocks.config._
import zio.blocks.config.yaml._

println(YamlReader.read("tags: [scala, zio"))
// Mapping(Chunk((Scalar(tags,None),Sequence(Chunk(Scalar(scala,None),Scalar(zi,None))))))

println(YamlReader.read("title: \"Zionomicon"))
// Mapping(Chunk((Scalar(title,None),Scalar(Zionomico,None))))

println(YamlReader.read("title: 'Zionomicon"))
// Mapping(Chunk((Scalar(title,None),Scalar(Zionomico,None))))

println(YamlReader.read("db: {host: localhost, port: 5432"))
// Mapping(Chunk((Scalar(db,None),Mapping(Chunk((Scalar(host,None),Scalar(localhost,None)),(Scalar(port,None),Scalar(543,None)))))))

println(YamlReader.read("tags:\n  [scala,\n   zio"))
// Mapping(Chunk((Scalar(tags,None),Sequence(Chunk(Scalar(scala,None),Scalar(zi,None))))))

println(ConfigSource.fromYaml("tags: [scala", "bookstore.yaml"))
// Right(MapSource(Map(tags.0 -> scal),bookstore.yaml))
```

**Expected.** Each call fails with a parse error that names the unclosed `[`, `{` or quote (for `ConfigSource.fromYaml`, `Left(ConfigError.ParseError(...))`). No value is changed.

**Root cause.** In `schema-yaml/shared/src/main/scala/zio/blocks/schema/yaml/YamlReader.scala`:

- `parseFlowMappingInline` (`:459`) and `parseFlowSequenceInline` (`:483`) take `s.substring(1, s.length - 1)` without checking that the last character is `}` or `]`.
- `unquoteSingle` (`:522`) and `unquoteDouble` (`:525`) do the same for the closing quote.
- For a flow collection that starts on its own line, `collectFlowContent` (`:381`) keeps reading lines while `depth > 0 && lineIdx < lines.length` (`:393`) and simply returns when the input runs out, still unbalanced. `parseFlowMapping` (`:333`) and `parseFlowSequence` (`:364`) then strip the last character the same way.

**Impact.** Silent data corruption. A missing `]`, `}` or quote, which is an easy typo, changes the value that is loaded rather than being reported, and the changed value often still passes type checks (`543` is a valid port).

**Test gap.** `YamlReaderSpec` has no test with an unterminated flow collection or quoted scalar.

**Suggested fix.** Before stripping delimiters, check that the value ends with the matching closing character (taking quotes into account), and fail with a parse error otherwise. Make `collectFlowContent` fail when it reaches the end of input with `depth > 0`. Add `YamlReaderSpec` tests for each unterminated form, including the multi-line flow sequence.

## config-hocon: array concatenation `[a] [b]` becomes an empty string

Found 2026-10-20 while writing the migration guide `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** HOCON lets two arrays written next to each other concatenate into one list (`tags = [scala] [zio]` is `[scala, zio]` in the HOCON spec and in Typesafe Config). `config-hocon` accepts the syntax but turns the value into the empty string `""`. There is no error, so a `List[String]` field loads as one empty element or fails far from the cause.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config-hocon` 0.0.56):

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._

def keys(doc: String): Unit =
  ConfigSource.fromHocon(doc) match {
    case Right(source) => println(source.all("").map { case (k, v) => s"$k = '${v.value}'" }.toList.sorted)
    case Left(error)   => println(error.message)
  }

keys("tags = [scala] [zio]")
// List(tags = '')

keys("tags = [scala, zio]")
// List(tags.0 = 'scala', tags.1 = 'zio')
```

**Expected.** `tags = [scala] [zio]` produces `tags.0 = 'scala'` and `tags.1 = 'zio'`, like the single array. If concatenation is not going to be supported, the parser should report an error instead.

**Root cause.** Adjacent values are parsed into an `MConcat`, and `resolveSubstitutions` turns every `MConcat` into a string by appending `hoconValueToString` of each part (`config-hocon/shared/src/main/scala/zio/blocks/config/hocon/HoconParser.scala:634-645`). `hoconValueToString` returns `""` for arrays and objects (`:652`, with the comment "objects/arrays don't stringify well in concat"). Object concatenation goes through the same code path: `db = {host: a} {port: 1}` produces the single key `db = ''`.

**Impact.** Silent data loss for a documented HOCON feature, often hit when an `application.conf` written for Typesafe Config is loaded with `config-hocon`.

**Test gap.** `HoconParserSpec` has no test for array or object concatenation.

**Suggested fix.** In the `MConcat` case, when every part resolves to an array, concatenate the arrays; when every part resolves to an object, merge them as HOCON does. Report a parse error when arrays or objects are mixed with strings. Add `HoconParserSpec` tests for both cases.

## config-hocon: `ConfigSource.fromHocon` silently drops every `include`

Found 2026-10-20 while writing the migration guide `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `ConfigSource.fromHocon` parses HOCON text with no way to resolve an `include`, and the parser silently skips an include it cannot resolve. Every `include` in a string is therefore dropped without an error, and an object whose only content is an include disappears. The public `ConfigSource` API has no way to pass an include resolver; only the JVM-only `ConfigSource.fromFile` resolves includes.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config-hocon` 0.0.56):

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._

def keys(doc: String): Unit =
  ConfigSource.fromHocon(doc) match {
    case Right(source) => println(source.all("").map { case (k, v) => s"$k = '${v.value}'" }.toList.sorted)
    case Left(error)   => println(error.message)
  }

keys("""db { include "db.conf" }, name = "Scala Bookstore"""")
// List(name = 'Scala Bookstore')

keys("""include "missing.conf", name = "Scala Bookstore"""")
// List(name = 'Scala Bookstore')
```

The only workaround is to bypass `ConfigSource.fromHocon` and call the parser directly:

```scala
val files = Map("db.conf" -> """host = "localhost"""")

val source = HoconParser
  .parse("""db { include "db.conf" }, name = "Scala Bookstore"""", files.get)
  .map(value => ConfigSource.fromMap(HoconValue.flatten(value), "application.conf"))

println(source.map(_.all("").keys.toList.sorted))
// Right(List(db.host, name))
```

**Expected.** Either `fromHocon` reports that an include cannot be resolved, or it accepts an include resolver so that callers on Scala.js, or with a classpath resource, can supply the included text.

**Root cause.** `ConfigSource.fromHocon` (`config-hocon/shared/src/main/scala/zio/blocks/config/hocon/ConfigSourceHoconSyntax.scala:23-24`) calls `HoconConfigSource.fromStringWithId`, which calls `HoconParser.parse(hocon)` with the default `includeCallback = _ => None` (`HoconParser.scala:42`). `handleInclude` treats a `None` as a missing include and ignores it on purpose (`HoconParser.scala:452`, tested by `HoconParserSpec` "silently ignores missing includes"). `HoconConfigSource.fromStringWithCallback` exists (`HoconConfigSource.scala:35`), but the whole object is `private[hocon]` (`:24`).

**Impact.** A configuration split across files loads with whole blocks missing and no error. Missing keys then show up as `MissingKey` errors (or as `None` for optional fields), far from the `include` that caused them.

**Test gap.** `HoconConfigSourceSpec` has no test with an `include`.

**Suggested fix.** Add a public overload, for example `ConfigSource.fromHocon(hocon, sourceId, includeResolver: String => Option[String])`. Also consider an option, or a different default, that makes an unresolved `include` an error. HOCON's `include required(...)` form, which `config-hocon` does not parse yet, is the standard way to say an include must exist.

## config-hocon: `${VAR}` and `${?VAR}` never fall back to environment variables

Found 2026-10-20 while writing the migration guide `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** The HOCON spec has a section on substitution fallback to environment variables, and Typesafe Config implements it: a `${VAR}` or `${?VAR}` that is not a path in the document is looked up as an environment variable. `config-hocon` resolves substitutions inside the document only. `${?DB_PASSWORD}`, the usual way to read a secret in an `application.conf`, silently resolves to nothing, and `${DB_PASSWORD}` is a parse error, even when the variable is set.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config-hocon` 0.0.56; run in an environment where `HOME` is set):

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._

def keys(doc: String): Unit =
  ConfigSource.fromHocon(doc) match {
    case Right(source) => println(source.all("").map { case (k, v) => s"$k = '${v.value}'" }.toList.sorted)
    case Left(error)   => println(error.message)
  }

println(sys.env.contains("HOME"))
// true

keys("home = ${?HOME}")
// List()

keys("home = ${HOME}")
// Parse error for key '' (expected valid HOCON) in source 'hocon:string'
```

**Expected.** If this is meant to match Typesafe Config: `home = ${?HOME}` and `home = ${HOME}` both produce `home = '<value of HOME>'`. If the omission is deliberate (environment access is layered with `EnvSource` instead), the parse error for `${HOME}` should say that environment substitution is not supported, and `formats.md` should say so in its HOCON section.

**Root cause.** `resolveSubstitutions` looks a substitution up with `lookupPath` in the parsed document only (`config-hocon/shared/src/main/scala/zio/blocks/config/hocon/HoconParser.scala:621-631`, `:655`). When the path is missing, an optional substitution becomes `HoconValue.Null`, which `HoconValue.flatten` drops, and a required one fails with `Unresolved substitution` (`:630`). No code in `config-hocon` reads the environment.

**Impact.** The most common migration step from Typesafe Config or PureConfig silently stops working: the secret is not loaded, and the error, if any, appears later as a missing key.

**Test gap.** None for the current behaviour; there is no test that pins either the fallback or its absence.

**Suggested fix.** Decide and document. Either add an opt-in fallback (for example a resolver parameter that defaults to no fallback, so `config-hocon` stays deterministic and works on Scala.js), or keep the current behaviour and make both cases visible: name environment substitution in the `Unresolved substitution` error, and document it in `docs/reference/config/formats.md`.

## config: `ProvenanceMap#dump` misreports env overrides through a key-formatted `EnvSource`

Found 2026-10-20 while writing the migration guide `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `EnvSource.keyFormat(KeyFormat.UpperSnakeCase)` is how a camelCase field such as `featuredTitle` is read from `FEATURED_TITLE`. Loading through it works, and `ProvenanceMap#provenanceOf` names the right variable, but `ProvenanceMap#dump` lists the override under a made-up key, `featured.title`. It also keeps the file's overridden `featuredTitle` row, which now looks like the value in effect.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config` and `zio-blocks-next-config-hocon` 0.0.56; run with `BOOKSTORE_FEATURED_TITLE="Programming in Scala"` set):

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.schema.Schema

case class Bookstore(featuredTitle: String, port: Int)
object Bookstore { implicit val schema: Schema[Bookstore] = Schema.derived }

val file = ConfigSource.fromHocon("""featured-title = "Zionomicon", port = 8080""", "application.conf").toOption.get
val env  = EnvSource.keyFormat(KeyFormat.UpperSnakeCase).prefix("bookstore")

val provenance = Config.loadWithProvenance[Bookstore](env.orElse(file.keyFormat(KeyFormat.KebabCase))).toOption.get

println(provenance.value)
// Bookstore(Programming in Scala,8080)

println(provenance.provenanceOf("featuredTitle"))
// Resolved(env,BOOKSTORE_FEATURED_TITLE,Programming in Scala)

println(provenance.dump())
// ┌────────────────┬──────────────────────┬──────────────────┐
// │ Key            │ Value                │ Source           │
// ├────────────────┼──────────────────────┼──────────────────┤
// │ featured.title │ Programming in Scala │ env              │
// │ featuredTitle  │ Zionomicon           │ application.conf │
// │ port           │ 8080                 │ application.conf │
// └────────────────┴──────────────────────┴──────────────────┘
```

**Expected.** One row, `featuredTitle | Programming in Scala | env`, and no `featured.title` row.

**Root cause.** `dump` lists `source.all(prefix)`. `EnvSource.all` turns each variable name back into a key with `fromEnvKey`, which lower-cases it and replaces every `_` with `.` (`config/jvm/src/main/scala/zio/blocks/config/PlatformConfigSource.scala:37-48`, `:53-54`; the same on Scala.js at `config/js/.../PlatformConfigSource.scala:49-50`). So `BOOKSTORE_FEATURED_TITLE` becomes `bookstore.featured.title`, and the word boundary is lost. The key-format wrapper's `all` then calls `KeyMapper.toCanonical` (`config/shared/src/main/scala/zio/blocks/config/ConfigSource.scala:100-103`), which leaves `featured.title` alone because it has no `_` or `-`. `orElse.all` merges the layers by key (`ConfigSource.scala:54-60`), and `featured.title` never meets the file's `featuredTitle`.

**Impact.** The provenance table, whose job is to answer "where did this value come from?", shows the wrong value as active for every multi-word field that is overridden from the environment.

**Test gap.** `ConfigSpec` "dump produces formatted table" uses a plain map source, and `ConfigSourceCompositionSpec` "keyMapper composes with orElse" checks `get` only. Nothing checks `all` or `dump` through a key-formatted `EnvSource`.

**Suggested fix.** In the key-format wrapper's `all`, map environment-style keys with the target format in mind: for `UpperSnakeCase`, treat each `_`-separated word of the original variable name as a camelCase word boundary. One way is for `EnvSource.all` to return keys whose segments keep `_`, so `toCanonical` can rebuild `featuredTitle`. Add a `dump` test for a key-formatted env layer over a file layer.

## config-hocon: parse errors lose their line and column

Found 2026-10-20 while writing the migration guide `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** A syntax error in a HOCON document is reported as `Parse error for key '' (expected valid HOCON) in source '<name>'`, with no hint of where the error is. The parser does record a message with the line and column, but it is held only in the `cause` field of `ConfigError.ParseError`. It is not part of `message` or `getMessage`, and `getCause` returns `null`. Errors found while resolving substitutions report line 0, column 0 even there.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config-hocon` 0.0.56):

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._

List("name = \"Scala Bookstore\"\ntags = [scala,\n", "a = ${missing}").foreach { doc =>
  ConfigSource.fromHocon(doc, "application.conf") match {
    case Left(e: ConfigError.ParseError) =>
      println(e.message)
      println(e.getCause)
      println(e.cause.map(_.getMessage))
    case other =>
      println(other)
  }
}
// Parse error for key '' (expected valid HOCON) in source 'application.conf'
// null
// Some(Unterminated array, expected ']' after ',' (line 3, column 1))
// Parse error for key '' (expected valid HOCON) in source 'application.conf'
// null
// Some(Unresolved substitution: ${missing} (line 0, column 0))
```

**Expected.** `message` (and so `getMessage` and any log line) includes the parser's message, for example `... in source 'application.conf': Unterminated array, expected ']' after ',' (line 3, column 1)`. `getCause` returns the `HoconError`. Resolution errors carry the position of the substitution.

**Root cause.** `HoconConfigSource.toConfigError` builds `ConfigError.ParseError("", sourceId, "valid HOCON", Some(e))` with an empty path (`config-hocon/shared/src/main/scala/zio/blocks/config/hocon/HoconConfigSource.scala:55-56`). `ParseError#message` appends the cause only when `path` is non-empty (`config/shared/src/main/scala/zio/blocks/config/ConfigError.scala:165-173`). `ConfigError` extends `NoStackTrace` and never passes `cause` to `Throwable` (`ConfigError.scala:30-33`), so `getCause` is `null`. Errors from the resolution pass are wrapped as `HoconError(msg, 0, 0)` (`HoconParser.scala:55`).

**Impact.** Every HOCON syntax error in a real file has to be found by hand. JSON and YAML parse failures are worse off: `ConfigSource.fromJson` and `ConfigSource.fromYaml` build `ParseError("", sourceId, "valid JSON")` (or `"valid YAML"`) with no cause at all, so not even the `cause` field says what went wrong.

**Test gap.** `ConfigErrorSpec` tests `ParseError` messages only with a non-empty path, and no test checks the message of a HOCON parse failure.

**Suggested fix.** Append the cause message in `ParseError#message` whatever the path, and override `getCause` to return the cause. Record the position of each substitution when parsing, and use it for resolution errors. Add a `HoconConfigSourceSpec` test that asserts the full message of a syntax error.

## config: `Config.load` returns one `Composite` for many errors, so `loadOrThrow` reports "1 error(s)"

Found 2026-10-19 while writing the tutorial `docs/guides/typed-config-loader.md` (backlog T-05), and reproduced 2026-10-20 while writing `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running. PR #1761 fixed the report but was closed without merging, so the bug is still open.

**Summary.** `Config.load` returns `Either[::[ConfigError], A]`, and its Scaladoc says it "returns all accumulated `ConfigError`s". When a case class has several bad fields, though, the list holds a single `ConfigError.Composite` that wraps them. Code that counts or walks the list sees one error. `Config.loadOrThrow` builds its report from that list, so it says `failed with 1 error(s)`, prints the `-` bullet only before the first message, and puts the other messages on unindented lines. `ConfigLoadException#errors` also has length 1.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config` 0.0.56):

```scala
import zio.blocks.config._
import zio.blocks.schema.Schema

case class DbConfig(host: String, port: Int)
case class BookstoreConfig(name: String, port: Int, db: DbConfig)
object BookstoreConfig { implicit val schema: Schema[BookstoreConfig] = Schema.derived }

val source = ConfigSource.fromMap(Map("port" -> "eighty", "db.port" -> "5432"), "application.conf")

Config.load[BookstoreConfig](source) match {
  case Left(errors) =>
    println(errors.length)
    println(errors.head.getClass.getSimpleName)
  case Right(config) => println(config)
}
// 1
// Composite

try Config.loadOrThrow[BookstoreConfig](source)
catch { case e: ConfigLoadException => print(e.getMessage); println(e.errors.length) }
// Configuration loading failed with 1 error(s):
//   - Missing required key 'name' in source 'application.conf'
// Invalid value 'eighty' for key 'port' (expected Int) in source 'application.conf': For input string: "eighty"
// Missing required key 'db.host' in source 'application.conf'
// 1
```

**Expected.** `errors.length` is `3`, with one entry per problem. The report says `failed with 3 error(s):` and puts each message on its own `  - ` line. `e.errors.length` is `3`.

**Root cause.** The record decoder collects every field error, but when there is more than one it returns `Left(::(ConfigError.Composite(errors), Nil))`, a one-element list (`config/shared/src/main/scala/zio/blocks/config/ConfigDecoderDeriver.scala:122-127`, in `deriveRecord` at `:61`). The sequence and map decoders return the errors as a flat list instead (`:249-251` and `:304-306`), so the shape of `Left` depends on the type being decoded. `Config.formatErrors` counts `errors.length` and writes one `  - ` bullet per list element (`config/shared/src/main/scala/zio/blocks/config/Config.scala:131-142`), and `Composite#message` joins its children with `"\n"` (`ConfigError.scala:126-130`), which is where the unindented lines come from.

**Impact.** The headline count in every startup failure report is wrong whenever more than one field is bad, which is the case `Config.load` is designed for. Callers who match on the list (for example to group errors by kind) have to know to unwrap `Composite` first.

**Test gap.** `ConfigSpec` checks only that `e.errors.head` is a `Composite` and that `e.report.contains("error(s)")` (`config/shared/src/test/scala/zio/blocks/config/ConfigSpec.scala:115`, `:143`). No test asserts the count or the full report.

**Suggested fix.** Return the record errors as a flat list, as the sequence and map decoders already do, and keep `Composite` only for callers that build one themselves. At the least, flatten `Composite` in `formatErrors` (this is what PR #1761 did). Assert the full report string in `ConfigSpec`.

## docs: `formats.md` says infinite HOCON numbers keep their `Double` rendering

Found 2026-10-20 while writing `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `docs/reference/config/formats.md:164` says: "Non-integral and infinite values keep their `Double` rendering." The HOCON parser never produces an infinite number. A literal too large for a `Double` (such as `1e400`), `Infinity` and `-1e400` are all kept as the text that was written, as strings.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config-hocon` 0.0.56):

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._

List("big = 1e400", "big = Infinity", "big = 1.5", "big = -1e400").foreach { doc =>
  println(ConfigSource.fromHocon(doc).map(_.all("").map { case (k, v) => s"$k='${v.value}'" }.toList))
}
// Right(List(big='1e400'))
// Right(List(big='Infinity'))
// Right(List(big='1.5'))
// Right(List(big='-1e400'))

println(HoconParser.parse("big = 1e400"))
// Right(Obj(Map(big -> Str(1e400))))
```

**Root cause.** `interpretUnquoted` keeps a number only when `!d.isNaN && !d.isInfinite`, and otherwise falls back to a string (`config-hocon/shared/src/main/scala/zio/blocks/config/hocon/HoconParser.scala:392-393`).

**Suggested fix.** Replace the sentence with: "Non-integral values keep their `Double` rendering. A number too large for a `Double`, such as `1e400`, is kept as the text that was written."

## docs: `formats.md` leaves out the 1 MiB file-size limit of `ConfigSource.fromFile`

Found 2026-10-20 while writing `docs/guides/config-library-migration.md` (backlog M-06), on `main` at `4e89c0be`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** The "Loading from a File" section of `docs/reference/config/formats.md` (`:199-228`) lists `allowedBase` and `maxIncludeDepth` as the two guards on `ConfigSource.fromFile`. It does not mention the third one: `fromFile` refuses a file or include larger than 1 MiB, and the only way to raise the limit is `ConfigSource.fromFileWithLimits(..., maxFileBytes = ...)`, which the page never names. A reader whose generated configuration grows past 1 MiB gets a parse error that the page does not explain.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-config-hocon` 0.0.56, JVM):

```scala
import zio.blocks.config._
import zio.blocks.config.hocon._
import java.nio.file.Files

val file = Files.createTempDirectory("bookstore").resolve("application.conf")
Files.writeString(file, "bookstore.name = \"Scala Bookstore\"\n" + ("# padding\n" * 120000))
println(Files.size(file))

ConfigSource.fromFile(file.toString) match {
  case Left(e: ConfigError.ParseError) => println(e.cause.map(_.getMessage))
  case other                           => println(other)
}

ConfigSource.fromFileWithLimits(file.toString, maxFileBytes = 4L * 1024 * 1024).map(_.all("").keys.toList).foreach(println)
// 1200035
// Some(File size exceeds limit (1048576 bytes) (line 0, column 0))
// List(bookstore.name)
```

**Root cause.** `fromFile` delegates to `fromFileWithLimits` with `DefaultMaxFileBytes = 1024L * 1024L` (`config-hocon/jvm/src/main/scala/zio/blocks/config/hocon/HoconConfigSourcePlatform.scala:28`, `:204`), and the read throws `File size exceeds limit` above it (`:36`, `:54`). The docs table was written before the limit existed (added by PR #1649).

**Suggested fix.** Add a third row to the table: `maxFileBytes` | `1 MiB` | "Maximum size of the file and of each include; a larger one is a parse failure. Set it with `ConfigSource.fromFileWithLimits`." Then change "Two parameters guard ..." to "Three parameters guard ...", and add the size failure to the failures paragraph.

## `Jwt.sign` writes a registered claim twice when `extra` also contains it, and verifiers read the `extra` value

Found 2026-10-20 while writing the how-to guide `docs/guides/issue-and-verify-jwts.md` (backlog H-37), on `main` at `ad0821d2`. The jwt sources are the same at tag `v0.0.56`. Verified by running against the published 0.0.56.

**Summary.** `JwtClaims` has typed fields for the registered claims (`iss`, `sub`, `aud`, `exp`, `nbf`, `iat`, `jti`) and a free-form `extra` map. If `extra` also contains one of these names, `Jwt.sign` writes the claim twice into the signed payload. Every JSON parser that keeps the last duplicate, including this module's own decoder, then reads the `extra` value, not the typed field. The issuer believes it signed `iss = "bookstore-auth"`; every verifier sees `iss = "partner-auth"`. RFC 7519 §4 says claim names within a claims set MUST be unique, so the issuer produces a non-conforming token without any error.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-jwt` 0.0.56, run with `scala-cli run`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-jwt:0.0.56
import zio.blocks.jwt._
import java.nio.charset.StandardCharsets.UTF_8

val secret = "sample-bookstore-secret-32-bytes".getBytes(UTF_8)

// The issuer sets iss = "bookstore-auth"; a custom claim map built elsewhere also contains "iss"
val claims = JwtClaims(
  iss = Some("bookstore-auth"),
  sub = Some("reader-1024"),
  extra = Map("iss" -> JwtValue.Str("partner-auth"))
)
val token = Jwt.sign(claims, secret, Algorithm.HS256).fold(e => throw e, identity)

println(Base64Url.decode(token.split('.')(1)).map(new String(_, UTF_8)))
println(Jwt.decode(token, secret, Algorithm.HS256, JwtDecodeOptions(nowSeconds = Some(0L))).map(_.iss))
println(Jwt.decode(token, secret, Algorithm.HS256, JwtDecodeOptions(issuer = Some("bookstore-auth"), nowSeconds = Some(0L))))
// Right({"iss":"bookstore-auth","sub":"reader-1024","iss":"partner-auth"})
// Right(Some(partner-auth))
// Left(zio.blocks.jwt.JwtError$InvalidClaim: Invalid JWT claim 'iss': expected bookstore-auth but found partner-auth)
```

**Expected.** `Jwt.sign` never emits duplicate claim names. Either it rejects the claims (for example `Left(InvalidClaim("iss", "registered claim name must not appear in extra"))`), or the typed field wins and the `extra` entry is dropped. The decoded `iss` should be `Some(bookstore-auth)`.

**Root cause.** `JwtClaims.render` writes the typed fields and then every `extra` entry without filtering registered names (`jwt/shared/src/main/scala/zio/blocks/jwt/JwtClaims.scala:91-93`). The parser's object builder keeps the last value for a repeated key (`fields.update(key, value)`, `jwt/shared/src/main/scala/zio/blocks/jwt/Jwt.scala:622` and `:671`), and `parseExtra` drops registered names from `extra` (`JwtClaims.scala:98-104`), so the duplicate silently replaces the typed field. The decoder's last-wins behaviour is allowed by RFC 7515 §5.2; the bug is on the signing side.

**Impact.** Code that merges custom claims from another source (a user profile map, a config file) into `extra` can override `iss`, `sub`, `aud` or `exp` without noticing. An `exp` in `extra` can extend a token's lifetime past the typed `exp`; a `sub` can change whose token it is. The JWT reference page now tells users not to put registered names in `extra`, but nothing enforces it.

**Test gap.** No test signs claims whose `extra` contains a registered name. `JwtFeatureSpec` only round-trips non-reserved custom claims.

**Suggested fix.** Validate in `Jwt.sign` (or in `JwtClaims.render`) that `extra.keySet` and the registered names are disjoint, and return `Left(JwtError.InvalidClaim(name, ...))` otherwise. Add a test per registered name.

## `JwtSignOptions.limits` does not apply the JSON-shape limits, and two Scaladocs contradict the code

Found 2026-10-20 while writing the how-to guide `docs/guides/issue-and-verify-jwts.md` (backlog H-37). Verified by running against the published 0.0.56.

**Summary.** `Jwt.sign` applies only the size limits of `JwtSignOptions.limits` (`maxJsonChars`, `maxSegmentChars`, `maxTokenChars`). It ignores `maxDepth`, `maxFields` and `maxArrayElements`, so an issuer can sign a token that a verifier configured with the same `JwtLimits` rejects. Separately, the Scaladoc of `JwtDecodeOptions` says an audience mismatch yields `InvalidToken`, but the code returns `InvalidClaim("aud", ...)`.

**Reproduction** (Scala 3.9.0, `zio-blocks-next-jwt` 0.0.56, run with `scala-cli run`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-jwt:0.0.56
import zio.blocks.jwt._
import java.nio.charset.StandardCharsets.UTF_8

val secret = "sample-bookstore-secret-32-bytes".getBytes(UTF_8)
val limits = JwtLimits(maxDepth = 3)

// A custom claim nested five arrays deep
val deep   = (1 to 5).foldLeft(JwtValue.Str("Zionomicon"): JwtValue)((v, _) => JwtValue.Arr(v))
val signed = Jwt.sign(JwtClaims(extra = Map("shelf" -> deep)), secret, Algorithm.HS256, JwtSignOptions(limits = limits))

println(signed.isRight)
println(signed.flatMap(t => Jwt.decode(t, secret, Algorithm.HS256, JwtDecodeOptions(limits = limits, nowSeconds = Some(0L)))))
// true
// Left(zio.blocks.jwt.JwtError$TooDeep: JWT JSON too deep: depth 4 exceeds limit 3)
```

**Expected.** Signing with `JwtSignOptions(limits = limits)` fails with `Left(TooDeep(...))` when the decoder with the same `limits` would, or the Scaladoc says which limits apply on sign.

**Root cause.** The sign path checks `maxJsonChars`, `maxSegmentChars` and `maxTokenChars` only (`jwt/shared/src/main/scala/zio/blocks/jwt/Jwt.scala:245-259`); it renders JSON with `JwtClaims.render` and never runs the depth, field or element checks that the parser applies on decode. The `JwtSignOptions` Scaladoc describes `limits` as "resource limits for header JSON validation" (`Jwt.scala:169`), which matches neither behaviour. The `JwtDecodeOptions` Scaladoc says "mismatch yields `InvalidToken`" (`Jwt.scala:131-132`), while `validateAudience` returns `JwtError.InvalidClaim("aud", ...)` (`Jwt.scala:450-453`, tested in `JwtFeatureSpec.scala:354-372`).

**Impact.** Low. An issuer that shares one `JwtLimits` value between signing and verifying can mint tokens its own verifiers reject, and only finds out at verification time. The wrong `InvalidToken` statement in the Scaladoc was also copied into `docs/reference/jwt.md` (fixed in the H-37 PR).

**Test gap.** No test signs with tightened `JwtSignOptions.limits`. No test sends an `alg: none` header (the code returns `UnsupportedAlgorithm("none")`, or `InvalidToken` when the signature segment is empty, but nothing asserts it).

**Suggested fix.** Either apply all six limits on sign (parse the rendered JSON with the same limits before encoding), or document that only the size limits apply on sign. Change the `JwtDecodeOptions` Scaladoc to "mismatch yields `InvalidClaim("aud", ...)`". Add tests for both, plus an `alg: none` test in `JwtEdgeCasesSpec`.

## config: `Config.wire[A](prefix)` reports keys without the prefix, so the report doesn't say which section failed

Found 2026-10-20 while writing the how-to guide `docs/guides/feed-config-into-a-wire-graph.md` (backlog H-30), on `main` at `b4179489`. The code is the same at tag `v0.0.56`. Verified by running.

**Summary.** `Config.wire[A](prefix)` reads one section of a `ConfigSource`, such as `db` or `catalog`, while a `Resource.from` graph is built. When the section is wrong, the `ConfigLoadException` report names the keys relative to the section: `Missing required key 'url'` instead of `'db.url'`. An application usually has several sections with the same field names (`url`, `port`, `host`), so the report can't tell the operator which section to fix. Decoding the same sections as fields of one case class with `Config.load` reports the full paths.

**Reproduction** (`scala-cli run .`):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-config:0.0.56

import zio.blocks.config.*
import zio.blocks.context.Context
import zio.blocks.schema.Schema
import zio.blocks.scope.*

case class DbConfig(url: String, poolSize: Int)
object DbConfig { implicit val schema: Schema[DbConfig] = Schema.derived }

case class CatalogConfig(url: String, pageSize: Int)
object CatalogConfig { implicit val schema: Schema[CatalogConfig] = Schema.derived }

@main def wirePrefix(): Unit = {
  val source = ConfigSource.fromMap(Map("db.poolSize" -> "ten", "catalog.url" -> "https://books.example"), "app.yaml")

  for (wire <- List(Config.wire[DbConfig]("db"), Config.wire[CatalogConfig]("catalog")))
    try Scope.global.scoped(scope => wire.make(scope, Context[ConfigSource](source)): Unit)
    catch { case e: ConfigLoadException => println(e.getMessage) }

  // The same sections decoded as fields of one class report the full path:
  case class AppConfig(db: DbConfig, catalog: CatalogConfig)
  given Schema[AppConfig] = Schema.derived
  println(Config.load[AppConfig](source).left.map(_.head.message))
}
// Configuration loading failed with 1 error(s):
//   - Missing required key 'url' in source 'app.yaml'
// Invalid value 'ten' for key 'poolSize' (expected Int) in source 'app.yaml': For input string: "ten"
//
// Configuration loading failed with 1 error(s):
//   - Missing required key 'pageSize' in source 'app.yaml'
//
// Left(Missing required key 'db.url' in source 'app.yaml'
// Invalid value 'ten' for key 'db.poolSize' (expected Int) in source 'app.yaml': For input string: "ten"
// Missing required key 'catalog.pageSize' in source 'app.yaml')
```

(The `1 error(s)` count is the separate `Composite` bug above.)

**Expected.** The first report names `db.url` and `db.poolSize`, and the second names `catalog.pageSize`, as `Config.load[AppConfig]` does.

**Root cause.** `Config.wire[A](prefix)` re-roots the source with `ConfigSource#prefix` and then decodes it at the empty path: `loadOrThrow[A](ctx.get[ConfigSource].prefix(prefix))` (`config/shared/src/main/scala/zio/blocks/config/Config.scala:40-43`), which calls `decoder.decode(source, "")` (`Config.scala:66-69`). The decoder builds each error's key from the path it was given (`ConfigDecoderDeriver.scala:396`, `:438`), so the prefix lives only inside the wrapped source and never reaches the error. The prefixed source also keeps the original `sourceId` (`ConfigSource.scala:72`), so nothing in the message shows the section.

**Impact.** Every application that uses `Config.wire` with a prefix, which is the pattern for giving each service its own section, gets startup reports that don't name the section. The guide H-30 tells readers that `pageSize` in the report means `catalog.pageSize`.

**Test gap.** The `Config.wire` suite in `ConfigSpec` tests the prefixed wire only on success (`config/shared/src/test/scala/zio/blocks/config/ConfigSpec.scala:89-95`); its failure test uses the unprefixed wire and checks only `report.contains("error(s)")` (`:103-116`).

**Suggested fix.** Decode the original source at the prefix instead of re-rooting it: `ConfigDecoder.derive[A].decode(ctx.get[ConfigSource], prefix)`, then throw `ConfigLoadException` with the formatted report as `loadOrThrow` does (for example through a `private[config]` overload `loadOrThrow[A](source, prefix)`). Run against 0.0.56, `ConfigDecoder.derive[DbConfig].decode(source, "db")` already reports `Missing required key 'db.url'` and `'db.poolSize'`. Add a failure test for the prefixed wire that asserts the full report.

## docs: `context.md` shows `Wire.make[Config & Logger, Service]`, which does not exist

Found 2026-10-20 while writing the how-to guide `docs/guides/feed-config-into-a-wire-graph.md` (backlog H-30), on `main` at `b4179489`. The same at tag `v0.0.56`. Verified by compiling.

**Summary.** The section "Integration with Wire and Scope" of `docs/reference/context.md:470-493` builds a service with `val buildService = Wire.make[Config & Logger, Service]`. The block is a plain fence labelled pseudocode, so mdoc never compiles it, but it reads as the API. `Wire` has no `make` method on its companion; `make(scope, ctx)` is an instance method of `Wire.Shared` and `Wire.Unique`. The macro that derives a wire from `Service`'s constructor is `Wire.shared[Service]` (or `Wire.unique[Service]`), and it infers the input type `Config & Logger` itself.

**Reproduction** (`scala-cli run .`; the page's code with `Wire.shared`, then the page's line):

```scala
//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-next-scope:0.0.56

import zio.blocks.context.*
import zio.blocks.scope.*

case class Config(debug: Boolean)
case class Logger(name: String)
case class Service(config: Config, logger: Logger)

@main def contextWire(): Unit = {
  val buildService = Wire.shared[Service]
  val deps         = Context(Config(debug = true), Logger("app"))
  Scope.global.scoped { scope =>
    val service: Service = buildService.make(scope, deps)
    println(service)
  }
}
// Service(Config(true),Logger(app))
```

With the page's line instead, `val buildService = Wire.make[Config & Logger, Service]`, compilation fails:

```
[error] ./ContextWireMake.scala:15:22
[error] value make is not a member of object zio.blocks.scope.Wire
[error]   val buildService = Wire.make[Config & Logger, Service]
[error]                      ^^^^^^^^^
```

**Expected.** The page shows code that compiles.

**Root cause.** The page was written against an API that doesn't exist: `Wire`'s companion offers `apply`, `shared`, `unique` and `Shared.fromFunction`/`Unique.fromFunction` (`scope/shared/src/main/scala/zio/blocks/scope/Wire.scala:282`, `scope/shared/src/main/scala-3/zio/blocks/scope/WireVersionSpecific.scala:59`, `:72`, `:93`, `:114`), and no `make`.

**Impact.** A reader who copies the snippet gets a compile error with no hint of the right method. The backlog already warns writers not to copy it (`docs/dev/backlog.md`, rows H-30 and the context entry).

**Test gap.** The block is a plain ` ```scala ` fence labelled pseudocode, so mdoc doesn't compile it.

**Suggested fix.** Replace the line with `val buildService = Wire.shared[Service]`, drop the "pseudocode" label, and make the block `mdoc:compile-only` so it stays compiled.
