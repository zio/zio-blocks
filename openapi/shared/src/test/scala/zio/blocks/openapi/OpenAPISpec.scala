package zio.blocks.openapi

import zio.blocks.schema._
import zio.blocks.schema.json.Json
import zio.test._

object OpenAPISpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("OpenAPI")(
    test("can be constructed with required fields only") {
      val info = Info(title = "Test API", version = "1.0.0")
      val api  = OpenAPI(openapi = "3.1.0", info = info)

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
      val info       = Info(title = "Test API", version = "1.0.0")
      val extensions = Map("x-custom" -> Json.String("value"), "x-number" -> Json.Number(42))
      val api        = OpenAPI(openapi = "3.1.0", info = info, extensions = extensions)

      assertTrue(
        api.extensions.size == 2,
        api.extensions.get("x-custom").contains(Json.String("value")),
        api.extensions.get("x-number").contains(Json.Number(42))
      )
    },
    test("Schema[OpenAPI] can be derived") {
      val info   = Info(title = "Test API", version = "1.0.0")
      val api    = OpenAPI(openapi = "3.1.0", info = info)
      val schema = Schema[OpenAPI]

      assertTrue(schema != null, api != null)
    },
    test("OpenAPI round-trips through DynamicValue") {
      val info = Info(title = "Test API", version = "1.0.0")
      val api  = OpenAPI(
        openapi = "3.1.0",
        info = info,
        jsonSchemaDialect = Some("https://spec.openapis.org/oas/3.1/dialect/base"),
        extensions = Map("x-custom" -> Json.String("test"))
      )

      val dv     = Schema[OpenAPI].toDynamicValue(api)
      val result = Schema[OpenAPI].fromDynamicValue(dv)

      assertTrue(
        result.isRight,
        result.exists(_.openapi == "3.1.0"),
        result.exists(_.info.title == "Test API"),
        result.exists(_.jsonSchemaDialect.contains("https://spec.openapis.org/oas/3.1/dialect/base")),
        result.exists(_.extensions.contains("x-custom"))
      )
    },
    test("OpenAPI with all optional fields populated") {
      val info         = Info(title = "Test API", version = "1.0.0")
      val server       = Server(url = "https://api.example.com")
      val paths        = Paths(Map.empty)
      val components   = Components()
      val security     = List(SecurityRequirement(Map.empty))
      val tag          = Tag(name = "test")
      val externalDocs = ExternalDocumentation(url = "https://docs.example.com")
      val api          = OpenAPI(
        openapi = "3.1.0",
        info = info,
        jsonSchemaDialect = Some("https://spec.openapis.org/oas/3.1/dialect/base"),
        servers = List(server),
        paths = Some(paths),
        components = Some(components),
        security = security,
        tags = List(tag),
        externalDocs = Some(externalDocs),
        extensions = Map("x-custom" -> Json.String("value"))
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
    }
  )
}
