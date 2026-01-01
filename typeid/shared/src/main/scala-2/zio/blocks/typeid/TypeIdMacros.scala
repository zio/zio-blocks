package zio.blocks.typeid

import scala.reflect.macros.blackbox

object TypeIdMacros {
  def deriveMacro[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] = {
    import c.universe._

    val tpe = weakTypeOf[A]
    val symbol = tpe.typeSymbol

    def extractOwner(s: Symbol): Owner = {
      def loop(curr: Symbol): List[Owner.Segment] = {
        if (curr == NoSymbol || curr.isPackage && curr.name.decodedName.toString == "<root>") Nil
        else {
          val segment = if (curr.isPackage) Owner.Package(curr.name.decodedName.toString)
                        else if (curr.isTerm) Owner.Term(curr.name.decodedName.toString)
                        else Owner.Type(curr.name.decodedName.toString)
          loop(curr.owner) :+ segment
        }
      }
      Owner(loop(s.owner))
    }

    val name = symbol.name.decodedName.toString
    val owner = extractOwner(symbol)
    
    val typeParams = tpe.typeArgs.zipWithIndex.map { case (arg, idx) =>
      val pName = arg.typeSymbol.name.decodedName.toString
      q"_root_.zio.blocks.typeid.TypeParam($pName, $idx)"
    }

    // This is a simplified version of the logic. 
    // For a real implementation, we'd want to handle aliases and opaque types too.
    // For now, focusing on nominal types.
    
    val ownerSegments = owner.segments.map {
      case Owner.Package(n) => q"_root_.zio.blocks.typeid.Owner.Package($n)"
      case Owner.Term(n)    => q"_root_.zio.blocks.typeid.Owner.Term($n)"
      case Owner.Type(n)    => q"_root_.zio.blocks.typeid.Owner.Type($n)"
    }
    
    val ownerTree = q"_root_.zio.blocks.typeid.Owner(List(..$ownerSegments))"

    c.Expr[TypeId[A]](q"_root_.zio.blocks.typeid.TypeId.nominal[$tpe]($name, $ownerTree, List(..$typeParams))")
  }
}
