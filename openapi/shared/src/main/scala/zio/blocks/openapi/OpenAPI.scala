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
 * This is a stub placeholder - will be fully implemented in a later task.
 */
final case class License(
  name: String,
  identifier: Option[String] = None,
  url: Option[String] = None,
  extensions: Map[String, Json] = Map.empty
)

object License {
  implicit val schema: Schema[License] = Schema.derived
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
