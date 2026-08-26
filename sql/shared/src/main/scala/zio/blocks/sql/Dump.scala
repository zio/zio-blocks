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

  inline def dumpTable[A](inline table: Table[A])(using
    @scala.annotation.unused schema: Schema[A]
  ): Unit =
    ${ dumpTableImpl[A]('table) }

  inline def dump(inline query: SqlQuery[?]): Unit =
    ${ dumpQueryImpl('query) }

  inline def dumpQuery[A](inline query: zio.blocks.sql.query.SqlQuery[A]): Unit =
    ${ dumpQueryIrImpl[A]('query) }

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

  def dumpQueryImpl(query: Expr[SqlQuery[?]])(using Quotes): Expr[Unit] = {
    import quotes.reflect._
    val dirProp = System.getProperty("zib.sql.dumpDir")
    if (dirProp == null) '{ () }
    else {
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
            val dbVal =
              if (fieldTpe =:= TypeRepr.of[Int]) DbValue.DbInt(0)
              else if (fieldTpe =:= TypeRepr.of[String]) DbValue.DbString("")
              else DbValue.DbString("")
            IndexedSeq(ColumnMeta(colName, dbVal, false))
          }.toIndexedSeq
        } else IndexedSeq.empty
      }

      val qTpe                         = query.asTerm.tpe.widen
      val typeArgOpt: Option[TypeRepr] = qTpe match {
        case AppliedType(_, args) if args.nonEmpty => Some(args.head)
        case _                                     => None
      }
      val tableNameFromType: Option[String] = typeArgOpt.map { arg =>
        val sym = arg.typeSymbol
        val tn  = if (sym.name == "<none>" || sym.name.isEmpty) "query" else sym.name
        SqlNameMapper.SnakeCase(tn)
      }
      var explicitFound: Option[String] = None
      def walk(t: Term): Unit           = t match {
        case Literal(StringConstant(s)) if s.matches("[A-Za-z_][A-Za-z0-9_]*") && s.length > 2 =>
          if (explicitFound.isEmpty) explicitFound = Some(s)
        case Apply(fun, args)     => walk(fun); args.foreach(walk)
        case Select(qual, _)      => walk(qual)
        case Inlined(_, _, inner) => walk(inner)
        case Block(_, inner)      => walk(inner)
        case Typed(inner, _)      => walk(inner)
        case _                    =>
      }
      walk(query.asTerm)
      val baseName                        = explicitFound.orElse(tableNameFromType).getOrElse("query")
      val columns: IndexedSeq[ColumnMeta] =
        typeArgOpt.map(deriveColumnsLocal).getOrElse(IndexedSeq.empty)
      val fileBase = s"$baseName-query"

      for (dialect <- Seq(SqlDialect.PostgreSQL, SqlDialect.SQLite)) {
        val selectList =
          if (columns.nonEmpty) columns.map(c => s"""t0."${c.name}"""").mkString(", ")
          else "t0.*"
        val sqlText = s"""SELECT $selectList FROM "$baseName" AS t0"""
        emit(fileBase, dialect, sqlText)
      }
      '{ () }
    }
  }

  def dumpQueryIrImpl[A: Type](
    @scala.annotation.unused query: Expr[zio.blocks.sql.query.SqlQuery[A]]
  )(using Quotes): Expr[Unit] = {
    import quotes.reflect._
    val dirProp = System.getProperty("zib.sql.dumpDir")
    if (dirProp == null) '{ () }
    else {
      val tpe                  = TypeRepr.of[A]
      val sym                  = tpe.typeSymbol
      val typeName             = if (sym.name == "<none>" || sym.name.isEmpty) "query" else sym.name
      val baseName             = SqlNameMapper.SnakeCase(typeName)
      val fileBase             = s"$baseName-query"
      val fields: List[Symbol] =
        try sym.caseFields
        catch { case _: Throwable => Nil }
      val columns: IndexedSeq[ColumnMeta] =
        if (fields.nonEmpty)
          fields.map(f => ColumnMeta(SqlNameMapper.SnakeCase(f.name), DbValue.DbString(""), false)).toIndexedSeq
        else IndexedSeq.empty
      for (dialect <- Seq(SqlDialect.PostgreSQL, SqlDialect.SQLite)) {
        val selectList =
          if (columns.nonEmpty) columns.map(c => s"""t0."${c.name}"""").mkString(", ")
          else "t0.*"
        val sqlText = s"""SELECT $selectList FROM "$baseName" AS t0"""
        emit(fileBase, dialect, sqlText)
      }
      '{ () }
    }
  }
}
