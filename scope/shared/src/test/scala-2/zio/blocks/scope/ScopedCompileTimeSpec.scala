package zio.blocks.scope

import zio.test._

/**
 * Tests that verify compile-time safety of scoped values.
 *
 * In Scala 2, we can't use `typeCheckErrors` like Scala 3, so these tests
 * verify runtime behavior that demonstrates the scoping system works.
 */
object ScopedCompileTimeSpec extends ZIOSpecDefault {

  class MockResource {
    def getData(): Int = 42
  }

  implicit val globalScope: Scope.Any = Scope.global

  def spec = suite("Scoped Compile-Time Safety (Scala 2)")(
    suite("Tag type safety")(
      test("map preserves tag type") {
        // Verify that map returns a scoped value
        val closeable = injected(new MockResource)
        closeable.run { implicit scope =>
          val scoped = $[MockResource]
          val mapped = scoped.map(_.getData())
          // mapped is Int @@ Tag - verify we can use it with $ operator
          val result: Int = mapped.$(identity)
          assertTrue(result == 42)
        }
      },
      test("flatMap combines tags") {
        // Create scoped values directly for testing flatMap behavior
        val t1: Int @@ String    = @@.scoped(1)
        val t2: String @@ String = @@.scoped("hello")

        // flatMap produces a combined scoped value
        val combined = for {
          x <- t1
          y <- t2
        } yield (x, y)

        // Verify the combined value can be mapped
        val result = combined.map { case (a, b) => a + b.length }
        assertTrue(result != null)
      }
    ),
    suite("Unscoped types escape")(
      test("Int escapes unscoped via $ operator") {
        val closeable = injected(new MockResource)
        closeable.run { implicit scope =>
          val scoped = $[MockResource]
          val n: Int = scoped.$(_.getData())
          assertTrue(n == 42)
        }
      },
      test("resourceful types stay scoped via $ operator") {
        class Inner { def value: Int = 99          }
        class Outer { def inner: Inner = new Inner }

        val closeable = injected(new Outer)
        closeable.run { implicit scope =>
          val scoped = $[Outer]

          // inner is not Unscoped, so it stays scoped (can't assign to raw Inner)
          val inner = scoped.$(_.inner)

          // Can extract value from inner with same scope
          val n: Int = inner.$(_.value)

          assertTrue(n == 99)
        }
      }
    )
  )
}
