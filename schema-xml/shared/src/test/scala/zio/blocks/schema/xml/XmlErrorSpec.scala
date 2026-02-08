package zio.blocks.schema.xml

import zio.blocks.schema.{DynamicOptic, SchemaBaseSpec}
import zio.test._

object XmlErrorSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("XmlErrorSpec")(
    suite("XmlError")(
      test("creates error with message") {
        val error = XmlError("test error")
        assertTrue(
          error.getMessage == "test error",
          error.spans.isEmpty
        )
      },
      test("creates error with message and spans") {
        val spans = List(DynamicOptic.Node.Field("foo"))
        val error = XmlError("test error", spans)
        assertTrue(
          error.getMessage == "test error",
          error.spans == spans
        )
      },
      test("atSpan prepends span") {
        val error    = XmlError("test error")
        val withSpan = error.atSpan(DynamicOptic.Node.Field("bar"))
        assertTrue(
          withSpan.spans == List(DynamicOptic.Node.Field("bar"))
        )
      },
      test("path returns DynamicOptic with reversed spans") {
        val error = XmlError(
          "test",
          List(
            DynamicOptic.Node.Field("a"),
            DynamicOptic.Node.Field("b")
          )
        )
        val path = error.path
        assertTrue(
          path.nodes.length == 2,
          path.nodes(0) == DynamicOptic.Node.Field("b"),
          path.nodes(1) == DynamicOptic.Node.Field("a")
        )
      }
    ),
    suite("factory methods")(
      test("parseError includes line and column") {
        val error = XmlError.parseError("unexpected char", 10, 5)
        assertTrue(error.getMessage.contains("line 10") && error.getMessage.contains("column 5"))
      },
      test("validationError includes prefix") {
        val error = XmlError.validationError("invalid value")
        assertTrue(error.getMessage.startsWith("Validation error:"))
      },
      test("encodingError includes prefix") {
        val error = XmlError.encodingError("cannot encode")
        assertTrue(error.getMessage.startsWith("Encoding error:"))
      },
      test("selectionError includes prefix") {
        val error = XmlError.selectionError("path not found")
        assertTrue(error.getMessage.startsWith("Selection error:"))
      },
      test("patchError includes prefix") {
        val error = XmlError.patchError("operation failed")
        assertTrue(error.getMessage.startsWith("Patch error:"))
      }
    )
  )
}
