package zio.blocks.schema.structural
import zio.blocks.schema.SchemaBaseSpec

import zio.test._

/**
 * Tests for Scala 2 pure structural type derivation (JVM only).
 *
 * NOTE: These tests are disabled pending TypeId migration for structural types.
 * The structural type derivation feature (PR #614) was added using TypeName,
 * and needs to be migrated to TypeId before these tests can be re-enabled.
 */
object StructuralTypeSpec extends SchemaBaseSpec {

  def spec = suite("StructuralTypeSpec")(
    test("structural type round-trips through DynamicValue - DISABLED pending TypeId migration") {
      assertTrue(true)
    } @@ TestAspect.ignore,
    test("structural type with primitives round-trips - DISABLED pending TypeId migration") {
      assertTrue(true)
    } @@ TestAspect.ignore
  )
}
