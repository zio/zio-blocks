package into

import zio.blocks.schema.Into

// Demonstrates migrating a product type across API versions.
// The macro handles: Int → Long widening, and a new Option field
// that defaults to None when absent from the source type.
object IntoSchemaEvolutionExample extends App {

  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Long, email: Option[String])

  val migrate = Into.derived[PersonV1, PersonV2]

  // age widens from Int to Long; email defaults to None
  println(migrate.into(PersonV1("Alice", 30)))
  println(migrate.into(PersonV1("Bob", 25)))
}
