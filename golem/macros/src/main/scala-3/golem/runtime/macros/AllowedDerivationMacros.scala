package golem.runtime.macros

import golem.data.unstructured.{AllowedLanguages, AllowedMimeTypes}
import golem.runtime.annotations.{languageCode, mimeType}
import scala.quoted.*

object AllowedLanguagesDerivation {
  inline def derived[A]: AllowedLanguages[A] =
    ${ AllowedLanguagesDerivationMacro.derive[A] }

  inline given autoDerived[A]: AllowedLanguages[A] = derived[A]
}

object AllowedMimeTypesDerivation {
  inline def derived[A]: AllowedMimeTypes[A] =
    ${ AllowedMimeTypesDerivationMacro.derive[A] }

  inline given autoDerived[A]: AllowedMimeTypes[A] = derived[A]
}

private object AllowedLanguagesDerivationMacro {
  def derive[A: Type](using Quotes): Expr[AllowedLanguages[A]] = {
    import quotes.reflect.*
    val symbol = TypeRepr.of[A].typeSymbol
    val codes  = collectEntries(symbol, TypeRepr.of[languageCode], _.toLowerCase.replace('_', '-'))
    if codes.isEmpty then
      report.errorAndAbort(
        s"AllowedLanguages can only be derived for enums or sealed traits with cases, found: ${symbol.fullName}"
      )
    val codesExpr = Expr.ofList(codes.map(Expr(_)))
    '{
      new AllowedLanguages[A] {
        override val codes: Option[List[String]] = Some($codesExpr)
      }
    }
  }
}

private object AllowedMimeTypesDerivationMacro {
  def derive[A: Type](using Quotes): Expr[AllowedMimeTypes[A]] = {
    import quotes.reflect.*
    val symbol = TypeRepr.of[A].typeSymbol
    val codes  = collectEntries(symbol, TypeRepr.of[mimeType], identity)
    if codes.isEmpty then
      report.errorAndAbort(
        s"AllowedMimeTypes can only be derived for enums or sealed traits with cases, found: ${symbol.fullName}"
      )
    val codesExpr = Expr.ofList(codes.map(Expr(_)))
    '{
      new AllowedMimeTypes[A] {
        override val mimeTypes: Option[List[String]] = Some($codesExpr)
      }
    }
  }
}

private def collectEntries(using
  Quotes
)(
  symbol: quotes.reflect.Symbol,
  annotationType: quotes.reflect.TypeRepr,
  defaultTransform: String => String
): List[String] = {
  val children = symbol.children
  if children.isEmpty then Nil
  else {
    children.map { child =>
      annotationValue(child, annotationType).getOrElse(defaultTransform(child.name))
    }
  }
}

private def annotationValue(using
  Quotes
)(
  symbol: quotes.reflect.Symbol,
  annotationType: quotes.reflect.TypeRepr
): Option[String] = {
  import quotes.reflect.*
  symbol.annotations.collectFirst {
    case Apply(Select(New(tpt), _), List(Literal(StringConstant(value)))) if tpt.tpe =:= annotationType =>
      value
  }
}
