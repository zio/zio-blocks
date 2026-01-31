package zio.blocks.schema.json

import zio.blocks.chunk.ChunkMap
import zio.blocks.schema._
import zio.test._

object JsonSchemaToSchemaSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Address(street: String, city: String, zipCode: Option[String])
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class Company(name: String, address: Address, employees: List[Person])
  object Company {
    implicit val schema: Schema[Company] = Schema.derived
  }

  sealed trait Color
  object Color {
    case object Red   extends Color
    case object Green extends Color
    case object Blue  extends Color

    implicit val schema: Schema[Color] = Schema.derived
  }

  sealed trait Shape
  object Shape {
    case class Circle(radius: Double)                   extends Shape
    case class Rectangle(width: Double, height: Double) extends Shape

    object Circle {
      implicit val schema: Schema[Circle] = Schema.derived
    }
    object Rectangle {
      implicit val schema: Schema[Rectangle] = Schema.derived
    }

    implicit val schema: Schema[Shape] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("JsonSchemaToSchemaSpec")(
    suite("Primitive JsonSchema to Schema")(
      test("string schema converts to Schema with String primitive") {
        val jsonSchema  = JsonSchema.string()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode(""""hello"""")
        assertTrue(result.isRight)
      },
      test("integer schema converts to Schema with BigInt primitive") {
        val jsonSchema  = JsonSchema.integer()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("42")
        assertTrue(result.isRight)
      },
      test("number schema converts to Schema with BigDecimal primitive") {
        val jsonSchema  = JsonSchema.number()
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("3.14159")
        assertTrue(result.isRight)
      },
      test("boolean schema converts to Schema with Boolean primitive") {
        val jsonSchema  = JsonSchema.boolean
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        assertTrue(
          codec.decode("true").isRight,
          codec.decode("false").isRight
        )
      },
      test("null schema converts to Dynamic Schema") {
        val jsonSchema  = JsonSchema.nullSchema
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("null")
        assertTrue(result.isRight)
      }
    ),
    suite("Validation translation from JsonSchema")(
      test("string minLength/maxLength translates to String validation") {
        val jsonSchema = JsonSchema.string(
          minLength = Some(NonNegativeInt.unsafe(5)),
          maxLength = Some(NonNegativeInt.unsafe(10))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)

        assertTrue(
          codec.decode(""""hello"""").isRight,
          codec.decode(""""hi"""").isLeft,
          codec.decode(""""verylongstring"""").isLeft
        )
      },
      test("string pattern translates to Pattern validation") {
        val jsonSchema  = JsonSchema.string(pattern = Some(RegexPattern.unsafe("^[a-z]+$")))
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)

        assertTrue(
          codec.decode(""""hello"""").isRight,
          codec.decode(""""HELLO"""").isLeft,
          codec.decode(""""hello123"""").isLeft
        )
      },
      test("integer minimum/maximum translates to Range validation") {
        val jsonSchema = JsonSchema.integer(
          minimum = Some(BigDecimal(0)),
          maximum = Some(BigDecimal(100))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)

        assertTrue(
          codec.decode("50").isRight,
          codec.decode("-1").isLeft,
          codec.decode("101").isLeft
        )
      },
      test("number minimum/maximum translates to Range validation") {
        val jsonSchema = JsonSchema.number(
          minimum = Some(BigDecimal(0.0)),
          maximum = Some(BigDecimal(100.0))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)

        assertTrue(
          codec.decode("50.5").isRight,
          codec.decode("-0.1").isLeft,
          codec.decode("100.1").isLeft
        )
      },
      test("string minLength=1 translates to NonEmpty validation") {
        val jsonSchema  = JsonSchema.string(minLength = Some(NonNegativeInt.one))
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)

        assertTrue(
          codec.decode(""""a"""").isRight,
          codec.decode("\"\"").isLeft
        )
      }
    ),
    suite("Record/Object JsonSchema to Schema")(
      test("object with properties converts to Record") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name", "age"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""{"name": "Alice", "age": 30}""")
        assertTrue(result.isRight)
      },
      test("object schema rejects missing required fields") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name", "age"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""{"name": "Alice"}""")
        assertTrue(result.isLeft)
      },
      test("nested object schema converts correctly") {
        val addressSchema = JsonSchema.obj(
          properties = Some(ChunkMap("city" -> JsonSchema.string(), "zip" -> JsonSchema.string())),
          required = Some(Set("city", "zip"))
        )
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "address" -> addressSchema)),
          required = Some(Set("name", "address"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""{"name": "Bob", "address": {"city": "NYC", "zip": "10001"}}""")
        assertTrue(result.isRight)
      },
      test("closed object (additionalProperties: false) produces closed Record") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string())),
          required = Some(Set("name")),
          additionalProperties = Some(JsonSchema.False)
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)

        val wrapped = schemaForJs.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isRecord))
      },
      test("open object produces Record with open modifier") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string()))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)

        val wrapped         = schemaForJs.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val modifiers       = wrapped.map(_.modifiers).getOrElse(Nil)
        val hasOpenModifier = modifiers.exists {
          case Modifier.config("json.closure", "open") => true
          case _                                       => false
        }
        assertTrue(wrapped.exists(_.isRecord) && hasOpenModifier)
      }
    ),
    suite("Collection JsonSchema to Schema")(
      test("array with items converts to Sequence") {
        val jsonSchema  = JsonSchema.array(items = Some(JsonSchema.string()))
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""["a", "b", "c"]""")
        assertTrue(result.isRight)
      },
      test("array rejects wrong item types") {
        val jsonSchema  = JsonSchema.array(items = Some(JsonSchema.integer()))
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""[1, "two", 3]""")
        assertTrue(result.isLeft)
      },
      test("object with only additionalProperties converts to Map") {
        val jsonSchema = JsonSchema.obj(
          additionalProperties = Some(JsonSchema.integer())
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)

        val wrapped      = schemaForJs.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val innerWrapped = wrapped.flatMap(_.asWrapperUnknown).map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isWrapper) && innerWrapped.exists(_.isMap))
      },
      test("tuple schema (prefixItems + items:false) converts to Record with positional fields") {
        val jsonSchema = JsonSchema.Object(
          prefixItems = Some(new ::(JsonSchema.string(), JsonSchema.integer() :: Nil)),
          items = Some(JsonSchema.False)
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)

        val wrapped      = schemaForJs.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val innerWrapped = wrapped.flatMap(_.asWrapperUnknown).map(_.wrapper.wrapped)
        val record       = innerWrapped.flatMap(_.asRecord)
        val fieldNames   = record.map(_.fields.map(_.name).toList).getOrElse(Nil)
        assertTrue(fieldNames == List("_1", "_2"))
      }
    ),
    suite("Enum/Variant JsonSchema to Schema")(
      test("string enum converts to Variant") {
        val jsonSchema = JsonSchema.Object(
          `enum` = Some(new ::(Json.String("Red"), Json.String("Green") :: Json.String("Blue") :: Nil))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)

        val wrapped = schemaForJs.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isVariant))
      },
      test("string enum has correct case names") {
        val jsonSchema = JsonSchema.Object(
          `enum` = Some(new ::(Json.String("Red"), Json.String("Green") :: Json.String("Blue") :: Nil))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)

        val wrapped   = schemaForJs.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val variant   = wrapped.flatMap(_.asVariant)
        val caseNames = variant.map(_.cases.map(_.name).toSet).getOrElse(Set.empty)
        assertTrue(caseNames == Set("Red", "Green", "Blue"))
      },
      test("key-discriminated oneOf converts to Variant") {
        val jsonSchema = JsonSchema.Object(
          oneOf = Some(
            new ::(
              JsonSchema.obj(
                properties = Some(
                  ChunkMap("Circle" -> JsonSchema.obj(properties = Some(ChunkMap("radius" -> JsonSchema.number()))))
                ),
                required = Some(Set("Circle"))
              ),
              JsonSchema.obj(
                properties = Some(
                  ChunkMap(
                    "Rectangle" -> JsonSchema.obj(properties =
                      Some(ChunkMap("width" -> JsonSchema.number(), "height" -> JsonSchema.number()))
                    )
                  )
                ),
                required = Some(Set("Rectangle"))
              ) :: Nil
            )
          )
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)

        val wrapped   = schemaForJs.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val variant   = wrapped.flatMap(_.asVariant)
        val caseNames = variant.map(_.cases.map(_.name).toSet).getOrElse(Set.empty)
        assertTrue(caseNames == Set("Circle", "Rectangle"))
      }
    ),
    suite("Dynamic/True/False JsonSchema to Schema")(
      test("JsonSchema.True converts to Dynamic Schema") {
        val schemaForJs = Schema.fromJsonSchema(JsonSchema.True)
        val wrapped     = schemaForJs.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isDynamic))
      },
      test("JsonSchema.False converts to Dynamic Schema") {
        val schemaForJs = Schema.fromJsonSchema(JsonSchema.False)
        val wrapped     = schemaForJs.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isDynamic))
      },
      test("Dynamic schema accepts any JSON") {
        val schemaForJs = Schema.fromJsonSchema(JsonSchema.True)
        val codec       = schemaForJs.derive(JsonFormat)

        assertTrue(
          codec.decode(""""hello"""").isRight,
          codec.decode("42").isRight,
          codec.decode("true").isRight,
          codec.decode("null").isRight,
          codec.decode("""{"key": "value"}""").isRight,
          codec.decode("""[1, 2, 3]""").isRight
        )
      }
    ),
    suite("Schema -> JsonSchema -> Schema roundtrip (structure preservation)")(
      test("String primitive roundtrips to correct structure") {
        val jsonSchema  = Schema[String].toJsonSchema
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val structure   = schemaForJs.reflect.toString

        assertTrue(structure == "wrapper Json(wrapper DynamicValue(String))")
      },
      test("Int primitive roundtrips to BigInt (JsonSchema integer)") {
        val jsonSchema  = Schema[Int].toJsonSchema
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val structure   = schemaForJs.reflect.toString

        assertTrue(structure == "wrapper Json(wrapper DynamicValue(BigInt))")
      },
      test("Boolean primitive roundtrips to correct structure") {
        val jsonSchema  = Schema[Boolean].toJsonSchema
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val structure   = schemaForJs.reflect.toString

        assertTrue(structure == "wrapper Json(wrapper DynamicValue(Boolean))")
      },
      test("Double primitive roundtrips to BigDecimal (JsonSchema number)") {
        val jsonSchema  = Schema[Double].toJsonSchema
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val structure   = schemaForJs.reflect.toString

        assertTrue(structure == "wrapper Json(wrapper DynamicValue(BigDecimal))")
      },
      test("Person record roundtrips with correct field structure") {
        val jsonSchema  = Schema[Person].toJsonSchema
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val wrapped     = schemaForJs.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val structure   = wrapped.map(_.toString).getOrElse("")

        val expected = """record Person {
  name: wrapper DynamicValue(String)
  age: wrapper DynamicValue(BigInt)
}"""
        assertTrue(structure == expected)
      },
      test("List[Int] roundtrips to sequence of BigInt") {
        val jsonSchema  = Schema[List[Int]].toJsonSchema
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val structure   = schemaForJs.reflect.toString

        assertTrue(structure == "wrapper Json(wrapper DynamicValue(sequence Chunk[wrapper DynamicValue(BigInt)]))")
      },
      test("Map[String, Int] roundtrips to map structure") {
        val jsonSchema  = Schema[Map[String, Int]].toJsonSchema
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val structure   = schemaForJs.reflect.toString

        assertTrue(
          structure == "wrapper Json(wrapper DynamicValue(map Map[DynamicValue, wrapper DynamicValue(BigInt)]))"
        )
      },
      test("Color enum roundtrips with correct case names") {
        val jsonSchema  = Schema[Color].toJsonSchema
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val wrapped     = schemaForJs.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val structure   = wrapped.map(_.toString).getOrElse("")

        val expected = """variant Color {
  | Red
  | Green
  | Blue
}"""
        assertTrue(structure == expected)
      },
      test("Shape variant roundtrips with correct case structures") {
        val jsonSchema  = Schema[Shape].toJsonSchema
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val wrapped     = schemaForJs.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val structure   = wrapped.map(_.toString).getOrElse("")

        val expected = """variant Shape {
  | Circle(radius: wrapper DynamicValue(BigDecimal))
  | Rectangle(
      width: wrapper DynamicValue(BigDecimal),
      height: wrapper DynamicValue(BigDecimal)
    )
}"""
        assertTrue(structure == expected)
      },
      test("tuple schema roundtrips to record with positional fields") {
        val jsonSchema = JsonSchema.Object(
          prefixItems = Some(new ::(JsonSchema.string(), JsonSchema.integer() :: Nil)),
          items = Some(JsonSchema.False)
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val wrapped     = schemaForJs.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val inner       = wrapped.flatMap(_.asWrapperUnknown).map(_.wrapper.wrapped)
        val structure   = inner.map(_.toString).getOrElse("")

        val expected = """record DynamicValue {
  _1: wrapper DynamicValue(String)
  _2: wrapper DynamicValue(BigInt)
}"""
        assertTrue(structure == expected)
      },
      test("nested record roundtrips with correct nested structure") {
        val jsonSchema = JsonSchema.obj(
          properties = Some(
            ChunkMap(
              "name"    -> JsonSchema.string(),
              "address" -> JsonSchema.obj(
                properties = Some(ChunkMap("city" -> JsonSchema.string(), "zip" -> JsonSchema.string())),
                required = Some(Set("city", "zip"))
              )
            )
          ),
          required = Some(Set("name", "address"))
        )
        val schemaForJs = Schema.fromJsonSchema(jsonSchema)
        val wrapped     = schemaForJs.reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val structure   = wrapped.map(_.toString).getOrElse("")

        val expected = """record DynamicValue {
  name: wrapper DynamicValue(String)
  address:   record DynamicValue {
    city: wrapper DynamicValue(String)
    zip: wrapper DynamicValue(String)
  }
}"""
        assertTrue(structure == expected)
      }
    ),
    suite("Behavioral roundtrip (encode/decode)")(
      test("Person record encoded JSON conforms to generated JsonSchema") {
        val original      = Schema[Person]
        val jsonSchema    = original.toJsonSchema
        val originalCodec = original.derive(JsonFormat)

        val person  = Person("Alice", 30)
        val encoded = originalCodec.encodeToString(person)
        val parsed  = Json.parse(encoded)

        parsed match {
          case Right(json) => assertTrue(jsonSchema.conforms(json))
          case Left(_)     => assertTrue(false)
        }
      },
      test("Person record JSON can be decoded by Schema.fromJsonSchema") {
        val jsonSchema     = Schema[Person].toJsonSchema
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        val input   = """{"name": "Alice", "age": 30}"""
        val decoded = roundtripCodec.decode(input)

        decoded match {
          case Right(json: Json) =>
            assertTrue(
              json.get("name").one == Right(Json.String("Alice")),
              json.get("age").one == Right(Json.Number(30))
            )
          case _ => assertTrue(false)
        }
      },
      test("Color enum encoded JSON conforms to generated JsonSchema") {
        val original      = Schema[Color]
        val jsonSchema    = original.toJsonSchema
        val originalCodec = original.derive(JsonFormat)

        val color   = Color.Red
        val encoded = originalCodec.encodeToString(color)
        val parsed  = Json.parse(encoded)

        parsed match {
          case Right(json) => assertTrue(jsonSchema.conforms(json))
          case Left(_)     => assertTrue(false)
        }
      },
      test("Color enum Json validates correctly against generated JsonSchema") {
        val jsonSchema = Schema[Color].toJsonSchema
        assertTrue(
          jsonSchema.conforms(Json.String("Red")),
          jsonSchema.conforms(Json.String("Green")),
          jsonSchema.conforms(Json.String("Blue")),
          !jsonSchema.conforms(Json.String("Yellow"))
        )
      },
      test("Shape variant encoded JSON conforms to generated JsonSchema") {
        val original      = Schema[Shape]
        val jsonSchema    = original.toJsonSchema
        val originalCodec = original.derive(JsonFormat)

        val circle  = Shape.Circle(5.0): Shape
        val encoded = originalCodec.encodeToString(circle)
        val parsed  = Json.parse(encoded)

        parsed match {
          case Right(json) => assertTrue(jsonSchema.conforms(json))
          case Left(_)     => assertTrue(false)
        }
      },
      test("Shape variant Json validates correctly against generated JsonSchema") {
        val jsonSchema = Schema[Shape].toJsonSchema
        val circleJson = Json.Object("Circle" -> Json.Object("radius" -> Json.Number(5.0)))
        val rectJson   =
          Json.Object("Rectangle" -> Json.Object("width" -> Json.Number(10.0), "height" -> Json.Number(20.0)))
        val invalid = Json.Object("Triangle" -> Json.Object("base" -> Json.Number(5.0)))

        assertTrue(
          jsonSchema.conforms(circleJson),
          jsonSchema.conforms(rectJson),
          !jsonSchema.conforms(invalid)
        )
      },
      test("List[Int] encoded JSON conforms to generated JsonSchema") {
        val original      = Schema[List[Int]]
        val jsonSchema    = original.toJsonSchema
        val originalCodec = original.derive(JsonFormat)

        val list    = List(1, 2, 3)
        val encoded = originalCodec.encodeToString(list)
        val parsed  = Json.parse(encoded)

        parsed match {
          case Right(json) => assertTrue(jsonSchema.conforms(json))
          case Left(_)     => assertTrue(false)
        }
      },
      test("List[Int] JSON can be decoded by Schema.fromJsonSchema") {
        val jsonSchema     = Schema[List[Int]].toJsonSchema
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        val input   = """[1, 2, 3]"""
        val decoded = roundtripCodec.decode(input)

        decoded match {
          case Right(json: Json) =>
            assertTrue(
              json.elements.length == 3,
              json.elements.head == Json.Number(1)
            )
          case _ => assertTrue(false)
        }
      },
      test("nested Company encoded JSON conforms to generated JsonSchema") {
        val original      = Schema[Company]
        val jsonSchema    = original.toJsonSchema
        val originalCodec = original.derive(JsonFormat)

        val company = Company("Acme", Address("123 Main", "Springfield", Some("12345")), List(Person("Alice", 30)))
        val encoded = originalCodec.encodeToString(company)
        val parsed  = Json.parse(encoded)

        parsed match {
          case Right(json) => assertTrue(jsonSchema.conforms(json))
          case Left(_)     => assertTrue(false)
        }
      },
      test("nested Company Json validates correctly against generated JsonSchema") {
        val jsonSchema = Schema[Company].toJsonSchema
        val validJson  = Json.Object(
          "name"    -> Json.String("Acme"),
          "address" -> Json.Object(
            "street"  -> Json.String("123 Main St"),
            "city"    -> Json.String("Springfield"),
            "zipCode" -> Json.String("12345")
          ),
          "employees" -> Json.Array(
            Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))
          )
        )
        val missingRequired = Json.Object(
          "name"      -> Json.String("Acme"),
          "employees" -> Json.Array()
        )

        assertTrue(
          jsonSchema.conforms(validJson),
          !jsonSchema.conforms(missingRequired)
        )
      }
    ),
    suite("Validation roundtrip")(
      test("Byte constraints survive roundtrip") {
        val original       = Schema[Byte]
        val jsonSchema     = original.toJsonSchema
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("127").isRight,
          roundtripCodec.decode("-128").isRight,
          roundtripCodec.decode("128").isLeft,
          roundtripCodec.decode("-129").isLeft
        )
      },
      test("Short constraints survive roundtrip") {
        val original       = Schema[Short]
        val jsonSchema     = original.toJsonSchema
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("32767").isRight,
          roundtripCodec.decode("-32768").isRight,
          roundtripCodec.decode("32768").isLeft,
          roundtripCodec.decode("-32769").isLeft
        )
      },
      test("Char length constraints survive roundtrip") {
        val original       = Schema[Char]
        val jsonSchema     = original.toJsonSchema
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode(""""a"""").isRight,
          roundtripCodec.decode("\"\"").isLeft,
          roundtripCodec.decode(""""ab"""").isLeft
        )
      },
      test("Int constraints survive roundtrip (no range limits)") {
        val original       = Schema[Int]
        val jsonSchema     = original.toJsonSchema
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("0").isRight,
          roundtripCodec.decode("2147483647").isRight,
          roundtripCodec.decode("-2147483648").isRight
        )
      },
      test("Long constraints survive roundtrip (no range limits)") {
        val original       = Schema[Long]
        val jsonSchema     = original.toJsonSchema
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("0").isRight,
          roundtripCodec.decode("9223372036854775807").isRight,
          roundtripCodec.decode("-9223372036854775808").isRight
        )
      },
      test("BigInt constraints survive roundtrip") {
        val original       = Schema[BigInt]
        val jsonSchema     = original.toJsonSchema
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("0").isRight,
          roundtripCodec.decode("999999999999999999999999999999").isRight,
          roundtripCodec.decode("-999999999999999999999999999999").isRight
        )
      },
      test("BigDecimal constraints survive roundtrip") {
        val original       = Schema[BigDecimal]
        val jsonSchema     = original.toJsonSchema
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("0.0").isRight,
          roundtripCodec.decode("3.14159265358979323846").isRight,
          roundtripCodec.decode("-999999999999.999999999999").isRight
        )
      },
      test("Float constraints survive roundtrip") {
        val original       = Schema[Float]
        val jsonSchema     = original.toJsonSchema
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("0.0").isRight,
          roundtripCodec.decode("3.14").isRight,
          roundtripCodec.decode("-123.456").isRight
        )
      },
      test("Double constraints survive roundtrip") {
        val original       = Schema[Double]
        val jsonSchema     = original.toJsonSchema
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("0.0").isRight,
          roundtripCodec.decode("3.141592653589793").isRight,
          roundtripCodec.decode("-1.7976931348623157E308").isRight
        )
      },
      test("Boolean constraints survive roundtrip") {
        val original       = Schema[Boolean]
        val jsonSchema     = original.toJsonSchema
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("true").isRight,
          roundtripCodec.decode("false").isRight,
          roundtripCodec.decode("\"true\"").isLeft
        )
      },
      test("String constraints survive roundtrip") {
        val original       = Schema[String]
        val jsonSchema     = original.toJsonSchema
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("\"\"").isRight,
          roundtripCodec.decode(""""hello world"""").isRight,
          roundtripCodec.decode(""""special chars: \n\t\r"""").isRight
        )
      }
    ),
    suite("JsonSchema validation constraints roundtrip")(
      test("string minLength constraint survives roundtrip") {
        val jsonSchema     = JsonSchema.string(minLength = Some(NonNegativeInt.unsafe(3)))
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode(""""abc"""").isRight,
          roundtripCodec.decode(""""abcd"""").isRight,
          roundtripCodec.decode(""""ab"""").isLeft,
          roundtripCodec.decode(""""a"""").isLeft,
          roundtripCodec.decode("\"\"").isLeft
        )
      },
      test("string maxLength constraint survives roundtrip") {
        val jsonSchema     = JsonSchema.string(maxLength = Some(NonNegativeInt.unsafe(5)))
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("\"\"").isRight,
          roundtripCodec.decode(""""abc"""").isRight,
          roundtripCodec.decode(""""abcde"""").isRight,
          roundtripCodec.decode(""""abcdef"""").isLeft,
          roundtripCodec.decode(""""this is too long"""").isLeft
        )
      },
      test("string minLength and maxLength together survive roundtrip") {
        val jsonSchema = JsonSchema.string(
          minLength = Some(NonNegativeInt.unsafe(2)),
          maxLength = Some(NonNegativeInt.unsafe(4))
        )
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode(""""ab"""").isRight,
          roundtripCodec.decode(""""abc"""").isRight,
          roundtripCodec.decode(""""abcd"""").isRight,
          roundtripCodec.decode(""""a"""").isLeft,
          roundtripCodec.decode(""""abcde"""").isLeft
        )
      },
      test("string pattern constraint survives roundtrip") {
        val jsonSchema     = JsonSchema.string(pattern = Some(RegexPattern.unsafe("^[a-z]+$")))
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode(""""abc"""").isRight,
          roundtripCodec.decode(""""hello"""").isRight,
          roundtripCodec.decode(""""ABC"""").isLeft,
          roundtripCodec.decode(""""Hello"""").isLeft,
          roundtripCodec.decode(""""hello123"""").isLeft
        )
      },
      test("string email-like pattern constraint survives roundtrip") {
        val jsonSchema     = JsonSchema.string(pattern = Some(RegexPattern.unsafe("^[^@]+@[^@]+\\.[^@]+$")))
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode(""""test@example.com"""").isRight,
          roundtripCodec.decode(""""user@domain.org"""").isRight,
          roundtripCodec.decode(""""invalid"""").isLeft,
          roundtripCodec.decode(""""@missing.com"""").isLeft
        )
      },
      test("integer minimum constraint survives roundtrip") {
        val jsonSchema     = JsonSchema.integer(minimum = Some(BigDecimal(0)))
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("0").isRight,
          roundtripCodec.decode("1").isRight,
          roundtripCodec.decode("100").isRight,
          roundtripCodec.decode("-1").isLeft,
          roundtripCodec.decode("-100").isLeft
        )
      },
      test("integer maximum constraint survives roundtrip") {
        val jsonSchema     = JsonSchema.integer(maximum = Some(BigDecimal(100)))
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("-1000").isRight,
          roundtripCodec.decode("0").isRight,
          roundtripCodec.decode("100").isRight,
          roundtripCodec.decode("101").isLeft,
          roundtripCodec.decode("1000").isLeft
        )
      },
      test("integer minimum and maximum together survive roundtrip") {
        val jsonSchema = JsonSchema.integer(
          minimum = Some(BigDecimal(10)),
          maximum = Some(BigDecimal(20))
        )
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("10").isRight,
          roundtripCodec.decode("15").isRight,
          roundtripCodec.decode("20").isRight,
          roundtripCodec.decode("9").isLeft,
          roundtripCodec.decode("21").isLeft
        )
      },
      test("number minimum constraint survives roundtrip") {
        val jsonSchema     = JsonSchema.number(minimum = Some(BigDecimal(0.0)))
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("0.0").isRight,
          roundtripCodec.decode("0.001").isRight,
          roundtripCodec.decode("100.5").isRight,
          roundtripCodec.decode("-0.001").isLeft,
          roundtripCodec.decode("-100.0").isLeft
        )
      },
      test("number maximum constraint survives roundtrip") {
        val jsonSchema     = JsonSchema.number(maximum = Some(BigDecimal(100.0)))
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("-1000.0").isRight,
          roundtripCodec.decode("0.0").isRight,
          roundtripCodec.decode("100.0").isRight,
          roundtripCodec.decode("100.001").isLeft,
          roundtripCodec.decode("1000.0").isLeft
        )
      },
      test("number minimum and maximum together survive roundtrip") {
        val jsonSchema = JsonSchema.number(
          minimum = Some(BigDecimal(-1.0)),
          maximum = Some(BigDecimal(1.0))
        )
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("-1.0").isRight,
          roundtripCodec.decode("0.0").isRight,
          roundtripCodec.decode("0.5").isRight,
          roundtripCodec.decode("1.0").isRight,
          roundtripCodec.decode("-1.001").isLeft,
          roundtripCodec.decode("1.001").isLeft
        )
      },
      test("exclusiveMinimum constraint survives roundtrip") {
        val jsonSchema     = JsonSchema.integer(exclusiveMinimum = Some(BigDecimal(0)))
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("1").isRight,
          roundtripCodec.decode("100").isRight,
          roundtripCodec.decode("0").isLeft,
          roundtripCodec.decode("-1").isLeft
        )
      },
      test("exclusiveMaximum constraint survives roundtrip") {
        val jsonSchema     = JsonSchema.integer(exclusiveMaximum = Some(BigDecimal(100)))
        val schemaForJs    = Schema.fromJsonSchema(jsonSchema)
        val roundtripCodec = schemaForJs.derive(JsonFormat)

        assertTrue(
          roundtripCodec.decode("-100").isRight,
          roundtripCodec.decode("0").isRight,
          roundtripCodec.decode("99").isRight,
          roundtripCodec.decode("100").isLeft,
          roundtripCodec.decode("101").isLeft
        )
      }
    )
  )
}
