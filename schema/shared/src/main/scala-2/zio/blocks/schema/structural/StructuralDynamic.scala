package zio.blocks.schema.structural

import scala.language.dynamics

/**
 * Scala 2 implementation of structural type access using Dynamic.
 *
 * Dynamic is a marker trait in Scala 2 that enables dynamic field access at
 * runtime. Unlike Scala 3's Selectable, this does not provide compile-time type
 * checking.
 *
 * Usage:
 * {{{
 * val structural: StructuralDynamic = ...
 * val name: Any = structural.name // Dynamic field access
 * }}}
 */
trait StructuralDynamic extends Dynamic {

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

  /**
   * Dynamic method application (for method-style access).
   */
  def applyDynamic(name: String)(args: Any*): Any =
    if (args.isEmpty) selectDynamic(name)
    else throw new UnsupportedOperationException(s"Cannot call method '$name' with arguments on structural type")
}

object StructuralDynamic {

  /**
   * Create a StructuralDynamic from a StructuralValue.
   */
  def apply(value: StructuralValue): StructuralDynamic = new StructuralDynamic {
    val underlying: StructuralValue = value
  }

  /**
   * Create a StructuralDynamic from any value with a ToStructural instance.
   */
  def from[A](value: A)(implicit ts: ToStructural[A]): StructuralDynamic =
    apply(ts.toStructural(value))

  /**
   * Implicit class for Scala 2 extension methods.
   */
  implicit class StructuralDynamicOps[A](private val value: A) extends AnyVal {

    /**
     * Convert to a Dynamic structural representation.
     */
    def toDynamic(implicit ts: ToStructural[A]): StructuralDynamic =
      StructuralDynamic.from(value)

    /**
     * Get the structural type name.
     */
    def structuralType(implicit ts: ToStructural[A]): String = ts.structuralTypeName
  }
}
