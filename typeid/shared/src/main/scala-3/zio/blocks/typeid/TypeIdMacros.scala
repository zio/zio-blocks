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

    // Handle union and intersection types first
    tpe match {
      case OrType(left, right) =>
        deriveUnionType[A](left, right)
      case AndType(left, right) =>
        deriveIntersectionType[A](left, right)
      case AppliedType(tycon, args) =>
        // Check if the type constructor is a type alias
        tycon match {
          case tr: TypeRef if tr.typeSymbol.isAliasType =>
            // This is an applied type alias (e.g., StringMap[Int])
            deriveAppliedTypeAlias[A](tr, args)
          case _ =>
            // Regular applied type - try to find implicit for the type constructor
            deriveAppliedType[A](tycon)
        }
      case _ =>
        searchOrDerive[A]
    }
  }

  private def deriveAppliedType[A <: AnyKind: Type](using
    Quotes
  )(
    tycon: quotes.reflect.TypeRepr
  ): Expr[TypeId[A]] = {
    import quotes.reflect.*

    val tyconSym   = tycon.typeSymbol
    val typeParams = tyconSym.typeMembers.filter(_.isTypeParam)

    if (typeParams.isEmpty) {
      searchOrDerive[A]
    } else {
      // Create existential type with wildcards (e.g., List[_])
      val wildcardArgs    = typeParams.map(_ => TypeRepr.of[Any])
      val existentialType = tycon.appliedTo(wildcardArgs)

      // Try to find implicit for the existential type
      existentialType.asType match {
        case '[t] =>
          val typeIdType = quotes.reflect.TypeRepr.of[TypeId[t]]
          Implicits.search(typeIdType) match {
            case iss: ImplicitSearchSuccess =>
              val found = iss.tree.asExprOf[TypeId[t]]
              '{ $found.asInstanceOf[TypeId[A]] }
            case _: ImplicitSearchFailure =>
              searchOrDerive[A]
          }
        case _ =>
          searchOrDerive[A]
      }
    }
  }

  private def deriveAppliedTypeAlias[A <: AnyKind: Type](using
    Quotes
  )(
    tr: quotes.reflect.TypeRef,
    _args: List[quotes.reflect.TypeRepr]
  ): Expr[TypeId[A]] = {

    val typeSymbol     = tr.typeSymbol
    val name           = typeSymbol.name
    val ownerExpr      = buildOwner(typeSymbol.owner)
    val typeParamsExpr = buildTypeParams(typeSymbol)

    // Get the aliased type (with args applied)
    val aliasedType = tr.translucentSuperType.dealias
    val aliasedExpr = buildTypeReprFromTypeRepr(aliasedType)

    '{
      TypeId.alias[A](
        ${ Expr(name) },
        ${ ownerExpr },
        ${ typeParamsExpr },
        ${ aliasedExpr }
      )
    }
  }

  private def deriveUnionType[A <: AnyKind: Type](using
    Quotes
  )(
    left: quotes.reflect.TypeRepr,
    right: quotes.reflect.TypeRepr
  ): Expr[TypeId[A]] = {

    val leftReprExpr  = buildTypeReprFromTypeRepr(left)
    val rightReprExpr = buildTypeReprFromTypeRepr(right)

    '{
      TypeId.nominal[A](
        "Union",
        Owner.Root,
        Nil
      )
    }
  }

  private def deriveIntersectionType[A <: AnyKind: Type](using
    Quotes
  )(
    left: quotes.reflect.TypeRepr,
    right: quotes.reflect.TypeRepr
  ): Expr[TypeId[A]] =

    '{
      TypeId.nominal[A](
        "Intersection",
        Owner.Root,
        Nil
      )
    }

  private def searchOrDerive[A <: AnyKind: Type](using Quotes): Expr[TypeId[A]] = {
    import quotes.reflect.*

    // Check if this is a user-defined type alias first
    val currentTpe = TypeRepr.of[A]
    currentTpe match {
      case tr: TypeRef if tr.typeSymbol.isAliasType =>
        if (isUserDefinedAlias(tr)) {
          // This is a user-defined type alias - derive it directly without searching for implicits
          deriveNew[A]
        } else {
          searchForImplicits[A]
        }
      case _ =>
        searchForImplicits[A]
    }
  }

  private def searchForImplicits[A <: AnyKind: Type](using Quotes): Expr[TypeId[A]] = {
    import quotes.reflect.*

    // Try to find an existing implicit TypeId[A]
    val typeIdType = quotes.reflect.TypeRepr.of[TypeId[A]]

    Implicits.search(typeIdType) match {
      case iss: ImplicitSearchSuccess =>
        // Found an existing implicit instance, use it directly
        iss.tree.asExprOf[TypeId[A]]
      case _: ImplicitSearchFailure =>
        // No implicit found, derive one
        deriveNew[A]
    }
  }

  private def isUserDefinedAlias(using Quotes)(tr: quotes.reflect.TypeRef): Boolean = {

    val owner     = tr.typeSymbol.owner
    val ownerPath = buildOwnerPath(owner)

    // Built-in aliases are in scala.Predef or scala package object
    val isBuiltIn = ownerPath.exists(_.contains("Predef")) ||
      (ownerPath.contains("scala") && ownerPath.exists(_.contains("package")))

    !isBuiltIn // User-defined if it's NOT built-in
  }

  private def buildOwnerPath(using Quotes)(sym: quotes.reflect.Symbol): List[String] = {
    import quotes.reflect.*

    def loop(s: Symbol, acc: List[String]): List[String] =
      if (s.isNoSymbol || s == defn.RootPackage || s == defn.RootClass || s == defn.EmptyPackageClass) {
        acc
      } else {
        loop(s.owner, s.name :: acc)
      }

    loop(sym, Nil)
  }

  private def deriveNew[A <: AnyKind: Type](using Quotes): Expr[TypeId[A]] = {
    import quotes.reflect.*

    val tpe = quotes.reflect.TypeRepr.of[A]

    // Check if this is a type alias (TypeRef with isAliasType)
    tpe match {
      case tr: TypeRef if tr.typeSymbol.isAliasType =>
        deriveTypeAlias[A](tr)
      case _ =>
        deriveNominalOrOpaque[A](tpe)
    }
  }

  private def deriveTypeAlias[A <: AnyKind: Type](using
    Quotes
  )(
    tr: quotes.reflect.TypeRef
  ): Expr[TypeId[A]] = {

    val typeSymbol     = tr.typeSymbol
    val name           = typeSymbol.name
    val ownerExpr      = buildOwner(typeSymbol.owner)
    val typeParamsExpr = buildTypeParams(typeSymbol)

    // Get the aliased type
    val aliasedType = tr.translucentSuperType.dealias
    val aliasedExpr = buildTypeReprFromTypeRepr(aliasedType)

    '{
      TypeId.alias[A](
        ${ Expr(name) },
        ${ ownerExpr },
        ${ typeParamsExpr },
        ${ aliasedExpr }
      )
    }
  }

  private def deriveNominalOrOpaque[A <: AnyKind: Type](using
    Quotes
  )(
    tpe: quotes.reflect.TypeRepr
  ): Expr[TypeId[A]] = {
    import quotes.reflect.*

    val typeSymbol = tpe.typeSymbol

    // Extract the simple name, stripping $ suffix for modules/objects
    val rawName = typeSymbol.name
    val name    = if (typeSymbol.flags.is(Flags.Module)) rawName.stripSuffix("$") else rawName

    // Build the owner path
    val ownerExpr = buildOwner(typeSymbol.owner)

    // Extract type parameters
    val typeParamsExpr = buildTypeParams(typeSymbol)

    // Determine if this is an opaque or nominal type
    val flags = typeSymbol.flags

    if (flags.is(Flags.Opaque)) {
      // Opaque type - extract the actual underlying representation
      val reprExpr = extractOpaqueRepresentation(tpe, typeSymbol)
      '{
        TypeId.opaque[A](
          ${ Expr(name) },
          ${ ownerExpr },
          ${ typeParamsExpr },
          ${ reprExpr }
        )
      }
    } else {
      // Nominal type (class, trait, object, enum)
      '{
        TypeId.nominal[A](
          ${ Expr(name) },
          ${ ownerExpr },
          ${ typeParamsExpr }
        )
      }
    }
  }

  private def extractOpaqueRepresentation(using
    Quotes
  )(
    tpe: quotes.reflect.TypeRepr,
    typeSymbol: quotes.reflect.Symbol
  ): Expr[zio.blocks.typeid.TypeRepr] = {
    import quotes.reflect.*

    // For opaque types, use translucentSuperType to get the underlying type
    tpe match {
      case tr: TypeRef if tr.isOpaqueAlias =>
        val underlying = tr.translucentSuperType.dealias
        buildTypeReprFromTypeRepr(underlying)
      case _ =>
        // Fallback - try dealias
        val underlying = tpe.dealias
        if (underlying != tpe) {
          buildTypeReprFromTypeRepr(underlying)
        } else {
          '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.string) }
        }
    }
  }

  private def buildTypeReprFromTypeTree(using
    Quotes
  )(
    tree: quotes.reflect.Tree
  ): Expr[zio.blocks.typeid.TypeRepr] = {
    import quotes.reflect.*
    tree match {
      case tpt: TypeTree => buildTypeReprFromTypeRepr(tpt.tpe)
      case _             => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.string) }
    }
  }

  private def buildTypeReprFromTypeRepr(using
    Quotes
  )(
    tpe: quotes.reflect.TypeRepr
  ): Expr[zio.blocks.typeid.TypeRepr] = {
    import quotes.reflect.*

    tpe.dealias match {
      case AppliedType(tycon, args) =>
        val tyconRepr = buildTypeReprFromTypeRepr(tycon)
        val argsRepr  = args.map(buildTypeReprFromTypeRepr)
        '{ zio.blocks.typeid.TypeRepr.Applied($tyconRepr, ${ Expr.ofList(argsRepr) }) }
      case tref: TypeRef =>
        val sym        = tref.typeSymbol
        val name       = sym.name
        val typeIdExpr = name match {
          case "Int"     => '{ TypeId.int }
          case "String"  => '{ TypeId.string }
          case "Long"    => '{ TypeId.long }
          case "Boolean" => '{ TypeId.boolean }
          case "Double"  => '{ TypeId.double }
          case "Float"   => '{ TypeId.float }
          case "Byte"    => '{ TypeId.byte }
          case "Short"   => '{ TypeId.short }
          case "Char"    => '{ TypeId.char }
          case "Unit"    => '{ TypeId.unit }
          case "List"    => '{ TypeId.list }
          case "Option"  => '{ TypeId.option }
          case "Map"     => '{ TypeId.map }
          case "Either"  => '{ TypeId.either }
          case "Set"     => '{ TypeId.set }
          case "Vector"  => '{ TypeId.vector }
          case _         =>
            // Create a nominal type reference
            val ownerExpr = buildOwner(sym.owner)
            '{ TypeId.nominal[Nothing](${ Expr(name) }, $ownerExpr, Nil) }
        }
        '{ zio.blocks.typeid.TypeRepr.Ref($typeIdExpr) }
      case tref: TermRef =>
        val sym       = tref.termSymbol
        val name      = sym.name
        val ownerExpr = buildOwner(sym.owner)
        '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.nominal[Nothing](${ Expr(name) }, $ownerExpr, Nil)) }
      case other =>
        // Fallback for other types
        val sym  = other.typeSymbol
        val name = if (sym.isNoSymbol) "Unknown" else sym.name
        '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.nominal[Nothing](${ Expr(name) }, Owner.Root, Nil)) }
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
