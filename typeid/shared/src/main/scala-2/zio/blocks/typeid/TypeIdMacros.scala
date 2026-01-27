package zio.blocks.typeid

import scala.reflect.macros.blackbox

object TypeIdMacros {

  def fromImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] = {
    import c.universe._

    val tpe = weakTypeOf[A]

    // Known ZIO Prelude base types for newtype detection
    val zioPreludeNewtypeBases = Set(
      "zio.prelude.NewtypeCustom",
      "zio.prelude.SubtypeCustom",
      "zio.prelude.Newtype",
      "zio.prelude.Subtype",
      "zio.prelude.NewtypeVersionSpecific"
    )

    // Check if a type is a ZIO Prelude newtype pattern: TypeRef(companionTpe, Type, Nil)
    // where companionTpe's base classes include zio.prelude.Newtype
    def isZioPreludeNewtype(t: Type): Boolean = {
      val effectiveTpe = t.dealias
      effectiveTpe match {
        case TypeRef(compTpe, typeSym, Nil) if typeSym.name.toString == "Type" =>
          compTpe.baseClasses.exists(bc => zioPreludeNewtypeBases.contains(bc.fullName))
        case _ => false
      }
    }

    // Build an owner expression from a symbol chain
    def buildOwner(sym: Symbol): Tree = {
      def loop(s: Symbol, acc: List[Tree]): List[Tree] =
        if (s == NoSymbol || s.isPackageClass && s.fullName == "<root>" || s.fullName == "<empty>") {
          acc
        } else if (s.isPackage || s.isPackageClass) {
          val pkgName = s.name.decodedName.toString
          if (pkgName != "<root>" && pkgName != "<empty>") {
            loop(s.owner, q"_root_.zio.blocks.typeid.Owner.Package($pkgName)" :: acc)
          } else acc
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

    // Build type parameters list
    def buildTypeParams(sym: Symbol): Tree = {
      if (!sym.isType) q"_root_.scala.Nil"
      else {
        val params = sym.asType.typeParams.zipWithIndex.map { case (p, idx) =>
          val paramName = p.name.decodedName.toString
          val typeSym = p.asType
          val varianceExpr = if (typeSym.isCovariant) {
            q"_root_.zio.blocks.typeid.Variance.Covariant"
          } else if (typeSym.isContravariant) {
            q"_root_.zio.blocks.typeid.Variance.Contravariant"
          } else {
            q"_root_.zio.blocks.typeid.Variance.Invariant"
          }
          q"_root_.zio.blocks.typeid.TypeParam($paramName, $idx, $varianceExpr)"
        }
        q"_root_.scala.List(..$params)"
      }
    }

    // Build TypeDefKind
    def buildDefKind(sym: Symbol): Tree = {
      if (sym.isModule || sym.isModuleClass) {
        q"_root_.zio.blocks.typeid.TypeDefKind.Object"
      } else if (sym.isClass) {
        val classSym = sym.asClass
        if (classSym.isTrait) {
          val isSealed = classSym.isSealed
          q"_root_.zio.blocks.typeid.TypeDefKind.Trait(isSealed = $isSealed)"
        } else {
          val isFinal = classSym.isFinal
          val isAbstract = classSym.isAbstract && !classSym.isTrait
          val isCase = classSym.isCaseClass
          val isValue = classSym.baseClasses.exists(_.fullName == "scala.AnyVal")
          q"""_root_.zio.blocks.typeid.TypeDefKind.Class(
            isFinal = $isFinal,
            isAbstract = $isAbstract,
            isCase = $isCase,
            isValue = $isValue
          )"""
        }
      } else if (sym.isType && sym.asType.isAliasType) {
        // Type aliases are transparent in Scala 2, just use Class as a fallback
        q"_root_.zio.blocks.typeid.TypeDefKind.Class()"
      } else if (sym.isType && sym.asType.isAbstract && !sym.isClass) {
        q"_root_.zio.blocks.typeid.TypeDefKind.AbstractType"
      } else {
        q"_root_.zio.blocks.typeid.TypeDefKind.Class()"
      }
    }

    // Build TypeRepr for a type
    def buildTypeRepr(t: Type): Tree = {
      t.dealias match {
        case TypeRef(_, sym, Nil) =>
          // Simple type without type args - create a Ref with a DynamicTypeId
          val name = sym.name.decodedName.toString
          val ownerExpr = buildOwner(sym.owner)
          val typeParamsExpr = buildTypeParams(sym)
          val defKindExpr = buildDefKind(sym)
          q"""_root_.zio.blocks.typeid.TypeRepr.Ref(
            _root_.zio.blocks.typeid.DynamicTypeId(
              $ownerExpr,
              $name,
              $typeParamsExpr,
              $defKindExpr,
              _root_.scala.Nil,
              _root_.scala.Nil
            ),
            _root_.scala.Nil
          )"""
        case TypeRef(_, sym, args) =>
          // Applied type - create Ref for constructor with applied args
          val name = sym.name.decodedName.toString
          val ownerExpr = buildOwner(sym.owner)
          val typeParamsExpr = buildTypeParams(sym)
          val defKindExpr = buildDefKind(sym)
          val argReprs = args.map(buildTypeRepr)
          q"""_root_.zio.blocks.typeid.TypeRepr.AppliedType(
            _root_.zio.blocks.typeid.TypeRepr.Ref(
              _root_.zio.blocks.typeid.DynamicTypeId(
                $ownerExpr,
                $name,
                $typeParamsExpr,
                $defKindExpr,
                _root_.scala.Nil,
                _root_.scala.Nil
              ),
              _root_.scala.Nil
            ),
            _root_.scala.List(..$argReprs)
          )"""
        case _ =>
          // Fallback for other type structures
          val sym = t.typeSymbol
          val name = sym.name.decodedName.toString
          val ownerExpr = buildOwner(sym.owner)
          q"""_root_.zio.blocks.typeid.TypeRepr.Ref(
            _root_.zio.blocks.typeid.DynamicTypeId(
              $ownerExpr,
              $name,
              _root_.scala.Nil,
              _root_.zio.blocks.typeid.TypeDefKind.Class(),
              _root_.scala.Nil,
              _root_.scala.Nil
            ),
            _root_.scala.Nil
          )"""
      }
    }

    // Build TypeId for ZIO Prelude newtypes
    def buildZioPreludeTypeId(t: Type): Tree = {
      val effectiveTpe = t.dealias
      effectiveTpe match {
        case TypeRef(compTpe, _, Nil) =>
          // Extract the companion object from the prefix
          compTpe match {
            case SingleType(_, termSym) if termSym.isModule =>
              // The termSym is the companion object (e.g., Name$)
              val newtypeName = termSym.name.decodedName.toString.stripSuffix("$")
              val ownerExpr = buildOwner(termSym.owner)
              q"""_root_.zio.blocks.typeid.TypeId[$t](
                _root_.zio.blocks.typeid.DynamicTypeId(
                  $ownerExpr,
                  $newtypeName,
                  _root_.scala.Nil,
                  _root_.zio.blocks.typeid.TypeDefKind.Class(),
                  _root_.scala.Nil,
                  _root_.scala.Nil
                )
              )"""
            case _ =>
              // Fallback: use typeSymbol of the prefix
              val companionSym = compTpe.typeSymbol
              val newtypeName = companionSym.name.decodedName.toString.stripSuffix("$")
              val ownerExpr = buildOwner(companionSym.owner)
              q"""_root_.zio.blocks.typeid.TypeId[$t](
                _root_.zio.blocks.typeid.DynamicTypeId(
                  $ownerExpr,
                  $newtypeName,
                  _root_.scala.Nil,
                  _root_.zio.blocks.typeid.TypeDefKind.Class(),
                  _root_.scala.Nil,
                  _root_.scala.Nil
                )
              )"""
          }
        case _ =>
          c.abort(c.enclosingPosition, s"Cannot derive TypeId for ZIO Prelude newtype: $t")
      }
    }

    // Build TypeId for regular types
    def buildRegularTypeId(t: Type): Tree = {
      // Dealias to get the canonical type
      val effectiveTpe = t.dealias
      val typeSymbol = effectiveTpe.typeSymbol
      val name = typeSymbol.name.decodedName.toString
      val ownerExpr = buildOwner(typeSymbol.owner)
      val typeParamsExpr = buildTypeParams(typeSymbol)
      val defKindExpr = buildDefKind(typeSymbol)
      
      // Extract type arguments if this is an applied type
      val argsExpr = effectiveTpe match {
        case TypeRef(_, _, args) if args.nonEmpty =>
          val argReprs = args.map(buildTypeRepr)
          q"_root_.scala.List(..$argReprs)"
        case _ =>
          q"_root_.scala.Nil"
      }

      q"""_root_.zio.blocks.typeid.TypeId[$t](
        _root_.zio.blocks.typeid.DynamicTypeId(
          $ownerExpr,
          $name,
          $typeParamsExpr,
          $defKindExpr,
          _root_.scala.Nil,
          $argsExpr
        )
      )"""
    }

    // Main derivation logic
    val result = if (isZioPreludeNewtype(tpe)) {
      buildZioPreludeTypeId(tpe)
    } else {
      buildRegularTypeId(tpe)
    }

    c.Expr[TypeId[A]](result)
  }
}
