package zio.blocks.schema.json

/**
 * A `JsonKeyCodec[A]` instance has the ability to decode and encode JSON keys
 * to/from values of type `A`, potentially failing with an error if the JSON
 * input is not a key or does not encode a value of the given type or `A` cannot
 * be encoded properly according to RFC-8259 requirements.
 */
trait JsonKeyCodec[A] extends Serializable {

  /**
   * Attempts to decode a value of type `A` from the specified `JsonReader`, but
   * may fail with `JsonReaderException` error if the JSON input is not a key or
   * does not encode a value of this type.
   *
   * @param in
   *   an instance of `JsonReader` which provide an access to the JSON input to
   *   parse a JSON key to value of type `A`
   */
  def decodeKey(in: JsonReader): A

  /**
   * Encodes the specified value using provided `JsonWriter` as a JSON key, but
   * may fail with `JsonWriterException` if it cannot be encoded properly
   * according to RFC-8259 requirements.
   *
   * @param x
   *   the value provided for serialization
   * @param out
   *   an instance of `JsonWriter` which provides access to JSON output to
   *   serialize the specified value as a JSON key
   */
  def encodeKey(x: A, out: JsonWriter): Unit
}
