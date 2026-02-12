package zio.blocks.scope

import zio.test._

/**
 * Isolated test for SAM syntax with path-dependent return types.
 *
 * APPROACH: ScopeUser has type member $, Child takes $ as type parameter. The $
 * is existentially quantified, not user-chosen.
 */

// SAM-compatible: return type is Any to avoid path-dependence in signature
// Child still has abstract $, so user code is type-safe internally
@FunctionalInterface
trait MockScopeUser[P <: MockScope, +A] {
  def use(child: MockScope.Child[P]): Any
}

sealed abstract class MockScope extends Finalizer { self =>
  type $[+A]

  protected def wrap[A](a: A): $[A]

  object wrapValue {
    def apply[A](a: A): $[A] = wrap(a)
  }

  // scoped with Any return type - casting happens here
  // Safe because Unscoped[A] ensures A is a pure value type
  final def scoped[A](f: MockScopeUser[self.type, A])(implicit ev: Unscoped[A]): A = {
    val child       = new MockScope.Child[self.type](self)
    val result: Any = f.use(child)
    result.asInstanceOf[A]
  }
}

object MockScope {
  object global extends MockScope {
    type $[+A] = A
    protected def wrap[A](a: A): $[A] = a
    def defer(f: => Unit): Unit       = ()
  }

  // Child has abstract $ - each instance has unique type
  final class Child[P <: MockScope](val parent: P) extends MockScope {
    type $[+A] // Abstract! This is what makes child1.$ != child2.$
    protected def wrap[A](a: A): $[A] = a.asInstanceOf[$[A]]
    def defer(f: => Unit): Unit       = ()
  }
}

object SamSyntaxSpec extends ZIOSpecDefault {
  def spec = suite("SAM Syntax")(
    suite("explicit ScopeUser")(
      test("explicit anonymous class works") {
        val user = new MockScopeUser[MockScope.global.type, Int] {
          def use(child: MockScope.Child[MockScope.global.type]): Any =
            child.wrapValue(42) // returns child.$[Int], widens to Any
        }
        val result: Int = MockScope.global.scoped(user)
        assertTrue(result == 42)
      }
    ),
    suite("SAM lambda")(
      test("SAM lambda with Any return - does SAM work now?") {
        val result: Int = MockScope.global.scoped { child =>
          child.wrapValue(42)
        }
        assertTrue(result == 42)
      }
    ),
    suite("Type safety")(
      test("child.$ is abstract, so wrapValue returns opaque type") {
        val result: Int = MockScope.global.scoped { child =>
          val x: child.$[Int] = child.wrapValue(42)
          // x has type child.$[Int], not Int
          // This proves $ is abstract from user's perspective
          x // widens to Any for SAM return
        }
        assertTrue(result == 42)
      }
    )
  )
}
