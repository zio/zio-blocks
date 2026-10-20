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
