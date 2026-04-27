/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.sql

import zio.test.*

import scala.language.implicitConversions
import java.time._
import java.util.UUID

object SqlInterpolatorSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("SqlInterpolatorSpec")(
    suite("DbParam givens")(
      test("Int param converts to DbInt") {
        val p = DbParam[Int]
        assertTrue(p.toDbValue(42) == DbValue.DbInt(42))
      },
      test("Long param converts to DbLong") {
        val p = DbParam[Long]
        assertTrue(p.toDbValue(42L) == DbValue.DbLong(42L))
      },
      test("Double param converts to DbDouble") {
        val p = DbParam[Double]
        assertTrue(p.toDbValue(3.14) == DbValue.DbDouble(3.14))
      },
      test("Float param converts to DbFloat") {
        val p = DbParam[Float]
        assertTrue(p.toDbValue(3.14f) == DbValue.DbFloat(3.14f))
      },
      test("Boolean param converts to DbBoolean") {
        val p = DbParam[Boolean]
        assertTrue(p.toDbValue(true) == DbValue.DbBoolean(true))
      },
      test("String param converts to DbString") {
        val p = DbParam[String]
        assertTrue(p.toDbValue("hello") == DbValue.DbString("hello"))
      },
      test("Short param converts to DbShort") {
        val p = DbParam[Short]
        assertTrue(p.toDbValue(42.toShort) == DbValue.DbShort(42.toShort))
      },
      test("Byte param converts to DbByte") {
        val p = DbParam[Byte]
        assertTrue(p.toDbValue(7.toByte) == DbValue.DbByte(7.toByte))
      },
      test("BigDecimal param converts to DbBigDecimal") {
        val p = DbParam[BigDecimal]
        assertTrue(p.toDbValue(BigDecimal("3.14")) == DbValue.DbBigDecimal(BigDecimal("3.14")))
      },
      test("Array[Byte] param converts to DbBytes") {
        val p      = DbParam[Array[Byte]]
        val bytes  = Array[Byte](1, 2, 3)
        val result = p.toDbValue(bytes) match {
          case DbValue.DbBytes(v) => v.sameElements(bytes)
          case _                  => false
        }
        assertTrue(result)
      },
      test("LocalDate param converts to DbLocalDate") {
        val p    = DbParam[LocalDate]
        val date = LocalDate.of(2024, 1, 15)
        assertTrue(p.toDbValue(date) == DbValue.DbLocalDate(date))
      },
      test("LocalDateTime param converts to DbLocalDateTime") {
        val p  = DbParam[LocalDateTime]
        val dt = LocalDateTime.of(2024, 1, 15, 12, 30)
        assertTrue(p.toDbValue(dt) == DbValue.DbLocalDateTime(dt))
      },
      test("LocalTime param converts to DbLocalTime") {
        val p = DbParam[LocalTime]
        val t = LocalTime.of(12, 30, 45)
        assertTrue(p.toDbValue(t) == DbValue.DbLocalTime(t))
      },
      test("Instant param converts to DbInstant") {
        val p       = DbParam[Instant]
        val instant = Instant.parse("2024-01-15T12:00:00Z")
        assertTrue(p.toDbValue(instant) == DbValue.DbInstant(instant))
      },
      test("Duration param converts to DbDuration") {
        val p   = DbParam[Duration]
        val dur = Duration.ofHours(2)
        assertTrue(p.toDbValue(dur) == DbValue.DbDuration(dur))
      },
      test("UUID param converts to DbUUID") {
        val p    = DbParam[UUID]
        val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(p.toDbValue(uuid) == DbValue.DbUUID(uuid))
      },
      test("DbValue passthrough") {
        val p = DbParam[DbValue]
        val v = DbValue.DbString("raw")
        assertTrue(p.toDbValue(v) == DbValue.DbString("raw"))
      },
      test("Option Some produces inner value") {
        val p = DbParam[Option[Int]]
        assertTrue(p.toDbValue(Some(42)) == DbValue.DbInt(42))
      },
      test("Option None produces DbNull") {
        val p = DbParam[Option[Int]]
        assertTrue(p.toDbValue(None) == DbValue.DbNull)
      },
      test("Nested Option Some(Some) produces inner value") {
        val p = DbParam[Option[Option[String]]]
        assertTrue(p.toDbValue(Some(Some("nested"))) == DbValue.DbString("nested"))
      },
      test("Nested Option Some(None) produces DbNull") {
        val p = DbParam[Option[Option[String]]]
        assertTrue(p.toDbValue(Some(None)) == DbValue.DbNull)
      }
    ),
    suite("sql interpolator with DbValue params")(
      test("single DbValue param") {
        val frag = sql"SELECT ${DbValue.DbInt(42)}"
        assertTrue(
          frag.queryParams == IndexedSeq(DbValue.DbInt(42)),
          frag.parts == IndexedSeq("SELECT ", "")
        )
      },
      test("multiple DbValue params") {
        val frag =
          sql"INSERT INTO t VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("hello")}, ${DbValue.DbBoolean(true)}, ${DbValue.DbDouble(3.14)})"
        assertTrue(
          frag.queryParams.length == 4,
          frag.queryParams(0) == DbValue.DbInt(1),
          frag.queryParams(1) == DbValue.DbString("hello"),
          frag.queryParams(2) == DbValue.DbBoolean(true),
          frag.queryParams(3) == DbValue.DbDouble(3.14)
        )
      },
      test("no params produces empty params") {
        val frag = sql"SELECT 1"
        assertTrue(
          frag.queryParams.isEmpty,
          frag.parts == IndexedSeq("SELECT 1")
        )
      }
    ),
    suite("sql interpolator with DbParam conversion")(
      test("Int param converts via DbParam") {
        val frag = sql"SELECT ${42}"
        assertTrue(
          frag.queryParams == IndexedSeq(DbValue.DbInt(42)),
          frag.parts == IndexedSeq("SELECT ", "")
        )
      },
      test("String param converts via DbParam") {
        val frag = sql"SELECT ${"hello"}"
        assertTrue(frag.queryParams == IndexedSeq(DbValue.DbString("hello")))
      },
      test("Boolean param converts via DbParam") {
        val frag = sql"SELECT ${true}"
        assertTrue(frag.queryParams == IndexedSeq(DbValue.DbBoolean(true)))
      },
      test("multiple mixed DbParam types") {
        val frag = sql"INSERT INTO t VALUES (${1}, ${"hello"}, ${true}, ${3.14})"
        assertTrue(
          frag.queryParams.length == 4,
          frag.queryParams(0) == DbValue.DbInt(1),
          frag.queryParams(1) == DbValue.DbString("hello"),
          frag.queryParams(2) == DbValue.DbBoolean(true),
          frag.queryParams(3) == DbValue.DbDouble(3.14)
        )
      },
      test("Option Some converts via DbParam") {
        val v: Option[Int] = Some(42)
        val frag           = sql"SELECT ${v}"
        assertTrue(frag.queryParams == IndexedSeq(DbValue.DbInt(42)))
      },
      test("Option None converts via DbParam") {
        val v: Option[Int] = None
        val frag           = sql"SELECT ${v}"
        assertTrue(frag.queryParams == IndexedSeq(DbValue.DbNull))
      }
    )
  )
}
