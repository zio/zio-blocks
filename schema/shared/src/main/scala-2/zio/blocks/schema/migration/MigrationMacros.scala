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

import scala.reflect.NameTransformer
import scala.reflect.macros.whitebox

/**
 * Scala 2 whitebox macro that converts a selector lambda (e.g.
 * `_.address.street`) into a [[zio.blocks.schema.DynamicOptic]] at compile
 * time.
 *
 * This is the Scala 2 counterpart of the Scala 3 [[MigrationMacros]] object.
 * The same selector surface (`each`, `when[T]`, `at(i)`, `atKey(k)`, etc.) is
 * supported. The empty-tree convention from
 * [[zio.blocks.schema.CompanionOptics]] is reused: `q""` signals "root"
 * (`DynamicOptic.root`), any non-empty tree represents the accumulated
 * [[DynamicOptic]] expression up to that node.
 *
 * Implicit resolution for `atKey`/`atKeys` (`Schema[K]`) and `searchFor[T]`
 * (`TypeId[T]`) is left to the call site: the emitted tree contains the method
 * call and Scala's normal implicit search fills in the evidence.
 */
private[migration] object MigrationMacros {

  /**
   * Macro implementation. Called from [[MigrationBuilderCompanion]] via
   * `def selectorToDynamicOptic[S, A](selector: S => A): DynamicOptic = macro ...`
   */
  def selectorToDynamicOptic[S, A](c: whitebox.Context)(
    selector: c.Expr[S => A]
  ): c.Tree = {
    import c.universe._
    import zio.blocks.schema.CommonMacroOps

    def fail(msg: String): Nothing = CommonMacroOps.fail(c)(msg)

    // ── Lambda unwrap ────────────────────────────────────────────────────────

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => fail(s"Expected a lambda expression, got '$tree'")
    }

    // ── Core walker ─────────────────────────────────────────────────────────
    //
    // Returns `q""` (empty tree) to signal "root" (DynamicOptic.root).
    // Returns a non-empty tree representing the DynamicOptic expression
    // accumulated up to and including this node.

    def parentOrRoot(parent: c.Tree): c.Tree = {
      val p = toNode(parent)
      if (p.isEmpty) q"_root_.zio.blocks.schema.DynamicOptic.root"
      else p
    }

    def toNode(tree: c.Tree): c.Tree = tree match {

      // ── _.each ─────────────────────────────────────────────────────────────
      case q"$_[..$_]($parent).each" =>
        q"${parentOrRoot(parent)}.elements"

      // ── _.eachKey ──────────────────────────────────────────────────────────
      case q"$_[..$_]($parent).eachKey" =>
        q"${parentOrRoot(parent)}.mapKeys"

      // ── _.eachValue ────────────────────────────────────────────────────────
      case q"$_[..$_]($parent).eachValue" =>
        q"${parentOrRoot(parent)}.mapValues"

      // ── _.when[T] ──────────────────────────────────────────────────────────
      // Uses the simple type-symbol name, matching what Reflect stores in
      // DynamicValue.Variant case names (e.g. "Some", "None", "Left", "Right").
      case q"$_[..$_]($parent).when[$caseTree]" =>
        val caseName = caseTree.tpe.dealias.typeSymbol.name.toString
        q"${parentOrRoot(parent)}.caseOf($caseName)"

      // ── _.wrapped[T] ───────────────────────────────────────────────────────
      case q"$_[..$_]($parent).wrapped[$_]" =>
        q"${parentOrRoot(parent)}.wrapped"

      // ── _.at(i) ────────────────────────────────────────────────────────────
      case q"$_[..$_]($parent).at(..$args)" if args.size == 1 && args.head.tpe.widen.dealias <:< definitions.IntTpe =>
        q"${parentOrRoot(parent)}.at(${args.head})"

      // ── _.atIndices(i*) ────────────────────────────────────────────────────
      case q"$_[..$_]($parent).atIndices(..$args)"
          if args.nonEmpty && args.forall(_.tpe.widen.dealias <:< definitions.IntTpe) =>
        q"${parentOrRoot(parent)}.atIndices(..$args)"

      // ── _.atKey(k) ─────────────────────────────────────────────────────────
      // DynamicOptic.atKey[K](key: K)(implicit schema: Schema[K]) — the
      // implicit is resolved at the call site by Scala's normal implicit search.
      case q"$_[..$_]($parent).atKey(..$args)" if args.size == 1 =>
        q"${parentOrRoot(parent)}.atKey(${args.head})"

      // ── _.atKeys(k*) ───────────────────────────────────────────────────────
      case q"$_[..$_]($parent).atKeys(..$args)" if args.nonEmpty =>
        q"${parentOrRoot(parent)}.atKeys(..$args)"

      // ── _.searchFor[T] ─────────────────────────────────────────────────────
      // DynamicOptic.search[A](implicit typeId: TypeId[A]) — the implicit
      // TypeId[T] is resolved at the call site.
      case q"$_[..$_]($parent).searchFor[$searchTree]" =>
        val searchTpe = searchTree.tpe.dealias
        q"${parentOrRoot(parent)}.search[$searchTpe]"

      // ── _.fieldName (record field access by name) ──────────────────────────
      case q"$parent.$child" =>
        val fieldName = NameTransformer.decode(child.toString)
        q"${parentOrRoot(parent)}.field($fieldName)"

      // ── Root Ident — the lambda parameter itself ───────────────────────────
      case _: Ident =>
        q""

      // ── Unsupported ────────────────────────────────────────────────────────
      case _ =>
        fail(
          s"Unsupported selector element. Expected: .<field>, .when[T], .at(i), " +
            s".atIndices(i*), .atKey(k), .atKeys(k*), .each, .eachKey, .eachValue, " +
            s".wrapped[T], .searchFor[T]; got '$tree'"
        )
    }

    val result = toNode(toPathBody(selector.tree))
    // c.info(c.enclosingPosition, s"Generated DynamicOptic:\n${showCode(result)}", force = true)
    if (result.isEmpty) q"_root_.zio.blocks.schema.DynamicOptic.root"
    else result
  }
}
