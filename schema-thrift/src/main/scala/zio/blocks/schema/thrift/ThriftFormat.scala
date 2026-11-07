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

package zio.blocks.schema.thrift

import zio.blocks.schema.codec.BinaryFormat

/**
 * The Thrift binary format, with MIME type `application/thrift`. Pass it to
 * `Schema#derive` to get a [[ThriftCodec]] for a type, which encodes values
 * with Apache Thrift's `TBinaryProtocol`. Its codecs come from
 * [[ThriftCodecDeriver]].
 *
 * {{{
 * import zio.blocks.schema._
 *
 * case class AuditEntry(actor: String, action: String)
 * object AuditEntry {
 *   implicit val schema: Schema[AuditEntry] = Schema.derived
 * }
 *
 * val codec: ThriftCodec[AuditEntry] = AuditEntry.schema.derive(ThriftFormat)
 * val mimeType                       = ThriftFormat.mimeType // application/thrift
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/thrift/ Thrift Codec Module]]
 */
object ThriftFormat extends BinaryFormat("application/thrift", ThriftCodecDeriver)
