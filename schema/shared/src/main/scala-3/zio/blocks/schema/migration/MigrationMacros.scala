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
import scala.quoted._
import zio.blocks.schema.{DynamicOptic, Schema}
import zio.blocks.typeid.TypeId

/**
 * Scala 3 macro that converts a selector lambda (e.g. `_.address.street`) into
 * a [[DynamicOptic]] at compile time.
 *
 * This is the counterpart of `CompanionOptics` for the migration DSL: instead
 * of producing a typed `Optic[S, A]`, it walks the same selector AST and emits
 * a chain of [[DynamicOptic]] node appends (`field`, `caseOf`, `at`,
 * `elements`, etc.).
 *
 * ==Supported selector operations==
 *   - `_.field` / `_._N` → [[DynamicOptic.Node.Field]]
 *   - `_.when[T]` → [[DynamicOptic.Node.Case]] (uses the simple type-symbol
 *     name)
 *   - `_.each` → [[DynamicOptic.Node.Elements]]
 *   - `_.eachKey` → [[DynamicOptic.Node.MapKeys]]
 *   - `_.eachValue` → [[DynamicOptic.Node.MapValues]]
 *   - `_.wrapped[T]` → [[DynamicOptic.Node.Wrapped]]
 *   - `_.at(i)` → [[DynamicOptic.Node.AtIndex]]
 *   - `_.atIndices(i*)` → [[DynamicOptic.Node.AtIndices]]
 *   - `_.atKey(k)` → [[DynamicOptic.Node.AtMapKey]] (requires `Schema[K]` in
 *     scope)
 *   - `_.atKeys(k*)` → [[DynamicOptic.Node.AtMapKeys]] (requires `Schema[K]` in
 *     scope)
 *   - `_.searchFor[T]` → [[DynamicOptic.Node.TypeSearch]] (requires `TypeId[T]`
 *     in scope)
 */
private[migration] object MigrationMacros {
  import zio.blocks.schema.CommonMacroOps._

  /**
   * Entry point invoked from the macro DSL extension methods.
   *
   * Expands `selector` into a compile-time [[DynamicOptic]] expression. Fails
   * with a compile error if the lambda body contains an unsupported operation.
   */
  def selectorToDynamicOptic[S: Type, A: Type](
    selector: Expr[S => A]
  )(using q: Quotes): Expr[DynamicOptic] = {
    import q.reflect._

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

    @tailrec
    def unwrapStructuralSelect(term: Term): Option[(Term, String)] = term match {
      case Inlined(_, _, inner) =>
        unwrapStructuralSelect(inner)
      case TypeApply(Select(inner, "$asInstanceOf$"), _) =>
        unwrapStructuralSelect(inner)
      case Apply(Select(receiver, "selectDynamic"), List(Literal(StringConstant(fieldName)))) =>
        Some((unwrapReflectiveSelectable(receiver), fieldName))
      case _ =>
        None
    }

    @tailrec
    def unwrapReflectiveSelectable(term: Term): Term = term match {
      case Inlined(_, _, inner) =>
        unwrapReflectiveSelectable(inner)
      case Apply(Select(_, "reflectiveSelectable"), List(parent)) =>
        unwrapReflectiveSelectable(parent)
      case _ =>
        term
    }

    /**
     * Recursively walks the selector term, returning:
     *   - `None` for the root `Ident` (represents `DynamicOptic.root`)
     *   - `Some(expr)` for every path element, where `expr` evaluates to the
     *     [[DynamicOptic]] up to and including this node
     */
    def toNode(term: Term): Option[Expr[DynamicOptic]] = term match {

      // ── _.each ────────────────────────────────────────────────────────────
      case Apply(TypeApply(elementTerm, _), List(parent)) if hasName(elementTerm, "each") =>
        val parentExpr = toNode(parent).getOrElse('{ DynamicOptic.root })
        Some('{ $parentExpr.elements })

      // ── _.eachKey ─────────────────────────────────────────────────────────
      case Apply(TypeApply(keyTerm, _), List(parent)) if hasName(keyTerm, "eachKey") =>
        val parentExpr = toNode(parent).getOrElse('{ DynamicOptic.root })
        Some('{ $parentExpr.mapKeys })

      // ── _.eachValue ───────────────────────────────────────────────────────
      case Apply(TypeApply(valueTerm, _), List(parent)) if hasName(valueTerm, "eachValue") =>
        val parentExpr = toNode(parent).getOrElse('{ DynamicOptic.root })
        Some('{ $parentExpr.mapValues })

      // ── _.when[T] ─────────────────────────────────────────────────────────
      case TypeApply(Apply(TypeApply(caseTerm, _), List(parent)), List(typeTree)) if hasName(caseTerm, "when") =>
        val caseName   = typeTree.tpe.dealias.typeSymbol.name
        val parentExpr = toNode(parent).getOrElse('{ DynamicOptic.root })
        Some('{ $parentExpr.caseOf(${ Expr(caseName) }) })

      // ── _.wrapped[T] ──────────────────────────────────────────────────────
      case TypeApply(Apply(TypeApply(wrapperTerm, _), List(parent)), List(_)) if hasName(wrapperTerm, "wrapped") =>
        val parentExpr = toNode(parent).getOrElse('{ DynamicOptic.root })
        Some('{ $parentExpr.wrapped })

      // ── _.at(i) ───────────────────────────────────────────────────────────
      case Apply(Apply(TypeApply(indexTerm, _), List(parent)), List(index))
          if hasName(indexTerm, "at") && index.tpe.widen.dealias <:< TypeRepr.of[Int] =>
        val parentExpr = toNode(parent).getOrElse('{ DynamicOptic.root })
        val indexExpr  = index.asExpr.asInstanceOf[Expr[Int]]
        Some('{ $parentExpr.at($indexExpr) })

      // ── _.atIndices(i*) ───────────────────────────────────────────────────
      case Apply(Apply(TypeApply(indicesTerm, _), List(parent)), List(Typed(Repeated(indices, _), _)))
          if hasName(indicesTerm, "atIndices") &&
            indices.forall(_.tpe.widen.dealias <:< TypeRepr.of[Int]) =>
        val parentExpr  = toNode(parent).getOrElse('{ DynamicOptic.root })
        val indicesExpr = Expr.ofSeq(indices.map(_.asExpr.asInstanceOf[Expr[Int]]))
        Some('{ $parentExpr.atIndices($indicesExpr: _*) })

      // ── _.atKey(k) ────────────────────────────────────────────────────────
      case Apply(Apply(TypeApply(keyTerm, _), List(parent)), List(key)) if hasName(keyTerm, "atKey") =>
        val keyTpe     = key.tpe.widen.dealias
        val parentExpr = toNode(parent).getOrElse('{ DynamicOptic.root })
        keyTpe.asType match {
          case '[k] =>
            Expr.summon[Schema[k]] match {
              case Some(schemaExpr) =>
                val keyExpr = key.asExpr.asInstanceOf[Expr[k]]
                Some('{ $parentExpr.atKey[k]($keyExpr)(using $schemaExpr) })
              case None =>
                fail(s"No implicit Schema[${keyTpe.show}] found for atKey")
            }
        }

      // ── _.atKeys(k*) ──────────────────────────────────────────────────────
      case Apply(Apply(TypeApply(keysTerm, _), List(parent)), List(Typed(Repeated(keys, _), _)))
          if hasName(keysTerm, "atKeys") =>
        // Determine key type from the first key element's type
        val keyTpe = keys.headOption
          .map(_.tpe.widen.dealias)
          .getOrElse(fail("atKeys requires at least one key argument"))
        val parentExpr = toNode(parent).getOrElse('{ DynamicOptic.root })
        keyTpe.asType match {
          case '[k] =>
            Expr.summon[Schema[k]] match {
              case Some(schemaExpr) =>
                val keysExpr = Expr.ofSeq(keys.map(_.asExpr.asInstanceOf[Expr[k]]))
                Some('{ $parentExpr.atKeys[k]($keysExpr: _*)(using $schemaExpr) })
              case None =>
                fail(s"No implicit Schema[${keyTpe.show}] found for atKeys")
            }
        }

      // ── _.searchFor[T] ────────────────────────────────────────────────────
      case TypeApply(Apply(TypeApply(searchTerm, _), List(parent)), List(typeTree))
          if hasName(searchTerm, "searchFor") =>
        val searchTpe  = typeTree.tpe.dealias
        val parentExpr = toNode(parent).getOrElse('{ DynamicOptic.root })
        searchTpe.asType match {
          case '[s] =>
            Expr.summon[TypeId[s]] match {
              case Some(typeIdExpr) =>
                Some('{ $parentExpr.search[s](using $typeIdExpr) })
              case None =>
                fail(s"No implicit TypeId[${searchTpe.show}] found for searchFor")
            }
        }

      // ── _.fieldName (record field access) ────────────────────────────────
      case Select(parent, fieldName) =>
        val parentExpr = toNode(parent).getOrElse('{ DynamicOptic.root })
        Some('{ $parentExpr.field(${ Expr(fieldName) }) })

      // ── structural _.fieldName via reflectiveSelectable/selectDynamic ────
      case term if unwrapStructuralSelect(term).isDefined =>
        val (parent, fieldName) = unwrapStructuralSelect(term).get
        val parentExpr          = toNode(parent).getOrElse('{ DynamicOptic.root })
        Some('{ $parentExpr.field(${ Expr(fieldName) }) })

      // ── Root Ident — the lambda parameter itself ──────────────────────────
      case _: Ident =>
        None

      // ── structural root via reflectiveSelectable(_) ──────────────────────
      case Apply(Select(_, "reflectiveSelectable"), List(parent)) =>
        toNode(parent)

      // ── structural root via reflectiveSelectable(_) ──────────────────────
      case term if !(unwrapReflectiveSelectable(term) eq term) =>
        toNode(unwrapReflectiveSelectable(term))

      // ── Tuple index: _._1, _._2, … or apply(i) ───────────────────────────
      case _ =>
        val (parent, idx) = term match {
          case Apply(Apply(_, List(p)), List(Literal(IntConstant(i))))                => (p, i)
          case Apply(TypeApply(Select(p, "apply"), _), List(Literal(IntConstant(i)))) => (p, i)
          case _                                                                      =>
            fail(
              s"Unsupported selector element. Expected: .<field>, .when[T], .at(i), " +
                s".atIndices(i*), .atKey(k), .atKeys(k*), .each, .eachKey, .eachValue, " +
                s".wrapped[T], .searchFor[T]; got '${term.show}'"
            )
        }
        var parentTpe = parent.tpe.widen.dealias
        if (isGenericTuple(parentTpe)) {
          val typeArgs = genericTupleTypeArgs(parentTpe)
          parentTpe = normalizeGenericTuple(typeArgs)
        }
        // Tuple element fields are named _1, _2, etc. at the DynamicValue level.
        val fieldName  = s"_${idx + 1}"
        val parentExpr = toNode(parent).getOrElse('{ DynamicOptic.root })
        Some('{ $parentExpr.field(${ Expr(fieldName) }) })
    }

    val body = toPathBody(selector.asTerm)
    toNode(body).getOrElse('{ DynamicOptic.root })
  }
}
