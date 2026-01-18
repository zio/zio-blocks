package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for large coproduct conversions (22+ cases to exceed typical limits).
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

  // 22-case sealed traits (boundary case)
  sealed trait Status22
  object Status22 {
    case object S01 extends Status22
    case object S02 extends Status22
    case object S03 extends Status22
    case object S04 extends Status22
    case object S05 extends Status22
    case object S06 extends Status22
    case object S07 extends Status22
    case object S08 extends Status22
    case object S09 extends Status22
    case object S10 extends Status22
    case object S11 extends Status22
    case object S12 extends Status22
    case object S13 extends Status22
    case object S14 extends Status22
    case object S15 extends Status22
    case object S16 extends Status22
    case object S17 extends Status22
    case object S18 extends Status22
    case object S19 extends Status22
    case object S20 extends Status22
    case object S21 extends Status22
    case object S22 extends Status22
  }

  sealed trait Status22Alt
  object Status22Alt {
    case object S01 extends Status22Alt
    case object S02 extends Status22Alt
    case object S03 extends Status22Alt
    case object S04 extends Status22Alt
    case object S05 extends Status22Alt
    case object S06 extends Status22Alt
    case object S07 extends Status22Alt
    case object S08 extends Status22Alt
    case object S09 extends Status22Alt
    case object S10 extends Status22Alt
    case object S11 extends Status22Alt
    case object S12 extends Status22Alt
    case object S13 extends Status22Alt
    case object S14 extends Status22Alt
    case object S15 extends Status22Alt
    case object S16 extends Status22Alt
    case object S17 extends Status22Alt
    case object S18 extends Status22Alt
    case object S19 extends Status22Alt
    case object S20 extends Status22Alt
    case object S21 extends Status22Alt
    case object S22 extends Status22Alt
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

  // 22-case mixed ADT with payloads
  sealed trait Event22
  object Event22 {
    case object E01                    extends Event22
    case object E02                    extends Event22
    case object E03                    extends Event22
    case object E04                    extends Event22
    case object E05                    extends Event22
    case class E06(value: Int)         extends Event22
    case class E07(value: Int)         extends Event22
    case class E08(value: Int)         extends Event22
    case class E09(value: Int)         extends Event22
    case class E10(value: Int)         extends Event22
    case class E11(name: String)       extends Event22
    case class E12(name: String)       extends Event22
    case class E13(name: String)       extends Event22
    case class E14(name: String)       extends Event22
    case class E15(name: String)       extends Event22
    case class E16(x: Int, y: Int)     extends Event22
    case class E17(x: Int, y: Int)     extends Event22
    case class E18(x: Int, y: Int)     extends Event22
    case class E19(x: Int, y: Int)     extends Event22
    case class E20(x: Int, y: Int)     extends Event22
    case class E21(a: String, b: Long) extends Event22
    case class E22(a: String, b: Long) extends Event22
  }

  sealed trait Event22Alt
  object Event22Alt {
    case object E01                    extends Event22Alt
    case object E02                    extends Event22Alt
    case object E03                    extends Event22Alt
    case object E04                    extends Event22Alt
    case object E05                    extends Event22Alt
    case class E06(value: Long)        extends Event22Alt
    case class E07(value: Long)        extends Event22Alt
    case class E08(value: Long)        extends Event22Alt
    case class E09(value: Long)        extends Event22Alt
    case class E10(value: Long)        extends Event22Alt
    case class E11(name: String)       extends Event22Alt
    case class E12(name: String)       extends Event22Alt
    case class E13(name: String)       extends Event22Alt
    case class E14(name: String)       extends Event22Alt
    case class E15(name: String)       extends Event22Alt
    case class E16(x: Long, y: Long)   extends Event22Alt
    case class E17(x: Long, y: Long)   extends Event22Alt
    case class E18(x: Long, y: Long)   extends Event22Alt
    case class E19(x: Long, y: Long)   extends Event22Alt
    case class E20(x: Long, y: Long)   extends Event22Alt
    case class E21(a: String, b: Long) extends Event22Alt
    case class E22(a: String, b: Long) extends Event22Alt
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
    suite("22-case coproducts (boundary case)")(
      test("coproduct with 22 case objects - first case") {
        val source: Status22 = Status22.S01
        val result           = Into.derived[Status22, Status22Alt].into(source)
        assert(result)(isRight(equalTo(Status22Alt.S01: Status22Alt)))
      },
      test("coproduct with 22 case objects - middle case") {
        val source: Status22 = Status22.S11
        val result           = Into.derived[Status22, Status22Alt].into(source)
        assert(result)(isRight(equalTo(Status22Alt.S11: Status22Alt)))
      },
      test("coproduct with 22 case objects - last case") {
        val source: Status22 = Status22.S22
        val result           = Into.derived[Status22, Status22Alt].into(source)
        assert(result)(isRight(equalTo(Status22Alt.S22: Status22Alt)))
      },
      test("22-case mixed ADT - case object") {
        val source: Event22 = Event22.E03
        val result          = Into.derived[Event22, Event22Alt].into(source)
        assert(result)(isRight(equalTo(Event22Alt.E03: Event22Alt)))
      },
      test("22-case mixed ADT - case class with Int coercion") {
        val source: Event22 = Event22.E07(42)
        val result          = Into.derived[Event22, Event22Alt].into(source)
        assert(result)(isRight(equalTo(Event22Alt.E07(42L): Event22Alt)))
      },
      test("22-case mixed ADT - case class with String (no coercion)") {
        val source: Event22 = Event22.E13("hello")
        val result          = Into.derived[Event22, Event22Alt].into(source)
        assert(result)(isRight(equalTo(Event22Alt.E13("hello"): Event22Alt)))
      },
      test("22-case mixed ADT - case class with multiple fields") {
        val source: Event22 = Event22.E18(10, 20)
        val result          = Into.derived[Event22, Event22Alt].into(source)
        assert(result)(isRight(equalTo(Event22Alt.E18(10L, 20L): Event22Alt)))
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
