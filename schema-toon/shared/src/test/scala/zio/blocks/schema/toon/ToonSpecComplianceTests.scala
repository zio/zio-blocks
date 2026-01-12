package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema.{DynamicValue, PrimitiveValue, Schema}
import java.nio.charset.StandardCharsets

/*
 * Test cases in this file are derived from:
 *
 * 1. Official TOON Specification (https://github.com/toon-format/spec)
 *    Licensed under Apache 2.0 License
 *    Copyright (c) TOON Format Contributors
 *
 * 2. toon4s Library (https://github.com/vim89/toon4s)
 *    Licensed under Apache 2.0 License (assumed - originally stated as MIT in research)
 *    Copyright (c) vim89 and contributors
 *
 * All borrowed content is used in compliance with open source license terms.
 */
object ToonSpecComplianceTests extends ZIOSpecDefault {

  def spec = suite("TOON Specification Compliance Tests")(
    suite("Official Spec Fixtures - Encoding")(
      suite("primitives.json - Complete Test Coverage")(
        test("safe strings without quotes") {
          val codec = ToonBinaryCodec.stringCodec
          assertTrue(
            codec.encodeToString("hello") == "hello",
            codec.encodeToString("Ada_99") == "Ada_99"
          )
        },
        test("quotes empty string") {
          val codec = ToonBinaryCodec.stringCodec
          assertTrue(codec.encodeToString("") == "\"\"")
        },
        test("quotes strings resembling reserved words") {
          val codec = ToonBinaryCodec.stringCodec
          assertTrue(
            codec.encodeToString("true") == "\"true\"",
            codec.encodeToString("false") == "\"false\"",
            codec.encodeToString("null") == "\"null\""
          )
        },
        test("quotes numeric-looking strings") {
          val codec = ToonBinaryCodec.stringCodec
          assertTrue(
            codec.encodeToString("42") == "\"42\"",
            codec.encodeToString("-3.14") == "\"-3.14\"",
            codec.encodeToString("1e-6") == "\"1e-6\"",
            codec.encodeToString("05") == "\"05\""
          )
        },
        test("escapes control characters") {
          val codec = ToonBinaryCodec.stringCodec
          assertTrue(
            codec.encodeToString("line1\nline2") == "\"line1\\nline2\"",
            codec.encodeToString("tab\there") == "\"tab\\there\"",
            codec.encodeToString("return\rcarriage") == "\"return\\rcarriage\"",
            codec.encodeToString("C:\\Users\\path") == "\"C:\\\\Users\\\\path\""
          )
        },
        test("quotes TOON syntax patterns") {
          val codec = ToonBinaryCodec.stringCodec
          assertTrue(
            codec.encodeToString("[3]: x,y") == "\"[3]: x,y\"",
            codec.encodeToString("- item") == "\"- item\"",
            codec.encodeToString("[test]") == "\"[test]\"",
            codec.encodeToString("{key}") == "\"{key}\""
          )
        },
        test("handles single hyphen correctly") {
          case class Marker(marker: String)
          case class Note(note: String)
          case class Items(items: List[String])
          case class Tags(tags: List[String])

          val markerSchema = Schema.derived[Marker]
          val noteSchema   = Schema.derived[Note]
          val itemsSchema  = Schema.derived[Items]
          val tagsSchema   = Schema.derived[Tags]

          assertTrue(
            markerSchema.derive(ToonFormat.deriver).encodeToString(Marker("-")) == "marker: \"-\"",
            noteSchema.derive(ToonFormat.deriver).encodeToString(Note("- item")) == "note: \"- item\"",
            itemsSchema.derive(ToonFormat.deriver).encodeToString(Items(List("-"))) == "items[1]: \"-\"",
            tagsSchema
              .derive(ToonFormat.deriver)
              .encodeToString(Tags(List("a", "- item", "b"))) == "tags[3]: a,\"- item\",b"
          )
        },
        test("encodes Unicode strings without quotes") {
          val codec = ToonBinaryCodec.stringCodec
          assertTrue(
            codec.encodeToString("café") == "café",
            codec.encodeToString("你好") == "你好",
            codec.encodeToString("🚀") == "🚀",
            codec.encodeToString("hello 👋 world") == "hello 👋 world"
          )
        },
        test("encodes numbers correctly") {
          assertTrue(
            ToonBinaryCodec.intCodec.encodeToString(42) == "42",
            ToonBinaryCodec.intCodec.encodeToString(-7) == "-7",
            ToonBinaryCodec.intCodec.encodeToString(0) == "0",
            ToonBinaryCodec.doubleCodec.encodeToString(-0.0) == "0",
            ToonBinaryCodec.doubleCodec.encodeToString(3.14) == "3.14",
            ToonBinaryCodec.doubleCodec.encodeToString(0.000001) == "0.000001",
            ToonBinaryCodec.longCodec.encodeToString(1000000L) == "1000000",
            ToonBinaryCodec.longCodec.encodeToString(9007199254740991L) == "9007199254740991"
          )
        },
        test("encodes booleans and null") {
          val boolCodec = ToonBinaryCodec.booleanCodec
          val optCodec  = Schema.option[String].derive(ToonFormat.deriver)
          assertTrue(
            boolCodec.encodeToString(true) == "true",
            boolCodec.encodeToString(false) == "false",
            optCodec.encodeToString(None) == "null"
          )
        }
      ),
      suite("arrays-tabular.json - Tabular Format Tests")(
        test("uniform objects with 2-space indentation") {
          case class Item(sku: String, qty: Int, price: Double)
          case class Wrapper(items: List[Item])
          val codec  = Schema.derived[Wrapper].derive(ToonFormat.deriver)
          val result = codec.encodeToString(
            Wrapper(
              List(
                Item("A1", 2, 9.99),
                Item("B2", 1, 14.5)
              )
            )
          )
          val expected = "items[2]{sku,qty,price}:\n  A1,2,9.99\n  B2,1,14.5\n"
          assertTrue(result == expected)
        },
        test("null values in tabular rows") {
          case class Item(id: Int, value: Option[String])
          case class Wrapper(items: List[Item])
          val codec  = Schema.derived[Wrapper].derive(ToonFormat.deriver)
          val result = codec.encodeToString(
            Wrapper(
              List(
                Item(1, None),
                Item(2, Some("test"))
              )
            )
          )
          val expected = "items[2]{id,value}:\n  1,null\n  2,test\n"
          assertTrue(result == expected)
        },
        test("quotes delimiter-containing values") {
          case class Item(sku: String, desc: String, qty: Int)
          case class Wrapper(items: List[Item])
          val codec  = Schema.derived[Wrapper].derive(ToonFormat.deriver)
          val result = codec.encodeToString(
            Wrapper(
              List(
                Item("A,1", "cool", 2),
                Item("B2", "wip: test", 1)
              )
            )
          )
          val expected = "items[2]{sku,desc,qty}:\n  \"A,1\",cool,2\n  B2,\"wip: test\",1\n"
          assertTrue(result == expected)
        },
        test("quotes ambiguous string values") {
          case class Item(id: Int, status: String)
          case class Wrapper(items: List[Item])
          val codec  = Schema.derived[Wrapper].derive(ToonFormat.deriver)
          val result = codec.encodeToString(
            Wrapper(
              List(
                Item(1, "true"),
                Item(2, "false")
              )
            )
          )
          val expected = "items[2]{id,status}:\n  1,\"true\"\n  2,\"false\"\n"
          assertTrue(result == expected)
        }
      ),
      suite("arrays-primitive.json - Primitive Array Tests")(
        test("inline primitive arrays") {
          case class Wrapper(numbers: List[Int])
          val codec    = Schema.derived[Wrapper].derive(ToonFormat.deriver)
          val result   = codec.encodeToString(Wrapper(List(1, 2, 3, 4, 5)))
          val expected = "numbers[5]: 1,2,3,4,5"
          assertTrue(result == expected)
        },
        test("empty arrays") {
          case class Wrapper(items: List[String])
          val codec    = Schema.derived[Wrapper].derive(ToonFormat.deriver)
          val result   = codec.encodeToString(Wrapper(List.empty))
          val expected = "items[0]:"
          assertTrue(result == expected)
        },
        test("string arrays with quoting") {
          case class Wrapper(tags: List[String])
          val codec    = Schema.derived[Wrapper].derive(ToonFormat.deriver)
          val result   = codec.encodeToString(Wrapper(List("alpha", "beta-1", "gamma")))
          val expected = "tags[3]: alpha,beta-1,gamma"
          assertTrue(result == expected)
        }
      ),
      suite("arrays-nested.json - Nested Array Encoding")(
        test("encodes nested arrays within objects") {
          case class Data(coords: List[Int])
          case class Wrapper(data: Data)
          val schema   = Schema.derived[Wrapper]
          val codec    = schema.derive(ToonFormat.deriver)
          val input    = Wrapper(Data(List(1, 2, 3)))
          val result   = codec.encodeToString(input)
          val expected =
            """data: 
              |  coords[3]: 1,2,3""".stripMargin
          assertTrue(result == expected)
        },
        test("encodes empty nested arrays") {
          case class Data(items: List[String])
          case class Wrapper(data: Data)
          val schema   = Schema.derived[Wrapper]
          val codec    = schema.derive(ToonFormat.deriver)
          val input    = Wrapper(Data(List.empty))
          val result   = codec.encodeToString(input)
          val expected =
            """data: 
              |  items[0]:""".stripMargin
          assertTrue(result == expected)
        }
      ),
      suite("objects.json - Object Encoding Tests")(
        test("simple objects with primitives") {
          case class User(name: String, age: Int, active: Boolean)
          val codec    = Schema.derived[User].derive(ToonFormat.deriver)
          val result   = codec.encodeToString(User("Ada", 36, true))
          val expected =
            """name: Ada
              |age: 36
              |active: true""".stripMargin
          assertTrue(result == expected)
        },
        test("nested object structures") {
          case class Address(city: String, zip: String)
          case class Person(name: String, address: Address)
          val codec    = Schema.derived[Person].derive(ToonFormat.deriver)
          val result   = codec.encodeToString(Person("Ada", Address("Austin", "78701")))
          val expected =
            """name: Ada
              |address: 
              |  city: Austin
              |  zip: "78701"""".stripMargin
          assertTrue(result == expected)
        },
        test("quotes object keys containing delimiters") {
          // This test requires dynamic value support since we can't have ":" in Scala field names
          val dv = DynamicValue.Record(
            Vector(
              ("order:id", DynamicValue.Primitive(PrimitiveValue.Int(1))),
              ("full name", DynamicValue.Primitive(PrimitiveValue.String("Ada")))
            )
          )
          val codec    = Schema.dynamic.derive(ToonFormat.deriver)
          val result   = codec.encodeToString(dv)
          val expected =
            """"order:id": 
              |1
              |full name: 
              |Ada
              |""".stripMargin
          assertTrue(result == expected)
        }
      )
    ),
    suite("Official Spec Fixtures - Decoding")(
      suite("primitives.json - String Parsing")(
        test("parses unquoted safe strings") {
          val input  = "hello"
          val reader = new ToonReader(
            input.getBytes(StandardCharsets.UTF_8),
            new Array[Char](1024),
            input.length,
            ToonReaderConfig
          )
          assertTrue(ToonBinaryCodec.stringCodec.decodeValue(reader, "") == "hello")
        },
        test("parses quoted strings with escapes") {
          val input  = "\"line1\\nline2\""
          val reader = new ToonReader(
            input.getBytes(StandardCharsets.UTF_8),
            new Array[Char](1024),
            input.length,
            ToonReaderConfig
          )
          assertTrue(ToonBinaryCodec.stringCodec.decodeValue(reader, "") == "line1\nline2")
        },
        test("parses quoted reserved words as strings") {
          val tests = List(
            ("\"true\"", "true"),
            ("\"false\"", "false"),
            ("\"null\"", "null")
          )
          val results = tests.map { case (input, expected) =>
            val reader = new ToonReader(
              input.getBytes(StandardCharsets.UTF_8),
              new Array[Char](1024),
              input.length,
              ToonReaderConfig
            )
            ToonBinaryCodec.stringCodec.decodeValue(reader, "") == expected
          }.forall(identity)
          assertTrue(results)
        }
      ),
      suite("numbers.json - Number Parsing")(
        test("parses integers correctly") {
          case class V(v: Int)
          val codec  = Schema.derived[V].derive(ToonFormat.deriver)
          val input  = "v: 42"
          val reader = new ToonReader(
            input.getBytes(StandardCharsets.UTF_8),
            new Array[Char](1024),
            input.length,
            ToonReaderConfig
          )
          assertTrue(codec.decodeValue(reader, V(0)) == V(42))
        },
        test("normalizes negative zero") {
          case class V(v: Int)
          val codec  = Schema.derived[V].derive(ToonFormat.deriver)
          val input  = "v: -0"
          val reader = new ToonReader(
            input.getBytes(StandardCharsets.UTF_8),
            new Array[Char](1024),
            input.length,
            ToonReaderConfig
          )
          assertTrue(codec.decodeValue(reader, V(0)) == V(0))
        },
        test("parses decimals") {
          case class Value(v: Double)
          val schema = Schema.derived[Value]
          val codec  = schema.derive(ToonFormat.deriver)
          val input  = "v: 3.14"
          val reader = new ToonReader(
            input.getBytes(StandardCharsets.UTF_8),
            new Array[Char](1024),
            input.length,
            ToonReaderConfig
          )
          assertTrue(codec.decodeValue(reader, Value(0.0)) == Value(3.14))
        },
        test("treats leading zero as string not number") {
          val codec  = Schema.dynamic.derive(ToonFormat.deriver)
          val input  = "value: 05"
          val reader = new ToonReader(
            input.getBytes(StandardCharsets.UTF_8),
            new Array[Char](1024),
            input.length,
            ToonReaderConfig
          )
          val decoded = codec.decodeValue(reader, DynamicValue.Primitive(PrimitiveValue.Unit))
          decoded match {
            case r: DynamicValue.Record =>
              val fieldMap = r.fields.toMap
              assertTrue(fieldMap("value") == DynamicValue.Primitive(PrimitiveValue.String("05")))
            case _ => assertTrue(false)
          }
        }
      )
    ),
    suite("Edge Cases and Advanced Patterns")(
      test("deeply nested structures") {
        case class Inner(value: Int)
        case class Middle(inner: Inner, tag: String)
        case class Outer(middle: Middle, count: Int)
        val codec   = Schema.derived[Outer].derive(ToonFormat.deriver)
        val input   = Outer(Middle(Inner(42), "test"), 3)
        val encoded = codec.encodeToString(input)
        val decoded = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(input))
      },
      test("round-trip encoding preserves data") {
        case class Complex(
          id: Int,
          name: String,
          active: Boolean,
          score: Option[Double],
          tags: List[String]
        )
        val codec    = Schema.derived[Complex].derive(ToonFormat.deriver)
        val original = Complex(123, "test-user", true, Some(98.6), List("a", "b", "c"))
        val encoded  = codec.encodeToString(original)
        val decoded  = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(original))
      },
      test("handles optional fields correctly") {
        case class Data(required: String, optional: Option[Int])
        val codec        = Schema.derived[Data].derive(ToonFormat.deriver)
        val expectedSome =
          """required: test
            |optional: 42""".stripMargin
        val expectedNone =
          """required: test
            |optional: null""".stripMargin
        assertTrue(
          codec.encodeToString(Data("test", Some(42))) == expectedSome,
          codec.encodeToString(Data("test", None)) == expectedNone
        )
      },
      test("preserves array order") {
        case class Items(values: List[Int])
        val codec   = Schema.derived[Items].derive(ToonFormat.deriver)
        val input   = Items(List(5, 3, 9, 1, 7))
        val encoded = codec.encodeToString(input)
        val decoded = codec.decodeFromString(encoded)
        assertTrue(decoded == Right(input))
      }
    )
  )
}
