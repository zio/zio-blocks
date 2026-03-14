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

import scala.language.implicitConversions
import scala.reflect.macros.blackbox

/**
 * Phantom implicit class that marks a collection traversal inside a selector
 * lambda. Never called at runtime — `SelectorMacro` recognises the `.each` call
 * in the AST and emits a [[DynamicOptic.Element]] node.
 */
final class SelectorEachOps[A](val a: Iterable[A]) extends AnyVal {
  def each: A = throw new UnsupportedOperationException(
    "each is only valid inside a migration selector passed to SelectorMacro.extractPathImpl"
  )
}

/**
 * Phantom implicit class that marks a variant-case projection inside a selector
 * lambda. `B` must be a subtype of the receiver so that the selector type-checks
 * at the call site. Never called at runtime — `SelectorMacro` recognises the
 * `.when[B]` call in the AST and emits a [[DynamicOptic.Case]] node.
 */
final class SelectorWhenOps[A](val a: A) extends AnyVal {
  def when[B <: A]: B = throw new UnsupportedOperationException(
    "when is only valid inside a migration selector passed to SelectorMacro.extractPathImpl"
  )
}

object SelectorMacro {

  implicit def selectorEachOps[A](a: Iterable[A]): SelectorEachOps[A]   = new SelectorEachOps(a)
  implicit def selectorWhenOps[A](a: A): SelectorWhenOps[A]              = new SelectorWhenOps(a)

  def extractPathImpl[S, A](c: blackbox.Context)(selector: c.Expr[S => A]): c.Expr[DynamicOptic] = {
    import c.universe._

    // Internal path-segment ADT — richer than a plain String list so we can
    // distinguish field access, collection traversal, and case projection.
    sealed trait Segment
    case class FieldSeg(name: String)   extends Segment
    case object EachSeg                 extends Segment
    case class WhenSeg(typeName: String) extends Segment

    def parseTree(tree: Tree): List[Segment] = tree match {
      case Function(_, body) =>
        parseTree(body)

      // Collection traversal: _.items.each
      case Select(qualifier, TermName("each")) =>
        parseTree(qualifier) :+ EachSeg

      // Variant case projection: _.payment.when[CreditCard]
      case TypeApply(Select(qualifier, TermName("when")), List(typeTree)) =>
        parseTree(qualifier) :+ WhenSeg(typeTree.tpe.typeSymbol.name.decodedName.toString)

      // Standard field access
      case Select(qualifier, name) =>
        parseTree(qualifier) :+ FieldSeg(name.decodedName.toString)

      // Structural type field access: qualifier.selectDynamic("fieldName")
      case Apply(
            Select(qualifier, TermName("selectDynamic")),
            List(Literal(Constant(fieldName: String)))
          ) =>
        parseTree(qualifier) :+ FieldSeg(fieldName)

      case Ident(_) =>
        Nil

      case Typed(expr, _) =>
        parseTree(expr)

      case Block(_, expr) =>
        parseTree(expr)

      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Unsupported selector syntax: $tree. Must be simple field access, .each, or .when[T]."
        )
    }

    val segs = parseTree(selector.tree)

    if (segs.isEmpty) {
      c.abort(c.enclosingPosition, "Selector path cannot be empty")
    }

    def buildOptic(path: List[Segment]): Tree = path match {
      case FieldSeg(name) :: Nil =>
        q"zio.blocks.schema.migration.DynamicOptic.Field($name, None)"
      case FieldSeg(name) :: tail =>
        q"zio.blocks.schema.migration.DynamicOptic.Field($name, Some(${buildOptic(tail)}))"

      case EachSeg :: Nil =>
        q"zio.blocks.schema.migration.DynamicOptic.Element(None)"
      case EachSeg :: tail =>
        q"zio.blocks.schema.migration.DynamicOptic.Element(Some(${buildOptic(tail)}))"

      case WhenSeg(typeName) :: Nil =>
        q"zio.blocks.schema.migration.DynamicOptic.Case($typeName, None)"
      case WhenSeg(typeName) :: tail =>
        q"zio.blocks.schema.migration.DynamicOptic.Case($typeName, Some(${buildOptic(tail)}))"

      case Nil =>
        c.abort(c.enclosingPosition, "Unreachable")
    }

    c.Expr[DynamicOptic](buildOptic(segs))
  }
}
