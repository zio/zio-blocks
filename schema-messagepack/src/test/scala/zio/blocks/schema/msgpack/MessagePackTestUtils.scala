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

package zio.blocks.schema.msgpack

import zio.blocks.schema.{Schema, SchemaError}
import zio.test.Assertion._
import zio.test._

import java.nio.ByteBuffer
import java.util
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.ArraySeq

object MessagePackTestUtils {

  def roundTrip[A](value: A)(implicit schema: Schema[A]): TestResult =
    roundTrip(value, getOrDeriveCodec(schema))

  def roundTrip[A](value: A, codec: MessagePackCodec[A]): TestResult = {
    val encoded        = codec.encode(value)
    val heapByteBuffer = ByteBuffer.allocate(maxBufSize)
    codec.encode(value, heapByteBuffer)
    val encodedFromBuffer = util.Arrays.copyOf(heapByteBuffer.array, heapByteBuffer.position)
    assert(ArraySeq.unsafeWrapArray(encoded))(equalTo(ArraySeq.unsafeWrapArray(encodedFromBuffer))) &&
    assert(codec.decode(encoded))(isRight(equalTo(value))) &&
    assert(codec.decode(toHeapByteBuffer(encoded)))(isRight(equalTo(value))) &&
    assert(codec.decode(toDirectByteBuffer(encoded)))(isRight(equalTo(value)))
  }

  def roundTripBytes[A](value: A, expectedHex: String)(implicit schema: Schema[A]): TestResult =
    roundTripBytes(value, expectedHex, getOrDeriveCodec(schema))

  def roundTripBytes[A](value: A, expectedHex: String, codec: MessagePackCodec[A]): TestResult = {
    val encoded = codec.encode(value)
    assert(toHex(encoded))(equalTo(expectedHex)) &&
    assert(codec.decode(encoded))(isRight(equalTo(value)))
  }

  def encode[A](value: A, expectedHex: String)(implicit schema: Schema[A]): TestResult =
    encode(value, expectedHex, getOrDeriveCodec(schema))

  def encode[A](value: A, expectedHex: String, codec: MessagePackCodec[A]): TestResult = {
    val encoded = codec.encode(value)
    assert(toHex(encoded))(equalTo(expectedHex))
  }

  def decode[A](hexBytes: String, expectedValue: A)(implicit schema: Schema[A]): TestResult =
    decode(hexBytes, expectedValue, getOrDeriveCodec(schema))

  def decode[A](hexBytes: String, expectedValue: A, codec: MessagePackCodec[A]): TestResult = {
    val bytes = fromHex(hexBytes)
    assert(codec.decode(bytes))(isRight(equalTo(expectedValue)))
  }

  def decodeError[A](hexBytes: String, errorSubstring: String)(implicit schema: Schema[A]): TestResult =
    decodeError(hexBytes, errorSubstring, getOrDeriveCodec(schema))

  def decodeError[A](hexBytes: String, errorSubstring: String, codec: MessagePackCodec[A]): TestResult = {
    val bytes = fromHex(hexBytes)
    assert(codec.decode(bytes))(isLeft(hasErrorContaining(errorSubstring)))
  }

  def decodeBytes[A](bytes: Array[Byte], expectedValue: A)(implicit schema: Schema[A]): TestResult =
    decodeBytes(bytes, expectedValue, getOrDeriveCodec(schema))

  def decodeBytes[A](bytes: Array[Byte], expectedValue: A, codec: MessagePackCodec[A]): TestResult =
    assert(codec.decode(bytes))(isRight(equalTo(expectedValue)))

  def decodeFromHex[A](hex: String)(implicit schema: Schema[A]): Either[SchemaError, A] =
    getOrDeriveCodec(schema).decode(fromHex(hex))

  def hasErrorContaining(substring: String): Assertion[SchemaError] =
    hasField[SchemaError, String](
      "message",
      _.getMessage,
      containsString(substring)
    )

  def toHex(bytes: Array[Byte]): String =
    bytes.map(b => f"${b & 0xff}%02x").mkString

  def fromHex(hex: String): Array[Byte] = {
    val cleanHex = hex.replaceAll("\\s", "")
    require(cleanHex.length % 2 == 0, "Hex string must have even length")
    cleanHex.grouped(2).map(Integer.parseInt(_, 16).toByte).toArray
  }

  private def getOrDeriveCodec[A](schema: Schema[A]): MessagePackCodec[A] =
    codecs
      .computeIfAbsent(
        schema,
        (s: Schema[?]) => s.derive(MessagePackFormat.deriver).asInstanceOf[MessagePackCodec[?]]
      )
      .asInstanceOf[MessagePackCodec[A]]

  private def toHeapByteBuffer(bs: Array[Byte]): ByteBuffer = ByteBuffer.wrap(bs)

  private def toDirectByteBuffer(bs: Array[Byte]): ByteBuffer =
    ByteBuffer.allocateDirect(maxBufSize).put(bs).position(0).limit(bs.length)

  private val codecs     = new ConcurrentHashMap[Schema[?], MessagePackCodec[?]]()
  private val maxBufSize = 4096
}
