package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import scala.annotation.nowarn
import scala.language.reflectiveCalls

/**
 * Tests that migrations work with structural types. Structural types are
 * JVM-only (require reflection). These tests verify:
 *   - Structural type → case class migration
 *   - Structural type → structural type migration
 *   - Migration composition across structural types
 *   - Reverse migrations with structural types
 */
object StructuralMigrationSpec extends SchemaBaseSpec {

  // --- Target case class (the "current" version) ---
  case class PersonV2(fullName: String, age: Int)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class PersonV3(fullName: String, age: Int, country: String)
  object PersonV3 {
    implicit val schema: Schema[PersonV3] = Schema.derived
  }

  // --- Structural types (representing "old" versions) ---
  type PersonV1Structural = { def firstName: String; def lastName: String }

  type PersonV2Structural = { def fullName: String; def age: Int }

  // --- Enum / sealed trait types for union migration tests ---
  enum PaymentV1 {
    case CreditCard(number: String, expiry: String)
    case PayPal(email: String)
  }
  object PaymentV1 {
    implicit val schema: Schema[PaymentV1] = Schema.derived
  }

  enum PaymentV2 {
    case CreditCard(number: String, expiry: String, cvv: String)
    case PaypalPayment(email: String)
  }
  object PaymentV2 {
    implicit val schema: Schema[PaymentV2] = Schema.derived
  }

  sealed trait ResultV1
  object ResultV1 {
    case class Success(value: Int)    extends ResultV1
    case class Failure(error: String) extends ResultV1
    implicit val schema: Schema[ResultV1] = Schema.derived
  }

  sealed trait ResultV2
  object ResultV2 {
    case class Ok(value: Int, timestamp: Long) extends ResultV2
    case class Failure(error: String)          extends ResultV2
    implicit val schema: Schema[ResultV2] = Schema.derived
  }

  def spec: Spec[Any, Nothing] = suite("StructuralMigrationSpec")(
    suite("structural → case class migration")(
      test("rename + addField migrates structural type to case class") {
        val sourceSchema = Schema.derived[PersonV1Structural]

        val migration = Migration(
          dynamicMigration = DynamicMigration(
            Vector(
              MigrationAction.Rename(
                at = DynamicOptic.root.field("firstName"),
                to = "fullName"
              ),
              MigrationAction.DropField(
                at = DynamicOptic.root.field("lastName"),
                defaultForReverse = ""
              ),
              MigrationAction.AddField(
                at = DynamicOptic.root.field("age"),
                default = 0
              )
            )
          ),
          sourceSchema = sourceSchema,
          targetSchema = PersonV2.schema
        )

        @nowarn("msg=unused") val oldPerson: PersonV1Structural = new {
          def firstName: String = "Alice"
          def lastName: String  = "Smith"
        }

        val result = migration.apply(oldPerson)

        assertTrue(
          result == Right(PersonV2("Alice", 0))
        )
      },
      test("join fields migrates structural type to case class") {
        val sourceSchema = Schema.derived[PersonV1Structural]

        val migration = Migration(
          dynamicMigration = DynamicMigration(
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
              MigrationAction.AddField(
                at = DynamicOptic.root.field("age"),
                default = 25
              )
            )
          ),
          sourceSchema = sourceSchema,
          targetSchema = PersonV2.schema
        )

        @nowarn("msg=unused") val oldPerson: PersonV1Structural = new {
          def firstName: String = "John"
          def lastName: String  = "Doe"
        }

        val result = migration.apply(oldPerson)

        assertTrue(
          result == Right(PersonV2("John Doe", 25))
        )
      }
    ),
    suite("structural → structural migration")(
      test("rename + addField between structural types via DynamicMigration") {
        val sourceSchema = Schema.derived[PersonV1Structural]

        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.Rename(
              at = DynamicOptic.root.field("firstName"),
              to = "fullName"
            ),
            MigrationAction.DropField(
              at = DynamicOptic.root.field("lastName"),
              defaultForReverse = ""
            ),
            MigrationAction.AddField(
              at = DynamicOptic.root.field("age"),
              default = 42
            )
          )
        )

        @nowarn("msg=unused") val oldPerson: PersonV1Structural = new {
          def firstName: String = "Bob"
          def lastName: String  = "Jones"
        }

        // Convert structural source to DynamicValue, apply migration, verify result
        val sourceDv = sourceSchema.toDynamicValue(oldPerson)
        val result   = dynMigration.apply(sourceDv)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              zio.blocks.chunk.Chunk(
                "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
                "age"      -> DynamicValue.Primitive(PrimitiveValue.Int(42))
              )
            )
          )
        )
      }
    ),
    suite("case class → structural migration (via .structural)")(
      test("case class schema converted to structural can be used as migration source") {
        val nominalSchema    = Schema.derived[PersonV2]
        val structuralSchema = nominalSchema.structural

        // Migration from structural representation of PersonV2 to PersonV2Structural
        // This is an identity-like migration since both have the same fields
        val migration = Migration(
          dynamicMigration = DynamicMigration.identity,
          sourceSchema = structuralSchema,
          targetSchema = Schema.derived[PersonV2Structural]
        )

        val person = PersonV2("Alice", 30)
        // Convert to structural representation first
        val dynamicValue     = nominalSchema.toDynamicValue(person)
        val structuralPerson = structuralSchema.fromDynamicValue(dynamicValue)

        assertTrue(structuralPerson.isRight) &&
        assertTrue {
          val sp = structuralPerson.toOption.get
          migration.apply(sp).isRight
        }
      }
    ),
    suite("composition with structural types")(
      test("structural → case class → case class composition") {
        val sourceSchema = Schema.derived[PersonV1Structural]

        // V1 (structural) → V2 (case class)
        val migration1 = Migration(
          dynamicMigration = DynamicMigration(
            Vector(
              MigrationAction.Rename(
                at = DynamicOptic.root.field("firstName"),
                to = "fullName"
              ),
              MigrationAction.DropField(
                at = DynamicOptic.root.field("lastName"),
                defaultForReverse = ""
              ),
              MigrationAction.AddField(
                at = DynamicOptic.root.field("age"),
                default = 30
              )
            )
          ),
          sourceSchema = sourceSchema,
          targetSchema = PersonV2.schema
        )

        // V2 → V3: Add country
        val migration2 = Migration(
          dynamicMigration = DynamicMigration(
            Vector(
              MigrationAction.AddField(
                at = DynamicOptic.root.field("country"),
                default = "US"
              )
            )
          ),
          sourceSchema = PersonV2.schema,
          targetSchema = PersonV3.schema
        )

        val composed = migration1 ++ migration2

        @nowarn("msg=unused") val oldPerson: PersonV1Structural = new {
          def firstName: String = "Jane"
          def lastName: String  = "Doe"
        }

        val result = composed.apply(oldPerson)

        assertTrue(
          result == Right(PersonV3("Jane", 30, "US"))
        )
      },
      test("structural → structural → case class composition") {
        type AddressV1 = { def street: String; def city: String }
        type AddressV2 = { def street: String; def city: String; def zip: String }

        case class Address(street: String, city: String, zip: String, country: String)
        object Address {
          implicit val schema: Schema[Address] = Schema.derived
        }

        val v1Schema = Schema.derived[AddressV1]
        val v2Schema = Schema.derived[AddressV2]

        // V1 → V2: Add zip
        val migration1 = Migration(
          dynamicMigration = DynamicMigration(
            Vector(
              MigrationAction.AddField(
                at = DynamicOptic.root.field("zip"),
                default = "00000"
              )
            )
          ),
          sourceSchema = v1Schema,
          targetSchema = v2Schema
        )

        // V2 → Address: Add country
        val migration2 = Migration(
          dynamicMigration = DynamicMigration(
            Vector(
              MigrationAction.AddField(
                at = DynamicOptic.root.field("country"),
                default = "US"
              )
            )
          ),
          sourceSchema = v2Schema,
          targetSchema = Address.schema
        )

        val composed = migration1 ++ migration2

        @nowarn("msg=unused") val oldAddress: AddressV1 = new {
          def street: String = "123 Main St"
          def city: String   = "Springfield"
        }

        val result = composed.apply(oldAddress)

        assertTrue(
          result == Right(Address("123 Main St", "Springfield", "00000", "US"))
        )
      }
    ),
    suite("reverse migration with structural types")(
      test("reverse of case class → structural via DynamicMigration") {
        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("country"),
              default = "US"
            )
          )
        )

        val reversed = dynMigration.reverse

        // Apply forward on PersonV2-like DynamicValue, then reverse
        val personV3Dv = PersonV3.schema.toDynamicValue(PersonV3("Alice", 30, "US"))
        val result     = reversed.apply(personV3Dv)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              zio.blocks.chunk.Chunk(
                "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
                "age"      -> DynamicValue.Primitive(PrimitiveValue.Int(30))
              )
            )
          )
        )
      },
      test("reverse.reverse equals original structurally") {
        val sourceSchema = Schema.derived[PersonV1Structural]

        val migration = Migration(
          dynamicMigration = DynamicMigration(
            Vector(
              MigrationAction.Rename(
                at = DynamicOptic.root.field("firstName"),
                to = "fullName"
              ),
              MigrationAction.DropField(
                at = DynamicOptic.root.field("lastName"),
                defaultForReverse = ""
              ),
              MigrationAction.AddField(
                at = DynamicOptic.root.field("age"),
                default = 0
              )
            )
          ),
          sourceSchema = sourceSchema,
          targetSchema = PersonV2.schema
        )

        val doubleReversed = migration.reverse.reverse

        assertTrue(doubleReversed.dynamicMigration == migration.dynamicMigration)
      }
    ),
    suite("identity migration with structural types")(
      test("identity migration on structural type preserves DynamicValue") {
        val schema = Schema.derived[PersonV1Structural]

        @nowarn("msg=unused") val person: PersonV1Structural = new {
          def firstName: String = "Test"
          def lastName: String  = "User"
        }

        val original = schema.toDynamicValue(person)
        val result   = DynamicMigration.identity.apply(original)

        assertTrue(
          result == Right(original)
        )
      }
    ),
    suite("structural union type migrations")(
      test("renameCase on structural union source via DynamicMigration") {
        // Use .structural to convert enum to union type, then rename a case
        val structuralSchema = PaymentV1.schema.structural

        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.RenameCase(
              at = DynamicOptic.root,
              from = "PayPal",
              to = "PaypalPayment"
            )
          )
        )

        // Create a PayPal instance, convert to DynamicValue via structural schema
        val paypal    = PaymentV1.PayPal("alice@example.com")
        val dynamicDv = PaymentV1.schema.toDynamicValue(paypal)

        val result = dynMigration.apply(dynamicDv)

        assertTrue(
          result == Right(
            DynamicValue.Variant(
              "PaypalPayment",
              DynamicValue.Record(
                zio.blocks.chunk.Chunk(
                  "email" -> DynamicValue.Primitive(PrimitiveValue.String("alice@example.com"))
                )
              )
            )
          )
        )
      },
      test("renameCase leaves non-matching cases unchanged") {
        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.RenameCase(
              at = DynamicOptic.root,
              from = "PayPal",
              to = "PaypalPayment"
            )
          )
        )

        // CreditCard should pass through unchanged
        val cc        = PaymentV1.CreditCard("4111", "12/25")
        val dynamicDv = PaymentV1.schema.toDynamicValue(cc)
        val result    = dynMigration.apply(dynamicDv)

        assertTrue(
          result == Right(dynamicDv)
        )
      },
      test("transformCase adds field to a specific case") {
        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.TransformCase(
              at = DynamicOptic.root,
              caseName = "CreditCard",
              actions = Vector(
                MigrationAction.AddField(
                  at = DynamicOptic.root.field("cvv"),
                  default = "000"
                )
              )
            )
          )
        )

        val cc        = PaymentV1.CreditCard("4111", "12/25")
        val dynamicDv = PaymentV1.schema.toDynamicValue(cc)
        val result    = dynMigration.apply(dynamicDv)

        assertTrue(
          result == Right(
            DynamicValue.Variant(
              "CreditCard",
              DynamicValue.Record(
                zio.blocks.chunk.Chunk(
                  "number" -> DynamicValue.Primitive(PrimitiveValue.String("4111")),
                  "expiry" -> DynamicValue.Primitive(PrimitiveValue.String("12/25")),
                  "cvv"    -> DynamicValue.Primitive(PrimitiveValue.String("000"))
                )
              )
            )
          )
        )
      },
      test("transformCase leaves non-matching cases unchanged") {
        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.TransformCase(
              at = DynamicOptic.root,
              caseName = "CreditCard",
              actions = Vector(
                MigrationAction.AddField(
                  at = DynamicOptic.root.field("cvv"),
                  default = "000"
                )
              )
            )
          )
        )

        val paypal    = PaymentV1.PayPal("bob@example.com")
        val dynamicDv = PaymentV1.schema.toDynamicValue(paypal)
        val result    = dynMigration.apply(dynamicDv)

        assertTrue(
          result == Right(dynamicDv)
        )
      },
      test("combined renameCase + transformCase migrates enum union") {
        // Full migration: PaymentV1 → PaymentV2
        // - Rename PayPal → PaypalPayment
        // - Add cvv field to CreditCard
        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.RenameCase(
              at = DynamicOptic.root,
              from = "PayPal",
              to = "PaypalPayment"
            ),
            MigrationAction.TransformCase(
              at = DynamicOptic.root,
              caseName = "CreditCard",
              actions = Vector(
                MigrationAction.AddField(
                  at = DynamicOptic.root.field("cvv"),
                  default = "000"
                )
              )
            )
          )
        )

        // Test PayPal case
        val paypalDv     = PaymentV1.schema.toDynamicValue(PaymentV1.PayPal("alice@example.com"))
        val paypalResult = dynMigration.apply(paypalDv)

        // Test CreditCard case
        val ccDv     = PaymentV1.schema.toDynamicValue(PaymentV1.CreditCard("4111", "12/25"))
        val ccResult = dynMigration.apply(ccDv)

        assertTrue(
          paypalResult == Right(
            DynamicValue.Variant(
              "PaypalPayment",
              DynamicValue.Record(
                zio.blocks.chunk.Chunk(
                  "email" -> DynamicValue.Primitive(PrimitiveValue.String("alice@example.com"))
                )
              )
            )
          ),
          ccResult == Right(
            DynamicValue.Variant(
              "CreditCard",
              DynamicValue.Record(
                zio.blocks.chunk.Chunk(
                  "number" -> DynamicValue.Primitive(PrimitiveValue.String("4111")),
                  "expiry" -> DynamicValue.Primitive(PrimitiveValue.String("12/25")),
                  "cvv"    -> DynamicValue.Primitive(PrimitiveValue.String("000"))
                )
              )
            )
          )
        )
      },
      test("renameCase on sealed trait structural source") {
        // Use .structural on a sealed trait and migrate via renameCase
        val structuralSchema = ResultV1.schema.structural

        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.RenameCase(
              at = DynamicOptic.root,
              from = "Success",
              to = "Ok"
            )
          )
        )

        val success   = ResultV1.Success(42)
        val dynamicDv = ResultV1.schema.toDynamicValue(success)
        val result    = dynMigration.apply(dynamicDv)

        assertTrue(
          result == Right(
            DynamicValue.Variant(
              "Ok",
              DynamicValue.Record(
                zio.blocks.chunk.Chunk(
                  "value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
                )
              )
            )
          )
        )
      },
      test("renameCase + transformCase on sealed trait for full migration") {
        // ResultV1 → ResultV2: Rename Success→Ok, add timestamp to Ok
        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.RenameCase(
              at = DynamicOptic.root,
              from = "Success",
              to = "Ok"
            ),
            MigrationAction.TransformCase(
              at = DynamicOptic.root,
              caseName = "Ok",
              actions = Vector(
                MigrationAction.AddField(
                  at = DynamicOptic.root.field("timestamp"),
                  default = 0L
                )
              )
            )
          )
        )

        val successDv = ResultV1.schema.toDynamicValue(ResultV1.Success(42))
        val failureDv = ResultV1.schema.toDynamicValue(ResultV1.Failure("oops"))

        val successResult = dynMigration.apply(successDv)
        val failureResult = dynMigration.apply(failureDv)

        assertTrue(
          successResult == Right(
            DynamicValue.Variant(
              "Ok",
              DynamicValue.Record(
                zio.blocks.chunk.Chunk(
                  "value"     -> DynamicValue.Primitive(PrimitiveValue.Int(42)),
                  "timestamp" -> DynamicValue.Primitive(PrimitiveValue.Long(0L))
                )
              )
            )
          ),
          failureResult == Right(failureDv)
        )
      }
    ),
    suite("structural union reverse migrations")(
      test("reverse of renameCase flips from/to") {
        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.RenameCase(
              at = DynamicOptic.root,
              from = "PayPal",
              to = "PaypalPayment"
            )
          )
        )

        val reversed = dynMigration.reverse

        // Apply reverse to a "PaypalPayment" variant
        val renamedDv = DynamicValue.Variant(
          "PaypalPayment",
          DynamicValue.Record(
            zio.blocks.chunk.Chunk(
              "email" -> DynamicValue.Primitive(PrimitiveValue.String("alice@example.com"))
            )
          )
        )

        val result = reversed.apply(renamedDv)

        assertTrue(
          result == Right(
            DynamicValue.Variant(
              "PayPal",
              DynamicValue.Record(
                zio.blocks.chunk.Chunk(
                  "email" -> DynamicValue.Primitive(PrimitiveValue.String("alice@example.com"))
                )
              )
            )
          )
        )
      },
      test("reverse of transformCase reverses nested actions") {
        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.TransformCase(
              at = DynamicOptic.root,
              caseName = "CreditCard",
              actions = Vector(
                MigrationAction.AddField(
                  at = DynamicOptic.root.field("cvv"),
                  default = "000"
                )
              )
            )
          )
        )

        val reversed = dynMigration.reverse

        // Apply reverse: should drop the cvv field from CreditCard
        val withCvv = DynamicValue.Variant(
          "CreditCard",
          DynamicValue.Record(
            zio.blocks.chunk.Chunk(
              "number" -> DynamicValue.Primitive(PrimitiveValue.String("4111")),
              "expiry" -> DynamicValue.Primitive(PrimitiveValue.String("12/25")),
              "cvv"    -> DynamicValue.Primitive(PrimitiveValue.String("000"))
            )
          )
        )

        val result = reversed.apply(withCvv)

        assertTrue(
          result == Right(
            DynamicValue.Variant(
              "CreditCard",
              DynamicValue.Record(
                zio.blocks.chunk.Chunk(
                  "number" -> DynamicValue.Primitive(PrimitiveValue.String("4111")),
                  "expiry" -> DynamicValue.Primitive(PrimitiveValue.String("12/25"))
                )
              )
            )
          )
        )
      },
      test("round-trip: forward then reverse restores original union value") {
        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.RenameCase(
              at = DynamicOptic.root,
              from = "PayPal",
              to = "PaypalPayment"
            ),
            MigrationAction.TransformCase(
              at = DynamicOptic.root,
              caseName = "CreditCard",
              actions = Vector(
                MigrationAction.AddField(
                  at = DynamicOptic.root.field("cvv"),
                  default = "000"
                )
              )
            )
          )
        )

        val originalDv = PaymentV1.schema.toDynamicValue(PaymentV1.PayPal("test@example.com"))

        val roundTrip = for {
          migrated <- dynMigration.apply(originalDv)
          restored <- dynMigration.reverse.apply(migrated)
        } yield restored

        assertTrue(roundTrip == Right(originalDv))
      },
      test("reverse.reverse equals original for union migration") {
        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.RenameCase(
              at = DynamicOptic.root,
              from = "Success",
              to = "Ok"
            ),
            MigrationAction.TransformCase(
              at = DynamicOptic.root,
              caseName = "Ok",
              actions = Vector(
                MigrationAction.AddField(
                  at = DynamicOptic.root.field("timestamp"),
                  default = 0L
                )
              )
            )
          )
        )

        val doubleReversed = dynMigration.reverse.reverse

        assertTrue(doubleReversed == dynMigration)
      }
    ),
    suite("structural union via .structural as migration source")(
      test(".structural enum schema produces Variant that can be migrated") {
        val structuralSchema = PaymentV1.schema.structural

        // Verify that .structural produces a Variant schema
        val isVariant = (structuralSchema.reflect: @unchecked) match {
          case _: Reflect.Variant[_, _] => true
          case _                        => false
        }

        // Verify it can encode/decode via DynamicValue
        val paypal    = PaymentV1.PayPal("test@example.com")
        val dynamicDv = PaymentV1.schema.toDynamicValue(paypal)

        // The DynamicValue from the nominal schema should be compatible with migration
        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.RenameCase(
              at = DynamicOptic.root,
              from = "PayPal",
              to = "PaypalPayment"
            )
          )
        )

        val result = dynMigration.apply(dynamicDv)

        assertTrue(
          isVariant,
          result.isRight,
          result == Right(
            DynamicValue.Variant(
              "PaypalPayment",
              DynamicValue.Record(
                zio.blocks.chunk.Chunk(
                  "email" -> DynamicValue.Primitive(PrimitiveValue.String("test@example.com"))
                )
              )
            )
          )
        )
      },
      test(".structural sealed trait schema can serve as typed migration source") {
        val structuralSchema = ResultV1.schema.structural

        // Verify the structural schema is a Variant with the right cases
        val caseNames = (structuralSchema.reflect: @unchecked) match {
          case v: Reflect.Variant[_, _] => v.cases.map(_.name).toSet
          case _                        => Set.empty[String]
        }

        assertTrue(
          caseNames == Set("Success", "Failure")
        )
      }
    )
  )
}
