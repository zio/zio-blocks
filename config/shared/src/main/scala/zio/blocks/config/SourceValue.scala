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

/**
 * A value read from a source, with the [[Provenance]] that says where it came
 * from. A [[ConfigSource]] returns a `SourceValue[String]` for each key.
 *
 * @param value
 *   the value that the source holds
 * @param provenance
 *   where the value came from
 */
final case class SourceValue[A](value: A, provenance: Provenance)
