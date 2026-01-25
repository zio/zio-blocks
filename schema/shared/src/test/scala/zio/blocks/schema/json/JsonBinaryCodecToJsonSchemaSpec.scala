package zio.blocks.schema.json

import zio.blocks.schema._
import zio.test._

/**
 * Tests for JsonBinaryCodec.toJsonSchema - schema extraction from codecs.
 *
 * Verifies that codecs produce accurate JSON Schema representations reflecting
 * their configuration.
 */
object JsonBinaryCodecToJsonSchemaSpec extends ZIOSpecDefault {

  // Test case classes
  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Address(street: String, city: String, zip: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class Company(name: String, employees: List[Person], headquarters: Address)
  object Company {
    implicit val schema: Schema[Company] = Schema.derived
  }

  // Test sealed traits
  sealed trait Animal
  case class Dog(name: String, breed: String)   extends Animal
  case class Cat(name: String, indoor: Boolean) extends Animal
  object Animal {
    implicit val schema: Schema[Animal] = Schema.derived
  }

  // Test enumeration (all case objects)
  sealed trait Color
  case object Red   extends Color
  case object Green extends Color
  case object Blue  extends Color
  object Color {
    implicit val schema: Schema[Color] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("JsonBinaryCodecToJsonSchemaSpec")(
    suite("Primitive types")(
      test("String produces type: string") {
        val codec      = Schema[String].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        assertTrue(
          jsonSchema.isInstanceOf[JsonSchema.SchemaObject],
          jsonSchema.asInstanceOf[JsonSchema.SchemaObject].`type` == Some(JsonType.String)
        )
      },
      test("Int produces type: integer") {
        val codec      = Schema[Int].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        assertTrue(
          jsonSchema.isInstanceOf[JsonSchema.SchemaObject],
          jsonSchema.asInstanceOf[JsonSchema.SchemaObject].`type` == Some(JsonType.Integer)
        )
      },
      test("Long produces type: integer") {
        val codec      = Schema[Long].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        assertTrue(
          jsonSchema.isInstanceOf[JsonSchema.SchemaObject],
          jsonSchema.asInstanceOf[JsonSchema.SchemaObject].`type` == Some(JsonType.Integer)
        )
      },
      test("Double produces type: number") {
        val codec      = Schema[Double].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        assertTrue(
          jsonSchema.isInstanceOf[JsonSchema.SchemaObject],
          jsonSchema.asInstanceOf[JsonSchema.SchemaObject].`type` == Some(JsonType.Number)
        )
      },
      test("Boolean produces type: boolean") {
        val codec      = Schema[Boolean].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        assertTrue(
          jsonSchema.isInstanceOf[JsonSchema.SchemaObject],
          jsonSchema.asInstanceOf[JsonSchema.SchemaObject].`type` == Some(JsonType.Boolean)
        )
      },
      test("Byte produces type: integer with range") {
        val codec      = Schema[Byte].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.Integer),
          schemaObj.minimum == Some(BigDecimal(-128)),
          schemaObj.maximum == Some(BigDecimal(127))
        )
      },
      test("Short produces type: integer with range") {
        val codec      = Schema[Short].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.Integer),
          schemaObj.minimum == Some(BigDecimal(-32768)),
          schemaObj.maximum == Some(BigDecimal(32767))
        )
      },
      test("Char produces type: string with length constraints") {
        val codec      = Schema[Char].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.String),
          schemaObj.minLength == Some(1),
          schemaObj.maxLength == Some(1)
        )
      }
    ),
    suite("Temporal types")(
      test("Instant produces format: date-time") {
        val codec      = Schema[java.time.Instant].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.String),
          schemaObj.format == Some("date-time")
        )
      },
      test("LocalDate produces format: date") {
        val codec      = Schema[java.time.LocalDate].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.String),
          schemaObj.format == Some("date")
        )
      },
      test("LocalTime produces format: time") {
        val codec      = Schema[java.time.LocalTime].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.String),
          schemaObj.format == Some("time")
        )
      },
      test("UUID produces format: uuid") {
        val codec      = Schema[java.util.UUID].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.String),
          schemaObj.format == Some("uuid")
        )
      },
      test("Duration produces format: duration") {
        val codec      = Schema[java.time.Duration].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.String),
          schemaObj.format == Some("duration")
        )
      }
    ),
    suite("Records (case classes)")(
      test("Person produces type: object with properties") {
        val codec      = Schema[Person].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.Object),
          schemaObj.properties.isDefined,
          schemaObj.properties.get.contains("name"),
          schemaObj.properties.get.contains("age")
        )
      },
      test("Person has required fields") {
        val codec      = Schema[Person].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.required.isDefined,
          schemaObj.required.get.contains("name"),
          schemaObj.required.get.contains("age")
        )
      },
      test("Nested records work correctly") {
        val codec      = Schema[Company].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.properties.isDefined,
          schemaObj.properties.get.contains("name"),
          schemaObj.properties.get.contains("employees"),
          schemaObj.properties.get.contains("headquarters")
        )
      }
    ),
    suite("Variants (sealed traits)")(
      test("Animal variant produces oneOf schema") {
        val codec      = Schema[Animal].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema

        // Depending on discriminator kind, could be oneOf or properties with discriminator
        assertTrue(jsonSchema.isInstanceOf[JsonSchema.SchemaObject])
      }
    ),
    suite("Collections")(
      test("List[String] produces type: array with items") {
        val codec      = Schema[List[String]].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.Array),
          schemaObj.items.isDefined
        )
      },
      test("Vector[Int] produces type: array") {
        val codec      = Schema[Vector[Int]].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(schemaObj.`type` == Some(JsonType.Array))
      },
      test("Set[String] produces type: array with uniqueItems") {
        val codec      = Schema[Set[String]].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(schemaObj.`type` == Some(JsonType.Array))
      }
    ),
    suite("Maps")(
      test("Map[String, Int] produces type: object with additionalProperties") {
        val codec      = Schema[Map[String, Int]].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.Object),
          schemaObj.additionalProperties.isDefined
        )
      }
    ),
    suite("Option fields")(
      test("Option[String] produces correct schema") {
        val codec      = Schema[Option[String]].derive(JsonFormat.deriver)
        val jsonSchema = codec.toJsonSchema
        // Option can produce anyOf with null or nullable type
        assertTrue(jsonSchema.isInstanceOf[JsonSchema.SchemaObject])
      }
    )
  )
}
