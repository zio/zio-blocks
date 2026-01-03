package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for large coproduct conversions. */
object LargeCoproductSpec extends ZIOSpecDefault {

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

  def spec: Spec[TestEnvironment, Any] = suite("LargeCoproductSpec")(
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
  )
}
