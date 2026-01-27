package zio.blocks.schema.as.validation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import zio.prelude._

object AsVersionSpecificSpec extends SchemaBaseSpec {

  object Types {
    object RtAge extends Subtype[Int] {
      override def assertion = assert(zio.prelude.Assertion.between(0, 150))
    }
    type RtAge = RtAge.Type
  }

  case class PersonRawA(name: String, age: Int)
  case class PersonValidatedB(name: String, age: Types.RtAge)

  object PreludeDomain {
    object RtAge extends Subtype[Int] {
      override def assertion = assert(zio.prelude.Assertion.between(0, 150))
    }
    type RtAge = RtAge.Type

    object RtName extends Newtype[String] {
      override def assertion = assert(!zio.prelude.Assertion.isEmptyString)
    }
    type RtName = RtName.Type
  }

  case class RtAgeWrapper(value: Int)     extends AnyVal
  case class RtNameWrapper(value: String) extends AnyVal

  def spec = suite("AsVersionSpecificSpec")(
    suite("ZIO Prelude Newtypes")(
      test("round-trip with newtype field") {
        val original = PersonRawA("Alice", 30)
        val as       = As.derived[PersonRawA, PersonValidatedB]
        assert(as.into(original).flatMap(b => as.from(b)))(isRight(equalTo(original)))
      },
      test("reverse works with newtype") {
        val as        = As.derived[PersonRawA, PersonValidatedB]
        val reversed  = as.reverse
        val validated = PersonValidatedB("Bob", Types.RtAge.make(25).toEither.toOption.get)
        assert(reversed.into(validated))(isRight(equalTo(PersonRawA("Bob", 25))))
      }
    ),
    suite("ZIO Prelude Newtype/Subtype")(
      test("Int <-> Age round-trip") {
        import PreludeDomain._

        val as       = As.derived[Int, RtAge]
        val original = 30
        val age      = RtAge.make(30).toOption.get
        assert(as.into(original).flatMap(as.from))(isRight(equalTo(original))) &&
        assertTrue(as.from(age).flatMap(as.into).isRight)
      },
      test("String <-> Name round-trip") {
        import PreludeDomain._

        val as       = As.derived[String, RtName]
        val original = "Alice"
        val name     = RtName.make("Alice").toOption.get
        assert(as.into(original).flatMap(as.from))(isRight(equalTo(original))) &&
        assertTrue(as.from(name).flatMap(as.into).isRight)
      },
      test("reverse works correctly for newtypes") {
        import PreludeDomain._

        val swapped = As.derived[Int, RtAge].reverse
        val age     = RtAge.make(25).toOption.get
        assert(swapped.into(age))(isRight(equalTo(25)))
      }
    ),
    suite("AnyVal Case Classes")(
      test("Int <-> AgeWrapper round-trip") {
        val as       = As.derived[Int, RtAgeWrapper]
        val original = 30
        val wrapper  = RtAgeWrapper(30)
        assert(as.into(original).flatMap(as.from))(isRight(equalTo(original))) &&
        assert(as.from(wrapper).flatMap(as.into))(isRight(equalTo(wrapper)))
      },
      test("String <-> NameWrapper round-trip") {
        val as       = As.derived[String, RtNameWrapper]
        val original = "Carol"
        val wrapper  = RtNameWrapper("Carol")
        assert(as.into(original).flatMap(as.from))(isRight(equalTo(original))) &&
        assert(as.from(wrapper).flatMap(as.into))(isRight(equalTo(wrapper)))
      },
      test("reverse works correctly for AnyVal") {
        val swapped = As.derived[Int, RtAgeWrapper].reverse
        val wrapper = RtAgeWrapper(25)
        assert(swapped.into(wrapper))(isRight(equalTo(25)))
      }
    )
  )
}
