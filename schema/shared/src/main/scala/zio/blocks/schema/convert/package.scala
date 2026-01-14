package zio.blocks.schema

/**
 * Package providing schema-driven type conversion utilities.
 *
 * This package contains type classes for converting between types:
 *   - `Into[A, B]`: One-way conversion from A to B
 *   - `As[A, B]`: Bidirectional conversion between A and B
 *
 * Extension methods are provided for convenient syntax:
 *   - `value.into[B]`: Convert value to type B using Into
 *   - `value.as[B]`: Convert value to type B using As (bidirectional)
 */
package object convert {

  /**
   * Extension methods for any value that can be converted.
   */
  implicit class IntoOps[A](private val self: A) extends AnyVal {

    /**
     * Converts this value to type B using an implicit Into instance.
     *
     * @tparam B
     *   The target type
     * @return
     *   Either a SchemaError or the converted value
     */
    def into[B](implicit converter: Into[A, B]): Either[SchemaError, B] =
      converter.into(self)

    /**
     * Converts this value to type B using an implicit As instance. This method
     * is available when a bidirectional conversion exists.
     *
     * @tparam B
     *   The target type
     * @return
     *   Either a SchemaError or the converted value
     */
    def as[B](implicit converter: As[A, B]): Either[SchemaError, B] =
      converter.into(self)
  }

  /**
   * Extension methods for values that can be converted back from B to A.
   */
  implicit class AsFromOps[B](private val self: B) extends AnyVal {

    /**
     * Converts this value back to type A using an implicit As instance.
     *
     * @tparam A
     *   The target type
     * @return
     *   Either a SchemaError or the converted value
     */
    def from[A](implicit converter: As[A, B]): Either[SchemaError, A] =
      converter.from(self)
  }
}
