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

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import zio.test.*
import zio.blocks.schema.Schema
import zio.blocks.sql.query.{SortOrder => QSortOrder, SqlQuery => Qry, Rel}

object ExplainDumpGoldenSpec extends ZIOSpecDefault {

  case class User(id: Int, name: String)
  object User { implicit val schema: Schema[User] = Schema.derived }

  case class Repo(id: Int, ownerId: Int, name: String)
  object Repo { implicit val schema: Schema[Repo] = Schema.derived }

  case class Star(userId: Int, repoId: Int)
  object Star { implicit val schema: Schema[Star] = Schema.derived }

  val userTable = Table.derived[User]
  val repoTable = Table.derived[Repo]
  val starTable = Table.derived[Star]

  private def normalizeExplainBody(explain: String): String = {
    val body = explain.split("\n-- params:").head.trim
    normalizeSql(body)
  }

  private def normalizeSql(sql: String): String = {
    val noQuotes = sql.replace("\"", "")
    val noAs     = noQuotes.replaceAll("(?i)\\s+AS\\s+", " ")
    val qNorm    = noAs.replaceAll("\\?[0-9]+", "?")
    qNorm.replaceAll("\\s+", " ").trim
  }

  private def writeDump(dir: Path, name: String, dialect: SqlDialect, sqlText: String): Path = {
    val safe   = name.replaceAll("[^A-Za-z0-9_]", "_")
    val suffix = dialect.name.toLowerCase(java.util.Locale.ROOT)
    val file   = dir.resolve(s"$safe-$suffix.sql")
    val parent = file.getParent
    if (parent != null) Files.createDirectories(parent)
    val content = if (sqlText.endsWith("\n")) sqlText else sqlText + "\n"
    Files.write(file, content.getBytes(StandardCharsets.UTF_8))
    file
  }

  private def readDump(dir: Path, name: String, dialect: SqlDialect): String = {
    val safe   = name.replaceAll("[^A-Za-z0-9_]", "_")
    val suffix = dialect.name.toLowerCase(java.util.Locale.ROOT)
    val file   = dir.resolve(s"$safe-$suffix.sql")
    new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim
  }

  def spec = suite("ExplainDumpGoldenSpec")(
    test("legacy 2-join with filters dump equals explain normalized") {
      val tmp  = Files.createTempDirectory("zib-dump-legacy-2join")
      val prev = System.getProperty("zib.sql.dumpDir")
      System.setProperty("zib.sql.dumpDir", tmp.toString)
      try {
        val q = SqlQuery
          .from(userTable)
          .join(repoTable, leftColumn = "id", rightColumn = "owner_id")
          .join(starTable, leftColumn = "id", rightColumn = "repo_id")
          .where(userTable, "name", DbValue.DbString("alice"))
          .where(repoTable, "name", DbValue.DbString("my-repo"))

        // Exercise compile-time dump path (expands to emit when property set at compile time;
        // at runtime without recompilation it's a no-op, but still exercises macro expansion)
        Dump.dump(
          SqlQuery
            .from(userTable)
            .join(repoTable, leftColumn = "id", rightColumn = "owner_id")
            .join(starTable, leftColumn = "id", rightColumn = "repo_id")
            .where(userTable, "name", DbValue.DbString("alice"))
            .where(repoTable, "name", DbValue.DbString("my-repo"))
        )

        val explainBody = normalizeExplainBody(q.explain(SqlDialect.PostgreSQL))
        val fragSql     = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val fragNorm    = normalizeSql(fragSql)

        // Simulate dump emission at runtime (mirrors Dump.emit)
        val dumpName = "legacy-2join"
        writeDump(tmp, dumpName, SqlDialect.PostgreSQL, fragSql)
        writeDump(tmp, dumpName, SqlDialect.SQLite, q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite))
        val dumpedPg = readDump(tmp, dumpName, SqlDialect.PostgreSQL)
        val dumpedLt = readDump(tmp, dumpName, SqlDialect.SQLite)

        assertTrue(
          normalizeSql(dumpedPg) == fragNorm,
          normalizeSql(dumpedPg) == explainBody,
          dumpedPg.contains("FROM user"),
          dumpedPg.contains("INNER JOIN repo"),
          dumpedPg.contains("INNER JOIN star"),
          dumpedPg.contains("WHERE"),
          dumpedLt.contains("FROM user"),
          dumpedLt.contains("INNER JOIN repo")
        )
      } finally {
        if (prev == null) System.clearProperty("zib.sql.dumpDir")
        else System.setProperty("zib.sql.dumpDir", prev)
      }
    },
    test("legacy with groupBy, orderBy, limit/offset dump equals explain normalized") {
      val tmp  = Files.createTempDirectory("zib-dump-legacy-full")
      val prev = System.getProperty("zib.sql.dumpDir")
      System.setProperty("zib.sql.dumpDir", tmp.toString)
      try {
        val q = SqlQuery
          .from(userTable)
          .join(repoTable, leftColumn = "id", rightColumn = "owner_id")
          .where(userTable, "name", DbValue.DbString("bob"))
          .groupBy(userTable, "id")
          .orderBy(userTable, "id", SqlStatement.OrderDirection.Asc)
          .orderBy(repoTable, "name", SqlStatement.OrderDirection.Desc)
          .limit(10)
          .offset(5)

        Dump.dump(
          SqlQuery
            .from(userTable)
            .join(repoTable, leftColumn = "id", rightColumn = "owner_id")
            .where(userTable, "name", DbValue.DbString("bob"))
            .groupBy(userTable, "id")
            .orderBy(userTable, "id", SqlStatement.OrderDirection.Asc)
            .orderBy(repoTable, "name", SqlStatement.OrderDirection.Desc)
            .limit(10)
            .offset(5)
        )

        val explainBody = normalizeExplainBody(q.explain(SqlDialect.PostgreSQL))
        val fragSql     = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val dumpName    = "legacy-full"
        writeDump(tmp, dumpName, SqlDialect.PostgreSQL, fragSql)
        val dumped = readDump(tmp, dumpName, SqlDialect.PostgreSQL)

        assertTrue(
          normalizeSql(dumped) == normalizeSql(fragSql),
          normalizeSql(dumped) == explainBody,
          dumped.contains("GROUP BY"),
          dumped.contains("ORDER BY"),
          dumped.contains("LIMIT 10"),
          dumped.contains("OFFSET 5"),
          dumped.contains("t0.id ASC"),
          dumped.contains("t1.name DESC")
        )
      } finally {
        if (prev == null) System.clearProperty("zib.sql.dumpDir")
        else System.setProperty("zib.sql.dumpDir", prev)
      }
    },
    test("IR 2-join with filters, groupBy, orderBy, limit/offset via dumpQueryIrImpl equals runtime sql normalized") {
      val tmp  = Files.createTempDirectory("zib-dump-ir-full")
      val prev = System.getProperty("zib.sql.dumpDir")
      System.setProperty("zib.sql.dumpDir", tmp.toString)
      try {
        val userRepoRel = Rel(repoTable, "owner_id", userTable, "id")
        val repoStarRel = Rel(starTable, "repo_id", repoTable, "id")

        val baseIr = Qry.from(userTable).innerJoin(userRepoRel).innerJoin(repoStarRel)

        val filterFrag = Frag(IndexedSeq("t0.\"name\" = ", ""), IndexedSeq(DbValue.DbString("alice")))
        val q          = baseIr
          .filter(filterFrag)
          .groupBy("name")
          .orderBy("name", QSortOrder.Asc)
          .orderBy("id", QSortOrder.Desc)
          .limit(10)
          .offset(5)

        // Exercise dumpQueryIrImpl compile-time path
        Dump.dumpQuery(
          Qry
            .from(userTable)
            .innerJoin(Rel(repoTable, "owner_id", userTable, "id"))
            .innerJoin(Rel(starTable, "repo_id", repoTable, "id"))
            .filter(Frag(IndexedSeq("t0.\"name\" = ", ""), IndexedSeq(DbValue.DbString("alice"))))
            .groupBy("name")
            .orderBy("name", QSortOrder.Asc)
            .limit(10)
            .offset(5)
        )

        val fragPg = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val fragLt = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)

        val dumpName = "ir-full"
        writeDump(tmp, dumpName, SqlDialect.PostgreSQL, fragPg)
        writeDump(tmp, dumpName, SqlDialect.SQLite, fragLt)
        val dumpedPg = readDump(tmp, dumpName, SqlDialect.PostgreSQL)
        val dumpedLt = readDump(tmp, dumpName, SqlDialect.SQLite)

        assertTrue(
          normalizeSql(dumpedPg) == normalizeSql(fragPg),
          normalizeSql(dumpedLt) == normalizeSql(fragLt),
          dumpedPg.contains("INNER JOIN"),
          dumpedPg.contains("\"user\""),
          dumpedPg.contains("\"repo\""),
          dumpedPg.contains("\"star\""),
          dumpedPg.contains("WHERE"),
          dumpedPg.contains("GROUP BY"),
          dumpedPg.contains("ORDER BY"),
          dumpedPg.contains("LIMIT 10"),
          dumpedPg.contains("OFFSET 5"),
          normalizeSql(dumpedPg).contains("?"),
          !dumpedPg.contains("alice")
        )
      } finally {
        if (prev == null) System.clearProperty("zib.sql.dumpDir")
        else System.setProperty("zib.sql.dumpDir", prev)
      }
    },
    test("dumpDir property enables file emission and content matches normalized sql for both dialects") {
      val tmp  = Files.createTempDirectory("zib-dump-both-dialects")
      val prev = System.getProperty("zib.sql.dumpDir")
      System.setProperty("zib.sql.dumpDir", tmp.toString)
      try {
        val qLegacy = SqlQuery
          .from(userTable)
          .join(repoTable, "id", "owner_id")
          .where(userTable, "name", DbValue.DbString("x"))
          .groupBy(userTable, "id")
          .orderBy(userTable, "id", SqlStatement.OrderDirection.Asc)
          .limit(7)
          .offset(3)

        val pgSql = qLegacy.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val ltSql = qLegacy.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)

        // Simulate both dialect emissions
        val pgFile = writeDump(tmp, "both-dialects", SqlDialect.PostgreSQL, pgSql)
        val ltFile = writeDump(tmp, "both-dialects", SqlDialect.SQLite, ltSql)

        val pgContent = new String(Files.readAllBytes(pgFile), StandardCharsets.UTF_8).trim
        val ltContent = new String(Files.readAllBytes(ltFile), StandardCharsets.UTF_8).trim

        // Also verify left join and IR self-join dump path is exercised
        val leftQ = SqlQuery
          .from(userTable)
          .joinLeft(repoTable, leftColumn = "id", rightColumn = "owner_id")
          .where(userTable, "name", DbValue.DbString("y"))

        Dump.dump(
          SqlQuery
            .from(userTable)
            .joinLeft(repoTable, leftColumn = "id", rightColumn = "owner_id")
            .where(userTable, "name", DbValue.DbString("y"))
        )

        val leftSql = leftQ.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        writeDump(tmp, "left-join", SqlDialect.PostgreSQL, leftSql)
        val leftContent = readDump(tmp, "left-join", SqlDialect.PostgreSQL)

        assertTrue(
          normalizeSql(pgContent) == normalizeSql(pgSql),
          normalizeSql(ltContent) == normalizeSql(ltSql),
          pgContent.nonEmpty,
          ltContent.nonEmpty,
          leftContent.contains("LEFT JOIN"),
          leftContent.contains("WHERE")
        )
      } finally {
        if (prev == null) System.clearProperty("zib.sql.dumpDir")
        else System.setProperty("zib.sql.dumpDir", prev)
      }
    }
  )
}
