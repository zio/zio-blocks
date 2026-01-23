package zio.blocks.typeid

import scala.quoted._

object TypeIdMacros {

  def fromImpl[A: Type](using Quotes): Expr[TypeId[A]] = {
    import quotes.reflect._

    def getTypeId(rootTpe: TypeRepr): Expr[TypeId[A]] = {
      def getTypeIdImpl(tpe: TypeRepr): Expr[TypeId[?]] =
        tpe match {
          // Special handling for ZIO Prelude newtypes/subtypes: TypeRef(prefix, "Type")
          // Return the base class (Subtype/NewtypeCustom) TypeId instead of the concrete wrapper
          // Note: neotype expects concrete wrapper names, so only handle zio.prelude here
          case TypeRef(compTpe, "Type") =>
            val baseClassSymbol = compTpe.baseClasses.find { sym =>
              val name = sym.fullName
              name == "zio.prelude.Subtype" || name == "zio.prelude.NewtypeCustom" ||
              name == "zio.prelude.Newtype"
              // Intentionally NOT including neotype.Newtype - it expects concrete wrapper names
            }
            baseClassSymbol match {
              case Some(baseSym) =>
                // Use the base class as owner, "Type" as name
                val ownerExpr = makeOwner(baseSym)
                '{
                  TypeId(
                    $ownerExpr,
                    "Type",
                    Nil,
                    TypeDefKind.Class(),
                    Nil,
                    Nil,
                    Nil
                  )
                }
              case None =>
                // Not a recognized newtype, fall through to normal handling
                makeTypeId(tpe.typeSymbol, Nil, deep = true)
            }

          case t: TypeRef =>
            // Dealias to expand type aliases (e.g. String -> java.lang.String)
            val dealiased = t.dealias
            if (dealiased != t && !dealiased.typeSymbol.isNoSymbol) {
              getTypeIdImpl(dealiased)
            } else {
              makeTypeId(t.typeSymbol, Nil, deep = true)
            }

          case t: TermRef =>
            makeTypeId(t.termSymbol, Nil, deep = true)

          case AppliedType(base, args) =>
            base match {
              case t: TypeRef => makeTypeId(t.typeSymbol, args, deep = true)
              case t: TermRef => makeTypeId(t.termSymbol, args, deep = true)
              case _          =>
                base.dealias match {
                  case t: TypeRef => makeTypeId(t.typeSymbol, args, deep = true)
                  case t: TermRef => makeTypeId(t.termSymbol, args, deep = true)
                  case _          => getTypeIdImpl(base)
                }
            }

          case AndType(l, r) =>
            makeSyntheticTypeId("Intersection", List(l, r))

          case OrType(l, r) =>
            makeSyntheticTypeId("Union", List(l, r))

          case AnnotatedType(underlying, _) =>
            getTypeIdImpl(underlying)

          case TypeLambda(_, _, body) =>
            def unwrap(t: TypeRepr): TypeRepr = t.dealias match {
              case AnnotatedType(u, _) => unwrap(u)
              case other               => other
            }
            unwrap(body) match {
              case AppliedType(base, _) => getTypeIdImpl(base)
              case _                    => getTypeIdImpl(body)
            }

          case _ =>
            // Attempt intersection? No, catch-all.
            // Fallback for refinements etc.
            makeSyntheticTypeId("Refined", Nil)
        }
      val res = getTypeIdImpl(rootTpe)
      '{ $res.asInstanceOf[TypeId[A]] }
    }

    def makeTypeId(sym: Symbol, args: List[TypeRepr], deep: Boolean): Expr[TypeId[?]] = {
      val ownerExpr = makeOwner(sym.maybeOwner)
      val nameStr   = sym.name.stripSuffix("$")
      val nameExpr  = Expr(nameStr)

      val kindExpr = makeKind(sym)

      val typeParamsExpr = if (deep) {
        // Extract type params
        val tparams = sym.typeMembers.filter(_.isTypeParam)
        Expr.ofList(tparams.map(makeTypeParam)).asInstanceOf[Expr[List[TypeParam]]]
      } else {
        Expr.ofList(Nil).asInstanceOf[Expr[List[TypeParam]]]
      }

      val parentsExpr = if (deep && !sym.isNoSymbol) {
        val relevantParents =
          sym.typeRef.baseClasses.filter(_ != sym).filter(_.name != "Object").filter(_.name != "Any")
        Expr
          .ofList(relevantParents.map(s => makeTypeRepr(s.typeRef).asInstanceOf[Expr[zio.blocks.typeid.TypeRepr]]))
          .asInstanceOf[Expr[List[zio.blocks.typeid.TypeRepr]]]
      } else {
        Expr.ofList(Nil).asInstanceOf[Expr[List[zio.blocks.typeid.TypeRepr]]]
      }

      val argsExpr = Expr
        .ofList(args.map(t => makeTypeRepr(t).asInstanceOf[Expr[zio.blocks.typeid.TypeRepr]]))
        .asInstanceOf[Expr[List[zio.blocks.typeid.TypeRepr]]]

      '{
        TypeId(
          $ownerExpr,
          $nameExpr,
          $typeParamsExpr,
          $kindExpr,
          $parentsExpr,
          $argsExpr,
          Nil // Annotations placeholders
        )
      }
    }

    def makeOwner(sym: Symbol): Expr[Owner] =
      if (sym == Symbol.noSymbol || sym == defn.RootClass || sym == defn.RootPackage) '{ Owner.Root }
      else {
        val parent = makeOwner(sym.maybeOwner)
        val name   = sym.name.stripSuffix("$")

        if (sym.isPackageDef) '{ $parent / Owner.Package(${ Expr(name) }) }
        else if (sym.flags.is(Flags.Module) || sym.isTerm) '{ $parent / Owner.Term(${ Expr(name) }) }
        else '{ $parent / Owner.Type(${ Expr(name) }) }
      }

    def makeKind(sym: Symbol): Expr[TypeDefKind] =
      if (sym.flags.is(Flags.Trait)) '{ TypeDefKind.Trait() }
      else if (sym.flags.is(Flags.Case)) '{ TypeDefKind.Class(isCase = true) }
      else if (sym.flags.is(Flags.Module)) '{ TypeDefKind.Object }
      else '{ TypeDefKind.Class() }

    def makeTypeParam(sym: Symbol): Expr[TypeParam] = {
      val name     = sym.name
      val variance =
        if (sym.flags.is(Flags.Covariant)) '{ Variance.Covariant }
        else if (sym.flags.is(Flags.Contravariant)) '{ Variance.Contravariant }
        else '{ Variance.Invariant }

      '{ TypeParam(${ Expr(name) }, 0, $variance) }
    }

    def makeTypeRepr(tpe: TypeRepr): Expr[zio.blocks.typeid.TypeRepr] =
      // Canonicalize Any/Nothing
      if (tpe =:= TypeRepr.of[Any]) '{ zio.blocks.typeid.TypeRepr.AnyType }
      else if (tpe =:= TypeRepr.of[Nothing]) '{ zio.blocks.typeid.TypeRepr.NothingType }
      else {
        tpe.dealias match {
          case t: TypeRef =>
            val sym = t.typeSymbol
            // Double check if it is Any/Nothing via symbol just in case
            if (sym == defn.AnyClass) '{ zio.blocks.typeid.TypeRepr.AnyType }
            else if (sym == defn.NothingClass) '{ zio.blocks.typeid.TypeRepr.NothingType }
            else {
              // Use shallow ID!
              val id   = makeTypeId(sym, Nil, deep = false)
              val args = t match {
                case AppliedType(_, as) => as.map(makeTypeRepr)
                case _                  => Nil
              }
              '{ zio.blocks.typeid.TypeRepr.Ref($id, ${ Expr.ofList(args) }) }
            }

          case AppliedType(base, args) =>
            // If base is TypeRef, handled above? No, dealias handles it?
            // AppliedType(TypeRef(...), ...) matches TypeRef? No.
            // Wait, AppliedType structure:
            // AppliedType(t, args)
            makeTypeRepr(base) match {
              // If base became a Ref, we should ideally merge types?
              // But `TypeRepr.Ref` has args.
              // Let's just recurse.
              case '{ zio.blocks.typeid.TypeRepr.Ref($id, $_) } =>
                // Check if we need to replace args?
                // Usually logic is:
                '{ zio.blocks.typeid.TypeRepr.Ref($id, ${ Expr.ofList(args.map(makeTypeRepr)) }) }
              case other =>
                '{ zio.blocks.typeid.TypeRepr.AppliedType($other, ${ Expr.ofList(args.map(makeTypeRepr)) }) }
            }

          case OrType(l, r) =>
            '{ zio.blocks.typeid.TypeRepr.Union(List(${ makeTypeRepr(l) }, ${ makeTypeRepr(r) })) } // Flattening?

          case AndType(l, r) =>
            '{ zio.blocks.typeid.TypeRepr.Intersection(List(${ makeTypeRepr(l) }, ${ makeTypeRepr(r) })) }

          case _ =>
            // Fallback to Abstract/Wildcard or error?
            // For now, simpler fallback or error?
            // Let's dump as Wildcard with no bounds if unknown
            '{ zio.blocks.typeid.TypeRepr.Wildcard(zio.blocks.typeid.TypeBounds.empty) }
        }
      }

    def makeSyntheticTypeId(name: String, parents: List[TypeRepr]): Expr[TypeId[?]] = {
      // Canonicalize: Sort parents
      // We need to use TypeRepr.ordering, but we are in Expr world.
      // We can sort the List[TypeRepr] before converting to Expr.
      // But makeSyntheticTypeId takes List[TypeRepr] (the Expr helpers one defined in this file? No, param is List[TypeRepr] from strings 130/133)
      // Wait, 130 passes `List(${makeTypeRepr(l)}, ...)` which is List[Expr[TypeRepr]].
      // The signature in line 143 says parents: List[TypeRepr].
      // BUT lines 130/133 construct Exprs: '{ ... List(...) }
      // Ah, makeSyntheticTypeId is handling the Expr construction?
      // No, lines 26/29 call makeSyntheticTypeId.
      // Line 26: makeSyntheticTypeId("Intersection", List(l, r)) where l,r are TypeRepr (quotes.reflect).

      val sorted = parents.sortBy(_.show)

      val parentsExpr = Expr
        .ofList(sorted.map(p => makeTypeRepr(p).asInstanceOf[Expr[zio.blocks.typeid.TypeRepr]]))
        .asInstanceOf[Expr[List[zio.blocks.typeid.TypeRepr]]]
      '{
        TypeId(
          Owner.Root,
          ${ Expr(name) },
          Nil,
          TypeDefKind.AbstractType,
          $parentsExpr,
          Nil
        )
      }
    }

    val tpe = TypeRepr.of[A]
    getTypeId(tpe)
  }
}
