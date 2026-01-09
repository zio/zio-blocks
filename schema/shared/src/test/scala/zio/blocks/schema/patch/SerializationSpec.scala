package zio.blocks.schema.patch

import zio.blocks.schema._
import zio.blocks.schema.json.JsonTestUtils._
import zio.blocks.schema.patch.PatchSchemas._
import zio.test._

object SerializationSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("SerializationSpec")(
    suite("PatchPath serialization")(
      test("PatchPath.Field serializes") {
        roundTrip(
          PatchPath.Field("name"): PatchPath,
          """{"Field":{"name":"name"}}"""
        )
      },
      test("PatchPath.AtIndex serializes") {
        roundTrip(
          PatchPath.AtIndex(5): PatchPath,
          """{"AtIndex":{"index":5}}"""
        )
      },
      test("PatchPath.AtMapKey serializes") {
        val key = DynamicValue.Primitive(PrimitiveValue.String("key1"))
        roundTrip(
          PatchPath.AtMapKey(key): PatchPath,
          """{"AtMapKey":{"key":"key1"}}"""
        )
      },
      test("PatchPath.AtMapKey with Int key") {
        val key = DynamicValue.Primitive(PrimitiveValue.Int(42))
        roundTrip(
          PatchPath.AtMapKey(key): PatchPath,
          """{"AtMapKey":{"key":42}}"""
        )
      },
      test("PatchPath.Case serializes") {
        roundTrip(
          PatchPath.Case("Active"): PatchPath,
          """{"Case":{"name":"Active"}}"""
        )
      },
      test("PatchPath.Elements serializes") {
        roundTrip(
          PatchPath.Elements: PatchPath,
          """{"Elements":{}}"""
        )
      },
      test("PatchPath.Wrapped serializes") {
        roundTrip(
          PatchPath.Wrapped: PatchPath,
          """{"Wrapped":{}}"""
        )
      }
    ),
    suite("DynamicPatchOp serialization")(
      test("DynamicPatchOp with empty path and Set operation") {
        val op = DynamicPatchOp(
          Vector.empty,
          Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        roundTrip(
          op,
          """{"operation":{"Set":{"value":42}}}"""
        )
      },
      test("DynamicPatchOp with single field path") {
        val op = DynamicPatchOp(
          Vector(PatchPath.Field("age")),
          Operation.PrimitiveDelta(PrimitiveOp.IntDelta(5))
        )
        roundTrip(
          op,
          """{"path":[{"Field":{"name":"age"}}],"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":5}}}}}"""
        )
      },
      test("DynamicPatchOp with nested path") {
        val op = DynamicPatchOp(
          Vector(PatchPath.Field("address"), PatchPath.Field("city")),
          Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("NYC")))
        )
        roundTrip(
          op,
          """{"path":[{"Field":{"name":"address"}},{"Field":{"name":"city"}}],"operation":{"Set":{"value":"NYC"}}}"""
        )
      },
      test("DynamicPatchOp with index path") {
        val op = DynamicPatchOp(
          Vector(PatchPath.Field("items"), PatchPath.AtIndex(2)),
          Operation.PrimitiveDelta(PrimitiveOp.IntDelta(10))
        )
        roundTrip(
          op,
          """{"path":[{"Field":{"name":"items"}},{"AtIndex":{"index":2}}],"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":10}}}}}"""
        )
      },
      test("DynamicPatchOp with SequenceEdit") {
        val op = DynamicPatchOp(
          Vector(PatchPath.Field("tags")),
          Operation.SequenceEdit(Vector(SeqOp.Append(Vector(DynamicValue.Primitive(PrimitiveValue.String("new"))))))
        )
        roundTrip(
          op,
          """{"path":[{"Field":{"name":"tags"}}],"operation":{"SequenceEdit":{"ops":[{"Append":{"values":["new"]}}]}}}"""
        )
      },
      test("DynamicPatchOp with MapEdit") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("key1"))
        val value = DynamicValue.Primitive(PrimitiveValue.Int(100))
        val op    = DynamicPatchOp(
          Vector(PatchPath.Field("metadata")),
          Operation.MapEdit(Vector(MapOp.Add(key, value)))
        )
        roundTrip(
          op,
          """{"path":[{"Field":{"name":"metadata"}}],"operation":{"MapEdit":{"ops":[{"Add":{"key":"key1","value":100}}]}}}"""
        )
      }
    ),
    suite("DynamicPatch serialization")(
      test("DynamicPatch.empty serializes") {
        roundTrip(
          DynamicPatch.empty,
          """{}"""
        )
      },
      test("DynamicPatch with single operation") {
        val patch = DynamicPatch(
          Vector(
            DynamicPatchOp(
              Vector(PatchPath.Field("age")),
              Operation.PrimitiveDelta(PrimitiveOp.IntDelta(1))
            )
          )
        )
        roundTrip(
          patch,
          """{"ops":[{"path":[{"Field":{"name":"age"}}],"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":1}}}}}]}"""
        )
      },
      test("DynamicPatch with multiple operations") {
        val patch = DynamicPatch(
          Vector(
            DynamicPatchOp(
              Vector(PatchPath.Field("name")),
              Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("Alice")))
            ),
            DynamicPatchOp(
              Vector(PatchPath.Field("age")),
              Operation.PrimitiveDelta(PrimitiveOp.IntDelta(5))
            )
          )
        )
        roundTrip(
          patch,
          """{"ops":[{"path":[{"Field":{"name":"name"}}],"operation":{"Set":{"value":"Alice"}}},{"path":[{"Field":{"name":"age"}}],"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":5}}}}}]}"""
        )
      },
      test("DynamicPatch with nested record operations") {
        val patch = DynamicPatch(
          Vector(
            DynamicPatchOp(
              Vector(PatchPath.Field("address"), PatchPath.Field("street")),
              Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("123 Main St")))
            ),
            DynamicPatchOp(
              Vector(PatchPath.Field("address"), PatchPath.Field("zipCode")),
              Operation.PrimitiveDelta(PrimitiveOp.IntDelta(10000))
            )
          )
        )
        roundTrip(
          patch,
          """{"ops":[{"path":[{"Field":{"name":"address"}},{"Field":{"name":"street"}}],"operation":{"Set":{"value":"123 Main St"}}},{"path":[{"Field":{"name":"address"}},{"Field":{"name":"zipCode"}}],"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":10000}}}}}]}"""
        )
      },
      test("DynamicPatch with sequence operations") {
        val patch = DynamicPatch(
          Vector(
            DynamicPatchOp(
              Vector(PatchPath.Field("items")),
              Operation.SequenceEdit(
                Vector(
                  SeqOp.Append(Vector(DynamicValue.Primitive(PrimitiveValue.Int(1)))),
                  SeqOp.Insert(0, Vector(DynamicValue.Primitive(PrimitiveValue.Int(0))))
                )
              )
            )
          )
        )
        roundTrip(
          patch,
          """{"ops":[{"path":[{"Field":{"name":"items"}}],"operation":{"SequenceEdit":{"ops":[{"Append":{"values":[1]}},{"Insert":{"index":0,"values":[0]}}]}}}]}"""
        )
      },
      test("DynamicPatch with map operations") {
        val patch = DynamicPatch(
          Vector(
            DynamicPatchOp(
              Vector(PatchPath.Field("metadata")),
              Operation.MapEdit(
                Vector(
                  MapOp.Add(
                    DynamicValue.Primitive(PrimitiveValue.String("key1")),
                    DynamicValue.Primitive(PrimitiveValue.Int(100))
                  ),
                  MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String("key2")))
                )
              )
            )
          )
        )
        roundTrip(
          patch,
          """{"ops":[{"path":[{"Field":{"name":"metadata"}}],"operation":{"MapEdit":{"ops":[{"Add":{"key":"key1","value":100}},{"Remove":{"key":"key2"}}]}}}]}"""
        )
      }
    ),
    suite("Complex nested patch serialization")(
      test("Deeply nested patch with multiple operation types") {
        val patch = DynamicPatch(
          Vector(
            // Set a nested field
            DynamicPatchOp(
              Vector(PatchPath.Field("company"), PatchPath.Field("name")),
              Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("Acme Corp")))
            ),
            // Modify an array element
            DynamicPatchOp(
              Vector(
                PatchPath.Field("company"),
                PatchPath.Field("employees"),
                PatchPath.AtIndex(0),
                PatchPath.Field("age")
              ),
              Operation.PrimitiveDelta(PrimitiveOp.IntDelta(1))
            ),
            // Append to a sequence
            DynamicPatchOp(
              Vector(PatchPath.Field("tags")),
              Operation.SequenceEdit(
                Vector(
                  SeqOp.Append(
                    Vector(
                      DynamicValue.Primitive(PrimitiveValue.String("important"))
                    )
                  )
                )
              )
            )
          )
        )

        val json =
          """{"ops":[{"path":[{"Field":{"name":"company"}},{"Field":{"name":"name"}}],"operation":{"Set":{"value":"Acme Corp"}}},{"path":[{"Field":{"name":"company"}},{"Field":{"name":"employees"}},{"AtIndex":{"index":0}},{"Field":{"name":"age"}}],"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":1}}}}},{"path":[{"Field":{"name":"tags"}}],"operation":{"SequenceEdit":{"ops":[{"Append":{"values":["important"]}}]}}}]}"""

        // Just verify decode works
        decode(json, patch)
      },
      test("Patch with all PrimitiveOp types") {
        val patch = DynamicPatch(
          Vector(
            DynamicPatchOp(Vector.empty, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(1))),
            DynamicPatchOp(Vector.empty, Operation.PrimitiveDelta(PrimitiveOp.LongDelta(2L))),
            DynamicPatchOp(Vector.empty, Operation.PrimitiveDelta(PrimitiveOp.DoubleDelta(3.14))),
            DynamicPatchOp(Vector.empty, Operation.PrimitiveDelta(PrimitiveOp.FloatDelta(2.71f))),
            DynamicPatchOp(Vector.empty, Operation.PrimitiveDelta(PrimitiveOp.BigIntDelta(BigInt(1000)))),
            DynamicPatchOp(Vector.empty, Operation.PrimitiveDelta(PrimitiveOp.BigDecimalDelta(BigDecimal("123.456"))))
          )
        )

        val json =
          """{"ops":[{"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":1}}}}},{"operation":{"PrimitiveDelta":{"op":{"LongDelta":{"delta":2}}}}},{"operation":{"PrimitiveDelta":{"op":{"DoubleDelta":{"delta":3.14}}}}},{"operation":{"PrimitiveDelta":{"op":{"FloatDelta":{"delta":2.71}}}}},{"operation":{"PrimitiveDelta":{"op":{"BigIntDelta":{"delta":1000}}}}},{"operation":{"PrimitiveDelta":{"op":{"BigDecimalDelta":{"delta":123.456}}}}}]}"""
        decode(json, patch)
      },
      test("Large patch with many operations") {
        val ops = (0 until 20).map { i =>
          DynamicPatchOp(
            Vector(PatchPath.Field(s"field$i")),
            Operation.PrimitiveDelta(PrimitiveOp.IntDelta(i))
          )
        }.toVector

        val patch = DynamicPatch(ops)

        // Just verify it can be encoded and decoded without checking exact JSON
        import zio.blocks.schema.json._
        val schema  = Schema[DynamicPatch]
        val codec   = schema.derive(JsonBinaryCodecDeriver)
        val encoded = codec.encode(patch, WriterConfig)
        val decoded = codec.decode(encoded, ReaderConfig)
        assertTrue(decoded == Right(patch))
      }
    ),
    suite("Edge cases")(
      test("PatchPath with unicode field names") {
        val op = DynamicPatchOp(
          Vector(PatchPath.Field("名前"), PatchPath.Field("年齢")),
          Operation.PrimitiveDelta(PrimitiveOp.IntDelta(1))
        )
        val json =
          """{"path":[{"Field":{"name":"名前"}},{"Field":{"name":"年齢"}}],"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":1}}}}}"""
        roundTrip(op, json)
      },
      test("DynamicPatch with empty string field names") {
        val op = DynamicPatchOp(
          Vector(PatchPath.Field("")),
          Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val json = """{"path":[{"Field":{"name":""}}],"operation":{"Set":{"value":42}}}"""
        roundTrip(op, json)
      },
      test("AtMapKey with complex DynamicValue") {
        val complexKey = DynamicValue.Record(
          Vector(
            "field1" -> DynamicValue.Primitive(PrimitiveValue.String("value1")),
            "field2" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
          )
        )
        val path = PatchPath.AtMapKey(complexKey)
        val json = """{"AtMapKey":{"key":{"field1":"value1","field2":42}}}"""
        roundTrip(path: PatchPath, json)
      },
      test("Very deep nesting") {
        val deepPath = Vector(
          PatchPath.Field("level1"),
          PatchPath.Field("level2"),
          PatchPath.Field("level3"),
          PatchPath.Field("level4"),
          PatchPath.Field("level5"),
          PatchPath.Field("level6"),
          PatchPath.Field("level7"),
          PatchPath.Field("level8"),
          PatchPath.Field("level9"),
          PatchPath.Field("level10")
        )
        val op = DynamicPatchOp(
          deepPath,
          Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("deep value")))
        )
        val json =
          """{"path":[{"Field":{"name":"level1"}},{"Field":{"name":"level2"}},{"Field":{"name":"level3"}},{"Field":{"name":"level4"}},{"Field":{"name":"level5"}},{"Field":{"name":"level6"}},{"Field":{"name":"level7"}},{"Field":{"name":"level8"}},{"Field":{"name":"level9"}},{"Field":{"name":"level10"}}],"operation":{"Set":{"value":"deep value"}}}"""
        roundTrip(op, json)
      }
    ),
    suite("Stability tests")(
      test("Roundtrip preserves patch equality") {
        val patch = DynamicPatch(
          Vector(
            DynamicPatchOp(
              Vector(PatchPath.Field("name")),
              Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("Alice")))
            ),
            DynamicPatchOp(
              Vector(PatchPath.Field("age")),
              Operation.PrimitiveDelta(PrimitiveOp.IntDelta(5))
            )
          )
        )

        val json =
          """{"ops":[{"path":[{"Field":{"name":"name"}}],"operation":{"Set":{"value":"Alice"}}},{"path":[{"Field":{"name":"age"}}],"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":5}}}}}]}"""
        roundTrip(patch, json)
      }
    )
  )
}
