package zio.blocks.scope

import zio.test._
import zio.blocks.chunk.Chunk
import java.time._
import java.util.UUID
import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration}

/**
 * Tests that all expected Unscoped instances exist and work correctly.
 */
object UnscopedSpec extends ZIOSpecDefault {

  def spec = suite("Unscoped")(
    suite("Primitives")(
      test("Int") {
        implicitly[Unscoped[Int]]; assertCompletes
      },
      test("Long") {
        implicitly[Unscoped[Long]]; assertCompletes
      },
      test("Short") {
        implicitly[Unscoped[Short]]; assertCompletes
      },
      test("Byte") {
        implicitly[Unscoped[Byte]]; assertCompletes
      },
      test("Char") {
        implicitly[Unscoped[Char]]; assertCompletes
      },
      test("Boolean") {
        implicitly[Unscoped[Boolean]]; assertCompletes
      },
      test("Float") {
        implicitly[Unscoped[Float]]; assertCompletes
      },
      test("Double") {
        implicitly[Unscoped[Double]]; assertCompletes
      },
      test("Unit") {
        implicitly[Unscoped[Unit]]; assertCompletes
      },
      test("String") {
        implicitly[Unscoped[String]]; assertCompletes
      },
      test("BigInt") {
        implicitly[Unscoped[BigInt]]; assertCompletes
      },
      test("BigDecimal") {
        implicitly[Unscoped[BigDecimal]]; assertCompletes
      }
    ),
    suite("Collections")(
      test("Array[Int]") {
        implicitly[Unscoped[Array[Int]]]; assertCompletes
      },
      test("List[String]") {
        implicitly[Unscoped[List[String]]]; assertCompletes
      },
      test("Vector[Int]") {
        implicitly[Unscoped[Vector[Int]]]; assertCompletes
      },
      test("Set[Int]") {
        implicitly[Unscoped[Set[Int]]]; assertCompletes
      },
      test("Option[Int] - Some") {
        implicitly[Unscoped[Option[Int]]]; assertCompletes
      },
      test("Option[Int] - None") {
        implicitly[Unscoped[Option[Int]]]; assertCompletes
      },
      test("Seq[Int]") {
        implicitly[Unscoped[Seq[Int]]]; assertCompletes
      },
      test("IndexedSeq[Int]") {
        implicitly[Unscoped[IndexedSeq[Int]]]; assertCompletes
      },
      test("Iterable[Int]") {
        implicitly[Unscoped[Iterable[Int]]]; assertCompletes
      },
      test("Map[String, Int]") {
        implicitly[Unscoped[Map[String, Int]]]; assertCompletes
      },
      test("Chunk[Int]") {
        implicitly[Unscoped[Chunk[Int]]]; assertCompletes
      }
    ),
    suite("Either")(
      test("Either[String, Int] - Right") {
        implicitly[Unscoped[Either[String, Int]]]; assertCompletes
      },
      test("Either[String, Int] - Left") {
        implicitly[Unscoped[Either[String, Int]]]; assertCompletes
      }
    ),
    suite("Tuples")(
      test("Tuple2") {
        implicitly[Unscoped[(Int, String)]]; assertCompletes
      },
      test("Tuple3") {
        implicitly[Unscoped[(Int, String, Boolean)]]; assertCompletes
      },
      test("Tuple4") {
        implicitly[Unscoped[(Int, String, Boolean, Double)]]; assertCompletes
      }
    ),
    suite("Java time types")(
      test("Instant") {
        implicitly[Unscoped[Instant]]; assertCompletes
      },
      test("LocalDate") {
        implicitly[Unscoped[LocalDate]]; assertCompletes
      },
      test("LocalTime") {
        implicitly[Unscoped[LocalTime]]; assertCompletes
      },
      test("LocalDateTime") {
        implicitly[Unscoped[LocalDateTime]]; assertCompletes
      },
      test("ZonedDateTime") {
        implicitly[Unscoped[ZonedDateTime]]; assertCompletes
      },
      test("OffsetDateTime") {
        implicitly[Unscoped[OffsetDateTime]]; assertCompletes
      },
      test("java.time.Duration") {
        implicitly[Unscoped[Duration]]; assertCompletes
      },
      test("Period") {
        implicitly[Unscoped[Period]]; assertCompletes
      },
      test("ZoneId") {
        implicitly[Unscoped[ZoneId]]; assertCompletes
      },
      test("ZoneOffset") {
        implicitly[Unscoped[ZoneOffset]]; assertCompletes
      }
    ),
    suite("Common Java types")(
      test("UUID") {
        implicitly[Unscoped[UUID]]; assertCompletes
      }
    ),
    suite("Scala duration")(
      test("scala.concurrent.duration.Duration") {
        implicitly[Unscoped[ScalaDuration]]; assertCompletes
      },
      test("FiniteDuration") {
        implicitly[Unscoped[FiniteDuration]]; assertCompletes
      }
    ),
    suite("Nested collections")(
      test("List[Option[Int]]") {
        implicitly[Unscoped[List[Option[Int]]]]; assertCompletes
      },
      test("Map[String, List[Int]]") {
        implicitly[Unscoped[Map[String, List[Int]]]]; assertCompletes
      },
      test("Either[String, List[Int]]") {
        implicitly[Unscoped[Either[String, List[Int]]]]; assertCompletes
      },
      test("Chunk[Option[String]]") {
        implicitly[Unscoped[Chunk[Option[String]]]]; assertCompletes
      }
    )
  )
}
