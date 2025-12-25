package zio.blocks.schema

import scala.quoted.*

object AsMacros {
  inline def derive[A, B]: As[A, B] = ${ deriveImpl[A, B] }

  def deriveImpl[A: Type, B: Type](using Quotes): Expr[As[A, B]] = {
    import quotes.reflect.*

    if (TypeRepr.of[A] =:= TypeRepr.of[B]) {
      return '{ As.identity[A].asInstanceOf[As[A, B]] }
    }

    val toExpr   = IntoMacros.deriveImpl[A, B]
    val fromExpr = IntoMacros.deriveImpl[B, A]

    '{
      new As[A, B] {
        private val forward  = $toExpr
        private val backward = $fromExpr

        def to(a: A): Either[SchemaError, B]   = forward.into(a)
        def from(b: B): Either[SchemaError, A] = backward.into(b)
      }
    }
  }
}
