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
    val defKindExpr    = buildDefKind(typeSymbol)

    // Get the aliased type (with args applied)
    val aliasedType = tr.translucentSuperType.dealias
    val aliasedExpr = buildTypeReprFromTypeRepr(aliasedType, Set.empty[String])

    '{
      TypeId.alias[A](
        ${ Expr(name) },
        ${ ownerExpr },
        ${ typeParamsExpr },
        ${ aliasedExpr },
        Nil // annotations
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
    val typeReprs = types.map(t => buildTypeReprFromTypeRepr(t, Set.empty[String]))

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
    val typeReprs = types.map(t => buildTypeReprFromTypeRepr(t, Set.empty[String]))

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

    val currentTpe = TypeRepr.of[A]
    currentTpe match {
      // Opaque types must be derived directly - implicit search finds the underlying type's TypeId due to subtyping
      case tr: TypeRef if tr.typeSymbol.flags.is(Flags.Opaque) =>
        deriveNew[A]
      case tr: TypeRef if tr.typeSymbol.isAliasType =>
        if (isUserDefinedAlias(tr)) {
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

    val aliasedType = tr.translucentSuperType.dealias
    val aliasedExpr = buildTypeReprFromTypeRepr(aliasedType, Set(typeSymbol.fullName))

    '{
      TypeId.alias[A](
        ${ Expr(name) },
        ${ ownerExpr },
        ${ typeParamsExpr },
        ${ aliasedExpr },
        Nil // annotations
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
    val termSymbol = tpe.termSymbol

    // Check if this is an enum case value (like TrafficLight.Red)
    // For enum values, termSymbol has the Enum flag and contains the case name
    val isEnumValue = !termSymbol.isNoSymbol && termSymbol.flags.is(Flags.Enum)

    // Extract the simple name
    val (name, ownerSymbol) = if (isEnumValue) {
      // For enum cases, use the term symbol's name (e.g., "Red")
      // The owner is the parent enum (e.g., TrafficLight)
      (termSymbol.name, termSymbol.owner)
    } else {
      // For regular types, use the type symbol's name
      val rawName = typeSymbol.name
      val nm      = if (typeSymbol.flags.is(Flags.Module)) rawName.stripSuffix("$") else rawName
      (nm, typeSymbol.owner)
    }

    val ownerExpr = buildOwner(ownerSymbol)

    val typeParamsExpr = buildTypeParams(typeSymbol)

    val flags = typeSymbol.flags

    val defKindExpr =
      if (isEnumValue) buildEnumCaseDefKindFromTermSymbol(termSymbol)
      else buildDefKind(typeSymbol)

    if (flags.is(Flags.Opaque)) {
      val reprExpr     = extractOpaqueRepresentation(tpe, typeSymbol)
      val publicBounds = defKindExpr match {
        case '{ TypeDefKind.OpaqueType($bounds) } => bounds
        case _                                    => '{ zio.blocks.typeid.TypeBounds.Unbounded }
      }
      '{
        TypeId.opaque[A](
          ${ Expr(name) },
          ${ ownerExpr },
          ${ typeParamsExpr },
          ${ reprExpr },
          ${ publicBounds },
          Nil // annotations
        )
      }
    } else {
      val parentsExpr = buildBaseTypes(typeSymbol)
      '{
        TypeId.nominal[A](
          ${ Expr(name) },
          ${ ownerExpr },
          ${ typeParamsExpr },
          ${ defKindExpr },
          ${ parentsExpr },
          None, // selfType
          Nil   // annotations
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
        buildTypeReprFromTypeRepr(underlying, Set.empty[String])
      case _ =>
        // Fallback - try dealias
        val underlying = tpe.dealias
        if (underlying != tpe) {
          buildTypeReprFromTypeRepr(underlying, Set.empty[String])
        } else {
          '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.string) }
        }
    }
  }

  private def buildTypeReprFromTypeRepr(using
    Quotes
  )(
    tpe: quotes.reflect.TypeRepr,
    visitingAliases: Set[String] = Set.empty
  ): Expr[zio.blocks.typeid.TypeRepr] = {
    import quotes.reflect.*

    val sym         = tpe.typeSymbol
    val symFullName = if (sym.isNoSymbol) "" else sym.fullName
    val isAlias     = !sym.isNoSymbol && sym.isAliasType

    if (isAlias && visitingAliases.contains(symFullName)) {
      val name      = sym.name
      val ownerExpr = buildOwner(sym.owner)
      return '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.nominal[Nothing](${ Expr(name) }, $ownerExpr, Nil)) }
    }

    val newVisiting = if (isAlias) visitingAliases + symFullName else visitingAliases

    tpe match {
      case OrType(_, _) =>
        val types     = flattenUnion(tpe)
        val typeReprs = types.map(t => buildTypeReprFromTypeRepr(t, newVisiting))
        '{ zio.blocks.typeid.TypeRepr.Union(${ Expr.ofList(typeReprs) }) }

      case AndType(_, _) =>
        val types     = flattenIntersection(tpe)
        val typeReprs = types.map(t => buildTypeReprFromTypeRepr(t, newVisiting))
        '{ zio.blocks.typeid.TypeRepr.Intersection(${ Expr.ofList(typeReprs) }) }

      case Refinement(_, _, _) =>
        buildRefinementType(tpe, newVisiting)

      case AppliedType(tycon, args) =>
        val tyconName = tycon.typeSymbol.fullName

        if (isTupleType(tyconName)) {
          buildTupleTypeRepr(args, newVisiting)
        } else if (isFunctionType(tyconName)) {
          val paramTypes = args.init.map(t => buildTypeReprFromTypeRepr(t, newVisiting))
          val resultType = buildTypeReprFromTypeRepr(args.last, newVisiting)
          '{ zio.blocks.typeid.TypeRepr.Function(${ Expr.ofList(paramTypes) }, $resultType) }
        } else if (isContextFunctionType(tyconName)) {
          val paramTypes = args.init.map(t => buildTypeReprFromTypeRepr(t, newVisiting))
          val resultType = buildTypeReprFromTypeRepr(args.last, newVisiting)
          '{ zio.blocks.typeid.TypeRepr.ContextFunction(${ Expr.ofList(paramTypes) }, $resultType) }
        } else {
          val tyconRepr = buildTypeReprFromTypeRepr(tycon, newVisiting)
          val argsRepr  = args.map(t => buildTypeReprFromTypeRepr(t, newVisiting))
          '{ zio.blocks.typeid.TypeRepr.Applied($tyconRepr, ${ Expr.ofList(argsRepr) }) }
        }

      case ConstantType(const) =>
        buildConstantTypeRepr(const)

      case tref: TypeRef =>
        buildTypeRefRepr(tref)

      case tref: TermRef =>
        val path = buildTermPath(tref)
        '{ zio.blocks.typeid.TypeRepr.Singleton($path) }

      case ThisType(tref) =>
        val ownerExpr = buildOwner(tref.typeSymbol)
        '{ zio.blocks.typeid.TypeRepr.ThisType($ownerExpr) }

      case ByNameType(underlying) =>
        val underlyingRepr = buildTypeReprFromTypeRepr(underlying, newVisiting)
        '{ zio.blocks.typeid.TypeRepr.ByName($underlyingRepr) }

      case AnnotatedType(underlying, _) =>
        buildTypeReprFromTypeRepr(underlying, newVisiting)

      case bounds: TypeBounds =>
        val lowerExpr: Expr[Option[zio.blocks.typeid.TypeRepr]] = bounds.low match {
          case nt if nt =:= TypeRepr.of[Nothing] => '{ None }
          case other                             =>
            val otherRepr = buildTypeReprFromTypeRepr(other, newVisiting)
            '{ Some($otherRepr) }
        }
        val upperExpr: Expr[Option[zio.blocks.typeid.TypeRepr]] = bounds.hi match {
          case at if at =:= TypeRepr.of[Any] => '{ None }
          case other                         =>
            val otherRepr = buildTypeReprFromTypeRepr(other, newVisiting)
            '{ Some($otherRepr) }
        }
        '{ zio.blocks.typeid.TypeRepr.Wildcard(zio.blocks.typeid.TypeBounds($lowerExpr, $upperExpr)) }

      case tl: TypeLambda =>
        val params = tl.paramNames.zipWithIndex.map { case (name, idx) =>
          '{ TypeParam(${ Expr(name) }, ${ Expr(idx) }) }
        }
        val bodyRepr = buildTypeReprFromTypeRepr(tl.resType, newVisiting)
        '{ zio.blocks.typeid.TypeRepr.TypeLambda(${ Expr.ofList(params) }, $bodyRepr) }

      // Handle param refs within type lambdas
      case pr: ParamRef =>
        val paramName = pr.binder match {
          case tl: TypeLambda => tl.paramNames(pr.paramNum)
          case _              => s"T${pr.paramNum}"
        }
        '{ zio.blocks.typeid.TypeRepr.ParamRef(TypeParam(${ Expr(paramName) }, ${ Expr(pr.paramNum) }), 0) }

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
    import quotes.reflect.*

    val sym     = tref.typeSymbol
    val rawName = sym.name
    val name    = if (sym.flags.is(Flags.Module)) rawName.stripSuffix("$") else rawName

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

  // ============================================================================
  // Type Detection Helpers
  // ============================================================================

  private def isTupleType(fullName: String): Boolean =
    fullName.startsWith("scala.Tuple") || fullName == "scala.EmptyTuple" ||
      fullName.startsWith("scala.*:")

  private def isFunctionType(fullName: String): Boolean =
    fullName.startsWith("scala.Function") && !fullName.contains("ContextFunction")

  private def isContextFunctionType(fullName: String): Boolean =
    fullName.startsWith("scala.ContextFunction")

  // ============================================================================
  // Tuple Type Building
  // ============================================================================

  private def buildTupleTypeRepr(using
    Quotes
  )(
    args: List[quotes.reflect.TypeRepr],
    visiting: Set[String]
  ): Expr[zio.blocks.typeid.TypeRepr] = {
    // For now, treat all tuple elements as unlabeled
    // Named tuple support would require additional detection
    val elements = args.map { arg =>
      val tpeRepr = buildTypeReprFromTypeRepr(arg, visiting)
      '{ TupleElement(None, $tpeRepr) }
    }
    '{ zio.blocks.typeid.TypeRepr.Tuple(${ Expr.ofList(elements) }) }
  }

  // ============================================================================
  // Refinement/Structural Type Building
  // ============================================================================

  private def buildRefinementType(using
    Quotes
  )(
    tpe: quotes.reflect.TypeRepr,
    visiting: Set[String]
  ): Expr[zio.blocks.typeid.TypeRepr] = {
    import quotes.reflect.*

    // Collect all refinement members
    def collectRefinements(
      t: TypeRepr,
      members: List[(String, TypeRepr, Boolean)]
    ): (TypeRepr, List[(String, TypeRepr, Boolean)]) =
      t match {
        case Refinement(parent, name, info) =>
          val isMethod = info match {
            case _: MethodType => true
            case _             => false
          }
          val memberType = info match {
            case mt: MethodType   => mt.resType
            case TypeBounds(_, _) => t // Abstract type member
            case other            => other
          }
          collectRefinements(parent, (name, memberType, isMethod) :: members)
        case other =>
          (other, members)
      }

    val (parent, refinements) = collectRefinements(tpe, Nil)

    // Build parent types list
    val parentTypes = parent match {
      case AndType(_, _) => flattenIntersection(parent)
      case other         => List(other)
    }
    val parentReprs = parentTypes.map(t => buildTypeReprFromTypeRepr(t, visiting))

    // Build members list
    val memberExprs = refinements.map { case (name, memberType, isMethod) =>
      val typeRepr = buildTypeReprFromTypeRepr(memberType, visiting)
      if (isMethod) {
        // Def member (simplified - no parameters for now)
        '{ Member.Def(${ Expr(name) }, Nil, Nil, $typeRepr) }
      } else {
        // Val member
        '{ Member.Val(${ Expr(name) }, $typeRepr) }
      }
    }

    '{ zio.blocks.typeid.TypeRepr.Structural(${ Expr.ofList(parentReprs) }, ${ Expr.ofList(memberExprs) }) }
  }

  // ============================================================================
  // TypeDefKind Extraction
  // ============================================================================

  private def buildDefKind(using
    Quotes
  )(
    sym: quotes.reflect.Symbol
  ): Expr[TypeDefKind] = {
    import quotes.reflect.*

    val flags = sym.flags

    // Check for enum first (Scala 3 specific)
    if (flags.is(Flags.Enum) && !flags.is(Flags.Case)) {
      // This is an enum definition - extract cases
      buildEnumDefKind(sym)
    } else if (flags.is(Flags.Enum) && flags.is(Flags.Case)) {
      // This is an enum case
      buildEnumCaseDefKind(sym)
    } else if (flags.is(Flags.Module)) {
      // Singleton object
      buildObjectDefKind(sym)
    } else if (flags.is(Flags.Trait)) {
      // Trait
      buildTraitDefKind(sym)
    } else if (sym.isClassDef) {
      // Class (regular, case, abstract, final, value)
      buildClassDefKind(sym, flags)
    } else if (flags.is(Flags.Opaque)) {
      // Opaque type
      '{ TypeDefKind.OpaqueType() }
    } else if (sym.isAliasType) {
      // Type alias
      '{ TypeDefKind.TypeAlias }
    } else if (sym.isAbstractType) {
      // Abstract type member
      '{ TypeDefKind.AbstractType }
    } else {
      '{ TypeDefKind.Unknown }
    }
  }

  private def buildBaseTypes(using
    Quotes
  )(
    sym: quotes.reflect.Symbol
  ): Expr[List[TypeRepr]] = {

    // Get the base classes excluding the type itself and common types like Any, AnyRef, Object
    val baseClasses = sym.typeRef.baseClasses.filterNot { base =>
      base == sym ||
      base.fullName == "scala.Any" ||
      base.fullName == "scala.AnyRef" ||
      base.fullName == "java.lang.Object" ||
      base.fullName == "scala.Matchable"
    }

    val baseExprs = baseClasses.map { base =>
      buildTypeReprFromTypeRepr(base.typeRef, Set.empty[String])
    }

    Expr.ofList(baseExprs)
  }

  private def buildObjectDefKind(using
    Quotes
  )(
    sym: quotes.reflect.Symbol
  ): Expr[TypeDefKind] = {
    val basesExpr = buildBaseTypes(sym)
    '{ TypeDefKind.Object(bases = $basesExpr) }
  }

  private def buildClassDefKind(using
    Quotes
  )(
    sym: quotes.reflect.Symbol,
    flags: quotes.reflect.Flags
  ): Expr[TypeDefKind] = {
    import quotes.reflect.*

    val isFinal    = flags.is(Flags.Final)
    val isAbstract = flags.is(Flags.Abstract)
    val isCase     = flags.is(Flags.Case)

    // Check if it's a value class (extends AnyVal)
    val isValue = sym.typeRef.baseClasses.exists(_.fullName == "scala.AnyVal")

    val basesExpr = buildBaseTypes(sym)

    '{
      TypeDefKind.Class(
        isFinal = ${ Expr(isFinal) },
        isAbstract = ${ Expr(isAbstract) },
        isCase = ${ Expr(isCase) },
        isValue = ${ Expr(isValue) },
        bases = $basesExpr
      )
    }
  }

  private def buildTraitDefKind(using
    Quotes
  )(
    sym: quotes.reflect.Symbol
  ): Expr[TypeDefKind] = {
    import quotes.reflect.*

    val flags    = sym.flags
    val isSealed = flags.is(Flags.Sealed)

    val basesExpr = buildBaseTypes(sym)

    if (isSealed) {
      // Get known subtypes for sealed traits
      val children     = sym.children
      val subtypeExprs = children.map { child =>
        // Build a TypeRepr for each child
        val childTypeRepr = child.typeRef
        buildTypeReprFromTypeRepr(childTypeRepr, Set.empty[String])
      }
      '{ TypeDefKind.Trait(isSealed = true, knownSubtypes = ${ Expr.ofList(subtypeExprs) }, bases = $basesExpr) }
    } else {
      '{ TypeDefKind.Trait(isSealed = false, bases = $basesExpr) }
    }
  }

  private def buildEnumDefKind(using
    Quotes
  )(
    sym: quotes.reflect.Symbol
  ): Expr[TypeDefKind] = {
    import quotes.reflect.*

    // Get enum cases from children
    val children  = sym.children
    val caseExprs = children.zipWithIndex.collect {
      case (child, idx) if child.flags.is(Flags.Case) =>
        buildEnumCaseInfo(child, idx)
    }

    val basesExpr = buildBaseTypes(sym)

    '{ TypeDefKind.Enum(cases = ${ Expr.ofList(caseExprs) }, bases = $basesExpr) }
  }

  private def buildEnumCaseInfo(using
    Quotes
  )(
    caseSym: quotes.reflect.Symbol,
    ordinal: Int
  ): Expr[EnumCaseInfo] = {
    import quotes.reflect.*

    val name = caseSym.name

    // Check if this is an object case (no parameters) or a class case (with parameters)
    val isObjectCase = caseSym.flags.is(Flags.Module) ||
      caseSym.primaryConstructor.paramSymss.flatten.isEmpty

    if (isObjectCase) {
      '{ EnumCaseInfo(${ Expr(name) }, ${ Expr(ordinal) }, Nil, isObjectCase = true) }
    } else {
      // Extract constructor parameters
      val params = caseSym.primaryConstructor.paramSymss.flatten.filter(_.isTerm).map { param =>
        val paramName     = param.name
        val paramType     = param.termRef.widenTermRefByName
        val paramTypeRepr = buildTypeReprFromTypeRepr(paramType, Set.empty[String])
        '{ EnumCaseParam(${ Expr(paramName) }, $paramTypeRepr) }
      }
      '{ EnumCaseInfo(${ Expr(name) }, ${ Expr(ordinal) }, ${ Expr.ofList(params) }, isObjectCase = false) }
    }
  }

  private def buildEnumCaseDefKind(using
    Quotes
  )(
    caseSym: quotes.reflect.Symbol
  ): Expr[TypeDefKind] = {
    import quotes.reflect.*

    // Find parent enum
    val parentSym = caseSym.owner

    // Get ordinal by finding position in parent's children
    val siblings = parentSym.children.filter(_.flags.is(Flags.Case))
    val ordinal  = siblings.indexOf(caseSym)

    // Check if object case
    val isObjectCase = caseSym.flags.is(Flags.Module) ||
      caseSym.primaryConstructor.paramSymss.flatten.isEmpty

    // Build parent enum TypeRepr
    val parentTypeRepr = buildTypeReprFromTypeRepr(parentSym.typeRef, Set.empty[String])

    '{ TypeDefKind.EnumCase($parentTypeRepr, ${ Expr(ordinal) }, ${ Expr(isObjectCase) }) }
  }

  private def buildEnumCaseDefKindFromTermSymbol(using
    Quotes
  )(
    termSym: quotes.reflect.Symbol
  ): Expr[TypeDefKind] = {
    import quotes.reflect.*

    val parentSym = termSym.owner

    val siblings = parentSym.children.filter(_.flags.is(Flags.Case))
    val ordinal  = siblings.indexOf(termSym)

    val isObjectCase = termSym.flags.is(Flags.Module) ||
      termSym.primaryConstructor.paramSymss.flatten.isEmpty

    val parentTypeRepr = buildTypeReprFromTypeRepr(parentSym.typeRef, Set.empty[String])

    '{ TypeDefKind.EnumCase($parentTypeRepr, ${ Expr(ordinal) }, ${ Expr(isObjectCase) }) }
  }
}
