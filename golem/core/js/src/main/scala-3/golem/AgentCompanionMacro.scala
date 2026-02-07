package golem

import golem.runtime.agenttype.AgentType
import golem.runtime.rpc.{AgentClient, AgentClientRuntime}
import golem.Uuid

import scala.quoted.*
import scala.scalajs.js

/**
 * Scala 3 implementation for `AgentCompanion` methods that need to be checked
 * against `BaseAgent[Input]` on the agent trait.
 *
 * This lives in `core` (not `macros`) to avoid introducing a cyclic project
 * dependency.
 */
private[golem] object AgentCompanionMacro {
  def getImpl[Trait: Type, In: Type](input: Expr[In])(using Quotes): Expr[Trait] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val got      = TypeRepr.of[In]
    if !(got =:= expected) then
      report.errorAndAbort(
        s"get(input) requires: BaseAgent[${expected.show}] (found argument type: ${got.show})"
      )
    '{
      AgentClientRuntime.resolve[Trait, In](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, In]],
        $input
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          ${ attachTriggerSchedule[Trait]('resolved) }
      }
    }
  }

  def getPhantomImpl[Trait: Type, In: Type](input: Expr[In], phantom: Expr[Uuid])(using Quotes): Expr[Trait] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val got      = TypeRepr.of[In]
    if !(got =:= expected) then
      report.errorAndAbort(
        s"getPhantom(input, phantom) requires: BaseAgent[${expected.show}] (found argument type: ${got.show})"
      )
    '{
      AgentClientRuntime.resolveWithPhantom[Trait, In](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, In]],
        $input,
        phantom = Some($phantom)
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          ${ attachTriggerSchedule[Trait]('resolved) }
      }
    }
  }

  def getUnitImpl[Trait: Type](using Quotes): Expr[Trait] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    if !(expected =:= TypeRepr.of[Unit]) then
      report.errorAndAbort(s"get() requires: BaseAgent[Unit] (found: ${expected.show})")
    '{
      AgentClientRuntime.resolve[Trait, Unit](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Unit]],
        ()
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          ${ attachTriggerSchedule[Trait]('resolved) }
      }
    }
  }

  def getPhantomUnitImpl[Trait: Type](phantom: Expr[Uuid])(using Quotes): Expr[Trait] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    if !(expected =:= TypeRepr.of[Unit]) then
      report.errorAndAbort(s"getPhantom(phantom) requires: BaseAgent[Unit] (found: ${expected.show})")
    '{
      AgentClientRuntime.resolveWithPhantom[Trait, Unit](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Unit]],
        (),
        phantom = Some($phantom)
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          ${ attachTriggerSchedule[Trait]('resolved) }
      }
    }
  }

  def getTuple2Impl[Trait: Type, A1: Type, A2: Type](a1: Expr[A1], a2: Expr[A2])(using Quotes): Expr[Trait] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple2[A1, A2]]
    if !(expected =:= want) then
      report.errorAndAbort(s"get(a1, a2) requires: BaseAgent[${want.show}] (found: ${expected.show})")
    val tup = '{ Tuple2($a1, $a2) }
    '{
      AgentClientRuntime.resolve[Trait, Tuple2[A1, A2]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple2[A1, A2]]],
        $tup
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          ${ attachTriggerSchedule[Trait]('resolved) }
      }
    }
  }

  def getPhantomTuple2Impl[Trait: Type, A1: Type, A2: Type](
    a1: Expr[A1],
    a2: Expr[A2],
    phantom: Expr[Uuid]
  )(using Quotes): Expr[Trait] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple2[A1, A2]]
    if !(expected =:= want) then
      report.errorAndAbort(
        s"getPhantom(a1, a2, phantom) requires: BaseAgent[${want.show}] (found: ${expected.show})"
      )
    val tup = '{ Tuple2($a1, $a2) }
    '{
      AgentClientRuntime.resolveWithPhantom[Trait, Tuple2[A1, A2]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple2[A1, A2]]],
        $tup,
        phantom = Some($phantom)
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          ${ attachTriggerSchedule[Trait]('resolved) }
      }
    }
  }

  def getTuple3Impl[Trait: Type, A1: Type, A2: Type, A3: Type](a1: Expr[A1], a2: Expr[A2], a3: Expr[A3])(using
    Quotes
  ): Expr[Trait] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple3[A1, A2, A3]]
    if !(expected =:= want) then
      report.errorAndAbort(s"get(a1, a2, a3) requires: BaseAgent[${want.show}] (found: ${expected.show})")
    val tup = '{ Tuple3($a1, $a2, $a3) }
    '{
      AgentClientRuntime.resolve[Trait, Tuple3[A1, A2, A3]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple3[A1, A2, A3]]],
        $tup
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          ${ attachTriggerSchedule[Trait]('resolved) }
      }
    }
  }

  def getPhantomTuple3Impl[Trait: Type, A1: Type, A2: Type, A3: Type](
    a1: Expr[A1],
    a2: Expr[A2],
    a3: Expr[A3],
    phantom: Expr[Uuid]
  )(using Quotes): Expr[Trait] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple3[A1, A2, A3]]
    if !(expected =:= want) then
      report.errorAndAbort(
        s"getPhantom(a1, a2, a3, phantom) requires: BaseAgent[${want.show}] (found: ${expected.show})"
      )
    val tup = '{ Tuple3($a1, $a2, $a3) }
    '{
      AgentClientRuntime.resolveWithPhantom[Trait, Tuple3[A1, A2, A3]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple3[A1, A2, A3]]],
        $tup,
        phantom = Some($phantom)
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          ${ attachTriggerSchedule[Trait]('resolved) }
      }
    }
  }

  def getTuple4Impl[Trait: Type, A1: Type, A2: Type, A3: Type, A4: Type](
    a1: Expr[A1],
    a2: Expr[A2],
    a3: Expr[A3],
    a4: Expr[A4]
  )(using Quotes): Expr[Trait] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple4[A1, A2, A3, A4]]
    if !(expected =:= want) then
      report.errorAndAbort(s"get(a1, a2, a3, a4) requires: BaseAgent[${want.show}] (found: ${expected.show})")
    val tup = '{ Tuple4($a1, $a2, $a3, $a4) }
    '{
      AgentClientRuntime.resolve[Trait, Tuple4[A1, A2, A3, A4]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple4[A1, A2, A3, A4]]],
        $tup
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          ${ attachTriggerSchedule[Trait]('resolved) }
      }
    }
  }

  def getPhantomTuple4Impl[Trait: Type, A1: Type, A2: Type, A3: Type, A4: Type](
    a1: Expr[A1],
    a2: Expr[A2],
    a3: Expr[A3],
    a4: Expr[A4],
    phantom: Expr[Uuid]
  )(using Quotes): Expr[Trait] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple4[A1, A2, A3, A4]]
    if !(expected =:= want) then
      report.errorAndAbort(
        s"getPhantom(a1, a2, a3, a4, phantom) requires: BaseAgent[${want.show}] (found: ${expected.show})"
      )
    val tup = '{ Tuple4($a1, $a2, $a3, $a4) }
    '{
      AgentClientRuntime.resolveWithPhantom[Trait, Tuple4[A1, A2, A3, A4]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple4[A1, A2, A3, A4]]],
        $tup,
        phantom = Some($phantom)
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          ${ attachTriggerSchedule[Trait]('resolved) }
      }
    }
  }

  def getTuple5Impl[Trait: Type, A1: Type, A2: Type, A3: Type, A4: Type, A5: Type](
    a1: Expr[A1],
    a2: Expr[A2],
    a3: Expr[A3],
    a4: Expr[A4],
    a5: Expr[A5]
  )(using Quotes): Expr[Trait] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple5[A1, A2, A3, A4, A5]]
    if !(expected =:= want) then
      report.errorAndAbort(
        s"get(a1, a2, a3, a4, a5) requires: BaseAgent[${want.show}] (found: ${expected.show})"
      )
    val tup = '{ Tuple5($a1, $a2, $a3, $a4, $a5) }
    '{
      AgentClientRuntime.resolve[Trait, Tuple5[A1, A2, A3, A4, A5]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple5[A1, A2, A3, A4, A5]]],
        $tup
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          ${ attachTriggerSchedule[Trait]('resolved) }
      }
    }
  }

  def getPhantomTuple5Impl[Trait: Type, A1: Type, A2: Type, A3: Type, A4: Type, A5: Type](
    a1: Expr[A1],
    a2: Expr[A2],
    a3: Expr[A3],
    a4: Expr[A4],
    a5: Expr[A5],
    phantom: Expr[Uuid]
  )(using Quotes): Expr[Trait] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple5[A1, A2, A3, A4, A5]]
    if !(expected =:= want) then
      report.errorAndAbort(
        s"getPhantom(a1, a2, a3, a4, a5, phantom) requires: BaseAgent[${want.show}] (found: ${expected.show})"
      )
    val tup = '{ Tuple5($a1, $a2, $a3, $a4, $a5) }
    '{
      AgentClientRuntime.resolveWithPhantom[Trait, Tuple5[A1, A2, A3, A4, A5]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple5[A1, A2, A3, A4, A5]]],
        $tup,
        phantom = Some($phantom)
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          ${ attachTriggerSchedule[Trait]('resolved) }
      }
    }
  }

  private def agentInputTypeRepr[Trait: Type](using Quotes): quotes.reflect.TypeRepr = {
    import quotes.reflect.*
    val traitRepr = TypeRepr.of[Trait]
    val baseSym   = traitRepr.baseClasses.find(_.fullName == "golem.BaseAgent").getOrElse(Symbol.noSymbol)
    if (baseSym == Symbol.noSymbol) TypeRepr.of[Unit]
    else
      traitRepr.baseType(baseSym) match {
        case AppliedType(_, List(arg)) => arg.dealias
        case _                         => TypeRepr.of[Unit]
      }
  }

  private def attachTriggerSchedule[Trait: Type](
    resolvedExpr: Expr[AgentClientRuntime.ResolvedAgent[Trait]]
  )(using Quotes): Expr[Trait] = {
    import quotes.reflect.*

    case class MethodData(
      method: Symbol,
      params: List[(String, TypeRepr)],
      accessMode: MethodParamAccess,
      inputType: TypeRepr,
      outputType: TypeRepr
    )

    val traitRepr   = TypeRepr.of[Trait]
    val traitSymbol = traitRepr.typeSymbol

    if !traitSymbol.flags.is(Flags.Trait) then
      report.errorAndAbort(s"Agent client target must be a trait, found: ${traitSymbol.fullName}")

    val methods: List[MethodData] =
      traitSymbol.methodMembers.collect {
        case m if m.flags.is(Flags.Deferred) && m.isDefDef && m.name != "new" =>
          val params                                    = extractParameters(m)
          val accessMode                                = methodAccess(params)
          val inputType                                 = inputTypeFor(accessMode, params)
          val (outputType, _) /* Future[Out] or Unit */ =
            returnAndOutputTypeFor(m)
          MethodData(m, params, accessMode, inputType, outputType)
      }

    val resolvedSym =
      Symbol.newVal(
        Symbol.spliceOwner,
        "$resolvedAgent",
        TypeRepr.of[AgentClientRuntime.ResolvedAgent[Trait]],
        Flags.EmptyFlags,
        Symbol.noSymbol
      )
    val resolvedVal = ValDef(resolvedSym, Some(resolvedExpr.asTerm))
    val resolvedRef = Ref(resolvedSym).asExprOf[AgentClientRuntime.ResolvedAgent[Trait]]

    val baseSym =
      Symbol.newVal(Symbol.spliceOwner, "$agent", traitRepr, Flags.EmptyFlags, Symbol.noSymbol)
    val baseVal    = ValDef(baseSym, Some('{ AgentClient.bind[Trait]($resolvedRef) }.asTerm))
    val baseDynSym =
      Symbol.newVal(Symbol.spliceOwner, "$agentDyn", TypeRepr.of[js.Dynamic], Flags.EmptyFlags, Symbol.noSymbol)
    val baseDynVal =
      ValDef(baseDynSym, Some('{ ${ Ref(baseSym).asExprOf[Trait] }.asInstanceOf[js.Dynamic] }.asTerm))
    val baseRef = Ref(baseDynSym).asExprOf[js.Dynamic]

    val triggerSym =
      Symbol.newVal(Symbol.spliceOwner, "$trigger", TypeRepr.of[AgentMethodProxy], Flags.EmptyFlags, Symbol.noSymbol)
    val triggerVal = ValDef(triggerSym, Some('{ new AgentMethodProxy() }.asTerm))
    val triggerRef = Ref(triggerSym).asExprOf[AgentMethodProxy]

    val scheduleSym =
      Symbol.newVal(Symbol.spliceOwner, "$schedule", TypeRepr.of[AgentMethodProxy], Flags.EmptyFlags, Symbol.noSymbol)
    val scheduleVal = ValDef(scheduleSym, Some('{ new AgentMethodProxy() }.asTerm))
    val scheduleRef = Ref(scheduleSym).asExprOf[AgentMethodProxy]

    def findMethod[In: Type, Out: Type](
      methodName: String
    ): Expr[golem.runtime.agenttype.AgentMethod[Trait, In, Out]] = {
      val methodNameExpr = Expr(methodName)
      '{
        $resolvedRef.agentType.methods.collectFirst {
          case mm if mm.metadata.name == $methodNameExpr =>
            mm.asInstanceOf[golem.runtime.agenttype.AgentMethod[Trait, In, Out]]
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

    def buildFn(method: MethodData, op: String): Expr[js.Any] = {
      val methodName = method.method.name

      method.inputType.asType match {
        case '[in] =>
          method.outputType.asType match {
            case '[out] =>
              val methodExpr = findMethod[in, out](methodName)

              val fnTerm: Term =
                op match {
                  case "trigger" =>
                    val mt =
                      MethodType(method.params.map(_._1))(
                        _ => method.params.map(_._2),
                        _ => TypeRepr.of[scala.concurrent.Future[Unit]]
                      )
                    Lambda(
                      Symbol.spliceOwner,
                      mt,
                      (owner, params) => {
                        val args    = params.collect { case t: Term => t.asExprOf[Any] }.toList
                        val inValue =
                          buildInputValueExpr(method.accessMode, method.inputType, args).asExprOf[in]
                        '{ $resolvedRef.trigger[in]($methodExpr, $inValue) }.asTerm.changeOwner(owner)
                      }
                    )
                  case "schedule" =>
                    val mt =
                      MethodType("datetime" :: method.params.map(_._1))(
                        _ => TypeRepr.of[Datetime] :: method.params.map(_._2),
                        _ => TypeRepr.of[scala.concurrent.Future[Unit]]
                      )
                    Lambda(
                      Symbol.spliceOwner,
                      mt,
                      (owner, params) => {
                        val all = params.collect { case t: Term => t }.toList
                        val dt  =
                          all.headOption.getOrElse(
                            report.errorAndAbort(s"schedule.${methodName} missing datetime argument")
                          )
                        val rest    = all.drop(1)
                        val args    = rest.map(_.asExprOf[Any])
                        val inValue =
                          buildInputValueExpr(method.accessMode, method.inputType, args).asExprOf[in]
                        val dtExpr = dt.asExprOf[Datetime]
                        '{ $resolvedRef.schedule[in]($methodExpr, $dtExpr, $inValue) }.asTerm.changeOwner(owner)
                      }
                    )
                  case other =>
                    report.errorAndAbort(s"Unsupported op: $other")
                }

              val fnExpr = fnTerm.asExprOf[Any]

              op match {
                case "schedule" =>
                  (method.params.length + 1) match {
                    case 1 => '{ js.Any.fromFunction1($fnExpr.asInstanceOf[Any => Any]) }
                    case 2 => '{ js.Any.fromFunction2($fnExpr.asInstanceOf[(Any, Any) => Any]) }
                    case 3 => '{ js.Any.fromFunction3($fnExpr.asInstanceOf[(Any, Any, Any) => Any]) }
                    case 4 => '{ js.Any.fromFunction4($fnExpr.asInstanceOf[(Any, Any, Any, Any) => Any]) }
                    case n =>
                      report.errorAndAbort(
                        s"Unsupported agent method arity for schedule.${methodName}: $n (supported: 1-4 including datetime)"
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
                        s"Unsupported agent method arity for trigger.${methodName}: $n (supported: 0-3)"
                      )
                  }
              }
          }
      }
    }

    val triggerUpdates: List[Statement] =
      methods.map { m =>
        val plainName = Expr(m.method.name)
        val fn        = buildFn(m, "trigger")
        '{ $triggerRef.register($plainName, $fn) }.asTerm
      }

    val scheduleUpdates: List[Statement] =
      methods.map { m =>
        val plainName = Expr(m.method.name)
        val fn        = buildFn(m, "schedule")
        '{ $scheduleRef.register($plainName, $fn) }.asTerm
      }

    val attachTrigger  = '{ $baseRef.updateDynamic("trigger")($triggerRef.asInstanceOf[js.Any]) }.asTerm
    val attachSchedule = '{ $baseRef.updateDynamic("schedule")($scheduleRef.asInstanceOf[js.Any]) }.asTerm

    Block(
      resolvedVal :: baseVal :: baseDynVal :: triggerVal :: scheduleVal :: (triggerUpdates ++ scheduleUpdates :+ attachTrigger :+ attachSchedule),
      Ref(baseSym)
    ).asExprOf[Trait]
  }

  private enum MethodParamAccess {
    case NoArgs
    case SingleArg
    case MultiArgs
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
          case AppliedType(constructor, args)
              if constructor.typeSymbol.fullName == "scala.scalajs.js.Promise" && args.nonEmpty =>
            (args.head, tpe)
          case other =>
            (other, other)
        }
      case other =>
        report.errorAndAbort(s"Unable to read return type for ${method.name}: $other")
    }
  }

  def triggerOpsImpl[Trait: Type](agent: Expr[Trait])(using Quotes): Expr[Selectable] = {
    import quotes.reflect.*
    val traitRepr   = TypeRepr.of[Trait]
    val traitSymbol = traitRepr.typeSymbol

    if !traitSymbol.flags.is(Flags.Trait) then
      report.errorAndAbort(s"Agent client target must be a trait, found: ${traitSymbol.fullName}")

    val methods =
      traitSymbol.methodMembers.collect {
        case m if m.flags.is(Flags.Deferred) && m.isDefDef && m.name != "new" =>
          val params = extractParameters(m)
          (m, params)
      }

    // Build a refined type on AgentMethodProxy (which has applyDynamic) so the
    // compiler knows about the available trigger methods and can dispatch them.
    val triggerType: TypeRepr =
      methods.foldLeft(TypeRepr.of[AgentMethodProxy]) { case (acc, (method, params)) =>
        val triggerTpe =
          MethodType(params.map(_._1))(_ => params.map(_._2), _ => TypeRepr.of[scala.concurrent.Future[Unit]])
        Refinement(acc, method.name, triggerTpe)
      }

    // Access via js.Dynamic (trigger is attached at runtime by attachTriggerSchedule)
    val proxy = '{ $agent.asInstanceOf[js.Dynamic].selectDynamic("trigger").asInstanceOf[AgentMethodProxy] }

    triggerType.asType match {
      case '[t] => '{ $proxy.asInstanceOf[t] }.asExprOf[Selectable]
    }
  }

  def scheduleOpsImpl[Trait: Type](agent: Expr[Trait])(using Quotes): Expr[Selectable] = {
    import quotes.reflect.*
    val traitRepr   = TypeRepr.of[Trait]
    val traitSymbol = traitRepr.typeSymbol

    if !traitSymbol.flags.is(Flags.Trait) then
      report.errorAndAbort(s"Agent client target must be a trait, found: ${traitSymbol.fullName}")

    val methods =
      traitSymbol.methodMembers.collect {
        case m if m.flags.is(Flags.Deferred) && m.isDefDef && m.name != "new" =>
          val params = extractParameters(m)
          (m, params)
      }

    // Build a refined type on AgentMethodProxy (which has applyDynamic) so the
    // compiler knows about the available schedule methods and can dispatch them.
    val scheduleType: TypeRepr =
      methods.foldLeft(TypeRepr.of[AgentMethodProxy]) { case (acc, (method, params)) =>
        val scheduleTpe =
          MethodType("datetime" :: params.map(_._1))(
            _ => TypeRepr.of[Datetime] :: params.map(_._2),
            _ => TypeRepr.of[scala.concurrent.Future[Unit]]
          )
        Refinement(acc, method.name, scheduleTpe)
      }

    // Access via js.Dynamic (schedule is attached at runtime by attachTriggerSchedule)
    val proxy = '{ $agent.asInstanceOf[js.Dynamic].selectDynamic("schedule").asInstanceOf[AgentMethodProxy] }

    scheduleType.asType match {
      case '[t] => '{ $proxy.asInstanceOf[t] }.asExprOf[Selectable]
    }
  }
}
