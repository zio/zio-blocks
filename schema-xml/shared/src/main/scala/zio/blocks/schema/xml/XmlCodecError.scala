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

package zio.blocks.schema.xml

import zio.blocks.schema.DynamicOptic

/**
 * An exception without a stack trace for errors that occur while reading or
 * decoding XML.
 *
 * [[XmlReader]] throws it for malformed input, with the line and column in the
 * message, and `XmlCodec#decodeValue` throws it when the tree does not match
 * the type (some primitive codecs, used on their own, throw another exception
 * instead: the JDK's exception for types such as `DayOfWeek` or `UUID`, and
 * `zio.blocks.schema.json.JsonCodecError` for `Instant`, `Duration` and most
 * other `java.time` types). `XmlCodec#decode` catches it and returns a
 * [[zio.blocks.schema.SchemaError]] whose path is built from `spans`.
 *
 * @param spans
 *   The path to the value that failed, outermost node first: a field `inner`
 *   holding a failing field `n` gives `List(Field(inner), Field(n))`. Each
 *   enclosing codec prepends its node as the error propagates.
 * @param message
 *   A descriptive message providing additional context or details about the
 *   error.
 */
class XmlCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
