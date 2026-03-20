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

package zio.blocks.schema

import zio.blocks.schema.json.Json
import zio.blocks.schema.patch.{Patch, PatchMode}

trait SyntaxVersionSpecific {
  extension [A](self: A) {
    def applyPatch(patch: Patch[A]): A = patch(self)

    def applyPatchStrict(patch: Patch[A]): Either[SchemaError, A] = patch(self, PatchMode.Strict)

    def diff(that: A)(using schema: Schema[A]): Patch[A] = schema.diff(self, that)

    def show(using schema: Schema[A]): String = schema.toDynamicValue(self).toString

    def toJson(using schema: Schema[A]): Json = schema.jsonCodec.encodeValue(self)

    def toJsonString(using schema: Schema[A]): String = schema.jsonCodec.encodeToString(self)

    def toJsonBytes(using schema: Schema[A]): Array[Byte] = schema.jsonCodec.encode(self)
  }

  extension (self: String) {
    def fromJson[A](using schema: Schema[A]): Either[SchemaError, A] = schema.jsonCodec.decode(self)
  }

  extension (self: Array[Byte]) {
    def fromJson[A](using schema: Schema[A]): Either[SchemaError, A] = schema.jsonCodec.decode(self)
  }
}
