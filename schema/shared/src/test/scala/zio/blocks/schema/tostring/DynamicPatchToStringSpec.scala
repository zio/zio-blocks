package zio.blocks.schema.tostring

import zio.test._
import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.patch._
import java.time._

object DynamicPatchToStringSpec extends ZIOSpecDefault {
  def spec = suite("DynamicPatchToStringSpec")(
    test("renders simple set operation") {
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("name"),
            DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .name = "John"
            |}""".stripMargin
      )
    },
    test("renders numeric delta") {
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("age"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .age += 5
            |}""".stripMargin
      )
    },
    test("renders numeric delta negative") {
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("score"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(-10))
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .score -= 10
            |}""".stripMargin
      )
    },
    test("renders simple map edits") {
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("config"),
            DynamicPatch.Operation.MapEdit(
              Chunk(
                DynamicPatch.MapOp.Add(
                  DynamicValue.Primitive(PrimitiveValue.String("newKey")),
                  DynamicValue.Primitive(PrimitiveValue.String("new value"))
                ),
                DynamicPatch.MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String("deletedKey")))
              )
            )
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .config:
            |    + {"newKey": "new value"}
            |    - {"deletedKey"}
            |}""".stripMargin
      )
    },
    test("renders simple sequence edits") {
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("items"),
            DynamicPatch.Operation.SequenceEdit(
              Chunk(
                DynamicPatch.SeqOp.Insert(0, Chunk(DynamicValue.Primitive(PrimitiveValue.String("inserted")))),
                DynamicPatch.SeqOp.Delete(2, 1)
              )
            )
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .items:
            |    + [0: "inserted"]
            |    - [2]
            |}""".stripMargin
      )
    },
    test("renders multi-operation patch with indentation") {
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("name"),
            DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("John")))
          ),
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("tags"),
            DynamicPatch.Operation.SequenceEdit(
              Chunk(
                DynamicPatch.SeqOp.Append(Chunk(DynamicValue.Primitive(PrimitiveValue.String("new"))))
              )
            )
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .name = "John"
            |  .tags:
            |    + "new"
            |}""".stripMargin
      )
    },
    test("renders string edits") {
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("bio"),
            DynamicPatch.Operation.PrimitiveDelta(
              DynamicPatch.PrimitiveOp.StringEdit(
                Chunk(
                  DynamicPatch.StringOp.Insert(0, "Hello "),
                  DynamicPatch.StringOp.Append("!"),
                  DynamicPatch.StringOp.Delete(5, 1)
                )
              )
            )
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .bio:
            |    + [0: "Hello "]
            |    + "!"
            |    - [5, 1]
            |}""".stripMargin
      )
    },
    test("renders various primitive deltas") {
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("long"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LongDelta(10L))
          ),
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("double"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.DoubleDelta(1.5))
          ),
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("duration"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.DurationDelta(Duration.ofSeconds(60)))
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .long += 10
            |  .double += 1.5
            |  .duration += PT1M
            |}""".stripMargin
      )
    },
    test("renders nested patch") {
      val nested = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("street"),
            DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("Broadway")))
          )
        )
      )
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("address"),
            DynamicPatch.Operation.Patch(nested)
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .address:
            |    .street = "Broadway"
            |}""".stripMargin
      )
    },
    test("renders nested map value patch") {
      val innerPatch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("counter"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
          )
        )
      )
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("data"),
            DynamicPatch.Operation.MapEdit(
              Chunk(
                DynamicPatch.MapOp.Modify(DynamicValue.Primitive(PrimitiveValue.String("item1")), innerPatch)
              )
            )
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .data:
            |    ~ {"item1"}:
            |      .counter += 1
            |}""".stripMargin
      )
    },
    test("renders all numeric primitive deltas") {
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("floatField"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.FloatDelta(2.5f))
          ),
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("shortField"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.ShortDelta(10))
          ),
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("byteField"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.ByteDelta(5))
          ),
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("bigIntField"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigIntDelta(BigInt("999999999999")))
          ),
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("balance"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigDecimalDelta(BigDecimal("100.50")))
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .floatField += 2.5
            |  .shortField += 10
            |  .byteField += 5
            |  .bigIntField += 999999999999
            |  .balance += 100.50
            |}""".stripMargin
      )
    },
    test("renders temporal primitive deltas") {
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("instant"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.InstantDelta(Duration.ofHours(1)))
          ),
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("period"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.PeriodDelta(java.time.Period.ofDays(7)))
          ),
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("localDate"),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LocalDateDelta(java.time.Period.ofMonths(1)))
          ),
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("localDateTime"),
            DynamicPatch.Operation.PrimitiveDelta(
              DynamicPatch.PrimitiveOp.LocalDateTimeDelta(java.time.Period.ofDays(1), Duration.ofHours(2))
            )
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .instant += PT1H
            |  .period += P7D
            |  .localDate += P1M
            |  .localDateTime += P1D, PT2H
            |}""".stripMargin
      )
    },
    test("renders map with non-string keys") {
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("flags"),
            DynamicPatch.Operation.MapEdit(
              Chunk(
                DynamicPatch.MapOp.Add(
                  DynamicValue.Primitive(PrimitiveValue.Boolean(true)),
                  DynamicValue.Primitive(PrimitiveValue.String("yes"))
                ),
                DynamicPatch.MapOp.Add(
                  DynamicValue.Primitive(PrimitiveValue.Boolean(false)),
                  DynamicValue.Primitive(PrimitiveValue.String("no"))
                )
              )
            )
          ),
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("codes"),
            DynamicPatch.Operation.MapEdit(
              Chunk(
                DynamicPatch.MapOp.Add(
                  DynamicValue.Primitive(PrimitiveValue.Int(1)),
                  DynamicValue.Primitive(PrimitiveValue.String("one"))
                ),
                DynamicPatch.MapOp.Add(
                  DynamicValue.Primitive(PrimitiveValue.Int(42)),
                  DynamicValue.Primitive(PrimitiveValue.String("answer"))
                ),
                DynamicPatch.MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.Int(99)))
              )
            )
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .flags:
            |    + {true: "yes"}
            |    + {false: "no"}
            |  .codes:
            |    + {1: "one"}
            |    + {42: "answer"}
            |    - {99}
            |}""".stripMargin
      )
    },
    test("renders sequence modify operation") {
      val innerPatch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("status"),
            DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("updated")))
          )
        )
      )
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("items"),
            DynamicPatch.Operation.SequenceEdit(
              Chunk(
                DynamicPatch.SeqOp.Modify(0, DynamicPatch.Operation.Patch(innerPatch))
              )
            )
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .items:
            |    ~ [0]:
            |      .:
            |        .status = "updated"
            |}""".stripMargin
      )
    },
    test("renders string modify operation") {
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("text"),
            DynamicPatch.Operation.PrimitiveDelta(
              DynamicPatch.PrimitiveOp.StringEdit(
                Chunk(
                  DynamicPatch.StringOp.Modify(0, 5, "Goodbye")
                )
              )
            )
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .text:
            |    ~ [0, 5: "Goodbye"]
            |}""".stripMargin
      )
    },
    test("renders empty patch") {
      val patch = DynamicPatch(Chunk.empty)
      assertTrue(patch.toString == "DynamicPatch {}")
    },
    test("renders multiple sequence deletes") {
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("items"),
            DynamicPatch.Operation.SequenceEdit(
              Chunk(
                DynamicPatch.SeqOp.Delete(0, 1),
                DynamicPatch.SeqOp.Delete(2, 2),
                DynamicPatch.SeqOp.Delete(7, 1)
              )
            )
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .items:
            |    - [0]
            |    - [2, 3]
            |    - [7]
            |}""".stripMargin
      )
    },
    test("renders multiple sequence inserts at different indices") {
      val patch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root.field("tags"),
            DynamicPatch.Operation.SequenceEdit(
              Chunk(
                DynamicPatch.SeqOp.Insert(0, Chunk(DynamicValue.Primitive(PrimitiveValue.String("first")))),
                DynamicPatch.SeqOp.Insert(3, Chunk(DynamicValue.Primitive(PrimitiveValue.String("middle")))),
                DynamicPatch.SeqOp.Append(
                  Chunk(
                    DynamicValue.Primitive(PrimitiveValue.String("last1")),
                    DynamicValue.Primitive(PrimitiveValue.String("last2"))
                  )
                )
              )
            )
          )
        )
      )
      assertTrue(
        patch.toString ==
          """DynamicPatch {
            |  .tags:
            |    + [0: "first"]
            |    + [3: "middle"]
            |    + "last1"
            |    + "last2"
            |}""".stripMargin
      )
    },
    suite("Long-form editing - comprehensive edits")(
      test("renders all sequence operations combined") {
        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("items"),
              DynamicPatch.Operation.SequenceEdit(
                Chunk(
                  DynamicPatch.SeqOp.Insert(0, Chunk(DynamicValue.Primitive(PrimitiveValue.String("new-item")))),
                  DynamicPatch.SeqOp.Delete(5, 2),
                  DynamicPatch.SeqOp.Modify(
                    3,
                    DynamicPatch.Operation.Patch(
                      DynamicPatch(
                        Chunk(
                          DynamicPatch.DynamicPatchOp(
                            DynamicOptic.root.field("status"),
                            DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("active")))
                          )
                        )
                      )
                    )
                  ),
                  DynamicPatch.SeqOp.Append(
                    Chunk(
                      DynamicValue.Primitive(PrimitiveValue.String("appended-1")),
                      DynamicValue.Primitive(PrimitiveValue.String("appended-2"))
                    )
                  )
                )
              )
            )
          )
        )
        assertTrue(
          patch.toString ==
            """DynamicPatch {
              |  .items:
              |    + [0: "new-item"]
              |    - [5, 6]
              |    ~ [3]:
              |      .:
              |        .status = "active"
              |    + "appended-1"
              |    + "appended-2"
              |}""".stripMargin
        )
      },
      test("renders all map operations combined") {
        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("config"),
              DynamicPatch.Operation.MapEdit(
                Chunk(
                  DynamicPatch.MapOp.Add(
                    DynamicValue.Primitive(PrimitiveValue.String("newKey")),
                    DynamicValue.Primitive(PrimitiveValue.String("newValue"))
                  ),
                  DynamicPatch.MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String("oldKey"))),
                  DynamicPatch.MapOp.Modify(
                    DynamicValue.Primitive(PrimitiveValue.String("existingKey")),
                    DynamicPatch(
                      Chunk(
                        DynamicPatch.DynamicPatchOp(
                          DynamicOptic.root.field("counter"),
                          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(10))
                        )
                      )
                    )
                  ),
                  DynamicPatch.MapOp.Add(
                    DynamicValue.Primitive(PrimitiveValue.Int(42)),
                    DynamicValue.Primitive(PrimitiveValue.String("answer"))
                  )
                )
              )
            )
          )
        )
        assertTrue(
          patch.toString ==
            """DynamicPatch {
              |  .config:
              |    + {"newKey": "newValue"}
              |    - {"oldKey"}
              |    ~ {"existingKey"}:
              |      .counter += 10
              |    + {42: "answer"}
              |}""".stripMargin
        )
      },
      test("renders all string operations combined") {
        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("text"),
              DynamicPatch.Operation.PrimitiveDelta(
                DynamicPatch.PrimitiveOp.StringEdit(
                  Chunk(
                    DynamicPatch.StringOp.Insert(0, "Prefix: "),
                    DynamicPatch.StringOp.Delete(10, 5),
                    DynamicPatch.StringOp.Modify(20, 3, "REPLACED"),
                    DynamicPatch.StringOp.Append(" - END")
                  )
                )
              )
            )
          )
        )
        assertTrue(
          patch.toString ==
            """DynamicPatch {
              |  .text:
              |    + [0: "Prefix: "]
              |    - [10, 5]
              |    ~ [20, 3: "REPLACED"]
              |    + " - END"
              |}""".stripMargin
        )
      }
    ),
    suite("Nested patch operations using DynamicPatchOp")(
      test("renders 3-level deep nested patch") {
        val level3Patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("deepValue"),
              DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(999)))
            )
          )
        )

        val level2Patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("level3"),
              DynamicPatch.Operation.Patch(level3Patch)
            )
          )
        )

        val level1Patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("level2"),
              DynamicPatch.Operation.Patch(level2Patch)
            )
          )
        )

        assertTrue(
          level1Patch.toString ==
            """DynamicPatch {
              |  .level2:
              |    .level3:
              |      .deepValue = 999
              |}""".stripMargin
        )
      },
      test("renders mixed nested patches with sequence and map modifications") {
        val itemPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("quantity"),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("price"),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigDecimalDelta(BigDecimal("10.00")))
            )
          )
        )

        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("items"),
              DynamicPatch.Operation.SequenceEdit(
                Chunk(
                  DynamicPatch.SeqOp.Modify(0, DynamicPatch.Operation.Patch(itemPatch))
                )
              )
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("metadata"),
              DynamicPatch.Operation.MapEdit(
                Chunk(
                  DynamicPatch.MapOp.Modify(
                    DynamicValue.Primitive(PrimitiveValue.String("stats")),
                    DynamicPatch(
                      Chunk(
                        DynamicPatch.DynamicPatchOp(
                          DynamicOptic.root.field("views"),
                          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )

        assertTrue(
          patch.toString ==
            """DynamicPatch {
              |  .items:
              |    ~ [0]:
              |      .:
              |        .quantity += 5
              |        .price += 10.00
              |  .metadata:
              |    ~ {"stats"}:
              |      .views += 1
              |}""".stripMargin
        )
      },
      test("renders deeply nested patch with variant navigation") {
        val innerPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("data").caseOf("Some").field("value"),
              DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("updated")))
            )
          )
        )

        val outerPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("result").caseOf("Success").field("payload"),
              DynamicPatch.Operation.Patch(innerPatch)
            )
          )
        )

        assertTrue(
          outerPatch.toString ==
            """DynamicPatch {
              |  .result<Success>.payload:
              |    .data<Some>.value = "updated"
              |}""".stripMargin
        )
      }
    )
  )
}
