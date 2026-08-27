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
import zio.blocks.sql._
import kyo.{
  `<`,
  Abort,
  AllowUnsafe,
  Async,
  DB,
  Duration,
  KyoApp,
  Result,
  Scope,
  Sql,
  SqlClient,
  SqlException,
  SqlSchema
}

/**
 * Real-world workload comparison on ONE PostgreSQL server with ONE dataset:
 *
 *   - `raw*` — hand-rolled JDBC mapping (the floor every framework pays)
 *   - `zb_*` — zio-blocks `sql` module (JDBC + postgres driver)
 *   - `kyo_*` — kyo-sql RC6 (native wire protocol, pooled connections)
 *
 * Workloads mirror application shapes: point lookups (`Repo.find` hot loop),
 * full-list rendering, large export (chunked/streamed), and a bulk write.
 *
 * Fixture: 10,000-row `users` table; `nickname` NULL every 3rd row, `bio` NULL
 * every 5th row, so Option/Maybe decode paths are exercised.
 *
 * Run (allocations + timing):
 * {{{
 * sbt --client -Dsbt.color=false "++3.8.3; sql-benchmarks/Jmh/run -prof gc -f 1 -wi 3 -i 3 -r 1s zio.blocks.sql.bench.RealWorldBenchmark"
 * }}}
 *
 * Environment: PostgreSQL 17 at localhost:32886, db `benchdb`, user `bench`.
 */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
class RealWorldBenchmark extends BaseBenchmark {

  case class BenchUser(
    id: Int,
    name: String,
    email: String,
    age: Int,
    active: Boolean,
    nickname: Option[String],
    bio: Option[String]
  )

  object BenchUser {
    implicit val dbCodec: DbCodec[BenchUser] = DbCodec.derived[BenchUser]
  }

  // Separate row type so kyo's typed DSL (which derives the table name from
  // the type) targets the bulkuser scratch table, not the benchuser fixture.
  case class BulkUser(
    id: Int,
    name: String,
    email: String,
    age: Int,
    active: Boolean,
    nickname: Option[String],
    bio: Option[String]
  ) derives SqlSchema

  private val Rows = 10000
  private val Url  = "postgres://bench:bench@localhost:32886/benchdb"

  private var conn: java.sql.Connection            = uninitialized
  private var transactor: JdbcTransactor           = uninitialized
  private var kyoClient: SqlClient                 = uninitialized
  private var findStmt: java.sql.PreparedStatement = uninitialized
  private var allFrag: Frag                        = uninitialized
  private val Cols                                 = "id, name, email, age, active, nickname, bio"

  given AllowUnsafe = AllowUnsafe.embrace.danger

  private def eval[A](program: A < (Async & Abort[SqlException] & Scope)): A = {
    val result = KyoApp.Unsafe.runAndBlock(Duration.Infinity)(program)
    result match {
      case Result.Success(a) => a
      case Result.Failure(t) => throw new IllegalStateException("kyo failure", t)
      case Result.Panic(t)   => throw new IllegalStateException("kyo panic", t)
    }
  }

  @Setup
  def setup(): Unit = {
    Class.forName("org.postgresql.Driver")
    conn = DriverManager.getConnection("jdbc:postgresql://localhost:32886/benchdb", "bench", "bench")
    conn.setAutoCommit(true)

    val ddl = conn.createStatement()
    ddl.execute("DROP TABLE IF EXISTS benchuser")
    ddl.execute(
      "CREATE TABLE benchuser (id INT PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL, " +
        "age INT NOT NULL, active BOOLEAN NOT NULL, nickname TEXT, bio TEXT)"
    )
    ddl.execute("DROP TABLE IF EXISTS bulkuser")
    ddl.execute(
      "CREATE TABLE bulkuser (id INT PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL, " +
        "age INT NOT NULL, active BOOLEAN NOT NULL, nickname TEXT, bio TEXT)"
    )
    ddl.close()

    val ins = conn.prepareStatement("INSERT INTO benchuser VALUES (?, ?, ?, ?, ?, ?, ?)")
    var i   = 0
    while (i < Rows) {
      ins.setInt(1, i)
      ins.setString(2, "user" + i)
      ins.setString(3, "user" + i + "@example.com")
      ins.setInt(4, 20 + (i % 50))
      ins.setBoolean(5, i % 2 == 0)
      if (i % 3 == 0) ins.setNull(6, java.sql.Types.VARCHAR) else ins.setString(6, "nick" + i)
      if (i % 5 == 0) ins.setNull(7, java.sql.Types.VARCHAR) else ins.setString(7, "bio" + i)
      ins.addBatch()
      i += 1
    }
    ins.executeBatch()
    ins.close()

    findStmt = conn.prepareStatement(s"SELECT $Cols FROM benchuser WHERE id = ?")

    transactor = new JdbcTransactor(() => conn, SqlDialect.PostgreSQL) {
      override def connect[B](f: DbCon ?=> B): B = {
        val dbConn       = new JdbcConnection(conn)
        given con: DbCon = new DbCon {
          val connection: DbConnection = dbConn
          val dialect: SqlDialect      = SqlDialect.PostgreSQL
          val logger: SqlLogger        = SqlLogger.noop
        }
        f
      }
    }

    kyoClient = eval(SqlClient.initUnscoped(Url))

    allFrag = sql"SELECT id, name, email, age, active, nickname, bio FROM benchuser"
  }

  @TearDown
  def tearDown(): Unit = {
    eval(kyoClient.close)
    findStmt.close()
    conn.close()
  }

  // ---- point lookup hot loop (Repo.find style), 100 lookups per op ----

  private def rawFindOne(id: Int): Int = {
    findStmt.setInt(1, id)
    val rs = findStmt.executeQuery()
    rs.next()
    // Decode all seven selected columns like the framework paths do, so the
    // comparison measures identical row materialization work.
    rs.getInt(1)
    rs.getString(2)
    rs.getString(3)
    rs.getInt(4)
    rs.getBoolean(5)
    val nick = rs.getString(6)
    rs.getString(7)
    rs.close()
    if (nick == null) 0 else 1
  }

  @Benchmark
  def raw_findById100(): Int =
    runFindLoop(rawFindOne)

  private def runFindLoop(f: Int => Int): Int = {
    var n  = 0
    var i  = 0
    var id = 0
    while (i < 100) {
      n += f(id)
      id = if (id == 0) 7 else (id * 37 + 11) % Rows
      i += 1
    }
    n
  }

  @Benchmark
  def zb_findById100(): Int = {
    var n  = 0
    var i  = 0
    var id = 0
    transactor.connect {
      while (i < 100) {
        val r = sql"SELECT id, name, email, age, active, nickname, bio FROM benchuser WHERE id = ${DbValue.DbInt(id)}"
          .queryOne[BenchUser]
        r.toOption.foreach(u => if (u.nickname.isDefined) n += 1)
        id = if (id == 0) 7 else (id * 37 + 11) % Rows
        i += 1
      }
    }
    n
  }

  private def findIds: Vector[Int] = {
    val b  = Vector.newBuilder[Int]
    var id = 0
    var i  = 0
    while (i < 100) {
      b += id
      id = if (id == 0) 7 else (id * 37 + 11) % Rows
      i += 1
    }
    b.result()
  }

  @Benchmark
  def kyo_findById100(): Int = {
    val ids     = findIds
    val lookups = ids.map { id =>
      Sql.from[BenchUser].where(r => r.benchUser.id == id).run.map { rows =>
        if (rows.headMaybe.exists(_.nickname.isDefined)) 1 else 0
      }
    }
    eval(DB.run(kyoClient)(lookups.reduceLeft((acc, p) => acc.flatMap(n => p.map(n + _)))))
  }

  // ---- full-list rendering, 10k rows ----

  private def rawListAll(): Int = {
    val st = conn.createStatement()
    val rs = st.executeQuery(s"SELECT $Cols FROM benchuser")
    var n  = 0
    while (rs.next()) {
      rs.getInt(1)
      rs.getString(2)
      rs.getString(3)
      rs.getInt(4)
      rs.getBoolean(5)
      val _nick = rs.getString(6)
      val _bio  = rs.getString(7)
      if (_nick != null || _bio != null) n += 1
    }
    rs.close()
    st.close()
    n
  }

  @Benchmark
  def raw_listAll10k(): Int = rawListAll()

  @Benchmark
  def zb_listAll10k(): Int =
    transactor.connect(allFrag.query[BenchUser].count(u => u.nickname.isDefined || u.bio.isDefined))

  @Benchmark
  def kyo_listAll10k(): Int =
    eval(
      DB.run(kyoClient)(
        Sql.from[BenchUser].run.map(_.count(u => u.nickname.isDefined || u.bio.isDefined))
      )
    )

  // ---- large export, bounded memory ----
  // NOTE: kyo-sql RC6 has no typed-DSL streaming terminal, and its raw
  // `sql"...".stream` interpolator fails to compile under Scala 3.8.3
  // (macro position assertion), so only zio-blocks' chunked export is timed;
  // kyo's closest equivalent is the fully-materializing `kyo_listAll10k`.

  @Benchmark
  def zb_exportChunked10k(): Int =
    transactor.connect {
      allFrag.queryChunked[BenchUser](64).runFold(0)((acc, chunk) => acc + chunk.size) match {
        case Right(n)  => n
        case Left(err) => throw err
      }
    }

  // ---- bulk write: clear scratch table + multi-row insert of 100 ----

  private def nextBulkRows(): Vector[BenchUser] =
    Vector.tabulate(100) { i =>
      BenchUser(
        i,
        "bulk" + i,
        s"bulk$i@example.com",
        30,
        true,
        if (i % 3 == 0) None else Some("n"),
        if (i % 5 == 0) None else Some("b")
      )
    }

  @Benchmark
  def zb_bulkInsert100(): Int = {
    transactor.connect(Frag.literal("DELETE FROM bulkuser").update)
    val rows = nextBulkRows()
    transactor.connect {
      (sql"INSERT INTO bulkuser (id, name, email, age, active, nickname, bio) VALUES " ++ Frag.values(rows)).update
    }
    rows.size
  }

  // kyo-sql's typed insert.values() is an inline macro requiring literal rows,
  // so runtime-built batches degrade to one statement per row.
  private def kyoInsertAll(rs: Vector[BulkUser]): Unit < (DB & Async & Abort[SqlException] & Scope) = {
    val inserts = rs.map(r => Sql.insert[BulkUser].values(r).run)
    Sql.delete[BulkUser].build.run.flatMap(_ => inserts.reduceLeft((acc, p) => acc.flatMap(_ => p)).unit)
  }

  private def nextKyoBulkRows(): Vector[BulkUser] =
    Vector.tabulate(100) { i =>
      BulkUser(
        i,
        "bulk" + i,
        s"bulk$i@example.com",
        30,
        true,
        if (i % 3 == 0) None else Some("n"),
        if (i % 5 == 0) None else Some("b")
      )
    }

  @Benchmark
  def kyo_bulkInsert100(): Int = {
    val rows = nextKyoBulkRows()
    eval(DB.run(kyoClient)(kyoInsertAll(rows)))
    rows.size
  }
}
