package golem

import golem.runtime.agenttype.AgentMethod

import scala.concurrent.Future
import scala.quoted.*
import scala.scalajs.js

/**
 * Scala-3-only ergonomic RPC surface that resembles Rust/TS SDKs:
 *
 *   - `remote.api.foo(...)` -> await (normal trait call)
 *   - `remote.rpc.trigger_foo(...)` -> trigger (always Future[Unit])
 *   - `remote.rpc.schedule_foo(ts, ...)` -> schedule (always Future[Unit])
 *   - `remote.rpc.call_foo(...)` -> await (always invoke-and-await, even if the
 *     trait method is `Unit`)
 *
 * Usage:
 * {{{
 * import golem.RemoteAgentOps.*
 * val remote = await(MyAgent.getRemote(...))
 * remote.rpc.trigger_foo(...)
 * remote.rpc.schedule_foo(ts, ...)
 * }}}
 */
object RemoteAgentOps {
  extension [Trait](remote: RemoteAgent[Trait]) {
    transparent inline def rpc =
      ${ RemoteAgentRpcMacro.rpcImpl[Trait]('remote) }
  }
}

private object RemoteAgentRpcMacro {
  def rpcImpl[Trait: Type](remoteExpr: Expr[RemoteAgent[Trait]])(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    case class MethodData(
      method: Symbol,
      params: List[(String, TypeRepr)],
      accessMode: MethodParamAccess,
      inputType: TypeRepr,
      outputType: TypeRepr,
      returnType: TypeRepr
    )

    val traitRepr   = TypeRepr.of[Trait]
    val traitSymbol = traitRepr.typeSymbol

    if !traitSymbol.flags.is(Flags.Trait) then
      report.errorAndAbort(s"RemoteAgentOps.rpc target must be a trait, found: ${traitSymbol.fullName}")

    val pendingMethods: List[MethodData] =
      traitSymbol.methodMembers.collect {
        case m if m.flags.is(Flags.Deferred) && m.isDefDef && m.name != "new" =>
          val params                                              = extractParameters(m)
          val accessMode                                          = methodAccess(params)
          val inputType                                           = inputTypeFor(accessMode, params)
          val (outputType, returnType /* Future[Out] or Unit */ ) = returnAndOutputTypeFor(m)
          MethodData(m, params, accessMode, inputType, outputType, returnType)
      }

    // Construct a refined structural type that includes:
    // - call_<name>(...): Future[Out]
    // - trigger_<name>(...): Future[Unit]
    // - schedule_<name>(datetime: js.Dynamic, ...): Future[Unit]
    val rpcType: TypeRepr =
      pendingMethods.foldLeft(TypeRepr.of[AnyRef]) { case (acc, m) =>
        val callTpe =
          MethodType(m.params.map(_._1))(_ => m.params.map(_._2), _ => TypeRepr.of[Future].appliedTo(m.outputType))
        val triggerTpe =
          MethodType(m.params.map(_._1))(_ => m.params.map(_._2), _ => TypeRepr.of[Future[Unit]])
        val scheduleTpe =
          MethodType("datetime" :: m.params.map(_._1))(
            _ => TypeRepr.of[Datetime] :: m.params.map(_._2),
            _ => TypeRepr.of[Future[Unit]]
          )

        Refinement(
          Refinement(
            Refinement(acc, s"call_${m.method.name}", callTpe),
            s"trigger_${m.method.name}",
            triggerTpe
          ),
          s"schedule_${m.method.name}",
          scheduleTpe
        )
      }

    val remoteSym = Symbol.newVal(
      Symbol.spliceOwner,
      "$remote",
      TypeRepr.of[RemoteAgent[Trait]],
      Flags.EmptyFlags,
      Symbol.noSymbol
    )
    val remoteVal = ValDef(remoteSym, Some(remoteExpr.asTerm))
    val remoteRef = Ref(remoteSym).asExprOf[RemoteAgent[Trait]]

    val objSym = Symbol.newVal(Symbol.spliceOwner, "$rpc", TypeRepr.of[js.Dynamic], Flags.EmptyFlags, Symbol.noSymbol)
    val objVal = ValDef(objSym, Some('{ js.Dynamic.literal() }.asTerm))
    val objRef = Ref(objSym).asExprOf[js.Dynamic]

    def findMethod[In: Type, Out: Type](methodName: String): Expr[AgentMethod[Trait, In, Out]] = {
      val methodNameExpr = Expr(methodName)
      '{
        $remoteRef.resolved.agentType.methods.collectFirst {
          case mm if mm.metadata.name == $methodNameExpr =>
            mm.asInstanceOf[AgentMethod[Trait, In, Out]]
        }
          .getOrElse(throw new IllegalStateException(s"Method definition for ${$methodNameExpr} not found"))
      }
    }

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

    def encodeTypeName(tpe0: TypeRepr): String = {
      val tpe = tpe0.dealias.widen
      if tpe =:= TypeRepr.of[Unit] then "V"
      else if tpe =:= TypeRepr.of[Boolean] then "Z"
      else if tpe =:= TypeRepr.of[Byte] then "B"
      else if tpe =:= TypeRepr.of[Short] then "S"
      else if tpe =:= TypeRepr.of[Char] then "C"
      else if tpe =:= TypeRepr.of[Int] then "I"
      else if tpe =:= TypeRepr.of[Long] then "J"
      else if tpe =:= TypeRepr.of[Float] then "F"
      else if tpe =:= TypeRepr.of[Double] then "D"
      else if tpe =:= TypeRepr.of[String] then "T"
      else
        tpe match {
          case AppliedType(arr, List(elem)) if arr.typeSymbol.fullName == "scala.Array" =>
            "A" + encodeTypeName(elem)
          case AppliedType(constructor, _) =>
            encodeTypeName(constructor)
          case _ =>
            val full = tpe.typeSymbol.fullName
            if full.startsWith("scala.scalajs.") then "sjs_" + full.stripPrefix("scala.scalajs.").replace('.', '_')
            else if full.startsWith("scala.collection.immutable.") then
              "sci_" + full.stripPrefix("scala.collection.immutable.").replace('.', '_')
            else if full.startsWith("scala.collection.mutable.") then
              "scm_" + full.stripPrefix("scala.collection.mutable.").replace('.', '_')
            else if full.startsWith("scala.collection.") then
              "sc_" + full.stripPrefix("scala.collection.").replace('.', '_')
            else if full.startsWith("scala.") then "s_" + full.stripPrefix("scala.").replace('.', '_')
            else if full.startsWith("java.lang.") then "jl_" + full.stripPrefix("java.lang.").replace('.', '_')
            else if full.startsWith("java.util.") then "ju_" + full.stripPrefix("java.util.").replace('.', '_')
            else if full == "" || full == "<none>" then "O"
            else "L" + full.replace('.', '_')
        }
    }

    // Ensure Scala.js can call these members efficiently by providing the mangled names it expects for method calls.
    def scalaJsMethodName(name: String, paramTypes: List[TypeRepr], resultType: TypeRepr): String = {
      val paramTypeNames = paramTypes.map(encodeTypeName)
      val resultTypeName = encodeTypeName(resultType)
      if paramTypeNames.isEmpty then s"${name}__${resultTypeName}"
      else s"${name}__${paramTypeNames.mkString("__")}__${resultTypeName}"
    }

    def buildFn(method: MethodData, op: String): Expr[js.Any] = {
      val methodName = method.method.name

      method.inputType.asType match {
        case '[in] =>
          method.outputType.asType match {
            case '[out] =>
              val methodExpr = findMethod[in, out](methodName)

              val fnTerm: Term =
                op match {
                  case "call" =>
                    val mt =
                      MethodType(method.params.map(_._1))(_ => method.params.map(_._2), _ => TypeRepr.of[Future[out]])
                    Lambda(
                      Symbol.spliceOwner,
                      mt,
                      (owner, params) => {
                        val args    = params.collect { case t: Term => t.asExprOf[Any] }.toList
                        val inValue =
                          buildInputValueExpr(method.accessMode, method.inputType, args).asExprOf[in]
                        '{ $remoteRef.resolved.await[in, out]($methodExpr, $inValue) }.asTerm.changeOwner(owner)
                      }
                    )
                  case "trigger" =>
                    val mt =
                      MethodType(method.params.map(_._1))(_ => method.params.map(_._2), _ => TypeRepr.of[Future[Unit]])
                    Lambda(
                      Symbol.spliceOwner,
                      mt,
                      (owner, params) => {
                        val args    = params.collect { case t: Term => t.asExprOf[Any] }.toList
                        val inValue =
                          buildInputValueExpr(method.accessMode, method.inputType, args).asExprOf[in]
                        '{ $remoteRef.resolved.trigger[in]($methodExpr, $inValue) }.asTerm.changeOwner(owner)
                      }
                    )
                  case "schedule" =>
                    val mt =
                      MethodType("datetime" :: method.params.map(_._1))(
                        _ => TypeRepr.of[Datetime] :: method.params.map(_._2),
                        _ => TypeRepr.of[Future[Unit]]
                      )
                    Lambda(
                      Symbol.spliceOwner,
                      mt,
                      (owner, params) => {
                        val all = params.collect { case t: Term => t }.toList
                        val dt  =
                          all.headOption.getOrElse(
                            report.errorAndAbort(s"schedule_${methodName} missing datetime argument")
                          )
                        val rest    = all.drop(1)
                        val args    = rest.map(_.asExprOf[Any])
                        val inValue =
                          buildInputValueExpr(method.accessMode, method.inputType, args).asExprOf[in]
                        val dtExpr = dt.asExprOf[Datetime]
                        '{ $remoteRef.resolved.schedule[in]($methodExpr, $dtExpr, $inValue) }.asTerm.changeOwner(owner)
                      }
                    )
                  case other =>
                    report.errorAndAbort(s"Unsupported rpc op: $other")
                }

              val fnExpr = fnTerm.asExprOf[Any]

              op match {
                case "schedule" =>
                  // schedule arity is params + 1 (datetime)
                  (method.params.length + 1) match {
                    case 1 => '{ js.Any.fromFunction1($fnExpr.asInstanceOf[Any => Any]) }
                    case 2 => '{ js.Any.fromFunction2($fnExpr.asInstanceOf[(Any, Any) => Any]) }
                    case 3 => '{ js.Any.fromFunction3($fnExpr.asInstanceOf[(Any, Any, Any) => Any]) }
                    case 4 => '{ js.Any.fromFunction4($fnExpr.asInstanceOf[(Any, Any, Any, Any) => Any]) }
                    case n =>
                      report.errorAndAbort(
                        s"Unsupported agent method arity for schedule_${methodName}: $n (supported: 1-4 including datetime)"
                      )
                  }
                case _ =>
                  method.params.length match {
                    case 0 => '{ js.Any.fromFunction0($fnExpr.asInstanceOf[() => Any]) }
                    case 1 => '{ js.Any.fromFunction1($fnExpr.asInstanceOf[Any => Any]) }
                    case 2 => '{ js.Any.fromFunction2($fnExpr.asInstanceOf[(Any, Any) => Any]) }
                    case 3 => '{ js.Any.fromFunction3($fnExpr.asInstanceOf[(Any, Any, Any) => Any]) }
                    case n =>
                      report.errorAndAbort(
                        s"Unsupported agent method arity for ${op}_${methodName}: $n (supported: 0-3)"
                      )
                  }
              }
          }
      }
    }

    val updates: List[Statement] =
      pendingMethods.flatMap { m =>
        val methodName = m.method.name

        val callName     = s"call_${methodName}"
        val triggerName  = s"trigger_${methodName}"
        val scheduleName = s"schedule_${methodName}"

        val callJsName  = scalaJsMethodName(callName, m.params.map(_._2), TypeRepr.of[Future].appliedTo(m.outputType))
        val trigJsName  = scalaJsMethodName(triggerName, m.params.map(_._2), TypeRepr.of[Future[Unit]])
        val schedJsName =
          scalaJsMethodName(
            scheduleName,
            TypeRepr.of[Datetime] :: m.params.map(_._2),
            TypeRepr.of[Future[Unit]]
          )

        val callFn     = buildFn(m, "call")
        val triggerFn  = buildFn(m, "trigger")
        val scheduleFn = buildFn(m, "schedule")

        List(
          '{ $objRef.updateDynamic(${ Expr(callJsName) })($callFn) }.asTerm,
          '{ $objRef.updateDynamic(${ Expr(trigJsName) })($triggerFn) }.asTerm,
          '{ $objRef.updateDynamic(${ Expr(schedJsName) })($scheduleFn) }.asTerm
        )
      }

    val casted = Typed(objRef.asTerm, Inferred(rpcType)).asExpr

    Block(remoteVal :: objVal :: updates, casted.asTerm).asExpr
  }

  private enum MethodParamAccess { case NoArgs, SingleArg, MultiArgs }

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

  private def methodAccess(using Quotes)(params: List[(String, quotes.reflect.TypeRepr)]): MethodParamAccess =
    params match {
      case Nil      => MethodParamAccess.NoArgs
      case _ :: Nil => MethodParamAccess.SingleArg
      case _        => MethodParamAccess.MultiArgs
    }

  private def inputTypeFor(using
    Quotes
  )(
    access: MethodParamAccess,
    params: List[(String, quotes.reflect.TypeRepr)]
  ): quotes.reflect.TypeRepr = {
    import quotes.reflect.*
    access match {
      case MethodParamAccess.NoArgs    => TypeRepr.of[Unit]
      case MethodParamAccess.SingleArg => params.head._2
      case MethodParamAccess.MultiArgs => TypeRepr.of[Vector[Any]]
    }
  }

  private def returnAndOutputTypeFor(using
    Quotes
  )(method: quotes.reflect.Symbol): (quotes.reflect.TypeRepr, quotes.reflect.TypeRepr) = {
    import quotes.reflect.*
    method.tree match {
      case d: DefDef =>
        val tpe = d.returnTpt.tpe
        tpe match {
          case AppliedType(constructor, args)
              if constructor.typeSymbol.fullName == "scala.concurrent.Future" && args.nonEmpty =>
            (args.head, tpe)
          case other if other =:= TypeRepr.of[Unit] =>
            (TypeRepr.of[Unit], other)
          case other =>
            report.errorAndAbort(
              s"Agent method ${method.name} must return scala.concurrent.Future[...] or Unit (for RPC compatibility). Found: ${other.show}"
            )
        }
      case other =>
        report.errorAndAbort(s"Unable to read return type for ${method.name}: $other")
    }
  }
}
