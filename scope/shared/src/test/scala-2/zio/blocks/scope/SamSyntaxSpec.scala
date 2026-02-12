package zio.blocks.scope

import zio.test._

/**
 * Test lambda syntax with macro-extracted return types.
 */

sealed abstract class MockScope extends Finalizer { self =>
  import scala.language.experimental.macros
  
  type $[+A]
  
  protected def wrap[A](a: A): $[A]
  
  object wrapValue {
    def apply[A](a: A): $[A] = wrap(a)
  }
  
  // scoped is a macro - inspects lambda body and extracts the underlying type
  final def scoped(f: MockScope.Child[self.type] => Any): Any = macro ScopeMacros.mockScopedImpl
}

object MockScope {
  object global extends MockScope {
    type $[+A] = A
    protected def wrap[A](a: A): $[A] = a
    def defer(f: => Unit): Unit = ()
  }
  
  final class Child[P <: MockScope](val parent: P) extends MockScope {
    type $[+A]
    protected def wrap[A](a: A): $[A] = a.asInstanceOf[$[A]]
    def defer(f: => Unit): Unit = ()
  }
}

object SamSyntaxSpec extends ZIOSpecDefault {
  
  def spec = suite("Scoped Macro")(
    suite("typed return")(
      test("extracts Int from child.$[Int]") {
        val result: Int = MockScope.global.scoped { child =>
          child.wrapValue(42)
        }
        assertTrue(result == 42)
      },
      test("extracts String from child.$[String]") {
        val result: String = MockScope.global.scoped { child =>
          child.wrapValue("hello")
        }
        assertTrue(result == "hello")
      }
    ),
    suite("nested scopes")(
      test("nested scoped blocks work correctly") {
        val result: Int = MockScope.global.scoped { outer =>
          val outerVal: Int = MockScope.global.scoped { inner =>
            inner.wrapValue(20)
          }
          outer.wrapValue(outerVal + 5)
        }
        assertTrue(result == 25)
      }
    ),
    suite("plain return (no child.$)")(
      test("returning plain Int works (macro validates Unscoped[Int])") {
        val result: Int = MockScope.global.scoped { _ =>
          42
        }
        assertTrue(result == 42)
      }
    ),
    // NOTE: "wrong scope $" is a compile-time error - tested separately
    // The macro detects when returning outer.$[Int] from inner scope
    suite("scope validation")(
      test("nested scopes with correct returns compile") {
        val result: Int = MockScope.global.scoped { outer =>
          val x: Int = MockScope.global.scoped { inner =>
            inner.wrapValue(10)
          }
          outer.wrapValue(x + 5)
        }
        assertTrue(result == 15)
      }
    )
  )
}
