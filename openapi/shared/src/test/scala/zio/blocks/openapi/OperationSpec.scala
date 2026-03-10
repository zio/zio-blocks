package zio.blocks.openapi

import zio.blocks.chunk.{Chunk, ChunkMap}
import zio.blocks.docs.{Doc, Inline, Paragraph}
import zio.blocks.schema._
import zio.blocks.schema.json.Json
import zio.test._

object OperationSpec extends SchemaBaseSpec {
  private def doc(s: String): Doc      = Doc(Chunk.single(Paragraph(Chunk.single(Inline.Text(s)))))
  def spec: Spec[TestEnvironment, Any] = suite("Operation")(
    test("can be constructed with no fields (responses defaults to empty)") {
      val operation = Operation()

      assertTrue(
        operation.responses.responses.isEmpty,
        operation.tags.isEmpty,
        operation.summary.isEmpty,
        operation.description.isEmpty,
        operation.externalDocs.isEmpty,
        operation.operationId.isEmpty,
        operation.parameters.isEmpty,
        operation.requestBody.isEmpty,
        operation.callbacks.isEmpty,
        operation.deprecated == false,
        operation.security.isEmpty,
        operation.servers.isEmpty,
        operation.extensions.isEmpty
      )
    },
    test("can be constructed with all fields populated") {
      val responses = Responses(
        responses = ChunkMap(
          "200" -> ReferenceOr.Value(Response(description = doc("Success"))),
          "404" -> ReferenceOr.Value(Response(description = doc("Not Found")))
        )
      )
      val tags         = Chunk("users", "admin")
      val summary      = doc("Get user by ID")
      val description  = doc("Retrieves a user from the database by their unique identifier")
      val externalDocs = ExternalDocumentation(
        url = "https://docs.example.com/users",
        description = Some(doc("User API Documentation"))
      )
      val operationId = "getUserById"
      val parameters  = Chunk(
        ReferenceOr.Value(Parameter(name = "id", in = ParameterLocation.Path, required = true))
      )
      val requestBody = ReferenceOr.Value(
        RequestBody(content = ChunkMap("application/json" -> MediaType()), required = true)
      )
      val callbacks = ChunkMap(
        "myCallback" -> ReferenceOr.Value(Callback())
      )
      val deprecated = true
      val security   = Chunk(
        SecurityRequirement(ChunkMap("api_key" -> Chunk("read:users")))
      )
      val servers = Chunk(
        Server(url = "https://api.example.com/v1")
      )
      val extensions = ChunkMap(
        "x-internal"   -> Json.Boolean(false),
        "x-rate-limit" -> Json.Number(100)
      )

      val operation = Operation(
        responses = responses,
        tags = tags,
        summary = Some(summary),
        description = Some(description),
        externalDocs = Some(externalDocs),
        operationId = Some(operationId),
        parameters = parameters,
        requestBody = Some(requestBody),
        callbacks = callbacks,
        deprecated = deprecated,
        security = security,
        servers = servers,
        extensions = extensions
      )

      assertTrue(
        operation.responses == responses,
        operation.tags == tags,
        operation.summary.contains(summary),
        operation.description.contains(description),
        operation.externalDocs.contains(externalDocs),
        operation.operationId.contains(operationId),
        operation.parameters == parameters,
        operation.requestBody.contains(requestBody),
        operation.callbacks == callbacks,
        operation.deprecated == deprecated,
        operation.security == security,
        operation.servers == servers,
        operation.extensions == extensions
      )
    },
    test("responses field defaults to empty Responses") {
      val operation = Operation()

      assertTrue(
        operation.responses.responses.isEmpty
      )
    },
    test("has separate summary and description fields") {
      val summary     = doc("Short summary")
      val description = doc("Long detailed description with lots of information")
      val operation   = Operation(
        summary = Some(summary),
        description = Some(description)
      )

      assertTrue(
        operation.summary.contains(summary),
        operation.description.contains(description),
        operation.summary != operation.description
      )
    },
    test("preserves extensions on construction") {
      val extensions = ChunkMap(
        "x-code-samples"     -> Json.Array(Json.String("sample1")),
        "x-visibility"       -> Json.String("public"),
        "x-deprecated-since" -> Json.String("2024-01-01")
      )
      val operation = Operation(extensions = extensions)

      assertTrue(
        operation.extensions.size == 3,
        operation.extensions.get("x-code-samples").isDefined,
        operation.extensions.get("x-visibility").contains(Json.String("public")),
        operation.extensions.get("x-deprecated-since").contains(Json.String("2024-01-01"))
      )
    },
    test("tags can be used for logical grouping") {
      val tags      = Chunk("pets", "store", "user")
      val operation = Operation(tags = tags)

      assertTrue(
        operation.tags.length == 3,
        operation.tags.contains("pets"),
        operation.tags.contains("store"),
        operation.tags.contains("user")
      )
    },
    test("operationId provides unique identifier") {
      val operationId = "listPets"
      val operation   = Operation(operationId = Some(operationId))

      assertTrue(
        operation.operationId.contains(operationId),
        operation.operationId.contains("listPets")
      )
    },
    test("deprecated field defaults to false") {
      val operation = Operation()

      assertTrue(
        operation.deprecated == false
      )
    },
    test("deprecated field can be set to true") {
      val operation = Operation(deprecated = true)

      assertTrue(
        operation.deprecated == true
      )
    },
    test("supports multiple security requirements") {
      val security = Chunk(
        SecurityRequirement(ChunkMap("api_key" -> Chunk.empty)),
        SecurityRequirement(ChunkMap("oauth2" -> Chunk("read", "write")))
      )
      val operation = Operation(security = security)

      assertTrue(
        operation.security.length == 2,
        operation.security(0).requirements.contains("api_key"),
        operation.security(1).requirements.contains("oauth2")
      )
    },
    test("supports alternative servers") {
      val servers = Chunk(
        Server(url = "https://dev.example.com"),
        Server(url = "https://staging.example.com"),
        Server(url = "https://prod.example.com")
      )
      val operation = Operation(servers = servers)

      assertTrue(
        operation.servers.length == 3,
        operation.servers(0).url == "https://dev.example.com",
        operation.servers(1).url == "https://staging.example.com",
        operation.servers(2).url == "https://prod.example.com"
      )
    },
    test("supports external documentation") {
      val externalDocs = ExternalDocumentation(
        url = "https://example.com/docs/operation",
        description = Some(doc("Additional documentation for this operation"))
      )
      val operation = Operation(externalDocs = Some(externalDocs))

      assertTrue(
        operation.externalDocs.isDefined,
        operation.externalDocs.exists(_.url == "https://example.com/docs/operation"),
        operation.externalDocs.exists(_.description.contains(doc("Additional documentation for this operation")))
      )
    },
    test("parameters field uses typed ReferenceOr[Parameter]") {
      val parameters = Chunk(
        ReferenceOr.Value(Parameter(name = "id", in = ParameterLocation.Path, required = true)),
        ReferenceOr.Value(Parameter(name = "limit", in = ParameterLocation.Query))
      )
      val operation = Operation(parameters = parameters)

      assertTrue(
        operation.parameters.length == 2
      )
    },
    test("requestBody field uses typed ReferenceOr[RequestBody]") {
      val requestBody = ReferenceOr.Value(
        RequestBody(
          content = ChunkMap("application/json" -> MediaType()),
          required = true
        )
      )
      val operation = Operation(requestBody = Some(requestBody))

      assertTrue(
        operation.requestBody.isDefined
      )
    },
    test("callbacks field uses typed ReferenceOr[Callback]") {
      val callbacks = ChunkMap(
        "onData"  -> ReferenceOr.Value(Callback()),
        "onError" -> ReferenceOr.Value(Callback())
      )
      val operation = Operation(callbacks = callbacks)

      assertTrue(
        operation.callbacks.size == 2,
        operation.callbacks.contains("onData"),
        operation.callbacks.contains("onError")
      )
    },
    test("Schema[Operation] can be derived") {
      val operation = Operation()
      val schema    = Schema[Operation]

      assertTrue(schema != null, operation != null)
    },
    test("Operation round-trips through DynamicValue") {
      val responses = Responses(
        responses = ChunkMap(
          "200" -> ReferenceOr.Value(Response(description = doc("Success"))),
          "400" -> ReferenceOr.Value(Response(description = doc("Bad Request")))
        )
      )
      val operation = Operation(
        responses = responses,
        tags = Chunk("users", "api"),
        summary = Some(doc("List users")),
        description = Some(doc("Returns a list of all users in the system")),
        operationId = Some("listUsers"),
        deprecated = false,
        extensions = ChunkMap("x-internal" -> Json.Boolean(true))
      )

      val dv     = Schema[Operation].toDynamicValue(operation)
      val result = Schema[Operation].fromDynamicValue(dv)

      assertTrue(
        result.isRight,
        result.exists(_.responses == responses),
        result.exists(_.tags == Chunk("users", "api")),
        result.exists(_.summary.contains(doc("List users"))),
        result.exists(_.description.contains(doc("Returns a list of all users in the system"))),
        result.exists(_.operationId.contains("listUsers")),
        result.exists(_.deprecated == false),
        result.exists(_.extensions.contains("x-internal"))
      )
    },
    test("Operation with empty responses") {
      val operation = Operation()

      assertTrue(
        operation.responses.responses.isEmpty
      )
    },
    test("Operation supports complex nested structures") {
      val responses = Responses(
        responses = ChunkMap(
          "200" -> ReferenceOr.Value(
            Response(
              description = doc("Success"),
              content = ChunkMap(
                "application/json" -> MediaType(
                  schema =
                    Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("object")))))
                )
              )
            )
          )
        )
      )
      val operation = Operation(
        responses = responses,
        tags = Chunk("complex"),
        summary = Some(doc("Complex operation")),
        parameters = Chunk(
          ReferenceOr.Value(
            Parameter(
              name = "filter",
              in = ParameterLocation.Query,
              schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("string")))))
            )
          )
        )
      )

      assertTrue(
        operation.responses.responses.nonEmpty,
        operation.tags.contains("complex"),
        operation.parameters.nonEmpty
      )
    },
    test("Operation with empty collections uses defaults") {
      val operation = Operation()

      assertTrue(
        operation.tags == Chunk.empty,
        operation.parameters == Chunk.empty,
        operation.callbacks == ChunkMap.empty[String, ReferenceOr[Callback]],
        operation.security == Chunk.empty,
        operation.servers == Chunk.empty,
        operation.extensions == ChunkMap.empty[String, Json],
        operation.responses == Responses()
      )
    },
    test("multiple responses with different status codes") {
      val responses = Responses(
        responses = ChunkMap(
          "200" -> ReferenceOr.Value(Response(description = doc("Success"))),
          "201" -> ReferenceOr.Value(Response(description = doc("Created"))),
          "400" -> ReferenceOr.Value(Response(description = doc("Bad Request"))),
          "401" -> ReferenceOr.Value(Response(description = doc("Unauthorized"))),
          "404" -> ReferenceOr.Value(Response(description = doc("Not Found"))),
          "500" -> ReferenceOr.Value(Response(description = doc("Internal Server Error")))
        )
      )
      val operation = Operation(responses = responses)

      assertTrue(
        operation.responses.responses.size == 6,
        operation.responses.responses.contains("200"),
        operation.responses.responses.contains("404"),
        operation.responses.responses.contains("500")
      )
    }
  )
}
