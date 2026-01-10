package zio.blocks.schema.migration

import zio.schema.DynamicValue


/**
 * Represents a failure during expression evaluation (Borrowed from ZIO Schema).
 */
case class OpticCheck(message: String)

/**
 * Type Class for Arithmetic Operations (Borrowed from ZIO Schema Logic).
 * Allows generic math on Int, Long, Double, etc.
 */
trait IsNumeric[A] {
  def plus(x: A, y: A): A
  def minus(x: A, y: A): A
  def times(x: A, y: A): A
  // Helper to convert result back to DynamicValue
  def toDynamic(a: A): DynamicValue
}

object IsNumeric {
  implicit val intNumeric: IsNumeric[Int] = new IsNumeric[Int] {
    def plus(x: Int, y: Int): Int = x + y
    def minus(x: Int, y: Int): Int = x - y
    def times(x: Int, y: Int): Int = x * y
    def toDynamic(a: Int): DynamicValue = DynamicValue.Primitive(a)
  }

  implicit val longNumeric: IsNumeric[Long] = new IsNumeric[Long] {
    def plus(x: Long, y: Long): Long = x + y
    def minus(x: Long, y: Long): Long = x - y
    def times(x: Long, y: Long): Long = x * y
    def toDynamic(a: Long): DynamicValue = DynamicValue.Primitive(a)
  }

  implicit val doubleNumeric: IsNumeric[Double] = new IsNumeric[Double] {
    def plus(x: Double, y: Double): Double = x + y
    def minus(x: Double, y: Double): Double = x - y
    def times(x: Double, y: Double): Double = x * y
    def toDynamic(a: Double): DynamicValue = DynamicValue.Primitive(a)
  }
}