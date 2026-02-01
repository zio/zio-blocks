package golem

import golem.runtime.agenttype.AgentType
import golem.runtime.rpc.{AgentClient, AgentClientRuntime}
import golem.Uuid

import scala.quoted.*

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
          AgentClient.bind[Trait](resolved)
      }
    }
  }

  def getRemoteImpl[Trait: Type, In: Type](input: Expr[In])(using Quotes): Expr[RemoteAgent[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val got      = TypeRepr.of[In]
    if !(got =:= expected) then
      report.errorAndAbort(
        s"getRemote(input) requires: BaseAgent[${expected.show}] (found argument type: ${got.show})"
      )
    '{
      AgentClientRuntime.resolve[Trait, In](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, In]],
        $input
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          RemoteAgent(resolved)
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
          AgentClient.bind[Trait](resolved)
      }
    }
  }

  def getRemotePhantomImpl[Trait: Type, In: Type](
    input: Expr[In],
    phantom: Expr[Uuid]
  )(using Quotes): Expr[RemoteAgent[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val got      = TypeRepr.of[In]
    if !(got =:= expected) then
      report.errorAndAbort(
        s"getRemotePhantom(input, phantom) requires: BaseAgent[${expected.show}] (found argument type: ${got.show})"
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
          RemoteAgent(resolved)
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
          AgentClient.bind[Trait](resolved)
      }
    }
  }

  def getRemoteUnitImpl[Trait: Type](using Quotes): Expr[RemoteAgent[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    if !(expected =:= TypeRepr.of[Unit]) then
      report.errorAndAbort(s"getRemote() requires: BaseAgent[Unit] (found: ${expected.show})")
    '{
      AgentClientRuntime.resolve[Trait, Unit](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Unit]],
        ()
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          RemoteAgent(resolved)
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
          AgentClient.bind[Trait](resolved)
      }
    }
  }

  def getRemotePhantomUnitImpl[Trait: Type](phantom: Expr[Uuid])(using Quotes): Expr[RemoteAgent[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    if !(expected =:= TypeRepr.of[Unit]) then
      report.errorAndAbort(s"getRemotePhantom(phantom) requires: BaseAgent[Unit] (found: ${expected.show})")
    '{
      AgentClientRuntime.resolveWithPhantom[Trait, Unit](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Unit]],
        (),
        phantom = Some($phantom)
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          RemoteAgent(resolved)
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
      AgentClient.connect[Trait, Tuple2[A1, A2]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple2[A1, A2]]],
        $tup
      )
    }
  }

  def getRemoteTuple2Impl[Trait: Type, A1: Type, A2: Type](
    a1: Expr[A1],
    a2: Expr[A2]
  )(using Quotes): Expr[RemoteAgent[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple2[A1, A2]]
    if !(expected =:= want) then
      report.errorAndAbort(s"getRemote(a1, a2) requires: BaseAgent[${want.show}] (found: ${expected.show})")
    val tup = '{ Tuple2($a1, $a2) }
    '{
      AgentClientRuntime.resolve[Trait, Tuple2[A1, A2]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple2[A1, A2]]],
        $tup
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          RemoteAgent(resolved)
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
  )(using Quotes): Expr[RemoteAgent[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple2[A1, A2]]
    if !(expected =:= want) then
      report.errorAndAbort(
        s"getRemotePhantom(a1, a2, phantom) requires: BaseAgent[${want.show}] (found: ${expected.show})"
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
          RemoteAgent(resolved)
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
  )(using Quotes): Expr[RemoteAgent[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple3[A1, A2, A3]]
    if !(expected =:= want) then
      report.errorAndAbort(s"getRemote(a1, a2, a3) requires: BaseAgent[${want.show}] (found: ${expected.show})")
    val tup = '{ Tuple3($a1, $a2, $a3) }
    '{
      AgentClientRuntime.resolve[Trait, Tuple3[A1, A2, A3]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple3[A1, A2, A3]]],
        $tup
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          RemoteAgent(resolved)
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
  )(using Quotes): Expr[RemoteAgent[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple3[A1, A2, A3]]
    if !(expected =:= want) then
      report.errorAndAbort(
        s"getRemotePhantom(a1, a2, a3, phantom) requires: BaseAgent[${want.show}] (found: ${expected.show})"
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
          RemoteAgent(resolved)
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
      AgentClient.connect[Trait, Tuple4[A1, A2, A3, A4]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple4[A1, A2, A3, A4]]],
        $tup
      )
    }
  }

  def getRemoteTuple4Impl[Trait: Type, A1: Type, A2: Type, A3: Type, A4: Type](
    a1: Expr[A1],
    a2: Expr[A2],
    a3: Expr[A3],
    a4: Expr[A4]
  )(using Quotes): Expr[RemoteAgent[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple4[A1, A2, A3, A4]]
    if !(expected =:= want) then
      report.errorAndAbort(
        s"getRemote(a1, a2, a3, a4) requires: BaseAgent[${want.show}] (found: ${expected.show})"
      )
    val tup = '{ Tuple4($a1, $a2, $a3, $a4) }
    '{
      AgentClientRuntime.resolve[Trait, Tuple4[A1, A2, A3, A4]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple4[A1, A2, A3, A4]]],
        $tup
      ) match {
        case Left(err) =>
          throw scala.scalajs.js.JavaScriptException(err)
        case Right(resolved) =>
          RemoteAgent(resolved)
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
      AgentClient.connectPhantom[Trait, Tuple4[A1, A2, A3, A4]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple4[A1, A2, A3, A4]]],
        $tup,
        $phantom
      )
    }
  }

  def getRemotePhantomTuple4Impl[Trait: Type, A1: Type, A2: Type, A3: Type, A4: Type](
    a1: Expr[A1],
    a2: Expr[A2],
    a3: Expr[A3],
    a4: Expr[A4],
    phantom: Expr[Uuid]
  )(using Quotes): Expr[RemoteAgent[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple4[A1, A2, A3, A4]]
    if !(expected =:= want) then
      report.errorAndAbort(
        s"getRemotePhantom(a1, a2, a3, a4, phantom) requires: BaseAgent[${want.show}] (found: ${expected.show})"
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
          RemoteAgent(resolved)
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
      AgentClient.connect[Trait, Tuple5[A1, A2, A3, A4, A5]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple5[A1, A2, A3, A4, A5]]],
        $tup
      )
    }
  }

  def getRemoteTuple5Impl[Trait: Type, A1: Type, A2: Type, A3: Type, A4: Type, A5: Type](
    a1: Expr[A1],
    a2: Expr[A2],
    a3: Expr[A3],
    a4: Expr[A4],
    a5: Expr[A5]
  )(using Quotes): Expr[RemoteAgent[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple5[A1, A2, A3, A4, A5]]
    if !(expected =:= want) then
      report.errorAndAbort(
        s"getRemote(a1, a2, a3, a4, a5) requires: BaseAgent[${want.show}] (found: ${expected.show})"
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
          RemoteAgent(resolved)
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
      AgentClient.connectPhantom[Trait, Tuple5[A1, A2, A3, A4, A5]](
        AgentClient.agentType[Trait].asInstanceOf[AgentType[Trait, Tuple5[A1, A2, A3, A4, A5]]],
        $tup,
        $phantom
      )
    }
  }

  def getRemotePhantomTuple5Impl[Trait: Type, A1: Type, A2: Type, A3: Type, A4: Type, A5: Type](
    a1: Expr[A1],
    a2: Expr[A2],
    a3: Expr[A3],
    a4: Expr[A4],
    a5: Expr[A5],
    phantom: Expr[Uuid]
  )(using Quotes): Expr[RemoteAgent[Trait]] = {
    import quotes.reflect.*
    val expected = agentInputTypeRepr[Trait]
    val want     = TypeRepr.of[Tuple5[A1, A2, A3, A4, A5]]
    if !(expected =:= want) then
      report.errorAndAbort(
        s"getRemotePhantom(a1, a2, a3, a4, a5, phantom) requires: BaseAgent[${want.show}] (found: ${expected.show})"
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
          RemoteAgent(resolved)
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
}
