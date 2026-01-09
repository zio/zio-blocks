package zio.blocks.schema.patch

import zio.blocks.schema._
import zio.blocks.schema.json.JsonTestUtils._
import zio.blocks.schema.patch.PatchSchemas._
import zio.test._

object OperationSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("OperationSpec")(
    suite("StringOp")(
      test("Insert serializes/deserializes") {
        roundTrip(
          StringOp.Insert(5, "hello"): StringOp,
          """{"Insert":{"index":5,"text":"hello"}}"""
        )
      },
      test("Insert at index 0") {
        roundTrip(
          StringOp.Insert(0, "prefix"): StringOp,
          """{"Insert":{"index":0,"text":"prefix"}}"""
        )
      },
      test("Insert empty string") {
        roundTrip(
          StringOp.Insert(10, ""): StringOp,
          """{"Insert":{"index":10,"text":""}}"""
        )
      },
      test("Insert with special characters") {
        roundTrip(
          StringOp.Insert(0, "hello\nworld\t\"quoted\""): StringOp,
          """{"Insert":{"index":0,"text":"hello\nworld\t\"quoted\""}}"""
        )
      },
      test("Delete serializes/deserializes") {
        roundTrip(
          StringOp.Delete(0, 3): StringOp,
          """{"Delete":{"index":0,"length":3}}"""
        )
      },
      test("Delete zero length") {
        roundTrip(
          StringOp.Delete(5, 0): StringOp,
          """{"Delete":{"index":5,"length":0}}"""
        )
      }
    ),
    suite("PrimitiveOp")(
      suite("Numeric deltas")(
        test("IntDelta positive") {
          roundTrip(
            PrimitiveOp.IntDelta(42): PrimitiveOp,
            """{"IntDelta":{"delta":42}}"""
          )
        },
        test("IntDelta negative") {
          roundTrip(
            PrimitiveOp.IntDelta(-100): PrimitiveOp,
            """{"IntDelta":{"delta":-100}}"""
          )
        },
        test("IntDelta zero") {
          roundTrip(
            PrimitiveOp.IntDelta(0): PrimitiveOp,
            """{"IntDelta":{"delta":0}}"""
          )
        },
        test("IntDelta min/max values") {
          roundTrip(
            PrimitiveOp.IntDelta(Int.MinValue): PrimitiveOp,
            s"""{"IntDelta":{"delta":${Int.MinValue}}}"""
          ) &&
          roundTrip(
            PrimitiveOp.IntDelta(Int.MaxValue): PrimitiveOp,
            s"""{"IntDelta":{"delta":${Int.MaxValue}}}"""
          )
        },
        test("LongDelta") {
          roundTrip(
            PrimitiveOp.LongDelta(9876543210L): PrimitiveOp,
            """{"LongDelta":{"delta":9876543210}}"""
          )
        },
        test("LongDelta min/max values") {
          roundTrip(
            PrimitiveOp.LongDelta(Long.MinValue): PrimitiveOp,
            s"""{"LongDelta":{"delta":${Long.MinValue}}}"""
          ) &&
          roundTrip(
            PrimitiveOp.LongDelta(Long.MaxValue): PrimitiveOp,
            s"""{"LongDelta":{"delta":${Long.MaxValue}}}"""
          )
        },
        test("DoubleDelta") {
          roundTrip(
            PrimitiveOp.DoubleDelta(3.14159): PrimitiveOp,
            """{"DoubleDelta":{"delta":3.14159}}"""
          )
        },
        test("DoubleDelta negative") {
          roundTrip(
            PrimitiveOp.DoubleDelta(-2.5): PrimitiveOp,
            """{"DoubleDelta":{"delta":-2.5}}"""
          )
        },
        test("FloatDelta") {
          roundTrip(
            PrimitiveOp.FloatDelta(1.5f): PrimitiveOp,
            """{"FloatDelta":{"delta":1.5}}"""
          )
        },
        test("ShortDelta") {
          roundTrip(
            PrimitiveOp.ShortDelta(100.toShort): PrimitiveOp,
            """{"ShortDelta":{"delta":100}}"""
          )
        },
        test("ByteDelta") {
          roundTrip(
            PrimitiveOp.ByteDelta(50.toByte): PrimitiveOp,
            """{"ByteDelta":{"delta":50}}"""
          )
        },
        test("BigIntDelta") {
          roundTrip(
            PrimitiveOp.BigIntDelta(BigInt("123456789012345678901234567890")): PrimitiveOp,
            """{"BigIntDelta":{"delta":123456789012345678901234567890}}"""
          )
        },
        test("BigIntDelta negative") {
          roundTrip(
            PrimitiveOp.BigIntDelta(BigInt("-999999999999999999999")): PrimitiveOp,
            """{"BigIntDelta":{"delta":-999999999999999999999}}"""
          )
        },
        test("BigDecimalDelta") {
          roundTrip(
            PrimitiveOp.BigDecimalDelta(BigDecimal("123.456789")): PrimitiveOp,
            """{"BigDecimalDelta":{"delta":123.456789}}"""
          )
        }
      ),
      suite("StringEdit")(
        test("StringEdit with single Insert") {
          roundTrip(
            PrimitiveOp.StringEdit(Vector(StringOp.Insert(0, "hello"))): PrimitiveOp,
            """{"StringEdit":{"ops":[{"Insert":{"index":0,"text":"hello"}}]}}"""
          )
        },
        test("StringEdit with single Delete") {
          roundTrip(
            PrimitiveOp.StringEdit(Vector(StringOp.Delete(5, 3))): PrimitiveOp,
            """{"StringEdit":{"ops":[{"Delete":{"index":5,"length":3}}]}}"""
          )
        },
        test("StringEdit with multiple operations") {
          roundTrip(
            PrimitiveOp.StringEdit(
              Vector(
                StringOp.Delete(0, 5),
                StringOp.Insert(0, "world")
              )
            ): PrimitiveOp,
            """{"StringEdit":{"ops":[{"Delete":{"index":0,"length":5}},{"Insert":{"index":0,"text":"world"}}]}}"""
          )
        },
        test("StringEdit empty") {
          roundTrip(
            PrimitiveOp.StringEdit(Vector.empty): PrimitiveOp,
            """{"StringEdit":{}}"""
          )
        }
      ),
      suite("Temporal deltas")(
        test("InstantDelta") {
          roundTrip(
            PrimitiveOp.InstantDelta(java.time.Duration.ofHours(24)): PrimitiveOp,
            """{"InstantDelta":{"delta":"PT24H"}}"""
          )
        },
        test("InstantDelta negative") {
          roundTrip(
            PrimitiveOp.InstantDelta(java.time.Duration.ofMinutes(-30)): PrimitiveOp,
            """{"InstantDelta":{"delta":"PT-30M"}}"""
          )
        },
        test("DurationDelta") {
          roundTrip(
            PrimitiveOp.DurationDelta(java.time.Duration.ofSeconds(3600)): PrimitiveOp,
            """{"DurationDelta":{"delta":"PT1H"}}"""
          )
        },
        test("LocalDateDelta") {
          roundTrip(
            PrimitiveOp.LocalDateDelta(java.time.Period.ofDays(7)): PrimitiveOp,
            """{"LocalDateDelta":{"delta":"P7D"}}"""
          )
        },
        test("LocalDateDelta with months and years") {
          roundTrip(
            PrimitiveOp.LocalDateDelta(java.time.Period.of(1, 2, 3)): PrimitiveOp,
            """{"LocalDateDelta":{"delta":"P1Y2M3D"}}"""
          )
        },
        test("LocalDateTimeDelta") {
          roundTrip(
            PrimitiveOp.LocalDateTimeDelta(
              java.time.Period.ofMonths(1),
              java.time.Duration.ofHours(12)
            ): PrimitiveOp,
            """{"LocalDateTimeDelta":{"periodDelta":"P1M","durationDelta":"PT12H"}}"""
          )
        },
        test("PeriodDelta") {
          roundTrip(
            PrimitiveOp.PeriodDelta(java.time.Period.ofWeeks(2)): PrimitiveOp,
            """{"PeriodDelta":{"delta":"P14D"}}"""
          )
        }
      )
    ),
    suite("SeqOp")(
      test("Insert at index") {
        roundTrip(
          SeqOp.Insert(
            0,
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          ): SeqOp,
          """{"Insert":{"index":0,"values":[1,2]}}"""
        )
      },
      test("Insert empty vector") {
        roundTrip(
          SeqOp.Insert(5, Vector.empty): SeqOp,
          """{"Insert":{"index":5}}"""
        )
      },
      test("Append elements") {
        roundTrip(
          SeqOp.Append(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.String("hello"))
            )
          ): SeqOp,
          """{"Append":{"values":["hello"]}}"""
        )
      },
      test("Append empty vector") {
        roundTrip(
          SeqOp.Append(Vector.empty): SeqOp,
          """{"Append":{}}"""
        )
      },
      test("Delete range") {
        roundTrip(
          SeqOp.Delete(2, 3): SeqOp,
          """{"Delete":{"index":2,"count":3}}"""
        )
      },
      test("Delete zero count") {
        roundTrip(
          SeqOp.Delete(0, 0): SeqOp,
          """{"Delete":{"index":0,"count":0}}"""
        )
      },
      test("Modify with Set operation") {
        roundTrip(
          SeqOp.Modify(
            0,
            Operation.Set(
              DynamicValue.Primitive(PrimitiveValue.Int(42))
            )
          ): SeqOp,
          """{"Modify":{"index":0,"op":{"Set":{"value":42}}}}"""
        )
      },
      test("Modify with PrimitiveDelta") {
        roundTrip(
          SeqOp.Modify(
            5,
            Operation.PrimitiveDelta(
              PrimitiveOp.IntDelta(10)
            )
          ): SeqOp,
          """{"Modify":{"index":5,"op":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":10}}}}}}"""
        )
      }
    ),
    suite("MapOp")(
      test("Add string key") {
        roundTrip(
          MapOp.Add(
            DynamicValue.Primitive(PrimitiveValue.String("key1")),
            DynamicValue.Primitive(PrimitiveValue.Int(100))
          ): MapOp,
          """{"Add":{"key":"key1","value":100}}"""
        )
      },
      test("Add int key") {
        roundTrip(
          MapOp.Add(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("value"))
          ): MapOp,
          """{"Add":{"key":1,"value":"value"}}"""
        )
      },
      test("Remove key") {
        roundTrip(
          MapOp.Remove(
            DynamicValue.Primitive(PrimitiveValue.String("keyToRemove"))
          ): MapOp,
          """{"Remove":{"key":"keyToRemove"}}"""
        )
      },
      test("Modify value at key") {
        roundTrip(
          MapOp.Modify(
            DynamicValue.Primitive(PrimitiveValue.String("myKey")),
            Operation.PrimitiveDelta(PrimitiveOp.IntDelta(5))
          ): MapOp,
          """{"Modify":{"key":"myKey","op":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":5}}}}}}"""
        )
      }
    ),
    suite("Operation")(
      test("Set primitive int value") {
        roundTrip(
          Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42))): Operation,
          """{"Set":{"value":42}}"""
        )
      },
      test("Set primitive string value") {
        roundTrip(
          Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("hello"))): Operation,
          """{"Set":{"value":"hello"}}"""
        )
      },
      test("Set record value") {
        roundTrip(
          Operation.Set(
            DynamicValue.Record(
              Vector(
                "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
                "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
              )
            )
          ): Operation,
          """{"Set":{"value":{"name":"Alice","age":30}}}"""
        )
      },
      test("Set sequence value") {
        roundTrip(
          Operation.Set(
            DynamicValue.Sequence(
              Vector(
                DynamicValue.Primitive(PrimitiveValue.Int(1)),
                DynamicValue.Primitive(PrimitiveValue.Int(2)),
                DynamicValue.Primitive(PrimitiveValue.Int(3))
              )
            )
          ): Operation,
          """{"Set":{"value":[1,2,3]}}"""
        )
      },
      test("PrimitiveDelta with IntDelta") {
        roundTrip(
          Operation.PrimitiveDelta(PrimitiveOp.IntDelta(10)): Operation,
          """{"PrimitiveDelta":{"op":{"IntDelta":{"delta":10}}}}"""
        )
      },
      test("PrimitiveDelta with StringEdit") {
        roundTrip(
          Operation.PrimitiveDelta(
            PrimitiveOp.StringEdit(
              Vector(
                StringOp.Insert(0, "prefix")
              )
            )
          ): Operation,
          """{"PrimitiveDelta":{"op":{"StringEdit":{"ops":[{"Insert":{"index":0,"text":"prefix"}}]}}}}"""
        )
      },
      test("SequenceEdit with multiple ops") {
        roundTrip(
          Operation.SequenceEdit(
            Vector(
              SeqOp.Delete(0, 2),
              SeqOp.Append(Vector(DynamicValue.Primitive(PrimitiveValue.Int(99))))
            )
          ): Operation,
          """{"SequenceEdit":{"ops":[{"Delete":{"index":0,"count":2}},{"Append":{"values":[99]}}]}}"""
        )
      },
      test("SequenceEdit empty") {
        roundTrip(
          Operation.SequenceEdit(Vector.empty): Operation,
          """{"SequenceEdit":{}}"""
        )
      },
      test("MapEdit with multiple ops") {
        roundTrip(
          Operation.MapEdit(
            Vector(
              MapOp.Add(
                DynamicValue.Primitive(PrimitiveValue.String("newKey")),
                DynamicValue.Primitive(PrimitiveValue.Int(1))
              ),
              MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String("oldKey")))
            )
          ): Operation,
          """{"MapEdit":{"ops":[{"Add":{"key":"newKey","value":1}},{"Remove":{"key":"oldKey"}}]}}"""
        )
      },
      test("MapEdit empty") {
        roundTrip(
          Operation.MapEdit(Vector.empty): Operation,
          """{"MapEdit":{}}"""
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
      test("Deeply nested SeqOp.Modify") {
        // SeqOp.Modify -> Operation.SequenceEdit -> SeqOp.Modify -> Operation.Set
        roundTrip(
          SeqOp.Modify(
            0,
            Operation.SequenceEdit(
              Vector(
                SeqOp.Modify(
                  1,
                  Operation.Set(
                    DynamicValue.Primitive(PrimitiveValue.String("deep"))
                  )
                )
              )
            )
          ): SeqOp,
          """{"Modify":{"index":0,"op":{"SequenceEdit":{"ops":[{"Modify":{"index":1,"op":{"Set":{"value":"deep"}}}}]}}}}"""
        )
      },
      test("Deeply nested MapOp.Modify") {
        // MapOp.Modify -> Operation.MapEdit -> MapOp.Modify -> Operation.PrimitiveDelta
        roundTrip(
          MapOp.Modify(
            DynamicValue.Primitive(PrimitiveValue.String("outer")),
            Operation.MapEdit(
              Vector(
                MapOp.Modify(
                  DynamicValue.Primitive(PrimitiveValue.String("inner")),
                  Operation.PrimitiveDelta(PrimitiveOp.IntDelta(100))
                )
              )
            )
          ): MapOp,
          """{"Modify":{"key":"outer","op":{"MapEdit":{"ops":[{"Modify":{"key":"inner","op":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":100}}}}}}]}}}}"""
        )
      },
      test("Mixed nesting: Seq containing Map operations") {
        roundTrip(
          Operation.SequenceEdit(
            Vector(
              SeqOp.Modify(
                0,
                Operation.MapEdit(
                  Vector(
                    MapOp.Add(
                      DynamicValue.Primitive(PrimitiveValue.String("key")),
                      DynamicValue.Primitive(PrimitiveValue.Int(42))
                    )
                  )
                )
              )
            )
          ): Operation,
          """{"SequenceEdit":{"ops":[{"Modify":{"index":0,"op":{"MapEdit":{"ops":[{"Add":{"key":"key","value":42}}]}}}}]}}"""
        )
      }
    ),
    suite("Edge cases")(
      test("Operation.Set with empty sequence") {
        roundTrip(
          Operation.Set(DynamicValue.Sequence(Vector.empty)): Operation,
          """{"Set":{"value":[]}}"""
        )
      },
      test("Unicode in string values") {
        roundTrip(
          StringOp.Insert(0, "Hello 世界 🌍"): StringOp,
          """{"Insert":{"index":0,"text":"Hello 世界 🌍"}}"""
        )
      },
      test("Very large index values") {
        roundTrip(
          SeqOp.Insert(Int.MaxValue, Vector.empty): SeqOp,
          s"""{"Insert":{"index":${Int.MaxValue}}}"""
        )
      },
      test("Boolean primitive in DynamicValue") {
        roundTrip(
          Operation.Set(DynamicValue.Primitive(PrimitiveValue.Boolean(true))): Operation,
          """{"Set":{"value":true}}"""
        )
      },
      test("Null/Unit primitive in DynamicValue") {
        roundTrip(
          Operation.Set(DynamicValue.Primitive(PrimitiveValue.Unit)): Operation,
          """{"Set":{"value":null}}"""
        )
      }
    )
  )
}
