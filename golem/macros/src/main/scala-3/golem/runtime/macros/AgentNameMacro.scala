package golem.runtime.macros

import scala.quoted.*

object AgentNameMacro {
  inline def typeName[T]: String =
    ${ typeNameImpl[T] }

  private def typeNameImpl[T: Type](using Quotes): Expr[String] = {
    import quotes.reflect.*
    val sym = TypeRepr.of[T].typeSymbol

    def defaultTypeNameFromTrait(sym: Symbol): String =
      sym.name
        .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
        .toLowerCase

    def extractAgentDefinitionTypeName(args: List[Term]): Option[String] =
      args.collectFirst {
        case Literal(StringConstant(value))                       => value
        case NamedArg("typeName", Literal(StringConstant(value))) => value
      }

    val maybe = sym.annotations.collectFirst {
      case Apply(Select(New(tpt), _), args)
          if tpt.tpe.dealias.typeSymbol.fullName == "golem.runtime.annotations.agentDefinition" =>
        extractAgentDefinitionTypeName(args)
    }.flatten

    maybe match {
      case Some(value) if value.trim.nonEmpty => Expr(value)
      case _                                  =>
        // If @agentDefinition is present but typeName was omitted/empty, derive a stable default.
        // This keeps user code minimal while still requiring an explicit marker annotation.
        val hasAnn =
          sym.annotations.exists {
            case Apply(Select(New(tpt), _), _)
                if tpt.tpe.dealias.typeSymbol.fullName == "golem.runtime.annotations.agentDefinition" =>
              true
            case _ => false
          }
        if !hasAnn then report.errorAndAbort(s"Missing @agentDefinition(...) on agent trait: ${sym.fullName}")
        Expr(defaultTypeNameFromTrait(sym))
    }
  }

}
