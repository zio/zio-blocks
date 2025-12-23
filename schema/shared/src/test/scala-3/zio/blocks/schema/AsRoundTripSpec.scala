package zio.blocks.schema

import scala.annotation.experimental
import zio.test._

@experimental
object AsRoundTripSpec extends ZIOSpecDefault {

  // Test types
  case class Person(name: String, age: Int)
  case class User(name: String, age: Int)

  sealed trait Color
  object Color {
    case object Red   extends Color
    case object Green extends Color
    case object Blue  extends Color
  }

  sealed trait Colour
  object Colour {
    case object Red   extends Colour
    case object Green extends Colour
    case object Blue  extends Colour
  }

  def spec: Spec[TestEnvironment, Any] = suite("As - Bidirectional Conversions")(
    suite("Identity conversions")(
      test("As[Int, Int]") {
        val as = As.derived[Int, Int]

        assertTrue(
          as.into(42) == Right(42),
          as.from(42) == Right(42)
        )
      }
    ),
    suite("Numeric widening (lossless bidirectional)")(
      test("As[Int, Long]") {
        val as = As.derived[Int, Long]

        assertTrue(
          as.into(42) == Right(42L),
          as.from(42L) == Right(42),
          as.into(Int.MaxValue) == Right(Int.MaxValue.toLong),
          as.from(Int.MaxValue.toLong) == Right(Int.MaxValue)
        )
      },
      test("round-trip Int -> Long -> Int") {
        val as       = As.derived[Int, Long]
        val original = 12345

        val roundTrip = for {
          long <- as.into(original)
          back <- as.from(long)
        } yield back

        assertTrue(roundTrip == Right(original))
      }
    ),
    suite("Product types")(
      test("As[Person, User]") {
        val as     = As.derived[Person, User]
        val person = Person("Alice", 30)

        assertTrue(
          as.into(person) == Right(User("Alice", 30)),
          as.from(User("Alice", 30)) == Right(Person("Alice", 30))
        )
      },
      test("round-trip Person -> User -> Person") {
        val as       = As.derived[Person, User]
        val original = Person("Bob", 25)

        val roundTrip = for {
          user <- as.into(original)
          back <- as.from(user)
        } yield back

        assertTrue(roundTrip == Right(original))
      }
    ),
    suite("Coproduct types")(
      test("As[Color, Colour]") {
        val as = As.derived[Color, Colour]

        assertTrue(
          as.into(Color.Red) == Right(Colour.Red),
          as.from(Colour.Red) == Right(Color.Red)
        )
      },
      test("round-trip Color -> Colour -> Color") {
        val as       = As.derived[Color, Colour]
        val original = Color.Green

        val roundTrip = for {
          colour <- as.into(original)
          back   <- as.from(colour)
        } yield back

        assertTrue(roundTrip == Right(original))
      }
    ),
    suite("Collections")(
      test("As[List[Int], Vector[Int]]") {
        val as   = As.derived[List[Int], Vector[Int]]
        val list = List(1, 2, 3)

        assertTrue(
          as.into(list) == Right(Vector(1, 2, 3)),
          as.from(Vector(1, 2, 3)) == Right(List(1, 2, 3))
        )
      },
      test("round-trip List -> Vector -> List") {
        val as       = As.derived[List[String], Vector[String]]
        val original = List("a", "b", "c")

        val roundTrip = for {
          vec  <- as.into(original)
          back <- as.from(vec)
        } yield back

        assertTrue(roundTrip == Right(original))
      }
    ),
    suite("Helper methods")(
      test("asInto and asIntoReverse") {
        val as = As.derived[Int, Long]

        val intoAB: Into[Int, Long] = as.asInto
        val intoBA: Into[Long, Int] = as.asIntoReverse

        assertTrue(
          intoAB.into(42) == Right(42L),
          intoBA.into(42L) == Right(42)
        )
      },
      test("intoOrThrow and fromOrThrow") {
        val as = As.derived[Int, Long]

        assertTrue(
          as.intoOrThrow(42) == 42L,
          as.fromOrThrow(42L) == 42
        )
      }
    )
  )
}
