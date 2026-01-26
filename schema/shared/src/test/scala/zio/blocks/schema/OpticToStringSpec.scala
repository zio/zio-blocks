package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object OpticToStringSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("OpticToStringSpec")(
    test("Lens.toString") {
      val optic = Schema[Person].reflect.asRecord.get.lensByName[String]("name").get
      assert(optic.toString)(equalTo("Lens(_.name)"))
    },
    test("composed Lens.toString") {
      val address = Schema[Person].reflect.asRecord.get.lensByName[Address]("address").get
      val street = Schema[Address].reflect.asRecord.get.lensByName[String]("street").get
      val composed = address.apply(street)
      assert(composed.toString)(equalTo("Lens(_.address.street)"))
    },
    test("Prism.toString") {
      val optic = Schema[Option[String]].reflect.asVariant.get.prismByName[Some[String]]("Some").get
      assert(optic.toString)(equalTo("Prism(_.when[Some].value)"))
    },
    test("Optional.at.toString") {
        val optic = Optional.at(Schema.list[Int].reflect.asSequence.get.asInstanceOf[Reflect.Sequence.Bound[Int, List]], 0)
        assert(optic.toString)(equalTo("Optional(_.at(0))"))
    },
    test("Traversal.listValues.toString") {
        val optic = Traversal.listValues(Schema[Int].reflect)
        assert(optic.toString)(equalTo("Traversal(_.each)"))
    }
  )

  case class Address(street: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }
  case class Person(name: String, address: Address)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }
}
