package zio.blocks.schema.migration

import zio.Scope
import zio.blocks.schema.*
import zio.test.*

object MigrationBuilderMacroSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment & Scope, Any] = suite("MigrationBuilder Macro API")(
    suite("Selector-based field operations")(
      test("add using selector extracts field path") {
        val builder = MigrationBuilder[PersonV1, PersonV2]
          .add(_.age, DynamicValue.int(0))
        val migration = builder.build
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonV2("Alice", 0)))
      },
      test("drop using selector extracts field path") {
        val builder = MigrationBuilder[PersonV2, PersonV1]
          .drop(_.age, DynamicValue.int(0))
        val migration = builder.build
        val original  = PersonV2("Alice", 30)
        val result    = migration(original)

        assertTrue(result == Right(PersonV1("Alice")))
      },
      test("rename using selectors extracts both paths") {
        val builder = MigrationBuilder[PersonV1, PersonRenamed]
          .rename(_.name, _.fullName)
        val migration = builder.build
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonRenamed("Alice")))
      },
      test("changeType using selector extracts field path") {
        val builder = MigrationBuilder[PersonWithIntId, PersonWithLongId]
          .changeType(_.id, PrimitiveConversion.IntToLong, PrimitiveConversion.LongToInt)
        val migration = builder.build
        val original  = PersonWithIntId("Alice", 42)
        val result    = migration(original)

        assertTrue(result == Right(PersonWithLongId("Alice", 42L)))
      },
      test("nested selector path works for deeply nested fields") {
        val builder = MigrationBuilder[NestedV1, NestedV2]
          .add(_.person.contact.phone, DynamicValue.string("555-0000"))
        val migration = builder.build
        val original  = NestedV1(PersonWithContact("Alice", Contact("alice@example.com")))
        val result    = migration(original)

        assertTrue(result == Right(NestedV2(PersonWithContactV2("Alice", ContactV2("alice@example.com", "555-0000")))))
      },
      test("multiple selector operations chain correctly") {
        val builder = MigrationBuilder[PersonV1, PersonV3]
          .add(_.age, DynamicValue.int(0))
          .add(_.active, DynamicValue.boolean(true))
        val migration = builder.build
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonV3("Alice", 0, true)))
      },
      test("transform using selector applies bidirectional transformation") {
        val builder = MigrationBuilder[PersonWithCount, PersonWithCount]
          .transform(
            _.count,
            DynamicValueTransform.numericMultiply(2),
            DynamicValueTransform.numericMultiply(0.5)
          )
        val migration = builder.build
        val original  = PersonWithCount("Alice", 10)
        val result    = migration(original)

        assertTrue(result == Right(PersonWithCount("Alice", 20)))
      }
    ),
    suite("Macro compile-time extraction")(
      test("fieldPath macro extracts simple field name") {
        val path = MigrationBuilderMacros.fieldPath[PersonV1, String](_.name)
        assertTrue(path == "name")
      },
      test("fieldPath macro extracts nested field path") {
        val path = MigrationBuilderMacros.fieldPath[NestedV1, String](_.person.name)
        assertTrue(path == "person.name")
      },
      test("fieldPath macro extracts deeply nested field path") {
        val path = MigrationBuilderMacros.fieldPath[NestedV1, String](_.person.contact.email)
        assertTrue(path == "person.contact.email")
      },
      test("lastFieldName macro extracts terminal field name") {
        val name = MigrationBuilderMacros.lastFieldName[NestedV1, String](_.person.contact.email)
        assertTrue(name == "email")
      }
    ),
    suite("Mixed API usage")(
      test("can mix selector-based and string-based operations") {
        val builder = MigrationBuilder[PersonV1, PersonV3]
          .add(_.age, DynamicValue.int(25))
          .addField("active", DynamicValue.boolean(false))
        val migration = builder.build
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonV3("Alice", 25, false)))
      }
    ),
    suite("MigrationExpr-based operations")(
      test("addExpr using literal default") {
        val builder = MigrationBuilder[PersonV1, PersonV2]
          .addExpr(_.age, MigrationExpr.literal(42))
        val migration = builder.build
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonV2("Alice", 42)))
      },
      test("toDynamicOptic macro extracts simple field path") {
        val optic = MigrationBuilderMacros.toDynamicOptic[PersonV1, String](_.name)
        assertTrue(
          optic.nodes.size == 1 &&
            optic.nodes.head == DynamicOptic.Node.Field("name")
        )
      },
      test("toDynamicOptic macro extracts nested field path") {
        val optic = MigrationBuilderMacros.toDynamicOptic[NestedV1, String](_.person.contact.email)
        assertTrue(
          optic.nodes.size == 3 &&
            optic.nodes(0) == DynamicOptic.Node.Field("person") &&
            optic.nodes(1) == DynamicOptic.Node.Field("contact") &&
            optic.nodes(2) == DynamicOptic.Node.Field("email")
        )
      }
    ),
    suite("Field name extraction macros")(
      test("extractFieldNames extracts all field names from a case class") {
        val fields = MigrationBuilderMacros.extractFieldNames[PersonV2]
        assertTrue(
          fields.contains("name") &&
            fields.contains("age") &&
            fields.size == 2
        )
      },
      test("extractFieldNames works with multiple fields") {
        val fields = MigrationBuilderMacros.extractFieldNames[PersonV3]
        assertTrue(
          fields.contains("name") &&
            fields.contains("age") &&
            fields.contains("active") &&
            fields.size == 3
        )
      }
    ),
    suite("Migration completeness validation")(
      test("validation passes when migration is complete") {
        val sourceFields  = Set("name")
        val targetFields  = Set("name", "age")
        val addedFields   = Set("age")
        val removedFields = Set.empty[String]
        val renamedFields = Map.empty[String, String]

        MigrationBuilderMacros.validateMigrationCompleteness[PersonV1, PersonV2](
          sourceFields,
          targetFields,
          addedFields,
          removedFields,
          renamedFields
        )
        assertTrue(true)
      },
      test("validation passes with rename") {
        val sourceFields  = Set("name")
        val targetFields  = Set("fullName")
        val addedFields   = Set.empty[String]
        val removedFields = Set.empty[String]
        val renamedFields = Map("name" -> "fullName")

        MigrationBuilderMacros.validateMigrationCompleteness[PersonV1, PersonRenamed](
          sourceFields,
          targetFields,
          addedFields,
          removedFields,
          renamedFields
        )
        assertTrue(true)
      },
      test("validation fails when migration is incomplete") {
        val sourceFields  = Set("name")
        val targetFields  = Set("name", "age")
        val addedFields   = Set.empty[String]
        val removedFields = Set.empty[String]
        val renamedFields = Map.empty[String, String]

        val result = scala.util.Try {
          MigrationBuilderMacros.validateMigrationCompleteness[PersonV1, PersonV2](
            sourceFields,
            targetFields,
            addedFields,
            removedFields,
            renamedFields
          )
        }
        assertTrue(result.isFailure && result.failed.get.getMessage.contains("Missing fields"))
      }
    ),
    suite("buildValidated extension method")(
      test("buildValidated succeeds for complete migration") {
        val builder = MigrationBuilder[PersonV1, PersonV2]
          .add(_.age, DynamicValue.int(0))
        val migration = builder.buildValidated
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonV2("Alice", 0)))
      },
      test("buildValidated succeeds with rename") {
        val builder = MigrationBuilder[PersonV1, PersonRenamed]
          .rename(_.name, _.fullName)
        val migration = builder.buildValidated
        val original  = PersonV1("Alice")
        val result    = migration(original)

        assertTrue(result == Right(PersonRenamed("Alice")))
      },
      test("buildValidated succeeds with add and drop") {
        val builder = MigrationBuilder[PersonV2, PersonRenamed]
          .drop(_.age, DynamicValue.int(0))
          .rename(_.name, _.fullName)
        val migration = builder.buildValidated
        val original  = PersonV2("Alice", 30)
        val result    = migration(original)

        assertTrue(result == Right(PersonRenamed("Alice")))
      },
      test("buildValidated tracks addedFieldNames correctly") {
        val builder = MigrationBuilder[PersonV1, PersonV2]
          .add(_.age, DynamicValue.int(0))

        assertTrue(builder.addedFieldNames == Set("age"))
      },
      test("buildValidated tracks removedFieldNames correctly") {
        val builder = MigrationBuilder[PersonV2, PersonV1]
          .drop(_.age, DynamicValue.int(0))

        assertTrue(builder.removedFieldNames == Set("age"))
      },
      test("buildValidated tracks renamedFromNames and renamedToNames correctly") {
        val builder = MigrationBuilder[PersonV1, PersonRenamed]
          .rename(_.name, _.fullName)

        assertTrue(
          builder.renamedFromNames == Set("name") &&
            builder.renamedToNames == Set("fullName")
        )
      },
      test("buildValidated handles joinFields tracking correctly") {
        val combiner = DynamicValueTransform.stringJoinFields(Vector("firstName", "lastName"))
        val splitter = DynamicValueTransform.stringSplitToFields(Vector("firstName", "lastName"), " ", 2)

        val builder = MigrationBuilder[PersonWithSplitName, PersonWithFullName]
          .joinFields("fullName", Vector("firstName", "lastName"), combiner, splitter)
          .drop(_.age, DynamicValue.int(0))

        assertTrue(
          builder.addedFieldNames == Set("fullName"),
          builder.removedFieldNames == Set("firstName", "lastName", "age")
        )
      },
      test("buildValidated handles splitField tracking correctly") {
        val splitter = DynamicValueTransform.stringSplitToFields(Vector("firstName", "lastName"), " ", 2)
        val combiner = DynamicValueTransform.stringJoinFields(Vector("firstName", "lastName"))

        val builder = MigrationBuilder[PersonWithFullName, PersonWithSplitName]
          .splitField("fullName", Vector("firstName", "lastName"), splitter, combiner)
          .add(_.age, DynamicValue.int(0))

        assertTrue(
          builder.addedFieldNames == Set("firstName", "lastName", "age"),
          builder.removedFieldNames == Set("fullName")
        )
      }
    ),
    suite("buildChecked compile-time validation")(
      test("buildChecked succeeds with correct literal field sets") {
        val builder = MigrationBuilder[PersonV1, PersonV2]
          .add(_.age, DynamicValue.int(0))
        val migration = builder.buildChecked(
          added = Set("age"),
          removed = Set.empty
        )
        val original = PersonV1("Alice")
        val result   = migration(original)

        assertTrue(result == Right(PersonV2("Alice", 0)))
      },
      test("buildChecked succeeds with rename operations") {
        val builder = MigrationBuilder[PersonV1, PersonRenamed]
          .rename(_.name, _.fullName)
        val migration = builder.buildChecked(
          added = Set.empty,
          removed = Set.empty,
          renamedFrom = Set("name"),
          renamedTo = Set("fullName")
        )
        val original = PersonV1("Alice")
        val result   = migration(original)

        assertTrue(result == Right(PersonRenamed("Alice")))
      },
      test("buildChecked succeeds with add and drop") {
        val builder = MigrationBuilder[PersonV2, PersonRenamed]
          .drop(_.age, DynamicValue.int(0))
          .rename(_.name, _.fullName)
        val migration = builder.buildChecked(
          added = Set.empty,
          removed = Set("age"),
          renamedFrom = Set("name"),
          renamedTo = Set("fullName")
        )
        val original = PersonV2("Alice", 30)
        val result   = migration(original)

        assertTrue(result == Right(PersonRenamed("Alice")))
      },
      test("validateCompileTime passes with correct operations") {
        MigrationBuilderMacros.validateCompileTime[PersonV1, PersonV2](
          added = Set("age"),
          removed = Set.empty,
          renamedFrom = Set.empty,
          renamedTo = Set.empty
        )
        assertTrue(true)
      },
      test("validateCompileTime passes with rename") {
        MigrationBuilderMacros.validateCompileTime[PersonV1, PersonRenamed](
          added = Set.empty,
          removed = Set.empty,
          renamedFrom = Set("name"),
          renamedTo = Set("fullName")
        )
        assertTrue(true)
      },
      test("validateCompileTime passes with complex migration") {
        MigrationBuilderMacros.validateCompileTime[PersonV2, PersonRenamed](
          added = Set.empty,
          removed = Set("age"),
          renamedFrom = Set("name"),
          renamedTo = Set("fullName")
        )
        assertTrue(true)
      }
    )
  )
}

case class PersonWithCount(name: String, count: Int)
object PersonWithCount {
  implicit val schema: Schema[PersonWithCount] = Schema.derived
}
