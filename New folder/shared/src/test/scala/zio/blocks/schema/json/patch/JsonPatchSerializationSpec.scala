package zio.blocks.schema.json.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.json._
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.schema.json.JsonTestUtils._
import zio.blocks.schema.patch.{DynamicPatch, PatchMode}
import zio.test._

object JsonPatchSerializationSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("JsonPatchSerializationSpec")(
    stringOpSuite,
    primitiveOpSuite,
    arrayOpSuite,
    objectOpSuite,
    opSuite,
    jsonPatchOpSuite,
    jsonPatchSuite,
    complexNestedSuite,
    endToEndSuite,
    dynamicPatchConversionSuite
  )

  // StringOp Serialization Suite

  private lazy val stringOpSuite = suite("StringOp serialization")(
    test("StringOp.Insert serializes") {
      roundTrip(
        StringOp.Insert(5, "hello"): StringOp,
        """{"Insert":{"index":5,"text":"hello"}}"""
      )
    },
    test("StringOp.Delete serializes") {
      roundTrip(
        StringOp.Delete(3, 10): StringOp,
        """{"Delete":{"index":3,"length":10}}"""
      )
    },
    test("StringOp.Append serializes") {
      roundTrip(
        StringOp.Append(" world"): StringOp,
        """{"Append":{"text":" world"}}"""
      )
    },
    test("StringOp.Modify serializes") {
      roundTrip(
        StringOp.Modify(2, 5, "replacement"): StringOp,
        """{"Modify":{"index":2,"length":5,"text":"replacement"}}"""
      )
    },
    test("StringOp with unicode text") {
      roundTrip(
        StringOp.Insert(0, "日本語テキスト"): StringOp,
        """{"Insert":{"index":0,"text":"日本語テキスト"}}"""
      )
    }
  )

  // PrimitiveOp Serialization Suite

  private lazy val primitiveOpSuite = suite("PrimitiveOp serialization")(
    test("PrimitiveOp.NumberDelta with positive value") {
      roundTrip(
        PrimitiveOp.NumberDelta(BigDecimal(42)): PrimitiveOp,
        """{"NumberDelta":{"delta":42}}"""
      )
    },
    test("PrimitiveOp.NumberDelta with negative value") {
      roundTrip(
        PrimitiveOp.NumberDelta(BigDecimal(-10)): PrimitiveOp,
        """{"NumberDelta":{"delta":-10}}"""
      )
    },
    test("PrimitiveOp.NumberDelta with decimal value") {
      roundTrip(
        PrimitiveOp.NumberDelta(BigDecimal("3.14159")): PrimitiveOp,
        """{"NumberDelta":{"delta":3.14159}}"""
      )
    },
    test("PrimitiveOp.StringEdit with single operation") {
      roundTrip(
        PrimitiveOp.StringEdit(Vector(StringOp.Insert(0, "prefix"))): PrimitiveOp,
        """{"StringEdit":{"ops":[{"Insert":{"index":0,"text":"prefix"}}]}}"""
      )
    },
    test("PrimitiveOp.StringEdit with multiple operations") {
      roundTrip(
        PrimitiveOp.StringEdit(
          Vector(
            StringOp.Delete(0, 5),
            StringOp.Insert(0, "new"),
            StringOp.Append("!")
          )
        ): PrimitiveOp,
        """{"StringEdit":{"ops":[{"Delete":{"index":0,"length":5}},{"Insert":{"index":0,"text":"new"}},{"Append":{"text":"!"}}]}}"""
      )
    }
  )

  // ArrayOp Serialization Suite

  private lazy val arrayOpSuite = suite("ArrayOp serialization")(
    test("ArrayOp.Insert serializes") {
      roundTrip(
        ArrayOp.Insert(1, Chunk(Json.Number(42), Json.String("test"))): ArrayOp,
        """{"Insert":{"index":1,"values":[{"Number":{"value":"42"}},{"String":{"value":"test"}}]}}"""
      )
    },
    test("ArrayOp.Append serializes") {
      roundTrip(
        ArrayOp.Append(Chunk(Json.Boolean(true), Json.Null)): ArrayOp,
        """{"Append":{"values":[{"Boolean":{"value":true}},{"Null":{}}]}}"""
      )
    },
    test("ArrayOp.Delete serializes") {
      roundTrip(
        ArrayOp.Delete(2, 3): ArrayOp,
        """{"Delete":{"index":2,"count":3}}"""
      )
    },
    test("ArrayOp.Modify with Set operation") {
      roundTrip(
        ArrayOp.Modify(0, Op.Set(Json.Number(100))): ArrayOp,
        """{"Modify":{"index":0,"op":{"Set":{"value":{"Number":{"value":"100"}}}}}}"""
      )
    },
    test("ArrayOp.Modify with nested PrimitiveDelta") {
      roundTrip(
        ArrayOp.Modify(5, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(10)))): ArrayOp,
        """{"Modify":{"index":5,"op":{"PrimitiveDelta":{"op":{"NumberDelta":{"delta":10}}}}}}"""
      )
    }
  )

  // ObjectOp Serialization Suite

  private lazy val objectOpSuite = suite("ObjectOp serialization")(
    test("ObjectOp.Add serializes") {
      roundTrip(
        ObjectOp.Add("name", Json.String("Alice")): ObjectOp,
        """{"Add":{"key":"name","value":{"String":{"value":"Alice"}}}}"""
      )
    },
    test("ObjectOp.Remove serializes") {
      roundTrip(
        ObjectOp.Remove("obsolete"): ObjectOp,
        """{"Remove":{"key":"obsolete"}}"""
      )
    },
    test("ObjectOp.Modify serializes") {
      val innerPatch = JsonPatch.root(Op.Set(Json.Number(42)))
      roundTrip(
        ObjectOp.Modify("counter", innerPatch): ObjectOp,
        // Empty path serializes as empty object
        """{"Modify":{"key":"counter","patch":{"ops":[{"path":{},"operation":{"Set":{"value":{"Number":{"value":"42"}}}}}]}}}"""
      )
    },
    test("ObjectOp with unicode keys") {
      roundTrip(
        ObjectOp.Add("名前", Json.String("太郎")): ObjectOp,
        """{"Add":{"key":"名前","value":{"String":{"value":"太郎"}}}}"""
      )
    }
  )

  // Op Serialization Suite

  private lazy val opSuite = suite("Op serialization")(
    test("Op.Set with simple value") {
      roundTrip(
        Op.Set(Json.String("hello")): Op,
        """{"Set":{"value":{"String":{"value":"hello"}}}}"""
      )
    },
    test("Op.Set with complex object") {
      roundTrip(
        Op.Set(Json.Object("a" -> Json.Number(1), "b" -> Json.Array(Json.Boolean(true)))): Op,
        """{"Set":{"value":{"Object":{"value":[["a",{"Number":{"value":"1"}}],["b",{"Array":{"value":[{"Boolean":{"value":true}}]}}]]}}}}"""
      )
    },
    test("Op.PrimitiveDelta with NumberDelta") {
      roundTrip(
        Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))): Op,
        """{"PrimitiveDelta":{"op":{"NumberDelta":{"delta":5}}}}"""
      )
    },
    test("Op.PrimitiveDelta with StringEdit") {
      roundTrip(
        Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Append("!")))): Op,
        """{"PrimitiveDelta":{"op":{"StringEdit":{"ops":[{"Append":{"text":"!"}}]}}}}"""
      )
    },
    test("Op.ArrayEdit serializes") {
      roundTrip(
        Op.ArrayEdit(
          Vector(
            ArrayOp.Append(Chunk(Json.Number(1))),
            ArrayOp.Delete(0, 1)
          )
        ): Op,
        """{"ArrayEdit":{"ops":[{"Append":{"values":[{"Number":{"value":"1"}}]}},{"Delete":{"index":0,"count":1}}]}}"""
      )
    },
    test("Op.ObjectEdit serializes") {
      roundTrip(
        Op.ObjectEdit(
          Vector(
            ObjectOp.Add("x", Json.Number(10)),
            ObjectOp.Remove("y")
          )
        ): Op,
        """{"ObjectEdit":{"ops":[{"Add":{"key":"x","value":{"Number":{"value":"10"}}}},{"Remove":{"key":"y"}}]}}"""
      )
    },
    test("Op.Nested serializes") {
      val innerPatch = JsonPatch.root(Op.Set(Json.Boolean(true)))
      roundTrip(
        Op.Nested(innerPatch): Op,
        // Empty path serializes as empty object
        """{"Nested":{"patch":{"ops":[{"path":{},"operation":{"Set":{"value":{"Boolean":{"value":true}}}}}]}}}"""
      )
    }
  )

  // JsonPatchOp Serialization Suite

  private lazy val jsonPatchOpSuite = suite("JsonPatchOp serialization")(
    test("JsonPatchOp with empty path") {
      roundTrip(
        JsonPatchOp(DynamicOptic.root, Op.Set(Json.Number(42))),
        // Empty path serializes as empty object
        """{"path":{},"operation":{"Set":{"value":{"Number":{"value":"42"}}}}}"""
      )
    },
    test("JsonPatchOp with single field path") {
      roundTrip(
        JsonPatchOp(
          DynamicOptic.root.field("name"),
          Op.Set(Json.String("Bob"))
        ),
        // Path is serialized as DynamicOptic record with nodes field
        """{"path":{"nodes":[{"Field":{"name":"name"}}]},"operation":{"Set":{"value":{"String":{"value":"Bob"}}}}}"""
      )
    },
    test("JsonPatchOp with nested path") {
      roundTrip(
        JsonPatchOp(
          DynamicOptic.root.field("user").field("address").field("city"),
          Op.Set(Json.String("NYC"))
        ),
        """{"path":{"nodes":[{"Field":{"name":"user"}},{"Field":{"name":"address"}},{"Field":{"name":"city"}}]},"operation":{"Set":{"value":{"String":{"value":"NYC"}}}}}"""
      )
    },
    test("JsonPatchOp with array index in path") {
      roundTrip(
        JsonPatchOp(
          DynamicOptic.root.field("items").at(3),
          Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1)))
        ),
        """{"path":{"nodes":[{"Field":{"name":"items"}},{"AtIndex":{"index":3}}]},"operation":{"PrimitiveDelta":{"op":{"NumberDelta":{"delta":1}}}}}"""
      )
    }
  )

  // JsonPatch Serialization Suite

  private lazy val jsonPatchSuite = suite("JsonPatch serialization")(
    test("empty JsonPatch serializes") {
      roundTrip(
        JsonPatch.empty,
        """{}"""
      )
    },
    test("JsonPatch with single operation") {
      roundTrip(
        JsonPatch.root(Op.Set(Json.String("hello"))),
        // Empty path serializes as empty object
        """{"ops":[{"path":{},"operation":{"Set":{"value":{"String":{"value":"hello"}}}}}]}"""
      )
    },
    test("JsonPatch with multiple operations") {
      roundTrip(
        JsonPatch(
          Vector(
            JsonPatchOp(
              DynamicOptic.root.field("name"),
              Op.Set(Json.String("Alice"))
            ),
            JsonPatchOp(
              DynamicOptic.root.field("age"),
              Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1)))
            )
          )
        ),
        // Path is serialized as DynamicOptic record with nodes field
        """{"ops":[{"path":{"nodes":[{"Field":{"name":"name"}}]},"operation":{"Set":{"value":{"String":{"value":"Alice"}}}}},{"path":{"nodes":[{"Field":{"name":"age"}}]},"operation":{"PrimitiveDelta":{"op":{"NumberDelta":{"delta":1}}}}}]}"""
      )
    }
  )

  // Complex Nested Patch Suite

  private lazy val complexNestedSuite = suite("Complex nested patch serialization")(
    test("deeply nested Op.Nested (3 levels)") {
      val level3 = JsonPatch.root(Op.Set(Json.Number(42)))
      val level2 = JsonPatch.root(Op.Nested(level3))
      val level1 = JsonPatch.root(Op.Nested(level2))

      // Empty paths serialize as empty objects
      roundTrip(
        level1,
        """{"ops":[{"path":{},"operation":{"Nested":{"patch":{"ops":[{"path":{},"operation":{"Nested":{"patch":{"ops":[{"path":{},"operation":{"Set":{"value":{"Number":{"value":"42"}}}}}]}}}}]}}}}]}"""
      )
    },
    test("ObjectOp.Modify with deeply nested patches") {
      val innerPatch = JsonPatch.root(
        Op.ObjectEdit(
          Vector(
            ObjectOp.Add("nested", Json.Object("a" -> Json.Number(1))),
            ObjectOp.Modify(
              "other",
              JsonPatch.root(Op.Set(Json.String("deep")))
            )
          )
        )
      )
      // Empty paths serialize as empty objects
      roundTrip(
        ObjectOp.Modify("outer", innerPatch): ObjectOp,
        """{"Modify":{"key":"outer","patch":{"ops":[{"path":{},"operation":{"ObjectEdit":{"ops":[{"Add":{"key":"nested","value":{"Object":{"value":[["a",{"Number":{"value":"1"}}]]}}}},{"Modify":{"key":"other","patch":{"ops":[{"path":{},"operation":{"Set":{"value":{"String":{"value":"deep"}}}}}]}}}]}}}]}}}"""
      )
    },
    test("ArrayOp.Modify containing ObjectEdit containing ArrayEdit") {
      val patch = JsonPatch.root(
        Op.ArrayEdit(
          Vector(
            ArrayOp.Modify(
              0,
              Op.ObjectEdit(
                Vector(
                  ObjectOp.Modify(
                    "items",
                    JsonPatch.root(
                      Op.ArrayEdit(
                        Vector(
                          ArrayOp.Append(Chunk(Json.Number(1), Json.Number(2)))
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
      )

      // Verify roundtrip works for complex nested structure
      import zio.blocks.schema.json._
      val schema  = Schema[JsonPatch]
      val codec   = schema.derive(JsonBinaryCodecDeriver)
      val encoded = codec.encode(patch, WriterConfig)
      val decoded = codec.decode(encoded, ReaderConfig)
      assertTrue(decoded == Right(patch))
    }
  )

  // End-to-End Suite

  private lazy val endToEndSuite = suite("End-to-end workflow")(
    test("Full workflow: diff -> serialize patch -> deserialize -> apply") {
      // Start with a proper JSON document
      val originalJson = Json.Object(
        "user" -> Json.Object(
          "name"    -> Json.String("Alice"),
          "age"     -> Json.Number(30),
          "email"   -> Json.String("alice@example.com"),
          "tags"    -> Json.Array(Json.String("developer"), Json.String("scala")),
          "address" -> Json.Object(
            "city"    -> Json.String("San Francisco"),
            "country" -> Json.String("USA")
          )
        ),
        "settings" -> Json.Object(
          "theme"         -> Json.String("dark"),
          "notifications" -> Json.Boolean(true)
        )
      )

      val targetJson = Json.Object(
        "user" -> Json.Object(
          "name"    -> Json.String("Alice Smith"),                                                     // Name changed
          "age"     -> Json.Number(31),                                                                // Age incremented
          "email"   -> Json.String("alice@newmail.com"),                                               // Email changed
          "tags"    -> Json.Array(Json.String("developer"), Json.String("scala"), Json.String("zio")), // Tag added
          "address" -> Json.Object(
            "city"    -> Json.String("New York"), // City changed
            "country" -> Json.String("USA")
          )
        ),
        "settings" -> Json.Object(
          "theme"         -> Json.String("light"), // Theme changed
          "notifications" -> Json.Boolean(false)   // Notifications toggled
        )
      )

      // Step 1: Compute the diff to get a JsonPatch
      val patch = originalJson.diff(targetJson)

      // Step 2: Serialize the patch to JSON bytes
      val patchSchema = Schema[JsonPatch]
      val patchCodec  = patchSchema.derive(JsonBinaryCodecDeriver)
      val patchBytes  = patchCodec.encode(patch, WriterConfig)

      // Step 3: Deserialize the patch from JSON bytes
      val deserializedPatch = patchCodec.decode(patchBytes, ReaderConfig)

      // Step 4: Apply the deserialized patch to the original JSON
      val patchedResult = deserializedPatch.flatMap(_.apply(originalJson, PatchMode.Strict))

      // Verify:
      // 1. Patch serialization roundtrip succeeds
      assertTrue(deserializedPatch.isRight) &&
      // 2. Applying the patch produces the target JSON
      assertTrue(patchedResult == Right(targetJson))
    },
    test("Serialize and deserialize a patch with mixed operation types") {
      // Create a patch with various operation types
      val complexPatch = JsonPatch(
        Vector(
          // Set a field
          JsonPatchOp(
            DynamicOptic.root.field("title"),
            Op.Set(Json.String("Updated Title"))
          ),
          // Apply a number delta
          JsonPatchOp(
            DynamicOptic.root.field("count"),
            Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5)))
          ),
          // Apply string edits
          JsonPatchOp(
            DynamicOptic.root.field("description"),
            Op.PrimitiveDelta(
              PrimitiveOp.StringEdit(
                Vector(
                  StringOp.Insert(0, "IMPORTANT: "),
                  StringOp.Append(" (updated)")
                )
              )
            )
          ),
          // Array operations
          JsonPatchOp(
            DynamicOptic.root.field("items"),
            Op.ArrayEdit(
              Vector(
                ArrayOp.Append(Chunk(Json.String("new item"))),
                ArrayOp.Delete(0, 1),
                ArrayOp.Modify(0, Op.Set(Json.String("modified")))
              )
            )
          ),
          // Object operations
          JsonPatchOp(
            DynamicOptic.root.field("metadata"),
            Op.ObjectEdit(
              Vector(
                ObjectOp.Add("created", Json.String("2024-01-01")),
                ObjectOp.Remove("deprecated"),
                ObjectOp.Modify(
                  "version",
                  JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1))))
                )
              )
            )
          ),
          // Nested patch
          JsonPatchOp(
            DynamicOptic.root.field("config"),
            Op.Nested(
              JsonPatch(
                Vector(
                  JsonPatchOp(
                    DynamicOptic.root.field("debug"),
                    Op.Set(Json.Boolean(true))
                  ),
                  JsonPatchOp(
                    DynamicOptic.root.field("timeout"),
                    Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1000)))
                  )
                )
              )
            )
          )
        )
      )

      // Serialize and deserialize
      val patchSchema = Schema[JsonPatch]
      val patchCodec  = patchSchema.derive(JsonBinaryCodecDeriver)
      val encoded     = patchCodec.encode(complexPatch, WriterConfig)
      val decoded     = patchCodec.decode(encoded, ReaderConfig)

      // Verify roundtrip
      assertTrue(decoded == Right(complexPatch))
    },
    test("Patch can be converted to readable JSON string") {
      val patch = JsonPatch(
        Vector(
          JsonPatchOp(
            DynamicOptic.root.field("name"),
            Op.Set(Json.String("Alice"))
          ),
          JsonPatchOp(
            DynamicOptic.root.field("age"),
            Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1)))
          )
        )
      )

      val patchSchema = Schema[JsonPatch]
      val patchCodec  = patchSchema.derive(JsonBinaryCodecDeriver)
      val jsonString  = patchCodec.encodeToString(patch, WriterConfig)

      // Verify it's valid JSON that can be parsed back
      val decoded = patchCodec.decode(jsonString, ReaderConfig)
      assertTrue(
        decoded == Right(patch) &&
          jsonString.nonEmpty &&
          jsonString.contains("Set") &&
          jsonString.contains("NumberDelta")
      )
    }
  )

  // DynamicPatch Conversion Suite

  private lazy val dynamicPatchConversionSuite = suite("DynamicPatch Conversion")(
    test("fromDynamicPatch converts ShortDelta to NumberDelta") {
      val dynamicPatch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.ShortDelta(5.toShort))
          )
        )
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(
        result.isRight && result.exists { patch =>
          patch.ops.head.operation match {
            case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(d)) => d == BigDecimal(5)
            case _                                             => false
          }
        }
      )
    },
    test("fromDynamicPatch converts ByteDelta to NumberDelta") {
      val dynamicPatch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.ByteDelta(3.toByte))
          )
        )
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(
        result.isRight && result.exists { patch =>
          patch.ops.head.operation match {
            case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(d)) => d == BigDecimal(3)
            case _                                             => false
          }
        }
      )
    },
    test("fromDynamicPatch converts FloatDelta to NumberDelta") {
      val dynamicPatch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.FloatDelta(1.5f))
          )
        )
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(
        result.isRight && result.exists { patch =>
          patch.ops.head.operation match {
            case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(d)) => d == BigDecimal(1.5)
            case _                                             => false
          }
        }
      )
    },
    test("fromDynamicPatch converts DoubleDelta to NumberDelta") {
      val dynamicPatch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.DoubleDelta(2.5))
          )
        )
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(
        result.isRight && result.exists { patch =>
          patch.ops.head.operation match {
            case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(d)) => d == BigDecimal(2.5)
            case _                                             => false
          }
        }
      )
    },
    test("fromDynamicPatch converts BigIntDelta to NumberDelta") {
      val dynamicPatch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigIntDelta(BigInt("12345678901234567890")))
          )
        )
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(
        result.isRight && result.exists { patch =>
          patch.ops.head.operation match {
            case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(d)) => d == BigDecimal(BigInt("12345678901234567890"))
            case _                                             => false
          }
        }
      )
    },
    test("fromDynamicPatch converts BigDecimalDelta to NumberDelta") {
      val dynamicPatch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigDecimalDelta(BigDecimal("123.456")))
          )
        )
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(
        result.isRight && result.exists { patch =>
          patch.ops.head.operation match {
            case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(d)) => d == BigDecimal("123.456")
            case _                                             => false
          }
        }
      )
    },
    test("fromDynamicPatch converts IntDelta to NumberDelta") {
      val dynamicPatch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(42))
          )
        )
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(
        result.isRight && result.exists { patch =>
          patch.ops.head.operation match {
            case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(d)) => d == BigDecimal(42)
            case _                                             => false
          }
        }
      )
    },
    test("fromDynamicPatch converts LongDelta to NumberDelta") {
      val dynamicPatch = DynamicPatch(
        Vector(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic.root,
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LongDelta(9999999999L))
          )
        )
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(
        result.isRight && result.exists { patch =>
          patch.ops.head.operation match {
            case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(d)) => d == BigDecimal(9999999999L)
            case _                                             => false
          }
        }
      )
    }
  )
}
