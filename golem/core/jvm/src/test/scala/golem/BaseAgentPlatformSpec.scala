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

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BaseAgentPlatformSpec extends AnyFunSuite with Matchers {
  test("BaseAgentPlatform accessors throw on JVM") {
    val ex1 = intercept[UnsupportedOperationException](BaseAgentPlatform.agentId)
    val ex2 = intercept[UnsupportedOperationException](BaseAgentPlatform.agentType)
    val ex3 = intercept[UnsupportedOperationException](BaseAgentPlatform.agentName)

    ex1.getMessage.should(include("BaseAgent is only available"))
    ex2.getMessage.should(include("BaseAgent is only available"))
    ex3.getMessage.should(include("BaseAgent is only available"))
  }
}
