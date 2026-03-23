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

object BaseAgentPlatformSpec extends ZIOSpecDefault {
  def spec = suite("BaseAgentPlatformSpec")(
    test("BaseAgentPlatform accessors throw on JVM") {
      val ex1 = Try(BaseAgentPlatform.agentId).failed.get
      val ex2 = Try(BaseAgentPlatform.agentType).failed.get
      val ex3 = Try(BaseAgentPlatform.agentName).failed.get

      assertTrue(
        ex1.isInstanceOf[UnsupportedOperationException],
        ex1.getMessage.contains("BaseAgent is only available"),
        ex2.isInstanceOf[UnsupportedOperationException],
        ex2.getMessage.contains("BaseAgent is only available"),
        ex3.isInstanceOf[UnsupportedOperationException],
        ex3.getMessage.contains("BaseAgent is only available")
      )
    }
  )
}
