package zio.blocks.smithy

import zio.test._

object NodeValueSpec extends ZIOSpecDefault {
  def spec = suite("NodeValue")(
    suite("String")(
      test("creates string value") {
        val value = NodeValue.String("hello")
        assertTrue(value.asInstanceOf[NodeValue.String].value == "hello")
      },
      test("handles empty string") {
        val value = NodeValue.String("")
        assertTrue(value.asInstanceOf[NodeValue.String].value == "")
      },
      test("handles string with special characters") {
        val value = NodeValue.String("hello\nworld\t!")
        assertTrue(value.asInstanceOf[NodeValue.String].value == "hello\nworld\t!")
      }
    ),
    suite("Number")(
      test("creates number value from Int") {
        val value = NodeValue.Number(BigDecimal(42))
        assertTrue(value.asInstanceOf[NodeValue.Number].value == BigDecimal(42))
      },
      test("creates number value from Double") {
        val value = NodeValue.Number(BigDecimal(3.14))
        val num   = value.asInstanceOf[NodeValue.Number].value
        assertTrue(num.toDouble == 3.14)
      },
      test("creates number value from large integer") {
        val value = NodeValue.Number(BigDecimal("123456789012345678901234567890"))
        val num   = value.asInstanceOf[NodeValue.Number].value
        assertTrue(num == BigDecimal("123456789012345678901234567890"))
      },
      test("creates negative number") {
        val value = NodeValue.Number(BigDecimal(-42))
        assertTrue(value.asInstanceOf[NodeValue.Number].value == BigDecimal(-42))
      },
      test("creates zero") {
        val value = NodeValue.Number(BigDecimal(0))
        assertTrue(value.asInstanceOf[NodeValue.Number].value == BigDecimal(0))
      }
    ),
    suite("Boolean")(
      test("creates true value") {
        val value = NodeValue.Boolean(true)
        assertTrue(value.asInstanceOf[NodeValue.Boolean].value == true)
      },
      test("creates false value") {
        val value = NodeValue.Boolean(false)
        assertTrue(value.asInstanceOf[NodeValue.Boolean].value == false)
      }
    ),
    suite("Null")(
      test("creates null value") {
        val value = NodeValue.Null
        assertTrue(value.isInstanceOf[NodeValue.Null.type])
      }
    ),
    suite("Array")(
      test("creates empty array") {
        val value = NodeValue.Array(List.empty)
        assertTrue(value.asInstanceOf[NodeValue.Array].values.isEmpty)
      },
      test("creates array with string values") {
        val values = List(
          NodeValue.String("a"),
          NodeValue.String("b"),
          NodeValue.String("c")
        )
        val array = NodeValue.Array(values)
        assertTrue(array.asInstanceOf[NodeValue.Array].values.length == 3)
      },
      test("creates array with mixed types") {
        val values = List(
          NodeValue.String("hello"),
          NodeValue.Number(BigDecimal(42)),
          NodeValue.Boolean(true),
          NodeValue.Null
        )
        val array = NodeValue.Array(values)
        val arr   = array.asInstanceOf[NodeValue.Array]
        assertTrue(
          arr.values.length == 4,
          arr.values(0).isInstanceOf[NodeValue.String],
          arr.values(1).isInstanceOf[NodeValue.Number],
          arr.values(2).isInstanceOf[NodeValue.Boolean],
          arr.values(3) == NodeValue.Null
        )
      },
      test("creates array with nested arrays") {
        val inner1 = NodeValue.Array(
          List(NodeValue.String("a"), NodeValue.String("b"))
        )
        val inner2 = NodeValue.Array(
          List(NodeValue.Number(BigDecimal(1)), NodeValue.Number(BigDecimal(2)))
        )
        val outer = NodeValue.Array(List(inner1, inner2))
        val arr   = outer.asInstanceOf[NodeValue.Array]
        assertTrue(
          arr.values.length == 2,
          arr.values(0).isInstanceOf[NodeValue.Array],
          arr.values(1).isInstanceOf[NodeValue.Array]
        )
      }
    ),
    suite("Object")(
      test("creates empty object") {
        val value = NodeValue.Object(List.empty)
        assertTrue(value.asInstanceOf[NodeValue.Object].fields.isEmpty)
      },
      test("creates object with single field") {
        val fields = List(("name", NodeValue.String("Alice")))
        val obj    = NodeValue.Object(fields)
        val o      = obj.asInstanceOf[NodeValue.Object]
        assertTrue(
          o.fields.length == 1,
          o.fields(0)._1 == "name",
          o.fields(0)._2.asInstanceOf[NodeValue.String].value == "Alice"
        )
      },
      test("creates object with multiple fields") {
        val fields = List(
          ("name", NodeValue.String("Alice")),
          ("age", NodeValue.Number(BigDecimal(30))),
          ("active", NodeValue.Boolean(true))
        )
        val obj = NodeValue.Object(fields)
        val o   = obj.asInstanceOf[NodeValue.Object]
        assertTrue(
          o.fields.length == 3,
          o.fields(0)._1 == "name",
          o.fields(1)._1 == "age",
          o.fields(2)._1 == "active"
        )
      },
      test("creates object with nested object") {
        val inner = NodeValue.Object(
          List(("street", NodeValue.String("123 Main St")))
        )
        val fields = List(
          ("address", inner)
        )
        val obj = NodeValue.Object(fields)
        val o   = obj.asInstanceOf[NodeValue.Object]
        assertTrue(
          o.fields.length == 1,
          o.fields(0)._1 == "address",
          o.fields(0)._2.isInstanceOf[NodeValue.Object]
        )
      },
      test("creates object with array field") {
        val array = NodeValue.Array(
          List(NodeValue.String("tag1"), NodeValue.String("tag2"))
        )
        val fields = List(("tags", array))
        val obj    = NodeValue.Object(fields)
        val o      = obj.asInstanceOf[NodeValue.Object]
        assertTrue(
          o.fields.length == 1,
          o.fields(0)._1 == "tags",
          o.fields(0)._2.isInstanceOf[NodeValue.Array]
        )
      },
      test("creates complex nested structure") {
        val address = NodeValue.Object(
          List(
            ("street", NodeValue.String("123 Main St")),
            ("city", NodeValue.String("Springfield"))
          )
        )
        val tags = NodeValue.Array(
          List(NodeValue.String("developer"), NodeValue.String("scala"))
        )
        val person = NodeValue.Object(
          List(
            ("name", NodeValue.String("Alice")),
            ("age", NodeValue.Number(BigDecimal(30))),
            ("address", address),
            ("tags", tags),
            ("active", NodeValue.Boolean(true))
          )
        )
        val p = person.asInstanceOf[NodeValue.Object]
        assertTrue(
          p.fields.length == 5,
          p.fields(0)._1 == "name",
          p.fields(2)._2.isInstanceOf[NodeValue.Object],
          p.fields(3)._2.isInstanceOf[NodeValue.Array]
        )
      }
    )
  )
}
