# ZIO Blocks: documentation issues

Documentation errors (pages, Scaladoc and examples that contradict the code) and docs-tooling problems (mdoc, the docs build, the example projects) found while writing the tutorials, guides and Scaladoc in `docs/dev/backlog.md`. Library bugs and usability traps are in [`library-issues.md`](library-issues.md). When an entry is fixed, add the PR and move it to [Resolved](#resolved).

"Verified" means a session reproduced the problem by running code. "From source" means it was found by reading the code and has not been run.

**Who edits this file:** only the coordinating session. Writing sessions send complete entries to it (see AGENTS.md).

Contents:

- [Documentation errors](#documentation-errors)
- [Docs tooling and examples](#docs-tooling-and-examples)
- [Resolved](#resolved)

## Documentation errors

Pages that contradict the source. None of these were copied into the new guides.

### `reference/schema/built-in-codecs/xml.md`: the `@xmlAttribute` and `@xmlNamespace` examples do not do what the page says

**Where found.** `docs/reference/schema/built-in-codecs/xml.md` (Attributes, Namespaces, "Complete Example with Attributes and Namespaces", "RSS Feed Parsing", "Atom Feed with Advanced Features"), 2026-11-05, commit a7014433f.

**Summary.** The annotations have no effect (see the library entry "`@xmlAttribute` and `@xmlNamespace` have no effect"). The `mdoc` blocks that print output (`val xml = codec.encodeToString(person)` at about line 325, the two Feed blocks at about 358 and 378, `xmlStr` at about 835) render plain child elements without attributes or namespaces, contradicting the prose ("Encode the person with attributes", "Encode the feed with a namespace prefix"). The RSS example's `codec.decode(feedXml)` fails (`guid` is an attribute in the input, a child element for the codec).

**Fix.** Until the library is fixed, show `@Modifier.config("xml.attribute", "")` / `@Modifier.config("xml.namespace.uri", ...)` and `@Modifier.config("xml.namespace.prefix", ...)`, name the bug, and re-check the rendered output; also state that decoding ignores namespaces.

### `reference/schema/built-in-codecs/xml.md`: the `XmlSelection` and `XmlPatch` paths start one level too high

**Where found.** `docs/reference/schema/built-in-codecs/xml.md` ("XmlSelection > Navigation", "XmlPatch > Creating Patches", "Applying Patches", "Composing Patches", "Navigation and Transformation"), 2026-11-05, commit a7014433f.

**Summary.** `xml.select` already selects the root element, and `get(name)` looks at children, so `xml.select.get("library").get("books")` on a `<library>` document is empty, and so is every value derived from it (`firstBook`, `title`, `titles`, `books`). `books.get("book")(0)` would not pick the first book either: `apply(index)` returns the child at that index of every selected element (here the `<title>` of each book). `XmlPatch` paths also start at the root's children: `XmlPatch.setAttribute(p".person", ...)` on `<person>...</person>` returns `Left(Element 'person' not found)`, and `p".library.books"` on `<library>` fails the same way. Probe `/tmp/claude-1000/xml-2a/p1` (both Scala versions):

```
11 select.get(library) => 0
11b select.get(books) => 1
11c get(book)(0) => Chunk(Element(title,Chunk(),Chunk(Text(T1))),Element(title,Chunk(),Chunk(Text(T2))))
12 patch p.person => Left(zio.blocks.schema.SchemaError: Element 'person' not found)
12b patch root => Right(<person active="true"><name>Alice</name></person>)
```

The `mdoc` blocks that print `title` and `allTitles` render whatever the empty selection gives; the `compile-only` patch blocks are never run.

**Fix.** Drop the root name from selection chains (`xml.select.get("books").get("book")`), use `DynamicOptic.root` for the root and `p".books.book"`-style paths below it, and use `XmlSelection#apply` only with its real meaning (or pick from `toChunk`). Run the patch blocks with `mdoc` so their results show.

### `reference/schema/built-in-codecs/xml.md`: smaller errors

**Where found.** `docs/reference/schema/built-in-codecs/xml.md`, 2026-11-05, commit a7014433f.

- XmlName section: `simple.namespace // ""` and `simple.prefix // ""` — both are `Option[String]`, so the values are `None`.
- XmlBuilder section: `XmlBuilder.cdata("<![CDATA[raw content]]>")` puts the markers inside the content, so it prints `<![CDATA[<![CDATA[raw content]]>]]>`, which is malformed; pass `"raw content"`. The "Result:" comment shows indented output for a value that is never pretty-printed.
- WriterConfig section: `val compact = WriterConfig` binds the companion object; it should be `WriterConfig.default`.
- Supported Types / XmlCodec sections: "Variants (sealed traits)" and "Options" are listed without the limits (a variant below the top level and an `Option` inside a collection do not round-trip; see the library entries).
- Error Handling: "All decoding operations return `Either[SchemaError, A]` or `Either[SchemaError, A]`" repeats itself; "Error information includes parse location" holds only for malformed XML (reader errors carry `(line: L, column: C)`), not for decoding errors, which carry a path such as `at: .qty`.

### `reference/schema/built-in-codecs/toon.md`: misstates config scope, primitive count and the reader/writer

Found 2026-11-05 while fact-checking the schema-toon Scaladoc against the reference page (Task 2b), commit `a7014433f`; the page is the same at the 0.0.56 docs. Evidence: probes `p1`, `p2`, `p5` under `/tmp/claude-1000/task2b-toon/` (all against 0.0.56).

1. **`toon.md:84-94`** ("How They Work Together", steps 6 and 7) says `WriterConfig` controls "Array format selection" and `ReaderConfig` controls "Delimiter selection for inline arrays". The array format is a deriver setting (`ToonCodecDeriver.withArrayFormat`); `WriterConfig` has no such field (`WriterConfig.scala`, five fields: indent, delimiter, keyFolding, flattenDepth, discriminatorField). `ReaderConfig.withDelimiter` has no effect on decoding arrays, as the page itself says at `:624` (probe `p2`: `ReaderConfig.withDelimiter(Delimiter.Pipe)` still decodes `[2]: a,b` as `List(a, b)`). Steps 6 and 7 should also say that key folding, the delimiter and the discriminator field reach only the `DynamicValue` codec. Fix: drop "Array format selection" from step 6 and "Delimiter selection" from step 7; add "(`DynamicValue` codec only)" to the other DynamicValue-only items.
2. **`toon.md:120-121`** ("Type Relationships") repeats it: `WriterConfig` "(indentation, delimiters, discriminators)" and `ReaderConfig` "(path expansion, strict mode, delimiters)". Fix: "indentation for every codec; delimiter, key folding and discriminator field for the `DynamicValue` codec" and "indentation and strict mode for every codec; path expansion and discriminator field for the `DynamicValue` codec".
3. **`toon.md:6`, `:54`, `:440`, `:461`** say "27 primitive types". `ToonCodecDeriver#derivePrimitive` maps 30 (`ToonCodecDeriver.scala:137-167`: `Unit` through `UUID`), and the page's own list at `:462-465` names 30 (8 numeric, 3 logical, 16 temporal, 3 special). Fix: "30".
4. **`toon.md:1006`** says "`ToonCodecDeriver` drives all codec derivation via `ToonCodecDeriver.derive(schema)`". No such method exists (`Deriver` has only the `deriveX` hooks, `schema/shared/src/main/scala/zio/blocks/schema/derive/Deriver.scala:100-200`); derivation is `Schema#derive(ToonCodecDeriver)` or `Schema#derive(ToonFormat)`. Fix the call.
5. **`toon.md:738-742`, `:784-788`** call `ToonReader` a "low-level binary parser" with "error reporting at the byte level" and `ToonWriter` a "binary encoder" into "TOON binary format". TOON is text: the reader decodes the input as UTF-8 and splits it into lines (`ToonReader.scala:93-104`), and errors carry a path (`at: .port`), not a byte offset. Fix: "line-oriented text parser/writer".
6. **`toon.md:51`** ("Handle errors — Catch `SchemaError`"): the `decode` methods return `Either[SchemaError, A]` and catch the exceptions themselves (`ToonCodec.scala:234-236`); nothing needs catching. Fix: "Handle the `Left(SchemaError)`".
7. **`toon.md:865-907`** (DiscriminatorKind) and **`:688-690`** (known bugs) do not mention the two new `DiscriminatorKind.Field`/`None` bugs (B2, B3) or the `Array` field bug (B1). Once filed, add them to the page's known-bugs admonitions next to #1778-#1780.

### `reference/schema/built-in-codecs/yaml.md`: error paths are shown as `.field[0]`, but YAML errors render indexes as `.at(0)`

Found 2026-11-05 during the schema-yaml Scaladoc rollout (base a7014433f; page last changed in `4d774b674`). Same at 0.0.56: yes. Verified by running.

**Summary.** `docs/reference/schema/built-in-codecs/yaml.md` says "Errors render as paths like `.field[0].nested.value`" (line 582) and, in the pipeline diagram, "Shows path (.field[index].nested)" (line 94). A YAML decoding error renders a sequence index as `.at(1)`, the `DynamicOptic` form, so a reader who searches logs for `[1]` finds nothing.

**Reproducer** (save as `Repro.sc`, run `scala-cli run --server=false Repro.sc`; probe directory `/tmp/claude-1000/yaml-2c/docs-path`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema-yaml:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Team(name: String, members: List[String])
object Team { implicit val schema: Schema[Team] = Schema.derived }

println(Team.schema.derive(YamlFormat).decode("name: core\nmembers:\n  - ada\n  - lead: grace\n"))
// Left(zio.blocks.schema.SchemaError: Expected scalar for string at: .members.at(1))
```

(Output of the same shape from `fc1`: `Left(zio.blocks.schema.SchemaError: Expected scalar for string at: .tags.at(1))`.)

**Expected page text.** "Errors render as paths like `.members.at(1)` or `.inner.level`" and, in the diagram, "Shows path (.field.at(index).nested)".

**Suggested fix.** Edit the two lines; optionally add an mdoc example whose failure is inside a list, so the rendered output shows `.at(n)`.

### `reference/schema/built-in-codecs/avro.md`: claims a configurable sum-type encoding and calls a format property a bug

Found 2026-11-05 while fact-checking the Avro Scaladoc against this page (schema formats Scaladoc rollout, Task 1), on `main` at `c2e4a39fd`. Same at v0.0.56.

**Summary and fixes.**
- Line 17, "Configurable sum type handling (union fields with discriminators)": `AvroCodecDeriver` has no configuration at all (`object AvroCodecDeriver extends Deriver[AvroCodec]`, no options); a variant is always a union of the case records, written as the branch index and the case (`AvroCodecDeriver.scala:180-231`). Replace with "Sum types as Avro unions, one record per case".
- Lines 8, 19 and 178, "recursive data structures with cycle detection" / "automatic cycle detection": the deriver handles recursive *types* (a record refers to itself by name in the schema, `recursiveRecordCache`), but there is no cycle detection for *values*; a cyclic value cannot exist for case classes anyway. Say "recursive types, such as trees".
- Lines 43 and 323-325: step 4 says "Catch `SchemaError`", but `decode` returns `Either[SchemaError, A]` and never throws it; say "Handle the `Left`". Line 323 says decoding bytes of another type "is a known bug in 0.0.56, not a choice of the format", but Avro binary has no type information, so this is inherent to the format (the library-issues entry says so in its root cause). Reword: "Avro binary carries no type information, so the codec cannot detect this; zio-blocks adds no check of its own (a known limitation in 0.0.56)."
- Missing: `decode(ByteBuffer)` on a sliced heap buffer reads the wrong bytes (see the new library entry), worth one sentence in "Decoding Trusts the Reader's Schema".

### `reference/schema/built-in-codecs/thrift.md`: wrong primitive count, invented features, and no word on missing fields

Found 2026-11-05 while fact-checking the Thrift Scaladoc against this page (schema formats Scaladoc rollout, Task 1), on `main` at `c2e4a39fd`. Same at v0.0.56.

**Summary and fixes.**
- Lines 6, 45, 309 and 330 say 27 primitive types; `ThriftCodecDeriver.derivePrimitive` maps all 30 `PrimitiveType`s (`ThriftCodecDeriver.scala:50-79`), and the list under line 330 itself names 30 (it includes `BigInt`, `Unit`, `Currency`, `UUID`). Say 30.
- Line 14, "Automatic schema generation from Scala types", and line 45, "automatically generates Thrift-compatible formats": the module generates no Thrift IDL or schema object (`ThriftCodec` has no schema member, unlike `AvroCodec#avroSchema`). Drop the line or say "No `.thrift` file needed".
- Line 73, "Automatic format detection and optimization": nothing detects a format; `TBinaryProtocol` is fixed (`ThriftCodec.scala:86-88`, `:97-99`). Drop it.
- Lines 12 and 101, 376 say "ZIO Schema"; this module uses ZIO Blocks Schema (`zio.blocks.schema`), a different library. Replace.
- Lines 17 and 157, "automatic cycle detection": as for Avro, recursive types, not cycle detection.
- Line 43, "Catch `SchemaError`": `decode` returns `Either`.
- Lines 180 and 355, "discriminated variants with type information" / "union types with discriminators": the encoding is a struct with one field whose id is the case index plus one (`ThriftCodecDeriver.scala:241-246`); the case name is not written. Say that.
- Missing, and the most important for users: a record field absent from the input decodes as `null`/`0`/`false`; the field's wire type is not checked; `DynamicValue` cannot be decoded; trailing bytes are ignored; `Float` is written as a Thrift `double`; most `java.time` types, `UUID` and `Currency` are Thrift strings. See the new library entries; the Scaladoc of `ThriftCodec` and `ThriftCodecDeriver` now states these.

### `reference/schema/built-in-codecs/bson.md`: claims about timestamps, skipping absent values and error reporting that the code does not meet

Found 2026-11-05 while fact-checking the `schema-bson` Scaladoc against `docs/reference/schema/built-in-codecs/bson.md` at `c2e4a39fd` (same at `v0.0.56`).

- Motivation, first bullet: "Full BSON type support (documents, arrays, strings, numbers, ObjectId, Decimal128, timestamps, etc.)". The codecs never write BSON `DATE_TIME` or `TIMESTAMP`: `Instant`, `LocalDate`, `LocalDateTime`, `OffsetDateTime`, `ZonedDateTime`, `Duration` and the other `java.time` types are written as ISO-8601 `STRING`s (`BsonTypes.scala:448-561`), `UUID` as a `STRING` (`:328-331`), and `BsonCodec.json` fails to decode a `DATE_TIME` with `Unsupported BSON type for Json: DATE_TIME` (`:440`). Verified: `{"inst": "2026-11-05T10:15:30Z"}`, BSON type `STRING`. Suggested text: list what is supported and add a type table (Scala type → BSON type), as `avro.md` has; say plainly that dates are strings, so MongoDB date queries and TTL indexes don't apply to them.
- Introduction, step 4 (`bson.md:46`): "**Handle errors** — Catch `BsonDecoder.Error` with location traces ...". `decode` and `fromBsonValue` return it in a `Left` (`BsonTypes.scala:112-122`); only the `*Unsafe` methods throw it. Suggested: "Handle errors — `decode` and `fromBsonValue` return a `BsonDecoder.Error` in a `Left`, with the path to the bad value".
- `BsonEncoder[A]` › Overview: "It supports skipping absent values". `isAbsent` exists but no code in the module calls it (`grep -rn isAbsent schema-bson/src/main` finds only its definition and `contramap`), so no field is ever skipped (transient fields are dropped by `Modifier.transient`, not by `isAbsent`). Suggested: drop the sentence or say `isAbsent` is unused.
- Motivation: "Precise error reporting with location traces showing the path to errors". True only for `BsonDecoder.Error`; malformed strings and (through `decode`) wrong BSON types throw instead (library entry 1 above). Suggested: add "with a known bug in 0.0.56: ..." once entry 1 is filed.

### `reference/schema/built-in-codecs/csv.md`: Introduction step 4 tells readers to catch `CsvError`; the codec never throws it

Found 2026-11-05 while fact-checking the `schema-csv` Scaladoc against `docs/reference/schema/built-in-codecs/csv.md` at `c2e4a39fd` (same at `v0.0.56`).

- Introduction, step 4: "Handle errors — Catch `CsvError` for malformed input, and `SchemaError` for a cell that doesn't convert". Nothing throws `CsvError`: `CsvReader` returns it in a `Left`, and `CsvCodec#decode` turns malformed CSV into a `SchemaError` whose message is the `CsvError`'s `getMessage` (`CsvCodecDeriver.scala:182`), for example `Left(SchemaError: CSV error at row 1, column 14: Unclosed quoted field at: .)`. Suggested: "Handle errors — `CsvReader` returns a `CsvError` in a `Left`; `CsvCodec#decode` returns a `SchemaError` in a `Left` for a malformed row, a wrong number of cells or a bad cell."
- CsvCodecDeriver › Record Type Support: "Each field becomes a CSV column with the field name as the header" does not say that `Modifier.rename` and `Modifier.transient` are ignored (library entry 3), and "Flat case classes ... are supported" does not warn that an `AnyVal` case class field counts as a nested record (amendment 5). Suggested: one sentence each, naming them as known bugs in 0.0.56.

### `reference/schema/built-in-codecs/messagepack.md`: ext support, variant layout, primitive count and error-path format contradict the codec

Found 2026-11-05 while fact-checking the `schema-messagepack` Scaladoc against `docs/reference/schema/built-in-codecs/messagepack.md` at `aa8164217` (module sources identical to v0.0.56). Verified on Scala 2.13.18 against published 0.0.56.

**Summary.** Four claims on the page are false:

1. Lines 13 and 420: "Full MessagePack type support (all fixint, fixarray, fixmap, ext types, ...)" and the reader handles "all MessagePack types (... ext ...)". No reader path accepts an extension type: the typed readers fail with `Expected ... value, got: 212`, `MessagePackReader#skipValue` with `Unknown MessagePack type: ...` (`MessagePackReader.scala:304`), and the `DynamicValue` codec with `Unsupported MessagePack type for DynamicValue: 212` (`MessagePackCodecDeriver.scala:559`).
2. Line 370: "Sealed traits and sum types are encoded as MessagePack maps with discriminator fields". A variant is the case index as an int followed by the case's value (`MessagePackCodecDeriver.scala:327-331`); only `Either` is a map (`{"left": ...}` / `{"right": ...}`), and `Option` is an array of 0 or 1 element. (That layout is itself a known bug; see the Library bugs entry "a derived variant is written as two MessagePack values".)
3. Lines 6, 50, 324 and 345: "27 primitive types". The page's own list under line 345 names 30, and `MessagePackCodecDeriver#derivePrimitive` maps 30 `PrimitiveType`s (`MessagePackCodecDeriver.scala:43-74`).
4. Lines 82, 109 and 480: errors "render as paths like `.field[0].nested`". A sequence index renders as `.at(1)` (`.emails.at(1)`), a map value as `.atKey(...)`, a case as `.when[Case]`.

**Reproduction** (Verified; Scala 2.13.18, `zio-blocks-schema` and `zio-blocks-schema-messagepack` 0.0.56, run with `scala-cli run --server=false RefPage.scala`):

```scala
//> using scala 2.13.18
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-schema-messagepack:0.0.56
import zio.blocks.schema._
import zio.blocks.schema.msgpack._

sealed trait Status
object Status {
  case class Active(since: String)    extends Status
  case class Inactive(reason: String) extends Status
  implicit val schema: Schema[Status] = Schema.derived
}

case class Contact(emails: Seq[String])
object Contact { implicit val schema: Schema[Contact] = Schema.derived }

object RefPage {
  def hex(bytes: Array[Byte]): String = bytes.map(b => "%02x".format(b & 0xff)).mkString(" ")

  def main(args: Array[String]): Unit = {
    // A variant is the case index (01) followed by the case's map, not a map with a discriminator
    println(hex(Schema[Status].derive(MessagePackFormat).encode(Status.Inactive("x"))))
    // 01 81 a6 72 65 61 73 6f 6e a1 78

    // ext types (fixext1 d4 here) are rejected by every reader path
    println(Schema[Int].derive(MessagePackFormat).decode(Array[Byte](0xd4.toByte, 1, 1)))
    // Left(zio.blocks.schema.SchemaError: Expected int value, got: 212 at: .)
    println(Schema[DynamicValue].derive(MessagePackFormat).decode(Array[Byte](0xd4.toByte, 1, 1)))
    // Left(zio.blocks.schema.SchemaError: Unsupported MessagePack type for DynamicValue: 212 at: .)

    // error paths render as .emails.at(1), not .emails[1]
    val bytes = Array(0x81, 0xa6, 'e', 'm', 'a', 'i', 'l', 's', 0x92, 0xa1, 'a', 0x01).map(_.toByte)
    println(Schema[Contact].derive(MessagePackFormat).decode(bytes))
    // Left(zio.blocks.schema.SchemaError: Expected string header, got: 1 at: .emails.at(1))
  }
}
```

**Expected.** The page states what the codec does: no ext support, the index-then-value variant layout (with the interop warning), 30 primitives, and paths such as `.emails.at(1)`.

**Root cause.** The page was written from the MessagePack spec and the JSON page, not from `MessagePackCodecDeriver`.

**Impact.** Medium: a reader planning interop with another MessagePack library is told variants are self-describing maps and ext types work.

**Suggested fix.** Correct the four claims; add a "Wire format" table (record = map by field name, exactly one entry per field; `Option` = array of 0/1; `Either` = one-entry map; other variants = index then value; sequences = arrays; maps = maps with codec-encoded keys); link the Library bugs entries on variants and duplicate keys by GitHub issue once filed.

## Docs tooling and examples

Docs build, mdoc and example-project problems (moved here from the old "Test and CI" section).

### mdoc: `mdoc:fail` on Scala 3 ignores earlier `:reset` sections

Seen 2026-10-20 while writing H-02. A `mdoc:fail` block after a `mdoc:silent:reset` reports bogus "already defined" errors for names from before the reset, so the expected compile error cannot be shown. H-02 described that error in prose instead. Pages that rely on `mdoc:fail` (H-31) should check their rendered output.

### mdoc: `mdoc:fail` and `mdoc:warn` also compile earlier `compile-only` blocks

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

### `schema-examples` has no scalafmt setup

Found 2026-10-20 while writing H-03. The standalone `schema-examples/` build has no scalafmt plugin or config, so `sbt scalafmtAll` in the root build does not format it. Sessions format example files by hand or with `cs launch scalafmt -- --config .scalafmt.conf`. Add the plugin (or a `.scalafmt.conf` symlink) to the example builds, or document the command in AGENTS.md.

### Example projects' tests never run in CI

Found 2026-10-20 while writing H-47. CI's "Compile example projects" step (`project/CiWorkflow.scala:360`, generated into `.github/workflows/ci.yml`) runs `sbt compile` in each `*-examples/` build, so `src/test` sources are never compiled or run. `sql-examples` is the first examples build with tests (H-47's `BookshopSpec`, 4 tests, passing locally against 0.0.56). Switching the step to `sbt test` (and regenerating `ci.yml`) would cover them. This is a CI change for the maintainer to decide; H-47's attempt to make it was blocked by a permission check, so it was left alone.

### Three example builds are not registered in the root build

Found 2026-10-20 while recording H-37. `config-examples` (T-05), `sql-examples` (T-07) and `html-examples` (T-09) are standalone builds with their own `build.sbt` but no `lazy val ... = RootProject(file("..."))` line or root `aggregate(...)` entry in `build.sbt`, unlike the other standalone `*-examples/` builds. (`projection-examples` is deliberately a root subproject, `lazy val \`projection-examples\` = project` at `build.sbt:560`, with no `build.sbt` of its own, so it is not affected.) CI's examples loop still compiles them (it looks for a `build.sbt`), so the gap shows up in the root aggregate and IDE import. AGENTS.md (updated on the `jwt` branch, H-37) now says a new examples build needs both. Add the three missing entries.

### Docs build memory with many sbt servers

Seen 2026-10-20: a full `sbt check` was OOM-killed while several worktrees each ran an sbt server (about 4 GB free). Shut down idle servers in finished worktrees with `sbt --client shutdown`.

### `schema-examples` and `streams-examples` have no scalafmt setup

Found 2026-10-20 while writing H-03. The standalone `schema-examples/` build has no scalafmt plugin or config, so `sbt scalafmtAll` in the root build does not format it. Sessions format example files by hand or with `cs launch scalafmt -- --config .scalafmt.conf`. The same gap exists in `streams-examples` (seen while writing M-04). Add the plugin (or a `.scalafmt.conf` symlink) to the example builds, or document the command in AGENTS.md.

### A subagent killed every sbt server on the machine

Seen 2026-10-20 around 23:40 (+0330) while H-30 was written: its examples-builder subagent ran `pkill -f sbt-launch`, which stops the sbt servers of every session on the machine, not only its own. Any session that lost its server around then should rerun its last sbt step. H-30 added a "never kill sbt by pattern" rule to the docs-examples skill; stop a server with `sbt --client shutdown` from its own worktree instead.

### htmx examples: the `zioBlocks` helper in `zio-blocks-htmx-examples/build.sbt` used the 3.10 artifact rule for 0.0.56

Found 2026-10-23 while writing T-10 (`docs/guides/add-interactivity-with-htmx.md`), from `origin/main` `a643b7cb0`. Same at tag `v0.0.56`.

**Summary.** The helper chose `zio-blocks-next-*` only for Scala 3.10 and later. Version 0.0.56 publishes the `next` artifacts for Scala 3.9 and later, which is the rule `http-model-examples` uses. On Scala 3.9.0 the examples therefore resolve `zio-blocks-http-htmx` instead of the `next` artifact.

**Reproduction.** Compare the `minor >= 10` line in `zio-blocks-htmx-examples/build.sbt` on main with the `minor >= 9` line in `http-model-examples/build.sbt`. The sonnet and Haiku runs both changed this line in their branches; the resolution output was not captured on main.

**Expected.** Both example builds use the same rule.

**Root cause.** `zio-blocks-htmx-examples/build.sbt`, the `zioBlocks` helper.

**Impact.** A latent artifact mismatch for the htmx examples on Scala 3.9.x. CI compiles the examples but cannot show which artifact name resolved.

**Test gap.** None: CI does not check which artifact is used.

**Suggested fix.** Change `minor >= 10` to `minor >= 9` in that helper, as the one-line fix in the T-10 branches does.

**Update (2026-10-29, H-57).** The comparison no longer holds: on `main` `9d8bb9d70`, `http-model-examples/build.sbt:23` also reads `minor >= 10`, the same rule as htmx. On Scala 3.9.0 both resolve the plain `zio-blocks-*_3:0.0.56` artifacts, which compile and run (the H-57 examples do), so nothing is broken today. The real gap is the docs-wide install-rule pass planned for the next release (see backlog, "Install-rule pass after the next release").

### streams-examples does not compile on Scala 2.13: `NioBinaryTelemetryWalkthrough.scala` uses `import zio.blocks.streams.*`

**Status (2026-10-29).** Fixed in #1897 (merged 2026-10-29). The scope was 18 files with `import x.*`, plus 12 Scala 3-only files that `build.sbt` now skips on Scala 2.x: 11 call zio-sbt-source 0.8.6 `ExprEval.show`, whose macro expands on 2.13 to the `private[sbt]` `SourceReader.commentsAbove` ("cannot be accessed"); `MixedFeedAuditWalkthrough` uses union types. The files stay in place because docs embed them by path.

Found 2026-10-26 while writing H-26 (`docs/guides/run-streams-without-blocking.md`), on `main` at `7f551627c`.

**Summary.** The AGENTS.md recipe for checking an example on Scala 2.13 (`sbt "++2.13.18!" compile` inside the examples build) fails for `streams-examples`. `streams-examples/src/main/scala/nio/NioBinaryTelemetryWalkthrough.scala` has the Scala 3-only `import zio.blocks.streams.*`, which 2.13 reads as "object * is not a member of package zio.blocks.streams", and every use after it fails too (`Stream.fromChunk`, `NioSinks`, `NioStreams`, `NioWriters`, `NioReaders`).

**Reproduction.** `cd streams-examples && sbt "++2.13.18!; compile"`.

**Expected.** The build compiles on 2.13, or Scala 3-only files are excluded on 2.13.

**Impact.** No example package in `streams-examples` can be checked on 2.13 without hiding that file first (H-26 used a local, uncommitted `Compile / unmanagedSources / excludeFilter` to compile its own package, which passed). CI doesn't compile the examples on 2.13, so nothing catches it. Only this file was confirmed; others may fail too.

**Suggested fix.** Change `.*` to `._` in that file (or add a Scala 3-only source filter), then compile the whole build on 2.13 once.

### docs build: `docs/mdoc` ran out of heap and still reported success, leaving `website/docs` incomplete

Found 2026-10-28 in one local run by the `remove-stale-datastar` session (worktree on `main` at `4e288c5f2`; sbt 2.0.8 launcher, JDK 25, sbt server heap `-Xmx6g`). Not seen on other machines or in CI so far.

**Summary.** `sbt --client -Dsbt.color=false "++3.9.0; docs/mdoc"` hit `java.lang.OutOfMemoryError: Java heap space` (with `[warn] In the last 10 seconds, 85.875 (908.9%) were spent in GC. [Heap: 0.02GB free of 6.00GB, max 6.00GB]`) after 785 s, and the run was reported as a success. `website/docs/reference/sql/*` and `website/docs/reference/streams/core/*` were never written, and a later `yarn build` failed with "These sidebar document ids do not exist", which hides the real cause.

**Caveat.** The run went through the short-lived `sbtq` wrapper (since removed), which printed the exit status. Whether plain `sbt --client` also returns 0 here, or whether mdoc swallows the error, isn't verified.

**Expected.** Any exception during `docs/mdoc` fails the command with a non-zero exit code.

**Impact.** A docs build can look green with pages missing, and the failure shows up later as an unrelated site-build error. CI's Build Docs job runs on a larger runner, so it may never hit this, but nothing guards against it.

**Test gap.** Nothing checks that every page under `docs/` (outside `docs/dev/`) gets an output file in `website/docs`.

**Suggested fix.** First reproduce with plain `sbt --client` and a small heap (`SBT_OPTS=-Xmx2g`) to find who swallows the error. Then make `docs/mdoc` fail on it, or add a post-mdoc check, for example in `docs/checkDocsOnFreshWebsite` or a website unit test, that every source page has an output.

### `scaladoc-coverage.py`: a multi-line annotation hides the Scaladoc, and `--drift` pairs members of different types

**Where found.** Running the coverage and drift gates for the maybe/combinators/context rollout, 2026-11-05, base `c2e4a39fd`.

- **Multi-line annotation.** `trait ContextHas` (`context/shared/src/main/scala/zio/blocks/context/ContextHas.scala`) has a full Scaladoc above `@implicitNotFound(` / `"..."` / `)`, but `_doc_before` skips only single-line annotations (`_is_annotation_line`), so the trait counts as undocumented: context reports 36/37 with every member documented. The annotation string is longer than `maxColumn`, so scalafmt keeps it multi-line.
- **Drift keying.** `_keyed` keys members by `"<kind> <name>"` and occurrence index within the file, ignoring the enclosing type. In `Maybe.scala`, Scala 2 has one `def apply` (`Maybe.apply`) while Scala 3 has two (`Present.apply`, then `Maybe.apply`), so `--drift` compares Scala 2 `Maybe.apply` with Scala 3 `Present.apply` and reports `def apply` although both `Maybe.apply` copies have identical text. (The same keying paired `MaybeOps.map` with `WithFilter.map`; those docs were made identical, see the report.)

Suggested fix: in `_doc_before`, walk back over a balanced multi-line annotation (a line starting with `@name(` up to its closing `)`); in `scan`, record the enclosing type names and key drift by `(enclosing path, kind name, index)`, falling back to the current key when the paths differ only by a version-specific wrapper. Add both cases to `test_scaladoc_coverage.py`.

### jwt Scaladoc: the `Jwt` object example ends in a bare expression, so `scaladoc-check` warns "pure expression does nothing"

Found 2026-11-06 by the maybe/combinators/context session while running `scaladoc-check` on `main` `07f71de18` (after #1947). The `Jwt` object's "Minimal usage" block (`jwt/shared/src/main/scala/zio/blocks/jwt/Jwt.scala`, the `{{{` block above `object Jwt`) ends with `decoded.sub // Some(user-42)`. In the generated snippet (`Snippet_jwt_shared_scala_Jwt_L335`) that line is a statement whose value is unused, so every Scala version warns ("A pure expression does nothing in statement position; you may be omitting necessary parentheses", twice on 2.13). The check still passes, but the warning shows up in every module's gate log. Suggested fix: `println(decoded.sub) // Some(user-42)`, or bind it (`val subject = decoded.sub // Some(user-42)`), matching how the other modules end their examples.

### schema-xml Scaladoc: the `syntax` examples give 18 unused-import warnings in `scaladoc-check`

Found 2026-11-06 by the maybe/combinators/context session while running `scaladoc-check` on `main` after #1948 (`04b56bc92`). The examples in `schema-xml/shared/src/main/scala-3/zio/blocks/schema/xml/syntax.scala` and its `scala-2.12-2.13` copy (snippets `Snippet_schema_xml_shared_scala_3_syntax_L31`, `_L53`, `_L64`, `_L92`, `_L107`, `_L122` and the matching `scala_2_12_2_13_syntax` ones) each import something they don't use, giving 18 unused-import warnings across the three Scala versions. The check still passes, but the warnings appear in every module's gate log and hide new ones. Suggested fix: drop the unused imports in those examples (the wrapper already imports `zio.blocks.schema.xml._`), keeping the two copies identical.

**Addition (2026-11-07, scaladoc-cleanup).** The suggested fix is wrong: the unused import is the snippet wrapper's own `import zio.blocks.schema.xml._` (`project/ScaladocSnippets.scala`), not one in the examples. The examples need `import zio.blocks.schema.xml.syntax._`, which makes the wrapper's import unused. Changing the examples to a bare `import syntax._` would hurt copy-paste. Fix in the tooling instead: have the wrapper suppress unused-import warnings for its own import (for example `@nowarn("msg=Unused import")` or a scoped `-Wconf`), then re-run `scaladoc-check` on 3.9.0, 3.10.0 and 2.13.18. Left open after #1966, which is comments only.

### schema Scaladoc: eight files still point readers to `docs/dev/issues.md`

Found 2026-11-06 by the schema core Scaladoc session on `main` `07f71de18`. The Scaladoc of `CompanionOptics`, `DerivedOptics`, `IntoVersionSpecific`, `PathInterpolator`, `SelectorMacros`, `MigrationCompanionVersionSpecific`, `DynamicSchema` and `Into` says "see docs/dev/issues.md". That file is now only an index (the entries live in `docs/dev/library-issues.md` and `docs-issues.md`), the website excludes `docs/dev/`, and the style guide forbids `docs/dev/` references in Scaladoc, so the pointer leads nowhere for API readers. Suggested fix: replace each with plain text ("a known bug in 0.0.56") or a GitHub issue link, keeping version copies identical (a comment-only follow-up PR).

## Resolved

- Reference-page errors in eleven modules fixed in #1967 (merge `e4827f867`, 2026-11-07), each re-checked against the code, the reviewed Scaladoc and 0.0.56 probes: config `config-source.md` (why `SysPropSource` is empty on Scala.js); sql `db-codec.md` (`derivedOpaque`/`dbCodecFromAs`), `db-value.md` (`DbArray`, `DbNull`), `repo.md` (`insertReturning` with a placeholder ID), `table.md` (`Table#columns`), `transactor.md`/`sql-logger.md`/`transactor-zio.md` (three statements); async "Direct Style", "Depth Limits on Waiting Values" and "Completer"; `jwt.md` (values dropped during parsing, the tested curve-check cases); `combinators.md` (nine items); `maybe.md` (five items); `projection/testing.md`; telemetry `common/index.md`, `metrics/metric-data.md` and `metrics/index.md`, `otel/index.md` and `custom-exporter.md`, `logging/` and `tracing/index.md`; ringbuffer `spsc.mdx` (`NaN`); mux `mux.mdx` (`MuxError`). 20 entries.
- Web reference-page errors fixed in #1964 (merge `21bcc6ee7`, 2026-11-07), each re-checked against the code and the reviewed Scaladoc: http-model `model.md`, `headers.md` and `server-sent-event.md` (eleven statements); http-model `schema-codecs.md` and `schema.md` (overrides on record fields, non-standard UUIDs); `html.md` (the `id="user-$age"` example, the throwing "Template Composition" example, `Dom#filter`, multi-valued attributes/override/`accept`, the JavaScript escape descriptions, smaller errors); htmx `attribute-values.md` "Extending with Custom Types" and smaller errors in `hx-swap.md`, `hx-target.md`, `index.md`; datastar `attributes.md` (`DatastarAttrKey` constructor), `events.md` (`threshold` percentage), `sse.md` (`Outer`/`Inner` morph); endpoint `endpoint.md` (first `out`/`outError`), `bulk-creation.md` (auto-names, Scala/artifact rule), `auth-type.md` and `http-codec.md`. 16 entries.
- Nine standalone example builds that didn't compile on Scala 2.13 (found 2026-10-29 by #1897): fixed in #1936 (merge `0c0021d61`, 2026-11-04), one commit per build. endpoint, http-model, mux, smithy, scope, schema, async, openapi and telemetry now pass `sbt "++2.13.18!" compile` (config, html, jwt and streams already did). Milad's decision: no example code was rewritten; each `build.sbt` skips Scala 3-only files on 2.x (the #1897 mechanism: a content regex plus per-file and per-package sets), so endpoint and scope compile almost nothing on 2.13 (nearly every file uses `@main`), and the generated smithy/openapi apps compile nothing on 2.x. Correction to the entry: "expected start of definition" came from `@main def`, not `import x.*`. The real 0.0.56 API gaps behind the other failures were already filed (telemetry `log.*` and `GlobalLogState`, `json"..."`, `yaml"..."`, `MigrationBuilder`); the async `Async.promise` difference is a new Usability traps entry (the Scala 2 body must bind the completer). Not done: a 2.13 compile step for the examples in CI (needs Milad's OK); trying the codegen's `EmitterConfig.scala2` to make the generated smithy/openapi apps 2.13-compatible. Opus review 8 findings, 3 applied, the rewrite-`@main` finding declined per Milad's decision.
- `wire.md` had no mdoc-checked code blocks (found 2026-10-30 by #1917): fixed in #1934 (merge `5eb809c8f`, 2026-11-04) for all six resource-management pages. Plain ```` ```scala ```` fences 63 → 48 (wire 17 → 8, resource 19 → 18, scope 18 → 13); the rest are signatures, sbt lines, pseudocode and 5 deliberate-error fences that stay plain because `mdoc:fail` on this page also compiles the earlier blocks and reports "Conflicting definitions" (the known mdoc entry above); their error messages were confirmed in an isolated probe page. Compiling them found three `wire.md` errors, fixed in the same PR: the `Wire#isShared`/`Wire#shared` examples used `Wire.shared[String]` ("Cannot derive Wire for scala.Predef.String: not a class"); the `Wire#toResource` example passed a `Context[Config]` where `Context[String]` was needed; the motivation's manual-wiring example passed a scoped value to a constructor, which the scope checker rejects. Also fixed a Scala 3.9 pattern-narrowing warning at `scope.md:914`. Review: 0 blocking, 7 minor, 6 nits, all applied. Merged while GitHub Actions was backed up, so its PR CI never ran.
- `typeid.md`: four claims that contradicted the library (found 2026-11-04 by the typeid Scaladoc rollout, #1924) fixed in #1933 (merge `6ee89c6b8`, 2026-11-04): (1) derived `TypeParam`s record name, index and variance only, always `Kind.Type` and unbounded (a known bug in 0.0.56), with a `TypeParam.higherKinded` example and the Kind table fixed; (2) `normalize` follows aliases only and leaves opaque types as they are, with an example; (3) `construct` builds `List`, `Vector`, `Set`, `Seq`, `IndexedSeq` and `Map`, while `Chunk`, `Array` and `ArraySeq` give `Left`, with an example; (4) `unapplied(TypeId.of[List[Int]])` equals `TypeId.list` on Scala 3. Probed against 0.0.56; Opus review 6 findings, 6 applied. Merged while GitHub Actions was down, so its PR CI never ran.
- JdkHttp adapter: an `Error` in a handler (e.g. `ExceptionInInitializerError`, `UnsupportedClassVersionError`) closed the connection with no response and no log line, because the adapter caught only `NonFatal` (found 2026-10-29 by H-57). Fixed in #1923 (merge `aa7023da6`, 2026-11-04): both catch sites are now `case error @ (NonFatal(_) | _: LinkageError) =>`, so the error is logged and answered with 500; `VirtualMachineError` and `InterruptedException` still propagate. Applied to all 10 copies (8 `JdkHttp.scala`, 2 `StreamingJdkHttp.scala` in http-model, jwt, capstone, htmx and datastar examples) and the inline code in 5 guides; the serving guide explains it. Verified with the entry's reproducer: before, `GET /price -> Failure(IOException: HTTP/1.1 header parser received no bytes)`; after, `Handler failed: java.lang.ExceptionInInitializerError` and `GET /price -> Success(500 Internal Server Error)`. **Known limit, kept on purpose (Milad):** the 500 body is built with http-model classes (`Response.text` → `Body.fromString` → `Stream`), so when the broken class is http-model or streams itself (the http-model 0.0.56 jars on JDK 17, `UnsupportedClassVersionError`), building the 500 throws again and the client still sees an empty reply; the log line now names the cause. The serving guide states this. A JDK-only 500 fallback would close it but adds code to every copy; not done.
- Three scope reference pages that contradicted the code and the fact-checked scope Scaladoc (#1916) fixed in #1917 (merge `31563a0e1`, 2026-11-03), each confirmed first against source and with scala-cli probes on 0.0.56 (Scala 3.9.0, 2.13.18, Scala.js 1.22.0): `resource.md` no longer says `Resource.from[T]` always registers `close()` (the no-argument form skips an `AutoCloseable` whose constructor takes a `Finalizer` or `Scope`, a known bug; the wires form does close it) and no longer calls `Resource.shared` "lock-free" or its initializer "exactly once"; `wire.md` no longer promises a fresh instance from every unique wire (Scala 3 treats an explicit `Wire.unique` as shared), says `fromFunction` never closes an `AutoCloseable` result, and its four `Context.empty[Any]` blocks are now compiled `mdoc:compile-only` blocks; `scope.md` states the Scala.js behaviour of `Scope.global` and `isOwner`. `guides/resource-and-di-migration.md:441` got the same `Finalizer`/`Scope` exception. Still open: the rest of `wire.md` has no mdoc-checked blocks (Docs tooling entry).
- Four `docs/reference/chunk.md` errors found by the chunk Scaladoc fact-check (#1908) fixed in #1913 (merge `c5cc1ec96`, 2026-10-30): `ChunkBuilder` was described as a buffer that feeds a tree; `dropUntil` was said to keep the matching element; the `reduceMapLeft`/`reduceMapRight` examples reduced a one-element chunk (now `NonEmptyChunk(10, 20, 30, 40)` and `(1, 2, 3, 4)`); the "Bitwise Operations" section documented private methods (removed). The same PR replaced the 23 Zionomicon sample-data lines in the chunk Scaladoc and changed `scaladoc-style.md:58` to the new sample-data rule.
- Batch 5 doc errors fixed in #1914 (merge `610a89803`, 2026-10-30), each confirmed first by a fresh-context Opus fact-check with probes on 0.0.56: (1) `instrument-a-program-with-logs-traces-and-metrics.md:562` said exporting to an OpenTelemetry Collector "isn't possible yet" (it is, from the public `OtlpJsonEncoder` and `HttpSender`; the page now links the custom-exporter reference); `telemetry-guide.md` Part 5 now uses only public API (the package-declaration trick survives only in a warning), and its uncompilable plain block, its Part 4 `new OtlpJsonMetricExporter`, the shutdown order and the FAQ were fixed; (2) `NioBinaryTelemetryWalkthrough` section 6 no longer says its replay-window bug was "since fixed" (it still fails on 0.0.56, a known library bug), and three references to a nonexistent `NioSpec` test were removed; (3) `reference/http-model/model.md` cookies now use ZIO Blocks' `Chunk` and render the header, say `SameSite` has no default attribute, and describe `Lax` as cross-site top-level GET only.
- `netlify.toml` redirects never reached zioblocks.com (the deploy job uploads the build without the repo, so `[[redirects]]` were never sent), so `/docs`, `/docs/*` and the old install-guide URL returned 404 (found 2026-10-30 after the post-deploy check). Fixed in #1912 (merge `a47f4502a`): every rule now lives in `website/static/_redirects`, a unit test guards them, and `site.yml` checks one redirect after each deploy. Deployed 2026-10-30 (run 41050926189, which passed its new redirect check); live: `/docs` → 301 `/overview`, `/docs/guides/your-first-schema` → 301 `/guides/your-first-schema`, `/guides/add-zio-blocks-to-your-build` (with and without `/`) → 301 `/getting-started/installation`.
- `guides/validated-newtypes.md:362` ("Use Scala 3 Opaque Types") and `schema-examples/.../validatednewtypes/OpaqueTypesExample.scala:27` built `given Schema[Isbn] = Schema[String].transform(...)` inside the opaque scope, so on Scala 3.3 `Schema[String]` resolved to the given itself and the example hung (found by H-58, 2026-10-29). Fixed in #1906 (H-58, merge `b574f40f5`): both use `Schema.string.transform(`, with one sentence explaining why; verified on 3.3.7. The general trap stays in library-issues.md → Usability traps.
- Fixed in #1890 (docs cleanup, merge `0959c8abb`): `reference/sql/repo.md` presented `Repo` as a public concrete class (it is abstract with a protected constructor) and said its SQL is built once at construction (only identifiers are cached; each call builds its statement). `query-dsl-sql.md` had unbalanced parentheses in two hard-coded output comments and an incomplete `ArithmeticOperator` list. `query-dsl-fluent-builder.md` pointed at files the page doesn't use. The Scope tutorial had a malformed mdoc fence. Three `undocumented-report.md` figures were wrong. `ddl.md` and `repo.md` now call the missing `PRIMARY KEY` a known bug in 0.0.56, and the object-hash outputs in `db-codec.md`, `db-codec-deriver.md`, `toon.md`, `ddl.md` and `repo.md` are gone.
- `docs/guides/write-joins-and-inspect-generated-sql.md` (H-19) gave an unnamed dump file as `_local_<Object>__query-<dialect>.sql`; the real name has three underscores (`_local_<Object>___query-<dialect>.sql`). Fixed in #1889 (merge `bafc31bef`), which also added the `SqlQuery`, `Rel` and `Dump` reference pages and names the four query-layer bugs as known bugs in 0.0.56.
- Scaladoc fixed in #1891 (merge `fa76f487f`): the `Stream#repeated` Scaladoc (`Stream.scala:713`) said `Stream.eval` runs once "with both terminal families"; it now says once under the blocking terminals and every cycle under the asynchronous ones, and that an empty cycle loops forever only in a blocking pull. The `Json` (`Json.scala:40`), `JsonCodec.bigIntCodec` (`:919`) and `bigDecimalCodec` (`:955`) Scaladoc stated the digit limit off by one; they now say 308 or more digits are rejected and up to 307 are accepted. With these, the Documentation errors section has no open entries.
- `reader.md`/`writer.md` contradicted the code on `isClosed` after `reset()`, the `AnyRef` lane, error-closed writers, `Writer.single` and `concat` (seven items, from the wave 2 streams Scaladoc): fixed in #1881. Its one leftover, that the `NioWriters.fromChannel` Scaladoc should describe the close-after-failed-write leak, was already done on `main` (`NioWriters.scala:72-84`, checked 2026-10-28).
- Data doc errors fixed in #1887 (batch 4b, merge `5779e440d`, three review rounds): `db-codec.md` said non-optional types throw on SQL `NULL` (`String` gives `null`, numbers `0`); `sql-zio.md` and `transactor-zio.md` (JDBC calls on ZIO's default pool, undefined names, `:memory:` examples that can't work; both pages now compiled mdoc); `table.md` and `db-codec-deriver.md` on `createTable` with collection and nested-record fields; the `Frag#queryChunked` Scaladoc's `Transactor.connect(transactor)`; the "schema / optics and paths" bullets (`path-interpolator.md` escapes and `.one`, `OpticCheck`, map-key `toString`); the "schema-toon (`toon.md`)" bullets, including today's three (`KeyFolding.Safe`, `ReaderConfig.withDelimiter`, inline/tabular forms); and the `stream-files-and-sockets.md` "Scala 3.7" install line. The sql, optics and toon library bugs those pages name carry a Status line.
- Fixed in #1886 (merge `6462ef6e9`): a stale `docs/reference/datastar.md`, deleted by #1638 and re-added by the #1672 squash merge, shadowed `docs/reference/datastar/index.md`, so no Datastar overview fix since 2026-08-28 reached the site. `onDuplicateRoutes: 'throw'` didn't fire because Docusaurus compares routes as exact strings: `/reference/datastar` and `/reference/datastar/` differ only by the trailing slash, yet both write `reference/datastar/index.html` (the stale copy won in production). The PR deletes the file and adds `website/test/unit/page-beside-folder-index.test.mjs`, which fails on any `docs/**/X.md` next to `docs/**/X/index.md(x)` (140/141 before, 141/141 after). Checked in the PR's CI `website-artifact`: `reference/datastar/index.html` now contains `zio-blocks-next-datastar` and "Scala 3.x only". Goes live with the next deploy.
- Runtime doc errors fixed in #1885 (batch 4a, merge `ac8a1d76a`, three review rounds): config `formats.md` (infinite HOCON numbers are kept as written strings; the 1 MiB `fromFile` limit and `fromFileWithLimits`; the include-callback and `HoconError` position claims, plus warnings for the config-hocon known bugs) and the flags/rollouts bullets (`rollout.md` share and sum-above-100 wording, `flags.md` exception wording, the `DynamicFlag` Scaladoc example); the scope bullets and the Scope tutorial entry (signatures, `andThen`, error text, `wire.md`, `finalization.md`, thread ownership, Scala 3 `Unscoped` derivation, single-use `Resource.from`, plus leftover cycle-detection claims in two guides); `context.md` `Wire.make`; the telemetry JDK 21, 3.9 artifact and double-printing items (and the `HistogramDataPoint` boundaries Scaladoc); `telemetry-guide.md` `log.removeAll()` teardown; the `B3Propagator.multi` Scaladoc header list; the async Scala 3 `Completer` warning; and the T-12 entry in the `docs/index.md` Guides list. The library entries those pages now name carry a Status line.
- Streams doc errors fixed in #1881 (batch 1, merge `c8c65babe`, three review rounds): `stream.md` (`repeated` described as rematerializing, a warning box quoting replaced `mapPar` Scaladoc, the wrong `fromAcquireRelease` output, the eager `FileReader`, an unreachable Scala.js merge path, the `bufferSize` claim, and no Long/Double count note in "The Reader Protocol"); the worker-thread-name, virtual-thread-fallback, Scala.js `ReadableStream`, `NioReaders.fromChannel`, `NioWriters.fromByteBuffer` and full-buffer `NioSinks` statements in `platform-differences.md`, `reader.md`, `writer.md` and `sink.md` (the thread-name text changes again once that library bug is fixed); `zero-boxing.md` saying `Sink` has unboxed `write` methods (it now also names the accumulator boxing as a known bug); `streaming-library-migration.md` telling readers to pick a sentinel on every lane; and the `SinkSentinelGuardExample`/`SinkSentinelLimitationExample` pair, replaced by `streams-examples/src/main/scala/sink/SinkSentinelExample.scala`, with a `NioStreamsSinksSpec` case that writes `Long.MaxValue` and `Double.MaxValue`. The same PR documents the two `Stream#start` findings from H-53.
- Already fixed on `main`, moved here on 2026-10-28 after checking each page: `docs/reference/mux.mdx` registry and single-consumer wording (#1870, H-29); the `openapi.md` signature, install, round-trip, `Parameter`/`Operation` and extensions bullets (H-39); the `http-model/schema.md` `RequestSchemaOps`/`ResponseSchemaOps` and "Supported Types" bullets (#1879); the `binding.md`, `registers.md`, `binding-resolver.md` and `Registry#bind`/`StructuralReflection` Scaladoc bullets (schema Scaladoc wave 3).
- Schema-core doc errors fixed in #1877 (batch 2a, merge `edd42b4a2`, three review rounds): `maybe.md` said flat primitive `Maybe`s allocate nothing (they are stored as JVM boxes; only cached small values are free); `typeid.md:1927` listed named tuples under `TypeRepr.Tuple` (a named tuple is an applied `NamedTuple`, labels `None`); `as.md` called dropping a one-sided `Option` field a clean round trip (`Some(v)` comes back `None`; `into.md` qualified too); `migration.md` and the ZIO Schema migration guide called `DynamicMigration` serializable or "plain data" (no `Schema`, so no codec); `dynamic-schema.md` and three `schema-examples` comments said a `DynamicSchema` can be stored as JSON and that `BindingResolver.defaults` covers `Option` (now a compiled Avro storage subsection, a compiled `Option` binding, and the `toSchema`/`check` JSON caveat); `data-migration.md` promised that `fence()` stops producers and that `complete()` migrates every queued row, and its Full Example did not compile (rewritten after H-23, run against PostgreSQL 16 and SQLite). `schema.md` gained a compiled "Tuples" subsection and the named-tuple `given` rule and known bugs; those library entries stay open with a Status line.
- Codec doc errors fixed in #1878 (batch 2b, merge `13423e664`): `avro.md` now has the Scala-to-Avro type table and the decode-trusts-the-reader-schema note, and names the three Avro library bugs; `yaml.md` (now evaluated mdoc) no longer shows `YamlOptions` output from `encodeToString` or a `---` line it never prints, and documents kebab-case keys; `csv.md` no longer promises document-level error rows and columns, marks `CsvError.TypeError` and `CsvConfig.nullValue` as unused, and states the nested-record behaviour; the whole "schema / JSON" list (the `require*` encoder effect in `customize-json-encoding.md`, `transient` on decode in `modifier.md`, `schema-error.md` messages and first-failure behaviour, `syntax.md` `show` output, `validation.md` rewritten as evaluated mdoc, `json-schema.md:366` and `pattern` anchoring, `json/index.md:66-67`, the `json"..."` Scala 2.13 note and `toKV` output in `json.md`, and `json-selection.md:92`); the `Schema.fromJsonSchema` Scaladoc was already correct on main. Also fixed there: `schema-bson/README.md` showed class-level `@Modifier.rename`/`alias` on a record, which the BSON deriver ignores (probed against 0.0.56: a record with class-level `rename("NewName")` still encodes as `{"x": 1}`; on a sealed-trait case, `rename("Round")` gives `{"Round": {...}}` and `alias("Circle")` decodes), and the README now says `withNativeObjectId` has no effect and shows the `Option` sub-document shape. The library-bug entries for Avro, YAML, CSV and BSON stay open.
- Web doc errors fixed in #1879 (batch 3, merge `1a5561f65`): `reference/datastar/index.md` claimed Scala 2.13 support (the module is Scala 3 only) and lacked the 3.9+ `zio-blocks-next-datastar` + `-experimental` install line (live only after #1886 removed the stale `docs/reference/datastar.md`); `reference/htmx/response-headers.md` paired `HX-Redirect` with `HX-Trigger-After-Settle`, which htmx 2 never fires after a redirect (browser-verified); `reference/htmx/index.md` never said its syntax targets htmx 2.x; and the `JdkHttp` adapter (7 copies plus the 2 `StreamingJdkHttp` copies) wrote the response outside its `catch`, so a failed write dropped the connection silently (now a logged 500). The same PR documented the htmx `HX-Boosted`/history-restore traps, the html shadowing and `search` ambiguity traps, and the docs side of the `HxLocation` and four http-model-schema bugs (those entries stay open with a Status line).
- `docs/index.md` described `Maybe` as "Low-allocation optional values backed by `null`"; since #1532 absence is an `Absent` singleton and `Maybe.present(null)` is present. Fixed in #1866 (H-51, commit `1dfba0647`); regenerate the README.
- `telemetry-guide.md` propagation examples started a new trace instead of continuing the caller's: the HTTP server example took its tracer before `trace.install`, and `traceIncoming` extracted the parent but never scoped it. The "Span-log correlation" line called the `private[telemetry]` `new TracerProvider(...)`, and `reference/telemetry/otel/index.md` listed `X-B3-ParentSpanId` and `X-B3-Flags` as headers `B3Propagator.multi` writes (it writes only TraceId, SpanId and Sampled). Fixed in #1853 (H-43, commit `4df2713bc`). Still open: the same wrong header list in the Scaladoc at `otel/src/main/scala/zio/blocks/telemetry/otel/B3Propagator.scala:41-42` (backlog doc-fix follow-up).
- `reference/http-model/headers.md:660` said `ServerSentEvent` uses `Headers` indirectly through `Response`; it renders a `String` and touches neither. Fixed in #1842 (H-41), which also links the SSE guide from that sentence.
- JSON number docs fixed in wave 8b of the schema Scaladoc (#1843, `JsonReader`/`JsonWriter`): `json.md:18` and `:26` said `Json.Number` has "arbitrary precision" and preserves precision for financial and scientific data, but `Json.parse` and the `BigDecimal` codec round to 34 significant digits (`MathContext.DECIMAL128`) and reject 308 or more digits (`Json.scala:2351`, `JsonReader.scala:3125`, `:5243-5254`, `JsonCodec.scala:875`; `Json.parse("1234567890123456789012345678901234567890")` prints `1.234567890123456789012345678901235E39`). The Scaladoc of `JsonReader.bigIntDigitsLimit`/`bigDecimalDigitsLimit` (308) and the `digitsLimit` parameters called 308 "the maximum number of digits", but the check is `len >= digitsLimit`, so 308 digits are rejected and the scale limit 6178 is exclusive too (`JsonReader.scala:2922`, `:3020`, `:3062`, `:3126`). `JsonWriter` Scaladoc: `writeKey(x: Instant)` was described as writing a `Duration`, `writeVal(x: Char)` as writing "a JSON key", and `writeObjectStart`/`writeObjectEnd` as array markers. `JsonReader` Scaladoc: `readStringAsBigDecimal` said it reads "a JSON number value" (it reads a string), and `readBigInt(digitsLimit)`/`readStringAsBigInt(digitsLimit)` said they throw on "the default limit" (they use the given one).
- JSON reference pages and Scaladoc fixed in wave 8a of the schema Scaladoc (#1841): `json-config.md` taught `implicit val nameMapper` in the companion, which nothing reads (now `@Modifier.fieldNaming`, with `withFieldNameMapper` named as the other way), listed only three `NameMapper`s, gave `preferredCharBufSize` a default of `8192` (it is `4096`) and left out the other three `ReaderConfig` options, showed `withCheckForEndOfInput(false)` as allowing trailing whitespace (whitespace is always allowed; it ignores text after the value), said `checkForEndOfInput` "adds one comparison", and listed `Strict`, `Right` and `Left` merge strategies, which do not exist (`MergeStrategy.scala` has `Auto`, `Shallow`, `Concat`, `Replace` and `Custom`). `json-patch.md` said that `JsonPatch.diff` emits `Op.Nested` and array `Modify` steps; it emits neither (`JsonDiffer.scala`). The `Json` Scaladoc said numbers are stored as a `String` (they are a `BigDecimal`), the `JsonSelection` Scaladoc used removed `asArrays`/`asStrings` methods (already fixed on `main` at `67a9be9ff`), and the `ValidationOptions` Scaladoc said formats are annotation-only by default (they are checked). In the wave 8a review round: `json.md` said objects use a `Vector` (a `Chunk`), that `Auto` and `Shallow` concatenate arrays and that a `Deep` strategy exists (`Auto` merges arrays by index, `Shallow` takes nested values from the right), and showed the wrong out-of-bounds message (`Index 5 out of bounds (size: 1) at: [5]`); `json-selection.md` showed `"field not found"` (`Key 'age' not found`); `json-patch.md` and `json-differ.md` called the diff round trip unconditional (it fails for duplicate keys, see Library bugs).
- streams overview (`streams/index.md`) marked `fromInputStream` and `fromJavaReader` as JVM-only, but both run on Scala.js. Fixed in #1796 (H-24).
- docs: `zio-blocks-next-*` needs Scala 3.9+ (plus `-experimental`), not 3.7+. Fixed across 20 guides and 14 example builds in #1810 (H-01). Leftovers on `main`: `guides/stream-files-and-sockets.md:80` still says "Scala 3.7 and newer", and 14 example builds still turn on `-experimental` at `minor >= 8`.
- Codecs: `format.md` (`sealed trait`, real `BinaryFormat`/`TextFormat` signatures, which formats are binary); TOON `ArrayFormat.Auto` Scaladoc and `toon.md:706`; `csv.md` (`CsvConfig` ignored by derived codecs, runtime rejection, `Option` rejected); `xml.md` stale version; `built-in-codecs/index.md` artifact prefix. Fixed in #1773.
- `json-patch.md:608` said that converting a `JsonPatch` to RFC 6902 is a matter of encoding it with its `Schema` and reformatting. The encoded patch holds number deltas and string edits that RFC 6902 cannot express, so the conversion needs the source document to compute `replace` values and must track list indices as earlier operations shift them. No other page or Scaladoc claims RFC 6902 compatibility. The sentence now says this and links the H-07 guide's converter. Fixed in #1815 (H-07).
- `transactor-zio.md` mentioned `JdbcTransactor.postgresLayer` without saying it is an extension that needs `import zio.blocks.sql.zio.*` (found while writing H-17). Fixed on the H-21 branch `docs/h21-sql-from-zio`.
- `type-class-derivation.md` "Customizing Derivation with Instance and Modifier Overrides": all four examples were uncompiled plain blocks, one with a `// ... AST overrides ...` placeholder and undefined types. Each is now a self-contained `mdoc:compile-only` block with the real JSON AST overrides. Fixed in #1787.
- `jwt.md`: audience mismatch documented as `InvalidToken` (it is `InvalidClaim("aud", ...)`), missing `InvalidClaim`/`InvalidKey` rows, no key-format guidance, no frontmatter. Fixed in #1789 (H-37).
- `sql-transactions.md`: the Hikari recipe, "JDK 25+" virtual-thread claim and "pool size equals database max connections" advice were replaced by a link to H-17's guide (virtual threads are final in JDK 21, JEP 444; JEP 491 shipped in JDK 24; pool sizing must keep the sum of all pools under `max_connections`). Also fixed: `.query[Int].toOne` (no such method; `queryOne[Long]`) and the unsupported "read-only avoids WAL writes" claim. Fixed in #1790 (H-17).
- `codec.md` never mentioned `Deriver#withInstance`/`withModifier`, and its BinaryCodec/TextCodec paragraph was wrong (`CsvCodec` extends `TextCodec`; XML and YAML extend `BinaryCodec`; `BsonCodec` extends neither). New "Overriding a Type on a Deriver" subsection and fix in #1784 (merged).
- Stale example: `schema-examples/src/main/scala/deriveroverrides/CompleteExample.scala` predated #1348 and showed no codec override. Rewritten in #1781 to demonstrate type, term-name and optic overrides with a round trip and the current run command.
- Docs build: a broken relative link in `docs/reference/schema/modifier.md:395` (`../guides/` instead of `../../guides/`), added by #1775, failed Build Docs on main. Fixed in #1772.
- Config: `formats.md:97` (YAML parse error type) and `config-source.md` (`EnvSource` and `keyFormat`). Fixed in #1771.
- sql: `sql-transactions.md` default isolation and PostgreSQL facts; `repo.md`/`table.md`/`ddl.md` upserts, `pageAfter` and the missing `PRIMARY KEY` caveat; `:memory:` warnings in `transactor.md` and `transactor-zio.md`; `db-con.md` `DbTx` members; the backlog's "sql has no JS backend" note. Fixed in #1760.
- Codecs: `format.md` (`sealed trait`, real `BinaryFormat`/`TextFormat` signatures, which formats are binary); TOON `ArrayFormat.Auto` Scaladoc and `toon.md:706`; `csv.md` (`CsvConfig` ignored by derived codecs, runtime rejection, `Option` rejected); `xml.md` stale version; `built-in-codecs/index.md` artifact prefix. Fixed in #1773.
- `patch.md`: every snippet was a plain uncompiled fence; five sections did not compile (`editString` with `Vector`, `modifyKey` with a forward-referenced implicit, `Patch[Data] ++ Patch[Counter]`, `val new`, a non-existent `JsonEncoder`), and the Strict, Clobber, `Patch#apply`, Prism-`set`, LazyList and string-delta claims contradicted the source. The snippets are now mdoc blocks and the claims are corrected; see the H-07 entry "docs: `patch.md` has six snippets that don't compile and several claims the source contradicts". Fixed in #1828.
- `into.md` said that the numeric widening instances "cannot lose information" (`Int` and `Long` to `Float`, and `Long` to `Double`, round large values) and that `Into.derived` matches coproduct cases by name only (it falls back to field signature and then to position; see the entry on `Into.derived` and case positions). Both sentences are corrected. Fixed on the wave 7 schema Scaladoc branch `scaladoc-wave-7`.
