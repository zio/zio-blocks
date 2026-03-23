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
import zio.blocks.docs.{Doc, Inline, Paragraph}
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, JsonCodec}
import zio.test._

object OpenAPIJsonSerializationSpec extends SchemaBaseSpec {
  import OpenAPICodec._

  private def doc(s: String): Doc = Doc(Chunk.single(Paragraph(Chunk.single(Inline.Text(s)))))

  private def hasField(json: Json, key: String): Boolean =
    json.fields.exists(_._1 == key)

  private def fieldValue(json: Json, key: String): Option[Json] =
    json.fields.collectFirst { case kv if kv._1 == key => kv._2 }

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
    schemaToOpenAPIConversionSuite,
    decoderFromRawJsonSuite,
    errorCasesSuite,
    securitySchemeVariantsSuite,
    responsesAndSchemaEdgeCasesSuite,
    webhooksSuite
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
        discriminator = Some(Discriminator(propertyName = "kind", mapping = ChunkMap("a" -> "#/a")))
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
        extensions = ChunkMap("x-custom" -> Json.String("val"), "x-num" -> Json.Number(42))
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
        discriminator = Some(Discriminator(propertyName = "type", mapping = ChunkMap("a" -> "#/a"))),
        xml = Some(XML(name = Some("item"), wrapped = true)),
        externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com")),
        example = Some(Json.Object("id" -> Json.Number(1))),
        extensions = ChunkMap("x-ext" -> Json.Boolean(true))
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
      val json = parameterLocationCodec.encodeValue(ParameterLocation.Query)
      assertTrue(json == Json.String("query"))
    },
    test("ParameterLocation.Header encodes as 'header'") {
      val json = parameterLocationCodec.encodeValue(ParameterLocation.Header)
      assertTrue(json == Json.String("header"))
    },
    test("ParameterLocation.Path encodes as 'path'") {
      val json = parameterLocationCodec.encodeValue(ParameterLocation.Path)
      assertTrue(json == Json.String("path"))
    },
    test("ParameterLocation.Cookie encodes as 'cookie'") {
      val json = parameterLocationCodec.encodeValue(ParameterLocation.Cookie)
      assertTrue(json == Json.String("cookie"))
    },
    test("APIKeyLocation.Query encodes as 'query'") {
      val json = apiKeyLocationCodec.encodeValue(APIKeyLocation.Query)
      assertTrue(json == Json.String("query"))
    },
    test("APIKeyLocation.Header encodes as 'header'") {
      val json = apiKeyLocationCodec.encodeValue(APIKeyLocation.Header)
      assertTrue(json == Json.String("header"))
    },
    test("APIKeyLocation.Cookie encodes as 'cookie'") {
      val json = apiKeyLocationCodec.encodeValue(APIKeyLocation.Cookie)
      assertTrue(json == Json.String("cookie"))
    }
  )

  private lazy val referenceOrEncodingSuite = suite("ReferenceOr encodes as spec-compliant JSON")(
    test("ReferenceOr.Ref encodes as flat $ref object") {
      val ref: ReferenceOr[SchemaObject] = ReferenceOr.Ref(
        Reference(`$ref` = "#/components/schemas/Pet")
      )
      val json = referenceOrCodec(schemaObjectCodec).encodeValue(ref)
      assertTrue(
        fieldValue(json, "$ref").contains(Json.String("#/components/schemas/Pet")),
        !hasField(json, "Ref"),
        !hasField(json, "reference")
      )
    },
    test("ReferenceOr.Value encodes as the value directly") {
      val value: ReferenceOr[SchemaObject] =
        ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("string"))))
      val json = referenceOrCodec(schemaObjectCodec).encodeValue(value)
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
      val json = referenceOrCodec(schemaObjectCodec).encodeValue(ref)
      assertTrue(
        fieldValue(json, "$ref").contains(Json.String("#/components/schemas/User")),
        hasField(json, "summary"),
        hasField(json, "description"),
        !hasField(json, "Ref")
      )
    }
  )

  private lazy val securitySchemeEncodingSuite = suite("SecurityScheme encodes with type discriminator")(
    test("SecurityScheme.APIKey encodes with type discriminator") {
      val ss: SecurityScheme = SecurityScheme.APIKey(name = "api_key", in = APIKeyLocation.Header)
      val json               = securitySchemeCodec.encodeValue(ss)
      assertTrue(
        fieldValue(json, "type").contains(Json.String("apiKey")),
        fieldValue(json, "name").contains(Json.String("api_key")),
        fieldValue(json, "in").contains(Json.String("header")),
        !hasField(json, "APIKey")
      )
    },
    test("SecurityScheme.HTTP encodes with type discriminator") {
      val ss: SecurityScheme = SecurityScheme.HTTP(scheme = "bearer", bearerFormat = Some("JWT"))
      val json               = securitySchemeCodec.encodeValue(ss)
      assertTrue(
        fieldValue(json, "type").contains(Json.String("http")),
        fieldValue(json, "scheme").contains(Json.String("bearer")),
        fieldValue(json, "bearerFormat").contains(Json.String("JWT")),
        !hasField(json, "HTTP")
      )
    },
    test("SecurityScheme.OAuth2 encodes with type discriminator") {
      val ss: SecurityScheme = SecurityScheme.OAuth2(
        flows = OAuthFlows(
          `implicit` = Some(
            OAuthFlow(
              authorizationUrl = Some("https://example.com/oauth/authorize"),
              scopes = ChunkMap("read" -> "Read access")
            )
          )
        )
      )
      val json = securitySchemeCodec.encodeValue(ss)
      assertTrue(
        fieldValue(json, "type").contains(Json.String("oauth2")),
        hasField(json, "flows"),
        !hasField(json, "OAuth2")
      )
    },
    test("SecurityScheme.OpenIdConnect encodes with type discriminator") {
      val ss: SecurityScheme = SecurityScheme.OpenIdConnect(
        openIdConnectUrl = "https://example.com/.well-known/openid-configuration"
      )
      val json = securitySchemeCodec.encodeValue(ss)
      assertTrue(
        fieldValue(json, "type").contains(Json.String("openIdConnect")),
        fieldValue(json, "openIdConnectUrl").contains(
          Json.String("https://example.com/.well-known/openid-configuration")
        ),
        !hasField(json, "OpenIdConnect")
      )
    },
    test("SecurityScheme.MutualTLS encodes with type discriminator") {
      val ss: SecurityScheme = SecurityScheme.MutualTLS(description = Some(doc("Mutual TLS")))
      val json               = securitySchemeCodec.encodeValue(ss)
      assertTrue(
        fieldValue(json, "type").contains(Json.String("mutualTLS")),
        !hasField(json, "MutualTLS")
      )
    }
  )

  private lazy val extensionsFlatteningSuite = suite("Extensions are flattened at top level")(
    test("Info extensions are flattened at top level") {
      val info = Info(title = "API", version = "1.0", extensions = ChunkMap("x-custom" -> Json.String("val")))
      val json = infoCodec.encodeValue(info)
      assertTrue(
        hasField(json, "x-custom"),
        !hasField(json, "extensions")
      )
    },
    test("Contact extensions are flattened at top level") {
      val contact = Contact(name = Some("Support"), extensions = ChunkMap("x-id" -> Json.Number(1)))
      val json    = contactCodec.encodeValue(contact)
      assertTrue(
        hasField(json, "x-id"),
        !hasField(json, "extensions")
      )
    },
    test("License extensions are flattened at top level") {
      val license = License(name = "MIT", extensions = ChunkMap("x-year" -> Json.Number(2024)))
      val json    = licenseCodec.encodeValue(license)
      assertTrue(
        hasField(json, "x-year"),
        !hasField(json, "extensions")
      )
    },
    test("Server extensions are flattened at top level") {
      val server = Server(url = "https://api.example.com", extensions = ChunkMap("x-region" -> Json.String("us")))
      val json   = serverCodec.encodeValue(server)
      assertTrue(
        hasField(json, "x-region"),
        !hasField(json, "extensions")
      )
    },
    test("Tag extensions are flattened at top level") {
      val tag  = Tag(name = "users", extensions = ChunkMap("x-order" -> Json.Number(1)))
      val json = tagCodec.encodeValue(tag)
      assertTrue(
        hasField(json, "x-order"),
        !hasField(json, "extensions")
      )
    },
    test("ExternalDocumentation extensions are flattened") {
      val ed =
        ExternalDocumentation(url = "https://docs.example.com", extensions = ChunkMap("x-lang" -> Json.String("en")))
      val json = externalDocumentationCodec.encodeValue(ed)
      assertTrue(
        hasField(json, "x-lang"),
        !hasField(json, "extensions")
      )
    },
    test("Operation extensions are flattened at top level") {
      val op = Operation(
        responses = Responses(responses = ChunkMap("200" -> ReferenceOr.Value(Response(description = doc("OK"))))),
        extensions = ChunkMap("x-rate-limit" -> Json.Number(100))
      )
      val json = operationCodec.encodeValue(op)
      assertTrue(
        hasField(json, "x-rate-limit"),
        !hasField(json, "extensions")
      )
    },
    test("Parameter extensions are flattened at top level") {
      val param = Parameter(
        name = "q",
        in = ParameterLocation.Query,
        extensions = ChunkMap("x-internal" -> Json.Boolean(true))
      )
      val json = parameterCodec.encodeValue(param)
      assertTrue(
        hasField(json, "x-internal"),
        !hasField(json, "extensions")
      )
    },
    test("Response extensions are flattened at top level") {
      val resp = Response(description = doc("OK"), extensions = ChunkMap("x-response-id" -> Json.String("r1")))
      val json = responseCodec.encodeValue(resp)
      assertTrue(
        hasField(json, "x-response-id"),
        !hasField(json, "extensions")
      )
    },
    test("OpenAPI extensions are flattened at top level") {
      val api = OpenAPI(
        openapi = "3.1.0",
        info = Info(title = "API", version = "1.0"),
        extensions = ChunkMap("x-api-id" -> Json.String("api-123"))
      )
      val json = openAPICodec.encodeValue(api)
      assertTrue(
        hasField(json, "x-api-id"),
        !hasField(json, "extensions")
      )
    }
  )

  private lazy val wrapperTypesSuite = suite("Wrapper types encode as flat maps")(
    test("SecurityRequirement encodes as flat map") {
      val sr   = SecurityRequirement(ChunkMap("oauth2" -> Chunk("read", "write")))
      val json = securityRequirementCodec.encodeValue(sr)
      assertTrue(
        hasField(json, "oauth2"),
        !hasField(json, "requirements")
      )
    },
    test("Paths encodes with path keys at top level") {
      val paths =
        Paths(paths = ChunkMap("/pets" -> PathItem()), extensions = ChunkMap("x-id" -> Json.String("v1")))
      val json = pathsCodec.encodeValue(paths)
      assertTrue(
        hasField(json, "/pets"),
        hasField(json, "x-id"),
        !hasField(json, "paths")
      )
    },
    test("Callback encodes with callback keys at top level") {
      val cb = Callback(
        callbacks = ChunkMap(
          "{$request.body#/callbackUrl}" -> ReferenceOr.Value(PathItem(summary = Some(doc("Callback"))))
        ),
        extensions = ChunkMap("x-cb" -> Json.String("v1"))
      )
      val json = callbackCodec.encodeValue(cb)
      assertTrue(
        hasField(json, "{$request.body#/callbackUrl}"),
        hasField(json, "x-cb"),
        !hasField(json, "callbacks")
      )
    },
    test("Responses encodes with status code keys at top level") {
      val resps = Responses(
        responses = ChunkMap(
          "200" -> ReferenceOr.Value(Response(description = doc("OK"))),
          "404" -> ReferenceOr.Ref(Reference(`$ref` = "#/components/responses/NotFound"))
        ),
        default = Some(ReferenceOr.Value(Response(description = doc("Error")))),
        extensions = ChunkMap("x-resps" -> Json.String("v1"))
      )
      val json = responsesCodec.encodeValue(resps)
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
      val json  = parameterCodec.encodeValue(param)
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
      val json   = headerCodec.encodeValue(header)
      assertTrue(
        !hasField(json, "required"),
        !hasField(json, "deprecated"),
        !hasField(json, "allowEmptyValue")
      )
    },
    test("Operation with deprecated=false omits it") {
      val op   = Operation()
      val json = operationCodec.encodeValue(op)
      assertTrue(
        !hasField(json, "deprecated")
      )
    },
    test("RequestBody with required=false omits it") {
      val rb   = RequestBody(content = ChunkMap.empty)
      val json = requestBodyCodec.encodeValue(rb)
      assertTrue(
        !hasField(json, "required")
      )
    },
    test("Encoding with allowReserved=false omits it") {
      val enc  = Encoding()
      val json = encodingCodec.encodeValue(enc)
      assertTrue(
        !hasField(json, "allowReserved")
      )
    },
    test("XML with attribute=false and wrapped=false omits them") {
      val xml  = XML()
      val json = xmlCodec.encodeValue(xml)
      assertTrue(
        !hasField(json, "attribute"),
        !hasField(json, "wrapped")
      )
    },
    test("Empty maps and lists are omitted") {
      val op   = Operation()
      val json = operationCodec.encodeValue(op)
      assertTrue(
        !hasField(json, "tags"),
        !hasField(json, "parameters"),
        !hasField(json, "callbacks"),
        !hasField(json, "security"),
        !hasField(json, "servers"),
        hasField(json, "responses")
      )
    }
  )

  private lazy val roundTripSuite = suite("Encode/decode round-trip")(
    test("Contact round-trips") {
      val original = Contact(name = Some("Support"), url = Some("https://example.com"), email = Some("a@b.com"))
      val json     = contactCodec.encodeValue(original)
      val decoded  = contactCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("License round-trips") {
      val original =
        License(name = "MIT", identifier = Some("MIT"), extensions = ChunkMap("x-y" -> Json.Number(1)))
      val json    = licenseCodec.encodeValue(original)
      val decoded = licenseCodec.decodeValue(json)
      assertTrue(decoded == original)
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
        extensions = ChunkMap("x-api" -> Json.String("v1"))
      )
      val json    = infoCodec.encodeValue(original)
      val decoded = infoCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("Server round-trips") {
      val original = Server(
        url = "https://api.example.com",
        description = Some(doc("Prod")),
        variables = ChunkMap("env" -> ServerVariable(default = "prod")),
        extensions = ChunkMap("x-r" -> Json.String("us"))
      )
      val json    = serverCodec.encodeValue(original)
      val decoded = serverCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("ServerVariable round-trips") {
      val original = ServerVariable(
        default = "v1",
        `enum` = Chunk("v1", "v2"),
        description = Some(doc("Version")),
        extensions = ChunkMap("x-d" -> Json.Boolean(false))
      )
      val json    = serverVariableCodec.encodeValue(original)
      val decoded = serverVariableCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("Tag round-trips") {
      val original = Tag(
        name = "users",
        description = Some(doc("User ops")),
        externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com")),
        extensions = ChunkMap("x-o" -> Json.Number(1))
      )
      val json    = tagCodec.encodeValue(original)
      val decoded = tagCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("ExternalDocumentation round-trips") {
      val original = ExternalDocumentation(
        url = "https://docs.example.com",
        description = Some(doc("Full docs")),
        extensions = ChunkMap("x-l" -> Json.String("en"))
      )
      val json    = externalDocumentationCodec.encodeValue(original)
      val decoded = externalDocumentationCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("XML round-trips") {
      val original = XML(
        name = Some("item"),
        namespace = Some("http://example.com"),
        prefix = Some("ns"),
        attribute = true,
        wrapped = true
      )
      val json    = xmlCodec.encodeValue(original)
      val decoded = xmlCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("Discriminator round-trips") {
      val original = Discriminator(
        propertyName = "petType",
        mapping = ChunkMap("dog" -> "#/components/schemas/Dog")
      )
      val json    = discriminatorCodec.encodeValue(original)
      val decoded = discriminatorCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("Parameter round-trips") {
      val original = Parameter(
        name = "limit",
        in = ParameterLocation.Query,
        description = Some(doc("Page limit")),
        required = true,
        schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("integer"))))),
        extensions = ChunkMap("x-p" -> Json.String("val"))
      )
      val json    = parameterCodec.encodeValue(original)
      val decoded = parameterCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("Operation round-trips") {
      val original = Operation(
        responses = Responses(responses = ChunkMap("200" -> ReferenceOr.Value(Response(description = doc("OK"))))),
        tags = Chunk("users"),
        summary = Some(doc("Get users")),
        operationId = Some("getUsers"),
        deprecated = true,
        extensions = ChunkMap("x-rl" -> Json.Number(100))
      )
      val json    = operationCodec.encodeValue(original)
      val decoded = operationCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("Response round-trips") {
      val original = Response(
        description = doc("Success"),
        headers = ChunkMap(
          "X-Rate" -> ReferenceOr.Value(
            Header(schema =
              Some(
                ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("integer"))))
              )
            )
          )
        ),
        content = ChunkMap(
          "application/json" -> MediaType(
            schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("object")))))
          )
        ),
        extensions = ChunkMap("x-rid" -> Json.String("r1"))
      )
      val json    = responseCodec.encodeValue(original)
      val decoded = responseCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("SecurityScheme.APIKey round-trips") {
      val original: SecurityScheme = SecurityScheme.APIKey(
        name = "api_key",
        in = APIKeyLocation.Header,
        description = Some(doc("API key auth")),
        extensions = ChunkMap("x-f" -> Json.String("uuid"))
      )
      val json    = securitySchemeCodec.encodeValue(original)
      val decoded = securitySchemeCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("SecurityScheme.HTTP round-trips") {
      val original: SecurityScheme = SecurityScheme.HTTP(
        scheme = "bearer",
        bearerFormat = Some("JWT"),
        description = Some(doc("Bearer auth")),
        extensions = ChunkMap("x-t" -> Json.String("jwt"))
      )
      val json    = securitySchemeCodec.encodeValue(original)
      val decoded = securitySchemeCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("SecurityScheme.OAuth2 round-trips") {
      val original: SecurityScheme = SecurityScheme.OAuth2(
        flows = OAuthFlows(
          `implicit` = Some(
            OAuthFlow(
              authorizationUrl = Some("https://example.com/auth"),
              scopes = ChunkMap("read" -> "Read access")
            )
          ),
          extensions = ChunkMap("x-fl" -> Json.String("all"))
        ),
        description = Some(doc("OAuth2")),
        extensions = ChunkMap("x-p" -> Json.String("custom"))
      )
      val json    = securitySchemeCodec.encodeValue(original)
      val decoded = securitySchemeCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("SecurityScheme.OpenIdConnect round-trips") {
      val original: SecurityScheme = SecurityScheme.OpenIdConnect(
        openIdConnectUrl = "https://example.com/.well-known/openid-configuration",
        description = Some(doc("OIDC")),
        extensions = ChunkMap("x-oidc" -> Json.String("custom"))
      )
      val json    = securitySchemeCodec.encodeValue(original)
      val decoded = securitySchemeCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("SecurityScheme.MutualTLS round-trips") {
      val original: SecurityScheme = SecurityScheme.MutualTLS(
        description = Some(doc("mTLS")),
        extensions = ChunkMap("x-cert" -> Json.Boolean(true))
      )
      val json    = securitySchemeCodec.encodeValue(original)
      val decoded = securitySchemeCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("ReferenceOr.Ref round-trips") {
      val original: ReferenceOr[SchemaObject] = ReferenceOr.Ref(
        Reference(`$ref` = "#/components/schemas/Pet")
      )
      val json    = referenceOrCodec(schemaObjectCodec).encodeValue(original)
      val decoded = referenceOrCodec(schemaObjectCodec).decodeValue(json)
      assertTrue(decoded == original)
    },
    test("ReferenceOr.Value round-trips") {
      val original: ReferenceOr[SchemaObject] =
        ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("string"))))
      val json    = referenceOrCodec(schemaObjectCodec).encodeValue(original)
      val decoded = referenceOrCodec(schemaObjectCodec).decodeValue(json)
      assertTrue(decoded == original)
    },
    test("PathItem round-trips") {
      val original = PathItem(
        summary = Some(doc("API endpoint")),
        description = Some(doc("Detailed description")),
        get = Some(
          Operation(responses =
            Responses(responses = ChunkMap("200" -> ReferenceOr.Value(Response(description = doc("OK")))))
          )
        ),
        servers = Chunk(Server(url = "https://api.example.com")),
        parameters = Chunk(
          ReferenceOr.Value(Parameter(name = "id", in = ParameterLocation.Path, required = true))
        ),
        extensions = ChunkMap("x-path" -> Json.Number(1))
      )
      val json    = pathItemCodec.encodeValue(original)
      val decoded = pathItemCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("OpenAPI round-trips") {
      val original = OpenAPI(
        openapi = "3.1.0",
        info = Info(title = "Test API", version = "1.0.0"),
        servers = Chunk(Server(url = "https://api.example.com")),
        paths = Some(
          Paths(paths = ChunkMap("/users" -> PathItem(summary = Some(doc("Users")))))
        ),
        components = Some(
          Components(
            schemas = ChunkMap(
              "User" -> ReferenceOr.Value(
                SchemaObject(jsonSchema = Json.Object("type" -> Json.String("object")))
              )
            )
          )
        ),
        security = Chunk(SecurityRequirement(ChunkMap("bearerAuth" -> Chunk.empty))),
        tags = Chunk(Tag(name = "users")),
        externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com")),
        extensions = ChunkMap("x-api-id" -> Json.String("api-123"))
      )
      val json    = openAPICodec.encodeValue(original)
      val decoded = openAPICodec.decodeValue(json)
      assertTrue(decoded == original)
    }
  )

  private lazy val petstoreRoundTripSuite = suite("Petstore JSON round-trip")(
    test("petstore.json round-trips through decode/encode") {
      val jsonString = scala.util.Using.resource(scala.io.Source.fromResource("openapi/petstore.json"))(_.mkString)
      val parsed     = Json.parse(jsonString)
      assertTrue(parsed.isRight) &&
      (parsed match {
        case Right(json) =>
          val api       = openAPICodec.decodeValue(json)
          val reEncoded = openAPICodec.encodeValue(api)
          assertTrue(
            fieldValue(reEncoded, "openapi").contains(Json.String("3.1.0")),
            hasField(reEncoded, "info"),
            hasField(reEncoded, "paths"),
            hasField(reEncoded, "components")
          ) && {
            val infoJson = fieldValue(reEncoded, "info").get
            assertTrue(
              fieldValue(infoJson, "title").contains(Json.String("Swagger Petstore")),
              fieldValue(infoJson, "version").contains(Json.String("1.0.0"))
            )
          } && {
            val reDecoded = openAPICodec.decodeValue(reEncoded)
            assertTrue(reDecoded == api)
          }
        case Left(_) =>
          assertTrue(false)
      })
    },
    test("minimal.json round-trips through decode/encode") {
      val jsonString = scala.util.Using.resource(scala.io.Source.fromResource("openapi/minimal.json"))(_.mkString)
      val parsed     = Json.parse(jsonString)
      assertTrue(parsed.isRight) &&
      (parsed match {
        case Right(json) =>
          val api       = openAPICodec.decodeValue(json)
          val reEncoded = openAPICodec.encodeValue(api)
          val reDecoded = openAPICodec.decodeValue(reEncoded)
          assertTrue(reDecoded == api)
        case Left(_) =>
          assertTrue(false)
      })
    },
    test("with-security.json round-trips through decode/encode") {
      val jsonString = scala.util.Using.resource(scala.io.Source.fromResource("openapi/with-security.json"))(_.mkString)
      val parsed     = Json.parse(jsonString)
      assertTrue(parsed.isRight) &&
      (parsed match {
        case Right(json) =>
          val api       = openAPICodec.decodeValue(json)
          val reEncoded = openAPICodec.encodeValue(api)
          val reDecoded = openAPICodec.decodeValue(reEncoded)
          assertTrue(reDecoded == api)
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
            paths = ChunkMap(
              "/pets" -> PathItem(
                get = Some(
                  Operation(
                    responses = Responses(
                      responses = ChunkMap(
                        "200" -> ReferenceOr.Value(
                          Response(
                            description = doc("List of pets"),
                            content = ChunkMap("application/json" -> MediaType(schema = Some(petRef)))
                          )
                        )
                      )
                    ),
                    parameters = Chunk(
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
            schemas = ChunkMap.from(petDefs.map { case (k, v) => k -> ReferenceOr.Value(v) })
          )
        )
      )

      val json = openAPICodec.encodeValue(api)
      assertTrue(
        hasField(json, "openapi"),
        hasField(json, "info"),
        hasField(json, "paths"),
        hasField(json, "components")
      )
    }
  )

  private lazy val decoderFromRawJsonSuite = suite("Decode from raw JSON strings")(
    test("decode Info from raw JSON") {
      val json = Json.Object(
        "title"          -> Json.String("My API"),
        "version"        -> Json.String("2.0.0"),
        "description"    -> Json.String("An awesome API"),
        "termsOfService" -> Json.String("https://example.com/terms"),
        "x-custom"       -> Json.String("value")
      )
      val result = infoCodec.decodeValue(json)
      assertTrue(
        result.title == "My API",
        result.version == "2.0.0",
        result.description.isDefined,
        result.termsOfService.contains("https://example.com/terms"),
        result.extensions.contains("x-custom")
      )
    },
    test("decode Parameter from raw JSON with 'in' as string") {
      val json = Json.Object(
        "name"     -> Json.String("limit"),
        "in"       -> Json.String("query"),
        "required" -> Json.Boolean(false),
        "schema"   -> Json.Object("type" -> Json.String("integer"))
      )
      val result = parameterCodec.decodeValue(json)
      assertTrue(
        result.name == "limit",
        result.in == ParameterLocation.Query,
        result.required == false
      )
    },
    test("decode SecurityScheme.APIKey from raw JSON") {
      val json = Json.Object(
        "type" -> Json.String("apiKey"),
        "name" -> Json.String("X-API-Key"),
        "in"   -> Json.String("header")
      )
      val result = securitySchemeCodec.decodeValue(json)
      assertTrue(result.isInstanceOf[SecurityScheme.APIKey])
    },
    test("decode ReferenceOr with $ref key produces Ref") {
      val json = Json.Object(
        "$ref"    -> Json.String("#/components/schemas/Pet"),
        "summary" -> Json.String("A pet")
      )
      val result = referenceOrCodec(schemaObjectCodec).decodeValue(json)
      result match {
        case ReferenceOr.Ref(ref) =>
          assertTrue(ref.`$ref` == "#/components/schemas/Pet", ref.summary.isDefined)
        case _ => assertTrue(false)
      }
    },
    test("decode ReferenceOr without $ref produces Value") {
      val json = Json.Object(
        "type"   -> Json.String("string"),
        "format" -> Json.String("email")
      )
      val result = referenceOrCodec(schemaObjectCodec).decodeValue(json)
      result match {
        case ReferenceOr.Value(_) => assertTrue(true)
        case _                    => assertTrue(false)
      }
    },
    test("decoder ignores unknown fields gracefully") {
      val json = Json.Object(
        "name"           -> Json.String("Test"),
        "url"            -> Json.String("https://example.com"),
        "unknownField"   -> Json.String("should be ignored"),
        "anotherUnknown" -> Json.Number(42)
      )
      val result = contactCodec.decodeValue(json)
      assertTrue(result != null)
    }
  )

  private lazy val errorCasesSuite = suite("Decoder error handling")(
    test("Info decoder fails on missing required 'title' field") {
      val json   = Json.Object("version" -> Json.String("1.0"))
      val result = infoCodec.decode(json)
      assertTrue(result.isLeft)
    },
    test("Info decoder fails on missing required 'version' field") {
      val json   = Json.Object("title" -> Json.String("My API"))
      val result = infoCodec.decode(json)
      assertTrue(result.isLeft)
    },
    test("ParameterLocation decoder fails on invalid location string") {
      val json   = Json.String("invalid_location")
      val result = parameterLocationCodec.decode(json)
      assertTrue(result.isLeft)
    },
    test("APIKeyLocation decoder fails on invalid location string") {
      val json   = Json.String("body")
      val result = apiKeyLocationCodec.decode(json)
      assertTrue(result.isLeft)
    },
    test("SecurityScheme decoder fails on unknown type") {
      val json   = Json.Object("type" -> Json.String("unknownType"))
      val result = securitySchemeCodec.decode(json)
      assertTrue(result.isLeft)
    },
    test("SecurityScheme decoder fails on missing type field") {
      val json   = Json.Object("name" -> Json.String("api_key"))
      val result = securitySchemeCodec.decode(json)
      assertTrue(result.isLeft)
    },
    test("Parameter decoder fails on missing 'name' field") {
      val json   = Json.Object("in" -> Json.String("query"))
      val result = parameterCodec.decode(json)
      assertTrue(result.isLeft)
    },
    test("Parameter decoder fails on missing 'in' field") {
      val json   = Json.Object("name" -> Json.String("limit"))
      val result = parameterCodec.decode(json)
      assertTrue(result.isLeft)
    },
    test("Response decoder fails on missing required 'description'") {
      val json   = Json.Object("content" -> Json.Object())
      val result = responseCodec.decode(json)
      assertTrue(result.isLeft)
    },
    test("Info decoder fails when given non-object JSON") {
      val json   = Json.String("not an object")
      val result = infoCodec.decode(json)
      assertTrue(result.isLeft)
    }
  )

  private lazy val securitySchemeVariantsSuite = suite("SecurityScheme variant round-trips")(
    test("SecurityScheme.APIKey round-trips correctly") {
      val original: SecurityScheme = SecurityScheme.APIKey(
        name = "api_key",
        in = APIKeyLocation.Header,
        description = Some(doc("API key")),
        extensions = ChunkMap("x-id" -> Json.String("1"))
      )
      val json    = securitySchemeCodec.encodeValue(original)
      val decoded = securitySchemeCodec.decodeValue(json)
      assertTrue(
        decoded == original,
        fieldValue(json, "type").contains(Json.String("apiKey")),
        fieldValue(json, "in").contains(Json.String("header"))
      )
    },
    test("SecurityScheme.HTTP round-trips correctly") {
      val original: SecurityScheme =
        SecurityScheme.HTTP(scheme = "bearer", bearerFormat = Some("JWT"), description = Some(doc("Bearer")))
      val json    = securitySchemeCodec.encodeValue(original)
      val decoded = securitySchemeCodec.decodeValue(json)
      assertTrue(
        decoded == original,
        fieldValue(json, "type").contains(Json.String("http")),
        fieldValue(json, "scheme").contains(Json.String("bearer"))
      )
    },
    test("SecurityScheme.OAuth2 round-trips correctly") {
      val original: SecurityScheme = SecurityScheme.OAuth2(
        flows = OAuthFlows(
          authorizationCode = Some(
            OAuthFlow(
              authorizationUrl = Some("https://example.com/auth"),
              tokenUrl = Some("https://example.com/token"),
              scopes = ChunkMap("read" -> "Read access")
            )
          )
        ),
        description = Some(doc("OAuth2"))
      )
      val json    = securitySchemeCodec.encodeValue(original)
      val decoded = securitySchemeCodec.decodeValue(json)
      assertTrue(
        decoded == original,
        fieldValue(json, "type").contains(Json.String("oauth2"))
      )
    },
    test("SecurityScheme.OpenIdConnect round-trips correctly") {
      val original: SecurityScheme = SecurityScheme.OpenIdConnect(
        openIdConnectUrl = "https://example.com/.well-known",
        description = Some(doc("OIDC"))
      )
      val json    = securitySchemeCodec.encodeValue(original)
      val decoded = securitySchemeCodec.decodeValue(json)
      assertTrue(
        decoded == original,
        fieldValue(json, "type").contains(Json.String("openIdConnect"))
      )
    },
    test("SecurityScheme.MutualTLS round-trips correctly") {
      val original: SecurityScheme = SecurityScheme.MutualTLS(description = Some(doc("mTLS")))
      val json                     = securitySchemeCodec.encodeValue(original)
      val decoded                  = securitySchemeCodec.decodeValue(json)
      assertTrue(
        decoded == original,
        fieldValue(json, "type").contains(Json.String("mutualTLS"))
      )
    }
  )

  private lazy val responsesAndSchemaEdgeCasesSuite = suite("Responses and SchemaObject edge cases")(
    test("Responses encodes 'default' key at top level") {
      val responses = Responses(
        responses = ChunkMap("200" -> ReferenceOr.Value(Response(description = doc("OK")))),
        default = Some(ReferenceOr.Value(Response(description = doc("Error")))),
        extensions = ChunkMap("x-id" -> Json.String("1"))
      )
      val json = responsesCodec.encodeValue(responses)
      assertTrue(
        hasField(json, "200"),
        hasField(json, "default"),
        hasField(json, "x-id"),
        !hasField(json, "responses")
      )
    },
    test("Responses round-trips with 'default' key") {
      val original = Responses(
        responses = ChunkMap(
          "200" -> ReferenceOr.Value(Response(description = doc("OK"))),
          "404" -> ReferenceOr.Value(Response(description = doc("Not found")))
        ),
        default = Some(ReferenceOr.Value(Response(description = doc("Error")))),
        extensions = ChunkMap("x-resp" -> Json.Boolean(true))
      )
      val json    = responsesCodec.encodeValue(original)
      val decoded = responsesCodec.decodeValue(json)
      assertTrue(decoded == original)
    },
    test("Responses decodes 'default' from raw JSON") {
      val json = Json.Object(
        "200"      -> Json.Object("description" -> Json.String("OK")),
        "default"  -> Json.Object("description" -> Json.String("Error")),
        "x-custom" -> Json.String("val")
      )
      val result = responsesCodec.decodeValue(json)
      assertTrue(
        result.default.isDefined,
        result.responses.contains("200"),
        result.extensions.contains("x-custom")
      )
    },
    test("SchemaObject boolean schema (true) round-trips") {
      val original = SchemaObject(jsonSchema = Json.Boolean(true))
      val json     = schemaObjectCodec.encodeValue(original)
      val decoded  = schemaObjectCodec.decodeValue(json)
      assertTrue(json == Json.Boolean(true), decoded == original)
    },
    test("SchemaObject boolean schema (false) round-trips") {
      val original = SchemaObject(jsonSchema = Json.Boolean(false))
      val json     = schemaObjectCodec.encodeValue(original)
      val decoded  = schemaObjectCodec.decodeValue(json)
      assertTrue(json == Json.Boolean(false), decoded == original)
    },
    test("SchemaObject.toJson renders discriminator correctly with OpenAPICodec") {
      val so = SchemaObject(
        jsonSchema = Json.Object("type" -> Json.String("object")),
        discriminator = Some(Discriminator(propertyName = "petType", mapping = ChunkMap("dog" -> "#/dog"))),
        xml = Some(XML(name = Some("root"), attribute = true)),
        externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com", description = Some(doc("Docs")))),
        example = Some(Json.Object("id" -> Json.Number(1))),
        extensions = ChunkMap("x-schema" -> Json.String("custom"))
      )
      val json     = so.toJson
      val discJson = fieldValue(json, "discriminator")
      assertTrue(discJson.isDefined)
      val discObj = discJson.get
      assertTrue(
        discObj.fields.exists(_._1 == "propertyName"),
        discObj.fields.exists(_._1 == "mapping")
      )
      val edJson = fieldValue(json, "externalDocs")
      assertTrue(edJson.isDefined)
      val descField = edJson.get.fields.find(_._1 == "description")
      assertTrue(descField.isDefined)
      descField.get._2 match {
        case _: Json.String => assertTrue(true)
        case _              => assertTrue(false)
      }
      val xmlJson = fieldValue(json, "xml")
      assertTrue(xmlJson.isDefined)
      assertTrue(xmlJson.get.fields.exists(_._1 == "name"))
      assertTrue(xmlJson.get.fields.exists(_._1 == "attribute"))
    },
    test("SchemaObject with all vocabulary fields round-trips through codec") {
      val original = SchemaObject(
        jsonSchema = Json.Object("type" -> Json.String("object"), "properties" -> Json.Object()),
        discriminator = Some(Discriminator(propertyName = "type", mapping = ChunkMap("a" -> "#/a"))),
        xml = Some(XML(name = Some("root"), namespace = Some("http://ns.example.com"), wrapped = true)),
        externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com")),
        example = Some(Json.String("example")),
        extensions = ChunkMap("x-ext" -> Json.Number(42))
      )
      val json    = schemaObjectCodec.encodeValue(original)
      val decoded = schemaObjectCodec.decodeValue(json)
      assertTrue(decoded == original)
    }
  )

  private lazy val webhooksSuite = suite("Webhooks")(
    test("OpenAPI with webhooks round-trips") {
      val original = OpenAPI(
        openapi = "3.1.0",
        info = Info(title = "API", version = "1.0"),
        webhooks = ChunkMap(
          "newPet" -> ReferenceOr.Value(
            PathItem(
              post = Some(
                Operation(responses =
                  Responses(responses = ChunkMap("200" -> ReferenceOr.Value(Response(description = doc("OK")))))
                )
              )
            )
          )
        )
      )
      val json = openAPICodec.encodeValue(original)
      assertTrue(hasField(json, "webhooks")) &&
      assertTrue(openAPICodec.decodeValue(json) == original)
    },
    test("OpenAPI without webhooks omits field") {
      val original = OpenAPI(
        openapi = "3.1.0",
        info = Info(title = "API", version = "1.0")
      )
      val json = openAPICodec.encodeValue(original)
      assertTrue(!hasField(json, "webhooks"))
    }
  )
}
