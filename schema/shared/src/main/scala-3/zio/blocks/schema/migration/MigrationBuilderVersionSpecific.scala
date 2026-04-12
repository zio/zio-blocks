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

import scala.annotation.tailrec
import scala.quoted.*
import zio.blocks.schema.{Schema, SchemaExpr}

/**
 * Scala 3 version-specific surface for [[MigrationBuilder]].
 *
 * This provides:
 *   - an `apply` constructor that seeds the builder type-state with
 *     `EmptyTuple`
 *   - selector-based methods that accumulate type-state (for `.build`
 *     validation)
 */
trait MigrationBuilderVersionSpecific {
  def apply[A, B](sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] {
    type Actions = EmptyTuple
  } =
    new MigrationBuilder[A, B](sourceSchema, targetSchema, Vector.empty) {
      type Actions = EmptyTuple
    }

  extension [A, B, Acts <: Tuple](builder: MigrationBuilder[A, B] { type Actions = Acts }) {
    transparent inline def addField(
      inline at: B => Any,
      default: SchemaExpr[Any, _]
    )(using sa: Schema[A], sb: Schema[B]): MigrationBuilder[A, B] =
      ${ MigrationBuilderVersionSpecificImpl.addFieldImpl[A, B, Acts]('builder, 'at, 'default, 'sa, 'sb) }

    transparent inline def dropField(
      inline at: A => Any,
      defaultForReverse: SchemaExpr[Any, _]
    )(using sa: Schema[A], sb: Schema[B]): MigrationBuilder[A, B] =
      ${ MigrationBuilderVersionSpecificImpl.dropFieldImpl[A, B, Acts]('builder, 'at, 'defaultForReverse, 'sa, 'sb) }

    transparent inline def renameField(
      inline from: A => Any,
      inline to: B => Any
    )(using sa: Schema[A], sb: Schema[B]): MigrationBuilder[A, B] =
      ${ MigrationBuilderVersionSpecificImpl.renameFieldImpl[A, B, Acts]('builder, 'from, 'to, 'sa, 'sb) }

    transparent inline def transformField(
      inline at: A => Any,
      transform: SchemaExpr[Any, _]
    )(using sa: Schema[A], sb: Schema[B]): MigrationBuilder[A, B] =
      ${ MigrationBuilderVersionSpecificImpl.transformFieldImpl[A, B, Acts]('builder, 'at, 'transform, 'sa, 'sb) }
  }
}

private object MigrationBuilderVersionSpecificImpl {
  def selectorLeafFieldName[S: Type, A: Type](selector: Expr[S => A])(using Quotes): String = {
    import quotes.reflect.*

    def fail(msg: String): Nothing =
      report.errorAndAbort(msg, Position.ofMacroExpansion)

    @tailrec
    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => fail(s"Expected a lambda expression, got '${term.show}'")
    }

    def hasName(term: Term, name: String): Boolean = term match {
      case Ident(s)     => name == s
      case Select(_, s) => name == s
      case _            => false
    }

    def leaf(term: Term): Option[String] = term match {
      case Inlined(_, _, inner) =>
        leaf(inner)

      case Ident(_) =>
        None

      // _.each
      case Apply(TypeApply(eachTerm, _), List(_)) if hasName(eachTerm, "each") =>
        None

      // _.when[Case]
      case TypeApply(Apply(TypeApply(whenTerm, _), List(_)), _) if hasName(whenTerm, "when") =>
        None

      // structural selectDynamic("fieldName")
      case Apply(Select(_, "selectDynamic"), List(Literal(StringConstant(fieldName)))) =>
        Some(fieldName)

      // _.fieldName
      case Select(_, fieldName) =>
        Some(fieldName)

      // Ignore wrappers introduced by structural selection
      case Apply(Select(_, "reflectiveSelectable"), List(parent)) =>
        leaf(parent)

      // Ignore casts introduced by the compiler
      case TypeApply(Select(parent, "$asInstanceOf$"), _) =>
        leaf(parent)

      case _ =>
        None
    }

    val body = toPathBody(selector.asTerm)
    leaf(body).getOrElse {
      fail(s"Selector must end in a field selection (e.g. _.foo.bar), got: '${body.show}'")
    }
  }

  private def stringLiteralTerm(using Quotes)(s: String): quotes.reflect.Term = {
    import quotes.reflect.*
    Literal(StringConstant(s))
  }

  def addFieldImpl[A: Type, B: Type, Acts: Type](
    builder: Expr[MigrationBuilder[A, B] { type Actions = Acts }],
    at: Expr[B => Any],
    default: Expr[SchemaExpr[Any, _]],
    sa: Expr[Schema[A]],
    sb: Expr[Schema[B]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val _    = (sa, sb)
    val path = MigrationMacros.selectorToDynamicOptic[B, Any](at)
    val name = selectorLeafFieldName[B, Any](at)

    val ops    = Ref(Symbol.requiredModule("zio.blocks.schema.migration.MigrationBuilderStateOps"))
    val addSym =
      Symbol.requiredModule("zio.blocks.schema.migration.MigrationBuilderStateOps").methodMember("addField").head
    val fType   = ConstantType(StringConstant(name))
    val applied = Apply(
      TypeApply(Select(ops, addSym), List(TypeTree.of[A], TypeTree.of[B], TypeTree.of[Acts], Inferred(fType))),
      List(builder.asTerm, path.asTerm, stringLiteralTerm(name), default.asTerm)
    )

    applied.asExpr.asInstanceOf[Expr[MigrationBuilder[A, B]]]
  }

  def dropFieldImpl[A: Type, B: Type, Acts: Type](
    builder: Expr[MigrationBuilder[A, B] { type Actions = Acts }],
    at: Expr[A => Any],
    defaultForReverse: Expr[SchemaExpr[Any, _]],
    sa: Expr[Schema[A]],
    sb: Expr[Schema[B]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val _    = (sa, sb)
    val path = MigrationMacros.selectorToDynamicOptic[A, Any](at)
    val name = selectorLeafFieldName[A, Any](at)

    val ops     = Ref(Symbol.requiredModule("zio.blocks.schema.migration.MigrationBuilderStateOps"))
    val dropSym =
      Symbol.requiredModule("zio.blocks.schema.migration.MigrationBuilderStateOps").methodMember("dropField").head
    val fType   = ConstantType(StringConstant(name))
    val applied = Apply(
      TypeApply(Select(ops, dropSym), List(TypeTree.of[A], TypeTree.of[B], TypeTree.of[Acts], Inferred(fType))),
      List(builder.asTerm, path.asTerm, stringLiteralTerm(name), defaultForReverse.asTerm)
    )

    applied.asExpr.asInstanceOf[Expr[MigrationBuilder[A, B]]]
  }

  def renameFieldImpl[A: Type, B: Type, Acts: Type](
    builder: Expr[MigrationBuilder[A, B] { type Actions = Acts }],
    from: Expr[A => Any],
    to: Expr[B => Any],
    sa: Expr[Schema[A]],
    sb: Expr[Schema[B]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val _    = (sa, sb)
    val path = MigrationMacros.selectorToDynamicOptic[A, Any](from)
    val old  = selectorLeafFieldName[A, Any](from)
    val neu  = selectorLeafFieldName[B, Any](to)

    val ops    = Ref(Symbol.requiredModule("zio.blocks.schema.migration.MigrationBuilderStateOps"))
    val renSym =
      Symbol.requiredModule("zio.blocks.schema.migration.MigrationBuilderStateOps").methodMember("renameField").head
    val oldType = ConstantType(StringConstant(old))
    val newType = ConstantType(StringConstant(neu))
    val applied = Apply(
      TypeApply(
        Select(ops, renSym),
        List(TypeTree.of[A], TypeTree.of[B], TypeTree.of[Acts], Inferred(oldType), Inferred(newType))
      ),
      List(builder.asTerm, path.asTerm, stringLiteralTerm(old), stringLiteralTerm(neu))
    )

    applied.asExpr.asInstanceOf[Expr[MigrationBuilder[A, B]]]
  }

  def transformFieldImpl[A: Type, B: Type, Acts: Type](
    builder: Expr[MigrationBuilder[A, B] { type Actions = Acts }],
    at: Expr[A => Any],
    transform: Expr[SchemaExpr[Any, _]],
    sa: Expr[Schema[A]],
    sb: Expr[Schema[B]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val _    = (sa, sb)
    val path = MigrationMacros.selectorToDynamicOptic[A, Any](at)

    // TransformValue does not affect structural field coverage, so we do not
    // add it to the type-state.
    val ops   = Ref(Symbol.requiredModule("zio.blocks.schema.migration.MigrationBuilderStateOps"))
    val trSym =
      Symbol.requiredModule("zio.blocks.schema.migration.MigrationBuilderStateOps").methodMember("transformField").head
    val applied = Apply(
      TypeApply(Select(ops, trSym), List(TypeTree.of[A], TypeTree.of[B], TypeTree.of[Acts])),
      List(builder.asTerm, path.asTerm, transform.asTerm)
    )

    applied.asExpr.asInstanceOf[Expr[MigrationBuilder[A, B]]]
  }
}
