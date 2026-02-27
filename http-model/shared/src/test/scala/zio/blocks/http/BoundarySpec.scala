package zio.blocks.http

import zio.test._

object BoundarySpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Boundary")(
    suite("construction")(
      test("value returns the string passed to constructor") {
        assertTrue(Boundary("abc123").value == "abc123")
      },
      test("equality works by value") {
        assertTrue(Boundary("x") == Boundary("x"))
      },
      test("toString returns the value") {
        assertTrue(Boundary("test-boundary").toString == "test-boundary")
      }
    ),
    suite("generate")(
      test("produces a non-empty boundary") {
        val b = Boundary.generate
        assertTrue(b.value.nonEmpty)
      },
      test("produces 24-character boundaries") {
        val b = Boundary.generate
        assertTrue(b.value.length == 24)
      },
      test("produces different values on successive calls") {
        val b1 = Boundary.generate
        val b2 = Boundary.generate
        assertTrue(b1 != b2)
      },
      test("generated boundary contains only alphanumeric characters") {
        val b = Boundary.generate
        assertTrue(b.value.forall(c => c.isLetterOrDigit))
      }
    )
  )
}
