package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for large coproduct conversions (25 cases to exceed typical limits).
 */
object LargeCoproductSpec extends ZIOSpecDefault {

  // 10-case sealed traits (original tests)
  sealed trait Status10
  object Status10 {
    case object S01 extends Status10
    case object S02 extends Status10
    case object S03 extends Status10
    case object S04 extends Status10
    case object S05 extends Status10
    case object S06 extends Status10
    case object S07 extends Status10
    case object S08 extends Status10
    case object S09 extends Status10
    case object S10 extends Status10
  }

  sealed trait Status10Alt
  object Status10Alt {
    case object S01 extends Status10Alt
    case object S02 extends Status10Alt
    case object S03 extends Status10Alt
    case object S04 extends Status10Alt
    case object S05 extends Status10Alt
    case object S06 extends Status10Alt
    case object S07 extends Status10Alt
    case object S08 extends Status10Alt
    case object S09 extends Status10Alt
    case object S10 extends Status10Alt
  }

  sealed trait MixedEvent
  object MixedEvent {
    case object Started               extends MixedEvent
    case class Error(code: Int)       extends MixedEvent
    case class Progress(percent: Int) extends MixedEvent
  }

  sealed trait MixedEventAlt
  object MixedEventAlt {
    case object Started                extends MixedEventAlt
    case class Error(code: Long)       extends MixedEventAlt
    case class Progress(percent: Long) extends MixedEventAlt
  }

  // 25-case sealed traits (beyond typical limits)
  sealed trait Status25
  object Status25 {
    case object S01 extends Status25
    case object S02 extends Status25
    case object S03 extends Status25
    case object S04 extends Status25
    case object S05 extends Status25
    case object S06 extends Status25
    case object S07 extends Status25
    case object S08 extends Status25
    case object S09 extends Status25
    case object S10 extends Status25
    case object S11 extends Status25
    case object S12 extends Status25
    case object S13 extends Status25
    case object S14 extends Status25
    case object S15 extends Status25
    case object S16 extends Status25
    case object S17 extends Status25
    case object S18 extends Status25
    case object S19 extends Status25
    case object S20 extends Status25
    case object S21 extends Status25
    case object S22 extends Status25
    case object S23 extends Status25
    case object S24 extends Status25
    case object S25 extends Status25
  }

  sealed trait Status25Alt
  object Status25Alt {
    case object S01 extends Status25Alt
    case object S02 extends Status25Alt
    case object S03 extends Status25Alt
    case object S04 extends Status25Alt
    case object S05 extends Status25Alt
    case object S06 extends Status25Alt
    case object S07 extends Status25Alt
    case object S08 extends Status25Alt
    case object S09 extends Status25Alt
    case object S10 extends Status25Alt
    case object S11 extends Status25Alt
    case object S12 extends Status25Alt
    case object S13 extends Status25Alt
    case object S14 extends Status25Alt
    case object S15 extends Status25Alt
    case object S16 extends Status25Alt
    case object S17 extends Status25Alt
    case object S18 extends Status25Alt
    case object S19 extends Status25Alt
    case object S20 extends Status25Alt
    case object S21 extends Status25Alt
    case object S22 extends Status25Alt
    case object S23 extends Status25Alt
    case object S24 extends Status25Alt
    case object S25 extends Status25Alt
  }

  def spec: Spec[TestEnvironment, Any] = suite("LargeCoproductSpec")(
    suite("10-case coproducts")(
      test("coproduct with 10 case objects") {
        val source: Status10 = Status10.S05
        val result           = Into.derived[Status10, Status10Alt].into(source)
        assert(result)(isRight(equalTo(Status10Alt.S05: Status10Alt)))
      },
      test("mixed case objects and case classes with coercion") {
        val source: MixedEvent = MixedEvent.Error(42)
        val result             = Into.derived[MixedEvent, MixedEventAlt].into(source)
        assert(result)(isRight(equalTo(MixedEventAlt.Error(42L): MixedEventAlt)))
      }
    ),
    suite("25-case coproducts (beyond typical limits)")(
      test("coproduct with 25 case objects - first case") {
        val source: Status25 = Status25.S01
        val result           = Into.derived[Status25, Status25Alt].into(source)
        assert(result)(isRight(equalTo(Status25Alt.S01: Status25Alt)))
      },
      test("coproduct with 25 case objects - middle case") {
        val source: Status25 = Status25.S13
        val result           = Into.derived[Status25, Status25Alt].into(source)
        assert(result)(isRight(equalTo(Status25Alt.S13: Status25Alt)))
      },
      test("coproduct with 25 case objects - last case") {
        val source: Status25 = Status25.S25
        val result           = Into.derived[Status25, Status25Alt].into(source)
        assert(result)(isRight(equalTo(Status25Alt.S25: Status25Alt)))
      }
    )
  )
}
