package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for case object conversions in Into derivation.
 *
 * Covers:
 *   - Case object to case object
 *   - Case object to empty case class
 *   - Empty case class to case object
 *   - Case objects within sealed traits
 */
object CaseObjectSpec extends ZIOSpecDefault {

  // === Standalone Case Objects ===
  case object SingletonA
  case object SingletonB
  case object SingletonC

  // === Empty Case Classes for Comparison ===
  case class EmptyClass()
  case class AnotherEmptyClass()

  // === Sealed Traits with Case Objects Only ===
  sealed trait Status
  object Status {
    case object Active   extends Status
    case object Inactive extends Status
    case object Pending  extends Status
  }

  sealed trait StatusAlt
  object StatusAlt {
    case object Active   extends StatusAlt
    case object Inactive extends StatusAlt
    case object Pending  extends StatusAlt
  }

  // === Sealed Trait with Mixed Case Objects and Case Classes ===
  sealed trait Result
  object Result {
    case object Success               extends Result
    case object Failure               extends Result
    case class Error(message: String) extends Result
  }

  sealed trait ResultAlt
  object ResultAlt {
    case object Success           extends ResultAlt
    case object Failure           extends ResultAlt
    case class Error(msg: String) extends ResultAlt
  }

  // === Target types for empty conversions ===
  case class WithOptional(value: Option[Int])
  case class WithDefault(name: String = "default")

  def spec: Spec[TestEnvironment, Any] = suite("CaseObjectSpec")(
    suite("Case Object to Case Object")(
      test("converts case object to another case object") {
        val result = Into.derived[SingletonA.type, SingletonB.type].into(SingletonA)

        assert(result)(isRight(equalTo(SingletonB)))
      },
      test("converts case object to itself") {
        val result = Into.derived[SingletonA.type, SingletonA.type].into(SingletonA)

        assert(result)(isRight(equalTo(SingletonA)))
      },
      test("chain of case object conversions") {
        val intoAB = Into.derived[SingletonA.type, SingletonB.type]
        val intoBC = Into.derived[SingletonB.type, SingletonC.type]

        val result = intoAB.into(SingletonA).flatMap(b => intoBC.into(b))

        assert(result)(isRight(equalTo(SingletonC)))
      }
    ),
    suite("Case Object to Empty Case Class")(
      test("converts case object to empty case class") {
        val result = Into.derived[SingletonA.type, EmptyClass].into(SingletonA)

        assert(result)(isRight(equalTo(EmptyClass())))
      }
    ),
    suite("Empty Case Class to Case Object")(
      test("converts empty case class to case object") {
        val source = EmptyClass()
        val result = Into.derived[EmptyClass, SingletonA.type].into(source)

        assert(result)(isRight(equalTo(SingletonA)))
      }
    ),
    suite("Case Object to Case Class with Optional")(
      test("converts case object to case class with optional field (None)") {
        val result = Into.derived[SingletonA.type, WithOptional].into(SingletonA)

        assert(result)(isRight(equalTo(WithOptional(None))))
      }
    ),
    suite("Case Object to Case Class with Default")(
      test("converts case object to case class with default value") {
        val result = Into.derived[SingletonA.type, WithDefault].into(SingletonA)

        assert(result)(isRight(equalTo(WithDefault("default"))))
      }
    ),
    suite("Sealed Trait with Case Objects Only")(
      test("converts Active status") {
        val result = Into.derived[Status, StatusAlt].into(Status.Active: Status)

        assert(result)(isRight(equalTo(StatusAlt.Active: StatusAlt)))
      },
      test("converts Inactive status") {
        val result = Into.derived[Status, StatusAlt].into(Status.Inactive: Status)

        assert(result)(isRight(equalTo(StatusAlt.Inactive: StatusAlt)))
      },
      test("converts Pending status") {
        val result = Into.derived[Status, StatusAlt].into(Status.Pending: Status)

        assert(result)(isRight(equalTo(StatusAlt.Pending: StatusAlt)))
      },
      test("converts all case objects in sealed trait") {
        val into = Into.derived[Status, StatusAlt]

        val active   = into.into(Status.Active: Status)
        val inactive = into.into(Status.Inactive: Status)
        val pending  = into.into(Status.Pending: Status)

        assert(active)(isRight(equalTo(StatusAlt.Active: StatusAlt))) &&
        assert(inactive)(isRight(equalTo(StatusAlt.Inactive: StatusAlt))) &&
        assert(pending)(isRight(equalTo(StatusAlt.Pending: StatusAlt)))
      }
    ),
    suite("Mixed Case Objects and Case Classes")(
      test("converts Success case object") {
        val result = Into.derived[Result, ResultAlt].into(Result.Success: Result)

        assert(result)(isRight(equalTo(ResultAlt.Success: ResultAlt)))
      },
      test("converts Failure case object") {
        val result = Into.derived[Result, ResultAlt].into(Result.Failure: Result)

        assert(result)(isRight(equalTo(ResultAlt.Failure: ResultAlt)))
      },
      test("converts Error case class") {
        val into   = Into.derived[Result, ResultAlt]
        val result = into.into(Result.Error("oops"): Result)

        assert(result)(isRight(equalTo(ResultAlt.Error("oops"): ResultAlt)))
      }
    )
  )
}
