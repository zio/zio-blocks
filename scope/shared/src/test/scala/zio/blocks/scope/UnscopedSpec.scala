package zio.blocks.scope

import zio.test._
import zio.blocks.chunk.Chunk
import java.time._
import java.util.UUID
import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration}

/**
 * Tests that all expected Unscoped instances exist and work correctly. Each
 * instance is tested by verifying it can be summoned and used with ScopeEscape.
 */
object UnscopedSpec extends ZIOSpecDefault {

  // Helper to verify an Unscoped instance exists and ScopeEscape returns raw type
  def verifyUnscoped[A](value: A)(implicit ev: Unscoped[A]): Boolean = {
    val escape = implicitly[ScopeEscape[A, String]]
    escape(value) == value
  }

  def spec = suite("Unscoped")(
    suite("Primitives")(
      test("Int") {
        assertTrue(verifyUnscoped(42))
      },
      test("Long") {
        assertTrue(verifyUnscoped(42L))
      },
      test("Short") {
        assertTrue(verifyUnscoped(42.toShort))
      },
      test("Byte") {
        assertTrue(verifyUnscoped(42.toByte))
      },
      test("Char") {
        assertTrue(verifyUnscoped('a'))
      },
      test("Boolean") {
        assertTrue(verifyUnscoped(true))
      },
      test("Float") {
        assertTrue(verifyUnscoped(3.14f))
      },
      test("Double") {
        assertTrue(verifyUnscoped(3.14))
      },
      test("Unit") {
        assertTrue(verifyUnscoped(()))
      },
      test("String") {
        assertTrue(verifyUnscoped("hello"))
      },
      test("BigInt") {
        assertTrue(verifyUnscoped(BigInt(42)))
      },
      test("BigDecimal") {
        assertTrue(verifyUnscoped(BigDecimal(3.14)))
      }
    ),
    suite("Collections")(
      test("Array[Int]") {
        assertTrue(verifyUnscoped(Array(1, 2, 3)))
      },
      test("List[String]") {
        assertTrue(verifyUnscoped(List("a", "b")))
      },
      test("Vector[Int]") {
        assertTrue(verifyUnscoped(Vector(1, 2)))
      },
      test("Set[Int]") {
        assertTrue(verifyUnscoped(Set(1, 2)))
      },
      test("Option[Int] - Some") {
        assertTrue(verifyUnscoped(Some(42): Option[Int]))
      },
      test("Option[Int] - None") {
        assertTrue(verifyUnscoped(None: Option[Int]))
      },
      test("Seq[Int]") {
        assertTrue(verifyUnscoped(Seq(1, 2)))
      },
      test("IndexedSeq[Int]") {
        assertTrue(verifyUnscoped(IndexedSeq(1, 2)))
      },
      test("Iterable[Int]") {
        assertTrue(verifyUnscoped(Iterable(1, 2)))
      },
      test("Map[String, Int]") {
        assertTrue(verifyUnscoped(Map("a" -> 1)))
      },
      test("Chunk[Int]") {
        assertTrue(verifyUnscoped(Chunk(1, 2, 3)))
      }
    ),
    suite("Either")(
      test("Either[String, Int] - Right") {
        assertTrue(verifyUnscoped(Right(42): Either[String, Int]))
      },
      test("Either[String, Int] - Left") {
        assertTrue(verifyUnscoped(Left("error"): Either[String, Int]))
      }
    ),
    suite("Tuples")(
      test("Tuple2") {
        assertTrue(verifyUnscoped((1, "a")))
      },
      test("Tuple3") {
        assertTrue(verifyUnscoped((1, "a", true)))
      },
      test("Tuple4") {
        assertTrue(verifyUnscoped((1, "a", true, 3.14)))
      }
    ),
    suite("Java time types")(
      test("Instant") {
        assertTrue(verifyUnscoped(Instant.now()))
      },
      test("LocalDate") {
        assertTrue(verifyUnscoped(LocalDate.now()))
      },
      test("LocalTime") {
        assertTrue(verifyUnscoped(LocalTime.now()))
      },
      test("LocalDateTime") {
        assertTrue(verifyUnscoped(LocalDateTime.now()))
      },
      test("ZonedDateTime") {
        assertTrue(verifyUnscoped(ZonedDateTime.now()))
      },
      test("OffsetDateTime") {
        assertTrue(verifyUnscoped(OffsetDateTime.now()))
      },
      test("java.time.Duration") {
        assertTrue(verifyUnscoped(Duration.ofSeconds(10)))
      },
      test("Period") {
        assertTrue(verifyUnscoped(Period.ofDays(5)))
      },
      test("ZoneId") {
        assertTrue(verifyUnscoped(ZoneId.systemDefault()))
      },
      test("ZoneOffset") {
        assertTrue(verifyUnscoped(ZoneOffset.UTC))
      }
    ),
    suite("Common Java types")(
      test("UUID") {
        // Use fixed UUID instead of randomUUID() for Scala.js compatibility (no SecureRandom)
        val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(verifyUnscoped(uuid))
      }
    ),
    suite("Scala duration")(
      test("scala.concurrent.duration.Duration") {
        assertTrue(verifyUnscoped(ScalaDuration.fromNanos(1000)))
      },
      test("FiniteDuration") {
        assertTrue(verifyUnscoped(FiniteDuration(5, "seconds")))
      }
    ),
    suite("Nested collections")(
      test("List[Option[Int]]") {
        assertTrue(verifyUnscoped(List(Some(1), None, Some(2))))
      },
      test("Map[String, List[Int]]") {
        assertTrue(verifyUnscoped(Map("a" -> List(1, 2))))
      },
      test("Either[String, List[Int]]") {
        assertTrue(verifyUnscoped(Right(List(1, 2)): Either[String, List[Int]]))
      },
      test("Chunk[Option[String]]") {
        assertTrue(verifyUnscoped(Chunk(Some("a"), None)))
      }
    )
  )
}
