package zio.blocks.typeid

import scala.quoted._

/**
 * Scala 3.5+ macro implementations for TypeId derivation.
 */
object TypeIdMacros {
  
  inline def derive[A]: TypeId[A] = ${ deriveMacroImpl[A] }
  
  private def deriveMacroImpl[A: Type](using Quotes): Expr[TypeId[A]] = {
    import quotes.reflect._
    
    val tpe = TypeRepr.of[A]
    val symbol = tpe.typeSymbol
    val name = symbol.name
    
    // Build owner path
    val ownerSegments = buildOwnerSegments(symbol.owner)
    val ownerExpr = buildOwnerExpr(ownerSegments)
    
    // Build type parameters
    val typeParamsExpr = buildTypeParamsExpr(symbol.typeMembers.filter(_.isTypeParam))
    
    // Detect opaque types (Scala 3 specific)
    val isOpaque = symbol.flags.is(Flags.Opaque)
    val isAlias = tpe match {
      case TypeRef(_, _) => false
      case TypeBounds(_, _) => false
      case _ => 
        // Check if dealiased type is different
        tpe.dealias != tpe && !isOpaque
    }
    
    if (isOpaque) {
      // Opaque type: opaque type Email = String
      val reprType = tpe.dealias
      val reprExpr = buildTypeReprExpr(reprType)
      '{
        TypeId.opaque[A](
          ${Expr(name)},
          ${ownerExpr},
          ${typeParamsExpr},
          ${reprExpr}
        )
      }
    } else if (isAlias) {
      // Type alias
      val aliasedType = tpe.dealias
      val aliasedExpr = buildTypeReprExpr(aliasedType)
      '{
        TypeId.alias[A](
          ${Expr(name)},
          ${ownerExpr},
          ${typeParamsExpr},
          ${aliasedExpr}
        )
      }
    } else {
      // Nominal type
      '{
        TypeId.nominal[A](
          ${Expr(name)},
          ${ownerExpr},
          ${typeParamsExpr}
        )
      }
    }
  }
  
  private def buildOwnerSegments(using Quotes)(owner: quotes.reflect.Symbol): List[(String, String)] = {
    import quotes.reflect._
    
    if (owner.isNoSymbol || owner.isPackageDef || owner == defn.RootClass) {
      Nil
    } else {
      val name = owner.name
      val kind = 
        if (owner.isPackageDef) "Package"
        else if (owner.flags.is(Flags.Module)) "Term"
        else "Type"
      buildOwnerSegments(owner.owner) :+ (kind, name)
    }
  }
  
  private def buildOwnerExpr(using Quotes)(segments: List[(String, String)]): Expr[Owner] = {
    import quotes.reflect._
    
    if (segments.isEmpty) {
      '{ Owner.Root }
    } else {
      val segmentExprs: List[Expr[Owner.Segment]] = segments.map { case (kind, name) =>
        kind match {
          case "Package" => '{ Owner.Package(${Expr(name)}) }
          case "Term"    => '{ Owner.Term(${Expr(name)}) }
          case "Type"    => '{ Owner.Type(${Expr(name)}) }
        }
      }
      '{ Owner(${Expr.ofList(segmentExprs)}) }
    }
  }
  
  private def buildTypeParamsExpr(using Quotes)(typeParams: List[quotes.reflect.Symbol]): Expr[List[TypeParam]] = {
    import quotes.reflect._
    
    val paramExprs: List[Expr[TypeParam]] = typeParams.zipWithIndex.map { case (param, idx) =>
      val paramName = param.name
      '{ TypeParam(${Expr(paramName)}, ${Expr(idx)}) }
    }
    Expr.ofList(paramExprs)
  }
  
  private def buildTypeReprExpr(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[TypeRepr] = {
    import quotes.reflect._
    
    tpe match {
      case AppliedType(tycon, args) =>
        val tyconExpr = buildTypeReprExpr(tycon)
        val argsExpr = Expr.ofList(args.map(buildTypeReprExpr))
        '{ TypeRepr.Applied(${tyconExpr}, ${argsExpr}) }
        
      case TypeRef(qualifier, name) =>
        val symbol = tpe.typeSymbol
        val ownerSegments = buildOwnerSegments(symbol.owner)
        val ownerExpr = buildOwnerExpr(ownerSegments)
        val typeParamsExpr = buildTypeParamsExpr(symbol.typeMembers.filter(_.isTypeParam))
        '{ TypeRepr.Ref(TypeId.nominal[Any](${Expr(name)}, ${ownerExpr}, ${typeParamsExpr})) }
        
      case ParamRef(binder, idx) =>
        '{ TypeRepr.ParamRef(TypeParam("?", ${Expr(idx)})) }
        
      case other =>
        // Fallback for other types
        val name = other.typeSymbol.name
        val ownerSegments = buildOwnerSegments(other.typeSymbol.owner)
        val ownerExpr = buildOwnerExpr(ownerSegments)
        '{ TypeRepr.Ref(TypeId.nominal[Any](${Expr(name)}, ${ownerExpr}, Nil)) }
    }
  }
}

trait TypeIdVersionSpecific {
  inline def derive[A]: TypeId[A] = TypeIdMacros.derive[A]
}
