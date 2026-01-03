package zio.blocks.schema.into

import zio.blocks.schema.Into
import zio.test.*
import zio.test.Assertion.*

object IntoEnumSpec extends ZIOSpecDefault {

  enum Status {
    case Active, Inactive, Suspended
  }

  enum State {
    case Active, Inactive, Suspended
  }

  def spec: Spec[TestEnvironment, Any] = suite("IntoScala3Spec")(
    suite("Enum to Enum (Scala 3)")(
      test("maps enum values by matching names - Active") {
        val status: Status = Status.Active
        val result         = Into.derived[Status, State].into(status)

        assert(result)(isRight(equalTo(State.Active: State)))
      },
      test("maps Inactive status to Inactive state") {
        val status: Status = Status.Inactive
        val result         = Into.derived[Status, State].into(status)

        assert(result)(isRight(equalTo(State.Inactive: State)))
      },
      test("maps Suspended status to Suspended state") {
        val status: Status = Status.Suspended
        val result         = Into.derived[Status, State].into(status)

        assert(result)(isRight(equalTo(State.Suspended: State)))
      }
    )
  )
}
