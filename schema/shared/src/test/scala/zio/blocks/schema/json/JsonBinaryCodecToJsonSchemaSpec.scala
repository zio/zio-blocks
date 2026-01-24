package zio.blocks.schema.json

import zio.blocks.schema._
import zio.test._
import java.time._
import java.util.UUID

object JsonBinaryCodecToJsonSchemaSpec extends SchemaBaseSpec {

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
    vector: Vector[Double],
    array: Array[Boolean]
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

  def spec: Spec[TestEnvironment, Any] = suite("JsonBinaryCodecToJsonSchemaSpec")(
    suite("Primitive types")(
      test("String produces string JSON Schema") {
        val jsonSchema = Schema[String].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(json.get("type").string == Right("string"))
      },
      test("Boolean produces boolean JSON Schema") {
        val jsonSchema = Schema[Boolean].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(json.get("type").string == Right("boolean"))
      },
      test("Int produces integer JSON Schema") {
        val jsonSchema = Schema[Int].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(json.get("type").string == Right("integer"))
      },
      test("Long produces integer JSON Schema") {
        val jsonSchema = Schema[Long].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(json.get("type").string == Right("integer"))
      },
      test("Double produces number JSON Schema") {
        val jsonSchema = Schema[Double].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(json.get("type").string == Right("number"))
      },
      test("Float produces number JSON Schema") {
        val jsonSchema = Schema[Float].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(json.get("type").string == Right("number"))
      },
      test("Byte produces integer JSON Schema with min/max constraints") {
        val jsonSchema = Schema[Byte].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("integer"),
          json.get("minimum").number == Right(BigDecimal(-128)),
          json.get("maximum").number == Right(BigDecimal(127))
        )
      },
      test("Short produces integer JSON Schema with min/max constraints") {
        val jsonSchema = Schema[Short].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("integer"),
          json.get("minimum").number == Right(BigDecimal(-32768)),
          json.get("maximum").number == Right(BigDecimal(32767))
        )
      },
      test("Char produces string JSON Schema with minLength/maxLength = 1") {
        val jsonSchema = Schema[Char].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("string"),
          json.get("minLength").number == Right(BigDecimal(1)),
          json.get("maxLength").number == Right(BigDecimal(1))
        )
      },
      test("BigInt produces integer JSON Schema") {
        val jsonSchema = Schema[BigInt].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(json.get("type").string == Right("integer"))
      },
      test("BigDecimal produces number JSON Schema") {
        val jsonSchema = Schema[BigDecimal].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(json.get("type").string == Right("number"))
      },
      test("Unit produces empty object JSON Schema") {
        val jsonSchema = Schema[Unit].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("object"),
          json.get("additionalProperties").boolean == Right(false)
        )
      }
    ),
    suite("Temporal types with format annotations")(
      test("Instant produces string with date-time format") {
        val jsonSchema = Schema[Instant].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("string"),
          json.get("format").string == Right("date-time")
        )
      },
      test("LocalDate produces string with date format") {
        val jsonSchema = Schema[LocalDate].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("string"),
          json.get("format").string == Right("date")
        )
      },
      test("LocalTime produces string with time format") {
        val jsonSchema = Schema[LocalTime].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("string"),
          json.get("format").string == Right("time")
        )
      },
      test("LocalDateTime produces string with date-time format") {
        val jsonSchema = Schema[LocalDateTime].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("string"),
          json.get("format").string == Right("date-time")
        )
      },
      test("OffsetDateTime produces string with date-time format") {
        val jsonSchema = Schema[OffsetDateTime].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("string"),
          json.get("format").string == Right("date-time")
        )
      },
      test("OffsetTime produces string with time format") {
        val jsonSchema = Schema[OffsetTime].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("string"),
          json.get("format").string == Right("time")
        )
      },
      test("ZonedDateTime produces string with date-time format") {
        val jsonSchema = Schema[ZonedDateTime].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("string"),
          json.get("format").string == Right("date-time")
        )
      },
      test("Duration produces string with duration format") {
        val jsonSchema = Schema[Duration].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("string"),
          json.get("format").string == Right("duration")
        )
      },
      test("Period produces string with duration format") {
        val jsonSchema = Schema[Period].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("string"),
          json.get("format").string == Right("duration")
        )
      },
      test("UUID produces string with uuid format") {
        val jsonSchema = Schema[UUID].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("string"),
          json.get("format").string == Right("uuid")
        )
      }
    ),
    suite("Record types (case classes)")(
      test("simple case class produces object schema with properties") {
        val jsonSchema = Schema[Person].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("object"),
          json.get("properties").get("name").get("type").string == Right("string"),
          json.get("properties").get("age").get("type").string == Right("integer")
        )
      },
      test("required fields are listed in required array") {
        val jsonSchema = Schema[Person].toJsonSchema
        val json       = jsonSchema.toJson
        val required   = json.get("required").first.map(_.elements.flatMap(_.stringValue).toSet)
        assertTrue(required == Right(Set("name", "age")))
      },
      test("nested case classes produce nested object schemas") {
        val jsonSchema = Schema[Company].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("object"),
          json.get("properties").get("address").get("type").string == Right("object"),
          json.get("properties").get("address").get("properties").get("street").get("type").string == Right("string"),
          json.get("properties").get("employees").get("type").string == Right("array")
        )
      },
      test("optional fields are not in required array") {
        val jsonSchema = Schema[Address].toJsonSchema
        val json       = jsonSchema.toJson
        val required   = json.get("required").first.map(_.elements.flatMap(_.stringValue).toSet)
        assertTrue(
          required == Right(Set("street", "city"))
        )
      },
      test("fields with defaults are not in required array") {
        val jsonSchema = Schema[OptionalFields].toJsonSchema
        val json       = jsonSchema.toJson
        val required   = json.get("required").first.map(_.elements.flatMap(_.stringValue).toSet)
        assertTrue(required == Right(Set("required")))
      },
      test("generated schema validates matching JSON") {
        val jsonSchema = Schema[Person].toJsonSchema
        val validJson  = Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(30))
        assertTrue(jsonSchema.conforms(validJson))
      },
      test("generated schema rejects invalid JSON") {
        val jsonSchema  = Schema[Person].toJsonSchema
        val invalidJson = Json.obj("name" -> Json.number(123), "age" -> Json.str("not-a-number"))
        assertTrue(!jsonSchema.conforms(invalidJson))
      }
    ),
    suite("Collection types")(
      test("List[Int] produces array schema with integer items") {
        val jsonSchema = Schema[List[Int]].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("array"),
          json.get("items").get("type").string == Right("integer")
        )
      },
      test("Set[String] produces array schema with string items") {
        val jsonSchema = Schema[Set[String]].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("array"),
          json.get("items").get("type").string == Right("string")
        )
      },
      test("Vector[Double] produces array schema with number items") {
        val jsonSchema = Schema[Vector[Double]].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("array"),
          json.get("items").get("type").string == Right("number")
        )
      },
      test("List[Person] produces array schema with object items") {
        val jsonSchema = Schema[List[Person]].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("array"),
          json.get("items").get("type").string == Right("object"),
          json.get("items").get("properties").get("name").get("type").string == Right("string")
        )
      },
      test("nested collections produce nested array schemas") {
        val jsonSchema = Schema[List[List[Int]]].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("array"),
          json.get("items").get("type").string == Right("array"),
          json.get("items").get("items").get("type").string == Right("integer")
        )
      }
    ),
    suite("Map types")(
      test("Map[String, Int] produces object schema with additionalProperties") {
        val jsonSchema = Schema[Map[String, Int]].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("object"),
          json.get("additionalProperties").get("type").string == Right("integer")
        )
      },
      test("Map[String, Person] produces object schema with object additionalProperties") {
        val jsonSchema = Schema[Map[String, Person]].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("type").string == Right("object"),
          json.get("additionalProperties").get("type").string == Right("object"),
          json.get("additionalProperties").get("properties").get("name").get("type").string == Right("string")
        )
      }
    ),
    suite("Option types")(
      test("Option[String] produces nullable schema") {
        val jsonSchema = Schema[Option[String]].toJsonSchema
        assertTrue(
          jsonSchema.conforms(Json.str("hello")),
          jsonSchema.conforms(Json.Null)
        )
      },
      test("Option[Int] produces nullable schema") {
        val jsonSchema = Schema[Option[Int]].toJsonSchema
        assertTrue(
          jsonSchema.conforms(Json.number(42)),
          jsonSchema.conforms(Json.Null)
        )
      },
      test("Option[Person] produces nullable object schema") {
        val jsonSchema = Schema[Option[Person]].toJsonSchema
        assertTrue(
          jsonSchema.conforms(Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(30))),
          jsonSchema.conforms(Json.Null)
        )
      }
    ),
    suite("Enum types (sealed trait with case objects)")(
      test("Color enum produces enum schema with string values") {
        val jsonSchema = Schema[Color].toJsonSchema
        assertTrue(
          jsonSchema.conforms(Json.str("Red")),
          jsonSchema.conforms(Json.str("Green")),
          jsonSchema.conforms(Json.str("Blue")),
          !jsonSchema.conforms(Json.str("Yellow"))
        )
      },
      test("Color enum JSON contains enum keyword") {
        val jsonSchema = Schema[Color].toJsonSchema
        val json       = jsonSchema.toJson
        val enumValues = json.get("enum").first.map(_.elements.flatMap(_.stringValue).toSet)
        assertTrue(enumValues == Right(Set("Red", "Green", "Blue")))
      }
    ),
    suite("Variant types (sealed trait with case classes)")(
      test("Shape variant produces oneOf schema") {
        val jsonSchema = Schema[Shape].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(json.get("oneOf").isSuccess)
      },
      test("Shape variant validates Circle") {
        val jsonSchema = Schema[Shape].toJsonSchema
        val circleJson = Json.obj("Circle" -> Json.obj("radius" -> Json.number(5.0)))
        assertTrue(jsonSchema.conforms(circleJson))
      },
      test("Shape variant validates Rectangle") {
        val jsonSchema = Schema[Shape].toJsonSchema
        val rectJson   = Json.obj("Rectangle" -> Json.obj("width" -> Json.number(10.0), "height" -> Json.number(20.0)))
        assertTrue(jsonSchema.conforms(rectJson))
      },
      test("Shape variant rejects invalid shape") {
        val jsonSchema  = Schema[Shape].toJsonSchema
        val invalidJson = Json.obj("Triangle" -> Json.obj("base" -> Json.number(5.0)))
        assertTrue(!jsonSchema.conforms(invalidJson))
      }
    ),
    suite("Complex nested structures")(
      test("deeply nested structure produces correct schema") {
        val jsonSchema = Schema[Company].toJsonSchema
        val validJson  = Json.obj(
          "name"    -> Json.str("Acme Corp"),
          "address" -> Json.obj(
            "street"  -> Json.str("123 Main St"),
            "city"    -> Json.str("Springfield"),
            "zipCode" -> Json.str("12345")
          ),
          "employees" -> Json.arr(
            Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(30)),
            Json.obj("name" -> Json.str("Bob"), "age"   -> Json.number(25))
          )
        )
        assertTrue(jsonSchema.conforms(validJson))
      },
      test("record with temporal types produces correct schema") {
        val jsonSchema = Schema[TemporalTypes].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("properties").get("instant").get("format").string == Right("date-time"),
          json.get("properties").get("localDate").get("format").string == Right("date"),
          json.get("properties").get("localTime").get("format").string == Right("time"),
          json.get("properties").get("duration").get("format").string == Right("duration")
        )
      },
      test("record with UUID produces correct schema") {
        val jsonSchema = Schema[WithUUID].toJsonSchema
        val json       = jsonSchema.toJson
        assertTrue(
          json.get("properties").get("id").get("format").string == Right("uuid"),
          json.get("properties").get("name").get("type").string == Right("string")
        )
      }
    ),
    suite("Schema validation behavior")(
      test("schema validates conforming primitives") {
        assertTrue(
          Schema[String].toJsonSchema.conforms(Json.str("hello")),
          Schema[Int].toJsonSchema.conforms(Json.number(42)),
          Schema[Boolean].toJsonSchema.conforms(Json.True),
          Schema[Double].toJsonSchema.conforms(Json.number(3.14))
        )
      },
      test("schema rejects non-conforming primitives") {
        assertTrue(
          !Schema[String].toJsonSchema.conforms(Json.number(42)),
          !Schema[Int].toJsonSchema.conforms(Json.str("hello")),
          !Schema[Boolean].toJsonSchema.conforms(Json.number(1))
        )
      },
      test("array schema validates arrays") {
        val jsonSchema = Schema[List[Int]].toJsonSchema
        assertTrue(
          jsonSchema.conforms(Json.arr(Json.number(1), Json.number(2), Json.number(3))),
          jsonSchema.conforms(Json.arr()),
          !jsonSchema.conforms(Json.obj()),
          !jsonSchema.conforms(Json.str("not an array"))
        )
      },
      test("object schema validates objects") {
        val jsonSchema = Schema[Person].toJsonSchema
        assertTrue(
          jsonSchema.conforms(Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(30))),
          !jsonSchema.conforms(Json.arr()),
          !jsonSchema.conforms(Json.str("not an object"))
        )
      }
    )
  )
}
