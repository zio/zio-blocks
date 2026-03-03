package structural

import zio.blocks.schema._
import util.ShowExpr.show

/**
 * Structural Types Reference — Sum Types
 *
 * Demonstrates converting sealed traits and enums to structural union schemas.
 * This example is Scala 3 only.
 *
 * Run with:
 *   sbt "schema-examples/runMain structural.StructuralSealedTraitExample"
 *   sbt "schema-examples/runMain structural.StructuralEnumExample"
 */

// ──────────────────────────────────────────────────────────────────────────
// Sealed Trait
// ──────────────────────────────────────────────────────────────────────────

object StructuralSealedTraitExample extends App {

  sealed trait Shape
  object Shape {
    case class Circle(radius: Double) extends Shape
    case class Rectangle(width: Double, height: Double) extends Shape
  }

  implicit val shapeSchema: Schema[Shape] = Schema.derived[Shape]

  val nominalSchema: Schema[Shape] = Schema.derived[Shape]
  val structuralSchema = nominalSchema.structural

  println("=== Sum Type: Shape (Sealed Trait) ===\n")

  // Encode a Circle variant using nominal schema
  val circle: Shape = Shape.Circle(5.0)
  val circleDynamic = nominalSchema.toDynamicValue(circle)

  println("Circle variant as DynamicValue:")
  show(circleDynamic)

  // Encode a Rectangle variant using nominal schema
  val rectangle: Shape = Shape.Rectangle(10.0, 20.0)
  val rectangleDynamic = nominalSchema.toDynamicValue(rectangle)

  println("\nRectangle variant as DynamicValue:")
  show(rectangleDynamic)

  // Decode from DynamicValue using nominal schema
  val decodedCircle = nominalSchema.fromDynamicValue(circleDynamic)
  println("\nDecoded Circle:")
  show(decodedCircle)

  // Show the structural schema representation
  println("\nStructural schema representation:")
  show("Union of Circle and Rectangle with method syntax")

  // Pattern match on decoded result
  decodedCircle match {
    case Right(_) =>
      println("\nSuccessfully decoded shape")
    case Left(error) =>
      println(s"\nDecoding failed: $error")
  }
}

// ──────────────────────────────────────────────────────────────────────────
// Enum (Scala 3)
// ──────────────────────────────────────────────────────────────────────────

object StructuralEnumExample extends App {

  enum Color {
    case Red, Green, Blue
  }
  object Color {
    implicit val schema: Schema[Color] = Schema.derived[Color]
  }

  enum Status {
    case Active, Inactive, Suspended
  }
  object Status {
    implicit val schema: Schema[Status] = Schema.derived[Status]
  }

  enum Fruit {
    case Apple(variety: String)
    case Orange(juicy: Boolean)
    case Banana(length: Int)
  }
  object Fruit {
    implicit val schema: Schema[Fruit] = Schema.derived[Fruit]
  }

  println("=== Sum Type: Color (Simple Enum) ===\n")

  val colorSchema: Schema[Color] = Schema.derived[Color]
  val colorStructural = colorSchema.structural

  val red: Color = Color.Red
  val redDynamic = colorSchema.toDynamicValue(red)

  println("Color.Red as DynamicValue:")
  show(redDynamic)

  println("\n=== Sum Type: Status (Simple Enum) ===\n")

  val statusSchema: Schema[Status] = Schema.derived[Status]
  val statusStructural = statusSchema.structural

  val active: Status = Status.Active
  val activeDynamic = statusSchema.toDynamicValue(active)

  println("Status.Active as DynamicValue:")
  show(activeDynamic)

  println("\n=== Sum Type: Fruit (Parameterized Enum) ===\n")

  val fruitSchema: Schema[Fruit] = Schema.derived[Fruit]
  val fruitStructural = fruitSchema.structural

  // Parameterized enum cases
  val apple: Fruit = Fruit.Apple("Granny Smith")
  val appleDynamic = fruitSchema.toDynamicValue(apple)

  println("Fruit.Apple as DynamicValue:")
  show(appleDynamic)

  val orange: Fruit = Fruit.Orange(true)
  val orangeDynamic = fruitSchema.toDynamicValue(orange)

  println("\nFruit.Orange as DynamicValue:")
  show(orangeDynamic)

  // Decode back
  val decodedFruit = fruitSchema.fromDynamicValue(appleDynamic)
  println("\nDecoded Apple:")
  show(decodedFruit)

  // Show structural representation
  println("\nStructural schema represents sum types as unions with method syntax")
}
