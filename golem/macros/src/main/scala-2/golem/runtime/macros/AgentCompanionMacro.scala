package golem.runtime.macros

import golem.runtime.plan.AgentClientPlan

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Scala 2 macro implementations backing `golem.AgentCompanion`.
 *
 * These expand at the call site and therefore work even when agent traits and
 * their companions are defined in the same compilation run.
 */
object AgentCompanionMacro {
  private def defaultTypeNameFromTrait(sym: scala.reflect.api.Universe#Symbol): String = {
    val raw = sym.name.decodedName.toString
    raw
      .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
      .toLowerCase
  }

  private def prefixTraitAndInput(c: blackbox.Context): (c.universe.Type, c.universe.Type) = {
    import c.universe._
    val prefix = c.prefix.actualType

    val baseSym = typeOf[_root_.golem.AgentCompanionBase[_]].typeSymbol
    val baseTpe = prefix.baseType(baseSym)
    if (baseTpe == NoType)
      c.abort(
        c.enclosingPosition,
        s"Expected companion to extend golem.AgentCompanionBase[AgentTrait], found: $prefix"
      )

    val traitTpe =
      baseTpe.typeArgs.headOption.getOrElse {
        c.abort(c.enclosingPosition, s"AgentCompanionBase is missing its agent trait type argument (found: $baseTpe)")
      }
    val inputTpe = agentInputType(c)(traitTpe)
    (traitTpe, inputTpe)
  }

  def typeNameImpl(c: blackbox.Context): c.Expr[String] = {
    import c.universe._
    val (traitTpe, _) = prefixTraitAndInput(c)
    val traitSym      = traitTpe.typeSymbol

    val agentDefinitionType = typeOf[_root_.golem.runtime.annotations.agentDefinition]
    val raw                 = traitSym.annotations.collectFirst {
      case ann if ann.tree.tpe != null && ann.tree.tpe =:= agentDefinitionType =>
        ann.tree.children.tail.collectFirst { case Literal(Constant(s: String)) => s }.getOrElse("")
    }.getOrElse("")

    val value = raw.trim
    if (value.nonEmpty) c.Expr[String](Literal(Constant(value)))
    else {
      val hasAnn = traitSym.annotations.exists(a => a.tree.tpe != null && a.tree.tpe =:= agentDefinitionType)
      if (!hasAnn) c.abort(c.enclosingPosition, s"Missing @agentDefinition(...) on agent trait: ${traitSym.fullName}")
      c.Expr[String](Literal(Constant(defaultTypeNameFromTrait(traitSym))))
    }
  }

  def planImpl(c: blackbox.Context): c.Tree = {
    import c.universe._
    val (traitTpe, inTpe) = prefixTraitAndInput(c)
    q"""
       _root_.golem.runtime.rpc.AgentClient
         .plan[$traitTpe]
         .asInstanceOf[_root_.golem.runtime.plan.AgentClientPlan[$traitTpe, $inTpe]]
     """
  }

  def getImpl(c: blackbox.Context)(input: c.Tree): c.Tree = {
    import c.universe._
    val (traitTpe, inTpe) = prefixTraitAndInput(c)
    val planExpr          = planImpl(c)
    q"""
       _root_.golem.runtime.rpc.AgentClientRuntime
         .resolve[$traitTpe, $inTpe](
           $planExpr.asInstanceOf[_root_.golem.runtime.plan.AgentClientPlan[$traitTpe, $inTpe]],
           $input.asInstanceOf[$inTpe]
         ) match {
           case _root_.scala.util.Left(err) =>
             _root_.scala.concurrent.Future.failed(_root_.scala.scalajs.js.JavaScriptException(err))
           case _root_.scala.util.Right(resolved) =>
             _root_.scala.concurrent.Future.successful(
               _root_.golem.runtime.rpc.AgentClient.bind[$traitTpe](resolved)
             )
         }
     """
  }

  def getPhantomImpl(c: blackbox.Context)(input: c.Tree, phantom: c.Tree): c.Tree = {
    import c.universe._
    val (traitTpe, inTpe) = prefixTraitAndInput(c)
    val planExpr          = planImpl(c)
    q"""
       _root_.golem.runtime.rpc.AgentClientRuntime.resolveWithPhantom[$traitTpe, $inTpe](
         $planExpr.asInstanceOf[_root_.golem.runtime.plan.AgentClientPlan[$traitTpe, $inTpe]],
         $input.asInstanceOf[$inTpe],
         phantom = _root_.scala.Some($phantom.asInstanceOf[_root_.golem.Uuid])
       ) match {
         case _root_.scala.util.Left(err) =>
           _root_.scala.concurrent.Future.failed(_root_.scala.scalajs.js.JavaScriptException(err))
         case _root_.scala.util.Right(resolved) =>
           _root_.scala.concurrent.Future.successful(
             _root_.golem.runtime.rpc.AgentClient.bind[$traitTpe](resolved)
           )
       }
     """
  }

  def getUnitImpl(c: blackbox.Context)(): c.Tree = {
    import c.universe._
    val (_, inTpe) = prefixTraitAndInput(c)
    if (!(inTpe =:= typeOf[Unit]))
      c.abort(c.enclosingPosition, s"get() requires: type AgentInput = Unit (found: $inTpe)")
    getImpl(c)(q"()")
  }

  def getPhantomUnitImpl(c: blackbox.Context)(phantom: c.Tree): c.Tree = {
    import c.universe._
    val (_, inTpe) = prefixTraitAndInput(c)
    if (!(inTpe =:= typeOf[Unit]))
      c.abort(c.enclosingPosition, s"getPhantom(phantom) requires: type AgentInput = Unit (found: $inTpe)")
    getPhantomImpl(c)(q"()", phantom)
  }

  def getTuple2Impl[A1: c.WeakTypeTag, A2: c.WeakTypeTag](c: blackbox.Context)(
    a1: c.Expr[A1],
    a2: c.Expr[A2]
  ): c.Tree = {
    import c.universe._
    val (_, inTpe) = prefixTraitAndInput(c)
    val expected   = appliedType(typeOf[Tuple2[_, _]].typeConstructor, List(weakTypeOf[A1], weakTypeOf[A2]))
    if (!(inTpe =:= expected))
      c.abort(c.enclosingPosition, s"get(a1,a2) requires: type AgentInput = ($expected) (found: $inTpe)")
    getImpl(c)(q"_root_.scala.Tuple2($a1, $a2)")
  }

  def getPhantomTuple2Impl[A1: c.WeakTypeTag, A2: c.WeakTypeTag](c: blackbox.Context)(
    a1: c.Expr[A1],
    a2: c.Expr[A2],
    phantom: c.Expr[_root_.golem.Uuid]
  ): c.Tree = {
    import c.universe._
    val (_, inTpe) = prefixTraitAndInput(c)
    val expected   = appliedType(typeOf[Tuple2[_, _]].typeConstructor, List(weakTypeOf[A1], weakTypeOf[A2]))
    if (!(inTpe =:= expected))
      c.abort(c.enclosingPosition, s"getPhantom(a1,a2,phantom) requires: type AgentInput = ($expected) (found: $inTpe)")
    getPhantomImpl(c)(q"_root_.scala.Tuple2($a1, $a2)", phantom.tree)
  }

  def getTuple3Impl[A1: c.WeakTypeTag, A2: c.WeakTypeTag, A3: c.WeakTypeTag](c: blackbox.Context)(
    a1: c.Expr[A1],
    a2: c.Expr[A2],
    a3: c.Expr[A3]
  ): c.Tree = {
    import c.universe._
    val (_, inTpe) = prefixTraitAndInput(c)
    val expected   =
      appliedType(typeOf[Tuple3[_, _, _]].typeConstructor, List(weakTypeOf[A1], weakTypeOf[A2], weakTypeOf[A3]))
    if (!(inTpe =:= expected))
      c.abort(c.enclosingPosition, s"get(a1,a2,a3) requires: type AgentInput = ($expected) (found: $inTpe)")
    getImpl(c)(q"_root_.scala.Tuple3($a1, $a2, $a3)")
  }

  def getPhantomTuple3Impl[A1: c.WeakTypeTag, A2: c.WeakTypeTag, A3: c.WeakTypeTag](c: blackbox.Context)(
    a1: c.Expr[A1],
    a2: c.Expr[A2],
    a3: c.Expr[A3],
    phantom: c.Expr[_root_.golem.Uuid]
  ): c.Tree = {
    import c.universe._
    val (_, inTpe) = prefixTraitAndInput(c)
    val expected   =
      appliedType(typeOf[Tuple3[_, _, _]].typeConstructor, List(weakTypeOf[A1], weakTypeOf[A2], weakTypeOf[A3]))
    if (!(inTpe =:= expected))
      c.abort(
        c.enclosingPosition,
        s"getPhantom(a1,a2,a3,phantom) requires: type AgentInput = ($expected) (found: $inTpe)"
      )
    getPhantomImpl(c)(q"_root_.scala.Tuple3($a1, $a2, $a3)", phantom.tree)
  }

  private def agentInputType(c: blackbox.Context)(traitType: c.universe.Type): c.universe.Type = {
    import c.universe._
    val member = traitType.member(TypeName("AgentInput"))
    if (member == NoSymbol) typeOf[Unit]
    else {
      member.typeSignatureIn(traitType) match {
        case TypeBounds(_, hi) => hi.dealias
        case other             => other.dealias
      }
    }
  }
}
