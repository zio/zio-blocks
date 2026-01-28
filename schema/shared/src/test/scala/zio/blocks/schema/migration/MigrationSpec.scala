package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}
import zio.test._

object MigrationSpec extends ZIOSpecDefault {

  case class AddressV1(street: String, city: String)
  object AddressV1 {
    implicit val schema: Schema[AddressV1] = Schema.derived
  }

  case class AddressV2(street: String, city: String, zip: Option[String])
  object AddressV2 {
    implicit val schema: Schema[AddressV2] = Schema.derived
  }

  override def spec: Spec[Any, Nothing] = suite("MigrationSpec")(
    suite("DynamicMigration")(
      test("identity migration returns value unchanged") {
        val migration = DynamicMigration.identity
        val value     = DynamicValue.Record(
          Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("John")))
        )
        assertTrue(migration(value) == Right(value))
      },
      test("rename field changes field name") {
        val migration = DynamicMigration.renameField("firstName", "givenName")
        val input     = DynamicValue.Record(
          Chunk("firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")))
        )
        val expected = DynamicValue.Record(
          Chunk("givenName" -> DynamicValue.Primitive(PrimitiveValue.String("John")))
        )
        assertTrue(migration(input) == Right(expected))
      },
      test("add field with default value") {
        val migration = DynamicMigration.addField(
          "country",
          DynamicValue.Primitive(PrimitiveValue.String("USA"))
        )
        val input = DynamicValue.Record(
          Chunk("city" -> DynamicValue.Primitive(PrimitiveValue.String("New York")))
        )
        val result = migration(input)
        assertTrue(
          result.isRight &&
            result.toOption.get
              .asInstanceOf[DynamicValue.Record]
              .fields
              .exists(_._1 == "country")
        )
      },
      test("drop field removes field from record") {
        val migration = DynamicMigration.dropField("obsoleteField")
        val input     = DynamicValue.Record(
          Chunk(
            "name"          -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "obsoleteField" -> DynamicValue.Primitive(PrimitiveValue.String("remove me"))
          )
        )
        val result = migration(input)
        assertTrue(
          result.isRight &&
            !result.toOption.get
              .asInstanceOf[DynamicValue.Record]
              .fields
              .exists(_._1 == "obsoleteField")
        )
      },
      test("transform nested field applies transformation") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("address").field("city"),
            SchemaExpr.Identity
          )
        )
        val input = DynamicValue.Record(
          Chunk(
            "address" -> DynamicValue.Record(
              Chunk("city" -> DynamicValue.Primitive(PrimitiveValue.String("Boston")))
            )
          )
        )
        val result = migration(input)
        assertTrue(result == Right(input))
      },
      test("compose migrations applies them in sequence") {
        val rename     = DynamicMigration.renameField("old", "new")
        val addDefault = DynamicMigration.addField(
          "extra",
          DynamicValue.Primitive(PrimitiveValue.Int(42))
        )
        val composed = rename ++ addDefault
        val input    = DynamicValue.Record(
          Chunk("old" -> DynamicValue.Primitive(PrimitiveValue.String("value")))
        )
        val result = composed(input)
        assertTrue(
          result.isRight && {
            val record = result.toOption.get.asInstanceOf[DynamicValue.Record]
            record.fields.exists(_._1 == "new") &&
            record.fields.exists(_._1 == "extra")
          }
        )
      }
    ),
    suite("MigrationAction")(
      test("AddField action adds field with default") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "newField",
          DynamicValue.Primitive(PrimitiveValue.Boolean(true))
        )
        val input = DynamicValue.Record(
          Chunk("existing" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val result = action(input)
        assertTrue(
          result.isRight &&
            result.toOption.get
              .asInstanceOf[DynamicValue.Record]
              .fields
              .length == 2
        )
      },
      test("Rename action preserves field value") {
        val action = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        val input  = DynamicValue.Record(
          Chunk("a" -> DynamicValue.Primitive(PrimitiveValue.Long(100L)))
        )
        val result = action(input)
        assertTrue(
          result.isRight && {
            val fields = result.toOption.get.asInstanceOf[DynamicValue.Record].fields
            fields.exists { case (name, value) =>
              name == "b" && value == DynamicValue.Primitive(PrimitiveValue.Long(100L))
            }
          }
        )
      },
      test("DropField action removes specified field") {
        val action = MigrationAction.DropField(DynamicOptic.root, "toRemove", None)
        val input  = DynamicValue.Record(
          Chunk(
            "keep"     -> DynamicValue.Primitive(PrimitiveValue.String("stays")),
            "toRemove" -> DynamicValue.Primitive(PrimitiveValue.String("goes"))
          )
        )
        val result = action(input)
        assertTrue(
          result.isRight &&
            result.toOption.get.asInstanceOf[DynamicValue.Record].fields.length == 1
        )
      }
    ),
    suite("SchemaExpr")(
      test("Identity returns input unchanged") {
        val input = DynamicValue.Primitive(PrimitiveValue.String("test"))
        assertTrue(SchemaExpr.Identity(input) == Right(input))
      },
      test("Constant always returns the same value") {
        val constant = SchemaExpr.Constant(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        val input    = DynamicValue.Primitive(PrimitiveValue.String("ignored"))
        assertTrue(
          constant(input) == Right(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
      },
      test("Compose chains expressions") {
        val composed = SchemaExpr.Compose(SchemaExpr.Identity, SchemaExpr.Identity)
        val input    = DynamicValue.Primitive(PrimitiveValue.Double(3.14))
        assertTrue(composed(input) == Right(input))
      },
      test("StringExpr.ToUpperCase transforms string") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val result = SchemaExpr.StringExpr.ToUpperCase(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("HELLO"))))
      },
      test("StringExpr.ToLowerCase transforms string") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("WORLD"))
        val result = SchemaExpr.StringExpr.ToLowerCase(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("world"))))
      },
      test("NumericExpr.Add increments value") {
        val expr   = SchemaExpr.NumericExpr.Add(10)
        val input  = DynamicValue.Primitive(PrimitiveValue.Long(5L))
        val result = expr(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(15L))))
      },
      test("PrimitiveConvert converts Int to String") {
        val expr = SchemaExpr.PrimitiveConvert(
          SchemaExpr.PrimitiveType.Int,
          SchemaExpr.PrimitiveType.String
        )
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(123))
        val result = expr(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("123"))))
      }
    ),
    suite("MigrationBuilder")(
      test("builder accumulates actions correctly") {
        val builder = MigrationBuilder[AddressV1, AddressV2](
          AddressV1.schema,
          AddressV2.schema,
          Vector.empty
        ).addFieldAt(
          DynamicOptic.root,
          "zip",
          DynamicValue.Variant("None", DynamicValue.Primitive(PrimitiveValue.Unit))
        )
        assertTrue(builder.actions.length == 1)
      },
      test("builder supports chaining multiple operations") {
        val builder = MigrationBuilder[AddressV1, AddressV2](
          AddressV1.schema,
          AddressV2.schema,
          Vector.empty
        ).renameFieldAt(DynamicOptic.root, "street", "streetAddress")
          .addFieldAt(
            DynamicOptic.root,
            "zip",
            DynamicValue.Variant("None", DynamicValue.Primitive(PrimitiveValue.Unit))
          )
        assertTrue(builder.actions.length == 2)
      },
      test("build creates executable migration") {
        val builder = MigrationBuilder[AddressV1, AddressV2](
          AddressV1.schema,
          AddressV2.schema,
          Vector.empty
        ).addFieldAt(
          DynamicOptic.root,
          "zip",
          DynamicValue.Variant("None", DynamicValue.Primitive(PrimitiveValue.Unit))
        )
        val migration = builder.buildDynamic
        val input     = DynamicValue.Record(
          Chunk(
            "street" -> DynamicValue.Primitive(PrimitiveValue.String("123 Main St")),
            "city"   -> DynamicValue.Primitive(PrimitiveValue.String("Boston"))
          )
        )
        val result = migration(input)
        assertTrue(result.isRight)
      }
    ),
    suite("MigrationError")(
      test("missingField error contains field name") {
        val error = MigrationError.missingField(DynamicOptic.root, "nonexistent")
        assertTrue(error.message.contains("nonexistent"))
      },
      test("typeMismatch error contains expected type") {
        val error = MigrationError.typeMismatch(DynamicOptic.root, "Record", "Primitive")
        assertTrue(error.message.contains("Record") && error.message.contains("Primitive"))
      }
    )
  )
}
