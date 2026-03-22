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

import golem.runtime.autowire.AgentMode
import org.scalatest.funsuite.AnyFunSuite

final class AgentModeCompileSpec extends AnyFunSuite {

  test("AgentMode.Durable has value 'durable'") {
    assert(AgentMode.Durable.value == "durable")
  }

  test("AgentMode.Ephemeral has value 'ephemeral'") {
    assert(AgentMode.Ephemeral.value == "ephemeral")
  }

  test("AgentMode.fromString parses 'durable'") {
    assert(AgentMode.fromString("durable").contains(AgentMode.Durable))
  }

  test("AgentMode.fromString parses 'ephemeral'") {
    assert(AgentMode.fromString("ephemeral").contains(AgentMode.Ephemeral))
  }

  test("AgentMode.fromString is case-insensitive") {
    assert(AgentMode.fromString("DURABLE").contains(AgentMode.Durable))
    assert(AgentMode.fromString("Ephemeral").contains(AgentMode.Ephemeral))
    assert(AgentMode.fromString("EPHEMERAL").contains(AgentMode.Ephemeral))
  }

  test("AgentMode.fromString returns None for unknown values") {
    assert(AgentMode.fromString("unknown").isEmpty)
    assert(AgentMode.fromString("").isEmpty)
  }

  test("AgentMode.fromString returns None for null") {
    assert(AgentMode.fromString(null).isEmpty)
  }

  test("AgentMode sealed trait is exhaustive") {
    def describe(mode: AgentMode): String = mode match {
      case AgentMode.Durable   => "durable"
      case AgentMode.Ephemeral => "ephemeral"
    }
    assert(describe(AgentMode.Durable) == "durable")
    assert(describe(AgentMode.Ephemeral) == "ephemeral")
  }

}
