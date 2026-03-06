package comptime

import zio.blocks.schema._
import zio.blocks.schema.comptime.Allows
import Allows.{Primitive, Record}
import util.ShowExpr.show

// ---------------------------------------------------------------------------
// Sealed trait auto-unwrap example
//
// Sealed traits and enums are automatically unwrapped by the Allows macro.
// Each case is checked individually against the grammar — no Variant node
// is needed.
//
// Auto-unwrap is recursive: if a case is itself a sealed trait, its cases
// are unwrapped too, to any depth.
//
// Zero-field records (case objects) are vacuously true for any Record[A]
// constraint.
// ---------------------------------------------------------------------------

// Simple sealed trait with case classes and a case object
sealed trait Shape
case class Circle(radius: Double)                   extends Shape
case class Rectangle(width: Double, height: Double) extends Shape
case object Point                                   extends Shape
object Shape { implicit val schema: Schema[Shape] = Schema.derived }

// Nested sealed trait hierarchy — two levels deep
sealed trait Expr
sealed trait BinaryOp                                              extends Expr
case class Add(left: Double, right: Double)                        extends BinaryOp
case class Multiply(left: Double, right: Double)                   extends BinaryOp
case class Literal(value: Double)                                  extends Expr
case object Zero                                                   extends Expr
object Expr { implicit val schema: Schema[Expr] = Schema.derived }

// All-singleton enum (all case objects)
sealed trait Color
case object Red   extends Color
case object Green extends Color
case object Blue  extends Color
object Color { implicit val schema: Schema[Color] = Schema.derived }

object SealedTraitValidator {

  /** Validate that a value's type has a flat record structure. */
  def validate[A](value: A)(implicit schema: Schema[A], ev: Allows[A, Record[Primitive]]): String = {
    val dv = schema.toDynamicValue(value)
    dv match {
      case DynamicValue.Variant(caseName, inner) =>
        s"Valid variant case '$caseName': ${inner.toJson}"
      case DynamicValue.Record(fields) =>
        s"Valid record with ${fields.size} field(s): ${fields.map(_._1).mkString(", ")}"
      case _ =>
        s"Valid: ${dv.toJson}"
    }
  }
}

// ---------------------------------------------------------------------------
// Demonstration
// ---------------------------------------------------------------------------

object AllowsSealedTraitExample extends App {

  // Simple sealed trait — all cases checked against Record[Primitive]
  // Circle: Record(radius: Double) — satisfies Record[Primitive]
  // Rectangle: Record(width: Double, height: Double) — satisfies Record[Primitive]
  // Point: zero-field case object — vacuously true
  show(SealedTraitValidator.validate[Shape](Circle(3.14)))
  show(SealedTraitValidator.validate[Shape](Rectangle(4.0, 5.0)))
  show(SealedTraitValidator.validate[Shape](Point))

  // Nested sealed trait — auto-unwrap is recursive
  // BinaryOp is itself sealed with Add and Multiply
  // All leaf cases have only Double fields — satisfies Record[Primitive]
  show(SealedTraitValidator.validate[Expr](Add(1.0, 2.0)))
  show(SealedTraitValidator.validate[Expr](Multiply(3.0, 4.0)))
  show(SealedTraitValidator.validate[Expr](Literal(42.0)))
  show(SealedTraitValidator.validate[Expr](Zero))

  // All-singleton enum — every case is a zero-field record (vacuously true)
  show(SealedTraitValidator.validate[Color](Red))
  show(SealedTraitValidator.validate[Color](Green))
  show(SealedTraitValidator.validate[Color](Blue))
}
