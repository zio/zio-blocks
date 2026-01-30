package zio.blocks.schema.into.coproduct

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for Into[SealedTrait, SealedTrait] conversions.
 *
 * Covers:
 *   - Case object to case object matching
 *   - Case class to case class matching within sealed traits
 *   - Mixed sealed trait conversions
 *   - Sealed trait with payloads
 */
object SealedTraitToSealedTraitSpec extends ZIOSpecDefault {

  // === Test Data Types ===

  // Simple sealed trait with case objects (by name)
  sealed trait Color
  object Color {
    case object Red   extends Color
    case object Blue  extends Color
    case object Green extends Color
  }

  sealed trait Hue
  object Hue {
    case object Red   extends Hue
    case object Blue  extends Hue
    case object Green extends Hue
  }

  // Sealed trait with case classes (by signature)
  sealed trait EventV1
  object EventV1 {
    case class Created(id: String, ts: Long) extends EventV1
    case class Deleted(id: String)           extends EventV1
  }

  sealed trait EventV2
  object EventV2 {
    case class Spawned(id: String, ts: Long) extends EventV2
    case class Removed(id: String)           extends EventV2
  }

  // ADT with payload conversion
  sealed trait ResultV1
  object ResultV1 {
    case class SuccessV1(value: Int)  extends ResultV1
    case class FailureV1(msg: String) extends ResultV1
  }

  sealed trait ResultV2
  object ResultV2 {
    case class SuccessV2(value: Int)  extends ResultV2
    case class FailureV2(msg: String) extends ResultV2
  }

  // Mixed case objects and case classes
  sealed trait Status
  object Status {
    case object Active              extends Status
    case object Inactive            extends Status
    case class Custom(name: String) extends Status
  }

  sealed trait State
  object State {
    case object Active              extends State
    case object Inactive            extends State
    case class Custom(name: String) extends State
  }

  // Sealed trait with type coercion in payloads
  sealed trait DataV1
  object DataV1 {
    case class IntData(value: Int)      extends DataV1
    case class StringData(text: String) extends DataV1
  }

  sealed trait DataV2
  object DataV2 {
    case class IntData(value: Long)     extends DataV2
    case class StringData(text: String) extends DataV2
  }

  // For "Multiple Cases" test
  sealed trait SourceADTMulti
  object SourceADTMulti {
    case class CaseA(x: Int)     extends SourceADTMulti
    case class CaseB(y: String)  extends SourceADTMulti
    case class CaseC(z: Boolean) extends SourceADTMulti
  }

  sealed trait TargetADTMulti
  object TargetADTMulti {
    case class CaseA(x: Long)    extends TargetADTMulti
    case class CaseB(y: String)  extends TargetADTMulti
    case class CaseC(z: Boolean) extends TargetADTMulti
  }

  // For "Error Propagation" tests
  sealed trait SourceErr
  object SourceErr {
    case class DataErr(value: Long) extends SourceErr
  }

  sealed trait TargetErr
  object TargetErr {
    case class DataErr(value: Int) extends TargetErr
  }

  sealed trait SourceOk
  object SourceOk {
    case class DataOk(value: Long) extends SourceOk
  }

  sealed trait TargetOk
  object TargetOk {
    case class DataOk(value: Int) extends TargetOk
  }

  // For "Sealed Trait with Single Case" tests
  sealed trait SingleV1
  object SingleV1 {
    case class OnlyCase(value: Int) extends SingleV1
  }

  sealed trait SingleV2
  object SingleV2 {
    case class OnlyCase(value: Long) extends SingleV2
  }

  sealed trait SingleObjV1
  object SingleObjV1 {
    case object Singleton extends SingleObjV1
  }

  sealed trait SingleObjV2
  object SingleObjV2 {
    case object Singleton extends SingleObjV2
  }

  def spec: Spec[TestEnvironment, Any] = suite("SealedTraitToSealedTraitSpec")(
    suite("Case Objects by Name")(
      test("maps case object Red to Red") {
        val color: Color = Color.Red
        val result       = Into.derived[Color, Hue].into(color)

        assert(result)(isRight(equalTo(Hue.Red: Hue)))
      },
      test("maps case object Blue to Blue") {
        val color: Color = Color.Blue
        val result       = Into.derived[Color, Hue].into(color)

        assert(result)(isRight(equalTo(Hue.Blue: Hue)))
      },
      test("maps case object Green to Green") {
        val color: Color = Color.Green
        val result       = Into.derived[Color, Hue].into(color)

        assert(result)(isRight(equalTo(Hue.Green: Hue)))
      }
    ),
    suite("Case Classes by Signature")(
      test("maps Created(String, Long) to Spawned(String, Long) by signature") {
        val event: EventV1 = EventV1.Created("abc", 123L)
        val result         = Into.derived[EventV1, EventV2].into(event)

        assert(result)(isRight(equalTo(EventV2.Spawned("abc", 123L): EventV2)))
      },
      test("maps Deleted(String) to Removed(String) by signature") {
        val event: EventV1 = EventV1.Deleted("xyz")
        val result         = Into.derived[EventV1, EventV2].into(event)

        assert(result)(isRight(equalTo(EventV2.Removed("xyz"): EventV2)))
      }
    ),
    suite("ADT with Payloads")(
      test("converts Success case class") {
        val result1: ResultV1 = ResultV1.SuccessV1(42)
        val converted         = Into.derived[ResultV1, ResultV2].into(result1)

        assert(converted)(isRight(equalTo(ResultV2.SuccessV2(42): ResultV2)))
      },
      test("converts Failure case class") {
        val result1: ResultV1 = ResultV1.FailureV1("error message")
        val converted         = Into.derived[ResultV1, ResultV2].into(result1)

        assert(converted)(isRight(equalTo(ResultV2.FailureV2("error message"): ResultV2)))
      }
    ),
    suite("Mixed Case Objects and Case Classes")(
      test("converts Active case object") {
        val status: Status = Status.Active
        val result         = Into.derived[Status, State].into(status)

        assert(result)(isRight(equalTo(State.Active: State)))
      },
      test("converts Inactive case object") {
        val status: Status = Status.Inactive
        val result         = Into.derived[Status, State].into(status)

        assert(result)(isRight(equalTo(State.Inactive: State)))
      },
      test("converts Custom case class") {
        val status: Status = Status.Custom("pending")
        val result         = Into.derived[Status, State].into(status)

        assert(result)(isRight(equalTo(State.Custom("pending"): State)))
      }
    ),
    suite("Type Coercion in Payloads")(
      test("converts with Int to Long coercion in case class payload") {
        val data: DataV1 = DataV1.IntData(42)
        val result       = Into.derived[DataV1, DataV2].into(data)

        assert(result)(isRight(equalTo(DataV2.IntData(42L): DataV2)))
      },
      test("converts case class with String payload (no coercion)") {
        val data: DataV1 = DataV1.StringData("hello")
        val result       = Into.derived[DataV1, DataV2].into(data)

        assert(result)(isRight(equalTo(DataV2.StringData("hello"): DataV2)))
      }
    ),
    suite("Multiple Cases")(
      test("converts all cases in sealed trait correctly") {
        val instances: List[SourceADTMulti] = List(
          SourceADTMulti.CaseA(10),
          SourceADTMulti.CaseB("test"),
          SourceADTMulti.CaseC(true)
        )

        val results = instances.map(Into.derived[SourceADTMulti, TargetADTMulti].into)

        assertTrue(
          results == List(
            Right(TargetADTMulti.CaseA(10L): TargetADTMulti),
            Right(TargetADTMulti.CaseB("test"): TargetADTMulti),
            Right(TargetADTMulti.CaseC(true): TargetADTMulti)
          )
        )
      }
    ),
    suite("Error Propagation")(
      test("propagates conversion error from case class payload") {
        val source: SourceErr = SourceErr.DataErr(Long.MaxValue)
        val result            = Into.derived[SourceErr, TargetErr].into(source)

        // Should fail due to overflow
        assert(result)(isLeft)
      },
      test("succeeds when payload conversion is valid") {
        val source: SourceOk = SourceOk.DataOk(42L)
        val result           = Into.derived[SourceOk, TargetOk].into(source)

        assert(result)(isRight(equalTo(TargetOk.DataOk(42): TargetOk)))
      }
    ),
    suite("Sealed Trait with Single Case")(
      test("converts sealed trait with single case class") {
        val source: SingleV1 = SingleV1.OnlyCase(42)
        val result           = Into.derived[SingleV1, SingleV2].into(source)

        assert(result)(isRight(equalTo(SingleV2.OnlyCase(42L): SingleV2)))
      },
      test("converts sealed trait with single case object") {

        val source: SingleObjV1 = SingleObjV1.Singleton
        val result              = Into.derived[SingleObjV1, SingleObjV2].into(source)

        assert(result)(isRight(equalTo(SingleObjV2.Singleton: SingleObjV2)))
      }
    )
  )
}
