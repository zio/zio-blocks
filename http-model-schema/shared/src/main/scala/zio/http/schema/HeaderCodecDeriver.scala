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

package zio.http.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.SchemaError
import zio.http.{Headers, HeadersBuilder}

object HeaderCodecDeriver
    extends ParamCodecDeriver[Headers, HeadersBuilder, HeaderCodec](
      "HeaderCodec",
      (name, raw, cause) => HeaderError.Malformed(name, raw, cause).message
    ) {

  protected def fieldKey(fieldName: String): String = toHeaderName(fieldName)

  protected def readField(input: Headers, key: String): Option[Chunk[String]] = {
    val all = input.rawGetAll(key)
    if (all.isEmpty) None else Some(all)
  }

  protected def writeField(output: HeadersBuilder, key: String, value: String): Unit =
    output.add(key, value)

  protected def makeCodec[A](
    encodeFn: (A, HeadersBuilder) => Unit,
    decodeFn: Headers => Either[SchemaError, A]
  ): HeaderCodec[A] =
    new HeaderCodec[A] {
      def encode(value: A, output: HeadersBuilder): Unit = encodeFn(value, output)
      def decode(input: Headers): Either[SchemaError, A] = decodeFn(input)
    }

  private def toHeaderName(fieldName: String): String = {
    val builder = new StringBuilder
    var index   = 0
    while (index < fieldName.length) {
      val char = fieldName.charAt(index)
      if (index > 0 && char.isUpper) builder.append('-')
      builder.append(char.toUpper)
      index += 1
    }
    builder.toString()
  }
}
