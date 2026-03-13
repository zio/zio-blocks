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

    def defaultTypeNameFromTrait(sym: Symbol): String =
      sym.name
        .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
        .toLowerCase

    def extractTypeNameFromAgentDefinition(sym: Symbol): Option[String] =
      sym.annotations.collectFirst {
        case Apply(Select(New(tpt), _), args)
            if tpt.tpe.dealias.typeSymbol.fullName == "golem.runtime.annotations.agentDefinition" =>
          args.collectFirst {
            case Literal(StringConstant(value))                       => value
            case NamedArg("typeName", Literal(StringConstant(value))) => value
          }.map(_.trim).filter(_.nonEmpty)
      }.flatten

    val hasAnn =
      traitSym.annotations.exists {
        case Apply(Select(New(tpt), _), _)
            if tpt.tpe.dealias.typeSymbol.fullName == "golem.runtime.annotations.agentDefinition" =>
          true
        case _ => false
      }

    val typeName =
      extractTypeNameFromAgentDefinition(traitSym).map(validateTypeName).getOrElse {
        if !hasAnn then report.errorAndAbort(s"Missing @agentDefinition(...) on agent trait: ${traitSym.fullName}")
        defaultTypeNameFromTrait(traitSym)
      }
    val typeNameExpr = Expr(typeName)

    val ctorTypeRepr = {
      val traitRepr = TypeRepr.of[Trait]
      val baseSym   = traitRepr.baseClasses.find(_.fullName == "golem.BaseAgent").getOrElse(Symbol.noSymbol)
      if (baseSym == Symbol.noSymbol) TypeRepr.of[Unit]
      else
        traitRepr.baseType(baseSym) match {
          case AppliedType(_, List(arg)) => arg
          case _                         => TypeRepr.of[Unit]
        }
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

  private def validateTypeName(value: String): String =
    value
}
