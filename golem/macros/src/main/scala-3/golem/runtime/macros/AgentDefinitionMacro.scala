package golem.runtime.macros

import golem.data.{GolemSchema, NamedElementSchema, StructuredSchema}
import golem.runtime.annotations.{description, prompt}
import golem.runtime.{AgentMetadata, MethodMetadata}

import scala.quoted.*

object AgentDefinitionMacro {
  private val schemaHint: String =
    "\nHint: GolemSchema is derived from zio.blocks.schema.Schema.\n" +
      "Define or import an implicit Schema[T] for your type.\n" +
      "Scala 3: `final case class T(...) derives zio.blocks.schema.Schema` (or `given Schema[T] = Schema.derived`).\n" +
      "Scala 2: `implicit val schema: zio.blocks.schema.Schema[T] = zio.blocks.schema.Schema.derived`.\n"
  inline def generate[T]: AgentMetadata = ${ impl[T] }

  private def impl[T: Type](using Quotes): Expr[AgentMetadata] = {
    import quotes.reflect.*

    val typeRepr   = TypeRepr.of[T]
    val typeSymbol = typeRepr.typeSymbol

    if !typeSymbol.flags.is(Flags.Trait) then
      report.errorAndAbort(s"@agent target must be a trait, found: ${typeSymbol.fullName}")

    def defaultTypeNameFromTrait(sym: Symbol): String =
      sym.name
        .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
        .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
        .toLowerCase

    val hasAgentDefinition =
      typeSymbol.annotations.exists {
        case Apply(Select(New(tpt), _), _)
            if tpt.tpe.dealias.typeSymbol.fullName == "golem.runtime.annotations.agentDefinition" =>
          true
        case _ => false
      }

    val agentTypeName =
      agentDefinitionTypeName(typeSymbol).map(validateTypeName).getOrElse {
        if !hasAgentDefinition then
          report.errorAndAbort(s"Missing @agentDefinition(...) on agent trait: ${typeSymbol.fullName}")
        defaultTypeNameFromTrait(typeSymbol)
      }

    val traitDescription = annotationString(typeSymbol, TypeRepr.of[description])
    // Note: `@agentDefinition` has a default `mode = Durable`. We omit that default in metadata via
    // `agentDefinitionMode`.
    val traitMode = agentDefinitionMode(typeSymbol)

    val methods = typeSymbol.methodMembers.collect {
      case method if method.flags.is(Flags.Deferred) && method.isDefDef =>
        methodMetadata(method)
    }

    val constructorSchema = inferConstructorSchema(typeRepr)

    '{
      AgentMetadata(
        name = ${
          Expr(agentTypeName)
        },
        description = ${
          optionalString(traitDescription)
        },
        mode = ${
          optionalExprString(traitMode)
        },
        methods = ${
          Expr.ofList(methods)
        },
        constructor = $constructorSchema
      )
    }
  }

  private def agentDefinitionTypeName(using
    Quotes
  )(symbol: quotes.reflect.Symbol): Option[String] = {
    import quotes.reflect.*
    symbol.annotations.collectFirst {
      case Apply(Select(New(tpt), _), args)
          if tpt.tpe.dealias.typeSymbol.fullName == "golem.runtime.annotations.agentDefinition" =>
        args.collectFirst {
          case Literal(StringConstant(value))                       => value
          case NamedArg("typeName", Literal(StringConstant(value))) => value
        }
    }.flatten.map(_.trim).filter(_.nonEmpty)
  }

  private def validateTypeName(value: String): String =
    value

  private def agentDefinitionMode(using
    Quotes
  )(symbol: quotes.reflect.Symbol): Option[Expr[String]] = {
    import quotes.reflect.*
    symbol.annotations.collectFirst {
      case Apply(Select(New(tpt), _), args)
          if tpt.tpe.dealias.typeSymbol.fullName == "golem.runtime.annotations.agentDefinition" =>
        // The compiler may represent default args as:
        // - positional args (typeName, mode)
        // - NamedArg(...) entries (even if not explicitly provided by the user)
        // Be robust and accept both forms.
        val rawModeArg: Option[Term] =
          args.collectFirst { case NamedArg("mode", arg: Term) => arg }.orElse {
            args.lift(1).collect { case t: Term => t }
          }

        rawModeArg.flatMap {
          case Literal(StringConstant(value)) =>
            // (Legacy) allow stringly-typed values.
            // Note: annotation defaults may be inlined by the compiler; treat default "durable" as unset.
            val v = value.trim.toLowerCase
            if (v == "durable") None else Some(Expr(v))
          case term: Term =>
            // Treat default Durable as unset to preserve the "omit defaults" metadata behavior.
            val e = durabilityWireExpr(term)
            if (e.valueOrAbort == "durable") None else Some(e)
        }
    }.flatten
  }

  private def methodMetadata(using Quotes)(method: quotes.reflect.Symbol): Expr[MethodMetadata] = {
    import quotes.reflect.*

    val methodName   = method.name
    val descExpr     = optionalString(annotationString(method, TypeRepr.of[description]))
    val promptExpr   = optionalString(annotationString(method, TypeRepr.of[prompt]))
    val inputSchema  = methodInputSchema(method)
    val outputSchema = methodOutputSchema(method)

    '{
      MethodMetadata(
        name = ${
          Expr(methodName)
        },
        description = $descExpr,
        prompt = $promptExpr,
        mode = None,
        input = $inputSchema,
        output = $outputSchema
      )
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

    if params.isEmpty then
      '{
        StructuredSchema.Tuple(Nil)
      }
    else if params.length == 1 then structuredSchemaExpr(params.head._2)
    else {
      val elements = params.map { case (name, tpe) =>
        val elementExpr = elementSchemaExpr(name, tpe)
        '{
          NamedElementSchema(
            ${
              Expr(name)
            },
            $elementExpr
          )
        }
      }
      val listExpr = Expr.ofList(elements)
      '{
        StructuredSchema.Tuple($listExpr)
      }
    }
  }

  private def methodOutputSchema(using Quotes)(method: quotes.reflect.Symbol): Expr[StructuredSchema] = {
    import quotes.reflect.*

    method.tree match {
      case d: DefDef =>
        val outputType = unwrapAsyncType(d.returnTpt.tpe)
        structuredSchemaExpr(outputType)
      case other =>
        report.errorAndAbort(s"Unable to read return type for ${method.name}: $other")
    }
  }

  private def structuredSchemaExpr(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[StructuredSchema] = {
    import quotes.reflect.*

    tpe.asType match {
      case '[t] =>
        Expr.summon[GolemSchema[t]] match {
          case Some(codecExpr) => '{ $codecExpr.schema }
          case None            => report.errorAndAbort(s"No implicit GolemSchema available for type ${Type.show[t]}.$schemaHint")
        }
    }
  }

  private def elementSchemaExpr(using
    Quotes
  )(paramName: String, tpe: quotes.reflect.TypeRepr): Expr[golem.data.ElementSchema] = {
    import quotes.reflect.*

    tpe.asType match {
      case '[t] =>
        Expr.summon[GolemSchema[t]] match {
          case Some(codecExpr) =>
            '{
              $codecExpr.schema match {
                case StructuredSchema.Tuple(elements) if elements.length == 1 => elements.head.schema
                case StructuredSchema.Tuple(_)                                =>
                  throw new IllegalArgumentException(
                    s"Parameter ${${ Expr(paramName) }} expands to multiple elements; wrap it in a case class"
                  )
                case StructuredSchema.Multimodal(_) =>
                  throw new IllegalArgumentException(
                    s"Parameter ${${ Expr(paramName) }} is multimodal; use a dedicated multimodal wrapper"
                  )
              }
            }
          case None =>
            report.errorAndAbort(s"No implicit GolemSchema available for type ${Type.show[t]}.$schemaHint")
        }
    }
  }

  private def inferConstructorSchema(using
    Quotes
  )(
    traitRepr: quotes.reflect.TypeRepr
  ): Expr[StructuredSchema] = {
    import quotes.reflect.*
    val baseSym = traitRepr.baseClasses.find(_.fullName == "golem.BaseAgent").getOrElse(Symbol.noSymbol)
    if (baseSym == Symbol.noSymbol) '{ StructuredSchema.Tuple(Nil) }
    else
      traitRepr.baseType(baseSym) match {
        case AppliedType(_, List(arg)) =>
          val tpe = arg.dealias
          if (tpe =:= TypeRepr.of[Unit]) '{ StructuredSchema.Tuple(Nil) } else structuredSchemaExpr(tpe)
        case _ =>
          '{ StructuredSchema.Tuple(Nil) }
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

  /**
   * Convert a `DurabilityMode` term from annotations into the wire-value string
   * without splicing the original term (which can carry invalid positions when
   * sourced from annotation trees under -Xcheck-macros).
   */
  private def durabilityWireExpr(using Quotes)(term: quotes.reflect.Term): Expr[String] = {
    import quotes.reflect.*
    def loop(t: Term): String =
      t match {
        case Inlined(_, _, inner: Term) => loop(inner)
        case _                          =>
          t.symbol.name match {
            // Scala may represent default annotation args via synthetic default-getter methods.
            // For `agentDefinition(mode: DurabilityMode = DurabilityMode.Durable)`, this is the default.
            case "$lessinit$greater$default$2" => "durable"
            case "Durable"                     => "durable"
            case "Ephemeral"                   => "ephemeral"
            case other                         =>
              report.errorAndAbort(
                s"Unsupported DurabilityMode annotation value: ${t.show} (symbol=$other). Use DurabilityMode.Durable or DurabilityMode.Ephemeral."
              )
          }
      }

    Expr(loop(term))
  }

  private def optionalString(using Quotes)(value: Option[String]): Expr[Option[String]] =
    value match {
      case Some(v) =>
        '{
          Some(${
            Expr(v)
          })
        }
      case None =>
        '{
          None
        }
    }

  private def optionalExprString(using Quotes)(value: Option[Expr[String]]): Expr[Option[String]] =
    value match {
      case Some(v) => '{ Some($v) }
      case None    => '{ None }
    }

  private def unwrapAsyncType(using Quotes)(tpe: quotes.reflect.TypeRepr): quotes.reflect.TypeRepr = {
    import quotes.reflect.*
    tpe match {
      case AppliedType(constructor, args) if constructor.typeSymbol.fullName == "scala.concurrent.Future" =>
        args.headOption.getOrElse(TypeRepr.of[Unit])
      case AppliedType(constructor, args) if constructor.typeSymbol.fullName == "scala.scalajs.js.Promise" =>
        args.headOption.getOrElse(TypeRepr.of[Unit])
      case other =>
        other
    }
  }
}
