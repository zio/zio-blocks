package zio.blocks.schema.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.json.JsonTestUtils._
import zio.test._

object SerializationSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("SerializationSpec")(
    suite("DynamicOptic.Node serialization")(
      test("DynamicOptic.Node.Field serializes") {
        roundTrip(
          DynamicOptic.Node.Field("name"): DynamicOptic.Node,
          """{"Field":{"name":"name"}}"""
        )
      },
      test("DynamicOptic.Node.AtIndex serializes") {
        roundTrip(
          DynamicOptic.Node.AtIndex(5): DynamicOptic.Node,
          """{"AtIndex":{"index":5}}"""
        )
      },
      test("DynamicOptic.Node.AtMapKey serializes") {
        val key = DynamicValue.Primitive(PrimitiveValue.String("key1"))
        roundTrip(
          DynamicOptic.Node.AtMapKey(key): DynamicOptic.Node,
          """{"AtMapKey":{"key":"key1"}}"""
        )
      },
      test("DynamicOptic.Node.AtMapKey with Int key") {
        val key = DynamicValue.Primitive(PrimitiveValue.Int(42))
        roundTrip(
          DynamicOptic.Node.AtMapKey(key): DynamicOptic.Node,
          """{"AtMapKey":{"key":42}}"""
        )
      },
      test("DynamicOptic.Node.Case serializes") {
        roundTrip(
          DynamicOptic.Node.Case("Active"): DynamicOptic.Node,
          """{"Case":{"name":"Active"}}"""
        )
      },
      test("DynamicOptic.Node.Elements serializes") {
        roundTrip(
          DynamicOptic.Node.Elements: DynamicOptic.Node,
          """{"Elements":{}}"""
        )
      },
      test("DynamicOptic.Node.Wrapped serializes") {
        roundTrip(
          DynamicOptic.Node.Wrapped: DynamicOptic.Node,
          """{"Wrapped":{}}"""
        )
      }
    ),
    suite("DynamicPatchOp serialization")(
      test("DynamicPatchOp with empty path and Set operation") {
        val op = DynamicPatch.DynamicPatchOp(
          DynamicOptic.root,
          DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        roundTrip(
          op,
          """{"path":{},"operation":{"Set":{"value":42}}}"""
        )
      },
      test("DynamicPatchOp with single field path") {
        val op = DynamicPatch.DynamicPatchOp(
          DynamicOptic(Vector(DynamicOptic.Node.Field("age"))),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
        )
        roundTrip(
          op,
          """{"path":{"nodes":[{"Field":{"name":"age"}}]},"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":5}}}}}"""
        )
      },
      test("DynamicPatchOp with nested path") {
        val op = DynamicPatch.DynamicPatchOp(
          DynamicOptic(Vector(DynamicOptic.Node.Field("address"), DynamicOptic.Node.Field("city"))),
          DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("NYC")))
        )
        roundTrip(
          op,
          """{"path":{"nodes":[{"Field":{"name":"address"}},{"Field":{"name":"city"}}]},"operation":{"Set":{"value":"NYC"}}}"""
        )
      },
      test("DynamicPatchOp with index path") {
        val op = DynamicPatch.DynamicPatchOp(
          DynamicOptic(Vector(DynamicOptic.Node.Field("items"), DynamicOptic.Node.AtIndex(2))),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(10))
        )
        roundTrip(
          op,
          """{"path":{"nodes":[{"Field":{"name":"items"}},{"AtIndex":{"index":2}}]},"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":10}}}}}"""
        )
      },
      test("DynamicPatchOp with SequenceEdit") {
        val op = DynamicPatch.DynamicPatchOp(
          DynamicOptic(Vector(DynamicOptic.Node.Field("tags"))),
          DynamicPatch.Operation.SequenceEdit(
            Chunk(DynamicPatch.SeqOp.Append(Chunk(DynamicValue.Primitive(PrimitiveValue.String("new")))))
          )
        )
        roundTrip(
          op,
          """{"path":{"nodes":[{"Field":{"name":"tags"}}]},"operation":{"SequenceEdit":{"ops":[{"Append":{"values":["new"]}}]}}}"""
        )
      },
      test("DynamicPatchOp with MapEdit") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("key1"))
        val value = DynamicValue.Primitive(PrimitiveValue.Int(100))
        val op    = DynamicPatch.DynamicPatchOp(
          DynamicOptic(Chunk(DynamicOptic.Node.Field("metadata"))),
          DynamicPatch.Operation.MapEdit(Chunk(DynamicPatch.MapOp.Add(key, value)))
        )
        roundTrip(
          op,
          """{"path":{"nodes":[{"Field":{"name":"metadata"}}]},"operation":{"MapEdit":{"ops":[{"Add":{"key":"key1","value":100}}]}}}"""
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
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("age"))),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
            )
          )
        )
        roundTrip(
          patch,
          """{"ops":[{"path":{"nodes":[{"Field":{"name":"age"}}]},"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":1}}}}}]}"""
        )
      },
      test("DynamicPatch with multiple operations") {
        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("name"))),
              DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("Alice")))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("age"))),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
            )
          )
        )
        roundTrip(
          patch,
          """{"ops":[{"path":{"nodes":[{"Field":{"name":"name"}}]},"operation":{"Set":{"value":"Alice"}}},{"path":{"nodes":[{"Field":{"name":"age"}}]},"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":5}}}}}]}"""
        )
      },
      test("DynamicPatch with nested record operations") {
        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("address"), DynamicOptic.Node.Field("street"))),
              DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("123 Main St")))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("address"), DynamicOptic.Node.Field("zipCode"))),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(10000))
            )
          )
        )
        roundTrip(
          patch,
          """{"ops":[{"path":{"nodes":[{"Field":{"name":"address"}},{"Field":{"name":"street"}}]},"operation":{"Set":{"value":"123 Main St"}}},{"path":{"nodes":[{"Field":{"name":"address"}},{"Field":{"name":"zipCode"}}]},"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":10000}}}}}]}"""
        )
      },
      test("DynamicPatch with sequence operations") {
        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("items"))),
              DynamicPatch.Operation.SequenceEdit(
                Chunk(
                  DynamicPatch.SeqOp.Append(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1)))),
                  DynamicPatch.SeqOp.Insert(0, Chunk(DynamicValue.Primitive(PrimitiveValue.Int(0))))
                )
              )
            )
          )
        )
        roundTrip(
          patch,
          """{"ops":[{"path":{"nodes":[{"Field":{"name":"items"}}]},"operation":{"SequenceEdit":{"ops":[{"Append":{"values":[1]}},{"Insert":{"index":0,"values":[0]}}]}}}]}"""
        )
      },
      test("DynamicPatch with map operations") {
        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("metadata"))),
              DynamicPatch.Operation.MapEdit(
                Chunk(
                  DynamicPatch.MapOp.Add(
                    DynamicValue.Primitive(PrimitiveValue.String("key1")),
                    DynamicValue.Primitive(PrimitiveValue.Int(100))
                  ),
                  DynamicPatch.MapOp.Remove(DynamicValue.Primitive(PrimitiveValue.String("key2")))
                )
              )
            )
          )
        )
        roundTrip(
          patch,
          """{"ops":[{"path":{"nodes":[{"Field":{"name":"metadata"}}]},"operation":{"MapEdit":{"ops":[{"Add":{"key":"key1","value":100}},{"Remove":{"key":"key2"}}]}}}]}"""
        )
      }
    ),
    suite("Complex nested patch serialization")(
      test("Deeply nested patch with multiple operation types") {
        val patch = DynamicPatch(
          Chunk(
            // Set a nested field
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("company"), DynamicOptic.Node.Field("name"))),
              DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("Acme Corp")))
            ),
            // Modify an array element
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(
                Chunk(
                  DynamicOptic.Node.Field("company"),
                  DynamicOptic.Node.Field("employees"),
                  DynamicOptic.Node.AtIndex(0),
                  DynamicOptic.Node.Field("age")
                )
              ),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
            ),
            // Append to a sequence
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("tags"))),
              DynamicPatch.Operation.SequenceEdit(
                Chunk(
                  DynamicPatch.SeqOp.Append(
                    Chunk(
                      DynamicValue.Primitive(PrimitiveValue.String("important"))
                    )
                  )
                )
              )
            )
          )
        )

        val json =
          """{"ops":[{"path":{"nodes":[{"Field":{"name":"company"}},{"Field":{"name":"name"}}]},"operation":{"Set":{"value":"Acme Corp"}}},{"path":{"nodes":[{"Field":{"name":"company"}},{"Field":{"name":"employees"}},{"AtIndex":{"index":0}},{"Field":{"name":"age"}}]},"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":1}}}}},{"path":{"nodes":[{"Field":{"name":"tags"}}]},"operation":{"SequenceEdit":{"ops":[{"Append":{"values":["important"]}}]}}}]}"""

        // Just verify decode works
        decode(json, patch)
      },
      test("Patch with all PrimitiveOp types") {
        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root,
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root,
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LongDelta(2L))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root,
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.DoubleDelta(3.14))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root,
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.FloatDelta(2.71f))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root,
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigIntDelta(BigInt(1000)))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root,
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigDecimalDelta(BigDecimal("123.456")))
            )
          )
        )

        val json =
          """{"ops":[{"path":{"nodes":[]},"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":1}}}}},{"path":{"nodes":[]},"operation":{"PrimitiveDelta":{"op":{"LongDelta":{"delta":2}}}}},{"path":{"nodes":[]},"operation":{"PrimitiveDelta":{"op":{"DoubleDelta":{"delta":3.14}}}}},{"path":{"nodes":[]},"operation":{"PrimitiveDelta":{"op":{"FloatDelta":{"delta":2.71}}}}},{"path":{"nodes":[]},"operation":{"PrimitiveDelta":{"op":{"BigIntDelta":{"delta":1000}}}}},{"path":{"nodes":[]},"operation":{"PrimitiveDelta":{"op":{"BigDecimalDelta":{"delta":123.456}}}}}]}"""
        decode(json, patch)
      },
      test("Large patch with many operations") {
        val ops = (0 until 20).map { i =>
          DynamicPatch.DynamicPatchOp(
            DynamicOptic(Chunk(DynamicOptic.Node.Field(s"field$i"))),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(i))
          )
        }
        val patch = DynamicPatch(Chunk.from(ops))
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
      test("DynamicOptic.Node with unicode field names") {
        val op = DynamicPatch.DynamicPatchOp(
          DynamicOptic(Chunk(DynamicOptic.Node.Field("名前"), DynamicOptic.Node.Field("年齢"))),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
        )
        val json =
          """{"path":{"nodes":[{"Field":{"name":"名前"}},{"Field":{"name":"年齢"}}]},"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":1}}}}}"""
        roundTrip(op, json)
      },
      test("DynamicPatch with empty string field names") {
        val op = DynamicPatch.DynamicPatchOp(
          DynamicOptic(Chunk(DynamicOptic.Node.Field(""))),
          DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val json = """{"path":{"nodes":[{"Field":{"name":""}}]},"operation":{"Set":{"value":42}}}"""
        roundTrip(op, json)
      },
      test("AtMapKey with complex DynamicValue") {
        val complexKey = DynamicValue.Record(
          Chunk(
            "field1" -> DynamicValue.Primitive(PrimitiveValue.String("value1")),
            "field2" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
          )
        )
        val path = DynamicOptic.Node.AtMapKey(complexKey)
        val json = """{"AtMapKey":{"key":{"field1":"value1","field2":42}}}"""
        roundTrip(path: DynamicOptic.Node, json)
      },
      test("Very deep nesting") {
        val deepPath = Chunk(
          DynamicOptic.Node.Field("level1"),
          DynamicOptic.Node.Field("level2"),
          DynamicOptic.Node.Field("level3"),
          DynamicOptic.Node.Field("level4"),
          DynamicOptic.Node.Field("level5"),
          DynamicOptic.Node.Field("level6"),
          DynamicOptic.Node.Field("level7"),
          DynamicOptic.Node.Field("level8"),
          DynamicOptic.Node.Field("level9"),
          DynamicOptic.Node.Field("level10")
        )
        val op = DynamicPatch.DynamicPatchOp(
          DynamicOptic(deepPath),
          DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("deep value")))
        )
        val json =
          """{"path":{"nodes":[{"Field":{"name":"level1"}},{"Field":{"name":"level2"}},{"Field":{"name":"level3"}},{"Field":{"name":"level4"}},{"Field":{"name":"level5"}},{"Field":{"name":"level6"}},{"Field":{"name":"level7"}},{"Field":{"name":"level8"}},{"Field":{"name":"level9"}},{"Field":{"name":"level10"}}]},"operation":{"Set":{"value":"deep value"}}}"""
        roundTrip(op, json)
      }
    ),
    suite("Stability tests")(
      test("Roundtrip preserves patch equality") {
        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("name"))),
              DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("Alice")))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("age"))),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
            )
          )
        )

        val json =
          """{"ops":[{"path":{"nodes":[{"Field":{"name":"name"}}]},"operation":{"Set":{"value":"Alice"}}},{"path":{"nodes":[{"Field":{"name":"age"}}]},"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":5}}}}}]}"""
        roundTrip(patch, json)
      },
      test("DynamicPatch with Operation.Patch (nested patch)") {
        val nestedPatch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("street"))),
              DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("456 Elm")))
            ),
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("city"))),
              DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("LA")))
            )
          )
        )

        val patch = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("address"))),
              DynamicPatch.Operation.Patch(nestedPatch)
            )
          )
        )

        val json =
          """{"ops":[{"path":{"nodes":[{"Field":{"name":"address"}}]},"operation":{"Patch":{"patch":{"ops":[{"path":{"nodes":[{"Field":{"name":"street"}}]},"operation":{"Set":{"value":"456 Elm"}}},{"path":{"nodes":[{"Field":{"name":"city"}}]},"operation":{"Set":{"value":"LA"}}}]}}}}]}"""
        roundTrip(patch, json)
      },
      test("DynamicPatch with recursive Operation.Patch (3 levels)") {
        val level3 = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("value"))),
              DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(10))
            )
          )
        )

        val level2 = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("inner"))),
              DynamicPatch.Operation.Patch(level3)
            )
          )
        )

        val level1 = DynamicPatch(
          Chunk(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic(Chunk(DynamicOptic.Node.Field("outer"))),
              DynamicPatch.Operation.Patch(level2)
            )
          )
        )

        val json =
          """{"ops":[{"path":{"nodes":[{"Field":{"name":"outer"}}]},"operation":{"Patch":{"patch":{"ops":[{"path":{"nodes":[{"Field":{"name":"inner"}}]},"operation":{"Patch":{"patch":{"ops":[{"path":{"nodes":[{"Field":{"name":"value"}}]},"operation":{"PrimitiveDelta":{"op":{"IntDelta":{"delta":10}}}}}]}}}}]}}}}]}"""
        roundTrip(level1, json)
      }
    )
  )
}
