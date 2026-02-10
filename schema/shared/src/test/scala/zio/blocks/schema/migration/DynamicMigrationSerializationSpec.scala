package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Serialization round-trip tests for the migration ADT. Demonstrates that
 * `DynamicMigration` and all its constituent types are fully serializable via
 * `Schema.toDynamicValue` / `Schema.fromDynamicValue`.
 */
object DynamicMigrationSerializationSpec extends ZIOSpecDefault {

  // --- helpers ---

  private def roundTrip[A](value: A)(implicit schema: Schema[A]): TestResult = {
    val dv       = schema.toDynamicValue(value)
    val restored = schema.fromDynamicValue(dv)
    assertTrue(restored == Right(value))
  }

  // --- sample values ---

  private val intLit    = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
  private val strLit    = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello")))
  private val boolLit   = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
  private val defaultDV = DynamicSchemaExpr.DefaultValue(DynamicValue.Primitive(PrimitiveValue.Int(0)))
  private val dynField  = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("x"))

  def spec = suite("DynamicMigrationSerializationSpec")(
    // ---- Bare literal implicit conversions ----
    suite("bare literal syntax")(
      test("Int literal converts to Literal") {
        val expr: DynamicSchemaExpr = 42
        assertTrue(expr == DynamicSchemaExpr.Literal(DynamicValue.int(42)))
      },
      test("Long literal converts to Literal") {
        val expr: DynamicSchemaExpr = 99L
        assertTrue(expr == DynamicSchemaExpr.Literal(DynamicValue.long(99L)))
      },
      test("Double literal converts to Literal") {
        val expr: DynamicSchemaExpr = 3.14
        assertTrue(expr == DynamicSchemaExpr.Literal(DynamicValue.double(3.14)))
      },
      test("Float literal converts to Literal") {
        val expr: DynamicSchemaExpr = 1.5f
        assertTrue(expr == DynamicSchemaExpr.Literal(DynamicValue.float(1.5f)))
      },
      test("Short literal converts to Literal") {
        val expr: DynamicSchemaExpr = (7: Short)
        assertTrue(expr == DynamicSchemaExpr.Literal(DynamicValue.short(7)))
      },
      test("Byte literal converts to Literal") {
        val expr: DynamicSchemaExpr = (3: Byte)
        assertTrue(expr == DynamicSchemaExpr.Literal(DynamicValue.byte(3)))
      },
      test("String literal converts to Literal") {
        val expr: DynamicSchemaExpr = "hello"
        assertTrue(expr == DynamicSchemaExpr.Literal(DynamicValue.string("hello")))
      },
      test("Boolean literal converts to Literal") {
        val expr: DynamicSchemaExpr = true
        assertTrue(expr == DynamicSchemaExpr.Literal(DynamicValue.boolean(true)))
      },
      test("Char literal converts to Literal") {
        val expr: DynamicSchemaExpr = 'x'
        assertTrue(expr == DynamicSchemaExpr.Literal(DynamicValue.char('x')))
      },
      test("BigInt converts to Literal") {
        val expr: DynamicSchemaExpr = BigInt(123)
        assertTrue(expr == DynamicSchemaExpr.Literal(DynamicValue.bigInt(BigInt(123))))
      },
      test("BigDecimal converts to Literal") {
        val expr: DynamicSchemaExpr = BigDecimal("9.99")
        assertTrue(expr == DynamicSchemaExpr.Literal(DynamicValue.bigDecimal(BigDecimal("9.99"))))
      },
      test("bare literal round-trips through serialization") {
        val expr: DynamicSchemaExpr = 42
        roundTrip(expr)
      }
    ),
    // ---- Operator enums ----
    suite("ArithmeticOperator round-trip")(
      test("Add")(roundTrip(DynamicSchemaExpr.ArithmeticOperator.Add: DynamicSchemaExpr.ArithmeticOperator)),
      test("Subtract")(roundTrip(DynamicSchemaExpr.ArithmeticOperator.Subtract: DynamicSchemaExpr.ArithmeticOperator)),
      test("Multiply")(roundTrip(DynamicSchemaExpr.ArithmeticOperator.Multiply: DynamicSchemaExpr.ArithmeticOperator)),
      test("Divide")(roundTrip(DynamicSchemaExpr.ArithmeticOperator.Divide: DynamicSchemaExpr.ArithmeticOperator))
    ),
    suite("RelationalOperator round-trip")(
      test("Equal")(roundTrip(DynamicSchemaExpr.RelationalOperator.Equal: DynamicSchemaExpr.RelationalOperator)),
      test("NotEqual")(roundTrip(DynamicSchemaExpr.RelationalOperator.NotEqual: DynamicSchemaExpr.RelationalOperator)),
      test("LessThan")(roundTrip(DynamicSchemaExpr.RelationalOperator.LessThan: DynamicSchemaExpr.RelationalOperator)),
      test("LessThanOrEqual")(
        roundTrip(DynamicSchemaExpr.RelationalOperator.LessThanOrEqual: DynamicSchemaExpr.RelationalOperator)
      ),
      test("GreaterThan")(
        roundTrip(DynamicSchemaExpr.RelationalOperator.GreaterThan: DynamicSchemaExpr.RelationalOperator)
      ),
      test("GreaterThanOrEqual")(
        roundTrip(DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual: DynamicSchemaExpr.RelationalOperator)
      )
    ),
    suite("LogicalOperator round-trip")(
      test("And")(roundTrip(DynamicSchemaExpr.LogicalOperator.And: DynamicSchemaExpr.LogicalOperator)),
      test("Or")(roundTrip(DynamicSchemaExpr.LogicalOperator.Or: DynamicSchemaExpr.LogicalOperator))
    ),
    // ---- NumericType ----
    suite("NumericType round-trip")(
      test("ByteType")(roundTrip(DynamicSchemaExpr.NumericType.ByteType: DynamicSchemaExpr.NumericType)),
      test("ShortType")(roundTrip(DynamicSchemaExpr.NumericType.ShortType: DynamicSchemaExpr.NumericType)),
      test("IntType")(roundTrip(DynamicSchemaExpr.NumericType.IntType: DynamicSchemaExpr.NumericType)),
      test("LongType")(roundTrip(DynamicSchemaExpr.NumericType.LongType: DynamicSchemaExpr.NumericType)),
      test("FloatType")(roundTrip(DynamicSchemaExpr.NumericType.FloatType: DynamicSchemaExpr.NumericType)),
      test("DoubleType")(roundTrip(DynamicSchemaExpr.NumericType.DoubleType: DynamicSchemaExpr.NumericType)),
      test("BigIntType")(roundTrip(DynamicSchemaExpr.NumericType.BigIntType: DynamicSchemaExpr.NumericType)),
      test("BigDecimalType")(roundTrip(DynamicSchemaExpr.NumericType.BigDecimalType: DynamicSchemaExpr.NumericType))
    ),
    // ---- PrimitiveConverter ----
    suite("PrimitiveConverter round-trip")(
      test("StringToInt")(roundTrip(PrimitiveConverter.StringToInt: PrimitiveConverter)),
      test("IntToString")(roundTrip(PrimitiveConverter.IntToString: PrimitiveConverter)),
      test("StringToLong")(roundTrip(PrimitiveConverter.StringToLong: PrimitiveConverter)),
      test("LongToString")(roundTrip(PrimitiveConverter.LongToString: PrimitiveConverter)),
      test("StringToDouble")(roundTrip(PrimitiveConverter.StringToDouble: PrimitiveConverter)),
      test("DoubleToString")(roundTrip(PrimitiveConverter.DoubleToString: PrimitiveConverter)),
      test("IntToLong")(roundTrip(PrimitiveConverter.IntToLong: PrimitiveConverter)),
      test("LongToInt")(roundTrip(PrimitiveConverter.LongToInt: PrimitiveConverter)),
      test("IntToDouble")(roundTrip(PrimitiveConverter.IntToDouble: PrimitiveConverter)),
      test("DoubleToInt")(roundTrip(PrimitiveConverter.DoubleToInt: PrimitiveConverter))
    ),
    // ---- DynamicSchemaExpr variants ----
    suite("DynamicSchemaExpr round-trip")(
      test("Literal with Int")(roundTrip(intLit: DynamicSchemaExpr)),
      test("Literal with String")(roundTrip(strLit: DynamicSchemaExpr)),
      test("DefaultValue")(roundTrip(defaultDV: DynamicSchemaExpr)),
      test("Dynamic (field optic)")(roundTrip(dynField: DynamicSchemaExpr)),
      test("Dynamic (nested optic)") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("a").field("b"))
        roundTrip(expr)
      },
      test("Arithmetic (Add, IntType)") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.Arithmetic(
          dynField,
          intLit,
          DynamicSchemaExpr.ArithmeticOperator.Add,
          DynamicSchemaExpr.NumericType.IntType
        )
        roundTrip(expr)
      },
      test("Arithmetic (Multiply, DoubleType)") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(2.5))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(4.0))),
          DynamicSchemaExpr.ArithmeticOperator.Multiply,
          DynamicSchemaExpr.NumericType.DoubleType
        )
        roundTrip(expr)
      },
      test("StringConcat") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("first")),
          DynamicSchemaExpr.StringConcat(
            " ",
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("last"))
          )
        )
        roundTrip(expr)
      },
      test("StringSplit") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.StringSplit(
          DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("name")),
          " "
        )
        roundTrip(expr)
      },
      test("StringUppercase") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.StringUppercase(dynField)
        roundTrip(expr)
      },
      test("StringLowercase") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.StringLowercase(dynField)
        roundTrip(expr)
      },
      test("StringLength") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.StringLength(dynField)
        roundTrip(expr)
      },
      test("StringRegexMatch") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.StringRegexMatch(
          "^\\d+$",
          dynField
        )
        roundTrip(expr)
      },
      test("Not") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.Not(boolLit)
        roundTrip(expr)
      },
      test("Relational (Equal)") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.Relational(
          intLit,
          intLit,
          DynamicSchemaExpr.RelationalOperator.Equal
        )
        roundTrip(expr)
      },
      test("Relational (LessThan)") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.Relational(
          DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("a")),
          DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("b")),
          DynamicSchemaExpr.RelationalOperator.LessThan
        )
        roundTrip(expr)
      },
      test("Logical (And)") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.Logical(
          boolLit,
          boolLit,
          DynamicSchemaExpr.LogicalOperator.And
        )
        roundTrip(expr)
      },
      test("Logical (Or)") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.Logical(
          boolLit,
          DynamicSchemaExpr.Not(boolLit),
          DynamicSchemaExpr.LogicalOperator.Or
        )
        roundTrip(expr)
      },
      test("Convert (StringToInt)") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.Convert(strLit, PrimitiveConverter.StringToInt)
        roundTrip(expr)
      },
      test("Convert (IntToDouble)") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.Convert(intLit, PrimitiveConverter.IntToDouble)
        roundTrip(expr)
      },
      test("deeply nested expression") {
        val expr: DynamicSchemaExpr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Arithmetic(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("a")),
            1,
            DynamicSchemaExpr.ArithmeticOperator.Add,
            DynamicSchemaExpr.NumericType.IntType
          ),
          DynamicSchemaExpr.Arithmetic(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("b")),
            2,
            DynamicSchemaExpr.ArithmeticOperator.Multiply,
            DynamicSchemaExpr.NumericType.IntType
          ),
          DynamicSchemaExpr.ArithmeticOperator.Subtract,
          DynamicSchemaExpr.NumericType.IntType
        )
        roundTrip(expr)
      }
    ),
    // ---- MigrationAction variants ----
    suite("MigrationAction round-trip")(
      test("AddField (old verbose notation)") {
        // Intentionally kept in old notation to verify it still works
        val action: MigrationAction = MigrationAction.AddField(
          at = DynamicOptic.root.field("age"),
          default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        roundTrip(action)
      },
      test("DropField") {
        val action: MigrationAction = MigrationAction.DropField(
          at = DynamicOptic.root.field("oldField"),
          defaultForReverse = ""
        )
        roundTrip(action)
      },
      test("Rename") {
        val action: MigrationAction = MigrationAction.Rename(
          at = DynamicOptic.root.field("firstName"),
          to = "name"
        )
        roundTrip(action)
      },
      test("TransformValue") {
        val action: MigrationAction = MigrationAction.TransformValue(
          at = DynamicOptic.root.field("age"),
          transform = DynamicSchemaExpr.Arithmetic(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root),
            1,
            DynamicSchemaExpr.ArithmeticOperator.Add,
            DynamicSchemaExpr.NumericType.IntType
          )
        )
        roundTrip(action)
      },
      test("ChangeType") {
        val action: MigrationAction = MigrationAction.ChangeType(
          at = DynamicOptic.root.field("age"),
          converter = PrimitiveConverter.StringToInt
        )
        roundTrip(action)
      },
      test("Mandate") {
        val action: MigrationAction = MigrationAction.Mandate(
          at = DynamicOptic.root.field("name"),
          default = "Unknown"
        )
        roundTrip(action)
      },
      test("Optionalize") {
        val action: MigrationAction = MigrationAction.Optionalize(
          at = DynamicOptic.root.field("name"),
          defaultForReverse = ""
        )
        roundTrip(action)
      },
      test("Join") {
        val action: MigrationAction = MigrationAction.Join(
          at = DynamicOptic.root.field("fullName"),
          sourcePaths = Vector(
            DynamicOptic.root.field("firstName"),
            DynamicOptic.root.field("lastName")
          ),
          combiner = DynamicSchemaExpr.StringConcat(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
            DynamicSchemaExpr.StringConcat(
              " ",
              DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
            )
          )
        )
        roundTrip(action)
      },
      test("Split") {
        val action: MigrationAction = MigrationAction.Split(
          at = DynamicOptic.root.field("fullName"),
          targetPaths = Vector(
            DynamicOptic.root.field("firstName"),
            DynamicOptic.root.field("lastName")
          ),
          splitter = DynamicSchemaExpr.StringSplit(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root),
            " "
          )
        )
        roundTrip(action)
      },
      test("TransformElements") {
        val action: MigrationAction = MigrationAction.TransformElements(
          at = DynamicOptic.root.field("items"),
          transform = DynamicSchemaExpr.Arithmetic(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root),
            10,
            DynamicSchemaExpr.ArithmeticOperator.Add,
            DynamicSchemaExpr.NumericType.IntType
          )
        )
        roundTrip(action)
      },
      test("TransformKeys") {
        val action: MigrationAction = MigrationAction.TransformKeys(
          at = DynamicOptic.root.field("data"),
          transform = DynamicSchemaExpr.StringUppercase(DynamicSchemaExpr.Dynamic(DynamicOptic.root))
        )
        roundTrip(action)
      },
      test("TransformValues") {
        val action: MigrationAction = MigrationAction.TransformValues(
          at = DynamicOptic.root.field("data"),
          transform = DynamicSchemaExpr.Arithmetic(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root),
            100,
            DynamicSchemaExpr.ArithmeticOperator.Add,
            DynamicSchemaExpr.NumericType.IntType
          )
        )
        roundTrip(action)
      },
      test("RenameCase") {
        val action: MigrationAction = MigrationAction.RenameCase(
          at = DynamicOptic.root,
          from = "PayPal",
          to = "PaypalPayment"
        )
        roundTrip(action)
      },
      test("TransformCase") {
        val action: MigrationAction = MigrationAction.TransformCase(
          at = DynamicOptic.root,
          caseName = "CreditCard",
          actions = Vector(
            MigrationAction.Rename(DynamicOptic.root.field("number"), "cardNumber")
          )
        )
        roundTrip(action)
      },
      test("Irreversible") {
        val action: MigrationAction = MigrationAction.Irreversible(
          at = DynamicOptic.root.field("x"),
          reason = "Cannot reverse this"
        )
        roundTrip(action)
      },
      test("nested path action") {
        val action: MigrationAction = MigrationAction.Rename(
          at = DynamicOptic.root.field("address").field("street"),
          to = "streetName"
        )
        roundTrip(action)
      }
    ),
    // ---- DynamicMigration ----
    suite("DynamicMigration round-trip")(
      test("identity (empty)") {
        roundTrip(DynamicMigration.identity)
      },
      test("single action") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root.field("a"), "b")
          )
        )
        roundTrip(migration)
      },
      test("multiple actions") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root.field("country"),
              "USA"
            ),
            MigrationAction.Rename(DynamicOptic.root.field("firstName"), "givenName"),
            MigrationAction.TransformValue(
              DynamicOptic.root.field("age"),
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                1,
                DynamicSchemaExpr.ArithmeticOperator.Add,
                DynamicSchemaExpr.NumericType.IntType
              )
            ),
            MigrationAction.DropField(
              DynamicOptic.root.field("lastName"),
              ""
            )
          )
        )
        roundTrip(migration)
      },
      test("migration with Join and Split") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Join(
              at = DynamicOptic.root.field("fullName"),
              sourcePaths = Vector(
                DynamicOptic.root.field("firstName"),
                DynamicOptic.root.field("lastName")
              ),
              combiner = DynamicSchemaExpr.StringConcat(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
                DynamicSchemaExpr.StringConcat(
                  " ",
                  DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
                )
              )
            ),
            MigrationAction.ChangeType(
              DynamicOptic.root.field("age"),
              PrimitiveConverter.StringToInt
            )
          )
        )
        roundTrip(migration)
      },
      test("migration with enum operations") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.RenameCase(DynamicOptic.root, "PayPal", "PaypalPayment"),
            MigrationAction.TransformCase(
              DynamicOptic.root,
              "CreditCard",
              Vector(
                MigrationAction.Rename(DynamicOptic.root.field("number"), "cardNumber"),
                MigrationAction.AddField(
                  DynamicOptic.root.field("cvv"),
                  ""
                )
              )
            )
          )
        )
        roundTrip(migration)
      },
      test("migration with collection and map operations") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.TransformElements(
              DynamicOptic.root.field("scores"),
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                10,
                DynamicSchemaExpr.ArithmeticOperator.Multiply,
                DynamicSchemaExpr.NumericType.IntType
              )
            ),
            MigrationAction.TransformKeys(
              DynamicOptic.root.field("labels"),
              DynamicSchemaExpr.StringUppercase(DynamicSchemaExpr.Dynamic(DynamicOptic.root))
            ),
            MigrationAction.TransformValues(
              DynamicOptic.root.field("labels"),
              DynamicSchemaExpr.StringLowercase(DynamicSchemaExpr.Dynamic(DynamicOptic.root))
            )
          )
        )
        roundTrip(migration)
      },
      test("reverse of migration round-trips") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root.field("age"),
              0
            ),
            MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName")
          )
        )
        val reversed = migration.reverse
        roundTrip(reversed)
      }
    )
  )
}
