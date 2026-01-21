package zio.blocks.schema.messagepack

import zio.blocks.schema._
import zio.test._

object MessagePackTestUtils {

  def roundTrip[A: Schema](value: A)(implicit codec: MessagePackBinaryCodec[A] = null): TestResult = {
    val actualCodec = if (codec != null) codec else Schema[A].derive(MessagePackFormat.deriver)
    val encoded     = actualCodec.encode(value)
    val decoded     = actualCodec.decode(encoded)
    assertTrue(decoded == Right(value))
  }

  def roundTripWithCodec[A](value: A, codec: MessagePackBinaryCodec[A]): TestResult = {
    val encoded = codec.encode(value)
    val decoded = codec.decode(encoded)
    assertTrue(decoded == Right(value))
  }

  def decodeError[A: Schema](input: Array[Byte], expectedError: String): TestResult = {
    val codec  = Schema[A].derive(MessagePackFormat.deriver)
    val result = codec.decode(input)
    result match {
      case Left(error) => assertTrue(error.getMessage.contains(expectedError.dropRight(1)))
      case Right(v)    => assertTrue(false) ?? s"Expected error but got $v"
    }
  }

  def decodeErrorWithCodec[A](
    input: Array[Byte],
    codec: MessagePackBinaryCodec[A],
    expectedError: String
  ): TestResult = {
    val result = codec.decode(input)
    result match {
      case Left(error) => assertTrue(error.getMessage.contains(expectedError.dropRight(1)))
      case Right(v)    => assertTrue(false) ?? s"Expected error but got $v"
    }
  }

  implicit lazy val eitherStringIntSchema: Schema[Either[String, Int]] = Schema.derived[Either[String, Int]]
}
