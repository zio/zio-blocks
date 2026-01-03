package zio.blocks.typeid

import scala.quoted.*

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
   *   - Classification (nominal, alias, or opaque)
   */
  inline def derived[A <: AnyKind]: TypeId[A] = ${ derivedImpl[A] }

  private def derivedImpl[A <: AnyKind: Type](using Quotes): Expr[TypeId[A]] = {
    import quotes.reflect.*

    val tpe = quotes.reflect.TypeRepr.of[A]

    // Check if this is an applied type (e.g., List[Int], Map[String, Int])
    tpe match {
      case AppliedType(tycon, _) =>
        // For applied types, try to find an implicit for the type constructor
        // e.g., for List[Int], look for TypeId[List[_]]
        val tyconSym   = tycon.typeSymbol
        val typeParams = tyconSym.typeMembers.filter(_.isTypeParam)

        if (typeParams.isEmpty) {
          // No type parameters, just search or derive
          searchOrDerive[A]
        } else {
          // Create existential type with wildcards (e.g., List[_])
          val wildcardArgs    = typeParams.map(_ => TypeRepr.of[Any])
          val existentialType = tycon.appliedTo(wildcardArgs)

          // Try to find implicit for the existential type (e.g., List[Any] which matches List[_])
          existentialType.asType match {
            case '[t] =>
              val typeIdType = quotes.reflect.TypeRepr.of[TypeId[t]]
              Implicits.search(typeIdType) match {
                case iss: ImplicitSearchSuccess =>
                  // Cast to the expected type since TypeId is invariant
                  val found = iss.tree.asExprOf[TypeId[t]]
                  '{ $found.asInstanceOf[TypeId[A]] }
                case _: ImplicitSearchFailure =>
                  // Fall back to searching for the exact type or deriving
                  searchOrDerive[A]
              }
            case _ =>
              // Fallback for edge cases
              searchOrDerive[A]
          }
        }
      case _ =>
        searchOrDerive[A]
    }
  }

  private def searchOrDerive[A <: AnyKind: Type](using Quotes): Expr[TypeId[A]] = {
    import quotes.reflect.*

    // First, try to find an existing implicit TypeId[A]
    val typeIdType = quotes.reflect.TypeRepr.of[TypeId[A]]

    Implicits.search(typeIdType) match {
      case iss: ImplicitSearchSuccess =>
        // Found an existing implicit instance, use it
        iss.tree.asExprOf[TypeId[A]]
      case _: ImplicitSearchFailure =>
        // No implicit found, derive one
        deriveNew[A]
    }
  }

  private def deriveNew[A <: AnyKind: Type](using Quotes): Expr[TypeId[A]] = {
    import quotes.reflect.*

    val tpe        = quotes.reflect.TypeRepr.of[A]
    val typeSymbol = tpe.typeSymbol

    // Extract the simple name, stripping $ suffix for modules/objects
    val rawName = typeSymbol.name
    val name    = if (typeSymbol.flags.is(Flags.Module)) rawName.stripSuffix("$") else rawName

    // Build the owner path
    val ownerExpr = buildOwner(typeSymbol.owner)

    // Extract type parameters
    val typeParamsExpr = buildTypeParams(typeSymbol)

    // Determine if this is an alias, opaque, or nominal type
    val flags = typeSymbol.flags

    if (flags.is(Flags.Opaque)) {
      // Opaque type
      val reprExpr = '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.string) } // TODO: extract actual representation
      '{
        TypeId.opaque[A](
          ${ Expr(name) },
          ${ ownerExpr },
          ${ typeParamsExpr },
          ${ reprExpr }
        )
      }
    } else if (typeSymbol.isAliasType) {
      // Type alias
      val aliasedExpr = '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.int) } // TODO: extract actual aliased type
      '{
        TypeId.alias[A](
          ${ Expr(name) },
          ${ ownerExpr },
          ${ typeParamsExpr },
          ${ aliasedExpr }
        )
      }
    } else {
      // Nominal type (class, trait, object)
      '{
        TypeId.nominal[A](
          ${ Expr(name) },
          ${ ownerExpr },
          ${ typeParamsExpr }
        )
      }
    }
  }

  private def buildOwner(using Quotes)(sym: quotes.reflect.Symbol): Expr[Owner] = {
    import quotes.reflect.*

    def loop(s: Symbol, acc: List[Expr[Owner.Segment]]): List[Expr[Owner.Segment]] =
      if (s.isNoSymbol || s == defn.RootPackage || s == defn.RootClass || s == defn.EmptyPackageClass) {
        acc
      } else if (s.isPackageDef) {
        loop(s.owner, '{ Owner.Package(${ Expr(s.name) }) } :: acc)
      } else if (s.isClassDef && s.flags.is(Flags.Module)) {
        loop(s.owner, '{ Owner.Term(${ Expr(s.name.stripSuffix("$")) }) } :: acc)
      } else if (s.isClassDef) {
        loop(s.owner, '{ Owner.Type(${ Expr(s.name) }) } :: acc)
      } else {
        loop(s.owner, acc)
      }

    val segments = loop(sym, Nil)
    '{ Owner(${ Expr.ofList(segments) }) }
  }

  private def buildTypeParams(using Quotes)(sym: quotes.reflect.Symbol): Expr[List[TypeParam]] = {
    val params = sym.typeMembers.filter(_.isTypeParam).zipWithIndex.map { case (p, idx) =>
      '{ TypeParam(${ Expr(p.name) }, ${ Expr(idx) }) }
    }

    Expr.ofList(params)
  }
}
