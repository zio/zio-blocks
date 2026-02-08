package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

object DynamicMigrationSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Data Helpers
  // ─────────────────────────────────────────────────────────────────────────

  private def intDV(i: Int): DynamicValue             = DynamicValue.Primitive(PrimitiveValue.Int(i))
  private def longDV(l: Long): DynamicValue           = DynamicValue.Primitive(PrimitiveValue.Long(l))
  private def doubleDV(d: Double): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Double(d))
  private def stringDV(s: String): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def boolDV(b: Boolean): DynamicValue        = DynamicValue.Primitive(PrimitiveValue.Boolean(b))
  private def lit(dv: DynamicValue): MigrationExpr    = MigrationExpr.Literal(dv)
  private def litInt(i: Int): MigrationExpr           = lit(intDV(i))
  private def litStr(s: String): MigrationExpr        = lit(stringDV(s))
  private def fieldRef(name: String): MigrationExpr   = MigrationExpr.FieldRef(DynamicOptic.root.field(name))
  private def defVal(dv: DynamicValue): MigrationExpr = MigrationExpr.DefaultValue(dv)

  private val emptyRecord: DynamicValue = DynamicValue.Record(Chunk.empty)

  private val simpleRecord: DynamicValue = DynamicValue.Record(
    Chunk(("name", stringDV("Alice")), ("age", intDV(30)))
  )

  private def optionSome(inner: DynamicValue): DynamicValue =
    DynamicValue.Variant("Some", DynamicValue.Record(Chunk(("value", inner))))

  private def optionNone: DynamicValue =
    DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty))

  def spec: Spec[TestEnvironment, Any] = suite("DynamicMigrationSpec")(
    identitySuite,
    addFieldSuite,
    dropFieldSuite,
    renameSuite,
    transformValueSuite,
    mandateSuite,
    optionalizeSuite,
    changeTypeSuite,
    renameCaseSuite,
    transformCaseSuite,
    transformElementsSuite,
    transformKeysSuite,
    transformValuesSuite,
    joinSplitSuite,
    exprEvalSuite,
    sequentialCompositionSuite,
    errorCaseSuite,
    nestedNavigationSuite,
    reversalSuite,
    coercionCoverageSuite,
    arithmeticCoverageSuite,
    navigationEdgeCaseSuite,
    unwrapOptionEdgeSuite
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Identity
  // ─────────────────────────────────────────────────────────────────────────

  private val identitySuite = suite("identity")(
    test("empty migration returns value unchanged") {
      val result = DynamicMigration.empty(simpleRecord)
      assertTrue(result == Right(simpleRecord))
    },
    test("empty migration on empty record") {
      val result = DynamicMigration.empty(emptyRecord)
      assertTrue(result == Right(emptyRecord))
    },
    test("empty migration on primitive") {
      val v      = intDV(42)
      val result = DynamicMigration.empty(v)
      assertTrue(result == Right(v))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // AddField
  // ─────────────────────────────────────────────────────────────────────────

  private val addFieldSuite = suite("AddField")(
    test("adds a field to an empty record") {
      val action    = MigrationAction.AddField(DynamicOptic.root, "age", litInt(0))
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(emptyRecord)
      assertTrue(result == Right(DynamicValue.Record(Chunk(("age", intDV(0))))))
    },
    test("adds a field to a record with existing fields") {
      val action    = MigrationAction.AddField(DynamicOptic.root, "email", litStr("none"))
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(simpleRecord)
      val expected  = DynamicValue.Record(
        Chunk(("name", stringDV("Alice")), ("age", intDV(30)), ("email", stringDV("none")))
      )
      assertTrue(result == Right(expected))
    },
    test("errors on duplicate field") {
      val action    = MigrationAction.AddField(DynamicOptic.root, "name", litStr("Bob"))
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(simpleRecord)
      assertTrue(result.isLeft)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // DropField
  // ─────────────────────────────────────────────────────────────────────────

  private val dropFieldSuite = suite("DropField")(
    test("removes a field from a record") {
      val action    = MigrationAction.DropField(DynamicOptic.root, "age", litInt(30))
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(simpleRecord)
      assertTrue(result == Right(DynamicValue.Record(Chunk(("name", stringDV("Alice"))))))
    },
    test("errors when field does not exist") {
      val action    = MigrationAction.DropField(DynamicOptic.root, "nonexistent", litInt(0))
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(simpleRecord)
      assertTrue(result.isLeft)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Rename
  // ─────────────────────────────────────────────────────────────────────────

  private val renameSuite = suite("Rename")(
    test("renames a field preserving value and order") {
      val action    = MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(simpleRecord)
      val expected  = DynamicValue.Record(
        Chunk(("fullName", stringDV("Alice")), ("age", intDV(30)))
      )
      assertTrue(result == Right(expected))
    },
    test("errors when source field does not exist") {
      val action    = MigrationAction.Rename(DynamicOptic.root, "missing", "other")
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(simpleRecord)
      assertTrue(result.isLeft)
    },
    test("errors when target field already exists") {
      val action    = MigrationAction.Rename(DynamicOptic.root, "name", "age")
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(simpleRecord)
      assertTrue(result.isLeft)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // TransformValue
  // ─────────────────────────────────────────────────────────────────────────

  private val transformValueSuite = suite("TransformValue")(
    test("transforms a field value using a literal expression") {
      val action = MigrationAction.TransformValue(
        DynamicOptic.root,
        "age",
        litInt(99),
        litInt(30)
      )
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(simpleRecord)
      val expected  = DynamicValue.Record(
        Chunk(("name", stringDV("Alice")), ("age", intDV(99)))
      )
      assertTrue(result == Right(expected))
    },
    test("transforms a field value using arithmetic") {
      val addExpr = MigrationExpr.Add(
        MigrationExpr.Literal(intDV(0)),
        MigrationExpr.Literal(intDV(10))
      )
      val subExpr = MigrationExpr.Subtract(
        MigrationExpr.Literal(intDV(0)),
        MigrationExpr.Literal(intDV(10))
      )
      val action    = MigrationAction.TransformValue(DynamicOptic.root, "age", addExpr, subExpr)
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(simpleRecord)
      val expected  = DynamicValue.Record(
        Chunk(("name", stringDV("Alice")), ("age", intDV(10)))
      )
      assertTrue(result == Right(expected))
    },
    test("errors when field does not exist") {
      val action    = MigrationAction.TransformValue(DynamicOptic.root, "missing", litInt(0), litInt(0))
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(simpleRecord)
      assertTrue(result.isLeft)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Mandate
  // ─────────────────────────────────────────────────────────────────────────

  private val mandateSuite = suite("Mandate")(
    test("unwraps Some variant") {
      val record = DynamicValue.Record(
        Chunk(("value", optionSome(intDV(42))))
      )
      val action    = MigrationAction.Mandate(DynamicOptic.root, "value", litInt(0))
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(record)
      assertTrue(result == Right(DynamicValue.Record(Chunk(("value", intDV(42))))))
    },
    test("uses default for None variant") {
      val record = DynamicValue.Record(
        Chunk(("value", optionNone))
      )
      val action    = MigrationAction.Mandate(DynamicOptic.root, "value", litInt(99))
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(record)
      assertTrue(result == Right(DynamicValue.Record(Chunk(("value", intDV(99))))))
    },
    test("uses default for Null") {
      val record = DynamicValue.Record(
        Chunk(("value", DynamicValue.Null))
      )
      val action    = MigrationAction.Mandate(DynamicOptic.root, "value", litInt(99))
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(record)
      assertTrue(result == Right(DynamicValue.Record(Chunk(("value", intDV(99))))))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Optionalize
  // ─────────────────────────────────────────────────────────────────────────

  private val optionalizeSuite = suite("Optionalize")(
    test("wraps field value in Some variant") {
      val record    = DynamicValue.Record(Chunk(("x", intDV(10))))
      val action    = MigrationAction.Optionalize(DynamicOptic.root, "x", litInt(0))
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(record)
      val expected  = DynamicValue.Record(
        Chunk(("x", optionSome(intDV(10))))
      )
      assertTrue(result == Right(expected))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // ChangeType
  // ─────────────────────────────────────────────────────────────────────────

  private val changeTypeSuite = suite("ChangeType")(
    test("coerces Int to String") {
      val record   = DynamicValue.Record(Chunk(("x", intDV(42))))
      val coercion = MigrationExpr.Coerce(
        MigrationExpr.FieldRef(DynamicOptic.root),
        "String"
      )
      val reverseCoercion = MigrationExpr.Coerce(
        MigrationExpr.FieldRef(DynamicOptic.root),
        "Int"
      )
      val action    = MigrationAction.ChangeType(DynamicOptic.root, "x", coercion, reverseCoercion)
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(record)
      assertTrue(result == Right(DynamicValue.Record(Chunk(("x", stringDV("42"))))))
    },
    test("coerces String to Int") {
      val record   = DynamicValue.Record(Chunk(("x", stringDV("123"))))
      val coercion = MigrationExpr.Coerce(MigrationExpr.FieldRef(DynamicOptic.root), "Int")
      val reverse  = MigrationExpr.Coerce(MigrationExpr.FieldRef(DynamicOptic.root), "String")
      val action   = MigrationAction.ChangeType(DynamicOptic.root, "x", coercion, reverse)
      val result   = DynamicMigration(Chunk(action))(record)
      assertTrue(result == Right(DynamicValue.Record(Chunk(("x", intDV(123))))))
    },
    test("errors on invalid string to int coercion") {
      val record   = DynamicValue.Record(Chunk(("x", stringDV("abc"))))
      val coercion = MigrationExpr.Coerce(MigrationExpr.FieldRef(DynamicOptic.root), "Int")
      val reverse  = MigrationExpr.Coerce(MigrationExpr.FieldRef(DynamicOptic.root), "String")
      val action   = MigrationAction.ChangeType(DynamicOptic.root, "x", coercion, reverse)
      val result   = DynamicMigration(Chunk(action))(record)
      assertTrue(result.isLeft)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // RenameCase
  // ─────────────────────────────────────────────────────────────────────────

  private val renameCaseSuite = suite("RenameCase")(
    test("renames a matching variant case") {
      val variant   = DynamicValue.Variant("OldName", intDV(1))
      val action    = MigrationAction.RenameCase(DynamicOptic.root, "OldName", "NewName")
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(variant)
      assertTrue(result == Right(DynamicValue.Variant("NewName", intDV(1))))
    },
    test("leaves non-matching case unchanged") {
      val variant   = DynamicValue.Variant("Other", intDV(1))
      val action    = MigrationAction.RenameCase(DynamicOptic.root, "OldName", "NewName")
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(variant)
      assertTrue(result == Right(variant))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // TransformCase
  // ─────────────────────────────────────────────────────────────────────────

  private val transformCaseSuite = suite("TransformCase")(
    test("transforms inner value of matching case") {
      val inner         = DynamicValue.Record(Chunk(("x", intDV(1))))
      val variant       = DynamicValue.Variant("MyCase", inner)
      val innerAction   = MigrationAction.AddField(DynamicOptic.root, "y", litInt(2))
      val action        = MigrationAction.TransformCase(DynamicOptic.root, "MyCase", Chunk(innerAction))
      val migration     = DynamicMigration(Chunk(action))
      val result        = migration(variant)
      val expectedInner = DynamicValue.Record(Chunk(("x", intDV(1)), ("y", intDV(2))))
      assertTrue(result == Right(DynamicValue.Variant("MyCase", expectedInner)))
    },
    test("leaves non-matching case unchanged") {
      val variant     = DynamicValue.Variant("OtherCase", intDV(1))
      val innerAction = MigrationAction.AddField(DynamicOptic.root, "y", litInt(2))
      val action      = MigrationAction.TransformCase(DynamicOptic.root, "MyCase", Chunk(innerAction))
      val migration   = DynamicMigration(Chunk(action))
      val result      = migration(variant)
      assertTrue(result == Right(variant))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // TransformElements
  // ─────────────────────────────────────────────────────────────────────────

  private val transformElementsSuite = suite("TransformElements")(
    test("transforms each element in a sequence") {
      val seq      = DynamicValue.Sequence(Chunk(intDV(1), intDV(2), intDV(3)))
      val expr     = MigrationExpr.Literal(intDV(0))
      val action   = MigrationAction.TransformElements(DynamicOptic.root, expr, expr)
      val result   = DynamicMigration(Chunk(action))(seq)
      val expected = DynamicValue.Sequence(Chunk(intDV(0), intDV(0), intDV(0)))
      assertTrue(result == Right(expected))
    },
    test("works on empty sequence") {
      val seq    = DynamicValue.Sequence(Chunk.empty)
      val expr   = MigrationExpr.Literal(intDV(0))
      val action = MigrationAction.TransformElements(DynamicOptic.root, expr, expr)
      val result = DynamicMigration(Chunk(action))(seq)
      assertTrue(result == Right(DynamicValue.Sequence(Chunk.empty)))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // TransformKeys
  // ─────────────────────────────────────────────────────────────────────────

  private val transformKeysSuite = suite("TransformKeys")(
    test("transforms keys of a map") {
      val m        = DynamicValue.Map(Chunk((stringDV("a"), intDV(1)), (stringDV("b"), intDV(2))))
      val expr     = MigrationExpr.Concat(MigrationExpr.FieldRef(DynamicOptic.root), litStr("_key"))
      val action   = MigrationAction.TransformKeys(DynamicOptic.root, expr, expr)
      val result   = DynamicMigration(Chunk(action))(m)
      val expected = DynamicValue.Map(Chunk((stringDV("a_key"), intDV(1)), (stringDV("b_key"), intDV(2))))
      assertTrue(result == Right(expected))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // TransformValues
  // ─────────────────────────────────────────────────────────────────────────

  private val transformValuesSuite = suite("TransformValues")(
    test("transforms values of a map") {
      val m        = DynamicValue.Map(Chunk((stringDV("a"), intDV(1)), (stringDV("b"), intDV(2))))
      val expr     = MigrationExpr.Literal(intDV(99))
      val action   = MigrationAction.TransformValues(DynamicOptic.root, expr, expr)
      val result   = DynamicMigration(Chunk(action))(m)
      val expected = DynamicValue.Map(Chunk((stringDV("a"), intDV(99)), (stringDV("b"), intDV(99))))
      assertTrue(result == Right(expected))
    },
    test("works on empty map") {
      val m      = DynamicValue.Map(Chunk.empty)
      val expr   = MigrationExpr.Literal(intDV(99))
      val action = MigrationAction.TransformValues(DynamicOptic.root, expr, expr)
      val result = DynamicMigration(Chunk(action))(m)
      assertTrue(result == Right(DynamicValue.Map(Chunk.empty)))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Join / Split
  // ─────────────────────────────────────────────────────────────────────────

  private val joinSplitSuite = suite("Join and Split")(
    test("Join combines fields using joinExpr") {
      val record = DynamicValue.Record(
        Chunk(("first", stringDV("John")), ("last", stringDV("Doe")))
      )
      val joinExpr = MigrationExpr.Concat(
        MigrationExpr.Concat(fieldRef("first"), litStr(" ")),
        fieldRef("last")
      )
      val splitExprs = Chunk(
        ("first", litStr("John")),
        ("last", litStr("Doe"))
      )
      val action = MigrationAction.Join(
        DynamicOptic.root,
        Chunk("first", "last"),
        "fullName",
        joinExpr,
        splitExprs
      )
      val result   = DynamicMigration(Chunk(action))(record)
      val expected = DynamicValue.Record(Chunk(("fullName", stringDV("John Doe"))))
      assertTrue(result == Right(expected))
    },
    test("Split decomposes a field into multiple fields") {
      val record = DynamicValue.Record(
        Chunk(("fullName", stringDV("John Doe")))
      )
      val targetExprs = Chunk(
        ("first", litStr("John")),
        ("last", litStr("Doe"))
      )
      val joinExpr = MigrationExpr.Concat(
        MigrationExpr.Concat(fieldRef("first"), litStr(" ")),
        fieldRef("last")
      )
      val action = MigrationAction.Split(
        DynamicOptic.root,
        "fullName",
        targetExprs,
        joinExpr
      )
      val result   = DynamicMigration(Chunk(action))(record)
      val expected = DynamicValue.Record(
        Chunk(("first", stringDV("John")), ("last", stringDV("Doe")))
      )
      assertTrue(result == Right(expected))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // MigrationExpr Evaluation
  // ─────────────────────────────────────────────────────────────────────────

  private val exprEvalSuite = suite("MigrationExpr evaluation")(
    test("Literal returns constant value") {
      val result = DynamicMigration.evalExpr(litInt(42), DynamicValue.Null, DynamicOptic.root)
      assertTrue(result == Right(intDV(42)))
    },
    test("DefaultValue returns stored value") {
      val result = DynamicMigration.evalExpr(defVal(intDV(10)), DynamicValue.Null, DynamicOptic.root)
      assertTrue(result == Right(intDV(10)))
    },
    test("FieldRef navigates context") {
      val ctx    = DynamicValue.Record(Chunk(("x", intDV(5))))
      val expr   = fieldRef("x")
      val result = DynamicMigration.evalExpr(expr, ctx, DynamicOptic.root)
      assertTrue(result == Right(intDV(5)))
    },
    test("Add adds two integers") {
      val expr   = MigrationExpr.Add(litInt(3), litInt(7))
      val result = DynamicMigration.evalExpr(expr, DynamicValue.Null, DynamicOptic.root)
      assertTrue(result == Right(intDV(10)))
    },
    test("Subtract subtracts two integers") {
      val expr   = MigrationExpr.Subtract(litInt(10), litInt(3))
      val result = DynamicMigration.evalExpr(expr, DynamicValue.Null, DynamicOptic.root)
      assertTrue(result == Right(intDV(7)))
    },
    test("Multiply multiplies two integers") {
      val expr   = MigrationExpr.Multiply(litInt(4), litInt(5))
      val result = DynamicMigration.evalExpr(expr, DynamicValue.Null, DynamicOptic.root)
      assertTrue(result == Right(intDV(20)))
    },
    test("Divide divides two integers") {
      val expr   = MigrationExpr.Divide(litInt(20), litInt(4))
      val result = DynamicMigration.evalExpr(expr, DynamicValue.Null, DynamicOptic.root)
      assertTrue(result == Right(intDV(5)))
    },
    test("Divide by zero returns error") {
      val expr   = MigrationExpr.Divide(litInt(10), litInt(0))
      val result = DynamicMigration.evalExpr(expr, DynamicValue.Null, DynamicOptic.root)
      assertTrue(result.isLeft)
    },
    test("Add promotes Int + Long to Long") {
      val expr = MigrationExpr.Add(
        lit(intDV(1)),
        lit(longDV(2L))
      )
      val result = DynamicMigration.evalExpr(expr, DynamicValue.Null, DynamicOptic.root)
      assertTrue(result == Right(longDV(3L)))
    },
    test("Concat concatenates strings") {
      val expr   = MigrationExpr.Concat(litStr("Hello"), litStr(" World"))
      val result = DynamicMigration.evalExpr(expr, DynamicValue.Null, DynamicOptic.root)
      assertTrue(result == Right(stringDV("Hello World")))
    },
    test("Coerce Int to String") {
      val expr   = MigrationExpr.Coerce(litInt(42), "String")
      val result = DynamicMigration.evalExpr(expr, DynamicValue.Null, DynamicOptic.root)
      assertTrue(result == Right(stringDV("42")))
    },
    test("Coerce String to Int success") {
      val expr   = MigrationExpr.Coerce(litStr("123"), "Int")
      val result = DynamicMigration.evalExpr(expr, DynamicValue.Null, DynamicOptic.root)
      assertTrue(result == Right(intDV(123)))
    },
    test("Coerce invalid String to Int fails") {
      val expr   = MigrationExpr.Coerce(litStr("not_a_number"), "Int")
      val result = DynamicMigration.evalExpr(expr, DynamicValue.Null, DynamicOptic.root)
      assertTrue(result.isLeft)
    },
    test("Coerce Int to Long") {
      val expr   = MigrationExpr.Coerce(litInt(42), "Long")
      val result = DynamicMigration.evalExpr(expr, DynamicValue.Null, DynamicOptic.root)
      assertTrue(result == Right(longDV(42L)))
    },
    test("Coerce Int to Double") {
      val expr   = MigrationExpr.Coerce(litInt(42), "Double")
      val result = DynamicMigration.evalExpr(expr, DynamicValue.Null, DynamicOptic.root)
      assertTrue(result == Right(doubleDV(42.0)))
    },
    test("Coerce Boolean to String") {
      val expr   = MigrationExpr.Coerce(lit(boolDV(true)), "String")
      val result = DynamicMigration.evalExpr(expr, DynamicValue.Null, DynamicOptic.root)
      assertTrue(result == Right(stringDV("true")))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Sequential Composition
  // ─────────────────────────────────────────────────────────────────────────

  private val sequentialCompositionSuite = suite("sequential composition")(
    test("multiple actions applied in order") {
      val actions = Chunk(
        MigrationAction.AddField(DynamicOptic.root, "x", litInt(1)),
        MigrationAction.AddField(DynamicOptic.root, "y", litInt(2)),
        MigrationAction.DropField(DynamicOptic.root, "x", litInt(1))
      )
      val migration = DynamicMigration(actions)
      val result    = migration(emptyRecord)
      assertTrue(result == Right(DynamicValue.Record(Chunk(("y", intDV(2))))))
    },
    test("add then rename") {
      val actions = Chunk(
        MigrationAction.AddField(DynamicOptic.root, "x", litInt(1)),
        MigrationAction.Rename(DynamicOptic.root, "x", "y")
      )
      val migration = DynamicMigration(actions)
      val result    = migration(emptyRecord)
      assertTrue(result == Right(DynamicValue.Record(Chunk(("y", intDV(1))))))
    },
    test("composed migrations (++ operator)") {
      val m1       = DynamicMigration(Chunk(MigrationAction.AddField(DynamicOptic.root, "a", litInt(1))))
      val m2       = DynamicMigration(Chunk(MigrationAction.AddField(DynamicOptic.root, "b", litInt(2))))
      val composed = m1 ++ m2
      val result   = composed(emptyRecord)
      val expected = DynamicValue.Record(Chunk(("a", intDV(1)), ("b", intDV(2))))
      assertTrue(result == Right(expected))
    },
    test("short-circuits on first error") {
      val actions = Chunk(
        MigrationAction.AddField(DynamicOptic.root, "x", litInt(1)),
        MigrationAction.DropField(DynamicOptic.root, "nonexistent", litInt(0)),
        MigrationAction.AddField(DynamicOptic.root, "y", litInt(2))
      )
      val migration = DynamicMigration(actions)
      val result    = migration(emptyRecord)
      assertTrue(result.isLeft)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Error Cases
  // ─────────────────────────────────────────────────────────────────────────

  private val errorCaseSuite = suite("error cases")(
    test("type mismatch: expected Record got Primitive") {
      val action    = MigrationAction.AddField(DynamicOptic.root, "x", litInt(1))
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(intDV(42))
      assertTrue(result.isLeft)
    },
    test("type mismatch: expected Variant got Record") {
      val action    = MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(simpleRecord)
      assertTrue(result.isLeft)
    },
    test("type mismatch: expected Sequence got Record") {
      val expr   = MigrationExpr.Literal(intDV(0))
      val action = MigrationAction.TransformElements(DynamicOptic.root, expr, expr)
      val result = DynamicMigration(Chunk(action))(simpleRecord)
      assertTrue(result.isLeft)
    },
    test("type mismatch: expected Map got Primitive") {
      val expr   = MigrationExpr.Literal(intDV(0))
      val action = MigrationAction.TransformValues(DynamicOptic.root, expr, expr)
      val result = DynamicMigration(Chunk(action))(intDV(42))
      assertTrue(result.isLeft)
    },
    test("navigation failure: field not found in nested path") {
      val action    = MigrationAction.AddField(DynamicOptic.root.field("missing"), "x", litInt(1))
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(simpleRecord)
      assertTrue(result.isLeft)
    },
    test("FieldRef navigation failure") {
      val expr   = MigrationExpr.FieldRef(DynamicOptic.root.field("nonexistent"))
      val result = DynamicMigration.evalExpr(expr, simpleRecord, DynamicOptic.root)
      assertTrue(result.isLeft)
    },
    test("Concat with non-string operands") {
      val expr   = MigrationExpr.Concat(litInt(1), litInt(2))
      val result = DynamicMigration.evalExpr(expr, DynamicValue.Null, DynamicOptic.root)
      assertTrue(result.isLeft)
    },
    test("Arithmetic with non-numeric operands") {
      val expr   = MigrationExpr.Add(litStr("a"), litStr("b"))
      val result = DynamicMigration.evalExpr(expr, DynamicValue.Null, DynamicOptic.root)
      assertTrue(result.isLeft)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Nested Navigation
  // ─────────────────────────────────────────────────────────────────────────

  private val nestedNavigationSuite = suite("nested navigation")(
    test("add field inside a nested record") {
      val inner    = DynamicValue.Record(Chunk(("x", intDV(1))))
      val outer    = DynamicValue.Record(Chunk(("inner", inner)))
      val action   = MigrationAction.AddField(DynamicOptic.root.field("inner"), "y", litInt(2))
      val result   = DynamicMigration(Chunk(action))(outer)
      val expected = DynamicValue.Record(
        Chunk(("inner", DynamicValue.Record(Chunk(("x", intDV(1)), ("y", intDV(2))))))
      )
      assertTrue(result == Right(expected))
    },
    test("rename field inside variant case") {
      val inner    = DynamicValue.Record(Chunk(("old", intDV(1))))
      val variant  = DynamicValue.Variant("MyCase", inner)
      val action   = MigrationAction.Rename(DynamicOptic.root.caseOf("MyCase"), "old", "new")
      val result   = DynamicMigration(Chunk(action))(variant)
      val expected = DynamicValue.Variant(
        "MyCase",
        DynamicValue.Record(Chunk(("new", intDV(1))))
      )
      assertTrue(result == Right(expected))
    },
    test("transform value inside sequence element") {
      val seq = DynamicValue.Sequence(
        Chunk(
          DynamicValue.Record(Chunk(("v", intDV(1)))),
          DynamicValue.Record(Chunk(("v", intDV(2))))
        )
      )
      val action = MigrationAction.AddField(
        DynamicOptic.root.at(0),
        "extra",
        litInt(99)
      )
      val result   = DynamicMigration(Chunk(action))(seq)
      val expected = DynamicValue.Sequence(
        Chunk(
          DynamicValue.Record(Chunk(("v", intDV(1)), ("extra", intDV(99)))),
          DynamicValue.Record(Chunk(("v", intDV(2))))
        )
      )
      assertTrue(result == Right(expected))
    },
    test("deeply nested navigation (3+ levels)") {
      val level3 = DynamicValue.Record(Chunk(("deep", intDV(42))))
      val level2 = DynamicValue.Record(Chunk(("mid", level3)))
      val level1 = DynamicValue.Record(Chunk(("top", level2)))
      val action = MigrationAction.Rename(
        DynamicOptic.root.field("top").field("mid"),
        "deep",
        "shallow"
      )
      val result   = DynamicMigration(Chunk(action))(level1)
      val expected = DynamicValue.Record(
        Chunk(("top", DynamicValue.Record(Chunk(("mid", DynamicValue.Record(Chunk(("shallow", intDV(42)))))))))
      )
      assertTrue(result == Right(expected))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Reversal Round-Trip
  // ─────────────────────────────────────────────────────────────────────────

  private val reversalSuite = suite("reversal")(
    test("double reverse produces original migration") {
      val actions = Chunk(
        MigrationAction.AddField(DynamicOptic.root, "x", litInt(1)),
        MigrationAction.Rename(DynamicOptic.root, "a", "b")
      )
      val m = DynamicMigration(actions)
      assertTrue(m.reverse.reverse == m)
    },
    test("AddField then reverse (DropField) is round-trip") {
      val addAction = MigrationAction.AddField(DynamicOptic.root, "x", litInt(42))
      val migration = DynamicMigration(Chunk(addAction))
      val forward   = migration(emptyRecord)
      assertTrue(forward.isRight) && {
        val reversed = migration.reverse(forward.toOption.get)
        assertTrue(reversed == Right(emptyRecord))
      }
    },
    test("Rename then reverse is round-trip") {
      val action    = MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
      val migration = DynamicMigration(Chunk(action))
      val forward   = migration(simpleRecord)
      assertTrue(forward.isRight) && {
        val reversed = migration.reverse(forward.toOption.get)
        assertTrue(reversed == Right(simpleRecord))
      }
    },
    test("RenameCase then reverse is round-trip") {
      val variant   = DynamicValue.Variant("A", intDV(1))
      val action    = MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
      val migration = DynamicMigration(Chunk(action))
      val forward   = migration(variant)
      assertTrue(forward.isRight) && {
        val reversed = migration.reverse(forward.toOption.get)
        assertTrue(reversed == Right(variant))
      }
    },
    test("Optionalize then Mandate is round-trip") {
      val record    = DynamicValue.Record(Chunk(("x", intDV(10))))
      val optAction = MigrationAction.Optionalize(DynamicOptic.root, "x", litInt(0))
      val migration = DynamicMigration(Chunk(optAction))
      val forward   = migration(record)
      assertTrue(forward.isRight) && {
        // The reverse of Optionalize is Mandate with the same default
        val reversed = migration.reverse(forward.toOption.get)
        assertTrue(reversed == Right(record))
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Coercion Coverage
  // ─────────────────────────────────────────────────────────────────────────

  private def shortDV(s: Short): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Short(s))
  private def byteDV(b: Byte): DynamicValue         = DynamicValue.Primitive(PrimitiveValue.Byte(b))
  private def floatDV(f: Float): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Float(f))
  private def bigIntDV(b: BigInt): DynamicValue     = DynamicValue.Primitive(PrimitiveValue.BigInt(b))
  private def bigDecDV(b: BigDecimal): DynamicValue = DynamicValue.Primitive(PrimitiveValue.BigDecimal(b))
  private def charDV(c: Char): DynamicValue         = DynamicValue.Primitive(PrimitiveValue.Char(c))

  private def coerceField(value: DynamicValue, targetType: String): Either[MigrationError, DynamicValue] = {
    val record = DynamicValue.Record(Chunk(("x", value)))
    val action = MigrationAction.ChangeType(
      DynamicOptic.root,
      "x",
      MigrationExpr.Coerce(MigrationExpr.FieldRef(DynamicOptic.root), targetType),
      MigrationExpr.Coerce(MigrationExpr.FieldRef(DynamicOptic.root), "String") // dummy reverse
    )
    DynamicMigration(Chunk(action))(record).map(r => r.asInstanceOf[DynamicValue.Record].fields(0)._2)
  }

  private val coercionCoverageSuite = suite("coercion coverage")(
    // ── To String (primitiveToString branches) ─────────────────────────
    test("Coerce Short to String") {
      assertTrue(coerceField(shortDV(42), "String") == Right(stringDV("42")))
    },
    test("Coerce Byte to String") {
      assertTrue(coerceField(byteDV(7), "String") == Right(stringDV("7")))
    },
    test("Coerce Long to String") {
      assertTrue(coerceField(longDV(999L), "String") == Right(stringDV("999")))
    },
    test("Coerce Float to String") {
      assertTrue(coerceField(floatDV(3.14f), "String") == Right(stringDV("3.14")))
    },
    test("Coerce Double to String") {
      assertTrue(coerceField(doubleDV(2.718), "String") == Right(stringDV("2.718")))
    },
    test("Coerce BigInt to String") {
      assertTrue(coerceField(bigIntDV(BigInt(123)), "String") == Right(stringDV("123")))
    },
    test("Coerce BigDecimal to String") {
      assertTrue(coerceField(bigDecDV(BigDecimal("1.5")), "String") == Right(stringDV("1.5")))
    },
    test("Coerce Char to String") {
      assertTrue(coerceField(charDV('Z'), "String") == Right(stringDV("Z")))
    },
    test("Coerce String to String") {
      assertTrue(coerceField(stringDV("hello"), "String") == Right(stringDV("hello")))
    },
    // ── To Int (from various types) ────────────────────────────────────
    test("Coerce Byte to Int") {
      assertTrue(coerceField(byteDV(10), "Int") == Right(intDV(10)))
    },
    test("Coerce Short to Int") {
      assertTrue(coerceField(shortDV(200), "Int") == Right(intDV(200)))
    },
    test("Coerce Long to Int") {
      assertTrue(coerceField(longDV(50L), "Int") == Right(intDV(50)))
    },
    test("Coerce Float to Int") {
      assertTrue(coerceField(floatDV(9.7f), "Int") == Right(intDV(9)))
    },
    test("Coerce Double to Int") {
      assertTrue(coerceField(doubleDV(7.2), "Int") == Right(intDV(7)))
    },
    test("Coerce BigInt to Int") {
      assertTrue(coerceField(bigIntDV(BigInt(42)), "Int") == Right(intDV(42)))
    },
    test("Coerce Boolean to Int fails") {
      assertTrue(coerceField(boolDV(true), "Int").isLeft)
    },
    // ── To Long ────────────────────────────────────────────────────────
    test("Coerce Byte to Long") {
      assertTrue(coerceField(byteDV(5), "Long") == Right(longDV(5L)))
    },
    test("Coerce Short to Long") {
      assertTrue(coerceField(shortDV(100), "Long") == Right(longDV(100L)))
    },
    test("Coerce Long to Long") {
      assertTrue(coerceField(longDV(77L), "Long") == Right(longDV(77L)))
    },
    test("Coerce Float to Long") {
      assertTrue(coerceField(floatDV(3.9f), "Long") == Right(longDV(3L)))
    },
    test("Coerce Double to Long") {
      assertTrue(coerceField(doubleDV(8.1), "Long") == Right(longDV(8L)))
    },
    test("Coerce BigInt to Long") {
      assertTrue(coerceField(bigIntDV(BigInt(99)), "Long") == Right(longDV(99L)))
    },
    test("Coerce String to Long") {
      assertTrue(coerceField(stringDV("123"), "Long") == Right(longDV(123L)))
    },
    test("Coerce invalid String to Long fails") {
      assertTrue(coerceField(stringDV("abc"), "Long").isLeft)
    },
    // ── To Float ───────────────────────────────────────────────────────
    test("Coerce Int to Float") {
      assertTrue(coerceField(intDV(5), "Float") == Right(floatDV(5.0f)))
    },
    test("Coerce Long to Float") {
      assertTrue(coerceField(longDV(10L), "Float") == Right(floatDV(10.0f)))
    },
    test("Coerce Double to Float") {
      assertTrue(coerceField(doubleDV(1.5), "Float") == Right(floatDV(1.5f)))
    },
    test("Coerce String to Float") {
      assertTrue(coerceField(stringDV("2.5"), "Float") == Right(floatDV(2.5f)))
    },
    test("Coerce invalid String to Float fails") {
      assertTrue(coerceField(stringDV("xyz"), "Float").isLeft)
    },
    test("Coerce Byte to Float") {
      assertTrue(coerceField(byteDV(3), "Float") == Right(floatDV(3.0f)))
    },
    test("Coerce Short to Float") {
      assertTrue(coerceField(shortDV(7), "Float") == Right(floatDV(7.0f)))
    },
    test("Coerce Float to Float") {
      assertTrue(coerceField(floatDV(1.1f), "Float") == Right(floatDV(1.1f)))
    },
    // ── To Double ──────────────────────────────────────────────────────
    test("Coerce Byte to Double") {
      assertTrue(coerceField(byteDV(2), "Double") == Right(doubleDV(2.0)))
    },
    test("Coerce Short to Double") {
      assertTrue(coerceField(shortDV(3), "Double") == Right(doubleDV(3.0)))
    },
    test("Coerce Long to Double") {
      assertTrue(coerceField(longDV(4L), "Double") == Right(doubleDV(4.0)))
    },
    test("Coerce Float to Double") {
      assertTrue(coerceField(floatDV(1.5f), "Double") == Right(doubleDV(1.5f.toDouble)))
    },
    test("Coerce Double to Double") {
      assertTrue(coerceField(doubleDV(9.9), "Double") == Right(doubleDV(9.9)))
    },
    test("Coerce String to Double") {
      assertTrue(coerceField(stringDV("3.14"), "Double") == Right(doubleDV(3.14)))
    },
    test("Coerce invalid String to Double fails") {
      assertTrue(coerceField(stringDV("nope"), "Double").isLeft)
    },
    // ── To BigInt ──────────────────────────────────────────────────────
    test("Coerce Byte to BigInt") {
      assertTrue(coerceField(byteDV(1), "BigInt") == Right(bigIntDV(BigInt(1))))
    },
    test("Coerce Short to BigInt") {
      assertTrue(coerceField(shortDV(2), "BigInt") == Right(bigIntDV(BigInt(2))))
    },
    test("Coerce Int to BigInt") {
      assertTrue(coerceField(intDV(3), "BigInt") == Right(bigIntDV(BigInt(3))))
    },
    test("Coerce Long to BigInt") {
      assertTrue(coerceField(longDV(4L), "BigInt") == Right(bigIntDV(BigInt(4))))
    },
    test("Coerce BigInt to BigInt") {
      assertTrue(coerceField(bigIntDV(BigInt(5)), "BigInt") == Right(bigIntDV(BigInt(5))))
    },
    test("Coerce String to BigInt") {
      assertTrue(coerceField(stringDV("999"), "BigInt") == Right(bigIntDV(BigInt(999))))
    },
    test("Coerce invalid String to BigInt fails") {
      assertTrue(coerceField(stringDV("bad"), "BigInt").isLeft)
    },
    // ── To BigDecimal ──────────────────────────────────────────────────
    test("Coerce Byte to BigDecimal") {
      assertTrue(coerceField(byteDV(1), "BigDecimal") == Right(bigDecDV(BigDecimal(1))))
    },
    test("Coerce Short to BigDecimal") {
      assertTrue(coerceField(shortDV(2), "BigDecimal") == Right(bigDecDV(BigDecimal(2))))
    },
    test("Coerce Int to BigDecimal") {
      assertTrue(coerceField(intDV(3), "BigDecimal") == Right(bigDecDV(BigDecimal(3))))
    },
    test("Coerce Long to BigDecimal") {
      assertTrue(coerceField(longDV(4L), "BigDecimal") == Right(bigDecDV(BigDecimal(4))))
    },
    test("Coerce Float to BigDecimal") {
      assertTrue(coerceField(floatDV(1.5f), "BigDecimal") == Right(bigDecDV(BigDecimal(1.5f.toDouble))))
    },
    test("Coerce Double to BigDecimal") {
      assertTrue(coerceField(doubleDV(2.5), "BigDecimal") == Right(bigDecDV(BigDecimal(2.5))))
    },
    test("Coerce BigInt to BigDecimal") {
      assertTrue(coerceField(bigIntDV(BigInt(7)), "BigDecimal") == Right(bigDecDV(BigDecimal(BigInt(7)))))
    },
    test("Coerce BigDecimal to BigDecimal") {
      assertTrue(coerceField(bigDecDV(BigDecimal("3.3")), "BigDecimal") == Right(bigDecDV(BigDecimal("3.3"))))
    },
    test("Coerce String to BigDecimal") {
      assertTrue(coerceField(stringDV("1.23"), "BigDecimal") == Right(bigDecDV(BigDecimal("1.23"))))
    },
    test("Coerce invalid String to BigDecimal fails") {
      assertTrue(coerceField(stringDV("oops"), "BigDecimal").isLeft)
    },
    // ── To Boolean ─────────────────────────────────────────────────────
    test("Coerce String true to Boolean") {
      assertTrue(coerceField(stringDV("true"), "Boolean") == Right(boolDV(true)))
    },
    test("Coerce String false to Boolean") {
      assertTrue(coerceField(stringDV("false"), "Boolean") == Right(boolDV(false)))
    },
    test("Coerce invalid String to Boolean fails") {
      assertTrue(coerceField(stringDV("maybe"), "Boolean").isLeft)
    },
    test("Coerce Int to Boolean") {
      assertTrue(coerceField(intDV(1), "Boolean") == Right(boolDV(true))) &&
      assertTrue(coerceField(intDV(0), "Boolean") == Right(boolDV(false)))
    },
    test("Coerce Boolean to Boolean") {
      assertTrue(coerceField(boolDV(true), "Boolean") == Right(boolDV(true)))
    },
    test("Coerce Float to Boolean fails") {
      assertTrue(coerceField(floatDV(1.0f), "Boolean").isLeft)
    },
    // ── To Short ───────────────────────────────────────────────────────
    test("Coerce Byte to Short") {
      assertTrue(coerceField(byteDV(5), "Short") == Right(shortDV(5)))
    },
    test("Coerce Int to Short") {
      assertTrue(coerceField(intDV(100), "Short") == Right(shortDV(100)))
    },
    test("Coerce Long to Short") {
      assertTrue(coerceField(longDV(50L), "Short") == Right(shortDV(50)))
    },
    test("Coerce Short to Short") {
      assertTrue(coerceField(shortDV(7), "Short") == Right(shortDV(7)))
    },
    test("Coerce String to Short") {
      assertTrue(coerceField(stringDV("32"), "Short") == Right(shortDV(32)))
    },
    test("Coerce invalid String to Short fails") {
      assertTrue(coerceField(stringDV("big"), "Short").isLeft)
    },
    test("Coerce Float to Short fails") {
      assertTrue(coerceField(floatDV(1.0f), "Short").isLeft)
    },
    // ── To Byte ────────────────────────────────────────────────────────
    test("Coerce Short to Byte") {
      assertTrue(coerceField(shortDV(10), "Byte") == Right(byteDV(10)))
    },
    test("Coerce Int to Byte") {
      assertTrue(coerceField(intDV(20), "Byte") == Right(byteDV(20)))
    },
    test("Coerce Long to Byte") {
      assertTrue(coerceField(longDV(30L), "Byte") == Right(byteDV(30)))
    },
    test("Coerce Byte to Byte") {
      assertTrue(coerceField(byteDV(1), "Byte") == Right(byteDV(1)))
    },
    test("Coerce String to Byte") {
      assertTrue(coerceField(stringDV("8"), "Byte") == Right(byteDV(8)))
    },
    test("Coerce invalid String to Byte fails") {
      assertTrue(coerceField(stringDV("nah"), "Byte").isLeft)
    },
    test("Coerce Float to Byte fails") {
      assertTrue(coerceField(floatDV(1.0f), "Byte").isLeft)
    },
    // ── Unknown target type ────────────────────────────────────────────
    test("Coerce to unknown type fails") {
      assertTrue(coerceField(intDV(1), "Timestamp").isLeft)
    },
    // ── Coerce non-Primitive fails ─────────────────────────────────────
    test("Coerce Record to String fails") {
      assertTrue(coerceField(emptyRecord, "String").isLeft)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Arithmetic Coverage (Long, Float, Double, BigInt, BigDecimal)
  // ─────────────────────────────────────────────────────────────────────────

  private def evalArith(
    op: String,
    left: DynamicValue,
    right: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val exprCtor: (MigrationExpr, MigrationExpr) => MigrationExpr = op match {
      case "Add"      => MigrationExpr.Add(_, _)
      case "Subtract" => MigrationExpr.Subtract(_, _)
      case "Multiply" => MigrationExpr.Multiply(_, _)
      case "Divide"   => MigrationExpr.Divide(_, _)
    }
    val record = DynamicValue.Record(Chunk(("x", intDV(0))))
    val action = MigrationAction.TransformValue(
      DynamicOptic.root,
      "x",
      exprCtor(MigrationExpr.Literal(left), MigrationExpr.Literal(right)),
      litInt(0)
    )
    DynamicMigration(Chunk(action))(record).map(r => r.asInstanceOf[DynamicValue.Record].fields(0)._2)
  }

  private val arithmeticCoverageSuite = suite("arithmetic coverage")(
    // ── Long operations ────────────────────────────────────────────────
    test("Add Long + Long") {
      assertTrue(evalArith("Add", longDV(10L), longDV(20L)) == Right(longDV(30L)))
    },
    test("Subtract Long - Long") {
      assertTrue(evalArith("Subtract", longDV(30L), longDV(10L)) == Right(longDV(20L)))
    },
    test("Multiply Long * Long") {
      assertTrue(evalArith("Multiply", longDV(5L), longDV(6L)) == Right(longDV(30L)))
    },
    test("Divide Long / Long") {
      assertTrue(evalArith("Divide", longDV(20L), longDV(4L)) == Right(longDV(5L)))
    },
    test("Divide Long by zero") {
      assertTrue(evalArith("Divide", longDV(1L), longDV(0L)).isLeft)
    },
    // ── Float operations ───────────────────────────────────────────────
    test("Add Float + Float") {
      assertTrue(evalArith("Add", floatDV(1.5f), floatDV(2.5f)) == Right(floatDV(4.0f)))
    },
    test("Subtract Float - Float") {
      assertTrue(evalArith("Subtract", floatDV(5.0f), floatDV(2.0f)) == Right(floatDV(3.0f)))
    },
    test("Multiply Float * Float") {
      assertTrue(evalArith("Multiply", floatDV(3.0f), floatDV(2.0f)) == Right(floatDV(6.0f)))
    },
    test("Divide Float / Float") {
      assertTrue(evalArith("Divide", floatDV(10.0f), floatDV(4.0f)) == Right(floatDV(2.5f)))
    },
    test("Divide Float by zero") {
      assertTrue(evalArith("Divide", floatDV(1.0f), floatDV(0.0f)).isLeft)
    },
    // ── Double operations ──────────────────────────────────────────────
    test("Add Double + Double") {
      assertTrue(evalArith("Add", doubleDV(1.1), doubleDV(2.2)) == Right(doubleDV(3.3000000000000003)))
    },
    test("Subtract Double - Double") {
      assertTrue(evalArith("Subtract", doubleDV(5.0), doubleDV(3.0)) == Right(doubleDV(2.0)))
    },
    test("Multiply Double * Double") {
      assertTrue(evalArith("Multiply", doubleDV(2.0), doubleDV(3.0)) == Right(doubleDV(6.0)))
    },
    test("Divide Double / Double") {
      assertTrue(evalArith("Divide", doubleDV(10.0), doubleDV(4.0)) == Right(doubleDV(2.5)))
    },
    test("Divide Double by zero") {
      assertTrue(evalArith("Divide", doubleDV(1.0), doubleDV(0.0)).isLeft)
    },
    // ── BigInt operations ──────────────────────────────────────────────
    test("Add BigInt + BigInt") {
      assertTrue(evalArith("Add", bigIntDV(BigInt(10)), bigIntDV(BigInt(20))) == Right(bigIntDV(BigInt(30))))
    },
    test("Subtract BigInt - BigInt") {
      assertTrue(evalArith("Subtract", bigIntDV(BigInt(30)), bigIntDV(BigInt(10))) == Right(bigIntDV(BigInt(20))))
    },
    test("Multiply BigInt * BigInt") {
      assertTrue(evalArith("Multiply", bigIntDV(BigInt(5)), bigIntDV(BigInt(7))) == Right(bigIntDV(BigInt(35))))
    },
    test("Divide BigInt / BigInt") {
      assertTrue(evalArith("Divide", bigIntDV(BigInt(20)), bigIntDV(BigInt(5))) == Right(bigIntDV(BigInt(4))))
    },
    test("Divide BigInt by zero") {
      assertTrue(evalArith("Divide", bigIntDV(BigInt(1)), bigIntDV(BigInt(0))).isLeft)
    },
    // ── BigDecimal operations ──────────────────────────────────────────
    test("Add BigDecimal + BigDecimal") {
      assertTrue(
        evalArith("Add", bigDecDV(BigDecimal("1.1")), bigDecDV(BigDecimal("2.2"))) == Right(bigDecDV(BigDecimal("3.3")))
      )
    },
    test("Subtract BigDecimal - BigDecimal") {
      assertTrue(
        evalArith("Subtract", bigDecDV(BigDecimal("5.5")), bigDecDV(BigDecimal("2.2"))) == Right(
          bigDecDV(BigDecimal("3.3"))
        )
      )
    },
    test("Multiply BigDecimal * BigDecimal") {
      assertTrue(
        evalArith("Multiply", bigDecDV(BigDecimal("2.0")), bigDecDV(BigDecimal("3.5"))) == Right(
          bigDecDV(BigDecimal("7.00"))
        )
      )
    },
    test("Divide BigDecimal / BigDecimal") {
      assertTrue(
        evalArith("Divide", bigDecDV(BigDecimal("10.0")), bigDecDV(BigDecimal("4.0"))) == Right(
          bigDecDV(BigDecimal("2.5"))
        )
      )
    },
    test("Divide BigDecimal by zero") {
      assertTrue(evalArith("Divide", bigDecDV(BigDecimal("1.0")), bigDecDV(BigDecimal("0"))).isLeft)
    },
    // ── Type promotion ─────────────────────────────────────────────────
    test("Add Int + Float promotes to Float") {
      assertTrue(evalArith("Add", intDV(2), floatDV(3.5f)) == Right(floatDV(5.5f)))
    },
    test("Add Int + Double promotes to Double") {
      assertTrue(evalArith("Add", intDV(1), doubleDV(2.5)) == Right(doubleDV(3.5)))
    },
    test("Add Long + BigInt promotes to BigInt") {
      assertTrue(evalArith("Add", longDV(10L), bigIntDV(BigInt(20))) == Right(bigIntDV(BigInt(30))))
    },
    test("Add Int + BigDecimal promotes to BigDecimal") {
      assertTrue(evalArith("Add", intDV(1), bigDecDV(BigDecimal("2.5"))) == Right(bigDecDV(BigDecimal("3.5"))))
    },
    // ── Non-numeric operands ───────────────────────────────────────────
    test("Add with Boolean operands fails") {
      assertTrue(evalArith("Add", boolDV(true), boolDV(false)).isLeft)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Navigation Edge Cases
  // ─────────────────────────────────────────────────────────────────────────

  private val navigationEdgeCaseSuite = suite("navigation edge cases")(
    test("navigate through AtIndex out of bounds") {
      val seq    = DynamicValue.Sequence(Chunk(intDV(1)))
      val action = MigrationAction.AddField(DynamicOptic.root.at(5), "x", litInt(0))
      val result = DynamicMigration(Chunk(action))(seq)
      assertTrue(result.isLeft)
    },
    test("navigate through AtIndex on non-Sequence") {
      val record = DynamicValue.Record(Chunk(("x", intDV(1))))
      val action = MigrationAction.AddField(DynamicOptic.root.at(0), "x", litInt(0))
      val result = DynamicMigration(Chunk(action))(record)
      assertTrue(result.isLeft)
    },
    test("navigate through Field on non-Record") {
      val seq    = DynamicValue.Sequence(Chunk(intDV(1)))
      val action = MigrationAction.AddField(DynamicOptic.root.field("inner"), "x", litInt(0))
      val result = DynamicMigration(Chunk(action))(seq)
      assertTrue(result.isLeft)
    },
    test("navigate through Case on non-Variant") {
      val record = DynamicValue.Record(Chunk(("x", intDV(1))))
      val action = MigrationAction.RenameCase(DynamicOptic.root.caseOf("Foo"), "A", "B")
      val result = DynamicMigration(Chunk(action))(record)
      assertTrue(result.isLeft)
    },
    test("navigate through Case with wrong case name") {
      val variant = DynamicValue.Variant("Wrong", intDV(1))
      val action  = MigrationAction.RenameCase(DynamicOptic.root.caseOf("Expected"), "A", "B")
      val result  = DynamicMigration(Chunk(action))(variant)
      assertTrue(result.isLeft)
    },
    test("navigate through AtMapKey") {
      val key    = DynamicValue.Primitive(PrimitiveValue.String("k"))
      val m      = DynamicValue.Map(Chunk((key, DynamicValue.Record(Chunk(("v", intDV(1)))))))
      val action = MigrationAction.Rename(DynamicOptic(IndexedSeq(DynamicOptic.Node.AtMapKey(key))), "v", "val")
      val result = DynamicMigration(Chunk(action))(m)
      assertTrue(result.isRight)
    },
    test("navigate through AtMapKey not found") {
      val key     = DynamicValue.Primitive(PrimitiveValue.String("k"))
      val missing = DynamicValue.Primitive(PrimitiveValue.String("missing"))
      val m       = DynamicValue.Map(Chunk((key, intDV(1))))
      val action  =
        MigrationAction.AddField(DynamicOptic(IndexedSeq(DynamicOptic.Node.AtMapKey(missing))), "x", litInt(0))
      val result = DynamicMigration(Chunk(action))(m)
      assertTrue(result.isLeft)
    },
    test("navigate through AtMapKey on non-Map") {
      val key    = DynamicValue.Primitive(PrimitiveValue.String("k"))
      val record = DynamicValue.Record(Chunk(("x", intDV(1))))
      val action = MigrationAction.AddField(DynamicOptic(IndexedSeq(DynamicOptic.Node.AtMapKey(key))), "x", litInt(0))
      val result = DynamicMigration(Chunk(action))(record)
      assertTrue(result.isLeft)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // UnwrapOption Edge Cases
  // ─────────────────────────────────────────────────────────────────────────

  private val unwrapOptionEdgeSuite = suite("unwrapOption edge cases")(
    test("Mandate on non-optional value passes through") {
      val record    = DynamicValue.Record(Chunk(("x", intDV(42))))
      val action    = MigrationAction.Mandate(DynamicOptic.root, "x", litInt(0))
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(record)
      assertTrue(result == Right(DynamicValue.Record(Chunk(("x", intDV(42))))))
    },
    test("Mandate on Variant Some with direct value") {
      val someVal   = DynamicValue.Variant("Some", intDV(99))
      val record    = DynamicValue.Record(Chunk(("x", someVal)))
      val action    = MigrationAction.Mandate(DynamicOptic.root, "x", litInt(0))
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(record)
      assertTrue(result == Right(DynamicValue.Record(Chunk(("x", intDV(99))))))
    },
    test("Mandate on empty Record uses default") {
      val record    = DynamicValue.Record(Chunk(("x", DynamicValue.Record(Chunk.empty))))
      val action    = MigrationAction.Mandate(DynamicOptic.root, "x", litInt(7))
      val migration = DynamicMigration(Chunk(action))
      val result    = migration(record)
      assertTrue(result == Right(DynamicValue.Record(Chunk(("x", intDV(7))))))
    }
  )
}
