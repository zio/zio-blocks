package zio.blocks.openapi

import zio.blocks.docs.{Doc, Parser}
import zio.blocks.schema._
import zio.blocks.schema.json.Json
import zio.test._

object TagExternalDocsSpec extends SchemaBaseSpec {
  private def doc(s: String): Doc      = Parser.parse(s).toOption.get
  def spec: Spec[TestEnvironment, Any] = suite("Tag and ExternalDocumentation")(
    suite("Tag")(
      test("can be constructed with required name field only") {
        val tag = Tag(name = "users")

        assertTrue(
          tag.name == "users",
          tag.description.isEmpty,
          tag.externalDocs.isEmpty,
          tag.extensions.isEmpty
        )
      },
      test("can be constructed with all fields") {
        val externalDocs = ExternalDocumentation(
          url = "https://docs.example.com/users",
          description = Some(doc("User documentation"))
        )
        val extensions = Map(
          "x-custom"   -> Json.String("value"),
          "x-priority" -> Json.Number(1)
        )
        val tag = Tag(
          name = "users",
          description = Some(doc("User operations")),
          externalDocs = Some(externalDocs),
          extensions = extensions
        )

        assertTrue(
          tag.name == "users",
          tag.description.contains(doc("User operations")),
          tag.externalDocs.isDefined,
          tag.externalDocs.exists(_.url == "https://docs.example.com/users"),
          tag.extensions.size == 2,
          tag.extensions.get("x-custom").contains(Json.String("value")),
          tag.extensions.get("x-priority").contains(Json.Number(1))
        )
      },
      test("preserves extensions on construction") {
        val extensions = Map(
          "x-display-name" -> Json.String("User Management"),
          "x-icon"         -> Json.String("user-icon"),
          "x-order"        -> Json.Number(5)
        )
        val tag = Tag(name = "users", extensions = extensions)

        assertTrue(
          tag.extensions.size == 3,
          tag.extensions.get("x-display-name").contains(Json.String("User Management")),
          tag.extensions.get("x-icon").contains(Json.String("user-icon")),
          tag.extensions.get("x-order").contains(Json.Number(5))
        )
      },
      test("Schema[Tag] can be derived") {
        val tag    = Tag(name = "users")
        val schema = Schema[Tag]

        assertTrue(schema != null, tag != null)
      },
      test("Tag round-trips through DynamicValue with minimal fields") {
        val tag = Tag(name = "users")

        val dv     = Schema[Tag].toDynamicValue(tag)
        val result = Schema[Tag].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists(_.name == "users"),
          result.exists(_.description.isEmpty),
          result.exists(_.externalDocs.isEmpty),
          result.exists(_.extensions.isEmpty)
        )
      },
      test("Tag round-trips through DynamicValue with all fields") {
        val externalDocs = ExternalDocumentation(
          url = "https://docs.example.com/users",
          description = Some(doc("User API docs"))
        )
        val extensions = Map(
          "x-custom" -> Json.String("test"),
          "x-value"  -> Json.Number(42)
        )
        val tag = Tag(
          name = "users",
          description = Some(doc("User operations")),
          externalDocs = Some(externalDocs),
          extensions = extensions
        )

        val dv     = Schema[Tag].toDynamicValue(tag)
        val result = Schema[Tag].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists(_.name == "users"),
          result.exists(_.description.contains(doc("User operations"))),
          result.exists(_.externalDocs.isDefined),
          result.exists(_.externalDocs.exists(_.url == "https://docs.example.com/users")),
          result.exists(_.externalDocs.exists(_.description.contains(doc("User API docs")))),
          result.exists(_.extensions.size == 2),
          result.exists(_.extensions.contains("x-custom")),
          result.exists(_.extensions.contains("x-value"))
        )
      },
      test("Tag preserves nested ExternalDocumentation with extensions") {
        val externalDocs = ExternalDocumentation(
          url = "https://example.com",
          description = Some(doc("Docs")),
          extensions = Map("x-doc-version" -> Json.String("v1"))
        )
        val tag = Tag(
          name = "test",
          externalDocs = Some(externalDocs),
          extensions = Map("x-tag-level" -> Json.String("top"))
        )

        val dv     = Schema[Tag].toDynamicValue(tag)
        val result = Schema[Tag].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists(_.externalDocs.exists(_.extensions.contains("x-doc-version"))),
          result.exists(_.extensions.contains("x-tag-level"))
        )
      }
    ),
    suite("ExternalDocumentation")(
      test("can be constructed with required url field only") {
        val docs = ExternalDocumentation(url = "https://docs.example.com")

        assertTrue(
          docs.url == "https://docs.example.com",
          docs.description.isEmpty,
          docs.extensions.isEmpty
        )
      },
      test("can be constructed with all fields") {
        val extensions = Map(
          "x-language" -> Json.String("en"),
          "x-version"  -> Json.String("2.0")
        )
        val docs = ExternalDocumentation(
          url = "https://docs.example.com/api",
          description = Some(doc("Complete API documentation")),
          extensions = extensions
        )

        assertTrue(
          docs.url == "https://docs.example.com/api",
          docs.description.contains(doc("Complete API documentation")),
          docs.extensions.size == 2,
          docs.extensions.get("x-language").contains(Json.String("en")),
          docs.extensions.get("x-version").contains(Json.String("2.0"))
        )
      },
      test("preserves extensions on construction") {
        val extensions = Map(
          "x-format" -> Json.String("swagger-ui"),
          "x-public" -> Json.Boolean(true),
          "x-rating" -> Json.Number(4.5)
        )
        val docs = ExternalDocumentation(
          url = "https://example.com",
          extensions = extensions
        )

        assertTrue(
          docs.extensions.size == 3,
          docs.extensions.get("x-format").contains(Json.String("swagger-ui")),
          docs.extensions.get("x-public").contains(Json.Boolean(true)),
          docs.extensions.get("x-rating").contains(Json.Number(4.5))
        )
      },
      test("Schema[ExternalDocumentation] can be derived") {
        val docs   = ExternalDocumentation(url = "https://example.com")
        val schema = Schema[ExternalDocumentation]

        assertTrue(schema != null, docs != null)
      },
      test("ExternalDocumentation round-trips through DynamicValue with minimal fields") {
        val docs = ExternalDocumentation(url = "https://docs.example.com")

        val dv     = Schema[ExternalDocumentation].toDynamicValue(docs)
        val result = Schema[ExternalDocumentation].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists(_.url == "https://docs.example.com"),
          result.exists(_.description.isEmpty),
          result.exists(_.extensions.isEmpty)
        )
      },
      test("ExternalDocumentation round-trips through DynamicValue with all fields") {
        val extensions = Map(
          "x-custom" -> Json.String("value"),
          "x-number" -> Json.Number(123)
        )
        val docs = ExternalDocumentation(
          url = "https://docs.example.com/api/v2",
          description = Some(doc("API Reference Documentation")),
          extensions = extensions
        )

        val dv     = Schema[ExternalDocumentation].toDynamicValue(docs)
        val result = Schema[ExternalDocumentation].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists(_.url == "https://docs.example.com/api/v2"),
          result.exists(_.description.contains(doc("API Reference Documentation"))),
          result.exists(_.extensions.size == 2),
          result.exists(_.extensions.contains("x-custom")),
          result.exists(_.extensions.contains("x-number"))
        )
      },
      test("ExternalDocumentation handles different URL formats") {
        val urls = List(
          "https://example.com",
          "http://docs.example.com/path",
          "https://api.example.com/v1/docs#section",
          "https://example.com/docs?version=latest"
        )

        val results = urls.map { url =>
          val docs   = ExternalDocumentation(url = url)
          val dv     = Schema[ExternalDocumentation].toDynamicValue(docs)
          val result = Schema[ExternalDocumentation].fromDynamicValue(dv)
          result.exists(_.url == url)
        }

        assertTrue(results.forall(identity))
      }
    ),
    suite("Integration")(
      test("Tag can contain ExternalDocumentation and both preserve extensions") {
        val docExtensions = Map("x-doc-lang" -> Json.String("en"))
        val tagExtensions = Map("x-tag-group" -> Json.String("core"))

        val docs = ExternalDocumentation(
          url = "https://example.com",
          description = Some(doc("Documentation")),
          extensions = docExtensions
        )
        val tag = Tag(
          name = "api",
          description = Some(doc("API operations")),
          externalDocs = Some(docs),
          extensions = tagExtensions
        )

        val dv     = Schema[Tag].toDynamicValue(tag)
        val result = Schema[Tag].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists(_.name == "api"),
          result.exists(_.externalDocs.isDefined),
          result.exists(_.externalDocs.exists(_.url == "https://example.com")),
          result.exists(_.externalDocs.exists(_.extensions.contains("x-doc-lang"))),
          result.exists(_.extensions.contains("x-tag-group"))
        )
      }
    )
  )
}
