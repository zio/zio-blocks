package zio.blocks.schema

/**
 * Scala 2 version-specific methods for Schema instances.
 */
trait SchemaVersionSpecific[A] { self: Schema[A] =>

  /**
   * Derives a format instance for this schema using the given format.
   *
   * This method provides a convenient way to derive format-specific codecs:
   *
   * {{{
   * implicit val jsonCodec: JsonBinaryCodec[Person] = schema.deriveFormat(JsonFormat)
   * implicit val avroCodec: AvroBinaryCodec[Person] = schema.deriveFormat(AvroFormat)
   * }}}
   *
   * The method name `deriveFormat` avoids overload ambiguity with
   * `derive[TC[_]](deriver)`, ensuring explicit type annotations work correctly
   * in both Scala 2 and Scala 3.
   *
   * @param format
   *   The format to derive a codec for (e.g., JsonFormat, AvroFormat)
   * @return
   *   A codec instance for encoding/decoding values of type A in the given
   *   format
   */
  def deriveFormat[F <: codec.Format](format: F): format.TypeClass[A] = deriving(format.deriver).derive

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
  def structural(implicit ts: ToStructural[A]): Schema[ts.StructuralType] = ts(this)
}
