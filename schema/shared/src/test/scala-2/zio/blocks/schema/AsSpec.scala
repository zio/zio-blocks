package zio.blocks.schema

import zio.test._

object AsSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int)
  case class User(name: String, age: Int)

  def spec: Spec[TestEnvironment, Any] = suite("As - Scala 2")(
    suite("Bidirectional numeric")(
      test("As[Int, Long]") {
        val as = As.derived[Int, Long]

        assertTrue(
          as.into(42) == Right(42L),
          as.from(42L) == Right(42)
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
    )
  )
}
