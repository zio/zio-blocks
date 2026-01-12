package golem.runtime.macros

// Macro annotations live in a separate module; do not depend on them here.
import golem.runtime.agenttype.AgentType
import golem.AgentApi

import scala.quoted.*

object AgentSdkMacro {
  transparent inline def derived[Trait]: AgentApi[Trait] =
    ${ derivedImpl[Trait] }

  private def derivedImpl[Trait: Type](using Quotes): Expr[AgentApi[Trait]] = {
    import quotes.reflect.*

    val traitSym = TypeRepr.of[Trait].typeSymbol

    def defaultTypeNameFromTrait(sym: Symbol): String = {
      val raw = sym.name
      raw
        .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
        .toLowerCase
    }

    def extractTypeNameFromAgentDefinition(sym: Symbol): Option[String] =
      sym.annotations.collectFirst {
        case Apply(Select(New(tpt), _), args)
            if tpt.tpe.typeSymbol.fullName == "golem.runtime.annotations.agentDefinition" =>
          args.collectFirst {
            case Literal(StringConstant(value))                       => value
            case NamedArg("typeName", Literal(StringConstant(value))) => value
          }.map(_.trim).filter(_.nonEmpty)
      }.flatten

    val hasAnn =
      traitSym.annotations.exists {
        case Apply(Select(New(tpt), _), _)
            if tpt.tpe.typeSymbol.fullName == "golem.runtime.annotations.agentDefinition" =>
          true
        case _ => false
      }

    val typeName =
      extractTypeNameFromAgentDefinition(traitSym).getOrElse {
        if !hasAnn then report.errorAndAbort(s"Missing @agentDefinition(...) on agent trait: ${traitSym.fullName}")
        defaultTypeNameFromTrait(traitSym)
      }
    val typeNameExpr = Expr(typeName)

    val ctorTypeRepr =
      traitSym.typeMembers.find(_.name == "AgentInput") match {
        case Some(typeSym) =>
          TypeRepr.of[Trait].memberType(typeSym) match {
            case TypeBounds(_, hi) => hi
            case other             => other
          }
        case None =>
          TypeRepr.of[Unit]
      }

    ctorTypeRepr.asType match {
      case '[ctor] =>
        '{
          new AgentApi[Trait] {
            override type Constructor = ctor
            override val typeName: String                  = $typeNameExpr
            override val agentType: AgentType[Trait, ctor] =
              golem.runtime.macros.AgentClientMacro
                .agentType[Trait]
                .asInstanceOf[AgentType[Trait, ctor]]
          }
        }
    }
  }
}
