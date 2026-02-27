package zio.blocks.codegen.ir

/**
 * Represents a method definition in the IR.
 *
 * @param name
 *   The method name
 * @param typeParams
 *   Type parameters for generic methods (defaults to empty list)
 * @param params
 *   Parameter lists (supports curried methods, defaults to empty list)
 * @param returnType
 *   The return type of the method
 * @param body
 *   Optional method body as a string (defaults to None)
 * @param annotations
 *   List of annotations on the method (defaults to empty list)
 * @param isOverride
 *   Whether this method overrides a parent method (defaults to false)
 * @param doc
 *   Optional documentation for the method (defaults to None)
 */
final case class Method(
  name: String,
  typeParams: List[TypeRef] = Nil,
  params: List[List[MethodParam]] = Nil,
  returnType: TypeRef,
  body: Option[String] = None,
  annotations: List[Annotation] = Nil,
  isOverride: Boolean = false,
  doc: Option[String] = None
)

/**
 * Represents a method parameter in the IR.
 *
 * @param name
 *   The parameter name
 * @param typeRef
 *   The parameter type
 * @param defaultValue
 *   Optional default value as a string (defaults to None)
 */
final case class MethodParam(
  name: String,
  typeRef: TypeRef,
  defaultValue: Option[String] = None
)
