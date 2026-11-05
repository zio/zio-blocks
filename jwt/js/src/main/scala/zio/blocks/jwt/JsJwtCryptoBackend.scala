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
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.typedarray.{Int8Array, Uint8Array}
import scala.util.control.NonFatal

private[jwt] object JsCryptoCapability {
  private def defaultProbe(): Boolean =
    try {
      if (js.isUndefined(g.require) || g.require == null) false
      else {
        val c = g.require("crypto")
        !js.isUndefined(c) && c != null && !js.isUndefined(c.createSign) && !js.isUndefined(c.createVerify)
      }
    } catch {
      case _: js.JavaScriptException => false
      case NonFatal(_)               => false
    }

  private val probeRef =
    new java.util.concurrent.atomic.AtomicReference[() => Boolean](() => defaultProbe())

  def isAvailable: Boolean = probeRef.get()()

  def setForTest(value: Option[Boolean]): Unit =
    probeRef.set(value match {
      case Some(v) => () => v
      case None    => () => defaultProbe()
    })
}

/**
 * A [[JwtCryptoBackend]] for Scala.js. It supports `HS*` with the built-in pure
 * Scala implementation, and `RS*` and `ES*` through the `crypto` module of
 * Node.js. `PS*` and `EdDSA` are not supported. Call `install()` once at
 * startup so that [[Jwt]] uses it.
 */
object JsJwtCryptoBackend extends JwtCryptoBackend {

  /**
   * Makes this backend the one [[JwtCrypto]] uses, replacing the backend
   * installed before.
   */
  def install(): Unit = JwtCrypto.installBackend(this)

  /**
   * `HS256`, `HS384`, `HS512`, `RS256`, `RS384`, `RS512`, `ES256`, `ES384` and
   * `ES512`.
   */
  val supportedAlgorithms: Set[Algorithm] =
    Set(
      Algorithm.HS256,
      Algorithm.HS384,
      Algorithm.HS512,
      Algorithm.RS256,
      Algorithm.RS384,
      Algorithm.RS512,
      Algorithm.ES256,
      Algorithm.ES384,
      Algorithm.ES512
    )

  /**
   * Signs `data` with `key` and returns the raw signature bytes. `HS*` take the
   * secret bytes (at least 32, 48 or 64 for `HS256`, `HS384`, `HS512`). `RS*`
   * take a PKCS#8 DER RSA private key, and `ES*` a PKCS#8 DER EC private key;
   * `ES*` return the signature as fixed-width `R || S` (64, 96 or 132 bytes).
   * Unlike the JVM backend, it does not check RSA key size, EC curve or that
   * the key's type fits the algorithm: an EC key signs an `RS*` token with
   * ECDSA, and an `ES*` signature from a key on a larger curve than the
   * algorithm's fails with `InvalidSignature`.
   *
   * It returns `UnsupportedAlgorithm` for `PS*` and `EdDSA`, and for `RS*` and
   * `ES*` when the Node.js `crypto` module is not available. A short `HS*` key
   * gives `InvalidKey`; for a key Node.js cannot read, the case (`InvalidKey`
   * or `InvalidToken`) depends on the Node.js error message.
   */
  def sign(data: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Array[Byte]] = alg match {
    case Algorithm.HS256 | Algorithm.HS384 | Algorithm.HS512 =>
      SharedJwtCryptoBackend.sign(data, key, alg)
    case Algorithm.RS256 | Algorithm.RS384 | Algorithm.RS512 =>
      if (!JsCryptoCapability.isAvailable) Left(JwtError.UnsupportedAlgorithm(alg.name))
      else withNodeCrypto(signWithAsymmetric(data, key, nodeRsaAlgorithm(alg)))
    case Algorithm.ES256 | Algorithm.ES384 | Algorithm.ES512 =>
      if (!JsCryptoCapability.isAvailable) Left(JwtError.UnsupportedAlgorithm(alg.name))
      else
        withNodeCrypto(EcdsaDer.derToP1363(signWithAsymmetric(data, key, nodeEcAlgorithm(alg)), ecComponentSize(alg)))
    case _ =>
      Left(JwtError.UnsupportedAlgorithm(alg.name))
  }

  /**
   * Checks `signature` over `data` against `key`. The key is a raw secret for
   * `HS*` and an X.509 SubjectPublicKeyInfo DER public key for `RS*` and `ES*`.
   *
   * It returns `Right(false)` for a signature that does not match, and
   * `InvalidSignature` for an `ES*` signature of the wrong length. As in
   * `sign`, the key's size, curve and type are not checked, and the algorithm
   * errors are the same; a public key Node.js cannot read gives `InvalidKey`.
   */
  def verify(data: Array[Byte], signature: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Boolean] =
    alg match {
      case Algorithm.HS256 | Algorithm.HS384 | Algorithm.HS512 =>
        SharedJwtCryptoBackend.verify(data, signature, key, alg)
      case Algorithm.RS256 | Algorithm.RS384 | Algorithm.RS512 =>
        if (!JsCryptoCapability.isAvailable) Left(JwtError.UnsupportedAlgorithm(alg.name))
        else withNodeCrypto(verifyWithAsymmetric(data, signature, key, nodeRsaAlgorithm(alg)))
      case Algorithm.ES256 | Algorithm.ES384 | Algorithm.ES512 =>
        if (!JsCryptoCapability.isAvailable) Left(JwtError.UnsupportedAlgorithm(alg.name))
        else
          withNodeCrypto(
            verifyWithAsymmetric(data, EcdsaDer.p1363ToDer(signature, ecComponentSize(alg)), key, nodeEcAlgorithm(alg))
          )
      case _ =>
        Left(JwtError.UnsupportedAlgorithm(alg.name))
    }

  private def nodeCrypto: js.Dynamic = g.require("crypto")

  private def signWithAsymmetric(data: Array[Byte], key: Array[Byte], algorithm: String): Array[Byte] = {
    val crypto = nodeCrypto
    val signer = crypto.createSign(algorithm)
    signer.update(bytesToBuffer(data))
    bufferToBytes(signer.sign(privateKeyObject(key)))
  }

  private def verifyWithAsymmetric(
    data: Array[Byte],
    signature: Array[Byte],
    key: Array[Byte],
    algorithm: String
  ): Boolean = {
    val crypto   = nodeCrypto
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
    val uint8 = new Uint8Array(bytes.length)
    var index = 0
    while (index < bytes.length) {
      uint8(index) = (bytes(index) & 0xff).toShort
      index += 1
    }
    g.Buffer.from(uint8.buffer.asInstanceOf[js.typedarray.ArrayBuffer], uint8.byteOffset, uint8.byteLength)
  }

  private def bufferToBytes(buffer: js.Dynamic): Array[Byte] = {
    val offset = buffer.byteOffset.asInstanceOf[Int]
    val length = buffer.length.asInstanceOf[Int]
    val buf    = buffer.buffer.asInstanceOf[js.typedarray.ArrayBuffer]
    new Int8Array(buf, offset, length).toArray
  }

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
      case e: IllegalArgumentException =>
        val msg   = Option(e.getMessage).getOrElse(e.getClass.getName)
        val lower = msg.toLowerCase
        if (lower.contains("key") || lower.contains("hmac")) Left(JwtError.InvalidKey(msg))
        else if (
          lower.contains("der") || lower.contains("p1363") || lower
            .contains("ecdsa") || lower.contains("p-521") || lower.contains("signature")
        )
          Left(JwtError.InvalidSignature(msg))
        else Left(JwtError.InvalidToken(msg))
      case js.JavaScriptException(err) =>
        val msg   = jsErrorMessage(err)
        val lower = msg.toLowerCase
        if (
          lower.contains("require is not defined") ||
          lower.contains("cannot find module") ||
          lower.contains("crypto") && lower.contains("not defined") ||
          lower.contains("is not a function")
        ) Left(JwtError.UnsupportedAlgorithm(msg))
        else if (lower.contains("unsupported") || lower.contains("not supported"))
          Left(JwtError.UnsupportedAlgorithm(msg))
        else if (lower.contains("key")) Left(JwtError.InvalidKey(msg))
        else if (lower.contains("der") || lower.contains("signature")) Left(JwtError.InvalidSignature(msg))
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

  private def jsErrorMessage(err: Any): String =
    if (err == null || js.isUndefined(err.asInstanceOf[js.Any])) "JavaScript error"
    else err.toString
}

private[jwt] object JsJwtInit {
  def install(): Unit = JsJwtCryptoBackend.install()
}
