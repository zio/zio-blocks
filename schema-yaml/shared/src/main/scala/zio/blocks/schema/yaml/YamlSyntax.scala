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

import zio.blocks.schema.{Schema, SchemaError}

object YamlSyntax {
  implicit class YamlEncoderOps[A](private val value: A) extends AnyVal {
    def toYaml(implicit schema: Schema[A]): Yaml = schema.getInstance(YamlFormat).encodeValue(value)

    def toYamlString(implicit schema: Schema[A]): String =
      YamlWriter.write(schema.getInstance(YamlFormat).encodeValue(value))

    def toYamlBytes(implicit schema: Schema[A]): Array[Byte] =
      YamlWriter.writeToBytes(schema.getInstance(YamlFormat).encodeValue(value))
  }

  implicit class YamlStringOps(private val s: String) extends AnyVal {
    def fromYaml[A](implicit schema: Schema[A]): Either[SchemaError, A] = schema.getInstance(YamlFormat).decode(s)
  }
}
