package golem.runtime.rpc

import golem.data.GolemSchema
import golem.runtime.annotations.{DurabilityMode, agentDefinition}
import golem.BaseAgent
import golem.runtime.agenttype.{AgentMethod, AgentType, MethodInvocation}
import golem.runtime.rpc.AgentClientRuntime.TestHooks
import golem.runtime.rpc.AgentClientRuntimeSpecFixtures._
import golem.runtime.rpc.host.AgentHostApi
import org.scalatest.funsuite.AsyncFunSuite
import zio.blocks.schema.Schema

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

final class AgentClientRuntimeSpec extends AsyncFunSuite {
  override implicit def executionContext: ExecutionContext = queue

  test("ResolvedAgent encodes awaitable inputs and decodes results") {
    val agentType = rpcAgentType
    val invoker   = new RecordingRpcInvoker
    val resolved  = resolvedAgent(invoker, agentType)

    val method   = findMethod[RpcParityAgent, SampleInput, SampleOutput](agentType, "rpcCall")
    val expected = SampleOutput("ack")
    invoker.enqueueInvokeResult(encodeValue(expected)(using method.outputSchema))

    resolved.call(method, SampleInput("hello", 2)).map { result =>
      assert(result == expected)
      assert(invoker.invokeCalls.headOption.exists(_._1 == method.functionName))
      assert(invoker.invokeCalls.headOption.exists(_._2.length == 1))
    }
  }

  test("ResolvedAgent routes fire-and-forget calls through trigger and schedule") {
    val agentType = rpcAgentType
    val invoker   = new RecordingRpcInvoker
    val resolved  = resolvedAgent(invoker, agentType)
    val method    = findMethod[RpcParityAgent, String, Unit](agentType, "fireAndForget")

    val triggerF  = resolved.trigger(method, "event")
    val scheduleF = resolved.schedule(method, golem.Datetime.fromEpochMillis(42), "event")

    triggerF.flatMap(_ => scheduleF).map { _ =>
      assert(invoker.triggerCalls.headOption.exists(_._1 == method.functionName))
      assert(invoker.scheduleCalls.nonEmpty)
    }
  }

  test("Awaitable calls reject when RPC invoker returns an error") {
    val invoker = new RecordingRpcInvoker
    invoker.enqueueInvokeResult(Left("rpc failed"))
    val resolved = resolvedAgent(invoker)
    val method   = findMethod[RpcParityAgent, SampleInput, SampleOutput](rpcAgentType, "rpcCall")

    recoverToExceptionIf[js.JavaScriptException] {
      resolved.call(method, SampleInput("oops", 1))
    }.map { ex =>
      assert(ex.getMessage.contains("rpc failed"))
    }
  }

  test("Trigger works for awaitable methods (invocation kind does not restrict trigger)") {
    val agentType                                             = rpcAgentType
    val invoker                                               = new RecordingRpcInvoker
    val resolved                                              = resolvedAgent(invoker, agentType)
    val methodBase: AgentMethod[RpcParityAgent, String, Unit] =
      findMethod[RpcParityAgent, String, Unit](agentType, "fireAndForget")
    val method: AgentMethod[RpcParityAgent, String, Unit] =
      methodBase.copy(invocation = MethodInvocation.Awaitable)

    resolved.trigger(method, "noop").map { _ =>
      assert(invoker.triggerCalls.nonEmpty)
      assert(invoker.triggerCalls.head._1 == method.functionName)
    }
  }

  test("Schedule works for awaitable methods (invocation kind does not restrict schedule)") {
    val agentType                                             = rpcAgentType
    val invoker                                               = new RecordingRpcInvoker
    val resolved                                              = resolvedAgent(invoker, agentType)
    val methodBase: AgentMethod[RpcParityAgent, String, Unit] =
      findMethod[RpcParityAgent, String, Unit](agentType, "fireAndForget")
    val method: AgentMethod[RpcParityAgent, String, Unit] =
      methodBase.copy(invocation = MethodInvocation.Awaitable)

    resolved.schedule(method, golem.Datetime.fromEpochMillis(1.0), "noop").map { _ =>
      assert(invoker.scheduleCalls.nonEmpty)
      assert(invoker.scheduleCalls.head._2 == method.functionName)
    }
  }

  test("AgentClient binder override proxies awaitable RPC methods") {
    val agentType = rpcAgentType
    val invoker   = new RecordingRpcInvoker
    val resolved  = resolvedAgent(invoker, agentType)

    TestHooks.withClientBinder(manualBinder(agentType)) {
      val client =
        TestHooks.bindOverride(resolved).getOrElse(fail("client binder override missing"))

      val output = SampleOutput("ack")
      invoker.enqueueInvokeResult(encodeValue(output))

      val method = findMethod[RpcParityAgent, SampleInput, SampleOutput](agentType, "rpcCall")
      client.rpcCall(SampleInput("hello", 1)).map { result =>
        assert(result == output)
        assert(invoker.invokeCalls.headOption.exists(_._1 == method.functionName))
      }
    }
  }

  test("AgentClient binder override proxies fire-and-forget RPC methods") {
    val agentType = rpcAgentType
    val invoker   = new RecordingRpcInvoker
    val resolved  = resolvedAgent(invoker, agentType)

    TestHooks.withClientBinder(manualBinder(agentType)) {
      val client =
        TestHooks.bindOverride(resolved).getOrElse(fail("client binder override missing"))
      val method = findMethod[RpcParityAgent, String, Unit](agentType, "fireAndForget")
      client.fireAndForget("event")
      assert(invoker.triggerCalls.nonEmpty)
      assert(invoker.triggerCalls.head._1 == method.functionName)
    }
  }

  private def rpcAgentType: AgentType[RpcParityAgent, RpcCtor] =
    AgentClient.agentType[RpcParityAgent].asInstanceOf[AgentType[RpcParityAgent, RpcCtor]]

  private def resolvedAgent(
    invoker: RecordingRpcInvoker,
    agentType: AgentType[RpcParityAgent, RpcCtor] = rpcAgentType
  ) =
    AgentClientRuntime.ResolvedAgent(
      agentType.asInstanceOf[AgentType[RpcParityAgent, Any]],
      stubRemote(agentType, invoker)
    )

  private def stubRemote(
    agentType: AgentType[RpcParityAgent, RpcCtor],
    invoker: RpcInvoker
  ): RemoteAgentClient = {
    val metadata = js.Dynamic
      .literal(
        "agent-type"     -> js.Dynamic.literal("type-name" -> agentType.typeName),
        "implemented-by" -> js.Dynamic.literal(
          "uuid" -> js.Dynamic.literal("high-bits" -> 0.0, "low-bits" -> 0.0)
        )
      )
      .asInstanceOf[AgentHostApi.RegisteredAgentType]
    RemoteAgentClient(agentType.typeName, "agent-1", metadata, invoker)
  }

  private def encodeValue[A](value: A)(implicit codec: GolemSchema[A]): Either[String, js.Dynamic] =
    RpcValueCodec.encodeValue(value)

  private def manualBinder(
    rpcAgentType: AgentType[RpcParityAgent, RpcCtor]
  ): AgentClientRuntime.ResolvedAgent[RpcParityAgent] => RpcParityAgent =
    resolved =>
      new RpcParityAgent {
        override def `new`(config: RpcCtor): RpcParityAgent =
          this

        override def rpcCall(input: SampleInput): Future[SampleOutput] = {
          val method = findMethod[RpcParityAgent, SampleInput, SampleOutput](rpcAgentType, "rpcCall")
          resolved.call(method, input)
        }

        override def multiArgs(message: String, count: Int): Future[Int] = {
          val method = findMethod[RpcParityAgent, Vector[Any], Int](rpcAgentType, "multiArgs")
          resolved.call(method, Vector[Any](message, count))
        }

        override def fireAndForget(event: String): Unit = {
          val method = findMethod[RpcParityAgent, String, Unit](rpcAgentType, "fireAndForget")
          resolved.trigger(method, event).failed.foreach { err =>
            js.Dynamic.global.console.error("fire-and-forget trigger failed", err.asInstanceOf[js.Any])
          }
          ()
        }
      }

  private def findMethod[Trait, In, Out](
    agentType: AgentType[Trait, RpcCtor],
    name: String
  ): AgentMethod[Trait, In, Out] =
    agentType.methods.collectFirst {
      case candidate if candidate.metadata.name == name =>
        candidate.asInstanceOf[AgentMethod[Trait, In, Out]]
    }.getOrElse(throw new IllegalArgumentException(s"Method definition for $name not found"))

  private final class RecordingRpcInvoker extends RpcInvoker {
    val invokeCalls   = mutable.ListBuffer.empty[(String, js.Array[js.Dynamic])]
    val triggerCalls  = mutable.ListBuffer.empty[(String, js.Array[js.Dynamic])]
    val scheduleCalls = mutable.ListBuffer.empty[(golem.Datetime, String, js.Array[js.Dynamic])]

    private val invokeResults = mutable.Queue.empty[Either[String, js.Dynamic]]

    def enqueueInvokeResult(result: Either[String, js.Dynamic]): Unit =
      invokeResults.enqueue(result)

    override def invokeAndAwait(functionName: String, params: js.Array[js.Dynamic]): Either[String, js.Dynamic] = {
      invokeCalls += ((functionName, params))
      if (invokeResults.nonEmpty) invokeResults.dequeue()
      else Right(js.Dynamic.literal())
    }

    override def trigger(functionName: String, params: js.Array[js.Dynamic]): Either[String, Unit] = {
      triggerCalls += ((functionName, params))
      Right(())
    }

    override def scheduleInvocation(
      datetime: golem.Datetime,
      functionName: String,
      params: js.Array[js.Dynamic]
    ): Either[String, Unit] = {
      scheduleCalls += ((datetime, functionName, params))
      Right(())
    }
  }
}

private object AgentClientRuntimeSpecFixtures {
  @agentDefinition("rpc-parity-agent", mode = DurabilityMode.Durable)
  trait RpcParityAgent extends BaseAgent[RpcCtor] {
    def `new`(config: RpcCtor): RpcParityAgent

    def rpcCall(input: SampleInput): Future[SampleOutput]

    def multiArgs(message: String, count: Int): Future[Int]

    def fireAndForget(event: String): Unit
  }

  final case class RpcCtor(token: String)

  final case class SampleInput(message: String, count: Int)

  final case class SampleOutput(result: String)

  object RpcCtor {
    implicit val schemaRpcCtor: Schema[RpcCtor] = Schema.derived
  }

  object SampleInput {
    implicit val schemaSampleInput: Schema[SampleInput] = Schema.derived
  }

  object SampleOutput {
    implicit val schemaSampleOutput: Schema[SampleOutput] = Schema.derived
  }
}
