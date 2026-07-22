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

import zio.blocks.schema.derive.{Derivable, Deriver}

/**
 * Base class for query-parameter serialization formats. Pairs a MIME type with
 * a [[zio.blocks.schema.derive.Deriver]] that can derive [[QueryCodec]]
 * instances for arbitrary schema-described types.
 */
abstract class QueryFormat[TC[A] <: QueryCodec[A]](val mimeType: String, val deriver: Deriver[TC]) {

  implicit final def derivable: Derivable[this.type, TC] =
    new Derivable[this.type, TC] {
      def deriver(d: QueryFormat.this.type): Deriver[TC] = QueryFormat.this.deriver
    }
}

sealed abstract class DefaultQueryFormat
    extends QueryFormat[QueryCodec]("application/x-www-form-urlencoded", QueryCodecDeriver)

case object DefaultQueryFormat extends DefaultQueryFormat
