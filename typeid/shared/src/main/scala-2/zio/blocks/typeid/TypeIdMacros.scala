package zio.blocks.typeid

import scala.reflect.macros.blackbox

/**
 * Scala 2.13 macro implementation for TypeId derivation.
 */
object TypeIdMacros {

  def derive[A](c: blackbox.Context)(implicit A: c.WeakTypeTag[A]): c.Expr[TypeId[A]] = {
    import c.universe._
    val tpe = A.tpe

    // Check for RefinedType (intersection/structural types) first - they must be handled specially
    tpe match {
      case RefinedType(_, decls) =>
        // Handle intersection types (A with B) and structural types
        // For Scala 2.13, we cannot use RefinedType directly in type parameter positions
        // Workaround: use existential type with AnyKind and let type erasure handle it
        val repr = processTypeRepr(c)(tpe)
        val name = if (decls.nonEmpty) "Structural" else "Intersection"
        // Create TypeId with existential AnyKind - safe due to type erasure
        val typeIdTree =
          q"_root_.zio.blocks.typeid.TypeId.alias[_root_.zio.blocks.typeid.AnyKind]($name, _root_.zio.blocks.typeid.Owner.Root, Nil, $repr)"
        // Cast through Any first to avoid type parameter issues, then to TypeId[A]
        return c.Expr[TypeId[A]](
          q"$typeIdTree.asInstanceOf[_root_.zio.blocks.typeid.TypeId[_]].asInstanceOf[_root_.zio.blocks.typeid.TypeId[${A.tpe}]]"
        )
      case _ =>
        // Check if it's an applied type (e.g. List[Int])
        val isApplied = tpe.typeArgs.nonEmpty
        if (isApplied) {
          // For applied types like List[Int], create an alias that wraps Applied TypeRepr
          deriveAppliedType[A](c)(tpe)
        } else {
          deriveImpl[A](c)(tpe)
        }
    }
  }

  private def deriveAppliedType[A](
    c: blackbox.Context
  )(tpe: c.universe.Type)(implicit A: c.WeakTypeTag[A]): c.Expr[TypeId[A]] = {
    import c.universe._

    // Check if the type constructor is an alias (e.g., type MyList[A] = List[A])
    // If so, use dealias to get the underlying type and apply args to that
    val tyconType = tpe.typeConstructor
    if (tyconType.dealias != tyconType) {
      // The type constructor is an alias, so dealias the entire type and derive it
      // This ensures MyList[String] normalizes to List[String]
      val dealiasedTpe = tpe.dealias
      val repr         = processTypeRepr(c)(dealiasedTpe)
      val symbol       = tpe.typeSymbol
      c.Expr[TypeId[A]](q"""
        _root_.zio.blocks.typeid.TypeId.alias[$tpe](
          name = ${symbol.name.toString},
          owner = ${processOwner(c)(symbol.owner)},
          typeParams = Nil,
          aliased = $repr
        )
      """)
    } else {
      // The type constructor is not an alias, proceed as before
      val tyconSymbol = tyconType.typeSymbol
      val tyconName   = tyconSymbol.name.toString

      val tyconOwner = processOwner(c)(tyconSymbol.owner)

      val tyconParams = tyconType.typeParams.zipWithIndex.map { case (p, idx) =>
        val pSym     = p.asType
        val variance =
          if (pSym.isCovariant) q"_root_.zio.blocks.typeid.Variance.Covariant"
          else if (pSym.isContravariant) q"_root_.zio.blocks.typeid.Variance.Contravariant"
          else q"_root_.zio.blocks.typeid.Variance.Invariant"
        q"_root_.zio.blocks.typeid.TypeParam(${pSym.name.toString}, $idx, $variance, _root_.zio.blocks.typeid.TypeBounds.empty)"
      }

      val tyconDefKind = if (tyconSymbol.isClass && tyconSymbol.asClass.isTrait) {
        q"_root_.zio.blocks.typeid.TypeDefKind.Trait(isSealed = ${tyconSymbol.asClass.isSealed}, knownSubtypes = Nil)"
      } else if (tyconSymbol.isClass) {
        val cl = tyconSymbol.asClass
        q"_root_.zio.blocks.typeid.TypeDefKind.Class(isFinal = ${cl.isFinal}, isAbstract = ${cl.isAbstract}, isCase = ${cl.isCaseClass}, isValue = false)"
      } else {
        q"_root_.zio.blocks.typeid.TypeDefKind.AbstractType"
      }

      val tyconRef =
        q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.nominal($tyconName, $tyconOwner, List(..$tyconParams), $tyconDefKind))"
      val args    = tpe.typeArgs.map(arg => processTypeRepr(c)(arg))
      val applied = q"_root_.zio.blocks.typeid.TypeRepr.Applied($tyconRef, List(..$args))"

      c.Expr[TypeId[A]](q"""
        _root_.zio.blocks.typeid.TypeId.alias[$tpe](
          name = $tyconName,
          owner = $tyconOwner,
          typeParams = Nil,
          aliased = $applied
        )
      """)
    }
  }

  private def deriveImpl[A](
    c: blackbox.Context
  )(tpe: c.universe.Type)(implicit A: c.WeakTypeTag[A]): c.Expr[TypeId[A]] = {
    import c.universe._

    // Handle composite types (Applied/Intersections/Unions) that describe the type's structure
    // Note: RefinedType handling is skipped here for Scala 2.13 compatibility
    // RefinedType cannot be used in type parameter positions in Scala 2.13 macro-generated code
    tpe match {
      case _ if tpe.typeArgs.nonEmpty =>
        val repr   = processTypeRepr(c)(tpe)
        val symbol = tpe.typeSymbol
        return c.Expr[TypeId[A]](
          q"_root_.zio.blocks.typeid.TypeId.alias[$tpe](${symbol.name.toString}, ${processOwner(c)(symbol.owner)}, Nil, $repr)"
        )
      case _ => // Continue
    }

    val symbol = tpe.typeSymbol

    val nameExpr  = q"${symbol.name.toString}"
    val ownerExpr = processOwner(c)(symbol.owner)

    val typeParamsExpr = {
      val typeParams = tpe.typeParams
      q"List(..${typeParams.zipWithIndex.map { case (tp, idx) =>
          processTypeParam(c)(tp.asType, idx)
        }})"
    }

    // Determine DefKind and Children
    val children = if (symbol.isClass) {
      symbol.asClass.knownDirectSubclasses.toList
    } else {
      Nil
    }

    val defKindExpr = if (symbol.isClass && symbol.asClass.isTrait) {
      val isSealed     = symbol.asClass.isSealed
      val subtypesExpr = if (isSealed) {
        q"List(..${children.map(child => basicRef(c)(child))})"
      } else q"Nil"
      q"_root_.zio.blocks.typeid.TypeDefKind.Trait(isSealed = $isSealed, knownSubtypes = $subtypesExpr)"
    } else if (symbol.isClass && !symbol.asClass.isTrait) {
      val cl = symbol.asClass
      q"_root_.zio.blocks.typeid.TypeDefKind.Class(isFinal = ${cl.isFinal}, isAbstract = ${cl.isAbstract}, isCase = ${cl.isCaseClass}, isValue = ${tpe <:< typeOf[AnyVal]})"
    } else if (symbol.isModule) {
      q"_root_.zio.blocks.typeid.TypeDefKind.Object"
    } else {
      q"_root_.zio.blocks.typeid.TypeDefKind.AbstractType"
    }

    // Parents - capture with proper type application
    val parentsExpr = q"List(..${tpe.baseClasses.filterNot { s =>
        val n = s.name.toString
        n == "Any" || n == "Object" || n == "Product" || n == "Serializable" || n == "Equals" ||
        n == "Matchable" || n.endsWith("Ops") || n.contains("StrictOptimized") || n == "DefaultSerializable" || s == symbol
      }.take(5).map { parentSym =>
        // Get the actual parent type as applied to this type's parameters
        val parentTpe = tpe.baseType(parentSym)
        if (parentTpe =:= typeOf[Nothing] || parentTpe =:= typeOf[Any]) {
          basicRef(c)(parentSym)
        } else {
          processTypeRepr(c)(parentTpe)
        }
      }})"

    // Scala 2.13 doesn't support opaque types, so this is always false
    val isOpaque = false

    if (symbol.isType && !isOpaque && tpe.dealias != tpe) {
      val aliasedTpe  = tpe.dealias
      val aliasedExpr = processTypeRepr(c)(aliasedTpe)
      c.Expr[TypeId[A]](q"""
        _root_.zio.blocks.typeid.TypeId.alias[$tpe](
          name = $nameExpr,
          owner = $ownerExpr,
          typeParams = $typeParamsExpr,
          aliased = $aliasedExpr
        )
      """)
    } else if (isOpaque) {
      val reprTpe  = tpe.dealias
      val reprExpr = processTypeRepr(c)(reprTpe)
      c.Expr[TypeId[A]](q"""
        _root_.zio.blocks.typeid.TypeId.opaque[$tpe](
          name = $nameExpr,
          owner = $ownerExpr,
          typeParams = $typeParamsExpr,
          representation = $reprExpr
        )
      """)
    } else {
      c.Expr[TypeId[A]](q"""
        _root_.zio.blocks.typeid.TypeId.nominal[$tpe](
          name = $nameExpr,
          owner = $ownerExpr,
          typeParams = $typeParamsExpr,
          defKind = $defKindExpr,
          parents = $parentsExpr
        )
      """)
    }
  }

  private def processOwner(c: blackbox.Context)(owner: c.universe.Symbol): c.Tree = {
    import c.universe._

    def buildSegments(sym: Symbol, acc: List[c.Tree] = Nil): List[c.Tree] =
      if (sym == NoSymbol || sym == rootMirror.RootClass) acc.reverse
      else {
        val segment = if (sym.isPackage) {
          q"_root_.zio.blocks.typeid.Owner.Package(${sym.name.toString})"
        } else if (sym.isType) {
          q"_root_.zio.blocks.typeid.Owner.Type(${sym.name.toString})"
        } else {
          q"_root_.zio.blocks.typeid.Owner.Term(${sym.name.toString})"
        }
        buildSegments(sym.owner, segment :: acc)
      }

    val segments = buildSegments(owner)
    if (segments.isEmpty) {
      q"_root_.zio.blocks.typeid.Owner.Root"
    } else {
      q"_root_.zio.blocks.typeid.Owner(List(..$segments))"
    }
  }

  private def processTypeParam(c: blackbox.Context)(tp: c.universe.TypeSymbol, idx: Int): c.Tree = {
    import c.universe._

    val variance =
      if (tp.isCovariant) q"_root_.zio.blocks.typeid.Variance.Covariant"
      else if (tp.isContravariant) q"_root_.zio.blocks.typeid.Variance.Contravariant"
      else q"_root_.zio.blocks.typeid.Variance.Invariant"

    q"_root_.zio.blocks.typeid.TypeParam(${tp.name.toString}, $idx, $variance, _root_.zio.blocks.typeid.TypeBounds.empty)"
  }

  private def basicRef(c: blackbox.Context)(sym: c.universe.Symbol): c.Tree = {
    import c.universe._

    val name       = sym.name.toString
    val owner      = processOwner(c)(sym.owner)
    val typeParams = sym.typeSignature.typeParams.zipWithIndex.map { case (tp, idx) =>
      processTypeParam(c)(tp.asType, idx)
    }

    val defKind = if (sym.isClass && sym.asClass.isTrait) {
      q"_root_.zio.blocks.typeid.TypeDefKind.Trait(isSealed = ${sym.asClass.isSealed}, knownSubtypes = Nil)"
    } else if (sym.isClass) {
      val cl = sym.asClass
      q"_root_.zio.blocks.typeid.TypeDefKind.Class(isFinal = ${cl.isFinal}, isAbstract = ${cl.isAbstract}, isCase = ${cl.isCaseClass}, isValue = false)"
    } else {
      q"_root_.zio.blocks.typeid.TypeDefKind.AbstractType"
    }

    q"_root_.zio.blocks.typeid.TypeId.nominal($name, $owner, List(..$typeParams), $defKind)"
  }

  private def processTypeRepr(c: blackbox.Context)(tpe: c.universe.Type): c.Tree = {
    import c.universe._

    tpe match {
      // Handle Function types: Function1[A, B], Function2[A, B, C], etc.
      case TypeRef(_, sym, args) if sym.fullName.startsWith("scala.Function") && args.nonEmpty =>
        val paramTypes = args.init.map(processTypeRepr(c)(_))
        val resultType = processTypeRepr(c)(args.last)
        q"_root_.zio.blocks.typeid.TypeRepr.Function(List(..$paramTypes), $resultType)"

      // Handle Tuple types: Tuple2[A, B], Tuple3[A, B, C], etc.
      case TypeRef(_, sym, args) if sym.fullName.startsWith("scala.Tuple") && args.nonEmpty =>
        val elemTrees  = args.map(processTypeRepr(c)(_))
        val tupleElems = elemTrees.map(e => q"_root_.zio.blocks.typeid.TypeRepr.TupleElement(None, $e)")
        q"_root_.zio.blocks.typeid.TypeRepr.Tuple(List(..$tupleElems))"

      case TypeRef(_, sym, args) if args.nonEmpty =>
        val tycon     = basicRef(c)(sym)
        val argsTrees = args.map(processTypeRepr(c)(_))
        q"_root_.zio.blocks.typeid.TypeRepr.Applied($tycon, List(..$argsTrees))"

      case TypeRef(pre, sym, Nil) =>
        val segments = termPathSegments(c)(pre)
        if (segments.nonEmpty) {
          // Local types: use TypeSelect
          q"_root_.zio.blocks.typeid.TypeRepr.TypeSelect(_root_.zio.blocks.typeid.TermPath(List(..$segments)), ${sym.name.toString})"
        } else {
          q"_root_.zio.blocks.typeid.TypeRepr.Ref(${basicRef(c)(sym)})"
        }

      case ThisType(sym) =>
        q"_root_.zio.blocks.typeid.TypeRepr.ThisType(${basicRef(c)(sym)})"

      case SingleType(pre, sym) =>
        val segments = termPathSegments(c)(pre)
        q"_root_.zio.blocks.typeid.TypeRepr.Singleton(_root_.zio.blocks.typeid.TermPath(List(..${segments :+ q"_root_.zio.blocks.typeid.TermPath.Val(${sym.name.toString})"})))"

      case ConstantType(const) =>
        val constTree = const match {
          case Constant(s: String)  => q"_root_.zio.blocks.typeid.Constant.StringConst($s)"
          case Constant(i: Int)     => q"_root_.zio.blocks.typeid.Constant.IntConst($i)"
          case Constant(l: Long)    => q"_root_.zio.blocks.typeid.Constant.LongConst($l)"
          case Constant(d: Double)  => q"_root_.zio.blocks.typeid.Constant.DoubleConst($d)"
          case Constant(f: Float)   => q"_root_.zio.blocks.typeid.Constant.FloatConst($f)"
          case Constant(b: Boolean) => q"_root_.zio.blocks.typeid.Constant.BooleanConst($b)"
          case Constant(c: Char)    => q"_root_.zio.blocks.typeid.Constant.CharConst($c)"
          case Constant(b: Byte)    => q"_root_.zio.blocks.typeid.Constant.ByteConst($b)"
          case Constant(s: Short)   => q"_root_.zio.blocks.typeid.Constant.ShortConst($s)"
          case Constant(())         => q"_root_.zio.blocks.typeid.Constant.UnitConst()"
          case Constant(null)       => q"_root_.zio.blocks.typeid.Constant.NullConst()"
          case _                    => q"_root_.zio.blocks.typeid.Constant.NullConst()" // fallback
        }
        q"_root_.zio.blocks.typeid.TypeRepr.ConstantType($constTree)"

      case RefinedType(parents, decls) =>
        val parentTrees = parents.map(processTypeRepr(c)(_))
        // Extract structural members from decls
        val memberTrees = decls.toList.flatMap { decl =>
          decl match {
            case m: MethodSymbol =>
              val paramLists = m.paramLists
              // In Scala 2 structural types:
              // - val size: Int is represented as MethodSymbol with no params (getter)
              // - def size: Int is also MethodSymbol with no params (parameterless method)
              // We cannot reliably distinguish them in Scala 2.13 - both compile to the same bytecode
              // However, we check if it has params to distinguish def size(): Int from val size
              // For structural types, val size: Int vs def size: Int cannot be distinguished
              // This is a known limitation of Scala 2.13 structural types
              if (paramLists.isEmpty || paramLists.forall(_.isEmpty)) {
                // This could be either a val (getter) or a def (parameterless method)
                // In Scala 2.13, we cannot distinguish them, so treat all as def for consistency
                // with the issue requirement that val and def should be different
                // However, since Scala 2.13 cannot distinguish them, we create Member.Def
                // with empty param clauses to represent both
                // Note: This means val vs def will be equal in Scala 2.13, which is a limitation
                val paramClauses = paramLists.map { params =>
                  val paramTrees = params.map { p =>
                    val pType      = processTypeRepr(c)(p.typeSignature)
                    val isRepeated = p.typeSignature.typeSymbol.fullName == "scala.<repeated>"
                    q"_root_.zio.blocks.typeid.Param(${p.name.toString}, $pType, hasDefault = false, isRepeated = $isRepeated)"
                  }
                  q"_root_.zio.blocks.typeid.ParamClause.Regular(List(..$paramTrees))"
                }
                val returnType = processTypeRepr(c)(m.returnType)
                // Check if this looks like a val (no params, could be accessor)
                // In Scala 2, we cannot reliably distinguish, so we'll try to use method flags
                // If it's marked as ACCESSOR, treat as Val, otherwise as Def
                // But ACCESSOR flag check is not available, so we treat all parameterless as Def
                // This is a limitation - val vs def cannot be distinguished in Scala 2 structural types
                Some(
                  q"_root_.zio.blocks.typeid.Member.Def(${m.name.toString}, Nil, List(..$paramClauses), $returnType)"
                )
              } else {
                // This is a def (method with params)
                val paramClauses = paramLists.map { params =>
                  val paramTrees = params.map { p =>
                    val pType      = processTypeRepr(c)(p.typeSignature)
                    val isRepeated = p.typeSignature.typeSymbol.fullName == "scala.<repeated>"
                    q"_root_.zio.blocks.typeid.Param(${p.name.toString}, $pType, hasDefault = false, isRepeated = $isRepeated)"
                  }
                  q"_root_.zio.blocks.typeid.ParamClause.Regular(List(..$paramTrees))"
                }
                val returnType = processTypeRepr(c)(m.returnType)
                Some(
                  q"_root_.zio.blocks.typeid.Member.Def(${m.name.toString}, Nil, List(..$paramClauses), $returnType)"
                )
              }
            case t: TermSymbol if t.isTerm =>
              val tType     = processTypeRepr(c)(t.typeSignature)
              val isMutable = t.isVar
              Some(q"_root_.zio.blocks.typeid.Member.Val(${t.name.toString}, $tType, isMutable = $isMutable)")
            case t: TypeSymbol if t.isType =>
              val bounds = t.typeSignature match {
                case TypeBounds(lo, hi) =>
                  val loTree = if (lo =:= typeOf[Nothing]) q"None" else q"Some(${processTypeRepr(c)(lo)})"
                  val hiTree = if (hi =:= typeOf[Any]) q"None" else q"Some(${processTypeRepr(c)(hi)})"
                  q"_root_.zio.blocks.typeid.TypeBounds($loTree, $hiTree)"
                case _ =>
                  q"_root_.zio.blocks.typeid.TypeBounds.empty"
              }
              Some(q"_root_.zio.blocks.typeid.Member.TypeMember(${t.name.toString}, Nil, $bounds, None)")
            case _ => None
          }
        }
        if (memberTrees.nonEmpty) {
          q"_root_.zio.blocks.typeid.TypeRepr.Structural(List(..$parentTrees), List(..$memberTrees))"
        } else {
          q"_root_.zio.blocks.typeid.TypeRepr.Intersection(List(..$parentTrees))"
        }

      case ExistentialType(_, underlying) =>
        // Approximate existentials as the underlying type
        processTypeRepr(c)(underlying)

      case AnnotatedType(_, underlying) =>
        val underlyingTree = processTypeRepr(c)(underlying)
        // Skip annotations for now
        underlyingTree

      case _ if tpe =:= typeOf[Int]    => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.Int)"
      case _ if tpe =:= typeOf[Long]   => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.Long)"
      case _ if tpe =:= typeOf[String] =>
        q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.String)"
      case _ if tpe =:= typeOf[Boolean] =>
        q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.Boolean)"
      case _ if tpe =:= typeOf[Any]     => q"_root_.zio.blocks.typeid.TypeRepr.AnyType"
      case _ if tpe =:= typeOf[Nothing] => q"_root_.zio.blocks.typeid.TypeRepr.NothingType"
      case _ if tpe =:= typeOf[Unit]    => q"_root_.zio.blocks.typeid.TypeRepr.UnitType"

      case _ =>
        // Fallback for unknown types
        q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.nominal(${tpe.toString}, _root_.zio.blocks.typeid.Owner.Root, Nil, _root_.zio.blocks.typeid.TypeDefKind.AbstractType))"
    }
  }

  private def termPathSegments(c: blackbox.Context)(pre: c.universe.Type): List[c.Tree] = {
    import c.universe._

    def build(pre: Type, acc: List[c.Tree]): List[c.Tree] = pre match {
      case ThisType(sym) =>
        q"_root_.zio.blocks.typeid.TermPath.This(${sym.name.toString})" :: acc
      case SingleType(ThisType(_), name) =>
        q"_root_.zio.blocks.typeid.TermPath.Val(${name.toString})" :: acc
      case TypeRef(ThisType(sym), _, _) =>
        q"_root_.zio.blocks.typeid.TermPath.This(${sym.name.toString})" :: acc
      case _ => acc
    }

    build(pre, Nil)
  }
}
