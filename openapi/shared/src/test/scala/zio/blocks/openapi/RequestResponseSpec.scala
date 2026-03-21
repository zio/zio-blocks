package zio.blocks.openapi

import zio.blocks.chunk.{Chunk, ChunkMap}
import zio.blocks.docs.{Doc, Inline, Paragraph}
import zio.blocks.schema._
import zio.blocks.schema.json.Json
import zio.test._

object RequestResponseSpec extends SchemaBaseSpec {
  private def doc(s: String): Doc      = Doc(Chunk.single(Paragraph(Chunk.single(Inline.Text(s)))))
  def spec: Spec[TestEnvironment, Any] = suite("Request and Response Types")(
    suite("RequestBody")(
      test("can be constructed with required content field only") {
        val requestBody = RequestBody(content = ChunkMap("application/json" -> MediaType()))
        assertTrue(
          requestBody.content.size == 1,
          requestBody.description.isEmpty,
          !requestBody.required,
          requestBody.extensions.isEmpty
        )
      },
      test("can be constructed with all fields populated") {
        val requestBody = RequestBody(
          content = ChunkMap(
            "application/json" -> MediaType(
              schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("object")))))
            ),
            "application/xml" -> MediaType()
          ),
          description = Some(doc("Request payload")),
          required = true,
          extensions = ChunkMap("x-custom" -> Json.String("value"))
        )
        assertTrue(
          requestBody.content.size == 2,
          requestBody.description.contains(doc("Request payload")),
          requestBody.required,
          requestBody.extensions.size == 1
        )
      },
      test("preserves extensions on construction") {
        val requestBody = RequestBody(
          content = ChunkMap("text/plain" -> MediaType()),
          extensions = ChunkMap("x-internal" -> Json.Boolean(true), "x-version" -> Json.Number(2))
        )
        assertTrue(
          requestBody.extensions.size == 2,
          requestBody.extensions.get("x-internal").contains(Json.Boolean(true)),
          requestBody.extensions.get("x-version").contains(Json.Number(2))
        )
      },
      test("Schema[RequestBody] can be derived") {
        val requestBody = RequestBody(content = ChunkMap("application/json" -> MediaType()))
        val schema      = Schema[RequestBody]
        assertTrue(schema != null, requestBody != null)
      },
      test("RequestBody round-trips through DynamicValue") {
        val requestBody = RequestBody(
          content = ChunkMap("application/json" -> MediaType()),
          description = Some(doc("Test body")),
          required = true,
          extensions = ChunkMap("x-test" -> Json.String("value"))
        )
        val result = Schema[RequestBody].fromDynamicValue(Schema[RequestBody].toDynamicValue(requestBody))
        assertTrue(
          result.isRight,
          result.exists(_.content.nonEmpty),
          result.exists(_.description.contains(doc("Test body"))),
          result.exists(_.required),
          result.exists(_.extensions.nonEmpty)
        )
      }
    ),
    suite("MediaType")(
      test("can be constructed with no fields") {
        val mediaType = MediaType()
        assertTrue(
          mediaType.schema.isEmpty,
          mediaType.example.isEmpty,
          mediaType.examples.isEmpty,
          mediaType.encoding.isEmpty,
          mediaType.extensions.isEmpty
        )
      },
      test("can be constructed with all fields populated") {
        val mediaType = MediaType(
          schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("object"))))),
          example = Some(Json.Object("name" -> Json.String("John"))),
          examples = ChunkMap(
            "example1" -> ReferenceOr.Value(Example(summary = Some(doc("First example")), value = Some(Json.Number(1))))
          ),
          encoding = ChunkMap("profileImage" -> Encoding(contentType = Some("image/png"))),
          extensions = ChunkMap("x-custom" -> Json.String("value"))
        )
        assertTrue(
          mediaType.schema.isDefined,
          mediaType.example.isDefined,
          mediaType.examples.size == 1,
          mediaType.encoding.size == 1,
          mediaType.extensions.size == 1
        )
      },
      test("preserves encoding definitions") {
        val mediaType = MediaType(encoding =
          ChunkMap(
            "file" -> Encoding(
              contentType = Some("application/octet-stream"),
              headers = ChunkMap("X-File-Type" -> ReferenceOr.Value(Header()))
            ),
            "metadata" -> Encoding(contentType = Some("application/json"))
          )
        )
        assertTrue(
          mediaType.encoding.size == 2,
          mediaType.encoding.contains("file"),
          mediaType.encoding.contains("metadata")
        )
      },
      test("Schema[MediaType] can be derived") {
        val mediaType = MediaType()
        val schema    = Schema[MediaType]
        assertTrue(schema != null, mediaType != null)
      },
      test("MediaType round-trips through DynamicValue") {
        val mediaType = MediaType(
          schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("string"))))),
          example = Some(Json.String("test")),
          examples = ChunkMap.empty,
          encoding = ChunkMap.empty,
          extensions = ChunkMap("x-test" -> Json.Boolean(true))
        )
        val result = Schema[MediaType].fromDynamicValue(Schema[MediaType].toDynamicValue(mediaType))
        assertTrue(
          result.isRight,
          result.exists(_.schema.isDefined),
          result.exists(_.example.isDefined),
          result.exists(_.extensions.nonEmpty)
        )
      }
    ),
    suite("Encoding")(
      test("can be constructed with no fields") {
        val encoding = Encoding()
        assertTrue(
          encoding.contentType.isEmpty,
          encoding.headers.isEmpty,
          encoding.style.isEmpty,
          encoding.explode.isEmpty,
          !encoding.allowReserved,
          encoding.extensions.isEmpty
        )
      },
      test("can be constructed with all fields populated") {
        val encoding = Encoding(
          contentType = Some("image/jpeg"),
          headers = ChunkMap("X-Rate-Limit" -> ReferenceOr.Value(Header(description = Some(doc("Rate limit info"))))),
          style = Some("form"),
          explode = Some(true),
          allowReserved = true,
          extensions = ChunkMap("x-custom" -> Json.String("value"))
        )
        assertTrue(
          encoding.contentType.contains("image/jpeg"),
          encoding.headers.size == 1,
          encoding.style.contains("form"),
          encoding.explode.contains(true),
          encoding.allowReserved,
          encoding.extensions.size == 1
        )
      },
      test("preserves multiple headers") {
        val encoding = Encoding(headers =
          ChunkMap(
            "X-Custom-1" -> ReferenceOr.Value(Header()),
            "X-Custom-2" -> ReferenceOr.Value(Header()),
            "X-Custom-3" -> ReferenceOr.Ref(Reference(`$ref` = "#/components/headers/CommonHeader"))
          )
        )
        assertTrue(
          encoding.headers.size == 3,
          encoding.headers.contains("X-Custom-1"),
          encoding.headers.contains("X-Custom-2"),
          encoding.headers.contains("X-Custom-3")
        )
      },
      test("Schema[Encoding] can be derived") {
        val encoding = Encoding()
        val schema   = Schema[Encoding]
        assertTrue(schema != null, encoding != null)
      },
      test("Encoding round-trips through DynamicValue") {
        val encoding = Encoding(
          contentType = Some("application/json"),
          headers = ChunkMap.empty,
          style = Some("form"),
          explode = Some(false),
          allowReserved = true,
          extensions = ChunkMap("x-test" -> Json.Number(1))
        )
        val result = Schema[Encoding].fromDynamicValue(Schema[Encoding].toDynamicValue(encoding))
        assertTrue(
          result.isRight,
          result.exists(_.contentType.contains("application/json")),
          result.exists(_.style.contains("form")),
          result.exists(_.explode.contains(false)),
          result.exists(_.allowReserved),
          result.exists(_.extensions.nonEmpty)
        )
      }
    ),
    suite("Responses")(
      test("can be constructed with empty responses") {
        val responses = Responses()
        assertTrue(
          responses.responses.isEmpty,
          responses.default.isEmpty,
          responses.extensions.isEmpty
        )
      },
      test("can be constructed with all fields populated") {
        val responses = Responses(
          responses = ChunkMap(
            "200" -> ReferenceOr.Value(Response(description = doc("Success"))),
            "404" -> ReferenceOr.Value(Response(description = doc("Not found")))
          ),
          default = Some(ReferenceOr.Value(Response(description = doc("Default response")))),
          extensions = ChunkMap("x-custom" -> Json.String("value"))
        )
        assertTrue(
          responses.responses.size == 2,
          responses.default.isDefined,
          responses.extensions.size == 1
        )
      },
      test("preserves multiple status codes") {
        val responsesMap = ChunkMap(
          "200" -> ReferenceOr.Value(Response(description = doc("OK"))),
          "201" -> ReferenceOr.Value(Response(description = doc("Created"))),
          "400" -> ReferenceOr.Value(Response(description = doc("Bad Request"))),
          "500" -> ReferenceOr.Value(Response(description = doc("Internal Server Error")))
        )
        val responses = Responses(responses = responsesMap)
        assertTrue(
          responses.responses.size == 4,
          responses.responses.contains("200"),
          responses.responses.contains("201"),
          responses.responses.contains("400"),
          responses.responses.contains("500")
        )
      },
      test("supports reference to response") {
        val responsesMap = ChunkMap(
          "200" -> ReferenceOr.Ref(Reference(`$ref` = "#/components/responses/SuccessResponse"))
        )
        val responses = Responses(responses = responsesMap)
        assertTrue(
          responses.responses.size == 1,
          responses.responses.contains("200")
        )
      },
      test("Schema[Responses] can be derived") {
        val responses = Responses()
        val schema    = Schema[Responses]
        assertTrue(schema != null, responses != null)
      },
      test("Responses round-trips through DynamicValue") {
        val responses = Responses(
          responses = ChunkMap("200" -> ReferenceOr.Value(Response(description = doc("OK")))),
          default = Some(ReferenceOr.Value(Response(description = doc("Error")))),
          extensions = ChunkMap("x-test" -> Json.Boolean(false))
        )
        val result = Schema[Responses].fromDynamicValue(Schema[Responses].toDynamicValue(responses))
        assertTrue(
          result.isRight,
          result.exists(_.responses.nonEmpty),
          result.exists(_.default.isDefined),
          result.exists(_.extensions.nonEmpty)
        )
      }
    ),
    suite("Response")(
      test("can be constructed with required description only") {
        val response = Response(description = doc("Success"))
        assertTrue(
          response.description == doc("Success"),
          response.headers.isEmpty,
          response.content.isEmpty,
          response.links.isEmpty,
          response.extensions.isEmpty
        )
      },
      test("can be constructed with all fields populated") {
        val response = Response(
          description = doc("Successful response"),
          headers = ChunkMap("X-Rate-Limit" -> ReferenceOr.Value(Header())),
          content = ChunkMap("application/json" -> MediaType()),
          links = ChunkMap("GetUserById" -> ReferenceOr.Value(Link(operationId = Some("getUserById")))),
          extensions = ChunkMap("x-custom" -> Json.String("value"))
        )
        assertTrue(
          response.description == doc("Successful response"),
          response.headers.size == 1,
          response.content.size == 1,
          response.links.size == 1,
          response.extensions.size == 1
        )
      },
      test("preserves multiple content types") {
        val content = ChunkMap(
          "application/json" -> MediaType(schema =
            Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("object")))))
          ),
          "application/xml" -> MediaType(),
          "text/plain"      -> MediaType()
        )
        val response = Response(description = doc("Multi-format response"), content = content)
        assertTrue(
          response.content.size == 3,
          response.content.contains("application/json"),
          response.content.contains("application/xml"),
          response.content.contains("text/plain")
        )
      },
      test("Schema[Response] can be derived") {
        val response = Response(description = doc("Test"))
        val schema   = Schema[Response]
        assertTrue(schema != null, response != null)
      },
      test("Response round-trips through DynamicValue") {
        val response = Response(
          description = doc("Success"),
          headers = ChunkMap("X-Test" -> ReferenceOr.Value(Header())),
          content = ChunkMap("application/json" -> MediaType()),
          links = ChunkMap.empty,
          extensions = ChunkMap("x-test" -> Json.String("value"))
        )
        val result = Schema[Response].fromDynamicValue(Schema[Response].toDynamicValue(response))
        assertTrue(
          result.isRight,
          result.exists(_.description == doc("Success")),
          result.exists(_.headers.nonEmpty),
          result.exists(_.content.nonEmpty),
          result.exists(_.extensions.nonEmpty)
        )
      }
    ),
    suite("Example")(
      test("can be constructed with no fields") {
        val example = Example()
        assertTrue(
          example.summary.isEmpty,
          example.description.isEmpty,
          example.value.isEmpty,
          example.externalValue.isEmpty,
          example.extensions.isEmpty
        )
      },
      test("can be constructed with value field") {
        val example = Example(
          summary = Some(doc("User example")),
          description = Some(doc("A sample user object")),
          value = Some(Json.Object("name" -> Json.String("John"), "age" -> Json.Number(30)))
        )
        assertTrue(
          example.summary.contains(doc("User example")),
          example.description.contains(doc("A sample user object")),
          example.value.isDefined,
          example.externalValue.isEmpty
        )
      },
      test("can be constructed with externalValue field") {
        val example = Example(
          summary = Some(doc("External example")),
          externalValue = Some("https://example.com/examples/user.json")
        )
        assertTrue(
          example.summary.contains(doc("External example")),
          example.value.isEmpty,
          example.externalValue.contains("https://example.com/examples/user.json")
        )
      },
      test("value and externalValue are mutually exclusive") {
        val result = scala.util.Try {
          Example(
            value = Some(Json.String("test")),
            externalValue = Some("https://example.com/test.json")
          )
        }
        assertTrue(
          result.isFailure,
          result.failed.get.getMessage.contains("mutually exclusive")
        )
      },
      test("both value and externalValue can be None") {
        val example = Example(summary = Some(doc("Empty example")))
        assertTrue(
          example.value.isEmpty,
          example.externalValue.isEmpty
        )
      },
      test("preserves extensions") {
        val example = Example(
          value = Some(Json.String("test")),
          extensions = ChunkMap("x-internal" -> Json.Boolean(true), "x-version" -> Json.Number(1))
        )
        assertTrue(
          example.extensions.size == 2,
          example.extensions.get("x-internal").contains(Json.Boolean(true)),
          example.extensions.get("x-version").contains(Json.Number(1))
        )
      },
      test("Schema[Example] can be derived") {
        val example = Example()
        val schema  = Schema[Example]
        assertTrue(schema != null, example != null)
      },
      test("Example round-trips through DynamicValue") {
        val example = Example(
          summary = Some(doc("Test")),
          description = Some(doc("A test example")),
          value = Some(Json.Number(42)),
          extensions = ChunkMap("x-test" -> Json.Boolean(true))
        )
        val result = Schema[Example].fromDynamicValue(Schema[Example].toDynamicValue(example))
        assertTrue(
          result.isRight,
          result.exists(_.summary.contains(doc("Test"))),
          result.exists(_.description.contains(doc("A test example"))),
          result.exists(_.value.isDefined),
          result.exists(_.extensions.nonEmpty)
        )
      },
      test("Example minimal round-trip exercises private constructor defaults") {
        val example = Example()
        val result  = Schema[Example].fromDynamicValue(Schema[Example].toDynamicValue(example))
        assertTrue(
          result.isRight,
          result.exists(_.summary.isEmpty),
          result.exists(_.description.isEmpty),
          result.exists(_.value.isEmpty),
          result.exists(_.externalValue.isEmpty),
          result.exists(_.extensions.isEmpty)
        )
      }
    ),
    suite("Link")(
      test("can be constructed with no fields") {
        val link = Link()
        assertTrue(
          link.operationRef.isEmpty,
          link.operationId.isEmpty,
          link.parameters.isEmpty,
          link.requestBody.isEmpty,
          link.description.isEmpty,
          link.server.isEmpty,
          link.extensions.isEmpty
        )
      },
      test("can be constructed with operationRef") {
        val link = Link(
          operationRef = Some("#/paths/~1users~1{userId}/get"),
          description = Some(doc("Link to user by ID"))
        )
        assertTrue(
          link.operationRef.contains("#/paths/~1users~1{userId}/get"),
          link.description.contains(doc("Link to user by ID"))
        )
      },
      test("can be constructed with operationId") {
        val link = Link(
          operationId = Some("getUserById"),
          parameters = ChunkMap("userId" -> Json.String("$response.body#/id"))
        )
        assertTrue(
          link.operationId.contains("getUserById"),
          link.parameters.size == 1
        )
      },
      test("operationRef and operationId are mutually exclusive") {
        val result = scala.util.Try {
          Link(
            operationRef = Some("#/paths/~1users/get"),
            operationId = Some("getUsers"),
            parameters = ChunkMap.empty,
            requestBody = None,
            description = None,
            server = None,
            extensions = ChunkMap.empty
          )
        }
        assertTrue(
          result.isFailure,
          result.failed.get.getMessage.contains("mutually exclusive")
        )
      },
      test("both operationRef and operationId can be None") {
        val link = Link(description = Some(doc("Some link")))
        assertTrue(
          link.operationRef.isEmpty,
          link.operationId.isEmpty
        )
      },
      test("can include server and requestBody") {
        val server      = Server(url = "https://api.example.com")
        val requestBody = Json.Object("userId" -> Json.String("$response.body#/id"))
        val link        = Link(
          operationId = Some("createUser"),
          requestBody = Some(requestBody),
          server = Some(server)
        )
        assertTrue(
          link.requestBody.isDefined,
          link.server.isDefined
        )
      },
      test("preserves multiple parameters") {
        val link = Link(
          operationId = Some("getUser"),
          parameters = ChunkMap(
            "userId" -> Json.String("$response.body#/id"),
            "format" -> Json.String("json"),
            "limit"  -> Json.Number(10)
          )
        )
        assertTrue(
          link.parameters.size == 3,
          link.parameters.contains("userId"),
          link.parameters.contains("format"),
          link.parameters.contains("limit")
        )
      },
      test("preserves extensions") {
        val extensions = ChunkMap("x-internal" -> Json.Boolean(false), "x-rate-limit" -> Json.Number(100))
        val link       = Link(operationId = Some("test"), extensions = extensions)
        assertTrue(
          link.extensions.size == 2,
          link.extensions.get("x-internal").contains(Json.Boolean(false)),
          link.extensions.get("x-rate-limit").contains(Json.Number(100))
        )
      },
      test("Schema[Link] can be derived") {
        val link   = Link()
        val schema = Schema[Link]
        assertTrue(schema != null, link != null)
      },
      test("Link round-trips through DynamicValue") {
        val link = Link(
          operationId = Some("getUser"),
          parameters = ChunkMap("id" -> Json.Number(1)),
          description = Some(doc("Get user link")),
          extensions = ChunkMap("x-test" -> Json.String("value"))
        )
        val result = Schema[Link].fromDynamicValue(Schema[Link].toDynamicValue(link))
        assertTrue(
          result.isRight,
          result.exists(_.operationRef.isEmpty),
          result.exists(_.operationId.contains("getUser")),
          result.exists(_.parameters.nonEmpty),
          result.exists(_.description.contains(doc("Get user link"))),
          result.exists(_.extensions.nonEmpty)
        )
      },
      test("Link minimal round-trip exercises private constructor defaults") {
        val link   = Link()
        val result = Schema[Link].fromDynamicValue(Schema[Link].toDynamicValue(link))
        assertTrue(
          result.isRight,
          result.exists(_.operationRef.isEmpty),
          result.exists(_.operationId.isEmpty),
          result.exists(_.parameters.isEmpty),
          result.exists(_.requestBody.isEmpty),
          result.exists(_.description.isEmpty),
          result.exists(_.server.isEmpty),
          result.exists(_.extensions.isEmpty)
        )
      }
    ),
    suite("Callback")(
      test("can be constructed with empty callbacks") {
        val callback = Callback()
        assertTrue(
          callback.callbacks.isEmpty,
          callback.extensions.isEmpty
        )
      },
      test("can be constructed with callbacks") {
        val pathItem = PathItem(summary = Some(doc("Callback path")))
        val callback = Callback(callbacks = ChunkMap("{$request.body#/callbackUrl}" -> ReferenceOr.Value(pathItem)))
        assertTrue(
          callback.callbacks.size == 1,
          callback.callbacks.contains("{$request.body#/callbackUrl}")
        )
      },
      test("supports multiple callback expressions") {
        val callbacks = ChunkMap(
          "{$request.body#/webhookUrl1}" -> ReferenceOr.Value(PathItem(summary = Some(doc("Webhook 1")))),
          "{$request.body#/webhookUrl2}" -> ReferenceOr.Value(PathItem(summary = Some(doc("Webhook 2"))))
        )
        val callback = Callback(callbacks = callbacks)
        assertTrue(
          callback.callbacks.size == 2,
          callback.callbacks.contains("{$request.body#/webhookUrl1}"),
          callback.callbacks.contains("{$request.body#/webhookUrl2}")
        )
      },
      test("supports reference to path item") {
        val callbacks = ChunkMap(
          "{$request.body#/callbackUrl}" -> ReferenceOr.Ref(Reference(`$ref` = "#/components/pathItems/WebhookPath"))
        )
        val callback = Callback(callbacks = callbacks)
        assertTrue(callback.callbacks.size == 1)
      },
      test("preserves extensions") {
        val extensions = ChunkMap("x-webhook-type" -> Json.String("async"), "x-priority" -> Json.Number(1))
        val callback   = Callback(extensions = extensions)
        assertTrue(
          callback.extensions.size == 2,
          callback.extensions.get("x-webhook-type").contains(Json.String("async")),
          callback.extensions.get("x-priority").contains(Json.Number(1))
        )
      },
      test("Schema[Callback] can be derived") {
        val callback = Callback()
        val schema   = Schema[Callback]
        assertTrue(schema != null, callback != null)
      },
      test("Callback round-trips through DynamicValue") {
        val callback = Callback(
          callbacks = ChunkMap("{$request.body#/url}" -> ReferenceOr.Value(PathItem())),
          extensions = ChunkMap("x-test" -> Json.Boolean(true))
        )
        val result = Schema[Callback].fromDynamicValue(Schema[Callback].toDynamicValue(callback))
        assertTrue(
          result.isRight,
          result.exists(_.callbacks.nonEmpty),
          result.exists(_.extensions.nonEmpty)
        )
      }
    )
  )
}
