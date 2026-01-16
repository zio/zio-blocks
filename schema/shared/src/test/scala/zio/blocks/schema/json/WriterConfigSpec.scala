package zio.blocks.schema.json

import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.json.JsonTestUtils._
import zio.test._
import zio.test.Assertion._
import scala.util.Try

object WriterConfigSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("WriterConfigSpec")(
    test("have safe and handy defaults") {
      assert(WriterConfig.indentionStep)(equalTo(0)) &&
      assert(WriterConfig.escapeUnicode)(equalTo(false)) &&
      assert(WriterConfig.preferredBufSize)(equalTo(32768))
    },
    test("allow to set values") {
      assert(WriterConfig.withIndentionStep(2).indentionStep)(equalTo(2)) &&
      assert(WriterConfig.withEscapeUnicode(true).escapeUnicode)(equalTo(true)) &&
      assert(WriterConfig.withPreferredBufSize(12).preferredBufSize)(equalTo(12))
    },
    test("throw exception in case for unsupported values of params") {
      assert(Try(WriterConfig.withIndentionStep(-1)).toEither)(
        isLeft(hasError("'indentionStep' should be not less than 0"))
      ) &&
      assert(Try(WriterConfig.withPreferredBufSize(0)).toEither)(
        isLeft(hasError("'preferredBufSize' should be not less than 1"))
      )
    }
  )
}
