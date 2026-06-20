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

import zio.blocks.config.{ConfigError, ConfigSource}

/**
 * Create a `ConfigSource` from HOCON text using the built-in `HoconParser`.
 */
private[hocon] object HoconConfigSource {

  def fromString(hocon: String): Either[ConfigError, ConfigSource] =
    fromStringWithId(hocon, "hocon:string")

  def fromStringWithId(hocon: String, sourceId: String): Either[ConfigError, ConfigSource] =
    HoconParser.parse(hocon).left.map(toConfigError(_, sourceId)).map { value =>
      val flat = HoconValue.flatten(value)
      ConfigSource.fromMap(flat, sourceId)
    }

  def fromStringWithCallback(
    hocon: String,
    sourceId: String,
    includeCallback: String => Option[String]
  ): Either[ConfigError, ConfigSource] =
    HoconParser.parse(hocon, includeCallback).left.map(toConfigError(_, sourceId)).map { value =>
      val flat = HoconValue.flatten(value)
      ConfigSource.fromMap(flat, sourceId)
    }

  private[hocon] def fromStringWithResolver(
    hocon: String,
    sourceId: String,
    includeResolver: String => Option[HoconParser.IncludedResource]
  ): Either[ConfigError, ConfigSource] =
    HoconParser.parseWithResolver(hocon, includeResolver).left.map(toConfigError(_, sourceId)).map { value =>
      val flat = HoconValue.flatten(value)
      ConfigSource.fromMap(flat, sourceId)
    }

  private def toConfigError(e: HoconError, sourceId: String): ConfigError =
    ConfigError.ParseError("", sourceId, "valid HOCON", Some(e))
}
