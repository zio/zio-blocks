package zio.blocks.openapi

import zio.blocks.chunk.{Chunk, ChunkMap}
import zio.blocks.schema._
import zio.blocks.schema.json.Json
import zio.test._

object OpenAPISpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("OpenAPI")(
    test("can be constructed with required fields only") {
      val api = OpenAPI(openapi = "3.1.0", info = Info(title = "Test API", version = "1.0.0"))
      assertTrue(
        api.openapi == "3.1.0",
        api.info.title == "Test API",
        api.info.version == "1.0.0",
        api.jsonSchemaDialect.isEmpty,
        api.servers.isEmpty,
        api.paths.isEmpty,
        api.components.isEmpty,
        api.security.isEmpty,
        api.tags.isEmpty,
        api.externalDocs.isEmpty,
        api.extensions.isEmpty
      )
    },
    test("preserves extensions on construction") {
      val api = OpenAPI(
        openapi = "3.1.0",
        info = Info(title = "Test API", version = "1.0.0"),
        extensions = ChunkMap("x-custom" -> Json.String("value"), "x-number" -> Json.Number(42))
      )
      assertTrue(
        api.extensions.size == 2,
        api.extensions.get("x-custom").contains(Json.String("value")),
        api.extensions.get("x-number").contains(Json.Number(42))
      )
    },
    test("Schema[OpenAPI] can be derived") {
      val api    = OpenAPI(openapi = "3.1.0", info = Info(title = "Test API", version = "1.0.0"))
      val schema = Schema[OpenAPI]
      assertTrue(schema != null, api != null)
    },
    test("OpenAPI round-trips through DynamicValue") {
      val api = OpenAPI(
        openapi = "3.1.0",
        info = Info(title = "Test API", version = "1.0.0"),
        jsonSchemaDialect = Some("https://spec.openapis.org/oas/3.1/dialect/base"),
        extensions = ChunkMap("x-custom" -> Json.String("test"))
      )
      val result = Schema[OpenAPI].fromDynamicValue(Schema[OpenAPI].toDynamicValue(api))
      assertTrue(
        result.isRight,
        result.exists(_.openapi == "3.1.0"),
        result.exists(_.info.title == "Test API"),
        result.exists(_.jsonSchemaDialect.contains("https://spec.openapis.org/oas/3.1/dialect/base")),
        result.exists(_.extensions.contains("x-custom"))
      )
    },
    test("OpenAPI with all optional fields populated") {
      val api = OpenAPI(
        openapi = "3.1.0",
        info = Info(title = "Test API", version = "1.0.0"),
        jsonSchemaDialect = Some("https://spec.openapis.org/oas/3.1/dialect/base"),
        servers = Chunk(Server(url = "https://api.example.com")),
        paths = Some(Paths(ChunkMap.empty)),
        components = Some(Components()),
        security = Chunk(SecurityRequirement(ChunkMap.empty)),
        tags = Chunk(Tag(name = "test")),
        externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com")),
        extensions = ChunkMap("x-custom" -> Json.String("value"))
      )
      assertTrue(
        api.openapi == "3.1.0",
        api.servers.length == 1,
        api.paths.isDefined,
        api.components.isDefined,
        api.security.length == 1,
        api.tags.length == 1,
        api.externalDocs.isDefined,
        api.extensions.size == 1
      )
    },
    test("OpenAPI fully-populated round-trips through DynamicValue") {
      val api = OpenAPI(
        openapi = "3.1.0",
        info = Info(title = "Full API", version = "2.0.0"),
        jsonSchemaDialect = Some("https://spec.openapis.org/oas/3.1/dialect/base"),
        servers = Chunk(Server(url = "https://api.example.com")),
        paths = Some(Paths(ChunkMap.empty)),
        components = Some(Components()),
        security = Chunk(SecurityRequirement(ChunkMap("bearer" -> Chunk("read", "write")))),
        tags = Chunk(Tag(name = "users")),
        externalDocs = Some(ExternalDocumentation(url = "https://docs.example.com")),
        extensions = ChunkMap("x-custom" -> Json.String("value"))
      )
      val result = Schema[OpenAPI].fromDynamicValue(Schema[OpenAPI].toDynamicValue(api))
      assertTrue(
        result.isRight,
        result.exists(_.openapi == "3.1.0"),
        result.exists(_.info.title == "Full API"),
        result.exists(_.jsonSchemaDialect.isDefined),
        result.exists(_.servers.nonEmpty),
        result.exists(_.paths.isDefined),
        result.exists(_.components.isDefined),
        result.exists(_.security.nonEmpty),
        result.exists(_.tags.nonEmpty),
        result.exists(_.externalDocs.isDefined),
        result.exists(_.extensions.nonEmpty)
      )
    }
  )
}
