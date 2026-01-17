/*
 * Copyright 2023 ZIO Blocks Maintainers
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
 * Represents a specialized codec for encoding and decoding values between
 * binary input and output representations using ByteBuffer.
 *
 * This abstraction is commonly used with binary formats (e.g., Avro) where both
 * encoding and decoding operations are performed using ByteBuffer as the
 * underlying data representation.
 *
 * @tparam A
 *   The type of values being encoded or decoded.
 */
abstract class BinaryCodec[A] extends Codec[ByteBuffer, ByteBuffer, A]
