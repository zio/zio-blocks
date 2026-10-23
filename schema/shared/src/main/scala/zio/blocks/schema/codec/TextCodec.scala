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

import java.nio.CharBuffer

/**
 * A [[Codec]] that decodes from and encodes into a `CharBuffer`. The codecs of
 * every [[TextFormat]] extend it; among the built-in formats that is CSV's
 * `CsvCodec`. JSON codecs are [[BinaryCodec]]s that work on UTF-8 bytes.
 *
 * @tparam A
 *   The type of values being encoded or decoded.
 */
abstract class TextCodec[A] extends Codec[CharBuffer, CharBuffer, A]
