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
object ToonSpecConformanceSpec extends ZIOSpecDefault {

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
      }
    ),
    suite("delimiters")(
      test("encodes primitive arrays with pipe delimiter") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Pipe)
        val codec    = deriveCodec(Tags.schema, deriver)
        val data     = Tags(List("reading", "gaming", "coding"))
        val expected = "tags[3|]: reading|gaming|coding"
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
      test("does not quote commas with pipe delimiter") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Pipe)
        val codec    = deriveCodec(Items.schema, deriver)
        val data     = Items(List("a,b", "c,d"))
        val expected = "items[2|]: a,b|c,d"
        encode(data, expected, codec)
      },
      test("quotes strings containing pipe delimiter when using pipe") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Pipe)
        val codec    = deriveCodec(Items.schema, deriver)
        val data     = Items(List("a", "b|c", "d"))
        val expected = "items[3|]: a|\"b|c\"|d"
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
                         |
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
                         |
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
}
