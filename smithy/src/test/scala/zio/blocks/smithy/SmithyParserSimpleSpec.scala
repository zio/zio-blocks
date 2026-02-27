package zio.blocks.smithy

import zio.test._

object SmithyParserSimpleSpec extends ZIOSpecDefault {

  def spec = suite("SmithyParser")(
    suite("minimal valid files")(
      test("parses version and namespace") {
        val input  = "$version: \"2\"\nnamespace com.example"
        val result = SmithyParser.parse(input)
        assertTrue(
          result.isRight,
          result.toOption.get.version == "2",
          result.toOption.get.namespace == "com.example"
        )
      },
      test("parses version 2.0") {
        val input  = "$version: \"2.0\"\nnamespace com.example"
        val result = SmithyParser.parse(input)
        assertTrue(
          result.isRight,
          result.toOption.get.version == "2.0"
        )
      },
      test("handles extra whitespace and blank lines") {
        val input =
          """$version: "2"
            |
            |namespace com.example
            |""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(result.isRight)
      }
    ),
    suite("simple shapes")(
      test("parses a single string shape") {
        val input =
          """$version: "2"
            |namespace com.example
            |string MyString""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.isRight,
          result.toOption.get.shapes.length == 1,
          result.toOption.get.shapes.head.name == "MyString",
          result.toOption.get.shapes.head.shape.isInstanceOf[StringShape]
        )
      },
      test("parses multiple simple shapes") {
        val input =
          """$version: "2"
            |namespace com.example
            |string MyString
            |integer MyInt
            |boolean MyBool
            |blob MyBlob""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.isRight,
          result.toOption.get.shapes.length == 4,
          result.toOption.get.shapes(0).shape.isInstanceOf[StringShape],
          result.toOption.get.shapes(1).shape.isInstanceOf[IntegerShape],
          result.toOption.get.shapes(2).shape.isInstanceOf[BooleanShape],
          result.toOption.get.shapes(3).shape.isInstanceOf[BlobShape]
        )
      },
      test("parses all 13 simple shape types") {
        val input =
          """$version: "2"
            |namespace com.example
            |blob B
            |boolean Bo
            |string S
            |byte By
            |short Sh
            |integer I
            |long L
            |float F
            |double D
            |bigInteger BI
            |bigDecimal BD
            |timestamp T
            |document Doc""".stripMargin
        val result = SmithyParser.parse(input)
        val model  = result.toOption.get
        assertTrue(
          model.shapes.length == 13,
          model.shapes(0).shape.isInstanceOf[BlobShape],
          model.shapes(1).shape.isInstanceOf[BooleanShape],
          model.shapes(2).shape.isInstanceOf[StringShape],
          model.shapes(3).shape.isInstanceOf[ByteShape],
          model.shapes(4).shape.isInstanceOf[ShortShape],
          model.shapes(5).shape.isInstanceOf[IntegerShape],
          model.shapes(6).shape.isInstanceOf[LongShape],
          model.shapes(7).shape.isInstanceOf[FloatShape],
          model.shapes(8).shape.isInstanceOf[DoubleShape],
          model.shapes(9).shape.isInstanceOf[BigIntegerShape],
          model.shapes(10).shape.isInstanceOf[BigDecimalShape],
          model.shapes(11).shape.isInstanceOf[TimestampShape],
          model.shapes(12).shape.isInstanceOf[DocumentShape]
        )
      }
    ),
    suite("use statements")(
      test("parses a single use statement") {
        val input =
          """$version: "2"
            |namespace com.example
            |use com.other#MyShape""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.isRight,
          result.toOption.get.useStatements == List(ShapeId("com.other", "MyShape"))
        )
      },
      test("parses multiple use statements") {
        val input =
          """$version: "2"
            |namespace com.example
            |use com.other#Shape1
            |use com.other#Shape2""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.isRight,
          result.toOption.get.useStatements.length == 2,
          result.toOption.get.useStatements(0) == ShapeId("com.other", "Shape1"),
          result.toOption.get.useStatements(1) == ShapeId("com.other", "Shape2")
        )
      }
    ),
    suite("metadata")(
      test("parses string metadata") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata foo = "bar"""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.isRight,
          result.toOption.get.metadata == Map("foo" -> NodeValue.StringValue("bar"))
        )
      },
      test("parses numeric metadata") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata count = 42""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.isRight,
          result.toOption.get.metadata == Map("count" -> NodeValue.NumberValue(BigDecimal(42)))
        )
      },
      test("parses boolean metadata") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata debug = true""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.isRight,
          result.toOption.get.metadata == Map("debug" -> NodeValue.BooleanValue(true))
        )
      },
      test("parses null metadata") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata nothing = null""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.isRight,
          result.toOption.get.metadata == Map("nothing" -> NodeValue.NullValue)
        )
      },
      test("parses array metadata") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata tags = ["a", "b", "c"]""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.isRight,
          result.toOption.get.metadata == Map(
            "tags" -> NodeValue.ArrayValue(
              List(
                NodeValue.StringValue("a"),
                NodeValue.StringValue("b"),
                NodeValue.StringValue("c")
              )
            )
          )
        )
      },
      test("parses object metadata") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata config = {key: "value", count: 10}""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.isRight,
          result.toOption.get.metadata == Map(
            "config" -> NodeValue.ObjectValue(
              List(
                "key"   -> NodeValue.StringValue("value"),
                "count" -> NodeValue.NumberValue(BigDecimal(10))
              )
            )
          )
        )
      },
      test("parses multiple metadata entries") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata foo = "bar"
            |metadata baz = 42""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.isRight,
          result.toOption.get.metadata.size == 2,
          result.toOption.get.metadata("foo") == NodeValue.StringValue("bar"),
          result.toOption.get.metadata("baz") == NodeValue.NumberValue(BigDecimal(42))
        )
      }
    ),
    suite("trait applications")(
      test("parses @required trait on simple shape") {
        val input =
          """$version: "2"
            |namespace com.example
            |@required
            |string MyString""".stripMargin
        val result = SmithyParser.parse(input)
        val model  = result.toOption.get
        assertTrue(
          model.shapes.length == 1,
          model.shapes.head.shape.traits == List(TraitApplication.required)
        )
      },
      test("parses @documentation trait with string value") {
        val input =
          """$version: "2"
            |namespace com.example
            |@documentation("My documentation")
            |string MyString""".stripMargin
        val result = SmithyParser.parse(input)
        val model  = result.toOption.get
        assertTrue(
          model.shapes.head.shape.traits == List(TraitApplication.documentation("My documentation"))
        )
      },
      test("parses trait with object value") {
        val input =
          """$version: "2"
            |namespace com.example
            |@http(method: "GET", uri: "/foo")
            |string MyString""".stripMargin
        val result = SmithyParser.parse(input)
        val model  = result.toOption.get
        assertTrue(
          model.shapes.head.shape.traits == List(TraitApplication.http("GET", "/foo"))
        )
      },
      test("parses multiple traits on a shape") {
        val input =
          """$version: "2"
            |namespace com.example
            |@required
            |@documentation("doc")
            |string MyString""".stripMargin
        val result = SmithyParser.parse(input)
        val model  = result.toOption.get
        assertTrue(
          model.shapes.head.shape.traits.length == 2,
          model.shapes.head.shape.traits(0) == TraitApplication.required,
          model.shapes.head.shape.traits(1) == TraitApplication.documentation("doc")
        )
      },
      test("parses trait with fully qualified name") {
        val input =
          """$version: "2"
            |namespace com.example
            |@smithy.api#required
            |string MyString""".stripMargin
        val result = SmithyParser.parse(input)
        val model  = result.toOption.get
        assertTrue(
          model.shapes.head.shape.traits == List(
            TraitApplication(ShapeId("smithy.api", "required"), None)
          )
        )
      }
    ),
    suite("documentation comments")(
      test("/// converts to @documentation trait") {
        val input =
          """$version: "2"
            |namespace com.example
            |/// My documentation
            |string MyString""".stripMargin
        val result = SmithyParser.parse(input)
        val model  = result.toOption.get
        assertTrue(
          model.shapes.head.shape.traits == List(TraitApplication.documentation("My documentation"))
        )
      },
      test("multiple /// lines are concatenated") {
        val input =
          """$version: "2"
            |namespace com.example
            |/// Line one
            |/// Line two
            |string MyString""".stripMargin
        val result = SmithyParser.parse(input)
        val model  = result.toOption.get
        assertTrue(
          model.shapes.head.shape.traits == List(TraitApplication.documentation("Line one\nLine two"))
        )
      },
      test("/// combined with @trait") {
        val input =
          """$version: "2"
            |namespace com.example
            |/// My doc
            |@required
            |string MyString""".stripMargin
        val result = SmithyParser.parse(input)
        val model  = result.toOption.get
        assertTrue(
          model.shapes.head.shape.traits.length == 2,
          model.shapes.head.shape.traits(0) == TraitApplication.documentation("My doc"),
          model.shapes.head.shape.traits(1) == TraitApplication.required
        )
      }
    ),
    suite("node values")(
      test("parses negative numbers") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata n = -42""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.toOption.get.metadata("n") == NodeValue.NumberValue(BigDecimal(-42))
        )
      },
      test("parses decimal numbers") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata pi = 3.14""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.toOption.get.metadata("pi") == NodeValue.NumberValue(BigDecimal("3.14"))
        )
      },
      test("parses nested arrays and objects") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata nested = {arr: [1, 2], obj: {a: "b"}}""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.toOption.get.metadata("nested") == NodeValue.ObjectValue(
            List(
              "arr" -> NodeValue.ArrayValue(
                List(NodeValue.NumberValue(BigDecimal(1)), NodeValue.NumberValue(BigDecimal(2)))
              ),
              "obj" -> NodeValue.ObjectValue(
                List("a" -> NodeValue.StringValue("b"))
              )
            )
          )
        )
      },
      test("parses empty array") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata empty = []""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.toOption.get.metadata("empty") == NodeValue.ArrayValue(Nil)
        )
      },
      test("parses empty object") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata empty = {}""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.toOption.get.metadata("empty") == NodeValue.ObjectValue(Nil)
        )
      }
    ),
    suite("comments")(
      test("skips line comments") {
        val input =
          """$version: "2"
            |// This is a comment
            |namespace com.example
            |// Another comment
            |string MyString""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.isRight,
          result.toOption.get.shapes.length == 1
        )
      }
    ),
    suite("error cases")(
      test("fails on missing version") {
        val input  = "namespace com.example"
        val result = SmithyParser.parse(input)
        assertTrue(result.isLeft)
      },
      test("fails on missing namespace") {
        val input  = "$version: \"2\""
        val result = SmithyParser.parse(input)
        assertTrue(result.isLeft)
      },
      test("fails on invalid syntax") {
        val input =
          """$version: "2"
            |namespace com.example
            |!! invalid""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(result.isLeft)
      },
      test("error includes line and column info") {
        val input  = "namespace com.example"
        val result = SmithyParser.parse(input)
        result match {
          case Left(err) =>
            assertTrue(
              err.line >= 1,
              err.column >= 0
            )
          case Right(_) => assertTrue(false)
        }
      }
    ),
    suite("comprehensive")(
      test("parses a complete model with all control statements and shapes") {
        val input =
          """$version: "2"
            |
            |metadata author = "test"
            |metadata version = 1
            |
            |namespace com.example
            |
            |use com.other#Helper
            |
            |/// A name type
            |string Name
            |
            |@required
            |integer Age
            |
            |boolean Active""".stripMargin
        val result = SmithyParser.parse(input)
        val model  = result.toOption.get
        assertTrue(
          model.version == "2",
          model.namespace == "com.example",
          model.useStatements == List(ShapeId("com.other", "Helper")),
          model.metadata.size == 2,
          model.metadata("author") == NodeValue.StringValue("test"),
          model.metadata("version") == NodeValue.NumberValue(BigDecimal(1)),
          model.shapes.length == 3,
          model.shapes(0).name == "Name",
          model.shapes(0).shape.traits == List(TraitApplication.documentation("A name type")),
          model.shapes(1).name == "Age",
          model.shapes(1).shape.traits == List(TraitApplication.required),
          model.shapes(2).name == "Active",
          model.shapes(2).shape.traits.isEmpty
        )
      }
    ),
    suite("string escapes")(
      test("parses strings with escape sequences") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata msg = "hello\nworld"""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.toOption.get.metadata("msg") == NodeValue.StringValue("hello\nworld")
        )
      }
    ),
    suite("metadata before namespace")(
      test("metadata can appear before namespace") {
        val input =
          """$version: "2"
            |metadata foo = "bar"
            |namespace com.example""".stripMargin
        val result = SmithyParser.parse(input)
        assertTrue(
          result.isRight,
          result.toOption.get.metadata("foo") == NodeValue.StringValue("bar"),
          result.toOption.get.namespace == "com.example"
        )
      }
    )
  )
}
