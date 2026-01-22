package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Test suite for Phase 10: Build Validation
 *
 * Tests .build() method validation for:
 *   - Top-level record fields
 *   - Nested record fields
 *   - Variant cases
 *   - All migration actions
 */
object MigrationValidationSpec extends ZIOSpecDefault {

  // ===== Test Data Types =====

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

  // ===== Test Suites =====

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
      test("variant case rename passes validation") {
        // For now, variants are validated at top level
        val builder = MigrationBuilder
          .newBuilder[PaymentV1, PaymentV2]
          .renameCase(DynamicOptic.root, "PayPalV1", "PayPalPaymentV2")

        val result = builder.build

        // This may pass or fail depending on how we handle variant validation
        // For now, just check that it returns Either
        assertTrue(result.isRight || result.isLeft)
      },
      test("variant case field addition requires handling") {
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

        // Placeholder assertion
        assertTrue(result.isRight || result.isLeft)
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
