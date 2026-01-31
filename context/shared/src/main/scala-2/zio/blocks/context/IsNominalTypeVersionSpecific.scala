package zio.blocks.context

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

private[context] trait IsNominalTypeVersionSpecific {
  implicit def derived[A]: IsNominalType[A] = macro IsNominalTypeMacros.deriveImpl[A]
}

private[context] object IsNominalTypeMacros {
  def deriveImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[IsNominalType[A]] = {
    import c.universe._

    val tpe = weakTypeOf[A].dealias

    def isNominal(t: Type): Boolean = t match {
      case RefinedType(_, scope) if scope.nonEmpty => false
      case ExistentialType(_, _)                   => false
      case PolyType(_, _)                          => false
      case TypeBounds(_, _)                        => false
      case ConstantType(_)                         => false
      case t if t.takesTypeArgs                    => true
      case t if t.typeSymbol.isClass               => true
      case t if t.typeSymbol.isModuleClass         => true
      case _                                       => false
    }

    val dealiased      = tpe.dealias
    val isIntersection = dealiased match {
      case RefinedType(parents, _) if parents.size > 1 => true
      case _                                           => false
    }

    if (isIntersection || !isNominal(dealiased)) {
      c.abort(
        c.enclosingPosition,
        s"Cannot derive IsNominalType for non-nominal type: $tpe. " +
          "Only classes, traits, objects, and applied types are supported. " +
          "Intersection types (A with B), structural types, and type lambdas are not allowed."
      )
    }

    c.Expr[IsNominalType[A]](q"""
      new _root_.zio.blocks.context.IsNominalType[$tpe] {
        val typeId: _root_.zio.blocks.typeid.TypeId[$tpe] = _root_.zio.blocks.typeid.TypeId.of[$tpe]
        val typeIdErased: _root_.zio.blocks.typeid.TypeId.Erased = typeId.erased
      }
    """)
  }
}
