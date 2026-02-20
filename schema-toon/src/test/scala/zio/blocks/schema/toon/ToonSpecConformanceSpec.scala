package zio.blocks.schema.toon

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.toon.ToonTestUtils._
import zio.test._

/**
 * E2E conformance tests against TOON Specification
 * https://github.com/toon-format/spec
 *
 * These tests verify that our implementation produces output matching the
 * official TOON specification fixtures and examples.
 */
object ToonSpecConformanceSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("TOON Spec Conformance")(
    suite("inline arrays")(
      test("encodes string arrays inline") {
        encode(Tags(List("reading", "gaming")), "tags[2]: reading,gaming")
      },
      test("encodes number arrays inline") {
        encode(Nums(List(1, 2, 3)), "nums[3]: 1,2,3")
      },
      test("encodes empty arrays") {
        encode(Items(List.empty), "items[0]:", deriveCodec[Items](_.withTransientEmptyCollection(false)))
      },
      test("encodes empty string in single-item array") {
        encode(Items(List("")), "items[1]: \"\"")
      },
      test("encodes empty string in multi-item array") {
        encode(Items(List("a", "", "b")), "items[3]: a,\"\",b")
      },
      test("quotes array strings with comma") {
        encode(Items(List("a", "🚀,🚀", "d:e")), "items[3]: a,\"🚀,🚀\",\"d:e\"")
      },
      test("quotes strings that look like booleans in arrays") {
        encode(Items(List("x", "true", "42", "-3.14")), "items[4]: x,\"true\",\"42\",\"-3.14\"")
      }
    ),
    suite("tabular arrays")(
      test("encodes arrays of uniform objects in tabular format") {
        encode(
          ItemsTable(List(ItemRow("A1", 2, BigDecimal("9.99")), ItemRow("B2", 1, BigDecimal("14.5")))),
          """items[2]{sku,qty,price}:
            |  A1,2,9.99
            |  B2,1,14.5""".stripMargin,
          deriveCodec[ItemsTable](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("encodes null values in tabular format") {
        encode(
          ItemsNullable(List(ItemNullable(1, None), ItemNullable(2, Some("test")))),
          """items[2]{id,value}:
            |  1,null
            |  2,test""".stripMargin,
          deriveCodec[ItemsNullable](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("quotes strings containing delimiters in tabular rows") {
        encode(
          ItemsWithSkuDescQty(List(SkuDescQty("A,1", "cool", 2), SkuDescQty("B2", "wip: test", 1))),
          """items[2]{sku,desc,qty}:
            |  "A,1",cool,2
            |  B2,"wip: test",1""".stripMargin,
          deriveCodec[ItemsWithSkuDescQty](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("quotes ambiguous strings in tabular rows") {
        encode(
          ItemsWithIdStatus(List(IdStatus(1, "true"), IdStatus(2, "false"))),
          """items[2]{id,status}:
            |  1,"true"
            |  2,"false"""".stripMargin,
          deriveCodec[ItemsWithIdStatus](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("encodes tabular arrays with keys needing quotes") {
        encode(
          OrderFullNameItems(List(OrderFullName(1, "Ada"), OrderFullName(2, "Bob"))),
          """items[2]{"order:id","full name"}:
            |  1,Ada
            |  2,Bob""".stripMargin,
          deriveCodec[OrderFullNameItems](_.withArrayFormat(ArrayFormat.Tabular))
        )
      }
    ),
    suite("delimiters")(
      test("encodes primitive arrays with tab delimiter") {
        encode(
          Tags(List("reading", "gaming", "coding")),
          "tags[3\t]: reading\tgaming\tcoding",
          deriveCodec[Tags](_.withDelimiter(Delimiter.Tab))
        )
      },
      test("encodes primitive arrays with pipe delimiter") {
        encode(
          Tags(List("reading", "gaming", "coding")),
          "tags[3|]: reading|gaming|coding",
          deriveCodec[Tags](_.withDelimiter(Delimiter.Pipe))
        )
      },
      test("encodes primitive arrays with comma delimiter (default)") {
        encode(Tags(List("reading", "gaming", "coding")), "tags[3]: reading,gaming,coding")
      },
      test("encodes tabular arrays with tab delimiter") {
        encode(
          ItemsTable(List(ItemRow("A1", 2, BigDecimal("9.99")), ItemRow("B2", 1, BigDecimal("14.5")))),
          "items[2\t]{sku\tqty\tprice}:\n  A1\t2\t9.99\n  B2\t1\t14.5",
          deriveCodec[ItemsTable](_.withArrayFormat(ArrayFormat.Tabular).withDelimiter(Delimiter.Tab))
        )
      },
      test("encodes tabular arrays with pipe delimiter") {
        encode(
          ItemsTable(List(ItemRow("A1", 2, BigDecimal("9.99")), ItemRow("B2", 1, BigDecimal("14.5")))),
          """items[2|]{sku|qty|price}:
            |  A1|2|9.99
            |  B2|1|14.5""".stripMargin,
          deriveCodec[ItemsTable](_.withArrayFormat(ArrayFormat.Tabular).withDelimiter(Delimiter.Pipe))
        )
      },
      test("does not quote commas with tab delimiter") {
        encode(Items(List("a,b", "c,d")), "items[2\t]: a,b\tc,d", deriveCodec[Items](_.withDelimiter(Delimiter.Tab)))
      },
      test("does not quote commas with pipe delimiter") {
        encode(
          Items(List("a,b", "c,d")),
          "items[2|]: a,b|c,d",
          deriveCodec[Items](_.withDelimiter(Delimiter.Pipe))
        )
      },
      test("quotes strings containing tab delimiter") {
        encode(
          Items(List("a", "b\tc", "d")),
          "items[3\t]: a\t\"b\\tc\"\td",
          deriveCodec[Items](_.withDelimiter(Delimiter.Tab))
        )
      },
      test("quotes strings containing pipe delimiter when using pipe") {
        encode(
          Items(List("a", "b|c", "d")),
          "items[3|]: a|\"b|c\"|d",
          deriveCodec[Items](_.withDelimiter(Delimiter.Pipe))
        )
      },
      test("quotes tabular values containing comma delimiter") {
        encode(
          ItemsWithNote(List(ItemWithNote(1, "a,b"), ItemWithNote(2, "c,d"))),
          "items[2]{id,note}:\n  1,\"a,b\"\n  2,\"c,d\"",
          deriveCodec[ItemsWithNote](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("does not quote commas in tabular values with tab delimiter") {
        encode(
          ItemsWithNote(List(ItemWithNote(1, "a,b"), ItemWithNote(2, "c,d"))),
          "items[2\t]{id\tnote}:\n  1\ta,b\n  2\tc,d",
          deriveCodec[ItemsWithNote](_.withArrayFormat(ArrayFormat.Tabular).withDelimiter(Delimiter.Tab))
        )
      },
      test("does not quote commas in object values with pipe delimiter") {
        encode(NoteWrapper("a,b"), "note: a,b", deriveCodec[NoteWrapper](_.withDelimiter(Delimiter.Pipe)))
      },
      test("does not quote commas in object values with tab delimiter") {
        encode(NoteWrapper("a,b"), "note: a,b", deriveCodec[NoteWrapper](_.withDelimiter(Delimiter.Tab)))
      },
      test("encodes nested arrays with tab delimiter") {
        encode(
          NestedIntList(List(IntListItem(List(1, 2)), IntListItem(List(3, 4)))),
          "items[2\t]:\n  - xs[2\t]: 1\t2\n  - xs[2\t]: 3\t4",
          deriveCodec[NestedIntList](_.withDelimiter(Delimiter.Tab))
        )
      },
      test("encodes nested arrays with pipe delimiter") {
        encode(
          NestedIntList(List(IntListItem(List(1, 2)), IntListItem(List(3, 4)))),
          "items[2|]:\n  - xs[2|]: 1|2\n  - xs[2|]: 3|4",
          deriveCodec[NestedIntList](_.withDelimiter(Delimiter.Pipe))
        )
      },
      test("encodes root-level array with tab delimiter") {
        encodeDynamic(
          DynamicValue.Sequence(Chunk(dynamicStr("x"), dynamicStr("y"), dynamicStr("z"))),
          "[3\t]: x\ty\tz",
          WriterConfig.withDelimiter(Delimiter.Tab)
        )
      },
      test("encodes root-level array with pipe delimiter") {
        encodeDynamic(
          DynamicValue.Sequence(Chunk(dynamicStr("x"), dynamicStr("y"), dynamicStr("z"))),
          "[3|]: x|y|z",
          WriterConfig.withDelimiter(Delimiter.Pipe)
        )
      },
      test("encodes root-level array of objects with tab delimiter") {
        encodeDynamic(
          DynamicValue.Sequence(Chunk(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2)))),
          "[2\t]{id}:\n  1\n  2",
          WriterConfig.withDelimiter(Delimiter.Tab)
        )
      },
      test("encodes root-level array of objects with pipe delimiter") {
        encodeDynamic(
          DynamicValue.Sequence(Chunk(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2)))),
          "[2|]{id}:\n  1\n  2",
          WriterConfig.withDelimiter(Delimiter.Pipe)
        )
      },
      test("quotes nested array values containing pipe delimiter") {
        encode(
          NestedStringList(List(StringListItem(List("a", "b|c")))),
          "pairs[1|]:\n  - xs[2|]: a|\"b|c\"",
          deriveCodec[NestedStringList](_.withDelimiter(Delimiter.Pipe))
        )
      },
      test("quotes nested array values containing tab delimiter") {
        encode(
          NestedStringList(List(StringListItem(List("a", "b\tc")))),
          "pairs[1\t]:\n  - xs[2\t]: a\t\"b\\tc\"",
          deriveCodec[NestedStringList](_.withDelimiter(Delimiter.Tab))
        )
      },
      test("preserves ambiguity quoting regardless of delimiter") {
        encode(
          Items(List("true", "42", "-3.14")),
          "items[3|]: \"true\"|\"42\"|\"-3.14\"",
          deriveCodec[Items](_.withDelimiter(Delimiter.Pipe))
        )
      }
    ),
    suite("objects")(
      test("encodes simple object") {
        encode(
          SimpleObject(123, "Ada Lovelace", true),
          """id: 123
            |name: Ada Lovelace
            |active: true""".stripMargin
        )
      },
      test("object field value does not need delimiter quoting") {
        encode(NameWrapper("hello,world"), "name: hello,world")
      },
      test("encodes nested objects") {
        encode(
          UserWrapper(User(123, "Ada Lovelace", Contact("ada@example.com", "+1-555-0100"))),
          """user:
            |  id: 123
            |  name: Ada Lovelace
            |  contact:
            |    email: ada@example.com
            |    phone: +1-555-0100""".stripMargin
        )
      }
    ),
    suite("decode inline arrays")(
      test("decodes string arrays inline") {
        decode("tags[3]: reading,gaming,coding", Tags(List("reading", "gaming", "coding")))
      },
      test("decodes number arrays inline") {
        decode("nums[3]: 1,2,3", Nums(List(1, 2, 3)))
      },
      test("decodes empty arrays") {
        decode("items[0]:", Items(List.empty))
      },
      test("decodes single-item array with empty string") {
        decode("items[1]: \"\"", Items(List("")))
      },
      test("decodes multi-item array with empty string") {
        decode("items[3]: a,\"\",b", Items(List("a", "", "b")))
      },
      test("decodes whitespace-only strings in arrays") {
        decode("items[2]: \" \",\"  \"", Items(List(" ", "  ")))
      },
      test("decodes strings with delimiters in arrays") {
        decode("items[3]: a,\"b,c\",\"d:e\"", Items(List("a", "b,c", "d:e")))
      },
      test("decodes strings that look like primitives when quoted") {
        decode("items[4]: x,\"true\",\"42\",\"-3.14\"", Items(List("x", "true", "42", "-3.14")))
      },
      test("decodes strings with structural tokens in arrays") {
        decode("items[3]: \"[5]\",\"- item\",\"{key}\"", Items(List("[5]", "- item", "{key}")))
      }
    ),
    suite("decode delimiters")(
      test("decodes primitive arrays with tab delimiter") {
        decode("tags[3\t]: reading\tgaming\tcoding", Tags(List("reading", "gaming", "coding")))
      },
      test("decodes primitive arrays with pipe delimiter") {
        decode("tags[3|]: reading|gaming|coding", Tags(List("reading", "gaming", "coding")))
      },
      test("does not split on commas when using tab delimiter") {
        decode("items[2\t]: a,b\tc,d", Items(List("a,b", "c,d")))
      },
      test("does not split on commas when using pipe delimiter") {
        decode("items[2|]: a,b|c,d", Items(List("a,b", "c,d")))
      },
      test("decodes values containing tab delimiter when quoted") {
        decode("items[3\t]: a\t\"b\\tc\"\td", Items(List("a", "b\tc", "d")))
      },
      test("decodes values containing pipe delimiter when quoted") {
        decode("items[3|]: a|\"b|c\"|d", Items(List("a", "b|c", "d")))
      },
      test("decodes tabular arrays with tab delimiter") {
        decode(
          "items[2\t]{sku\tqty\tprice}:\n  A1\t2\t9.99\n  B2\t1\t14.5",
          ItemsTable(List(ItemRow("A1", 2, BigDecimal("9.99")), ItemRow("B2", 1, BigDecimal("14.5")))),
          deriveCodec[ItemsTable](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("decodes tabular arrays with pipe delimiter") {
        decode(
          "items[2|]{sku|qty|price}:\n  A1|2|9.99\n  B2|1|14.5",
          ItemsTable(List(ItemRow("A1", 2, BigDecimal("9.99")), ItemRow("B2", 1, BigDecimal("14.5")))),
          deriveCodec[ItemsTable](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("decodes quoted ambiguity with pipe delimiter") {
        decode("items[3|]: \"true\"|\"42\"|\"-3.14\"", Items(List("true", "42", "-3.14")))
      },
      test("decodes quoted ambiguity with tab delimiter") {
        decode("items[3\t]: \"true\"\t\"42\"\t\"-3.14\"", Items(List("true", "42", "-3.14")))
      },
      test("decodes structural-looking strings when quoted with pipe delimiter") {
        decode("items[3|]: \"[5]\"|\"{key}\"|\"- item\"", Items(List("[5]", "{key}", "- item")))
      },
      test("decodes structural-looking strings when quoted with tab delimiter") {
        decode("items[3\t]: \"[5]\"\t\"{key}\"\t\"- item\"", Items(List("[5]", "{key}", "- item")))
      },
      test("decodes primitive arrays with comma delimiter") {
        decode("tags[3]: reading,gaming,coding", Tags(List("reading", "gaming", "coding")))
      },
      test("does not require quoting commas in object values") {
        decode("note: a,b", NoteWrapper("a,b"))
      },
      test("parses nested arrays with tab delimiter") {
        decodeDynamic(
          "pairs[2\t]:\n  - [2\t]: a\tb\n  - [2\t]: c\td",
          record(
            "pairs" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Sequence(Chunk(dynamicStr("a"), dynamicStr("b"))),
                DynamicValue.Sequence(Chunk(dynamicStr("c"), dynamicStr("d")))
              )
            )
          ),
          ReaderConfig.withDelimiter(Delimiter.Tab)
        )
      },
      test("parses nested arrays with pipe delimiter") {
        decodeDynamic(
          "pairs[2|]:\n  - [2|]: a|b\n  - [2|]: c|d",
          record(
            "pairs" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Sequence(Chunk(dynamicStr("a"), dynamicStr("b"))),
                DynamicValue.Sequence(Chunk(dynamicStr("c"), dynamicStr("d")))
              )
            )
          ),
          ReaderConfig.withDelimiter(Delimiter.Pipe)
        )
      },
      test("parses nested arrays inside list items with default comma delimiter") {
        decode(
          "items[1\t]:\n  - tags[3]: a,b,c",
          ItemsTags(List(Tags(List("a", "b", "c")))),
          deriveCodec[ItemsTags](_.withDelimiter(Delimiter.Tab).withArrayFormat(ArrayFormat.List))
        )
      },
      test("parses nested arrays inside list items with default comma delimiter when parent uses pipe") {
        decode(
          "items[1|]:\n  - tags[3]: a,b,c",
          ItemsTags(List(Tags(List("a", "b", "c")))),
          deriveCodec[ItemsTags](_.withDelimiter(Delimiter.Pipe).withArrayFormat(ArrayFormat.List))
        )
      },
      test("parses root-level array with tab delimiter") {
        decodeDynamic(
          "[3\t]: x\ty\tz",
          DynamicValue.Sequence(Chunk(dynamicStr("x"), dynamicStr("y"), dynamicStr("z"))),
          ReaderConfig.withDelimiter(Delimiter.Tab)
        )
      },
      test("parses root-level array with pipe delimiter") {
        decodeDynamic(
          "[3|]: x|y|z",
          DynamicValue.Sequence(Chunk(dynamicStr("x"), dynamicStr("y"), dynamicStr("z"))),
          ReaderConfig.withDelimiter(Delimiter.Pipe)
        )
      },
      test("parses root-level array of objects with tab delimiter") {
        decodeDynamic(
          "[2\t]{id}:\n  1\n  2",
          DynamicValue.Sequence(Chunk(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2)))),
          ReaderConfig.withDelimiter(Delimiter.Tab)
        )
      },
      test("parses root-level array of objects with pipe delimiter") {
        decodeDynamic(
          "[2|]{id}:\n  1\n  2",
          DynamicValue.Sequence(Chunk(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2)))),
          ReaderConfig.withDelimiter(Delimiter.Pipe)
        )
      },
      test("object values in list items follow document delimiter") {
        decode(
          "items[2\t]:\n  - status: a,b\n  - status: c,d",
          StatusItems(List(StatusItem("a,b"), StatusItem("c,d"))),
          deriveCodec[StatusItems](_.withDelimiter(Delimiter.Tab).withArrayFormat(ArrayFormat.List))
        )
      },
      test("object values with comma must be quoted when document delimiter is comma") {
        decode(
          "items[2]:\n  - status: \"a,b\"\n  - status: \"c,d\"",
          StatusItems(List(StatusItem("a,b"), StatusItem("c,d"))),
          deriveCodec[StatusItems](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("parses nested array values containing pipe delimiter") {
        decodeDynamic(
          "pairs[1|]:\n  - [2|]: a|\"b|c\"",
          record(
            "pairs" -> DynamicValue.Sequence(Chunk(DynamicValue.Sequence(Chunk(dynamicStr("a"), dynamicStr("b|c")))))
          ),
          ReaderConfig.withDelimiter(Delimiter.Pipe)
        )
      },
      test("parses nested array values containing tab delimiter") {
        decodeDynamic(
          "pairs[1\t]:\n  - [2\t]: a\t\"b\\tc\"",
          record(
            "pairs" -> DynamicValue.Sequence(Chunk(DynamicValue.Sequence(Chunk(dynamicStr("a"), dynamicStr("b\tc")))))
          ),
          ReaderConfig.withDelimiter(Delimiter.Tab)
        )
      },
      test("parses tabular headers with keys containing the active delimiter") {
        decode(
          "items[2|]{\"a|b\"}:\n  1\n  2",
          ABItems(List(ABField(1), ABField(2))),
          deriveCodec[ABItems](_.withArrayFormat(ArrayFormat.Tabular).withDelimiter(Delimiter.Pipe))
        )
      }
    ),
    suite("decode tabular arrays")(
      test("decodes tabular arrays of uniform objects") {
        decode(
          "items[2]{sku,qty,price}:\n  A1,2,9.99\n  B2,1,14.5",
          ItemsTable(List(ItemRow("A1", 2, BigDecimal("9.99")), ItemRow("B2", 1, BigDecimal("14.5")))),
          deriveCodec[ItemsTable](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("decodes nulls and quoted values in tabular rows") {
        decode(
          "items[2]{id,value}:\n  1,null\n  2,\"test\"",
          ItemsNullable(List(ItemNullable(1, None), ItemNullable(2, Some("test")))),
          deriveCodec[ItemsNullable](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("decodes quoted colon in tabular row as data") {
        decode(
          "items[2]{id,note}:\n  1,\"a:b\"\n  2,\"c:d\"",
          ItemsWithNote(List(ItemWithNote(1, "a:b"), ItemWithNote(2, "c:d"))),
          deriveCodec[ItemsWithNote](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("decodes tabular values containing comma with comma delimiter") {
        decode(
          "items[2]{id,note}:\n  1,\"a,b\"\n  2,\"c,d\"",
          ItemsWithNote(List(ItemWithNote(1, "a,b"), ItemWithNote(2, "c,d"))),
          deriveCodec[ItemsWithNote](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("does not require quoting commas with tab delimiter") {
        decode(
          "items[2\t]{id\tnote}:\n  1\ta,b\n  2\tc,d",
          ItemsWithNote(List(ItemWithNote(1, "a,b"), ItemWithNote(2, "c,d"))),
          deriveCodec[ItemsWithNote](_.withArrayFormat(ArrayFormat.Tabular).withDelimiter(Delimiter.Tab))
        )
      }
    ),
    suite("decode primitives")(
      test("decodes quoted string with newline escape") {
        decode("name: \"line1\\nline2\"", NameWrapper("line1\nline2"))
      },
      test("decodes quoted string with tab escape") {
        decode("name: \"tab\\there\"", NameWrapper("tab\there"))
      },
      test("decodes quoted string with carriage return escape") {
        decode("name: \"return\\rcarriage\"", NameWrapper("return\rcarriage"))
      },
      test("decodes quoted string with backslash escape") {
        decode("name: \"C:\\\\Users\\\\path\"", NameWrapper("C:\\Users\\path"))
      },
      test("decodes quoted string with escaped quotes") {
        decode("name: \"say \\\"hello\\\"\"", NameWrapper("say \"hello\""))
      },
      test("decodes Unicode string") {
        decode("name: café", NameWrapper("café"))
      },
      test("decodes Chinese characters") {
        decode("name: 你好", NameWrapper("你好"))
      },
      test("decodes emoji") {
        decode("name: 🚀", NameWrapper("🚀"))
      },
      test("decodes empty quoted string") {
        decode("name: \"\"", NameWrapper(""))
      },
      test("decodes quoted true as string") {
        decode("name: \"true\"", NameWrapper("true"))
      },
      test("decodes quoted false as string") {
        decode("name: \"false\"", NameWrapper("false"))
      },
      test("decodes quoted null as string") {
        decode("name: \"null\"", NameWrapper("null"))
      },
      test("decodes quoted integer as string") {
        decode("name: \"42\"", NameWrapper("42"))
      },
      test("decodes quoted negative decimal as string") {
        decode("name: \"-3.14\"", NameWrapper("-3.14"))
      },
      test("parses safe unquoted string") {
        decode("name: hello", NameWrapper("hello"))
      },
      test("parses unquoted string with underscore and numbers") {
        decode("name: Ada_99", NameWrapper("Ada_99"))
      },
      test("parses empty quoted string") {
        decode("name: \"\"", NameWrapper(""))
      },
      test("parses positive integer") {
        decodeDynamic("42", dynamicInt(42))
      },
      test("parses negative integer") {
        decodeDynamic("-7", dynamicInt(-7))
      },
      test("parses true") {
        decodeDynamic("true", dynamicBoolean(true))
      },
      test("parses false") {
        decodeDynamic("false", dynamicBoolean(false))
      },
      test("parses null") {
        decodeDynamic("null", dynamicUnit)
      },
      test("parses string with emoji and spaces") {
        decodeDynamic("hello 👋 world", dynamicStr("hello 👋 world"))
      },
      test("respects ambiguity quoting for scientific notation") {
        decode("name: \"1e-6\"", NameWrapper("1e-6"))
      },
      test("respects ambiguity quoting for leading-zero") {
        decode("name: \"05\"", NameWrapper("05"))
      }
    ),
    suite("decode numbers")(
      test("decodes number with trailing zeros in fractional part") {
        decode("value: 1.5000", ValueWrapper(BigDecimal("1.5")))
      },
      test("decodes lowercase exponent") {
        decode("value: 2.5e2", ValueWrapper(BigDecimal("250")))
      },
      test("decodes uppercase exponent with negative sign") {
        decode("value: 3E-02", ValueWrapper(BigDecimal("0.03")))
      },
      test("decodes negative number with positive exponent") {
        decode("value: -1E+03", ValueWrapper(BigDecimal("-1000")))
      },
      test("decodes negative zero as zero") {
        decode("value: -0", ValueWrapper(BigDecimal("0")))
      },
      test("decodes very small exponent") {
        decode("value: 1e-10", ValueWrapper(BigDecimal("0.0000000001")))
      },
      test("decodes zero with exponent as number") {
        decode("value: 0e1", ValueWrapper(BigDecimal("0")))
      },
      test("decodes array with mixed numeric forms") {
        decode(
          "nums[5]: 42,-1E+03,1.5000,-0,2.5e2",
          NumsDecimal(
            List(BigDecimal("42"), BigDecimal("-1000"), BigDecimal("1.5"), BigDecimal("0"), BigDecimal("250"))
          )
        )
      },
      test("parses exponent notation") {
        decode("value: 1e6", ValueWrapper(BigDecimal("1000000")))
      },
      test("parses exponent notation with uppercase E") {
        decode("value: 1E+6", ValueWrapper(BigDecimal("1000000")))
      },
      test("parses negative exponent notation") {
        decode("value: -1e-3", ValueWrapper(BigDecimal("-0.001")))
      },
      test("treats unquoted leading-zero number as string") {
        decodeDynamic("05", dynamicStr("05"), ReaderConfig.withExpandPaths(PathExpansion.Safe))
      },
      test("treats unquoted multi-leading-zero as string") {
        decodeDynamic("007", dynamicStr("007"), ReaderConfig.withExpandPaths(PathExpansion.Safe))
      },
      test("treats leading-zeros in array as strings") {
        decode("items[3]: 05,007,0123", Items(List("05", "007", "0123")))
      },
      test("treats negative leading-zeros in array as strings") {
        decode("items[2]: -05,-007", Items(List("-05", "-007")))
      },
      test("parses negative zero with fractional part") {
        decode("value: -0.0", ValueWrapper(BigDecimal("0")))
      },
      test("parses integer with positive exponent") {
        decode("value: 5E+00", ValueWrapper(BigDecimal("5")))
      },
      test("parses negative zero with exponent as number") {
        decode("value: -0e1", ValueWrapper(BigDecimal("0")))
      },
      test("treats unquoted octal-like as string") {
        decodeDynamic("0123", dynamicStr("0123"), ReaderConfig.withExpandPaths(PathExpansion.Safe))
      },
      test("treats leading-zero in object value as string") {
        decodeDynamic("a: 05", record("a" -> dynamicStr("05")), ReaderConfig.withExpandPaths(PathExpansion.Safe))
      },
      test("treats unquoted negative leading-zero number as string") {
        decodeDynamic("-05", dynamicStr("-05"), ReaderConfig.withExpandPaths(PathExpansion.Safe))
      }
    ),
    suite("decode whitespace handling")(
      test("decodes inline arrays with spaces around commas") {
        decode("tags[3]: a , b , c", Tags(List("a", "b", "c")))
      },
      test("decodes inline arrays with spaces around pipes") {
        decode("tags[3|]: a | b | c", Tags(List("a", "b", "c")))
      },
      test("decodes inline arrays with spaces around tabs") {
        decode("tags[3\t]: a \t b \t c", Tags(List("a", "b", "c")))
      },
      test("decodes tabular rows with leading and trailing spaces") {
        decode(
          "items[2]{id,note}:\n  1 , Alice \n  2 , Bob ",
          ItemsWithNote(List(ItemWithNote(1, "Alice"), ItemWithNote(2, "Bob"))),
          deriveCodec[ItemsWithNote](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("decodes quoted values with spaces around delimiters") {
        decode("items[3]: \"a\" , \"b\" , \"c\"", Items(List("a", "b", "c")))
      },
      test("decodes empty tokens as empty string") {
        decode("items[3]: a,,c", Items(List("a", "", "c")))
      }
    ),
    suite("decode objects")(
      test("decodes objects with primitive values") {
        decode("id: 123\nname: Ada\nactive: true", SimpleObject(123, "Ada", true))
      },
      test("decodes quoted object value with colon") {
        decode("name: \"a:b\"", NameWrapper("a:b"))
      },
      test("decodes quoted object value with comma") {
        decode("name: \"a,b\"", NameWrapper("a,b"))
      },
      test("decodes quoted object value with leading and trailing spaces") {
        decode("text: \" padded \"", TextWrapper(" padded "))
      },
      test("decodes quoted object value with only spaces") {
        decode("text: \"  \"", TextWrapper("  "))
      },
      test("decodes null value in object") {
        decode("id: 123\nvalue: null", IdValue(123, None))
      },
      test("decodes quoted string value that looks like null") {
        decode("text: \"null\"", TextWrapper("null"))
      },
      test("parses dotted keys as identifiers") {
        decodeDynamic("user.name: Ada", record("user.name" -> dynamicStr("Ada")))
      },
      test("parses underscore-prefixed keys") {
        decodeDynamic("_private: 1", record("_private" -> dynamicInt(1)))
      },
      test("parses underscore-containing keys") {
        decodeDynamic("user_name: 1", record("user_name" -> dynamicInt(1)))
      },
      test("parses quoted key with colon") {
        decodeDynamic("\"order:id\": 7", record("order:id" -> dynamicInt(7)))
      },
      test("parses quoted key with brackets") {
        decodeDynamic("\"[index]\": 5", record("[index]" -> dynamicInt(5)))
      },
      test("parses quoted key with braces") {
        decodeDynamic("\"{key}\": 5", record("{key}" -> dynamicInt(5)))
      },
      test("parses quoted key with comma") {
        decodeDynamic("\"a,b\": 1", record("a,b" -> dynamicInt(1)))
      },
      test("parses quoted key with spaces") {
        decodeDynamic("\"full name\": Ada", record("full name" -> dynamicStr("Ada")))
      },
      test("parses quoted key with leading hyphen") {
        decodeDynamic("\"-lead\": 1", record("-lead" -> dynamicInt(1)))
      },
      test("parses quoted numeric key") {
        decodeDynamic("\"123\": x", record("123" -> dynamicStr("x")))
      },
      test("parses quoted empty string key") {
        decodeDynamic("\"\": 1", record("" -> dynamicInt(1)))
      },
      test("unescapes newline in key") {
        decodeDynamic("\"line\\nbreak\": 1", record("line\nbreak" -> dynamicInt(1)))
      },
      test("unescapes tab in key") {
        decodeDynamic("\"tab\\there\": 2", record("tab\there" -> dynamicInt(2)))
      },
      test("unescapes quotes in key") {
        decodeDynamic("\"he said \\\"hi\\\"\": 1", record("he said \"hi\"" -> dynamicInt(1)))
      },
      test("parses quoted key with leading and trailing spaces") {
        decodeDynamic("\" a \": 1", record(" a " -> dynamicInt(1)))
      },
      test("parses empty nested object header") {
        decodeDynamic("user:", record("user" -> record()))
      },
      test("parses deeply nested objects with indentation") {
        decodeDynamic("a:\n  b:\n    c: deep", record("a" -> record("b" -> record("c" -> dynamicStr("deep")))))
      },
      test("parses quoted object value with newline escape") {
        decode("text: \"line1\\nline2\"", TextWrapper("line1\nline2"))
      },
      test("parses quoted object value with escaped quotes") {
        decodeDynamic("text: \"say \\\"hello\\\"\"", record("text" -> dynamicStr("say \"hello\"")))
      },
      test("parses quoted string value that looks like integer") {
        decode("name: \"42\"", NameWrapper("42"))
      },
      test("parses quoted string value that looks like negative decimal") {
        decode("name: \"-7.5\"", NameWrapper("-7.5"))
      }
    ),
    suite("strict mode errors")(
      test("tabs in indentation error in strict mode") {
        decodeError[Nums]("items[2]:\n\t- 1\n  - 2", "Tabs are not allowed in indentation at: .")
      },
      test("tabs in indentation accepted in non-strict mode") {
        decode("name: test", NameWrapper("test"), ReaderConfig.withStrict(false))
      },
      test("blank lines inside tabular arrays error in strict mode") {
        decodeError(
          "items[3]{id,note}:\n  1,a\n\n  2,b\n  3,c",
          "Blank lines are not allowed inside arrays/tabular blocks in strict mode at: .items",
          deriveCodec[ItemsWithNote](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("blank lines inside tabular arrays parsed correctly") {
        decode(
          "items[2]{id,note}:\n  1,first\n  2,second",
          ItemsWithNote(List(ItemWithNote(1, "first"), ItemWithNote(2, "second"))),
          deriveCodec[ItemsWithNote](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("indentation not multiple of indent size errors in strict mode") {
        decodeError[UserWrapper](
          "user:\n   name: Ada", // 3 spaces instead of 2
          "Indentation must be multiple of 2 spaces at: .user"
        )
      },
      test("path expansion conflict errors in strict mode") {
        decodeDynamicError(
          "a.b: 1\na: 2",
          "Path expansion conflict at key 'a': cannot overwrite existing value with new value in strict mode at: .",
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("path expansion conflict object vs array errors in strict mode") {
        decodeDynamicError(
          "a.b: 1\na[2]: 2,3",
          "Path expansion conflict at key 'a': cannot overwrite existing value with new value in strict mode at: .",
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("path expansion conflict uses LWW in non-strict mode") {
        decodeDynamic(
          "a.b: 1\na: 2", // In non-strict mode, LWW applies: a: 2 wins over a.b: 1
          record("a" -> dynamicInt(2)),
          ReaderConfig.withExpandPaths(PathExpansion.Safe).withStrict(false)
        )
      },
      test("path expansion with record merge succeeds in strict mode") {
        decodeDynamic(
          "a.b: 1\na.c: 2", // When both are records, they merge - this is NOT a conflict
          record("a" -> record("b" -> dynamicInt(1), "c" -> dynamicInt(2))),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("array count mismatch errors for inline arrays") {
        decodeError[Nums](
          "nums[3]: 1,2", // Declared [3] but only 2 values provided
          "Array count mismatch: expected 3 items but got 2 at: .nums"
        )
      },
      test("array count mismatch errors for too many items") {
        decodeError[Nums](
          "nums[2]: 1,2,3", // Declared [2] but 3 values provided
          "Array count mismatch: expected 2 items but got 3 at: .nums"
        )
      },
      test("array count match succeeds") {
        decode(
          "nums[3]: 1,2,3", // Declared [3] with exactly 3 values
          Nums(List(1, 2, 3))
        )
      }
    ),
    suite("decode quoted keys")(
      test("decodes quoted key in array header") {
        val reader = ToonReader(ReaderConfig)
        reader.reset("\"my-key\"[3]: 1,2,3")
        val header = reader.parseArrayHeader()
        assertTrue(header.key == "my-key") &&
        assertTrue(header.length == 3)
      },
      test("decodes quoted key containing brackets in array header") {
        val reader = ToonReader(ReaderConfig)
        val input  = "\"key[test]\"[3]: 1,2,3"
        reader.reset(input)
        val header = reader.parseArrayHeader()
        assertTrue(header.key == "key[test]") &&
        assertTrue(header.length == 3)
      },
      test("decodes quoted key with tabular array format") {
        val reader = ToonReader(ReaderConfig)
        val input  = "\"x-items\"[2]{id,name}:\n  1,Ada\n  2,Bob"
        reader.reset(input)
        val header = reader.parseArrayHeader()
        assertTrue(header.key == "x-items") &&
        assertTrue(header.length == 2) &&
        assertTrue(header.fields.toList == List("id", "name"))
      },
      test("decodes quoted header keys in tabular arrays") {
        val reader = ToonReader(ReaderConfig)
        val input  = "items[2]{\"order:id\",\"full name\"}:\n  1,Ada\n  2,Bob"
        reader.reset(input)
        val header = reader.parseArrayHeader()
        assertTrue(header.key == "items") &&
        assertTrue(header.length == 2) &&
        assertTrue(header.fields.toList == List("order:id", "full name"))
      },
      test("decodes quoted key in key-value pair") {
        val reader = ToonReader(ReaderConfig)
        val input  = "\"my-key\": value"
        reader.reset(input)
        val key = reader.readKey()
        assertTrue(key == "my-key")
      },
      test("decodes quoted key with colon inside") {
        val reader = ToonReader(ReaderConfig)
        val input  = "\"key:with:colons\": value"
        reader.reset(input)
        val key = reader.readKey()
        assertTrue(key == "key:with:colons")
      },
      test("decodes quoted key with escape sequences") {
        val reader = ToonReader(ReaderConfig)
        val input  = "\"key\\twith\\ttabs\": value"
        reader.reset(input)
        val key = reader.readKey()
        assertTrue(key == "key\twith\ttabs")
      }
    ),
    suite("root form discovery")(
      test("empty document decodes to empty record") {
        decodeDynamic("", record())
      },
      test("single primitive at root decodes correctly") {
        decodeDynamic("42", dynamicInt(42))
      },
      test("single string primitive at root") {
        decodeDynamic("hello", dynamicStr("hello"))
      },
      test("object at root decodes correctly") {
        decodeDynamic("key: value", record("key" -> dynamicStr("value")))
      }
    ),
    suite("nested arrays")(
      test("encodes nested primitive arrays in list format") {
        encode(
          NestedIntList(List(IntListItem(List(1, 2)), IntListItem(List(3, 4)))),
          """items[2]:
            |  - xs[2]:
            |    - 1
            |    - 2
            |  - xs[2]:
            |    - 3
            |    - 4""".stripMargin,
          deriveCodec[NestedIntList](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("encodes empty inner arrays") {
        encode(
          NestedIntList(List(IntListItem(List.empty), IntListItem(List(1)))),
          """items[2]:
            |  - xs[0]:
            |  - xs[1]:
            |    - 1""".stripMargin,
          deriveCodec[NestedIntList](_.withArrayFormat(ArrayFormat.List).withTransientEmptyCollection(false))
        )
      }
    ),
    suite("roundtrip")(
      test("primitive array roundtrips") {
        roundTrip(Tags(List("reading", "gaming")), "tags[2]: reading,gaming")
      },
      test("tabular array roundtrips") {
        roundTrip(
          ItemsTable(List(ItemRow("A1", 2, BigDecimal("9.99")), ItemRow("B2", 1, BigDecimal("14.5")))),
          """items[2]{sku,qty,price}:
            |  A1,2,9.99
            |  B2,1,14.5""".stripMargin,
          deriveCodec[ItemsTable](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("tabular array with pipe delimiter roundtrips") {
        roundTrip(
          ItemsTable(List(ItemRow("A1", 2, BigDecimal("9.99")), ItemRow("B2", 1, BigDecimal("14.5")))),
          """items[2|]{sku|qty|price}:
            |  A1|2|9.99
            |  B2|1|14.5""".stripMargin,
          deriveCodec[ItemsTable](_.withArrayFormat(ArrayFormat.Tabular).withDelimiter(Delimiter.Pipe))
        )
      },
      test("tabular array with nullable values roundtrips") {
        roundTrip(
          ItemsNullable(List(ItemNullable(1, None), ItemNullable(2, Some("test")))),
          """items[2]{id,value}:
            |  1,null
            |  2,test""".stripMargin,
          deriveCodec[ItemsNullable](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("nested object roundtrips") {
        roundTrip(
          UserWrapper(User(123, "Ada Lovelace", Contact("ada@example.com", "+1-555-0100"))),
          """user:
            |  id: 123
            |  name: Ada Lovelace
            |  contact:
            |    email: ada@example.com
            |    phone: +1-555-0100""".stripMargin
        )
      },
      test("inline array with quoted delimiters roundtrips") {
        roundTrip(Items(List("a", "b,c", "d:e")), "items[3]: a,\"b,c\",\"d:e\"")
      },
      test("inline array with quoted booleans roundtrips") {
        roundTrip(Items(List("x", "true", "42", "-3.14")), "items[4]: x,\"true\",\"42\",\"-3.14\"")
      },
      test("tabular array with tab delimiter roundtrips") {
        roundTrip(
          ItemsTable(List(ItemRow("A1", 2, BigDecimal("9.99")), ItemRow("B2", 1, BigDecimal("14.5")))),
          "items[2\t]{sku\tqty\tprice}:\n  A1\t2\t9.99\n  B2\t1\t14.5",
          deriveCodec[ItemsTable](_.withArrayFormat(ArrayFormat.Tabular).withDelimiter(Delimiter.Tab))
        )
      },
      test("empty array roundtrips") {
        roundTrip(Items(List.empty), "items[0]:", deriveCodec[Items](_.withTransientEmptyCollection(false)))
      },
      test("empty string in array roundtrips") {
        roundTrip(Items(List("")), "items[1]: \"\"")
      },
      test("pipe delimiter does not quote commas") {
        roundTrip(Items(List("a,b", "c,d")), "items[2|]: a,b|c,d", deriveCodec[Items](_.withDelimiter(Delimiter.Pipe)))
      },
      test("pipe delimiter quotes pipes") {
        roundTrip(
          Items(List("a", "b|c", "d")),
          "items[3|]: a|\"b|c\"|d",
          deriveCodec[Items](_.withDelimiter(Delimiter.Pipe))
        )
      }
    ),
    suite("key folding")(
      test("encodes folded chain to primitive in safe mode") {
        encodeDynamic(
          record("a" -> record("b" -> record("c" -> dynamicInt(1)))),
          "a.b.c: 1",
          WriterConfig.withKeyFolding(KeyFolding.Safe)
        )
      },
      test("encodes standard nesting with keyFolding off") {
        encodeDynamic(
          record("a" -> record("b" -> record("c" -> dynamicInt(1)))),
          "a:\n  b:\n    c: 1",
          WriterConfig.withKeyFolding(KeyFolding.Off)
        )
      },
      test("encodes full chain with flattenDepth infinity") {
        encodeDynamic(
          record("a" -> record("b" -> record("c" -> record("d" -> dynamicInt(1))))),
          "a.b.c.d: 1",
          WriterConfig.withKeyFolding(KeyFolding.Safe)
        )
      },
      test("encodes partial folding with flattenDepth 2") {
        encodeDynamic(
          record("a" -> record("b" -> record("c" -> record("d" -> dynamicInt(1))))),
          "a.b:\n  c:\n    d: 1",
          WriterConfig.withKeyFolding(KeyFolding.Safe).withFlattenDepth(2)
        )
      },
      test("encodes standard nesting with flattenDepth 0") {
        encodeDynamic(
          record("a" -> record("b" -> record("c" -> dynamicInt(1)))),
          "a:\n  b:\n    c: 1",
          WriterConfig.withKeyFolding(KeyFolding.Safe).withFlattenDepth(0)
        )
      },
      test("encodes standard nesting with flattenDepth 1") {
        encodeDynamic(
          record("a" -> record("b" -> record("c" -> dynamicInt(1)))),
          "a:\n  b:\n    c: 1",
          WriterConfig.withKeyFolding(KeyFolding.Safe).withFlattenDepth(1)
        )
      },
      test("skips folding when segment requires quotes in safe mode") {
        encodeDynamic(
          record("data" -> record("full-name" -> record("x" -> dynamicInt(1)))),
          "data:\n  \"full-name\":\n    x: 1",
          WriterConfig.withKeyFolding(KeyFolding.Safe)
        )
      },
      test("encodes folded chain ending with empty object") {
        encodeDynamic(
          record("a" -> record("b" -> record("c" -> record()))),
          "a.b.c:",
          WriterConfig.withKeyFolding(KeyFolding.Safe)
        )
      },
      test("encodes folded chains preserving sibling field order") {
        encodeDynamic(
          record(
            "first"  -> record("second" -> record("third" -> dynamicInt(1))),
            "simple" -> dynamicInt(2),
            "short"  -> record("path" -> dynamicInt(3))
          ),
          "first.second.third: 1\nsimple: 2\nshort.path: 3",
          WriterConfig.withKeyFolding(KeyFolding.Safe)
        )
      },
      test("stops folding at multi-key records") {
        encodeDynamic(
          record("a" -> record("b" -> dynamicInt(1), "c" -> dynamicInt(2))),
          "a:\n  b: 1\n  c: 2",
          WriterConfig.withKeyFolding(KeyFolding.Safe)
        )
      },
      test("encodes folded chain with tabular array") {
        encodeDynamic(
          record(
            "a" -> record(
              "b" -> record(
                "items" -> DynamicValue.Sequence(
                  Chunk(
                    record("id" -> dynamicInt(1), "name" -> dynamicStr("A")),
                    record("id" -> dynamicInt(2), "name" -> dynamicStr("B"))
                  )
                )
              )
            )
          ),
          "a.b.items[2]{id,name}:\n  1,A\n  2,B",
          WriterConfig.withKeyFolding(KeyFolding.Safe)
        )
      },
      test("stops folding at array boundary") {
        encodeDynamic(
          record(
            "a" -> record(
              "b" -> DynamicValue.Sequence(
                Chunk(dynamicInt(1), dynamicInt(2))
              )
            )
          ),
          "a.b[2]: 1,2",
          WriterConfig.withKeyFolding(KeyFolding.Safe)
        )
      }
    ),
    suite("path expansion")(
      test("expands dotted key to nested object in safe mode") {
        decodeDynamic(
          "a.b.c: 1",
          record("a" -> record("b" -> record("c" -> dynamicInt(1)))),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("preserves literal dotted keys when expansion is off") {
        decodeDynamic(
          "user.name: Ada",
          record("user.name" -> dynamicStr("Ada"))
        )
      },
      test("expands and deep-merges preserving document order") {
        decodeDynamic(
          "a.b.c: 1\na.b.d: 2\na.e: 3",
          record(
            "a" -> record(
              "b" -> record("c" -> dynamicInt(1), "d" -> dynamicInt(2)),
              "e" -> dynamicInt(3)
            )
          ),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("applies LWW when strict false and primitive overwrites object") {
        decodeDynamic(
          "a.b: 1\na: 2",
          record("a" -> dynamicInt(2)),
          ReaderConfig.withExpandPaths(PathExpansion.Safe).withStrict(false)
        )
      },
      test("applies LWW when strict false and object overwrites primitive") {
        decodeDynamic(
          "a: 1\na.b: 2",
          record("a" -> record("b" -> dynamicInt(2))),
          ReaderConfig.withExpandPaths(PathExpansion.Safe).withStrict(false)
        )
      },
      test("preserves quoted dotted key as literal in safe mode") {
        decodeDynamic(
          "a.b: 1\n\"c.d\": 2",
          record(
            "a"   -> record("b" -> dynamicInt(1)),
            "c.d" -> dynamicInt(2)
          ),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("preserves non-identifier segment keys as literals") {
        decodeDynamic(
          "full-name.x: 1",
          record("full-name.x" -> dynamicInt(1)),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("expands keys creating empty nested objects") {
        decodeDynamic(
          "a.b.c:",
          record("a" -> record("b" -> record("c" -> record()))),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("expands dotted key with inline array") {
        decodeDynamic(
          "data.meta.items[2]: a,b",
          record(
            "data" -> record(
              "meta" -> record(
                "items" -> DynamicValue.Sequence(
                  Chunk(dynamicStr("a"), dynamicStr("b"))
                )
              )
            )
          ),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("expands dotted key with tabular array") {
        decodeDynamic(
          "a.b.items[2]{id,name}:\n  1,A\n  2,B",
          record(
            "a" -> record(
              "b" -> record(
                "items" -> DynamicValue.Sequence(
                  Chunk(
                    record("id" -> dynamicInt(1), "name" -> dynamicStr("A")),
                    record("id" -> dynamicInt(2), "name" -> dynamicStr("B"))
                  )
                )
              )
            )
          ),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      }
    ),
    suite("encode primitives")(
      test("quotes string that looks like scientific notation") {
        encode("1e-6", "\"1e-6\"")
      },
      test("quotes string with leading zero") {
        encode("05", "\"05\"")
      },
      test("quotes string with array-like syntax") {
        encode("[3]: x,y", "\"[3]: x,y\"")
      },
      test("quotes string starting with hyphen-space") {
        encode("- item", "\"- item\"")
      },
      test("encodes positive integer") {
        encode(42, "42")
      },
      test("encodes decimal number") {
        encode(3.14, "3.14")
      },
      test("encodes negative integer") {
        encode(-7, "-7")
      },
      test("encodes zero") {
        encode(0, "0")
      },
      test("encodes true") {
        encode(true, "true")
      },
      test("encodes false") {
        encode(false, "false")
      },
      test("encodes Chinese characters without quotes") {
        encode("你好", "你好")
      },
      test("encodes string with emoji and spaces") {
        encode("hello 👋 world", "hello 👋 world")
      },
      test("encodes safe strings without quotes") {
        encode("hello", "hello")
      },
      test("encodes safe string with underscore and numbers") {
        encode("Ada_99", "Ada_99")
      },
      test("quotes string that looks like negative decimal") {
        encode("-3.14", "\"-3.14\"")
      },
      test("quotes single hyphen in array") {
        encode(Items(List("-")), "items[1]: \"-\"")
      },
      test("quotes leading-hyphen string in array") {
        encode(Items(List("a", "- item", "b")), "items[3]: a,\"- item\",b")
      },
      test("encodes scientific notation as decimal") {
        encode(1000000L, "1000000")
      },
      test("encodes small decimal from scientific notation") {
        encode(BigDecimal("1e-6"), "0.000001")
      },
      test("encodes large number") {
        encode(BigDecimal("1e+20"), "100000000000000000000")
      },
      test("encodes MAX_SAFE_INTEGER") {
        encode(9007199254740991L, "9007199254740991")
      },
      test("encodes repeating decimal with full precision") {
        encode(BigDecimal(1.0 / 3), "0.3333333333333333")
      },
      test("encodes null") {
        encodeDynamic(dynamicUnit, "null")
      }
    ),
    suite("encode objects")(
      test("preserves key order in objects") {
        encode(SimpleObject(123, "Ada", true), "id: 123\nname: Ada\nactive: true")
      },
      test("encodes null values in objects") {
        encode(IdValue(123, None), "id: 123\nvalue: null", deriveCodec[IdValue](_.withTransientNone(false)))
      },
      test("quotes string value with colon") {
        encode(NameWrapper("a:b"), "name: \"a:b\"")
      },
      test("does not quote string value with comma (commas only special in arrays)") {
        encode(NameWrapper("a,b"), "name: a,b")
      },
      test("quotes string value with newline") {
        encode(NameWrapper("line1\nline2"), "name: \"line1\\nline2\"")
      },
      test("quotes string value with embedded quotes") {
        encode(NameWrapper("say \"hello\""), "name: \"say \\\"hello\\\"\"")
      },
      test("quotes string value with leading space") {
        encode(NameWrapper(" padded "), "name: \" padded \"")
      },
      test("quotes string value with only spaces") {
        encode(NameWrapper("  "), "name: \"  \"")
      },
      test("encodes deeply nested objects") {
        encode(
          UserWrapper(User(123, "Ada", Contact("ada@example.com", "555-0100"))),
          "user:\n  id: 123\n  name: Ada\n  contact:\n    email: ada@example.com\n    phone: 555-0100"
        )
      },
      test("encodes empty objects as empty string") {
        encodeDynamic(record(), "")
      },
      test("quotes string value that looks like true") {
        encode(NameWrapper("true"), "name: \"true\"")
      },
      test("quotes string value that looks like number") {
        encode(NameWrapper("42"), "name: \"42\"")
      },
      test("quotes string value that looks like negative decimal") {
        encode(NameWrapper("-7.5"), "name: \"-7.5\"")
      },
      test("quotes key with colon") {
        encodeDynamic(record("order:id" -> dynamicInt(7)), "\"order:id\": 7")
      },
      test("quotes key with brackets") {
        encodeDynamic(record("[index]" -> dynamicInt(5)), "\"[index]\": 5")
      },
      test("quotes key with braces") {
        encodeDynamic(record("{key}" -> dynamicInt(5)), "\"{key}\": 5")
      },
      test("quotes key with comma") {
        encodeDynamic(record("a,b" -> dynamicInt(1)), "\"a,b\": 1")
      },
      test("quotes key with spaces") {
        encodeDynamic(record("full name" -> dynamicStr("Ada")), "\"full name\": Ada")
      },
      test("quotes key with leading hyphen") {
        encodeDynamic(record("-lead" -> dynamicInt(1)), "\"-lead\": 1")
      },
      test("quotes key with leading and trailing spaces") {
        encodeDynamic(record(" a " -> dynamicInt(1)), "\" a \": 1")
      },
      test("quotes numeric key") {
        encodeDynamic(record("123" -> dynamicStr("x")), "\"123\": x")
      },
      test("quotes empty string key") {
        encodeDynamic(record("" -> dynamicInt(1)), "\"\": 1")
      },
      test("escapes newline in key") {
        encodeDynamic(record("line\nbreak" -> dynamicInt(1)), "\"line\\nbreak\": 1")
      },
      test("escapes tab in key") {
        encodeDynamic(record("tab\there" -> dynamicInt(2)), "\"tab\\there\": 2")
      },
      test("escapes quotes in key") {
        encodeDynamic(record("he said \"hi\"" -> dynamicInt(1)), "\"he said \\\"hi\\\"\": 1")
      },
      test("encodes empty nested object") {
        encodeDynamic(record("user" -> record()), "user:")
      }
    ),
    suite("encode arrays primitive")(
      test("encodes whitespace-only strings in arrays") {
        encode(Items(List(" ", "  ")), "items[2]: \" \",\"  \"")
      },
      test("quotes strings with structural meanings in arrays") {
        encode(Items(List("[5]", "- item", "{key}")), "items[3]: \"[5]\",\"- item\",\"{key}\"")
      }
    ),
    suite("encode arrays tabular")(
      test("encodes arrays of uniform objects in tabular format") {
        encode(
          ItemsTable(List(ItemRow("A1", 2, BigDecimal("9.99")), ItemRow("B2", 1, BigDecimal("14.5")))),
          "items[2]{sku,qty,price}:\n  A1,2,9.99\n  B2,1,14.5",
          deriveCodec[ItemsTable](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("encodes null values in tabular format") {
        encode(
          ItemsNullable(List(ItemNullable(1, None), ItemNullable(2, Some("test")))),
          "items[2]{id,value}:\n  1,null\n  2,test",
          deriveCodec[ItemsNullable](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("quotes strings containing delimiters in tabular rows") {
        encode(
          ItemsWithSkuDescQty(List(SkuDescQty("A,1", "cool", 2), SkuDescQty("B2", "wip: test", 1))),
          "items[2]{sku,desc,qty}:\n  \"A,1\",cool,2\n  B2,\"wip: test\",1",
          deriveCodec[ItemsWithSkuDescQty](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("quotes ambiguous strings in tabular rows") {
        encode(
          ItemsWithIdStatus(List(IdStatus(1, "true"), IdStatus(2, "false"))),
          "items[2]{id,status}:\n  1,\"true\"\n  2,\"false\"",
          deriveCodec[ItemsWithIdStatus](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("encodes tabular arrays with keys needing quotes") {
        encode(
          OrderFullNameItems(List(OrderFullName(1, "Ada"), OrderFullName(2, "Bob"))),
          "items[2]{\"order:id\",\"full name\"}:\n  1,Ada\n  2,Bob",
          deriveCodec[OrderFullNameItems](_.withArrayFormat(ArrayFormat.Tabular))
        )
      }
    ),
    suite("encode arrays nested")(
      test("encodes nested arrays of primitives") {
        encode(
          PairsList(List(StringPair(List("a", "b")), StringPair(List("c", "d")))),
          """pairs[2]:
            |  - items[2]:
            |    - a
            |    - b
            |  - items[2]:
            |    - c
            |    - d""".stripMargin,
          deriveCodec[PairsList](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("encodes empty inner arrays") {
        encode(
          PairsList(List(StringPair(List.empty), StringPair(List.empty))),
          """pairs[2]:
            |  - items[0]:
            |  - items[0]:""".stripMargin,
          deriveCodec[PairsList](_.withArrayFormat(ArrayFormat.List).withTransientEmptyCollection(false))
        )
      },
      test("encodes complex nested structure") {
        encode(
          ComplexUser(123, "Ada", List("reading", "gaming"), true, List.empty),
          """id: 123
            |name: Ada
            |tags[2]: reading,gaming
            |active: true""".stripMargin
        )
      },
      test("encodes root-level primitive array") {
        encodeDynamic(
          DynamicValue.Sequence(
            Chunk(dynamicStr("x"), dynamicStr("y"), dynamicStr("true"), dynamicBoolean(true), dynamicInt(10))
          ),
          "[5]: x,y,\"true\",true,10"
        )
      },
      test("encodes root-level array of uniform objects in tabular format") {
        encodeDynamic(
          DynamicValue.Sequence(Chunk(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2)))),
          "[2]{id}:\n  1\n  2"
        )
      },
      test("encodes root-level array of non-uniform objects in list format") {
        encodeDynamic(
          DynamicValue.Sequence(
            Chunk(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2), "name" -> dynamicStr("Ada")))
          ),
          "[2]:\n  - id: 1\n  - id: 2\n    name: Ada"
        )
      },
      test("encodes root-level array mixing primitive, object, and array of objects in list format") {
        encodeDynamic(
          DynamicValue.Sequence(
            Chunk(
              dynamicStr("summary"),
              record("id" -> dynamicInt(1), "name" -> dynamicStr("Ada")),
              DynamicValue.Sequence(Chunk(record("id" -> dynamicInt(2)), record("status" -> dynamicStr("draft"))))
            )
          ),
          "[3]:\n  - summary\n  - id: 1\n    name: Ada\n  - [2]:\n    - id: 2\n    - status: draft"
        )
      },
      test("encodes root-level arrays of arrays") {
        encodeDynamic(
          DynamicValue.Sequence(
            Chunk(DynamicValue.Sequence(Chunk(dynamicInt(1), dynamicInt(2))), DynamicValue.Sequence(Chunk()))
          ),
          "[2]:\n  - [2]: 1,2\n  - [0]:"
        )
      },
      test("encodes empty root-level array") {
        encodeDynamic(DynamicValue.Sequence(Chunk()), "[0]:")
      },
      test("quotes strings containing delimiters in nested arrays") {
        encodeDynamic(
          record(
            "pairs" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Sequence(Chunk(dynamicStr("a"), dynamicStr("b"))),
                DynamicValue.Sequence(Chunk(dynamicStr("c,d"), dynamicStr("e:f"), dynamicStr("true")))
              )
            )
          ),
          "pairs[2]:\n  - [2]: a,b\n  - [3]: \"c,d\",\"e:f\",\"true\""
        )
      },
      test("encodes mixed-length inner arrays") {
        encodeDynamic(
          record(
            "pairs" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Sequence(Chunk(dynamicInt(1))),
                DynamicValue.Sequence(Chunk(dynamicInt(2), dynamicInt(3)))
              )
            )
          ),
          "pairs[2]:\n  - [1]: 1\n  - [2]: 2,3"
        )
      },
      test("uses list format for arrays mixing primitives and objects") {
        encodeDynamic(
          record(
            "items" -> DynamicValue.Sequence(
              Chunk(dynamicInt(1), record("a" -> dynamicInt(1)), dynamicStr("text"))
            )
          ),
          "items[3]:\n  - 1\n  - a: 1\n  - text"
        )
      },
      test("uses list format for arrays mixing objects and arrays") {
        encodeDynamic(
          record(
            "items" -> DynamicValue.Sequence(
              Chunk(
                record("a" -> dynamicInt(1)),
                DynamicValue.Sequence(Chunk(dynamicInt(1), dynamicInt(2)))
              )
            )
          ),
          "items[2]:\n  - a: 1\n  - [2]: 1,2"
        )
      }
    ),
    suite("encode arrays objects")(
      test("uses list format for objects with different fields") {
        encode(
          PersonListWrapper(List(Person("First", 25), Person("Second", 30))),
          """people[2]:
            |  - name: First
            |    age: 25
            |  - name: Second
            |    age: 30""".stripMargin,
          deriveCodec[PersonListWrapper](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("uses list format for objects with nested values") {
        encode(
          ItemsWithNestedWrapper(List(ItemWithNested(1, NestedX(1)))),
          """items[1]:
            |  - id: 1
            |    nested:
            |      x: 1""".stripMargin,
          deriveCodec[ItemsWithNestedWrapper](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("preserves field order in list items - primitive first") {
        encode(
          ItemsNameNums(List(NameNums("Ada", List(1, 2, 3)))),
          """items[1]:
            |  - name: Ada
            |    nums[3]:
            |      - 1
            |      - 2
            |      - 3""".stripMargin,
          deriveCodec[ItemsNameNums](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("encodes objects with empty arrays in list format") {
        encode(
          ItemsNameData(List(NameData("Ada", List.empty))),
          """items[1]:
            |  - name: Ada
            |    data[0]:""".stripMargin,
          deriveCodec[ItemsNameData](_.withArrayFormat(ArrayFormat.List).withTransientEmptyCollection(false))
        )
      },
      test("uses list format for objects with multiple array fields") {
        encode(
          ItemsNumsTags(List(NumsTags(List(1, 2), List("a", "b"), "test"))),
          """items[1]:
            |  - nums[2]:
            |    - 1
            |    - 2
            |    tags[2]:
            |      - a
            |      - b
            |    name: test""".stripMargin,
          deriveCodec[ItemsNumsTags](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("uses field order from first object for tabular headers") {
        encode(
          ABCItems(List(ABC(1, 2, 3), ABC(10, 20, 30))),
          "items[2]{a,b,c}:\n  1,2,3\n  10,20,30",
          deriveCodec[ABCItems](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("preserves field order in list items - array first") {
        encode(
          ItemsNumsName(List(NumsName(List(1, 2, 3), "Ada"))),
          "items[1]:\n  - nums[3]:\n    - 1\n    - 2\n    - 3\n    name: Ada",
          deriveCodec[ItemsNumsName](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("uses list format for objects containing arrays of arrays") {
        encode(
          ItemsMatrixName(List(MatrixName(List(IntListWrapper(List(1, 2)), IntListWrapper(List(3, 4))), "grid"))),
          "items[1]:\n  - matrix[2]:\n    - xs[2]:\n      - 1\n      - 2\n    - xs[2]:\n      - 3\n      - 4\n    name: grid",
          deriveCodec[ItemsMatrixName](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("uses tabular format for nested uniform object arrays") {
        encode(
          ItemsUsersStatus(List(UsersStatus(List(IdName(1, "Ada"), IdName(2, "Bob")), "active"))),
          "items[1]:\n  - users[2]{id,name}:\n      1,Ada\n      2,Bob\n    status: active",
          deriveCodec[ItemsUsersStatus](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("uses list format for nested object arrays with mismatched keys") {
        encode(
          ItemsUsersMismatch(List(UsersMismatch(List(UserMismatch(1, Some("Ada")), UserMismatch(2, None)), "active"))),
          "items[1]:\n  - users[2]:\n    - id: 1\n      name: Ada\n    - id: 2\n    status: active",
          deriveCodec[ItemsUsersMismatch](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("uses list format for objects with only array fields") {
        encode(
          ItemsNumsTagsOnly(List(NumsTagsOnly(List(1, 2, 3), List("a", "b")))),
          """items[1]:
            |  - nums[3]:
            |    - 1
            |    - 2
            |    - 3
            |    tags[2]:
            |      - a
            |      - b""".stripMargin,
          deriveCodec[ItemsNumsTagsOnly](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("uses canonical encoding for multi-field list-item objects with tabular arrays") {
        encodeDynamic(
          record(
            "items" -> DynamicValue.Sequence(
              Chunk(
                record(
                  "users" -> DynamicValue.Sequence(
                    Chunk(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2)))
                  ),
                  "note" -> dynamicStr("x")
                )
              )
            )
          ),
          "items[1]:\n  - users[2]{id}:\n      1\n      2\n    note: x"
        )
      },
      test("uses canonical encoding for single-field list-item tabular arrays") {
        encodeDynamic(
          record(
            "items" -> DynamicValue.Sequence(
              Chunk(
                record(
                  "users" -> DynamicValue.Sequence(
                    Chunk(
                      record("id" -> dynamicInt(1), "name" -> dynamicStr("Ada")),
                      record("id" -> dynamicInt(2), "name" -> dynamicStr("Bob"))
                    )
                  )
                )
              )
            )
          ),
          "items[1]:\n  - users[2]{id,name}:\n      1,Ada\n      2,Bob"
        )
      },
      test("places empty arrays on hyphen line when first") {
        encode(
          ItemsDataName(List(DataName(List.empty, "x"))),
          "items[1]:\n  - data[0]:\n    name: x",
          deriveCodec[ItemsDataName](_.withArrayFormat(ArrayFormat.List).withTransientEmptyCollection(false))
        )
      },
      test("encodes empty object list items as bare hyphen") {
        encodeDynamic(
          record(
            "items" -> DynamicValue.Sequence(
              Chunk(dynamicStr("first"), dynamicStr("second"), record())
            )
          ),
          "items[3]:\n  - first\n  - second\n  -"
        )
      },
      test("uses list format when one object has nested field") {
        encodeDynamic(
          record(
            "items" -> DynamicValue.Sequence(
              Chunk(
                record("id" -> dynamicInt(1), "data" -> dynamicStr("string")),
                record(
                  "id"   -> dynamicInt(2),
                  "data" -> record(
                    "nested" -> dynamicBoolean(true)
                  )
                )
              )
            )
          ),
          "items[2]:\n  - id: 1\n    data: string\n  - id: 2\n    data:\n      nested: true"
        )
      }
    ),
    suite("decode whitespace")(
      test("tolerates spaces around commas in inline arrays") {
        decode("tags[3]: a , b , c", Tags(List("a", "b", "c")))
      },
      test("tolerates spaces around pipes in inline arrays") {
        decode("tags[3|]: a | b | c", Tags(List("a", "b", "c")), deriveCodec[Tags](_.withDelimiter(Delimiter.Pipe)))
      },
      test("tolerates spaces around tabs in inline arrays") {
        decode("tags[3\t]: a \t b \t c", Tags(List("a", "b", "c")), deriveCodec[Tags](_.withDelimiter(Delimiter.Tab)))
      },
      test("tolerates leading and trailing spaces in tabular row values") {
        decode(
          "people[2]{name,age}:\n  Alice , 25 \n  Bob , 30 ",
          PersonListWrapper(List(Person("Alice", 25), Person("Bob", 30))),
          deriveCodec[PersonListWrapper](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("tolerates spaces around delimiters with quoted values") {
        decode("items[3]: \"a\" , \"b\" , \"c\"", Items(List("a", "b", "c")))
      },
      test("parses empty tokens as empty string") {
        decode("items[3]: a,,c", Items(List("a", "", "c")))
      }
    ),
    suite("decode path expansion")(
      test("expands dotted key to nested object in safe mode") {
        decodeDynamic(
          "a.b.c: 1",
          record("a" -> record("b" -> record("c" -> dynamicInt(1)))),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("expands dotted key with inline array") {
        decodeDynamic(
          "data.meta.items[2]: a,b",
          record(
            "data" -> record(
              "meta" -> record("items" -> DynamicValue.Sequence(Chunk(dynamicStr("a"), dynamicStr("b"))))
            )
          ),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("expands dotted key with tabular array") {
        decodeDynamic(
          "a.b.items[2]{id,name}:\n  1,A\n  2,B",
          record(
            "a" -> record(
              "b" -> record(
                "items" -> DynamicValue.Sequence(
                  Chunk(
                    record("id" -> dynamicInt(1), "name" -> dynamicStr("A")),
                    record("id" -> dynamicInt(2), "name" -> dynamicStr("B"))
                  )
                )
              )
            )
          ),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("preserves literal dotted keys when expansion is off") {
        decodeDynamic("user.name: Ada", record("user.name" -> dynamicStr("Ada")))
      },
      test("expands and deep-merges preserving document-order insertion") {
        decodeDynamic(
          "a.b.c: 1\na.b.d: 2\na.e: 3",
          record(
            "a" -> record(
              "b" -> record("c" -> dynamicInt(1), "d" -> dynamicInt(2)),
              "e" -> dynamicInt(3)
            )
          ),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("throws on expansion conflict (object vs primitive) when strict=true") {
        decodeError(
          "a.b: 1\na: 2",
          "Path expansion conflict at key 'a': cannot overwrite existing value with new value in strict mode at: .",
          ToonBinaryCodec.dynamicValueCodec,
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("throws on expansion conflict (object vs array) when strict=true") {
        decodeError(
          "a.b: 1\na[2]: 2,3",
          "Path expansion conflict at key 'a': cannot overwrite existing value with new value in strict mode at: .",
          ToonBinaryCodec.dynamicValueCodec,
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("applies LWW when strict=false (primitive overwrites expanded object)") {
        decodeDynamic(
          "a.b: 1\na: 2",
          record("a" -> dynamicInt(2)),
          ReaderConfig.withExpandPaths(PathExpansion.Safe).withStrict(false)
        )
      },
      test("applies LWW when strict=false (expanded object overwrites primitive)") {
        decodeDynamic(
          "a: 1\na.b: 2",
          record("a" -> record("b" -> dynamicInt(2))),
          ReaderConfig.withExpandPaths(PathExpansion.Safe).withStrict(false)
        )
      },
      test("preserves quoted dotted key as literal when expandPaths=safe") {
        decodeDynamic(
          "a.b: 1\n\"c.d\": 2",
          record(
            "a"   -> record("b" -> dynamicInt(1)),
            "c.d" -> dynamicInt(2)
          ),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("preserves non-IdentifierSegment keys as literals") {
        decodeDynamic(
          "full-name.x: 1",
          record("full-name.x" -> dynamicInt(1)),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      },
      test("expands keys creating empty nested objects") {
        decodeDynamic(
          "a.b.c:",
          record("a" -> record("b" -> record("c" -> record()))),
          ReaderConfig.withExpandPaths(PathExpansion.Safe)
        )
      }
    ),
    suite("decode arrays primitive")(
      test("parses string arrays inline") {
        decodeDynamic(
          "tags[3]: reading,gaming,coding",
          record(
            "tags" -> DynamicValue.Sequence(Chunk(dynamicStr("reading"), dynamicStr("gaming"), dynamicStr("coding")))
          )
        )
      },
      test("parses number arrays inline") {
        decode("nums[3]: 1,2,3", Nums(List(1, 2, 3)))
      },
      test("parses mixed primitive arrays inline") {
        decodeDynamic(
          "data[4]: x,y,true,10",
          record(
            "data" -> DynamicValue.Sequence(
              Chunk(dynamicStr("x"), dynamicStr("y"), dynamicBoolean(true), dynamicInt(10))
            )
          )
        )
      },
      test("parses empty arrays") {
        decode("items[0]:", Items(List.empty), deriveCodec[Items](_.withTransientEmptyCollection(false)))
      },
      test("parses single-item array with empty string") {
        decodeDynamic("items[1]: \"\"", record("items" -> DynamicValue.Sequence(Chunk(dynamicStr("")))))
      },
      test("parses multi-item array with empty string") {
        decodeDynamic(
          "items[3]: a,\"\",b",
          record("items" -> DynamicValue.Sequence(Chunk(dynamicStr("a"), dynamicStr(""), dynamicStr("b"))))
        )
      },
      test("parses whitespace-only strings in arrays") {
        decodeDynamic(
          "items[2]: \" \",\"  \"",
          record("items" -> DynamicValue.Sequence(Chunk(dynamicStr(" "), dynamicStr("  "))))
        )
      },
      test("parses strings with delimiters in arrays") {
        decodeDynamic(
          "items[3]: a,\"b,c\",\"d:e\"",
          record("items" -> DynamicValue.Sequence(Chunk(dynamicStr("a"), dynamicStr("b,c"), dynamicStr("d:e"))))
        )
      },
      test("parses strings that look like primitives when quoted") {
        decodeDynamic(
          "items[4]: x,\"true\",\"42\",\"-3.14\"",
          record(
            "items" -> DynamicValue.Sequence(
              Chunk(dynamicStr("x"), dynamicStr("true"), dynamicStr("42"), dynamicStr("-3.14"))
            )
          )
        )
      },
      test("parses strings with structural tokens in arrays") {
        decodeDynamic(
          "items[3]: \"[5]\",\"- item\",\"{key}\"",
          record("items" -> DynamicValue.Sequence(Chunk(dynamicStr("[5]"), dynamicStr("- item"), dynamicStr("{key}"))))
        )
      },
      test("parses quoted key with inline array") {
        decode("\"my-key\"[3]: 1,2,3", MyKeyWrapper(List(1, 2, 3)))
      },
      test("parses quoted key containing brackets with inline array") {
        decode("\"key[test]\"[3]: 1,2,3", KeyTestWrapper(List(1, 2, 3)))
      },
      test("parses quoted key with empty array") {
        decodeDynamic("\"x-custom\"[0]:", record("x-custom" -> DynamicValue.Sequence(Chunk.empty)))
      }
    ),
    suite("decode arrays nested")(
      test("parses list arrays for non-uniform objects") {
        decodeDynamic(
          "items[2]:\n  - id: 1\n    name: First\n  - id: 2\n    name: Second\n    extra: true",
          record(
            "items" -> DynamicValue.Sequence(
              Chunk(
                record("id" -> dynamicInt(1), "name" -> dynamicStr("First")),
                record("id" -> dynamicInt(2), "name" -> dynamicStr("Second"), "extra" -> dynamicBoolean(true))
              )
            )
          )
        )
      },
      test("parses list arrays with empty items") {
        decodeDynamic(
          "items[3]:\n  - first\n  - second\n  -",
          record("items" -> DynamicValue.Sequence(Chunk(dynamicStr("first"), dynamicStr("second"), record())))
        )
      },
      test("parses list arrays with deeply nested objects") {
        decodeDynamic(
          "items[2]:\n  - properties:\n      state:\n        type: string\n  - id: 2",
          record(
            "items" -> DynamicValue.Sequence(
              Chunk(
                record("properties" -> record("state" -> record("type" -> dynamicStr("string")))),
                record("id"         -> dynamicInt(2))
              )
            )
          )
        )
      },
      test("parses list arrays containing objects with nested properties") {
        decode(
          "items[1]:\n  - id: 1\n    nested:\n      x: 1",
          ItemsWithNestedWrapper(List(ItemWithNested(1, NestedX(1)))),
          deriveCodec[ItemsWithNestedWrapper](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("parses list items whose first field is a tabular array") {
        decodeDynamic(
          "items[1]:\n  - users[2]{id,name}:\n      1,Ada\n      2,Bob\n    status: active",
          record(
            "items" -> DynamicValue.Sequence(
              Chunk(
                record(
                  "users" -> DynamicValue.Sequence(
                    Chunk(
                      record("id" -> dynamicInt(1), "name" -> dynamicStr("Ada")),
                      record("id" -> dynamicInt(2), "name" -> dynamicStr("Bob"))
                    )
                  ),
                  "status" -> dynamicStr("active")
                )
              )
            )
          )
        )
      },
      test("parses single-field list-item object with tabular array") {
        decodeDynamic(
          "items[1]:\n  - users[2]{id,name}:\n      1,Ada\n      2,Bob",
          record(
            "items" -> DynamicValue.Sequence(
              Chunk(
                record(
                  "users" -> DynamicValue.Sequence(
                    Chunk(
                      record("id" -> dynamicInt(1), "name" -> dynamicStr("Ada")),
                      record("id" -> dynamicInt(2), "name" -> dynamicStr("Bob"))
                    )
                  )
                )
              )
            )
          )
        )
      },
      test("parses objects containing arrays (including empty arrays) in list format") {
        decode(
          "items[1]:\n  - name: Ada\n    data[0]:",
          ItemsNameData(List(NameData("Ada", List.empty))),
          deriveCodec[ItemsNameData](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("parses arrays of arrays within objects") {
        decodeDynamic(
          "items[1]:\n  - matrix[2]:\n      - [2]: 1,2\n      - [2]: 3,4\n    name: grid",
          record(
            "items" -> DynamicValue.Sequence(
              Chunk(
                record(
                  "matrix" -> DynamicValue.Sequence(
                    Chunk(
                      DynamicValue.Sequence(Chunk(dynamicInt(1), dynamicInt(2))),
                      DynamicValue.Sequence(Chunk(dynamicInt(3), dynamicInt(4)))
                    )
                  ),
                  "name" -> dynamicStr("grid")
                )
              )
            )
          )
        )
      },
      test("parses nested arrays of primitives") {
        decodeDynamic(
          "pairs[2]:\n  - [2]: a,b\n  - [2]: c,d",
          record(
            "pairs" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Sequence(Chunk(dynamicStr("a"), dynamicStr("b"))),
                DynamicValue.Sequence(Chunk(dynamicStr("c"), dynamicStr("d")))
              )
            )
          )
        )
      },
      test("parses quoted strings and mixed lengths in nested arrays") {
        decodeDynamic(
          "pairs[2]:\n  - [2]: a,b\n  - [3]: \"c,d\",\"e:f\",\"true\"",
          record(
            "pairs" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Sequence(Chunk(dynamicStr("a"), dynamicStr("b"))),
                DynamicValue.Sequence(Chunk(dynamicStr("c,d"), dynamicStr("e:f"), dynamicStr("true")))
              )
            )
          )
        )
      },
      test("parses empty inner arrays") {
        decodeDynamic(
          "pairs[2]:\n  - [0]:\n  - [0]:",
          record(
            "pairs" -> DynamicValue.Sequence(
              Chunk(DynamicValue.Sequence(Chunk.empty), DynamicValue.Sequence(Chunk.empty))
            )
          )
        )
      },
      test("parses mixed-length inner arrays") {
        decodeDynamic(
          "pairs[2]:\n  - [1]: 1\n  - [2]: 2,3",
          record(
            "pairs" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Sequence(Chunk(dynamicInt(1))),
                DynamicValue.Sequence(Chunk(dynamicInt(2), dynamicInt(3)))
              )
            )
          )
        )
      },
      test("parses root-level primitive array inline") {
        decodeDynamic(
          "[5]: x,y,\"true\",true,10",
          DynamicValue.Sequence(
            Chunk(dynamicStr("x"), dynamicStr("y"), dynamicStr("true"), dynamicBoolean(true), dynamicInt(10))
          )
        )
      },
      test("parses root-level array of uniform objects in tabular format") {
        decodeDynamic(
          "[2]{id}:\n  1\n  2",
          DynamicValue.Sequence(
            Chunk(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2)))
          )
        )
      },
      test("parses root-level array of non-uniform objects in list format") {
        decodeDynamic(
          "[2]:\n  - id: 1\n  - id: 2\n    name: Ada",
          DynamicValue.Sequence(
            Chunk(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2), "name" -> dynamicStr("Ada")))
          )
        )
      },
      test("parses root-level array mixing primitive, object, and array of objects in list format") {
        decodeDynamic(
          "[3]:\n  - summary\n  - id: 1\n    name: Ada\n  - [2]:\n    - id: 2\n    - status: draft",
          DynamicValue.Sequence(
            Chunk(
              dynamicStr("summary"),
              record("id" -> dynamicInt(1), "name" -> dynamicStr("Ada")),
              DynamicValue.Sequence(Chunk(record("id" -> dynamicInt(2)), record("status" -> dynamicStr("draft"))))
            )
          )
        )
      },
      test("parses root-level array of arrays") {
        decodeDynamic(
          "[2]:\n  - [2]: 1,2\n  - [0]:",
          DynamicValue.Sequence(
            Chunk(DynamicValue.Sequence(Chunk(dynamicInt(1), dynamicInt(2))), DynamicValue.Sequence(Chunk.empty))
          )
        )
      },
      test("parses empty root-level array") {
        decodeDynamic("[0]:", DynamicValue.Sequence(Chunk.empty))
      },
      test("parses complex mixed object with arrays and nested objects") {
        decode(
          """id: 123
            |name: Ada
            |tags[2]: reading,gaming
            |active: true
            |prefs[0]:""".stripMargin,
          ComplexUser(123, "Ada", List("reading", "gaming"), true, List.empty)
        )
      },
      test("parses arrays mixing primitives, objects, and strings in list format") {
        decodeDynamic(
          "items[3]:\n  - 1\n  - a: 1\n  - text",
          record(
            "items" -> DynamicValue.Sequence(Chunk(dynamicInt(1), record("a" -> dynamicInt(1)), dynamicStr("text")))
          )
        )
      },
      test("parses arrays mixing objects and arrays") {
        decodeDynamic(
          "items[2]:\n  - a: 1\n  - [2]: 1,2",
          record(
            "items" -> DynamicValue.Sequence(
              Chunk(record("a" -> dynamicInt(1)), DynamicValue.Sequence(Chunk(dynamicInt(1), dynamicInt(2))))
            )
          )
        )
      },
      test("parses quoted key with list array format") {
        decodeDynamic(
          "\"x-items\"[2]:\n  - id: 1\n  - id: 2",
          record(
            "x-items" -> DynamicValue.Sequence(Chunk(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2))))
          )
        )
      }
    ),
    suite("decode arrays tabular")(
      test("parses tabular arrays of uniform objects") {
        decode(
          "items[2]{sku,qty,price}:\n  A1,2,9.99\n  B2,1,14.5",
          SkuQtyPriceItems(List(SkuQtyPrice("A1", 2, 9.99), SkuQtyPrice("B2", 1, 14.5))),
          deriveCodec[SkuQtyPriceItems](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("parses nulls and quoted values in tabular rows") {
        decodeDynamic(
          "items[2]{id,value}:\n  1,null\n  2,\"test\"",
          record(
            "items" -> DynamicValue.Sequence(
              Chunk(
                record("id" -> dynamicInt(1), "value" -> dynamicUnit),
                record("id" -> dynamicInt(2), "value" -> dynamicStr("test"))
              )
            )
          )
        )
      },
      test("parses quoted colon in tabular row as data") {
        decode(
          "items[2]{id,note}:\n  1,\"a:b\"\n  2,\"c:d\"",
          IdNoteItems(List(IdNote(1, "a:b"), IdNote(2, "c:d"))),
          deriveCodec[IdNoteItems](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("parses quoted header keys in tabular arrays") {
        decode(
          "items[2]{\"order:id\",\"full name\"}:\n  1,Ada\n  2,Bob",
          OrderFullNameItems(List(OrderFullName(1, "Ada"), OrderFullName(2, "Bob"))),
          deriveCodec[OrderFullNameItems](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("parses quoted key with tabular array format") {
        decodeDynamic(
          "\"x-items\"[2]{id,name}:\n  1,Ada\n  2,Bob",
          record(
            "x-items" -> DynamicValue.Sequence(
              Chunk(
                record("id" -> dynamicInt(1), "name" -> dynamicStr("Ada")),
                record("id" -> dynamicInt(2), "name" -> dynamicStr("Bob"))
              )
            )
          )
        )
      },
      test("treats unquoted colon as terminator for tabular rows and start of key-value pair") {
        decode(
          "items[2]{sku,qty,price}:\n  A1,2,9.99\n  B2,1,14.5\ncount: 2",
          ItemsTableWithCount(List(ItemRow("A1", 2, BigDecimal("9.99")), ItemRow("B2", 1, BigDecimal("14.5"))), 2),
          deriveCodec[ItemsTableWithCount](_.withArrayFormat(ArrayFormat.Tabular))
        )
      }
    ),
    suite("decode validation errors")(
      test("throws on array length mismatch (inline primitives - too many)") {
        decodeError[Tags]("tags[2]: a,b,c", "Array count mismatch: expected 2 items but got 3 at: .tags")
      },
      test("throws on tabular row value count mismatch with header field count") {
        decodeError(
          "people[2]{name,age}:\n  Ada,25\n  Bob",
          "Missing required field in tabular row: age at: .people.at(1)",
          deriveCodec[PersonListWrapper](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("throws on tabular row count mismatch with header length") {
        decodeError(
          "people[1]{name,age}:\n  Ada,25\n  Bob,30",
          "Expected key:value, no colon found at: .",
          deriveCodec[PersonListWrapper](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("throws on unterminated string") {
        decodeError[NameWrapper]("name: \"unterminated", "Unterminated string at: .name")
      },
      test("throws on invalid escape sequence") {
        decodeError[NameWrapper]("name: \"a\\x\"", "Invalid escape: \\x at: .name")
      }
    ),
    suite("decode indentation errors")(
      test("throws on object field with non-multiple indentation (3 spaces with indent=2)") {
        decodeError[DynamicValue]("a:\n   b: 1", "Indentation must be multiple of 2 spaces at: .")
      },
      test("accepts correct indentation with custom indent size (4 spaces with indent=4)") {
        decodeDynamic("a:\n    b: 1", record("a" -> record("b" -> dynamicInt(1))), ReaderConfig.withIndent(4))
      },
      test("throws on tab character used in indentation") {
        decodeError[DynamicValue]("a:\n\tb: 1", "Tabs are not allowed in indentation at: .")
      },
      test("throws on mixed tabs and spaces in indentation") {
        decodeError[DynamicValue]("a:\n \tb: 1", "Tabs are not allowed in indentation at: .")
      },
      test("accepts tabs in quoted string values") {
        decode("text: \"hello\\tworld\"", TextWrapper("hello\tworld"))
      },
      test("accepts tabs in quoted keys") {
        decodeDynamic("\"key\\ttab\": value", record("key\ttab" -> dynamicStr("value")))
      },
      test("accepts non-multiple indentation when strict=false") {
        decodeDynamic(
          "a:\n   b: 1", // 3 spaces
          record("a" -> record("b" -> dynamicInt(1))),
          ReaderConfig.withStrict(false)
        )
      },
      test("accepts deeply nested non-multiples when strict=false") {
        decodeDynamic(
          "a:\n   b:\n     c: 1",
          record("a" -> record("b" -> record("c" -> dynamicInt(1)))),
          ReaderConfig.withStrict(false)
        )
      },
      test("parses empty lines without validation errors") {
        decodeDynamic("a: 1\n\nb: 2", record("a" -> dynamicInt(1), "b" -> dynamicInt(2)))
      },
      test("parses root-level content (0 indentation) as always valid") {
        decodeDynamic("a: 1\nb: 2\nc: 3", record("a" -> dynamicInt(1), "b" -> dynamicInt(2), "c" -> dynamicInt(3)))
      }
    ),
    suite("decode blank lines")(
      test("throws on blank line inside list array") {
        decodeError[DynamicValue](
          "items[3]:\n  - a\n\n  - b\n  - c",
          "Blank lines are not allowed inside arrays/tabular blocks in strict mode at: ."
        )
      },
      test("throws on blank line inside tabular array") {
        decodeError(
          "people[2]{name,age}:\n  Ada,25\n\n  Bob,30",
          "Blank lines are not allowed inside arrays/tabular blocks in strict mode at: .people",
          deriveCodec[PersonListWrapper](_.withArrayFormat(ArrayFormat.Tabular))
        )
      },
      test("throws on multiple blank lines inside array") {
        decodeError[DynamicValue](
          "items[2]:\n  - a\n\n\n  - b",
          "Blank lines are not allowed inside arrays/tabular blocks in strict mode at: ."
        )
      },
      test("accepts blank line between root-level fields") {
        decodeDynamic("a: 1\n\nb: 2", record("a" -> dynamicInt(1), "b" -> dynamicInt(2)))
      },
      test("accepts trailing newline at end of file") {
        decodeDynamic("a: 1\n", record("a" -> dynamicInt(1)))
      },
      test("accepts multiple trailing newlines") {
        decodeDynamic("a: 1\n\n\n", record("a" -> dynamicInt(1)))
      },
      test("accepts blank line after array ends") {
        decodeDynamic(
          "items[1]:\n  - a\n\nb: 2",
          record(
            "items" -> DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.String("a")))),
            "b"     -> dynamicInt(2)
          )
        )
      },
      test("accepts blank line between nested object fields") {
        decodeDynamic("a:\n  b: 1\n\n  c: 2", record("a" -> record("b" -> dynamicInt(1), "c" -> dynamicInt(2))))
      },
      test("ignores blank lines inside list array when strict=false") {
        decodeDynamic(
          "items[3]:\n  - a\n\n  - b\n  - c",
          record(
            "items" -> DynamicValue.Sequence(
              Chunk(
                DynamicValue.Primitive(PrimitiveValue.String("a")),
                DynamicValue.Primitive(PrimitiveValue.String("b")),
                DynamicValue.Primitive(PrimitiveValue.String("c"))
              )
            )
          ),
          ReaderConfig.withStrict(false)
        )
      },
      test("ignores blank lines inside tabular array when strict=false") {
        decode(
          "people[2]{name,age}:\n  Ada,25\n\n  Bob,30",
          PersonListWrapper(List(Person("Ada", 25), Person("Bob", 30))),
          deriveCodec[PersonListWrapper](_.withArrayFormat(ArrayFormat.Tabular)),
          ReaderConfig.withStrict(false)
        )
      }
    ),
    suite("encode whitespace")(
      test("produces no trailing newline at end of output") {
        assertTrue(!deriveCodec[IdWrapper](identity).encodeToString(IdWrapper(123)).endsWith("\n"))
      },
      test("maintains proper indentation for nested structures") {
        encode(
          UserWithItems(UserSimple(123, "Ada"), List("a", "b")),
          "user:\n  id: 123\n  name: Ada\nitems[2]: a,b"
        )
      },
      test("respects custom indent size option") {
        encode(
          UserWrapper(User(123, "Ada", Contact("ada@example.com", "555-0100"))),
          "user:\n    id: 123\n    name: Ada\n    contact:\n        email: ada@example.com\n        phone: 555-0100",
          deriveCodec[UserWrapper](identity),
          WriterConfig.withIndent(4)
        )
      }
    ),
    suite("encode key folding")(
      test("encodes folded chain to primitive (safe mode)") {
        encodeDynamic(
          record("a" -> record("b" -> record("c" -> dynamicInt(1)))),
          "a.b.c: 1",
          WriterConfig.withKeyFolding(KeyFolding.Safe)
        )
      },
      test("encodes folded chain with inline array") {
        encodeDynamic(
          record(
            "data" -> record(
              "meta" -> record("items" -> DynamicValue.Sequence(Chunk(dynamicStr("x"), dynamicStr("y"))))
            )
          ),
          "data.meta.items[2]: x,y",
          WriterConfig.withKeyFolding(KeyFolding.Safe)
        )
      },
      test("skips folding on sibling literal-key collision (safe mode)") {
        encodeDynamic(
          record(
            "data" -> record(
              "meta" -> record("items" -> DynamicValue.Sequence(Chunk(dynamicInt(1), dynamicInt(2))))
            ),
            "data.meta.items" -> dynamicStr("literal")
          ),
          "data:\n  meta:\n    items[2]: 1,2\ndata.meta.items: literal",
          WriterConfig.withKeyFolding(KeyFolding.Safe)
        )
      },
      test("encodes partial folding with flattenDepth=2") {
        encodeDynamic(
          record("a" -> record("b" -> record("c" -> record("d" -> dynamicInt(1))))),
          "a.b:\n  c:\n    d: 1",
          WriterConfig.withKeyFolding(KeyFolding.Safe).withFlattenDepth(2)
        )
      },
      test("encodes full chain with flattenDepth=Infinity (default)") {
        encodeDynamic(
          record("a" -> record("b" -> record("c" -> record("d" -> dynamicInt(1))))),
          "a.b.c.d: 1",
          WriterConfig.withKeyFolding(KeyFolding.Safe)
        )
      },
      test("encodes standard nesting with flattenDepth=0 (no folding)") {
        encodeDynamic(
          record("a" -> record("b" -> record("c" -> dynamicInt(1)))),
          "a:\n  b:\n    c: 1",
          WriterConfig.withKeyFolding(KeyFolding.Safe).withFlattenDepth(0)
        )
      },
      test("encodes standard nesting with flattenDepth=1 (no practical effect)") {
        encodeDynamic(
          record("a" -> record("b" -> record("c" -> dynamicInt(1)))),
          "a:\n  b:\n    c: 1",
          WriterConfig.withKeyFolding(KeyFolding.Safe).withFlattenDepth(1)
        )
      },
      test("encodes standard nesting with keyFolding=off (baseline)") {
        encodeDynamic(
          record("a" -> record("b" -> record("c" -> dynamicInt(1)))),
          "a:\n  b:\n    c: 1",
          WriterConfig.withKeyFolding(KeyFolding.Off)
        )
      },
      test("encodes folded chain ending with empty object") {
        encodeDynamic(
          record("a" -> record("b" -> record("c" -> record()))),
          "a.b.c:",
          WriterConfig.withKeyFolding(KeyFolding.Safe)
        )
      },
      test("stops folding at array boundary (not single-key object)") {
        encodeDynamic(
          record("a" -> record("b" -> DynamicValue.Sequence(Chunk(dynamicInt(1), dynamicInt(2))))),
          "a.b[2]: 1,2",
          WriterConfig.withKeyFolding(KeyFolding.Safe)
        )
      },
      test("encodes folded chains preserving sibling field order") {
        encodeDynamic(
          record(
            "first"  -> record("second" -> record("third" -> dynamicInt(1))),
            "simple" -> dynamicInt(2),
            "short"  -> record("path" -> dynamicInt(3))
          ),
          "first.second.third: 1\nsimple: 2\nshort.path: 3",
          WriterConfig.withKeyFolding(KeyFolding.Safe)
        )
      },
      test("stops folding at multi-key records") {
        encodeDynamic(
          record("a" -> record("b" -> dynamicInt(1), "c" -> dynamicInt(2))),
          "a:\n  b: 1\n  c: 2",
          WriterConfig.withKeyFolding(KeyFolding.Safe)
        )
      }
    ),
    suite("deep nesting - records with lists of records with lists")(
      test("encodes record with list of records containing lists of primitives") {
        encode(
          TeamWrapper(
            Team("Engineering", List(Member("Alice", List("Scala", "Rust")), Member("Bob", List("Go", "Python"))))
          ),
          """team:
            |  name: Engineering
            |  members[2]:
            |    - name: Alice
            |      skills[2]:
            |        - Scala
            |        - Rust
            |    - name: Bob
            |      skills[2]:
            |        - Go
            |        - Python""".stripMargin,
          deriveCodec[TeamWrapper](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("decodes record with list of records containing lists of primitives") {
        decode(
          """team:
            |  name: Engineering
            |  members[2]:
            |    - name: Alice
            |      skills[2]: Scala,Rust
            |    - name: Bob
            |      skills[2]: Go,Python""".stripMargin,
          TeamWrapper(
            Team("Engineering", List(Member("Alice", List("Scala", "Rust")), Member("Bob", List("Go", "Python"))))
          ),
          deriveCodec[TeamWrapper](_.withArrayFormat(ArrayFormat.List))
        )
      },
      test("roundtrips deep nesting with three levels") {
        roundTrip(
          OrgWrapper(
            Organization(
              "TechCorp",
              List(
                Department(
                  "Engineering",
                  List(
                    TeamMember("Alice", List(Project("ZIO", 100), Project("Cats", 50))),
                    TeamMember("Bob", List(Project("Akka", 75)))
                  )
                ),
                Department(
                  "Design",
                  List(
                    TeamMember("Carol", List(Project("UI Kit", 200)))
                  )
                )
              )
            )
          ),
          """org:
            |  name: TechCorp
            |  departments[2]:
            |    - name: Engineering
            |      members[2]:
            |        - name: Alice
            |          projects[2]{name,hours}:
            |              ZIO,100
            |              Cats,50
            |        - name: Bob
            |          projects[1]{name,hours}:
            |              Akka,75
            |    - name: Design
            |      members[1]:
            |        - name: Carol
            |          projects[1]{name,hours}:
            |              UI Kit,200""".stripMargin,
          deriveCodec[OrgWrapper](_.withArrayFormat(ArrayFormat.List))
        )
      }
    )
  )

  case class Tags(tags: List[String])
  object Tags {
    implicit val schema: Schema[Tags] = Schema.derived
  }

  case class Nums(nums: List[Int])
  object Nums {
    implicit val schema: Schema[Nums] = Schema.derived
  }

  case class Items(items: List[String])
  object Items {
    implicit val schema: Schema[Items] = Schema.derived
  }

  case class ItemRow(sku: String, qty: Int, price: BigDecimal)
  object ItemRow {
    implicit val schema: Schema[ItemRow] = Schema.derived
  }

  case class ItemsTable(items: List[ItemRow])
  object ItemsTable {
    implicit val schema: Schema[ItemsTable] = Schema.derived
  }

  case class ItemNullable(id: Int, value: Option[String])
  object ItemNullable {
    implicit val schema: Schema[ItemNullable] = Schema.derived
  }

  case class ItemsNullable(items: List[ItemNullable])
  object ItemsNullable {
    implicit val schema: Schema[ItemsNullable] = Schema.derived
  }

  case class SimpleObject(id: Int, name: String, active: Boolean)
  object SimpleObject {
    implicit val schema: Schema[SimpleObject] = Schema.derived
  }

  case class Contact(email: String, phone: String)
  object Contact {
    implicit val schema: Schema[Contact] = Schema.derived
  }

  case class User(id: Int, name: String, contact: Contact)
  object User {
    implicit val schema: Schema[User] = Schema.derived
  }

  case class UserWrapper(user: User)
  object UserWrapper {
    implicit val schema: Schema[UserWrapper] = Schema.derived
  }

  case class ItemWithNote(id: Int, note: String)
  object ItemWithNote {
    implicit val schema: Schema[ItemWithNote] = Schema.derived
  }

  case class ItemsWithNote(items: List[ItemWithNote])
  object ItemsWithNote {
    implicit val schema: Schema[ItemsWithNote] = Schema.derived
  }

  case class NameWrapper(name: String)
  object NameWrapper {
    implicit val schema: Schema[NameWrapper] = Schema.derived
  }

  case class ValueWrapper(value: BigDecimal)
  object ValueWrapper {
    implicit val schema: Schema[ValueWrapper] = Schema.derived
  }

  case class NumsDecimal(nums: List[BigDecimal])
  object NumsDecimal {
    implicit val schema: Schema[NumsDecimal] = Schema.derived
  }

  case class TextWrapper(text: String)
  object TextWrapper {
    implicit val schema: Schema[TextWrapper] = Schema.derived
  }

  case class IdValue(id: Int, value: Option[String])
  object IdValue {
    implicit val schema: Schema[IdValue] = Schema.derived
  }

  case class NestedIntList(items: List[IntListItem])
  object NestedIntList {
    implicit val schema: Schema[NestedIntList] = Schema.derived
  }

  case class IntListItem(xs: List[Int])
  object IntListItem {
    implicit val schema: Schema[IntListItem] = Schema.derived
  }

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class PersonListWrapper(people: List[Person])
  object PersonListWrapper {
    implicit val schema: Schema[PersonListWrapper] = Schema.derived
  }

  case class UserListWrapper(users: List[User])
  object UserListWrapper {
    implicit val schema: Schema[UserListWrapper] = Schema.derived
  }

  case class StringPair(items: List[String])
  object StringPair {
    implicit val schema: Schema[StringPair] = Schema.derived
  }

  case class PairsList(pairs: List[StringPair])
  object PairsList {
    implicit val schema: Schema[PairsList] = Schema.derived
  }

  case class ComplexUser(id: Int, name: String, tags: List[String], active: Boolean, prefs: List[String])
  object ComplexUser {
    implicit val schema: Schema[ComplexUser] = Schema.derived
  }

  case class NestedX(x: Int)
  object NestedX {
    implicit val schema: Schema[NestedX] = Schema.derived
  }

  case class ItemWithNested(id: Int, nested: NestedX)
  object ItemWithNested {
    implicit val schema: Schema[ItemWithNested] = Schema.derived
  }

  case class ItemsWithNestedWrapper(items: List[ItemWithNested])
  object ItemsWithNestedWrapper {
    implicit val schema: Schema[ItemsWithNestedWrapper] = Schema.derived
  }

  case class NameNums(name: String, nums: List[Int])
  object NameNums {
    implicit val schema: Schema[NameNums] = Schema.derived
  }

  case class ItemsNameNums(items: List[NameNums])
  object ItemsNameNums {
    implicit val schema: Schema[ItemsNameNums] = Schema.derived
  }

  case class NameData(name: String, data: List[String])
  object NameData {
    implicit val schema: Schema[NameData] = Schema.derived
  }

  case class ItemsNameData(items: List[NameData])
  object ItemsNameData {
    implicit val schema: Schema[ItemsNameData] = Schema.derived
  }

  case class NumsTags(nums: List[Int], tags: List[String], name: String)
  object NumsTags {
    implicit val schema: Schema[NumsTags] = Schema.derived
  }

  case class ItemsNumsTags(items: List[NumsTags])
  object ItemsNumsTags {
    implicit val schema: Schema[ItemsNumsTags] = Schema.derived
  }

  case class ABC(a: Int, b: Int, c: Int)
  object ABC {
    implicit val schema: Schema[ABC] = Schema.derived
  }

  case class ABCItems(items: List[ABC])
  object ABCItems {
    implicit val schema: Schema[ABCItems] = Schema.derived
  }

  case class MyKeyWrapper(`my-key`: List[Int])
  object MyKeyWrapper {
    implicit val schema: Schema[MyKeyWrapper] = Schema.derived
  }

  case class KeyTestWrapper(`key[test]`: List[Int])
  object KeyTestWrapper {
    implicit val schema: Schema[KeyTestWrapper] = Schema.derived
  }

  case class OrderFullName(`order:id`: Int, `full name`: String)
  object OrderFullName {
    implicit val schema: Schema[OrderFullName] = Schema.derived
  }

  case class OrderFullNameItems(items: List[OrderFullName])
  object OrderFullNameItems {
    implicit val schema: Schema[OrderFullNameItems] = Schema.derived
  }

  case class ItemsTableWithCount(items: List[ItemRow], count: Int)
  object ItemsTableWithCount {
    implicit val schema: Schema[ItemsTableWithCount] = Schema.derived
  }

  case class ABField(`a|b`: Int)
  object ABField {
    implicit val schema: Schema[ABField] = Schema.derived
  }

  case class ABItems(items: List[ABField])
  object ABItems {
    implicit val schema: Schema[ABItems] = Schema.derived
  }

  case class IdWrapper(id: Int)
  object IdWrapper {
    implicit val schema: Schema[IdWrapper] = Schema.derived
  }

  case class UserSimple(id: Int, name: String)
  object UserSimple {
    implicit val schema: Schema[UserSimple] = Schema.derived
  }

  case class UserWithItems(user: UserSimple, items: List[String])
  object UserWithItems {
    implicit val schema: Schema[UserWithItems] = Schema.derived
  }

  case class Member(name: String, skills: List[String])
  object Member {
    implicit val schema: Schema[Member] = Schema.derived
  }

  case class Team(name: String, members: List[Member])
  object Team {
    implicit val schema: Schema[Team] = Schema.derived
  }

  case class TeamWrapper(team: Team)
  object TeamWrapper {
    implicit val schema: Schema[TeamWrapper] = Schema.derived
  }

  case class Project(name: String, hours: Int)
  object Project {
    implicit val schema: Schema[Project] = Schema.derived
  }

  case class TeamMember(name: String, projects: List[Project])
  object TeamMember {
    implicit val schema: Schema[TeamMember] = Schema.derived
  }

  case class Department(name: String, members: List[TeamMember])
  object Department {
    implicit val schema: Schema[Department] = Schema.derived
  }

  case class Organization(name: String, departments: List[Department])
  object Organization {
    implicit val schema: Schema[Organization] = Schema.derived
  }

  case class OrgWrapper(org: Organization)
  object OrgWrapper {
    implicit val schema: Schema[OrgWrapper] = Schema.derived
  }

  case class SkuDescQty(sku: String, desc: String, qty: Int)
  object SkuDescQty {
    implicit val schema: Schema[SkuDescQty] = Schema.derived
  }

  case class ItemsWithSkuDescQty(items: List[SkuDescQty])
  object ItemsWithSkuDescQty {
    implicit val schema: Schema[ItemsWithSkuDescQty] = Schema.derived
  }

  case class IdStatus(id: Int, status: String)
  object IdStatus {
    implicit val schema: Schema[IdStatus] = Schema.derived
  }

  case class ItemsWithIdStatus(items: List[IdStatus])
  object ItemsWithIdStatus {
    implicit val schema: Schema[ItemsWithIdStatus] = Schema.derived
  }

  case class NoteWrapper(note: String)
  object NoteWrapper {
    implicit val schema: Schema[NoteWrapper] = Schema.derived
  }

  case class NumsName(nums: List[Int], name: String)
  object NumsName {
    implicit val schema: Schema[NumsName] = Schema.derived
  }

  case class ItemsNumsName(items: List[NumsName])
  object ItemsNumsName {
    implicit val schema: Schema[ItemsNumsName] = Schema.derived
  }

  case class IntListWrapper(xs: List[Int])
  object IntListWrapper {
    implicit val schema: Schema[IntListWrapper] = Schema.derived
  }

  case class MatrixName(matrix: List[IntListWrapper], name: String)
  object MatrixName {
    implicit val schema: Schema[MatrixName] = Schema.derived
  }

  case class ItemsMatrixName(items: List[MatrixName])
  object ItemsMatrixName {
    implicit val schema: Schema[ItemsMatrixName] = Schema.derived
  }

  case class IdName(id: Int, name: String)
  object IdName {
    implicit val schema: Schema[IdName] = Schema.derived
  }

  case class UsersStatus(users: List[IdName], status: String)
  object UsersStatus {
    implicit val schema: Schema[UsersStatus] = Schema.derived
  }

  case class ItemsUsersStatus(items: List[UsersStatus])
  object ItemsUsersStatus {
    implicit val schema: Schema[ItemsUsersStatus] = Schema.derived
  }

  case class UserMismatch(id: Int, name: Option[String])
  object UserMismatch {
    implicit val schema: Schema[UserMismatch] = Schema.derived
  }

  case class UsersMismatch(users: List[UserMismatch], status: String)
  object UsersMismatch {
    implicit val schema: Schema[UsersMismatch] = Schema.derived
  }

  case class ItemsUsersMismatch(items: List[UsersMismatch])
  object ItemsUsersMismatch {
    implicit val schema: Schema[ItemsUsersMismatch] = Schema.derived
  }

  case class NumsTagsOnly(nums: List[Int], tags: List[String])
  object NumsTagsOnly {
    implicit val schema: Schema[NumsTagsOnly] = Schema.derived
  }

  case class ItemsNumsTagsOnly(items: List[NumsTagsOnly])
  object ItemsNumsTagsOnly {
    implicit val schema: Schema[ItemsNumsTagsOnly] = Schema.derived
  }

  case class DataName(data: List[String], name: String)
  object DataName {
    implicit val schema: Schema[DataName] = Schema.derived
  }

  case class ItemsDataName(items: List[DataName])
  object ItemsDataName {
    implicit val schema: Schema[ItemsDataName] = Schema.derived
  }

  case class IdNote(id: Int, note: String)
  object IdNote {
    implicit val schema: Schema[IdNote] = Schema.derived
  }

  case class IdNoteItems(items: List[IdNote])
  object IdNoteItems {
    implicit val schema: Schema[IdNoteItems] = Schema.derived
  }

  case class StringListItem(xs: List[String])
  object StringListItem {
    implicit val schema: Schema[StringListItem] = Schema.derived
  }

  case class NestedStringList(pairs: List[StringListItem])
  object NestedStringList {
    implicit val schema: Schema[NestedStringList] = Schema.derived
  }

  case class SkuQtyPrice(sku: String, qty: Int, price: Double)
  object SkuQtyPrice {
    implicit val schema: Schema[SkuQtyPrice] = Schema.derived
  }

  case class SkuQtyPriceItems(items: List[SkuQtyPrice])
  object SkuQtyPriceItems {
    implicit val schema: Schema[SkuQtyPriceItems] = Schema.derived
  }

  case class ItemsTags(items: List[Tags])
  object ItemsTags {
    implicit val schema: Schema[ItemsTags] = Schema.derived
  }

  case class StatusItem(status: String)
  object StatusItem {
    implicit val schema: Schema[StatusItem] = Schema.derived
  }

  case class StatusItems(items: List[StatusItem])
  object StatusItems {
    implicit val schema: Schema[StatusItems] = Schema.derived
  }
}
