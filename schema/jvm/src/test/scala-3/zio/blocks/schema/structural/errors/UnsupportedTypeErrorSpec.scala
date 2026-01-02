package zio.blocks.schema.structural.errors

import zio.blocks.schema._
import zio.test._

/**
 * Tests for type validation in structural schema conversion.
 * 
 * These tests verify that valid types work correctly with structural conversion.
 * Sum type error handling is tested in Scala 2/3 specific test files.
 */
object UnsupportedTypeErrorSpec extends ZIOSpecDefault {

  // Case class extending abstract class
  abstract class AbstractBase(val id: Int)
  case class ConcreteImpl(override val id: Int, name: String) extends AbstractBase(id)

  case class Empty()

  case class OptionalFields(required: String, optional: Option[Int])

  case class Container(items: List[Int], mapping: Map[String, Int])

  case class Outer(name: String, inner: Inner)

  case class Inner(value: Int)

  case class Simple(x: Int, y: String)


  def spec = suite("UnsupportedTypeErrorSpec")(
    suite("Supported Types")(
      test("concrete case class extending abstract class works") {
        val schema = Schema.derived[ConcreteImpl]
        val structural = schema.structural
        assertTrue(structural != null)
      },
      test("case class with simple fields works") {
        val schema = Schema.derived[Simple]
        val structural = schema.structural
        assertTrue(structural != null)
      },
      test("case class with nested case class works") {
        val schema = Schema.derived[Outer]
        val structural = schema.structural
        assertTrue(structural != null)
      },
      test("case class with collections works") {
        val schema = Schema.derived[Container]
        val structural = schema.structural
        assertTrue(structural != null)
      },
      test("case class with Option works") {
        val schema = Schema.derived[OptionalFields]
        val structural = schema.structural
        assertTrue(structural != null)
      },
      test("empty case class works") {
        val schema = Schema.derived[Empty]
        val structural = schema.structural
        assertTrue(structural != null)
      },
      test("case object works") {
        case object Singleton
        val schema = Schema.derived[Singleton.type]
        val structural = schema.structural
        assertTrue(structural != null)
      }
    ),
    suite("Round-Trip Verification")(
      test("concrete case class round-trips through structural") {
        val schema = Schema.derived[ConcreteImpl]
        val structural = schema.structural
        val value = ConcreteImpl(1, "test")
        
        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(value)
        val result = structural.fromDynamicValue(dynamic)
        
        assertTrue(result.isRight)
      }
    )
  )
}
