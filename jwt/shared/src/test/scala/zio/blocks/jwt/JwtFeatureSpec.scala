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

object JwtFeatureSpec extends ZIOSpecDefault {

  private val key256 = Array.fill(32)(0x01.toByte)

  def spec: Spec[TestEnvironment, Any] = suite("JwtFeatureSpec")(
    suite("public JwtValue construction")(
      test("external callers can construct all JwtValue kinds") {
        val vStr  = JwtValue.Str("hello")
        val vNum  = JwtValue.Num("123.45")
        val vBool = JwtValue.Bool(true)
        val vNull = JwtValue.Null
        val vArr  = JwtValue.Arr(Chunk(vStr, vNum, vBool, vNull))
        val vObj  = JwtValue.Obj(Map("a" -> vStr, "b" -> vArr))
        assertTrue(vStr.value == "hello") &&
        assertTrue(vNum.raw == "123.45") &&
        assertTrue(vBool.value) &&
        assertTrue(vNull == JwtValue.Null) &&
        assertTrue(vArr.items.length == 4) &&
        assertTrue(vObj.fields.size == 2)
      },
      test("extra map accepts JwtValue public type") {
        val claims = JwtClaims(extra = Map("k" -> JwtValue.Str("v")))
        assertTrue(claims.extra.get("k").contains(JwtValue.Str("v")))
      }
    ),
    suite("nested object/array roundtrip")(
      test("nested object preserved") {
        val nested = JwtValue.Obj(Map("inner" -> JwtValue.Str("x"), "num" -> JwtValue.Num("42")))
        val claims = JwtClaims(sub = Some("s"), extra = Map("obj" -> nested))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.extra.get("obj")) == Right(Some(nested)))
      },
      test("nested array preserved") {
        val arr    = JwtValue.Arr(JwtValue.Str("a"), JwtValue.Num("1"), JwtValue.Bool(false))
        val claims = JwtClaims(extra = Map("arr" -> arr))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.extra.get("arr")) == Right(Some(arr)))
      },
      test("object containing array containing object roundtrip") {
        val inner  = JwtValue.Obj(Map("k" -> JwtValue.Str("v")))
        val arr    = JwtValue.Arr(inner, JwtValue.Num("1.5"))
        val outer  = JwtValue.Obj(Map("arr" -> arr, "flag" -> JwtValue.Bool(true)))
        val claims = JwtClaims(extra = Map("outer" -> outer))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.extra.get("outer")) == Right(Some(outer)))
      },
      test("mixed array with all kinds preserved") {
        val mixed = JwtValue.Arr(
          JwtValue.Str("s"),
          JwtValue.Num("3.14"),
          JwtValue.Bool(true),
          JwtValue.Null,
          JwtValue.Obj(Map("x" -> JwtValue.Num("1")))
        )
        val claims = JwtClaims(extra = Map("mixed" -> mixed))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.extra.get("mixed")) == Right(Some(mixed)))
      }
    ),
    suite("authenticated decode verifies before parsing payload")(
      test("tampered payload with malformed JSON returns InvalidSignature not InvalidToken") {
        val claims      = JwtClaims(sub = Some("ok"))
        val tokenEither = Jwt.sign(claims, key256, Algorithm.HS256)
        val tampered    = tokenEither.map { token =>
          val parts = token.split('.')
          // malformed JSON: not an object
          val badPayload = Base64Url.encode("not-json".getBytes("UTF-8"))
          parts(0) + "." + badPayload + "." + parts(2)
        }
        val result = tampered.flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result match {
          case Left(_: JwtError.InvalidSignature) => true
          case _                                  => false
        })
      },
      test("tampered payload with oversized JSON returns InvalidSignature before JsonTooLarge") {
        val decodeLimits = JwtLimits(
          maxJsonChars = 50,
          maxTokenChars = 8192,
          maxSegmentChars = 4096,
          maxDepth = 32,
          maxFields = 512,
          maxArrayElements = 512
        )
        val claims     = JwtClaims(sub = Some("ok"))
        val token      = Jwt.sign(claims, key256, Algorithm.HS256)
        val bigPayload = Base64Url.encode(("{\"a\":\"" + ("x" * 100) + "\"}").getBytes("UTF-8"))
        val tampered   = token.map { t =>
          val p = t.split('.')
          p(0) + "." + bigPayload + "." + p(2)
        }
        val result =
          tampered.flatMap(t => Jwt.decode(t, key256, Algorithm.HS256, JwtDecodeOptions(limits = decodeLimits)))
        assertTrue(result match {
          case Left(_: JwtError.InvalidSignature) => true
          case _                                  => false
        })
      },
      test("valid signature but malformed payload returns InvalidToken (after verify)") {
        // Create a token manually with valid signature but payload is not JSON object
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val badPayload   = Base64Url.encode("not-json".getBytes("UTF-8"))
        val signingInput = header + "." + badPayload
        // sign with correct backend
        val sig = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token  = signingInput + "." + sig
        val result = Jwt.decode(token, key256, Algorithm.HS256)
        assertTrue(result.isLeft && result.left.exists(_.isInstanceOf[JwtError.InvalidToken]))
      }
    ),
    suite("limits")(
      test("token too large returns TokenTooLarge") {
        val limits = JwtLimits(maxTokenChars = 10)
        val token  = "a.b." + ("c" * 8)
        val result = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(limits = limits))
        assertTrue(result match {
          case Left(_: JwtError.TokenTooLarge) => true
          case _                               => false
        })
      },
      test("segment too large returns SegmentTooLarge") {
        val limits  = JwtLimits(maxSegmentChars = 2, maxTokenChars = 100)
        val header  = Base64Url.encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes("UTF-8"))
        val payload = Base64Url.encode("{\"sub\":\"x\"}".getBytes("UTF-8"))
        val sig     = Base64Url.encode(Array.fill(16)(0x01.toByte))
        val token   = header + "." + payload + "." + sig
        val result  = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(limits = limits))
        assertTrue(result match {
          case Left(_: JwtError.SegmentTooLarge) => true
          case _                                 => false
        })
      },
      test("json too large returns JsonTooLarge") {
        val limits = JwtLimits(maxJsonChars = 5, maxSegmentChars = 4096, maxTokenChars = 8192)
        // header is small, payload JSON will be large
        val largeClaims = JwtClaims(extra = Map("x" -> JwtValue.Str("a" * 100)))
        val token       = Jwt
          .sign(largeClaims, key256, Algorithm.HS256, JwtSignOptions(limits = JwtLimits(maxJsonChars = 8192)))
          .getOrElse("")
        // Now decode with tiny json limits - should trigger JsonTooLarge on payload after verify
        val result = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(limits = limits))
        assertTrue(result match {
          case Left(_: JwtError.JsonTooLarge) => true
          case _                              => false
        })
      },
      test("depth exceeded returns TooDeep") {
        val limits = JwtLimits(maxDepth = 2)
        // depth 4: {"a": {"b": {"c": 1}}}
        val deep   = JwtValue.Obj(Map("a" -> JwtValue.Obj(Map("b" -> JwtValue.Obj(Map("c" -> JwtValue.Num("1")))))))
        val claims = JwtClaims(extra = Map("deep" -> deep))
        // sign with default limits (depth 32) succeeds
        val token  = Jwt.sign(claims, key256, Algorithm.HS256, JwtSignOptions(limits = JwtLimits.Default)).getOrElse("")
        val result = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(limits = limits))
        assertTrue(result match {
          case Left(_: JwtError.TooDeep) => true
          case _                         => false
        })
      },
      test("too many fields returns TooManyFields") {
        val limits = JwtLimits(maxFields = 2)
        val fields = (0 until 5).map(i => s"k$i" -> (JwtValue.Str("v"): JwtValue)).toMap
        val claims = JwtClaims(extra = fields)
        val token  = Jwt.sign(claims, key256, Algorithm.HS256, JwtSignOptions(limits = JwtLimits.Default)).getOrElse("")
        val result = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(limits = limits))
        assertTrue(result match {
          case Left(_: JwtError.TooManyFields) => true
          case _                               => false
        })
      },
      test("too many array elements returns TooManyElements") {
        val limits = JwtLimits(maxArrayElements = 2)
        val arr    = JwtValue.Arr(JwtValue.Num("1"), JwtValue.Num("2"), JwtValue.Num("3"))
        val claims = JwtClaims(extra = Map("arr" -> arr))
        val token  = Jwt.sign(claims, key256, Algorithm.HS256, JwtSignOptions(limits = JwtLimits.Default)).getOrElse("")
        val result = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(limits = limits))
        assertTrue(result match {
          case Left(_: JwtError.TooManyElements) => true
          case _                                 => false
        })
      },
      test("deep malformed arrays cannot StackOverflow - rejected via TooDeep") {
        val limits = JwtLimits(maxDepth = 5)
        // Create deep array nesting: [[[[[1]]]]]
        var json = "1"
        var d    = 0
        while (d < 10) {
          json = "[" + json + "]"
          d += 1
        }
        val payload      = Base64Url.encode(("{\"a\":" + json + "}").getBytes("UTF-8"))
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token  = signingInput + "." + sig
        val result = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(limits = limits))
        assertTrue(result match {
          case Left(_: JwtError.TooDeep) => true
          case _                         => false
        })
      },
      test("empty header segment returns InvalidToken") {
        val payload = Base64Url.encode("{\"sub\":\"x\"}".getBytes("UTF-8"))
        val sig     = Base64Url.encode(Array.fill(16)(0x01.toByte))
        val token   = "." + payload + "." + sig
        val result  = Jwt.decode(token, key256, Algorithm.HS256)
        assertTrue(result match { case Left(_: JwtError.InvalidToken) => true; case _ => false })
      },
      test("empty payload segment returns InvalidToken") {
        val header = Base64Url.encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes("UTF-8"))
        val sig    = Base64Url.encode(Array.fill(16)(0x01.toByte))
        val token  = header + ".." + sig
        val result = Jwt.decode(token, key256, Algorithm.HS256)
        assertTrue(result match { case Left(_: JwtError.InvalidToken) => true; case _ => false })
      },
      test("empty signature segment returns InvalidToken") {
        val header  = Base64Url.encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes("UTF-8"))
        val payload = Base64Url.encode("{\"sub\":\"x\"}".getBytes("UTF-8"))
        val token   = header + "." + payload + "."
        val result  = Jwt.decode(token, key256, Algorithm.HS256)
        assertTrue(result match { case Left(_: JwtError.InvalidToken) => true; case _ => false })
      }
    ),
    suite("header alg handling")(
      test("unknown alg returns UnsupportedAlgorithm") {
        val headerJson   = "{\"alg\":\"FOO\",\"typ\":\"JWT\"}"
        val header       = Base64Url.encode(headerJson.getBytes("UTF-8"))
        val payload      = Base64Url.encode("{\"sub\":\"x\"}".getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = Base64Url.encode(Array.fill(16)(0x01.toByte))
        val token        = signingInput + "." + sig
        val result       = Jwt.decode(token, key256, Algorithm.HS256)
        assertTrue(result match {
          case Left(_: JwtError.UnsupportedAlgorithm) => true
          case _                                      => false
        })
      },
      test("missing alg returns MissingClaim") {
        val headerJson = "{\"typ\":\"JWT\"}"
        val header     = Base64Url.encode(headerJson.getBytes("UTF-8"))
        val payload    = Base64Url.encode("{\"sub\":\"x\"}".getBytes("UTF-8"))
        val sig        = Base64Url.encode(Array.fill(16)(0x01.toByte))
        val token      = header + "." + payload + "." + sig
        val result     = Jwt.decode(token, key256, Algorithm.HS256)
        assertTrue(result match {
          case Left(JwtError.MissingClaim("alg")) => true
          case _                                  => false
        })
      },
      test("empty segments with dots returns InvalidToken") {
        val result = Jwt.decode("..", key256, Algorithm.HS256)
        assertTrue(result match {
          case Left(e: JwtError.InvalidToken) => e.reason.contains("three dot-separated")
          case _                              => false
        })
      }
    ),
    suite("aud handling")(
      test("aud single string valid") {
        val claims = JwtClaims(aud = Some(JwtAudience.Single("aud1")))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.aud) == Right(Some(JwtAudience.Single("aud1"))))
      },
      test("aud array of strings valid") {
        val claims = JwtClaims(aud = Some(JwtAudience.Multiple(Chunk("a", "b"))))
        val result = Jwt.sign(claims, key256, Algorithm.HS256).flatMap(t => Jwt.decode(t, key256, Algorithm.HS256))
        assertTrue(result.map(_.aud) == Right(Some(JwtAudience.Multiple(Chunk("a", "b")))))
      },
      test("aud invalid - array with non-string returns InvalidClaim on decode") {
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val payloadJson  = "{\"aud\":[\"a\", 123]}"
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
      test("aud invalid - number returns InvalidClaim") {
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val payloadJson  = "{\"aud\":123}"
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
      }
    ),
    suite("audience validation via expectedAudience")(
      test("expectedAudience matches single string aud") {
        val claims = JwtClaims(aud = Some(JwtAudience.Single("my-audience")))
        val token  = Jwt.sign(claims, key256, Algorithm.HS256).getOrElse("")
        val result =
          Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(expectedAudience = Some("my-audience")))
        assertTrue(result.isRight)
      },
      test("expectedAudience matches array containing audience") {
        val claims = JwtClaims(aud = Some(JwtAudience.Multiple(Chunk("aud1", "my-audience", "aud2"))))
        val token  = Jwt.sign(claims, key256, Algorithm.HS256).getOrElse("")
        val result =
          Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(expectedAudience = Some("my-audience")))
        assertTrue(result.isRight)
      },
      test("expectedAudience missing aud returns MissingClaim") {
        val claims = JwtClaims(sub = Some("s"))
        val token  = Jwt.sign(claims, key256, Algorithm.HS256).getOrElse("")
        val result =
          Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(expectedAudience = Some("my-audience")))
        assertTrue(result match {
          case Left(JwtError.MissingClaim("aud")) => true
          case _                                  => false
        })
      },
      test("expectedAudience mismatch returns InvalidClaim") {
        val claims = JwtClaims(aud = Some(JwtAudience.Single("other-audience")))
        val token  = Jwt.sign(claims, key256, Algorithm.HS256).getOrElse("")
        val result =
          Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(expectedAudience = Some("my-audience")))
        assertTrue(result match {
          case Left(_: JwtError.InvalidClaim) => true
          case _                              => false
        })
      },
      test("expectedAudience mismatch for array returns InvalidClaim") {
        val claims = JwtClaims(aud = Some(JwtAudience.Multiple(Chunk("a", "b"))))
        val token  = Jwt.sign(claims, key256, Algorithm.HS256).getOrElse("")
        val result =
          Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(expectedAudience = Some("my-audience")))
        assertTrue(result match {
          case Left(_: JwtError.InvalidClaim) => true
          case _                              => false
        })
      },
      test("no expectedAudience passes even without aud") {
        val claims = JwtClaims(sub = Some("s"))
        val token  = Jwt.sign(claims, key256, Algorithm.HS256).getOrElse("")
        val result = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(expectedAudience = None))
        assertTrue(result.isRight)
      }
    ),
    suite("JwtLimits validation")(
      test("JwtLimits rejects non-positive maxTokenChars") {
        val result = try {
          JwtLimits(maxTokenChars = 0)
          false
        } catch {
          case _: IllegalArgumentException => true
        }
        assertTrue(result)
      },
      test("JwtLimits rejects non-positive maxDepth") {
        val result = try {
          JwtLimits(maxDepth = -1)
          false
        } catch {
          case _: IllegalArgumentException => true
        }
        assertTrue(result)
      },
      test("JwtLimits rejects non-positive maxArrayElements") {
        val result = try {
          JwtLimits(maxArrayElements = 0)
          false
        } catch {
          case _: IllegalArgumentException => true
        }
        assertTrue(result)
      }
    ),
    suite("InvalidKey for weak HMAC")(
      test("weak HMAC key returns InvalidKey not InvalidToken") {
        val weakKey = Array.fill(10)(0x01.toByte)
        val claims  = JwtClaims(sub = Some("s"))
        val result  = Jwt.sign(claims, weakKey, Algorithm.HS256)
        assertTrue(result match {
          case Left(_: JwtError.InvalidKey) => true
          case _                            => false
        })
      }
    ),
    suite("NumericDate fractional/exponent")(
      test("fractional exp truncated toward zero (floor)") {
        val header = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        // exp = 1500.999 should be treated as 1500
        val payloadJson  = "{\"exp\":1500.999}"
        val payload      = Base64Url.encode(payloadJson.getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token = signingInput + "." + sig
        // now 1500 should be valid (exp floor 1500), now 1501 should be expired
        val ok      = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(nowSeconds = Some(1500L)))
        val expired = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(nowSeconds = Some(1501L)))
        assertTrue(ok.isRight) && assertTrue(expired match {
          case Left(_: JwtError.ExpiredToken) => true
          case _                              => false
        })
      },
      test("exponent notation accepted") {
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val payloadJson  = "{\"exp\":1.5e3}"
        val payload      = Base64Url.encode(payloadJson.getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token  = signingInput + "." + sig
        val claims = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(nowSeconds = Some(1400L)))
        assertTrue(claims.map(_.exp) == Right(Some(1500L)))
      },
      test("negative NumericDate rejected") {
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val payloadJson  = "{\"exp\":-100}"
        val payload      = Base64Url.encode(payloadJson.getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token  = signingInput + "." + sig
        val result = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(nowSeconds = Some(0L)))
        assertTrue(result match {
          case Left(_: JwtError.InvalidClaim) => true
          case _                              => false
        })
      },
      test("out of range NumericDate rejected") {
        val header = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        // Long.MaxValue + 1
        val payloadJson  = "{\"exp\":9223372036854775808}"
        val payload      = Base64Url.encode(payloadJson.getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token  = signingInput + "." + sig
        val result = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(nowSeconds = Some(0L)))
        assertTrue(result match {
          case Left(_: JwtError.InvalidClaim) => true
          case _                              => false
        })
      },
      test("integer NumericDate still works") {
        val claims = JwtClaims(exp = Some(2000L))
        val token  = Jwt.sign(claims, key256, Algorithm.HS256).getOrElse("")
        val result = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(nowSeconds = Some(1999L)))
        assertTrue(result.isRight)
      },
      test("fractional nbf handling") {
        val header       = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(JwtHeader(Algorithm.HS256))))
        val payloadJson  = "{\"nbf\":1000.1}"
        val payload      = Base64Url.encode(payloadJson.getBytes("UTF-8"))
        val signingInput = header + "." + payload
        val sig          = JwtCrypto.backend
          .sign(JwtText.encodeUtf8(signingInput), key256, Algorithm.HS256)
          .map(Base64Url.encode)
          .getOrElse("")
        val token  = signingInput + "." + sig
        val before = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(nowSeconds = Some(999L)))
        val after  = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(nowSeconds = Some(1000L)))
        assertTrue(before match {
          case Left(_: JwtError.NotYetValid) => true
          case _                             => false
        }) && assertTrue(after.isRight)
      }
    ),
    suite("deterministic now and explicit backend")(
      test("deterministic nowSeconds is used instead of system clock") {
        val pastExp = 1000L
        val claims  = JwtClaims(exp = Some(pastExp))
        val token   = Jwt.sign(claims, key256, Algorithm.HS256).getOrElse("")
        // system time is now >> 1000, but we pass now=500 should be valid
        val withOldNow = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(nowSeconds = Some(500L)))
        val withNewNow = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(nowSeconds = Some(2000L)))
        assertTrue(withOldNow.isRight) && assertTrue(withNewNow match {
          case Left(_: JwtError.ExpiredToken) => true
          case _                              => false
        })
      },
      test("explicit backend is snapshot once per operation") {
        var signCalls       = 0
        var verifyCalls     = 0
        val countingBackend = new JwtCryptoBackend {
          val supportedAlgorithms: Set[Algorithm]                                                      = Set(Algorithm.HS256)
          def sign(data: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Array[Byte]] = {
            signCalls += 1
            Right(HmacSha2.hmacSha256(key, data))
          }
          def verify(
            data: Array[Byte],
            signature: Array[Byte],
            key: Array[Byte],
            alg: Algorithm
          ): Either[JwtError, Boolean] = {
            verifyCalls += 1
            Right(java.util.Arrays.equals(HmacSha2.hmacSha256(key, data), signature))
          }
        }
        val claims = JwtClaims(sub = Some("test"))
        val token  =
          Jwt.sign(claims, key256, Algorithm.HS256, JwtSignOptions(backend = Some(countingBackend))).getOrElse("")
        assertTrue(signCalls == 1) &&
        {
          val decoded = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(backend = Some(countingBackend)))
          assertTrue(verifyCalls == 1) && assertTrue(decoded.isRight)
        }
      },
      test("explicit backend verify failure returns InvalidSignature") {
        val failingBackend = new JwtCryptoBackend {
          val supportedAlgorithms: Set[Algorithm]                                                      = Set(Algorithm.HS256)
          def sign(data: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Array[Byte]] =
            Right(HmacSha2.hmacSha256(key, data))
          def verify(
            data: Array[Byte],
            signature: Array[Byte],
            key: Array[Byte],
            alg: Algorithm
          ): Either[JwtError, Boolean] =
            Right(false)
        }
        val claims = JwtClaims(sub = Some("test"))
        val token  =
          Jwt.sign(claims, key256, Algorithm.HS256, JwtSignOptions(backend = Some(failingBackend))).getOrElse("")
        val result = Jwt.decode(token, key256, Algorithm.HS256, JwtDecodeOptions(backend = Some(failingBackend)))
        assertTrue(result match {
          case Left(_: JwtError.InvalidSignature) => true
          case _                                  => false
        })
      },
      test("global backend remains source-compatible") {
        val current     = JwtCrypto.backend
        var called      = false
        val testBackend = new JwtCryptoBackend {
          val supportedAlgorithms: Set[Algorithm]                                                      = Set(Algorithm.HS256)
          def sign(data: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Array[Byte]] = {
            called = true; Right(HmacSha2.hmacSha256(key, data))
          }
          def verify(
            data: Array[Byte],
            signature: Array[Byte],
            key: Array[Byte],
            alg: Algorithm
          ): Either[JwtError, Boolean] =
            Right(java.util.Arrays.equals(HmacSha2.hmacSha256(key, data), signature))
        }
        val claims        = JwtClaims(sub = Some("test"))
        val tokenExplicit =
          Jwt.sign(claims, key256, Algorithm.HS256, JwtSignOptions(backend = Some(testBackend))).getOrElse("")
        assertTrue(called) && assertTrue(JwtCrypto.backend == current) && assertTrue(tokenExplicit.nonEmpty)
      }
    ),
    suite("decodeWithoutVerification")(
      test("decodeWithoutVerification parses without verifying") {
        val claims = JwtClaims(sub = Some("test"))
        val token  = Jwt.sign(claims, key256, Algorithm.HS256).getOrElse("")
        val result = Jwt.decodeWithoutVerification(token)
        assertTrue(result.isRight)
      },
      test("decodeUnsafe alias still works") {
        val claims = JwtClaims(sub = Some("test"))
        val token  = Jwt.sign(claims, key256, Algorithm.HS256).getOrElse("")
        val result = Jwt.decodeUnsafe(token)
        assertTrue(result.isRight)
      }
    )
  )
}
