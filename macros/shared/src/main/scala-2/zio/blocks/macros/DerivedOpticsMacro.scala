package zio.blocks.macros

import scala.reflect.macros.whitebox
import zio.optics._

object DerivedOpticsMacro {
  def impl[T: c.WeakTypeTag](c: whitebox.Context)(underscore: c.Expr[Boolean]): c.Expr[Any] = {
    import c.universe._

    val tpe = weakTypeOf[T]
    val underscoreValue = underscore.tree match {
      case Literal(Constant(b: Boolean)) => b
      case _ => false
    }

    val symbol = tpe.typeSymbol

    if (symbol.isClass && symbol.asClass.isCaseClass) {
      generateCaseClassOptics(c)(tpe, underscoreValue)
    } else if (symbol.isClass && symbol.asClass.isSealed) {
      generateSealedTraitOptics(c)(tpe, underscoreValue)
    } else {
      c.abort(c.enclosingPosition, s"DerivedOptics only supports Case Classes or Sealed Traits. Found: $symbol")
    }
  }

  private def generateCaseClassOptics(c: whitebox.Context)(tpe: c.Type, underscore: Boolean): c.Expr[Any] = {
    import c.universe._

    val fields = tpe.decls.collect {
      case m: MethodSymbol if m.isCaseAccessor => m
    }.toList

    val defs = fields.map { field =>
      val fieldName = field.name
      val accessorName = TermName(if (underscore) "_" + fieldName.toString else fieldName.toString)
      val fieldTpe = field.returnType.asSeenFrom(tpe, tpe.typeSymbol)

      q"""
        val $accessorName: zio.optics.Lens[$tpe, $fieldTpe] = 
          zio.optics.Lens(
            s => scala.util.Right(s.$fieldName),
            v => s => scala.util.Right(s.copy($fieldName = v))
          )
      """
    }


    val anon = q"""
      new {
        ..$defs
      }
    """
    c.Expr[Any](anon)
  }

  private def generateSealedTraitOptics(c: whitebox.Context)(tpe: c.Type, underscore: Boolean): c.Expr[Any] = {
    import c.universe._
    
    val children = tpe.typeSymbol.asClass.knownDirectSubclasses.toList

    val defs = children.map { child =>
      val childName = child.name.toString
      val lowerName = childName.head.toLower + childName.tail
      val accessorName = TermName(if (underscore) "_" + lowerName else lowerName)
      
      val childTpe = if (child.asClass.typeParams.nonEmpty && tpe.typeArgs.nonEmpty) {
         appliedType(child, tpe.typeArgs)
      } else {
         child.asType.toType
      }

      q"""
        val $accessorName: zio.optics.Prism[$tpe, $childTpe] = 
          zio.optics.Prism(
            {
              case x: $childTpe => scala.util.Right(x)
              case _ => scala.util.Left(zio.optics.OpticFailure("Not a match"))
            },
            (x: $childTpe) => x
          )
      """
    }

    val anon = q"""
      new {
        ..$defs
      }
    """
    c.Expr[Any](anon)
  }
}