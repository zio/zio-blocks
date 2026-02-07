package zio.blocks.openapi

import zio.blocks.schema._
import zio.blocks.schema.json.Json

/**
 * The root object of an OpenAPI 3.1 document.
 *
 * @param openapi
 *   REQUIRED. This string MUST be the version number of the OpenAPI
 *   Specification that the OpenAPI document uses. The openapi field SHOULD be
 *   used by tooling to interpret the OpenAPI document.
 * @param info
 *   REQUIRED. Provides metadata about the API. The metadata MAY be used by
 *   tooling as required.
 * @param jsonSchemaDialect
 *   The default value for the $schema keyword within Schema Objects contained
 *   within this OAS document. This MUST be in the form of a URI.
 * @param servers
 *   An array of Server Objects, which provide connectivity information to a
 *   target server. If the servers property is not provided, or is an empty
 *   array, the default value would be a Server Object with a url value of /.
 * @param paths
 *   The available paths and operations for the API.
 * @param components
 *   An element to hold various schemas for the document.
 * @param security
 *   A declaration of which security mechanisms can be used across the API. The
 *   list of values includes alternative security requirement objects that can
 *   be used.
 * @param tags
 *   A list of tags used by the document with additional metadata. The order of
 *   the tags can be used to reflect on their order by the parsing tools.
 * @param externalDocs
 *   Additional external documentation.
 * @param extensions
 *   Specification extensions (x-* fields). These allow adding additional
 *   properties beyond the standard OpenAPI fields.
 */
final case class OpenAPI(
  openapi: String,
  info: Info,
  jsonSchemaDialect: Option[String] = None,
  servers: List[Server] = Nil,
  paths: Option[Paths] = None,
  components: Option[Components] = None,
  security: List[SecurityRequirement] = Nil,
  tags: List[Tag] = Nil,
  externalDocs: Option[ExternalDocumentation] = None,
  extensions: Map[String, Json] = Map.empty
)

object OpenAPI {
  implicit val schema: Schema[OpenAPI] = Schema.derived
}

/**
 * The object provides metadata about the API. The metadata MAY be used by the
 * clients if needed, and MAY be presented in editing or documentation
 * generation tools for convenience.
 *
 * This is a stub placeholder - will be fully implemented in a later task.
 */
final case class Info(
  title: String,
  version: String,
  summary: Option[String] = None,
  description: Option[String] = None,
  termsOfService: Option[String] = None,
  contact: Option[Contact] = None,
  license: Option[License] = None,
  extensions: Map[String, Json] = Map.empty
)

object Info {
  implicit val schema: Schema[Info] = Schema.derived
}

/**
 * Contact information for the exposed API.
 *
 * This is a stub placeholder - will be fully implemented in a later task.
 */
final case class Contact(
  name: Option[String] = None,
  url: Option[String] = None,
  email: Option[String] = None,
  extensions: Map[String, Json] = Map.empty
)

object Contact {
  implicit val schema: Schema[Contact] = Schema.derived
}

/**
 * License information for the exposed API.
 *
 * @param name
 *   REQUIRED. The license name used for the API.
 * @param identifier
 *   An SPDX license expression for the API. The identifier field is mutually
 *   exclusive of the url field.
 * @param url
 *   A URL to the license used for the API. This MUST be in the form of a URL.
 *   The url field is mutually exclusive of the identifier field.
 * @param extensions
 *   Specification extensions (x-* fields). These allow adding additional
 *   properties beyond the standard OpenAPI fields.
 */
final case class License private (
  name: String,
  identifier: Option[String] = None,
  url: Option[String] = None,
  extensions: Map[String, Json] = Map.empty
) {
  require(
    identifier.isEmpty || url.isEmpty,
    "License identifier and url fields are mutually exclusive - only one may be specified"
  )
}

object License {
  implicit val schema: Schema[License] = Schema.derived

  /**
   * Creates a License with validation of mutual exclusivity constraints.
   */
  def apply(
    name: String,
    identifier: Option[String] = None,
    url: Option[String] = None,
    extensions: Map[String, Json] = Map.empty
  ): License = {
    require(
      identifier.isEmpty || url.isEmpty,
      "License identifier and url fields are mutually exclusive - only one may be specified"
    )
    new License(name, identifier, url, extensions)
  }
}

/**
 * An object representing a Server.
 *
 * This is a stub placeholder - will be fully implemented in a later task.
 */
final case class Server(
  url: String,
  description: Option[String] = None,
  variables: Map[String, ServerVariable] = Map.empty,
  extensions: Map[String, Json] = Map.empty
)

object Server {
  implicit val schema: Schema[Server] = Schema.derived
}

/**
 * An object representing a Server Variable for server URL template
 * substitution.
 *
 * This is a stub placeholder - will be fully implemented in a later task.
 */
final case class ServerVariable(
  default: String,
  `enum`: List[String] = Nil,
  description: Option[String] = None,
  extensions: Map[String, Json] = Map.empty
)

object ServerVariable {
  implicit val schema: Schema[ServerVariable] = Schema.derived

  /**
   * Creates a ServerVariable with validation. If `enum` is non-empty, the
   * `default` value MUST be one of the enum values.
   *
   * @param default
   *   REQUIRED. The default value to use for substitution.
   * @param enum
   *   An enumeration of string values to be used if the substitution options
   *   are from a limited set.
   * @param description
   *   An optional description for the server variable.
   * @param extensions
   *   Specification extensions (x-* fields).
   * @return
   *   Either a validation error message or a valid ServerVariable.
   */
  def validated(
    default: String,
    `enum`: List[String] = Nil,
    description: Option[String] = None,
    extensions: Map[String, Json] = Map.empty
  ): Either[String, ServerVariable] =
    if (`enum`.nonEmpty && !`enum`.contains(default)) {
      Left(
        s"ServerVariable validation failed: default value '$default' must be one of the enum values: ${`enum`.mkString(", ")}"
      )
    } else {
      Right(ServerVariable(default, `enum`, description, extensions))
    }
}

/**
 * Holds the relative paths to the individual endpoints and their operations.
 *
 * This is a stub placeholder - will be fully implemented in a later task.
 */
final case class Paths(
  paths: Map[String, PathItem] = Map.empty,
  extensions: Map[String, Json] = Map.empty
)

object Paths {
  implicit val schema: Schema[Paths] = Schema.derived
}

/**
 * Describes the operations available on a single path.
 *
 * This is a stub placeholder - will be fully implemented in a later task.
 */
final case class PathItem(
  summary: Option[String] = None,
  description: Option[String] = None,
  extensions: Map[String, Json] = Map.empty
)

object PathItem {
  implicit val schema: Schema[PathItem] = Schema.derived
}

/**
 * Holds a set of reusable objects for different aspects of the OAS.
 *
 * This is a stub placeholder - will be fully implemented in a later task.
 */
final case class Components(
  schemas: Map[String, Json] = Map.empty,
  responses: Map[String, Json] = Map.empty,
  parameters: Map[String, Json] = Map.empty,
  examples: Map[String, Json] = Map.empty,
  requestBodies: Map[String, Json] = Map.empty,
  headers: Map[String, Json] = Map.empty,
  securitySchemes: Map[String, Json] = Map.empty,
  links: Map[String, Json] = Map.empty,
  callbacks: Map[String, Json] = Map.empty,
  pathItems: Map[String, Json] = Map.empty,
  extensions: Map[String, Json] = Map.empty
)

object Components {
  implicit val schema: Schema[Components] = Schema.derived
}

/**
 * Lists the required security schemes to execute this operation.
 *
 * This is a stub placeholder - will be fully implemented in a later task.
 */
final case class SecurityRequirement(
  requirements: Map[String, List[String]]
)

object SecurityRequirement {
  implicit val schema: Schema[SecurityRequirement] = Schema.derived
}

/**
 * Adds metadata to a single tag that is used by the Operation Object.
 *
 * This is a stub placeholder - will be fully implemented in a later task.
 */
final case class Tag(
  name: String,
  description: Option[String] = None,
  externalDocs: Option[ExternalDocumentation] = None,
  extensions: Map[String, Json] = Map.empty
)

object Tag {
  implicit val schema: Schema[Tag] = Schema.derived
}

/**
 * Allows referencing an external resource for extended documentation.
 *
 * This is a stub placeholder - will be fully implemented in a later task.
 */
final case class ExternalDocumentation(
  url: String,
  description: Option[String] = None,
  extensions: Map[String, Json] = Map.empty
)

object ExternalDocumentation {
  implicit val schema: Schema[ExternalDocumentation] = Schema.derived
}

/**
 * Describes a single API operation on a path.
 *
 * @param responses
 *   REQUIRED. The list of possible responses as they are returned from
 *   executing this operation.
 * @param tags
 *   A list of tags for API documentation control.
 * @param summary
 *   A short summary of what the operation does.
 * @param description
 *   A verbose explanation of the operation behavior.
 * @param externalDocs
 *   Additional external documentation for this operation.
 * @param operationId
 *   Unique string used to identify the operation.
 * @param parameters
 *   A list of parameters that are applicable for this operation.
 * @param requestBody
 *   The request body applicable for this operation.
 * @param callbacks
 *   A map of possible out-of band callbacks related to the parent operation.
 * @param deprecated
 *   Declares this operation to be deprecated.
 * @param security
 *   A declaration of which security mechanisms can be used for this operation.
 * @param servers
 *   An alternative server array to service this operation.
 * @param extensions
 *   Extension fields starting with x-.
 */
final case class Operation(
  responses: Json,
  tags: List[String] = Nil,
  summary: Option[String] = None,
  description: Option[String] = None,
  externalDocs: Option[ExternalDocumentation] = None,
  operationId: Option[String] = None,
  parameters: List[Json] = Nil,
  requestBody: Option[Json] = None,
  callbacks: Map[String, Json] = Map.empty,
  deprecated: Boolean = false,
  security: List[SecurityRequirement] = Nil,
  servers: List[Server] = Nil,
  extensions: Map[String, Json] = Map.empty
)

object Operation {
  implicit val schema: Schema[Operation] = Schema.derived
}

/**
 * A simple object to allow referencing other components in the OpenAPI
 * document, internally and externally.
 *
 * @param `$ref`
 *   REQUIRED. The reference identifier. This MUST be in the form of a URI.
 * @param summary
 *   A short summary which by default SHOULD override that of the referenced
 *   component.
 * @param description
 *   A description which by default SHOULD override that of the referenced
 *   component.
 */
final case class Reference(
  `$ref`: String,
  summary: Option[String] = None,
  description: Option[String] = None
)

object Reference {
  implicit val schema: Schema[Reference] = Schema.derived
}

/**
 * A type that can either be a Reference or a concrete value of type A. This
 * pattern is used extensively in OpenAPI 3.1 to allow reusable components.
 *
 * @tparam A
 *   The type of the concrete value when not using a reference.
 */
sealed trait ReferenceOr[+A]

object ReferenceOr {

  /**
   * A reference to a component defined elsewhere in the OpenAPI document.
   *
   * @param reference
   *   The Reference object containing the $ref URI.
   */
  final case class Ref(reference: Reference) extends ReferenceOr[Nothing]

  /**
   * A concrete value (not a reference).
   *
   * @param value
   *   The actual value of type A.
   */
  final case class Value[A](value: A) extends ReferenceOr[A]

  implicit def schema[A: Schema]: Schema[ReferenceOr[A]] = Schema.derived
}

/**
 * When request bodies or response payloads may be one of a number of different
 * schemas, a discriminator object can be used to aid in serialization,
 * deserialization, and validation.
 *
 * The discriminator is a specific object in a schema which is used to inform
 * the consumer of the document of an alternative schema based on the value
 * associated with it.
 *
 * @param propertyName
 *   REQUIRED. The name of the property in the payload that will hold the
 *   discriminator value.
 * @param mapping
 *   An object to hold mappings between payload values and schema names or
 *   references.
 */
final case class Discriminator(
  propertyName: String,
  mapping: Map[String, String] = Map.empty
)

object Discriminator {
  implicit val schema: Schema[Discriminator] = Schema.derived
}

/**
 * A metadata object that allows for more fine-tuned XML model definitions.
 *
 * When using arrays, XML element names are not inferred (for singular/plural
 * forms) and the name property SHOULD be used to add that information.
 *
 * @param name
 *   Replaces the name of the element/attribute used for the described schema
 *   property. When defined within items, it will affect the name of the
 *   individual XML elements within the list. When defined alongside type being
 *   array (outside the items), it will affect the wrapping element and only if
 *   wrapped is true. If wrapped is false, it will be ignored.
 * @param namespace
 *   The URI of the namespace definition. This MUST be in the form of an
 *   absolute URI.
 * @param prefix
 *   The prefix to be used for the name.
 * @param attribute
 *   Declares whether the property definition translates to an attribute instead
 *   of an element. Default value is false.
 * @param wrapped
 *   MAY be used only for an array definition. Signifies whether the array is
 *   wrapped (for example, `<books><book/><book/></books>`) or unwrapped
 *   (`<book/><book/>`). Default value is false. The definition takes effect
 *   only when defined alongside type being array (outside the items).
 */
final case class XML(
  name: Option[String] = None,
  namespace: Option[String] = None,
  prefix: Option[String] = None,
  attribute: Boolean = false,
  wrapped: Boolean = false
)

object XML {
  implicit val schema: Schema[XML] = Schema.derived
}

/**
 * The Schema Object allows the definition of input and output data types. These
 * types can be objects, but also primitives and arrays.
 *
 * In OpenAPI 3.1, the Schema Object is fully compatible with JSON Schema
 * 2020-12, with the addition of OpenAPI-specific vocabulary.
 *
 * This implementation wraps the existing JsonSchema from zio-blocks-schema and
 * adds OpenAPI-specific vocabulary fields.
 *
 * @param jsonSchema
 *   The underlying JSON Schema 2020-12 schema represented as Json. This
 *   contains all standard JSON Schema keywords (type, properties, items, etc.).
 * @param discriminator
 *   Adds support for polymorphism. The discriminator is an object name that is
 *   used to differentiate between other schemas which may satisfy the payload
 *   description.
 * @param xml
 *   This MAY be used only on properties schemas. It has no effect on root
 *   schemas. Adds additional metadata to describe the XML representation of
 *   this property.
 * @param externalDocs
 *   Additional external documentation for this schema.
 * @param example
 *   A free-form property to include an example of an instance for this schema.
 *   To represent examples that cannot be naturally represented in JSON or YAML,
 *   a string value can be used to contain the example with escaping where
 *   necessary. Deprecated: The example property has been deprecated in favor of
 *   the JSON Schema examples keyword. Use of example is discouraged, and later
 *   versions of this specification may remove it.
 * @param extensions
 *   Specification extensions (x-* fields). These allow adding additional
 *   properties beyond the standard OpenAPI fields.
 */
final case class SchemaObject(
  jsonSchema: Json,
  discriminator: Option[Discriminator] = None,
  xml: Option[XML] = None,
  externalDocs: Option[ExternalDocumentation] = None,
  example: Option[Json] = None,
  extensions: Map[String, Json] = Map.empty
) {

  /**
   * Extracts the underlying JSON Schema as Json.
   */
  def toJson: Json = jsonSchema

  /**
   * Attempts to parse the underlying JSON Schema.
   */
  def toJsonSchema: Either[zio.blocks.schema.SchemaError, zio.blocks.schema.json.JsonSchema] =
    zio.blocks.schema.json.JsonSchema.fromJson(jsonSchema)
}

object SchemaObject {

  /**
   * Creates a SchemaObject from a JsonSchema, with no OpenAPI-specific
   * vocabulary.
   */
  def fromJsonSchema(js: zio.blocks.schema.json.JsonSchema): SchemaObject =
    SchemaObject(jsonSchema = js.toJson)

  implicit val schema: Schema[SchemaObject] = Schema.derived
}

/**
 * The location of the parameter.
 *
 * Possible values are "query", "header", "path" or "cookie".
 */
sealed trait ParameterLocation

object ParameterLocation {
  case object Query  extends ParameterLocation
  case object Header extends ParameterLocation
  case object Path   extends ParameterLocation
  case object Cookie extends ParameterLocation

  implicit val schema: Schema[ParameterLocation] = Schema.derived
}

/**
 * Describes a single operation parameter.
 *
 * A unique parameter is defined by a combination of a name and location.
 *
 * @param name
 *   REQUIRED. The name of the parameter. Parameter names are case sensitive.
 * @param in
 *   REQUIRED. The location of the parameter.
 * @param description
 *   A brief description of the parameter. This could contain examples of use.
 *   CommonMark syntax MAY be used for rich text representation.
 * @param required
 *   Determines whether this parameter is mandatory. If the parameter location
 *   is "path", this property is REQUIRED and its value MUST be true. Otherwise,
 *   the property MAY be included and its default value is false.
 * @param deprecated
 *   Specifies that a parameter is deprecated and SHOULD be transitioned out of
 *   usage. Default value is false.
 * @param allowEmptyValue
 *   Sets the ability to pass empty-valued parameters. This is valid only for
 *   query parameters and allows sending a parameter with an empty value.
 *   Default value is false.
 * @param style
 *   Describes how the parameter value will be serialized depending on the type
 *   of the parameter value.
 * @param explode
 *   When this is true, parameter values of type array or object generate
 *   separate parameters for each value of the array or key-value pair of the
 *   map.
 * @param allowReserved
 *   Determines whether the parameter value SHOULD allow reserved characters, as
 *   defined by RFC3986, to be included without percent-encoding.
 * @param schema
 *   The schema defining the type used for the parameter.
 * @param example
 *   Example of the parameter's potential value. The example SHOULD match the
 *   specified schema and encoding properties if present.
 * @param examples
 *   Examples of the parameter's potential value. Each example SHOULD contain a
 *   value in the correct format as specified in the parameter encoding.
 * @param content
 *   A map containing the representations for the parameter. The key is the
 *   media type and the value describes it.
 * @param extensions
 *   Specification extensions (x-* fields). These allow adding additional
 *   properties beyond the standard OpenAPI fields.
 */
final case class Parameter private (
  name: String,
  in: ParameterLocation,
  description: Option[String] = None,
  required: Boolean = false,
  deprecated: Boolean = false,
  allowEmptyValue: Boolean = false,
  style: Option[String] = None,
  explode: Option[Boolean] = None,
  allowReserved: Option[Boolean] = None,
  schema: Option[Json] = None,
  example: Option[Json] = None,
  examples: Map[String, Json] = Map.empty,
  content: Map[String, Json] = Map.empty,
  extensions: Map[String, Json] = Map.empty
) {
  require(
    in != ParameterLocation.Path || required,
    "Parameter with location 'path' must have required=true"
  )
}

object Parameter {
  implicit val schema: Schema[Parameter] = Schema.derived

  /**
   * Creates a Parameter with validation of the required field constraint.
   *
   * If the parameter location is "path", the required field MUST be true.
   */
  def apply(
    name: String,
    in: ParameterLocation,
    description: Option[String] = None,
    required: Boolean = false,
    deprecated: Boolean = false,
    allowEmptyValue: Boolean = false,
    style: Option[String] = None,
    explode: Option[Boolean] = None,
    allowReserved: Option[Boolean] = None,
    schema: Option[Json] = None,
    example: Option[Json] = None,
    examples: Map[String, Json] = Map.empty,
    content: Map[String, Json] = Map.empty,
    extensions: Map[String, Json] = Map.empty
  ): Parameter = {
    require(
      in != ParameterLocation.Path || required,
      "Parameter with location 'path' must have required=true"
    )
    new Parameter(
      name,
      in,
      description,
      required,
      deprecated,
      allowEmptyValue,
      style,
      explode,
      allowReserved,
      schema,
      example,
      examples,
      content,
      extensions
    )
  }
}

/**
 * The Header Object follows the structure of the Parameter Object with the
 * following changes:
 *
 *   1. name MUST NOT be specified, it is given in the corresponding headers
 *      map.
 *   2. in MUST NOT be specified, it is implicitly in header.
 *   3. All traits that are affected by the location MUST be applicable to a
 *      location of header (for example, style).
 *
 * @param description
 *   A brief description of the header. This could contain examples of use.
 *   CommonMark syntax MAY be used for rich text representation.
 * @param required
 *   Determines whether this header is mandatory. Default value is false.
 * @param deprecated
 *   Specifies that a header is deprecated and SHOULD be transitioned out of
 *   usage. Default value is false.
 * @param allowEmptyValue
 *   Sets the ability to pass empty-valued headers. Default value is false.
 * @param style
 *   Describes how the header value will be serialized.
 * @param explode
 *   When this is true, header values of type array or object generate separate
 *   parameters for each value of the array or key-value pair of the map.
 * @param allowReserved
 *   Determines whether the header value SHOULD allow reserved characters, as
 *   defined by RFC3986, to be included without percent-encoding.
 * @param schema
 *   The schema defining the type used for the header.
 * @param example
 *   Example of the header's potential value.
 * @param examples
 *   Examples of the header's potential value.
 * @param content
 *   A map containing the representations for the header. The key is the media
 *   type and the value describes it.
 * @param extensions
 *   Specification extensions (x-* fields). These allow adding additional
 *   properties beyond the standard OpenAPI fields.
 */
final case class Header(
  description: Option[String] = None,
  required: Boolean = false,
  deprecated: Boolean = false,
  allowEmptyValue: Boolean = false,
  style: Option[String] = None,
  explode: Option[Boolean] = None,
  allowReserved: Option[Boolean] = None,
  schema: Option[Json] = None,
  example: Option[Json] = None,
  examples: Map[String, Json] = Map.empty,
  content: Map[String, Json] = Map.empty,
  extensions: Map[String, Json] = Map.empty
)

object Header {
  implicit val schema: Schema[Header] = Schema.derived
}
