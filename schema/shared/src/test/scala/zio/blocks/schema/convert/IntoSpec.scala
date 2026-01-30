package zio.blocks.schema.convert

import zio.test._
import zio.test.Assertion._

object IntoSpec extends ZIOSpecDefault {

  // Priority 1: Exact match (same name + same type) with reordering
  case class Point(x: Int, y: Int)
  case class Coord(y: Int, x: Int)

  // Priority 3: Unique type match (each type appears once)
  case class Person(name: String, age: Int, active: Boolean)
  case class User(username: String, yearsOld: Int, enabled: Boolean)

  // Priority 4: Position + matching type (for tuple-like conversions)
  case class RGB(r: Int, g: Int, b: Int)
  case class ColorValues(red: Int, green: Int, blue: Int)

  // Combined: some fields match by name, others by unique type
  case class Employee(id: Long, name: String, department: Int)
  case class Worker(name: String, team: Int, identifier: Long)

  // Case class to case class with unique type matching
  case class PersonV1(name: String, age: Int)
  case class PersonV2(fullName: String, age: Int)

  // Case class to tuple
  case class PointDouble(x: Double, y: Double)

  // Tuple to case class
  case class RGBColor(red: Int, green: Int, blue: Int)

  // === Coproduct Types ===

  // Sealed trait to sealed trait (by name)
  sealed trait Color
  object Color {
    case object Red  extends Color
    case object Blue extends Color
  }

  sealed trait Hue
  object Hue {
    case object Red  extends Hue
    case object Blue extends Hue
  }

  // Sealed trait to sealed trait (by signature)
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
  case class SuccessV1(value: Int)  extends ResultV1
  case class FailureV1(msg: String) extends ResultV1

  sealed trait ResultV2
  case class SuccessV2(value: Int)  extends ResultV2
  case class FailureV2(msg: String) extends ResultV2

  def spec: Spec[TestEnvironment, Any] = suite("IntoSpec")(
    suite("Product to Product")(
      suite("Priority 1: Exact match (same name + same type)")(
        test("maps fields by name despite different ordering") {
          val point  = Point(1, 2)
          val result = Into.derived[Point, Coord].into(point)

          // x→x, y→y (by name, not position)
          assert(result)(isRight(equalTo(Coord(y = 2, x = 1))))
        },
        test("maps fields when names and types match exactly") {
          case class Source(id: String, value: Int)
          case class Target(id: String, value: Int)

          val source = Source("abc", 42)
          val result = Into.derived[Source, Target].into(source)

          assert(result)(isRight(equalTo(Target("abc", 42))))
        }
      ),
      suite("Priority 3: Unique type match")(
        test("maps fields by unique type when names differ") {
          val person = Person(name = "Alice", age = 30, active = true)
          val result = Into.derived[Person, User].into(person)

          // Each type appears exactly once, so mapping is unambiguous:
          // String→String, Int→Int, Boolean→Boolean
          assert(result)(isRight(equalTo(User(username = "Alice", yearsOld = 30, enabled = true))))
        }
      ),
      suite("Priority 4: Position + matching type")(
        test("maps fields by position when types match but names differ (tuple-like)") {
          // r→red, g→green, b→blue by position (all Int, names don't match)
          val rgb    = RGB(r = 255, g = 128, b = 64)
          val result = Into.derived[RGB, ColorValues].into(rgb)

          assert(result)(isRight(equalTo(ColorValues(red = 255, green = 128, blue = 64))))
        }
      ),
      suite("Combined: name match and unique type match")(
        test("uses name match for matching names and unique type for others") {
          // name→name (exact match), id→identifier (Long is unique), department→team (Int is unique)
          val employee = Employee(id = 123L, name = "Bob", department = 5)
          val result   = Into.derived[Employee, Worker].into(employee)

          assert(result)(isRight(equalTo(Worker(name = "Bob", team = 5, identifier = 123L))))
        }
      ),
      suite("Case Class to Case Class")(
        test("maps by unique type when field names differ") {
          // 'name' is unique String in V1, 'fullName' is unique String in V2
          // 'age' matches by name
          val personV1 = PersonV1("Alice", 30)
          val result   = Into.derived[PersonV1, PersonV2].into(personV1)

          assert(result)(isRight(equalTo(PersonV2("Alice", 30))))
        }
      ),
      suite("Case Class to Tuple")(
        test("maps case class fields to tuple by position") {
          val point  = PointDouble(1.0, 2.0)
          val result = Into.derived[PointDouble, (Double, Double)].into(point)

          assert(result)(isRight(equalTo((1.0, 2.0))))
        }
      ),
      suite("Tuple to Case Class")(
        test("maps tuple elements to case class fields by position") {
          val tuple  = (255, 128, 64)
          val result = Into.derived[(Int, Int, Int), RGBColor].into(tuple)

          assert(result)(isRight(equalTo(RGBColor(red = 255, green = 128, blue = 64))))
        }
      ),
      suite("Tuple to Tuple")(
        test("maps tuple to tuple by position with same types") {
          val tuple  = (42, "hello")
          val result = Into.derived[(Int, String), (Int, String)].into(tuple)

          assert(result)(isRight(equalTo((42, "hello"))))
        }
        // TODO: Add test for coercion (Int -> Long) once coercion is implemented
        // test("maps tuple to tuple with type coercion") {
        //   val tuple = (42, "hello")
        //   val result = Into.derived[(Int, String), (Long, String)].into(tuple)
        //   assert(result)(isRight(equalTo((42L, "hello"))))
        // }
      )
    ),
    suite("Coproduct to Coproduct")(
      suite("Sealed Trait to Sealed Trait (by name)")(
        test("maps case objects by matching names") {
          val color: Color = Color.Red
          val result       = Into.derived[Color, Hue].into(color)

          // Red → Red (matched by name)
          assert(result)(isRight(equalTo(Hue.Red: Hue)))
        },
        test("maps Blue to Blue") {
          val color: Color = Color.Blue
          val result       = Into.derived[Color, Hue].into(color)

          assert(result)(isRight(equalTo(Hue.Blue: Hue)))
        }
      ),
      suite("Sealed Trait to Sealed Trait (by signature)")(
        test("maps case classes by constructor signature when names differ") {
          val event: EventV1 = EventV1.Created("abc", 123L)
          val result         = Into.derived[EventV1, EventV2].into(event)

          // Created(String, Long) → Spawned(String, Long) matched by signature
          assert(result)(isRight(equalTo(EventV2.Spawned("abc", 123L): EventV2)))
        },
        test("maps Deleted to Removed by signature") {
          val event: EventV1 = EventV1.Deleted("xyz")
          val result         = Into.derived[EventV1, EventV2].into(event)

          // Deleted(String) → Removed(String) matched by signature
          assert(result)(isRight(equalTo(EventV2.Removed("xyz"): EventV2)))
        }
      ),
      suite("ADT with Payload Conversion")(
        test("maps case classes within sealed trait by matching structure") {
          val result1: ResultV1 = SuccessV1(42)
          val converted         = Into.derived[ResultV1, ResultV2].into(result1)

          assert(converted)(isRight(equalTo(SuccessV2(42): ResultV2)))
        },
        test("maps Failure case by matching structure") {
          val result1: ResultV1 = FailureV1("error message")
          val converted         = Into.derived[ResultV1, ResultV2].into(result1)

          assert(converted)(isRight(equalTo(FailureV2("error message"): ResultV2)))
        }
      )
    ),
    suite("Primitive Type Coercions")(
      suite("Numeric Widening (Lossless)")(
        test("Byte to Short") {
          assert(Into[Byte, Short].into(42.toByte))(isRight(equalTo(42.toShort)))
        },
        test("Short to Int") {
          assert(Into[Short, Int].into(1000.toShort))(isRight(equalTo(1000)))
        },
        test("Int to Long") {
          assert(Into[Int, Long].into(100000))(isRight(equalTo(100000L)))
        },
        test("Float to Double") {
          assert(Into[Float, Double].into(3.14f))(isRight(equalTo(3.14f.toDouble)))
        },
        test("Byte to Long") {
          assert(Into[Byte, Long].into(42.toByte))(isRight(equalTo(42L)))
        },
        test("Int to Double") {
          assert(Into[Int, Double].into(42))(isRight(equalTo(42.0)))
        }
      ),
      suite("Numeric Narrowing (with Runtime Validation)")(
        test("Long to Int - success") {
          assert(Into[Long, Int].into(42L))(isRight(equalTo(42)))
        },
        test("Long to Int - overflow") {
          assert(Into[Long, Int].into(3000000000L))(isLeft)
        },
        test("Double to Float - success") {
          assert(Into[Double, Float].into(3.14))(isRight(equalTo(3.14f)))
        },
        test("Double to Float - overflow") {
          assert(Into[Double, Float].into(1e100))(isLeft)
        },
        test("Int to Byte - success") {
          assert(Into[Int, Byte].into(100))(isRight(equalTo(100.toByte)))
        },
        test("Int to Byte - overflow") {
          assert(Into[Int, Byte].into(200))(isLeft)
        },
        test("Int to Short - success") {
          assert(Into[Int, Short].into(1000))(isRight(equalTo(1000.toShort)))
        },
        test("Int to Short - overflow") {
          assert(Into[Int, Short].into(100000))(isLeft)
        }
      )
    ),
    suite("Collection Element Coercion")(
      test("List[Int] to List[Long]") {
        assert(Into[List[Int], List[Long]].into(List(1, 2, 3)))(isRight(equalTo(List(1L, 2L, 3L))))
      },
      test("Vector[Float] to Vector[Double]") {
        val result = Into[Vector[Float], Vector[Double]].into(Vector(1.5f, 2.5f))
        assert(result)(isRight(equalTo(Vector(1.5f.toDouble, 2.5f.toDouble))))
      },
      test("Set[Short] to Set[Int]") {
        assert(Into[Set[Short], Set[Int]].into(Set(10.toShort, 20.toShort)))(isRight(equalTo(Set(10, 20))))
      },
      test("List[Long] to List[Int] - with overflow error") {
        assert(Into[List[Long], List[Int]].into(List(42L, 3000000000L)))(isLeft)
      },
      test("Seq[Int] to Seq[Long]") {
        assert(Into[Seq[Int], Seq[Long]].into(Seq(1, 2, 3)))(isRight(equalTo(Seq(1L, 2L, 3L))))
      }
    ),
    suite("Map Key/Value Coercion")(
      test("Map[Int, Float] to Map[Long, Double]") {
        val result = Into[Map[Int, Float], Map[Long, Double]].into(Map(1 -> 1.5f, 2 -> 2.5f))
        assert(result)(isRight(equalTo(Map(1L -> 1.5f.toDouble, 2L -> 2.5f.toDouble))))
      },
      test("Map[Long, String] to Map[Int, String] - with key overflow error") {
        assert(Into[Map[Long, String], Map[Int, String]].into(Map(42L -> "a", 3000000000L -> "b")))(isLeft)
      },
      test("Map[String, Int] to Map[String, Long]") {
        assert(Into[Map[String, Int], Map[String, Long]].into(Map("a" -> 1, "b" -> 2)))(
          isRight(equalTo(Map("a" -> 1L, "b" -> 2L)))
        )
      }
    ),
    suite("Option Type Coercion")(
      test("Option[Int] to Option[Long] - Some") {
        assert(Into[Option[Int], Option[Long]].into(Some(42)))(isRight(equalTo(Some(42L))))
      },
      test("Option[Int] to Option[Long] - None") {
        assert(Into[Option[Int], Option[Long]].into(None))(isRight(equalTo(None)))
      },
      test("Option[Long] to Option[Int] - with overflow error") {
        assert(Into[Option[Long], Option[Int]].into(Some(3000000000L)))(isLeft)
      },
      test("Option[Long] to Option[Int] - None is always valid") {
        assert(Into[Option[Long], Option[Int]].into(None))(isRight(equalTo(None)))
      }
    ),
    suite("Either Type Coercion")(
      test("Either[String, Int] to Either[String, Long] - Right") {
        assert(Into[Either[String, Int], Either[String, Long]].into(Right(42)))(isRight(equalTo(Right(42L))))
      },
      test("Either[String, Int] to Either[String, Long] - Left") {
        assert(Into[Either[String, Int], Either[String, Long]].into(Left("error")))(isRight(equalTo(Left("error"))))
      },
      test("Either[Int, String] to Either[Long, String] - Left") {
        assert(Into[Either[Int, String], Either[Long, String]].into(Left(100)))(isRight(equalTo(Left(100L))))
      },
      test("Either[Long, String] to Either[Int, String] - Left with overflow") {
        assert(Into[Either[Long, String], Either[Int, String]].into(Left(3000000000L)))(isLeft)
      }
    )
  )
}
