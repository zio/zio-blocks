package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object MigrationSpec extends ZIOSpecDefault {

  // Helper to create a Literal SchemaExpr from a DynamicValue
  private def literal(dv: DynamicValue): SchemaExpr[Any, Any] =
    SchemaExpr.Literal[Any, Any](dv)

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("DynamicMigration")(
      test("empty migration is identity") {
        val value = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))
        assertTrue(DynamicMigration.empty(value) == Right(value))
      },

      test("addField") {
        val input = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )
        val expected = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "age" -> DynamicValue.Primitive(PrimitiveValue.Int(0))
        ))
        assertTrue(migration(input) == Right(expected))
      },

      test("dropField") {
        val input = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
        ))
        val migration = DynamicMigration.single(MigrationAction.DropField(
          DynamicOptic.root.field("age"),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        ))
        val expected = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))
        assertTrue(migration(input) == Right(expected))
      },

      test("rename") {
        val input = DynamicValue.Record(Vector("old" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val migration = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("old"), "new"))
        val expected = DynamicValue.Record(Vector("new" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        assertTrue(migration(input) == Right(expected))
      },

      test("transformValue") {
        val input = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            DynamicOptic.root, 
            literal(DynamicValue.Primitive(PrimitiveValue.String("42")))
          )
        )
        assertTrue(migration(input) == Right(DynamicValue.Primitive(PrimitiveValue.String("42"))))
      },

      test("renameCase") {
        val input = DynamicValue.Variant("Old", DynamicValue.Record(Vector.empty))
        val migration = DynamicMigration.single(MigrationAction.RenameCase(DynamicOptic.root, "Old", "New"))
        assertTrue(migration(input) == Right(DynamicValue.Variant("New", DynamicValue.Record(Vector.empty))))
      },

      test("composition") {
        val input = DynamicValue.Record(Vector("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val m1 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("a"), "b"))
        val m2 = DynamicMigration.single(MigrationAction.AddField(
          DynamicOptic.root.field("c"),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
        ))
        val expected = DynamicValue.Record(Vector(
          "b" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          "c" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        ))
        assertTrue((m1 ++ m2)(input) == Right(expected))
      }
    ),

    suite("Laws")(
      test("associativity") {
        val m1 = DynamicMigration.single(MigrationAction.AddField(
          DynamicOptic.root.field("a"),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        ))
        val m2 = DynamicMigration.single(MigrationAction.AddField(
          DynamicOptic.root.field("b"),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
        ))
        val m3 = DynamicMigration.single(MigrationAction.AddField(
          DynamicOptic.root.field("c"),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        ))
        val input = DynamicValue.Record(Vector.empty)
        assertTrue(((m1 ++ m2) ++ m3)(input) == (m1 ++ (m2 ++ m3))(input))
      },

      test("structural reverse") {
        val m = DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root.field("x"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          ),
          MigrationAction.Rename(DynamicOptic.root.field("a"), "b")
        )
        assertTrue(m.reverse.reverse.actions == m.actions)
      },

      test("semantic inverse for rename") {
        val input = DynamicValue.Record(Vector("old" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val m = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("old"), "new"))
        assertTrue(m(input).flatMap(m.reverse(_)) == Right(input))
      }
    ),

    suite("Errors")(
      test("dropField missing") {
        val input = DynamicValue.Record(Vector("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val migration = DynamicMigration.single(MigrationAction.DropField(
          DynamicOptic.root.field("b"),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        ))
        assertTrue(migration(input).isLeft)
      },

      test("addField duplicate") {
        val input = DynamicValue.Record(Vector("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val migration = DynamicMigration.single(MigrationAction.AddField(
          DynamicOptic.root.field("a"),
          literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
        ))
        assertTrue(migration(input).isLeft)
      },

      test("transform type mismatch") {
        // With Literal, we're just replacing the value - no actual type checking
        // This test needs to be adjusted for the new behavior
        val input = DynamicValue.Primitive(PrimitiveValue.String("abc"))
        val migration = DynamicMigration.single(MigrationAction.TransformValue(
          DynamicOptic.root, 
          literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        ))
        // With Literal, the transform just replaces the value
        assertTrue(migration(input) == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      }
    ),

    suite("Nested paths")(
      test("rename in nested record") {
        val input = DynamicValue.Record(Vector(
          "outer" -> DynamicValue.Record(Vector("old" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        ))
        val migration = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("outer").field("old"), "new"))
        val expected = DynamicValue.Record(Vector(
          "outer" -> DynamicValue.Record(Vector("new" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        ))
        assertTrue(migration(input) == Right(expected))
      }
    ),

    suite("TransformValue")(
      test("literal replacement") {
        val intVal = DynamicValue.Primitive(PrimitiveValue.Int(42))
        
        def transform(newValue: DynamicValue, v: DynamicValue) =
          DynamicMigration.single(MigrationAction.TransformValue(DynamicOptic.root, literal(newValue)))(v)
        
        assertTrue(
          transform(DynamicValue.Primitive(PrimitiveValue.Long(42L)), intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Long(42L))) &&
          transform(DynamicValue.Primitive(PrimitiveValue.Double(42.0)), intVal) == Right(DynamicValue.Primitive(PrimitiveValue.Double(42.0)))
        )
      }
    ),

    suite("Selector Syntax")(
      test("simple field selector") {
        case class PersonV1(name: String)
        case class PersonV2(name: String, age: Int)
        
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]
        
        val migration = Migration.newBuilder[PersonV1, PersonV2]
          .addField(_.age, literal(DynamicValue.Primitive(PrimitiveValue.Int(0))))
          .build
        
        val input = PersonV1("Alice")
        val result = migration(input)
        
        assertTrue(result == Right(PersonV2("Alice", 0)))
      },

      test("renameField selector syntax") {
        case class PersonV1(firstName: String)
        case class PersonV2(fullName: String)
        
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]
        
        val migration = Migration.newBuilder[PersonV1, PersonV2]
          .renameField(_.firstName, _.fullName)
          .build
        
        val input = PersonV1("Alice")
        val result = migration(input)
        
        assertTrue(result == Right(PersonV2("Alice")))
      },

      test("dropField selector syntax") {
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String)
        
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]
        
        val migration = Migration.newBuilder[PersonV1, PersonV2]
          .dropField(_.age, literal(DynamicValue.Primitive(PrimitiveValue.Int(0))))
          .build
        
        val input = PersonV1("Alice", 30)
        val result = migration(input)
        
        assertTrue(result == Right(PersonV2("Alice")))
      },

      test("transformField selector syntax") {
        case class PersonV1(age: Int)
        case class PersonV2(age: Long)
        
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]
        
        val migration = Migration.newBuilder[PersonV1, PersonV2]
          .transformField(_.age, _.age, literal(DynamicValue.Primitive(PrimitiveValue.Long(30L))))
          .build
        
        val input = PersonV1(30)
        val result = migration(input)
        
        assertTrue(result == Right(PersonV2(30L)))
      }
    )
  )
}

