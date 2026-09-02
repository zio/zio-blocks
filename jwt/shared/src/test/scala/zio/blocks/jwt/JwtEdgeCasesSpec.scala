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

import zio.blocks.chunk.Chunk
import zio.test._

object JwtEdgeCasesSpec extends ZIOSpecDefault {

  private val key256 = Array.fill(32)(0x01.toByte)
  private val key384 = Array.fill(48)(0x02.toByte)

  def spec: Spec[TestEnvironment, Any] = suite("JwtEdgeCasesSpec")(
    suite("aud string/array/mixed-invalid")(
      test("aud single string roundtrips") {
        val claims = JwtClaims(aud = Some(JwtAudience.Single("single-aud")))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.aud) == Right(Some(JwtAudience.Single("single-aud"))))
      },
      test("aud array roundtrips") {
        val claims = JwtClaims(aud = Some(JwtAudience.Multiple(Chunk("a", "b", "c"))))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.aud) == Right(Some(JwtAudience.Multiple(Chunk("a", "b", "c")))))
      },
      test("aud empty array roundtrips") {
        val claims = JwtClaims(aud = Some(JwtAudience.Multiple(Chunk.empty)))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.aud) == Right(Some(JwtAudience.Multiple(Chunk.empty))))
      },
      test("aud array with non-string fails with InvalidClaim") {
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val payloadJson  = """{"aud":["ok", 123]}"""
        val payload      = Base64Url.encode(payloadJson.getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token  = signingInput + "." + sig
        val result = Jwt.decode(token, key256, Algorithm.HS256)
        assertTrue(result match {
          case Left(_: JwtError.InvalidClaim) => true
          case _                              => false
        })
      },
      test("aud as number fails with InvalidClaim") {
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val payload      = Base64Url.encode("""{"aud":123}""".getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token = signingInput + "." + sig
        assertTrue(Jwt.decode(token, key256, Algorithm.HS256) match {
          case Left(_: JwtError.InvalidClaim) => true; case _ => false
        })
      },
      test("aud as bool fails with InvalidClaim") {
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val payload      = Base64Url.encode("""{"aud":true}""".getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token = signingInput + "." + sig
        assertTrue(Jwt.decode(token, key256, Algorithm.HS256) match {
          case Left(_: JwtError.InvalidClaim) => true; case _ => false
        })
      },
      test("aud as null is treated as absent (no aud)") {
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val payload      = Base64Url.encode("""{"aud":null}""".getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token  = signingInput + "." + sig
        val result = Jwt.decode(token, key256, Algorithm.HS256)
        assertTrue(result.map(_.aud) == Right(None))
      }
    ),
    suite("extra bool/number/array/null/nested object (public JwtValue API)")(
      test("extra bool true roundtrips") {
        val claims = JwtClaims(extra = Map("flag" -> JwtValue.Bool(true)))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.extra.get("flag")) == Right(Some(JwtValue.Bool(true))))
      },
      test("extra bool false roundtrips") {
        val claims = JwtClaims(extra = Map("flag" -> JwtValue.Bool(false)))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.extra.get("flag")) == Right(Some(JwtValue.Bool(false))))
      },
      test("extra number integer roundtrips") {
        val claims = JwtClaims(extra = Map("num" -> JwtValue.Num("42")))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.extra.get("num")) == Right(Some(JwtValue.Num("42"))))
      },
      test("extra number fractional roundtrips") {
        val claims = JwtClaims(extra = Map("pi" -> JwtValue.Num("3.14159")))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.extra.get("pi")) == Right(Some(JwtValue.Num("3.14159"))))
      },
      test("extra number exponent roundtrips") {
        val claims = JwtClaims(extra = Map("big" -> JwtValue.Num("1e9")))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.extra.get("big")) == Right(Some(JwtValue.Num("1e9"))))
      },
      test("extra null roundtrips") {
        val claims = JwtClaims(extra = Map("nothing" -> JwtValue.Null))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.extra.get("nothing")) == Right(Some(JwtValue.Null)))
      },
      test("extra array roundtrips") {
        val arr    = JwtValue.Arr(Chunk(JwtValue.Str("a"), JwtValue.Num("1"), JwtValue.Bool(true), JwtValue.Null))
        val claims = JwtClaims(extra = Map("arr" -> arr))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.extra.get("arr")) == Right(Some(arr)))
      },
      test("extra nested object roundtrips") {
        val inner  = JwtValue.Obj(Map("innerStr" -> JwtValue.Str("hello"), "innerNum" -> JwtValue.Num("99")))
        val outer  = JwtValue.Obj(Map("inner" -> inner, "flag" -> JwtValue.Bool(false)))
        val claims = JwtClaims(extra = Map("outer" -> outer))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.extra.get("outer")) == Right(Some(outer)))
      },
      test("extra nested object containing array roundtrips") {
        val arr    = JwtValue.Arr(Chunk(JwtValue.Obj(Map("k" -> JwtValue.Str("v"))), JwtValue.Num("2")))
        val obj    = JwtValue.Obj(Map("arr" -> arr))
        val claims = JwtClaims(extra = Map("obj" -> obj))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.extra.get("obj")) == Right(Some(obj)))
      }
    ),
    suite("header kid/typ")(
      test("header kid roundtrips") {
        val header  = JwtHeader(Algorithm.HS256, kid = Some("key-id-123"))
        val claims  = JwtClaims(sub = Some("s"))
        val token   = Jwt.sign(claims, key256, Algorithm.HS256, header).getOrElse("")
        val decoded = Jwt.decodeWithoutVerification(token)
        assertTrue(decoded.map(_._1.kid) == Right(Some("key-id-123")))
      },
      test("header typ custom roundtrips") {
        val header  = JwtHeader(Algorithm.HS256, typ = "at+jwt")
        val claims  = JwtClaims(sub = Some("s"))
        val token   = Jwt.sign(claims, key256, Algorithm.HS256, header).getOrElse("")
        val decoded = Jwt.decodeWithoutVerification(token)
        assertTrue(decoded.map(_._1.typ) == Right("at+jwt"))
      },
      test("header typ defaults to JWT when absent in token") {
        val headerJson   = """{"alg":"HS256"}"""
        val headerSeg    = Base64Url.encode(headerJson.getBytes("UTF-8"))
        val payloadSeg   = Base64Url.encode("""{"sub":"x"}""".getBytes("UTF-8"))
        val signingInput = headerSeg + "." + payloadSeg
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token  = signingInput + "." + sig
        val result = Jwt.decodeWithoutVerification(token)
        assertTrue(result.map(_._1.typ) == Right("JWT"))
      },
      test("header kid absent is None") {
        val header  = JwtHeader(Algorithm.HS256)
        val claims  = JwtClaims(sub = Some("s"))
        val token   = Jwt.sign(claims, key256, Algorithm.HS256, header).getOrElse("")
        val decoded = Jwt.decodeWithoutVerification(token)
        assertTrue(decoded.map(_._1.kid) == Right(None))
      }
    ),
    suite("explicit sign header mismatch")(
      test("sign with header alg mismatch returns AlgorithmMismatch") {
        val header = JwtHeader(Algorithm.HS384)
        val claims = JwtClaims(sub = Some("s"))
        val result = Jwt.sign(claims, key384, Algorithm.HS256, header)
        assertTrue(result match {
          case Left(JwtError.AlgorithmMismatch("HS256", "HS384")) => true
          case _                                                  => false
        })
      },
      test("sign with header alg mismatch for RS vs HS") {
        val header = JwtHeader(Algorithm.RS256)
        val claims = JwtClaims(sub = Some("s"))
        val result = Jwt.sign(claims, key256, Algorithm.HS256, header)
        assertTrue(result match {
          case Left(_: JwtError.AlgorithmMismatch) => true
          case _                                   => false
        })
      }
    ),
    suite("unknown/missing alg")(
      test("unknown alg in header returns UnsupportedAlgorithm on decode") {
        val headerJson = """{"alg":"FOO","typ":"JWT"}"""
        val header     = Base64Url.encode(headerJson.getBytes("UTF-8"))
        val payload    = Base64Url.encode("""{"sub":"x"}""".getBytes("UTF-8"))
        val sig        = Base64Url.encode(Array.fill(16)(0x01.toByte))
        val token      = header + "." + payload + "." + sig
        val result     = Jwt.decode(token, key256, Algorithm.HS256)
        assertTrue(result match {
          case Left(_: JwtError.UnsupportedAlgorithm) => true
          case _                                      => false
        })
      },
      test("missing alg in header returns MissingClaim") {
        val headerJson = """{"typ":"JWT"}"""
        val header     = Base64Url.encode(headerJson.getBytes("UTF-8"))
        val payload    = Base64Url.encode("""{"sub":"x"}""".getBytes("UTF-8"))
        val sig        = Base64Url.encode(Array.fill(16)(0x01.toByte))
        val token      = header + "." + payload + "." + sig
        val result     = Jwt.decode(token, key256, Algorithm.HS256)
        assertTrue(result match {
          case Left(JwtError.MissingClaim("alg")) => true
          case _                                  => false
        })
      },
      test("empty alg string returns UnsupportedAlgorithm") {
        val headerJson = """{"alg":"","typ":"JWT"}"""
        val header     = Base64Url.encode(headerJson.getBytes("UTF-8"))
        val payload    = Base64Url.encode("""{"sub":"x"}""".getBytes("UTF-8"))
        val sig        = Base64Url.encode(Array.fill(16)(0x01.toByte))
        val token      = header + "." + payload + "." + sig
        val result     = Jwt.decode(token, key256, Algorithm.HS256)
        assertTrue(result match {
          case Left(_: JwtError.UnsupportedAlgorithm) => true
          case _                                      => false
        })
      }
    ),
    suite("empty segments")(
      test("empty header segment returns InvalidToken") {
        val payload = Base64Url.encode("""{"sub":"x"}""".getBytes("UTF-8"))
        val sig     = Base64Url.encode(Array.fill(16)(0x01.toByte))
        val token   = "." + payload + "." + sig
        assertTrue(Jwt.decode(token, key256, Algorithm.HS256) match {
          case Left(_: JwtError.InvalidToken) => true; case _ => false
        })
      },
      test("empty payload segment returns InvalidToken") {
        val header = Base64Url.encode("""{"alg":"HS256","typ":"JWT"}""".getBytes("UTF-8"))
        val sig    = Base64Url.encode(Array.fill(16)(0x01.toByte))
        val token  = header + ".." + sig
        assertTrue(Jwt.decode(token, key256, Algorithm.HS256) match {
          case Left(_: JwtError.InvalidToken) => true; case _ => false
        })
      },
      test("empty signature segment returns InvalidToken") {
        val header  = Base64Url.encode("""{"alg":"HS256","typ":"JWT"}""".getBytes("UTF-8"))
        val payload = Base64Url.encode("""{"sub":"x"}""".getBytes("UTF-8"))
        val token   = header + "." + payload + "."
        assertTrue(Jwt.decode(token, key256, Algorithm.HS256) match {
          case Left(_: JwtError.InvalidToken) => true; case _ => false
        })
      },
      test("token with only dots returns InvalidToken") {
        assertTrue(Jwt.decode("..", key256, Algorithm.HS256) match {
          case Left(_: JwtError.InvalidToken) => true; case _ => false
        })
        assertTrue(Jwt.decode("...", key256, Algorithm.HS256) match {
          case Left(_: JwtError.InvalidToken) => true; case _ => false
        })
      },
      test("token with no dots returns InvalidToken") {
        assertTrue(Jwt.decode("nodots", key256, Algorithm.HS256) match {
          case Left(_: JwtError.InvalidToken) => true; case _ => false
        })
      },
      test("token with two segments returns InvalidToken") {
        assertTrue(Jwt.decode("a.b", key256, Algorithm.HS256) match {
          case Left(_: JwtError.InvalidToken) => true; case _ => false
        })
      },
      test("token with four segments returns InvalidToken") {
        assertTrue(Jwt.decode("a.b.c.d", key256, Algorithm.HS256) match {
          case Left(_: JwtError.InvalidToken) => true; case _ => false
        })
      }
    ),
    suite("bad claim types")(
      test("iss as number returns InvalidToken") {
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val payload      = Base64Url.encode("""{"iss":123}""".getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token  = signingInput + "." + sig
        val result = Jwt.decode(token, key256, Algorithm.HS256)
        assertTrue(result match {
          case Left(_: JwtError.InvalidClaim) => true; case _ => false
        })
      },
      test("sub as number returns InvalidToken") {
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val payload      = Base64Url.encode("""{"sub":123}""".getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token = signingInput + "." + sig
        assertTrue(Jwt.decode(token, key256, Algorithm.HS256) match {
          case Left(_: JwtError.InvalidClaim) => true; case _ => false
        })
      },
      test("exp as string returns InvalidToken") {
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val payload      = Base64Url.encode("""{"exp":"not-a-number"}""".getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token = signingInput + "." + sig
        assertTrue(Jwt.decode(token, key256, Algorithm.HS256) match {
          case Left(_: JwtError.InvalidClaim) => true; case _ => false
        })
      },
      test("nbf as bool returns InvalidToken") {
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val payload      = Base64Url.encode("""{"nbf":true}""".getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token = signingInput + "." + sig
        assertTrue(Jwt.decode(token, key256, Algorithm.HS256) match {
          case Left(_: JwtError.InvalidClaim) => true; case _ => false
        })
      },
      test("iat as array returns InvalidToken") {
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val payload      = Base64Url.encode("""{"iat":[1,2]}""".getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token = signingInput + "." + sig
        assertTrue(Jwt.decode(token, key256, Algorithm.HS256) match {
          case Left(_: JwtError.InvalidClaim) => true; case _ => false
        })
      },
      test("jti as number returns InvalidToken") {
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val payload      = Base64Url.encode("""{"jti":123}""".getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token = signingInput + "." + sig
        assertTrue(Jwt.decode(token, key256, Algorithm.HS256) match {
          case Left(_: JwtError.InvalidClaim) => true; case _ => false
        })
      },
      test("exp as bool returns InvalidToken") {
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val payload      = Base64Url.encode("""{"exp":false}""".getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token = signingInput + "." + sig
        assertTrue(Jwt.decode(token, key256, Algorithm.HS256) match {
          case Left(_: JwtError.InvalidClaim) => true; case _ => false
        })
      }
    ),
    suite("header decodeWithoutVerification")(
      test("decodeWithoutVerification returns header and claims without signature check") {
        val claims = JwtClaims(sub = Some("s"), iss = Some("iss"))
        val token  = Jwt.sign(claims, key256, Algorithm.HS256).getOrElse("")
        val result = Jwt.decodeWithoutVerification(token)
        assertTrue(result.map(_._2.sub) == Right(Some("s")))
      }
    )
  )
}
