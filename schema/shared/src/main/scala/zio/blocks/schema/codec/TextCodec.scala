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

import java.nio.CharBuffer

/**
 * Represents a specialized codec for encoding and decoding values between
 * text-based input and output representations using CharBuffer.
 *
 * This abstraction is typically used with text-based formats (e.g., JSON) where
 * both encoding and decoding operations are performed using CharBuffer as the
 * underlying data representation.
 *
 * @tparam A
 *   The type of values being encoded or decoded.
 */
abstract class TextCodec[A] extends Codec[CharBuffer, CharBuffer, A]
