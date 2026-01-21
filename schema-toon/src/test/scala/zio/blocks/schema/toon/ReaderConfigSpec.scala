package zio.blocks.schema.toon

import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.toon.ToonTestUtils._
import zio.test._
import zio.test.Assertion._
import scala.util.Try

object ReaderConfigSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("ReaderConfigSpec")(
    test("have safe and handy defaults") {
      assert(ReaderConfig.indent)(equalTo(2)) &&
      assert(ReaderConfig.delimiter)(equalTo(Delimiter.Comma)) &&
      assert(ReaderConfig.strict)(equalTo(true)) &&
      assert(ReaderConfig.expandPaths)(equalTo(PathExpansion.Off)) &&
      assert(ReaderConfig.discriminatorField)(equalTo(None))
    },
    test("allow to set values") {
      assert(ReaderConfig.withIndent(3).indent)(equalTo(3)) &&
      assert(ReaderConfig.withDelimiter(Delimiter.Pipe).delimiter)(equalTo(Delimiter.Pipe)) &&
      assert(ReaderConfig.withStrict(false).strict)(equalTo(false)) &&
      assert(ReaderConfig.withExpandPaths(PathExpansion.Safe).expandPaths)(equalTo(PathExpansion.Safe)) &&
      assert(ReaderConfig.withDiscriminatorField(Some("type")).discriminatorField)(isSome(equalTo("type")))
    },
    test("throw exception in case for unsupported values of params") {
      assert(Try(ReaderConfig.withIndent(-1)).toEither)(
        isLeft(hasError("'indent' should be not less than 0"))
      )
    }
  )
}
