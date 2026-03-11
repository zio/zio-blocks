package zio.blocks.schema.yaml

import zio.test._

object YamlContextDetectorSpec extends YamlBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("YamlContextDetector")(
    suite("detectContexts")(
      test("empty parts returns empty") {
        val result = YamlContextDetector.detectContexts(Seq.empty)
        assertTrue(result == Right(Nil))
      },
      test("single part returns empty") {
        val result = YamlContextDetector.detectContexts(Seq("name: value"))
        assertTrue(result == Right(Nil))
      },
      test("value context after colon") {
        val result = YamlContextDetector.detectContexts(Seq("key: ", ""))
        assertTrue(
          result.isRight && result.toOption.get.length == 1 &&
            result.toOption.get.head == YamlInterpolationContext.Value
        )
      },
      test("key context (no colon)") {
        val result = YamlContextDetector.detectContexts(Seq("", ": value"))
        assertTrue(
          result.isRight && result.toOption.get.length == 1 &&
            result.toOption.get.head == YamlInterpolationContext.Key
        )
      },
      test("in-string context (inside single quotes)") {
        val result = YamlContextDetector.detectContexts(Seq("key: 'hello ", "'"))
        assertTrue(
          result.isRight && result.toOption.get.length == 1 &&
            result.toOption.get.head == YamlInterpolationContext.InString
        )
      },
      test("in-string context (inside double quotes)") {
        val result = YamlContextDetector.detectContexts(Seq("key: \"hello ", "\""))
        assertTrue(
          result.isRight && result.toOption.get.length == 1 &&
            result.toOption.get.head == YamlInterpolationContext.InString
        )
      },
      test("value context after dash") {
        val result = YamlContextDetector.detectContexts(Seq("- ", ""))
        assertTrue(
          result.isRight && result.toOption.get.length == 1 &&
            result.toOption.get.head == YamlInterpolationContext.Value
        )
      },
      test("value context in flow sequence after [") {
        val result = YamlContextDetector.detectContexts(Seq("[", "]"))
        assertTrue(
          result.isRight && result.toOption.get.length == 1 &&
            result.toOption.get.head == YamlInterpolationContext.Value
        )
      },
      test("key context in flow mapping after {") {
        val result = YamlContextDetector.detectContexts(Seq("{", ": v}"))
        assertTrue(
          result.isRight && result.toOption.get.length == 1 &&
            result.toOption.get.head == YamlInterpolationContext.Key
        )
      },
      test("value context after comma in flow sequence") {
        val result = YamlContextDetector.detectContexts(Seq("[a, ", "]"))
        assertTrue(
          result.isRight && result.toOption.get.length == 1 &&
            result.toOption.get.head == YamlInterpolationContext.Value
        )
      },
      test("key context after comma in flow mapping") {
        val result = YamlContextDetector.detectContexts(Seq("{a: 1, ", ": 2}"))
        assertTrue(
          result.isRight && result.toOption.get.length == 1 &&
            result.toOption.get.head == YamlInterpolationContext.Key
        )
      },
      test("multiple contexts") {
        val result = YamlContextDetector.detectContexts(Seq("key: ", "\nother: ", ""))
        assertTrue(
          result.isRight && result.toOption.get.length == 2
        )
      },
      test("escaped double quote does not open string") {
        val result = YamlContextDetector.detectContexts(Seq("key: \\\"", ""))
        assertTrue(result.isRight)
      },
      test("value context for char after other chars at end") {
        val result = YamlContextDetector.detectContexts(Seq("key: value ", ""))
        assertTrue(
          result.isRight && result.toOption.get.head == YamlInterpolationContext.Value
        )
      },
      test("comma with nested brackets looks past depth") {
        val result = YamlContextDetector.detectContexts(Seq("[a, [b], ", "]"))
        assertTrue(
          result.isRight && result.toOption.get.head == YamlInterpolationContext.Value
        )
      },
      test("comma after nested } looks past depth") {
        val result = YamlContextDetector.detectContexts(Seq("{a: {b: 1}}, ", ""))
        assertTrue(result.isRight)
      },
      test("whitespace-only ending defaults to key context") {
        val result = YamlContextDetector.detectContexts(Seq("   ", ""))
        assertTrue(
          result.isRight && result.toOption.get.head == YamlInterpolationContext.Key
        )
      },
      test("tab character counts as whitespace") {
        val result = YamlContextDetector.detectContexts(Seq("key:\t", ""))
        assertTrue(
          result.isRight && result.toOption.get.head == YamlInterpolationContext.Value
        )
      }
    )
  )
}
