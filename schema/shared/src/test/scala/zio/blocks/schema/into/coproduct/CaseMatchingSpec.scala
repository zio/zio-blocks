package zio.blocks.schema.into.coproduct

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for case matching by name in coproduct conversions.
 *
 * Focuses on matching case objects and case classes by their names.
 *
 * Covers:
 *   - Exact name matching for case objects
 *   - Name matching for case classes (with different field types)
 *   - Partial name matches
 */
object CaseMatchingSpec extends ZIOSpecDefault {

  // === Test Data Types ===

  // Case objects with matching names
  sealed trait Direction
  object Direction {
    case object North extends Direction
    case object South extends Direction
    case object East  extends Direction
    case object West  extends Direction
  }

  sealed trait Compass
  object Compass {
    case object North extends Compass
    case object South extends Compass
    case object East  extends Compass
    case object West  extends Compass
  }

  // Case classes with matching names but different field types
  sealed trait CommandV1
  object CommandV1 {
    case class Create(id: String, data: Int) extends CommandV1
    case class Update(id: String, data: Int) extends CommandV1
    case class Delete(id: String)            extends CommandV1
  }

  sealed trait CommandV2
  object CommandV2 {
    case class Create(id: String, data: Long) extends CommandV2
    case class Update(id: String, data: Long) extends CommandV2
    case class Delete(id: String)             extends CommandV2
  }

  // Mixed case objects and case classes with name matching
  sealed trait TrafficLight
  object TrafficLight {
    case object Red                    extends TrafficLight
    case object Yellow                 extends TrafficLight
    case object Green                  extends TrafficLight
    case class Blinking(color: String) extends TrafficLight
  }

  sealed trait Signal
  object Signal {
    case object Red                    extends Signal
    case object Yellow                 extends Signal
    case object Green                  extends Signal
    case class Blinking(color: String) extends Signal
  }

  // Case classes with nested structures
  sealed trait RequestV1
  object RequestV1 {
    case class Get(path: String)                extends RequestV1
    case class Post(path: String, body: String) extends RequestV1
  }

  sealed trait RequestV2
  object RequestV2 {
    case class Get(path: String)                extends RequestV2
    case class Post(path: String, body: String) extends RequestV2
  }

  // For "All Cases Match by Name" test
  sealed trait SourceADTAll
  object SourceADTAll {
    case object A                   extends SourceADTAll
    case object B                   extends SourceADTAll
    case class C(value: Int)        extends SourceADTAll
    case class D(x: String, y: Int) extends SourceADTAll
  }

  sealed trait TargetADTAll
  object TargetADTAll {
    case object A                    extends TargetADTAll
    case object B                    extends TargetADTAll
    case class C(value: Long)        extends TargetADTAll
    case class D(x: String, y: Long) extends TargetADTAll
  }

  // For "Error Handling" test
  sealed trait SourceOverflow
  object SourceOverflow {
    case class Overflow(value: Long) extends SourceOverflow
  }

  sealed trait TargetOverflow
  object TargetOverflow {
    case class Overflow(value: Int) extends TargetOverflow
  }

  // For "Case Sensitivity" test
  sealed trait SourceActive
  object SourceActive {
    case object Active extends SourceActive
  }

  sealed trait TargetActive
  object TargetActive {
    case object Active extends TargetActive
  }

  def spec: Spec[TestEnvironment, Any] = suite("CaseMatchingSpec")(
    suite("Case Object Name Matching")(
      test("matches North to North by name") {
        val direction: Direction = Direction.North
        val result               = Into.derived[Direction, Compass].into(direction)

        assert(result)(isRight(equalTo(Compass.North: Compass)))
      },
      test("matches South to South by name") {
        val direction: Direction = Direction.South
        val result               = Into.derived[Direction, Compass].into(direction)

        assert(result)(isRight(equalTo(Compass.South: Compass)))
      },
      test("matches East to East by name") {
        val direction: Direction = Direction.East
        val result               = Into.derived[Direction, Compass].into(direction)

        assert(result)(isRight(equalTo(Compass.East: Compass)))
      },
      test("matches West to West by name") {
        val direction: Direction = Direction.West
        val result               = Into.derived[Direction, Compass].into(direction)

        assert(result)(isRight(equalTo(Compass.West: Compass)))
      }
    ),
    suite("Case Class Name Matching with Type Coercion")(
      test("matches Create by name with Int to Long coercion") {
        val cmd: CommandV1 = CommandV1.Create("123", 42)
        val result         = Into.derived[CommandV1, CommandV2].into(cmd)

        assert(result)(isRight(equalTo(CommandV2.Create("123", 42L): CommandV2)))
      },
      test("matches Update by name with Int to Long coercion") {
        val cmd: CommandV1 = CommandV1.Update("456", 100)
        val result         = Into.derived[CommandV1, CommandV2].into(cmd)

        assert(result)(isRight(equalTo(CommandV2.Update("456", 100L): CommandV2)))
      },
      test("matches Delete by name (same signature)") {
        val cmd: CommandV1 = CommandV1.Delete("789")
        val result         = Into.derived[CommandV1, CommandV2].into(cmd)

        assert(result)(isRight(equalTo(CommandV2.Delete("789"): CommandV2)))
      }
    ),
    suite("Mixed Case Objects and Case Classes")(
      test("matches Red case object by name") {
        val light: TrafficLight = TrafficLight.Red
        val result              = Into.derived[TrafficLight, Signal].into(light)

        assert(result)(isRight(equalTo(Signal.Red: Signal)))
      },
      test("matches Yellow case object by name") {
        val light: TrafficLight = TrafficLight.Yellow
        val result              = Into.derived[TrafficLight, Signal].into(light)

        assert(result)(isRight(equalTo(Signal.Yellow: Signal)))
      },
      test("matches Green case object by name") {
        val light: TrafficLight = TrafficLight.Green
        val result              = Into.derived[TrafficLight, Signal].into(light)

        assert(result)(isRight(equalTo(Signal.Green: Signal)))
      },
      test("matches Blinking case class by name") {
        val light: TrafficLight = TrafficLight.Blinking("red")
        val result              = Into.derived[TrafficLight, Signal].into(light)

        assert(result)(isRight(equalTo(Signal.Blinking("red"): Signal)))
      }
    ),
    suite("Name Matching with Same Signature")(
      test("matches Get by name") {
        val req: RequestV1 = RequestV1.Get("/api/users")
        val result         = Into.derived[RequestV1, RequestV2].into(req)

        assert(result)(isRight(equalTo(RequestV2.Get("/api/users"): RequestV2)))
      },
      test("matches Post by name") {
        val req: RequestV1 = RequestV1.Post("/api/users", """{"name":"Alice"}""")
        val result         = Into.derived[RequestV1, RequestV2].into(req)

        assert(result)(isRight(equalTo(RequestV2.Post("/api/users", """{"name":"Alice"}"""): RequestV2)))
      }
    ),
    suite("All Cases Match by Name")(
      test("converts all cases when names match") {
        val cases: List[SourceADTAll] = List(
          SourceADTAll.A,
          SourceADTAll.B,
          SourceADTAll.C(42),
          SourceADTAll.D("test", 100)
        )

        val results = cases.map(Into.derived[SourceADTAll, TargetADTAll].into)

        assertTrue(
          results == List(
            Right(TargetADTAll.A: TargetADTAll),
            Right(TargetADTAll.B: TargetADTAll),
            Right(TargetADTAll.C(42L): TargetADTAll),
            Right(TargetADTAll.D("test", 100L): TargetADTAll)
          )
        )
      }
    ),
    suite("Error Handling")(
      test("fails when payload conversion fails despite name match") {
        val source: SourceOverflow = SourceOverflow.Overflow(Long.MaxValue)
        val result                 = Into.derived[SourceOverflow, TargetOverflow].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Case Sensitivity")(
      test("name matching is case-sensitive") {
        // Names must match exactly - case sensitivity matters
        val source: SourceActive = SourceActive.Active
        val result               = Into.derived[SourceActive, TargetActive].into(source)

        assert(result)(isRight(equalTo(TargetActive.Active: TargetActive)))
      }
    )
  )
}
