package zio.blocks.schema.migration

import scala.annotation.tailrec
import scala.quoted._
import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * Scala 3 macros for converting selector expressions into DynamicOptic paths.
 *
 * These macros parse selector expressions like:
 *   - `_.field` → `DynamicOptic.root.field("field")`
 *   - `_.a.b.c` → `DynamicOptic.root.field("a").field("b").field("c")`
 *   - `_.items.each` → `DynamicOptic.root.field("items").elements`
 *   - `_.country.when[UK]` → `DynamicOptic.root.field("country").caseOf("UK")`
 */
object SelectorMacros {

  inline def toPath[S, A](inline selector: S => A): DynamicOptic =
    ${ toPathImpl[S, A]('selector) }

  inline def extractFieldName[S, A](inline selector: S => A): String =
    ${ extractFieldNameImpl[S, A]('selector) }

  def extractFieldNameImpl[S: Type, A: Type](selector: Expr[S => A])(using q: Quotes): Expr[String] = {
    import q.reflect.*

    @scala.annotation.tailrec
    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'")
    }

    def extractLastFieldName(term: Term): String = term match {
      case Select(_, fieldName) => fieldName
      case Ident(_)             => report.errorAndAbort("Selector must access at least one field")
      case _                    => report.errorAndAbort(s"Cannot extract field name from: ${term.show}")
    }

    val pathBody  = toPathBody(selector.asTerm)
    val fieldName = extractLastFieldName(pathBody)
    Expr(fieldName)
  }

  def toPathImpl[S: Type, A: Type](selector: Expr[S => A])(using q: Quotes): Expr[DynamicOptic] = {
    import q.reflect._

    def fail(msg: String): Nothing =
      report.errorAndAbort(msg)

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

    def getTypeName(tpe: TypeRepr): String =
      tpe.dealias match {
        case tr: TypeRef => tr.name
        case other       => other.show
      }

    def toDynamicOptic(term: Term): Expr[DynamicOptic] = term match {
      // Identity - just the parameter reference
      case Ident(_) =>
        '{ DynamicOptic.root }

      // Field access: _.field or _.a.b.c
      case Select(parent, fieldName) =>
        val parentOptic = toDynamicOptic(parent)
        val fieldExpr   = Expr(fieldName)
        '{ $parentOptic.field($fieldExpr) }

      // Collection traversal: _.items.each
      case Apply(TypeApply(elementTerm, _), List(parent)) if hasName(elementTerm, "each") =>
        val parentOptic = toDynamicOptic(parent)
        '{ $parentOptic.elements }

      // Map key traversal: _.map.eachKey
      case Apply(TypeApply(keyTerm, _), List(parent)) if hasName(keyTerm, "eachKey") =>
        val parentOptic = toDynamicOptic(parent)
        '{ $parentOptic.mapKeys }

      // Map value traversal: _.map.eachValue
      case Apply(TypeApply(valueTerm, _), List(parent)) if hasName(valueTerm, "eachValue") =>
        val parentOptic = toDynamicOptic(parent)
        '{ $parentOptic.mapValues }

      // Case selection: _.variant.when[CaseType]
      case TypeApply(Apply(TypeApply(caseTerm, _), List(parent)), List(typeTree)) if hasName(caseTerm, "when") =>
        val parentOptic = toDynamicOptic(parent)
        val caseName    = Expr(getTypeName(typeTree.tpe))
        '{ $parentOptic.caseOf($caseName) }

      // Wrapper unwrap: _.wrapper.wrapped[Inner]
      case TypeApply(Apply(TypeApply(wrapperTerm, _), List(parent)), List(_)) if hasName(wrapperTerm, "wrapped") =>
        val parentOptic = toDynamicOptic(parent)
        '{ $parentOptic.wrapped }

      // Index access: _.seq.at(0)
      case Apply(Apply(TypeApply(atTerm, _), List(parent)), List(index)) if hasName(atTerm, "at") =>
        val parentOptic = toDynamicOptic(parent)
        val indexExpr   = index.asExprOf[Int]
        '{ $parentOptic.at($indexExpr) }

      // Map key access: _.map.atKey(key)
      case Apply(Apply(TypeApply(atKeyTerm, keyTypes), List(parent)), List(key)) if hasName(atKeyTerm, "atKey") =>
        val parentOptic = toDynamicOptic(parent)
        val keyExpr     = key.asExpr
        val keyTypeRepr = keyTypes.head.tpe
        val schemaTpe   = TypeRepr.of[Schema].appliedTo(keyTypeRepr)
        Implicits.search(schemaTpe) match {
          case v: ImplicitSearchSuccess =>
            val schemaExpr = v.tree.asExpr.asInstanceOf[Expr[Schema[Any]]]
            '{ $parentOptic.atKey($keyExpr)(using $schemaExpr) }
          case _ =>
            fail(s"Cannot find Schema[${keyTypeRepr.show}] for atKey")
        }

      case other =>
        fail(s"Unsupported selector expression: ${other.show}")
    }

    val pathBody = toPathBody(selector.asTerm)
    toDynamicOptic(pathBody)
  }
}
