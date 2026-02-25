package ziosschemamigration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._

/**
 * Migrating from ZIO Schema to ZIO Blocks Schema — Step 3: Optics and
 * DynamicSchema
 *
 * This example demonstrates:
 *   - Obtaining Lens and Prism from a derived schema (replaces AccessorBuilder)
 *   - Using Lens.get, Lens.modify, Lens.replace
 *   - Using Prism.getOption, Prism.reverseGet
 *   - DynamicSchema (replaces MetaSchema / schema.ast)
 *   - Validating DynamicValue against a DynamicSchema
 *
 * Run with: sbt "examples/runMain
 * ziosschemamigration.Step3OpticsAndDynamicSchema"
 */
object Step3OpticsAndDynamicSchema extends App {

  // ─────────────────────────────────────────────────────────────────────────
  // Domain types
  // ─────────────────────────────────────────────────────────────────────────

  final case class Address(street: String, city: String)
  final case class Person(name: String, age: Int, address: Address)

  object Address {
    implicit val schema: Schema[Address] = Schema.derived[Address]

    val street: Lens[Address, String] =
      schema.reflect.asRecord.get.lensByName[String]("street").get
    val city: Lens[Address, String] =
      schema.reflect.asRecord.get.lensByName[String]("city").get
  }

  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]

    // Obtain lenses from the schema's reflect tree
    // ZIO Schema: schema.makeAccessors(ZioOpticsBuilder) → (nameLens, ageLens, addressLens)
    // ZIO Blocks: schema.reflect.asRecord.get.lensByName[A]("field")
    val name: Lens[Person, String] =
      schema.reflect.asRecord.get.lensByName[String]("name").get
    val age: Lens[Person, Int] =
      schema.reflect.asRecord.get.lensByName[Int]("age").get
    val address: Lens[Person, Address] =
      schema.reflect.asRecord.get.lensByName[Address]("address").get
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 1. Lens usage
  //    get / modify / replace
  // ─────────────────────────────────────────────────────────────────────────

  val person = Person("Alice", 30, Address("1 Main St", "Springfield"))

  val name: String  = Person.name.get(person) // "Alice"
  val upper: Person = Person.name.modify(person, _.toUpperCase)
  val older: Person = Person.age.modify(person, _ + 1)

  println(s"Original:  $person")
  println(s"name:      $name")
  println(s"uppercase: $upper")
  println(s"older:     $older")

  // ─────────────────────────────────────────────────────────────────────────
  // 2. Lens composition
  //    In ZIO Blocks, apply one optic inside another using the apply method
  // ─────────────────────────────────────────────────────────────────────────

  val streetOfPerson: Lens[Person, String] = Person.address(Address.street)
  val onNewStreet: Person                  = streetOfPerson.modify(person, _ => "42 Oak Ave")
  println(s"\nComposed lens:\n  $onNewStreet")

  // ─────────────────────────────────────────────────────────────────────────
  // 3. Prism for sealed traits
  //    ZIO Schema: make a Prism via makeAccessors
  //    ZIO Blocks: schema.reflect.asVariant.get.prismByName[SubType]("CaseName")
  // ─────────────────────────────────────────────────────────────────────────

  sealed trait Shape
  final case class Circle(radius: Double)                   extends Shape
  final case class Rectangle(width: Double, height: Double) extends Shape

  object Shape {
    implicit val schema: Schema[Shape] = Schema.derived[Shape]

    val circle: Prism[Shape, Circle] =
      schema.reflect.asVariant.get.prismByName[Circle]("Circle").get
    val rectangle: Prism[Shape, Rectangle] =
      schema.reflect.asVariant.get.prismByName[Rectangle]("Rectangle").get
  }

  val c: Shape = Circle(5.0)
  val r: Shape = Rectangle(4.0, 6.0)

  println(s"\nPrism.getOption on Circle:    ${Shape.circle.getOption(c)}")
  println(s"Prism.getOption on Rectangle: ${Shape.circle.getOption(r)}")
  println(s"Prism.reverseGet:             ${Shape.circle.reverseGet(Circle(3.0))}")

  // ─────────────────────────────────────────────────────────────────────────
  // 4. DynamicSchema (replaces MetaSchema / schema.ast)
  //    ZIO Schema: schema.ast: MetaSchema; meta.toSchema: Schema[_]
  //    ZIO Blocks: schema.toDynamicSchema: DynamicSchema
  // ─────────────────────────────────────────────────────────────────────────

  val personDynSchema: DynamicSchema = Person.schema.toDynamicSchema

  // Validate a conforming DynamicValue
  val conformingValue = DynamicValue.Record(
    Chunk(
      "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
      "age"     -> DynamicValue.Primitive(PrimitiveValue.Int(25)),
      "address" -> DynamicValue.Record(
        Chunk(
          "street" -> DynamicValue.Primitive(PrimitiveValue.String("99 Pine Rd")),
          "city"   -> DynamicValue.Primitive(PrimitiveValue.String("Shelbyville"))
        )
      )
    )
  )

  val nonConformingValue = DynamicValue.Record(
    Chunk(
      "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob"))
      // missing "age" and "address"
    )
  )

  println(s"\nDynamicSchema.conforms (valid):     ${personDynSchema.conforms(conformingValue)}")
  println(s"DynamicSchema.check    (valid):     ${personDynSchema.check(conformingValue)}")
  println(s"DynamicSchema.conforms (invalid):   ${personDynSchema.conforms(nonConformingValue)}")
  println(s"DynamicSchema.check    (invalid):   ${personDynSchema.check(nonConformingValue)}")
}
