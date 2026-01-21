package zio.blocks.schema.into.evolution

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for schema evolution with type refinement. */
object TypeRefinementSpec extends ZIOSpecDefault {

  case class CounterV1(count: Int)
  case class CounterV2(count: Long)

  case class MetricsV1(min: Int, max: Int)
  case class MetricsV2(min: Long, max: Long)

  def spec: Spec[TestEnvironment, Any] = suite("TypeRefinementSpec")(
    test("widens Int to Long field") {
      val result = Into.derived[CounterV1, CounterV2].into(CounterV1(42))
      assert(result)(isRight(equalTo(CounterV2(42L))))
    },
    test("narrows Long to Int when value fits") {
      val result = Into.derived[CounterV2, CounterV1].into(CounterV2(42L))
      assert(result)(isRight(equalTo(CounterV1(42))))
    },
    test("fails when Long value overflows Int") {
      val result = Into.derived[CounterV2, CounterV1].into(CounterV2(Long.MaxValue))
      assert(result)(isLeft)
    },
    test("widens multiple fields") {
      val result = Into.derived[MetricsV1, MetricsV2].into(MetricsV1(1, 100))
      assert(result)(isRight(equalTo(MetricsV2(1L, 100L))))
    }
  )
}
