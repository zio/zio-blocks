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
 * A sealed enumeration of actions that can be applied to a projection.
 *
 * `ProjectionAction[+A]` is covariant in `A`, allowing actions on subtypes to
 * be used where supertype actions are expected.
 *
 * Each variant represents a distinct operation on projected data:
 *   - `Insert`: Add a new entity
 *   - `Upsert`: Add or update an entity
 *   - `Update`: Modify specific fields of an existing entity
 *   - `Delete`: Remove an entity (sentinel — no payload)
 *   - `Truncate`: Remove all entities (sentinel — no payload)
 *   - `Noop`: No operation (sentinel — no payload)
 *
 * @tparam A
 *   The entity type this action operates on
 */
enum ProjectionAction[+A] {

  case Insert[A](value: A)                          extends ProjectionAction[A]
  case Upsert[A](value: A)                          extends ProjectionAction[A]
  case Update[A](modifications: Chunk[FieldUpdate]) extends ProjectionAction[A]
  case Delete                                       extends ProjectionAction[Nothing]
  case Truncate                                     extends ProjectionAction[Nothing]
  case Noop                                         extends ProjectionAction[Nothing]
}

object ProjectionAction {

  /** The `Delete` sentinel action. */
  val delete: ProjectionAction[Nothing] = ProjectionAction.Delete

  /** The `Truncate` sentinel action. */
  val truncate: ProjectionAction[Nothing] = ProjectionAction.Truncate

  /** The `Noop` sentinel action. */
  val noop: ProjectionAction[Nothing] = ProjectionAction.Noop
}
