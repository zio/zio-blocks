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
 * A source of configuration values: it maps dot-separated keys such as
 * `db.port` to strings, and records where each value came from.
 *
 * Build one with [[ConfigSource.fromMap]], read the process with [[EnvSource]]
 * or [[SysPropSource]], or parse a document with `ConfigSource.fromHocon`,
 * `ConfigSource.fromJson` or `ConfigSource.fromYaml` from the `config-hocon`,
 * `config-json` and `config-yaml` modules. Layer sources with `orElse`, select
 * a section with `prefix`, and translate key spellings with `keyFormat`.
 * `Config.load` decodes a typed value from a source.
 *
 * {{{
 * val defaults  = ConfigSource.fromMap(Map("db.host" -> "localhost", "db.port" -> "5432"), "defaults")
 * val overrides = ConfigSource.fromMap(Map("db.port" -> "6432"), "overrides")
 * val db        = overrides.orElse(defaults).prefix("db")
 *
 * val port     = db.get("port").toOption.map(_.value)
 * // Some(6432)
 * val hostFrom = db.get("host").toOption.map(_.provenance.sourceId)
 * // Some(defaults)
 * }}}
 *
 * A `ConfigSource` is also a [[FlagSource]], so `FlagSource.Registry` can hold
 * one.
 *
 * @see
 *   [[https://zioblocks.com/reference/config/config-source/ ConfigSource]]
 */
trait ConfigSource extends FlagSource {

  /**
   * Names this source in provenance and in error messages. A source built with
   * `orElse` combines the names of its two sources as `first|fallback`.
   */
  def sourceId: String

  /**
   * Looks up one key and returns its value with its provenance, or
   * `Maybe.absent` when this source has no value for the key.
   */
  def get(key: String): Maybe[SourceValue[String]]

  /**
   * Returns the entries whose key is `prefix` or starts with `prefix` followed
   * by a dot; an empty `prefix` returns every entry. The keys in the result are
   * full keys of this source, not keys relative to `prefix`.
   *
   * Decoders call it to find the elements of a sequence and the keys of a map,
   * so a source that returns nothing here cannot supply a map.
   */
  def all(prefix: String): Map[String, SourceValue[String]]

  /**
   * Returns a source that looks a key up in this source first and in `fallback`
   * only when this source has no value for it.
   *
   * A value keeps the provenance of the source that held it. The combined
   * `sourceId` is `this.sourceId|fallback.sourceId`, and errors such as
   * [[ConfigError.MissingKey]] name that combined id. `all` merges the entries
   * of both, and this source's entry wins for a key that both have.
   *
   * {{{
   * val env      = ConfigSource.fromMap(Map("http.port" -> "9000"), "env")
   * val defaults = ConfigSource.fromMap(Map("http.port" -> "8080", "http.host" -> "0.0.0.0"), "defaults")
   * val layered  = env.orElse(defaults)
   *
   * val port = layered.get("http.port").toOption.map(_.value)
   * // Some(9000)
   * val host = layered.get("http.host").toOption.map(_.value)
   * // Some(0.0.0.0)
   * val id   = layered.sourceId
   * // env|defaults
   * }}}
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
   * Returns a view of the section under `prefix`: `get("host")` reads
   * `<prefix>.host` from this source, and `all` returns keys with `<prefix>.`
   * removed. The view keeps this source's `sourceId`, and provenance still
   * names the full key.
   *
   * {{{
   * val source = ConfigSource.fromMap(Map("db.host" -> "localhost", "db.port" -> "5432"))
   * val db     = source.prefix("db")
   *
   * val host = db.get("host").toOption.map(_.value)
   * // Some(localhost)
   * val keys = db.all("").keySet
   * // Set(host, port)
   * }}}
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
   * Returns a source that rewrites each requested key before looking it up
   * here: `mapper.toCanonical` turns it into camelCase, and
   * `mapper.fromCanonical` renders that in `targetFormat`. `all` turns the keys
   * it returns into camelCase with `mapper.toCanonical`.
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

  /**
   * Same as `keyMapper(KeyMapper.default, format)`: lets a decoder that asks
   * for camelCase field names such as `maxPoolSize` read a source whose keys
   * are spelled in `format`, such as `max-pool-size`.
   *
   * {{{
   * val file = ConfigSource.fromMap(Map("max-pool-size" -> "20")).keyFormat(KeyFormat.KebabCase)
   * val size = file.get("maxPoolSize").toOption.map(_.value)
   * // Some(20)
   * }}}
   */
  final def keyFormat(format: KeyFormat): ConfigSource =
    keyMapper(KeyMapper.default, format)
}

/** Constructors for [[ConfigSource]]. */
object ConfigSource {

  /** Joins `prefix` and `key` with a dot; an empty side is left out. */
  private[config] def composeKey(prefix: String, key: String): String =
    if (prefix.isEmpty) key
    else if (key.isEmpty) prefix
    else s"$prefix.$key"

  /**
   * Removes `prefix` and the dot after it from `key`; a key equal to `prefix`
   * becomes empty, and a key outside `prefix` is returned unchanged.
   */
  private[config] def stripKeyPrefix(key: String, prefix: String): String =
    if (prefix.isEmpty) key
    else if (key == prefix) ""
    else if (key.startsWith(s"$prefix.")) key.drop(prefix.length + 1)
    else key

  /**
   * A source backed by an in-memory map whose keys are full dot-separated
   * paths, such as `db.port`. A value's provenance names `sourceId` and the
   * key.
   */
  final case class MapSource(map: Map[String, String], sourceId: String = "map") extends ConfigSource {

    /**
     * Returns the value stored under exactly `key`, with provenance naming
     * `sourceId`, `key` and the raw value, or `Maybe.absent`.
     */
    def get(key: String): Maybe[SourceValue[String]] =
      Maybe.fromOption(map.get(key).map(v => SourceValue(v, Provenance.Resolved(sourceId, key, Maybe.present(v)))))

    /**
     * Returns the entries whose key is `prefix` or starts with `prefix`
     * followed by a dot; an empty `prefix` returns every entry.
     */
    def all(prefix: String): Map[String, SourceValue[String]] = {
      val dotPrefix = if (prefix.isEmpty) "" else s"$prefix."
      map.collect {
        case (k, v) if prefix.isEmpty || k == prefix || k.startsWith(dotPrefix) =>
          k -> SourceValue(v, Provenance.Resolved(sourceId, k, Maybe.present(v)))
      }
    }
  }

  /**
   * Returns a [[MapSource]] over `map`. `sourceId` names the source in
   * provenance and error messages.
   */
  def fromMap(map: Map[String, String], sourceId: String = "map"): ConfigSource =
    MapSource(map, sourceId)
}
