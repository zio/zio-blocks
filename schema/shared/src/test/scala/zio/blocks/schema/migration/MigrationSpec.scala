package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationSpec extends ZIOSpecDefault {

  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(fullName: String, age: Int, email: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class Product(name: String, price: Int)
  object Product {
    implicit val schema: Schema[Product] = Schema.derived
  }

  case class ProductV2(name: String, price: Double)
  object ProductV2 {
    implicit val schema: Schema[ProductV2] = Schema.derived
  }

  def spec = suite("MigrationSpec")(
    suite("DynamicMigration")(
      test("empty migration returns input unchanged") {
        val migration = DynamicMigration.empty
        val input     = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result    = migration(input)
        assertTrue(result == Right(input))
      },
      test("AddField adds a field to a record") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "newField",
            DynamicValue.Primitive(PrimitiveValue.String("default"))
          )
        )
        val input  = DynamicValue.Record(Vector("existing" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val result = migration(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "newField")) &&
            assertTrue(fields.exists(_._1 == "existing"))
          case _ => assertTrue(false)
        }
      },
      test("DropField removes a field from a record") {
        val migration = DynamicMigration.single(
          MigrationAction.DropField(DynamicOptic.root, "toRemove")
        )
        val input = DynamicValue.Record(
          Vector(
            "keep"     -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            "toRemove" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val result = migration(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "keep")) &&
            assertTrue(!fields.exists(_._1 == "toRemove"))
          case _ => assertTrue(false)
        }
      },
      test("RenameField renames a field") {
        val migration = DynamicMigration.single(
          MigrationAction.RenameField(DynamicOptic.root, "oldName", "newName")
        )
        val input  = DynamicValue.Record(Vector("oldName" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
        val result = migration(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "newName")) &&
            assertTrue(!fields.exists(_._1 == "oldName"))
          case _ => assertTrue(false)
        }
      },
      test("TransformField applies expression to field value") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformField(DynamicOptic.root, "value", MigrationExpr.IntAdd(10))
        )
        val input  = DynamicValue.Record(Vector("value" -> DynamicValue.Primitive(PrimitiveValue.Int(5))))
        val result = migration(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            fields.find(_._1 == "value") match {
              case Some((_, DynamicValue.Primitive(PrimitiveValue.Int(n)))) =>
                assertTrue(n == 15)
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },
      test("RenameCase renames a variant case") {
        val migration = DynamicMigration.single(
          MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        )
        val input  = DynamicValue.Variant("OldCase", DynamicValue.Record(Vector.empty))
        val result = migration(input)
        result match {
          case Right(DynamicValue.Variant(name, _)) =>
            assertTrue(name == "NewCase")
          case _ => assertTrue(false)
        }
      },
      test("composition applies actions in order") {
        val m1 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "new", DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        val m2 = DynamicMigration.single(
          MigrationAction.TransformField(DynamicOptic.root, "new", MigrationExpr.IntAdd(100))
        )
        val combined = m1 ++ m2
        val input    = DynamicValue.Record(Vector.empty)
        val result   = combined(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            fields.find(_._1 == "new") match {
              case Some((_, DynamicValue.Primitive(PrimitiveValue.Int(n)))) =>
                assertTrue(n == 100)
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationExpr")(
      test("Identity returns input unchanged") {
        val expr  = MigrationExpr.Identity
        val input = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assertTrue(expr(input) == Right(input))
      },
      test("Constant returns fixed value") {
        val expr  = MigrationExpr.Constant(DynamicValue.Primitive(PrimitiveValue.String("hello")))
        val input = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assertTrue(expr(input) == Right(DynamicValue.Primitive(PrimitiveValue.String("hello"))))
      },
      test("IntAdd adds to integer") {
        val expr   = MigrationExpr.IntAdd(10)
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(5))
        val result = expr(input)
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.Int(n))) =>
            assertTrue(n == 15)
          case _ => assertTrue(false)
        }
      },
      test("IntMultiply multiplies integer") {
        val expr   = MigrationExpr.IntMultiply(3)
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(7))
        val result = expr(input)
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.Int(n))) =>
            assertTrue(n == 21)
          case _ => assertTrue(false)
        }
      },
      test("StringAppend appends to string") {
        val expr   = MigrationExpr.StringAppend(" world")
        val input  = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val result = expr(input)
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.String(s))) =>
            assertTrue(s == "hello world")
          case _ => assertTrue(false)
        }
      },
      test("StringPrepend prepends to string") {
        val expr   = MigrationExpr.StringPrepend("hello ")
        val input  = DynamicValue.Primitive(PrimitiveValue.String("world"))
        val result = expr(input)
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.String(s))) =>
            assertTrue(s == "hello world")
          case _ => assertTrue(false)
        }
      },
      test("IntToLong converts int to long") {
        val expr   = MigrationExpr.IntToLong
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = expr(input)
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.Long(n))) =>
            assertTrue(n == 42L)
          case _ => assertTrue(false)
        }
      },
      test("IntToString converts int to string") {
        val expr   = MigrationExpr.IntToString
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = expr(input)
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.String(s))) =>
            assertTrue(s == "42")
          case _ => assertTrue(false)
        }
      },
      test("Compose chains expressions") {
        val expr   = MigrationExpr.IntAdd(5).andThen(MigrationExpr.IntMultiply(2))
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(10))
        val result = expr(input)
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.Int(n))) =>
            assertTrue(n == 30) // (10 + 5) * 2
          case _ => assertTrue(false)
        }
      },
      test("GetField extracts field from record") {
        val expr   = MigrationExpr.GetField("name")
        val input  = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))
        val result = expr(input)
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.String(s))) =>
            assertTrue(s == "John")
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Migration (type-safe)")(
      test("identity migration returns input unchanged") {
        val migration = Migration.identity[PersonV1]
        val input     = PersonV1("John", 30)
        val result    = migration(input)
        assertTrue(result == Right(input))
      },
      test("renameField renames field in typed migration") {
        val migration = Migration
          .from[PersonV1, PersonV2]
          .renameField("name", "fullName")
          .addField("email", "")
          .buildPartial()

        val input  = PersonV1("John", 30)
        val result = migration(input)
        assertTrue(result == Right(PersonV2("John", 30, "")))
      },
      test("transformField with IntToDouble changes field type") {
        val migration = Migration
          .from[Product, ProductV2]
          .changeFieldType("price", MigrationExpr.IntToDouble)
          .buildPartial()

        val input  = Product("Widget", 100)
        val result = migration(input)
        assertTrue(result == Right(ProductV2("Widget", 100.0)))
      },
      test("composition of migrations works") {
        val m1 = Migration
          .from[PersonV1, PersonV1]
          .transformField("age", MigrationExpr.IntAdd(1))
          .buildPartial()

        val m2 = Migration
          .from[PersonV1, PersonV1]
          .transformField("age", MigrationExpr.IntMultiply(2))
          .buildPartial()

        val combined = m1 ++ m2
        val input    = PersonV1("John", 10)
        val result   = combined(input)
        assertTrue(result == Right(PersonV1("John", 22))) // (10 + 1) * 2
      }
    ),
    suite("migration laws")(
      test("identity law: identity(a) == a") {
        val migration = Migration.identity[PersonV1]
        val input     = PersonV1("Alice", 25)
        assertTrue(migration(input) == Right(input))
      },
      test("associativity law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
        val m1 = Migration
          .from[PersonV1, PersonV1]
          .transformField("age", MigrationExpr.IntAdd(1))
          .buildPartial()
        val m2 = Migration
          .from[PersonV1, PersonV1]
          .transformField("age", MigrationExpr.IntMultiply(2))
          .buildPartial()
        val m3 = Migration
          .from[PersonV1, PersonV1]
          .transformField("age", MigrationExpr.IntAdd(3))
          .buildPartial()

        val left  = (m1 ++ m2) ++ m3
        val right = m1 ++ (m2 ++ m3)

        val input = PersonV1("Test", 5)
        assertTrue(left(input) == right(input))
      }
    ),
    suite("reverse migration")(
      test("AddField reversed becomes DropField") {
        val action   = MigrationAction.AddField(DynamicOptic.root, "field", DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val reversed = MigrationAction.reverseAction(action)
        reversed match {
          case Vector(MigrationAction.DropField(_, name)) =>
            assertTrue(name == "field")
          case _ => assertTrue(false)
        }
      },
      test("RenameField reversed swaps names") {
        val action   = MigrationAction.RenameField(DynamicOptic.root, "old", "new")
        val reversed = MigrationAction.reverseAction(action)
        reversed match {
          case Vector(MigrationAction.RenameField(_, o, n)) =>
            assertTrue(o == "new" && n == "old")
          case _ => assertTrue(false)
        }
      },
      test("DropField cannot be reversed") {
        val action   = MigrationAction.DropField(DynamicOptic.root, "field")
        val reversed = MigrationAction.reverseAction(action)
        assertTrue(reversed.isEmpty)
      }
    )
  )
}
