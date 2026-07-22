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
 * A typeclass for decoding a value of type `A` from a `ConfigSource` rooted at
 * a given key prefix.
 *
 * All errors are accumulated (not fail-fast) and returned as a non-empty list
 * of `ConfigError`.
 */
trait ConfigDecoder[A] {

  /**
   * Decode a value of type `A` from the given `source`, using `prefix` as the
   * root path for key lookups (dot-separated, e.g. "db.host").
   *
   * @return
   *   Right(a) on success, Left(errors) with all accumulated errors on failure.
   */
  def decode(source: ConfigSource, prefix: String): Either[::[ConfigError], A]
}

object ConfigDecoder {

  def apply[A](implicit decoder: ConfigDecoder[A]): ConfigDecoder[A] = decoder

  /**
   * Derive a `ConfigDecoder[A]` from a `Schema[A]` using the default
   * `ConfigDecoderDeriver`.
   *
   * For one-off loads, calling this at the use site is fine. If you decode the
   * same type repeatedly, derive once at startup and reuse the returned
   * decoder.
   */
  def derive[A](implicit schema: Schema[A]): ConfigDecoder[A] =
    schema.deriving(ConfigDecoderDeriver).derive
}
