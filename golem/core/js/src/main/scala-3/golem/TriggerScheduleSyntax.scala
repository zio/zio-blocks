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

object TriggerScheduleSyntax {
  type TriggerOps[Trait]  = Selectable
  type ScheduleOps[Trait] = Selectable

  extension [Trait <: BaseAgent[?]](agent: Trait) {
    transparent inline def trigger: TriggerOps[Trait] =
      ${ golem.AgentCompanionMacro.triggerOpsImpl[Trait]('agent) }

    transparent inline def schedule: ScheduleOps[Trait] =
      ${ golem.AgentCompanionMacro.scheduleOpsImpl[Trait]('agent) }
  }
}
