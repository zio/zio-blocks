package zio.blocks.scope

import zio.test._

/**
 * Scala 2 version of scope type inference tests.
 * Uses import scope._ pattern to bring operations into scope.
 */
object ScopeInferenceSpec extends ZIOSpecDefault {

  // Mock scoped value
  final case class @@[+A, -S](private[scope] val value: A) extends AnyVal

  object @@ {
    def wrap[A, S](a: A): A @@ S = new @@(a)
    def unwrap[A, S](s: A @@ S): A = s.value
  }

  // Mock Scope with operations as members
  trait MockScope { self =>
    type Upper
    type ScopeTag <: Upper

    // Operations defined on scope instance
    def allocate[A](value: A): A @@ ScopeTag =
      @@.wrap(value)

    def $[A, B](scoped: A @@ ScopeTag)(f: A => B): B @@ ScopeTag =
      @@.wrap(f(@@.unwrap(scoped)))

    def execute[A](scoped: A @@ ScopeTag): A @@ ScopeTag =
      scoped

    def defer(f: => Unit): Unit =
      () // no-op in mock

    def scoped[A](f: MockScope.Typed[ScopeTag, ScopeTag] => A): A = {
      val child: MockScope.Typed[ScopeTag, ScopeTag] = new MockScope {
        type Upper    = self.ScopeTag
        type ScopeTag = self.ScopeTag
      }
      f(child)
    }

    // Implicit class to add map/flatMap to scoped values
    implicit class ScopedOps[A](private val scoped: A @@ self.ScopeTag) {
      def map[B](f: A => B): B @@ self.ScopeTag =
        @@.wrap(f(@@.unwrap(scoped)))

      def flatMap[B](f: A => B @@ self.ScopeTag): B @@ self.ScopeTag =
        f(@@.unwrap(scoped))
    }
  }

  object MockScope {
    type Typed[Upper0, ScopeTag0 <: Upper0] = MockScope { type Upper = Upper0; type ScopeTag = ScopeTag0 }

    type GlobalTag

    val global: MockScope.Typed[GlobalTag, GlobalTag] = new MockScope {
      type Upper    = GlobalTag
      type ScopeTag = GlobalTag
    }
  }

  def spec = suite("Scope inference with import scope._ (Scala 2)")(
    test("single level scoped works with map") {
      var captured: Int = 0
      MockScope.global.scoped { scope =>
        import scope._

        val resource = allocate("hello")
        val len = resource.map(_.length)

        $(len) { l =>
          captured = l
          l
        }
      }
      assertTrue(captured == 5)
    },
    test("$ operator works") {
      var captured: String = null
      MockScope.global.scoped { scope =>
        import scope._

        val resource = allocate("hello")
        $(resource) { s =>
          captured = s
          s
        }
      }
      assertTrue(captured == "hello")
    },
    test("for-comprehension works") {
      var captured: Boolean = false
      MockScope.global.scoped { scope =>
        import scope._

        val a = allocate("hello")
        val b = allocate("world")

        val result = for {
          x <- a
          y <- b
        } yield x.length == y.length

        $(result) { r =>
          captured = r
          r
        }
      }
      assertTrue(captured == true)
    },
    test("nested scoped works") {
      var parentCaptured: String = null
      var childCaptured: Int = 0

      MockScope.global.scoped { parent =>
        import parent._

        val parentVal = allocate("parent")

        scoped { child =>
          import child._

          val childVal = allocate(42)

          // Parent value needs explicit $ from parent scope
          parent.$(parentVal) { p => parentCaptured = p; p }
          $(childVal) { c => childCaptured = c; c }
        }
      }

      assertTrue(
        parentCaptured == "parent",
        childCaptured == 42
      )
    }
  )
}
