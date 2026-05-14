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

/**
 * Config source backed by environment variables (JVM). Key lookup converts dots
 * to underscores and uppercases: `db.host` → `DB_HOST`. A `null` env var is
 * treated as absent (None). An empty string `""` is present (Some).
 */
object EnvSource extends ConfigSource {
  val sourceId: String = "env"

  def get(key: String): Option[ConfigValue[String]] = {
    val envKey = toEnvKey(key)
    val raw    = System.getenv(envKey)
    if (raw == null) None
    else Some(ConfigValue(raw, Provenance.Resolved(sourceId, envKey, Some(raw))))
  }

  def getAll(prefix: String): Map[String, ConfigValue[String]] = {
    val envPrefix = toEnvKey(prefix)
    val dotPrefix = if (envPrefix.isEmpty) "" else s"${envPrefix}_"
    System
      .getenv()
      .asScala
      .collect {
        case (k, v) if envPrefix.isEmpty || k == envPrefix || k.startsWith(dotPrefix) =>
          k -> ConfigValue(v, Provenance.Resolved(sourceId, k, Some(v)))
      }
      .toMap
  }

  private def toEnvKey(key: String): String =
    key.replace('.', '_').toUpperCase
}

/**
 * Config source backed by JVM system properties. Key lookup uses the
 * dot-separated path directly.
 */
object SysPropSource extends ConfigSource {
  val sourceId: String = "sysprop"

  def get(key: String): Option[ConfigValue[String]] = {
    val raw = System.getProperty(key)
    if (raw == null) None
    else Some(ConfigValue(raw, Provenance.Resolved(sourceId, key, Some(raw))))
  }

  def getAll(prefix: String): Map[String, ConfigValue[String]] = {
    val dotPrefix = if (prefix.isEmpty) "" else s"$prefix."
    System.getProperties.asScala.collect {
      case (k, v) if prefix.isEmpty || k == prefix || k.startsWith(dotPrefix) =>
        k -> ConfigValue(v, Provenance.Resolved(sourceId, k, Some(v)))
    }.toMap
  }
}
