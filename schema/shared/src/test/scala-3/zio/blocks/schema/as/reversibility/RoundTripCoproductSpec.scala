package zio.blocks.schema.as.reversibility

import zio.test._
import zio.blocks.schema._

// Enums must be at package level to avoid compiler bugs
enum Status {
  case Active
  case Inactive
  case Pending
}

enum StatusDTO {
  case Active
  case Inactive
  case Pending
}

// Sealed traits at package level
sealed trait Color
object Color {
  case object Red extends Color
  case object Blue extends Color
  case object Green extends Color
}

sealed trait ColorDTO
object ColorDTO {
  case object Red extends ColorDTO
  case object Blue extends ColorDTO
  case object Green extends ColorDTO
}

sealed trait Result
object Result {
  case class Success(value: Int) extends Result
  case class Failure(message: String) extends Result
  case object Pending extends Result
}

sealed trait ResultDTO
object ResultDTO {
  case class Success(value: Int) extends ResultDTO
  case class Failure(message: String) extends ResultDTO
  case object Pending extends ResultDTO
}

object RoundTripCoproductSpec extends ZIOSpecDefault {

  def spec = suite("RoundTripCoproductSpec")(
    suite("Round Trip for Identical Enums")(
      test("should round trip Status -> StatusDTO -> Status for all cases") {

        val as = As.derived[Status, StatusDTO]

        // Test all cases
        val activeResult = as.into(Status.Active).flatMap(as.from)
        val inactiveResult = as.into(Status.Inactive).flatMap(as.from)
        val pendingResult = as.into(Status.Pending).flatMap(as.from)

        assertTrue(
          activeResult == Right(Status.Active) &&
            inactiveResult == Right(Status.Inactive) &&
            pendingResult == Right(Status.Pending)
        )
      },
      test("should round trip StatusDTO -> Status -> StatusDTO for all cases") {

        val as = As.derived[Status, StatusDTO]

        // Test all cases in reverse direction
        val activeResult = as.from(StatusDTO.Active).flatMap(as.into)
        val inactiveResult = as.from(StatusDTO.Inactive).flatMap(as.into)
        val pendingResult = as.from(StatusDTO.Pending).flatMap(as.into)

        assertTrue(
          activeResult == Right(StatusDTO.Active) &&
            inactiveResult == Right(StatusDTO.Inactive) &&
            pendingResult == Right(StatusDTO.Pending)
        )
      }
    ),
    suite("Round Trip for Sealed Traits with Case Objects")(
      test("should round trip Color -> ColorDTO -> Color") {

        val as = As.derived[Color, ColorDTO]

        val redResult = as.into(Color.Red).flatMap(as.from)
        val blueResult = as.into(Color.Blue).flatMap(as.from)
        val greenResult = as.into(Color.Green).flatMap(as.from)

        assertTrue(
          redResult == Right(Color.Red) &&
            blueResult == Right(Color.Blue) &&
            greenResult == Right(Color.Green)
        )
      }
    ),
    suite("Round Trip for Sealed Traits with Case Classes")(
      test("should round trip Result -> ResultDTO -> Result") {

        val as = As.derived[Result, ResultDTO]

        val successResult = as.into(Result.Success(42)).flatMap(as.from)
        val failureResult = as.into(Result.Failure("Error")).flatMap(as.from)
        val pendingResult = as.into(Result.Pending).flatMap(as.from)

        assertTrue(
          successResult == Right(Result.Success(42)) &&
            failureResult == Right(Result.Failure("Error")) &&
            pendingResult == Right(Result.Pending)
        )
      }
    )
  )
}

