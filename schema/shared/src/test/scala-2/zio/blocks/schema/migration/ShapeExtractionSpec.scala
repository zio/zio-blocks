package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema.migration.ShapeExtraction._

object ShapeExtractionSpec extends ZIOSpecDefault {

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

  // Test 4: Sealed trait
  sealed trait Result
  case class Success(value: Int)    extends Result
  case class Failure(error: String) extends Result

  // Test 5: Sealed trait with case object
  sealed trait Status
  case class Active(since: String) extends Status
  case object Inactive             extends Status

  // Test 6: Edge cases
  case class Empty()
  case class SingleField(x: Int)
  case class WithOption(value: Option[String])
  case class WithList(items: List[String])
  case class WithMap(data: Map[String, Int])
  case class WithVector(elements: Vector[Double])

  // Test 7: Complex nested structures
  case class Inner(x: Int, y: Int)
  case class Outer(inner: Inner, z: Int)
  case class DeepOuter(outer: Outer, w: Int)

  // Test 8: Sealed trait with nested case class fields
  sealed trait Payment
  case class Card(number: String, expiry: String) extends Payment
  case class Cash(amount: Int)                    extends Payment
  case class BankTransfer(bank: BankInfo)         extends Payment
  case class BankInfo(name: String, swift: String)

  // Test 9: Multiple levels of nesting
  case class L5(value: String)
  case class L4(l5: L5)
  case class L3(l4: L4)
  case class L2(l3: L3)
  case class L1(l2: L2)

  // Test 10: Recursion through container (should NOT error)
  case class TreeNode(value: Int, children: List[TreeNode])

  def spec = suite("ShapeExtractionSpec")(
    suite("extractFieldPaths")(
      test("flat case class") {
        val paths = extractFieldPaths[Simple]
        assertTrue(
          paths == List("age", "name")
        )
      },
      test("nested case class") {
        val paths = extractFieldPaths[Person]
        assertTrue(
          paths == List("address", "address.city", "address.street", "name")
        )
      },
      test("deeply nested case class (3 levels)") {
        val paths = extractFieldPaths[Contact]
        assertTrue(
          paths == List(
            "address",
            "address.country",
            "address.country.code",
            "address.country.name",
            "address.street"
          )
        )
      },
      test("empty case class") {
        val paths = extractFieldPaths[Empty]
        assertTrue(paths == List())
      },
      test("single field case class") {
        val paths = extractFieldPaths[SingleField]
        assertTrue(paths == List("x"))
      },
      test("case class with Option field - does not recurse into Option") {
        val paths = extractFieldPaths[WithOption]
        assertTrue(paths == List("value"))
      },
      test("case class with List field - does not recurse into List") {
        val paths = extractFieldPaths[WithList]
        assertTrue(paths == List("items"))
      },
      test("case class with Map field - does not recurse into Map") {
        val paths = extractFieldPaths[WithMap]
        assertTrue(paths == List("data"))
      },
      test("case class with Vector field - does not recurse into Vector") {
        val paths = extractFieldPaths[WithVector]
        assertTrue(paths == List("elements"))
      },
      test("complex nested structure") {
        val paths = extractFieldPaths[DeepOuter]
        assertTrue(
          paths == List(
            "outer",
            "outer.inner",
            "outer.inner.x",
            "outer.inner.y",
            "outer.z",
            "w"
          )
        )
      },
      test("five levels of nesting") {
        val paths = extractFieldPaths[L1]
        assertTrue(
          paths == List(
            "l2",
            "l2.l3",
            "l2.l3.l4",
            "l2.l3.l4.l5",
            "l2.l3.l4.l5.value"
          )
        )
      },
      test("sealed trait returns empty field paths") {
        val paths = extractFieldPaths[Result]
        assertTrue(paths == List())
      },
      test("recursion through List does NOT error - stops at container") {
        val paths = extractFieldPaths[TreeNode]
        assertTrue(paths == List("children", "value"))
      }
    ),
    suite("extractCaseNames")(
      test("sealed trait with case classes") {
        val names = extractCaseNames[Result]
        assertTrue(names == List("Failure", "Success"))
      },
      test("sealed trait with case object") {
        val names = extractCaseNames[Status]
        assertTrue(names == List("Active", "Inactive"))
      },
      test("non-sealed type returns empty list") {
        val names = extractCaseNames[Simple]
        assertTrue(names == List())
      },
      test("sealed trait with three cases") {
        val names = extractCaseNames[Payment]
        assertTrue(names == List("BankTransfer", "Card", "Cash"))
      }
    ),
    suite("extractShape")(
      test("flat case class shape") {
        val shape = extractShape[Simple]
        assertTrue(
          shape.fieldPaths == List("age", "name"),
          shape.caseNames == List.empty[String],
          shape.caseFieldPaths == Map.empty[String, List[String]]
        )
      },
      test("nested case class shape") {
        val shape = extractShape[Person]
        assertTrue(
          shape.fieldPaths == List("address", "address.city", "address.street", "name"),
          shape.caseNames == List.empty[String],
          shape.caseFieldPaths == Map.empty[String, List[String]]
        )
      },
      test("sealed trait shape") {
        val shape = extractShape[Result]
        assertTrue(
          shape.fieldPaths == List(),
          shape.caseNames == List("Failure", "Success"),
          shape.caseFieldPaths == Map(
            "Success" -> List("value"),
            "Failure" -> List("error")
          )
        )
      },
      test("sealed trait with case object shape") {
        val shape = extractShape[Status]
        assertTrue(
          shape.fieldPaths == List(),
          shape.caseNames == List("Active", "Inactive"),
          shape.caseFieldPaths == Map(
            "Active"   -> List("since"),
            "Inactive" -> List()
          )
        )
      },
      test("sealed trait with nested fields in cases") {
        val shape = extractShape[Payment]
        assertTrue(
          shape.fieldPaths == List(),
          shape.caseNames == List("BankTransfer", "Card", "Cash"),
          shape.caseFieldPaths == Map(
            "Card"         -> List("expiry", "number"),
            "Cash"         -> List("amount"),
            "BankTransfer" -> List("bank", "bank.name", "bank.swift")
          )
        )
      }
    ),
    suite("recursion detection")(
      test("direct recursion produces compile error") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.ShapeExtraction._
          case class DirectRecursion(self: DirectRecursion)
          extractFieldPaths[DirectRecursion]
        """))(Assertion.isLeft)
      },
      test("recursion through List does NOT error - stops at container") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.ShapeExtraction._
          case class ListRecursion(children: List[ListRecursion])
          extractFieldPaths[ListRecursion]
        """))(Assertion.isRight)
      },
      test("recursion through Option does NOT error - stops at container") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.ShapeExtraction._
          case class OptionRecursion(next: Option[OptionRecursion])
          extractFieldPaths[OptionRecursion]
        """))(Assertion.isRight)
      },
      test("mutual recursion produces compile error") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.ShapeExtraction._
          case class MutualA(b: MutualB)
          case class MutualB(a: MutualA)
          extractFieldPaths[MutualA]
        """))(Assertion.isLeft)
      }
    ),
    suite("compile-time safety")(
      test("non-case-class types return empty field paths") {
        val paths = extractFieldPaths[String]
        assertTrue(paths == List())
      }
    )
  )
}
