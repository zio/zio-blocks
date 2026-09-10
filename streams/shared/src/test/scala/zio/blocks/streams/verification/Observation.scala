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

final case class Observation(
  result: Either[Vector[String], Vector[Int]],
  provenance: Vector[String],
  throwableOrder: Vector[String],
  readerKind: String,
  demand: Int,
  callbacks: Int,
  ownership: Vector[String],
  outstanding: Int,
  suppressed: Vector[String],
  epoch: Long,
  trace: Vector[String]
) extends Serializable {
  def semantic: Observation = copy(trace = trace.filterNot(_.startsWith("runner:")))
}
