package zio.blocks.typeid

import scala.reflect.macros.blackbox

object TypeIdMacros {
  def fromImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] = {
    import c.universe._

    val tpe = weakTypeOf[A]

    def getTypeId(tpe: Type): Tree = {
      val dealiased = tpe.dealias
      dealiased match {
        case TypeRef(_, sym, args) =>
          makeTypeId(sym, args, deep = true)

        case SingleType(_, sym) =>
          // Singleton types like None.type, Elements.type
          // Get the type of the singleton object
          makeTypeId(sym.typeSignature.typeSymbol, Nil, deep = true)

        case RefinedType(parents, _) =>
          makeSyntheticTypeId("Intersection", parents)

        case ExistentialType(_, underlying) => getTypeId(underlying)
        case _                              =>
          c.abort(c.enclosingPosition, s"Cannot derive TypeId for $tpe. It must be a nominal type.")
      }
    }

    def makeSyntheticTypeId(name: String, parents: List[Type]): Tree = {
      val sortedParents = parents.sortBy(_.toString).map(p => makeTypeRepr(p))
      val parentsTree   = q"List(..$sortedParents)"
      val nameTree      = Literal(Constant(name))

      q"""
          zio.blocks.typeid.TypeId(
            zio.blocks.typeid.Owner.Root,
            $nameTree,
            Nil,
            zio.blocks.typeid.TypeDefKind.AbstractType,
            $parentsTree,
            Nil,
            Nil
          )
        """
    }

    def makeTypeId(sym: Symbol, args: List[Type], deep: Boolean): Tree = {
      val ownerTree = makeOwner(sym.owner)
      val nameStr   = sym.name.decodedName.toString.stripSuffix("$")
      val nameTree  = Literal(Constant(nameStr))

      val kindTree = makeKind(sym)

      val typeParamsTree = if (deep) {
        val tparams = sym.asType.typeParams
        val mapped  = tparams.map(makeTypeParam)
        q"List(..$mapped)"
      } else {
        q"Nil"
      }

      val parentsTree = if (deep && sym.isClass && sym != NoSymbol) {
        def getParents(t: Type): List[Type] = t match {
          case ClassInfoType(parents, _, _) => parents
          case PolyType(_, res)             => getParents(res)
          case _                            => Nil
        }
        val parents = getParents(sym.info)

        val filtered = parents
          .filterNot(p => p.typeSymbol.fullName == "java.lang.Object")
          .filterNot(p => p.typeSymbol.fullName == "scala.Any")
          .filterNot(p => p.typeSymbol.fullName == "scala.AnyRef")
          .filterNot(p => p.typeSymbol.fullName == "scala.Product")
          .filterNot(p => p.typeSymbol.fullName == "scala.Serializable")

        val mapped = filtered.map(t => makeTypeRepr(t))
        q"List(..$mapped)"
      } else {
        q"Nil"
      }

      val argsTree = {
        val mapped = args.map(makeTypeRepr)
        q"List(..$mapped)"
      }

      q"""
         zio.blocks.typeid.TypeId(
           $ownerTree,
           $nameTree,
           $typeParamsTree,
           $kindTree,
           $parentsTree,
           $argsTree,
           Nil
         )
       """
    }

    def makeOwner(sym: Symbol): Tree =
      if (sym == NoSymbol || sym == rootMirror.RootPackage || sym == rootMirror.RootClass) {
        q"zio.blocks.typeid.Owner.Root"
      } else {
        val parent = makeOwner(sym.owner)
        val name   = sym.name.decodedName.toString.stripSuffix("$")

        if (sym.isPackage) q"$parent / zio.blocks.typeid.Owner.Package($name)"
        else if (sym.isModule || sym.isTerm) q"$parent / zio.blocks.typeid.Owner.Term($name)"
        else q"$parent / zio.blocks.typeid.Owner.Type($name)"
      }

    def makeKind(sym: Symbol): Tree =
      if (!sym.isClass) q"zio.blocks.typeid.TypeDefKind.AbstractType"
      else {
        val cls = sym.asClass
        if (cls.isTrait) {
          val isSealed = cls.isSealed
          q"zio.blocks.typeid.TypeDefKind.Trait($isSealed, Nil)"
        } else if (cls.isCaseClass) q"zio.blocks.typeid.TypeDefKind.Class(isCase = true)"
        else if (cls.isModuleClass) q"zio.blocks.typeid.TypeDefKind.Object"
        else q"zio.blocks.typeid.TypeDefKind.Class()"
      }

    def makeTypeParam(sym: Symbol): Tree = {
      val name     = sym.name.decodedName.toString
      val variance =
        if (sym.asType.isCovariant) q"zio.blocks.typeid.Variance.Covariant"
        else if (sym.asType.isContravariant) q"zio.blocks.typeid.Variance.Contravariant"
        else q"zio.blocks.typeid.Variance.Invariant"
      q"zio.blocks.typeid.TypeParam($name, 0, $variance)"
    }

    def makeTypeRepr(tpe: Type): Tree =
      // Canonicalize Any/Nothing
      if (tpe =:= typeOf[Any]) q"zio.blocks.typeid.TypeRepr.AnyType"
      else if (tpe =:= typeOf[Nothing]) q"zio.blocks.typeid.TypeRepr.NothingType"
      else {
        val dealiased = tpe.dealias
        dealiased match {
          case TypeRef(_, sym, args) =>
            // Double check standard types
            if (sym == definitions.AnyClass) q"zio.blocks.typeid.TypeRepr.AnyType"
            else if (sym == definitions.NothingClass) q"zio.blocks.typeid.TypeRepr.NothingType"
            else {
              val id        = makeTypeId(sym, Nil, deep = false)
              val argsTrees = args.map(makeTypeRepr)
              // Use Ref with args directly
              q"zio.blocks.typeid.TypeRepr.Ref($id, List(..$argsTrees))"
            }

          case RefinedType(parents, _) =>
            // Sort parents for canonicalization
            val sortedParents = parents.sortBy(_.toString).map(makeTypeRepr)
            q"zio.blocks.typeid.TypeRepr.Intersection(List(..$sortedParents))"

          case ExistentialType(_, underlying) => makeTypeRepr(underlying)

          case _ =>
            // Fallback for wildcards or unsupported types
            q"zio.blocks.typeid.TypeRepr.Wildcard(zio.blocks.typeid.TypeBounds.empty)"
        }
      }

    c.Expr[TypeId[A]](getTypeId(tpe))
  }
}
