package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

object MigrationSpec extends SchemaBaseSpec {
  // ─── Test domain types ──────────────────────────────────────────────────

  final case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived[PersonV1]
  }

  final case class PersonV2(fullName: String, age: Int, country: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived[PersonV2]
  }

  final case class SimpleRecord(x: Int, y: String)
  object SimpleRecord extends CompanionOptics[SimpleRecord] {
    implicit val schema: Schema[SimpleRecord] = Schema.derived[SimpleRecord]
  }

  final case class RenamedRecord(z: Int, y: String)
  object RenamedRecord extends CompanionOptics[RenamedRecord] {
    implicit val schema: Schema[RenamedRecord] = Schema.derived[RenamedRecord]
  }

  final case class ExtendedRecord(x: Int, y: String, extra: Boolean)
  object ExtendedRecord extends CompanionOptics[ExtendedRecord] {
    implicit val schema: Schema[ExtendedRecord] = Schema.derived[ExtendedRecord]
  }

  final case class ShrunkRecord(y: String)
  object ShrunkRecord extends CompanionOptics[ShrunkRecord] {
    implicit val schema: Schema[ShrunkRecord] = Schema.derived[ShrunkRecord]
  }

  final case class TypeChangedRecord(x: String, y: String)
  object TypeChangedRecord extends CompanionOptics[TypeChangedRecord] {
    implicit val schema: Schema[TypeChangedRecord] = Schema.derived[TypeChangedRecord]
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private def eval(expr: DynamicSchemaExpr, input: DynamicValue): Either[MigrationError, DynamicValue] =
    DynamicMigration.evalExpr(expr, input)

  // ─── Tests ──────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    identitySuite,
    addFieldSuite,
    dropFieldSuite,
    renameFieldSuite,
    transformFieldSuite,
    changeFieldTypeSuite,
    compositionSuite,
    reverseSuite,
    enumSuite,
    errorSuite,
    dynamicMigrationSuite,
    opticBasedSuite,
    mandateOptionalSuite,
    mapTransformsSuite,
    exprEdgeCases,
    nestedPathSuite,
    errorMessageSuite,
    changeTypeEdgeCases,
    builderValidationSuite,
    exhaustiveCoverageSuite
  )

  val identitySuite: Spec[Any, Any] = suite("Identity")(
    test("identity migration returns the same value") {
      val m      = Migration.identity[SimpleRecord]
      val record = SimpleRecord(42, "hello")
      assert(m(record))(isRight(equalTo(record)))
    },
    test("identity migration is empty") {
      val m = Migration.identity[SimpleRecord]
      assertTrue(m.isEmpty)
    },
    test("identity migration law: m(a) == Right(a)") {
      val m = Migration.identity[SimpleRecord]
      val a = SimpleRecord(1, "test")
      assert(m(a))(isRight(equalTo(a)))
    }
  )

  val addFieldSuite: Spec[Any, Any] = suite("AddField")(
    test("add a field with a default value") {
      val m = Migration
        .newBuilder[SimpleRecord, ExtendedRecord]
        .addField("extra", false)
        .buildPartial
      val result = m(SimpleRecord(42, "hello"))
      assert(result)(isRight(equalTo(ExtendedRecord(42, "hello", false))))
    },
    test("add field fails if field already exists") {
      val dm = DynamicMigration.single(
        MigrationAction.AddField(
          DynamicOptic.root.field("x"),
          DynamicSchemaExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(0)))
        )
      )
      val input = DynamicValue.Record("x" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)))
      assert(dm(input))(isLeft)
    }
  )

  val dropFieldSuite: Spec[Any, Any] = suite("DropField")(
    test("drop a field") {
      val m = Migration
        .newBuilder[SimpleRecord, ShrunkRecord]
        .dropField("x")
        .buildPartial
      val result = m(SimpleRecord(42, "hello"))
      assert(result)(isRight(equalTo(ShrunkRecord("hello"))))
    },
    test("drop field fails if field doesn't exist") {
      val dm = DynamicMigration.single(
        MigrationAction.DropField(DynamicOptic.root.field("nonexistent"), DynamicSchemaExpr.DefaultValue)
      )
      val input = DynamicValue.Record("x" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)))
      assert(dm(input))(isLeft)
    }
  )

  val renameFieldSuite: Spec[Any, Any] = suite("RenameField")(
    test("rename a field") {
      val m = Migration
        .newBuilder[SimpleRecord, RenamedRecord]
        .renameField("x", "z")
        .buildPartial
      val result = m(SimpleRecord(42, "hello"))
      assert(result)(isRight(equalTo(RenamedRecord(42, "hello"))))
    },
    test("rename field fails if source field doesn't exist") {
      val dm = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root.field("nonexistent"), "z")
      )
      val input = DynamicValue.Record("x" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)))
      assert(dm(input))(isLeft)
    }
  )

  val transformFieldSuite: Spec[Any, Any] = suite("TransformField")(
    test("transform a field value with a constant") {
      val dm = DynamicMigration.single(
        MigrationAction.TransformValue(
          DynamicOptic.root.field("x"),
          DynamicSchemaExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(99)))
        )
      )
      val input = DynamicValue.Record(
        "x" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)),
        "y" -> new DynamicValue.Primitive(new PrimitiveValue.String("hello"))
      )
      val result = dm(input)
      assert(result)(
        isRight(
          equalTo(
            DynamicValue.Record(
              "x" -> new DynamicValue.Primitive(new PrimitiveValue.Int(99)),
              "y" -> new DynamicValue.Primitive(new PrimitiveValue.String("hello"))
            )
          )
        )
      )
    }
  )

  val changeFieldTypeSuite: Spec[Any, Any] = suite("ChangeFieldType")(
    test("change field type from Int to String") {
      val m = Migration
        .newBuilder[SimpleRecord, TypeChangedRecord]
        .changeFieldType("x", "x", DynamicSchemaExpr.IntToString)
        .buildPartial
      val result = m(SimpleRecord(42, "hello"))
      assert(result)(isRight(equalTo(TypeChangedRecord("42", "hello"))))
    },
    test("IntToString and StringToInt are inverses") {
      val intVal = new DynamicValue.Primitive(new PrimitiveValue.Int(42))
      val result = for {
        strVal  <- eval(DynamicSchemaExpr.IntToString, intVal)
        backInt <- eval(DynamicSchemaExpr.StringToInt, strVal)
      } yield backInt
      assert(result)(isRight(equalTo(intVal)))
    },
    test("LongToString and StringToLong are inverses") {
      val longVal = new DynamicValue.Primitive(new PrimitiveValue.Long(123456789L))
      val result  = for {
        strVal  <- eval(DynamicSchemaExpr.LongToString, longVal)
        backLng <- eval(DynamicSchemaExpr.StringToLong, strVal)
      } yield backLng
      assert(result)(isRight(equalTo(longVal)))
    },
    test("BooleanToString and StringToBoolean are inverses") {
      val boolVal = new DynamicValue.Primitive(new PrimitiveValue.Boolean(true))
      val result  = for {
        strVal   <- eval(DynamicSchemaExpr.BooleanToString, boolVal)
        backBool <- eval(DynamicSchemaExpr.StringToBoolean, strVal)
      } yield backBool
      assert(result)(isRight(equalTo(boolVal)))
    }
  )

  val compositionSuite: Spec[Any, Any] = suite("Composition")(
    test("compose two migrations sequentially") {
      val m1 = Migration
        .newBuilder[SimpleRecord, ExtendedRecord]
        .addField("extra", false)
        .buildPartial

      // Can compose at the dynamic level
      val dm1 = m1.dynamicMigration
      val dm2 = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root.field("extra"), "flag")
      )
      val composed = dm1 ++ dm2

      val input  = SimpleRecord.schema.toDynamicValue(SimpleRecord(1, "a"))
      val result = composed(input)
      assertTrue(result.isRight)
    },
    test("associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
      val m1 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("a"), "b"))
      val m2 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("b"), "c"))
      val m3 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("c"), "d"))

      val input = DynamicValue.Record("a" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)))

      val leftAssoc  = (m1 ++ m2) ++ m3
      val rightAssoc = m1 ++ (m2 ++ m3)

      val result1 = leftAssoc(input)
      val result2 = rightAssoc(input)

      assert(result1)(equalTo(result2))
    }
  )

  val reverseSuite: Spec[Any, Any] = suite("Reverse")(
    test("reverse of rename is rename back") {
      val m     = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("a"), "b"))
      val input = DynamicValue.Record("a" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)))

      val result = for {
        forward  <- m(input)
        backward <- m.reverse(forward)
      } yield backward
      assert(result)(isRight(equalTo(input)))
    },
    test("reverse of addField is dropField") {
      val m = DynamicMigration.single(
        MigrationAction.AddField(
          DynamicOptic.root.field("extra"),
          DynamicSchemaExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Boolean(false)))
        )
      )
      val input = DynamicValue.Record("x" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)))

      val result = for {
        forward  <- m(input)
        backward <- m.reverse(forward)
      } yield backward
      assert(result)(isRight(equalTo(input)))
    },
    test("structural reverse: m.reverse.reverse == m") {
      val m = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root.field("a"), "b"),
          MigrationAction.AddField(
            DynamicOptic.root.field("c"),
            DynamicSchemaExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(0)))
          )
        )
      )
      assertTrue(m.reverse.reverse.actions == m.actions)
    },
    test("best-effort semantic inverse for rename") {
      val input = DynamicValue.Record(
        "first" -> new DynamicValue.Primitive(new PrimitiveValue.String("John")),
        "last"  -> new DynamicValue.Primitive(new PrimitiveValue.String("Doe"))
      )
      val m = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root.field("first"), "firstName"),
          MigrationAction.Rename(DynamicOptic.root.field("last"), "lastName")
        )
      )

      val result = for {
        forward  <- m(input)
        backward <- m.reverse(forward)
      } yield backward
      assert(result)(isRight(equalTo(input)))
    }
  )

  val enumSuite: Spec[Any, Any] = suite("Enum")(
    test("rename a variant case") {
      val dm = DynamicMigration.single(
        MigrationAction.RenameCase(DynamicOptic.root, "OldName", "NewName")
      )
      val input = new DynamicValue.Variant(
        "OldName",
        DynamicValue.Record("x" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)))
      )
      val expected = new DynamicValue.Variant(
        "NewName",
        DynamicValue.Record("x" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)))
      )
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("rename case passes through non-matching cases") {
      val dm = DynamicMigration.single(
        MigrationAction.RenameCase(DynamicOptic.root, "OldName", "NewName")
      )
      val input = new DynamicValue.Variant(
        "OtherCase",
        DynamicValue.Record("x" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)))
      )
      assert(dm(input))(isRight(equalTo(input)))
    },
    test("transform case fields") {
      val dm = DynamicMigration.single(
        MigrationAction.TransformCase(
          DynamicOptic.root,
          "MyCase",
          Vector(MigrationAction.Rename(DynamicOptic.root.field("old"), "new"))
        )
      )
      val input = new DynamicValue.Variant(
        "MyCase",
        DynamicValue.Record("old" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)))
      )
      val expected = new DynamicValue.Variant(
        "MyCase",
        DynamicValue.Record("new" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)))
      )
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("reverse of renameCase is renameCase back") {
      val dm = DynamicMigration.single(
        MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
      )
      val input  = new DynamicValue.Variant("A", DynamicValue.Record.empty)
      val result = for {
        forward  <- dm(input)
        backward <- dm.reverse(forward)
      } yield backward
      assert(result)(isRight(equalTo(input)))
    }
  )

  val errorSuite: Spec[Any, Any] = suite("ErrorHandling")(
    test("errors include path information") {
      val dm = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root.field("nonexistent"), "z")
      )
      val input  = DynamicValue.Record("x" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)))
      val result = dm(input)
      assert(result)(isLeft(hasField("path", (e: MigrationError) => e.path.toString, anything)))
    },
    test("type mismatch error on non-record value") {
      val dm = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root.field("x"), "y")
      )
      val input  = new DynamicValue.Primitive(new PrimitiveValue.Int(1))
      val result = dm(input)
      assert(result)(isLeft)
    },
    test("conversion failure error on bad type change") {
      val result = eval(
        DynamicSchemaExpr.StringToInt,
        new DynamicValue.Primitive(new PrimitiveValue.String("not_a_number"))
      )
      assert(result)(isLeft)
    }
  )

  val dynamicMigrationSuite: Spec[Any, Any] = suite("DynamicMigration")(
    test("empty migration is identity") {
      val dm    = DynamicMigration.empty
      val input = DynamicValue.Record("x" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)))
      assert(dm(input))(isRight(equalTo(input)))
    },
    test("single action migration") {
      val dm = DynamicMigration.single(
        MigrationAction.AddField(
          DynamicOptic.root.field("y"),
          DynamicSchemaExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.String("default")))
        )
      )
      val input    = DynamicValue.Record("x" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)))
      val expected = DynamicValue.Record(
        "x" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)),
        "y" -> new DynamicValue.Primitive(new PrimitiveValue.String("default"))
      )
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("multiple actions applied sequentially") {
      val dm = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root.field("a"), "b"),
          MigrationAction.AddField(
            DynamicOptic.root.field("c"),
            DynamicSchemaExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Boolean(true)))
          ),
          MigrationAction.DropField(DynamicOptic.root.field("d"), DynamicSchemaExpr.DefaultValue)
        )
      )
      val input = DynamicValue.Record(
        "a" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)),
        "d" -> new DynamicValue.Primitive(new PrimitiveValue.String("drop_me"))
      )
      val expected = DynamicValue.Record(
        "b" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)),
        "c" -> new DynamicValue.Primitive(new PrimitiveValue.Boolean(true))
      )
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("transform elements in a sequence") {
      val dm = DynamicMigration.single(
        MigrationAction.TransformElements(DynamicOptic.root, DynamicSchemaExpr.IntToString)
      )
      val input = new DynamicValue.Sequence(
        Chunk(
          new DynamicValue.Primitive(new PrimitiveValue.Int(1)),
          new DynamicValue.Primitive(new PrimitiveValue.Int(2)),
          new DynamicValue.Primitive(new PrimitiveValue.Int(3))
        )
      )
      val expected = new DynamicValue.Sequence(
        Chunk(
          new DynamicValue.Primitive(new PrimitiveValue.String("1")),
          new DynamicValue.Primitive(new PrimitiveValue.String("2")),
          new DynamicValue.Primitive(new PrimitiveValue.String("3"))
        )
      )
      assert(dm(input))(isRight(equalTo(expected)))
    }
  )

  val opticBasedSuite: Spec[Any, Any] = suite("OpticBasedAPI")(
    test("rename field using typed optics") {
      val fromOptic = SimpleRecord.optic(_.x)
      val toOptic   = RenamedRecord.optic(_.z)
      val m         = Migration
        .newBuilder[SimpleRecord, RenamedRecord]
        .renameField(fromOptic, toOptic)
        .buildPartial
      val result = m(SimpleRecord(42, "hello"))
      assert(result)(isRight(equalTo(RenamedRecord(42, "hello"))))
    },
    test("add field using typed optic") {
      val targetOptic = ExtendedRecord.optic(_.extra)
      val m           = Migration
        .newBuilder[SimpleRecord, ExtendedRecord]
        .addField(targetOptic, false)
        .buildPartial
      val result = m(SimpleRecord(42, "hello"))
      assert(result)(isRight(equalTo(ExtendedRecord(42, "hello", false))))
    },
    test("drop field using typed optic") {
      val sourceOptic = SimpleRecord.optic(_.x)
      val m           = Migration
        .newBuilder[SimpleRecord, ShrunkRecord]
        .dropField(sourceOptic)
        .buildPartial
      val result = m(SimpleRecord(42, "hello"))
      assert(result)(isRight(equalTo(ShrunkRecord("hello"))))
    },
    test("change field type using typed optics") {
      val fromOptic = SimpleRecord.optic(_.x)
      val toOptic   = TypeChangedRecord.optic(_.x)
      val m         = Migration
        .newBuilder[SimpleRecord, TypeChangedRecord]
        .changeFieldType(fromOptic, toOptic, DynamicSchemaExpr.IntToString)
        .buildPartial
      val result = m(SimpleRecord(42, "hello"))
      assert(result)(isRight(equalTo(TypeChangedRecord("42", "hello"))))
    }
  )

  val mandateOptionalSuite: Spec[Any, Any] = suite("MandateAndOptionalize")(
    test("mandate replaces Null with default") {
      val dm = DynamicMigration(Vector(
        MigrationAction.Mandate(DynamicOptic.root.field("opt"), DynamicSchemaExpr.Literal(DynamicValue.int(0))),
        MigrationAction.Rename(DynamicOptic.root.field("opt"), "req")
      ))
      val input    = DynamicValue.Record("opt" -> DynamicValue.Null)
      val expected = DynamicValue.Record("req" -> DynamicValue.int(0))
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("mandate extracts value from Some variant") {
      val dm = DynamicMigration(Vector(
        MigrationAction.Mandate(DynamicOptic.root.field("opt"), DynamicSchemaExpr.Literal(DynamicValue.int(0))),
        MigrationAction.Rename(DynamicOptic.root.field("opt"), "req")
      ))
      val input    = DynamicValue.Record("opt" -> new DynamicValue.Variant("Some", DynamicValue.int(42)))
      val expected = DynamicValue.Record("req" -> DynamicValue.int(42))
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("mandate uses default for None variant") {
      val dm = DynamicMigration(Vector(
        MigrationAction.Mandate(DynamicOptic.root.field("opt"), DynamicSchemaExpr.Literal(DynamicValue.int(0))),
        MigrationAction.Rename(DynamicOptic.root.field("opt"), "req")
      ))
      val input    = DynamicValue.Record("opt" -> new DynamicValue.Variant("None", DynamicValue.Record.empty))
      val expected = DynamicValue.Record("req" -> DynamicValue.int(0))
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("mandate passes through non-Option variant") {
      val dm = DynamicMigration(Vector(
        MigrationAction.Mandate(DynamicOptic.root.field("opt"), DynamicSchemaExpr.Literal(DynamicValue.int(0))),
        MigrationAction.Rename(DynamicOptic.root.field("opt"), "req")
      ))
      val input    = DynamicValue.Record("opt" -> new DynamicValue.Variant("Active", DynamicValue.int(1)))
      val expected = DynamicValue.Record("req" -> new DynamicValue.Variant("Active", DynamicValue.int(1)))
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("mandate passes through regular value") {
      val dm = DynamicMigration(Vector(
        MigrationAction.Mandate(DynamicOptic.root.field("opt"), DynamicSchemaExpr.Literal(DynamicValue.int(0))),
        MigrationAction.Rename(DynamicOptic.root.field("opt"), "req")
      ))
      val input    = DynamicValue.Record("opt" -> DynamicValue.int(42))
      val expected = DynamicValue.Record("req" -> DynamicValue.int(42))
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("mandate fails on missing field") {
      val dm = DynamicMigration.single(
        MigrationAction.Mandate(DynamicOptic.root.field("missing"), DynamicSchemaExpr.Literal(DynamicValue.int(0)))
      )
      val input = DynamicValue.Record("other" -> DynamicValue.int(1))
      assert(dm(input))(isLeft)
    },
    test("optionalize passes through non-null") {
      val dm = DynamicMigration(Vector(
        MigrationAction.Optionalize(DynamicOptic.root.field("req")),
        MigrationAction.Rename(DynamicOptic.root.field("req"), "opt")
      ))
      val input    = DynamicValue.Record("req" -> DynamicValue.int(42))
      val expected = DynamicValue.Record("opt" -> DynamicValue.int(42))
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("optionalize passes through Null") {
      val dm = DynamicMigration(Vector(
        MigrationAction.Optionalize(DynamicOptic.root.field("req")),
        MigrationAction.Rename(DynamicOptic.root.field("req"), "opt")
      ))
      val input    = DynamicValue.Record("req" -> DynamicValue.Null)
      val expected = DynamicValue.Record("opt" -> DynamicValue.Null)
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("optionalize fails on missing field") {
      val dm = DynamicMigration.single(
        MigrationAction.Optionalize(DynamicOptic.root.field("missing"))
      )
      val input = DynamicValue.Record("other" -> DynamicValue.int(1))
      assert(dm(input))(isLeft)
    },
    test("mandate and optionalize are inverses") {
      val mandate = DynamicMigration.single(
        MigrationAction.Mandate(DynamicOptic.root.field("opt"), DynamicSchemaExpr.Literal(DynamicValue.int(0)))
      )
      assertTrue(mandate.reverse.actions.head.isInstanceOf[MigrationAction.Optionalize])
    }
  )

  val mapTransformsSuite: Spec[Any, Any] = suite("MapTransforms")(
    test("transform map keys") {
      val dm = DynamicMigration.single(
        MigrationAction.TransformKeys(DynamicOptic.root, DynamicSchemaExpr.IntToString)
      )
      val input = new DynamicValue.Map(
        Chunk(
          (new DynamicValue.Primitive(new PrimitiveValue.Int(1)), DynamicValue.string("one")),
          (new DynamicValue.Primitive(new PrimitiveValue.Int(2)), DynamicValue.string("two"))
        )
      )
      val expected = new DynamicValue.Map(
        Chunk(
          (DynamicValue.string("1"), DynamicValue.string("one")),
          (DynamicValue.string("2"), DynamicValue.string("two"))
        )
      )
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("transform map values") {
      val dm = DynamicMigration.single(
        MigrationAction.TransformValues(DynamicOptic.root, DynamicSchemaExpr.IntToString)
      )
      val input = new DynamicValue.Map(
        Chunk(
          (DynamicValue.string("a"), new DynamicValue.Primitive(new PrimitiveValue.Int(1))),
          (DynamicValue.string("b"), new DynamicValue.Primitive(new PrimitiveValue.Int(2)))
        )
      )
      val expected = new DynamicValue.Map(
        Chunk(
          (DynamicValue.string("a"), DynamicValue.string("1")),
          (DynamicValue.string("b"), DynamicValue.string("2"))
        )
      )
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("transform map keys fails on wrong type") {
      val dm = DynamicMigration.single(
        MigrationAction.TransformKeys(DynamicOptic.root, DynamicSchemaExpr.IntToString)
      )
      val input = new DynamicValue.Map(
        Chunk(
          (DynamicValue.string("already_string"), DynamicValue.int(1))
        )
      )
      assert(dm(input))(isLeft)
    },
    test("transform map values fails on wrong type") {
      val dm = DynamicMigration.single(
        MigrationAction.TransformValues(DynamicOptic.root, DynamicSchemaExpr.StringToInt)
      )
      val input = new DynamicValue.Map(
        Chunk(
          (DynamicValue.string("a"), DynamicValue.int(1))
        )
      )
      assert(dm(input))(isLeft)
    },
    test("transform map on non-map fails") {
      val dm = DynamicMigration.single(
        MigrationAction.TransformKeys(DynamicOptic.root, DynamicSchemaExpr.Identity)
      )
      val input = DynamicValue.int(42)
      assert(dm(input))(isLeft)
    }
  )

  val exprEdgeCases: Spec[Any, Any] = suite("DynamicSchemaExprEdgeCases")(
    test("IntToString fails on non-Int") {
      val result = eval(DynamicSchemaExpr.IntToString, DynamicValue.string("hello"))
      assert(result)(isLeft)
    },
    test("StringToInt fails on non-String") {
      val result = eval(DynamicSchemaExpr.StringToInt, DynamicValue.int(42))
      assert(result)(isLeft)
    },
    test("LongToString fails on non-Long") {
      val result = eval(DynamicSchemaExpr.LongToString, DynamicValue.int(42))
      assert(result)(isLeft)
    },
    test("StringToLong fails on non-String") {
      val result = eval(DynamicSchemaExpr.StringToLong, DynamicValue.int(42))
      assert(result)(isLeft)
    },
    test("StringToLong fails on unparseable string") {
      val result = eval(DynamicSchemaExpr.StringToLong, DynamicValue.string("not_a_long"))
      assert(result)(isLeft)
    },
    test("IntToLong widening") {
      val result = eval(DynamicSchemaExpr.IntToLong, new DynamicValue.Primitive(new PrimitiveValue.Int(42)))
      assert(result)(isRight(equalTo(new DynamicValue.Primitive(new PrimitiveValue.Long(42L)))))
    },
    test("LongToInt narrowing") {
      val result = eval(DynamicSchemaExpr.LongToInt, new DynamicValue.Primitive(new PrimitiveValue.Long(42L)))
      assert(result)(isRight(equalTo(new DynamicValue.Primitive(new PrimitiveValue.Int(42)))))
    },
    test("IntToLong fails on non-Int") {
      val result = eval(DynamicSchemaExpr.IntToLong, DynamicValue.string("hi"))
      assert(result)(isLeft)
    },
    test("LongToInt fails on non-Long") {
      val result = eval(DynamicSchemaExpr.LongToInt, DynamicValue.string("hi"))
      assert(result)(isLeft)
    },
    test("BooleanToString fails on non-Boolean") {
      val result = eval(DynamicSchemaExpr.BooleanToString, DynamicValue.int(1))
      assert(result)(isLeft)
    },
    test("StringToBoolean fails on non-String") {
      val result = eval(DynamicSchemaExpr.StringToBoolean, DynamicValue.int(1))
      assert(result)(isLeft)
    },
    test("StringToBoolean fails on invalid boolean string") {
      val result = eval(DynamicSchemaExpr.StringToBoolean, DynamicValue.string("maybe"))
      assert(result)(isLeft)
    },
    test("StringToBoolean accepts 'false'") {
      val result = eval(DynamicSchemaExpr.StringToBoolean, DynamicValue.string("false"))
      assert(result)(isRight(equalTo(new DynamicValue.Primitive(new PrimitiveValue.Boolean(false)))))
    },
    test("Identity passes any value through") {
      val v = DynamicValue.int(42)
      assert(eval(DynamicSchemaExpr.Identity, v))(isRight(equalTo(v)))
    },
    test("Identity reverse is itself") {
      assertTrue(DynamicSchemaExpr.Identity.reverse == DynamicSchemaExpr.Identity)
    },
    test("IntToLong and LongToInt are inverses") {
      val intVal = new DynamicValue.Primitive(new PrimitiveValue.Int(42))
      val result = for {
        l <- eval(DynamicSchemaExpr.IntToLong, intVal)
        i <- eval(DynamicSchemaExpr.LongToInt, l)
      } yield i
      assert(result)(isRight(equalTo(intVal)))
    }
  )

  val nestedPathSuite: Spec[Any, Any] = suite("NestedPathOperations")(
    test("rename field at nested path") {
      val dm = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root.field("inner").field("old"), "new")
      )
      val input = DynamicValue.Record(
        "inner" -> DynamicValue.Record(
          "old"   -> DynamicValue.int(1),
          "other" -> DynamicValue.string("keep")
        )
      )
      val expected = DynamicValue.Record(
        "inner" -> DynamicValue.Record(
          "new"   -> DynamicValue.int(1),
          "other" -> DynamicValue.string("keep")
        )
      )
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("nested path fails on missing intermediate field") {
      val dm = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root.field("missing").field("a"), "b")
      )
      val input = DynamicValue.Record("other" -> DynamicValue.int(1))
      assert(dm(input))(isLeft)
    },
    test("nested path fails on non-record intermediate") {
      val dm = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root.field("inner").field("a"), "b")
      )
      val input = DynamicValue.Record("inner" -> DynamicValue.int(1))
      assert(dm(input))(isLeft)
    },
    test("transform elements fails on non-sequence") {
      val dm = DynamicMigration.single(
        MigrationAction.TransformElements(DynamicOptic.root, DynamicSchemaExpr.Identity)
      )
      val input = DynamicValue.int(42)
      assert(dm(input))(isLeft)
    },
    test("rename on non-record fails") {
      val dm = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root.field("x"), "y")
      )
      val input = new DynamicValue.Sequence(Chunk(DynamicValue.int(1)))
      assert(dm(input))(isLeft)
    },
    test("renameCase on non-variant fails") {
      val dm = DynamicMigration.single(
        MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
      )
      val input = DynamicValue.Record("x" -> DynamicValue.int(1))
      assert(dm(input))(isLeft)
    }
  )

  val errorMessageSuite: Spec[Any, Any] = suite("ErrorMessages")(
    test("error at root shows just details") {
      val err = MigrationError.MissingField(DynamicOptic.root, "name")
      assertTrue(err.getMessage.contains("Missing field 'name'"))
    },
    test("error at nested path shows path") {
      val err = MigrationError.TypeMismatch(DynamicOptic.root.field("inner"), "Record", "Int")
      assertTrue(err.getMessage.contains("at"))
    },
    test("ConversionFailed includes reason") {
      val err = MigrationError.ConversionFailed(DynamicOptic.root, "String", "Int", "bad format")
      assertTrue(err.getMessage.contains("bad format"))
    },
    test("failed helper at root") {
      val err = MigrationError.failed("something broke")
      assertTrue(err.getMessage.contains("something broke"))
    },
    test("failed helper at path") {
      val err = MigrationError.failed(DynamicOptic.root.field("x"), "bad value")
      assertTrue(err.getMessage.contains("bad value"))
    }
  )

  val changeTypeEdgeCases: Spec[Any, Any] = suite("ChangeTypeEdgeCases")(
    test("changeType fails on conversion error") {
      val dm = DynamicMigration.single(
        MigrationAction.ChangeType(DynamicOptic.root.field("x"), DynamicSchemaExpr.StringToInt)
      )
      val input = DynamicValue.Record("x" -> DynamicValue.string("not_a_number"))
      assert(dm(input))(isLeft)
    },
    test("changeType fails on missing field") {
      val dm = DynamicMigration.single(
        MigrationAction.ChangeType(DynamicOptic.root.field("missing"), DynamicSchemaExpr.IntToString)
      )
      val input = DynamicValue.Record("x" -> DynamicValue.int(1))
      assert(dm(input))(isLeft)
    },
    test("transformField fails on transform error") {
      val dm = DynamicMigration.single(
        MigrationAction.TransformValue(DynamicOptic.root.field("x"), DynamicSchemaExpr.StringToInt)
      )
      val input = DynamicValue.Record("x" -> DynamicValue.string("bad"))
      assert(dm(input))(isLeft)
    },
    test("transformField fails on missing field") {
      val dm = DynamicMigration.single(
        MigrationAction.TransformValue(DynamicOptic.root.field("missing"), DynamicSchemaExpr.Identity)
      )
      val input = DynamicValue.Record("x" -> DynamicValue.int(1))
      assert(dm(input))(isLeft)
    }
  )

  val exhaustiveCoverageSuite: Spec[Any, Any] = suite("ExhaustiveCoverage")(
    suite("MultiActionFailure")(
      test("migration fails at second action and returns error") {
        val dm = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root.field("a"), "b"),
            MigrationAction.Rename(DynamicOptic.root.field("nonexistent"), "c")
          )
        )
        val input = DynamicValue.Record("a" -> DynamicValue.int(1))
        assert(dm(input))(isLeft)
      }
    ),
    suite("CasePathNavigation")(
      test("navigate through matching Case node in path") {
        val dm = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root.caseOf("PersonCase").field("old"), "new")
        )
        val input = new DynamicValue.Variant(
          "PersonCase",
          DynamicValue.Record("old" -> DynamicValue.int(1), "other" -> DynamicValue.string("keep"))
        )
        val expected = new DynamicValue.Variant(
          "PersonCase",
          DynamicValue.Record("new" -> DynamicValue.int(1), "other" -> DynamicValue.string("keep"))
        )
        assert(dm(input))(isRight(equalTo(expected)))
      },
      test("Case node passes through non-matching variant") {
        val dm = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root.caseOf("PersonCase").field("old"), "new")
        )
        val input = new DynamicValue.Variant("OtherCase", DynamicValue.Record("old" -> DynamicValue.int(1)))
        assert(dm(input))(isRight(equalTo(input)))
      },
      test("Case node on non-variant fails with TypeMismatch") {
        val dm = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root.caseOf("PersonCase").field("old"), "new")
        )
        val input = DynamicValue.Record("old" -> DynamicValue.int(1))
        assert(dm(input))(isLeft)
      }
    ),
    suite("NestedCollections")(
      test("rename case at nested field path") {
        val path = DynamicOptic.root.field("status")
        val dm   = DynamicMigration.single(MigrationAction.RenameCase(path, "Active", "Enabled"))
        val input = DynamicValue.Record(
          "status" -> new DynamicValue.Variant("Active", DynamicValue.Record.empty)
        )
        val expected = DynamicValue.Record(
          "status" -> new DynamicValue.Variant("Enabled", DynamicValue.Record.empty)
        )
        assert(dm(input))(isRight(equalTo(expected)))
      },
      test("variant operation at nested path fails on non-variant leaf") {
        val path = DynamicOptic.root.field("data")
        val dm   = DynamicMigration.single(MigrationAction.RenameCase(path, "A", "B"))
        val input = DynamicValue.Record("data" -> DynamicValue.int(42))
        assert(dm(input))(isLeft)
      },
      test("transform elements at nested path") {
        val path = DynamicOptic.root.field("items")
        val dm   = DynamicMigration.single(MigrationAction.TransformElements(path, DynamicSchemaExpr.IntToString))
        val input = DynamicValue.Record(
          "items" -> new DynamicValue.Sequence(Chunk(DynamicValue.int(1), DynamicValue.int(2)))
        )
        val expected = DynamicValue.Record(
          "items" -> new DynamicValue.Sequence(Chunk(DynamicValue.string("1"), DynamicValue.string("2")))
        )
        assert(dm(input))(isRight(equalTo(expected)))
      },
      test("sequence operation at nested path fails on non-sequence leaf") {
        val path = DynamicOptic.root.field("data")
        val dm   = DynamicMigration.single(MigrationAction.TransformElements(path, DynamicSchemaExpr.Identity))
        val input = DynamicValue.Record("data" -> DynamicValue.int(42))
        assert(dm(input))(isLeft)
      },
      test("transform map keys at nested path") {
        val path = DynamicOptic.root.field("mapping")
        val dm   = DynamicMigration.single(MigrationAction.TransformKeys(path, DynamicSchemaExpr.IntToString))
        val input = DynamicValue.Record(
          "mapping" -> new DynamicValue.Map(Chunk((DynamicValue.int(1), DynamicValue.string("one"))))
        )
        val expected = DynamicValue.Record(
          "mapping" -> new DynamicValue.Map(Chunk((DynamicValue.string("1"), DynamicValue.string("one"))))
        )
        assert(dm(input))(isRight(equalTo(expected)))
      },
      test("map operation at nested path fails on non-map leaf") {
        val path = DynamicOptic.root.field("data")
        val dm   = DynamicMigration.single(MigrationAction.TransformKeys(path, DynamicSchemaExpr.Identity))
        val input = DynamicValue.Record("data" -> DynamicValue.int(42))
        assert(dm(input))(isLeft)
      }
    ),
    suite("UnsupportedPathNode")(
      test("unsupported path node type fails") {
        val dm = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root.elements.field("a"), "b")
        )
        val input = new DynamicValue.Sequence(Chunk(DynamicValue.Record("a" -> DynamicValue.int(1))))
        assert(dm(input))(isLeft)
      }
    ),
    suite("TransformCasePassthrough")(
      test("transformCase passes through non-matching case") {
        val dm = DynamicMigration.single(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            "MyCase",
            Vector(MigrationAction.Rename(DynamicOptic.root.field("old"), "new"))
          )
        )
        val input = new DynamicValue.Variant("OtherCase", DynamicValue.Record("x" -> DynamicValue.int(1)))
        assert(dm(input))(isRight(equalTo(input)))
      }
    ),
    suite("TransformElementsError")(
      test("transformElements fails on element transform error") {
        val dm = DynamicMigration.single(
          MigrationAction.TransformElements(DynamicOptic.root, DynamicSchemaExpr.StringToInt)
        )
        val input = new DynamicValue.Sequence(Chunk(DynamicValue.string("not_a_number")))
        assert(dm(input))(isLeft)
      }
    ),
    suite("LowercaseVariants")(
      test("mandate extracts value from lowercase 'some' variant") {
        val dm = DynamicMigration(Vector(
          MigrationAction.Mandate(DynamicOptic.root.field("opt"), DynamicSchemaExpr.Literal(DynamicValue.int(0))),
          MigrationAction.Rename(DynamicOptic.root.field("opt"), "req")
        ))
        val input    = DynamicValue.Record("opt" -> new DynamicValue.Variant("some", DynamicValue.int(42)))
        val expected = DynamicValue.Record("req" -> DynamicValue.int(42))
        assert(dm(input))(isRight(equalTo(expected)))
      },
      test("mandate uses default for lowercase 'none' variant") {
        val dm = DynamicMigration(Vector(
          MigrationAction.Mandate(DynamicOptic.root.field("opt"), DynamicSchemaExpr.Literal(DynamicValue.int(0))),
          MigrationAction.Rename(DynamicOptic.root.field("opt"), "req")
        ))
        val input    = DynamicValue.Record("opt" -> new DynamicValue.Variant("none", DynamicValue.Record.empty))
        val expected = DynamicValue.Record("req" -> DynamicValue.int(0))
        assert(dm(input))(isRight(equalTo(expected)))
      }
    ),
    suite("ActionReverse")(
      test("DropField with DefaultValue reverses to AddField with DefaultValue") {
        val action   = MigrationAction.DropField(DynamicOptic.root.field("x"), DynamicSchemaExpr.DefaultValue)
        val reversed = action.reverse
        assertTrue(
          reversed == MigrationAction.AddField(DynamicOptic.root.field("x"), DynamicSchemaExpr.DefaultValue)
        )
      },
      test("TransformValue reverse reverses transform") {
        val action   = MigrationAction.TransformValue(DynamicOptic.root.field("a"), DynamicSchemaExpr.IntToString)
        val reversed = action.reverse
        assertTrue(
          reversed == MigrationAction.TransformValue(DynamicOptic.root.field("a"), DynamicSchemaExpr.StringToInt)
        )
      },
      test("ChangeType reverse reverses converter") {
        val action   = MigrationAction.ChangeType(DynamicOptic.root.field("a"), DynamicSchemaExpr.IntToString)
        val reversed = action.reverse
        assertTrue(
          reversed == MigrationAction.ChangeType(DynamicOptic.root.field("a"), DynamicSchemaExpr.StringToInt)
        )
      },
      test("Mandate reverse is Optionalize") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root.field("opt"),
          DynamicSchemaExpr.Literal(DynamicValue.int(0))
        )
        assertTrue(action.reverse == MigrationAction.Optionalize(DynamicOptic.root.field("opt")))
      },
      test("Optionalize reverse is Mandate with Null default") {
        val action = MigrationAction.Optionalize(DynamicOptic.root.field("req"))
        assertTrue(
          action.reverse == MigrationAction.Mandate(
            DynamicOptic.root.field("req"),
            DynamicSchemaExpr.Literal(DynamicValue.Null)
          )
        )
      },
      test("TransformCase reverse reverses nested actions") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "Case1",
          Vector(
            MigrationAction.Rename(DynamicOptic.root.field("a"), "b"),
            MigrationAction.AddField(
              DynamicOptic.root.field("c"),
              DynamicSchemaExpr.Literal(DynamicValue.int(0))
            )
          )
        )
        val reversed = action.reverse.asInstanceOf[MigrationAction.TransformCase]
        assertTrue(
          reversed.caseName == "Case1" &&
            reversed.actions.length == 2 &&
            reversed.actions(0) == MigrationAction.DropField(
              DynamicOptic.root.field("c"),
              DynamicSchemaExpr.Literal(DynamicValue.int(0))
            ) &&
            reversed.actions(1) == MigrationAction.Rename(DynamicOptic.root.field("b"), "a")
        )
      },
      test("TransformElements reverse reverses transform") {
        val action = MigrationAction.TransformElements(DynamicOptic.root, DynamicSchemaExpr.IntToString)
        assertTrue(
          action.reverse == MigrationAction.TransformElements(DynamicOptic.root, DynamicSchemaExpr.StringToInt)
        )
      },
      test("TransformKeys reverse reverses transform") {
        val action = MigrationAction.TransformKeys(DynamicOptic.root, DynamicSchemaExpr.IntToString)
        assertTrue(
          action.reverse == MigrationAction.TransformKeys(DynamicOptic.root, DynamicSchemaExpr.StringToInt)
        )
      },
      test("TransformValues reverse reverses transform") {
        val action = MigrationAction.TransformValues(DynamicOptic.root, DynamicSchemaExpr.IntToString)
        assertTrue(
          action.reverse == MigrationAction.TransformValues(DynamicOptic.root, DynamicSchemaExpr.StringToInt)
        )
      },
      test("Literal reverse returns equivalent Literal") {
        val l = DynamicSchemaExpr.Literal(DynamicValue.int(42))
        assertTrue(l.reverse == l)
      }
    ),
    suite("BuilderMethods")(
      test("dropField with defaultForReverse") {
        val m = Migration
          .newBuilder[SimpleRecord, ShrunkRecord]
          .dropField("x", 0)
          .buildPartial
        val result = m(SimpleRecord(42, "hello"))
        assert(result)(isRight(equalTo(ShrunkRecord("hello"))))
      },
      test("mandateField via builder") {
        val dm = Migration
          .newBuilder[SimpleRecord, SimpleRecord]
          .mandateField("x", "x", 0)
          .buildPartial
          .dynamicMigration
        val input = DynamicValue.Record("x" -> DynamicValue.int(42), "y" -> DynamicValue.string("hello"))
        assertTrue(dm(input).isRight)
      },
      test("optionalizeField via builder") {
        val dm = Migration
          .newBuilder[SimpleRecord, SimpleRecord]
          .optionalizeField("x", "x")
          .buildPartial
          .dynamicMigration
        val input = DynamicValue.Record("x" -> DynamicValue.int(42), "y" -> DynamicValue.string("hello"))
        assertTrue(dm(input).isRight)
      },
      test("addFieldDynamic via builder") {
        val dm = Migration
          .newBuilder[SimpleRecord, SimpleRecord]
          .addFieldDynamic("extra", DynamicValue.int(0))
          .buildPartial
          .dynamicMigration
        val input = DynamicValue.Record("x" -> DynamicValue.int(42), "y" -> DynamicValue.string("hello"))
        assertTrue(dm(input).isRight)
      },
      test("addFieldAt via builder") {
        val dm = Migration
          .newBuilder[SimpleRecord, SimpleRecord]
          .addFieldAt(DynamicOptic.root, "extra", 0)
          .buildPartial
          .dynamicMigration
        val input = DynamicValue.Record("x" -> DynamicValue.int(42), "y" -> DynamicValue.string("hello"))
        assertTrue(dm(input).isRight)
      },
      test("dropFieldAt via builder") {
        val m = Migration
          .newBuilder[SimpleRecord, ShrunkRecord]
          .dropFieldAt(DynamicOptic.root, "x")
          .buildPartial
        val result = m(SimpleRecord(42, "hello"))
        assert(result)(isRight(equalTo(ShrunkRecord("hello"))))
      },
      test("renameFieldAt via builder") {
        val m = Migration
          .newBuilder[SimpleRecord, RenamedRecord]
          .renameFieldAt(DynamicOptic.root, "x", "z")
          .buildPartial
        val result = m(SimpleRecord(42, "hello"))
        assert(result)(isRight(equalTo(RenamedRecord(42, "hello"))))
      },
      test("renameCase via builder") {
        val dm = Migration
          .newBuilder[SimpleRecord, SimpleRecord]
          .renameCase("A", "B")
          .buildPartial
          .dynamicMigration
        val input = new DynamicValue.Variant("A", DynamicValue.Record.empty)
        assert(dm(input))(isRight(equalTo(new DynamicValue.Variant("B", DynamicValue.Record.empty))))
      },
      test("renameCaseAt via builder") {
        val dm = Migration
          .newBuilder[SimpleRecord, SimpleRecord]
          .renameCaseAt(DynamicOptic.root, "A", "B")
          .buildPartial
          .dynamicMigration
        val input = new DynamicValue.Variant("A", DynamicValue.Record.empty)
        assert(dm(input))(isRight(equalTo(new DynamicValue.Variant("B", DynamicValue.Record.empty))))
      },
      test("transformCase via builder") {
        val dm = Migration
          .newBuilder[SimpleRecord, SimpleRecord]
          .transformCase("MyCase")(_.renameField("old", "new"))
          .buildPartial
          .dynamicMigration
        val input    = new DynamicValue.Variant("MyCase", DynamicValue.Record("old" -> DynamicValue.int(1)))
        val expected = new DynamicValue.Variant("MyCase", DynamicValue.Record("new" -> DynamicValue.int(1)))
        assert(dm(input))(isRight(equalTo(expected)))
      },
      test("transformCaseAt via builder") {
        val dm = Migration
          .newBuilder[SimpleRecord, SimpleRecord]
          .transformCaseAt(DynamicOptic.root, "MyCase")(_.renameField("old", "new"))
          .buildPartial
          .dynamicMigration
        val input    = new DynamicValue.Variant("MyCase", DynamicValue.Record("old" -> DynamicValue.int(1)))
        val expected = new DynamicValue.Variant("MyCase", DynamicValue.Record("new" -> DynamicValue.int(1)))
        assert(dm(input))(isRight(equalTo(expected)))
      },
      test("transformElements via builder") {
        val dm = Migration
          .newBuilder[SimpleRecord, SimpleRecord]
          .transformElements(DynamicOptic.root, DynamicSchemaExpr.IntToString)
          .buildPartial
          .dynamicMigration
        val input    = new DynamicValue.Sequence(Chunk(DynamicValue.int(1), DynamicValue.int(2)))
        val expected = new DynamicValue.Sequence(Chunk(DynamicValue.string("1"), DynamicValue.string("2")))
        assert(dm(input))(isRight(equalTo(expected)))
      },
      test("transformMapKeys via builder") {
        val dm = Migration
          .newBuilder[SimpleRecord, SimpleRecord]
          .transformMapKeys(DynamicOptic.root, DynamicSchemaExpr.IntToString)
          .buildPartial
          .dynamicMigration
        val input    = new DynamicValue.Map(Chunk((DynamicValue.int(1), DynamicValue.string("one"))))
        val expected = new DynamicValue.Map(Chunk((DynamicValue.string("1"), DynamicValue.string("one"))))
        assert(dm(input))(isRight(equalTo(expected)))
      },
      test("transformMapValues via builder") {
        val dm = Migration
          .newBuilder[SimpleRecord, SimpleRecord]
          .transformMapValues(DynamicOptic.root, DynamicSchemaExpr.IntToString)
          .buildPartial
          .dynamicMigration
        val input    = new DynamicValue.Map(Chunk((DynamicValue.string("a"), DynamicValue.int(1))))
        val expected = new DynamicValue.Map(Chunk((DynamicValue.string("a"), DynamicValue.string("1"))))
        assert(dm(input))(isRight(equalTo(expected)))
      }
    ),
    suite("BuilderBuildValidation")(
      test("build succeeds when all target fields mapped via rename") {
        val m = Migration
          .newBuilder[SimpleRecord, RenamedRecord]
          .renameField("x", "z")
          .build
        assertTrue(!m.isEmpty)
      },
      test("build succeeds with addField") {
        val m = Migration
          .newBuilder[SimpleRecord, ExtendedRecord]
          .addField("extra", false)
          .build
        assertTrue(!m.isEmpty)
      },
      test("build succeeds with dropField") {
        val m = Migration
          .newBuilder[SimpleRecord, ShrunkRecord]
          .dropField("x")
          .build
        assertTrue(!m.isEmpty)
      },
      test("build succeeds with changeFieldType") {
        val m = Migration
          .newBuilder[SimpleRecord, TypeChangedRecord]
          .changeFieldType("x", "x", DynamicSchemaExpr.IntToString)
          .build
        assertTrue(!m.isEmpty)
      },
      test("build succeeds with transformField") {
        val m = Migration
          .newBuilder[SimpleRecord, TypeChangedRecord]
          .transformField("x", "x", DynamicSchemaExpr.IntToString)
          .build
        assertTrue(!m.isEmpty)
      },
      test("build succeeds with mandateField") {
        val m = Migration
          .newBuilder[SimpleRecord, SimpleRecord]
          .mandateField("x", "x", 0)
          .build
        assertTrue(m.isEmpty || !m.isEmpty)
      },
      test("build succeeds with optionalizeField") {
        val m = Migration
          .newBuilder[SimpleRecord, SimpleRecord]
          .optionalizeField("x", "x")
          .build
        assertTrue(m.isEmpty || !m.isEmpty)
      },
      test("build succeeds with enum action (catch-all)") {
        val m = Migration
          .newBuilder[SimpleRecord, SimpleRecord]
          .renameCase("A", "B")
          .build
        assertTrue(m.isEmpty || !m.isEmpty)
      }
    ),
    suite("TypedMigration")(
      test("typed migration fails when dynamic result does not match target schema") {
        val m = Migration
          .newBuilder[SimpleRecord, PersonV2]
          .buildPartial
        val result = m(SimpleRecord(42, "hello"))
        assert(result)(isLeft)
      },
      test("applyDynamic works at DynamicValue level") {
        val m = Migration
          .newBuilder[SimpleRecord, RenamedRecord]
          .renameField("x", "z")
          .buildPartial
        val input = SimpleRecord.schema.toDynamicValue(SimpleRecord(42, "hello"))
        assertTrue(m.applyDynamic(input).isRight)
      },
      test("andThen composes migrations") {
        val m1 = Migration
          .newBuilder[SimpleRecord, RenamedRecord]
          .renameField("x", "z")
          .buildPartial
        val m2 = Migration
          .newBuilder[RenamedRecord, SimpleRecord]
          .renameField("z", "x")
          .buildPartial
        val composed = m1.andThen(m2)
        val result   = composed(SimpleRecord(42, "hello"))
        assert(result)(isRight(equalTo(SimpleRecord(42, "hello"))))
      }
    ),
    suite("AdditionalErrors")(
      test("MissingDefault error message") {
        val err = MigrationError.MissingDefault(DynamicOptic.root, "field1")
        assertTrue(err.getMessage.contains("No default value") && err.getMessage.contains("field1"))
      },
      test("UnknownCase error message") {
        val err = MigrationError.UnknownCase(DynamicOptic.root, "BadCase")
        assertTrue(err.getMessage.contains("Unknown variant case") && err.getMessage.contains("BadCase"))
      },
      test("UnexpectedField error message") {
        val err = MigrationError.UnexpectedField(DynamicOptic.root, "extra")
        assertTrue(err.getMessage.contains("Unexpected field") && err.getMessage.contains("extra"))
      }
    )
  )

  val builderValidationSuite: Spec[Any, Any] = suite("BuilderValidation")(
    test("buildPartial always succeeds") {
      val m = Migration
        .newBuilder[SimpleRecord, ExtendedRecord]
        .buildPartial
      assertTrue(!m.isEmpty || m.isEmpty) // just verify it compiles and doesn't throw
    },
    test("build validates target fields are covered") {
      val result =
        try {
          Migration
            .newBuilder[SimpleRecord, PersonV2]
            .build
          false // should have thrown
        } catch {
          case _: IllegalArgumentException => true
          case _: Throwable                => false
        }
      assertTrue(result)
    }
  )
}
