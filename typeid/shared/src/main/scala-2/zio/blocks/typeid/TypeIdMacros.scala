package zio.blocks.typeid

import scala.reflect.macros.blackbox

private[typeid] object TypeIdMacros {
  def deriveImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] = {
    import c.universe._
    
    val tpe = weakTypeOf[A]
    val typeSymbol = tpe.typeSymbol
    
    val ownerTree = extractOwner(c)(typeSymbol.owner)
    val typeParamsTree = extractTypeParams(c)(tpe)
    
    // Check if it's a type alias
    if (typeSymbol.isType && typeSymbol.asType.isAliasType) {
      val underlying = tpe.dealias
      if (underlying =:= tpe) {
        // Not really an alias
        c.Expr[TypeId[A]](
          q"""_root_.zio.blocks.typeid.TypeId.nominal[${tpe}](
            ${typeSymbol.name.toString},
            $ownerTree,
            $typeParamsTree
          )"""
        )
      } else {
        val underlyingTree = extractTypeRepr(c)(underlying)
        c.Expr[TypeId[A]](
          q"""_root_.zio.blocks.typeid.TypeId.alias[${tpe}](
            ${typeSymbol.name.toString},
            $ownerTree,
            $typeParamsTree,
            $underlyingTree
          )"""
        )
      }
    } else {
      c.Expr[TypeId[A]](
        q"""_root_.zio.blocks.typeid.TypeId.nominal[${tpe}](
          ${typeSymbol.name.toString},
          $ownerTree,
          $typeParamsTree
        )"""
      )
    }
  }
  
  private def extractOwner(c: blackbox.Context)(symbol: c.Symbol): c.Tree = {
    import c.universe._
    
    def loop(sym: Symbol, acc: List[Tree]): List[Tree] = {
      if (sym == c.universe.rootMirror.RootPackage || sym == c.universe.rootMirror.RootClass) {
        acc
      } else {
        val name = sym.name.toString
        val segment: Tree =
          if (sym.isPackage) {
            q"_root_.zio.blocks.typeid.Owner.Segment.Package($name)"
          } else if (sym.isModuleClass || sym.isModule) {
            val cleanName = if (name.endsWith("$")) name.dropRight(1) else name
            q"_root_.zio.blocks.typeid.Owner.Segment.Term($cleanName)"
          } else {
            q"_root_.zio.blocks.typeid.Owner.Segment.Type($name)"
          }
        loop(sym.owner, segment :: acc)
      }
    }
    
    val segments = loop(symbol, Nil).reverse
    q"_root_.zio.blocks.typeid.Owner(_root_.scala.collection.immutable.List(..$segments))"
  }
  
  private def extractTypeParams(c: blackbox.Context)(tpe: c.Type): c.Tree = {
    import c.universe._
    
    val typeParams = tpe.typeSymbol.asType.typeParams
    val paramTrees = typeParams.zipWithIndex.map { case (sym, idx) =>
      q"_root_.zio.blocks.typeid.TypeParam(${sym.name.toString}, $idx)"
    }
    q"_root_.scala.collection.immutable.List(..$paramTrees)"
  }
  
  private def extractTypeRepr(c: blackbox.Context)(tpe: c.Type): c.Tree = {
    import c.universe._
    
    tpe match {
      case TypeRef(_, _, args) if args.nonEmpty =>
        val tyconTree = q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.derive[${tpe.typeConstructor}])"
        val argTrees = args.map(extractTypeRepr(c))
        q"_root_.zio.blocks.typeid.TypeRepr.Applied($tyconTree, _root_.scala.collection.immutable.List(..$argTrees))"
      
      case _ if tpe =:= c.universe.typeOf[Any] =>
        q"_root_.zio.blocks.typeid.TypeRepr.AnyType"
      
      case _ if tpe =:= c.universe.typeOf[Nothing] =>
        q"_root_.zio.blocks.typeid.TypeRepr.NothingType"

      case ConstantType(Constant(value)) =>
        // Use Expr to properly lift the value
        val valueLiteral = Literal(Constant(value))
        q"_root_.zio.blocks.typeid.TypeRepr.Constant($valueLiteral)"

      case _ =>
        q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.derive[$tpe])"
    }
  }
}
