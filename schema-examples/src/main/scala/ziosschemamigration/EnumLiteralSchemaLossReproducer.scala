package ziosschemamigration

import zio.blocks.schema._
import zio.blocks.schema.migration._

// Reproducer: DynamicSchemaExpr.Literal drops Schema, breaking migration for enum cases.
//
// SchemaExpr.literal converts the value to DynamicValue and stores only that in
// DynamicSchemaExpr.Literal, discarding the Schema.  For a no-field case object,
// every sealed trait — whether all-no-field (pure enum) or mixed — produces the
// same DynamicValue.Variant(caseName, Record.empty).  The two are indistinguishable
// in the DynamicSchemaExpr tree.
//
// Consequence for migration: addField(_.f, literal(Color.Red)) and
// addField(_.f, literal(MixedColor.Red)) build the same DynamicMigration.
// Any interpreter that processes the DynamicMigration cannot recover which
// sealed-trait encoding was intended.
//
// Fix: DynamicSchemaExpr.Literal should carry Schema[_] alongside DynamicValue.
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

  // ── 1. Root cause ────────────────────────────────────────────────────────
  println("1. Both sealed traits produce identical DynamicValue for the same case name")
  val colorDV     = Schema[Color].toDynamicValue(Red)
  val mixedColorDV = Schema[MixedColor].toDynamicValue(MixedColor.Red)
  println(s"     Color.Red      → $colorDV")
  println(s"     MixedColor.Red → $mixedColorDV")
  check("DynamicValues are equal", colorDV == mixedColorDV)
  println()

  // ── 2. Schema survives in SchemaExpr but not DynamicSchemaExpr ───────────
  println("2. SchemaExpr.literal preserves isEnumeration in outputSchema ...")
  val colorLit     = SchemaExpr.literal[Any, Color](Red)
  val mixedColorLit = SchemaExpr.literal[Any, MixedColor](MixedColor.Red)
  check("Color     isEnumeration = true",  colorLit.outputSchema.reflect.isEnumeration)
  check("MixedColor isEnumeration = false", !mixedColorLit.outputSchema.reflect.isEnumeration)
  println("   ... but DynamicSchemaExpr.Literal drops the Schema:")
  println(s"     Color literal dynamic     → ${colorLit.dynamic}")
  println(s"     MixedColor literal dynamic → ${mixedColorLit.dynamic}")
  check("DynamicSchemaExpr.Literals are DIFFERENT (expect FAIL)", colorLit.dynamic != mixedColorLit.dynamic)
  println()

  // ── 3. Migration representation contract broken ───────────────────────────
  println("3. addField migrations for Color vs MixedColor produce the same DynamicMigration")
  val migToV2 = Migration.newBuilder[TaskV1, TaskV2].addField(_.color, SchemaExpr.literal[Any, Color](Red)).build
  val migToV3 = Migration.newBuilder[TaskV1, TaskV3].addField(_.color, SchemaExpr.literal[Any, MixedColor](MixedColor.Red)).build
  println(s"     migToV2 DynamicMigration → ${migToV2.dynamicMigration}")
  println(s"     migToV3 DynamicMigration → ${migToV3.dynamicMigration}")
  check("DynamicMigrations are DIFFERENT (expect FAIL)", migToV2.dynamicMigration != migToV3.dynamicMigration)
  println()

  // ── 4. Execution contract broken ─────────────────────────────────────────
  println("4. DynamicMigration built for Color is silently accepted by MixedColor schema")
  val inputDV = Schema[TaskV1].toDynamicValue(TaskV1("Alice"))
  val migrated = migToV2.dynamicMigration(inputDV)
  val wrongDecode = migrated.flatMap(dv => Schema[TaskV3].fromDynamicValue(dv))
  println(s"     Migration target: TaskV2 (Color — all-no-field enum)")
  println(s"     Decoded with:     Schema[TaskV3] (MixedColor — mixed enum)")
  println(s"     Result:           $wrongDecode")
  check("Decoding with wrong schema returns Left (expect FAIL)", wrongDecode.isLeft)
}
