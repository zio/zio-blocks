package cloud.golem.runtime.rpc

import cloud.golem.runtime.plan.AgentClientPlan
import scala.language.experimental.macros

object AgentClient {
  /**
   * Resolves an agent id + RPC invoker for a trait + constructor args.
   *
   * In Scala 2 (Scala.js), prefer:
   *   1) `val plan = AgentClient.plan[MyAgent]`
   *   2) `val resolved = AgentClient.resolve(plan, ctorArgs)`
   *   3) `val client = AgentClient.bind[MyAgent](resolved)`
   *
   * This avoids relying on Java reflection proxies (which Scala.js doesn't support).
   */
  def resolve[Trait, Constructor](
    plan: AgentClientPlan[Trait, Constructor],
    constructorArgs: Constructor
  ): Either[String, AgentClientRuntime.ResolvedAgent[Trait]] =
    AgentClientRuntime.resolve(plan, constructorArgs)

  def bind[Trait](
    resolved: AgentClientRuntime.ResolvedAgent[Trait]
  ): Trait = macro AgentClientBindMacro.bindImpl[Trait]

  def plan[Trait]: AgentClientPlan[Trait, _] = macro cloud.golem.runtime.macros.AgentClientMacroImpl.planImpl[Trait]
}

private[rpc] object AgentClientBindMacro {
  def bindImpl[Trait: c.WeakTypeTag](c: scala.reflect.macros.blackbox.Context)(
    resolved: c.Expr[AgentClientRuntime.ResolvedAgent[Trait]]
  ): c.Expr[Trait] = {
    import c.universe._

    val traitTpe = weakTypeOf[Trait]
    val traitSym = traitTpe.typeSymbol

    if (!traitSym.isClass || !traitSym.asClass.isTrait)
      c.abort(c.enclosingPosition, s"Agent client target must be a trait, found: ${traitSym.fullName}")

    val futureSym = typeOf[scala.concurrent.Future[_]].typeSymbol

    def isPromiseReturn(tpe: Type): Boolean =
      tpe.typeSymbol.fullName == "scala.scalajs.js.Promise"

    def isFutureReturn(tpe: Type): Boolean =
      tpe.typeSymbol == futureSym

    def unwrapAsync(tpe: Type): Type =
      tpe match {
        case TypeRef(_, sym, List(arg)) if sym == futureSym => arg
        case TypeRef(_, sym, List(arg)) if sym.fullName == "scala.scalajs.js.Promise" => arg
        case other => other
      }

    val methods: List[MethodSymbol] =
      traitTpe.decls.collect {
        case m: MethodSymbol if m.isAbstract && m.isMethod && m.name.toString != "new" => m
      }.toList

    val resolvedValName = TermName(c.freshName("resolved"))
    val resolvedValDef  = q"val $resolvedValName = $resolved"
    val resolvedRef     = Ident(resolvedValName)

    def methodPlanLookup(methodName: String, inTpe: Type, outTpe: Type): Tree = {
      q"""
        $resolvedRef.plan.methods
          .collectFirst {
            case p if p.metadata.name == $methodName =>
              p.asInstanceOf[_root_.cloud.golem.runtime.plan.ClientMethodPlan[$traitTpe, $inTpe, $outTpe]]
          }
          .getOrElse(throw new _root_.java.lang.IllegalStateException("Method plan for " + $methodName + " not found"))
      """
    }

    def inputExpr(paramss: List[List[ValDef]]): Tree = {
      val params = paramss.flatten
      params match {
        case Nil => q"()"
        case one :: Nil => q"${Ident(one.name)}"
        case many =>
          q"_root_.scala.collection.immutable.Vector(..${many.map(p => Ident(p.name))})"
      }
    }

    def mkParamValDef(p: Symbol): ValDef = {
      val name = p.name.toTermName
      val tpt  = TypeTree(p.typeSignature)
      ValDef(Modifiers(Flag.PARAM), name, tpt, EmptyTree)
    }

    def mkMethodDef(m: MethodSymbol, rhs: Tree): DefDef = {
      val name     = m.name.toTermName
      val tparams  = Nil
      val paramss  = m.paramLists.map(_.map(mkParamValDef))
      val retTpt   = TypeTree(m.returnType)
      DefDef(Modifiers(Flag.OVERRIDE), name, tparams, paramss, retTpt, rhs)
    }

    val methodDefs: List[DefDef] = methods.map { m =>
      val methodNameStr = m.name.toString
      val paramss: List[List[ValDef]] = m.paramLists.map(_.map(mkParamValDef))
      val returnTpe = m.returnType

      val inType: Type =
        m.paramLists.flatten match {
          case Nil => typeOf[Unit]
          case one :: Nil => one.typeSignature
          case _ => typeOf[Vector[Any]]
        }

      if (returnTpe =:= typeOf[Unit]) {
        val planLookup = methodPlanLookup(methodNameStr, inType, typeOf[Unit])
        val inValue    = inputExpr(paramss)
        val rhs =
          q"""
            val plan = $planLookup
            $resolvedRef
              .trigger(plan, $inValue.asInstanceOf[$inType])
              .failed
              .foreach(err => _root_.scala.scalajs.js.Dynamic.global.console.error("RPC trigger " + $methodNameStr + " failed", err.asInstanceOf[_root_.scala.scalajs.js.Any]))
            ()
          """
        mkMethodDef(m, rhs)
      } else if (isFutureReturn(returnTpe) || isPromiseReturn(returnTpe)) {
        val outType   = unwrapAsync(returnTpe)
        val planLookup = methodPlanLookup(methodNameStr, inType, outType)
        val inValue    = inputExpr(paramss)
        val rhs =
          q"""
            val plan = $planLookup
            $resolvedRef.call(plan, $inValue.asInstanceOf[$inType])
          """
        mkMethodDef(m, rhs)
      } else {
        c.abort(
          c.enclosingPosition,
          s"Agent client method $methodNameStr must return scala.concurrent.Future[...] (or js.Promise[...]) or Unit, found: $returnTpe"
        )
      }
    }

    val anon =
      q"""
        new $traitTpe {
          ..$methodDefs
        }
      """

    c.Expr[Trait](q"{ $resolvedValDef; $anon }")
  }
}
