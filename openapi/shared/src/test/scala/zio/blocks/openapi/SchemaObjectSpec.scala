package zio.blocks.openapi

import zio.blocks.docs.{Doc, Parser}
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, JsonSchema, JsonSchemaType, SchemaType}
import zio.blocks.chunk.ChunkMap
import zio.test._

object SchemaObjectSpec extends SchemaBaseSpec {
  private def doc(s: String): Doc      = Parser.parse(s).toOption.get
  def spec: Spec[TestEnvironment, Any] = suite("SchemaObject")(
    suite("construction")(
      test("can be created from JsonSchema via fromJsonSchema") {
        val jsonSchema = JsonSchema.string()
        val schemaObj  = SchemaObject.fromJsonSchema(jsonSchema)

        assertTrue(
          schemaObj.jsonSchema == jsonSchema.toJson,
          schemaObj.discriminator.isEmpty,
          schemaObj.xml.isEmpty,
          schemaObj.externalDocs.isEmpty,
          schemaObj.example.isEmpty,
          schemaObj.extensions.isEmpty
        )
      },
      test("can be created with all OpenAPI vocabulary fields") {
        val jsonSchema    = JsonSchema.obj()
        val discriminator = Discriminator(
          propertyName = "type",
          mapping = Map("dog" -> "#/components/schemas/Dog", "cat" -> "#/components/schemas/Cat")
        )
        val xml = XML(
          name = Some("Pet"),
          namespace = Some("http://example.com/schema/pet"),
          prefix = Some("pet"),
          attribute = false,
          wrapped = false
        )
        val externalDocs = ExternalDocumentation(
          url = "https://example.com/docs/pet",
          description = Some(doc("Pet documentation"))
        )
        val example = Json.Object("name" -> Json.String("Fluffy"), "age" -> Json.Number(3))

        val schemaObj = SchemaObject(
          jsonSchema = jsonSchema.toJson,
          discriminator = Some(discriminator),
          xml = Some(xml),
          externalDocs = Some(externalDocs),
          example = Some(example),
          extensions = Map("x-custom" -> Json.String("value"))
        )

        assertTrue(
          schemaObj.jsonSchema == jsonSchema.toJson,
          schemaObj.discriminator.contains(discriminator),
          schemaObj.xml.contains(xml),
          schemaObj.externalDocs.contains(externalDocs),
          schemaObj.example.contains(example),
          schemaObj.extensions == Map("x-custom" -> Json.String("value"))
        )
      },
      test("wraps JsonSchema without duplicating keywords") {
        val jsonSchema = JsonSchema.Object(
          `type` = Some(SchemaType.Single(JsonSchemaType.Object)),
          properties = Some(
            ChunkMap.from(
              Map(
                "name" -> JsonSchema.string(),
                "age"  -> JsonSchema.integer()
              )
            )
          ),
          required = Some(Set("name"))
        )

        val schemaObj = SchemaObject.fromJsonSchema(jsonSchema)

        assertTrue(
          schemaObj.toJsonSchema.isRight,
          schemaObj.discriminator.isEmpty
        )
      },
      test("toJson returns the underlying jsonSchema") {
        val js        = Json.Object("type" -> Json.String("string"), "minLength" -> Json.Number(1))
        val schemaObj = SchemaObject(jsonSchema = js)
        assertTrue(schemaObj.toJson == js)
      }
    ),
    suite("toJsonSchema conversion")(
      test("toJsonSchema extracts the wrapped JsonSchema") {
        val jsonSchema = JsonSchema.string()
        val schemaObj  = SchemaObject(
          jsonSchema = jsonSchema.toJson,
          discriminator = Some(Discriminator("type")),
          xml = Some(XML(name = Some("Item")))
        )

        val extracted = schemaObj.toJsonSchema

        assertTrue(
          extracted.isRight,
          extracted.exists(_ == jsonSchema)
        )
      },
      test("toJsonSchema discards OpenAPI-specific vocabulary") {
        val jsonSchema = JsonSchema.array(JsonSchema.string())
        val schemaObj  = SchemaObject(
          jsonSchema = jsonSchema.toJson,
          discriminator = Some(Discriminator("type")),
          xml = Some(XML(wrapped = true)),
          externalDocs = Some(ExternalDocumentation("https://example.com")),
          example = Some(Json.Array(Json.String("example1"), Json.String("example2"))),
          extensions = Map("x-custom" -> Json.Boolean(true))
        )

        val extracted = schemaObj.toJsonSchema

        assertTrue(
          extracted.isRight,
          extracted.exists(_ == jsonSchema)
        )
      }
    ),
    suite("round-trip conversion")(
      test("JsonSchema -> SchemaObject -> JsonSchema preserves data") {
        val original = JsonSchema.Object(
          `type` = Some(SchemaType.Single(JsonSchemaType.String)),
          minLength = Some(JsonSchema.NonNegativeInt.unsafe(1)),
          maxLength = Some(JsonSchema.NonNegativeInt.unsafe(100)),
          pattern = Some(JsonSchema.RegexPattern.unsafe("^[a-z]+$"))
        )

        val schemaObj = SchemaObject.fromJsonSchema(original)
        val extracted = schemaObj.toJsonSchema

        assertTrue(
          extracted.isRight,
          extracted.exists(_ == original)
        )
      },
      test("round-trip preserves complex JsonSchema structures") {
        val original = JsonSchema.Object(
          `type` = Some(SchemaType.Single(JsonSchemaType.Object)),
          properties = Some(
            ChunkMap.from(
              Map(
                "id"   -> JsonSchema.integer(),
                "name" -> JsonSchema.string(),
                "tags" -> JsonSchema.array(JsonSchema.string())
              )
            )
          ),
          required = Some(Set("id", "name")),
          additionalProperties = Some(JsonSchema.False)
        )

        val schemaObj = SchemaObject.fromJsonSchema(original)
        val extracted = schemaObj.toJsonSchema

        assertTrue(
          extracted.isRight,
          extracted.exists(_ == original)
        )
      }
    ),
    suite("Schema derivation")(
      test("Schema[SchemaObject] can be derived") {
        val schema = Schema[SchemaObject]

        assertTrue(schema != null)
      },
      test("SchemaObject round-trips through DynamicValue") {
        val jsonSchema    = JsonSchema.string()
        val discriminator = Discriminator(
          propertyName = "petType",
          mapping = Map("dog" -> "#/components/schemas/Dog")
        )
        val schemaObj = SchemaObject(
          jsonSchema = jsonSchema.toJson,
          discriminator = Some(discriminator),
          xml = Some(XML(name = Some("Pet")))
        )

        val dv     = Schema[SchemaObject].toDynamicValue(schemaObj)
        val result = Schema[SchemaObject].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists(_.discriminator.exists(_.propertyName == "petType")),
          result.exists(_.xml.exists(_.name.contains("Pet")))
        )
      },
      test("SchemaObject with complex JsonSchema round-trips through DynamicValue") {
        val jsonSchema = JsonSchema.Object(
          `type` = Some(SchemaType.Single(JsonSchemaType.Object)),
          properties = Some(
            ChunkMap.from(
              Map(
                "name"  -> JsonSchema.string(),
                "count" -> JsonSchema.integer(minimum = Some(BigDecimal(0)))
              )
            )
          )
        )

        val schemaObj = SchemaObject(
          jsonSchema = jsonSchema.toJson,
          externalDocs = Some(ExternalDocumentation("https://example.com/docs")),
          example = Some(Json.Object("name" -> Json.String("test"), "count" -> Json.Number(5)))
        )

        val dv     = Schema[SchemaObject].toDynamicValue(schemaObj)
        val result = Schema[SchemaObject].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists(_.externalDocs.exists(_.url == "https://example.com/docs")),
          result.exists(_.example.isDefined)
        )
      }
    ),
    suite("OpenAPI vocabulary")(
      test("discriminator field stores polymorphism metadata") {
        val discriminator = Discriminator(
          propertyName = "type",
          mapping = Map(
            "circle" -> "#/components/schemas/Circle",
            "square" -> "#/components/schemas/Square"
          )
        )

        val schemaObj = SchemaObject(
          jsonSchema = JsonSchema.obj().toJson,
          discriminator = Some(discriminator)
        )

        assertTrue(
          schemaObj.discriminator.exists(_.propertyName == "type"),
          schemaObj.discriminator.exists(_.mapping.size == 2)
        )
      },
      test("xml field stores XML serialization metadata") {
        val xml = XML(
          name = Some("book"),
          namespace = Some("http://example.com/schema/book"),
          prefix = Some("bk"),
          attribute = false,
          wrapped = true
        )

        val schemaObj = SchemaObject(
          jsonSchema = JsonSchema.array(JsonSchema.string()).toJson,
          xml = Some(xml)
        )

        assertTrue(
          schemaObj.xml.exists(_.name.contains("book")),
          schemaObj.xml.exists(_.wrapped == true),
          schemaObj.xml.exists(_.namespace.contains("http://example.com/schema/book"))
        )
      },
      test("externalDocs field stores documentation references") {
        val externalDocs = ExternalDocumentation(
          url = "https://example.com/api-docs",
          description = Some(doc("Full API documentation"))
        )

        val schemaObj = SchemaObject(
          jsonSchema = JsonSchema.string().toJson,
          externalDocs = Some(externalDocs)
        )

        assertTrue(
          schemaObj.externalDocs.exists(_.url == "https://example.com/api-docs"),
          schemaObj.externalDocs.exists(_.description.contains(doc("Full API documentation")))
        )
      },
      test("example field stores deprecated example data") {
        val example = Json.Object(
          "id"     -> Json.Number(123),
          "name"   -> Json.String("Example User"),
          "active" -> Json.Boolean(true)
        )

        val schemaObj = SchemaObject(
          jsonSchema = JsonSchema.obj().toJson,
          example = Some(example)
        )

        assertTrue(
          schemaObj.example.isDefined,
          schemaObj.example.exists(_.isInstanceOf[Json.Object])
        )
      },
      test("extensions field stores custom x-* properties") {
        val extensions = Map(
          "x-internal" -> Json.Boolean(true),
          "x-version"  -> Json.String("1.0"),
          "x-metadata" -> Json.Object("key" -> Json.String("value"))
        )

        val schemaObj = SchemaObject(
          jsonSchema = JsonSchema.string().toJson,
          extensions = extensions
        )

        assertTrue(
          schemaObj.extensions.size == 3,
          schemaObj.extensions.contains("x-internal"),
          schemaObj.extensions.contains("x-version"),
          schemaObj.extensions.contains("x-metadata")
        )
      }
    ),
    suite("integration with JsonSchema types")(
      test("works with JsonSchema.True") {
        val schemaObj = SchemaObject.fromJsonSchema(JsonSchema.True)

        assertTrue(
          schemaObj.toJsonSchema.isRight,
          schemaObj.toJsonSchema.exists(_ == JsonSchema.True)
        )
      },
      test("works with JsonSchema.False") {
        val schemaObj = SchemaObject.fromJsonSchema(JsonSchema.False)

        assertTrue(
          schemaObj.toJsonSchema.isRight,
          schemaObj.toJsonSchema.exists(_ == JsonSchema.False)
        )
      },
      test("works with JsonSchema.Object containing allOf") {
        val schemaObj = SchemaObject.fromJsonSchema(
          JsonSchema.Object(
            allOf = Some(
              new ::(
                JsonSchema.obj(properties = Some(ChunkMap.from(Map("name" -> JsonSchema.string())))),
                List(JsonSchema.obj(properties = Some(ChunkMap.from(Map("age" -> JsonSchema.integer())))))
              )
            )
          )
        )

        assertTrue(
          schemaObj.toJsonSchema.isRight,
          schemaObj.toJsonSchema.exists(_.isInstanceOf[JsonSchema.Object])
        )
      },
      test("works with JsonSchema reference schemas") {
        val refSchema = JsonSchema.refString("#/components/schemas/User")
        val schemaObj = SchemaObject.fromJsonSchema(refSchema)

        assertTrue(
          schemaObj.toJsonSchema.isRight,
          schemaObj.toJsonSchema.exists(_ == refSchema)
        )
      }
    )
  )
}
