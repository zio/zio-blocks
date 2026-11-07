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

package zio.blocks.schema.json

/**
 * Where a value is spliced into a `json"..."` literal, which decides how the
 * macro encodes it: as a key, as a value, or inside a string literal. Every
 * spliced type needs a `Schema` (or is `Json`).
 */
private[schema] sealed trait InterpolationContext

private[schema] object InterpolationContext {

  /**
   * A key position, `{$key: ...}`: the value is written as its key text, with
   * `JsonCodec#encodeKey`.
   */
  case object Key extends InterpolationContext

  /**
   * A value position, `{...: $value}` or `[$value, ...]`: the value is encoded
   * with its JSON codec, and `null` becomes `Json.Null`.
   */
  case object Value extends InterpolationContext

  /**
   * Inside a string literal, `{...: "by $author"}`: the value's key text, with
   * `JsonCodec#encodeKey`, is escaped and joined into the string.
   */
  case object InString extends InterpolationContext
}
