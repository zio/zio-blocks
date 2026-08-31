/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package zio.blocks.jwt

import zio.test._
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.typedarray.Int8Array

object JwtJsSecuritySpec extends ZIOSpecDefault {

  JsJwtCryptoBackend.install()

  private val claims  = JwtClaims(sub = Some("js-security-test"))
  private val hmacKey = "test-secret-key-for-js-spec-32b-pad!!".getBytes("UTF-8") // 32+

  private def withUnavailable[A](body: => A): A = {
    JsCryptoCapability.setForTest(Some(false))
    try body
    finally JsCryptoCapability.setForTest(None)
  }

  private def bufferToBytes(buffer: js.Dynamic): Array[Byte] = {
    val offset = buffer.byteOffset.asInstanceOf[Int]
    val length = buffer.length.asInstanceOf[Int]
    val buf    = buffer.buffer.asInstanceOf[js.typedarray.ArrayBuffer]
    new Int8Array(buf, offset, length).toArray
  }

  private def generateRsaKeyPair(): (Array[Byte], Array[Byte]) = {
    val crypto  = g.require("crypto")
    val pair    = crypto.generateKeyPairSync("rsa", js.Dynamic.literal(modulusLength = 2048))
    val privBuf = pair.privateKey.`export`(js.Dynamic.literal(`type` = "pkcs8", format = "der"))
    val pubBuf  = pair.publicKey.`export`(js.Dynamic.literal(`type` = "spki", format = "der"))
    (bufferToBytes(privBuf), bufferToBytes(pubBuf))
  }

  private def generateEcKeyPair(curve: String): (Array[Byte], Array[Byte]) = {
    val crypto  = g.require("crypto")
    val pair    = crypto.generateKeyPairSync("ec", js.Dynamic.literal(namedCurve = curve))
    val privBuf = pair.privateKey.`export`(js.Dynamic.literal(`type` = "pkcs8", format = "der"))
    val pubBuf  = pair.publicKey.`export`(js.Dynamic.literal(`type` = "spki", format = "der"))
    (bufferToBytes(privBuf), bufferToBytes(pubBuf))
  }

  def spec: Spec[TestEnvironment, Any] = suite("JwtJsSecuritySpec")(
    suite("HMAC key strength (shared validation)")(
      test("HS256 rejects 31-byte key with InvalidKey, accepts 32-byte key") {
        val key31        = Array.fill(31)(0x01.toByte)
        val key32        = Array.fill(32)(0x01.toByte)
        val r31          = JsJwtCryptoBackend.sign("data".getBytes("UTF-8"), key31, Algorithm.HS256)
        val r32          = JsJwtCryptoBackend.sign("data".getBytes("UTF-8"), key32, Algorithm.HS256)
        val isInvalidKey = r31 match {
          case Left(e: JwtError.InvalidKey) => e.reason.contains("HS256"); case _ => false
        }
        assertTrue(r31.isLeft && isInvalidKey && r32.isRight)
      },
      test("HS512 rejects 63-byte key with InvalidKey") {
        val key63        = Array.fill(63)(0x01.toByte)
        val key64        = Array.fill(64)(0x01.toByte)
        val r63          = JsJwtCryptoBackend.sign("data".getBytes("UTF-8"), key63, Algorithm.HS512)
        val r64          = JsJwtCryptoBackend.sign("data".getBytes("UTF-8"), key64, Algorithm.HS512)
        val isInvalidKey = r63 match {
          case Left(e: JwtError.InvalidKey) => e.reason.contains("HS512"); case _ => false
        }
        assertTrue(r63.isLeft && isInvalidKey && r64.isRight)
      }
    ),
    suite("EcdsaDer strict validation shared")(
      test("oversized DER integer is rejected (no truncation)") {
        val r                                              = Array.fill(33)(0x01.toByte) // exceeds 32 for ES256
        val s                                              = Array[Byte](0x01.toByte)
        def encodeInteger(bytes: Array[Byte]): Array[Byte] =
          Array.concat(Array[Byte](0x02.toByte, bytes.length.toByte), bytes)
        val rEnc    = encodeInteger(r)
        val sEnc    = encodeInteger(s)
        val content = Array.concat(rEnc, sEnc)
        val der     = Array.concat(Array[Byte](0x30.toByte, content.length.toByte), content)
        val outcome = try {
          EcdsaDer.derToP1363(der, 32)
          false
        } catch {
          case _: IllegalArgumentException => true
          case _: Exception                => false
        }
        assertTrue(outcome)
      },
      test("p1363ToDer/derToP1363 roundtrip preserves signature") {
        val p1363 = Array.fill(64)(0x42.toByte)
        val der   = EcdsaDer.p1363ToDer(p1363, 32)
        val back  = EcdsaDer.derToP1363(der, 32)
        assertTrue(back.toSeq == p1363.toSeq)
      },
      test("P-521 unused high bits rejected") {
        val bad     = Array.fill(132)(0xff.toByte)
        val outcome = try { EcdsaDer.p1363ToDer(bad, 66); false }
        catch {
          case e: IllegalArgumentException => e.getMessage.toLowerCase.contains("p-521")
          case _: Exception                => false
        }
        assertTrue(outcome)
      },
      test("P-521 valid max scalar roundtrips") {
        val maxComp = Array.fill(66)(0xff.toByte)
        maxComp(0) = 0x01.toByte
        val p1363 = Array.concat(maxComp, maxComp)
        val der   = EcdsaDer.p1363ToDer(p1363, 66)
        val back  = EcdsaDer.derToP1363(der, 66)
        assertTrue(back.sameElements(p1363))
      }
    ),
    suite("JS browser/ESM capability seam returns UnsupportedAlgorithm")(
      test("asymmetric sign returns UnsupportedAlgorithm when crypto unavailable") {
        val result = withUnavailable {
          val key = Array.fill(32)(0x01.toByte)
          JsJwtCryptoBackend.sign("data".getBytes("UTF-8"), key, Algorithm.RS256)
        }
        assertTrue(result match {
          case Left(e: JwtError.UnsupportedAlgorithm) => e.alg == "RS256"
          case _                                      => false
        })
      },
      test("asymmetric verify returns UnsupportedAlgorithm when crypto unavailable") {
        val result = withUnavailable {
          val key = Array.fill(32)(0x01.toByte)
          val sig = Array.fill(32)(0x00.toByte)
          JsJwtCryptoBackend.verify("data".getBytes("UTF-8"), sig, key, Algorithm.ES256)
        }
        assertTrue(result match {
          case Left(e: JwtError.UnsupportedAlgorithm) => e.alg == "ES256"
          case _                                      => false
        })
      },
      test("browser-like verify does not throw") {
        val result = withUnavailable {
          val key = Array.fill(32)(0x01.toByte)
          val sig = Array.fill(32)(0x00.toByte)
          JsJwtCryptoBackend.verify("data".getBytes("UTF-8"), sig, key, Algorithm.RS256)
        }
        assertTrue(result match {
          case Left(_: JwtError.UnsupportedAlgorithm) => true
          case _                                      => false
        })
      },
      test("HMAC still works when crypto unavailable (shared backend)") {
        val res = withUnavailable {
          val key = Array.fill(32)(0x01.toByte)
          JsJwtCryptoBackend.sign("data".getBytes("UTF-8"), key, Algorithm.HS256)
        }
        assertTrue(res.isRight)
      },
      test("capability restores after withUnavailable (no leakage)") {
        withUnavailable(())
        assertTrue(JsCryptoCapability.isAvailable)
      }
    ),
    suite("Node RSA/ECDSA via Node crypto")(
      test("RS256 sign and verify roundtrip via Node crypto") {
        assertTrue(JsCryptoCapability.isAvailable)
        val (priv, pub) = generateRsaKeyPair()
        val data        = "hello-rsa".getBytes("UTF-8")
        val sig         = JsJwtCryptoBackend.sign(data, priv, Algorithm.RS256)
        assertTrue(sig.isRight)
        val ver = sig.flatMap(s => JsJwtCryptoBackend.verify(data, s, pub, Algorithm.RS256))
        assertTrue(ver == Right(true))
      },
      test("ES256 sign and verify roundtrip via Node crypto") {
        assertTrue(JsCryptoCapability.isAvailable)
        val (priv, pub) = generateEcKeyPair("prime256v1")
        val data        = "hello-ec".getBytes("UTF-8")
        val sig         = JsJwtCryptoBackend.sign(data, priv, Algorithm.ES256)
        assertTrue(sig.isRight)
        val ver = sig.flatMap(s => JsJwtCryptoBackend.verify(data, s, pub, Algorithm.ES256))
        assertTrue(ver == Right(true))
      },
      test("JWT HS256 roundtrip via Node") {
        val result = Jwt.sign(claims, hmacKey, Algorithm.HS256).flatMap(t => Jwt.decode(t, hmacKey, Algorithm.HS256))
        assertTrue(result == Right(claims))
      }
    ),
    suite("backend install atomic setter")(
      test("install uses JwtCrypto.installBackend") {
        JsJwtCryptoBackend.install()
        assertTrue(JwtCrypto.backend == JsJwtCryptoBackend)
      }
    ),
    suite("malformed signature classification")(
      test("weak HMAC key maps to InvalidKey with algorithm name") {
        val res = JsJwtCryptoBackend.sign("data".getBytes("UTF-8"), Array.fill(10)(0x01.toByte), Algorithm.HS256)
        assertTrue(res match {
          case Left(e: JwtError.InvalidKey) => e.reason.contains("HS256")
          case _                            => false
        })
      },
      test("malformed ECDSA signature maps to InvalidSignature") {
        assertTrue(JsCryptoCapability.isAvailable)
        val (_, pub) = generateEcKeyPair("prime256v1")
        val res      = JsJwtCryptoBackend.verify("data".getBytes("UTF-8"), Array.fill(10)(0x01.toByte), pub, Algorithm.ES256)
        assertTrue(res match {
          case Left(_: JwtError.InvalidSignature) => true
          case _                                  => false
        })
      },
      test("unsupported algorithm maps to UnsupportedAlgorithm") {
        val res = JsJwtCryptoBackend.sign("data".getBytes("UTF-8"), Array.fill(32)(0x01.toByte), Algorithm.PS256)
        assertTrue(res match {
          case Left(e: JwtError.UnsupportedAlgorithm) => e.alg == "PS256"
          case _                                      => false
        })
      }
    )
  ) @@ TestAspect.sequential
}
