package zio.blocks.context

import zio.blocks.chunk.Chunk
import zio.blocks.typeid.TypeId
import scala.quoted.*

private[context] trait IsNominalIntersectionVersionSpecific {
  inline implicit def derived[A]: IsNominalIntersection[A] = ${ IsNominalIntersectionMacros.deriveImpl[A] }
}

private[context] object IsNominalIntersectionMacros {
  def deriveImpl[A: Type](using Quotes): Expr[IsNominalIntersection[A]] = {
    import quotes.reflect.*

    def flattenIntersection(tpe: TypeRepr): List[TypeRepr] = tpe.dealias match {
      case AndType(left, right) =>
        flattenIntersection(left) ++ flattenIntersection(right)
      case other => List(other)
    }

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

    val tpe     = TypeRepr.of[A]
    val members = flattenIntersection(tpe)

    members.foreach { member =>
      if (!isNominal(member)) {
        report.errorAndAbort(
          s"Cannot derive IsNominalIntersection: member type ${member.show} is not a nominal type. " +
            "All intersection members must be classes, traits, objects, enums, or applied types."
        )
      }
    }

    val typeIdExprs: List[Expr[TypeId.Erased]] = members.map { member =>
      member.asType match {
        case '[t] =>
          '{ TypeId.of[t].erased }
      }
    }

    val listExpr = Expr.ofList(typeIdExprs)

    '{
      new IsNominalIntersection[A] {
        val typeIdsErased: Chunk[TypeId.Erased] = Chunk.fromIterable($listExpr)
      }
    }
  }
}
