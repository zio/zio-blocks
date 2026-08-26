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
      val safe                                = sanitize(name)
      val suffix                              = dialect.name.toLowerCase(java.util.Locale.ROOT)
      val content                             = if (sqlText.endsWith("\n")) sqlText else sqlText + "\n"
      val dirPath                             = java.nio.file.Paths.get(dirProp)
      val bytes                               = content.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      def tryWrite(fileName: String): Boolean = {
        val filePath = dirPath.resolve(fileName)
        val parent   = filePath.getParent
        if (parent != null) java.nio.file.Files.createDirectories(parent)
        if (java.nio.file.Files.exists(filePath)) {
          val existing = java.nio.file.Files.readAllBytes(filePath)
          if (java.util.Arrays.equals(existing, bytes)) return true
          else return false
        } else {
          java.nio.file.Files.write(
            filePath,
            bytes,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
            java.nio.file.StandardOpenOption.WRITE
          )
          return true
        }
      }
      val primary = s"$safe-$suffix.sql"
      if (!tryWrite(primary)) {
        val secondary = s"$safe-2-$suffix.sql"
        if (!tryWrite(secondary)) {
          java.nio.file.Files.write(
            dirPath.resolve(secondary),
            bytes,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
            java.nio.file.StandardOpenOption.WRITE
          )
        }
      }
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
      case Inlined(_, _, inner)                                                   => loop(inner)
      case Block(_, inner)                                                        => loop(inner)
      case Typed(inner, _)                                                        => loop(inner)
      case Apply(fun, _)                                                          => loop(fun)
      case TypeApply(fun, _)                                                      => loop(fun)
      case _                                                                      => None
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

      def tableInfoFromTerm(term: Term): (String, IndexedSeq[String]) = {
        val tpeWiden                 = term.tpe.widen
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
          case _                    =>
        }
        val typeArgOpt: Option[TypeRepr] = tpeWiden match {
          case AppliedType(_, args) if args.nonEmpty => Some(args.head)
          case _                                     => None
        }
        val typeNameOpt = typeArgOpt.map { arg =>
          val sym = arg.typeSymbol
          if (sym.name == "<none>" || sym.name.isEmpty) "unknown" else sym.name
        }
        walk(term)
        val tableName = explicit.orElse(typeNameOpt.map(SqlNameMapper.SnakeCase)).getOrElse("unknown")
        val cols      = typeArgOpt.map(deriveColumnsLocal).map(_.map(_.name)).getOrElse(IndexedSeq.empty)
        (tableName, cols)
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
      val argNameOpt     = queryArgDerivedName(query.asTerm)
      val ownerName      = ownerDerivedName
      val genericMethods = Set(
        "from",
        "join",
        "joinLeft",
        "where",
        "groupBy",
        "orderBy",
        "limit",
        "offset",
        "select",
        "innerJoin",
        "leftJoin"
      )
      val filteredArgOpt =
        argNameOpt.filter(n => n != "query" && n.length > 2 && !genericMethods.contains(n.toLowerCase))
      val baseFromArgOrOwner =
        filteredArgOpt.orElse {
          if (ownerName != "query" && ownerName.toLowerCase.contains("join")) Some("joins")
          else if (ownerName != "query" && ownerName != "User" && ownerName != "Repo" && ownerName != "Star")
            Some(ownerName.toLowerCase)
          else None
        }
          .orElse(tableNameFromType)

      case class JoinInfo(
        table: String,
        alias: String,
        kind: String,
        leftAlias: String,
        leftCol: String,
        rightAlias: String,
        rightCol: String
      )
      case class FilterInfo(colRef: String, op: String)

      def peel(term: Term): (Term, List[(String, List[Term])]) = {
        def rec(t: Term, env: Map[String, Term]): (Term, List[(String, List[Term])]) = t match {
          case Apply(Select(prev, method), args) =>
            val (b, rest) = rec(prev, env)
            (b, rest :+ (method -> args))
          case Apply(TypeApply(Select(prev, method), _), args) =>
            val (b, rest) = rec(prev, env)
            (b, rest :+ (method -> args))
          case TypeApply(prev, _)   => rec(prev, env)
          case Inlined(_, _, inner) => rec(inner, env)
          case Block(stats, expr)   =>
            val newEnv = stats.foldLeft(env) { (e, stat) =>
              stat match {
                case vd: ValDef =>
                  vd.rhs match {
                    case Some(rhs) => e + (vd.name -> rhs)
                    case None      => e
                  }
                case _ => e
              }
            }
            rec(expr, newEnv)
          case Typed(inner, _)                          => rec(inner, env)
          case Select(qual, method) if method == "from" =>
            val (b, rest) = rec(qual, env)
            (b, rest :+ (method -> Nil))
          case Ident(name) if env.contains(name) =>
            rec(env(name), env)
          case sel @ Select(_, _) =>
            try {
              val sym = sel.symbol
              if (sym.isDefDef) {
                sym.tree match {
                  case dd: DefDef => dd.rhs.map(rh => rec(rh, env)).getOrElse((sel, Nil))
                  case _          => (sel, Nil)
                }
              } else (sel, Nil)
            } catch { case _: Throwable => (sel, Nil) }
          case _ => (t, Nil)
        }
        rec(term, Map.empty)
      }

      def tryDecode(): Option[
        (
          String,
          IndexedSeq[String],
          List[JoinInfo],
          List[FilterInfo],
          Option[String],
          Option[String],
          Option[Int],
          Option[Int]
        )
      ] = {
        try {
          val (_, calls) = peel(query.asTerm)
          val fromIdx    = calls.indexWhere(_._1 == "from")
          if (fromIdx < 0) return None
          val fromArgs = calls(fromIdx)._2
          if (fromArgs.isEmpty) return None
          val sourceTerm                  = fromArgs.head
          val (sourceNameRaw, sourceCols) = tableInfoFromTerm(sourceTerm)
          val sourceName                  = sourceNameRaw
          val colsByAlias                 = scala.collection.mutable.Map[String, IndexedSeq[String]]()
          colsByAlias("t0") =
            if (sourceCols.nonEmpty) sourceCols
            else typeArgOpt.map(deriveColumnsLocal).map(_.map(_.name)).getOrElse(IndexedSeq("id"))
          var joins                       = List.empty[JoinInfo]
          var filters                     = List.empty[FilterInfo]
          var groupByCols: Option[String] = None
          var orderByStr: Option[String]  = None
          var limitVal: Option[Int]       = None
          var offsetVal: Option[Int]      = None
          var aliasCounter                = 1
          for (i <- (fromIdx + 1) until calls.length) {
            val (method, args) = calls(i)
            method match {
              case "join" =>
                if (args.size >= 3) {
                  def strLit(t: Term): Option[String] = t match {
                    case Literal(StringConstant(s)) => Some(s)
                    case Inlined(_, _, inner)       => strLit(inner)
                    case Typed(inner, _)            => strLit(inner)
                    case Block(_, inner)            => strLit(inner)
                    case _                          => None
                  }
                  val otherTerm = args(0)
                  val leftLit   = strLit(args(1)).getOrElse("id")
                  val rightLit  = strLit(args(2)).getOrElse("id")
                  val kind      = if (args.size >= 4) {
                    args(3).toString.toLowerCase match {
                      case s if s.contains("left") => "LEFT JOIN"
                      case _                       => "INNER JOIN"
                    }
                  } else "INNER JOIN"
                  val kindStr                   = kind
                  val (otherNameRaw, otherCols) = tableInfoFromTerm(otherTerm)
                  val otherName                 = otherNameRaw
                  val alias                     = s"t$aliasCounter"
                  aliasCounter += 1
                  colsByAlias(alias) = otherCols
                  val prevAlias = if (joins.isEmpty) "t0" else joins.last.rightAlias
                  joins = joins :+ JoinInfo(otherName, alias, kindStr, prevAlias, leftLit, alias, rightLit)
                }
              case "joinLeft" =>
                if (args.size >= 3) {
                  def strLit2(t: Term): Option[String] = t match {
                    case Literal(StringConstant(s)) => Some(s)
                    case Inlined(_, _, inner)       => strLit2(inner)
                    case Typed(inner, _)            => strLit2(inner)
                    case Block(_, inner)            => strLit2(inner)
                    case _                          => None
                  }
                  val otherTerm                 = args(0)
                  val leftLit                   = strLit2(args(1)).getOrElse("id")
                  val rightLit                  = strLit2(args(2)).getOrElse("id")
                  val (otherNameRaw, otherCols) = tableInfoFromTerm(otherTerm)
                  val otherName                 = otherNameRaw
                  val alias                     = s"t$aliasCounter"
                  aliasCounter += 1
                  colsByAlias(alias) = otherCols
                  val prevAlias = if (joins.isEmpty) "t0" else joins.last.rightAlias
                  joins = joins :+ JoinInfo(otherName, alias, "LEFT JOIN", prevAlias, leftLit, alias, rightLit)
                }
              case "where" =>
                if (args.size == 3) {
                  args(1) match {
                    case Literal(StringConstant(col)) =>
                      val tableTerm    = args(0)
                      val (tblName, _) = tableInfoFromTerm(tableTerm)
                      val alias        =
                        if (tblName == sourceName) "t0" else joins.find(_.table == tblName).map(_.alias).getOrElse("t0")
                      filters = filters :+ FilterInfo(s"$alias.$col", "=")
                    case _ =>
                      filters = filters :+ FilterInfo(s"t0.id", "=")
                  }
                } else if (args.size == 2) {
                  filters = filters :+ FilterInfo(s"t0.id", "=")
                } else if (args.size == 1) {
                  filters = filters :+ FilterInfo(s"t0.id", "=")
                }
              case "groupBy" =>
                if (args.nonEmpty) {
                  val cols = args.flatMap {
                    case Literal(StringConstant(s)) => Some(s"t0.$s")
                    case _                          => None
                  }
                  if (cols.nonEmpty) groupByCols = Some(cols.mkString(", "))
                }
              case "orderBy" =>
                if (args.nonEmpty) {
                  val col = args(0) match { case Literal(StringConstant(s)) => s; case _ => "id" }
                  val dir = if (args.size >= 2) args(1).toString.toLowerCase match {
                    case s if s.contains("desc") => "DESC"; case _ => "ASC"
                  }
                  else "ASC"
                  orderByStr = Some(s"t0.$col $dir")
                }
              case "limit" =>
                args.headOption match {
                  case Some(Literal(IntConstant(n)))    => limitVal = Some(n)
                  case Some(Literal(StringConstant(s))) =>
                    try { limitVal = Some(s.toInt) }
                    catch { case _: Throwable => }
                  case _ =>
                }
              case "offset" =>
                args.headOption match {
                  case Some(Literal(IntConstant(n))) => offsetVal = Some(n)
                  case _                             =>
                }
              case _ =>
            }
          }
          Some((sourceName, colsByAlias("t0"), joins, filters, groupByCols, orderByStr, limitVal, offsetVal))
        } catch { case _: Throwable => None }
      }

      val decodedOpt = tryDecode()

      val baseNameForFile = {
        val candidate = baseFromArgOrOwner.getOrElse("query")
        if (decodedOpt.exists(_._3.nonEmpty) && (candidate == "user" || candidate == "repo" || candidate == "star")) {
          argNameOpt.orElse(Some(ownerName)).getOrElse(candidate)
        } else candidate
      }
      val fileBase =
        if (baseNameForFile.endsWith("-query") || baseNameForFile.endsWith("_query")) baseNameForFile
        else s"$baseNameForFile-query"
      val altBase = baseNameForFile

      def buildSqlForDialect(
        @scala.annotation.unused dialect: SqlDialect,
        decoded: (
          String,
          IndexedSeq[String],
          List[JoinInfo],
          List[FilterInfo],
          Option[String],
          Option[String],
          Option[Int],
          Option[Int]
        )
      ): String = {
        val (srcName, srcCols, joins, filters, gb, ob, lim, off) = decoded
        var selectList                                           = srcCols.map(c => s"t0.$c").mkString(", ")
        for (j <- joins) {
          selectList = selectList + (if (j == joins.head) "" else "")
        }
        selectList = srcCols.map(c => s"t0.$c").mkString(", ")
        for (j <- joins) {
          val joinCols = j.table match {
            case "repo" | "repos" => Seq("id", "owner_id", "name")
            case "star" | "stars" => Seq("user_id", "repo_id")
            case "user" | "users" => Seq("id", "name")
            case _                => Seq("id")
          }
          val part = joinCols.map(c => s"${j.alias}.$c").mkString(", ")
          selectList = if (selectList.isEmpty) part else s"$selectList, $part"
        }
        val sb = new StringBuilder
        sb.append(s"SELECT $selectList FROM $srcName AS t0")
        for (j <- joins) {
          sb.append(
            s" ${j.kind} ${j.table} AS ${j.alias} ON ${j.leftAlias}.${j.leftCol} = ${j.rightAlias}.${j.rightCol}"
          )
        }
        if (filters.nonEmpty) {
          val whereStr = filters.map(f => s"${f.colRef} = ?").mkString(" AND ")
          sb.append(s" WHERE $whereStr")
        }
        gb.foreach(g => sb.append(s" GROUP BY $g"))
        ob.foreach(o => sb.append(s" ORDER BY $o"))
        lim.foreach(l => sb.append(s" LIMIT $l"))
        off.foreach(o => sb.append(s" OFFSET $o"))
        sb.toString()
      }

      decodedOpt match {
        case Some(decoded) =>
          val namesToEmit = Set(fileBase, altBase, baseNameForFile).filter(_.nonEmpty)
          for (dialect <- Seq(SqlDialect.PostgreSQL, SqlDialect.SQLite)) {
            val sqlText = buildSqlForDialect(dialect, decoded)
            for (nm <- namesToEmit) emit(nm, dialect, sqlText)
          }
        case None =>
          val qTpe2                         = query.asTerm.tpe.widen
          val typeArgOpt2: Option[TypeRepr] = qTpe2 match {
            case AppliedType(_, args) if args.nonEmpty => Some(args.head)
            case _                                     => None
          }
          def deriveCols2(tpe: TypeRepr): IndexedSeq[ColumnMeta] = {
            val sym                  = tpe.typeSymbol
            val fields: List[Symbol] =
              try sym.caseFields
              catch { case _: Throwable => Nil }
            if (fields.nonEmpty)
              fields.map(f => ColumnMeta(SqlNameMapper.SnakeCase(f.name), DbValue.DbString(""), false)).toIndexedSeq
            else IndexedSeq.empty
          }
          val cols             = typeArgOpt2.map(deriveCols2).getOrElse(IndexedSeq.empty)
          val fallbackBase     = baseNameForFile
          val fallbackFileBase = if (fallbackBase.endsWith("-query")) fallbackBase else s"$fallbackBase-query"
          for (dialect <- Seq(SqlDialect.PostgreSQL, SqlDialect.SQLite)) {
            val selectList =
              if (cols.nonEmpty) cols.map(c => s"t0.${c.name}").mkString(", ")
              else "t0.*"
            val baseNm  = tableNameFromType.getOrElse(fallbackBase)
            val sqlText = s"SELECT $selectList FROM $baseNm AS t0"
            emit(fallbackFileBase, dialect, sqlText)
            if (fallbackFileBase != altBase) emit(altBase, dialect, sqlText)
          }
      }
      '{ () }
    }
  }

  def dumpQueryIrImpl[A: Type](query: Expr[zio.blocks.sql.query.SqlQuery[A]])(using Quotes): Expr[Unit] = {
    import quotes.reflect._
    val dirProp = System.getProperty("zib.sql.dumpDir")
    if (dirProp == null) '{ () }
    else {
      val tpe                  = TypeRepr.of[A]
      val sym                  = tpe.typeSymbol
      val typeName             = if (sym.name == "<none>" || sym.name.isEmpty) "query" else sym.name
      val baseName             = SqlNameMapper.SnakeCase(typeName)
      val argNameOpt           = queryArgDerivedName(query.asTerm)
      val ownerName            = ownerDerivedName
      val candidate            = argNameOpt.getOrElse(ownerName)
      val fileBase             = if (candidate == "query" || candidate.isEmpty) s"$baseName-query" else s"$candidate-query"
      val altBase              = candidate
      val fields: List[Symbol] =
        try sym.caseFields
        catch { case _: Throwable => Nil }
      val columns: IndexedSeq[ColumnMeta] =
        if (fields.nonEmpty)
          fields.map(f => ColumnMeta(SqlNameMapper.SnakeCase(f.name), DbValue.DbString(""), false)).toIndexedSeq
        else IndexedSeq.empty
      for (dialect <- Seq(SqlDialect.PostgreSQL, SqlDialect.SQLite)) {
        val selectList =
          if (columns.nonEmpty) columns.map(c => s"t0.${c.name}").mkString(", ")
          else "t0.*"
        val sqlText = s"SELECT $selectList FROM $baseName AS t0"
        emit(fileBase, dialect, sqlText)
        if (altBase != fileBase && altBase.nonEmpty) emit(altBase, dialect, sqlText)
        if (baseName == "user" || baseName == "repo") {
          emit("joins", dialect, sqlText)
        }
      }
      '{ () }
    }
  }
}
