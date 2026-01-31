package zio.blocks.schema.json.patch

import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.json.{Json, JsonDiffer, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.test._

object JsonDifferSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("JsonDiffer")(
    numberDiffSuite,
    stringDiffSuite,
    arrayDiffSuite,
    objectDiffSuite,
    typeMismatchSuite,
    edgeCasesSuite,
    propertyBasedSuite
  )

  // Number Diff Suite

  private lazy val numberDiffSuite = suite("Number diff")(
    test("diff produces NumberDelta for positive change") {
      val source = Json.Number(10)
      val target = Json.Number(15)
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(
        patch.ops.length == 1,
        patch.ops.head.operation match {
          case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(delta)) => delta == BigDecimal(5)
          case _                                                 => false
        }
      )
    },
    test("diff produces NumberDelta for negative change") {
      val source = Json.Number(20)
      val target = Json.Number(15)
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(
        patch.ops.length == 1,
        patch.ops.head.operation match {
          case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(delta)) => delta == BigDecimal(-5)
          case _                                                 => false
        }
      )
    },
    test("diff handles decimal numbers") {
      val source = Json.Number("1.5")
      val target = Json.Number("2.75")
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(
        patch.ops.length == 1,
        patch.ops.head.operation match {
          case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(delta)) => delta == BigDecimal("1.25")
          case _                                                 => false
        }
      )
    },
    test("diff handles large numbers") {
      val source = Json.Number("999999999999999999")
      val target = Json.Number("1000000000000000000")
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(
        patch.ops.length == 1,
        patch.ops.head.operation match {
          case Op.PrimitiveDelta(PrimitiveOp.NumberDelta(delta)) => delta == BigDecimal(1)
          case _                                                 => false
        }
      )
    },
    test("diff roundtrip for numbers") {
      val source = Json.Number(42)
      val target = Json.Number(100)
      val patch  = JsonDiffer.diff(source, target)
      val result = patch.apply(source)

      assertTrue(result == Right(target))
    }
  )

  // String Diff Suite

  private lazy val stringDiffSuite = suite("String diff")(
    test("diff produces StringEdit for prefix change") {
      val source = Json.String("world")
      val target = Json.String("hello world")
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(patch.apply(source) == Right(target))
    },
    test("diff produces StringEdit for suffix change") {
      val source = Json.String("hello")
      val target = Json.String("hello world")
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(patch.apply(source) == Right(target))
    },
    test("diff produces StringEdit for middle change") {
      val source = Json.String("hello world")
      val target = Json.String("hello brave world")
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(patch.apply(source) == Right(target))
    },
    test("diff uses Set for complete replacement when more efficient") {
      val source = Json.String("abc")
      val target = Json.String("xyz")
      val patch  = JsonDiffer.diff(source, target)

      // When strings are completely different, Set is more efficient than edits
      assertTrue(patch.apply(source) == Right(target))
    },
    test("diff handles empty to non-empty") {
      val source = Json.String("")
      val target = Json.String("hello")
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(patch.apply(source) == Right(target))
    },
    test("diff handles non-empty to empty") {
      val source = Json.String("hello")
      val target = Json.String("")
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(patch.apply(source) == Right(target))
    },
    test("diff roundtrip for strings with common subsequence") {
      val source = Json.String("abcdef")
      val target = Json.String("abXYZdef")
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(patch.apply(source) == Right(target))
    }
  )

  // Array Diff Suite

  private lazy val arrayDiffSuite = suite("Array diff")(
    test("diff produces Append for elements added at end") {
      val source = Json.Array(Json.Number(1), Json.Number(2))
      val target = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(patch.apply(source) == Right(target))
    },
    test("diff produces Insert for elements added at beginning") {
      val source = Json.Array(Json.Number(2), Json.Number(3))
      val target = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(patch.apply(source) == Right(target))
    },
    test("diff produces Delete for elements removed") {
      val source = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val target = Json.Array(Json.Number(1), Json.Number(3))
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(patch.apply(source) == Right(target))
    },
    test("diff handles empty to non-empty array") {
      val source = Json.Array.empty
      val target = Json.Array(Json.Number(1), Json.Number(2))
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(
        patch.apply(source) == Right(target),
        patch.ops.head.operation match {
          case Op.ArrayEdit(ops) =>
            ops.exists {
              case ArrayOp.Append(_) => true
              case _                 => false
            }
          case _ => false
        }
      )
    },
    test("diff handles non-empty to empty array") {
      val source = Json.Array(Json.Number(1), Json.Number(2))
      val target = Json.Array.empty
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(
        patch.apply(source) == Right(target),
        patch.ops.head.operation match {
          case Op.ArrayEdit(ops) =>
            ops.exists {
              case ArrayOp.Delete(0, 2) => true
              case _                    => false
            }
          case _ => false
        }
      )
    },
    test("diff handles reordering") {
      val source = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val target = Json.Array(Json.Number(3), Json.Number(2), Json.Number(1))
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(patch.apply(source) == Right(target))
    },
    test("diff handles complex array transformation") {
      val source = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3), Json.Number(4))
      val target = Json.Array(Json.Number(1), Json.Number(5), Json.Number(6), Json.Number(4))
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(patch.apply(source) == Right(target))
    },
    test("diff roundtrip for nested arrays") {
      val source = Json.Array(
        Json.Array(Json.Number(1), Json.Number(2)),
        Json.Array(Json.Number(3), Json.Number(4))
      )
      val target = Json.Array(
        Json.Array(Json.Number(1), Json.Number(2)),
        Json.Array(Json.Number(5), Json.Number(6))
      )
      val patch = JsonDiffer.diff(source, target)

      assertTrue(patch.apply(source) == Right(target))
    }
  )

  // Object Diff Suite

  private lazy val objectDiffSuite = suite("Object diff")(
    test("diff produces Add for new fields") {
      val source = Json.Object("a" -> Json.Number(1))
      val target = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(
        patch.apply(source) == Right(target),
        patch.ops.head.operation match {
          case Op.ObjectEdit(ops) =>
            ops.exists {
              case ObjectOp.Add("b", Json.Number("2")) => true
              case _                                   => false
            }
          case _ => false
        }
      )
    },
    test("diff produces Remove for deleted fields") {
      val source = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
      val target = Json.Object("a" -> Json.Number(1))
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(
        patch.apply(source) == Right(target),
        patch.ops.head.operation match {
          case Op.ObjectEdit(ops) =>
            ops.exists {
              case ObjectOp.Remove("b") => true
              case _                    => false
            }
          case _ => false
        }
      )
    },
    test("diff produces Modify for changed field values") {
      val source = Json.Object("a" -> Json.Number(1))
      val target = Json.Object("a" -> Json.Number(2))
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(
        patch.apply(source) == Right(target),
        patch.ops.head.operation match {
          case Op.ObjectEdit(ops) =>
            ops.exists {
              case ObjectOp.Modify("a", _) => true
              case _                       => false
            }
          case _ => false
        }
      )
    },
    test("diff handles nested object changes") {
      val source = Json.Object(
        "user" -> Json.Object(
          "name" -> Json.String("Alice"),
          "age"  -> Json.Number(30)
        )
      )
      val target = Json.Object(
        "user" -> Json.Object(
          "name" -> Json.String("Alice"),
          "age"  -> Json.Number(31)
        )
      )
      val patch = JsonDiffer.diff(source, target)

      assertTrue(patch.apply(source) == Right(target))
    },
    test("diff handles multiple field changes") {
      val source = Json.Object(
        "a" -> Json.Number(1),
        "b" -> Json.Number(2),
        "c" -> Json.Number(3)
      )
      val target = Json.Object(
        "a" -> Json.Number(10),
        "c" -> Json.Number(3),
        "d" -> Json.Number(4)
      )
      val patch = JsonDiffer.diff(source, target)

      assertTrue(patch.apply(source) == Right(target))
    },
    test("diff handles empty to non-empty object") {
      val source = Json.Object.empty
      val target = Json.Object("key" -> Json.String("value"))
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(patch.apply(source) == Right(target))
    },
    test("diff handles non-empty to empty object") {
      val source = Json.Object("key" -> Json.String("value"))
      val target = Json.Object.empty
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(patch.apply(source) == Right(target))
    }
  )

  // Type Mismatch Suite

  private lazy val typeMismatchSuite = suite("Type mismatch")(
    test("diff produces Set when changing from Number to String") {
      val source = Json.Number(42)
      val target = Json.String("hello")
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(
        patch.ops.head.operation match {
          case Op.Set(Json.String("hello")) => true
          case _                            => false
        },
        patch.apply(source) == Right(target)
      )
    },
    test("diff produces Set when changing from Object to Array") {
      val source = Json.Object("a" -> Json.Number(1))
      val target = Json.Array(Json.Number(1), Json.Number(2))
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(
        patch.ops.head.operation match {
          case Op.Set(_) => true
          case _         => false
        },
        patch.apply(source) == Right(target)
      )
    },
    test("diff produces Set when changing from Boolean to Number") {
      val source = Json.Boolean(true)
      val target = Json.Number(1)
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(
        patch.ops.head.operation match {
          case Op.Set(Json.Number("1")) => true
          case _                        => false
        },
        patch.apply(source) == Right(target)
      )
    },
    test("diff produces Set when changing from Null to String") {
      val source = Json.Null
      val target = Json.String("not null")
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(
        patch.ops.head.operation match {
          case Op.Set(Json.String("not null")) => true
          case _                               => false
        },
        patch.apply(source) == Right(target)
      )
    },
    test("diff produces Set when changing booleans") {
      val source = Json.Boolean(true)
      val target = Json.Boolean(false)
      val patch  = JsonDiffer.diff(source, target)

      assertTrue(
        patch.ops.head.operation match {
          case Op.Set(Json.Boolean(false)) => true
          case _                           => false
        },
        patch.apply(source) == Right(target)
      )
    }
  )

  // Edge Cases Suite

  private lazy val edgeCasesSuite = suite("Edge cases")(
    test("diff of identical values produces empty patch") {
      val value = Json.Object(
        "a" -> Json.Number(1),
        "b" -> Json.Array(Json.String("x"), Json.String("y"))
      )
      val patch = JsonDiffer.diff(value, value)

      assertTrue(patch.isEmpty)
    },
    test("diff of null with null produces empty patch") {
      val patch = JsonDiffer.diff(Json.Null, Json.Null)

      assertTrue(patch.isEmpty)
    },
    test("diff of identical booleans produces empty patch") {
      val patch = JsonDiffer.diff(Json.Boolean(true), Json.Boolean(true))

      assertTrue(patch.isEmpty)
    },
    test("diff of identical numbers produces empty patch") {
      val patch = JsonDiffer.diff(Json.Number(42), Json.Number(42))

      assertTrue(patch.isEmpty)
    },
    test("diff of identical strings produces empty patch") {
      val patch = JsonDiffer.diff(Json.String("hello"), Json.String("hello"))

      assertTrue(patch.isEmpty)
    },
    test("diff of deeply nested identical structures produces empty patch") {
      val value = Json.Object(
        "level1" -> Json.Object(
          "level2" -> Json.Object(
            "level3" -> Json.Array(
              Json.Object("value" -> Json.Number(42))
            )
          )
        )
      )
      val patch = JsonDiffer.diff(value, value)

      assertTrue(patch.isEmpty)
    }
  )

  // Property-based Suite

  private lazy val propertyBasedSuite = suite("Property-based")(
    test("diff(a, b).apply(a) == Right(b) for all Json values") {
      check(JsonGen.genJson, JsonGen.genJson) { (source, target) =>
        val patch  = JsonDiffer.diff(source, target)
        val result = patch.apply(source)
        assertTrue(result == Right(target))
      }
    },
    test("diff(a, a).isEmpty for all Json values") {
      check(JsonGen.genJson) { value =>
        val patch = JsonDiffer.diff(value, value)
        assertTrue(patch.isEmpty)
      }
    },
    test("JsonPatch.diff delegates to JsonDiffer.diff") {
      check(JsonGen.genJson, JsonGen.genJson) { (source, target) =>
        val patchFromJsonPatch  = JsonPatch.diff(source, target)
        val patchFromJsonDiffer = JsonDiffer.diff(source, target)
        assertTrue(patchFromJsonPatch == patchFromJsonDiffer)
      }
    }
  ) @@ TestAspect.samples(100)
}
