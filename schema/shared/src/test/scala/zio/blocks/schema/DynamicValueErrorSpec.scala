package zio.blocks.schema

import zio.test._

object DynamicValueErrorSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicValueErrorSpec")(
    suite("apply(message)")(
      test("creates error with root path") {
        val error = DynamicValueError("test error")
        assertTrue(error.message == "test error") &&
        assertTrue(error.path == DynamicOptic.root) &&
        assertTrue(error.path.nodes.isEmpty)
      }
    ),
    suite("toString")(
      test("returns just message when path is empty (root)") {
        val error = DynamicValueError("test error")
        assertTrue(error.toString == "test error")
      },
      test("returns message with path when path is not empty") {
        val error = DynamicValueError("test error").atField("name")
        assertTrue(error.toString == "test error at: .name")
      },
      test("returns message with complex path") {
        val error = DynamicValueError("test error")
          .atField("value")
          .atIndex(0)
          .atField("items")
        assertTrue(error.toString == "test error at: .items[0].value")
      }
    ),
    suite("getMessage")(
      test("equals toString for root path") {
        val error = DynamicValueError("test error")
        assertTrue(error.getMessage == error.toString)
      },
      test("equals toString for non-empty path") {
        val error = DynamicValueError("test error").atField("name")
        assertTrue(error.getMessage == error.toString) &&
        assertTrue(error.getMessage == "test error at: .name")
      }
    ),
    suite("++")(
      test("combines two error messages") {
        val error1   = DynamicValueError("first error")
        val error2   = DynamicValueError("second error")
        val combined = error1 ++ error2
        assertTrue(combined.message == "first error; second error")
      },
      test("preserves the path of the first error") {
        val error1   = DynamicValueError("first error").atField("field1")
        val error2   = DynamicValueError("second error").atField("field2")
        val combined = error1 ++ error2
        assertTrue(combined.path == error1.path)
      },
      test("combines multiple errors") {
        val error1   = DynamicValueError("error1")
        val error2   = DynamicValueError("error2")
        val error3   = DynamicValueError("error3")
        val combined = error1 ++ error2 ++ error3
        assertTrue(combined.message == "error1; error2; error3")
      }
    ),
    suite("atField")(
      test("prepends field access to empty path") {
        val error = DynamicValueError("test error").atField("name")
        assertTrue(error.path.nodes.size == 1) &&
        assertTrue(error.path.nodes.head == DynamicOptic.Node.Field("name"))
      },
      test("prepends field access to existing path") {
        val error = DynamicValueError("test error")
          .atField("inner")
          .atField("outer")
        assertTrue(error.path.nodes.size == 2) &&
        assertTrue(error.path.nodes(0) == DynamicOptic.Node.Field("outer")) &&
        assertTrue(error.path.nodes(1) == DynamicOptic.Node.Field("inner"))
      },
      test("renders correctly in toString") {
        val error = DynamicValueError("test error").atField("fieldName")
        assertTrue(error.toString == "test error at: .fieldName")
      }
    ),
    suite("atIndex")(
      test("prepends index access to empty path") {
        val error = DynamicValueError("test error").atIndex(5)
        assertTrue(error.path.nodes.size == 1) &&
        assertTrue(error.path.nodes.head == DynamicOptic.Node.AtIndex(5))
      },
      test("prepends index access to existing path") {
        val error = DynamicValueError("test error")
          .atField("value")
          .atIndex(3)
        assertTrue(error.path.nodes.size == 2) &&
        assertTrue(error.path.nodes(0) == DynamicOptic.Node.AtIndex(3)) &&
        assertTrue(error.path.nodes(1) == DynamicOptic.Node.Field("value"))
      },
      test("renders correctly in toString") {
        val error = DynamicValueError("test error").atIndex(42)
        assertTrue(error.toString == "test error at: [42]")
      }
    ),
    suite("atKey")(
      test("prepends map key access to empty path") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("myKey"))
        val error = DynamicValueError("test error").atKey(key)
        assertTrue(error.path.nodes.size == 1) &&
        assertTrue(error.path.nodes.head == DynamicOptic.Node.AtMapKey(key))
      },
      test("prepends map key access to existing path") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("key"))
        val error = DynamicValueError("test error")
          .atField("value")
          .atKey(key)
        assertTrue(error.path.nodes.size == 2) &&
        assertTrue(error.path.nodes(0) == DynamicOptic.Node.AtMapKey(key)) &&
        assertTrue(error.path.nodes(1) == DynamicOptic.Node.Field("value"))
      },
      test("renders correctly in toString") {
        val key   = DynamicValue.Primitive(PrimitiveValue.Int(123))
        val error = DynamicValueError("test error").atKey(key)
        assertTrue(error.toString == "test error at: {123}")
      }
    ),
    suite("atCase")(
      test("prepends case access to empty path") {
        val error = DynamicValueError("test error").atCase("SomeCase")
        assertTrue(error.path.nodes.size == 1) &&
        assertTrue(error.path.nodes.head == DynamicOptic.Node.Case("SomeCase"))
      },
      test("prepends case access to existing path") {
        val error = DynamicValueError("test error")
          .atField("inner")
          .atCase("OuterCase")
        assertTrue(error.path.nodes.size == 2) &&
        assertTrue(error.path.nodes(0) == DynamicOptic.Node.Case("OuterCase")) &&
        assertTrue(error.path.nodes(1) == DynamicOptic.Node.Field("inner"))
      },
      test("renders correctly in toString") {
        val error = DynamicValueError("test error").atCase("MyVariant")
        assertTrue(error.toString == "test error at: <MyVariant>")
      }
    ),
    suite("complex path construction")(
      test("supports all path types in combination") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("mapKey"))
        val error = DynamicValueError("validation failed")
          .atField("deepValue")
          .atIndex(0)
          .atKey(key)
          .atCase("SomeCase")
          .atField("items")
        assertTrue(error.path.nodes.size == 5) &&
        assertTrue(error.path.nodes(0) == DynamicOptic.Node.Field("items")) &&
        assertTrue(error.path.nodes(1) == DynamicOptic.Node.Case("SomeCase")) &&
        assertTrue(error.path.nodes(2) == DynamicOptic.Node.AtMapKey(key)) &&
        assertTrue(error.path.nodes(3) == DynamicOptic.Node.AtIndex(0)) &&
        assertTrue(error.path.nodes(4) == DynamicOptic.Node.Field("deepValue"))
      },
      test("renders complex path correctly") {
        val error = DynamicValueError("error")
          .atField("value")
          .atIndex(2)
          .atField("list")
          .atCase("Right")
        assertTrue(error.toString == "error at: <Right>.list[2].value")
      }
    ),
    suite("exception behavior")(
      test("is a valid Exception") {
        val error = DynamicValueError("test error")
        assertTrue(error.isInstanceOf[Exception])
      },
      test("getMessage returns the expected string") {
        val error = DynamicValueError("test error").atField("field")
        assertTrue(error.getMessage == "test error at: .field")
      }
    )
  )
}
