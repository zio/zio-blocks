package zio.blocks.schema.into.validation

import zio.blocks.schema.{Into, SchemaError}
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
        val into = Into.derived[SourceWithMultipleLongs, TargetWithMultipleInts]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.toOption.exists { error =>
            // Should have exactly 2 errors (for 'a' and 'b' fields)
            error.errors.size == 2 &&
            // Check that both overflow values are mentioned in error messages
            error.message.contains(Long.MaxValue.toString) &&
            error.message.contains(Long.MinValue.toString)
          }
        )
      },
      test("accumulates all errors when all fields fail") {
        val source = SourceWithMultipleLongs(
          a = Long.MaxValue,
          b = Long.MinValue,
          c = Long.MaxValue
        )
        val into = Into.derived[SourceWithMultipleLongs, TargetWithMultipleInts]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.toOption.exists { error =>
            // All three fields should have errors
            error.errors.size == 3
          }
        )
      },
      test("returns Right when all fields succeed") {
        val source = SourceWithMultipleLongs(a = 1L, b = 2L, c = 3L)
        val into = Into.derived[SourceWithMultipleLongs, TargetWithMultipleInts]
        val result = into.into(source)

        assertTrue(
          result == Right(TargetWithMultipleInts(1, 2, 3))
        )
      },
      test("accumulates errors for 5 fields") {
        val source = SourceWithFiveFields(
          f1 = Long.MaxValue,  // overflow
          f2 = 100L,           // ok
          f3 = Long.MinValue,  // overflow
          f4 = Long.MaxValue,  // overflow
          f5 = 200L            // ok
        )
        val into = Into.derived[SourceWithFiveFields, TargetWithFiveInts]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.toOption.exists { error =>
            // Should have exactly 3 errors for f1, f3, f4
            error.errors.size == 3
          }
        )
      }
    ),
    suite("Error Message Content")(
      test("error messages identify which fields failed") {
        val source = SourceWithMultipleLongs(
          a = Long.MaxValue,
          b = Long.MinValue,
          c = 100L
        )
        val into = Into.derived[SourceWithMultipleLongs, TargetWithMultipleInts]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.toOption.exists { error =>
            val messages = error.errors.map(_.message)
            // Each error should contain field context and overflow information
            messages.forall { msg =>
              msg.contains("converting field") && msg.contains("out of range")
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
        val into = Into.derived[SourceWithMultipleLongs, TargetWithMultipleInts]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.toOption.exists { error =>
            val msg = error.message
            // Should contain type name context
            msg.contains("SourceWithMultipleLongs") && msg.contains("TargetWithMultipleInts")
          }
        )
      }
    ),
    suite("Tuple Error Accumulation")(
      test("accumulates errors in tuple to case class conversion") {
        val source: (Long, Long, Long) = (Long.MaxValue, Long.MinValue, 100L)
        val into = Into.derived[(Long, Long, Long), TargetWithMultipleInts]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.toOption.exists { error =>
            // Should have exactly 2 errors
            error.errors.size == 2
          }
        )
      },
      test("accumulates errors in case class to tuple conversion") {
        case class SourceLongs(x: Long, y: Long, z: Long)
        val source = SourceLongs(Long.MaxValue, Long.MinValue, 100L)
        val into = Into.derived[SourceLongs, (Int, Int, Int)]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.toOption.exists { error =>
            // Should have exactly 2 errors
            error.errors.size == 2
          }
        )
      },
      test("accumulates errors in tuple to tuple conversion") {
        val source: (Long, Long, Long) = (Long.MaxValue, Long.MinValue, Long.MaxValue)
        val into = Into.derived[(Long, Long, Long), (Int, Int, Int)]
        val result = into.into(source)

        assertTrue(
          result.isLeft,
          result.left.toOption.exists { error =>
            // Should have exactly 3 errors
            error.errors.size == 3
          }
        )
      }
    )
  )
}
