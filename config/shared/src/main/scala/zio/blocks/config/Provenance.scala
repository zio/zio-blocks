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

package zio.blocks.config

sealed trait Provenance {
  def sourceId: String
}

object Provenance {
  final case class Resolved(sourceId: String, key: String, rawValue: Option[String]) extends Provenance

  case object Default extends Provenance {
    val sourceId: String = "schema-default"
  }

  final case class Merged(primary: Provenance, fallback: Provenance) extends Provenance {
    def sourceId: String = s"${primary.sourceId}|${fallback.sourceId}"
  }
}
