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

package zio.blocks.config.hocon

import zio.blocks.config.ConfigSource

/**
 * Create a `ConfigSource` from HOCON text using the built-in `HoconParser`.
 */
object HoconConfigSource {

  /**
   * Parse a HOCON string into a `ConfigSource`.
   */
  def fromString(hocon: String): Either[HoconError, ConfigSource] =
    fromStringWithId(hocon, "hocon:string")

  /**
   * Parse a HOCON string into a `ConfigSource` with a custom source identifier.
   */
  def fromStringWithId(hocon: String, sourceId: String): Either[HoconError, ConfigSource] =
    HoconParser.parse(hocon).map { value =>
      val flat = HoconValue.flatten(value)
      ConfigSource.fromMap(flat, sourceId)
    }
}
