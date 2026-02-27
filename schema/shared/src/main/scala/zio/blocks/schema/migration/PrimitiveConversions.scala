package zio.blocks.schema.migration

import zio.blocks.schema.{PrimitiveType, Schema}
import zio.blocks.schema.SchemaExpr

/**
 * Provides primitive type conversions for migrations.
 * These are safe type coercions that can be used in ChangeType actions.
 */
object PrimitiveConversions {

  /**
   * All supported primitive conversions.
   * Each entry is (source type, target type, conversion function).
   */
  val all: List[(PrimitiveType[_], PrimitiveType[_], (Any) => Any)] = List(
    // Widening conversions (always safe)
    (PrimitiveType.Byte, PrimitiveType.Short, (v: Any) => v.asInstanceOf[Byte].toShort),
    (PrimitiveType.Byte, PrimitiveType.Int, (v: Any) => v.asInstanceOf[Byte].toInt),
    (PrimitiveType.Byte, PrimitiveType.Long, (v: Any) => v.asInstanceOf[Byte].toLong),
    (PrimitiveType.Byte, PrimitiveType.Float, (v: Any) => v.asInstanceOf[Byte].toFloat),
    (PrimitiveType.Byte, PrimitiveType.Double, (v: Any) => v.asInstanceOf[Byte].toDouble),

    (PrimitiveType.Short, PrimitiveType.Int, (v: Any) => v.asInstanceOf[Short].toInt),
    (PrimitiveType.Short, PrimitiveType.Long, (v: Any) => v.asInstanceOf[Short].toLong),
    (PrimitiveType.Short, PrimitiveType.Float, (v: Any) => v.asInstanceOf[Short].toFloat),
    (PrimitiveType.Short, PrimitiveType.Double, (v: Any) => v.asInstanceOf[Short].toDouble),

    (PrimitiveType.Int, PrimitiveType.Long, (v: Any) => v.asInstanceOf[Int].toLong),
    (PrimitiveType.Int, PrimitiveType.Float, (v: Any) => v.asInstanceOf[Int].toFloat),
    (PrimitiveType.Int, PrimitiveType.Double, (v: Any) => v.asInstanceOf[Int].toDouble),

    (PrimitiveType.Long, PrimitiveType.Float, (v: Any) => v.asInstanceOf[Long].toFloat),
    (PrimitiveType.Long, PrimitiveType.Double, (v: Any) => v.asInstanceOf[Long].toDouble),

    (PrimitiveType.Float, PrimitiveType.Double, (v: Any) => v.asInstanceOf[Float].toDouble),

    // String conversions
    (PrimitiveType.String, PrimitiveType.Int, (v: Any) => v.asInstanceOf[String].toInt),
    (PrimitiveType.String, PrimitiveType.Long, (v: Any) => v.asInstanceOf[String].toLong),
    (PrimitiveType.String, PrimitiveType.Double, (v: Any) => v.asInstanceOf[String].toDouble),
    (PrimitiveType.String, PrimitiveType.Float, (v: Any) => v.asInstanceOf[String].toFloat),
    (PrimitiveType.String, PrimitiveType.BigInt, (v: Any) => BigInt(v.asInstanceOf[String])),
    (PrimitiveType.String, PrimitiveType.BigDecimal, (v: Any) => BigDecimal(v.asInstanceOf[String])),

    // Character conversions
    (PrimitiveType.Char, PrimitiveType.Int, (v: Any) => v.asInstanceOf[Char].toInt),
    (PrimitiveType.Char, PrimitiveType.String, (v: Any) => String.valueOf(v.asInstanceOf[Char]))
  )

  /**
   * Checks if a conversion from source to target is supported.
   */
  def isSupported(source: PrimitiveType[_], target: PrimitiveType[_]): Boolean =
    all.exists { case (s, t, _) => s == source && t == target }

  /**
   * Gets a conversion function if supported.
   */
  def getConversion(source: PrimitiveType[_], target: PrimitiveType[_]): Option[(Any) => Any] =
    all.find { case (s, t, _) => s == source && t == target }.map { case (_, _, f) => f }

  /**
   * Creates a SchemaExpr that performs the conversion.
   * Returns None if the conversion is not supported.
   */
  def createConversionExpr[Source, Target](
    sourceSchema: Schema[Source],
    targetSchema: Schema[Target]
  ): Option[SchemaExpr[?, Target]] = {
    // Simplified - would need to get primitive types from schemas
    None
  }

  /**
   * Numeric widening - always safe.
   */
  object Widening {

    def canWiden(source: PrimitiveType[_], target: PrimitiveType[_]): Boolean =
      (source, target) match {
        // Byte
        (PrimitiveType.Byte, PrimitiveType.Short) => true
        (PrimitiveType.Byte, PrimitiveType.Int) => true
        (PrimitiveType.Byte, PrimitiveType.Long) => true
        (PrimitiveType.Byte, PrimitiveType.Float) => true
        (PrimitiveType.Byte, PrimitiveType.Double) => true
        // Short
        (PrimitiveType.Short, PrimitiveType.Int) => true
        (PrimitiveType.Short, PrimitiveType.Long) => true
        (PrimitiveType.Short, PrimitiveType.Float) => true
        (PrimitiveType.Short, PrimitiveType.Double) => true
        // Int
        (PrimitiveType.Int, PrimitiveType.Long) => true
        (PrimitiveType.Int, PrimitiveType.Float) => true
        (PrimitiveType.Int, PrimitiveType.Double) => true
        // Long
        (PrimitiveType.Long, PrimitiveType.Float) => true
        (PrimitiveType.Long, PrimitiveType.Double) => true
        // Float
        (PrimitiveType.Float, PrimitiveType.Double) => true
        // Same type
        (a, b) => a == b
        case _ => false
      }
  }

  /**
   * Numeric narrowing - requires bounds checking.
   */
  object Narrowing {

    def canNarrow(source: PrimitiveType[_], target: PrimitiveType[_]): Boolean =
      !Widening.canWiden(source, target) &&
        (source, target) match {
          case (PrimitiveType.Short, PrimitiveType.Byte) => true
          case (PrimitiveType.Int, PrimitiveType.Byte) => true
          case (PrimitiveType.Int, PrimitiveType.Short) => true
          case (PrimitiveType.Long, PrimitiveType.Byte) => true
          case (PrimitiveType.Long, PrimitiveType.Short) => true
          case (PrimitiveType.Long, PrimitiveType.Int) => true
          case (PrimitiveType.Float, PrimitiveType.Byte) => true
          case (PrimitiveType.Float, PrimitiveType.Short) => true
          case (PrimitiveType.Float, PrimitiveType.Int) => true
          case (PrimitiveType.Float, PrimitiveType.Long) => true
          case (PrimitiveType.Double, PrimitiveType.Byte) => true
          case (PrimitiveType.Double, PrimitiveType.Short) => true
          case (PrimitiveType.Double, PrimitiveType.Int) => true
          case (PrimitiveType.Double, PrimitiveType.Long) => true
          case (PrimitiveType.Double, PrimitiveType.Float) => true
          case _ => false
        }

    def narrowByte(value: Long): Option[Byte] =
      if (value >= Byte.MinValue && value <= Byte.MaxValue) Some(value.toByte)
      else None

    def narrowShort(value: Long): Option[Short] =
      if (value >= Short.MinValue && value <= Short.MaxValue) Some(value.toShort)
      else None

    def narrowInt(value: Long): Option[Int] =
      if (value >= Int.MinValue && value <= Int.MaxValue) Some(value.toInt)
      else None
  }
}
