package zio.blocks.schema.codec
import zio.blocks.schema.SchemaError

abstract class Codec[DecodeInput, EncodeOutput, Value] {
  def encode(value: Value, output: EncodeOutput): Unit

  def decode(input: DecodeInput): Either[SchemaError, Value]
}
