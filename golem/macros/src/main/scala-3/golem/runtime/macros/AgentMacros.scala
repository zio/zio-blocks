package golem.runtime.macros

import golem.data.{ElementSchema, GolemSchema, NamedElementSchema, StructuredSchema}
import golem.runtime.{AgentMetadata, MethodMetadata}
import golem.runtime.annotations.{DurabilityMode, description, mode, prompt}

import scala.quoted.*

object AgentMacros {
  inline def agentMetadata[T]: AgentMetadata =
    ${ agentMetadataImpl[T] }

  private def agentMetadataImpl[T: Type](using Quotes): Expr[AgentMetadata] = {
    import quotes.reflect.*

    val typeRepr   = TypeRepr.of[T]
    val typeSymbol = typeRepr.typeSymbol
    val typeName   = typeSymbol.fullName

    if !typeSymbol.flags.is(Flags.Trait) then report.errorAndAbort(s"@agent target must be a trait, found: $typeName")

    val traitDescription = annotationString(typeSymbol, TypeRepr.of[description])
    val traitMode        = annotationMode(typeSymbol, TypeRepr.of[mode])

    val methods = typeSymbol.methodMembers.collect {
      case method if method.flags.is(Flags.Deferred) =>
        val methodName = method.name
        val descExpr   = optionalString(annotationString(method, TypeRepr.of[description]))
        val promptExpr = optionalString(annotationString(method, TypeRepr.of[prompt]))
        val modeExpr   = optionalExprString(annotationMode(method, TypeRepr.of[mode]))

        val inputSchemaExpr  = methodInputSchema(method)
        val outputSchemaExpr = methodOutputSchema(method)

        '{
          MethodMetadata(
            name = ${ Expr(methodName) },
            description = $descExpr,
            prompt = $promptExpr,
            mode = $modeExpr,
            input = $inputSchemaExpr,
            output = $outputSchemaExpr
          )
        }
    }

    val methodsExpr = Expr.ofList(methods)

    val ctorSchema = constructorSchemaFromAgentInput(typeRepr, typeSymbol)

    '{
      AgentMetadata(
        name = ${ Expr(typeName) },
        description = ${ optionalString(traitDescription) },
        mode = ${ optionalExprString(traitMode) },
        methods = $methodsExpr,
        constructor = $ctorSchema
      )
    }
  }

  private def constructorSchemaFromAgentInput(using
    Quotes
  )(
    traitRepr: quotes.reflect.TypeRepr,
    traitSymbol: quotes.reflect.Symbol
  ): Expr[StructuredSchema] = {
    import quotes.reflect.*
    traitSymbol.typeMembers.find(_.name == "AgentInput") match {
      case Some(typeSym) =>
        val tpe = traitRepr.memberType(typeSym) match {
          case TypeBounds(_, hi) => hi
          case other             => other
        }
        if (tpe =:= TypeRepr.of[Unit]) '{ StructuredSchema.Tuple(Nil) } else structuredSchemaExpr(tpe)
      case None =>
        '{ StructuredSchema.Tuple(Nil) }
    }
  }

  private def methodInputSchema(using Quotes)(method: quotes.reflect.Symbol): Expr[StructuredSchema] = {
    import quotes.reflect.*

    val params = method.paramSymss.flatten.collect {
      case sym if sym.isTerm =>
        sym.tree match {
          case v: ValDef => (sym.name, v.tpt.tpe)
          case other     => report.errorAndAbort(s"Unsupported parameter declaration in ${method.name}: $other")
        }
    }

    if params.isEmpty then '{ StructuredSchema.Tuple(Nil) }
    else if params.length == 1 then {
      val (_, paramType) = params.head
      structuredSchemaExpr(paramType)
    } else {
      val elements = params.map { case (name, tpe) =>
        val schemaExpr = elementSchemaExpr(name, tpe)
        '{ NamedElementSchema(${ Expr(name) }, $schemaExpr) }
      }
      val listExpr = Expr.ofList(elements)
      '{ StructuredSchema.Tuple($listExpr) }
    }
  }

  private def elementSchemaExpr(using Quotes)(paramName: String, tpe: quotes.reflect.TypeRepr): Expr[ElementSchema] = {
    import quotes.reflect.*

    tpe.asType match {
      case '[t] =>
        Expr.summon[GolemSchema[t]] match {
          case Some(schemaExpr) =>
            '{
              $schemaExpr.schema match {
                case StructuredSchema.Tuple(elements) if elements.length == 1 =>
                  elements.head.schema
                case StructuredSchema.Tuple(_) =>
                  throw new IllegalArgumentException(
                    s"Parameter ${${ Expr(paramName) }} expands to multiple elements; wrap it in a case class"
                  )
                case StructuredSchema.Multimodal(_) =>
                  throw new IllegalArgumentException(
                    s"Parameter ${${ Expr(paramName) }} is multimodal; use a single parameter of the multimodal wrapper type"
                  )
              }
            }
          case None =>
            report.errorAndAbort(s"No implicit GolemSchema available for type ${Type.show[t]}")
        }
    }
  }

  private def methodOutputSchema(using Quotes)(method: quotes.reflect.Symbol): Expr[StructuredSchema] = {
    import quotes.reflect.*
    method.tree match {
      case d: DefDef =>
        structuredSchemaExpr(d.returnTpt.tpe)
      case other =>
        report.errorAndAbort(s"Unable to read return type for ${method.name}: $other")
    }
  }

  private def structuredSchemaExpr(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[StructuredSchema] = {
    import quotes.reflect.*

    tpe.asType match {
      case '[t] =>
        Expr.summon[GolemSchema[t]] match {
          case Some(schemaExpr) =>
            '{ $schemaExpr.schema }
          case None =>
            report.errorAndAbort(s"No implicit GolemSchema available for type ${Type.show[t]}")
        }
    }
  }

  private def annotationString(using
    Quotes
  )(symbol: quotes.reflect.Symbol, annType: quotes.reflect.TypeRepr): Option[String] = {
    import quotes.reflect.*
    symbol.annotations.collectFirst {
      case Apply(Select(New(tpt), _), List(Literal(StringConstant(value)))) if tpt.tpe =:= annType =>
        value
    }
  }

  private def annotationMode(using
    Quotes
  )(symbol: quotes.reflect.Symbol, annType: quotes.reflect.TypeRepr): Option[Expr[String]] = {
    import quotes.reflect.*
    symbol.annotations.collectFirst {
      case Apply(Select(New(tpt), _), List(arg)) if tpt.tpe =:= annType =>
        arg match {
          case Literal(StringConstant(value)) =>
            // (Legacy) allow stringly-typed annotations if present.
            Expr(value)
          case term: Term =>
            val dm = term.asExprOf[DurabilityMode]
            '{ $dm.wireValue() }
        }
    }
  }

  private def optionalString(using Quotes)(value: Option[String]): Expr[Option[String]] =
    value match {
      case Some(v) => '{ Some(${ Expr(v) }) }
      case None    => '{ None }
    }

  private def optionalExprString(using Quotes)(value: Option[Expr[String]]): Expr[Option[String]] =
    value match {
      case Some(v) => '{ Some($v) }
      case None    => '{ None }
    }
}
