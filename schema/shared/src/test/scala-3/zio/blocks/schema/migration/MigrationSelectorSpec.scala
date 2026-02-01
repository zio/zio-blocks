package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}
import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.chunk.Chunk
import zio.test._

/**
 * Tests for Scala 3 selector-based migration API.
 */
object MigrationSelectorSpec extends SchemaBaseSpec {

  case class PersonV0(firstName: String, lastName: String, age: Int)
  object PersonV0 {
    implicit val schema: Schema[PersonV0] = Schema.derived
  }

  case class Person(name: String, age: Long)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class AddressV1(street: String, city: String)
  object AddressV1 {
    implicit val schema: Schema[AddressV1] = Schema.derived
  }

  case class AddressV2(street: String, city: String, zipCode: String)
  object AddressV2 {
    implicit val schema: Schema[AddressV2] = Schema.derived
  }

  case class OptionalFieldsV1(name: String, email: Option[String])
  object OptionalFieldsV1 {
    implicit val schema: Schema[OptionalFieldsV1] = Schema.derived
  }

  case class OptionalFieldsV2(name: String, email: String)
  object OptionalFieldsV2 {
    implicit val schema: Schema[OptionalFieldsV2] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSelectorSpec")(
    suite("Selector API")(
      test("PersonV0 -> Person migration with join and type change") {
        val builder = MigrationBuilder[PersonV0, Person]
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

        val migration = builder.buildPartial
        assertTrue(migration.dynamicMigration.actions.size == 4)
      },
      test("addField with selector") {
        import MigrationBuilderSyntax._

        val builder = MigrationBuilder[AddressV1, AddressV2]
          .addField(_.zipCode, "00000")

        val migration = builder.buildPartial
        assertTrue(migration.dynamicMigration.actions.size == 1) &&
        assertTrue(migration.dynamicMigration.actions.head match {
          case MigrationAction.AddField(_, "zipCode", Resolved.Literal(dv)) =>
            dv == DynamicValue.Primitive(PrimitiveValue.String("00000"))
          case _ => false
        })
      },
      test("dropField with selector and default") {
        import MigrationBuilderSyntax._

        val builder = MigrationBuilder[AddressV2, AddressV1]
          .dropField(_.zipCode, "00000")

        val migration = builder.buildPartial
        assertTrue(migration.dynamicMigration.actions.size == 1) &&
        assertTrue(migration.dynamicMigration.actions.head match {
          case MigrationAction.DropField(_, "zipCode", _) => true
          case _                                          => false
        })
      },
      test("dropField without default uses SchemaDefault") {
        import MigrationBuilderSyntax._

        case class WithExtra(name: String, extra: String)
        case class WithoutExtra(name: String)
        object WithExtra    { implicit val schema: Schema[WithExtra] = Schema.derived    }
        object WithoutExtra { implicit val schema: Schema[WithoutExtra] = Schema.derived }

        val builder = MigrationBuilder[WithExtra, WithoutExtra]
          .dropField(_.extra)

        val migration = builder.buildPartial
        assertTrue(migration.dynamicMigration.actions.size == 1) &&
        assertTrue(migration.dynamicMigration.actions.head match {
          case MigrationAction.DropField(_, "extra", Resolved.SchemaDefault) => true
          case _                                                             => false
        })
      },
      test("renameField with dual selectors") {
        import MigrationBuilderSyntax._

        case class V1(oldName: String, data: Int)
        case class V2(newName: String, data: Int)
        object V1 { implicit val schema: Schema[V1] = Schema.derived }
        object V2 { implicit val schema: Schema[V2] = Schema.derived }

        val builder = MigrationBuilder[V1, V2]
          .renameField(_.oldName, _.newName)

        val migration = builder.buildPartial
        assertTrue(migration.dynamicMigration.actions.size == 1) &&
        assertTrue(migration.dynamicMigration.actions.head match {
          case MigrationAction.Rename(_, "oldName", "newName") => true
          case _                                               => false
        })
      },
      test("transformField with dual selectors") {
        import MigrationBuilderSyntax._

        case class V1(count: Int)
        case class V2(count: Long)
        object V1 { implicit val schema: Schema[V1] = Schema.derived }
        object V2 { implicit val schema: Schema[V2] = Schema.derived }

        val builder = MigrationBuilder[V1, V2]
          .transformField(
            _.count,
            _.count,
            Resolved.Convert("Int", "Long", Resolved.Identity),
            Resolved.Convert("Long", "Int", Resolved.Identity)
          )

        val migration = builder.buildPartial
        assertTrue(migration.dynamicMigration.actions.size == 1) &&
        assertTrue(migration.dynamicMigration.actions.head match {
          case MigrationAction.TransformValue(_, "count", _, _) => true
          case _                                                => false
        })
      },
      test("mandateField with dual selectors") {
        import MigrationBuilderSyntax._

        val builder = MigrationBuilder[OptionalFieldsV1, OptionalFieldsV2]
          .mandateField(_.email, _.email, "default@example.com")

        val migration = builder.buildPartial
        assertTrue(migration.dynamicMigration.actions.size == 1) &&
        assertTrue(migration.dynamicMigration.actions.head match {
          case MigrationAction.Mandate(_, "email", _) => true
          case _                                      => false
        })
      },
      test("optionalizeField with dual selectors") {
        import MigrationBuilderSyntax._

        val builder = MigrationBuilder[OptionalFieldsV2, OptionalFieldsV1]
          .optionalizeField(_.email, _.email)

        val migration = builder.buildPartial
        assertTrue(migration.dynamicMigration.actions.size == 1) &&
        assertTrue(migration.dynamicMigration.actions.head match {
          case MigrationAction.Optionalize(_, "email") => true
          case _                                       => false
        })
      },
      test("changeFieldType with dual selectors") {
        import MigrationBuilderSyntax._

        case class IntAge(age: Int)
        case class LongAge(age: Long)
        object IntAge  { implicit val schema: Schema[IntAge] = Schema.derived  }
        object LongAge { implicit val schema: Schema[LongAge] = Schema.derived }

        val builder = MigrationBuilder[IntAge, LongAge]
          .changeFieldType(_.age, _.age, "Int", "Long")

        val migration = builder.buildPartial
        assertTrue(migration.dynamicMigration.actions.size == 1) &&
        assertTrue(migration.dynamicMigration.actions.head match {
          case MigrationAction.ChangeType(_, "age", _, _) => true
          case _                                          => false
        })
      }
    ),
    suite("Nested Field Support")(
      test("addField with parent selector for nested fields") {
        import MigrationBuilderSyntax._

        case class Inner(value: String)
        case class OuterV1(inner: Inner)
        case class InnerV2(value: String, extra: Int)
        case class OuterV2(inner: InnerV2)

        object Inner   { implicit val schema: Schema[Inner] = Schema.derived   }
        object OuterV1 { implicit val schema: Schema[OuterV1] = Schema.derived }
        object InnerV2 { implicit val schema: Schema[InnerV2] = Schema.derived }
        object OuterV2 { implicit val schema: Schema[OuterV2] = Schema.derived }

        val builder = MigrationBuilder[OuterV1, OuterV2]
          .addFieldWithSelector(_.inner)("extra", 0)

        val migration = builder.buildPartial
        assertTrue(migration.dynamicMigration.actions.size == 1)
      }
    ),
    suite("Resolved Expressions")(
      test("SchemaDefault represents schema default") {
        val schemaDefault = Resolved.SchemaDefault
        val result        = schemaDefault.evalDynamic
        assertTrue(result.isLeft) &&
        assertTrue(result.swap.getOrElse("").contains("SchemaDefault"))
      },
      test("Literal evaluates to constant") {
        val literal = Resolved.Literal.string("test")
        val result  = literal.evalDynamic
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("test"))))
      },
      test("Identity passes through input") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = Resolved.Identity.evalDynamic(input)
        assertTrue(result == Right(input))
      },
      test("Convert changes primitive type") {
        val convert = Resolved.Convert("Int", "Long", Resolved.Identity)
        val input   = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result  = convert.evalDynamic(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(42L))))
      },
      test("FieldAccess extracts field from record") {
        val access = Resolved.FieldAccess("name", Resolved.Identity)
        val input  = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val result = access.evalDynamic(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("John"))))
      },
      test("Concat joins strings") {
        val concat = Resolved.Concat(
          Vector(Resolved.Literal.string("Hello"), Resolved.Literal.string("World")),
          " "
        )
        val result = concat.evalDynamic(DynamicValue.Primitive(PrimitiveValue.Unit))
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("Hello World"))))
      }
    ),
    suite("Serialization")(
      test("DynamicMigration has Schema") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "test", Resolved.Literal.int(0))
        )
        val schema  = DynamicMigration.schema
        val dynamic = schema.toDynamicValue(migration)
        assertTrue(dynamic != null)
      },
      test("MigrationAction has Schema") {
        val action  = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val schema  = MigrationAction.schema
        val dynamic = schema.toDynamicValue(action)
        assertTrue(dynamic != null)
      },
      test("Resolved has Schema") {
        val resolved = Resolved.Literal.string("test")
        val schema   = Resolved.schema
        val dynamic  = schema.toDynamicValue(resolved)
        assertTrue(dynamic != null)
      }
    ),
    suite("Validation")(
      test("validateShape returns result") {
        val migration = MigrationBuilder[AddressV1, AddressV2]
          .addFieldLiteral("zipCode", "00000")
          .buildPartial

        val result = SchemaShapeValidator.validateShape(migration)
        assertTrue(result != null)
      },
      test("analyzeCoverage returns MigrationCoverage") {
        val builder = MigrationBuilder[PersonV0, Person]
          .dropFieldNoReverse("firstName")
          .dropFieldNoReverse("lastName")

        val coverage = builder.analyzeCoverage
        assertTrue(coverage.droppedFields.nonEmpty)
      }
    ),
    suite("Migration Laws")(
      test("identity migration is no-op") {
        val migration = DynamicMigration.identity
        val value     = DynamicValue.Record(Chunk("test" -> DynamicValue.Primitive(PrimitiveValue.String("value"))))
        val result    = DynamicMigrationInterpreter(migration, value)
        assertTrue(result == Right(value))
      },
      test("composition is associative") {
        val m1 = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal.int(1)))
        val m2 = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "b", Resolved.Literal.int(2)))
        val m3 = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(3)))

        val input = DynamicValue.Record(Chunk.empty)

        val leftGrouped  = (m1 ++ m2) ++ m3
        val rightGrouped = m1 ++ (m2 ++ m3)

        val leftResult  = DynamicMigrationInterpreter(leftGrouped, input)
        val rightResult = DynamicMigrationInterpreter(rightGrouped, input)

        assertTrue(leftResult == rightResult)
      },
      test("reverse of reverse is identity") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "old", "new")
        )
        val reversed = migration.reverse.reverse
        assertTrue(reversed.actions == migration.actions)
      }
    ),
    suite("Edge Cases")(
      test("empty migration has no actions") {
        val migration = DynamicMigration.identity
        assertTrue(migration.isEmpty) &&
        assertTrue(migration.size == 0)
      },
      test("single action migration size") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "test", Resolved.Literal.int(0))
        )
        assertTrue(migration.size == 1)
      },
      test("composed migrations sum actions") {
        val m1       = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal.int(1)))
        val m2       = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "b", Resolved.Literal.int(2)))
        val composed = m1 ++ m2
        assertTrue(composed.size == 2)
      }
    )
  )
}
