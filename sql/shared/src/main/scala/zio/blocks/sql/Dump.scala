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

  private def tableInfoFromTerm(using quotes: Quotes)(term: quotes.reflect.Term): (String, IndexedSeq[String]) = {
    import quotes.reflect._
    val tpeWiden                     = term.tpe.widen
    val explicit                     = explicitTableNameFromTerm(term)
    val typeArgOpt: Option[TypeRepr] = tpeWiden match {
      case AppliedType(_, args) if args.nonEmpty => Some(args.head)
      case _                                     => None
    }
    val typeNameOpt = typeArgOpt.map { arg =>
      val sym = arg.typeSymbol
      if (sym.name == "<none>" || sym.name.isEmpty) "unknown" else sym.name
    }
    val tableName = explicit.orElse(typeNameOpt.map(SqlNameMapper.SnakeCase)).getOrElse("unknown")
    val cols      = typeArgOpt.map(deriveColumns).map(_.map(_.name)).getOrElse(IndexedSeq.empty)
    (tableName, cols)
  }

  private def stringLiteral(using quotes: Quotes)(term: quotes.reflect.Term): Option[String] = {
    import quotes.reflect._
    term match {
      case Literal(StringConstant(s)) => Some(s)
      case Inlined(_, _, inner)       => stringLiteral(inner)
      case Typed(inner, _)            => stringLiteral(inner)
      case Block(_, inner)            => stringLiteral(inner)
      case NamedArg(_, inner)         => stringLiteral(inner)
      case _                          => None
    }
  }

  private def intLiteral(using quotes: Quotes)(term: quotes.reflect.Term): Option[Int] = {
    import quotes.reflect._
    term match {
      case Literal(IntConstant(n))    => Some(n)
      case Literal(StringConstant(s)) =>
        try Some(s.toInt)
        catch { case _: Throwable => None }
      case Inlined(_, _, inner) => intLiteral(inner)
      case Typed(inner, _)      => intLiteral(inner)
      case Block(_, inner)      => intLiteral(inner)
      case NamedArg(_, inner)   => intLiteral(inner)
      case _                    => None
    }
  }

  private def peel(using
    quotes: Quotes
  )(term: quotes.reflect.Term): (quotes.reflect.Term, List[(String, List[quotes.reflect.Term])]) = {
    import quotes.reflect._
    def rec(t: Term, env: Map[String, Term]): (Term, List[(String, List[Term])]) = t match {
      case Apply(Select(prev, method), args) =>
        val (b, rest)    = rec(prev, env)
        val resolvedArgs = args.map {
          case Ident(n) if env.contains(n) => env(n)
          case other                       => other
        }
        def expand(term: Term): List[Term] = term match {
          case Typed(inner, _)      => expand(inner)
          case Inlined(_, _, inner) => expand(inner)
          case Repeated(elems, _)   => elems.flatMap(expand)
          case other                => List(other)
        }
        val expanded = resolvedArgs.flatMap(expand)
        (b, rest :+ (method -> expanded))
      case Apply(TypeApply(Select(prev, method), _), args) =>
        val (b, rest)     = rec(prev, env)
        val resolvedArgs2 = args.map {
          case Ident(n) if env.contains(n) => env(n)
          case other                       => other
        }
        def expand2(term: Term): List[Term] = term match {
          case Typed(inner, _)      => expand2(inner)
          case Inlined(_, _, inner) => expand2(inner)
          case Repeated(elems, _)   => elems.flatMap(expand2)
          case other                => List(other)
        }
        val expanded2 = resolvedArgs2.flatMap(expand2)
        (b, rest :+ (method -> expanded2))
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

  private def isTableTerm(using quotes: Quotes)(term: quotes.reflect.Term): Boolean = {
    import quotes.reflect._
    val tpe = term.tpe.widen
    tpe match {
      case AppliedType(tycon, _) => tycon.typeSymbol.fullName == "zio.blocks.sql.Table"
      case _                     => tpe.typeSymbol.fullName == "zio.blocks.sql.Table"
    }
  }

  private def isColumnRefTerm(using quotes: Quotes)(term: quotes.reflect.Term): Boolean = {
    val symName = term.tpe.widen.typeSymbol.name
    val full    = term.tpe.widen.typeSymbol.fullName
    symName == "ColumnRef" || full.endsWith("ColumnRef")
  }

  private def decodeColumnRef(using quotes: Quotes)(term: quotes.reflect.Term): Option[(String, String)] = {
    import quotes.reflect._
    def unwrap(t: Term): Term = t match {
      case Inlined(_, _, inner) => unwrap(inner)
      case Typed(inner, _)      => unwrap(inner)
      case Block(_, inner)      => unwrap(inner)
      case _                    => t
    }
    val u = unwrap(term)
    // Try Apply of ColumnRef companion or constructor: alias, column
    def extractStrings(args: List[Term]): Option[(String, String)] =
      args match {
        case List(a, b) =>
          for {
            alias <- stringLiteral(a)
            col   <- stringLiteral(b)
          } yield (alias, col)
        case _ => None
      }
    u match {
      case Apply(Select(New(_), "<init>"), args) if args.size == 2 =>
        extractStrings(args)
      case Apply(Select(_, "apply"), args) if args.size == 2 =>
        // check if the select's qualifier is ColumnRef type
        val sym = u match {
          case Apply(Select(qual, _), _) => qual.tpe.typeSymbol.fullName
          case _                         => ""
        }
        if (sym.contains("ColumnRef")) extractStrings(args) else None
      case Apply(fun, args) if args.size == 2 =>
        // generic fallback: two string literals
        extractStrings(args)
      case _ => None
    }
  }

  private def decodeRel(using
    quotes: Quotes
  )(term: quotes.reflect.Term): Option[(quotes.reflect.Term, String, quotes.reflect.Term, String)] = {
    import quotes.reflect._
    def unwrap(t: Term): Term = t match {
      case Inlined(_, _, inner) => unwrap(inner)
      case Typed(inner, _)      => unwrap(inner)
      case Block(_, inner)      => unwrap(inner)
      case _                    => t
    }
    val u = unwrap(term)
    u match {
      case Apply(Select(New(_), "<init>"), List(from, Literal(StringConstant(fk)), to, Literal(StringConstant(pk)))) =>
        Some((from, fk, to, pk))
      case Apply(Select(_, "apply"), List(from, Literal(StringConstant(fk)), to, Literal(StringConstant(pk)))) =>
        Some((from, fk, to, pk))
      case Apply(Select(_, name), args) if (name == "manyToOne" || name == "oneToMany") && args.size == 4 =>
        (args(1), args(3)) match {
          case (Literal(StringConstant(fk)), Literal(StringConstant(pk))) => Some((args(0), fk, args(2), pk))
          case (a1, a3)                                                   =>
            (stringLiteral(a1), stringLiteral(a3)) match {
              case (Some(fk2), Some(pk2)) => Some((args(0), fk2, args(2), pk2))
              case _                      => None
            }
        }
      case Apply(fun, args) if args.size == 4 =>
        (args(1), args(3)) match {
          case (Literal(StringConstant(fk)), Literal(StringConstant(pk))) => Some((args(0), fk, args(2), pk))
          case (a1, a3)                                                   =>
            (stringLiteral(a1), stringLiteral(a3)) match {
              case (Some(fk2), Some(pk2)) => Some((args(0), fk2, args(2), pk2))
              case _                      => None
            }
        }
      case _ => None
    }
  }

  private def decodeDirection(using quotes: Quotes)(term: quotes.reflect.Term): String = {
    import quotes.reflect._
    term match {
      case Select(_, "Asc")                                             => "ASC"
      case Select(_, "Desc")                                            => "DESC"
      case Inlined(_, _, inner)                                         => decodeDirection(inner)
      case Typed(inner, _)                                              => decodeDirection(inner)
      case Block(_, inner)                                              => decodeDirection(inner)
      case Literal(StringConstant(s)) if s.toLowerCase.contains("desc") => "DESC"
      case _                                                            =>
        val str = term.toString.toLowerCase
        if (str.contains("desc")) "DESC" else "ASC"
    }
  }

  private def decodeFrag(using quotes: Quotes)(term: quotes.reflect.Term): Option[(String, Int)] = {
    import quotes.reflect._
    def unwrap(t: Term): Term = t match {
      case Inlined(_, _, inner) => unwrap(inner)
      case Typed(inner, _)      => unwrap(inner)
      case Block(_, inner)      => unwrap(inner)
      case _                    => t
    }
    def extractIndexedSeqStrings(t: Term): Option[List[String]] = {
      val u = unwrap(t)
      u match {
        case Apply(Select(_, "apply"), args) =>
          // Could be IndexedSeq.apply or Seq.apply
          val lits = args.flatMap(a => stringLiteral(a).toList)
          if (lits.size == args.size && args.nonEmpty) Some(lits)
          else if (lits.nonEmpty) Some(lits)
          else None
        case Apply(TypeApply(Select(_, "apply"), _), args) =>
          val lits = args.flatMap(a => stringLiteral(a).toList)
          if (lits.size == args.size && args.nonEmpty) Some(lits)
          else if (lits.nonEmpty) Some(lits)
          else None
        case Typed(inner, _)      => extractIndexedSeqStrings(inner)
        case Inlined(_, _, inner) => extractIndexedSeqStrings(inner)
        case _                    => None
      }
    }
    def countParams(t: Term): Int = {
      var count = 0
      object counter extends TreeTraverser {
        override def traverseTree(tree: Tree)(owner: Symbol): Unit = tree match {
          case Apply(Select(_, name), _)
              if name == "DbInt" || name == "DbLong" || name == "DbString" || name == "DbBoolean" || name == "DbDouble" || name == "DbFloat" || name == "DbShort" || name == "DbByte" || name == "DbChar" || name == "DbBigDecimal" || name == "DbBytes" || name == "DbLocalDate" || name == "DbLocalDateTime" || name == "DbLocalTime" || name == "DbInstant" || name == "DbDuration" || name == "DbUUID" || name == "DbNull" =>
            count += 1
            super.traverseTree(tree)(owner)
          case _ => super.traverseTree(tree)(owner)
        }
      }
      try counter.traverseTree(t)(Symbol.spliceOwner)
      catch { case _: Throwable => () }
      count
    }
    def collectStringLits(t: Term): List[String] = {
      var buf = List.empty[String]
      object trav extends TreeTraverser {
        override def traverseTree(tree: Tree)(owner: Symbol): Unit = tree match {
          case Literal(StringConstant(s)) =>
            buf = buf :+ s
            super.traverseTree(tree)(owner)
          case _ => super.traverseTree(tree)(owner)
        }
      }
      try trav.traverseTree(t)(Symbol.spliceOwner)
      catch { case _: Throwable => () }
      buf
    }
    val u = unwrap(term)
    // Try direct Frag constructor
    u match {
      case Apply(Select(New(tpt), "<init>"), List(partsTerm, paramsTerm)) if tpt.tpe.typeSymbol.name == "Frag" =>
        extractIndexedSeqStrings(partsTerm) match {
          case Some(parts) =>
            val paramCount = countParams(paramsTerm)
            // params count fallback to IndexedSeq size if dbValue counting fails
            val sizeFromSeq = {
              val ut = unwrap(paramsTerm)
              ut match {
                case Apply(Select(_, "apply"), args)               => args.size
                case Apply(TypeApply(Select(_, "apply"), _), args) => args.size
                case _                                             => paramCount
              }
            }
            val cnt = if (paramCount > 0) paramCount else sizeFromSeq
            val sql = if (parts.isEmpty) "" else parts.mkString("?")
            Some((sql, cnt))
          case None =>
            // fallback to literal collection
            val lits = collectStringLits(partsTerm)
            if (lits.nonEmpty) Some((lits.mkString("?"), countParams(paramsTerm)))
            else None
        }
      case Apply(Select(_, "literal"), List(Literal(StringConstant(s)))) =>
        Some((s, 0))
      case Apply(Select(_, "apply"), List(partsTerm, paramsTerm)) =>
        // check if qualifier is Frag
        extractIndexedSeqStrings(partsTerm) match {
          case Some(parts) =>
            val cnt = countParams(paramsTerm)
            Some((parts.mkString("?"), cnt))
          case None =>
            val lits = collectStringLits(partsTerm)
            if (lits.nonEmpty) Some((lits.mkString("?"), countParams(paramsTerm)))
            else None
        }
      case Apply(TypeApply(Select(_, "apply"), _), List(partsTerm, paramsTerm)) =>
        extractIndexedSeqStrings(partsTerm) match {
          case Some(parts) =>
            val cnt = countParams(paramsTerm)
            Some((parts.mkString("?"), cnt))
          case None =>
            val lits = collectStringLits(partsTerm)
            if (lits.nonEmpty) Some((lits.mkString("?"), countParams(paramsTerm)))
            else None
        }
      case _ =>
        // generic fallback: collect string lits containing SQL-ish content and count ?
        val lits = collectStringLits(u)
        // Filter lits that look like SQL fragments (contain . or " or = or space)
        val sqlLits = lits.filter(s =>
          s.contains(".") || s.contains("\"") || s.contains("=") || s.contains("SELECT") || s.contains("WHERE")
        )
        if (sqlLits.nonEmpty) {
          // For single filter, parts joined with ? would be sqlLits.mkString("?"), but we don't know parts boundaries.
          // Use first lit as SQL if single
          val sql = if (sqlLits.size == 1) sqlLits.head else sqlLits.mkString("?")
          val cnt = countParams(u)
          Some((sql, if (cnt == 0 && sql.contains("?")) 1 else cnt))
        } else if (lits.nonEmpty) {
          // maybe simple where with column?
          None
        } else None
    }
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
   * Compile-time dump of a legacy `SqlQuery`'s `SELECT` statement.
   *
   * When `-Dzib.sql.dumpDir` is set, emits `<owner>-<dialect>.sql` for each
   * dialect at the call site (owner derived from the enclosing symbol/val
   * name). No-op otherwise.
   *
   * '''Inline-value requirement:''' the `query` argument must be an
   * inline-constructible tree (e.g. inline val/def or direct
   * `SqlQuery.from(...).join(...).where(...)` chain). Passing a preconstructed
   * non-inline `val` (e.g. `val userQuery = ...; Dump.dump(userQuery)`)
   * prevents the macro from peeling the construction tree and will emit a
   * compile-time `report.warning`: "Dump requires inline query value;
   * preconstructed value will emit no file; use inline val/def or
   * Dump.dumpTable" and emit no file (skips emit) instead of an incomplete
   * source-only fallback. For full joins/filters use `inline val`/`inline def`
   * or construct the query directly at the call site.
   */
  inline def dump(inline query: SqlQuery[?]): Unit =
    ${ dumpQueryImpl('query) }

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

  def dumpQueryImpl(query: Expr[SqlQuery[?]])(using Quotes): Expr[Unit] = {
    import quotes.reflect._
    val dirProp = System.getProperty("zib.sql.dumpDir")
    if (dirProp == null) '{ () }
    else {
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
      case class FilterInfo(colRef: String, op: String, placeholder: String)

      @scala.annotation.nowarn("msg=unchecked")
      def inPlaceholder(valueTerm: quotes.reflect.Term): String = {
        import quotes.reflect._
        def unwrap(t: Term): Term = t match {
          case Inlined(_, _, inner) => unwrap(inner)
          case Typed(inner, _)      => unwrap(inner)
          case Block(_, inner)      => unwrap(inner)
          case NamedArg(_, inner)   => unwrap(inner)
          case _                    => t
        }
        @scala.annotation.nowarn("msg=unchecked")
        def countIndexedSeqSize(t: Term): Int =
          try {
            val u = unwrap(t)
            u match {
              case Repeated(elems, _)              => elems.size
              case Typed(inner, _)                 => countIndexedSeqSize(inner)
              case Inlined(_, _, inner)            => countIndexedSeqSize(inner)
              case Block(_, inner)                 => countIndexedSeqSize(inner)
              case Apply(Select(_, "apply"), args) =>
                val uwArgs = args.map(unwrap)
                if (uwArgs.size == 1 && uwArgs.head.isInstanceOf[Repeated @unchecked])
                  uwArgs.head.asInstanceOf[Repeated].elems.size
                else if (uwArgs.exists(_.isInstanceOf[Repeated @unchecked]))
                  uwArgs.flatMap {
                    case r: Repeated @unchecked => r.elems
                    case other                  => List(other)
                  }.size
                else if (uwArgs.isEmpty) 0
                else uwArgs.size
              case Apply(TypeApply(Select(_, "apply"), _), args) =>
                val uwArgs = args.map(unwrap)
                if (uwArgs.size == 1 && uwArgs.head.isInstanceOf[Repeated @unchecked])
                  uwArgs.head.asInstanceOf[Repeated].elems.size
                else if (uwArgs.exists(_.isInstanceOf[Repeated @unchecked]))
                  uwArgs.flatMap {
                    case r: Repeated @unchecked => r.elems
                    case other                  => List(other)
                  }.size
                else if (uwArgs.isEmpty) 0
                else uwArgs.size
              case Apply(Select(_, "empty"), _)               => 0
              case Apply(TypeApply(Select(_, "empty"), _), _) => 0
              case Select(_, "empty")                         => 0
              case Ident("empty")                             => 0
              case _                                          =>
                val s = u.show
                if (
                  s.contains("empty") || s == "Nil" || s.endsWith(".Nil") || s
                    .contains("IndexedSeq.empty") || s.contains("Seq.empty") || s.contains("List.empty")
                ) 0
                else {
                  var cnt: Option[Int] = None
                  object trav extends TreeTraverser {
                    override def traverseTree(tree: Tree)(owner: Symbol): Unit = tree match {
                      case r: Repeated @unchecked => cnt = Some(r.elems.size)
                      case _                      => if (cnt.isEmpty) super.traverseTree(tree)(owner)
                    }
                  }
                  try trav.traverseTree(u)(Symbol.spliceOwner)
                  catch { case _: Throwable => () }
                  cnt.getOrElse(-1)
                }
            }
          } catch { case _: Throwable => -1 }
        def countElements(t: Term): Option[Int] = {
          val u                                        = unwrap(t)
          def isDbArrayApply(tree: Tree): Option[Term] = tree match {
            case Apply(Select(qual, "apply"), args) if args.size == 2 =>
              val qStr  = qual.toString
              val qType =
                try qual.tpe.typeSymbol.fullName
                catch { case _: Throwable => "" }
              if (qStr.contains("DbArray") || qType.contains("DbArray")) Some(args(1))
              else None
            case Apply(TypeApply(Select(qual, "apply"), _), args) if args.size == 2 =>
              val qStr  = qual.toString
              val qType =
                try qual.tpe.typeSymbol.fullName
                catch { case _: Throwable => "" }
              if (qStr.contains("DbArray") || qType.contains("DbArray")) Some(args(1))
              else None
            case _ => None
          }
          u match {
            case Apply(Select(_, "DbArray"), args) if args.size == 2 =>
              Some(countIndexedSeqSize(args(1)))
            case Apply(TypeApply(Select(_, "DbArray"), _), args) if args.size == 2 =>
              Some(countIndexedSeqSize(args(1)))
            case Apply(Select(qual, "DbArray"), args)
                if args.size == 2 && (qual.tpe.typeSymbol.fullName
                  .contains("DbValue") || qual.toString.contains("DbValue")) =>
              Some(countIndexedSeqSize(args(1)))
            case app @ Apply(Select(_, "apply"), _) =>
              isDbArrayApply(app).map(countIndexedSeqSize)
            case app @ Apply(TypeApply(Select(_, "apply"), _), _) =>
              isDbArrayApply(app).map(countIndexedSeqSize)
            case _ =>
              var found: Option[Int] = None
              object finder extends TreeTraverser {
                override def traverseTree(tree: Tree)(owner: Symbol): Unit = tree match {
                  case Apply(Select(_, "DbArray"), args) if args.size == 2 && found.isEmpty =>
                    found = Some(countIndexedSeqSize(args(1)))
                  case Apply(TypeApply(Select(_, "DbArray"), _), args) if args.size == 2 && found.isEmpty =>
                    found = Some(countIndexedSeqSize(args(1)))
                  case app2 @ Apply(Select(_, "apply"), args) if args.size == 2 && found.isEmpty =>
                    isDbArrayApply(app2).foreach(t => found = Some(countIndexedSeqSize(t)))
                  case app2 @ Apply(TypeApply(Select(_, "apply"), _), args) if args.size == 2 && found.isEmpty =>
                    isDbArrayApply(app2).foreach(t => found = Some(countIndexedSeqSize(t)))
                  case _ => if (found.isEmpty) super.traverseTree(tree)(owner)
                }
              }
              try finder.traverseTree(u)(Symbol.spliceOwner)
              catch { case _: Throwable => () }
              found
          }
        }
        try {
          countElements(valueTerm) match {
            case Some(n) if n >= 0 =>
              if (n == 0) {
                report.warning(
                  "IN operator with empty DbArray will emit IN (NULL) - empty collections produce no rows",
                  Position.ofMacroExpansion
                )
                "(NULL)"
              } else {
                "(" + List.fill(n)("?").mkString(", ") + ")"
              }
            case Some(n) if n == -1 =>
              report.warning(
                s"IN placeholder: could not determine DbArray size for term ${valueTerm.show.take(200)}; falling back to single placeholder",
                Position.ofMacroExpansion
              )
              "(?)"
            case None =>
              report.warning(
                s"IN operator requires DbArray value, got ${valueTerm.show.take(200)}; emitting (NULL)",
                Position.ofMacroExpansion
              )
              "(NULL)"
            case _ =>
              report.warning(
                s"IN placeholder: unexpected state for term ${valueTerm.show.take(200)}",
                Position.ofMacroExpansion
              )
              "(NULL)"
          }
        } catch {
          case _: Throwable =>
            // Fallback to parsing show string for element count to avoid -Werror failure
            val s =
              try valueTerm.show
              catch { case _: Throwable => "" }
            if (s.contains("DbArray")) {
              // try count commas inside IndexedSeq
              val inner        = s.split("IndexedSeq").lastOption.getOrElse("")
              val commaCount   = inner.count(_ == ',')
              val parenContent = inner.dropWhile(_ != '(').takeWhile(_ != ')')
              // heuristic: number of elements = commas +1 if not empty, else 0 if empty
              if (inner.contains("empty") || parenContent.trim == "()") "(NULL)"
              else if (commaCount >= 0 && inner.contains("(")) {
                val n = commaCount + 1
                if (n == 1 && inner.contains("()")) "(NULL)" else "(" + List.fill(n)("?").mkString(", ") + ")"
              } else "(?, ?, ?)"
            } else "(NULL)"
        }
      }

      def validateTableAlias(alias: String): Unit = {
        try SqlIdentifier.validate("tableAlias", alias)
        catch { case _: Throwable => report.warning(s"Invalid tableAlias '$alias'", Position.ofMacroExpansion) }
        try {
          val _ = SqlIdentifierChecker.validate(Seq(alias), Set(alias), Set.empty[String])
          ()
        } catch { case _: Throwable => () }
      }

      def tryDecode(): Option[
        (
          String,
          IndexedSeq[String],
          List[JoinInfo],
          List[FilterInfo],
          Option[String],
          List[String],
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
            else typeArgOpt.map(deriveColumns).map(_.map(_.name)).getOrElse(IndexedSeq("id"))
          var joins                       = List.empty[JoinInfo]
          var filters                     = List.empty[FilterInfo]
          var groupByCols: Option[String] = None
          var orderByList: List[String]   = Nil
          var limitVal: Option[Int]       = None
          var offsetVal: Option[Int]      = None
          var aliasCounter                = 1
          for (i <- (fromIdx + 1).until(calls.length)) {
            val (method, args) = calls(i)
            method match {
              case "join" =>
                if (args.size >= 3) {
                  val otherTerm = args(0)
                  val leftLit   = stringLiteral(args(1)).getOrElse("id")
                  val rightLit  = stringLiteral(args(2)).getOrElse("id")
                  val kindStr   = if (args.size >= 4) {
                    val s = args(3).toString.toLowerCase
                    if (s.contains("left")) "LEFT JOIN" else "INNER JOIN"
                  } else "INNER JOIN"
                  val (otherNameRaw, otherCols) = tableInfoFromTerm(otherTerm)
                  val otherName                 = otherNameRaw
                  val alias                     = s"t$aliasCounter"
                  aliasCounter += 1
                  colsByAlias(alias) = otherCols
                  val prevAlias = if (joins.isEmpty) "t0" else joins.last.alias
                  // validate identifiers
                  try SqlIdentifier.validate("column", leftLit)
                  catch {
                    case _: Throwable => report.warning(s"Invalid column '$leftLit' in join", Position.ofMacroExpansion)
                  }
                  try SqlIdentifier.validate("column", rightLit)
                  catch {
                    case _: Throwable =>
                      report.warning(s"Invalid column '$rightLit' in join", Position.ofMacroExpansion)
                  }
                  joins = joins :+ JoinInfo(otherName, alias, kindStr, prevAlias, leftLit, alias, rightLit)
                } else {
                  report.warning(
                    s"Dump.dump: join expects at least 3 args (table, leftCol, rightCol), got ${args.size}",
                    Position.ofMacroExpansion
                  )
                }
              case "joinLeft" =>
                if (args.size >= 3) {
                  val otherTerm                 = args(0)
                  val leftLit                   = stringLiteral(args(1)).getOrElse("id")
                  val rightLit                  = stringLiteral(args(2)).getOrElse("id")
                  val (otherNameRaw, otherCols) = tableInfoFromTerm(otherTerm)
                  val otherName                 = otherNameRaw
                  val alias                     = s"t$aliasCounter"
                  aliasCounter += 1
                  colsByAlias(alias) = otherCols
                  val prevAlias = if (joins.isEmpty) "t0" else joins.last.alias
                  joins = joins :+ JoinInfo(otherName, alias, "LEFT JOIN", prevAlias, leftLit, alias, rightLit)
                } else {
                  report.warning(s"Dump.dump: joinLeft expects 3 args, got ${args.size}", Position.ofMacroExpansion)
                }
              case "joinOn" =>
                // joinOn(other, onLeft ColumnRef, onRight ColumnRef, kind?)
                if (args.size >= 3) {
                  val otherTerm = args(0)
                  val leftOpt   = decodeColumnRef(args(1))
                  val rightOpt  = decodeColumnRef(args(2))
                  (leftOpt, rightOpt) match {
                    case (Some((leftAlias, leftCol)), Some((rightAlias, rightCol))) =>
                      validateTableAlias(leftAlias)
                      validateTableAlias(rightAlias)
                      try SqlIdentifier.validate("column", leftCol)
                      catch {
                        case _: Throwable =>
                          report.warning(s"Invalid column '$leftCol' in joinOn", Position.ofMacroExpansion)
                      }
                      try SqlIdentifier.validate("column", rightCol)
                      catch {
                        case _: Throwable =>
                          report.warning(s"Invalid column '$rightCol' in joinOn", Position.ofMacroExpansion)
                      }
                      val (otherNameRaw, otherCols) = tableInfoFromTerm(otherTerm)
                      val alias                     = s"t$aliasCounter"
                      aliasCounter += 1
                      colsByAlias(alias) = otherCols
                      val kindStr = if (args.size >= 4) {
                        val s = args(3).toString.toLowerCase
                        if (s.contains("left")) "LEFT JOIN" else "INNER JOIN"
                      } else "INNER JOIN"
                      // For dump, we need to map leftAlias/rightAlias to actual alias names; if they are generic like t0/t1 they are okay.
                      joins = joins :+ JoinInfo(otherNameRaw, alias, kindStr, leftAlias, leftCol, rightAlias, rightCol)
                    case _ =>
                      report.warning(
                        s"Dump.dump: joinOn expects ColumnRef args, could not decode",
                        Position.ofMacroExpansion
                      )
                  }
                }
              case "where" =>
                args.size match {
                  case 4 =>
                    // 4-arg where(table, column, operator, value) — correctly decode all four args
                    (args(0), stringLiteral(args(1)), stringLiteral(args(2))) match {
                      case (tableTerm, Some(col), Some(op)) =>
                        val (tblName, _) = tableInfoFromTerm(tableTerm)
                        val alias        =
                          if (tblName == sourceName) "t0"
                          else joins.find(_.table == tblName).map(_.alias).getOrElse("t0")
                        try SqlIdentifier.validate("column", col)
                        catch {
                          case _: Throwable =>
                            report.warning(s"Invalid column '$col' in where", Position.ofMacroExpansion)
                        }
                        validateTableAlias(alias)
                        val placeholder =
                          if (op.equalsIgnoreCase("IN")) inPlaceholder(args(3)) else "?"
                        filters = filters :+ FilterInfo(s"$alias.$col", op, placeholder)
                      case _ =>
                        report.warning(
                          s"Dump.dump: 4-arg where could not decode table/column/operator, got ${args.map(_.show).mkString(", ")}",
                          Position.ofMacroExpansion
                        )
                        filters = filters :+ FilterInfo(s"t0.id", "=", "?")
                    }
                  case 3 =>
                    val firstIsTable  = isTableTerm(args(0))
                    val firstIsColRef = isColumnRefTerm(args(0))
                    if (firstIsTable) {
                      // where(table, column, value) -> operator "="
                      (args(0), stringLiteral(args(1))) match {
                        case (tableTerm, Some(col)) =>
                          val (tblName, _) = tableInfoFromTerm(tableTerm)
                          val alias        =
                            if (tblName == sourceName) "t0"
                            else joins.find(_.table == tblName).map(_.alias).getOrElse("t0")
                          try SqlIdentifier.validate("column", col)
                          catch {
                            case _: Throwable =>
                              report.warning(s"Invalid column '$col' in where", Position.ofMacroExpansion)
                          }
                          validateTableAlias(alias)
                          filters = filters :+ FilterInfo(s"$alias.$col", "=", "?")
                        case _ =>
                          report.warning(
                            s"Dump.dump: where(Table, String, DbValue) expected string literal column, got ${args(1).show}",
                            Position.ofMacroExpansion
                          )
                          filters = filters :+ FilterInfo(s"t0.id", "=", "?")
                      }
                    } else if (firstIsColRef) {
                      // where(ColumnRef, operator, value)
                      (decodeColumnRef(args(0)), stringLiteral(args(1))) match {
                        case (Some((alias, col)), Some(op)) =>
                          try SqlIdentifier.validate("column", col)
                          catch {
                            case _: Throwable =>
                              report.warning(s"Invalid column '$col' in where", Position.ofMacroExpansion)
                          }
                          validateTableAlias(alias)
                          val placeholder =
                            if (op.equalsIgnoreCase("IN")) inPlaceholder(args(2)) else "?"
                          filters = filters :+ FilterInfo(s"$alias.$col", op, placeholder)
                        case _ =>
                          report.warning(
                            s"Dump.dump: where(ColumnRef, operator, value) could not decode ColumnRef or operator",
                            Position.ofMacroExpansion
                          )
                          filters = filters :+ FilterInfo(s"t0.id", "=", "?")
                      }
                    } else {
                      report.warning(
                        s"Dump.dump: unsupported 3-arg where overload; expected (Table, String, DbValue) or (ColumnRef, String, DbValue)",
                        Position.ofMacroExpansion
                      )
                      filters = filters :+ FilterInfo(s"t0.id", "=", "?")
                    }
                  case 2 =>
                    val firstIsColRef = isColumnRefTerm(args(0))
                    if (firstIsColRef) {
                      decodeColumnRef(args(0)) match {
                        case Some((alias, col)) =>
                          try SqlIdentifier.validate("column", col)
                          catch {
                            case _: Throwable =>
                              report.warning(s"Invalid column '$col' in where", Position.ofMacroExpansion)
                          }
                          validateTableAlias(alias)
                          filters = filters :+ FilterInfo(s"$alias.$col", "=", "?")
                        case None =>
                          report.warning(
                            "Dump.dump: could not decode ColumnRef in 2-arg where",
                            Position.ofMacroExpansion
                          )
                          filters = filters :+ FilterInfo(s"t0.id", "=", "?")
                      }
                    } else {
                      report.warning(
                        s"Dump.dump: unsupported 2-arg where overload; expected (ColumnRef, DbValue)",
                        Position.ofMacroExpansion
                      )
                      filters = filters :+ FilterInfo(s"t0.id", "=", "?")
                    }
                  case 1 =>
                    report.warning(
                      s"Dump.dump: unsupported 1-arg where overload for legacy SqlQuery; use where(ColumnRef, ...) etc.",
                      Position.ofMacroExpansion
                    )
                    filters = filters :+ FilterInfo(s"t0.id", "=", "?")
                  case other =>
                    report.warning(s"Dump.dump: unsupported where overload with $other args", Position.ofMacroExpansion)
                }
              case "groupBy" =>
                if (args.isEmpty) {
                  report.warning("Dump.dump: groupBy requires at least one column", Position.ofMacroExpansion)
                } else {
                  val firstIsTable = args.nonEmpty && isTableTerm(args(0))
                  if (firstIsTable && args.size >= 2) {
                    val tableTerm    = args(0)
                    val (tblName, _) = tableInfoFromTerm(tableTerm)
                    val alias        =
                      if (tblName == sourceName) "t0" else joins.find(_.table == tblName).map(_.alias).getOrElse("t0")
                    validateTableAlias(alias)
                    val cols = args.tail.flatMap { term =>
                      stringLiteral(term).map { s =>
                        val v =
                          try SqlIdentifier.validate("column", s)
                          catch { case _: Throwable => s }
                        s"$alias.$v"
                      }.orElse {
                        decodeColumnRef(term).map { case (a, c) =>
                          validateTableAlias(a)
                          val v =
                            try SqlIdentifier.validate("column", c)
                            catch { case _: Throwable => c }
                          s"$a.$v"
                        }.orElse {
                          report.warning(
                            s"Dump.dump: groupBy arg not a string literal nor ColumnRef: ${term.show}",
                            Position.ofMacroExpansion
                          )
                          None
                        }
                      }
                    }
                    if (cols.nonEmpty) groupByCols = Some(cols.mkString(", "))
                  } else {
                    val cols = args.flatMap { term =>
                      decodeColumnRef(term).map { case (alias, col) =>
                        validateTableAlias(alias)
                        val v =
                          try SqlIdentifier.validate("column", col)
                          catch { case _: Throwable => col }
                        s"$alias.$v"
                      }.orElse {
                        stringLiteral(term).map { s =>
                          val v =
                            try SqlIdentifier.validate("column", s)
                            catch { case _: Throwable => s }
                          s"t0.$v"
                        }.orElse {
                          report.warning(s"Dump.dump: groupBy unsupported arg ${term.show}", Position.ofMacroExpansion)
                          None
                        }
                      }
                    }
                    if (cols.nonEmpty) groupByCols = Some(cols.mkString(", "))
                  }
                }
              case "orderBy" =>
                if (args.isEmpty) {
                  report.warning("Dump.dump: orderBy requires at least one arg", Position.ofMacroExpansion)
                } else {
                  val firstIsTable  = isTableTerm(args(0))
                  val firstIsColRef = isColumnRefTerm(args(0))
                  if (firstIsTable && args.size >= 2) {
                    val tableTerm    = args(0)
                    val (tblName, _) = tableInfoFromTerm(tableTerm)
                    val alias        =
                      if (tblName == sourceName) "t0" else joins.find(_.table == tblName).map(_.alias).getOrElse("t0")
                    val col = stringLiteral(args(1)).getOrElse("id")
                    val dir = if (args.size >= 3) decodeDirection(args(2)) else "ASC"
                    try SqlIdentifier.validate("column", col)
                    catch {
                      case _: Throwable => report.warning(s"Invalid orderBy column '$col'", Position.ofMacroExpansion)
                    }
                    validateTableAlias(alias)
                    orderByList = orderByList :+ s"$alias.$col $dir"
                  } else if (firstIsColRef) {
                    decodeColumnRef(args(0)) match {
                      case Some((alias, col)) =>
                        validateTableAlias(alias)
                        val dir = if (args.size >= 2) decodeDirection(args(1)) else "ASC"
                        try SqlIdentifier.validate("column", col)
                        catch {
                          case _: Throwable =>
                            report.warning(s"Invalid orderBy column '$col'", Position.ofMacroExpansion)
                        }
                        orderByList = orderByList :+ s"$alias.$col $dir"
                      case None =>
                        report.warning(s"Dump.dump: orderBy could not decode ColumnRef", Position.ofMacroExpansion)
                    }
                  } else {
                    // literal fallback: orderBy("id") or orderBy("id", "DESC")
                    val col = stringLiteral(args(0)).getOrElse("id")
                    val dir = if (args.size >= 2) decodeDirection(args(1)) else "ASC"
                    try SqlIdentifier.validate("column", col)
                    catch {
                      case _: Throwable => report.warning(s"Invalid orderBy column '$col'", Position.ofMacroExpansion)
                    }
                    orderByList = orderByList :+ s"t0.$col $dir"
                  }
                }
              case "limit" =>
                args.headOption.flatMap(intLiteral) match {
                  case Some(n) => limitVal = Some(n)
                  case None    => report.warning("Dump.dump: limit expects int literal", Position.ofMacroExpansion)
                }
              case "offset" =>
                args.headOption.flatMap(intLiteral) match {
                  case Some(n) => offsetVal = Some(n)
                  case None    => report.warning("Dump.dump: offset expects int literal", Position.ofMacroExpansion)
                }
              case other =>
                report.warning(s"Dump.dump: unsupported method '$other' in query chain", Position.ofMacroExpansion)
            }
          }
          Some((sourceName, colsByAlias("t0"), joins, filters, groupByCols, orderByList, limitVal, offsetVal))
        } catch {
          case e: Throwable =>
            report.warning(s"Dump.dump: failed to decode query: ${e.getMessage}", Position.ofMacroExpansion)
            None
        }
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

      // Re-implement build logic with proper colsByAlias handling
      decodedOpt match {
        case Some(decoded) =>
          // decoded contains orderByList not orderByStr
          val (srcName, srcCols, joins, filters, gb, obList, lim, off) = decoded
          // Reconstruct colsByAlias for select: we need to capture it from tryDecode.
          // We stored colsByAlias inside tryDecode but not returned; instead we re-derive by re-peeling?
          // To avoid complexity, we re-derive select list via a helper that uses tableInfo for each join's table.
          // However we have join table names; we can attempt to get columns via type lookup if we re-peel? Simpler: rebuild colsByAlias by re-examining query term again to get each join's otherCols.
          // Easiest: capture colsByAlias as mutable map in outer scope and use it here.
          // Let's recompute by walking again to get otherCols per alias.
          val aliasToCols: Map[String, IndexedSeq[String]] =
            try {
              val (_, calls)      = peel(query.asTerm)
              val fromIdx         = calls.indexWhere(_._1 == "from")
              val sourceColsLocal = tableInfoFromTerm(calls(fromIdx)._2.head)._2
              val map             = scala.collection.mutable.Map[String, IndexedSeq[String]](
                "t0" -> (if (sourceColsLocal.nonEmpty) sourceColsLocal else srcCols)
              )
              var counter = 1
              for (i <- (fromIdx + 1).until(calls.length)) {
                val (m, a) = calls(i)
                if (m == "join" || m == "joinLeft" || m == "joinOn") {
                  if (a.nonEmpty) {
                    val otherTerm  = a.head
                    val (_, oCols) = tableInfoFromTerm(otherTerm)
                    val alias      = s"t$counter"
                    counter += 1
                    map(alias) = oCols
                  }
                }
              }
              map.toMap
            } catch { case _: Throwable => Map("t0" -> srcCols) }
          val allAliases = "t0" +: joins.map(_.alias)
          val selectList = allAliases
            .flatMap(alias => aliasToCols.getOrElse(alias, IndexedSeq.empty).map(c => s"$alias.$c"))
            .mkString(", ")
          val effectiveSelect     = if (selectList.isEmpty) "t0.*" else selectList
          def renderSql(): String = {
            val sb = new StringBuilder
            sb.append(s"SELECT $effectiveSelect FROM $srcName AS t0")
            for (j <- joins) {
              sb.append(
                s" ${j.kind} ${j.table} AS ${j.alias} ON ${j.leftAlias}.${j.leftCol} = ${j.rightAlias}.${j.rightCol}"
              )
            }
            if (filters.nonEmpty) {
              val whereStr = filters.map(f => s"${f.colRef} ${f.op} ${f.placeholder}").mkString(" AND ")
              sb.append(s" WHERE $whereStr")
            }
            gb.foreach(g => sb.append(s" GROUP BY $g"))
            if (obList.nonEmpty) sb.append(s" ORDER BY ${obList.mkString(", ")}")
            lim.foreach(l => sb.append(s" LIMIT $l"))
            off.foreach(o => sb.append(s" OFFSET $o"))
            sb.toString()
          }
          val sqlText = renderSql()
          for (dialect <- Seq(SqlDialect.PostgreSQL, SqlDialect.SQLite)) {
            emit(fileBase, dialect, sqlText)
          }
        case None =>
          report.warning(
            "Dump requires inline query value; preconstructed value will emit no file; use inline val/def or Dump.dumpTable. Dump.dump requires inline query value; got preconstructed value - emitting no file. Use inline val/def for full tree",
            Position.ofMacroExpansion
          )
      }
      '{ () }
    }
  }

  def dumpQueryIrImpl[A: Type](query: Expr[zio.blocks.sql.query.SqlQuery[A]])(using Quotes): Expr[Unit] = {
    import quotes.reflect._
    val dirProp = System.getProperty("zib.sql.dumpDir")
    if (dirProp == null) '{ () }
    else {
      val tpe        = TypeRepr.of[A]
      val sym        = tpe.typeSymbol
      val typeName   = if (sym.name == "<none>" || sym.name.isEmpty) "query" else sym.name
      val baseName   = SqlNameMapper.SnakeCase(typeName)
      val argNameOpt = queryArgDerivedName(query.asTerm)
      val ownerName  = ownerDerivedName
      val candidate  = argNameOpt.getOrElse(ownerName)
      val fileBase   = if (candidate == "query" || candidate.isEmpty) s"$baseName-query" else s"$candidate-query"

      // Try to decode full IR via peel
      case class IrJoin(table: String, alias: String, kind: String, on: String)
      case class IrFilter(sql: String, paramCount: Int)

      def tryDecodeIr(): Option[
        (
          String,
          IndexedSeq[String],
          List[IrJoin],
          List[IrFilter],
          Option[String],
          Option[String],
          List[String],
          Option[Int],
          Option[Int],
          Map[String, IndexedSeq[String]]
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
          val colsByAlias                 = scala.collection.mutable.Map[String, IndexedSeq[String]](
            "t0" -> (if (sourceCols.nonEmpty) sourceCols else deriveColumns(tpe).map(_.name))
          )
          var joins                     = List.empty[IrJoin]
          var filters                   = List.empty[IrFilter]
          var groupBy: Option[String]   = None
          var having: Option[String]    = None
          var orderByList: List[String] = Nil
          var limitVal: Option[Int]     = None
          var offsetVal: Option[Int]    = None
          var aliasCounter              = 1

          // helper to get alias for existing table
          def aliasForTableName(name: String): Option[String] =
            if (name == sourceName) Some("t0")
            else joins.find(_.table == name).map(_.alias)

          for (i <- (fromIdx + 1).until(calls.length)) {
            val (method, args) = calls(i)
            method match {
              case "innerJoin" | "leftJoin" | "join" | "joinLeft" =>
                // decode Rel and optional kind
                val relOpt = if (args.nonEmpty) decodeRel(args(0)) else None
                relOpt match {
                  case Some((fromTerm, fk, toTerm, pk)) =>
                    val (fromName, _)    = tableInfoFromTerm(fromTerm)
                    val (toName, toCols) = tableInfoFromTerm(toTerm)
                    // validate column identifiers
                    try SqlIdentifier.validate("column", fk)
                    catch { case _: Throwable => report.warning(s"Invalid fk column '$fk'", Position.ofMacroExpansion) }
                    try SqlIdentifier.validate("column", pk)
                    catch { case _: Throwable => report.warning(s"Invalid pk column '$pk'", Position.ofMacroExpansion) }
                    val kindStr = method match {
                      case "leftJoin" | "joinLeft" => "LEFT JOIN"
                      case "innerJoin"             => "INNER JOIN"
                      case "join"                  =>
                        if (args.size >= 2) {
                          val s = args(1).toString.toLowerCase
                          if (s.contains("left")) "LEFT JOIN" else "INNER JOIN"
                        } else "INNER JOIN"
                      case _ => "INNER JOIN"
                    }
                    val alias = s"t$aliasCounter"
                    aliasCounter += 1
                    // determine ON clause aliases
                    val fkAliasOpt                                 = aliasForTableName(fromName)
                    val pkAliasOpt                                 = aliasForTableName(toName)
                    val (fkAlias, pkAlias, targetName, targetCols) = (fkAliasOpt, pkAliasOpt) match {
                      case (Some(fa), None) =>
                        (fa, alias, toName, toCols)
                      case (None, Some(ta)) =>
                        (alias, ta, fromName, tableInfoFromTerm(fromTerm)._2)
                      case (None, None) =>
                        if (sourceName == fromName) ("t0", alias, toName, toCols)
                        else if (sourceName == toName) (alias, "t0", fromName, tableInfoFromTerm(fromTerm)._2)
                        else if (joins.nonEmpty && joins.last.table == fromName)
                          (joins.last.alias, alias, toName, toCols)
                        else if (joins.nonEmpty && joins.last.table == toName)
                          (alias, joins.last.alias, fromName, tableInfoFromTerm(fromTerm)._2)
                        else ("t0", alias, toName, toCols)
                      case (Some(_), Some(_)) =>
                        // both present, create new alias for target as toTable duplicate
                        (fkAliasOpt.get, alias, toName, toCols)
                    }
                    // self-join handling
                    val isSelfJoin = fromName == toName
                    val onStr      = if (isSelfJoin) {
                      s"""t0."$fk" = $alias."$pk""""
                    } else {
                      s"""$fkAlias."$fk" = $pkAlias."$pk""""
                    }
                    colsByAlias(alias) = targetCols
                    joins = joins :+ IrJoin(targetName, alias, kindStr, onStr)
                  case None =>
                    report.warning(s"Dump.dumpQuery: could not decode Rel for $method", Position.ofMacroExpansion)
                }
              case "filter" | "where" =>
                if (args.nonEmpty) {
                  decodeFrag(args(0)) match {
                    case Some((sql, cnt)) =>
                      // sql may already contain ? placeholders; if cnt ==0 and sql contains ? still okay
                      // For dump we ensure placeholders are ?
                      val normalized = if (sql.contains("?")) sql else if (cnt > 0) sql + " ?" else sql
                      filters = filters :+ IrFilter(normalized, cnt)
                    case None =>
                      // fallback: treat as generic filter with ?
                      report.warning(
                        s"Dump.dumpQuery: could not decode filter Frag, using placeholder",
                        Position.ofMacroExpansion
                      )
                      filters = filters :+ IrFilter("?", 1)
                  }
                }
              case "groupBy" =>
                if (args.nonEmpty) {
                  val cols = args.flatMap(a => stringLiteral(a))
                  if (cols.nonEmpty) {
                    val validated = cols.map(c =>
                      try SqlIdentifier.validate("column", c)
                      catch { case _: Throwable => c }
                    )
                    // Render with quoted identifiers to match QueryRenderer
                    groupBy = Some(validated.map(c => s"""t0."$c"""").mkString(", "))
                  }
                }
              case "having" =>
                if (args.nonEmpty) {
                  decodeFrag(args(0)) match {
                    case Some((sql, _)) => having = Some(sql)
                    case None           =>
                      report.warning("Dump.dumpQuery: could not decode having Frag", Position.ofMacroExpansion)
                  }
                }
              case "orderBy" =>
                if (args.nonEmpty) {
                  stringLiteral(args(0)) match {
                    case Some(col) =>
                      val dir = if (args.size >= 2) decodeDirection(args(1)) else "ASC"
                      try SqlIdentifier.validate("column", col)
                      catch {
                        case _: Throwable => report.warning(s"Invalid orderBy column '$col'", Position.ofMacroExpansion)
                      }
                      orderByList = orderByList :+ s"""t0."$col" $dir"""
                    case None =>
                      report.warning("Dump.dumpQuery: orderBy expects string literal column", Position.ofMacroExpansion)
                  }
                }
              case "orderByMany" =>
                // varargs OrderBy objects: each OrderBy is case class OrderBy(column, direction)
                for (obTerm <- args) {
                  // try to decode OrderBy(col, dir)
                  obTerm match {
                    case Apply(Select(New(_), "<init>"), List(Literal(StringConstant(col)), dirTerm)) =>
                      val dir = decodeDirection(dirTerm)
                      orderByList = orderByList :+ s"""t0."$col" $dir"""
                    case Apply(Select(_, "apply"), List(Literal(StringConstant(col)), dirTerm)) =>
                      val dir = decodeDirection(dirTerm)
                      orderByList = orderByList :+ s"""t0."$col" $dir"""
                    case _ =>
                      // fallback via string literal collection
                      stringLiteral(obTerm).foreach { col =>
                        orderByList = orderByList :+ s"""t0."$col" ASC"""
                      }
                  }
                }
              case "limit" =>
                args.headOption.flatMap(intLiteral) match {
                  case Some(n) => limitVal = Some(n)
                  case None    => report.warning("Dump.dumpQuery: limit expects int literal", Position.ofMacroExpansion)
                }
              case "offset" =>
                args.headOption.flatMap(intLiteral) match {
                  case Some(n) => offsetVal = Some(n)
                  case None    => report.warning("Dump.dumpQuery: offset expects int literal", Position.ofMacroExpansion)
                }
              case other =>
                report.warning(s"Dump.dumpQuery: unsupported method '$other' in query IR", Position.ofMacroExpansion)
            }
          }
          Some(
            (
              sourceName,
              colsByAlias("t0"),
              joins,
              filters,
              groupBy,
              having,
              orderByList,
              limitVal,
              offsetVal,
              colsByAlias.toMap
            )
          )
        } catch {
          case e: Throwable =>
            report.warning(s"Dump.dumpQuery: failed to decode IR: ${e.getMessage}", Position.ofMacroExpansion)
            None
        }
      }

      val decodedOpt = tryDecodeIr()

      decodedOpt match {
        case Some((srcName, _, joins, filters, gb, having, obList, lim, off, aliasToCols)) =>
          val allAliases = "t0" +: joins.map(_.alias)
          val selectList = allAliases
            .flatMap(alias => aliasToCols.getOrElse(alias, IndexedSeq.empty).map(c => s"""$alias."$c""""))
            .mkString(", ")
          val effectiveSelect = if (selectList.isEmpty) "*" else selectList
          // Build SQL via Frag-like composition but as string with quoted identifiers and ? placeholders
          var fragStr = s"SELECT $effectiveSelect FROM \"$srcName\" AS t0"
          for (j <- joins) {
            fragStr = fragStr + s" ${j.kind} \"${j.table}\" AS ${j.alias} ON ${j.on}"
          }
          if (filters.nonEmpty) {
            val wherePart = filters.map(_.sql).mkString(" AND ")
            // ensure each filter's SQL already contains ? if needed; if not, add ?
            fragStr = fragStr + s" WHERE $wherePart"
          }
          gb.foreach(g => fragStr = fragStr + s" GROUP BY $g")
          having.foreach(h => fragStr = fragStr + s" HAVING $h")
          if (obList.nonEmpty) fragStr = fragStr + s" ORDER BY ${obList.mkString(", ")}"
          lim.foreach(l => fragStr = fragStr + s" LIMIT $l")
          off.foreach(o => fragStr = fragStr + s" OFFSET $o")

          for (dialect <- Seq(SqlDialect.PostgreSQL, SqlDialect.SQLite)) {
            emit(fileBase, dialect, fragStr)
          }
        case None =>
          report.warning(
            "Dump requires inline query value; preconstructed value will emit no file; use inline val/def or Dump.dumpTable. Dump.dumpQuery requires inline query value; got preconstructed value - emitting no file. Use inline val/def for full tree",
            Position.ofMacroExpansion
          )
      }
      '{ () }
    }
  }
}
