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
import zio.blocks.schema.Schema

/**
 * Scala 3 macro implementation for [[MigrationBuilder.build]].
 *
 * This macro performs compile-time validation by reading the migration state
 * from the builder's type member `Actions` (a tuple of action descriptors).
 *
 * The key property is that the macro does **not** inspect the builder call
 * chain (which is fragile under `val` extraction). Instead, it uses only type
 * information, which survives `val` extraction unless the user widens the
 * builder type (in which case the macro aborts with a clear error).
 */
private[migration] object MigrationBuilderMacros {
  private sealed trait Action
  private final case class Rename(oldName: String, newName: String) extends Action
  private final case class Add(fieldName: String)                   extends Action
  private final case class Drop(fieldName: String)                  extends Action

  def validateAndBuild[A: Type, B: Type, Actions: Type](
    builder: Expr[MigrationBuilder[A, B]]
  )(using q: Quotes): Expr[Migration[A, B]] = {
    import q.reflect.*

    val emptyTupleTpe = TypeRepr.of[EmptyTuple].dealias

    def abort(msg: String): Nothing =
      report.errorAndAbort(msg, Position.ofMacroExpansion)

    def structuralMembers(tpe: TypeRepr): List[(String, TypeRepr)] = {
      def loop(t: TypeRepr): List[(String, TypeRepr)] =
        t.dealias match {
          case Refinement(parent, name, info) =>
            val memberTpeOpt: Option[TypeRepr] = info match {
              case MethodType(_, _, returnType) => Some(returnType)
              case ByNameType(underlying)       => Some(underlying)
              case _: TypeBounds                => None // type member (e.g. Tag) - not a record field
              case other                        => Some(other)
            }
            memberTpeOpt match {
              case Some(mt) => (name, mt) :: loop(parent)
              case None     => loop(parent)
            }
          case _ => Nil
        }

      loop(tpe).reverse
    }

    def recordFieldNames(tpe: TypeRepr): List[String] =
      tpe.dealias match {
        case Refinement(_, _, _) =>
          structuralMembers(tpe).map(_._1)

        case other =>
          val sym        = other.typeSymbol
          val caseFields = sym.caseFields.map(_.name)
          if (caseFields.nonEmpty) caseFields.toList
          else {
            val ctor   = sym.primaryConstructor
            val params = ctor.paramSymss.flatten.filterNot(_.isTypeParam).map(_.name)
            if (params.nonEmpty) params.toList
            else abort(s"Expected a record type (case class or structural record), got: ${other.show}")
          }
      }

    def stringLiteral(tpe: TypeRepr): Option[String] =
      tpe.dealias match {
        case ConstantType(StringConstant(s)) => Some(s)
        case ConstantType(c)                 =>
          c.value match {
            case s: String => Some(s)
            case _         => None
          }
        case _ => None
      }

    def expectLiteral(tpe: TypeRepr, what: String): String =
      stringLiteral(tpe).getOrElse(abort(s"$what must be a string literal type, got: ${tpe.show}"))

    def extractActionHead(head: TypeRepr): Action =
      head.dealias match {
        case AppliedType(t2, List(tagTpe, fTpe)) if t2.typeSymbol.fullName == "scala.Tuple2" =>
          expectLiteral(tagTpe, "Action tag") match {
            case "add"  => Add(expectLiteral(fTpe, "add fieldName"))
            case "drop" => Drop(expectLiteral(fTpe, "drop fieldName"))
            case other  => abort(s"Unsupported Tuple2 action tag '$other' (expected 'add' or 'drop')")
          }

        case AppliedType(t3, List(tagTpe, oldTpe, newTpe)) if t3.typeSymbol.fullName == "scala.Tuple3" =>
          expectLiteral(tagTpe, "Action tag") match {
            case "rename" =>
              Rename(
                expectLiteral(oldTpe, "rename oldName"),
                expectLiteral(newTpe, "rename newName")
              )
            case other => abort(s"Unsupported Tuple3 action tag '$other' (expected 'rename')")
          }

        case other =>
          abort(s"Unsupported action head type: ${other.show}")
      }

    def extractActions(actionsTpe: TypeRepr): List[Action] =
      actionsTpe.dealias match {
        case t if t =:= emptyTupleTpe =>
          Nil
        case AppliedType(cons, List(head, tail)) if cons.typeSymbol.fullName == "scala.*:" =>
          extractActionHead(head) :: extractActions(tail)
        case other =>
          abort(
            s"""MigrationBuilder type was widened / action state erased.
               |Expected a concrete Actions tuple (e.g. ... *: EmptyTuple) but got: ${other.show}
               |Hint: avoid type ascriptions like `val b: MigrationBuilder[A, B] = ...`; use `val b = ...`.
               |""".stripMargin
          )
      }

    val from0    = recordFieldNames(TypeRepr.of[A])
    val to0      = recordFieldNames(TypeRepr.of[B])
    val actions0 = extractActions(TypeRepr.of[Actions]).reverse // apply in call order

    def insertAccordingToTo(current: List[String], field: String): List[String] = {
      val idxInTo = to0.indexOf(field)
      if (idxInTo < 0) current // nested adds are ignored by top-level record validation
      else {
        val insertPos = current.indexWhere { name =>
          val nameIdx = to0.indexOf(name)
          nameIdx >= 0 && nameIdx > idxInTo
        }
        if (insertPos < 0) current :+ field
        else current.take(insertPos) ::: (field :: current.drop(insertPos))
      }
    }

    val migrated =
      actions0.foldLeft(from0) { (fields, action) =>
        action match {
          case Rename(oldN, newN) =>
            if (fields.contains(oldN)) fields.map(n => if (n == oldN) newN else n) else fields

          case Drop(f) =>
            if (fields.contains(f)) fields.filterNot(_ == f) else fields

          case Add(f) =>
            if (fields.contains(f)) fields
            else insertAccordingToTo(fields, f)
        }
      }

    val missing = to0.filterNot(migrated.contains)
    val extra   = migrated.filterNot(to0.contains)

    if (missing.nonEmpty || extra.nonEmpty || migrated != to0) {
      abort(
        s"""MigrationBuilder build validation failed.
           |From fields: ${from0.mkString("(", ", ", ")")}
           |To fields:   ${to0.mkString("(", ", ", ")")}
           |Actions:     ${actions0.map {
            case Rename(o, n) => s"rename($o->$n)"
            case Add(f)       => s"add($f)"
            case Drop(f)      => s"drop($f)"
          }.mkString("[", ", ", "]")}
           |After:       ${migrated.mkString("(", ", ", ")")}
           |Missing:     ${missing.mkString("[", ", ", "]")}
           |Extra:       ${extra.mkString("[", ", ", "]")}
           |""".stripMargin
      )
    }

    // Build the runtime Migration using the builder's stored schemas and actions.
    val builderTerm      = builder.asTerm
    val sourceSchemaExpr = Select.unique(builderTerm, "sourceSchema").asExprOf[Schema[A]]
    val targetSchemaExpr = Select.unique(builderTerm, "targetSchema").asExprOf[Schema[B]]
    val actionsExpr      = Select.unique(builderTerm, "actions").asExprOf[Vector[MigrationAction]]

    '{ Migration(DynamicMigration($actionsExpr), $sourceSchemaExpr, $targetSchemaExpr) }
  }
}
