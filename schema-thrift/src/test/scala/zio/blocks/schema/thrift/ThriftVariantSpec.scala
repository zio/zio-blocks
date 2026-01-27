package zio.blocks.schema.thrift

import zio.blocks.schema._
import zio.test._
import java.nio.ByteBuffer

/**
 * Extended tests for variant (sealed trait/enum) encoding and decoding with
 * ThriftFormat.
 */
object ThriftVariantSpec extends SchemaBaseSpec {

  sealed trait Color

  object Color {
    case object Red    extends Color
    case object Green  extends Color
    case object Blue   extends Color
    case object Yellow extends Color
    case object Purple extends Color

    implicit val schema: Schema[Color] = Schema.derived
  }

  sealed trait Shape

  object Shape {
    case class Circle(radius: Double)                   extends Shape
    case class Rectangle(width: Double, height: Double) extends Shape
    case class Triangle(base: Double, height: Double)   extends Shape
    case class Point()                                  extends Shape

    implicit val schema: Schema[Shape] = Schema.derived
  }

  sealed trait Tree

  object Tree {
    case class Leaf(value: Int)                extends Tree
    case class Branch(left: Tree, right: Tree) extends Tree

    implicit val schema: Schema[Tree] = Schema.derived
  }

  sealed trait Response

  object Response {
    case class Success(data: String)             extends Response
    case class Error(code: Int, message: String) extends Response
    case object NotFound                         extends Response
    case object Pending                          extends Response

    implicit val schema: Schema[Response] = Schema.derived
  }

  case class RecordWithVariant(id: Int, color: Color)

  object RecordWithVariant {
    implicit val schema: Schema[RecordWithVariant] = Schema.derived
  }

  case class VariantInList(shapes: List[Shape])

  object VariantInList {
    implicit val schema: Schema[VariantInList] = Schema.derived
  }

  case class VariantInOption(maybeColor: Option[Color])

  object VariantInOption {
    implicit val schema: Schema[VariantInOption] = Schema.derived
  }

  case class NestedVariants(outer: Response, inner: Option[Shape])

  object NestedVariants {
    implicit val schema: Schema[NestedVariants] = Schema.derived
  }

  def roundTrip[A](value: A)(implicit schema: Schema[A]): TestResult = {
    val buffer = ByteBuffer.allocate(8192)
    schema.encode(ThriftFormat)(buffer)(value)
    buffer.flip()
    assertTrue(schema.decode(ThriftFormat)(buffer) == Right(value))
  }

  def spec = suite("ThriftVariantSpec")(
    suite("case object variants")(
      test("encode/decode all Color case objects") {
        roundTrip[Color](Color.Red) &&
        roundTrip[Color](Color.Green) &&
        roundTrip[Color](Color.Blue) &&
        roundTrip[Color](Color.Yellow) &&
        roundTrip[Color](Color.Purple)
      },
      test("encode/decode Response case objects") {
        roundTrip[Response](Response.NotFound) &&
        roundTrip[Response](Response.Pending)
      }
    ),
    suite("case class variants")(
      test("encode/decode Shape case classes") {
        roundTrip[Shape](Shape.Circle(5.0)) &&
        roundTrip[Shape](Shape.Rectangle(10.0, 20.0)) &&
        roundTrip[Shape](Shape.Triangle(15.0, 8.0)) &&
        roundTrip[Shape](Shape.Point())
      },
      test("encode/decode Response case classes") {
        roundTrip[Response](Response.Success("all good")) &&
        roundTrip[Response](Response.Error(500, "internal error"))
      }
    ),
    suite("recursive variants")(
      test("encode/decode simple Tree") {
        roundTrip[Tree](Tree.Leaf(42))
      },
      test("encode/decode nested Tree") {
        roundTrip[Tree](
          Tree.Branch(
            Tree.Leaf(1),
            Tree.Branch(Tree.Leaf(2), Tree.Leaf(3))
          )
        )
      },
      test("encode/decode deep Tree") {
        val deep: Tree = Tree.Branch(
          Tree.Branch(
            Tree.Branch(Tree.Leaf(1), Tree.Leaf(2)),
            Tree.Leaf(3)
          ),
          Tree.Branch(
            Tree.Leaf(4),
            Tree.Branch(Tree.Leaf(5), Tree.Leaf(6))
          )
        )
        roundTrip(deep)
      }
    ),
    suite("variant in record")(
      test("encode/decode record with Color variant") {
        roundTrip(RecordWithVariant(1, Color.Red)) &&
        roundTrip(RecordWithVariant(2, Color.Blue)) &&
        roundTrip(RecordWithVariant(3, Color.Purple))
      }
    ),
    suite("variant in collections")(
      test("encode/decode List of Shape variants") {
        roundTrip(
          VariantInList(
            List(
              Shape.Circle(1.0),
              Shape.Rectangle(2.0, 3.0),
              Shape.Triangle(4.0, 5.0),
              Shape.Point()
            )
          )
        )
      },
      test("encode/decode empty List of variants") {
        roundTrip(VariantInList(List.empty))
      }
    ),
    suite("variant in Option")(
      test("encode/decode Option[Color] - Some") {
        roundTrip(VariantInOption(Some(Color.Green)))
      },
      test("encode/decode Option[Color] - None") {
        roundTrip(VariantInOption(None))
      }
    ),
    suite("nested variants")(
      test("encode/decode nested variants - Success with Circle") {
        roundTrip(NestedVariants(Response.Success("ok"), Some(Shape.Circle(10.0))))
      },
      test("encode/decode nested variants - Error with Rectangle") {
        roundTrip(NestedVariants(Response.Error(404, "not found"), Some(Shape.Rectangle(5.0, 10.0))))
      },
      test("encode/decode nested variants - NotFound with None") {
        roundTrip(NestedVariants(Response.NotFound, None))
      }
    ),
    suite("variant edge cases")(
      test("encode/decode variant with zero values") {
        roundTrip[Shape](Shape.Circle(0.0)) &&
        roundTrip[Shape](Shape.Rectangle(0.0, 0.0))
      },
      test("encode/decode variant with extreme values") {
        roundTrip[Shape](Shape.Circle(Double.MaxValue)) &&
        roundTrip[Shape](Shape.Circle(Double.MinValue))
      },
      test("encode/decode many variants in sequence") {
        val shapes = (1 to 100).map(i => Shape.Circle(i.toDouble)).toList
        roundTrip(VariantInList(shapes))
      }
    )
  )
}
