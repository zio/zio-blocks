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

package zio.blocks.htmx

/**
 * Encodings supported by the `hx-encoding` attribute.
 *
 * The current DSL exposes HTMX's explicit multipart form encoding for requests
 * that include file uploads or other multipart data.
 *
 * HTMX's default URL-encoded form behavior is represented by omitting the
 * attribute entirely, so there is no separate DSL value for the default case.
 */
sealed trait HxEncoding extends Product with Serializable {
  def render: String
}

object HxEncoding {

  /** Uses `multipart/form-data`, typically for file uploads. */
  case object Multipart extends HxEncoding {
    def render: String = "multipart/form-data"
  }

  implicit val toHtmxValue: ToHtmxValue[HxEncoding] = new ToHtmxValue[HxEncoding] {
    def toHtmxValue(value: HxEncoding): String = value.render
  }
}
