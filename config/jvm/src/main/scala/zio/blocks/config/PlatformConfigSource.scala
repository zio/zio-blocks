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

import scala.jdk.CollectionConverters._
import zio.blocks.maybe.Maybe

/**
 * A [[ConfigSource]] over the process environment. A key is read from the
 * environment variable with the same name, its dots replaced by underscores and
 * in upper case: `db.host` reads `DB_HOST`. A variable set to the empty string
 * is present. `all` turns variable names back into keys in lower case with
 * every underscore replaced by a dot, so `MAX_POOL_SIZE` is listed as
 * `max.pool.size`.
 *
 * On the JVM it reads `System.getenv`. On Scala.js it reads `process.env` under
 * Node.js, and finds nothing where `process` is not defined, such as in a
 * browser.
 */
object EnvSource extends ConfigSource {
  val sourceId: String = "env"

  def get(key: String): Maybe[SourceValue[String]] = {
    val envKey = toEnvKey(key)
    val raw    = System.getenv(envKey)
    if (raw == null) Maybe.absent
    else Maybe.present(SourceValue(raw, Provenance.Resolved(sourceId, envKey, Maybe.present(raw))))
  }

  def all(prefix: String): Map[String, SourceValue[String]] = {
    val envPrefix = toEnvKey(prefix)
    val dotPrefix = if (envPrefix.isEmpty) "" else s"${envPrefix}_"
    System
      .getenv()
      .asScala
      .collect {
        case (k, v) if envPrefix.isEmpty || k == envPrefix || k.startsWith(dotPrefix) =>
          fromEnvKey(k) -> SourceValue(v, Provenance.Resolved(sourceId, k, Maybe.present(v)))
      }
      .toMap
  }

  private def toEnvKey(key: String): String =
    key.replace('.', '_').toUpperCase

  private def fromEnvKey(key: String): String =
    key.toLowerCase.replace('_', '.')
}

/**
 * A [[ConfigSource]] over JVM system properties: a key is read from the
 * property with the same name, so `http.port` reads `-Dhttp.port=9000`. On
 * Scala.js it is always empty, even for properties set with
 * `System.setProperty`.
 */
object SysPropSource extends ConfigSource {
  val sourceId: String = "sysprop"

  def get(key: String): Maybe[SourceValue[String]] = {
    val raw = System.getProperty(key)
    if (raw == null) Maybe.absent
    else Maybe.present(SourceValue(raw, Provenance.Resolved(sourceId, key, Maybe.present(raw))))
  }

  def all(prefix: String): Map[String, SourceValue[String]] = {
    val dotPrefix = if (prefix.isEmpty) "" else s"$prefix."
    System.getProperties.asScala.collect {
      case (k, v) if prefix.isEmpty || k == prefix || k.startsWith(dotPrefix) =>
        k -> SourceValue(v, Provenance.Resolved(sourceId, k, Maybe.present(v)))
    }.toMap
  }
}
