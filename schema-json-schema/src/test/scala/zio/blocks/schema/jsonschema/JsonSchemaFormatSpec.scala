package zio.blocks.schema.jsonschema

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

object JsonSchemaFormatSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("JsonSchemaFormatSpec")(
    suite("primitives")(
      test("Unit") {
        val schema = Schema[Unit].derive(JsonSchemaFormat.deriver)
        assert(schema.schema.toJson)(equalTo("""{"type":"null"}"""))
      },
      test("Boolean") {
        val schema = Schema[Boolean].derive(JsonSchemaFormat.deriver)
        assert(schema.schema.toJson)(equalTo("""{"type":"boolean"}"""))
      },
      test("Byte") {
        val schema = Schema[Byte].derive(JsonSchemaFormat.deriver)
        assert(schema.schema.toJson)(equalTo("""{"type":"integer","minimum":-128,"maximum":127}"""))
      },
      test("Short") {
        val schema = Schema[Short].derive(JsonSchemaFormat.deriver)
        assert(schema.schema.toJson)(equalTo("""{"type":"integer","minimum":-32768,"maximum":32767}"""))
      },
      test("Int") {
        val schema = Schema[Int].derive(JsonSchemaFormat.deriver)
        assert(schema.schema.toJson)(equalTo("""{"type":"integer"}"""))
      },
      test("Long") {
        val schema = Schema[Long].derive(JsonSchemaFormat.deriver)
        assert(schema.schema.toJson)(equalTo("""{"type":"integer"}"""))
      },
      test("Float") {
        val schema = Schema[Float].derive(JsonSchemaFormat.deriver)
        assert(schema.schema.toJson)(equalTo("""{"type":"number"}"""))
      },
      test("Double") {
        val schema = Schema[Double].derive(JsonSchemaFormat.deriver)
        assert(schema.schema.toJson)(equalTo("""{"type":"number"}"""))
      },
      test("Char") {
        val schema = Schema[Char].derive(JsonSchemaFormat.deriver)
        assert(schema.schema.toJson)(equalTo("""{"type":"string","minLength":1,"maxLength":1}"""))
      },
      test("String") {
        val schema = Schema[String].derive(JsonSchemaFormat.deriver)
        assert(schema.schema.toJson)(equalTo("""{"type":"string"}"""))
      },
      test("BigInt") {
        val schema = Schema[BigInt].derive(JsonSchemaFormat.deriver)
        assert(schema.schema.toJson)(equalTo("""{"type":"integer"}"""))
      },
      test("BigDecimal") {
        val schema = Schema[BigDecimal].derive(JsonSchemaFormat.deriver)
        assert(schema.schema.toJson)(equalTo("""{"type":"number"}"""))
      },
      test("UUID") {
        val schema = Schema[java.util.UUID].derive(JsonSchemaFormat.deriver)
        assert(schema.schema.toJson)(equalTo("""{"type":"string","format":"uuid"}"""))
      }
    ),
    suite("collections")(
      test("List[Int]") {
        val schema = Schema[List[Int]].derive(JsonSchemaFormat.deriver)
        assert(schema.schema.toJson)(equalTo("""{"type":"array","items":{"type":"integer"}}"""))
      },
      test("Vector[String]") {
        val schema = Schema[Vector[String]].derive(JsonSchemaFormat.deriver)
        assert(schema.schema.toJson)(equalTo("""{"type":"array","items":{"type":"string"}}"""))
      },
      test("Map[String, Int]") {
        val schema = Schema[Map[String, Int]].derive(JsonSchemaFormat.deriver)
        assert(schema.schema.toJson)(equalTo("""{"type":"object","additionalProperties":{"type":"integer"}}"""))
      }
    ),
    suite("records")(
      test("simple record") {
        val schema = Person.schema.derive(JsonSchemaFormat.deriver)
        val json   = schema.schema.toJson
        assert(json)(containsString(""""type":"object"""")) &&
        assert(json)(containsString(""""properties":""")) &&
        assert(json)(containsString(""""name":{"type":"string"}""")) &&
        assert(json)(containsString(""""age":{"type":"integer"}""")) &&
        assert(json)(containsString(""""required":["name","age"]"""))
      },
      test("nested record") {
        val schema = Company.schema.derive(JsonSchemaFormat.deriver)
        val json   = schema.schema.toJson
        assert(json)(containsString(""""name":{"type":"string"}""")) &&
        assert(json)(containsString(""""ceo":"""))
      },
      test("record with optional field") {
        val schema = PersonWithOptionalEmail.schema.derive(JsonSchemaFormat.deriver)
        val json   = schema.schema.toJson
        // email should not be in required since it's optional
        assert(json)(containsString(""""required":["name","age"]"""))
      }
    ),
    suite("variants")(
      test("simple enum") {
        val schema = Color.schema.derive(JsonSchemaFormat.deriver)
        val json   = schema.schema.toJson
        assert(json)(containsString(""""enum":""")) &&
        assert(json)(containsString(""""Red"""")) &&
        assert(json)(containsString(""""Green"""")) &&
        assert(json)(containsString(""""Blue""""))
      },
      test("sealed trait with data") {
        val schema = Shape.schema.derive(JsonSchemaFormat.deriver)
        val json   = schema.schema.toJson
        assert(json)(containsString(""""oneOf":""")) &&
        assert(json)(containsString(""""Circle":""")) &&
        assert(json)(containsString(""""Rectangle":"""))
      }
    ),
    suite("documentation")(
      test("record with doc") {
        val schema = DocumentedPerson.schema.derive(JsonSchemaFormat.deriver)
        val json   = schema.schema.toJson
        assert(json)(containsString(""""description":"A person with a name and age""""))
      }
    ),
    suite("JsonSchemaValue")(
      test("toJson escapes strings correctly") {
        val value = JsonSchemaValue.Str("hello \"world\"\ntest")
        assert(value.toJson)(equalTo(""""hello \"world\"\ntest""""))
      },
      test("toJson handles nested objects") {
        val obj = JsonSchemaValue.Obj(
          "name"   -> JsonSchemaValue.Str("test"),
          "nested" -> JsonSchemaValue.Obj(
            "value" -> JsonSchemaValue.Num(42)
          )
        )
        assert(obj.toJson)(equalTo("""{"name":"test","nested":{"value":42}}"""))
      },
      test("toPrettyJson formats correctly") {
        val obj = JsonSchemaValue.Obj(
          "type" -> JsonSchemaValue.Str("string")
        )
        val pretty = obj.toPrettyJson
        assert(pretty)(containsString("\n")) &&
        assert(pretty)(containsString("  "))
      }
    ),
    suite("extension methods")(
      test("toJsonSchema extension method works") {
        import zio.blocks.schema.jsonschema._
        val schema = Schema[Int].toJsonSchema
        assert(schema.schema.toJson)(equalTo("""{"type":"integer"}"""))
      }
    )
  )

  // Test data types
  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Company(name: String, ceo: Person)
  object Company {
    implicit val schema: Schema[Company] = Schema.derived
  }

  case class PersonWithOptionalEmail(name: String, age: Int, email: Option[String])
  object PersonWithOptionalEmail {
    implicit val schema: Schema[PersonWithOptionalEmail] = Schema.derived
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

    implicit val schema: Schema[Shape] = Schema.derived
  }

  case class DocumentedPerson(name: String, age: Int)
  object DocumentedPerson {
    implicit val schema: Schema[DocumentedPerson] =
      Schema.derived[DocumentedPerson].doc("A person with a name and age")
  }
}
