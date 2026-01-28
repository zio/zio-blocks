package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}
import zio.test._

object MigrationSpec extends ZIOSpecDefault {

  case class PersonV1(firstName: String, lastName: String)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(fullName: String, age: Int)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class PersonV3(fullName: String, age: Option[Int])
  object PersonV3 {
    implicit val schema: Schema[PersonV3] = Schema.derived
  }

  case class Address(street: String, city: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class PersonWithAddress(name: String, address: Address)
  object PersonWithAddress {
    implicit val schema: Schema[PersonWithAddress] = Schema.derived
  }

  case class PersonWithAddressV2(name: String, address: Address, country: String)
  object PersonWithAddressV2 {
    implicit val schema: Schema[PersonWithAddressV2] = Schema.derived
  }

  case class Container(items: Vector[String])
  object Container {
    implicit val schema: Schema[Container] = Schema.derived
  }

  case class MapContainer(data: Map[String, Int])
  object MapContainer {
    implicit val schema: Schema[MapContainer] = Schema.derived
  }

  sealed trait Status
  object Status {
    case object Active   extends Status
    case object Inactive extends Status
    case object Pending  extends Status

    implicit val schema: Schema[Status] = Schema.derived
  }

  sealed trait StatusV2
  object StatusV2 {
    case object Running extends StatusV2
    case object Stopped extends StatusV2
    case object Pending extends StatusV2

    implicit val schema: Schema[StatusV2] = Schema.derived
  }

  override def spec = suite("MigrationSpec")(
    suite("DynamicMigration")(
      test("identity migration returns value unchanged") {
        val migration = DynamicMigration.identity
        val value     = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        assertTrue(migration(value) == Right(value))
      },

      test("identity composition law: identity ++ m == m") {
        val m     = DynamicMigration.addField("age", DynamicValue.Primitive(PrimitiveValue.Int(25)))
        val value = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val result1 = (DynamicMigration.identity ++ m)(value)
        val result2 = m(value)
        assertTrue(result1 == result2)
      },

      test("identity composition law: m ++ identity == m") {
        val m     = DynamicMigration.addField("age", DynamicValue.Primitive(PrimitiveValue.Int(25)))
        val value = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val result1 = (m ++ DynamicMigration.identity)(value)
        val result2 = m(value)
        assertTrue(result1 == result2)
      },

      test("associativity law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
        val m1    = DynamicMigration.addField("a", DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val m2    = DynamicMigration.addField("b", DynamicValue.Primitive(PrimitiveValue.Int(2)))
        val m3    = DynamicMigration.addField("c", DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val value = DynamicValue.Record(Vector.empty)

        val leftAssoc  = ((m1 ++ m2) ++ m3)(value)
        val rightAssoc = (m1 ++ (m2 ++ m3))(value)
        assertTrue(leftAssoc == rightAssoc)
      },

      test("add field adds a new field with default value") {
        val migration = DynamicMigration.addField("age", DynamicValue.Primitive(PrimitiveValue.Int(25)))
        val value     = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val expected = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        assertTrue(migration(value) == Right(expected))
      },

      test("drop field removes a field") {
        val migration = DynamicMigration.dropField("age", Some(DynamicValue.Primitive(PrimitiveValue.Int(0))))
        val value     = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val expected = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        assertTrue(migration(value) == Right(expected))
      },

      test("rename field changes field name") {
        val migration = DynamicMigration.renameField("firstName", "givenName")
        val value     = DynamicValue.Record(
          Vector(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val expected = DynamicValue.Record(
          Vector(
            "givenName" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        assertTrue(migration(value) == Right(expected))
      },

      test("reverse of rename is rename with swapped names") {
        val migration = DynamicMigration.renameField("firstName", "givenName")
        val value     = DynamicValue.Record(
          Vector(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )

        val forward  = migration(value).toOption.get
        val backward = migration.reverse(forward).toOption.get
        assertTrue(backward == value)
      },

      test("change field type converts primitive types") {
        val migration = DynamicMigration.changeFieldType(
          "count",
          SchemaExpr.convert(SchemaExpr.PrimitiveType.String, SchemaExpr.PrimitiveType.Int)
        )
        val value = DynamicValue.Record(
          Vector(
            "count" -> DynamicValue.Primitive(PrimitiveValue.String("42"))
          )
        )
        val expected = DynamicValue.Record(
          Vector(
            "count" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
          )
        )
        assertTrue(migration(value) == Right(expected))
      },

      test("mandate field converts Option to required") {
        val migration = DynamicMigration.mandateField(
          "age",
          DynamicValue.Primitive(PrimitiveValue.Int(0))
        )
        val value = DynamicValue.Record(
          Vector(
            "age" -> DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(25)))
          )
        )
        val expected = DynamicValue.Record(
          Vector(
            "age" -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        assertTrue(migration(value) == Right(expected))
      },

      test("mandate field uses default when None") {
        val migration = DynamicMigration.mandateField(
          "age",
          DynamicValue.Primitive(PrimitiveValue.Int(18))
        )
        val value = DynamicValue.Record(
          Vector(
            "age" -> DynamicValue.Variant("None", DynamicValue.Record(Vector.empty))
          )
        )
        val expected = DynamicValue.Record(
          Vector(
            "age" -> DynamicValue.Primitive(PrimitiveValue.Int(18))
          )
        )
        assertTrue(migration(value) == Right(expected))
      },

      test("optionalize field wraps in Some") {
        val migration = DynamicMigration.optionalizeField("name")
        val value     = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val expected = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )
        assertTrue(migration(value) == Right(expected))
      },

      test("rename case changes variant case name") {
        val migration = DynamicMigration.renameCase("Active", "Running")
        val value     = DynamicValue.Variant("Active", DynamicValue.Record(Vector.empty))
        val expected  = DynamicValue.Variant("Running", DynamicValue.Record(Vector.empty))
        assertTrue(migration(value) == Right(expected))
      },

      test("transform elements applies to all sequence elements") {
        val migration = DynamicMigration.transformElements(SchemaExpr.StringExpr.ToUpperCase)
        val value     = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.String("hello")),
            DynamicValue.Primitive(PrimitiveValue.String("world"))
          )
        )
        val expected = DynamicValue.Sequence(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.String("HELLO")),
            DynamicValue.Primitive(PrimitiveValue.String("WORLD"))
          )
        )
        assertTrue(migration(value) == Right(expected))
      },

      test("transform map values applies to all map values") {
        val migration = DynamicMigration.transformMapValues(SchemaExpr.NumericExpr.Add(10))
        val value     = DynamicValue.Map(
          Vector(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
            (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
        )
        val expected = DynamicValue.Map(
          Vector(
            (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(11))),
            (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(12)))
          )
        )
        assertTrue(migration(value) == Right(expected))
      },

      test("join fields combines multiple fields into one") {
        val migration = DynamicMigration.joinFields(
          Vector("firstName", "lastName"),
          "fullName",
          SchemaExpr.concat(" ", "firstName", "lastName")
        )
        val value = DynamicValue.Record(
          Vector(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
          )
        )
        val expected = DynamicValue.Record(
          Vector(
            "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe"))
          )
        )
        assertTrue(migration(value) == Right(expected))
      },

      test("split field divides one field into multiple") {
        val migration = DynamicMigration.splitField(
          "fullName",
          Vector("firstName", "lastName"),
          SchemaExpr.split(" ", "firstName", "lastName")
        )
        val value = DynamicValue.Record(
          Vector(
            "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe"))
          )
        )
        val expected = DynamicValue.Record(
          Vector(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
          )
        )
        assertTrue(migration(value) == Right(expected))
      }
    ),

    suite("Migration[A, B]")(
      test("identity migration returns value unchanged") {
        val migration = Migration.identity[PersonV1]
        val person    = PersonV1("John", "Doe")
        assertTrue(migration(person) == Right(person))
      },

      test("migration composition chains correctly") {
        val addAge = Migration.fromActions[PersonV1, PersonV2](
          MigrationAction.AddField(DynamicOptic.root, "fullName", DynamicValue.Primitive(PrimitiveValue.String(""))),
          MigrationAction.AddField(DynamicOptic.root, "age", DynamicValue.Primitive(PrimitiveValue.Int(0))),
          MigrationAction.DropField(DynamicOptic.root, "firstName", None),
          MigrationAction.DropField(DynamicOptic.root, "lastName", None)
        )

        val makeAgeOptional = Migration.fromActions[PersonV2, PersonV3](
          MigrationAction.Optionalize(DynamicOptic.root, "age")
        )

        val composed = addAge andThen makeAgeOptional
        assertTrue(composed.size == 5)
      },

      test("applyUnsafe throws on failure") {
        val migration = Migration.fromActions[PersonV1, PersonV2](
          MigrationAction.Rename(DynamicOptic.root, "nonExistent", "something")
        )
        val person = PersonV1("John", "Doe")

        val result = try {
          migration.applyUnsafe(person)
          false
        } catch {
          case _: MigrationError => true
        }
        assertTrue(result)
      }
    ),

    suite("MigrationBuilder")(
      test("builder accumulates actions") {
        val builder = MigrationBuilder[PersonV1, PersonV2]
          .addField("fullName", DynamicValue.Primitive(PrimitiveValue.String("")))
          .addField("age", DynamicValue.Primitive(PrimitiveValue.Int(0)))
          .dropField("firstName")
          .dropField("lastName")

        assertTrue(builder.size == 4)
      },

      test("buildPartial creates a migration") {
        val builder = MigrationBuilder[PersonV1, PersonV2]
          .addField("fullName", DynamicValue.Primitive(PrimitiveValue.String("default")))
          .addField("age", DynamicValue.Primitive(PrimitiveValue.Int(25)))
          .dropField("firstName")
          .dropField("lastName")

        val migration = builder.buildPartial
        assertTrue(migration.size == 4)
      },

      test("builder with rename") {
        val builder = MigrationBuilder[PersonV1, PersonV1]
          .renameField("firstName", "givenName")

        val migration = builder.buildPartial
        assertTrue(migration.size == 1)
      }
    ),

    suite("MigrationError")(
      test("error includes path information") {
        val error = MigrationError.missingField(
          DynamicOptic.root.field("user").field("address"),
          "street"
        )
        assertTrue(error.message.contains("street"))
        assertTrue(error.message.contains("user"))
        assertTrue(error.message.contains("address"))
      },

      test("errors can be combined") {
        val error1   = MigrationError.missingField(DynamicOptic.root, "field1")
        val error2   = MigrationError.missingField(DynamicOptic.root, "field2")
        val combined = error1 ++ error2
        assertTrue(combined.errors.length == 2)
      },

      test("action failed includes action name") {
        val error = MigrationError.actionFailed(
          DynamicOptic.root.field("data"),
          "TransformValue",
          "Invalid format"
        )
        assertTrue(error.message.contains("TransformValue"))
        assertTrue(error.message.contains("Invalid format"))
      }
    ),

    suite("SchemaExpr")(
      test("identity returns value unchanged") {
        val expr  = SchemaExpr.identity
        val value = DynamicValue.Primitive(PrimitiveValue.String("test"))
        assertTrue(expr(value) == Right(value))
      },

      test("constant always returns same value") {
        val constValue = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val expr       = SchemaExpr.constDynamic(constValue)
        val anyValue   = DynamicValue.Primitive(PrimitiveValue.String("anything"))
        assertTrue(expr(anyValue) == Right(constValue))
      },

      test("compose applies inner then outer") {
        val inner    = SchemaExpr.NumericExpr.Add(10)
        val outer    = SchemaExpr.NumericExpr.Multiply(2.0)
        val composed = SchemaExpr.compose(outer, inner)

        val value    = DynamicValue.Primitive(PrimitiveValue.Int(5))
        val expected = DynamicValue.Primitive(PrimitiveValue.Double(30.0))
        assertTrue(composed(value) == Right(expected))
      },

      test("string concat joins strings") {
        val expr  = SchemaExpr.concat(" ", "first", "last")
        val value = DynamicValue.Record(
          Vector(
            "first" -> DynamicValue.Primitive(PrimitiveValue.String("Hello")),
            "last"  -> DynamicValue.Primitive(PrimitiveValue.String("World"))
          )
        )
        val expected = DynamicValue.Primitive(PrimitiveValue.String("Hello World"))
        assertTrue(expr(value) == Right(expected))
      },

      test("string split divides string") {
        val expr     = SchemaExpr.split(" ", "first", "last")
        val value    = DynamicValue.Primitive(PrimitiveValue.String("Hello World"))
        val expected = DynamicValue.Record(
          Vector(
            "first" -> DynamicValue.Primitive(PrimitiveValue.String("Hello")),
            "last"  -> DynamicValue.Primitive(PrimitiveValue.String("World"))
          )
        )
        assertTrue(expr(value) == Right(expected))
      },

      test("numeric add is reversible") {
        val expr     = SchemaExpr.add(10)
        val reversed = expr.reverse
        assertTrue(reversed.isDefined)

        val value  = DynamicValue.Primitive(PrimitiveValue.Int(5))
        val result = expr(value).flatMap(reversed.get.apply)
        assertTrue(result == Right(value))
      },

      test("numeric multiply is reversible") {
        val expr     = SchemaExpr.multiply(2.0)
        val reversed = expr.reverse
        assertTrue(reversed.isDefined)

        val value   = DynamicValue.Primitive(PrimitiveValue.Double(5.0))
        val forward = expr(value).toOption.get

        forward match {
          case DynamicValue.Primitive(PrimitiveValue.Double(v)) =>
            assertTrue(v == 10.0)
          case _ => assertTrue(false)
        }
      },

      test("primitive convert String to Int") {
        val expr     = SchemaExpr.convert(SchemaExpr.PrimitiveType.String, SchemaExpr.PrimitiveType.Int)
        val value    = DynamicValue.Primitive(PrimitiveValue.String("42"))
        val expected = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assertTrue(expr(value) == Right(expected))
      },

      test("primitive convert Int to String") {
        val expr     = SchemaExpr.convert(SchemaExpr.PrimitiveType.Int, SchemaExpr.PrimitiveType.String)
        val value    = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val expected = DynamicValue.Primitive(PrimitiveValue.String("42"))
        assertTrue(expr(value) == Right(expected))
      },

      test("wrap option wraps value in Some") {
        val expr     = SchemaExpr.WrapOption
        val value    = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val expected = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42)))
        assertTrue(expr(value) == Right(expected))
      },

      test("unwrap option extracts from Some") {
        val expr     = SchemaExpr.UnwrapOption
        val value    = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42)))
        val expected = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assertTrue(expr(value) == Right(expected))
      }
    ),

    suite("Reverse migrations")(
      test("structural reverse: m.reverse.reverse == m") {
        val migration      = DynamicMigration.renameField("old", "new")
        val doubleReversed = migration.reverse.reverse
        assertTrue(migration.actions == doubleReversed.actions)
      },

      test("semantic inverse for add/drop") {
        val addMigration = DynamicMigration.addField("age", DynamicValue.Primitive(PrimitiveValue.Int(25)))
        val value        = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )

        val forward  = addMigration(value).toOption.get
        val backward = addMigration.reverse(forward).toOption.get
        assertTrue(backward == value)
      },

      test("semantic inverse for rename") {
        val migration = DynamicMigration.renameField("firstName", "givenName")
        val value     = DynamicValue.Record(
          Vector(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )

        val forward  = migration(value).toOption.get
        val backward = migration.reverse(forward).toOption.get
        assertTrue(backward == value)
      },

      test("semantic inverse for join/split") {
        val joinMigration = DynamicMigration.joinFields(
          Vector("first", "last"),
          "full",
          SchemaExpr.concat(" ", "first", "last")
        )
        val value = DynamicValue.Record(
          Vector(
            "first" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "last"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
          )
        )

        val forward  = joinMigration(value).toOption.get
        val backward = joinMigration.reverse(forward).toOption.get

        backward match {
          case DynamicValue.Record(fields) =>
            val firstOpt = fields.find(_._1 == "first")
            val lastOpt  = fields.find(_._1 == "last")
            assertTrue(firstOpt.isDefined && lastOpt.isDefined)
          case _ => assertTrue(false)
        }
      },

      test("semantic inverse for enum rename") {
        val migration = DynamicMigration.renameCase("Active", "Running")
        val value     = DynamicValue.Variant("Active", DynamicValue.Record(Vector.empty))

        val forward  = migration(value).toOption.get
        val backward = migration.reverse(forward).toOption.get
        assertTrue(backward == value)
      }
    ),

    suite("Nested path operations")(
      test("transform nested field") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            DynamicOptic.root.field("address").field("city"),
            SchemaExpr.StringExpr.ToUpperCase
          )
        )
        val value = DynamicValue.Record(
          Vector(
            "name"    -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "address" -> DynamicValue.Record(
              Vector(
                "street" -> DynamicValue.Primitive(PrimitiveValue.String("123 Main St")),
                "city"   -> DynamicValue.Primitive(PrimitiveValue.String("new york"))
              )
            )
          )
        )

        val result = migration(value)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val address = fields.find(_._1 == "address").map(_._2)
            address match {
              case Some(DynamicValue.Record(addrFields)) =>
                val city = addrFields.find(_._1 == "city").map(_._2)
                assertTrue(city == Some(DynamicValue.Primitive(PrimitiveValue.String("NEW YORK"))))
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },

      test("add field to nested record") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("address"),
            "country",
            DynamicValue.Primitive(PrimitiveValue.String("USA"))
          )
        )
        val value = DynamicValue.Record(
          Vector(
            "name"    -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "address" -> DynamicValue.Record(
              Vector(
                "city" -> DynamicValue.Primitive(PrimitiveValue.String("NYC"))
              )
            )
          )
        )

        val result = migration(value)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val address = fields.find(_._1 == "address").map(_._2)
            address match {
              case Some(DynamicValue.Record(addrFields)) =>
                val country = addrFields.find(_._1 == "country").map(_._2)
                assertTrue(country == Some(DynamicValue.Primitive(PrimitiveValue.String("USA"))))
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },

      test("transform elements in nested sequence") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("items"),
            SchemaExpr.StringExpr.ToUpperCase
          )
        )
        val value = DynamicValue.Record(
          Vector(
            "items" -> DynamicValue.Sequence(
              Vector(
                DynamicValue.Primitive(PrimitiveValue.String("apple")),
                DynamicValue.Primitive(PrimitiveValue.String("banana"))
              )
            )
          )
        )

        val result = migration(value)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val items = fields.find(_._1 == "items").map(_._2)
            items match {
              case Some(DynamicValue.Sequence(elements)) =>
                assertTrue(
                  elements == Vector(
                    DynamicValue.Primitive(PrimitiveValue.String("APPLE")),
                    DynamicValue.Primitive(PrimitiveValue.String("BANANA"))
                  )
                )
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      }
    )
  )
}
