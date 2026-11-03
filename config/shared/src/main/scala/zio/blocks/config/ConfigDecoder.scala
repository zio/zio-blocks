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

/**
 * Decodes a value of type `A` from the keys of a [[ConfigSource]] under a
 * dot-separated path.
 *
 * Derive one from a `Schema[A]` with [[ConfigDecoder.derive]], which is what
 * `Config.load` does on every call. Derive it once and reuse it when you decode
 * the same type repeatedly. A derived decoder reads every field before it
 * returns, so one call reports all the problems it finds.
 *
 * {{{
 * import zio.blocks.schema.Schema
 *
 * final case class Http(host: String, port: Int = 8080)
 * object Http { implicit val schema: Schema[Http] = Schema.derived }
 *
 * val decoder = ConfigDecoder.derive[Http]
 * val source  = ConfigSource.fromMap(Map("http.host" -> "0.0.0.0"))
 * val http    = decoder.decode(source, "http")
 * // Right(Http(0.0.0.0,8080))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/config/config-decoder/ ConfigDecoder]]
 */
trait ConfigDecoder[A] {

  /**
   * Decodes an `A` from the keys of `source` under `prefix`, a dot-separated
   * path such as `db`; an empty `prefix` reads from the root, so a field `host`
   * is read at `host`.
   *
   * @return
   *   the value, or a non-empty list of errors
   */
  def decode(source: ConfigSource, prefix: String): Either[::[ConfigError], A]
}

/**
 * Entry points for [[ConfigDecoder]]: `apply` finds an implicit decoder and
 * `derive` builds one from a schema.
 */
object ConfigDecoder {

  /** Returns the implicit `ConfigDecoder[A]` in scope. */
  def apply[A](implicit decoder: ConfigDecoder[A]): ConfigDecoder[A] = decoder

  /**
   * Derives a `ConfigDecoder[A]` from the implicit `Schema[A]` with the default
   * [[ConfigDecoderDeriver]], whose sealed-trait discriminator key is `type`.
   * Derivation walks the whole schema at run time, so derive once at startup
   * when you decode the same type repeatedly.
   */
  def derive[A](implicit schema: Schema[A]): ConfigDecoder[A] =
    schema.deriving(ConfigDecoderDeriver).derive
}
