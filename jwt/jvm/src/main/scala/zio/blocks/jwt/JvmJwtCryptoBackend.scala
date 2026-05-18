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

import java.security.{KeyFactory, MessageDigest, PrivateKey, PublicKey, Signature}
import java.security.spec.{MGF1ParameterSpec, PKCS8EncodedKeySpec, PSSParameterSpec, X509EncodedKeySpec}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object JvmJwtCryptoBackend extends JwtCryptoBackend {
  def install(): Unit = JwtCrypto.backend = this

  val supportedAlgorithms: Set[Algorithm] = Algorithm.all.toSet

  def sign(data: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Array[Byte]] =
    withCrypto {
      alg match {
        case Algorithm.HS256 => signWithMac(data, key, "HmacSHA256")
        case Algorithm.HS384 => signWithMac(data, key, "HmacSHA384")
        case Algorithm.HS512 => signWithMac(data, key, "HmacSHA512")
        case Algorithm.RS256 => signWithSignature(data, key, "SHA256withRSA", "RSA")
        case Algorithm.RS384 => signWithSignature(data, key, "SHA384withRSA", "RSA")
        case Algorithm.RS512 => signWithSignature(data, key, "SHA512withRSA", "RSA")
        case Algorithm.PS256 => signWithPss(data, key, "SHA256withRSA/PSS", "SHA-256", MGF1ParameterSpec.SHA256, 32)
        case Algorithm.PS384 => signWithPss(data, key, "SHA384withRSA/PSS", "SHA-384", MGF1ParameterSpec.SHA384, 48)
        case Algorithm.PS512 => signWithPss(data, key, "SHA512withRSA/PSS", "SHA-512", MGF1ParameterSpec.SHA512, 64)
        case Algorithm.ES256 => derToP1363(signWithSignature(data, key, "SHA256withECDSA", "EC"), 32)
        case Algorithm.ES384 => derToP1363(signWithSignature(data, key, "SHA384withECDSA", "EC"), 48)
        case Algorithm.ES512 => derToP1363(signWithSignature(data, key, "SHA512withECDSA", "EC"), 66)
        case Algorithm.EdDSA => signWithSignature(data, key, "Ed25519", "EdDSA")
      }
    }

  def verify(data: Array[Byte], signature: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Boolean] =
    withCrypto {
      alg match {
        case Algorithm.HS256 => verifyWithMac(data, signature, key, "HmacSHA256")
        case Algorithm.HS384 => verifyWithMac(data, signature, key, "HmacSHA384")
        case Algorithm.HS512 => verifyWithMac(data, signature, key, "HmacSHA512")
        case Algorithm.RS256 => verifyWithSignature(data, signature, key, "SHA256withRSA", "RSA")
        case Algorithm.RS384 => verifyWithSignature(data, signature, key, "SHA384withRSA", "RSA")
        case Algorithm.RS512 => verifyWithSignature(data, signature, key, "SHA512withRSA", "RSA")
        case Algorithm.PS256 => verifyWithPss(data, signature, key, "SHA256withRSA/PSS", "SHA-256", MGF1ParameterSpec.SHA256, 32)
        case Algorithm.PS384 => verifyWithPss(data, signature, key, "SHA384withRSA/PSS", "SHA-384", MGF1ParameterSpec.SHA384, 48)
        case Algorithm.PS512 => verifyWithPss(data, signature, key, "SHA512withRSA/PSS", "SHA-512", MGF1ParameterSpec.SHA512, 64)
        case Algorithm.ES256 => verifyWithSignature(data, p1363ToDer(signature, 32), key, "SHA256withECDSA", "EC")
        case Algorithm.ES384 => verifyWithSignature(data, p1363ToDer(signature, 48), key, "SHA384withECDSA", "EC")
        case Algorithm.ES512 => verifyWithSignature(data, p1363ToDer(signature, 66), key, "SHA512withECDSA", "EC")
        case Algorithm.EdDSA => verifyWithSignature(data, signature, key, "Ed25519", "EdDSA")
      }
    }

  private def signWithMac(data: Array[Byte], key: Array[Byte], algorithm: String): Array[Byte] = {
    val mac = Mac.getInstance(algorithm)
    mac.init(new SecretKeySpec(key, algorithm))
    mac.doFinal(data)
  }

  private def verifyWithMac(data: Array[Byte], signature: Array[Byte], key: Array[Byte], algorithm: String): Boolean =
    MessageDigest.isEqual(signWithMac(data, key, algorithm), signature)

  private def signWithSignature(data: Array[Byte], key: Array[Byte], algorithm: String, keyAlgorithm: String): Array[Byte] = {
    val signer = Signature.getInstance(algorithm)
    signer.initSign(loadPrivateKey(key, keyAlgorithm))
    signer.update(data)
    signer.sign()
  }

  private def verifyWithSignature(
    data: Array[Byte],
    signature: Array[Byte],
    key: Array[Byte],
    algorithm: String,
    keyAlgorithm: String
  ): Boolean = {
    val verifier = Signature.getInstance(algorithm)
    verifier.initVerify(loadPublicKey(key, keyAlgorithm))
    verifier.update(data)
    verifier.verify(signature)
  }

  private def signWithPss(
    data: Array[Byte],
    key: Array[Byte],
    preferredAlgorithm: String,
    digestAlgorithm: String,
    mgf1Algorithm: MGF1ParameterSpec,
    saltLength: Int
  ): Array[Byte] = {
    val signer = pssSignature(preferredAlgorithm, digestAlgorithm, mgf1Algorithm, saltLength)
    signer.initSign(loadPrivateKey(key, "RSA"))
    signer.update(data)
    signer.sign()
  }

  private def verifyWithPss(
    data: Array[Byte],
    signature: Array[Byte],
    key: Array[Byte],
    preferredAlgorithm: String,
    digestAlgorithm: String,
    mgf1Algorithm: MGF1ParameterSpec,
    saltLength: Int
  ): Boolean = {
    val verifier = pssSignature(preferredAlgorithm, digestAlgorithm, mgf1Algorithm, saltLength)
    verifier.initVerify(loadPublicKey(key, "RSA"))
    verifier.update(data)
    verifier.verify(signature)
  }

  private def pssSignature(
    preferredAlgorithm: String,
    digestAlgorithm: String,
    mgf1Algorithm: MGF1ParameterSpec,
    saltLength: Int
  ): Signature =
    try Signature.getInstance(preferredAlgorithm)
    catch {
      case _: Exception =>
        val signature = Signature.getInstance("RSASSA-PSS")
        signature.setParameter(new PSSParameterSpec(digestAlgorithm, "MGF1", mgf1Algorithm, saltLength, 1))
        signature
    }

  private def loadPrivateKey(key: Array[Byte], algorithm: String): PrivateKey =
    KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(key))

  private def loadPublicKey(key: Array[Byte], algorithm: String): PublicKey =
    KeyFactory.getInstance(algorithm).generatePublic(new X509EncodedKeySpec(key))

  private def withCrypto[A](thunk: => A): Either[JwtError, A] =
    try Right(thunk)
    catch {
      case e: Exception => Left(JwtError.InvalidToken(Option(e.getMessage).getOrElse(e.getClass.getName)))
    }

  private def derToP1363(der: Array[Byte], componentSize: Int): Array[Byte] = {
    if (der.length < 8 || der(0) != 0x30.toByte) throw new IllegalArgumentException("invalid DER ECDSA signature")

    var index = 1
    val (sequenceLength, sequenceLengthBytes) = readDerLength(der, index)
    index += sequenceLengthBytes
    val sequenceEnd = index + sequenceLength

    val (r, afterR) = readDerInteger(der, index)
    val (s, afterS) = readDerInteger(der, afterR)

    if (afterS != sequenceEnd || sequenceEnd != der.length) throw new IllegalArgumentException("invalid DER ECDSA signature length")

    val result = new Array[Byte](componentSize * 2)
    val normalizedR = normalizeP1363Component(r, componentSize)
    val normalizedS = normalizeP1363Component(s, componentSize)

    java.lang.System.arraycopy(normalizedR, 0, result, 0, componentSize)
    java.lang.System.arraycopy(normalizedS, 0, result, componentSize, componentSize)
    result
  }

  private def p1363ToDer(p1363: Array[Byte], componentSize: Int): Array[Byte] = {
    if (p1363.length != componentSize * 2) throw new IllegalArgumentException("invalid P1363 ECDSA signature length")

    val r = new Array[Byte](componentSize)
    val s = new Array[Byte](componentSize)
    java.lang.System.arraycopy(p1363, 0, r, 0, componentSize)
    java.lang.System.arraycopy(p1363, componentSize, s, 0, componentSize)

    val encodedR = encodeDerInteger(r)
    val encodedS = encodeDerInteger(s)
    val contentLength = encodedR.length + encodedS.length
    val lengthBytes = encodeDerLength(contentLength)
    val result = new Array[Byte](1 + lengthBytes.length + contentLength)

    result(0) = 0x30.toByte
    java.lang.System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length)
    java.lang.System.arraycopy(encodedR, 0, result, 1 + lengthBytes.length, encodedR.length)
    java.lang.System.arraycopy(encodedS, 0, result, 1 + lengthBytes.length + encodedR.length, encodedS.length)
    result
  }

  private def readDerInteger(input: Array[Byte], offset: Int): (Array[Byte], Int) = {
    if (offset >= input.length || input(offset) != 0x02.toByte) throw new IllegalArgumentException("invalid DER integer tag")

    val (length, lengthBytes) = readDerLength(input, offset + 1)
    val start = offset + 1 + lengthBytes
    val end = start + length

    if (length <= 0 || end > input.length) throw new IllegalArgumentException("invalid DER integer length")

    val value = new Array[Byte](length)
    java.lang.System.arraycopy(input, start, value, 0, length)
    (value, end)
  }

  private def readDerLength(input: Array[Byte], offset: Int): (Int, Int) = {
    if (offset >= input.length) throw new IllegalArgumentException("missing DER length")

    val first = input(offset) & 0xff
    if ((first & 0x80) == 0) (first, 1)
    else {
      val byteCount = first & 0x7f
      if (byteCount == 0 || byteCount > 4 || offset + byteCount >= input.length) throw new IllegalArgumentException("invalid DER length encoding")

      var length = 0
      var index = 0
      while (index < byteCount) {
        length = (length << 8) | (input(offset + 1 + index) & 0xff)
        index += 1
      }
      (length, byteCount + 1)
    }
  }

  private def normalizeP1363Component(component: Array[Byte], componentSize: Int): Array[Byte] = {
    val magnitude = trimLeadingZeros(component)
    val result = new Array[Byte](componentSize)
    val copySource = math.max(0, magnitude.length - componentSize)
    val copyLength = math.min(magnitude.length, componentSize)
    java.lang.System.arraycopy(magnitude, copySource, result, componentSize - copyLength, copyLength)
    result
  }

  private def encodeDerInteger(component: Array[Byte]): Array[Byte] = {
    val magnitude = trimLeadingZeros(component)
    val positiveMagnitude =
      if (magnitude.isEmpty) Array(0.toByte)
      else if ((magnitude(0) & 0x80) != 0) {
        val prefixed = new Array[Byte](magnitude.length + 1)
        java.lang.System.arraycopy(magnitude, 0, prefixed, 1, magnitude.length)
        prefixed
      } else magnitude

    val lengthBytes = encodeDerLength(positiveMagnitude.length)
    val result = new Array[Byte](1 + lengthBytes.length + positiveMagnitude.length)
    result(0) = 0x02.toByte
    java.lang.System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length)
    java.lang.System.arraycopy(positiveMagnitude, 0, result, 1 + lengthBytes.length, positiveMagnitude.length)
    result
  }

  private def encodeDerLength(length: Int): Array[Byte] = {
    if (length < 0) throw new IllegalArgumentException("negative DER length")
    else if (length < 128) Array(length.toByte)
    else {
      var value = length
      var bytesNeeded = 0
      while (value > 0) {
        bytesNeeded += 1
        value = value >>> 8
      }

      val result = new Array[Byte](bytesNeeded + 1)
      result(0) = (0x80 | bytesNeeded).toByte

      var index = bytesNeeded
      var remaining = length
      while (index > 0) {
        result(index) = (remaining & 0xff).toByte
        remaining = remaining >>> 8
        index -= 1
      }
      result
    }
  }

  private def trimLeadingZeros(bytes: Array[Byte]): Array[Byte] = {
    var index = 0
    while (index < bytes.length && bytes(index) == 0.toByte) index += 1

    val size = bytes.length - index
    if (size <= 0) Array.emptyByteArray
    else {
      val result = new Array[Byte](size)
      java.lang.System.arraycopy(bytes, index, result, 0, size)
      result
    }
  }
}

private[jwt] object JvmJwtInit {
  def install(): Unit = JvmJwtCryptoBackend.install()
}
