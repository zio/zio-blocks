package zio.blocks.codegen.ir

/**
 * Represents a field/property in the IR, with type, default value, annotations,
 * and documentation.
 *
 * @param name
 *   The name of the field
 * @param typeRef
 *   The type of the field
 * @param defaultValue
 *   Optional default value for the field as a string (defaults to None)
 * @param annotations
 *   List of annotations on the field (defaults to empty list)
 * @param doc
 *   Optional documentation/description of the field (defaults to None)
 *
 * @example
 *   {{{
 * // Simple field
 * val nameField = Field("name", TypeRef.String)
 *
 * // Field with default value
 * val ageField = Field("age", TypeRef.Int, Some("0"))
 *
 * // Field with annotations and documentation
 * val emailField = Field(
 *   "email",
 *   TypeRef.String,
 *   annotations = List(Annotation("required")),
 *   doc = Some("User email address")
 * )
 *
 * // Field with complex type
 * val itemsField = Field("items", TypeRef.list(TypeRef.String))
 *   }}}
 */
final case class Field(
  name: String,
  typeRef: TypeRef,
  defaultValue: Option[String] = None,
  annotations: List[Annotation] = Nil,
  doc: Option[String] = None
)
