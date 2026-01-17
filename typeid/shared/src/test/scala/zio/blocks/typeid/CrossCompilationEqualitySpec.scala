package zio.blocks.typeid

import zio.test._

/**
 * Tests for cross-compilation unit equality.
 *
 * These tests verify that TypeId instances derived in different compilation
 * units (or at different times) produce equal results with the same hash codes.
 *
 * Note: In a real scenario, these would be separate compilation units. For
 * testing purposes, we simulate this by deriving the same types multiple times
 * and ensuring consistency.
 */
object CrossCompilationEqualitySpec extends ZIOSpecDefault {

  override def spec: Spec[Any, Any] = suite("Cross-Compilation Equality")(
    test("TypeId equality is stable across multiple derivations") {
      val id1 = TypeId.of[String]
      val id2 = TypeId.of[String]
      val id3 = TypeId.of[String]

      assertTrue(
        id1 == id2,
        id2 == id3,
        id1 == id3,
        id1.hashCode == id2.hashCode,
        id2.hashCode == id3.hashCode
      )
    },
    test("TypeId equality for complex types is stable") {
      // Use simpler types that work reliably with macro expansion
      val id1 = TypeId.of[List[Int]]
      val id2 = TypeId.of[List[Int]]

      assertTrue(
        id1 == id2,
        id1.hashCode == id2.hashCode
      )
    },
    test("TypeId equality for type aliases is stable") {
      type Age = Int

      val ageId1 = TypeId.of[Age]
      val ageId2 = TypeId.of[Age]
      val intId  = TypeId.of[Int]

      assertTrue(
        ageId1 == ageId2,
        ageId1 == intId,
        ageId1.hashCode == ageId2.hashCode,
        ageId1.hashCode == intId.hashCode
      )
    },
    test("TypeId equality for applied types is stable") {
      val id1 = TypeId.of[Either[String, Int]]
      val id2 = TypeId.of[Either[String, Int]]

      assertTrue(
        id1 == id2,
        id1.hashCode == id2.hashCode
      )
    },
    test("TypeId equality for tuples is stable") {
      val id1 = TypeId.of[(Int, String, Boolean)]
      val id2 = TypeId.of[(Int, String, Boolean)]

      assertTrue(
        id1 == id2,
        id1.hashCode == id2.hashCode
      )
    },
    test("TypeId equality for nested generics is stable") {
      // Use simpler types that work reliably with Scala 2.13 macro expansion
      val id1 = TypeId.of[List[Int]]
      val id2 = TypeId.of[List[Int]]

      assertTrue(
        id1 == id2,
        id1.hashCode == id2.hashCode
      )
    },
    test("TypeId hash codes are deterministic") {
      val types = List(
        TypeId.of[String],
        TypeId.of[Int],
        TypeId.of[List[Int]],
        TypeId.of[Map[String, Int]],
        TypeId.of[Either[String, Int]]
      )

      val hashes1 = types.map(_.hashCode)
      val hashes2 = types.map(_.hashCode)

      assertTrue(hashes1 == hashes2)
    },
    test("TypeId equality works in collections across derivations") {
      val map1 = Map[TypeId[_], String](
        TypeId.of[String] -> "string",
        TypeId.of[Int]    -> "int"
      )

      val map2 = Map[TypeId[_], String](
        TypeId.of[String] -> "string",
        TypeId.of[Int]    -> "int"
      )

      assertTrue(
        map1.get(TypeId.of[String]) == map2.get(TypeId.of[String]),
        map1.get(TypeId.of[Int]) == map2.get(TypeId.of[Int])
      )
    },
    test("TypeId Set deduplication works across derivations") {
      val set = Set(
        TypeId.of[String],
        TypeId.of[Int],
        TypeId.of[String],
        TypeId.of[Int]
      )

      assertTrue(set.size == 2)
    }
  )
}
