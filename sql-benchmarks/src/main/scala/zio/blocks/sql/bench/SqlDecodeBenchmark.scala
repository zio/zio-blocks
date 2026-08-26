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

package zio.blocks.sql.bench

import java.sql.DriverManager
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._
import scala.compiletime.uninitialized
import zio.blocks.BaseBenchmark
import zio.blocks.maybe.Maybe
import zio.blocks.sql._

/**
 * Benchmarks the per-row SQL decode path: `DbCodec.derived` record decode of
 * rows from an in-memory SQLite table. This is the allocation hot spot fixed in
 * steps 1-4 (ThreadLocal `Registers`, index-based decode fast path, cached SQL
 * re-render) plus step-6 NULL handling for `Option`/`Maybe` columns: bitmap
 * recording with post-read `wasNull` fallback. The `isNull` precheck only
 * short-circuits decoding on backends that know nullness before the getter runs
 * (wire-protocol drivers); JDBC reports it after the read, so this benchmark
 * measures the fallback path those backends must not regress.
 *
 * The fixture is inserted once in `@Setup`; the benchmark methods only run the
 * `SELECT ... WHERE ...` decode loop, so per-row allocations are the measured
 * quantity. `-prof gc` reports `alloc` per operation; divide by `rows` for the
 * per-row figure.
 *
 * Full run (per-row allocation counts):
 * {{{
 * sbt --client -Dsbt.color=false "++3.8.3; sql-benchmarks/Jmh/run -prof gc -f 1 -wi 3 -i 3 -r 1s zio.blocks.sql.bench.SqlDecodeBenchmark"
 * }}}
 *
 * Smoke run (throughput sanity, no profiler):
 * {{{
 * sbt --client -Dsbt.color=false "++3.8.3; sql-benchmarks/Jmh/run -f 1 -wi 1 -i 1 -r 1s zio.blocks.sql.bench.SqlDecodeBenchmark"
 * }}}
 */
@BenchmarkMode(Array(Mode.SampleTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
class SqlDecodeBenchmark extends BaseBenchmark {

  case class UserRow(
    id: Int,
    name: String,
    email: String,
    age: Int,
    active: Boolean,
    nickname: Option[String],
    bio: Maybe[String]
  )

  object UserRow {
    implicit val dbCodec: DbCodec[UserRow] = DbCodec.derived[UserRow]
  }

  @Param(Array("1000"))
  var rows: Int = uninitialized

  private var transactor: JdbcTransactor = uninitialized
  private var codec: DbCodec[UserRow]    = uninitialized
  private var allFrag: Frag              = uninitialized
  private var oneFrag: Frag              = uninitialized

  @Setup
  def setup(): Unit = {
    // Shared connection so every `connect` sees the same in-memory database.
    // `connect` is overridden to wrap without closing: the default
    // `JdbcTransactor.connect` closes the underlying connection on return,
    // which would kill the in-memory DB after the first benchmark invocation.
    val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
    transactor = new JdbcTransactor(() => conn, SqlDialect.SQLite) {
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

    transactor.connect {
      Frag
        .literal(
          "CREATE TABLE users (id INTEGER NOT NULL, name TEXT NOT NULL, email TEXT NOT NULL, " +
            "age INTEGER NOT NULL, active INTEGER NOT NULL, nickname TEXT, bio TEXT)"
        )
        .update

      var i = 0
      while (i < rows) {
        sql"INSERT INTO users (id, name, email, age, active, nickname, bio) VALUES (${DbValue.DbInt(i)}, ${DbValue.DbString("user" + i)}, ${DbValue.DbString("user" + i + "@example.com")}, ${DbValue.DbInt(20 + (i % 50))}, ${DbValue.DbBoolean(i % 2 == 0)}, ${
            if (i % 3 == 0) DbValue.DbNull else DbValue.DbString("nick" + i)
          }, ${if (i % 5 == 0) DbValue.DbNull else DbValue.DbString("bio" + i)})".update
        i += 1
      }
    }

    codec = UserRow.dbCodec
    allFrag = sql"SELECT id, name, email, age, active, nickname, bio FROM users WHERE age >= 20"
    oneFrag = sql"SELECT id, name, email, age, active, nickname, bio FROM users WHERE id = 1"
  }

  /**
   * Decodes all `rows` rows per invocation; `alloc`/op divided by `rows` =
   * per-row.
   */
  @Benchmark
  def decodeAllRows(): Int =
    transactor.connect {
      allFrag.query[UserRow](using summon[DbCon], codec).size
    }

  /** Single-row decode via `queryOne` (the `Repo.find`-style hot path). */
  @Benchmark
  def decodeSingleRow(): Maybe[UserRow] =
    transactor.connect {
      oneFrag.queryOne[UserRow](using summon[DbCon], codec)
    }
}
