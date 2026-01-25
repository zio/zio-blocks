package zio.blocks.schema.json

import zio.blocks.schema._
import zio.blocks.chunk.Chunk
import zio.test._
import java.nio.ByteBuffer

object JsonSchemaSpec extends ZIOSpecDefault {
  def spec = suite("JsonSchemaSpec")(
    test("primitive schemas") {
      val intSchema = getCodec[Int].toJsonSchema.toJson
      val stringSchema = getCodec[String].toJsonSchema.toJson
      val boolSchema = getCodec[Boolean].toJsonSchema.toJson
      
      assertTrue(intSchema == Json.Object(Chunk(
        "type" -> Json.str("integer"),
        "minimum" -> Json.number(Int.MinValue),
        "maximum" -> Json.number(Int.MaxValue)
      ))) &&
      assertTrue(stringSchema == Json.Object(Chunk("type" -> Json.str("string")))) &&
      assertTrue(boolSchema == Json.Object(Chunk("type" -> Json.str("boolean"))))
    },
    test("record schema") {
      case class Person(name: String, age: Int)
      // silence unused warning
      val _ = Person("test", 30).age
      val schema: Schema[Person] = Schema.derived
      
      val personSchema = getCodec(schema).toJsonSchema.toJson
      
      val fields = personSchema.asObject.toMap
      assertTrue(fields("type") == Json.str("object")) &&
      assertTrue(fields("properties").asObject.toMap.contains("name")) &&
      assertTrue(fields("properties").asObject.toMap.contains("age")) && {
        val req = fields("required")
        assertTrue(req.isArray) &&
        assertTrue(req.elements.toSet == Set(Json.str("name"), Json.str("age")))
      }
    },
    test("sequence schema") {
      val listSchema = getCodec[List[Int]].toJsonSchema.toJson
      val expected = Json.Object(Chunk(
        "type" -> Json.str("array"),
        "items" -> Json.Object(Chunk(
          "type" -> Json.str("integer"),
          "minimum" -> Json.number(Int.MinValue),
          "maximum" -> Json.number(Int.MaxValue)
        ))
      ))
      assertTrue(listSchema == expected)
    },
    test("option schema") {
      val optSchema = getCodec[Option[Int]].toJsonSchema.toJson
      val anyOf = optSchema.asObject.toMap("anyOf")
      assertTrue(anyOf.isArray)
      val anyOfElements = anyOf.asInstanceOf[Json.Array].elements
      assertTrue(anyOfElements.contains(Json.Object(Chunk(
        "type" -> Json.str("integer"),
        "minimum" -> Json.number(Int.MinValue),
        "maximum" -> Json.number(Int.MaxValue)
      )))) &&
      assertTrue(anyOfElements.contains(Json.Object(Chunk("type" -> Json.str("null")))))
    },
    test("enum schema (as strings)") {
       sealed trait Color
       case object Red extends Color
       case object Green extends Color
       val schema: Schema[Color] = Schema.derived
       
       val colorSchema = getCodec(schema).toJsonSchema.toJson
       assertTrue(colorSchema == Json.Object(Chunk("enum" -> Json.arr(Json.str("Red"), Json.str("Green")))))
    },
    test("JsonSchema parsing and serialization") {
      val json = """{
        "type": "object",
        "properties": {
          "name": { "type": "string", "minLength": 1 },
          "age": { "type": "integer", "minimum": 0 }
        },
        "required": ["name"]
      }"""
      val parsed = JsonSchema.parse(json)
      assertTrue(parsed.isRight) && {
        val schemaObj = parsed.toOption.get.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(schemaObj.`type`.get == SchemaType.Single(JsonType.Object)) &&
        assertTrue(schemaObj.properties.get.contains("name")) && {
          val nameSchema = schemaObj.properties.get("name").asInstanceOf[JsonSchema.SchemaObject]
          assertTrue(nameSchema.minLength.get == NonNegativeInt.unsafe(1))
        }
      }
    },
    test("Schema.fromJsonSchema validation") {
      val jsonSchema = JsonSchema.parse("""{
        "type": "object",
        "properties": {
          "age": { "type": "integer", "minimum": 18 }
        }
      }""").toOption.get
      
      val schema = Schema.fromJsonSchema(jsonSchema)
      
      val validJson = Json.obj("age" -> Json.number(20))
      val invalidJson = Json.obj("age" -> Json.number(15))
      
      val decodedValid = schema.decode(JsonFormat)(ByteBuffer.wrap(Json.jsonCodec.encode(validJson)))
      val decodedInvalid = schema.decode(JsonFormat)(ByteBuffer.wrap(Json.jsonCodec.encode(invalidJson)))
      
      assertTrue(decodedValid.isRight) &&
      assertTrue(decodedInvalid.isLeft)
    }
  )

  private def getCodec[A](implicit schema: Schema[A]): JsonBinaryCodec[A] =
    schema.derive(JsonBinaryCodecDeriver)
}
