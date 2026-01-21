package golem.runtime.macros

import scala.concurrent.Future
import scala.reflect.macros.blackbox

object RemoteAgentOpsMacro {
  def rpcImpl[Trait: c.WeakTypeTag](c: blackbox.Context): c.Expr[Any] = {
    import c.universe._

    val traitTpe = weakTypeOf[Trait]
    val traitSym = traitTpe.typeSymbol

    if (!traitSym.isClass || !traitSym.asClass.isTrait) {
      c.abort(c.enclosingPosition, s"RemoteAgentOps.rpc target must be a trait, found: ${traitSym.fullName}")
    }

    val remoteRef = q"${c.prefix}.remote"
    val resolvedRef = q"$remoteRef.resolved"

    val futureSym = typeOf[Future[_]].typeSymbol

    def isPromiseReturn(tpe: Type): Boolean =
      tpe.typeSymbol.fullName == "scala.scalajs.js.Promise"

    def isFutureReturn(tpe: Type): Boolean =
      tpe.typeSymbol == futureSym

    def unwrapAsync(tpe: Type): Type =
      tpe match {
        case TypeRef(_, sym, List(arg)) if sym == futureSym                           => arg
        case TypeRef(_, sym, List(arg)) if sym.fullName == "scala.scalajs.js.Promise" => arg
        case other                                                                    => other
      }

    def inputExpr(paramss: List[List[ValDef]]): Tree = {
      val params = paramss.flatten
      params match {
        case Nil        => q"()"
        case one :: Nil => q"${Ident(one.name)}"
        case many       =>
          q"_root_.scala.collection.immutable.Vector(..${many.map(p => Ident(p.name))})"
      }
    }

    def mkParamValDef(p: Symbol): ValDef = {
      val name = p.name.toTermName
      val tpt  = TypeTree(p.typeSignature)
      ValDef(Modifiers(Flag.PARAM), name, tpt, EmptyTree)
    }

    def mkMethodDef(name: TermName, paramss: List[List[ValDef]], ret: Type, rhs: Tree): DefDef =
      DefDef(Modifiers(), name, Nil, paramss, TypeTree(ret), rhs)

    def methodLookup(methodName: String, inTpe: Type, outTpe: Type): Tree =
      q"""
        $resolvedRef.agentType.methods.collectFirst {
          case m if m.metadata.name == $methodName =>
            m.asInstanceOf[_root_.golem.runtime.agenttype.AgentMethod[$traitTpe, $inTpe, $outTpe]]
        }.getOrElse(throw new _root_.java.lang.IllegalStateException("Method definition for " + $methodName + " not found"))
      """

    val methodDefs: List[DefDef] =
      traitTpe.decls.collect {
        case m: MethodSymbol if m.isAbstract && m.isMethod && m.name.toString != "new" =>
          val methodNameStr               = m.name.toString
          val baseParamss                 = m.paramLists.map(_.map(mkParamValDef))
          val baseParams                  = m.paramLists.flatten
          val inType: Type =
            baseParams match {
              case Nil        => typeOf[Unit]
              case one :: Nil => one.typeSignature
              case _          => typeOf[Vector[Any]]
            }

          val outType: Type =
            if (m.returnType =:= typeOf[Unit]) typeOf[Unit]
            else if (isFutureReturn(m.returnType) || isPromiseReturn(m.returnType)) unwrapAsync(m.returnType)
            else {
              c.abort(
                c.enclosingPosition,
                s"RemoteAgentOps.rpc method $methodNameStr must return scala.concurrent.Future[...] (or js.Promise[...]) or Unit, found: ${m.returnType}"
              )
            }

          val methodLookup0 = methodLookup(methodNameStr, inType, outType)
          val inValue       = inputExpr(baseParamss)

          val callName    = TermName(s"call_$methodNameStr")
          val triggerName = TermName(s"trigger_$methodNameStr")
          val schedName   = TermName(s"schedule_$methodNameStr")

          val callDef =
            mkMethodDef(
              callName,
              baseParamss,
              appliedType(typeOf[_root_.scala.concurrent.Future[Any]].typeConstructor, outType),
              q"{ val method = $methodLookup0; $resolvedRef.await(method, $inValue.asInstanceOf[$inType]) }"
            )

          val triggerDef =
            mkMethodDef(
              triggerName,
              baseParamss,
              typeOf[Future[Unit]],
              q"{ val method = $methodLookup0; $resolvedRef.trigger(method, $inValue.asInstanceOf[$inType]) }"
            )

          val datetimeParam = ValDef(Modifiers(Flag.PARAM), TermName("datetime"), TypeTree(typeOf[_root_.golem.Datetime]), EmptyTree)
          val schedParamss  = datetimeParam :: baseParamss.flatten
          val schedDef =
            mkMethodDef(
              schedName,
              List(schedParamss),
              typeOf[Future[Unit]],
              q"{ val method = $methodLookup0; $resolvedRef.schedule(method, datetime, $inValue.asInstanceOf[$inType]) }"
            )

          List(callDef, triggerDef, schedDef)
      }.toList.flatten

    val anon =
      q"""
        new {
          ..$methodDefs
        }
      """

    c.Expr[Any](anon)
  }
}
