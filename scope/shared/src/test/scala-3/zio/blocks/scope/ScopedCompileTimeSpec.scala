package zio.blocks.scope

import zio.test._

/**
 * Tests that verify compile-time safety of scoped values.
 *
 * These tests use `typeCheckErrors` to verify that certain code DOESN'T
 * compile, which is the key safety property of the escape prevention system.
 */
object ScopedCompileTimeSpec extends ZIOSpecDefault {

  class MockResource {
    def getData(): Int = 42
  }

  given Scope.Any = Scope.global

  def spec = suite("Scoped Compile-Time Safety")(
    suite("Opaque type hides methods")(
      test("cannot call methods on scoped value directly") {
        // This verifies the core safety property: methods are hidden
        val errors = typeCheckErrors("""
          val scoped: Int @@ String = zio.blocks.scope.@@.scoped(42)
          scoped + 1  // Should fail: + is not a member of Int @@ String
        """)
        assertTrue(errors.exists(_.message.contains("value + is not a member")))
      },
      test("cannot call String methods on scoped String") {
        val errors = typeCheckErrors("""
          val scoped: String @@ Int = zio.blocks.scope.@@.scoped("hello")
          scoped.length  // Should fail: length is not a member
        """)
        assertTrue(errors.exists(_.message.contains("is not a member")))
      },
      test("cannot assign scoped value to raw type") {
        val errors = typeCheckErrors("""
          val scoped: Int @@ String = zio.blocks.scope.@@.scoped(42)
          val raw: Int = scoped  // Should fail: type mismatch
        """)
        assertTrue(errors.nonEmpty)
      }
    ),
    suite("$ operator tag checking")(
      test("cannot use $ with value from different scope") {
        val errors = typeCheckErrors("""
          import zio.blocks.scope._

          class Resource1 { def value: Int = 1 }
          class Resource2 { def value: Int = 2 }

          given Scope.Any = Scope.global

          val closeable1 = injected(new Resource1)
          val closeable2 = injected(new Resource2)

          // Get a scoped value from closeable1's scope
          var escapedValue: Resource1 @@ closeable1.Tag = null.asInstanceOf[Resource1 @@ closeable1.Tag]
          closeable1.run {
            escapedValue = $[Resource1]
          }

          // Try to use it with closeable2's scope - should fail
          closeable2.run {
            // closeable2.Tag is not a supertype of closeable1.Tag
            escapedValue $ (_.value)
          }
        """)
        assertTrue(errors.nonEmpty)
      },
      test("cannot use $ without scope in context") {
        val errors = typeCheckErrors("""
          import zio.blocks.scope._
          val scoped: Int @@ String = @@.scoped(42)
          scoped $ identity  // Should fail: no implicit scope
        """)
        assertTrue(errors.nonEmpty)
      }
    ),
    suite("Escape prevention")(
      test("resource types cannot escape as raw via $") {
        // Resource types (non-Unscoped) stay scoped after $ - can't assign to raw
        val errors = typeCheckErrors("""
          import zio.blocks.scope._
          class Resource { def value: Int = 42 }

          given Scope.Any = Scope.global
          val closeable = injected(new Resource)
          closeable.run {
            val scoped = $[Resource]
            // Resource is not Unscoped, so $ returns Resource @@ Tag, not raw Resource
            val raw: Resource = scoped $ identity  // Should fail: type mismatch
          }
        """)
        assertTrue(errors.nonEmpty)
      }
    ),
    suite("Tag type safety")(
      test("map preserves tag type") {
        // Verify that map returns a scoped value
        val closeable = injected(new MockResource)
        closeable.run {
          val scoped = $[MockResource]
          val mapped = scoped.map(_.getData())
          // mapped is Int @@ Tag - verify we can use it with $ operator
          val result: Int = mapped $ identity
          assertTrue(result == 42)
        }
      },
      test("flatMap combines tags") {
        // Tests type algebra of scoped values in isolation.
        // Uses artificial String tag (not from a real scope) to verify
        // that map/flatMap preserve and combine tags correctly.
        val t1: Int @@ String    = @@.scoped(1)
        val t2: String @@ String = @@.scoped("hello")

        val combined = for {
          x <- t1
          y <- t2
        } yield (x, y)

        // Compilation proves type algebra works; verify runtime behavior
        val result: Int @@ String = combined.map { case (a, b) => a + b.length }
        assertTrue(@@.unscoped(result) == 6)
      }
    ),
    suite("Unscoped types escape")(
      test("Int escapes unscoped via $ operator") {
        val closeable = injected(new MockResource)
        closeable.run {
          val scoped = $[MockResource]
          val n: Int = scoped $ (_.getData())
          assertTrue(n == 42)
        }
      },
      test("resourceful types stay scoped via $ operator") {
        class Inner { def value: Int = 99          }
        class Outer { def inner: Inner = new Inner }

        val closeable = injected(new Outer)
        closeable.run {
          val scoped = $[Outer]

          // inner is not Unscoped, so it stays scoped (can't assign to raw Inner)
          val inner = scoped $ (_.inner)

          // Can extract value from inner with same scope
          val n: Int = inner $ (_.value)

          assertTrue(n == 99)
        }
      }
    )
  )
}
