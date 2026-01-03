package zio.blocks.typeid

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Scala 2.13 macro implementations for TypeId derivation.
 */
object TypeIdMacros {
  
  def deriveMacro[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] = {
    import c.universe._
    
    val tpe = weakTypeOf[A]
    val symbol = tpe.typeSymbol
    val name = symbol.name.decodedName.toString
    
    // Check for applied type FIRST (e.g., List[Int], Map[String, Int])
    val typeArgs = tpe.typeArgs
    
    if (typeArgs.nonEmpty) {
      // Applied type: List[Int], Map[String, Int], etc.
      val tyconSymbol = symbol
      val tyconName = tyconSymbol.name.decodedName.toString
      val tyconOwnerExpr = buildOwnerExpr(c)(buildOwnerSegments(c)(tyconSymbol.owner))
      val tyconTypeParamsExpr = buildTypeParamsExpr(c)(typeArgs, tyconSymbol.asType.typeParams)
      
      // Build the type constructor (e.g., TypeId for List, not List[Int])
      val tyconExpr = q"""
        _root_.zio.blocks.typeid.TypeId.nominal[Any](
          $tyconName,
          $tyconOwnerExpr,
          $tyconTypeParamsExpr
        )
      """
      
      // Build TypeId for each argument (recursive derivation)
      val argExprs = typeArgs.map { argTpe =>
        q"_root_.zio.blocks.typeid.TypeId.derive[$argTpe]"
      }
      
      c.Expr[TypeId[A]](q"""
        _root_.zio.blocks.typeid.TypeId.applied[$tpe](
          $tyconExpr,
          _root_.scala.List(..$argExprs)
        )
      """)
    } else {
      // Non-applied type (Nominal or Alias)
      // Build owner path
      val ownerSegments = buildOwnerSegments(c)(symbol.owner)
      val ownerExpr = buildOwnerExpr(c)(ownerSegments)
      
      // Build type parameters
      val typeParamsExpr = buildTypeParamsExpr(c)(typeArgs, symbol.asType.typeParams)
      
      // Determine if this is an alias (typeAlias in Scala 2)
      val isAlias = symbol.isType && symbol.asType.isAliasType
      
      if (isAlias) {
        // Type alias: type Age = Int
        val aliasedType = tpe.dealias
        val aliasedExpr = buildTypeReprExpr(c)(aliasedType, typeArgs, symbol.asType.typeParams)
        c.Expr[TypeId[A]](q"""
          _root_.zio.blocks.typeid.TypeId.alias[$tpe](
            $name,
            $ownerExpr,
            $typeParamsExpr,
            $aliasedExpr
          )
        """)
      } else {
        // Nominal type: class, trait, object
        c.Expr[TypeId[A]](q"""
          _root_.zio.blocks.typeid.TypeId.nominal[$tpe](
            $name,
            $ownerExpr,
            $typeParamsExpr
          )
        """)
      }
    }
  }
  
  private def buildOwnerSegments(c: blackbox.Context)(owner: c.Symbol): List[(String, String)] = {
    import c.universe._
    
    if (owner == NoSymbol || owner.isPackageClass || owner == c.mirror.RootClass) {
      Nil
    } else {
      val name = owner.name.decodedName.toString
      val kind = 
        if (owner.isPackage || owner.isPackageClass) "Package"
        else if (owner.isModuleClass || owner.isModule) "Term"
        else "Type"
      buildOwnerSegments(c)(owner.owner) :+ (kind, name)
    }
  }
  
  private def buildOwnerExpr(c: blackbox.Context)(segments: List[(String, String)]): c.Tree = {
    import c.universe._
    
    if (segments.isEmpty) {
      q"_root_.zio.blocks.typeid.Owner.Root"
    } else {
      val segmentExprs = segments.map { case (kind, name) =>
        kind match {
          case "Package" => q"new _root_.zio.blocks.typeid.Owner.Package($name)"
          case "Term"    => q"new _root_.zio.blocks.typeid.Owner.Term($name)"
          case "Type"    => q"new _root_.zio.blocks.typeid.Owner.Type($name)"
        }
      }
      q"new _root_.zio.blocks.typeid.Owner(_root_.scala.List(..$segmentExprs))"
    }
  }
  
  private def buildTypeParamsExpr(c: blackbox.Context)(
    typeArgs: List[c.Type], 
    typeParams: List[c.Symbol]
  ): c.Tree = {
    import c.universe._
    
    val paramExprs = typeParams.zipWithIndex.map { case (param, idx) =>
      val paramName = param.name.decodedName.toString
      q"new _root_.zio.blocks.typeid.TypeParam($paramName, $idx)"
    }
    q"_root_.scala.List(..$paramExprs)"
  }
  
  private def buildTypeReprExpr(c: blackbox.Context)(
    tpe: c.Type,
    typeArgs: List[c.Type],
    typeParams: List[c.Symbol]
  ): c.Tree = {
    import c.universe._
    
    // Check if it's a type parameter reference
    typeParams.indexWhere(_.name == tpe.typeSymbol.name) match {
      case -1 =>
        // Not a parameter reference, build a Ref
        val symbol = tpe.typeSymbol
        val name = symbol.name.decodedName.toString
        val ownerSegments = buildOwnerSegments(c)(symbol.owner)
        val ownerExpr = buildOwnerExpr(c)(ownerSegments)
        
        if (tpe.typeArgs.isEmpty) {
          q"""
            new _root_.zio.blocks.typeid.TypeRepr.Ref(
              _root_.zio.blocks.typeid.TypeId.nominal[$tpe]($name, $ownerExpr, _root_.scala.Nil)
            )
          """
        } else {
          val argExprs = tpe.typeArgs.map(arg => buildTypeReprExpr(c)(arg, typeArgs, typeParams))
          val tyconTypeParamExprs = tpe.typeSymbol.asType.typeParams.zipWithIndex.map { case (p, i) =>
            q"new _root_.zio.blocks.typeid.TypeParam(${p.name.decodedName.toString}, $i)"
          }
          q"""
            new _root_.zio.blocks.typeid.TypeRepr.Applied(
              new _root_.zio.blocks.typeid.TypeRepr.Ref(
                _root_.zio.blocks.typeid.TypeId.nominal[Any]($name, $ownerExpr, _root_.scala.List(..$tyconTypeParamExprs))
              ),
              _root_.scala.List(..$argExprs)
            )
          """
        }
      case idx =>
        // It's a reference to a type parameter
        val paramName = typeParams(idx).name.decodedName.toString
        q"""
          new _root_.zio.blocks.typeid.TypeRepr.ParamRef(
            new _root_.zio.blocks.typeid.TypeParam($paramName, $idx)
          )
        """
    }
  }
}

trait TypeIdVersionSpecific {
  def derive[A]: TypeId[A] = macro TypeIdMacros.deriveMacro[A]
}
