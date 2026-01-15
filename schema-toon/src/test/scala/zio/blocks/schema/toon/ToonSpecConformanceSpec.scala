package zio.blocks.schema.toon

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
    suite("numbers")(
      test("negative zero normalizes to zero") {
        encode(-0.0, "0")
      },
      test("no exponent notation for large numbers") {
        encode(1000000L, "1000000")
      },
      test("decimal numbers without trailing zeros") {
        encode(3.14, "3.14") && encode(5.0, "5")
      },
      test("leading zeros decode as string, not number") {
        // "05" should be treated as a string, not the number 5
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic("value: 05", record("value" -> dynamicStr("05")), config)
      },
      test("multiple leading zeros decode as string") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic("value: 007", record("value" -> dynamicStr("007")), config)
      },
      test("negative with leading zero decodes as string") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic("value: -05", record("value" -> dynamicStr("-05")), config)
      },
      test("single zero is a valid number") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic("value: 0", record("value" -> dynamicInt(0)), config)
      },
      test("zero with decimal is a valid number") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic(
          "value: 0.5",
          record(
            "value" -> zio.blocks.schema.DynamicValue
              .Primitive(zio.blocks.schema.PrimitiveValue.BigDecimal(BigDecimal("0.5")))
          ),
          config
        )
      }
    ),
    suite("strings")(
      test("quotes empty string") {
        encode("", "\"\"")
      },
      test("quotes string that looks like true") {
        encode("true", "\"true\"")
      },
      test("quotes string that looks like false") {
        encode("false", "\"false\"")
      },
      test("quotes string that looks like null") {
        encode("null", "\"null\"")
      },
      test("quotes string that looks like integer") {
        encode("42", "\"42\"")
      },
      test("quotes string starting with hyphen") {
        encode("-item", "\"-item\"")
      },
      test("quotes single hyphen") {
        encode("-", "\"-\"")
      },
      test("escapes newline in string") {
        encode("line1\nline2", "\"line1\\nline2\"")
      },
      test("escapes tab in string") {
        encode("tab\there", "\"tab\\there\"")
      },
      test("escapes carriage return in string") {
        encode("return\rcarriage", "\"return\\rcarriage\"")
      },
      test("escapes backslash in string") {
        encode("C:\\Users\\path", "\"C:\\\\Users\\\\path\"")
      },
      test("quotes string with bracket notation") {
        encode("[test]", "\"[test]\"")
      },
      test("quotes string with brace notation") {
        encode("{key}", "\"{key}\"")
      },
      test("encodes Unicode string without quotes") {
        encode("café", "café")
      },
      test("encodes emoji without quotes") {
        encode("🚀", "🚀")
      }
    ),
    suite("inline arrays")(
      test("encodes string arrays inline") {
        val expected = "tags[2]: reading,gaming"
        encode(Tags(List("reading", "gaming")), expected)
      },
      test("encodes number arrays inline") {
        val expected = "nums[3]: 1,2,3"
        encode(Nums(List(1, 2, 3)), expected)
      },
      test("encodes empty arrays") {
        // Need to disable transientEmptyCollection to see empty arrays
        val deriver  = ToonBinaryCodecDeriver.withTransientEmptyCollection(false)
        val codec    = deriveCodec(Items.schema, deriver)
        val expected = "items[0]:"
        encode(Items(List.empty), expected, codec)
      },
      test("encodes empty string in single-item array") {
        val expected = "items[1]: \"\""
        encode(Items(List("")), expected)
      },
      test("encodes empty string in multi-item array") {
        val expected = "items[3]: a,\"\",b"
        encode(Items(List("a", "", "b")), expected)
      },
      test("quotes array strings with comma") {
        val expected = "items[3]: a,\"b,c\",\"d:e\""
        encode(Items(List("a", "b,c", "d:e")), expected)
      },
      test("quotes strings that look like booleans in arrays") {
        val expected = "items[4]: x,\"true\",\"42\",\"-3.14\""
        encode(Items(List("x", "true", "42", "-3.14")), expected)
      }
    ),
    suite("tabular arrays")(
      test("encodes arrays of uniform objects in tabular format") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(ItemsTable.schema, deriver)
        val data    = ItemsTable(
          List(
            ItemRow("A1", 2, BigDecimal("9.99")),
            ItemRow("B2", 1, BigDecimal("14.5"))
          )
        )
        val expected = """items[2]{sku,qty,price}:
                         |  A1,2,9.99
                         |  B2,1,14.5""".stripMargin
        encode(data, expected, codec)
      },
      test("encodes null values in tabular format") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(ItemsNullable.schema, deriver)
        val data    = ItemsNullable(
          List(
            ItemNullable(1, None),
            ItemNullable(2, Some("test"))
          )
        )
        val expected = """items[2]{id,value}:
                         |  1,null
                         |  2,test""".stripMargin
        encode(data, expected, codec)
      },
      test("quotes strings containing delimiters in tabular rows") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(ItemsWithSkuDescQty.schema, deriver)
        val data    = ItemsWithSkuDescQty(
          List(
            SkuDescQty("A,1", "cool", 2),
            SkuDescQty("B2", "wip: test", 1)
          )
        )
        val expected = """items[2]{sku,desc,qty}:
                         |  "A,1",cool,2
                         |  B2,"wip: test",1""".stripMargin
        encode(data, expected, codec)
      },
      test("quotes ambiguous strings in tabular rows") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(ItemsWithIdStatus.schema, deriver)
        val data    = ItemsWithIdStatus(
          List(
            IdStatus(1, "true"),
            IdStatus(2, "false")
          )
        )
        val expected = """items[2]{id,status}:
                         |  1,"true"
                         |  2,"false"""".stripMargin
        encode(data, expected, codec)
      },
      test("encodes tabular arrays with keys needing quotes") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(OrderFullNameItems.schema, deriver)
        val data    = OrderFullNameItems(
          List(
            OrderFullName(1, "Ada"),
            OrderFullName(2, "Bob")
          )
        )
        val expected = """items[2]{"order:id","full name"}:
                         |  1,Ada
                         |  2,Bob""".stripMargin
        encode(data, expected, codec)
      }
    ),
    suite("delimiters")(
      test("encodes primitive arrays with tab delimiter") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Tab)
        val codec    = deriveCodec(Tags.schema, deriver)
        val data     = Tags(List("reading", "gaming", "coding"))
        val expected = "tags[3\t]: reading\tgaming\tcoding"
        encode(data, expected, codec)
      },
      test("encodes primitive arrays with pipe delimiter") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Pipe)
        val codec    = deriveCodec(Tags.schema, deriver)
        val data     = Tags(List("reading", "gaming", "coding"))
        val expected = "tags[3|]: reading|gaming|coding"
        encode(data, expected, codec)
      },
      test("encodes primitive arrays with comma delimiter (default)") {
        val codec    = deriveCodec(Tags.schema, ToonBinaryCodecDeriver)
        val data     = Tags(List("reading", "gaming", "coding"))
        val expected = "tags[3]: reading,gaming,coding"
        encode(data, expected, codec)
      },
      test("encodes tabular arrays with tab delimiter") {
        val deriver = ToonBinaryCodecDeriver
          .withArrayFormat(ArrayFormat.Tabular)
          .withDelimiter(Delimiter.Tab)
        val codec = deriveCodec(ItemsTable.schema, deriver)
        val data  = ItemsTable(
          List(
            ItemRow("A1", 2, BigDecimal("9.99")),
            ItemRow("B2", 1, BigDecimal("14.5"))
          )
        )
        val expected = "items[2\t]{sku\tqty\tprice}:\n  A1\t2\t9.99\n  B2\t1\t14.5"
        encode(data, expected, codec)
      },
      test("encodes tabular arrays with pipe delimiter") {
        val deriver = ToonBinaryCodecDeriver
          .withArrayFormat(ArrayFormat.Tabular)
          .withDelimiter(Delimiter.Pipe)
        val codec = deriveCodec(ItemsTable.schema, deriver)
        val data  = ItemsTable(
          List(
            ItemRow("A1", 2, BigDecimal("9.99")),
            ItemRow("B2", 1, BigDecimal("14.5"))
          )
        )
        val expected = """items[2|]{sku|qty|price}:
                         |  A1|2|9.99
                         |  B2|1|14.5""".stripMargin
        encode(data, expected, codec)
      },
      test("does not quote commas with tab delimiter") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Tab)
        val codec    = deriveCodec(Items.schema, deriver)
        val data     = Items(List("a,b", "c,d"))
        val expected = "items[2\t]: a,b\tc,d"
        encode(data, expected, codec)
      },
      test("does not quote commas with pipe delimiter") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Pipe)
        val codec    = deriveCodec(Items.schema, deriver)
        val data     = Items(List("a,b", "c,d"))
        val expected = "items[2|]: a,b|c,d"
        encode(data, expected, codec)
      },
      test("quotes strings containing tab delimiter") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Tab)
        val codec    = deriveCodec(Items.schema, deriver)
        val data     = Items(List("a", "b\tc", "d"))
        val expected = "items[3\t]: a\t\"b\\tc\"\td"
        encode(data, expected, codec)
      },
      test("quotes strings containing pipe delimiter when using pipe") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Pipe)
        val codec    = deriveCodec(Items.schema, deriver)
        val data     = Items(List("a", "b|c", "d"))
        val expected = "items[3|]: a|\"b|c\"|d"
        encode(data, expected, codec)
      },
      test("quotes tabular values containing comma delimiter") {
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec    = deriveCodec(ItemsWithNote.schema, deriver)
        val data     = ItemsWithNote(List(ItemWithNote(1, "a,b"), ItemWithNote(2, "c,d")))
        val expected = "items[2]{id,note}:\n  1,\"a,b\"\n  2,\"c,d\""
        encode(data, expected, codec)
      },
      test("does not quote commas in tabular values with tab delimiter") {
        val deriver = ToonBinaryCodecDeriver
          .withArrayFormat(ArrayFormat.Tabular)
          .withDelimiter(Delimiter.Tab)
        val codec    = deriveCodec(ItemsWithNote.schema, deriver)
        val data     = ItemsWithNote(List(ItemWithNote(1, "a,b"), ItemWithNote(2, "c,d")))
        val expected = "items[2\t]{id\tnote}:\n  1\ta,b\n  2\tc,d"
        encode(data, expected, codec)
      },
      test("does not quote commas in object values with pipe delimiter") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Pipe)
        val codec    = deriveCodec(NoteWrapper.schema, deriver)
        val data     = NoteWrapper("a,b")
        val expected = "note: a,b"
        encode(data, expected, codec)
      },
      test("does not quote commas in object values with tab delimiter") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Tab)
        val codec    = deriveCodec(NoteWrapper.schema, deriver)
        val data     = NoteWrapper("a,b")
        val expected = "note: a,b"
        encode(data, expected, codec)
      },
      test("encodes nested arrays with tab delimiter") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Tab)
        val codec    = deriveCodec(NestedIntList.schema, deriver)
        val data     = NestedIntList(List(IntListItem(List(1, 2)), IntListItem(List(3, 4))))
        val expected = "items[2\t]:\n  - xs[2\t]: 1\t2\n  - xs[2\t]: 3\t4"
        encode(data, expected, codec)
      },
      test("encodes nested arrays with pipe delimiter") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Pipe)
        val codec    = deriveCodec(NestedIntList.schema, deriver)
        val data     = NestedIntList(List(IntListItem(List(1, 2)), IntListItem(List(3, 4))))
        val expected = "items[2|]:\n  - xs[2|]: 1|2\n  - xs[2|]: 3|4"
        encode(data, expected, codec)
      },
      test("encodes root-level array with tab delimiter") {
        val config = WriterConfig.withDelimiter(Delimiter.Tab)
        val input  = zio.blocks.schema.DynamicValue.Sequence(Vector(dynamicStr("x"), dynamicStr("y"), dynamicStr("z")))
        encodeDynamic(input, "[3\t]: x\ty\tz", config)
      },
      test("encodes root-level array with pipe delimiter") {
        val config = WriterConfig.withDelimiter(Delimiter.Pipe)
        val input  = zio.blocks.schema.DynamicValue.Sequence(Vector(dynamicStr("x"), dynamicStr("y"), dynamicStr("z")))
        encodeDynamic(input, "[3|]: x|y|z", config)
      },
      test("encodes root-level array of objects with tab delimiter") {
        val config = WriterConfig.withDelimiter(Delimiter.Tab)
        val input  =
          zio.blocks.schema.DynamicValue.Sequence(Vector(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2))))
        encodeDynamic(input, "[2\t]{id}:\n  1\n  2", config)
      },
      test("encodes root-level array of objects with pipe delimiter") {
        val config = WriterConfig.withDelimiter(Delimiter.Pipe)
        val input  =
          zio.blocks.schema.DynamicValue.Sequence(Vector(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2))))
        encodeDynamic(input, "[2|]{id}:\n  1\n  2", config)
      },
      test("quotes nested array values containing pipe delimiter") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Pipe)
        val codec    = deriveCodec(NestedStringList.schema, deriver)
        val data     = NestedStringList(List(StringListItem(List("a", "b|c"))))
        val expected = "pairs[1|]:\n  - xs[2|]: a|\"b|c\""
        encode(data, expected, codec)
      },
      test("quotes nested array values containing tab delimiter") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Tab)
        val codec    = deriveCodec(NestedStringList.schema, deriver)
        val data     = NestedStringList(List(StringListItem(List("a", "b\tc"))))
        val expected = "pairs[1\t]:\n  - xs[2\t]: a\t\"b\\tc\""
        encode(data, expected, codec)
      },
      test("preserves ambiguity quoting regardless of delimiter") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Pipe)
        val codec    = deriveCodec(Items.schema, deriver)
        val data     = Items(List("true", "42", "-3.14"))
        val expected = "items[3|]: \"true\"|\"42\"|\"-3.14\""
        encode(data, expected, codec)
      }
    ),
    suite("objects")(
      test("encodes simple object") {
        val expected = """id: 123
                         |name: Ada Lovelace
                         |active: true""".stripMargin
        encode(SimpleObject(123, "Ada Lovelace", true), expected)
      },
      test("object field value does not need delimiter quoting") {
        // Object field values are on their own line, so commas don't need quoting
        // (unlike inline array values which are delimiter-separated)
        val expected = "name: hello,world"
        encode(NameWrapper("hello,world"), expected)
      },
      test("encodes nested objects") {
        val expected = """user:
                         |  id: 123
                         |  name: Ada Lovelace
                         |  contact:
                         |    email: ada@example.com
                         |    phone: +1-555-0100""".stripMargin
        val data = UserWrapper(
          User(
            123,
            "Ada Lovelace",
            Contact("ada@example.com", "+1-555-0100")
          )
        )
        encode(data, expected)
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
        val deriver = ToonBinaryCodecDeriver.withTransientEmptyCollection(false)
        val codec   = deriveCodec(Items.schema, deriver)
        decode("items[0]:", Items(List.empty), codec)
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
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(ItemsTable.schema, deriver)
        decode(
          "items[2\t]{sku\tqty\tprice}:\n  A1\t2\t9.99\n  B2\t1\t14.5",
          ItemsTable(
            List(
              ItemRow("A1", 2, BigDecimal("9.99")),
              ItemRow("B2", 1, BigDecimal("14.5"))
            )
          ),
          codec
        )
      },
      test("decodes tabular arrays with pipe delimiter") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(ItemsTable.schema, deriver)
        decode(
          "items[2|]{sku|qty|price}:\n  A1|2|9.99\n  B2|1|14.5",
          ItemsTable(
            List(
              ItemRow("A1", 2, BigDecimal("9.99")),
              ItemRow("B2", 1, BigDecimal("14.5"))
            )
          ),
          codec
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
        val input    = "pairs[2\t]:\n  - [2\t]: a\tb\n  - [2\t]: c\td"
        val expected = record(
          "pairs" -> DynamicValue.Sequence(
            Vector(
              DynamicValue.Sequence(Vector(dynamicStr("a"), dynamicStr("b"))),
              DynamicValue.Sequence(Vector(dynamicStr("c"), dynamicStr("d")))
            )
          )
        )
        val config = ReaderConfig.withDelimiter(Delimiter.Tab)
        decodeDynamic(input, expected, config)
      },
      test("parses nested arrays with pipe delimiter") {
        val input    = "pairs[2|]:\n  - [2|]: a|b\n  - [2|]: c|d"
        val expected = record(
          "pairs" -> DynamicValue.Sequence(
            Vector(
              DynamicValue.Sequence(Vector(dynamicStr("a"), dynamicStr("b"))),
              DynamicValue.Sequence(Vector(dynamicStr("c"), dynamicStr("d")))
            )
          )
        )
        val config = ReaderConfig.withDelimiter(Delimiter.Pipe)
        decodeDynamic(input, expected, config)
      },
      test("parses nested arrays inside list items with default comma delimiter") {
        val deriver = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Tab).withArrayFormat(ArrayFormat.List)
        val codec   = deriveCodec(ItemsTags.schema, deriver)
        val input   = "items[1\t]:\n  - tags[3]: a,b,c"
        decode(input, ItemsTags(List(Tags(List("a", "b", "c")))), codec)
      },
      test("parses nested arrays inside list items with default comma delimiter when parent uses pipe") {
        val deriver = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Pipe).withArrayFormat(ArrayFormat.List)
        val codec   = deriveCodec(ItemsTags.schema, deriver)
        val input   = "items[1|]:\n  - tags[3]: a,b,c"
        decode(input, ItemsTags(List(Tags(List("a", "b", "c")))), codec)
      },
      test("parses root-level array with tab delimiter") {
        val input    = "[3\t]: x\ty\tz"
        val expected = DynamicValue.Sequence(Vector(dynamicStr("x"), dynamicStr("y"), dynamicStr("z")))
        val config   = ReaderConfig.withDelimiter(Delimiter.Tab)
        decodeDynamic(input, expected, config)
      },
      test("parses root-level array with pipe delimiter") {
        val input    = "[3|]: x|y|z"
        val expected = DynamicValue.Sequence(Vector(dynamicStr("x"), dynamicStr("y"), dynamicStr("z")))
        val config   = ReaderConfig.withDelimiter(Delimiter.Pipe)
        decodeDynamic(input, expected, config)
      },
      test("parses root-level array of objects with tab delimiter") {
        val input    = "[2\t]{id}:\n  1\n  2"
        val expected = DynamicValue.Sequence(
          Vector(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2)))
        )
        val config = ReaderConfig.withDelimiter(Delimiter.Tab)
        decodeDynamic(input, expected, config)
      },
      test("parses root-level array of objects with pipe delimiter") {
        val input    = "[2|]{id}:\n  1\n  2"
        val expected = DynamicValue.Sequence(
          Vector(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2)))
        )
        val config = ReaderConfig.withDelimiter(Delimiter.Pipe)
        decodeDynamic(input, expected, config)
      },
      test("object values in list items follow document delimiter") {
        val deriver = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Tab).withArrayFormat(ArrayFormat.List)
        val codec   = deriveCodec(StatusItems.schema, deriver)
        val input   = "items[2\t]:\n  - status: a,b\n  - status: c,d"
        decode(input, StatusItems(List(StatusItem("a,b"), StatusItem("c,d"))), codec)
      },
      test("object values with comma must be quoted when document delimiter is comma") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec   = deriveCodec(StatusItems.schema, deriver)
        val input   = "items[2]:\n  - status: \"a,b\"\n  - status: \"c,d\""
        decode(input, StatusItems(List(StatusItem("a,b"), StatusItem("c,d"))), codec)
      },
      test("parses nested array values containing pipe delimiter") {
        val input    = "pairs[1|]:\n  - [2|]: a|\"b|c\""
        val expected = record(
          "pairs" -> DynamicValue.Sequence(
            Vector(DynamicValue.Sequence(Vector(dynamicStr("a"), dynamicStr("b|c"))))
          )
        )
        val config = ReaderConfig.withDelimiter(Delimiter.Pipe)
        decodeDynamic(input, expected, config)
      },
      test("parses nested array values containing tab delimiter") {
        val input    = "pairs[1\t]:\n  - [2\t]: a\t\"b\\tc\""
        val expected = record(
          "pairs" -> DynamicValue.Sequence(
            Vector(DynamicValue.Sequence(Vector(dynamicStr("a"), dynamicStr("b\tc"))))
          )
        )
        val config = ReaderConfig.withDelimiter(Delimiter.Tab)
        decodeDynamic(input, expected, config)
      },
      test("parses tabular headers with keys containing the active delimiter") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular).withDelimiter(Delimiter.Pipe)
        val codec   = deriveCodec(ABItems.schema, deriver)
        val input   = "items[2|]{\"a|b\"}:\n  1\n  2"
        decode(input, ABItems(List(ABField(1), ABField(2))), codec)
      }
    ),
    suite("decode tabular arrays")(
      test("decodes tabular arrays of uniform objects") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(ItemsTable.schema, deriver)
        decode(
          "items[2]{sku,qty,price}:\n  A1,2,9.99\n  B2,1,14.5",
          ItemsTable(
            List(
              ItemRow("A1", 2, BigDecimal("9.99")),
              ItemRow("B2", 1, BigDecimal("14.5"))
            )
          ),
          codec
        )
      },
      test("decodes nulls and quoted values in tabular rows") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(ItemsNullable.schema, deriver)
        decode(
          "items[2]{id,value}:\n  1,null\n  2,\"test\"",
          ItemsNullable(
            List(
              ItemNullable(1, None),
              ItemNullable(2, Some("test"))
            )
          ),
          codec
        )
      },
      test("decodes quoted colon in tabular row as data") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(ItemsWithNote.schema, deriver)
        decode(
          "items[2]{id,note}:\n  1,\"a:b\"\n  2,\"c:d\"",
          ItemsWithNote(
            List(
              ItemWithNote(1, "a:b"),
              ItemWithNote(2, "c:d")
            )
          ),
          codec
        )
      },
      test("decodes tabular values containing comma with comma delimiter") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(ItemsWithNote.schema, deriver)
        decode(
          "items[2]{id,note}:\n  1,\"a,b\"\n  2,\"c,d\"",
          ItemsWithNote(
            List(
              ItemWithNote(1, "a,b"),
              ItemWithNote(2, "c,d")
            )
          ),
          codec
        )
      },
      test("does not require quoting commas with tab delimiter") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular).withDelimiter(Delimiter.Tab)
        val codec   = deriveCodec(ItemsWithNote.schema, deriver)
        decode(
          "items[2\t]{id\tnote}:\n  1\ta,b\n  2\tc,d",
          ItemsWithNote(
            List(
              ItemWithNote(1, "a,b"),
              ItemWithNote(2, "c,d")
            )
          ),
          codec
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
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("42", dynamicInt(42), config)
      },
      test("parses negative integer") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("-7", dynamicInt(-7), config)
      },
      test("parses true") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic(
          "true",
          zio.blocks.schema.DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Boolean(true)),
          config
        )
      },
      test("parses false") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic(
          "false",
          zio.blocks.schema.DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Boolean(false)),
          config
        )
      },
      test("parses null") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("null", zio.blocks.schema.DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Unit), config)
      },
      test("parses string with emoji and spaces") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("hello 👋 world", dynamicStr("hello 👋 world"), config)
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
            List(
              BigDecimal("42"),
              BigDecimal("-1000"),
              BigDecimal("1.5"),
              BigDecimal("0"),
              BigDecimal("250")
            )
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
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic("05", dynamicStr("05"), config)
      },
      test("treats unquoted multi-leading-zero as string") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic("007", dynamicStr("007"), config)
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
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic("0123", dynamicStr("0123"), config)
      },
      test("treats leading-zero in object value as string") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic("a: 05", record("a" -> dynamicStr("05")), config)
      },
      test("treats unquoted negative leading-zero number as string") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic("-05", dynamicStr("-05"), config)
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
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(ItemsWithNote.schema, deriver)
        decode(
          "items[2]{id,note}:\n  1 , Alice \n  2 , Bob ",
          ItemsWithNote(
            List(
              ItemWithNote(1, "Alice"),
              ItemWithNote(2, "Bob")
            )
          ),
          codec
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
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("user.name: Ada", record("user.name" -> dynamicStr("Ada")), config)
      },
      test("parses underscore-prefixed keys") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("_private: 1", record("_private" -> dynamicInt(1)), config)
      },
      test("parses underscore-containing keys") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("user_name: 1", record("user_name" -> dynamicInt(1)), config)
      },
      test("parses quoted key with colon") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("\"order:id\": 7", record("order:id" -> dynamicInt(7)), config)
      },
      test("parses quoted key with brackets") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("\"[index]\": 5", record("[index]" -> dynamicInt(5)), config)
      },
      test("parses quoted key with braces") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("\"{key}\": 5", record("{key}" -> dynamicInt(5)), config)
      },
      test("parses quoted key with comma") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("\"a,b\": 1", record("a,b" -> dynamicInt(1)), config)
      },
      test("parses quoted key with spaces") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("\"full name\": Ada", record("full name" -> dynamicStr("Ada")), config)
      },
      test("parses quoted key with leading hyphen") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("\"-lead\": 1", record("-lead" -> dynamicInt(1)), config)
      },
      test("parses quoted numeric key") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("\"123\": x", record("123" -> dynamicStr("x")), config)
      },
      test("parses quoted empty string key") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("\"\": 1", record("" -> dynamicInt(1)), config)
      },
      test("unescapes newline in key") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("\"line\\nbreak\": 1", record("line\nbreak" -> dynamicInt(1)), config)
      },
      test("unescapes tab in key") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("\"tab\\there\": 2", record("tab\there" -> dynamicInt(2)), config)
      },
      test("unescapes quotes in key") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("\"he said \\\"hi\\\"\": 1", record("he said \"hi\"" -> dynamicInt(1)), config)
      },
      test("parses quoted key with leading and trailing spaces") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("\" a \": 1", record(" a " -> dynamicInt(1)), config)
      },
      test("parses empty nested object header") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("user:", record("user" -> record()), config)
      },
      test("parses deeply nested objects with indentation") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("a:\n  b:\n    c: deep", record("a" -> record("b" -> record("c" -> dynamicStr("deep")))), config)
      },
      test("parses quoted object value with newline escape") {
        decode("text: \"line1\\nline2\"", TextWrapper("line1\nline2"))
      },
      test("parses quoted object value with escaped quotes") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("text: \"say \\\"hello\\\"\"", record("text" -> dynamicStr("say \"hello\"")), config)
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
        val input = "items[2]:\n\t- 1\n  - 2"
        val codec = ToonTestUtils.deriveCodec(Nums.schema, ToonBinaryCodecDeriver)
        decodeError(input, "Tabs are not allowed in indentation", codec, ReaderConfig.withStrict(true))
      },
      test("tabs in indentation accepted in non-strict mode") {
        val input  = "name: test"
        val config = ReaderConfig.withStrict(false)
        decode(input, NameWrapper("test"), config)
      },
      test("blank lines inside tabular arrays error in strict mode") {
        val input   = "items[3]{id,note}:\n  1,a\n\n  2,b\n  3,c"
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = ToonTestUtils.deriveCodec(ItemsWithNote.schema, deriver)
        decodeError(input, "Blank lines are not allowed inside arrays", codec, ReaderConfig.withStrict(true))
      },
      test("blank lines inside tabular arrays parsed correctly") {
        val input   = "items[2]{id,note}:\n  1,first\n  2,second"
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = ToonTestUtils.deriveCodec(ItemsWithNote.schema, deriver)
        val config  = ReaderConfig.withStrict(true)
        decode(input, ItemsWithNote(List(ItemWithNote(1, "first"), ItemWithNote(2, "second"))), codec, config)
      },
      test("indentation not multiple of indent size errors in strict mode") {
        val input = "user:\n   name: Ada" // 3 spaces instead of 2
        val codec = ToonTestUtils.deriveCodec(UserWrapper.schema, ToonBinaryCodecDeriver)
        decodeError(input, "Indentation must be multiple of", codec, ReaderConfig.withStrict(true))
      },
      test("path expansion conflict errors in strict mode") {
        val input  = "a.b: 1\na: 2"
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe).withStrict(true)
        decodeDynamicError(input, "Path expansion conflict", config)
      },
      test("path expansion conflict object vs array errors in strict mode") {
        val input  = "a.b: 1\na[2]: 2,3"
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe).withStrict(true)
        decodeDynamicError(input, "Path expansion conflict", config)
      },
      test("path expansion conflict uses LWW in non-strict mode") {
        // In non-strict mode, LWW applies: a: 2 wins over a.b: 1
        val input  = "a.b: 1\na: 2"
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe).withStrict(false)
        decodeDynamic(input, record("a" -> dynamicInt(2)), config)
      },
      test("path expansion with record merge succeeds in strict mode") {
        // When both are records, they merge - this is NOT a conflict
        val input  = "a.b: 1\na.c: 2"
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe).withStrict(true)
        decodeDynamic(input, record("a" -> record("b" -> dynamicInt(1), "c" -> dynamicInt(2))), config)
      },
      test("array count mismatch errors for inline arrays") {
        // Declared [3] but only 2 values provided
        val input = "nums[3]: 1,2"
        decodeError[Nums](input, "Array count mismatch: expected 3 items but got 2")
      },
      test("array count mismatch errors for too many items") {
        // Declared [2] but 3 values provided
        val input = "nums[2]: 1,2,3"
        decodeError[Nums](input, "Array count mismatch: expected 2 items but got 3")
      },
      test("array count match succeeds") {
        // Declared [3] with exactly 3 values
        val input = "nums[3]: 1,2,3"
        decode(input, Nums(List(1, 2, 3)))
      }
    ),
    suite("decode quoted keys")(
      test("decodes quoted key in array header") {
        val reader = ToonReader.fresh(ReaderConfig)
        reader.reset("\"my-key\"[3]: 1,2,3".getBytes(java.nio.charset.StandardCharsets.UTF_8), 0, 18)
        val header = reader.parseArrayHeader()
        assertTrue(header.key == "my-key") &&
        assertTrue(header.length == 3)
      },
      test("decodes quoted key containing brackets in array header") {
        val reader = ToonReader.fresh(ReaderConfig)
        val input  = "\"key[test]\"[3]: 1,2,3"
        reader.reset(input.getBytes(java.nio.charset.StandardCharsets.UTF_8), 0, input.length)
        val header = reader.parseArrayHeader()
        assertTrue(header.key == "key[test]") &&
        assertTrue(header.length == 3)
      },
      test("decodes quoted key with tabular array format") {
        val reader = ToonReader.fresh(ReaderConfig)
        val input  = "\"x-items\"[2]{id,name}:\n  1,Ada\n  2,Bob"
        reader.reset(input.getBytes(java.nio.charset.StandardCharsets.UTF_8), 0, input.length)
        val header = reader.parseArrayHeader()
        assertTrue(header.key == "x-items") &&
        assertTrue(header.length == 2) &&
        assertTrue(header.fields.toList == List("id", "name"))
      },
      test("decodes quoted header keys in tabular arrays") {
        val reader = ToonReader.fresh(ReaderConfig)
        val input  = "items[2]{\"order:id\",\"full name\"}:\n  1,Ada\n  2,Bob"
        reader.reset(input.getBytes(java.nio.charset.StandardCharsets.UTF_8), 0, input.length)
        val header = reader.parseArrayHeader()
        assertTrue(header.key == "items") &&
        assertTrue(header.length == 2) &&
        assertTrue(header.fields.toList == List("order:id", "full name"))
      },
      test("decodes quoted key in key-value pair") {
        val reader = ToonReader.fresh(ReaderConfig)
        val input  = "\"my-key\": value"
        reader.reset(input.getBytes(java.nio.charset.StandardCharsets.UTF_8), 0, input.length)
        val key = reader.readKey()
        assertTrue(key == "my-key")
      },
      test("decodes quoted key with colon inside") {
        val reader = ToonReader.fresh(ReaderConfig)
        val input  = "\"key:with:colons\": value"
        reader.reset(input.getBytes(java.nio.charset.StandardCharsets.UTF_8), 0, input.length)
        val key = reader.readKey()
        assertTrue(key == "key:with:colons")
      },
      test("decodes quoted key with escape sequences") {
        val reader = ToonReader.fresh(ReaderConfig)
        val input  = "\"key\\twith\\ttabs\": value"
        reader.reset(input.getBytes(java.nio.charset.StandardCharsets.UTF_8), 0, input.length)
        val key = reader.readKey()
        assertTrue(key == "key\twith\ttabs")
      }
    ),
    suite("root form discovery")(
      test("empty document decodes to empty record") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        // Empty document should decode to empty object (Unit value in DynamicValue)
        val codec  = ToonBinaryCodec.dynamicValueCodec
        val result = codec.decode("", config)
        assertTrue(result.isRight)
      },
      test("single primitive at root decodes correctly") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("42", dynamicInt(42), config)
      },
      test("single string primitive at root") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("hello", dynamicStr("hello"), config)
      },
      test("object at root decodes correctly") {
        val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic("key: value", record("key" -> dynamicStr("value")), config)
      }
    ),
    suite("nested arrays")(
      test("encodes nested primitive arrays in list format") {
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec    = deriveCodec(NestedIntList.schema, deriver)
        val data     = NestedIntList(List(IntListItem(List(1, 2)), IntListItem(List(3, 4))))
        val expected = """items[2]:
                         |  - xs[2]:
                         |    - 1
                         |    - 2
                         |  - xs[2]:
                         |    - 3
                         |    - 4""".stripMargin
        encode(data, expected, codec)
      },
      test("encodes empty inner arrays") {
        val deriver = ToonBinaryCodecDeriver
          .withArrayFormat(ArrayFormat.List)
          .withTransientEmptyCollection(false)
        val codec    = deriveCodec(NestedIntList.schema, deriver)
        val data     = NestedIntList(List(IntListItem(List.empty), IntListItem(List(1))))
        val expected = """items[2]:
                         |  - xs[0]:
                         |  - xs[1]:
                         |    - 1""".stripMargin
        encode(data, expected, codec)
      }
    ),
    suite("roundtrip")(
      test("primitive array roundtrips") {
        roundTrip(Tags(List("reading", "gaming")), "tags[2]: reading,gaming")
      },
      test("tabular array roundtrips") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(ItemsTable.schema, deriver)
        val data    = ItemsTable(
          List(
            ItemRow("A1", 2, BigDecimal("9.99")),
            ItemRow("B2", 1, BigDecimal("14.5"))
          )
        )
        val expected = """items[2]{sku,qty,price}:
                         |  A1,2,9.99
                         |  B2,1,14.5""".stripMargin
        roundTrip(data, expected, codec)
      },
      test("tabular array with pipe delimiter roundtrips") {
        val deriver = ToonBinaryCodecDeriver
          .withArrayFormat(ArrayFormat.Tabular)
          .withDelimiter(Delimiter.Pipe)
        val codec = deriveCodec(ItemsTable.schema, deriver)
        val data  = ItemsTable(
          List(
            ItemRow("A1", 2, BigDecimal("9.99")),
            ItemRow("B2", 1, BigDecimal("14.5"))
          )
        )
        val expected = """items[2|]{sku|qty|price}:
                         |  A1|2|9.99
                         |  B2|1|14.5""".stripMargin
        roundTrip(data, expected, codec)
      },
      test("tabular array with nullable values roundtrips") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(ItemsNullable.schema, deriver)
        val data    = ItemsNullable(
          List(
            ItemNullable(1, None),
            ItemNullable(2, Some("test"))
          )
        )
        val expected = """items[2]{id,value}:
                         |  1,null
                         |  2,test""".stripMargin
        roundTrip(data, expected, codec)
      },
      test("nested object roundtrips") {
        val expected = """user:
                         |  id: 123
                         |  name: Ada Lovelace
                         |  contact:
                         |    email: ada@example.com
                         |    phone: +1-555-0100""".stripMargin
        val data = UserWrapper(
          User(
            123,
            "Ada Lovelace",
            Contact("ada@example.com", "+1-555-0100")
          )
        )
        roundTrip(data, expected)
      },
      test("inline array with quoted delimiters roundtrips") {
        val expected = "items[3]: a,\"b,c\",\"d:e\""
        val data     = Items(List("a", "b,c", "d:e"))
        roundTrip(data, expected)
      },
      test("inline array with quoted booleans roundtrips") {
        val expected = "items[4]: x,\"true\",\"42\",\"-3.14\""
        val data     = Items(List("x", "true", "42", "-3.14"))
        roundTrip(data, expected)
      },
      test("tabular array with tab delimiter roundtrips") {
        val deriver = ToonBinaryCodecDeriver
          .withArrayFormat(ArrayFormat.Tabular)
          .withDelimiter(Delimiter.Tab)
        val codec = deriveCodec(ItemsTable.schema, deriver)
        val data  = ItemsTable(
          List(
            ItemRow("A1", 2, BigDecimal("9.99")),
            ItemRow("B2", 1, BigDecimal("14.5"))
          )
        )
        val expected = "items[2\t]{sku\tqty\tprice}:\n  A1\t2\t9.99\n  B2\t1\t14.5"
        roundTrip(data, expected, codec)
      },
      test("empty array roundtrips") {
        val deriver  = ToonBinaryCodecDeriver.withTransientEmptyCollection(false)
        val codec    = deriveCodec(Items.schema, deriver)
        val expected = "items[0]:"
        roundTrip(Items(List.empty), expected, codec)
      },
      test("empty string in array roundtrips") {
        val expected = "items[1]: \"\""
        roundTrip(Items(List("")), expected)
      },
      test("pipe delimiter does not quote commas") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Pipe)
        val codec    = deriveCodec(Items.schema, deriver)
        val data     = Items(List("a,b", "c,d"))
        val expected = "items[2|]: a,b|c,d"
        roundTrip(data, expected, codec)
      },
      test("pipe delimiter quotes pipes") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Pipe)
        val codec    = deriveCodec(Items.schema, deriver)
        val data     = Items(List("a", "b|c", "d"))
        val expected = "items[3|]: a|\"b|c\"|d"
        roundTrip(data, expected, codec)
      }
    ),
    suite("key folding")(
      test("encodes folded chain to primitive in safe mode") {
        // {a: {b: {c: 1}}} -> "a.b.c: 1"
        val input    = record("a" -> record("b" -> record("c" -> dynamicInt(1))))
        val expected = "a.b.c: 1"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe)
        encodeDynamic(input, expected, config)
      },
      test("encodes standard nesting with keyFolding off") {
        // {a: {b: {c: 1}}} -> "a:\n  b:\n    c: 1"
        val input    = record("a" -> record("b" -> record("c" -> dynamicInt(1))))
        val expected = "a:\n  b:\n    c: 1"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Off)
        encodeDynamic(input, expected, config)
      },
      test("encodes full chain with flattenDepth infinity") {
        // {a: {b: {c: {d: 1}}}} -> "a.b.c.d: 1"
        val input    = record("a" -> record("b" -> record("c" -> record("d" -> dynamicInt(1)))))
        val expected = "a.b.c.d: 1"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe)
        encodeDynamic(input, expected, config)
      },
      test("encodes partial folding with flattenDepth 2") {
        // {a: {b: {c: {d: 1}}}} with depth 2 -> "a.b:\n  c:\n    d: 1"
        val input    = record("a" -> record("b" -> record("c" -> record("d" -> dynamicInt(1)))))
        val expected = "a.b:\n  c:\n    d: 1"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe).withFlattenDepth(2)
        encodeDynamic(input, expected, config)
      },
      test("encodes standard nesting with flattenDepth 0") {
        // {a: {b: {c: 1}}} with depth 0 -> standard nesting
        val input    = record("a" -> record("b" -> record("c" -> dynamicInt(1))))
        val expected = "a:\n  b:\n    c: 1"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe).withFlattenDepth(0)
        encodeDynamic(input, expected, config)
      },
      test("encodes standard nesting with flattenDepth 1") {
        // {a: {b: {c: 1}}} with depth 1 -> standard nesting (need at least 2 for folding)
        val input    = record("a" -> record("b" -> record("c" -> dynamicInt(1))))
        val expected = "a:\n  b:\n    c: 1"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe).withFlattenDepth(1)
        encodeDynamic(input, expected, config)
      },
      test("skips folding when segment requires quotes in safe mode") {
        // {data: {"full-name": {x: 1}}} -> standard nesting (full-name has hyphen)
        val input    = record("data" -> record("full-name" -> record("x" -> dynamicInt(1))))
        val expected = "data:\n  \"full-name\":\n    x: 1"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe)
        encodeDynamic(input, expected, config)
      },
      test("encodes folded chain ending with empty object") {
        // {a: {b: {c: {}}}} -> "a.b.c:"
        val input    = record("a" -> record("b" -> record("c" -> record())))
        val expected = "a.b.c:"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe)
        encodeDynamic(input, expected, config)
      },
      test("encodes folded chains preserving sibling field order") {
        // Multiple folded chains should preserve order
        val input = record(
          "first"  -> record("second" -> record("third" -> dynamicInt(1))),
          "simple" -> dynamicInt(2),
          "short"  -> record("path" -> dynamicInt(3))
        )
        val expected = "first.second.third: 1\nsimple: 2\nshort.path: 3"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe)
        encodeDynamic(input, expected, config)
      },
      test("stops folding at multi-key records") {
        // {a: {b: 1, c: 2}} -> "a:\n  b: 1\n  c: 2" (not single-key, so no folding)
        val input    = record("a" -> record("b" -> dynamicInt(1), "c" -> dynamicInt(2)))
        val expected = "a:\n  b: 1\n  c: 2"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe)
        encodeDynamic(input, expected, config)
      },
      test("encodes folded chain with tabular array") {
        // {a: {b: {items: [{id: 1, name: A}, {id: 2, name: B}]}}}
        val input = record(
          "a" -> record(
            "b" -> record(
              "items" -> zio.blocks.schema.DynamicValue.Sequence(
                Vector(
                  record("id" -> dynamicInt(1), "name" -> dynamicStr("A")),
                  record("id" -> dynamicInt(2), "name" -> dynamicStr("B"))
                )
              )
            )
          )
        )
        val expected = "a.b.items[2]{id,name}:\n  1,A\n  2,B"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe)
        encodeDynamic(input, expected, config)
      },
      test("stops folding at array boundary") {
        // {a: {b: [1, 2]}} -> "a.b[2]: 1,2"
        val input = record(
          "a" -> record(
            "b" -> zio.blocks.schema.DynamicValue.Sequence(
              Vector(dynamicInt(1), dynamicInt(2))
            )
          )
        )
        val expected = "a.b[2]: 1,2"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe)
        encodeDynamic(input, expected, config)
      }
    ),
    suite("path expansion")(
      test("expands dotted key to nested object in safe mode") {
        // "a.b.c: 1" -> {a: {b: {c: 1}}}
        val input    = "a.b.c: 1"
        val expected = record("a" -> record("b" -> record("c" -> dynamicInt(1))))
        val config   = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic(input, expected, config)
      },
      test("preserves literal dotted keys when expansion is off") {
        // "user.name: Ada" with off -> {"user.name": "Ada"}
        val input    = "user.name: Ada"
        val expected = record("user.name" -> dynamicStr("Ada"))
        val config   = ReaderConfig.withExpandPaths(PathExpansion.Off)
        decodeDynamic(input, expected, config)
      },
      test("expands and deep-merges preserving document order") {
        // "a.b.c: 1\na.b.d: 2\na.e: 3" -> {a: {b: {c: 1, d: 2}, e: 3}}
        val input    = "a.b.c: 1\na.b.d: 2\na.e: 3"
        val expected = record(
          "a" -> record(
            "b" -> record("c" -> dynamicInt(1), "d" -> dynamicInt(2)),
            "e" -> dynamicInt(3)
          )
        )
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic(input, expected, config)
      },
      test("applies LWW when strict false and primitive overwrites object") {
        // "a.b: 1\na: 2" with strict=false -> {a: 2}
        val input    = "a.b: 1\na: 2"
        val expected = record("a" -> dynamicInt(2))
        val config   = ReaderConfig.withExpandPaths(PathExpansion.Safe).withStrict(false)
        decodeDynamic(input, expected, config)
      },
      test("applies LWW when strict false and object overwrites primitive") {
        // "a: 1\na.b: 2" with strict=false -> {a: {b: 2}}
        val input    = "a: 1\na.b: 2"
        val expected = record("a" -> record("b" -> dynamicInt(2)))
        val config   = ReaderConfig.withExpandPaths(PathExpansion.Safe).withStrict(false)
        decodeDynamic(input, expected, config)
      },
      test("preserves quoted dotted key as literal in safe mode") {
        // 'a.b: 1\n"c.d": 2' -> {a: {b: 1}, "c.d": 2}
        val input    = "a.b: 1\n\"c.d\": 2"
        val expected = record(
          "a"   -> record("b" -> dynamicInt(1)),
          "c.d" -> dynamicInt(2)
        )
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic(input, expected, config)
      },
      test("preserves non-identifier segment keys as literals") {
        // "full-name.x: 1" -> {"full-name.x": 1} (hyphen not allowed in identifier)
        val input    = "full-name.x: 1"
        val expected = record("full-name.x" -> dynamicInt(1))
        val config   = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic(input, expected, config)
      },
      test("expands keys creating empty nested objects") {
        // "a.b.c:" -> {a: {b: {c: {}}}}
        val input    = "a.b.c:"
        val expected = record("a" -> record("b" -> record("c" -> record())))
        val config   = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic(input, expected, config)
      },
      test("expands dotted key with inline array") {
        // "data.meta.items[2]: a,b" -> {data: {meta: {items: [a, b]}}}
        val input    = "data.meta.items[2]: a,b"
        val expected = record(
          "data" -> record(
            "meta" -> record(
              "items" -> zio.blocks.schema.DynamicValue.Sequence(
                Vector(dynamicStr("a"), dynamicStr("b"))
              )
            )
          )
        )
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic(input, expected, config)
      },
      test("expands dotted key with tabular array") {
        // "a.b.items[2]{id,name}:\n  1,A\n  2,B" -> {a: {b: {items: [...]}}}
        val input    = "a.b.items[2]{id,name}:\n  1,A\n  2,B"
        val expected = record(
          "a" -> record(
            "b" -> record(
              "items" -> zio.blocks.schema.DynamicValue.Sequence(
                Vector(
                  record("id" -> dynamicInt(1), "name" -> dynamicStr("A")),
                  record("id" -> dynamicInt(2), "name" -> dynamicStr("B"))
                )
              )
            )
          )
        )
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        decodeDynamic(input, expected, config)
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
        // 1e6 input represented as decimal 1000000
        encode(1000000L, "1000000")
      },
      test("encodes small decimal from scientific notation") {
        // 1e-6 = 0.000001
        encode(BigDecimal("0.000001"), "0.000001")
      },
      test("encodes large number") {
        // 1e20 represented as decimal
        encode(BigDecimal("100000000000000000000"), "100000000000000000000")
      },
      test("encodes MAX_SAFE_INTEGER") {
        encode(9007199254740991L, "9007199254740991")
      },
      test("encodes repeating decimal with full precision") {
        // Result of 1/3 in JavaScript: 0.3333333333333333
        encode(BigDecimal("0.3333333333333333"), "0.3333333333333333")
      },
      test("encodes null") {
        // Null as root primitive using DynamicValue
        val config = WriterConfig
        encodeDynamic(zio.blocks.schema.DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Unit), "null", config)
      }
    ),
    suite("encode objects")(
      test("preserves key order in objects") {
        val expected = "id: 123\nname: Ada\nactive: true"
        encode(SimpleObject(123, "Ada", true), expected)
      },
      test("encodes null values in objects") {
        // Need to disable transientNone to see null values serialized
        val deriver  = ToonBinaryCodecDeriver.withTransientNone(false)
        val codec    = deriveCodec(IdValue.schema, deriver)
        val expected = "id: 123\nvalue: null"
        encode(IdValue(123, None), expected, codec)
      },
      test("quotes string value with colon") {
        encode(NameWrapper("a:b"), "name: \"a:b\"")
      },
      test("does not quote string value with comma (commas only special in arrays)") {
        // Object field values are on their own line, so commas don't need quoting
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
        val expected = "user:\n  id: 123\n  name: Ada\n  contact:\n    email: ada@example.com\n    phone: 555-0100"
        val data     = UserWrapper(User(123, "Ada", Contact("ada@example.com", "555-0100")))
        encode(data, expected)
      },
      test("encodes empty objects as empty string") {
        val config = WriterConfig
        encodeDynamic(record(), "", config)
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
        val config = WriterConfig
        encodeDynamic(record("order:id" -> dynamicInt(7)), "\"order:id\": 7", config)
      },
      test("quotes key with brackets") {
        val config = WriterConfig
        encodeDynamic(record("[index]" -> dynamicInt(5)), "\"[index]\": 5", config)
      },
      test("quotes key with braces") {
        val config = WriterConfig
        encodeDynamic(record("{key}" -> dynamicInt(5)), "\"{key}\": 5", config)
      },
      test("quotes key with comma") {
        val config = WriterConfig
        encodeDynamic(record("a,b" -> dynamicInt(1)), "\"a,b\": 1", config)
      },
      test("quotes key with spaces") {
        val config = WriterConfig
        encodeDynamic(record("full name" -> dynamicStr("Ada")), "\"full name\": Ada", config)
      },
      test("quotes key with leading hyphen") {
        val config = WriterConfig
        encodeDynamic(record("-lead" -> dynamicInt(1)), "\"-lead\": 1", config)
      },
      test("quotes key with leading and trailing spaces") {
        val config = WriterConfig
        encodeDynamic(record(" a " -> dynamicInt(1)), "\" a \": 1", config)
      },
      test("quotes numeric key") {
        val config = WriterConfig
        encodeDynamic(record("123" -> dynamicStr("x")), "\"123\": x", config)
      },
      test("quotes empty string key") {
        val config = WriterConfig
        encodeDynamic(record("" -> dynamicInt(1)), "\"\": 1", config)
      },
      test("escapes newline in key") {
        val config = WriterConfig
        encodeDynamic(record("line\nbreak" -> dynamicInt(1)), "\"line\\nbreak\": 1", config)
      },
      test("escapes tab in key") {
        val config = WriterConfig
        encodeDynamic(record("tab\there" -> dynamicInt(2)), "\"tab\\there\": 2", config)
      },
      test("escapes quotes in key") {
        val config = WriterConfig
        encodeDynamic(record("he said \"hi\"" -> dynamicInt(1)), "\"he said \\\"hi\\\"\": 1", config)
      },
      test("encodes empty nested object") {
        val config = WriterConfig
        encodeDynamic(record("user" -> record()), "user:", config)
      }
    ),
    suite("encode arrays primitive")(
      test("encodes whitespace-only strings in arrays") {
        val expected = "items[2]: \" \",\"  \""
        encode(Items(List(" ", "  ")), expected)
      },
      test("quotes strings with structural meanings in arrays") {
        val expected = "items[3]: \"[5]\",\"- item\",\"{key}\""
        encode(Items(List("[5]", "- item", "{key}")), expected)
      }
    ),
    suite("encode arrays tabular")(
      test("encodes arrays of uniform objects in tabular format") {
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec    = deriveCodec(ItemsTable.schema, deriver)
        val data     = ItemsTable(List(ItemRow("A1", 2, BigDecimal("9.99")), ItemRow("B2", 1, BigDecimal("14.5"))))
        val expected = "items[2]{sku,qty,price}:\n  A1,2,9.99\n  B2,1,14.5"
        encode(data, expected, codec)
      },
      test("encodes null values in tabular format") {
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec    = deriveCodec(ItemsNullable.schema, deriver)
        val data     = ItemsNullable(List(ItemNullable(1, None), ItemNullable(2, Some("test"))))
        val expected = "items[2]{id,value}:\n  1,null\n  2,test"
        encode(data, expected, codec)
      },
      test("quotes strings containing delimiters in tabular rows") {
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec    = deriveCodec(ItemsWithSkuDescQty.schema, deriver)
        val data     = ItemsWithSkuDescQty(List(SkuDescQty("A,1", "cool", 2), SkuDescQty("B2", "wip: test", 1)))
        val expected = "items[2]{sku,desc,qty}:\n  \"A,1\",cool,2\n  B2,\"wip: test\",1"
        encode(data, expected, codec)
      },
      test("quotes ambiguous strings in tabular rows") {
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec    = deriveCodec(ItemsWithIdStatus.schema, deriver)
        val data     = ItemsWithIdStatus(List(IdStatus(1, "true"), IdStatus(2, "false")))
        val expected = "items[2]{id,status}:\n  1,\"true\"\n  2,\"false\""
        encode(data, expected, codec)
      },
      test("encodes tabular arrays with keys needing quotes") {
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec    = deriveCodec(OrderFullNameItems.schema, deriver)
        val data     = OrderFullNameItems(List(OrderFullName(1, "Ada"), OrderFullName(2, "Bob")))
        val expected = "items[2]{\"order:id\",\"full name\"}:\n  1,Ada\n  2,Bob"
        encode(data, expected, codec)
      }
    ),
    suite("encode arrays nested")(
      test("encodes nested arrays of primitives") {
        // With ArrayFormat.List, primitive arrays are also expanded
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec    = deriveCodec(PairsList.schema, deriver)
        val data     = PairsList(List(StringPair(List("a", "b")), StringPair(List("c", "d"))))
        val expected =
          """pairs[2]:
            |  - items[2]:
            |    - a
            |    - b
            |  - items[2]:
            |    - c
            |    - d""".stripMargin
        encode(data, expected, codec)
      },
      test("encodes empty inner arrays") {
        val deriver = ToonBinaryCodecDeriver
          .withArrayFormat(ArrayFormat.List)
          .withTransientEmptyCollection(false)
        val codec    = deriveCodec(PairsList.schema, deriver)
        val data     = PairsList(List(StringPair(List.empty), StringPair(List.empty)))
        val expected =
          """pairs[2]:
            |  - items[0]:
            |  - items[0]:""".stripMargin
        encode(data, expected, codec)
      },
      test("encodes complex nested structure") {
        val deriver  = ToonBinaryCodecDeriver.withTransientEmptyCollection(false)
        val codec    = deriveCodec(ComplexUser.schema, deriver)
        val data     = ComplexUser(123, "Ada", List("reading", "gaming"), true, List.empty)
        val expected =
          """id: 123
            |name: Ada
            |tags[2]: reading,gaming
            |active: true
            |prefs[0]:""".stripMargin
        encode(data, expected, codec)
      },
      test("encodes root-level primitive array") {
        // [5]: x,y,"true",true,10
        val config = WriterConfig
        val input  = zio.blocks.schema.DynamicValue.Sequence(
          Vector(
            dynamicStr("x"),
            dynamicStr("y"),
            dynamicStr("true"),
            zio.blocks.schema.DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Boolean(true)),
            dynamicInt(10)
          )
        )
        encodeDynamic(input, "[5]: x,y,\"true\",true,10", config)
      },
      test("encodes root-level array of uniform objects in tabular format") {
        // [2]{id}:\n  1\n  2
        val config = WriterConfig
        val input  = zio.blocks.schema.DynamicValue.Sequence(
          Vector(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2)))
        )
        encodeDynamic(input, "[2]{id}:\n  1\n  2", config)
      },
      test("encodes root-level array of non-uniform objects in list format") {
        // [2]:\n  - id: 1\n  - id: 2\n    name: Ada
        val config = WriterConfig
        val input  = zio.blocks.schema.DynamicValue.Sequence(
          Vector(
            record("id" -> dynamicInt(1)),
            record("id" -> dynamicInt(2), "name" -> dynamicStr("Ada"))
          )
        )
        encodeDynamic(input, "[2]:\n  - id: 1\n  - id: 2\n    name: Ada", config)
      },
      test("encodes root-level array mixing primitive, object, and array of objects in list format") {
        val config = WriterConfig
        val input  = zio.blocks.schema.DynamicValue.Sequence(
          Vector(
            dynamicStr("summary"),
            record("id" -> dynamicInt(1), "name" -> dynamicStr("Ada")),
            zio.blocks.schema.DynamicValue.Sequence(
              Vector(record("id" -> dynamicInt(2)), record("status" -> dynamicStr("draft")))
            )
          )
        )
        val expected = "[3]:\n  - summary\n  - id: 1\n    name: Ada\n  - [2]:\n    - id: 2\n    - status: draft"
        encodeDynamic(input, expected, config)
      },
      test("encodes root-level arrays of arrays") {
        val config = WriterConfig
        val input  = zio.blocks.schema.DynamicValue.Sequence(
          Vector(
            zio.blocks.schema.DynamicValue.Sequence(Vector(dynamicInt(1), dynamicInt(2))),
            zio.blocks.schema.DynamicValue.Sequence(Vector())
          )
        )
        encodeDynamic(input, "[2]:\n  - [2]: 1,2\n  - [0]:", config)
      },
      test("encodes empty root-level array") {
        val config = WriterConfig
        val input  = zio.blocks.schema.DynamicValue.Sequence(Vector())
        encodeDynamic(input, "[0]:", config)
      },
      test("quotes strings containing delimiters in nested arrays") {
        val config = WriterConfig
        val input  = record(
          "pairs" -> zio.blocks.schema.DynamicValue.Sequence(
            Vector(
              zio.blocks.schema.DynamicValue.Sequence(Vector(dynamicStr("a"), dynamicStr("b"))),
              zio.blocks.schema.DynamicValue.Sequence(Vector(dynamicStr("c,d"), dynamicStr("e:f"), dynamicStr("true")))
            )
          )
        )
        encodeDynamic(input, "pairs[2]:\n  - [2]: a,b\n  - [3]: \"c,d\",\"e:f\",\"true\"", config)
      },
      test("encodes mixed-length inner arrays") {
        val config = WriterConfig
        val input  = record(
          "pairs" -> zio.blocks.schema.DynamicValue.Sequence(
            Vector(
              zio.blocks.schema.DynamicValue.Sequence(Vector(dynamicInt(1))),
              zio.blocks.schema.DynamicValue.Sequence(Vector(dynamicInt(2), dynamicInt(3)))
            )
          )
        )
        encodeDynamic(input, "pairs[2]:\n  - [1]: 1\n  - [2]: 2,3", config)
      },
      test("uses list format for arrays mixing primitives and objects") {
        val config = WriterConfig
        val input  = record(
          "items" -> zio.blocks.schema.DynamicValue.Sequence(
            Vector(dynamicInt(1), record("a" -> dynamicInt(1)), dynamicStr("text"))
          )
        )
        encodeDynamic(input, "items[3]:\n  - 1\n  - a: 1\n  - text", config)
      },
      test("uses list format for arrays mixing objects and arrays") {
        val config = WriterConfig
        val input  = record(
          "items" -> zio.blocks.schema.DynamicValue.Sequence(
            Vector(
              record("a" -> dynamicInt(1)),
              zio.blocks.schema.DynamicValue.Sequence(Vector(dynamicInt(1), dynamicInt(2)))
            )
          )
        )
        encodeDynamic(input, "items[2]:\n  - a: 1\n  - [2]: 1,2", config)
      }
    ),
    suite("encode arrays objects")(
      test("uses list format for objects with different fields") {
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec    = deriveCodec(PersonListWrapper.schema, deriver)
        val data     = PersonListWrapper(List(Person("First", 25), Person("Second", 30)))
        val expected =
          """people[2]:
            |  - name: First
            |    age: 25
            |  - name: Second
            |    age: 30""".stripMargin
        encode(data, expected, codec)
      },
      test("uses list format for objects with nested values") {
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec    = deriveCodec(ItemsWithNestedWrapper.schema, deriver)
        val data     = ItemsWithNestedWrapper(List(ItemWithNested(1, NestedX(1))))
        val expected =
          """items[1]:
            |  - id: 1
            |    nested:
            |      x: 1""".stripMargin
        encode(data, expected, codec)
      },
      test("preserves field order in list items - primitive first") {
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec    = deriveCodec(ItemsNameNums.schema, deriver)
        val data     = ItemsNameNums(List(NameNums("Ada", List(1, 2, 3))))
        val expected =
          """items[1]:
            |  - name: Ada
            |    nums[3]:
            |      - 1
            |      - 2
            |      - 3""".stripMargin
        encode(data, expected, codec)
      },
      test("encodes objects with empty arrays in list format") {
        val deriver = ToonBinaryCodecDeriver
          .withArrayFormat(ArrayFormat.List)
          .withTransientEmptyCollection(false)
        val codec    = deriveCodec(ItemsNameData.schema, deriver)
        val data     = ItemsNameData(List(NameData("Ada", List.empty)))
        val expected =
          """items[1]:
            |  - name: Ada
            |    data[0]:""".stripMargin
        encode(data, expected, codec)
      },
      test("uses list format for objects with multiple array fields") {
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec    = deriveCodec(ItemsNumsTags.schema, deriver)
        val data     = ItemsNumsTags(List(NumsTags(List(1, 2), List("a", "b"), "test")))
        val expected =
          """items[1]:
            |  - nums[2]:
            |    - 1
            |    - 2
            |    tags[2]:
            |      - a
            |      - b
            |    name: test""".stripMargin
        encode(data, expected, codec)
      },
      test("uses field order from first object for tabular headers") {
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec    = deriveCodec(ABCItems.schema, deriver)
        val data     = ABCItems(List(ABC(1, 2, 3), ABC(10, 20, 30)))
        val expected = "items[2]{a,b,c}:\n  1,2,3\n  10,20,30"
        encode(data, expected, codec)
      },
      test("preserves field order in list items - array first") {
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec    = deriveCodec(ItemsNumsName.schema, deriver)
        val data     = ItemsNumsName(List(NumsName(List(1, 2, 3), "Ada")))
        val expected = "items[1]:\n  - nums[3]:\n    - 1\n    - 2\n    - 3\n    name: Ada"
        encode(data, expected, codec)
      },
      test("uses list format for objects containing arrays of arrays") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec   = deriveCodec(ItemsMatrixName.schema, deriver)
        val data    =
          ItemsMatrixName(List(MatrixName(List(IntListWrapper(List(1, 2)), IntListWrapper(List(3, 4))), "grid")))
        val expected =
          "items[1]:\n  - matrix[2]:\n    - xs[2]:\n      - 1\n      - 2\n    - xs[2]:\n      - 3\n      - 4\n    name: grid"
        encode(data, expected, codec)
      },
      test("uses tabular format for nested uniform object arrays") {
        // items[1]:\n  - users[2]{id,name}:\n      1,Ada\n      2,Bob\n    status: active
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec    = deriveCodec(ItemsUsersStatus.schema, deriver)
        val data     = ItemsUsersStatus(List(UsersStatus(List(IdName(1, "Ada"), IdName(2, "Bob")), "active")))
        val expected = "items[1]:\n  - users[2]{id,name}:\n      1,Ada\n      2,Bob\n    status: active"
        encode(data, expected, codec)
      },
      test("uses list format for nested object arrays with mismatched keys") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec   = deriveCodec(ItemsUsersMismatch.schema, deriver)
        val data    =
          ItemsUsersMismatch(List(UsersMismatch(List(UserMismatch(1, Some("Ada")), UserMismatch(2, None)), "active")))
        val expected = "items[1]:\n  - users[2]:\n    - id: 1\n      name: Ada\n    - id: 2\n    status: active"
        encode(data, expected, codec)
      },
      test("uses list format for objects with only array fields") {
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec    = deriveCodec(ItemsNumsTagsOnly.schema, deriver)
        val data     = ItemsNumsTagsOnly(List(NumsTagsOnly(List(1, 2, 3), List("a", "b"))))
        val expected =
          """items[1]:
            |  - nums[3]:
            |    - 1
            |    - 2
            |    - 3
            |    tags[2]:
            |      - a
            |      - b""".stripMargin
        encode(data, expected, codec)
      },
      test("uses canonical encoding for multi-field list-item objects with tabular arrays") {
        // items[1]:\n  - users[2]{id}:\n      1\n      2\n    note: x
        val config = WriterConfig
        val input  = record(
          "items" -> zio.blocks.schema.DynamicValue.Sequence(
            Vector(
              record(
                "users" -> zio.blocks.schema.DynamicValue.Sequence(
                  Vector(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2)))
                ),
                "note" -> dynamicStr("x")
              )
            )
          )
        )
        encodeDynamic(input, "items[1]:\n  - users[2]{id}:\n      1\n      2\n    note: x", config)
      },
      test("uses canonical encoding for single-field list-item tabular arrays") {
        // items[1]:\n  - users[2]{id,name}:\n      1,Ada\n      2,Bob
        val config = WriterConfig
        val input  = record(
          "items" -> zio.blocks.schema.DynamicValue.Sequence(
            Vector(
              record(
                "users" -> zio.blocks.schema.DynamicValue.Sequence(
                  Vector(
                    record("id" -> dynamicInt(1), "name" -> dynamicStr("Ada")),
                    record("id" -> dynamicInt(2), "name" -> dynamicStr("Bob"))
                  )
                )
              )
            )
          )
        )
        encodeDynamic(input, "items[1]:\n  - users[2]{id,name}:\n      1,Ada\n      2,Bob", config)
      },
      test("places empty arrays on hyphen line when first") {
        // items[1]:\n  - data[0]:\n    name: x
        val deriver = ToonBinaryCodecDeriver
          .withArrayFormat(ArrayFormat.List)
          .withTransientEmptyCollection(false)
        val codec    = deriveCodec(ItemsDataName.schema, deriver)
        val data     = ItemsDataName(List(DataName(List.empty, "x")))
        val expected = "items[1]:\n  - data[0]:\n    name: x"
        encode(data, expected, codec)
      },
      test("encodes empty object list items as bare hyphen") {
        // items[3]:\n  - first\n  - second\n  -
        val config = WriterConfig
        val input  = record(
          "items" -> zio.blocks.schema.DynamicValue.Sequence(
            Vector(dynamicStr("first"), dynamicStr("second"), record())
          )
        )
        encodeDynamic(input, "items[3]:\n  - first\n  - second\n  -", config)
      },
      test("uses list format when one object has nested field") {
        // items[2]:\n  - id: 1\n    data: string\n  - id: 2\n    data:\n      nested: true
        val config = WriterConfig
        val input  = record(
          "items" -> zio.blocks.schema.DynamicValue.Sequence(
            Vector(
              record("id" -> dynamicInt(1), "data" -> dynamicStr("string")),
              record(
                "id"   -> dynamicInt(2),
                "data" -> record(
                  "nested" -> zio.blocks.schema.DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Boolean(true))
                )
              )
            )
          )
        )
        encodeDynamic(input, "items[2]:\n  - id: 1\n    data: string\n  - id: 2\n    data:\n      nested: true", config)
      }
    ),
    suite("decode root form")(
      test("parses empty document as empty object") {
        val config   = ReaderConfig.withStrict(true)
        val expected = record()
        decodeDynamic("", expected, config)
      }
    ),
    suite("decode whitespace")(
      test("tolerates spaces around commas in inline arrays") {
        decode("tags[3]: a , b , c", Tags(List("a", "b", "c")))
      },
      test("tolerates spaces around pipes in inline arrays") {
        val deriver = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Pipe)
        val codec   = deriveCodec(Tags.schema, deriver)
        decode("tags[3|]: a | b | c", Tags(List("a", "b", "c")), codec)
      },
      test("tolerates spaces around tabs in inline arrays") {
        val deriver = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Tab)
        val codec   = deriveCodec(Tags.schema, deriver)
        decode("tags[3\t]: a \t b \t c", Tags(List("a", "b", "c")), codec)
      },
      test("tolerates leading and trailing spaces in tabular row values") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(PersonListWrapper.schema, deriver)
        decode(
          "people[2]{name,age}:\n  Alice , 25 \n  Bob , 30 ",
          PersonListWrapper(List(Person("Alice", 25), Person("Bob", 30))),
          codec
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
        val config   = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        val expected = record("a" -> record("b" -> record("c" -> dynamicInt(1))))
        decodeDynamic("a.b.c: 1", expected, config)
      },
      test("expands dotted key with inline array") {
        val config   = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        val expected = record(
          "data" -> record(
            "meta" -> record(
              "items" -> zio.blocks.schema.DynamicValue.Sequence(Vector(dynamicStr("a"), dynamicStr("b")))
            )
          )
        )
        decodeDynamic("data.meta.items[2]: a,b", expected, config)
      },
      test("expands dotted key with tabular array") {
        val config   = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        val expected = record(
          "a" -> record(
            "b" -> record(
              "items" -> zio.blocks.schema.DynamicValue.Sequence(
                Vector(
                  record("id" -> dynamicInt(1), "name" -> dynamicStr("A")),
                  record("id" -> dynamicInt(2), "name" -> dynamicStr("B"))
                )
              )
            )
          )
        )
        decodeDynamic("a.b.items[2]{id,name}:\n  1,A\n  2,B", expected, config)
      },
      test("preserves literal dotted keys when expansion is off") {
        val config   = ReaderConfig.withExpandPaths(PathExpansion.Off)
        val expected = record("user.name" -> dynamicStr("Ada"))
        decodeDynamic("user.name: Ada", expected, config)
      },
      test("expands and deep-merges preserving document-order insertion") {
        val config   = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        val expected = record(
          "a" -> record(
            "b" -> record("c" -> dynamicInt(1), "d" -> dynamicInt(2)),
            "e" -> dynamicInt(3)
          )
        )
        decodeDynamic("a.b.c: 1\na.b.d: 2\na.e: 3", expected, config)
      },
      test("throws on expansion conflict (object vs primitive) when strict=true") {
        val codec  = ToonBinaryCodec.dynamicValueCodec
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe).withStrict(true)
        decodeError("a.b: 1\na: 2", "conflict", codec, config)
      },
      test("throws on expansion conflict (object vs array) when strict=true") {
        val codec  = ToonBinaryCodec.dynamicValueCodec
        val config = ReaderConfig.withExpandPaths(PathExpansion.Safe).withStrict(true)
        decodeError("a.b: 1\na[2]: 2,3", "conflict", codec, config)
      },
      test("applies LWW when strict=false (primitive overwrites expanded object)") {
        val config   = ReaderConfig.withExpandPaths(PathExpansion.Safe).withStrict(false)
        val expected = record("a" -> dynamicInt(2))
        decodeDynamic("a.b: 1\na: 2", expected, config)
      },
      test("applies LWW when strict=false (expanded object overwrites primitive)") {
        val config   = ReaderConfig.withExpandPaths(PathExpansion.Safe).withStrict(false)
        val expected = record("a" -> record("b" -> dynamicInt(2)))
        decodeDynamic("a: 1\na.b: 2", expected, config)
      },
      test("preserves quoted dotted key as literal when expandPaths=safe") {
        val config   = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        val expected = record(
          "a"   -> record("b" -> dynamicInt(1)),
          "c.d" -> dynamicInt(2)
        )
        decodeDynamic("a.b: 1\n\"c.d\": 2", expected, config)
      },
      test("preserves non-IdentifierSegment keys as literals") {
        val config   = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        val expected = record("full-name.x" -> dynamicInt(1))
        decodeDynamic("full-name.x: 1", expected, config)
      },
      test("expands keys creating empty nested objects") {
        val config   = ReaderConfig.withExpandPaths(PathExpansion.Safe)
        val expected = record("a" -> record("b" -> record("c" -> record())))
        decodeDynamic("a.b.c:", expected, config)
      }
    ),
    suite("decode arrays primitive")(
      test("parses string arrays inline") {
        val input    = "tags[3]: reading,gaming,coding"
        val expected = record(
          "tags" -> DynamicValue.Sequence(Vector(dynamicStr("reading"), dynamicStr("gaming"), dynamicStr("coding")))
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses number arrays inline") {
        decode("nums[3]: 1,2,3", Nums(List(1, 2, 3)))
      },
      test("parses mixed primitive arrays inline") {
        val input    = "data[4]: x,y,true,10"
        val expected = record(
          "data" -> DynamicValue.Sequence(
            Vector(
              dynamicStr("x"),
              dynamicStr("y"),
              zio.blocks.schema.DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Boolean(true)),
              dynamicInt(10)
            )
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses empty arrays") {
        val deriver = ToonBinaryCodecDeriver.withTransientEmptyCollection(false)
        val codec   = deriveCodec(Items.schema, deriver)
        decode("items[0]:", Items(List.empty), codec)
      },
      test("parses single-item array with empty string") {
        val input    = "items[1]: \"\""
        val expected = record("items" -> DynamicValue.Sequence(Vector(dynamicStr(""))))
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses multi-item array with empty string") {
        val input    = "items[3]: a,\"\",b"
        val expected =
          record("items" -> DynamicValue.Sequence(Vector(dynamicStr("a"), dynamicStr(""), dynamicStr("b"))))
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses whitespace-only strings in arrays") {
        val input    = "items[2]: \" \",\"  \""
        val expected = record("items" -> DynamicValue.Sequence(Vector(dynamicStr(" "), dynamicStr("  "))))
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses strings with delimiters in arrays") {
        val input    = "items[3]: a,\"b,c\",\"d:e\""
        val expected =
          record("items" -> DynamicValue.Sequence(Vector(dynamicStr("a"), dynamicStr("b,c"), dynamicStr("d:e"))))
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses strings that look like primitives when quoted") {
        val input    = "items[4]: x,\"true\",\"42\",\"-3.14\""
        val expected = record(
          "items" -> DynamicValue.Sequence(
            Vector(dynamicStr("x"), dynamicStr("true"), dynamicStr("42"), dynamicStr("-3.14"))
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses strings with structural tokens in arrays") {
        val input    = "items[3]: \"[5]\",\"- item\",\"{key}\""
        val expected =
          record("items" -> DynamicValue.Sequence(Vector(dynamicStr("[5]"), dynamicStr("- item"), dynamicStr("{key}"))))
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses quoted key with inline array") {
        decode("\"my-key\"[3]: 1,2,3", MyKeyWrapper(List(1, 2, 3)))
      },
      test("parses quoted key containing brackets with inline array") {
        decode("\"key[test]\"[3]: 1,2,3", KeyTestWrapper(List(1, 2, 3)))
      },
      test("parses quoted key with empty array") {
        val input    = "\"x-custom\"[0]:"
        val expected = record("x-custom" -> DynamicValue.Sequence(Vector.empty))
        decodeDynamic(input, expected, ReaderConfig)
      }
    ),
    suite("decode arrays nested")(
      test("parses list arrays for non-uniform objects") {
        val input    = "items[2]:\n  - id: 1\n    name: First\n  - id: 2\n    name: Second\n    extra: true"
        val expected = record(
          "items" -> DynamicValue.Sequence(
            Vector(
              record("id" -> dynamicInt(1), "name" -> dynamicStr("First")),
              record(
                "id"    -> dynamicInt(2),
                "name"  -> dynamicStr("Second"),
                "extra" -> zio.blocks.schema.DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Boolean(true))
              )
            )
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses list arrays with empty items") {
        val input    = "items[3]:\n  - first\n  - second\n  -"
        val expected = record(
          "items" -> DynamicValue.Sequence(
            Vector(dynamicStr("first"), dynamicStr("second"), record())
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses list arrays with deeply nested objects") {
        val input    = "items[2]:\n  - properties:\n      state:\n        type: string\n  - id: 2"
        val expected = record(
          "items" -> DynamicValue.Sequence(
            Vector(
              record("properties" -> record("state" -> record("type" -> dynamicStr("string")))),
              record("id"         -> dynamicInt(2))
            )
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses list arrays containing objects with nested properties") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec   = deriveCodec(ItemsWithNestedWrapper.schema, deriver)
        val input   = "items[1]:\n  - id: 1\n    nested:\n      x: 1"
        decode(input, ItemsWithNestedWrapper(List(ItemWithNested(1, NestedX(1)))), codec)
      },
      test("parses list items whose first field is a tabular array") {
        val input    = "items[1]:\n  - users[2]{id,name}:\n      1,Ada\n      2,Bob\n    status: active"
        val expected = record(
          "items" -> DynamicValue.Sequence(
            Vector(
              record(
                "users" -> DynamicValue.Sequence(
                  Vector(
                    record("id" -> dynamicInt(1), "name" -> dynamicStr("Ada")),
                    record("id" -> dynamicInt(2), "name" -> dynamicStr("Bob"))
                  )
                ),
                "status" -> dynamicStr("active")
              )
            )
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses single-field list-item object with tabular array") {
        val input    = "items[1]:\n  - users[2]{id,name}:\n      1,Ada\n      2,Bob"
        val expected = record(
          "items" -> DynamicValue.Sequence(
            Vector(
              record(
                "users" -> DynamicValue.Sequence(
                  Vector(
                    record("id" -> dynamicInt(1), "name" -> dynamicStr("Ada")),
                    record("id" -> dynamicInt(2), "name" -> dynamicStr("Bob"))
                  )
                )
              )
            )
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses objects containing arrays (including empty arrays) in list format") {
        val deriver = ToonBinaryCodecDeriver
          .withArrayFormat(ArrayFormat.List)
          .withTransientEmptyCollection(false)
        val codec = deriveCodec(ItemsNameData.schema, deriver)
        val input = "items[1]:\n  - name: Ada\n    data[0]:"
        decode(input, ItemsNameData(List(NameData("Ada", List.empty))), codec)
      },
      test("parses arrays of arrays within objects") {
        val input    = "items[1]:\n  - matrix[2]:\n      - [2]: 1,2\n      - [2]: 3,4\n    name: grid"
        val expected = record(
          "items" -> DynamicValue.Sequence(
            Vector(
              record(
                "matrix" -> DynamicValue.Sequence(
                  Vector(
                    DynamicValue.Sequence(Vector(dynamicInt(1), dynamicInt(2))),
                    DynamicValue.Sequence(Vector(dynamicInt(3), dynamicInt(4)))
                  )
                ),
                "name" -> dynamicStr("grid")
              )
            )
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses nested arrays of primitives") {
        val input    = "pairs[2]:\n  - [2]: a,b\n  - [2]: c,d"
        val expected = record(
          "pairs" -> DynamicValue.Sequence(
            Vector(
              DynamicValue.Sequence(Vector(dynamicStr("a"), dynamicStr("b"))),
              DynamicValue.Sequence(Vector(dynamicStr("c"), dynamicStr("d")))
            )
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses quoted strings and mixed lengths in nested arrays") {
        val input    = "pairs[2]:\n  - [2]: a,b\n  - [3]: \"c,d\",\"e:f\",\"true\""
        val expected = record(
          "pairs" -> DynamicValue.Sequence(
            Vector(
              DynamicValue.Sequence(Vector(dynamicStr("a"), dynamicStr("b"))),
              DynamicValue.Sequence(Vector(dynamicStr("c,d"), dynamicStr("e:f"), dynamicStr("true")))
            )
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses empty inner arrays") {
        val input    = "pairs[2]:\n  - [0]:\n  - [0]:"
        val expected = record(
          "pairs" -> DynamicValue.Sequence(
            Vector(DynamicValue.Sequence(Vector.empty), DynamicValue.Sequence(Vector.empty))
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses mixed-length inner arrays") {
        val input    = "pairs[2]:\n  - [1]: 1\n  - [2]: 2,3"
        val expected = record(
          "pairs" -> DynamicValue.Sequence(
            Vector(
              DynamicValue.Sequence(Vector(dynamicInt(1))),
              DynamicValue.Sequence(Vector(dynamicInt(2), dynamicInt(3)))
            )
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses root-level primitive array inline") {
        val input    = "[5]: x,y,\"true\",true,10"
        val expected = DynamicValue.Sequence(
          Vector(
            dynamicStr("x"),
            dynamicStr("y"),
            dynamicStr("true"),
            zio.blocks.schema.DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Boolean(true)),
            dynamicInt(10)
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses root-level array of uniform objects in tabular format") {
        val input    = "[2]{id}:\n  1\n  2"
        val expected = DynamicValue.Sequence(
          Vector(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2)))
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses root-level array of non-uniform objects in list format") {
        val input    = "[2]:\n  - id: 1\n  - id: 2\n    name: Ada"
        val expected = DynamicValue.Sequence(
          Vector(
            record("id" -> dynamicInt(1)),
            record("id" -> dynamicInt(2), "name" -> dynamicStr("Ada"))
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses root-level array mixing primitive, object, and array of objects in list format") {
        val input    = "[3]:\n  - summary\n  - id: 1\n    name: Ada\n  - [2]:\n    - id: 2\n    - status: draft"
        val expected = DynamicValue.Sequence(
          Vector(
            dynamicStr("summary"),
            record("id" -> dynamicInt(1), "name" -> dynamicStr("Ada")),
            DynamicValue.Sequence(
              Vector(record("id" -> dynamicInt(2)), record("status" -> dynamicStr("draft")))
            )
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses root-level array of arrays") {
        val input    = "[2]:\n  - [2]: 1,2\n  - [0]:"
        val expected = DynamicValue.Sequence(
          Vector(
            DynamicValue.Sequence(Vector(dynamicInt(1), dynamicInt(2))),
            DynamicValue.Sequence(Vector.empty)
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses empty root-level array") {
        val input    = "[0]:"
        val expected = DynamicValue.Sequence(Vector.empty)
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses complex mixed object with arrays and nested objects") {
        val deriver = ToonBinaryCodecDeriver.withTransientEmptyCollection(false)
        val codec   = deriveCodec(ComplexUser.schema, deriver)
        val input   =
          """id: 123
            |name: Ada
            |tags[2]: reading,gaming
            |active: true
            |prefs[0]:""".stripMargin
        decode(input, ComplexUser(123, "Ada", List("reading", "gaming"), true, List.empty), codec)
      },
      test("parses arrays mixing primitives, objects, and strings in list format") {
        val input    = "items[3]:\n  - 1\n  - a: 1\n  - text"
        val expected = record(
          "items" -> DynamicValue.Sequence(
            Vector(dynamicInt(1), record("a" -> dynamicInt(1)), dynamicStr("text"))
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses arrays mixing objects and arrays") {
        val input    = "items[2]:\n  - a: 1\n  - [2]: 1,2"
        val expected = record(
          "items" -> DynamicValue.Sequence(
            Vector(
              record("a" -> dynamicInt(1)),
              DynamicValue.Sequence(Vector(dynamicInt(1), dynamicInt(2)))
            )
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses quoted key with list array format") {
        val input    = "\"x-items\"[2]:\n  - id: 1\n  - id: 2"
        val expected = record(
          "x-items" -> DynamicValue.Sequence(
            Vector(record("id" -> dynamicInt(1)), record("id" -> dynamicInt(2)))
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      }
    ),
    suite("decode arrays tabular")(
      test("parses tabular arrays of uniform objects") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(SkuQtyPriceItems.schema, deriver)
        val input   = "items[2]{sku,qty,price}:\n  A1,2,9.99\n  B2,1,14.5"
        decode(input, SkuQtyPriceItems(List(SkuQtyPrice("A1", 2, 9.99), SkuQtyPrice("B2", 1, 14.5))), codec)
      },
      test("parses nulls and quoted values in tabular rows") {
        val input    = "items[2]{id,value}:\n  1,null\n  2,\"test\""
        val expected = record(
          "items" -> DynamicValue.Sequence(
            Vector(
              record(
                "id"    -> dynamicInt(1),
                "value" -> zio.blocks.schema.DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Unit)
              ),
              record("id" -> dynamicInt(2), "value" -> dynamicStr("test"))
            )
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("parses quoted colon in tabular row as data") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(IdNoteItems.schema, deriver)
        val input   = "items[2]{id,note}:\n  1,\"a:b\"\n  2,\"c:d\""
        decode(input, IdNoteItems(List(IdNote(1, "a:b"), IdNote(2, "c:d"))), codec)
      },
      test("parses quoted header keys in tabular arrays") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(OrderFullNameItems.schema, deriver)
        val input   = "items[2]{\"order:id\",\"full name\"}:\n  1,Ada\n  2,Bob"
        decode(input, OrderFullNameItems(List(OrderFullName(1, "Ada"), OrderFullName(2, "Bob"))), codec)
      },
      test("parses quoted key with tabular array format") {
        val input    = "\"x-items\"[2]{id,name}:\n  1,Ada\n  2,Bob"
        val expected = record(
          "x-items" -> DynamicValue.Sequence(
            Vector(
              record("id" -> dynamicInt(1), "name" -> dynamicStr("Ada")),
              record("id" -> dynamicInt(2), "name" -> dynamicStr("Bob"))
            )
          )
        )
        decodeDynamic(input, expected, ReaderConfig)
      },
      test("treats unquoted colon as terminator for tabular rows and start of key-value pair") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(ItemsTableWithCount.schema, deriver)
        val input   = "items[2]{sku,qty,price}:\n  A1,2,9.99\n  B2,1,14.5\ncount: 2"
        decode(
          input,
          ItemsTableWithCount(List(ItemRow("A1", 2, BigDecimal("9.99")), ItemRow("B2", 1, BigDecimal("14.5"))), 2),
          codec
        )
      }
    ),
    suite("decode validation errors")(
      test("throws on array length mismatch (inline primitives - too many)") {
        decodeError[Tags]("tags[2]: a,b,c", "Array count mismatch: expected 2 items but got 3")
      },
      test("throws on tabular row value count mismatch with header field count") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(PersonListWrapper.schema, deriver)
        // The decoder reports "Missing required field" when a tabular row has fewer values than headers
        decodeError("people[2]{name,age}:\n  Ada,25\n  Bob", "Missing required field in tabular row: age", codec)
      },
      test("throws on tabular row count mismatch with header length") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(PersonListWrapper.schema, deriver)
        // With [1], decoder reads 1 row then tries to parse next as key:value
        decodeError("people[1]{name,age}:\n  Ada,25\n  Bob,30", "Expected key:value, no colon found", codec)
      },
      test("throws on unterminated string") {
        decodeError[NameWrapper]("name: \"unterminated", "Unterminated string")
      },
      test("throws on invalid escape sequence") {
        decodeError[NameWrapper]("name: \"a\\x\"", "Invalid escape")
      }
    ),
    suite("decode indentation errors")(
      test("throws on object field with non-multiple indentation (3 spaces with indent=2)") {
        val input = "a:\n   b: 1"
        val codec = ToonBinaryCodec.dynamicValueCodec
        decodeError(input, "Indentation must be multiple of", codec, ReaderConfig.withStrict(true))
      },
      test("accepts correct indentation with custom indent size (4 spaces with indent=4)") {
        val input    = "a:\n    b: 1"
        val config   = ReaderConfig.withStrict(true).withIndent(4)
        val expected = record("a" -> record("b" -> dynamicInt(1)))
        decodeDynamic(input, expected, config)
      },
      test("throws on tab character used in indentation") {
        val input = "a:\n\tb: 1"
        val codec = ToonBinaryCodec.dynamicValueCodec
        decodeError(input, "Tabs are not allowed in indentation", codec, ReaderConfig.withStrict(true))
      },
      test("throws on mixed tabs and spaces in indentation") {
        val input = "a:\n \tb: 1"
        val codec = ToonBinaryCodec.dynamicValueCodec
        decodeError(input, "Tabs are not allowed in indentation", codec, ReaderConfig.withStrict(true))
      },
      test("accepts tabs in quoted string values") {
        val input  = "text: \"hello\\tworld\""
        val config = ReaderConfig.withStrict(true)
        decode(input, TextWrapper("hello\tworld"), config)
      },
      test("accepts tabs in quoted keys") {
        val input    = "\"key\\ttab\": value"
        val config   = ReaderConfig.withStrict(true).withExpandPaths(PathExpansion.Off)
        val expected = record("key\ttab" -> dynamicStr("value"))
        decodeDynamic(input, expected, config)
      },
      test("accepts non-multiple indentation when strict=false") {
        val input    = "a:\n   b: 1" // 3 spaces
        val config   = ReaderConfig.withStrict(false)
        val expected = record("a" -> record("b" -> dynamicInt(1)))
        decodeDynamic(input, expected, config)
      },
      test("accepts deeply nested non-multiples when strict=false") {
        val input    = "a:\n   b:\n     c: 1"
        val config   = ReaderConfig.withStrict(false)
        val expected = record("a" -> record("b" -> record("c" -> dynamicInt(1))))
        decodeDynamic(input, expected, config)
      },
      test("parses empty lines without validation errors") {
        val input    = "a: 1\n\nb: 2"
        val config   = ReaderConfig.withStrict(true)
        val expected = record("a" -> dynamicInt(1), "b" -> dynamicInt(2))
        decodeDynamic(input, expected, config)
      },
      test("parses root-level content (0 indentation) as always valid") {
        val input    = "a: 1\nb: 2\nc: 3"
        val config   = ReaderConfig.withStrict(true)
        val expected = record("a" -> dynamicInt(1), "b" -> dynamicInt(2), "c" -> dynamicInt(3))
        decodeDynamic(input, expected, config)
      }
    ),
    suite("decode blank lines")(
      test("throws on blank line inside list array") {
        val input = "items[3]:\n  - a\n\n  - b\n  - c"
        val codec = ToonBinaryCodec.dynamicValueCodec
        decodeError(input, "Blank lines are not allowed inside arrays", codec, ReaderConfig.withStrict(true))
      },
      test("throws on blank line inside tabular array") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(PersonListWrapper.schema, deriver)
        val input   = "people[2]{name,age}:\n  Ada,25\n\n  Bob,30"
        decodeError(input, "Blank lines are not allowed inside arrays", codec, ReaderConfig.withStrict(true))
      },
      test("throws on multiple blank lines inside array") {
        val input = "items[2]:\n  - a\n\n\n  - b"
        val codec = ToonBinaryCodec.dynamicValueCodec
        decodeError(input, "Blank lines are not allowed inside arrays", codec, ReaderConfig.withStrict(true))
      },
      test("accepts blank line between root-level fields") {
        val input    = "a: 1\n\nb: 2"
        val config   = ReaderConfig.withStrict(true)
        val expected = record("a" -> dynamicInt(1), "b" -> dynamicInt(2))
        decodeDynamic(input, expected, config)
      },
      test("accepts trailing newline at end of file") {
        val input    = "a: 1\n"
        val config   = ReaderConfig.withStrict(true)
        val expected = record("a" -> dynamicInt(1))
        decodeDynamic(input, expected, config)
      },
      test("accepts multiple trailing newlines") {
        val input    = "a: 1\n\n\n"
        val config   = ReaderConfig.withStrict(true)
        val expected = record("a" -> dynamicInt(1))
        decodeDynamic(input, expected, config)
      },
      test("accepts blank line after array ends") {
        val input    = "items[1]:\n  - a\n\nb: 2"
        val config   = ReaderConfig.withStrict(true)
        val expected = record(
          "items" -> zio.blocks.schema.DynamicValue.Sequence(
            Vector(zio.blocks.schema.DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.String("a")))
          ),
          "b" -> dynamicInt(2)
        )
        decodeDynamic(input, expected, config)
      },
      test("accepts blank line between nested object fields") {
        val input    = "a:\n  b: 1\n\n  c: 2"
        val config   = ReaderConfig.withStrict(true)
        val expected = record("a" -> record("b" -> dynamicInt(1), "c" -> dynamicInt(2)))
        decodeDynamic(input, expected, config)
      },
      test("ignores blank lines inside list array when strict=false") {
        val input    = "items[3]:\n  - a\n\n  - b\n  - c"
        val config   = ReaderConfig.withStrict(false)
        val expected = record(
          "items" -> zio.blocks.schema.DynamicValue.Sequence(
            Vector(
              zio.blocks.schema.DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.String("a")),
              zio.blocks.schema.DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.String("b")),
              zio.blocks.schema.DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.String("c"))
            )
          )
        )
        decodeDynamic(input, expected, config)
      },
      test("ignores blank lines inside tabular array when strict=false") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(PersonListWrapper.schema, deriver)
        val input   = "people[2]{name,age}:\n  Ada,25\n\n  Bob,30"
        val config  = ReaderConfig.withStrict(false)
        decode(input, PersonListWrapper(List(Person("Ada", 25), Person("Bob", 30))), codec, config)
      }
    ),
    suite("encode whitespace")(
      test("produces no trailing newline at end of output") {
        val data    = IdWrapper(123)
        val codec   = deriveCodec(IdWrapper.schema, ToonBinaryCodecDeriver)
        val encoded = codec.encodeToString(data)
        assertTrue(!encoded.endsWith("\n"))
      },
      test("maintains proper indentation for nested structures") {
        val data = UserWithItems(
          UserSimple(123, "Ada"),
          List("a", "b")
        )
        val expected = "user:\n  id: 123\n  name: Ada\nitems[2]: a,b"
        encode(data, expected)
      },
      test("respects custom indent size option") {
        val data     = UserWrapper(User(123, "Ada", Contact("ada@example.com", "555-0100")))
        val config   = WriterConfig.withIndent(4)
        val expected =
          "user:\n    id: 123\n    name: Ada\n    contact:\n        email: ada@example.com\n        phone: 555-0100"
        val codec = deriveCodec(UserWrapper.schema, ToonBinaryCodecDeriver)
        encode(data, expected, codec, config)
      }
    ),
    suite("encode key folding")(
      test("encodes folded chain to primitive (safe mode)") {
        val input    = record("a" -> record("b" -> record("c" -> dynamicInt(1))))
        val expected = "a.b.c: 1"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe)
        encodeDynamic(input, expected, config)
      },
      test("encodes folded chain with inline array") {
        val input = record(
          "data" -> record(
            "meta" -> record(
              "items" -> DynamicValue.Sequence(Vector(dynamicStr("x"), dynamicStr("y")))
            )
          )
        )
        val expected = "data.meta.items[2]: x,y"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe)
        encodeDynamic(input, expected, config)
      },
      test("skips folding on sibling literal-key collision (safe mode)") {
        val input = record(
          "data" -> record(
            "meta" -> record("items" -> DynamicValue.Sequence(Vector(dynamicInt(1), dynamicInt(2))))
          ),
          "data.meta.items" -> dynamicStr("literal")
        )
        val expected = "data:\n  meta:\n    items[2]: 1,2\ndata.meta.items: literal"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe)
        encodeDynamic(input, expected, config)
      },
      test("encodes partial folding with flattenDepth=2") {
        val input    = record("a" -> record("b" -> record("c" -> record("d" -> dynamicInt(1)))))
        val expected = "a.b:\n  c:\n    d: 1"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe).withFlattenDepth(2)
        encodeDynamic(input, expected, config)
      },
      test("encodes full chain with flattenDepth=Infinity (default)") {
        val input    = record("a" -> record("b" -> record("c" -> record("d" -> dynamicInt(1)))))
        val expected = "a.b.c.d: 1"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe)
        encodeDynamic(input, expected, config)
      },
      test("encodes standard nesting with flattenDepth=0 (no folding)") {
        val input    = record("a" -> record("b" -> record("c" -> dynamicInt(1))))
        val expected = "a:\n  b:\n    c: 1"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe).withFlattenDepth(0)
        encodeDynamic(input, expected, config)
      },
      test("encodes standard nesting with flattenDepth=1 (no practical effect)") {
        val input    = record("a" -> record("b" -> record("c" -> dynamicInt(1))))
        val expected = "a:\n  b:\n    c: 1"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe).withFlattenDepth(1)
        encodeDynamic(input, expected, config)
      },
      test("encodes standard nesting with keyFolding=off (baseline)") {
        val input    = record("a" -> record("b" -> record("c" -> dynamicInt(1))))
        val expected = "a:\n  b:\n    c: 1"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Off)
        encodeDynamic(input, expected, config)
      },
      test("encodes folded chain ending with empty object") {
        val input    = record("a" -> record("b" -> record("c" -> record())))
        val expected = "a.b.c:"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe)
        encodeDynamic(input, expected, config)
      },
      test("stops folding at array boundary (not single-key object)") {
        val input    = record("a" -> record("b" -> DynamicValue.Sequence(Vector(dynamicInt(1), dynamicInt(2)))))
        val expected = "a.b[2]: 1,2"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe)
        encodeDynamic(input, expected, config)
      },
      test("encodes folded chains preserving sibling field order") {
        val input = record(
          "first"  -> record("second" -> record("third" -> dynamicInt(1))),
          "simple" -> dynamicInt(2),
          "short"  -> record("path" -> dynamicInt(3))
        )
        val expected = "first.second.third: 1\nsimple: 2\nshort.path: 3"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe)
        encodeDynamic(input, expected, config)
      },
      test("stops folding at multi-key records") {
        val input    = record("a" -> record("b" -> dynamicInt(1), "c" -> dynamicInt(2)))
        val expected = "a:\n  b: 1\n  c: 2"
        val config   = WriterConfig.withKeyFolding(KeyFolding.Safe)
        encodeDynamic(input, expected, config)
      }
    ),
    suite("deep nesting - records with lists of records with lists")(
      test("encodes record with list of records containing lists of primitives") {
        val data = TeamWrapper(
          Team("Engineering", List(Member("Alice", List("Scala", "Rust")), Member("Bob", List("Go", "Python"))))
        )
        val expected =
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
            |        - Python""".stripMargin
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec   = deriveCodec(TeamWrapper.schema, deriver)
        encode(data, expected, codec)
      },
      test("decodes record with list of records containing lists of primitives") {
        val input =
          """team:
            |  name: Engineering
            |  members[2]:
            |    - name: Alice
            |      skills[2]: Scala,Rust
            |    - name: Bob
            |      skills[2]: Go,Python""".stripMargin
        val expected = TeamWrapper(
          Team("Engineering", List(Member("Alice", List("Scala", "Rust")), Member("Bob", List("Go", "Python"))))
        )
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec   = deriveCodec(TeamWrapper.schema, deriver)
        decode(input, expected, codec)
      },
      test("roundtrips deep nesting with three levels") {
        val data = OrgWrapper(
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
        )
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec   = deriveCodec(OrgWrapper.schema, deriver)
        val encoded = codec.encodeToString(data)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(data))
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
