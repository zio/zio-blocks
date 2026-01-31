package zio.blocks.schema

import zio.test._

/**
 * Tests for SchemaError's path manipulation methods (atField, atIndex, atKey,
 * atCase). These methods were previously on DynamicValueError and JsonError but
 * are now unified in SchemaError.
 */
object SchemaErrorPathSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("SchemaErrorPathSpec")(
    suite("apply(message)")(
      test("creates error with root path") {
        val error = SchemaError("test error")
        assertTrue(error.message == "test error") &&
        assertTrue(error.errors.head.source == DynamicOptic.root) &&
        assertTrue(error.errors.head.source.nodes.isEmpty)
      }
    ),
    suite("message formatting")(
      test("returns just message when path is empty (root)") {
        val error = SchemaError("test error")
        assertTrue(error.message == "test error")
      },
      test("returns message with path when path is not empty") {
        val error = SchemaError("test error").atField("name")
        assertTrue(error.message == "test error at: .name")
      },
      test("returns message with complex path") {
        val error = SchemaError("test error")
          .atField("value")
          .atIndex(0)
          .atField("items")
        assertTrue(error.message == "test error at: .items[0].value")
      }
    ),
    suite("getMessage")(
      test("equals message for root path") {
        val error = SchemaError("test error")
        assertTrue(error.getMessage == error.message)
      },
      test("equals message for non-empty path") {
        val error = SchemaError("test error").atField("name")
        assertTrue(error.getMessage == error.message) &&
        assertTrue(error.getMessage == "test error at: .name")
      }
    ),
    suite("++")(
      test("aggregates errors from both sides") {
        val error1   = SchemaError("first error")
        val error2   = SchemaError("second error")
        val combined = error1 ++ error2
        assertTrue(combined.errors.length == 2) &&
        assertTrue(combined.errors.head.message == "first error") &&
        assertTrue(combined.errors.tail.head.message == "second error")
      },
      test("preserves paths of all errors") {
        val error1   = SchemaError("first error").atField("field1")
        val error2   = SchemaError("second error").atField("field2")
        val combined = error1 ++ error2
        assertTrue(combined.errors.length == 2) &&
        assertTrue(combined.errors.head.message.contains("field1")) &&
        assertTrue(combined.errors.tail.head.message.contains("field2"))
      },
      test("combines multiple errors") {
        val error1   = SchemaError("error1")
        val error2   = SchemaError("error2")
        val error3   = SchemaError("error3")
        val combined = error1 ++ error2 ++ error3
        assertTrue(combined.errors.length == 3)
      },
      test("message contains all individual error messages") {
        val error1   = SchemaError("first error")
        val error2   = SchemaError("second error")
        val combined = error1 ++ error2
        assertTrue(combined.message.contains("first error")) &&
        assertTrue(combined.message.contains("second error"))
      }
    ),
    suite("atField")(
      test("prepends field access to empty path") {
        val error  = SchemaError("test error").atField("name")
        val source = error.errors.head.source
        assertTrue(source.nodes.size == 1) &&
        assertTrue(source.nodes.head == DynamicOptic.Node.Field("name"))
      },
      test("prepends field access to existing path") {
        val error = SchemaError("test error")
          .atField("inner")
          .atField("outer")
        val source = error.errors.head.source
        assertTrue(source.nodes.size == 2) &&
        assertTrue(source.nodes(0) == DynamicOptic.Node.Field("outer")) &&
        assertTrue(source.nodes(1) == DynamicOptic.Node.Field("inner"))
      },
      test("renders correctly in message") {
        val error = SchemaError("test error").atField("fieldName")
        assertTrue(error.message == "test error at: .fieldName")
      }
    ),
    suite("atIndex")(
      test("prepends index access to empty path") {
        val error  = SchemaError("test error").atIndex(5)
        val source = error.errors.head.source
        assertTrue(source.nodes.size == 1) &&
        assertTrue(source.nodes.head == DynamicOptic.Node.AtIndex(5))
      },
      test("prepends index access to existing path") {
        val error = SchemaError("test error")
          .atField("value")
          .atIndex(3)
        val source = error.errors.head.source
        assertTrue(source.nodes.size == 2) &&
        assertTrue(source.nodes(0) == DynamicOptic.Node.AtIndex(3)) &&
        assertTrue(source.nodes(1) == DynamicOptic.Node.Field("value"))
      },
      test("renders correctly in message") {
        val error = SchemaError("test error").atIndex(42)
        assertTrue(error.message == "test error at: [42]")
      }
    ),
    suite("atKey")(
      test("prepends map key access to empty path") {
        val key    = DynamicValue.Primitive(PrimitiveValue.String("myKey"))
        val error  = SchemaError("test error").atKey(key)
        val source = error.errors.head.source
        assertTrue(source.nodes.size == 1) &&
        assertTrue(source.nodes.head == DynamicOptic.Node.AtMapKey(key))
      },
      test("prepends map key access to existing path") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("key"))
        val error = SchemaError("test error")
          .atField("value")
          .atKey(key)
        val source = error.errors.head.source
        assertTrue(source.nodes.size == 2) &&
        assertTrue(source.nodes(0) == DynamicOptic.Node.AtMapKey(key)) &&
        assertTrue(source.nodes(1) == DynamicOptic.Node.Field("value"))
      },
      test("renders correctly in message") {
        val key   = DynamicValue.Primitive(PrimitiveValue.Int(123))
        val error = SchemaError("test error").atKey(key)
        assertTrue(error.message == "test error at: {123}")
      }
    ),
    suite("atCase")(
      test("prepends case access to empty path") {
        val error  = SchemaError("test error").atCase("SomeCase")
        val source = error.errors.head.source
        assertTrue(source.nodes.size == 1) &&
        assertTrue(source.nodes.head == DynamicOptic.Node.Case("SomeCase"))
      },
      test("prepends case access to existing path") {
        val error = SchemaError("test error")
          .atField("inner")
          .atCase("OuterCase")
        val source = error.errors.head.source
        assertTrue(source.nodes.size == 2) &&
        assertTrue(source.nodes(0) == DynamicOptic.Node.Case("OuterCase")) &&
        assertTrue(source.nodes(1) == DynamicOptic.Node.Field("inner"))
      },
      test("renders correctly in message") {
        val error = SchemaError("test error").atCase("MyVariant")
        assertTrue(error.message == "test error at: <MyVariant>")
      }
    ),
    suite("complex path construction")(
      test("supports all path types in combination") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("mapKey"))
        val error = SchemaError("validation failed")
          .atField("deepValue")
          .atIndex(0)
          .atKey(key)
          .atCase("SomeCase")
          .atField("items")
        val source = error.errors.head.source
        assertTrue(source.nodes.size == 5) &&
        assertTrue(source.nodes(0) == DynamicOptic.Node.Field("items")) &&
        assertTrue(source.nodes(1) == DynamicOptic.Node.Case("SomeCase")) &&
        assertTrue(source.nodes(2) == DynamicOptic.Node.AtMapKey(key)) &&
        assertTrue(source.nodes(3) == DynamicOptic.Node.AtIndex(0)) &&
        assertTrue(source.nodes(4) == DynamicOptic.Node.Field("deepValue"))
      },
      test("renders complex path correctly") {
        val error = SchemaError("error")
          .atField("value")
          .atIndex(2)
          .atField("list")
          .atCase("Right")
        assertTrue(error.message == "error at: <Right>.list[2].value")
      }
    ),
    suite("exception behavior")(
      test("is a valid Exception") {
        val error = SchemaError("test error")
        assertTrue(error.isInstanceOf[Exception])
      },
      test("getMessage returns the expected string") {
        val error = SchemaError("test error").atField("field")
        assertTrue(error.getMessage == "test error at: .field")
      }
    ),
    suite("atField/atIndex/atKey/atCase apply to all errors in aggregation")(
      test("atField applies to all aggregated errors") {
        val error1   = SchemaError("error1")
        val error2   = SchemaError("error2")
        val combined = (error1 ++ error2).atField("wrapper")
        assertTrue(combined.errors.length == 2) &&
        assertTrue(combined.errors.head.message.contains("wrapper")) &&
        assertTrue(combined.errors.tail.head.message.contains("wrapper"))
      }
    )
  )
}
