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

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.typedarray.Int8Array

object JwtJsSpec extends ZIOSpecDefault {

  JsJwtCryptoBackend.install()

  private val key256   = Array.fill(32)(0x11.toByte)
  private val key384   = Array.fill(48)(0x12.toByte)
  private val key512   = Array.fill(64)(0x13.toByte)
  private val wrongKey = Array.fill(32)(0x14.toByte)
  private val claims   = JwtClaims(sub = Some("js-test-subject"))

  private def bufferToBytes(buf: js.Dynamic): Array[Byte] = {
    val off = buf.byteOffset.asInstanceOf[Int]
    val len = buf.length.asInstanceOf[Int]
    val ab  = buf.buffer.asInstanceOf[js.typedarray.ArrayBuffer]
    new Int8Array(ab, off, len).toArray
  }

  private def genRsaPair(): (Array[Byte], Array[Byte]) = {
    val crypto  = g.require("crypto")
    val pair    = crypto.generateKeyPairSync("rsa", js.Dynamic.literal(modulusLength = 2048))
    val privBuf = pair.privateKey.`export`(js.Dynamic.literal(`type` = "pkcs8", format = "der"))
    val pubBuf  = pair.publicKey.`export`(js.Dynamic.literal(`type` = "spki", format = "der"))
    (bufferToBytes(privBuf), bufferToBytes(pubBuf))
  }

  private def genEcPair(curve: String): (Array[Byte], Array[Byte]) = {
    val crypto  = g.require("crypto")
    val pair    = crypto.generateKeyPairSync("ec", js.Dynamic.literal(namedCurve = curve))
    val privBuf = pair.privateKey.`export`(js.Dynamic.literal(`type` = "pkcs8", format = "der"))
    val pubBuf  = pair.publicKey.`export`(js.Dynamic.literal(`type` = "spki", format = "der"))
    (bufferToBytes(privBuf), bufferToBytes(pubBuf))
  }

  def spec: Spec[TestEnvironment, Any] = suite("JwtJsSpec")(
    suite("HMAC roundtrip")(
      test("HS256 sign and decode roundtrip preserves claims") {
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result == Right(claims))
      },
      test("HS384 sign and decode roundtrip preserves claims") {
        val result = Jwt.sign(claims, key384, Algorithm.HS384).flatMap(t => Jwt.decode(t, key384, Algorithm.HS384))
        assertTrue(result == Right(claims))
      },
      test("HS512 sign and decode roundtrip preserves claims") {
        val result = Jwt.sign(claims, key512, Algorithm.HS512).flatMap(t => Jwt.decode(t, key512, Algorithm.HS512))
        assertTrue(result == Right(claims))
      }
    ),
    suite("signature verification")(
      test("wrong key returns Left with InvalidSignature") {
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, wrongKey, Algorithm.HS256))
        assertTrue(result match {
          case Left(_: JwtError.InvalidSignature) => true
          case _                                  => false
        })
      }
    ),
    suite("claims validation")(
      test("expired token is rejected with ExpiredToken") {
        val now           = System.currentTimeMillis() / 1000L
        val expiredClaims = JwtClaims(sub = Some("js-test"), exp = Some(now - 3600L))
        val result        =
          Jwt.sign(expiredClaims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result match {
          case Left(_: JwtError.ExpiredToken) => true
          case _                              => false
        })
      }
    ),
    suite("unsupported algorithms")(
      test("PS256 returns Left with UnsupportedAlgorithm") {
        val result = JsJwtCryptoBackend.sign("data".getBytes("UTF-8"), key256, Algorithm.PS256)
        assertTrue(result match {
          case Left(_: JwtError.UnsupportedAlgorithm) => true
          case _                                      => false
        })
      },
      test("EdDSA returns Left with UnsupportedAlgorithm") {
        val result = JsJwtCryptoBackend.sign("data".getBytes("UTF-8"), key256, Algorithm.EdDSA)
        assertTrue(result match {
          case Left(_: JwtError.UnsupportedAlgorithm) => true
          case _                                      => false
        })
      }
    ),
    suite("RSA roundtrips (Node)")(
      test("RS256 sign and decode roundtrip preserves claims") {
        assertTrue(JsCryptoCapability.isAvailable)
        val (priv, pub) = genRsaPair()
        val result      = Jwt.sign(claims, priv, Algorithm.RS256).flatMap(t => Jwt.decode(t, pub, Algorithm.RS256))
        assertTrue(result == Right(claims))
      },
      test("RS384 sign and decode roundtrip preserves claims") {
        assertTrue(JsCryptoCapability.isAvailable)
        val (priv, pub) = genRsaPair()
        val result      = Jwt.sign(claims, priv, Algorithm.RS384).flatMap(t => Jwt.decode(t, pub, Algorithm.RS384))
        assertTrue(result == Right(claims))
      },
      test("RS512 sign and decode roundtrip preserves claims") {
        assertTrue(JsCryptoCapability.isAvailable)
        val (priv, pub) = genRsaPair()
        val result      = Jwt.sign(claims, priv, Algorithm.RS512).flatMap(t => Jwt.decode(t, pub, Algorithm.RS512))
        assertTrue(result == Right(claims))
      },
      test("RS256 wrong public key returns InvalidSignature") {
        assertTrue(JsCryptoCapability.isAvailable)
        val (priv, _)     = genRsaPair()
        val (_, pubWrong) = genRsaPair()
        val result        = Jwt.sign(claims, priv, Algorithm.RS256).flatMap(t => Jwt.decode(t, pubWrong, Algorithm.RS256))
        assertTrue(result match {
          case Left(_: JwtError.InvalidSignature) => true
          case _                                  => false
        })
      }
    ),
    suite("ECDSA roundtrips (Node)")(
      test("ES256 sign and decode roundtrip preserves claims") {
        assertTrue(JsCryptoCapability.isAvailable)
        val (priv, pub) = genEcPair("prime256v1")
        val result      = Jwt.sign(claims, priv, Algorithm.ES256).flatMap(t => Jwt.decode(t, pub, Algorithm.ES256))
        assertTrue(result == Right(claims))
      },
      test("ES384 sign and decode roundtrip preserves claims") {
        assertTrue(JsCryptoCapability.isAvailable)
        val (priv, pub) = genEcPair("secp384r1")
        val result      = Jwt.sign(claims, priv, Algorithm.ES384).flatMap(t => Jwt.decode(t, pub, Algorithm.ES384))
        assertTrue(result == Right(claims))
      },
      test("ES512 sign and decode roundtrip preserves claims") {
        assertTrue(JsCryptoCapability.isAvailable)
        val (priv, pub) = genEcPair("secp521r1")
        val result      = Jwt.sign(claims, priv, Algorithm.ES512).flatMap(t => Jwt.decode(t, pub, Algorithm.ES512))
        assertTrue(result == Right(claims))
      },
      test("ES256 wrong public key returns InvalidSignature") {
        assertTrue(JsCryptoCapability.isAvailable)
        val (priv, _)     = genEcPair("prime256v1")
        val (_, pubWrong) = genEcPair("prime256v1")
        val result        = Jwt.sign(claims, priv, Algorithm.ES256).flatMap(t => Jwt.decode(t, pubWrong, Algorithm.ES256))
        assertTrue(result match {
          case Left(_: JwtError.InvalidSignature) => true
          case _                                  => false
        })
      }
    ),
    suite("decodeUnsafe")(
      test("decodeUnsafe parses header and claims without signature verification") {
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(Jwt.decodeUnsafe)
        assertTrue(result match {
          case Right((hdr, cls)) => hdr.alg == Algorithm.HS256 && cls.sub == Some("js-test-subject")
          case _                 => false
        })
      }
    )
  )
}
