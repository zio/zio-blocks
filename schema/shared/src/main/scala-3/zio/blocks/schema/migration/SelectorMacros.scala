package zio.blocks.schema.migration

import zio.blocks.schema._
import scala.quoted._

/**
 * Scala 3 macros for type-safe selector expressions.
 *
 * These macros convert lambda expressions like `_.fieldName.nested` into
 * [[DynamicOptic]] paths at compile time, ensuring type safety.
 */
object SelectorMacros {

  /**
   * Convert a selector lambda to a DynamicOptic.
   *
   * Usage:
   * {{{
   * import SelectorMacros.toOptic
   *
   * val path: DynamicOptic = toOptic[Person, String](_.name)
   * val nested: DynamicOptic = toOptic[Person, String](_.address.street)
   * }}}
   */
  inline def toOptic[A, B](inline selector: A => B): DynamicOptic =
    ${ toOpticImpl[A, B]('selector) }

  /**
   * Convert a selector lambda to a Selector.
   */
  inline def toSelector[A, B](inline selector: A => B): Selector[A, B] =
    ${ toSelectorImpl[A, B]('selector) }

  private def toOpticImpl[A: Type, B: Type](
    selector: Expr[A => B]
  )(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect._
    buildDynamicOptic(selector.asTerm)
  }

  private def toSelectorImpl[A: Type, B: Type](
    selector: Expr[A => B]
  )(using Quotes): Expr[Selector[A, B]] = {
    val opticExpr = toOpticImpl[A, B](selector)
    '{
      val optic = $opticExpr
      new Selector[A, B] {
        def toOptic: DynamicOptic = optic
      }
    }
  }

  private def hasName(using Quotes)(term: quotes.reflect.Term, name: String): Boolean = {
    import quotes.reflect._
    term match {
      case Ident(s)     => s == name
      case Select(_, s) => s == name
      case _            => false
    }
  }

  private def extractTagFromRefinement(using Quotes)(tpe: quotes.reflect.TypeRepr): Option[String] = {
    import quotes.reflect._

    def findTag(t: TypeRepr): Option[String] = t.dealias match {
      case Refinement(_, "Tag", TypeBounds(ConstantType(StringConstant(tagName)), ConstantType(StringConstant(_)))) =>
        Some(tagName)
      case Refinement(_, "Tag", ConstantType(StringConstant(tagName))) =>
        Some(tagName)
      case Refinement(inner, _, _) =>
        findTag(inner)
      case _ => None
    }

    findTag(tpe)
  }

  private def caseNameFromType(using Quotes)(tpe: quotes.reflect.TypeRepr): String = {
    val direct = extractTagFromRefinement(tpe)
    direct.getOrElse {
      val n = tpe.typeSymbol.name
      if (n.endsWith("$")) n.stripSuffix("$") else n
    }
  }

  private def buildDynamicOptic(using Quotes)(term: quotes.reflect.Term): Expr[DynamicOptic] = {
    import quotes.reflect._

    def recurse(t: Term): Expr[DynamicOptic] = t match {
      case Inlined(_, _, body) =>
        recurse(body)

      case Block(Nil, body) =>
        recurse(body)

      case Block(List(DefDef(_, _, _, Some(body))), _) =>
        recurse(body)

      case Lambda(_, body) =>
        recurse(body)

      case Typed(expr, _) =>
        recurse(expr)

      // parameter reference
      case Ident(_) =>
        '{ DynamicOptic.root }

      // Field selection: _.fieldName
      case Select(qualifier, name) if name != "apply" =>
        name match {
          case "get" =>
            // Optional access doesn't add to the DynamicOptic path.
            recurse(qualifier)
          case "head" =>
            '{ ${ recurse(qualifier) }.at(0) }
          case "keys" =>
            '{ ${ recurse(qualifier) }.mapKeys }
          case "values" =>
            '{ ${ recurse(qualifier) }.mapValues }
          case fieldName =>
            '{ ${ recurse(qualifier) }.field(${ Expr(fieldName) }) }
        }

      // Indexed access: _.seq(0)
      case Apply(Select(qualifier, "apply"), List(index)) if index.tpe.widen.dealias <:< TypeRepr.of[Int] =>
        '{ ${ recurse(qualifier) }.at(${ index.asExprOf[Int] }) }

      // .each
      case Apply(TypeApply(eachTerm, _), List(parent)) if hasName(eachTerm, "each") =>
        '{ ${ recurse(parent) }.elements }

      // .eachKey
      case Apply(TypeApply(keyTerm, _), List(parent)) if hasName(keyTerm, "eachKey") =>
        '{ ${ recurse(parent) }.mapKeys }

      // .eachValue
      case Apply(TypeApply(valueTerm, _), List(parent)) if hasName(valueTerm, "eachValue") =>
        '{ ${ recurse(parent) }.mapValues }

      // .when[Case]
      case TypeApply(Apply(TypeApply(caseTerm, _), List(parent)), List(typeTree)) if hasName(caseTerm, "when") =>
        val caseName = caseNameFromType(typeTree.tpe)
        '{ ${ recurse(parent) }.caseOf(${ Expr(caseName) }) }

      // .wrapped[Wrapped]
      case TypeApply(Apply(TypeApply(wrapperTerm, _), List(parent)), List(_)) if hasName(wrapperTerm, "wrapped") =>
        '{ ${ recurse(parent) }.wrapped }

      // .at(index)
      case Apply(Apply(TypeApply(atTerm, _), List(parent)), List(index)) if hasName(atTerm, "at") =>
        '{ ${ recurse(parent) }.at(${ index.asExprOf[Int] }) }

      // .atIndices(i1, i2, ...)
      case Apply(Apply(TypeApply(atIndicesTerm, _), List(parent)), List(Typed(Repeated(indices, _), _)))
          if hasName(atIndicesTerm, "atIndices") =>
        val indicesExprs = indices.map(_.asExprOf[Int])
        '{ ${ recurse(parent) }.atIndices(${ Varargs(indicesExprs) }*) }

      // .atKey(key)
      case Apply(Apply(TypeApply(atKeyTerm, _), List(parent)), List(key)) if hasName(atKeyTerm, "atKey") =>
        key.tpe.widen.dealias.asType match {
          case '[k] =>
            Expr.summon[Schema[k]] match {
              case Some(schemaExpr) =>
                '{
                  given Schema[k] = $schemaExpr
                  ${ recurse(parent) }.atKey(${ key.asExprOf[k] })
                }
              case None =>
                report.errorAndAbort(s"Missing Schema for key type: ${key.tpe.widen.show}")
            }
        }

      // .atKeys(k1, k2, ...)
      case Apply(Apply(TypeApply(atKeysTerm, _), List(parent)), List(Typed(Repeated(keys, _), _)))
          if hasName(atKeysTerm, "atKeys") =>
        keys match {
          case Nil =>
            report.errorAndAbort("`.atKeys` requires at least one key.")
          case head :: _ =>
            head.tpe.widen.dealias.asType match {
              case '[k] =>
                val keyExprs = keys.map(_.asExprOf[k])
                Expr.summon[Schema[k]] match {
                  case Some(schemaExpr) =>
                    '{
                      given Schema[k] = $schemaExpr
                      ${ recurse(parent) }.atKeys(${ Varargs(keyExprs) }*)
                    }
                  case None =>
                    report.errorAndAbort(s"Missing Schema for key type: ${head.tpe.widen.show}")
                }
            }
        }

      case other =>
        report.errorAndAbort(
          s"Unsupported selector expression: ${other.show}. " +
            "Expected path elements: .<field>, .when[<T>], .at(<index>), .atIndices(<indices>), .atKey(<key>), " +
            ".atKeys(<keys>), .each, .eachKey, .eachValue, .wrapped[<T>], or optional `.get`."
        )
    }

    recurse(term)
  }
}
