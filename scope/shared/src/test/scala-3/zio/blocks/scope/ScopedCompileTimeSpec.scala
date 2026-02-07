package zio.blocks.scope

import zio.test._
import scala.compiletime.testing.typeCheckErrors

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
        assertTrue(errors.exists(e => e.message.contains("Found") || e.message.contains("type mismatch")))
      }
    ),
    suite("$ operator tag checking")(
      test("cannot use $ without Scope.Permit in context") {
        // Test the simpler case without macros - scoped value with arbitrary tag
        val errors: List[scala.compiletime.testing.Error] = typeCheckErrors("""
          val scoped: zio.blocks.scope.@@[Int, String] = zio.blocks.scope.@@.scoped(42)
          scoped $ (_ + 1)
        """)
        assertTrue(
          errors.nonEmpty && errors.exists(e => e.message.contains("No given instance") || e.message.contains("Permit"))
        )
      },
      test("cannot use .get without Scope.Permit in context") {
        val errors: List[scala.compiletime.testing.Error] = typeCheckErrors("""
          val scoped: zio.blocks.scope.@@[Int, String] = zio.blocks.scope.@@.scoped(42)
          scoped.get
        """)
        // Error can be "is not a member" (extension not found) or "No given instance" (Permit not found)
        assertTrue(
          errors.nonEmpty && errors.exists(e =>
            e.message.contains("No given instance") ||
              e.message.contains("Permit") ||
              e.message.contains("is not a member")
          )
        )
      },
      test("$ on scoped value works with scope in context") {
        // Positive test - verify $ works when scope is available
        import zio.blocks.context.Context
        import zio.blocks.scope.internal.Finalizers

        class Resource { def value: Int = 42 }

        val closeable = Scope.makeCloseable(Scope.global, Context(new Resource), new Finalizers)
        val result    = closeable.use {
          val res = $[Resource]
          res $ (_.value)
        }
        assertTrue(result == 42)
      }
    ),
    suite("Escape prevention")(
      test("resource types cannot escape as raw via .get") {
        // Resource types (non-Unscoped) stay scoped after .get - can't assign to raw
        val errors = typeCheckErrors("""
          import zio.blocks.scope._
          class Resource { def value: Int = 42 }

          given Scope.Any = Scope.global
          val closeable = injected(new Resource)
          closeable.use {
            val scoped = $[Resource]
            // Resource is not Unscoped, so .get returns Resource @@ Tag, not raw Resource
            val raw: Resource = scoped.get  // Should fail: type mismatch
          }
        """)
        assertTrue(errors.exists(e => e.message.contains("Found") || e.message.contains("type mismatch")))
      }
    ),
    suite("Tag hierarchy safety")(
      test("parent scope cannot access child-scoped values") {
        // A value tagged with child.Tag should NOT be accessible from parent scope
        val errors = typeCheckErrors("""
          import zio.blocks.scope._
          import zio.blocks.context.Context
          import zio.blocks.scope.internal.Finalizers

          class ParentResource { def value: Int = 1 }
          class ChildResource { def value: Int = 2 }

          val parent = Scope.makeCloseable(Scope.global, Context(new ParentResource), new Finalizers)

          // Create a child scope
          parent.use {
            val child = Scope.makeCloseable(summon[Scope.Any], Context(new ChildResource), new Finalizers)

            // Get a value tagged with child's Tag
            val childValue: ChildResource @@ child.Tag = child.use {
              $[ChildResource]
            }

            // Now we're back in parent's scope - try to access child-tagged value
            // This should fail: parent's Permit doesn't satisfy child's Tag
            childValue $ (_.value)
          }
        """)
        assertTrue(
          errors.nonEmpty && errors.exists(e => e.message.contains("No given instance") || e.message.contains("Permit"))
        )
      },
      test("sibling scopes cannot access each other's values") {
        // A value tagged with sibling1.Tag should NOT be accessible from sibling2
        val errors = typeCheckErrors("""
          import zio.blocks.scope._
          import zio.blocks.context.Context
          import zio.blocks.scope.internal.Finalizers

          class Resource1 { def value: Int = 1 }
          class Resource2 { def value: Int = 2 }

          val sibling1 = Scope.makeCloseable(Scope.global, Context(new Resource1), new Finalizers)
          val sibling2 = Scope.makeCloseable(Scope.global, Context(new Resource2), new Finalizers)

          // Get a value from sibling1's scope
          val sib1Value: Resource1 @@ sibling1.Tag = sibling1.use {
            $[Resource1]
          }

          // Try to use it in sibling2's scope - should fail
          sibling2.use {
            sib1Value $ (_.value)
          }
        """)
        assertTrue(
          errors.nonEmpty && errors.exists(e => e.message.contains("No given instance") || e.message.contains("Permit"))
        )
      }
    ),
    suite("Tag type safety")(
      test("map preserves tag type") {
        // Verify that map returns a scoped value
        val closeable = injected(new MockResource)
        closeable.use {
          val scoped = $[MockResource]
          val mapped = scoped.map(_.getData())
          // mapped is Int @@ Tag - verify we can use .get to extract
          val result: Int = mapped.get
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
        closeable.use {
          val scoped = $[MockResource]
          val n: Int = scoped $ (_.getData())
          assertTrue(n == 42)
        }
      },
      test("resourceful types stay scoped via $ operator") {
        class Inner { def value: Int = 99          }
        class Outer { def inner: Inner = new Inner }

        val closeable = injected(new Outer)
        closeable.use {
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
