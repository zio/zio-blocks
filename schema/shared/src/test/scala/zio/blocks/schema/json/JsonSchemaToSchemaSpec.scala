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
        val codec = Schema.fromJsonSchema(JsonSchema.string()).derive(JsonFormat)
        assertTrue(codec.decode(""""hello"""").isRight)
      },
      test("integer schema converts to Schema with BigInt primitive") {
        val codec = Schema.fromJsonSchema(JsonSchema.integer()).derive(JsonFormat)
        assertTrue(codec.decode("42").isRight)
      },
      test("number schema converts to Schema with BigDecimal primitive") {
        val codec = Schema.fromJsonSchema(JsonSchema.number()).derive(JsonFormat)
        assertTrue(codec.decode("3.14159").isRight)
      },
      test("boolean schema converts to Schema with Boolean primitive") {
        val codec = Schema.fromJsonSchema(JsonSchema.boolean).derive(JsonFormat)
        assertTrue(
          codec.decode("true").isRight,
          codec.decode("false").isRight
        )
      },
      test("null schema converts to Dynamic Schema") {
        val codec = Schema.fromJsonSchema(JsonSchema.nullSchema).derive(JsonFormat)
        assertTrue(codec.decode("null").isRight)
      }
    ),
    suite("Validation translation from JsonSchema")(
      test("string minLength/maxLength translates to String validation") {
        val codec = Schema
          .fromJsonSchema(
            JsonSchema.string(
              minLength = Some(NonNegativeInt.unsafe(5)),
              maxLength = Some(NonNegativeInt.unsafe(10))
            )
          )
          .derive(JsonFormat)
        assertTrue(
          codec.decode(""""hello"""").isRight,
          codec.decode(""""hi"""").isLeft,
          codec.decode(""""verylongstring"""").isLeft
        )
      },
      test("string pattern translates to Pattern validation") {
        val codec = Schema
          .fromJsonSchema(JsonSchema.string(pattern = Some(RegexPattern.unsafe("^[a-z]+$"))))
          .derive(JsonFormat)
        assertTrue(
          codec.decode(""""hello"""").isRight,
          codec.decode(""""HELLO"""").isLeft,
          codec.decode(""""hello123"""").isLeft
        )
      },
      test("integer minimum/maximum translates to Range validation") {
        val codec = Schema
          .fromJsonSchema(
            JsonSchema.integer(
              minimum = Some(BigDecimal(0)),
              maximum = Some(BigDecimal(100))
            )
          )
          .derive(JsonFormat)
        assertTrue(
          codec.decode("50").isRight,
          codec.decode("-1").isLeft,
          codec.decode("101").isLeft
        )
      },
      test("number minimum/maximum translates to Range validation") {
        val codec = Schema
          .fromJsonSchema(
            JsonSchema.number(
              minimum = Some(BigDecimal(0.0)),
              maximum = Some(BigDecimal(100.0))
            )
          )
          .derive(JsonFormat)
        assertTrue(
          codec.decode("50.5").isRight,
          codec.decode("-0.1").isLeft,
          codec.decode("100.1").isLeft
        )
      },
      test("string minLength=1 translates to NonEmpty validation") {
        val codec = Schema
          .fromJsonSchema(JsonSchema.string(minLength = Some(NonNegativeInt.one)))
          .derive(JsonFormat)
        assertTrue(
          codec.decode(""""a"""").isRight,
          codec.decode("\"\"").isLeft
        )
      }
    ),
    suite("Record/Object JsonSchema to Schema")(
      test("object with properties converts to Record") {
        val codec = Schema
          .fromJsonSchema(
            JsonSchema.obj(
              properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
              required = Some(Set("name", "age"))
            )
          )
          .derive(JsonFormat)
        assertTrue(codec.decode("""{"name": "Alice", "age": 30}""").isRight)
      },
      test("object schema rejects missing required fields") {
        val codec = Schema
          .fromJsonSchema(
            JsonSchema.obj(
              properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
              required = Some(Set("name", "age"))
            )
          )
          .derive(JsonFormat)
        assertTrue(codec.decode("""{"name": "Alice"}""").isLeft)
      },
      test("nested object schema converts correctly") {
        val codec = Schema
          .fromJsonSchema(
            JsonSchema.obj(
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
          )
          .derive(JsonFormat)
        assertTrue(codec.decode("""{"name": "Bob", "address": {"city": "NYC", "zip": "10001"}}""").isRight)
      },
      test("closed object (additionalProperties: false) produces closed Record") {
        val wrapped = Schema
          .fromJsonSchema(
            JsonSchema.obj(
              properties = Some(ChunkMap("name" -> JsonSchema.string())),
              required = Some(Set("name")),
              additionalProperties = Some(JsonSchema.False)
            )
          )
          .reflect
          .asWrapperUnknown
          .map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isRecord))
      },
      test("open object produces Record with open modifier") {
        val wrapped = Schema
          .fromJsonSchema(
            JsonSchema.obj(
              properties = Some(ChunkMap("name" -> JsonSchema.string()))
            )
          )
          .reflect
          .asWrapperUnknown
          .map(_.wrapper.wrapped)
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
        val codec = Schema.fromJsonSchema(JsonSchema.array(items = Some(JsonSchema.string()))).derive(JsonFormat)
        assertTrue(codec.decode("""["a", "b", "c"]""").isRight)
      },
      test("array rejects wrong item types") {
        val codec = Schema.fromJsonSchema(JsonSchema.array(items = Some(JsonSchema.integer()))).derive(JsonFormat)
        assertTrue(codec.decode("""[1, "two", 3]""").isLeft)
      },
      test("object with only additionalProperties converts to Map") {
        val wrapped = Schema
          .fromJsonSchema(
            JsonSchema.obj(
              additionalProperties = Some(JsonSchema.integer())
            )
          )
          .reflect
          .asWrapperUnknown
          .map(_.wrapper.wrapped)
        val innerWrapped = wrapped.flatMap(_.asWrapperUnknown).map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isWrapper) && innerWrapped.exists(_.isMap))
      },
      test("tuple schema (prefixItems + items:false) converts to Record with positional fields") {
        val wrapped = Schema
          .fromJsonSchema(
            JsonSchema.Object(
              prefixItems = Some(new ::(JsonSchema.string(), JsonSchema.integer() :: Nil)),
              items = Some(JsonSchema.False)
            )
          )
          .reflect
          .asWrapperUnknown
          .map(_.wrapper.wrapped)
        val innerWrapped = wrapped.flatMap(_.asWrapperUnknown).map(_.wrapper.wrapped)
        val record       = innerWrapped.flatMap(_.asRecord)
        val fields       = record.map(_.fields.map(_.name).toList).getOrElse(Nil)
        assertTrue(fields == List("_1", "_2"))
      }
    ),
    suite("Enum/Variant JsonSchema to Schema")(
      test("string enum converts to Variant") {
        val wrapped = Schema
          .fromJsonSchema(
            JsonSchema.Object(
              `enum` = Some(new ::(Json.String("Red"), Json.String("Green") :: Json.String("Blue") :: Nil))
            )
          )
          .reflect
          .asWrapperUnknown
          .map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isVariant))
      },
      test("string enum has correct case names") {
        val wrapped = Schema
          .fromJsonSchema(
            JsonSchema.Object(
              `enum` = Some(new ::(Json.String("Red"), Json.String("Green") :: Json.String("Blue") :: Nil))
            )
          )
          .reflect
          .asWrapperUnknown
          .map(_.wrapper.wrapped)
        val variant = wrapped.flatMap(_.asVariant)
        val cases   = variant.map(_.cases.map(_.name).toSet).getOrElse(Set.empty)
        assertTrue(cases == Set("Red", "Green", "Blue"))
      },
      test("key-discriminated oneOf converts to Variant") {
        val wrapped = Schema
          .fromJsonSchema(
            JsonSchema.Object(
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
          )
          .reflect
          .asWrapperUnknown
          .map(_.wrapper.wrapped)
        val variant = wrapped.flatMap(_.asVariant)
        val cases   = variant.map(_.cases.map(_.name).toSet).getOrElse(Set.empty)
        assertTrue(cases == Set("Circle", "Rectangle"))
      }
    ),
    suite("Dynamic/True/False JsonSchema to Schema")(
      test("JsonSchema.True converts to Dynamic Schema") {
        val wrapped = Schema.fromJsonSchema(JsonSchema.True).reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isDynamic))
      },
      test("JsonSchema.False converts to Dynamic Schema") {
        val wrapped = Schema.fromJsonSchema(JsonSchema.False).reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        assertTrue(wrapped.exists(_.isDynamic))
      },
      test("Dynamic schema accepts any JSON") {
        val codec = Schema.fromJsonSchema(JsonSchema.True).derive(JsonFormat)
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
        val structure = Schema.fromJsonSchema(Schema[String].toJsonSchema).reflect.toString
        assertTrue(structure == "wrapper Json(wrapper DynamicValue(String))")
      },
      test("Int primitive roundtrips to BigInt (JsonSchema integer)") {
        val structure = Schema.fromJsonSchema(Schema[Int].toJsonSchema).reflect.toString
        assertTrue(structure == "wrapper Json(wrapper DynamicValue(BigInt @Range(min=-2147483648, max=2147483647)))")
      },
      test("Boolean primitive roundtrips to correct structure") {
        val structure = Schema.fromJsonSchema(Schema[Boolean].toJsonSchema).reflect.toString
        assertTrue(structure == "wrapper Json(wrapper DynamicValue(Boolean))")
      },
      test("Double primitive roundtrips to BigDecimal (JsonSchema number)") {
        val structure = Schema.fromJsonSchema(Schema[Double].toJsonSchema).reflect.toString
        assertTrue(structure == "wrapper Json(wrapper DynamicValue(BigDecimal))")
      },
      test("Person record roundtrips with correct field structure") {
        val wrapped = Schema
          .fromJsonSchema(Schema[Person].toJsonSchema)
          .reflect
          .asWrapperUnknown
          .map(_.wrapper.wrapped)
        val structure = wrapped.map(_.toString).getOrElse("")
        assertTrue(
          structure ==
            """record Person {
              |  name: wrapper DynamicValue(String)
              |  age: wrapper DynamicValue(BigInt @Range(min=-2147483648, max=2147483647))
              |}""".stripMargin
        )
      },
      test("List[Int] roundtrips to sequence of BigInt") {
        val structure = Schema.fromJsonSchema(Schema[List[Int]].toJsonSchema).reflect.toString
        assertTrue(
          structure == "wrapper Json(wrapper DynamicValue(sequence Chunk[wrapper DynamicValue(BigInt @Range(min=-2147483648, max=2147483647))]))"
        )
      },
      test("Map[String, Int] roundtrips to map structure") {
        val structure = Schema.fromJsonSchema(Schema[Map[String, Int]].toJsonSchema).reflect.toString
        assertTrue(
          structure == "wrapper Json(wrapper DynamicValue(map Map[DynamicValue, wrapper DynamicValue(BigInt @Range(min=-2147483648, max=2147483647))]))"
        )
      },
      test("Color enum roundtrips with correct case names") {
        val wrapped = Schema
          .fromJsonSchema(Schema[Color].toJsonSchema)
          .reflect
          .asWrapperUnknown
          .map(_.wrapper.wrapped)
        assertTrue(
          wrapped.map(_.toString).getOrElse("") ==
            """variant Color {
              |  | Red
              |  | Green
              |  | Blue
              |}""".stripMargin
        )
      },
      test("Shape variant roundtrips with correct case structures") {
        val wrapped = Schema.fromJsonSchema(Schema[Shape].toJsonSchema).reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        assertTrue(
          wrapped.map(_.toString).getOrElse("") ==
            """variant Shape {
              |  | Circle(radius: wrapper DynamicValue(BigDecimal))
              |  | Rectangle(
              |      width: wrapper DynamicValue(BigDecimal),
              |      height: wrapper DynamicValue(BigDecimal)
              |    )
              |}""".stripMargin
        )
      },
      test("tuple schema roundtrips to record with positional fields") {
        val wrapped = Schema
          .fromJsonSchema(
            JsonSchema.Object(
              prefixItems = Some(new ::(JsonSchema.string(), JsonSchema.integer() :: Nil)),
              items = Some(JsonSchema.False)
            )
          )
          .reflect
          .asWrapperUnknown
          .map(_.wrapper.wrapped)
        val inner = wrapped.flatMap(_.asWrapperUnknown).map(_.wrapper.wrapped)
        assertTrue(
          inner.map(_.toString).getOrElse("") ==
            """record DynamicValue {
              |  _1: wrapper DynamicValue(String)
              |  _2: wrapper DynamicValue(BigInt)
              |}""".stripMargin
        )
      },
      test("nested record roundtrips with correct nested structure") {
        val wrapped = Schema
          .fromJsonSchema(
            JsonSchema.obj(
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
          )
          .reflect
          .asWrapperUnknown
          .map(_.wrapper.wrapped)
        assertTrue(
          wrapped.map(_.toString).getOrElse("") ==
            """record DynamicValue {
              |  name: wrapper DynamicValue(String)
              |  address:   record DynamicValue {
              |    city: wrapper DynamicValue(String)
              |    zip: wrapper DynamicValue(String)
              |  }
              |}""".stripMargin
        )
      }
    ),
    suite("Behavioral roundtrip (encode/decode)")(
      test("Person record encoded JSON conforms to generated JsonSchema") {
        val codec = Schema[Person].derive(JsonFormat)
        Json.parse(codec.encodeToString(Person("Alice", 30))) match {
          case Right(json) => assertTrue(Schema[Person].toJsonSchema.conforms(json))
          case _           => assertTrue(false)
        }
      },
      test("Person record JSON can be decoded by Schema.fromJsonSchema") {
        val codec = Schema.fromJsonSchema(Schema[Person].toJsonSchema).derive(JsonFormat)
        codec.decode("""{"name": "Alice", "age": 30}""") match {
          case Right(json) =>
            assertTrue(
              json.get("name").one == Right(Json.String("Alice")),
              json.get("age").one == Right(Json.Number(30))
            )
          case _ => assertTrue(false)
        }
      },
      test("Color enum encoded JSON conforms to generated JsonSchema") {
        val codec = Schema[Color].derive(JsonFormat)
        Json.parse(codec.encodeToString(Color.Red)) match {
          case Right(json) => assertTrue(Schema[Color].toJsonSchema.conforms(json))
          case _           => assertTrue(false)
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
        val codec = Schema[Shape].derive(JsonFormat)
        Json.parse(codec.encodeToString(Shape.Circle(5.0): Shape)) match {
          case Right(json) => assertTrue(Schema[Shape].toJsonSchema.conforms(json))
          case _           => assertTrue(false)
        }
      },
      test("Shape variant Json validates correctly against generated JsonSchema") {
        val json1 = Json.Object("Circle" -> Json.Object("radius" -> Json.Number(5.0)))
        val json2 = Json.Object("Rectangle" -> Json.Object("width" -> Json.Number(10.0), "height" -> Json.Number(20.0)))
        val json3 = Json.Object("Triangle" -> Json.Object("base" -> Json.Number(5.0)))
        assertTrue(
          Schema[Shape].toJsonSchema.conforms(json1),
          Schema[Shape].toJsonSchema.conforms(json2),
          !Schema[Shape].toJsonSchema.conforms(json3)
        )
      },
      test("List[Int] encoded JSON conforms to generated JsonSchema") {
        val codec = Schema[List[Int]].derive(JsonFormat)
        Json.parse(codec.encodeToString(List(1, 2, 3))) match {
          case Right(json) => assertTrue(Schema[List[Int]].toJsonSchema.conforms(json))
          case _           => assertTrue(false)
        }
      },
      test("List[Int] JSON can be decoded by Schema.fromJsonSchema") {
        val codec = Schema.fromJsonSchema(Schema[List[Int]].toJsonSchema).derive(JsonFormat)
        codec.decode("""[1, 2, 3]""") match {
          case Right(json) =>
            assertTrue(
              json.elements.length == 3,
              json.elements.head == Json.Number(1)
            )
          case _ => assertTrue(false)
        }
      },
      test("nested Company encoded JSON conforms to generated JsonSchema") {
        val codec = Schema[Company].derive(JsonFormat)
        val value = Company("Acme", Address("123 Main", "Springfield", Some("12345")), List(Person("Alice", 30)))
        Json.parse(codec.encodeToString(value)) match {
          case Right(json) => assertTrue(Schema[Company].toJsonSchema.conforms(json))
          case _           => assertTrue(false)
        }
      },
      test("nested Company Json validates correctly against generated JsonSchema") {
        val json1 = Json.Object(
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
        val json2 = Json.Object(
          "name"      -> Json.String("Acme"),
          "employees" -> Json.Array()
        )
        assertTrue(
          Schema[Company].toJsonSchema.conforms(json1),
          !Schema[Company].toJsonSchema.conforms(json2)
        )
      }
    ),
    suite("Validation roundtrip")(
      test("Byte constraints survive roundtrip") {
        val codec = Schema.fromJsonSchema(Schema[Byte].toJsonSchema).derive(JsonFormat)
        check(Gen.byte)(x => assertTrue(codec.decode(x.toString).isRight)) &&
        assertTrue(
          codec.decode("127").isRight,
          codec.decode("-128").isRight,
          codec.decode("128").isLeft,
          codec.decode("-129").isLeft
        )
      },
      test("Short constraints survive roundtrip") {
        val codec = Schema.fromJsonSchema(Schema[Short].toJsonSchema).derive(JsonFormat)
        check(Gen.short)(x => assertTrue(codec.decode(x.toString).isRight)) &&
        assertTrue(
          codec.decode("32767").isRight,
          codec.decode("-32768").isRight,
          codec.decode("32768").isLeft,
          codec.decode("-32769").isLeft
        )
      },
      test("Char length constraints survive roundtrip") {
        val codec = Schema.fromJsonSchema(Schema[Char].toJsonSchema).derive(JsonFormat)
        check(Gen.char.filter(x => x >= ' ' && x <= 0xd800 || x >= 0xdfff)) { // excluding control and surrogate chars
          x => assertTrue(codec.decode(s""""$x"""").isRight)
        } &&
        assertTrue(
          codec.decode(""""a"""").isRight,
          codec.decode("\"\"").isLeft,
          codec.decode(""""ab"""").isLeft
        )
      },
      test("Int constraints survive roundtrip") {
        val codec = Schema.fromJsonSchema(Schema[Int].toJsonSchema).derive(JsonFormat)
        check(Gen.int)(x => assertTrue(codec.decode(x.toString).isRight)) &&
        assertTrue(
          codec.decode("2147483647").isRight,
          codec.decode("-2147483648").isRight,
          codec.decode("2147483648").isLeft,
          codec.decode("-2147483649").isLeft
        )
      },
      test("Long constraints survive roundtrip") {
        val codec = Schema.fromJsonSchema(Schema[Long].toJsonSchema).derive(JsonFormat)
        check(Gen.long)(x => assertTrue(codec.decode(x.toString).isRight)) &&
        assertTrue(
          codec.decode("9223372036854775807").isRight,
          codec.decode("-9223372036854775808").isRight,
          codec.decode("9223372036854775808").isLeft,
          codec.decode("-9223372036854775809").isLeft
        )
      },
      test("BigInt constraints survive roundtrip") {
        val codec = Schema.fromJsonSchema(Schema[BigInt].toJsonSchema).derive(JsonFormat)
        check(Gen.bigInt(BigInt("-" + "9" * 20), BigInt("9" * 20))) { x =>
          assertTrue(codec.decode(x.toString).isRight)
        } &&
        assertTrue(
          codec.decode("0").isRight,
          codec.decode("999999999999999999999999999999").isRight,
          codec.decode("-999999999999999999999999999999").isRight
        )
      },
      test("BigDecimal constraints survive roundtrip") {
        val codec = Schema.fromJsonSchema(Schema[BigDecimal].toJsonSchema).derive(JsonFormat)
        check(Gen.bigDecimal(BigDecimal("-" + "9" * 20), BigDecimal("9" * 20))) { x =>
          assertTrue(codec.decode(x.toString).isRight)
        } &&
        assertTrue(
          codec.decode("0.0").isRight,
          codec.decode("3.14159265358979323846").isRight,
          codec.decode("-999999999999.999999999999").isRight
        )
      },
      test("Float constraints survive roundtrip") {
        val codec = Schema.fromJsonSchema(Schema[Float].toJsonSchema).derive(JsonFormat)
        check(Gen.float.filter(_.isFinite))(x => assertTrue(codec.decode(x.toString).isRight)) &&
        assertTrue(
          codec.decode("0.0").isRight,
          codec.decode("3.14").isRight,
          codec.decode("-123.456").isRight
        )
      },
      test("Double constraints survive roundtrip") {
        val codec = Schema.fromJsonSchema(Schema[Double].toJsonSchema).derive(JsonFormat)
        check(Gen.double.filter(_.isFinite))(x => assertTrue(codec.decode(x.toString).isRight)) &&
        assertTrue(
          codec.decode("0.0").isRight,
          codec.decode("3.141592653589793").isRight,
          codec.decode("-1.7976931348623157E308").isRight
        )
      },
      test("Boolean constraints survive roundtrip") {
        val codec = Schema.fromJsonSchema(Schema[Boolean].toJsonSchema).derive(JsonFormat)
        assertTrue(
          codec.decode("true").isRight,
          codec.decode("false").isRight,
          codec.decode("\"true\"").isLeft
        )
      },
      test("String constraints survive roundtrip") {
        val codec = Schema.fromJsonSchema(Schema[String].toJsonSchema).derive(JsonFormat)
        check(
          Gen
            .listOfBounded(0, 5)( // excluding control, surrogate and must be escaped chars
              Gen.char.filter(x => x >= ' ' && x <= 0xd800 && x != '"' && x != '\\' && x != 0xff || x >= 0xdfff)
            )
            .map(_.mkString)
        )(x => assertTrue(codec.decode(s""""$x"""").isRight)) &&
        assertTrue(
          codec.decode("\"\"").isRight,
          codec.decode(""""hello world"""").isRight,
          codec.decode(""""special chars: \n\t\r"""").isRight
        )
      },
      test("Currency constraints survive roundtrip") {
        val codec = Schema.fromJsonSchema(Schema[java.util.Currency].toJsonSchema).derive(JsonFormat)
        check(Gen.currency)(x => assertTrue(codec.decode(s""""$x"""").isRight)) &&
        assertTrue(
          codec.decode("\"USD\"").isRight,
          codec.decode("\"USDC\"").isLeft
        )
      },
      test("UUID constraints survive roundtrip") {
        val codec = Schema.fromJsonSchema(Schema[java.util.UUID].toJsonSchema).derive(JsonFormat)
        check(Gen.uuid)(x => assertTrue(codec.decode(s""""$x"""").isRight)) &&
        assertTrue(codec.decode("\"0-0-0-0-0\"").isLeft)
      }
    ),
    suite("JsonSchema validation constraints roundtrip")(
      test("string minLength constraint survives roundtrip") {
        val codec =
          Schema.fromJsonSchema(JsonSchema.string(minLength = Some(NonNegativeInt.unsafe(3)))).derive(JsonFormat)
        assertTrue(
          codec.decode(""""abc"""").isRight,
          codec.decode(""""abcd"""").isRight,
          codec.decode(""""ab"""").isLeft,
          codec.decode(""""a"""").isLeft,
          codec.decode("\"\"").isLeft
        )
      },
      test("string maxLength constraint survives roundtrip") {
        val codec =
          Schema.fromJsonSchema(JsonSchema.string(maxLength = Some(NonNegativeInt.unsafe(5)))).derive(JsonFormat)
        assertTrue(
          codec.decode("\"\"").isRight,
          codec.decode(""""abc"""").isRight,
          codec.decode(""""abcde"""").isRight,
          codec.decode(""""abcdef"""").isLeft,
          codec.decode(""""this is too long"""").isLeft
        )
      },
      test("string minLength and maxLength together survive roundtrip") {
        val codec = Schema
          .fromJsonSchema(
            JsonSchema.string(
              minLength = Some(NonNegativeInt.unsafe(2)),
              maxLength = Some(NonNegativeInt.unsafe(4))
            )
          )
          .derive(JsonFormat)
        assertTrue(
          codec.decode(""""ab"""").isRight,
          codec.decode(""""abc"""").isRight,
          codec.decode(""""abcd"""").isRight,
          codec.decode(""""a"""").isLeft,
          codec.decode(""""abcde"""").isLeft
        )
      },
      test("string pattern constraint survives roundtrip") {
        val codec = Schema
          .fromJsonSchema(JsonSchema.string(pattern = Some(RegexPattern.unsafe("^[a-z]+$"))))
          .derive(JsonFormat)
        assertTrue(
          codec.decode(""""abc"""").isRight,
          codec.decode(""""hello"""").isRight,
          codec.decode(""""ABC"""").isLeft,
          codec.decode(""""Hello"""").isLeft,
          codec.decode(""""hello123"""").isLeft
        )
      },
      test("string email-like pattern constraint survives roundtrip") {
        val codec = Schema
          .fromJsonSchema(JsonSchema.string(pattern = Some(RegexPattern.unsafe("^[^@]+@[^@]+\\.[^@]+$"))))
          .derive(JsonFormat)
        assertTrue(
          codec.decode(""""test@example.com"""").isRight,
          codec.decode(""""user@domain.org"""").isRight,
          codec.decode(""""invalid"""").isLeft,
          codec.decode(""""@missing.com"""").isLeft
        )
      },
      test("integer minimum constraint survives roundtrip") {
        val codec = Schema.fromJsonSchema(JsonSchema.integer(minimum = Some(BigDecimal(0)))).derive(JsonFormat)
        assertTrue(
          codec.decode("0").isRight,
          codec.decode("1").isRight,
          codec.decode("100").isRight,
          codec.decode("-1").isLeft,
          codec.decode("-100").isLeft
        )
      },
      test("integer maximum constraint survives roundtrip") {
        val codec = Schema.fromJsonSchema(JsonSchema.integer(maximum = Some(BigDecimal(100)))).derive(JsonFormat)
        assertTrue(
          codec.decode("-1000").isRight,
          codec.decode("0").isRight,
          codec.decode("100").isRight,
          codec.decode("101").isLeft,
          codec.decode("1000").isLeft
        )
      },
      test("integer minimum and maximum together survive roundtrip") {
        val codec = Schema
          .fromJsonSchema(JsonSchema.integer(minimum = Some(BigDecimal(10)), maximum = Some(BigDecimal(20))))
          .derive(JsonFormat)
        assertTrue(
          codec.decode("10").isRight,
          codec.decode("15").isRight,
          codec.decode("20").isRight,
          codec.decode("9").isLeft,
          codec.decode("21").isLeft
        )
      },
      test("number minimum constraint survives roundtrip") {
        val codec = Schema.fromJsonSchema(JsonSchema.number(minimum = Some(BigDecimal(0.0)))).derive(JsonFormat)
        assertTrue(
          codec.decode("0.0").isRight,
          codec.decode("0.001").isRight,
          codec.decode("100.5").isRight,
          codec.decode("-0.001").isLeft,
          codec.decode("-100.0").isLeft
        )
      },
      test("number maximum constraint survives roundtrip") {
        val codec = Schema.fromJsonSchema(JsonSchema.number(maximum = Some(BigDecimal(100.0)))).derive(JsonFormat)
        assertTrue(
          codec.decode("-1000.0").isRight,
          codec.decode("0.0").isRight,
          codec.decode("100.0").isRight,
          codec.decode("100.001").isLeft,
          codec.decode("1000.0").isLeft
        )
      },
      test("number minimum and maximum together survive roundtrip") {
        val codec = Schema
          .fromJsonSchema(JsonSchema.number(minimum = Some(BigDecimal(-1.0)), maximum = Some(BigDecimal(1.0))))
          .derive(JsonFormat)
        assertTrue(
          codec.decode("-1.0").isRight,
          codec.decode("0.0").isRight,
          codec.decode("0.5").isRight,
          codec.decode("1.0").isRight,
          codec.decode("-1.001").isLeft,
          codec.decode("1.001").isLeft
        )
      },
      test("exclusiveMinimum constraint survives roundtrip") {
        val codec = Schema.fromJsonSchema(JsonSchema.integer(exclusiveMinimum = Some(BigDecimal(0)))).derive(JsonFormat)
        assertTrue(
          codec.decode("1").isRight,
          codec.decode("100").isRight,
          codec.decode("0").isLeft,
          codec.decode("-1").isLeft
        )
      },
      test("exclusiveMaximum constraint survives roundtrip") {
        val codec = Schema
          .fromJsonSchema(JsonSchema.integer(exclusiveMaximum = Some(BigDecimal(100))))
          .derive(JsonFormat)
        assertTrue(
          codec.decode("-100").isRight,
          codec.decode("0").isRight,
          codec.decode("99").isRight,
          codec.decode("100").isLeft,
          codec.decode("101").isLeft
        )
      }
    )
  )
}
