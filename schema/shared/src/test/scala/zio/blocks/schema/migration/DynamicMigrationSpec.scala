package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaBaseSpec}
import zio.test.Assertion._
import zio.test._

object DynamicMigrationSpec extends SchemaBaseSpec {

  private def prim(s: String): DynamicValue   = new DynamicValue.Primitive(new PrimitiveValue.String(s))
  private def primInt(i: Int): DynamicValue    = new DynamicValue.Primitive(new PrimitiveValue.Int(i))
  private def primLong(l: Long): DynamicValue  = new DynamicValue.Primitive(new PrimitiveValue.Long(l))
  private def primBool(b: Boolean): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Boolean(b))

  private def record(fields: (String, DynamicValue)*): DynamicValue =
    new DynamicValue.Record(Chunk.fromIterable(fields))

  private def variant(caseName: String, value: DynamicValue): DynamicValue =
    new DynamicValue.Variant(caseName, value)

  private def seq(elements: DynamicValue*): DynamicValue =
    new DynamicValue.Sequence(Chunk.fromIterable(elements))

  private def map(entries: (DynamicValue, DynamicValue)*): DynamicValue =
    new DynamicValue.Map(Chunk.fromIterable(entries))

  def spec: Spec[TestEnvironment, Any] = suite("DynamicMigrationSpec")(
    identitySuite,
    addFieldSuite,
    dropFieldSuite,
    renameSuite,
    transformValueSuite,
    mandateSuite,
    optionalizeSuite,
    changeTypeSuite,
    joinSuite,
    splitSuite,
    renameCaseSuite,
    transformCaseSuite,
    transformElementsSuite,
    transformKeysSuite,
    transformValuesSuite,
    compositionSuite,
    reverseSuite,
    lawsSuite
  )

  private val identitySuite = suite("identity")(
    test("identity migration returns value unchanged") {
      val v = record("name" -> prim("Alice"), "age" -> primInt(30))
      assert(DynamicMigration.identity(v))(isRight(equalTo(v)))
    }
  )

  private val addFieldSuite = suite("AddField")(
    test("adds a new field to a record") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.AddField(DynamicOptic.root.field("age"), primInt(0))
      )
      val input  = record("name" -> prim("Alice"))
      val result = migration(input)
      assert(result)(isRight(equalTo(
        record("name" -> prim("Alice"), "age" -> primInt(0))
      )))
    },
    test("fails when field already exists") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.AddField(DynamicOptic.root.field("name"), prim("Bob"))
      )
      val input = record("name" -> prim("Alice"))
      assert(migration(input))(isLeft)
    }
  )

  private val dropFieldSuite = suite("DropField")(
    test("removes a field from a record") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.DropField(DynamicOptic.root.field("age"), primInt(0))
      )
      val input  = record("name" -> prim("Alice"), "age" -> primInt(30))
      val result = migration(input)
      assert(result)(isRight(equalTo(
        record("name" -> prim("Alice"))
      )))
    },
    test("fails when field does not exist") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.DropField(DynamicOptic.root.field("missing"), prim(""))
      )
      val input = record("name" -> prim("Alice"))
      assert(migration(input))(isLeft)
    }
  )

  private val renameSuite = suite("Rename")(
    test("renames a field in a record") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.Rename(DynamicOptic.root, "firstName", "givenName")
      )
      val input  = record("firstName" -> prim("Alice"), "age" -> primInt(30))
      val result = migration(input)
      assert(result)(isRight(equalTo(
        record("givenName" -> prim("Alice"), "age" -> primInt(30))
      )))
    },
    test("fails when source field does not exist") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.Rename(DynamicOptic.root, "missing", "newName")
      )
      val input = record("name" -> prim("Alice"))
      assert(migration(input))(isLeft)
    }
  )

  private val transformValueSuite = suite("TransformValue")(
    test("transforms a value in-place using a constant transform") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.TransformValue(
          DynamicOptic.root.field("name"),
          DynamicOptic.root.field("name"),
          DynamicValueTransform.Constant(prim("Bob")),
          Some(DynamicValueTransform.Constant(prim("Alice")))
        )
      )
      val input  = record("name" -> prim("Alice"))
      val result = migration(input)
      assert(result)(isRight(equalTo(
        record("name" -> prim("Bob"))
      )))
    },
    test("transforms a value using NumericToString") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.TransformValue(
          DynamicOptic.root.field("count"),
          DynamicOptic.root.field("count"),
          DynamicValueTransform.NumericToString,
          Some(DynamicValueTransform.StringToInt)
        )
      )
      val input  = record("count" -> primInt(42))
      val result = migration(input)
      assert(result)(isRight(equalTo(
        record("count" -> prim("42"))
      )))
    }
  )

  private val mandateSuite = suite("Mandate")(
    test("converts a Null value to the default") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.Mandate(
          DynamicOptic.root.field("age"),
          DynamicOptic.root.field("age"),
          primInt(0)
        )
      )
      val input  = record("age" -> DynamicValue.Null)
      val result = migration(input)
      assert(result)(isRight(equalTo(
        record("age" -> primInt(0))
      )))
    },
    test("keeps non-null value unchanged") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.Mandate(
          DynamicOptic.root.field("age"),
          DynamicOptic.root.field("age"),
          primInt(0)
        )
      )
      val input  = record("age" -> primInt(25))
      val result = migration(input)
      assert(result)(isRight(equalTo(
        record("age" -> primInt(25))
      )))
    }
  )

  private val optionalizeSuite = suite("Optionalize")(
    test("wraps a value in Some variant") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.Optionalize(
          DynamicOptic.root.field("age"),
          DynamicOptic.root.field("age")
        )
      )
      val input  = record("age" -> primInt(25))
      val result = migration(input)
      assert(result)(isRight(equalTo(
        record("age" -> variant("Some", primInt(25)))
      )))
    },
    test("wraps Null in None variant") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.Optionalize(
          DynamicOptic.root.field("age"),
          DynamicOptic.root.field("age")
        )
      )
      val input  = record("age" -> DynamicValue.Null)
      val result = migration(input)
      assert(result)(isRight(equalTo(
        record("age" -> variant("None", record()))
      )))
    }
  )

  private val changeTypeSuite = suite("ChangeType")(
    test("changes Int to Long") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.ChangeType(
          DynamicOptic.root.field("count"),
          DynamicOptic.root.field("count"),
          DynamicValueTransform.IntToLong,
          Some(DynamicValueTransform.LongToInt)
        )
      )
      val input  = record("count" -> primInt(42))
      val result = migration(input)
      assert(result)(isRight(equalTo(
        record("count" -> primLong(42L))
      )))
    },
    test("changes String to Int") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.ChangeType(
          DynamicOptic.root.field("count"),
          DynamicOptic.root.field("count"),
          DynamicValueTransform.StringToInt,
          Some(DynamicValueTransform.NumericToString)
        )
      )
      val input  = record("count" -> prim("42"))
      val result = migration(input)
      assert(result)(isRight(equalTo(
        record("count" -> primInt(42))
      )))
    },
    test("fails on invalid conversion") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.ChangeType(
          DynamicOptic.root.field("count"),
          DynamicOptic.root.field("count"),
          DynamicValueTransform.StringToInt,
          None
        )
      )
      val input = record("count" -> prim("not-a-number"))
      assert(migration(input))(isLeft)
    }
  )

  private val joinSuite = suite("Join")(
    test("joins multiple fields into one using ConcatFields") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.Join(
          DynamicOptic.root,
          Vector("first", "last"),
          "fullName",
          DynamicValueTransform.ConcatFields(Vector("first", "last"), " "),
          None
        )
      )
      val input  = record("first" -> prim("John"), "last" -> prim("Doe"), "age" -> primInt(30))
      val result = migration(input)
      assert(result)(isRight(equalTo(
        record("age" -> primInt(30), "fullName" -> prim("John Doe"))
      )))
    },
    test("fails when source fields are empty") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.Join(
          DynamicOptic.root,
          Vector.empty,
          "combined",
          DynamicValueTransform.Identity,
          None
        )
      )
      val input = record("name" -> prim("Alice"))
      assert(migration(input))(isLeft)
    },
    test("fails on non-record value") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.Join(
          DynamicOptic.root,
          Vector("a", "b"),
          "ab",
          DynamicValueTransform.Identity,
          None
        )
      )
      assert(migration(prim("not a record")))(isLeft)
    }
  )

  private val splitSuite = suite("Split")(
    test("splits a field into multiple fields using SplitString") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.Split(
          DynamicOptic.root,
          "fullName",
          Vector("first", "last"),
          DynamicValueTransform.SplitString(" ", Vector("first", "last")),
          None
        )
      )
      val input  = record("fullName" -> prim("John Doe"), "age" -> primInt(30))
      val result = migration(input)
      assert(result)(isRight(equalTo(
        record("age" -> primInt(30), "first" -> prim("John"), "last" -> prim("Doe"))
      )))
    },
    test("fails when source field does not exist") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.Split(
          DynamicOptic.root,
          "missing",
          Vector("a", "b"),
          DynamicValueTransform.SplitString(" ", Vector("a", "b")),
          None
        )
      )
      val input = record("name" -> prim("Alice"))
      assert(migration(input))(isLeft)
    },
    test("Join and Split are inverses") {
      val join = DynamicMigration.fromAction(
        MigrationAction.Join(
          DynamicOptic.root,
          Vector("first", "last"),
          "fullName",
          DynamicValueTransform.ConcatFields(Vector("first", "last"), " "),
          Some(DynamicValueTransform.SplitString(" ", Vector("first", "last")))
        )
      )
      val input     = record("first" -> prim("John"), "last" -> prim("Doe"))
      val joined    = join(input)
      val splitBack = joined.flatMap(join.reverse.apply)
      assert(splitBack)(isRight(equalTo(input)))
    }
  )

  private val renameCaseSuite = suite("RenameCase")(
    test("renames a variant case") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
      )
      val input  = variant("OldCase", record("x" -> primInt(1)))
      val result = migration(input)
      assert(result)(isRight(equalTo(
        variant("NewCase", record("x" -> primInt(1)))
      )))
    },
    test("leaves non-matching cases unchanged") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
      )
      val input  = variant("OtherCase", record("x" -> primInt(1)))
      val result = migration(input)
      assert(result)(isRight(equalTo(input)))
    }
  )

  private val transformCaseSuite = suite("TransformCase")(
    test("transforms a specific variant case with sub-actions") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.TransformCase(
          DynamicOptic.root,
          "PersonV1",
          "PersonV2",
          Vector(
            MigrationAction.AddField(DynamicOptic.root.field("email"), prim(""))
          )
        )
      )
      val input  = variant("PersonV1", record("name" -> prim("Alice")))
      val result = migration(input)
      assert(result)(isRight(equalTo(
        variant("PersonV2", record("name" -> prim("Alice"), "email" -> prim("")))
      )))
    },
    test("leaves non-matching cases unchanged") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.TransformCase(
          DynamicOptic.root,
          "PersonV1",
          "PersonV2",
          Vector(MigrationAction.AddField(DynamicOptic.root.field("email"), prim("")))
        )
      )
      val input  = variant("CompanyV1", record("name" -> prim("Acme")))
      val result = migration(input)
      assert(result)(isRight(equalTo(input)))
    }
  )

  private val transformElementsSuite = suite("TransformElements")(
    test("transforms all elements of a sequence") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.TransformElements(
          DynamicOptic.root,
          DynamicValueTransform.NumericToString,
          Some(DynamicValueTransform.StringToInt)
        )
      )
      val input  = seq(primInt(1), primInt(2), primInt(3))
      val result = migration(input)
      assert(result)(isRight(equalTo(
        seq(prim("1"), prim("2"), prim("3"))
      )))
    }
  )

  private val transformKeysSuite = suite("TransformKeys")(
    test("transforms all keys of a map") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.TransformKeys(
          DynamicOptic.root,
          DynamicValueTransform.NumericToString,
          Some(DynamicValueTransform.StringToInt)
        )
      )
      val input  = map(primInt(1) -> prim("a"), primInt(2) -> prim("b"))
      val result = migration(input)
      assert(result)(isRight(equalTo(
        map(prim("1") -> prim("a"), prim("2") -> prim("b"))
      )))
    }
  )

  private val transformValuesSuite = suite("TransformValues")(
    test("transforms all values of a map") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.TransformValues(
          DynamicOptic.root,
          DynamicValueTransform.NumericToString,
          Some(DynamicValueTransform.StringToInt)
        )
      )
      val input  = map(prim("a") -> primInt(1), prim("b") -> primInt(2))
      val result = migration(input)
      assert(result)(isRight(equalTo(
        map(prim("a") -> prim("1"), prim("b") -> prim("2"))
      )))
    }
  )

  private val compositionSuite = suite("composition")(
    test("++ composes migrations sequentially") {
      val m1 = DynamicMigration.fromAction(
        MigrationAction.AddField(DynamicOptic.root.field("age"), primInt(0))
      )
      val m2 = DynamicMigration.fromAction(
        MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
      )
      val composed = m1 ++ m2
      val input    = record("name" -> prim("Alice"))
      val result   = composed(input)
      assert(result)(isRight(equalTo(
        record("fullName" -> prim("Alice"), "age" -> primInt(0))
      )))
    },
    test("complex multi-step migration") {
      val migration = DynamicMigration.fromActions(
        MigrationAction.Rename(DynamicOptic.root, "firstName", "name"),
        MigrationAction.DropField(DynamicOptic.root.field("middleName"), prim("")),
        MigrationAction.AddField(DynamicOptic.root.field("email"), prim(""))
      )
      val input = record(
        "firstName"  -> prim("Alice"),
        "middleName" -> prim("M"),
        "lastName"   -> prim("Smith")
      )
      val result = migration(input)
      assert(result)(isRight(equalTo(
        record(
          "name"     -> prim("Alice"),
          "lastName" -> prim("Smith"),
          "email"    -> prim("")
        )
      )))
    }
  )

  private val reverseSuite = suite("reverse")(
    test("reverse of AddField is DropField") {
      val add = MigrationAction.AddField(DynamicOptic.root.field("age"), primInt(0))
      val rev = add.reverse
      assert(rev)(equalTo(MigrationAction.DropField(DynamicOptic.root.field("age"), primInt(0))))
    },
    test("reverse of DropField is AddField") {
      val drop = MigrationAction.DropField(DynamicOptic.root.field("age"), primInt(0))
      val rev  = drop.reverse
      assert(rev)(equalTo(MigrationAction.AddField(DynamicOptic.root.field("age"), primInt(0))))
    },
    test("reverse of Rename swaps names") {
      val rename = MigrationAction.Rename(DynamicOptic.root, "old", "new")
      val rev    = rename.reverse
      assert(rev)(equalTo(MigrationAction.Rename(DynamicOptic.root, "new", "old")))
    },
    test("reverse of RenameCase swaps names") {
      val rename = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
      val rev    = rename.reverse
      assert(rev)(equalTo(MigrationAction.RenameCase(DynamicOptic.root, "NewCase", "OldCase")))
    },
    test("double reverse returns original migration") {
      val migration = DynamicMigration.fromActions(
        MigrationAction.AddField(DynamicOptic.root.field("age"), primInt(0)),
        MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
      )
      assert(migration.reverse.reverse)(equalTo(migration))
    },
    test("reverse migration undoes add/drop") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.AddField(DynamicOptic.root.field("age"), primInt(0))
      )
      val input  = record("name" -> prim("Alice"))
      val result = migration(input)
      assert(result.flatMap(migration.reverse.apply))(isRight(equalTo(input)))
    },
    test("reverse migration undoes rename") {
      val migration = DynamicMigration.fromAction(
        MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
      )
      val input  = record("name" -> prim("Alice"))
      val result = migration(input)
      assert(result.flatMap(migration.reverse.apply))(isRight(equalTo(input)))
    }
  )

  private val lawsSuite = suite("laws")(
    test("identity law: identity migration returns value unchanged") {
      val v = record("name" -> prim("Alice"), "age" -> primInt(30))
      assert(DynamicMigration.identity(v))(isRight(equalTo(v)))
    },
    test("associativity law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
      val m1 = DynamicMigration.fromAction(
        MigrationAction.AddField(DynamicOptic.root.field("x"), primInt(1))
      )
      val m2 = DynamicMigration.fromAction(
        MigrationAction.AddField(DynamicOptic.root.field("y"), primInt(2))
      )
      val m3 = DynamicMigration.fromAction(
        MigrationAction.AddField(DynamicOptic.root.field("z"), primInt(3))
      )
      val input = record("a" -> prim("hello"))
      val left  = ((m1 ++ m2) ++ m3)(input)
      val right = (m1 ++ (m2 ++ m3))(input)
      assert(left)(equalTo(right))
    },
    test("structural reverse law: m.reverse.reverse == m") {
      val migration = DynamicMigration.fromActions(
        MigrationAction.AddField(DynamicOptic.root.field("age"), primInt(0)),
        MigrationAction.Rename(DynamicOptic.root, "name", "fullName"),
        MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
      )
      assert(migration.reverse.reverse)(equalTo(migration))
    }
  )
}
