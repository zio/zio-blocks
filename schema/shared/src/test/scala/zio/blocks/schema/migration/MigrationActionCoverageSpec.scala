package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationActionCoverageSpec extends SchemaBaseSpec {

  private val root = DynamicOptic.root
  private val litI = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
  private val litS = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationActionCoverageSpec")(
    suite("AddField reverse")(
      test("reverse is DropField") {
        val action = MigrationAction.AddField(root, "x", litI)
        assertTrue(action.reverse.isInstanceOf[MigrationAction.DropField])
      },
      test("reverse preserves path and name") {
        val action = MigrationAction.AddField(root.field("a"), "x", litI)
        val rev    = action.reverse.asInstanceOf[MigrationAction.DropField]
        assertTrue(rev.at == root.field("a") && rev.name == "x" && rev.defaultForReverse == litI)
      }
    ),
    suite("DropField reverse")(
      test("reverse is AddField") {
        val action = MigrationAction.DropField(root, "x", litI)
        assertTrue(action.reverse.isInstanceOf[MigrationAction.AddField])
      },
      test("reverse preserves path and name") {
        val action = MigrationAction.DropField(root, "x", litS)
        val rev    = action.reverse.asInstanceOf[MigrationAction.AddField]
        assertTrue(rev.name == "x" && rev.default == litS)
      }
    ),
    suite("RenameField reverse")(
      test("reverse swaps from/to") {
        val action = MigrationAction.RenameField(root, "old", "new")
        val rev    = action.reverse.asInstanceOf[MigrationAction.RenameField]
        assertTrue(rev.from == "new" && rev.to == "old")
      },
      test("at field accessor") {
        val action = MigrationAction.RenameField(root.field("x"), "a", "b")
        assertTrue(action.at.nodes.length == 1)
      }
    ),
    suite("TransformValue reverse")(
      test("reverse swaps transform/reverseTransform") {
        val t1     = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val t2     = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
        val action = MigrationAction.TransformValue(root, t1, t2)
        val rev    = action.reverse.asInstanceOf[MigrationAction.TransformValue]
        assertTrue(rev.transform == t2 && rev.reverseTransform == t1)
      }
    ),
    suite("Mandate reverse")(
      test("reverse is Optionalize") {
        val action = MigrationAction.Mandate(root.field("x"), litI)
        val rev    = action.reverse
        assertTrue(rev.isInstanceOf[MigrationAction.Optionalize])
      },
      test("reverse preserves default") {
        val action = MigrationAction.Mandate(root.field("x"), litS)
        val rev    = action.reverse.asInstanceOf[MigrationAction.Optionalize]
        assertTrue(rev.defaultForReverse == litS)
      }
    ),
    suite("Optionalize reverse")(
      test("reverse is Mandate") {
        val action = MigrationAction.Optionalize(root.field("x"), litI)
        assertTrue(action.reverse.isInstanceOf[MigrationAction.Mandate])
      },
      test("default Optionalize uses DefaultValue") {
        val action = MigrationAction.Optionalize(root.field("x"))
        assertTrue(action.defaultForReverse == DynamicSchemaExpr.DefaultValue)
      }
    ),
    suite("ChangeType reverse")(
      test("reverse swaps converter/reverseConverter") {
        val c1     = DynamicSchemaExpr.CoercePrimitive(litI, "Long")
        val c2     = DynamicSchemaExpr.CoercePrimitive(litI, "Int")
        val action = MigrationAction.ChangeType(root, c1, c2)
        val rev    = action.reverse.asInstanceOf[MigrationAction.ChangeType]
        assertTrue(rev.converter == c2 && rev.reverseConverter == c1)
      }
    ),
    suite("Join reverse")(
      test("reverse is Split") {
        val action = MigrationAction.Join(root.field("c"), Vector(root.field("a"), root.field("b")), litS, litS)
        assertTrue(action.reverse.isInstanceOf[MigrationAction.Split])
      },
      test("reverse preserves combiner/splitter swap") {
        val combiner = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("comb")))
        val splitter = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("split")))
        val action   = MigrationAction.Join(root.field("c"), Vector(root.field("a")), combiner, splitter)
        val rev      = action.reverse.asInstanceOf[MigrationAction.Split]
        assertTrue(rev.splitter == splitter && rev.combiner == combiner)
      }
    ),
    suite("Split reverse")(
      test("reverse is Join") {
        val action = MigrationAction.Split(root.field("c"), Vector(root.field("a")), litS, litS)
        assertTrue(action.reverse.isInstanceOf[MigrationAction.Join])
      },
      test("reverse preserves combiner/splitter swap") {
        val splitter = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("s")))
        val combiner = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("c")))
        val action   = MigrationAction.Split(root.field("x"), Vector(root.field("a")), splitter, combiner)
        val rev      = action.reverse.asInstanceOf[MigrationAction.Join]
        assertTrue(rev.combiner == combiner && rev.splitter == splitter)
      }
    ),
    suite("RenameCase reverse")(
      test("reverse swaps from/to") {
        val action = MigrationAction.RenameCase(root, "Dog", "Hound")
        val rev    = action.reverse.asInstanceOf[MigrationAction.RenameCase]
        assertTrue(rev.from == "Hound" && rev.to == "Dog")
      }
    ),
    suite("TransformCase reverse")(
      test("reverse reverses nested actions") {
        val inner = Vector(
          MigrationAction.AddField(root, "x", litI),
          MigrationAction.RenameField(root, "a", "b")
        )
        val action = MigrationAction.TransformCase(root, "Dog", inner)
        val rev    = action.reverse.asInstanceOf[MigrationAction.TransformCase]
        assertTrue(
          rev.caseName == "Dog" &&
            rev.actions.length == 2 &&
            rev.actions(0).isInstanceOf[MigrationAction.RenameField] &&
            rev.actions(1).isInstanceOf[MigrationAction.DropField]
        )
      }
    ),
    suite("TransformElements reverse")(
      test("reverse swaps transform/reverseTransform") {
        val t1     = litI
        val t2     = litS
        val action = MigrationAction.TransformElements(root, t1, t2)
        val rev    = action.reverse.asInstanceOf[MigrationAction.TransformElements]
        assertTrue(rev.transform == t2 && rev.reverseTransform == t1)
      }
    ),
    suite("TransformKeys reverse")(
      test("reverse swaps transform/reverseTransform") {
        val t1     = litS
        val t2     = litI
        val action = MigrationAction.TransformKeys(root, t1, t2)
        val rev    = action.reverse.asInstanceOf[MigrationAction.TransformKeys]
        assertTrue(rev.transform == t2 && rev.reverseTransform == t1)
      }
    ),
    suite("TransformValues reverse")(
      test("reverse swaps transform/reverseTransform") {
        val t1     = litI
        val t2     = litS
        val action = MigrationAction.TransformValues(root, t1, t2)
        val rev    = action.reverse.asInstanceOf[MigrationAction.TransformValues]
        assertTrue(rev.transform == t2 && rev.reverseTransform == t1)
      }
    ),
    suite("Identity")(
      test("at is root") {
        assertTrue(MigrationAction.Identity.at == root)
      },
      test("reverse is Identity") {
        assertTrue(MigrationAction.Identity.reverse == MigrationAction.Identity)
      }
    )
  )
}
