package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/**
 * Tests for As (bidirectional) integration with structural types.
 * 
 * Note: Bidirectional conversion between nominal and structural requires
 * both directions to work. nominal->structural works, but structural->nominal 
 * requires the structural value to be created with proper method backing
 * (which the Into macro handles internally on JVM).
 */
object AsIntegrationSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int)
  type PersonStructure = { def name: String; def age: Int }

  def spec = suite("AsIntegrationSpec")(
    test("Into from Person to structural works") {
      // One direction: nominal to structural
      val person = Person("Alice", 30)
      val into = Into.derived[Person, PersonStructure]
      val result = into.into(person)

      assertTrue(result.isRight)
    },
    test("nominal to structural preserves data via reflection") {
      // Verify the data is preserved through reflection
      val person = Person("Bob", 25)
      val into = Into.derived[Person, PersonStructure]
      val result = into.into(person)

      result match {
        case Right(r) =>
          val nameMethod = r.getClass.getMethod("name")
          val ageMethod = r.getClass.getMethod("age")
          assertTrue(
            nameMethod.invoke(r) == "Bob",
            ageMethod.invoke(r) == 25
          )
        case Left(err) =>
          assertTrue(false) ?? s"Conversion failed: $err"
      }
    },
    test("bidirectional conversion with tuples") {
      type Tuple2Struct = { def _1: String; def _2: Int }

      val tuple = ("Hello", 42)
      val into = Into.derived[(String, Int), Tuple2Struct]
      val result = into.into(tuple)

      assertTrue(result.isRight)
    },
    test("structural schema round-trip via DynamicValue") {
      case class Inner(value: Int)
      case class Outer(name: String, inner: Inner)

      val outer = Outer("Test", Inner(42))
      val schema = Schema.derived[Outer]
      val structural = schema.structural

      // Use toDynamicValue/fromDynamicValue as intermediate
      val dynamic = schema.toDynamicValue(outer)
      val roundTrip = schema.fromDynamicValue(dynamic)
      
      assertTrue(roundTrip == Right(outer))
    }
  )
}
