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

import zio.test._

object Base64UrlSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("Base64UrlSpec")(
    suite("encode")(
      test("empty array encodes to empty string") {
        assertTrue(Base64Url.encode(Array.emptyByteArray) == "")
      },
      test("RFC 4648 vector: 'f'") {
        assertTrue(Base64Url.encode("f".getBytes("UTF-8")) == "Zg")
      },
      test("RFC 4648 vector: 'fo'") {
        assertTrue(Base64Url.encode("fo".getBytes("UTF-8")) == "Zm8")
      },
      test("RFC 4648 vector: 'foo'") {
        assertTrue(Base64Url.encode("foo".getBytes("UTF-8")) == "Zm9v")
      },
      test("RFC 4648 vector: 'foob'") {
        assertTrue(Base64Url.encode("foob".getBytes("UTF-8")) == "Zm9vYg")
      },
      test("RFC 4648 vector: 'fooba'") {
        assertTrue(Base64Url.encode("fooba".getBytes("UTF-8")) == "Zm9vYmE")
      },
      test("RFC 4648 vector: 'foobar'") {
        assertTrue(Base64Url.encode("foobar".getBytes("UTF-8")) == "Zm9vYmFy")
      },
      test("URL-safe alphabet: bytes that would produce '+' and '/' in standard base64 produce '-' and '_'") {
        val input = Array(0xfb.toByte, 0xff.toByte, 0xfe.toByte)
        assertTrue(Base64Url.encode(input) == "-__-")
      },
      test("single zero byte encodes correctly") {
        assertTrue(Base64Url.encode(Array(0x00.toByte)) == "AA")
      }
    ),
    suite("decode")(
      test("empty string decodes to empty array") {
        assertTrue(Base64Url.decode("").map(_.toSeq) == Right(Array.emptyByteArray.toSeq))
      },
      test("RFC 4648 vector: 'Zg' decodes to 'f'") {
        assertTrue(Base64Url.decode("Zg").map(_.toSeq) == Right("f".getBytes("UTF-8").toSeq))
      },
      test("RFC 4648 vector: 'Zm8' decodes to 'fo'") {
        assertTrue(Base64Url.decode("Zm8").map(_.toSeq) == Right("fo".getBytes("UTF-8").toSeq))
      },
      test("RFC 4648 vector: 'Zm9v' decodes to 'foo'") {
        assertTrue(Base64Url.decode("Zm9v").map(_.toSeq) == Right("foo".getBytes("UTF-8").toSeq))
      },
      test("RFC 4648 vector: 'Zm9vYg' decodes to 'foob'") {
        assertTrue(Base64Url.decode("Zm9vYg").map(_.toSeq) == Right("foob".getBytes("UTF-8").toSeq))
      },
      test("RFC 4648 vector: 'Zm9vYmE' decodes to 'fooba'") {
        assertTrue(Base64Url.decode("Zm9vYmE").map(_.toSeq) == Right("fooba".getBytes("UTF-8").toSeq))
      },
      test("RFC 4648 vector: 'Zm9vYmFy' decodes to 'foobar'") {
        assertTrue(Base64Url.decode("Zm9vYmFy").map(_.toSeq) == Right("foobar".getBytes("UTF-8").toSeq))
      },
      test("URL-safe chars '-' and '_' are decoded correctly") {
        assertTrue(Base64Url.decode("-__-").map(_.toSeq) == Right(Array(0xfb.toByte, 0xff.toByte, 0xfe.toByte).toSeq))
      },
      test("padding character '=' returns Left") {
        assertTrue(Base64Url.decode("YQ==").isLeft)
      },
      test("single '=' character returns Left") {
        assertTrue(Base64Url.decode("=").isLeft)
      },
      test("standard base64 '+' character (not in base64url) returns Left") {
        assertTrue(Base64Url.decode("ab+d").isLeft)
      },
      test("standard base64 '/' character (not in base64url) returns Left") {
        assertTrue(Base64Url.decode("ab/d").isLeft)
      },
      test("non-ASCII character returns Left") {
        assertTrue(Base64Url.decode("abc!").isLeft)
      },
      test("length mod 4 == 1 returns Left") {
        assertTrue(Base64Url.decode("a").isLeft)
      },
      test("length mod 4 == 1 with multiple chars returns Left") {
        assertTrue(Base64Url.decode("aaaaa").isLeft)
      },
      test("trailing bits non-zero for 1-byte tail returns Left (invalid trailing base64url bits)") {
        // 1-byte input encodes to 2 chars where second char's low 4 bits must be zero.
        // "AB" has c1=1 (0b00001), low 4 bits =0001 !=0 => invalid
        assertTrue(Base64Url.decode("AB").isLeft)
      },
      test("trailing bits non-zero for 2-byte tail returns Left (invalid trailing base64url bits)") {
        // 2-byte input encodes to 3 chars where third char's low 2 bits must be zero.
        // "ABC" has c2=2 (0b000010), low 2 bits =10 !=0 => invalid
        assertTrue(Base64Url.decode("ABC").isLeft)
      },
      test("valid trailing bits for 2-char tail passes (AA is valid)") {
        assertTrue(Base64Url.decode("AA").isRight)
      },
      test("valid trailing bits for 3-char tail passes (AAA is valid for 2 bytes of zeros)") {
        // "AAA" decodes to 2 bytes of zeros (since AAA -> c0=0,c1=0,c2=0, low bits zero)
        assertTrue(Base64Url.decode("AAA").map(_.toSeq) == Right(Array(0x00.toByte, 0x00.toByte).toSeq))
      }
    ),
    suite("round-trip")(
      test("encode then decode returns original bytes for 1-byte input") {
        val bytes = Array(0x42.toByte)
        assertTrue(Base64Url.decode(Base64Url.encode(bytes)).map(_.toSeq) == Right(bytes.toSeq))
      },
      test("encode then decode returns original bytes for 2-byte input") {
        val bytes = Array(0x01.toByte, 0x02.toByte)
        assertTrue(Base64Url.decode(Base64Url.encode(bytes)).map(_.toSeq) == Right(bytes.toSeq))
      },
      test("encode then decode returns original bytes for 3-byte input") {
        val bytes = Array(0x01.toByte, 0x02.toByte, 0x03.toByte)
        assertTrue(Base64Url.decode(Base64Url.encode(bytes)).map(_.toSeq) == Right(bytes.toSeq))
      },
      test("encode then decode returns original bytes for 4-byte input") {
        val bytes = Array(0xde.toByte, 0xad.toByte, 0xbe.toByte, 0xef.toByte)
        assertTrue(Base64Url.decode(Base64Url.encode(bytes)).map(_.toSeq) == Right(bytes.toSeq))
      },
      test("encode then decode returns original for all-zero bytes") {
        val bytes = Array.fill(9)(0x00.toByte)
        assertTrue(Base64Url.decode(Base64Url.encode(bytes)).map(_.toSeq) == Right(bytes.toSeq))
      },
      test("encode then decode returns original for all-ones bytes") {
        val bytes = Array.fill(7)(0xff.toByte)
        assertTrue(Base64Url.decode(Base64Url.encode(bytes)).map(_.toSeq) == Right(bytes.toSeq))
      },
      test("encode then decode returns original for UTF-8 text") {
        val bytes = "Hello, World!".getBytes("UTF-8")
        assertTrue(Base64Url.decode(Base64Url.encode(bytes)).map(_.toSeq) == Right(bytes.toSeq))
      }
    )
  )
}
