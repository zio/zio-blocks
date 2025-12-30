package zio.blocks.schema.structural

import zio.test._
import zio.blocks.schema._

/**
 * Tests for opaque type support in ToStructural (Scala 3 only).
 *
 * Opaque types should be unwrapped to their underlying type in the structural
 * representation. For example, `opaque type UserId = String` in a case class
 * field should become `String` in the structural type.
 */
object OpaqueTypeSpec extends ZIOSpecDefault {

  // Simple opaque type backed by String
  opaque type UserId = String
  object UserId {
    def apply(s: String): UserId             = s
    extension (id: UserId) def value: String = id
  }

  // Opaque type backed by Int
  opaque type Age = Int
  object Age {
    def apply(i: Int): Age              = i
    extension (age: Age) def value: Int = age
  }

  // Opaque type backed by Long
  opaque type Timestamp = Long
  object Timestamp {
    def apply(l: Long): Timestamp             = l
    extension (ts: Timestamp) def value: Long = ts
  }

  // Opaque type backed by Double
  opaque type Price = Double
  object Price {
    def apply(d: Double): Price            = d
    extension (p: Price) def value: Double = p
  }

  // Opaque type backed by Boolean
  opaque type Active = Boolean
  object Active {
    def apply(b: Boolean): Active            = b
    extension (a: Active) def value: Boolean = a
  }

  // Test case classes using opaque types
  case class User(id: UserId, name: String)
  case class UserWithAge(id: UserId, name: String, age: Age)
  case class WithTimestamp(ts: Timestamp, value: String)
  case class Product(name: String, price: Price, active: Active)
  case class MultipleOpaqueFields(userId: UserId, age: Age, ts: Timestamp)

  // Nested case class with opaque type
  case class Address(street: String, city: String)
  case class UserWithAddress(id: UserId, name: String, address: Address)

  // Collections with opaque types
  case class UserIds(ids: List[UserId])
  case class OptionalUserId(id: Option[UserId])
  case class UserIdMap(lookup: Map[String, UserId])

  def spec = suite("OpaqueTypeSpec (Scala 3)")(
    suite("Simple Opaque Types")(
      test("opaque type backed by String") {
        val ts = ToStructural.derived[User]
        val s  = ts.toStructural(User(UserId("user123"), "Alice"))
        assertTrue(
          s.id == "user123",
          s.name == "Alice"
        )
      },
      test("opaque type backed by Int") {
        val ts = ToStructural.derived[UserWithAge]
        val s  = ts.toStructural(UserWithAge(UserId("user123"), "Alice", Age(30)))
        assertTrue(
          s.id == "user123",
          s.name == "Alice",
          s.age == 30
        )
      },
      test("opaque type backed by Long") {
        val ts = ToStructural.derived[WithTimestamp]
        val s  = ts.toStructural(WithTimestamp(Timestamp(1234567890L), "event"))
        assertTrue(
          s.ts == 1234567890L,
          s.value == "event"
        )
      },
      test("opaque type backed by Double") {
        val ts = ToStructural.derived[Product]
        val s  = ts.toStructural(Product("Widget", Price(19.99), Active(true)))
        assertTrue(
          s.name == "Widget",
          s.price == 19.99,
          s.active == true
        )
      },
      test("multiple opaque type fields") {
        val ts = ToStructural.derived[MultipleOpaqueFields]
        val s  = ts.toStructural(MultipleOpaqueFields(UserId("user1"), Age(25), Timestamp(999L)))
        assertTrue(
          s.userId == "user1",
          s.age == 25,
          s.ts == 999L
        )
      }
    ),
    suite("Nested Types with Opaque Fields")(
      test("case class with opaque field and nested case class") {
        val ts = ToStructural.derived[UserWithAddress]
        val s  = ts.toStructural(UserWithAddress(UserId("u1"), "Bob", Address("Main St", "NYC")))
        assertTrue(
          s.id == "u1",
          s.name == "Bob",
          s.address.street == "Main St",
          s.address.city == "NYC"
        )
      }
    ),
    suite("Collections of Opaque Types")(
      test("List of opaque type") {
        val ts = ToStructural.derived[UserIds]
        val s  = ts.toStructural(UserIds(List(UserId("a"), UserId("b"), UserId("c"))))
        assertTrue(s.ids == List("a", "b", "c"))
      },
      test("Option of opaque type with Some") {
        val ts = ToStructural.derived[OptionalUserId]
        val s  = ts.toStructural(OptionalUserId(Some(UserId("present"))))
        assertTrue(s.id == Some("present"))
      },
      test("Option of opaque type with None") {
        val ts = ToStructural.derived[OptionalUserId]
        val s  = ts.toStructural(OptionalUserId(None))
        assertTrue(s.id == None)
      },
      test("Map with opaque type values") {
        val ts = ToStructural.derived[UserIdMap]
        val s  = ts.toStructural(UserIdMap(Map("admin" -> UserId("admin1"), "user" -> UserId("user1"))))
        assertTrue(s.lookup == Map("admin" -> "admin1", "user" -> "user1"))
      }
    ),
    suite("Field Access via selectDynamic")(
      test("selectDynamic returns unwrapped value") {
        val ts = ToStructural.derived[User]
        val s  = ts.toStructural(User(UserId("test"), "Test User"))
        assertTrue(
          s.selectDynamic("id") == "test",
          s.selectDynamic("name") == "Test User"
        )
      }
    ),
    suite("Equality")(
      test("structural records with same opaque values are equal") {
        val ts = ToStructural.derived[User]
        val s1 = ts.toStructural(User(UserId("same"), "Alice"))
        val s2 = ts.toStructural(User(UserId("same"), "Alice"))
        assertTrue(s1 == s2)
      },
      test("structural records with different opaque values are not equal") {
        val ts = ToStructural.derived[User]
        val s1 = ts.toStructural(User(UserId("id1"), "Alice"))
        val s2 = ts.toStructural(User(UserId("id2"), "Alice"))
        assertTrue(s1 != s2)
      }
    )
  )
}
