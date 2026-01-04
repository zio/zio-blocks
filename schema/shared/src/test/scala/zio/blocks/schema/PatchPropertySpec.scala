package zio.blocks.schema

import zio.test._
import zio.test.Assertion._
import DynamicValueGen._

/**
 * Property-based tests for the Patch and DynamicPatch systems.
 *
 * These tests verify:
 *   - The fundamental diff-apply law: diff(a, b).apply(a) == b
 *   - Monoid laws for patch composition
 *   - DeleteField operation correctness
 *   - Error path information in SchemaError
 */
object PatchPropertySpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("PatchPropertySpec")(
    suite("Diff-Apply Law: diff(a, b).apply(a) == b")(
      test("primitive values") {
        check(genPrimitiveValue, genPrimitiveValue) { (prim1, prim2) =>
          val a     = DynamicValue.Primitive(prim1)
          val b     = DynamicValue.Primitive(prim2)
          val patch = DynamicValue.diff(a, b)
          // Use Clobber mode to handle type changes (e.g., Int -> String)
          val result = patch.apply(a, DynamicPatch.PatchMode.Clobber)
          assert(result)(isRight(equalTo(b)))
        }
      },
      test("record values") {
        check(genRecord, genRecord) { (a, b) =>
          val patch = DynamicValue.diff(a, b)
          // Use Clobber mode to handle field additions
          val result = patch.apply(a, DynamicPatch.PatchMode.Clobber)
          assert(result)(isRight(equalTo(b)))
        }
      },
      test("variant values") {
        check(genVariant, genVariant) { (a, b) =>
          val patch = DynamicValue.diff(a, b)
          // Use Clobber mode to handle case changes
          val result = patch.apply(a, DynamicPatch.PatchMode.Clobber)
          assert(result)(isRight(equalTo(b)))
        }
      },
      test("sequence values") {
        check(genSequence, genSequence) { (a, b) =>
          val patch  = DynamicValue.diff(a, b)
          val result = patch.apply(a, DynamicPatch.PatchMode.Clobber)
          assert(result)(isRight(equalTo(b)))
        }
      },
      test("map values") {
        check(genMap, genMap) { (a, b) =>
          val patch  = DynamicValue.diff(a, b)
          val result = patch.apply(a, DynamicPatch.PatchMode.Clobber)
          assert(result)(isRight(equalTo(b)))
        }
      },
      test("any dynamic values") {
        check(genDynamicValue, genDynamicValue) { (a, b) =>
          val patch = DynamicValue.diff(a, b)
          // Use Clobber mode which allows creating missing fields and changing types
          val result = patch.apply(a, DynamicPatch.PatchMode.Clobber)
          assert(result)(isRight(equalTo(b)))
        }
      },
      test("diff of identical values produces empty patch") {
        check(genDynamicValue) { value =>
          val patch = DynamicValue.diff(value, value)
          assertTrue(patch.ops.isEmpty)
        }
      }
    ),
    suite("DynamicPatch Monoid Laws")(
      test("left identity: empty ++ patch == patch") {
        check(genDynamicValue) { value =>
          val patch = DynamicPatch.set(value)
          assertTrue((DynamicPatch.empty ++ patch) == patch)
        }
      },
      test("right identity: patch ++ empty == patch") {
        check(genDynamicValue) { value =>
          val patch = DynamicPatch.set(value)
          assertTrue((patch ++ DynamicPatch.empty) == patch)
        }
      },
      test("associativity: (p1 ++ p2) ++ p3 == p1 ++ (p2 ++ p3)") {
        check(genDynamicValue, genDynamicValue, genDynamicValue) { (v1, v2, v3) =>
          val p1 = DynamicPatch.single(DynamicPatch.Op(DynamicOptic.root.field("a"), DynamicPatch.Operation.Set(v1)))
          val p2 = DynamicPatch.single(DynamicPatch.Op(DynamicOptic.root.field("b"), DynamicPatch.Operation.Set(v2)))
          val p3 = DynamicPatch.single(DynamicPatch.Op(DynamicOptic.root.field("c"), DynamicPatch.Operation.Set(v3)))
          assertTrue(((p1 ++ p2) ++ p3) == (p1 ++ (p2 ++ p3)))
        }
      },
      test("empty patch application is identity") {
        check(genDynamicValue) { value =>
          val result = DynamicPatch.empty.apply(value, DynamicPatch.PatchMode.Strict)
          assert(result)(isRight(equalTo(value)))
        }
      }
    ),
    suite("DeleteField Operation")(
      test("removes field from record") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30)),
            "city" -> DynamicValue.Primitive(PrimitiveValue.String("NYC"))
          )
        )
        val patch  = DynamicPatch.deleteField("age")
        val result = patch.apply(record, DynamicPatch.PatchMode.Strict)
        assert(result)(
          isRight(
            equalTo(
              DynamicValue.Record(
                Vector(
                  "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
                  "city" -> DynamicValue.Primitive(PrimitiveValue.String("NYC"))
                )
              )
            )
          )
        )
      },
      test("removes first field correctly") {
        val record = DynamicValue.Record(
          Vector(
            "a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            "b" -> DynamicValue.Primitive(PrimitiveValue.Int(2)),
            "c" -> DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val patch  = DynamicPatch.deleteField("a")
        val result = patch.apply(record, DynamicPatch.PatchMode.Strict)
        assert(result)(
          isRight(
            equalTo(
              DynamicValue.Record(
                Vector(
                  "b" -> DynamicValue.Primitive(PrimitiveValue.Int(2)),
                  "c" -> DynamicValue.Primitive(PrimitiveValue.Int(3))
                )
              )
            )
          )
        )
      },
      test("removes last field correctly") {
        val record = DynamicValue.Record(
          Vector(
            "a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            "b" -> DynamicValue.Primitive(PrimitiveValue.Int(2)),
            "c" -> DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val patch  = DynamicPatch.deleteField("c")
        val result = patch.apply(record, DynamicPatch.PatchMode.Strict)
        assert(result)(
          isRight(
            equalTo(
              DynamicValue.Record(
                Vector(
                  "a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
                  "b" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
                )
              )
            )
          )
        )
      },
      test("strict mode fails when field not found") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
          )
        )
        val patch  = DynamicPatch.deleteField("missing")
        val result = patch.apply(record, DynamicPatch.PatchMode.Strict)
        assertTrue(result.isLeft) &&
        assertTrue(result.swap.toOption.get.message.contains("not found"))
      },
      test("lenient mode ignores missing field") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
          )
        )
        val patch  = DynamicPatch.deleteField("missing")
        val result = patch.apply(record, DynamicPatch.PatchMode.Lenient)
        assert(result)(isRight(equalTo(record)))
      },
      test("clobber mode ignores missing field") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
          )
        )
        val patch  = DynamicPatch.deleteField("missing")
        val result = patch.apply(record, DynamicPatch.PatchMode.Clobber)
        assert(result)(isRight(equalTo(record)))
      },
      test("fails on non-record value") {
        val primitive = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val patch     = DynamicPatch.deleteField("field")
        val result    = patch.apply(primitive, DynamicPatch.PatchMode.Strict)
        assertTrue(result.isLeft) &&
        assertTrue(result.swap.toOption.get.message.contains("Type mismatch"))
      },
      test("diffRecords emits DeleteField for removed fields") {
        val oldRecord = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30)),
            "city" -> DynamicValue.Primitive(PrimitiveValue.String("NYC"))
          )
        )
        val newRecord = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "city" -> DynamicValue.Primitive(PrimitiveValue.String("NYC"))
          )
        )
        val patch = DynamicValue.diff(oldRecord, newRecord)
        // Verify the patch contains a DeleteField operation
        val hasDeleteFieldOp = patch.ops.exists {
          case DynamicPatch.Op(_, DynamicPatch.Operation.DeleteField("age")) => true
          case _                                                             => false
        }
        assertTrue(hasDeleteFieldOp) &&
        assert(patch.apply(oldRecord, DynamicPatch.PatchMode.Strict))(isRight(equalTo(newRecord)))
      },
      test("diffRecords handles multiple field deletions") {
        val oldRecord = DynamicValue.Record(
          Vector(
            "a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            "b" -> DynamicValue.Primitive(PrimitiveValue.Int(2)),
            "c" -> DynamicValue.Primitive(PrimitiveValue.Int(3)),
            "d" -> DynamicValue.Primitive(PrimitiveValue.Int(4))
          )
        )
        val newRecord = DynamicValue.Record(
          Vector(
            "b" -> DynamicValue.Primitive(PrimitiveValue.Int(2)),
            "d" -> DynamicValue.Primitive(PrimitiveValue.Int(4))
          )
        )
        val patch = DynamicValue.diff(oldRecord, newRecord)
        assert(patch.apply(oldRecord, DynamicPatch.PatchMode.Strict))(isRight(equalTo(newRecord)))
      }
    ),
    suite("Error Path Information")(
      test("nested field error includes full path") {
        val record = DynamicValue.Record(
          Vector(
            "outer" -> DynamicValue.Record(
              Vector(
                "inner" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
              )
            )
          )
        )
        val patch = DynamicPatch.single(
          DynamicPatch.Op(
            DynamicOptic.root.field("outer").field("missing"),
            DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(100)))
          )
        )
        val result = patch.apply(record, DynamicPatch.PatchMode.Strict)
        assertTrue(result.isLeft) &&
        assertTrue(result.swap.toOption.get.message.contains("missing")) &&
        assertTrue(result.swap.toOption.get.message.contains("outer"))
      },
      test("variant case mismatch includes path") {
        val variant = DynamicValue.Variant("Left", DynamicValue.Primitive(PrimitiveValue.Int(42)))
        val patch   = DynamicPatch.single(
          DynamicPatch.Op(
            DynamicOptic.root.caseOf("Right"),
            DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String("value")))
          )
        )
        val result = patch.apply(variant, DynamicPatch.PatchMode.Strict)
        assertTrue(result.isLeft) &&
        assertTrue(result.swap.toOption.get.message.contains("Case mismatch"))
      },
      test("deeply nested error preserves full optic path") {
        val record = DynamicValue.Record(
          Vector(
            "level1" -> DynamicValue.Record(
              Vector(
                "level2" -> DynamicValue.Record(
                  Vector(
                    "level3" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
                  )
                )
              )
            )
          )
        )
        val patch = DynamicPatch.single(
          DynamicPatch.Op(
            DynamicOptic.root.field("level1").field("level2").field("missing"),
            DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(100)))
          )
        )
        val result = patch.apply(record, DynamicPatch.PatchMode.Strict)
        assertTrue(result.isLeft)
      }
    ),
    suite("Typed Patch via Schema")(
      test("typed patch set with Lens") {
        case class Person(name: String, age: Int)
        object Person extends CompanionOptics[Person] {
          implicit val schema: Schema[Person] = Schema.derived
          val name: Lens[Person, String]      = optic(_.name)
          val age: Lens[Person, Int]          = optic(_.age)
        }

        val patch   = Patch.set(Person.name, "Bob")
        val person1 = Person("Alice", 30)
        val result  = patch.applyOrFail(person1)

        assert(result)(isRight(equalTo(Person("Bob", 30))))
      },
      test("typed patch addInt delta") {
        case class Counter(value: Int)
        object Counter extends CompanionOptics[Counter] {
          implicit val schema: Schema[Counter] = Schema.derived
          val value: Lens[Counter, Int]        = optic(_.value)
        }

        val patch   = Patch.addInt(Counter.value, 10)
        val counter = Counter(5)
        val result  = patch.applyOrFail(counter)

        assert(result)(isRight(equalTo(Counter(15))))
      },
      test("typed patch composition") {
        case class Person(name: String, age: Int)
        object Person extends CompanionOptics[Person] {
          implicit val schema: Schema[Person] = Schema.derived
          val name: Lens[Person, String]      = optic(_.name)
          val age: Lens[Person, Int]          = optic(_.age)
        }

        val patch  = Patch.set(Person.name, "Bob") ++ Patch.addInt(Person.age, 1)
        val person = Person("Alice", 30)
        val result = patch.applyOrFail(person)

        assert(result)(isRight(equalTo(Person("Bob", 31))))
      },
      test("empty patch is identity") {
        case class Data(value: Int)
        implicit val schema: Schema[Data] = Schema.derived

        val patch = Patch.empty[Data]
        val data  = Data(42)
        assertTrue(patch(data) == data) &&
        assertTrue(patch.isEmpty)
      }
    )
  )
}
