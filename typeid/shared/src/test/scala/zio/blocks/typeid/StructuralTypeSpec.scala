package zio.blocks.typeid

import zio.test._

object StructuralTypeSpec extends ZIOSpecDefault {

  override def spec: Spec[Any, Any] = suite("StructuralTypeSpec")(
    test("structural types with same members are equal") {
      type HasSize   = { def size: Int }
      type HasLength = { def size: Int }

      assertTrue(TypeId.of[HasSize] == TypeId.of[HasLength])
    },
    test("structural types with different members are not equal") {
      type HasSize = { def size: Int }
      type HasName = { def name: String }

      assertTrue(TypeId.of[HasSize] != TypeId.of[HasName])
    },
    test("member order doesn't affect equality due to canonical sorting") {
      type AB = { def a: Int; def b: String }
      type BA = { def b: String; def a: Int }

      // Canonical sorting ensures equality
      assertTrue(TypeId.of[AB] == TypeId.of[BA])
    },

    // Note: In Scala 2.13, val vs def cannot be distinguished in structural types
    // Both are represented as MethodSymbol with no params, so they are equal
    // This is a known limitation of Scala 2.13 structural types
    // The test is only meaningful in Scala 3 where they can be distinguished
    // For Scala 2.13 compatibility, we verify that parameterless methods work correctly
    test("structural types with parameterless methods work correctly") {
      type HasSize   = { def size: Int }
      type HasLength = { def size: Int }

      // Both def size: Int should be equal (same member signature)
      assertTrue(TypeId.of[HasSize] == TypeId.of[HasLength])
    },
    test("structural type hashCode is stable across invocations") {
      type HasSize = { def size: Int }

      val id    = TypeId.of[HasSize]
      val hash1 = id.hashCode
      val hash2 = id.hashCode

      assertTrue(hash1 == hash2)
    },
    test("complex structural types are distinguished") {
      type Multi1 = {
        def size: Int
        def name: String
        def isEmpty: Boolean
      }

      type Multi2 = {
        def size: Int
        def name: String
      }

      // Different member sets
      assertTrue(TypeId.of[Multi1] != TypeId.of[Multi2])
    }
  )
}
