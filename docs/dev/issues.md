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

## Rollout buckets depend on the key alone, so every percentage rollout picks the same users

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

## `Rollout.validate` assumes percentages are slices, but evaluation treats them as thresholds

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

## `Flag.dump` prints `StaticFlag[String]` values even when the flag's name marks it as sensitive

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

## `DynamicFlag` accepts expressions whose values its reader cannot parse, and a selector without `%` silently becomes a path

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
