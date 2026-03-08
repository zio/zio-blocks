package zio.blocks.schema.yaml

import zio.test._

object YamlOptionsSpec extends YamlBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("YamlOptions")(
    test("default options") {
      val opts = YamlOptions.default
      assertTrue(
        opts.indentStep == 2 &&
          opts.flowStyle == false &&
          opts.documentMarkers == false
      )
    },
    test("pretty options") {
      val opts = YamlOptions.pretty
      assertTrue(
        opts.indentStep == 2 &&
          opts.documentMarkers == true
      )
    },
    test("flow options") {
      val opts = YamlOptions.flow
      assertTrue(opts.flowStyle == true)
    },
    test("custom indent step") {
      val opts = YamlOptions(indentStep = 4)
      assertTrue(opts.indentStep == 4)
    },
    test("custom options") {
      val opts = YamlOptions(indentStep = 3, flowStyle = true, documentMarkers = true)
      assertTrue(
        opts.indentStep == 3 &&
          opts.flowStyle == true &&
          opts.documentMarkers == true
      )
    }
  )
}
