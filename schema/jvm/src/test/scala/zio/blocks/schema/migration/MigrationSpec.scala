package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationSpec extends ZIOSpecDefault {

  def spec = suite("MigrationSpec")(
    suite("DynamicMigration")(
      test("addField adds a new field to a record") {
        val record = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
        ))

        val migration = DynamicMigration.addField("age", DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val result = migration(record)

        assertTrue(
          result == Right(DynamicValue.Record(Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age" -> DynamicValue.Primitive(PrimitiveValue.Int(0))
          )))
        )
      },

      test("dropField removes a field from a record") {
        val record = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
        ))

        val migration = DynamicMigration.dropField("age", DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val result = migration(record)

        assertTrue(
          result == Right(DynamicValue.Record(Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )))
        )
      },

      test("renameField renames a field") {
        val record = DynamicValue.Record(Vector(
          "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
        ))

        val migration = DynamicMigration.renameField("firstName", "name")
        val result = migration(record)

        assertTrue(
          result == Right(DynamicValue.Record(Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )))
        )
      },

      test("compose migrations with ++") {
        val record = DynamicValue.Record(Vector(
          "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
        ))

        val migration = DynamicMigration.renameField("firstName", "name") ++
          DynamicMigration.addField("age", DynamicValue.Primitive(PrimitiveValue.Int(0)))

        val result = migration(record)

        assertTrue(
          result == Right(DynamicValue.Record(Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age" -> DynamicValue.Primitive(PrimitiveValue.Int(0))
          )))
        )
      },

      test("reverse migration undoes changes") {
        val original = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
        ))

        val migration = DynamicMigration.addField("age", DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val migrated = migration(original)
        val reversed = migration.reverse(migrated.toOption.get)

        assertTrue(reversed == Right(original))
      },

      test("nested field migration via path") {
        val record = DynamicValue.Record(Vector(
          "person" -> DynamicValue.Record(Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          ))
        ))

        val path = DynamicOptic.root.field("person")
        val migration = DynamicMigration.addFieldAt(path, "age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
        val result = migration(record)

        assertTrue(
          result == Right(DynamicValue.Record(Vector(
            "person" -> DynamicValue.Record(Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
            ))
          )))
        )
      },

      test("addField fails if field already exists") {
        val record = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
        ))

        val migration = DynamicMigration.addField("name", DynamicValue.Primitive(PrimitiveValue.String("Jane")))
        val result = migration(record)

        assertTrue(result.isLeft)
      },

      test("dropField fails if field doesn't exist") {
        val record = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
        ))

        val migration = DynamicMigration.dropField("age", DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val result = migration(record)

        assertTrue(result.isLeft)
      }
    ),

    suite("DynamicMigration Laws")(
      test("identity: empty migration doesn't change value") {
        val record = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
        ))

        val result = DynamicMigration.empty(record)
        assertTrue(result == Right(record))
      },

      test("associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
        val record = DynamicValue.Record(Vector(
          "a" -> DynamicValue.Primitive(PrimitiveValue.String("x"))
        ))

        val m1 = DynamicMigration.addField("b", DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val m2 = DynamicMigration.addField("c", DynamicValue.Primitive(PrimitiveValue.Int(2)))
        val m3 = DynamicMigration.addField("d", DynamicValue.Primitive(PrimitiveValue.Int(3)))

        val left = ((m1 ++ m2) ++ m3)(record)
        val right = (m1 ++ (m2 ++ m3))(record)

        assertTrue(left == right)
      }
    )
  )
}
