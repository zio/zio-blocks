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

package zio.blocks.projection

import zio.blocks.chunk.Chunk

/**
 * What a handler asks the engine to do to a projection's store for one event.
 * The engine turns each action into a call on the [[ProjectionStore]] chosen
 * for the event, and the actions that need a row ID use the event's
 * `ctx.entityId`.
 *
 * `ProjectionAction[+A]` is covariant in `A`, so the three actions without a
 * row are `ProjectionAction[Nothing]` and fit any projection.
 *
 * @tparam A
 *   the row type of the projection
 */
enum ProjectionAction[+A] {

  /**
   * Adds `value` as a new row, with `ProjectionStore#insert`. A row with the
   * same ID makes the action fail, except in a rebuild, which retries it as an
   * upsert.
   */
  case Insert[A](value: A) extends ProjectionAction[A]

  /** Adds `value`, or replaces the row with the same ID. */
  case Upsert[A](value: A) extends ProjectionAction[A]

  /**
   * Applies `modifications` to the row of `ctx.entityId`, creating the row
   * first if it is missing (see `ProjectionStore#updateFields`).
   */
  case Update[A](modifications: Chunk[FieldUpdate]) extends ProjectionAction[A]

  /** Deletes the row of `ctx.entityId`, if there is one. */
  case Delete extends ProjectionAction[Nothing]

  /**
   * Deletes every row of the store the event is routed to: the whole
   * projection, or one shard of a cross-entity projection.
   */
  case Truncate extends ProjectionAction[Nothing]

  /**
   * Changes nothing. The engine still counts the event as applied and moves the
   * watermark past it.
   */
  case Noop extends ProjectionAction[Nothing]
}

/** The actions without a row, as values typed `ProjectionAction[Nothing]`. */
object ProjectionAction {

  /** The `Delete` sentinel action. */
  val delete: ProjectionAction[Nothing] = ProjectionAction.Delete

  /** The `Truncate` sentinel action. */
  val truncate: ProjectionAction[Nothing] = ProjectionAction.Truncate

  /** The `Noop` sentinel action. */
  val noop: ProjectionAction[Nothing] = ProjectionAction.Noop
}
