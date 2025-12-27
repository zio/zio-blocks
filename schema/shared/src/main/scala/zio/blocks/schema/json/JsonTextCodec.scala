package zio.blocks.schema.json

import zio.blocks.schema.SchemaError
import zio.blocks.schema.codec.TextCodec
import zio.blocks.schema.json.ReaderConfig
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal

final case class JsonTextCodec[A](schema: zio.blocks.schema.Schema[A]) extends TextCodec[A] {

  private val binaryCodec = schema.derive(JsonBinaryCodecDeriver)

  override def encode(value: A, output: CharBuffer): Unit = {
    // Encode to JSON bytes using binary codec
    val jsonBytes = binaryCodec.encode(value)
    // Convert to string
    val jsonString = new String(jsonBytes, StandardCharsets.UTF_8)
    // Put into CharBuffer
    output.clear()
    output.put(jsonString)
    output.flip()
  }

  override def decode(input: CharBuffer): Either[SchemaError, A] = {
    try {
      // Convert CharBuffer to string
      val jsonString = input.toString
      // Convert to UTF-8 bytes
      val jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8)
      // Decode using binary codec with strict config
      val config = ReaderConfig // default has checkForEndOfInput = true
      binaryCodec.decode(jsonBytes, config)
    } catch {
      case error if NonFatal(error) => Left(SchemaError.expectationMismatch(Nil, error.getMessage))
    }
  }
}