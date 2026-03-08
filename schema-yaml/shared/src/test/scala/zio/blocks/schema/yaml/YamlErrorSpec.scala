package zio.blocks.schema.yaml

import zio.blocks.schema.DynamicOptic
import zio.test._

object YamlErrorSpec extends YamlBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("YamlError")(
    test("message with line and column") {
      val err = YamlError("test error", 5, 10)
      assertTrue(err.getMessage == "test error (at line 5, column 10)")
    },
    test("message with line only") {
      val err = new YamlError(Nil, "test error", line = Some(5))
      assertTrue(err.getMessage == "test error (at line 5)")
    },
    test("message without position") {
      val err = YamlError("test error")
      assertTrue(err.getMessage == "test error")
    },
    test("parseError") {
      val err = YamlError.parseError("bad syntax", 1, 2)
      assertTrue(err.getMessage.contains("Parse error"))
    },
    test("validationError") {
      val err = YamlError.validationError("bad value")
      assertTrue(err.getMessage.contains("Validation error"))
    },
    test("encodingError") {
      val err = YamlError.encodingError("can't encode")
      assertTrue(err.getMessage.contains("Encoding error"))
    },
    test("apply with spans") {
      val err = YamlError("test", List(DynamicOptic.Node.Field("field1")))
      assertTrue(err.spans.length == 1)
    },
    test("path returns DynamicOptic") {
      val err   = YamlError("test", List(DynamicOptic.Node.Field("a"), DynamicOptic.Node.Field("b")))
      val optic = err.path
      assertTrue(optic.nodes.length == 2)
    },
    test("atSpan adds span") {
      val err  = YamlError("test")
      val err2 = err.atSpan(DynamicOptic.Node.Field("f"))
      assertTrue(err2.spans.length == 1)
    }
  )
}
