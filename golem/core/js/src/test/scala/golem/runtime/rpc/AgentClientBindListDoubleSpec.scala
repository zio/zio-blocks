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

import golem.runtime.agenttype.AgentType
import org.scalatest.funsuite.AsyncFunSuite

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

final class AgentClientBindListDoubleSpec extends AsyncFunSuite {
  override implicit def executionContext: ExecutionContext = queue

  test("AgentClient.bind supports Unit method with List[Double] param (no missing JS function)") {
    val agentType =
      AgentClient.agentType[BindListDoubleWorkflow].asInstanceOf[AgentType[BindListDoubleWorkflow, Unit]]

    final class RecordingInvoker extends RpcInvoker {
      var triggered: Boolean = false

      override def invokeAndAwait(functionName: String, params: js.Array[js.Dynamic]): Either[String, js.Dynamic] =
        Left("not used")

      override def trigger(functionName: String, params: js.Array[js.Dynamic]): Either[String, Unit] = {
        triggered = true
        Right(())
      }

      override def scheduleInvocation(
        datetime: golem.Datetime,
        functionName: String,
        params: js.Array[js.Dynamic]
      ): Either[String, Unit] =
        Left("not used")
    }

    val invoker = new RecordingInvoker
    val remote  = RemoteAgentClient(agentType.typeName, "agent-1", null, invoker)

    val resolved =
      AgentClientRuntime.ResolvedAgent(
        agentType.asInstanceOf[AgentType[BindListDoubleWorkflow, Any]],
        remote
      )

    val client = AgentClient.bind[BindListDoubleWorkflow](resolved)

    // If the binding uses an incorrect Scala.js method name mangling for List[Double],
    // this call can throw "TypeError: not a function" at runtime.
    client.finished(List(1.0, 2.0))

    Future.successful(assert(invoker.triggered))
  }
}
