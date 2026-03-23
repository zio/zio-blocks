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

package example.minimal

import golem.runtime.annotations.agentImplementation

import scala.concurrent.Future

@agentImplementation()
final class StatefulCounterImpl(private val state: CounterState) extends StatefulCounter {
  private var count: Int = state.initialCount

  override def increment(): Future[Int] =
    Future.successful {
      count += 1
      count
    }

  override def current(): Future[Int] =
    Future.successful(count)
}

@agentImplementation()
final class StatefulCallerImpl(private val state: CounterState) extends StatefulCaller {
  private val counter = StatefulCounter.get(state)

  override def remoteIncrement(): Future[Int] =
    counter.increment()
}
