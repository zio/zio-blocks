package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * A type-safe selector for building [[DynamicOptic]] paths from lambda
 * expressions.
 *
 * Selectors allow compile-time validation of field paths while producing
 * runtime [[DynamicOptic]] values. The macro inspects selector lambdas like
 * `_.fieldName.nestedField` and converts them to path segments.
 *
 * @tparam A
 *   the source type
 * @tparam B
 *   the selected value type
 */
trait Selector[-A, +B] {

  /**
   * The dynamic optic path represented by this selector.
   */
  def toOptic: DynamicOptic

  /**
   * Compose this selector with another to create a deeper path.
   */
  def andThen[C](that: Selector[B, C]): Selector[A, C] =
    Selector.Composed(this, that)

  /**
   * Alias for andThen.
   */
  def >>>[C](that: Selector[B, C]): Selector[A, C] = andThen(that)
}

object Selector {

  /**
   * The identity selector that selects the root.
   */
  def root[A]: Selector[A, A] = Root[A]()

  /**
   * Create a selector for a single field.
   */
  def field[A, B](name: String): Selector[A, B] = Field(name)

  /**
   * Create a selector for sequence elements.
   */
  def elements[A, B]: Selector[Seq[A], B] = Elements()

  /**
   * Create a selector for map keys.
   */
  def mapKeys[K, V]: Selector[Map[K, V], K] = MapKeys()

  /**
   * Create a selector for map values.
   */
  def mapValues[K, V]: Selector[Map[K, V], V] = MapValues()

  // Internal implementations

  private[migration] final case class Root[A]() extends Selector[A, A] {
    def toOptic: DynamicOptic = DynamicOptic.root
  }

  private[migration] final case class Field[A, B](name: String) extends Selector[A, B] {
    def toOptic: DynamicOptic = DynamicOptic.root.field(name)
  }

  private[migration] final case class Composed[A, B, C](
    first: Selector[A, B],
    second: Selector[B, C]
  ) extends Selector[A, C] {
    def toOptic: DynamicOptic = {
      val firstNodes  = first.toOptic.nodes
      val secondNodes = second.toOptic.nodes
      new DynamicOptic(firstNodes ++ secondNodes)
    }
  }

  private[migration] final case class Elements[A, B]() extends Selector[Seq[A], B] {
    def toOptic: DynamicOptic = DynamicOptic.elements
  }

  private[migration] final case class MapKeys[K, V]() extends Selector[Map[K, V], K] {
    def toOptic: DynamicOptic = DynamicOptic.mapKeys
  }

  private[migration] final case class MapValues[K, V]() extends Selector[Map[K, V], V] {
    def toOptic: DynamicOptic = DynamicOptic.mapValues
  }

  private[migration] final case class Optional[A, B](inner: Selector[A, B]) extends Selector[Option[A], Option[B]] {
    def toOptic: DynamicOptic = inner.toOptic
  }
}
