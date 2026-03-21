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
      val operation = Operation(
        responses = Responses(
          responses = ChunkMap(
            "200" -> ReferenceOr.Value(Response(description = doc("Success"))),
            "404" -> ReferenceOr.Value(Response(description = doc("Not Found")))
          )
        ),
        tags = Chunk("users", "admin"),
        summary = Some(doc("Get user by ID")),
        description = Some(doc("Retrieves a user from the database by their unique identifier")),
        externalDocs = Some(
          ExternalDocumentation(
            url = "https://docs.example.com/users",
            description = Some(doc("User API Documentation"))
          )
        ),
        operationId = Some("getUserById"),
        parameters = Chunk(ReferenceOr.Value(Parameter(name = "id", in = ParameterLocation.Path, required = true))),
        requestBody = Some(
          ReferenceOr.Value(
            RequestBody(content = ChunkMap("application/json" -> MediaType()), required = true)
          )
        ),
        callbacks = ChunkMap("myCallback" -> ReferenceOr.Value(Callback())),
        deprecated = true,
        security = Chunk(SecurityRequirement(ChunkMap("api_key" -> Chunk("read:users")))),
        servers = Chunk(Server(url = "https://api.example.com/v1")),
        extensions = ChunkMap("x-internal" -> Json.Boolean(false), "x-rate-limit" -> Json.Number(100))
      )
      assertTrue(
        operation.responses == Responses(
          responses = ChunkMap(
            "200" -> ReferenceOr.Value(Response(description = doc("Success"))),
            "404" -> ReferenceOr.Value(Response(description = doc("Not Found")))
          )
        ),
        operation.tags == Chunk("users", "admin"),
        operation.summary.contains(doc("Get user by ID")),
        operation.description.contains(doc("Retrieves a user from the database by their unique identifier")),
        operation.externalDocs.contains(
          ExternalDocumentation(
            url = "https://docs.example.com/users",
            description = Some(doc("User API Documentation"))
          )
        ),
        operation.operationId.contains("getUserById"),
        operation.parameters == Chunk(
          ReferenceOr.Value(Parameter(name = "id", in = ParameterLocation.Path, required = true))
        ),
        operation.requestBody.contains(
          ReferenceOr.Value(
            RequestBody(content = ChunkMap("application/json" -> MediaType()), required = true)
          )
        ),
        operation.callbacks == ChunkMap("myCallback" -> ReferenceOr.Value(Callback())),
        operation.deprecated == true,
        operation.security == Chunk(SecurityRequirement(ChunkMap("api_key" -> Chunk("read:users")))),
        operation.servers == Chunk(Server(url = "https://api.example.com/v1")),
        operation.extensions == ChunkMap("x-internal" -> Json.Boolean(false), "x-rate-limit" -> Json.Number(100))
      )
    },
    test("responses field defaults to empty Responses") {
      val operation = Operation()
      assertTrue(operation.responses.responses.isEmpty)
    },
    test("has separate summary and description fields") {
      val operation = Operation(
        summary = Some(doc("Short summary")),
        description = Some(doc("Long detailed description with lots of information"))
      )
      assertTrue(
        operation.summary.contains(doc("Short summary")),
        operation.description.contains(doc("Long detailed description with lots of information")),
        operation.summary != operation.description
      )
    },
    test("preserves extensions on construction") {
      val operation = Operation(extensions =
        ChunkMap(
          "x-code-samples"     -> Json.Array(Json.String("sample1")),
          "x-visibility"       -> Json.String("public"),
          "x-deprecated-since" -> Json.String("2024-01-01")
        )
      )
      assertTrue(
        operation.extensions.size == 3,
        operation.extensions.get("x-code-samples").isDefined,
        operation.extensions.get("x-visibility").contains(Json.String("public")),
        operation.extensions.get("x-deprecated-since").contains(Json.String("2024-01-01"))
      )
    },
    test("tags can be used for logical grouping") {
      val operation = Operation(tags = Chunk("pets", "store", "user"))
      assertTrue(
        operation.tags.length == 3,
        operation.tags.contains("pets"),
        operation.tags.contains("store"),
        operation.tags.contains("user")
      )
    },
    test("operationId provides unique identifier") {
      val operation = Operation(operationId = Some("listPets"))
      assertTrue(
        operation.operationId.contains("listPets"),
        operation.operationId.contains("listPets")
      )
    },
    test("deprecated field defaults to false") {
      val operation = Operation()
      assertTrue(operation.deprecated == false)
    },
    test("deprecated field can be set to true") {
      val operation = Operation(deprecated = true)
      assertTrue(operation.deprecated == true)
    },
    test("supports multiple security requirements") {
      val operation = Operation(security =
        Chunk(
          SecurityRequirement(ChunkMap("api_key" -> Chunk.empty)),
          SecurityRequirement(ChunkMap("oauth2" -> Chunk("read", "write")))
        )
      )
      assertTrue(
        operation.security.length == 2,
        operation.security(0).requirements.contains("api_key"),
        operation.security(1).requirements.contains("oauth2")
      )
    },
    test("supports alternative servers") {
      val operation = Operation(servers =
        Chunk(
          Server(url = "https://dev.example.com"),
          Server(url = "https://staging.example.com"),
          Server(url = "https://prod.example.com")
        )
      )
      assertTrue(
        operation.servers.length == 3,
        operation.servers(0).url == "https://dev.example.com",
        operation.servers(1).url == "https://staging.example.com",
        operation.servers(2).url == "https://prod.example.com"
      )
    },
    test("supports external documentation") {
      val operation = Operation(externalDocs =
        Some(
          ExternalDocumentation(
            url = "https://example.com/docs/operation",
            description = Some(doc("Additional documentation for this operation"))
          )
        )
      )
      assertTrue(
        operation.externalDocs.isDefined,
        operation.externalDocs.exists(_.url == "https://example.com/docs/operation"),
        operation.externalDocs.exists(_.description.contains(doc("Additional documentation for this operation")))
      )
    },
    test("parameters field uses typed ReferenceOr[Parameter]") {
      val operation = Operation(parameters =
        Chunk(
          ReferenceOr.Value(Parameter(name = "id", in = ParameterLocation.Path, required = true)),
          ReferenceOr.Value(Parameter(name = "limit", in = ParameterLocation.Query))
        )
      )
      assertTrue(operation.parameters.length == 2)
    },
    test("requestBody field uses typed ReferenceOr[RequestBody]") {
      val operation = Operation(requestBody =
        Some(
          ReferenceOr.Value(
            RequestBody(content = ChunkMap("application/json" -> MediaType()), required = true)
          )
        )
      )
      assertTrue(operation.requestBody.isDefined)
    },
    test("callbacks field uses typed ReferenceOr[Callback]") {
      val operation = Operation(callbacks =
        ChunkMap(
          "onData"  -> ReferenceOr.Value(Callback()),
          "onError" -> ReferenceOr.Value(Callback())
        )
      )
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
      val operation = Operation(
        responses = Responses(
          responses = ChunkMap(
            "200" -> ReferenceOr.Value(Response(description = doc("Success"))),
            "400" -> ReferenceOr.Value(Response(description = doc("Bad Request")))
          )
        ),
        tags = Chunk("users", "api"),
        summary = Some(doc("List users")),
        description = Some(doc("Returns a list of all users in the system")),
        operationId = Some("listUsers"),
        extensions = ChunkMap("x-internal" -> Json.Boolean(true))
      )
      val result = Schema[Operation].fromDynamicValue(Schema[Operation].toDynamicValue(operation))
      assertTrue(
        result.isRight,
        result.exists(
          _.responses == Responses(
            responses = ChunkMap(
              "200" -> ReferenceOr.Value(Response(description = doc("Success"))),
              "400" -> ReferenceOr.Value(Response(description = doc("Bad Request")))
            )
          )
        ),
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
      assertTrue(operation.responses.responses.isEmpty)
    },
    test("Operation supports complex nested structures") {
      val operation = Operation(
        responses = Responses(
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
        ),
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
      val operation = Operation(responses =
        Responses(
          responses = ChunkMap(
            "200" -> ReferenceOr.Value(Response(description = doc("Success"))),
            "201" -> ReferenceOr.Value(Response(description = doc("Created"))),
            "400" -> ReferenceOr.Value(Response(description = doc("Bad Request"))),
            "401" -> ReferenceOr.Value(Response(description = doc("Unauthorized"))),
            "404" -> ReferenceOr.Value(Response(description = doc("Not Found"))),
            "500" -> ReferenceOr.Value(Response(description = doc("Internal Server Error")))
          )
        )
      )
      assertTrue(
        operation.responses.responses.size == 6,
        operation.responses.responses.contains("200"),
        operation.responses.responses.contains("404"),
        operation.responses.responses.contains("500")
      )
    }
  )
}
