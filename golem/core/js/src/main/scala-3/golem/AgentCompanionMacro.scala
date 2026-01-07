package golem

import golem.runtime.plan.AgentClientPlan
import golem.runtime.rpc.AgentClient
import golem.Uuid

import scala.concurrent.Future
import scala.quoted.*

/**
 * Scala 3 implementation for `AgentCompanion` methods that need to be checked
 * against `type AgentInput = ...` on the agent trait.
 *
 * This lives in `core` (not `macros`) to avoid introducing a cyclic project
 * dependency.
 */
private[golem] object AgentCompanionMacro {
  def getImpl[Trait: Type, In: Type](input: Expr[In])(using Quotes): Expr[Future[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val got      = TypeRepr.of[In]
    if !(got =:= expected) then
      report.errorAndAbort(
        s"get(input) requires: type AgentInput = ${expected.show} (found argument type: ${got.show})"
      )
    '{
      AgentClient.connect[Trait, In](
        AgentClient.plan[Trait].asInstanceOf[AgentClientPlan[Trait, In]],
        $input
      )
    }
  }

  def getPhantomImpl[Trait: Type, In: Type](input: Expr[In], phantom: Expr[Uuid])(using Quotes): Expr[Future[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val got      = TypeRepr.of[In]
    if !(got =:= expected) then
      report.errorAndAbort(
        s"getPhantom(input, phantom) requires: type AgentInput = ${expected.show} (found argument type: ${got.show})"
      )
    '{
      AgentClient.connectPhantom[Trait, In](
        AgentClient.plan[Trait].asInstanceOf[AgentClientPlan[Trait, In]],
        $input,
        $phantom
      )
    }
  }

  def getUnitImpl[Trait: Type](using Quotes): Expr[Future[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    if !(expected =:= TypeRepr.of[Unit]) then
      report.errorAndAbort(s"get() requires: type AgentInput = Unit (found: ${expected.show})")
    '{
      AgentClient.connect[Trait, Unit](
        AgentClient.plan[Trait].asInstanceOf[AgentClientPlan[Trait, Unit]],
        ()
      )
    }
  }

  def getPhantomUnitImpl[Trait: Type](phantom: Expr[Uuid])(using Quotes): Expr[Future[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    if !(expected =:= TypeRepr.of[Unit]) then
      report.errorAndAbort(s"getPhantom(phantom) requires: type AgentInput = Unit (found: ${expected.show})")
    '{
      AgentClient.connectPhantom[Trait, Unit](
        AgentClient.plan[Trait].asInstanceOf[AgentClientPlan[Trait, Unit]],
        (),
        $phantom
      )
    }
  }

  def getTuple2Impl[Trait: Type, A1: Type, A2: Type](a1: Expr[A1], a2: Expr[A2])(using Quotes): Expr[Future[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple2[A1, A2]]
    if !(expected =:= want) then
      report.errorAndAbort(s"get(a1, a2) requires: type AgentInput = ${want.show} (found: ${expected.show})")
    val tup = '{ Tuple2($a1, $a2) }
    '{
      AgentClient.connect[Trait, Tuple2[A1, A2]](
        AgentClient.plan[Trait].asInstanceOf[AgentClientPlan[Trait, Tuple2[A1, A2]]],
        $tup
      )
    }
  }

  def getPhantomTuple2Impl[Trait: Type, A1: Type, A2: Type](
    a1: Expr[A1],
    a2: Expr[A2],
    phantom: Expr[Uuid]
  )(using Quotes): Expr[Future[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple2[A1, A2]]
    if !(expected =:= want) then
      report.errorAndAbort(
        s"getPhantom(a1, a2, phantom) requires: type AgentInput = ${want.show} (found: ${expected.show})"
      )
    val tup = '{ Tuple2($a1, $a2) }
    '{
      AgentClient.connectPhantom[Trait, Tuple2[A1, A2]](
        AgentClient.plan[Trait].asInstanceOf[AgentClientPlan[Trait, Tuple2[A1, A2]]],
        $tup,
        $phantom
      )
    }
  }

  def getTuple3Impl[Trait: Type, A1: Type, A2: Type, A3: Type](a1: Expr[A1], a2: Expr[A2], a3: Expr[A3])(using
    Quotes
  ): Expr[Future[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple3[A1, A2, A3]]
    if !(expected =:= want) then
      report.errorAndAbort(s"get(a1, a2, a3) requires: type AgentInput = ${want.show} (found: ${expected.show})")
    val tup = '{ Tuple3($a1, $a2, $a3) }
    '{
      AgentClient.connect[Trait, Tuple3[A1, A2, A3]](
        AgentClient.plan[Trait].asInstanceOf[AgentClientPlan[Trait, Tuple3[A1, A2, A3]]],
        $tup
      )
    }
  }

  def getPhantomTuple3Impl[Trait: Type, A1: Type, A2: Type, A3: Type](
    a1: Expr[A1],
    a2: Expr[A2],
    a3: Expr[A3],
    phantom: Expr[Uuid]
  )(using Quotes): Expr[Future[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple3[A1, A2, A3]]
    if !(expected =:= want) then
      report.errorAndAbort(
        s"getPhantom(a1, a2, a3, phantom) requires: type AgentInput = ${want.show} (found: ${expected.show})"
      )
    val tup = '{ Tuple3($a1, $a2, $a3) }
    '{
      AgentClient.connectPhantom[Trait, Tuple3[A1, A2, A3]](
        AgentClient.plan[Trait].asInstanceOf[AgentClientPlan[Trait, Tuple3[A1, A2, A3]]],
        $tup,
        $phantom
      )
    }
  }

  private def agentInputTypeRepr[Trait: Type](using Quotes): quotes.reflect.TypeRepr = {
    import quotes.reflect.*
    val traitSym = TypeRepr.of[Trait].typeSymbol
    traitSym.typeMembers.find(_.name == "AgentInput") match {
      case None =>
        TypeRepr.of[Unit]
      case Some(tSym) =>
        traitSym.typeRef.memberType(tSym) match {
          case TypeBounds(_, hi) => hi.dealias
          case other             => other.dealias
        }
    }
  }
}
