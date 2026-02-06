package zio.blocks.scope

import zio.test._
import java.io.InputStream

/**
 * Tests for ScopeEscape behavior, especially the global scope escape rule.
 */
object ScopeEscapeSpec extends ZIOSpecDefault {

  // A resource type that is NOT Unscoped
  class Resource {
    def getData(): Int = 42
  }

  def spec = suite("ScopeEscape")(
    suite("Global scope escape")(
      test("any type escapes from global scope via ScopeEscape") {
        // Resource is NOT Unscoped, but TNil (global scope tag) allows escape
        val escape: ScopeEscape.Aux[Resource, TNil, Resource] =
          implicitly[ScopeEscape[Resource, TNil]].asInstanceOf[ScopeEscape.Aux[Resource, TNil, Resource]]
        val resource         = new Resource
        val result: Resource = escape(resource)
        // result should be raw Resource, not Resource @@ TNil
        assertTrue(result.getData() == 42)
      },
      test("InputStream escapes from global scope") {
        // InputStream is NOT Unscoped, but should escape from global scope
        val escape = implicitly[ScopeEscape[InputStream, TNil]]
        assertTrue(escape != null)
      },
      test("primitives also escape from global scope") {
        val escape: ScopeEscape.Aux[Int, TNil, Int] =
          implicitly[ScopeEscape[Int, TNil]].asInstanceOf[ScopeEscape.Aux[Int, TNil, Int]]
        val result: Int = escape(42)
        assertTrue(result == 42)
      }
    ),
    suite("Child scope behavior")(
      test("Unscoped types escape from child scopes") {
        // String has Unscoped, so it escapes from any scope
        val escape: ScopeEscape.Aux[String, String, String] =
          implicitly[ScopeEscape[String, String]].asInstanceOf[ScopeEscape.Aux[String, String, String]]
        val result: String = escape("hello")
        assertTrue(result == "hello")
      },
      test("Resource types stay scoped in child scopes") {
        // Resource is NOT Unscoped and tag is not TNil, so it stays scoped
        val escape: ScopeEscape.Aux[Resource, String, Resource @@ String] =
          implicitly[ScopeEscape[Resource, String]].asInstanceOf[ScopeEscape.Aux[Resource, String, Resource @@ String]]
        val resource                   = new Resource
        val result: Resource @@ String = escape(resource)
        // result is Resource @@ String (scoped), verify it's the same instance
        assertTrue(@@.unscoped(result) eq resource)
      }
    ),
    suite("Priority ordering")(
      test("global scope has higher priority than Unscoped") {
        // Both globalScope and unscoped instances match for Int @@ TNil
        // The globalScope instance should win
        val escape: ScopeEscape.Aux[Int, TNil, Int] =
          implicitly[ScopeEscape[Int, TNil]].asInstanceOf[ScopeEscape.Aux[Int, TNil, Int]]
        assertTrue(escape(123) == 123)
      }
    )
  )
}
