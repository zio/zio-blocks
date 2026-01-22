package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object EnumOperationsSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("EnumOperationsSpec")(
    suite("RenameCase")(
      test("rename a variant case name at root level") {
        // Create a variant representing PayPal case
        val paypalVariant = DynamicValue.Variant(
          "PayPal",
          DynamicValue.Record(Vector("email" -> DynamicValue.Primitive(PrimitiveValue.String("test@example.com"))))
        )

        val action = MigrationAction.RenameCase(
          DynamicOptic.root,
          from = "PayPal",
          to = "PaypalPayment"
        )

        val result = action.execute(paypalVariant)

        val expected = DynamicValue.Variant(
          "PaypalPayment",
          DynamicValue.Record(Vector("email" -> DynamicValue.Primitive(PrimitiveValue.String("test@example.com"))))
        )

        assertTrue(result == Right(expected))
      },
      test("rename case in nested field") {
        // Create a record with a variant field
        val record = DynamicValue.Record(
          Vector(
            "name"          -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "paymentMethod" -> DynamicValue.Variant(
              "CreditCard",
              DynamicValue.Record(
                Vector(
                  "number" -> DynamicValue.Primitive(PrimitiveValue.String("1234-5678-9012-3456")),
                  "cvv"    -> DynamicValue.Primitive(PrimitiveValue.Int(123))
                )
              )
            )
          )
        )

        val action = MigrationAction.RenameCase(
          DynamicOptic.root.field("paymentMethod"),
          from = "CreditCard",
          to = "Card"
        )

        val result = action.execute(record)

        val expected = DynamicValue.Record(
          Vector(
            "name"          -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "paymentMethod" -> DynamicValue.Variant(
              "Card",
              DynamicValue.Record(
                Vector(
                  "number" -> DynamicValue.Primitive(PrimitiveValue.String("1234-5678-9012-3456")),
                  "cvv"    -> DynamicValue.Primitive(PrimitiveValue.Int(123))
                )
              )
            )
          )
        )

        assertTrue(result == Right(expected))
      },
      test("rename case leaves non-matching cases unchanged") {
        val paypalVariant = DynamicValue.Variant(
          "PayPal",
          DynamicValue.Record(Vector("email" -> DynamicValue.Primitive(PrimitiveValue.String("test@example.com"))))
        )

        val action = MigrationAction.RenameCase(
          DynamicOptic.root,
          from = "CreditCard",
          to = "Card"
        )

        val result = action.execute(paypalVariant)

        // Should remain unchanged since case name doesn't match
        assertTrue(result == Right(paypalVariant))
      },
      test("reverse renames back to original") {
        val variant = DynamicValue.Variant(
          "PayPal",
          DynamicValue.Record(Vector("email" -> DynamicValue.Primitive(PrimitiveValue.String("test@example.com"))))
        )

        val action = MigrationAction.RenameCase(
          DynamicOptic.root,
          from = "PayPal",
          to = "PaypalPayment"
        )

        val forward  = action.execute(variant)
        val backward = forward.flatMap(action.reverse.execute)

        assertTrue(backward == Right(variant))
      },
      test("error when root value is not a variant") {
        val primitive = DynamicValue.Primitive(PrimitiveValue.String("not a variant"))

        val action = MigrationAction.RenameCase(
          DynamicOptic.root,
          from = "PayPal",
          to = "PaypalPayment"
        )

        val result = action.execute(primitive)

        assertTrue(result.isLeft)
      },
      test("error when field value is not a variant") {
        val record = DynamicValue.Record(
          Vector("paymentMethod" -> DynamicValue.Primitive(PrimitiveValue.String("not a variant")))
        )

        val action = MigrationAction.RenameCase(
          DynamicOptic.root.field("paymentMethod"),
          from = "PayPal",
          to = "PaypalPayment"
        )

        val result = action.execute(record)

        assertTrue(result.isLeft)
      },
      test("error when field does not exist") {
        val record = DynamicValue.Record(
          Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John")))
        )

        val action = MigrationAction.RenameCase(
          DynamicOptic.root.field("paymentMethod"),
          from = "PayPal",
          to = "PaypalPayment"
        )

        val result = action.execute(record)

        assertTrue(result.isLeft)
      }
    ),
    suite("TransformCase")(
      test("transform fields within a specific case at root level") {
        // Create a CreditCard variant
        val creditCardVariant = DynamicValue.Variant(
          "CreditCard",
          DynamicValue.Record(
            Vector(
              "number" -> DynamicValue.Primitive(PrimitiveValue.String("1234")),
              "cvv"    -> DynamicValue.Primitive(PrimitiveValue.Int(123))
            )
          )
        )

        // Add a field to the CreditCard case
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          caseName = "CreditCard",
          actions = Vector(
            MigrationAction.AddField(
              DynamicOptic.root.field("expiryDate"),
              SchemaExpr.Literal[DynamicValue, String]("2030-12", Schema.string)
            )
          )
        )

        val result = action.execute(creditCardVariant)

        val expected = DynamicValue.Variant(
          "CreditCard",
          DynamicValue.Record(
            Vector(
              "number"     -> DynamicValue.Primitive(PrimitiveValue.String("1234")),
              "cvv"        -> DynamicValue.Primitive(PrimitiveValue.Int(123)),
              "expiryDate" -> DynamicValue.Primitive(PrimitiveValue.String("2030-12"))
            )
          )
        )

        assertTrue(result == Right(expected))
      },
      test("transform case in nested field") {
        val record = DynamicValue.Record(
          Vector(
            "name"          -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "paymentMethod" -> DynamicValue.Variant(
              "CreditCard",
              DynamicValue.Record(
                Vector(
                  "number" -> DynamicValue.Primitive(PrimitiveValue.String("1234")),
                  "cvv"    -> DynamicValue.Primitive(PrimitiveValue.Int(123))
                )
              )
            )
          )
        )

        val action = MigrationAction.TransformCase(
          DynamicOptic.root.field("paymentMethod"),
          caseName = "CreditCard",
          actions = Vector(
            MigrationAction.Rename(
              DynamicOptic.root.field("number"),
              to = "cardNumber"
            )
          )
        )

        val result = action.execute(record)

        val expected = DynamicValue.Record(
          Vector(
            "name"          -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "paymentMethod" -> DynamicValue.Variant(
              "CreditCard",
              DynamicValue.Record(
                Vector(
                  "cardNumber" -> DynamicValue.Primitive(PrimitiveValue.String("1234")),
                  "cvv"        -> DynamicValue.Primitive(PrimitiveValue.Int(123))
                )
              )
            )
          )
        )

        assertTrue(result == Right(expected))
      },
      test("transform case leaves non-matching cases unchanged") {
        val paypalVariant = DynamicValue.Variant(
          "PayPal",
          DynamicValue.Record(Vector("email" -> DynamicValue.Primitive(PrimitiveValue.String("test@example.com"))))
        )

        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          caseName = "CreditCard",
          actions = Vector(
            MigrationAction.AddField(
              DynamicOptic.root.field("expiryDate"),
              SchemaExpr.Literal[DynamicValue, String]("2030-12", Schema.string)
            )
          )
        )

        val result = action.execute(paypalVariant)

        // Should remain unchanged since case name doesn't match
        assertTrue(result == Right(paypalVariant))
      },
      test("transform case with multiple nested actions") {
        val creditCardVariant = DynamicValue.Variant(
          "CreditCard",
          DynamicValue.Record(
            Vector(
              "number" -> DynamicValue.Primitive(PrimitiveValue.String("1234")),
              "cvv"    -> DynamicValue.Primitive(PrimitiveValue.Int(123))
            )
          )
        )

        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          caseName = "CreditCard",
          actions = Vector(
            MigrationAction.Rename(DynamicOptic.root.field("number"), to = "cardNumber"),
            MigrationAction.AddField(
              DynamicOptic.root.field("expiryDate"),
              SchemaExpr.Literal[DynamicValue, String]("2030-12", Schema.string)
            ),
            MigrationAction.DropField(
              DynamicOptic.root.field("cvv"),
              SchemaExpr.Literal[DynamicValue, Int](0, Schema.int)
            )
          )
        )

        val result = action.execute(creditCardVariant)

        val expected = DynamicValue.Variant(
          "CreditCard",
          DynamicValue.Record(
            Vector(
              "cardNumber" -> DynamicValue.Primitive(PrimitiveValue.String("1234")),
              "expiryDate" -> DynamicValue.Primitive(PrimitiveValue.String("2030-12"))
            )
          )
        )

        assertTrue(result == Right(expected))
      },
      test("reverse applies reverse of nested actions in reverse order") {
        val creditCardVariant = DynamicValue.Variant(
          "CreditCard",
          DynamicValue.Record(
            Vector(
              "number" -> DynamicValue.Primitive(PrimitiveValue.String("1234")),
              "cvv"    -> DynamicValue.Primitive(PrimitiveValue.Int(123))
            )
          )
        )

        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          caseName = "CreditCard",
          actions = Vector(
            MigrationAction.AddField(
              DynamicOptic.root.field("expiryDate"),
              SchemaExpr.Literal[DynamicValue, String]("2030-12", Schema.string)
            )
          )
        )

        val forward  = action.execute(creditCardVariant)
        val backward = forward.flatMap(action.reverse.execute)

        assertTrue(backward == Right(creditCardVariant))
      },
      test("error when root value is not a variant") {
        val primitive = DynamicValue.Primitive(PrimitiveValue.String("not a variant"))

        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          caseName = "CreditCard",
          actions = Vector.empty
        )

        val result = action.execute(primitive)

        assertTrue(result.isLeft)
      },
      test("error when field value is not a variant") {
        val record = DynamicValue.Record(
          Vector("paymentMethod" -> DynamicValue.Primitive(PrimitiveValue.String("not a variant")))
        )

        val action = MigrationAction.TransformCase(
          DynamicOptic.root.field("paymentMethod"),
          caseName = "CreditCard",
          actions = Vector.empty
        )

        val result = action.execute(record)

        assertTrue(result.isLeft)
      },
      test("error when nested action fails") {
        val creditCardVariant = DynamicValue.Variant(
          "CreditCard",
          DynamicValue.Record(
            Vector(
              "number" -> DynamicValue.Primitive(PrimitiveValue.String("1234"))
            )
          )
        )

        // Try to drop a field that doesn't exist
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          caseName = "CreditCard",
          actions = Vector(
            MigrationAction.DropField(
              DynamicOptic.root.field("nonExistentField"),
              SchemaExpr.Literal[DynamicValue, Int](0, Schema.int)
            )
          )
        )

        val result = action.execute(creditCardVariant)

        assertTrue(result.isLeft)
      }
    )
  )
}
