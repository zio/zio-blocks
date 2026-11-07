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

package zio.blocks.schema.yaml

import zio.blocks.schema.DynamicOptic

/**
 * The exception, without a stack trace, that a [[YamlCodec]] throws from
 * `YamlCodec#decodeValue` or `YamlCodec#decodeKey` when the YAML does not match
 * the type (some `java.time` and enum codecs throw their own exception
 * instead). `YamlCodec#decode` catches it and returns a
 * [[zio.blocks.schema.SchemaError]] whose path comes from `spans`, such as
 * `Invalid int value: ten at: .size`.
 *
 * @param spans
 *   the path to the value that failed, outermost node first: a bad `level`
 *   field inside an `inner` field gives `List(Field(inner), Field(level))`.
 *   Each enclosing codec adds its node to the front as the error passes through
 *   it.
 * @param message
 *   the error message without the path, such as `Invalid int value: ten`;
 *   `getMessage` returns it.
 */
class YamlCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
