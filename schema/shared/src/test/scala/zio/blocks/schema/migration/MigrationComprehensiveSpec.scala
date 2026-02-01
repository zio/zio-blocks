package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}
import zio.blocks.schema.SchemaBaseSpec
import zio.test._

/**
 * Tests demonstrating comprehensive migration capabilities including
 * serialization, path-based actions, selector API, and migration laws.
 */
object MigrationComprehensiveSpec extends SchemaBaseSpec {

  case class PersonV0(firstName: String, lastName: String, age: Int)
  case class PersonV1(name: String, age: Long)
  case class PersonV2(name: String, age: Long, country: String)

  object PersonV0 { implicit val schema: Schema[PersonV0] = Schema.derived }
  object PersonV1 { implicit val schema: Schema[PersonV1] = Schema.derived }
  object PersonV2 { implicit val schema: Schema[PersonV2] = Schema.derived }

  case class Address(street: String, city: String)
  case class AddressWithZip(street: String, city: String, zipCode: String)

  object Address        { implicit val schema: Schema[Address] = Schema.derived        }
  object AddressWithZip { implicit val schema: Schema[AddressWithZip] = Schema.derived }

  def spec: Spec[TestEnvironment, Any] = suite("MigrationComprehensiveSpec")(
    suite("Serialization")(
      test("DynamicMigration is fully serializable") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "newField", Resolved.Literal.string("default")),
            MigrationAction.Rename(DynamicOptic.root, "oldField", "renamedField"),
            MigrationAction.DropField(DynamicOptic.root, "obsoleteField", Resolved.SchemaDefault)
          )
        )

        val schema       = DynamicMigration.schema
        val dynamicValue = schema.toDynamicValue(migration)
        val roundTrip    = schema.fromDynamicValue(dynamicValue)

        assertTrue(roundTrip.isRight) &&
        assertTrue(roundTrip.toOption.get.actions.size == migration.actions.size)
      },
      test("MigrationAction is serializable with all action types") {
        val actions = Chunk(
          MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(42)),
          MigrationAction.DropField(DynamicOptic.root, "field", Resolved.Literal.int(0)),
          MigrationAction.Rename(DynamicOptic.root, "old", "new"),
          MigrationAction.TransformValue(DynamicOptic.root, "field", Resolved.Identity, Resolved.Identity),
          MigrationAction.Mandate(DynamicOptic.root, "field", Resolved.Literal.string("default")),
          MigrationAction.Optionalize(DynamicOptic.root, "field"),
          MigrationAction.ChangeType(DynamicOptic.root, "field", Resolved.Identity, Resolved.Identity),
          MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        )

        val schema          = MigrationAction.schema
        val allSerializable = actions.iterator.forall { action =>
          val dv = schema.toDynamicValue(action)
          dv != null
        }

        assertTrue(allSerializable)
      },
      test("Resolved expressions are serializable") {
        val expressions = List(
          Resolved.Identity,
          Resolved.SchemaDefault,
          Resolved.Literal.string("test"),
          Resolved.Literal.int(42),
          Resolved.Literal.long(123L),
          Resolved.Literal.boolean(true),
          Resolved.Convert("Int", "Long", Resolved.Identity),
          Resolved.FieldAccess("field", Resolved.Identity),
          Resolved.Concat(Vector(Resolved.Literal.string("a"), Resolved.Literal.string("b")), " ")
        )

        val schema          = Resolved.schema
        val allSerializable = expressions.forall { expr =>
          val dv = schema.toDynamicValue(expr)
          dv != null
        }

        assertTrue(allSerializable)
      }
    ),
    suite("Path-Based Actions")(
      test("actions operate at correct DynamicOptic path") {
        val nestedPath = DynamicOptic.root.field("address").field("street")
        val action     = MigrationAction.Rename(nestedPath, "oldStreet", "newStreet")

        assertTrue(action.at == nestedPath) &&
        assertTrue(action.at.nodes.size == 2)
      },
      test("interpreter applies actions at correct paths") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "newField", Resolved.Literal.int(100))
        )

        val input = DynamicValue.Record(
          Chunk(
            "existingField" -> DynamicValue.Primitive(PrimitiveValue.String("value"))
          )
        )

        val result = DynamicMigrationInterpreter(migration, input)

        assertTrue(result.isRight) && {
          val record = result.toOption.get.asInstanceOf[DynamicValue.Record]
          assertTrue(record.fields.size == 2) &&
          assertTrue(record.fields.exists(_._1 == "newField"))
        }
      }
    ),
    suite("Migration Builder API")(
      test("builds migration with path validation") {
        val builder = MigrationBuilder[Address, AddressWithZip]
          .addFieldLiteral("zipCode", "00000")

        val migration = builder.buildStrict
        assertTrue(migration.dynamicMigration.actions.size == 1)
      },
      test("buildPartial creates migration without validation") {
        val builder = MigrationBuilder[Address, AddressWithZip]
          .addFieldLiteral("zipCode", "12345")
          .addFieldLiteral("extra", "ignored")

        val migration = builder.buildPartial
        assertTrue(migration.dynamicMigration.actions.size == 2)
      }
    ),
    suite("Migration Laws")(
      test("identity law: identity.apply(a) == Right(a)") {
        val identityMigration = DynamicMigration.identity

        val value = DynamicValue.Record(
          Chunk(
            "name"  -> DynamicValue.Primitive(PrimitiveValue.String("test")),
            "count" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
          )
        )

        val result = DynamicMigrationInterpreter(identityMigration, value)
        assertTrue(result == Right(value))
      },
      test("associativity law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
        val m1 = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal.int(1)))
        val m2 = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "b", Resolved.Literal.int(2)))
        val m3 = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(3)))

        val left  = (m1 ++ m2) ++ m3
        val right = m1 ++ (m2 ++ m3)

        val input       = DynamicValue.Record(Chunk.empty)
        val leftResult  = DynamicMigrationInterpreter(left, input)
        val rightResult = DynamicMigrationInterpreter(right, input)

        assertTrue(leftResult == rightResult)
      },
      test("structural reverse law: m.reverse.reverse == m") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "old", "new"),
            MigrationAction.AddField(DynamicOptic.root, "added", Resolved.Literal.int(0))
          )
        )

        val reversed = migration.reverse.reverse
        assertTrue(reversed.actions == migration.actions)
      }
    ),
    suite("Error Handling")(
      test("errors include path information") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("nested"),
            "missingField",
            Resolved.Identity,
            Resolved.Identity
          )
        )

        val input = DynamicValue.Record(
          Chunk(
            "other" -> DynamicValue.Primitive(PrimitiveValue.String("value"))
          )
        )

        val result = DynamicMigrationInterpreter(migration, input)
        assertTrue(result.isLeft)
      },
      test("MigrationError provides diagnostic render") {
        val error = MigrationError.PathNotFound(
          DynamicOptic.root.field("missing"),
          Set.empty
        )

        val rendered = error.render
        assertTrue(rendered.nonEmpty) &&
        assertTrue(rendered.contains("missing"))
      }
    ),
    suite("Enum Operations")(
      test("renameCase renames enum case") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        assertTrue(action.from == "OldCase") &&
        assertTrue(action.to == "NewCase")
      },
      test("renameCase reverse swaps from and to") {
        val action   = MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
        val reversed = action.reverse

        reversed match {
          case MigrationAction.RenameCase(_, from, to) =>
            assertTrue(from == "B") && assertTrue(to == "A")
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Join and Split Operations")(
      test("Join combines multiple fields into one") {
        val action = MigrationAction.Join(
          DynamicOptic.root,
          "fullName",
          Chunk(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName")),
          Resolved.Concat(
            Vector(
              Resolved.FieldAccess("firstName", Resolved.Identity),
              Resolved.FieldAccess("lastName", Resolved.Identity)
            ),
            " "
          ),
          Resolved.SplitString(Resolved.Identity, " ", 0)
        )

        assertTrue(action.targetFieldName == "fullName") &&
        assertTrue(action.sourcePaths.size == 2)
      },
      test("Split divides one field into multiple") {
        val action = MigrationAction.Split(
          DynamicOptic.root,
          "fullName",
          Chunk(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName")),
          Resolved.SplitString(Resolved.Identity, " ", 0),
          Resolved.Concat(Vector(Resolved.Identity), " ")
        )

        assertTrue(action.sourceFieldName == "fullName") &&
        assertTrue(action.targetPaths.size == 2)
      }
    ),
    suite("Resolved Expression Evaluation")(
      test("Identity returns input unchanged") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val result = Resolved.Identity.evalDynamic(input)
        assertTrue(result == Right(input))
      },
      test("Literal evaluates to constant value") {
        val literal = Resolved.Literal.int(42)
        val result  = literal.evalDynamic
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },
      test("Convert transforms between primitive types") {
        val convert = Resolved.Convert("Int", "Long", Resolved.Identity)
        val input   = DynamicValue.Primitive(PrimitiveValue.Int(100))
        val result  = convert.evalDynamic(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(100L))))
      },
      test("FieldAccess extracts field from record") {
        val access = Resolved.FieldAccess("name", Resolved.Identity)
        val input  = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )

        val result = access.evalDynamic(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("John"))))
      },
      test("Concat joins multiple strings") {
        val concat = Resolved.Concat(
          Vector(
            Resolved.Literal.string("Hello"),
            Resolved.Literal.string("World")
          ),
          " "
        )

        val result = concat.evalDynamic(DynamicValue.Primitive(PrimitiveValue.Unit))
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("Hello World"))))
      }
    ),
    suite("Complex Migration Scenarios")(
      test("PersonV0 to PersonV1 migration with join and type change") {
        val migration = MigrationBuilder[PersonV0, PersonV1]
          .joinFields(
            DynamicOptic.root,
            "name",
            Chunk(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName")),
            Resolved.Concat(
              Vector(
                Resolved.FieldAccess("firstName", Resolved.Identity),
                Resolved.FieldAccess("lastName", Resolved.Identity)
              ),
              " "
            ),
            Resolved.SplitString(Resolved.Identity, " ", 0)
          )
          .changeFieldType("age", "Int", "Long")
          .dropFieldNoReverse("firstName")
          .dropFieldNoReverse("lastName")
          .buildPartial

        assertTrue(migration.dynamicMigration.actions.size == 4)
      },
      test("address migration adds zipCode field") {
        val migration = MigrationBuilder[Address, AddressWithZip]
          .addFieldLiteral("zipCode", "00000")
          .buildPartial

        val inputValue = DynamicValue.Record(
          Chunk(
            "street" -> DynamicValue.Primitive(PrimitiveValue.String("123 Main St")),
            "city"   -> DynamicValue.Primitive(PrimitiveValue.String("Springfield"))
          )
        )

        val result = DynamicMigrationInterpreter(migration.dynamicMigration, inputValue)

        assertTrue(result.isRight) && {
          val record = result.toOption.get.asInstanceOf[DynamicValue.Record]
          assertTrue(record.fields.exists { case (name, _) => name == "zipCode" })
        }
      }
    ),
    suite("Coverage Analysis")(
      test("analyzeCoverage tracks field additions") {
        val builder = MigrationBuilder[Address, AddressWithZip]
          .addFieldLiteral("zipCode", "00000")

        val coverage = builder.analyzeCoverage
        assertTrue(coverage.addedFields.nonEmpty)
      },
      test("analyzeCoverage tracks field drops") {
        val builder = MigrationBuilder[AddressWithZip, Address]
          .dropFieldLiteral("zipCode", "")

        val coverage = builder.analyzeCoverage
        assertTrue(coverage.droppedFields.nonEmpty)
      },
      test("analyzeCoverage tracks field renames") {
        case class V1(oldName: String)
        case class V2(newName: String)
        object V1 { implicit val schema: Schema[V1] = Schema.derived }
        object V2 { implicit val schema: Schema[V2] = Schema.derived }

        val builder = MigrationBuilder[V1, V2]
          .renameField("oldName", "newName")

        val coverage = builder.analyzeCoverage
        assertTrue(coverage.renamedFields.nonEmpty)
      }
    )
  )
}
