package zio.blocks.schema.patch

import zio.blocks.schema._
import zio.blocks.schema.json.JsonTestUtils._
import zio.blocks.schema.patch.PatchSchemas._
import zio.test._

/**
 * Tests for the new PatchPath types added in Phase 2:
 *   - PatchPath.Case for variant/prism navigation
 *   - PatchPath.Elements for traversal over sequences
 *   - PatchPath.Wrapped for newtype wrappers
 *
 * These tests operate at the DynamicPatch level to verify the underlying
 * navigation logic works correctly.
 */
object PatchPathExtensionsSpec extends ZIOSpecDefault {

  // Helper to create DynamicValue representations
  def stringPrimitive(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def intPrimitive(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  // Sample variant (simulating sealed trait Animal with Dog/Cat cases)
  val dogVariant: DynamicValue = DynamicValue.Variant(
    "Dog",
    DynamicValue.Record(
      Vector(
        "name"  -> stringPrimitive("Rex"),
        "breed" -> stringPrimitive("German Shepherd")
      )
    )
  )

  val catVariant: DynamicValue = DynamicValue.Variant(
    "Cat",
    DynamicValue.Record(
      Vector(
        "name"  -> stringPrimitive("Whiskers"),
        "lives" -> intPrimitive(9)
      )
    )
  )

  // Sample sequence
  val personSequence: DynamicValue = DynamicValue.Sequence(
    Vector(
      DynamicValue.Record(
        Vector(
          "name" -> stringPrimitive("Alice"),
          "age"  -> intPrimitive(30)
        )
      ),
      DynamicValue.Record(
        Vector(
          "name" -> stringPrimitive("Bob"),
          "age"  -> intPrimitive(25)
        )
      ),
      DynamicValue.Record(
        Vector(
          "name" -> stringPrimitive("Charlie"),
          "age"  -> intPrimitive(35)
        )
      )
    )
  )

  // Sample team with nested sequence
  val teamRecord: DynamicValue = DynamicValue.Record(
    Vector(
      "name"    -> stringPrimitive("Engineering"),
      "members" -> personSequence
    )
  )

  def spec: Spec[TestEnvironment, Any] = suite("PatchPathExtensionsSpec")(
    suite("PatchPath.Case")(
      test("navigates into matching variant case") {
        val path  = Vector(PatchPath.Case("Dog"), PatchPath.Field("name"))
        val op    = DynamicPatchOp(path, Operation.Set(stringPrimitive("Max")))
        val patch = DynamicPatch(Vector(op))

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
        val path  = Vector(PatchPath.Case("Dog"), PatchPath.Field("name"))
        val op    = DynamicPatchOp(path, Operation.Set(stringPrimitive("Max")))
        val patch = DynamicPatch(Vector(op))

        val result = patch(catVariant, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("skips operation when case doesn't match in Lenient mode") {
        val path  = Vector(PatchPath.Case("Dog"), PatchPath.Field("name"))
        val op    = DynamicPatchOp(path, Operation.Set(stringPrimitive("Max")))
        val patch = DynamicPatch(Vector(op))

        val result = patch(catVariant, PatchMode.Lenient)
        assertTrue(result == Right(catVariant))
      },
      test("can replace entire variant content") {
        val path          = Vector(PatchPath.Case("Dog"))
        val newDogContent = DynamicValue.Record(
          Vector(
            "name"  -> stringPrimitive("Buddy"),
            "breed" -> stringPrimitive("Labrador")
          )
        )
        val op    = DynamicPatchOp(path, Operation.Set(newDogContent))
        val patch = DynamicPatch(Vector(op))

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
            Vector(
              "animal" -> dogVariant
            )
          )
        )

        val path = Vector(
          PatchPath.Case("Container"),
          PatchPath.Field("animal"),
          PatchPath.Case("Dog"),
          PatchPath.Field("name")
        )
        val op    = DynamicPatchOp(path, Operation.Set(stringPrimitive("Max")))
        val patch = DynamicPatch(Vector(op))

        val result = patch(nestedVariant, PatchMode.Strict)
        assertTrue(result.isRight)
      }
    ),
    suite("PatchPath.Elements")(
      test("applies operation to all elements") {
        val path  = Vector(PatchPath.Elements, PatchPath.Field("name"))
        val op    = DynamicPatchOp(path, Operation.Set(stringPrimitive("Anonymous")))
        val patch = DynamicPatch(Vector(op))

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
        val emptySequence = DynamicValue.Sequence(Vector.empty)
        val path          = Vector(PatchPath.Elements, PatchPath.Field("name"))
        val op            = DynamicPatchOp(path, Operation.Set(stringPrimitive("Anonymous")))
        val patch         = DynamicPatch(Vector(op))

        val result = patch(emptySequence, PatchMode.Strict)
        result match {
          case Left(error) =>
            assertTrue(error.message.contains("encountered an empty sequence"))
          case Right(_) =>
            assertTrue(false) // Strict mode should fail
        }
      },
      test("navigates through record to sequence elements") {
        val path  = Vector(PatchPath.Field("members"), PatchPath.Elements, PatchPath.Field("name"))
        val op    = DynamicPatchOp(path, Operation.Set(stringPrimitive("Anonymous")))
        val patch = DynamicPatch(Vector(op))

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
        val path  = Vector(PatchPath.Elements, PatchPath.Field("age"))
        val op    = DynamicPatchOp(path, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(1)))
        val patch = DynamicPatch(Vector(op))

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
            assertTrue(allAges == Vector(Some(31), Some(26), Some(36)))
          case _ =>
            assertTrue(false) // Unexpected result
        }
      },
      test("elements in Strict mode fails if any element fails") {
        // Create a sequence where one element is missing the field
        val mixedSequence = DynamicValue.Sequence(
          Vector(
            DynamicValue.Record(Vector("name" -> stringPrimitive("Alice"))),
            DynamicValue.Record(Vector("different" -> stringPrimitive("Bob"))) // missing "name"
          )
        )

        val path  = Vector(PatchPath.Elements, PatchPath.Field("name"))
        val op    = DynamicPatchOp(path, Operation.Set(stringPrimitive("Anonymous")))
        val patch = DynamicPatch(Vector(op))

        val result = patch(mixedSequence, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("elements in Lenient mode keeps original on element failure") {
        val mixedSequence = DynamicValue.Sequence(
          Vector(
            DynamicValue.Record(Vector("name" -> stringPrimitive("Alice"))),
            DynamicValue.Record(Vector("different" -> stringPrimitive("Bob")))
          )
        )

        val path  = Vector(PatchPath.Elements, PatchPath.Field("name"))
        val op    = DynamicPatchOp(path, Operation.Set(stringPrimitive("Anonymous")))
        val patch = DynamicPatch(Vector(op))

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
    suite("PatchPath.Wrapped")(
      test("navigates through wrapper") {
        // Simulate a wrapped value (wrappers are transparent in DynamicValue)
        val wrappedValue = intPrimitive(42)

        val path  = Vector(PatchPath.Wrapped)
        val op    = DynamicPatchOp(path, Operation.Set(intPrimitive(100)))
        val patch = DynamicPatch(Vector(op))

        val result = patch(wrappedValue, PatchMode.Strict)
        assertTrue(result == Right(intPrimitive(100)))
      },
      test("wrapped with delta operation") {
        val wrappedValue = intPrimitive(42)

        val path  = Vector(PatchPath.Wrapped)
        val op    = DynamicPatchOp(path, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(8)))
        val patch = DynamicPatch(Vector(op))

        val result = patch(wrappedValue, PatchMode.Strict)
        assertTrue(result == Right(intPrimitive(50)))
      },
      test("nested wrapped navigation") {
        // Record with a wrapped field
        val record = DynamicValue.Record(
          Vector(
            "value" -> intPrimitive(42)
          )
        )

        val path  = Vector(PatchPath.Field("value"), PatchPath.Wrapped)
        val op    = DynamicPatchOp(path, Operation.Set(intPrimitive(100)))
        val patch = DynamicPatch(Vector(op))

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
    suite("PatchPath serialization")(
      test("Case serializes correctly") {
        roundTrip(
          PatchPath.Case("Dog"): PatchPath,
          """{"Case":{"name":"Dog"}}"""
        )
      },
      test("Elements serializes correctly") {
        roundTrip(
          PatchPath.Elements: PatchPath,
          """{"Elements":{}}"""
        )
      },
      test("Wrapped serializes correctly") {
        roundTrip(
          PatchPath.Wrapped: PatchPath,
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
            Vector(
              "items" -> personSequence
            )
          )
        )

        val path = Vector(
          PatchPath.Case("ItemList"),
          PatchPath.Field("items"),
          PatchPath.Elements,
          PatchPath.Field("name")
        )
        val op    = DynamicPatchOp(path, Operation.Set(stringPrimitive("Updated")))
        val patch = DynamicPatch(Vector(op))

        val result = patch(listVariant, PatchMode.Strict)
        assertTrue(result.isRight)
      },
      test("Field + Elements + Case") {
        // Record with sequence of variants
        val animalList = DynamicValue.Record(
          Vector(
            "animals" -> DynamicValue.Sequence(Vector(dogVariant, catVariant, dogVariant))
          )
        )

        val path = Vector(
          PatchPath.Field("animals"),
          PatchPath.Elements,
          PatchPath.Case("Dog"),
          PatchPath.Field("name")
        )
        val op    = DynamicPatchOp(path, Operation.Set(stringPrimitive("Max")))
        val patch = DynamicPatch(Vector(op))

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
