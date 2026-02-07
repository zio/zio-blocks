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
