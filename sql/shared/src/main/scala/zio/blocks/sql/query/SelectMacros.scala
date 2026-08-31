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

package zio.blocks.sql.query

import scala.quoted.{Expr as SExpr, Quotes, Type as SType, Varargs}

private[query] object SelectMacros {

  def selectImpl[T: SType](
    exprs: SExpr[Seq[Expr[?]]],
    codec: SExpr[zio.blocks.sql.DbCodec[T]],
    query: SExpr[SqlQuery[?]]
  )(using quotes: Quotes): SExpr[TypedQuery[T]] = {
    import quotes.reflect.*

    val varargsOpt                      = Varargs.unapply(exprs)
    val exprsList: List[SExpr[Expr[?]]] = varargsOpt match {
      case Some(seq) => seq.toList
      case None      =>
        report.errorAndAbort(s"select: could not extract varargs, got ${exprs.asTerm.show}")
    }

    if (exprsList.isEmpty) {
      report.errorAndAbort("select: empty projection")
    }

    if (exprsList.size > 8) {
      report.errorAndAbort(s"select: arity ${exprsList.size} exceeds maximum 8")
    }

    def exprResultType(e: SExpr[Expr[?]]): TypeRepr = {
      val tpe     = e.asTerm.tpe.widen
      val exprSym = TypeRepr.of[Expr[?]].typeSymbol
      // Try baseType for Expr
      val base = tpe.baseType(exprSym)
      base match {
        case AppliedType(_, List(arg)) => arg
        case _                         =>
          tpe match {
            case AppliedType(_, args) if args.nonEmpty => args.last
            case other                                 => other
          }
      }
    }

    val exprTypes: List[TypeRepr] = exprsList.map(exprResultType)

    val tpe = TypeRepr.of[T]

    def isTupleType(tr: TypeRepr): Boolean = tr <:< TypeRepr.of[Tuple]

    def flattenTupleType(tr: TypeRepr): List[TypeRepr] =
      if (tr =:= TypeRepr.of[EmptyTuple]) Nil
      else
        tr match {
          case AppliedType(_, List(head, tail)) if tr.typeSymbol.name == "*:" =>
            head :: flattenTupleType(tail)
          case AppliedType(_, args) if tr.typeSymbol.fullName.startsWith("scala.Tuple") =>
            args
          case _ => List(tr)
        }

    def isRecordType(tr: TypeRepr): Boolean = {
      val sym = tr.typeSymbol
      sym.isClassDef && sym.flags.is(Flags.Case) && !sym.flags.is(Flags.Enum)
    }

    def hasInlineAnnotation(sym: Symbol): Boolean =
      hasConfigAnnotation(sym, "sql.inline", "true") || sym.annotations.exists { annot =>
        val show = annot.show
        show.contains("sql.inline") && show.contains("\"true\"") && !show.contains("inline_fields")
      }

    def hasInlineFieldsAnnotation(sym: Symbol): Boolean =
      hasConfigAnnotation(sym, "sql.inline_fields", "true") || sym.annotations.exists { annot =>
        val show = annot.show
        show.contains("sql.inline_fields") && show.contains("\"true\"")
      }

    def hasConfigAnnotation(sym: Symbol, key: String, value: String): Boolean =
      sym.annotations.exists { term =>
        (term.tpe <:< TypeRepr.of[zio.blocks.schema.Modifier.config]) && {
          val strs = collectStringLiterals(term)
          strs.contains(key) && strs.contains(value)
        }
      }

    def collectStringLiterals(term: Term): List[String] = {
      object accu extends TreeAccumulator[List[String]] {
        def foldTree(x: List[String], tree: Tree)(owner: Symbol): List[String] = tree match {
          case Literal(StringConstant(s)) => s :: x
          case _                          => foldOverTree(x, tree)(owner)
        }
      }
      accu.foldTree(Nil, term)(Symbol.spliceOwner)
    }

    def flattenedRecordTypes(tr: TypeRepr): List[TypeRepr] = {
      val sym = tr.typeSymbol
      if (!sym.isClassDef || !sym.flags.is(Flags.Case)) return List(tr)
      val fields         = sym.caseFields
      val classInlineAll = hasInlineFieldsAnnotation(sym)
      fields.toList.flatMap { field =>
        val fieldTpe = tr.memberType(field)
        val isInline = hasInlineAnnotation(field) || (classInlineAll && isRecordType(fieldTpe))
        if (isRecordType(fieldTpe) && isInline) {
          flattenedRecordTypes(fieldTpe)
        } else {
          List(fieldTpe)
        }
      }
    }

    val expectedTypes: List[TypeRepr] =
      if (isTupleType(tpe)) {
        flattenTupleType(tpe)
      } else if (tpe.typeSymbol.isClassDef && tpe.typeSymbol.flags.is(Flags.Case)) {
        flattenedRecordTypes(tpe)
      } else {
        List(tpe)
      }

    if (exprTypes.size != expectedTypes.size) {
      report.errorAndAbort(
        s"select: projection arity ${exprTypes.size} does not match codec for ${SType.show[T]} with ${expectedTypes.size} columns (expected: ${expectedTypes.map(_.show).mkString(", ")}, got: ${exprTypes.map(_.show).mkString(", ")})"
      )
    }

    exprTypes.zip(expectedTypes).zipWithIndex.foreach { case ((actual, expected), idx) =>
      if (!(actual =:= expected)) {
        report.errorAndAbort(
          s"select: type mismatch at position ${idx + 1}: expected ${expected.show} for ${SType.show[T]}, got ${actual.show}"
        )
      }
    }

    val vecExpr: SExpr[Vector[Expr[?]]] = '{
      Vector(${ Varargs(exprsList) }: _*)
    }
    '{ TypedQuery.create[T]($query, $vecExpr.asInstanceOf[Vector[Expr[_]]], $codec) }
  }
}
