package zio.blocks.schema.json

import zio.blocks.schema._
import zio.test._
import java.time._
import java.util.UUID

object SchemaToJsonSchemaSpec extends SchemaBaseSpec {
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

  case class OptionalFields(required: String, optional: Option[Int], withDefault: Boolean = true)
  object OptionalFields {
    implicit val schema: Schema[OptionalFields] = Schema.derived
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
    case class Point()                                  extends Shape

    object Circle {
      implicit val schema: Schema[Circle] = Schema.derived
    }
    object Rectangle {
      implicit val schema: Schema[Rectangle] = Schema.derived
    }
    object Point {
      implicit val schema: Schema[Point] = Schema.derived
    }

    implicit val schema: Schema[Shape] = Schema.derived
  }

  case class WithCollections(
    list: List[Int],
    set: Set[String],
    vector: Vector[Double]
  )
  object WithCollections {
    implicit val schema: Schema[WithCollections] = Schema.derived
  }

  case class WithMaps(
    stringMap: Map[String, Int],
    intKeyMap: Map[Int, String]
  )
  object WithMaps {
    implicit val schema: Schema[WithMaps] = Schema.derived
  }

  case class TemporalTypes(
    instant: Instant,
    localDate: LocalDate,
    localTime: LocalTime,
    localDateTime: LocalDateTime,
    offsetDateTime: OffsetDateTime,
    zonedDateTime: ZonedDateTime,
    duration: Duration,
    period: Period
  )
  object TemporalTypes {
    implicit val schema: Schema[TemporalTypes] = Schema.derived
  }

  case class WithUUID(id: UUID, name: String)
  object WithUUID {
    implicit val schema: Schema[WithUUID] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("SchemaToJsonSchemaSpec")(
    suite("Primitive types produce correct JSON Schema types")(
      test("String schema produces string JSON Schema") {
        val schema     = Schema[String].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").one.map(_.asInstanceOf[Json.String].value)
        assertTrue(typeString == Right("string"))
      },
      test("Int schema produces integer JSON Schema") {
        val schema     = Schema[Int].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").one.map(_.asInstanceOf[Json.String].value)
        assertTrue(typeString == Right("integer"))
      },
      test("Long schema produces integer JSON Schema") {
        val schema     = Schema[Long].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").one.map(_.asInstanceOf[Json.String].value)
        assertTrue(typeString == Right("integer"))
      },
      test("Boolean schema produces boolean JSON Schema") {
        val schema     = Schema[Boolean].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").one.map(_.asInstanceOf[Json.String].value)
        assertTrue(typeString == Right("boolean"))
      },
      test("Double schema produces number JSON Schema") {
        val schema     = Schema[Double].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").one.map(_.asInstanceOf[Json.String].value)
        assertTrue(typeString == Right("number"))
      },
      test("Float schema produces number JSON Schema") {
        val schema     = Schema[Float].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").one.map(_.asInstanceOf[Json.String].value)
        assertTrue(typeString == Right("number"))
      },
      test("BigInt schema produces integer JSON Schema") {
        val schema     = Schema[BigInt].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").one.map(_.asInstanceOf[Json.String].value)
        assertTrue(typeString == Right("integer"))
      },
      test("BigDecimal schema produces number JSON Schema") {
        val schema     = Schema[BigDecimal].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").one.map(_.asInstanceOf[Json.String].value)
        assertTrue(typeString == Right("number"))
      },
      test("Unit schema produces empty object JSON Schema") {
        val schema = Schema[Unit].toJsonSchema
        val json   = schema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("object")),
          json.get("additionalProperties").one == Right(Json.Boolean(false))
        )
      }
    ),
    suite("Primitive type constraints are preserved")(
      test("Byte schema includes min/max constraints") {
        val schema = Schema[Byte].toJsonSchema
        val json   = schema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("integer")),
          json.get("minimum").one == Right(Json.Number("-128")),
          json.get("maximum").one == Right(Json.Number("127"))
        )
      },
      test("Short schema includes min/max constraints") {
        val schema = Schema[Short].toJsonSchema
        val json   = schema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("integer")),
          json.get("minimum").one == Right(Json.Number("-32768")),
          json.get("maximum").one == Right(Json.Number("32767"))
        )
      },
      test("Char schema includes length constraints") {
        val schema = Schema[Char].toJsonSchema
        val json   = schema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("string")),
          json.get("minLength").one == Right(Json.Number("1")),
          json.get("maxLength").one == Right(Json.Number("1"))
        )
      }
    ),
    suite("Temporal types produce string with format annotations")(
      test("Instant produces string with date-time format") {
        val jsonSchema = Schema[Instant].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("string")),
          json.get("format").one == Right(Json.String("date-time"))
        )
      },
      test("LocalDate produces string with date format") {
        val jsonSchema = Schema[LocalDate].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("string")),
          json.get("format").one == Right(Json.String("date"))
        )
      },
      test("LocalTime produces string with time format") {
        val jsonSchema = Schema[LocalTime].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("string")),
          json.get("format").one == Right(Json.String("time"))
        )
      },
      test("LocalDateTime produces string with date-time format") {
        val jsonSchema = Schema[LocalDateTime].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("string")),
          json.get("format").one == Right(Json.String("date-time"))
        )
      },
      test("OffsetDateTime produces string with date-time format") {
        val jsonSchema = Schema[OffsetDateTime].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("string")),
          json.get("format").one == Right(Json.String("date-time"))
        )
      },
      test("OffsetTime produces string with time format") {
        val jsonSchema = Schema[OffsetTime].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("string")),
          json.get("format").one == Right(Json.String("time"))
        )
      },
      test("ZonedDateTime produces string with date-time format") {
        val jsonSchema = Schema[ZonedDateTime].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("string")),
          json.get("format").one == Right(Json.String("date-time"))
        )
      },
      test("Duration produces string with duration format") {
        val jsonSchema = Schema[Duration].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("string")),
          json.get("format").one == Right(Json.String("duration"))
        )
      },
      test("Period produces string with duration format") {
        val jsonSchema = Schema[Period].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("string")),
          json.get("format").one == Right(Json.String("duration"))
        )
      },
      test("UUID produces string with uuid format") {
        val jsonSchema = Schema[UUID].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("string")),
          json.get("format").one == Right(Json.String("uuid"))
        )
      }
    ),
    suite("Record types (case classes)")(
      test("case class produces object JSON Schema with properties") {
        val schema     = Schema[Person].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").one.map(_.asInstanceOf[Json.String].value)
        val hasName    = json.get("properties").get("name").isSuccess
        val hasAge     = json.get("properties").get("age").isSuccess
        assertTrue(typeString == Right("object"), hasName, hasAge)
      },
      test("required fields are listed in required array") {
        val jsonSchema = Schema[Person].toJsonSchema
        val json       = jsonSchema.toJson
        val required   = json.get("required").one.map(_.elements.collect { case s: Json.String => s.value }.toSet)
        assertTrue(required == Right(Set("name", "age")))
      },
      test("nested case classes produce nested object schemas") {
        val jsonSchema = Schema[Company].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("object")),
          json.get("properties").get("address").get("type").one == Right(Json.String("object")),
          json.get("properties").get("address").get("properties").get("street").get("type").one == Right(
            Json.String("string")
          ),
          json.get("properties").get("employees").get("type").one == Right(Json.String("array"))
        )
      },
      test("optional fields are not in required array") {
        val jsonSchema = Schema[Address].toJsonSchema
        val json       = jsonSchema.toJson
        val required   = json.get("required").one.map(_.elements.collect { case s: Json.String => s.value }.toSet)
        assertTrue(
          required == Right(Set("street", "city"))
        )
      },
      test("fields with defaults are not in required array") {
        val jsonSchema = Schema[OptionalFields].toJsonSchema
        val json       = jsonSchema.toJson
        val required   = json.get("required").one.map(_.elements.collect { case s: Json.String => s.value }.toSet)
        assertTrue(required == Right(Set("required")))
      },
      test("generated schema validates matching JSON") {
        val schema   = Schema[Person].toJsonSchema
        val validObj = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))
        assertTrue(schema.conforms(validObj))
      },
      test("generated schema rejects non-matching JSON") {
        val schema     = Schema[Person].toJsonSchema
        val invalidObj = Json.Object("name" -> Json.Number(123), "age" -> Json.String("not-a-number"))
        assertTrue(!schema.conforms(invalidObj))
      },
      test("record with temporal types produces correct schema") {
        val jsonSchema = Schema[TemporalTypes].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("properties").get("instant").get("format").one == Right(Json.String("date-time")),
          json.get("properties").get("localDate").get("format").one == Right(Json.String("date")),
          json.get("properties").get("localTime").get("format").one == Right(Json.String("time")),
          json.get("properties").get("duration").get("format").one == Right(Json.String("duration"))
        )
      },
      test("record with UUID produces correct schema") {
        val jsonSchema = Schema[WithUUID].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("properties").get("id").get("format").one == Right(Json.String("uuid")),
          json.get("properties").get("name").get("type").one == Right(Json.String("string"))
        )
      }
    ),
    suite("Collection types")(
      test("List[Int] produces array JSON Schema") {
        val schema     = Schema[List[Int]].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").one.map(_.asInstanceOf[Json.String].value)
        assertTrue(typeString == Right("array"))
      },
      test("List[Int] produces array schema with integer items") {
        val jsonSchema = Schema[List[Int]].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("array")),
          json.get("items").get("type").one == Right(Json.String("integer"))
        )
      },
      test("Set[String] produces array schema with string items") {
        val jsonSchema = Schema[Set[String]].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("array")),
          json.get("items").get("type").one == Right(Json.String("string"))
        )
      },
      test("Vector[Double] produces array schema with number items") {
        val jsonSchema = Schema[Vector[Double]].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("array")),
          json.get("items").get("type").one == Right(Json.String("number"))
        )
      },
      test("List[Person] produces array schema with object items") {
        val jsonSchema = Schema[List[Person]].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("array")),
          json.get("items").get("type").one == Right(Json.String("object")),
          json.get("items").get("properties").get("name").get("type").one == Right(Json.String("string"))
        )
      },
      test("nested collections produce nested array schemas") {
        val jsonSchema = Schema[List[List[Int]]].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("array")),
          json.get("items").get("type").one == Right(Json.String("array")),
          json.get("items").get("items").get("type").one == Right(Json.String("integer"))
        )
      }
    ),
    suite("Map types")(
      test("Map[String, Int] produces object JSON Schema") {
        val schema     = Schema[Map[String, Int]].toJsonSchema
        val json       = schema.toJson
        val typeString = json.get("type").one.map(_.asInstanceOf[Json.String].value)
        assertTrue(typeString == Right("object"))
      },
      test("Map[String, Int] produces object schema with additionalProperties") {
        val jsonSchema = Schema[Map[String, Int]].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("object")),
          json.get("additionalProperties").get("type").one == Right(Json.String("integer"))
        )
      },
      test("Map[String, Person] produces object schema with object additionalProperties") {
        val jsonSchema = Schema[Map[String, Person]].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").one == Right(Json.String("object")),
          json.get("additionalProperties").get("type").one == Right(Json.String("object")),
          json.get("additionalProperties").get("properties").get("name").get("type").one == Right(Json.String("string"))
        )
      }
    ),
    suite("Option types")(
      test("Option[String] produces nullable string JSON Schema") {
        val schema = Schema[Option[String]].toJsonSchema
        assertTrue(
          schema.conforms(Json.String("hello")),
          schema.conforms(Json.Null)
        )
      },
      test("Option[Int] produces nullable schema") {
        val jsonSchema = Schema[Option[Int]].toJsonSchema
        assertTrue(
          jsonSchema.conforms(Json.Number(42)),
          jsonSchema.conforms(Json.Null)
        )
      },
      test("Option[Person] produces nullable object schema") {
        val jsonSchema = Schema[Option[Person]].toJsonSchema
        assertTrue(
          jsonSchema.conforms(Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))),
          jsonSchema.conforms(Json.Null)
        )
      }
    ),
    suite("Enum/Variant types")(
      test("sealed trait enum produces enum JSON Schema") {
        val schema = Schema[Color].toJsonSchema
        assertTrue(
          schema.conforms(Json.String("Red")),
          schema.conforms(Json.String("Green")),
          schema.conforms(Json.String("Blue"))
        )
      },
      test("Color enum JSON contains enum keyword") {
        val jsonSchema = Schema[Color].toJsonSchema
        val json       = jsonSchema.toJson
        val enumValues = json.get("enum").one.map(_.elements.collect { case s: Json.String => s.value }.toSet)
        assertTrue(enumValues == Right(Set("Red", "Green", "Blue")))
      },
      test("Shape variant produces oneOf schema") {
        val jsonSchema = Schema[Shape].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(json.get("oneOf").isSuccess)
      },
      test("Shape variant validates Circle") {
        val jsonSchema = Schema[Shape].toJsonSchema
        val circleJson = Json.Object("Circle" -> Json.Object("radius" -> Json.Number(5.0)))
        assertTrue(jsonSchema.conforms(circleJson))
      },
      test("Shape variant validates Rectangle") {
        val jsonSchema = Schema[Shape].toJsonSchema
        val rectJson   =
          Json.Object("Rectangle" -> Json.Object("width" -> Json.Number(10.0), "height" -> Json.Number(20.0)))
        assertTrue(jsonSchema.conforms(rectJson))
      },
      test("Shape variant rejects invalid shape") {
        val jsonSchema  = Schema[Shape].toJsonSchema
        val invalidJson = Json.Object("Triangle" -> Json.Object("base" -> Json.Number(5.0)))
        assertTrue(!jsonSchema.conforms(invalidJson))
      }
    ),

    suite("Schema validation behavior")(
      test("schema validates conforming primitives") {
        assertTrue(
          Schema[String].toJsonSchema.conforms(Json.String("hello")),
          Schema[Int].toJsonSchema.conforms(Json.Number(42)),
          Schema[Boolean].toJsonSchema.conforms(Json.True),
          Schema[Double].toJsonSchema.conforms(Json.Number(3.14))
        )
      },
      test("schema rejects non-conforming primitives") {
        assertTrue(
          !Schema[String].toJsonSchema.conforms(Json.Number(42)),
          !Schema[Int].toJsonSchema.conforms(Json.String("hello")),
          !Schema[Boolean].toJsonSchema.conforms(Json.Number(1))
        )
      },
      test("array schema validates arrays") {
        val jsonSchema = Schema[List[Int]].toJsonSchema
        assertTrue(
          jsonSchema.conforms(Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))),
          jsonSchema.conforms(Json.Array()),
          !jsonSchema.conforms(Json.Object()),
          !jsonSchema.conforms(Json.String("not an array"))
        )
      },
      test("object schema validates objects") {
        val jsonSchema = Schema[Person].toJsonSchema
        assertTrue(
          jsonSchema.conforms(Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))),
          !jsonSchema.conforms(Json.Array()),
          !jsonSchema.conforms(Json.String("not an object"))
        )
      }
    ),
    suite("Error messages")(
      test("type mismatch error includes path information") {
        val schema  = Schema[Person].toJsonSchema
        val invalid = Json.Object("name" -> Json.Number(123), "age" -> Json.Number(30))
        val error   = schema.check(invalid)
        assertTrue(
          error.isDefined,
          error.exists(_.message.contains("at:"))
        )
      },
      test("error message describes the type mismatch") {
        val schema  = Schema[Person].toJsonSchema
        val invalid = Json.Object("name" -> Json.Number(123), "age" -> Json.Number(30))
        val error   = schema.check(invalid)
        assertTrue(
          error.exists(_.message.contains("Expected type"))
        )
      }
    ),
    suite("Complex nested structures")(
      test("deeply nested structure produces correct schema") {
        val jsonSchema = Schema[Company].toJsonSchema
        val validJson  = Json.Object(
          "name"    -> Json.String("Acme Corp"),
          "address" -> Json.Object(
            "street"  -> Json.String("123 Main St"),
            "city"    -> Json.String("Springfield"),
            "zipCode" -> Json.String("12345")
          ),
          "employees" -> Json.Array(
            Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30)),
            Json.Object("name" -> Json.String("Bob"), "age"   -> Json.Number(25))
          )
        )
        assertTrue(jsonSchema.conforms(validJson))
      }
    )
  )
}
