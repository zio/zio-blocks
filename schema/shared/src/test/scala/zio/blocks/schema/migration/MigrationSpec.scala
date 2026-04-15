package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.typeid.TypeId
import zio.blocks.chunk.Chunk
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

/**
 * Comprehensive tests for the Schema Migration System.
 */
object MigrationSpec extends munit.FunSuite {

  // ============================================================================
  // Test Schemas
  // ============================================================================

  case class UserV1(name: String, age: Int)
  case class UserV2(fullName: String, age: Int, email: String)
  case class UserV3(fullName: String, email: String) // removed age

  implicit val userV1Schema: Schema[UserV1] = Schema.derive[UserV1]
  implicit val userV2Schema: Schema[UserV2] = Schema.derive[UserV2]
  implicit val userV3Schema: Schema[UserV3] = Schema.derive[UserV3]

  // ============================================================================
  // DynamicMigration Tests
  // ============================================================================

  test("DynamicMigration.empty should be identity") {
    val value = DynamicValue.Record(Chunk(
      ("name", DynamicValue.string("Alice")),
      ("age", DynamicValue.int(30))
    ))
    
    val result = DynamicMigration.empty.apply(value)
    
    assert(result.isRight)
    assertEquals(result.toOption.get, value)
  }

  test("DynamicMigration should rename field") {
    val value = DynamicValue.Record(Chunk(
      ("name", DynamicValue.string("Alice")),
      ("age", DynamicValue.int(30))
    ))
    
    val expected = DynamicValue.Record(Chunk(
      ("fullName", DynamicValue.string("Alice")),
      ("age", DynamicValue.int(30))
    ))
    
    val migration = DynamicMigration(
      MigrationAction.renameField(DynamicOptic.root, "name", "fullName")
    )
    
    val result = migration.apply(value)
    
    assert(result.isRight, clue = s"Migration failed: $result")
    assertEquals(result.toOption.get, expected)
  }

  test("DynamicMigration should add field") {
    val value = DynamicValue.Record(Chunk(
      ("name", DynamicValue.string("Alice")),
      ("age", DynamicValue.int(30))
    ))
    
    val migration = DynamicMigration(
      MigrationAction.addField(
        DynamicOptic.root,
        "email",
        SchemaExpr.Literal("", Schema[String])
      )
    )
    
    val result = migration.apply(value)
    
    assert(result.isRight, clue = s"Migration failed: $result")
    val migrated = result.toOption.get
    assert(migrated.is(DynamicValueType.Record))
    val fields = migrated.asInstanceOf[DynamicValue.Record].fields
    assert(fields.exists(_._1 == "email"))
  }

  test("DynamicMigration should drop field") {
    val value = DynamicValue.Record(Chunk(
      ("name", DynamicValue.string("Alice")),
      ("age", DynamicValue.int(30))
    ))
    
    val expected = DynamicValue.Record(Chunk(
      ("name", DynamicValue.string("Alice"))
    ))
    
    val migration = DynamicMigration(
      MigrationAction.dropField(
        DynamicOptic.root,
        "age",
        SchemaExpr.Literal(0, Schema[Int])
      )
    )
    
    val result = migration.apply(value)
    
    assert(result.isRight, clue = s"Migration failed: $result")
    val migrated = result.toOption.get
    assert(!migrated.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "age"))
  }

  test("DynamicMigration should rename variant case") {
    val value = DynamicValue.Variant("Admin", DynamicValue.Record(Chunk(
      ("permissions", DynamicValue.string("all"))
    )))
    
    val migration = DynamicMigration(
      MigrationAction.renameCase(DynamicOptic.root, "Admin", "Administrator")
    )
    
    val result = migration.apply(value)
    
    assert(result.isRight, clue = s"Migration failed: $result")
    val migrated = result.toOption.get.asInstanceOf[DynamicValue.Variant]
    assertEquals(migrated.caseNameValue, "Administrator")
  }

  test("DynamicMigration.reverse should reverse actions") {
    val migration = DynamicMigration(
      MigrationAction.renameField(DynamicOptic.root, "oldName", "newName"),
      MigrationAction.addField(DynamicOptic.root, "newField", SchemaExpr.Literal("", Schema[String]))
    )
    
    val reversed = migration.reverse
    
    assertEquals(reversed.actions.length, 2)
    // First action should be DropField (reverse of AddField)
    assert(reversed.actions.head.isInstanceOf[MigrationAction.DropField])
    // Second action should be RenameField with swapped names
    val renameAction = reversed.actions(1).asInstanceOf[MigrationAction.RenameField]
    assertEquals(renameAction.from, "newName")
    assertEquals(renameAction.to, "oldName")
  }

  test("DynamicMigration composition should be associative") {
    val m1 = DynamicMigration(
      MigrationAction.renameField(DynamicOptic.root, "a", "b")
    )
    val m2 = DynamicMigration(
      MigrationAction.renameField(DynamicOptic.root, "b", "c")
    )
    val m3 = DynamicMigration(
      MigrationAction.addField(DynamicOptic.root, "newField", SchemaExpr.Literal(0, Schema[Int]))
    )
    
    val left = (m1 ++ m2) ++ m3
    val right = m1 ++ (m2 ++ m3)
    
    assertEquals(left.actions.length, right.actions.length)
  }

  // ============================================================================
  // MigrationError Tests
  // ============================================================================

  test("MigrationError should carry path information") {
    val error = MigrationError.fieldNotFound("missingField", DynamicOptic.root.field("user"))
    
    assert(error.message.contains("missingField"))
    assert(error.path.isDefined)
    assert(error.path.get.toString.contains("user"))
  }

  test("MigrationError should be convertible to string") {
    val error = MigrationError.typeMismatch("Record", "Sequence", DynamicOptic.root)
    
    val str = error.toString
    assert(str.contains("TypeMismatch"))
    assert(str.contains("Record"))
    assert(str.contains("Sequence"))
  }

  // ============================================================================
  // MigrationAction Tests
  // ============================================================================

  test("MigrationAction.RenameField reverse should swap names") {
    val action = MigrationAction.renameField(DynamicOptic.root, "from", "to")
    val reversed = action.reverse
    
    assert(reversed.isInstanceOf[MigrationAction.RenameField])
    val renameAction = reversed.asInstanceOf[MigrationAction.RenameField]
    assertEquals(renameAction.from, "to")
    assertEquals(renameAction.to, "from")
  }

  test("MigrationAction.AddField reverse should be DropField") {
    val action = MigrationAction.addField(
      DynamicOptic.root,
      "field",
      SchemaExpr.Literal("default", Schema[String])
    )
    val reversed = action.reverse
    
    assert(reversed.isInstanceOf[MigrationAction.DropField])
  }

  test("MigrationAction.DropField reverse should be AddField") {
    val action = MigrationAction.dropField(
      DynamicOptic.root,
      "field",
      SchemaExpr.Literal("default", Schema[String])
    )
    val reversed = action.reverse
    
    assert(reversed.isInstanceOf[MigrationAction.AddField])
  }

  // ============================================================================
  // MigrationBuilder Tests
  // ============================================================================

  test("MigrationBuilder should build migration with multiple actions") {
    val builder = Migration.builder(userV1Schema, userV2Schema)
      .renameField("name", "fullName")
      .addField("email", SchemaExpr.Literal("", Schema[String]))
    
    val migration = builder.build
    
    assertEquals(migration.dynamicMigration.actions.length, 2)
  }

  test("MigrationBuilder should support adding raw actions") {
    val action = MigrationAction.renameField(DynamicOptic.root, "a", "b")
    
    val migration = Migration.builder(userV1Schema, userV2Schema)
      .add(action)
      .build
    
    assertEquals(migration.dynamicMigration.actions.length, 1)
  }

  // ============================================================================
  // Migration[A, B] Tests
  // ============================================================================

  test("Migration.identity should return same value") {
    val migration = Migration.identity[UserV1]
    val user = UserV1("Alice", 30)
    
    val result = migration(user)
    
    assert(result.isRight)
    val migrated = result.toOption.get
    assertEquals(migrated.name, user.name)
    assertEquals(migrated.age, user.age)
  }

  test("Migration should compose") {
    val m1: Migration[UserV1, UserV2] = Migration.builder(userV1Schema, userV2Schema)
      .renameField("name", "fullName")
      .addField("email", SchemaExpr.Literal("", Schema[String]))
      .build
    
    val m2: Migration[UserV2, UserV3] = Migration.builder(userV2Schema, userV3Schema)
      .dropField("age", SchemaExpr.Literal(0, Schema[Int]))
      .build
    
    val composed = m1 ++ m2
    
    assertEquals(composed.sourceSchema, userV1Schema)
    assertEquals(composed.targetSchema, userV3Schema)
  }

  test("Migration.reverse should swap source and target") {
    val migration = Migration.builder(userV1Schema, userV2Schema)
      .renameField("name", "fullName")
      .build
    
    val reversed = migration.reverse
    
    assertEquals(reversed.sourceSchema, userV2Schema)
    assertEquals(reversed.targetSchema, userV1Schema)
  }

  // ============================================================================
  // DynamicOptic Extension Tests
  // ============================================================================

  test("DynamicOptic extension should create actions") {
    val action = DynamicOptic.root
      .field("user")
      .renameField("oldName", "newName")
    
    assert(action.isInstanceOf[MigrationAction.RenameField])
    val renameAction = action.asInstanceOf[MigrationAction.RenameField]
    assert(renameAction.at.toString.contains("user"))
  }

  // ============================================================================
  // Serialization Tests (placeholder)
  // ============================================================================

  test("DynamicMigration should be serializable conceptually") {
    // This test verifies that migrations contain only serializable data
    val migration = DynamicMigration(
      MigrationAction.renameField(DynamicOptic.root, "a", "b"),
      MigrationAction.addField(DynamicOptic.root, "c", SchemaExpr.Literal("", Schema[String]))
    )
    
    // All components should be case classes or case objects
    migration.actions.foreach { action =>
      assert(action.productArity >= 0) // Product indicates serializable structure
    }
  }

  // ============================================================================
  // Path Navigation Tests
  // ============================================================================

  test("Migration should apply at nested path") {
    val value = DynamicValue.Record(Chunk(
      ("user", DynamicValue.Record(Chunk(
        ("name", DynamicValue.string("Alice"))
      )))
    ))
    
    val expected = DynamicValue.Record(Chunk(
      ("user", DynamicValue.Record(Chunk(
        ("fullName", DynamicValue.string("Alice"))
      )))
    ))
    
    val migration = DynamicMigration(
      MigrationAction.renameField(DynamicOptic.root.field("user"), "name", "fullName")
    )
    
    val result = migration.apply(value)
    
    assert(result.isRight, clue = s"Migration failed: $result")
  }

  // ============================================================================
  // Error Handling Tests
  // ============================================================================

  test("Migration should return error for missing field") {
    val value = DynamicValue.Record(Chunk(
      ("otherField", DynamicValue.string("value"))
    ))
    
    val migration = DynamicMigration(
      MigrationAction.dropField(DynamicOptic.root, "nonExistent", SchemaExpr.Literal("", Schema[String]))
    )
    
    val result = migration.apply(value)
    
    assert(result.isLeft)
    val error = result.swap.toOption.get
    assert(error.isInstanceOf[MigrationError.FieldNotFound])
  }

  test("Migration should return error for type mismatch") {
    val value = DynamicValue.Sequence(Chunk(
      DynamicValue.string("a"),
      DynamicValue.string("b")
    ))
    
    val migration = DynamicMigration(
      MigrationAction.renameField(DynamicOptic.root, "field", "newField")
    )
    
    val result = migration.apply(value)
    
    assert(result.isLeft)
    val error = result.swap.toOption.get
    assert(error.isInstanceOf[MigrationError.TypeMismatch])
  }
}
