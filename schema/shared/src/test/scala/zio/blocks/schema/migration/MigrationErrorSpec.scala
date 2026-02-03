package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationErrorSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Migration Errors")(
    errorMessageSuite,
    schemaErrorMessageSuite,
    basicErrorHandlingSuite,
    makeRequiredErrorSuite,
    makeOptionalErrorSuite,
    changeTypeErrorSuite,
    transformErrorSuite,
    additionalErrorTypesSuite,
    conversionErrorSuite,
    renameFieldEdgeCasesSuite,
    errorPropagationSuite,
    sequenceTransformErrorSuite
  )

  private val errorMessageSuite = suite("MigrationError message methods")(
    test("FieldNotFound.message includes field name and path") {
      val err = MigrationError.FieldNotFound(DynamicOptic.root.field("parent"), "missingField")
      val msg = err.message
      assertTrue(
        msg.contains("missingField"),
        msg.contains("parent")
      )
    },
    test("CaseNotFound.message includes case name and path") {
      val err = MigrationError.CaseNotFound(DynamicOptic.root, "UnknownCase")
      val msg = err.message
      assertTrue(msg.contains("UnknownCase"))
    },
    test("TypeMismatch.message includes expected and actual types") {
      val err = MigrationError.TypeMismatch(DynamicOptic.root, "Record", "Variant")
      val msg = err.message
      assertTrue(
        msg.contains("Record"),
        msg.contains("Variant"),
        msg.contains("expected")
      )
    },
    test("InvalidIndex.message includes index and size") {
      val err = MigrationError.InvalidIndex(DynamicOptic.root, 5, 3)
      val msg = err.message
      assertTrue(
        msg.contains("5"),
        msg.contains("3"),
        msg.contains("out of bounds")
      )
    },
    test("TransformFailed.message includes reason") {
      val err = MigrationError.TransformFailed(DynamicOptic.root, "conversion failed")
      val msg = err.message
      assertTrue(msg.contains("conversion failed"))
    },
    test("ExpressionEvalFailed.message includes reason") {
      val err = MigrationError.ExpressionEvalFailed(DynamicOptic.root, "eval error")
      val msg = err.message
      assertTrue(msg.contains("eval error"))
    },
    test("FieldAlreadyExists.message includes field name") {
      val err = MigrationError.FieldAlreadyExists(DynamicOptic.root, "duplicateField")
      val msg = err.message
      assertTrue(msg.contains("duplicateField"))
    },
    test("IncompatibleValue.message includes reason") {
      val err = MigrationError.IncompatibleValue(DynamicOptic.root, "wrong type")
      val msg = err.message
      assertTrue(msg.contains("wrong type"))
    },
    test("DuplicateMapKey.message includes path") {
      val err = MigrationError.DuplicateMapKey(DynamicOptic.root.field("mapField"))
      val msg = err.message
      assertTrue(msg.contains("duplicate"))
    }
  )

  private val schemaErrorMessageSuite = suite("SchemaError message coverage")(
    test("ConversionFailed with no cause") {
      val err = SchemaError.ConversionFailed(DynamicOptic.root, "Test error")
      assertTrue(err.message == "Test error")
    },
    test("ConversionFailed with single cause error") {
      val innerErr = SchemaError.ExpectationMismatch(DynamicOptic.root, "inner error")
      val cause    = new SchemaError(::(innerErr, Nil))
      val err      = SchemaError.ConversionFailed(DynamicOptic.root, "Outer error", Some(cause))
      assertTrue(
        err.message.contains("Outer error"),
        err.message.contains("Caused by"),
        err.message.contains("inner error")
      )
    },
    test("ConversionFailed with multiple cause errors") {
      val err1  = SchemaError.ExpectationMismatch(DynamicOptic.root, "error 1")
      val err2  = SchemaError.ExpectationMismatch(DynamicOptic.root, "error 2")
      val cause = new SchemaError(::(err1, List(err2)))
      val err   = SchemaError.ConversionFailed(DynamicOptic.root, "Outer error", Some(cause))
      assertTrue(
        err.message.contains("Outer error"),
        err.message.contains("Caused by"),
        err.message.contains("error 1"),
        err.message.contains("error 2")
      )
    }
  )

  private val basicErrorHandlingSuite = suite("Basic error handling")(
    test("Returns FieldNotFound when field does not exist") {
      val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
      val migration = DynamicMigration.record(_.removeField("age", DynamicValue.int(0)))
      val result    = migration(original)

      result match {
        case Left(MigrationError.FieldNotFound(_, _)) => assertTrue(true)
        case _                                        => assertTrue(false)
      }
    },
    test("Returns FieldAlreadyExists when adding duplicate field") {
      val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
      val migration = DynamicMigration.record(_.addField("name", DynamicValue.string("Bob")))
      val result    = migration(original)

      result match {
        case Left(MigrationError.FieldAlreadyExists(_, _)) => assertTrue(true)
        case _                                             => assertTrue(false)
      }
    },
    test("Returns TypeMismatch when applying record migration to non-record") {
      val original  = DynamicValue.int(42)
      val migration = DynamicMigration.record(_.addField("name", DynamicValue.string("Alice")))
      val result    = migration(original)

      result match {
        case Left(MigrationError.TypeMismatch(_, _, _)) => assertTrue(true)
        case _                                          => assertTrue(false)
      }
    }
  )

  private val makeRequiredErrorSuite = suite("MakeRequired error cases")(
    test("MakeRequired fails for non-Option value") {
      val original = DynamicValue.Record(
        "name" -> DynamicValue.string("plain string, not an Option")
      )
      val migration = DynamicMigration.record(_.makeFieldRequired("name", DynamicValue.string("")))
      val result    = migration(original)

      result match {
        case Left(MigrationError.TransformFailed(_, _)) => assertTrue(true)
        case _                                          => assertTrue(false)
      }
    },
    test("MakeRequired fails when field does not exist") {
      val original  = DynamicValue.Record("other" -> DynamicValue.int(1))
      val migration = DynamicMigration.record(_.makeFieldRequired("nonexistent", DynamicValue.string("")))
      val result    = migration(original)

      result match {
        case Left(MigrationError.FieldNotFound(_, _)) => assertTrue(true)
        case _                                        => assertTrue(false)
      }
    },
    test("MakeRequired handles DynamicValue.Null") {
      val original = DynamicValue.Record(
        "field" -> DynamicValue.Null
      )
      val migration = DynamicMigration.record(
        _.makeFieldRequired("field", DynamicValue.string("default"))
      )
      val result = migration(original)

      result match {
        case Right(DynamicValue.Record(fields)) =>
          val fieldMap = fields.toVector.toMap
          assertTrue(fieldMap("field") == DynamicValue.string("default"))
        case _ => assertTrue(false)
      }
    }
  )

  private val makeOptionalErrorSuite = suite("MakeOptional error cases")(
    test("MakeOptional fails when field does not exist") {
      val original  = DynamicValue.Record("other" -> DynamicValue.int(1))
      val migration = DynamicMigration.record(_.makeFieldOptional("nonexistent", DynamicValue.Null))
      val result    = migration(original)

      result match {
        case Left(MigrationError.FieldNotFound(_, _)) => assertTrue(true)
        case _                                        => assertTrue(false)
      }
    },
    test("MakeOptional fails when field does not exist in record") {
      val original  = DynamicValue.Record("other" -> DynamicValue.int(1))
      val migration = DynamicMigration.record(_.makeFieldOptional("missing", DynamicValue.Null))
      val result    = migration(original)

      result match {
        case Left(MigrationError.FieldNotFound(_, "missing")) => assertTrue(true)
        case _                                                => assertTrue(false)
      }
    }
  )

  private val changeTypeErrorSuite = suite("ChangeType error cases")(
    test("ChangeType fails when field does not exist") {
      val original  = DynamicValue.Record("other" -> DynamicValue.int(1))
      val migration = DynamicMigration.record(
        _.changeFieldType("nonexistent", PrimitiveConversion.IntToLong, PrimitiveConversion.LongToInt)
      )
      val result = migration(original)

      result match {
        case Left(MigrationError.FieldNotFound(_, _)) => assertTrue(true)
        case _                                        => assertTrue(false)
      }
    },
    test("ChangeType fails when conversion fails") {
      val original  = DynamicValue.Record("field" -> DynamicValue.string("not an int"))
      val migration = DynamicMigration.record(
        _.changeFieldType("field", PrimitiveConversion.IntToLong, PrimitiveConversion.LongToInt)
      )
      val result = migration(original)

      result match {
        case Left(MigrationError.TransformFailed(_, _)) => assertTrue(true)
        case _                                          => assertTrue(false)
      }
    }
  )

  private val transformErrorSuite = suite("Transform error cases")(
    test("Transform fails when field does not exist") {
      val original  = DynamicValue.Record("other" -> DynamicValue.int(1))
      val migration = DynamicMigration.record(
        _.transformField("nonexistent", DynamicValueTransform.identity, DynamicValueTransform.identity)
      )
      val result = migration(original)

      result match {
        case Left(MigrationError.FieldNotFound(_, _)) => assertTrue(true)
        case _                                        => assertTrue(false)
      }
    }
  )

  private val additionalErrorTypesSuite = suite("Additional MigrationError types")(
    test("CaseNotFound error can be serialized") {
      val error  = MigrationError.CaseNotFound(DynamicOptic.root, "MissingCase")
      val schema = Schema[MigrationError]
      val dv     = schema.toDynamicValue(error)
      val result = schema.fromDynamicValue(dv)
      assertTrue(result == Right(error))
    },
    test("InvalidIndex error can be serialized") {
      val error  = MigrationError.InvalidIndex(DynamicOptic.root, 10, 5)
      val schema = Schema[MigrationError]
      val dv     = schema.toDynamicValue(error)
      val result = schema.fromDynamicValue(dv)
      assertTrue(result == Right(error))
    },
    test("ExpressionEvalFailed error can be serialized") {
      val error  = MigrationError.ExpressionEvalFailed(DynamicOptic.root, "eval failed")
      val schema = Schema[MigrationError]
      val dv     = schema.toDynamicValue(error)
      val result = schema.fromDynamicValue(dv)
      assertTrue(result == Right(error))
    },
    test("IncompatibleValue error can be serialized") {
      val error  = MigrationError.IncompatibleValue(DynamicOptic.root, "incompatible")
      val schema = Schema[MigrationError]
      val dv     = schema.toDynamicValue(error)
      val result = schema.fromDynamicValue(dv)
      assertTrue(result == Right(error))
    }
  )

  private val conversionErrorSuite = suite("IntToDouble conversion errors")(
    test("IntToDouble fails for non-Int input") {
      val value  = DynamicValue.string("not an int")
      val result = PrimitiveConversion.IntToDouble(value)
      assertTrue(result.isLeft)
    }
  )

  private val renameFieldEdgeCasesSuite = suite("Rename field edge cases")(
    test("Rename fails when target field already exists") {
      val original = DynamicValue.Record(
        "oldName" -> DynamicValue.string("value"),
        "newName" -> DynamicValue.string("existing")
      )
      val migration = DynamicMigration.record(_.renameField("oldName", "newName"))
      val result    = migration(original)

      result match {
        case Left(MigrationError.FieldAlreadyExists(_, "newName")) => assertTrue(true)
        case _                                                     => assertTrue(false)
      }
    },
    test("Rename fails when source field does not exist") {
      val original  = DynamicValue.Record("other" -> DynamicValue.string("value"))
      val migration = DynamicMigration.record(_.renameField("nonexistent", "newName"))
      val result    = migration(original)

      result match {
        case Left(MigrationError.FieldNotFound(_, "nonexistent")) => assertTrue(true)
        case _                                                    => assertTrue(false)
      }
    }
  )

  private val errorPropagationSuite = suite("Error propagation in fold operations")(
    test("Multiple field action errors stop at first failure") {
      val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
      val migration = DynamicMigration.record(
        _.removeField("nonexistent1", DynamicValue.Null)
          .removeField("nonexistent2", DynamicValue.Null)
      )
      val result = migration(original)

      result match {
        case Left(MigrationError.FieldNotFound(_, "nonexistent1")) => assertTrue(true)
        case _                                                     => assertTrue(false)
      }
    },
    test("Nested field error propagates correctly when earlier nested step fails") {
      val original = DynamicValue.Record(
        "field1" -> DynamicValue.Record("inner" -> DynamicValue.int(1)),
        "field2" -> DynamicValue.Record("inner" -> DynamicValue.int(2))
      )
      val migration = DynamicMigration.record(
        _.nested("field1")(_.removeField("missing", DynamicValue.Null))
          .nested("field2")(_.addField("added", DynamicValue.int(0)))
      )
      val result = migration(original)

      result match {
        case Left(MigrationError.FieldNotFound(_, "missing")) => assertTrue(true)
        case _                                                => assertTrue(false)
      }
    }
  )

  private val sequenceTransformErrorSuite = suite("Sequence transform error propagation")(
    test("Sequence transform stops at first failure and propagates error") {
      val failingFirst = DynamicValueTransform.sequence(
        DynamicValueTransform.stringAppend(" suffix"),
        DynamicValueTransform.identity,
        DynamicValueTransform.stringPrepend("prefix ")
      )
      val result = failingFirst(DynamicValue.int(10))
      assertTrue(result.isLeft)
    }
  )
}
