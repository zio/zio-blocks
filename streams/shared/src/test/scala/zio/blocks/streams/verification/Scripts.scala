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

package zio.blocks.streams.verification

sealed trait Fault extends Product with Serializable { def token: String }
object Fault {
  final case class Throw(token: String)                  extends Fault
  final case class Fail(token: String, trusted: Boolean) extends Fault
  final case class Pending(token: String)                extends Fault
  final case class Duplicate(token: String)              extends Fault
  final case class Late(token: String)                   extends Fault
  final case class Reentrant(token: String)              extends Fault
}
final case class FaultScript(events: Vector[Fault]) extends Serializable
sealed trait ScheduleStep                           extends Product with Serializable { def id: String }
object ScheduleStep {
  final case class Complete(id: String)   extends ScheduleStep
  final case class Duplicate(id: String)  extends ScheduleStep
  final case class Checkpoint(id: String) extends ScheduleStep
  case object Cancel                      extends ScheduleStep { val id = "cancel" }
}
final case class Schedule(steps: Vector[String]) extends Serializable {
  def parsed: Vector[ScheduleStep] = steps.map {
    case "cancel"                        => ScheduleStep.Cancel
    case s if s.startsWith("duplicate:") => ScheduleStep.Duplicate(s.drop(10))
    case s if s.startsWith("complete:")  => ScheduleStep.Complete(s.drop(9))
    case s                               => ScheduleStep.Checkpoint(s)
  }
}
final case class Validation(offset: Int, length: Int, capacity: Int)      extends Serializable
final case class Ownership(managed: Boolean, closeAt: Int, cancelAt: Int) extends Serializable
