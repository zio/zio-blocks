package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for large coproduct (sealed trait with many cases) conversions.
 *
 * Covers:
 *   - Sealed traits with 20+ cases
 *   - Case matching with many cases
 *   - Mix of case objects and case classes
 *   - Type coercion within cases
 */
object LargeCoproductSpec extends ZIOSpecDefault {

  // === Sealed trait with 20 case objects ===
  sealed trait Status20
  object Status20 {
    case object S01 extends Status20
    case object S02 extends Status20
    case object S03 extends Status20
    case object S04 extends Status20
    case object S05 extends Status20
    case object S06 extends Status20
    case object S07 extends Status20
    case object S08 extends Status20
    case object S09 extends Status20
    case object S10 extends Status20
    case object S11 extends Status20
    case object S12 extends Status20
    case object S13 extends Status20
    case object S14 extends Status20
    case object S15 extends Status20
    case object S16 extends Status20
    case object S17 extends Status20
    case object S18 extends Status20
    case object S19 extends Status20
    case object S20 extends Status20
  }

  sealed trait Status20Alt
  object Status20Alt {
    case object S01 extends Status20Alt
    case object S02 extends Status20Alt
    case object S03 extends Status20Alt
    case object S04 extends Status20Alt
    case object S05 extends Status20Alt
    case object S06 extends Status20Alt
    case object S07 extends Status20Alt
    case object S08 extends Status20Alt
    case object S09 extends Status20Alt
    case object S10 extends Status20Alt
    case object S11 extends Status20Alt
    case object S12 extends Status20Alt
    case object S13 extends Status20Alt
    case object S14 extends Status20Alt
    case object S15 extends Status20Alt
    case object S16 extends Status20Alt
    case object S17 extends Status20Alt
    case object S18 extends Status20Alt
    case object S19 extends Status20Alt
    case object S20 extends Status20Alt
  }

  // === Sealed trait with 15 mixed cases ===
  sealed trait MixedEvent
  object MixedEvent {
    case object Started                            extends MixedEvent
    case object Stopped                            extends MixedEvent
    case object Paused                             extends MixedEvent
    case object Resumed                            extends MixedEvent
    case object Cancelled                          extends MixedEvent
    case class Error(code: Int, message: String)   extends MixedEvent
    case class Warning(message: String)            extends MixedEvent
    case class Progress(percent: Int)              extends MixedEvent
    case class Data(payload: String)               extends MixedEvent
    case class Metric(name: String, value: Double) extends MixedEvent
    case object Completed                          extends MixedEvent
    case object Failed                             extends MixedEvent
    case class Retry(attempt: Int)                 extends MixedEvent
    case class Timeout(seconds: Int)               extends MixedEvent
    case class Custom(kind: String, data: String)  extends MixedEvent
  }

  sealed trait MixedEventAlt
  object MixedEventAlt {
    case object Started                            extends MixedEventAlt
    case object Stopped                            extends MixedEventAlt
    case object Paused                             extends MixedEventAlt
    case object Resumed                            extends MixedEventAlt
    case object Cancelled                          extends MixedEventAlt
    case class Error(code: Long, message: String)  extends MixedEventAlt
    case class Warning(message: String)            extends MixedEventAlt
    case class Progress(percent: Long)             extends MixedEventAlt
    case class Data(payload: String)               extends MixedEventAlt
    case class Metric(name: String, value: Double) extends MixedEventAlt
    case object Completed                          extends MixedEventAlt
    case object Failed                             extends MixedEventAlt
    case class Retry(attempt: Long)                extends MixedEventAlt
    case class Timeout(seconds: Long)              extends MixedEventAlt
    case class Custom(kind: String, data: String)  extends MixedEventAlt
  }

  // === Sealed trait with nested case classes ===
  sealed trait NestedEvent
  object NestedEvent {
    case class Simple(value: Int)           extends NestedEvent
    case class Wrapped(inner: Inner)        extends NestedEvent
    case class Multiple(a: Inner, b: Inner) extends NestedEvent
    case object Empty                       extends NestedEvent

    case class Inner(x: Int, y: Int)
  }

  sealed trait NestedEventAlt
  object NestedEventAlt {
    case class Simple(value: Long)          extends NestedEventAlt
    case class Wrapped(inner: Inner)        extends NestedEventAlt
    case class Multiple(a: Inner, b: Inner) extends NestedEventAlt
    case object Empty                       extends NestedEventAlt

    case class Inner(x: Long, y: Long)
  }

  def spec: Spec[TestEnvironment, Any] = suite("LargeCoproductSpec")(
    suite("20 Case Objects")(
      test("converts first case object") {
        val result = Into.derived[Status20, Status20Alt].into(Status20.S01: Status20)
        assert(result)(isRight(equalTo(Status20Alt.S01: Status20Alt)))
      },
      test("converts middle case object") {
        val result = Into.derived[Status20, Status20Alt].into(Status20.S10: Status20)
        assert(result)(isRight(equalTo(Status20Alt.S10: Status20Alt)))
      },
      test("converts last case object") {
        val result = Into.derived[Status20, Status20Alt].into(Status20.S20: Status20)
        assert(result)(isRight(equalTo(Status20Alt.S20: Status20Alt)))
      },
      test("converts all 20 case objects") {
        val into = Into.derived[Status20, Status20Alt]

        val results = List(
          into.into(Status20.S01),
          into.into(Status20.S02),
          into.into(Status20.S03),
          into.into(Status20.S04),
          into.into(Status20.S05),
          into.into(Status20.S06),
          into.into(Status20.S07),
          into.into(Status20.S08),
          into.into(Status20.S09),
          into.into(Status20.S10),
          into.into(Status20.S11),
          into.into(Status20.S12),
          into.into(Status20.S13),
          into.into(Status20.S14),
          into.into(Status20.S15),
          into.into(Status20.S16),
          into.into(Status20.S17),
          into.into(Status20.S18),
          into.into(Status20.S19),
          into.into(Status20.S20)
        )

        assert(results.forall(_.isRight))(isTrue)
      }
    ),
    suite("15 Mixed Cases")(
      test("converts case object") {
        val result = Into.derived[MixedEvent, MixedEventAlt].into(MixedEvent.Started: MixedEvent)
        assert(result)(isRight(equalTo(MixedEventAlt.Started: MixedEventAlt)))
      },
      test("converts case class with type coercion") {
        val result = Into.derived[MixedEvent, MixedEventAlt].into(MixedEvent.Error(500, "Internal Error"): MixedEvent)
        assert(result)(isRight(equalTo(MixedEventAlt.Error(500L, "Internal Error"): MixedEventAlt)))
      },
      test("converts case class with same types") {
        val result = Into.derived[MixedEvent, MixedEventAlt].into(MixedEvent.Warning("low memory"): MixedEvent)
        assert(result)(isRight(equalTo(MixedEventAlt.Warning("low memory"): MixedEventAlt)))
      },
      test("converts Progress case with coercion") {
        val result = Into.derived[MixedEvent, MixedEventAlt].into(MixedEvent.Progress(75): MixedEvent)
        assert(result)(isRight(equalTo(MixedEventAlt.Progress(75L): MixedEventAlt)))
      },
      test("converts Metric case with Double") {
        val result = Into.derived[MixedEvent, MixedEventAlt].into(MixedEvent.Metric("cpu_usage", 85.5): MixedEvent)
        assert(result)(isRight(equalTo(MixedEventAlt.Metric("cpu_usage", 85.5): MixedEventAlt)))
      },
      test("converts Retry case") {
        val result = Into.derived[MixedEvent, MixedEventAlt].into(MixedEvent.Retry(3): MixedEvent)
        assert(result)(isRight(equalTo(MixedEventAlt.Retry(3L): MixedEventAlt)))
      },
      test("converts Custom case") {
        val result = Into.derived[MixedEvent, MixedEventAlt].into(MixedEvent.Custom("special", "payload"): MixedEvent)
        assert(result)(isRight(equalTo(MixedEventAlt.Custom("special", "payload"): MixedEventAlt)))
      }
    ),
    suite("Nested Case Classes in Coproduct")(
      test("converts Simple case with coercion") {
        implicit val innerInto: Into[NestedEvent.Inner, NestedEventAlt.Inner] =
          Into.derived[NestedEvent.Inner, NestedEventAlt.Inner]

        val result = Into.derived[NestedEvent, NestedEventAlt].into(NestedEvent.Simple(42): NestedEvent)
        assert(result)(isRight(equalTo(NestedEventAlt.Simple(42L): NestedEventAlt)))
      },
      test("converts Wrapped case with nested type") {
        implicit val innerInto: Into[NestedEvent.Inner, NestedEventAlt.Inner] =
          Into.derived[NestedEvent.Inner, NestedEventAlt.Inner]

        val result = Into
          .derived[NestedEvent, NestedEventAlt]
          .into(
            NestedEvent.Wrapped(NestedEvent.Inner(1, 2)): NestedEvent
          )
        assert(result)(
          isRight(
            equalTo(
              NestedEventAlt.Wrapped(NestedEventAlt.Inner(1L, 2L)): NestedEventAlt
            )
          )
        )
      },
      test("converts Multiple case with two nested types") {
        implicit val innerInto: Into[NestedEvent.Inner, NestedEventAlt.Inner] =
          Into.derived[NestedEvent.Inner, NestedEventAlt.Inner]

        val result = Into
          .derived[NestedEvent, NestedEventAlt]
          .into(
            NestedEvent.Multiple(NestedEvent.Inner(1, 2), NestedEvent.Inner(3, 4)): NestedEvent
          )
        assert(result)(
          isRight(
            equalTo(
              NestedEventAlt.Multiple(NestedEventAlt.Inner(1L, 2L), NestedEventAlt.Inner(3L, 4L)): NestedEventAlt
            )
          )
        )
      },
      test("converts Empty case object") {
        implicit val innerInto: Into[NestedEvent.Inner, NestedEventAlt.Inner] =
          Into.derived[NestedEvent.Inner, NestedEventAlt.Inner]

        val result = Into.derived[NestedEvent, NestedEventAlt].into(NestedEvent.Empty: NestedEvent)
        assert(result)(isRight(equalTo(NestedEventAlt.Empty: NestedEventAlt)))
      }
    ),
    suite("Identity on Large Coproduct")(
      test("converts large coproduct to itself") {
        val result = Into.derived[Status20, Status20].into(Status20.S15: Status20)
        assert(result)(isRight(equalTo(Status20.S15: Status20)))
      }
    )
  )
}
