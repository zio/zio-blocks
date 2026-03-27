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

package zio.blocks.schema.migration

import scala.quoted.*
import scala.util.control.NonFatal
import zio.blocks.schema.DynamicOptic

object MigrationMacros {
  inline def select[A](inline f: A => Any): DynamicOptic = ${ selectImpl[A]('f) }

  def selectImpl[A: Type](f: Expr[A => Any])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    def loop(term: Term): List[DynamicOptic.Node] = term match {
      case Inlined(_, _, t) => loop(t)
      case Block(_, t)      => loop(t)
      case Select(parent, "each") =>
        loop(parent) :+ DynamicOptic.Node.Elements
      case TypeApply(Select(parent, "when"), List(tpe)) =>
        val caseName = tpe.tpe.typeSymbol.name
        loop(parent) :+ DynamicOptic.Node.Case(caseName)
      case Select(parent, field) if field != "apply" =>
        loop(parent) :+ DynamicOptic.Node.Field(field)
      case Ident(_) =>
        Nil
      case _ =>
        report.errorAndAbort(
          s"Invalid selector expression '${term.show}'. Only field selects, .when[T], and .each are supported."
        )
    }

    f.asTerm match {
      case Inlined(_, _, Block(List(DefDef(_, List(List(_)), _, Some(body))), _)) =>
        val nodes = loop(body)
        '{ DynamicOptic(${
          Expr.ofSeq(nodes.map {
            case DynamicOptic.Node.Field(name) => '{ DynamicOptic.Node.Field(${ Expr(name) }) }
            case DynamicOptic.Node.Case(name)  => '{ DynamicOptic.Node.Case(${ Expr(name) }) }
            case DynamicOptic.Node.Elements    => '{ DynamicOptic.Node.Elements }
            case _ =>
              report.errorAndAbort("Unsupported selector node in migration macro")
          })
        }.toIndexedSeq) }
      case _ =>
        report.errorAndAbort("Expected selector lambda of shape `x => x.foo.bar`")
    }
  }

  def buildImpl[A: Type, B: Type](builder: Expr[MigrationBuilder[A, B]])(using Quotes): Expr[Migration[A, B]] = {
    import quotes.reflect.*

    def targetFieldsOf(tpe: TypeRepr): Set[String] =
      tpe.dealias match {
        case Refinement(parent, name, _) => targetFieldsOf(parent) + name
        case other =>
          val params = other.typeSymbol.primaryConstructor.paramSymss.flatten.map(_.name).toSet
          if (params.nonEmpty) params
          else Set.empty
      }

    def extractTopField(term: Term): Option[String] = {
      def loop(t: Term): Option[String] = t match {
        case Inlined(_, _, inner) => loop(inner)
        case Block(_, expr) =>
          loop(expr)
        case Literal(StringConstant(name)) =>
          Some(name)
        case Apply(Select(New(tpt), _), List(Literal(StringConstant(name))))
            if tpt.tpe =:= TypeRepr.of[DynamicOptic.Node.Field] =>
          Some(name)
        case Apply(fn, args) =>
          loop(fn).orElse(args.view.flatMap(a => loop(a).toList).headOption)
        case Select(qual, _) =>
          loop(qual)
        case Typed(inner, _) =>
          loop(inner)
        case _ =>
          None
      }
      loop(term)
    }

    def baseBuilder(term: Term): Boolean = {
      val sym = term.symbol
      val full = sym.fullName
      val ownerAndName = s"${sym.owner.fullName}.${sym.name}"
      full == "zio.blocks.schema.migration.MigrationBuilder" ||
      full == "zio.blocks.schema.migration.Migration" ||
      ownerAndName.endsWith("MigrationBuilder.apply") ||
      ownerAndName.endsWith("Migration.newBuilder")
    }

    def extractHandled(term: Term): Option[Set[String]] = term match {
      case Inlined(_, _, inner) => extractHandled(inner)
      case Typed(inner, _)      => extractHandled(inner)
      case id: Ident =>
        id.symbol.tree match {
          case v: ValDef => v.rhs.flatMap(extractHandled)
          case _         => None
        }
      case Apply(Select(prev, method), args) =>
        extractHandled(prev).map { handled =>
          method match {
            case "addField" if args.nonEmpty =>
              handled ++ extractTopField(args.head)
            case "renameField" | "transformField" | "mandateField" | "optionalizeField" | "changeFieldType" | "inField"
                if args.length >= 2 =>
              handled ++ extractTopField(args(1))
            case _ =>
              handled
          }
        }
      case TypeApply(inner, _) =>
        extractHandled(inner)
      case Apply(inner, _) if baseBuilder(inner) =>
        Some(Set.empty)
      case inner if baseBuilder(inner) =>
        Some(Set.empty)
      case _ =>
        None
    }

    try {
      val targetFields  = targetFieldsOf(TypeRepr.of[B])
      val handledFields = extractHandled(builder.asTerm)
      handledFields match {
        case Some(handled) =>
          val unhandled = targetFields -- handled
          if (unhandled.nonEmpty)
            report.errorAndAbort(
              s"Migration.build: unhandled target fields in ${TypeRepr.of[B].show}: ${unhandled.toVector.sorted.mkString(", ")}"
            )
          '{ $builder.buildPartial }
        case None =>
          report.warning(
            s"Migration.build: compile-time handledFields extraction is unavailable for ${TypeRepr.of[B].show}; falling back to buildPartial"
          )
          '{ $builder.buildPartial }
      }
    } catch {
      case NonFatal(t) =>
        report.warning(s"Migration.build: validation fallback due to macro extraction error: ${t.getMessage}")
        '{ $builder.buildPartial }
    }
  }
}
