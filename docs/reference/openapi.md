---
id: openapi
title: "OpenAPI"
---

`zio-blocks-openapi` is a **complete, type-safe OpenAPI 3.1 data model** for building API documentation programmatically. It provides immutable case classes and sealed traits representing every OpenAPI concept—operations, parameters, security schemes, and components—enabling you to construct OpenAPI documents in compile-time-safe Scala and export them as JSON for consumption by tools like Swagger UI, Redoc, and API validators.

Core types: `OpenAPI`, `Info`, `Paths`, `PathItem`, `Operation`, `Parameter`, `RequestBody`, `Response`, `Components`, `SchemaObject`, `SecurityScheme`, `ReferenceOr`.

```scala
final case class OpenAPI(
  openapi: String,
  info: Info,
  servers: Option[Chunk[Server]] = None,
  paths: Option[Paths] = None,
  components: Option[Components] = None,
  security: Option[Chunk[SecurityRequirement]] = None
)
```

## Introduction

OpenAPI documents are the **lingua franca for API specifications**. They define request/response contracts, authentication methods, and data schemas in a standardized JSON or YAML format that external tools consume. Building these documents manually in JSON is error-prone; maintaining them as your API evolves is tedious.

The OpenAPI module bridges the gap by letting you author API specs as Scala code—leveraging the type system for compile-time correctness—then export to standard JSON that any OpenAPI tool understands. You get type safety during authoring plus interoperability with the entire OpenAPI ecosystem.

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-openapi" % "@VERSION@"

// You'll also need the schema module for Schema[A] integration:
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"
```

For Scala.js:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-openapi" % "@VERSION@"
```

Supported Scala versions: 2.13.x and 3.x.

## How They Work Together

The OpenAPI module follows a clear workflow:

**1. Define your data types** using ZIO Blocks `Schema`:

```scala mdoc:silent
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

import zio.blocks.schema._

case class User(id: Int, name: String, email: String)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

case class ErrorResponse(code: Int, message: String)
object ErrorResponse {
  implicit val schema: Schema[ErrorResponse] = Schema.derived
}
```

**2. Create an OpenAPI document** by composing types:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

import zio.blocks.openapi._
import zio.blocks.markdown._

val api = OpenAPI(
  openapi = "3.1.0",
  info = Info(
    title = "User API",
    version = "1.0.0",
    description = Some(Doc("API for managing users"))
  ),
  paths = Some(Paths(ChunkMap(
    "/users" -> PathItem(
      get = Some(Operation(
        summary = Some("List all users"),
        description = Some(Doc("Returns a paginated list of users")),
        responses = Responses(ChunkMap(
          "200" -> ReferenceOr.Value(Response(
            description = Doc("Successful response"),
            content = ChunkMap(
              "application/json" -> MediaType(
                schema = Some(ReferenceOr.Value(
                  Schema[List[User]].toOpenAPISchema
                ))
              )
            )
          ))
        ))
      ))
    ),
    "/users/{id}" -> PathItem(
      get = Some(Operation(
        summary = Some("Get a user by ID"),
        parameters = Some(Chunk(
          ReferenceOr.Value(Parameter(
            name = "id",
            in = ParameterLocation.Path,
            required = true,
            schema = Some(ReferenceOr.Value(
              Schema[Int].toOpenAPISchema
            ))
          ))
        )),
        responses = Responses(ChunkMap(
          "200" -> ReferenceOr.Value(Response(
            description = Doc("User found"),
            content = ChunkMap(
              "application/json" -> MediaType(
                schema = Some(ReferenceOr.Value(
                  Schema[User].toOpenAPISchema
                ))
              )
            )
          )),
          "404" -> ReferenceOr.Value(Response(
            description = Doc("User not found"),
            content = ChunkMap(
              "application/json" -> MediaType(
                schema = Some(ReferenceOr.Value(
                  Schema[ErrorResponse].toOpenAPISchema
                ))
              )
            )
          ))
        ))
      ))
    )
  ))),
  components = Some(Components(
    schemas = Some(ChunkMap(
      Schema[User].toRefSchema._2,
      Schema[ErrorResponse].toRefSchema._2
    ))
  ))
)
```

**3. Serialize to JSON** for tools to consume:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

import zio.blocks.openapi.OpenAPICodec._

val json = openAPICodec.encodeValue(api)
```

**4. Render or serve** the JSON (e.g., to Swagger UI):

```scala mdoc:compile-only
// Pseudo-code: render json to string and serve at GET /openapi.json
val jsonString = json.render // Produces JSON string
```

### Type Relationships Diagram

```
OpenAPI (root document)
├─ info: Info (metadata)
├─ servers: Chunk[Server]
├─ paths: Paths (map of path strings to PathItem)
│  └─ PathItem
│     ├─ get: Operation
│     ├─ post: Operation
│     ├─ put: Operation
│     └─ ... (other HTTP methods)
│        ├─ parameters: Chunk[ReferenceOr[Parameter]]
│        ├─ requestBody: ReferenceOr[RequestBody]
│        │  └─ content: Map[String, MediaType]
│        │     └─ schema: ReferenceOr[SchemaObject]
│        └─ responses: Responses
│           └─ Map[statusCode, ReferenceOr[Response]]
│              └─ content: Map[String, MediaType]
│                 └─ schema: ReferenceOr[SchemaObject]
├─ components: Components
│  ├─ schemas: Map[String, SchemaObject]
│  ├─ responses: Map[String, ReferenceOr[Response]]
│  ├─ parameters: Map[String, ReferenceOr[Parameter]]
│  └─ securitySchemes: Map[String, ReferenceOr[SecurityScheme]]
└─ security: Chunk[SecurityRequirement]
```

## Common Patterns

### Building Reusable Schema Components

Avoid duplicating schema definitions by moving them to `components.schemas`:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val userSchemaComponent = Schema[User].toRefSchema
// Returns: (ReferenceOr.Ref(...), ("User", SchemaObject(...)))
// Use the ref in operations, store the component in components.schemas
```

### Using `ReferenceOr` for Inline vs. Referenced Schemas

`ReferenceOr[A]` is a sealed trait with two cases:

- **`ReferenceOr.Ref`**: Points to a schema in `#/components/schemas/<name>`
- **`ReferenceOr.Value`**: Inline schema definition

Prefer `Ref` for reusable schemas; use `Value` for simple, one-off schemas:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

// Reusable: use Ref
val userRef = ReferenceOr.Ref(Reference("$ref" -> "#/components/schemas/User"))

// One-off: use Value
val simpleString = ReferenceOr.Value(Schema[String].toOpenAPISchema)
```

### Security Schemes

Define authentication methods in `components.securitySchemes`:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val apiKeyScheme = SecurityScheme.APIKey(
  name = "X-API-Key",
  in = "header",
  description = Some(Doc("API key for authentication"))
)

val oauthScheme = SecurityScheme.OAuth2(
  flows = OAuthFlows(
    authorizationCode = Some(OAuthFlow(
      authorizationUrl = "https://example.com/oauth/authorize",
      tokenUrl = "https://example.com/oauth/token",
      scopes = ChunkMap("read" -> Doc("Read access"), "write" -> Doc("Write access"))
    ))
  ),
  description = Some(Doc("OAuth 2.0 authorization"))
)

val components = Components(
  securitySchemes = Some(ChunkMap(
    "api_key" -> ReferenceOr.Value(apiKeyScheme),
    "oauth2" -> ReferenceOr.Value(oauthScheme)
  ))
)
```

### Path Parameters vs. Query Parameters

Distinguish parameter locations using `ParameterLocation`:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val pathParam = Parameter(
  name = "id",
  in = ParameterLocation.Path,
  required = true,
  schema = Some(ReferenceOr.Value(Schema[Int].toOpenAPISchema))
)

val queryParam = Parameter(
  name = "limit",
  in = ParameterLocation.Query,
  required = false,
  schema = Some(ReferenceOr.Value(Schema[Int].toOpenAPISchema))
)

val headerParam = Parameter(
  name = "X-Custom-Header",
  in = ParameterLocation.Header,
  required = false,
  schema = Some(ReferenceOr.Value(Schema[String].toOpenAPISchema))
)
```

## Integration Points

The OpenAPI module integrates tightly with other ZIO Blocks components:

- **Schema Integration**: All OpenAPI types have `Schema.derived` instances, enabling round-trip serialization via `DynamicValue`. Use `Schema[A].toOpenAPISchema` to convert any schema to an OpenAPI component.
- **Markdown Support**: Description fields use the in-house `Doc` type, which supports CommonMark rendering. This ensures markdown descriptions round-trip correctly.
- **JSON AST**: All codecs operate on the `Json` AST from `zio-blocks-schema`, not external JSON libraries. To render as YAML, pipe the `Json` through `zio-blocks-schema-yaml` separately.

---

## OpenAPI

`OpenAPI` is the root document object representing a complete OpenAPI 3.1 specification.

### Definition

Every OpenAPI document requires:
- **`openapi`**: Version string (typically `"3.1.0"`)
- **`info`**: Metadata about the API (`Info`)
- **`paths`**: Map of endpoint paths to operations (`Paths`)
- **`responses`**: HTTP response definitions (`Responses`)

Optional fields include servers, components, security requirements, tags, and external documentation.

### Creating an OpenAPI Document

To construct an `OpenAPI` document:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val minimalApi = OpenAPI(
  openapi = "3.1.0",
  info = Info(title = "My API", version = "1.0.0"),
  paths = Some(Paths(ChunkMap()))  // Empty paths initially
)
```

Add paths, operations, and components as shown in the "How They Work Together" section above.

### Serialization

Encode an `OpenAPI` document to `Json` AST:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

import zio.blocks.openapi.OpenAPICodec._

val encoded: Json = openAPICodec.encodeValue(api)
```

Decode from `Json` AST back to an `OpenAPI` instance:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val decoded: OpenAPI = openAPICodec.decodeValue(encoded)
```

---

## Info

`Info` contains metadata about the API: title, version, contact, and license.

### Definition

Required fields:
- **`title`**: API name (e.g., `"User API"`)
- **`version`**: API version (e.g., `"1.0.0"`)

Optional fields:
- **`description`**: Markdown-formatted description (`Doc`)
- **`termsOfService`**: Terms of service URL
- **`contact`**: Contact information (`Contact`)
- **`license`**: License information (`License`)

### Creating Info

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val info = Info(
  title = "Pet Store API",
  version = "3.0.0",
  description = Some(Doc("API for managing a pet store")),
  contact = Some(Contact(
    name = Some("API Support"),
    url = Some("https://example.com/support"),
    email = Some("support@example.com")
  )),
  license = Some(License(
    name = "Apache 2.0",
    identifier = Some("Apache-2.0")
  ))
)
```

---

## Paths & PathItem

`Paths` is a map of URL paths to their operations. `PathItem` groups HTTP methods (GET, POST, PUT, etc.) on a single path.

### Definition

`Paths` is a type alias for `ChunkMap[String, PathItem]`, where keys are path strings (e.g., `"/users/{id}"`).

`PathItem` contains optional fields for each HTTP method:
- **`get`, `post`, `put`, `delete`, `patch`, `head`, `options`, `trace`**: `Operation` instances
- **`parameters`**: Path-level parameters shared by all methods on this path
- **`servers`**: Optional server overrides for this path

### Creating Path Items

To define a path with multiple operations:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val userPaths = Paths(ChunkMap(
  "/users" -> PathItem(
    get = Some(Operation(
      summary = Some("List users"),
      responses = Responses(ChunkMap(
        "200" -> ReferenceOr.Value(Response(
          description = Doc("User list"),
          content = ChunkMap(
            "application/json" -> MediaType(schema = None)
          )
        ))
      ))
    )),
    post = Some(Operation(
      summary = Some("Create user"),
      requestBody = Some(ReferenceOr.Value(RequestBody(
        description = Some(Doc("User data")),
        content = ChunkMap(
          "application/json" -> MediaType(schema = None)
        ),
        required = true
      ))),
      responses = Responses(ChunkMap(
        "201" -> ReferenceOr.Value(Response(
          description = Doc("User created"),
          content = ChunkMap(
            "application/json" -> MediaType(schema = None)
          )
        ))
      ))
    ))
  ),
  "/users/{id}" -> PathItem(
    parameters = Some(Chunk(
      ReferenceOr.Value(Parameter(
        name = "id",
        in = ParameterLocation.Path,
        required = true,
        schema = Some(ReferenceOr.Value(Schema[String].toOpenAPISchema))
      ))
    )),
    get = Some(Operation(
      summary = Some("Get user by ID"),
      responses = Responses(ChunkMap(
        "200" -> ReferenceOr.Value(Response(
          description = Doc("User found"),
          content = ChunkMap(
            "application/json" -> MediaType(schema = None)
          )
        )),
        "404" -> ReferenceOr.Value(Response(
          description = Doc("User not found"),
          content = ChunkMap(
            "application/json" -> MediaType(schema = None)
          )
        ))
      ))
    ))
  )
))
```

---

## Operation

`Operation` represents a single HTTP operation (GET, POST, etc.) on a path.

### Definition

Key fields:
- **`responses`**: Required. Map of status codes to response definitions
- **`operationId`**: Unique operation identifier
- **`summary`**: Short description
- **`description`**: Detailed markdown description
- **`parameters`**: Path, query, header, and cookie parameters
- **`requestBody`**: Request payload definition
- **`deprecated`**: Whether the operation is deprecated
- **`tags`**: Group operations in documentation (e.g., `"users"`, `"products"`)

### Defining an Operation

With summary, description, and parameters:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val getUser = Operation(
  tags = Some(Chunk("users")),
  summary = Some("Retrieve user"),
  description = Some(Doc("Fetches a single user by ID")),
  operationId = Some("getUserById"),
  parameters = Some(Chunk(
    ReferenceOr.Value(Parameter(
      name = "id",
      in = ParameterLocation.Path,
      required = true,
      schema = Some(ReferenceOr.Value(Schema[String].toOpenAPISchema)),
      description = Some(Doc("User ID"))
    ))
  )),
  responses = Responses(ChunkMap(
    "200" -> ReferenceOr.Value(Response(
      description = Doc("User found"),
      content = ChunkMap(
        "application/json" -> MediaType(schema = None)
      )
    )),
    "404" -> ReferenceOr.Value(Response(
      description = Doc("User not found"),
      content = ChunkMap(
        "application/json" -> MediaType(schema = None)
      )
    ))
  ))
)
```

---

## Parameter

`Parameter` represents query, path, header, or cookie parameters in a request.

### Definition

Required fields:
- **`name`**: Parameter name (e.g., `"id"`, `"limit"`)
- **`in`**: Location—`Path`, `Query`, `Header`, or `Cookie` (`ParameterLocation`)
- **`schema`**: Data type of the parameter (`ReferenceOr[SchemaObject]`)

Optional fields:
- **`description`**: Markdown description
- **`required`**: Whether the parameter is mandatory (default: `false`)
- **`deprecated`**: Whether the parameter is deprecated
- **`allowEmptyValue`**: Whether empty string values are allowed

### Creating Parameters

Path parameter (required):

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val idPathParam = Parameter(
  name = "id",
  in = ParameterLocation.Path,
  required = true,
  schema = Some(ReferenceOr.Value(Schema[String].toOpenAPISchema)),
  description = Some(Doc("User identifier"))
)
```

Query parameter (optional with default):

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val limitQueryParam = Parameter(
  name = "limit",
  in = ParameterLocation.Query,
  required = false,
  schema = Some(ReferenceOr.Value(Schema[Int].toOpenAPISchema)),
  description = Some(Doc("Maximum number of results (default: 20)"))
)
```

Header parameter:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val authHeaderParam = Parameter(
  name = "X-API-Key",
  in = ParameterLocation.Header,
  required = true,
  schema = Some(ReferenceOr.Value(Schema[String].toOpenAPISchema)),
  description = Some(Doc("API key for authentication"))
)
```

---

## RequestBody & Response

`RequestBody` defines the structure of a request payload. `Response` defines the structure and status of a response.

### RequestBody Definition

Key fields:
- **`content`**: Map of MIME types to `MediaType` definitions
- **`description`**: Optional markdown description
- **`required`**: Whether the request body is mandatory (default: `false`)

### Creating a RequestBody

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val createUserBody = RequestBody(
  description = Some(Doc("User data to create")),
  content = ChunkMap(
    "application/json" -> MediaType(
      schema = Some(ReferenceOr.Value(Schema[User].toOpenAPISchema)),
      example = Some(Json.Object(Map(
        "name" -> Json.String("John Doe"),
        "email" -> Json.String("john@example.com")
      )))
    )
  ),
  required = true
)
```

### Response Definition

Key fields:
- **`description`**: Required. Markdown description of the response
- **`content`**: Map of MIME types to `MediaType` definitions
- **`headers`**: Optional response headers
- **`links`**: Optional links to related operations

### Creating a Response

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val successResponse = Response(
  description = Doc("User successfully created"),
  content = ChunkMap(
    "application/json" -> MediaType(
      schema = Some(ReferenceOr.Value(Schema[User].toOpenAPISchema))
    )
  )
)

val errorResponse = Response(
  description = Doc("Request validation failed"),
  content = ChunkMap(
    "application/json" -> MediaType(
      schema = Some(ReferenceOr.Value(Schema[ErrorResponse].toOpenAPISchema))
    )
  )
)
```

### Responses

`Responses` is a map of HTTP status codes to `ReferenceOr[Response]`:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val responses = Responses(ChunkMap(
  "201" -> ReferenceOr.Value(successResponse),
  "400" -> ReferenceOr.Value(errorResponse),
  "401" -> ReferenceOr.Value(Response(
    description = Doc("Unauthorized"),
    content = ChunkMap()
  )),
  "500" -> ReferenceOr.Value(Response(
    description = Doc("Internal server error"),
    content = ChunkMap()
  ))
))
```

---

## MediaType

`MediaType` specifies the schema and encoding for a particular MIME type in a request or response.

### Definition

Key fields:
- **`schema`**: Data type for this MIME type (`ReferenceOr[SchemaObject]`)
- **`example`**: Example value as `Json`
- **`encoding`**: Encoding rules for multipart form data
- **`extensions`**: Custom vendor extensions (`x-*` fields)

### Creating MediaType

With schema and example:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val jsonMedia = MediaType(
  schema = Some(ReferenceOr.Value(Schema[User].toOpenAPISchema)),
  example = Some(Json.Object(Map(
    "id" -> Json.Number(1),
    "name" -> Json.String("Alice"),
    "email" -> Json.String("alice@example.com")
  )))
)
```

For form data:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val formMedia = MediaType(
  schema = Some(ReferenceOr.Value(Schema[Map[String, String]].toOpenAPISchema)),
  encoding = Some(ChunkMap(
    "file" -> Encoding(
      contentType = Some("application/octet-stream"),
      style = Some("form")
    )
  ))
)
```

---

## Components

`Components` stores reusable schema and security definitions referenced throughout the document.

### Definition

Key fields:
- **`schemas`**: Reusable schema objects (`ChunkMap[String, SchemaObject]`)
- **`responses`**: Reusable response definitions
- **`parameters`**: Reusable parameter definitions
- **`securitySchemes`**: Authentication method definitions
- **`examples`**, **`requestBodies`**, **`headers`**, **`links`**, **`callbacks`**: Additional reusable components

### Creating Components

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val components = Components(
  schemas = Some(ChunkMap(
    Schema[User].toRefSchema._2,
    Schema[ErrorResponse].toRefSchema._2
  )),
  parameters = Some(ChunkMap(
    "id" -> ReferenceOr.Value(Parameter(
      name = "id",
      in = ParameterLocation.Path,
      required = true,
      schema = Some(ReferenceOr.Value(Schema[String].toOpenAPISchema))
    ))
  )),
  responses = Some(ChunkMap(
    "NotFound" -> ReferenceOr.Value(Response(
      description = Doc("Resource not found"),
      content = ChunkMap(
        "application/json" -> MediaType(
          schema = Some(ReferenceOr.Value(
            Schema[ErrorResponse].toOpenAPISchema
          ))
        )
      )
    )),
    "Unauthorized" -> ReferenceOr.Value(Response(
      description = Doc("Unauthorized access"),
      content = ChunkMap()
    ))
  )),
  securitySchemes = Some(ChunkMap(
    "api_key" -> ReferenceOr.Value(SecurityScheme.APIKey(
      name = "X-API-Key",
      in = "header",
      description = Some(Doc("API key header"))
    ))
  ))
)
```

---

## SchemaObject

`SchemaObject` wraps a JSON Schema 2020-12 definition with OpenAPI-specific extensions like discriminator, XML metadata, and examples.

### Definition

`SchemaObject` contains:
- **`jsonSchema`**: Raw JSON Schema 2020-12 as `Json` AST
- **`discriminator`**: Polymorphism discriminator for oneOf/anyOf
- **`xml`**: XML serialization metadata
- **`example`**: Example value for documentation
- **`extensions`**: Custom `x-*` fields

### Creating SchemaObject

Directly from a `Schema[A]`:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val userSchema = Schema[User].toOpenAPISchema
// Returns a SchemaObject with the User type's JSON Schema
```

Or with additional OpenAPI metadata:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val enrichedSchema = SchemaObject(
  jsonSchema = Schema[User].toJsonSchema.toJson,
  discriminator = None,
  xml = Some(XML(
    name = Some("user"),
    prefix = None,
    namespace = None,
    attribute = false,
    wrapped = false,
    extensions = None
  )),
  example = Some(Json.Object(Map(
    "id" -> Json.Number(1),
    "name" -> Json.String("John")
  ))),
  extensions = Some(ChunkMap(
    "x-generated" -> Json.String("true"),
    "x-version" -> Json.String("1.0.0")
  ))
)
```

### Converting Schemas to SchemaObject

Use the `SchemaOps` extension methods on any `Schema[A]`:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val schemaObj: SchemaObject = Schema[User].toOpenAPISchema
val (ref, component) = Schema[User].toRefSchema
// ref = ReferenceOr.Ref pointing to #/components/schemas/User
// component = ("User", SchemaObject(...))
```

---

## ReferenceOr

`ReferenceOr[A]` is a sealed trait representing the OpenAPI pattern of choosing between a `$ref` and an inline value.

### Definition

Two cases:
- **`ReferenceOr.Ref`**: Points to a definition at `#/components/<type>/<name>`
- **`ReferenceOr.Value`**: Inline definition without reference

### Using ReferenceOr

Prefer `Ref` for reusable components:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val userRef = ReferenceOr.Ref(Reference("$ref" -> "#/components/schemas/User"))

val responseRef = ReferenceOr.Ref(Reference(
  "$ref" -> "#/components/responses/NotFound"
))
```

Use `Value` for inline, one-off definitions:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val inlineUser = ReferenceOr.Value(Schema[User].toOpenAPISchema)

val inlineError = ReferenceOr.Value(Response(
  description = Doc("Quick error"),
  content = ChunkMap()
))
```

### Pattern Matching on ReferenceOr

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

def describeRef[A](ref: ReferenceOr[A]): String = ref match {
  case ReferenceOr.Ref(r) => s"Reference to ${r.ref}"
  case ReferenceOr.Value(v) => "Inline value"
}
```

---

## SecurityScheme

`SecurityScheme` is a sealed trait representing different authentication methods. Variants include API Key, HTTP Basic/Bearer, OAuth 2.0, OpenID Connect, and Mutual TLS.

### Definition

Sealed trait variants:
- **`APIKey`**: API key in header, query, or cookie
- **`HTTP`**: HTTP authentication (Basic, Bearer, etc.)
- **`OAuth2`**: OAuth 2.0 authorization flows
- **`OpenIdConnect`**: OpenID Connect discovery
- **`MutualTLS`**: Mutual TLS certificate-based

### Creating Security Schemes

API Key authentication:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val apiKeySecurity = SecurityScheme.APIKey(
  name = "X-API-Key",
  in = "header",
  description = Some(Doc("API key required in header"))
)
```

HTTP Bearer token:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val bearerSecurity = SecurityScheme.HTTP(
  scheme = "bearer",
  bearerFormat = Some("JWT"),
  description = Some(Doc("JWT bearer token"))
)
```

OAuth 2.0:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val oauthSecurity = SecurityScheme.OAuth2(
  flows = OAuthFlows(
    authorizationCode = Some(OAuthFlow(
      authorizationUrl = "https://example.com/oauth/authorize",
      tokenUrl = "https://example.com/oauth/token",
      scopes = ChunkMap(
        "read:users" -> Doc("Read user data"),
        "write:users" -> Doc("Modify user data")
      )
    ))
  ),
  description = Some(Doc("OAuth 2.0 authorization"))
)
```

:::note
The `OAuthFlows` type supports multiple flow types: `implicit`, `password`, `clientCredentials`, and `authorizationCode`. Choose the flow that matches your OAuth 2.0 configuration.
:::

OpenID Connect:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val oidcSecurity = SecurityScheme.OpenIdConnect(
  openIdConnectUrl = "https://example.com/.well-known/openid-configuration",
  description = Some(Doc("OpenID Connect discovery"))
)
```

---

## Discriminator

`Discriminator` specifies how to distinguish between different variants in a polymorphic schema (using `oneOf` or `anyOf`).

### Definition

Key fields:
- **`propertyName`**: Field name used to discriminate (e.g., `"type"`, `"kind"`)
- **`mapping`**: Optional explicit mapping of discriminator values to schema references

### Creating a Discriminator

Simple discriminator by property name:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val discriminator = Discriminator(
  propertyName = "type",
  mapping = None
)
```

With explicit value-to-schema mapping:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val mappedDiscriminator = Discriminator(
  propertyName = "kind",
  mapping = Some(ChunkMap(
    "user" -> "#/components/schemas/User",
    "admin" -> "#/components/schemas/Admin",
    "guest" -> "#/components/schemas/Guest"
  ))
)
```

---

## Server & ServerVariable

`Server` specifies base URLs and server-specific variables. `ServerVariable` allows parameterization of server URLs.

### Definition

`Server` contains:
- **`url`**: Server URL (may contain variable placeholders like `{base_path}`)
- **`description`**: Optional description
- **`variables`**: Map of variable names to `ServerVariable`

`ServerVariable` contains:
- **`enum`**: List of allowed values
- **`default`**: Default value
- **`description`**: Description of the variable

### Creating Servers

Single static server:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val server = Server(
  url = "https://api.example.com",
  description = Some(Doc("Production API")),
  variables = None
)
```

Server with variables (showing structure without reserved keywords):

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val variableServer = Server(
  url = "https://{host}:{port}/{basePath}",
  description = Some(Doc("Development API with variables")),
  variables = Some(ChunkMap(
    "host" -> ServerVariable(
      default = "localhost",
      enumValues = Some(Chunk("localhost", "staging.example.com", "api.example.com")),
      description = Some(Doc("API host"))
    ),
    "port" -> ServerVariable(
      default = "8080",
      enumValues = Some(Chunk("8080", "443")),
      description = Some(Doc("Port number"))
    ),
    "basePath" -> ServerVariable(
      default = "v1",
      enumValues = Some(Chunk("v1", "v2")),
      description = Some(Doc("API version path"))
    )
  ))
)
```

:::note
`ServerVariable` contains enumerable values for each variable. The field is named to avoid the reserved `enum` keyword in Scala.
:::

---

## Tag

`Tag` groups related operations under a heading in generated documentation.

### Definition

Key fields:
- **`name`**: Tag identifier (e.g., `"users"`, `"products"`)
- **`description`**: Markdown description of the tag
- **`externalDocs`**: Link to external documentation

### Creating Tags

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val userTag = Tag(
  name = "users",
  description = Some(Doc("User management operations")),
  externalDocs = None
)

val productsTag = Tag(
  name = "products",
  description = Some(Doc("Product catalog operations")),
  externalDocs = Some(ExternalDocumentation(
    url = "https://docs.example.com/products",
    description = Some(Doc("Full product API documentation"))
  ))
)
```

---

## Common Extension Fields

All types support custom `x-*` extension fields for vendor-specific metadata. These extensions are preserved during encoding/decoding:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val operationWithExtensions = Operation(
  summary = Some("Get user"),
  responses = Responses(ChunkMap(
    "200" -> ReferenceOr.Value(Response(
      description = Doc("User found"),
      content = ChunkMap()
    ))
  )),
  extensions = Some(ChunkMap(
    "x-internal" -> Json.Bool(true),
    "x-rate-limit" -> Json.Number(100),
    "x-deprecated-at" -> Json.String("2024-01-01")
  ))
)
```

---

## Round-Tripping with Schema

All OpenAPI types have `Schema.derived` instances, enabling serialization through `DynamicValue`:

```scala mdoc:compile-only
import zio.blocks.openapi._
import zio.blocks.markdown._
import zio.blocks.chunk._

val openAPISchema = Schema[OpenAPI]

val apiDynamic = openAPISchema.toDynamicValue(api)

val apiRestored = openAPISchema.fromDynamicValue(apiDynamic)
// apiRestored == api
```

This enables integration with other ZIO Blocks modules that work with `DynamicValue`.
