package zio.blocks.schema.structural

import scala.Selectable

/**
 * Scala 3 implementation of structural type access using Selectable.
 *
 * Selectable is a marker trait in Scala 3 that enables dynamic field access
 * with compile-time type checking through structural types.
 *
 * Usage:
 * {{{
 * val structural: StructuralSelectable = ...
 * val name: String = structural.selectDynamic("name").asInstanceOf[String]
 * }}}
 */
trait StructuralSelectable extends Selectable {

  /**
   * The underlying structural value.
   */
  def underlying: StructuralValue

  /**
   * Dynamic field selection.
   */
  def selectDynamic(name: String): Any =
    underlying.selectDynamic(name) match {
      case Right(value) => value
      case Left(error)  => throw new NoSuchFieldException(s"Field '$name' not found: ${error.message}")
    }
}

object StructuralSelectable {

  /**
   * Create a StructuralSelectable from a StructuralValue.
   */
  def apply(value: StructuralValue): StructuralSelectable = new StructuralSelectable {
    val underlying: StructuralValue = value
  }

  /**
   * Create a StructuralSelectable from any value with a ToStructural instance.
   */
  def from[A](value: A)(using ts: ToStructural[A]): StructuralSelectable =
    apply(ts.toStructural(value))
}

/**
 * Extension methods for Scala 3 structural type support.
 */
extension [A](value: A)(using ts: ToStructural[A]) {

  /**
   * Convert to a Selectable structural representation.
   */
  def toSelectable: StructuralSelectable = StructuralSelectable.from(value)

  /**
   * Get the structural type as a refinement type. This is useful for pattern
   * matching and type-safe access.
   */
  def structuralType: String = ts.structuralTypeName
}
