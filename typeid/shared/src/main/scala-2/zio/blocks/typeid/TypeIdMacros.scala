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

  def ofImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] = {
    import c.universe._

    val tpe = weakTypeOf[A]

    tpe match {
      case TypeRef(_, sym, args) if args.nonEmpty =>
        if (sym.isType && sym.asType.isAliasType && isUserDefinedAlias(c)(sym))
          deriveAppliedTypeAlias[A](c)(sym, args)
        else
          deriveAppliedTypeNew[A](c)(sym, args)
      case TypeRef(_, sym, _) if sym.isType && sym.asType.isAliasType =>
        deriveTypeId[A](c)
      case _ =>
        val fullName = tpe.typeSymbol.fullName
        fullName match {
          case "scala.Int" =>
            c.Expr[TypeId[A]](
              q"_root_.zio.blocks.typeid.TypeId.int.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$tpe]]"
            )
          case "scala.Long" =>
            c.Expr[TypeId[A]](
              q"_root_.zio.blocks.typeid.TypeId.long.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$tpe]]"
            )
          case "scala.Double" =>
            c.Expr[TypeId[A]](
              q"_root_.zio.blocks.typeid.TypeId.double.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$tpe]]"
            )
          case "scala.Float" =>
            c.Expr[TypeId[A]](
              q"_root_.zio.blocks.typeid.TypeId.float.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$tpe]]"
            )
          case "scala.Boolean" =>
            c.Expr[TypeId[A]](
              q"_root_.zio.blocks.typeid.TypeId.boolean.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$tpe]]"
            )
          case "scala.Byte" =>
            c.Expr[TypeId[A]](
              q"_root_.zio.blocks.typeid.TypeId.byte.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$tpe]]"
            )
          case "scala.Short" =>
            c.Expr[TypeId[A]](
              q"_root_.zio.blocks.typeid.TypeId.short.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$tpe]]"
            )
          case "scala.Char" =>
            c.Expr[TypeId[A]](
              q"_root_.zio.blocks.typeid.TypeId.char.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$tpe]]"
            )
          case "scala.Unit" =>
            c.Expr[TypeId[A]](
              q"_root_.zio.blocks.typeid.TypeId.unit.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$tpe]]"
            )
          case _ =>
            deriveTypeId[A](c)
        }
    }
  }

  def derivedImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] =
    deriveTypeId[A](c)

  private def deriveTypeId[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] = {
    import c.universe._

    val tpe = weakTypeOf[A]

    tpe match {
      case TypeRef(_, sym, args) =>
        if (sym.isType && sym.asType.isAliasType) {
          if (isUserDefinedAlias(c)(sym)) {
            deriveNew[A](c)
          } else {
            val dealiased = tpe.dealias
            getPredefinedTypeId[A](c)(dealiased).getOrElse(deriveNew[A](c))
          }
        } else if (args.nonEmpty) {
          deriveAppliedTypeNew[A](c)(sym, args)
        } else {
          deriveNew[A](c)
        }
      case _ =>
        deriveNew[A](c)
    }
  }

  private def getPredefinedTypeId[A: c.WeakTypeTag](
    c: blackbox.Context
  )(dealiased: c.Type): Option[c.Expr[TypeId[A]]] = {
    import c.universe._
    val fullName = dealiased.typeSymbol.fullName
    fullName match {
      case "java.lang.String" =>
        Some(
          c.Expr[TypeId[A]](
            q"_root_.zio.blocks.typeid.TypeId.string.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$dealiased]]"
          )
        )
      case "scala.math.BigInt" | "scala.BigInt" =>
        Some(
          c.Expr[TypeId[A]](
            q"_root_.zio.blocks.typeid.TypeId.bigInt.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$dealiased]]"
          )
        )
      case "scala.math.BigDecimal" | "scala.BigDecimal" =>
        Some(
          c.Expr[TypeId[A]](
            q"_root_.zio.blocks.typeid.TypeId.bigDecimal.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$dealiased]]"
          )
        )
      case _ => None
    }
  }

  private def deriveAppliedTypeNew[A: c.WeakTypeTag](
    c: blackbox.Context
  )(sym: c.Symbol, args: List[c.Type]): c.Expr[TypeId[A]] = {
    import c.universe._

    val tpe          = weakTypeOf[A]
    val typeParams   = if (sym.isType) sym.asType.typeParams else Nil
    val typeArgsExpr = args.map(buildTypeReprFromType(c)(_))

    // Check for known predefined type constructors by name (including Predef aliases)
    val symFullName     = sym.fullName
    val dealiasFullName = if (sym.isType && sym.asType.isAliasType) {
      tpe.dealias.typeSymbol.fullName
    } else symFullName

    val predefinedBase: Option[c.Tree] = dealiasFullName match {
      case "scala.collection.immutable.List"       => Some(q"_root_.zio.blocks.typeid.TypeId.list")
      case "scala.collection.immutable.Vector"     => Some(q"_root_.zio.blocks.typeid.TypeId.vector")
      case "scala.collection.immutable.Set"        => Some(q"_root_.zio.blocks.typeid.TypeId.set")
      case "scala.collection.immutable.Seq"        => Some(q"_root_.zio.blocks.typeid.TypeId.seq")
      case "scala.collection.immutable.IndexedSeq" => Some(q"_root_.zio.blocks.typeid.TypeId.indexedSeq")
      case "scala.collection.immutable.Map"        => Some(q"_root_.zio.blocks.typeid.TypeId.map")
      case "scala.Option"                          => Some(q"_root_.zio.blocks.typeid.TypeId.option")
      case "scala.Some"                            => Some(q"_root_.zio.blocks.typeid.TypeId.some")
      case "scala.util.Either"                     => Some(q"_root_.zio.blocks.typeid.TypeId.either")
      case "scala.Array"                           => Some(q"_root_.zio.blocks.typeid.TypeId.array")
      case "scala.collection.immutable.ArraySeq"   => Some(q"_root_.zio.blocks.typeid.TypeId.arraySeq")
      case "zio.blocks.chunk.Chunk"                => Some(q"_root_.zio.blocks.typeid.TypeId.chunk")
      case _                                       => None
    }

    predefinedBase match {
      case Some(baseTree) =>
        c.Expr[TypeId[A]](
          q"""
            val base = $baseTree.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$tpe]]
            _root_.zio.blocks.typeid.TypeId.nominal[$tpe](
              base.name,
              base.owner,
              base.typeParams,
              _root_.scala.List(..$typeArgsExpr),
              base.defKind,
              base.selfType,
              base.annotations
            )
          """
        )
      case None =>
        if (typeParams.nonEmpty) {
          val wildcardArgs    = typeParams.map(_ => typeOf[Any])
          val existentialType = appliedType(sym.asType.toType.typeConstructor, wildcardArgs)
          val typeIdType      = appliedType(typeOf[TypeId[_]].typeConstructor, existentialType)
          val implicitSearch  = c.inferImplicitValue(typeIdType, silent = true)

          if (implicitSearch != EmptyTree) {
            val isDerived = implicitSearch.symbol.fullName.contains("TypeIdMacros") ||
              implicitSearch.symbol.fullName.contains("derived")
            if (isDerived) {
              deriveAppliedTypeFresh[A](c)(sym, args)
            } else {
              c.Expr[TypeId[A]](
                q"""
                  val base = $implicitSearch.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$tpe]]
                  _root_.zio.blocks.typeid.TypeId.nominal[$tpe](
                    base.name,
                    base.owner,
                    base.typeParams,
                    _root_.scala.List(..$typeArgsExpr),
                    base.defKind,
                    base.selfType,
                    base.annotations
                  )
                """
              )
            }
          } else {
            deriveAppliedTypeFresh[A](c)(sym, args)
          }
        } else {
          deriveAppliedTypeFresh[A](c)(sym, args)
        }
    }
  }

  private def deriveAppliedTypeFresh[A: c.WeakTypeTag](
    c: blackbox.Context
  )(sym: c.Symbol, args: List[c.Type]): c.Expr[TypeId[A]] = {
    import c.universe._

    val tpe             = weakTypeOf[A]
    val name            = sym.name.decodedName.toString
    val ownerExpr       = buildOwner(c)(sym.owner)
    val typeParamsExpr  = buildTypeParams(c)(sym)
    val typeArgsExpr    = args.map(buildTypeReprFromType(c)(_))
    val defKindExpr     = buildDefKind(c)(sym)
    val annotationsExpr = buildAnnotations(c)(sym)

    c.Expr[TypeId[A]](
      q"""
        _root_.zio.blocks.typeid.TypeId.nominal[$tpe](
          $name,
          $ownerExpr,
          $typeParamsExpr,
          _root_.scala.List(..$typeArgsExpr),
          $defKindExpr,
          _root_.scala.None,
          $annotationsExpr
        )
      """
    )
  }

  private def deriveAppliedTypeAlias[A: c.WeakTypeTag](
    c: blackbox.Context
  )(sym: c.Symbol, args: List[c.Type]): c.Expr[TypeId[A]] = {
    import c.universe._

    val tpe             = weakTypeOf[A]
    val name            = sym.name.decodedName.toString
    val ownerExpr       = buildOwner(c)(sym.owner)
    val typeParamsExpr  = buildTypeParams(c)(sym)
    val typeArgsExpr    = args.map(buildTypeReprFromType(c)(_))
    val annotationsExpr = buildAnnotations(c)(sym)

    val aliasedType = tpe.dealias
    val aliasedExpr = buildTypeReprFromType(c)(aliasedType)

    c.Expr[TypeId[A]](
      q"""
        _root_.zio.blocks.typeid.TypeId.alias[$tpe](
          $name,
          $ownerExpr,
          $typeParamsExpr,
          $aliasedExpr,
          _root_.scala.List(..$typeArgsExpr),
          $annotationsExpr
        )
      """
    )
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

    tpe match {
      case tr @ TypeRef(pre, sym, _) if sym.isType && sym.asType.isAliasType =>
        val aliasName       = sym.name.decodedName.toString
        val ownerExpr       = resolveOwnerExprFromTypeRef(c)(tr, pre, sym.owner)
        val typeParamsExpr  = buildTypeParams(c)(sym)
        val annotationsExpr = buildAnnotations(c)(sym)

        val aliasedType = tpe.dealias
        val aliasedExpr = buildTypeReprFromType(c)(aliasedType)

        c.Expr[TypeId[A]](
          q"""
            _root_.zio.blocks.typeid.TypeId.alias[$tpe](
              $aliasName,
              $ownerExpr,
              $typeParamsExpr,
              $aliasedExpr,
              _root_.scala.Nil,
              $annotationsExpr
            )
          """
        )
      case tr @ TypeRef(pre, _, _) =>
        val typeSymbol      = tpe.typeSymbol
        val name            = typeSymbol.name.decodedName.toString
        val ownerExpr       = resolveOwnerExprFromTypeRef(c)(tr, pre, typeSymbol.owner)
        val typeParamsExpr  = buildTypeParams(c)(typeSymbol)
        val defKindExpr     = buildDefKind(c)(typeSymbol)
        val annotationsExpr = buildAnnotations(c)(typeSymbol)
        val selfTypeExpr    = extractSelfType(c)(typeSymbol)

        c.Expr[TypeId[A]](
          q"""
            _root_.zio.blocks.typeid.TypeId.nominal[$tpe](
              $name,
              $ownerExpr,
              $typeParamsExpr,
              _root_.scala.Nil,
              $defKindExpr,
              $selfTypeExpr,
              $annotationsExpr
            )
          """
        )
      case _ =>
        val typeSymbol      = tpe.typeSymbol
        val name            = typeSymbol.name.decodedName.toString
        val ownerExpr       = buildOwner(c)(typeSymbol.owner)
        val typeParamsExpr  = buildTypeParams(c)(typeSymbol)
        val defKindExpr     = buildDefKind(c)(typeSymbol)
        val annotationsExpr = buildAnnotations(c)(typeSymbol)
        val selfTypeExpr    = extractSelfType(c)(typeSymbol)

        c.Expr[TypeId[A]](
          q"""
            _root_.zio.blocks.typeid.TypeId.nominal[$tpe](
              $name,
              $ownerExpr,
              $typeParamsExpr,
              _root_.scala.Nil,
              $defKindExpr,
              $selfTypeExpr,
              $annotationsExpr
            )
          """
        )
    }
  }

  private def buildTypeReprFromType(c: blackbox.Context)(tpe: c.Type): c.Tree = {
    import c.universe._

    tpe match {
      case RefinedType(parents, decls) =>
        // Structural/refinement type (e.g., { def name: String; def age: Int })
        val structuralMembers = decls.toList.collect {
          case m: MethodSymbol if m.paramLists.flatten.isEmpty && !m.isConstructor && m.isPublic =>
            val memberName   = m.name.decodedName.toString
            val memberType   = m.returnType.asSeenFrom(tpe, tpe.typeSymbol)
            val memberRepr   = buildTypeReprFromType(c)(memberType)
            val hasParamList = m.paramLists.nonEmpty
            if (hasParamList) {
              // Parameterless def (e.g., def foo: Int)
              q"_root_.zio.blocks.typeid.Member.Def($memberName, _root_.scala.Nil, _root_.scala.Nil, $memberRepr)"
            } else {
              // Val-like member
              q"_root_.zio.blocks.typeid.Member.Val($memberName, $memberRepr)"
            }
        }

        // Build parent type reprs, filtering out AnyRef/Any/Object
        val meaningfulParents = parents.filterNot { p =>
          val fullName = p.typeSymbol.fullName
          fullName == "scala.Any" || fullName == "scala.AnyRef" || fullName == "java.lang.Object"
        }
        val parentReprs = if (meaningfulParents.nonEmpty) {
          meaningfulParents.map(buildTypeReprFromType(c)(_))
        } else {
          val anyRefName = "AnyRef"
          val scalaPkg   = "scala"
          List(
            q"""_root_.zio.blocks.typeid.TypeRepr.Ref(
              _root_.zio.blocks.typeid.TypeId.nominal[_root_.scala.AnyRef](
                $anyRefName,
                _root_.zio.blocks.typeid.Owner(_root_.scala.List(_root_.zio.blocks.typeid.Owner.Package($scalaPkg))),
                _root_.scala.Nil
              )
            )"""
          )
        }

        q"_root_.zio.blocks.typeid.TypeRepr.Structural(_root_.scala.List(..$parentReprs), _root_.scala.List(..$structuralMembers))"

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
      case "Any"     => return q"_root_.zio.blocks.typeid.TypeRepr.AnyType"
      case "Nothing" => return q"_root_.zio.blocks.typeid.TypeRepr.NothingType"
      case "Null"    => return q"_root_.zio.blocks.typeid.TypeRepr.NullType"
      case _         =>
        def createFreshTypeId(): c.Tree = {
          val ownerExpr = buildOwner(c)(sym.owner)
          if (sym.isType && sym.asType.isAliasType) {
            val aliasedType = sym.asType.toType.dealias
            val aliasedExpr = buildTypeReprFromType(c)(aliasedType)
            q"_root_.zio.blocks.typeid.TypeId.alias[_root_.scala.Nothing]($name, $ownerExpr, _root_.scala.Nil, $aliasedExpr, _root_.scala.Nil, _root_.scala.Nil)"
          } else {
            val typeParamsExpr = if (sym.isType) buildTypeParams(c)(sym) else q"_root_.scala.Nil"
            val defKindExpr    = buildDefKindShallow(c)(sym)
            q"_root_.zio.blocks.typeid.TypeId.nominal[_root_.scala.Nothing]($name, $ownerExpr, $typeParamsExpr, _root_.scala.Nil, $defKindExpr)"
          }
        }

        val symType        = if (sym.isType) sym.asType.toType else sym.typeSignature
        val typeIdType     = appliedType(typeOf[TypeId[_]].typeConstructor, symType)
        val implicitSearch = c.inferImplicitValue(typeIdType, silent = true)

        if (implicitSearch != EmptyTree) {
          val isDerived = implicitSearch.symbol.fullName.contains("TypeIdMacros") ||
            implicitSearch.symbol.fullName.contains("derived")
          if (isDerived) createFreshTypeId()
          else implicitSearch
        } else {
          createFreshTypeId()
        }
    }
    q"_root_.zio.blocks.typeid.TypeRepr.Ref($typeIdExpr)"
  }

  private val zioPreludeNewtypeBases = Set(
    "zio.prelude.NewtypeCustom",
    "zio.prelude.SubtypeCustom",
    "zio.prelude.Newtype",
    "zio.prelude.Subtype",
    "zio.prelude.NewtypeVersionSpecific"
  )

  private def isZioPreludeNewtypeBase(c: blackbox.Context)(sym: c.Symbol): Boolean =
    sym != c.universe.NoSymbol && zioPreludeNewtypeBases.contains(sym.fullName)

  private def resolveOwnerExprFromTypeRef(
    c: blackbox.Context
  )(
    tr: c.universe.TypeRef,
    pre: c.Type,
    fallback: c.Symbol
  ): c.Tree = {
    import c.universe._

    val directOwner = tr.sym.owner
    val ownerBases  =
      if (directOwner.isClass) directOwner.asClass.baseClasses.map(_.fullName)
      else if (directOwner.isModule) directOwner.asModule.moduleClass.asClass.baseClasses.map(_.fullName)
      else Nil

    val isPreludeNewtypeOwner = ownerBases.exists(zioPreludeNewtypeBases.contains) ||
      isZioPreludeNewtypeBase(c)(directOwner)

    if (isPreludeNewtypeOwner) {
      pre match {
        case SingleType(_, termSym) if termSym.isModule =>
          val termName    = termSym.name.decodedName.toString.stripSuffix("$")
          val parentOwner = buildOwner(c)(termSym.owner)
          q"_root_.zio.blocks.typeid.Owner($parentOwner.segments :+ _root_.zio.blocks.typeid.Owner.Term($termName))"
        case _ =>
          buildOwner(c)(fallback)
      }
    } else {
      buildOwner(c)(fallback)
    }
  }

  private def buildOwner(c: blackbox.Context)(sym: c.Symbol): c.Tree = {
    import c.universe._

    sealed trait SegmentInfo { def name: String }
    case class PkgSegment(name: String)  extends SegmentInfo
    case class TermSegment(name: String) extends SegmentInfo
    case class TypeSegment(name: String) extends SegmentInfo

    def loop(s: Symbol, acc: List[SegmentInfo]): List[SegmentInfo] =
      if (s == NoSymbol || s.isPackageClass && s.fullName == "<root>" || s.fullName == "<empty>") {
        acc
      } else if (s.isPackage || s.isPackageClass) {
        val pkgName = s.name.decodedName.toString
        if (pkgName != "<root>" && pkgName != "<empty>") {
          loop(s.owner, PkgSegment(pkgName) :: acc)
        } else {
          acc
        }
      } else if (s.isModule || s.isModuleClass) {
        val termName = s.name.decodedName.toString.stripSuffix("$")
        loop(s.owner, TermSegment(termName) :: acc)
      } else if (s.isClass || s.isType) {
        loop(s.owner, TypeSegment(s.name.decodedName.toString) :: acc)
      } else {
        loop(s.owner, acc)
      }

    val segmentInfos = loop(sym, Nil)

    if (segmentInfos.isEmpty) {
      q"_root_.zio.blocks.typeid.Owner.Root"
    } else {
      val (pkgPrefix, rest) = segmentInfos.span(_.isInstanceOf[PkgSegment])
      val base              =
        if (pkgPrefix.isEmpty) q"_root_.zio.blocks.typeid.Owner.Root"
        else {
          val path = pkgPrefix.map(_.name).mkString(".")
          q"_root_.zio.blocks.typeid.Owner.fromPackagePath($path)"
        }
      rest.foldLeft(base: c.Tree) {
        case (acc, TermSegment(name)) => q"$acc.term($name)"
        case (acc, TypeSegment(name)) => q"$acc.tpe($name)"
        case (acc, PkgSegment(name))  => q"$acc./($name)"
      }
    }
  }

  private def buildTypeParams(c: blackbox.Context)(sym: c.Symbol): c.Tree = {
    import c.universe._

    val params = sym.asType.typeParams.zipWithIndex.map { case (p, idx) =>
      val paramName    = p.name.decodedName.toString
      val typeSym      = p.asType
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

  private val filteredBaseTypes = Set(
    "scala.Any",
    "scala.AnyRef",
    "java.lang.Object",
    "scala.Matchable",
    "scala.Product",
    "scala.Equals",
    "scala.deriving.Mirror",
    "scala.deriving.Mirror$.Product",
    "scala.deriving.Mirror$.Singleton",
    "scala.deriving.Mirror$.Sum",
    "java.io.Serializable"
  )

  private def buildBaseTypes(c: blackbox.Context)(sym: c.Symbol): c.Tree = {
    import c.universe._

    // Get base classes excluding the type itself and common types
    val baseClasses = if (sym.isClass) {
      sym.asClass.baseClasses.filterNot(base => base == sym || filteredBaseTypes.contains(base.fullName))
    } else if (sym.isModule) {
      sym.asModule.moduleClass.asClass.baseClasses.filterNot(base =>
        base == sym || filteredBaseTypes.contains(base.fullName)
      )
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
        q"_root_.zio.blocks.typeid.TypeDefKind.Trait(isSealed = true, bases = $basesExpr)"
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

  private def buildDefKindShallow(c: blackbox.Context)(sym: c.Symbol): c.Tree = {
    import c.universe._

    if (sym.isModule || sym.isModuleClass) {
      q"_root_.zio.blocks.typeid.TypeDefKind.Unknown"
    } else if (sym.isClass) {
      val classSym = sym.asClass
      buildClassDefKindShallow(c)(classSym)
    } else if (sym.isType && sym.asType.isAliasType) {
      q"_root_.zio.blocks.typeid.TypeDefKind.TypeAlias"
    } else if (sym.isType && sym.asType.isAbstract && !sym.isClass) {
      q"_root_.zio.blocks.typeid.TypeDefKind.AbstractType"
    } else {
      q"_root_.zio.blocks.typeid.TypeDefKind.Unknown"
    }
  }

  private def buildClassDefKindShallow(c: blackbox.Context)(classSym: c.universe.ClassSymbol): c.Tree = {
    import c.universe._

    val basesExpr = buildBaseTypesMinimal(c)(classSym)

    if (classSym.isTrait) {
      val isSealed = classSym.isSealed
      if (isSealed) {
        q"_root_.zio.blocks.typeid.TypeDefKind.Trait(isSealed = true, bases = $basesExpr)"
      } else {
        q"_root_.zio.blocks.typeid.TypeDefKind.Trait(isSealed = false, bases = $basesExpr)"
      }
    } else {
      val isFinal    = classSym.isFinal
      val isAbstract = classSym.isAbstract && !classSym.isTrait
      val isCase     = classSym.isCaseClass
      val isValue    = classSym.baseClasses.exists(_.fullName == "scala.AnyVal")

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

  private def buildBaseTypesMinimal(c: blackbox.Context)(sym: c.Symbol): c.Tree = {
    import c.universe._

    val baseClasses = if (sym.isClass) {
      sym.asClass.baseClasses.filterNot(base => base == sym || filteredBaseTypes.contains(base.fullName))
    } else if (sym.isModule) {
      sym.asModule.moduleClass.asClass.baseClasses.filterNot(base =>
        base == sym || filteredBaseTypes.contains(base.fullName)
      )
    } else {
      Nil
    }

    val baseExprs = baseClasses.map { base =>
      buildTypeReprMinimal(c)(base)
    }

    q"_root_.scala.List(..$baseExprs)"
  }

  private def buildTypeReprMinimal(c: blackbox.Context)(sym: c.Symbol): c.Tree = {
    import c.universe._

    val name = sym.name.decodedName.toString
    name match {
      case "Int"     => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.int)"
      case "String"  => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.string)"
      case "Long"    => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.long)"
      case "Boolean" => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.boolean)"
      case "Double"  => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.double)"
      case "Float"   => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.float)"
      case "Byte"    => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.byte)"
      case "Short"   => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.short)"
      case "Char"    => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.char)"
      case "Unit"    => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.unit)"
      case "List"    => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.list)"
      case "Option"  => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.option)"
      case "Map"     => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.map)"
      case "Either"  => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.either)"
      case "Set"     => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.set)"
      case "Vector"  => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.vector)"
      case "Any"     => q"_root_.zio.blocks.typeid.TypeRepr.AnyType"
      case "Nothing" => q"_root_.zio.blocks.typeid.TypeRepr.NothingType"
      case "Null"    => q"_root_.zio.blocks.typeid.TypeRepr.NullType"
      case _         =>
        val ownerExpr = buildOwner(c)(sym.owner)
        q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.nominal[_root_.scala.Nothing]($name, $ownerExpr, _root_.scala.Nil))"
    }
  }

  // ============================================================================
  // Annotation Extraction (Scala 2)
  // ============================================================================

  private def buildAnnotations(c: blackbox.Context)(sym: c.Symbol): c.Tree = {
    import c.universe._

    val annotations = sym.annotations.filterNot { annot =>
      val fullName = annot.tree.tpe.typeSymbol.fullName
      fullName.startsWith("scala.annotation.internal.") ||
      fullName.startsWith("scala.annotation.unchecked.") ||
      fullName == "scala.annotation.nowarn" ||
      fullName == "scala.annotation.tailcall" ||
      fullName == "scala.specialized"
    }

    val annotExprs = annotations.flatMap { annot =>
      buildAnnotation(c)(annot)
    }

    q"_root_.scala.List(..$annotExprs)"
  }

  private def buildAnnotation(c: blackbox.Context)(annot: c.universe.Annotation): Option[c.Tree] = {
    import c.universe._

    val annotTpe = annot.tree.tpe
    val annotSym = annotTpe.typeSymbol

    if (annotSym == NoSymbol) return None

    val annotName       = annotSym.name.decodedName.toString
    val annotOwnerExpr  = buildOwner(c)(annotSym.owner)
    val annotTypeIdExpr =
      q"""
        _root_.zio.blocks.typeid.TypeId.nominal[_root_.scala.Any](
          $annotName,
          $annotOwnerExpr,
          _root_.scala.Nil,
          _root_.scala.Nil,
          _root_.zio.blocks.typeid.TypeDefKind.Class(isFinal = false, isAbstract = false, isCase = false, isValue = false)
        )
      """

    val argsExpr = annot.tree match {
      case Apply(_, args) =>
        val argExprs = args.flatMap(arg => buildAnnotationArg(c)(arg))
        q"_root_.scala.List(..$argExprs)"
      case _ =>
        q"_root_.scala.Nil"
    }

    Some(q"_root_.zio.blocks.typeid.Annotation($annotTypeIdExpr, $argsExpr)")
  }

  private def buildAnnotationArg(c: blackbox.Context)(arg: c.Tree): Option[c.Tree] = {
    import c.universe._

    arg match {
      case Literal(Constant(v: String)) =>
        Some(q"_root_.zio.blocks.typeid.AnnotationArg.Const($v)")
      case Literal(Constant(v: Int)) =>
        Some(q"_root_.zio.blocks.typeid.AnnotationArg.Const($v)")
      case Literal(Constant(v: Long)) =>
        Some(q"_root_.zio.blocks.typeid.AnnotationArg.Const($v)")
      case Literal(Constant(v: Double)) =>
        Some(q"_root_.zio.blocks.typeid.AnnotationArg.Const($v)")
      case Literal(Constant(v: Float)) =>
        Some(q"_root_.zio.blocks.typeid.AnnotationArg.Const($v)")
      case Literal(Constant(v: Boolean)) =>
        Some(q"_root_.zio.blocks.typeid.AnnotationArg.Const($v)")
      case Literal(Constant(v: Char)) =>
        Some(q"_root_.zio.blocks.typeid.AnnotationArg.Const($v)")
      case Literal(Constant(v: Byte)) =>
        Some(q"_root_.zio.blocks.typeid.AnnotationArg.Const($v: _root_.scala.Byte)")
      case Literal(Constant(v: Short)) =>
        Some(q"_root_.zio.blocks.typeid.AnnotationArg.Const($v: _root_.scala.Short)")
      case Literal(Constant(null)) =>
        Some(q"_root_.zio.blocks.typeid.AnnotationArg.Const(null)")

      case NamedArg(Ident(TermName(name)), value) =>
        buildAnnotationArg(c)(value).map { valueExpr =>
          q"_root_.zio.blocks.typeid.AnnotationArg.Named($name, $valueExpr)"
        }

      case Typed(expr, _) =>
        buildAnnotationArg(c)(expr)

      case Apply(TypeApply(Select(Ident(TermName("Array")), TermName("apply")), _), elems) =>
        val elemExprs = elems.flatMap(e => buildAnnotationArg(c)(e))
        Some(q"_root_.zio.blocks.typeid.AnnotationArg.ArrayArg(_root_.scala.List(..$elemExprs))")

      case Apply(Select(New(tpt), termNames.CONSTRUCTOR), nestedArgs) =>
        val nestedAnnotSym   = tpt.tpe.typeSymbol
        val nestedOwnerExpr  = buildOwner(c)(nestedAnnotSym.owner)
        val nestedTypeIdExpr =
          q"_root_.zio.blocks.typeid.TypeId.nominal[_root_.scala.Any](${nestedAnnotSym.name.decodedName.toString}, $nestedOwnerExpr, _root_.scala.Nil)"
        val nestedArgsExpr = nestedArgs.flatMap(a => buildAnnotationArg(c)(a))
        Some(
          q"_root_.zio.blocks.typeid.AnnotationArg.Nested(_root_.zio.blocks.typeid.Annotation($nestedTypeIdExpr, _root_.scala.List(..$nestedArgsExpr)))"
        )

      case _ =>
        None
    }
  }

  // ============================================================================
  // Self Type Extraction (Scala 2)
  // ============================================================================

  private def extractSelfType(c: blackbox.Context)(sym: c.Symbol): c.Tree = {
    import c.universe._

    if (!sym.isClass) {
      return q"_root_.scala.None"
    }

    val classSym  = sym.asClass
    val selfType  = classSym.selfType
    val classType = classSym.toType

    // No self-type annotation if selfType equals the class type
    if (selfType =:= classType) {
      return q"_root_.scala.None"
    }

    // In Scala 2, selfType for `trait Foo { self: Bar => }` is a refinement type
    // representing `this.type with Bar`. We need to extract `Bar` by looking at
    // the base types and filtering out the trait itself and standard types.
    val baseTypes = selfType.baseType(classSym) match {
      case NoType => selfType.baseClasses.map(selfType.baseType).filter(_ != NoType)
      case _      => selfType.baseClasses.map(selfType.baseType).filter(_ != NoType)
    }

    // Filter out:
    // 1. The class/trait itself
    // 2. AnyRef and Any (not meaningful for self-type annotation)
    val meaningfulBases = baseTypes.filter { tpe =>
      val typeSym = tpe.typeSymbol
      typeSym != classSym &&
      typeSym.fullName != "scala.Any" &&
      typeSym.fullName != "scala.AnyRef" &&
      typeSym.fullName != "java.lang.Object"
    }

    if (meaningfulBases.isEmpty) {
      q"_root_.scala.None"
    } else if (meaningfulBases.size == 1) {
      val selfTypeRepr = buildTypeReprFromType(c)(meaningfulBases.head)
      q"_root_.scala.Some($selfTypeRepr)"
    } else {
      // Multiple base types -> create Intersection
      val reprTrees = meaningfulBases.map(t => buildTypeReprFromType(c)(t))
      val reprList  = reprTrees.foldRight(q"_root_.scala.Nil": c.Tree)((elem, acc) => q"$elem :: $acc")
      q"_root_.scala.Some(_root_.zio.blocks.typeid.TypeRepr.Intersection($reprList))"
    }
  }
}
