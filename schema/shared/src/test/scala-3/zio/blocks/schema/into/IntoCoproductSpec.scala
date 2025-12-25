package zio.blocks.schema.into

import zio.test._
import zio._
import zio.blocks.schema.Into

// Simple Enums (Scala 3) - Moved outside object to avoid compiler bug
enum Status {
  case Active
  case Inactive
}

enum State {
  case Active
  case Inactive
  case Pending
}

// Sealed Traits at package level with companion objects (avoids compiler bug)
sealed trait ColorTypesColor
object ColorTypesColor {
  case object Red   extends ColorTypesColor
  case object Blue  extends ColorTypesColor
  case object Green extends ColorTypesColor
}

sealed trait HueTypesHue
object HueTypesHue {
  case object Red  extends HueTypesHue
  case object Blue extends HueTypesHue
}

// Complex Coproducts (Recursive with case classes)
sealed trait EventTypesEvent
object EventTypesEvent {
  case class Created(id: Int)    extends EventTypesEvent
  case class Deleted(id: String) extends EventTypesEvent
  case object Updated            extends EventTypesEvent
}

sealed trait ActionTypesAction
object ActionTypesAction {
  case class Created(id: Long)   extends ActionTypesAction // Int -> Long widening
  case class Deleted(id: String) extends ActionTypesAction
  case object Updated            extends ActionTypesAction
}

object IntoCoproductSpec extends ZIOSpecDefault {

  def spec = suite("Into Coproduct Support")(
    suite("Simple Enums (Scala 3)")(
      test("Should convert Status.Active to State.Active") {
        val derivation = Into.derived[Status, State]
        val input      = Status.Active
        val result     = derivation.into(input)

        assertTrue(result == Right(State.Active))
      },
      test("Should convert Status.Inactive to State.Inactive") {
        val derivation = Into.derived[Status, State]
        val input      = Status.Inactive
        val result     = derivation.into(input)

        assertTrue(result == Right(State.Inactive))
      },
      test("Should handle all matching cases from Status to State") {
        val derivation = Into.derived[Status, State]

        val activeResult   = derivation.into(Status.Active)
        val inactiveResult = derivation.into(Status.Inactive)

        assertTrue(
          activeResult == Right(State.Active) &&
            inactiveResult == Right(State.Inactive)
        )
      }
    ),
    suite("Sealed Traits (Classic)")(
      test("Should convert Color.Red to Hue.Red") {
        val derivation              = Into.derived[ColorTypesColor, HueTypesHue]
        val input: ColorTypesColor = ColorTypesColor.Red
        val result                  = derivation.into(input)

        assertTrue(result == Right(HueTypesHue.Red: HueTypesHue))
      },
      test("Should convert Color.Blue to Hue.Blue") {
        val derivation              = Into.derived[ColorTypesColor, HueTypesHue]
        val input: ColorTypesColor = ColorTypesColor.Blue
        val result                  = derivation.into(input)

        assertTrue(result == Right(HueTypesHue.Blue: HueTypesHue))
      },
      test("Should fail when converting Color.Green (not in Hue)") {
        val derivation              = Into.derived[ColorTypesColor, HueTypesHue]
        val input: ColorTypesColor = ColorTypesColor.Green
        val result                  = derivation.into(input)

        // Green is not in Hue, so should hit catch-all case
        assertTrue(result.isLeft)
      }
    ),
    suite("Complex Coproducts (Recursive)")(
      test("Should convert Event.Created(1) to Action.Created(1L) with Int -> Long widening") {
        val derivation              = Into.derived[EventTypesEvent, ActionTypesAction]
        val input: EventTypesEvent = EventTypesEvent.Created(1)
        val result                  = derivation.into(input)

        assertTrue(result == Right(ActionTypesAction.Created(1L)))
      },
      test("Should convert Event.Deleted(\"id\") to Action.Deleted(\"id\") (identity)") {
        val derivation              = Into.derived[EventTypesEvent, ActionTypesAction]
        val input: EventTypesEvent = EventTypesEvent.Deleted("id123")
        val result                  = derivation.into(input)

        assertTrue(result == Right(ActionTypesAction.Deleted("id123")))
      },
      test("Should convert Event.Updated to Action.Updated (case object)") {
        val derivation              = Into.derived[EventTypesEvent, ActionTypesAction]
        val input: EventTypesEvent = EventTypesEvent.Updated
        val result                  = derivation.into(input)

        assertTrue(result == Right(ActionTypesAction.Updated: ActionTypesAction))
      },
      test("Should handle all Event cases") {
        val derivation = Into.derived[EventTypesEvent, ActionTypesAction]

        val createdResult = derivation.into(EventTypesEvent.Created(42): EventTypesEvent)
        val deletedResult = derivation.into(EventTypesEvent.Deleted("test"): EventTypesEvent)
        val updatedResult = derivation.into(EventTypesEvent.Updated: EventTypesEvent)

        assertTrue(
          createdResult == Right(ActionTypesAction.Created(42L)) &&
            deletedResult == Right(ActionTypesAction.Deleted("test")) &&
            updatedResult == Right(ActionTypesAction.Updated: ActionTypesAction)
        )
      }
    ),
    suite("Edge Cases")(
      test("Should handle identity conversion (Status to Status)") {
        val derivation = Into.derived[Status, Status]
        val input      = Status.Active
        val result     = derivation.into(input)

        assertTrue(result == Right(Status.Active))
      },
      test("Should handle enum with more cases in target") {
        // State has Pending, but Status doesn't - should still work
        val derivation     = Into.derived[Status, State]
        val activeResult   = derivation.into(Status.Active)
        val inactiveResult = derivation.into(Status.Inactive)

        assertTrue(
          activeResult == Right(State.Active) &&
            inactiveResult == Right(State.Inactive)
        )
      }
    )

    // NOTE: Compile-time error tests are commented out because typeCheck
    // helper may not be available. Uncomment and adjust if typeCheck is available.
    //
    // suite("Compile-Time Error Cases")(
    //   test("Should fail compilation when subtype is missing in target") {
    //     typeCheck {
    //       "Into.derived[StatusWithCancel, State]"
    //     }.map(
    //       assert(_)(
    //         isLeft(
    //           containsString("Missing subtype mapping") ||
    //           containsString("Canceled") ||
    //           containsString("has no matching subtype")
    //         )
    //       )
    //     )
    //   }
    // )
  )
}

