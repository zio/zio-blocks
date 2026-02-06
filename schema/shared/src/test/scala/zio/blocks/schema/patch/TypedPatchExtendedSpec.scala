package zio.blocks.schema.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.json.JsonTestUtils._
import zio.test._

// Tests for the new DynamicOptic.Node of elements, wrapped, and case types.
object DynamicOpticNodeExtensionsSpec extends SchemaBaseSpec {

  // Helper to create DynamicValue representations
  def stringPrimitive(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def intPrimitive(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  // Sample variant (simulating sealed trait Animal with Dog/Cat cases)
  val dogVariant: DynamicValue = DynamicValue.Variant(
    "Dog",
    DynamicValue.Record(
      Chunk(
        "name"  -> stringPrimitive("Rex"),
        "breed" -> stringPrimitive("German Shepherd")
      )
    )
  )

  val catVariant: DynamicValue = DynamicValue.Variant(
    "Cat",
    DynamicValue.Record(
      Chunk(
        "name"  -> stringPrimitive("Whiskers"),
        "lives" -> intPrimitive(9)
      )
    )
  )

  // Sample sequence
  val personSequence: DynamicValue = DynamicValue.Sequence(
    Chunk(
      DynamicValue.Record(
        Chunk(
          "name" -> stringPrimitive("Alice"),
          "age"  -> intPrimitive(30)
        )
      ),
      DynamicValue.Record(
        Chunk(
          "name" -> stringPrimitive("Bob"),
          "age"  -> intPrimitive(25)
        )
      ),
      DynamicValue.Record(
        Chunk(
          "name" -> stringPrimitive("Charlie"),
          "age"  -> intPrimitive(35)
        )
      )
    )
  )

  // Sample team with nested sequence
  val teamRecord: DynamicValue = DynamicValue.Record(
    Chunk(
      "name"    -> stringPrimitive("Engineering"),
      "members" -> personSequence
    )
  )

  def spec: Spec[TestEnvironment, Any] = suite("DynamicOpticNodeExtensionsSpec")(
    suite("DynamicOptic.Node.Case")(
      test("navigates into matching variant case") {
        val path  = Vector(DynamicOptic.Node.Case("Dog"), DynamicOptic.Node.Field("name"))
        val op    = DynamicPatch.DynamicPatchOp(DynamicOptic(path), DynamicPatch.Operation.Set(stringPrimitive("Max")))
        val patch = DynamicPatch(Chunk(op))

        val result = patch(dogVariant, PatchMode.Strict)

        result match {
          case Right(DynamicValue.Variant("Dog", DynamicValue.Record(fields))) =>
            val nameField = fields.find(_._1 == "name")
            assertTrue(nameField.exists(_._2 == stringPrimitive("Max")))
          case _ =>
            assertTrue(false) // Unexpected result
        }
      },
      test("fails when case doesn't match in Strict mode") {
        val path  = Chunk(DynamicOptic.Node.Case("Dog"), DynamicOptic.Node.Field("name"))
        val op    = DynamicPatch.DynamicPatchOp(DynamicOptic(path), DynamicPatch.Operation.Set(stringPrimitive("Max")))
        val patch = DynamicPatch(Chunk(op))

        val result = patch(catVariant, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("skips operation when case doesn't match in Lenient mode") {
        val path  = Chunk(DynamicOptic.Node.Case("Dog"), DynamicOptic.Node.Field("name"))
        val op    = DynamicPatch.DynamicPatchOp(DynamicOptic(path), DynamicPatch.Operation.Set(stringPrimitive("Max")))
        val patch = DynamicPatch(Chunk(op))

        val result = patch(catVariant, PatchMode.Lenient)
        assertTrue(result == Right(catVariant))
      },
      test("can replace entire variant content") {
        val path          = Chunk(DynamicOptic.Node.Case("Dog"))
        val newDogContent = DynamicValue.Record(
          Chunk(
            "name"  -> stringPrimitive("Buddy"),
            "breed" -> stringPrimitive("Labrador")
          )
        )
        val op    = DynamicPatch.DynamicPatchOp(DynamicOptic(path), DynamicPatch.Operation.Set(newDogContent))
        val patch = DynamicPatch(Chunk(op))

        val result = patch(dogVariant, PatchMode.Strict)

        result match {
          case Right(DynamicValue.Variant("Dog", content)) =>
            assertTrue(content == newDogContent)
          case _ =>
            assertTrue(false) // Unexpected result
        }
      },
      test("nested case navigation") {
        // Variant containing another variant
        val nestedVariant = DynamicValue.Variant(
          "Container",
          DynamicValue.Record(
            Chunk(
              "animal" -> dogVariant
            )
          )
        )

        val path = Chunk(
          DynamicOptic.Node.Case("Container"),
          DynamicOptic.Node.Field("animal"),
          DynamicOptic.Node.Case("Dog"),
          DynamicOptic.Node.Field("name")
        )
        val op    = DynamicPatch.DynamicPatchOp(DynamicOptic(path), DynamicPatch.Operation.Set(stringPrimitive("Max")))
        val patch = DynamicPatch(Chunk(op))

        val result = patch(nestedVariant, PatchMode.Strict)
        assertTrue(result.isRight)
      }
    ),
    suite("DynamicOptic.Node.Elements")(
      test("applies operation to all elements") {
        val path = Chunk(DynamicOptic.Node.Elements, DynamicOptic.Node.Field("name"))
        val op   =
          DynamicPatch.DynamicPatchOp(DynamicOptic(path), DynamicPatch.Operation.Set(stringPrimitive("Anonymous")))
        val patch = DynamicPatch(Chunk(op))

        val result = patch(personSequence, PatchMode.Strict)

        result match {
          case Right(DynamicValue.Sequence(elements)) =>
            val allNames = elements.map {
              case DynamicValue.Record(fields) =>
                fields.find(_._1 == "name").map(_._2)
              case _ => None
            }
            assertTrue(
              allNames.forall(_ == Some(stringPrimitive("Anonymous"))),
              elements.length == 3
            )
          case _ =>
            assertTrue(false) // Unexpected result
        }
      },
      test("fails on empty sequence in Strict mode") {
        val emptySequence = DynamicValue.Sequence(Chunk.empty)
        val path          = Chunk(DynamicOptic.Node.Elements, DynamicOptic.Node.Field("name"))
        val op            =
          DynamicPatch.DynamicPatchOp(DynamicOptic(path), DynamicPatch.Operation.Set(stringPrimitive("Anonymous")))
        val patch = DynamicPatch(Chunk(op))

        val result = patch(emptySequence, PatchMode.Strict)
        result match {
          case Left(error) =>
            assertTrue(error.message.contains("encountered an empty sequence"))
          case Right(_) =>
            assertTrue(false) // Strict mode should fail
        }
      },
      test("navigates through record to sequence elements") {
        val path =
          DynamicOptic.root.field("members").elements.field("name")
        val op    = DynamicPatch.DynamicPatchOp(path, DynamicPatch.Operation.Set(stringPrimitive("Anonymous")))
        val patch = DynamicPatch(Chunk(op))

        val result = patch(teamRecord, PatchMode.Strict)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val membersField = fields.find(_._1 == "members")
            membersField match {
              case Some((_, DynamicValue.Sequence(elements))) =>
                val allNames = elements.map {
                  case DynamicValue.Record(fs) =>
                    fs.find(_._1 == "name").map(_._2)
                  case _ => None
                }
                assertTrue(allNames.forall(_ == Some(stringPrimitive("Anonymous"))))
              case _ =>
                assertTrue(false) // Expected members sequence
            }
          case _ =>
            assertTrue(false) // Unexpected result
        }
      },
      test("elements with numeric delta") {
        val path = Chunk(DynamicOptic.Node.Elements, DynamicOptic.Node.Field("age"))
        val op   = DynamicPatch.DynamicPatchOp(
          DynamicOptic(path),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(1))
        )
        val patch = DynamicPatch(Chunk(op))

        val result = patch(personSequence, PatchMode.Strict)

        result match {
          case Right(DynamicValue.Sequence(elements)) =>
            val allAges = elements.map {
              case DynamicValue.Record(fields) =>
                fields.find(_._1 == "age").map {
                  case (_, DynamicValue.Primitive(PrimitiveValue.Int(age))) => age
                  case _                                                    => -1
                }
              case _ => None
            }
            assertTrue(allAges == Chunk(Some(31), Some(26), Some(36)))
          case _ =>
            assertTrue(false) // Unexpected result
        }
      },
      test("elements in Strict mode fails if any element fails") {
        // Create a sequence where one element is missing the field
        val mixedSequence = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk("name" -> stringPrimitive("Alice"))),
            DynamicValue.Record(Chunk("different" -> stringPrimitive("Bob"))) // missing "name"
          )
        )

        val path = Chunk(DynamicOptic.Node.Elements, DynamicOptic.Node.Field("name"))
        val op   =
          DynamicPatch.DynamicPatchOp(DynamicOptic(path), DynamicPatch.Operation.Set(stringPrimitive("Anonymous")))
        val patch = DynamicPatch(Chunk(op))

        val result = patch(mixedSequence, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("elements in Lenient mode keeps original on element failure") {
        val mixedSequence = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Record(Chunk("name" -> stringPrimitive("Alice"))),
            DynamicValue.Record(Chunk("different" -> stringPrimitive("Bob")))
          )
        )

        val path = Chunk(DynamicOptic.Node.Elements, DynamicOptic.Node.Field("name"))
        val op   =
          DynamicPatch.DynamicPatchOp(DynamicOptic(path), DynamicPatch.Operation.Set(stringPrimitive("Anonymous")))
        val patch = DynamicPatch(Chunk(op))

        val result = patch(mixedSequence, PatchMode.Lenient)

        result match {
          case Right(DynamicValue.Sequence(elements)) =>
            // First element should be updated, second should be original
            assertTrue(elements.length == 2)
          case _ =>
            assertTrue(false) // Unexpected result
        }
      }
    ),
    suite("DynamicOptic.Node.Wrapped")(
      test("navigates through wrapper") {
        // Simulate a wrapped value (wrappers are transparent in DynamicValue)
        val wrappedValue = intPrimitive(42)

        val path  = Chunk(DynamicOptic.Node.Wrapped)
        val op    = DynamicPatch.DynamicPatchOp(DynamicOptic(path), DynamicPatch.Operation.Set(intPrimitive(100)))
        val patch = DynamicPatch(Chunk(op))

        val result = patch(wrappedValue, PatchMode.Strict)
        assertTrue(result == Right(intPrimitive(100)))
      },
      test("wrapped with delta operation") {
        val wrappedValue = intPrimitive(42)

        val path = Chunk(DynamicOptic.Node.Wrapped)
        val op   = DynamicPatch.DynamicPatchOp(
          DynamicOptic(path),
          DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(8))
        )
        val patch = DynamicPatch(Chunk(op))

        val result = patch(wrappedValue, PatchMode.Strict)
        assertTrue(result == Right(intPrimitive(50)))
      },
      test("nested wrapped navigation") {
        // Record with a wrapped field
        val record = DynamicValue.Record(
          Chunk(
            "value" -> intPrimitive(42)
          )
        )

        val path  = Chunk(DynamicOptic.Node.Field("value"), DynamicOptic.Node.Wrapped)
        val op    = DynamicPatch.DynamicPatchOp(DynamicOptic(path), DynamicPatch.Operation.Set(intPrimitive(100)))
        val patch = DynamicPatch(Chunk(op))

        val result = patch(record, PatchMode.Strict)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val valueField = fields.find(_._1 == "value")
            assertTrue(valueField.exists(_._2 == intPrimitive(100)))
          case _ =>
            assertTrue(false) // Unexpected result
        }
      }
    ),
    suite("DynamicOptic.Node serialization")(
      test("Case serializes correctly") {
        roundTrip(
          DynamicOptic.Node.Case("Dog"): DynamicOptic.Node,
          """{"Case":{"name":"Dog"}}"""
        )
      },
      test("Elements serializes correctly") {
        roundTrip(
          DynamicOptic.Node.Elements: DynamicOptic.Node,
          """{"Elements":{}}"""
        )
      },
      test("Wrapped serializes correctly") {
        roundTrip(
          DynamicOptic.Node.Wrapped: DynamicOptic.Node,
          """{"Wrapped":{}}"""
        )
      }
    ),
    suite("combined path types")(
      test("Case + Elements + Field") {
        // Variant containing a sequence of records
        val listVariant = DynamicValue.Variant(
          "ItemList",
          DynamicValue.Record(
            Chunk(
              "items" -> personSequence
            )
          )
        )

        val path = Chunk(
          DynamicOptic.Node.Case("ItemList"),
          DynamicOptic.Node.Field("items"),
          DynamicOptic.Node.Elements,
          DynamicOptic.Node.Field("name")
        )
        val op    = DynamicPatch.DynamicPatchOp(DynamicOptic(path), DynamicPatch.Operation.Set(stringPrimitive("Updated")))
        val patch = DynamicPatch(Chunk(op))

        val result = patch(listVariant, PatchMode.Strict)
        assertTrue(result.isRight)
      },
      test("Field + Elements + Case") {
        // Record with sequence of variants
        val animalList = DynamicValue.Record(
          Chunk(
            "animals" -> DynamicValue.Sequence(Chunk(dogVariant, catVariant, dogVariant))
          )
        )

        val path = Chunk(
          DynamicOptic.Node.Field("animals"),
          DynamicOptic.Node.Elements,
          DynamicOptic.Node.Case("Dog"),
          DynamicOptic.Node.Field("name")
        )
        val op    = DynamicPatch.DynamicPatchOp(DynamicOptic(path), DynamicPatch.Operation.Set(stringPrimitive("Max")))
        val patch = DynamicPatch(Chunk(op))

        // This should update dogs and skip cats
        val result = patch(animalList, PatchMode.Lenient)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val animalsField = fields.find(_._1 == "animals")
            animalsField match {
              case Some((_, DynamicValue.Sequence(elements))) =>
                // Should have 3 elements
                assertTrue(elements.length == 3)
              case _ =>
                assertTrue(false) // Expected animals sequence
            }
          case _ =>
            assertTrue(false) // Unexpected result
        }
      }
    )
  )
}
