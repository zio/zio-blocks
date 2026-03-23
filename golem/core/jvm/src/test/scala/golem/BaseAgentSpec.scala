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

package golem

import zio.test._

import scala.util.Try

object BaseAgentSpec extends ZIOSpecDefault {
  final class DummyAgent extends BaseAgent[Unit]

  def spec = suite("BaseAgentSpec")(
    test("BaseAgent delegates to platform accessors") {
      val agent = new DummyAgent

      assertTrue(
        Try(agent.agentId).failed.get.isInstanceOf[UnsupportedOperationException],
        Try(agent.agentType).failed.get.isInstanceOf[UnsupportedOperationException],
        Try(agent.agentName).failed.get.isInstanceOf[UnsupportedOperationException]
      )
    }
  )
}
