package zio.blocks.schema.json.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaBaseSpec, SchemaError}
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.schema.patch.{DynamicPatch, PatchMode}
import zio.test._

object JsonPatchIntegrationSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("JsonPatch Integration")(
    jsonDiffSuite,
    jsonPatchSuite,
    toDynamicPatchSuite,
    fromDynamicPatchSuite,
    roundtripSuite
  )

  private lazy val jsonDiffSuite = suite("Json.diff method")(
    test("diff returns empty patch for identical values") {
      val json  = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))
      val patch = json.diff(json)
      assertTrue(patch.isEmpty)
    },
    test("diff transforms source to target") {
      val source = Json.Object("name" -> Json.String("Alice"))
      val target = Json.Object("name" -> Json.String("Bob"))
      val patch  = source.diff(target)
      val result = patch.apply(source)
      assertTrue(result == Right(target))
    },
    test("diff works with nested structures") {
      val source = Json.Object(
        "user" -> Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(25))
      )
      val target = Json.Object(
        "user" -> Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(26))
      )
      val patch  = source.diff(target)
      val result = patch.apply(source)
      assertTrue(result == Right(target))
    },
    test("diff returns JsonPatch type (not Json)") {
      val source           = Json.Number(1)
      val target           = Json.Number(2)
      val patch: JsonPatch = source.diff(target) // Type annotation proves return type
      assertTrue(!patch.isEmpty)
    },
    test("diff handles type changes") {
      val source = Json.String("hello")
      val target = Json.Number(42)
      val patch  = source.diff(target)
      val result = patch.apply(source)
      assertTrue(result == Right(target))
    },
    test("diff handles array changes") {
      val source = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val target = Json.Array(Json.Number(1), Json.Number(4), Json.Number(3))
      val patch  = source.diff(target)
      val result = patch.apply(source)
      assertTrue(result == Right(target))
    }
  )

  private lazy val jsonPatchSuite = suite("Json.patch method")(
    test("patch uses Strict mode by default") {
      val json = Json.Object("a" -> Json.Number(1))
      // Create a patch that adds a key that already exists
      val patch  = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Add("a", Json.Number(2)))))
      val result = json.patch(patch)
      assertTrue(result.isLeft) // Should fail in Strict mode
    },
    test("patch with Lenient mode skips failing operations") {
      val json = Json.Object("a" -> Json.Number(1))
      // Create a patch that adds a key that already exists
      val patch  = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Add("a", Json.Number(2)))))
      val result = json.patch(patch, PatchMode.Lenient)
      assertTrue(result == Right(json)) // Original unchanged, operation skipped
    },
    test("patch with Clobber mode overwrites") {
      val json = Json.Object("a" -> Json.Number(1))
      // Create a patch that adds a key that already exists
      val patch  = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Add("a", Json.Number(2)))))
      val result = json.patch(patch, PatchMode.Clobber)
      assertTrue(result == Right(Json.Object("a" -> Json.Number(2)))) // Overwrites
    },
    test("patch applies multiple operations") {
      val json  = Json.Object("x" -> Json.Number(1))
      val patch = JsonPatch(
        Vector(
          JsonPatchOp(DynamicOptic.root, Op.ObjectEdit(Vector(ObjectOp.Add("y", Json.Number(2))))),
          JsonPatchOp(DynamicOptic.root, Op.ObjectEdit(Vector(ObjectOp.Add("z", Json.Number(3)))))
        )
      )
      val result   = json.patch(patch)
      val expected = Json.Object("x" -> Json.Number(1), "y" -> Json.Number(2), "z" -> Json.Number(3))
      assertTrue(result == Right(expected))
    },
    test("patch returns Either[SchemaError, Json]") {
      val json                              = Json.Number(10)
      val patch                             = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))))
      val result: Either[SchemaError, Json] = json.patch(patch) // Type annotation proves return type
      assertTrue(result == Right(Json.Number("15")))
    }
  )

  // toDynamicPatch Suite

  private lazy val toDynamicPatchSuite = suite("toDynamicPatch")(
    test("empty patch converts to empty DynamicPatch") {
      val jsonPatch    = JsonPatch.empty
      val dynamicPatch = jsonPatch.toDynamicPatch
      assertTrue(dynamicPatch.isEmpty)
    },
    test("Set operation converts correctly") {
      val jsonPatch    = JsonPatch.root(Op.Set(Json.String("hello")))
      val dynamicPatch = jsonPatch.toDynamicPatch
      assertTrue(
        dynamicPatch.ops.length == 1 &&
          dynamicPatch.ops.head.operation.isInstanceOf[DynamicPatch.Operation.Set]
      )
    },
    test("NumberDelta converts to BigDecimalDelta") {
      val jsonPatch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(42))))
      val dynamicPatch = jsonPatch.toDynamicPatch
      dynamicPatch.ops.head.operation match {
        case DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigDecimalDelta(delta)) =>
          assertTrue(delta == BigDecimal(42))
        case _ =>
          assertTrue(false)
      }
    },
    test("StringEdit converts correctly") {
      val jsonPatch = JsonPatch.root(
        Op.PrimitiveDelta(
          PrimitiveOp.StringEdit(
            Vector(
              StringOp.Insert(0, "hello"),
              StringOp.Delete(5, 3)
            )
          )
        )
      )
      val dynamicPatch = jsonPatch.toDynamicPatch
      dynamicPatch.ops.head.operation match {
        case DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.StringEdit(ops)) =>
          assertTrue(ops.length == 2)
        case _ =>
          assertTrue(false)
      }
    },
    test("ArrayEdit converts to SequenceEdit") {
      val jsonPatch = JsonPatch.root(
        Op.ArrayEdit(
          Vector(
            ArrayOp.Append(Chunk(Json.Number(1))),
            ArrayOp.Insert(0, Chunk(Json.Number(2))),
            ArrayOp.Delete(1, 1)
          )
        )
      )
      val dynamicPatch = jsonPatch.toDynamicPatch
      dynamicPatch.ops.head.operation match {
        case DynamicPatch.Operation.SequenceEdit(ops) =>
          assertTrue(ops.length == 3)
        case _ =>
          assertTrue(false)
      }
    },
    test("ObjectEdit converts to MapEdit with string keys") {
      val jsonPatch = JsonPatch.root(
        Op.ObjectEdit(
          Vector(
            ObjectOp.Add("name", Json.String("Alice")),
            ObjectOp.Remove("age")
          )
        )
      )
      val dynamicPatch = jsonPatch.toDynamicPatch
      dynamicPatch.ops.head.operation match {
        case DynamicPatch.Operation.MapEdit(ops) =>
          assertTrue(
            ops.length == 2 &&
              ops.head.isInstanceOf[DynamicPatch.MapOp.Add] &&
              ops(1).isInstanceOf[DynamicPatch.MapOp.Remove]
          )
        case _ =>
          assertTrue(false)
      }
    },
    test("Nested patch converts to Patch operation") {
      val innerPatch   = JsonPatch.root(Op.Set(Json.Number(42)))
      val jsonPatch    = JsonPatch.root(Op.Nested(innerPatch))
      val dynamicPatch = jsonPatch.toDynamicPatch
      dynamicPatch.ops.head.operation match {
        case DynamicPatch.Operation.Patch(nested) =>
          assertTrue(nested.ops.length == 1)
        case _ =>
          assertTrue(false)
      }
    }
  )

  // fromDynamicPatch Suite

  private lazy val fromDynamicPatchSuite = suite("fromDynamicPatch")(
    test("empty DynamicPatch converts to empty JsonPatch") {
      val dynamicPatch = DynamicPatch.empty
      val result       = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(result == Right(JsonPatch.empty))
    },
    test("IntDelta widens to NumberDelta") {
      val dynamicPatch = DynamicPatch.root(
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(42))
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      result match {
        case Right(patch) =>
          patch.ops.head.operation match {
            case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(delta)) =>
              assertTrue(delta == BigDecimal(42))
            case _ => assertTrue(false)
          }
        case Left(_) => assertTrue(false)
      }
    },
    test("LongDelta widens to NumberDelta") {
      val dynamicPatch = DynamicPatch.root(
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LongDelta(123456789012345L))
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      result match {
        case Right(patch) =>
          patch.ops.head.operation match {
            case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(delta)) =>
              assertTrue(delta == BigDecimal(123456789012345L))
            case _ => assertTrue(false)
          }
        case Left(_) => assertTrue(false)
      }
    },
    test("DoubleDelta widens to NumberDelta") {
      val dynamicPatch = DynamicPatch.root(
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.DoubleDelta(3.14159))
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      result match {
        case Right(patch) =>
          patch.ops.head.operation match {
            case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(delta)) =>
              assertTrue(delta == BigDecimal(3.14159))
            case _ => assertTrue(false)
          }
        case Left(_) => assertTrue(false)
      }
    },
    test("BigDecimalDelta converts to NumberDelta") {
      val dynamicPatch = DynamicPatch.root(
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigDecimalDelta(BigDecimal("123.456")))
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      result match {
        case Right(patch) =>
          patch.ops.head.operation match {
            case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(delta)) =>
              assertTrue(delta == BigDecimal("123.456"))
            case _ => assertTrue(false)
          }
        case Left(_) => assertTrue(false)
      }
    },
    test("InstantDelta fails with SchemaError") {
      import java.time.Duration
      val dynamicPatch = DynamicPatch.root(
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.InstantDelta(Duration.ofHours(1)))
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(
        result.isLeft &&
          result.left.exists(_.message.contains("InstantDelta"))
      )
    },
    test("DurationDelta fails with SchemaError") {
      import java.time.Duration
      val dynamicPatch = DynamicPatch.root(
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.DurationDelta(Duration.ofMinutes(30)))
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(
        result.isLeft &&
          result.left.exists(_.message.contains("DurationDelta"))
      )
    },
    test("LocalDateDelta fails with SchemaError") {
      import java.time.Period
      val dynamicPatch = DynamicPatch.root(
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LocalDateDelta(Period.ofDays(7)))
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(
        result.isLeft &&
          result.left.exists(_.message.contains("LocalDateDelta"))
      )
    },
    test("LocalDateTimeDelta fails with SchemaError") {
      import java.time.{Duration, Period}
      val dynamicPatch = DynamicPatch.root(
        DynamicPatch.Operation.PrimitiveDelta(
          DynamicPatch.PrimitiveOp.LocalDateTimeDelta(Period.ofDays(1), Duration.ofHours(2))
        )
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(
        result.isLeft &&
          result.left.exists(_.message.contains("LocalDateTimeDelta"))
      )
    },
    test("PeriodDelta fails with SchemaError") {
      import java.time.Period
      val dynamicPatch = DynamicPatch.root(
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.PeriodDelta(Period.ofMonths(3)))
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(
        result.isLeft &&
          result.left.exists(_.message.contains("PeriodDelta"))
      )
    },
    test("non-string map keys fail with SchemaError") {
      val dynamicPatch = DynamicPatch.root(
        DynamicPatch.Operation.MapEdit(
          Vector(
            DynamicPatch.MapOp.Add(
              DynamicValue.Primitive(new PrimitiveValue.Int(42)), // Non-string key
              DynamicValue.string("value")
            )
          )
        )
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(
        result.isLeft &&
          result.left.exists(_.message.contains("must be strings"))
      )
    },
    test("string map keys succeed") {
      val dynamicPatch = DynamicPatch.root(
        DynamicPatch.Operation.MapEdit(
          Vector(
            DynamicPatch.MapOp.Add(
              DynamicValue.string("name"),
              DynamicValue.string("Alice")
            )
          )
        )
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(result.isRight)
    }
  )

  // Roundtrip Suite

  private lazy val roundtripSuite = suite("Roundtrip: fromDynamicPatch(p.toDynamicPatch) == Right(p)")(
    test("roundtrip preserves empty patch") {
      val original  = JsonPatch.empty
      val roundtrip = JsonPatch.fromDynamicPatch(original.toDynamicPatch)
      assertTrue(roundtrip == Right(original))
    },
    test("roundtrip preserves Set operation") {
      val original  = JsonPatch.root(Op.Set(Json.String("hello")))
      val roundtrip = JsonPatch.fromDynamicPatch(original.toDynamicPatch)
      assertTrue(roundtrip == Right(original))
    },
    test("roundtrip preserves NumberDelta") {
      val original  = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal("123.456"))))
      val roundtrip = JsonPatch.fromDynamicPatch(original.toDynamicPatch)
      assertTrue(roundtrip == Right(original))
    },
    test("roundtrip preserves StringEdit") {
      val original = JsonPatch.root(
        Op.PrimitiveDelta(
          PrimitiveOp.StringEdit(
            Vector(
              StringOp.Insert(0, "hello"),
              StringOp.Delete(5, 2),
              StringOp.Append(" world"),
              StringOp.Modify(3, 4, "test")
            )
          )
        )
      )
      val roundtrip = JsonPatch.fromDynamicPatch(original.toDynamicPatch)
      assertTrue(roundtrip == Right(original))
    },
    test("roundtrip preserves ArrayEdit") {
      val original = JsonPatch.root(
        Op.ArrayEdit(
          Vector(
            ArrayOp.Insert(0, Chunk(Json.Number(1), Json.Number(2))),
            ArrayOp.Append(Chunk(Json.String("end"))),
            ArrayOp.Delete(2, 1),
            ArrayOp.Modify(0, Op.Set(Json.Boolean(true)))
          )
        )
      )
      val roundtrip = JsonPatch.fromDynamicPatch(original.toDynamicPatch)
      assertTrue(roundtrip == Right(original))
    },
    test("roundtrip preserves ObjectEdit") {
      val original = JsonPatch.root(
        Op.ObjectEdit(
          Vector(
            ObjectOp.Add("name", Json.String("Alice")),
            ObjectOp.Remove("age"),
            ObjectOp.Modify("address", JsonPatch.root(Op.Set(Json.String("123 Main St"))))
          )
        )
      )
      val roundtrip = JsonPatch.fromDynamicPatch(original.toDynamicPatch)
      assertTrue(roundtrip == Right(original))
    },
    test("roundtrip preserves Nested operation") {
      val innerPatch = JsonPatch(
        Vector(
          JsonPatchOp(DynamicOptic.root, Op.Set(Json.Number(42))),
          JsonPatchOp(DynamicOptic.root, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(10))))
        )
      )
      val original  = JsonPatch.root(Op.Nested(innerPatch))
      val roundtrip = JsonPatch.fromDynamicPatch(original.toDynamicPatch)
      assertTrue(roundtrip == Right(original))
    },
    test("roundtrip preserves complex nested structures") {
      val original = JsonPatch(
        Vector(
          JsonPatchOp(
            DynamicOptic.root,
            Op.ObjectEdit(
              Vector(
                ObjectOp.Add("users", Json.Array()),
                ObjectOp.Modify(
                  "config",
                  JsonPatch.root(
                    Op.ObjectEdit(
                      Vector(
                        ObjectOp.Add("debug", Json.Boolean(true))
                      )
                    )
                  )
                )
              )
            )
          ),
          JsonPatchOp(
            DynamicOptic.root,
            Op.ArrayEdit(
              Vector(
                ArrayOp.Append(Chunk(Json.Object("id" -> Json.Number(1))))
              )
            )
          )
        )
      )
      val roundtrip = JsonPatch.fromDynamicPatch(original.toDynamicPatch)
      assertTrue(roundtrip == Right(original))
    },
    test("roundtrip with property-based test (semantic equality)") {

      check(JsonGen.genJson, JsonGen.genJson) { (a, b) =>
        val patch     = a.diff(b)
        val roundtrip = JsonPatch.fromDynamicPatch(patch.toDynamicPatch)
        // Both patches should produce semantically equal results when applied
        val originalResult  = patch.apply(a).map(_.toDynamicValue)
        val roundtripResult = roundtrip.flatMap(_.apply(a)).map(_.toDynamicValue)
        assertTrue(
          roundtrip.isRight &&
            originalResult == roundtripResult
        )
      }
    }
  ) @@ TestAspect.samples(100)
}
