package zio.blocks.schema

import zio.test._

object DynamicValueMergeSpec extends SchemaBaseSpec {
  import DynamicValue._
  import DynamicValueMergeStrategy._

  private val intPrimitive1  = int(1)
  private val intPrimitive2  = int(2)
  private val strPrimitive1  = string("a")
  private val strPrimitive2  = string("b")
  private val boolPrimitive1 = boolean(true)

  private val simpleRecord1 = Record(Vector("x" -> intPrimitive1, "y" -> strPrimitive1))
  private val simpleRecord2 = Record(Vector("x" -> intPrimitive2, "z" -> boolPrimitive1))

  private val nestedRecord1 = Record(
    Vector(
      "outer" -> intPrimitive1,
      "inner" -> Record(Vector("a" -> intPrimitive1, "b" -> strPrimitive1))
    )
  )
  private val nestedRecord2 = Record(
    Vector(
      "outer" -> intPrimitive2,
      "inner" -> Record(Vector("a" -> intPrimitive2, "c" -> boolPrimitive1))
    )
  )

  private val seq1 = Sequence(Vector(intPrimitive1, strPrimitive1, boolPrimitive1))
  private val seq2 = Sequence(Vector(intPrimitive2, strPrimitive2))

  private val nestedSeq1 = Sequence(Vector(Record(Vector("x" -> intPrimitive1)), intPrimitive1))
  private val nestedSeq2 = Sequence(Vector(Record(Vector("x" -> intPrimitive2, "y" -> strPrimitive1))))

  private val map1 = Map(Vector(strPrimitive1 -> intPrimitive1, string("key1") -> strPrimitive1))
  private val map2 = Map(Vector(strPrimitive1 -> intPrimitive2, string("key2") -> boolPrimitive1))

  private val nestedMap1 = Map(Vector(strPrimitive1 -> Record(Vector("x" -> intPrimitive1))))
  private val nestedMap2 = Map(Vector(strPrimitive1 -> Record(Vector("x" -> intPrimitive2, "y" -> strPrimitive1))))

  private val variant1 = Variant("CaseA", intPrimitive1)
  private val variant2 = Variant("CaseA", intPrimitive2)
  private val variant3 = Variant("CaseB", strPrimitive1)

  private val nestedVariant1 = Variant("CaseA", Record(Vector("x" -> intPrimitive1)))
  private val nestedVariant2 = Variant("CaseA", Record(Vector("x" -> intPrimitive2, "y" -> strPrimitive1)))

  def spec: Spec[TestEnvironment, Any] = suite("DynamicValueMergeSpec")(
    suite("DynamicValueMergeStrategy.Auto")(
      test("primitives: right wins") {
        val result = intPrimitive1.merge(intPrimitive2, Auto)
        assertTrue(result == intPrimitive2)
      },
      test("records: deep merge with overlapping fields") {
        val result = simpleRecord1.merge(simpleRecord2, Auto)
        assertTrue(
          result == Record(Vector("x" -> intPrimitive2, "y" -> strPrimitive1, "z" -> boolPrimitive1))
        )
      },
      test("records: non-overlapping fields preserved") {
        val left   = Record(Vector("a" -> intPrimitive1))
        val right  = Record(Vector("b" -> intPrimitive2))
        val result = left.merge(right, Auto)
        assertTrue(result == Record(Vector("a" -> intPrimitive1, "b" -> intPrimitive2)))
      },
      test("nested records: deep recursive merge") {
        val result        = nestedRecord1.merge(nestedRecord2, Auto)
        val expectedInner = Record(
          Vector("a" -> intPrimitive2, "b" -> strPrimitive1, "c" -> boolPrimitive1)
        )
        val expected = Record(Vector("outer" -> intPrimitive2, "inner" -> expectedInner))
        assertTrue(result == expected)
      },
      test("sequences: merge by index, right wins on overlap") {
        val result = seq1.merge(seq2, Auto)
        assertTrue(result == Sequence(Vector(intPrimitive2, strPrimitive2, boolPrimitive1)))
      },
      test("nested sequences: recursive merge of elements") {
        val result       = nestedSeq1.merge(nestedSeq2, Auto)
        val expectedElem = Record(Vector("x" -> intPrimitive2, "y" -> strPrimitive1))
        assertTrue(result == Sequence(Vector(expectedElem, intPrimitive1)))
      },
      test("maps: merge by key, right wins on overlap") {
        val result = map1.merge(map2, Auto)
        assertTrue(
          result == Map(
            Vector(
              strPrimitive1  -> intPrimitive2,
              string("key1") -> strPrimitive1,
              string("key2") -> boolPrimitive1
            )
          )
        )
      },
      test("nested maps: recursive merge of values") {
        val result   = nestedMap1.merge(nestedMap2, Auto)
        val expected = Map(Vector(strPrimitive1 -> Record(Vector("x" -> intPrimitive2, "y" -> strPrimitive1))))
        assertTrue(result == expected)
      },
      test("variants: same case merges inner values") {
        val result = variant1.merge(variant2, Auto)
        assertTrue(result == Variant("CaseA", intPrimitive2))
      },
      test("variants: different cases, right wins") {
        val result = variant1.merge(variant3, Auto)
        assertTrue(result == variant3)
      },
      test("nested variants: recursive merge of inner record") {
        val result   = nestedVariant1.merge(nestedVariant2, Auto)
        val expected = Variant("CaseA", Record(Vector("x" -> intPrimitive2, "y" -> strPrimitive1)))
        assertTrue(result == expected)
      },
      test("null values: right null wins") {
        val result = intPrimitive1.merge(Null, Auto)
        assertTrue(result == Null)
      },
      test("null values: left null replaced by right") {
        val result = Null.merge(intPrimitive1, Auto)
        assertTrue(result == intPrimitive1)
      }
    ),
    suite("DynamicValueMergeStrategy.Replace")(
      test("primitives: right wins") {
        val result = intPrimitive1.merge(intPrimitive2, Replace)
        assertTrue(result == intPrimitive2)
      },
      test("records: complete replacement, no merge") {
        val result = simpleRecord1.merge(simpleRecord2, Replace)
        assertTrue(result == simpleRecord2)
      },
      test("nested records: no recursion, complete replacement") {
        val result = nestedRecord1.merge(nestedRecord2, Replace)
        assertTrue(result == nestedRecord2)
      },
      test("sequences: complete replacement") {
        val result = seq1.merge(seq2, Replace)
        assertTrue(result == seq2)
      },
      test("maps: complete replacement") {
        val result = map1.merge(map2, Replace)
        assertTrue(result == map2)
      },
      test("variants: complete replacement") {
        val result = variant1.merge(variant3, Replace)
        assertTrue(result == variant3)
      },
      test("null values: right wins") {
        val result = simpleRecord1.merge(Null, Replace)
        assertTrue(result == Null)
      }
    ),
    suite("DynamicValueMergeStrategy.KeepLeft")(
      test("primitives: left always wins") {
        val result = intPrimitive1.merge(intPrimitive2, KeepLeft)
        assertTrue(result == intPrimitive1)
      },
      test("records: left completely preserved") {
        val result = simpleRecord1.merge(simpleRecord2, KeepLeft)
        assertTrue(result == simpleRecord1)
      },
      test("nested records: no recursion, left preserved") {
        val result = nestedRecord1.merge(nestedRecord2, KeepLeft)
        assertTrue(result == nestedRecord1)
      },
      test("sequences: left preserved") {
        val result = seq1.merge(seq2, KeepLeft)
        assertTrue(result == seq1)
      },
      test("maps: left preserved") {
        val result = map1.merge(map2, KeepLeft)
        assertTrue(result == map1)
      },
      test("variants: left preserved") {
        val result = variant1.merge(variant3, KeepLeft)
        assertTrue(result == variant1)
      },
      test("null values: left null preserved") {
        val result = Null.merge(intPrimitive1, KeepLeft)
        assertTrue(result == Null)
      }
    ),
    suite("DynamicValueMergeStrategy.Shallow")(
      test("primitives: right wins at root") {
        val result = intPrimitive1.merge(intPrimitive2, Shallow)
        assertTrue(result == intPrimitive2)
      },
      test("records: root-level merge, nested replaced") {
        val result = simpleRecord1.merge(simpleRecord2, Shallow)
        assertTrue(
          result == Record(Vector("x" -> intPrimitive2, "y" -> strPrimitive1, "z" -> boolPrimitive1))
        )
      },
      test("nested records: only root merged, inner replaced entirely") {
        val result   = nestedRecord1.merge(nestedRecord2, Shallow)
        val expected = Record(
          Vector(
            "outer" -> intPrimitive2,
            "inner" -> Record(Vector("a" -> intPrimitive2, "c" -> boolPrimitive1))
          )
        )
        assertTrue(result == expected)
      },
      test("sequences: root-level merge by index, nested replaced") {
        val result   = nestedSeq1.merge(nestedSeq2, Shallow)
        val expected = Sequence(
          Vector(Record(Vector("x" -> intPrimitive2, "y" -> strPrimitive1)), intPrimitive1)
        )
        assertTrue(result == expected)
      },
      test("maps: root-level merge, nested replaced") {
        val result   = nestedMap1.merge(nestedMap2, Shallow)
        val expected = Map(
          Vector(strPrimitive1 -> Record(Vector("x" -> intPrimitive2, "y" -> strPrimitive1)))
        )
        assertTrue(result == expected)
      },
      test("deeply nested: no recursion beyond root") {
        val deepLeft = Record(
          Vector(
            "level1" -> Record(
              Vector("level2" -> Record(Vector("a" -> intPrimitive1, "b" -> strPrimitive1)))
            )
          )
        )
        val deepRight = Record(
          Vector(
            "level1" -> Record(
              Vector("level2" -> Record(Vector("a" -> intPrimitive2, "c" -> boolPrimitive1)))
            )
          )
        )
        val result = deepLeft.merge(deepRight, Shallow)
        assertTrue(result == deepRight)
      }
    ),
    suite("DynamicValueMergeStrategy.Concat")(
      test("primitives: right wins") {
        val result = intPrimitive1.merge(intPrimitive2, Concat)
        assertTrue(result == intPrimitive2)
      },
      test("records: merged by key recursively") {
        val result = simpleRecord1.merge(simpleRecord2, Concat)
        assertTrue(
          result == Record(Vector("x" -> intPrimitive2, "y" -> strPrimitive1, "z" -> boolPrimitive1))
        )
      },
      test("sequences: concatenated instead of merged by index") {
        val result = seq1.merge(seq2, Concat)
        assertTrue(
          result == Sequence(
            Vector(intPrimitive1, strPrimitive1, boolPrimitive1, intPrimitive2, strPrimitive2)
          )
        )
      },
      test("sequences with different lengths: concatenated") {
        val left   = Sequence(Vector(intPrimitive1))
        val right  = Sequence(Vector(strPrimitive1, strPrimitive2, boolPrimitive1))
        val result = left.merge(right, Concat)
        assertTrue(
          result == Sequence(Vector(intPrimitive1, strPrimitive1, strPrimitive2, boolPrimitive1))
        )
      },
      test("empty sequences: concatenated") {
        val empty  = Sequence(Vector.empty)
        val result = empty.merge(seq1, Concat)
        assertTrue(result == seq1)
      },
      test("maps: merged by key recursively") {
        val result = map1.merge(map2, Concat)
        assertTrue(
          result == Map(
            Vector(
              strPrimitive1  -> intPrimitive2,
              string("key1") -> strPrimitive1,
              string("key2") -> boolPrimitive1
            )
          )
        )
      },
      test("nested records with sequences: sequences concatenated") {
        val left     = Record(Vector("items" -> Sequence(Vector(intPrimitive1, intPrimitive2))))
        val right    = Record(Vector("items" -> Sequence(Vector(strPrimitive1, strPrimitive2))))
        val result   = left.merge(right, Concat)
        val expected = Record(
          Vector(
            "items" -> Sequence(Vector(intPrimitive1, intPrimitive2, strPrimitive1, strPrimitive2))
          )
        )
        assertTrue(result == expected)
      },
      test("variants: right wins (no recursion for variants)") {
        val result = variant1.merge(variant3, Concat)
        assertTrue(result == variant3)
      },
      test("null values: right wins") {
        val result = seq1.merge(Null, Concat)
        assertTrue(result == Null)
      }
    ),
    suite("DynamicValueMergeStrategy.Custom")(
      test("custom function: always return left") {
        val strategy = Custom(f = (_, left, _) => left)
        val result   = intPrimitive1.merge(intPrimitive2, strategy)
        assertTrue(result == intPrimitive1)
      },
      test("custom function: swap values") {
        val strategy = Custom(f = (_, left, _) => left, r = (_, _) => false)
        val result   = simpleRecord1.merge(simpleRecord2, strategy)
        assertTrue(result == simpleRecord1)
      },
      test("custom function: path-based logic") {
        val strategy = Custom(
          f = (path, left, right) =>
            if (
              path.nodes.exists {
                case DynamicOptic.Node.Field("x") => true
                case _                            => false
              }
            ) left
            else right
        )
        val result = simpleRecord1.merge(simpleRecord2, strategy)
        assertTrue(
          result == Record(Vector("x" -> intPrimitive1, "y" -> strPrimitive1, "z" -> boolPrimitive1))
        )
      },
      test("custom function: no recursion") {
        val strategy = Custom(f = (_, _, right) => right, r = (_, _) => false)
        val result   = nestedRecord1.merge(nestedRecord2, strategy)
        assertTrue(result == nestedRecord2)
      },
      test("custom function: selective recursion for records only") {
        val strategy = Custom(
          f = (_, _, right) => right,
          r = (_, t) => t == DynamicValueType.Record
        )
        val left = Record(
          Vector(
            "items"  -> Sequence(Vector(intPrimitive1)),
            "nested" -> Record(Vector("a" -> intPrimitive1))
          )
        )
        val right = Record(
          Vector(
            "items"  -> Sequence(Vector(intPrimitive2, strPrimitive1)),
            "nested" -> Record(Vector("a" -> intPrimitive2, "b" -> strPrimitive1))
          )
        )
        val result   = left.merge(right, strategy)
        val expected = Record(
          Vector(
            "items"  -> Sequence(Vector(intPrimitive2, strPrimitive1)),
            "nested" -> Record(Vector("a" -> intPrimitive2, "b" -> strPrimitive1))
          )
        )
        assertTrue(result == expected)
      },
      test("custom function: depth-limited recursion") {
        val strategy = Custom(
          f = (_, _, right) => right,
          r = (path, _) => path.nodes.length < 1
        )
        val deep1 = Record(
          Vector("a" -> Record(Vector("b" -> Record(Vector("c" -> intPrimitive1)))))
        )
        val deep2 = Record(
          Vector("a" -> Record(Vector("b" -> Record(Vector("c" -> intPrimitive2, "d" -> strPrimitive1)))))
        )
        val result = deep1.merge(deep2, strategy)
        assertTrue(result == deep2)
      },
      test("custom function: merge primitives by addition") {
        val strategy = Custom(
          f = (_, left, right) =>
            (left, right) match {
              case (Primitive(PrimitiveValue.Int(l)), Primitive(PrimitiveValue.Int(r))) => int(l + r)
              case _                                                                    => right
            },
          r = (_, _) => false
        )
        val result = intPrimitive1.merge(intPrimitive2, strategy)
        assertTrue(result == int(3))
      }
    ),
    suite("DynamicValue.merge method")(
      test("default strategy is Auto") {
        val result = simpleRecord1.merge(simpleRecord2)
        val auto   = simpleRecord1.merge(simpleRecord2, Auto)
        assertTrue(result == auto)
      },
      test("merge with explicit Auto strategy") {
        val result        = nestedRecord1.merge(nestedRecord2, Auto)
        val expectedInner = Record(
          Vector("a" -> intPrimitive2, "b" -> strPrimitive1, "c" -> boolPrimitive1)
        )
        val expected = Record(Vector("outer" -> intPrimitive2, "inner" -> expectedInner))
        assertTrue(result == expected)
      },
      test("merge with Replace strategy") {
        val result = simpleRecord1.merge(simpleRecord2, Replace)
        assertTrue(result == simpleRecord2)
      },
      test("merge with KeepLeft strategy") {
        val result = simpleRecord1.merge(simpleRecord2, KeepLeft)
        assertTrue(result == simpleRecord1)
      },
      test("merge with Shallow strategy") {
        val result   = nestedRecord1.merge(nestedRecord2, Shallow)
        val expected = Record(
          Vector(
            "outer" -> intPrimitive2,
            "inner" -> Record(Vector("a" -> intPrimitive2, "c" -> boolPrimitive1))
          )
        )
        assertTrue(result == expected)
      },
      test("merge with Concat strategy") {
        val result = seq1.merge(seq2, Concat)
        assertTrue(
          result == Sequence(
            Vector(intPrimitive1, strPrimitive1, boolPrimitive1, intPrimitive2, strPrimitive2)
          )
        )
      },
      test("merge with Custom strategy") {
        val strategy = Custom(f = (_, left, _) => left)
        val result   = intPrimitive1.merge(intPrimitive2, strategy)
        assertTrue(result == intPrimitive1)
      },
      test("merge chaining") {
        val r1     = Record(Vector("a" -> intPrimitive1))
        val r2     = Record(Vector("b" -> intPrimitive2))
        val r3     = Record(Vector("c" -> strPrimitive1))
        val result = r1.merge(r2).merge(r3)
        assertTrue(
          result == Record(Vector("a" -> intPrimitive1, "b" -> intPrimitive2, "c" -> strPrimitive1))
        )
      }
    ),
    suite("Edge cases")(
      test("empty record merge") {
        val empty  = Record(Vector.empty)
        val result = empty.merge(simpleRecord1, Auto)
        assertTrue(result == simpleRecord1)
      },
      test("merge into empty record") {
        val empty  = Record(Vector.empty)
        val result = simpleRecord1.merge(empty, Auto)
        assertTrue(result == simpleRecord1)
      },
      test("empty sequence merge") {
        val empty  = Sequence(Vector.empty)
        val result = empty.merge(seq1, Auto)
        assertTrue(result == seq1)
      },
      test("merge into empty sequence") {
        val empty  = Sequence(Vector.empty)
        val result = seq1.merge(empty, Auto)
        assertTrue(result == seq1)
      },
      test("empty map merge") {
        val empty  = Map(Vector.empty)
        val result = empty.merge(map1, Auto)
        assertTrue(result == map1)
      },
      test("merge into empty map") {
        val empty  = Map(Vector.empty)
        val result = map1.merge(empty, Auto)
        assertTrue(result == map1)
      },
      test("null with null") {
        val result = Null.merge(Null, Auto)
        assertTrue(result == Null)
      },
      test("different types: right wins") {
        val result = intPrimitive1.merge(simpleRecord1, Auto)
        assertTrue(result == simpleRecord1)
      },
      test("record with sequence: right wins") {
        val result = simpleRecord1.merge(seq1, Auto)
        assertTrue(result == seq1)
      },
      test("deeply nested merge with Auto") {
        val deep1 = Record(
          Vector(
            "level1" -> Record(
              Vector(
                "level2" -> Record(
                  Vector("level3" -> Record(Vector("value" -> intPrimitive1)))
                )
              )
            )
          )
        )
        val deep2 = Record(
          Vector(
            "level1" -> Record(
              Vector(
                "level2" -> Record(
                  Vector("level3" -> Record(Vector("value" -> intPrimitive2, "extra" -> strPrimitive1)))
                )
              )
            )
          )
        )
        val result   = deep1.merge(deep2, Auto)
        val expected = Record(
          Vector(
            "level1" -> Record(
              Vector(
                "level2" -> Record(
                  Vector("level3" -> Record(Vector("value" -> intPrimitive2, "extra" -> strPrimitive1)))
                )
              )
            )
          )
        )
        assertTrue(result == expected)
      }
    )
  )
}
