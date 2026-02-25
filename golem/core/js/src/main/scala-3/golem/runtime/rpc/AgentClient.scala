package golem.runtime.rpc

import golem.runtime.macros.AgentClientMacro
import golem.runtime.agenttype.{AgentMethod, AgentType}
import golem.Uuid

import scala.collection.immutable.Vector
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

object AgentClient {
  transparent inline def agentType[Trait]: AgentType[Trait, ?] =
    AgentClientMacro.agentType[Trait]

  /**
   * Typed agent-type accessor (no user-land casts).
   *
   * This exists because Scala.js cannot safely cast a plain JS object to a
   * Scala trait at runtime. When you need to operate at the "agent type +
   * resolved client" level (e.g. in internal wiring), use this API to keep
   * examples cast-free.
   */
  transparent inline def agentTypeWithCtor[Trait, Constructor]: AgentType[Trait, Constructor] =
    ${ AgentTypeMacro.agentTypeWithCtorImpl[Trait, Constructor] }

  transparent inline def connect[Trait, Constructor](
    agentType: AgentType[Trait, Constructor],
    constructorArgs: Constructor
  ): Trait =
    AgentClientInlineMacros.connect[Trait, Constructor](agentType, constructorArgs)

  transparent inline def connectPhantom[Trait, Constructor](
    agentType: AgentType[Trait, Constructor],
    constructorArgs: Constructor,
    phantom: Uuid
  ): Trait =
    AgentClientInlineMacros.connectPhantom[Trait, Constructor](agentType, constructorArgs, phantom)

  transparent inline def bind[Trait](
    resolved: AgentClientRuntime.ResolvedAgent[Trait]
  ): Trait =
    AgentClientRuntime.TestHooks.bindOverride(resolved).getOrElse {
      AgentClientInlineMacros.bind[Trait](resolved)
    }
}

private object AgentTypeMacro {
  import scala.quoted.*

  def agentTypeWithCtorImpl[Trait: Type, Constructor: Type](using Quotes): Expr[AgentType[Trait, Constructor]] = {
    import quotes.reflect.*

    val traitRepr   = TypeRepr.of[Trait]
    val traitSymbol = traitRepr.typeSymbol

    if !traitSymbol.flags.is(Flags.Trait) then
      report.errorAndAbort(s"Agent client target must be a trait, found: ${traitSymbol.fullName}")

    val expectedCtor: TypeRepr = {
      val baseSym = traitRepr.baseClasses.find(_.fullName == "golem.BaseAgent").getOrElse(Symbol.noSymbol)
      if (baseSym == Symbol.noSymbol) TypeRepr.of[Unit]
      else
        traitRepr.baseType(baseSym) match {
          case AppliedType(_, List(arg)) => arg
          case _                         => TypeRepr.of[Unit]
        }
    }.dealias.widen

    val gotCtor = TypeRepr.of[Constructor].dealias.widen

    if !(gotCtor =:= expectedCtor) then
      report.errorAndAbort(
        s"AgentClient.agentTypeWithCtor requires: BaseAgent[${expectedCtor.show}] (found: ${gotCtor.show})"
      )

    '{ AgentClientMacro.agentType[Trait].asInstanceOf[AgentType[Trait, Constructor]] }
  }
}

private object AgentClientInlineMacros {
  import scala.quoted.*

  transparent inline def connect[Trait, Constructor](
    agentType: AgentType[Trait, Constructor],
    constructorArgs: Constructor
  ): Trait =
    ${ connectImpl[Trait, Constructor]('agentType, 'constructorArgs) }

  transparent inline def connectPhantom[Trait, Constructor](
    agentType: AgentType[Trait, Constructor],
    constructorArgs: Constructor,
    phantom: Uuid
  ): Trait =
    ${ connectPhantomImpl[Trait, Constructor]('agentType, 'constructorArgs, 'phantom) }

  transparent inline def bind[Trait](
    resolved: AgentClientRuntime.ResolvedAgent[Trait]
  ): Trait =
    ${ stubImpl[Trait]('resolved) }

  private def connectImpl[Trait: Type, Constructor: Type](
    agentTypeExpr: Expr[AgentType[Trait, Constructor]],
    constructorExpr: Expr[Constructor]
  )(using Quotes): Expr[Trait] =
    '{
      AgentClientRuntime.resolve[Trait, Constructor]($agentTypeExpr, $constructorExpr) match {
        case Left(err) =>
          throw js.JavaScriptException(err)
        case Right(resolved) =>
          ${ stubImpl[Trait]('resolved) }
      }
    }

  private def connectPhantomImpl[Trait: Type, Constructor: Type](
    agentTypeExpr: Expr[AgentType[Trait, Constructor]],
    constructorExpr: Expr[Constructor],
    phantomExpr: Expr[Uuid]
  )(using Quotes): Expr[Trait] =
    '{
      AgentClientRuntime.resolveWithPhantom[Trait, Constructor](
        $agentTypeExpr,
        $constructorExpr,
        phantom = Some($phantomExpr)
      ) match {
        case Left(err) =>
          throw js.JavaScriptException(err)
        case Right(resolved) =>
          ${ stubImpl[Trait]('resolved) }
      }
    }

  private def stubImpl[Trait: Type](
    resolvedExpr: Expr[AgentClientRuntime.ResolvedAgent[Trait]]
  )(using Quotes): Expr[Trait] = {
    import quotes.reflect.*

    case class MethodData(
      method: Symbol,
      params: List[(String, TypeRepr)],
      accessMode: MethodParamAccess,
      inputType: TypeRepr,
      outputType: TypeRepr,
      returnType: TypeRepr,
      invocation: InvocationKind
    )

    val traitRepr   = TypeRepr.of[Trait]
    val traitSymbol = traitRepr.typeSymbol

    if !traitSymbol.flags.is(Flags.Trait) then
      report.errorAndAbort(s"Agent client target must be a trait, found: ${traitSymbol.fullName}")

    val pendingMethods = traitSymbol.methodMembers.collect {
      case method if method.flags.is(Flags.Deferred) && method.isDefDef && method.name != "new" =>
        val params                                   = extractParameters(method)
        val accessMode                               = methodAccess(params)
        val inputType                                = inputTypeFor(accessMode, params)
        val (invocationKind, outputType, returnType) = methodInvocationInfo(method)

        MethodData(
          method = method,
          params = params,
          accessMode = accessMode,
          inputType = inputType,
          outputType = outputType,
          returnType = returnType,
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

    def findMethod[In: Type, Out: Type](methodName: String): Expr[AgentMethod[Trait, In, Out]] = {
      val methodNameExpr = Expr(methodName)
      '{
        $resolvedRef.agentType.methods.collectFirst {
          case m if m.metadata.name == $methodNameExpr =>
            m.asInstanceOf[AgentMethod[Trait, In, Out]]
        }
          .getOrElse(throw new IllegalStateException(s"Method definition for ${$methodNameExpr} not found"))
      }
    }

    def buildMethodBody(methodData: MethodData, paramExprs: List[Expr[Any]]): Expr[Any] = {
      val methodNameExpr: Expr[String] = Expr(methodData.method.name)

      methodData.inputType.asType match {
        case '[input] =>
          methodData.outputType.asType match {
            case '[output] =>
              val inputValueExpr =
                buildInputValueExpr(methodData.accessMode, methodData.inputType, paramExprs).asExprOf[input]
              val methodExpr =
                findMethod[input, output](methodData.method.name)

              methodData.invocation match {
                case InvocationKind.Awaitable =>
                  '{

                    $resolvedRef.call[input, output](
                      $methodExpr,
                      $inputValueExpr
                    )
                  }
                case InvocationKind.FireAndForget =>
                  if !(methodData.outputType =:= TypeRepr.of[Unit]) then
                    report.errorAndAbort(s"Fire-and-forget method ${methodData.method.name} must return Unit")
                  val triggerMethodExpr =
                    methodExpr.asExprOf[AgentMethod[Trait, input, Unit]]
                  '{
                    import scala.concurrent.ExecutionContext.Implicits.global
                    $resolvedRef
                      .trigger[input](
                        $triggerMethodExpr,
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
                case InvocationKind.UnsupportedSync =>
                  val msg =
                    s"Agent client method ${methodData.method.name} must return scala.concurrent.Future[...] or Unit when invoked via RPC."
                  '{ throw new IllegalStateException(${ Expr(msg) }) }
              }
          }
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

    def scalaJsMethodName(methodData: MethodData): String = {
      val paramTypeNames = methodData.params.map(_._2).map(encodeTypeName)
      val resultTypeName = encodeTypeName(methodData.returnType)
      if paramTypeNames.isEmpty then s"${methodData.method.name}__${resultTypeName}"
      else s"${methodData.method.name}__${paramTypeNames.mkString("__")}__${resultTypeName}"
    }

    // Conservative fallback for non-overloaded methods: some Scala.js encodings use only erased param shapes.
    // To avoid "TypeError: not a function" for certain generic/collection types, also publish an erased signature name.
    def encodeErasedParamTypeName(tpe0: TypeRepr): String = {
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
            "A" + encodeErasedParamTypeName(elem)
          case _ =>
            // Erased reference type
            "O"
        }
    }

    def scalaJsMethodNameErased(methodData: MethodData): String = {
      val paramTypeNames = methodData.params.map(_._2).map(encodeErasedParamTypeName)
      val resultTypeName = encodeTypeName(methodData.returnType)
      if paramTypeNames.isEmpty then s"${methodData.method.name}__${resultTypeName}"
      else s"${methodData.method.name}__${paramTypeNames.mkString("__")}__${resultTypeName}"
    }

    val overloadCounts: Map[String, Int] =
      pendingMethods.groupBy(_.method.name).view.mapValues(_.size).toMap

    val objSym =
      Symbol.newVal(Symbol.spliceOwner, "$agentClient", TypeRepr.of[js.Dynamic], Flags.EmptyFlags, Symbol.noSymbol)
    val objVal = ValDef(objSym, Some('{ js.Dynamic.literal() }.asTerm))
    val objRef = Ref(objSym).asExprOf[js.Dynamic]

    val updates: List[Statement] =
      pendingMethods.map { data =>
        val jsName     = scalaJsMethodName(data)
        val jsNameAlt  = scalaJsMethodNameErased(data)
        val methodTpe  = traitRepr.memberType(data.method)
        val lambdaTerm = methodTpe match {
          case mt: MethodType =>
            Lambda(
              Symbol.spliceOwner,
              mt,
              (owner, params) => {
                val paramExprs = params.collect { case t: Term => t.asExprOf[Any] }.toList
                buildMethodBody(data, paramExprs).asTerm.changeOwner(owner)
              }
            )
          case other =>
            report.errorAndAbort(s"Unsupported agent method shape for ${data.method.name}: ${other.show}")
        }

        val fnExpr = lambdaTerm.asExprOf[Any]

        val jsFnExpr: Expr[js.Any] =
          data.params.length match {
            case 0 => '{ js.Any.fromFunction0($fnExpr.asInstanceOf[() => Any]) }
            case 1 => '{ js.Any.fromFunction1($fnExpr.asInstanceOf[Any => Any]) }
            case 2 => '{ js.Any.fromFunction2($fnExpr.asInstanceOf[(Any, Any) => Any]) }
            case 3 => '{ js.Any.fromFunction3($fnExpr.asInstanceOf[(Any, Any, Any) => Any]) }
            case n =>
              report.errorAndAbort(s"Unsupported agent method arity for ${data.method.name}: $n (supported: 0-3)")
          }

        // Always publish the precise name. Publish erased fallback only when the method name is not overloaded.
        val primary = '{ $objRef.updateDynamic(${ Expr(jsName) })($jsFnExpr) }.asTerm
        if (jsNameAlt == jsName || overloadCounts.getOrElse(data.method.name, 0) > 1)
          primary
        else
          Block(
            List(primary),
            '{ $objRef.updateDynamic(${ Expr(jsNameAlt) })($jsFnExpr) }.asTerm
          )
      }

    // Minimal Scala.js runtime type info: provide `$classData.ancestors[...]` entries so `$as_...` succeeds.
    def encodeAncestor(fullName: String): String =
      "L" + fullName.replace('.', '_')

    val ancestorKeys: List[String] =
      traitRepr.baseClasses
        .map(_.fullName)
        .distinct
        .map(encodeAncestor)

    val classDataSym =
      Symbol.newVal(Symbol.spliceOwner, "$classData", TypeRepr.of[js.Dynamic], Flags.EmptyFlags, Symbol.noSymbol)
    val classDataVal =
      ValDef(classDataSym, Some('{ js.Dynamic.literal("ancestors" -> js.Dynamic.literal()) }.asTerm))
    val classDataRef = Ref(classDataSym).asExprOf[js.Dynamic]
    val ancestorsRef = '{ $classDataRef.selectDynamic("ancestors").asInstanceOf[js.Dynamic] }

    val ancestorUpdates: List[Statement] =
      ancestorKeys.map { key =>
        '{ $ancestorsRef.updateDynamic(${ Expr(key) })(1.asInstanceOf[js.Any]) }.asTerm
      }

    val attachClassData: Statement =
      '{ $objRef.updateDynamic("$classData")($classDataRef.asInstanceOf[js.Any]) }.asTerm

    val casted =
      '{ $objRef.asInstanceOf[Trait] }.asTerm

    Block(
      resolvedVal :: objVal :: classDataVal :: (updates ++ ancestorUpdates :+ attachClassData),
      casted
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
  ): (InvocationKind, quotes.reflect.TypeRepr, quotes.reflect.TypeRepr) = {
    import quotes.reflect.*
    method.tree match {
      case d: DefDef =>
        val returnType = d.returnTpt.tpe
        returnType match {
          case AppliedType(constructor, args) if isAsyncReturn(constructor) && args.nonEmpty =>
            (InvocationKind.Awaitable, args.head, returnType)
          case _ =>
            if returnType =:= TypeRepr.of[Unit] then
              (InvocationKind.FireAndForget, TypeRepr.of[Unit], TypeRepr.of[Unit])
            else {
              (InvocationKind.UnsupportedSync, returnType, returnType)
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
    case UnsupportedSync
  }
}
