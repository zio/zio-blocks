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
import zio.blocks.schema.json.Json
import zio.test._

object ServerSpec extends SchemaBaseSpec {
  private def doc(s: String): Doc      = Doc(Chunk.single(Paragraph(Chunk.single(Inline.Text(s)))))
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
        val server = Server(
          url = "https://api.example.com/{version}",
          description = Some(doc("Production API server")),
          variables = ChunkMap(
            "version" -> ServerVariable(
              default = "v1",
              `enum` = Chunk("v1", "v2"),
              description = Some(doc("API version"))
            )
          ),
          extensions = ChunkMap("x-custom" -> Json.String("value"))
        )
        assertTrue(
          server.url == "https://api.example.com/{version}",
          server.description.contains(doc("Production API server")),
          server.variables.size == 1,
          server.variables.contains("version"),
          server.extensions.size == 1,
          server.extensions.get("x-custom").contains(Json.String("value"))
        )
      },
      test("preserves extensions on construction") {
        val server = Server(
          url = "https://api.example.com",
          extensions = ChunkMap(
            "x-internal"   -> Json.Boolean(true),
            "x-rate-limit" -> Json.Number(1000)
          )
        )
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
        val server = Server(
          url = "https://{environment}.api.example.com",
          description = Some(doc("Multi-environment server")),
          variables = ChunkMap(
            "environment" -> ServerVariable(
              default = "prod",
              `enum` = Chunk("dev", "staging", "prod"),
              description = Some(doc("Environment"))
            )
          ),
          extensions = ChunkMap("x-region" -> Json.String("us-east-1"))
        )
        val result = Schema[Server].fromDynamicValue(Schema[Server].toDynamicValue(server))
        assertTrue(
          result.isRight,
          result.exists(_.url == "https://{environment}.api.example.com"),
          result.exists(_.description.contains(doc("Multi-environment server"))),
          result.exists(_.variables.contains("environment")),
          result.exists(_.extensions.contains("x-region"))
        )
      },
      test("Server with URL template variables") {
        val server = Server(
          url = "https://api.example.com:{port}/{version}",
          variables = ChunkMap(
            "port"    -> ServerVariable(default = "443", `enum` = Chunk("443", "8443")),
            "version" -> ServerVariable(default = "v1", `enum` = Chunk("v1", "v2", "v3"))
          )
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
        val variable = ServerVariable(
          default = "production",
          `enum` = Chunk("dev", "staging", "production"),
          description = Some(doc("Deployment environment")),
          extensions = ChunkMap("x-priority" -> Json.Number(1))
        )
        assertTrue(
          variable.default == "production",
          variable.`enum` == Chunk("dev", "staging", "production"),
          variable.description.contains(doc("Deployment environment")),
          variable.extensions.size == 1,
          variable.extensions.get("x-priority").contains(Json.Number(1))
        )
      },
      test("enum field uses backticks (reserved keyword)") {
        val variable = ServerVariable(default = "v1", `enum` = Chunk("v1", "v2"))
        assertTrue(
          variable.`enum` == Chunk("v1", "v2"),
          variable.`enum`.contains("v1"),
          variable.`enum`.contains("v2")
        )
      },
      test("validates that default is in enum when enum is non-empty") {
        val validResult = util
          .Try(
            ServerVariable(
              default = "v1",
              `enum` = Chunk("v1", "v2", "v3")
            )
          )
          .toEither
        val invalidResult = util
          .Try(
            ServerVariable(
              default = "v4",
              `enum` = Chunk("v1", "v2", "v3")
            )
          )
          .toEither
        assertTrue(
          validResult.isRight,
          validResult.exists(_.default == "v1"),
          invalidResult.isLeft,
          invalidResult.left.exists(_.getMessage.contains("default"))
        )
      },
      test("validation allows any default when enum is empty") {
        val result = util.Try(ServerVariable(default = "any-value", `enum` = Chunk.empty)).toEither
        assertTrue(
          result.isRight,
          result.exists(_.default == "any-value"),
          result.exists(_.`enum`.isEmpty)
        )
      },
      test("validation works with description and extensions") {
        val result = util
          .Try(
            ServerVariable(
              default = "prod",
              `enum` = Chunk("dev", "prod"),
              description = Some(doc("Environment")),
              extensions = ChunkMap("x-custom" -> Json.String("value"))
            )
          )
          .toEither
        assertTrue(
          result.isRight,
          result.exists(_.description.contains(doc("Environment"))),
          result.exists(_.extensions.contains("x-custom"))
        )
      },
      test("preserves extensions on construction") {
        val variable = ServerVariable(
          default = "default",
          extensions = ChunkMap(
            "x-example"    -> Json.String("example-value"),
            "x-deprecated" -> Json.Boolean(false)
          )
        )
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
          `enum` = Chunk("443", "8443", "9443"),
          description = Some(doc("HTTPS port")),
          extensions = ChunkMap("x-secure" -> Json.Boolean(true))
        )
        val result = Schema[ServerVariable].fromDynamicValue(Schema[ServerVariable].toDynamicValue(variable))
        assertTrue(
          result.isRight,
          result.exists(_.default == "8443"),
          result.exists(_.`enum` == Chunk("443", "8443", "9443")),
          result.exists(_.description.contains(doc("HTTPS port"))),
          result.exists(_.extensions.contains("x-secure"))
        )
      },
      test("ServerVariable with numeric-like string values") {
        val variable = ServerVariable(default = "443", `enum` = Chunk("80", "443", "8080", "8443"))
        assertTrue(
          variable.default == "443",
          variable.`enum`.length == 4,
          variable.`enum`.contains("443")
        )
      },
      test("ServerVariable with path segment values") {
        val variable = ServerVariable(
          default = "v1",
          `enum` = Chunk("v1", "v2", "beta"),
          description = Some(doc("API version path segment"))
        )
        assertTrue(
          variable.default == "v1",
          variable.`enum` == Chunk("v1", "v2", "beta"),
          variable.description.contains(doc("API version path segment"))
        )
      },
      test("validated with only default parameter uses default enum") {
        val result = util.Try(ServerVariable(default = "value")).toEither
        assertTrue(
          result.isRight,
          result.exists(_.default == "value"),
          result.exists(_.`enum`.isEmpty)
        )
      }
    )
  )
}
