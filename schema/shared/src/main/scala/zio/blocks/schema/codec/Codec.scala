package zio.blocks.schema.codec
import zio.blocks.schema.SchemaError

abstract class Codec[DecodeInput, EncodeOutput, Value] {

  /**
   * The format of the codec. For any given type `A`, there should be a unique
   * `Format` that corresponds to the binary encoding of that type.
   *
   * The format tag is used to cache derivation of type classes on a per-schema
   * basis.
   */
  def format: Format

  def encode(value: Value, output: EncodeOutput): Unit

  def decode(input: DecodeInput): Either[SchemaError, Value]
}
