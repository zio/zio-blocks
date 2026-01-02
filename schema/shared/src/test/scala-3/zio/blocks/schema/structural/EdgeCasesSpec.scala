package zio.blocks.schema.structural

import zio.test._
import zio.blocks.schema._

/**
 * Edge case tests for structural types. Covers: nested collections, non-String
 * map keys, empty collections, large tuples.
 */
object EdgeCasesSpec extends ZIOSpecDefault {

  // ===========================================
  // NESTED COLLECTIONS
  // ===========================================

  case class WithNestedList(matrix: List[List[Int]])
  case class WithNestedVector(matrix: Vector[Vector[String]])
  case class WithListOfMap(data: List[Map[String, Int]])
  case class WithMapOfList(data: Map[String, List[Int]])
  case class WithListOfOption(items: List[Option[String]])
  case class WithOptionOfList(maybeItems: Option[List[String]])
  case class WithDeepNesting(data: List[List[List[Int]]])
  case class WithMixedNesting(data: Map[String, List[Option[Int]]])

  // ===========================================
  // NON-STRING MAP KEYS
  // ===========================================

  case class WithIntKeys(data: Map[Int, String])
  case class WithLongKeys(data: Map[Long, String])

  // Case class as map key (if supported)
  case class SimpleKey(id: Int)
  case class WithCaseClassKeys(data: Map[SimpleKey, String])

  // Tuple as map value
  case class WithTupleValue(data: Map[String, (Int, String)])

  // ===========================================
  // EMPTY COLLECTIONS
  // ===========================================

  case class WithEmptyList(items: List[String])
  case class WithEmptyVector(items: Vector[Int])
  case class WithEmptySet(items: Set[String])
  case class WithEmptyMap(data: Map[String, Int])
  case class WithEmptyOption(value: Option[String])
  case class AllEmpty(
    list: List[Int],
    vector: Vector[String],
    set: Set[Double],
    map: Map[String, Boolean]
  )

  // ===========================================
  // LARGE TUPLES
  // ===========================================

  case class WithTuple4(value: (Int, String, Boolean, Double))
  case class WithTuple5(value: (Int, String, Boolean, Double, Long))
  case class WithTuple10(value: (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int))
  case class WithTuple22(
    value: (
      Int,
      Int,
      Int,
      Int,
      Int,
      Int,
      Int,
      Int,
      Int,
      Int,
      Int,
      Int,
      Int,
      Int,
      Int,
      Int,
      Int,
      Int,
      Int,
      Int,
      Int,
      Int
    )
  )

  // Nested tuples
  case class WithNestedTuples(value: ((Int, String), (Boolean, Double)))
  case class WithTupleOfList(value: (List[Int], List[String]))
  case class WithTupleOfOption(value: (Option[Int], Option[String]))

  // Tuple containing case class
  case class SimpleData(x: Int, y: String)
  case class WithTupleOfCaseClass(value: (SimpleData, SimpleData))

  def spec = suite("EdgeCasesSpec")(
    // ===========================================
    // NESTED COLLECTIONS
    // ===========================================
    suite("Nested Collections - ToStructural")(
      test("List[List[Int]]") {
        val ts                       = ToStructural.derived[WithNestedList]
        given Schema[WithNestedList] = Schema.derived[WithNestedList]
        val s                        = ts.toStructural(WithNestedList(List(List(1, 2), List(3, 4, 5))))
        assertTrue(s.matrix == List(List(1, 2), List(3, 4, 5)))
        assertTrue(ts.structuralSchema.reflect.typeName.name == """{matrix:List[List[Int]]}""")
      },
      test("Vector[Vector[String]]") {
        val ts                         = ToStructural.derived[WithNestedVector]
        given Schema[WithNestedVector] = Schema.derived[WithNestedVector]
        val s                          = ts.toStructural(WithNestedVector(Vector(Vector("a", "b"), Vector("c"))))
        assertTrue(s.matrix == Vector(Vector("a", "b"), Vector("c")))
        assertTrue(ts.structuralSchema.reflect.typeName.name == """{matrix:Vector[Vector[String]]}""")
      },
      test("List[Map[String, Int]]") {
        val ts                      = ToStructural.derived[WithListOfMap]
        given Schema[WithListOfMap] = Schema.derived[WithListOfMap]
        val s                       = ts.toStructural(WithListOfMap(List(Map("a" -> 1), Map("b" -> 2, "c" -> 3))))
        assertTrue(s.data == List(Map("a" -> 1), Map("b" -> 2, "c" -> 3)))
        assertTrue(ts.structuralSchema.reflect.typeName.name == """{data:List[Map[String,Int]]}""")
      },
      test("Map[String, List[Int]]") {
        val ts                      = ToStructural.derived[WithMapOfList]
        given Schema[WithMapOfList] = Schema.derived[WithMapOfList]
        val s                       = ts.toStructural(WithMapOfList(Map("x" -> List(1, 2), "y" -> List(3))))
        assertTrue(s.data == Map("x" -> List(1, 2), "y" -> List(3)))
        assertTrue(ts.structuralSchema.reflect.typeName.name == """{data:Map[String,List[Int]]}""")
      },
      test("List[Option[String]]") {
        val ts                         = ToStructural.derived[WithListOfOption]
        given Schema[WithListOfOption] = Schema.derived[WithListOfOption]
        val s                          = ts.toStructural(WithListOfOption(List(Some("a"), None, Some("b"))))
        assertTrue(s.items == List(Some("a"), None, Some("b")))
        assertTrue(ts.structuralSchema.reflect.typeName.name == """{items:List[Option[String]]}""")
      },
      test("Option[List[String]]") {
        val ts                         = ToStructural.derived[WithOptionOfList]
        given Schema[WithOptionOfList] = Schema.derived[WithOptionOfList]
        val s1                         = ts.toStructural(WithOptionOfList(Some(List("a", "b"))))
        val s2                         = ts.toStructural(WithOptionOfList(None))
        assertTrue(
          s1.maybeItems == Some(List("a", "b")),
          s2.maybeItems == None
        )
        assertTrue(ts.structuralSchema.reflect.typeName.name == """{maybeItems:Option[List[String]]}""")
      },
      test("List[List[List[Int]]] - deeply nested") {
        val ts                        = ToStructural.derived[WithDeepNesting]
        given Schema[WithDeepNesting] = Schema.derived[WithDeepNesting]
        val s                         = ts.toStructural(WithDeepNesting(List(List(List(1, 2), List(3)), List(List(4)))))
        assertTrue(s.data == List(List(List(1, 2), List(3)), List(List(4))))
        assertTrue(ts.structuralSchema.reflect.typeName.name == """{data:List[List[List[Int]]]}""")
      },
      test("Map[String, List[Option[Int]]] - mixed nesting") {
        val ts                         = ToStructural.derived[WithMixedNesting]
        given Schema[WithMixedNesting] = Schema.derived[WithMixedNesting]
        val s                          = ts.toStructural(WithMixedNesting(Map("k" -> List(Some(1), None, Some(2)))))
        assertTrue(s.data == Map("k" -> List(Some(1), None, Some(2))))
        assertTrue(ts.structuralSchema.reflect.typeName.name == """{data:Map[String,List[Option[Int]]]}""")
      }
    ),
    suite("Nested Collections - Round-Trip")(
      test("List[List[Int]] round-trip") {
        val ts                       = ToStructural.derived[WithNestedList]
        given Schema[WithNestedList] = Schema.derived[WithNestedList]
        val structSchema             = ts.structuralSchema

        val original   = WithNestedList(List(List(1, 2), List(3, 4)))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.matrix) == Right(List(List(1, 2), List(3, 4)))
        )
      },
      test("Map[String, List[Int]] round-trip") {
        val ts                      = ToStructural.derived[WithMapOfList]
        given Schema[WithMapOfList] = Schema.derived[WithMapOfList]
        val structSchema            = ts.structuralSchema

        val original   = WithMapOfList(Map("a" -> List(1, 2, 3), "b" -> List(4, 5)))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.data) == Right(Map("a" -> List(1, 2, 3), "b" -> List(4, 5)))
        )
      },
      test("List[Option[String]] round-trip") {
        val ts                         = ToStructural.derived[WithListOfOption]
        given Schema[WithListOfOption] = Schema.derived[WithListOfOption]
        val structSchema               = ts.structuralSchema

        val original   = WithListOfOption(List(Some("x"), None, Some("y")))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.items) == Right(List(Some("x"), None, Some("y")))
        )
      }
    ),
    // ===========================================
    // NON-STRING MAP KEYS
    // ===========================================
    suite("Non-String Map Keys - ToStructural")(
      test("Map[Int, String]") {
        val ts                    = ToStructural.derived[WithIntKeys]
        given Schema[WithIntKeys] = Schema.derived[WithIntKeys]
        val s                     = ts.toStructural(WithIntKeys(Map(1 -> "one", 2 -> "two")))
        assertTrue(s.data == Map(1 -> "one", 2 -> "two"))
        assertTrue(ts.structuralSchema.reflect.typeName.name == """{data:Map[Int,String]}""")
      },
      test("Map[Long, String]") {
        val ts                     = ToStructural.derived[WithLongKeys]
        given Schema[WithLongKeys] = Schema.derived[WithLongKeys]
        val s                      = ts.toStructural(WithLongKeys(Map(100L -> "hundred", 200L -> "two hundred")))
        assertTrue(s.data == Map(100L -> "hundred", 200L -> "two hundred"))
        assertTrue(ts.structuralSchema.reflect.typeName.name == """{data:Map[Long,String]}""")
      }
    ),
    suite("Non-String Map Keys - Round-Trip")(
      test("Map[Int, String] round-trip") {
        val ts                    = ToStructural.derived[WithIntKeys]
        given Schema[WithIntKeys] = Schema.derived[WithIntKeys]
        val structSchema          = ts.structuralSchema

        val original   = WithIntKeys(Map(1 -> "a", 2 -> "b", 3 -> "c"))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.data) == Right(Map(1 -> "a", 2 -> "b", 3 -> "c"))
        )
        assertTrue(ts.structuralSchema.reflect.typeName.name == """{data:Map[Int,String]}""")
      },
      test("Map[Long, String] round-trip") {
        val ts                     = ToStructural.derived[WithLongKeys]
        given Schema[WithLongKeys] = Schema.derived[WithLongKeys]
        val structSchema           = ts.structuralSchema

        val original   = WithLongKeys(Map(1000000000L -> "billion", 2000000000L -> "two billion"))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.data) == Right(Map(1000000000L -> "billion", 2000000000L -> "two billion"))
        )
        assertTrue(ts.structuralSchema.reflect.typeName.name == """{data:Map[Long,String]}""")
      }
    ),
    suite("Tuple Map Values")(
      test("Map[String, (Int, String)] - ToStructural") {
        val ts                       = ToStructural.derived[WithTupleValue]
        given Schema[WithTupleValue] = Schema.derived[WithTupleValue]
        val s                        = ts.toStructural(WithTupleValue(Map("key" -> (42, "value"))))

        // The tuple becomes a StructuralRecord
        val mapValue = s.data.asInstanceOf[Map[String, StructuralRecord]]
        assertTrue(
          mapValue("key").selectDynamic("_1") == 42,
          mapValue("key").selectDynamic("_2") == "value"
        )
        assertTrue(ts.structuralSchema.reflect.typeName.name == """{data:Map[String,{_1:Int,_2:String}]}""")
      },
      // TODO STRUCT: disable until tuple round-trip is fixed
      test("Map[String, (Int, String)] - round-trip") {
        val ts                       = ToStructural.derived[WithTupleValue]
        given Schema[WithTupleValue] = Schema.derived[WithTupleValue]
        val structSchema             = ts.structuralSchema

        val original   = WithTupleValue(Map("a" -> (1, "one"), "b" -> (2, "two")))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight, {
            val result   = roundTrip.toOption.get
            val mapValue = result.data.asInstanceOf[Map[String, StructuralRecord]]
            mapValue("a").selectDynamic("_1") == 1 &&
            mapValue("b").selectDynamic("_2") == "two"
          }
        )
        assertTrue(ts.structuralSchema.reflect.typeName.name == """{data:Map[String,{_1:Int,_2:String}]}""")
      }
    ),
    // ===========================================
    // EMPTY COLLECTIONS
    // ===========================================
    suite("Empty Collections - ToStructural")(
      test("empty List") {
        val ts                      = ToStructural.derived[WithEmptyList]
        given Schema[WithEmptyList] = Schema.derived[WithEmptyList]
        val s                       = ts.toStructural(WithEmptyList(List.empty))
        assertTrue(s.items == List.empty)
        assertTrue(ts.structuralSchema.reflect.typeName.name == "{items:List[String]}")
      },
      test("empty Vector") {
        val ts                        = ToStructural.derived[WithEmptyVector]
        given Schema[WithEmptyVector] = Schema.derived[WithEmptyVector]
        val s                         = ts.toStructural(WithEmptyVector(Vector.empty))
        assertTrue(s.items == Vector.empty)
        assertTrue(ts.structuralSchema.reflect.typeName.name == "{items:Vector[Int]}")
      },
      test("empty Set") {
        val ts                     = ToStructural.derived[WithEmptySet]
        given Schema[WithEmptySet] = Schema.derived[WithEmptySet]
        val s                      = ts.toStructural(WithEmptySet(Set.empty))
        assertTrue(s.items == Set.empty)
        assertTrue(ts.structuralSchema.reflect.typeName.name == "{items:Set[String]}")
      },
      test("empty Map") {
        val ts                     = ToStructural.derived[WithEmptyMap]
        given Schema[WithEmptyMap] = Schema.derived[WithEmptyMap]
        val s                      = ts.toStructural(WithEmptyMap(Map.empty))
        assertTrue(s.data == Map.empty)
        assertTrue(ts.structuralSchema.reflect.typeName.name == "{data:Map[String,Int]}")
      },
      test("all empty collections") {
        val ts                 = ToStructural.derived[AllEmpty]
        given Schema[AllEmpty] = Schema.derived[AllEmpty]
        val s                  = ts.toStructural(AllEmpty(List.empty, Vector.empty, Set.empty, Map.empty))
        assertTrue(
          s.list == List.empty,
          s.vector == Vector.empty,
          s.set == Set.empty,
          s.map == Map.empty
        )
        assertTrue(
          ts.structuralSchema.reflect.typeName.name == "{list:List[Int],map:Map[String,Boolean],set:Set[Double],vector:Vector[String]}"
        )
      }
    ),
    suite("Empty Collections - Round-Trip")(
      test("empty List round-trip") {
        val ts                      = ToStructural.derived[WithEmptyList]
        given Schema[WithEmptyList] = Schema.derived[WithEmptyList]
        val structSchema            = ts.structuralSchema

        val original   = WithEmptyList(List.empty)
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.items) == Right(List.empty)
        )
        assertTrue(ts.structuralSchema.reflect.typeName.name == "{items:List[String]}")
      },
      test("empty Map round-trip") {
        val ts                     = ToStructural.derived[WithEmptyMap]
        given Schema[WithEmptyMap] = Schema.derived[WithEmptyMap]
        val structSchema           = ts.structuralSchema

        val original   = WithEmptyMap(Map.empty)
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.data) == Right(Map.empty)
        )
        assertTrue(ts.structuralSchema.reflect.typeName.name == "{data:Map[String,Int]}")
      },
      test("all empty collections round-trip") {
        val ts                 = ToStructural.derived[AllEmpty]
        given Schema[AllEmpty] = Schema.derived[AllEmpty]
        val structSchema       = ts.structuralSchema

        val original   = AllEmpty(List.empty, Vector.empty, Set.empty, Map.empty)
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(
          roundTrip.isRight,
          roundTrip.map(_.list) == Right(List.empty),
          roundTrip.map(_.vector) == Right(Vector.empty),
          roundTrip.map(_.set) == Right(Set.empty),
          roundTrip.map(_.map) == Right(Map.empty)
        )
        assertTrue(
          ts.structuralSchema.reflect.typeName.name == "{list:List[Int],map:Map[String,Boolean],set:Set[Double],vector:Vector[String]}"
        )
      }
    ),
    // ===========================================
    // LARGE TUPLES
    // ===========================================
    suite("Large Tuples - ToStructural")(
      test("Tuple4") {
        val ts                   = ToStructural.derived[WithTuple4]
        given Schema[WithTuple4] = Schema.derived[WithTuple4]
        val s                    = ts.toStructural(WithTuple4((1, "two", true, 4.0)))
        val tuple                = s.value.asInstanceOf[StructuralRecord]
        assertTrue(
          tuple.selectDynamic("_1") == 1,
          tuple.selectDynamic("_2") == "two",
          tuple.selectDynamic("_3") == true,
          tuple.selectDynamic("_4") == 4.0
        )
        assertTrue(ts.structuralSchema.reflect.typeName.name == "{value:{_1:Int,_2:String,_3:Boolean,_4:Double}}")
      },
      test("Tuple5") {
        val ts                   = ToStructural.derived[WithTuple5]
        given Schema[WithTuple5] = Schema.derived[WithTuple5]
        val s                    = ts.toStructural(WithTuple5((1, "two", true, 4.0, 5L)))
        val tuple                = s.value.asInstanceOf[StructuralRecord]
        assertTrue(
          tuple.selectDynamic("_1") == 1,
          tuple.selectDynamic("_2") == "two",
          tuple.selectDynamic("_3") == true,
          tuple.selectDynamic("_4") == 4.0,
          tuple.selectDynamic("_5") == 5L
        )
        assertTrue(
          ts.structuralSchema.reflect.typeName.name == "{value:{_1:Int,_2:String,_3:Boolean,_4:Double,_5:Long}}"
        )
      },
      test("Tuple10") {
        val ts                    = ToStructural.derived[WithTuple10]
        given Schema[WithTuple10] = Schema.derived[WithTuple10]
        val s                     = ts.toStructural(WithTuple10((1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))
        val tuple                 = s.value.asInstanceOf[StructuralRecord]
        assertTrue(
          tuple.selectDynamic("_1") == 1,
          tuple.selectDynamic("_5") == 5,
          tuple.selectDynamic("_10") == 10
        )
        assertTrue(
          ts.structuralSchema.reflect.typeName.name == "{value:{_1:Int,_10:Int,_2:Int,_3:Int,_4:Int,_5:Int,_6:Int,_7:Int,_8:Int,_9:Int}}"
        )
      },
      test("Tuple22 (max standard tuple)") {
        val ts                    = ToStructural.derived[WithTuple22]
        given Schema[WithTuple22] = Schema.derived[WithTuple22]
        val s                     =
          ts.toStructural(WithTuple22((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)))
        val tuple = s.value.asInstanceOf[StructuralRecord]
        assertTrue(
          tuple.selectDynamic("_1") == 1,
          tuple.selectDynamic("_11") == 11,
          tuple.selectDynamic("_22") == 22
        )
        assertTrue(
          ts.structuralSchema.reflect.typeName.name == "{value:{_1:Int,_10:Int,_11:Int,_12:Int,_13:Int,_14:Int,_15:Int,_16:Int,_17:Int,_18:Int,_19:Int,_2:Int,_20:Int,_21:Int,_22:Int,_3:Int,_4:Int,_5:Int,_6:Int,_7:Int,_8:Int,_9:Int}}"
        )
      }
    ),
    suite("Nested Tuples - ToStructural")(
      test("nested tuples ((Int, String), (Boolean, Double))") {
        val ts                         = ToStructural.derived[WithNestedTuples]
        given Schema[WithNestedTuples] = Schema.derived[WithNestedTuples]
        val s                          = ts.toStructural(WithNestedTuples(((1, "a"), (true, 2.5))))
        val outer                      = s.value.asInstanceOf[StructuralRecord]
        val inner1                     = outer.selectDynamic("_1").asInstanceOf[StructuralRecord]
        val inner2                     = outer.selectDynamic("_2").asInstanceOf[StructuralRecord]
        assertTrue(
          inner1.selectDynamic("_1") == 1,
          inner1.selectDynamic("_2") == "a",
          inner2.selectDynamic("_1") == true,
          inner2.selectDynamic("_2") == 2.5
        )
        assertTrue(
          ts.structuralSchema.reflect.typeName.name == "{value:{_1:{_1:Int,_2:String},_2:{_1:Boolean,_2:Double}}}"
        )
      },
      test("tuple of lists (List[Int], List[String])") {
        val ts                        = ToStructural.derived[WithTupleOfList]
        given Schema[WithTupleOfList] = Schema.derived[WithTupleOfList]
        val s                         = ts.toStructural(WithTupleOfList((List(1, 2), List("a", "b"))))
        val tuple                     = s.value.asInstanceOf[StructuralRecord]
        assertTrue(
          tuple.selectDynamic("_1") == List(1, 2),
          tuple.selectDynamic("_2") == List("a", "b")
        )
        assertTrue(ts.structuralSchema.reflect.typeName.name == "{value:{_1:List[Int],_2:List[String]}}")
      },
      test("tuple of options (Option[Int], Option[String])") {
        val ts                          = ToStructural.derived[WithTupleOfOption]
        given Schema[WithTupleOfOption] = Schema.derived[WithTupleOfOption]

        val s1     = ts.toStructural(WithTupleOfOption((Some(42), Some("hello"))))
        val tuple1 = s1.value.asInstanceOf[StructuralRecord]
        assertTrue(
          tuple1.selectDynamic("_1") == Some(42),
          tuple1.selectDynamic("_2") == Some("hello")
        )

        val s2     = ts.toStructural(WithTupleOfOption((None, None)))
        val tuple2 = s2.value.asInstanceOf[StructuralRecord]
        assertTrue(
          tuple2.selectDynamic("_1") == None,
          tuple2.selectDynamic("_2") == None
        )
        assertTrue(ts.structuralSchema.reflect.typeName.name == "{value:{_1:Option[Int],_2:Option[String]}}")
      },
      test("tuple of case classes") {
        val ts                             = ToStructural.derived[WithTupleOfCaseClass]
        given Schema[WithTupleOfCaseClass] = Schema.derived[WithTupleOfCaseClass]
        val s                              = ts.toStructural(WithTupleOfCaseClass((SimpleData(1, "a"), SimpleData(2, "b"))))
        val tuple                          = s.value.asInstanceOf[StructuralRecord]
        val data1                          = tuple.selectDynamic("_1").asInstanceOf[StructuralRecord]
        val data2                          = tuple.selectDynamic("_2").asInstanceOf[StructuralRecord]
        assertTrue(
          data1.selectDynamic("x") == 1,
          data1.selectDynamic("y") == "a",
          data2.selectDynamic("x") == 2,
          data2.selectDynamic("y") == "b"
        )
        assertTrue(ts.structuralSchema.reflect.typeName.name == "{value:{_1:{x:Int,y:String},_2:{x:Int,y:String}}}")
      }
    ),
    suite("Large Tuples - Round-Trip")(
      // TODO STRUCT: disable until tuple round-trip is fixed
      test("Tuple4 round-trip") {
        val ts                   = ToStructural.derived[WithTuple4]
        given Schema[WithTuple4] = Schema.derived[WithTuple4]
        val structSchema         = ts.structuralSchema

        val original   = WithTuple4((1, "two", true, 4.0))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(roundTrip.isRight) &&
        assertTrue {
          val result = roundTrip.toOption.get
          val tuple  = result.value.asInstanceOf[StructuralRecord]
          tuple.selectDynamic("_1") == 1 &&
          tuple.selectDynamic("_2") == "two" &&
          tuple.selectDynamic("_3") == true &&
          tuple.selectDynamic("_4") == 4.0
        }
      },
      // TODO STRUCT: disable until tuple round-trip is fixed
      test("Tuple10 round-trip") {
        val ts                    = ToStructural.derived[WithTuple10]
        given Schema[WithTuple10] = Schema.derived[WithTuple10]
        val structSchema          = ts.structuralSchema

        val original   = WithTuple10((1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(roundTrip.isRight) &&
        assertTrue {
          val result = roundTrip.toOption.get
          val tuple  = result.value.asInstanceOf[StructuralRecord]
          tuple.selectDynamic("_1") == 1 &&
          tuple.selectDynamic("_5") == 5 &&
          tuple.selectDynamic("_10") == 10
        }
      },
      // TODO STRUCT: disable until tuple round-trip is fixed
      test("Tuple22 round-trip") {
        val ts                    = ToStructural.derived[WithTuple22]
        given Schema[WithTuple22] = Schema.derived[WithTuple22]
        val structSchema          = ts.structuralSchema

        val original =
          WithTuple22((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(roundTrip.isRight) &&
        assertTrue {
          val result = roundTrip.toOption.get
          val tuple  = result.value.asInstanceOf[StructuralRecord]
          tuple.selectDynamic("_1") == 1 &&
          tuple.selectDynamic("_11") == 11 &&
          tuple.selectDynamic("_22") == 22
        }
      },
      // TODO STRUCT: disable until tuple round-trip is fixed
      test("nested tuples round-trip") {
        val ts                         = ToStructural.derived[WithNestedTuples]
        given Schema[WithNestedTuples] = Schema.derived[WithNestedTuples]
        val structSchema               = ts.structuralSchema

        val original   = WithNestedTuples(((10, "hello"), (false, 3.14)))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(roundTrip.isRight) &&
        assertTrue {
          val result = roundTrip.toOption.get
          val outer  = result.value.asInstanceOf[StructuralRecord]
          val inner1 = outer.selectDynamic("_1").asInstanceOf[StructuralRecord]
          val inner2 = outer.selectDynamic("_2").asInstanceOf[StructuralRecord]
          inner1.selectDynamic("_1") == 10 &&
          inner1.selectDynamic("_2") == "hello" &&
          inner2.selectDynamic("_1") == false &&
          inner2.selectDynamic("_2") == 3.14
        }
      },
      // TODO STRUCT: disable until tuple round-trip is fixed
      test("tuple of case classes round-trip") {
        val ts                             = ToStructural.derived[WithTupleOfCaseClass]
        given Schema[WithTupleOfCaseClass] = Schema.derived[WithTupleOfCaseClass]
        val structSchema                   = ts.structuralSchema

        val original   = WithTupleOfCaseClass((SimpleData(100, "first"), SimpleData(200, "second")))
        val structural = ts.toStructural(original)

        val dv        = structSchema.toDynamicValue(structural)
        val roundTrip = structSchema.fromDynamicValue(dv)

        assertTrue(roundTrip.isRight) &&
        assertTrue {
          val result = roundTrip.toOption.get
          val tuple  = result.value.asInstanceOf[StructuralRecord]
          val data1  = tuple.selectDynamic("_1").asInstanceOf[StructuralRecord]
          val data2  = tuple.selectDynamic("_2").asInstanceOf[StructuralRecord]
          data1.selectDynamic("x") == 100 &&
          data1.selectDynamic("y") == "first" &&
          data2.selectDynamic("x") == 200 &&
          data2.selectDynamic("y") == "second"
        }
      }
    ),
    // ===========================================
    // TYPENAME TESTS
    // ===========================================
    suite("TypeName - Edge Cases")(
      test("TypeName for nested List") {
        val ts                       = ToStructural.derived[WithNestedList]
        given Schema[WithNestedList] = Schema.derived[WithNestedList]

        assertTrue(ts.structuralSchema.reflect.typeName.name == "{matrix:List[List[Int]]}")
      },
      test("TypeName for Map with Int keys") {
        val ts                    = ToStructural.derived[WithIntKeys]
        given Schema[WithIntKeys] = Schema.derived[WithIntKeys]

        assertTrue(ts.structuralSchema.reflect.typeName.name == "{data:Map[Int,String]}")
      },
      test("TypeName for large tuple") {
        val ts                   = ToStructural.derived[WithTuple5]
        given Schema[WithTuple5] = Schema.derived[WithTuple5]

        assertTrue(
          ts.structuralSchema.reflect.typeName.name == "{value:{_1:Int,_2:String,_3:Boolean,_4:Double,_5:Long}}"
        )
      }
    ),
    // ===========================================
    // EQUALITY TESTS
    // ===========================================
    suite("Equality - Edge Cases")(
      test("nested collections equality") {
        val ts = ToStructural.derived[WithNestedList]
        val s1 = ts.toStructural(WithNestedList(List(List(1, 2), List(3))))
        val s2 = ts.toStructural(WithNestedList(List(List(1, 2), List(3))))
        val s3 = ts.toStructural(WithNestedList(List(List(1, 2), List(4))))
        assertTrue(
          s1 == s2,
          s1 != s3
        )
      },
      test("empty collections equality") {
        val ts = ToStructural.derived[AllEmpty]
        val s1 = ts.toStructural(AllEmpty(List.empty, Vector.empty, Set.empty, Map.empty))
        val s2 = ts.toStructural(AllEmpty(List.empty, Vector.empty, Set.empty, Map.empty))
        assertTrue(s1 == s2)
      },
      test("large tuple equality") {
        val ts = ToStructural.derived[WithTuple10]
        val s1 = ts.toStructural(WithTuple10((1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))
        val s2 = ts.toStructural(WithTuple10((1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))
        val s3 = ts.toStructural(WithTuple10((1, 2, 3, 4, 5, 6, 7, 8, 9, 99)))
        assertTrue(
          s1 == s2,
          s1 != s3
        )
      }
    )
  )
}
