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

import zio.blocks.maybe.Maybe

/**
 * A source of configuration key-value pairs.
 */
trait ConfigSource extends FlagSource {

  /**
   * A unique identifier for this source, used in provenance tracking.
   */
  def sourceId: String

  /**
   * Look up a single key and return its value with provenance, or None if
   * absent.
   */
  def get(key: String): Maybe[SourceValue[String]]

  /**
   * Return all key-value pairs whose keys start with the given prefix (using
   * dot-separated paths).
   */
  def all(prefix: String): Map[String, SourceValue[String]]

  /**
   * Compose this source with a fallback. Keys are looked up in this source
   * first; if absent, the fallback is consulted. Provenance tracks the actual
   * providing source.
   */
  final def orElse(fallback: ConfigSource): ConfigSource = new ConfigSource {
    val sourceId: String = s"${ConfigSource.this.sourceId}|${fallback.sourceId}"

    def get(key: String): Maybe[SourceValue[String]] =
      ConfigSource.this.get(key).orElse(fallback.get(key))

    def all(prefix: String): Map[String, SourceValue[String]] = {
      val builder   = scala.collection.mutable.Map.empty[String, SourceValue[String]]
      val primary   = ConfigSource.this.all(prefix)
      val secondary = fallback.all(prefix)
      secondary.foreach { case (k, v) => builder(k) = v }
      primary.foreach { case (k, v) => builder(k) = v }
      builder.toMap
    }
  }

  /**
   * Prepend a prefix to all key lookups. For example,
   * `source.prefix("db").get("host")` looks up `"db.host"` in the underlying
   * source.
   */
  final def prefix(prefix: String): ConfigSource = {
    val keyPrefix = prefix

    new ConfigSource {
      val sourceId: String = ConfigSource.this.sourceId

      def get(key: String): Maybe[SourceValue[String]] =
        ConfigSource.this.get(ConfigSource.composeKey(keyPrefix, key))

      def all(pfx: String): Map[String, SourceValue[String]] = {
        val rawPrefix = ConfigSource.composeKey(keyPrefix, pfx)
        ConfigSource.this.all(rawPrefix).map { case (key, value) =>
          ConfigSource.stripKeyPrefix(key, keyPrefix) -> value
        }
      }
    }
  }

  /**
   * Apply a key transformation before lookup. The mapper transforms the
   * requested key before it is passed to the underlying source.
   */
  final def keyMapper(mapper: KeyMapper, targetFormat: KeyFormat): ConfigSource = new ConfigSource {
    val sourceId: String = ConfigSource.this.sourceId

    private def mapKey(key: String): String =
      mapper.fromCanonical(mapper.toCanonical(key), targetFormat)

    def get(key: String): Maybe[SourceValue[String]] =
      ConfigSource.this.get(mapKey(key))

    def all(prefix: String): Map[String, SourceValue[String]] =
      ConfigSource.this.all(mapKey(prefix)).map { case (key, value) =>
        mapper.toCanonical(key) -> value
      }
  }

  final def keyFormat(format: KeyFormat): ConfigSource =
    keyMapper(KeyMapper.default, format)
}

object ConfigSource {

  private[config] def composeKey(prefix: String, key: String): String =
    if (prefix.isEmpty) key
    else if (key.isEmpty) prefix
    else s"$prefix.$key"

  private[config] def stripKeyPrefix(key: String, prefix: String): String =
    if (prefix.isEmpty) key
    else if (key == prefix) ""
    else if (key.startsWith(s"$prefix.")) key.drop(prefix.length + 1)
    else key

  /**
   * A config source backed by an in-memory map.
   */
  final case class MapSource(map: Map[String, String], sourceId: String = "map") extends ConfigSource {

    def get(key: String): Maybe[SourceValue[String]] =
      Maybe.fromOption(map.get(key).map(v => SourceValue(v, Provenance.Resolved(sourceId, key, Maybe.present(v)))))

    def all(prefix: String): Map[String, SourceValue[String]] = {
      val dotPrefix = if (prefix.isEmpty) "" else s"$prefix."
      map.collect {
        case (k, v) if prefix.isEmpty || k == prefix || k.startsWith(dotPrefix) =>
          k -> SourceValue(v, Provenance.Resolved(sourceId, k, Maybe.present(v)))
      }
    }
  }

  def fromMap(map: Map[String, String], sourceId: String = "map"): ConfigSource =
    MapSource(map, sourceId)
}
