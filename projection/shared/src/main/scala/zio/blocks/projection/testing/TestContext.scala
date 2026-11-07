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

import zio.blocks.projection.ProjectionContext

import java.time.Instant

/**
 * Builds [[zio.blocks.projection.ProjectionContext]]s for calling a handler
 * directly in a test, with `Projection#handle` or [[TestProjectionEngine]]. The
 * functions that take no timestamp use the current time.
 */
object TestContext {

  /**
   * Builds a context from the given parts. The defaults are the entity
   * `test-1`, the sequence number `0`, the current time and no
   * `sourceEntityId`.
   */
  def make(
    entityId: String = "test-1",
    seq: Long = 0L,
    timestamp: Instant = Instant.now(),
    sourceEntityId: Option[String] = None
  ): ProjectionContext =
    ProjectionContext(entityId, timestamp, seq, sourceEntityId)

  /** Builds a context with a `sourceEntityId`, at sequence number `0`. */
  def makeWithSource(
    entityId: String,
    sourceEntityId: String
  ): ProjectionContext =
    ProjectionContext(entityId, Instant.now(), 0L, Some(sourceEntityId))

  /** Builds a context with the given sequence number. */
  def withSeq(entityId: String, seq: Long): ProjectionContext =
    ProjectionContext(entityId, Instant.now(), seq, None)

  /** Builds a context with the given timestamp, at sequence number `0`. */
  def withTimestamp(entityId: String, timestamp: Instant): ProjectionContext =
    ProjectionContext(entityId, timestamp, 0L, None)
}
