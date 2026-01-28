package zio.blocks.schema

import zio.blocks.schema._
import zio.prelude.{Assertion => PreludeAssertion, _}
import zio.test._
import zio.test.Assertion._

object OpaqueTypeDefs {
  opaque type SimpleAge = Int
  object SimpleAge {
    def apply(value: Int): Either[String, SimpleAge] =
      if (value >= 0 && value <= 150) Right(value)
      else Left(s"Invalid age: $value")

    def unsafe(value: Int): SimpleAge = value

    extension (age: SimpleAge) def toInt: Int = age
  }

  opaque type Email = String
  object Email {
    def apply(value: String): Either[String, Email] =
      if (value.contains("@")) Right(value)
      else Left(s"Invalid email: $value")

    def unsafe(value: String): Email = value

    extension (email: Email) def value: String = email
  }

  opaque type PositiveInt = Int
  object PositiveInt {
    def apply(value: Int): Either[String, PositiveInt] =
      if (value > 0) Right(value)
      else Left(s"Value must be positive: $value")

    def unsafe(value: Int): PositiveInt = value

    extension (pi: PositiveInt) def toInt: Int = pi
  }

  opaque type UserId = Long

  object UserId {
    def apply(value: Long): Either[String, UserId] =
      if (value > 0) Right(value)
      else Left(s"Invalid user id: $value")

    def unsafe(value: Long): UserId = value

    extension (id: UserId) def toLong: Long = id
  }
}

opaque type OpaqueAge = Int
object OpaqueAge {
  def apply(value: Int): Either[String, OpaqueAge] =
    if (value >= 0 && value <= 150) Right(value)
    else Left(s"Invalid age: $value")

  def unsafe(value: Int): OpaqueAge = value

  extension (age: OpaqueAge) def toInt: Int = age
}

opaque type OpaqueName = String
object OpaqueName {
  def apply(value: String): Either[String, OpaqueName] =
    if (value.nonEmpty) Right(value)
    else Left("Name cannot be empty")

  def unsafe(value: String): OpaqueName = value

  extension (name: OpaqueName) def underlying: String = name
}

object IntoVersionSpecificSpec extends SchemaBaseSpec {

  enum Status {
    case Active, Inactive, Suspended
  }

  enum State {
    case Active, Inactive, Suspended
  }

  object Domain {
    object Age extends Subtype[Int] {
      override def assertion: PreludeAssertion[Int] = zio.prelude.Assertion.between(0, 150)
    }

    type Age = Age.Type

    object Name extends Newtype[String] {
      override def assertion: PreludeAssertion[String] = !zio.prelude.Assertion.isEmptyString
    }

    type Name = Name.Type
  }

  import Domain.*

  case class PersonV1(name: String, age: Int)

  case class PersonV2(name: Name, age: Age)

  import OpaqueTypeDefs.*

  case class PersonV3(name: String, age: Int, email: String)
  case class PersonV4(name: String, age: SimpleAge, email: Email)

  // === AnyVal Case Classes ===
  case class AgeWrapper(value: Int) extends AnyVal

  case class NameWrapper(value: String) extends AnyVal

  import OpaqueAge.toInt
  import OpaqueName.underlying

  case class WrappedId(value: Long) extends AnyVal
  case class WrappedName(value: String)

  case class UserRaw(id: Long, name: String, age: Int)
  case class UserValidated(id: UserId, name: Email, age: Age)

  case class PersonPrimitive(name: String, age: Int)
  case class PersonWrapped(name: Name, age: Age)

  case class RecordWithId(id: Long, label: String)
  case class RecordWithWrappedId(id: WrappedId, label: String)

  def spec: Spec[TestEnvironment, Any] = suite("IntoVersionSpecificSpec")(
    suite("Enum to Enum")(
      test("maps enum values by matching names - Active") {
        assert(Into.derived[Status, State].into(Status.Active))(isRight(equalTo(State.Active: State)))
      },
      test("maps Inactive status to Inactive state") {
        assert(Into.derived[Status, State].into(Status.Inactive))(isRight(equalTo(State.Inactive: State)))
      },
      test("maps Suspended status to Suspended state") {
        assert(Into.derived[Status, State].into(Status.Suspended))(isRight(equalTo(State.Suspended: State)))
      }
    ),
    suite("ZIO Prelude Newtype")(
      test("converts Int to Age (Subtype) with successful validation") {
        assert(Into.derived[PersonV1, PersonV2].into(PersonV1("Alice", 30)))(isRight(anything))
      },
      test("fails when Age validation fails - negative value") {
        val result = Into.derived[PersonV1, PersonV2].into(PersonV1("Charlie", -5))
        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.toLowerCase.contains("validation") || err.toString.contains("-5"))
        )
      },
      test("derive primitive to NewType conversion with successful validation") {
        assert(Into.derived[String, Name].into("Bob"))(isRight(anything))
      }
    ),
    suite("Opaque Type")(
      suite("Direct opaque type derivation")(
        test("Into.derived[Int, SimpleAge] works for valid value") {
          val result = Into.derived[Int, SimpleAge].into(30)
          assertTrue(
            result.isRight,
            result.map(_.toInt).getOrElse(0) == 30
          )
        },
        test("Into.derived[Int, SimpleAge] fails for invalid value") {
          val result = Into.derived[Int, SimpleAge].into(-5)
          assertTrue(
            result.isLeft,
            result.swap.exists(err => err.toString.contains("Invalid age"))
          )
        }
      ),
      suite("Product type with Opaque Type field")(
        test("converts to opaque types with successful validation") {
          val result = Into.derived[PersonV3, PersonV4].into(PersonV3("Alice", 30, "a@b.com"))
          assertTrue(
            result.isRight,
            result.map(_.name).getOrElse("") == "Alice",
            result.map(_.age.toInt).getOrElse(0) == 30
          )
        },
        test("fails when age validation fails - negative value") {
          val result = Into.derived[PersonV3, PersonV4].into(PersonV3("Bob", -5, "a@b.com"))
          assertTrue(
            result.isLeft,
            result.swap.exists(err => err.toString.contains("Validation failed")),
            result.swap.exists(err => err.toString.contains("Invalid age"))
          )
        }
      ),
      test("errors are accumulated when multiple fields are invalid") {
        val result  = Into.derived[PersonV3, PersonV4].into(PersonV3("Invalid", -5, "bad-email"))
        val message = result.swap.map(_.getMessage).getOrElse("")
        assertTrue(
          result.isLeft,
          message ==
            """converting field PersonV3.email to PersonV4.email failed
              |  Caused by: Validation failed for field 'email': Invalid email: bad-email
              |converting field PersonV3.age to PersonV4.age failed
              |  Caused by: Validation failed for field 'age': Invalid age: -5""".stripMargin
        )
      },
      test("opaque type in collection - error propagates from collection element") {
        case class AgeList(ages: List[Int])
        @scala.annotation.nowarn("msg=unused local definition")
        case class ValidatedAgeList(ages: List[SimpleAge])

        val result = Into.derived[AgeList, ValidatedAgeList].into(AgeList(List(30, 200, 40)))
        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.contains("Invalid age"))
        )
      },
      test("opaque type validation in coproduct case") {
        sealed trait RequestV1
        case class CreateV1(name: String, age: Int) extends RequestV1

        sealed trait RequestV2
        @scala.annotation.nowarn("msg=unused local definition")
        case class CreateV2(name: String, age: SimpleAge) extends RequestV2

        val result = Into.derived[RequestV1, RequestV2].into(CreateV1("Alice", -5))
        assertTrue(
          result.isLeft,
          result.swap.exists(err => err.toString.contains("Invalid age"))
        )
      }
    ),
    suite("Primitive To Wrapper")(
      suite("ZIO Prelude Newtype/Subtype")(
        suite("Into: Primitive -> Newtype")(
          test("Int -> Age (valid)") {
            assertTrue(Into.derived[Int, Age].into(30).isRight)
          },
          test("Int -> Age (invalid - negative)") {
            assertTrue(Into.derived[Int, Age].into(-5).isLeft)
          },
          test("Int -> Age (invalid - too high)") {
            assertTrue(Into.derived[Int, Age].into(200).isLeft)
          },
          test("String -> Name (valid)") {
            assertTrue(Into.derived[String, Name].into("Alice").isRight)
          },
          test("String -> Name (invalid - empty)") {
            assertTrue(Into.derived[String, Name].into("").isLeft)
          }
        ),
        suite("Into: Newtype -> Primitive")(
          test("Age -> Int") {
            assert(Into.derived[Age, Int].into(Age.make(30).toOption.get))(isRight(equalTo(30)))
          },
          test("Name -> String") {
            assert(Into.derived[Name, String].into(Name.make("Alice").toOption.get))(isRight(equalTo("Alice")))
          }
        )
      ),
      suite("Scala 3 Opaque Types")(
        suite("Into: Primitive -> Opaque")(
          test("Int -> OpaqueAge (valid)") {
            val result = Into.derived[Int, OpaqueAge].into(30)
            assertTrue(
              result.isRight,
              result.map(_.toInt).getOrElse(0) == 30
            )
          },
          test("Int -> OpaqueAge (invalid - negative)") {
            assertTrue(Into.derived[Int, OpaqueAge].into(-5).isLeft)
          },
          test("String -> OpaqueName (valid)") {
            val result = Into.derived[String, OpaqueName].into("Bob")
            assertTrue(
              result.isRight,
              result.map(_.underlying).getOrElse("") == "Bob"
            )
          },
          test("String -> OpaqueName (invalid - empty)") {
            assertTrue(Into.derived[String, OpaqueName].into("").isLeft)
          }
        ),
        suite("Into: Opaque -> Primitive")(
          test("OpaqueAge -> Int") {
            assert(Into.derived[OpaqueAge, Int].into(OpaqueAge.unsafe(30)))(isRight(equalTo(30)))
          },
          test("OpaqueName -> String") {
            assert(Into.derived[OpaqueName, String].into(OpaqueName.unsafe("Carol")))(isRight(equalTo("Carol")))
          }
        )
      ),
      suite("AnyVal Case Classes")(
        suite("Into: Primitive -> AnyVal")(
          test("Int -> AgeWrapper") {
            assert(Into.derived[Int, AgeWrapper].into(30))(isRight(equalTo(AgeWrapper(30))))
          },
          test("String -> NameWrapper") {
            assert(Into.derived[String, NameWrapper].into("Dave"))(isRight(equalTo(NameWrapper("Dave"))))
          }
        ),
        suite("Into: AnyVal -> Primitive")(
          test("AgeWrapper -> Int") {
            assert(Into.derived[AgeWrapper, Int].into(AgeWrapper(30)))(isRight(equalTo(30)))
          },
          test("NameWrapper -> String") {
            assert(Into.derived[NameWrapper, String].into(NameWrapper("Eve")))(isRight(equalTo("Eve")))
          }
        )
      )
    ),
    suite("Wrapper Fields")(
      suite("Opaque Type Fields")(
        test("Into: primitive fields -> opaque type fields") {
          val result = Into.derived[UserRaw, UserValidated].into(UserRaw(1L, "test@example.com", 25))
          assertTrue(
            result.isRight,
            result.toOption.get.id.toLong == 1L,
            result.toOption.get.name.value == "test@example.com",
            result.toOption.get.age == Age.make(25).toOption.get
          )
        },
        test("Into: primitive fields -> opaque type fields (validation failure)") {
          assertTrue(Into.derived[UserRaw, UserValidated].into(UserRaw(-1L, "invalid-email", 200)).isLeft)
        },
        test("Into: opaque type fields -> primitive fields") {
          val validated = UserValidated(
            UserId.unsafe(42L),
            Email.unsafe("user@example.com"),
            Age.make(30).toOption.get
          )
          val result = Into.derived[UserValidated, UserRaw].into(validated)
          assertTrue(
            result.isRight,
            result.toOption.get.id == 42L,
            result.toOption.get.name == "user@example.com",
            result.toOption.get.age == 30
          )
        }
      ),
      suite("ZIO Prelude Newtype Fields")(
        test("Into: primitive fields -> newtype fields") {
          val result = Into.derived[PersonPrimitive, PersonWrapped].into(PersonPrimitive("Alice", 25))
          assertTrue(
            result.isRight,
            result.toOption.get.name == Name.make("Alice").toOption.get,
            result.toOption.get.age == Age.make(25).toOption.get
          )
        },
        test("Into: primitive fields -> newtype fields (validation failure)") {
          assertTrue(Into.derived[PersonPrimitive, PersonWrapped].into(PersonPrimitive("", 200)).isLeft)
        },
        test("Into: newtype fields -> primitive fields") {
          val wrapped = PersonWrapped(
            Name.make("Bob").toOption.get,
            Age.make(30).toOption.get
          )
          val result = Into.derived[PersonWrapped, PersonPrimitive].into(wrapped)
          assertTrue(
            result.isRight,
            result.toOption.get.name == "Bob",
            result.toOption.get.age == 30
          )
        }
      ),
      suite("AnyVal/Single-field Case Class Fields")(
        test("Into: primitive field -> single-field case class field") {
          val result = Into.derived[RecordWithId, RecordWithWrappedId].into(RecordWithId(123L, "test"))
          assertTrue(
            result.isRight,
            result.toOption.get.id.value == 123L,
            result.toOption.get.label == "test"
          )
        },
        test("Into: single-field case class field -> primitive field") {
          val result =
            Into.derived[RecordWithWrappedId, RecordWithId].into(RecordWithWrappedId(WrappedId(456L), "label"))
          assertTrue(
            result.isRight,
            result.toOption.get.id == 456L,
            result.toOption.get.label == "label"
          )
        }
      ),
      suite("As Round-trips with Wrapper Fields")(
        test("As: primitive <-> newtype fields round-trip") {
          val as        = As.derived[PersonPrimitive, PersonWrapped]
          val original  = PersonPrimitive("Carol", 40)
          val roundTrip = as.into(original).flatMap(as.from)
          assertTrue(
            roundTrip.isRight,
            roundTrip.toOption.get == original
          )
        },
        test("As: wrapper -> primitive -> wrapper round-trip") {
          val as      = As.derived[PersonPrimitive, PersonWrapped]
          val wrapped = PersonWrapped(
            Name.make("Dave").toOption.get,
            Age.make(35).toOption.get
          )
          val roundTrip = as.from(wrapped).flatMap(as.into)
          assertTrue(
            roundTrip.isRight,
            roundTrip.toOption.get == wrapped
          )
        }
      )
    ),
    suite("tuple conversion macro coverage")(
      test("converts tuple to case class with matching arity") {
        case class Point(x: Int, y: Int)
        val into   = Into.derived[(Int, Int), Point]
        val result = into.into((10, 20))

        assert(result)(isRight(equalTo(Point(10, 20))))
      },
      test("converts case class to tuple") {
        case class Point(x: Int, y: Int)
        val into   = Into.derived[Point, (Int, Int)]
        val result = into.into(Point(10, 20))

        assert(result)(isRight(equalTo((10, 20))))
      },
      test("converts tuple to tuple with element type coercion") {
        val into   = Into.derived[(Int, Int), (Long, Long)]
        val result = into.into((1, 2))

        assert(result)(isRight(equalTo((1L, 2L))))
      },
      test("converts 3-element tuple to case class") {
        case class Triple(a: Int, b: String, c: Boolean)
        val into   = Into.derived[(Int, String, Boolean), Triple]
        val result = into.into((1, "hello", true))

        assert(result)(isRight(equalTo(Triple(1, "hello", true))))
      },
      test("converts case class to 3-element tuple") {
        case class Triple(a: Int, b: String, c: Boolean)
        val into   = Into.derived[Triple, (Int, String, Boolean)]
        val result = into.into(Triple(1, "hello", true))

        assert(result)(isRight(equalTo((1, "hello", true))))
      }
    ),
    suite("primitive type macro coverage")(
      test("converts Boolean to Boolean") {
        val into = Into[Boolean, Boolean]
        assert(into.into(true))(isRight(equalTo(true))) &&
        assert(into.into(false))(isRight(equalTo(false)))
      },
      test("converts Unit to Unit") {
        val into = Into[Unit, Unit]
        assert(into.into(()))(isRight(equalTo(())))
      },
      test("converts primitive to single-field wrapper") {
        case class Age(value: Int)
        val into = Into.derived[Int, Age]
        assert(into.into(42))(isRight(equalTo(Age(42))))
      },
      test("converts single-field wrapper to primitive") {
        case class Age(value: Int)
        val into = Into.derived[Age, Int]
        assert(into.into(Age(42)))(isRight(equalTo(42)))
      },
      test("converts Byte single-field wrapper") {
        case class ByteWrapper(value: Byte)
        val into = Into.derived[Byte, ByteWrapper]
        assert(into.into(42.toByte))(isRight(equalTo(ByteWrapper(42.toByte))))
      },
      test("converts Short single-field wrapper") {
        case class ShortWrapper(value: Short)
        val into = Into.derived[Short, ShortWrapper]
        assert(into.into(42.toShort))(isRight(equalTo(ShortWrapper(42.toShort))))
      },
      test("converts Long single-field wrapper") {
        case class LongWrapper(value: Long)
        val into = Into.derived[Long, LongWrapper]
        assert(into.into(42L))(isRight(equalTo(LongWrapper(42L))))
      },
      test("converts Float single-field wrapper") {
        case class FloatWrapper(value: Float)
        val into = Into.derived[Float, FloatWrapper]
        assert(into.into(3.14f))(isRight(equalTo(FloatWrapper(3.14f))))
      },
      test("converts Double single-field wrapper") {
        case class DoubleWrapper(value: Double)
        val into = Into.derived[Double, DoubleWrapper]
        assert(into.into(3.14))(isRight(equalTo(DoubleWrapper(3.14))))
      },
      test("converts Char single-field wrapper") {
        case class CharWrapper(value: Char)
        val into = Into.derived[Char, CharWrapper]
        assert(into.into('X'))(isRight(equalTo(CharWrapper('X'))))
      },
      test("converts String single-field wrapper") {
        case class StringWrapper(value: String)
        val into = Into.derived[String, StringWrapper]
        assert(into.into("hello"))(isRight(equalTo(StringWrapper("hello"))))
      }
    )
  )
}
