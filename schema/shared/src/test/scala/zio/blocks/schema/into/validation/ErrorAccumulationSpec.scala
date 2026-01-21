package zio.blocks.schema.into.validation

import zio.blocks.schema.Into
import zio.test._

/** Tests for error accumulation in Into conversions. */
object ErrorAccumulationSpec extends ZIOSpecDefault {

  case class SourceLongs(a: Long, b: Long, c: Long)
  case class TargetInts(a: Int, b: Int, c: Int)

  def spec: Spec[TestEnvironment, Any] = suite("ErrorAccumulationSpec")(
    test("accumulates errors from multiple fields that fail narrowing") {
      val source = SourceLongs(a = Long.MaxValue, b = Long.MinValue, c = 100L)
      val result = Into.derived[SourceLongs, TargetInts].into(source)

      assertTrue(
        result.isLeft,
        result.left.exists(_.errors.size == 2)
      )
    },
    test("returns Right when all fields succeed") {
      val source = SourceLongs(a = 1L, b = 2L, c = 3L)
      val result = Into.derived[SourceLongs, TargetInts].into(source)
      assertTrue(result == Right(TargetInts(1, 2, 3)))
    },
    test("accumulates all errors when all fields fail") {
      val source = SourceLongs(Long.MaxValue, Long.MinValue, Long.MaxValue)
      val result = Into.derived[SourceLongs, TargetInts].into(source)

      assertTrue(
        result.isLeft,
        result.left.exists(_.errors.size == 3)
      )
    }
  )
}
