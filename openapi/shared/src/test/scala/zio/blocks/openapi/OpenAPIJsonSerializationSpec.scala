package zio.blocks.openapi

import zio.blocks.docs.{Doc, Parser}
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, JsonDecoder, JsonEncoder}
import zio.test._

object OpenAPIJsonSerializationSpec extends SchemaBaseSpec {
  import OpenAPICodec._

  private def doc(s: String): Doc = Parser.parse(s).toOption.get

  private def hasField(json: Json, key: String): Boolean =
    json.fields.exists(_._1 == key)

  private def fieldValue(json: Json, key: String): Option[Json] =
    json.fields.find(_._1 == key).map(_._2)

  def spec: Spec[TestEnvironment, Any] = suite("OpenAPI JSON Serialization")(
    schemaObjectToJsonSuite,
    enumEncodingSuite,
    referenceOrEncodingSuite,
    securitySchemeEncodingSuite,
    extensionsFlatteningSuite,
    wrapperTypesSuite,
    defaultOmissionSuite,
    roundTripSuite,
    petstoreRoundTripSuite,
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

  private lazy val enumEncodingSuite = suite("Enum types encode as spec-compliant strings")(
    test("ParameterLocation.Query encodes as 'query'") {
      val json = JsonEncoder[ParameterLocation].encode(ParameterLocation.Query)
      assertTrue(json == new Json.String("query"))
    },
    test("ParameterLocation.Header encodes as 'header'") {
      val json = JsonEncoder[ParameterLocation].encode(ParameterLocation.Header)
      assertTrue(json == new Json.String("header"))
    },
    test("ParameterLocation.Path encodes as 'path'") {
      val json = JsonEncoder[ParameterLocation].encode(ParameterLocation.Path)
      assertTrue(json == new Json.String("path"))
    },
    test("ParameterLocation.Cookie encodes as 'cookie'") {
      val json = JsonEncoder[ParameterLocation].encode(ParameterLocation.Cookie)
      assertTrue(json == new Json.String("cookie"))
    },
    test("APIKeyLocation.Query encodes as 'query'") {
      val json = JsonEncoder[APIKeyLocation].encode(APIKeyLocation.Query)
      assertTrue(json == new Json.String("query"))
    },
    test("APIKeyLocation.Header encodes as 'header'") {
      val json = JsonEncoder[APIKeyLocation].encode(APIKeyLocation.Header)
      assertTrue(json == new Json.String("header"))
    },
    test("APIKeyLocation.Cookie encodes as 'cookie'") {
      val json = JsonEncoder[APIKeyLocation].encode(APIKeyLocation.Cookie)
      assertTrue(json == new Json.String("cookie"))
    }
  )

  private lazy val referenceOrEncodingSuite = suite("ReferenceOr encodes as spec-compliant JSON")(
    test("ReferenceOr.Ref encodes as flat $ref object") {
      val ref: ReferenceOr[SchemaObject] = ReferenceOr.Ref(
        Reference(`$ref` = "#/components/schemas/Pet")
      )
      val json = JsonEncoder[ReferenceOr[SchemaObject]].encode(ref)
      assertTrue(
        fieldValue(json, "$ref").contains(new Json.String("#/components/schemas/Pet")),
        !hasField(json, "Ref"),
        !hasField(json, "reference")
      )
    },
    test("ReferenceOr.Value encodes as the value directly") {
      val value: ReferenceOr[SchemaObject] =
        ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("string"))))
      val json = JsonEncoder[ReferenceOr[SchemaObject]].encode(value)
      assertTrue(
        hasField(json, "type"),
        !hasField(json, "Value")
      )
    },
    test("ReferenceOr.Ref with summary and description") {
      val ref: ReferenceOr[SchemaObject] = ReferenceOr.Ref(
        Reference(
          `$ref` = "#/components/schemas/User",
          summary = Some(doc("User ref")),
          description = Some(doc("Reference to User schema"))
        )
      )
      val json = JsonEncoder[ReferenceOr[SchemaObject]].encode(ref)
      assertTrue(
        fieldValue(json, "$ref").contains(new Json.String("#/components/schemas/User")),
        hasField(json, "summary"),
        hasField(json, "description"),
        !hasField(json, "Ref")
      )
    }
  )

  private lazy val securitySchemeEncodingSuite = suite("SecurityScheme encodes with type discriminator")(
    test("SecurityScheme.APIKey encodes with type discriminator") {
      val ss: SecurityScheme = SecurityScheme.APIKey(name = "api_key", in = APIKeyLocation.Header)
      val json               = JsonEncoder[SecurityScheme].encode(ss)
      assertTrue(
        fieldValue(json, "type").contains(new Json.String("apiKey")),
        fieldValue(json, "name").contains(new Json.String("api_key")),
        fieldValue(json, "in").contains(new Json.String("header")),
        !hasField(json, "APIKey")
      )
    },
    test("SecurityScheme.HTTP encodes with type discriminator") {
      val ss: SecurityScheme = SecurityScheme.HTTP(scheme = "bearer", bearerFormat = Some("JWT"))
      val json               = JsonEncoder[SecurityScheme].encode(ss)
      assertTrue(
        fieldValue(json, "type").contains(new Json.String("http")),
        fieldValue(json, "scheme").contains(new Json.String("bearer")),
        fieldValue(json, "bearerFormat").contains(new Json.String("JWT")),
        !hasField(json, "HTTP")
      )
    },
    test("SecurityScheme.OAuth2 encodes with type discriminator") {
      val ss: SecurityScheme = SecurityScheme.OAuth2(
        flows = OAuthFlows(
          `implicit` = Some(
            OAuthFlow(
              authorizationUrl = Some("https://example.com/oauth/authorize"),
              scopes = Map("read" -> "Read access")
            )
          )
        )
      )
      val json = JsonEncoder[SecurityScheme].encode(ss)
      assertTrue(
        fieldValue(json, "type").contains(new Json.String("oauth2")),
        hasField(json, "flows"),
        !hasField(json, "OAuth2")
      )
    },
    test("SecurityScheme.OpenIdConnect encodes with type discriminator") {
      val ss: SecurityScheme = SecurityScheme.OpenIdConnect(
        openIdConnectUrl = "https://example.com/.well-known/openid-configuration"
      )
      val json = JsonEncoder[SecurityScheme].encode(ss)
      assertTrue(
        fieldValue(json, "type").contains(new Json.String("openIdConnect")),
        fieldValue(json, "openIdConnectUrl").contains(
          new Json.String("https://example.com/.well-known/openid-configuration")
        ),
        !hasField(json, "OpenIdConnect")
      )
    },
    test("SecurityScheme.MutualTLS encodes with type discriminator") {
      val ss: SecurityScheme = SecurityScheme.MutualTLS(description = Some(doc("Mutual TLS")))
      val json               = JsonEncoder[SecurityScheme].encode(ss)
      assertTrue(
        fieldValue(json, "type").contains(new Json.String("mutualTLS")),
        !hasField(json, "MutualTLS")
      )
    }
  )

  private lazy val extensionsFlatteningSuite = suite("Extensions are flattened at top level")(
    test("Info extensions are flattened at top level") {
      val info = Info(title = "API", version = "1.0", extensions = Map("x-custom" -> Json.String("val")))
      val json = JsonEncoder[Info].encode(info)
      assertTrue(
        hasField(json, "x-custom"),
        !hasField(json, "extensions")
      )
    },
    test("Contact extensions are flattened at top level") {
      val contact = Contact(name = Some("Support"), extensions = Map("x-id" -> Json.Number(1)))
      val json    = JsonEncoder[Contact].encode(contact)
      assertTrue(
        hasField(json, "x-id"),
        !hasField(json, "extensions")
      )
    },
    test("License extensions are flattened at top level") {
      val license = License(name = "MIT", extensions = Map("x-year" -> Json.Number(2024)))
      val json    = JsonEncoder[License].encode(license)
      assertTrue(
        hasField(json, "x-year"),
        !hasField(json, "extensions")
      )
    },
    test("Server extensions are flattened at top level") {
      val server = Server(url = "https://api.example.com", extensions = Map("x-region" -> Json.String("us")))
      val json   = JsonEncoder[Server].encode(server)
      assertTrue(
        hasField(json, "x-region"),
        !hasField(json, "extensions")
      )
    },
    test("Tag extensions are flattened at top level") {
      val tag  = Tag(name = "users", extensions = Map("x-order" -> Json.Number(1)))
      val json = JsonEncoder[Tag].encode(tag)
      assertTrue(
        hasField(json, "x-order"),
        !hasField(json, "extensions")
      )
    },
    test("ExternalDocumentation extensions are flattened") {
      val ed =
        ExternalDocumentation(url = "https://docs.example.com", extensions = Map("x-lang" -> Json.String("en")))
      val json = JsonEncoder[ExternalDocumentation].encode(ed)
      assertTrue(
        hasField(json, "x-lang"),
        !hasField(json, "extensions")
      )
    },
    test("Operation extensions are flattened at top level") {
      val op = Operation(
        responses = Map("200" -> ReferenceOr.Value(Response(description = doc("OK")))),
        extensions = Map("x-rate-limit" -> Json.Number(100))
      )
      val json = JsonEncoder[Operation].encode(op)
      assertTrue(
        hasField(json, "x-rate-limit"),
        !hasField(json, "extensions")
      )
    },
    test("Parameter extensions are flattened at top level") {
      val param = Parameter(
        name = "q",
        in = ParameterLocation.Query,
        extensions = Map("x-internal" -> Json.Boolean(true))
      )
      val json = JsonEncoder[Parameter].encode(param)
      assertTrue(
        hasField(json, "x-internal"),
        !hasField(json, "extensions")
      )
    },
    test("Response extensions are flattened at top level") {
      val resp = Response(description = doc("OK"), extensions = Map("x-response-id" -> Json.String("r1")))
      val json = JsonEncoder[Response].encode(resp)
      assertTrue(
        hasField(json, "x-response-id"),
        !hasField(json, "extensions")
      )
    },
    test("OpenAPI extensions are flattened at top level") {
      val api = OpenAPI(
        openapi = "3.1.0",
        info = Info(title = "API", version = "1.0"),
        extensions = Map("x-api-id" -> Json.String("api-123"))
      )
      val json = JsonEncoder[OpenAPI].encode(api)
      assertTrue(
        hasField(json, "x-api-id"),
        !hasField(json, "extensions")
      )
    }
  )

  private lazy val wrapperTypesSuite = suite("Wrapper types encode as flat maps")(
    test("SecurityRequirement encodes as flat map") {
      val sr   = SecurityRequirement(Map("oauth2" -> List("read", "write")))
      val json = JsonEncoder[SecurityRequirement].encode(sr)
      assertTrue(
        hasField(json, "oauth2"),
        !hasField(json, "requirements")
      )
    },
    test("Paths encodes with path keys at top level") {
      val paths =
        Paths(paths = Map("/pets" -> PathItem()), extensions = Map("x-id" -> Json.String("v1")))
      val json = JsonEncoder[Paths].encode(paths)
      assertTrue(
        hasField(json, "/pets"),
        hasField(json, "x-id"),
        !hasField(json, "paths")
      )
    },
    test("Callback encodes with callback keys at top level") {
      val cb = Callback(
        callbacks = Map(
          "{$request.body#/callbackUrl}" -> ReferenceOr.Value(PathItem(summary = Some(doc("Callback"))))
        ),
        extensions = Map("x-cb" -> Json.String("v1"))
      )
      val json = JsonEncoder[Callback].encode(cb)
      assertTrue(
        hasField(json, "{$request.body#/callbackUrl}"),
        hasField(json, "x-cb"),
        !hasField(json, "callbacks")
      )
    },
    test("Responses encodes with status code keys at top level") {
      val resps = Responses(
        responses = Map(
          "200" -> ReferenceOr.Value(Response(description = doc("OK"))),
          "404" -> ReferenceOr.Ref(Reference(`$ref` = "#/components/responses/NotFound"))
        ),
        default = Some(ReferenceOr.Value(Response(description = doc("Error")))),
        extensions = Map("x-resps" -> Json.String("v1"))
      )
      val json = JsonEncoder[Responses].encode(resps)
      assertTrue(
        hasField(json, "200"),
        hasField(json, "404"),
        hasField(json, "default"),
        hasField(json, "x-resps"),
        !hasField(json, "responses")
      )
    }
  )

  private lazy val defaultOmissionSuite = suite("Default values are omitted")(
    test("Parameter with default boolean values omits them") {
      val param = Parameter(name = "q", in = ParameterLocation.Query)
      val json  = JsonEncoder[Parameter].encode(param)
      assertTrue(
        hasField(json, "name"),
        hasField(json, "in"),
        !hasField(json, "required"),
        !hasField(json, "deprecated"),
        !hasField(json, "allowEmptyValue")
      )
    },
    test("Header with default boolean values omits them") {
      val header = Header()
      val json   = JsonEncoder[Header].encode(header)
      assertTrue(
        !hasField(json, "required"),
        !hasField(json, "deprecated"),
        !hasField(json, "allowEmptyValue")
      )
    },
    test("Operation with deprecated=false omits it") {
      val op   = Operation()
      val json = JsonEncoder[Operation].encode(op)
      assertTrue(
        !hasField(json, "deprecated")
      )
    },
    test("RequestBody with required=false omits it") {
      val rb   = RequestBody(content = Map.empty)
      val json = JsonEncoder[RequestBody].encode(rb)
      assertTrue(
        !hasField(json, "required")
      )
    },
    test("Encoding with allowReserved=false omits it") {
      val enc  = Encoding()
      val json = JsonEncoder[Encoding].encode(enc)
      assertTrue(
        !hasField(json, "allowReserved")
      )
    },
    test("XML with attribute=false and wrapped=false omits them") {
      val xml  = XML()
      val json = JsonEncoder[XML].encode(xml)
      assertTrue(
        !hasField(json, "attribute"),
        !hasField(json, "wrapped")
      )
    },
    test("Empty maps and lists are omitted") {
      val op   = Operation()
      val json = JsonEncoder[Operation].encode(op)
      assertTrue(
        !hasField(json, "tags"),
        !hasField(json, "parameters"),
        !hasField(json, "callbacks"),
        !hasField(json, "security"),
        !hasField(json, "servers"),
        !hasField(json, "responses")
      )
    }
  )

  private lazy val roundTripSuite = suite("Encode/decode round-trip")(
    test("Contact round-trips") {
      val original = Contact(name = Some("Support"), url = Some("https://example.com"), email = Some("a@b.com"))
      val json     = JsonEncoder[Contact].encode(original)
      val decoded  = JsonDecoder[Contact].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("License round-trips") {
      val original = License(name = "MIT", identifier = Some("MIT"), extensions = Map("x-y" -> Json.Number(1)))
      val json     = JsonEncoder[License].encode(original)
      val decoded  = JsonDecoder[License].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("Info round-trips") {
      val original = Info(
        title = "API",
        version = "1.0",
        summary = Some(doc("Summary")),
        description = Some(doc("Description")),
        termsOfService = Some("https://example.com/terms"),
        contact = Some(Contact(name = Some("Support"))),
        license = Some(License(name = "MIT")),
        extensions = Map("x-api" -> Json.String("v1"))
      )
      val json    = JsonEncoder[Info].encode(original)
      val decoded = JsonDecoder[Info].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("Server round-trips") {
      val original = Server(
        url = "https://api.example.com",
        description = Some(doc("Prod")),
        variables = Map("env" -> ServerVariable(default = "prod")),
        extensions = Map("x-r" -> Json.String("us"))
      )
      val json    = JsonEncoder[Server].encode(original)
      val decoded = JsonDecoder[Server].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("ServerVariable round-trips") {
      val original = ServerVariable(
        default = "v1",
        `enum` = List("v1", "v2"),
        description = Some(doc("Version")),
        extensions = Map("x-d" -> Json.Boolean(false))
      )
      val json    = JsonEncoder[ServerVariable].encode(original)
      val decoded = JsonDecoder[ServerVariable].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("Tag round-trips") {
      val original = Tag(
        name = "users",
        description = Some(doc("User ops")),
        externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com")),
        extensions = Map("x-o" -> Json.Number(1))
      )
      val json    = JsonEncoder[Tag].encode(original)
      val decoded = JsonDecoder[Tag].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("ExternalDocumentation round-trips") {
      val original = ExternalDocumentation(
        url = "https://docs.example.com",
        description = Some(doc("Full docs")),
        extensions = Map("x-l" -> Json.String("en"))
      )
      val json    = JsonEncoder[ExternalDocumentation].encode(original)
      val decoded = JsonDecoder[ExternalDocumentation].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("XML round-trips") {
      val original = XML(
        name = Some("item"),
        namespace = Some("http://example.com"),
        prefix = Some("ns"),
        attribute = true,
        wrapped = true
      )
      val json    = JsonEncoder[XML].encode(original)
      val decoded = JsonDecoder[XML].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("Discriminator round-trips") {
      val original = Discriminator(
        propertyName = "petType",
        mapping = Map("dog" -> "#/components/schemas/Dog")
      )
      val json    = JsonEncoder[Discriminator].encode(original)
      val decoded = JsonDecoder[Discriminator].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("Parameter round-trips") {
      val original = Parameter(
        name = "limit",
        in = ParameterLocation.Query,
        description = Some(doc("Page limit")),
        required = true,
        schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("integer"))))),
        extensions = Map("x-p" -> Json.String("val"))
      )
      val json    = JsonEncoder[Parameter].encode(original)
      val decoded = JsonDecoder[Parameter].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("Operation round-trips") {
      val original = Operation(
        responses = Map("200" -> ReferenceOr.Value(Response(description = doc("OK")))),
        tags = List("users"),
        summary = Some(doc("Get users")),
        operationId = Some("getUsers"),
        deprecated = true,
        extensions = Map("x-rl" -> Json.Number(100))
      )
      val json    = JsonEncoder[Operation].encode(original)
      val decoded = JsonDecoder[Operation].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("Response round-trips") {
      val original = Response(
        description = doc("Success"),
        headers = Map(
          "X-Rate" -> ReferenceOr.Value(
            Header(schema =
              Some(
                ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("integer"))))
              )
            )
          )
        ),
        content = Map(
          "application/json" -> MediaType(
            schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("object")))))
          )
        ),
        extensions = Map("x-rid" -> Json.String("r1"))
      )
      val json    = JsonEncoder[Response].encode(original)
      val decoded = JsonDecoder[Response].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("SecurityScheme.APIKey round-trips") {
      val original: SecurityScheme = SecurityScheme.APIKey(
        name = "api_key",
        in = APIKeyLocation.Header,
        description = Some(doc("API key auth")),
        extensions = Map("x-f" -> Json.String("uuid"))
      )
      val json    = JsonEncoder[SecurityScheme].encode(original)
      val decoded = JsonDecoder[SecurityScheme].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("SecurityScheme.HTTP round-trips") {
      val original: SecurityScheme = SecurityScheme.HTTP(
        scheme = "bearer",
        bearerFormat = Some("JWT"),
        description = Some(doc("Bearer auth")),
        extensions = Map("x-t" -> Json.String("jwt"))
      )
      val json    = JsonEncoder[SecurityScheme].encode(original)
      val decoded = JsonDecoder[SecurityScheme].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("SecurityScheme.OAuth2 round-trips") {
      val original: SecurityScheme = SecurityScheme.OAuth2(
        flows = OAuthFlows(
          `implicit` = Some(
            OAuthFlow(
              authorizationUrl = Some("https://example.com/auth"),
              scopes = Map("read" -> "Read access")
            )
          ),
          extensions = Map("x-fl" -> Json.String("all"))
        ),
        description = Some(doc("OAuth2")),
        extensions = Map("x-p" -> Json.String("custom"))
      )
      val json    = JsonEncoder[SecurityScheme].encode(original)
      val decoded = JsonDecoder[SecurityScheme].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("SecurityScheme.OpenIdConnect round-trips") {
      val original: SecurityScheme = SecurityScheme.OpenIdConnect(
        openIdConnectUrl = "https://example.com/.well-known/openid-configuration",
        description = Some(doc("OIDC")),
        extensions = Map("x-oidc" -> Json.String("custom"))
      )
      val json    = JsonEncoder[SecurityScheme].encode(original)
      val decoded = JsonDecoder[SecurityScheme].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("SecurityScheme.MutualTLS round-trips") {
      val original: SecurityScheme = SecurityScheme.MutualTLS(
        description = Some(doc("mTLS")),
        extensions = Map("x-cert" -> Json.Boolean(true))
      )
      val json    = JsonEncoder[SecurityScheme].encode(original)
      val decoded = JsonDecoder[SecurityScheme].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("ReferenceOr.Ref round-trips") {
      val original: ReferenceOr[SchemaObject] = ReferenceOr.Ref(
        Reference(`$ref` = "#/components/schemas/Pet")
      )
      val json    = JsonEncoder[ReferenceOr[SchemaObject]].encode(original)
      val decoded = JsonDecoder[ReferenceOr[SchemaObject]].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("ReferenceOr.Value round-trips") {
      val original: ReferenceOr[SchemaObject] =
        ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("string"))))
      val json    = JsonEncoder[ReferenceOr[SchemaObject]].encode(original)
      val decoded = JsonDecoder[ReferenceOr[SchemaObject]].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("PathItem round-trips") {
      val original = PathItem(
        summary = Some(doc("API endpoint")),
        description = Some(doc("Detailed description")),
        get = Some(
          Operation(responses = Map("200" -> ReferenceOr.Value(Response(description = doc("OK")))))
        ),
        servers = List(Server(url = "https://api.example.com")),
        parameters = List(
          ReferenceOr.Value(Parameter(name = "id", in = ParameterLocation.Path, required = true))
        ),
        extensions = Map("x-path" -> Json.Number(1))
      )
      val json    = JsonEncoder[PathItem].encode(original)
      val decoded = JsonDecoder[PathItem].decode(json)
      assertTrue(decoded == Right(original))
    },
    test("OpenAPI round-trips") {
      val original = OpenAPI(
        openapi = "3.1.0",
        info = Info(title = "Test API", version = "1.0.0"),
        servers = List(Server(url = "https://api.example.com")),
        paths = Some(
          Paths(paths = Map("/users" -> PathItem(summary = Some(doc("Users")))))
        ),
        components = Some(
          Components(
            schemas = Map(
              "User" -> ReferenceOr.Value(
                SchemaObject(jsonSchema = Json.Object("type" -> Json.String("object")))
              )
            )
          )
        ),
        security = List(SecurityRequirement(Map("bearerAuth" -> Nil))),
        tags = List(Tag(name = "users")),
        externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com")),
        extensions = Map("x-api-id" -> Json.String("api-123"))
      )
      val json    = JsonEncoder[OpenAPI].encode(original)
      val decoded = JsonDecoder[OpenAPI].decode(json)
      assertTrue(decoded == Right(original))
    }
  )

  private lazy val petstoreRoundTripSuite = suite("Petstore JSON round-trip")(
    test("petstore.json round-trips through decode/encode") {
      val jsonString = scala.io.Source.fromResource("openapi/petstore.json").mkString
      val parsed     = Json.parse(jsonString)
      assertTrue(parsed.isRight) &&
      (parsed match {
        case Right(json) =>
          val decoded = JsonDecoder[OpenAPI].decode(json)
          assertTrue(decoded.isRight) &&
          (decoded match {
            case Right(api) =>
              val reEncoded = JsonEncoder[OpenAPI].encode(api)
              assertTrue(
                fieldValue(reEncoded, "openapi").contains(new Json.String("3.1.0")),
                hasField(reEncoded, "info"),
                hasField(reEncoded, "paths"),
                hasField(reEncoded, "components")
              ) && {
                val infoJson = fieldValue(reEncoded, "info").get
                assertTrue(
                  fieldValue(infoJson, "title").contains(new Json.String("Swagger Petstore")),
                  fieldValue(infoJson, "version").contains(new Json.String("1.0.0"))
                )
              } && {
                val reDecoded = JsonDecoder[OpenAPI].decode(reEncoded)
                assertTrue(reDecoded == Right(api))
              }
            case Left(_) =>
              assertTrue(false)
          })
        case Left(_) =>
          assertTrue(false)
      })
    },
    test("minimal.json round-trips through decode/encode") {
      val jsonString = scala.io.Source.fromResource("openapi/minimal.json").mkString
      val parsed     = Json.parse(jsonString)
      assertTrue(parsed.isRight) &&
      (parsed match {
        case Right(json) =>
          val decoded = JsonDecoder[OpenAPI].decode(json)
          assertTrue(decoded.isRight) &&
          (decoded match {
            case Right(api) =>
              val reEncoded = JsonEncoder[OpenAPI].encode(api)
              val reDecoded = JsonDecoder[OpenAPI].decode(reEncoded)
              assertTrue(reDecoded == Right(api))
            case Left(_) =>
              assertTrue(false)
          })
        case Left(_) =>
          assertTrue(false)
      })
    },
    test("with-security.json round-trips through decode/encode") {
      val jsonString = scala.io.Source.fromResource("openapi/with-security.json").mkString
      val parsed     = Json.parse(jsonString)
      assertTrue(parsed.isRight) &&
      (parsed match {
        case Right(json) =>
          val decoded = JsonDecoder[OpenAPI].decode(json)
          assertTrue(decoded.isRight) &&
          (decoded match {
            case Right(api) =>
              val reEncoded = JsonEncoder[OpenAPI].encode(api)
              val reDecoded = JsonDecoder[OpenAPI].decode(reEncoded)
              assertTrue(reDecoded == Right(api))
            case Left(_) =>
              assertTrue(false)
          })
        case Left(_) =>
          assertTrue(false)
      })
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

      val json = JsonEncoder[OpenAPI].encode(api)
      assertTrue(
        hasField(json, "openapi"),
        hasField(json, "info"),
        hasField(json, "paths"),
        hasField(json, "components")
      )
    }
  )
}
