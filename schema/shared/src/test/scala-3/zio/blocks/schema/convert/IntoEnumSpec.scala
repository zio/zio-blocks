package zio.blocks.schema.convert

import zio.test._
import zio.test.Assertion._

object IntoEnumSpec extends ZIOSpecDefault {

  // Scala 3 enum types
  enum Status {
    case Active, Inactive, Suspended
  }

  enum State {
    case Active, Inactive, Suspended
  }

  def spec: Spec[TestEnvironment, Any] = suite("IntoEnumSpec")(
    suite("Enum to Enum (Scala 3)")(
      test("maps enum values by matching names - Active") {
        val status: Status = Status.Active
        val result = Into.derived[Status, State].into(status)
        
        assert(result)(isRight(equalTo(State.Active: State)))
      },
      test("maps Inactive status to Inactive state") {
        val status: Status = Status.Inactive
        val result = Into.derived[Status, State].into(status)
        
        assert(result)(isRight(equalTo(State.Inactive: State)))
      },
      test("maps Suspended status to Suspended state") {
        val status: Status = Status.Suspended
        val result = Into.derived[Status, State].into(status)
        
        assert(result)(isRight(equalTo(State.Suspended: State)))
      }
    )
  )
}

