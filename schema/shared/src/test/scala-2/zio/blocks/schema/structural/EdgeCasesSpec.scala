package zio.blocks.schema.structural

import zio.test._
import zio.blocks.schema._

/**
 * Edge case tests for Scala 2 structural types. Covers: nested collections,
 * non-String map keys, empty collections, large tuples. Note: Sum types (sealed
 * traits, Either) are not supported in Scala 2.
 */
object EdgeCasesSpec extends ZIOSpecDefault {

  // Nested collections
  case class NestedList(values: List[List[Int]])
  case class NestedVector(values: Vector[Vector[String]])
  case class MapOfLists(data: Map[String, List[Int]])
  case class ListOfMaps(items: List[Map[String, String]])
  case class DeeplyNested(data: List[Vector[Set[Int]]])

  // Non-String map keys
  case class IntKeyMap(data: Map[Int, String])
  case class LongKeyMap(data: Map[Long, String])

  // Empty collections
  case class WithEmptyList(values: List[String])
  case class WithEmptyMap(data: Map[String, Int])
  case class WithEmptyOption(value: Option[String])

  // Large tuples
  case class WithTuple4(value: (Int, String, Boolean, Double))
  case class WithTuple5(value: (Int, String, Boolean, Double, Long))
  case class Address(city: String, zip: Int)
  case class TupleWithCaseClass(value: (String, Address, Int))
  case class NestedTuple(value: ((Int, String), (Boolean, Double)))

  def spec = suite("EdgeCasesSpec (Scala 2)")(
    suite("Nested Collections")(
      test("List[List[Int]]") {
        val ts                                  = ToStructural.derived[NestedList]
        implicit val schema: Schema[NestedList] = Schema.derived[NestedList]
        val structSchema                        = ts.structuralSchema

        val original   = NestedList(List(List(1, 2), List(3, 4, 5), List()))
        val structural = ts.toStructural(original)

        assertTrue(structural.values == List(List(1, 2), List(3, 4, 5), List()))

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.values) == Right(List(List(1, 2), List(3, 4, 5), List()))
        )
      },
      test("Vector[Vector[String]]") {
        val ts                                    = ToStructural.derived[NestedVector]
        implicit val schema: Schema[NestedVector] = Schema.derived[NestedVector]
        val structSchema                          = ts.structuralSchema

        val original   = NestedVector(Vector(Vector("a", "b"), Vector("c")))
        val structural = ts.toStructural(original)

        assertTrue(structural.values == Vector(Vector("a", "b"), Vector("c")))

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.values) == Right(Vector(Vector("a", "b"), Vector("c")))
        )
      },
      test("Map[String, List[Int]]") {
        val ts                                  = ToStructural.derived[MapOfLists]
        implicit val schema: Schema[MapOfLists] = Schema.derived[MapOfLists]
        val structSchema                        = ts.structuralSchema

        val original   = MapOfLists(Map("nums" -> List(1, 2, 3), "empty" -> List()))
        val structural = ts.toStructural(original)

        assertTrue(structural.data == Map("nums" -> List(1, 2, 3), "empty" -> List()))

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.data) == Right(Map("nums" -> List(1, 2, 3), "empty" -> List()))
        )
      },
      test("List[Map[String, String]]") {
        val ts                                  = ToStructural.derived[ListOfMaps]
        implicit val schema: Schema[ListOfMaps] = Schema.derived[ListOfMaps]
        val structSchema                        = ts.structuralSchema

        val original   = ListOfMaps(List(Map("a" -> "1"), Map("b" -> "2", "c" -> "3")))
        val structural = ts.toStructural(original)

        assertTrue(structural.items == List(Map("a" -> "1"), Map("b" -> "2", "c" -> "3")))

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.items) == Right(List(Map("a" -> "1"), Map("b" -> "2", "c" -> "3")))
        )
      },
      test("deeply nested: List[Vector[Set[Int]]]") {
        val ts                                    = ToStructural.derived[DeeplyNested]
        implicit val schema: Schema[DeeplyNested] = Schema.derived[DeeplyNested]
        val structSchema                          = ts.structuralSchema

        val original   = DeeplyNested(List(Vector(Set(1, 2), Set(3)), Vector(Set(4))))
        val structural = ts.toStructural(original)

        assertTrue(structural.data == List(Vector(Set(1, 2), Set(3)), Vector(Set(4))))

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.data) == Right(List(Vector(Set(1, 2), Set(3)), Vector(Set(4))))
        )
      }
    ),
    suite("Non-String Map Keys")(
      test("Map[Int, String]") {
        val ts                                 = ToStructural.derived[IntKeyMap]
        implicit val schema: Schema[IntKeyMap] = Schema.derived[IntKeyMap]
        val structSchema                       = ts.structuralSchema

        val original   = IntKeyMap(Map(1 -> "one", 2 -> "two", 3 -> "three"))
        val structural = ts.toStructural(original)

        assertTrue(structural.data == Map(1 -> "one", 2 -> "two", 3 -> "three"))

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.data) == Right(Map(1 -> "one", 2 -> "two", 3 -> "three"))
        )
      },
      test("Map[Long, String]") {
        val ts                                  = ToStructural.derived[LongKeyMap]
        implicit val schema: Schema[LongKeyMap] = Schema.derived[LongKeyMap]
        val structSchema                        = ts.structuralSchema

        val original   = LongKeyMap(Map(100L -> "hundred", 1000L -> "thousand"))
        val structural = ts.toStructural(original)

        assertTrue(structural.data == Map(100L -> "hundred", 1000L -> "thousand"))

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.data) == Right(Map(100L -> "hundred", 1000L -> "thousand"))
        )
      }
    ),
    suite("Empty Collections")(
      test("empty List") {
        val ts                                     = ToStructural.derived[WithEmptyList]
        implicit val schema: Schema[WithEmptyList] = Schema.derived[WithEmptyList]
        val structSchema                           = ts.structuralSchema

        val original   = WithEmptyList(List.empty)
        val structural = ts.toStructural(original)

        assertTrue(structural.values == List.empty)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.values) == Right(List.empty[String])
        )
      },
      test("empty Map") {
        val ts                                    = ToStructural.derived[WithEmptyMap]
        implicit val schema: Schema[WithEmptyMap] = Schema.derived[WithEmptyMap]
        val structSchema                          = ts.structuralSchema

        val original   = WithEmptyMap(Map.empty)
        val structural = ts.toStructural(original)

        assertTrue(structural.data == Map.empty)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.data) == Right(Map.empty[String, Int])
        )
      },
      test("None Option") {
        val ts                                       = ToStructural.derived[WithEmptyOption]
        implicit val schema: Schema[WithEmptyOption] = Schema.derived[WithEmptyOption]
        val structSchema                             = ts.structuralSchema

        val original   = WithEmptyOption(None)
        val structural = ts.toStructural(original)

        assertTrue(structural.value == None)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.value) == Right(None)
        )
      }
    ),
    suite("Large Tuples")(
      test("Tuple4") {
        val ts                                  = ToStructural.derived[WithTuple4]
        implicit val schema: Schema[WithTuple4] = Schema.derived[WithTuple4]
        val structSchema                        = ts.structuralSchema

        val original   = WithTuple4((42, "hello", true, 3.14))
        val structural = ts.toStructural(original)

        val tuple = structural.value.asInstanceOf[StructuralRecord]
        assertTrue(
          tuple.selectDynamic("_1") == 42,
          tuple.selectDynamic("_2") == "hello",
          tuple.selectDynamic("_3") == true,
          tuple.selectDynamic("_4") == 3.14
        )

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(roundTrip.isRight)
        val rtTuple = roundTrip.toOption.get.value.asInstanceOf[StructuralRecord]
        assertTrue(
          rtTuple.selectDynamic("_1") == 42,
          rtTuple.selectDynamic("_2") == "hello"
        )
      },
      test("Tuple5") {
        val ts                                  = ToStructural.derived[WithTuple5]
        implicit val schema: Schema[WithTuple5] = Schema.derived[WithTuple5]
        val structSchema                        = ts.structuralSchema

        val original   = WithTuple5((1, "two", false, 4.0, 5L))
        val structural = ts.toStructural(original)

        val tuple = structural.value.asInstanceOf[StructuralRecord]
        assertTrue(
          tuple.selectDynamic("_1") == 1,
          tuple.selectDynamic("_5") == 5L
        )

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(roundTrip.isRight)
      },
      test("tuple with case class") {
        val ts                                          = ToStructural.derived[TupleWithCaseClass]
        implicit val schema: Schema[TupleWithCaseClass] = Schema.derived[TupleWithCaseClass]
        val structSchema                                = ts.structuralSchema

        val original   = TupleWithCaseClass(("label", Address("NYC", 10001), 42))
        val structural = ts.toStructural(original)

        val tuple = structural.value.asInstanceOf[StructuralRecord]
        val addr  = tuple.selectDynamic("_2").asInstanceOf[StructuralRecord]
        assertTrue(
          tuple.selectDynamic("_1") == "label",
          addr.city == "NYC",
          addr.zip == 10001,
          tuple.selectDynamic("_3") == 42
        )

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(roundTrip.isRight)
      },
      test("nested tuple") {
        val ts                                   = ToStructural.derived[NestedTuple]
        implicit val schema: Schema[NestedTuple] = Schema.derived[NestedTuple]
        val structSchema                         = ts.structuralSchema

        val original   = NestedTuple(((1, "one"), (true, 2.5)))
        val structural = ts.toStructural(original)

        val outer  = structural.value.asInstanceOf[StructuralRecord]
        val inner1 = outer.selectDynamic("_1").asInstanceOf[StructuralRecord]
        val inner2 = outer.selectDynamic("_2").asInstanceOf[StructuralRecord]
        assertTrue(
          inner1.selectDynamic("_1") == 1,
          inner1.selectDynamic("_2") == "one",
          inner2.selectDynamic("_1") == true,
          inner2.selectDynamic("_2") == 2.5
        )

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(roundTrip.isRight)
      }
    ),
    suite("Nested Case Classes in Collections")(
      test("List of nested case class") {
        case class Inner(value: Int)
        case class Outer(inners: List[Inner])

        val ts                             = ToStructural.derived[Outer]
        implicit val schema: Schema[Outer] = Schema.derived[Outer]
        val structSchema                   = ts.structuralSchema

        val original   = Outer(List(Inner(1), Inner(2), Inner(3)))
        val structural = ts.toStructural(original)

        val inners = structural.inners.asInstanceOf[List[StructuralRecord]]
        assertTrue(
          inners.size == 3,
          inners(0).value == 1,
          inners(1).value == 2,
          inners(2).value == 3
        )

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(roundTrip.isRight)
        val rtInners = roundTrip.toOption.get.inners.asInstanceOf[List[StructuralRecord]]
        assertTrue(rtInners(0).value == 1)
      },
      test("Map with case class values") {
        case class Value(data: String)
        case class Container(items: Map[String, Value])

        val ts                                 = ToStructural.derived[Container]
        implicit val schema: Schema[Container] = Schema.derived[Container]
        val structSchema                       = ts.structuralSchema

        val original   = Container(Map("a" -> Value("first"), "b" -> Value("second")))
        val structural = ts.toStructural(original)

        val items = structural.items.asInstanceOf[Map[String, StructuralRecord]]
        assertTrue(
          items("a").data == "first",
          items("b").data == "second"
        )

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(roundTrip.isRight)
      },
      test("Option of case class") {
        case class Data(name: String, count: Int)
        case class Container(data: Option[Data])

        val ts                                 = ToStructural.derived[Container]
        implicit val schema: Schema[Container] = Schema.derived[Container]
        val structSchema                       = ts.structuralSchema

        val original   = Container(Some(Data("test", 42)))
        val structural = ts.toStructural(original)

        val data = structural.data.asInstanceOf[Option[StructuralRecord]].get
        assertTrue(
          data.name == "test",
          data.count == 42
        )

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(roundTrip.isRight)
      }
    ),
    suite("Complex Combinations")(
      test("case class with multiple collection types") {
        case class Complex(
          list: List[Int],
          vector: Vector[String],
          set: Set[Double],
          map: Map[String, Boolean],
          option: Option[Long]
        )

        val ts                               = ToStructural.derived[Complex]
        implicit val schema: Schema[Complex] = Schema.derived[Complex]
        val structSchema                     = ts.structuralSchema

        val original = Complex(
          list = List(1, 2, 3),
          vector = Vector("a", "b"),
          set = Set(1.1, 2.2),
          map = Map("yes" -> true, "no" -> false),
          option = Some(42L)
        )
        val structural = ts.toStructural(original)

        assertTrue(
          structural.list == List(1, 2, 3),
          structural.vector == Vector("a", "b"),
          structural.set == Set(1.1, 2.2),
          structural.map == Map("yes" -> true, "no" -> false),
          structural.option == Some(42L)
        )

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.list) == Right(List(1, 2, 3)),
          roundTrip.map(_.vector) == Right(Vector("a", "b")),
          roundTrip.map(_.option) == Right(Some(42L))
        )
      },
      test("deeply nested structure with collections") {
        case class Level2(values: List[Int])
        case class Level1(level2: Level2, name: String)
        case class Root(items: Vector[Level1])

        val ts                            = ToStructural.derived[Root]
        implicit val schema: Schema[Root] = Schema.derived[Root]
        val structSchema                  = ts.structuralSchema

        val original = Root(
          Vector(
            Level1(Level2(List(1, 2)), "first"),
            Level1(Level2(List(3, 4, 5)), "second")
          )
        )
        val structural = ts.toStructural(original)

        val items = structural.items.asInstanceOf[Vector[StructuralRecord]]
        val first = items(0)
        val l2    = first.level2.asInstanceOf[StructuralRecord]
        assertTrue(
          items.size == 2,
          first.name == "first",
          l2.values == List(1, 2)
        )

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(roundTrip.isRight)
      }
    )
  )
}
