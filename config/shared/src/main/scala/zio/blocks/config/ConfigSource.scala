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
  def get(key: String): Option[SourceValue[String]]

  /**
   * Return all key-value pairs whose keys start with the given prefix (using
   * dot-separated paths).
   */
  def getAll(prefix: String): Map[String, SourceValue[String]]

  /**
   * Compose this source with a fallback. Keys are looked up in this source
   * first; if absent, the fallback is consulted. Provenance tracks the actual
   * providing source.
   */
  final def orElse(fallback: ConfigSource): ConfigSource = new ConfigSource {
    val sourceId: String = s"${ConfigSource.this.sourceId}|${fallback.sourceId}"

    def get(key: String): Option[SourceValue[String]] =
      ConfigSource.this.get(key).orElse(fallback.get(key))

    def getAll(prefix: String): Map[String, SourceValue[String]] = {
      val builder   = scala.collection.mutable.Map.empty[String, SourceValue[String]]
      val primary   = ConfigSource.this.getAll(prefix)
      val secondary = fallback.getAll(prefix)
      secondary.foreach { case (k, v) => builder(k) = v }
      primary.foreach { case (k, v) => builder(k) = v }
      builder.toMap
    }
  }

  /**
   * Prepend a prefix to all key lookups. For example,
   * `source.withPrefix("db").get("host")` looks up `"db.host"` in the
   * underlying source.
   */
  final def withPrefix(prefix: String): ConfigSource = new ConfigSource {
    val sourceId: String = ConfigSource.this.sourceId

    def get(key: String): Option[SourceValue[String]] =
      ConfigSource.this.get(s"$prefix.$key")

    def getAll(pfx: String): Map[String, SourceValue[String]] =
      ConfigSource.this.getAll(s"$prefix.$pfx")
  }

  /**
   * Apply a key transformation before lookup. The mapper transforms the
   * requested key before it is passed to the underlying source.
   */
  final def withKeyMapper(mapper: KeyMapper, targetFormat: KeyFormat): ConfigSource = new ConfigSource {
    val sourceId: String = ConfigSource.this.sourceId

    def get(key: String): Option[SourceValue[String]] = {
      val mappedKey = mapper.fromCanonical(mapper.toCanonical(key), targetFormat)
      ConfigSource.this.get(mappedKey)
    }

    def getAll(prefix: String): Map[String, SourceValue[String]] =
      ConfigSource.this.getAll(prefix)
  }
}

object ConfigSource {

  /**
   * A config source backed by an in-memory map.
   */
  final case class MapSource(map: Map[String, String], sourceId: String = "map") extends ConfigSource {

    def get(key: String): Option[SourceValue[String]] =
      map.get(key).map(v => SourceValue(v, Provenance.Resolved(sourceId, key, Some(v))))

    def getAll(prefix: String): Map[String, SourceValue[String]] = {
      val dotPrefix = if (prefix.isEmpty) "" else s"$prefix."
      map.collect {
        case (k, v) if prefix.isEmpty || k == prefix || k.startsWith(dotPrefix) =>
          k -> SourceValue(v, Provenance.Resolved(sourceId, k, Some(v)))
      }
    }
  }

  def fromMap(map: Map[String, String], sourceId: String = "map"): ConfigSource =
    MapSource(map, sourceId)
}
