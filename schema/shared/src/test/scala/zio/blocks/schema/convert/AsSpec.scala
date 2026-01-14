package zio.blocks.schema.convert

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

object AsSpec extends ZIOSpecDefault {

  case class Point2D(x: Int, y: Int)
  object Point2D {
    implicit val schema: Schema[Point2D] = Schema.derived
  }

  case class Coordinate(x: Int, y: Int)
  object Coordinate {
    implicit val schema: Schema[Coordinate] = Schema.derived
  }

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class User(name: String, age: Int)
  object User {
    implicit val schema: Schema[User] = Schema.derived
  }

  def spec = suite("AsSpec")(
    suite("identity conversion")(
      test("converts value to itself and back") {
        val as     = As[Int, Int]
        val result = as.into(42)
        assert(result)(isRight(equalTo(42)))
      },
      test("from returns the same value") {
        val as     = As[String, String]
        val result = as.from("hello")
        assert(result)(isRight(equalTo("hello")))
      }
    ),
    suite("primitive bidirectional conversions")(
      test("Byte <-> Short round-trip") {
        val as        = As[Byte, Short]
        val original  = 42.toByte
        val converted = as.into(original)
        val roundTrip = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Byte <-> Int round-trip") {
        val as        = As[Byte, Int]
        val original  = 100.toByte
        val converted = as.into(original)
        val roundTrip = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Byte <-> Long round-trip") {
        val as        = As[Byte, Long]
        val original  = (-50).toByte
        val converted = as.into(original)
        val roundTrip = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Short <-> Int round-trip") {
        val as        = As[Short, Int]
        val original  = 1000.toShort
        val converted = as.into(original)
        val roundTrip = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Short <-> Long round-trip") {
        val as        = As[Short, Long]
        val original  = (-1000).toShort
        val converted = as.into(original)
        val roundTrip = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Int <-> Long round-trip") {
        val as        = As[Int, Long]
        val original  = 1000000
        val converted = as.into(original)
        val roundTrip = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Float <-> Double round-trip") {
        val as        = As[Float, Double]
        val original  = 3.14f
        val converted = as.into(original)
        val roundTrip = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Char <-> Int round-trip") {
        val as        = As[Char, Int]
        val original  = 'Z'
        val converted = as.into(original)
        val roundTrip = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("narrowing from B to A fails for out-of-range values")(
      test("Short -> Byte fails for large Short") {
        val as     = As[Byte, Short]
        val result = as.from(1000.toShort)
        assert(result)(isLeft)
      },
      test("Int -> Byte fails for large Int") {
        val as     = As[Byte, Int]
        val result = as.from(1000)
        assert(result)(isLeft)
      },
      test("Long -> Int fails for Long.MaxValue") {
        val as     = As[Int, Long]
        val result = as.from(Long.MaxValue)
        assert(result)(isLeft)
      },
      test("Int -> Char fails for negative Int") {
        val as     = As[Char, Int]
        val result = as.from(-1)
        assert(result)(isLeft)
      },
      test("Double -> Float fails for Double.MaxValue") {
        val as     = As[Float, Double]
        val result = as.from(Double.MaxValue)
        assert(result)(isLeft)
      }
    ),
    suite("reverse conversion")(
      test("reverse creates the opposite conversion") {
        val as      = As[Int, Long]
        val reverse = as.reverse
        val result  = reverse.into(42L)
        assert(result)(isRight(equalTo(42)))
      },
      test("double reverse is equivalent to original") {
        val as            = As[Byte, Short]
        val original      = 50.toByte
        val doubleReverse = as.reverse.reverse
        val result        = doubleReverse.into(original)
        assert(result)(isRight(equalTo(original.toShort)))
      }
    ),
    suite("Option conversions")(
      test("Some converts and round-trips") {
        val as        = As[Option[Int], Option[Long]]
        val original  = Some(42)
        val converted = as.into(original)
        val roundTrip = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("None round-trips") {
        val as: As[Option[Int], Option[Long]] = As[Option[Int], Option[Long]]
        val original: Option[Int]             = None
        val converted                         = as.into(original)
        val roundTrip                         = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("List conversions")(
      test("List converts and round-trips") {
        val as        = As[List[Int], List[Long]]
        val original  = List(1, 2, 3)
        val converted = as.into(original)
        val roundTrip = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("empty List round-trips") {
        val as        = As[List[Int], List[Long]]
        val original  = List.empty[Int]
        val converted = as.into(original)
        val roundTrip = converted.flatMap(as.from)
        assert(roundTrip)(isRight(isEmpty))
      }
    ),
    suite("Vector conversions")(
      test("Vector converts and round-trips") {
        val as        = As[Vector[Int], Vector[Long]]
        val original  = Vector(1, 2, 3)
        val converted = as.into(original)
        val roundTrip = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("List <-> Vector conversions")(
      test("List to Vector and back") {
        val as        = As[List[Int], Vector[Int]]
        val original  = List(1, 2, 3)
        val converted = as.into(original)
        val roundTrip = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("Vector to List via reverse") {
        val as        = As[List[Int], Vector[Int]].reverse
        val original  = Vector(1, 2, 3)
        val converted = as.into(original)
        val roundTrip = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("String <-> Char collection conversions")(
      test("String to List[Char] round-trip") {
        val as        = As[String, List[Char]]
        val original  = "hello"
        val converted = as.into(original)
        val roundTrip = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("String to Vector[Char] round-trip") {
        val as        = As[String, Vector[Char]]
        val original  = "world"
        val converted = as.into(original)
        val roundTrip = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("schema-based conversion")(
      test("converts between structurally compatible case classes") {
        val point = Point2D(10, 20)
        // Use explicit schema-based converter to avoid diverging implicits
        val as: As[Point2D, Coordinate] = As.fromSchemas(Point2D.schema, Coordinate.schema)
        val converted                   = as.into(point)
        val expected                    = Coordinate(10, 20)
        assert(converted)(isRight(equalTo(expected)))
      },
      test("round-trips between compatible case classes") {
        val original = Person("Alice", 30)
        // Use explicit schema-based converter to avoid diverging implicits
        val as: As[Person, User] = As.fromSchemas(Person.schema, User.schema)
        val converted            = as.into(original)
        val roundTrip            = converted.flatMap(as.from)
        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("extension methods")(
      test("as method works") {
        val result: Either[SchemaError, Long] = 42.as[Long]
        assert(result)(isRight(equalTo(42L)))
      },
      test("from method works") {
        val result: Either[SchemaError, Int] = 42L.from[Int]
        assert(result)(isRight(equalTo(42)))
      },
      test("from fails for out-of-range") {
        val result: Either[SchemaError, Int] = Long.MaxValue.from[Int]
        assert(result)(isLeft)
      }
    ),
    suite("factory methods")(
      test("fromFunctions creates working bidirectional converter") {
        val as = As.fromFunctions[Int, String](
          i => Right(i.toString),
          s => s.toIntOption.toRight(SchemaError.expectationMismatch(Nil, s"Cannot parse $s as Int"))
        )
        assert(as.into(42))(isRight(equalTo("42"))) &&
        assert(as.from("42"))(isRight(equalTo(42))) &&
        assert(as.from("not a number"))(isLeft)
      },
      test("fromTotal creates working bidirectional converter") {
        val as = As.fromTotal[Int, (Int, Int)](i => (i, i), t => t._1)
        assert(as.into(5))(isRight(equalTo((5, 5)))) &&
        assert(as.from((10, 20)))(isRight(equalTo(10)))
      },
      test("fromIntos creates working bidirectional converter") {
        val intoAB = Into.fromTotal[Int, String](_.toString)
        val intoBA = Into.fromFunction[String, Int](s =>
          s.toIntOption.toRight(SchemaError.expectationMismatch(Nil, s"Cannot parse $s"))
        )
        val as = As.fromIntos(intoAB, intoBA)
        assert(as.into(42))(isRight(equalTo("42"))) &&
        assert(as.from("42"))(isRight(equalTo(42)))
      }
    ),
    suite("laws")(
      test("round-trip law: from(into(a)) == Right(a) for compatible values") {
        val as        = As[Int, Long]
        val original  = 12345
        val roundTrip = for {
          b <- as.into(original)
          a <- as.from(b)
        } yield a
        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("round-trip law: into(from(b)) == Right(b) for compatible values") {
        val as        = As[Int, Long]
        val original  = 12345L
        val roundTrip = for {
          a <- as.from(original)
          b <- as.into(a)
        } yield b
        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("reverse.reverse is equivalent to original") {
        val as      = As[Int, Long]
        val value   = 42
        val result1 = as.into(value)
        val result2 = as.reverse.reverse.into(value)
        assert(result1)(equalTo(result2))
      }
    )
  )
}
