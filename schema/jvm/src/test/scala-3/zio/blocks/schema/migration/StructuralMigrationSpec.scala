package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

/**
 * Tests for structural type migrations.
 *
 * Structural types allow representing historical schema versions without
 * runtime overhead. Current versions use case classes, while past versions use
 * structural types that exist only at compile time.
 *
 * JVM-only because structural types require reflection.
 */
object StructuralMigrationSpec extends ZIOSpecDefault {

  case class PersonV2(fullName: String, age: Int, country: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class Address(street: String, city: String, zip: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class Employee(name: String, department: String, salary: Int)
  object Employee {
    implicit val schema: Schema[Employee] = Schema.derived
  }

  sealed trait PaymentMethod
  case class CreditCard(number: String, expiry: String)     extends PaymentMethod
  case class BankTransfer(account: String, routing: String) extends PaymentMethod
  object PaymentMethod {
    implicit val schema: Schema[PaymentMethod] = Schema.derived
  }

  private def getFieldCount(schema: Schema[_]): Int =
    schema.reflect.asInstanceOf[Reflect.Record[_, _]].fields.size

  private def getFieldNames(schema: Schema[_]): Set[String] =
    schema.reflect.asInstanceOf[Reflect.Record[_, _]].fields.map(_.name).toSet

  private def getFieldNamesList(schema: Schema[_]): List[String] =
    schema.reflect.asInstanceOf[Reflect.Record[_, _]].fields.map(_.name).toList

  private def getCaseCount(schema: Schema[_]): Int =
    schema.reflect.asInstanceOf[Reflect.Variant[_, _]].cases.size

  def spec = suite("StructuralMigrationSpec")(
    structuralSchemaTests,
    dynamicValueMigrationTests,
    fieldOperationTests,
    enumMigrationTests,
    serializationTests
  )

  private val structuralSchemaTests = suite("Structural schema conversion")(
    test("simple case class converts to structural") {
      val schema     = Schema.derived[PersonV2]
      val structural = schema.structural
      val count      = getFieldCount(structural)
      assertTrue(count == 3)
    },
    test("structural preserves field names for PersonV2") {
      val schema     = Schema.derived[PersonV2]
      val structural = schema.structural
      val names      = getFieldNames(structural)
      assertTrue(names == Set("fullName", "age", "country"))
    },
    test("structural preserves field names for Address") {
      val schema     = Schema.derived[Address]
      val structural = schema.structural
      val names      = getFieldNames(structural)
      assertTrue(names == Set("street", "city", "zip"))
    },
    test("structural preserves field order") {
      val schema     = Schema.derived[Employee]
      val structural = schema.structural
      val names      = getFieldNamesList(structural)
      assertTrue(names == List("name", "department", "salary"))
    },
    test("sealed trait converts to structural variant") {
      val schema     = Schema.derived[PaymentMethod]
      val structural = schema.structural
      val count      = getCaseCount(structural)
      assertTrue(count == 2)
    },
    test("structural schema is equivalent to nominal for toDynamicValue") {
      val person     = PersonV2("Alice", 30, "US")
      val nominal    = Schema.derived[PersonV2]
      val structural = nominal.structural

      val dvNominal    = nominal.toDynamicValue(person)
      val dvStructural = structural.toDynamicValue(person)

      assertTrue(dvNominal == dvStructural)
    },
    test("multiple structural conversions are idempotent") {
      val schema = Schema.derived[Address]
      val s1     = schema.structural
      val s2     = schema.structural

      val addr = Address("123 Main", "NYC", "10001")
      val dv1  = s1.toDynamicValue(addr)
      val dv2  = s2.toDynamicValue(addr)

      assertTrue(dv1 == dv2)
    },
    test("CreditCard structural has correct fields") {
      val schema     = Schema.derived[CreditCard]
      val structural = schema.structural
      val count      = getFieldCount(structural)
      assertTrue(count == 2)
    },
    test("BankTransfer structural has correct fields") {
      val schema     = Schema.derived[BankTransfer]
      val structural = schema.structural
      val names      = getFieldNames(structural)
      assertTrue(names == Set("account", "routing"))
    }
  )

  private val dynamicValueMigrationTests = suite("DynamicValue migration")(
    test("rename field migration succeeds") {
      val oldData = DynamicValue.Record(
        Chunk[(String, DynamicValue)](
          "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
        )
      )
      val migration = DynamicMigration(
        Chunk(MigrationAction.Rename(DynamicOptic.root, "firstName", "name"))
      )
      val result = migration.apply(oldData)
      assertTrue(result.isRight)
    },
    test("add field migration succeeds") {
      val oldData = DynamicValue.Record(
        Chunk[(String, DynamicValue)](
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
        )
      )
      val migration = DynamicMigration(
        Chunk(MigrationAction.AddField(DynamicOptic.root, "age", Resolved.Literal.int(25)))
      )
      val result = migration.apply(oldData)
      assertTrue(result.isRight)
    },
    test("drop field migration succeeds") {
      val oldData = DynamicValue.Record(
        Chunk[(String, DynamicValue)](
          "name"     -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
          "obsolete" -> DynamicValue.Primitive(PrimitiveValue.String("remove"))
        )
      )
      val migration = DynamicMigration(
        Chunk(MigrationAction.DropField(DynamicOptic.root, "obsolete", Resolved.Literal.string("")))
      )
      val result = migration.apply(oldData)
      assertTrue(result.isRight)
    },
    test("combined rename and add migration succeeds") {
      val oldData = DynamicValue.Record(
        Chunk[(String, DynamicValue)](
          "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("Jane")),
          "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
        )
      )

      val rename1   = MigrationAction.Rename(DynamicOptic.root, "firstName", "first")
      val rename2   = MigrationAction.Rename(DynamicOptic.root, "lastName", "last")
      val add       = MigrationAction.AddField(DynamicOptic.root, "verified", Resolved.Literal.boolean(true))
      val migration = DynamicMigration(Chunk(rename1, rename2, add))
      val result    = migration.apply(oldData)

      assertTrue(result.isRight)
    },
    test("migration produces correct target type") {
      val oldData = DynamicValue.Record(
        Chunk[(String, DynamicValue)](
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(40))
        )
      )

      val renameAction = MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
      val addAction    = MigrationAction.AddField(DynamicOptic.root, "country", Resolved.Literal.string("Unknown"))
      val migration    = DynamicMigration(Chunk(renameAction, addAction))

      val result = for {
        migrated <- migration.apply(oldData)
        v2       <- PersonV2.schema.fromDynamicValue(migrated)
      } yield v2

      assertTrue(result == Right(PersonV2("Bob", 40, "Unknown")))
    },
    test("multiple field renames work correctly") {
      val oldData = DynamicValue.Record(
        Chunk[(String, DynamicValue)](
          "a" -> DynamicValue.Primitive(PrimitiveValue.String("val1")),
          "b" -> DynamicValue.Primitive(PrimitiveValue.String("val2"))
        )
      )
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root, "a", "x"),
          MigrationAction.Rename(DynamicOptic.root, "b", "y")
        )
      )
      val result = migration.apply(oldData)
      assertTrue(result.isRight)
    }
  )

  private val fieldOperationTests = suite("Field operations")(
    test("join fields with concat") {
      val oldData = DynamicValue.Record(
        Chunk[(String, DynamicValue)](
          "first" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "last"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe")),
          "age"   -> DynamicValue.Primitive(PrimitiveValue.Int(30))
        )
      )

      val join = MigrationAction.Join(
        at = DynamicOptic.root,
        targetFieldName = "fullName",
        sourcePaths = Chunk(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
        combiner = Resolved.Concat(
          Vector(
            Resolved.FieldAccess("first", Resolved.Identity),
            Resolved.Literal.string(" "),
            Resolved.FieldAccess("last", Resolved.Identity)
          ),
          ""
        ),
        splitter = Resolved.SplitString(Resolved.Identity, " ", 0)
      )
      val drop1 = MigrationAction.DropField(DynamicOptic.root, "first", Resolved.Literal.string(""))
      val drop2 = MigrationAction.DropField(DynamicOptic.root, "last", Resolved.Literal.string(""))
      val add   = MigrationAction.AddField(DynamicOptic.root, "country", Resolved.Literal.string("US"))

      val migration = DynamicMigration(Chunk(join, drop1, drop2, add))
      val result    = migration.apply(oldData).flatMap(PersonV2.schema.fromDynamicValue)

      assertTrue(result == Right(PersonV2("John Doe", 30, "US")))
    },
    test("optionalize field creates correct action") {
      val action    = MigrationAction.Optionalize(DynamicOptic.root, "email")
      val fieldName = action.fieldName
      assertTrue(fieldName == "email")
    },
    test("mandate field creates correct action") {
      val action    = MigrationAction.Mandate(DynamicOptic.root, "status", Resolved.Literal.string("active"))
      val fieldName = action.fieldName
      assertTrue(fieldName == "status")
    },
    test("nested field path can be created") {
      val path     = DynamicOptic.root.field("address").field("street")
      val pathStr  = path.toString
      val contains = pathStr.contains("address")
      assertTrue(contains)
    },
    test("collection element path can be created") {
      val path   = DynamicOptic.root.field("items").elements
      val action = MigrationAction.TransformElements(path, Resolved.Identity, Resolved.Identity)
      val same   = action.at == path
      assertTrue(same)
    },
    test("map key transformation action") {
      val path   = DynamicOptic.root.field("metadata").mapKeys
      val action = MigrationAction.TransformKeys(path, Resolved.Identity, Resolved.Identity)
      val same   = action.at == path
      assertTrue(same)
    },
    test("map value transformation action") {
      val path   = DynamicOptic.root.field("config").mapValues
      val action = MigrationAction.TransformValues(path, Resolved.Identity, Resolved.Identity)
      val same   = action.at == path
      assertTrue(same)
    }
  )

  private val enumMigrationTests = suite("Enum migrations")(
    test("rename enum case creates correct action") {
      val action = MigrationAction.RenameCase(DynamicOptic.root, "OldName", "NewName")
      val from   = action.from
      val to     = action.to
      assertTrue(from == "OldName" && to == "NewName")
    },
    test("transform enum case creates correct action") {
      val action   = MigrationAction.TransformCase(DynamicOptic.root, "SomeCase", Chunk.empty)
      val caseName = action.caseName
      assertTrue(caseName == "SomeCase")
    },
    test("rename case action is reversible") {
      val action   = MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
      val reversed = action.reverse
      val expected = MigrationAction.RenameCase(DynamicOptic.root, "B", "A")
      assertTrue(reversed == expected)
    },
    test("transform case with nested actions") {
      val nestedAction = MigrationAction.Rename(DynamicOptic.root, "x", "y")
      val action       = MigrationAction.TransformCase(DynamicOptic.root, "Case1", Chunk(nestedAction))
      val size         = action.caseActions.size
      assertTrue(size == 1)
    },
    test("transform elements is reversible") {
      val action   = MigrationAction.TransformElements(DynamicOptic.root, Resolved.Identity, Resolved.Identity)
      val reversed = action.reverse.asInstanceOf[MigrationAction.TransformElements]
      val same     = reversed.at == action.at
      assertTrue(same)
    },
    test("transform keys swaps transforms on reverse") {
      val forward       = Resolved.Literal.string("forward")
      val reverseVal    = Resolved.Literal.string("reverse")
      val action        = MigrationAction.TransformKeys(DynamicOptic.root, forward, reverseVal)
      val reversed      = action.reverse.asInstanceOf[MigrationAction.TransformKeys]
      val keyTransform  = reversed.keyTransform
      val expectedValue = reverseVal
      assertTrue(keyTransform == expectedValue)
    }
  )

  private val serializationTests = suite("Migration serialization")(
    test("single action migration is serializable") {
      val migration = DynamicMigration(
        Chunk(MigrationAction.Rename(DynamicOptic.root, "old", "new"))
      )
      val dv       = DynamicMigration.schema.toDynamicValue(migration)
      val restored = DynamicMigration.schema.fromDynamicValue(dv)
      assertTrue(restored == Right(migration))
    },
    test("multiple action migration is serializable") {
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1)),
          MigrationAction.DropField(DynamicOptic.root, "d", Resolved.Literal.string(""))
        )
      )
      val dv       = DynamicMigration.schema.toDynamicValue(migration)
      val restored = DynamicMigration.schema.fromDynamicValue(dv)
      assertTrue(restored == Right(migration))
    },
    test("MigrationAction has schema") {
      val schema = Schema.derived[MigrationAction]
      val exists = schema != null
      assertTrue(exists)
    },
    test("Resolved has schema") {
      val schema = Resolved.schema
      val exists = schema != null
      assertTrue(exists)
    },
    test("empty migration is valid") {
      val migration = DynamicMigration(Chunk.empty)
      val result    = migration.apply(DynamicValue.Record(Chunk.empty))
      assertTrue(result.isRight)
    },
    test("identity migration preserves data") {
      val data = DynamicValue.Record(
        Chunk[(String, DynamicValue)](
          "x" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
        )
      )
      val migration = DynamicMigration(Chunk.empty)
      val result    = migration.apply(data)
      assertTrue(result == Right(data))
    },
    test("complex migration actions are serializable") {
      val migration = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root, "x", "y"),
          MigrationAction.AddField(DynamicOptic.root, "z", Resolved.Literal.string("test")),
          MigrationAction.DropField(DynamicOptic.root, "w", Resolved.Literal.int(0)),
          MigrationAction.RenameCase(DynamicOptic.root, "Case1", "Case2")
        )
      )
      val dv       = DynamicMigration.schema.toDynamicValue(migration)
      val restored = DynamicMigration.schema.fromDynamicValue(dv)
      assertTrue(restored == Right(migration))
    },
    test("DynamicOptic has toString") {
      val path     = DynamicOptic.root.field("test")
      val str      = path.toString
      val notEmpty = str.nonEmpty
      assertTrue(notEmpty)
    }
  )
}
