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

/**
 * Macro that parses a selector function (e.g.
 * `(p: Person) => p.address.street`) into a pure-data [[DynamicOptic]] path at
 * compile time. No runtime reflection. Part of the algebraic migration system
 * (MigrationBuilder → DynamicMigration).
 *
 * Supported projection syntax in selectors:
 *   - Field access: `_.foo.bar`
 *   - Collection traversal: `_.items.each`
 *   - Variant case selection: `_.payment.when[CreditCard]`
 */
object SelectorMacro {

  /**
   * Phantom extension method that marks a collection traversal in a selector
   * lambda. Never called at runtime — the `SelectorMacro` recognises the
   * `.each` call in the AST and emits a [[DynamicOptic.Element]] node.
   */
  extension [A](a: Iterable[A]) {
    inline def each: A = throw new UnsupportedOperationException(
      "each is only valid inside a migration selector passed to SelectorMacro.extractPath"
    )
  }

  /**
   * Phantom extension method that marks a variant-case projection in a selector
   * lambda. The type parameter `B` must be a subtype of the receiver so that
   * case-class selectors type-check at the call site. Never called at runtime —
   * the `SelectorMacro` recognises the `.when[B]` call in the AST and emits a
   * [[DynamicOptic.Case]] node using the simple name of `B`.
   */
  extension [A](a: A) {
    inline def when[B <: A]: B = throw new UnsupportedOperationException(
      "when is only valid inside a migration selector passed to SelectorMacro.extractPath"
    )
  }

  inline def extractPath[S, A](inline selector: S => A): DynamicOptic =
    ${ extractPathImpl('selector) }

  def extractPathImpl[S: Type, A: Type](selector: Expr[S => A])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    // Internal path-segment ADT — richer than a plain String list so we can
    // distinguish field access, collection traversal, and case projection.
    sealed trait Segment
    case class FieldSeg(name: String)    extends Segment
    case object EachSeg                  extends Segment
    case class WhenSeg(typeName: String) extends Segment

    def lambdaBody(term: Term): Term = term match {
      case Inlined(_, _, body) =>
        lambdaBody(body)
      case Block(List(DefDef(_, _, _, Some(body))), _) =>
        body
      case _ =>
        report.errorAndAbort(
          s"Expected a lambda expression, got: ${term.show}. Use a simple selector (e.g. (p: Person) => p.address.street)."
        )
    }

    def parseTerm(term: Term): List[Segment] = term match {
      // Collection traversal: _.items.each
      case Select(qualifier, "each") =>
        parseTerm(qualifier) :+ EachSeg

      // Variant case projection: _.payment.when[CreditCard]
      case TypeApply(Select(qualifier, "when"), List(typeTree)) =>
        parseTerm(qualifier) :+ WhenSeg(typeTree.tpe.typeSymbol.name)

      // Standard case class / record field access
      case Select(qualifier, name) =>
        parseTerm(qualifier) :+ FieldSeg(name)

      // Structural type field access (Selectable): qualifier.selectDynamic("fieldName")
      case Apply(Select(qualifier, "selectDynamic"), List(Literal(StringConstant(fieldName)))) =>
        parseTerm(qualifier) :+ FieldSeg(fieldName)

      // Lambda parameter base
      case Ident(_) =>
        Nil

      // Inlined wrapper (often surrounds macros/lambdas)
      case Inlined(_, _, body) =>
        parseTerm(body)

      // Lambda definition
      case Block(List(DefDef(_, _, _, Some(body))), _) =>
        parseTerm(body)

      // Type ascription (e.g. (_.age: Int))
      case Typed(expr, _) =>
        parseTerm(expr)

      case _ =>
        report.errorAndAbort(
          s"Unsupported selector syntax: ${term.show}. Must be simple field access, .each, or .when[T]."
        )
    }

    val body = lambdaBody(selector.asTerm)
    val segs = parseTerm(body)

    if (segs.isEmpty) {
      report.errorAndAbort("Selector path cannot be empty.")
    }

    def buildOptic(path: List[Segment]): Expr[DynamicOptic] = path match {
      case FieldSeg(name) :: Nil =>
        '{ DynamicOptic.Field(${ Expr(name) }, None) }
      case FieldSeg(name) :: tail =>
        '{ DynamicOptic.Field(${ Expr(name) }, Some(${ buildOptic(tail) })) }

      case EachSeg :: Nil =>
        '{ DynamicOptic.Element(None) }
      case EachSeg :: tail =>
        '{ DynamicOptic.Element(Some(${ buildOptic(tail) })) }

      case WhenSeg(typeName) :: Nil =>
        '{ DynamicOptic.Case(${ Expr(typeName) }, None) }
      case WhenSeg(typeName) :: tail =>
        '{ DynamicOptic.Case(${ Expr(typeName) }, Some(${ buildOptic(tail) })) }

      case Nil =>
        report.errorAndAbort("Unreachable")
    }

    buildOptic(segs)
  }
}
