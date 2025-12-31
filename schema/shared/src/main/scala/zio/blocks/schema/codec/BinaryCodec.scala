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
