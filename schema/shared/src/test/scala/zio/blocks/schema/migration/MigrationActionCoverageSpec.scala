package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Supplementary coverage tests for [[MigrationAction]].
 *
 * Tests here cover edge cases and accessors not exercised by
 * [[MigrationActionSpec]], which already covers basic reverse semantics.
 */
object MigrationActionCoverageSpec extends SchemaBaseSpec {

  private val root = DynamicOptic.root
  private val litI = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
  private val litS = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationActionCoverageSpec")(
    test("AddField reverse preserves nested path and name") {
      val action = MigrationAction.AddField(root.field("a"), "x", litI)
      val rev    = action.reverse.asInstanceOf[MigrationAction.DropField]
      assertTrue(rev.at == root.field("a") && rev.name == "x" && rev.defaultForReverse == litI)
    },
    test("DropField reverse preserves default expression") {
      val action = MigrationAction.DropField(root, "x", litS)
      val rev    = action.reverse.asInstanceOf[MigrationAction.AddField]
      assertTrue(rev.name == "x" && rev.default == litS)
    },
    test("RenameField at field accessor has correct node count") {
      val action = MigrationAction.RenameField(root.field("x"), "a", "b")
      assertTrue(action.at.nodes.length == 1)
    },
    test("Mandate reverse preserves default into Optionalize") {
      val action = MigrationAction.Mandate(root.field("x"), litS)
      val rev    = action.reverse.asInstanceOf[MigrationAction.Optionalize]
      assertTrue(rev.defaultForReverse == litS)
    },
    test("Optionalize without explicit default uses DefaultValue") {
      val action = MigrationAction.Optionalize(root.field("x"))
      assertTrue(action.defaultForReverse == DynamicSchemaExpr.DefaultValue)
    },
    test("Join reverse preserves combiner/splitter swap") {
      val combiner = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("comb")))
      val splitter = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("split")))
      val action   = MigrationAction.Join(root.field("c"), Vector(root.field("a")), combiner, splitter)
      val rev      = action.reverse.asInstanceOf[MigrationAction.Split]
      assertTrue(rev.splitter == splitter && rev.combiner == combiner)
    },
    test("Split reverse preserves combiner/splitter swap") {
      val splitter = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("s")))
      val combiner = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("c")))
      val action   = MigrationAction.Split(root.field("x"), Vector(root.field("a")), splitter, combiner)
      val rev      = action.reverse.asInstanceOf[MigrationAction.Join]
      assertTrue(rev.combiner == combiner && rev.splitter == splitter)
    }
  )
}
