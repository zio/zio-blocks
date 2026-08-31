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
 * Compile-time SQL dump utilities for debugging and inspection.
 *
 * `Dump` exposes transparent `inline` macros that, when enabled, write the
 * generated SQL text to files **during compilation**. This is an opt-in
 * diagnostic aid — by default the macros expand to `()` and perform no I/O.
 *
 * ==Opt-in mechanism==
 *
 * Set the system property `zib.sql.dumpDir` to a directory path before
 * compilation, e.g. `-Dzib.sql.dumpDir=/tmp/sql-dump` on the `scalac`/`sbt`
 * invocation or via `System.setProperty("zib.sql.dumpDir", "/tmp/sql-dump")` in
 * the same JVM that runs the compiler. When the property is absent or `null`
 * every `Dump` call is a no-op. No code changes are required beyond adding the
 * `Dump` invocations where you want SQL inspected.
 *
 * ==Side effects==
 *
 * When `zib.sql.dumpDir` is set, each macro invocation may perform file I/O at
 * compile time:
 *   - Parent directories are created with `Files.createDirectories`.
 *   - SQL is written as UTF-8 to `<sanitized-name>-<dialect>.sql` (e.g.
 *     `users-postgresql.sql`, `users-sqlite.sql`) under the dump directory.
 *   - Existing files are compared byte-for-byte and left untouched if
 *     unchanged, to avoid spurious rebuilds.
 *   - Any I/O failure is reported as a compiler warning at the macro expansion
 *     site (`Position.ofMacroExpansion`) and never fails compilation.
 *
 * When the property is not set, the macros short-circuit to `'{ () }` without
 * touching the file system — compilation is side-effect free.
 *
 * ==Intended usage==
 *
 * Intended for local debugging, SQL review, and documentation — not for
 * production. Use `Dump` to inspect what DDL macros generate for each dialect
 * (`PostgreSQL` vs `SQLite`), diff the output across schema changes, or check
 * generated SQL into docs. Remove or gate `Dump` calls before release; they are
 * compile-time only and have no runtime cost when `zib.sql.dumpDir` is unset.
 *
 * @see
 *   [[SqlDialect]] for the dialects that are dumped (`PostgreSQL` and `SQLite`)
 */
object Dump {

  private[sql] def emit(name: String, dialect: SqlDialect, sqlText: String)(using Quotes): Unit = {
    import quotes.reflect._
    val dirProp = System.getProperty("zib.sql.dumpDir")
    if (dirProp == null) return
    try {
      val safe     = sanitize(name)
      val suffix   = dialect.name.toLowerCase(java.util.Locale.ROOT)
      val fileName = s"$safe-$suffix.sql"
      val content  = if (sqlText.endsWith("\n")) sqlText else sqlText + "\n"
      val dirPath  = java.nio.file.Paths.get(dirProp)
      val filePath = dirPath.resolve(fileName)
      val parent   = filePath.getParent
      if (parent != null) java.nio.file.Files.createDirectories(parent)
      val bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      if (java.nio.file.Files.exists(filePath)) {
        val existing = java.nio.file.Files.readAllBytes(filePath)
        if (java.util.Arrays.equals(existing, bytes)) return
      }
      java.nio.file.Files.write(
        filePath,
        bytes,
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
        java.nio.file.StandardOpenOption.WRITE
      )
    } catch {
      case e: Throwable =>
        report.warning(
          s"[zib.sql.dumpDir] Failed to dump $name/${dialect.name}: ${e.getMessage}",
          Position.ofMacroExpansion
        )
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

  /**
   * Dump the DDL for the given `Table` to the dump directory.
   *
   * Inline macro that expands at compile time. When `zib.sql.dumpDir` is set,
   * generates `CREATE TABLE` SQL for both `PostgreSQL` and `SQLite` and writes
   * each to `<table>-<dialect>.sql` via [[emit]]; otherwise expands to `()`.
   * Intended for debugging/inspection — see [[Dump]] for opt-in and side-effect
   * details.
   */
  inline def dumpTable[A](inline table: Table[A])(using
    @scala.annotation.unused schema: Schema[A]
  ): Unit =
    ${ dumpTableImpl[A]('table) }

  def dumpTableImpl[A: Type](table: Expr[Table[A]])(using Quotes): Expr[Unit] = {
    import quotes.reflect._
    val dirProp = System.getProperty("zib.sql.dumpDir")
    if (dirProp == null) '{ () }
    else {
      def dbValueForLocal(tpe: TypeRepr): DbValue =
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
            case "java.time.LocalTime"                    => DbValue.DbLocalTime(java.time.LocalTime.MIDNIGHT)
            case "java.time.Instant"                      => DbValue.DbInstant(java.time.Instant.EPOCH)
            case "java.time.Duration"                     => DbValue.DbDuration(java.time.Duration.ZERO)
            case "java.util.UUID"                         => DbValue.DbUUID(new java.util.UUID(0L, 0L))
            case _ if tpe.typeSymbol.flags.is(Flags.Enum) => DbValue.DbString("")
            case _                                        => DbValue.DbString("")
          }
        }

      def dbValueAndNullableLocal(tpe: TypeRepr): (DbValue, Boolean) = {
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
          val (innerVal, _) = dbValueAndNullableLocal(inner)
          (innerVal, true)
        } else (dbValueForLocal(tpe), false)
      }

      def deriveColumnsLocal(tpe: TypeRepr): IndexedSeq[ColumnMeta] = {
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
            val (dbVal, nullable) = dbValueAndNullableLocal(fieldTpe)
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

      val tpe      = TypeRepr.of[A]
      val sym      = tpe.typeSymbol
      val typeName = if (sym.name == "<none>" || sym.name.isEmpty) "unknown" else sym.name

      var explicit: Option[String] = None
      def walk(t: Term): Unit      = t match {
        case Literal(StringConstant(s)) if s.matches("[A-Za-z_][A-Za-z0-9_]*") =>
          if (explicit.isEmpty) explicit = Some(s)
        case Apply(fun, args)     => walk(fun); args.foreach(walk)
        case Select(qual, _)      => walk(qual)
        case Inlined(_, _, inner) => walk(inner)
        case Block(_, inner)      => walk(inner)
        case Typed(inner, _)      => walk(inner)
        case _                    =>
      }
      walk(table.asTerm)
      val tableName    = explicit.getOrElse(SqlNameMapper.SnakeCase(typeName))
      val columns      = deriveColumnsLocal(tpe)
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
}
