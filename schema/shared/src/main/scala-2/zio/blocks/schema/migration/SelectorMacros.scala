package zio.blocks.schema.migration

import zio.blocks.schema._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Scala 2 macros for type-safe selector expressions.
 *
 * These macros convert lambda expressions like `_.fieldName.nested` into
 * [[DynamicOptic]] paths at compile time.
 */
object SelectorMacros {

  /**
   * Convert a selector lambda to a DynamicOptic.
   */
  def toOptic[A, B](selector: A => B): DynamicOptic = macro SelectorMacrosImpl.toOpticImpl[A, B]

  /**
   * Convert a selector lambda to a Selector.
   */
  def toSelector[A, B](selector: A => B): Selector[A, B] = macro SelectorMacrosImpl.toSelectorImpl[A, B]
}

class SelectorMacrosImpl(val c: blackbox.Context) {
  import c.universe._

  def toOpticImpl[A: WeakTypeTag, B: WeakTypeTag](selector: c.Expr[A => B]): c.Expr[DynamicOptic] = {
    // Use tags to avoid -Xfatal-warnings unused-parameter errors in Scala 2.
    val _        = weakTypeOf[A]
    val _        = weakTypeOf[B]
    val segments = extractPathSegments(selector.tree)
    buildDynamicOptic(segments)
  }

  def toSelectorImpl[A: WeakTypeTag, B: WeakTypeTag](selector: c.Expr[A => B]): c.Expr[Selector[A, B]] = {
    val segments = extractPathSegments(selector.tree)
    buildSelector[A, B](segments)
  }

  private sealed trait PathSegment
  private case class FieldAccess(name: String)            extends PathSegment
  private case class CaseAccess(name: String)             extends PathSegment
  private case object ElementsAccess                      extends PathSegment
  private case object MapKeysAccess                       extends PathSegment
  private case object MapValuesAccess                     extends PathSegment
  private case object WrappedAccess                       extends PathSegment
  private case class AtIndexAccess(index: Tree)           extends PathSegment
  private case class AtIndicesAccess(indices: List[Tree]) extends PathSegment
  private case class AtMapKeyAccess(key: Tree)            extends PathSegment
  private case class AtMapKeysAccess(keys: List[Tree])    extends PathSegment
  private case object OptionGet                           extends PathSegment

  private def extractPathSegments(tree: Tree): List[PathSegment] = {
    def extract(t: Tree): List[PathSegment] = t match {
      // Lambda: (x: A) => expr
      case Function(_, body) =>
        extract(body)

      // Block with just the body
      case Block(Nil, body) =>
        extract(body)

      // .each / .eachKey / .eachValue
      case q"$_[..$_]($parent).each" =>
        extract(parent) :+ ElementsAccess
      case q"$_[..$_]($parent).eachKey" =>
        extract(parent) :+ MapKeysAccess
      case q"$_[..$_]($parent).eachValue" =>
        extract(parent) :+ MapValuesAccess

      // .wrapped[T]
      case q"$_[..$_]($parent).wrapped[$_]" =>
        extract(parent) :+ WrappedAccess

      // .when[T]
      case q"$_[..$_]($parent).when[$caseTree]" =>
        val name = caseTree.tpe.typeSymbol.name.decodedName.toString.stripSuffix("$")
        extract(parent) :+ CaseAccess(name)

      // .at(index)
      case q"$_[..$_]($parent).at(..$args)" if args.size == 1 =>
        extract(parent) :+ AtIndexAccess(args.head)

      // .atIndices(i1, i2, ...)
      case q"$_[..$_]($parent).atIndices(..$args)" =>
        extract(parent) :+ AtIndicesAccess(args)

      // .atKey(key)
      case q"$_[..$_]($parent).atKey(..$args)" if args.size == 1 =>
        extract(parent) :+ AtMapKeyAccess(args.head)

      // .atKeys(k1, k2, ...)
      case q"$_[..$_]($parent).atKeys(..$args)" =>
        extract(parent) :+ AtMapKeysAccess(args)

      // Method selection: _.field
      case Select(qualifier, name) if name.toString != "apply" =>
        val segments = extract(qualifier)
        name.toString match {
          case "get"     => segments :+ OptionGet
          case "head"    => segments :+ AtIndexAccess(Literal(Constant(0)))
          case "keys"    => segments :+ MapKeysAccess
          case "values"  => segments :+ MapValuesAccess
          case fieldName => segments :+ FieldAccess(fieldName)
        }

      // Apply for indexed access: _.seq(0)
      case Apply(Select(qualifier, TermName("apply")), List(idxTree))
          if idxTree.tpe.widen.dealias <:< definitions.IntTpe =>
        extract(qualifier) :+ AtIndexAccess(idxTree)

      // Typed expression
      case Typed(expr, _) =>
        extract(expr)

      // Identifier (the parameter reference)
      case Ident(_) =>
        Nil

      case other =>
        c.abort(
          c.enclosingPosition,
          s"Unsupported selector expression: ${showRaw(other)}. " +
            "Expected path elements: .<field>, .when[<T>], .at(<index>), .atIndices(<indices>), .atKey(<key>), " +
            ".atKeys(<keys>), .each, .eachKey, .eachValue, .wrapped[<T>], or optional `.get`."
        )
    }

    extract(tree)
  }

  private def buildDynamicOptic(segments: List[PathSegment]): c.Expr[DynamicOptic] =
    if (segments.isEmpty) {
      c.Expr[DynamicOptic](q"_root_.zio.blocks.schema.DynamicOptic.root")
    } else {
      val initialOptic = q"_root_.zio.blocks.schema.DynamicOptic.root"

      val resultTree = segments.foldLeft(initialOptic: Tree) { (optic, segment) =>
        segment match {
          case FieldAccess(name) =>
            q"$optic.field($name)"
          case CaseAccess(name) =>
            q"$optic.caseOf($name)"
          case ElementsAccess =>
            q"$optic.elements"
          case MapKeysAccess =>
            q"$optic.mapKeys"
          case MapValuesAccess =>
            q"$optic.mapValues"
          case WrappedAccess =>
            q"$optic.wrapped"
          case AtIndexAccess(index) =>
            q"$optic.at($index)"
          case AtIndicesAccess(indices) =>
            q"$optic.atIndices(..$indices)"
          case AtMapKeyAccess(key) =>
            q"$optic.atKey($key)"
          case AtMapKeysAccess(keys) =>
            q"$optic.atKeys(..$keys)"
          case OptionGet =>
            // Optional access doesn't add to path
            optic
        }
      }
      c.Expr[DynamicOptic](resultTree)
    }

  private def buildSelector[A: WeakTypeTag, B: WeakTypeTag](segments: List[PathSegment]): c.Expr[Selector[A, B]] = {
    val aType = weakTypeOf[A]
    val bType = weakTypeOf[B]

    if (segments.isEmpty) {
      c.Expr[Selector[A, B]](
        q"_root_.zio.blocks.schema.migration.Selector.root[$aType].asInstanceOf[_root_.zio.blocks.schema.migration.Selector[$aType, $bType]]"
      )
    } else {
      val opticExpr = buildDynamicOptic(segments)
      c.Expr[Selector[A, B]](
        q"""
          new _root_.zio.blocks.schema.migration.Selector[$aType, $bType] {
            def toOptic: _root_.zio.blocks.schema.DynamicOptic = $opticExpr
          }
        """
      )
    }
  }
}
