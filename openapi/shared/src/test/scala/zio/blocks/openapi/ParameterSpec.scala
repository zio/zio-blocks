package zio.blocks.openapi

import zio.blocks.schema._
import zio.blocks.schema.json.Json
import zio.test._

object ParameterSpec extends SchemaBaseSpec {
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
        val dv       = Schema[ParameterLocation].toDynamicValue(location)
        val result   = Schema[ParameterLocation].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.contains(ParameterLocation.Query)
        )
      }
    ),
    suite("Parameter")(
      test("can be constructed with required fields only (query)") {
        val param = Parameter(
          name = "limit",
          in = ParameterLocation.Query
        )

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
        val schema     = Json.Object("type" -> Json.String("integer"))
        val example    = Json.Number(10)
        val examples   = Map("example1" -> Json.Number(20))
        val content    = Map("application/json" -> Json.Object())
        val extensions = Map("x-custom" -> Json.String("value"))

        val param = Parameter(
          name = "limit",
          in = ParameterLocation.Query,
          description = Some("Maximum number of results"),
          required = true,
          deprecated = true,
          allowEmptyValue = true,
          style = Some("form"),
          explode = Some(true),
          allowReserved = Some(false),
          schema = Some(schema),
          example = Some(example),
          examples = examples,
          content = content,
          extensions = extensions
        )

        assertTrue(
          param.name == "limit",
          param.in == ParameterLocation.Query,
          param.description.contains("Maximum number of results"),
          param.required,
          param.deprecated,
          param.allowEmptyValue,
          param.style.contains("form"),
          param.explode.contains(true),
          param.allowReserved.contains(false),
          param.schema.isDefined,
          param.example.contains(example),
          param.examples.size == 1,
          param.content.size == 1,
          param.extensions.size == 1
        )
      },
      test("path parameter requires required=true") {
        val param = Parameter(
          name = "userId",
          in = ParameterLocation.Path,
          required = true
        )

        assertTrue(
          param.name == "userId",
          param.in == ParameterLocation.Path,
          param.required
        )
      },
      test("path parameter with required=false throws exception") {
        val result = scala.util.Try {
          Parameter(
            name = "userId",
            in = ParameterLocation.Path,
            required = false
          )
        }

        assertTrue(
          result.isFailure,
          result.failed.get.getMessage.contains("path"),
          result.failed.get.getMessage.contains("required")
        )
      },
      test("query parameter can have required=false") {
        val param = Parameter(
          name = "limit",
          in = ParameterLocation.Query,
          required = false
        )

        assertTrue(
          param.name == "limit",
          param.in == ParameterLocation.Query,
          !param.required
        )
      },
      test("header parameter can have required=false") {
        val param = Parameter(
          name = "X-Request-ID",
          in = ParameterLocation.Header,
          required = false
        )

        assertTrue(
          param.name == "X-Request-ID",
          param.in == ParameterLocation.Header,
          !param.required
        )
      },
      test("cookie parameter can have required=false") {
        val param = Parameter(
          name = "sessionId",
          in = ParameterLocation.Cookie,
          required = false
        )

        assertTrue(
          param.name == "sessionId",
          param.in == ParameterLocation.Cookie,
          !param.required
        )
      },
      test("preserves extensions on construction") {
        val extensions = Map(
          "x-internal"   -> Json.Boolean(true),
          "x-rate-limit" -> Json.Number(1000)
        )
        val param = Parameter(
          name = "apiKey",
          in = ParameterLocation.Query,
          extensions = extensions
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
          description = Some("Max results"),
          required = true,
          deprecated = false,
          allowEmptyValue = true,
          style = Some("form"),
          explode = Some(true),
          allowReserved = Some(false),
          schema = Some(Json.Object("type" -> Json.String("integer"))),
          example = Some(Json.Number(10)),
          examples = Map("ex1" -> Json.Number(20)),
          content = Map("application/json" -> Json.Object()),
          extensions = Map("x-custom" -> Json.String("value"))
        )

        val dv     = Schema[Parameter].toDynamicValue(param)
        val result = Schema[Parameter].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists(_.name == "limit"),
          result.exists(_.in == ParameterLocation.Query),
          result.exists(_.description.contains("Max results")),
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
          description = Some("Session authentication token"),
          required = true
        )

        assertTrue(
          param.name == "sessionToken",
          param.in == ParameterLocation.Cookie,
          param.required
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
        val schema     = Json.Object("type" -> Json.String("string"))
        val example    = Json.String("Bearer token123")
        val examples   = Map("example1" -> Json.String("Bearer abc"))
        val content    = Map("application/json" -> Json.Object())
        val extensions = Map("x-custom" -> Json.String("value"))

        val header = Header(
          description = Some("Authentication header"),
          required = true,
          deprecated = true,
          allowEmptyValue = false,
          style = Some("simple"),
          explode = Some(false),
          allowReserved = Some(true),
          schema = Some(schema),
          example = Some(example),
          examples = examples,
          content = content,
          extensions = extensions
        )

        assertTrue(
          header.description.contains("Authentication header"),
          header.required,
          header.deprecated,
          !header.allowEmptyValue,
          header.style.contains("simple"),
          header.explode.contains(false),
          header.allowReserved.contains(true),
          header.schema.isDefined,
          header.example.contains(example),
          header.examples.size == 1,
          header.content.size == 1,
          header.extensions.size == 1
        )
      },
      test("header can have required=false (no path constraint)") {
        val header = Header(
          description = Some("Optional header"),
          required = false
        )

        assertTrue(
          header.description.contains("Optional header"),
          !header.required
        )
      },
      test("preserves extensions on construction") {
        val extensions = Map(
          "x-rate-limit" -> Json.Number(100),
          "x-internal"   -> Json.Boolean(false)
        )
        val header = Header(extensions = extensions)

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
          description = Some("API Key"),
          required = true,
          deprecated = false,
          allowEmptyValue = false,
          style = Some("simple"),
          explode = Some(false),
          allowReserved = Some(true),
          schema = Some(Json.Object("type" -> Json.String("string"))),
          example = Some(Json.String("key123")),
          examples = Map("ex1" -> Json.String("key456")),
          content = Map("text/plain" -> Json.Object()),
          extensions = Map("x-custom" -> Json.Boolean(true))
        )

        val dv     = Schema[Header].toDynamicValue(header)
        val result = Schema[Header].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists(_.description.contains("API Key")),
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
        val arraySchema = Json.Object(
          "type"  -> Json.String("array"),
          "items" -> Json.Object("type" -> Json.String("string"))
        )
        val header = Header(
          schema = Some(arraySchema),
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
        val examples = Map(
          "default" -> Json.String("value1"),
          "special" -> Json.String("value2"),
          "extreme" -> Json.String("value3")
        )
        val header = Header(examples = examples)

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
