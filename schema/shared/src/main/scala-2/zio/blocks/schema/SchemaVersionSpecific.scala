package zio.blocks.schema

/**
 * Scala 2 version-specific methods for Schema instances.
 */
trait SchemaVersionSpecific[A] { self: Schema[A] =>

  /**
   * Derives a type class instance for this schema using the given format.
   *
   * In Scala 2, when using an explicit type annotation, you need to help the
   * compiler select the correct overload by either:
   *
   * 1. Specifying the type parameter explicitly:
   * {{{
   * implicit val jsonCodec: JsonBinaryCodec[Person] = schema.derive[JsonFormat.type](JsonFormat)
   * }}}
   *
   * 2. Or using the deriver directly:
   * {{{
   * implicit val jsonCodec: JsonBinaryCodec[Person] = schema.derive(JsonFormat.deriver)
   * }}}
   *
   * Without explicit type annotation, it works as expected:
   * {{{
   * implicit val jsonCodec = schema.derive(JsonFormat)
   * }}}
   *
   * In Scala 3, explicit type annotations work without these workarounds.
   */
  def derive[F <: codec.Format](format: F): format.TypeClass[A] = deriving(format.deriver).derive

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
