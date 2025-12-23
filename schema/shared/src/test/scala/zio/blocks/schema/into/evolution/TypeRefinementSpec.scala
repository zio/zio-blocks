package zio.blocks.schema.into.evolution

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for schema evolution with type refinement.
 *
 * Covers:
 *   - Widening types (Int → Long) in schema evolution
 *   - Narrowing types (Long → Int) with validation
 *   - Collection element type changes
 *   - Multiple field type refinements
 */
object TypeRefinementSpec extends ZIOSpecDefault {

  // === Test types for type refinement ===

  // Basic widening
  case class CounterV1(count: Int)
  case class CounterV2(count: Long)

  // Basic narrowing
  case class BigCounterV2(count: Long)
  case class BigCounterV1(count: Int)

  // Multiple field widening
  case class MetricsV1(min: Int, max: Int, avg: Float)
  case class MetricsV2(min: Long, max: Long, avg: Double)

  // Mixed widening and narrowing
  case class StatsV1(smallValue: Int, bigValue: Long)
  case class StatsV2(smallValue: Long, bigValue: Int)

  // Collection element type refinement
  case class NumbersV1(values: List[Int])
  case class NumbersV2(values: List[Long])

  // Nested type refinement
  case class InnerV1(value: Int)
  case class InnerV2(value: Long)
  case class OuterV1(inner: InnerV1, name: String)
  case class OuterV2(inner: InnerV2, name: String)

  // Optional field type refinement
  case class OptionalCountV1(count: Option[Int])
  case class OptionalCountV2(count: Option[Long])

  def spec: Spec[TestEnvironment, Any] = suite("TypeRefinementSpec")(
    suite("Basic Widening (Int → Long)")(
      test("widens Int to Long field") {
        val source = CounterV1(42)
        val result = Into.derived[CounterV1, CounterV2].into(source)

        assert(result)(isRight(equalTo(CounterV2(42L))))
      },
      test("widens Int.MaxValue to Long") {
        val source = CounterV1(Int.MaxValue)
        val result = Into.derived[CounterV1, CounterV2].into(source)

        assert(result)(isRight(equalTo(CounterV2(Int.MaxValue.toLong))))
      },
      test("widens Int.MinValue to Long") {
        val source = CounterV1(Int.MinValue)
        val result = Into.derived[CounterV1, CounterV2].into(source)

        assert(result)(isRight(equalTo(CounterV2(Int.MinValue.toLong))))
      }
    ),
    suite("Basic Narrowing (Long → Int)")(
      test("narrows Long to Int when value fits") {
        val source = BigCounterV2(42L)
        val result = Into.derived[BigCounterV2, BigCounterV1].into(source)

        assert(result)(isRight(equalTo(BigCounterV1(42))))
      },
      test("fails when Long value overflows Int") {
        val source = BigCounterV2(Long.MaxValue)
        val result = Into.derived[BigCounterV2, BigCounterV1].into(source)

        assert(result)(isLeft)
      },
      test("fails when Long value underflows Int") {
        val source = BigCounterV2(Long.MinValue)
        val result = Into.derived[BigCounterV2, BigCounterV1].into(source)

        assert(result)(isLeft)
      },
      test("narrows at Int boundary values") {
        val sourceMax = BigCounterV2(Int.MaxValue.toLong)
        val sourceMin = BigCounterV2(Int.MinValue.toLong)

        val resultMax = Into.derived[BigCounterV2, BigCounterV1].into(sourceMax)
        val resultMin = Into.derived[BigCounterV2, BigCounterV1].into(sourceMin)

        assert(resultMax)(isRight(equalTo(BigCounterV1(Int.MaxValue)))) &&
        assert(resultMin)(isRight(equalTo(BigCounterV1(Int.MinValue))))
      }
    ),
    suite("Multiple Field Widening")(
      test("widens all fields from Int/Float to Long/Double") {
        val source = MetricsV1(1, 100, 50.5f)
        val result = Into.derived[MetricsV1, MetricsV2].into(source)

        assert(result)(isRight(equalTo(MetricsV2(1L, 100L, 50.5f.toDouble))))
      }
    ),
    suite("Mixed Widening and Narrowing")(
      test("widens one field while narrowing another when values fit") {
        val source = StatsV1(10, 20L)
        val result = Into.derived[StatsV1, StatsV2].into(source)

        assert(result)(isRight(equalTo(StatsV2(10L, 20))))
      },
      test("fails mixed conversion when narrowing overflows") {
        val source = StatsV1(10, Long.MaxValue)
        val result = Into.derived[StatsV1, StatsV2].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Collection Element Type Refinement")(
      test("widens List[Int] to List[Long]") {
        val source = NumbersV1(List(1, 2, 3))
        val result = Into.derived[NumbersV1, NumbersV2].into(source)

        assert(result)(isRight(equalTo(NumbersV2(List(1L, 2L, 3L)))))
      },
      test("widens empty list") {
        val source = NumbersV1(List.empty)
        val result = Into.derived[NumbersV1, NumbersV2].into(source)

        assert(result)(isRight(equalTo(NumbersV2(List.empty))))
      },
      test("narrows List[Long] to List[Int] when all values fit") {
        case class LongList(values: List[Long])
        case class IntList(values: List[Int])

        val source = LongList(List(1L, 2L, 3L))
        val result = Into.derived[LongList, IntList].into(source)

        assert(result)(isRight(equalTo(IntList(List(1, 2, 3)))))
      },
      test("fails narrowing List when any element overflows") {
        case class LongList(values: List[Long])
        case class IntList(values: List[Int])

        val source = LongList(List(1L, Long.MaxValue, 3L))
        val result = Into.derived[LongList, IntList].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Nested Type Refinement")(
      test("widens nested type field") {
        implicit val innerV1ToV2: Into[InnerV1, InnerV2] = Into.derived[InnerV1, InnerV2]

        val source = OuterV1(InnerV1(42), "test")
        val result = Into.derived[OuterV1, OuterV2].into(source)

        assert(result)(isRight(equalTo(OuterV2(InnerV2(42L), "test"))))
      }
    ),
    suite("Optional Field Type Refinement")(
      test("widens Some[Int] to Some[Long]") {
        val source = OptionalCountV1(Some(42))
        val result = Into.derived[OptionalCountV1, OptionalCountV2].into(source)

        assert(result)(isRight(equalTo(OptionalCountV2(Some(42L)))))
      },
      test("preserves None with type refinement") {
        val source = OptionalCountV1(None)
        val result = Into.derived[OptionalCountV1, OptionalCountV2].into(source)

        assert(result)(isRight(equalTo(OptionalCountV2(None))))
      },
      test("narrows Some[Long] to Some[Int] when value fits") {
        case class OptLong(value: Option[Long])
        case class OptInt(value: Option[Int])

        val source = OptLong(Some(42L))
        val result = Into.derived[OptLong, OptInt].into(source)

        assert(result)(isRight(equalTo(OptInt(Some(42)))))
      },
      test("fails narrowing Some when value overflows") {
        case class OptLong(value: Option[Long])
        case class OptInt(value: Option[Int])

        val source = OptLong(Some(Long.MaxValue))
        val result = Into.derived[OptLong, OptInt].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Map Value Type Refinement")(
      test("widens Map[String, Int] to Map[String, Long]") {
        case class ConfigMapV1(settings: Map[String, Int])
        case class ConfigMapV2(settings: Map[String, Long])

        val source = ConfigMapV1(Map("a" -> 1, "b" -> 2))
        val result = Into.derived[ConfigMapV1, ConfigMapV2].into(source)

        assert(result)(isRight(equalTo(ConfigMapV2(Map("a" -> 1L, "b" -> 2L)))))
      }
    ),
    suite("Either Type Refinement")(
      test("widens Either[String, Int] to Either[String, Long]") {
        case class ResultV1(outcome: Either[String, Int])
        case class ResultV2(outcome: Either[String, Long])

        val source = ResultV1(Right(42))
        val result = Into.derived[ResultV1, ResultV2].into(source)

        assert(result)(isRight(equalTo(ResultV2(Right(42L)))))
      },
      test("preserves Left with type refinement on Right") {
        case class ResultV1(outcome: Either[String, Int])
        case class ResultV2(outcome: Either[String, Long])

        val source = ResultV1(Left("error"))
        val result = Into.derived[ResultV1, ResultV2].into(source)

        assert(result)(isRight(equalTo(ResultV2(Left("error")))))
      }
    )
  )
}
