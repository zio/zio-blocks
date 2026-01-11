package zio.blocks.schema.stress

import zio.blocks.schema._
import zio.test._

/**
 * Stress tests for Into and As with large products (many fields).
 *
 * These tests verify that the macro can handle products with 20+ fields and
 * that conversions work correctly with large data structures.
 */
object LargeProductStressSpec extends ZIOSpecDefault {

  // 10-field product
  case class Product10A(
    field1: Int,
    field2: String,
    field3: Long,
    field4: Boolean,
    field5: Double,
    field6: Int,
    field7: String,
    field8: Long,
    field9: Boolean,
    field10: Double
  )
  case class Product10B(
    field1: Int,
    field2: String,
    field3: Long,
    field4: Boolean,
    field5: Double,
    field6: Int,
    field7: String,
    field8: Long,
    field9: Boolean,
    field10: Double
  )

  // 20-field product
  case class Product20A(
    f1: Int,
    f2: String,
    f3: Long,
    f4: Boolean,
    f5: Double,
    f6: Int,
    f7: String,
    f8: Long,
    f9: Boolean,
    f10: Double,
    f11: Int,
    f12: String,
    f13: Long,
    f14: Boolean,
    f15: Double,
    f16: Int,
    f17: String,
    f18: Long,
    f19: Boolean,
    f20: Double
  )
  case class Product20B(
    f1: Int,
    f2: String,
    f3: Long,
    f4: Boolean,
    f5: Double,
    f6: Int,
    f7: String,
    f8: Long,
    f9: Boolean,
    f10: Double,
    f11: Int,
    f12: String,
    f13: Long,
    f14: Boolean,
    f15: Double,
    f16: Int,
    f17: String,
    f18: Long,
    f19: Boolean,
    f20: Double
  )

  // Product with all primitive types
  case class AllPrimitivesA(
    byteVal: Byte,
    shortVal: Short,
    intVal: Int,
    longVal: Long,
    floatVal: Float,
    doubleVal: Double,
    boolVal: Boolean,
    charVal: Char,
    stringVal: String
  )
  case class AllPrimitivesB(
    byteVal: Byte,
    shortVal: Short,
    intVal: Int,
    longVal: Long,
    floatVal: Float,
    doubleVal: Double,
    boolVal: Boolean,
    charVal: Char,
    stringVal: String
  )

  // Product with various collection types
  case class CollectionsA(
    listVal: List[Int],
    vectorVal: Vector[String],
    setVal: Set[Long],
    mapVal: Map[String, Int],
    optionVal: Option[Double],
    seqVal: Seq[Boolean]
  )
  case class CollectionsB(
    listVal: List[Int],
    vectorVal: Vector[String],
    setVal: Set[Long],
    mapVal: Map[String, Int],
    optionVal: Option[Double],
    seqVal: Seq[Boolean]
  )

  // Product with nested collections
  case class NestedCollectionsA(
    listOfLists: List[List[Int]],
    mapOfMaps: Map[String, Map[String, Int]],
    optionOfList: Option[List[String]],
    listOfOptions: List[Option[Int]]
  )
  case class NestedCollectionsB(
    listOfLists: List[List[Int]],
    mapOfMaps: Map[String, Map[String, Int]],
    optionOfList: Option[List[String]],
    listOfOptions: List[Option[Int]]
  )

  // Product with large collections
  case class LargeCollectionA(items: List[Int])
  case class LargeCollectionB(items: List[Int])

  def spec = suite("LargeProductStressSpec")(
    suite("Into - Large Products")(
      test("converts 10-field product correctly") {
        val source = Product10A(1, "a", 2L, true, 3.0, 4, "b", 5L, false, 6.0)
        val into   = Into.derived[Product10A, Product10B]

        val result = into.into(source)
        assertTrue(
          result == Right(Product10B(1, "a", 2L, true, 3.0, 4, "b", 5L, false, 6.0))
        )
      },
      test("converts 20-field product correctly") {
        val source = Product20A(
          1,
          "a",
          2L,
          true,
          3.0,
          4,
          "b",
          5L,
          false,
          6.0,
          7,
          "c",
          8L,
          true,
          9.0,
          10,
          "d",
          11L,
          false,
          12.0
        )
        val into = Into.derived[Product20A, Product20B]

        val result = into.into(source)
        assertTrue(
          result.isRight,
          result.map(_.f1) == Right(1),
          result.map(_.f10) == Right(6.0),
          result.map(_.f20) == Right(12.0)
        )
      },
      test("converts product with all primitive types") {
        val source = AllPrimitivesA(
          1.toByte,
          2.toShort,
          3,
          4L,
          5.0f,
          6.0,
          true,
          'x',
          "test"
        )
        val into = Into.derived[AllPrimitivesA, AllPrimitivesB]

        val result = into.into(source)
        assertTrue(
          result == Right(
            AllPrimitivesB(
              1.toByte,
              2.toShort,
              3,
              4L,
              5.0f,
              6.0,
              true,
              'x',
              "test"
            )
          )
        )
      }
    ),
    suite("Into - Collection Types")(
      test("converts product with various collections") {
        val source = CollectionsA(
          List(1, 2, 3),
          Vector("a", "b"),
          Set(1L, 2L),
          Map("x" -> 1, "y" -> 2),
          Some(3.14),
          Seq(true, false)
        )
        val into = Into.derived[CollectionsA, CollectionsB]

        val result = into.into(source)
        assertTrue(
          result.isRight,
          result.map(_.listVal) == Right(List(1, 2, 3)),
          result.map(_.optionVal) == Right(Some(3.14))
        )
      },
      test("converts product with nested collections") {
        val source = NestedCollectionsA(
          List(List(1, 2), List(3, 4)),
          Map("outer" -> Map("inner" -> 42)),
          Some(List("a", "b", "c")),
          List(Some(1), None, Some(3))
        )
        val into = Into.derived[NestedCollectionsA, NestedCollectionsB]

        val result = into.into(source)
        assertTrue(result.isRight, result.map(_.listOfLists) == Right(List(List(1, 2), List(3, 4))))
      },
      test("converts product with large collection (1000 elements)") {
        val largeList = (1 to 1000).toList
        val source    = LargeCollectionA(largeList)
        val into      = Into.derived[LargeCollectionA, LargeCollectionB]

        val result = into.into(source)
        assertTrue(
          result.isRight,
          result.map(_.items.size) == Right(1000),
          result.map(_.items.head) == Right(1),
          result.map(_.items.last) == Right(1000)
        )
      },
      test("converts product with very large collection (10000 elements)") {
        val largeList = (1 to 10000).toList
        val source    = LargeCollectionA(largeList)
        val into      = Into.derived[LargeCollectionA, LargeCollectionB]

        val result = into.into(source)
        assertTrue(result.isRight, result.map(_.items.size) == Right(10000))
      }
    ),
    suite("As - Large Products Round-Trip")(
      test("10-field product round-trips correctly") {
        val source                                  = Product10A(1, "a", 2L, true, 3.0, 4, "b", 5L, false, 6.0)
        implicit val as: As[Product10A, Product10B] = As.derived[Product10A, Product10B]

        val forward = as.into(source)
        assertTrue(forward.isRight, forward.flatMap(as.from) == Right(source))
      },
      test("20-field product round-trips correctly") {
        val source = Product20A(
          1,
          "a",
          2L,
          true,
          3.0,
          4,
          "b",
          5L,
          false,
          6.0,
          7,
          "c",
          8L,
          true,
          9.0,
          10,
          "d",
          11L,
          false,
          12.0
        )
        implicit val as: As[Product20A, Product20B] = As.derived[Product20A, Product20B]

        val forward = as.into(source)
        assertTrue(forward.isRight, forward.flatMap(as.from) == Right(source))
      },
      test("collections product round-trips correctly") {
        val source = CollectionsA(
          List(1, 2, 3),
          Vector("a", "b"),
          Set(1L, 2L),
          Map("x" -> 1, "y" -> 2),
          Some(3.14),
          Seq(true, false)
        )
        implicit val as: As[CollectionsA, CollectionsB] = As.derived[CollectionsA, CollectionsB]

        val forward = as.into(source)
        assertTrue(forward.isRight, forward.flatMap(as.from) == Right(source))
      },
      test("large collection round-trips correctly") {
        val largeList                                           = (1 to 1000).toList
        val source                                              = LargeCollectionA(largeList)
        implicit val as: As[LargeCollectionA, LargeCollectionB] = As.derived[LargeCollectionA, LargeCollectionB]

        val forward = as.into(source)
        assertTrue(forward.isRight, forward.flatMap(as.from) == Right(source))
      }
    ),
    suite("Same Type Conversions")(
      test("same 20-field product converts to itself") {
        val source = Product20A(
          1,
          "a",
          2L,
          true,
          3.0,
          4,
          "b",
          5L,
          false,
          6.0,
          7,
          "c",
          8L,
          true,
          9.0,
          10,
          "d",
          11L,
          false,
          12.0
        )
        val into = Into.derived[Product20A, Product20A]

        val result = into.into(source)
        assertTrue(result == Right(source))
      },
      test("same collections product converts to itself") {
        val source = CollectionsA(
          List(1, 2, 3),
          Vector("a", "b"),
          Set(1L, 2L),
          Map("x" -> 1),
          None,
          Seq(true)
        )
        val into = Into.derived[CollectionsA, CollectionsA]

        val result = into.into(source)
        assertTrue(result == Right(source))
      }
    )
  )
}
