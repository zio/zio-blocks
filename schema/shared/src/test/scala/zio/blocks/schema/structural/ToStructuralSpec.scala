package zio.blocks.schema.structural

import zio.test._
import zio.blocks.schema._

object ToStructuralSpec extends ZIOSpecDefault {

  // Test case classes
  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Address(street: String, city: String, zip: Int)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class Employee(person: Person, department: String)
  object Employee {
    implicit val schema: Schema[Employee] = Schema.derived
  }

  case class Point(x: Double, y: Double)
  object Point {
    implicit val schema: Schema[Point] = Schema.derived
  }

  case class Empty()
  object Empty {
    implicit val schema: Schema[Empty] = Schema.derived
  }

  case class SingleField(value: String)
  object SingleField {
    implicit val schema: Schema[SingleField] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("ToStructuralSpec")(
    suite("ToStructural derivation from Schema")(
      test("derives ToStructural for simple case class") {
        val ts = ToStructural[Person]
        assertTrue(ts != null)
      },
      test("generates correct structural type name for Person") {
        val ts = ToStructural[Person]
        assertTrue(ts.structuralTypeName == "{age:Int,name:String}")
      },
      test("generates correct structural type name for Address") {
        val ts = ToStructural[Address]
        assertTrue(ts.structuralTypeName == "{city:String,street:String,zip:Int}")
      },
      test("generates correct structural type name for Point") {
        val ts = ToStructural[Point]
        assertTrue(ts.structuralTypeName == "{x:Double,y:Double}")
      },
      test("handles empty case class") {
        val ts = ToStructural[Empty]
        assertTrue(ts.structuralTypeName == "{}")
      },
      test("handles single field case class") {
        val ts = ToStructural[SingleField]
        assertTrue(ts.structuralTypeName == "{value:String}")
      }
    ),
    suite("toStructural conversion")(
      test("converts Person to StructuralValue") {
        val person = Person("Alice", 30)
        val ts     = ToStructural[Person]
        val sv     = ts.toStructural(person)
        assertTrue(
          sv.typeName == "{age:Int,name:String}" &&
            sv.selectDynamic("name") == Right("Alice") &&
            sv.selectDynamic("age") == Right(30)
        )
      },
      test("converts Address to StructuralValue") {
        val address = Address("123 Main St", "Springfield", 12345)
        val ts      = ToStructural[Address]
        val sv      = ts.toStructural(address)
        assertTrue(
          sv.selectDynamic("street") == Right("123 Main St") &&
            sv.selectDynamic("city") == Right("Springfield") &&
            sv.selectDynamic("zip") == Right(12345)
        )
      },
      test("converts Point to StructuralValue") {
        val point = Point(1.5, 2.5)
        val ts    = ToStructural[Point]
        val sv    = ts.toStructural(point)
        assertTrue(
          sv.selectDynamic("x") == Right(1.5) &&
            sv.selectDynamic("y") == Right(2.5)
        )
      }
    ),
    suite("fromStructural conversion")(
      test("round-trips Person through structural") {
        val person = Person("Bob", 25)
        val ts     = ToStructural[Person]
        val sv     = ts.toStructural(person)
        val result = ts.fromStructural(sv)
        assertTrue(result == Right(person))
      },
      test("round-trips Address through structural") {
        val address = Address("456 Oak Ave", "Portland", 97201)
        val ts      = ToStructural[Address]
        val sv      = ts.toStructural(address)
        val result  = ts.fromStructural(sv)
        assertTrue(result == Right(address))
      },
      test("round-trips Point through structural") {
        val point  = Point(3.14, 2.71)
        val ts     = ToStructural[Point]
        val sv     = ts.toStructural(point)
        val result = ts.fromStructural(sv)
        assertTrue(result == Right(point))
      },
      test("round-trips Empty through structural") {
        val empty  = Empty()
        val ts     = ToStructural[Empty]
        val sv     = ts.toStructural(empty)
        val result = ts.fromStructural(sv)
        assertTrue(result == Right(empty))
      }
    ),
    suite("structuralSchema")(
      test("returns a valid Schema for StructuralValue") {
        val ts     = ToStructural[Person]
        val schema = ts.structuralSchema
        assertTrue(schema != null)
      },
      test("structuralSchema can convert to DynamicValue") {
        val person = Person("Charlie", 35)
        val ts     = ToStructural[Person]
        val sv     = ts.toStructural(person)
        val dv     = ts.structuralSchema.toDynamicValue(sv)
        assertTrue(dv.isInstanceOf[DynamicValue])
      }
    ),
    suite("extension methods")(
      test("toStructural extension works on values") {
        val person = Person("Diana", 28)
        val sv     = person.toStructural
        assertTrue(sv.typeName == "{age:Int,name:String}")
      },
      test("structuralTypeName extension works on Schema") {
        val schema   = Schema[Person]
        val typeName = schema.structuralTypeName
        assertTrue(typeName == "{age:Int,name:String}")
      }
    ),
    suite("nested types")(
      test("handles nested case classes") {
        val employee = Employee(Person("Eve", 40), "Engineering")
        val ts       = ToStructural[Employee]
        val sv       = ts.toStructural(employee)
        assertTrue(
          sv.selectDynamic("department") == Right("Engineering")
        )
      }
    ),
    suite("primitive types")(
      test("handles String schema") {
        val ts = ToStructural[String]
        val sv = ts.toStructural("hello")
        assertTrue(sv.selectDynamic("value") == Right("hello"))
      },
      test("handles Int schema") {
        val ts = ToStructural[Int]
        val sv = ts.toStructural(42)
        assertTrue(sv.selectDynamic("value") == Right(42))
      },
      test("handles Boolean schema") {
        val ts = ToStructural[Boolean]
        val sv = ts.toStructural(true)
        assertTrue(sv.selectDynamic("value") == Right(true))
      }
    )
  )
}
