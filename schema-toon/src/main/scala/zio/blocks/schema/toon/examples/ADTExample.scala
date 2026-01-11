package zio.blocks.schema.toon.examples

import zio.blocks.schema.Schema
import zio.blocks.schema.toon.ToonFormat

/**
 * Example demonstrating ADT (Algebraic Data Type) encoding in TOON.
 *
 * Run with:
 * `sbt "schema-toon/runMain zio.blocks.schema.toon.examples.ADTExample"`
 */
object ADTExample extends App {

  // Simple sealed trait with case classes
  sealed trait Shape
  case class Circle(radius: Double)                   extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape
  case class Triangle(base: Double, height: Double)   extends Shape
  case object Point                                   extends Shape

  object Shape {
    implicit val schema: Schema[Shape] = Schema.derived
  }

  // More complex ADT with nesting
  sealed trait JsonValue
  case object JsonNull                                  extends JsonValue
  case class JsonBool(value: Boolean)                   extends JsonValue
  case class JsonNumber(value: Double)                  extends JsonValue
  case class JsonString(value: String)                  extends JsonValue
  case class JsonArray(elements: List[JsonValue])       extends JsonValue
  case class JsonObject(fields: Map[String, JsonValue]) extends JsonValue

  object JsonValue {
    implicit val schema: Schema[JsonValue] = Schema.derived
  }

  // Enum-style sealed trait (case objects only)
  sealed trait Status
  case object Pending    extends Status
  case object InProgress extends Status
  case object Completed  extends Status
  case object Failed     extends Status

  object Status {
    implicit val schema: Schema[Status] = Schema.derived
  }

  // Derive codecs
  val shapeCodec  = Shape.schema.derive(ToonFormat.deriver)
  val statusCodec = Status.schema.derive(ToonFormat.deriver)

  println("=== ADT (Sealed Trait) Examples ===")
  println()

  // Simple ADT examples
  println("--- Shape ADT ---")
  println()

  val circle: Shape = Circle(5.0)
  println("Circle:")
  println(shapeCodec.encodeToString(circle))
  println()

  val rectangle: Shape = Rectangle(10.0, 20.0)
  println("Rectangle:")
  println(shapeCodec.encodeToString(rectangle))
  println()

  val triangle: Shape = Triangle(15.0, 8.0)
  println("Triangle:")
  println(shapeCodec.encodeToString(triangle))
  println()

  val point: Shape = Point
  println("Point (case object):")
  println(shapeCodec.encodeToString(point))
  println()

  // Enum-style ADT
  println("--- Status Enum ---")
  println()

  for (status <- List(Pending, InProgress, Completed, Failed)) {
    println(s"$status: ${statusCodec.encodeToString(status)}")
  }
  println()

  // Array of ADT
  println("--- List of Shapes ---")
  println()

  val shapesCodec         = Schema[List[Shape]].derive(ToonFormat.deriver)
  val shapes: List[Shape] = List(Circle(1.0), Rectangle(2.0, 3.0), Point, Triangle(4.0, 5.0))
  println(shapesCodec.encodeToString(shapes))
}
