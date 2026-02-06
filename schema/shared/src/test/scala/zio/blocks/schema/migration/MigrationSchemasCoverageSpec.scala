package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.json.{DiscriminatorKind, JsonBinaryCodecDeriver}
import zio.test._

object MigrationSchemasCoverageSpec extends SchemaBaseSpec {
  import MigrationSchemas._

  private val deriver = JsonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("_type"))

  private def roundTrip[A: Schema](a: A): Boolean = {
    val codec = Schema[A].derive(deriver)
    val json  = codec.encodeToString(a)
    codec.decode(json) == Right(a)
  }

  private val root  = DynamicOptic.root
  private val path  = root.field("name")
  private val litI  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
  private val litS  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("x")))
  private val pathE = DynamicSchemaExpr.Path(root.field("age"))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSchemasCoverageSpec")(
    suite("DynamicSchemaExpr round-trip serialization")(
      test("Literal") {
        assertTrue(roundTrip[DynamicSchemaExpr](litI))
      },
      test("Path") {
        assertTrue(roundTrip[DynamicSchemaExpr](pathE))
      },
      test("DefaultValue") {
        assertTrue(roundTrip[DynamicSchemaExpr](DynamicSchemaExpr.DefaultValue))
      },
      test("ResolvedDefault") {
        assertTrue(
          roundTrip[DynamicSchemaExpr](
            DynamicSchemaExpr.ResolvedDefault(DynamicValue.Primitive(PrimitiveValue.String("def")))
          )
        )
      },
      test("Not") {
        assertTrue(roundTrip[DynamicSchemaExpr](DynamicSchemaExpr.Not(litI)))
      },
      test("Logical And") {
        val expr: DynamicSchemaExpr =
          DynamicSchemaExpr.Logical(litI, litS, DynamicSchemaExpr.LogicalOperator.And)
        assertTrue(roundTrip[DynamicSchemaExpr](expr))
      },
      test("Logical Or") {
        val expr: DynamicSchemaExpr =
          DynamicSchemaExpr.Logical(litI, litS, DynamicSchemaExpr.LogicalOperator.Or)
        assertTrue(roundTrip[DynamicSchemaExpr](expr))
      },
      test("Relational LessThan") {
        val expr: DynamicSchemaExpr =
          DynamicSchemaExpr.Relational(litI, litI, DynamicSchemaExpr.RelationalOperator.LessThan)
        assertTrue(roundTrip[DynamicSchemaExpr](expr))
      },
      test("Relational LessThanOrEqual") {
        val expr: DynamicSchemaExpr =
          DynamicSchemaExpr.Relational(litI, litI, DynamicSchemaExpr.RelationalOperator.LessThanOrEqual)
        assertTrue(roundTrip[DynamicSchemaExpr](expr))
      },
      test("Relational GreaterThan") {
        val expr: DynamicSchemaExpr =
          DynamicSchemaExpr.Relational(litI, litI, DynamicSchemaExpr.RelationalOperator.GreaterThan)
        assertTrue(roundTrip[DynamicSchemaExpr](expr))
      },
      test("Relational GreaterThanOrEqual") {
        val expr: DynamicSchemaExpr =
          DynamicSchemaExpr.Relational(litI, litI, DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual)
        assertTrue(roundTrip[DynamicSchemaExpr](expr))
      },
      test("Relational Equal") {
        val expr: DynamicSchemaExpr =
          DynamicSchemaExpr.Relational(litI, litI, DynamicSchemaExpr.RelationalOperator.Equal)
        assertTrue(roundTrip[DynamicSchemaExpr](expr))
      },
      test("Relational NotEqual") {
        val expr: DynamicSchemaExpr =
          DynamicSchemaExpr.Relational(litI, litI, DynamicSchemaExpr.RelationalOperator.NotEqual)
        assertTrue(roundTrip[DynamicSchemaExpr](expr))
      },
      test("Arithmetic Add") {
        val expr: DynamicSchemaExpr =
          DynamicSchemaExpr.Arithmetic(litI, litI, DynamicSchemaExpr.ArithmeticOperator.Add)
        assertTrue(roundTrip[DynamicSchemaExpr](expr))
      },
      test("Arithmetic Subtract") {
        val expr: DynamicSchemaExpr =
          DynamicSchemaExpr.Arithmetic(litI, litI, DynamicSchemaExpr.ArithmeticOperator.Subtract)
        assertTrue(roundTrip[DynamicSchemaExpr](expr))
      },
      test("Arithmetic Multiply") {
        val expr: DynamicSchemaExpr =
          DynamicSchemaExpr.Arithmetic(litI, litI, DynamicSchemaExpr.ArithmeticOperator.Multiply)
        assertTrue(roundTrip[DynamicSchemaExpr](expr))
      },
      test("StringConcat") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.StringConcat(litS, litS)
        assertTrue(roundTrip[DynamicSchemaExpr](expr))
      },
      test("StringLength") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.StringLength(litS)
        assertTrue(roundTrip[DynamicSchemaExpr](expr))
      },
      test("CoercePrimitive") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.CoercePrimitive(litI, "String")
        assertTrue(roundTrip[DynamicSchemaExpr](expr))
      }
    ),
    suite("MigrationAction round-trip serialization")(
      test("AddField") {
        val a: MigrationAction = MigrationAction.AddField(root, "x", litI)
        assertTrue(roundTrip[MigrationAction](a))
      },
      test("DropField") {
        val a: MigrationAction = MigrationAction.DropField(root, "x", litI)
        assertTrue(roundTrip[MigrationAction](a))
      },
      test("RenameField") {
        val a: MigrationAction = MigrationAction.RenameField(root, "old", "new")
        assertTrue(roundTrip[MigrationAction](a))
      },
      test("TransformValue") {
        val a: MigrationAction = MigrationAction.TransformValue(path, litI, litI)
        assertTrue(roundTrip[MigrationAction](a))
      },
      test("Mandate") {
        val a: MigrationAction = MigrationAction.Mandate(path, litI)
        assertTrue(roundTrip[MigrationAction](a))
      },
      test("Optionalize") {
        val a: MigrationAction = MigrationAction.Optionalize(path, litI)
        assertTrue(roundTrip[MigrationAction](a))
      },
      test("ChangeType") {
        val a: MigrationAction = MigrationAction.ChangeType(path, litI, litI)
        assertTrue(roundTrip[MigrationAction](a))
      },
      test("Join") {
        val a: MigrationAction = MigrationAction.Join(path, Vector(root.field("a"), root.field("b")), litS, litS)
        assertTrue(roundTrip[MigrationAction](a))
      },
      test("Split") {
        val a: MigrationAction = MigrationAction.Split(path, Vector(root.field("a"), root.field("b")), litS, litS)
        assertTrue(roundTrip[MigrationAction](a))
      },
      test("RenameCase") {
        val a: MigrationAction = MigrationAction.RenameCase(root, "Old", "New")
        assertTrue(roundTrip[MigrationAction](a))
      },
      test("TransformCase") {
        val a: MigrationAction = MigrationAction.TransformCase(
          root,
          "Active",
          Vector(MigrationAction.RenameField(root, "a", "b"))
        )
        assertTrue(roundTrip[MigrationAction](a))
      },
      test("TransformElements") {
        val a: MigrationAction = MigrationAction.TransformElements(root, litI, litI)
        assertTrue(roundTrip[MigrationAction](a))
      },
      test("TransformKeys") {
        val a: MigrationAction = MigrationAction.TransformKeys(root, litS, litS)
        assertTrue(roundTrip[MigrationAction](a))
      },
      test("TransformValues") {
        val a: MigrationAction = MigrationAction.TransformValues(root, litI, litI)
        assertTrue(roundTrip[MigrationAction](a))
      },
      test("Identity") {
        val a: MigrationAction = MigrationAction.Identity
        assertTrue(roundTrip[MigrationAction](a))
      }
    ),
    suite("DynamicMigration round-trip serialization")(
      test("empty migration") {
        assertTrue(roundTrip[DynamicMigration](DynamicMigration.empty))
      },
      test("single action migration") {
        assertTrue(roundTrip[DynamicMigration](DynamicMigration(MigrationAction.Identity)))
      },
      test("multi-action migration") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.AddField(root, "x", litI),
            MigrationAction.RenameField(root, "a", "b"),
            MigrationAction.DropField(root, "c", litS)
          )
        )
        assertTrue(roundTrip[DynamicMigration](m))
      },
      test("complex migration with nested expressions") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.TransformValue(
              root.field("age"),
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Path(root.field("age")),
                DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
                DynamicSchemaExpr.ArithmeticOperator.Add
              ),
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Path(root.field("age")),
                DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
                DynamicSchemaExpr.ArithmeticOperator.Subtract
              )
            ),
            MigrationAction.Optionalize(root.field("name")),
            MigrationAction.Mandate(
              root.field("email"),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("unknown@test.com")))
            )
          )
        )
        assertTrue(roundTrip[DynamicMigration](m))
      }
    ),
    suite("Operator schemas")(
      test("LogicalOperator And discriminator") {
        val codec = Schema[DynamicSchemaExpr.LogicalOperator].derive(deriver)
        val json  = codec.encodeToString(DynamicSchemaExpr.LogicalOperator.And)
        assertTrue(codec.decode(json) == Right(DynamicSchemaExpr.LogicalOperator.And))
      },
      test("LogicalOperator Or discriminator") {
        val codec = Schema[DynamicSchemaExpr.LogicalOperator].derive(deriver)
        val json  = codec.encodeToString(DynamicSchemaExpr.LogicalOperator.Or)
        assertTrue(codec.decode(json) == Right(DynamicSchemaExpr.LogicalOperator.Or))
      },
      test("RelationalOperator all variants") {
        val codec = Schema[DynamicSchemaExpr.RelationalOperator].derive(deriver)
        val ops   = Vector(
          DynamicSchemaExpr.RelationalOperator.LessThan,
          DynamicSchemaExpr.RelationalOperator.LessThanOrEqual,
          DynamicSchemaExpr.RelationalOperator.GreaterThan,
          DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual,
          DynamicSchemaExpr.RelationalOperator.Equal,
          DynamicSchemaExpr.RelationalOperator.NotEqual
        )
        assertTrue(ops.forall { op =>
          val json = codec.encodeToString(op)
          codec.decode(json) == Right(op)
        })
      },
      test("ArithmeticOperator all variants") {
        val codec = Schema[DynamicSchemaExpr.ArithmeticOperator].derive(deriver)
        val ops   = Vector(
          DynamicSchemaExpr.ArithmeticOperator.Add,
          DynamicSchemaExpr.ArithmeticOperator.Subtract,
          DynamicSchemaExpr.ArithmeticOperator.Multiply
        )
        assertTrue(ops.forall { op =>
          val json = codec.encodeToString(op)
          codec.decode(json) == Right(op)
        })
      }
    )
  )
}
