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

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{ global => g }
import scala.scalajs.js.typedarray.Int8Array

object JsJwtCryptoBackend extends JwtCryptoBackend {
  def install(): Unit = JwtCrypto.backend = this

  val supportedAlgorithms: Set[Algorithm] =
    Set(Algorithm.HS256, Algorithm.HS384, Algorithm.HS512,
        Algorithm.RS256, Algorithm.RS384, Algorithm.RS512,
        Algorithm.ES256, Algorithm.ES384, Algorithm.ES512)

  private lazy val crypto = g.require("crypto")

  def sign(data: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Array[Byte]] = alg match {
    case Algorithm.HS256 | Algorithm.HS384 | Algorithm.HS512 =>
      SharedJwtCryptoBackend.sign(data, key, alg)
    case Algorithm.RS256 | Algorithm.RS384 | Algorithm.RS512 =>
      withNodeCrypto(signWithAsymmetric(data, key, nodeRsaAlgorithm(alg)))
    case Algorithm.ES256 | Algorithm.ES384 | Algorithm.ES512 =>
      withNodeCrypto(derToP1363(signWithAsymmetric(data, key, nodeEcAlgorithm(alg)), ecComponentSize(alg)))
    case _ =>
      Left(JwtError.UnsupportedAlgorithm(alg.name))
  }

  def verify(data: Array[Byte], signature: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Boolean] = alg match {
    case Algorithm.HS256 | Algorithm.HS384 | Algorithm.HS512 =>
      SharedJwtCryptoBackend.verify(data, signature, key, alg)
    case Algorithm.RS256 | Algorithm.RS384 | Algorithm.RS512 =>
      withNodeCrypto(verifyWithAsymmetric(data, signature, key, nodeRsaAlgorithm(alg)))
    case Algorithm.ES256 | Algorithm.ES384 | Algorithm.ES512 =>
      withNodeCrypto(verifyWithAsymmetric(data, p1363ToDer(signature, ecComponentSize(alg)), key, nodeEcAlgorithm(alg)))
    case _ =>
      Left(JwtError.UnsupportedAlgorithm(alg.name))
  }

  private def signWithAsymmetric(data: Array[Byte], key: Array[Byte], algorithm: String): Array[Byte] = {
    val signer = crypto.createSign(algorithm)
    signer.update(bytesToBuffer(data))
    bufferToBytes(signer.sign(privateKeyObject(key)))
  }

  private def verifyWithAsymmetric(data: Array[Byte], signature: Array[Byte], key: Array[Byte], algorithm: String): Boolean = {
    val verifier = crypto.createVerify(algorithm)
    verifier.update(bytesToBuffer(data))
    verifier.verify(publicKeyObject(key), bytesToBuffer(signature)).asInstanceOf[Boolean]
  }

  private def privateKeyObject(key: Array[Byte]): js.Dynamic =
    js.Dynamic.literal(
      key = bytesToBuffer(key),
      format = "der",
      `type` = "pkcs8"
    )

  private def publicKeyObject(key: Array[Byte]): js.Dynamic =
    js.Dynamic.literal(
      key = bytesToBuffer(key),
      format = "der",
      `type` = "spki"
    )

  private def bytesToBuffer(bytes: Array[Byte]): js.Dynamic = {
    val int8Array = new Int8Array(bytes.length)
    var index     = 0
    while (index < bytes.length) {
      int8Array(index) = bytes(index)
      index += 1
    }
    g.Buffer.from(int8Array.buffer)
  }

  private def bufferToBytes(buffer: js.Dynamic): Array[Byte] =
    new Int8Array(
      buffer.buffer.asInstanceOf[js.typedarray.ArrayBuffer],
      buffer.byteOffset.asInstanceOf[Int],
      buffer.length.asInstanceOf[Int]
    ).toArray

  private def nodeRsaAlgorithm(alg: Algorithm): String = alg match {
    case Algorithm.RS256 => "RSA-SHA256"
    case Algorithm.RS384 => "RSA-SHA384"
    case Algorithm.RS512 => "RSA-SHA512"
    case _               => throw new IllegalArgumentException("unsupported RSA algorithm: " + alg.name)
  }

  private def nodeEcAlgorithm(alg: Algorithm): String = alg match {
    case Algorithm.ES256 => "SHA256"
    case Algorithm.ES384 => "SHA384"
    case Algorithm.ES512 => "SHA512"
    case _               => throw new IllegalArgumentException("unsupported ECDSA algorithm: " + alg.name)
  }

  private def ecComponentSize(alg: Algorithm): Int = alg match {
    case Algorithm.ES256 => 32
    case Algorithm.ES384 => 48
    case Algorithm.ES512 => 66
    case _               => throw new IllegalArgumentException("unsupported ECDSA algorithm: " + alg.name)
  }

  private def withNodeCrypto[A](thunk: => A): Either[JwtError, A] =
    try Right(thunk)
    catch {
      case js.JavaScriptException(err) =>
        Left(JwtError.InvalidToken(jsErrorMessage(err)))
      case e: Exception =>
        Left(JwtError.InvalidToken(Option(e.getMessage).getOrElse(e.getClass.getName)))
    }

  private def jsErrorMessage(err: Any): String =
    if (err == null || js.isUndefined(err.asInstanceOf[js.Any])) "JavaScript error"
    else err.toString

  private def derToP1363(der: Array[Byte], componentSize: Int): Array[Byte] = {
    if (der.length < 8 || der(0) != 0x30.toByte) throw new IllegalArgumentException("invalid DER ECDSA signature")

    var index                                = 1
    val (sequenceLength, sequenceLengthSize) = readDerLength(der, index)
    index += sequenceLengthSize
    val sequenceEnd = index + sequenceLength

    val (r, afterR) = readDerInteger(der, index)
    val (s, afterS) = readDerInteger(der, afterR)

    if (afterS != sequenceEnd || sequenceEnd != der.length) throw new IllegalArgumentException("invalid DER ECDSA signature length")

    val result      = new Array[Byte](componentSize * 2)
    val normalizedR = normalizeP1363Component(r, componentSize)
    val normalizedS = normalizeP1363Component(s, componentSize)

    Array.copy(normalizedR, 0, result, 0, componentSize)
    Array.copy(normalizedS, 0, result, componentSize, componentSize)
    result
  }

  private def p1363ToDer(p1363: Array[Byte], componentSize: Int): Array[Byte] = {
    if (p1363.length != componentSize * 2) throw new IllegalArgumentException("invalid P1363 ECDSA signature length")

    val r = new Array[Byte](componentSize)
    val s = new Array[Byte](componentSize)

    Array.copy(p1363, 0, r, 0, componentSize)
    Array.copy(p1363, componentSize, s, 0, componentSize)

    val encodedR      = encodeDerInteger(r)
    val encodedS      = encodeDerInteger(s)
    val contentLength = encodedR.length + encodedS.length
    val lengthBytes   = encodeDerLength(contentLength)
    val result        = new Array[Byte](1 + lengthBytes.length + contentLength)

    result(0) = 0x30.toByte
    Array.copy(lengthBytes, 0, result, 1, lengthBytes.length)
    Array.copy(encodedR, 0, result, 1 + lengthBytes.length, encodedR.length)
    Array.copy(encodedS, 0, result, 1 + lengthBytes.length + encodedR.length, encodedS.length)
    result
  }

  private def readDerInteger(input: Array[Byte], offset: Int): (Array[Byte], Int) = {
    if (offset >= input.length || input(offset) != 0x02.toByte) throw new IllegalArgumentException("invalid DER integer tag")

    val (length, lengthSize) = readDerLength(input, offset + 1)
    val start                = offset + 1 + lengthSize
    val end                  = start + length

    if (length <= 0 || end > input.length) throw new IllegalArgumentException("invalid DER integer length")

    val value = new Array[Byte](length)
    Array.copy(input, start, value, 0, length)
    (value, end)
  }

  private def readDerLength(input: Array[Byte], offset: Int): (Int, Int) = {
    if (offset >= input.length) throw new IllegalArgumentException("missing DER length")

    val first = input(offset) & 0xff
    if ((first & 0x80) == 0) (first, 1)
    else {
      val byteCount = first & 0x7f
      if (byteCount == 0 || byteCount > 4 || offset + byteCount >= input.length)
        throw new IllegalArgumentException("invalid DER length encoding")

      var length = 0
      var index  = 0
      while (index < byteCount) {
        length = (length << 8) | (input(offset + 1 + index) & 0xff)
        index += 1
      }
      (length, byteCount + 1)
    }
  }

  private def normalizeP1363Component(component: Array[Byte], componentSize: Int): Array[Byte] = {
    val magnitude  = trimLeadingZeros(component)
    if (magnitude.length > componentSize) throw new IllegalArgumentException("DER integer exceeds expected ECDSA component size")
    val result     = new Array[Byte](componentSize)
    val copySource = math.max(0, magnitude.length - componentSize)
    val copyLength = math.min(magnitude.length, componentSize)

    Array.copy(magnitude, copySource, result, componentSize - copyLength, copyLength)
    result
  }

  private def encodeDerInteger(component: Array[Byte]): Array[Byte] = {
    val magnitude = trimLeadingZeros(component)
    val positiveMagnitude =
      if (magnitude.isEmpty) Array(0.toByte)
      else if ((magnitude(0) & 0x80) != 0) {
        val prefixed = new Array[Byte](magnitude.length + 1)
        Array.copy(magnitude, 0, prefixed, 1, magnitude.length)
        prefixed
      } else magnitude

    val lengthBytes = encodeDerLength(positiveMagnitude.length)
    val result      = new Array[Byte](1 + lengthBytes.length + positiveMagnitude.length)

    result(0) = 0x02.toByte
    Array.copy(lengthBytes, 0, result, 1, lengthBytes.length)
    Array.copy(positiveMagnitude, 0, result, 1 + lengthBytes.length, positiveMagnitude.length)
    result
  }

  private def encodeDerLength(length: Int): Array[Byte] =
    if (length < 0) throw new IllegalArgumentException("negative DER length")
    else if (length < 128) Array(length.toByte)
    else {
      var value       = length
      var bytesNeeded = 0
      while (value > 0) {
        bytesNeeded += 1
        value = value >>> 8
      }

      val result = new Array[Byte](bytesNeeded + 1)
      result(0) = (0x80 | bytesNeeded).toByte

      var index     = bytesNeeded
      var remaining = length
      while (index > 0) {
        result(index) = (remaining & 0xff).toByte
        remaining = remaining >>> 8
        index -= 1
      }
      result
    }

  private def trimLeadingZeros(bytes: Array[Byte]): Array[Byte] = {
    var index = 0
    while (index < bytes.length && bytes(index) == 0.toByte) index += 1

    val size = bytes.length - index
    if (size <= 0) Array.emptyByteArray
    else {
      val result = new Array[Byte](size)
      Array.copy(bytes, index, result, 0, size)
      result
    }
  }
}

private[jwt] object JsJwtInit {
  def install(): Unit = JsJwtCryptoBackend.install()
}
