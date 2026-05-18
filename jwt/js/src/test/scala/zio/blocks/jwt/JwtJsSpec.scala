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

object JwtJsSpec extends ZIOSpecDefault {

  JsJwtCryptoBackend.install()

  private val key      = "test-secret-key-for-js-spec".getBytes("UTF-8")
  private val wrongKey = "completely-different-key-js".getBytes("UTF-8")
  private val claims   = JwtClaims(sub = Some("js-test-subject"))

  def spec: Spec[TestEnvironment, Any] = suite("JwtJsSpec")(
    suite("HMAC roundtrip")(
      test("HS256 sign and decode roundtrip preserves claims") {
        val result = Jwt.sign(claims, key, Algorithm.HS256).flatMap(t =>
          Jwt.decode(t, key, Algorithm.HS256)
        )
        assertTrue(result == Right(claims))
      },
      test("HS384 sign and decode roundtrip preserves claims") {
        val result = Jwt.sign(claims, key, Algorithm.HS384).flatMap(t =>
          Jwt.decode(t, key, Algorithm.HS384)
        )
        assertTrue(result == Right(claims))
      },
      test("HS512 sign and decode roundtrip preserves claims") {
        val result = Jwt.sign(claims, key, Algorithm.HS512).flatMap(t =>
          Jwt.decode(t, key, Algorithm.HS512)
        )
        assertTrue(result == Right(claims))
      }
    ),
    suite("signature verification")(
      test("wrong key returns Left with InvalidSignature") {
        val result = Jwt.sign(claims, key, Algorithm.HS256).flatMap(t =>
          Jwt.decode(t, wrongKey, Algorithm.HS256)
        )
        assertTrue(result match {
          case Left(_: JwtError.InvalidSignature) => true
          case _                                  => false
        })
      }
    ),
    suite("claims validation")(
      test("expired token is rejected with ExpiredToken") {
        val now          = System.currentTimeMillis() / 1000L
        val expiredClaims = JwtClaims(sub = Some("js-test"), exp = Some(now - 3600L))
        val result       = Jwt.sign(expiredClaims, key, Algorithm.HS256).flatMap(t =>
          Jwt.decode(t, key, Algorithm.HS256)
        )
        assertTrue(result match {
          case Left(_: JwtError.ExpiredToken) => true
          case _                              => false
        })
      }
    ),
    suite("unsupported algorithms")(
      test("PS256 returns Left with UnsupportedAlgorithm") {
        val result = JsJwtCryptoBackend.sign("data".getBytes("UTF-8"), key, Algorithm.PS256)
        assertTrue(result match {
          case Left(_: JwtError.UnsupportedAlgorithm) => true
          case _                                       => false
        })
      },
      test("EdDSA returns Left with UnsupportedAlgorithm") {
        val result = JsJwtCryptoBackend.sign("data".getBytes("UTF-8"), key, Algorithm.EdDSA)
        assertTrue(result match {
          case Left(_: JwtError.UnsupportedAlgorithm) => true
          case _                                       => false
        })
      }
    ),
    suite("decodeUnsafe")(
      test("decodeUnsafe parses header and claims without signature verification") {
        val result = Jwt.sign(claims, key, Algorithm.HS256).flatMap(Jwt.decodeUnsafe)
        assertTrue(result match {
          case Right((hdr, cls)) => hdr.alg == Algorithm.HS256 && cls.sub == Some("js-test-subject")
          case _                 => false
        })
      }
    )
  )
}
