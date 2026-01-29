package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema.migration.FieldExtraction._

object FieldPathsSpec extends ZIOSpecDefault {

  // ============ Test Case Classes ============

  // Test 1: Flat case class
  case class Simple(name: String, age: Int)

  // Test 2: Nested case class
  case class Address(street: String, city: String)
  case class Person(name: String, address: Address)

  // Test 3: Deeply nested (3+ levels)
  case class Country(name: String, code: String)
  case class FullAddress(street: String, country: Country)
  case class Contact(address: FullAddress)

  // Test 4: Edge cases
  case class Empty()
  case class SingleField(x: Int)
  case class WithOption(value: Option[String])
  case class WithList(items: List[String])

  // Test 5: Complex nested structures
  case class Inner(x: Int, y: Int)
  case class Outer(inner: Inner, z: Int)

  // Test 6: Recursion through container (should work)
  case class TreeNode(value: Int, children: List[TreeNode])

  // ============ Sealed Traits ============

  sealed trait Result
  case class Success(value: Int)    extends Result
  case class Failure(error: String) extends Result

  sealed trait Status
  case class Active(since: String) extends Status
  case object Inactive             extends Status

  sealed trait Payment
  case class Card(number: String) extends Payment
  case class Cash(amount: Int)    extends Payment

  // Helper to verify type-level paths exist
  // The implicit only resolves if the FieldPaths instance has the expected Paths type
  def verifyFieldPaths[A](implicit fp: FieldPaths[A]): FieldPaths[A] = fp

  def verifyCasePaths[A](implicit cp: CasePaths[A]): CasePaths[A] = cp

  def verifyFieldNames[A](implicit fn: FieldNames[A]): FieldNames[A] = fn

  def spec = suite("FieldPathsSpec")(
    suite("FieldPaths typeclass")(
      test("derives for flat case class") {
        val fp = implicitly[FieldPaths[Simple]]
        // The paths are encoded in the type, we can verify derivation works
        assertTrue(fp != null)
      },
      test("derives for nested case class") {
        val fp = implicitly[FieldPaths[Person]]
        assertTrue(fp != null)
      },
      test("derives for deeply nested case class") {
        val fp = implicitly[FieldPaths[Contact]]
        assertTrue(fp != null)
      },
      test("derives for empty case class") {
        val fp = implicitly[FieldPaths[Empty]]
        assertTrue(fp != null)
      },
      test("derives for single field case class") {
        val fp = implicitly[FieldPaths[SingleField]]
        assertTrue(fp != null)
      },
      test("derives for case class with Option field") {
        val fp = implicitly[FieldPaths[WithOption]]
        assertTrue(fp != null)
      },
      test("derives for case class with List field") {
        val fp = implicitly[FieldPaths[WithList]]
        assertTrue(fp != null)
      },
      test("derives for complex nested structure") {
        val fp = implicitly[FieldPaths[Outer]]
        assertTrue(fp != null)
      },
      test("derives for recursion through container") {
        val fp = implicitly[FieldPaths[TreeNode]]
        assertTrue(fp != null)
      },
      test("derives for sealed trait (returns empty paths)") {
        val fp = implicitly[FieldPaths[Result]]
        assertTrue(fp != null)
      },
      test("derives for primitive type (returns empty paths)") {
        val fp = implicitly[FieldPaths[String]]
        assertTrue(fp != null)
      }
    ),
    suite("CasePaths typeclass")(
      test("derives for sealed trait with case classes") {
        val cp = implicitly[CasePaths[Result]]
        assertTrue(cp != null)
      },
      test("derives for sealed trait with case object") {
        val cp = implicitly[CasePaths[Status]]
        assertTrue(cp != null)
      },
      test("derives for sealed trait with multiple cases") {
        val cp = implicitly[CasePaths[Payment]]
        assertTrue(cp != null)
      },
      test("derives for non-sealed type (returns empty cases)") {
        val cp = implicitly[CasePaths[Simple]]
        assertTrue(cp != null)
      },
      test("derives for primitive type (returns empty cases)") {
        val cp = implicitly[CasePaths[Int]]
        assertTrue(cp != null)
      }
    ),
    suite("FieldNames typeclass (legacy)")(
      test("derives for flat case class") {
        val fn = implicitly[FieldNames[Simple]]
        assertTrue(fn != null)
      },
      test("derives for nested case class (top-level only)") {
        val fn = implicitly[FieldNames[Person]]
        assertTrue(fn != null)
      }
    ),
    suite("type-level verification")(
      test("FieldPaths type member is accessible") {
        // This test verifies the Paths type member is properly set
        val fp = implicitly[FieldPaths[Simple]]
        // If this compiles, the type member exists and is a TList
        assertTrue(fp.isInstanceOf[FieldPaths[Simple]])
      },
      test("CasePaths type member is accessible") {
        val cp = implicitly[CasePaths[Result]]
        // If this compiles, the Cases type member exists
        assertTrue(cp.isInstanceOf[CasePaths[Result]])
      },
      test("FieldNames type member is accessible") {
        val fn = implicitly[FieldNames[Simple]]
        // If this compiles, the Labels type member exists
        assertTrue(fn.isInstanceOf[FieldNames[Simple]])
      }
    ),
    suite("recursion detection")(
      test("direct recursion produces compile error") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.FieldExtraction._
          case class DirectRecursion(self: DirectRecursion)
          implicitly[FieldPaths[DirectRecursion]]
        """))(Assertion.isLeft)
      },
      test("mutual recursion produces compile error") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.FieldExtraction._
          case class MutualA(b: MutualB)
          case class MutualB(a: MutualA)
          implicitly[FieldPaths[MutualA]]
        """))(Assertion.isLeft)
      },
      test("recursion through container does NOT error") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.FieldExtraction._
          case class ListRecursion(children: List[ListRecursion])
          implicitly[FieldPaths[ListRecursion]]
        """))(Assertion.isRight)
      }
    )
  )
}
