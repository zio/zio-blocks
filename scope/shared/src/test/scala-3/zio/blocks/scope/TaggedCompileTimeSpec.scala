package zio.blocks.scope

import zio.test._
import scala.compiletime.testing.typeCheckErrors

/**
 * Tests that verify compile-time safety of tagged values.
 *
 * These tests use `typeCheckErrors` to verify that certain code DOESN'T
 * compile, which is the key safety property of the escape prevention system.
 */
object TaggedCompileTimeSpec extends ZIOSpecDefault {

  class MockResource {
    def getData(): Int = 42
  }

  given Scope.Any = Scope.global

  def spec = suite("Tagged Compile-Time Safety")(
    suite("Opaque type hides methods")(
      test("cannot call methods on tagged value directly") {
        // This verifies the core safety property: methods are hidden
        val errors = typeCheckErrors("""
          val tagged: Int @@ String = zio.blocks.scope.@@.tag(42)
          tagged + 1  // Should fail: + is not a member of Int @@ String
        """)
        assertTrue(errors.exists(_.message.contains("value + is not a member")))
      }
    ),
    suite("Tag type safety")(
      test("map preserves exact tag type") {
        // Verify that map returns the same tag type
        val closeable                             = injectedValue(new MockResource)
        val tagged: MockResource @@ closeable.Tag = closeable.value
        val mapped: Int @@ closeable.Tag          = tagged.map(_.getData())
        closeable.closeOrThrow()
        assertTrue(true)
      },
      test("flatMap produces union tag") {
        val c1                   = injectedValue(1)
        val c2                   = injectedValue("hello")
        val t1: Int @@ c1.Tag    = c1.value
        val t2: String @@ c2.Tag = c2.value

        val combined: (Int, String) @@ (c1.Tag | c2.Tag) = for {
          x <- t1
          y <- t2
        } yield (x, y)

        c1.closeOrThrow()
        c2.closeOrThrow()
        assertTrue(true)
      }
    ),
    suite("Unscoped types escape")(
      test("Int escapes untagged via $ operator") {
        val closeable                             = injectedValue(new MockResource)
        val tagged: MockResource @@ closeable.Tag = closeable.value
        val n: Int                                = tagged.$(_.getData())(using closeable)(using summon)
        closeable.closeOrThrow()
        assertTrue(n == 42)
      },
      test("resourceful types stay tagged via $ operator") {
        class Inner { def value: Int = 99          }
        class Outer { def inner: Inner = new Inner }

        val closeable                      = injectedValue(new Outer)
        val tagged: Outer @@ closeable.Tag = closeable.value

        // inner is not Unscoped, so it stays tagged
        val inner: Inner @@ closeable.Tag = tagged.$(_.inner)(using closeable)(using summon)

        // Can extract value from inner with same scope
        val n: Int = inner.$(_.value)(using closeable)(using summon)

        closeable.closeOrThrow()
        assertTrue(n == 99)
      }
    )
  )
}
