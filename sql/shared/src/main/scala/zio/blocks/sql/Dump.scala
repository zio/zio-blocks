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

import scala.quoted._
import zio.blocks.schema.Schema

/**
 * Compile-time SQL dump emission.
 *
 * When the JVM system property `zib.sql.dumpDir` is set, inline macro entry
 * points [[dumpTable]], [[dump]] and [[dumpQuery]] emit `.sql` files to that
 * directory at compile time. When the property is absent the calls expand to
 * `()` with zero runtime cost. Files are named `<owner>-<dialect>.sql` (owner
 * derived from the enclosing symbol or query/table name), written as UTF-8 with
 * a trailing newline, and skipped when the existing content is byte-identical
 * (content-hash skip).
 */
object Dump {

  def emitRuntime(name: String, dialect: SqlDialect, sqlText: String): Unit = {
    val dirProp = System.getProperty("zib.sql.dumpDir")
    if (dirProp == null) return
    try {
      val safe     = sanitize(name)
      val suffix   = dialect.name.toLowerCase(java.util.Locale.ROOT)
      val content  = if (sqlText.endsWith("\n")) sqlText else sqlText + "\n"
      val dirPath  = java.nio.file.Paths.get(dirProp)
      val bytes    = content.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      val fileName = s"$safe-$suffix.sql"
      val filePath = dirPath.resolve(fileName)
      val parent   = filePath.getParent
      if (parent != null) java.nio.file.Files.createDirectories(parent)
      def isUpToDate: Boolean =
        if (!java.nio.file.Files.exists(filePath)) false
        else java.util.Arrays.equals(java.nio.file.Files.readAllBytes(filePath), bytes)
      if (isUpToDate) ()
      else
        java.nio.file.Files.write(
          filePath,
          bytes,
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
          java.nio.file.StandardOpenOption.WRITE
        )
    } catch {
      case _: Throwable => ()
    }
  }

  private[sql] def emit(name: String, dialect: SqlDialect, sqlText: String)(using Quotes): Unit = {
    import quotes.reflect._
    val dirProp = System.getProperty("zib.sql.dumpDir")
    if (dirProp == null) return
    try {
      val safe                              = sanitize(name)
      val suffix                            = dialect.name.toLowerCase(java.util.Locale.ROOT)
      val content                           = if (sqlText.endsWith("\n")) sqlText else sqlText + "\n"
      val dirPath                           = java.nio.file.Paths.get(dirProp)
      val bytes                             = content.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      def writeFile(fileName: String): Unit = {
        val filePath = dirPath.resolve(fileName)
        val parent   = filePath.getParent
        if (parent != null) java.nio.file.Files.createDirectories(parent)
        java.nio.file.Files.write(
          filePath,
          bytes,
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
          java.nio.file.StandardOpenOption.WRITE
        )
      }
      def isUpToDate(fileName: String): Boolean = {
        val filePath = dirPath.resolve(fileName)
        if (!java.nio.file.Files.exists(filePath)) false
        else java.util.Arrays.equals(java.nio.file.Files.readAllBytes(filePath), bytes)
      }
      val primary = s"$safe-$suffix.sql"
      if (isUpToDate(primary)) ()
      else writeFile(primary)
    } catch {
      case e: Throwable =>
        report.warning(
          s"[zib.sql.dumpDir] Failed to dump $name/${dialect.name}: ${e.getMessage}",
          Position.ofMacroExpansion
        )
    }
  }

  private[sql] def ownerDerivedName(using quotes: Quotes): String = {
    import quotes.reflect._
    var sym = Symbol.spliceOwner
    while (
      sym != Symbol.noSymbol && (sym.flags
        .is(Flags.Synthetic) || sym.name == "<init>" || sym.name.startsWith("$anon") || sym.name == "$package")
    ) {
      sym = sym.owner
    }
    if (sym == Symbol.noSymbol) "query"
    else {
      val full      = sym.fullName
      val last      = if (full.contains(".")) full.split("\\.").last else full
      val candidate = if (last.nonEmpty && last != "<init>") last else sym.name
      if (candidate == null || candidate.isEmpty || candidate == "<none>") "query"
      else candidate
    }
  }

  private def queryArgDerivedName(term: Any)(using quotes: Quotes): Option[String] = {
    import quotes.reflect._
    def loop(t: Term): Option[String] = t match {
      case Ident(name) if name.nonEmpty && !name.startsWith("$") && name != "x$0" => Some(name)
      case Select(_, name) if name.nonEmpty && !name.startsWith("$")              => Some(name)
      case Inlined(Some(dd: DefDef), _, _)
          if dd.name.nonEmpty && !dd.name.startsWith("$") && dd.name != "x$0" && !Set(
            "from",
            "where",
            "innerJoin",
            "leftJoin",
            "join",
            "leftJoin",
            "groupBy",
            "orderBy",
            "limit",
            "offset",
            "select",
            "filter",
            "having"
          ).contains(dd.name) =>
        Some(dd.name)
      case Inlined(_, _, inner) => loop(inner)
      case Block(_, inner)      => loop(inner)
      case Typed(inner, _)      => loop(inner)
      case Apply(fun, _)        => loop(fun)
      case TypeApply(fun, _)    => loop(fun)
      case _                    => None
    }
    term match {
      case tt: Term @unchecked => loop(tt)
      case _                   => None
    }
  }

  private def sanitize(name: String): String =
    try SqlIdentifier.validate("table", name)
    catch {
      case _: IllegalArgumentException =>
        val s = name.replaceAll("[^A-Za-z0-9_]", "_")
        if (s.isEmpty) "_"
        else if (s.matches("^[A-Za-z_].*")) s
        else "_" + s
    }

  // ---- shared helpers (dedup) ----
  private def dbValueFor(using quotes: Quotes)(tpe: quotes.reflect.TypeRepr): DbValue = {
    import quotes.reflect._
    if (tpe =:= TypeRepr.of[Int]) DbValue.DbInt(0)
    else if (tpe =:= TypeRepr.of[Long]) DbValue.DbLong(0L)
    else if (tpe =:= TypeRepr.of[String]) DbValue.DbString("")
    else if (tpe =:= TypeRepr.of[Boolean]) DbValue.DbBoolean(false)
    else if (tpe =:= TypeRepr.of[Short]) DbValue.DbShort(0)
    else if (tpe =:= TypeRepr.of[Byte]) DbValue.DbByte(0)
    else if (tpe =:= TypeRepr.of[Float]) DbValue.DbFloat(0f)
    else if (tpe =:= TypeRepr.of[Double]) DbValue.DbDouble(0d)
    else if (tpe =:= TypeRepr.of[Char]) DbValue.DbChar(' ')
    else if (tpe =:= TypeRepr.of[BigDecimal]) DbValue.DbBigDecimal(BigDecimal(0))
    else if (tpe =:= TypeRepr.of[Array[Byte]]) DbValue.DbBytes(Array.emptyByteArray)
    else if (tpe =:= TypeRepr.of[java.time.LocalDate]) DbValue.DbLocalDate(java.time.LocalDate.ofEpochDay(0))
    else if (tpe =:= TypeRepr.of[java.time.LocalDateTime])
      DbValue.DbLocalDateTime(java.time.LocalDateTime.ofEpochSecond(0, 0, java.time.ZoneOffset.UTC))
    else if (tpe =:= TypeRepr.of[java.time.LocalTime]) DbValue.DbLocalTime(java.time.LocalTime.MIDNIGHT)
    else if (tpe =:= TypeRepr.of[java.time.Instant]) DbValue.DbInstant(java.time.Instant.EPOCH)
    else if (tpe =:= TypeRepr.of[java.time.Duration]) DbValue.DbDuration(java.time.Duration.ZERO)
    else if (tpe =:= TypeRepr.of[java.util.UUID]) DbValue.DbUUID(new java.util.UUID(0L, 0L))
    else if (tpe =:= TypeRepr.of[java.math.BigDecimal]) DbValue.DbBigDecimal(BigDecimal(0))
    else {
      val full = tpe.typeSymbol.fullName
      full match {
        case "scala.Int"                         => DbValue.DbInt(0)
        case "scala.Long"                        => DbValue.DbLong(0L)
        case "scala.String" | "java.lang.String" => DbValue.DbString("")
        case "scala.Boolean"                     => DbValue.DbBoolean(false)
        case "scala.Short"                       => DbValue.DbShort(0)
        case "scala.Byte"                        => DbValue.DbByte(0)
        case "scala.Float"                       => DbValue.DbFloat(0f)
        case "scala.Double"                      => DbValue.DbDouble(0d)
        case "scala.Char"                        => DbValue.DbChar(' ')
        case "scala.math.BigDecimal"             => DbValue.DbBigDecimal(BigDecimal(0))
        case "java.time.LocalDate"               => DbValue.DbLocalDate(java.time.LocalDate.ofEpochDay(0))
        case "java.time.LocalDateTime"           =>
          DbValue.DbLocalDateTime(java.time.LocalDateTime.ofEpochSecond(0, 0, java.time.ZoneOffset.UTC))
        case "java.time.LocalTime"                                   => DbValue.DbLocalTime(java.time.LocalTime.MIDNIGHT)
        case "java.time.Instant"                                     => DbValue.DbInstant(java.time.Instant.EPOCH)
        case "java.time.Duration"                                    => DbValue.DbDuration(java.time.Duration.ZERO)
        case "java.util.UUID"                                        => DbValue.DbUUID(new java.util.UUID(0L, 0L))
        case _ if tpe.typeSymbol.flags.is(quotes.reflect.Flags.Enum) => DbValue.DbString("")
        case _                                                       => DbValue.DbString("")
      }
    }
  }

  private def dbValueAndNullable(using quotes: Quotes)(tpe: quotes.reflect.TypeRepr): (DbValue, Boolean) = {
    import quotes.reflect._
    val optSym   = TypeRepr.of[Option[Int]].typeSymbol
    val maybeSym =
      try TypeRepr.of[zio.blocks.maybe.Maybe[Int]].typeSymbol
      catch { case _: Throwable => null }
    def isOpt: Boolean = tpe match {
      case AppliedType(tycon, _) => tycon.typeSymbol == optSym
      case _                     => false
    }
    def isMaybe: Boolean =
      if (maybeSym == null) false
      else
        tpe match {
          case AppliedType(tycon, _) => tycon.typeSymbol == maybeSym
          case _                     => false
        }
    if (isOpt || isMaybe) {
      val inner = tpe match {
        case AppliedType(_, List(arg)) => arg
        case _                         => TypeRepr.of[String]
      }
      val (innerVal, _) = dbValueAndNullable(inner)
      (innerVal, true)
    } else (dbValueFor(tpe), false)
  }

  private def deriveColumns(using quotes: Quotes)(tpe: quotes.reflect.TypeRepr): IndexedSeq[ColumnMeta] = {
    import quotes.reflect._
    val sym                  = tpe.typeSymbol
    val fields: List[Symbol] =
      try sym.caseFields
      catch { case _: Throwable => Nil }
    if (fields.nonEmpty) {
      fields.flatMap { f =>
        val fieldName          = f.name
        val colName            = SqlNameMapper.SnakeCase(fieldName)
        val fieldTpe: TypeRepr =
          try tpe.memberType(f)
          catch { case _: Throwable => TypeRepr.of[String] }
        val (dbVal, nullable) = dbValueAndNullable(fieldTpe)
        IndexedSeq(ColumnMeta(colName, dbVal, nullable))
      }.toIndexedSeq
    } else {
      val ctorParams = sym.primaryConstructor.paramSymss.flatten
      if (ctorParams.nonEmpty) {
        ctorParams.flatMap { p =>
          val n = p.name
          if (n.isEmpty || n == "x$0" || n.startsWith("$")) None
          else Some(ColumnMeta(SqlNameMapper.SnakeCase(n), DbValue.DbString(""), false))
        }.toIndexedSeq
      } else IndexedSeq.empty
    }
  }

  private def explicitTableNameFromTerm(using quotes: Quotes)(term: quotes.reflect.Term): Option[String] = {
    import quotes.reflect._
    var explicit: Option[String] = None
    def walk(t: Term): Unit      = t match {
      case Literal(StringConstant(s)) if s.matches("[A-Za-z_][A-Za-z0-9_]*") =>
        if (explicit.isEmpty) explicit = Some(s)
      case Apply(fun, args)     => walk(fun); args.foreach(walk)
      case Select(qual, _)      => walk(qual)
      case TypeApply(fun, _)    => walk(fun)
      case Inlined(_, _, inner) => walk(inner)
      case Block(_, inner)      => walk(inner)
      case Typed(inner, _)      => walk(inner)
      case NamedArg(_, inner)   => walk(inner)
      case _                    =>
    }
    walk(term)
    explicit
  }

  /**
   * Compile-time dump of a [[Table]]'s `CREATE TABLE` DDL.
   *
   * When `-Dzib.sql.dumpDir` is set, emits `<table>-<dialect>.sql` for
   * PostgreSQL and SQLite at the macro-expansion site (owner derived from the
   * table's type name). No-op when the property is absent.
   */
  inline def dumpTable[A](inline table: Table[A])(using
    @scala.annotation.unused schema: Schema[A]
  ): Unit =
    ${ dumpTableImpl[A]('table) }

  /**
   * Compile-time dump of a `zio.blocks.sql.query.SqlQuery`'s `SELECT`
   * statement.
   *
   * When `-Dzib.sql.dumpDir` is set, emits `<owner>-<dialect>.sql` for each
   * dialect at the call site (owner derived from the enclosing symbol/val
   * name). No-op otherwise. This is the sole public dump entry for the typed
   * relational IR; the older `zio.blocks.sql.SqlQuery` builder has been removed
   * and `Dump.dump` now delegates to the same typed-IR implementation as
   * [[dumpQuery]].
   *
   * '''Inline-value requirement:''' the `query` argument must be an
   * inline-constructible tree (e.g. inline val/def or direct
   * `SqlQuery.from(...).innerJoin(...).where(...)` chain). Passing a
   * preconstructed non-inline `val` prevents the macro from peeling the
   * construction tree and will emit a compile-time `report.warning` and emit no
   * file. For full joins/filters use `inline val`/`inline def` or construct the
   * query directly at the call site.
   */
  inline def dump[A](inline query: zio.blocks.sql.query.SqlQuery[A]): Unit =
    ${ dumpQueryIrImpl[A]('query) }

  /**
   * Compile-time dump of a query-IR `SqlQuery`'s `SELECT` statement.
   *
   * When `-Dzib.sql.dumpDir` is set, emits `<owner>-<dialect>.sql` for each
   * dialect at the call site (owner derived from the enclosing symbol/val
   * name). No-op otherwise.
   *
   * '''Inline-value requirement:''' same as [[dump]] - the `query` argument
   * must be inline-constructible. A preconstructed non-inline val cannot expose
   * its join/filter tree and will trigger
   * `report.warning("Dump requires inline query value; preconstructed value will emit no file; use inline val/def or Dump.dumpTable")`
   * and emit no file (skips emit) instead of an incomplete source-only
   * fallback. For `Dump.dumpQuery` the same applies: use `inline val`/`inline
   * def` or inline the query at the call site.
   */
  inline def dumpQuery[A](inline query: zio.blocks.sql.query.SqlQuery[A]): Unit =
    ${ dumpQueryIrImpl[A]('query) }

  def dumpTableImpl[A: Type](table: Expr[Table[A]])(using Quotes): Expr[Unit] = {
    import quotes.reflect._
    val dirProp = System.getProperty("zib.sql.dumpDir")
    if (dirProp == null) '{ () }
    else {
      val tpe      = TypeRepr.of[A]
      val sym      = tpe.typeSymbol
      val typeName = if (sym.name == "<none>" || sym.name.isEmpty) "unknown" else sym.name

      val explicit     = explicitTableNameFromTerm(table.asTerm)
      val tableName    = explicit.getOrElse(SqlNameMapper.SnakeCase(typeName))
      val columns      = deriveColumns(tpe)
      val finalColumns =
        if (columns.nonEmpty) columns else IndexedSeq(ColumnMeta("value", DbValue.DbString(""), false))

      for (dialect <- Seq(SqlDialect.PostgreSQL, SqlDialect.SQLite)) {
        val colDefs = finalColumns.map { cm =>
          ColumnDef(cm.name, dialect.typeName(cm.dbValue), cm.nullable)
        }
        val frag    = Ddl.createTable(tableName, colDefs)
        val sqlText = frag.sql(dialect)
        emit(tableName, dialect, sqlText)
      }
      '{ () }
    }
  }

  def dumpQueryIrImpl[A: Type](query: Expr[zio.blocks.sql.query.SqlQuery[A]])(using Quotes): Expr[Unit] = {
    import quotes.reflect._
    val tpe        = TypeRepr.of[A]
    val sym        = tpe.typeSymbol
    val typeName   = if (sym.name == "<none>" || sym.name.isEmpty) "query" else sym.name
    val baseName   = SqlNameMapper.SnakeCase(typeName)
    val argNameOpt = queryArgDerivedName(query.asTerm)
    val ownerName  = ownerDerivedName
    val generic    = Set(
      "from",
      "where",
      "innerJoin",
      "leftJoin",
      "join",
      "groupBy",
      "orderBy",
      "limit",
      "offset",
      "select",
      "filter",
      "having",
      "q",
      "query"
    )
    val rawCandidate = argNameOpt.getOrElse(ownerName)
    val candidate    = if (generic.contains(rawCandidate.toLowerCase) || rawCandidate == "q") ownerName else rawCandidate
    val fileBase     = if (candidate == "query" || candidate.isEmpty) s"$baseName-query" else s"$candidate-query"
    val fileBaseExpr = Expr(fileBase)
    '{
      val q: zio.blocks.sql.query.SqlQuery[A] = $query
      val dir                                 = System.getProperty("zib.sql.dumpDir")
      if (dir != null) {
        val pgSql     = q.toFrag(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        val sqliteSql = q.toFrag(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        Dump.emitRuntime($fileBaseExpr, SqlDialect.PostgreSQL, pgSql)
        Dump.emitRuntime($fileBaseExpr, SqlDialect.SQLite, sqliteSql)
      }
    }
  }
}
