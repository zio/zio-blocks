package ziosschemamigration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._

/**
 * Migrating from ZIO Schema to ZIO Blocks Schema — Step 1: Schema Derivation
 * and Primitives
 *
 * This example demonstrates:
 *   - Deriving schemas for case classes and sealed traits (identical call sites
 *     to ZIO Schema)
 *   - Using primitive schemas (Int, String, UUID, java.time.*, etc.)
 *   - Converting between typed values and DynamicValue
 *   - Using Option, Either, and collection schemas
 *
 * Run with: sbt "examples/runMain
 * ziosschemamigration.Step1SchemaDerivedAndPrimitives"
 */
object Step1SchemaDerivedAndPrimitives extends App {

  // ─────────────────────────────────────────────────────────────────────────
  // 1. Case class derivation
  //    In ZIO Schema: DeriveSchema.gen
  //    In ZIO Blocks: Schema.derived[A]
  // ─────────────────────────────────────────────────────────────────────────

  final case class Address(street: String, city: String, postCode: String)
  final case class Person(name: String, age: Int, address: Address)

  object Address {
    implicit val schema: Schema[Address] = Schema.derived[Address]
  }
  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
  }

  val person = Person("Alice", 30, Address("1 Main St", "Springfield", "12345"))
  println(s"Person: $person")

  // ─────────────────────────────────────────────────────────────────────────
  // 2. Sealed trait (variant) derivation
  // ─────────────────────────────────────────────────────────────────────────

  sealed trait Shape
  final case class Circle(radius: Double)                   extends Shape
  final case class Rectangle(width: Double, height: Double) extends Shape

  object Shape {
    implicit val schema: Schema[Shape] = Schema.derived[Shape]
  }

  val circle: Shape    = Circle(5.0)
  val rectangle: Shape = Rectangle(4.0, 6.0)
  println(s"Shape 1: $circle")
  println(s"Shape 2: $rectangle")

  // ─────────────────────────────────────────────────────────────────────────
  // 3. Primitive schemas — call sites are identical to ZIO Schema
  // ─────────────────────────────────────────────────────────────────────────

  val intSchema: Schema[Int]                   = Schema[Int]
  val strSchema: Schema[String]                = Schema[String]
  val uuidSchema: Schema[java.util.UUID]       = Schema[java.util.UUID]
  val instantSchema: Schema[java.time.Instant] = Schema[java.time.Instant]
  println(s"Int schema: $intSchema")
  println(s"Instant schema: $instantSchema")

  // ─────────────────────────────────────────────────────────────────────────
  // 4. Option, Either, and collection schemas
  // ─────────────────────────────────────────────────────────────────────────

  val optSchema: Schema[Option[String]]         = Schema[Option[String]]
  val eitherSchema: Schema[Either[String, Int]] = Schema[Either[String, Int]]
  val listSchema: Schema[List[Person]]          = Schema[List[Person]]
  val mapSchema: Schema[Map[String, Int]]       = Schema[Map[String, Int]]
  println(s"Option[String] schema: $optSchema")
  println(s"List[Person] schema:   $listSchema")

  // ─────────────────────────────────────────────────────────────────────────
  // 5. Converting typed values to and from DynamicValue
  //    ZIO Schema:        schema.toDynamic(value) / dv.toTypedValue[A]
  //    ZIO Blocks Schema: schema.toDynamicValue(value) / schema.fromDynamicValue(dv)
  // ─────────────────────────────────────────────────────────────────────────

  val dv: DynamicValue = Person.schema.toDynamicValue(person)
  println(s"\nDynamicValue:\n  $dv")

  val back: Either[SchemaError, Person] = Person.schema.fromDynamicValue(dv)
  println(s"Roundtrip: $back")

  // ─────────────────────────────────────────────────────────────────────────
  // 6. DynamicValue structure (6 cases vs ZIO Schema's 15+)
  // ─────────────────────────────────────────────────────────────────────────

  val manualRecord = DynamicValue.Record(
    Chunk(
      "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
      "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
    )
  )
  println(s"\nManual DynamicValue.Record: $manualRecord")

  // Navigate a DynamicValue (rich API not present in ZIO Schema)
  // DynamicValue.get returns a DynamicValueSelection (supports chaining and multi-value ops)
  val nameSelection: DynamicValueSelection = manualRecord.get("name")
  println(s"get(\"name\"): ${nameSelection.one}")
}
