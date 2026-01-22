package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import MigrationTestCompat._

object MigrationBuilderSpec extends ZIOSpecDefault {
  // Ensure the import is used (brings implicit conversion in Scala 2, dummy in Scala 3)
  locally(ensureLoaded)

  // Test data structures
  case class PersonV1(firstName: String, lastName: String, age: Int)
  case class PersonV2(fullName: String, age: Int, country: String)

  case class WithOption(name: Option[String], age: Int)
  case class WithoutOption(name: String, age: Int)

  case class WithString(age: String)
  case class WithInt(age: Int)

  case class WithList(items: Vector[Int])
  case class WithMap(data: Map[String, Int])

  sealed trait PaymentMethod
  case class CreditCard(number: String, cvv: String) extends PaymentMethod
  case class PayPal(email: String)                   extends PaymentMethod

  object PaymentMethod extends CompanionOptics[PaymentMethod]

  implicit val personV1Schema: Schema[PersonV1]           = Schema.derived[PersonV1]
  implicit val personV2Schema: Schema[PersonV2]           = Schema.derived[PersonV2]
  implicit val withOptionSchema: Schema[WithOption]       = Schema.derived[WithOption]
  implicit val withoutOptionSchema: Schema[WithoutOption] = Schema.derived[WithoutOption]
  implicit val withStringSchema: Schema[WithString]       = Schema.derived[WithString]
  implicit val withIntSchema: Schema[WithInt]             = Schema.derived[WithInt]
  implicit val withListSchema: Schema[WithList]           = Schema.derived[WithList]
  implicit val withMapSchema: Schema[WithMap]             = Schema.derived[WithMap]
  implicit val paymentSchema: Schema[PaymentMethod]       = Schema.derived[PaymentMethod]

  def spec = suite("MigrationBuilderSpec")(
    suite("Basic Builder Construction")(
      test("create empty builder") {
        val builder = MigrationBuilder.newBuilder[PersonV1, PersonV2]
        assertTrue(
          builder.sourceSchema != null,
          builder.targetSchema != null,
          builder.actions.isEmpty
        )
      },
      test("build empty migration for identical schemas") {
        // Use same schema for source and target - no actions needed
        val result = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .build
        result match {
          case Right(migration) =>
            assertTrue(
              migration.sourceSchema != null,
              migration.targetSchema != null,
              migration.dynamicMigration.actions.isEmpty
            )
          case Left(_) =>
            // Fail with error message
            assertTrue(false)
        }
      },
      test("buildPartial empty migration") {
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .buildPartial
        assertTrue(
          migration.sourceSchema != null,
          migration.targetSchema != null,
          migration.dynamicMigration.actions.isEmpty
        )
      }
    ),
    suite("Record Operations")(
      test("addField - add new field with literal default") {
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(
            DynamicOptic.root.field("extra"),
            SchemaExpr.Literal[DynamicValue, String]("default", Schema.string)
          )
          .buildPartial

        val v1     = PersonV1("John", "Doe", 30)
        val result = migration(v1)

        assertTrue(result.isRight)
      },
      test("dropField - remove field") {
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .dropField(
            DynamicOptic.root.field("age"),
            SchemaExpr.Literal[DynamicValue, Int](0, Schema.int)
          )
          .buildPartial

        val v1 = PersonV1("John", "Doe", 30)
        // Test at DynamicValue level since structure changed
        val dynamicValue = personV1Schema.toDynamicValue(v1)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(result.isRight)
      },
      test("renameField - rename single field") {
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .renameField(
            DynamicOptic.root.field("firstName"),
            "givenName"
          )
          .buildPartial

        val v1 = PersonV1("John", "Doe", 30)
        // Test at DynamicValue level since structure changed
        val dynamicValue = personV1Schema.toDynamicValue(v1)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(result.isRight)
      },
      test("transformField - increment age") {
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .transformField(
            DynamicOptic.root.field("age"),
            SchemaExpr.Arithmetic[DynamicValue, Int](
              SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
              SchemaExpr.Literal[DynamicValue, Int](1, Schema.int),
              SchemaExpr.ArithmeticOperator.Add,
              IsNumeric.IsInt
            )
          )
          .buildPartial

        val v1     = PersonV1("John", "Doe", 30)
        val result = migration(v1)

        assertTrue(result.isRight)
      },
      test("mandateField - unwrap Option with default") {
        val migration = MigrationBuilder
          .newBuilder[WithOption, WithoutOption]
          .mandateField(
            DynamicOptic.root.field("name"),
            SchemaExpr.Literal[DynamicValue, String]("Unknown", Schema.string)
          )
          .buildPartial

        val v1     = WithOption(Some("John"), 30)
        val result = migration(v1)

        assertTrue(result.isRight)
      },
      test("optionalizeField - wrap field in Option") {
        val migration = MigrationBuilder
          .newBuilder[WithoutOption, WithOption]
          .optionalizeField(
            DynamicOptic.root.field("name"),
            SchemaExpr.Literal[DynamicValue, String]("", Schema.string)
          )
          .buildPartial

        val v1     = WithoutOption("John", 30)
        val result = migration(v1)

        assertTrue(result.isRight)
      },
      test("optionalizeField reverse - unwrap Some successfully") {
        val migration = MigrationBuilder
          .newBuilder[WithoutOption, WithOption]
          .optionalizeField(
            DynamicOptic.root.field("name"),
            SchemaExpr.Literal[DynamicValue, String]("DefaultName", Schema.string)
          )
          .buildPartial

        val reverseMigration = migration.reverse
        val v2               = WithOption(Some("Alice"), 25)
        val result           = reverseMigration(v2)

        assertTrue(
          result.isRight,
          result.map { v1 =>
            v1.name == "Alice" && v1.age == 25
          }.getOrElse(false)
        )
      },
      test("optionalizeField reverse - use default for None") {
        val migration = MigrationBuilder
          .newBuilder[WithoutOption, WithOption]
          .optionalizeField(
            DynamicOptic.root.field("name"),
            SchemaExpr.Literal[DynamicValue, String]("DefaultName", Schema.string)
          )
          .buildPartial

        val reverseMigration = migration.reverse
        val v2               = WithOption(None, 30)
        val result           = reverseMigration(v2)

        assertTrue(
          result.isRight,
          result.map { v1 =>
            v1.name == "DefaultName" && v1.age == 30
          }.getOrElse(false)
        )
      },
      test("optionalizeField reverse - round trip with Some") {
        val migration = MigrationBuilder
          .newBuilder[WithoutOption, WithOption]
          .optionalizeField(
            DynamicOptic.root.field("name"),
            SchemaExpr.Literal[DynamicValue, String]("", Schema.string)
          )
          .buildPartial

        val original = WithoutOption("Bob", 35)
        val forward  = migration(original)
        val backward = forward.flatMap(migration.reverse.apply)

        assertTrue(
          backward.isRight,
          backward.map(v => v.name == "Bob" && v.age == 35).getOrElse(false)
        )
      },
      test("changeFieldType - convert String to Int") {
        val migration = MigrationBuilder
          .newBuilder[WithString, WithInt]
          .changeFieldType(
            DynamicOptic.root.field("age"),
            PrimitiveConverter.StringToInt
          )
          .buildPartial

        val v1     = WithString("30")
        val result = migration(v1)

        assertTrue(result.isRight)
      }
    ),
    suite("Multi-Field Operations")(
      test("joinFields - combine firstName and lastName") {
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .joinFields(
            DynamicOptic.root.field("fullName"),
            Vector(
              DynamicOptic.root.field("firstName"),
              DynamicOptic.root.field("lastName")
            ),
            SchemaExpr.StringConcat[DynamicValue](
              SchemaExpr.StringConcat[DynamicValue](
                SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root.field("field0")),
                SchemaExpr.Literal[DynamicValue, String](" ", Schema.string)
              ),
              SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root.field("field1"))
            )
          )
          .buildPartial

        val v1 = PersonV1("John", "Doe", 30)
        // Test at DynamicValue level since structure changed
        val dynamicValue = personV1Schema.toDynamicValue(v1)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(result.isRight)
      },
      test("splitField - split fullName into firstName and lastName") {
        val migration = MigrationBuilder
          .newBuilder[PersonV2, PersonV2]
          .splitField(
            DynamicOptic.root.field("fullName"),
            Vector(
              DynamicOptic.root.field("firstName"),
              DynamicOptic.root.field("lastName")
            ),
            SchemaExpr.StringSplit[DynamicValue](
              SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root),
              " "
            )
          )
          .buildPartial

        val v2 = PersonV2("John Doe", 30, "USA")
        // Test at DynamicValue level since structure changed
        val dynamicValue = personV2Schema.toDynamicValue(v2)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(result.isRight)
      }
    ),
    suite("Collection Operations")(
      test("transformElements - add 10 to each element") {
        val migration = MigrationBuilder
          .newBuilder[WithList, WithList]
          .transformElements(
            DynamicOptic.root.field("items"),
            SchemaExpr.Arithmetic[DynamicValue, Int](
              SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
              SchemaExpr.Literal[DynamicValue, Int](10, Schema.int),
              SchemaExpr.ArithmeticOperator.Add,
              IsNumeric.IsInt
            )
          )
          .buildPartial

        val v1     = WithList(Vector(1, 2, 3))
        val result = migration(v1)

        assertTrue(result.isRight)
      },
      test("transformKeys - uppercase all keys") {
        val migration = MigrationBuilder
          .newBuilder[WithMap, WithMap]
          .transformKeys(
            DynamicOptic.root.field("data"),
            SchemaExpr.StringUppercase[DynamicValue](
              SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
            )
          )
          .buildPartial

        val v1     = WithMap(Map("foo" -> 1, "bar" -> 2))
        val result = migration(v1)

        assertTrue(result.isRight)
      },
      test("transformValues - add 100 to each value") {
        val migration = MigrationBuilder
          .newBuilder[WithMap, WithMap]
          .transformValues(
            DynamicOptic.root.field("data"),
            SchemaExpr.Arithmetic[DynamicValue, Int](
              SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
              SchemaExpr.Literal[DynamicValue, Int](100, Schema.int),
              SchemaExpr.ArithmeticOperator.Add,
              IsNumeric.IsInt
            )
          )
          .buildPartial

        val v1     = WithMap(Map("foo" -> 1, "bar" -> 2))
        val result = migration(v1)

        assertTrue(result.isRight)
      }
    ),
    suite("Enum Operations")(
      test("renameCase - rename PayPal case") {
        val migration = MigrationBuilder
          .newBuilder[PaymentMethod, PaymentMethod]
          .renameCase(
            DynamicOptic.root,
            "PayPal",
            "PaypalPayment"
          )
          .buildPartial

        val payment = PayPal("test@example.com")
        // Test at DynamicValue level since case name changed
        val dynamicValue = paymentSchema.toDynamicValue(payment)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(result.isRight)
      },
      test("transformCase - rename field in CreditCard case") {
        val migration = MigrationBuilder
          .newBuilder[PaymentMethod, PaymentMethod]
          .transformCase(
            DynamicOptic.root,
            "CreditCard",
            Vector(
              MigrationAction.Rename(
                DynamicOptic.root.field("number"),
                "cardNumber"
              )
            )
          )
          .buildPartial

        val payment = CreditCard("1234-5678-9012-3456", "123")
        // Test at DynamicValue level since field name changed
        val dynamicValue = paymentSchema.toDynamicValue(payment)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(result.isRight)
      }
    ),
    suite("Enum Selector Operations")(
      test("renameCase with selector - rename PayPal case using .when[PayPal]") {
        val migration = MigrationBuilder
          .newBuilder[PaymentMethod, PaymentMethod]
          .renameCase((p: PaymentMethod) => p.when[PayPal], "PaypalPayment")
          .buildPartial

        val payment = PayPal("test@example.com")
        // Test at DynamicValue level since case name changed
        val dynamicValue = paymentSchema.toDynamicValue(payment)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(result.isRight)
      },
      test("transformCase with selector - rename field in CreditCard case using .when[CreditCard]") {
        val migration = MigrationBuilder
          .newBuilder[PaymentMethod, PaymentMethod]
          .transformCase((p: PaymentMethod) => p.when[CreditCard])(
            _.renameField(DynamicOptic.root.field("number"), "cardNumber")
          )
          .buildPartial

        val payment = CreditCard("1234-5678-9012-3456", "123")
        // Test at DynamicValue level since field name changed
        val dynamicValue = paymentSchema.toDynamicValue(payment)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(result.isRight)
      },
      test("transformCase with selector - verify correct action generated") {
        val builder = MigrationBuilder
          .newBuilder[PaymentMethod, PaymentMethod]
          .transformCase((p: PaymentMethod) => p.when[CreditCard])(
            _.renameField(DynamicOptic.root.field("number"), "cardNumber")
          )

        // Verify the action structure
        assertTrue(
          builder.actions.length == 1,
          builder.actions.head.isInstanceOf[MigrationAction.TransformCase]
        )

        val action = builder.actions.head.asInstanceOf[MigrationAction.TransformCase]
        assertTrue(
          action.caseName == "CreditCard",
          action.actions.length == 1,
          action.actions.head.isInstanceOf[MigrationAction.Rename]
        )
      },
      test("renameCase with selector - verify correct action generated") {
        val builder = MigrationBuilder
          .newBuilder[PaymentMethod, PaymentMethod]
          .renameCase((p: PaymentMethod) => p.when[PayPal], "PaypalPayment")

        // Verify the action structure
        assertTrue(
          builder.actions.length == 1,
          builder.actions.head.isInstanceOf[MigrationAction.RenameCase]
        )

        val action = builder.actions.head.asInstanceOf[MigrationAction.RenameCase]
        assertTrue(
          action.from == "PayPal",
          action.to == "PaypalPayment"
        )
      }
    ),
    suite("Fluent API Chaining")(
      test("chain multiple operations") {
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(
            DynamicOptic.root.field("country"),
            SchemaExpr.Literal[DynamicValue, String]("USA", Schema.string)
          )
          .renameField(
            DynamicOptic.root.field("firstName"),
            "givenName"
          )
          .dropField(
            DynamicOptic.root.field("lastName"),
            SchemaExpr.Literal[DynamicValue, String]("", Schema.string)
          )
          .buildPartial

        val v1 = PersonV1("John", "Doe", 30)
        // Test at DynamicValue level since structure changed
        val dynamicValue = personV1Schema.toDynamicValue(v1)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(result.isRight)
      },
      test("verify immutability - builder returns new instance") {
        val builder1 = MigrationBuilder.newBuilder[PersonV1, PersonV2]
        val builder2 = builder1.addField(
          DynamicOptic.root.field("country"),
          SchemaExpr.Literal[DynamicValue, String]("USA", Schema.string)
        )

        assertTrue(
          builder1 != builder2,
          builder1.actions.isEmpty,
          builder2.actions.length == 1
        )
      },
      test("each method adds exactly one action") {
        val builder = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(
            DynamicOptic.root.field("extra"),
            SchemaExpr.Literal[DynamicValue, String]("test", Schema.string)
          )
          .renameField(
            DynamicOptic.root.field("firstName"),
            "givenName"
          )
          .dropField(
            DynamicOptic.root.field("lastName"),
            SchemaExpr.Literal[DynamicValue, String]("", Schema.string)
          )

        assertTrue(builder.actions.length == 3)
      }
    ),
    suite("Build Methods")(
      test("build validates while buildPartial does not") {
        // This migration is incomplete - adds field but doesn't provide it in schema
        val buildResult = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(
            DynamicOptic.root.field("extra"),
            SchemaExpr.Literal[DynamicValue, String]("test", Schema.string)
          )
          .build

        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(
            DynamicOptic.root.field("extra"),
            SchemaExpr.Literal[DynamicValue, String]("test", Schema.string)
          )
          .buildPartial

        assertTrue(
          // build should fail validation (extra field doesn't exist in schema)
          buildResult.isLeft,
          // buildPartial should succeed
          migration.dynamicMigration.actions.length == 1
        )
      }
    ),
    suite("Integration Tests")(
      test("complete migration PersonV1 to PersonV2") {
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .joinFields(
            DynamicOptic.root.field("fullName"),
            Vector(
              DynamicOptic.root.field("firstName"),
              DynamicOptic.root.field("lastName")
            ),
            SchemaExpr.StringConcat[DynamicValue](
              SchemaExpr.StringConcat[DynamicValue](
                SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root.field("field0")),
                SchemaExpr.Literal[DynamicValue, String](" ", Schema.string)
              ),
              SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root.field("field1"))
            )
          )
          .addField(
            DynamicOptic.root.field("country"),
            SchemaExpr.Literal[DynamicValue, String]("USA", Schema.string)
          )
          .buildPartial

        val v1     = PersonV1("John", "Doe", 30)
        val result = migration(v1)

        assertTrue(
          result.isRight,
          result.map(_.fullName) == Right("John Doe"),
          result.map(_.age) == Right(30),
          result.map(_.country) == Right("USA")
        )
      },
      test("reverse migration creates reversed actions") {
        val forwardMigration = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(
            DynamicOptic.root.field("country"),
            SchemaExpr.Literal[DynamicValue, String]("USA", Schema.string)
          )
          .renameField(
            DynamicOptic.root.field("firstName"),
            "givenName"
          )
          .buildPartial

        val reverseMigration = forwardMigration.reverse

        // Reverse should have same number of actions in reverse order
        assertTrue(
          reverseMigration.dynamicMigration.actions.length == 2
        )
      }
    ),
    suite("Macro-Based Selector API")(
      suite("Record Operations with Selectors")(
        test("addField with selector (must use DynamicOptic for new fields)") {
          // Note: Selector syntax only works for fields that exist at compile time.
          // For adding NEW fields, we must use DynamicOptic.root.field("fieldName")
          val migration = MigrationBuilder
            .newBuilder[PersonV1, PersonV1]
            .addField(
              DynamicOptic.root.field("country"), // Adding a new field that doesn't exist
              SchemaExpr.Literal[DynamicValue, String]("USA", Schema.string)
            )
            .buildPartial

          val v1     = PersonV1("John", "Doe", 30)
          val result = migration(v1)

          assertTrue(result.isRight)
        },
        test("dropField with selector") {
          val migration = MigrationBuilder
            .newBuilder[PersonV1, PersonV1]
            .dropField(
              (_: PersonV1).age, // macro extracts field name
              SchemaExpr.Literal[DynamicValue, Int](0, Schema.int)
            )
            .buildPartial

          val v1           = PersonV1("John", "Doe", 30)
          val dynamicValue = personV1Schema.toDynamicValue(v1)
          val result       = migration.dynamicMigration.apply(dynamicValue)

          assertTrue(result.isRight)
        },
        test("renameField with selectors (from and to)") {
          val migration = MigrationBuilder
            .newBuilder[PersonV1, PersonV2]
            .renameField(
              (_: PersonV1).firstName, // source field selector
              (_: PersonV2).fullName   // target field selector
            )
            .buildPartial

          val v1           = PersonV1("John", "Doe", 30)
          val dynamicValue = personV1Schema.toDynamicValue(v1)
          val result       = migration.dynamicMigration.apply(dynamicValue)

          assertTrue(result.isRight)
        },
        test("transformField with selector") {
          val migration = MigrationBuilder
            .newBuilder[PersonV1, PersonV1]
            .transformField(
              (_: PersonV1).age, // macro extracts field name
              SchemaExpr.Arithmetic[DynamicValue, Int](
                SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
                SchemaExpr.Literal[DynamicValue, Int](1, Schema.int),
                SchemaExpr.ArithmeticOperator.Add,
                IsNumeric.IsInt
              )
            )
            .buildPartial

          val v1     = PersonV1("John", "Doe", 30)
          val result = migration(v1)

          assertTrue(result.isRight)
        },
        test("mandateField with selector") {
          val migration = MigrationBuilder
            .newBuilder[WithOption, WithoutOption]
            .mandateField(
              (_: WithOption).name, // macro extracts field name
              SchemaExpr.Literal[DynamicValue, String]("Unknown", Schema.string)
            )
            .buildPartial

          val v1     = WithOption(Some("John"), 30)
          val result = migration(v1)

          assertTrue(result.isRight)
        },
        test("optionalizeField with selector") {
          val migration = MigrationBuilder
            .newBuilder[WithoutOption, WithOption]
            .optionalizeField(
              (_: WithoutOption).name, // macro extracts field name
              SchemaExpr.Literal[DynamicValue, String]("", Schema.string)
            )
            .buildPartial

          val v1     = WithoutOption("John", 30)
          val result = migration(v1)

          assertTrue(result.isRight)
        },
        test("optionalizeField with selector - reverse with Some") {
          val migration = MigrationBuilder
            .newBuilder[WithoutOption, WithOption]
            .optionalizeField(
              (_: WithoutOption).name,
              SchemaExpr.Literal[DynamicValue, String]("FallbackName", Schema.string)
            )
            .buildPartial

          val reverseMigration = migration.reverse
          val v2               = WithOption(Some("TestName"), 42)
          val result           = reverseMigration(v2)

          assertTrue(
            result.isRight,
            result.map(v => v.name == "TestName" && v.age == 42).getOrElse(false)
          )
        },
        test("optionalizeField with selector - reverse with None uses default") {
          val migration = MigrationBuilder
            .newBuilder[WithoutOption, WithOption]
            .optionalizeField(
              (_: WithoutOption).name,
              SchemaExpr.Literal[DynamicValue, String]("FallbackName", Schema.string)
            )
            .buildPartial

          val reverseMigration = migration.reverse
          val v2               = WithOption(None, 99)
          val result           = reverseMigration(v2)

          assertTrue(
            result.isRight,
            result.map(v => v.name == "FallbackName" && v.age == 99).getOrElse(false)
          )
        },
        test("changeFieldType with selector") {
          val migration = MigrationBuilder
            .newBuilder[WithString, WithInt]
            .changeFieldType(
              (_: WithString).age, // macro extracts field name
              PrimitiveConverter.StringToInt
            )
            .buildPartial

          val v1     = WithString("30")
          val result = migration(v1)

          assertTrue(result.isRight)
        }
      ),
      // Note - Think about vector vs seq problem
      suite("Multi-Field Operations with Selectors")(
        test("joinFields with source selectors") {
          val migration = MigrationBuilder
            .newBuilder[PersonV1, PersonV2]
            .joinFields(
              (_: PersonV2).fullName, // target selector
              Vector(
                (_: PersonV1).firstName, // source selectors
                (_: PersonV1).lastName
              ): Seq[PersonV1 => Any],
              SchemaExpr.StringConcat[DynamicValue](
                SchemaExpr.StringConcat[DynamicValue](
                  SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root.field("field0")),
                  SchemaExpr.Literal[DynamicValue, String](" ", Schema.string)
                ),
                SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root.field("field1"))
              )
            )
            .buildPartial

          val v1           = PersonV1("John", "Doe", 30)
          val dynamicValue = personV1Schema.toDynamicValue(v1)
          val result       = migration.dynamicMigration.apply(dynamicValue)

          assertTrue(result.isRight)
        },
        test("splitField with source selector and target selectors") {
          val migration = MigrationBuilder
            .newBuilder[PersonV2, PersonV1]
            .splitField(
              (_: PersonV2).fullName, // source selector
              Seq(
                (_: PersonV1).firstName, // target selectors
                (_: PersonV1).lastName
              ),
              SchemaExpr.StringSplit[DynamicValue](
                SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root),
                " "
              )
            )
            .buildPartial

          val v2           = PersonV2("John Doe", 30, "USA")
          val dynamicValue = personV2Schema.toDynamicValue(v2)
          val result       = migration.dynamicMigration.apply(dynamicValue)

          assertTrue(result.isRight)
        }
      ),
      suite("Collection Operations with Selectors")(
        test("transformElements with selector") {
          val migration = MigrationBuilder
            .newBuilder[WithList, WithList]
            .transformElements(
              (_: WithList).items, // macro extracts field name
              SchemaExpr.Arithmetic[DynamicValue, Int](
                SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
                SchemaExpr.Literal[DynamicValue, Int](10, Schema.int),
                SchemaExpr.ArithmeticOperator.Add,
                IsNumeric.IsInt
              )
            )
            .buildPartial

          val v1     = WithList(Vector(1, 2, 3))
          val result = migration(v1)

          assertTrue(result.isRight)
        },
        test("transformKeys with selector") {
          val migration = MigrationBuilder
            .newBuilder[WithMap, WithMap]
            .transformKeys(
              (_: WithMap).data, // macro extracts field name
              SchemaExpr.StringUppercase[DynamicValue](
                SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
              )
            )
            .buildPartial

          val v1     = WithMap(Map("foo" -> 1, "bar" -> 2))
          val result = migration(v1)

          assertTrue(result.isRight)
        },
        test("transformValues with selector") {
          val migration = MigrationBuilder
            .newBuilder[WithMap, WithMap]
            .transformValues(
              (_: WithMap).data, // macro extracts field name
              SchemaExpr.Arithmetic[DynamicValue, Int](
                SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
                SchemaExpr.Literal[DynamicValue, Int](100, Schema.int),
                SchemaExpr.ArithmeticOperator.Add,
                IsNumeric.IsInt
              )
            )
            .buildPartial

          val v1     = WithMap(Map("foo" -> 1, "bar" -> 2))
          val result = migration(v1)

          assertTrue(result.isRight)
        }
      ),
      suite("Fluent API with Mixed Selectors and Manual DynamicOptics")(
        test("chain operations using both selector and manual APIs") {
          val migration = MigrationBuilder
            .newBuilder[PersonV1, PersonV1]
            .addField(
              DynamicOptic.root.field("extra"), // using manual optic for new field
              SchemaExpr.Literal[DynamicValue, String]("default", Schema.string)
            )
            .renameField(
              DynamicOptic.root.field("lastName"), // using manual optic
              "surname"
            )
            .transformField(
              (_: PersonV1).age, // using selector for existing field
              SchemaExpr.Arithmetic[DynamicValue, Int](
                SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
                SchemaExpr.Literal[DynamicValue, Int](1, Schema.int),
                SchemaExpr.ArithmeticOperator.Add,
                IsNumeric.IsInt
              )
            )
            .buildPartial

          val v1           = PersonV1("John", "Doe", 30)
          val dynamicValue = personV1Schema.toDynamicValue(v1)
          val result       = migration.dynamicMigration.apply(dynamicValue)

          assertTrue(result.isRight)
        }
      ),
      suite("Nested Field Selectors")(
        test("nested field access - placeholder for future") {
          // Note: This test is a placeholder. Nested selectors like _.address.street
          // will be implemented when nested field support is added.
          // For now, we only support top-level field selectors.
          assertTrue(true)
        }
      )
    )
  )
}
