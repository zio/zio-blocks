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

object JwtJvmSecuritySpec extends ZIOSpecDefault {

  JvmJwtCryptoBackend.install()

  private val claims = JwtClaims(sub = Some("security-test"))

  // Helpers to craft DER for ECDSA malformed tests
  private def derWithIntegers(r: Array[Byte], s: Array[Byte]): Array[Byte] = {
    def encodeInteger(bytes: Array[Byte]): Array[Byte] =
      Array.concat(Array[Byte](0x02.toByte, bytes.length.toByte), bytes)
    val rEnc     = encodeInteger(r)
    val sEnc     = encodeInteger(s)
    val content  = Array.concat(rEnc, sEnc)
    val seqLen   = content.length
    val lenBytes =
      if (seqLen < 128) Array[Byte](seqLen.toByte) else Array[Byte]((0x80 | 1).toByte, seqLen.toByte)
    Array.concat(Array[Byte](0x30.toByte), lenBytes, content)
  }

  private def oversizedComponent(size: Int): Array[Byte] = Array.fill(size + 1)(0x01.toByte)

  private def ecKeyPair(curve: String): java.security.KeyPair = {
    val kpg = java.security.KeyPairGenerator.getInstance("EC")
    kpg.initialize(new java.security.spec.ECGenParameterSpec(curve))
    kpg.generateKeyPair()
  }

  private def rsaKeyPair(bits: Int): java.security.KeyPair = {
    val kpg = java.security.KeyPairGenerator.getInstance("RSA")
    kpg.initialize(bits)
    kpg.generateKeyPair()
  }

  def spec: Spec[TestEnvironment, Any] = suite("JwtJvmSecuritySpec")(
    suite("ECDSA DER strict validation (reproduces JVM truncation bug)")(
      test("oversized DER integer rejects with InvalidSignature") {
        val der     = derWithIntegers(oversizedComponent(32), Array(0x01.toByte))
        val outcome = try {
          EcdsaDer.derToP1363(der, 32)
          false
        } catch {
          case _: IllegalArgumentException => true
          case _: Exception                => false
        }
        assertTrue(outcome)
      },
      test("non-minimal DER integer (unnecessary leading zero) is rejected") {
        val der     = derWithIntegers(Array(0x00.toByte, 0x01.toByte), Array(0x01.toByte))
        val outcome = try {
          EcdsaDer.derToP1363(der, 32)
          false
        } catch {
          case e: IllegalArgumentException =>
            e.getMessage.toLowerCase.contains("non-minimal") || e.getMessage.toLowerCase.contains("leading zero")
          case _: Exception => false
        }
        assertTrue(outcome)
      },
      test("malformed DER length (non-minimal long form for small length) is rejected") {
        val der = Array[Byte](
          0x30.toByte,
          0x81.toByte,
          0x02.toByte,
          0x02.toByte,
          0x01.toByte,
          0x01.toByte,
          0x02.toByte,
          0x01.toByte,
          0x01.toByte
        )
        val outcome = try {
          EcdsaDer.derToP1363(der, 32)
          false
        } catch {
          case _: IllegalArgumentException => true
          case _: Exception                => false
        }
        assertTrue(outcome)
      },
      test("malformed DER integer tag is rejected") {
        val der = Array[Byte](
          0x30.toByte,
          0x06.toByte,
          0x03.toByte,
          0x01.toByte,
          0x01.toByte,
          0x02.toByte,
          0x01.toByte,
          0x01.toByte
        )
        val outcome = try {
          EcdsaDer.derToP1363(der, 32)
          false
        } catch {
          case _: IllegalArgumentException => true
          case _: Exception                => false
        }
        assertTrue(outcome)
      },
      test("ES512 width 66 is enforced (P-521) - P1363 length mismatch") {
        val sig66 = Array.fill(132)(0x01.toByte)
        // fix top bits for P-521: first byte of each component must have top 7 bits zero
        sig66(0) = 0x01.toByte
        sig66(66) = 0x01.toByte
        val sig64 = Array.fill(64)(0x01.toByte)
        val ok    = try { EcdsaDer.p1363ToDer(sig66, 66); true }
        catch { case _: Exception => false }
        val bad = try { EcdsaDer.p1363ToDer(sig64, 66); false }
        catch {
          case _: IllegalArgumentException => true; case _: Exception => false
        }
        assertTrue(ok && bad)
      },
      test("P-521 unused high bits are rejected") {
        // 66-byte component with top 7 bits set (0xff) should be rejected as InvalidSignature
        val bad = Array.fill(132)(0xff.toByte)
        val res = try { EcdsaDer.p1363ToDer(bad, 66); false }
        catch {
          case e: IllegalArgumentException => e.getMessage.toLowerCase.contains("p-521")
          case _: Exception                => false
        }
        assertTrue(res)
      },
      test("P-521 valid max scalar roundtrips deterministically") {
        val maxComp = Array.fill(66)(0xff.toByte)
        maxComp(0) = 0x01.toByte // top 7 bits zero, only bit0 may be 1
        val p1363 = Array.concat(maxComp, maxComp)
        val der   = EcdsaDer.p1363ToDer(p1363, 66)
        val back  = EcdsaDer.derToP1363(der, 66)
        assertTrue(back.sameElements(p1363))
      },
      test("P-521 valid signatures roundtrip deterministically over repeated iterations") {
        val results = (0 until 20).map { _ =>
          val kp   = ecKeyPair("secp521r1")
          val priv = kp.getPrivate.getEncoded
          val pub  = kp.getPublic.getEncoded
          val c    = JwtClaims(sub = Some("p521-test"))
          Jwt.sign(c, priv, Algorithm.ES512).flatMap(t => Jwt.decode(t, pub, Algorithm.ES512)).map(_.sub) == Right(
            Some("p521-test")
          )
        }
        assertTrue(results.forall(identity))
      }
    ),
    suite("HMAC key strength (JWA minimums)")(
      test("HS256 rejects 31-byte key with InvalidKey, accepts 32-byte key") {
        val key31      = Array.fill(31)(0x01.toByte)
        val key32      = Array.fill(32)(0x01.toByte)
        val r31        = Jwt.sign(claims, key31, Algorithm.HS256)
        val r32        = Jwt.sign(claims, key32, Algorithm.HS256)
        val r31Invalid = r31 match { case Left(_: JwtError.InvalidKey) => true; case _ => false }
        assertTrue(r31.isLeft && r31Invalid && r32.isRight)
      },
      test("HS384 rejects 47-byte key with InvalidKey") {
        val key47        = Array.fill(47)(0x01.toByte)
        val key48        = Array.fill(48)(0x01.toByte)
        val r47          = Jwt.sign(claims, key47, Algorithm.HS384)
        val r48          = Jwt.sign(claims, key48, Algorithm.HS384)
        val isInvalidKey = r47 match { case Left(_: JwtError.InvalidKey) => true; case _ => false }
        assertTrue(r47.isLeft && isInvalidKey && r48.isRight)
      },
      test("HS512 rejects 63-byte key with InvalidKey") {
        val key63        = Array.fill(63)(0x01.toByte)
        val key64        = Array.fill(64)(0x01.toByte)
        val r63          = Jwt.sign(claims, key63, Algorithm.HS512)
        val r64          = Jwt.sign(claims, key64, Algorithm.HS512)
        val isInvalidKey = r63 match { case Left(_: JwtError.InvalidKey) => true; case _ => false }
        assertTrue(r63.isLeft && isInvalidKey && r64.isRight)
      },
      test("HMAC key validation on verify path returns InvalidKey") {
        val key31 = Array.fill(31)(0x01.toByte)
        val key32 = Array.fill(32)(0x01.toByte)
        val token = Jwt.sign(claims, key32, Algorithm.HS256) match {
          case Right(t) => t
          case Left(_)  => ""
        }
        val res = Jwt.decode(token, key31, Algorithm.HS256)
        assertTrue(res match { case Left(_: JwtError.InvalidKey) => true; case _ => false })
      }
    ),
    suite("RSA key strength >=2048 bits via modulus")(
      test("RSA 1024-bit key is rejected with InvalidKey, 2048 accepted") {
        val kp1024           = rsaKeyPair(1024)
        val kp2048           = rsaKeyPair(2048)
        val priv1024         = kp1024.getPrivate.getEncoded
        val pub1024          = kp1024.getPublic.getEncoded
        val priv2048         = kp2048.getPrivate.getEncoded
        val sign1024         = JvmJwtCryptoBackend.sign("data".getBytes("UTF-8"), priv1024, Algorithm.RS256)
        val sign2048         = JvmJwtCryptoBackend.sign("data".getBytes("UTF-8"), priv2048, Algorithm.RS256)
        val fakeSig          = Array.fill(256)(0x00.toByte)
        val ver1024          = JvmJwtCryptoBackend.verify("data".getBytes("UTF-8"), fakeSig, pub1024, Algorithm.RS256)
        val signIsInvalidKey = sign1024 match { case Left(_: JwtError.InvalidKey) => true; case _ => false }
        val verIsInvalidKey  = ver1024 match { case Left(_: JwtError.InvalidKey) => true; case _ => false }
        assertTrue(signIsInvalidKey && sign2048.isRight && verIsInvalidKey)
      },
      test("RSA verify with PS256 weak key reports PS256 not RS256") {
        val kp1024 = rsaKeyPair(1024)
        val pub    = kp1024.getPublic.getEncoded
        val res    =
          JvmJwtCryptoBackend.verify("data".getBytes("UTF-8"), Array.fill(32)(0x00.toByte), pub, Algorithm.PS256)
        assertTrue(res match {
          case Left(JwtError.InvalidKey(msg)) => msg.contains("PS256")
          case _                              => false
        })
      }
    ),
    suite("EC curve size validation")(
      test("ES256 with secp384r1 key is rejected as InvalidKey") {
        val kp384  = ecKeyPair("secp384r1")
        val priv   = kp384.getPrivate.getEncoded
        val result = JvmJwtCryptoBackend.sign("data".getBytes("UTF-8"), priv, Algorithm.ES256)
        assertTrue(result match { case Left(_: JwtError.InvalidKey) => true; case _ => false })
      },
      test("ES384 with secp256r1 key is rejected as InvalidKey") {
        val kp256  = ecKeyPair("secp256r1")
        val priv   = kp256.getPrivate.getEncoded
        val result = JvmJwtCryptoBackend.sign("data".getBytes("UTF-8"), priv, Algorithm.ES384)
        assertTrue(result match { case Left(_: JwtError.InvalidKey) => true; case _ => false })
      },
      test("ES512 with secp256r1 key is rejected as InvalidKey") {
        val kp256  = ecKeyPair("secp256r1")
        val priv   = kp256.getPrivate.getEncoded
        val result = JvmJwtCryptoBackend.sign("data".getBytes("UTF-8"), priv, Algorithm.ES512)
        assertTrue(result match { case Left(_: JwtError.InvalidKey) => true; case _ => false })
      },
      test("EC key decode failure surfaces as InvalidKey (not swallowed)") {
        val badKey = Array.fill(20)(0x01.toByte)
        val result = JvmJwtCryptoBackend.sign("data".getBytes("UTF-8"), badKey, Algorithm.ES256)
        assertTrue(result match { case Left(_: JwtError.InvalidKey) => true; case _ => false })
      },
      test("Correct EC curve passes") {
        val kp256 = ecKeyPair("secp256r1")
        val priv  = kp256.getPrivate.getEncoded
        val pub   = kp256.getPublic.getEncoded
        val data  = "data".getBytes("UTF-8")
        val sig   = JvmJwtCryptoBackend.sign(data, priv, Algorithm.ES256)
        val ver   = sig.flatMap(s => JvmJwtCryptoBackend.verify(data, s, pub, Algorithm.ES256))
        assertTrue(ver == Right(true))
      }
    ),
    suite("error type distinctions")(
      test("weak HMAC key maps to InvalidKey") {
        val key = Array.fill(10)(0x01.toByte)
        val res = JvmJwtCryptoBackend.sign("data".getBytes("UTF-8"), key, Algorithm.HS256)
        assertTrue(res match {
          case Left(_: JwtError.InvalidKey) => true
          case _                            => false
        })
      },
      test("invalid P1363 length maps to InvalidSignature") {
        val badSig = Array.fill(10)(0x01.toByte)
        val kp256  = ecKeyPair("secp256r1")
        val pub    = kp256.getPublic.getEncoded
        val res    = JvmJwtCryptoBackend.verify("data".getBytes("UTF-8"), badSig, pub, Algorithm.ES256)
        assertTrue(res match {
          case Left(_: JwtError.InvalidSignature) => true
          case _                                  => false
        })
      },
      test("malformed DER via EcdsaDer maps to InvalidSignature") {
        val kp  = ecKeyPair("secp384r1")
        val pub = kp.getPublic.getEncoded
        val res =
          JvmJwtCryptoBackend.verify("hello".getBytes("UTF-8"), Array.fill(10)(0x00.toByte), pub, Algorithm.ES384)
        assertTrue(res match { case Left(_: JwtError.InvalidSignature) => true; case _ => false })
      }
    ),
    suite("backend install atomicity")(
      test("install uses atomic setter and is visible") {
        JvmJwtCryptoBackend.install()
        val after = JwtCrypto.backend
        assertTrue(after == JvmJwtCryptoBackend)
      }
    )
  ) @@ TestAspect.sequential
}
