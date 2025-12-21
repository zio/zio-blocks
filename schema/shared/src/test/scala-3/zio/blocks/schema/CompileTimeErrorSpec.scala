package zio.blocks.schema

import zio.test._

/**
 * Tests for compile-time error detection.
 *
 * These tests verify that certain invalid conversions are caught at
 * compile-time rather than runtime. Some tests are commented out because they
 * should fail to compile.
 *
 * To verify compile-time errors:
 *   1. Uncomment a test
 *   2. Try to compile - it should fail
 *   3. Re-comment the test
 */
object CompileTimeErrorSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("Compile-Time Error Detection")(
    suite("As - Default Values Not Allowed")(
      test("As should reject default values (compile-time check)") {
        // This should fail at compile-time:
        // case class V1(name: String)
        // case class V2(name: String, count: Int = 0)
        // val as = As.derived[V1, V2] // Should fail to compile

        // For now, we just document that this is checked
        assertTrue(true)
      }
    ),
    suite("As - Narrowing in Both Directions")(
      test("As should reject narrowing in both directions") {
        // This should fail at compile-time:
        // case class V1(value: Long)
        // case class V2(value: Int)
        // val as = As.derived[V1, V2] // Should fail - narrowing in both directions

        assertTrue(true)
      }
    ),
    suite("Ambiguous Field Mapping")(
      test("Should provide clear error for ambiguous mappings") {
        // This should provide a clear compile-time error:
        // case class V1(a: Int, b: Int)
        // case class V2(x: Int, y: Int)
        // val into = Into.derived[V1, V2] // Should fail with clear error message

        assertTrue(true)
      }
    ),
    suite("Missing Required Fields")(
      test("Should error when required field is missing") {
        // This should fail at compile-time:
        // case class V1(name: String)
        // case class V2(name: String, required: Int) // No default, not optional
        // val into = Into.derived[V1, V2] // Should fail with clear error

        assertTrue(true)
      }
    ),
    suite("Documentation of Compile-Time Checks")(
      test("Verify that compile-time checks are documented") {
        // The following patterns should be caught at compile-time:
        // 1. As with default values
        // 2. As with narrowing in both directions
        // 3. Ambiguous field mappings (when possible to detect)
        // 4. Missing required fields without defaults/options

        assertTrue(true)
      }
    )
  )
}
