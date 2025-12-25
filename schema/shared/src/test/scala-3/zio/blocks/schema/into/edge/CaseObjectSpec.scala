package zio.blocks.schema.into.edge

import zio.test._
import zio.blocks.schema._

// Sealed traits with case objects only (no case classes)
sealed trait Status
object Status {
  case object Active   extends Status
  case object Inactive extends Status
  case object Pending  extends Status
}

sealed trait State
object State {
  case object Active   extends State
  case object Inactive extends State
  case object Pending  extends State
}

// Sealed trait with single case object
sealed trait Singleton
object Singleton {
  case object Only extends Singleton
}

sealed trait SingletonCopy
object SingletonCopy {
  case object Only extends SingletonCopy
}

object CaseObjectSpec extends ZIOSpecDefault {

  def spec = suite("CaseObjectSpec")(
    suite("Case Objects Only (Sealed Traits)")(
      test("should convert sealed trait with case objects only") {
        val derivation = Into.derived[Status, State]
        val input      = Status.Active
        val result     = derivation.into(input)

        assertTrue(result == Right(State.Active))
      },
      test("should convert all case objects in sealed trait") {
        val derivation = Into.derived[Status, State]

        val active   = derivation.into(Status.Active)
        val inactive = derivation.into(Status.Inactive)
        val pending  = derivation.into(Status.Pending)

        assertTrue(
          active == Right(State.Active) &&
            inactive == Right(State.Inactive) &&
            pending == Right(State.Pending)
        )
      },
      test("should convert sealed trait with single case object") {
        val derivation = Into.derived[Singleton, SingletonCopy]
        val input      = Singleton.Only
        val result     = derivation.into(input)

        assertTrue(result == Right(SingletonCopy.Only))
      },
      test("should convert case object to itself (identity)") {
        val derivation = Into.derived[Status, Status]
        val input      = Status.Active
        val result     = derivation.into(input)

        assertTrue(result == Right(Status.Active))
      },
      test("should handle case objects in nested coproducts") {
        sealed trait OuterNested
        object OuterNested {
          case object A extends OuterNested
          case object B extends OuterNested
        }

        sealed trait OuterNestedCopy
        object OuterNestedCopy {
          case object A extends OuterNestedCopy
          case object B extends OuterNestedCopy
        }

        sealed trait WrapperNested
        object WrapperNested {
          case class Contains(outer: OuterNested) extends WrapperNested
        }

        sealed trait WrapperNestedCopy
        object WrapperNestedCopy {
          case class Contains(outer: OuterNestedCopy) extends WrapperNestedCopy
        }

        val derivation = Into.derived[WrapperNested, WrapperNestedCopy]
        val input      = WrapperNested.Contains(OuterNested.A)
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        result.map { wrapper =>
          wrapper match {
            case WrapperNestedCopy.Contains(outer) =>
              assertTrue(outer == OuterNestedCopy.A)
          }
        }
      }
    )
  )
}
