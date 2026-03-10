package zio.blocks.openapi

import zio.blocks.chunk.{Chunk, ChunkMap}
import zio.blocks.docs.{Doc, Inline, Paragraph}
import zio.blocks.schema._
import zio.blocks.schema.json.Json
import zio.test._

object OpenAPIRoundTripSpec extends SchemaBaseSpec {
  private def doc(s: String): Doc      = Doc(Chunk.single(Paragraph(Chunk.single(Inline.Text(s)))))
  def spec: Spec[TestEnvironment, Any] = suite("OpenAPIRoundTripSpec")(
    suite("JSON round-trip tests")(
      test("minimal.json round-trips through JSON") {
        val jsonString = scala.util.Using.resource(scala.io.Source.fromResource("openapi/minimal.json"))(_.mkString)
        val parsed     = Json.parse(jsonString)
        assertTrue(parsed.isRight)
      },
      test("petstore.json round-trips through JSON") {
        val jsonString = scala.util.Using.resource(scala.io.Source.fromResource("openapi/petstore.json"))(_.mkString)
        val parsed     = Json.parse(jsonString)
        assertTrue(parsed.isRight)
      },
      test("with-security.json round-trips through JSON") {
        val jsonString =
          scala.util.Using.resource(scala.io.Source.fromResource("openapi/with-security.json"))(_.mkString)
        val parsed = Json.parse(jsonString)
        assertTrue(parsed.isRight)
      }
    ),
    suite("Basic types round-trip through DynamicValue")(
      test("Info round-trips with all fields") {
        val original = Info(
          title = "Test API",
          version = "1.0.0",
          summary = Some(doc("A test API")),
          description = Some(doc("Detailed description")),
          termsOfService = Some("https://example.com/terms"),
          contact = Some(
            Contact(
              name = Some("Support Team"),
              url = Some("https://example.com/support"),
              email = Some("support@example.com"),
              extensions = ChunkMap("x-contact-id" -> Json.String("12345"))
            )
          ),
          license = Some(
            License(
              name = "MIT",
              identifierOrUrl = Some(Left("MIT")),
              extensions = ChunkMap("x-license-year" -> Json.Number(2024))
            )
          ),
          extensions = ChunkMap("x-api-version" -> Json.String("v1"))
        )

        val dv     = Schema[Info].toDynamicValue(original)
        val result = Schema[Info].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("Contact round-trips with all fields") {
        val original = Contact(
          name = Some("John Doe"),
          url = Some("https://example.com"),
          email = Some("john@example.com"),
          extensions = ChunkMap("x-custom" -> Json.Boolean(true))
        )

        val dv     = Schema[Contact].toDynamicValue(original)
        val result = Schema[Contact].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("Contact round-trips with no fields") {
        val original = Contact()

        val dv     = Schema[Contact].toDynamicValue(original)
        val result = Schema[Contact].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("License round-trips with identifier") {
        val original = License(
          name = "Apache 2.0",
          identifierOrUrl = Some(Left("Apache-2.0")),
          extensions = ChunkMap("x-year" -> Json.Number(2024))
        )

        val dv     = Schema[License].toDynamicValue(original)
        val result = Schema[License].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("License round-trips with url") {
        val original = License(
          name = "Custom License",
          identifierOrUrl = Some(Right("https://example.com/license")),
          extensions = ChunkMap("x-custom" -> Json.String("value"))
        )

        val dv     = Schema[License].toDynamicValue(original)
        val result = Schema[License].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("License round-trips with neither identifier nor url") {
        val original = License(name = "Proprietary")

        val dv     = Schema[License].toDynamicValue(original)
        val result = Schema[License].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      }
    ),
    suite("Server types round-trip through DynamicValue")(
      test("Server round-trips with all fields") {
        val original = Server(
          url = "https://api.example.com/v1",
          description = Some(doc("Production server")),
          variables = ChunkMap(
            "environment" -> ServerVariable(
              default = "prod",
              `enum` = Chunk("prod", "staging", "dev"),
              description = Some(doc("Environment name")),
              extensions = ChunkMap("x-var-id" -> Json.String("env-1"))
            ),
            "region" -> ServerVariable(
              default = "us-east",
              description = Some(doc("AWS region"))
            )
          ),
          extensions = ChunkMap("x-server-id" -> Json.Number(42))
        )

        val dv     = Schema[Server].toDynamicValue(original)
        val result = Schema[Server].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("Server round-trips with minimal fields") {
        val original = Server(url = "https://api.example.com")

        val dv     = Schema[Server].toDynamicValue(original)
        val result = Schema[Server].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("ServerVariable round-trips with enum") {
        val original = ServerVariable(
          default = "v1",
          `enum` = Chunk("v1", "v2", "v3"),
          description = Some(doc("API version")),
          extensions = ChunkMap("x-deprecated" -> Json.Boolean(false))
        )

        val dv     = Schema[ServerVariable].toDynamicValue(original)
        val result = Schema[ServerVariable].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("ServerVariable round-trips without enum") {
        val original = ServerVariable(default = "default-value")

        val dv     = Schema[ServerVariable].toDynamicValue(original)
        val result = Schema[ServerVariable].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      }
    ),
    suite("Tag and ExternalDocumentation round-trip")(
      test("Tag round-trips with all fields") {
        val original = Tag(
          name = "users",
          description = Some(doc("User operations")),
          externalDocs = Some(
            ExternalDocumentation(
              url = "https://docs.example.com/users",
              description = Some(doc("User documentation")),
              extensions = ChunkMap("x-doc-version" -> Json.String("1.0"))
            )
          ),
          extensions = ChunkMap("x-tag-color" -> Json.String("blue"))
        )

        val dv     = Schema[Tag].toDynamicValue(original)
        val result = Schema[Tag].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("Tag round-trips with minimal fields") {
        val original = Tag(name = "minimal")

        val dv     = Schema[Tag].toDynamicValue(original)
        val result = Schema[Tag].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("ExternalDocumentation round-trips") {
        val original = ExternalDocumentation(
          url = "https://docs.example.com",
          description = Some(doc("Full documentation")),
          extensions = ChunkMap("x-language" -> Json.String("en"))
        )

        val dv     = Schema[ExternalDocumentation].toDynamicValue(original)
        val result = Schema[ExternalDocumentation].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      }
    ),
    suite("Reference and ReferenceOr round-trip")(
      test("Reference round-trips") {
        val original = Reference(
          `$ref` = "#/components/schemas/User",
          summary = Some(doc("User reference")),
          description = Some(doc("Reference to User schema"))
        )

        val dv     = Schema[Reference].toDynamicValue(original)
        val result = Schema[Reference].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("ReferenceOr.Ref round-trips") {
        val original: ReferenceOr[String] = ReferenceOr.Ref(
          Reference(`$ref` = "#/components/schemas/Pet")
        )

        val dv     = Schema[ReferenceOr[String]].toDynamicValue(original)
        val result = Schema[ReferenceOr[String]].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("ReferenceOr.Value round-trips") {
        val original: ReferenceOr[String] = ReferenceOr.Value("actual value")

        val dv     = Schema[ReferenceOr[String]].toDynamicValue(original)
        val result = Schema[ReferenceOr[String]].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      }
    ),
    suite("Schema-related types round-trip")(
      test("SchemaObject round-trips with all fields") {
        val original = SchemaObject(
          jsonSchema = Json.Object(
            "type"       -> Json.String("object"),
            "properties" -> Json.Object(
              "name" -> Json.Object("type" -> Json.String("string"))
            )
          ),
          discriminator = Some(
            Discriminator(
              propertyName = "type",
              mapping = ChunkMap("user" -> "#/components/schemas/User")
            )
          ),
          xml = Some(
            XML(
              name = Some("User"),
              namespace = Some("http://example.com/schema"),
              prefix = Some("ex"),
              attribute = true,
              wrapped = false
            )
          ),
          externalDocs = Some(
            ExternalDocumentation(
              url = "https://example.com/schema-docs"
            )
          ),
          example = Some(Json.Object("name" -> Json.String("John"))),
          extensions = ChunkMap("x-schema-id" -> Json.String("user-v1"))
        )

        val dv     = Schema[SchemaObject].toDynamicValue(original)
        val result = Schema[SchemaObject].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("OpenAPIDiscriminator round-trips") {
        val original = Discriminator(
          propertyName = "petType",
          mapping = ChunkMap(
            "dog" -> "#/components/schemas/Dog",
            "cat" -> "#/components/schemas/Cat"
          )
        )

        val dv     = Schema[Discriminator].toDynamicValue(original)
        val result = Schema[Discriminator].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("XML round-trips with all fields") {
        val original = XML(
          name = Some("animal"),
          namespace = Some("http://example.com/xml"),
          prefix = Some("ns"),
          attribute = true,
          wrapped = true
        )

        val dv     = Schema[XML].toDynamicValue(original)
        val result = Schema[XML].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      }
    ),
    suite("Operation and Parameter types round-trip")(
      test("Operation round-trips with all fields") {
        val original = Operation(
          responses = Responses(
            responses = ChunkMap(
              "200" -> ReferenceOr.Value(Response(description = doc("Success")))
            )
          ),
          tags = Chunk("users", "admin"),
          summary = Some(doc("Get user")),
          description = Some(doc("Retrieves a user by ID")),
          externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com/get-user")),
          operationId = Some("getUser"),
          parameters = Chunk(
            ReferenceOr.Value(Parameter(name = "id", in = ParameterLocation.Path, required = true))
          ),
          requestBody = Some(
            ReferenceOr.Value(RequestBody(content = ChunkMap("application/json" -> MediaType()), required = true))
          ),
          callbacks = ChunkMap("onData" -> ReferenceOr.Value(Callback())),
          deprecated = true,
          security = Chunk(SecurityRequirement(ChunkMap("api_key" -> Chunk("read")))),
          servers = Chunk(Server(url = "https://api.example.com")),
          extensions = ChunkMap("x-operation-id" -> Json.Number(123))
        )

        val dv     = Schema[Operation].toDynamicValue(original)
        val result = Schema[Operation].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("Parameter round-trips with query location") {
        val original = Parameter(
          name = "limit",
          in = ParameterLocation.Query,
          description = Some(doc("Page limit")),
          required = false,
          deprecated = false,
          allowEmptyValue = true,
          style = Some("form"),
          explode = Some(true),
          allowReserved = Some(false),
          schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("integer"))))),
          example = Some(Json.Number(10)),
          examples = ChunkMap("default" -> ReferenceOr.Value(Example(value = Some(Json.Number(10))))),
          content = ChunkMap("application/json" -> MediaType()),
          extensions = ChunkMap("x-param-id" -> Json.String("limit-1"))
        )

        val dv     = Schema[Parameter].toDynamicValue(original)
        val result = Schema[Parameter].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("Parameter round-trips with path location") {
        val original = Parameter(
          name = "id",
          in = ParameterLocation.Path,
          required = true,
          schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("string")))))
        )

        val dv     = Schema[Parameter].toDynamicValue(original)
        val result = Schema[Parameter].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("Header round-trips") {
        val original = Header(
          description = Some(doc("Authorization header")),
          required = true,
          deprecated = false,
          allowEmptyValue = false,
          style = Some("simple"),
          explode = Some(false),
          allowReserved = Some(false),
          schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("string"))))),
          example = Some(Json.String("Bearer token")),
          examples = ChunkMap("auth" -> ReferenceOr.Value(Example(value = Some(Json.String("Bearer xyz"))))),
          content = ChunkMap.empty[String, MediaType],
          extensions = ChunkMap("x-header-id" -> Json.String("auth-1"))
        )

        val dv     = Schema[Header].toDynamicValue(original)
        val result = Schema[Header].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      }
    ),
    suite("Request and Response types round-trip")(
      test("RequestBody round-trips") {
        val original = RequestBody(
          content = ChunkMap(
            "application/json" -> MediaType(
              schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("object"))))),
              example = Some(Json.Object("name" -> Json.String("test"))),
              examples = ChunkMap.empty[String, ReferenceOr[Example]],
              encoding = ChunkMap.empty[String, Encoding]
            )
          ),
          description = Some(doc("User object")),
          required = true,
          extensions = ChunkMap("x-body-id" -> Json.String("user-body"))
        )

        val dv     = Schema[RequestBody].toDynamicValue(original)
        val result = Schema[RequestBody].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("MediaType round-trips with all fields") {
        val original = MediaType(
          schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("string"))))),
          example = Some(Json.String("example value")),
          examples = ChunkMap(
            "example1" -> ReferenceOr.Value(
              Example(
                summary = Some(doc("First example")),
                value = Some(Json.String("value1"))
              )
            )
          ),
          encoding = ChunkMap(
            "field1" -> Encoding(
              contentType = Some("application/json"),
              headers = ChunkMap.empty[String, ReferenceOr[Header]],
              style = Some("form"),
              explode = Some(true),
              allowReserved = false,
              extensions = ChunkMap("x-encoding" -> Json.Boolean(true))
            )
          ),
          extensions = ChunkMap("x-media-type" -> Json.String("custom"))
        )

        val dv     = Schema[MediaType].toDynamicValue(original)
        val result = Schema[MediaType].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("Encoding round-trips") {
        val original = Encoding(
          contentType = Some("application/json"),
          headers = ChunkMap(
            "X-Custom" -> ReferenceOr.Value(
              Header(
                description = Some(doc("Custom header"))
              )
            )
          ),
          style = Some("form"),
          explode = Some(false),
          allowReserved = true,
          extensions = ChunkMap("x-enc-id" -> Json.Number(1))
        )

        val dv     = Schema[Encoding].toDynamicValue(original)
        val result = Schema[Encoding].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("Responses round-trips") {
        val original = Responses(
          responses = ChunkMap(
            "200" -> ReferenceOr.Value(
              Response(
                description = doc("Success"),
                headers = ChunkMap.empty[String, ReferenceOr[Header]],
                content = ChunkMap.empty[String, MediaType],
                links = ChunkMap.empty[String, ReferenceOr[Link]]
              )
            ),
            "404" -> ReferenceOr.Ref(Reference(`$ref` = "#/components/responses/NotFound"))
          ),
          default = Some(ReferenceOr.Value(Response(description = doc("Error")))),
          extensions = ChunkMap("x-responses" -> Json.String("custom"))
        )

        val dv     = Schema[Responses].toDynamicValue(original)
        val result = Schema[Responses].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("Response round-trips") {
        val original = Response(
          description = doc("Successful operation"),
          headers = ChunkMap(
            "X-Rate-Limit" -> ReferenceOr.Value(
              Header(
                schema =
                  Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("integer")))))
              )
            )
          ),
          content = ChunkMap(
            "application/json" -> MediaType(
              schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("object")))))
            )
          ),
          links = ChunkMap(
            "next" -> ReferenceOr.Value(
              Link(
                operationRefOrId = Some(Right("getNextPage")),
                description = Some(doc("Next page link"))
              )
            )
          ),
          extensions = ChunkMap("x-response-id" -> Json.String("resp-1"))
        )

        val dv     = Schema[Response].toDynamicValue(original)
        val result = Schema[Response].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      }
    ),
    suite("Example and Link types round-trip")(
      test("Example round-trips with value") {
        val original = Example(
          summary = Some(doc("User example")),
          description = Some(doc("Example of a user object")),
          value = Some(Json.Object("name" -> Json.String("John"), "age" -> Json.Number(30))),
          extensions = ChunkMap("x-example-id" -> Json.String("ex-1"))
        )

        val dv     = Schema[Example].toDynamicValue(original)
        val result = Schema[Example].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("Example round-trips with externalValue") {
        val original = Example(
          summary = Some(doc("External example")),
          externalValue = Some("https://example.com/examples/user.json"),
          extensions = ChunkMap("x-external" -> Json.Boolean(true))
        )

        val dv     = Schema[Example].toDynamicValue(original)
        val result = Schema[Example].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("Link round-trips with operationRef") {
        val original = Link(
          operationRefOrId = Some(Left("#/paths/~1users~1{id}/get")),
          parameters = ChunkMap("id" -> Json.String("$response.body#/id")),
          requestBody = Some(Json.Object("data" -> Json.String("value"))),
          description = Some(doc("Link to get user")),
          server = Some(Server(url = "https://api.example.com")),
          extensions = ChunkMap("x-link-id" -> Json.String("link-1"))
        )

        val dv     = Schema[Link].toDynamicValue(original)
        val result = Schema[Link].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("Link round-trips with operationId") {
        val original = Link(
          operationRefOrId = Some(Right("getUserById")),
          parameters = ChunkMap("id" -> Json.Number(123)),
          description = Some(doc("Link by operation ID")),
          extensions = ChunkMap("x-link-type" -> Json.String("operation-id"))
        )

        val dv     = Schema[Link].toDynamicValue(original)
        val result = Schema[Link].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      }
    ),
    suite("Callback round-trip")(
      test("Callback round-trips") {
        val original = Callback(
          callbacks = ChunkMap(
            "{$request.body#/callbackUrl}" -> ReferenceOr.Value(
              PathItem(
                summary = Some(doc("Callback path")),
                description = Some(doc("Webhook callback"))
              )
            )
          ),
          extensions = ChunkMap("x-callback-id" -> Json.String("cb-1"))
        )

        val dv     = Schema[Callback].toDynamicValue(original)
        val result = Schema[Callback].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      }
    ),
    suite("Security types round-trip")(
      test("SecurityScheme.APIKey round-trips") {
        val original: SecurityScheme = SecurityScheme.APIKey(
          name = "api_key",
          in = APIKeyLocation.Header,
          description = Some(doc("API key authentication")),
          extensions = ChunkMap("x-api-key-format" -> Json.String("uuid"))
        )

        val dv     = Schema[SecurityScheme].toDynamicValue(original)
        val result = Schema[SecurityScheme].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("SecurityScheme.HTTP round-trips") {
        val original: SecurityScheme = SecurityScheme.HTTP(
          scheme = "bearer",
          bearerFormat = Some("JWT"),
          description = Some(doc("Bearer token authentication")),
          extensions = ChunkMap("x-token-type" -> Json.String("jwt"))
        )

        val dv     = Schema[SecurityScheme].toDynamicValue(original)
        val result = Schema[SecurityScheme].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("SecurityScheme.OAuth2 round-trips") {
        val original: SecurityScheme = SecurityScheme.OAuth2(
          flows = OAuthFlows(
            `implicit` = Some(
              OAuthFlow(
                authorizationUrl = Some("https://example.com/oauth/authorize"),
                scopes = ChunkMap("read" -> "Read access", "write" -> "Write access")
              )
            ),
            password = Some(
              OAuthFlow(
                tokenUrl = Some("https://example.com/oauth/token"),
                scopes = ChunkMap("admin" -> "Admin access")
              )
            ),
            clientCredentials = Some(
              OAuthFlow(
                tokenUrl = Some("https://example.com/oauth/token"),
                scopes = ChunkMap.empty
              )
            ),
            authorizationCode = Some(
              OAuthFlow(
                authorizationUrl = Some("https://example.com/oauth/authorize"),
                tokenUrl = Some("https://example.com/oauth/token"),
                refreshUrl = Some("https://example.com/oauth/refresh"),
                scopes = ChunkMap("read" -> "Read", "write" -> "Write")
              )
            ),
            extensions = ChunkMap("x-flows" -> Json.String("all"))
          ),
          description = Some(doc("OAuth2 security")),
          extensions = ChunkMap("x-oauth-provider" -> Json.String("custom"))
        )

        val dv     = Schema[SecurityScheme].toDynamicValue(original)
        val result = Schema[SecurityScheme].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("SecurityScheme.OpenIdConnect round-trips") {
        val original: SecurityScheme = SecurityScheme.OpenIdConnect(
          openIdConnectUrl = "https://example.com/.well-known/openid-configuration",
          description = Some(doc("OpenID Connect authentication")),
          extensions = ChunkMap("x-oidc-provider" -> Json.String("custom"))
        )

        val dv     = Schema[SecurityScheme].toDynamicValue(original)
        val result = Schema[SecurityScheme].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("SecurityScheme.MutualTLS round-trips") {
        val original: SecurityScheme = SecurityScheme.MutualTLS(
          description = Some(doc("Mutual TLS authentication")),
          extensions = ChunkMap("x-cert-required" -> Json.Boolean(true))
        )

        val dv     = Schema[SecurityScheme].toDynamicValue(original)
        val result = Schema[SecurityScheme].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("OAuthFlows round-trips with all flows") {
        val original = OAuthFlows(
          `implicit` = Some(
            OAuthFlow(
              authorizationUrl = Some("https://example.com/auth"),
              scopes = ChunkMap("read" -> "Read")
            )
          ),
          password = Some(
            OAuthFlow(
              tokenUrl = Some("https://example.com/token"),
              scopes = ChunkMap("write" -> "Write")
            )
          ),
          clientCredentials = Some(
            OAuthFlow(
              tokenUrl = Some("https://example.com/token"),
              scopes = ChunkMap.empty
            )
          ),
          authorizationCode = Some(
            OAuthFlow(
              authorizationUrl = Some("https://example.com/auth"),
              tokenUrl = Some("https://example.com/token"),
              scopes = ChunkMap("admin" -> "Admin")
            )
          ),
          extensions = ChunkMap("x-oauth" -> Json.Boolean(true))
        )

        val dv     = Schema[OAuthFlows].toDynamicValue(original)
        val result = Schema[OAuthFlows].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("OAuthFlow round-trips") {
        val original = OAuthFlow(
          authorizationUrl = Some("https://example.com/authorize"),
          tokenUrl = Some("https://example.com/token"),
          refreshUrl = Some("https://example.com/refresh"),
          scopes = ChunkMap("read" -> "Read access", "write" -> "Write access"),
          extensions = ChunkMap("x-flow-id" -> Json.String("flow-1"))
        )

        val dv     = Schema[OAuthFlow].toDynamicValue(original)
        val result = Schema[OAuthFlow].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("SecurityRequirement round-trips") {
        val original = SecurityRequirement(
          requirements = ChunkMap(
            "oauth2"  -> Chunk("read", "write"),
            "api_key" -> Chunk.empty
          )
        )

        val dv     = Schema[SecurityRequirement].toDynamicValue(original)
        val result = Schema[SecurityRequirement].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      }
    ),
    suite("Container types round-trip")(
      test("Components round-trips") {
        val original = Components(
          schemas = ChunkMap(
            "User" -> ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("object"))))
          ),
          responses = ChunkMap("NotFound" -> ReferenceOr.Value(Response(description = doc("Not found")))),
          parameters = ChunkMap("limit" -> ReferenceOr.Value(Parameter(name = "limit", in = ParameterLocation.Query))),
          examples = ChunkMap(
            "user" -> ReferenceOr.Value(Example(value = Some(Json.Object("name" -> Json.String("John")))))
          ),
          requestBodies = ChunkMap(
            "UserBody" -> ReferenceOr.Value(
              RequestBody(content = ChunkMap("application/json" -> MediaType()), required = true)
            )
          ),
          headers = ChunkMap("X-Custom" -> ReferenceOr.Value(Header())),
          securitySchemes = ChunkMap(
            "api_key" -> ReferenceOr.Value(SecurityScheme.APIKey(name = "api_key", in = APIKeyLocation.Header))
          ),
          links = ChunkMap("next" -> ReferenceOr.Value(Link(operationRefOrId = Some(Right("getNext"))))),
          callbacks = ChunkMap("onEvent" -> ReferenceOr.Value(Callback())),
          pathItems = ChunkMap("/users" -> ReferenceOr.Value(PathItem(summary = Some(doc("Users"))))),
          extensions = ChunkMap("x-components" -> Json.String("custom"))
        )

        val dv     = Schema[Components].toDynamicValue(original)
        val result = Schema[Components].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("Paths round-trips") {
        val original = Paths(
          paths = ChunkMap(
            "/users" -> PathItem(
              summary = Some(doc("Users endpoint")),
              description = Some(doc("Operations on users"))
            ),
            "/posts" -> PathItem(summary = Some(doc("Posts")))
          ),
          extensions = ChunkMap("x-paths-version" -> Json.String("v1"))
        )

        val dv     = Schema[Paths].toDynamicValue(original)
        val result = Schema[Paths].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("PathItem round-trips") {
        val original = PathItem(
          summary = Some(doc("API endpoint")),
          description = Some(doc("Description of endpoint")),
          extensions = ChunkMap("x-path-id" -> Json.Number(123))
        )

        val dv     = Schema[PathItem].toDynamicValue(original)
        val result = Schema[PathItem].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      }
    ),
    suite("OpenAPI root object round-trip")(
      test("OpenAPI round-trips with minimal fields") {
        val original = OpenAPI(
          openapi = "3.1.0",
          info = Info(title = "Test API", version = "1.0.0")
        )

        val dv     = Schema[OpenAPI].toDynamicValue(original)
        val result = Schema[OpenAPI].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      },
      test("OpenAPI round-trips with all fields populated") {
        val original = OpenAPI(
          openapi = "3.1.0",
          info = Info(
            title = "Comprehensive API",
            version = "2.0.0",
            summary = Some(doc("A comprehensive test API")),
            description = Some(doc("Full featured API for testing")),
            termsOfService = Some("https://example.com/terms"),
            contact = Some(
              Contact(
                name = Some("API Support"),
                url = Some("https://example.com/support"),
                email = Some("support@example.com")
              )
            ),
            license = Some(License(name = "Apache 2.0", identifierOrUrl = Some(Left("Apache-2.0"))))
          ),
          jsonSchemaDialect = Some("https://spec.openapis.org/oas/3.1/dialect/base"),
          servers = Chunk(
            Server(
              url = "https://api.example.com/v1",
              description = Some(doc("Production")),
              variables = ChunkMap(
                "env" -> ServerVariable(default = "prod", `enum` = Chunk("prod", "staging"))
              )
            ),
            Server(url = "https://staging.example.com/v1", description = Some(doc("Staging")))
          ),
          paths = Some(
            Paths(
              paths = ChunkMap(
                "/users" -> PathItem(summary = Some(doc("Users")), description = Some(doc("User operations"))),
                "/posts" -> PathItem(summary = Some(doc("Posts")))
              )
            )
          ),
          components = Some(
            Components(
              schemas = ChunkMap(
                "User" -> ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("object"))))
              ),
              securitySchemes = ChunkMap("bearerAuth" -> ReferenceOr.Value(SecurityScheme.HTTP(scheme = "bearer")))
            )
          ),
          security = Chunk(
            SecurityRequirement(ChunkMap("bearerAuth" -> Chunk.empty)),
            SecurityRequirement(ChunkMap("api_key" -> Chunk("read")))
          ),
          tags = Chunk(
            Tag(
              name = "users",
              description = Some(doc("User operations")),
              externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com/users"))
            ),
            Tag(name = "posts")
          ),
          externalDocs = Some(
            ExternalDocumentation(
              url = "https://docs.example.com",
              description = Some(doc("Complete documentation"))
            )
          ),
          extensions = ChunkMap(
            "x-api-id"   -> Json.String("api-123"),
            "x-version"  -> Json.Number(2),
            "x-internal" -> Json.Boolean(false)
          )
        )

        val dv     = Schema[OpenAPI].toDynamicValue(original)
        val result = Schema[OpenAPI].fromDynamicValue(dv)

        assertTrue(result == Right(original))
      }
    ),
    suite("Extension preservation on all extensible types")(
      test("extensions preserved on Info") {
        val original = Info(
          title = "API",
          version = "1.0",
          extensions = ChunkMap("x-custom" -> Json.String("value"))
        )
        val dv     = Schema[Info].toDynamicValue(original)
        val result = Schema[Info].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on Contact") {
        val original = Contact(extensions = ChunkMap("x-id" -> Json.Number(1)))
        val dv       = Schema[Contact].toDynamicValue(original)
        val result   = Schema[Contact].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on License") {
        val original = License(name = "MIT", extensions = ChunkMap("x-year" -> Json.Number(2024)))
        val dv       = Schema[License].toDynamicValue(original)
        val result   = Schema[License].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on Server") {
        val original = Server(url = "https://api.example.com", extensions = ChunkMap("x-region" -> Json.String("us")))
        val dv       = Schema[Server].toDynamicValue(original)
        val result   = Schema[Server].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on ServerVariable") {
        val original = ServerVariable(default = "v1", extensions = ChunkMap("x-deprecated" -> Json.Boolean(false)))
        val dv       = Schema[ServerVariable].toDynamicValue(original)
        val result   = Schema[ServerVariable].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on Tag") {
        val original = Tag(name = "users", extensions = ChunkMap("x-order" -> Json.Number(1)))
        val dv       = Schema[Tag].toDynamicValue(original)
        val result   = Schema[Tag].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on ExternalDocumentation") {
        val original =
          ExternalDocumentation(url = "https://docs.example.com", extensions = ChunkMap("x-lang" -> Json.String("en")))
        val dv     = Schema[ExternalDocumentation].toDynamicValue(original)
        val result = Schema[ExternalDocumentation].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on SchemaObject") {
        val original = SchemaObject(
          jsonSchema = Json.Object(),
          extensions = ChunkMap("x-schema-version" -> Json.String("1.0"))
        )
        val dv     = Schema[SchemaObject].toDynamicValue(original)
        val result = Schema[SchemaObject].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on Operation") {
        val original = Operation(
          extensions = ChunkMap("x-rate-limit" -> Json.Number(100))
        )
        val dv     = Schema[Operation].toDynamicValue(original)
        val result = Schema[Operation].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on Parameter") {
        val original = Parameter(
          name = "id",
          in = ParameterLocation.Query,
          extensions = ChunkMap("x-internal" -> Json.Boolean(true))
        )
        val dv     = Schema[Parameter].toDynamicValue(original)
        val result = Schema[Parameter].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on Header") {
        val original = Header(extensions = ChunkMap("x-custom-header" -> Json.String("value")))
        val dv       = Schema[Header].toDynamicValue(original)
        val result   = Schema[Header].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on RequestBody") {
        val original = RequestBody(
          content = ChunkMap.empty[String, MediaType],
          extensions = ChunkMap("x-body-id" -> Json.String("req-1"))
        )
        val dv     = Schema[RequestBody].toDynamicValue(original)
        val result = Schema[RequestBody].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on MediaType") {
        val original = MediaType(extensions = ChunkMap("x-media" -> Json.String("custom")))
        val dv       = Schema[MediaType].toDynamicValue(original)
        val result   = Schema[MediaType].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on Encoding") {
        val original = Encoding(extensions = ChunkMap("x-encoding-id" -> Json.Number(1)))
        val dv       = Schema[Encoding].toDynamicValue(original)
        val result   = Schema[Encoding].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on Responses") {
        val original = Responses(extensions = ChunkMap("x-responses" -> Json.String("custom")))
        val dv       = Schema[Responses].toDynamicValue(original)
        val result   = Schema[Responses].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on Response") {
        val original = Response(
          description = doc("OK"),
          extensions = ChunkMap("x-response-id" -> Json.String("resp-1"))
        )
        val dv     = Schema[Response].toDynamicValue(original)
        val result = Schema[Response].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on Example") {
        val original = Example(extensions = ChunkMap("x-example-source" -> Json.String("manual")))
        val dv       = Schema[Example].toDynamicValue(original)
        val result   = Schema[Example].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on Link") {
        val original = Link(
          operationRefOrId = Some(Right("getUser")),
          extensions = ChunkMap("x-link-type" -> Json.String("internal"))
        )
        val dv     = Schema[Link].toDynamicValue(original)
        val result = Schema[Link].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on Callback") {
        val original = Callback(extensions = ChunkMap("x-callback-id" -> Json.String("cb-1")))
        val dv       = Schema[Callback].toDynamicValue(original)
        val result   = Schema[Callback].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on all SecurityScheme variants") {
        val exts = ChunkMap("x-custom" -> Json.String("value"))

        val apiKey: SecurityScheme = SecurityScheme.APIKey(name = "key", in = APIKeyLocation.Header, extensions = exts)
        val http: SecurityScheme   = SecurityScheme.HTTP(scheme = "bearer", extensions = exts)
        val oauth2: SecurityScheme = SecurityScheme.OAuth2(flows = OAuthFlows(), extensions = exts)
        val oidc: SecurityScheme   =
          SecurityScheme.OpenIdConnect(openIdConnectUrl = "https://example.com", extensions = exts)
        val mtls: SecurityScheme = SecurityScheme.MutualTLS(extensions = exts)

        val results = Chunk(apiKey, http, oauth2, oidc, mtls).map { scheme =>
          val dv = Schema[SecurityScheme].toDynamicValue(scheme)
          Schema[SecurityScheme].fromDynamicValue(dv)
        }

        assertTrue(results.forall(_.isRight))
      },
      test("extensions preserved on OAuthFlows") {
        val original = OAuthFlows(extensions = ChunkMap("x-oauth-provider" -> Json.String("custom")))
        val dv       = Schema[OAuthFlows].toDynamicValue(original)
        val result   = Schema[OAuthFlows].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on OAuthFlow") {
        val original = OAuthFlow(
          scopes = ChunkMap.empty[String, String],
          extensions = ChunkMap("x-flow-id" -> Json.String("flow-1"))
        )
        val dv     = Schema[OAuthFlow].toDynamicValue(original)
        val result = Schema[OAuthFlow].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on Components") {
        val original = Components(extensions = ChunkMap("x-components-version" -> Json.String("1.0")))
        val dv       = Schema[Components].toDynamicValue(original)
        val result   = Schema[Components].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on Paths") {
        val original = Paths(extensions = ChunkMap("x-paths-id" -> Json.String("paths-1")))
        val dv       = Schema[Paths].toDynamicValue(original)
        val result   = Schema[Paths].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on PathItem") {
        val original = PathItem(extensions = ChunkMap("x-path-version" -> Json.Number(1)))
        val dv       = Schema[PathItem].toDynamicValue(original)
        val result   = Schema[PathItem].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      },
      test("extensions preserved on OpenAPI") {
        val original = OpenAPI(
          openapi = "3.1.0",
          info = Info(title = "API", version = "1.0"),
          extensions = ChunkMap("x-api-id" -> Json.String("api-123"))
        )
        val dv     = Schema[OpenAPI].toDynamicValue(original)
        val result = Schema[OpenAPI].fromDynamicValue(dv)
        assertTrue(result.exists(_.extensions == original.extensions))
      }
    )
  )
}
