package zio.blocks.typeid

import scala.reflect.macros.whitebox
import zio.blocks.typeid._

class TypeIdMacros(val c: whitebox.Context) {
  import c.universe._

  def deriveMacro[A: c.WeakTypeTag]: c.Expr[TypeId[A]] = {
    val tpe = weakTypeOf[A]
    val sym = tpe.typeSymbol

    if (!sym.isType) {
      c.abort(c.enclosingPosition, s"Symbol ${sym.name} is not a type")
    }

    val name           = sym.name.decodedName.toString
    val ownerTree      = getOwnerTree(sym)
    val typeParams     = sym.typeParams
    val typeParamTrees = getTypeParamsTree(typeParams)

    val impl = if (sym.isAliasType && !sym.isAbstract) {
      // Handle Alias
      // We need to get the RHS of the alias.
      // sym.typeSignature should be a PolyType or the type itself.
      // We need to be careful to get the definition, not the applied type A (which might be the alias applied).
      // But derive[A] passes the applied type or the symbol?
      // If A is MyAlias (no args), it's the alias.
      // If A is MyAlias[Int], it's applied.
      // But we want the TypeId for the definition MyAlias.

      // We need to inspect the symbol's type signature to get the RHS.
      val rhs = sym.typeSignature match {
        case PolyType(_, resultType) => resultType
        case t                       => t
      }

      val aliasedRepr = getTypeReprTree(rhs, typeParams)
      q"_root_.zio.blocks.typeid.TypeId.AliasImpl($name, $ownerTree, $typeParamTrees, $aliasedRepr)"
    } else if (sym.isClass) {
      // Handle Nominal (Class/Trait)
      q"_root_.zio.blocks.typeid.TypeId.NominalImpl($name, $ownerTree, $typeParamTrees)"
    } else {
      // Fallback
      q"_root_.zio.blocks.typeid.TypeId.NominalImpl($name, $ownerTree, $typeParamTrees)"
    }

    c.Expr[TypeId[A]](q"$impl.asInstanceOf[_root_.zio.blocks.typeid.TypeId[${tpe}]]")
  }

  private def getOwnerTree(sym: Symbol): Tree = {
    def loop(s: Symbol, segments: List[Tree]): List[Tree] =
      if (s == NoSymbol || s == rootMirror.RootClass || s == rootMirror.EmptyPackageClass) {
        segments
      } else {
        val segment = if (s.isPackage) {
          q"_root_.zio.blocks.typeid.Owner.Package(${s.name.decodedName.toString})"
        } else if (s.isModuleClass) {
          q"_root_.zio.blocks.typeid.Owner.Term(${s.name.decodedName.toString})"
        } else {
          q"_root_.zio.blocks.typeid.Owner.Type(${s.name.decodedName.toString})"
        }
        loop(s.owner, segment :: segments)
      }
    val segments = loop(sym.owner, Nil)
    q"_root_.zio.blocks.typeid.Owner(List(..$segments))"
  }

  private def getTypeParamsTree(typeParams: List[Symbol]): Tree = {
    val params = typeParams.zipWithIndex.map { case (p, i) =>
      q"_root_.zio.blocks.typeid.TypeParam(${p.name.decodedName.toString}, $i)"
    }
    q"List(..$params)"
  }

  private def getTypeReprTree(tpe: Type, ctxParams: List[Symbol]): Tree =
    tpe.dealias match {
      case TypeRef(_, sym, args) =>
        // Check if sym is one of the context type parameters
        val paramIndex = ctxParams.indexOf(sym)
        if (paramIndex >= 0) {
          // It's a type parameter
          val paramName = sym.name.decodedName.toString
          q"""
            _root_.zio.blocks.typeid.TypeRepr.ParamRef(
              _root_.zio.blocks.typeid.TypeParam($paramName, $paramIndex)
            )
          """
        } else {
          // It's a reference to a type
          // We need to derive TypeId for this type.
          // But we need the type constructor, not the applied type?
          // TypeRepr.Ref takes TypeId[_].
          // If we have List[Int], we want Ref(TypeId[List]).
          // sym is List.
          // We can generate TypeId.derive[sym].

          // However, we need to be careful about `sym`.
          // If `sym` is a class, we can use `sym.asType`.
          // If `sym` is an alias, we can use it too.

          // We need to construct the type corresponding to `sym` to pass to derive.
          // `sym.asType.toType` might work but might be missing prefix.
          // `tpe.typeConstructor` might be better.

          val typeIdTree = q"_root_.zio.blocks.typeid.TypeId.derive[${sym.asType.toType}]"

          val refTree = q"_root_.zio.blocks.typeid.TypeRepr.Ref($typeIdTree)"

          if (args.isEmpty) {
            refTree
          } else {
            val argTrees = args.map(a => getTypeReprTree(a, ctxParams))
            q"_root_.zio.blocks.typeid.TypeRepr.Applied($refTree, List(..$argTrees))"
          }
        }

      case SingleType(_, sym) =>
        // Singleton type x.type
        // We need TermPath
        val pathTree = getTermPathTree(sym)
        q"_root_.zio.blocks.typeid.TypeRepr.Singleton($pathTree)"

      case ConstantType(Constant(value)) =>
        q"_root_.zio.blocks.typeid.TypeRepr.Constant($value)"

      case RefinedType(parents, scope) =>
        // Structural type
        val parentTrees = parents.map(p => getTypeReprTree(p, ctxParams))
        val memberTrees = scope.map(s => getMemberTree(s, ctxParams)).toList
        q"_root_.zio.blocks.typeid.TypeRepr.Structural(List(..$parentTrees), List(..$memberTrees))"

      case _ =>
        // Fallback or error
        // For now, treat as AnyType or error?
        // Maybe AnyType if it's Any?
        if (tpe =:= typeOf[Any]) q"_root_.zio.blocks.typeid.TypeRepr.AnyType"
        else if (tpe =:= typeOf[Nothing]) q"_root_.zio.blocks.typeid.TypeRepr.NothingType"
        else c.abort(c.enclosingPosition, s"Unsupported type in TypeId derivation: $tpe")
    }

  private def getTermPathTree(sym: Symbol): Tree = {
    def loop(s: Symbol, segments: List[Tree]): List[Tree] =
      if (s == NoSymbol || s == rootMirror.RootClass || s == rootMirror.EmptyPackageClass) {
        segments
      } else {
        val segment = if (s.isPackage) {
          q"_root_.zio.blocks.typeid.TermPath.Package(${s.name.decodedName.toString})"
        } else {
          q"_root_.zio.blocks.typeid.TermPath.Term(${s.name.decodedName.toString})"
        }
        loop(s.owner, segment :: segments)
      }
    val segments = loop(sym, Nil)
    q"_root_.zio.blocks.typeid.TermPath(List(..$segments))"
  }

  private def getMemberTree(sym: Symbol, ctxParams: List[Symbol]): Tree = {
    val name = sym.name.decodedName.toString.trim // trim to handle operators sometimes?
    if (sym.isMethod) {
      val m          = sym.asMethod
      val paramLists = m.paramLists.map { pl =>
        pl.map { p =>
          val pName = p.name.decodedName.toString
          val pTpe  = getTypeReprTree(p.typeSignature, ctxParams)
          q"_root_.zio.blocks.typeid.Param($pName, $pTpe)"
        }
      }
      val paramListsTree = paramLists.map(pl => q"List(..$pl)")
      val resultTpe      = getTypeReprTree(m.returnType, ctxParams)
      val typeParams     = m.typeParams
      val typeParamsTree = getTypeParamsTree(typeParams)
      q"_root_.zio.blocks.typeid.Member.Def($name, $typeParamsTree, List(..$paramListsTree), $resultTpe)"
    } else if (sym.isTerm) {
      val t     = sym.asTerm
      val tpe   = getTypeReprTree(t.typeSignature, ctxParams)
      val isVar = t.isVar
      q"_root_.zio.blocks.typeid.Member.Val($name, $tpe, $isVar)"
    } else if (sym.isType) {
      // Type member
      val t                    = sym.asType
      val (tparams, low, high) = t.typeSignature match {
        case PolyType(params, TypeBounds(lo, hi)) => (params, Some(lo), Some(hi))
        case TypeBounds(lo, hi)                   => (Nil, Some(lo), Some(hi))
        case _                                    => (Nil, None, None)
      }

      val tparamsTree = getTypeParamsTree(tparams)
      val lowTree     = low.map(l => q"Some(${getTypeReprTree(l, ctxParams)})").getOrElse(q"None")
      val highTree    = high.map(h => q"Some(${getTypeReprTree(h, ctxParams)})").getOrElse(q"None")

      q"_root_.zio.blocks.typeid.Member.TypeMember($name, $tparamsTree, $lowTree, $highTree)"
    } else {
      c.abort(c.enclosingPosition, s"Unsupported member: $sym")
    }
  }
}
