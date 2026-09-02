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

package zio.blocks.jwt

import zio.blocks.chunk.Chunk

sealed trait JwtAudience extends Product with Serializable

object JwtAudience {

  final case class Single(value: String) extends JwtAudience

  final case class Multiple(values: Chunk[String]) extends JwtAudience

  def single(value: String): JwtAudience = Single(value)

  def multiple(values: Chunk[String]): JwtAudience = Multiple(values)

  def multiple(values: String*): JwtAudience = Multiple(Chunk.from(values))
}
