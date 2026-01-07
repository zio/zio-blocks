package zio.blocks.schema

import zio.blocks.schema.binding.Binding

/**
 * Represents a transformation or augmentation of a schema, applied to a
 * specific range of types.
 *
 * @tparam Upper
 *   The upper bound of types this `SchemaAspect` can be applied to.
 * @tparam Lower
 *   The lower bound of types this `SchemaAspect` will guarantee compatibility
 *   with.
 * @tparam F
 *   A type constructor representing the underlying structure for schema
 *   reflection.
 */
trait SchemaAspect[-Upper, +Lower, F[_, _]] {
  def apply[A >: Lower <: Upper](reflect: Reflect[F, A]): Reflect[F, A]
}

object SchemaAspect {

  /**
   * `identity` is a no-op `SchemaAspect` that performs no transformation on the
   * applied schema. It preserves the original structure and behaviors of the
   * input schema without modification.
   */
  val identity: SchemaAspect[Any, Nothing, Binding] = new SchemaAspect[Any, Nothing, Binding] {
    def apply[A](reflect: Reflect.Bound[A]): Reflect.Bound[A] = reflect
  }

  /**
   * Creates a `SchemaAspect` that sets or updates the documentation string of
   * the schema representation.
   *
   * @param value
   *   The documentation string.
   * @return
   *   A `SchemaAspect` that, when applied, associates the given documentation
   *   string with the schema.
   */
  def doc(value: String): SchemaAspect[Any, Nothing, Binding] = new SchemaAspect[Any, Nothing, Binding] {
    def apply[A](reflect: Reflect.Bound[A]): Reflect.Bound[A] = reflect.doc(value)
  }

  /**
   * Creates a `SchemaAspect` that associates one or more example values with
   * the schema for the specified type.
   *
   * @param value
   *   The first example value to be associated with the schema.
   * @param values
   *   Additional example values to be associated with the schema.
   * @return
   *   A `SchemaAspect` that, when applied, adds the specified example values to
   *   the schema.
   */
  def examples[A0](value: A0, values: A0*): SchemaAspect[A0, A0, Binding] = new SchemaAspect[A0, A0, Binding] {
    def apply[A >: A0 <: A0](reflect: Reflect.Bound[A]): Reflect.Bound[A] = reflect.examples(value, values: _*)
  }
}
