package zio.blocks.typeid

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object TypeIdMacros {

  /**
   * Derives a TypeId for any type or type constructor.
   *
   * This macro first searches for an existing implicit TypeId instance. If
   * found, it uses that instance. Otherwise, it derives a new one by
   * extracting:
   *   - The type's simple name
   *   - The owner path (packages, enclosing objects/classes)
   *   - Type parameters (for type constructors)
   *   - Classification (nominal or alias - opaque types don't exist in Scala 2)
   */
  def derived[A]: TypeId[A] = macro derivedImpl[A]

  def derivedImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] = {
    import c.universe._

    val tpe = weakTypeOf[A]

    // Check if this is an applied type (e.g., List[Int], Map[String, Int])
    tpe.dealias match {
      case TypeRef(_, sym, args) if args.nonEmpty =>
        // For applied types, try to find an implicit for the type constructor with wildcards
        // e.g., for List[Int], look for TypeId[List[_]]
        val wildcardArgs    = args.map(_ => WildcardType)
        val existentialType = appliedType(sym, wildcardArgs)
        val typeIdType      = appliedType(typeOf[TypeId[_]].typeConstructor, existentialType)
        val implicitSearch  = c.inferImplicitValue(typeIdType, silent = true)

        if (implicitSearch != EmptyTree) {
          // Found an existing implicit instance for the type constructor
          // Cast it to the expected type since TypeId is invariant
          c.Expr[TypeId[A]](q"$implicitSearch.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$tpe]]")
        } else {
          // Fall back to searching for exact type or deriving
          searchOrDerive[A](c)
        }
      case _ =>
        searchOrDerive[A](c)
    }
  }

  private def searchOrDerive[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] = {
    import c.universe._

    val tpe = weakTypeOf[A]

    // First, try to find an existing implicit TypeId[A]
    val typeIdType     = appliedType(typeOf[TypeId[_]].typeConstructor, tpe)
    val implicitSearch = c.inferImplicitValue(typeIdType, silent = true)

    if (implicitSearch != EmptyTree) {
      // Found an existing implicit instance, use it
      c.Expr[TypeId[A]](implicitSearch)
    } else {
      // No implicit found, derive one
      deriveNew[A](c)
    }
  }

  private def deriveNew[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] = {
    import c.universe._

    val tpe        = weakTypeOf[A]
    val typeSymbol = tpe.typeSymbol

    // Extract the simple name
    val name = typeSymbol.name.decodedName.toString

    // Build the owner path
    val ownerExpr = buildOwner(c)(typeSymbol.owner)

    // Extract type parameters
    val typeParamsExpr = buildTypeParams(c)(typeSymbol)

    // Determine if this is an alias or nominal type
    val isAlias = typeSymbol.isType && typeSymbol.asType.isAliasType

    if (isAlias) {
      // Type alias
      val aliasedExpr = q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.int)"
      c.Expr[TypeId[A]](
        q"""
          _root_.zio.blocks.typeid.TypeId.alias[$tpe](
            $name,
            $ownerExpr,
            $typeParamsExpr,
            $aliasedExpr
          )
        """
      )
    } else {
      // Nominal type (class, trait, object)
      c.Expr[TypeId[A]](
        q"""
          _root_.zio.blocks.typeid.TypeId.nominal[$tpe](
            $name,
            $ownerExpr,
            $typeParamsExpr
          )
        """
      )
    }
  }

  private def buildOwner(c: blackbox.Context)(sym: c.Symbol): c.Tree = {
    import c.universe._

    def loop(s: Symbol, acc: List[c.Tree]): List[c.Tree] =
      if (s == NoSymbol || s.isPackageClass && s.fullName == "<root>" || s.fullName == "<empty>") {
        acc
      } else if (s.isPackage || s.isPackageClass) {
        val pkgName = s.name.decodedName.toString
        if (pkgName != "<root>" && pkgName != "<empty>") {
          loop(s.owner, q"_root_.zio.blocks.typeid.Owner.Package($pkgName)" :: acc)
        } else {
          acc
        }
      } else if (s.isModule || s.isModuleClass) {
        val termName = s.name.decodedName.toString.stripSuffix("$")
        loop(s.owner, q"_root_.zio.blocks.typeid.Owner.Term($termName)" :: acc)
      } else if (s.isClass || s.isType) {
        loop(s.owner, q"_root_.zio.blocks.typeid.Owner.Type(${s.name.decodedName.toString})" :: acc)
      } else {
        loop(s.owner, acc)
      }

    val segments = loop(sym, Nil)
    q"_root_.zio.blocks.typeid.Owner(_root_.scala.List(..$segments))"
  }

  private def buildTypeParams(c: blackbox.Context)(sym: c.Symbol): c.Tree = {
    import c.universe._

    val params = sym.asType.typeParams.zipWithIndex.map { case (p, idx) =>
      val paramName = p.name.decodedName.toString
      q"_root_.zio.blocks.typeid.TypeParam($paramName, $idx)"
    }

    q"_root_.scala.List(..$params)"
  }
}
