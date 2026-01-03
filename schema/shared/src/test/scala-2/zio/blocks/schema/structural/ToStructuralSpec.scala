package zio.blocks.schema.structural

import zio.test._
import zio.blocks.schema._

object ToStructuralSpec extends ZIOSpecDefault {

  // Test case classes
  case class Empty()
  case class SingleField(value: String)
  case class Person(name: String, age: Int)
  case class Address(street: String, city: String, zip: Int)
  case class PersonWithAddress(name: String, address: Address)
  case class DoubleNested(person: PersonWithAddress)
  case class WithOption(name: String, nickname: Option[String])
  case class WithList(name: String, tags: List[String])
  case class WithListOfCaseClass(name: String, addresses: List[Address])
  case class WithMap(name: String, metadata: Map[String, Int])
  case class LargeProduct(
    f1: String,
    f2: Int,
    f3: Boolean,
    f4: Double,
    f5: Long,
    f6: String,
    f7: Int,
    f8: Boolean,
    f9: Double,
    f10: Long
  )

  def spec = suite("ToStructuralSpec (Scala 2)")(
    suite("Simple Products")(
      test("empty case class") {
        val ts = ToStructural.derived[Empty]
        val s  = ts.toStructural(Empty())
        assertTrue(s.toString == "{}")
      },
      test("single field case class") {
        val ts = ToStructural.derived[SingleField]
        val s  = ts.toStructural(SingleField("hello"))
        assertTrue(
          s.value == "hello"
        )
      },
      test("two field case class") {
        val ts = ToStructural.derived[Person]
        val s  = ts.toStructural(Person("Alice", 30))
        assertTrue(
          s.name == "Alice",
          s.age == 30
        )
      },
      test("large product (10 fields)") {
        val ts = ToStructural.derived[LargeProduct]
        val s  = ts.toStructural(LargeProduct("a", 1, true, 2.0, 3L, "b", 4, false, 5.0, 6L))
        assertTrue(
          s.f1 == "a",
          s.f5 == 3L,
          s.f10 == 6L
        )
      },
      test("all primitive types") {
        case class AllPrimitives(
          b: Boolean,
          by: Byte,
          sh: Short,
          i: Int,
          l: Long,
          f: Float,
          d: Double,
          c: Char,
          str: String
        )
        val ts = ToStructural.derived[AllPrimitives]
        val s  = ts.toStructural(AllPrimitives(true, 1, 2, 3, 4L, 5.0f, 6.0, 'x', "hello"))
        assertTrue(
          s.b == true,
          s.by == 1.toByte,
          s.sh == 2.toShort,
          s.i == 3,
          s.l == 4L,
          s.f == 5.0f,
          s.d == 6.0,
          s.c == 'x',
          s.str == "hello"
        )
      }
    ),
    suite("Nested Products")(
      test("simple nesting") {
        val ts = ToStructural.derived[PersonWithAddress]
        val s  = ts.toStructural(PersonWithAddress("Alice", Address("Main St", "NYC", 10001)))
        assertTrue(
          s.name == "Alice"
        )
        // Nested access requires cast in Scala 2
        val addr = s.address.asInstanceOf[StructuralRecord]
        assertTrue(
          addr.street == "Main St",
          addr.city == "NYC",
          addr.zip == 10001
        )
      },
      test("double nesting") {
        val ts     = ToStructural.derived[DoubleNested]
        val s      = ts.toStructural(DoubleNested(PersonWithAddress("Bob", Address("Oak Ave", "LA", 90001))))
        val person = s.person.asInstanceOf[StructuralRecord]
        assertTrue(person.name == "Bob")
        val addr = person.address.asInstanceOf[StructuralRecord]
        assertTrue(addr.city == "LA")
      }
    ),
    suite("Collections")(
      test("Option[String] with Some") {
        val ts = ToStructural.derived[WithOption]
        val s  = ts.toStructural(WithOption("Alice", Some("Ali")))
        assertTrue(
          s.name == "Alice",
          s.nickname == Some("Ali")
        )
      },
      test("Option[String] with None") {
        val ts = ToStructural.derived[WithOption]
        val s  = ts.toStructural(WithOption("Bob", None))
        assertTrue(
          s.name == "Bob",
          s.nickname == None
        )
      },
      test("List[String]") {
        val ts = ToStructural.derived[WithList]
        val s  = ts.toStructural(WithList("Alice", List("a", "b", "c")))
        assertTrue(
          s.name == "Alice",
          s.tags == List("a", "b", "c")
        )
      },
      test("List[CaseClass]") {
        val ts = ToStructural.derived[WithListOfCaseClass]
        val s  = ts.toStructural(
          WithListOfCaseClass("Alice", List(Address("A", "B", 1), Address("C", "D", 2)))
        )
        assertTrue(s.name == "Alice")
        val addresses = s.addresses.asInstanceOf[List[StructuralRecord]]
        assertTrue(addresses.size == 2)
        assertTrue(addresses.head.street == "A")
      },
      test("Map[String, Int]") {
        val ts = ToStructural.derived[WithMap]
        val s  = ts.toStructural(WithMap("Alice", Map("x" -> 1, "y" -> 2)))
        assertTrue(
          s.name == "Alice",
          s.metadata == Map("x" -> 1, "y" -> 2)
        )
      }
    ),
    suite("Case Objects")(
      test("case object produces empty structural") {
        case object Singleton
        val ts = ToStructural.derived[Singleton.type]
        val s  = ts.toStructural(Singleton)
        assertTrue(s.toString == "{}")
      }
    ),
    suite("Field Access")(
      test("selectDynamic works") {
        val ts = ToStructural.derived[Person]
        val s  = ts.toStructural(Person("Alice", 30))
        assertTrue(
          s.selectDynamic("name") == "Alice",
          s.selectDynamic("age") == 30
        )
      },
      test("wrong field name throws") {
        val ts     = ToStructural.derived[Person]
        val s      = ts.toStructural(Person("Alice", 30))
        val caught =
          try {
            s.selectDynamic("nonexistent")
            false
          } catch {
            case _: NoSuchElementException => true
            case _: Throwable              => false
          }
        assertTrue(caught)
      },
      test("null field value works") {
        val ts = ToStructural.derived[Person]
        val s  = ts.toStructural(Person(null, 30))
        assertTrue(s.name == null)
      }
    ),
    suite("StructuralRecord Equality")(
      test("equal values produce equal records") {
        val ts = ToStructural.derived[Person]
        val s1 = ts.toStructural(Person("Alice", 30))
        val s2 = ts.toStructural(Person("Alice", 30))
        assertTrue(s1 == s2)
      },
      test("different values produce different records") {
        val ts = ToStructural.derived[Person]
        val s1 = ts.toStructural(Person("Alice", 30))
        val s2 = ts.toStructural(Person("Bob", 25))
        assertTrue(s1 != s2)
      },
      test("hashCode is consistent") {
        val ts = ToStructural.derived[Person]
        val s1 = ts.toStructural(Person("Alice", 30))
        val s2 = ts.toStructural(Person("Alice", 30))
        assertTrue(s1.hashCode == s2.hashCode)
      }
    ),
    suite("Tuples")(
      test("Tuple2") {
        case class WithTuple2(value: (String, Int))
        val ts    = ToStructural.derived[WithTuple2]
        val s     = ts.toStructural(WithTuple2(("hello", 42)))
        val tuple = s.value.asInstanceOf[StructuralRecord]
        assertTrue(
          tuple.selectDynamic("_1") == "hello",
          tuple.selectDynamic("_2") == 42
        )
      },
      test("Tuple3") {
        case class WithTuple3(value: (String, Int, Boolean))
        val ts    = ToStructural.derived[WithTuple3]
        val s     = ts.toStructural(WithTuple3(("a", 1, true)))
        val tuple = s.value.asInstanceOf[StructuralRecord]
        assertTrue(
          tuple.selectDynamic("_1") == "a",
          tuple.selectDynamic("_2") == 1,
          tuple.selectDynamic("_3") == true
        )
      },
      test("nested tuple with case class") {
        case class WithNestedTuple(value: (String, Address))
        val ts    = ToStructural.derived[WithNestedTuple]
        val s     = ts.toStructural(WithNestedTuple(("label", Address("Main", "NYC", 10001))))
        val tuple = s.value.asInstanceOf[StructuralRecord]
        val addr  = tuple.selectDynamic("_2").asInstanceOf[StructuralRecord]
        assertTrue(
          tuple.selectDynamic("_1") == "label",
          addr.city == "NYC"
        )
      }
    ),
    suite("Complex Combinations")(
      test("Option of nested case class") {
        case class WithOptionalAddress(name: String, address: Option[Address])
        val ts = ToStructural.derived[WithOptionalAddress]

        val s1   = ts.toStructural(WithOptionalAddress("Alice", Some(Address("Main", "NYC", 10001))))
        val addr = s1.address.asInstanceOf[Option[StructuralRecord]].get
        assertTrue(
          s1.name == "Alice",
          addr.city == "NYC"
        )

        val s2 = ts.toStructural(WithOptionalAddress("Bob", None))
        assertTrue(
          s2.name == "Bob",
          s2.address == None
        )
      },
      test("Map with case class values") {
        case class WithMapOfAddresses(addresses: Map[String, Address])
        val ts   = ToStructural.derived[WithMapOfAddresses]
        val s    = ts.toStructural(WithMapOfAddresses(Map("home" -> Address("A", "B", 1))))
        val map  = s.addresses.asInstanceOf[Map[String, StructuralRecord]]
        val home = map("home")
        assertTrue(home.street == "A")
      },
      test("deeply nested structure") {
        case class Level3(value: String)
        case class Level2(level3: Level3)
        case class Level1(level2: Level2)
        case class Root(level1: Level1)

        val ts = ToStructural.derived[Root]
        val s  = ts.toStructural(Root(Level1(Level2(Level3("deep")))))
        val l1 = s.level1.asInstanceOf[StructuralRecord]
        val l2 = l1.level2.asInstanceOf[StructuralRecord]
        val l3 = l2.level3.asInstanceOf[StructuralRecord]
        assertTrue(l3.value == "deep")
      },
      test("multiple nested fields of same type") {
        case class Order(buyer: Person, seller: Person)
        val ts     = ToStructural.derived[Order]
        val s      = ts.toStructural(Order(Person("Alice", 30), Person("Bob", 25)))
        val buyer  = s.buyer.asInstanceOf[StructuralRecord]
        val seller = s.seller.asInstanceOf[StructuralRecord]
        assertTrue(
          buyer.name == "Alice",
          buyer.age == 30,
          seller.name == "Bob",
          seller.age == 25
        )
      }
    ),
    suite("Vector and Set")(
      test("Vector[Int]") {
        case class WithVector(values: Vector[Int])
        val ts = ToStructural.derived[WithVector]
        val s  = ts.toStructural(WithVector(Vector(1, 2, 3)))
        assertTrue(s.values == Vector(1, 2, 3))
      },
      test("Set[String]") {
        case class WithSet(values: Set[String])
        val ts = ToStructural.derived[WithSet]
        val s  = ts.toStructural(WithSet(Set("a", "b", "c")))
        assertTrue(s.values == Set("a", "b", "c"))
      },
      test("Vector of case class") {
        case class WithVectorOfAddress(addresses: Vector[Address])
        val ts   = ToStructural.derived[WithVectorOfAddress]
        val s    = ts.toStructural(WithVectorOfAddress(Vector(Address("A", "B", 1))))
        val addr = s.addresses.asInstanceOf[Vector[StructuralRecord]].head
        assertTrue(addr.street == "A")
      }
    )
  )
}
