package zio.blocks.schema.into.coproduct

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for nested coproduct conversions.
 *
 * Covers:
 *   - Coproducts containing other coproducts
 *   - Coproducts containing products with type coercion
 *   - Multiple levels of nesting
 */
object NestedCoproductsSpec extends ZIOSpecDefault {

  // === Test Data Types ===

  // Inner coproduct
  sealed trait Inner
  object Inner {
    case class A(value: Int) extends Inner
  }

  sealed trait InnerV2
  object InnerV2 {
    case class A(value: Long) extends InnerV2
  }

  implicit val innerToV2: Into[Inner, InnerV2] = Into.derived[Inner, InnerV2]

  // Outer coproduct containing inner coproduct
  sealed trait Outer
  object Outer {
    case class Container(inner: Inner, name: String) extends Outer
    case class Empty()                               extends Outer
  }

  sealed trait OuterV2
  object OuterV2 {
    case class Container(inner: InnerV2, name: String) extends OuterV2
    case class Empty()                                 extends OuterV2
  }

  // Coproduct with nested product conversions
  sealed trait Event
  object Event {
    case class UserCreated(userId: String, timestamp: Long) extends Event
    case class UserDeleted(userId: String)                  extends Event
    case class DataUpdated(id: Int, data: String)           extends Event
  }

  sealed trait EventV2
  object EventV2 {
    case class UserCreated(userId: String, timestamp: Long) extends EventV2
    case class UserDeleted(userId: String)                  extends EventV2
    case class DataUpdated(id: Long, data: String)          extends EventV2
  }

  // Multiple levels of nesting
  sealed trait Level3
  object Level3 {
    case class L3Data(value: Int) extends Level3
  }

  sealed trait Level2
  object Level2 {
    case class L2Container(data: Level3) extends Level2
  }

  sealed trait Level1
  object Level1 {
    case class L1Wrapper(inner: Level2, label: String) extends Level1
  }

  sealed trait Level3V2
  object Level3V2 {
    case class L3Data(value: Long) extends Level3V2
  }

  sealed trait Level2V2
  object Level2V2 {
    case class L2Container(data: Level3V2) extends Level2V2
  }

  sealed trait Level1V2
  object Level1V2 {
    case class L1Wrapper(inner: Level2V2, label: String) extends Level1V2
  }

  implicit val l3ToV2: Into[Level3, Level3V2] = Into.derived[Level3, Level3V2]
  implicit val l2ToV2: Into[Level2, Level2V2] = Into.derived[Level2, Level2V2]

  // Coproduct containing product with nested coproduct
  sealed trait Color
  object Color {
    case object Red  extends Color
    case object Blue extends Color
  }

  sealed trait ColorV2
  object ColorV2 {
    case object Red  extends ColorV2
    case object Blue extends ColorV2
  }

  implicit val colorToV2: Into[Color, ColorV2] = Into.derived[Color, ColorV2]

  sealed trait Message
  object Message {
    case class TextMsg(content: String, color: Color) extends Message
    case class BinaryMsg(data: Array[Byte])           extends Message
  }

  sealed trait MessageV2
  object MessageV2 {
    case class TextMsg(content: String, color: ColorV2) extends MessageV2
    case class BinaryMsg(data: Array[Byte])             extends MessageV2
  }

  // For "Error Propagation" tests
  sealed trait InnerFail
  object InnerFail {
    case class Data(value: Long) extends InnerFail
  }

  sealed trait OuterFail
  object OuterFail {
    case class Wrapper(inner: InnerFail) extends OuterFail
  }

  sealed trait InnerTarget
  object InnerTarget {
    case class Data(value: Int) extends InnerTarget
  }

  sealed trait OuterTarget
  object OuterTarget {
    case class Wrapper(inner: InnerTarget) extends OuterTarget
  }

  implicit val innerFailToTarget: Into[InnerFail, InnerTarget] = Into.derived[InnerFail, InnerTarget]

  sealed trait InnerOkNested
  object InnerOkNested {
    case class Data(value: Long) extends InnerOkNested
  }

  sealed trait OuterOkNested
  object OuterOkNested {
    case class Wrapper(inner: InnerOkNested) extends OuterOkNested
  }

  sealed trait InnerTargetOk
  object InnerTargetOk {
    case class Data(value: Int) extends InnerTargetOk
  }

  sealed trait OuterTargetOk
  object OuterTargetOk {
    case class Wrapper(inner: InnerTargetOk) extends OuterTargetOk
  }

  implicit val innerOkToTargetOk: Into[InnerOkNested, InnerTargetOk] = Into.derived[InnerOkNested, InnerTargetOk]

  // For "Complex Nested Scenarios" test
  sealed trait InnerComplex
  object InnerComplex {
    case object A extends InnerComplex
    case object B extends InnerComplex
  }

  sealed trait OuterComplex
  object OuterComplex {
    case class First(inner: InnerComplex)  extends OuterComplex
    case class Second(inner: InnerComplex) extends OuterComplex
    case class Third(value: String)        extends OuterComplex
  }

  sealed trait InnerComplexV2
  object InnerComplexV2 {
    case object A extends InnerComplexV2
    case object B extends InnerComplexV2
  }

  sealed trait OuterComplexV2
  object OuterComplexV2 {
    case class First(inner: InnerComplexV2)  extends OuterComplexV2
    case class Second(inner: InnerComplexV2) extends OuterComplexV2
    case class Third(value: String)          extends OuterComplexV2
  }

  implicit val innerComplexToV2: Into[InnerComplex, InnerComplexV2] = Into.derived[InnerComplex, InnerComplexV2]

  def spec: Spec[TestEnvironment, Any] = suite("NestedCoproductsSpec")(
    suite("Coproduct Containing Coproduct")(
      test("converts outer coproduct with nested inner coproduct") {
        val outer: Outer = Outer.Container(Inner.A(42), "test")
        val result       = Into.derived[Outer, OuterV2].into(outer)

        assert(result)(isRight(equalTo(OuterV2.Container(InnerV2.A(42L), "test"): OuterV2)))
      },
      test("converts Empty case without nested coproduct") {
        val outer: Outer = Outer.Empty()
        val result       = Into.derived[Outer, OuterV2].into(outer)

        assert(result)(isRight(equalTo(OuterV2.Empty(): OuterV2)))
      }
    ),
    suite("Coproduct with Nested Product Conversions")(
      test("converts UserCreated case") {
        val event: Event = Event.UserCreated("user123", 1234567890L)
        val result       = Into.derived[Event, EventV2].into(event)

        assert(result)(isRight(equalTo(EventV2.UserCreated("user123", 1234567890L): EventV2)))
      },
      test("converts UserDeleted case") {
        val event: Event = Event.UserDeleted("user456")
        val result       = Into.derived[Event, EventV2].into(event)

        assert(result)(isRight(equalTo(EventV2.UserDeleted("user456"): EventV2)))
      },
      test("converts DataUpdated case with Int to Long coercion") {
        val event: Event = Event.DataUpdated(100, "new data")
        val result       = Into.derived[Event, EventV2].into(event)

        assert(result)(isRight(equalTo(EventV2.DataUpdated(100L, "new data"): EventV2)))
      }
    ),
    suite("Multiple Levels of Nesting")(
      test("converts 3-level nested coproduct structure") {
        val level1: Level1 = Level1.L1Wrapper(Level2.L2Container(Level3.L3Data(42)), "root")
        val result         = Into.derived[Level1, Level1V2].into(level1)

        assert(result)(
          isRight(equalTo(Level1V2.L1Wrapper(Level2V2.L2Container(Level3V2.L3Data(42L)), "root"): Level1V2))
        )
      }
    ),
    suite("Product Containing Nested Coproduct")(
      test("converts TextMsg with nested Color coproduct") {
        val msg: Message = Message.TextMsg("hello", Color.Red)
        val result       = Into.derived[Message, MessageV2].into(msg)

        assert(result)(isRight(equalTo(MessageV2.TextMsg("hello", ColorV2.Red): MessageV2)))
      },
      test("converts TextMsg with Blue color") {
        val msg: Message = Message.TextMsg("world", Color.Blue)
        val result       = Into.derived[Message, MessageV2].into(msg)

        assert(result)(isRight(equalTo(MessageV2.TextMsg("world", ColorV2.Blue): MessageV2)))
      },
      test("converts BinaryMsg without nested coproduct") {
        val msg: Message = Message.BinaryMsg(Array[Byte](1, 2, 3))
        val result       = Into.derived[Message, MessageV2].into(msg)

        assert(result.map(_.isInstanceOf[MessageV2.BinaryMsg]))(isRight(isTrue))
      }
    ),
    suite("Error Propagation in Nested Coproducts")(
      test("propagates error from nested coproduct conversion") {
        val outer: OuterFail = OuterFail.Wrapper(InnerFail.Data(Long.MaxValue))
        val result           = Into.derived[OuterFail, OuterTarget].into(outer)

        assert(result)(isLeft)
      },
      test("succeeds when nested conversion is valid") {
        val outer: OuterOkNested = OuterOkNested.Wrapper(InnerOkNested.Data(42L))
        val result               = Into.derived[OuterOkNested, OuterTargetOk].into(outer)

        assert(result)(isRight(equalTo(OuterTargetOk.Wrapper(InnerTargetOk.Data(42)): OuterTargetOk)))
      }
    ),
    suite("Complex Nested Scenarios")(
      test("coproduct with multiple cases containing nested coproducts") {
        val cases: List[OuterComplex] = List(
          OuterComplex.First(InnerComplex.A),
          OuterComplex.Second(InnerComplex.B),
          OuterComplex.Third("data")
        )

        val results = cases.map(Into.derived[OuterComplex, OuterComplexV2].into)

        assertTrue(
          results == List(
            Right(OuterComplexV2.First(InnerComplexV2.A): OuterComplexV2),
            Right(OuterComplexV2.Second(InnerComplexV2.B): OuterComplexV2),
            Right(OuterComplexV2.Third("data"): OuterComplexV2)
          )
        )
      }
    )
  )
}
