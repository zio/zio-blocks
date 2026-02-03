package zio.blocks.schema.into.validation

import zio.blocks.schema._
import zio.prelude._
import zio.test._
import zio.test.Assertion._

/** Tests for Into derivation with ZIO Prelude Newtype/Subtype fields. */
object IntoVersionSpecificSpec extends SchemaBaseSpec {
  object Types {
    object Age extends Subtype[Int] {
      override def assertion = assert(zio.prelude.Assertion.between(0, 150))
    }
    type Age = Age.Type

    object Email extends Newtype[String] {
      override def assertion = assert(zio.prelude.Assertion.contains("@"))
    }
    type Email = Email.Type
  }

  case class PersonV1(name: String, age: Int, email: String)
  case class PersonV2(name: String, age: Types.Age, email: Types.Email)

  object PreludeDomain {
    object Age extends Subtype[Int] {
      override def assertion = assert(zio.prelude.Assertion.between(0, 150))
    }

    type Age = Age.Type

    object Name extends Newtype[String] {
      override def assertion = assert(!zio.prelude.Assertion.isEmptyString)
    }

    type Name = Name.Type
  }

  case class AgeWrapper(value: Int) extends AnyVal

  case class NameWrapper(value: String) extends AnyVal

  // === Single-field case class wrappers ===
  case class WrappedId(value: Long) extends AnyVal

  case class WrappedName(value: String)

  // === Test case classes ===

  // Single-field case class wrapper tests (AnyVal)
  case class RecordWithId(id: Long, label: String)

  case class RecordWithWrappedId(id: WrappedId, label: String)

  // Non-AnyVal single field case class wrapper tests
  case class RecordWithName(name: String, count: Int)

  case class RecordWithWrappedName(name: WrappedName, count: Int)

  // Multiple wrapped fields
  case class RecordWithMultiple(id: Long, name: String, score: Double)

  case class RecordWithMultipleWrapped(id: WrappedId, name: WrappedName, score: Double)

  def spec = suite("IntoVersionSpecificSpec")(
    suite("ZIO Prelude Newtype Validation")(
      test("derives Into for case class with multiple newtype fields") {
        assertTrue(Into.derived[PersonV1, PersonV2].into(PersonV1("Alice", 30, "alice@example.com")).isRight)
      }
    ),
    suite("ZIO Prelude Newtype/Subtype")(
      suite("Into: Primitive -> Newtype")(
        test("Int -> Age (valid)") {
          assertTrue(Into.derived[Int, PreludeDomain.Age].into(30).isRight)
        },
        test("Int -> Age (invalid - negative)") {
          assertTrue(Into.derived[Int, PreludeDomain.Age].into(-5).isLeft)
        },
        test("Int -> Age (invalid - too high)") {
          assertTrue(Into.derived[Int, PreludeDomain.Age].into(200).isLeft)
        },
        test("String -> Name (valid)") {
          assertTrue(Into.derived[String, PreludeDomain.Name].into("Alice").isRight)
        },
        test("String -> Name (invalid - empty)") {
          assertTrue(Into.derived[String, PreludeDomain.Name].into("").isLeft)
        }
      ),
      suite("Into: Newtype -> Primitive")(
        test("Age -> Int") {
          assert(Into.derived[PreludeDomain.Age, Int].into(PreludeDomain.Age.make(30).toOption.get))(
            isRight(equalTo(30))
          )
        },
        test("Name -> String") {
          assert(Into.derived[PreludeDomain.Name, String].into(PreludeDomain.Name.make("Alice").toOption.get))(
            isRight(equalTo("Alice"))
          )
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
    ),
    suite("Single-field Case Class Fields")(
      suite("AnyVal Single-field Case Class Fields")(
        test("Into: primitive field -> AnyVal wrapper field") {
          val result = Into.derived[RecordWithId, RecordWithWrappedId].into(RecordWithId(123L, "test"))
          assertTrue(
            result.isRight,
            result.toOption.get.id.value == 123L,
            result.toOption.get.label == "test"
          )
        },
        test("Into: AnyVal wrapper field -> primitive field") {
          val wrapped = RecordWithWrappedId(WrappedId(456L), "label")
          val result  = Into.derived[RecordWithWrappedId, RecordWithId].into(wrapped)
          assertTrue(
            result.isRight,
            result.toOption.get.id == 456L,
            result.toOption.get.label == "label"
          )
        }
      ),
      suite("Non-AnyVal Single-field Case Class Fields")(
        test("Into: primitive field -> non-AnyVal wrapper field") {
          val raw    = RecordWithName("hello", 42)
          val result = Into.derived[RecordWithName, RecordWithWrappedName].into(raw)
          assertTrue(
            result.isRight,
            result.toOption.get.name.value == "hello",
            result.toOption.get.count == 42
          )
        },
        test("Into: non-AnyVal wrapper field -> primitive field") {
          val wrapped = RecordWithWrappedName(WrappedName("world"), 99)
          val result  = Into.derived[RecordWithWrappedName, RecordWithName].into(wrapped)
          assertTrue(
            result.isRight,
            result.toOption.get.name == "world",
            result.toOption.get.count == 99
          )
        }
      ),
      suite("Multiple Wrapped Fields")(
        test("Into: multiple primitive fields -> multiple wrapper fields") {
          val raw    = RecordWithMultiple(100L, "multi", 3.14)
          val result = Into.derived[RecordWithMultiple, RecordWithMultipleWrapped].into(raw)
          assertTrue(
            result.isRight,
            result.toOption.get.id.value == 100L,
            result.toOption.get.name.value == "multi",
            result.toOption.get.score == 3.14
          )
        },
        test("Into: multiple wrapper fields -> multiple primitive fields") {
          val wrapped = RecordWithMultipleWrapped(WrappedId(200L), WrappedName("test"), 2.71)
          val result  = Into.derived[RecordWithMultipleWrapped, RecordWithMultiple].into(wrapped)
          assertTrue(
            result.isRight,
            result.toOption.get.id == 200L,
            result.toOption.get.name == "test",
            result.toOption.get.score == 2.71
          )
        }
      ),
      suite("As Round-trips with Single-field Case Class Fields")(
        test("As: primitive <-> AnyVal wrapper fields round-trip") {
          val as        = As.derived[RecordWithId, RecordWithWrappedId]
          val original  = RecordWithId(789L, "round-trip")
          val roundTrip = as.into(original).flatMap(as.from)
          assertTrue(
            roundTrip.isRight,
            roundTrip.toOption.get == original
          )
        },
        test("As: wrapper -> primitive -> wrapper round-trip") {
          val as        = As.derived[RecordWithId, RecordWithWrappedId]
          val wrapped   = RecordWithWrappedId(WrappedId(999L), "reverse")
          val roundTrip = as.from(wrapped).flatMap(as.into)
          assertTrue(
            roundTrip.isRight,
            roundTrip.toOption.get == wrapped
          )
        },
        test("As: non-AnyVal wrapper fields round-trip") {
          val as        = As.derived[RecordWithName, RecordWithWrappedName]
          val original  = RecordWithName("hello", 42)
          val roundTrip = as.into(original).flatMap(as.from)
          assertTrue(
            roundTrip.isRight,
            roundTrip.toOption.get == original
          )
        },
        test("As: multiple wrapper fields round-trip") {
          val as        = As.derived[RecordWithMultiple, RecordWithMultipleWrapped]
          val original  = RecordWithMultiple(555L, "multiple", 1.618)
          val roundTrip = as.into(original).flatMap(as.from)
          assertTrue(
            roundTrip.isRight,
            roundTrip.toOption.get == original
          )
        }
      )
    )
  )
}
