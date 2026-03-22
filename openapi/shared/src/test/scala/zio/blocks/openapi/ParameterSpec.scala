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

object ParameterSpec extends SchemaBaseSpec {
  private def doc(s: String): Doc      = Doc(Chunk.single(Paragraph(Chunk.single(Inline.Text(s)))))
  def spec: Spec[TestEnvironment, Any] = suite("Parameter and Header")(
    suite("ParameterLocation")(
      test("Query location exists") {
        val location = ParameterLocation.Query
        assertTrue(location == ParameterLocation.Query)
      },
      test("Header location exists") {
        val location = ParameterLocation.Header
        assertTrue(location == ParameterLocation.Header)
      },
      test("Path location exists") {
        val location = ParameterLocation.Path
        assertTrue(location == ParameterLocation.Path)
      },
      test("Cookie location exists") {
        val location = ParameterLocation.Cookie
        assertTrue(location == ParameterLocation.Cookie)
      },
      test("Schema[ParameterLocation] can be derived") {
        val schema = Schema[ParameterLocation]
        assertTrue(schema != null)
      },
      test("ParameterLocation round-trips through DynamicValue") {
        val location = ParameterLocation.Query
        val result   = Schema[ParameterLocation].fromDynamicValue(Schema[ParameterLocation].toDynamicValue(location))
        assertTrue(
          result.isRight,
          result.contains(ParameterLocation.Query)
        )
      }
    ),
    suite("Parameter")(
      test("can be constructed with required fields only (query)") {
        val param = Parameter(name = "limit", in = ParameterLocation.Query)
        assertTrue(
          param.name == "limit",
          param.in == ParameterLocation.Query,
          param.description.isEmpty,
          !param.required,
          !param.deprecated,
          !param.allowEmptyValue,
          param.style.isEmpty,
          param.explode.isEmpty,
          param.allowReserved.isEmpty,
          param.schema.isEmpty,
          param.example.isEmpty,
          param.examples.isEmpty,
          param.content.isEmpty,
          param.extensions.isEmpty
        )
      },
      test("can be constructed with all fields populated") {
        val param = Parameter(
          name = "limit",
          in = ParameterLocation.Query,
          description = Some(doc("Maximum number of results")),
          required = true,
          deprecated = true,
          allowEmptyValue = true,
          style = Some("form"),
          explode = Some(true),
          allowReserved = Some(false),
          schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("integer"))))),
          example = Some(Json.Number(10)),
          examples = ChunkMap("example1" -> ReferenceOr.Value(Example(value = Some(Json.Number(20))))),
          content = ChunkMap("application/json" -> MediaType()),
          extensions = ChunkMap("x-custom" -> Json.String("value"))
        )
        assertTrue(
          param.name == "limit",
          param.in == ParameterLocation.Query,
          param.description.contains(doc("Maximum number of results")),
          param.required,
          param.deprecated,
          param.allowEmptyValue,
          param.style.contains("form"),
          param.explode.contains(true),
          param.allowReserved.contains(false),
          param.schema.isDefined,
          param.example.contains(Json.Number(10)),
          param.examples.size == 1,
          param.content.size == 1,
          param.extensions.size == 1
        )
      },
      test("path parameter requires required=true") {
        val param = Parameter(name = "userId", in = ParameterLocation.Path, required = true)
        assertTrue(
          param.name == "userId",
          param.in == ParameterLocation.Path,
          param.required
        )
      },
      test("path parameter with required=false is allowed") {
        val param = Parameter(name = "userId", in = ParameterLocation.Path)
        assertTrue(
          param.name == "userId",
          param.in == ParameterLocation.Path,
          !param.required
        )
      },
      test("query parameter can have required=false") {
        val param = Parameter(name = "limit", in = ParameterLocation.Query)
        assertTrue(
          param.name == "limit",
          param.in == ParameterLocation.Query,
          !param.required
        )
      },
      test("header parameter can have required=false") {
        val param = Parameter(name = "X-Request-ID", in = ParameterLocation.Header)
        assertTrue(
          param.name == "X-Request-ID",
          param.in == ParameterLocation.Header,
          !param.required
        )
      },
      test("cookie parameter can have required=false") {
        val param = Parameter(name = "sessionId", in = ParameterLocation.Cookie)
        assertTrue(
          param.name == "sessionId",
          param.in == ParameterLocation.Cookie,
          !param.required
        )
      },
      test("preserves extensions on construction") {
        val param = Parameter(
          name = "apiKey",
          in = ParameterLocation.Query,
          extensions = ChunkMap("x-internal" -> Json.Boolean(true), "x-rate-limit" -> Json.Number(1000))
        )
        assertTrue(
          param.extensions.size == 2,
          param.extensions.get("x-internal").contains(Json.Boolean(true)),
          param.extensions.get("x-rate-limit").contains(Json.Number(1000))
        )
      },
      test("Schema[Parameter] can be derived") {
        val param  = Parameter(name = "test", in = ParameterLocation.Query)
        val schema = Schema[Parameter]
        assertTrue(schema != null, param != null)
      },
      test("Parameter round-trips through DynamicValue") {
        val param = Parameter(
          name = "limit",
          in = ParameterLocation.Query,
          description = Some(doc("Max results")),
          required = true,
          allowEmptyValue = true,
          style = Some("form"),
          explode = Some(true),
          allowReserved = Some(false),
          schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("integer"))))),
          example = Some(Json.Number(10)),
          examples = ChunkMap("ex1" -> ReferenceOr.Value(Example(value = Some(Json.Number(20))))),
          content = ChunkMap("application/json" -> MediaType()),
          extensions = ChunkMap("x-custom" -> Json.String("value"))
        )
        val result = Schema[Parameter].fromDynamicValue(Schema[Parameter].toDynamicValue(param))
        assertTrue(
          result.isRight,
          result.exists(_.name == "limit"),
          result.exists(_.in == ParameterLocation.Query),
          result.exists(_.description.contains(doc("Max results"))),
          result.exists(_.required),
          result.exists(!_.deprecated),
          result.exists(_.allowEmptyValue),
          result.exists(_.style.contains("form")),
          result.exists(_.explode.contains(true)),
          result.exists(_.allowReserved.contains(false)),
          result.exists(_.schema.isDefined),
          result.exists(_.example.isDefined),
          result.exists(_.examples.nonEmpty),
          result.exists(_.content.nonEmpty),
          result.exists(_.extensions.nonEmpty)
        )
      },
      test("Parameter with query location and various styles") {
        val param = Parameter(
          name = "filter",
          in = ParameterLocation.Query,
          style = Some("deepObject"),
          explode = Some(true)
        )
        assertTrue(
          param.style.contains("deepObject"),
          param.explode.contains(true)
        )
      },
      test("Parameter with cookie location") {
        val param = Parameter(
          name = "sessionToken",
          in = ParameterLocation.Cookie,
          description = Some(doc("Session authentication token")),
          required = true
        )
        assertTrue(
          param.name == "sessionToken",
          param.in == ParameterLocation.Cookie,
          param.required
        )
      },
      test("Parameter minimal round-trip exercises private constructor defaults") {
        val param  = Parameter(name = "q", in = ParameterLocation.Query)
        val result = Schema[Parameter].fromDynamicValue(Schema[Parameter].toDynamicValue(param))
        assertTrue(
          result.isRight,
          result.exists(_.name == "q"),
          result.exists(_.description.isEmpty),
          result.exists(!_.required),
          result.exists(!_.deprecated),
          result.exists(!_.allowEmptyValue),
          result.exists(_.style.isEmpty),
          result.exists(_.explode.isEmpty),
          result.exists(_.allowReserved.isEmpty),
          result.exists(_.schema.isEmpty),
          result.exists(_.example.isEmpty),
          result.exists(_.examples.isEmpty),
          result.exists(_.content.isEmpty),
          result.exists(_.extensions.isEmpty)
        )
      }
    ),
    suite("Header")(
      test("can be constructed with no fields") {
        val header = Header()
        assertTrue(
          header.description.isEmpty,
          !header.required,
          !header.deprecated,
          !header.allowEmptyValue,
          header.style.isEmpty,
          header.explode.isEmpty,
          header.allowReserved.isEmpty,
          header.schema.isEmpty,
          header.example.isEmpty,
          header.examples.isEmpty,
          header.content.isEmpty,
          header.extensions.isEmpty
        )
      },
      test("can be constructed with all fields populated") {
        val header = Header(
          description = Some(doc("Authentication header")),
          required = true,
          deprecated = true,
          style = Some("simple"),
          explode = Some(false),
          allowReserved = Some(true),
          schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("string"))))),
          example = Some(Json.String("Bearer token123")),
          examples = ChunkMap("example1" -> ReferenceOr.Value(Example(value = Some(Json.String("Bearer abc"))))),
          content = ChunkMap("application/json" -> MediaType()),
          extensions = ChunkMap("x-custom" -> Json.String("value"))
        )
        assertTrue(
          header.description.contains(doc("Authentication header")),
          header.required,
          header.deprecated,
          !header.allowEmptyValue,
          header.style.contains("simple"),
          header.explode.contains(false),
          header.allowReserved.contains(true),
          header.schema.isDefined,
          header.example.contains(Json.String("Bearer token123")),
          header.examples.size == 1,
          header.content.size == 1,
          header.extensions.size == 1
        )
      },
      test("header can have required=false (no path constraint)") {
        val header = Header(description = Some(doc("Optional header")))
        assertTrue(
          header.description.contains(doc("Optional header")),
          !header.required
        )
      },
      test("preserves extensions on construction") {
        val header = Header(extensions =
          ChunkMap(
            "x-rate-limit" -> Json.Number(100),
            "x-internal"   -> Json.Boolean(false)
          )
        )
        assertTrue(
          header.extensions.size == 2,
          header.extensions.get("x-rate-limit").contains(Json.Number(100)),
          header.extensions.get("x-internal").contains(Json.Boolean(false))
        )
      },
      test("Schema[Header] can be derived") {
        val header = Header()
        val schema = Schema[Header]
        assertTrue(schema != null, header != null)
      },
      test("Header round-trips through DynamicValue") {
        val header = Header(
          description = Some(doc("API Key")),
          required = true,
          style = Some("simple"),
          explode = Some(false),
          allowReserved = Some(true),
          schema = Some(ReferenceOr.Value(SchemaObject(jsonSchema = Json.Object("type" -> Json.String("string"))))),
          example = Some(Json.String("key123")),
          examples = ChunkMap("ex1" -> ReferenceOr.Value(Example(value = Some(Json.String("key456"))))),
          content = ChunkMap("text/plain" -> MediaType()),
          extensions = ChunkMap("x-custom" -> Json.Boolean(true))
        )
        val result = Schema[Header].fromDynamicValue(Schema[Header].toDynamicValue(header))
        assertTrue(
          result.isRight,
          result.exists(_.description.contains(doc("API Key"))),
          result.exists(_.required),
          result.exists(!_.deprecated),
          result.exists(!_.allowEmptyValue),
          result.exists(_.style.contains("simple")),
          result.exists(_.explode.contains(false)),
          result.exists(_.allowReserved.contains(true)),
          result.exists(_.schema.isDefined),
          result.exists(_.example.isDefined),
          result.exists(_.examples.nonEmpty),
          result.exists(_.content.nonEmpty),
          result.exists(_.extensions.nonEmpty)
        )
      },
      test("Header with schema for array type") {
        val header = Header(
          schema = Some(
            ReferenceOr.Value(
              SchemaObject(jsonSchema =
                Json.Object("type" -> Json.String("array"), "items" -> Json.Object("type" -> Json.String("string")))
              )
            )
          ),
          style = Some("simple"),
          explode = Some(false)
        )
        assertTrue(
          header.schema.isDefined,
          header.style.contains("simple"),
          header.explode.contains(false)
        )
      },
      test("Header with multiple examples") {
        val header = Header(examples =
          ChunkMap(
            "default" -> ReferenceOr.Value(Example(value = Some(Json.String("value1")))),
            "special" -> ReferenceOr.Value(Example(value = Some(Json.String("value2")))),
            "extreme" -> ReferenceOr.Value(Example(value = Some(Json.String("value3"))))
          )
        )
        assertTrue(
          header.examples.size == 3,
          header.examples.contains("default"),
          header.examples.contains("special"),
          header.examples.contains("extreme")
        )
      }
    )
  )
}
