package zio.schema.migration

import zio._
import zio.test._
import zio.schema._

/**
 * Test HOAS extraction with nested field access.
 */
object NestedHOASTest extends ZIOSpecDefault {

  case class Address(street: String, city: String, zip: String)
  case class Person(name: String, age: Int, address: Address)

  given Schema[Address] = DeriveSchema.gen[Address]
  given Schema[Person]  = DeriveSchema.gen[Person]

  def spec = suite("NestedHOASTest")(
    test("HOAS should extract nested field path") {
      val fieldPath = HOASPathMacros.extractPathHOAS[Person](_.address.street)

      assertTrue(
        fieldPath.serialize == "address.street"
      )
    },
    test("HOAS should extract triple nested path") {
      val fieldPath = HOASPathMacros.extractPathHOAS[Person](p => p.address.city)

      assertTrue(
        fieldPath.serialize == "address.city"
      )
    }
  )
}
