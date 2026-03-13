package golem.runtime.macros

import golem.data.GolemSchema
import golem.runtime.agenttype.{
  AgentImplementationType,
  AsyncImplementationMethod,
  ImplementationMethod,
  SyncImplementationMethod
}
import golem.runtime.{AgentMetadata, MethodMetadata}
import scala.quoted.*

object AgentImplementationMacro {
  private val schemaHint: String =
    "\nHint: GolemSchema is derived from zio.blocks.schema.Schema.\n" +
      "Define or import an implicit Schema[T] for your type.\n" +
      "Scala 3: `final case class T(...) derives zio.blocks.schema.Schema` (or `given Schema[T] = Schema.derived`).\n" +
      "Scala 2: `implicit val schema: zio.blocks.schema.Schema[T] = zio.blocks.schema.Schema.derived`.\n"
  inline def implementationType[Trait](inline build: => Trait): AgentImplementationType[Trait, Unit] =
    ${ implementationTypeImpl[Trait]('build) }

  inline def implementationTypeWithCtor[Trait, Ctor](
    inline build: Ctor => Trait
  ): AgentImplementationType[Trait, Ctor] =
    ${ implementationTypeWithCtorImpl[Trait, Ctor]('build) }

  private def implementationTypeImpl[Trait: Type](
    buildExpr: Expr[Trait]
  )(using Quotes): Expr[AgentImplementationType[Trait, Unit]] = {
    import quotes.reflect.*

    val traitRepr   = TypeRepr.of[Trait]
    val traitSymbol = traitRepr.typeSymbol

    if !traitSymbol.flags.is(Flags.Trait) then
      report.errorAndAbort(s"@agentImplementation target must be a trait, found: ${traitSymbol.fullName}")

    val methodSymbols = traitSymbol.methodMembers.collect {
      case method if method.owner == traitSymbol && method.flags.is(Flags.Deferred) && method.isDefDef =>
        method
    }

    val metadataExpr = '{ AgentDefinitionMacro.generate[Trait] }
    val methodsExpr  = buildImplementationMethodsExpr[Trait](methodSymbols, metadataExpr)

    val ctorSchemaExpr =
      Expr.summon[GolemSchema[Unit]].getOrElse {
        report.errorAndAbort(
          s"Unable to summon GolemSchema for Unit constructor type on ${traitSymbol.fullName}.$schemaHint"
        )
      }

    '{
      val metadata = $metadataExpr
      AgentImplementationType[Trait, Unit](
        metadata = metadata,
        constructorSchema = $ctorSchemaExpr,
        buildInstance = (_: Unit) => $buildExpr,
        methods = $methodsExpr
      )
    }
  }

  private def implementationTypeWithCtorImpl[Trait: Type, Ctor: Type](
    buildExpr: Expr[Any]
  )(using Quotes): Expr[AgentImplementationType[Trait, Ctor]] = {
    import quotes.reflect.*

    val traitRepr   = TypeRepr.of[Trait]
    val traitSymbol = traitRepr.typeSymbol

    if !traitSymbol.flags.is(Flags.Trait) then
      report.errorAndAbort(s"@agentImplementation target must be a trait, found: ${traitSymbol.fullName}")

    val expectedCtor = agentInputTypeRepr[Trait]
    val gotCtor      = TypeRepr.of[Ctor]
    if !(gotCtor =:= expectedCtor) then
      report.errorAndAbort(
        s"Constructor function must have input type matching BaseAgent[${expectedCtor.show}] on ${traitSymbol.fullName} (found: ${gotCtor.show})"
      )

    val metadataExpr = '{ AgentDefinitionMacro.generate[Trait] }
    val methodsExpr  = buildImplementationMethodsExpr[Trait](
      traitSymbol.methodMembers.collect {
        case method if method.owner == traitSymbol && method.flags.is(Flags.Deferred) && method.isDefDef => method
      },
      metadataExpr
    )

    val ctorSchemaExpr =
      Expr.summon[GolemSchema[Ctor]].getOrElse {
        report.errorAndAbort(
          s"Unable to summon GolemSchema for constructor type ${Type.show[Ctor]} on ${traitSymbol.fullName}.$schemaHint"
        )
      }

    val buildTyped = buildExpr.asExprOf[Ctor => Trait]

    '{
      val metadata = $metadataExpr
      AgentImplementationType[Trait, Ctor](
        metadata = metadata,
        constructorSchema = $ctorSchemaExpr,
        buildInstance = (input: Ctor) => $buildTyped(input),
        methods = $methodsExpr
      )
    }
  }

  private def agentInputTypeRepr[Trait: Type](using Quotes): quotes.reflect.TypeRepr = {
    import quotes.reflect.*
    val traitRepr = TypeRepr.of[Trait]
    val baseSym   = traitRepr.baseClasses.find(_.fullName == "golem.BaseAgent").getOrElse(Symbol.noSymbol)
    if (baseSym == Symbol.noSymbol) TypeRepr.of[Unit]
    else
      traitRepr.baseType(baseSym) match {
        case AppliedType(_, List(arg)) => arg
        case _                         => TypeRepr.of[Unit]
      }
  }

  private def buildImplementationMethodsExpr[Trait: Type](using
    quotes: Quotes
  )(
    methods: List[quotes.reflect.Symbol],
    metadataExpr: Expr[AgentMetadata]
  ): Expr[List[ImplementationMethod[Trait]]] = {
    import quotes.reflect.*

    val methodExprs: List[Expr[ImplementationMethod[Trait]]] = methods.map { methodSymbol =>
      val methodName       = methodSymbol.name
      val methodMetadata   = methodMetadataExpr(metadataExpr, methodName)
      val parameterDetails = extractParameters(methodSymbol)

      val accessMode: MethodParamAccess =
        parameterDetails match {
          case Nil      => MethodParamAccess.NoArgs
          case _ :: Nil => MethodParamAccess.SingleArg
          case _        => MethodParamAccess.MultiArgs
        }

      val inputTypeRepr =
        accessMode match {
          case MethodParamAccess.NoArgs    => TypeRepr.of[Unit]
          case MethodParamAccess.SingleArg => parameterDetails.head._2
          case MethodParamAccess.MultiArgs => TypeRepr.of[List[Any]]
        }

      val (isAsync, payloadTpe, handlerTpe) = methodReturnInfo(methodSymbol)

      val methodImpl: Expr[ImplementationMethod[Trait]] =
        inputTypeRepr.asType match {
          case '[in] =>
            payloadTpe.asType match {
              case '[out] =>
                val inputSchemaExpr =
                  accessMode match {
                    case MethodParamAccess.MultiArgs =>
                      multiParamSchemaExpr(methodName, parameterDetails).asExprOf[GolemSchema[in]]
                    case _ =>
                      summonSchema[in](methodName, "input")
                  }
                val outputSchemaExpr = summonSchema[out](methodName, "output")

                if !isAsync then {
                  val handlerExpr = handlerLambda[Trait, in, out](methodSymbol, accessMode, parameterDetails)
                  '{
                    val metadataEntry = $methodMetadata
                    SyncImplementationMethod[Trait, in, out](
                      metadata = metadataEntry,
                      inputSchema = $inputSchemaExpr,
                      outputSchema = $outputSchemaExpr,
                      handler = $handlerExpr
                    )
                  }
                } else
                  handlerTpe.asType match {
                    case '[handlerReturn] =>
                      val handlerExpr =
                        handlerLambda[Trait, in, handlerReturn](methodSymbol, accessMode, parameterDetails)
                      val normalized =
                        handlerExpr.asExprOf[(Trait, in) => scala.concurrent.Future[out]]
                      '{
                        val metadataEntry = $methodMetadata
                        AsyncImplementationMethod[Trait, in, out](
                          metadata = metadataEntry,
                          inputSchema = $inputSchemaExpr,
                          outputSchema = $outputSchemaExpr,
                          handler = $normalized
                        )
                      }
                    case _ =>
                      report.errorAndAbort(s"Unsupported async handler type for method $methodName")
                  }
              case _ =>
                report.errorAndAbort(s"Unsupported output type for method $methodName")
            }
          case _ =>
            report.errorAndAbort(s"Unsupported input type for method $methodName")
        }

      methodImpl
    }

    Expr.ofList(methodExprs)
  }

  private def methodMetadataExpr(using
    Quotes
  )(
    metadataExpr: Expr[AgentMetadata],
    methodName: String
  ): Expr[MethodMetadata] =
    '{
      $metadataExpr.methods.find(_.name == ${ Expr(methodName) }).getOrElse {
        throw new IllegalStateException(s"Method metadata missing for ${${ Expr(methodName) }}")
      }
    }

  private def extractParameters(using
    Quotes
  )(method: quotes.reflect.Symbol): List[(String, quotes.reflect.TypeRepr)] = {
    import quotes.reflect.*

    method.paramSymss.collectFirst {
      case params if params.forall(_.isTerm) =>
        params.collect {
          case sym if sym.isTerm =>
            sym.tree match {
              case v: ValDef => (sym.name, v.tpt.tpe)
              case other     => report.errorAndAbort(s"Unsupported parameter declaration in ${method.name}: $other")
            }
        }
    }.getOrElse(Nil)
  }

  private def methodReturnInfo(using
    Quotes
  )(
    method: quotes.reflect.Symbol
  ): (Boolean, quotes.reflect.TypeRepr, quotes.reflect.TypeRepr) = {
    import quotes.reflect.*

    method.tree match {
      case d: DefDef =>
        val returnType = d.returnTpt.tpe
        asyncInnerType(returnType) match {
          case Some(inner) =>
            (true, inner, returnType)
          case None =>
            (false, returnType, returnType)
        }
      case other =>
        report.errorAndAbort(s"Unable to read return type for ${method.name}: $other")
    }
  }

  private def asyncInnerType(using
    Quotes
  )(
    tpe: quotes.reflect.TypeRepr
  ): Option[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*

    tpe match {
      case AppliedType(constructor, args) if constructor.typeSymbol.fullName == "scala.concurrent.Future" =>
        args.headOption
      case _ =>
        None
    }
  }

  private enum MethodParamAccess {
    case NoArgs
    case SingleArg
    case MultiArgs
  }

  private def summonSchema[A: Type](methodName: String, position: String)(using Quotes): Expr[GolemSchema[A]] =
    Expr.summon[GolemSchema[A]].getOrElse {
      import quotes.reflect.*
      report.errorAndAbort(
        s"Unable to summon GolemSchema for $position of method $methodName with type ${Type.show[A]}.$schemaHint"
      )
    }

  private def multiParamSchemaExpr(using
    Quotes
  )(
    methodName: String,
    params: List[(String, quotes.reflect.TypeRepr)]
  ): Expr[GolemSchema[List[Any]]] = {

    val methodNameExpr    = Expr(methodName)
    val expectedCountExpr = Expr(params.length)

    val paramEntries: Seq[Expr[(String, GolemSchema[Any])]] =
      params.map { case (name, tpe) =>
        tpe.asType match {
          case '[p] =>
            val codecExpr = summonSchema[p](methodName, s"parameter '$name'")
            '{ (${ Expr(name) }, $codecExpr.asInstanceOf[GolemSchema[Any]]) }
        }
      }

    val paramsArrayExpr =
      '{ Array[(String, GolemSchema[Any])](${ Varargs(paramEntries) }*) }

    '{
      new GolemSchema[List[Any]] {
        private val params = $paramsArrayExpr

        override val schema: _root_.golem.data.StructuredSchema = {
          val builder = List.newBuilder[_root_.golem.data.NamedElementSchema]
          var idx     = 0
          while (idx < params.length) {
            val (paramName, codec) = params(idx)
            codec.schema match {
              case _root_.golem.data.StructuredSchema.Tuple(elements) if elements.length == 1 =>
                builder += _root_.golem.data.NamedElementSchema(paramName, elements.head.schema)
              case other =>
                throw new IllegalArgumentException(
                  s"Parameter '$paramName' in method '${$methodNameExpr}' must encode to a single element, found: $other"
                )
            }
            idx += 1
          }
          _root_.golem.data.StructuredSchema.Tuple(builder.result())
        }

        override def encode(value: List[Any]): Either[String, _root_.golem.data.StructuredValue] = {
          val values = value.toVector
          if (values.length != params.length)
            Left(
              s"Parameter count mismatch for method '${$methodNameExpr}'. Expected ${$expectedCountExpr}, found $${values.length}"
            )
          else {
            val builder = List.newBuilder[_root_.golem.data.NamedElementValue]
            var idx     = 0
            while (idx < params.length) {
              val (paramName, codec) = params(idx)
              codec.encode(values(idx)) match {
                case Left(err) =>
                  return Left(s"Failed to encode parameter '$paramName' in method '${$methodNameExpr}': $err")
                case Right(_root_.golem.data.StructuredValue.Tuple(elements)) =>
                  elements match {
                    case _root_.golem.data.NamedElementValue(_, elementValue) :: Nil =>
                      builder += _root_.golem.data.NamedElementValue(paramName, elementValue)
                    case _ =>
                      return Left(
                        s"Parameter '$paramName' in method '${$methodNameExpr}' must encode to a single element value"
                      )
                  }
                case Right(other) =>
                  return Left(
                    s"Parameter '$paramName' in method '${$methodNameExpr}' produced unexpected structured value: $other"
                  )
              }
              idx += 1
            }
            Right(_root_.golem.data.StructuredValue.Tuple(builder.result()))
          }
        }

        override def decode(
          value: _root_.golem.data.StructuredValue
        ): Either[String, List[Any]] =
          value match {
            case _root_.golem.data.StructuredValue.Tuple(elements) =>
              if (elements.length != params.length)
                Left(
                  s"Structured element count mismatch for method '${$methodNameExpr}'. Expected ${$expectedCountExpr}, found $${elements.length}"
                )
              else {
                val builder = List.newBuilder[Any]
                var idx     = 0
                while (idx < params.length) {
                  val (paramName, codec) = params(idx)
                  val element            = elements(idx)
                  if (element.name != paramName)
                    return Left(
                      s"Structured element name mismatch for method '${$methodNameExpr}'. Expected '$$paramName', found '${element.name}'"
                    )
                  codec.decode(_root_.golem.data.StructuredValue.single(element.value)) match {
                    case Left(err) =>
                      return Left(s"Failed to decode parameter '$paramName' in method '${$methodNameExpr}': $err")
                    case Right(decoded) =>
                      builder += decoded
                  }
                  idx += 1
                }
                Right(builder.result())
              }
            case other =>
              Left(s"Structured value mismatch for method '${$methodNameExpr}'. Expected tuple payload, found: $other")
          }
      }
    }
  }

  private def handlerLambda[Trait: Type, In: Type, Out: Type](using
    quotes: Quotes
  )(
    method: quotes.reflect.Symbol,
    access: MethodParamAccess,
    parameters: List[(String, quotes.reflect.TypeRepr)]
  ): Expr[(Trait, In) => Out] = {
    import quotes.reflect.*

    val lambdaType =
      MethodType(List("instance", "input"))(_ => List(TypeRepr.of[Trait], TypeRepr.of[In]), _ => TypeRepr.of[Out])

    Lambda(
      Symbol.spliceOwner,
      lambdaType,
      { (lambdaOwner, params) =>
        val instanceTerm = params.head.asInstanceOf[Term]
        val inputTerm    = params(1).asInstanceOf[Term]

        val callTerm: Term = access match {
          case MethodParamAccess.NoArgs =>
            Apply(Select(instanceTerm, method), Nil)
          case MethodParamAccess.SingleArg =>
            Apply(Select(instanceTerm, method), List(inputTerm))
          case MethodParamAccess.MultiArgs =>
            val valuesSym =
              Symbol.newVal(lambdaOwner, "values", TypeRepr.of[List[Any]], Flags.EmptyFlags, Symbol.noSymbol)
            val valuesVal         = ValDef(valuesSym, Some(inputTerm))
            val valuesRef         = Ref(valuesSym).asExprOf[List[Any]]
            val expectedCount     = parameters.length
            val lengthCheck: Term = {
              val expectedExpr          = Expr(expectedCount)
              val methodLabel           = Expr(method.name)
              val checkExpr: Expr[Unit] =
                '{
                  if ($valuesRef.length != $expectedExpr)
                    throw new IllegalArgumentException(
                      s"Parameter count mismatch when invoking method '${$methodLabel}'. Expected ${$expectedExpr}."
                    )
                }
              checkExpr.asTerm
            }
            val argTerms: List[Term] = parameters.zipWithIndex.map { case ((_, paramType), idx) =>
              paramType.asType match {
                case '[p] =>
                  '{ $valuesRef.apply(${ Expr(idx) }).asInstanceOf[p] }.asTerm
              }
            }
            Block(
              List(valuesVal),
              Block(
                List(lengthCheck),
                Apply(Select(instanceTerm, method), argTerms)
              )
            )
        }

        callTerm
      }
    ).asExprOf[(Trait, In) => Out]
  }
}
