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
import java.nio.file.{Files, Path, Paths}

import zio.test.*
import zio.blocks.schema.Schema
import zio.blocks.sql.query.{SortOrder => QSortOrder, SqlQuery => Qry, Rel}

// Separate fixture objects ensure distinct dump fileBase per query (owner-derived).
private object Legacy2JoinFixture {
  case class User(id: Int, name: String)
  object User { implicit val schema: Schema[User] = Schema.derived }
  case class Repo(id: Int, ownerId: Int, name: String)
  object Repo { implicit val schema: Schema[Repo] = Schema.derived }
  case class Star(userId: Int, repoId: Int)
  object Star { implicit val schema: Schema[Star] = Schema.derived }
  val userTable                    = Table.derived[User]
  val repoTable                    = Table.derived[Repo]
  val starTable                    = Table.derived[Star]
  inline def query: SqlQuery[User] =
    SqlQuery
      .from(userTable)
      .join(repoTable, leftColumn = "id", rightColumn = "owner_id")
      .join(starTable, leftColumn = "id", rightColumn = "repo_id")
      .where(userTable, "name", DbValue.DbString("alice"))
      .where(repoTable, "name", DbValue.DbString("my-repo"))
  Dump.dump(query)
}
private object LegacyFullFixture {
  case class User(id: Int, name: String)
  object User { implicit val schema: Schema[User] = Schema.derived }
  case class Repo(id: Int, ownerId: Int, name: String)
  object Repo { implicit val schema: Schema[Repo] = Schema.derived }
  val userTable                    = Table.derived[User]
  val repoTable                    = Table.derived[Repo]
  inline def query: SqlQuery[User] =
    SqlQuery
      .from(userTable)
      .join(repoTable, leftColumn = "id", rightColumn = "owner_id")
      .where(userTable, "name", DbValue.DbString("bob"))
      .groupBy(userTable, "id")
      .orderBy(userTable, "id", SqlStatement.OrderDirection.Asc)
      .orderBy(repoTable, "name", SqlStatement.OrderDirection.Desc)
      .limit(10)
      .offset(5)
  Dump.dump(query)
}
private object FourArgFixture {
  case class User(id: Int, name: String)
  object User { implicit val schema: Schema[User] = Schema.derived }
  val userTable                    = Table.derived[User]
  inline def query: SqlQuery[User] =
    SqlQuery.from(userTable).where(userTable, "name", "=", DbValue.DbString("alice"))
  Dump.dump(query)
}
private object InQueryFixture {
  case class User(id: Int, name: String)
  object User { implicit val schema: Schema[User] = Schema.derived }
  val userTable                    = Table.derived[User]
  inline def query: SqlQuery[User] =
    SqlQuery
      .from(userTable)
      .where(SqlStatement.ColumnRef("t0", "id"), "IN", DbValue.DbArray("integer", IndexedSeq(1, 2, 3)))
  Dump.dump(query)
}
private object IrFullFixture {
  case class User(id: Int, name: String)
  object User { implicit val schema: Schema[User] = Schema.derived }
  case class Repo(id: Int, ownerId: Int, name: String)
  object Repo { implicit val schema: Schema[Repo] = Schema.derived }
  case class Star(userId: Int, repoId: Int)
  object Star { implicit val schema: Schema[Star] = Schema.derived }
  val userTable               = Table.derived[User]
  val repoTable               = Table.derived[Repo]
  val starTable               = Table.derived[Star]
  inline def query: Qry[User] =
    Qry
      .from(userTable)
      .innerJoin(Rel(repoTable, "owner_id", userTable, "id"))
      .innerJoin(Rel(starTable, "repo_id", repoTable, "id"))
      .filter(Frag(IndexedSeq("t0.\"name\" = ", ""), IndexedSeq(DbValue.DbString("alice"))))
      .groupBy("name")
      .orderBy("name", QSortOrder.Asc)
      .limit(10)
      .offset(5)
  Dump.dumpQuery(query)
}

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
  private def dumpDirOpt: Option[Path] =
    Option(System.getProperty("zib.sql.dumpDir")).map(Paths.get(_))

  private def findDumpContaining(fragment: String): Option[String] =
    dumpDirOpt.flatMap { dir =>
      if (!Files.exists(dir)) None
      else {
        val stream = Files.list(dir)
        try {
          val it                    = stream.iterator()
          var found: Option[String] = None
          while (it.hasNext && found.isEmpty) {
            val p = it.next()
            if (p.toString.endsWith(".sql")) {
              val content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8)
              if (normalizeSql(content).contains(normalizeSql(fragment))) found = Some(content)
            }
          }
          found
        } finally stream.close()
      }
    }

  private def readDumpByBase(base: String, dialect: SqlDialect): Option[String] =
    dumpDirOpt.flatMap { dir =>
      val safe   = base.replaceAll("[^A-Za-z0-9_]", "_")
      val suffix = dialect.name.toLowerCase(java.util.Locale.ROOT)
      val file   = dir.resolve(s"$safe-$suffix.sql")
      if (Files.exists(file)) Some(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim)
      else None
    }

  def spec = suite("ExplainDumpGoldenSpec")(
    test("legacy 2-join with filters dump equals explain normalized (macro file)") {
      val q           = Legacy2JoinFixture.query
      val fragPg      = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      val explainBody = normalizeExplainBody(q.explain(SqlDialect.PostgreSQL))
      dumpDirOpt match {
        case None =>
          assertTrue(normalizeSql(fragPg) == explainBody, fragPg.contains("FROM user"))
        case Some(_) =>
          // Find any dump file containing the expected join pattern — proves macro emitted correct SQL, not source-only
          val expected = normalizeSql(fragPg)
          val found    = findDumpContaining(expected)
          assertTrue(found.isDefined) &&
          assertTrue(
            normalizeSql(found.get) == expected,
            normalizeSql(found.get) == explainBody,
            found.get.contains("FROM user") || normalizeSql(found.get).contains("FROM user"),
            found.get.contains("INNER JOIN") || normalizeSql(found.get).contains("INNER JOIN"),
            found.get.contains("WHERE") || normalizeSql(found.get).contains("WHERE")
          )
      }
    },
    test("legacy with groupBy, orderBy, limit/offset dump equals explain normalized (macro file)") {
      val q           = LegacyFullFixture.query
      val fragPg      = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      val explainBody = normalizeExplainBody(q.explain(SqlDialect.PostgreSQL))
      dumpDirOpt match {
        case None =>
          assertTrue(normalizeSql(fragPg) == explainBody, fragPg.contains("GROUP BY"))
        case Some(_) =>
          val expected = normalizeSql(fragPg)
          val found    = findDumpContaining(expected)
          assertTrue(found.isDefined) &&
          assertTrue(
            normalizeSql(found.get) == expected,
            normalizeSql(found.get) == explainBody,
            found.get.contains("GROUP BY") || normalizeSql(found.get).contains("GROUP BY"),
            found.get.contains("ORDER BY") || normalizeSql(found.get).contains("ORDER BY"),
            found.get.contains("LIMIT 10"),
            found.get.contains("OFFSET 5")
          )
      }
    },
    test("four-arg where (table, column, operator, value) dump equals explain normalized") {
      val q           = FourArgFixture.query
      val fragPg      = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      val explainBody = normalizeExplainBody(q.explain(SqlDialect.PostgreSQL))
      dumpDirOpt match {
        case None =>
          assertTrue(normalizeSql(fragPg) == explainBody, fragPg.contains("WHERE"))
        case Some(_) =>
          val expected = normalizeSql(fragPg)
          val found    = findDumpContaining(expected)
          assertTrue(found.isDefined) &&
          assertTrue(
            normalizeSql(found.get) == expected,
            normalizeSql(found.get) == explainBody,
            found.get.contains("WHERE") || normalizeSql(found.get).contains("WHERE"),
            !found.get.contains("alice")
          )
      }
    },
    test("IN operator produces IN (?, ?, ?) list syntax and dump matches runtime") {
      val q           = InQueryFixture.query
      val fragPg      = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      val explainBody = normalizeExplainBody(q.explain(SqlDialect.PostgreSQL))
      assertTrue(
        fragPg.contains("IN (?, ?, ?)"),
        normalizeSql(fragPg).contains("IN (?, ?, ?)"),
        normalizeSql(explainBody).contains("IN (?, ?, ?)")
      ) &&
      (dumpDirOpt match {
        case None    => assertTrue(true)
        case Some(_) =>
          val expected = normalizeSql(fragPg)
          val found    = findDumpContaining("IN (?, ?, ?)")
          assertTrue(found.isDefined) &&
          assertTrue(
            normalizeSql(found.get).contains("IN (?, ?, ?)"),
            !normalizeSql(found.get).contains("IN ?\"") && !found.get.contains("IN ? "),
            normalizeSql(found.get) == expected
          )
      })
    },
    test("IR 2-join with filters, groupBy, orderBy, limit/offset via dumpQuery equals runtime sql normalized") {
      val q      = IrFullFixture.query
      val fragPg = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      dumpDirOpt match {
        case None =>
          assertTrue(normalizeSql(fragPg).contains("INNER JOIN"), fragPg.contains("WHERE"))
        case Some(_) =>
          val expected = normalizeSql(fragPg)
          val found    = findDumpContaining(expected)
          assertTrue(found.isDefined) &&
          assertTrue(
            normalizeSql(found.get) == expected,
            found.get.contains("INNER JOIN") || normalizeSql(found.get).contains("INNER JOIN"),
            found.get.contains("WHERE") || normalizeSql(found.get).contains("WHERE"),
            found.get.contains("GROUP BY") || normalizeSql(found.get).contains("GROUP BY"),
            found.get.contains("ORDER BY") || normalizeSql(found.get).contains("ORDER BY"),
            found.get.contains("LIMIT 10"),
            found.get.contains("OFFSET 5"),
            !found.get.contains("alice")
          )
      }
    },
    test("tableAlias validation rejects invalid alias") {
      val badRef = SqlStatement.ColumnRef("bad-alias!", "id")
      val result = try {
        SqlQuery.from(userTable).where(badRef, "=", DbValue.DbInt(1))
        false
      } catch {
        case _: IllegalArgumentException => true
        case _: Throwable                => false
      }
      assertTrue(result)
    }
  )
}
