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

    // Check if this is a type alias by pattern matching on TypeRef
    tpe match {
      case TypeRef(_, sym, args) =>
        if (sym.isType && sym.asType.isAliasType && isUserDefinedAlias(c)(sym)) {
          // This is a user-defined type alias - derive it directly
          deriveNew[A](c)
        } else if (args.nonEmpty) {
          // Applied type (e.g., List[Int], Map[String, Int]) - try wildcard search
          val wildcardArgs    = args.map(_ => WildcardType)
          val existentialType = appliedType(sym, wildcardArgs)
          val typeIdType      = appliedType(typeOf[TypeId[_]].typeConstructor, existentialType)
          val implicitSearch  = c.inferImplicitValue(typeIdType, silent = true)

          if (implicitSearch != EmptyTree) {
            // Found an existing implicit instance for the type constructor
            c.Expr[TypeId[A]](q"$implicitSearch.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$tpe]]")
          } else {
            searchOrDerive[A](c)
          }
        } else {
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

  private def isUserDefinedAlias(c: blackbox.Context)(sym: c.Symbol): Boolean = {
    // Built-in aliases are in scala.Predef or scala package object
    val ownerFullName = sym.owner.fullName
    val isBuiltIn     = ownerFullName.contains("scala.Predef") ||
      (ownerFullName.contains("scala") && sym.owner.name.toString.contains("package"))

    !isBuiltIn // User-defined if it's NOT built-in
  }

  private def deriveNew[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] = {
    import c.universe._

    val tpe = weakTypeOf[A]

    // Check if this is a type alias using proper TypeRef pattern matching
    tpe match {
      case TypeRef(_, sym, _) if sym.isType && sym.asType.isAliasType =>
        // This is a type alias - extract alias information
        val aliasName      = sym.name.decodedName.toString
        val ownerExpr      = buildOwner(c)(sym.owner)
        val typeParamsExpr = buildTypeParams(c)(sym)

        // Get the aliased (underlying) type
        val aliasedType = tpe.dealias
        val aliasedExpr = buildTypeReprFromType(c)(aliasedType)

        c.Expr[TypeId[A]](
          q"""
            _root_.zio.blocks.typeid.TypeId.alias[$tpe](
              $aliasName,
              $ownerExpr,
              $typeParamsExpr,
              $aliasedExpr
            )
          """
        )
      case _ =>
        // Not a type alias - create nominal type
        val typeSymbol     = tpe.typeSymbol
        val name           = typeSymbol.name.decodedName.toString
        val ownerExpr      = buildOwner(c)(typeSymbol.owner)
        val typeParamsExpr = buildTypeParams(c)(typeSymbol)
        val defKindExpr    = buildDefKind(c)(typeSymbol)

        c.Expr[TypeId[A]](
          q"""
            _root_.zio.blocks.typeid.TypeId.nominal[$tpe](
              $name,
              $ownerExpr,
              $typeParamsExpr,
              $defKindExpr
            )
          """
        )
    }
  }

  private def buildTypeReprFromType(c: blackbox.Context)(tpe: c.Type): c.Tree = {
    import c.universe._

    tpe match {
      case TypeRef(_, sym, args) if args.nonEmpty =>
        // Applied type (e.g., List[Int], Map[String, Int])
        val tyconRepr = buildTypeReprFromSymbol(c)(sym)
        val argsRepr  = args.map(buildTypeReprFromType(c)(_))
        q"_root_.zio.blocks.typeid.TypeRepr.Applied($tyconRepr, _root_.scala.List(..$argsRepr))"
      case TypeRef(_, sym, _) =>
        // Simple type reference
        buildTypeReprFromSymbol(c)(sym)
      case _ =>
        // Fallback for other types
        q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.nominal[_root_.scala.Nothing](${tpe.typeSymbol.name.toString}, _root_.zio.blocks.typeid.Owner.Root, _root_.scala.Nil))"
    }
  }

  private def buildTypeReprFromSymbol(c: blackbox.Context)(sym: c.Symbol): c.Tree = {
    import c.universe._

    val name       = sym.name.decodedName.toString
    val typeIdExpr = name match {
      case "Int"     => q"_root_.zio.blocks.typeid.TypeId.int"
      case "String"  => q"_root_.zio.blocks.typeid.TypeId.string"
      case "Long"    => q"_root_.zio.blocks.typeid.TypeId.long"
      case "Boolean" => q"_root_.zio.blocks.typeid.TypeId.boolean"
      case "Double"  => q"_root_.zio.blocks.typeid.TypeId.double"
      case "Float"   => q"_root_.zio.blocks.typeid.TypeId.float"
      case "Byte"    => q"_root_.zio.blocks.typeid.TypeId.byte"
      case "Short"   => q"_root_.zio.blocks.typeid.TypeId.short"
      case "Char"    => q"_root_.zio.blocks.typeid.TypeId.char"
      case "Unit"    => q"_root_.zio.blocks.typeid.TypeId.unit"
      case "List"    => q"_root_.zio.blocks.typeid.TypeId.list"
      case "Option"  => q"_root_.zio.blocks.typeid.TypeId.option"
      case "Map"     => q"_root_.zio.blocks.typeid.TypeId.map"
      case "Either"  => q"_root_.zio.blocks.typeid.TypeId.either"
      case "Set"     => q"_root_.zio.blocks.typeid.TypeId.set"
      case "Vector"  => q"_root_.zio.blocks.typeid.TypeId.vector"
      case _         =>
        // Create a nominal type reference with type parameters
        val ownerExpr      = buildOwner(c)(sym.owner)
        val typeParamsExpr = if (sym.isType) buildTypeParams(c)(sym) else q"_root_.scala.Nil"
        q"_root_.zio.blocks.typeid.TypeId.nominal[_root_.scala.Nothing]($name, $ownerExpr, $typeParamsExpr)"
    }
    q"_root_.zio.blocks.typeid.TypeRepr.Ref($typeIdExpr)"
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

  // ============================================================================
  // TypeDefKind Extraction (Scala 2)
  // ============================================================================

  private def buildDefKind(c: blackbox.Context)(sym: c.Symbol): c.Tree = {
    import c.universe._

    if (sym.isModule || sym.isModuleClass) {
      buildObjectDefKind(c)(sym)
    } else if (sym.isClass) {
      val classSym = sym.asClass
      buildClassDefKind(c)(classSym)
    } else if (sym.isType && sym.asType.isAliasType) {
      q"_root_.zio.blocks.typeid.TypeDefKind.TypeAlias"
    } else if (sym.isType && sym.asType.isAbstract && !sym.isClass) {
      q"_root_.zio.blocks.typeid.TypeDefKind.AbstractType"
    } else {
      q"_root_.zio.blocks.typeid.TypeDefKind.Unknown"
    }
  }

  private def buildBaseTypes(c: blackbox.Context)(sym: c.Symbol): c.Tree = {
    import c.universe._

    // Get base classes excluding the type itself and common types
    val baseClasses = if (sym.isClass) {
      sym.asClass.baseClasses.filterNot { base =>
        base == sym ||
        base.fullName == "scala.Any" ||
        base.fullName == "scala.AnyRef" ||
        base.fullName == "java.lang.Object" ||
        base.fullName == "scala.Matchable"
      }
    } else if (sym.isModule) {
      sym.asModule.moduleClass.asClass.baseClasses.filterNot { base =>
        base == sym ||
        base.fullName == "scala.Any" ||
        base.fullName == "scala.AnyRef" ||
        base.fullName == "java.lang.Object" ||
        base.fullName == "scala.Matchable"
      }
    } else {
      Nil
    }

    val baseExprs = baseClasses.map { base =>
      buildTypeReprFromSymbol(c)(base)
    }

    q"_root_.scala.List(..$baseExprs)"
  }

  private def buildObjectDefKind(c: blackbox.Context)(sym: c.Symbol): c.Tree = {
    import c.universe._

    val basesExpr = buildBaseTypes(c)(sym)
    q"_root_.zio.blocks.typeid.TypeDefKind.Object(bases = $basesExpr)"
  }

  private def buildClassDefKind(c: blackbox.Context)(classSym: c.universe.ClassSymbol): c.Tree = {
    import c.universe._

    val basesExpr = buildBaseTypes(c)(classSym)

    if (classSym.isTrait) {
      // Trait
      val isSealed = classSym.isSealed
      if (isSealed) {
        // Get known subtypes for sealed traits
        val children     = classSym.knownDirectSubclasses.toList
        val subtypeExprs = children.map { child =>
          buildTypeReprFromSymbol(c)(child)
        }
        q"_root_.zio.blocks.typeid.TypeDefKind.Trait(isSealed = true, knownSubtypes = _root_.scala.List(..$subtypeExprs), bases = $basesExpr)"
      } else {
        q"_root_.zio.blocks.typeid.TypeDefKind.Trait(isSealed = false, bases = $basesExpr)"
      }
    } else {
      // Class (regular, case, abstract, final, value)
      val isFinal    = classSym.isFinal
      val isAbstract = classSym.isAbstract && !classSym.isTrait
      val isCase     = classSym.isCaseClass

      // Check if it's a value class (extends AnyVal)
      val isValue = classSym.baseClasses.exists(_.fullName == "scala.AnyVal")

      q"""
        _root_.zio.blocks.typeid.TypeDefKind.Class(
          isFinal = $isFinal,
          isAbstract = $isAbstract,
          isCase = $isCase,
          isValue = $isValue,
          bases = $basesExpr
        )
      """
    }
  }
}
