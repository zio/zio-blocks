package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object PathParserCoverageSpec extends SchemaBaseSpec {
  import DynamicOptic.Node

  def spec: Spec[TestEnvironment, Any] = suite("PathParserCoverageSpec")(
    test("parses element and index selectors") {
      assert(PathParser.parse("[*]"))(isRight(equalTo(Vector(Node.Elements)))) &&
      assert(PathParser.parse("[:*]"))(isRight(equalTo(Vector(Node.Elements)))) &&
      assert(PathParser.parse("[0]"))(isRight(equalTo(Vector(Node.AtIndex(0))))) &&
      assert(PathParser.parse("[0, 2,4]"))(isRight(equalTo(Vector(Node.AtIndices(Vector(0, 2, 4)))))) &&
      assert(PathParser.parse("[1:3]"))(isRight(equalTo(Vector(Node.AtIndices(1 until 3))))) &&
      assert(PathParser.parse("[3:1]"))(isRight(equalTo(Vector(Node.AtIndices(Seq.empty[Int]))))) &&
      assert(PathParser.parse("["))(isLeft(anything))
    },
    test("parses map access and map keys") {
      assert(PathParser.parse("{*}"))(isRight(equalTo(Vector(Node.MapValues)))) &&
      assert(PathParser.parse("{*:}"))(isRight(equalTo(Vector(Node.MapKeys)))) &&
      assert(PathParser.parse("{:*}"))(isRight(equalTo(Vector(Node.MapValues)))) &&
      assert(PathParser.parse("{1}"))(
        isRight(equalTo(Vector(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Int(1))))))
      ) &&
      assert(PathParser.parse("{-1}"))(
        isRight(equalTo(Vector(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Int(-1))))))
      ) &&
      assert(PathParser.parse("{true}"))(
        isRight(equalTo(Vector(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))))
      ) &&
      assert(PathParser.parse("{false}"))(
        isRight(equalTo(Vector(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))))
      ) &&
      assert(PathParser.parse("{1,2}"))(isRight(anything)) &&
      assert(PathParser.parse("{foo}"))(isLeft(anything))
    },
    test("parses quoted string/char keys and associated error cases") {
      assert(PathParser.parse("{\"k\"}"))(
        isRight(equalTo(Vector(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("k"))))))
      ) &&
      {
        val parsed = PathParser.parse("""{"a\n"}""")
        assert(parsed)(isRight(anything)) &&
        assertTrue(
          parsed.exists {
            case Vector(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String(s)))) =>
              s.length == 2 && s.charAt(0) == 'a' && s.charAt(1) == '\n'
            case _ => false
          }
        )
      } &&
      assert(PathParser.parse("""{"\x"}"""))(isLeft(anything)) &&
      assert(PathParser.parse("{\"\\"))(isLeft(anything)) &&
      assert(PathParser.parse("{'a'}"))(
        isRight(equalTo(Vector(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char('a'))))))
      ) &&
      assert(PathParser.parse("{'\\n'}"))(
        isRight(equalTo(Vector(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Char('\n'))))))
      ) &&
      assert(PathParser.parse("{''}"))(isLeft(anything)) &&
      assert(PathParser.parse("{'ab'}"))(isLeft(anything)) &&
      assert(PathParser.parse("{'\\x'}"))(isLeft(anything))
    },
    test("parses variant cases and hits end-of-input error branches") {
      assert(PathParser.parse("<Foo>"))(isRight(equalTo(Vector(Node.Case("Foo"))))) &&
      assert(PathParser.parse("<Foo"))(isLeft(anything))
    },
    test("hits additional parsing error branches") {
      assert(PathParser.parse("!"))(isLeft(anything)) &&
      assert(PathParser.parse("."))(isLeft(anything)) &&
      assert(PathParser.parse(".1"))(isLeft(anything)) &&
      assert(PathParser.parse("<>"))(isLeft(anything)) &&
      assert(PathParser.parse("<Foo]"))(isLeft(anything)) &&
      assert(PathParser.parse("[*x]"))(isLeft(anything)) &&
      assert(PathParser.parse("[*:x]"))(isLeft(anything)) &&
      assert(PathParser.parse("[:x]"))(isLeft(anything)) &&
      assert(PathParser.parse("[1:]"))(isLeft(anything)) &&
      assert(PathParser.parse("[2147483648]"))(isLeft(anything)) &&
      assert(PathParser.parse("{-}"))(isLeft(anything)) &&
      assert(PathParser.parse("{-x}"))(isLeft(anything)) &&
      assert(PathParser.parse("{-2147483648}"))(
        isRight(equalTo(Vector(Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.Int(Int.MinValue))))))
      ) &&
      assert(PathParser.parse("{\"abc}"))(isLeft(anything)) &&
      assert(PathParser.parse("{'a"))(isLeft(anything))
    }
  )
}
