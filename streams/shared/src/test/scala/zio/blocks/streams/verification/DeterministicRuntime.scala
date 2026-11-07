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

final class DeterministicRuntime {
  private var epoch                               = 0L
  private var pending                             = Map.empty[String, (Long, () => Unit)]
  private var events                              = Vector.empty[String]
  def submit(id: String)(complete: => Unit): Unit = {
    pending += id -> ((epoch, () => complete)); events :+= "submit:" + id
  }
  def complete(id: String): Unit = pending.get(id).foreach { case (submitted, action) =>
    pending -= id
    if (submitted == epoch) { action(); events :+= "complete:" + id }
    else events :+= "stale:" + id
  }
  def duplicate(id: String): Unit     = { complete(id); complete(id) }
  def cancel(): Unit                  = { epoch += 1; events :+= "cancel" }
  def checkpoint(label: String): Unit = events :+= "checkpoint:" + label
  def outstanding: Int                = pending.size
  def history: Vector[String]         = events
}

/**
 * A deliberately small, exhaustive lifecycle specification. Histories carry
 * invocation/response intervals rather than merely a completion order.
 */
object Linearizability {
  sealed trait Operation extends Product with Serializable { def id: String }
  object Operation {
    final case class Read(id: String)     extends Operation
    final case class BulkRead(id: String) extends Operation
    final case class Control(id: String)  extends Operation
    final case class Complete(id: String) extends Operation
    final case class Replace(id: String)  extends Operation
    final case class Close(id: String)    extends Operation
    final case class Cancel(id: String)   extends Operation
  }
  final case class Call(operation: Operation, invoked: Int, responded: Int) extends Serializable {
    require(invoked >= 0 && responded >= invoked)
  }
  final case class State(activePull: Boolean, closed: Boolean, cancelled: Boolean, epoch: Int, completions: Int)
  val initial: State = State(activePull = false, closed = false, cancelled = false, epoch = 0, completions = 0)

  def accepts(history: Vector[Call]): Boolean = permutations(history).exists(linearizable)

  private def permutations[A](values: Vector[A]): Vector[Vector[A]] =
    if (values.isEmpty) Vector(Vector.empty)
    else values.indices.toVector.flatMap(i => permutations(values.patch(i, Nil, 1)).map(values(i) +: _))

  private def linearizable(order: Vector[Call]): Boolean = {
    val position = order.zipWithIndex.toMap
    val realTime = order.forall(a => order.forall(b => a.responded >= b.invoked || position(a) < position(b)))
    realTime && order.foldLeft(Option(initial))((state, call) => state.flatMap(step(_, call.operation))).nonEmpty
  }

  private def step(state: State, operation: Operation): Option[State] = operation match {
    case _: Operation.Read | _: Operation.BulkRead | _: Operation.Control =>
      if (state.closed || state.cancelled || state.activePull) None else Some(state.copy(activePull = true))
    case _: Operation.Complete =>
      if (!state.activePull || state.closed || state.cancelled) None
      else Some(state.copy(activePull = false, completions = state.completions + 1))
    case _: Operation.Replace =>
      if (state.closed || state.cancelled || state.activePull) None else Some(state)
    case _: Operation.Close =>
      Some(state.copy(activePull = false, closed = true, epoch = state.epoch + (if (state.closed) 0 else 1)))
    case _: Operation.Cancel =>
      if (state.cancelled) Some(state)
      else Some(state.copy(activePull = false, cancelled = true, epoch = state.epoch + 1))
  }
}
