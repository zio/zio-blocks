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

package zio.http.htmx

/**
 * An `hx-encoding` value: how htmx encodes a request body.
 *
 * The only value is [[HxEncoding.Multipart]], for requests that upload files.
 * htmx's default, URL-encoded form data, has no value here: leave the attribute
 * out to get it.
 *
 * @see
 *   [[https://zioblocks.com/reference/htmx/hx-encoding/ HxEncoding]]
 */
sealed trait HxEncoding extends Product with Serializable {

  /** Renders this value as the literal `hx-encoding` attribute value. */
  def render: String
}

/** The [[HxEncoding]] values. */
object HxEncoding {

  /** `multipart/form-data`, typically for file uploads. */
  case object Multipart extends HxEncoding {

    /** Renders `multipart/form-data`. */
    def render: String = "multipart/form-data"
  }

  /** Renders an [[HxEncoding]] with [[HxEncoding#render]]. */
  implicit val toHtmxValue: ToHtmxValue[HxEncoding] = new ToHtmxValue[HxEncoding] {
    def toHtmxValue(value: HxEncoding): String = value.render
  }
}
