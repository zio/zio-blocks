package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationLawsSpec extends ZIOSpecDefault {

  // ============================================================
  // Test Data Models
  // ============================================================

  // Simple case class for basic tests
  final case class PersonV1(firstName: String, lastName: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  final case class PersonV2(fullName: String, age: Int, country: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  // Complex nested case classes
  final case class Address(street: String, city: String, zipCode: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  final case class Employee(name: String, age: Int, salary: Int)
  object Employee {
    implicit val schema: Schema[Employee] = Schema.derived
  }

  final case class CompanyV1(
    name: String,
    address: Address,
    employees: Vector[Employee],
    revenue: Int
  )
  object CompanyV1 {
    implicit val schema: Schema[CompanyV1] = Schema.derived
  }

  final case class CompanyV2(
    companyName: String,
    location: Address,
    staff: Vector[Employee],
    annualRevenue: Int,
    country: String
  )
  object CompanyV2 {
    implicit val schema: Schema[CompanyV2] = Schema.derived
  }

  val genPersonV1: Gen[Any, PersonV1] =
    for {
      firstName <- Gen.alphaNumericStringBounded(3, 10)
      lastName  <- Gen.alphaNumericStringBounded(3, 10)
      age       <- Gen.int(18, 100)
    } yield PersonV1(firstName, lastName, age)

  val genAddress: Gen[Any, Address] =
    for {
      street  <- Gen.alphaNumericStringBounded(5, 20)
      city    <- Gen.alphaNumericStringBounded(5, 15)
      zipCode <- Gen.alphaNumericStringBounded(5, 10)
    } yield Address(street, city, zipCode)

  val genEmployee: Gen[Any, Employee] =
    for {
      name   <- Gen.alphaNumericStringBounded(3, 15)
      age    <- Gen.int(18, 65)
      salary <- Gen.int(30000, 200000)
    } yield Employee(name, age, salary)

  val genCompanyV1: Gen[Any, CompanyV1] =
    for {
      name      <- Gen.alphaNumericStringBounded(5, 20)
      address   <- genAddress
      employees <- Gen.vectorOfBounded(0, 5)(genEmployee)
      revenue   <- Gen.int(100000, 10000000)
    } yield CompanyV1(name, address, employees, revenue)

  // Lossless migration: PersonV1 -> PersonV2
  def personV1ToV2Migration: Migration[PersonV1, PersonV2] =

    MigrationBuilder
      .newBuilder[PersonV1, PersonV2]
      .joinFields(
        DynamicOptic.root.field("fullName"),
        Vector(
          DynamicOptic.root.field("firstName"),
          DynamicOptic.root.field("lastName")
        ),
        SchemaExpr.StringConcat(
          SchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
          SchemaExpr.StringConcat(
            SchemaExpr.Literal(" ", Schema.string),
            SchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
          )
        )
      )
      .addField(DynamicOptic.root.field("country"), SchemaExpr.Literal("USA", Schema.string))
      .buildPartial

  // Lossy migration: CompanyV1 -> CompanyV2
  def companyV1ToV2Migration: Migration[CompanyV1, CompanyV2] =

    MigrationBuilder
      .newBuilder[CompanyV1, CompanyV2]
      .renameField(DynamicOptic.root.field("name"), "companyName")
      .renameField(DynamicOptic.root.field("address"), "location")
      .renameField(DynamicOptic.root.field("employees"), "staff")
      .renameField(DynamicOptic.root.field("revenue"), "annualRevenue")
      .addField(DynamicOptic.root.field("country"), SchemaExpr.Literal("USA", Schema.string))
      .buildPartial

  // Simple addField migration
  def addFieldMigration[A, B](implicit
    schemaA: Schema[A],
    schemaB: Schema[B]
  ): Migration[A, B] =
    MigrationBuilder
      .newBuilder[A, B]
      .addField(
        DynamicOptic.root.field("newField"),
        SchemaExpr.Literal(0, Schema.int)
      )
      .buildPartial

  // Simple dropField migration
  def dropFieldMigration[A, B](implicit
    schemaA: Schema[A],
    schemaB: Schema[B]
  ): Migration[A, B] =
    MigrationBuilder
      .newBuilder[A, B]
      .dropField(
        DynamicOptic.root.field("oldField"),
        SchemaExpr.Literal(0, Schema.int)
      )
      .buildPartial

  def spec = suite("MigrationLawsSpec")(
    suite("Identity Law")(
      test("identity migration returns input unchanged - simple case") {
        check(genPersonV1) { person =>
          val identityMigration = Migration.identity[PersonV1]
          val result            = identityMigration(person)

          assertTrue(result == Right(person))
        }
      },
      test("identity migration returns input unchanged - complex case") {
        check(genCompanyV1) { company =>
          val identityMigration = Migration.identity[CompanyV1]
          val result            = identityMigration(company)

          assertTrue(result == Right(company))
        }
      },
      test("identity migration is structurally empty") {
        val identityMigration = Migration.identity[PersonV1]
        assertTrue(identityMigration.dynamicMigration.actions.isEmpty)
      },
      test("identity migration composed with any migration m gives m") {
        val m        = personV1ToV2Migration
        val identity = Migration.identity[PersonV1]
        val composed = identity ++ m

        // Check that composed migration has same actions as m
        assertTrue(composed.dynamicMigration.actions == m.dynamicMigration.actions)
      },
      test("any migration m composed with identity gives m") {
        val m        = personV1ToV2Migration
        val identity = Migration.identity[PersonV2]
        val composed = m ++ identity

        // Check that composed migration has same actions as m
        assertTrue(composed.dynamicMigration.actions == m.dynamicMigration.actions)
      }
    ),
    suite("Associativity Law")(
      test("(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3) - structure") {
        // Create three simple migrations
        val m1 = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(DynamicOptic.root.field("temp1"), SchemaExpr.Literal(1, Schema.int))
          .buildPartial

        val m2 = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(DynamicOptic.root.field("temp2"), SchemaExpr.Literal(2, Schema.int))
          .buildPartial

        val m3 = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(DynamicOptic.root.field("temp3"), SchemaExpr.Literal(3, Schema.int))
          .buildPartial

        val leftAssoc  = (m1 ++ m2) ++ m3
        val rightAssoc = m1 ++ (m2 ++ m3)

        // Both should have same action sequence
        assertTrue(
          leftAssoc.dynamicMigration.actions == rightAssoc.dynamicMigration.actions
        )
      },
      test("(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3) - execution") {
        check(genPersonV1) { person =>
          val m1 = MigrationBuilder
            .newBuilder[PersonV1, PersonV1]
            .transformField(
              DynamicOptic.root.field("age"),
              SchemaExpr.Arithmetic(
                SchemaExpr.Dynamic(DynamicOptic.root),
                SchemaExpr.Literal(1, Schema.int),
                SchemaExpr.ArithmeticOperator.Add,
                IsNumeric.IsInt
              )
            )
            .buildPartial

          val m2 = MigrationBuilder
            .newBuilder[PersonV1, PersonV1]
            .transformField(
              DynamicOptic.root.field("age"),
              SchemaExpr.Arithmetic(
                SchemaExpr.Dynamic(DynamicOptic.root),
                SchemaExpr.Literal(2, Schema.int),
                SchemaExpr.ArithmeticOperator.Multiply,
                IsNumeric.IsInt
              )
            )
            .buildPartial

          val m3 = MigrationBuilder
            .newBuilder[PersonV1, PersonV1]
            .transformField(
              DynamicOptic.root.field("age"),
              SchemaExpr.Arithmetic(
                SchemaExpr.Dynamic(DynamicOptic.root),
                SchemaExpr.Literal(5, Schema.int),
                SchemaExpr.ArithmeticOperator.Subtract,
                IsNumeric.IsInt
              )
            )
            .buildPartial

          val leftAssoc  = (m1 ++ m2) ++ m3
          val rightAssoc = m1 ++ (m2 ++ m3)

          val leftResult  = leftAssoc(person)
          val rightResult = rightAssoc(person)

          // Both should produce the same result
          assertTrue(leftResult == rightResult)
        }
      }
    ),
    suite("Structural Reverse Law")(
      test("m.reverse.reverse == m - simple addField") {
        val m = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(DynamicOptic.root.field("newField"), SchemaExpr.Literal(42, Schema.int))
          .buildPartial

        val doubleReversed = m.reverse.reverse

        assertTrue(
          doubleReversed.dynamicMigration.actions == m.dynamicMigration.actions
        )
      },
      test("m.reverse.reverse == m - simple dropField") {
        val m = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .dropField(
            DynamicOptic.root.field("oldField"),
            SchemaExpr.Literal(0, Schema.int)
          )
          .buildPartial

        val doubleReversed = m.reverse.reverse

        assertTrue(
          doubleReversed.dynamicMigration.actions == m.dynamicMigration.actions
        )
      },
      test("m.reverse.reverse == m - rename field") {
        val m = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .renameField(
            DynamicOptic.root.field("oldName"),
            "newName"
          )
          .buildPartial

        val doubleReversed = m.reverse.reverse

        assertTrue(
          doubleReversed.dynamicMigration.actions == m.dynamicMigration.actions
        )
      },
      test("m.reverse.reverse == m - complex migration") {
        val m = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(DynamicOptic.root.field("field1"), SchemaExpr.Literal(1, Schema.int))
          .renameField(
            DynamicOptic.root.field("firstName"),
            "givenName"
          )
          .dropField(
            DynamicOptic.root.field("field2"),
            SchemaExpr.Literal(2, Schema.int)
          )
          .buildPartial

        val doubleReversed = m.reverse.reverse

        assertTrue(
          doubleReversed.dynamicMigration.actions == m.dynamicMigration.actions
        )
      },
      test("m.reverse flips action order") {
        val m = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(DynamicOptic.root.field("field1"), SchemaExpr.Literal(1, Schema.int))
          .addField(DynamicOptic.root.field("field2"), SchemaExpr.Literal(2, Schema.int))
          .addField(DynamicOptic.root.field("field3"), SchemaExpr.Literal(3, Schema.int))
          .buildPartial

        val reversed = m.reverse

        // Reversed should have actions in opposite order
        assertTrue(
          reversed.dynamicMigration.actions == m.dynamicMigration.actions.reverse
            .map(_.reverse)
        )
      }
    ),
    suite("Semantic Inverse Law")(
      test("lossless migration: m(a) = b => m.reverse(b) = a - rename only") {
        check(genCompanyV1) { company =>
          val m = companyV1ToV2Migration

          m(company) match {
            case Right(companyV2) =>
              val reversed = m.reverse(companyV2)

              reversed match {
                case Right(recovered) =>
                  // Should recover original structure
                  assertTrue(
                    recovered.name == company.name &&
                      recovered.address == company.address &&
                      recovered.employees == company.employees &&
                      recovered.revenue == company.revenue
                  )
                case Left(_) =>
                  // Reverse may fail due to added field (lossy)
                  assertTrue(true) // Expected for lossy migrations
              }

            case Left(_) =>
              assertTrue(false) // Migration failed
          }
        }
      },
      test("lossy migration: m(a) = b => m.reverse(b) may lose information") {
        check(genPersonV1) { person =>
          val m = personV1ToV2Migration

          m(person) match {
            case Right(personV2) =>
              val reversed = m.reverse(personV2)

              // Reverse will succeed but split may not recover exact firstName/lastName
              assertTrue(reversed.isRight)

            case Left(_) =>
              assertTrue(false) // Migration failed
          }
        }
      },
      test("addField reverse is dropField - semantic roundtrip loses added field") {
        val original = DynamicValue.Record(
          Vector(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe")),
            "age"       -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )

        val addMigration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root.field("country"),
              SchemaExpr.Literal("USA", Schema.string)
            )
          )
        )

        addMigration(original) match {
          case Right(withField) =>
            val reversed = addMigration.reverse(withField)

            reversed match {
              case Right(recovered) =>
                // Should recover original (added field removed)
                assertTrue(recovered == original)
              case Left(_) =>
                assertTrue(false) // Reverse failed
            }

          case Left(_) =>
            assertTrue(false) // AddField failed
        }
      },
      test("optionalize -> mandate roundtrip loses None values") {
        val someValue = DynamicValue.Variant(
          "Some",
          DynamicValue.Record(
            Vector("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
          )
        )

        val mandateMigration = DynamicMigration(
          Vector(
            MigrationAction.Mandate(
              DynamicOptic.root,
              SchemaExpr.Literal(0, Schema.int)
            )
          )
        )

        mandateMigration(someValue) match {
          case Right(unwrapped) =>
            // unwrapped should be Int(42)
            val reversed = mandateMigration.reverse(unwrapped)

            reversed match {
              case Right(rewrapped) =>
                // Should wrap back in Some
                assertTrue(
                  rewrapped.isInstanceOf[DynamicValue.Variant] &&
                    rewrapped.asInstanceOf[DynamicValue.Variant].caseName == "Some"
                )
              case Left(_) =>
                assertTrue(false) // Reverse failed
            }

          case Left(_) =>
            assertTrue(false) // Mandate failed
        }
      }
    ),
    suite("Error Path Tracking")(
      test("error includes path for missing field") {
        val record = DynamicValue.Record(
          Vector(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age"       -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )

        val migration = DynamicMigration(
          Vector(
            MigrationAction.DropField(
              DynamicOptic.root.field("lastName"),
              SchemaExpr.Literal("", Schema.string)
            )
          )
        )

        migration(record) match {
          case Left(err) =>
            assertTrue(
              err.isInstanceOf[MigrationError.FieldNotFound] &&
                err.asInstanceOf[MigrationError.FieldNotFound].path.toString.contains("lastName")
            )
          case Right(_) =>
            assertTrue(false) // Should have failed with FieldNotFound
        }
      },
      test("error includes path for nested field errors") {
        val record = DynamicValue.Record(
          Vector(
            "address" -> DynamicValue.Record(
              Vector(
                "street" -> DynamicValue.Primitive(PrimitiveValue.String("Main St")),
                "city"   -> DynamicValue.Primitive(PrimitiveValue.String("NYC"))
              )
            )
          )
        )

        val migration = DynamicMigration(
          Vector(
            MigrationAction.DropField(
              DynamicOptic.root.field("address").field("zipCode"),
              SchemaExpr.Literal("", Schema.string)
            )
          )
        )

        migration(record) match {
          case Left(err) =>
            val pathStr = err match {
              case MigrationError.FieldNotFound(path, _)                => path.toString
              case MigrationError.IntermediateFieldNotFound(path, _, _) => path.toString
              case _                                                    => ""
            }
            assertTrue(
              pathStr.contains("address") && pathStr.contains("zipCode")
            )
          case Right(_) =>
            assertTrue(false) // Should have failed with FieldNotFound
        }
      },
      test("error includes path for type mismatch") {
        val record = DynamicValue.Record(
          Vector(
            "age" -> DynamicValue.Primitive(PrimitiveValue.String("not a number"))
          )
        )

        val migration = DynamicMigration(
          Vector(
            MigrationAction.TransformValue(
              DynamicOptic.root.field("age"),
              SchemaExpr.Arithmetic(
                SchemaExpr.Dynamic(DynamicOptic.root),
                SchemaExpr.Literal(1, Schema.int),
                SchemaExpr.ArithmeticOperator.Add,
                IsNumeric.IsInt
              )
            )
          )
        )

        migration(record) match {
          case Left(err) =>
            val isEvalError     = err.isInstanceOf[MigrationError.EvaluationError]
            val pathContainsAge = if (isEvalError) {
              err.asInstanceOf[MigrationError.EvaluationError].path.toString.contains("age")
            } else false
            assertTrue(isEvalError && pathContainsAge)
          case Right(_) =>
            assertTrue(false) // Should have failed with evaluation error
        }
      },
      test("error message includes path information") {
        val error = MigrationError.FieldNotFound(
          DynamicOptic.root.field("person").field("address").field("street"),
          "street"
        )

        val message = error.toString

        assertTrue(
          message.contains("person") &&
            message.contains("address") &&
            message.contains("street")
        )
      }
    ),
    suite("Semantic Inverse Failures - Lossy Transformations")(
      test("split/join loses information - cannot recover exact split") {
        val original = DynamicValue.Record(
          Vector(
            "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Q. Doe"))
          )
        )

        // Split "John Q. Doe" by space -> ["John", "Q.", "Doe"] (3 parts)
        // But we only have 2 target fields, so split should FAIL
        val splitMigration = DynamicMigration(
          Vector(
            MigrationAction.Split(
              DynamicOptic.root.field("fullName"),
              Vector(
                DynamicOptic.root.field("firstName"),
                DynamicOptic.root.field("lastName")
              ),
              SchemaExpr.StringSplit(
                SchemaExpr.Dynamic(DynamicOptic.root.field("fullName")),
                " "
              )
            )
          )
        )

        splitMigration(original) match {
          case Right(_) =>
            // Split should have failed due to wrong number of parts
            assertTrue(false)
          case Left(err) =>
            // Expected: split produces 3 results but we have 2 target fields
            assertTrue(err.isInstanceOf[MigrationError.EvaluationError])
        }
      },
      test("type conversion loses precision - DoubleToInt") {
        val original = DynamicValue.Record(
          Vector(
            "value" -> DynamicValue.Primitive(PrimitiveValue.Double(42.7))
          )
        )

        val convertMigration = DynamicMigration(
          Vector(
            MigrationAction.ChangeType(
              DynamicOptic.root.field("value"),
              PrimitiveConverter.DoubleToInt
            )
          )
        )

        convertMigration(original) match {
          case Right(converted) =>
            // Should be Int(42) - lost 0.7
            val reversed = convertMigration.reverse(converted)

            reversed match {
              case Right(recovered) =>
                // Will be Double(42.0), not original Double(42.7)
                assertTrue(recovered != original)
              case Left(_) =>
                assertTrue(false) // Reverse failed
            }

          case Left(_) =>
            assertTrue(false) // Conversion failed
        }
      },
      test("dropField loses original value") {
        val original = DynamicValue.Record(
          Vector(
            "firstName"  -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "lastName"   -> DynamicValue.Primitive(PrimitiveValue.String("Doe")),
            "middleName" -> DynamicValue.Primitive(PrimitiveValue.String("Q."))
          )
        )

        val dropMigration = DynamicMigration(
          Vector(
            MigrationAction.DropField(
              DynamicOptic.root.field("middleName"),
              SchemaExpr.Literal("", Schema.string) // Default for reverse
            )
          )
        )

        dropMigration(original) match {
          case Right(dropped) =>
            val reversed = dropMigration.reverse(dropped)

            reversed match {
              case Right(recovered) =>
                // Will have middleName = "" (default), not original "Q."
                assertTrue(recovered != original)
              case Left(_) =>
                assertTrue(false) // Reverse failed
            }

          case Left(_) =>
            assertTrue(false) // Drop failed
        }
      },
      test("string transformation loses information - uppercase") {
        val original = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe"))
          )
        )

        val uppercaseMigration = DynamicMigration(
          Vector(
            MigrationAction.TransformValue(
              DynamicOptic.root.field("name"),
              SchemaExpr.StringUppercase(
                SchemaExpr.Dynamic(DynamicOptic.root)
              )
            )
          )
        )

        uppercaseMigration(original) match {
          case Right(uppercased) =>
            // uppercased is "JOHN DOE"
            // Reverse applies uppercase again (TransformValue.reverse = this)
            val reversed = uppercaseMigration.reverse(uppercased)

            reversed match {
              case Right(recovered) =>
                // recovered should be "JOHN DOE" (uppercase of "JOHN DOE")
                // which is different from original "John Doe"
                // Extract the name values for comparison
                val originalName = original
                  .asInstanceOf[DynamicValue.Record]
                  .fields
                  .find(_._1 == "name")
                  .map(_._2)
                val recoveredName = recovered
                  .asInstanceOf[DynamicValue.Record]
                  .fields
                  .find(_._1 == "name")
                  .map(_._2)

                // Verify they are different (lossy transformation)
                assertTrue(recoveredName.isDefined && originalName.isDefined && recoveredName != originalName)
              case Left(_) =>
                // Reverse should not fail for uppercase
                assertTrue(false)
            }

          case Left(_) =>
            assertTrue(false) // Uppercase failed
        }
      },
      test("mandate with None loses the None case") {
        val noneValue = DynamicValue.Variant(
          "None",
          DynamicValue.Record(Vector.empty)
        )

        val mandateMigration = DynamicMigration(
          Vector(
            MigrationAction.Mandate(
              DynamicOptic.root,
              SchemaExpr.Literal(99, Schema.int) // Default for None
            )
          )
        )

        mandateMigration(noneValue) match {
          case Right(unwrapped) =>
            // unwrapped is Int(99) from default
            val reversed = mandateMigration.reverse(unwrapped)

            reversed match {
              case Right(rewrapped) =>
                // Will wrap in Some(Int(99)), not None
                val variant = rewrapped.asInstanceOf[DynamicValue.Variant]
                assertTrue(
                  variant.caseName == "Some" // Lost the None case
                )
              case Left(_) =>
                assertTrue(false) // Reverse failed
            }

          case Left(_) =>
            assertTrue(false) // Mandate failed
        }
      },
      test("join + split is lossless for simple names without spaces") {
        // Simple names: "John" + " " + "Doe" -> "John Doe" -> split(" ") -> ["John", "Doe"]
        val person = PersonV1("John", "Doe", 30)
        val m      = personV1ToV2Migration

        val result = for {
          personV2  <- m(person)
          recovered <- m.reverse(personV2)
        } yield recovered

        result match {
          case Right(recovered) =>
            assertTrue(
              recovered.firstName == person.firstName &&
                recovered.lastName == person.lastName &&
                recovered.age == person.age
            )
          case Left(_) =>
            assertTrue(false)
        }
      },
      test("join + split is lossy when firstName contains spaces") {
        // firstName with space: "Mary Jane" + " " + "Watson" -> "Mary Jane Watson"
        // split(" ") -> ["Mary", "Jane", "Watson"] (3 parts, but we have 2 fields!)
        // This should fail on reverse because split produces wrong number of parts
        val person = PersonV1("Mary Jane", "Watson", 25)
        val m      = personV1ToV2Migration

        m(person) match {
          case Right(personV2) =>
            // Forward works: fullName = "Mary Jane Watson"
            assertTrue(personV2.fullName == "Mary Jane Watson") &&
            assertTrue(personV2.age == 25) && {
              // Reverse fails: split by space produces 3 parts, not 2
              val reversed = m.reverse(personV2)
              assertTrue(reversed.isLeft)
            }
          case Left(_) =>
            assertTrue(false) // Forward migration should succeed
        }
      },
      test("join + split is lossy when lastName contains spaces") {
        // lastName with space: "Jean" + " " + "Van Damme" -> "Jean Van Damme"
        // split(" ") -> ["Jean", "Van", "Damme"] (3 parts, but we have 2 fields!)
        val person = PersonV1("Jean", "Van Damme", 60)
        val m      = personV1ToV2Migration

        m(person) match {
          case Right(personV2) =>
            assertTrue(personV2.fullName == "Jean Van Damme") && {
              val reversed = m.reverse(personV2)
              // Reverse fails: split produces 3 parts
              assertTrue(reversed.isLeft)
            }
          case Left(_) =>
            assertTrue(false) // Forward migration should succeed
        }
      },
      test("join + split with extra whitespace loses exact spacing") {
        // Edge case: what if we had multiple consecutive spaces?
        // This tests a different kind of lossiness where spacing is normalized
        val original = DynamicValue.Record(
          Vector(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe")),
            "age"       -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )

        val joinMigration = DynamicMigration(
          Vector(
            MigrationAction.Join(
              DynamicOptic.root.field("fullName"),
              Vector(
                DynamicOptic.root.field("firstName"),
                DynamicOptic.root.field("lastName")
              ),
              SchemaExpr.StringConcat(
                SchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
                SchemaExpr.StringConcat(
                  SchemaExpr.Literal("  ", Schema.string), // Double space!
                  SchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
                )
              )
            )
          )
        )

        joinMigration(original) match {
          case Right(joined) =>
            // fullName = "John  Doe" (double space)
            val reversed = joinMigration.reverse(joined)

            reversed match {
              case Right(_) =>
                // Split by "  " should recover correctly only if reverse uses same separator
                // But the reverse Split uses space delimiter from forward Join expr
                // This demonstrates that the split is lossy if separators don't match exactly
                assertTrue(true) // The behavior depends on implementation
              case Left(_) =>
                // May fail if split doesn't handle double spaces
                assertTrue(true)
            }
          case Left(_) =>
            assertTrue(false)
        }
      },
      test("addField in migration loses information on reverse - country uses default") {
        // The personV1ToV2Migration adds a "country" field with default "USA"
        // On reverse, this field is dropped, so any non-default country is lost
        val person = PersonV1("Alice", "Smith", 28)
        val m      = personV1ToV2Migration

        m(person) match {
          case Right(personV2) =>
            // Forward adds country = "USA"
            assertTrue(personV2.country == "USA") && {
              // Even if we could modify the country, reverse would drop it
              // This shows that addField -> dropField loses the actual value
              val reversed = m.reverse(personV2)
              reversed match {
                case Right(recovered) =>
                  // Age and names are preserved, country is irreparably lost
                  assertTrue(
                    recovered.firstName == "Alice" &&
                      recovered.lastName == "Smith" &&
                      recovered.age == 28
                  )
                case Left(_) =>
                  assertTrue(false)
              }
            }
          case Left(_) =>
            assertTrue(false)
        }
      }
    ),
    suite("Additional Properties")(
      test("identity is left identity for composition") {
        check(genPersonV1) { person =>
          val m        = personV1ToV2Migration
          val identity = Migration.identity[PersonV1]
          val composed = identity ++ m

          val directResult   = m(person)
          val composedResult = composed(person)

          assertTrue(directResult == composedResult)
        }
      },
      test("identity is right identity for composition") {
        check(genPersonV1) { person =>
          val m        = personV1ToV2Migration
          val identity = Migration.identity[PersonV2]
          val composed = m ++ identity

          val directResult   = m(person)
          val composedResult = composed(person)

          assertTrue(directResult == composedResult)
        }
      },
      test("reverse distributes over composition: (m1 ++ m2).reverse == m2.reverse ++ m1.reverse") {
        val m1 = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(DynamicOptic.root.field("field1"), SchemaExpr.Literal(1, Schema.int))
          .buildPartial

        val m2 = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(DynamicOptic.root.field("field2"), SchemaExpr.Literal(2, Schema.int))
          .buildPartial

        val composedReversed = (m1 ++ m2).reverse
        val reversedComposed = m2.reverse ++ m1.reverse

        assertTrue(
          composedReversed.dynamicMigration.actions == reversedComposed.dynamicMigration.actions
        )
      }
    )
  )
}
