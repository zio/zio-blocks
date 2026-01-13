package zio.blocks.typeid

import scala.annotation.tailrec
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Macro implementation for TypeId.derive in Scala 2.13.
 *
 * This uses Scala 2's blackbox macro context to extract type information at compile time.
 */
private[typeid] object TypeIdMacros {

  def deriveMacro[A <: AnyKind: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] = {
    import c.universe._

    val tpe = weakTypeOf[A]
    val typeSymbol = tpe.typeSymbol

    // Extract owner hierarchy
    val ownerTree = extractOwner(c)(typeSymbol.owner)

    // Extract type name
    val name = typeSymbol.name.toString

    // Extract type parameters
    val typeParamsTree = extractTypeParams(c)(tpe)

    // Check if this is a type alias
    if (typeSymbol.isType && typeSymbol.asType.isAliasType) {
      val underlying = tpe.dealias
      val underlyingTree = extractTypeRepr(c)(underlying)

      c.Expr[TypeId[A]](q"""
        _root_.zio.blocks.typeid.TypeId.alias[$tpe](
          $name,
          $ownerTree,
          $typeParamsTree,
          $underlyingTree
        )
      """)
    }
    // Nominal type
    else {
      c.Expr[TypeId[A]](q"""
        _root_.zio.blocks.typeid.TypeId.nominal[$tpe](
          $name,
          $ownerTree,
          $typeParamsTree
        )
      """)
    }
  }

  /**
   * Extract the Owner for a symbol by walking up the owner chain.
   */
  private def extractOwner(c: blackbox.Context)(symbol: c.Symbol): c.Tree = {
    import c.universe._

    @tailrec
    def loop(sym: Symbol, acc: List[c.Tree]): List[c.Tree] = {
      if (sym == NoSymbol || sym == c.universe.rootMirror.RootClass || sym == c.universe.rootMirror.EmptyPackageClass) {
        acc.reverse
      } else {
        val ownerName = sym.name.toString
        val segment: c.Tree =
          if (sym.isPackage || sym.isPackageClass) {
            q"_root_.zio.blocks.typeid.Owner.Segment.Package($ownerName)"
          } else if (sym.isModule || sym.isModuleClass) {
            // Remove trailing $ from module names
            val cleanName = if (ownerName.endsWith("$")) ownerName.dropRight(1) else ownerName
            q"_root_.zio.blocks.typeid.Owner.Segment.Term($cleanName)"
          } else {
            q"_root_.zio.blocks.typeid.Owner.Segment.Type($ownerName)"
          }
        loop(sym.owner, segment :: acc)
      }
    }

    val segments = loop(symbol, Nil)
    q"_root_.zio.blocks.typeid.Owner(_root_.scala.collection.immutable.List(..$segments))"
  }

  /**
   * Extract type parameters from a type.
   */
  private def extractTypeParams(c: blackbox.Context)(tpe: c.Type): c.Tree = {
    import c.universe._

    val typeSymbol = tpe.typeSymbol
    if (typeSymbol.isType) {
      val typeParams = typeSymbol.asType.typeParams
      val paramTrees = typeParams.zipWithIndex.map { case (paramSym, idx) =>
        val paramName = paramSym.name.toString
        q"_root_.zio.blocks.typeid.TypeParam($paramName, $idx)"
      }
      q"_root_.scala.collection.immutable.List(..$paramTrees)"
    } else {
      q"_root_.scala.collection.immutable.List()"
    }
  }

  /**
   * Extract a TypeRepr expression from a compile-time Type.
   *
   * This recursively walks the type structure and builds a runtime TypeRepr ADT.
   */
  private def extractTypeRepr(c: blackbox.Context)(tpe: c.Type): c.Tree = {
    import c.universe._
    import c.internal._

    tpe match {
      // Applied type: F[A, B, ...]
      case TypeRef(_, _, args) if args.nonEmpty =>
        val tycon = TypeRef(NoPrefix, tpe.typeSymbol, Nil)
        val tyconTree = extractTypeRepr(c)(tycon)
        val argTrees = args.map(extractTypeRepr(c)(_))
        q"_root_.zio.blocks.typeid.TypeRepr.Applied($tyconTree, _root_.scala.collection.immutable.List(..$argTrees))"

      // Type parameter reference
      case TypeRef(_, sym, _) if sym.isParameter =>
        val paramName = sym.name.toString
        val paramIdx = 0 // TODO: Calculate proper index
        q"_root_.zio.blocks.typeid.TypeRepr.ParamRef(_root_.zio.blocks.typeid.TypeParam($paramName, $paramIdx))"

      // Nominal type reference
      case TypeRef(_, sym, _) =>
        val typeIdTree = deriveMacro[AnyKind](c)(c.WeakTypeTag(tpe.asInstanceOf[c.Type]))
        q"_root_.zio.blocks.typeid.TypeRepr.Ref($typeIdTree)"

      // Refined type (compound type in Scala 2)
      case RefinedType(parents, _) if parents.nonEmpty =>
        val parentTrees = parents.map(extractTypeRepr(c)(_))
        val firstParent = parentTrees.head
        parentTrees.tail.foldLeft(firstParent) { (acc, parent) =>
          q"_root_.zio.blocks.typeid.TypeRepr.Intersection($acc, $parent)"
        }

      // Constant type (literal)
      case ConstantType(Constant(value)) =>
        q"_root_.zio.blocks.typeid.TypeRepr.Constant($value)"

      // Singleton type
      case SingleType(_, sym) =>
        val path = extractTermPath(c)(sym)
        q"_root_.zio.blocks.typeid.TypeRepr.Singleton($path)"

      // Any type
      case t if t =:= typeOf[Any] =>
        q"_root_.zio.blocks.typeid.TypeRepr.AnyType"

      // Nothing type
      case t if t =:= typeOf[Nothing] =>
        q"_root_.zio.blocks.typeid.TypeRepr.NothingType"

      // Fallback: try to create a Ref
      case _ =>
        try {
          val typeIdTree = deriveMacro[AnyKind](c)(c.WeakTypeTag(tpe.asInstanceOf[c.Type]))
          q"_root_.zio.blocks.typeid.TypeRepr.Ref($typeIdTree)"
        } catch {
          case _: Exception =>
            c.warning(c.enclosingPosition, s"Unable to extract TypeRepr for: ${tpe.toString}, falling back to AnyType")
            q"_root_.zio.blocks.typeid.TypeRepr.AnyType"
        }
    }
  }

  /**
   * Extract a TermPath from a Symbol.
   */
  private def extractTermPath(c: blackbox.Context)(sym: c.Symbol): c.Tree = {
    import c.universe._

    @tailrec
    def loop(s: Symbol, acc: List[c.Tree]): List[c.Tree] = {
      if (s == NoSymbol || s == c.universe.rootMirror.RootClass || s == c.universe.rootMirror.EmptyPackageClass) {
        acc.reverse
      } else {
        val name = s.name.toString
        val segment: c.Tree =
          if (s.isPackage || s.isPackageClass) {
            q"_root_.zio.blocks.typeid.TermPath.Segment.Package($name)"
          } else {
            val cleanName = if (name.endsWith("$")) name.dropRight(1) else name
            q"_root_.zio.blocks.typeid.TermPath.Segment.Term($cleanName)"
          }
        loop(s.owner, segment :: acc)
      }
    }

    val segments = loop(sym, Nil)
    q"_root_.zio.blocks.typeid.TermPath(_root_.scala.collection.immutable.List(..$segments))"
  }
}
