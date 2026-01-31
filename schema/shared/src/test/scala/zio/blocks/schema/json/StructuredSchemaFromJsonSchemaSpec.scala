package zio.blocks.schema.json

import zio.blocks.chunk.ChunkMap
import zio.blocks.schema._
import zio.test._

object StructuredSchemaFromJsonSchemaSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("StructuredSchemaFromJsonSchemaSpec")(
    suite("Structure preservation")(
      test("closed object schema produces Reflect.Record") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name", "age")),
          additionalProperties = Some(JsonSchema.False)
        )
        val schema = Schema.fromJsonSchema(jsonSchema)

        val wrapped = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isRecord))
      },
      test("closed object schema has no open modifier") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string())),
          required = Some(Set("name")),
          additionalProperties = Some(JsonSchema.False)
        )
        val schema = Schema.fromJsonSchema(jsonSchema)

        val wrapped         = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val modifiers       = wrapped.map(_.modifiers).getOrElse(Nil)
        val hasOpenModifier = modifiers.exists {
          case Modifier.config("json.closure", "open") => true
          case _                                       => false
        }
        assertTrue(!hasOpenModifier)
      },
      test("open object schema produces Reflect.Record with open modifier") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string()))
        )
        val schema = Schema.fromJsonSchema(jsonSchema)

        val wrapped         = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val modifiers       = wrapped.map(_.modifiers).getOrElse(Nil)
        val hasOpenModifier = modifiers.exists {
          case Modifier.config("json.closure", "open") => true
          case _                                       => false
        }
        assertTrue(wrapped.exists(_.isRecord) && hasOpenModifier)
      },
      test("object schema has correct field names") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name", "age"))
        )
        val schema = Schema.fromJsonSchema(jsonSchema)

        val wrapped    = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val record     = wrapped.flatMap(_.asRecord)
        val fieldNames = record.map(_.fields.map(_.name).toSet).getOrElse(Set.empty)
        assertTrue(fieldNames == Set("name", "age"))
      },
      test("array schema produces Reflect.Sequence") {
        val jsonSchema = JsonSchema.array(items = Some(JsonSchema.string()))
        val schema     = Schema.fromJsonSchema(jsonSchema)

        val wrapped      = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val innerWrapped = wrapped.flatMap(_.asWrapperUnknown).map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isWrapper) && innerWrapped.exists(_.isSequence))
      },
      test("pure map object produces Reflect.Map") {
        val jsonSchema = JsonSchema.obj(
          additionalProperties = Some(JsonSchema.string())
        )
        val schema = Schema.fromJsonSchema(jsonSchema)

        val wrapped      = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val innerWrapped = wrapped.flatMap(_.asWrapperUnknown).map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isWrapper) && innerWrapped.exists(_.isMap))
      },
      test("enum schema produces Reflect.Variant") {
        val jsonSchema = JsonSchema.Object(
          `enum` = Some(
            new ::(Json.String("Red"), Json.String("Green") :: Json.String("Blue") :: Nil)
          )
        )
        val schema = Schema.fromJsonSchema(jsonSchema)

        val wrapped = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isVariant))
      },
      test("enum schema has correct case names") {
        val jsonSchema = JsonSchema.Object(
          `enum` = Some(
            new ::(Json.String("Red"), Json.String("Green") :: Json.String("Blue") :: Nil)
          )
        )
        val schema = Schema.fromJsonSchema(jsonSchema)

        val wrapped   = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val variant   = wrapped.flatMap(_.asVariant)
        val caseNames = variant.map(_.cases.map(_.name).toSet).getOrElse(Set.empty)
        assertTrue(caseNames == Set("Red", "Green", "Blue"))
      },
      test("enum cases are empty Records (isEnumeration)") {
        val jsonSchema = JsonSchema.Object(
          `enum` = Some(
            new ::(Json.String("Red"), Json.String("Green") :: Json.String("Blue") :: Nil)
          )
        )
        val schema = Schema.fromJsonSchema(jsonSchema)

        val wrapped = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isEnumeration))
      },
      test("key-discriminated variant schema produces Reflect.Variant") {
        val jsonSchema = JsonSchema.Object(
          oneOf = Some(
            new ::(
              JsonSchema.obj(
                properties = Some(
                  ChunkMap(
                    "CreditCard" -> JsonSchema.obj(
                      properties = Some(ChunkMap("ccNum" -> JsonSchema.string())),
                      required = Some(Set("ccNum"))
                    )
                  )
                ),
                required = Some(Set("CreditCard")),
                additionalProperties = Some(JsonSchema.False)
              ),
              JsonSchema.obj(
                properties = Some(
                  ChunkMap(
                    "BankAccount" -> JsonSchema.obj(
                      properties = Some(ChunkMap("accNo" -> JsonSchema.string())),
                      required = Some(Set("accNo"))
                    )
                  )
                ),
                required = Some(Set("BankAccount")),
                additionalProperties = Some(JsonSchema.False)
              ) :: Nil
            )
          )
        )
        val schema = Schema.fromJsonSchema(jsonSchema)

        val wrapped = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isVariant))
      },
      test("key-discriminated variant schema has correct case names") {
        val jsonSchema = JsonSchema.Object(
          oneOf = Some(
            new ::(
              JsonSchema.obj(
                properties = Some(
                  ChunkMap(
                    "CreditCard" -> JsonSchema.obj(
                      properties = Some(ChunkMap("ccNum" -> JsonSchema.string()))
                    )
                  )
                ),
                required = Some(Set("CreditCard"))
              ),
              JsonSchema.obj(
                properties = Some(
                  ChunkMap(
                    "BankAccount" -> JsonSchema.obj(
                      properties = Some(ChunkMap("accNo" -> JsonSchema.string()))
                    )
                  )
                ),
                required = Some(Set("BankAccount"))
              ) :: Nil
            )
          )
        )
        val schema = Schema.fromJsonSchema(jsonSchema)

        val wrapped   = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val variant   = wrapped.flatMap(_.asVariant)
        val caseNames = variant.map(_.cases.map(_.name).toSet).getOrElse(Set.empty)
        assertTrue(caseNames == Set("CreditCard", "BankAccount"))
      },
      test("tuple schema (prefixItems + items:false) produces Reflect.Record with positional fields") {
        val jsonSchema = JsonSchema.Object(
          prefixItems = Some(new ::(JsonSchema.string(), JsonSchema.integer() :: Nil)),
          items = Some(JsonSchema.False)
        )
        val schema = Schema.fromJsonSchema(jsonSchema)

        val wrapped      = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val innerWrapped = wrapped.flatMap(_.asWrapperUnknown).map(_.wrapper.wrapped)
        val record       = innerWrapped.flatMap(_.asRecord)
        val fieldNames   = record.map(_.fields.map(_.name).toList).getOrElse(Nil)
        assertTrue(fieldNames == List("_1", "_2"))
      },
      test("untagged union (oneOf without discriminator) falls back to Dynamic") {
        val jsonSchema = JsonSchema.Object(
          oneOf = Some(
            new ::(
              JsonSchema.obj(properties = Some(ChunkMap("left" -> JsonSchema.string()))),
              JsonSchema.obj(properties = Some(ChunkMap("right" -> JsonSchema.integer()))) :: Nil
            )
          )
        )
        val schema = Schema.fromJsonSchema(jsonSchema)

        val wrapped = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isDynamic))
      },
      test("anyOf schema remains Dynamic (untagged union)") {
        val jsonSchema = JsonSchema.Object(
          anyOf = Some(
            new ::(
              JsonSchema.string(),
              JsonSchema.integer() :: Nil
            )
          )
        )
        val schema = Schema.fromJsonSchema(jsonSchema)

        val wrapped = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isDynamic))
      },
      test("primitive string schema produces wrapped Reflect.Primitive") {
        val jsonSchema = JsonSchema.string()
        val schema     = Schema.fromJsonSchema(jsonSchema)

        val wrapped      = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val innerWrapped = wrapped.flatMap(_.asWrapperUnknown).map(_.wrapper.wrapped)
        assertTrue(innerWrapped.exists(_.isPrimitive))
      },
      test("primitive integer schema produces wrapped Reflect.Primitive") {
        val jsonSchema = JsonSchema.integer()
        val schema     = Schema.fromJsonSchema(jsonSchema)

        val wrapped      = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val innerWrapped = wrapped.flatMap(_.asWrapperUnknown).map(_.wrapper.wrapped)
        assertTrue(innerWrapped.exists(_.isPrimitive))
      },
      test("primitive boolean schema produces wrapped Reflect.Primitive") {
        val jsonSchema = JsonSchema.boolean
        val schema     = Schema.fromJsonSchema(jsonSchema)

        val wrapped      = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val innerWrapped = wrapped.flatMap(_.asWrapperUnknown).map(_.wrapper.wrapped)
        assertTrue(innerWrapped.exists(_.isPrimitive))
      },
      test("JsonSchema.True produces Dynamic") {
        val jsonSchema = JsonSchema.True
        val schema     = Schema.fromJsonSchema(jsonSchema)

        val wrapped = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isDynamic))
      },
      test("JsonSchema.False produces Dynamic") {
        val jsonSchema = JsonSchema.False
        val schema     = Schema.fromJsonSchema(jsonSchema)

        val wrapped = schema.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isDynamic))
      }
    ),
    suite("Behavioral compatibility invariants")(
      test("required fields must be present in closed record") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name", "age")),
          additionalProperties = Some(JsonSchema.False)
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)

        val result = codec.decode("""{"name": "Alice"}""")
        assertTrue(result.isLeft)
      },
      test("closed record decodes known fields (extra fields silently dropped for now)") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string())),
          required = Some(Set("name")),
          additionalProperties = Some(JsonSchema.False)
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)

        val result = codec.decode("""{"name": "Alice", "extra": 123}""")
        assertTrue(result.isRight)
      },
      test("open record decodes known fields (extra fields silently dropped for now)") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string()))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)

        val result = codec.decode("""{"name": "Alice", "extra": 123}""")
        assertTrue(result.isRight)
      },
      test("round-trip through closed record preserves known fields") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string())),
          required = Some(Set("name")),
          additionalProperties = Some(JsonSchema.False)
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)

        val result = codec.decode("""{"name": "Alice"}""")
        assertTrue(result.isRight)
        result.map { decoded =>
          val encoded = codec.encodeToString(decoded)
          assertTrue(encoded.contains("Alice"))
        }.getOrElse(assertTrue(false))
      },
      test("round-trip through array preserves element count") {
        val jsonSchema  = JsonSchema.array(items = Some(JsonSchema.string()))
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)

        val result = codec.decode("""["a", "b", "c"]""")
        assertTrue(result.isRight)
        result.map { decoded =>
          val encoded = codec.encodeToString(decoded)
          val count   = encoded.count(_ == '"') / 2
          assertTrue(count == 3)
        }.getOrElse(assertTrue(false))
      }
    ),
    suite("Existing validation tests still pass")(
      test("string schema accepts string values") {
        val jsonSchema  = JsonSchema.string()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode(""""hello world"""")
        assertTrue(result.isRight)
      },
      test("integer schema accepts integer values") {
        val jsonSchema  = JsonSchema.integer()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("42")
        assertTrue(result.isRight)
      },
      test("string schema rejects non-string values") {
        val jsonSchema  = JsonSchema.string()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("42")
        assertTrue(result.isLeft)
      },
      test("integer schema rejects string values") {
        val jsonSchema  = JsonSchema.integer()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode(""""not a number"""")
        assertTrue(result.isLeft)
      },
      test("array items schema validation fails for wrong item type") {
        val jsonSchema  = JsonSchema.array(items = Some(JsonSchema.integer()))
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""[1, "two", 3]""")
        assertTrue(result.isLeft)
      },
      test("object schema rejects missing required properties") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name", "age"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""{"name": "Alice"}""")
        assertTrue(result.isLeft)
      }
    )
  )
}
