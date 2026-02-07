package golem.data.multimodal

import golem.data._

/**
 * Wrapper that lifts a case class into a multimodal schema.
 *
 * Multimodal payloads combine different content types (text, binary,
 * component-model) in a single structure. Use this wrapper when your data
 * contains mixed modalities.
 *
 * @tparam A
 *   The underlying case class type
 * @param value
 *   The wrapped value
 */
final case class Multimodal[A](value: A)

/**
 * Companion providing [[GolemSchema]] derivation for [[Multimodal]] types.
 */
object Multimodal {

  /**
   * Derives a multimodal GolemSchema from the underlying type's schema.
   *
   * The resulting schema tags the structure as multimodal rather than tuple,
   * enabling the host to handle mixed-modality content appropriately.
   *
   * @tparam A
   *   The underlying type (must have a GolemSchema instance)
   * @param base
   *   The underlying GolemSchema
   * @return
   *   A GolemSchema for Multimodal[A]
   */
  implicit def derived[A](implicit base: GolemSchema[A]): GolemSchema[Multimodal[A]] =
    new GolemSchema[Multimodal[A]] {
      private val modalitySchema: List[NamedElementSchema] =
        schemaAsModality(base.schema)

      override val schema: StructuredSchema =
        StructuredSchema.Multimodal(modalitySchema)

      override def encode(value: Multimodal[A]): Either[String, StructuredValue] =
        base.encode(value.value).flatMap(valueAsModality).map(elements => StructuredValue.Multimodal(elements))

      override def decode(structured: StructuredValue): Either[String, Multimodal[A]] =
        structured match {
          case StructuredValue.Multimodal(elements) =>
            base.decode(StructuredValue.Tuple(elements)).map(Multimodal(_))
          case other =>
            Left(s"Expected multimodal structured value, found $other")
        }
    }

  private def schemaAsModality(structured: StructuredSchema): List[NamedElementSchema] =
    structured match {
      case StructuredSchema.Tuple(elements)      => elements
      case StructuredSchema.Multimodal(elements) => elements
    }

  private def valueAsModality(value: StructuredValue): Either[String, List[NamedElementValue]] =
    value match {
      case StructuredValue.Tuple(elements)      => Right(elements)
      case StructuredValue.Multimodal(elements) => Right(elements)
    }
}
