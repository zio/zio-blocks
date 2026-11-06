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

package zio.blocks.projection.testing

import zio.*
import zio.blocks.projection.{ProjectionAction, ProjectionContext, Projection, ProjectionStore}

/**
 * Runs a projection's handlers on events by hand, with no event store and no
 * fibers, for unit tests of a [[zio.blocks.projection.Projection]]. Each method
 * finds the handler with `Projection#dispatch`, without naming a source.
 */
class TestProjectionEngine {

  /**
   * Returns the action the projection's handler gives for `event`, or `Noop` if
   * no handler matches. A handler that throws makes the task die, rather than
   * fail.
   */
  def processEvent[E, A](
    spec: Projection[A],
    event: E,
    ctx: ProjectionContext
  ): Task[ProjectionAction[A]] =
    ZIO.succeed(spec.dispatch(event, ctx).getOrElse(ProjectionAction.Noop))

  /**
   * Runs the handlers on each event in order and discards the actions, so it
   * only shows that no handler throws.
   */
  def processEvents[E, A](
    spec: Projection[A],
    events: List[(E, ProjectionContext)]
  ): Task[Unit] =
    ZIO.foreachDiscard(events) { case (event, ctx) =>
      processEvent(spec, event, ctx).unit
    }

  /**
   * Runs the handlers on each event in order and applies each action to
   * `store`, stopping at the first failure.
   */
  def processEvents[E, A](
    spec: Projection[A],
    events: List[(E, ProjectionContext)],
    store: ProjectionStore[A]
  ): Task[Unit] =
    ZIO.foreachDiscard(events) { case (event, ctx) =>
      for {
        action <- processEvent(spec, event, ctx)
        _      <- applyAction(store, action, ctx)
      } yield ()
    }

  /** Returns the action for each event, in order, without applying any. */
  def processEventsWithActions[E, A](
    spec: Projection[A],
    events: List[(E, ProjectionContext)]
  ): Task[List[ProjectionAction[A]]] =
    ZIO.foreach(events) { case (event, ctx) => processEvent(spec, event, ctx) }

  /**
   * Applies `action` to `store` the way
   * [[zio.blocks.projection.ProjectionEngine]] does: the actions that need a
   * row ID use `ctx.entityId`.
   */
  def applyAction[A](
    store: ProjectionStore[A],
    action: ProjectionAction[A],
    ctx: ProjectionContext
  ): Task[Unit] =
    action match {
      case ProjectionAction.Insert(v)    => store.insert(v)
      case ProjectionAction.Upsert(v)    => store.upsert(v)
      case ProjectionAction.Update(mods) => store.updateFields(ctx.entityId, mods)
      case ProjectionAction.Delete       => store.delete(ctx.entityId)
      case ProjectionAction.Truncate     => store.truncate
      case ProjectionAction.Noop         => ZIO.unit
    }

  /** Runs the handler on `event`, applies the action, and returns it. */
  def processAndApply[E, A](
    spec: Projection[A],
    event: E,
    ctx: ProjectionContext,
    store: ProjectionStore[A]
  ): Task[ProjectionAction[A]] =
    for {
      action <- processEvent(spec, event, ctx)
      _      <- applyAction(store, action, ctx)
    } yield action
}

/** Creates [[TestProjectionEngine]]s. */
object TestProjectionEngine {

  /** Creates a new [[TestProjectionEngine]]; it holds no state. */
  def make: TestProjectionEngine = new TestProjectionEngine()
}
