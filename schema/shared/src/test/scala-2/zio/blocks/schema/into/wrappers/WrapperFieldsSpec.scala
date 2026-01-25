package zio.blocks.schema.into.wrappers

import zio.blocks.schema._
import zio.test._

/**
 * Tests for Into/As conversions between case classes where fields are
 * single-field case class (AnyVal wrapper) types.
 *
 * This tests the scenario where:
 *   - Source has primitive field, target has wrapper field (e.g., Long ->
 *     WrappedId)
 *   - Source has wrapper field, target has primitive field (e.g., WrappedId ->
 *     Long)
 *
 * Note: ZIO Prelude Newtype tests are in a separate spec.
 */
object WrapperFieldsSpec extends ZIOSpecDefault {

  // === Single-field case class wrappers ===
  case class WrappedId(value: Long) extends AnyVal
  case class WrappedName(value: String)

  // === Test case classes ===

  // Single-field case class wrapper tests (AnyVal)
  case class RecordWithId(id: Long, label: String)
  case class RecordWithWrappedId(id: WrappedId, label: String)

  // Non-AnyVal single field case class wrapper tests
  case class RecordWithName(name: String, count: Int)
  case class RecordWithWrappedName(name: WrappedName, count: Int)

  // Multiple wrapped fields
  case class RecordWithMultiple(id: Long, name: String, score: Double)
  case class RecordWithMultipleWrapped(id: WrappedId, name: WrappedName, score: Double)

  def spec = suite("WrapperFieldsSpec - Single-field Case Class Fields")(
    suite("AnyVal Single-field Case Class Fields")(
      test("Into: primitive field -> AnyVal wrapper field") {
        val into = Into.derived[RecordWithId, RecordWithWrappedId]

        val raw    = RecordWithId(123L, "test")
        val result = into.into(raw)

        assertTrue(
          result.isRight,
          result.toOption.get.id.value == 123L,
          result.toOption.get.label == "test"
        )
      },
      test("Into: AnyVal wrapper field -> primitive field") {
        val into = Into.derived[RecordWithWrappedId, RecordWithId]

        val wrapped = RecordWithWrappedId(WrappedId(456L), "label")
        val result  = into.into(wrapped)

        assertTrue(
          result.isRight,
          result.toOption.get.id == 456L,
          result.toOption.get.label == "label"
        )
      }
    ),
    suite("Non-AnyVal Single-field Case Class Fields")(
      test("Into: primitive field -> non-AnyVal wrapper field") {
        val into = Into.derived[RecordWithName, RecordWithWrappedName]

        val raw    = RecordWithName("hello", 42)
        val result = into.into(raw)

        assertTrue(
          result.isRight,
          result.toOption.get.name.value == "hello",
          result.toOption.get.count == 42
        )
      },
      test("Into: non-AnyVal wrapper field -> primitive field") {
        val into = Into.derived[RecordWithWrappedName, RecordWithName]

        val wrapped = RecordWithWrappedName(WrappedName("world"), 99)
        val result  = into.into(wrapped)

        assertTrue(
          result.isRight,
          result.toOption.get.name == "world",
          result.toOption.get.count == 99
        )
      }
    ),
    suite("Multiple Wrapped Fields")(
      test("Into: multiple primitive fields -> multiple wrapper fields") {
        val into = Into.derived[RecordWithMultiple, RecordWithMultipleWrapped]

        val raw    = RecordWithMultiple(100L, "multi", 3.14)
        val result = into.into(raw)

        assertTrue(
          result.isRight,
          result.toOption.get.id.value == 100L,
          result.toOption.get.name.value == "multi",
          result.toOption.get.score == 3.14
        )
      },
      test("Into: multiple wrapper fields -> multiple primitive fields") {
        val into = Into.derived[RecordWithMultipleWrapped, RecordWithMultiple]

        val wrapped = RecordWithMultipleWrapped(WrappedId(200L), WrappedName("test"), 2.71)
        val result  = into.into(wrapped)

        assertTrue(
          result.isRight,
          result.toOption.get.id == 200L,
          result.toOption.get.name == "test",
          result.toOption.get.score == 2.71
        )
      }
    ),
    suite("As Round-trips with Single-field Case Class Fields")(
      test("As: primitive <-> AnyVal wrapper fields round-trip") {
        val as = As.derived[RecordWithId, RecordWithWrappedId]

        val original  = RecordWithId(789L, "round-trip")
        val roundTrip = as.into(original).flatMap(as.from)

        assertTrue(
          roundTrip.isRight,
          roundTrip.toOption.get == original
        )
      },
      test("As: wrapper -> primitive -> wrapper round-trip") {
        val as = As.derived[RecordWithId, RecordWithWrappedId]

        val wrapped   = RecordWithWrappedId(WrappedId(999L), "reverse")
        val roundTrip = as.from(wrapped).flatMap(as.into)

        assertTrue(
          roundTrip.isRight,
          roundTrip.toOption.get == wrapped
        )
      },
      test("As: non-AnyVal wrapper fields round-trip") {
        val as = As.derived[RecordWithName, RecordWithWrappedName]

        val original  = RecordWithName("hello", 42)
        val roundTrip = as.into(original).flatMap(as.from)

        assertTrue(
          roundTrip.isRight,
          roundTrip.toOption.get == original
        )
      },
      test("As: multiple wrapper fields round-trip") {
        val as = As.derived[RecordWithMultiple, RecordWithMultipleWrapped]

        val original  = RecordWithMultiple(555L, "multiple", 1.618)
        val roundTrip = as.into(original).flatMap(as.from)

        assertTrue(
          roundTrip.isRight,
          roundTrip.toOption.get == original
        )
      }
    )
  )
}
