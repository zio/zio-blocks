package zio.blocks.schema.migration

import zio.test._
import zio.test.Assertion
import zio.blocks.schema.migration.FieldExtraction._
import zio.blocks.schema.migration.TypeLevel._

object FieldExtractionSpec extends ZIOSpecDefault {

  // Test case classes
  case class Person(name: String, age: Int)
  case class Address(street: String, city: String, zipCode: String)
  case class PersonWithAddress(name: String, age: Int, address: Address)
  case class Wrapper(value: String)
  case class LargeRecord(a: String, b: Int, c: Boolean, d: Double, e: Long, f: Float)

  // For testing unchanged fields
  case class PersonV1(name: String, age: Int, email: String)
  case class PersonV2(name: String, age: Int, phone: String)

  // For testing completely different schemas
  case class SchemaA(foo: String, bar: Int)
  case class SchemaB(baz: Boolean, qux: Double)

  // For testing identical schemas
  case class IdenticalA(x: String, y: Int)
  case class IdenticalB(x: String, y: Int)

  // For deeply nested field path extraction
  case class Deep(value: String)
  case class Inner(deep: Deep)
  case class Outer(inner: Inner)

  def spec = suite("FieldExtractionSpec")(
    suite("FieldNames typeclass")(
      test("simple case class with two fields") {
        val fn = summon[FieldNames[Person]]
        summon[fn.Labels =:= ("name", "age")]
        assertTrue(true)
      },
      test("case class with three fields") {
        val fn = summon[FieldNames[Address]]
        summon[fn.Labels =:= ("street", "city", "zipCode")]
        assertTrue(true)
      },
      test("nested case class extracts top-level fields only") {
        // PersonWithAddress has name, age, address - not the nested fields
        val fn = summon[FieldNames[PersonWithAddress]]
        summon[fn.Labels =:= ("name", "age", "address")]
        assertTrue(true)
      },
      test("single field case class") {
        val fn = summon[FieldNames[Wrapper]]
        summon[fn.Labels =:= Tuple1["value"]]
        assertTrue(true)
      },
      test("case class with many fields") {
        val fn = summon[FieldNames[LargeRecord]]
        summon[fn.Labels =:= ("a", "b", "c", "d", "e", "f")]
        assertTrue(true)
      },
      test("fields are extracted in declaration order") {
        // Verify the order matches the case class definition
        val fn = summon[FieldNames[Person]]
        type Expected = ("name", "age")
        summon[fn.Labels =:= Expected]
        assertTrue(true)
      }
    ),
    suite("fieldNames runtime extraction")(
      test("simple case class") {
        val names = fieldNames[Person]
        assertTrue(names == ("name", "age"))
      },
      test("case class with three fields") {
        val names = fieldNames[Address]
        assertTrue(names == ("street", "city", "zipCode"))
      },
      test("single field case class") {
        val names = fieldNames[Wrapper]
        assertTrue(names == Tuple1("value"))
      },
      test("case class with many fields") {
        val names = fieldNames[LargeRecord]
        assertTrue(names == ("a", "b", "c", "d", "e", "f"))
      }
    ),
    suite("extractFieldName")(
      test("simple field access") {
        val fieldName = extractFieldName[Person, String](_.name)
        assertTrue(fieldName == "name")
      },
      test("different field in same class") {
        val fieldName = extractFieldName[Person, Int](_.age)
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
        val path = extractFieldPath[Person, String](_.name)
        assertTrue(path == List("name"))
      },
      test("nested field access returns full path") {
        val path = extractFieldPath[PersonWithAddress, String](_.address.street)
        assertTrue(path == List("address", "street"))
      },
      test("deeply nested access") {
        val path = extractFieldPath[Outer, String](_.inner.deep.value)
        assertTrue(path == List("inner", "deep", "value"))
      }
    ),
    suite("type-level field operations")(
      test("UnchangedFields - shared fields between versions") {
        // PersonV1 has: name, age, email
        // PersonV2 has: name, age, phone
        // Unchanged: name, age
        val fnA = summon[FieldNames[PersonV1]]
        val fnB = summon[FieldNames[PersonV2]]
        type Unchanged = TypeLevel.Intersect[fnA.Labels, fnB.Labels]
        summon[Contains[Unchanged, "name"] =:= true]
        summon[Contains[Unchanged, "age"] =:= true]
        summon[Contains[Unchanged, "email"] =:= false]
        summon[Contains[Unchanged, "phone"] =:= false]
        assertTrue(true)
      },
      test("UnchangedFields - no shared fields") {
        val fnA = summon[FieldNames[SchemaA]]
        val fnB = summon[FieldNames[SchemaB]]
        type Unchanged = TypeLevel.Intersect[fnA.Labels, fnB.Labels]
        summon[Unchanged =:= EmptyTuple]
        assertTrue(true)
      },
      test("UnchangedFields - identical schemas have all fields unchanged") {
        val fnA = summon[FieldNames[IdenticalA]]
        val fnB = summon[FieldNames[IdenticalB]]
        type Unchanged = TypeLevel.Intersect[fnA.Labels, fnB.Labels]
        summon[Contains[Unchanged, "x"] =:= true]
        summon[Contains[Unchanged, "y"] =:= true]
        assertTrue(true)
      },
      test("RemovedFields - fields in source not in target") {
        // PersonV1 has: name, age, email
        // PersonV2 has: name, age, phone
        // Removed: email
        val fnA = summon[FieldNames[PersonV1]]
        val fnB = summon[FieldNames[PersonV2]]
        type Removed = TypeLevel.Difference[fnA.Labels, fnB.Labels]
        summon[Contains[Removed, "email"] =:= true]
        summon[Contains[Removed, "name"] =:= false]
        summon[Contains[Removed, "age"] =:= false]
        summon[Contains[Removed, "phone"] =:= false]
        assertTrue(true)
      },
      test("RemovedFields - no removed fields when all exist in target") {
        val fnA = summon[FieldNames[IdenticalA]]
        val fnB = summon[FieldNames[IdenticalB]]
        type Removed = TypeLevel.Difference[fnA.Labels, fnB.Labels]
        summon[Removed =:= EmptyTuple]
        assertTrue(true)
      },
      test("RemovedFields - all fields removed when completely different") {
        val fnA = summon[FieldNames[SchemaA]]
        val fnB = summon[FieldNames[SchemaB]]
        type Removed = TypeLevel.Difference[fnA.Labels, fnB.Labels]
        summon[Contains[Removed, "foo"] =:= true]
        summon[Contains[Removed, "bar"] =:= true]
        assertTrue(true)
      },
      test("AddedFields - fields in target not in source") {
        // PersonV1 has: name, age, email
        // PersonV2 has: name, age, phone
        // Added: phone
        val fnA = summon[FieldNames[PersonV1]]
        val fnB = summon[FieldNames[PersonV2]]
        type Added = TypeLevel.Difference[fnB.Labels, fnA.Labels]
        summon[Contains[Added, "phone"] =:= true]
        summon[Contains[Added, "name"] =:= false]
        summon[Contains[Added, "age"] =:= false]
        summon[Contains[Added, "email"] =:= false]
        assertTrue(true)
      },
      test("AddedFields - no added fields when all exist in source") {
        val fnA = summon[FieldNames[IdenticalA]]
        val fnB = summon[FieldNames[IdenticalB]]
        type Added = TypeLevel.Difference[fnB.Labels, fnA.Labels]
        summon[Added =:= EmptyTuple]
        assertTrue(true)
      },
      test("AddedFields - all fields added when completely different") {
        val fnA = summon[FieldNames[SchemaA]]
        val fnB = summon[FieldNames[SchemaB]]
        type Added = TypeLevel.Difference[fnB.Labels, fnA.Labels]
        summon[Contains[Added, "baz"] =:= true]
        summon[Contains[Added, "qux"] =:= true]
        assertTrue(true)
      },
      test("added and removed are inverses") {
        // AddedFields[A, B] == RemovedFields[B, A]
        val fnA = summon[FieldNames[PersonV1]]
        val fnB = summon[FieldNames[PersonV2]]
        type AddedAB   = TypeLevel.Difference[fnB.Labels, fnA.Labels]
        type RemovedBA = TypeLevel.Difference[fnB.Labels, fnA.Labels]
        summon[TupleEquals[AddedAB, RemovedBA] =:= true]
        assertTrue(true)
      }
    ),
    suite("compile-time safety")(
      test("FieldNames requires case class with Mirror") {
        // This test verifies that non-case-class types don't work
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.FieldExtraction._
          class NotCaseClass(val x: Int)
          summon[FieldNames[NotCaseClass]]
        """))(Assertion.isLeft)
      },
      test("extractFieldName requires field access syntax") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.FieldExtraction._
          case class Foo(x: Int)
          extractFieldName[Foo, Int](f => f.x + 1)
        """))(Assertion.isLeft)
      }
    ),
    suite("integration with TypeLevel")(
      test("can use IsSubset with field tuples") {
        // Verify that the extracted field tuples work with TypeLevel operations
        val fnPerson = summon[FieldNames[Person]]
        val fnLarge  = summon[FieldNames[LargeRecord]]

        // Person fields should not be a subset of LargeRecord fields
        summon[IsSubset[fnPerson.Labels, fnLarge.Labels] =:= false]

        // Empty tuple is always a subset
        summon[IsSubset[EmptyTuple, fnPerson.Labels] =:= true]
        assertTrue(true)
      },
      test("can compute required handled fields") {
        // For migrating PersonV1 to PersonV2:
        // Required to handle = RemovedFields = ("email")
        val fnA = summon[FieldNames[PersonV1]]
        val fnB = summon[FieldNames[PersonV2]]
        type Required = TypeLevel.Difference[fnA.Labels, fnB.Labels]

        // If we've handled ("email"), we satisfy the requirement
        type Handled = Tuple1["email"]
        summon[IsSubset[Required, Handled] =:= true]
        assertTrue(true)
      },
      test("can compute required provided fields") {
        // For migrating PersonV1 to PersonV2:
        // Required to provide = AddedFields = ("phone")
        val fnA = summon[FieldNames[PersonV1]]
        val fnB = summon[FieldNames[PersonV2]]
        type Required = TypeLevel.Difference[fnB.Labels, fnA.Labels]

        // If we've provided ("phone"), we satisfy the requirement
        type Provided = Tuple1["phone"]
        summon[IsSubset[Required, Provided] =:= true]
        assertTrue(true)
      }
    )
  )
}
