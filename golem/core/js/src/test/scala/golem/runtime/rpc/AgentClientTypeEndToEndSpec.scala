/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package golem.runtime.rpc

import golem.data.{DataType, DataValue}
import golem.BaseAgent
import golem.runtime.agenttype.AgentMethod
import golem.runtime.annotations.{DurabilityMode, agentDefinition}
import golem.runtime.autowire.{AgentImplementation, WitValueBuilder}
import org.scalatest.funsuite.AsyncFunSuite

import scala.annotation.unused
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

final class AgentClientTypeEndToEndSpec extends AsyncFunSuite {
  override implicit def executionContext: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  @agentDefinition("e2e-client-async", mode = DurabilityMode.Durable)
  trait AsyncEchoAgent extends BaseAgent[Unit] {
    def echo(in: String): Future[String]
  }

  test("client type loopback via AgentClientRuntime.resolve (Future-returning method)") {
    val impl = new AsyncEchoAgent {
      override def echo(in: String): Future[String] =
        Future.successful(s"hello $in")
    }

    AgentImplementation.register[AsyncEchoAgent]("e2e-client-async")(impl)

    val agentType = golem.runtime.macros.AgentClientMacro.agentType[AsyncEchoAgent]

    val rpc = new RpcInvoker {
      override def invokeAndAwait(functionName: String, params: js.Array[js.Dynamic]): Either[String, js.Dynamic] =
        if (functionName != "e2e-client-async.{echo}") Left(s"unexpected method: $functionName")
        else {
          val res = "hello world"
          WitValueBuilder.build(
            DataType.TupleType(List(DataType.StringType)),
            DataValue.TupleValue(List(DataValue.StringValue(res)))
          )
        }

      override def trigger(functionName: String, params: js.Array[js.Dynamic]): Either[String, Unit] =
        Left("not used")

      override def scheduleInvocation(
        @unused datetime: golem.Datetime,
        @unused functionName: String,
        @unused params: js.Array[js.Dynamic]
      ): Either[String, Unit] =
        Left("not used")
    }

    val resolvedAgent =
      AgentClientRuntime.ResolvedAgent(
        agentType.asInstanceOf[golem.runtime.agenttype.AgentType[AsyncEchoAgent, Any]],
        RemoteAgentClient("e2e-client-async", "fake-id", null, rpc)
      )

    val echo = agentType.methods.collectFirst { case m: AgentMethod[AsyncEchoAgent, String, String] @unchecked =>
      m
    }.get

    resolvedAgent.call(echo, "world").map(out => assert(out == "hello world"))
  }
}
