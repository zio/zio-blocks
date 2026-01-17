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

package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic

/**
 * A custom exception class without the stack trace representing errors that
 * occur during JSON binary codec operations.
 *
 * The `JsonBinaryCodecError` class extends `Throwable` to provide enhanced
 * error reporting for scenarios involving JSON encoding or decoding errors
 * where dynamic data paths (spans) are involved. These spans represent the
 * stack of nodes traversed within the data structure when the error occurred.
 * So that top-level spans are at the end of the list.
 *
 * @param spans
 *   A list of `DynamicOptic.Node` objects representing the traversal path
 *   within the data structure where the error occurred. Each node encapsulates
 *   information about a specific element (field, index, etc.).
 * @param message
 *   A descriptive message providing additional context or details about the
 *   error.
 */
class JsonBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
