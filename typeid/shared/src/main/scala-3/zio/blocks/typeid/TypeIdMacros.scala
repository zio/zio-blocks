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
        deriveUnionType[A](flattenUnion(tpe))
      case AndType(left, right) =>
        deriveIntersectionType[A](flattenIntersection(tpe))
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

  // Helper to flatten nested unions into a list
  private def flattenUnion(using Quotes)(tpe: quotes.reflect.TypeRepr): List[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*
    tpe match {
      case OrType(left, right) => flattenUnion(left) ++ flattenUnion(right)
      case other               => List(other)
    }
  }

  // Helper to flatten nested intersections into a list
  private def flattenIntersection(using Quotes)(tpe: quotes.reflect.TypeRepr): List[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*
    tpe match {
      case AndType(left, right) => flattenIntersection(left) ++ flattenIntersection(right)
      case other                => List(other)
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
    types: List[quotes.reflect.TypeRepr]
  ): Expr[TypeId[A]] = {
    // Build a TypeId that represents this union type
    // We create an alias that points to a Union TypeRepr
    val typeReprs = types.map(buildTypeReprFromTypeRepr)

    '{
      // Create a synthetic TypeId for the union
      TypeId.alias[A](
        "Union",
        Owner.Root,
        Nil,
        zio.blocks.typeid.TypeRepr.Union(${ Expr.ofList(typeReprs) })
      )
    }
  }

  private def deriveIntersectionType[A <: AnyKind: Type](using
    Quotes
  )(
    types: List[quotes.reflect.TypeRepr]
  ): Expr[TypeId[A]] = {
    // Build a TypeId that represents this intersection type
    // We create an alias that points to an Intersection TypeRepr
    val typeReprs = types.map(buildTypeReprFromTypeRepr)

    '{
      // Create a synthetic TypeId for the intersection
      TypeId.alias[A](
        "Intersection",
        Owner.Root,
        Nil,
        zio.blocks.typeid.TypeRepr.Intersection(${ Expr.ofList(typeReprs) })
      )
    }
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

  private def buildTypeReprFromTypeRepr(using
    Quotes
  )(
    tpe: quotes.reflect.TypeRepr
  ): Expr[zio.blocks.typeid.TypeRepr] = {
    import quotes.reflect.*

    tpe match {
      // Handle union types
      case OrType(_, _) =>
        val types     = flattenUnion(tpe)
        val typeReprs = types.map(buildTypeReprFromTypeRepr)
        '{ zio.blocks.typeid.TypeRepr.Union(${ Expr.ofList(typeReprs) }) }

      // Handle intersection types
      case AndType(_, _) =>
        val types     = flattenIntersection(tpe)
        val typeReprs = types.map(buildTypeReprFromTypeRepr)
        '{ zio.blocks.typeid.TypeRepr.Intersection(${ Expr.ofList(typeReprs) }) }

      // Handle applied types
      case AppliedType(tycon, args) =>
        val tyconRepr = buildTypeReprFromTypeRepr(tycon)
        val argsRepr  = args.map(buildTypeReprFromTypeRepr)
        '{ zio.blocks.typeid.TypeRepr.Applied($tyconRepr, ${ Expr.ofList(argsRepr) }) }

      // Handle constant types (literal types)
      case ConstantType(const) =>
        buildConstantTypeRepr(const)

      // Handle type references
      case tref: TypeRef =>
        buildTypeRefRepr(tref)

      // Handle term references (singleton types)
      case tref: TermRef =>
        val path = buildTermPath(tref)
        '{ zio.blocks.typeid.TypeRepr.Singleton($path) }

      // Handle this type
      case ThisType(tref) =>
        val ownerExpr = buildOwner(tref.typeSymbol)
        '{ zio.blocks.typeid.TypeRepr.ThisType($ownerExpr) }

      // Handle by-name types
      case ByNameType(underlying) =>
        val underlyingRepr = buildTypeReprFromTypeRepr(underlying)
        '{ zio.blocks.typeid.TypeRepr.ByName($underlyingRepr) }

      // Handle annotated types
      case AnnotatedType(underlying, _) =>
        // For now, just unwrap the annotation
        buildTypeReprFromTypeRepr(underlying)

      // Handle type bounds/wildcards
      case bounds: TypeBounds =>
        val lowerExpr: Expr[Option[zio.blocks.typeid.TypeRepr]] = bounds.low match {
          case nt if nt =:= TypeRepr.of[Nothing] => '{ None }
          case other                             =>
            val otherRepr = buildTypeReprFromTypeRepr(other)
            '{ Some($otherRepr) }
        }
        val upperExpr: Expr[Option[zio.blocks.typeid.TypeRepr]] = bounds.hi match {
          case at if at =:= TypeRepr.of[Any] => '{ None }
          case other                         =>
            val otherRepr = buildTypeReprFromTypeRepr(other)
            '{ Some($otherRepr) }
        }
        '{ zio.blocks.typeid.TypeRepr.Wildcard(zio.blocks.typeid.TypeBounds($lowerExpr, $upperExpr)) }

      // Handle type lambdas
      case tl: TypeLambda =>
        val params = tl.paramNames.zipWithIndex.map { case (name, idx) =>
          '{ TypeParam(${ Expr(name) }, ${ Expr(idx) }) }
        }
        val bodyRepr = buildTypeReprFromTypeRepr(tl.resType)
        '{ zio.blocks.typeid.TypeRepr.TypeLambda(${ Expr.ofList(params) }, $bodyRepr) }

      // Handle param refs within type lambdas
      case pr: ParamRef =>
        val paramName = pr.binder match {
          case tl: TypeLambda => tl.paramNames(pr.paramNum)
          case _              => s"T${pr.paramNum}"
        }
        '{ zio.blocks.typeid.TypeRepr.ParamRef(TypeParam(${ Expr(paramName) }, ${ Expr(pr.paramNum) }), 0) }

      // Fallback for other types
      case other =>
        val sym  = other.typeSymbol
        val name = if (sym.isNoSymbol) "Unknown" else sym.name
        '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.nominal[Nothing](${ Expr(name) }, Owner.Root, Nil)) }
    }
  }

  private def buildConstantTypeRepr(using Quotes)(const: quotes.reflect.Constant): Expr[zio.blocks.typeid.TypeRepr] =

    const.value match {
      case i: Int     => '{ zio.blocks.typeid.TypeRepr.Constant.IntConst(${ Expr(i) }) }
      case l: Long    => '{ zio.blocks.typeid.TypeRepr.Constant.LongConst(${ Expr(l) }) }
      case f: Float   => '{ zio.blocks.typeid.TypeRepr.Constant.FloatConst(${ Expr(f) }) }
      case d: Double  => '{ zio.blocks.typeid.TypeRepr.Constant.DoubleConst(${ Expr(d) }) }
      case b: Boolean => '{ zio.blocks.typeid.TypeRepr.Constant.BooleanConst(${ Expr(b) }) }
      case c: Char    => '{ zio.blocks.typeid.TypeRepr.Constant.CharConst(${ Expr(c) }) }
      case s: String  => '{ zio.blocks.typeid.TypeRepr.Constant.StringConst(${ Expr(s) }) }
      case null       => '{ zio.blocks.typeid.TypeRepr.Constant.NullConst }
      case ()         => '{ zio.blocks.typeid.TypeRepr.Constant.UnitConst }
      case _          => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.nominal[Nothing]("Constant", Owner.Root, Nil)) }
    }

  private def buildTypeRefRepr(using Quotes)(tref: quotes.reflect.TypeRef): Expr[zio.blocks.typeid.TypeRepr] = {

    val sym  = tref.typeSymbol
    val name = sym.name

    // Check for common predefined types
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
      case "Any"     => return '{ zio.blocks.typeid.TypeRepr.AnyType }
      case "Nothing" => return '{ zio.blocks.typeid.TypeRepr.NothingType }
      case "Null"    => return '{ zio.blocks.typeid.TypeRepr.NullType }
      case _         =>
        // Create a nominal type reference
        val ownerExpr = buildOwner(sym.owner)
        '{ TypeId.nominal[Nothing](${ Expr(name) }, $ownerExpr, Nil) }
    }
    '{ zio.blocks.typeid.TypeRepr.Ref($typeIdExpr) }
  }

  private def buildTermPath(using Quotes)(tref: quotes.reflect.TermRef): Expr[TermPath] = {
    import quotes.reflect.*

    def loop(t: TypeRepr, acc: List[Expr[TermPath.Segment]]): List[Expr[TermPath.Segment]] = t match {
      case tr: TermRef =>
        val segment = '{ TermPath.Term(${ Expr(tr.termSymbol.name) }) }
        tr.qualifier match {
          case NoPrefix() => segment :: acc
          case q          => loop(q, segment :: acc)
        }
      case tr: TypeRef if tr.typeSymbol.isPackageDef =>
        '{ TermPath.Package(${ Expr(tr.typeSymbol.name) }) } :: acc
      case _ => acc
    }

    val segments = loop(tref, Nil)
    '{ TermPath(${ Expr.ofList(segments) }) }
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
    import quotes.reflect.*

    val params = sym.typeMembers.filter(_.isTypeParam).zipWithIndex.map { case (p, idx) =>
      val varianceExpr = if (p.flags.is(Flags.Covariant)) {
        '{ Variance.Covariant }
      } else if (p.flags.is(Flags.Contravariant)) {
        '{ Variance.Contravariant }
      } else {
        '{ Variance.Invariant }
      }

      '{ TypeParam(${ Expr(p.name) }, ${ Expr(idx) }, $varianceExpr) }
    }

    Expr.ofList(params)
  }
}
