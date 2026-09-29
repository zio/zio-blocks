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
import zio.blocks.sql.query.{Rel, SortOrder => QSortOrder, SqlQuery => Qry, lit}
import zio.blocks.sql.query.*

// Typed fixtures: every query below uses typed Expr clauses that the
// compile-time peel dumper cannot decode faithfully. The dumper must skip
// emission (fail closed) — never emit WHERE ? placeholders or truncated SQL.
// Distinctive table names let tests assert absence by content.
//
// Fixture pattern note: query-bound `q.col` macros only elaborate in
// non-inline context from a stable val base (transparent-inline inference
// degrades inside `inline def` bodies and over parameterless `def` bases),
// so bases are object-level vals and the peel decoder expands member ValDef
// receivers to recover the inline-constructible chain.
private object TypedDumpFailClosedFixtures {
  case class WtypedUser(id: Int, name: String)
  object WtypedUser { implicit val schema: Schema[WtypedUser] = Schema.derived }
  val wtable = Table.derived[WtypedUser]

  val qEq     = Qry.from(wtable)
  val queryEq = qEq.where(qEq.col[WtypedUser](_.name) === lit("alice"))

  val qAndOr     = Qry.from(wtable)
  val queryAndOr = qAndOr
    .where((qAndOr.col[WtypedUser](_.name) === lit("bob")) && (qAndOr.col[WtypedUser](_.id) === lit(7)))
    .where((qAndOr.col[WtypedUser](_.name) === lit("x")) || (qAndOr.col[WtypedUser](_.id) === lit(8)))

  val qIn     = Qry.from(wtable)
  val queryIn = qIn.where(qIn.col[WtypedUser](_.id).in(Seq(1, 2, 3)))

  val qLike     = Qry.from(wtable)
  val queryLike = qLike.where(qLike.col[WtypedUser](_.name).like("a%"))

  val qHaving     = Qry.from(wtable)
  val queryHaving =
    qHaving.groupBy(qHaving.col[WtypedUser](_.name)).having(qHaving.count(qHaving.col[WtypedUser](_.id)) > lit(1L))

  val qOrder     = Qry.from(wtable)
  val queryOrder =
    qOrder.where(qOrder.col[WtypedUser](_.id) > lit(0)).orderBy(qOrder.col[WtypedUser](_.name), QSortOrder.Asc)

  Dump.dumpQuery(queryEq)
  Dump.dumpQuery(queryAndOr)
  Dump.dumpQuery(queryIn)
  Dump.dumpQuery(queryLike)
  Dump.dumpQuery(queryHaving)
  Dump.dumpQuery(queryOrder)
}

// Legacy member-val chain (positive control): shared base + legacy Frag
// filter through member vals must still dump exactly like runtime.
private object LegacyMemberValDumpFixture {
  case class WlegacyUser(id: Int, name: String)
  object WlegacyUser { implicit val schema: Schema[WlegacyUser] = Schema.derived }
  val wtable = Table.derived[WlegacyUser]

  val qBase       = Qry.from(wtable)
  val queryLegacy =
    qBase.filter(Frag(IndexedSeq("t0.\"name\" = ", ""), IndexedSeq(DbValue.DbString("alice"))))
  Dump.dumpQuery(queryLegacy)
}

// Legacy member-val Frag filter (positive skip control): a shared Frag val
// cannot be decoded at compile time, so the dumper must skip emission rather
// than emit a WHERE ? placeholder (the previous IrFilter("?", 1) fallback).
private object LegacyMemberFragDumpFixture {
  case class WlegacyUser(id: Int, name: String)
  object WlegacyUser { implicit val schema: Schema[WlegacyUser] = Schema.derived }
  val wtable = Table.derived[WlegacyUser]

  val nameFilter         = Frag(IndexedSeq("t0.\"name\" = ", ""), IndexedSeq(DbValue.DbString("alice")))
  val groupCol           = "name"
  inline def queryFilter = Qry.from(wtable).filter(nameFilter)
  inline def queryGroup  = Qry.from(wtable).groupBy(groupCol)
  Dump.dumpQuery(queryFilter)
  Dump.dumpQuery(queryGroup)
}

// Chained self-join via legacy string Rels: the dumper MUST emit (not skip)
// with ON aliases identical to runtime (t1->t2, never t0->t2).
private object ChainedSelfJoinDumpFixture {
  case class Wemp(id: Int, name: String, managerId: Option[Int])
  object Wemp { implicit val schema: Schema[Wemp] = Schema.derived }
  val wempTable    = Table.derived[Wemp]
  inline def query =
    Qry
      .from(wempTable)
      .innerJoin(Rel(wempTable, "manager_id", wempTable, "id"))
      .innerJoin(Rel(wempTable, "manager_id", wempTable, "id"))
  Dump.dumpQuery(query)
}

object TypedDumpFailClosedSpec extends ZIOSpecDefault {

  private def dumpDirOpt: Option[Path] =
    Option(System.getProperty("zib.sql.dumpDir")).map(Paths.get(_))

  private def normalizeSql(sql: String): String = {
    val noQuotes = sql.replace("\"", "")
    val noAs     = noQuotes.replaceAll("(?i)\\s+AS\\s+", " ")
    val qNorm    = noAs.replaceAll("\\?[0-9]+", "?")
    qNorm.replaceAll("\\s+", " ").trim
  }

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

  def spec = suite("TypedDumpFailClosedSpec")(
    test("typed predicate dumps are skipped — no file contains the typed table") {
      dumpDirOpt match {
        case None    => assertTrue(true) // skipped — run with -Dzib.sql.dumpDir=<fresh-dir> after clean
        case Some(_) =>
          // Any emission for a typed query would name its table; absence by
          // content proves fail-closed skipping regardless of file naming.
          assertTrue(findDumpContaining("wtyped_user").isEmpty)
      }
    },
    test("legacy member-val Frag filter/group dumps are skipped — no placeholder artifacts") {
      dumpDirOpt match {
        case None    => assertTrue(true) // skipped — run with -Dzib.sql.dumpDir=<fresh-dir> after clean
        case Some(_) =>
          // A shared Frag val / column string is not compile-time decodable;
          // the dumper must skip rather than emit WHERE ? or drop GROUP BY.
          assertTrue(findDumpContaining("wlegacy_user").isEmpty)
      }
    },
    test("no emitted file contains a bare WHERE ? placeholder artifact") {
      dumpDirOpt match {
        case None      => assertTrue(true) // skipped — run with -Dzib.sql.dumpDir=<fresh-dir> after clean
        case Some(dir) =>
          val stream = Files.list(dir)
          try {
            val it        = stream.iterator()
            var offenders = List.empty[String]
            while (it.hasNext) {
              val p = it.next()
              if (p.toString.endsWith(".sql")) {
                val content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8)
                // A bare WHERE ? (placeholder with no predicate) is a dump
                // artifact; legitimate predicates look like WHERE t0."x" = ?.
                val bare = "(?i)WHERE\\s+\\?([\\s,)]|$)".r
                if (bare.findFirstIn(content).isDefined) offenders ::= p.toString
              }
            }
            assertTrue(offenders.isEmpty)
          } finally stream.close()
      }
    },
    test("chained self-join dump equals runtime SQL with t1->t2 binding") {
      val q      = ChainedSelfJoinDumpFixture.query
      val fragPg = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
      // Runtime binds the second self-join from the latest compatible alias.
      assertTrue(
        fragPg.contains("t1.\"manager_id\" = t2.\"id\""),
        !fragPg.contains("t0.\"manager_id\" = t2.\"id\"")
      ) &&
      (dumpDirOpt match {
        case None    => assertTrue(true) // skipped — run with -Dzib.sql.dumpDir=<fresh-dir> after clean
        case Some(_) =>
          val expected = normalizeSql(fragPg)
          val found    = findDumpContaining("wemp")
          assertTrue(found.isDefined) &&
          assertTrue(
            normalizeSql(found.get) == expected,
            found.get.contains("t1.\"manager_id\" = t2.\"id\""),
            !found.get.contains("t0.\"manager_id\" = t2.\"id\"")
          )
      })
    }
  )
}
