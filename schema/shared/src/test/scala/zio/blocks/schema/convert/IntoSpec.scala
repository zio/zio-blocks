package zio.blocks.schema.convert

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

object IntoSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class PersonV2(name: String, age: Int, email: Option[String])
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class Employee(name: String, age: Int)
  object Employee {
    implicit val schema: Schema[Employee] = Schema.derived
  }

  def spec = suite("IntoSpec")(
    suite("identity conversion")(
      test("converts any value to itself") {
        val result = Into[Int, Int].into(42)
        assert(result)(isRight(equalTo(42)))
      },
      test("works with strings") {
        val result = Into[String, String].into("hello")
        assert(result)(isRight(equalTo("hello")))
      }
    ),
    suite("primitive widening conversions")(
      test("Byte to Short") {
        val result = Into[Byte, Short].into(42.toByte)
        assert(result)(isRight(equalTo(42.toShort)))
      },
      test("Byte to Int") {
        val result = Into[Byte, Int].into(42.toByte)
        assert(result)(isRight(equalTo(42)))
      },
      test("Byte to Long") {
        val result = Into[Byte, Long].into(42.toByte)
        assert(result)(isRight(equalTo(42L)))
      },
      test("Short to Int") {
        val result = Into[Short, Int].into(1000.toShort)
        assert(result)(isRight(equalTo(1000)))
      },
      test("Short to Long") {
        val result = Into[Short, Long].into(1000.toShort)
        assert(result)(isRight(equalTo(1000L)))
      },
      test("Int to Long") {
        val result = Into[Int, Long].into(1000000)
        assert(result)(isRight(equalTo(1000000L)))
      },
      test("Float to Double") {
        val result = Into[Float, Double].into(3.14f)
        assert(result)(isRight(isGreaterThan(3.13) && isLessThan(3.15)))
      },
      test("Char to Int") {
        val result = Into[Char, Int].into('A')
        assert(result)(isRight(equalTo(65)))
      }
    ),
    suite("primitive narrowing conversions")(
      test("Long to Int succeeds within range") {
        val result = Into[Long, Int].into(1000L)
        assert(result)(isRight(equalTo(1000)))
      },
      test("Long to Int fails outside range") {
        val result = Into[Long, Int].into(Long.MaxValue)
        assert(result)(isLeft)
      },
      test("Long to Short succeeds within range") {
        val result = Into[Long, Short].into(1000L)
        assert(result)(isRight(equalTo(1000.toShort)))
      },
      test("Long to Short fails outside range") {
        val result = Into[Long, Short].into(100000L)
        assert(result)(isLeft)
      },
      test("Long to Byte succeeds within range") {
        val result = Into[Long, Byte].into(100L)
        assert(result)(isRight(equalTo(100.toByte)))
      },
      test("Long to Byte fails outside range") {
        val result = Into[Long, Byte].into(1000L)
        assert(result)(isLeft)
      },
      test("Int to Short succeeds within range") {
        val result = Into[Int, Short].into(1000)
        assert(result)(isRight(equalTo(1000.toShort)))
      },
      test("Int to Short fails outside range") {
        val result = Into[Int, Short].into(100000)
        assert(result)(isLeft)
      },
      test("Int to Byte succeeds within range") {
        val result = Into[Int, Byte].into(100)
        assert(result)(isRight(equalTo(100.toByte)))
      },
      test("Int to Byte fails outside range") {
        val result = Into[Int, Byte].into(1000)
        assert(result)(isLeft)
      },
      test("Short to Byte succeeds within range") {
        val result = Into[Short, Byte].into(100.toShort)
        assert(result)(isRight(equalTo(100.toByte)))
      },
      test("Short to Byte fails outside range") {
        val result = Into[Short, Byte].into(1000.toShort)
        assert(result)(isLeft)
      },
      test("Double to Float succeeds within range") {
        val result = Into[Double, Float].into(3.14)
        assert(result)(isRight)
      },
      test("Double to Float fails outside range") {
        val result = Into[Double, Float].into(Double.MaxValue)
        assert(result)(isLeft)
      },
      test("Double to Long succeeds for whole numbers") {
        val result = Into[Double, Long].into(1000.0)
        assert(result)(isRight(equalTo(1000L)))
      },
      test("Double to Long fails for fractional numbers") {
        val result = Into[Double, Long].into(3.14)
        assert(result)(isLeft)
      },
      test("Double to Int succeeds for whole numbers within range") {
        val result = Into[Double, Int].into(1000.0)
        assert(result)(isRight(equalTo(1000)))
      },
      test("Double to Int fails for fractional numbers") {
        val result = Into[Double, Int].into(3.14)
        assert(result)(isLeft)
      }
    ),
    suite("Option conversions")(
      test("Some converts element") {
        val result = Into[Option[Int], Option[Long]].into(Some(42))
        assert(result)(isRight(isSome(equalTo(42L))))
      },
      test("None stays None") {
        val result = Into[Option[Int], Option[Long]].into(None)
        assert(result)(isRight(isNone))
      }
    ),
    suite("List conversions")(
      test("converts elements") {
        val result = Into[List[Int], List[Long]].into(List(1, 2, 3))
        assert(result)(isRight(equalTo(List(1L, 2L, 3L))))
      },
      test("empty list stays empty") {
        val result = Into[List[Int], List[Long]].into(List.empty)
        assert(result)(isRight(isEmpty))
      },
      test("fails if any element fails") {
        val result = Into[List[Long], List[Int]].into(List(1L, Long.MaxValue, 3L))
        assert(result)(isLeft)
      }
    ),
    suite("Vector conversions")(
      test("converts elements") {
        val result = Into[Vector[Int], Vector[Long]].into(Vector(1, 2, 3))
        assert(result)(isRight(equalTo(Vector(1L, 2L, 3L))))
      },
      test("empty vector stays empty") {
        val result = Into[Vector[Int], Vector[Long]].into(Vector.empty)
        assert(result)(isRight(isEmpty))
      }
    ),
    suite("Set conversions")(
      test("converts elements") {
        val result = Into[Set[Int], Set[Long]].into(Set(1, 2, 3))
        assert(result)(isRight(equalTo(Set(1L, 2L, 3L))))
      },
      test("empty set stays empty") {
        val result = Into[Set[Int], Set[Long]].into(Set.empty)
        assert(result)(isRight(isEmpty))
      }
    ),
    suite("collection type conversions")(
      test("List to Vector") {
        val result = Into[List[Int], Vector[Int]].into(List(1, 2, 3))
        assert(result)(isRight(equalTo(Vector(1, 2, 3))))
      },
      test("Vector to List") {
        val result = Into[Vector[Int], List[Int]].into(Vector(1, 2, 3))
        assert(result)(isRight(equalTo(List(1, 2, 3))))
      },
      test("List to Set") {
        val result = Into[List[Int], Set[Int]].into(List(1, 2, 2, 3))
        assert(result)(isRight(equalTo(Set(1, 2, 3))))
      },
      test("Vector to Set") {
        val result = Into[Vector[Int], Set[Int]].into(Vector(1, 2, 2, 3))
        assert(result)(isRight(equalTo(Set(1, 2, 3))))
      },
      test("Set to List") {
        val result = Into[Set[Int], List[Int]].into(Set(1, 2, 3))
        assert(result)(isRight(hasSize(equalTo(3))))
      },
      test("Set to Vector") {
        val result = Into[Set[Int], Vector[Int]].into(Set(1, 2, 3))
        assert(result)(isRight(hasSize(equalTo(3))))
      }
    ),
    suite("schema-based conversion")(
      test("converts between structurally compatible types") {
        val person = Person("Alice", 30)
        // Use explicit schema-based converter to avoid diverging implicits
        val converter: Into[Person, Employee] = Into.fromSchemas(Person.schema, Employee.schema)
        val result                            = converter.into(person)
        val expected                          = Employee("Alice", 30)
        assert(result)(isRight(equalTo(expected)))
      },
      test("converts Person to Employee via schema") {
        val person = Person("Bob", 25)
        // Use explicit schema-based converter to avoid diverging implicits
        val converter: Into[Person, Employee] = Into.fromSchemas(Person.schema, Employee.schema)
        val result                            = converter.into(person)
        assert(result)(isRight(equalTo(Employee("Bob", 25))))
      }
    ),
    suite("extension methods")(
      test("into method works") {
        val result: Either[SchemaError, Long] = 42.into[Long]
        assert(result)(isRight(equalTo(42L)))
      },
      test("into method with collection") {
        val result: Either[SchemaError, Vector[Int]] = List(1, 2, 3).into[Vector[Int]]
        assert(result)(isRight(equalTo(Vector(1, 2, 3))))
      }
    ),
    suite("factory methods")(
      test("fromFunction creates working converter") {
        val doubler = Into.fromFunction[Int, Int](x => Right(x * 2))
        assert(doubler.into(21))(isRight(equalTo(42)))
      },
      test("fromTotal creates working converter") {
        val toString = Into.fromTotal[Int, String](_.toString)
        assert(toString.into(42))(isRight(equalTo("42")))
      }
    )
  )
}
