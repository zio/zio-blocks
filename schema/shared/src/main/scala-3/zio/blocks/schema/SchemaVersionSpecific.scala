package zio.blocks.schema

/**
 * Scala 3 version-specific methods for Schema instances.
 */
trait SchemaVersionSpecific[A] { self: Schema[A] =>

  /**
   * Derives a type class instance for this schema using the given format.
   *
   * This method uses `transparent inline` to resolve the path-dependent type
   * `format.TypeClass[A]` at compile time, enabling explicit type annotations:
   *
   * {{{
   * implicit val jsonCodec: JsonBinaryCodec[Person] = schema.derive(JsonFormat)
   * }}}
   *
   * @param format
   *   The format to derive a codec for
   * @return
   *   A type class instance for encoding/decoding values of type A
   */
  transparent inline def derive[F <: codec.Format](format: F): format.TypeClass[A] =
    self.deriving(format.deriver).derive

  /**
   * Convert this schema to a structural type schema.
   *
   * The structural type represents the "shape" of A without its nominal
   * identity. This enables duck typing and structural validation.
   *
   * Example:
   * {{{
   * case class Person(name: String, age: Int)
   * val structuralSchema: Schema[{ def name: String; def age: Int }] =
   *   Schema.derived[Person].structural
   * }}}
   *
   * Note: This is JVM-only due to reflection requirements for structural types.
   *
   * @param ts
   *   Macro-generated conversion to structural representation
   * @return
   *   Schema for the structural type corresponding to A
   */
  transparent inline def structural(using ts: ToStructural[A]): Schema[ts.StructuralType] = ts(this)
}
