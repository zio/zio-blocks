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

package golem.quickstart.counter

import golem.runtime.annotations.agentImplementation

import scala.annotation.unused
import scala.concurrent.Future

@agentImplementation()
final class CounterAgentImpl(@unused private val name: String) extends CounterAgent {
  private var count: Int = 0

  override def increment(): Future[Int] =
    Future.successful {
      count += 1
      count
    }
}
