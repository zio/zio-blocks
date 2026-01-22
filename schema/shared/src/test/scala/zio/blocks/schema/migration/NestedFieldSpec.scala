package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object NestedFieldSpec extends ZIOSpecDefault {

  // 2-level nesting
  case class Address(street: String, city: String, zip: String)
  case class PersonV1(name: String, age: Int, address: Address)
  case class PersonV2(name: String, age: Int, address: Address) // For migrations

  // 3-level nesting
  case class Company(name: String, address: Address)
  case class EmployeeV1(name: String, company: Company)
  case class EmployeeV2(name: String, company: Company)

  // Nested with optional
  case class AddressWithApt(street: String, city: String, zip: String, apartment: Option[String])
  case class PersonWithOptAddress(name: String, address: AddressWithApt)

  // Nested inside sequence
  case class Contact(name: String, address: Address)
  case class Organization(contacts: Vector[Contact])

  // Nested with map
  case class AddressWithMetadata(street: String, city: String, zip: String, metadata: Map[String, String])
  case class PersonWithMetadata(name: String, address: AddressWithMetadata)

  // Nested with variant
  sealed trait AddressType
  object AddressType {
    case object Home  extends AddressType
    case object Work  extends AddressType
    case object Other extends AddressType
  }
  case class AddressWithType(street: String, city: String, zip: String, addressType: AddressType)
  case class PersonWithAddressType(name: String, address: AddressWithType)

  // Nested with sequence
  case class Department(name: String, employeeIds: Vector[Int])
  case class CompanyWithDept(name: String, department: Department)

  // Schemas
  implicit val addressSchema: Schema[Address]                             = Schema.derived[Address]
  implicit val personV1Schema: Schema[PersonV1]                           = Schema.derived[PersonV1]
  implicit val personV2Schema: Schema[PersonV2]                           = Schema.derived[PersonV2]
  implicit val companySchema: Schema[Company]                             = Schema.derived[Company]
  implicit val employeeV1Schema: Schema[EmployeeV1]                       = Schema.derived[EmployeeV1]
  implicit val employeeV2Schema: Schema[EmployeeV2]                       = Schema.derived[EmployeeV2]
  implicit val addressWithAptSchema: Schema[AddressWithApt]               = Schema.derived[AddressWithApt]
  implicit val personWithOptAddressSchema: Schema[PersonWithOptAddress]   = Schema.derived[PersonWithOptAddress]
  implicit val contactSchema: Schema[Contact]                             = Schema.derived[Contact]
  implicit val organizationSchema: Schema[Organization]                   = Schema.derived[Organization]
  implicit val addressWithMetadataSchema: Schema[AddressWithMetadata]     = Schema.derived[AddressWithMetadata]
  implicit val personWithMetadataSchema: Schema[PersonWithMetadata]       = Schema.derived[PersonWithMetadata]
  implicit val addressTypeSchema: Schema[AddressType]                     = Schema.derived[AddressType]
  implicit val addressWithTypeSchema: Schema[AddressWithType]             = Schema.derived[AddressWithType]
  implicit val personWithAddressTypeSchema: Schema[PersonWithAddressType] = Schema.derived[PersonWithAddressType]
  implicit val departmentSchema: Schema[Department]                       = Schema.derived[Department]
  implicit val companyWithDeptSchema: Schema[CompanyWithDept]             = Schema.derived[CompanyWithDept]

  def spec = suite("NestedFieldSpec")(
    suite("AddField - Nested")(
      test("2-level: add field to nested record") {
        // Add _.address.country with default "USA"
        val person    = PersonV1("Alice", 30, Address("123 Main St", "NYC", "10001"))
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .addField(
            DynamicOptic.root.field("address").field("country"),
            SchemaExpr.Literal[DynamicValue, String]("USA", Schema.string)
          )
          .buildPartial

        val result = migration(person)

        assertTrue(result.isRight)
      },
      test("3-level: add field to deeply nested record") {
        // Add _.company.address.country with default "USA"
        val employee  = EmployeeV1("Bob", Company("Acme Inc", Address("456 Oak Ave", "LA", "90001")))
        val migration = MigrationBuilder
          .newBuilder[EmployeeV1, EmployeeV2]
          .addField(
            DynamicOptic.root.field("company").field("address").field("country"),
            SchemaExpr.Literal[DynamicValue, String]("USA", Schema.string)
          )
          .buildPartial

        val result = migration(employee)

        assertTrue(result.isRight)
      },
      test("error: intermediate field not found") {
        // Try to add _.nonexistent.street
        val person    = PersonV1("Alice", 30, Address("123 Main St", "NYC", "10001"))
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .addField(
            DynamicOptic.root.field("nonexistent").field("street"),
            SchemaExpr.Literal[DynamicValue, String]("default", Schema.string)
          )
          .buildPartial

        val result = migration(person)

        assertTrue(result.isLeft)
      },
      test("error: intermediate field is not a record") {
        // Try to add _.name.street (name is String, not Record)
        val person    = PersonV1("Alice", 30, Address("123 Main St", "NYC", "10001"))
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .addField(
            DynamicOptic.root.field("name").field("street"),
            SchemaExpr.Literal[DynamicValue, String]("default", Schema.string)
          )
          .buildPartial

        val result = migration(person)

        assertTrue(result.isLeft)
      }
    ),

    suite("DropField - Nested")(
      test("2-level: drop field from nested record") {
        // Drop _.address.zip - test at DynamicValue level
        val person        = PersonV1("Alice", 30, Address("123 Main St", "NYC", "10001"))
        val personDynamic = personV1Schema.toDynamicValue(person)

        val migration = DynamicMigration(
          Vector(
            MigrationAction.DropField(
              DynamicOptic.root.field("address").field("zip"),
              SchemaExpr.Literal[DynamicValue, String]("00000", Schema.string)
            )
          )
        )

        val result = migration(personDynamic)

        assertTrue(result.isRight)
      },
      test("3-level: drop field from deeply nested record") {
        // Drop _.company.address.zip - test at DynamicValue level
        val employee        = EmployeeV1("Bob", Company("Acme Inc", Address("456 Oak Ave", "LA", "90001")))
        val employeeDynamic = employeeV1Schema.toDynamicValue(employee)

        val migration = DynamicMigration(
          Vector(
            MigrationAction.DropField(
              DynamicOptic.root.field("company").field("address").field("zip"),
              SchemaExpr.Literal[DynamicValue, String]("00000", Schema.string)
            )
          )
        )

        val result = migration(employeeDynamic)

        assertTrue(result.isRight)
      },
      test("error: nested field not found") {
        // Try to drop _.address.nonexistent
        val person    = PersonV1("Alice", 30, Address("123 Main St", "NYC", "10001"))
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .dropField(
            DynamicOptic.root.field("address").field("nonexistent"),
            SchemaExpr.Literal[DynamicValue, String]("default", Schema.string)
          )
          .buildPartial

        val result = migration(person)

        assertTrue(result.isLeft)
      }
    ),

    suite("Rename - Nested")(
      test("2-level: rename nested field") {
        // Rename _.address.street to _.address.streetName - test at DynamicValue level
        val person        = PersonV1("Alice", 30, Address("123 Main St", "NYC", "10001"))
        val personDynamic = personV1Schema.toDynamicValue(person)

        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(
              DynamicOptic.root.field("address").field("street"),
              "streetName"
            )
          )
        )

        val result = migration(personDynamic)

        assertTrue(result.isRight)
      },
      test("3-level: rename deeply nested field") {
        // Rename _.company.address.street to _.company.address.streetName - test at DynamicValue level
        val employee        = EmployeeV1("Bob", Company("Acme Inc", Address("456 Oak Ave", "LA", "90001")))
        val employeeDynamic = employeeV1Schema.toDynamicValue(employee)

        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(
              DynamicOptic.root.field("company").field("address").field("street"),
              "streetName"
            )
          )
        )

        val result = migration(employeeDynamic)

        assertTrue(result.isRight)
      },
      test("reverse: nested rename reverses correctly") {
        // Rename and reverse should restore original
        val person        = PersonV1("Alice", 30, Address("123 Main St", "NYC", "10001"))
        val personDynamic = personV1Schema.toDynamicValue(person)

        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(
              DynamicOptic.root.field("address").field("street"),
              "streetName"
            )
          )
        )

        val reversed = migration.reverse
        val result   = migration(personDynamic).flatMap(reversed.apply)

        assertTrue(result == Right(personDynamic))
      }
    ),

    suite("TransformValue - Nested")(
      test("2-level: transform nested field value") {
        // Transform _.address.city to uppercase
        val person    = PersonV1("Alice", 30, Address("123 Main St", "nyc", "10001"))
        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .transformField(
            DynamicOptic.root.field("address").field("city"),
            SchemaExpr.StringUppercase(
              SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
            )
          )
          .buildPartial

        val result = migration(person)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == PersonV2("Alice", 30, Address("123 Main St", "NYC", "10001"))
        )
      },
      test("3-level: transform deeply nested field") {
        // Transform _.company.address.city to uppercase
        val employee  = EmployeeV1("Bob", Company("Acme Inc", Address("456 Oak Ave", "la", "90001")))
        val migration = MigrationBuilder
          .newBuilder[EmployeeV1, EmployeeV2]
          .transformField(
            DynamicOptic.root.field("company").field("address").field("city"),
            SchemaExpr.StringUppercase(
              SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
            )
          )
          .buildPartial

        val result = migration(employee)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == EmployeeV2("Bob", Company("Acme Inc", Address("456 Oak Ave", "LA", "90001")))
        )
      }
    ),

    suite("ChangeType - Nested")(
      test("2-level: change type of nested field") {
        // Change _.address.zip from String to Int (if it's numeric)
        // For this test, we'll use a dynamic value with zip as string "10001"
        val personDynamic = DynamicValue.Record(
          Vector(
            "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"     -> DynamicValue.Primitive(PrimitiveValue.Int(30)),
            "address" -> DynamicValue.Record(
              Vector(
                "street" -> DynamicValue.Primitive(PrimitiveValue.String("123 Main St")),
                "city"   -> DynamicValue.Primitive(PrimitiveValue.String("NYC")),
                "zip"    -> DynamicValue.Primitive(PrimitiveValue.String("10001"))
              )
            )
          )
        )

        val migration = DynamicMigration(
          Vector(
            MigrationAction.ChangeType(
              DynamicOptic.root.field("address").field("zip"),
              PrimitiveConverter.StringToInt
            )
          )
        )

        val result = migration(personDynamic)

        val expected = DynamicValue.Record(
          Vector(
            "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"     -> DynamicValue.Primitive(PrimitiveValue.Int(30)),
            "address" -> DynamicValue.Record(
              Vector(
                "street" -> DynamicValue.Primitive(PrimitiveValue.String("123 Main St")),
                "city"   -> DynamicValue.Primitive(PrimitiveValue.String("NYC")),
                "zip"    -> DynamicValue.Primitive(PrimitiveValue.Int(10001))
              )
            )
          )
        )

        assertTrue(result == Right(expected))
      },
      test("3-level: change type of deeply nested field") {
        val employeeDynamic = DynamicValue.Record(
          Vector(
            "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
            "company" -> DynamicValue.Record(
              Vector(
                "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Acme Inc")),
                "address" -> DynamicValue.Record(
                  Vector(
                    "street" -> DynamicValue.Primitive(PrimitiveValue.String("456 Oak Ave")),
                    "city"   -> DynamicValue.Primitive(PrimitiveValue.String("LA")),
                    "zip"    -> DynamicValue.Primitive(PrimitiveValue.String("90001"))
                  )
                )
              )
            )
          )
        )

        val migration = DynamicMigration(
          Vector(
            MigrationAction.ChangeType(
              DynamicOptic.root.field("company").field("address").field("zip"),
              PrimitiveConverter.StringToInt
            )
          )
        )

        val result = migration(employeeDynamic)

        val expected = DynamicValue.Record(
          Vector(
            "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
            "company" -> DynamicValue.Record(
              Vector(
                "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Acme Inc")),
                "address" -> DynamicValue.Record(
                  Vector(
                    "street" -> DynamicValue.Primitive(PrimitiveValue.String("456 Oak Ave")),
                    "city"   -> DynamicValue.Primitive(PrimitiveValue.String("LA")),
                    "zip"    -> DynamicValue.Primitive(PrimitiveValue.Int(90001))
                  )
                )
              )
            )
          )
        )

        assertTrue(result == Right(expected))
      }
    ),

    suite("Mandate - Nested")(
      test("2-level: mandate nested optional field") {
        // Mandate _.address.apartment from Option[String] to String - test at DynamicValue level
        val person = PersonWithOptAddress(
          "Alice",
          AddressWithApt("123 Main St", "NYC", "10001", Some("5B"))
        )
        val personDynamic = personWithOptAddressSchema.toDynamicValue(person)

        val migration = DynamicMigration(
          Vector(
            MigrationAction.Mandate(
              DynamicOptic.root.field("address").field("apartment"),
              SchemaExpr.Literal[DynamicValue, String]("N/A", Schema.string)
            )
          )
        )

        val result = migration(personDynamic)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "address")
            .get
            ._2
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "apartment")
            .get
            ._2 == DynamicValue.Primitive(PrimitiveValue.String("5B"))
        )
      },
      test("2-level: mandate nested None with default") {
        val person = PersonWithOptAddress(
          "Alice",
          AddressWithApt("123 Main St", "NYC", "10001", None)
        )
        val personDynamic = personWithOptAddressSchema.toDynamicValue(person)

        val migration = DynamicMigration(
          Vector(
            MigrationAction.Mandate(
              DynamicOptic.root.field("address").field("apartment"),
              SchemaExpr.Literal[DynamicValue, String]("N/A", Schema.string)
            )
          )
        )

        val result = migration(personDynamic)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "address")
            .get
            ._2
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "apartment")
            .get
            ._2 == DynamicValue.Primitive(PrimitiveValue.String("N/A"))
        )
      }
    ),

    suite("Optionalize - Nested")(
      test("2-level: optionalize nested field") {
        // Optionalize _.address.zip from String to Option[String] - test at DynamicValue level
        val person        = PersonV1("Alice", 30, Address("123 Main St", "NYC", "10001"))
        val personDynamic = personV1Schema.toDynamicValue(person)

        val migration = DynamicMigration(
          Vector(
            MigrationAction.Optionalize(
              DynamicOptic.root.field("address").field("zip"),
              SchemaExpr.Literal[DynamicValue, String]("", Schema.string)
            )
          )
        )

        val result = migration(personDynamic)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "address")
            .get
            ._2
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "zip")
            .get
            ._2 == DynamicValue.Variant(
            "Some",
            DynamicValue.Record(Vector("value" -> DynamicValue.Primitive(PrimitiveValue.String("10001"))))
          )
        )
      },
      test("3-level: optionalize deeply nested field") {
        val employee        = EmployeeV1("Bob", Company("Acme Inc", Address("456 Oak Ave", "LA", "90001")))
        val employeeDynamic = employeeV1Schema.toDynamicValue(employee)

        val migration = DynamicMigration(
          Vector(
            MigrationAction.Optionalize(
              DynamicOptic.root.field("company").field("address").field("zip"),
              SchemaExpr.Literal[DynamicValue, String]("", Schema.string)
            )
          )
        )

        val result = migration(employeeDynamic)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "company")
            .get
            ._2
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "address")
            .get
            ._2
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "zip")
            .get
            ._2 == DynamicValue.Variant(
            "Some",
            DynamicValue.Record(Vector("value" -> DynamicValue.Primitive(PrimitiveValue.String("90001"))))
          )
        )
      }
    ),

    suite("Join - Nested")(
      test("2-level: join nested fields") {
        // Join _.address.street + _.address.city -> _.address.fullAddress - test at DynamicValue level
        val person        = PersonV1("Alice", 30, Address("123 Main St", "NYC", "10001"))
        val personDynamic = personV1Schema.toDynamicValue(person)

        val migration = DynamicMigration(
          Vector(
            MigrationAction.Join(
              DynamicOptic.root.field("address").field("fullAddress"),
              Vector(
                DynamicOptic.root.field("address").field("street"),
                DynamicOptic.root.field("address").field("city")
              ),
              SchemaExpr.StringConcat(
                SchemaExpr.StringConcat(
                  SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root.field("field0")),
                  SchemaExpr.Literal[DynamicValue, String](", ", Schema.string)
                ),
                SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root.field("field1"))
              )
            )
          )
        )

        val result = migration(personDynamic)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "address")
            .get
            ._2
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "fullAddress")
            .get
            ._2 == DynamicValue.Primitive(PrimitiveValue.String("123 Main St, NYC"))
        )
      },
      test("3-level: join deeply nested fields") {
        // Join _.company.address.street + _.company.address.city - test at DynamicValue level
        val employee        = EmployeeV1("Bob", Company("Acme Inc", Address("456 Oak Ave", "LA", "90001")))
        val employeeDynamic = employeeV1Schema.toDynamicValue(employee)

        val migration = DynamicMigration(
          Vector(
            MigrationAction.Join(
              DynamicOptic.root.field("company").field("address").field("fullAddress"),
              Vector(
                DynamicOptic.root.field("company").field("address").field("street"),
                DynamicOptic.root.field("company").field("address").field("city")
              ),
              SchemaExpr.StringConcat(
                SchemaExpr.StringConcat(
                  SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root.field("field0")),
                  SchemaExpr.Literal[DynamicValue, String](", ", Schema.string)
                ),
                SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root.field("field1"))
              )
            )
          )
        )

        val result = migration(employeeDynamic)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "company")
            .get
            ._2
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "address")
            .get
            ._2
            .asInstanceOf[DynamicValue.Record]
            .fields
            .find(_._1 == "fullAddress")
            .get
            ._2 == DynamicValue.Primitive(PrimitiveValue.String("456 Oak Ave, LA"))
        )
      }
    ),
    suite("Split - Nested")(
      test("2-level: split nested field") {
        // Split _.address.fullAddress -> _.address.street + _.address.city
        val personDynamic = DynamicValue.Record(
          Vector(
            "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"     -> DynamicValue.Primitive(PrimitiveValue.Int(30)),
            "address" -> DynamicValue.Record(
              Vector(
                "fullAddress" -> DynamicValue.Primitive(PrimitiveValue.String("123 Main St, NYC")),
                "zip"         -> DynamicValue.Primitive(PrimitiveValue.String("10001"))
              )
            )
          )
        )

        val migration = DynamicMigration(
          Vector(
            MigrationAction.Split(
              DynamicOptic.root.field("address").field("fullAddress"),
              Vector(
                DynamicOptic.root.field("address").field("street"),
                DynamicOptic.root.field("address").field("city")
              ),
              SchemaExpr.StringSplit(
                SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root),
                ", "
              )
            )
          )
        )

        val result = migration(personDynamic)

        assertTrue(result.isRight) &&
        assertTrue {
          val record  = result.toOption.get.asInstanceOf[DynamicValue.Record]
          val address = record.fields.find(_._1 == "address").get._2.asInstanceOf[DynamicValue.Record]
          address.fields.find(_._1 == "street").get._2 == DynamicValue.Primitive(
            PrimitiveValue.String("123 Main St")
          ) &&
          address.fields.find(_._1 == "city").get._2 == DynamicValue.Primitive(PrimitiveValue.String("NYC"))
        }
      },
      test("3-level: split deeply nested field") {
        val employeeDynamic = DynamicValue.Record(
          Vector(
            "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
            "company" -> DynamicValue.Record(
              Vector(
                "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Acme Inc")),
                "address" -> DynamicValue.Record(
                  Vector(
                    "fullAddress" -> DynamicValue.Primitive(PrimitiveValue.String("456 Oak Ave, LA")),
                    "zip"         -> DynamicValue.Primitive(PrimitiveValue.String("90001"))
                  )
                )
              )
            )
          )
        )

        val migration = DynamicMigration(
          Vector(
            MigrationAction.Split(
              DynamicOptic.root.field("company").field("address").field("fullAddress"),
              Vector(
                DynamicOptic.root.field("company").field("address").field("street"),
                DynamicOptic.root.field("company").field("address").field("city")
              ),
              SchemaExpr.StringSplit(
                SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root),
                ", "
              )
            )
          )
        )

        val result = migration(employeeDynamic)

        assertTrue(result.isRight) &&
        assertTrue {
          val record  = result.toOption.get.asInstanceOf[DynamicValue.Record]
          val company = record.fields.find(_._1 == "company").get._2.asInstanceOf[DynamicValue.Record]
          val address = company.fields.find(_._1 == "address").get._2.asInstanceOf[DynamicValue.Record]
          address.fields.find(_._1 == "street").get._2 == DynamicValue.Primitive(
            PrimitiveValue.String("456 Oak Ave")
          ) &&
          address.fields.find(_._1 == "city").get._2 == DynamicValue.Primitive(PrimitiveValue.String("LA"))
        }
      }
    ),
    suite("TransformElements - Nested")(
      test("2-level: transform elements of nested sequence") {
        // Transform _.department.employeeIds elements (multiply by 10)
        val company = CompanyWithDept(
          "Acme Inc",
          Department("Engineering", Vector(101, 102, 103))
        )

        val migration = MigrationBuilder
          .newBuilder[CompanyWithDept, CompanyWithDept]
          .transformElements(
            DynamicOptic.root.field("department").field("employeeIds"),
            SchemaExpr.Arithmetic(
              SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
              SchemaExpr.Literal[DynamicValue, Int](10, Schema.int),
              SchemaExpr.ArithmeticOperator.Multiply,
              IsNumeric.IsInt
            )
          )
          .buildPartial

        val result = migration(company)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == CompanyWithDept("Acme Inc", Department("Engineering", Vector(1010, 1020, 1030)))
        )
      }
    ),
    suite("TransformKeys - Nested")(
      test("2-level: transform keys of nested map") {
        // Transform _.address.metadata keys to uppercase
        val person = PersonWithMetadata(
          "Alice",
          AddressWithMetadata("123 Main St", "NYC", "10001", Map("floor" -> "5", "unit" -> "B"))
        )

        val migration = MigrationBuilder
          .newBuilder[PersonWithMetadata, PersonWithMetadata]
          .transformKeys(
            DynamicOptic.root.field("address").field("metadata"),
            SchemaExpr.StringUppercase(
              SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
            )
          )
          .buildPartial

        val result = migration(person)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == PersonWithMetadata(
            "Alice",
            AddressWithMetadata("123 Main St", "NYC", "10001", Map("FLOOR" -> "5", "UNIT" -> "B"))
          )
        )
      }
    ),
    suite("TransformValues - Nested")(
      test("2-level: transform values of nested map") {
        // Transform _.address.metadata values to uppercase
        val person = PersonWithMetadata(
          "Alice",
          AddressWithMetadata("123 Main St", "NYC", "10001", Map("floor" -> "five", "unit" -> "b"))
        )

        val migration = MigrationBuilder
          .newBuilder[PersonWithMetadata, PersonWithMetadata]
          .transformValues(
            DynamicOptic.root.field("address").field("metadata"),
            SchemaExpr.StringUppercase(
              SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
            )
          )
          .buildPartial

        val result = migration(person)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == PersonWithMetadata(
            "Alice",
            AddressWithMetadata("123 Main St", "NYC", "10001", Map("floor" -> "FIVE", "unit" -> "B"))
          )
        )
      }
    ),
    suite("RenameCase - Nested")(
      test("2-level: rename case of nested variant") {
        // Rename _.address.addressType case from "Home" to "Residential" - test at DynamicValue level
        val person = PersonWithAddressType(
          "Alice",
          AddressWithType("123 Main St", "NYC", "10001", AddressType.Home)
        )
        val personDynamic = personWithAddressTypeSchema.toDynamicValue(person)

        val migration = DynamicMigration(
          Vector(
            MigrationAction.RenameCase(
              DynamicOptic.root.field("address").field("addressType"),
              "Home",
              "Residential"
            )
          )
        )

        val result = migration(personDynamic)

        assertTrue(result.isRight) &&
        assertTrue {
          val record      = result.toOption.get.asInstanceOf[DynamicValue.Record]
          val address     = record.fields.find(_._1 == "address").get._2.asInstanceOf[DynamicValue.Record]
          val addressType = address.fields.find(_._1 == "addressType").get._2.asInstanceOf[DynamicValue.Variant]
          addressType.caseName == "Residential"
        }
      }
    ),
    suite("TransformCase - Nested")(
      test("2-level: transform case of nested variant") {
        // Transform _.address.addressType "Work" case (if we had nested data in the case)
        // For this test, we'll use a simple transformation that doesn't change the value
        val person = PersonWithAddressType(
          "Alice",
          AddressWithType("123 Main St", "NYC", "10001", AddressType.Work)
        )

        val migration = MigrationBuilder
          .newBuilder[PersonWithAddressType, PersonWithAddressType]
          .transformCase(
            DynamicOptic.root.field("address").field("addressType"),
            "Work",
            Vector.empty // No nested transformations for simple case objects
          )
          .buildPartial

        val result = migration(person)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == person
        )
      }
    ),
    suite("Integration - Multiple Nested Operations")(
      test("chain multiple nested operations") {
        // Add field, rename field, transform field - all nested - test at DynamicValue level
        val person        = PersonV1("Alice", 30, Address("123 Main St", "nyc", "10001"))
        val personDynamic = personV1Schema.toDynamicValue(person)

        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root.field("address").field("country"),
              SchemaExpr.Literal[DynamicValue, String]("USA", Schema.string)
            ),
            MigrationAction.Rename(
              DynamicOptic.root.field("address").field("street"),
              "streetName"
            ),
            MigrationAction.TransformValue(
              DynamicOptic.root.field("address").field("city"),
              SchemaExpr.StringUppercase(
                SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
              )
            )
          )
        )

        val result = migration(personDynamic)

        assertTrue(result.isRight) &&
        assertTrue {
          val record  = result.toOption.get.asInstanceOf[DynamicValue.Record]
          val address = record.fields.find(_._1 == "address").get._2.asInstanceOf[DynamicValue.Record]
          address.fields.find(_._1 == "country").get._2 == DynamicValue.Primitive(PrimitiveValue.String("USA")) &&
          address.fields.find(_._1 == "streetName").get._2 == DynamicValue.Primitive(
            PrimitiveValue.String("123 Main St")
          ) &&
          address.fields.find(_._1 == "city").get._2 == DynamicValue.Primitive(PrimitiveValue.String("NYC"))
        }
      },
      test("mixed top-level and nested operations") {
        // Top-level: rename name -> fullName
        // Nested: transform _.address.city to uppercase - test at DynamicValue level
        val person        = PersonV1("Alice", 30, Address("123 Main St", "nyc", "10001"))
        val personDynamic = personV1Schema.toDynamicValue(person)

        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(
              DynamicOptic.root.field("name"),
              "fullName"
            ),
            MigrationAction.TransformValue(
              DynamicOptic.root.field("address").field("city"),
              SchemaExpr.StringUppercase(
                SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
              )
            )
          )
        )

        val result = migration(personDynamic)

        assertTrue(result.isRight) &&
        assertTrue {
          val record = result.toOption.get.asInstanceOf[DynamicValue.Record]
          record.fields.find(_._1 == "fullName").get._2 == DynamicValue.Primitive(PrimitiveValue.String("Alice")) &&
          {
            val address = record.fields.find(_._1 == "address").get._2.asInstanceOf[DynamicValue.Record]
            address.fields.find(_._1 == "city").get._2 == DynamicValue.Primitive(PrimitiveValue.String("NYC"))
          }
        }
      }
    ),
    suite("Error Handling - Nested")(
      test("error: path too deep for structure") {
        // Try to access _.address.city.street (city is String, not Record)
        val person = PersonV1("Alice", 30, Address("123 Main St", "NYC", "10001"))

        val migration = MigrationBuilder
          .newBuilder[PersonV1, PersonV2]
          .addField(
            DynamicOptic.root.field("address").field("city").field("street"),
            SchemaExpr.Literal[DynamicValue, String]("default", Schema.string)
          )
          .buildPartial

        val result = migration(person)

        assertTrue(result.isLeft)
      },
      test("error: missing intermediate record") {
        // Try to access _.missingAddress.street
        val personDynamic = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
            // No address field
          )
        )

        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root.field("address").field("country"),
              SchemaExpr.Literal[DynamicValue, String]("USA", Schema.string)
            )
          )
        )

        val result = migration(personDynamic)

        assertTrue(result.isLeft)
      }
    )
  )
}
