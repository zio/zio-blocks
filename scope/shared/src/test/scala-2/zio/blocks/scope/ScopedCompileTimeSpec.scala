package zio.blocks.scope

import zio.test._

/**
 * Tests that verify compile-time safety of scoped values.
 *
 * In Scala 2, we use `typeCheck` from ZIO Test to verify that certain code
 * DOESN'T compile, which is the key safety property of the escape prevention
 * system.
 */
object ScopedCompileTimeSpec extends ZIOSpecDefault {

  class MockResource {
    def getData(): Int = 42
  }

  implicit val globalScope: Scope.Any = Scope.global

  def spec = suite("Scoped Compile-Time Safety (Scala 2)")(
    suite("Opaque type hides methods")(
      test("cannot call methods on scoped value directly") {
        // This verifies the core safety property: methods are hidden
        typeCheck {
          """
          import zio.blocks.scope._
          val scoped: Int @@ String = @@.scoped[Int, String](42)
          scoped + 1  // Should fail: + is not a member of Int @@ String
          """
        }.map(result => assertTrue(result.isLeft))
      },
      test("cannot call String methods on scoped String") {
        typeCheck {
          """
          import zio.blocks.scope._
          val scoped: String @@ Int = @@.scoped[String, Int]("hello")
          scoped.length  // Should fail: length is not a member
          """
        }.map(result => assertTrue(result.isLeft))
      },
      test("cannot assign scoped value to raw type") {
        typeCheck {
          """
          import zio.blocks.scope._
          val scoped: Int @@ String = @@.scoped[Int, String](42)
          val raw: Int = scoped  // Should fail: type mismatch
          """
        }.map(result => assertTrue(result.isLeft))
      }
    ),
    suite("$ operator tag checking")(
      test("cannot use $ with value from different scope") {
        // This verifies that the macro rejects mismatched scope tags
        // We use two different closeable scopes - a value from one scope
        // cannot be accessed with the other scope in context
        typeCheck {
          """
          import zio.blocks.scope._

          class Resource1 { def value: Int = 1 }
          class Resource2 { def value: Int = 2 }

          implicit val globalScope: Scope.Any = Scope.global

          val closeable1 = injected(new Resource1)
          val closeable2 = injected(new Resource2)

          // Get a scoped value from closeable1's scope
          var escapedValue: Resource1 @@ closeable1.Tag = null.asInstanceOf[Resource1 @@ closeable1.Tag]
          closeable1.run { implicit scope =>
            escapedValue = $[Resource1]
          }

          // Try to use it with closeable2's scope - should fail
          closeable2.run { implicit scope =>
            // This should fail: closeable2.Tag is not a supertype of closeable1.Tag
            escapedValue.$(_.value)
          }
          """
        }.map(result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg => msg.contains("cannot be accessed") || msg.contains("Tag"))
          )
        )
      },
      test("cannot use .get without scope in context") {
        typeCheck {
          """
          import zio.blocks.scope._
          val scoped: Int @@ String = @@.scoped[Int, String](42)
          scoped.get  // Should fail: no implicit scope
          """
        }.map(result => assertTrue(result.isLeft))
      },
      test("cannot use .get with value from different scope") {
        typeCheck {
          """
          import zio.blocks.scope._

          class Resource1 { def value: Int = 1 }
          class Resource2 { def value: Int = 2 }

          implicit val globalScope: Scope.Any = Scope.global

          val closeable1 = injected(new Resource1)
          val closeable2 = injected(new Resource2)

          // Get a scoped value from closeable1's scope
          var escapedValue: Resource1 @@ closeable1.Tag = null.asInstanceOf[Resource1 @@ closeable1.Tag]
          closeable1.run { implicit scope =>
            escapedValue = $[Resource1]
          }

          // Try to use .get with closeable2's scope - should fail
          closeable2.run { implicit scope =>
            // closeable2.Tag is not a supertype of closeable1.Tag
            escapedValue.get
          }
          """
        }.map(result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg => msg.contains("cannot be accessed") || msg.contains("Tag"))
          )
        )
      }
    ),
    suite("Escape prevention")(
      test("resource types cannot escape as raw via .get") {
        // Resource types (non-Unscoped) stay scoped after .get - can't assign to raw
        typeCheck {
          """
          import zio.blocks.scope._
          class Resource { def value: Int = 42 }

          implicit val globalScope: Scope.Any = Scope.global
          val closeable = injected(new Resource)
          closeable.run { implicit scope =>
            val scoped = $[Resource]
            // Resource is not Unscoped, so .get returns Resource @@ Tag, not raw Resource
            val raw: Resource = scoped.get  // Should fail: type mismatch
          }
          """
        }.map(result => assertTrue(result.isLeft))
      }
    ),
    suite("Tag type safety")(
      test("map preserves tag type") {
        // Verify that map returns a scoped value
        val closeable = injected(new MockResource)
        closeable.run { implicit scope =>
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
