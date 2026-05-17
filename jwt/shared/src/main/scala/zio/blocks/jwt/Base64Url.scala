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

package zio.blocks.jwt

object Base64Url {
  private[this] val Alphabet: Array[Char] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray
  private[this] val DecodeTable: Array[Int] = {
    val table = Array.fill(128)(-1)
    var index = 0
    while (index < Alphabet.length) {
      table(Alphabet(index).toInt) = index
      index += 1
    }
    table
  }

  def encode(bytes: Array[Byte]): String = {
    val outputLength = (bytes.length / 3) * 4 + (bytes.length % 3 match {
      case 0 => 0
      case 1 => 2
      case _ => 3
    })
    val chars        = new Array[Char](outputLength)

    var inputIndex  = 0
    var outputIndex = 0

    while (inputIndex + 2 < bytes.length) {
      val b0 = bytes(inputIndex) & 0xff
      val b1 = bytes(inputIndex + 1) & 0xff
      val b2 = bytes(inputIndex + 2) & 0xff

      chars(outputIndex) = Alphabet(b0 >>> 2)
      chars(outputIndex + 1) = Alphabet(((b0 & 0x03) << 4) | (b1 >>> 4))
      chars(outputIndex + 2) = Alphabet(((b1 & 0x0f) << 2) | (b2 >>> 6))
      chars(outputIndex + 3) = Alphabet(b2 & 0x3f)

      inputIndex += 3
      outputIndex += 4
    }

    val remaining = bytes.length - inputIndex
    if (remaining == 1) {
      val b0 = bytes(inputIndex) & 0xff
      chars(outputIndex) = Alphabet(b0 >>> 2)
      chars(outputIndex + 1) = Alphabet((b0 & 0x03) << 4)
    } else if (remaining == 2) {
      val b0 = bytes(inputIndex) & 0xff
      val b1 = bytes(inputIndex + 1) & 0xff
      chars(outputIndex) = Alphabet(b0 >>> 2)
      chars(outputIndex + 1) = Alphabet(((b0 & 0x03) << 4) | (b1 >>> 4))
      chars(outputIndex + 2) = Alphabet((b1 & 0x0f) << 2)
    }

    new String(chars)
  }

  def decode(s: String): Either[JwtError, Array[Byte]] = {
    if (s.indexOf('=') >= 0) Left(JwtError.InvalidToken("base64url input must not contain padding"))
    else {
      val remainder = s.length % 4
      if (remainder == 1) Left(JwtError.InvalidToken("invalid base64url length"))
      else {
        val outputLength = (s.length / 4) * 3 + (remainder match {
          case 0 => 0
          case 2 => 1
          case 3 => 2
          case _ => 0
        })
        val bytes        = new Array[Byte](outputLength)

        var inputIndex  = 0
        var outputIndex = 0

        def decodeChar(char: Char): Either[JwtError, Int] =
          if (char.toInt >= 0 && char.toInt < DecodeTable.length) {
            val value = DecodeTable(char.toInt)
            if (value >= 0) Right(value)
            else Left(JwtError.InvalidToken("invalid base64url character"))
          } else Left(JwtError.InvalidToken("invalid base64url character"))

        while (inputIndex + 3 < s.length) {
          val result = for {
            c0 <- decodeChar(s.charAt(inputIndex))
            c1 <- decodeChar(s.charAt(inputIndex + 1))
            c2 <- decodeChar(s.charAt(inputIndex + 2))
            c3 <- decodeChar(s.charAt(inputIndex + 3))
          } yield {
            bytes(outputIndex) = ((c0 << 2) | (c1 >>> 4)).toByte
            bytes(outputIndex + 1) = (((c1 & 0x0f) << 4) | (c2 >>> 2)).toByte
            bytes(outputIndex + 2) = (((c2 & 0x03) << 6) | c3).toByte
          }

          result match {
            case Left(error) => return Left(error)
            case Right(_)    =>
              inputIndex += 4
              outputIndex += 3
          }
        }

        val tailLength = s.length - inputIndex
        if (tailLength == 2) {
          val result = for {
            c0 <- decodeChar(s.charAt(inputIndex))
            c1 <- decodeChar(s.charAt(inputIndex + 1))
            _ <- if ((c1 & 0x0f) == 0) Right(())
                 else Left(JwtError.InvalidToken("invalid trailing base64url bits"))
          } yield bytes(outputIndex) = ((c0 << 2) | (c1 >>> 4)).toByte

          result match {
            case Left(error) => Left(error)
            case Right(_)    => Right(bytes)
          }
        } else if (tailLength == 3) {
          val result = for {
            c0 <- decodeChar(s.charAt(inputIndex))
            c1 <- decodeChar(s.charAt(inputIndex + 1))
            c2 <- decodeChar(s.charAt(inputIndex + 2))
            _ <- if ((c2 & 0x03) == 0) Right(())
                 else Left(JwtError.InvalidToken("invalid trailing base64url bits"))
          } yield {
            bytes(outputIndex) = ((c0 << 2) | (c1 >>> 4)).toByte
            bytes(outputIndex + 1) = (((c1 & 0x0f) << 4) | (c2 >>> 2)).toByte
          }

          result match {
            case Left(error) => Left(error)
            case Right(_)    => Right(bytes)
          }
        } else Right(bytes)
      }
    }
  }
}
