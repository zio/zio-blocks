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
