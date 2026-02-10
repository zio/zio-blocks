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
        // Resource is NOT Unscoped, but Scope.GlobalTag allows escape
        val escape: ScopeEscape.Aux[Resource, Scope.GlobalTag, Resource] =
          implicitly[ScopeEscape[Resource, Scope.GlobalTag]]
            .asInstanceOf[ScopeEscape.Aux[Resource, Scope.GlobalTag, Resource]]
        val resource         = new Resource
        val result: Resource = escape(resource)
        // result should be raw Resource, not Resource @@ Scope.GlobalTag
        assertTrue(result.getData() == 42)
      },
      test("InputStream escapes from global scope") {
        // InputStream is NOT Unscoped, but should escape from global scope
        val escape: ScopeEscape.Aux[InputStream, Scope.GlobalTag, InputStream] =
          implicitly[ScopeEscape[InputStream, Scope.GlobalTag]]
            .asInstanceOf[ScopeEscape.Aux[InputStream, Scope.GlobalTag, InputStream]]
        val stream              = new java.io.ByteArrayInputStream(Array[Byte](1, 2, 3))
        val result: InputStream = escape(stream)
        // Verify escape returns raw InputStream, not scoped
        assertTrue(result eq stream, result.read() == 1)
      },
      test("primitives also escape from global scope") {
        val escape: ScopeEscape.Aux[Int, Scope.GlobalTag, Int] =
          implicitly[ScopeEscape[Int, Scope.GlobalTag]].asInstanceOf[ScopeEscape.Aux[Int, Scope.GlobalTag, Int]]
        val result: Int = escape(42)
        assertTrue(result == 42)
      },
      test("global scope escapes all results as raw values") {
        Scope.global.scoped { scope =>
          val str: String @@ scope.Tag = scope.allocate(zio.blocks.scope.Resource("test"))
          val raw: String              = scope.$(str)(identity)
          assertTrue(raw == "test")
        }
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
        // Resource is NOT Unscoped and tag is not Scope.GlobalTag, so it stays scoped
        val escape: ScopeEscape.Aux[Resource, String, Resource @@ String] =
          implicitly[ScopeEscape[Resource, String]].asInstanceOf[ScopeEscape.Aux[Resource, String, Resource @@ String]]
        val resource                   = new Resource
        val result: Resource @@ String = escape(resource)
        // result is Resource @@ String (scoped), verify it's the same instance
        assertTrue(@@.unscoped(result) eq resource)
      },
      test("unscoped types escape as raw values in nested scopes") {
        val result: String = Scope.global.scoped { parent =>
          parent.scoped { child =>
            val str: String @@ child.Tag = child.allocate(zio.blocks.scope.Resource("hello"))
            child.$(str)(_.toUpperCase)
          }
        }
        assertTrue(result == "HELLO")
      }
    ),
    suite("Priority ordering")(
      test("global scope has higher priority than Unscoped") {
        // Both globalScope and unscoped instances match for Int @@ Scope.GlobalTag
        // The globalScope instance should win
        val escape: ScopeEscape.Aux[Int, Scope.GlobalTag, Int] =
          implicitly[ScopeEscape[Int, Scope.GlobalTag]].asInstanceOf[ScopeEscape.Aux[Int, Scope.GlobalTag, Int]]
        assertTrue(escape(123) == 123)
      }
    )
  )
}
