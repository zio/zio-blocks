package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests .build() method validation for:
 *   - Top-level record fields
 *   - Nested record fields
 *   - Variant cases
 *   - All migration actions
 */
object MigrationValidationSpec extends ZIOSpecDefault {

  // Simple record types for top-level testing
  case class PersonV1(firstName: String, lastName: String, age: Int)
  case class PersonV2(fullName: String, age: Int, country: String)

  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived[PersonV1]
  }

  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived[PersonV2]
  }

  // Nested record types
  case class Address(street: String, city: String, zipCode: String)
  case class PersonWithAddressV1(name: String, address: Address)
  case class PersonWithAddressV2(fullName: String, address: Address)

  object Address {
    implicit val schema: Schema[Address] = Schema.derived[Address]
  }

  object PersonWithAddressV1 {
    implicit val schema: Schema[PersonWithAddressV1] = Schema.derived[PersonWithAddressV1]
  }

  object PersonWithAddressV2 {
    implicit val schema: Schema[PersonWithAddressV2] = Schema.derived[PersonWithAddressV2]
  }

  // Variant types
  sealed trait PaymentV1
  case class CreditCardV1(number: String, cvv: String) extends PaymentV1
  case class PayPalV1(email: String)                   extends PaymentV1

  sealed trait PaymentV2
  case class CreditCardV2(number: String, cvv: String, expiryDate: String) extends PaymentV2
  case class PayPalPaymentV2(email: String)                                extends PaymentV2

  object PaymentV1 {
    implicit val schema: Schema[PaymentV1] = Schema.derived[PaymentV1]
  }

  object CreditCardV1 {
    implicit val schema: Schema[CreditCardV1] = Schema.derived[CreditCardV1]
  }

  object PayPalV1 {
    implicit val schema: Schema[PayPalV1] = Schema.derived[PayPalV1]
  }

  object PaymentV2 {
    implicit val schema: Schema[PaymentV2] = Schema.derived[PaymentV2]
  }

  object CreditCardV2 {
    implicit val schema: Schema[CreditCardV2] = Schema.derived[CreditCardV2]
  }

  object PayPalPaymentV2 {
    implicit val schema: Schema[PayPalPaymentV2] = Schema.derived[PayPalPaymentV2]
  }

  def spec = suite("MigrationValidationSpec")(
    suite("Top-level record validation")(
      test("complete migration passes validation") {
        val builder = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .dropField(DynamicOptic.root.field("firstName"), SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
          .dropField(DynamicOptic.root.field("lastName"), SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
          .addField(
            DynamicOptic.root.field("fullName"),
            SchemaExpr.Literal[DynamicValue, String]("John Doe", Schema.string)
          )
          .addField(DynamicOptic.root.field("country"), SchemaExpr.Literal[DynamicValue, String]("USA", Schema.string))

        val result = builder.build

        assertTrue(result.isRight)
      },
      test("missing source field handling fails validation") {
        // Only handle firstName and age, but not lastName
        val builder = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .dropField(DynamicOptic.root.field("firstName"), SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
          .addField(DynamicOptic.root.field("fullName"), SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
          .addField(DynamicOptic.root.field("country"), SchemaExpr.Literal[DynamicValue, String]("USA", Schema.string))

        val result = builder.build

        assertTrue(result.isLeft) &&
        assertTrue(result.left.exists {
          case MigrationError.ValidationError(_, unhandled, _) =>
            unhandled.contains("lastName")
          case _ => false
        })
      },
      test("missing target field provision fails validation") {
        // Handle all source fields but don't provide country
        val builder = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .dropField(DynamicOptic.root.field("firstName"), SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
          .dropField(DynamicOptic.root.field("lastName"), SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
          .addField(
            DynamicOptic.root.field("fullName"),
            SchemaExpr.Literal[DynamicValue, String]("John Doe", Schema.string)
          )

        val result = builder.build

        assertTrue(result.isLeft) &&
        assertTrue(result.left.exists {
          case MigrationError.ValidationError(_, _, unprovided) =>
            unprovided.contains("country")
          case _ => false
        })
      },
      test("renameField handles source and provides target") {
        // Rename firstName -> fullName, handle other fields
        val builder = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .renameField(DynamicOptic.root.field("firstName"), "fullName")
          .dropField(DynamicOptic.root.field("lastName"), SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
          .addField(DynamicOptic.root.field("country"), SchemaExpr.Literal[DynamicValue, String]("USA", Schema.string))

        val result = builder.build

        assertTrue(result.isRight)
      },
      test("joinFields handles multiple sources") {
        // Join firstName + lastName -> fullName
        val builder = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .joinFields(
            DynamicOptic.root.field("fullName"),
            Vector(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName")),
            SchemaExpr.Literal[DynamicValue, String]("John Doe", Schema.string)
          )
          .addField(DynamicOptic.root.field("country"), SchemaExpr.Literal[DynamicValue, String]("USA", Schema.string))

        val result = builder.build

        assertTrue(result.isRight)
      },
      test("splitField provides multiple targets") {
        // Split fullName -> firstName + lastName
        val builder = MigrationBuilder
          .newBuilder[PersonV2, PersonV1]
          .splitField(
            DynamicOptic.root.field("fullName"),
            Vector(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName")),
            SchemaExpr.StringSplit(
              SchemaExpr.Literal[DynamicValue, String]("John Doe", Schema.string),
              " "
            )
          )
          .dropField(DynamicOptic.root.field("country"), SchemaExpr.Literal[DynamicValue, String]("", Schema.string))

        val result = builder.build

        assertTrue(result.isRight)
      },
      test("transformField handles source and provides target") {
        // Transform "age" which exists in both schemas
        val builder = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .transformField(
            DynamicOptic.root.field("age"),
            SchemaExpr.Arithmetic[DynamicValue, Int](
              SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root.field("age")),
              SchemaExpr.Literal[DynamicValue, Int](1, Schema.int),
              SchemaExpr.ArithmeticOperator.Add,
              IsNumeric.IsInt
            )
          )
          .renameField(DynamicOptic.root.field("firstName"), "fullName")
          .dropField(DynamicOptic.root.field("lastName"), SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
          .addField(DynamicOptic.root.field("country"), SchemaExpr.Literal[DynamicValue, String]("USA", Schema.string))

        val result = builder.build

        assertTrue(result.isRight)
      },
      test("buildPartial bypasses validation") {
        // Incomplete migration - missing both source and target handling
        val builder = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .dropField(DynamicOptic.root.field("firstName"), SchemaExpr.Literal[DynamicValue, String]("", Schema.string))

        val migration = builder.buildPartial

        // buildPartial should always return Migration directly (no Either wrapper)
        assertTrue(migration.dynamicMigration.actions.length == 1)
      }
    ),
    suite("Nested field validation")(
      test("nested field migration passes validation") {
        // Rename name -> fullName at top level
        val builder = MigrationBuilder
          .newBuilder[PersonWithAddressV1, PersonWithAddressV2]
          .renameField(DynamicOptic.root.field("name"), "fullName")

        val result = builder.build

        assertTrue(result.isRight)
      },
      test("missing nested field handling fails validation") {
        // Forget to handle the 'name' field
        val builder = MigrationBuilder
          .newBuilder[PersonWithAddressV1, PersonWithAddressV2]
          .addField(DynamicOptic.root.field("fullName"), SchemaExpr.Literal[DynamicValue, String]("", Schema.string))

        val result = builder.build

        assertTrue(result.isLeft) &&
        assertTrue(result.left.exists {
          case MigrationError.ValidationError(_, unhandled, _) =>
            unhandled.contains("name")
          case _ => false
        })
      }
    ),
    suite("Variant validation")(
      test("variant case rename with nested fields passes validation") {
        // RenameCase now properly handles the case name and all nested fields
        // within the case, marking them as handled in the source schema and
        // provided in the target schema.
        val builder = MigrationBuilder
          .newBuilder[PaymentV1, PaymentV2]
          .renameCase(DynamicOptic.root, "CreditCardV1", "CreditCardV2")
          .renameCase(DynamicOptic.root, "PayPalV1", "PayPalPaymentV2")
          .transformCase(
            DynamicOptic.root,
            "CreditCardV2",
            Vector(
              MigrationAction.AddField(
                DynamicOptic.root.field("expiryDate"),
                SchemaExpr.Literal[DynamicValue, String]("01/25", Schema.string)
              )
            )
          )

        val result = builder.build

        // With the fix, this should now pass validation because:
        // - RenameCase handles all nested fields of source cases
        // - RenameCase provides all nested fields of target cases
        // - TransformCase adds the new expiryDate field
        assertTrue(result.isRight)
      },
      test("variant case field addition fails validation - incomplete migration") {
        // TransformCase with nested AddField is not sufficient for a complete migration.
        // The validation correctly identifies that:
        // 1. Other variant cases (PayPalV1) are not handled
        // 2. The CreditCardV1 case name change is not handled
        //
        // A complete migration would need to:
        // - Handle the CreditCardV1 -> CreditCardV2 transformation (rename + add field)
        // - Handle the PayPalV1 -> PayPalPaymentV2 transformation (rename)
        val builder = MigrationBuilder
          .newBuilder[PaymentV1, PaymentV2]
          .transformCase(
            DynamicOptic.root,
            "CreditCardV1",
            Vector(
              MigrationAction.AddField(
                DynamicOptic.root.field("expiryDate"),
                SchemaExpr.Literal[DynamicValue, String]("01/25", Schema.string)
              )
            )
          )

        val result = builder.build

        // Fails validation because:
        // - PayPalV1 and its fields are unhandled
        // - PayPalPaymentV2 and its fields are unprovided
        // - CreditCardV2 case name is unprovided (TransformCase doesn't rename)
        assertTrue(result.isLeft) &&
        assertTrue(result.left.exists {
          case MigrationError.ValidationError(_, unhandled, unprovided) =>
            // Should detect PayPal case and fields as unhandled
            unhandled.contains("PayPalV1") &&
            unhandled.contains("PayPalV1.email") &&
            unprovided.contains("PayPalPaymentV2") &&
            unprovided.contains("PayPalPaymentV2.email") &&
            unprovided.contains("CreditCardV2") &&
            // CreditCardV1 case should be handled by TransformCase
            !unhandled.contains("CreditCardV1")
          case _ => false
        })
      },
      test("complete variant migration with proper handling") {
        // This test shows a COMPLETE variant migration that properly handles
        // all variant cases and their nested fields.
        //
        // We need to:
        // 1. Rename both variant cases
        // 2. Transform the CreditCardV2 case to add the expiryDate field
        val builder = MigrationBuilder
          .newBuilder[PaymentV1, PaymentV2]
          .renameCase(DynamicOptic.root, "CreditCardV1", "CreditCardV2")
          .renameCase(DynamicOptic.root, "PayPalV1", "PayPalPaymentV2")
          .transformCase(
            DynamicOptic.root,
            "CreditCardV2",
            Vector(
              MigrationAction.AddField(
                DynamicOptic.root.field("expiryDate"),
                SchemaExpr.Literal[DynamicValue, String]("01/25", Schema.string)
              )
            )
          )

        val result = builder.build

        // With the fix, this complete migration now passes validation because:
        // - RenameCase properly tracks all nested fields within variant cases
        // - TransformCase correctly analyzes nested actions recursively
        // - All source fields are handled and all target fields are provided
        assertTrue(result.isRight)
      }
    ),
    suite("Complex variant validation edge cases")(
      test("nested variant in record passes validation") {
        // Test that variant validation works when the variant is nested inside a record
        case class OrderV1(id: String, payment: PaymentV1)
        case class OrderV2(id: String, payment: PaymentV2)

        object OrderV1 {
          implicit val schema: Schema[OrderV1] = Schema.derived[OrderV1]
        }

        object OrderV2 {
          implicit val schema: Schema[OrderV2] = Schema.derived[OrderV2]
        }

        val builder = MigrationBuilder
          .newBuilder[OrderV1, OrderV2]
          .renameCase(DynamicOptic.root.field("payment"), "CreditCardV1", "CreditCardV2")
          .renameCase(DynamicOptic.root.field("payment"), "PayPalV1", "PayPalPaymentV2")
          .transformCase(
            DynamicOptic.root.field("payment"),
            "CreditCardV2",
            Vector(
              MigrationAction.AddField(
                DynamicOptic.root.field("expiryDate"),
                SchemaExpr.Literal[DynamicValue, String]("01/25", Schema.string)
              )
            )
          )

        val result = builder.build

        assertTrue(result.isRight)
      },
      test("TransformCase with nested RenameField validates correctly") {
        // Test that TransformCase properly tracks nested rename actions within a variant case
        sealed trait PaymentWithBankV1
        case class CreditCardWithBankV1(number: String, cvv: String)                       extends PaymentWithBankV1
        case class BankTransferWithDetailsV1(accountNumber: String, routingNumber: String) extends PaymentWithBankV1

        sealed trait PaymentWithBankV2
        case class CreditCardWithBankV2(number: String, cvv: String)                    extends PaymentWithBankV2
        case class BankTransferWithDetailsV2(accountNum: String, routingNumber: String) extends PaymentWithBankV2

        object PaymentWithBankV1 {
          implicit val schema: Schema[PaymentWithBankV1] = Schema.derived[PaymentWithBankV1]
        }

        object PaymentWithBankV2 {
          implicit val schema: Schema[PaymentWithBankV2] = Schema.derived[PaymentWithBankV2]
        }

        object CreditCardWithBankV1 {
          implicit val schema: Schema[CreditCardWithBankV1] = Schema.derived[CreditCardWithBankV1]
        }

        object CreditCardWithBankV2 {
          implicit val schema: Schema[CreditCardWithBankV2] = Schema.derived[CreditCardWithBankV2]
        }

        object BankTransferWithDetailsV1 {
          implicit val schema: Schema[BankTransferWithDetailsV1] = Schema.derived[BankTransferWithDetailsV1]
        }

        object BankTransferWithDetailsV2 {
          implicit val schema: Schema[BankTransferWithDetailsV2] = Schema.derived[BankTransferWithDetailsV2]
        }

        val builder = MigrationBuilder
          .newBuilder[PaymentWithBankV1, PaymentWithBankV2]
          .renameCase(DynamicOptic.root, "CreditCardWithBankV1", "CreditCardWithBankV2")
          .transformCase(
            DynamicOptic.root,
            "BankTransferWithDetailsV2",
            Vector(
              MigrationAction.Rename(
                DynamicOptic.root.field("accountNumber"),
                "accountNum"
              )
            )
          )
          .renameCase(DynamicOptic.root, "BankTransferWithDetailsV1", "BankTransferWithDetailsV2")

        val result = builder.build

        assertTrue(result.isRight)
      },
      test("variant case with nested record containing multiple fields") {
        // Test complex variant case with deeply nested structure
        case class DetailedAddress(street: String, city: String, state: String, zipCode: String)

        sealed trait PaymentWithShippingV1
        case class CardV1(number: String)                                extends PaymentWithShippingV1
        case class ShippingV1(carrier: String, address: DetailedAddress) extends PaymentWithShippingV1

        sealed trait PaymentWithShippingV2
        case class CardV2(number: String)                                 extends PaymentWithShippingV2
        case class ShippingV2(carrier: String, location: DetailedAddress) extends PaymentWithShippingV2

        object DetailedAddress {
          implicit val schema: Schema[DetailedAddress] = Schema.derived[DetailedAddress]
        }

        object PaymentWithShippingV1 {
          implicit val schema: Schema[PaymentWithShippingV1] = Schema.derived[PaymentWithShippingV1]
        }

        object PaymentWithShippingV2 {
          implicit val schema: Schema[PaymentWithShippingV2] = Schema.derived[PaymentWithShippingV2]
        }

        object CardV1 {
          implicit val schema: Schema[CardV1] = Schema.derived[CardV1]
        }

        object CardV2 {
          implicit val schema: Schema[CardV2] = Schema.derived[CardV2]
        }

        object ShippingV1 {
          implicit val schema: Schema[ShippingV1] = Schema.derived[ShippingV1]
        }

        object ShippingV2 {
          implicit val schema: Schema[ShippingV2] = Schema.derived[ShippingV2]
        }

        val builder = MigrationBuilder
          .newBuilder[PaymentWithShippingV1, PaymentWithShippingV2]
          .renameCase(DynamicOptic.root, "CardV1", "CardV2")
          .renameCase(DynamicOptic.root, "ShippingV1", "ShippingV2")
          .transformCase(
            DynamicOptic.root,
            "ShippingV2",
            Vector(
              MigrationAction.Rename(
                DynamicOptic.root.field("address"),
                "location"
              )
            )
          )

        val result = builder.build

        assertTrue(result.isRight)
      },
      test("TransformCase with multiple nested actions validates correctly") {
        // Test that TransformCase properly handles multiple nested actions (AddField + DropField)
        sealed trait EnhancedPaymentV1
        case class EnhancedCardV1(number: String, cvv: String, notes: String) extends EnhancedPaymentV1
        case class SimpleCashV1(amount: Int)                                  extends EnhancedPaymentV1

        sealed trait EnhancedPaymentV2
        case class EnhancedCardV2(number: String, cvv: String, expiryDate: String) extends EnhancedPaymentV2
        case class SimpleCashV2(amount: Int)                                       extends EnhancedPaymentV2

        object EnhancedPaymentV1 {
          implicit val schema: Schema[EnhancedPaymentV1] = Schema.derived[EnhancedPaymentV1]
        }

        object EnhancedPaymentV2 {
          implicit val schema: Schema[EnhancedPaymentV2] = Schema.derived[EnhancedPaymentV2]
        }

        object EnhancedCardV1 {
          implicit val schema: Schema[EnhancedCardV1] = Schema.derived[EnhancedCardV1]
        }

        object EnhancedCardV2 {
          implicit val schema: Schema[EnhancedCardV2] = Schema.derived[EnhancedCardV2]
        }

        object SimpleCashV1 {
          implicit val schema: Schema[SimpleCashV1] = Schema.derived[SimpleCashV1]
        }

        object SimpleCashV2 {
          implicit val schema: Schema[SimpleCashV2] = Schema.derived[SimpleCashV2]
        }

        val builder = MigrationBuilder
          .newBuilder[EnhancedPaymentV1, EnhancedPaymentV2]
          .renameCase(DynamicOptic.root, "EnhancedCardV1", "EnhancedCardV2")
          .renameCase(DynamicOptic.root, "SimpleCashV1", "SimpleCashV2")
          .transformCase(
            DynamicOptic.root,
            "EnhancedCardV2",
            Vector(
              MigrationAction.DropField(
                DynamicOptic.root.field("notes"),
                SchemaExpr.Literal[DynamicValue, String]("", Schema.string)
              ),
              MigrationAction.AddField(
                DynamicOptic.root.field("expiryDate"),
                SchemaExpr.Literal[DynamicValue, String]("01/25", Schema.string)
              )
            )
          )

        val result = builder.build

        assertTrue(result.isRight)
      },
      test("deeply nested variant in record structure") {
        // Test: Record → Record → Variant with nested fields
        case class CustomerV1(name: String, primaryPayment: PaymentV1)
        case class AccountV1(customer: CustomerV1, accountId: String)

        case class CustomerV2(name: String, primaryPayment: PaymentV2)
        case class AccountV2(customer: CustomerV2, accountId: String)

        object CustomerV1 {
          implicit val schema: Schema[CustomerV1] = Schema.derived[CustomerV1]
        }

        object AccountV1 {
          implicit val schema: Schema[AccountV1] = Schema.derived[AccountV1]
        }

        object CustomerV2 {
          implicit val schema: Schema[CustomerV2] = Schema.derived[CustomerV2]
        }

        object AccountV2 {
          implicit val schema: Schema[AccountV2] = Schema.derived[AccountV2]
        }

        val builder = MigrationBuilder
          .newBuilder[AccountV1, AccountV2]
          .renameCase(
            DynamicOptic.root.field("customer").field("primaryPayment"),
            "CreditCardV1",
            "CreditCardV2"
          )
          .renameCase(
            DynamicOptic.root.field("customer").field("primaryPayment"),
            "PayPalV1",
            "PayPalPaymentV2"
          )
          .transformCase(
            DynamicOptic.root.field("customer").field("primaryPayment"),
            "CreditCardV2",
            Vector(
              MigrationAction.AddField(
                DynamicOptic.root.field("expiryDate"),
                SchemaExpr.Literal[DynamicValue, String]("01/25", Schema.string)
              )
            )
          )

        val result = builder.build

        assertTrue(result.isRight)
      },
      test("incomplete nested variant migration fails with proper error") {
        // Verify that incomplete migrations in nested variants are properly detected
        case class OrderWithPaymentV1(orderId: String, payment: PaymentV1)
        case class OrderWithPaymentV2(orderId: String, payment: PaymentV2)

        object OrderWithPaymentV1 {
          implicit val schema: Schema[OrderWithPaymentV1] = Schema.derived[OrderWithPaymentV1]
        }

        object OrderWithPaymentV2 {
          implicit val schema: Schema[OrderWithPaymentV2] = Schema.derived[OrderWithPaymentV2]
        }

        // Only handle CreditCard case, not PayPal
        val builder = MigrationBuilder
          .newBuilder[OrderWithPaymentV1, OrderWithPaymentV2]
          .renameCase(DynamicOptic.root.field("payment"), "CreditCardV1", "CreditCardV2")
          .transformCase(
            DynamicOptic.root.field("payment"),
            "CreditCardV2",
            Vector(
              MigrationAction.AddField(
                DynamicOptic.root.field("expiryDate"),
                SchemaExpr.Literal[DynamicValue, String]("01/25", Schema.string)
              )
            )
          )

        val result = builder.build

        assertTrue(result.isLeft) &&
        assertTrue(result.left.exists {
          case MigrationError.ValidationError(_, unhandled, unprovided) =>
            // Should detect unhandled PayPal case and its nested fields
            unhandled.contains("payment.PayPalV1") &&
            unhandled.contains("payment.PayPalV1.email") &&
            unprovided.contains("payment.PayPalPaymentV2") &&
            unprovided.contains("payment.PayPalPaymentV2.email")
          case _ => false
        })
      },
      test("variant with simple case objects passes validation") {
        // Test variant cases that are case objects (no nested fields)
        sealed trait StatusV1
        case object PendingV1  extends StatusV1
        case object ApprovedV1 extends StatusV1

        sealed trait StatusV2
        case object PendingV2  extends StatusV2
        case object AcceptedV2 extends StatusV2

        object StatusV1 {
          implicit val schema: Schema[StatusV1] = Schema.derived[StatusV1]
        }

        object StatusV2 {
          implicit val schema: Schema[StatusV2] = Schema.derived[StatusV2]
        }

        val builder = MigrationBuilder
          .newBuilder[StatusV1, StatusV2]
          .renameCase(DynamicOptic.root, "PendingV1", "PendingV2")
          .renameCase(DynamicOptic.root, "ApprovedV1", "AcceptedV2")

        val result = builder.build

        assertTrue(result.isRight)
      }
    ),
    suite("Special cases")(
      test("identity migration passes validation") {
        // Same schema, no actions needed
        val builder = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]

        val result = builder.build

        assertTrue(result.isRight)
      },
      test("migration with collection operations") {
        // Collections don't add/remove fields, just transform values
        case class PersonWithEmails(name: String, emails: Vector[String])
        object PersonWithEmails {
          implicit val schema: Schema[PersonWithEmails] = Schema.derived[PersonWithEmails]
        }

        val builder = MigrationBuilder
          .newBuilder[PersonWithEmails, PersonWithEmails]
          .transformElements(
            DynamicOptic.root.field("emails"),
            SchemaExpr.StringUppercase(SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root))
          )

        val result = builder.build

        assertTrue(result.isRight)
      }
    )
  )
}
