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
import java.security.interfaces.{ECKey, RSAPrivateKey, RSAPublicKey}
import java.security.spec.{MGF1ParameterSpec, PKCS8EncodedKeySpec, PSSParameterSpec, X509EncodedKeySpec}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object JvmJwtCryptoBackend extends JwtCryptoBackend {
  def install(): Unit = JwtCrypto.installBackend(this)

  val supportedAlgorithms: Set[Algorithm] = Algorithm.all.toSet

  def sign(data: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Array[Byte]] =
    withCrypto {
      alg match {
        case Algorithm.HS256 => signWithMac(data, key, "HmacSHA256", 32)
        case Algorithm.HS384 => signWithMac(data, key, "HmacSHA384", 48)
        case Algorithm.HS512 => signWithMac(data, key, "HmacSHA512", 64)
        case Algorithm.RS256 => signWithSignature(data, key, "SHA256withRSA", "RSA", Some(2048), alg)
        case Algorithm.RS384 => signWithSignature(data, key, "SHA384withRSA", "RSA", Some(2048), alg)
        case Algorithm.RS512 => signWithSignature(data, key, "SHA512withRSA", "RSA", Some(2048), alg)
        case Algorithm.PS256 =>
          signWithPss(data, key, "SHA256withRSA/PSS", "SHA-256", MGF1ParameterSpec.SHA256, 32, alg)
        case Algorithm.PS384 =>
          signWithPss(data, key, "SHA384withRSA/PSS", "SHA-384", MGF1ParameterSpec.SHA384, 48, alg)
        case Algorithm.PS512 =>
          signWithPss(data, key, "SHA512withRSA/PSS", "SHA-512", MGF1ParameterSpec.SHA512, 64, alg)
        case Algorithm.ES256 =>
          validateEcPrivateKey(key, 256, alg)
          EcdsaDer.derToP1363(signWithSignature(data, key, "SHA256withECDSA", "EC", None, alg), 32)
        case Algorithm.ES384 =>
          validateEcPrivateKey(key, 384, alg)
          EcdsaDer.derToP1363(signWithSignature(data, key, "SHA384withECDSA", "EC", None, alg), 48)
        case Algorithm.ES512 =>
          validateEcPrivateKey(key, 521, alg)
          EcdsaDer.derToP1363(signWithSignature(data, key, "SHA512withECDSA", "EC", None, alg), 66)
        case Algorithm.EdDSA => signWithSignature(data, key, "Ed25519", "EdDSA", None, alg)
      }
    }

  def verify(data: Array[Byte], signature: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Boolean] =
    withCrypto {
      alg match {
        case Algorithm.HS256 => verifyWithMac(data, signature, key, "HmacSHA256", 32)
        case Algorithm.HS384 => verifyWithMac(data, signature, key, "HmacSHA384", 48)
        case Algorithm.HS512 => verifyWithMac(data, signature, key, "HmacSHA512", 64)
        case Algorithm.RS256 => verifyWithSignature(data, signature, key, "SHA256withRSA", "RSA", Some(2048), alg)
        case Algorithm.RS384 => verifyWithSignature(data, signature, key, "SHA384withRSA", "RSA", Some(2048), alg)
        case Algorithm.RS512 => verifyWithSignature(data, signature, key, "SHA512withRSA", "RSA", Some(2048), alg)
        case Algorithm.PS256 =>
          verifyWithPss(data, signature, key, "SHA256withRSA/PSS", "SHA-256", MGF1ParameterSpec.SHA256, 32, alg)
        case Algorithm.PS384 =>
          verifyWithPss(data, signature, key, "SHA384withRSA/PSS", "SHA-384", MGF1ParameterSpec.SHA384, 48, alg)
        case Algorithm.PS512 =>
          verifyWithPss(data, signature, key, "SHA512withRSA/PSS", "SHA-512", MGF1ParameterSpec.SHA512, 64, alg)
        case Algorithm.ES256 =>
          validateEcPublicKey(key, 256, alg)
          verifyWithSignature(data, EcdsaDer.p1363ToDer(signature, 32), key, "SHA256withECDSA", "EC", None, alg)
        case Algorithm.ES384 =>
          validateEcPublicKey(key, 384, alg)
          verifyWithSignature(data, EcdsaDer.p1363ToDer(signature, 48), key, "SHA384withECDSA", "EC", None, alg)
        case Algorithm.ES512 =>
          validateEcPublicKey(key, 521, alg)
          verifyWithSignature(data, EcdsaDer.p1363ToDer(signature, 66), key, "SHA512withECDSA", "EC", None, alg)
        case Algorithm.EdDSA => verifyWithSignature(data, signature, key, "Ed25519", "EdDSA", None, alg)
      }
    }

  private def signWithMac(data: Array[Byte], key: Array[Byte], algorithm: String, minBytes: Int): Array[Byte] = {
    validateHmacKeyOrThrow(key, minBytes, algorithm)
    val mac = Mac.getInstance(algorithm)
    mac.init(new SecretKeySpec(key, algorithm))
    mac.doFinal(data)
  }

  private def verifyWithMac(
    data: Array[Byte],
    signature: Array[Byte],
    key: Array[Byte],
    algorithm: String,
    minBytes: Int
  ): Boolean =
    MessageDigest.isEqual(signWithMac(data, key, algorithm, minBytes), signature)

  private def signWithSignature(
    data: Array[Byte],
    key: Array[Byte],
    algorithm: String,
    keyAlgorithm: String,
    minRsaBits: Option[Int],
    jwtAlg: Algorithm
  ): Array[Byte] = {
    val privateKey = loadPrivateKey(key, keyAlgorithm)
    minRsaBits.foreach(bits => validateRsaKey(privateKey, bits, jwtAlg))
    val signer = Signature.getInstance(algorithm)
    signer.initSign(privateKey)
    signer.update(data)
    signer.sign()
  }

  private def verifyWithSignature(
    data: Array[Byte],
    signature: Array[Byte],
    key: Array[Byte],
    algorithm: String,
    keyAlgorithm: String,
    minRsaBits: Option[Int],
    jwtAlg: Algorithm
  ): Boolean = {
    val publicKey = loadPublicKey(key, keyAlgorithm)
    minRsaBits.foreach(bits => validateRsaKey(publicKey, bits, jwtAlg))
    val verifier = Signature.getInstance(algorithm)
    verifier.initVerify(publicKey)
    verifier.update(data)
    verifier.verify(signature)
  }

  private def signWithPss(
    data: Array[Byte],
    key: Array[Byte],
    preferredAlgorithm: String,
    digestAlgorithm: String,
    mgf1Algorithm: MGF1ParameterSpec,
    saltLength: Int,
    jwtAlg: Algorithm
  ): Array[Byte] = {
    val privateKey = loadPrivateKey(key, "RSA")
    validateRsaKey(privateKey, 2048, jwtAlg)
    val signer = pssSignature(preferredAlgorithm, digestAlgorithm, mgf1Algorithm, saltLength)
    signer.initSign(privateKey)
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
    saltLength: Int,
    jwtAlg: Algorithm
  ): Boolean = {
    val publicKey = loadPublicKey(key, "RSA")
    validateRsaKey(publicKey, 2048, jwtAlg)
    val verifier = pssSignature(preferredAlgorithm, digestAlgorithm, mgf1Algorithm, saltLength)
    verifier.initVerify(publicKey)
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
      case e: java.security.NoSuchAlgorithmException =>
        Left(JwtError.UnsupportedAlgorithm(Option(e.getMessage).getOrElse(e.getClass.getName)))
      case e: java.security.NoSuchProviderException =>
        Left(JwtError.UnsupportedAlgorithm(Option(e.getMessage).getOrElse(e.getClass.getName)))
      case e: java.security.spec.InvalidKeySpecException =>
        Left(JwtError.InvalidKey(Option(e.getMessage).getOrElse(e.getClass.getName)))
      case e: java.security.InvalidKeyException =>
        Left(JwtError.InvalidKey(Option(e.getMessage).getOrElse(e.getClass.getName)))
      case e: java.security.SignatureException =>
        Left(JwtError.InvalidSignature(Option(e.getMessage).getOrElse("invalid signature")))
      case e: IllegalArgumentException =>
        val msg   = Option(e.getMessage).getOrElse(e.getClass.getName)
        val lower = msg.toLowerCase
        if (lower.contains("key") || lower.contains("curve") || lower.contains("rsa") || lower.contains("hmac"))
          Left(JwtError.InvalidKey(msg))
        else if (
          lower.contains("der") || lower.contains("p1363") || lower
            .contains("ecdsa") || lower.contains("p-521") || lower.contains("signature")
        )
          Left(JwtError.InvalidSignature(msg))
        else Left(JwtError.InvalidToken(msg))
      case e: Exception =>
        val msg   = Option(e.getMessage).getOrElse(e.getClass.getName)
        val lower = msg.toLowerCase
        if (lower.contains("unsupported")) Left(JwtError.UnsupportedAlgorithm(msg))
        else if (lower.contains("key")) Left(JwtError.InvalidKey(msg))
        else if (lower.contains("der") || lower.contains("p1363") || lower.contains("signature"))
          Left(JwtError.InvalidSignature(msg))
        else Left(JwtError.InvalidToken(msg))
    }

  private def validateHmacKeyOrThrow(key: Array[Byte], minBytes: Int, algorithm: String): Unit =
    if (key.length < minBytes)
      throw new IllegalArgumentException(
        s"HMAC key too short: ${key.length} bytes, minimum $minBytes bytes required for $algorithm"
      )

  private def validateRsaKey(key: java.security.Key, minBits: Int, alg: Algorithm): Unit = {
    val bits = key match {
      case rsa: RSAPublicKey  => rsa.getModulus.bitLength()
      case rsa: RSAPrivateKey => rsa.getModulus.bitLength()
      case _                  => return // cannot inspect, skip
    }
    if (bits < minBits)
      throw new IllegalArgumentException(s"${alg.name} RSA key too weak: $bits bits, minimum $minBits bits required")
  }

  private def validateEcPrivateKey(keyBytes: Array[Byte], expectedFieldBits: Int, alg: Algorithm): Unit = {
    val key = loadPrivateKey(keyBytes, "EC")
    validateEcFieldSize(key, expectedFieldBits, alg)
  }

  private def validateEcPublicKey(keyBytes: Array[Byte], expectedFieldBits: Int, alg: Algorithm): Unit = {
    val key = loadPublicKey(keyBytes, "EC")
    validateEcFieldSize(key, expectedFieldBits, alg)
  }

  private def validateEcFieldSize(key: java.security.Key, expectedFieldBits: Int, alg: Algorithm): Unit =
    key match {
      case ec: ECKey =>
        val fieldSize = ec.getParams.getCurve.getField.getFieldSize
        if (fieldSize != expectedFieldBits)
          throw new IllegalArgumentException(
            s"${alg.name} EC key curve mismatch: field size $fieldSize bits, expected $expectedFieldBits bits"
          )
      case _ => ()
    }
}

private[jwt] object JvmJwtInit {
  def install(): Unit = JvmJwtCryptoBackend.install()
}
