package zio.blocks.schema.patch

import zio.blocks.schema._
import zio.blocks.schema.json.JsonTestUtils._
import zio.test._

object OperationSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("OperationSpec")(
    suite("Patch.StringOp")(
      test("Insert serializes/deserializes") {
        roundTrip(
          Patch.StringOp.Insert(5, "hello"): Patch.StringOp,
          """{"Insert":{"index":5,"text":"hello"}}"""
        )
      },
      test("Insert at index 0") {
        roundTrip(
          Patch.StringOp.Insert(0, "prefix"): Patch.StringOp,
          """{"Insert":{"index":0,"text":"prefix"}}"""
        )
      },
      test("Insert empty string") {
        roundTrip(
          Patch.StringOp.Insert(10, ""): Patch.StringOp,
          """{"Insert":{"index":10,"text":""}}"""
        )
      },
      test("Insert with special characters") {
        roundTrip(
          Patch.StringOp.Insert(0, "hello\nworld\t\"quoted\""): Patch.StringOp,
          """{"Insert":{"index":0,"text":"hello\nworld\t\"quoted\""}}"""
        )
      },
      test("Delete serializes/deserializes") {
        roundTrip(
          Patch.StringOp.Delete(0, 3): Patch.StringOp,
          """{"Delete":{"index":0,"length":3}}"""
        )
      },
      test("Delete zero length") {
        roundTrip(
          Patch.StringOp.Delete(5, 0): Patch.StringOp,
          """{"Delete":{"index":5,"length":0}}"""
        )
      },
      test("Append serializes/deserializes") {
        roundTrip(
          Patch.StringOp.Append("world"): Patch.StringOp,
          """{"Append":{"text":"world"}}"""
        )
      },
      test("Append empty string") {
        roundTrip(
          Patch.StringOp.Append(""): Patch.StringOp,
          """{"Append":{"text":""}}"""
        )
      },
      test("Modify serializes/deserializes") {
        roundTrip(
          Patch.StringOp.Modify(2, 3, "new"): Patch.StringOp,
          """{"Modify":{"index":2,"length":3,"text":"new"}}"""
        )
      },
      test("Modify with empty text") {
        roundTrip(
          Patch.StringOp.Modify(0, 5, ""): Patch.StringOp,
          """{"Modify":{"index":0,"length":5,"text":""}}"""
        )
      }
    ),
    suite("Patch.PrimitiveOp")(
      suite("Numeric deltas")(
        test("IntDelta positive") {
          roundTrip(
            Patch.PrimitiveOp.IntDelta(42): Patch.PrimitiveOp,
            """{"IntDelta":{"delta":42}}"""
          )
        },
        test("IntDelta negative") {
          roundTrip(
            Patch.PrimitiveOp.IntDelta(-100): Patch.PrimitiveOp,
            """{"IntDelta":{"delta":-100}}"""
          )
        },
        test("IntDelta zero") {
          roundTrip(
            Patch.PrimitiveOp.IntDelta(0): Patch.PrimitiveOp,
            """{"IntDelta":{"delta":0}}"""
          )
        },
        test("IntDelta min/max values") {
          roundTrip(
            Patch.PrimitiveOp.IntDelta(Int.MinValue): Patch.PrimitiveOp,
            s"""{"IntDelta":{"delta":${Int.MinValue}}}"""
          ) &&
          roundTrip(
            Patch.PrimitiveOp.IntDelta(Int.MaxValue): Patch.PrimitiveOp,
            s"""{"IntDelta":{"delta":${Int.MaxValue}}}"""
          )
        },
        test("LongDelta") {
          roundTrip(
            Patch.PrimitiveOp.LongDelta(9876543210L): Patch.PrimitiveOp,
            """{"LongDelta":{"delta":9876543210}}"""
          )
        },
        test("LongDelta min/max values") {
          roundTrip(
            Patch.PrimitiveOp.LongDelta(Long.MinValue): Patch.PrimitiveOp,
            s"""{"LongDelta":{"delta":${Long.MinValue}}}"""
          ) &&
          roundTrip(
            Patch.PrimitiveOp.LongDelta(Long.MaxValue): Patch.PrimitiveOp,
            s"""{"LongDelta":{"delta":${Long.MaxValue}}}"""
          )
        },
        test("DoubleDelta") {
          roundTrip(
            Patch.PrimitiveOp.DoubleDelta(3.14159): Patch.PrimitiveOp,
            """{"DoubleDelta":{"delta":3.14159}}"""
          )
        },
        test("DoubleDelta negative") {
          roundTrip(
            Patch.PrimitiveOp.DoubleDelta(-2.5): Patch.PrimitiveOp,
            """{"DoubleDelta":{"delta":-2.5}}"""
          )
        },
        test("FloatDelta") {
          roundTrip(
            Patch.PrimitiveOp.FloatDelta(1.5f): Patch.PrimitiveOp,
            """{"FloatDelta":{"delta":1.5}}"""
          )
        },
        test("ShortDelta") {
          roundTrip(
            Patch.PrimitiveOp.ShortDelta(100.toShort): Patch.PrimitiveOp,
            """{"ShortDelta":{"delta":100}}"""
          )
        },
        test("ByteDelta") {
          roundTrip(
            Patch.PrimitiveOp.ByteDelta(50.toByte): Patch.PrimitiveOp,
            """{"ByteDelta":{"delta":50}}"""
          )
        },
        test("BigIntDelta") {
          roundTrip(
            Patch.PrimitiveOp.BigIntDelta(BigInt("123456789012345678901234567890")): Patch.PrimitiveOp,
            """{"BigIntDelta":{"delta":123456789012345678901234567890}}"""
          )
        },
        test("BigIntDelta negative") {
          roundTrip(
            Patch.PrimitiveOp.BigIntDelta(BigInt("-999999999999999999999")): Patch.PrimitiveOp,
            """{"BigIntDelta":{"delta":-999999999999999999999}}"""
          )
        },
        test("BigDecimalDelta") {
          roundTrip(
            Patch.PrimitiveOp.BigDecimalDelta(BigDecimal("123.456789")): Patch.PrimitiveOp,
            """{"BigDecimalDelta":{"delta":123.456789}}"""
          )
        }
      ),
      suite("StringEdit")(
        test("StringEdit with single Insert") {
          roundTrip(
            Patch.PrimitiveOp.StringEdit(Vector(Patch.StringOp.Insert(0, "hello"))): Patch.PrimitiveOp,
            """{"StringEdit":{"ops":[{"Insert":{"index":0,"text":"hello"}}]}}"""
          )
        },
        test("StringEdit with single Delete") {
          roundTrip(
            Patch.PrimitiveOp.StringEdit(Vector(Patch.StringOp.Delete(5, 3))): Patch.PrimitiveOp,
            """{"StringEdit":{"ops":[{"Delete":{"index":5,"length":3}}]}}"""
          )
        },
        test("StringEdit with multiple operations") {
          roundTrip(
            Patch.PrimitiveOp.StringEdit(
              Vector(
                Patch.StringOp.Delete(0, 5),
                Patch.StringOp.Insert(0, "world")
              )
            ): Patch.PrimitiveOp,
            """{"StringEdit":{"ops":[{"Delete":{"index":0,"length":5}},{"Insert":{"index":0,"text":"world"}}]}}"""
          )
        },
        test("StringEdit empty") {
          roundTrip(
            Patch.PrimitiveOp.StringEdit(Vector.empty): Patch.PrimitiveOp,
            """{"StringEdit":{}}"""
          )
        },
        test("StringEdit with single Append") {
          roundTrip(
            Patch.PrimitiveOp.StringEdit(Vector(Patch.StringOp.Append(" suffix"))): Patch.PrimitiveOp,
            """{"StringEdit":{"ops":[{"Append":{"text":" suffix"}}]}}"""
          )
        },
        test("StringEdit with single Modify") {
          roundTrip(
            Patch.PrimitiveOp.StringEdit(Vector(Patch.StringOp.Modify(2, 3, "replacement"))): Patch.PrimitiveOp,
            """{"StringEdit":{"ops":[{"Modify":{"index":2,"length":3,"text":"replacement"}}]}}"""
          )
        },
        test("StringEdit with all operations") {
          roundTrip(
            Patch.PrimitiveOp.StringEdit(
              Vector(
                Patch.StringOp.Insert(0, "start "),
                Patch.StringOp.Modify(5, 2, "XX"),
                Patch.StringOp.Delete(10, 3),
                Patch.StringOp.Append(" end")
              )
            ): Patch.PrimitiveOp,
            """{"StringEdit":{"ops":[{"Insert":{"index":0,"text":"start "}},{"Modify":{"index":5,"length":2,"text":"XX"}},{"Delete":{"index":10,"length":3}},{"Append":{"text":" end"}}]}}"""
          )
        }
      ),
      suite("Temporal deltas")(
        test("InstantDelta") {
          roundTrip(
            Patch.PrimitiveOp.InstantDelta(java.time.Duration.ofHours(24)): Patch.PrimitiveOp,
            """{"InstantDelta":{"delta":"PT24H"}}"""
          )
        },
        test("InstantDelta negative") {
          roundTrip(
            Patch.PrimitiveOp.InstantDelta(java.time.Duration.ofMinutes(-30)): Patch.PrimitiveOp,
            """{"InstantDelta":{"delta":"PT-30M"}}"""
          )
        },
        test("DurationDelta") {
          roundTrip(
            Patch.PrimitiveOp.DurationDelta(java.time.Duration.ofSeconds(3600)): Patch.PrimitiveOp,
            """{"DurationDelta":{"delta":"PT1H"}}"""
          )
        },
        test("LocalDateDelta") {
          roundTrip(
            Patch.PrimitiveOp.LocalDateDelta(java.time.Period.ofDays(7)): Patch.PrimitiveOp,
            """{"LocalDateDelta":{"delta":"P7D"}}"""
          )
        },
        test("LocalDateDelta with months and years") {
          roundTrip(
            Patch.PrimitiveOp.LocalDateDelta(java.time.Period.of(1, 2, 3)): Patch.PrimitiveOp,
            """{"LocalDateDelta":{"delta":"P1Y2M3D"}}"""
          )
        },
        test("LocalDateTimeDelta") {
          roundTrip(
            Patch.PrimitiveOp.LocalDateTimeDelta(
              java.time.Period.ofMonths(1),
              java.time.Duration.ofHours(12)
            ): Patch.PrimitiveOp,
            """{"LocalDateTimeDelta":{"periodDelta":"P1M","durationDelta":"PT12H"}}"""
          )
        },
        test("PeriodDelta") {
          roundTrip(
            Patch.PrimitiveOp.PeriodDelta(java.time.Period.ofWeeks(2)): Patch.PrimitiveOp,
            """{"PeriodDelta":{"delta":"P14D"}}"""
          )
        }
      )
    ),
    suite("Patch.SeqOp")(
      test("Insert at index") {
        roundTrip(
          Patch.SeqOp.Insert(
            0,
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          ): Patch.SeqOp,
          """{"Insert":{"index":0,"values":[1,2]}}"""
        )
      },
      test("Insert empty vector") {
        roundTrip(
          Patch.SeqOp.Insert(5, Vector.empty): Patch.SeqOp,
          """{"Insert":{"index":5}}"""
        )
      },
      test("Append elements") {
        roundTrip(
          Patch.SeqOp.Append(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.String("hello"))
            )
          ): Patch.SeqOp,
          """{"Append":{"values":["hello"]}}"""
        )
      },
      test("Append empty vector") {
        roundTrip(
          Patch.SeqOp.Append(Vector.empty): Patch.SeqOp,
          """{"Append":{}}"""
        )
      },
      test("Delete range") {
        roundTrip(
          Patch.SeqOp.Delete(2, 3): Patch.SeqOp,
          """{"Delete":{"index":2,"count":3}}"""
        )
      },
      test("Delete zero count") {
        roundTrip(
          Patch.SeqOp.Delete(0, 0): Patch.SeqOp,
          """{"Delete":{"index":0,"count":0}}"""
        )
      },
      test("Modify with Set operation") {
        roundTrip(
          Patch.SeqOp.Modify(
            0,
            Patch.Operation.Set(
              DynamicValue.Primitive(PrimitiveValue.Int(42))
            )
          ): Patch.SeqOp,
          """{"Modify":{"index":0,"op":{"Set":{"value":42}}}}"""
        )
      },
      test("Modify with PrimitiveDelta") {
        roundTrip(
          Patch.SeqOp.Modify(
            5,
            Patch.Operation.PrimitiveDelta(
              Patch.PrimitiveOp.IntDelta(10)
            )
          ): Patch.SeqOp,
          """{"Modify":{"index":5,"op":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":10}}}}}}"""
        )
      }
    ),
    suite("Patch.MapOp")(
      test("Add string key") {
        roundTrip(
          Patch.MapOp.Add(
            DynamicValue.Primitive(PrimitiveValue.String("key1")),
            DynamicValue.Primitive(PrimitiveValue.Int(100))
          ): Patch.MapOp,
          """{"Add":{"key":"key1","value":100}}"""
        )
      },
      test("Add int key") {
        roundTrip(
          Patch.MapOp.Add(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("value"))
          ): Patch.MapOp,
          """{"Add":{"key":1,"value":"value"}}"""
        )
      },
      test("Remove key") {
        roundTrip(
          Patch.MapOp.Remove(
            DynamicValue.Primitive(PrimitiveValue.String("keyToRemove"))
          ): Patch.MapOp,
          """{"Remove":{"key":"keyToRemove"}}"""
        )
      },
      test("Modify value at key") {
        roundTrip(
          Patch.MapOp.Modify(
            DynamicValue.Primitive(PrimitiveValue.String("myKey")),
            DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.IntDelta(5)))
          ): Patch.MapOp,
          """{"Modify":{"key":"myKey","patch":{"ops":[{"path":{},"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":5}}}}}]}}}"""
        )
      }
    ),
    suite("Patch.Operation")(
      test("Set primitive int value") {
        roundTrip(
          Patch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42))): Patch.Operation,
          """{"Set":{"value":42}}"""
        )
      },
      test("Set primitive string value") {
        roundTrip(
          Patch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("hello"))): Patch.Operation,
          """{"Set":{"value":"hello"}}"""
        )
      },
      test("Set record value") {
        roundTrip(
          Patch.Operation.Set(
            DynamicValue.Record(
              Vector(
                "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
                "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
              )
            )
          ): Patch.Operation,
          """{"Set":{"value":{"name":"Alice","age":30}}}"""
        )
      },
      test("Set sequence value") {
        roundTrip(
          Patch.Operation.Set(
            DynamicValue.Sequence(
              Vector(
                DynamicValue.Primitive(PrimitiveValue.Int(1)),
                DynamicValue.Primitive(PrimitiveValue.Int(2)),
                DynamicValue.Primitive(PrimitiveValue.Int(3))
              )
            )
          ): Patch.Operation,
          """{"Set":{"value":[1,2,3]}}"""
        )
      },
      test("PrimitiveDelta with IntDelta") {
        roundTrip(
          Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.IntDelta(10)): Patch.Operation,
          """{"PrimitiveDelta":{"op":{"IntDelta":{"delta":10}}}}"""
        )
      },
      test("PrimitiveDelta with StringEdit") {
        roundTrip(
          Patch.Operation.PrimitiveDelta(
            Patch.PrimitiveOp.StringEdit(
              Vector(
                Patch.StringOp.Insert(0, "prefix")
              )
            )
          ): Patch.Operation,
          """{"PrimitiveDelta":{"op":{"StringEdit":{"ops":[{"Insert":{"index":0,"text":"prefix"}}]}}}}"""
        )
      },
      test("SequenceEdit with multiple ops") {
        roundTrip(
          Patch.Operation.SequenceEdit(
            Vector(
              Patch.SeqOp.Delete(0, 2),
              Patch.SeqOp.Append(Vector(DynamicValue.Primitive(PrimitiveValue.Int(99))))
            )
          ): Patch.Operation,
          """{"SequenceEdit":{"ops":[{"Delete":{"index":0,"count":2}},{"Append":{"values":[99]}}]}}"""
        )
      },
      test("SequenceEdit empty") {
        roundTrip(
          Patch.Operation.SequenceEdit(Vector.empty): Patch.Operation,
          """{"SequenceEdit":{}}"""
        )
      },
      test("MapEdit with multiple ops") {
        roundTrip(
          Patch.Operation.MapEdit(
            Vector(
              Patch.MapOp.Add(
                DynamicValue.Primitive(PrimitiveValue.String("newKey")),
                DynamicValue.Primitive(PrimitiveValue.Int(1))
              ),
              Patch.MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String("oldKey")))
            )
          ): Patch.Operation,
          """{"MapEdit":{"ops":[{"Add":{"key":"newKey","value":1}},{"Remove":{"key":"oldKey"}}]}}"""
        )
      },
      test("MapEdit empty") {
        roundTrip(
          Patch.Operation.MapEdit(Vector.empty): Patch.Operation,
          """{"MapEdit":{}}"""
        )
      },
      test("Patch with nested operations") {
        roundTrip(
          Patch.Operation.Patch(
            DynamicPatch(
              Vector(
                Patch.DynamicPatchOp(
                  DynamicOptic.root.field("street"),
                  Patch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("456 Elm")))
                ),
                Patch.DynamicPatchOp(
                  DynamicOptic.root.field("city"),
                  Patch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("LA")))
                ),
                Patch.DynamicPatchOp(
                  DynamicOptic.root.field("zip"),
                  Patch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("90002")))
                )
              )
            )
          ): Patch.Operation,
          """{"Patch":{"patch":{"ops":[{"path":{"nodes":[{"Field":{"name":"street"}}]},"operation":{"Set":{"value":"456 Elm"}}},{"path":{"nodes":[{"Field":{"name":"city"}}]},"operation":{"Set":{"value":"LA"}}},{"path":{"nodes":[{"Field":{"name":"zip"}}]},"operation":{"Set":{"value":"90002"}}}]}}}"""
        )
      },
      test("Patch with empty nested patch") {
        roundTrip(
          Patch.Operation.Patch(DynamicPatch(Vector.empty)): Patch.Operation,
          """{"Patch":{"patch":{}}}"""
        )
      }
    ),
    suite("PatchMode")(
      test("Strict mode") {
        roundTrip(
          PatchMode.Strict: PatchMode,
          """"Strict""""
        )
      },
      test("Lenient mode") {
        roundTrip(
          PatchMode.Lenient: PatchMode,
          """"Lenient""""
        )
      },
      test("Clobber mode") {
        roundTrip(
          PatchMode.Clobber: PatchMode,
          """"Clobber""""
        )
      }
    ),
    suite("Nested/recursive structures")(
      test("Deeply nested Patch.SeqOp.Modify") {
        // Patch.SeqOp.Modify -> Patch.Operation.SequenceEdit -> Patch.SeqOp.Modify -> Patch.Operation.Set
        roundTrip(
          Patch.SeqOp.Modify(
            0,
            Patch.Operation.SequenceEdit(
              Vector(
                Patch.SeqOp.Modify(
                  1,
                  Patch.Operation.Set(
                    DynamicValue.Primitive(PrimitiveValue.String("deep"))
                  )
                )
              )
            )
          ): Patch.SeqOp,
          """{"Modify":{"index":0,"op":{"SequenceEdit":{"ops":[{"Modify":{"index":1,"op":{"Set":{"value":"deep"}}}}]}}}}"""
        )
      },
      test("Deeply nested Patch.MapOp.Modify") {
        // Patch.MapOp.Modify -> DynamicPatch -> Patch.Operation.MapEdit -> Patch.MapOp.Modify -> DynamicPatch -> Patch.Operation.PrimitiveDelta
        roundTrip(
          Patch.MapOp.Modify(
            DynamicValue.Primitive(PrimitiveValue.String("outer")),
            DynamicPatch.root(
              Patch.Operation.MapEdit(
                Vector(
                  Patch.MapOp.Modify(
                    DynamicValue.Primitive(PrimitiveValue.String("inner")),
                    DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.IntDelta(100)))
                  )
                )
              )
            )
          ): Patch.MapOp,
          """{"Modify":{"key":"outer","patch":{"ops":[{"path":{},"operation":{"MapEdit":{"ops":[{"Modify":{"key":"inner","patch":{"ops":[{"path":{},"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":100}}}}}]}}}]}}}]}}}"""
        )
      },
      test("Mixed nesting: Seq containing Map operations") {
        roundTrip(
          Patch.Operation.SequenceEdit(
            Vector(
              Patch.SeqOp.Modify(
                0,
                Patch.Operation.MapEdit(
                  Vector(
                    Patch.MapOp.Add(
                      DynamicValue.Primitive(PrimitiveValue.String("key")),
                      DynamicValue.Primitive(PrimitiveValue.Int(42))
                    )
                  )
                )
              )
            )
          ): Patch.Operation,
          """{"SequenceEdit":{"ops":[{"Modify":{"index":0,"op":{"MapEdit":{"ops":[{"Add":{"key":"key","value":42}}]}}}}]}}"""
        )
      }
    ),
    suite("Edge cases")(
      test("Patch.Operation.Set with empty sequence") {
        roundTrip(
          Patch.Operation.Set(DynamicValue.Sequence(Vector.empty)): Patch.Operation,
          """{"Set":{"value":[]}}"""
        )
      },
      test("Unicode in string values") {
        roundTrip(
          Patch.StringOp.Insert(0, "Hello 世界 🌍"): Patch.StringOp,
          """{"Insert":{"index":0,"text":"Hello 世界 🌍"}}"""
        )
      },
      test("Very large index values") {
        roundTrip(
          Patch.SeqOp.Insert(Int.MaxValue, Vector.empty): Patch.SeqOp,
          s"""{"Insert":{"index":${Int.MaxValue}}}"""
        )
      },
      test("Boolean primitive in DynamicValue") {
        roundTrip(
          Patch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Boolean(true))): Patch.Operation,
          """{"Set":{"value":true}}"""
        )
      },
      test("Null/Unit primitive in DynamicValue") {
        roundTrip(
          Patch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Unit)): Patch.Operation,
          """{"Set":{"value":null}}"""
        )
      }
    )
  )
}
