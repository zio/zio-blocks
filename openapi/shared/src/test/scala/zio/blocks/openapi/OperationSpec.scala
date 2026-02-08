package zio.blocks.openapi

import zio.blocks.docs.{Doc, Parser}
import zio.blocks.schema._
import zio.blocks.schema.json.Json
import zio.test._

object OperationSpec extends SchemaBaseSpec {
  private def doc(s: String): Doc      = Parser.parse(s).toOption.get
  def spec: Spec[TestEnvironment, Any] = suite("Operation")(
    test("can be constructed with required fields only") {
      val responses = Json.Object("200" -> Json.String("OK"))
      val operation = Operation(responses = responses)

      assertTrue(
        operation.responses == responses,
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
      val responses = Json.Object(
        "200" -> Json.String("Success"),
        "404" -> Json.String("Not Found")
      )
      val tags         = List("users", "admin")
      val summary      = doc("Get user by ID")
      val description  = doc("Retrieves a user from the database by their unique identifier")
      val externalDocs = ExternalDocumentation(
        url = "https://docs.example.com/users",
        description = Some(doc("User API Documentation"))
      )
      val operationId = "getUserById"
      val parameters  = List(
        Json.Object("name" -> Json.String("id"), "in" -> Json.String("path"))
      )
      val requestBody = Json.Object("required" -> Json.Boolean(true))
      val callbacks   = Map(
        "myCallback" -> Json.Object("url" -> Json.String("https://callback.example.com"))
      )
      val deprecated = true
      val security   = List(
        SecurityRequirement(Map("api_key" -> List("read:users")))
      )
      val servers = List(
        Server(url = "https://api.example.com/v1")
      )
      val extensions = Map(
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
    test("responses field is required (non-optional)") {
      val responses = Json.Object("200" -> Json.String("OK"))
      val operation = Operation(responses = responses)

      assertTrue(
        operation.responses == responses
      )
    },
    test("has separate summary and description fields") {
      val responses   = Json.Object("200" -> Json.String("OK"))
      val summary     = doc("Short summary")
      val description = doc("Long detailed description with lots of information")
      val operation   = Operation(
        responses = responses,
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
      val responses  = Json.Object("200" -> Json.String("OK"))
      val extensions = Map(
        "x-code-samples"     -> Json.Array(Json.String("sample1")),
        "x-visibility"       -> Json.String("public"),
        "x-deprecated-since" -> Json.String("2024-01-01")
      )
      val operation = Operation(responses = responses, extensions = extensions)

      assertTrue(
        operation.extensions.size == 3,
        operation.extensions.get("x-code-samples").isDefined,
        operation.extensions.get("x-visibility").contains(Json.String("public")),
        operation.extensions.get("x-deprecated-since").contains(Json.String("2024-01-01"))
      )
    },
    test("tags can be used for logical grouping") {
      val responses = Json.Object("200" -> Json.String("OK"))
      val tags      = List("pets", "store", "user")
      val operation = Operation(responses = responses, tags = tags)

      assertTrue(
        operation.tags.length == 3,
        operation.tags.contains("pets"),
        operation.tags.contains("store"),
        operation.tags.contains("user")
      )
    },
    test("operationId provides unique identifier") {
      val responses   = Json.Object("200" -> Json.String("OK"))
      val operationId = "listPets"
      val operation   = Operation(responses = responses, operationId = Some(operationId))

      assertTrue(
        operation.operationId.contains(operationId),
        operation.operationId.contains("listPets")
      )
    },
    test("deprecated field defaults to false") {
      val responses = Json.Object("200" -> Json.String("OK"))
      val operation = Operation(responses = responses)

      assertTrue(
        operation.deprecated == false
      )
    },
    test("deprecated field can be set to true") {
      val responses = Json.Object("200" -> Json.String("OK"))
      val operation = Operation(responses = responses, deprecated = true)

      assertTrue(
        operation.deprecated == true
      )
    },
    test("supports multiple security requirements") {
      val responses = Json.Object("200" -> Json.String("OK"))
      val security  = List(
        SecurityRequirement(Map("api_key" -> Nil)),
        SecurityRequirement(Map("oauth2" -> List("read", "write")))
      )
      val operation = Operation(responses = responses, security = security)

      assertTrue(
        operation.security.length == 2,
        operation.security(0).requirements.contains("api_key"),
        operation.security(1).requirements.contains("oauth2")
      )
    },
    test("supports alternative servers") {
      val responses = Json.Object("200" -> Json.String("OK"))
      val servers   = List(
        Server(url = "https://dev.example.com"),
        Server(url = "https://staging.example.com"),
        Server(url = "https://prod.example.com")
      )
      val operation = Operation(responses = responses, servers = servers)

      assertTrue(
        operation.servers.length == 3,
        operation.servers(0).url == "https://dev.example.com",
        operation.servers(1).url == "https://staging.example.com",
        operation.servers(2).url == "https://prod.example.com"
      )
    },
    test("supports external documentation") {
      val responses    = Json.Object("200" -> Json.String("OK"))
      val externalDocs = ExternalDocumentation(
        url = "https://example.com/docs/operation",
        description = Some(doc("Additional documentation for this operation"))
      )
      val operation = Operation(responses = responses, externalDocs = Some(externalDocs))

      assertTrue(
        operation.externalDocs.isDefined,
        operation.externalDocs.exists(_.url == "https://example.com/docs/operation"),
        operation.externalDocs.exists(_.description.contains(doc("Additional documentation for this operation")))
      )
    },
    test("parameters field uses Json placeholder") {
      val responses  = Json.Object("200" -> Json.String("OK"))
      val parameters = List(
        Json.Object("name" -> Json.String("id"), "in"    -> Json.String("path")),
        Json.Object("name" -> Json.String("limit"), "in" -> Json.String("query"))
      )
      val operation = Operation(responses = responses, parameters = parameters)

      assertTrue(
        operation.parameters.length == 2,
        operation.parameters(0).isInstanceOf[Json.Object],
        operation.parameters(1).isInstanceOf[Json.Object]
      )
    },
    test("requestBody field uses Json placeholder") {
      val responses   = Json.Object("200" -> Json.String("OK"))
      val requestBody = Json.Object(
        "required" -> Json.Boolean(true),
        "content"  -> Json.Object("application/json" -> Json.String("schema"))
      )
      val operation = Operation(responses = responses, requestBody = Some(requestBody))

      assertTrue(
        operation.requestBody.isDefined,
        operation.requestBody.exists(_.isInstanceOf[Json.Object])
      )
    },
    test("callbacks field uses Json placeholder") {
      val responses = Json.Object("200" -> Json.String("OK"))
      val callbacks = Map(
        "onData"  -> Json.Object("url" -> Json.String("https://callback.example.com/onData")),
        "onError" -> Json.Object("url" -> Json.String("https://callback.example.com/onError"))
      )
      val operation = Operation(responses = responses, callbacks = callbacks)

      assertTrue(
        operation.callbacks.size == 2,
        operation.callbacks.contains("onData"),
        operation.callbacks.contains("onError")
      )
    },
    test("Schema[Operation] can be derived") {
      val responses = Json.Object("200" -> Json.String("OK"))
      val operation = Operation(responses = responses)
      val schema    = Schema[Operation]

      assertTrue(schema != null, operation != null)
    },
    test("Operation round-trips through DynamicValue") {
      val responses = Json.Object(
        "200" -> Json.String("Success"),
        "400" -> Json.String("Bad Request")
      )
      val operation = Operation(
        responses = responses,
        tags = List("users", "api"),
        summary = Some(doc("List users")),
        description = Some(doc("Returns a list of all users in the system")),
        operationId = Some("listUsers"),
        deprecated = false,
        extensions = Map("x-internal" -> Json.Boolean(true))
      )

      val dv     = Schema[Operation].toDynamicValue(operation)
      val result = Schema[Operation].fromDynamicValue(dv)

      assertTrue(
        result.isRight,
        result.exists(_.responses == responses),
        result.exists(_.tags == List("users", "api")),
        result.exists(_.summary.contains(doc("List users"))),
        result.exists(_.description.contains(doc("Returns a list of all users in the system"))),
        result.exists(_.operationId.contains("listUsers")),
        result.exists(_.deprecated == false),
        result.exists(_.extensions.contains("x-internal"))
      )
    },
    test("Operation with minimal responses") {
      val responses = Json.String("placeholder")
      val operation = Operation(responses = responses)

      assertTrue(
        operation.responses == responses,
        operation.responses.isInstanceOf[Json.String]
      )
    },
    test("Operation supports complex nested structures") {
      val responses = Json.Object(
        "200" -> Json.Object(
          "description" -> Json.String("Success"),
          "content"     -> Json.Object(
            "application/json" -> Json.Object(
              "schema" -> Json.String("UserSchema")
            )
          )
        )
      )
      val operation = Operation(
        responses = responses,
        tags = List("complex"),
        summary = Some(doc("Complex operation")),
        parameters = List(
          Json.Object(
            "name"   -> Json.String("filter"),
            "in"     -> Json.String("query"),
            "schema" -> Json.Object("type" -> Json.String("string"))
          )
        )
      )

      assertTrue(
        operation.responses.isInstanceOf[Json.Object],
        operation.tags.contains("complex"),
        operation.parameters.nonEmpty
      )
    },
    test("Operation with empty collections uses defaults") {
      val responses = Json.Object("200" -> Json.String("OK"))
      val operation = Operation(responses = responses)

      assertTrue(
        operation.tags == Nil,
        operation.parameters == Nil,
        operation.callbacks == Map.empty[String, Json],
        operation.security == Nil,
        operation.servers == Nil,
        operation.extensions == Map.empty[String, Json]
      )
    },
    test("multiple responses with different status codes") {
      val responses = Json.Object(
        "200" -> Json.String("Success"),
        "201" -> Json.String("Created"),
        "400" -> Json.String("Bad Request"),
        "401" -> Json.String("Unauthorized"),
        "404" -> Json.String("Not Found"),
        "500" -> Json.String("Internal Server Error")
      )
      val operation = Operation(responses = responses)

      val responsesObj = operation.responses.asInstanceOf[Json.Object]
      val keys         = responsesObj.value.map(_._1)
      assertTrue(
        responsesObj.value.size == 6,
        keys.contains("200"),
        keys.contains("404"),
        keys.contains("500")
      )
    }
  )
}
