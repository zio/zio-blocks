package zio.blocks.typeid

import scala.reflect.macros.blackbox

/**
 * Scala 2.13 macro implementation for TypeId derivation.
 * 
 * This object provides compile-time derivation of TypeId instances
 * using Scala 2's reflection-based macro system.
 */
object TypeIdMacros {

  /**
   * Derives a TypeId for type A at compile time.
   */
  def deriveMacro[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] = {
    import c.universe._

    val tpe = weakTypeOf[A]
    val typeSymbol = tpe.typeSymbol

    // Extract package segments
    def ownerChain(sym: Symbol): List[Symbol] = {
      def loop(s: Symbol, acc: List[Symbol]): List[Symbol] = {
        if (s == NoSymbol || s.isPackage && s.fullName == "<root>") acc
        else loop(s.owner, s :: acc)
      }
      loop(sym.owner, Nil)
    }

    // Build owner segments
    val owners = ownerChain(typeSymbol)
    val ownerSegments: List[Tree] = owners.map { owner =>
      if (owner.isPackage) {
        q"_root_.zio.blocks.typeid.Owner.Package(${owner.name.decodedName.toString})"
      } else if (owner.isModuleClass || owner.isModule) {
        q"_root_.zio.blocks.typeid.Owner.Term(${owner.name.decodedName.toString.stripSuffix("$")})"
      } else if (owner.isClass || owner.isType) {
        q"_root_.zio.blocks.typeid.Owner.Type(${owner.name.decodedName.toString})"
      } else {
        q"_root_.zio.blocks.typeid.Owner.Term(${owner.name.decodedName.toString})"
      }
    }

    // Build type parameters
    val typeParamTrees: List[Tree] = tpe.typeConstructor.typeParams.zipWithIndex.map { case (param, index) =>
      val name = param.name.decodedName.toString
      val variance = if (param.asType.isCovariant) {
        q"_root_.zio.blocks.typeid.Variance.Covariant"
      } else if (param.asType.isContravariant) {
        q"_root_.zio.blocks.typeid.Variance.Contravariant"
      } else {
        q"_root_.zio.blocks.typeid.Variance.Invariant"
      }
      q"new _root_.zio.blocks.typeid.TypeParam($name, $index, $variance, _root_.zio.blocks.typeid.TypeParam.Bounds.Unbounded, false)"
    }

    val typeName = typeSymbol.name.decodedName.toString
    val ownerTree = q"_root_.zio.blocks.typeid.Owner(_root_.scala.List(..$ownerSegments))"
    val typeParamsTree = q"_root_.scala.List(..$typeParamTrees)"

    // Determine if this is an alias
    if (tpe.typeSymbol.isType && tpe.typeSymbol.asType.isAliasType) {
      val aliasedType = tpe.dealias
      val aliasedRepr = typeToRepr(c)(aliasedType)
      c.Expr[TypeId[A]](
        q"_root_.zio.blocks.typeid.TypeId.alias[$tpe]($typeName, $ownerTree, $typeParamsTree, $aliasedRepr)"
      )
    } else {
      c.Expr[TypeId[A]](
        q"_root_.zio.blocks.typeid.TypeId.nominal[$tpe]($typeName, $ownerTree, $typeParamsTree)"
      )
    }
  }

  /**
   * Converts a Scala type to a TypeRepr tree.
   */
  private def typeToRepr(c: blackbox.Context)(tpe: c.universe.Type): c.Tree = {
    import c.universe._

    tpe match {
      case t if t =:= typeOf[Unit]    => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.unit)"
      case t if t =:= typeOf[Boolean] => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.boolean)"
      case t if t =:= typeOf[Byte]    => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.byte)"
      case t if t =:= typeOf[Short]   => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.short)"
      case t if t =:= typeOf[Int]     => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.int)"
      case t if t =:= typeOf[Long]    => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.long)"
      case t if t =:= typeOf[Float]   => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.float)"
      case t if t =:= typeOf[Double]  => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.double)"
      case t if t =:= typeOf[Char]    => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.char)"
      case t if t =:= typeOf[String]  => q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.string)"

      case TypeRef(_, sym, Nil) =>
        // Simple type reference
        val id = deriveTypeIdTree(c)(tpe)
        q"_root_.zio.blocks.typeid.TypeRepr.Ref($id)"

      case TypeRef(_, sym, args) =>
        // Applied type
        val tyconId = deriveTypeIdTree(c)(tpe.typeConstructor)
        val argReprs = args.map(typeToRepr(c))
        q"_root_.zio.blocks.typeid.TypeRepr.Applied(_root_.zio.blocks.typeid.TypeRepr.Ref($tyconId), _root_.scala.List(..$argReprs))"

      case _ =>
        // Fallback: create a nominal TypeId
        val id = deriveTypeIdTree(c)(tpe)
        q"_root_.zio.blocks.typeid.TypeRepr.Ref($id)"
    }
  }

  /**
   * Derives a TypeId tree for a given type (for use within TypeRepr).
   */
  private def deriveTypeIdTree(c: blackbox.Context)(tpe: c.universe.Type): c.Tree = {
    import c.universe._

    val typeSymbol = tpe.typeSymbol

    def ownerChain(sym: Symbol): List[Symbol] = {
      def loop(s: Symbol, acc: List[Symbol]): List[Symbol] = {
        if (s == NoSymbol || s.isPackage && s.fullName == "<root>") acc
        else loop(s.owner, s :: acc)
      }
      loop(sym.owner, Nil)
    }

    val owners = ownerChain(typeSymbol)
    val ownerSegments: List[Tree] = owners.map { owner =>
      if (owner.isPackage) {
        q"_root_.zio.blocks.typeid.Owner.Package(${owner.name.decodedName.toString})"
      } else if (owner.isModuleClass || owner.isModule) {
        q"_root_.zio.blocks.typeid.Owner.Term(${owner.name.decodedName.toString.stripSuffix("$")})"
      } else {
        q"_root_.zio.blocks.typeid.Owner.Type(${owner.name.decodedName.toString})"
      }
    }

    val typeName = typeSymbol.name.decodedName.toString
    val ownerTree = q"_root_.zio.blocks.typeid.Owner(_root_.scala.List(..$ownerSegments))"

    q"_root_.zio.blocks.typeid.TypeId.nominal[$tpe]($typeName, $ownerTree, _root_.scala.Nil)"
  }
}

/**
 * Scala 2.13 specific extension for TypeId companion.
 */
trait TypeIdVersionSpecific {
  import scala.language.experimental.macros

  /**
   * Derives a TypeId for type A at compile time.
   * 
   * == Example ==
   * {{{
   * val listId: TypeId[List] = TypeId.derive[List]
   * val myClassId: TypeId[MyClass] = TypeId.derive[MyClass]
   * }}}
   * 
   * @tparam A The type to derive a TypeId for
   * @return A TypeId representing type A
   */
  def derive[A]: TypeId[A] = macro TypeIdMacros.deriveMacro[A]
}
