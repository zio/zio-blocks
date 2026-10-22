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

package zio.blocks.schema.codec

import java.nio.ByteBuffer

/**
 * A [[Codec]] that decodes from and encodes into a `ByteBuffer`. The codecs of
 * every [[BinaryFormat]] extend it: JSON, Avro, MessagePack, Thrift, TOON, XML
 * and YAML, where the text-based ones read and write UTF-8 bytes.
 *
 * @tparam A
 *   The type of values being encoded or decoded.
 */
abstract class BinaryCodec[A] extends Codec[ByteBuffer, ByteBuffer, A]
