/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.openapi

import zio.blocks.chunk.{Chunk, ChunkMap}
import zio.blocks.docs.Doc
import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * The root object of an OpenAPI 3.1 document: the API's metadata, its paths and
 * operations, and the reusable components they refer to. Build one by hand or
 * decode one, and read or write its JSON with `OpenAPICodec.openAPICodec`.
 *
 * {{{
 * import zio.blocks.chunk.ChunkMap
 * import zio.blocks.docs._
 * import zio.blocks.openapi.OpenAPICodec.openAPICodec
 *
 * val listPets = Operation(
 *   operationId = Some("listPets"),
 *   responses = Responses(ChunkMap("200" -> ReferenceOr.Value(Response(md"A list of pets."))))
 * )
 * val api = OpenAPI(
 *   openapi = "3.1.0",
 *   info = Info(title = "Petstore", version = "1.0.0"),
 *   paths = Some(Paths(ChunkMap("/pets" -> PathItem(get = Some(listPets)))))
 * )
 *
 * val json = openAPICodec.encodeValue(api)
 * openAPICodec.decode(json).map(_.paths.map(_.paths.keys.toList)) // Right(Some(List(/pets)))
 * }}}
 *
 * @param openapi
 *   REQUIRED. The version of the OpenAPI Specification that the document uses,
 *   such as "3.1.0". Decoding fails when it is missing, but the value is not
 *   checked, so an OpenAPI 3.0 document decodes too.
 * @param info
 *   REQUIRED. Provides metadata about the API. The metadata MAY be used by
 *   tooling as required.
 * @param jsonSchemaDialect
 *   The default value for the $$schema keyword within Schema Objects contained
 *   within this OAS document. This MUST be in the form of a URI.
 * @param servers
 *   An array of Server Objects, which provide connectivity information to a
 *   target server. The specification reads a missing or empty array as a single
 *   server with the URL `/`; this library does not add that server.
 * @param paths
 *   The available paths and operations for the API.
 * @param webhooks
 *   Incoming requests that the API provider may send to the consumer, keyed by
 *   a name, each described by a Path Item.
 * @param components
 *   Reusable objects (schemas, responses, parameters, security schemes and
 *   more) that the rest of the document refers to.
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
 *   properties beyond the standard OpenAPI fields. The encoder writes every
 *   entry, but the decoder reads back only keys that start with `x-`.
 * @see
 *   [[https://zioblocks.com/reference/openapi/#openapi OpenAPI]]
 */
final case class OpenAPI(
  openapi: String,
  info: Info,
  jsonSchemaDialect: Option[String] = None,
  servers: Chunk[Server] = Chunk.empty,
  paths: Option[Paths] = None,
  webhooks: ChunkMap[String, ReferenceOr[PathItem]] = ChunkMap.empty,
  components: Option[Components] = None,
  security: Chunk[SecurityRequirement] = Chunk.empty,
  tags: Chunk[Tag] = Chunk.empty,
  externalDocs: Option[ExternalDocumentation] = None,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[OpenAPI]]. */
object OpenAPI {

  /**
   * The derived `Schema` of the document type. Its JSON codec writes the Scala
   * structure (Markdown trees for descriptions, an `extensions` key, a nested
   * `paths` key), not OpenAPI JSON; use `OpenAPICodec.openAPICodec` to read and
   * write documents.
   */
  implicit val schema: Schema[OpenAPI] = Schema.derived
}

/**
 * The object provides metadata about the API. The metadata MAY be used by the
 * clients if needed, and MAY be presented in editing or documentation
 * generation tools for convenience.
 */
final case class Info(
  title: String,
  version: String,
  summary: Option[Doc] = None,
  description: Option[Doc] = None,
  termsOfService: Option[String] = None,
  contact: Option[Contact] = None,
  license: Option[License] = None,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[Info]]. */
object Info {

  /**
   * The derived `Schema` of [[Info]]. Its JSON layout can differ from OpenAPI
   * JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit val schema: Schema[Info] = Schema.derived
}

/**
 * Contact information for the exposed API.
 */
final case class Contact(
  name: Option[String] = None,
  url: Option[String] = None,
  email: Option[String] = None,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[Contact]]. */
object Contact {

  /**
   * The derived `Schema` of [[Contact]]. Its JSON layout can differ from
   * OpenAPI JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit val schema: Schema[Contact] = Schema.derived
}

/**
 * License information for the exposed API. Optional SPDX license expression
 * (identifier) and a URL to the license are mutually exclusive per the OpenAPI
 * spec: the constructor throws `IllegalArgumentException` when both are set,
 * and decoding such a license returns a `Left`.
 *
 * @param name
 *   REQUIRED. The license name used for the API.
 * @param identifier
 *   An optional SPDX license expression (identifier)
 * @param url
 *   An optional URL to the license.
 * @param extensions
 *   Specification extensions (x-* fields). These allow adding additional
 *   properties beyond the standard OpenAPI fields.
 * @throws java.lang.IllegalArgumentException
 *   when both `identifier` and `url` are set
 */
final case class License(
  name: String,
  identifier: Option[String] = None,
  url: Option[String] = None,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
) {
  require(
    identifier.isEmpty || url.isEmpty,
    "License identifier and url fields are mutually exclusive - only one may be specified"
  )
}

/** Holds the derived `Schema` of [[License]]. */
object License {

  /**
   * The derived `Schema` of [[License]]. Its JSON layout can differ from
   * OpenAPI JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit val schema: Schema[License] = Schema.derived
}

/**
 * An object representing a Server.
 */
final case class Server(
  url: String,
  description: Option[Doc] = None,
  variables: ChunkMap[String, ServerVariable] = ChunkMap.empty,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[Server]]. */
object Server {

  /**
   * The derived `Schema` of [[Server]]. Its JSON layout can differ from OpenAPI
   * JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit val schema: Schema[Server] = Schema.derived
}

/**
 * A variable for substitution in a server URL template. If `enum` is non-empty,
 * the `default` value MUST be one of the enum values: the constructor throws
 * `IllegalArgumentException` otherwise, and decoding such a variable returns a
 * `Left`.
 *
 * @param default
 *   REQUIRED. The default value to use for substitution.
 * @param enum
 *   An enumeration of string values to be used if the substitution options are
 *   from a limited set.
 * @param description
 *   An optional description for the server variable.
 * @param extensions
 *   Specification extensions (x-* fields).
 * @throws java.lang.IllegalArgumentException
 *   when `enum` is non-empty and does not contain `default`
 */
final case class ServerVariable(
  default: String,
  `enum`: Chunk[String] = Chunk.empty,
  description: Option[Doc] = None,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
) {
  require(
    `enum`.isEmpty || `enum`.contains(default),
    s"ServerVariable validation failed: default value '$default' must be one of the enum values: ${`enum`.mkString(", ")}"
  )
}

/** Holds the derived `Schema` of [[ServerVariable]]. */
object ServerVariable {

  /**
   * The derived `Schema` of [[ServerVariable]]. Its JSON layout can differ from
   * OpenAPI JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit val schema: Schema[ServerVariable] = Schema.derived
}

/**
 * Holds the relative paths to the individual endpoints and their operations.
 */
final case class Paths(
  paths: ChunkMap[String, PathItem] = ChunkMap.empty,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[Paths]]. */
object Paths {

  /**
   * The derived `Schema` of [[Paths]]. Its JSON layout can differ from OpenAPI
   * JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit lazy val schema: Schema[Paths] = Schema.derived
}

/**
 * Describes the operations available on a single path, one optional
 * [[Operation]] per HTTP method, plus servers and parameters shared by all of
 * them. A [[Paths]] object maps each path, such as `/pets/{petId}`, to its
 * PathItem.
 *
 * {{{
 * val pets = PathItem(
 *   get = Some(Operation(operationId = Some("listPets"))),
 *   post = Some(Operation(operationId = Some("createPet")))
 * )
 * List(pets.get, pets.post, pets.delete).map(_.flatMap(_.operationId))
 * // List(Some(listPets), Some(createPet), None)
 * }}}
 *
 * @param summary
 *   A short summary for all operations on this path.
 * @param description
 *   A longer description for all operations on this path.
 * @param get
 *   The GET operation, if any.
 * @param put
 *   The PUT operation, if any.
 * @param post
 *   The POST operation, if any.
 * @param delete
 *   The DELETE operation, if any.
 * @param options
 *   The OPTIONS operation, if any.
 * @param head
 *   The HEAD operation, if any.
 * @param patch
 *   The PATCH operation, if any.
 * @param trace
 *   The TRACE operation, if any.
 * @param servers
 *   Alternative servers for all operations on this path.
 * @param parameters
 *   Parameters for all operations on this path. The specification lets an
 *   operation override one with a parameter of the same name and location; the
 *   library does not merge them.
 * @param extensions
 *   Specification extensions (x-* fields).
 * @see
 *   [[https://zioblocks.com/reference/openapi/#paths--pathitem Paths & PathItem]]
 */
final case class PathItem(
  summary: Option[Doc] = None,
  description: Option[Doc] = None,
  get: Option[Operation] = None,
  put: Option[Operation] = None,
  post: Option[Operation] = None,
  delete: Option[Operation] = None,
  options: Option[Operation] = None,
  head: Option[Operation] = None,
  patch: Option[Operation] = None,
  trace: Option[Operation] = None,
  servers: Chunk[Server] = Chunk.empty,
  parameters: Chunk[ReferenceOr[Parameter]] = Chunk.empty,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[PathItem]]. */
object PathItem {

  /**
   * The derived `Schema` of [[PathItem]]. Its JSON is not OpenAPI JSON; use
   * `OpenAPICodec.openAPICodec` for documents.
   */
  implicit lazy val schema: Schema[PathItem] = Schema.derived
}

/**
 * Holds a set of reusable objects for different aspects of the OAS: schemas,
 * responses, parameters, examples, request bodies, headers, security schemes,
 * links, callbacks and path items, each keyed by a name. Other parts of the
 * document refer to them with a `ReferenceOr.Ref` such as
 * `#/components/schemas/Pet`.
 *
 * {{{
 * import zio.blocks.chunk.ChunkMap
 * import zio.blocks.schema.Schema
 *
 * final case class Pet(id: Long, name: String)
 * object Pet {
 *   implicit val schema: Schema[Pet] = Schema.derived
 * }
 *
 * val (petRef, (petName, petSchema)) = Schema[Pet].toRefSchema
 * val components = Components(schemas = ChunkMap(petName -> ReferenceOr.Value(petSchema)))
 * // petRef: Ref(Reference(#/components/schemas/Pet,None,None))
 * components.schemas.keys.toList // List(Pet)
 * }}}
 *
 * @param schemas
 *   Reusable Schema Objects.
 * @param responses
 *   Reusable Response Objects.
 * @param parameters
 *   Reusable Parameter Objects.
 * @param examples
 *   Reusable Example Objects.
 * @param requestBodies
 *   Reusable Request Body Objects.
 * @param headers
 *   Reusable Header Objects.
 * @param securitySchemes
 *   Reusable Security Scheme Objects, which `security` requirements name.
 * @param links
 *   Reusable Link Objects.
 * @param callbacks
 *   Reusable Callback Objects.
 * @param pathItems
 *   Reusable Path Item Objects.
 * @param extensions
 *   Specification extensions (x-* fields).
 * @see
 *   [[https://zioblocks.com/reference/openapi/#components Components]]
 */
final case class Components(
  schemas: ChunkMap[String, ReferenceOr[SchemaObject]] = ChunkMap.empty,
  responses: ChunkMap[String, ReferenceOr[Response]] = ChunkMap.empty,
  parameters: ChunkMap[String, ReferenceOr[Parameter]] = ChunkMap.empty,
  examples: ChunkMap[String, ReferenceOr[Example]] = ChunkMap.empty,
  requestBodies: ChunkMap[String, ReferenceOr[RequestBody]] = ChunkMap.empty,
  headers: ChunkMap[String, ReferenceOr[Header]] = ChunkMap.empty,
  securitySchemes: ChunkMap[String, ReferenceOr[SecurityScheme]] = ChunkMap.empty,
  links: ChunkMap[String, ReferenceOr[Link]] = ChunkMap.empty,
  callbacks: ChunkMap[String, ReferenceOr[Callback]] = ChunkMap.empty,
  pathItems: ChunkMap[String, ReferenceOr[PathItem]] = ChunkMap.empty,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[Components]]. */
object Components {

  /**
   * The derived `Schema` of [[Components]]. Its JSON is not OpenAPI JSON; use
   * `OpenAPICodec.openAPICodec` for documents.
   */
  implicit lazy val schema: Schema[Components] = Schema.derived
}

/**
 * Lists the required security schemes to execute this operation.
 */
final case class SecurityRequirement(
  requirements: ChunkMap[String, Chunk[String]]
)

/** Holds the derived `Schema` of [[SecurityRequirement]]. */
object SecurityRequirement {

  /**
   * The derived `Schema` of [[SecurityRequirement]]. Its JSON layout can differ
   * from OpenAPI JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit val schema: Schema[SecurityRequirement] = Schema.derived
}

/**
 * Adds metadata to a single tag that is used by the Operation Object.
 */
final case class Tag(
  name: String,
  description: Option[Doc] = None,
  externalDocs: Option[ExternalDocumentation] = None,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[Tag]]. */
object Tag {

  /**
   * The derived `Schema` of [[Tag]]. Its JSON layout can differ from OpenAPI
   * JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit val schema: Schema[Tag] = Schema.derived
}

/**
 * Allows referencing an external resource for extended documentation.
 */
final case class ExternalDocumentation(
  url: String,
  description: Option[Doc] = None,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[ExternalDocumentation]]. */
object ExternalDocumentation {

  /**
   * The derived `Schema` of [[ExternalDocumentation]]. Its JSON layout can
   * differ from OpenAPI JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit val schema: Schema[ExternalDocumentation] = Schema.derived
}

/**
 * Describes a single API operation on a path: its parameters, request body,
 * responses, security and metadata such as `operationId` and `tags`. A
 * [[PathItem]] holds one Operation per HTTP method.
 *
 * {{{
 * import zio.blocks.chunk.{Chunk, ChunkMap}
 * import zio.blocks.docs._
 *
 * val showPetById = Operation(
 *   operationId = Some("showPetById"),
 *   parameters = Chunk(ReferenceOr.Value(Parameter("petId", ParameterLocation.Path, required = true))),
 *   responses = Responses(ChunkMap("200" -> ReferenceOr.Value(Response(md"The pet."))))
 * )
 * showPetById.responses.responses.keys.toList // List(200)
 * }}}
 *
 * @param responses
 *   The list of possible responses as they are returned from executing this
 *   operation. It defaults to no responses; the encoder always writes the
 *   `responses` key, and the decoder accepts an operation without one.
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
 * @see
 *   [[https://zioblocks.com/reference/openapi/#operation Operation]]
 */
final case class Operation(
  responses: Responses = Responses(),
  tags: Chunk[String] = Chunk.empty,
  summary: Option[Doc] = None,
  description: Option[Doc] = None,
  externalDocs: Option[ExternalDocumentation] = None,
  operationId: Option[String] = None,
  parameters: Chunk[ReferenceOr[Parameter]] = Chunk.empty,
  requestBody: Option[ReferenceOr[RequestBody]] = None,
  callbacks: ChunkMap[String, ReferenceOr[Callback]] = ChunkMap.empty,
  deprecated: Boolean = false,
  security: Chunk[SecurityRequirement] = Chunk.empty,
  servers: Chunk[Server] = Chunk.empty,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[Operation]]. */
object Operation {

  /**
   * The derived `Schema` of [[Operation]]. Its JSON is not OpenAPI JSON; use
   * `OpenAPICodec.openAPICodec` for documents.
   */
  implicit lazy val schema: Schema[Operation] = Schema.derived
}

/**
 * A simple object to allow referencing other components in the OpenAPI
 * document, internally and externally.
 *
 * @param `$$ref`
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
  summary: Option[Doc] = None,
  description: Option[Doc] = None
)

/** Holds the derived `Schema` of [[Reference]]. */
object Reference {

  /**
   * The derived `Schema` of [[Reference]]. Its JSON layout can differ from
   * OpenAPI JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit val schema: Schema[Reference] = Schema.derived
}

/**
 * A type that can either be a Reference or a concrete value of type A. This
 * pattern is used extensively in OpenAPI 3.1 to allow reusable components: a
 * field that takes a `ReferenceOr[SchemaObject]` holds either an inline schema
 * or a pointer such as `#/components/schemas/Pet`. The library does not resolve
 * references; look the name up in [[Components]] yourself.
 *
 * In JSON, an object with a `\$ref` key decodes as a `Ref` (keeping only
 * `\$ref`, `summary` and `description`) and any other value as a `Value`. A
 * schema held in a `ReferenceOr` that has `\$ref` next to other JSON Schema
 * keywords therefore loses those keywords (a known bug).
 *
 * {{{
 * import zio.blocks.schema.json.Json
 *
 * def describe(schema: ReferenceOr[SchemaObject]): String = schema match {
 *   case ReferenceOr.Ref(Reference(uri, _, _)) => "reference to " + uri
 *   case ReferenceOr.Value(obj)              => "inline schema " + obj.toJson.print
 * }
 *
 * describe(ReferenceOr.Ref(Reference("#/components/schemas/Pet"))) // reference to #/components/schemas/Pet
 * describe(ReferenceOr.Value(SchemaObject(Json.Object("type" -> Json.String("string")))))
 * // inline schema {"type":"string"}
 * }}}
 *
 * @tparam A
 *   The type of the concrete value when not using a reference.
 * @see
 *   [[https://zioblocks.com/reference/openapi/#referenceor ReferenceOr]]
 */
sealed trait ReferenceOr[+A]

/** The two cases of [[ReferenceOr]]. */
object ReferenceOr {

  /**
   * A reference to a component defined elsewhere in the OpenAPI document.
   *
   * @param reference
   *   The Reference object containing the $$ref URI.
   */
  final case class Ref(reference: Reference) extends ReferenceOr[Nothing]

  /**
   * A concrete value (not a reference).
   *
   * @param value
   *   The actual value of type A.
   */
  final case class Value[A](value: A) extends ReferenceOr[A]

  /**
   * The derived `Schema` of `ReferenceOr[A]`. Its JSON wraps each case in an
   * object named after it (`{"Ref":{...}}`), which is not OpenAPI JSON; use
   * `OpenAPICodec.openAPICodec` for documents.
   */
  implicit def schema[A: Schema]: Schema[ReferenceOr[A]] = Schema.derived
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
 *   arrayed (outside the items), it will affect the wrapping element and only
 *   if wrapped is true. If wrapped is false, it will be ignored.
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
 *   only when defined alongside type being arrayed (outside the items).
 */
final case class XML(
  name: Option[String] = None,
  namespace: Option[String] = None,
  prefix: Option[String] = None,
  attribute: Boolean = false,
  wrapped: Boolean = false
)

/** Holds the derived `Schema` of [[XML]]. */
object XML {

  /**
   * The derived `Schema` of [[XML]]. Its JSON layout can differ from OpenAPI
   * JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit val schema: Schema[XML] = Schema.derived
}

/**
 * The Schema Object allows the definition of input and output data types. These
 * types can be objects, but also primitives and arrays.
 *
 * In OpenAPI 3.1, the Schema Object is fully compatible with JSON Schema
 * 2020-12, with the addition of OpenAPI-specific vocabulary.
 *
 * This implementation keeps the JSON Schema keywords as raw `Json` (parse them
 * with [[toJsonSchema]]) and holds the OpenAPI-specific vocabulary in its own
 * fields.
 *
 * @param jsonSchema
 *   The underlying JSON Schema 2020-12 schema represented as Json: an object,
 *   `true` or `false`. A decoded schema keeps every key here except
 *   `discriminator`, `xml`, `externalDocs`, `example` and keys starting with
 *   `x-`, which go to the fields below.
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
  extensions: ChunkMap[String, Json] = ChunkMap.empty
) {

  /**
   * Returns the schema as one JSON value: `jsonSchema` with `discriminator`,
   * `xml`, `externalDocs`, `example` and the extensions added, replacing keys
   * of the same name. A boolean `jsonSchema` is returned as is, without them.
   */
  def toJson: Json = jsonSchema match {
    case obj: Json.Object =>
      val extra = Chunk.newBuilder[(String, Json)]
      discriminator.foreach { d =>
        extra += ("discriminator" -> OpenAPICodec.discriminatorCodec.encodeValue(d))
      }
      xml.foreach { x =>
        extra += ("xml" -> OpenAPICodec.xmlCodec.encodeValue(x))
      }
      externalDocs.foreach { ed =>
        extra += ("externalDocs" -> OpenAPICodec.externalDocumentationCodec.encodeValue(ed))
      }
      example.foreach { ex =>
        extra += ("example" -> ex)
      }
      extensions.foreach { case (k, v) =>
        extra += (k -> v)
      }
      val extraFields = extra.result()
      if (extraFields.isEmpty) obj
      else {
        val extraKeys    = extraFields.map(_._1).toSet
        val baseFiltered = obj.value.filter { case (k, _) => !extraKeys.contains(k) }
        new Json.Object(baseFiltered ++ extraFields)
      }
    case other => other
  }

  /**
   * Attempts to parse the underlying JSON Schema (`jsonSchema` only, not the
   * OpenAPI fields). Returns a `Left` when it is neither an object nor a
   * boolean, or when a keyword that holds subschemas has the wrong shape
   * (`"allOf": 1`); other malformed keywords are dropped.
   */
  def toJsonSchema: Either[zio.blocks.schema.SchemaError, zio.blocks.schema.json.JsonSchema] =
    zio.blocks.schema.json.JsonSchema.fromJson(jsonSchema)
}

/** Converts a ZIO Blocks `JsonSchema` into a [[SchemaObject]]. */
object SchemaObject {

  /**
   * Creates a SchemaObject from a JsonSchema, with no OpenAPI-specific
   * vocabulary.
   */
  def fromJsonSchema(js: zio.blocks.schema.json.JsonSchema): SchemaObject =
    SchemaObject(jsonSchema = js.toJson)

  /**
   * The derived `Schema` of [[SchemaObject]]. Its JSON is not OpenAPI JSON; use
   * `OpenAPICodec.openAPICodec` for documents.
   */
  implicit val schema: Schema[SchemaObject] = Schema.derived
}

/**
 * The location of the parameter.
 *
 * Possible values are "query", "header", "path" or "cookie".
 */
sealed trait ParameterLocation

/** Holds the cases and the derived `Schema` of [[ParameterLocation]]. */
object ParameterLocation {

  /** The parameter is passed in the query string. */
  case object Query extends ParameterLocation

  /** The parameter is passed in a request header. */
  case object Header extends ParameterLocation

  /** The parameter is a variable of the URL path template. */
  case object Path extends ParameterLocation

  /** The parameter is passed in a cookie. */
  case object Cookie extends ParameterLocation

  /**
   * The derived `Schema` of [[ParameterLocation]]. It encodes a case by its
   * object name, such as `"Query"`; `OpenAPICodec.openAPICodec` writes the
   * lowercase names `"query"`, `"header"`, `"path"` and `"cookie"`.
   */
  implicit val schema: Schema[ParameterLocation] = Schema.derived
}

/**
 * Describes a single operation parameter.
 *
 * A unique parameter is defined by a combination of a name and location.
 *
 * Note: If the parameter location is "path", the required field MUST be true.
 * This is not enforced in the constructor; callers are responsible for ensuring
 * path parameters have required=true.
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
final case class Parameter(
  name: String,
  in: ParameterLocation,
  description: Option[Doc] = None,
  required: Boolean = false,
  deprecated: Boolean = false,
  allowEmptyValue: Boolean = false,
  style: Option[String] = None,
  explode: Option[Boolean] = None,
  allowReserved: Option[Boolean] = None,
  schema: Option[ReferenceOr[SchemaObject]] = None,
  example: Option[Json] = None,
  examples: ChunkMap[String, ReferenceOr[Example]] = ChunkMap.empty,
  content: ChunkMap[String, MediaType] = ChunkMap.empty,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[Parameter]]. */
object Parameter {

  /**
   * The derived `Schema` of [[Parameter]]. Its JSON layout can differ from
   * OpenAPI JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit lazy val schema: Schema[Parameter] = Schema.derived
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
  description: Option[Doc] = None,
  required: Boolean = false,
  deprecated: Boolean = false,
  allowEmptyValue: Boolean = false,
  style: Option[String] = None,
  explode: Option[Boolean] = None,
  allowReserved: Option[Boolean] = None,
  schema: Option[ReferenceOr[SchemaObject]] = None,
  example: Option[Json] = None,
  examples: ChunkMap[String, ReferenceOr[Example]] = ChunkMap.empty,
  content: ChunkMap[String, MediaType] = ChunkMap.empty,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[Header]]. */
object Header {

  /**
   * The derived `Schema` of [[Header]]. Its JSON layout can differ from OpenAPI
   * JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit lazy val schema: Schema[Header] = Schema.derived
}

/**
 * Describes a single request body.
 *
 * @param content
 *   REQUIRED. The content of the request body. The key is a media type or media
 *   type range and the value describes it. For requests that match multiple
 *   keys, only the most specific key is applicable. e.g. text/plain overrides
 *   text/wildcard. When the map is empty the encoder leaves the key out, which
 *   makes the request body invalid (a known bug).
 * @param description
 *   A brief description of the request body. This could contain examples of
 *   use. CommonMark syntax MAY be used for rich text representation.
 * @param required
 *   Determines if the request body is required in the request. Defaults to
 *   false.
 * @param extensions
 *   Specification extensions (x-* fields). These allow adding additional
 *   properties beyond the standard OpenAPI fields.
 */
final case class RequestBody(
  content: ChunkMap[String, MediaType],
  description: Option[Doc] = None,
  required: Boolean = false,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[RequestBody]]. */
object RequestBody {

  /**
   * The derived `Schema` of [[RequestBody]]. Its JSON layout can differ from
   * OpenAPI JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit lazy val schema: Schema[RequestBody] = Schema.derived
}

/**
 * Each Media Type Object provides schema and examples for the media type
 * identified by its key.
 *
 * @param schema
 *   The schema defining the content of the request, response, or parameter.
 * @param example
 *   Example of the media type. The example object SHOULD be in the correct
 *   format as specified by the media type. The example field is mutually
 *   exclusive of the examples field. Furthermore, if referencing a schema which
 *   contains an example, the example value SHALL override the example provided
 *   by the schema.
 * @param examples
 *   Examples of the media type. Each example object SHOULD match the media type
 *   and specified schema if present. The examples field is mutually exclusive
 *   of the example field. Furthermore, if referencing a schema which contains
 *   an example, the examples value SHALL override the example provided by the
 *   schema.
 * @param encoding
 *   A map between a property name and its encoding information. The key, being
 *   the property name, MUST exist in the schema as a property. The encoding
 *   object SHALL only apply to requestBody objects when the media type is
 *   multipart or application/x-www-form-urlencoded.
 * @param extensions
 *   Specification extensions (x-* fields). These allow adding additional
 *   properties beyond the standard OpenAPI fields.
 */
final case class MediaType(
  schema: Option[ReferenceOr[SchemaObject]] = None,
  example: Option[Json] = None,
  examples: ChunkMap[String, ReferenceOr[Example]] = ChunkMap.empty,
  encoding: ChunkMap[String, Encoding] = ChunkMap.empty,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[MediaType]]. */
object MediaType {

  /**
   * The derived `Schema` of [[MediaType]]. Its JSON layout can differ from
   * OpenAPI JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit lazy val schema: Schema[MediaType] = Schema.derived
}

/**
 * A single encoding definition applied to a single schema property.
 *
 * @param contentType
 *   The Content-Type for encoding a specific property. Default value depends on
 *   the property type: for object - application/json; for array – the default
 *   is defined based on the inner type; for all other cases the default is
 *   application/octet-stream. The value can be a specific media type (e.g.
 *   application/json), a wildcard media type (e.g. image/wildcard), or a
 *   comma-separated list of the two types.
 * @param headers
 *   A map allowing additional information to be provided as headers, for
 *   example Content-Disposition. Content-Type is described separately and SHALL
 *   be ignored in this section. This property SHALL be ignored if the request
 *   body media type is not a multipart.
 * @param style
 *   Describes how a specific property value will be serialized depending on its
 *   type. See Parameter Object for details on the style property. The behavior
 *   follows the same values as query parameters, including default values. This
 *   property SHALL be ignored if the request body media type is not
 *   application/x-www-form-urlencoded or multipart/form-data. If a value is
 *   explicitly defined, then the value of contentType (implicit or explicit)
 *   SHALL be ignored.
 * @param explode
 *   When this is true, property values of type array or object generate
 *   separate parameters for each value of the array, or key-value-pair of the
 *   map. For other types of properties this property has no effect. When style
 *   is form, the default value is true. For all other styles, the default value
 *   is false. This property SHALL be ignored if the request body media type is
 *   not application/x-www-form-urlencoded or multipart/form-data. If a value is
 *   explicitly defined, then the value of contentType (implicit or explicit)
 *   SHALL be ignored.
 * @param allowReserved
 *   Determines whether the parameter value SHOULD allow reserved characters, as
 *   defined by RFC3986 :/?#[]@!$&'()*+,;= to be included without
 *   percent-encoding. The default value is false. This property SHALL be
 *   ignored if the request body media type is not
 *   application/x-www-form-urlencoded or multipart/form-data. If a value is
 *   explicitly defined, then the value of contentType (implicit or explicit)
 *   SHALL be ignored.
 * @param extensions
 *   Specification extensions (x-* fields). These allow adding additional
 *   properties beyond the standard OpenAPI fields.
 */
final case class Encoding(
  contentType: Option[String] = None,
  headers: ChunkMap[String, ReferenceOr[Header]] = ChunkMap.empty,
  style: Option[String] = None,
  explode: Option[Boolean] = None,
  allowReserved: Boolean = false,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[Encoding]]. */
object Encoding {

  /**
   * The derived `Schema` of [[Encoding]]. Its JSON layout can differ from
   * OpenAPI JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit lazy val schema: Schema[Encoding] = Schema.derived
}

/**
 * A container for the expected responses of an operation. The container maps a
 * HTTP response code to the expected response.
 *
 * The documentation is not necessarily expected to cover all possible HTTP
 * response codes because they may not be known in advance. However,
 * documentation is expected to cover a successful operation response and any
 * known errors.
 *
 * The default MAY be used as a default response object for all HTTP codes that
 * are not covered individually by the Responses Object.
 *
 * @param responses
 *   A map of HTTP status codes to Response objects. The key is the HTTP status
 *   code as a string (e.g. "200", "404", "4XX"). The value is a Response or
 *   Reference.
 * @param default
 *   The documentation of responses other than the ones declared for specific
 *   HTTP response codes. Use this field to cover undeclared responses.
 * @param extensions
 *   Specification extensions (x-* fields). These allow adding additional
 *   properties beyond the standard OpenAPI fields.
 */
final case class Responses(
  responses: ChunkMap[String, ReferenceOr[Response]] = ChunkMap.empty,
  default: Option[ReferenceOr[Response]] = None,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[Responses]]. */
object Responses {

  /**
   * The derived `Schema` of [[Responses]]. Its JSON layout can differ from
   * OpenAPI JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit lazy val schema: Schema[Responses] = Schema.derived
}

/**
 * Describes a single response from an API Operation, including design-time,
 * static links to operations based on the response.
 *
 * @param description
 *   REQUIRED. A description of the response. CommonMark syntax MAY be used for
 *   rich text representation.
 * @param headers
 *   Maps a header name to its definition. RFC7230 states header names are case
 *   insensitive. If a response header is defined with the name "Content-Type",
 *   it SHALL be ignored.
 * @param content
 *   A map containing descriptions of potential response payloads. The key is a
 *   media type or media type range and the value describes it. For responses
 *   that match multiple keys, only the most specific key is applicable. e.g.
 *   text/plain overrides text/wildcard
 * @param links
 *   A map of operations links that can be followed from the response. The key
 *   of the map is a short name for the link, following the naming constraints
 *   of the names for Component Objects.
 * @param extensions
 *   Specification extensions (x-* fields). These allow adding additional
 *   properties beyond the standard OpenAPI fields.
 */
final case class Response(
  description: Doc,
  headers: ChunkMap[String, ReferenceOr[Header]] = ChunkMap.empty,
  content: ChunkMap[String, MediaType] = ChunkMap.empty,
  links: ChunkMap[String, ReferenceOr[Link]] = ChunkMap.empty,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[Response]]. */
object Response {

  /**
   * The derived `Schema` of [[Response]]. Its JSON layout can differ from
   * OpenAPI JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit lazy val schema: Schema[Response] = Schema.derived
}

/**
 * An object representing an example. `value` and `externalValue` are mutually
 * exclusive: the constructor throws `IllegalArgumentException` when both are
 * set, and decoding such an example returns a `Left`.
 *
 * In all cases, the example value is expected to be compatible with the type
 * schema of its associated value. Tooling implementations MAY choose to
 * validate compatibility automatically, and reject the example value if
 * incompatible.
 *
 * @param summary
 *   Short description for the example.
 * @param description
 *   Long description for the example. CommonMark syntax MAY be used for rich
 *   text representation.
 * @param value
 *   Embedded literal example. The value field and externalValue field are
 *   mutually exclusive. To represent examples of media types that cannot be
 *   represented in JSON or YAML naturally, use a string value to contain the
 *   example, escaping where necessary.
 * @param externalValue
 *   A URI that identifies the location of the example value. This provides the
 *   capability to reference examples that cannot easily be included in JSON or
 *   YAML documents. The value field and externalValue field are mutually
 *   exclusive. See the rules for resolving Relative References.
 * @param extensions
 *   Specification extensions (x-* fields). These allow adding additional
 *   properties beyond the standard OpenAPI fields.
 * @throws java.lang.IllegalArgumentException
 *   when both `value` and `externalValue` are set
 */
final case class Example(
  summary: Option[Doc] = None,
  description: Option[Doc] = None,
  value: Option[Json] = None,
  externalValue: Option[String] = None,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
) {
  require(
    value.isEmpty || externalValue.isEmpty,
    "Example value and externalValue fields are mutually exclusive - only one may be specified"
  )
}

/** Holds the derived `Schema` of [[Example]]. */
object Example {

  /**
   * The derived `Schema` of [[Example]]. Its JSON layout can differ from
   * OpenAPI JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit val schema: Schema[Example] = Schema.derived
}

/**
 * The Link object represents a possible design-time link for a response. The
 * presence of a link does not guarantee the caller's ability to successfully
 * invoke it, rather it provides a known relationship and traversal mechanism
 * between responses and other operations. `operationRef` and `operationId` are
 * mutually exclusive: the constructor throws `IllegalArgumentException` when
 * both are set, and decoding such a link returns a `Left`.
 *
 * Unlike dynamic links (i.e. links provided in the response payload), the OAS
 * linking mechanism does not require link information in the runtime response.
 *
 * For computing links, and providing instructions to execute them, a runtime
 * expression is used for accessing values in an operation and using them as
 * parameters while invoking the linked operation.
 *
 * @param operationRef
 *   An optional absolute URI reference to an OAS operation (operationRef),
 *   mutually exclusive with operationId.
 * @param operationId
 *   A name of an existing, resolvable OAS operation (operationId), mutually
 *   exclusive with operationRef.
 * @param parameters
 *   A map representing parameters to pass to an operation as specified with
 *   operationId or identified via operationRef. The key is the parameter name
 *   to be used, whereas the value can be a constant or an expression to be
 *   evaluated and passed to the linked operation. The parameter name can be
 *   qualified using the parameter location [{in}.]{name} for operations that
 *   use the same parameter name in different locations (e.g. path.id).
 * @param requestBody
 *   A literal value or {expression} to use as a request body when calling the
 *   target operation.
 * @param description
 *   A description of the link. CommonMark syntax MAY be used for rich text
 *   representation.
 * @param server
 *   A server object to be used by the target operation.
 * @param extensions
 *   Specification extensions (x-* fields). These allow adding additional
 *   properties beyond the standard OpenAPI fields.
 * @throws java.lang.IllegalArgumentException
 *   when both `operationRef` and `operationId` are set
 */
final case class Link(
  operationRef: Option[String] = None,
  operationId: Option[String] = None,
  parameters: ChunkMap[String, Json] = ChunkMap.empty,
  requestBody: Option[Json] = None,
  description: Option[Doc] = None,
  server: Option[Server] = None,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
) {
  require(
    operationRef.isEmpty || operationId.isEmpty,
    "Link operationRef and operationId fields are mutually exclusive - only one may be specified"
  )
}

/** Holds the derived `Schema` of [[Link]]. */
object Link {

  /**
   * The derived `Schema` of [[Link]]. Its JSON layout can differ from OpenAPI
   * JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit lazy val schema: Schema[Link] = Schema.derived
}

/**
 * A map of possible out-of band callbacks related to the parent operation. Each
 * value in the map is a Path Item Object that describes a set of requests that
 * may be initiated by the API provider and the expected responses. The key
 * value used to identify the path item object is an expression, evaluated at
 * runtime, that identifies a URL to use for the callback operation.
 *
 * To describe incoming requests from the API provider independent from another
 * API call, use the webhooks field.
 *
 * @param callbacks
 *   A map of runtime expressions to Path Item Objects. The key is an
 *   expression, evaluated at runtime, that identifies a URL to use for the
 *   callback operation.
 * @param extensions
 *   Specification extensions (x-* fields). These allow adding additional
 *   properties beyond the standard OpenAPI fields.
 */
final case class Callback(
  callbacks: ChunkMap[String, ReferenceOr[PathItem]] = ChunkMap.empty,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[Callback]]. */
object Callback {

  /**
   * The derived `Schema` of [[Callback]]. Its JSON layout can differ from
   * OpenAPI JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit lazy val schema: Schema[Callback] = Schema.derived
}

/**
 * The location of the API key for API key authentication.
 *
 * Valid values are "query", "header", or "cookie".
 */
sealed trait APIKeyLocation

/** Holds the cases and the derived `Schema` of [[APIKeyLocation]]. */
object APIKeyLocation {

  /** The API key is passed in the query string. */
  case object Query extends APIKeyLocation

  /** The API key is passed in a request header. */
  case object Header extends APIKeyLocation

  /** The API key is passed in a cookie. */
  case object Cookie extends APIKeyLocation

  /**
   * The derived `Schema` of [[APIKeyLocation]]. It encodes a case by its object
   * name, such as `"Cookie"`; `OpenAPICodec.openAPICodec` writes the lowercase
   * names `"query"`, `"header"` and `"cookie"`.
   */
  implicit val schema: Schema[APIKeyLocation] = Schema.derived
}

/**
 * Defines a security scheme that can be used by the operations.
 *
 * Supported schemes are HTTP authentication, an API key (either as a header, a
 * cookie parameter or as a query parameter), mutual TLS (use of a client
 * certificate), OAuth2's common flows (implicit, password, client credentials
 * and authorization code) as defined in RFC6749, and OpenID Connect Discovery.
 */
sealed trait SecurityScheme

/** Holds the five kinds of [[SecurityScheme]] and its derived `Schema`. */
object SecurityScheme {

  /**
   * API key authentication.
   *
   * @param name
   *   REQUIRED. The name of the header, query or cookie parameter to be used.
   * @param in
   *   REQUIRED. The location of the API key.
   * @param description
   *   A description for security scheme. CommonMark syntax MAY be used for rich
   *   text representation.
   * @param extensions
   *   Specification extensions (x-* fields). These allow adding additional
   *   properties beyond the standard OpenAPI fields.
   */
  final case class APIKey(
    name: String,
    in: APIKeyLocation,
    description: Option[Doc] = None,
    extensions: ChunkMap[String, Json] = ChunkMap.empty
  ) extends SecurityScheme

  /**
   * HTTP authentication scheme (Bearer, Basic, etc.).
   *
   * @param scheme
   *   REQUIRED. The name of the HTTP Authorization scheme to be used in the
   *   Authorization header as defined in RFC7235. The values used SHOULD be
   *   registered in the IANA Authentication Scheme registry.
   * @param bearerFormat
   *   A hint to the client to identify how the bearer token is formatted.
   *   Bearer tokens are usually generated by an authorization server, so this
   *   information is primarily for documentation purposes.
   * @param description
   *   A description for security scheme. CommonMark syntax MAY be used for rich
   *   text representation.
   * @param extensions
   *   Specification extensions (x-* fields). These allow adding additional
   *   properties beyond the standard OpenAPI fields.
   */
  final case class HTTP(
    scheme: String,
    bearerFormat: Option[String] = None,
    description: Option[Doc] = None,
    extensions: ChunkMap[String, Json] = ChunkMap.empty
  ) extends SecurityScheme

  /**
   * OAuth 2.0 authentication.
   *
   * @param flows
   *   REQUIRED. An object containing configuration information for the flow
   *   types supported.
   * @param description
   *   A description for security scheme. CommonMark syntax MAY be used for rich
   *   text representation.
   * @param extensions
   *   Specification extensions (x-* fields). These allow adding additional
   *   properties beyond the standard OpenAPI fields.
   */
  final case class OAuth2(
    flows: OAuthFlows,
    description: Option[Doc] = None,
    extensions: ChunkMap[String, Json] = ChunkMap.empty
  ) extends SecurityScheme

  /**
   * OpenID Connect authentication.
   *
   * @param openIdConnectUrl
   *   REQUIRED. OpenID Connect URL to discover OAuth2 configuration values.
   *   This MUST be in the form of a URL. The OpenID Connect standard requires
   *   the use of TLS.
   * @param description
   *   A description for security scheme. CommonMark syntax MAY be used for rich
   *   text representation.
   * @param extensions
   *   Specification extensions (x-* fields). These allow adding additional
   *   properties beyond the standard OpenAPI fields.
   */
  final case class OpenIdConnect(
    openIdConnectUrl: String,
    description: Option[Doc] = None,
    extensions: ChunkMap[String, Json] = ChunkMap.empty
  ) extends SecurityScheme

  /**
   * Mutual TLS authentication.
   *
   * @param description
   *   A description for security scheme. CommonMark syntax MAY be used for rich
   *   text representation.
   * @param extensions
   *   Specification extensions (x-* fields). These allow adding additional
   *   properties beyond the standard OpenAPI fields.
   */
  final case class MutualTLS(
    description: Option[Doc] = None,
    extensions: ChunkMap[String, Json] = ChunkMap.empty
  ) extends SecurityScheme

  /**
   * The derived `Schema` of [[SecurityScheme]]. It wraps a case under its case
   * name, as in `{"MutualTLS":{}}`; use `OpenAPICodec.openAPICodec` for
   * documents.
   */
  implicit val schema: Schema[SecurityScheme] = Schema.derived
}

/**
 * Allows configuration of the supported OAuth Flows.
 *
 * @param `implicit`
 *   Configuration for the OAuth Implicit flow.
 * @param password
 *   Configuration for the OAuth Resource Owner Password flow.
 * @param clientCredentials
 *   Configuration for the OAuth Client Credentials flow. Previously called
 *   application in OAuth2.
 * @param authorizationCode
 *   Configuration for the OAuth Authorization Code flow. Previously called
 *   accessCode in OAuth2.
 * @param extensions
 *   Specification extensions (x-* fields). These allow adding additional
 *   properties beyond the standard OpenAPI fields.
 */
final case class OAuthFlows(
  `implicit`: Option[OAuthFlow] = None,
  password: Option[OAuthFlow] = None,
  clientCredentials: Option[OAuthFlow] = None,
  authorizationCode: Option[OAuthFlow] = None,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[OAuthFlows]]. */
object OAuthFlows {

  /**
   * The derived `Schema` of [[OAuthFlows]]. Its JSON layout can differ from
   * OpenAPI JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit val schema: Schema[OAuthFlows] = Schema.derived
}

/**
 * Configuration details for a supported OAuth Flow.
 *
 * @param authorizationUrl
 *   The authorization URL to be used for this flow. This MUST be in the form of
 *   a URL. The OAuth2 standard requires the use of TLS. REQUIRED for implicit
 *   and authorizationCode flows; the library does not check it.
 * @param tokenUrl
 *   The token URL to be used for this flow. This MUST be in the form of a URL.
 *   The OAuth2 standard requires the use of TLS. REQUIRED for password,
 *   clientCredentials, and authorizationCode flows; the library does not check
 *   it.
 * @param refreshUrl
 *   The URL to be used for obtaining refresh tokens. This MUST be in the form
 *   of a URL. The OAuth2 standard requires the use of TLS.
 * @param scopes
 *   REQUIRED. The available scopes for the OAuth2 security scheme. A map
 *   between the scope name and a short description for it. The map MAY be
 *   empty, but the encoder leaves an empty map out, which makes the flow
 *   invalid (a known bug).
 * @param extensions
 *   Specification extensions (x-* fields). These allow adding additional
 *   properties beyond the standard OpenAPI fields.
 */
final case class OAuthFlow(
  authorizationUrl: Option[String] = None,
  tokenUrl: Option[String] = None,
  refreshUrl: Option[String] = None,
  scopes: ChunkMap[String, String] = ChunkMap.empty,
  extensions: ChunkMap[String, Json] = ChunkMap.empty
)

/** Holds the derived `Schema` of [[OAuthFlow]]. */
object OAuthFlow {

  /**
   * The derived `Schema` of [[OAuthFlow]]. Its JSON layout can differ from
   * OpenAPI JSON; use `OpenAPICodec.openAPICodec` for documents.
   */
  implicit val schema: Schema[OAuthFlow] = Schema.derived
}
