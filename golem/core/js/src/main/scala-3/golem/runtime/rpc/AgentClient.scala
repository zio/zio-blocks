package golem.runtime.rpc

import golem.runtime.macros.AgentClientMacro
import golem.runtime.plan.{AgentClientPlan, ClientMethodPlan}
import golem.Uuid

import scala.collection.immutable.Vector
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

object AgentClient {
  transparent inline def plan[Trait]: AgentClientPlan[Trait, ?] =
    AgentClientMacro.plan[Trait]

  transparent inline def connect[Trait, Constructor](
    plan: AgentClientPlan[Trait, Constructor],
    constructorArgs: Constructor
  ): Future[Trait] =
    AgentClientInlineMacros.connect[Trait, Constructor](plan, constructorArgs)

  transparent inline def connectPhantom[Trait, Constructor](
    plan: AgentClientPlan[Trait, Constructor],
    constructorArgs: Constructor,
    phantom: Uuid
  ): Future[Trait] =
    AgentClientInlineMacros.connectPhantom[Trait, Constructor](plan, constructorArgs, phantom)

  transparent inline def bind[Trait](
    resolved: AgentClientRuntime.ResolvedAgent[Trait]
  ): Trait =
    AgentClientRuntime.TestHooks.bindOverride(resolved).getOrElse {
      AgentClientInlineMacros.bind[Trait](resolved)
    }
}

private object AgentClientInlineMacros {
  import scala.quoted.*

  transparent inline def connect[Trait, Constructor](
    plan: AgentClientPlan[Trait, Constructor],
    constructorArgs: Constructor
  ): Future[Trait] =
    ${ connectImpl[Trait, Constructor]('plan, 'constructorArgs) }

  transparent inline def connectPhantom[Trait, Constructor](
    plan: AgentClientPlan[Trait, Constructor],
    constructorArgs: Constructor,
    phantom: Uuid
  ): Future[Trait] =
    ${ connectPhantomImpl[Trait, Constructor]('plan, 'constructorArgs, 'phantom) }

  transparent inline def bind[Trait](
    resolved: AgentClientRuntime.ResolvedAgent[Trait]
  ): Trait =
    ${ stubImpl[Trait]('resolved) }

  private def connectImpl[Trait: Type, Constructor: Type](
    planExpr: Expr[AgentClientPlan[Trait, Constructor]],
    constructorExpr: Expr[Constructor]
  )(using Quotes): Expr[Future[Trait]] =
    '{
      AgentClientRuntime.resolve[Trait, Constructor]($planExpr, $constructorExpr) match {
        case Left(err) =>
          Future.failed(js.JavaScriptException(err))
        case Right(resolved) =>
          Future.successful(${ stubImpl[Trait]('resolved) })
      }
    }

  private def connectPhantomImpl[Trait: Type, Constructor: Type](
    planExpr: Expr[AgentClientPlan[Trait, Constructor]],
    constructorExpr: Expr[Constructor],
    phantomExpr: Expr[Uuid]
  )(using Quotes): Expr[Future[Trait]] =
    '{
      AgentClientRuntime.resolveWithPhantom[Trait, Constructor](
        $planExpr,
        $constructorExpr,
        phantom = Some($phantomExpr)
      ) match {
        case Left(err) =>
          Future.failed(js.JavaScriptException(err))
        case Right(resolved) =>
          Future.successful(${ stubImpl[Trait]('resolved) })
      }
    }

  private def stubImpl[Trait: Type](
    resolvedExpr: Expr[AgentClientRuntime.ResolvedAgent[Trait]]
  )(using Quotes): Expr[Trait] = {
    import quotes.reflect.*

    case class MethodPlanData(
      method: Symbol,
      accessMode: MethodParamAccess,
      inputType: TypeRepr,
      outputType: TypeRepr,
      invocation: InvocationKind
    )

    val traitRepr   = TypeRepr.of[Trait]
    val traitSymbol = traitRepr.typeSymbol

    if !traitSymbol.flags.is(Flags.Trait) then
      report.errorAndAbort(s"Agent client target must be a trait, found: ${traitSymbol.fullName}")

    val pendingMethods = traitSymbol.methodMembers.collect {
      case method if method.flags.is(Flags.Deferred) && method.isDefDef && method.name != "new" =>
        val params                       = extractParameters(method)
        val accessMode                   = methodAccess(params)
        val inputType                    = inputTypeFor(accessMode, params)
        val (invocationKind, outputType) = methodInvocationInfo(method)

        MethodPlanData(
          method = method,
          accessMode = accessMode,
          inputType = inputType,
          outputType = outputType,
          invocation = invocationKind
        )
    }

    val resolvedSym = Symbol.newVal(
      Symbol.spliceOwner,
      "$resolvedAgent",
      TypeRepr.of[AgentClientRuntime.ResolvedAgent[Trait]],
      Flags.EmptyFlags,
      Symbol.noSymbol
    )

    val resolvedVal = ValDef(resolvedSym, Some(resolvedExpr.asTerm))
    val resolvedRef = Ref(resolvedSym).asExprOf[AgentClientRuntime.ResolvedAgent[Trait]]

    def buildInputValueExpr(accessMode: MethodParamAccess, inputType: TypeRepr, params: List[Expr[Any]]): Expr[Any] =
      inputType.asType match {
        case '[input] =>
          accessMode match {
            case MethodParamAccess.NoArgs =>
              '{ ().asInstanceOf[input] }
            case MethodParamAccess.SingleArg =>
              params.headOption
                .getOrElse(report.errorAndAbort("Single argument access mode requires exactly one argument"))
                .asExprOf[input]
            case MethodParamAccess.MultiArgs =>
              val elements = params.map(_.asExprOf[Any])
              '{ Vector[Any](${ Varargs(elements) }*) }.asExprOf[input]
          }
      }

    def buildMethodPlanLookup[In: Type, Out: Type](methodName: String): Expr[ClientMethodPlan[Trait, In, Out]] = {
      val methodNameExpr = Expr(methodName)
      '{
        $resolvedRef.plan.methods.collectFirst {
          case plan if plan.metadata.name == $methodNameExpr =>
            plan.asInstanceOf[ClientMethodPlan[Trait, In, Out]]
        }
          .getOrElse(throw new IllegalStateException(s"Method plan for ${$methodNameExpr} not found"))
      }
    }

    def buildMethodBody(planData: MethodPlanData, paramExprs: List[Expr[Any]]): Expr[Any] = {
      val methodNameExpr: Expr[String] = Expr(planData.method.name)

      planData.inputType.asType match {
        case '[input] =>
          planData.outputType.asType match {
            case '[output] =>
              val inputValueExpr =
                buildInputValueExpr(planData.accessMode, planData.inputType, paramExprs).asExprOf[input]
              val methodPlanExpr =
                buildMethodPlanLookup[input, output](planData.method.name)

              planData.invocation match {
                case InvocationKind.Awaitable =>
                  '{

                    $resolvedRef.call[input, output](
                      $methodPlanExpr,
                      $inputValueExpr
                    )
                  }
                case InvocationKind.FireAndForget =>
                  if !(planData.outputType =:= TypeRepr.of[Unit]) then
                    report.errorAndAbort(s"Fire-and-forget method ${planData.method.name} must return Unit")
                  val triggerPlanExpr =
                    methodPlanExpr.asExprOf[ClientMethodPlan[Trait, input, Unit]]
                  '{
                    import scala.concurrent.ExecutionContext.Implicits.global
                    $resolvedRef
                      .trigger[input](
                        $triggerPlanExpr,
                        $inputValueExpr
                      )
                      .failed
                      .foreach(err =>
                        js.Dynamic.global.console.error(
                          s"RPC trigger ${$methodNameExpr} failed",
                          err.asInstanceOf[js.Any]
                        )
                      )
                    ()
                  }
              }
          }
      }
    }

    var methodSymbols = List.empty[(MethodPlanData, Symbol)]

    val parents     = List(TypeRepr.of[Object], traitRepr)
    val classSymbol = Symbol.newClass(
      Symbol.spliceOwner,
      traitSymbol.name + "$agentClient",
      parents,
      cls => {
        val decls = pendingMethods.map { data =>
          val methodType = traitRepr.memberType(data.method)
          val symbol     = Symbol.newMethod(
            cls,
            data.method.name,
            methodType,
            Flags.Override | Flags.Method,
            Symbol.noSymbol
          )
          methodSymbols = methodSymbols :+ (data -> symbol)
          symbol
        }
        decls
      },
      selfType = None
    )

    val ctorSymbol = classSymbol.primaryConstructor

    if ctorSymbol == Symbol.noSymbol then report.errorAndAbort("Failed to synthesize agent client stub constructor")

    val methodDefs = methodSymbols.map { case (data, symbol) =>
      DefDef(
        symbol,
        paramss => {
          val paramRefs = paramss.flatten.collect { case term: Term =>
            term.asExprOf[Any]
          }

          val bodyExpr = buildMethodBody(data, paramRefs)
          Some(bodyExpr.asTerm.changeOwner(symbol))
        }
      )
    }

    val parentsTrees = List(TypeTree.of[Object], TypeTree.of[Trait])
    // Omit an explicit constructor definition; Scala 3's constructors phase will synthesize it.
    // This avoids pickler assertion failures observed for macro-generated local classes on some versions.
    val classDef = ClassDef(classSymbol, parentsTrees, body = methodDefs)

    val newInstance = Apply(Select(New(TypeIdent(classSymbol)), ctorSymbol), Nil)

    Block(
      List(resolvedVal, classDef),
      newInstance
    ).asExprOf[Trait]
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

  private def methodAccess(using Quotes)(parameters: List[(String, quotes.reflect.TypeRepr)]): MethodParamAccess =
    parameters match {
      case Nil      => MethodParamAccess.NoArgs
      case _ :: Nil => MethodParamAccess.SingleArg
      case _        => MethodParamAccess.MultiArgs
    }

  private def inputTypeFor(using
    Quotes
  )(
    access: MethodParamAccess,
    parameters: List[(String, quotes.reflect.TypeRepr)]
  ): quotes.reflect.TypeRepr =
    access match {
      case MethodParamAccess.NoArgs    => quotes.reflect.TypeRepr.of[Unit]
      case MethodParamAccess.SingleArg => parameters.headOption.fold(quotes.reflect.TypeRepr.of[Unit])(_._2)
      case MethodParamAccess.MultiArgs => quotes.reflect.TypeRepr.of[Vector[Any]]
    }

  private def methodInvocationInfo(using
    Quotes
  )(
    method: quotes.reflect.Symbol
  ): (InvocationKind, quotes.reflect.TypeRepr) = {
    import quotes.reflect.*
    method.tree match {
      case d: DefDef =>
        val returnType = d.returnTpt.tpe
        returnType match {
          case AppliedType(constructor, args) if isAsyncReturn(constructor) && args.nonEmpty =>
            (InvocationKind.Awaitable, args.head)
          case _ =>
            if returnType =:= TypeRepr.of[Unit] then (InvocationKind.FireAndForget, TypeRepr.of[Unit])
            else {
              report.errorAndAbort(
                s"Agent client method ${method.name} must return scala.concurrent.Future[...] or Unit, found: ${returnType.show}"
              )
            }
        }
      case other =>
        report.errorAndAbort(s"Unable to read return type for ${method.name}: $other")
    }
  }

  private def isAsyncReturn(using Quotes)(constructor: quotes.reflect.TypeRepr): Boolean = {
    val name = constructor.typeSymbol.fullName
    name == "scala.concurrent.Future" || name == "scala.scalajs.js.Promise"
  }

  private enum MethodParamAccess {
    case NoArgs
    case SingleArg
    case MultiArgs
  }

  private enum InvocationKind {
    case Awaitable
    case FireAndForget
  }
}
