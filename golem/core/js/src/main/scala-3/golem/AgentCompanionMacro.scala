package golem

import golem.runtime.agenttype.AgentType
import golem.runtime.rpc.{AgentClient, AgentClientRuntime}
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
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, In]],
        $input
      )
    }
  }

  def getRemoteImpl[Trait: Type, In: Type](input: Expr[In])(using Quotes): Expr[Future[RemoteAgent[Trait]]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val got      = TypeRepr.of[In]
    if !(got =:= expected) then
      report.errorAndAbort(
        s"getRemote(input) requires: type AgentInput = ${expected.show} (found argument type: ${got.show})"
      )
    '{
      AgentClientRuntime.resolve[Trait, In](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, In]],
        $input
      ) match {
        case Left(err) =>
          Future.failed(scala.scalajs.js.JavaScriptException(err))
        case Right(resolved) =>
          Future.successful(RemoteAgent(AgentClient.bind[Trait](resolved), resolved))
      }
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
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, In]],
        $input,
        $phantom
      )
    }
  }

  def getRemotePhantomImpl[Trait: Type, In: Type](
    input: Expr[In],
    phantom: Expr[Uuid]
  )(using Quotes): Expr[Future[RemoteAgent[Trait]]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val got      = TypeRepr.of[In]
    if !(got =:= expected) then
      report.errorAndAbort(
        s"getRemotePhantom(input, phantom) requires: type AgentInput = ${expected.show} (found argument type: ${got.show})"
      )
    '{
      AgentClientRuntime.resolveWithPhantom[Trait, In](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, In]],
        $input,
        phantom = Some($phantom)
      ) match {
        case Left(err) =>
          Future.failed(scala.scalajs.js.JavaScriptException(err))
        case Right(resolved) =>
          Future.successful(RemoteAgent(AgentClient.bind[Trait](resolved), resolved))
      }
    }
  }

  def getUnitImpl[Trait: Type](using Quotes): Expr[Future[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    if !(expected =:= TypeRepr.of[Unit]) then
      report.errorAndAbort(s"get() requires: type AgentInput = Unit (found: ${expected.show})")
    '{
      AgentClient.connect[Trait, Unit](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Unit]],
        ()
      )
    }
  }

  def getRemoteUnitImpl[Trait: Type](using Quotes): Expr[Future[RemoteAgent[Trait]]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    if !(expected =:= TypeRepr.of[Unit]) then
      report.errorAndAbort(s"getRemote() requires: type AgentInput = Unit (found: ${expected.show})")
    '{
      AgentClientRuntime.resolve[Trait, Unit](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Unit]],
        ()
      ) match {
        case Left(err) =>
          Future.failed(scala.scalajs.js.JavaScriptException(err))
        case Right(resolved) =>
          Future.successful(RemoteAgent(AgentClient.bind[Trait](resolved), resolved))
      }
    }
  }

  def getPhantomUnitImpl[Trait: Type](phantom: Expr[Uuid])(using Quotes): Expr[Future[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    if !(expected =:= TypeRepr.of[Unit]) then
      report.errorAndAbort(s"getPhantom(phantom) requires: type AgentInput = Unit (found: ${expected.show})")
    '{
      AgentClient.connectPhantom[Trait, Unit](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Unit]],
        (),
        $phantom
      )
    }
  }

  def getRemotePhantomUnitImpl[Trait: Type](phantom: Expr[Uuid])(using Quotes): Expr[Future[RemoteAgent[Trait]]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    if !(expected =:= TypeRepr.of[Unit]) then
      report.errorAndAbort(s"getRemotePhantom(phantom) requires: type AgentInput = Unit (found: ${expected.show})")
    '{
      AgentClientRuntime.resolveWithPhantom[Trait, Unit](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Unit]],
        (),
        phantom = Some($phantom)
      ) match {
        case Left(err) =>
          Future.failed(scala.scalajs.js.JavaScriptException(err))
        case Right(resolved) =>
          Future.successful(RemoteAgent(AgentClient.bind[Trait](resolved), resolved))
      }
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
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple2[A1, A2]]],
        $tup
      )
    }
  }

  def getRemoteTuple2Impl[Trait: Type, A1: Type, A2: Type](
    a1: Expr[A1],
    a2: Expr[A2]
  )(using Quotes): Expr[Future[RemoteAgent[Trait]]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple2[A1, A2]]
    if !(expected =:= want) then
      report.errorAndAbort(s"getRemote(a1, a2) requires: type AgentInput = ${want.show} (found: ${expected.show})")
    val tup = '{ Tuple2($a1, $a2) }
    '{
      AgentClientRuntime.resolve[Trait, Tuple2[A1, A2]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple2[A1, A2]]],
        $tup
      ) match {
        case Left(err) =>
          Future.failed(scala.scalajs.js.JavaScriptException(err))
        case Right(resolved) =>
          Future.successful(RemoteAgent(AgentClient.bind[Trait](resolved), resolved))
      }
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
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple2[A1, A2]]],
        $tup,
        $phantom
      )
    }
  }

  def getRemotePhantomTuple2Impl[Trait: Type, A1: Type, A2: Type](
    a1: Expr[A1],
    a2: Expr[A2],
    phantom: Expr[Uuid]
  )(using Quotes): Expr[Future[RemoteAgent[Trait]]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple2[A1, A2]]
    if !(expected =:= want) then
      report.errorAndAbort(
        s"getRemotePhantom(a1, a2, phantom) requires: type AgentInput = ${want.show} (found: ${expected.show})"
      )
    val tup = '{ Tuple2($a1, $a2) }
    '{
      AgentClientRuntime.resolveWithPhantom[Trait, Tuple2[A1, A2]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple2[A1, A2]]],
        $tup,
        phantom = Some($phantom)
      ) match {
        case Left(err) =>
          Future.failed(scala.scalajs.js.JavaScriptException(err))
        case Right(resolved) =>
          Future.successful(RemoteAgent(AgentClient.bind[Trait](resolved), resolved))
      }
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
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple3[A1, A2, A3]]],
        $tup
      )
    }
  }

  def getRemoteTuple3Impl[Trait: Type, A1: Type, A2: Type, A3: Type](
    a1: Expr[A1],
    a2: Expr[A2],
    a3: Expr[A3]
  )(using Quotes): Expr[Future[RemoteAgent[Trait]]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple3[A1, A2, A3]]
    if !(expected =:= want) then
      report.errorAndAbort(s"getRemote(a1, a2, a3) requires: type AgentInput = ${want.show} (found: ${expected.show})")
    val tup = '{ Tuple3($a1, $a2, $a3) }
    '{
      AgentClientRuntime.resolve[Trait, Tuple3[A1, A2, A3]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple3[A1, A2, A3]]],
        $tup
      ) match {
        case Left(err) =>
          Future.failed(scala.scalajs.js.JavaScriptException(err))
        case Right(resolved) =>
          Future.successful(RemoteAgent(AgentClient.bind[Trait](resolved), resolved))
      }
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
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple3[A1, A2, A3]]],
        $tup,
        $phantom
      )
    }
  }

  def getRemotePhantomTuple3Impl[Trait: Type, A1: Type, A2: Type, A3: Type](
    a1: Expr[A1],
    a2: Expr[A2],
    a3: Expr[A3],
    phantom: Expr[Uuid]
  )(using Quotes): Expr[Future[RemoteAgent[Trait]]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple3[A1, A2, A3]]
    if !(expected =:= want) then
      report.errorAndAbort(
        s"getRemotePhantom(a1, a2, a3, phantom) requires: type AgentInput = ${want.show} (found: ${expected.show})"
      )
    val tup = '{ Tuple3($a1, $a2, $a3) }
    '{
      AgentClientRuntime.resolveWithPhantom[Trait, Tuple3[A1, A2, A3]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple3[A1, A2, A3]]],
        $tup,
        phantom = Some($phantom)
      ) match {
        case Left(err) =>
          Future.failed(scala.scalajs.js.JavaScriptException(err))
        case Right(resolved) =>
          Future.successful(RemoteAgent(AgentClient.bind[Trait](resolved), resolved))
      }
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
