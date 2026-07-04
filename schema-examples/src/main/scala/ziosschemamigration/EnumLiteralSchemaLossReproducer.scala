package ziosschemamigration

import zio.blocks.schema._
import zio.blocks.schema.migration._

// Reproducer: DynamicSchemaExpr.Literal now carries Schema[_] alongside DynamicValue.
//
// Before the fix, SchemaExpr.literal converted the value to DynamicValue and stored
// only that in DynamicSchemaExpr.Literal, discarding the Schema.  For a no-field
// case object every sealed trait — whether all-no-field (pure enum) or mixed —
// produces the same DynamicValue.Variant(caseName, Record.empty), making the two
// indistinguishable in the DynamicSchemaExpr tree.
//
// Fix applied: DynamicSchemaExpr.Literal(value: DynamicValue, schema: Schema[_])
// SchemaExpr.literal now passes the schema through:
//   DynamicSchemaExpr.Literal(schema.toDynamicValue(value), schema)
object EnumLiteralSchemaLossReproducer extends App {

  // All-no-field sealed trait (pure enum).
  sealed trait Color
  case object Red  extends Color
  case object Blue extends Color
  implicit val colorSchema: Schema[Color] = Schema.derived[Color]

  // Mixed sealed trait: same "Red" case name, but also has a data-carrying case.
  sealed trait MixedColor
  object MixedColor {
    case object Red              extends MixedColor
    case class  Dark(shade: Int) extends MixedColor
  }
  implicit val mixedColorSchema: Schema[MixedColor] = Schema.derived[MixedColor]

  case class TaskV1(name: String)
  case class TaskV2(name: String, color: Color)
  case class TaskV3(name: String, color: MixedColor)
  implicit val taskV1Schema: Schema[TaskV1] = Schema.derived[TaskV1]
  implicit val taskV2Schema: Schema[TaskV2] = Schema.derived[TaskV2]
  implicit val taskV3Schema: Schema[TaskV3] = Schema.derived[TaskV3]

  def check(label: String, ok: Boolean): Unit =
    println(s"  [${if (ok) "OK  " else "FAIL"}] $label")

  // ── 1. Root cause (DynamicValue is still structurally ambiguous) ──────────
  println("1. Both sealed traits produce identical DynamicValue for the same case name")
  val colorDV      = Schema[Color].toDynamicValue(Red)
  val mixedColorDV = Schema[MixedColor].toDynamicValue(MixedColor.Red)
  println(s"     Color.Red      → $colorDV")
  println(s"     MixedColor.Red → $mixedColorDV")
  check("DynamicValues are equal (DynamicValue itself is ambiguous)", colorDV == mixedColorDV)
  println()

  // ── 2. Fix: schema now survives in DynamicSchemaExpr.Literal ─────────────
  println("2. SchemaExpr.literal preserves isEnumeration in DynamicSchemaExpr.Literal ...")
  val colorLit      = SchemaExpr.literal[Any, Color](Red)
  val mixedColorLit = SchemaExpr.literal[Any, MixedColor](MixedColor.Red)
  check("Color     isEnumeration = true",  colorLit.outputSchema.reflect.isEnumeration)
  check("MixedColor isEnumeration = false", !mixedColorLit.outputSchema.reflect.isEnumeration)
  println("   DynamicSchemaExpr.Literal now carries the Schema:")
  println(s"     Color literal dynamic     → ${colorLit.dynamic}")
  println(s"     MixedColor literal dynamic → ${mixedColorLit.dynamic}")
  check("DynamicSchemaExpr.Literals are DIFFERENT", colorLit.dynamic != mixedColorLit.dynamic)
  println()

  // ── 3. Migration representation contract restored ─────────────────────────
  println("3. addField migrations for Color vs MixedColor produce distinct DynamicMigrations")
  val migToV2 = Migration.newBuilder[TaskV1, TaskV2].addField(_.color, SchemaExpr.literal[Any, Color](Red)).build
  val migToV3 = Migration.newBuilder[TaskV1, TaskV3].addField(_.color, SchemaExpr.literal[Any, MixedColor](MixedColor.Red)).build
  println(s"     migToV2 DynamicMigration → ${migToV2.dynamicMigration}")
  println(s"     migToV3 DynamicMigration → ${migToV3.dynamicMigration}")
  check("DynamicMigrations are DIFFERENT", migToV2.dynamicMigration != migToV3.dynamicMigration)
  println()

  // ── 4. Interpreter benefit: isEnumeration is now recoverable ────────────
  println("4. An interpreter can recover isEnumeration from the schema in each Literal")
  val colorIsEnum = migToV2.dynamicMigration.actions.head match {
    case migration.MigrationAction.AddField(_, DynamicSchemaExpr.Literal(_, schema)) =>
      schema.reflect.isEnumeration
    case _ => false
  }
  val mixedIsEnum = migToV3.dynamicMigration.actions.head match {
    case migration.MigrationAction.AddField(_, DynamicSchemaExpr.Literal(_, schema)) =>
      schema.reflect.isEnumeration
    case _ => true
  }
  println(s"     Color migration literal isEnumeration     → $colorIsEnum  (all-no-field: correct)")
  println(s"     MixedColor migration literal isEnumeration → $mixedIsEnum (mixed: correct)")
  check("Color literal schema reports isEnumeration = true",  colorIsEnum)
  check("MixedColor literal schema reports isEnumeration = false", !mixedIsEnum)
}
