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
      },
      test("atSpan returns new instance without modifying original") {
        val error    = XmlError("test error", List(DynamicOptic.Node.Field("a")))
        val withSpan = error.atSpan(DynamicOptic.Node.Field("b"))
        assertTrue(
          error.spans == List(DynamicOptic.Node.Field("a")),
          withSpan.spans == List(DynamicOptic.Node.Field("b"), DynamicOptic.Node.Field("a")),
          error ne withSpan
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
    ),
    suite("XmlName")(
      test("withPrefix adds prefix to name") {
        val name       = XmlName("local")
        val withPrefix = name.withPrefix("ns")
        assertTrue(
          withPrefix.prefix.contains("ns"),
          withPrefix.localName == "local",
          name.prefix.isEmpty
        )
      },
      test("withoutPrefix removes prefix") {
        val name          = XmlName("local", Some("ns"), None)
        val withoutPrefix = name.withoutPrefix
        assertTrue(
          withoutPrefix.prefix.isEmpty,
          withoutPrefix.localName == "local",
          name.prefix.contains("ns")
        )
      },
      test("qualifiedName concatenates prefix and local name") {
        val withPrefix = XmlName("local", Some("ns"), None)
        assertTrue(withPrefix.qualifiedName == "ns:local")
      },
      test("qualifiedName returns local name when no prefix") {
        val noPrefix = XmlName("local")
        assertTrue(noPrefix.qualifiedName == "local")
      },
      test("parse handles namespace and prefix together") {
        val parsed = XmlName.parse("{http://example.com}ns:local")
        assertTrue(
          parsed.namespace.contains("http://example.com"),
          parsed.prefix.contains("ns"),
          parsed.localName == "local"
        )
      },
      test("parse handles namespace only") {
        val parsed = XmlName.parse("{http://example.com}local")
        assertTrue(
          parsed.namespace.contains("http://example.com"),
          parsed.prefix.isEmpty,
          parsed.localName == "local"
        )
      },
      test("parse handles prefix only") {
        val parsed = XmlName.parse("ns:local")
        assertTrue(
          parsed.prefix.contains("ns"),
          parsed.namespace.isEmpty,
          parsed.localName == "local"
        )
      },
      test("parse handles local name only") {
        val parsed = XmlName.parse("local")
        assertTrue(
          parsed.prefix.isEmpty,
          parsed.namespace.isEmpty,
          parsed.localName == "local"
        )
      },
      test("parse handles malformed namespace (no closing brace)") {
        val parsed = XmlName.parse("{http://example.comlocal")
        assertTrue(
          parsed.prefix.isEmpty,
          parsed.namespace.isEmpty,
          parsed.localName == "{http://example.comlocal"
        )
      }
    )
  )
}
