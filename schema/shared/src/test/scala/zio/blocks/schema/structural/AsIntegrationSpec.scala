package zio.blocks.schema.structural

import zio.test._
import zio.blocks.schema._

object AsIntegrationSpec extends ZIOSpecDefault {
  def spec = suite("AsIntegrationSpec")(
    test("nominal to structural and back via As") {
      case class Person(name: String, age: Int)
      type PersonStructure = { def name: String; def age: Int }

      implicit val personSchema: Schema[Person]              = Schema.derived[Person]
      implicit val structuralSchema: Schema[PersonStructure] = Schema.derived[PersonStructure]

      val as       = As.derived[Person, PersonStructure]
      val original = Person("Alice", 30)

      val result = as.into(original).flatMap(as.from)

      assertTrue(result == Right(original))
    },
    test("nested structures roundtrip via As") {
      case class Address(city: String, zip: Int)
      case class Person(name: String, address: Address)
      type AddressStructure = { def city: String; def zip: Int }
      type PersonStructure  = { def name: String; def address: AddressStructure }

      implicit val addressSchema: Schema[Address]                    = Schema.derived[Address]
      implicit val personSchema: Schema[Person]                      = Schema.derived[Person]
      implicit val addressStructuralSchema: Schema[AddressStructure] = Schema.derived[AddressStructure]
      implicit val personStructuralSchema: Schema[PersonStructure]   = Schema.derived[PersonStructure]

      val as       = As.derived[Person, PersonStructure]
      val original = Person("Bob", Address("NYC", 10001))

      val result = as.into(original).flatMap(as.from)

      assertTrue(result == Right(original))
    },
    test("As with collections preserves data") {
      case class Team(name: String, members: List[String])
      type TeamStructure = { def name: String; def members: List[String] }

      implicit val teamSchema: Schema[Team]                = Schema.derived[Team]
      implicit val structuralSchema: Schema[TeamStructure] = Schema.derived[TeamStructure]

      val as       = As.derived[Team, TeamStructure]
      val original = Team("Dev", List("Alice", "Bob", "Charlie"))

      val result = as.into(original).flatMap(as.from)

      assertTrue(result == Right(original))
    },
    test("As with Option fields") {
      case class User(name: String, email: Option[String])
      type UserStructure = { def name: String; def email: Option[String] }

      implicit val userSchema: Schema[User]                = Schema.derived[User]
      implicit val structuralSchema: Schema[UserStructure] = Schema.derived[UserStructure]

      val as = As.derived[User, UserStructure]

      val withEmail    = User("Alice", Some("alice@example.com"))
      val withoutEmail = User("Bob", None)

      val result1 = as.into(withEmail).flatMap(as.from)
      val result2 = as.into(withoutEmail).flatMap(as.from)

      assertTrue(result1 == Right(withEmail)) &&
      assertTrue(result2 == Right(withoutEmail))
    },
    test("As with tuple types") {
      type TupleStructure = { def _1: String; def _2: Int; def _3: Boolean }

      implicit val tupleSchema: Schema[(String, Int, Boolean)] = Schema.derived[(String, Int, Boolean)]
      implicit val structuralSchema: Schema[TupleStructure]    = Schema.derived[TupleStructure]

      val as       = As.derived[(String, Int, Boolean), TupleStructure]
      val original = ("hello", 42, true)

      val result = as.into(original).flatMap(as.from)

      assertTrue(result == Right(original))
    },
    test("As with generic types") {
      case class Container[T](value: T, label: String)
      type ContainerStructure = { def value: Int; def label: String }

      implicit val containerSchema: Schema[Container[Int]]      = Schema.derived[Container[Int]]
      implicit val structuralSchema: Schema[ContainerStructure] = Schema.derived[ContainerStructure]

      val as       = As.derived[Container[Int], ContainerStructure]
      val original = Container(100, "test")

      val result = as.into(original).flatMap(as.from)

      assertTrue(result == Right(original))
    },
    test("As preserves all primitive types") {
      case class AllPrimitives(
        b: Boolean,
        by: Byte,
        s: Short,
        i: Int,
        l: Long,
        f: Float,
        d: Double,
        c: Char,
        str: String
      )
      type AllPrimitivesStructure = {
        def b: Boolean
        def by: Byte
        def s: Short
        def i: Int
        def l: Long
        def f: Float
        def d: Double
        def c: Char
        def str: String
      }

      implicit val schema: Schema[AllPrimitives]                    = Schema.derived[AllPrimitives]
      implicit val structuralSchema: Schema[AllPrimitivesStructure] = Schema.derived[AllPrimitivesStructure]

      val as       = As.derived[AllPrimitives, AllPrimitivesStructure]
      val original = AllPrimitives(true, 1.toByte, 2.toShort, 3, 4L, 5.5f, 6.6, 'x', "hello")

      val result = as.into(original).flatMap(as.from)

      assertTrue(result == Right(original))
    }
  )
}
