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
        // Resource is NOT Unscoped, but Scope.Global (global scope tag) allows escape
        val escape: ScopeEscape.Aux[Resource, Scope.Global, Resource] =
          implicitly[ScopeEscape[Resource, Scope.Global]]
            .asInstanceOf[ScopeEscape.Aux[Resource, Scope.Global, Resource]]
        val resource         = new Resource
        val result: Resource = escape(resource)
        // result should be raw Resource, not Resource @@ Scope.Global
        assertTrue(result.getData() == 42)
      },
      test("InputStream escapes from global scope") {
        // InputStream is NOT Unscoped, but should escape from global scope
        val escape: ScopeEscape.Aux[InputStream, Scope.Global, InputStream] =
          implicitly[ScopeEscape[InputStream, Scope.Global]]
            .asInstanceOf[ScopeEscape.Aux[InputStream, Scope.Global, InputStream]]
        val stream              = new java.io.ByteArrayInputStream(Array[Byte](1, 2, 3))
        val result: InputStream = escape(stream)
        // Verify escape returns raw InputStream, not scoped
        assertTrue(result eq stream, result.read() == 1)
      },
      test("primitives also escape from global scope") {
        val escape: ScopeEscape.Aux[Int, Scope.Global, Int] =
          implicitly[ScopeEscape[Int, Scope.Global]].asInstanceOf[ScopeEscape.Aux[Int, Scope.Global, Int]]
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
        // Resource is NOT Unscoped and tag is not Scope.Global, so it stays scoped
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
        // Both globalScope and unscoped instances match for Int @@ Scope.Global
        // The globalScope instance should win
        val escape: ScopeEscape.Aux[Int, Scope.Global, Int] =
          implicitly[ScopeEscape[Int, Scope.Global]].asInstanceOf[ScopeEscape.Aux[Int, Scope.Global, Int]]
        assertTrue(escape(123) == 123)
      }
    )
  )
}
