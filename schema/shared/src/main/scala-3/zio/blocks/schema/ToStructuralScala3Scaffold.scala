package zio.blocks.schema

import scala.quoted.*

private[schema] object ToStructuralScala3Scaffold {

  inline def derivedFallback[A]: ToStructural.Aux[A, DynamicValue] =
    ${ derivedFallbackImpl[A] }

  private def derivedFallbackImpl[A: Type](using q: Quotes): Expr[ToStructural.Aux[A, DynamicValue]] = {

    // Current safe implementation: delegate to the version-specific derived given
    // TODO: replace with real typed structural generation using quotes.reflect
    '{ zio.blocks.schema.ToStructuralVersionSpecific.derived[A] }
  }
}
