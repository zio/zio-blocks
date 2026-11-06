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

package zio.blocks.schema.avro

import zio.blocks.schema.codec.BinaryFormat

/**
 * The Avro binary format, with MIME type `application/avro`. Pass it to
 * `Schema#derive` to get an [[AvroCodec]] for a type, which encodes values as
 * Avro binary data and exposes the matching Avro schema. Its codecs come from
 * [[AvroCodecDeriver]].
 *
 * {{{
 * import zio.blocks.schema._
 *
 * case class SensorReading(sensorId: String, celsius: Double)
 * object SensorReading {
 *   implicit val schema: Schema[SensorReading] = Schema.derived
 * }
 *
 * val codec: AvroCodec[SensorReading] = SensorReading.schema.derive(AvroFormat)
 * val mimeType                        = AvroFormat.mimeType // application/avro
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/avro/ Avro Codec Module]]
 */
object AvroFormat extends BinaryFormat("application/avro", AvroCodecDeriver)
