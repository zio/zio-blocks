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

import zio.blocks.schema.DynamicOptic

/**
 * The exception, without a stack trace, that a [[JsonCodec]] throws while
 * reading or writing. The `decode` methods turn it into a
 * [[zio.blocks.schema.SchemaError]] with the path of `spans`; the `encode`
 * methods and `Json.from` let it escape. A custom codec raises one with
 * `JsonCodec#error`.
 *
 * @param spans
 *   the path to the failing value, outermost step first: each enclosing codec
 *   adds its step (a field, an index, a case) to the front as the error passes
 *   through it
 * @param message
 *   what went wrong, such as `illegal number: NaN`
 */
class JsonCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
