package zio.blocks.schema.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._
import java.time._

object PrimitiveDeltaSpec extends SchemaBaseSpec {

  // Test data types
  case class Counter(
    value: Int,
    longValue: Long,
    doubleValue: Double,
    floatValue: Float,
    shortValue: Short,
    byteValue: Byte
  )
  object Counter extends CompanionOptics[Counter] {
    implicit val schema: Schema[Counter]   = Schema.derived
    val value: Lens[Counter, Int]          = optic(_.value)
    val longValue: Lens[Counter, Long]     = optic(_.longValue)
    val doubleValue: Lens[Counter, Double] = optic(_.doubleValue)
    val floatValue: Lens[Counter, Float]   = optic(_.floatValue)
    val shortValue: Lens[Counter, Short]   = optic(_.shortValue)
    val byteValue: Lens[Counter, Byte]     = optic(_.byteValue)
  }

  case class BigNumbers(bigInt: BigInt, bigDecimal: BigDecimal)
  object BigNumbers extends CompanionOptics[BigNumbers] {
    implicit val schema: Schema[BigNumbers]      = Schema.derived
    val bigInt: Lens[BigNumbers, BigInt]         = optic(_.bigInt)
    val bigDecimal: Lens[BigNumbers, BigDecimal] = optic(_.bigDecimal)
  }

  case class TimeData(
    instant: Instant,
    duration: Duration,
    localDate: LocalDate,
    localDateTime: LocalDateTime,
    period: Period
  )
  object TimeData extends CompanionOptics[TimeData] {
    implicit val schema: Schema[TimeData]            = Schema.derived
    val instant: Lens[TimeData, Instant]             = optic(_.instant)
    val duration: Lens[TimeData, Duration]           = optic(_.duration)
    val localDate: Lens[TimeData, LocalDate]         = optic(_.localDate)
    val localDateTime: Lens[TimeData, LocalDateTime] = optic(_.localDateTime)
    val period: Lens[TimeData, Period]               = optic(_.period)
  }

  case class Document(title: String, content: String)
  object Document extends CompanionOptics[Document] {
    implicit val schema: Schema[Document] = Schema.derived
    val title: Lens[Document, String]     = optic(_.title)
    val content: Lens[Document, String]   = optic(_.content)
  }

  def spec: Spec[TestEnvironment, Any] = suite("PrimitiveDeltaSpec")(
    numericDeltaTests,
    bigNumberDeltaTests,
    temporalDeltaTests,
    stringEditTests,
    edgeCaseTests
  )

  val numericDeltaTests = suite("Numeric Delta Operations")(
    suite("Patch.increment (Int)")(
      test("increments positive value") {
        val counter = Counter(10, 0L, 0.0, 0.0f, 0, 0)
        val patch   = Patch.increment(Counter.value, 5)
        val result  = patch(counter, PatchMode.Strict)
        assertTrue(result == Right(Counter(15, 0L, 0.0, 0.0f, 0, 0)))
      },
      test("decrements with negative delta") {
        val counter = Counter(10, 0L, 0.0, 0.0f, 0, 0)
        val patch   = Patch.increment(Counter.value, -3)
        val result  = patch(counter, PatchMode.Strict)
        assertTrue(result == Right(Counter(7, 0L, 0.0, 0.0f, 0, 0)))
      },
      test("increments from zero") {
        val counter = Counter(0, 0L, 0.0, 0.0f, 0, 0)
        val patch   = Patch.increment(Counter.value, 1)
        val result  = patch(counter, PatchMode.Strict)
        assertTrue(result == Right(Counter(1, 0L, 0.0, 0.0f, 0, 0)))
      },
      test("increments to negative") {
        val counter = Counter(5, 0L, 0.0, 0.0f, 0, 0)
        val patch   = Patch.increment(Counter.value, -10)
        val result  = patch(counter, PatchMode.Strict)
        assertTrue(result == Right(Counter(-5, 0L, 0.0, 0.0f, 0, 0)))
      }
    ),
    suite("Patch.increment (Long)")(
      test("increments long value") {
        val counter = Counter(0, 1000000000L, 0.0, 0.0f, 0, 0)
        val patch   = Patch.increment(Counter.longValue, 500L)
        val result  = patch(counter, PatchMode.Strict)
        assertTrue(result == Right(Counter(0, 1000000500L, 0.0, 0.0f, 0, 0)))
      },
      test("decrements long value") {
        val counter = Counter(0, 1000L, 0.0, 0.0f, 0, 0)
        val patch   = Patch.increment(Counter.longValue, -200L)
        val result  = patch(counter, PatchMode.Strict)
        assertTrue(result == Right(Counter(0, 800L, 0.0, 0.0f, 0, 0)))
      }
    ),
    suite("Patch.increment (Double)")(
      test("increments double value") {
        val counter = Counter(0, 0L, 10.5, 0.0f, 0, 0)
        val patch   = Patch.increment(Counter.doubleValue, 2.3)
        val result  = patch(counter, PatchMode.Strict)
        assertTrue(result.map(_.doubleValue).exists(v => math.abs(v - 12.8) < 0.0001))
      },
      test("decrements double value") {
        val counter = Counter(0, 0L, 10.0, 0.0f, 0, 0)
        val patch   = Patch.increment(Counter.doubleValue, -3.5)
        val result  = patch(counter, PatchMode.Strict)
        assertTrue(result.map(_.doubleValue).exists(v => math.abs(v - 6.5) < 0.0001))
      }
    ),
    suite("Patch.increment (Float)")(
      test("increments float value") {
        val counter = Counter(0, 0L, 0.0, 5.5f, 0, 0)
        val patch   = Patch.increment(Counter.floatValue, 1.5f)
        val result  = patch(counter, PatchMode.Strict)
        assertTrue(result.map(_.floatValue).exists(v => math.abs(v - 7.0f) < 0.0001f))
      }
    ),
    suite("Patch.increment (Short)")(
      test("increments short value") {
        val counter = Counter(0, 0L, 0.0, 0.0f, 10, 0)
        val patch   = Patch.increment(Counter.shortValue, 5.toShort)
        val result  = patch(counter, PatchMode.Strict)
        assertTrue(result.exists(_.shortValue == 15.toShort))
      },
      test("decrements short value") {
        val counter = Counter(0, 0L, 0.0, 0.0f, 20, 0)
        val patch   = Patch.increment(Counter.shortValue, (-5).toShort)
        val result  = patch(counter, PatchMode.Strict)
        assertTrue(result.exists(_.shortValue == 15.toShort))
      }
    ),
    suite("Patch.increment (Byte)")(
      test("increments byte value") {
        val counter = Counter(0, 0L, 0.0, 0.0f, 0, 10)
        val patch   = Patch.increment(Counter.byteValue, 5.toByte)
        val result  = patch(counter, PatchMode.Strict)
        assertTrue(result.exists(_.byteValue == 15.toByte))
      },
      test("decrements byte value") {
        val counter = Counter(0, 0L, 0.0, 0.0f, 0, 20)
        val patch   = Patch.increment(Counter.byteValue, (-5).toByte)
        val result  = patch(counter, PatchMode.Strict)
        assertTrue(result.exists(_.byteValue == 15.toByte))
      }
    ),
    suite("Composition")(
      test("composes multiple numeric deltas") {
        val counter = Counter(10, 0L, 0.0, 0.0f, 0, 0)
        val patch   = Patch.increment(Counter.value, 5) ++
          Patch.increment(Counter.value, 3) ++
          Patch.increment(Counter.value, -2)
        val result = patch(counter, PatchMode.Strict)
        assertTrue(result == Right(Counter(16, 0L, 0.0, 0.0f, 0, 0)))
      }
    )
  )

  val bigNumberDeltaTests = suite("Big Number Delta Operations")(
    suite("Patch.increment (BigInt)")(
      test("increments BigInt") {
        val nums   = BigNumbers(BigInt(100), BigDecimal(0))
        val patch  = Patch.increment(BigNumbers.bigInt, BigInt(50))
        val result = patch(nums, PatchMode.Strict)
        assertTrue(result == Right(BigNumbers(BigInt(150), BigDecimal(0))))
      },
      test("decrements BigInt") {
        val nums   = BigNumbers(BigInt(100), BigDecimal(0))
        val patch  = Patch.increment(BigNumbers.bigInt, BigInt(-30))
        val result = patch(nums, PatchMode.Strict)
        assertTrue(result == Right(BigNumbers(BigInt(70), BigDecimal(0))))
      },
      test("handles very large BigInt") {
        val large  = BigInt("99999999999999999999999999999999")
        val nums   = BigNumbers(large, BigDecimal(0))
        val patch  = Patch.increment(BigNumbers.bigInt, BigInt(1))
        val result = patch(nums, PatchMode.Strict)
        assertTrue(result.exists(_.bigInt == large + 1))
      }
    ),
    suite("Patch.increment (BigDecimal)")(
      test("increments BigDecimal") {
        val nums   = BigNumbers(BigInt(0), BigDecimal("123.456"))
        val patch  = Patch.increment(BigNumbers.bigDecimal, BigDecimal("10.5"))
        val result = patch(nums, PatchMode.Strict)
        assertTrue(result == Right(BigNumbers(BigInt(0), BigDecimal("133.956"))))
      },
      test("decrements BigDecimal") {
        val nums   = BigNumbers(BigInt(0), BigDecimal("100.0"))
        val patch  = Patch.increment(BigNumbers.bigDecimal, BigDecimal("-25.5"))
        val result = patch(nums, PatchMode.Strict)
        assertTrue(result == Right(BigNumbers(BigInt(0), BigDecimal("74.5"))))
      }
    )
  )

  val temporalDeltaTests = suite("Temporal Delta Operations")(
    suite("Patch.addDuration (Instant)")(
      test("adds duration to instant") {
        val instant  = Instant.parse("2024-01-01T00:00:00Z")
        val timeData = TimeData(instant, Duration.ZERO, LocalDate.MIN, LocalDateTime.MIN, Period.ZERO)
        val patch    = Patch.addDuration(TimeData.instant, Duration.ofHours(2))
        val result   = patch(timeData, PatchMode.Strict)
        assertTrue(result.exists(_.instant == Instant.parse("2024-01-01T02:00:00Z")))
      },
      test("subtracts duration from instant") {
        val instant  = Instant.parse("2024-01-01T10:00:00Z")
        val timeData = TimeData(instant, Duration.ZERO, LocalDate.MIN, LocalDateTime.MIN, Period.ZERO)
        val patch    = Patch.addDuration(TimeData.instant, Duration.ofHours(-5))
        val result   = patch(timeData, PatchMode.Strict)
        assertTrue(result.exists(_.instant == Instant.parse("2024-01-01T05:00:00Z")))
      }
    ),
    suite("Patch.addDuration (Duration)")(
      test("adds duration to duration") {
        val duration = Duration.ofMinutes(30)
        val timeData = TimeData(Instant.MIN, duration, LocalDate.MIN, LocalDateTime.MIN, Period.ZERO)
        val patch    = Patch.addDuration(TimeData.duration, Duration.ofMinutes(15))
        val result   = patch(timeData, PatchMode.Strict)
        assertTrue(result.exists(_.duration == Duration.ofMinutes(45)))
      }
    ),
    suite("Patch.addPeriod (LocalDate)")(
      test("adds period to local date") {
        val date     = LocalDate.of(2024, 1, 1)
        val timeData = TimeData(Instant.MIN, Duration.ZERO, date, LocalDateTime.MIN, Period.ZERO)
        val patch    = Patch.addPeriod(TimeData.localDate, Period.ofDays(10))
        val result   = patch(timeData, PatchMode.Strict)
        assertTrue(result.exists(_.localDate == LocalDate.of(2024, 1, 11)))
      },
      test("adds months to local date") {
        val date     = LocalDate.of(2024, 1, 15)
        val timeData = TimeData(Instant.MIN, Duration.ZERO, date, LocalDateTime.MIN, Period.ZERO)
        val patch    = Patch.addPeriod(TimeData.localDate, Period.ofMonths(2))
        val result   = patch(timeData, PatchMode.Strict)
        assertTrue(result.exists(_.localDate == LocalDate.of(2024, 3, 15)))
      },
      test("subtracts period from local date") {
        val date     = LocalDate.of(2024, 1, 10)
        val timeData = TimeData(Instant.MIN, Duration.ZERO, date, LocalDateTime.MIN, Period.ZERO)
        val patch    = Patch.addPeriod(TimeData.localDate, Period.ofDays(-5))
        val result   = patch(timeData, PatchMode.Strict)
        assertTrue(result.exists(_.localDate == LocalDate.of(2024, 1, 5)))
      }
    ),
    suite("Patch.addPeriodAndDuration (LocalDateTime)")(
      test("adds period and duration to local date time") {
        val dateTime = LocalDateTime.of(2024, 1, 1, 10, 0)
        val timeData = TimeData(Instant.MIN, Duration.ZERO, LocalDate.MIN, dateTime, Period.ZERO)
        val patch    = Patch.addPeriodAndDuration(
          TimeData.localDateTime,
          Period.ofDays(1),
          Duration.ofHours(2)
        )
        val result = patch(timeData, PatchMode.Strict)
        assertTrue(result.exists(_.localDateTime == LocalDateTime.of(2024, 1, 2, 12, 0)))
      }
    ),
    suite("Patch.addPeriod (Period)")(
      test("adds period to period") {
        val period   = Period.ofDays(5)
        val timeData = TimeData(Instant.MIN, Duration.ZERO, LocalDate.MIN, LocalDateTime.MIN, period)
        val patch    = Patch.addPeriod(TimeData.period, Period.ofDays(3))
        val result   = patch(timeData, PatchMode.Strict)
        assertTrue(result.exists(_.period == Period.ofDays(8)))
      }
    )
  )

  val stringEditTests = suite("String Edit Operations")(
    suite("Patch.editString")(
      test("inserts text at position") {
        val doc    = Document("Title", "Hello world")
        val patch  = Patch.editString(Document.content, Chunk(DynamicPatch.StringOp.Insert(6, "beautiful ")))
        val result = patch(doc, PatchMode.Strict)
        assertTrue(result == Right(Document("Title", "Hello beautiful world")))
      },
      test("deletes text") {
        val doc    = Document("Title", "Hello beautiful world")
        val patch  = Patch.editString(Document.content, Chunk(DynamicPatch.StringOp.Delete(6, 10)))
        val result = patch(doc, PatchMode.Strict)
        assertTrue(result == Right(Document("Title", "Hello world")))
      },
      test("applies multiple operations in sequence") {
        val doc   = Document("Title", "Hello world")
        val patch = Patch.editString(
          Document.content,
          Chunk(
            DynamicPatch.StringOp.Insert(5, " there"),
            DynamicPatch.StringOp.Delete(0, 5) // Delete "Hello"
          )
        )
        val result = patch(doc, PatchMode.Strict)
        // After insert: "Hello there world"
        // After delete: " there world"
        assertTrue(result == Right(Document("Title", " there world")))
      },
      test("inserts at beginning") {
        val doc    = Document("Title", "world")
        val patch  = Patch.editString(Document.content, Chunk(DynamicPatch.StringOp.Insert(0, "Hello ")))
        val result = patch(doc, PatchMode.Strict)
        assertTrue(result == Right(Document("Title", "Hello world")))
      },
      test("inserts at end") {
        val doc    = Document("Title", "Hello")
        val patch  = Patch.editString(Document.content, Chunk(DynamicPatch.StringOp.Insert(5, " world")))
        val result = patch(doc, PatchMode.Strict)
        assertTrue(result == Right(Document("Title", "Hello world")))
      },
      test("handles unicode text") {
        val doc    = Document("Title", "Hello")
        val patch  = Patch.editString(Document.content, Chunk(DynamicPatch.StringOp.Insert(5, " 世界 🌍")))
        val result = patch(doc, PatchMode.Strict)
        assertTrue(result == Right(Document("Title", "Hello 世界 🌍")))
      },
      test("fails on out of bounds insert") {
        val doc    = Document("Title", "Hello")
        val patch  = Patch.editString(Document.content, Chunk(DynamicPatch.StringOp.Insert(100, "!")))
        val result = patch(doc, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("fails on out of bounds delete") {
        val doc    = Document("Title", "Hello")
        val patch  = Patch.editString(Document.content, Chunk(DynamicPatch.StringOp.Delete(0, 100)))
        val result = patch(doc, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("appends text to end") {
        val doc    = Document("Title", "Hello")
        val patch  = Patch.editString(Document.content, Chunk(DynamicPatch.StringOp.Append(" world")))
        val result = patch(doc, PatchMode.Strict)
        assertTrue(result == Right(Document("Title", "Hello world")))
      },
      test("appends empty string") {
        val doc    = Document("Title", "Hello")
        val patch  = Patch.editString(Document.content, Chunk(DynamicPatch.StringOp.Append("")))
        val result = patch(doc, PatchMode.Strict)
        assertTrue(result == Right(Document("Title", "Hello")))
      },
      test("modifies (replaces) substring") {
        val doc    = Document("Title", "Hello world")
        val patch  = Patch.editString(Document.content, Chunk(DynamicPatch.StringOp.Modify(6, 5, "everyone")))
        val result = patch(doc, PatchMode.Strict)
        assertTrue(result == Right(Document("Title", "Hello everyone")))
      },
      test("modifies with empty replacement (equivalent to delete)") {
        val doc    = Document("Title", "Hello beautiful world")
        val patch  = Patch.editString(Document.content, Chunk(DynamicPatch.StringOp.Modify(6, 10, "")))
        val result = patch(doc, PatchMode.Strict)
        assertTrue(result == Right(Document("Title", "Hello world")))
      },
      test("modifies at beginning") {
        val doc    = Document("Title", "Hello world")
        val patch  = Patch.editString(Document.content, Chunk(DynamicPatch.StringOp.Modify(0, 5, "Goodbye")))
        val result = patch(doc, PatchMode.Strict)
        assertTrue(result == Right(Document("Title", "Goodbye world")))
      },
      test("fails on out of bounds modify") {
        val doc    = Document("Title", "Hello")
        val patch  = Patch.editString(Document.content, Chunk(DynamicPatch.StringOp.Modify(0, 100, "test")))
        val result = patch(doc, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("combines all operations") {
        val doc   = Document("Title", "Hello")
        val patch = Patch.editString(
          Document.content,
          Chunk(
            DynamicPatch.StringOp.Insert(0, "Say: "),
            DynamicPatch.StringOp.Modify(5, 5, "Hi"),
            DynamicPatch.StringOp.Append("!")
          )
        )
        val result = patch(doc, PatchMode.Strict)
        // After insert: "Say: Hello"
        // After modify: "Say: Hi" (replaces "Hello" at index 5 with "Hi")
        // After append: "Say: Hi!"
        assertTrue(result == Right(Document("Title", "Say: Hi!")))
      }
    )
  )

  val edgeCaseTests = suite("Edge Cases")(
    test("type mismatch: apply int delta to string field fails") {
      // This test verifies that type mismatches are caught
      // We can't easily test this at compile time, but at runtime the patch should fail
      val doc = Document("Title", "Content")
      // Manually construct a patch with wrong operation type
      val wrongPatch = DynamicPatch(
        Chunk(
          DynamicPatch.DynamicPatchOp(
            DynamicOptic(Chunk(DynamicOptic.Node.Field("content"))),
            DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
          )
        )
      )
      val patch  = Patch(wrongPatch, Document.schema)
      val result = patch(doc, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("composition of different delta types") {
      val counter = Counter(10, 100L, 5.0, 2.0f, 0, 0)
      val patch   = Patch.increment(Counter.value, 5) ++
        Patch.increment(Counter.longValue, 50L) ++
        Patch.increment(Counter.doubleValue, 2.5)
      val result = patch(counter, PatchMode.Strict)
      assertTrue(
        result.exists { c =>
          c.value == 15 &&
          c.longValue == 150L &&
          math.abs(c.doubleValue - 7.5) < 0.0001
        }
      )
    },
    test("zero delta does nothing") {
      val counter = Counter(10, 0L, 0.0, 0.0f, 0, 0)
      val patch   = Patch.increment(Counter.value, 0)
      val result  = patch(counter, PatchMode.Strict)
      assertTrue(result == Right(counter))
    },
    test("string edit with empty operations") {
      val doc    = Document("Title", "Content")
      val patch  = Patch.editString(Document.content, Chunk.empty)
      val result = patch(doc, PatchMode.Strict)
      assertTrue(result == Right(doc))
    }
  )
}
