package zio.blocks.schema.migration

import zio.test._
import zio.test.Assertion
import zio.blocks.schema.migration.ShapeExtraction._
import zio.blocks.schema.migration.TypeLevel._

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

  // Test 5: Enum (Scala 3)
  enum Color {
    case Red, Green, Blue
  }

  // Test 6: Enum with fields
  enum Status {
    case Active(since: String)
    case Inactive
  }

  // Test 7: Edge cases
  case class Empty()
  case class SingleField(x: Int)
  case class WithOption(value: Option[String])
  case class WithList(items: List[String])
  case class WithMap(data: Map[String, Int])
  case class WithVector(elements: Vector[Double])

  // Test 8: Complex nested structures
  case class Inner(x: Int, y: Int)
  case class Outer(inner: Inner, z: Int)
  case class DeepOuter(outer: Outer, w: Int)

  // Test 9: Sealed trait with nested case class fields
  sealed trait Payment
  case class Card(number: String, expiry: String) extends Payment
  case class Cash(amount: Int)                    extends Payment
  case class BankTransfer(bank: BankInfo)         extends Payment
  case class BankInfo(name: String, swift: String)

  // Test 10: Multiple levels of nesting
  case class L5(value: String)
  case class L4(l5: L5)
  case class L3(l4: L4)
  case class L2(l3: L3)
  case class L1(l2: L2)

  // Test 11: Single field enum case
  enum SingleCaseEnum {
    case OnlyCase(data: String)
  }

  // ============ FieldPaths Test Case Classes ============

  // Flat case class for FieldPaths tests
  case class FlatPerson(name: String, age: Int)
  case class AddressWithZip(street: String, city: String, zipCode: String)
  case class PersonWithAddress(name: String, age: Int, address: AddressWithZip)
  case class Wrapper(value: String)
  case class LargeRecord(a: String, b: Int, c: Boolean, d: Double, e: Long, f: Float)

  // For deeply nested field path extraction
  case class FieldDeep(value: String)
  case class FieldInner(deep: FieldDeep)
  case class FieldOuter(inner: FieldInner)

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
      test("enum returns empty field paths") {
        val paths = extractFieldPaths[Color]
        assertTrue(paths == List())
      }
    ),
    suite("extractCaseNames")(
      test("sealed trait with case classes") {
        val names = extractCaseNames[Result]
        assertTrue(names == List("Failure", "Success"))
      },
      test("simple enum") {
        val names = extractCaseNames[Color]
        assertTrue(names == List("Blue", "Green", "Red"))
      },
      test("enum with fields") {
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
      },
      test("enum with single case") {
        val names = extractCaseNames[SingleCaseEnum]
        assertTrue(names == List("OnlyCase"))
      }
    ),
    suite("extractShape")(
      test("flat case class shape") {
        val shape = extractShape[Simple]
        assertTrue(
          shape.fieldPaths == List("age", "name"),
          shape.caseNames == List(),
          shape.caseFieldPaths == Map()
        )
      },
      test("nested case class shape") {
        val shape = extractShape[Person]
        assertTrue(
          shape.fieldPaths == List("address", "address.city", "address.street", "name"),
          shape.caseNames == List(),
          shape.caseFieldPaths == Map()
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
      test("enum shape") {
        val shape = extractShape[Color]
        assertTrue(
          shape.fieldPaths == List(),
          shape.caseNames == List("Blue", "Green", "Red"),
          // Simple enum cases have no fields
          shape.caseFieldPaths == Map(
            "Red"   -> List(),
            "Green" -> List(),
            "Blue"  -> List()
          )
        )
      },
      test("enum with fields shape") {
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
        // Recursion through containers is safe because we don't recurse into container element types
        // We just get "children" as the field path and stop there
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.ShapeExtraction._
          case class ListRecursion(children: List[ListRecursion])
          extractFieldPaths[ListRecursion]
        """))(Assertion.isRight)
      },
      test("recursion through Option does NOT error - stops at container") {
        // Recursion through containers is safe because we don't recurse into container element types
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
        // Regular classes are not product types, so they should return empty
        val paths = extractFieldPaths[String]
        assertTrue(paths == List())
      }
    ),
    suite("FieldPaths typeclass")(
      test("flat case class extracts top-level fields sorted alphabetically") {
        val fp = summon[FieldPaths[FlatPerson]]
        // Paths are sorted alphabetically: age, name
        summon[fp.Paths =:= ("age", "name")]
        assertCompletes
      },
      test("nested case class extracts all paths including nested") {
        val fp = summon[FieldPaths[PersonWithAddress]]
        // Should include: address, address.city, address.street, address.zipCode, age, name
        summon[fp.Paths =:= ("address", "address.city", "address.street", "address.zipCode", "age", "name")]
        assertCompletes
      },
      test("deeply nested case class extracts all levels") {
        val fp = summon[FieldPaths[FieldOuter]]
        // Should include: inner, inner.deep, inner.deep.value
        summon[fp.Paths =:= ("inner", "inner.deep", "inner.deep.value")]
        assertCompletes
      },
      test("single field case class") {
        val fp = summon[FieldPaths[Wrapper]]
        summon[fp.Paths =:= Tuple1["value"]]
        assertCompletes
      },
      test("nested path difference detects changes at any level") {
        // This demonstrates the key benefit of FieldPaths:
        // it can detect changes in nested structures
        @scala.annotation.nowarn("msg=unused local definition")
        case class AddressV1(street: String, city: String)
        @scala.annotation.nowarn("msg=unused local definition")
        case class AddressV2(street: String, zip: String) // city -> zip
        @scala.annotation.nowarn("msg=unused local definition")
        case class PersonV1(name: String, address: AddressV1)
        @scala.annotation.nowarn("msg=unused local definition")
        case class PersonV2(name: String, address: AddressV2)

        val fpA = summon[FieldPaths[PersonV1]]
        val fpB = summon[FieldPaths[PersonV2]]

        // PersonV1 paths: address, address.city, address.street, name
        // PersonV2 paths: address, address.street, address.zip, name
        // Difference (in V1 but not V2): address.city
        // Difference (in V2 but not V1): address.zip
        type Removed = Difference[fpA.Paths, fpB.Paths]
        type Added   = Difference[fpB.Paths, fpA.Paths]

        summon[Contains[Removed, "address.city"] =:= true]
        summon[Contains[Added, "address.zip"] =:= true]
        assertCompletes
      }
    ),
    suite("extractFieldName")(
      test("simple field access") {
        val fieldName = extractFieldName[FlatPerson, String](_.name)
        assertTrue(fieldName == "name")
      },
      test("different field in same class") {
        val fieldName = extractFieldName[FlatPerson, Int](_.age)
        assertTrue(fieldName == "age")
      },
      test("nested field access returns top-level field") {
        // When accessing _.address.street, we get "address" (top-level)
        val fieldName = extractFieldName[PersonWithAddress, String](_.address.street)
        assertTrue(fieldName == "address")
      },
      test("single field case class") {
        val fieldName = extractFieldName[Wrapper, String](_.value)
        assertTrue(fieldName == "value")
      },
      test("field from large record") {
        val fieldName = extractFieldName[LargeRecord, Double](_.d)
        assertTrue(fieldName == "d")
      }
    ),
    suite("extractFieldPath")(
      test("simple field access") {
        val path = extractFieldPath[FlatPerson, String](_.name)
        assertTrue(path == List("name"))
      },
      test("nested field access returns full path") {
        val path = extractFieldPath[PersonWithAddress, String](_.address.street)
        assertTrue(path == List("address", "street"))
      },
      test("deeply nested access") {
        val path = extractFieldPath[FieldOuter, String](_.inner.deep.value)
        assertTrue(path == List("inner", "deep", "value"))
      }
    ),
    suite("extractFieldName compile-time safety")(
      test("extractFieldName requires field access syntax") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.ShapeExtraction._
          case class Foo(x: Int)
          extractFieldName[Foo, Int](f => f.x + 1)
        """))(Assertion.isLeft)
      }
    )
  )
}
