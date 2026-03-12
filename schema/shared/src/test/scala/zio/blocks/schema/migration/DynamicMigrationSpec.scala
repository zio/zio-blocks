package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaBaseSpec}
import zio.test.Assertion._
import zio.test._

object DynamicMigrationSpec extends SchemaBaseSpec {

  private def intDV(n: Int): DynamicValue      = DynamicValue.Primitive(PrimitiveValue.Int(n))
  private def strDV(s: String): DynamicValue   = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def longDV(l: Long): DynamicValue    = DynamicValue.Primitive(PrimitiveValue.Long(l))
  private def boolDV(b: Boolean): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Boolean(b))

  private def rec(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(Chunk(fields: _*))

  private val defaultIntExpr = DynamicSchemaExpr.Literal(intDV(0))
  private val defaultStrExpr = DynamicSchemaExpr.Literal(strDV(""))

  def spec: Spec[TestEnvironment, Any] = suite("DynamicMigrationSpec")(
    addFieldSuite,
    dropFieldSuite,
    renameSuite,
    transformValueSuite,
    mandateSuite,
    optionalizeSuite,
    changeTypeSuite,
    renameCaseSuite,
    transformCaseSuite,
    applyMigrationSuite,
    transformElementsSuite,
    transformKeysSuite,
    transformValuesSuite,
    compositionSuite,
    reverseSuite,
    nestedPathSuite,
    edgeCasesSuite,
    lawsSuite
  )

  private val addFieldSuite = suite("AddField")(
    test("adds a field to an empty record") {
      val action = MigrationAction.AddField(DynamicOptic.root.field("x"), defaultIntExpr)
      val result = DynamicMigration.single(action)(DynamicValue.Record.empty)
      assert(result)(isRight(equalTo(rec("x" -> intDV(0)))))
    },
    test("adds a field to a record with existing fields") {
      val action = MigrationAction.AddField(DynamicOptic.root.field("age"), DynamicSchemaExpr.Literal(intDV(25)))
      val input  = rec("name" -> strDV("Alice"))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(rec("name" -> strDV("Alice"), "age" -> intDV(25)))))
    },
    test("fails if field already exists") {
      val action = MigrationAction.AddField(DynamicOptic.root.field("name"), defaultStrExpr)
      val input  = rec("name" -> strDV("Alice"))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isLeft)
    },
    test("fails on non-record input") {
      val action = MigrationAction.AddField(DynamicOptic.root.field("x"), defaultIntExpr)
      val result = DynamicMigration.single(action)(intDV(42))
      assert(result)(isLeft)
    }
  )

  private val dropFieldSuite = suite("DropField")(
    test("drops a field from a record") {
      val action = MigrationAction.DropField(DynamicOptic.root.field("age"), defaultIntExpr)
      val input  = rec("name" -> strDV("Alice"), "age" -> intDV(30))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(rec("name" -> strDV("Alice")))))
    },
    test("fails if field does not exist") {
      val action = MigrationAction.DropField(DynamicOptic.root.field("missing"), defaultIntExpr)
      val input  = rec("name" -> strDV("Alice"))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isLeft)
    },
    test("drops the only field leaving empty record") {
      val action = MigrationAction.DropField(DynamicOptic.root.field("x"), defaultIntExpr)
      val input  = rec("x" -> intDV(1))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(DynamicValue.Record.empty)))
    }
  )

  private val renameSuite = suite("Rename")(
    test("renames a field") {
      val action = MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName")
      val input  = rec("name" -> strDV("Alice"), "age" -> intDV(30))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(rec("fullName" -> strDV("Alice"), "age" -> intDV(30)))))
    },
    test("fails if source field not found") {
      val action = MigrationAction.Rename(DynamicOptic.root.field("missing"), "x")
      val input  = rec("name" -> strDV("Alice"))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isLeft)
    },
    test("fails if target field already exists") {
      val action = MigrationAction.Rename(DynamicOptic.root.field("name"), "age")
      val input  = rec("name" -> strDV("Alice"), "age" -> intDV(30))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isLeft)
    }
  )

  private val transformValueSuite = suite("TransformValue")(
    test("replaces a field value with expression result") {
      val action = MigrationAction.TransformValue(
        DynamicOptic.root.field("age"),
        DynamicSchemaExpr.Literal(intDV(99))
      )
      val input  = rec("name" -> strDV("Alice"), "age" -> intDV(30))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(rec("name" -> strDV("Alice"), "age" -> intDV(99)))))
    },
    test("transforms root value") {
      val action = MigrationAction.TransformValue(
        DynamicOptic.root,
        DynamicSchemaExpr.Literal(strDV("replaced"))
      )
      val result = DynamicMigration.single(action)(intDV(42))
      assert(result)(isRight(equalTo(strDV("replaced"))))
    }
  )

  private val mandateSuite = suite("Mandate")(
    test("unwraps Some to its inner value") {
      val action = MigrationAction.Mandate(DynamicOptic.root.field("opt"), DynamicSchemaExpr.Literal(intDV(0)))
      val input  = rec("opt" -> DynamicValue.Variant("Some", intDV(42)))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(rec("opt" -> intDV(42)))))
    },
    test("uses default for None") {
      val action = MigrationAction.Mandate(DynamicOptic.root.field("opt"), DynamicSchemaExpr.Literal(intDV(99)))
      val input  = rec("opt" -> DynamicValue.Variant("None", DynamicValue.Record.empty))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(rec("opt" -> intDV(99)))))
    },
    test("passes through non-option values") {
      val action = MigrationAction.Mandate(DynamicOptic.root.field("x"), DynamicSchemaExpr.Literal(intDV(0)))
      val input  = rec("x" -> intDV(42))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(rec("x" -> intDV(42)))))
    }
  )

  private val optionalizeSuite = suite("Optionalize")(
    test("wraps a value in Some") {
      val action = MigrationAction.Optionalize(DynamicOptic.root.field("x"))
      val input  = rec("x" -> intDV(42))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(rec("x" -> DynamicValue.Variant("Some", intDV(42))))))
    }
  )

  private val changeTypeSuite = suite("ChangeType")(
    test("changes Int to Long via PrimitiveConversion") {
      val action = MigrationAction.ChangeType(
        DynamicOptic.root.field("count"),
        DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.IntToLong)
      )
      val input  = rec("count" -> intDV(42))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(rec("count" -> longDV(42L)))))
    },
    test("changes Int to String") {
      val action = MigrationAction.ChangeType(
        DynamicOptic.root.field("id"),
        DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.IntToString)
      )
      val input  = rec("id" -> intDV(123))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(rec("id" -> strDV("123")))))
    }
  )

  private val renameCaseSuite = suite("RenameCase")(
    test("renames a variant case") {
      val action = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
      val input  = DynamicValue.Variant("OldCase", intDV(1))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(DynamicValue.Variant("NewCase", intDV(1)))))
    },
    test("leaves other cases unchanged") {
      val action = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
      val input  = DynamicValue.Variant("OtherCase", intDV(1))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(input)))
    }
  )

  private val transformCaseSuite = suite("TransformCase")(
    test("applies nested actions to variant payload") {
      val nestedActions = Vector(
        MigrationAction.AddField(DynamicOptic.root.field("extra"), DynamicSchemaExpr.Literal(intDV(0)))
      )
      val action = MigrationAction.TransformCase(DynamicOptic.root, nestedActions)
      val input  = DynamicValue.Variant("MyCase", rec("name" -> strDV("test")))
      val result = DynamicMigration.single(action)(input)
      assert(result)(
        isRight(
          equalTo(
            DynamicValue.Variant("MyCase", rec("name" -> strDV("test"), "extra" -> intDV(0)))
          )
        )
      )
    }
  )

  private val applyMigrationSuite = suite("ApplyMigration")(
    test("applies nested migration at path") {
      val nestedMigration = DynamicMigration(
        Vector(
          MigrationAction.AddField(DynamicOptic.root.field("z"), DynamicSchemaExpr.Literal(intDV(0)))
        )
      )
      val action = MigrationAction.ApplyMigration(DynamicOptic.root.field("inner"), nestedMigration)
      val input  = rec("inner" -> rec("x" -> intDV(1)))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(rec("inner" -> rec("x" -> intDV(1), "z" -> intDV(0))))))
    }
  )

  private val transformElementsSuite = suite("TransformElements")(
    test("transforms all elements of a sequence") {
      val action = MigrationAction.TransformElements(
        DynamicOptic.root.field("items"),
        DynamicSchemaExpr.Literal(intDV(0))
      )
      val input  = rec("items" -> DynamicValue.Sequence(Chunk(intDV(1), intDV(2), intDV(3))))
      val result = DynamicMigration.single(action)(input)
      assert(result)(
        isRight(
          equalTo(
            rec("items" -> DynamicValue.Sequence(Chunk(intDV(0), intDV(0), intDV(0))))
          )
        )
      )
    },
    test("transforms each element individually using PrimitiveConversion") {
      val action = MigrationAction.TransformElements(
        DynamicOptic.root.field("items"),
        DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.IntToString)
      )
      val input  = rec("items" -> DynamicValue.Sequence(Chunk(intDV(1), intDV(2), intDV(3))))
      val result = DynamicMigration.single(action)(input)
      assert(result)(
        isRight(
          equalTo(
            rec("items" -> DynamicValue.Sequence(Chunk(strDV("1"), strDV("2"), strDV("3"))))
          )
        )
      )
    },
    test("fails on non-sequence") {
      val action = MigrationAction.TransformElements(
        DynamicOptic.root.field("x"),
        DynamicSchemaExpr.Literal(intDV(0))
      )
      val input  = rec("x" -> intDV(42))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isLeft)
    }
  )

  private val transformKeysSuite = suite("TransformKeys")(
    test("transforms all keys of a map") {
      val action = MigrationAction.TransformKeys(
        DynamicOptic.root.field("data"),
        DynamicSchemaExpr.Literal(strDV("newKey"))
      )
      val input = rec(
        "data" -> DynamicValue.Map(
          Chunk(
            (strDV("a"), intDV(1)),
            (strDV("b"), intDV(2))
          )
        )
      )
      val result = DynamicMigration.single(action)(input)
      assert(result)(
        isRight(
          equalTo(
            rec(
              "data" -> DynamicValue.Map(
                Chunk(
                  (strDV("newKey"), intDV(1)),
                  (strDV("newKey"), intDV(2))
                )
              )
            )
          )
        )
      )
    }
  )

  private val transformValuesSuite = suite("TransformValues")(
    test("transforms all values of a map") {
      val action = MigrationAction.TransformValues(
        DynamicOptic.root.field("data"),
        DynamicSchemaExpr.Literal(intDV(0))
      )
      val input = rec(
        "data" -> DynamicValue.Map(
          Chunk(
            (strDV("a"), intDV(1)),
            (strDV("b"), intDV(2))
          )
        )
      )
      val result = DynamicMigration.single(action)(input)
      assert(result)(
        isRight(
          equalTo(
            rec(
              "data" -> DynamicValue.Map(
                Chunk(
                  (strDV("a"), intDV(0)),
                  (strDV("b"), intDV(0))
                )
              )
            )
          )
        )
      )
    }
  )

  private val compositionSuite = suite("Composition")(
    test("++ composes two migrations sequentially") {
      val m1 = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root.field("x"), DynamicSchemaExpr.Literal(intDV(1)))
      )
      val m2 = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root.field("y"), DynamicSchemaExpr.Literal(intDV(2)))
      )
      val result = (m1 ++ m2)(DynamicValue.Record.empty)
      assert(result)(isRight(equalTo(rec("x" -> intDV(1), "y" -> intDV(2)))))
    },
    test("andThen is the same as ++") {
      val m1 = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root.field("a"), DynamicSchemaExpr.Literal(intDV(1)))
      )
      val m2 = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root.field("a"), "b")
      )
      val r1 = (m1 ++ m2)(DynamicValue.Record.empty)
      val r2 = m1.andThen(m2)(DynamicValue.Record.empty)
      assert(r1)(equalTo(r2))
    },
    test("empty migration is identity") {
      val input  = rec("name" -> strDV("Alice"))
      val result = DynamicMigration.empty(input)
      assert(result)(isRight(equalTo(input)))
    },
    test("composing with empty is identity") {
      val m = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root.field("x"), DynamicSchemaExpr.Literal(intDV(1)))
      )
      val input = DynamicValue.Record.empty
      val r1    = m(input)
      val r2    = (m ++ DynamicMigration.empty)(input)
      val r3    = (DynamicMigration.empty ++ m)(input)
      assert(r1)(equalTo(r2)) && assert(r1)(equalTo(r3))
    },
    test("isEmpty returns true for empty migration") {
      assert(DynamicMigration.empty.isEmpty)(isTrue)
    },
    test("isEmpty returns false for non-empty migration") {
      val m = DynamicMigration.single(MigrationAction.Optionalize(DynamicOptic.root))
      assert(m.isEmpty)(isFalse)
    },
    test("size returns correct count") {
      val m = DynamicMigration(
        MigrationAction.AddField(DynamicOptic.root.field("a"), defaultIntExpr),
        MigrationAction.AddField(DynamicOptic.root.field("b"), defaultIntExpr),
        MigrationAction.Rename(DynamicOptic.root.field("a"), "x")
      )
      assert(m.size)(equalTo(3))
    }
  )

  private val reverseSuite = suite("Reverse")(
    test("AddField reversed becomes DropField") {
      val action   = MigrationAction.AddField(DynamicOptic.root.field("x"), defaultIntExpr)
      val reversed = action.reverse
      assert(reversed)(isSubtype[MigrationAction.DropField](anything))
    },
    test("DropField reversed becomes AddField") {
      val action   = MigrationAction.DropField(DynamicOptic.root.field("x"), defaultIntExpr)
      val reversed = action.reverse
      assert(reversed)(isSubtype[MigrationAction.AddField](anything))
    },
    test("Rename reversed swaps from/to") {
      val action   = MigrationAction.Rename(DynamicOptic.root.field("old"), "new")
      val reversed = action.reverse
      reversed match {
        case MigrationAction.Rename(at, to) =>
          assert(at.nodes.last)(equalTo(DynamicOptic.Node.Field("new"): DynamicOptic.Node)) &&
          assert(to)(equalTo("old"))
        case other =>
          assert(other)(isSubtype[MigrationAction.Rename](anything))
      }
    },
    test("Optionalize reversed is Mandate") {
      val action = MigrationAction.Optionalize(DynamicOptic.root)
      assert(action.reverse)(isSubtype[MigrationAction.Mandate](anything))
    },
    test("Mandate reversed is Optionalize") {
      val action = MigrationAction.Mandate(DynamicOptic.root, defaultIntExpr)
      assert(action.reverse)(isSubtype[MigrationAction.Optionalize](anything))
    },
    test("TransformValue reversed is Irreversible") {
      val action = MigrationAction.TransformValue(DynamicOptic.root, defaultIntExpr)
      assert(action.reverse)(isSubtype[MigrationAction.Irreversible](anything))
    },
    test("ChangeType reversed is Irreversible") {
      val action = MigrationAction.ChangeType(DynamicOptic.root, defaultIntExpr)
      assert(action.reverse)(isSubtype[MigrationAction.Irreversible](anything))
    },
    test("RenameCase reversed swaps from/to") {
      val action   = MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
      val reversed = action.reverse.asInstanceOf[MigrationAction.RenameCase]
      assert(reversed.from)(equalTo("B")) && assert(reversed.to)(equalTo("A"))
    },
    test("Irreversible reverses to itself") {
      val action = MigrationAction.Irreversible(DynamicOptic.root, "TransformValue")
      assert(action.reverse)(equalTo(action))
    },
    test("migration reverse reverses action order and each action") {
      val m = DynamicMigration(
        Vector(
          MigrationAction.AddField(DynamicOptic.root.field("x"), defaultIntExpr),
          MigrationAction.AddField(DynamicOptic.root.field("y"), defaultStrExpr)
        )
      )
      val reversed = m.reverse
      assert(reversed.size)(equalTo(2)) &&
      assert(reversed.actions(0))(isSubtype[MigrationAction.DropField](anything)) &&
      assert(reversed.actions(1))(isSubtype[MigrationAction.DropField](anything))
    },
    test("AddField then reverse DropField round-trips") {
      val input     = rec("name" -> strDV("Alice"))
      val addAction = MigrationAction.AddField(DynamicOptic.root.field("age"), DynamicSchemaExpr.Literal(intDV(25)))
      val migration = DynamicMigration.single(addAction)
      val forward   = migration(input)
      val reversed  = migration.reverse
      val roundTrip = forward.flatMap(reversed(_))
      assert(roundTrip)(isRight(equalTo(input)))
    },
    test("Rename round-trips") {
      val input     = rec("old" -> intDV(1))
      val migration = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root.field("old"), "new"))
      val forward   = migration(input)
      val roundTrip = forward.flatMap(migration.reverse(_))
      assert(roundTrip)(isRight(equalTo(input)))
    }
  )

  private val nestedPathSuite = suite("Nested paths")(
    test("adds field to nested record") {
      val action = MigrationAction.AddField(
        DynamicOptic.root.field("inner").field("z"),
        DynamicSchemaExpr.Literal(intDV(0))
      )
      val input  = rec("inner" -> rec("x" -> intDV(1)))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(rec("inner" -> rec("x" -> intDV(1), "z" -> intDV(0))))))
    },
    test("drops field from nested record") {
      val action = MigrationAction.DropField(
        DynamicOptic.root.field("inner").field("y"),
        defaultIntExpr
      )
      val input  = rec("inner" -> rec("x" -> intDV(1), "y" -> intDV(2)))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(rec("inner" -> rec("x" -> intDV(1))))))
    },
    test("renames field in nested record") {
      val action = MigrationAction.Rename(DynamicOptic.root.field("inner").field("old"), "new")
      val input  = rec("inner" -> rec("old" -> intDV(1)))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(rec("inner" -> rec("new" -> intDV(1))))))
    },
    test("transforms nested field value") {
      val action = MigrationAction.TransformValue(
        DynamicOptic.root.field("a").field("b"),
        DynamicSchemaExpr.Literal(strDV("replaced"))
      )
      val input  = rec("a" -> rec("b" -> intDV(1)))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(rec("a" -> rec("b" -> strDV("replaced"))))))
    },
    test("fails when intermediate path not found") {
      val action = MigrationAction.AddField(
        DynamicOptic.root.field("missing").field("z"),
        defaultIntExpr
      )
      val input  = rec("x" -> intDV(1))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isLeft)
    }
  )

  private val edgeCasesSuite = suite("Edge cases")(
    test("multi-step migration: add, rename, transform") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.AddField(DynamicOptic.root.field("score"), DynamicSchemaExpr.Literal(intDV(0))),
          MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName"),
          MigrationAction.TransformValue(DynamicOptic.root.field("score"), DynamicSchemaExpr.Literal(intDV(100)))
        )
      )
      val input  = rec("name" -> strDV("Alice"))
      val result = m(input)
      assert(result)(isRight(equalTo(rec("fullName" -> strDV("Alice"), "score" -> intDV(100)))))
    },
    test("Irreversible action fails on execution") {
      val action = MigrationAction.Irreversible(DynamicOptic.root, "TransformValue")
      val result = DynamicMigration.single(action)(intDV(42))
      assert(result)(isLeft)
    },
    test("DynamicMigration.apply varargs constructor") {
      val m = DynamicMigration(
        MigrationAction.AddField(DynamicOptic.root.field("a"), defaultIntExpr),
        MigrationAction.AddField(DynamicOptic.root.field("b"), defaultStrExpr)
      )
      assert(m.size)(equalTo(2))
    },
    test("operates on deeply nested paths (3 levels)") {
      val action = MigrationAction.AddField(
        DynamicOptic.root.field("l1").field("l2").field("l3").field("newField"),
        DynamicSchemaExpr.Literal(boolDV(true))
      )
      val input  = rec("l1" -> rec("l2" -> rec("l3" -> rec("existing" -> intDV(1)))))
      val result = DynamicMigration.single(action)(input)
      assert(result)(
        isRight(
          equalTo(
            rec("l1" -> rec("l2" -> rec("l3" -> rec("existing" -> intDV(1), "newField" -> boolDV(true)))))
          )
        )
      )
    },
    test("modifyAt with Case node") {
      val action = MigrationAction.TransformValue(
        DynamicOptic.root.caseOf("MyCase"),
        DynamicSchemaExpr.Literal(strDV("transformed"))
      )
      val input  = DynamicValue.Variant("MyCase", intDV(42))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(DynamicValue.Variant("MyCase", strDV("transformed")))))
    },
    test("modifyAt with Elements node transforms each element") {
      val action = MigrationAction.TransformValue(
        DynamicOptic.elements,
        DynamicSchemaExpr.Literal(intDV(0))
      )
      val input  = DynamicValue.Sequence(Chunk(intDV(1), intDV(2), intDV(3)))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(DynamicValue.Sequence(Chunk(intDV(0), intDV(0), intDV(0))))))
    },
    test("modifyAt with AtIndex node") {
      val action = MigrationAction.TransformValue(
        DynamicOptic.root.at(1),
        DynamicSchemaExpr.Literal(intDV(99))
      )
      val input  = DynamicValue.Sequence(Chunk(intDV(1), intDV(2), intDV(3)))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(DynamicValue.Sequence(Chunk(intDV(1), intDV(99), intDV(3))))))
    },
    test("modifyAt with MapKeys node") {
      val action = MigrationAction.TransformValue(
        DynamicOptic.mapKeys,
        DynamicSchemaExpr.Literal(strDV("k"))
      )
      val input  = DynamicValue.Map(Chunk((strDV("a"), intDV(1)), (strDV("b"), intDV(2))))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(DynamicValue.Map(Chunk((strDV("k"), intDV(1)), (strDV("k"), intDV(2)))))))
    },
    test("modifyAt with MapValues node") {
      val action = MigrationAction.TransformValue(
        DynamicOptic.mapValues,
        DynamicSchemaExpr.Literal(intDV(0))
      )
      val input  = DynamicValue.Map(Chunk((strDV("a"), intDV(1)), (strDV("b"), intDV(2))))
      val result = DynamicMigration.single(action)(input)
      assert(result)(isRight(equalTo(DynamicValue.Map(Chunk((strDV("a"), intDV(0)), (strDV("b"), intDV(0)))))))
    },
    test("per-element transform on keys via PrimitiveConversion") {
      val action = MigrationAction.TransformKeys(
        DynamicOptic.root.field("data"),
        DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.IntToString)
      )
      val input = rec(
        "data" -> DynamicValue.Map(
          Chunk(
            (intDV(1), strDV("a")),
            (intDV(2), strDV("b"))
          )
        )
      )
      val result = DynamicMigration.single(action)(input)
      assert(result)(
        isRight(
          equalTo(
            rec(
              "data" -> DynamicValue.Map(
                Chunk(
                  (strDV("1"), strDV("a")),
                  (strDV("2"), strDV("b"))
                )
              )
            )
          )
        )
      )
    },
    test("per-element transform on values via PrimitiveConversion") {
      val action = MigrationAction.TransformValues(
        DynamicOptic.root.field("data"),
        DynamicSchemaExpr.PrimitiveConversion(DynamicSchemaExpr.ConversionType.IntToLong)
      )
      val input = rec(
        "data" -> DynamicValue.Map(
          Chunk(
            (strDV("a"), intDV(1)),
            (strDV("b"), intDV(2))
          )
        )
      )
      val result = DynamicMigration.single(action)(input)
      assert(result)(
        isRight(
          equalTo(
            rec(
              "data" -> DynamicValue.Map(
                Chunk(
                  (strDV("a"), longDV(1L)),
                  (strDV("b"), longDV(2L))
                )
              )
            )
          )
        )
      )
    }
  )

  private val lawsSuite = suite("Laws")(
    test("identity law: empty migration is identity") {
      val input  = rec("name" -> strDV("Alice"), "age" -> intDV(30))
      val result = DynamicMigration.empty(input)
      assert(result)(isRight(equalTo(input)))
    },
    test("associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
      val m1 = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root.field("a"), DynamicSchemaExpr.Literal(intDV(1)))
      )
      val m2 = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root.field("b"), DynamicSchemaExpr.Literal(intDV(2)))
      )
      val m3 = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root.field("c"), DynamicSchemaExpr.Literal(intDV(3)))
      )
      val input      = DynamicValue.Record.empty
      val leftAssoc  = ((m1 ++ m2) ++ m3)(input)
      val rightAssoc = (m1 ++ (m2 ++ m3))(input)
      assert(leftAssoc)(equalTo(rightAssoc))
    },
    test("double reverse involution: m.reverse.reverse has same actions") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.AddField(DynamicOptic.root.field("x"), defaultIntExpr),
          MigrationAction.Rename(DynamicOptic.root.field("a"), "b")
        )
      )
      val rr = m.reverse.reverse
      assert(rr.actions)(equalTo(m.actions))
    },
    test("semantic inverse: AddField then reverse round-trips") {
      val input     = rec("name" -> strDV("test"))
      val migration = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root.field("x"), DynamicSchemaExpr.Literal(intDV(42)))
      )
      val roundTrip = migration(input).flatMap(migration.reverse(_))
      assert(roundTrip)(isRight(equalTo(input)))
    },
    test("semantic inverse: Rename then reverse round-trips") {
      val input     = rec("old" -> intDV(1), "other" -> intDV(2))
      val migration = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root.field("old"), "new")
      )
      val roundTrip = migration(input).flatMap(migration.reverse(_))
      assert(roundTrip)(isRight(equalTo(input)))
    },
    test("semantic inverse: Optionalize then Mandate round-trips") {
      val input     = rec("x" -> intDV(42))
      val migration = DynamicMigration.single(MigrationAction.Optionalize(DynamicOptic.root.field("x")))
      val forward   = migration(input)
      val backward  = forward.flatMap(migration.reverse(_))
      assert(backward)(isRight(equalTo(input)))
    },
    test("semantic inverse: multi-step round-trip") {
      val input     = rec("name" -> strDV("Alice"))
      val migration = new DynamicMigration(
        Vector(
          MigrationAction.AddField(DynamicOptic.root.field("age"), DynamicSchemaExpr.Literal(intDV(25))),
          MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName")
        )
      )
      val roundTrip = migration(input).flatMap(migration.reverse(_))
      assert(roundTrip)(isRight(equalTo(input)))
    },
    test("composition with identity is neutral") {
      val m = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root.field("x"), DynamicSchemaExpr.Literal(intDV(1)))
      )
      val input = DynamicValue.Record.empty
      val r1    = m(input)
      val r2    = (DynamicMigration.empty ++ m)(input)
      val r3    = (m ++ DynamicMigration.empty)(input)
      assert(r1)(equalTo(r2)) && assert(r1)(equalTo(r3))
    }
  )
}
