package zio.blocks.schema.into.validation

import zio.blocks.schema.Into
import zio.test._

object ErrorAccumulationSpec extends ZIOSpecDefault {

  // Test types for narrowing validation
  case class SourceWithMultipleLongs(a: Long, b: Long, c: Long)
  case class TargetWithMultipleInts(a: Int, b: Int, c: Int)

  case class SourceWithFiveFields(f1: Long, f2: Long, f3: Long, f4: Long, f5: Long)
  case class TargetWithFiveInts(f1: Int, f2: Int, f3: Int, f4: Int, f5: Int)

  def spec = suite("ErrorAccumulationSpec")(
    suite("Multiple Field Errors")(
      test("accumulates errors from multiple fields that fail narrowing") {
        val source = SourceWithMultipleLongs(
          a = Long.MaxValue, // Will overflow Int
          b = Long.MinValue, // Will overflow Int
          c = 100L           // Valid
        )
        val into   = Into.derived[SourceWithMultipleLongs, TargetWithMultipleInts]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.exists { error =>
            val errorMessages = error.errors.map(_.message)
            error.errors.size == 2 &&
            errorMessages.exists(msg =>
              msg.contains("converting field") &&
                msg.contains("SourceWithMultipleLongs.a") &&
                msg.contains("TargetWithMultipleInts.a") &&
                msg.contains(Long.MaxValue.toString) &&
                msg.contains("out of range")
            ) &&
            errorMessages.exists(msg =>
              msg.contains("converting field") &&
                msg.contains("SourceWithMultipleLongs.b") &&
                msg.contains("TargetWithMultipleInts.b") &&
                msg.contains(Long.MinValue.toString) &&
                msg.contains("out of range")
            )
          }
        )
      },
      test("accumulates all errors when all fields fail") {
        val source = SourceWithMultipleLongs(
          a = Long.MaxValue,
          b = Long.MinValue,
          c = Long.MaxValue
        )
        val into   = Into.derived[SourceWithMultipleLongs, TargetWithMultipleInts]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.exists { error =>
            val errorMessages = error.errors.map(_.message)
            error.errors.size == 3 &&
            errorMessages.exists(_.contains("SourceWithMultipleLongs.a")) &&
            errorMessages.exists(_.contains("SourceWithMultipleLongs.b")) &&
            errorMessages.exists(_.contains("SourceWithMultipleLongs.c")) &&
            errorMessages.forall(_.contains("TargetWithMultipleInts")) &&
            errorMessages.forall(_.contains("out of range"))
          }
        )
      },
      test("returns Right when all fields succeed") {
        val source = SourceWithMultipleLongs(a = 1L, b = 2L, c = 3L)
        val into   = Into.derived[SourceWithMultipleLongs, TargetWithMultipleInts]
        val result = into.into(source)

        assertTrue(
          result == Right(TargetWithMultipleInts(1, 2, 3))
        )
      },
      test("accumulates errors for 5 fields with correct field identification") {
        val source = SourceWithFiveFields(
          f1 = Long.MaxValue,
          f2 = 100L,
          f3 = Long.MinValue,
          f4 = Long.MaxValue,
          f5 = 200L
        )
        val into   = Into.derived[SourceWithFiveFields, TargetWithFiveInts]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.exists { error =>
            val errorMessages = error.errors.map(_.message)
            error.errors.size == 3 &&
            errorMessages.exists(_.contains("SourceWithFiveFields.f1")) &&
            errorMessages.exists(_.contains("SourceWithFiveFields.f3")) &&
            errorMessages.exists(_.contains("SourceWithFiveFields.f4")) &&
            errorMessages.exists(_.contains("TargetWithFiveInts.f1")) &&
            errorMessages.exists(_.contains("TargetWithFiveInts.f3")) &&
            errorMessages.exists(_.contains("TargetWithFiveInts.f4")) &&
            !errorMessages.exists(_.contains(".f2")) &&
            !errorMessages.exists(_.contains(".f5")) &&
            errorMessages.forall(_.contains("out of range"))
          }
        )
      }
    ),
    suite("Error Message Content")(
      test("error messages contain converting field context with source and target field names") {
        val source = SourceWithMultipleLongs(
          a = Long.MaxValue,
          b = Long.MinValue,
          c = 100L
        )
        val into   = Into.derived[SourceWithMultipleLongs, TargetWithMultipleInts]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.exists { error =>
            val messages = error.errors.map(_.message)
            messages.forall { msg =>
              msg.contains("converting field") &&
              msg.contains("SourceWithMultipleLongs") &&
              msg.contains("→") &&
              msg.contains("TargetWithMultipleInts") &&
              msg.contains("out of range for Int")
            }
          }
        )
      },
      test("error messages contain source and target type names") {
        val source = SourceWithMultipleLongs(
          a = Long.MaxValue,
          b = 100L,
          c = 100L
        )
        val into   = Into.derived[SourceWithMultipleLongs, TargetWithMultipleInts]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.exists { error =>
            val msg = error.message
            msg.contains("SourceWithMultipleLongs.a") &&
            msg.contains("TargetWithMultipleInts.a")
          }
        )
      },
      test("error messages contain the actual overflow values") {
        val overflowValue = Long.MaxValue
        val source        = SourceWithMultipleLongs(
          a = overflowValue,
          b = 100L,
          c = 100L
        )
        val into   = Into.derived[SourceWithMultipleLongs, TargetWithMultipleInts]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.exists { error =>
            val msg = error.message
            msg.contains(overflowValue.toString) &&
            msg.contains(Int.MinValue.toString) &&
            msg.contains(Int.MaxValue.toString)
          }
        )
      }
    ),
    suite("Tuple Error Accumulation")(
      test("accumulates errors in tuple to case class conversion with positional context") {
        val source: (Long, Long, Long) = (Long.MaxValue, Long.MinValue, 100L)
        val into                       = Into.derived[(Long, Long, Long), TargetWithMultipleInts]
        val result                     = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.exists { error =>
            val errorMessages = error.errors.map(_.message)
            error.errors.size == 2 &&
            errorMessages.forall(_.contains("out of range")) &&
            error.message.contains(Long.MaxValue.toString) &&
            error.message.contains(Long.MinValue.toString) &&
            error.message.contains("TargetWithMultipleInts")
          }
        )
      },
      test("accumulates errors in case class to tuple conversion with field context") {
        case class SourceLongs(x: Long, y: Long, z: Long)
        val source = SourceLongs(Long.MaxValue, Long.MinValue, 100L)
        val into   = Into.derived[SourceLongs, (Int, Int, Int)]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.exists { error =>
            val errorMessages = error.errors.map(_.message)
            // Should have exactly 2 errors
            error.errors.size == 2 &&
            // Errors should mention the source fields with type
            errorMessages.exists(_.contains("SourceLongs.x")) &&
            errorMessages.exists(_.contains("SourceLongs.y")) &&
            // Should NOT mention z (it succeeded)
            !errorMessages.exists(_.contains(".z")) &&
            // All should be overflow errors
            errorMessages.forall(_.contains("out of range"))
          }
        )
      },
      test("accumulates all errors in tuple to tuple conversion") {
        val source: (Long, Long, Long) = (Long.MaxValue, Long.MinValue, Long.MaxValue)
        val into                       = Into.derived[(Long, Long, Long), (Int, Int, Int)]
        val result                     = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.exists { error =>
            val errorMessages = error.errors.map(_.message)
            // Should have exactly 3 errors (all elements overflow)
            error.errors.size == 3 &&
            // All should be overflow errors
            errorMessages.forall(_.contains("out of range")) &&
            // Should contain the overflow values
            error.message.contains(Long.MaxValue.toString) &&
            error.message.contains(Long.MinValue.toString)
          }
        )
      }
    ),
    suite("Error Structure")(
      test("all failing field errors are present regardless of order") {
        val source = SourceWithMultipleLongs(
          a = Long.MaxValue, // First failing field
          b = Long.MinValue, // Second failing field
          c = 100L           // Valid
        )
        val into   = Into.derived[SourceWithMultipleLongs, TargetWithMultipleInts]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.exists { error =>
            // Use the combined message which includes all error details
            val combinedMsg = error.message
            // Both error values should be present
            combinedMsg.contains(Long.MaxValue.toString) &&
            combinedMsg.contains(Long.MinValue.toString) &&
            // Both fully qualified field references should be present
            combinedMsg.contains("SourceWithMultipleLongs.a") &&
            combinedMsg.contains("SourceWithMultipleLongs.b") &&
            combinedMsg.contains("TargetWithMultipleInts.a") &&
            combinedMsg.contains("TargetWithMultipleInts.b")
          }
        )
      },
      test("combined error message contains all individual errors") {
        val source = SourceWithMultipleLongs(
          a = Long.MaxValue,
          b = Long.MinValue,
          c = Long.MaxValue
        )
        val into   = Into.derived[SourceWithMultipleLongs, TargetWithMultipleInts]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.exists { error =>
            val combinedMessage = error.message
            // Combined message should contain all overflow values
            combinedMessage.contains(Long.MaxValue.toString) &&
            combinedMessage.contains(Long.MinValue.toString) &&
            // Should contain all fully qualified field references for source
            combinedMessage.contains("SourceWithMultipleLongs.a") &&
            combinedMessage.contains("SourceWithMultipleLongs.b") &&
            combinedMessage.contains("SourceWithMultipleLongs.c") &&
            // Should contain all fully qualified field references for target
            combinedMessage.contains("TargetWithMultipleInts.a") &&
            combinedMessage.contains("TargetWithMultipleInts.b") &&
            combinedMessage.contains("TargetWithMultipleInts.c")
          }
        )
      }
    )
  )
}
