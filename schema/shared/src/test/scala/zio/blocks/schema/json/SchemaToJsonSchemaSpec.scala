package zio.blocks.schema.json

import zio.blocks.schema._
import zio.test._

/**
 * Tests for Schema[A].toJsonSchema - schema conversion to JSON Schema.
 *
 * Verifies that Schema instances produce correct JSON Schema representations.
 */
object SchemaToJsonSchemaSpec extends ZIOSpecDefault {

  // Test case classes
  case class SimpleRecord(name: String, value: Int)
  object SimpleRecord {
    implicit val schema: Schema[SimpleRecord] = Schema.derived
  }

  case class NestedRecord(id: String, inner: SimpleRecord)
  object NestedRecord {
    implicit val schema: Schema[NestedRecord] = Schema.derived
  }

  case class OptionalFields(required: String, optional: Option[Int])
  object OptionalFields {
    implicit val schema: Schema[OptionalFields] = Schema.derived
  }

  // Recursive type
  case class TreeNode(value: String, children: List[TreeNode])
  object TreeNode {
    implicit lazy val schema: Schema[TreeNode] = Schema.derived
  }

  sealed trait Status
  case object Active                 extends Status
  case object Inactive               extends Status
  case class Custom(message: String) extends Status
  object Status {
    implicit val schema: Schema[Status] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("SchemaToJsonSchemaSpec")(
    suite("Built-in schemas")(
      test("Schema[Int] produces integer schema") {
        val jsonSchema = Schema[Int].toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(schemaObj.`type` == Some(JsonType.Integer))
      },
      test("Schema[String] produces string schema") {
        val jsonSchema = Schema[String].toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(schemaObj.`type` == Some(JsonType.String))
      },
      test("Schema[Boolean] produces boolean schema") {
        val jsonSchema = Schema[Boolean].toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(schemaObj.`type` == Some(JsonType.Boolean))
      },
      test("Schema[Double] produces number schema") {
        val jsonSchema = Schema[Double].toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(schemaObj.`type` == Some(JsonType.Number))
      },
      test("Schema[Unit] produces object or null schema") {
        val jsonSchema = Schema[Unit].toJsonSchema
        assertTrue(jsonSchema.isInstanceOf[JsonSchema.SchemaObject])
      },
      test("Schema[BigDecimal] produces number schema") {
        val jsonSchema = Schema[BigDecimal].toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(schemaObj.`type` == Some(JsonType.Number))
      },
      test("Schema[BigInt] produces integer schema") {
        val jsonSchema = Schema[BigInt].toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(schemaObj.`type` == Some(JsonType.Integer))
      }
    ),
    suite("Temporal schemas")(
      test("Schema[Instant] produces string with date-time format") {
        val jsonSchema = Schema[java.time.Instant].toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.String),
          schemaObj.format == Some("date-time")
        )
      },
      test("Schema[LocalDate] produces string with date format") {
        val jsonSchema = Schema[java.time.LocalDate].toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.String),
          schemaObj.format == Some("date")
        )
      },
      test("Schema[UUID] produces string with uuid format") {
        val jsonSchema = Schema[java.util.UUID].toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.String),
          schemaObj.format == Some("uuid")
        )
      }
    ),
    suite("Derived schemas")(
      test("SimpleRecord produces object schema with properties") {
        val jsonSchema = Schema[SimpleRecord].toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.Object),
          schemaObj.properties.isDefined,
          schemaObj.properties.get.contains("name"),
          schemaObj.properties.get.contains("value")
        )
      },
      test("NestedRecord produces nested object schema") {
        val jsonSchema = Schema[NestedRecord].toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.properties.isDefined,
          schemaObj.properties.get.contains("id"),
          schemaObj.properties.get.contains("inner")
        )
      },
      test("OptionalFields handles Option correctly") {
        val jsonSchema = Schema[OptionalFields].toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.properties.isDefined,
          schemaObj.properties.get.contains("required"),
          schemaObj.properties.get.contains("optional")
        )
      },
      test("Sealed trait produces schema") {
        val jsonSchema = Schema[Status].toJsonSchema
        assertTrue(jsonSchema.isInstanceOf[JsonSchema.SchemaObject])
      }
    ),
    suite("Collection schemas")(
      test("Schema[List[Int]] produces array schema") {
        val jsonSchema = Schema[List[Int]].toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.Array),
          schemaObj.items.isDefined
        )
      },
      test("Schema[Vector[String]] produces array schema") {
        val jsonSchema = Schema[Vector[String]].toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(schemaObj.`type` == Some(JsonType.Array))
      },
      test("Schema[Set[String]] produces array schema") {
        val jsonSchema = Schema[Set[String]].toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(schemaObj.`type` == Some(JsonType.Array))
      }
    ),
    suite("Map schemas")(
      test("Schema[Map[String, Int]] produces object schema") {
        val jsonSchema = Schema[Map[String, Int]].toJsonSchema
        val schemaObj  = jsonSchema.asInstanceOf[JsonSchema.SchemaObject]
        assertTrue(
          schemaObj.`type` == Some(JsonType.Object),
          schemaObj.additionalProperties.isDefined
        )
      }
    ),
    suite("Recursive types")(
      test("TreeNode recursive schema produces valid JSON Schema") {
        val jsonSchema = Schema[TreeNode].toJsonSchema
        assertTrue(jsonSchema.isInstanceOf[JsonSchema.SchemaObject])
      }
    ),
    suite("Consistency with codec schema")(
      test("Schema.toJsonSchema matches codec.toJsonSchema for primitives") {
        val schemaJsonSchema = Schema[String].toJsonSchema
        val codecJsonSchema  = Schema[String].derive(JsonFormat.deriver).toJsonSchema
        assertTrue(
          schemaJsonSchema.asInstanceOf[JsonSchema.SchemaObject].`type` ==
            codecJsonSchema.asInstanceOf[JsonSchema.SchemaObject].`type`
        )
      },
      test("Schema.toJsonSchema matches codec.toJsonSchema for records") {
        val schemaJsonSchema = Schema[SimpleRecord].toJsonSchema
        val codecJsonSchema  = Schema[SimpleRecord].derive(JsonFormat.deriver).toJsonSchema
        assertTrue(
          schemaJsonSchema.asInstanceOf[JsonSchema.SchemaObject].`type` ==
            codecJsonSchema.asInstanceOf[JsonSchema.SchemaObject].`type`
        )
      }
    )
  )
}
