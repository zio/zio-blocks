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
    primitiveTransformEdgeCases,
    nestedPathSuite,
    errorMessageSuite,
    changeTypeEdgeCases,
    builderValidationSuite
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
          DynamicOptic.root,
          "x",
          new DynamicValue.Primitive(new PrimitiveValue.Int(0))
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
        MigrationAction.DropField(DynamicOptic.root, "nonexistent", None)
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
        MigrationAction.Rename(DynamicOptic.root, "nonexistent", "z")
      )
      val input = DynamicValue.Record("x" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)))
      assert(dm(input))(isLeft)
    }
  )

  val transformFieldSuite: Spec[Any, Any] = suite("TransformField")(
    test("transform a field value with a constant") {
      val dm = DynamicMigration(
        Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root,
            "x",
            "x",
            PrimitiveTransform.Const(new DynamicValue.Primitive(new PrimitiveValue.Int(99)))
          )
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
        .changeFieldType("x", "x", PrimitiveTransform.IntToString)
        .buildPartial
      val result = m(SimpleRecord(42, "hello"))
      assert(result)(isRight(equalTo(TypeChangedRecord("42", "hello"))))
    },
    test("IntToString and StringToInt are inverses") {
      val intVal = new DynamicValue.Primitive(new PrimitiveValue.Int(42))
      val result = for {
        strVal  <- PrimitiveTransform.IntToString(intVal)
        backInt <- PrimitiveTransform.StringToInt(strVal)
      } yield backInt
      assert(result)(isRight(equalTo(intVal)))
    },
    test("LongToString and StringToLong are inverses") {
      val longVal = new DynamicValue.Primitive(new PrimitiveValue.Long(123456789L))
      val result  = for {
        strVal  <- PrimitiveTransform.LongToString(longVal)
        backLng <- PrimitiveTransform.StringToLong(strVal)
      } yield backLng
      assert(result)(isRight(equalTo(longVal)))
    },
    test("BooleanToString and StringToBoolean are inverses") {
      val boolVal = new DynamicValue.Primitive(new PrimitiveValue.Boolean(true))
      val result  = for {
        strVal   <- PrimitiveTransform.BooleanToString(boolVal)
        backBool <- PrimitiveTransform.StringToBoolean(strVal)
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
        MigrationAction.Rename(DynamicOptic.root, "extra", "flag")
      )
      val composed = dm1 ++ dm2

      val input  = SimpleRecord.schema.toDynamicValue(SimpleRecord(1, "a"))
      val result = composed(input)
      assertTrue(result.isRight)
    },
    test("associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
      val m1 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
      val m2 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "b", "c"))
      val m3 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))

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
      val m     = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
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
          DynamicOptic.root,
          "extra",
          new DynamicValue.Primitive(new PrimitiveValue.Boolean(false))
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
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.AddField(
            DynamicOptic.root,
            "c",
            new DynamicValue.Primitive(new PrimitiveValue.Int(0))
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
          MigrationAction.Rename(DynamicOptic.root, "first", "firstName"),
          MigrationAction.Rename(DynamicOptic.root, "last", "lastName")
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
          Vector(MigrationAction.Rename(DynamicOptic.root, "old", "new"))
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
        MigrationAction.Rename(DynamicOptic.root, "nonexistent", "z")
      )
      val input  = DynamicValue.Record("x" -> new DynamicValue.Primitive(new PrimitiveValue.Int(1)))
      val result = dm(input)
      assert(result)(isLeft(hasField("path", (e: MigrationError) => e.path.toString, anything)))
    },
    test("type mismatch error on non-record value") {
      val dm = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root, "x", "y")
      )
      val input  = new DynamicValue.Primitive(new PrimitiveValue.Int(1))
      val result = dm(input)
      assert(result)(isLeft)
    },
    test("conversion failure error on bad type change") {
      val result = PrimitiveTransform.StringToInt(
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
          DynamicOptic.root,
          "y",
          new DynamicValue.Primitive(new PrimitiveValue.String("default"))
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
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.AddField(
            DynamicOptic.root,
            "c",
            new DynamicValue.Primitive(new PrimitiveValue.Boolean(true))
          ),
          MigrationAction.DropField(DynamicOptic.root, "d", None)
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
        MigrationAction.TransformElements(DynamicOptic.root, PrimitiveTransform.IntToString)
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
        .changeFieldType(fromOptic, toOptic, PrimitiveTransform.IntToString)
        .buildPartial
      val result = m(SimpleRecord(42, "hello"))
      assert(result)(isRight(equalTo(TypeChangedRecord("42", "hello"))))
    }
  )

  val mandateOptionalSuite: Spec[Any, Any] = suite("MandateAndOptionalize")(
    test("mandate replaces Null with default") {
      val dm = DynamicMigration.single(
        MigrationAction.Mandate(DynamicOptic.root, "opt", "req", DynamicValue.int(0))
      )
      val input    = DynamicValue.Record("opt" -> DynamicValue.Null)
      val expected = DynamicValue.Record("req" -> DynamicValue.int(0))
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("mandate extracts value from Some variant") {
      val dm = DynamicMigration.single(
        MigrationAction.Mandate(DynamicOptic.root, "opt", "req", DynamicValue.int(0))
      )
      val input    = DynamicValue.Record("opt" -> new DynamicValue.Variant("Some", DynamicValue.int(42)))
      val expected = DynamicValue.Record("req" -> DynamicValue.int(42))
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("mandate uses default for None variant") {
      val dm = DynamicMigration.single(
        MigrationAction.Mandate(DynamicOptic.root, "opt", "req", DynamicValue.int(0))
      )
      val input    = DynamicValue.Record("opt" -> new DynamicValue.Variant("None", DynamicValue.Record.empty))
      val expected = DynamicValue.Record("req" -> DynamicValue.int(0))
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("mandate passes through non-Option variant") {
      val dm = DynamicMigration.single(
        MigrationAction.Mandate(DynamicOptic.root, "opt", "req", DynamicValue.int(0))
      )
      val input    = DynamicValue.Record("opt" -> new DynamicValue.Variant("Active", DynamicValue.int(1)))
      val expected = DynamicValue.Record("req" -> new DynamicValue.Variant("Active", DynamicValue.int(1)))
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("mandate passes through regular value") {
      val dm = DynamicMigration.single(
        MigrationAction.Mandate(DynamicOptic.root, "opt", "req", DynamicValue.int(0))
      )
      val input    = DynamicValue.Record("opt" -> DynamicValue.int(42))
      val expected = DynamicValue.Record("req" -> DynamicValue.int(42))
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("mandate fails on missing field") {
      val dm = DynamicMigration.single(
        MigrationAction.Mandate(DynamicOptic.root, "missing", "req", DynamicValue.int(0))
      )
      val input = DynamicValue.Record("other" -> DynamicValue.int(1))
      assert(dm(input))(isLeft)
    },
    test("optionalize passes through non-null") {
      val dm = DynamicMigration.single(
        MigrationAction.Optionalize(DynamicOptic.root, "req", "opt")
      )
      val input    = DynamicValue.Record("req" -> DynamicValue.int(42))
      val expected = DynamicValue.Record("opt" -> DynamicValue.int(42))
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("optionalize passes through Null") {
      val dm = DynamicMigration.single(
        MigrationAction.Optionalize(DynamicOptic.root, "req", "opt")
      )
      val input    = DynamicValue.Record("req" -> DynamicValue.Null)
      val expected = DynamicValue.Record("opt" -> DynamicValue.Null)
      assert(dm(input))(isRight(equalTo(expected)))
    },
    test("optionalize fails on missing field") {
      val dm = DynamicMigration.single(
        MigrationAction.Optionalize(DynamicOptic.root, "missing", "opt")
      )
      val input = DynamicValue.Record("other" -> DynamicValue.int(1))
      assert(dm(input))(isLeft)
    },
    test("mandate and optionalize are inverses") {
      val mandate = DynamicMigration.single(
        MigrationAction.Mandate(DynamicOptic.root, "opt", "req", DynamicValue.int(0))
      )
      assertTrue(mandate.reverse.actions.head.isInstanceOf[MigrationAction.Optionalize])
    }
  )

  val mapTransformsSuite: Spec[Any, Any] = suite("MapTransforms")(
    test("transform map keys") {
      val dm = DynamicMigration.single(
        MigrationAction.TransformKeys(DynamicOptic.root, PrimitiveTransform.IntToString)
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
        MigrationAction.TransformValues(DynamicOptic.root, PrimitiveTransform.IntToString)
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
        MigrationAction.TransformKeys(DynamicOptic.root, PrimitiveTransform.IntToString)
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
        MigrationAction.TransformValues(DynamicOptic.root, PrimitiveTransform.StringToInt)
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
        MigrationAction.TransformKeys(DynamicOptic.root, PrimitiveTransform.Identity)
      )
      val input = DynamicValue.int(42)
      assert(dm(input))(isLeft)
    }
  )

  val primitiveTransformEdgeCases: Spec[Any, Any] = suite("PrimitiveTransformEdgeCases")(
    test("IntToString fails on non-Int") {
      val result = PrimitiveTransform.IntToString(DynamicValue.string("hello"))
      assert(result)(isLeft)
    },
    test("StringToInt fails on non-String") {
      val result = PrimitiveTransform.StringToInt(DynamicValue.int(42))
      assert(result)(isLeft)
    },
    test("LongToString fails on non-Long") {
      val result = PrimitiveTransform.LongToString(DynamicValue.int(42))
      assert(result)(isLeft)
    },
    test("StringToLong fails on non-String") {
      val result = PrimitiveTransform.StringToLong(DynamicValue.int(42))
      assert(result)(isLeft)
    },
    test("StringToLong fails on unparseable string") {
      val result = PrimitiveTransform.StringToLong(DynamicValue.string("not_a_long"))
      assert(result)(isLeft)
    },
    test("IntToLong widening") {
      val result = PrimitiveTransform.IntToLong(new DynamicValue.Primitive(new PrimitiveValue.Int(42)))
      assert(result)(isRight(equalTo(new DynamicValue.Primitive(new PrimitiveValue.Long(42L)))))
    },
    test("LongToInt narrowing") {
      val result = PrimitiveTransform.LongToInt(new DynamicValue.Primitive(new PrimitiveValue.Long(42L)))
      assert(result)(isRight(equalTo(new DynamicValue.Primitive(new PrimitiveValue.Int(42)))))
    },
    test("IntToLong fails on non-Int") {
      val result = PrimitiveTransform.IntToLong(DynamicValue.string("hi"))
      assert(result)(isLeft)
    },
    test("LongToInt fails on non-Long") {
      val result = PrimitiveTransform.LongToInt(DynamicValue.string("hi"))
      assert(result)(isLeft)
    },
    test("BooleanToString fails on non-Boolean") {
      val result = PrimitiveTransform.BooleanToString(DynamicValue.int(1))
      assert(result)(isLeft)
    },
    test("StringToBoolean fails on non-String") {
      val result = PrimitiveTransform.StringToBoolean(DynamicValue.int(1))
      assert(result)(isLeft)
    },
    test("StringToBoolean fails on invalid boolean string") {
      val result = PrimitiveTransform.StringToBoolean(DynamicValue.string("maybe"))
      assert(result)(isLeft)
    },
    test("StringToBoolean accepts 'false'") {
      val result = PrimitiveTransform.StringToBoolean(DynamicValue.string("false"))
      assert(result)(isRight(equalTo(new DynamicValue.Primitive(new PrimitiveValue.Boolean(false)))))
    },
    test("Identity passes any value through") {
      val v = DynamicValue.int(42)
      assert(PrimitiveTransform.Identity(v))(isRight(equalTo(v)))
    },
    test("Identity reverse is itself") {
      assertTrue(PrimitiveTransform.Identity.reverse == PrimitiveTransform.Identity)
    },
    test("IntToLong and LongToInt are inverses") {
      val intVal = new DynamicValue.Primitive(new PrimitiveValue.Int(42))
      val result = for {
        l <- PrimitiveTransform.IntToLong(intVal)
        i <- PrimitiveTransform.LongToInt(l)
      } yield i
      assert(result)(isRight(equalTo(intVal)))
    }
  )

  val nestedPathSuite: Spec[Any, Any] = suite("NestedPathOperations")(
    test("rename field at nested path") {
      val path = DynamicOptic.root.field("inner")
      val dm   = DynamicMigration.single(
        MigrationAction.Rename(path, "old", "new")
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
      val path = DynamicOptic.root.field("missing")
      val dm   = DynamicMigration.single(
        MigrationAction.Rename(path, "a", "b")
      )
      val input = DynamicValue.Record("other" -> DynamicValue.int(1))
      assert(dm(input))(isLeft)
    },
    test("nested path fails on non-record intermediate") {
      val path = DynamicOptic.root.field("inner")
      val dm   = DynamicMigration.single(
        MigrationAction.Rename(path, "a", "b")
      )
      val input = DynamicValue.Record("inner" -> DynamicValue.int(1))
      assert(dm(input))(isLeft)
    },
    test("transform elements fails on non-sequence") {
      val dm = DynamicMigration.single(
        MigrationAction.TransformElements(DynamicOptic.root, PrimitiveTransform.Identity)
      )
      val input = DynamicValue.int(42)
      assert(dm(input))(isLeft)
    },
    test("rename on non-record fails") {
      val dm = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root, "x", "y")
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
        MigrationAction.ChangeType(DynamicOptic.root, "x", "x", PrimitiveTransform.StringToInt)
      )
      val input = DynamicValue.Record("x" -> DynamicValue.string("not_a_number"))
      assert(dm(input))(isLeft)
    },
    test("changeType fails on missing field") {
      val dm = DynamicMigration.single(
        MigrationAction.ChangeType(DynamicOptic.root, "missing", "y", PrimitiveTransform.IntToString)
      )
      val input = DynamicValue.Record("x" -> DynamicValue.int(1))
      assert(dm(input))(isLeft)
    },
    test("transformField fails on transform error") {
      val dm = DynamicMigration.single(
        MigrationAction.TransformValue(DynamicOptic.root, "x", "x", PrimitiveTransform.StringToInt)
      )
      val input = DynamicValue.Record("x" -> DynamicValue.string("bad"))
      assert(dm(input))(isLeft)
    },
    test("transformField fails on missing field") {
      val dm = DynamicMigration.single(
        MigrationAction.TransformValue(DynamicOptic.root, "missing", "x", PrimitiveTransform.Identity)
      )
      val input = DynamicValue.Record("x" -> DynamicValue.int(1))
      assert(dm(input))(isLeft)
    }
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
