package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
object MigrationBuilderSpec extends ZIOSpecDefault {

  // Test data structures
  case class PersonV1(firstName: String, lastName: String, age: Int)
  case class PersonV2(fullName: String, age: Int, country: String)

  case class WithOption(name: Option[String], age: Int)
  case class WithoutOption(name: String, age: Int)

  case class WithString(age: String)
  case class WithInt(age: Int)

  case class WithList(items: Vector[Int])
  object WithList extends CompanionOptics[WithList]

  case class WithMap(data: Map[String, Int])
  object WithMap extends CompanionOptics[WithMap]

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
      test("buildPartial empty migration for identical schemas") {
        // Use same schema for source and target - no actions needed
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .build

        assertTrue(
          migration.sourceSchema != null,
          migration.targetSchema != null,
          migration.dynamicMigration.actions.isEmpty
        )
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
          .withAction(
            MigrationAction.AddField(
              DynamicOptic.root.field("extra"),
              "default"
            )
          )
          .build

        val v1     = PersonV1("John", "Doe", 30)
        val result = migration(v1)

        assertTrue(result == Right(PersonV1("John", "Doe", 30)))
      },
      test("dropField - remove field") {
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .withAction(
            MigrationAction.DropField(
              DynamicOptic.root.field("age"),
              0
            )
          )
          .build

        val v1 = PersonV1("John", "Doe", 30)
        // Test at DynamicValue level since structure changed
        val dynamicValue = personV1Schema.toDynamicValue(v1)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(
          result.isRight,
          result.exists(_.get("age").isEmpty),
          result.exists(_.get("firstName").one == Right(DynamicValue.string("John"))),
          result.exists(_.get("lastName").one == Right(DynamicValue.string("Doe")))
        )
      },
      test("renameField - rename single field") {
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .withAction(
            MigrationAction.Rename(
              DynamicOptic.root.field("firstName"),
              "givenName"
            )
          )
          .build

        val v1 = PersonV1("John", "Doe", 30)
        // Test at DynamicValue level since structure changed
        val dynamicValue = personV1Schema.toDynamicValue(v1)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(
          result.isRight,
          result.exists(_.get("givenName").one == Right(DynamicValue.string("John"))),
          result.exists(_.get("firstName").isEmpty)
        )
      },
      test("transformField - increment age") {
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .withAction(
            MigrationAction.TransformValue(
              DynamicOptic.root.field("age"),
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                1,
                DynamicSchemaExpr.ArithmeticOperator.Add,
                DynamicSchemaExpr.NumericType.IntType
              )
            )
          )
          .build

        val v1     = PersonV1("John", "Doe", 30)
        val result = migration(v1)

        assertTrue(result == Right(PersonV1("John", "Doe", 31)))
      },
      test("mandateField - unwrap Option with default") {
        val migration = MigrationBuilder
          .newBuilder[WithOption, WithoutOption]
          .mandateField(
            (_: WithOption).name,
            "Unknown"
          )
          .build

        val v1     = WithOption(Some("John"), 30)
        val result = migration(v1)

        assertTrue(result == Right(WithoutOption("John", 30)))
      },
      test("optionalizeField - wrap field in Option") {
        val migration = MigrationBuilder
          .newBuilder[WithoutOption, WithOption]
          .optionalizeField(
            (_: WithoutOption).name,
            ""
          )
          .build

        val v1     = WithoutOption("John", 30)
        val result = migration(v1)

        assertTrue(result == Right(WithOption(Some("John"), 30)))
      },
      test("optionalizeField reverse - unwrap Some successfully") {
        val migration = MigrationBuilder
          .newBuilder[WithoutOption, WithOption]
          .optionalizeField(
            (_: WithoutOption).name,
            "DefaultName"
          )
          .build

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
            (_: WithoutOption).name,
            "DefaultName"
          )
          .build

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
            (_: WithoutOption).name,
            ""
          )
          .build

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
            (_: WithString).age,
            PrimitiveConverter.StringToInt
          )
          .build

        val v1     = WithString("30")
        val result = migration(v1)

        assertTrue(result == Right(WithInt(30)))
      }
    ),
    suite("Multi-Field Operations")(
      test("joinFields - combine firstName and lastName") {
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .withAction(
            MigrationAction.Join(
              DynamicOptic.root.field("fullName"),
              Vector(
                DynamicOptic.root.field("firstName"),
                DynamicOptic.root.field("lastName")
              ),
              DynamicSchemaExpr.StringConcat(
                DynamicSchemaExpr.StringConcat(
                  DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
                  " "
                ),
                DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
              )
            )
          )
          .build

        val v1 = PersonV1("John", "Doe", 30)
        // Test at DynamicValue level since structure changed
        val dynamicValue = personV1Schema.toDynamicValue(v1)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(
          result.isRight,
          result.exists(_.get("fullName").one == Right(DynamicValue.string("John Doe")))
        )
      },
      test("splitField - split fullName into firstName and lastName") {
        val migration = MigrationBuilder
          .newBuilder[PersonV2, PersonV2]
          .withAction(
            MigrationAction.Split(
              DynamicOptic.root.field("fullName"),
              Vector(
                DynamicOptic.root.field("firstName"),
                DynamicOptic.root.field("lastName")
              ),
              DynamicSchemaExpr.StringSplit(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                " "
              )
            )
          )
          .build

        val v2 = PersonV2("John Doe", 30, "USA")
        // Test at DynamicValue level since structure changed
        val dynamicValue = personV2Schema.toDynamicValue(v2)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(
          result.isRight,
          result.exists(_.get("firstName").one == Right(DynamicValue.string("John"))),
          result.exists(_.get("lastName").one == Right(DynamicValue.string("Doe")))
        )
      }
    ),
    suite("Collection Operations")(
      test("transformElements - add 10 to each element") {
        val migration = MigrationBuilder
          .newBuilder[WithList, WithList]
          .withAction(
            MigrationAction.TransformElements(
              DynamicOptic.root.field("items"),
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                10,
                DynamicSchemaExpr.ArithmeticOperator.Add,
                DynamicSchemaExpr.NumericType.IntType
              )
            )
          )
          .build

        val v1     = WithList(Vector(1, 2, 3))
        val result = migration(v1)

        assertTrue(result == Right(WithList(Vector(11, 12, 13))))
      },
      test("transformKeys - uppercase all keys") {
        val migration = MigrationBuilder
          .newBuilder[WithMap, WithMap]
          .withAction(
            MigrationAction.TransformKeys(
              DynamicOptic.root.field("data"),
              DynamicSchemaExpr.StringUppercase(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root)
              )
            )
          )
          .build

        val v1     = WithMap(Map("foo" -> 1, "bar" -> 2))
        val result = migration(v1)

        assertTrue(result == Right(WithMap(Map("FOO" -> 1, "BAR" -> 2))))
      },
      test("transformValues - add 100 to each value") {
        val migration = MigrationBuilder
          .newBuilder[WithMap, WithMap]
          .withAction(
            MigrationAction.TransformValues(
              DynamicOptic.root.field("data"),
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                100,
                DynamicSchemaExpr.ArithmeticOperator.Add,
                DynamicSchemaExpr.NumericType.IntType
              )
            )
          )
          .build

        val v1     = WithMap(Map("foo" -> 1, "bar" -> 2))
        val result = migration(v1)

        assertTrue(result == Right(WithMap(Map("foo" -> 101, "bar" -> 102))))
      }
    ),
    suite("Enum Operations")(
      test("renameCase - rename PayPal case") {
        val migration = MigrationBuilder
          .newBuilder[PaymentMethod, PaymentMethod]
          .withAction(
            MigrationAction.RenameCase(
              DynamicOptic.root,
              "PayPal",
              "PaypalPayment"
            )
          )
          .build

        val payment = PayPal("test@example.com")
        // Test at DynamicValue level since case name changed
        val dynamicValue = paymentSchema.toDynamicValue(payment)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(
          result.isRight,
          result.exists(_.caseName == Some("PaypalPayment")),
          result.exists(_.caseValue.flatMap(_.get("email").one.toOption).isDefined)
        )
      },
      test("transformCase - rename field in CreditCard case") {
        val migration = MigrationBuilder
          .newBuilder[PaymentMethod, PaymentMethod]
          .withAction(
            MigrationAction.TransformCase(
              DynamicOptic.root,
              "CreditCard",
              Vector(
                MigrationAction.Rename(
                  DynamicOptic.root.field("number"),
                  "cardNumber"
                )
              )
            )
          )
          .build

        val payment = CreditCard("1234-5678-9012-3456", "123")
        // Test at DynamicValue level since field name changed
        val dynamicValue = paymentSchema.toDynamicValue(payment)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(
          result.isRight,
          result.exists(_.caseName == Some("CreditCard")),
          result.exists(_.caseValue.flatMap(_.get("cardNumber").one.toOption).isDefined),
          result.exists(_.caseValue.flatMap(_.get("number").one.toOption).isEmpty)
        )
      }
    ),
    suite("Enum Selector Operations")(
      test("renameCase with selector - rename PayPal case using .when[PayPal]") {
        val migration = MigrationBuilder
          .newBuilder[PaymentMethod, PaymentMethod]
          .renameCase((p: PaymentMethod) => p.when[PayPal], "PaypalPayment")
          .build

        val payment = PayPal("test@example.com")
        // Test at DynamicValue level since case name changed
        val dynamicValue = paymentSchema.toDynamicValue(payment)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(
          result.isRight,
          result.exists(_.caseName == Some("PaypalPayment")),
          result.exists(_.caseValue.flatMap(_.get("email").one.toOption).isDefined)
        )
      },
      test("transformCase with selector - rename field in CreditCard case using .when[CreditCard]") {
        val migration = MigrationBuilder
          .newBuilder[PaymentMethod, PaymentMethod]
          .transformCase((p: PaymentMethod) => p.when[CreditCard])(
            _.withAction(MigrationAction.Rename(DynamicOptic.root.field("number"), "cardNumber"))
          )
          .build

        val payment = CreditCard("1234-5678-9012-3456", "123")
        // Test at DynamicValue level since field name changed
        val dynamicValue = paymentSchema.toDynamicValue(payment)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(
          result.isRight,
          result.exists(_.caseName == Some("CreditCard")),
          result.exists(_.caseValue.flatMap(_.get("cardNumber").one.toOption).isDefined),
          result.exists(_.caseValue.flatMap(_.get("number").one.toOption).isEmpty)
        )
      },
      test("transformCase with selector - verify correct action generated") {
        val builder = MigrationBuilder
          .newBuilder[PaymentMethod, PaymentMethod]
          .transformCase((p: PaymentMethod) => p.when[CreditCard])(
            _.withAction(MigrationAction.Rename(DynamicOptic.root.field("number"), "cardNumber"))
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
          .withAction(
            MigrationAction.AddField(
              DynamicOptic.root.field("country"),
              "USA"
            )
          )
          .withAction(
            MigrationAction.Rename(
              DynamicOptic.root.field("firstName"),
              "givenName"
            )
          )
          .withAction(
            MigrationAction.DropField(
              DynamicOptic.root.field("lastName"),
              ""
            )
          )
          .build

        val v1 = PersonV1("John", "Doe", 30)
        // Test at DynamicValue level since structure changed
        val dynamicValue = personV1Schema.toDynamicValue(v1)
        val result       = migration.dynamicMigration.apply(dynamicValue)

        assertTrue(
          result.isRight,
          result.exists(_.get("country").one == Right(DynamicValue.string("USA"))),
          result.exists(_.get("givenName").one == Right(DynamicValue.string("John"))),
          result.exists(_.get("lastName").isEmpty)
        )
      },
      test("verify immutability - builder returns new instance") {
        val builder1 = MigrationBuilder.newBuilder[PersonV1, PersonV2]
        val builder2 = builder1.withAction(
          MigrationAction.AddField(
            DynamicOptic.root.field("country"),
            DynamicSchemaExpr.Literal(
              DynamicValue.Primitive(PrimitiveValue.String("USA"))
            ) // Intentionally kept in old notation
          )
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
          .withAction(
            MigrationAction.AddField(
              DynamicOptic.root.field("extra"),
              "test"
            )
          )
          .withAction(
            MigrationAction.Rename(
              DynamicOptic.root.field("firstName"),
              "givenName"
            )
          )
          .withAction(
            MigrationAction.DropField(
              DynamicOptic.root.field("lastName"),
              ""
            )
          )

        assertTrue(builder.actions.length == 3)
      }
    ),
    suite("Build Methods")(
      test("buildPartial bypasses compile-time validation") {
        // buildPartial allows creating migrations without compile-time checks
        // This is useful for dynamic migrations or when validation is done elsewhere
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .withAction(
            MigrationAction.AddField(
              DynamicOptic.root.field("extra"),
              "test"
            )
          )
          .buildPartial

        assertTrue(
          migration.dynamicMigration.actions.length == 1,
          migration.sourceSchema == personV1Schema,
          migration.targetSchema == personV1Schema
        )
      },
      test("buildPartial works for incomplete migrations") {
        // Can create partial migrations that would fail compile-time validation
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .withAction(
            MigrationAction.Rename(
              DynamicOptic.root.field("firstName"),
              "fullName"
            )
          )
          .buildPartial

        // Only one action, but migration is incomplete (missing country, lastName handling)
        assertTrue(
          migration.dynamicMigration.actions.length == 1,
          migration.dynamicMigration.actions.head.isInstanceOf[MigrationAction.Rename]
        )
      }
    ),
    suite("Integration Tests")(
      test("complete migration PersonV1 to PersonV2") {
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .joinFields(
            (_: PersonV2).fullName,
            Seq(
              (_: PersonV1).firstName,
              (_: PersonV1).lastName
            ),
            DynamicSchemaExpr.StringConcat(
              DynamicSchemaExpr.StringConcat(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
                " "
              ),
              DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
            )
          )
          .addField(
            (_: PersonV2).country,
            "USA"
          )
          .build

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
          .withAction(
            MigrationAction.AddField(
              DynamicOptic.root.field("country"),
              "USA"
            )
          )
          .withAction(
            MigrationAction.Rename(
              DynamicOptic.root.field("firstName"),
              "givenName"
            )
          )
          .build

        val reverseMigration = forwardMigration.reverse

        // Reverse should have same number of actions in reverse order
        assertTrue(reverseMigration.dynamicMigration.actions.length == 2)

        // Verify reverse actually works: apply forward then reverse
        val v1           = PersonV1("John", "Doe", 30)
        val dynamicV1    = personV1Schema.toDynamicValue(v1)
        val forwarded    = forwardMigration.dynamicMigration.apply(dynamicV1)
        val roundTripped = forwarded.flatMap(reverseMigration.dynamicMigration.apply)
        assertTrue(
          roundTripped.isRight,
          roundTripped.exists(_.get("firstName").one == Right(DynamicValue.string("John")))
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
            .withAction(
              MigrationAction.AddField(
                DynamicOptic.root.field("country"), // Adding a new field that doesn't exist
                DynamicSchemaExpr.Literal(
                  DynamicValue.Primitive(PrimitiveValue.String("USA"))
                ) // Intentionally kept in old notation
              )
            )
            .build

          val v1     = PersonV1("John", "Doe", 30)
          val result = migration(v1)

          assertTrue(result == Right(PersonV1("John", "Doe", 30)))
        },
        test("dropField with selector") {
          val migration = MigrationBuilder
            .newBuilder[PersonV1, PersonV1]
            .dropField(
              (_: PersonV1).age, // macro extracts field name
              0
            )
            .build

          val v1           = PersonV1("John", "Doe", 30)
          val dynamicValue = personV1Schema.toDynamicValue(v1)
          val result       = migration.dynamicMigration.apply(dynamicValue)

          assertTrue(
            result.isRight,
            result.exists(_.get("age").isEmpty),
            result.exists(_.get("firstName").one == Right(DynamicValue.string("John"))),
            result.exists(_.get("lastName").one == Right(DynamicValue.string("Doe")))
          )
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

          assertTrue(
            result.isRight,
            result.exists(_.get("fullName").one == Right(DynamicValue.string("John"))),
            result.exists(_.get("firstName").isEmpty)
          )
        },
        test("transformField with selector") {
          val migration = MigrationBuilder
            .newBuilder[PersonV1, PersonV1]
            .transformField(
              (_: PersonV1).age, // macro extracts field name
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                1,
                DynamicSchemaExpr.ArithmeticOperator.Add,
                DynamicSchemaExpr.NumericType.IntType
              )
            )
            .build

          val v1     = PersonV1("John", "Doe", 30)
          val result = migration(v1)

          assertTrue(result == Right(PersonV1("John", "Doe", 31)))
        },
        test("mandateField with selector") {
          val migration = MigrationBuilder
            .newBuilder[WithOption, WithoutOption]
            .mandateField(
              (_: WithOption).name, // macro extracts field name
              "Unknown"
            )
            .build

          val v1     = WithOption(Some("John"), 30)
          val result = migration(v1)

          assertTrue(result == Right(WithoutOption("John", 30)))
        },
        test("optionalizeField with selector") {
          val migration = MigrationBuilder
            .newBuilder[WithoutOption, WithOption]
            .optionalizeField(
              (_: WithoutOption).name, // macro extracts field name
              ""
            )
            .build

          val v1     = WithoutOption("John", 30)
          val result = migration(v1)

          assertTrue(result == Right(WithOption(Some("John"), 30)))
        },
        test("optionalizeField with selector - reverse with Some") {
          val migration = MigrationBuilder
            .newBuilder[WithoutOption, WithOption]
            .optionalizeField(
              (_: WithoutOption).name,
              "FallbackName"
            )
            .build

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
              "FallbackName"
            )
            .build

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
            .build

          val v1     = WithString("30")
          val result = migration(v1)

          assertTrue(result == Right(WithInt(30)))
        }
      ),
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
              DynamicSchemaExpr.StringConcat(
                DynamicSchemaExpr.StringConcat(
                  DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
                  " "
                ),
                DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
              )
            )
            .buildPartial

          val v1           = PersonV1("John", "Doe", 30)
          val dynamicValue = personV1Schema.toDynamicValue(v1)
          val result       = migration.dynamicMigration.apply(dynamicValue)

          assertTrue(
            result.isRight,
            result.exists(_.get("fullName").one == Right(DynamicValue.string("John Doe")))
          )
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
              DynamicSchemaExpr.StringSplit(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                " "
              )
            )
            .buildPartial

          val v2           = PersonV2("John Doe", 30, "USA")
          val dynamicValue = personV2Schema.toDynamicValue(v2)
          val result       = migration.dynamicMigration.apply(dynamicValue)

          assertTrue(
            result.isRight,
            result.exists(_.get("firstName").one == Right(DynamicValue.string("John"))),
            result.exists(_.get("lastName").one == Right(DynamicValue.string("Doe")))
          )
        }
      ),
      suite("Collection Operations with Selectors")(
        test("transformElements with .each selector") {
          import WithList._
          val migration = MigrationBuilder
            .newBuilder[WithList, WithList]
            .transformElements(
              (_: WithList).items.each,
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                10,
                DynamicSchemaExpr.ArithmeticOperator.Add,
                DynamicSchemaExpr.NumericType.IntType
              )
            )
            .build

          val v1     = WithList(Vector(1, 2, 3))
          val result = migration(v1)

          assertTrue(result == Right(WithList(Vector(11, 12, 13))))
        },
        test("transformKeys with .eachKey selector") {
          import WithMap._
          val migration = MigrationBuilder
            .newBuilder[WithMap, WithMap]
            .transformKeys(
              (_: WithMap).data.eachKey,
              DynamicSchemaExpr.StringUppercase(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root)
              )
            )
            .build

          val v1     = WithMap(Map("foo" -> 1, "bar" -> 2))
          val result = migration(v1)

          assertTrue(result == Right(WithMap(Map("FOO" -> 1, "BAR" -> 2))))
        },
        test("transformValues with .eachValue selector") {
          import WithMap._
          val migration = MigrationBuilder
            .newBuilder[WithMap, WithMap]
            .transformValues(
              (_: WithMap).data.eachValue,
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                100,
                DynamicSchemaExpr.ArithmeticOperator.Add,
                DynamicSchemaExpr.NumericType.IntType
              )
            )
            .build

          val v1     = WithMap(Map("foo" -> 1, "bar" -> 2))
          val result = migration(v1)

          assertTrue(result == Right(WithMap(Map("foo" -> 101, "bar" -> 102))))
        },
        test("transformElements with selector") {
          val migration = MigrationBuilder
            .newBuilder[WithList, WithList]
            .transformElements(
              (_: WithList).items, // macro extracts field name
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                10,
                DynamicSchemaExpr.ArithmeticOperator.Add,
                DynamicSchemaExpr.NumericType.IntType
              )
            )
            .build

          val v1     = WithList(Vector(1, 2, 3))
          val result = migration(v1)

          assertTrue(result == Right(WithList(Vector(11, 12, 13))))
        },
        test("transformKeys with selector") {
          val migration = MigrationBuilder
            .newBuilder[WithMap, WithMap]
            .transformKeys(
              (_: WithMap).data, // macro extracts field name
              DynamicSchemaExpr.StringUppercase(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root)
              )
            )
            .build

          val v1     = WithMap(Map("foo" -> 1, "bar" -> 2))
          val result = migration(v1)

          assertTrue(result == Right(WithMap(Map("FOO" -> 1, "BAR" -> 2))))
        },
        test("transformValues with selector") {
          val migration = MigrationBuilder
            .newBuilder[WithMap, WithMap]
            .transformValues(
              (_: WithMap).data, // macro extracts field name
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                100,
                DynamicSchemaExpr.ArithmeticOperator.Add,
                DynamicSchemaExpr.NumericType.IntType
              )
            )
            .build

          val v1     = WithMap(Map("foo" -> 1, "bar" -> 2))
          val result = migration(v1)

          assertTrue(result == Right(WithMap(Map("foo" -> 101, "bar" -> 102))))
        }
      ),
      suite("Fluent API with Mixed Selectors and Manual DynamicOptics")(
        test("chain operations using both selector and manual APIs") {
          val migration = MigrationBuilder
            .newBuilder[PersonV1, PersonV1]
            .withAction(
              MigrationAction.AddField(
                DynamicOptic.root.field("extra"), // using manual optic for new field
                "default"
              )
            )
            .withAction(
              MigrationAction.Rename(
                DynamicOptic.root.field("lastName"), // using manual optic
                "surname"
              )
            )
            .transformField(
              (_: PersonV1).age, // using selector for existing field
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                1,
                DynamicSchemaExpr.ArithmeticOperator.Add,
                DynamicSchemaExpr.NumericType.IntType
              )
            )
            .build

          val v1           = PersonV1("John", "Doe", 30)
          val dynamicValue = personV1Schema.toDynamicValue(v1)
          val result       = migration.dynamicMigration.apply(dynamicValue)

          assertTrue(
            result.isRight,
            result.exists(_.get("extra").one == Right(DynamicValue.string("default"))),
            result.exists(_.get("surname").one == Right(DynamicValue.string("Doe"))),
            result.exists(_.get("lastName").isEmpty),
            result.exists(_.get("age").one == Right(DynamicValue.int(31)))
          )
        }
      ),
      suite("Nested Field Selectors")(
        test("2-level: addField to nested record") {
          // Add _.address.country with default "USA"
          case class Address(street: String, city: String, zip: String)
          case class PersonWithAddress(name: String, age: Int, address: Address)

          implicit val addressSchema: Schema[Address]                     = Schema.derived[Address]
          implicit val personWithAddressSchema: Schema[PersonWithAddress] = Schema.derived[PersonWithAddress]

          val person    = PersonWithAddress("Alice", 30, Address("123 Main St", "NYC", "10001"))
          val migration = MigrationBuilder
            .newBuilder[PersonWithAddress, PersonWithAddress]
            .withAction(
              MigrationAction.AddField(
                DynamicOptic.root.field("address").field("country"),
                "USA"
              )
            )
            .build

          val result = migration(person)

          // Typed result preserves all original fields
          assertTrue(result == Right(person))
          // Also verify the added field exists at dynamic level
          val dynamicResult = migration.dynamicMigration.apply(personWithAddressSchema.toDynamicValue(person))
          assertTrue(
            dynamicResult.exists(
              _.get("address").one.flatMap(_.get("country").one) == Right(DynamicValue.string("USA"))
            )
          )
        },
        test("2-level: dropField from nested record") {
          // Drop _.address.zip
          case class Address(street: String, city: String, zip: String)
          case class PersonWithAddress(name: String, age: Int, address: Address)

          implicit val addressSchema: Schema[Address]                     = Schema.derived[Address]
          implicit val personWithAddressSchema: Schema[PersonWithAddress] = Schema.derived[PersonWithAddress]

          val person        = PersonWithAddress("Alice", 30, Address("123 Main St", "NYC", "10001"))
          val personDynamic = personWithAddressSchema.toDynamicValue(person)

          val migration = DynamicMigration(
            Vector(
              MigrationAction.DropField(
                DynamicOptic.root.field("address").field("zip"),
                DynamicSchemaExpr.Literal(
                  DynamicValue.Primitive(PrimitiveValue.String("00000"))
                ) // Intentionally kept in old notation
              )
            )
          )

          val result = migration(personDynamic)

          assertTrue(
            result.isRight,
            result.exists(_.get("address").one.flatMap(_.get("zip").one).isLeft),
            result.exists(
              _.get("address").one.flatMap(_.get("street").one) == Right(DynamicValue.string("123 Main St"))
            ),
            result.exists(_.get("address").one.flatMap(_.get("city").one) == Right(DynamicValue.string("NYC")))
          )
        },
        test("2-level: rename nested field") {
          // Rename _.address.street to _.address.streetName
          case class Address(street: String, city: String, zip: String)
          case class PersonWithAddress(name: String, age: Int, address: Address)

          implicit val addressSchema: Schema[Address]                     = Schema.derived[Address]
          implicit val personWithAddressSchema: Schema[PersonWithAddress] = Schema.derived[PersonWithAddress]

          val person        = PersonWithAddress("Alice", 30, Address("123 Main St", "NYC", "10001"))
          val personDynamic = personWithAddressSchema.toDynamicValue(person)

          val migration = DynamicMigration(
            Vector(
              MigrationAction.Rename(
                DynamicOptic.root.field("address").field("street"),
                "streetName"
              )
            )
          )

          val result = migration(personDynamic)

          assertTrue(
            result.isRight,
            result.exists(
              _.get("address").one.flatMap(_.get("streetName").one) == Right(DynamicValue.string("123 Main St"))
            ),
            result.exists(_.get("address").one.flatMap(_.get("street").one).isLeft)
          )
        },
        test("2-level: transformField on nested field") {
          // Transform _.address.city to uppercase
          case class Address(street: String, city: String, zip: String)
          case class PersonWithAddress(name: String, age: Int, address: Address)

          implicit val addressSchema: Schema[Address]                     = Schema.derived[Address]
          implicit val personWithAddressSchema: Schema[PersonWithAddress] = Schema.derived[PersonWithAddress]

          val person    = PersonWithAddress("Alice", 30, Address("123 Main St", "nyc", "10001"))
          val migration = MigrationBuilder
            .newBuilder[PersonWithAddress, PersonWithAddress]
            .withAction(
              MigrationAction.TransformValue(
                DynamicOptic.root.field("address").field("city"),
                DynamicSchemaExpr.StringUppercase(
                  DynamicSchemaExpr.Dynamic(DynamicOptic.root)
                )
              )
            )
            .build

          val result = migration(person)

          assertTrue(result == Right(PersonWithAddress("Alice", 30, Address("123 Main St", "NYC", "10001"))))
        },
        test("3-level: addField to deeply nested record") {
          // Add _.company.address.country with default "USA"
          case class Address(street: String, city: String, zip: String)
          case class Company(name: String, address: Address)
          case class Employee(name: String, company: Company)

          implicit val addressSchema: Schema[Address]   = Schema.derived[Address]
          implicit val companySchema: Schema[Company]   = Schema.derived[Company]
          implicit val employeeSchema: Schema[Employee] = Schema.derived[Employee]

          val employee  = Employee("Bob", Company("Acme Inc", Address("456 Oak Ave", "LA", "90001")))
          val migration = MigrationBuilder
            .newBuilder[Employee, Employee]
            .withAction(
              MigrationAction.AddField(
                DynamicOptic.root.field("company").field("address").field("country"),
                "USA"
              )
            )
            .build

          val result = migration(employee)

          assertTrue(result == Right(employee))
          // Verify the added field at dynamic level
          val dynamicResult = migration.dynamicMigration.apply(employeeSchema.toDynamicValue(employee))
          assertTrue(
            dynamicResult.exists(
              _.get("company").one
                .flatMap(_.get("address").one)
                .flatMap(_.get("country").one) == Right(DynamicValue.string("USA"))
            )
          )
        },
        test("3-level: transformField on deeply nested field") {
          // Transform _.company.address.city to uppercase
          case class Address(street: String, city: String, zip: String)
          case class Company(name: String, address: Address)
          case class Employee(name: String, company: Company)

          implicit val addressSchema: Schema[Address]   = Schema.derived[Address]
          implicit val companySchema: Schema[Company]   = Schema.derived[Company]
          implicit val employeeSchema: Schema[Employee] = Schema.derived[Employee]

          val employee  = Employee("Bob", Company("Acme Inc", Address("456 Oak Ave", "la", "90001")))
          val migration = MigrationBuilder
            .newBuilder[Employee, Employee]
            .withAction(
              MigrationAction.TransformValue(
                DynamicOptic.root.field("company").field("address").field("city"),
                DynamicSchemaExpr.StringUppercase(
                  DynamicSchemaExpr.Dynamic(DynamicOptic.root)
                )
              )
            )
            .build

          val result = migration(employee)

          assertTrue(result == Right(Employee("Bob", Company("Acme Inc", Address("456 Oak Ave", "LA", "90001")))))
        },
        test("2-level: join nested fields") {
          // Join _.address.street + _.address.city -> _.address.fullAddress
          case class Address(street: String, city: String, zip: String)
          case class PersonWithAddress(name: String, age: Int, address: Address)

          implicit val addressSchema: Schema[Address]                     = Schema.derived[Address]
          implicit val personWithAddressSchema: Schema[PersonWithAddress] = Schema.derived[PersonWithAddress]

          val person        = PersonWithAddress("Alice", 30, Address("123 Main St", "NYC", "10001"))
          val personDynamic = personWithAddressSchema.toDynamicValue(person)

          val migration = DynamicMigration(
            Vector(
              MigrationAction.Join(
                DynamicOptic.root.field("address").field("fullAddress"),
                Vector(
                  DynamicOptic.root.field("address").field("street"),
                  DynamicOptic.root.field("address").field("city")
                ),
                DynamicSchemaExpr.StringConcat(
                  DynamicSchemaExpr.StringConcat(
                    DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
                    ", "
                  ),
                  DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
                )
              )
            )
          )

          val result = migration(personDynamic)

          assertTrue(
            result.isRight,
            result.exists(
              _.get("address").one.flatMap(_.get("fullAddress").one) == Right(DynamicValue.string("123 Main St, NYC"))
            )
          )
        },
        test("2-level: optionalize nested field") {
          // Optionalize _.address.zip from String to Option[String]
          case class Address(street: String, city: String, zip: String)
          case class PersonWithAddress(name: String, age: Int, address: Address)

          implicit val addressSchema: Schema[Address]                     = Schema.derived[Address]
          implicit val personWithAddressSchema: Schema[PersonWithAddress] = Schema.derived[PersonWithAddress]

          val person        = PersonWithAddress("Alice", 30, Address("123 Main St", "NYC", "10001"))
          val personDynamic = personWithAddressSchema.toDynamicValue(person)

          val migration = DynamicMigration(
            Vector(
              MigrationAction.Optionalize(
                DynamicOptic.root.field("address").field("zip"),
                ""
              )
            )
          )

          val result = migration(personDynamic)

          assertTrue(
            result.isRight,
            result.exists(_.get("address").one.flatMap(_.get("zip").one.map(_.caseName)) == Right(Some("Some")))
          )
        },
        test("error: intermediate field not found") {
          // Try to add _.nonexistent.street
          case class Address(street: String, city: String, zip: String)
          case class PersonWithAddress(name: String, age: Int, address: Address)

          implicit val addressSchema: Schema[Address]                     = Schema.derived[Address]
          implicit val personWithAddressSchema: Schema[PersonWithAddress] = Schema.derived[PersonWithAddress]

          val person    = PersonWithAddress("Alice", 30, Address("123 Main St", "NYC", "10001"))
          val migration = MigrationBuilder
            .newBuilder[PersonWithAddress, PersonWithAddress]
            .withAction(
              MigrationAction.AddField(
                DynamicOptic.root.field("nonexistent").field("street"),
                "default"
              )
            )
            .build

          val result = migration(person)

          assertTrue(result.isLeft) // Should fail with FieldNotFound
        },
        test("error: intermediate field is not a record") {
          // Try to add _.name.street (name is String, not Record)
          case class Address(street: String, city: String, zip: String)
          case class PersonWithAddress(name: String, age: Int, address: Address)

          implicit val addressSchema: Schema[Address]                     = Schema.derived[Address]
          implicit val personWithAddressSchema: Schema[PersonWithAddress] = Schema.derived[PersonWithAddress]

          val person    = PersonWithAddress("Alice", 30, Address("123 Main St", "NYC", "10001"))
          val migration = MigrationBuilder
            .newBuilder[PersonWithAddress, PersonWithAddress]
            .withAction(
              MigrationAction.AddField(
                DynamicOptic.root.field("name").field("street"),
                "default"
              )
            )
            .build

          val result = migration(person)

          assertTrue(result.isLeft) // Should fail with InvalidStructure
        }
      )
    ),
    suite("Edge Cases")(
      test("empty source schema migration") {
        case class EmptySource()
        case class NonEmptyTarget(field: String)

        implicit val emptySourceSchema: Schema[EmptySource]       = Schema.derived[EmptySource]
        implicit val nonEmptyTargetSchema: Schema[NonEmptyTarget] = Schema.derived[NonEmptyTarget]

        val migration = MigrationBuilder
          .newBuilder[EmptySource, NonEmptyTarget]
          .addField(
            (_: NonEmptyTarget).field,
            "default"
          )
          .build

        val result = migration(EmptySource())
        assertTrue(
          result == Right(NonEmptyTarget("default")),
          result.map(_.field) == Right("default")
        )
      },
      test("empty target schema migration") {
        case class NonEmptySource(field: String)
        case class EmptyTarget()

        implicit val nonEmptySourceSchema: Schema[NonEmptySource] = Schema.derived[NonEmptySource]
        implicit val emptyTargetSchema: Schema[EmptyTarget]       = Schema.derived[EmptyTarget]

        val migration = MigrationBuilder
          .newBuilder[NonEmptySource, EmptyTarget]
          .dropField(
            (_: NonEmptySource).field,
            ""
          )
          .build

        val result = migration(NonEmptySource("test"))
        assertTrue(
          result.isRight,
          result == Right(EmptyTarget())
        )
      },
      test("single field schema - identity migration") {
        case class SingleField(value: String)

        implicit val singleFieldSchema: Schema[SingleField] = Schema.derived[SingleField]

        val migration = MigrationBuilder
          .newBuilder[SingleField, SingleField]
          .build

        val result = migration(SingleField("test"))
        assertTrue(
          result.isRight,
          result.map(_.value) == Right("test")
        )
      },
      test("single field schema - transform only field") {
        case class SingleField(value: String)

        implicit val singleFieldSchema: Schema[SingleField] = Schema.derived[SingleField]

        val migration = MigrationBuilder
          .newBuilder[SingleField, SingleField]
          .withAction(
            MigrationAction.TransformValue(
              DynamicOptic.root.field("value"),
              DynamicSchemaExpr.StringUppercase(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root)
              )
            )
          )
          .build

        val result = migration(SingleField("test"))
        assertTrue(
          result.isRight,
          result.map(_.value) == Right("TEST")
        )
      },
      test("migration with all fields renamed") {
        case class AllRenamedSource(a: String, b: Int)
        case class AllRenamedTarget(x: String, y: Int)

        implicit val sourceSchema: Schema[AllRenamedSource] = Schema.derived[AllRenamedSource]
        implicit val targetSchema: Schema[AllRenamedTarget] = Schema.derived[AllRenamedTarget]

        val migration = MigrationBuilder
          .newBuilder[AllRenamedSource, AllRenamedTarget]
          .renameField((_: AllRenamedSource).a, (_: AllRenamedTarget).x)
          .renameField((_: AllRenamedSource).b, (_: AllRenamedTarget).y)
          .build

        val result = migration(AllRenamedSource("hello", 42))
        assertTrue(
          result == Right(AllRenamedTarget("hello", 42)),
          result.map(_.x) == Right("hello"),
          result.map(_.y) == Right(42)
        )
      },
      test("migration preserves field order") {
        case class Ordered(first: String, second: Int, third: Boolean)

        implicit val orderedSchema: Schema[Ordered] = Schema.derived[Ordered]

        val migration = MigrationBuilder
          .newBuilder[Ordered, Ordered]
          .withAction(
            MigrationAction.TransformValue(
              DynamicOptic.root.field("second"),
              100
            )
          )
          .build

        val result = migration(Ordered("a", 1, true))
        assertTrue(
          result.isRight,
          result.map(_.first) == Right("a"),
          result.map(_.second) == Right(100),
          result.map(_.third) == Right(true)
        )
      },
      test("chaining many operations") {
        case class ManyFields(a: String, b: Int, c: Boolean, d: Double, e: Long)

        implicit val manyFieldsSchema: Schema[ManyFields] = Schema.derived[ManyFields]

        val migration = MigrationBuilder
          .newBuilder[ManyFields, ManyFields]
          .withAction(
            MigrationAction.TransformValue(
              DynamicOptic.root.field("a"),
              DynamicSchemaExpr.StringUppercase(DynamicSchemaExpr.Dynamic(DynamicOptic.root))
            )
          )
          .withAction(
            MigrationAction.TransformValue(
              DynamicOptic.root.field("b"),
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                10,
                DynamicSchemaExpr.ArithmeticOperator.Add,
                DynamicSchemaExpr.NumericType.IntType
              )
            )
          )
          .withAction(
            MigrationAction.TransformValue(
              DynamicOptic.root.field("d"),
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                2.0,
                DynamicSchemaExpr.ArithmeticOperator.Multiply,
                DynamicSchemaExpr.NumericType.DoubleType
              )
            )
          )
          .build

        val input  = ManyFields("test", 1, true, 2.0, 3L)
        val result = migration(input)
        assertTrue(
          migration.dynamicMigration.actions.length == 3,
          result == Right(ManyFields("TEST", 11, true, 4.0, 3L)),
          result.map(_.a) == Right("TEST"),
          result.map(_.b) == Right(11)
        )
      },
      test("migration with Option[T] to T using mandateField") {
        case class WithOpt(value: Option[Int])
        case class WithoutOpt(value: Int)

        implicit val withOptSchema: Schema[WithOpt]       = Schema.derived[WithOpt]
        implicit val withoutOptSchema: Schema[WithoutOpt] = Schema.derived[WithoutOpt]

        val migration = MigrationBuilder
          .newBuilder[WithOpt, WithoutOpt]
          .mandateField(
            (_: WithOpt).value,
            0
          )
          .build

        val resultSome = migration(WithOpt(Some(42)))
        val resultNone = migration(WithOpt(None))

        assertTrue(
          resultSome == Right(WithoutOpt(42)),
          resultSome.map(_.value) == Right(42),
          resultNone == Right(WithoutOpt(0)),
          resultNone.map(_.value) == Right(0)
        )
      },
      test("migration with T to Option[T] using optionalizeField") {
        case class WithoutOpt(value: Int)
        case class WithOpt(value: Option[Int])

        implicit val withoutOptSchema: Schema[WithoutOpt] = Schema.derived[WithoutOpt]
        implicit val withOptSchema: Schema[WithOpt]       = Schema.derived[WithOpt]

        val migration = MigrationBuilder
          .newBuilder[WithoutOpt, WithOpt]
          .optionalizeField(
            (_: WithoutOpt).value,
            0
          )
          .build

        val result = migration(WithoutOpt(42))
        assertTrue(
          result == Right(WithOpt(Some(42))),
          result.map(_.value) == Right(Some(42))
        )
      },
      test("DynamicOptic-only migration (no macros)") {
        // Test that migrations work without any macro-based selectors
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .joinFields(
            (_: PersonV2).fullName,
            Seq(
              (_: PersonV1).firstName,
              (_: PersonV1).lastName
            ),
            DynamicSchemaExpr.StringConcat(
              DynamicSchemaExpr.StringConcat(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
                " "
              ),
              DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
            )
          )
          .addField(
            (_: PersonV2).country,
            "USA"
          )
          .build

        val v1     = PersonV1("John", "Doe", 30)
        val result = migration(v1)

        assertTrue(
          result.isRight,
          result.map(_.fullName) == Right("John Doe"),
          result.map(_.age) == Right(30),
          result.map(_.country) == Right("USA")
        )
      },
      test("round-trip migration preserves data") {
        val forward = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .joinFields(
            (_: PersonV2).fullName,
            Seq(
              (_: PersonV1).firstName,
              (_: PersonV1).lastName
            ),
            DynamicSchemaExpr.StringConcat(
              DynamicSchemaExpr.StringConcat(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
                " "
              ),
              DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
            )
          )
          .addField(
            (_: PersonV2).country,
            "USA"
          )
          .build

        val backward = MigrationBuilder
          .newBuilder[PersonV2, PersonV1]
          .splitField(
            (_: PersonV2).fullName,
            Seq(
              (_: PersonV1).firstName,
              (_: PersonV1).lastName
            ),
            DynamicSchemaExpr.StringSplit(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root),
              " "
            )
          )
          .dropField(
            (_: PersonV2).country,
            ""
          )
          .build

        val original  = PersonV1("John", "Doe", 30)
        val migrated  = forward(original)
        val roundTrip = migrated.flatMap(backward.apply)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.firstName) == Right("John"),
          roundTrip.map(_.lastName) == Right("Doe"),
          roundTrip.map(_.age) == Right(30)
        )
      },
      test("variant with multiple cases - transform specific case") {
        val migration = MigrationBuilder
          .newBuilder[PaymentMethod, PaymentMethod]
          .withAction(
            MigrationAction.TransformCase(
              DynamicOptic.root,
              "CreditCard",
              Vector(
                MigrationAction.TransformValue(
                  DynamicOptic.root.field("number"),
                  DynamicSchemaExpr.StringUppercase(
                    DynamicSchemaExpr.Dynamic(DynamicOptic.root)
                  )
                )
              )
            )
          )
          .build

        // CreditCard case should be transformed
        val creditCard       = CreditCard("abc-123", "456")
        val creditCardResult = migration(creditCard)

        // PayPal case should be unchanged
        val payPal       = PayPal("test@example.com")
        val payPalResult = migration(payPal)

        assertTrue(
          creditCardResult == Right(CreditCard("ABC-123", "456")),
          payPalResult == Right(PayPal("test@example.com"))
        )
      }
    )
  )
}
