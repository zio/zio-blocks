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
 * Adds `ConfigSource.fromHocon` to the [[zio.blocks.config.ConfigSource]]
 * companion. `import zio.blocks.config.hocon._` brings it into scope.
 */
trait ConfigSourceHoconSyntax {

  /** The `ConfigSource.fromHocon` constructor. */
  implicit final class ConfigSourceHoconOps(private val companion: ConfigSource.type) {

    /**
     * Parses the HOCON text `hocon` with [[HoconParser]] and returns its values
     * as a [[zio.blocks.config.ConfigSource]] keyed by dot-separated paths:
     * nested objects join their keys with dots, array elements get indexes
     * (`tags.0`), and `null` values are left out. `sourceId` names the source
     * in provenance and errors.
     *
     * Substitutions are resolved from the document only, never from environment
     * variables, and every `include` is skipped without an error, because this
     * method has no way to read other files; both are known bugs. A syntax
     * error is a [[zio.blocks.config.ConfigError.ParseError]] whose message has
     * no line or column (they are in its `cause`), also a known bug.
     *
     * {{{
     * import zio.blocks.config._
     *
     * val source = ConfigSource.fromHocon(
     *   """
     *   server { host = "0.0.0.0", port = 8080 }
     *   server.idle-timeout = 30s
     *   allowed-origins = ["https://example.com", "https://admin.example.com"]
     *   """,
     *   "application.conf"
     * )
     * val keys = source.map(_.all("").keySet.toList.sorted)
     * // Right(List(allowed-origins.0, allowed-origins.1, server.host, server.idle-timeout, server.port))
     * }}}
     *
     * @see
     *   [[https://zioblocks.com/reference/config/formats/ File Formats]]
     */
    def fromHocon(hocon: String, sourceId: String = "hocon:string"): Either[ConfigError, ConfigSource] =
      HoconConfigSource.fromStringWithId(hocon, sourceId)
  }
}
