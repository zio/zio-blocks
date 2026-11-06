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

package zio.blocks.schema.msgpack

import zio.blocks.schema.codec.BinaryFormat

/**
 * The MessagePack format: pass it to `Schema#derive` to get a
 * [[MessagePackCodec]] for any type that has a schema. MessagePack is a binary
 * format with the same shapes as JSON (maps, arrays, strings, numbers, booleans
 * and nil) and smaller payloads. The format's MIME type is
 * `application/msgpack`, and its deriver is [[MessagePackCodecDeriver]].
 *
 * A derived codec writes a record as a map from field names to values, a
 * sequence as an array, an `Option` as an array of zero or one element, an
 * `Either` as a map of one entry (`left` or `right`), and any other variant as
 * the index of its case followed by the case's value.
 * [[MessagePackCodecDeriver]] lists the layout of every kind of type, including
 * what other MessagePack tools see.
 *
 * {{{
 * import zio.blocks.schema._
 *
 * case class Reading(sensor: String, celsius: Double)
 * object Reading {
 *   implicit val schema: Schema[Reading] = Schema.derived
 * }
 *
 * val codec: MessagePackCodec[Reading] = Schema[Reading].derive(MessagePackFormat)
 * val bytes = codec.encode(Reading("greenhouse-1", 21.5))
 *
 * bytes.length        // 38
 * codec.decode(bytes) // Right(Reading(greenhouse-1,21.5))
 *
 * val mimeType = MessagePackFormat.mimeType // application/msgpack
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/messagepack/ MessagePack]]
 */
object MessagePackFormat extends BinaryFormat("application/msgpack", MessagePackCodecDeriver)
