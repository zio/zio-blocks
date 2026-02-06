package zio.blocks.scope

import zio.test._
import zio.test.TestAspect

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
      // TODO: Scala 2 limitation - the macro extracts scope from tag prefix but can't
      // verify that the implicit scope in context is compatible. This works in Scala 3
      // via refined type constraints (using scope: Scope { type Tag <: S }).
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
          closeable1.use { implicit scope =>
            escapedValue = $[Resource1]
          }

          // Try to use it with closeable2's scope - should fail
          closeable2.use { implicit scope =>
            // This should fail: closeable2.Tag is not a supertype of closeable1.Tag
            escapedValue.$(_.value)
          }
          """
        }.map(result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg =>
              msg.contains("cannot be accessed") || msg.contains("Tag") || msg.contains("No scope")
            )
          )
        )
      } @@ TestAspect.ignore,
      test("cannot use .get without scope in context") {
        typeCheck {
          """
          import zio.blocks.scope._
          val scoped: Int @@ String = @@.scoped[Int, String](42)
          scoped.get  // Should fail: no implicit scope
          """
        }.map(result => assertTrue(result.isLeft))
      },
      // TODO: Scala 2 limitation - same as above
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
          closeable1.use { implicit scope =>
            escapedValue = $[Resource1]
          }

          // Try to use .get with closeable2's scope - should fail
          closeable2.use { implicit scope =>
            // closeable2.Tag is not a supertype of closeable1.Tag
            escapedValue.get
          }
          """
        }.map(result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg =>
              msg.contains("cannot be accessed") || msg.contains("Tag") || msg.contains("No scope")
            )
          )
        )
      } @@ TestAspect.ignore
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
          closeable.use { implicit scope =>
            val scoped = $[Resource]
            // Resource is not Unscoped, so .get returns Resource @@ Tag, not raw Resource
            val raw: Resource = scoped.get  // Should fail: type mismatch
          }
          """
        }.map(result => assertTrue(result.isLeft, result.left.exists(_.contains("type mismatch"))))
      }
    ),
    // TODO: Scala 2 limitation - the macro extracts scope from tag prefix but can't
    // verify that the implicit scope in context is compatible. This works in Scala 3
    // via refined type constraints (using scope: Scope { type Tag <: S }).
    suite("Nested scope tag hierarchy")(
      test("sibling scopes cannot share scoped values") {
        // Negative test: sibling scopes have unrelated Tags
        typeCheck {
          """
          import zio.blocks.scope._

          class Resource1 { def value: Int = 1 }
          class Resource2 { def value: Int = 2 }

          implicit val globalScope: Scope.Any = Scope.global

          val sibling1 = injected(new Resource1)
          val sibling2 = injected(new Resource2)

          var escapedFromSibling1: Resource1 @@ sibling1.Tag = null.asInstanceOf[Resource1 @@ sibling1.Tag]
          sibling1.use { implicit scope =>
            escapedFromSibling1 = $[Resource1]
          }

          // Try to use sibling1's value in sibling2's scope - should fail
          sibling2.use { implicit scope =>
            escapedFromSibling1.$(_.value)
          }
          """
        }.map(result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg =>
              msg.contains("cannot be accessed") || msg.contains("type mismatch") || msg.contains("No scope")
            )
          )
        )
      } @@ TestAspect.ignore
    ),
    suite("Service retrieval")(
      test("cannot retrieve service not in scope") {
        // Negative test: $[T] should fail if T is not in the scope
        typeCheck {
          """
          import zio.blocks.scope._

          class Database { def query(): String = "result" }
          class Config { def url: String = "localhost" }

          implicit val globalScope: Scope.Any = Scope.global

          // Scope only has Config, not Database
          val closeable = injected(new Config)
          closeable.use { implicit scope =>
            // This should NOT compile: Database is not in scope
            val db = $[Database]
          }
          """
        }.map(result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg => msg.contains("could not find implicit") || msg.contains("No implicit"))
          )
        )
      }
    )
  )
}
