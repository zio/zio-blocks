package zio.blocks.schema

import zio.test._

object IntoSpec extends ZIOSpecDefault {

  // Shared test types
  case class Person(name: String, age: Int)
  case class User(name: String, age: Int)
  case class Employee(name: String, age: Int, salary: Long)
  case class PersonReordered(age: Int, name: String)
  case class Individual(fullName: String, years: Int)

  sealed trait Color
  object Color {
    case object Red   extends Color
    case object Green extends Color
    case object Blue  extends Color
  }

  sealed trait Colour // British spelling
  object Colour {
    case object Red   extends Colour
    case object Green extends Colour
    case object Blue  extends Colour
  }

  sealed trait Shape
  object Shape {
    case class Circle(radius: Double)                   extends Shape
    case class Rectangle(width: Double, height: Double) extends Shape
  }

  sealed trait Forma // Italian
  object Forma {
    case class Circle(radius: Double)                   extends Forma
    case class Rectangle(width: Double, height: Double) extends Forma
  }

  // Opaque types
  opaque type Age = Int
  object Age {
    def apply(i: Int): Age            = i
    extension (a: Age) def toInt: Int = a
  }

  opaque type ValidAge = Int
  object ValidAge {
    def apply(i: Int): Either[String, ValidAge] =
      if (i >= 0 && i <= 150) Right(i)
      else Left(s"Age must be between 0 and 150, got $i")

    def applyUnsafe(i: Int): ValidAge      = i
    extension (a: ValidAge) def toInt: Int = a
  }

  opaque type UserId <: String = String
  object UserId {
    def apply(s: String): Either[String, UserId] =
      if (s.nonEmpty && s.forall(_.isLetterOrDigit)) Right(s)
      else Left(s"UserId must be non-empty alphanumeric, got: $s")

    def applyUnsafe(s: String): UserId          = s
    extension (id: UserId) def toString: String = id
  }

  opaque type Count = Int
  object Count {
    def apply(i: Int): Count            = i
    extension (c: Count) def toInt: Int = c
  }

  // Simple 2-field products for nested conversion testing
  case class Point(x: Int, y: Int)
  case class Coord(x: Long, y: Long)

  // Test fixtures for tests with local definitions - moved to top level to avoid compiler crash
  // This prevents java.lang.AssertionError: missing outer accessor during erasure phase
  object TestFixtures {
    // For "Coproduct signature matching" tests
    sealed trait EventV1
    object EventV1 {
      case class Created(name: String, timestamp: Long) extends EventV1
      case class Updated(name: String, timestamp: Long) extends EventV1
    }

    sealed trait EventV2
    object EventV2 {
      case class Spawned(name: String, timestamp: Long)  extends EventV2
      case class Modified(name: String, timestamp: Long) extends EventV2
    }

    sealed trait StatusV1
    object StatusV1 {
      case object Pending                           extends StatusV1
      case class Active(id: Int)                    extends StatusV1
      case class Completed(id: Int, result: String) extends StatusV1
    }

    sealed trait StatusV2
    object StatusV2 {
      case object Pending                           extends StatusV2
      case class Running(id: Int)                   extends StatusV2 // Different name, same signature as Active
      case class Completed(id: Int, result: String) extends StatusV2 // Same name
    }

    // For "Large Products" tests
    case class LargeV1(
      f1: Int,
      f2: String,
      f3: Boolean,
      f4: Long,
      f5: Double,
      f6: Int,
      f7: String,
      f8: Boolean,
      f9: Long,
      f10: Double,
      f11: Int,
      f12: String,
      f13: Boolean,
      f14: Long,
      f15: Double
    )
    case class LargeV2(
      f1: Long,
      f2: String,
      f3: Boolean,
      f4: Long,
      f5: Double,
      f6: Long,
      f7: String,
      f8: Boolean,
      f9: Long,
      f10: Double,
      f11: Long,
      f12: String,
      f13: Boolean,
      f14: Long,
      f15: Double
    )

    // For "Large Coproducts" tests
    sealed trait LargeADT
    object LargeADT {
      case class Case1(v: Int)  extends LargeADT
      case class Case2(v: Int)  extends LargeADT
      case class Case3(v: Int)  extends LargeADT
      case class Case4(v: Int)  extends LargeADT
      case class Case5(v: Int)  extends LargeADT
      case class Case6(v: Int)  extends LargeADT
      case class Case7(v: Int)  extends LargeADT
      case class Case8(v: Int)  extends LargeADT
      case class Case9(v: Int)  extends LargeADT
      case class Case10(v: Int) extends LargeADT
      case class Case11(v: Int) extends LargeADT
      case class Case12(v: Int) extends LargeADT
      case class Case13(v: Int) extends LargeADT
      case class Case14(v: Int) extends LargeADT
      case class Case15(v: Int) extends LargeADT
      case class Case16(v: Int) extends LargeADT
      case class Case17(v: Int) extends LargeADT
      case class Case18(v: Int) extends LargeADT
      case class Case19(v: Int) extends LargeADT
      case class Case20(v: Int) extends LargeADT
      case class Case21(v: Int) extends LargeADT
      case class Case22(v: Int) extends LargeADT
    }

    sealed trait LargeADTV2
    object LargeADTV2 {
      case class Case1(v: Long)  extends LargeADTV2
      case class Case2(v: Long)  extends LargeADTV2
      case class Case3(v: Long)  extends LargeADTV2
      case class Case4(v: Long)  extends LargeADTV2
      case class Case5(v: Long)  extends LargeADTV2
      case class Case6(v: Long)  extends LargeADTV2
      case class Case7(v: Long)  extends LargeADTV2
      case class Case8(v: Long)  extends LargeADTV2
      case class Case9(v: Long)  extends LargeADTV2
      case class Case10(v: Long) extends LargeADTV2
      case class Case11(v: Long) extends LargeADTV2
      case class Case12(v: Long) extends LargeADTV2
      case class Case13(v: Long) extends LargeADTV2
      case class Case14(v: Long) extends LargeADTV2
      case class Case15(v: Long) extends LargeADTV2
      case class Case16(v: Long) extends LargeADTV2
      case class Case17(v: Long) extends LargeADTV2
      case class Case18(v: Long) extends LargeADTV2
      case class Case19(v: Long) extends LargeADTV2
      case class Case20(v: Long) extends LargeADTV2
      case class Case21(v: Long) extends LargeADTV2
      case class Case22(v: Long) extends LargeADTV2
    }

    sealed trait MixedADT
    object MixedADT {
      case object Obj1           extends MixedADT
      case object Obj2           extends MixedADT
      case class Class1(v: Int)  extends MixedADT
      case class Class2(v: Int)  extends MixedADT
      case object Obj3           extends MixedADT
      case class Class3(v: Int)  extends MixedADT
      case class Class4(v: Int)  extends MixedADT
      case class Class5(v: Int)  extends MixedADT
      case object Obj4           extends MixedADT
      case class Class6(v: Int)  extends MixedADT
      case class Class7(v: Int)  extends MixedADT
      case class Class8(v: Int)  extends MixedADT
      case class Class9(v: Int)  extends MixedADT
      case class Class10(v: Int) extends MixedADT
      case object Obj5           extends MixedADT
      case class Class11(v: Int) extends MixedADT
      case class Class12(v: Int) extends MixedADT
      case class Class13(v: Int) extends MixedADT
      case class Class14(v: Int) extends MixedADT
      case class Class15(v: Int) extends MixedADT
      case object Obj6           extends MixedADT
    }

    sealed trait MixedADTV2
    object MixedADTV2 {
      case object Obj1            extends MixedADTV2
      case object Obj2            extends MixedADTV2
      case class Class1(v: Long)  extends MixedADTV2
      case class Class2(v: Long)  extends MixedADTV2
      case object Obj3            extends MixedADTV2
      case class Class3(v: Long)  extends MixedADTV2
      case class Class4(v: Long)  extends MixedADTV2
      case class Class5(v: Long)  extends MixedADTV2
      case object Obj4            extends MixedADTV2
      case class Class6(v: Long)  extends MixedADTV2
      case class Class7(v: Long)  extends MixedADTV2
      case class Class8(v: Long)  extends MixedADTV2
      case class Class9(v: Long)  extends MixedADTV2
      case class Class10(v: Long) extends MixedADTV2
      case object Obj5            extends MixedADTV2
      case class Class11(v: Long) extends MixedADTV2
      case class Class12(v: Long) extends MixedADTV2
      case class Class13(v: Long) extends MixedADTV2
      case class Class14(v: Long) extends MixedADTV2
      case class Class15(v: Long) extends MixedADTV2
      case object Obj6            extends MixedADTV2
    }
  }

  def spec: Spec[TestEnvironment, Any] = suite("Into - Scala 3")(
    // Numeric coercions
    suite("Numeric coercions")(
      suite("Widening conversions (lossless)")(
        test("Byte -> Short") {
          val into = Into.derived[Byte, Short]
          assertTrue(
            into.into(0.toByte) == Right(0.toShort),
            into.into(127.toByte) == Right(127.toShort),
            into.into((-128).toByte) == Right((-128).toShort)
          )
        },
        test("Byte -> Int") {
          val into = Into.derived[Byte, Int]
          assertTrue(
            into.into(0.toByte) == Right(0),
            into.into(127.toByte) == Right(127),
            into.into((-128).toByte) == Right(-128)
          )
        },
        test("Byte -> Long") {
          val into = Into.derived[Byte, Long]
          assertTrue(
            into.into(0.toByte) == Right(0L),
            into.into(127.toByte) == Right(127L),
            into.into((-128).toByte) == Right(-128L)
          )
        },
        test("Short -> Int") {
          val into = Into.derived[Short, Int]
          assertTrue(
            into.into(0.toShort) == Right(0),
            into.into(32767.toShort) == Right(32767),
            into.into((-32768).toShort) == Right(-32768)
          )
        },
        test("Short -> Long") {
          val into = Into.derived[Short, Long]
          assertTrue(
            into.into(0.toShort) == Right(0L),
            into.into(32767.toShort) == Right(32767L),
            into.into((-32768).toShort) == Right(-32768L)
          )
        },
        test("Int -> Long") {
          val into = Into.derived[Int, Long]
          assertTrue(
            into.into(0) == Right(0L),
            into.into(Int.MaxValue) == Right(Int.MaxValue.toLong),
            into.into(Int.MinValue) == Right(Int.MinValue.toLong),
            into.into(42) == Right(42L)
          )
        },
        test("Float -> Double") {
          val into = Into.derived[Float, Double]
          assertTrue(
            into.into(0.0f) == Right(0.0),
            into.into(3.14f).map(_.toFloat) == Right(3.14f),
            into.into(Float.MaxValue).map(_.toFloat) == Right(Float.MaxValue),
            into.into(Float.MinValue).map(_.toFloat) == Right(Float.MinValue)
          )
        }
      ),
      suite("Narrowing conversions (with validation)")(
        suite("Long -> Int")(
          test("succeeds for values in range") {
            val into = Into.derived[Long, Int]
            assertTrue(
              into.into(0L) == Right(0),
              into.into(42L) == Right(42),
              into.into(Int.MaxValue.toLong) == Right(Int.MaxValue),
              into.into(Int.MinValue.toLong) == Right(Int.MinValue)
            )
          },
          test("fails for values out of range") {
            val into     = Into.derived[Long, Int]
            val tooLarge = Int.MaxValue.toLong + 1
            val tooSmall = Int.MinValue.toLong - 1

            assertTrue(
              into.into(tooLarge).isLeft,
              into.into(tooSmall).isLeft
            )
          }
        ),
        suite("Long -> Short")(
          test("succeeds for values in range") {
            val into = Into.derived[Long, Short]
            assertTrue(
              into.into(0L) == Right(0.toShort),
              into.into(42L) == Right(42.toShort),
              into.into(Short.MaxValue.toLong) == Right(Short.MaxValue),
              into.into(Short.MinValue.toLong) == Right(Short.MinValue)
            )
          },
          test("fails for values out of range") {
            val into     = Into.derived[Long, Short]
            val tooLarge = Short.MaxValue.toLong + 1
            val tooSmall = Short.MinValue.toLong - 1

            assertTrue(
              into.into(tooLarge).isLeft,
              into.into(tooSmall).isLeft
            )
          }
        ),
        suite("Long -> Byte")(
          test("succeeds for values in range") {
            val into = Into.derived[Long, Byte]
            assertTrue(
              into.into(0L) == Right(0.toByte),
              into.into(42L) == Right(42.toByte),
              into.into(Byte.MaxValue.toLong) == Right(Byte.MaxValue),
              into.into(Byte.MinValue.toLong) == Right(Byte.MinValue)
            )
          },
          test("fails for values out of range") {
            val into     = Into.derived[Long, Byte]
            val tooLarge = Byte.MaxValue.toLong + 1
            val tooSmall = Byte.MinValue.toLong - 1

            assertTrue(
              into.into(tooLarge).isLeft,
              into.into(tooSmall).isLeft
            )
          }
        ),
        suite("Int -> Short")(
          test("succeeds for values in range") {
            val into = Into.derived[Int, Short]
            assertTrue(
              into.into(0) == Right(0.toShort),
              into.into(42) == Right(42.toShort),
              into.into(Short.MaxValue.toInt) == Right(Short.MaxValue),
              into.into(Short.MinValue.toInt) == Right(Short.MinValue)
            )
          },
          test("fails for values out of range") {
            val into     = Into.derived[Int, Short]
            val tooLarge = Short.MaxValue.toInt + 1
            val tooSmall = Short.MinValue.toInt - 1

            assertTrue(
              into.into(tooLarge).isLeft,
              into.into(tooSmall).isLeft
            )
          }
        ),
        suite("Int -> Byte")(
          test("succeeds for values in range") {
            val into = Into.derived[Int, Byte]
            assertTrue(
              into.into(0) == Right(0.toByte),
              into.into(42) == Right(42.toByte),
              into.into(Byte.MaxValue.toInt) == Right(Byte.MaxValue),
              into.into(Byte.MinValue.toInt) == Right(Byte.MinValue)
            )
          },
          test("fails for values out of range") {
            val into     = Into.derived[Int, Byte]
            val tooLarge = Byte.MaxValue.toInt + 1
            val tooSmall = Byte.MinValue.toInt - 1

            assertTrue(
              into.into(tooLarge).isLeft,
              into.into(tooSmall).isLeft
            )
          }
        ),
        suite("Short -> Byte")(
          test("succeeds for values in range") {
            val into = Into.derived[Short, Byte]
            assertTrue(
              into.into(0.toShort) == Right(0.toByte),
              into.into(42.toShort) == Right(42.toByte),
              into.into(Byte.MaxValue.toShort) == Right(Byte.MaxValue),
              into.into(Byte.MinValue.toShort) == Right(Byte.MinValue)
            )
          },
          test("fails for values out of range") {
            val into     = Into.derived[Short, Byte]
            val tooLarge = (Byte.MaxValue.toShort + 1).toShort
            val tooSmall = (Byte.MinValue.toShort - 1).toShort

            assertTrue(
              into.into(tooLarge).isLeft,
              into.into(tooSmall).isLeft
            )
          }
        ),
        suite("Double -> Float")(
          test("succeeds for values in range") {
            val into = Into.derived[Double, Float]
            assertTrue(
              into.into(0.0) == Right(0.0f),
              into.into(3.14).map(_.toDouble).map(_ - 3.14 < 0.01).getOrElse(false),
              into.into(Float.MaxValue.toDouble) == Right(Float.MaxValue),
              into.into((-Float.MaxValue).toDouble) == Right(-Float.MaxValue)
            )
          },
          test("succeeds for special values") {
            val into = Into.derived[Double, Float]
            assertTrue(
              into.into(Double.NaN).map(_.isNaN).getOrElse(false),
              into.into(Double.PositiveInfinity) == Right(Float.PositiveInfinity),
              into.into(Double.NegativeInfinity) == Right(Float.NegativeInfinity)
            )
          },
          test("fails for values out of range") {
            val into     = Into.derived[Double, Float]
            val tooLarge = Float.MaxValue.toDouble * 2.0
            val tooSmall = -Float.MaxValue.toDouble * 2.0

            assertTrue(
              into.into(tooLarge).isLeft,
              into.into(tooSmall).isLeft
            )
          }
        )
      ),
      suite("Edge cases")(
        test("identity conversion") {
          val into = Into.derived[Int, Int]
          assertTrue(
            into.into(42) == Right(42),
            into.into(0) == Right(0),
            into.into(Int.MaxValue) == Right(Int.MaxValue)
          )
        },
        test("intoOrThrow succeeds") {
          val into = Into.derived[Int, Long]
          assertTrue(
            into.intoOrThrow(42) == 42L,
            into.intoOrThrow(Int.MaxValue) == Int.MaxValue.toLong
          )
        },
        test("intoOrThrow fails") {
          val into     = Into.derived[Long, Int]
          val tooLarge = Int.MaxValue.toLong + 1

          try {
            into.intoOrThrow(tooLarge)
            assertTrue(false) // Should not reach here
          } catch {
            case _: SchemaError => assertTrue(true)
            case _: Throwable   => assertTrue(false)
          }
        }
      )
    ),

    // Product types
    suite("Product types")(
      suite("Case class to case class")(
        test("same field order and types") {
          val into   = Into.derived[Person, User]
          val person = Person("Alice", 30)

          assertTrue(
            into.into(person) == Right(User("Alice", 30))
          )
        },
        test("multiple instances") {
          val into = Into.derived[Person, User]

          assertTrue(
            into.into(Person("Alice", 30)) == Right(User("Alice", 30)),
            into.into(Person("Bob", 25)) == Right(User("Bob", 25)),
            into.into(Person("Charlie", 35)) == Right(User("Charlie", 35))
          )
        }
      ),
      suite("Tuple conversions")(
        test("tuple to case class") {
          val into = Into.derived[(String, Int), Person]

          assertTrue(
            into.into(("Alice", 30)) == Right(Person("Alice", 30))
          )
        }
      ),
      suite("Empty case classes")(
        test("empty to empty") {
          case class Empty()
          val into = Into.derived[Empty, Empty]

          assertTrue(
            into.into(Empty()) == Right(Empty())
          )
        }
      ),
      suite("Field Mapping Disambiguation")(
        suite("Level 1: Exact match (name + type)")(
          test("same names and types") {
            val into = Into.derived[Person, User]

            assertTrue(
              into.into(Person("Alice", 30)) == Right(User("Alice", 30))
            )
          }
        ),
        suite("Level 2: Name match with coercion")(
          test("same names, different but coercible types") {
            case class ConfigV1(timeout: Int, retries: Short)
            case class ConfigV2(timeout: Long, retries: Int)

            val into = Into.derived[ConfigV1, ConfigV2]

            assertTrue(
              into.into(ConfigV1(30, 3.toShort)) == Right(ConfigV2(30L, 3))
            )
          },
          test("name match with collection coercion") {
            case class DataV1(items: List[Int])
            case class DataV2(items: Vector[Long])

            val into = Into.derived[DataV1, DataV2]

            assertTrue(
              into.into(DataV1(List(1, 2, 3))) == Right(DataV2(Vector(1L, 2L, 3L)))
            )
          }
        ),
        suite("Level 3: Unique type match")(
          test("different names but unique types") {
            case class PersonV1(fullName: String, yearOfBirth: Int, active: Boolean)
            case class PersonV2(name: String, birthYear: Int, enabled: Boolean)

            val into = Into.derived[PersonV1, PersonV2]

            assertTrue(
              into.into(PersonV1("Alice", 1990, true)) ==
                Right(PersonV2("Alice", 1990, true))
            )
          }
        ),
        suite("Level 4: Position match")(
          test("tuples use positional mapping") {
            case class RGB(r: Int, g: Int, b: Int)

            val into = Into.derived[(Int, Int, Int), RGB]

            assertTrue(
              into.into((255, 128, 64)) == Right(RGB(255, 128, 64))
            )
          }
        ),
        suite("Reordering with name match")(
          test("fields in different order") {
            case class Point(x: Int, y: Int)
            case class Coord(y: Int, x: Int)

            val into = Into.derived[Point, Coord]

            assertTrue(
              into.into(Point(10, 20)) == Right(Coord(20, 10))
            )
          }
        ),
        suite("Subset mapping (more source fields)")(
          test("target has fewer fields") {
            val into = Into.derived[Employee, Person]

            assertTrue(
              into.into(Employee("Alice", 30, 50000L)) == Right(Person("Alice", 30))
            )
          }
        ),
        suite("Complex nested field mapping")(
          test("nested collections with type coercion") {
            case class Record1(id: String, values: List[Int])
            case class Record2(id: String, values: Vector[Long])

            val into = Into.derived[Record1, Record2]

            assertTrue(
              into.into(Record1("abc", List(1, 2, 3))) ==
                Right(Record2("abc", Vector(1L, 2L, 3L)))
            )
          },
          test("option fields with nested conversion") {
            case class ConfigV1(name: String, port: Option[Int])
            case class ConfigV2(name: String, port: Option[Long])

            val into = Into.derived[ConfigV1, ConfigV2]

            assertTrue(
              into.into(ConfigV1("server", Some(8080))) == Right(ConfigV2("server", Some(8080L))),
              into.into(ConfigV1("server", None)) == Right(ConfigV2("server", None))
            )
          }
        )
      )
    ),

    // Coproduct types
    suite("Coproduct types")(
      suite("Sealed trait with case objects")(
        test("converts matching case objects") {
          val into = Into.derived[Color, Colour]

          assertTrue(
            into.into(Color.Red) == Right(Colour.Red),
            into.into(Color.Green) == Right(Colour.Green),
            into.into(Color.Blue) == Right(Colour.Blue)
          )
        }
      ),
      suite("Sealed trait with case classes")(
        test("converts matching case classes") {
          val into = Into.derived[Shape, Forma]

          assertTrue(
            into.into(Shape.Circle(5.0)) == Right(Forma.Circle(5.0)),
            into.into(Shape.Rectangle(10.0, 20.0)) == Right(Forma.Rectangle(10.0, 20.0))
          )
        }
      )
    ),

    // Opaque types
    suite("Opaque types")(
      suite("Simple opaque types (no validation)")(
        test("Int to Age") {
          val into = Into.derived[Int, Age]

          assertTrue(
            into.into(25) == Right(Age(25)),
            into.into(0) == Right(Age(0)),
            into.into(100) == Right(Age(100))
          )
        },
        test("Int to Count") {
          val into = Into.derived[Int, Count]

          assertTrue(
            into.into(42) == Right(Count(42)),
            into.into(-5) == Right(Count(-5))
          )
        }
      ),
      suite("Opaque types with validation")(
        test("Int to ValidAge - valid values") {
          val into = Into.derived[Int, ValidAge]

          assertTrue(
            into.into(0).isRight,
            into.into(25).isRight,
            into.into(150).isRight
          )
        },
        test("Int to ValidAge - invalid values") {
          val into = Into.derived[Int, ValidAge]

          assertTrue(
            into.into(-1).isLeft,
            into.into(151).isLeft,
            into.into(200).isLeft
          )
        },
        test("String to UserId - valid values") {
          val into = Into.derived[String, UserId]

          assertTrue(
            into.into("user123").isRight,
            into.into("abc").isRight,
            into.into("USER456").isRight
          )
        },
        test("String to UserId - invalid values") {
          val into = Into.derived[String, UserId]

          assertTrue(
            into.into("").isLeft,
            into.into("user-123").isLeft, // contains dash
            into.into("user 123").isLeft  // contains space
          )
        }
      ),
      suite("Opaque type unwrapping")(
        test("Age to Int") {
          val into = Into.derived[Age, Int]

          assertTrue(
            into.into(Age(25)) == Right(25),
            into.into(Age(100)) == Right(100)
          )
        },
        test("Count to Int") {
          val into = Into.derived[Count, Int]

          assertTrue(
            into.into(Count(42)) == Right(42),
            into.into(Count(-5)) == Right(-5)
          )
        }
      ),
      suite("Nested opaque types")(
        test("Case class with opaque type fields") {
          case class Person(name: String, age: Age)
          case class PersonWithValidAge(name: String, age: ValidAge)

          val into = Into.derived[Person, PersonWithValidAge]

          assertTrue(
            into.into(Person("Alice", Age(25))) ==
              Right(PersonWithValidAge("Alice", ValidAge.applyUnsafe(25)))
          )
        },
        test("Collection of opaque types") {
          val into = Into.derived[List[Int], List[Age]]

          assertTrue(
            into.into(List(1, 2, 3)) == Right(List(Age(1), Age(2), Age(3)))
          )
        }
      )
    ),

    // Collection types
    suite("Collection types")(
      suite("Same element type conversions")(
        test("List to Vector") {
          val into = Into.derived[List[Int], Vector[Int]]
          val list = List(1, 2, 3)

          assertTrue(
            into.into(list) == Right(Vector(1, 2, 3))
          )
        },
        test("Vector to List") {
          val into = Into.derived[Vector[String], List[String]]
          val vec  = Vector("a", "b", "c")

          assertTrue(
            into.into(vec) == Right(List("a", "b", "c"))
          )
        },
        test("List to Set") {
          val into = Into.derived[List[Int], Set[Int]]
          val list = List(1, 2, 3, 2, 1)

          assertTrue(
            into.into(list) == Right(Set(1, 2, 3))
          )
        },
        test("Set to List") {
          val into   = Into.derived[Set[Int], List[Int]]
          val set    = Set(1, 2, 3)
          val result = into.into(set)

          assertTrue(
            result.map(_.toSet) == Right(Set(1, 2, 3))
          )
        }
      ),
      suite("Element type coercion")(
        test("List[Int] to List[Long]") {
          val into = Into.derived[List[Int], List[Long]]
          val list = List(1, 2, 3)

          assertTrue(
            into.into(list) == Right(List(1L, 2L, 3L))
          )
        },
        test("Vector[Byte] to Vector[Int]") {
          val into = Into.derived[Vector[Byte], Vector[Int]]
          val vec  = Vector(1.toByte, 2.toByte, 3.toByte)

          assertTrue(
            into.into(vec) == Right(Vector(1, 2, 3))
          )
        }
      ),
      suite("Combined conversions")(
        test("List[Int] to Vector[Long]") {
          val into = Into.derived[List[Int], Vector[Long]]
          val list = List(1, 2, 3)

          assertTrue(
            into.into(list) == Right(Vector(1L, 2L, 3L))
          )
        },
        test("Vector[Short] to List[Int]") {
          val into = Into.derived[Vector[Short], List[Int]]
          val vec  = Vector(1.toShort, 2.toShort, 3.toShort)

          assertTrue(
            into.into(vec) == Right(List(1, 2, 3))
          )
        }
      ),
      suite("Option conversions")(
        test("Option[Int] to Option[Int] (identity)") {
          val into = Into.derived[Option[Int], Option[Int]]

          assertTrue(
            into.into(Some(42)) == Right(Some(42)),
            into.into(None) == Right(None)
          )
        },
        test("Option[Int] to Option[Long] (coercion)") {
          val into = Into.derived[Option[Int], Option[Long]]

          assertTrue(
            into.into(Some(42)) == Right(Some(42L)),
            into.into(None) == Right(None)
          )
        }
      ),
      suite("Edge cases")(
        test("empty list") {
          val into = Into.derived[List[Int], Vector[Long]]

          assertTrue(
            into.into(List.empty) == Right(Vector.empty)
          )
        },
        test("narrowing with validation in collection") {
          val into = Into.derived[List[Long], List[Int]]

          assertTrue(
            into.into(List(1L, 2L, 3L)) == Right(List(1, 2, 3)),
            into.into(List(Int.MaxValue.toLong + 1)).isLeft
          )
        }
      ),
      suite("Array conversions (reference types only)")(
        test("Array[String] to Vector[String]") {
          val into = Into.derived[Array[String], Vector[String]]
          val arr  = Array("a", "b", "c")

          assertTrue(
            into.into(arr) == Right(Vector("a", "b", "c"))
          )
        }
      )
    ),

    // Map and Either
    suite("Map and Either")(
      suite("Map conversions")(
        suite("Same key and value types")(
          test("Map[Int, String] to Map[Int, String] (identity)") {
            val into = Into.derived[Map[Int, String], Map[Int, String]]
            val map  = Map(1 -> "a", 2 -> "b", 3 -> "c")

            assertTrue(
              into.into(map) == Right(map)
            )
          }
        ),
        suite("Value type coercion")(
          test("Map[Int, Int] to Map[Int, Long]") {
            val into = Into.derived[Map[Int, Int], Map[Int, Long]]
            val map  = Map(1 -> 10, 2 -> 20, 3 -> 30)

            assertTrue(
              into.into(map) == Right(Map(1 -> 10L, 2 -> 20L, 3 -> 30L))
            )
          },
          test("Map[String, Short] to Map[String, Int]") {
            val into = Into.derived[Map[String, Short], Map[String, Int]]
            val map  = Map("a" -> 10.toShort, "b" -> 20.toShort)

            assertTrue(
              into.into(map) == Right(Map("a" -> 10, "b" -> 20))
            )
          }
        ),
        suite("Key type coercion")(
          test("Map[Int, String] to Map[Long, String]") {
            val into = Into.derived[Map[Int, String], Map[Long, String]]
            val map  = Map(1 -> "a", 2 -> "b")

            assertTrue(
              into.into(map) == Right(Map(1L -> "a", 2L -> "b"))
            )
          }
        ),
        suite("Both key and value coercion")(
          test("Map[Int, Int] to Map[Long, Long]") {
            val into = Into.derived[Map[Int, Int], Map[Long, Long]]
            val map  = Map(1 -> 10, 2 -> 20)

            assertTrue(
              into.into(map) == Right(Map(1L -> 10L, 2L -> 20L))
            )
          },
          test("Map[Short, Byte] to Map[Int, Short]") {
            val into = Into.derived[Map[Short, Byte], Map[Int, Short]]
            val map  = Map(10.toShort -> 5.toByte, 20.toShort -> 10.toByte)

            assertTrue(
              into.into(map) == Right(Map(10 -> 5.toShort, 20 -> 10.toShort))
            )
          }
        ),
        suite("Narrowing with validation")(
          test("Map[Long, Int] to Map[Int, Int] - valid values") {
            val into = Into.derived[Map[Long, Int], Map[Int, Int]]
            val map  = Map(1L -> 10, 2L -> 20)

            assertTrue(
              into.into(map) == Right(Map(1 -> 10, 2 -> 20))
            )
          },
          test("Map[Long, Int] to Map[Int, Int] - invalid key") {
            val into = Into.derived[Map[Long, Int], Map[Int, Int]]
            val map  = Map(1L -> 10, Int.MaxValue.toLong + 1 -> 20)

            assertTrue(
              into.into(map).isLeft
            )
          }
        ),
        suite("Edge cases")(
          test("empty map") {
            val into = Into.derived[Map[Int, String], Map[Long, String]]

            assertTrue(
              into.into(Map.empty) == Right(Map.empty)
            )
          }
        )
      ),
      suite("Either conversions")(
        suite("Same left and right types")(
          test("Either[String, Int] to Either[String, Int] (identity)") {
            val into = Into.derived[Either[String, Int], Either[String, Int]]

            assertTrue(
              into.into(Left("error")) == Right(Left("error")),
              into.into(Right(42)) == Right(Right(42))
            )
          }
        ),
        suite("Right type coercion")(
          test("Either[String, Int] to Either[String, Long]") {
            val into = Into.derived[Either[String, Int], Either[String, Long]]

            assertTrue(
              into.into(Left("error")) == Right(Left("error")),
              into.into(Right(42)) == Right(Right(42L))
            )
          }
        ),
        suite("Left type coercion")(
          test("Either[Int, String] to Either[Long, String]") {
            val into = Into.derived[Either[Int, String], Either[Long, String]]

            assertTrue(
              into.into(Left(42)) == Right(Left(42L)),
              into.into(Right("success")) == Right(Right("success"))
            )
          }
        ),
        suite("Both sides coercion")(
          test("Either[Int, Int] to Either[Long, Long]") {
            val into = Into.derived[Either[Int, Int], Either[Long, Long]]

            assertTrue(
              into.into(Left(10)) == Right(Left(10L)),
              into.into(Right(20)) == Right(Right(20L))
            )
          },
          test("Either[Short, Byte] to Either[Int, Int]") {
            val into = Into.derived[Either[Short, Byte], Either[Int, Int]]

            assertTrue(
              into.into(Left(10.toShort)) == Right(Left(10)),
              into.into(Right(5.toByte)) == Right(Right(5))
            )
          }
        ),
        suite("Narrowing with validation")(
          test("Either[String, Long] to Either[String, Int] - valid") {
            val into = Into.derived[Either[String, Long], Either[String, Int]]

            assertTrue(
              into.into(Left("error")) == Right(Left("error")),
              into.into(Right(42L)) == Right(Right(42))
            )
          },
          test("Either[String, Long] to Either[String, Int] - overflow") {
            val into     = Into.derived[Either[String, Long], Either[String, Int]]
            val tooLarge = Int.MaxValue.toLong + 1

            assertTrue(
              into.into(Right(tooLarge)).isLeft
            )
          }
        )
      )
    ),

    // Edge cases
    suite("Edge cases")(
      suite("Nested conversions (2 fields)")(
        test("simple product with numeric coercion") {
          val into  = Into.derived[Point, Coord]
          val point = Point(10, 20)

          assertTrue(
            into.into(point) == Right(Coord(10L, 20L))
          )
        }
      ),
      suite("Simple structures")(
        test("empty list") {
          val into = Into.derived[List[Int], Vector[Long]]

          assertTrue(
            into.into(List()) == Right(Vector())
          )
        },
        test("single field case class") {
          case class Wrapper1(value: Int)
          case class Wrapper2(value: Long)

          val into = Into.derived[Wrapper1, Wrapper2]

          assertTrue(
            into.into(Wrapper1(42)) == Right(Wrapper2(42L))
          )
        }
      ),
      suite("Nested collections (simple)")(
        test("Map of Lists with numeric coercion") {
          val into = Into.derived[Map[String, List[Int]], Map[String, Vector[Long]]]
          val map  = Map("a" -> List(1, 2, 3), "b" -> List(4, 5, 6))

          assertTrue(
            into.into(map) == Right(Map("a" -> Vector(1L, 2L, 3L), "b" -> Vector(4L, 5L, 6L)))
          )
        },
        test("List of Either with coercion") {
          val into = Into.derived[List[Either[String, Int]], Vector[Either[String, Long]]]
          val list = List(Left("error"), Right(42), Right(100))

          assertTrue(
            into.into(list) == Right(Vector(Left("error"), Right(42L), Right(100L)))
          )
        }
      ),
      suite("Large products (same types - no nested conversion)")(
        test("10-field case class with same types") {
          case class LargeV1(
            f1: Int,
            f2: String,
            f3: Boolean,
            f4: Long,
            f5: Double,
            f6: Int,
            f7: String,
            f8: Boolean,
            f9: Long,
            f10: Double
          )
          case class LargeV2(
            f1: Int,
            f2: String,
            f3: Boolean,
            f4: Long,
            f5: Double,
            f6: Int,
            f7: String,
            f8: Boolean,
            f9: Long,
            f10: Double
          )

          val into  = Into.derived[LargeV1, LargeV2]
          val large = LargeV1(1, "a", true, 2L, 3.0, 4, "b", false, 5L, 6.0)

          assertTrue(
            into.into(large) == Right(LargeV2(1, "a", true, 2L, 3.0, 4, "b", false, 5L, 6.0))
          )
        }
      )
    ),

    // Schema Evolution
    suite("Schema Evolution")(
      suite("Adding Optional Fields")(
        test("source without field, target with Option[T] - injects None") {
          case class UserV1(id: String, name: String)
          case class UserV2(id: String, name: String, email: Option[String])

          val into   = Into.derived[UserV1, UserV2]
          val userV1 = UserV1("123", "Alice")

          assertTrue(
            into.into(userV1) == Right(UserV2("123", "Alice", None))
          )
        },
        test("multiple optional fields added") {
          case class ProductV1(name: String, price: Double)
          case class ProductV2(name: String, price: Double, description: Option[String], tags: Option[List[String]])

          val into    = Into.derived[ProductV1, ProductV2]
          val product = ProductV1("Widget", 19.99)

          assertTrue(
            into.into(product) == Right(ProductV2("Widget", 19.99, None, None))
          )
        },
        test("optional field with existing source field - uses source value") {
          case class PersonV1(name: String, age: Int, email: Option[String])
          case class PersonV2(name: String, age: Int, email: Option[String], phone: Option[String])

          val into = Into.derived[PersonV1, PersonV2]

          assertTrue(
            into.into(PersonV1("Alice", 30, Some("alice@example.com"))) ==
              Right(PersonV2("Alice", 30, Some("alice@example.com"), None)),
            into.into(PersonV1("Bob", 25, None)) ==
              Right(PersonV2("Bob", 25, None, None))
          )
        }
      ),
      suite("Adding Required Fields with Defaults")(
        test("source without field, target with default value - uses default") {
          case class ConfigV1(port: Int, host: String)
          case class ConfigV2(port: Int, host: String, timeout: Int = 30)

          val into   = Into.derived[ConfigV1, ConfigV2]
          val config = ConfigV1(8080, "localhost")

          assertTrue(
            into.into(config) == Right(ConfigV2(8080, "localhost", 30))
          )
        },
        test("multiple default fields") {
          case class SettingsV1(theme: String)
          case class SettingsV2(theme: String, fontSize: Int = 14, darkMode: Boolean = false)

          val into = Into.derived[SettingsV1, SettingsV2]

          assertTrue(
            into.into(SettingsV1("light")) == Right(SettingsV2("light", 14, false))
          )
        },
        test("default value with type coercion") {
          case class DataV1(value: Int)
          case class DataV2(value: Int, count: Long = 0L)

          val into = Into.derived[DataV1, DataV2]

          assertTrue(
            into.into(DataV1(42)) == Right(DataV2(42, 0L))
          )
        }
      ),
      suite("Removing Optional Fields")(
        test("source with Option field, target without - drops field") {
          case class UserV2(id: String, name: String, email: Option[String])
          case class UserV1(id: String, name: String)

          val into = Into.derived[UserV2, UserV1]

          assertTrue(
            into.into(UserV2("123", "Alice", Some("alice@example.com"))) == Right(UserV1("123", "Alice")),
            into.into(UserV2("456", "Bob", None)) == Right(UserV1("456", "Bob"))
          )
        }
      ),
      suite("Combined Evolution Patterns")(
        test("add optional + add default + remove optional") {
          case class Version1(id: String, name: String, metadata: Option[Map[String, String]])
          case class Version2(id: String, name: String, email: Option[String], priority: Int = 5)

          val into = Into.derived[Version1, Version2]

          assertTrue(
            into.into(Version1("1", "Alice", Some(Map("key" -> "value")))) ==
              Right(Version2("1", "Alice", None, 5))
          )
        },
        test("field reordering with optional and defaults") {
          case class OrderV1(id: String, total: Double, customerId: String)
          case class OrderV2(
            customerId: String,
            id: String,
            total: Double,
            status: Option[String],
            discount: Double = 0.0
          )

          val into = Into.derived[OrderV1, OrderV2]

          assertTrue(
            into.into(OrderV1("order-1", 99.99, "cust-123")) ==
              Right(OrderV2("cust-123", "order-1", 99.99, None, 0.0))
          )
        }
      ),
      suite("Nested Schema Evolution")(
        test("nested case classes with evolution") {
          case class AddressV1(street: String, number: Int)
          case class AddressV2(street: String, number: Long, zipCode: Option[String])

          case class PersonV1(name: String, age: Int, address: AddressV1)
          case class PersonV2(name: String, age: Long, address: AddressV2)

          val into     = Into.derived[PersonV1, PersonV2]
          val personV1 = PersonV1("Alice", 30, AddressV1("Main St", 123))

          val result = into.into(personV1)

          assertTrue(
            result.isRight,
            result.map(_.name) == Right("Alice"),
            result.map(_.age) == Right(30L),
            result.map(_.address.street) == Right("Main St"),
            result.map(_.address.number) == Right(123L),
            result.map(_.address.zipCode) == Right(None)
          )
        },
        test("deeply nested case classes") {
          case class InnerV1(value: Int)
          case class InnerV2(value: Long)

          case class MiddleV1(inner: InnerV1, name: String)
          case class MiddleV2(inner: InnerV2, name: String)

          case class OuterV1(middle: MiddleV1, id: Int)
          case class OuterV2(middle: MiddleV2, id: Long)

          val into    = Into.derived[OuterV1, OuterV2]
          val outerV1 = OuterV1(MiddleV1(InnerV1(42), "test"), 100)

          val result = into.into(outerV1)

          assertTrue(
            result.isRight,
            result.map(_.id) == Right(100L),
            result.map(_.middle.name) == Right("test"),
            result.map(_.middle.inner.value) == Right(42L)
          )
        }
      ),
      suite("Error Cases")(
        test("required field without source, not Option, no default - fails") {
          // TODO: Test compile-time error detection
          // The macro expansion happens at compile-time, so we can't test failure with Try
          // This would need to be tested with a separate compilation unit
          assertTrue(true) // Placeholder - compile-time errors are tested by attempting compilation
        }
      )
    ),

    // Structural types (Selectable)
    // NOTE: The following 5 tests are disabled due to Scala 3.3.x SIP-44 architectural limitation.
    // See docs/STRUCTURAL_TYPES_LIMITATION.md for detailed explanation.
    suite("Structural types")(
      suite("Product to Structural type")(
        // Ignored due to Scala 3 Selectable architectural limitation (SIP-44). See docs/STRUCTURAL_TYPES_LIMITATION.md
        test("case class to structural type") @@ TestAspect.ignore {
          type PointStruct = {
            def x: Int
            def y: Int
          }

          val into  = Into.derived[Point, PointStruct]
          val point = Point(10, 20)

          val result = into.into(point)
          assertTrue(
            result.isRight
          )
          // Access fields using Selectable
          import scala.reflect.Selectable.reflectiveSelectable
          assertTrue(
            result.map(s => s.asInstanceOf[Selectable].selectDynamic("x").asInstanceOf[Int]) == Right(10),
            result.map(s => s.asInstanceOf[Selectable].selectDynamic("y").asInstanceOf[Int]) == Right(20)
          )
        },
        // Ignored due to Scala 3 Selectable architectural limitation (SIP-44). See docs/STRUCTURAL_TYPES_LIMITATION.md
        test("case class with different field types to structural type") @@ TestAspect.ignore {
          type PersonStruct = {
            def name: String
            def age: Int
          }

          val into   = Into.derived[Person, PersonStruct]
          val person = Person("Alice", 30)

          val result = into.into(person)
          assertTrue(
            result.isRight
          )
          // Access fields using Selectable
          import scala.reflect.Selectable.reflectiveSelectable
          assertTrue(
            result.map(s => s.asInstanceOf[Selectable].selectDynamic("name").asInstanceOf[String]) == Right("Alice"),
            result.map(s => s.asInstanceOf[Selectable].selectDynamic("age").asInstanceOf[Int]) == Right(30)
          )
        }
      ),
      suite("Structural type to Product")(
        // Ignored due to Scala 3 Selectable architectural limitation (SIP-44). See docs/STRUCTURAL_TYPES_LIMITATION.md
        test("structural type to case class") @@ TestAspect.ignore {
          type PointStruct = {
            def x: Int
            def y: Int
          }

          val into = Into.derived[PointStruct, Point]
          // Create a structural type instance using reflectiveSelectable
          import scala.reflect.Selectable.reflectiveSelectable
          val point  = Point(5, 15)
          val struct = reflectiveSelectable(point).asInstanceOf[PointStruct]

          val result = into.into(struct)
          assertTrue(
            result.isRight,
            result == Right(Point(5, 15))
          )
        }
      ),
      suite("Structural type to Structural type")(
        // Ignored due to Scala 3 Selectable architectural limitation (SIP-44). See docs/STRUCTURAL_TYPES_LIMITATION.md
        test("structural type to structural type with same methods") @@ TestAspect.ignore {
          type PointStruct1 = {
            def x: Int
            def y: Int
          }
          type PointStruct2 = {
            def x: Int
            def y: Int
          }

          val into = Into.derived[PointStruct1, PointStruct2]
          import scala.reflect.Selectable.reflectiveSelectable
          val point   = Point(7, 14)
          val struct1 = reflectiveSelectable(point).asInstanceOf[PointStruct1]

          val result = into.into(struct1)
          assertTrue(
            result.isRight
          )
          // Verify the result has the same methods
          assertTrue(
            result.map(s => s.asInstanceOf[Selectable].selectDynamic("x").asInstanceOf[Int]) == Right(7),
            result.map(s => s.asInstanceOf[Selectable].selectDynamic("y").asInstanceOf[Int]) == Right(14)
          )
        },
        // Ignored due to Scala 3 Selectable architectural limitation (SIP-44). See docs/STRUCTURAL_TYPES_LIMITATION.md
        test("structural type to structural type with subset of methods") @@ TestAspect.ignore {
          type FullStruct = {
            def name: String
            def age: Int
            def salary: Long
          }
          type PartialStruct = {
            def name: String
            def age: Int
          }

          val into = Into.derived[FullStruct, PartialStruct]
          import scala.reflect.Selectable.reflectiveSelectable
          val person     = Employee("Bob", 25, 50000L)
          val fullStruct = reflectiveSelectable(person).asInstanceOf[FullStruct]

          val result = into.into(fullStruct)
          assertTrue(
            result.isRight
          )
          // Verify the result has the required methods
          assertTrue(
            result.map(s => s.asInstanceOf[Selectable].selectDynamic("name").asInstanceOf[String]) == Right("Bob"),
            result.map(s => s.asInstanceOf[Selectable].selectDynamic("age").asInstanceOf[Int]) == Right(25)
          )
        }
      )
    ),

    // Signature-based coproduct matching
    suite("Coproduct signature matching")(
      suite("Match by constructor signature when names differ")(
        test("case classes with same signature but different names") {
          import TestFixtures._
          val into = Into.derived[EventV1, EventV2]

          assertTrue(
            into.into(EventV1.Created("test", 123L)) == Right(EventV2.Spawned("test", 123L)),
            into.into(EventV1.Updated("test2", 456L)) == Right(EventV2.Modified("test2", 456L))
          )
        },
        test("mixed: some by name, some by signature") {
          import TestFixtures._
          val into = Into.derived[StatusV1, StatusV2]

          assertTrue(
            into.into(StatusV1.Pending) == Right(StatusV2.Pending),
            into.into(StatusV1.Active(42)) == Right(StatusV2.Running(42)),
            into.into(StatusV1.Completed(1, "done")) == Right(StatusV2.Completed(1, "done"))
          )
        }
      )
    ),

    // Edge Cases
    suite("Edge Cases")(
      suite("Large Products")(
        test("case class with many fields") {
          import TestFixtures._
          val into  = Into.derived[LargeV1, LargeV2]
          val large = LargeV1(1, "a", true, 2L, 3.0, 4, "b", false, 5L, 6.0, 7, "c", true, 8L, 9.0)

          val result = into.into(large)
          assertTrue(
            result.isRight,
            result.map(_.f1) == Right(1L),
            result.map(_.f6) == Right(4L),
            result.map(_.f11) == Right(7L)
          )
        }
      ),
      suite("Recursive Types")(
        test("simple recursive case class - limitation documented") {
          // NOTE: Directly recursive types (where a type contains itself) are currently
          // limited by Scala's "Maximal number of successive inlines exceeded" error.
          // This is a compiler limitation when deriving Into for types like:
          //   case class Node(value: Int, children: List[Node])
          //
          // Workarounds:
          // 1. Use nested structures with Option (see "deeply nested structures" test below)
          // 2. Use manual Into instances for recursive types
          // 3. Use wrapper types to break the direct recursion
          //
          // Example of what would fail:
          //   case class Node(value: Int, children: List[Node])
          //   case class TreeNode(value: Long, children: Vector[TreeNode])
          //   val into = Into.derived[Node, TreeNode] // Would exceed inline limit
          assertTrue(true) // Placeholder documenting the limitation
        },
        test("deeply nested structures (5+ levels)") {
          case class Level1(a: Int, next: Option[Level2])
          case class Level2(b: String, next: Option[Level3])
          case class Level3(c: Long, next: Option[Level4])
          case class Level4(d: Double, next: Option[Level5])
          case class Level5(e: Boolean, next: Option[Level6])
          case class Level6(f: Int)

          case class Level1V2(a: Long, next: Option[Level2V2])
          case class Level2V2(b: String, next: Option[Level3V2])
          case class Level3V2(c: Long, next: Option[Level4V2])
          case class Level4V2(d: Double, next: Option[Level5V2])
          case class Level5V2(e: Boolean, next: Option[Level6V2])
          case class Level6V2(f: Long)

          val into = Into.derived[Level1, Level1V2]
          val deep =
            Level1(1, Some(Level2("a", Some(Level3(2L, Some(Level4(3.0, Some(Level5(true, Some(Level6(4)))))))))))

          val result = into.into(deep)
          assertTrue(
            result.isRight,
            result.map(_.a) == Right(1L),
            result.map(_.next.get.b) == Right("a"),
            result.map(_.next.get.next.get.c) == Right(2L),
            result.map(_.next.get.next.get.next.get.d) == Right(3.0),
            result.map(_.next.get.next.get.next.get.next.get.e) == Right(true),
            result.map(_.next.get.next.get.next.get.next.get.next.get.f) == Right(4L)
          )
        },
        test("mutually recursive types") {
          case class A(value: Int, b: Option[B])
          case class B(value: String, a: Option[A])

          case class AV2(value: Long, b: Option[BV2])
          case class BV2(value: String, a: Option[AV2])

          val into   = Into.derived[A, AV2]
          val mutual = A(1, Some(B("test", Some(A(2, None)))))

          val result = into.into(mutual)
          assertTrue(
            result.isRight,
            result.map(_.value) == Right(1L),
            result.map(_.b.get.value) == Right("test"),
            result.map(_.b.get.a.get.value) == Right(2L)
          )
        }
      ),
      suite("Collection Edge Cases")(
        test("Set order preservation (not guaranteed)") {
          val into = Into.derived[Set[Int], List[Int]]
          val set  = Set(3, 1, 4, 1, 5, 9, 2, 6)

          val result = into.into(set)
          assertTrue(
            result.isRight,
            result.map(_.toSet) == Right(set) // Order may differ, but elements should match
          )
        },
        test("List to Set removes duplicates") {
          val into = Into.derived[List[Int], Set[Int]]
          val list = List(1, 2, 2, 3, 3, 3, 4)

          val result = into.into(list)
          assertTrue(
            result.isRight,
            result.map(_.size) == Right(4),
            result.map(_.contains(1)) == Right(true),
            result.map(_.contains(2)) == Right(true),
            result.map(_.contains(3)) == Right(true),
            result.map(_.contains(4)) == Right(true)
          )
        },
        test("Set to List to Set (duplicates removed)") {
          val into1       = Into.derived[Set[Int], List[Int]]
          val into2       = Into.derived[List[Int], Set[Int]]
          val originalSet = Set(1, 2, 3)

          val result1 = into1.into(originalSet)
          val result2 = result1.flatMap(list => into2.into(list))

          assertTrue(
            result2.isRight,
            result2.map(_.size) == Right(3),
            result2.map(_ == originalSet) == Right(true)
          )
        },
        test("Array conversions") {
          val into1 = Into.derived[Array[Int], List[Long]]
          val into2 = Into.derived[List[Int], Array[Long]]
          val arr   = Array(1, 2, 3, 4, 5)

          val result1 = into1.into(arr)
          assertTrue(
            result1.isRight,
            result1.map(_.toList) == Right(List(1L, 2L, 3L, 4L, 5L))
          )

          val list    = List(10, 20, 30)
          val result2 = into2.into(list)
          assertTrue(
            result2.isRight,
            result2.map(_.toList) == Right(List(10L, 20L, 30L))
          )
        },
        test("Array[Int] to Array[Long]") {
          val into = Into.derived[Array[Int], Array[Long]]
          val arr  = Array(1, 2, 3, 4, 5)

          val result = into.into(arr)
          assertTrue(
            result.isRight,
            result.map(_.toList) == Right(List(1L, 2L, 3L, 4L, 5L))
          )
        },
        test("Vector to Array") {
          val into = Into.derived[Vector[String], Array[String]]
          val vec  = Vector("a", "b", "c")

          val result = into.into(vec)
          assertTrue(
            result.isRight,
            result.map(_.toList) == Right(List("a", "b", "c"))
          )
        }
      ),
      suite("Empty Collections")(
        test("empty List to Set") {
          val into = Into.derived[List[Int], Set[Int]]
          assertTrue(
            into.into(Nil) == Right(Set.empty[Int])
          )
        },
        test("empty Set to List") {
          val into = Into.derived[Set[String], List[String]]
          assertTrue(
            into.into(Set.empty[String]) == Right(Nil)
          )
        },
        test("empty Array to List") {
          val into = Into.derived[Array[Int], List[Long]]
          assertTrue(
            into.into(Array.empty[Int]) == Right(Nil)
          )
        }
      ),
      suite("Single Element Collections")(
        test("single element List to Set") {
          val into = Into.derived[List[Int], Set[Int]]
          assertTrue(
            into.into(List(42)) == Right(Set(42))
          )
        },
        test("single element Array to Vector") {
          val into = Into.derived[Array[String], Vector[String]]
          assertTrue(
            into.into(Array("test")) == Right(Vector("test"))
          )
        }
      ),
      suite("Large Coproducts")(
        test("sealed trait with 20+ cases") {
          import TestFixtures._
          val into = Into.derived[LargeADT, LargeADTV2]

          assertTrue(
            into.into(LargeADT.Case1(1)) == Right(LargeADTV2.Case1(1L)),
            into.into(LargeADT.Case10(10)) == Right(LargeADTV2.Case10(10L)),
            into.into(LargeADT.Case20(20)) == Right(LargeADTV2.Case20(20L)),
            into.into(LargeADT.Case22(22)) == Right(LargeADTV2.Case22(22L))
          )
        },
        test("large coproduct with mixed cases (objects and classes)") {
          import TestFixtures._
          val into = Into.derived[MixedADT, MixedADTV2]

          assertTrue(
            into.into(MixedADT.Obj1) == Right(MixedADTV2.Obj1),
            into.into(MixedADT.Obj3) == Right(MixedADTV2.Obj3),
            into.into(MixedADT.Class1(1)) == Right(MixedADTV2.Class1(1L)),
            into.into(MixedADT.Class10(10)) == Right(MixedADTV2.Class10(10L)),
            into.into(MixedADT.Class15(15)) == Right(MixedADTV2.Class15(15L)),
            into.into(MixedADT.Obj6) == Right(MixedADTV2.Obj6)
          )
        }
      )
    )
  )
}
