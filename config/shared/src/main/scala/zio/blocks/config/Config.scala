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
 * Loads typed configuration: it derives a [[ConfigDecoder]] from a `Schema[A]`
 * and runs it against a [[ConfigSource]], which maps dot-separated keys such as
 * `db.port` to strings.
 *
 * Use `load` to get the errors as a value, `loadOrThrow` to stop at startup
 * with a readable report, `loadWithProvenance` to also see where each key came
 * from, and `wire` to decode inside a `Resource.from` dependency graph.
 *
 * {{{
 * import zio.blocks.schema.Schema
 *
 * final case class Db(host: String, port: Int)
 * object Db { implicit val schema: Schema[Db] = Schema.derived }
 *
 * val source = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"))
 * val db     = Config.load[Db](source)
 * // Right(Db(localhost,5432))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/config/ Config]]
 */
object Config {

  /**
   * Returns a shared wire that decodes an `A` from the [[ConfigSource]] of the
   * wire graph with [[loadOrThrow]]. Decoding happens when the resource is
   * allocated, for example by `use`, not when `Resource.from` builds it, and a
   * failure throws [[ConfigLoadException]] there.
   *
   * {{{
   * import zio.blocks.schema.Schema
   * import zio.blocks.scope.{Resource, Unscoped, Wire}
   *
   * final case class Db(host: String, port: Int)
   * object Db {
   *   implicit val schema: Schema[Db]     = Schema.derived
   *   implicit val unscoped: Unscoped[Db] = Unscoped.derived[Db]
   * }
   * final class DbPool(val config: Db)
   *
   * val source = ConfigSource.fromMap(Map("host" -> "db.internal", "port" -> "5432"))
   * val pool   = Resource.from[DbPool](Wire(source), Config.wire[Db])
   * val port   = pool.use(_.config.port)
   * // 5432
   * }}}
   */
  def wire[A](implicit schema: Schema[A]): Wire.Shared[ConfigSource, A] =
    Wire.Shared.fromFunction { (_, ctx) =>
      loadOrThrow[A](ctx.get[ConfigSource])
    }

  /**
   * Returns a shared wire that decodes an `A` from the keys under `prefix` in
   * the [[ConfigSource]] of the wire graph, so field `port` is read from
   * `<prefix>.port`. A decoding failure throws [[ConfigLoadException]]; its
   * report names keys without the prefix (`port`, not `db.port`), a known bug.
   */
  def wire[A](prefix: String)(implicit schema: Schema[A]): Wire.Shared[ConfigSource, A] =
    Wire.Shared.fromFunction { (_, ctx) =>
      loadOrThrow[A](ctx.get[ConfigSource].prefix(prefix))
    }

  /**
   * Decodes an `A` from `source` with a decoder derived from the implicit
   * `Schema[A]`. Each field is read at its name, and a nested field at a
   * dot-separated path such as `db.port`.
   *
   * The decoder reads every field before it returns, so one call reports all
   * the problems it finds. When a record has two or more bad fields, though,
   * the list holds a single [[ConfigError.Composite]] that wraps them, a known
   * bug.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * final case class Db(host: String, port: Int)
   * object Db { implicit val schema: Schema[Db] = Schema.derived }
   *
   * val ok  = Config.load[Db](ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432")))
   * // Right(Db(localhost,5432))
   * val bad = Config.load[Db](ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "54x2"))).left.map(_.map(_.message))
   * // Left(List(Invalid value '54x2' for key 'port' (expected Int) in source 'map': For input string: "54x2"))
   * }}}
   *
   * Each call derives a new [[ConfigDecoder]]. To load the same type
   * repeatedly, for example on every reload, derive one with
   * `ConfigDecoder.derive[A]` and call its `decode` method.
   */
  def load[A](source: ConfigSource)(implicit schema: Schema[A]): Either[::[ConfigError], A] = {
    val decoder = ConfigDecoder.derive[A]
    decoder.decode(source, "")
  }

  /**
   * Decodes an `A` from `source` like the other `load`, with a decoder built by
   * `deriver`, for example one whose sealed-trait discriminator key is not
   * `type`.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * sealed trait Storage
   * object Storage {
   *   final case class Memory(maxEntries: Int) extends Storage
   *   final case class Disk(path: String)      extends Storage
   *   implicit val schema: Schema[Storage] = Schema.derived
   * }
   *
   * val source  = ConfigSource.fromMap(Map("kind" -> "Disk", "path" -> "/var/cache/app"))
   * val storage = Config.load[Storage](source, ConfigDecoderDeriver.discriminator("kind"))
   * // Right(Disk(/var/cache/app))
   * }}}
   */
  def load[A](source: ConfigSource, deriver: ConfigDecoderDeriver)(implicit
    schema: Schema[A]
  ): Either[::[ConfigError], A] = {
    val decoder = schema.deriving(deriver).derive
    decoder.decode(source, "")
  }

  /**
   * Decodes an `A` from `source` like `load`, or throws a
   * [[ConfigLoadException]].
   *
   * The exception's message starts with `Configuration loading failed with n
   * error(s):`, followed by one `  - ` line per element of the error list. A
   * record with several bad fields counts as one error, because they arrive
   * wrapped in one [[ConfigError.Composite]] (a known bug).
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * final case class Db(host: String, port: Int)
   * object Db { implicit val schema: Schema[Db] = Schema.derived }
   *
   * val db = Config.loadOrThrow[Db](ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432")))
   * // Db(localhost,5432)
   *
   * val report =
   *   try { Config.loadOrThrow[Db](ConfigSource.fromMap(Map("host" -> "localhost"), "app.conf")); "" }
   *   catch { case e: ConfigLoadException => e.getMessage }
   * // Configuration loading failed with 1 error(s):
   * //   - Missing required key 'port' in source 'app.conf'
   * }}}
   *
   * @throws ConfigLoadException
   *   when decoding fails
   */
  def loadOrThrow[A](source: ConfigSource)(implicit schema: Schema[A]): A =
    load[A](source) match {
      case Right(a)     => a
      case Left(errors) =>
        val report = formatErrors(errors)
        throw new ConfigLoadException(report, errors)
    }

  /**
   * Decodes an `A` from `source` like `load` and pairs it with `source` in a
   * [[ProvenanceMap]], which reports where a key's value came from.
   *
   * The map asks `source` again on each query, so a field filled from its
   * default value has no provenance, and a source whose contents change (such
   * as [[EnvSource]]) can answer differently from the load.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * final case class Db(host: String, port: Int)
   * object Db { implicit val schema: Schema[Db] = Schema.derived }
   *
   * val loaded = Config.loadWithProvenance[Db](
   *   ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"), "env")
   * )
   * val hostSource = loaded.map(_.provenanceOf("host").toOption.map(_.sourceId))
   * // Right(Some(env))
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
 * Thrown by [[Config.loadOrThrow]], and by the wires of `Config.wire`, when
 * decoding fails.
 *
 * `report` is the multi-line text that is also the exception's message, and
 * `errors` is the non-empty error list the decoder returned.
 */
final class ConfigLoadException(val report: String, val errors: ::[ConfigError]) extends RuntimeException(report)
