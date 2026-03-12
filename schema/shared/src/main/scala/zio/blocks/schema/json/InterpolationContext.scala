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
 * Represents the context in which a JSON interpolation occurs.
 *
 * Used by the JSON string interpolator macro to determine type constraints:
 *   - Key: Requires `Keyable[A]` - only types that can be used as JSON keys
 *   - Value: Requires `JsonEncoder[A]` - any type that can be encoded to JSON
 *   - InString: Requires `Keyable[A]` - embedded within a JSON string literal
 */
private[schema] sealed trait InterpolationContext

private[schema] object InterpolationContext {

  /**
   * Interpolation in key position: `{$key: ...}`
   *
   * Only "keyable" types (those defined in `PrimitiveType`) are allowed. The
   * value will be converted to a string using `Keyable[A].asKey`.
   */
  case object Key extends InterpolationContext

  /**
   * Interpolation in value position: `{...: $value}` or `[$value, ...]`
   *
   * Any type with a `JsonEncoder[A]` instance is allowed.
   */
  case object Value extends InterpolationContext

  /**
   * Interpolation inside a string literal: `{...: "hello $name"}`
   *
   * Only "keyable" types are allowed. The value will be converted to a string
   * using `Keyable[A].asKey` and concatenated into the JSON string.
   */
  case object InString extends InterpolationContext
}
