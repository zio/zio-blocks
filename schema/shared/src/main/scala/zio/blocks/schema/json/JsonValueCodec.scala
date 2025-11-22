package zio.blocks.schema.json

/**
 * A `JsonValueCodec[A]` instance has the ability to decode and encode JSON
 * values to/from values of type `A`, potentially failing with an error if the
 * JSON content does not encode a value of the given type or `A` cannot be
 * encoded properly according to RFC-8259 requirements.
 */
trait JsonValueCodec[A] extends Serializable {

  /**
   * Attempts to decode a value of type `A` from the specified `JsonReader`, but
   * may fail with `JsonReaderException` error if the JSON input does not encode
   * a value of this type.
   *
   * @param in
   *   an instance of `JsonReader` which provide an access to the JSON input to
   *   parse a JSON value to value of type `A`
   * @param default
   *   the placeholder value provided to initialize some possible local
   *   variables
   */
  def decodeValue(in: JsonReader, default: A): A

  /**
   * Encodes the specified value using provided `JsonWriter`, but may fail with
   * `JsonWriterException` if it cannot be encoded properly according to
   * RFC-8259 requirements.
   *
   * @param x
   *   the value provided for serialization
   * @param out
   *   an instance of `JsonWriter` which provides access to JSON output to
   *   serialize the specified value as a JSON value
   */
  def encodeValue(x: A, out: JsonWriter): Unit

  /**
   * Returns some placeholder value that will be used by the high level code
   * that generates codec instances to initialize local variables for parsed
   * field values which have a codec that was injected using implicit `val`.
   *
   * See the `jsoniter-scala-macros` sub-project code and its tests for usages
   * of `.nullValue` calls.
   */
  def nullValue: A
}
