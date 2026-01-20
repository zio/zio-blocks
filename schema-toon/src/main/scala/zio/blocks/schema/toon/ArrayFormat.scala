/*
 * Copyright 2023 ZIO Blocks Maintainers
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

package zio.blocks.schema.toon

/**
 * Specifies how arrays should be encoded in TOON format.
 */
sealed trait ArrayFormat

object ArrayFormat {

  /**
   * Automatically select the most compact format based on array contents:
   *   - Tabular for uniform object arrays with primitive fields
   *   - Inline for primitive arrays
   *   - List for heterogeneous or nested data
   */
  case object Auto extends ArrayFormat

  /**
   * Force tabular format: `items[N]{field1,field2}: val1,val2` Falls back to
   * List if an array is not tabular-eligible.
   */
  case object Tabular extends ArrayFormat

  /**
   * Force inline format: `items[N]: val1,val2,val3` Only valid for primitive
   * arrays; falls back to List for complex types.
   */
  case object Inline extends ArrayFormat

  /**
   * Force list format with `- ` markers.
   */
  case object List extends ArrayFormat
}
