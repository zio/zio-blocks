package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema.Schema

// Variant test types
sealed trait Shape
case class Circle(radius: Double)                   extends Shape
case class Rectangle(width: Double, height: Double) extends Shape
case object Unknown                                 extends Shape

object Shape {
  implicit val schema: Schema[Shape] = Schema.derived
}

sealed trait Result[+A]
case class Success[A](value: A)     extends Result[A]
case class Failure(message: String) extends Result[Nothing]

object Result {
  implicit def schema[A](implicit a: Schema[A]): Schema[Result[A]] = Schema.derived
}

// Simple enum-like sealed trait
sealed trait Color
case object Red   extends Color
case object Green extends Color
case object Blue  extends Color

object Color {
  implicit val schema: Schema[Color] = Schema.derived
}

/**
 * Comprehensive tests for variant (sealed trait) codecs.
 */
object ComprehensiveVariantSpec extends ZIOSpecDefault {
  def spec = suite("ComprehensiveVariant")(
    suite("Simple Sealed Traits")(
      test("case class variant - Circle") {
        val codec        = Shape.schema.derive(ToonFormat.deriver)
        val shape: Shape = Circle(5.0)
        val encoded      = codec.encodeToString(shape)
        assertTrue(encoded.contains("Circle:") && encoded.contains("radius: 5.0"))
      },
      test("case class variant - Rectangle") {
        val codec        = Shape.schema.derive(ToonFormat.deriver)
        val shape: Shape = Rectangle(10.0, 20.0)
        val encoded      = codec.encodeToString(shape)
        assertTrue(
          encoded.contains("Rectangle:") &&
            encoded.contains("width: 10.0") &&
            encoded.contains("height: 20.0")
        )
      },
      test("case object variant") {
        val codec        = Shape.schema.derive(ToonFormat.deriver)
        val shape: Shape = Unknown
        val encoded      = codec.encodeToString(shape)
        assertTrue(encoded.contains("Unknown"))
      }
    ),
    suite("Enum-like Variants")(
      test("case object Red") {
        val codec        = Color.schema.derive(ToonFormat.deriver)
        val color: Color = Red
        val encoded      = codec.encodeToString(color)
        assertTrue(encoded.contains("Red"))
      },
      test("case object Green") {
        val codec        = Color.schema.derive(ToonFormat.deriver)
        val color: Color = Green
        val encoded      = codec.encodeToString(color)
        assertTrue(encoded.contains("Green"))
      },
      test("case object Blue") {
        val codec        = Color.schema.derive(ToonFormat.deriver)
        val color: Color = Blue
        val encoded      = codec.encodeToString(color)
        assertTrue(encoded.contains("Blue"))
      }
    ),
    suite("Parameterized Variants")(
      test("Success case") {
        val codec               = Result.schema[Int].derive(ToonFormat.deriver)
        val result: Result[Int] = Success(42)
        val encoded             = codec.encodeToString(result)
        assertTrue(encoded.contains("Success") && encoded.contains("42"))
      },
      test("Failure case") {
        val codec               = Result.schema[Int].derive(ToonFormat.deriver)
        val result: Result[Int] = Failure("Something went wrong")
        val encoded             = codec.encodeToString(result)
        assertTrue(encoded.contains("Failure") && encoded.contains("Something went wrong"))
      }
    )
  )
}
