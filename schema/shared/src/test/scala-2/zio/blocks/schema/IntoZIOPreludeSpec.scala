package zio.blocks.schema

import zio.prelude.{Newtype, Subtype}
import zio.test._

/**
 * Comprehensive tests for Into conversions with ZIO Prelude newtypes (Scala 2).
 *
 * These tests verify that Into can handle conversions involving:
 *   - Newtype wrapping/unwrapping
 *   - Subtype wrapping/unwrapping
 *   - Newtypes in product types
 *   - Newtypes in collections
 */
object IntoZIOPreludeSpec extends ZIOSpecDefault {

  // Test newtypes
  // In Scala 2, make() is a macro in ZIO Prelude and cannot be overridden.
  // We use applyUnsafe for direct wrapping and define validate() for validation.
  // The macro will use the fallback (direct cast) which works for newtypes.
  type UserId = UserId.Type
  object UserId extends Newtype[String] {
    def applyUnsafe(s: String): UserId = s.asInstanceOf[UserId]
    // Custom validation method (macro will use direct cast fallback)
    def validate(s: String): zio.prelude.Validation[String, UserId] =
      if (s.nonEmpty && s.forall(_.isLetterOrDigit)) zio.prelude.Validation.succeed(s.asInstanceOf[UserId])
      else zio.prelude.Validation.fail(s"UserId must be non-empty alphanumeric, got: $s")
  }

  type Age = Age.Type
  object Age extends Newtype[Int] {
    def applyUnsafe(i: Int): Age = i.asInstanceOf[Age]
    // Custom validation method (macro will use direct cast fallback)
    def validate(i: Int): zio.prelude.Validation[String, Age] =
      if (i >= 0 && i <= 150) zio.prelude.Validation.succeed(i.asInstanceOf[Age])
      else zio.prelude.Validation.fail(s"Age must be between 0 and 150, got $i")
  }

  type Salary = Salary.Type
  object Salary extends Subtype[Long]

  type Count = Count.Type
  object Count extends Newtype[Int] {
    def applyUnsafe(i: Int): Count = i.asInstanceOf[Count]
  }

  // Test case classes with newtypes
  case class PersonV1(name: String, age: Int, salary: Long)
  case class PersonV2(name: String, age: Age, salary: Salary)

  case class UserV1(id: String, name: String)
  case class UserV2(id: UserId, name: String)

  case class EmployeeV1(id: String, age: Int, count: Int)
  case class EmployeeV2(id: UserId, age: Age, count: Count)

  def spec: Spec[TestEnvironment, Any] = suite("Into with ZIO Prelude Newtypes (Scala 2)")(
    suite("Newtype wrapping (underlying -> newtype)")(
      test("String -> UserId (with validation)") {
        val into = Into.derived[String, UserId]
        assertTrue(
          into.into("alice123") == Right(UserId.applyUnsafe("alice123")),
          into.into("") == Left(SchemaError.expectationMismatch(Nil, "UserId must be non-empty alphanumeric, got: ")),
          into.into("invalid!") == Left(
            SchemaError.expectationMismatch(Nil, "UserId must be non-empty alphanumeric, got: invalid!")
          )
        )
      },
      test("Int -> Age (with validation)") {
        val into = Into.derived[Int, Age]
        assertTrue(
          into.into(25) == Right(Age.applyUnsafe(25)),
          into.into(0) == Right(Age.applyUnsafe(0)),
          into.into(150) == Right(Age.applyUnsafe(150)),
          into.into(-1).isLeft,
          into.into(200).isLeft
        )
      },
      test("Int -> Count (no validation)") {
        val into = Into.derived[Int, Count]
        assertTrue(
          into.into(42) == Right(Count.applyUnsafe(42)),
          into.into(0) == Right(Count.applyUnsafe(0)),
          into.into(-100) == Right(Count.applyUnsafe(-100))
        )
      },
      test("Long -> Salary (Subtype)") {
        val into = Into.derived[Long, Salary]
        assertTrue(
          into.into(50000L) == Right(50000L.asInstanceOf[Salary]),
          into.into(0L) == Right(0L.asInstanceOf[Salary])
        )
      }
    ),
    suite("Newtype unwrapping (newtype -> underlying)")(
      test("UserId -> String") {
        val into   = Into.derived[UserId, String]
        val userId = UserId.applyUnsafe("alice123")
        assertTrue(into.into(userId) == Right("alice123"))
      },
      test("Age -> Int") {
        val into = Into.derived[Age, Int]
        val age  = Age.applyUnsafe(25)
        assertTrue(into.into(age) == Right(25))
      },
      test("Count -> Int") {
        val into  = Into.derived[Count, Int]
        val count = Count.applyUnsafe(42)
        assertTrue(into.into(count) == Right(42))
      },
      test("Salary -> Long") {
        val into   = Into.derived[Salary, Long]
        val salary = 50000L.asInstanceOf[Salary]
        assertTrue(into.into(salary) == Right(50000L))
      }
    ),
    suite("Newtypes in product types")(
      test("PersonV1 -> PersonV2 (partial newtype conversion)") {
        val into   = Into.derived[PersonV1, PersonV2]
        val person = PersonV1("Alice", 30, 50000L)
        val result = into.into(person)
        assertTrue(
          result.isRight,
          result.map(_.name) == Right("Alice"),
          result.map(_.age) == Right(Age.applyUnsafe(30)),
          result.map(_.salary) == Right(50000L.asInstanceOf[Salary])
        )
      },
      test("UserV1 -> UserV2 (newtype in first field)") {
        val into   = Into.derived[UserV1, UserV2]
        val user   = UserV1("alice123", "Alice")
        val result = into.into(user)
        assertTrue(
          result.isRight,
          result.map(_.id) == Right(UserId.applyUnsafe("alice123")),
          result.map(_.name) == Right("Alice")
        )
      },
      test("EmployeeV1 -> EmployeeV2 (multiple newtypes)") {
        val into     = Into.derived[EmployeeV1, EmployeeV2]
        val employee = EmployeeV1("bob456", 35, 10)
        val result   = into.into(employee)
        assertTrue(
          result.isRight,
          result.map(_.id) == Right(UserId.applyUnsafe("bob456")),
          result.map(_.age) == Right(Age.applyUnsafe(35)),
          result.map(_.count) == Right(Count.applyUnsafe(10))
        )
      }
    ),
    suite("Newtypes in collections")(
      test("List[String] -> List[UserId]") {
        val into    = Into.derived[List[String], List[UserId]]
        val strings = List("alice", "bob", "charlie")
        val result  = into.into(strings)
        assertTrue(
          result.isRight,
          result.map(_.size) == Right(3),
          result.map(_(0)) == Right(UserId.applyUnsafe("alice"))
        )
      },
      test("List[Int] -> List[Age]") {
        val into   = Into.derived[List[Int], List[Age]]
        val ages   = List(25, 30, 35)
        val result = into.into(ages)
        assertTrue(
          result.isRight,
          result.map(_.size) == Right(3)
        )
      },
      test("Option[String] -> Option[UserId]") {
        val into = Into.derived[Option[String], Option[UserId]]
        assertTrue(
          into.into(Some("alice")) == Right(Some(UserId.applyUnsafe("alice"))),
          into.into(None) == Right(None)
        )
      }
    ),
    suite("Newtype validation errors")(
      test("Invalid UserId in product type") {
        val into   = Into.derived[UserV1, UserV2]
        val user   = UserV1("", "Alice") // Invalid UserId
        val result = into.into(user)
        assertTrue(result.isLeft)
      },
      test("Invalid Age in product type") {
        val into   = Into.derived[PersonV1, PersonV2]
        val person = PersonV1("Alice", 200, 50000L) // Invalid Age
        val result = into.into(person)
        assertTrue(result.isLeft)
      },
      test("Invalid UserId in collection") {
        val into    = Into.derived[List[String], List[UserId]]
        val strings = List("valid", "", "invalid!")
        val result  = into.into(strings)
        assertTrue(result.isLeft) // Should fail on first invalid element
      }
    )
  )
}
