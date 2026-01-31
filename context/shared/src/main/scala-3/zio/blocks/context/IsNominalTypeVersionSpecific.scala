package zio.blocks.context

import zio.blocks.typeid.TypeId
import scala.quoted.*

private[context] trait IsNominalTypeVersionSpecific {
  inline implicit def derived[A]: IsNominalType[A] = ${ IsNominalTypeMacros.deriveImpl[A] }
}

private[context] object IsNominalTypeMacros {
  def deriveImpl[A: Type](using Quotes): Expr[IsNominalType[A]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[A].dealias

    def isNominal(t: TypeRepr): Boolean = t match {
      case AndType(_, _)                => false
      case OrType(_, _)                 => false
      case Refinement(_, _, _)          => false
      case TypeLambda(_, _, _)          => false
      case ByNameType(_)                => false
      case TypeBounds(_, _)             => false
      case NoPrefix()                   => false
      case MatchType(_, _, _)           => false
      case AppliedType(tycon, _)        => isNominal(tycon)
      case TermRef(_, _)                => true
      case TypeRef(_, _)                => true
      case ConstantType(_)              => false
      case ParamRef(_, _)               => false
      case ThisType(_)                  => true
      case RecursiveThis(_)             => false
      case RecursiveType(underlying)    => isNominal(underlying)
      case AnnotatedType(underlying, _) => isNominal(underlying)
      case _                            => false
    }

    if (!isNominal(tpe)) {
      val typeName = tpe.show
      report.errorAndAbort(
        s"Cannot derive IsNominalType for non-nominal type: $typeName. " +
          "Only classes, traits, objects, enums, and applied types are supported. " +
          "Intersection types (A & B), union types (A | B), structural types, and type lambdas are not allowed."
      )
    }

    '{
      new IsNominalType[A] {
        val typeId: TypeId[A]           = TypeId.of[A]
        val typeIdErased: TypeId.Erased = typeId.erased
      }
    }
  }
}
