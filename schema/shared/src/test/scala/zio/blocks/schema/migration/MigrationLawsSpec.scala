package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._
import MigrationTestCompat._

object MigrationLawsSpec extends ZIOSpecDefault {
  locally(ensureLoaded)

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

  // CompanyV1 with only renamed fields (no added/removed fields) for lossless reverse test
  final case class CompanyV1Renamed(
    companyName: String,
    location: Address,
    staff: Vector[Employee],
    annualRevenue: Int
  )
  object CompanyV1Renamed {
    implicit val schema: Schema[CompanyV1Renamed] = Schema.derived
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
        DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
          DynamicSchemaExpr.StringConcat(
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(" "))),
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
          )
        )
      )
      .addField(
        DynamicOptic.root.field("country"),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("USA")))
      )
      .buildPartial

  // Lossy migration: CompanyV1 -> CompanyV2
  def companyV1ToV2Migration: Migration[CompanyV1, CompanyV2] =

    MigrationBuilder
      .newBuilder[CompanyV1, CompanyV2]
      .renameField(DynamicOptic.root.field("name"), "companyName")
      .renameField(DynamicOptic.root.field("address"), "location")
      .renameField(DynamicOptic.root.field("employees"), "staff")
      .renameField(DynamicOptic.root.field("revenue"), "annualRevenue")
      .addField(
        DynamicOptic.root.field("country"),
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("USA")))
      )
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
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
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
        DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
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
          .addField(
            DynamicOptic.root.field("temp1"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
          .build

        val m2 = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(
            DynamicOptic.root.field("temp2"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
          .build

        val m3 = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(
            DynamicOptic.root.field("temp3"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
          )
          .build

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
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
                DynamicSchemaExpr.ArithmeticOperator.Add,
                DynamicSchemaExpr.NumericType.IntType
              )
            )
            .build

          val m2 = MigrationBuilder
            .newBuilder[PersonV1, PersonV1]
            .transformField(
              DynamicOptic.root.field("age"),
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
                DynamicSchemaExpr.ArithmeticOperator.Multiply,
                DynamicSchemaExpr.NumericType.IntType
              )
            )
            .build

          val m3 = MigrationBuilder
            .newBuilder[PersonV1, PersonV1]
            .transformField(
              DynamicOptic.root.field("age"),
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(5))),
                DynamicSchemaExpr.ArithmeticOperator.Subtract,
                DynamicSchemaExpr.NumericType.IntType
              )
            )
            .build

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
          .addField(
            DynamicOptic.root.field("newField"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
          )
          .build

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
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
          .build

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
          .build

        val doubleReversed = m.reverse.reverse

        assertTrue(
          doubleReversed.dynamicMigration.actions == m.dynamicMigration.actions
        )
      },
      test("m.reverse.reverse == m - complex migration") {
        val m = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(
            DynamicOptic.root.field("field1"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
          .renameField(
            DynamicOptic.root.field("firstName"),
            "givenName"
          )
          .dropField(
            DynamicOptic.root.field("field2"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
          .build

        val doubleReversed = m.reverse.reverse

        assertTrue(
          doubleReversed.dynamicMigration.actions == m.dynamicMigration.actions
        )
      },
      test("m.reverse flips action order") {
        val m = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(
            DynamicOptic.root.field("field1"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
          .addField(
            DynamicOptic.root.field("field2"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
          .addField(
            DynamicOptic.root.field("field3"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(3)))
          )
          .build

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
        // Use a truly lossless migration (renames only, no addField/dropField)
        val m: Migration[CompanyV1, CompanyV1Renamed] =
          MigrationBuilder
            .newBuilder[CompanyV1, CompanyV1Renamed]
            .renameField(DynamicOptic.root.field("name"), "companyName")
            .renameField(DynamicOptic.root.field("address"), "location")
            .renameField(DynamicOptic.root.field("employees"), "staff")
            .renameField(DynamicOptic.root.field("revenue"), "annualRevenue")
            .buildPartial

        check(genCompanyV1) { company =>
          val forward = m(company)

          forward match {
            case Right(renamed) =>
              val reversed = m.reverse(renamed)

              reversed match {
                case Right(recovered) =>
                  assertTrue(
                    recovered.name == company.name &&
                      recovered.address == company.address &&
                      recovered.employees == company.employees &&
                      recovered.revenue == company.revenue
                  )
                case Left(_) =>
                  assertTrue(false) // Reverse of lossless rename-only migration should not fail
              }

            case Left(_) =>
              assertTrue(false) // Forward migration failed
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
          Chunk(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe")),
            "age"       -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )

        val addMigration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root.field("country"),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("USA")))
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
            Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
          )
        )

        val mandateMigration = DynamicMigration(
          Vector(
            MigrationAction.Mandate(
              DynamicOptic.root,
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
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
                    rewrapped.asInstanceOf[DynamicValue.Variant].caseNameValue == "Some"
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
          Chunk(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age"       -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )

        val migration = DynamicMigration(
          Vector(
            MigrationAction.DropField(
              DynamicOptic.root.field("lastName"),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
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
          Chunk(
            "address" -> DynamicValue.Record(
              Chunk(
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
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
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
          Chunk(
            "age" -> DynamicValue.Primitive(PrimitiveValue.String("not a number"))
          )
        )

        val migration = DynamicMigration(
          Vector(
            MigrationAction.TransformValue(
              DynamicOptic.root.field("age"),
              DynamicSchemaExpr.Arithmetic(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root),
                DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
                DynamicSchemaExpr.ArithmeticOperator.Add,
                DynamicSchemaExpr.NumericType.IntType
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
          Chunk(
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
              DynamicSchemaExpr.StringSplit(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("fullName")),
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
          Chunk(
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
          Chunk(
            "firstName"  -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "lastName"   -> DynamicValue.Primitive(PrimitiveValue.String("Doe")),
            "middleName" -> DynamicValue.Primitive(PrimitiveValue.String("Q."))
          )
        )

        val dropMigration = DynamicMigration(
          Vector(
            MigrationAction.DropField(
              DynamicOptic.root.field("middleName"),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(""))) // Default for reverse
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
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe"))
          )
        )

        val uppercaseMigration = DynamicMigration(
          Vector(
            MigrationAction.TransformValue(
              DynamicOptic.root.field("name"),
              DynamicSchemaExpr.StringUppercase(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root)
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
          DynamicValue.Record(Chunk.empty)
        )

        val mandateMigration = DynamicMigration(
          Vector(
            MigrationAction.Mandate(
              DynamicOptic.root,
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(99))) // Default for None
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
                  variant.caseNameValue == "Some" // Lost the None case
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
          Chunk(
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
              DynamicSchemaExpr.StringConcat(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
                DynamicSchemaExpr.StringConcat(
                  DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("  "))), // Double space!
                  DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
                )
              )
            )
          )
        )

        joinMigration(original) match {
          case Right(joined) =>
            // fullName = "John  Doe" (double space)
            val fullNameField = joined
              .asInstanceOf[DynamicValue.Record]
              .fields
              .find(_._1 == "fullName")
              .map(_._2)
            assertTrue(
              fullNameField == Some(DynamicValue.Primitive(PrimitiveValue.String("John  Doe")))
            ) && {
              // Reverse split recovers the original fields despite double space,
              // demonstrating the join combiner's separator doesn't affect split fidelity
              val reversed = joinMigration.reverse(joined)
              reversed match {
                case Right(recovered) =>
                  val fields    = recovered.asInstanceOf[DynamicValue.Record].fields
                  val firstName = fields.find(_._1 == "firstName").map(_._2)
                  val lastName  = fields.find(_._1 == "lastName").map(_._2)
                  assertTrue(
                    firstName == Some(DynamicValue.Primitive(PrimitiveValue.String("John"))) &&
                      lastName == Some(DynamicValue.Primitive(PrimitiveValue.String("Doe")))
                  )
                case Left(_) =>
                  assertTrue(false) // Reverse failed
              }
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
          .addField(
            DynamicOptic.root.field("field1"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
          .build

        val m2 = MigrationBuilder
          .newBuilder[PersonV1, PersonV1]
          .addField(
            DynamicOptic.root.field("field2"),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
          )
          .build

        val composedReversed = (m1 ++ m2).reverse
        val reversedComposed = m2.reverse ++ m1.reverse

        assertTrue(
          composedReversed.dynamicMigration.actions == reversedComposed.dynamicMigration.actions
        )
      }
    )
  )
}
