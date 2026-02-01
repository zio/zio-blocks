package zio.blocks.schema.migration

import zio.test._

/**
 * Additional coverage tests for compile-time migration utilities. Tests
 * runtime-accessible portions of FieldTracker and CompileTimeValidator.
 */
object FieldTrackerSpec extends ZIOSpecDefault {

  def spec = suite("FieldTrackerSpec")(
    suite("TrackedFields")(
      test("empty creates empty TrackedFields") {
        val tracked = FieldTracker.TrackedFields.empty
        assertTrue(
          tracked.handledFromSource.isEmpty &&
            tracked.providedToTarget.isEmpty &&
            tracked.renamedFields.isEmpty &&
            tracked.droppedFields.isEmpty &&
            tracked.addedFields.isEmpty
        )
      },
      test("handleField adds to handledFromSource") {
        val tracked = FieldTracker.TrackedFields.empty.handleField("name")
        assertTrue(tracked.handledFromSource.contains("name"))
      },
      test("handleField multiple fields") {
        val tracked = FieldTracker.TrackedFields.empty
          .handleField("a")
          .handleField("b")
          .handleField("c")
        assertTrue(tracked.handledFromSource.size == 3)
      },
      test("provideField adds to providedToTarget") {
        val tracked = FieldTracker.TrackedFields.empty.provideField("email")
        assertTrue(tracked.providedToTarget.contains("email"))
      },
      test("provideField multiple fields") {
        val tracked = FieldTracker.TrackedFields.empty
          .provideField("x")
          .provideField("y")
          .provideField("z")
        assertTrue(tracked.providedToTarget.size == 3)
      },
      test("renameField updates all tracking sets") {
        val tracked = FieldTracker.TrackedFields.empty.renameField("oldName", "newName")
        assertTrue(
          tracked.renamedFields == Map("oldName" -> "newName") &&
            tracked.handledFromSource.contains("oldName") &&
            tracked.providedToTarget.contains("newName")
        )
      },
      test("renameField multiple renames") {
        val tracked = FieldTracker.TrackedFields.empty
          .renameField("a", "x")
          .renameField("b", "y")
        assertTrue(tracked.renamedFields.size == 2)
      },
      test("dropField updates handledFromSource and droppedFields") {
        val tracked = FieldTracker.TrackedFields.empty.dropField("removedField")
        assertTrue(
          tracked.handledFromSource.contains("removedField") &&
            tracked.droppedFields.contains("removedField")
        )
      },
      test("dropField multiple fields") {
        val tracked = FieldTracker.TrackedFields.empty
          .dropField("a")
          .dropField("b")
        assertTrue(tracked.droppedFields.size == 2)
      },
      test("addField updates providedToTarget and addedFields") {
        val tracked = FieldTracker.TrackedFields.empty.addField("newField")
        assertTrue(
          tracked.providedToTarget.contains("newField") &&
            tracked.addedFields.contains("newField")
        )
      },
      test("addField multiple fields") {
        val tracked = FieldTracker.TrackedFields.empty
          .addField("x")
          .addField("y")
        assertTrue(tracked.addedFields.size == 2)
      },
      test("isComplete returns true when all source and target fields covered") {
        val tracked = FieldTracker.TrackedFields.empty
          .handleField("a")
          .handleField("b")
          .provideField("x")
          .provideField("y")
        assertTrue(tracked.isComplete(Set("a", "b"), Set("x", "y")))
      },
      test("isComplete returns false when missing source fields") {
        val tracked = FieldTracker.TrackedFields.empty
          .handleField("a")
          .provideField("x")
          .provideField("y")
        assertTrue(!tracked.isComplete(Set("a", "b"), Set("x", "y")))
      },
      test("isComplete returns false when missing target fields") {
        val tracked = FieldTracker.TrackedFields.empty
          .handleField("a")
          .handleField("b")
          .provideField("x")
        assertTrue(!tracked.isComplete(Set("a", "b"), Set("x", "y")))
      },
      test("missingFromSource returns fields not handled") {
        val tracked = FieldTracker.TrackedFields.empty
          .handleField("a")
        val missing = tracked.missingFromSource(Set("a", "b", "c"))
        assertTrue(missing == Set("b", "c"))
      },
      test("missingFromSource returns empty when all handled") {
        val tracked = FieldTracker.TrackedFields.empty
          .handleField("a")
          .handleField("b")
        val missing = tracked.missingFromSource(Set("a", "b"))
        assertTrue(missing.isEmpty)
      },
      test("missingFromTarget returns fields not provided") {
        val tracked = FieldTracker.TrackedFields.empty
          .provideField("x")
        val missing = tracked.missingFromTarget(Set("x", "y", "z"))
        assertTrue(missing == Set("y", "z"))
      },
      test("missingFromTarget returns empty when all provided") {
        val tracked = FieldTracker.TrackedFields.empty
          .provideField("x")
          .provideField("y")
        val missing = tracked.missingFromTarget(Set("x", "y"))
        assertTrue(missing.isEmpty)
      },
      test("complex multi-operation tracking") {
        val tracked = FieldTracker.TrackedFields.empty
          .handleField("existingA")
          .handleField("existingB")
          .renameField("oldC", "newC")
          .dropField("removedD")
          .addField("addedE")
          .provideField("existingA")
          .provideField("existingB")

        assertTrue(
          tracked.handledFromSource.size == 4 &&
            tracked.providedToTarget.size == 4 &&
            tracked.renamedFields.size == 1 &&
            tracked.droppedFields.size == 1 &&
            tracked.addedFields.size == 1
        )
      }
    ),
    suite("FieldTracker macros")(
      test("fieldsOf extracts case class fields") {
        case class Person(name: String, age: Int)
        val fields = FieldTracker.fieldsOf[Person]
        assertTrue(fields.contains("name") && fields.contains("age"))
      },
      test("fieldsOf returns empty for primitive types") {
        val fields = FieldTracker.fieldsOf[Int]
        assertTrue(fields.isEmpty)
      },
      test("isStructural returns false for case class") {
        case class Simple(x: Int)
        assertTrue(!FieldTracker.isStructural[Simple])
      },
      test("nameOf returns type name") {
        case class TestType(x: Int)
        val name = FieldTracker.nameOf[TestType]
        assertTrue(name.contains("TestType"))
      }
    )
  )
}
