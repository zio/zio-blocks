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
import zio.http.{QueryParams, QueryParamsBuilder}

object QueryCodecDeriver
    extends ParamCodecDeriver[QueryParams, QueryParamsBuilder, QueryCodec](
      "QueryCodec",
      (name, raw, cause) => QueryParamError.Malformed(name, raw, cause).message
    ) {

  protected def fieldKey(fieldName: String): String = fieldName

  protected def readField(input: QueryParams, key: String): Option[Chunk[String]] =
    input.get(key)

  protected def writeField(output: QueryParamsBuilder, key: String, value: String): Unit =
    output.add(key, value)

  protected def makeCodec[A](
    encodeFn: (A, QueryParamsBuilder) => Unit,
    decodeFn: QueryParams => Either[SchemaError, A]
  ): QueryCodec[A] =
    new QueryCodec[A] {
      def encode(value: A, output: QueryParamsBuilder): Unit = encodeFn(value, output)
      def decode(input: QueryParams): Either[SchemaError, A] = decodeFn(input)
    }
}
