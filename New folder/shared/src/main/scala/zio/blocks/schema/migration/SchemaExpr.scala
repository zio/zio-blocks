package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, PrimitiveValue, Schema}
import scala.annotation.unused

/**
 * SchemaExpr represents a Pure Data Abstract Syntax Tree (AST) used to define
 * value-level transformations during migrations. * @tparam A The source type
 * from which data is being migrated. The expression evaluates to a DynamicValue
 * at runtime.
 */
sealed trait SchemaExpr[A] extends Product with Serializable

object SchemaExpr {

  /** Represents a fallback value when a field is missing or null. */
  case class DefaultValue[A](value: DynamicValue) extends SchemaExpr[A]

  /** Represents a fixed value that ignores the source input. */
  case class Constant[A](value: DynamicValue) extends SchemaExpr[A]

  /** Passes the source value through without modification. */
  case class Identity[A]() extends SchemaExpr[A]

  /** Supported operations for type conversion within an expression. */
  sealed trait ConversionOp extends Product with Serializable
  object ConversionOp {
    case object ToString extends ConversionOp
    case object ToInt    extends ConversionOp
  }

  /** Wraps an expression to apply a specific conversion operation. */
  case class Converted[A](operand: SchemaExpr[A], op: ConversionOp) extends SchemaExpr[A]

  // --- Smart Constructors ---

  /**
   * Creates a constant expression from a typed value using its schema.
   */
  def constant[A, T](value: T, schema: Schema[T]): SchemaExpr[A] =
    Constant(schema.toDynamicValue(value))

  /**
   * Generates a default value expression. Note: This expression is independent
   * of the source type 'A' and carries the default value required by the target
   * schema.
   */
  def default[A, T](@unused schema: Schema[T]): SchemaExpr[A] =
    // Standardizing on Unit as a prototype default; to be handled by the interpreter.
    DefaultValue(DynamicValue.Primitive(PrimitiveValue.Unit))
}
