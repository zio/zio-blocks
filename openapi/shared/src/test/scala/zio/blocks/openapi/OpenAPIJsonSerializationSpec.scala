package zio.blocks.openapi

import zio.blocks.docs.{Doc, Parser}
import zio.blocks.schema._
import zio.blocks.schema.json.Json
import zio.test._

object OpenAPIJsonSerializationSpec extends SchemaBaseSpec {
  private def doc(s: String): Doc = Parser.parse(s).toOption.get

  private def hasField(json: Json, key: String): Boolean =
    json.fields.exists(_._1 == key)

  private def fieldValue(json: Json, key: String): Option[Json] =
    json.fields.find(_._1 == key).map(_._2)

  def spec: Spec[TestEnvironment, Any] = suite("OpenAPI JSON Serialization")(
    schemaObjectToJsonSuite,
    primitiveAndContainerTypesSuite,
    operationTypesSuite,
    responseTypesSuite,
    securityTypesSuite,
    containerTypesSuite,
    schemaToOpenAPIConversionSuite
  )

  private lazy val schemaObjectToJsonSuite = suite("SchemaObject.toJson fix")(
    test("toJson with only jsonSchema") {
      val so   = SchemaObject(jsonSchema = Json.Object("type" -> Json.String("string")))
      val json = so.toJson
      assertTrue(
        hasField(json, "type"),
        fieldValue(json, "type").contains(Json.String("string"))
      )
    },
    test("toJson with discriminator") {
      val so = SchemaObject(
        jsonSchema = Json.Object("type" -> Json.String("object")),
        discriminator = Some(Discriminator(propertyName = "kind", mapping = Map("a" -> "#/a")))
      )
      val json = so.toJson
      assertTrue(
        hasField(json, "type"),
        hasField(json, "discriminator")
      )
    },
    test("toJson with xml") {
      val so = SchemaObject(
        jsonSchema = Json.Object("type" -> Json.String("object")),
        xml = Some(XML(name = Some("root"), namespace = Some("http://example.com")))
      )
      val json = so.toJson
      assertTrue(
        hasField(json, "type"),
        hasField(json, "xml")
      )
    },
    test("toJson with externalDocs") {
      val so = SchemaObject(
        jsonSchema = Json.Object("type" -> Json.String("object")),
        externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com"))
      )
      val json = so.toJson
      assertTrue(
        hasField(json, "type"),
        hasField(json, "externalDocs")
      )
    },
    test("toJson with example") {
      val so = SchemaObject(
        jsonSchema = Json.Object("type" -> Json.String("string")),
        example = Some(Json.String("hello"))
      )
      val json = so.toJson
      assertTrue(
        hasField(json, "type"),
        hasField(json, "example"),
        fieldValue(json, "example").contains(Json.String("hello"))
      )
    },
    test("toJson with extensions") {
      val so = SchemaObject(
        jsonSchema = Json.Object("type" -> Json.String("object")),
        extensions = Map("x-custom" -> Json.String("val"), "x-num" -> Json.Number(42))
      )
      val json = so.toJson
      assertTrue(
        hasField(json, "type"),
        hasField(json, "x-custom"),
        hasField(json, "x-num"),
        fieldValue(json, "x-custom").contains(Json.String("val")),
        fieldValue(json, "x-num").contains(Json.Number(42))
      )
    },
    test("toJson with ALL fields populated") {
      val so = SchemaObject(
        jsonSchema = Json.Object("type" -> Json.String("object"), "title" -> Json.String("MyObj")),
        discriminator = Some(Discriminator(propertyName = "type", mapping = Map("a" -> "#/a"))),
        xml = Some(XML(name = Some("item"), wrapped = true)),
        externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com")),
        example = Some(Json.Object("id" -> Json.Number(1))),
        extensions = Map("x-ext" -> Json.Boolean(true))
      )
      val json = so.toJson
      assertTrue(
        hasField(json, "type"),
        hasField(json, "title"),
        hasField(json, "discriminator"),
        hasField(json, "xml"),
        hasField(json, "externalDocs"),
        hasField(json, "example"),
        hasField(json, "x-ext")
      )
    },
    test("toJson with boolean schema (true) returns true as-is") {
      val so   = SchemaObject(jsonSchema = Json.Boolean(true))
      val json = so.toJson
      assertTrue(json == Json.Boolean(true))
    }
  )

  private lazy val primitiveAndContainerTypesSuite = suite("Primitive/container types serialize all fields to JSON")(
    test("Contact serializes all fields") {
      val value = Contact(
        name = Some("Support"),
        url = Some("https://example.com"),
        email = Some("support@example.com"),
        extensions = Map("x-id" -> Json.Number(1))
      )
      val json = Schema[Contact].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "name"),
        hasField(json, "url"),
        hasField(json, "email"),
        hasField(json, "extensions")
      )
    },
    test("Info serializes all fields") {
      val value = Info(
        title = "Test API",
        version = "1.0.0",
        summary = Some(doc("A summary")),
        description = Some(doc("A description")),
        termsOfService = Some("https://example.com/terms"),
        contact = Some(Contact(name = Some("Support"))),
        license = Some(License(name = "MIT", identifier = Some("MIT"))),
        extensions = Map("x-api" -> Json.String("v1"))
      )
      val json = Schema[Info].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "title"),
        hasField(json, "version"),
        hasField(json, "summary"),
        hasField(json, "description"),
        hasField(json, "termsOfService"),
        hasField(json, "contact"),
        hasField(json, "license"),
        hasField(json, "extensions")
      )
    },
    test("License serializes all fields (with identifier)") {
      val value = License(
        name = "Apache 2.0",
        identifier = Some("Apache-2.0"),
        extensions = Map("x-year" -> Json.Number(2024))
      )
      val json = Schema[License].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "name"),
        hasField(json, "identifier"),
        hasField(json, "extensions")
      )
    },
    test("License serializes all fields (with url)") {
      val value = License(
        name = "Custom",
        url = Some("https://example.com/license"),
        extensions = Map("x-id" -> Json.String("lic"))
      )
      val json = Schema[License].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "name"),
        hasField(json, "url"),
        hasField(json, "extensions")
      )
    },
    test("Server serializes all fields") {
      val value = Server(
        url = "https://api.example.com",
        description = Some(doc("Production")),
        variables = Map("env" -> ServerVariable(default = "prod")),
        extensions = Map("x-region" -> Json.String("us"))
      )
      val json = Schema[Server].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "url"),
        hasField(json, "description"),
        hasField(json, "variables"),
        hasField(json, "extensions")
      )
    },
    test("ServerVariable serializes all fields") {
      val value = ServerVariable(
        default = "v1",
        `enum` = List("v1", "v2"),
        description = Some(doc("API version")),
        extensions = Map("x-deprecated" -> Json.Boolean(false))
      )
      val json = Schema[ServerVariable].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "default"),
        hasField(json, "enum"),
        hasField(json, "description"),
        hasField(json, "extensions")
      )
    },
    test("Tag serializes all fields") {
      val value = Tag(
        name = "users",
        description = Some(doc("User operations")),
        externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com/users")),
        extensions = Map("x-order" -> Json.Number(1))
      )
      val json = Schema[Tag].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "name"),
        hasField(json, "description"),
        hasField(json, "externalDocs"),
        hasField(json, "extensions")
      )
    },
    test("ExternalDocumentation serializes all fields") {
      val value = ExternalDocumentation(
        url = "https://docs.example.com",
        description = Some(doc("Full docs")),
        extensions = Map("x-lang" -> Json.String("en"))
      )
      val json = Schema[ExternalDocumentation].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "url"),
        hasField(json, "description"),
        hasField(json, "extensions")
      )
    },
    test("XML serializes all fields") {
      val value = XML(
        name = Some("item"),
        namespace = Some("http://example.com"),
        prefix = Some("ns"),
        attribute = true,
        wrapped = true
      )
      val json = Schema[XML].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "name"),
        hasField(json, "namespace"),
        hasField(json, "prefix"),
        hasField(json, "attribute"),
        hasField(json, "wrapped")
      )
    },
    test("SchemaObject serializes all fields") {
      val value = SchemaObject(
        jsonSchema = Json.Object("type" -> Json.String("object")),
        discriminator = Some(Discriminator(propertyName = "type")),
        xml = Some(XML(name = Some("root"))),
        externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com")),
        example = Some(Json.String("ex")),
        extensions = Map("x-schema-id" -> Json.String("s1"))
      )
      val json = Schema[SchemaObject].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "jsonSchema"),
        hasField(json, "discriminator"),
        hasField(json, "xml"),
        hasField(json, "externalDocs"),
        hasField(json, "example"),
        hasField(json, "extensions")
      )
    },
    test("Discriminator serializes all fields") {
      val value = Discriminator(
        propertyName = "petType",
        mapping = Map("dog" -> "#/components/schemas/Dog", "cat" -> "#/components/schemas/Cat")
      )
      val json = Schema[Discriminator].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "propertyName"),
        hasField(json, "mapping")
      )
    },
    test("SecurityRequirement serializes all fields") {
      val value = SecurityRequirement(
        requirements = Map("oauth2" -> List("read", "write"), "api_key" -> Nil)
      )
      val json = Schema[SecurityRequirement].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "requirements")
      )
    },
    test("ParameterLocation.Query serializes to JSON") {
      val json = Schema[ParameterLocation].toDynamicValue(ParameterLocation.Query).toJson
      assertTrue(json != Json.Null)
    },
    test("ParameterLocation.Header serializes to JSON") {
      val json = Schema[ParameterLocation].toDynamicValue(ParameterLocation.Header).toJson
      assertTrue(json != Json.Null)
    },
    test("ParameterLocation.Path serializes to JSON") {
      val json = Schema[ParameterLocation].toDynamicValue(ParameterLocation.Path).toJson
      assertTrue(json != Json.Null)
    },
    test("ParameterLocation.Cookie serializes to JSON") {
      val json = Schema[ParameterLocation].toDynamicValue(ParameterLocation.Cookie).toJson
      assertTrue(json != Json.Null)
    },
    test("APIKeyLocation.Query serializes to JSON") {
      val json = Schema[APIKeyLocation].toDynamicValue(APIKeyLocation.Query).toJson
      assertTrue(json != Json.Null)
    },
    test("APIKeyLocation.Header serializes to JSON") {
      val json = Schema[APIKeyLocation].toDynamicValue(APIKeyLocation.Header).toJson
      assertTrue(json != Json.Null)
    },
    test("APIKeyLocation.Cookie serializes to JSON") {
      val json = Schema[APIKeyLocation].toDynamicValue(APIKeyLocation.Cookie).toJson
      assertTrue(json != Json.Null)
    }
  )

  private lazy val operationTypesSuite = suite("Operation types serialize all fields to JSON")(
    test("Operation serializes all fields") {
      val value = Operation(
        responses = Map("200" -> ReferenceOr.Value(Response(description = doc("OK")))),
        tags = List("users", "admin"),
        summary = Some(doc("Get users")),
        description = Some(doc("Retrieves all users")),
        externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com/users")),
        operationId = Some("getUsers"),
        parameters = List(
          ReferenceOr.Value(Parameter(name = "limit", in = ParameterLocation.Query))
        ),
        requestBody = Some(
          ReferenceOr.Value(RequestBody(content = Map("application/json" -> MediaType()), required = true))
        ),
        callbacks = Map("onData" -> ReferenceOr.Value(Callback())),
        deprecated = true,
        security = List(SecurityRequirement(Map("api_key" -> List("read")))),
        servers = List(Server(url = "https://api.example.com")),
        extensions = Map("x-rate-limit" -> Json.Number(100))
      )
      val json = Schema[Operation].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "responses"),
        hasField(json, "tags"),
        hasField(json, "summary"),
        hasField(json, "description"),
        hasField(json, "externalDocs"),
        hasField(json, "operationId"),
        hasField(json, "parameters"),
        hasField(json, "requestBody"),
        hasField(json, "callbacks"),
        hasField(json, "deprecated"),
        hasField(json, "security"),
        hasField(json, "servers"),
        hasField(json, "extensions")
      )
    },
    test("Parameter serializes all fields") {
      val value = Parameter(
        name = "limit",
        in = ParameterLocation.Query,
        description = Some(doc("Page limit")),
        required = false,
        deprecated = true,
        allowEmptyValue = true,
        style = Some("form"),
        explode = Some(true),
        allowReserved = Some(false),
        schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("integer"))))),
        example = Some(Json.Number(10)),
        examples = Map("default" -> ReferenceOr.Value(Example(value = Some(Json.Number(10))))),
        content = Map("application/json" -> MediaType()),
        extensions = Map("x-param" -> Json.String("val"))
      )
      val json = Schema[Parameter].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "name"),
        hasField(json, "in"),
        hasField(json, "description"),
        hasField(json, "required"),
        hasField(json, "deprecated"),
        hasField(json, "allowEmptyValue"),
        hasField(json, "style"),
        hasField(json, "explode"),
        hasField(json, "allowReserved"),
        hasField(json, "schema"),
        hasField(json, "example"),
        hasField(json, "examples"),
        hasField(json, "content"),
        hasField(json, "extensions")
      )
    },
    test("Header serializes all fields") {
      val value = Header(
        description = Some(doc("Auth header")),
        required = true,
        deprecated = false,
        allowEmptyValue = true,
        style = Some("simple"),
        explode = Some(false),
        allowReserved = Some(true),
        schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("string"))))),
        example = Some(Json.String("Bearer token")),
        examples = Map("auth" -> ReferenceOr.Value(Example(value = Some(Json.String("Bearer xyz"))))),
        content = Map("text/plain" -> MediaType()),
        extensions = Map("x-header-id" -> Json.String("h1"))
      )
      val json = Schema[Header].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "description"),
        hasField(json, "required"),
        hasField(json, "deprecated"),
        hasField(json, "allowEmptyValue"),
        hasField(json, "style"),
        hasField(json, "explode"),
        hasField(json, "allowReserved"),
        hasField(json, "schema"),
        hasField(json, "example"),
        hasField(json, "examples"),
        hasField(json, "content"),
        hasField(json, "extensions")
      )
    },
    test("RequestBody serializes all fields") {
      val value = RequestBody(
        content = Map(
          "application/json" -> MediaType(schema =
            Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("object")))))
          )
        ),
        description = Some(doc("Request body")),
        required = true,
        extensions = Map("x-body" -> Json.String("custom"))
      )
      val json = Schema[RequestBody].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "content"),
        hasField(json, "description"),
        hasField(json, "required"),
        hasField(json, "extensions")
      )
    },
    test("MediaType serializes all fields") {
      val value = MediaType(
        schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("string"))))),
        example = Some(Json.String("example value")),
        examples = Map("ex1" -> ReferenceOr.Value(Example(value = Some(Json.String("v1"))))),
        encoding = Map("field1" -> Encoding(contentType = Some("application/json"))),
        extensions = Map("x-media" -> Json.String("custom"))
      )
      val json = Schema[MediaType].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "schema"),
        hasField(json, "example"),
        hasField(json, "examples"),
        hasField(json, "encoding"),
        hasField(json, "extensions")
      )
    },
    test("Encoding serializes all fields") {
      val value = Encoding(
        contentType = Some("application/json"),
        headers = Map("X-Custom" -> ReferenceOr.Value(Header(description = Some(doc("Custom"))))),
        style = Some("form"),
        explode = Some(true),
        allowReserved = true,
        extensions = Map("x-enc" -> Json.Number(1))
      )
      val json = Schema[Encoding].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "contentType"),
        hasField(json, "headers"),
        hasField(json, "style"),
        hasField(json, "explode"),
        hasField(json, "allowReserved"),
        hasField(json, "extensions")
      )
    }
  )

  private lazy val responseTypesSuite = suite("Response types serialize all fields to JSON")(
    test("Responses serializes all fields") {
      val value = Responses(
        responses = Map(
          "200" -> ReferenceOr.Value(Response(description = doc("Success"))),
          "404" -> ReferenceOr.Ref(Reference(`$ref` = "#/components/responses/NotFound"))
        ),
        default = Some(ReferenceOr.Value(Response(description = doc("Error")))),
        extensions = Map("x-responses" -> Json.String("custom"))
      )
      val json = Schema[Responses].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "responses"),
        hasField(json, "default"),
        hasField(json, "extensions")
      )
    },
    test("Response serializes all fields") {
      val value = Response(
        description = doc("Successful operation"),
        headers = Map(
          "X-Rate-Limit" -> ReferenceOr.Value(
            Header(schema =
              Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("integer")))))
            )
          )
        ),
        content = Map(
          "application/json" -> MediaType(
            schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("object")))))
          )
        ),
        links = Map(
          "next" -> ReferenceOr.Value(Link(operationId = Some("getNext")))
        ),
        extensions = Map("x-response-id" -> Json.String("resp-1"))
      )
      val json = Schema[Response].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "description"),
        hasField(json, "headers"),
        hasField(json, "content"),
        hasField(json, "links"),
        hasField(json, "extensions")
      )
    },
    test("Example serializes all fields (with value)") {
      val value = Example(
        summary = Some(doc("User example")),
        description = Some(doc("Example of a user object")),
        value = Some(Json.Object("name" -> Json.String("John"))),
        extensions = Map("x-source" -> Json.String("manual"))
      )
      val json = Schema[Example].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "summary"),
        hasField(json, "description"),
        hasField(json, "value"),
        hasField(json, "extensions")
      )
    },
    test("Example serializes all fields (with externalValue)") {
      val value = Example(
        summary = Some(doc("External example")),
        description = Some(doc("Points to external file")),
        externalValue = Some("https://example.com/examples/user.json"),
        extensions = Map("x-external" -> Json.Boolean(true))
      )
      val json = Schema[Example].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "summary"),
        hasField(json, "description"),
        hasField(json, "externalValue"),
        hasField(json, "extensions")
      )
    },
    test("Link serializes all fields (with operationRef)") {
      val value = Link(
        operationRef = Some("#/paths/~1users~1{id}/get"),
        parameters = Map("id" -> Json.String("$response.body#/id")),
        requestBody = Some(Json.Object("data" -> Json.String("value"))),
        description = Some(doc("Link to get user")),
        server = Some(Server(url = "https://api.example.com")),
        extensions = Map("x-link-id" -> Json.String("link-1"))
      )
      val json = Schema[Link].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "operationRef"),
        hasField(json, "parameters"),
        hasField(json, "requestBody"),
        hasField(json, "description"),
        hasField(json, "server"),
        hasField(json, "extensions")
      )
    },
    test("Link serializes all fields (with operationId)") {
      val value = Link(
        operationId = Some("getUserById"),
        parameters = Map("id" -> Json.Number(123)),
        description = Some(doc("Link by operation ID")),
        extensions = Map("x-link-type" -> Json.String("opId"))
      )
      val json = Schema[Link].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "operationId"),
        hasField(json, "parameters"),
        hasField(json, "description"),
        hasField(json, "extensions")
      )
    },
    test("Callback serializes all fields") {
      val value = Callback(
        callbacks = Map(
          "{$request.body#/callbackUrl}" -> ReferenceOr.Value(
            PathItem(summary = Some(doc("Callback path")))
          )
        ),
        extensions = Map("x-callback-id" -> Json.String("cb-1"))
      )
      val json = Schema[Callback].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "callbacks"),
        hasField(json, "extensions")
      )
    }
  )

  private lazy val securityTypesSuite = suite("Security types serialize all fields to JSON")(
    test("SecurityScheme.APIKey serializes all fields") {
      val value: SecurityScheme = SecurityScheme.APIKey(
        name = "api_key",
        in = APIKeyLocation.Header,
        description = Some(doc("API key authentication")),
        extensions = Map("x-format" -> Json.String("uuid"))
      )
      val json = Schema[SecurityScheme].toDynamicValue(value).toJson
      assertTrue(json != Json.Null)
    },
    test("SecurityScheme.HTTP serializes all fields") {
      val value: SecurityScheme = SecurityScheme.HTTP(
        scheme = "bearer",
        bearerFormat = Some("JWT"),
        description = Some(doc("Bearer token auth")),
        extensions = Map("x-token" -> Json.String("jwt"))
      )
      val json = Schema[SecurityScheme].toDynamicValue(value).toJson
      assertTrue(json != Json.Null)
    },
    test("SecurityScheme.OAuth2 serializes all fields") {
      val value: SecurityScheme = SecurityScheme.OAuth2(
        flows = OAuthFlows(
          `implicit` = Some(
            OAuthFlow(
              authorizationUrl = Some("https://example.com/oauth/authorize"),
              scopes = Map("read" -> "Read access")
            )
          ),
          extensions = Map("x-flows" -> Json.String("all"))
        ),
        description = Some(doc("OAuth2 security")),
        extensions = Map("x-provider" -> Json.String("custom"))
      )
      val json = Schema[SecurityScheme].toDynamicValue(value).toJson
      assertTrue(json != Json.Null)
    },
    test("SecurityScheme.OpenIdConnect serializes all fields") {
      val value: SecurityScheme = SecurityScheme.OpenIdConnect(
        openIdConnectUrl = "https://example.com/.well-known/openid-configuration",
        description = Some(doc("OpenID Connect")),
        extensions = Map("x-oidc" -> Json.String("custom"))
      )
      val json = Schema[SecurityScheme].toDynamicValue(value).toJson
      assertTrue(json != Json.Null)
    },
    test("SecurityScheme.MutualTLS serializes all fields") {
      val value: SecurityScheme = SecurityScheme.MutualTLS(
        description = Some(doc("Mutual TLS")),
        extensions = Map("x-cert" -> Json.Boolean(true))
      )
      val json = Schema[SecurityScheme].toDynamicValue(value).toJson
      assertTrue(json != Json.Null)
    },
    test("OAuthFlows serializes all fields") {
      val value = OAuthFlows(
        `implicit` = Some(
          OAuthFlow(
            authorizationUrl = Some("https://example.com/auth"),
            scopes = Map("read" -> "Read")
          )
        ),
        password = Some(
          OAuthFlow(
            tokenUrl = Some("https://example.com/token"),
            scopes = Map("write" -> "Write")
          )
        ),
        clientCredentials = Some(
          OAuthFlow(
            tokenUrl = Some("https://example.com/token"),
            scopes = Map.empty
          )
        ),
        authorizationCode = Some(
          OAuthFlow(
            authorizationUrl = Some("https://example.com/auth"),
            tokenUrl = Some("https://example.com/token"),
            refreshUrl = Some("https://example.com/refresh"),
            scopes = Map("admin" -> "Admin")
          )
        ),
        extensions = Map("x-oauth" -> Json.Boolean(true))
      )
      val json = Schema[OAuthFlows].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "implicit"),
        hasField(json, "password"),
        hasField(json, "clientCredentials"),
        hasField(json, "authorizationCode"),
        hasField(json, "extensions")
      )
    },
    test("OAuthFlow serializes all fields") {
      val value = OAuthFlow(
        authorizationUrl = Some("https://example.com/authorize"),
        tokenUrl = Some("https://example.com/token"),
        refreshUrl = Some("https://example.com/refresh"),
        scopes = Map("read" -> "Read access", "write" -> "Write access"),
        extensions = Map("x-flow-id" -> Json.String("flow-1"))
      )
      val json = Schema[OAuthFlow].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "authorizationUrl"),
        hasField(json, "tokenUrl"),
        hasField(json, "refreshUrl"),
        hasField(json, "scopes"),
        hasField(json, "extensions")
      )
    }
  )

  private lazy val containerTypesSuite = suite("Container types serialize all fields to JSON")(
    test("Reference serializes all fields") {
      val value = Reference(
        `$ref` = "#/components/schemas/User",
        summary = Some(doc("User reference")),
        description = Some(doc("Reference to User schema"))
      )
      val json = Schema[Reference].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "$ref"),
        hasField(json, "summary"),
        hasField(json, "description")
      )
    },
    test("ReferenceOr.Ref serializes reference field") {
      val value: ReferenceOr[String] = ReferenceOr.Ref(
        Reference(`$ref` = "#/components/schemas/Pet")
      )
      val json = Schema[ReferenceOr[String]].toDynamicValue(value).toJson
      assertTrue(json != Json.Null)
    },
    test("ReferenceOr.Value serializes value field") {
      val value: ReferenceOr[String] = ReferenceOr.Value("actual value")
      val json                       = Schema[ReferenceOr[String]].toDynamicValue(value).toJson
      assertTrue(json != Json.Null)
    },
    test("Paths serializes all fields") {
      val value = Paths(
        paths = Map(
          "/users" -> PathItem(summary = Some(doc("Users endpoint"))),
          "/posts" -> PathItem(summary = Some(doc("Posts endpoint")))
        ),
        extensions = Map("x-paths" -> Json.String("v1"))
      )
      val json = Schema[Paths].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "paths"),
        hasField(json, "extensions")
      )
    },
    test("PathItem serializes all fields") {
      val minimalOp = Operation(
        responses = Map("200" -> ReferenceOr.Value(Response(description = doc("OK"))))
      )
      val value = PathItem(
        summary = Some(doc("API endpoint")),
        description = Some(doc("Detailed description")),
        get = Some(minimalOp),
        put = Some(minimalOp),
        post = Some(minimalOp),
        delete = Some(minimalOp),
        options = Some(minimalOp),
        head = Some(minimalOp),
        patch = Some(minimalOp),
        trace = Some(minimalOp),
        servers = List(Server(url = "https://api.example.com")),
        parameters = List(ReferenceOr.Value(Parameter(name = "id", in = ParameterLocation.Path, required = true))),
        extensions = Map("x-path" -> Json.Number(1))
      )
      val json = Schema[PathItem].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "summary"),
        hasField(json, "description"),
        hasField(json, "get"),
        hasField(json, "put"),
        hasField(json, "post"),
        hasField(json, "delete"),
        hasField(json, "options"),
        hasField(json, "head"),
        hasField(json, "patch"),
        hasField(json, "trace"),
        hasField(json, "servers"),
        hasField(json, "parameters"),
        hasField(json, "extensions")
      )
    },
    test("Components serializes all fields") {
      val value = Components(
        schemas =
          Map("User" -> ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("object"))))),
        responses = Map("NotFound" -> ReferenceOr.Value(Response(description = doc("Not found")))),
        parameters = Map("limit" -> ReferenceOr.Value(Parameter(name = "limit", in = ParameterLocation.Query))),
        examples = Map("user" -> ReferenceOr.Value(Example(value = Some(Json.Object("name" -> Json.String("John")))))),
        requestBodies = Map(
          "UserBody" -> ReferenceOr.Value(
            RequestBody(content = Map("application/json" -> MediaType()), required = true)
          )
        ),
        headers = Map("X-Custom" -> ReferenceOr.Value(Header())),
        securitySchemes =
          Map("api_key" -> ReferenceOr.Value(SecurityScheme.APIKey(name = "api_key", in = APIKeyLocation.Header))),
        links = Map("next" -> ReferenceOr.Value(Link(operationId = Some("getNext")))),
        callbacks = Map("onEvent" -> ReferenceOr.Value(Callback())),
        pathItems = Map("/users" -> ReferenceOr.Value(PathItem(summary = Some(doc("Users"))))),
        extensions = Map("x-components" -> Json.String("custom"))
      )
      val json = Schema[Components].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "schemas"),
        hasField(json, "responses"),
        hasField(json, "parameters"),
        hasField(json, "examples"),
        hasField(json, "requestBodies"),
        hasField(json, "headers"),
        hasField(json, "securitySchemes"),
        hasField(json, "links"),
        hasField(json, "callbacks"),
        hasField(json, "pathItems"),
        hasField(json, "extensions")
      )
    },
    test("OpenAPI serializes all fields") {
      val value = OpenAPI(
        openapi = "3.1.0",
        info = Info(title = "Comprehensive API", version = "2.0.0"),
        jsonSchemaDialect = Some("https://spec.openapis.org/oas/3.1/dialect/base"),
        servers = List(
          Server(url = "https://api.example.com/v1", description = Some(doc("Production")))
        ),
        paths = Some(
          Paths(
            paths = Map("/users" -> PathItem(summary = Some(doc("Users"))))
          )
        ),
        components = Some(
          Components(
            schemas =
              Map("User" -> ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("object")))))
          )
        ),
        security = List(SecurityRequirement(Map("bearerAuth" -> Nil))),
        tags = List(Tag(name = "users", description = Some(doc("User operations")))),
        externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com")),
        extensions = Map("x-api-id" -> Json.String("api-123"))
      )
      val json = Schema[OpenAPI].toDynamicValue(value).toJson
      assertTrue(
        hasField(json, "openapi"),
        hasField(json, "info"),
        hasField(json, "jsonSchemaDialect"),
        hasField(json, "servers"),
        hasField(json, "paths"),
        hasField(json, "components"),
        hasField(json, "security"),
        hasField(json, "tags"),
        hasField(json, "externalDocs"),
        hasField(json, "extensions")
      )
    }
  )

  private lazy val schemaToOpenAPIConversionSuite = suite("Schema-to-OpenAPI conversion API")(
    test("schemaName returns simple type name") {
      val name = Schema[String].schemaName
      assertTrue(name == "String")
    },
    test("toInlineSchema returns ReferenceOr.Value") {
      val inline = Schema[Int].toInlineSchema
      inline match {
        case ReferenceOr.Value(so) =>
          assertTrue(so.toJsonSchema.isRight)
        case _ =>
          assertTrue(false)
      }
    },
    test("toRefSchema returns correct $ref and definition") {
      case class User(name: String, age: Int)
      object User { implicit val schema: Schema[User] = Schema.derived }

      val (ref, (defName, defSchema)) = Schema[User].toRefSchema
      ref match {
        case ReferenceOr.Ref(reference) =>
          assertTrue(
            reference.`$ref` == "#/components/schemas/User",
            defName == "User",
            defSchema.toJsonSchema.isRight
          )
        case _ =>
          assertTrue(false)
      }
    },
    test("toRefSchema with custom name") {
      case class Item(id: Int)
      object Item { implicit val schema: Schema[Item] = Schema.derived }

      val (ref, (defName, _)) = Schema[Item].toRefSchema("CustomItem")
      ref match {
        case ReferenceOr.Ref(reference) =>
          assertTrue(
            reference.`$ref` == "#/components/schemas/CustomItem",
            defName == "CustomItem"
          )
        case _ =>
          assertTrue(false)
      }
    },
    test("OpenAPIGen.schema returns ref and definition map") {
      case class Product(name: String, price: Double)
      object Product { implicit val schema: Schema[Product] = Schema.derived }

      val (ref, defs) = OpenAPIGen.schema[Product]
      ref match {
        case ReferenceOr.Ref(reference) =>
          assertTrue(
            reference.`$ref` == "#/components/schemas/Product",
            defs.contains("Product"),
            defs("Product").toJsonSchema.isRight
          )
        case _ =>
          assertTrue(false)
      }
    },
    test("OpenAPIGen.schemas collects multiple schemas") {
      case class Order(id: Int)
      case class Customer(name: String)
      object Order    { implicit val schema: Schema[Order] = Schema.derived    }
      object Customer { implicit val schema: Schema[Customer] = Schema.derived }

      val result = OpenAPIGen.schemas(Schema[Order], Schema[Customer])
      assertTrue(
        result.contains("Order"),
        result.contains("Customer"),
        result("Order").toJsonSchema.isRight,
        result("Customer").toJsonSchema.isRight
      )
    },
    test("full OpenAPI document with inline and ref schemas") {
      case class Pet(name: String, tag: Option[String])
      object Pet { implicit val schema: Schema[Pet] = Schema.derived }

      val (petRef, petDefs)  = OpenAPIGen.schema[Pet]
      val inlineStringSchema = Schema[String].toInlineSchema

      val api = OpenAPI(
        openapi = "3.1.0",
        info = Info(title = "Pet Store", version = "1.0.0"),
        paths = Some(
          Paths(
            paths = Map(
              "/pets" -> PathItem(
                get = Some(
                  Operation(
                    responses = Map(
                      "200" -> ReferenceOr.Value(
                        Response(
                          description = doc("List of pets"),
                          content = Map("application/json" -> MediaType(schema = Some(petRef)))
                        )
                      )
                    ),
                    parameters = List(
                      ReferenceOr.Value(
                        Parameter(
                          name = "name",
                          in = ParameterLocation.Query,
                          schema = Some(inlineStringSchema)
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        ),
        components = Some(
          Components(
            schemas = petDefs.map { case (k, v) => k -> ReferenceOr.Value(v) }
          )
        )
      )

      val json = Schema[OpenAPI].toDynamicValue(api).toJson
      assertTrue(
        hasField(json, "openapi"),
        hasField(json, "info"),
        hasField(json, "paths"),
        hasField(json, "components")
      )
    }
  )
}
