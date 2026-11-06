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

package zio.http.schema

/**
 * Why `query` or `queryAll` could not read a query parameter (`queryOrElse`
 * returns its default instead): the key is [[QueryParamError.Missing]], or its
 * value is [[QueryParamError.Malformed]]. Match on the case to tell a client's
 * omission from a bad value, or use `message` for a ready-made sentence.
 */
sealed trait QueryParamError extends Product with Serializable {

  /** A one-line, human-readable description of the error. */
  def message: String
}

/** The two kinds of [[QueryParamError]]. */
object QueryParamError {

  /**
   * The query parameter `key` is absent. Its `message` reads
   * `Missing query parameter: page`.
   */
  final case class Missing(key: String) extends QueryParamError {

    /** `Missing query parameter: ` followed by the key. */
    def message: String = s"Missing query parameter: $key"
  }

  /**
   * The query parameter `key` is present but its `value` does not decode;
   * `cause` says why, for example `Cannot parse 'abc' as Int`. Its `message`
   * names the key, the value and the cause, as in "Malformed query parameter
   * 'limit' value 'abc': Cannot parse 'abc' as Int". A derived [[QueryCodec]]
   * reports a bad value with this same message inside its
   * [[zio.blocks.schema.SchemaError]].
   */
  final case class Malformed(key: String, value: String, cause: String) extends QueryParamError {

    /** Names the key, the raw value and the cause, in that order. */
    def message: String = s"Malformed query parameter '$key' value '$value': $cause"
  }
}
