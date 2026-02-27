package zio.blocks.smithy

import zio.test._

object NodeValueSpec extends ZIOSpecDefault {
  def spec = suite("NodeValue")(
    suite("StringValue")(
      test("creates string value") {
        val value = NodeValue.StringValue("hello")
        assertTrue(value.asInstanceOf[NodeValue.StringValue].value == "hello")
      },
      test("handles empty string") {
        val value = NodeValue.StringValue("")
        assertTrue(value.asInstanceOf[NodeValue.StringValue].value == "")
      },
      test("handles string with special characters") {
        val value = NodeValue.StringValue("hello\nworld\t!")
        assertTrue(value.asInstanceOf[NodeValue.StringValue].value == "hello\nworld\t!")
      }
    ),
    suite("NumberValue")(
      test("creates number value from Int") {
        val value = NodeValue.NumberValue(BigDecimal(42))
        assertTrue(value.asInstanceOf[NodeValue.NumberValue].value == BigDecimal(42))
      },
      test("creates number value from Double") {
        val value = NodeValue.NumberValue(BigDecimal(3.14))
        val num   = value.asInstanceOf[NodeValue.NumberValue].value
        assertTrue(num.toDouble == 3.14)
      },
      test("creates number value from large integer") {
        val value = NodeValue.NumberValue(BigDecimal("123456789012345678901234567890"))
        val num   = value.asInstanceOf[NodeValue.NumberValue].value
        assertTrue(num == BigDecimal("123456789012345678901234567890"))
      },
      test("creates negative number") {
        val value = NodeValue.NumberValue(BigDecimal(-42))
        assertTrue(value.asInstanceOf[NodeValue.NumberValue].value == BigDecimal(-42))
      },
      test("creates zero") {
        val value = NodeValue.NumberValue(BigDecimal(0))
        assertTrue(value.asInstanceOf[NodeValue.NumberValue].value == BigDecimal(0))
      }
    ),
    suite("BooleanValue")(
      test("creates true value") {
        val value = NodeValue.BooleanValue(true)
        assertTrue(value.asInstanceOf[NodeValue.BooleanValue].value == true)
      },
      test("creates false value") {
        val value = NodeValue.BooleanValue(false)
        assertTrue(value.asInstanceOf[NodeValue.BooleanValue].value == false)
      }
    ),
    suite("NullValue")(
      test("creates null value") {
        val value = NodeValue.NullValue
        assertTrue(value.isInstanceOf[NodeValue.NullValue.type])
      }
    ),
    suite("ArrayValue")(
      test("creates empty array") {
        val value = NodeValue.ArrayValue(List.empty)
        assertTrue(value.asInstanceOf[NodeValue.ArrayValue].values.isEmpty)
      },
      test("creates array with string values") {
        val values = List(
          NodeValue.StringValue("a"),
          NodeValue.StringValue("b"),
          NodeValue.StringValue("c")
        )
        val array = NodeValue.ArrayValue(values)
        assertTrue(array.asInstanceOf[NodeValue.ArrayValue].values.length == 3)
      },
      test("creates array with mixed types") {
        val values = List(
          NodeValue.StringValue("hello"),
          NodeValue.NumberValue(BigDecimal(42)),
          NodeValue.BooleanValue(true),
          NodeValue.NullValue
        )
        val array = NodeValue.ArrayValue(values)
        val arr   = array.asInstanceOf[NodeValue.ArrayValue]
        assertTrue(
          arr.values.length == 4,
          arr.values(0).isInstanceOf[NodeValue.StringValue],
          arr.values(1).isInstanceOf[NodeValue.NumberValue],
          arr.values(2).isInstanceOf[NodeValue.BooleanValue],
          arr.values(3) == NodeValue.NullValue
        )
      },
      test("creates array with nested arrays") {
        val inner1 = NodeValue.ArrayValue(
          List(NodeValue.StringValue("a"), NodeValue.StringValue("b"))
        )
        val inner2 = NodeValue.ArrayValue(
          List(NodeValue.NumberValue(BigDecimal(1)), NodeValue.NumberValue(BigDecimal(2)))
        )
        val outer = NodeValue.ArrayValue(List(inner1, inner2))
        val arr   = outer.asInstanceOf[NodeValue.ArrayValue]
        assertTrue(
          arr.values.length == 2,
          arr.values(0).isInstanceOf[NodeValue.ArrayValue],
          arr.values(1).isInstanceOf[NodeValue.ArrayValue]
        )
      }
    ),
    suite("ObjectValue")(
      test("creates empty object") {
        val value = NodeValue.ObjectValue(List.empty)
        assertTrue(value.asInstanceOf[NodeValue.ObjectValue].fields.isEmpty)
      },
      test("creates object with single field") {
        val fields = List(("name", NodeValue.StringValue("Alice")))
        val obj    = NodeValue.ObjectValue(fields)
        val o      = obj.asInstanceOf[NodeValue.ObjectValue]
        assertTrue(
          o.fields.length == 1,
          o.fields(0)._1 == "name",
          o.fields(0)._2.asInstanceOf[NodeValue.StringValue].value == "Alice"
        )
      },
      test("creates object with multiple fields") {
        val fields = List(
          ("name", NodeValue.StringValue("Alice")),
          ("age", NodeValue.NumberValue(BigDecimal(30))),
          ("active", NodeValue.BooleanValue(true))
        )
        val obj = NodeValue.ObjectValue(fields)
        val o   = obj.asInstanceOf[NodeValue.ObjectValue]
        assertTrue(
          o.fields.length == 3,
          o.fields(0)._1 == "name",
          o.fields(1)._1 == "age",
          o.fields(2)._1 == "active"
        )
      },
      test("creates object with nested object") {
        val inner = NodeValue.ObjectValue(
          List(("street", NodeValue.StringValue("123 Main St")))
        )
        val fields = List(
          ("address", inner)
        )
        val obj = NodeValue.ObjectValue(fields)
        val o   = obj.asInstanceOf[NodeValue.ObjectValue]
        assertTrue(
          o.fields.length == 1,
          o.fields(0)._1 == "address",
          o.fields(0)._2.isInstanceOf[NodeValue.ObjectValue]
        )
      },
      test("creates object with array field") {
        val array = NodeValue.ArrayValue(
          List(NodeValue.StringValue("tag1"), NodeValue.StringValue("tag2"))
        )
        val fields = List(("tags", array))
        val obj    = NodeValue.ObjectValue(fields)
        val o      = obj.asInstanceOf[NodeValue.ObjectValue]
        assertTrue(
          o.fields.length == 1,
          o.fields(0)._1 == "tags",
          o.fields(0)._2.isInstanceOf[NodeValue.ArrayValue]
        )
      },
      test("creates complex nested structure") {
        val address = NodeValue.ObjectValue(
          List(
            ("street", NodeValue.StringValue("123 Main St")),
            ("city", NodeValue.StringValue("Springfield"))
          )
        )
        val tags = NodeValue.ArrayValue(
          List(NodeValue.StringValue("developer"), NodeValue.StringValue("scala"))
        )
        val person = NodeValue.ObjectValue(
          List(
            ("name", NodeValue.StringValue("Alice")),
            ("age", NodeValue.NumberValue(BigDecimal(30))),
            ("address", address),
            ("tags", tags),
            ("active", NodeValue.BooleanValue(true))
          )
        )
        val p = person.asInstanceOf[NodeValue.ObjectValue]
        assertTrue(
          p.fields.length == 5,
          p.fields(0)._1 == "name",
          p.fields(2)._2.isInstanceOf[NodeValue.ObjectValue],
          p.fields(3)._2.isInstanceOf[NodeValue.ArrayValue]
        )
      }
    )
  )
}
