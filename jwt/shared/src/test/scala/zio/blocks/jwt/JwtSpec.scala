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

object JwtSpec extends ZIOSpecDefault {

  private val key256     = "your-256-bit-secret".getBytes("UTF-8")
  private val key384     = "your-384-bit-secret-which-is-longer".getBytes("UTF-8")
  private val key512     = "your-512-bit-secret-which-is-even-longer-for-testing".getBytes("UTF-8")
  private val wrongKey   = "completely-different-secret".getBytes("UTF-8")

  private val simpleClaims  = JwtClaims(sub = Some("test-subject"))
  private val vectorClaims  = JwtClaims(
    sub   = Some("1234567890"),
    iat   = Some(1516239022L),
    extra = Map("name" -> JwtJson.StringValue("John Doe"))
  )
  private val issuerClaims  = JwtClaims(sub = Some("test-subject"), iss = Some("test-issuer"))
  private val noIssuerClaims = JwtClaims(sub = Some("test-subject"))

  def spec: Spec[TestEnvironment, Any] = suite("JwtSpec")(
    suite("HMAC sign and decode roundtrip")(
      test("HS256 roundtrip preserves claims") {
        val result = Jwt.sign(simpleClaims, key256, Algorithm.HS256).flatMap(t =>
          Jwt.decode(t, key256, Algorithm.HS256)
        )
        assertTrue(result == Right(simpleClaims))
      },
      test("HS384 roundtrip preserves claims") {
        val result = Jwt.sign(simpleClaims, key384, Algorithm.HS384).flatMap(t =>
          Jwt.decode(t, key384, Algorithm.HS384)
        )
        assertTrue(result == Right(simpleClaims))
      },
      test("HS512 roundtrip preserves claims") {
        val result = Jwt.sign(simpleClaims, key512, Algorithm.HS512).flatMap(t =>
          Jwt.decode(t, key512, Algorithm.HS512)
        )
        assertTrue(result == Right(simpleClaims))
      }
    ),
    suite("known test vector")(
      test("HS256 with jwt.io example key and claims: sub is preserved after roundtrip") {
        val result = Jwt.sign(vectorClaims, key256, Algorithm.HS256).flatMap(t =>
          Jwt.decode(t, key256, Algorithm.HS256)
        )
        assertTrue(result.map(_.sub) == Right(Some("1234567890")))
      },
      test("HS256 with jwt.io example key and claims: extra claims preserved") {
        val result = Jwt.sign(vectorClaims, key256, Algorithm.HS256).flatMap(t =>
          Jwt.decode(t, key256, Algorithm.HS256)
        )
        assertTrue(result.map(_.extra.get("name")) == Right(Some(JwtJson.StringValue("John Doe"))))
      }
    ),
    suite("claims validation")(
      test("expired token is rejected with ExpiredToken") {
        val now          = System.currentTimeMillis() / 1000L
        val expiredAt    = now - 3600L
        val expiredClaims = JwtClaims(sub = Some("test"), exp = Some(expiredAt))
        val result       = Jwt.sign(expiredClaims, key256, Algorithm.HS256).flatMap(t =>
          Jwt.decode(t, key256, Algorithm.HS256)
        )
        assertTrue(result match {
          case Left(_: JwtError.ExpiredToken) => true
          case _                              => false
        })
      },
      test("not-yet-valid token is rejected with NotYetValid") {
        val now          = System.currentTimeMillis() / 1000L
        val futureClaims = JwtClaims(sub = Some("test"), nbf = Some(now + 3600L))
        val result       = Jwt.sign(futureClaims, key256, Algorithm.HS256).flatMap(t =>
          Jwt.decode(t, key256, Algorithm.HS256)
        )
        assertTrue(result match {
          case Left(_: JwtError.NotYetValid) => true
          case _                             => false
        })
      },
      test("expired token passes with sufficient clock skew") {
        val now          = System.currentTimeMillis() / 1000L
        val expiredClaims = JwtClaims(sub = Some("test"), exp = Some(now - 3600L))
        val tokenResult  = Jwt.sign(expiredClaims, key256, Algorithm.HS256)
        val withoutSkew  = tokenResult.flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        val withSkew     = tokenResult.flatMap(t => Jwt.decode(t, key256, Algorithm.HS256, clockSkewSeconds = 7200L))
        assertTrue(withoutSkew.isLeft && withSkew.isRight)
      },
      test("missing exp and nbf is accepted") {
        val claims = JwtClaims(sub = Some("no-time-claims"))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t =>
          Jwt.decode(t, key256, Algorithm.HS256)
        )
        assertTrue(result.isRight)
      }
    ),
    suite("signature verification")(
      test("wrong key returns InvalidSignature") {
        val result = Jwt.sign(simpleClaims, key256, Algorithm.HS256).flatMap(t =>
          Jwt.decode(t, wrongKey, Algorithm.HS256)
        )
        assertTrue(result match {
          case Left(_: JwtError.InvalidSignature) => true
          case _                                  => false
        })
      },
      test("algorithm mismatch between header and decoder returns AlgorithmMismatch") {
        val result = Jwt.sign(simpleClaims, key384, Algorithm.HS384).flatMap(t =>
          Jwt.decode(t, key256, Algorithm.HS256)
        )
        assertTrue(result match {
          case Left(JwtError.AlgorithmMismatch("HS256", "HS384")) => true
          case _                                                   => false
        })
      }
    ),
    suite("decodeUnsafe")(
      test("decodeUnsafe parses header and claims without signature verification") {
        val result = Jwt.sign(simpleClaims, key256, Algorithm.HS256).flatMap(Jwt.decodeUnsafe)
        assertTrue(result match {
          case Right((hdr, cls)) => hdr.alg == Algorithm.HS256 && cls.sub == Some("test-subject")
          case _                 => false
        })
      },
      test("decodeUnsafe returns Left for malformed token") {
        assertTrue(Jwt.decodeUnsafe("not-a-jwt").isLeft)
      }
    ),
    suite("token format validation")(
      test("token with only one dot (two segments) returns Left") {
        val result = Jwt.decode("header.payload", key256, Algorithm.HS256)
        assertTrue(result.isLeft)
      },
      test("token with three dots (four segments) returns Left") {
        val result = Jwt.decode("a.b.c.d", key256, Algorithm.HS256)
        assertTrue(result.isLeft)
      },
      test("token with no dots returns Left") {
        val result = Jwt.decode("nodots", key256, Algorithm.HS256)
        assertTrue(result.isLeft)
      }
    ),
    suite("issuer validation")(
      test("matching issuer passes") {
        val result = Jwt.sign(issuerClaims, key256, Algorithm.HS256).flatMap(t =>
          Jwt.decode(t, key256, Algorithm.HS256, issuer = Some("test-issuer"))
        )
        assertTrue(result.isRight)
      },
      test("mismatched issuer returns Left with InvalidToken") {
        val result = Jwt.sign(issuerClaims, key256, Algorithm.HS256).flatMap(t =>
          Jwt.decode(t, key256, Algorithm.HS256, issuer = Some("other-issuer"))
        )
        assertTrue(result match {
          case Left(_: JwtError.InvalidToken) => true
          case _                              => false
        })
      },
      test("missing iss claim when issuer validation required returns Left with MissingClaim") {
        val result = Jwt.sign(noIssuerClaims, key256, Algorithm.HS256).flatMap(t =>
          Jwt.decode(t, key256, Algorithm.HS256, issuer = Some("test-issuer"))
        )
        assertTrue(result match {
          case Left(JwtError.MissingClaim("iss")) => true
          case _                                  => false
        })
      },
      test("no issuer validation required and no iss in claims passes") {
        val result = Jwt.sign(noIssuerClaims, key256, Algorithm.HS256).flatMap(t =>
          Jwt.decode(t, key256, Algorithm.HS256)
        )
        assertTrue(result.isRight)
      }
    )
  )
}
