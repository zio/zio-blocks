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

import java.sql.DriverManager
import zio.blocks.maybe.Maybe
import zio.blocks.schema.Schema
import zio.test.*

object DbCodecOptionSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

  case class MixedRecord(id: Int, a: Option[Int], b: Option[String])
  object MixedRecord {
    implicit val schema: Schema[MixedRecord] = Schema.derived
  }

  case class Profile(id: Int, nickname: Option[String], bio: Maybe[String])
  object Profile {
    implicit val schema: Schema[Profile] = Schema.derived
  }

  private val profileTable       = Table.derived[Profile]
  private given DbCodec[Profile] = Profile.schema.deriving(DbCodecDeriver).derive
  private val profileRepo        = Repo(profileTable, "id", summon[DbCodec[Int]], (_: Profile).id)

  private def withFreshDb[A](f: JdbcTransactor => A): A = {
    val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
    try {
      val tx = new JdbcTransactor(() => conn, SqlDialect.SQLite) {
        override def connect[B](f: DbCon ?=> B): B = {
          val dbConn       = new JdbcConnection(conn)
          given con: DbCon = new DbCon {
            val connection: DbConnection = dbConn
            val dialect: SqlDialect      = SqlDialect.SQLite
            val logger: SqlLogger        = SqlLogger.noop
          }
          f
        }
      }
      f(tx)
    } finally conn.close()
  }

  private final class RecordingWriter extends DbParamWriter {
    var nullCalls: Vector[(Int, Int)] = Vector.empty

    def setInt(index: Int, value: Int): Unit                                       = ()
    def setLong(index: Int, value: Long): Unit                                     = ()
    def setDouble(index: Int, value: Double): Unit                                 = ()
    def setFloat(index: Int, value: Float): Unit                                   = ()
    def setBoolean(index: Int, value: Boolean): Unit                               = ()
    def setString(index: Int, value: String): Unit                                 = ()
    def setBigDecimal(index: Int, value: java.math.BigDecimal): Unit               = ()
    def setBytes(index: Int, value: Array[Byte]): Unit                             = ()
    def setShort(index: Int, value: Short): Unit                                   = ()
    def setByte(index: Int, value: Byte): Unit                                     = ()
    def setLocalDate(index: Int, value: java.time.LocalDate): Unit                 = ()
    def setLocalDateTime(index: Int, value: java.time.LocalDateTime): Unit         = ()
    def setLocalTime(index: Int, value: java.time.LocalTime): Unit                 = ()
    def setInstant(index: Int, value: java.time.Instant): Unit                     = ()
    def setDuration(index: Int, value: java.time.Duration): Unit                   = ()
    def setUUID(index: Int, value: java.util.UUID): Unit                           = ()
    def setNull(index: Int, sqlType: Int): Unit                                    = nullCalls = nullCalls :+ (index -> sqlType)
    def setArray(index: Int, elementType: String, elements: IndexedSeq[Any]): Unit = ()
  }

  def spec: Spec[TestEnvironment, Any] = suite("DbCodecOptionSpec")(
    test("Option[String] round-trips SQL NULL and non-NULL") {
      withFreshDb { tx =>
        tx.connect {
          Frag.literal("CREATE TABLE option_string_rt (value TEXT)").update
          sql"INSERT INTO option_string_rt (value) VALUES (${DbValue.DbString("hello")})".update
          sql"INSERT INTO option_string_rt (value) VALUES (${DbValue.DbNull})".update

          val results = sql"SELECT value FROM option_string_rt ORDER BY rowid".query[Option[String]]

          assertTrue(results == List(Some("hello"), None))
        }
      }
    },
    test("Option[Int] round-trips SQL NULL and non-NULL") {
      withFreshDb { tx =>
        tx.connect {
          Frag.literal("CREATE TABLE option_int_rt (value INTEGER)").update
          sql"INSERT INTO option_int_rt (value) VALUES (${DbValue.DbInt(42)})".update
          sql"INSERT INTO option_int_rt (value) VALUES (${DbValue.DbNull})".update

          val results = sql"SELECT value FROM option_int_rt ORDER BY rowid".query[Option[Int]]

          assertTrue(results == List(Some(42), None))
        }
      }
    },
    test("Maybe[String] round-trips SQL NULL and non-NULL") {
      withFreshDb { tx =>
        tx.connect {
          Frag.literal("CREATE TABLE maybe_string_rt (value TEXT)").update
          sql"INSERT INTO maybe_string_rt (value) VALUES (${DbValue.DbString("hello")})".update
          sql"INSERT INTO maybe_string_rt (value) VALUES (${DbValue.DbNull})".update

          val results = sql"SELECT value FROM maybe_string_rt ORDER BY rowid".query[Maybe[String]]

          assertTrue(
            results.length == 2,
            results.head == Maybe.present("hello"),
            results(1).isAbsent
          )
        }
      }
    },
    test("tuple codec auto-derives for Option fields") {
      withFreshDb { tx =>
        tx.connect {
          val codec = summon[DbCodec[(String, Option[Int], Option[String])]]
          Frag.literal("CREATE TABLE tuple_option_rt (name TEXT NOT NULL, age INTEGER, nickname TEXT)").update
          sql"INSERT INTO tuple_option_rt (name, age, nickname) VALUES (${DbValue.DbString("Alice")}, ${DbValue.DbInt(30)}, ${DbValue.DbString("ally")})".update
          sql"INSERT INTO tuple_option_rt (name, age, nickname) VALUES (${DbValue.DbString("Bob")}, ${DbValue.DbNull}, ${DbValue.DbNull})".update

          val results = sql"SELECT name, age, nickname FROM tuple_option_rt ORDER BY rowid"
            .query[(String, Option[Int], Option[String])]

          assertTrue(
            codec.columnCount == 3,
            results == List(
              ("Alice", Some(30), Some("ally")),
              ("Bob", None, None)
            )
          )
        }
      }
    },
    test("Option and Maybe codecs write SQL NULL with setNull") {
      val writer      = new RecordingWriter
      val optionCodec = summon[DbCodec[Option[Int]]]
      val maybeCodec  = summon[DbCodec[Maybe[String]]]

      optionCodec.writeValue(writer, 2, None)
      maybeCodec.writeValue(writer, 5, Maybe.absent)

      assertTrue(
        writer.nullCalls == Vector(
          2 -> java.sql.Types.NULL,
          5 -> java.sql.Types.NULL
        )
      )
    },
    test("Option tuple decodes mixed NULL and non-NULL columns per row") {
      withFreshDb { tx =>
        tx.connect {
          Frag.literal("CREATE TABLE mixed_null_rt (a INTEGER, b TEXT)").update
          sql"INSERT INTO mixed_null_rt (a, b) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("x")})".update
          sql"INSERT INTO mixed_null_rt (a, b) VALUES (${DbValue.DbNull}, ${DbValue.DbString("y")})".update
          sql"INSERT INTO mixed_null_rt (a, b) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbNull})".update
          sql"INSERT INTO mixed_null_rt (a, b) VALUES (${DbValue.DbNull}, ${DbValue.DbNull})".update

          val results = sql"SELECT a, b FROM mixed_null_rt ORDER BY rowid".query[(Option[Int], Option[String])]

          assertTrue(
            results == List(
              (Some(1), Some("x")),
              (None, Some("y")),
              (Some(2), None),
              (None, None)
            )
          )
        }
      }
    },
    test("derived record with Option fields decodes mixed NULL and non-NULL columns") {
      withFreshDb { tx =>
        tx.connect {
          Frag.literal("CREATE TABLE mixed_record_rt (id INTEGER NOT NULL, a INTEGER, b TEXT)").update
          sql"INSERT INTO mixed_record_rt (id, a, b) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbInt(10)}, ${DbValue.DbString("x")})".update
          sql"INSERT INTO mixed_record_rt (id, a, b) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbNull}, ${DbValue.DbString("y")})".update
          sql"INSERT INTO mixed_record_rt (id, a, b) VALUES (${DbValue.DbInt(3)}, ${DbValue.DbInt(30)}, ${DbValue.DbNull})".update
          sql"INSERT INTO mixed_record_rt (id, a, b) VALUES (${DbValue.DbInt(4)}, ${DbValue.DbNull}, ${DbValue.DbNull})".update

          val results = sql"SELECT id, a, b FROM mixed_record_rt ORDER BY id".query[MixedRecord]

          assertTrue(
            results == List(
              MixedRecord(1, Some(10), Some("x")),
              MixedRecord(2, None, Some("y")),
              MixedRecord(3, Some(30), None),
              MixedRecord(4, None, None)
            )
          )
        }
      }
    },
    test("Repo-backed entity with Option and Maybe fields round-trips NULL and non-NULL") {
      withFreshDb { tx =>
        tx.connect {
          Frag.literal("CREATE TABLE profile (id INTEGER NOT NULL, nickname TEXT, bio TEXT)").update
          profileRepo.insert(Profile(1, Some("nabil"), Maybe.present("hello")))
          profileRepo.insert(Profile(2, None, Maybe.absent))
          profileRepo.insert(Profile(3, Some("x"), Maybe.absent))

          val p1 = profileRepo.find(1)
          val p2 = profileRepo.find(2)
          val p3 = profileRepo.find(3)

          assertTrue(
            p1 == Maybe(Profile(1, Some("nabil"), Maybe.present("hello"))),
            p2 == Maybe(Profile(2, None, Maybe.absent)),
            p3 == Maybe(Profile(3, Some("x"), Maybe.absent))
          )
        }
      }
    }
  )
}
