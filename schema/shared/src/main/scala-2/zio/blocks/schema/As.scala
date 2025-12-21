package zio.blocks.schema

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Type class for bidirectional type-safe conversions between A and B.
 *
 * As[A, B] provides both `into` (A -> B) and `from` (B -> A) conversions,
 * ensuring that the types are compatible for round-trip conversions.
 *
 * @tparam A
 *   First type
 * @tparam B
 *   Second type
 */
trait As[A, B] {

  /**
   * Convert from A to B.
   */
  def into(input: A): Either[SchemaError, B]

  /**
   * Convert from B to A.
   */
  def from(input: B): Either[SchemaError, A]

  /**
   * Convert from A to B, throwing on failure.
   */
  final def intoOrThrow(input: A): B = into(input) match {
    case Right(b)  => b
    case Left(err) => throw err
  }

  /**
   * Convert from B to A, throwing on failure.
   */
  final def fromOrThrow(input: B): A = from(input) match {
    case Right(a)  => a
    case Left(err) => throw err
  }

  /**
   * Get the Into[A, B] instance from this As[A, B].
   */
  def asInto: Into[A, B] = new Into[A, B] {
    def into(input: A): Either[SchemaError, B] = As.this.into(input)
  }

  /**
   * Get the Into[B, A] instance from this As[A, B].
   */
  def asIntoReverse: Into[B, A] = new Into[B, A] {
    def into(input: B): Either[SchemaError, A] = As.this.from(input)
  }
}

object As {

  /**
   * Summon an As[A, B] instance from implicit scope or derive it.
   */
  def apply[A, B](implicit as: As[A, B]): As[A, B] = as

  /**
   * Automatically derive As[A, B] instances at compile time.
   */
  implicit def derived[A, B]: As[A, B] = macro AsMacros.deriveAs[A, B]

  /**
   * Identity conversion (A to A).
   */
  implicit def identity[A]: As[A, A] = new As[A, A] {
    def into(input: A): Either[SchemaError, A] = Right(input)
    def from(input: A): Either[SchemaError, A] = Right(input)
  }

  /**
   * Create an As[A, B] from two Into instances.
   */
  def fromInto[A, B](intoAB: Into[A, B], intoBA: Into[B, A]): As[A, B] = new As[A, B] {
    def into(input: A): Either[SchemaError, B] = intoAB.into(input)
    def from(input: B): Either[SchemaError, A] = intoBA.into(input)
  }
}

private object AsMacros {
  def deriveAs[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context): c.Expr[As[A, B]] = {
    import c.universe._

    val aType = weakTypeOf[A]
    val bType = weakTypeOf[B]

    // If types are the same, use identity
    if (aType =:= bType) {
      return c.Expr[As[A, B]](
        q"_root_.zio.blocks.schema.As.identity[$aType].asInstanceOf[_root_.zio.blocks.schema.As[$aType, $bType]]"
      )
    }

    // Create As from two Into instances
    c.Expr[As[A, B]](q"""
      _root_.zio.blocks.schema.As.fromInto(
        _root_.zio.blocks.schema.Into.derived[$aType, $bType],
        _root_.zio.blocks.schema.Into.derived[$bType, $aType]
      )
    """)
  }
}
