package zio.blocks.schema.structural

import zio.test._
import zio.blocks.schema._

object IntoIntegrationSpec extends ZIOSpecDefault {
  def spec = suite("IntoIntegrationSpec")(
    test("nominal to structural conversion via Into") {
      case class Person(name: String, age: Int)
      type PersonStructure = { def name: String; def age: Int }

      implicit val personSchema: Schema[Person]              = Schema.derived[Person]
      implicit val structuralSchema: Schema[PersonStructure] = Schema.derived[PersonStructure]

      val person = Person("Alice", 30)
      val into   = Into.derived[Person, PersonStructure]

      val result = into.into(person)
      assertTrue(result.isRight)

      // Convert back using another Into to verify content
      val back      = Into.derived[PersonStructure, Person]
      val roundTrip = result.flatMap(back.into)

      assertTrue(roundTrip == Right(person))
    },
    test("composed conversion: V1 -> Structural -> V2") {
      // Version 1 of a type
      case class PersonV1(firstName: String, lastName: String, age: Int)
      // Version 2 with combined name field
      case class PersonV2(fullName: String, age: Int)
      // Structural intermediary
      type PersonStructure = { def fullName: String; def age: Int }

      implicit val v1Schema: Schema[PersonV1]                = Schema.derived[PersonV1]
      implicit val v2Schema: Schema[PersonV2]                = Schema.derived[PersonV2]
      implicit val structuralSchema: Schema[PersonStructure] = Schema.derived[PersonStructure]

      // Custom V1 -> Structural conversion (combines names)
      val v1 = PersonV1("John", "Doe", 30)

      // Manual transformation to structural
      val structural = new zio.blocks.schema.binding.StructuralValue(
        Map("fullName" -> s"${v1.firstName} ${v1.lastName}", "age" -> v1.age)
      ).asInstanceOf[PersonStructure]

      // Structural -> V2 via Into
      val structuralToV2 = Into.derived[PersonStructure, PersonV2]
      val result         = structuralToV2.into(structural)

      assertTrue(result == Right(PersonV2("John Doe", 30)))
    },
    test("structural to nominal with nested types") {
      case class Address(city: String, zip: Int)
      case class Person(name: String, address: Address)
      type AddressStructure = { def city: String; def zip: Int }
      type PersonStructure  = { def name: String; def address: AddressStructure }

      implicit val addressSchema: Schema[Address]                    = Schema.derived[Address]
      implicit val personSchema: Schema[Person]                      = Schema.derived[Person]
      implicit val addressStructuralSchema: Schema[AddressStructure] = Schema.derived[AddressStructure]
      implicit val personStructuralSchema: Schema[PersonStructure]   = Schema.derived[PersonStructure]

      val person = Person("Alice", Address("NYC", 10001))
      val into   = Into.derived[Person, PersonStructure]
      val back   = Into.derived[PersonStructure, Person]

      val roundTrip = into.into(person).flatMap(back.into)

      assertTrue(roundTrip == Right(person))
    }
  )
}
