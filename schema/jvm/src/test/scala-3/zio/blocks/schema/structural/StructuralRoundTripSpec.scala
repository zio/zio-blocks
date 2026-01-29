package zio.blocks.schema.structural
import zio.blocks.schema.SchemaBaseSpec

import zio.blocks.schema._
import zio.test._

/**
 * Round-trip tests for structural schemas through DynamicValue (JVM only).
 * Tests both product and sum type structural conversions.
 */
object StructuralRoundTripSpec extends SchemaBaseSpec {

  case class Simple(x: Int, y: String)
  case class Nested(inner: Simple, flag: Boolean)
  case class WithOption(name: String, value: Option[Int])
  case class WithList(name: String, items: List[String])
  case class WithMap(name: String, data: Map[String, Int])

  sealed trait Animal
  case class Dog(name: String, breed: String)   extends Animal
  case class Cat(name: String, indoor: Boolean) extends Animal
  case object Fish                              extends Animal

  def spec = suite("StructuralRoundTripSpec")(
    suite("Product type round-trips")(
      test("simple case class round-trip") {
        val schema   = Schema.derived[Simple]
        val original = Simple(42, "test")

        val dynamic   = schema.toDynamicValue(original)
        val roundTrip = schema.fromDynamicValue(dynamic)

        assertTrue(roundTrip == Right(original))
      },
      test("nested case class round-trip") {
        val schema   = Schema.derived[Nested]
        val original = Nested(Simple(1, "inner"), true)

        val dynamic   = schema.toDynamicValue(original)
        val roundTrip = schema.fromDynamicValue(dynamic)

        assertTrue(roundTrip == Right(original))
      },
      test("case class with Option round-trip") {
        val schema    = Schema.derived[WithOption]
        val original1 = WithOption("test", Some(42))
        val original2 = WithOption("test", None)

        val roundTrip1 = schema.fromDynamicValue(schema.toDynamicValue(original1))
        val roundTrip2 = schema.fromDynamicValue(schema.toDynamicValue(original2))

        assertTrue(
          roundTrip1 == Right(original1),
          roundTrip2 == Right(original2)
        )
      },
      test("case class with List round-trip") {
        val schema   = Schema.derived[WithList]
        val original = WithList("items", List("a", "b", "c"))

        val dynamic   = schema.toDynamicValue(original)
        val roundTrip = schema.fromDynamicValue(dynamic)

        assertTrue(roundTrip == Right(original))
      },
      test("case class with Map round-trip") {
        val schema   = Schema.derived[WithMap]
        val original = WithMap("data", Map("a" -> 1, "b" -> 2))

        val dynamic   = schema.toDynamicValue(original)
        val roundTrip = schema.fromDynamicValue(dynamic)

        assertTrue(roundTrip == Right(original))
      }
    ),
    suite("Sum type round-trips")(
      test("sealed trait case class round-trip") {
        val schema      = Schema.derived[Animal]
        val dog: Animal = Dog("Rex", "German Shepherd")

        val dynamic   = schema.toDynamicValue(dog)
        val roundTrip = schema.fromDynamicValue(dynamic)

        assertTrue(roundTrip == Right(dog))
      },
      test("sealed trait case object round-trip") {
        val schema       = Schema.derived[Animal]
        val fish: Animal = Fish

        val dynamic   = schema.toDynamicValue(fish)
        val roundTrip = schema.fromDynamicValue(dynamic)

        assertTrue(roundTrip == Right(fish))
      },
      test("all sealed trait cases round-trip correctly") {
        val schema = Schema.derived[Animal]

        val dog: Animal  = Dog("Buddy", "Labrador")
        val cat: Animal  = Cat("Whiskers", true)
        val fish: Animal = Fish

        val dogResult  = schema.fromDynamicValue(schema.toDynamicValue(dog))
        val catResult  = schema.fromDynamicValue(schema.toDynamicValue(cat))
        val fishResult = schema.fromDynamicValue(schema.toDynamicValue(fish))

        assertTrue(
          dogResult == Right(dog),
          catResult == Right(cat),
          fishResult == Right(fish)
        )
      }
    ),
    suite("Type-level structural conversion")(
      test("Simple product converts to structural type") {
        typeCheck("""
          import zio.blocks.schema._
          case class Simple(x: Int, y: String)
          val schema = Schema.derived[Simple]
          val structural: Schema[{def x: Int; def y: String}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      },
      test("Animal sealed trait converts to union structural type") {
        typeCheck("""
          import zio.blocks.schema._
          sealed trait Animal
          case class Dog(name: String, breed: String) extends Animal
          case class Cat(name: String, indoor: Boolean) extends Animal
          case object Fish extends Animal
          val schema = Schema.derived[Animal]
          val structural: Schema[{def Cat: {def indoor: Boolean; def name: String}} | {def Dog: {def breed: String; def name: String}} | {def Fish: {}}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      },
      test("Nested product converts to structural type") {
        typeCheck("""
          import zio.blocks.schema._
          case class Simple(x: Int, y: String)
          case class Nested(inner: Simple, flag: Boolean)
          val schema = Schema.derived[Nested]
          val structural: Schema[{def flag: Boolean; def inner: Simple}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      }
    ),
    suite("Structural schema preserves reflect structure")(
      test("product structural schema is still a Record") {
        val schema     = Schema.derived[Simple]
        val structural = schema.structural
        val isRecord   = (structural.reflect: @unchecked) match {
          case _: Reflect.Record[_, _] => true
        }
        assertTrue(isRecord)
      },
      test("sum type structural schema is still a Variant") {
        val schema     = Schema.derived[Animal]
        val structural = schema.structural
        val isVariant  = (structural.reflect: @unchecked) match {
          case _: Reflect.Variant[_, _] => true
        }
        assertTrue(isVariant)
      },
      test("product structural schema preserves field count") {
        val schema     = Schema.derived[Nested]
        val structural = schema.structural
        val fieldCount = (structural.reflect: @unchecked) match {
          case r: Reflect.Record[_, _] => r.fields.size
        }
        assertTrue(fieldCount == 2)
      },
      test("sum type structural schema preserves case count") {
        val schema     = Schema.derived[Animal]
        val structural = schema.structural
        val caseCount  = (structural.reflect: @unchecked) match {
          case v: Reflect.Variant[_, _] => v.cases.size
        }
        assertTrue(caseCount == 3)
      }
    )
  )
}
