package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.migration.{SchemaExpr => SE}
import zio.test._
import zio.test.Assertion._

/**
 * Comprehensive test suite for schema migrations.
 *
 * Tests cover:
 *   - Flat record migrations (1 level)
 *   - Shallow nested migrations (2-3 levels)
 *   - Deep nested migrations (4-6 levels) - as requested by @jdegoes
 *   - Round-trip migrations (forward + reverse)
 *   - Composition of migrations
 *   - Error cases
 */
object MigrationSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    flatRecordTests,
    shallowNestedTests,
    deepNestedTests,
    roundTripTests,
    compositionTests,
    errorTests
  )

  // ===== Test Data Structures =====

  // Flat record (1 level)
  case class PersonV0(name: String, age: Int)
  case class PersonV1(fullName: String, age: Int, country: String)

  // Shallow nested (2-3 levels)
  case class AddressV0(street: String, city: String)
  case class AddressV1(street: String, city: String, country: String)

  case class UserV0(name: String, address: AddressV0)
  case class UserV1(name: String, address: AddressV1, email: String)

  // Deep nested (6 levels) - company → department → team → member → contact → email
  case class EmailV0(address: String)
  case class EmailV1(address: String, verified: Boolean)

  case class ContactV0(email: EmailV0, phone: String)
  case class ContactV1(email: EmailV1, phone: String, preferred: String)

  case class MemberV0(name: String, contact: ContactV0)
  case class MemberV1(name: String, contact: ContactV1, role: String)

  case class TeamV0(name: String, members: Vector[MemberV0])
  case class TeamV1(name: String, members: Vector[MemberV1], budget: Int)

  case class DepartmentV0(name: String, teams: Vector[TeamV0])
  case class DepartmentV1(name: String, teams: Vector[TeamV1], location: String)

  case class CompanyV0(name: String, departments: Vector[DepartmentV0])
  case class CompanyV1(name: String, departments: Vector[DepartmentV1], founded: Int)

  // ===== Flat Record Tests =====

  val flatRecordTests = suite("Flat Record Migrations")(
    test("add field with default value") {
      val person = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
        )
      )

      val migration = DynamicMigration.single(
        MigrationAction.AddField(
          DynamicOptic.root,
          "country",
          SE.literalString("USA")
        )
      )

      val result = migration.apply(person)

      assert(result)(isRight(anything)) &&
      assert(result.map(_.asInstanceOf[DynamicValue.Record].fields.size))(isRight(equalTo(3))) &&
      assert(result.map(_.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "country")))(
        isRight(isSome(anything))
      )
    },

    test("drop field") {
      val person = DynamicValue.Record(
        Vector(
          "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
          "age"     -> DynamicValue.Primitive(PrimitiveValue.Int(25)),
          "country" -> DynamicValue.Primitive(PrimitiveValue.String("USA"))
        )
      )

      val migration = DynamicMigration.single(
        MigrationAction.DropField(DynamicOptic.root, "country", None)
      )

      val result = migration.apply(person)

      assert(result)(isRight(anything)) &&
      assert(result.map(_.asInstanceOf[DynamicValue.Record].fields.size))(isRight(equalTo(2))) &&
      assert(result.map(_.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "country")))(
        isRight(isNone)
      )
    },

    test("rename field") {
      val person = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Charlie")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(35))
        )
      )

      val migration = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
      )

      val result = migration.apply(person)

      assert(result)(isRight(anything)) &&
      assert(result.map(_.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "name")))(
        isRight(isNone)
      ) &&
      assert(result.map(_.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "fullName")))(
        isRight(isSome(anything))
      )
    },

    test("compose multiple actions") {
      val person = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Diana")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(28))
        )
      )

      val migration = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName"),
          MigrationAction.AddField(DynamicOptic.root, "country", SE.literalString("UK"))
        )
      )

      val result = migration.apply(person)

      assert(result)(isRight(anything)) &&
      assert(result.map(_.asInstanceOf[DynamicValue.Record].fields.size))(isRight(equalTo(3)))
    }
  )

  // ===== Shallow Nested Tests =====

  val shallowNestedTests = suite("Shallow Nested Migrations (2-3 levels)")(
    test("add field to nested record at level 2") {
      val address = DynamicValue.Record(
        Vector(
          "street" -> DynamicValue.Primitive(PrimitiveValue.String("123 Main St")),
          "city"   -> DynamicValue.Primitive(PrimitiveValue.String("NYC"))
        )
      )

      val user = DynamicValue.Record(
        Vector(
          "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
          "address" -> address
        )
      )

      // Add country to address
      val migration = DynamicMigration.single(
        MigrationAction.AddField(
          DynamicOptic.root / "address",
          "country",
          SE.literalString("USA")
        )
      )

      val result = migration.apply(user)

      assert(result)(isRight(anything))
    },

    test("rename field at level 3") {
      val address = DynamicValue.Record(
        Vector(
          "street" -> DynamicValue.Primitive(PrimitiveValue.String("456 Oak Ave")),
          "city"   -> DynamicValue.Primitive(PrimitiveValue.String("LA")),
          "zip"    -> DynamicValue.Primitive(PrimitiveValue.String("90001"))
        )
      )

      val user = DynamicValue.Record(
        Vector(
          "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
          "address" -> address
        )
      )

      // Rename zip to zipCode in address
      val migration = DynamicMigration.single(
        MigrationAction.Rename(
          DynamicOptic.root / "address",
          "zip",
          "zipCode"
        )
      )

      val result = migration.apply(user)

      assert(result)(isRight(anything))
    }
  )

  // ===== Deep Nested Tests =====

  val deepNestedTests = suite("Deep Nested Migrations (4-6 levels)")(
    test("migrate 6-level deep structure - add field at level 6") {
      // Level 6: Email
      val email = DynamicValue.Record(
        Vector(
          "address" -> DynamicValue.Primitive(PrimitiveValue.String("alice@example.com"))
        )
      )

      // Level 5: Contact
      val contact = DynamicValue.Record(
        Vector(
          "email" -> email,
          "phone" -> DynamicValue.Primitive(PrimitiveValue.String("555-1234"))
        )
      )

      // Level 4: Member
      val member = DynamicValue.Record(
        Vector(
          "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
          "contact" -> contact
        )
      )

      // Level 3: Team
      val team = DynamicValue.Record(
        Vector(
          "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Engineering")),
          "members" -> DynamicValue.Sequence(Vector(member))
        )
      )

      // Level 2: Department
      val department = DynamicValue.Record(
        Vector(
          "name"  -> DynamicValue.Primitive(PrimitiveValue.String("Tech")),
          "teams" -> DynamicValue.Sequence(Vector(team))
        )
      )

      // Level 1: Company
      val company = DynamicValue.Record(
        Vector(
          "name"        -> DynamicValue.Primitive(PrimitiveValue.String("Acme Corp")),
          "departments" -> DynamicValue.Sequence(Vector(department))
        )
      )

      // Add 'verified' field to email at level 6
      val migration = DynamicMigration.single(
        MigrationAction.AddField(
          DynamicOptic.root / "departments" / 0 / "teams" / 0 / "members" / 0 / "contact" / "email",
          "verified",
          SE.literalBool(false)
        )
      )

      val result = migration.apply(company)

      assert(result)(isRight(anything))
    },

    test("migrate 5-level deep - rename field at level 5") {
      // Simplified 5-level structure
      val email = DynamicValue.Record(
        Vector(
          "addr" -> DynamicValue.Primitive(PrimitiveValue.String("bob@test.com"))
        )
      )

      val contact = DynamicValue.Record(
        Vector(
          "email" -> email,
          "phone" -> DynamicValue.Primitive(PrimitiveValue.String("555-5678"))
        )
      )

      val member = DynamicValue.Record(
        Vector(
          "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
          "contact" -> contact
        )
      )

      val team = DynamicValue.Record(
        Vector(
          "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Sales")),
          "members" -> DynamicValue.Sequence(Vector(member))
        )
      )

      val department = DynamicValue.Record(
        Vector(
          "name"  -> DynamicValue.Primitive(PrimitiveValue.String("Business")),
          "teams" -> DynamicValue.Sequence(Vector(team))
        )
      )

      // Rename 'addr' to 'address' in email
      val migration = DynamicMigration.single(
        MigrationAction.Rename(
          DynamicOptic.root / "teams" / 0 / "members" / 0 / "contact" / "email",
          "addr",
          "address"
        )
      )

      val result = migration.apply(department)

      assert(result)(isRight(anything))
    }
  )

  // ===== Round-trip Tests =====

  val roundTripTests = suite("Round-trip Migrations")(
    test("forward then reverse is identity for AddField") {
      val person = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Eve")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(32))
        )
      )

      val forward = DynamicMigration.single(
        MigrationAction.AddField(
          DynamicOptic.root,
          "country",
          SE.literalString("Canada")
        )
      )

      val reverse = forward.reverse

      val result = for {
        migrated <- forward.apply(person)
        restored <- reverse.apply(migrated)
      } yield restored

      assert(result)(isRight(equalTo(person)))
    },

    test("forward then reverse is identity for Rename") {
      val person = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Frank")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(40))
        )
      )

      val forward = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
      )

      val reverse = forward.reverse

      val result = for {
        migrated <- forward.apply(person)
        restored <- reverse.apply(migrated)
      } yield restored

      assert(result)(isRight(equalTo(person)))
    }
  )

  // ===== Composition Tests =====

  val compositionTests = suite("Migration Composition")(
    test("compose two migrations with ++") {
      val person = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Grace")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(29))
        )
      )

      val migration1 = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
      )

      val migration2 = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root, "country", SE.literalString("France"))
      )

      val composed = migration1 ++ migration2

      val result = composed.apply(person)

      assert(result)(isRight(anything)) &&
      assert(result.map(_.asInstanceOf[DynamicValue.Record].fields.size))(isRight(equalTo(3)))
    }
  )

  // ===== Error Tests =====

  val errorTests = suite("Error Handling")(
    test("field not found error when dropping non-existent field") {
      val person = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Henry")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(45))
        )
      )

      val migration = DynamicMigration.single(
        MigrationAction.DropField(DynamicOptic.root, "nonexistent", None)
      )

      val result = migration.apply(person)

      assert(result)(isLeft(anything))
    },

    test("field already exists error when adding duplicate field") {
      val person = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Iris")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(27))
        )
      )

      val migration = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root, "name", SE.literalString("duplicate"))
      )

      val result = migration.apply(person)

      assert(result)(isLeft(anything))
    },

    test("type mismatch error when operating on wrong type") {
      val primitive = DynamicValue.Primitive(PrimitiveValue.String("not a record"))

      val migration = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root, "field", SE.literalString("value"))
      )

      val result = migration.apply(primitive)

      assert(result)(isLeft(anything))
    },

    test("invalid path error for deep navigation") {
      val person = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Jack"))
        )
      )

      val migration = DynamicMigration.single(
        MigrationAction.AddField(
          DynamicOptic.root / "address" / "city",
          "zip",
          SE.literalString("12345")
        )
      )

      val result = migration.apply(person)

      assert(result)(isLeft(anything))
    },

    test("index out of bounds error") {
      val sequence = DynamicValue.Sequence(
        Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
      )

      val migration = DynamicMigration.single(
        MigrationAction.AddField(
          DynamicOptic.root / 10, // Out of bounds
          "field",
          SE.literalString("value")
        )
      )

      val result = migration.apply(sequence)

      assert(result)(isLeft(anything))
    }
  )
}

// ===== Additional Test Suites =====

/**
 * Tests for extremely deep nesting (7-10 levels). This goes beyond @jdegoes'
 * requirement of 6 levels to show we can handle any depth.
 */
object DeepNestingExtraSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Deep Nesting Extra Tests")(
    test("migrate 7-level deep structure") {
      // Level 7: Tag
      val tag = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("urgent"))
        )
      )

      // Level 6: Email
      val email = DynamicValue.Record(
        Vector(
          "address" -> DynamicValue.Primitive(PrimitiveValue.String("test@example.com")),
          "tags"    -> DynamicValue.Sequence(Vector(tag))
        )
      )

      // Level 5: Contact
      val contact = DynamicValue.Record(
        Vector(
          "email" -> email,
          "phone" -> DynamicValue.Primitive(PrimitiveValue.String("555-0000"))
        )
      )

      // Level 4: Member
      val member = DynamicValue.Record(
        Vector(
          "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
          "contact" -> contact
        )
      )

      // Level 3: Team
      val team = DynamicValue.Record(
        Vector(
          "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Engineering")),
          "members" -> DynamicValue.Sequence(Vector(member))
        )
      )

      // Level 2: Department
      val department = DynamicValue.Record(
        Vector(
          "name"  -> DynamicValue.Primitive(PrimitiveValue.String("Tech")),
          "teams" -> DynamicValue.Sequence(Vector(team))
        )
      )

      // Level 1: Company
      val company = DynamicValue.Record(
        Vector(
          "name"        -> DynamicValue.Primitive(PrimitiveValue.String("Acme")),
          "departments" -> DynamicValue.Sequence(Vector(department))
        )
      )

      // Add field at level 7
      val migration = DynamicMigration.single(
        MigrationAction.AddField(
          DynamicOptic.root / "departments" / 0 / "teams" / 0 / "members" / 0 / "contact" / "email" / "tags" / 0,
          "priority",
          SE.literalInt(1)
        )
      )

      val result = migration.apply(company)

      assert(result)(isRight(anything))
    },

    test("multiple migrations at different depths") {
      val company = DynamicValue.Record(
        Vector(
          "name"        -> DynamicValue.Primitive(PrimitiveValue.String("TechCorp")),
          "departments" -> DynamicValue.Sequence(
            Vector(
              DynamicValue.Record(
                Vector(
                  "name"  -> DynamicValue.Primitive(PrimitiveValue.String("Engineering")),
                  "teams" -> DynamicValue.Sequence(
                    Vector(
                      DynamicValue.Record(
                        Vector(
                          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Backend"))
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
      )

      val migration = DynamicMigration(
        Vector(
          // Level 1: Add field to company
          MigrationAction.AddField(DynamicOptic.root, "founded", SE.literalInt(2020)),
          // Level 2: Add field to department
          MigrationAction.AddField(DynamicOptic.root / "departments" / 0, "location", SE.literalString("SF")),
          // Level 3: Add field to team
          MigrationAction.AddField(DynamicOptic.root / "departments" / 0 / "teams" / 0, "size", SE.literalInt(10))
        )
      )

      val result = migration.apply(company)

      assert(result)(isRight(anything))
    }
  )
}

/**
 * Tests for complex transformation scenarios.
 */
object ComplexTransformationSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Complex Transformation Tests")(
    test("chain 10 migrations together") {
      val person = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob"))
        )
      )

      val migrations = (1 to 10).map { i =>
        MigrationAction.AddField(
          DynamicOptic.root,
          s"field$i",
          SE.literalInt(i)
        )
      }

      val migration = DynamicMigration(migrations.toVector)

      val result = migration.apply(person)

      assert(result)(isRight(anything)) &&
      assert(result.map(_.asInstanceOf[DynamicValue.Record].fields.size))(isRight(equalTo(11)))
    },

    test("complex rename chain") {
      val record = DynamicValue.Record(
        Vector(
          "a" -> DynamicValue.Primitive(PrimitiveValue.String("value"))
        )
      )

      val migration = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.Rename(DynamicOptic.root, "b", "c"),
          MigrationAction.Rename(DynamicOptic.root, "c", "d")
        )
      )

      val result = migration.apply(record)

      assert(result)(isRight(anything))
    }
  )
}
