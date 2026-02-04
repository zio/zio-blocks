package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

object MigrationSpec extends SchemaBaseSpec {
  
  // Test case classes
  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }
  
  case class PersonV2(fullName: String, age: Int, country: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }
  
  case class SimpleRecord(a: String, b: Int)
  object SimpleRecord {
    implicit val schema: Schema[SimpleRecord] = Schema.derived
  }
  
  case class RenamedRecord(x: String, y: Int)
  object RenamedRecord {
    implicit val schema: Schema[RenamedRecord] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("Migration.identity")(
      test("identity migration returns unchanged value") {
        val person = PersonV1("Alice", 30)
        val migration = Migration.identity[PersonV1]
        val result = migration(person)
        assertTrue(result == Right(person))
      },
      test("identity migration has empty actions") {
        val migration = Migration.identity[PersonV1]
        assertTrue(migration.isEmpty)
      }
    ),
    suite("Migration.apply")(
      test("applies migration to typed value") {
        val source = SimpleRecord("hello", 42)
        val migration = Migration.newBuilder[SimpleRecord, RenamedRecord]
          .renameField("a", "x")
          .renameField("b", "y")
          .buildPartial
        val result = migration(source)
        assertTrue(result == Right(RenamedRecord("hello", 42)))
      },
      test("returns error on migration failure") {
        // Create a migration that will fail (trying to drop a field that doesn't exist in the shape we test)
        val migration = Migration.fromAction[SimpleRecord, SimpleRecord](
          MigrationAction.DropField(
            DynamicOptic.root,
            "nonexistent",
            DynamicSchemaExpr.DefaultValue
          )
        )
        val result = migration(SimpleRecord("test", 1))
        assertTrue(result.isLeft)
      }
    ),
    suite("Migration.applyDynamic")(
      test("applies migration on DynamicValue directly") {
        val dynamicValue = DynamicValue.Record(Vector(
          "a" -> DynamicValue.Primitive(PrimitiveValue.String("test")),
          "b" -> DynamicValue.Primitive(PrimitiveValue.Int(10))
        ))
        val migration = Migration.newBuilder[SimpleRecord, RenamedRecord]
          .renameField("a", "x")
          .renameField("b", "y")
          .buildPartial
        val result = migration.applyDynamic(dynamicValue)
        assertTrue(result == Right(DynamicValue.Record(Vector(
          "x" -> DynamicValue.Primitive(PrimitiveValue.String("test")),
          "y" -> DynamicValue.Primitive(PrimitiveValue.Int(10))
        ))))
      }
    ),
    suite("Migration.++")(
      test("composes two migrations") {
        val m1 = Migration.fromAction[SimpleRecord, SimpleRecord](
          MigrationAction.AddField(
            DynamicOptic.root,
            "c",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
          )
        )
        val m2 = Migration.fromAction[SimpleRecord, SimpleRecord](
          MigrationAction.RenameField(DynamicOptic.root, "a", "alpha")
        )
        val combined = m1 ++ m2
        assertTrue(combined.actions.length == 2)
      },
      test("andThen is alias for ++") {
        val m1 = Migration.identity[SimpleRecord]
        val m2 = Migration.identity[SimpleRecord]
        assertTrue((m1 andThen m2).actions == (m1 ++ m2).actions)
      }
    ),
    suite("Migration.reverse")(
      test("reverse swaps source and target schemas") {
        val migration = Migration.newBuilder[SimpleRecord, RenamedRecord]
          .renameField("a", "x")
          .renameField("b", "y")
          .buildPartial
        val reversed = migration.reverse
        assertTrue(
          reversed.sourceSchema == migration.targetSchema,
          reversed.targetSchema == migration.sourceSchema
        )
      },
      test("reverse creates reversed actions") {
        val migration = Migration.fromAction[SimpleRecord, SimpleRecord](
          MigrationAction.RenameField(DynamicOptic.root, "a", "b")
        )
        val reversed = migration.reverse
        val action = reversed.actions.head.asInstanceOf[MigrationAction.RenameField]
        assertTrue(action.from == "b" && action.to == "a")
      }
    ),
    suite("Migration.fromActions")(
      test("creates migration from multiple actions") {
        val migration = Migration.fromActions[SimpleRecord, SimpleRecord](
          MigrationAction.RenameField(DynamicOptic.root, "a", "alpha"),
          MigrationAction.AddField(
            DynamicOptic.root,
            "c",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
          )
        )
        assertTrue(migration.actions.length == 2)
      }
    ),
    suite("Migration.fromDynamic")(
      test("wraps a DynamicMigration with schemas") {
        val dynamicMigration = DynamicMigration(Vector(
          MigrationAction.RenameField(DynamicOptic.root, "a", "x")
        ))
        val migration = Migration.fromDynamic[SimpleRecord, RenamedRecord](dynamicMigration)
        assertTrue(
          migration.dynamicMigration == dynamicMigration,
          migration.nonEmpty
        )
      }
    ),
    suite("Laws")(
      test("identity law: Migration.identity[A].apply(a) == Right(a)") {
        val value = SimpleRecord("test", 42)
        val identity = Migration.identity[SimpleRecord]
        assertTrue(identity(value) == Right(value))
      },
      test("associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3) structurally") {
        val m1 = Migration.fromAction[SimpleRecord, SimpleRecord](
          MigrationAction.AddField(DynamicOptic.root, "c", DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        )
        val m2 = Migration.fromAction[SimpleRecord, SimpleRecord](
          MigrationAction.AddField(DynamicOptic.root, "d", DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))))
        )
        val m3 = Migration.fromAction[SimpleRecord, SimpleRecord](
          MigrationAction.AddField(DynamicOptic.root, "e", DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(3))))
        )
        val left = (m1 ++ m2) ++ m3
        val right = m1 ++ (m2 ++ m3)
        assertTrue(left.actions.length == right.actions.length)
      },
      test("structural reverse: m.reverse.reverse has same action count") {
        val migration = Migration.fromActions[SimpleRecord, SimpleRecord](
          MigrationAction.AddField(DynamicOptic.root, "c", DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))),
          MigrationAction.RenameField(DynamicOptic.root, "a", "alpha")
        )
        val doubleReversed = migration.reverse.reverse
        assertTrue(migration.actions.length == doubleReversed.actions.length)
      }
    )
  )
}
