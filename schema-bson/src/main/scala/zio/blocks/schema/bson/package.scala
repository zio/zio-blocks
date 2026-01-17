package zio.blocks.schema

/**
 * BSON (Binary JSON) format support for ZIO Blocks Schema.
 * 
 * This package provides encoding and decoding capabilities for BSON format,
 * which is the binary serialization format used by MongoDB and other systems.
 * 
 * == Quick Start ==
 * 
 * {{{
 * import zio.blocks.schema._
 * import zio.blocks.schema.bson.BsonFormat
 * 
 * case class Person(name: String, age: Int)
 * object Person {
 *   given Reflect[BsonFormat.type, Person] = Reflect.derive[BsonFormat.type, Person]
 * }
 * 
 * val person = Person("Alice", 30)
 * val codec = summon[BsonFormat.TypeClass[Person]]
 * 
 * // Encode to BSON
 * val encoded: Array[Byte] = codec.encode(person)
 * 
 * // Decode from BSON
 * val decoded: Either[SchemaError, Person] = codec.decode(java.nio.ByteBuffer.wrap(encoded))
 * }}}
 * 
 * == Supported Types ==
 * 
 * - All Scala primitive types (Boolean, Byte, Short, Int, Long, Float, Double, Char, String)
 * - BigInt and BigDecimal
 * - Java time types (Instant, LocalDate, LocalTime, LocalDateTime, Duration, Period, etc.)
 * - Collections (List, Vector, Set, Map)
 * - Optional values (Option)
 * - Case classes (records)
 * - Sealed traits (variants/enums)
 * - UUID and Currency
 * - MongoDB ObjectId
 * 
 * == BSON Format Details ==
 * 
 * BSON is a binary format that extends JSON with additional data types.
 * It is designed to be:
 * - Lightweight: Minimal overhead
 * - Traversable: Fast to navigate
 * - Efficient: Optimized for encoding and decoding
 * 
 * @see [[BsonFormat]] for the main format implementation
 * @see [[BsonBinaryCodec]] for the codec trait
 */
package object bson {
  
  /**
   * Type alias for BSON codec.
   */
  type BsonCodec[A] = BsonFormat.TypeClass[A]
  
  /**
   * Convenience method to get a BSON codec for a type.
   * 
   * @tparam A the type to encode/decode
   * @return the BSON codec for type A
   */
  def bsonCodec[A](implicit codec: BsonCodec[A]): BsonCodec[A] = codec
  
  /**
   * Encode a value to BSON bytes.
   * 
   * @param value the value to encode
   * @param codec the codec to use
   * @tparam A the type of the value
   * @return the encoded bytes
   */
  def encodeBson[A](value: A)(implicit codec: BsonCodec[A]): Array[Byte] =
    codec.encode(value)
  
  /**
   * Decode a value from BSON bytes.
   * 
   * @param bytes the bytes to decode
   * @param codec the codec to use
   * @tparam A the type to decode to
   * @return either a schema error or the decoded value
   */
  def decodeBson[A](bytes: Array[Byte])(implicit codec: BsonCodec[A]): Either[SchemaError, A] =
    codec.decode(java.nio.ByteBuffer.wrap(bytes))
  
  /**
   * Decode a value from a ByteBuffer.
   * 
   * @param buffer the buffer to decode from
   * @param codec the codec to use
   * @tparam A the type to decode to
   * @return either a schema error or the decoded value
   */
  def decodeBson[A](buffer: java.nio.ByteBuffer)(implicit codec: BsonCodec[A]): Either[SchemaError, A] =
    codec.decode(buffer)
}
