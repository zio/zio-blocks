package cloud.golem.runtime.rpc

import cloud.golem.data.GolemSchema
import cloud.golem.runtime.plan.{AgentClientPlan, ClientInvocation, ClientMethodPlan}
import cloud.golem.runtime.rpc.AgentClientRuntime.TestHooks
import cloud.golem.runtime.rpc.AgentClientRuntimeSpecFixtures._
import cloud.golem.runtime.rpc.host.AgentHostApi
import org.scalatest.funsuite.AsyncFunSuite
import zio.blocks.schema.Schema

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

final class AgentClientRuntimeSpec extends AsyncFunSuite {
  override implicit def executionContext: ExecutionContext = queue

  test("ResolvedAgent encodes awaitable inputs and decodes results") {
    val plan     = rpcPlan
    val invoker  = new RecordingRpcInvoker
    val resolved = resolvedAgent(invoker, plan)

    val callPlan = methodPlan[RpcParityAgent, SampleInput, SampleOutput](plan, "rpcCall")
    val expected = SampleOutput("ack")
    invoker.enqueueInvokeResult(encodeValue(expected)(using callPlan.outputSchema))

    resolved.call(callPlan, SampleInput("hello", 2)).map { result =>
      assert(result == expected)
      assert(invoker.invokeCalls.headOption.exists(_._1 == callPlan.functionName))
      assert(invoker.invokeCalls.headOption.exists(_._2.length == 1))
    }
  }

  test("ResolvedAgent routes fire-and-forget calls through trigger and schedule") {
    val plan     = rpcPlan
    val invoker  = new RecordingRpcInvoker
    val resolved = resolvedAgent(invoker, plan)
    val firePlan = methodPlan[RpcParityAgent, String, Unit](plan, "fireAndForget")

    val triggerF  = resolved.trigger(firePlan, "event")
    val scheduleF = resolved.schedule(firePlan, js.Dynamic.literal("ts" -> 42), "event")

    triggerF.flatMap(_ => scheduleF).map { _ =>
      assert(invoker.triggerCalls.headOption.exists(_._1 == firePlan.functionName))
      assert(invoker.scheduleCalls.nonEmpty)
    }
  }

  test("Awaitable calls reject when RPC invoker returns an error") {
    val invoker = new RecordingRpcInvoker
    invoker.enqueueInvokeResult(Left("rpc failed"))
    val resolved = resolvedAgent(invoker)
    val callPlan = methodPlan[RpcParityAgent, SampleInput, SampleOutput](rpcPlan, "rpcCall")

    recoverToExceptionIf[js.JavaScriptException] {
      resolved.call(callPlan, SampleInput("oops", 1))
    }.map { ex =>
      assert(ex.getMessage.contains("rpc failed"))
    }
  }

  test("Trigger rejects awaitable methods") {
    val resolved                                                     = resolvedAgent()
    val firePlan: ClientMethodPlan.Aux[RpcParityAgent, String, Unit] =
      methodPlan[RpcParityAgent, String, Unit](rpcPlan, "fireAndForget")
    val awaitablePlan: ClientMethodPlan.Aux[RpcParityAgent, String, Unit] =
      firePlan.copy(invocation = ClientInvocation.Awaitable)

    recoverToExceptionIf[js.JavaScriptException] {
      resolved.trigger(awaitablePlan, "noop")
    }.map { ex =>
      assert(ex.getMessage.contains("Method is awaitable"))
    }
  }

  test("Schedule rejects awaitable methods") {
    val resolved                                                     = resolvedAgent()
    val firePlan: ClientMethodPlan.Aux[RpcParityAgent, String, Unit] =
      methodPlan[RpcParityAgent, String, Unit](rpcPlan, "fireAndForget")
    val awaitablePlan: ClientMethodPlan.Aux[RpcParityAgent, String, Unit] =
      firePlan.copy(invocation = ClientInvocation.Awaitable)

    recoverToExceptionIf[js.JavaScriptException] {
      resolved.schedule(awaitablePlan, js.Dynamic.literal("ts" -> 1.0), "noop")
    }.map { ex =>
      assert(ex.getMessage.contains("scheduling is only supported"))
    }
  }

  test("AgentClient binder override proxies awaitable RPC methods") {
    val plan     = rpcPlan
    val invoker  = new RecordingRpcInvoker
    val resolved = resolvedAgent(invoker, plan)

    TestHooks.withClientBinder(manualBinder(plan)) {
      val client =
        TestHooks.bindOverride(resolved).getOrElse(fail("client binder override missing"))

      val output = SampleOutput("ack")
      invoker.enqueueInvokeResult(encodeValue(output))

      client.rpcCall(SampleInput("hello", 1)).map { result =>
        assert(result == output)
        assert(invoker.invokeCalls.headOption.exists(_._1 == "rpc-call"))
      }
    }
  }

  test("AgentClient binder override proxies fire-and-forget RPC methods") {
    val plan     = rpcPlan
    val invoker  = new RecordingRpcInvoker
    val resolved = resolvedAgent(invoker, plan)

    TestHooks.withClientBinder(manualBinder(plan)) {
      val client =
        TestHooks.bindOverride(resolved).getOrElse(fail("client binder override missing"))
      client.fireAndForget("event")
      assert(invoker.triggerCalls.nonEmpty)
      assert(invoker.triggerCalls.head._1 == "fire-and-forget")
    }
  }

  private def rpcPlan: AgentClientPlan[RpcParityAgent, RpcCtor] =
    AgentClient.plan[RpcParityAgent].asInstanceOf[AgentClientPlan[RpcParityAgent, RpcCtor]]

  private def resolvedAgent(
    invoker: RecordingRpcInvoker = new RecordingRpcInvoker,
    plan: AgentClientPlan[RpcParityAgent, RpcCtor] = rpcPlan
  ) =
    AgentClientRuntime.ResolvedAgent(plan, stubRemote(plan, invoker))

  private def stubRemote(
    plan: AgentClientPlan[RpcParityAgent, RpcCtor],
    invoker: RpcInvoker
  ): RemoteAgentClient = {
    val metadata = js.Dynamic
      .literal(
        "agent-type"     -> js.Dynamic.literal("type-name" -> plan.traitName),
        "implemented-by" -> js.Dynamic.literal(
          "uuid" -> js.Dynamic.literal("high-bits" -> 0.0, "low-bits" -> 0.0)
        )
      )
      .asInstanceOf[AgentHostApi.RegisteredAgentType]
    RemoteAgentClient(plan.traitName, "agent-1", metadata, invoker)
  }

  private def encodeValue[A](value: A)(implicit codec: GolemSchema[A]): Either[String, js.Dynamic] =
    RpcValueCodec.encodeValue(value)

  private def manualBinder(
    plan: AgentClientPlan[RpcParityAgent, RpcCtor]
  ): AgentClientRuntime.ResolvedAgent[RpcParityAgent] => RpcParityAgent =
    resolved =>
      new RpcParityAgent {
        override def `new`(config: RpcCtor): RpcParityAgent =
          this

        override def rpcCall(input: SampleInput): Future[SampleOutput] = {
          val method = methodPlan[RpcParityAgent, SampleInput, SampleOutput](plan, "rpcCall")
          resolved.call(method, input)
        }

        override def multiArgs(message: String, count: Int): Future[Int] = {
          val method = methodPlan[RpcParityAgent, Vector[Any], Int](plan, "multiArgs")
          resolved.call(method, Vector[Any](message, count))
        }

        override def fireAndForget(event: String): Unit = {
          val method = methodPlan[RpcParityAgent, String, Unit](plan, "fireAndForget")
          resolved.trigger(method, event).failed.foreach { err =>
            js.Dynamic.global.console.error("fire-and-forget trigger failed", err.asInstanceOf[js.Any])
          }
          ()
        }
      }

  private def methodPlan[Trait, In, Out](
    plan: AgentClientPlan[Trait, RpcCtor],
    name: String
  ): ClientMethodPlan.Aux[Trait, In, Out] =
    plan.methods.collectFirst {
      case candidate if candidate.metadata.name == name =>
        candidate.asInstanceOf[ClientMethodPlan.Aux[Trait, In, Out]]
    }.getOrElse(throw new IllegalArgumentException(s"Method plan for $name not found"))

  // --- Test fixtures -----------------------------------------------------------------------

  private final class RecordingRpcInvoker extends RpcInvoker {
    val invokeCalls   = mutable.ListBuffer.empty[(String, js.Array[js.Dynamic])]
    val triggerCalls  = mutable.ListBuffer.empty[(String, js.Array[js.Dynamic])]
    val scheduleCalls = mutable.ListBuffer.empty[(js.Dynamic, String, js.Array[js.Dynamic])]

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
      datetime: js.Dynamic,
      functionName: String,
      params: js.Array[js.Dynamic]
    ): Either[String, Unit] = {
      scheduleCalls += ((datetime, functionName, params))
      Right(())
    }
  }
}

private object AgentClientRuntimeSpecFixtures {
  trait RpcParityAgent {
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
