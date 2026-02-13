package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object JoinSplitFieldSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("JoinFields and SplitField")(
    suite("JoinFields")(
      test("JoinFields combines multiple fields into one using StringAppend") {
        val original = DynamicValue.Record(
          "prefix" -> DynamicValue.string("Hello"),
          "suffix" -> DynamicValue.string("World"),
          "age"    -> DynamicValue.int(30)
        )

        val migration = DynamicMigration.record(
          _.joinFields(
            "combined",
            Vector("prefix", "suffix"),
            DynamicValueTransform.identity,
            DynamicValueTransform.identity
          )
        )
        val result = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(
              fieldMap.contains("combined"),
              fieldMap.contains("age"),
              !fieldMap.contains("prefix"),
              !fieldMap.contains("suffix")
            )
          case _ => assertTrue(false)
        }
      },
      test("JoinFields fails when source field not found") {
        val original = DynamicValue.Record(
          "firstName" -> DynamicValue.string("John"),
          "age"       -> DynamicValue.int(30)
        )

        val migration = DynamicMigration.record(
          _.joinFields(
            "fullName",
            Vector("firstName", "lastName"),
            DynamicValueTransform.identity,
            DynamicValueTransform.identity
          )
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(_, "lastName")) => assertTrue(true)
          case _                                                 => assertTrue(false)
        }
      },
      test("JoinFields fails when target field already exists") {
        val original = DynamicValue.Record(
          "firstName" -> DynamicValue.string("John"),
          "lastName"  -> DynamicValue.string("Doe"),
          "fullName"  -> DynamicValue.string("Existing")
        )

        val migration = DynamicMigration.record(
          _.joinFields(
            "fullName",
            Vector("firstName", "lastName"),
            DynamicValueTransform.identity,
            DynamicValueTransform.identity
          )
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.FieldAlreadyExists(_, "fullName")) => assertTrue(true)
          case _                                                      => assertTrue(false)
        }
      }
    ),
    suite("SplitField")(
      test("SplitField splits one field into multiple") {
        val original = DynamicValue.Record(
          "source" -> DynamicValue.Record(
            "first"  -> DynamicValue.string("Hello"),
            "second" -> DynamicValue.string("World")
          ),
          "age" -> DynamicValue.int(30)
        )

        val migration = DynamicMigration.record(
          _.splitField(
            "source",
            Vector("first", "second"),
            DynamicValueTransform.identity,
            DynamicValueTransform.identity
          )
        )
        val result = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(
              fieldMap.contains("first"),
              fieldMap.contains("second"),
              fieldMap.contains("age"),
              !fieldMap.contains("source")
            )
          case _ => assertTrue(false)
        }
      },
      test("SplitField fails when source field not found") {
        val original = DynamicValue.Record(
          "age" -> DynamicValue.int(30)
        )

        val migration = DynamicMigration.record(
          _.splitField("fullName", Vector("a", "b"), DynamicValueTransform.identity, DynamicValueTransform.identity)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(_, "fullName")) => assertTrue(true)
          case _                                                 => assertTrue(false)
        }
      },
      test("SplitField fails when target field already exists") {
        val original = DynamicValue.Record(
          "fullName"  -> DynamicValue.string("John Doe"),
          "firstName" -> DynamicValue.string("Existing")
        )

        val splitter = DynamicValueTransform.stringSplitToFields(Vector("firstName", "lastName"), " ", 2)

        val migration = DynamicMigration.record(
          _.splitField("fullName", Vector("firstName", "lastName"), splitter, DynamicValueTransform.identity)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.FieldAlreadyExists(_, "firstName")) => assertTrue(true)
          case _                                                       => assertTrue(false)
        }
      }
    ),
    suite("Reversibility")(
      test("JoinFields and SplitField are reversible") {
        val original = DynamicValue.Record(
          "firstName" -> DynamicValue.string("Jane"),
          "lastName"  -> DynamicValue.string("Smith"),
          "age"       -> DynamicValue.int(25)
        )

        val combiner = DynamicValueTransform.stringJoinFields(Vector("firstName", "lastName"), " ")
        val splitter = DynamicValueTransform.stringSplitToFields(Vector("firstName", "lastName"), " ", 2)

        val migration = DynamicMigration.record(
          _.joinFields("fullName", Vector("firstName", "lastName"), combiner, splitter)
        )
        val migrated = migration(original)
        val reversed = migration.reverse(migrated.toOption.get)

        reversed match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap    = fields.toVector.toMap
            val originalMap = original.fields.toVector.toMap
            assertTrue(
              fieldMap == originalMap
            )
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationBuilder with JoinFields/SplitField")(
      test("joinFields via builder works correctly") {
        val combiner = DynamicValueTransform.stringJoinFields(Vector("firstName", "lastName"), " ")
        val splitter = DynamicValueTransform.stringSplitToFields(Vector("firstName", "lastName"), " ", 2)

        val builder = MigrationBuilder[PersonWithSplitName, PersonWithFullName]
          .joinFields("fullName", Vector("firstName", "lastName"), combiner, splitter)
        val dynamic = builder.toDynamicMigration

        val original = DynamicValue.Record(
          "firstName" -> DynamicValue.string("Alice"),
          "lastName"  -> DynamicValue.string("Wonder"),
          "age"       -> DynamicValue.int(28)
        )
        val result = dynamic(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(
              fieldMap.get("fullName") == Some(DynamicValue.string("Alice Wonder")),
              fieldMap.get("age") == Some(DynamicValue.int(28)),
              !fieldMap.contains("firstName"),
              !fieldMap.contains("lastName")
            )
          case _ => assertTrue(false)
        }
      },
      test("splitField via builder works correctly") {
        val splitter = DynamicValueTransform.stringSplitToFields(Vector("firstName", "lastName"), " ", 2)
        val combiner = DynamicValueTransform.stringJoinFields(Vector("firstName", "lastName"))

        val builder = MigrationBuilder[PersonWithFullName, PersonWithSplitName]
          .splitField("fullName", Vector("firstName", "lastName"), splitter, combiner)
        val dynamic = builder.toDynamicMigration

        val original = DynamicValue.Record(
          "fullName" -> DynamicValue.string("Bob Builder"),
          "age"      -> DynamicValue.int(35)
        )
        val result = dynamic(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(
              fieldMap.get("firstName") == Some(DynamicValue.string("Bob")),
              fieldMap.get("lastName") == Some(DynamicValue.string("Builder")),
              fieldMap.get("age") == Some(DynamicValue.int(35)),
              !fieldMap.contains("fullName")
            )
          case _ => assertTrue(false)
        }
      }
    ),
    suite("SplitField error cases")(
      test("SplitField fails when splitter returns non-Record") {
        val nonRecordSplitter = DynamicValueTransform.constant(DynamicValue.string("not a record"))

        val original = DynamicValue.Record(
          "source" -> DynamicValue.string("value")
        )
        val migration = DynamicMigration.record(
          _.splitField("source", Vector("a", "b"), nonRecordSplitter, DynamicValueTransform.identity)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.TransformFailed(_, msg)) =>
            assertTrue(msg.contains("Record"))
          case _ => assertTrue(false)
        }
      },
      test("SplitField fails when splitter returns Record without all target fields") {
        val incompleteSplitter = DynamicValueTransform.constant(
          DynamicValue.Record("a" -> DynamicValue.string("only a"))
        )

        val original = DynamicValue.Record(
          "source" -> DynamicValue.string("value")
        )
        val migration = DynamicMigration.record(
          _.splitField("source", Vector("a", "b", "c"), incompleteSplitter, DynamicValueTransform.identity)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.TransformFailed(_, msg)) =>
            assertTrue(msg.contains("all target fields"))
          case _ => assertTrue(false)
        }
      },
      test("SplitField fails when splitter returns Left") {
        val failingSplitter = DynamicValueTransform.numericAdd(1)
        val original        = DynamicValue.Record(
          "source" -> DynamicValue.string("value")
        )
        val migration = DynamicMigration.record(
          _.splitField("source", Vector("a", "b"), failingSplitter, DynamicValueTransform.identity)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.TransformFailed(_, _)) => assertTrue(true)
          case _                                          => assertTrue(false)
        }
      }
    ),
    suite("JoinFields combiner error")(
      test("JoinFields fails when combiner returns Left") {
        val original = DynamicValue.Record(
          "a" -> DynamicValue.int(1),
          "b" -> DynamicValue.int(2)
        )
        val failingCombiner = DynamicValueTransform.stringJoinFields(Vector("a", "b"), "-")
        val migration       = DynamicMigration.record(
          _.joinFields("combined", Vector("a", "b"), failingCombiner, DynamicValueTransform.identity)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.TransformFailed(_, _)) => assertTrue(true)
          case _                                          => assertTrue(false)
        }
      }
    )
  )
}
