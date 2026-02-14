package zio.blocks.openapi

import zio.blocks.docs.{Doc, Parser}
import zio.blocks.schema._
import zio.blocks.schema.json.Json
import zio.test._

object ServerSpec extends SchemaBaseSpec {
  private def doc(s: String): Doc      = Parser.parse(s).toOption.get
  def spec: Spec[TestEnvironment, Any] = suite("Server and ServerVariable")(
    suite("Server")(
      test("can be constructed with required fields only") {
        val server = Server(url = "https://api.example.com")

        assertTrue(
          server.url == "https://api.example.com",
          server.description.isEmpty,
          server.variables.isEmpty,
          server.extensions.isEmpty
        )
      },
      test("can be constructed with all fields populated") {
        val variable = ServerVariable(
          default = "v1",
          `enum` = List("v1", "v2"),
          description = Some(doc("API version"))
        )
        val variables   = Map("version" -> variable)
        val extensions  = Map("x-custom" -> Json.String("value"))
        val description = doc("Production API server")
        val server      = Server(
          url = "https://api.example.com/{version}",
          description = Some(description),
          variables = variables,
          extensions = extensions
        )

        assertTrue(
          server.url == "https://api.example.com/{version}",
          server.description.contains(description),
          server.variables.size == 1,
          server.variables.contains("version"),
          server.extensions.size == 1,
          server.extensions.get("x-custom").contains(Json.String("value"))
        )
      },
      test("preserves extensions on construction") {
        val extensions = Map(
          "x-internal"   -> Json.Boolean(true),
          "x-rate-limit" -> Json.Number(1000)
        )
        val server = Server(url = "https://api.example.com", extensions = extensions)

        assertTrue(
          server.extensions.size == 2,
          server.extensions.get("x-internal").contains(Json.Boolean(true)),
          server.extensions.get("x-rate-limit").contains(Json.Number(1000))
        )
      },
      test("Schema[Server] can be derived") {
        val server = Server(url = "https://api.example.com")
        val schema = Schema[Server]

        assertTrue(schema != null, server != null)
      },
      test("Server round-trips through DynamicValue") {
        val variable = ServerVariable(
          default = "prod",
          `enum` = List("dev", "staging", "prod"),
          description = Some(doc("Environment"))
        )
        val server = Server(
          url = "https://{environment}.api.example.com",
          description = Some(doc("Multi-environment server")),
          variables = Map("environment" -> variable),
          extensions = Map("x-region" -> Json.String("us-east-1"))
        )

        val dv     = Schema[Server].toDynamicValue(server)
        val result = Schema[Server].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists(_.url == "https://{environment}.api.example.com"),
          result.exists(_.description.contains(doc("Multi-environment server"))),
          result.exists(_.variables.contains("environment")),
          result.exists(_.extensions.contains("x-region"))
        )
      },
      test("Server with URL template variables") {
        val portVar    = ServerVariable(default = "443", `enum` = List("443", "8443"))
        val versionVar = ServerVariable(default = "v1", `enum` = List("v1", "v2", "v3"))
        val server     = Server(
          url = "https://api.example.com:{port}/{version}",
          variables = Map("port" -> portVar, "version" -> versionVar)
        )

        assertTrue(
          server.url == "https://api.example.com:{port}/{version}",
          server.variables.size == 2,
          server.variables.contains("port"),
          server.variables.contains("version")
        )
      }
    ),
    suite("ServerVariable")(
      test("can be constructed with required fields only") {
        val variable = ServerVariable(default = "production")

        assertTrue(
          variable.default == "production",
          variable.`enum`.isEmpty,
          variable.description.isEmpty,
          variable.extensions.isEmpty
        )
      },
      test("can be constructed with all fields populated") {
        val enumValues = List("dev", "staging", "production")
        val extensions = Map("x-priority" -> Json.Number(1))
        val variable   = ServerVariable(
          default = "production",
          `enum` = enumValues,
          description = Some(doc("Deployment environment")),
          extensions = extensions
        )

        assertTrue(
          variable.default == "production",
          variable.`enum` == enumValues,
          variable.description.contains(doc("Deployment environment")),
          variable.extensions.size == 1,
          variable.extensions.get("x-priority").contains(Json.Number(1))
        )
      },
      test("enum field uses backticks (reserved keyword)") {
        val variable = ServerVariable(default = "v1", `enum` = List("v1", "v2"))

        assertTrue(
          variable.`enum` == List("v1", "v2"),
          variable.`enum`.contains("v1"),
          variable.`enum`.contains("v2")
        )
      },
      test("validates that default is in enum when enum is non-empty") {
        val validResult = ServerVariable.validated(
          default = "v1",
          `enum` = List("v1", "v2", "v3")
        )
        val invalidResult = ServerVariable.validated(
          default = "v4",
          `enum` = List("v1", "v2", "v3")
        )

        assertTrue(
          validResult.isRight,
          validResult.exists(_.default == "v1"),
          invalidResult.isLeft,
          invalidResult.left.exists(_.contains("default"))
        )
      },
      test("validation allows any default when enum is empty") {
        val result = ServerVariable.validated(
          default = "any-value",
          `enum` = Nil
        )

        assertTrue(
          result.isRight,
          result.exists(_.default == "any-value"),
          result.exists(_.`enum`.isEmpty)
        )
      },
      test("validation works with description and extensions") {
        val result = ServerVariable.validated(
          default = "prod",
          `enum` = List("dev", "prod"),
          description = Some(doc("Environment")),
          extensions = Map("x-custom" -> Json.String("value"))
        )

        assertTrue(
          result.isRight,
          result.exists(_.description.contains(doc("Environment"))),
          result.exists(_.extensions.contains("x-custom"))
        )
      },
      test("preserves extensions on construction") {
        val extensions = Map(
          "x-example"    -> Json.String("example-value"),
          "x-deprecated" -> Json.Boolean(false)
        )
        val variable = ServerVariable(default = "default", extensions = extensions)

        assertTrue(
          variable.extensions.size == 2,
          variable.extensions.get("x-example").contains(Json.String("example-value")),
          variable.extensions.get("x-deprecated").contains(Json.Boolean(false))
        )
      },
      test("Schema[ServerVariable] can be derived") {
        val variable = ServerVariable(default = "v1")
        val schema   = Schema[ServerVariable]

        assertTrue(schema != null, variable != null)
      },
      test("ServerVariable round-trips through DynamicValue") {
        val variable = ServerVariable(
          default = "8443",
          `enum` = List("443", "8443", "9443"),
          description = Some(doc("HTTPS port")),
          extensions = Map("x-secure" -> Json.Boolean(true))
        )

        val dv     = Schema[ServerVariable].toDynamicValue(variable)
        val result = Schema[ServerVariable].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists(_.default == "8443"),
          result.exists(_.`enum` == List("443", "8443", "9443")),
          result.exists(_.description.contains(doc("HTTPS port"))),
          result.exists(_.extensions.contains("x-secure"))
        )
      },
      test("ServerVariable with numeric-like string values") {
        val variable = ServerVariable(
          default = "443",
          `enum` = List("80", "443", "8080", "8443")
        )

        assertTrue(
          variable.default == "443",
          variable.`enum`.length == 4,
          variable.`enum`.contains("443")
        )
      },
      test("ServerVariable with path segment values") {
        val variable = ServerVariable(
          default = "v1",
          `enum` = List("v1", "v2", "beta"),
          description = Some(doc("API version path segment"))
        )

        assertTrue(
          variable.default == "v1",
          variable.`enum` == List("v1", "v2", "beta"),
          variable.description.contains(doc("API version path segment"))
        )
      },
      test("validated with only default parameter uses default enum") {
        val result = ServerVariable.validated(default = "value")
        assertTrue(
          result.isRight,
          result.exists(_.default == "value"),
          result.exists(_.`enum`.isEmpty)
        )
      }
    )
  )
}
