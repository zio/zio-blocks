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

import zio.blocks.schema.Schema
import zio.blocks.scope.Wire

/**
 * Entry point for loading typed configuration from a `ConfigSource`.
 */
object Config {

  /**
   * Create a shared config wire that decodes `A` from an injected
   * [[ConfigSource]].
   */
  def wire[A](implicit schema: Schema[A]): Wire.Shared[ConfigSource, A] =
    Wire.Shared.fromFunction { (_, ctx) =>
      loadOrThrow[A](ctx.get[ConfigSource])
    }

  /**
   * Create a shared config wire that decodes `A` from an injected
   * [[ConfigSource]] under the specified prefix.
   */
  def wire[A](prefix: String)(implicit schema: Schema[A]): Wire.Shared[ConfigSource, A] =
    Wire.Shared.fromFunction { (_, ctx) =>
      loadOrThrow[A](ctx.get[ConfigSource].withPrefix(prefix))
    }

  /**
   * Decode a value of type `A` from `source` using the implicit `Schema[A]`.
   *
   * Typical usage in Scala 3 derives both `Schema` and `Unscoped` directly on
   * the case class and then loads from a `ConfigSource`:
   * {{{
   * import zio.blocks.scope.Unscoped
   *
   * final case class Db(host: String, port: Int) derives Schema, Unscoped
   *
   * val result = Config.load[Db](
   *   ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"))
   * )
   * }}}
   *
   * This method derives a fresh [[ConfigDecoder]] for each call. If you load
   * the same type repeatedly (for example during reloads), derive the decoder
   * once with `ConfigDecoder.derive[A]` and reuse it directly.
   *
   * Returns all accumulated `ConfigError`s instead of failing fast.
   */
  def load[A](source: ConfigSource)(implicit schema: Schema[A]): Either[::[ConfigError], A] = {
    val decoder = ConfigDecoder.derive[A]
    decoder.decode(source, "")
  }

  /**
   * Decode a value of type `A` from `source` using an explicit
   * `ConfigDecoderDeriver` and the implicit `Schema[A]`.
   */
  def load[A](source: ConfigSource, deriver: ConfigDecoderDeriver)(implicit
    schema: Schema[A]
  ): Either[::[ConfigError], A] = {
    val decoder = schema.deriving(deriver).derive
    decoder.decode(source, "")
  }

  def withKeyFormat(source: ConfigSource, format: KeyFormat): ConfigSource =
    source.withKeyMapper(KeyMapper.default, format)

  /**
   * Load a value of type `A` from the given source, or throw with a formatted
   * error report.
   *
   * Requires an implicit `Schema[A]`:
   * {{{
   * import zio.blocks.scope.Unscoped
   *
   * final case class Db(host: String, port: Int) derives Schema, Unscoped
   *
   * val db = Config.loadOrThrow[Db](
   *   ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"))
   * )
   * }}}
   *
   * Throws [[ConfigLoadException]] when decoding fails.
   */
  def loadOrThrow[A](source: ConfigSource)(implicit schema: Schema[A]): A =
    load[A](source) match {
      case Right(a)     => a
      case Left(errors) =>
        val report = formatErrors(errors)
        throw new ConfigLoadException(report, errors)
    }

  /**
   * Load a value of type `A` together with provenance information for each
   * resolved key.
   *
   * Requires an implicit `Schema[A]` and returns a `ProvenanceMap[A]` so
   * callers can inspect where each resolved field came from:
   * {{{
   * import zio.blocks.scope.Unscoped
   *
   * final case class Db(host: String, port: Int) derives Schema, Unscoped
   *
   * val loaded = Config.loadWithProvenance[Db](
   *   ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"), "env")
   * )
   * val hostSource = loaded.map(_.provenanceOf("host"))
   * }}}
   */
  def loadWithProvenance[A](
    source: ConfigSource
  )(implicit schema: Schema[A]): Either[::[ConfigError], ProvenanceMap[A]] =
    load[A](source).map { a =>
      ProvenanceMap(a, source)
    }

  private def formatErrors(errors: ::[ConfigError]): String = {
    val sb = new StringBuilder
    sb.append("Configuration loading failed with ")
    sb.append(errors.length)
    sb.append(" error(s):\n")
    errors.toList.foreach { e =>
      sb.append("  - ")
      sb.append(e.message)
      sb.append('\n')
    }
    sb.toString
  }
}

/**
 * Exception thrown by [[Config.loadOrThrow]] when configuration loading fails.
 *
 * Carries both the formatted multi-line report shown to users and the original
 * non-empty list of accumulated `ConfigError`s for programmatic inspection.
 */
final class ConfigLoadException(val report: String, val errors: ::[ConfigError]) extends RuntimeException(report)
