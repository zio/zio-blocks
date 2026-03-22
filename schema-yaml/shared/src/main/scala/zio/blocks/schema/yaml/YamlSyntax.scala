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

import zio.blocks.schema.SchemaError

object YamlSyntax {
  implicit class YamlEncoderOps[A](private val value: A) extends AnyVal {
    def toYaml(implicit encoder: YamlEncoder[A]): Yaml         = encoder.encode(value)
    def toYamlString(implicit encoder: YamlEncoder[A]): String =
      YamlWriter.write(encoder.encode(value))
    def toYamlBytes(implicit encoder: YamlEncoder[A]): Array[Byte] =
      YamlWriter.writeToBytes(encoder.encode(value))
  }

  implicit class YamlStringOps(private val s: String) extends AnyVal {
    def fromYaml[A](implicit decoder: YamlDecoder[A]): Either[SchemaError, A] =
      YamlReader.read(s) match {
        case Left(err)   => Left(SchemaError(err.getMessage))
        case Right(yaml) =>
          decoder.decode(yaml) match {
            case Left(err) => Left(SchemaError(err.getMessage))
            case Right(v)  => Right(v)
          }
      }
  }
}
