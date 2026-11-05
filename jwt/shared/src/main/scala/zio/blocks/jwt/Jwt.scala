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

import scala.collection.mutable
import zio.blocks.chunk.Chunk

/**
 * The signature operations that [[Jwt]] delegates to: computing and checking
 * the signature of a token for one [[Algorithm]].
 *
 * Three backends come with the module. The default one, active until another is
 * installed, is written in pure Scala and supports `HS256`, `HS384` and `HS512`
 * only. `JvmJwtCryptoBackend` (JVM, on the JDK's `java.security`) supports all
 * 13 algorithms of [[Algorithm]], and `JsJwtCryptoBackend` (Scala.js, on
 * Node.js's `crypto` module) supports the `HS*`, `RS*` and `ES*` algorithms.
 * Install a platform backend once at startup with its `install()` method, or
 * pass one for a single call in [[JwtSignOptions]] or [[JwtDecodeOptions]];
 * [[JwtCrypto]] holds the installed one.
 *
 * @see
 *   [[https://zioblocks.com/reference/jwt/#supported-algorithms-and-key-strength JWT]]
 */
trait JwtCryptoBackend {

  /**
   * The algorithms this backend can sign and verify with. `Jwt.sign` and
   * `Jwt.decode` do not consult this set; the three built-in backends return
   * `Left(JwtError.UnsupportedAlgorithm(...))` from `sign` and `verify` for an
   * algorithm they do not support.
   */
  def supportedAlgorithms: Set[Algorithm]

  /**
   * Computes the signature of `data` (the bytes of `header.payload`) with the
   * private key or secret `key` for `alg`, and returns the raw signature bytes.
   * For the `ES*` algorithms these are the fixed-length `r || s` bytes that JWS
   * uses (64, 96 or 132 bytes), not DER. Returns a `Left`, such as
   * `JwtError.InvalidKey` or `JwtError.UnsupportedAlgorithm`, when the key or
   * algorithm cannot be used.
   */
  def sign(data: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Array[Byte]]

  /**
   * Checks that `signature` is a signature of `data` for the secret or public
   * key `key` and `alg`. Returns `Right(false)` for a signature that does not
   * match, and a `Left` when the key or the signature bytes cannot be used, for
   * example `JwtError.InvalidKey` for an HMAC key that is too short.
   */
  def verify(data: Array[Byte], signature: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Boolean]
}

/**
 * The global holder of the active [[JwtCryptoBackend]], used by every `Jwt`
 * call that does not pass its own backend. Such a call reads the holder once,
 * when it starts.
 *
 * It starts with the built-in pure Scala backend, which handles the HMAC
 * algorithms (`HS256`, `HS384`, `HS512`) only. Call
 * `JvmJwtCryptoBackend.install()` (JVM) or `JsJwtCryptoBackend.install()`
 * (Scala.js) once at application startup to use the other algorithms. The
 * holder is an atomic reference, so a backend installed on one thread is seen
 * by the others.
 *
 * {{{
 * JwtCrypto.backend.supportedAlgorithms.contains(Algorithm.RS256) // false
 * JvmJwtCryptoBackend.install()
 * JwtCrypto.backend.supportedAlgorithms.contains(Algorithm.RS256) // true
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/jwt/#getting-started JWT]]
 */
object JwtCrypto {
  private val backendRef =
    new java.util.concurrent.atomic.AtomicReference[JwtCryptoBackend](SharedJwtCryptoBackend)

  /** Returns the active backend. */
  def backend: JwtCryptoBackend = backendRef.get()

  /** Replaces the active backend; the same as `installBackend`. */
  def backend_=(impl: JwtCryptoBackend): Unit = backendRef.set(impl)

  /**
   * Replaces the active backend with `impl` for every later call that does not
   * pass its own backend. Calls already running keep the backend they started
   * with.
   */
  def installBackend(impl: JwtCryptoBackend): Unit = backendRef.set(impl)

  /** Replaces the active backend with `impl`; the same as `installBackend`. */
  def install(impl: JwtCryptoBackend): Unit = backendRef.set(impl)

  /**
   * Replaces the active backend with `updated` only if it is `expected`
   * (compared by reference), in one atomic step, and returns whether it did.
   */
  def compareAndSet(expected: JwtCryptoBackend, updated: JwtCryptoBackend): Boolean =
    backendRef.compareAndSet(expected, updated)

  /**
   * Replaces the active backend with `impl` in one atomic step and returns the
   * backend it replaced, for example to restore it after a test.
   */
  def getAndSet(impl: JwtCryptoBackend): JwtCryptoBackend = backendRef.getAndSet(impl)
}

/**
 * Size and shape limits for a token, so that an oversized or deeply nested
 * token is rejected with a [[JwtError]] instead of being parsed in full.
 *
 * `Jwt.decode` and `Jwt.decodeWithoutVerification` apply all six limits;
 * `JwtHeader.parse` and `JwtClaims.parse` apply all but `maxTokenChars`. On
 * decode, the three size limits run before any parsing, and the header's JSON
 * limits before the signature check. `Jwt.sign` applies only the three size
 * limits (`maxTokenChars`, `maxSegmentChars`, `maxJsonChars`), a known bug, so
 * it can sign a token that a decoder with the same limits rejects. Every value
 * is an inclusive maximum, and the constructor throws
 * `IllegalArgumentException` ("requirement failed: maxDepth must be positive")
 * for a value that is not positive.
 *
 * {{{
 * val key   = "demo-secret-do-not-use-0123456789".getBytes("UTF-8")
 * val token = Jwt.sign(JwtClaims(sub = Some("user-42")), key, Algorithm.HS256)
 *
 * token.flatMap(t => Jwt.decodeWithoutVerification(t, JwtLimits(maxTokenChars = 64))).left.map(_.message)
 * // Left(JWT token too large: 104 chars exceeds limit 64)
 * }}}
 *
 * @param maxTokenChars
 *   the maximum length of the whole compact token, in characters (default
 *   8192); returns `TokenTooLarge` when exceeded
 * @param maxSegmentChars
 *   the maximum length of each of the three Base64URL segments, in characters
 *   (default 4096); returns `SegmentTooLarge`
 * @param maxJsonChars
 *   the maximum length of the header JSON and of the payload JSON, each in
 *   characters after Base64URL decoding (default 8192); returns `JsonTooLarge`
 * @param maxDepth
 *   the maximum nesting depth of the header and payload JSON (default 32);
 *   returns `TooDeep`. The top-level object is depth 1, a value in it depth 2,
 *   and a value inside a nested array or object one level more. A nested object
 *   counts one level deeper than its position even when it is empty, a known
 *   bug, so `{"a":[]}` passes with `maxDepth = 2` but `{"a":{}}` needs 3.
 * @param maxFields
 *   the maximum number of fields in any one JSON object, the header and the
 *   payload included (default 512); returns `TooManyFields`. The registered
 *   claims count, and a repeated field name counts once.
 * @param maxArrayElements
 *   the maximum number of elements in any one JSON array (default 512); returns
 *   `TooManyElements`
 *
 * @see
 *   [[https://zioblocks.com/reference/jwt/#limits-and-options JWT]]
 */
final case class JwtLimits(
  maxTokenChars: Int = 8192,
  maxSegmentChars: Int = 4096,
  maxJsonChars: Int = 8192,
  maxDepth: Int = 32,
  maxFields: Int = 512,
  maxArrayElements: Int = 512
) {
  require(maxTokenChars > 0, "maxTokenChars must be positive")
  require(maxSegmentChars > 0, "maxSegmentChars must be positive")
  require(maxJsonChars > 0, "maxJsonChars must be positive")
  require(maxDepth > 0, "maxDepth must be positive")
  require(maxFields > 0, "maxFields must be positive")
  require(maxArrayElements > 0, "maxArrayElements must be positive")
}

/** Holds the default [[JwtLimits]]. */
object JwtLimits {

  /**
   * The default limits: `maxTokenChars = 8192`, `maxSegmentChars = 4096`,
   * `maxJsonChars = 8192`, `maxDepth = 32`, `maxFields = 512` and
   * `maxArrayElements = 512`.
   */
  val Default: JwtLimits = JwtLimits()
}

/**
 * The checks and settings of one `Jwt.decode` call, beyond the token, key and
 * algorithm.
 *
 * After the signature verifies, `decode` checks the claims in this order and
 * returns the first failure: `exp`, `nbf`, then `iss` (if `issuer` is set) and
 * `aud` (if `expectedAudience` is set). `exp` and `nbf` are checked only when
 * the token has them, so a token without `exp` never expires, and `iat` is not
 * checked. The audience check follows RFC 7519 §4.1.3: the token's `aud` must
 * be present (else `MissingClaim("aud")`) and equal the expected value, or
 * contain it when `aud` is an array (else `InvalidClaim("aud", ...)`, with the
 * reason "expected notes-api but found billing-api").
 *
 * {{{
 * val key   = "demo-secret-do-not-use-0123456789".getBytes("UTF-8")
 * val token = Jwt.sign(
 *   JwtClaims(iss = Some("https://auth.example.com"), aud = Some(JwtAudience.single("billing-api"))),
 *   key,
 *   Algorithm.HS256
 * )
 * val options = JwtDecodeOptions(
 *   issuer = Some("https://auth.example.com"),
 *   expectedAudience = Some("notes-api"),
 *   clockSkewSeconds = 30L
 * )
 *
 * token.flatMap(t => Jwt.decode(t, key, Algorithm.HS256, options)).left.map(_.message)
 * // Left(Invalid JWT claim 'aud': expected notes-api but found billing-api)
 * }}}
 *
 * @param clockSkewSeconds
 *   the tolerance, in seconds, for the time checks (default 0): a token is
 *   expired when `now - clockSkewSeconds > exp` and not yet valid when `now +
 *   clockSkewSeconds < nbf`. A token whose `exp` equals `now` still passes with
 *   no skew, a known bug, since RFC 7519 requires `now` to be before `exp`. A
 *   negative value is not rejected and narrows the window instead.
 * @param issuer
 *   if set, the `iss` claim must be present (else `MissingClaim("iss")`) and
 *   equal this value, case-sensitively (else `InvalidClaim("iss", ...)`)
 * @param expectedAudience
 *   if set, the `aud` claim must be present and equal this value or, as an
 *   array, contain it, case-sensitively
 * @param backend
 *   the backend for this call; `None` uses [[JwtCrypto.backend]], read once
 *   when the call starts
 * @param nowSeconds
 *   the current time, in seconds since the Unix epoch, for the `exp` and `nbf`
 *   checks; `None` uses `System.currentTimeMillis() / 1000`. Fix it in tests.
 * @param limits
 *   the size and shape limits for the token
 *
 * @see
 *   [[https://zioblocks.com/reference/jwt/#decoding-and-verifying JWT]]
 */
final case class JwtDecodeOptions(
  clockSkewSeconds: Long = 0L,
  issuer: Option[String] = None,
  expectedAudience: Option[String] = None,
  backend: Option[JwtCryptoBackend] = None,
  nowSeconds: Option[Long] = None,
  limits: JwtLimits = JwtLimits.Default
)

/** Holds the default [[JwtDecodeOptions]]. */
object JwtDecodeOptions {

  /**
   * The options of `Jwt.decode(token, key, alg)`: no clock skew, no issuer or
   * audience check, the global backend, the system clock and
   * [[JwtLimits.Default]].
   */
  val Default: JwtDecodeOptions = JwtDecodeOptions()
}

/**
 * The settings of one `Jwt.sign` call, beyond the claims, key and algorithm.
 *
 * {{{
 * val key = "demo-secret-do-not-use-0123456789".getBytes("UTF-8")
 *
 * Jwt.sign(JwtClaims(sub = Some("user-42")), key, Algorithm.HS256, JwtSignOptions(limits = JwtLimits(maxTokenChars = 64)))
 *   .left
 *   .map(_.message)
 * // Left(JWT token too large: 104 chars exceeds limit 64)
 * }}}
 *
 * @param backend
 *   the backend for this call; `None` uses [[JwtCrypto.backend]], read once
 *   when the call starts
 * @param limits
 *   the size limits for the token: `sign` returns `JsonTooLarge`,
 *   `SegmentTooLarge` or `TokenTooLarge` when the rendered header or claims
 *   JSON, a segment or the whole token would exceed `maxJsonChars`,
 *   `maxSegmentChars` or `maxTokenChars`. It does not apply `maxDepth`,
 *   `maxFields` and `maxArrayElements`, a known bug.
 *
 * @see
 *   [[https://zioblocks.com/reference/jwt/#limits-and-options JWT]]
 */
final case class JwtSignOptions(
  backend: Option[JwtCryptoBackend] = None,
  limits: JwtLimits = JwtLimits.Default
)

/** Holds the default [[JwtSignOptions]]. */
object JwtSignOptions {

  /**
   * The options of `Jwt.sign(claims, key, alg)`: the global backend and
   * [[JwtLimits.Default]].
   */
  val Default: JwtSignOptions = JwtSignOptions()
}

/**
 * Signs and verifies JSON Web Tokens (RFC 7519) in the compact JWS form
 * `header.payload.signature`, each part Base64URL-encoded without padding.
 *
 * `sign` renders a [[JwtHeader]] and [[JwtClaims]] as JSON, encodes them and
 * signs them with a key; `decode` checks a token and returns its claims; and
 * `decodeWithoutVerification` reads a token without checking it. Each operation
 * returns its failure as a `Left` of [[JwtError]]. The signing work is done by
 * a [[JwtCryptoBackend]]: the HMAC algorithms (`HS256`, `HS384`, `HS512`) work
 * with the built-in default, and the others need a platform backend.
 *
 * The caller, not the token, chooses the algorithm: `decode` rejects a token
 * whose header names another algorithm with `AlgorithmMismatch`, and one whose
 * header names an algorithm this module does not know, such as `none`, with
 * `UnsupportedAlgorithm`.
 *
 * ==Minimal usage==
 * {{{
 * JvmJwtCryptoBackend.install() // once at startup
 * val key     = "demo-secret-do-not-use-0123456789".getBytes("UTF-8")
 * val claims  = JwtClaims(sub = Some("user-42"), iss = Some("https://auth.example.com"))
 * val token   = Jwt.sign(claims, key, Algorithm.HS256).getOrElse(throw new RuntimeException("cannot sign"))
 * val decoded = Jwt.decode(token, key, Algorithm.HS256).getOrElse(throw new RuntimeException("invalid token"))
 * decoded.sub // Some(user-42)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/jwt/ JWT]]
 */
object Jwt {

  /**
   * Signs `claims` with `key` and `alg` and returns the compact token. The
   * header is `JwtHeader(alg)`, rendered as `{"alg":"HS256","typ":"JWT"}` for
   * `HS256`, and the backend is [[JwtCrypto.backend]].
   *
   * For the `HS*` algorithms `key` is the shared secret, at least 32, 48 or 64
   * bytes long for `HS256`, `HS384` or `HS512`; for the others it is the
   * private key in PKCS#8 DER form. A shorter secret, or a key the backend
   * cannot use, gives `Left(JwtError.InvalidKey(...))`, and an algorithm the
   * backend does not support gives `Left(JwtError.UnsupportedAlgorithm(...))`.
   * A registered claim name in `claims.extra` is written a second time after
   * the typed field, and decoders read the later value, a known bug.
   *
   * {{{
   * val key    = "demo-secret-do-not-use-0123456789".getBytes("UTF-8")
   * val claims = JwtClaims(sub = Some("user-42"), exp = Some(1767225600L))
   *
   * Jwt.sign(claims, key, Algorithm.HS256).map(_.split('.').length) // Right(3)
   * Jwt.sign(claims, "too-short".getBytes("UTF-8"), Algorithm.HS256).isLeft // true
   * }}}
   */
  def sign(
    claims: JwtClaims,
    key: Array[Byte],
    alg: Algorithm
  ): Either[JwtError, String] =
    sign(claims, key, alg, JwtHeader(alg))

  /**
   * Signs `claims` with `key` and `alg` under `header`, for example to set a
   * `kid` or another `typ`. Returns
   * `Left(JwtError.AlgorithmMismatch(alg.name, header.alg.name))` when
   * `header.alg` is not `alg`.
   */
  def sign(
    claims: JwtClaims,
    key: Array[Byte],
    alg: Algorithm,
    header: JwtHeader
  ): Either[JwtError, String] =
    sign(claims, key, alg, header, JwtSignOptions.Default)

  /**
   * Signs `claims` with `key` and `alg` under `header`, using `backend` instead
   * of [[JwtCrypto.backend]].
   */
  def sign(
    claims: JwtClaims,
    key: Array[Byte],
    alg: Algorithm,
    header: JwtHeader,
    backend: JwtCryptoBackend
  ): Either[JwtError, String] =
    sign(claims, key, alg, header, JwtSignOptions(backend = Some(backend)))

  /**
   * Signs `claims` with `key` and `alg` under `JwtHeader(alg)` and `options`.
   */
  def sign(
    claims: JwtClaims,
    key: Array[Byte],
    alg: Algorithm,
    options: JwtSignOptions
  ): Either[JwtError, String] =
    sign(claims, key, alg, JwtHeader(alg), options)

  /**
   * Signs `claims` with `key` and `alg` under `header` and `options`; the other
   * `sign` overloads delegate to this one.
   *
   * The checks run in this order, and the first failure is returned:
   * `header.alg` against `alg` (`AlgorithmMismatch`), the length of the header
   * and claims JSON (`JsonTooLarge`), of the encoded header and payload
   * (`SegmentTooLarge`) and of the unsigned token (`TokenTooLarge`), then the
   * backend's signature (for example `InvalidKey`), and last the length of the
   * signature segment and of the finished token.
   */
  def sign(
    claims: JwtClaims,
    key: Array[Byte],
    alg: Algorithm,
    header: JwtHeader,
    options: JwtSignOptions
  ): Either[JwtError, String] =
    if (header.alg != alg) Left(JwtError.AlgorithmMismatch(alg.name, header.alg.name))
    else {
      val backend    = options.backend.getOrElse(JwtCrypto.backend)
      val limits     = options.limits
      val headerJson = JwtHeader.render(header)
      val claimsJson = JwtClaims.render(claims)
      if (headerJson.length > limits.maxJsonChars)
        Left(JwtError.JsonTooLarge(headerJson.length, limits.maxJsonChars))
      else if (claimsJson.length > limits.maxJsonChars)
        Left(JwtError.JsonTooLarge(claimsJson.length, limits.maxJsonChars))
      else {
        val headerSegment  = Base64Url.encode(JwtText.encodeUtf8(headerJson))
        val payloadSegment = Base64Url.encode(JwtText.encodeUtf8(claimsJson))
        if (headerSegment.length > limits.maxSegmentChars)
          Left(JwtError.SegmentTooLarge(headerSegment.length, limits.maxSegmentChars))
        else if (payloadSegment.length > limits.maxSegmentChars)
          Left(JwtError.SegmentTooLarge(payloadSegment.length, limits.maxSegmentChars))
        else {
          val totalLen = headerSegment.length + 1 + payloadSegment.length
          if (totalLen > limits.maxTokenChars)
            Left(JwtError.TokenTooLarge(totalLen, limits.maxTokenChars))
          else {
            val signingInput = headerSegment + "." + payloadSegment
            backend.sign(JwtText.encodeUtf8(signingInput), key, alg).flatMap { signature =>
              val sigSegment = Base64Url.encode(signature)
              if (sigSegment.length > limits.maxSegmentChars)
                Left(JwtError.SegmentTooLarge(sigSegment.length, limits.maxSegmentChars))
              else {
                val token = signingInput + "." + sigSegment
                if (token.length > limits.maxTokenChars)
                  Left(JwtError.TokenTooLarge(token.length, limits.maxTokenChars))
                else Right(token)
              }
            }
          }
        }
      }
    }

  /**
   * Verifies `token` with `key` for `alg` and returns its claims, using the
   * global backend, the system clock and [[JwtLimits.Default]].
   *
   * It is the same as `decode(token, key, alg, options)` with
   * `JwtDecodeOptions(clockSkewSeconds = clockSkewSeconds, issuer = issuer)`;
   * use that overload for an audience check or a fixed clock.
   *
   * {{{
   * val key   = "demo-secret-do-not-use-0123456789".getBytes("UTF-8")
   * val token = Jwt.sign(JwtClaims(sub = Some("user-42"), iss = Some("https://auth.example.com")), key, Algorithm.HS256)
   *
   * token.flatMap(t => Jwt.decode(t, key, Algorithm.HS256, issuer = Some("https://auth.example.com"))).map(_.sub)
   * // Right(Some(user-42))
   * token.flatMap(t => Jwt.decode(t, key, Algorithm.HS384)).left.map(_.message)
   * // Left(JWT algorithm mismatch: expected HS384 but found HS256)
   * }}}
   *
   * @param token
   *   the compact token, `header.payload.signature`
   * @param key
   *   the shared secret for the `HS*` algorithms, or the public key in X.509
   *   SubjectPublicKeyInfo DER form for the others
   * @param alg
   *   the algorithm the token must be signed with
   * @param clockSkewSeconds
   *   the tolerance, in seconds, for the `exp` and `nbf` checks
   * @param issuer
   *   if set, the `iss` claim must be present and equal this value
   */
  def decode(
    token: String,
    key: Array[Byte],
    alg: Algorithm,
    clockSkewSeconds: Long = 0L,
    issuer: Option[String] = None
  ): Either[JwtError, JwtClaims] =
    decode(token, key, alg, JwtDecodeOptions(clockSkewSeconds = clockSkewSeconds, issuer = issuer))

  /**
   * Verifies `token` with `key` for `alg` and returns its claims, using
   * `backend` instead of [[JwtCrypto.backend]] and the defaults of
   * [[JwtDecodeOptions]] otherwise.
   */
  def decode(
    token: String,
    key: Array[Byte],
    alg: Algorithm,
    backend: JwtCryptoBackend
  ): Either[JwtError, JwtClaims] =
    decode(token, key, alg, JwtDecodeOptions(backend = Some(backend)))

  /**
   * Verifies `token` with `key` for `alg` and returns its claims, using
   * `backend` and taking `nowSeconds` (seconds since the Unix epoch) as the
   * current time for the `exp` and `nbf` checks.
   */
  def decode(
    token: String,
    key: Array[Byte],
    alg: Algorithm,
    backend: JwtCryptoBackend,
    nowSeconds: Long
  ): Either[JwtError, JwtClaims] =
    decode(token, key, alg, JwtDecodeOptions(backend = Some(backend), nowSeconds = Some(nowSeconds)))

  /**
   * Verifies `token` with `key` for `alg` and `options` and returns its claims;
   * the other `decode` overloads delegate to this one.
   *
   * The checks run in this order, and the first failure is returned: the token
   * size (`TokenTooLarge`), its three non-empty segments (`InvalidToken`,
   * `SegmentTooLarge`), the header (`InvalidToken`, `UnsupportedAlgorithm` for
   * an unknown `alg`, the JSON limits), the header's `alg` against `alg`
   * (`AlgorithmMismatch`), the signature (`InvalidSignature`, or `InvalidKey`
   * for a key the backend cannot use), and only then the payload and its claims
   * (`InvalidToken`, `InvalidClaim`, `ExpiredToken`, `NotYetValid`,
   * `MissingClaim`). A tampered payload is therefore reported as
   * `InvalidSignature`. The header fields other than `alg`, `typ` and `kid` are
   * not read.
   */
  def decode(
    token: String,
    key: Array[Byte],
    alg: Algorithm,
    options: JwtDecodeOptions
  ): Either[JwtError, JwtClaims] = {
    // Snapshot backend once per operation
    val backend = options.backend.getOrElse(JwtCrypto.backend)
    val limits  = options.limits
    val now     = options.nowSeconds.getOrElse(System.currentTimeMillis() / 1000L)
    for {
      _         <- checkTokenSize(token, limits)
      parts     <- splitCompact(token, limits)
      header    <- JwtHeader.parse(parts.headerSegment, limits)
      _         <- validateAlgorithm(alg, header.alg)
      signature <- Base64Url.decode(parts.signatureSegment)
      verified  <- backend.verify(JwtText.encodeUtf8(parts.signingInput), signature, key, alg)
      _         <- if (verified) Right(()) else Left(JwtError.InvalidSignature(alg.name))
      claims    <- JwtClaims.parse(parts.payloadSegment, limits)
      _         <- validateClaims(claims, options.clockSkewSeconds, options.issuer, now)
      _         <- validateAudience(claims, options.expectedAudience)
    } yield claims
  }

  /**
   * Reads the header and claims of `token` **without** checking its signature
   * or claims, with [[JwtLimits.Default]]. It is an older name for
   * `decodeWithoutVerification`; prefer that name in new code.
   */
  def decodeUnsafe(token: String): Either[JwtError, (JwtHeader, JwtClaims)] =
    decodeWithoutVerification(token, JwtLimits.Default)

  /**
   * Reads the header and claims of `token` **without** checking its signature,
   * its algorithm or any claim (`exp`, `nbf`, `iss`, `aud`).
   *
   * It applies `limits` and the format checks of `decode`: three non-empty
   * segments, valid Base64URL, JSON objects, a known `alg` and well-typed
   * registered claims. The signature segment must be present but is not
   * decoded. Use it to inspect a token, for example to read its `kid`, and
   * verify it with `decode` before trusting any claim.
   *
   * {{{
   * val key   = "demo-secret-do-not-use-0123456789".getBytes("UTF-8")
   * val token = Jwt.sign(JwtClaims(sub = Some("user-42")), key, Algorithm.HS256, JwtHeader(Algorithm.HS256, kid = Some("key-2026-01")))
   *
   * token.flatMap(t => Jwt.decodeWithoutVerification(t)).map { case (header, claims) => (header.kid, claims.sub) }
   * // Right((Some(key-2026-01),Some(user-42)))
   * }}}
   *
   * @param token
   *   the compact token, `header.payload.signature`
   * @param limits
   *   the size and shape limits for the token
   */
  def decodeWithoutVerification(
    token: String,
    limits: JwtLimits = JwtLimits.Default
  ): Either[JwtError, (JwtHeader, JwtClaims)] =
    for {
      _      <- checkTokenSize(token, limits)
      parts  <- splitCompact(token, limits)
      header <- JwtHeader.parse(parts.headerSegment, limits)
      claims <- JwtClaims.parse(parts.payloadSegment, limits)
    } yield (header, claims)

  /**
   * Reads the header and claims of `token` **without** checking its signature
   * or claims, with `limits`; the same as `decodeWithoutVerification(token,
   * limits)`.
   */
  def decodeUnsafe(token: String, limits: JwtLimits): Either[JwtError, (JwtHeader, JwtClaims)] =
    decodeWithoutVerification(token, limits)

  private def checkTokenSize(token: String, limits: JwtLimits): Either[JwtError, Unit] =
    if (token.isEmpty) Left(JwtError.InvalidToken("JWT must have exactly three dot-separated segments"))
    else if (token.length > limits.maxTokenChars) Left(JwtError.TokenTooLarge(token.length, limits.maxTokenChars))
    else Right(())

  private def splitCompact(token: String, limits: JwtLimits): Either[JwtError, TokenParts] =
    if (token.isEmpty) Left(JwtError.InvalidToken("JWT must have exactly three dot-separated segments"))
    else {
      val firstDot  = token.indexOf('.')
      val secondDot = if (firstDot >= 0) token.indexOf('.', firstDot + 1) else -1
      val thirdDot  = if (secondDot >= 0) token.indexOf('.', secondDot + 1) else -1
      if (firstDot <= 0 || secondDot <= firstDot + 1 || thirdDot >= 0)
        Left(JwtError.InvalidToken("JWT must have exactly three dot-separated segments"))
      else if (secondDot == token.length - 1)
        Left(JwtError.InvalidToken("JWT signature segment must not be empty"))
      else {
        val headerSegment    = token.substring(0, firstDot)
        val payloadSegment   = token.substring(firstDot + 1, secondDot)
        val signatureSegment = token.substring(secondDot + 1)
        if (headerSegment.isEmpty) Left(JwtError.InvalidToken("JWT header segment must not be empty"))
        else if (payloadSegment.isEmpty) Left(JwtError.InvalidToken("JWT payload segment must not be empty"))
        else if (signatureSegment.isEmpty) Left(JwtError.InvalidToken("JWT signature segment must not be empty"))
        else if (headerSegment.length > limits.maxSegmentChars)
          Left(JwtError.SegmentTooLarge(headerSegment.length, limits.maxSegmentChars))
        else if (payloadSegment.length > limits.maxSegmentChars)
          Left(JwtError.SegmentTooLarge(payloadSegment.length, limits.maxSegmentChars))
        else if (signatureSegment.length > limits.maxSegmentChars)
          Left(JwtError.SegmentTooLarge(signatureSegment.length, limits.maxSegmentChars))
        else Right(TokenParts(headerSegment, payloadSegment, signatureSegment))
      }
    }

  private def validateAlgorithm(expected: Algorithm, found: Algorithm): Either[JwtError, Unit] =
    if (expected == found) Right(())
    else Left(JwtError.AlgorithmMismatch(expected.name, found.name))

  private def validateClaims(
    claims: JwtClaims,
    clockSkewSeconds: Long,
    issuer: Option[String],
    now: Long
  ): Either[JwtError, Unit] =
    claims.exp match {
      case Some(exp) if now - clockSkewSeconds > exp => Left(JwtError.ExpiredToken(exp, now))
      case _                                         =>
        claims.nbf match {
          case Some(nbf) if now + clockSkewSeconds < nbf => Left(JwtError.NotYetValid(nbf, now))
          case _                                         =>
            issuer match {
              case Some(expectedIssuer) =>
                claims.iss match {
                  case Some(foundIssuer) if foundIssuer == expectedIssuer => Right(())
                  case Some(foundIssuer)                                  =>
                    Left(JwtError.InvalidClaim("iss", s"expected $expectedIssuer but found $foundIssuer"))
                  case None => Left(JwtError.MissingClaim("iss"))
                }
              case None => Right(())
            }
        }
    }

  private def validateAudience(
    claims: JwtClaims,
    expectedAudience: Option[String]
  ): Either[JwtError, Unit] =
    expectedAudience match {
      case None           => Right(())
      case Some(expected) =>
        claims.aud match {
          case Some(JwtAudience.Single(single)) if single == expected          => Right(())
          case Some(JwtAudience.Multiple(values)) if values.contains(expected) => Right(())
          case Some(JwtAudience.Single(single))                                =>
            Left(JwtError.InvalidClaim("aud", s"expected $expected but found $single"))
          case Some(JwtAudience.Multiple(values)) =>
            Left(JwtError.InvalidClaim("aud", s"expected $expected but found ${values.mkString(",")}"))
          case None => Left(JwtError.MissingClaim("aud"))
        }
    }

  private final case class TokenParts(headerSegment: String, payloadSegment: String, signatureSegment: String) {
    val signingInput: String = headerSegment + "." + payloadSegment
  }
}

/** UTF-8 conversion between strings and bytes. */
private[jwt] object JwtText {
  def encodeUtf8(value: String): Array[Byte] = value.getBytes("UTF-8")

  def decodeUtf8(bytes: Array[Byte]): String = new String(bytes, "UTF-8")
}

/** The JSON reader and writer for token headers and claims. */
private[jwt] object JwtJson {
  import JwtValue._

  def parseObject(input: String, limits: JwtLimits): Either[JwtError, Map[String, JwtValue]] =
    if (input.length > limits.maxJsonChars) Left(JwtError.JsonTooLarge(input.length, limits.maxJsonChars))
    else {
      val parser = new Parser(input, limits)
      parser.parseObjectAtDepth(1)
    }

  def parseObject(input: String): Either[JwtError, Map[String, JwtValue]] =
    parseObject(input, JwtLimits.Default)

  def requiredString(fields: Map[String, JwtValue], key: String): Either[JwtError, String] =
    fields.get(key) match {
      case Some(Str(value)) => Right(value)
      case Some(_)          => Left(JwtError.InvalidClaim(key, "must be a string"))
      case None             => Left(JwtError.MissingClaim(key))
    }

  def optionalString(fields: Map[String, JwtValue], key: String): Either[JwtError, Option[String]] =
    fields.get(key) match {
      case Some(Str(value)) => Right(Some(value))
      case Some(Null)       => Right(None)
      case Some(_)          => Left(JwtError.InvalidClaim(key, "must be a string"))
      case None             => Right(None)
    }

  /**
   * Parses `aud` per RFC 7519 §4.1.3: either a single string or an array of
   * strings.
   */
  def optionalAud(
    fields: Map[String, JwtValue],
    key: String
  ): Either[JwtError, Option[JwtAudience]] =
    fields.get(key) match {
      case Some(Str(value)) => Right(Some(JwtAudience.Single(value)))
      case Some(Arr(items)) =>
        val strings = items.collect { case Str(s) => s }
        if (strings.size != items.size) Left(JwtError.InvalidClaim(key, "array must contain only strings"))
        else Right(Some(JwtAudience.Multiple(strings)))
      case Some(Null) => Right(None)
      case Some(_)    => Left(JwtError.InvalidClaim(key, "must be a string or array of strings"))
      case None       => Right(None)
    }

  /**
   * Parses a NumericDate (RFC 7519 §2): a non-negative JSON number that fits a
   * `Long` after it is rounded down to whole seconds. Fractional and exponent
   * forms are accepted; `null` reads as absent.
   */
  def optionalNumericDate(fields: Map[String, JwtValue], key: String): Either[JwtError, Option[Long]] =
    fields.get(key) match {
      case Some(Num(raw)) => parseNumericDate(raw, key).map(v => Some(v))
      case Some(Null)     => Right(None)
      case Some(_)        => Left(JwtError.InvalidClaim(key, "must be a number"))
      case None           => Right(None)
    }

  def optionalLong(fields: Map[String, JwtValue], key: String): Either[JwtError, Option[Long]] =
    optionalNumericDate(fields, key)

  private def parseNumericDate(raw: String, key: String): Either[JwtError, Long] =
    try {
      val bd = scala.math.BigDecimal(raw)
      if (bd.signum < 0) Left(JwtError.InvalidClaim(key, "must be non-negative"))
      else {
        val floored = bd.setScale(0, scala.math.BigDecimal.RoundingMode.FLOOR)
        if (floored > scala.math.BigDecimal(Long.MaxValue))
          Left(JwtError.InvalidClaim(key, s"NumericDate out of range: $raw"))
        else {
          try Right(floored.toLongExact)
          catch {
            case _: ArithmeticException => Left(JwtError.InvalidClaim(key, s"invalid NumericDate: $raw"))
          }
        }
      }
    } catch {
      case _: NumberFormatException => Left(JwtError.InvalidClaim(key, s"invalid NumericDate: $raw"))
      case _: ArithmeticException   => Left(JwtError.InvalidClaim(key, s"invalid NumericDate: $raw"))
    }

  def renderField(key: String, value: JwtValue): String = quote(key) + ":" + renderValue(value)

  def quote(value: String): String = {
    val builder = new java.lang.StringBuilder(value.length + 2)
    builder.append('"')

    var index = 0
    while (index < value.length) {
      value.charAt(index) match {
        case '"'                => builder.append("\\\"")
        case '\\'               => builder.append("\\\\")
        case '\b'               => builder.append("\\b")
        case '\f'               => builder.append("\\f")
        case '\n'               => builder.append("\\n")
        case '\r'               => builder.append("\\r")
        case '\t'               => builder.append("\\t")
        case char if char < ' ' =>
          builder.append("\\u")
          val hex     = Integer.toHexString(char.toInt)
          var padding = hex.length
          while (padding < 4) {
            builder.append('0')
            padding += 1
          }
          builder.append(hex)
        case char => builder.append(char)
      }
      index += 1
    }

    builder.append('"').toString
  }

  def renderValue(value: JwtValue): String = value match {
    case Str(text)   => quote(text)
    case Num(number) => number
    case Bool(bool)  => if (bool) "true" else "false"
    case Arr(items)  => items.map(renderValue).mkString("[", ",", "]")
    case Obj(fields) =>
      fields.toList.sortBy(_._1).map { case (k, v) => quote(k) + ":" + renderValue(v) }.mkString("{", ",", "}")
    case Null => "null"
  }

  private final class Parser(input: String, limits: JwtLimits) {
    private val length = input.length
    private var index  = 0

    def parseObjectAtDepth(depth: Int): Either[JwtError, Map[String, JwtValue]] =
      try {
        if (depth > limits.maxDepth) throw JwtError.TooDeep(depth, limits.maxDepth)
        skipWhitespace()
        expect('{')
        skipWhitespace()

        val fields = mutable.LinkedHashMap.empty[String, JwtValue]
        var done   = false

        if (peek('}')) {
          index += 1
          done = true
        }

        while (!done) {
          if (fields.size >= limits.maxFields) throw JwtError.TooManyFields(fields.size + 1, limits.maxFields)
          val key = parseString()
          skipWhitespace()
          expect(':')
          skipWhitespace()
          val value = parseValue(depth + 1)
          fields.update(key, value)
          skipWhitespace()

          if (peek(',')) {
            index += 1
            skipWhitespace()
          } else if (peek('}')) {
            index += 1
            done = true
          } else fail("expected ',' or '}'")
        }

        skipWhitespace()
        if (index != length) fail("unexpected trailing content")
        Right(fields.toMap)
      } catch {
        case error: JwtError => Left(error)
      }

    private def parseValue(depth: Int): JwtValue = {
      if (depth > limits.maxDepth) throw JwtError.TooDeep(depth, limits.maxDepth)
      if (index >= length) fail("unexpected end of input")

      input.charAt(index) match {
        case '"'                                                             => Str(parseString())
        case '{'                                                             => parseObjectValue(depth)
        case '['                                                             => parseArray(depth)
        case 't'                                                             => consumeLiteral("true"); Bool(true)
        case 'f'                                                             => consumeLiteral("false"); Bool(false)
        case 'n'                                                             => consumeLiteral("null"); Null
        case '-' | '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' => Num(parseNumber())
        case _                                                               => fail("invalid JSON value")
      }
    }

    private def parseObjectValue(depth: Int): Obj = {
      if (depth + 1 > limits.maxDepth) throw JwtError.TooDeep(depth + 1, limits.maxDepth)
      expect('{')
      skipWhitespace()
      val fields = mutable.LinkedHashMap.empty[String, JwtValue]
      var done   = false
      if (peek('}')) { index += 1; done = true }
      while (!done) {
        if (fields.size >= limits.maxFields) throw JwtError.TooManyFields(fields.size + 1, limits.maxFields)
        val key = parseString()
        skipWhitespace()
        expect(':')
        skipWhitespace()
        val value = parseValue(depth + 1)
        fields.update(key, value)
        skipWhitespace()
        if (peek(',')) { index += 1; skipWhitespace() }
        else if (peek('}')) { index += 1; done = true }
        else fail("expected ',' or '}'")
      }
      Obj(fields.toMap)
    }

    private def parseArray(depth: Int): Arr = {
      expect('[')
      skipWhitespace()

      val items = scala.collection.mutable.ListBuffer.empty[JwtValue]
      var done  = false

      if (peek(']')) { index += 1; done = true }

      while (!done) {
        if (items.size >= limits.maxArrayElements)
          throw JwtError.TooManyElements(items.size + 1, limits.maxArrayElements)
        items += parseValue(depth + 1)
        skipWhitespace()
        if (peek(',')) {
          index += 1
          skipWhitespace()
          if (peek(']')) fail("trailing comma in array")
        } else if (peek(']')) { index += 1; done = true }
        else fail("expected ',' or ']'")
      }

      Arr(Chunk.from(items))
    }

    private def parseString(): String = {
      expect('"')
      val builder = new java.lang.StringBuilder
      var closed  = false

      while (!closed && index < length) {
        val ch = input.charAt(index)
        index += 1

        if (ch == '"') closed = true
        else if (ch == '\\') {
          if (index >= length) fail("unterminated escape sequence")
          input.charAt(index) match {
            case '"'  => builder.append('"'); index += 1
            case '\\' => builder.append('\\'); index += 1
            case '/'  => builder.append('/'); index += 1
            case 'b'  => builder.append('\b'); index += 1
            case 'f'  => builder.append('\f'); index += 1
            case 'n'  => builder.append('\n'); index += 1
            case 'r'  => builder.append('\r'); index += 1
            case 't'  => builder.append('\t'); index += 1
            case 'u'  =>
              index += 1
              if (index + 4 > length) fail("invalid unicode escape")
              val codePoint = parseHex(input.substring(index, index + 4))
              builder.append(codePoint.toChar)
              index += 4
            case _ => fail("invalid escape sequence")
          }
        } else {
          if (ch < ' ') fail("unescaped control character")
          builder.append(ch)
        }
      }

      if (closed) builder.toString else fail("unterminated string")
    }

    private def parseNumber(): String = {
      val start = index

      if (input.charAt(index) == '-') index += 1
      parseDigits(requireAtLeastOne = true)

      if (index < length && input.charAt(index) == '.') {
        index += 1
        parseDigits(requireAtLeastOne = true)
      }

      if (index < length && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
        index += 1
        if (index < length && (input.charAt(index) == '+' || input.charAt(index) == '-')) index += 1
        parseDigits(requireAtLeastOne = true)
      }

      input.substring(start, index)
    }

    private def parseDigits(requireAtLeastOne: Boolean): Unit = {
      val start = index
      while (index < length && input.charAt(index).isDigit) index += 1
      if (requireAtLeastOne && start == index) fail("invalid number")
    }

    private def consumeLiteral(literal: String): Unit = {
      if (index + literal.length > length || input.substring(index, index + literal.length) != literal)
        fail(s"expected '$literal'")
      index += literal.length
    }

    private def parseHex(value: String): Int =
      try Integer.parseInt(value, 16)
      catch {
        case _: NumberFormatException => fail("invalid unicode escape")
      }

    private def peek(expected: Char): Boolean = index < length && input.charAt(index) == expected

    private def expect(expected: Char): Unit =
      if (index >= length || input.charAt(index) != expected) fail(s"expected '$expected'")
      else index += 1

    private def skipWhitespace(): Unit =
      while (index < length && input.charAt(index).isWhitespace) index += 1

    private def fail(reason: String): Nothing = throw JwtError.InvalidToken(reason)
  }
}

/**
 * The default backend: HMAC-SHA2 in pure Scala, `HS256`, `HS384` and `HS512`
 * only.
 */
private[jwt] object SharedJwtCryptoBackend extends JwtCryptoBackend {
  val supportedAlgorithms: Set[Algorithm] = Set(Algorithm.HS256, Algorithm.HS384, Algorithm.HS512)

  def sign(data: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Array[Byte]] =
    try {
      alg match {
        case Algorithm.HS256 =>
          validateHmacKeyOrThrow(key, 32, "HS256"); Right(HmacSha2.hmacSha256(key, data))
        case Algorithm.HS384 =>
          validateHmacKeyOrThrow(key, 48, "HS384"); Right(HmacSha2.hmacSha384(key, data))
        case Algorithm.HS512 =>
          validateHmacKeyOrThrow(key, 64, "HS512"); Right(HmacSha2.hmacSha512(key, data))
        case _ => Left(JwtError.UnsupportedAlgorithm(alg.name))
      }
    } catch {
      case e: IllegalArgumentException =>
        Left(JwtError.InvalidKey(Option(e.getMessage).getOrElse(e.getClass.getName)))
    }

  def verify(data: Array[Byte], signature: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Boolean] =
    sign(data, key, alg).map(expected => constantTimeEquals(expected, signature))

  private def validateHmacKeyOrThrow(key: Array[Byte], minBytes: Int, alg: String): Unit =
    if (key.length < minBytes)
      throw new IllegalArgumentException(
        s"HMAC key too short: ${key.length} bytes, minimum $minBytes bytes required for $alg"
      )

  private def constantTimeEquals(left: Array[Byte], right: Array[Byte]): Boolean = {
    var diff  = left.length ^ right.length
    val size  = math.min(left.length, right.length)
    var index = 0
    while (index < size) {
      diff |= (left(index) ^ right(index)) & 0xff
      index += 1
    }
    diff == 0
  }
}

/** HMAC (RFC 2104) over the pure Scala SHA-2 digests. */
private[jwt] object HmacSha2 {
  def hmacSha256(key: Array[Byte], data: Array[Byte]): Array[Byte] = hmac(key, data, 64, Sha2.sha256)

  def hmacSha384(key: Array[Byte], data: Array[Byte]): Array[Byte] = hmac(key, data, 128, Sha2.sha384)

  def hmacSha512(key: Array[Byte], data: Array[Byte]): Array[Byte] = hmac(key, data, 128, Sha2.sha512)

  private def hmac(
    key: Array[Byte],
    data: Array[Byte],
    blockSize: Int,
    digest: Array[Byte] => Array[Byte]
  ): Array[Byte] = {
    val normalizedKey =
      if (key.length > blockSize) digest(key)
      else key.clone()

    val keyBlock = new Array[Byte](blockSize)
    java.lang.System.arraycopy(normalizedKey, 0, keyBlock, 0, math.min(normalizedKey.length, blockSize))

    val innerPad = new Array[Byte](blockSize)
    val outerPad = new Array[Byte](blockSize)

    var index = 0
    while (index < blockSize) {
      innerPad(index) = (keyBlock(index) ^ 0x36).toByte
      outerPad(index) = (keyBlock(index) ^ 0x5c).toByte
      index += 1
    }

    val innerInput = new Array[Byte](blockSize + data.length)
    java.lang.System.arraycopy(innerPad, 0, innerInput, 0, blockSize)
    java.lang.System.arraycopy(data, 0, innerInput, blockSize, data.length)
    val innerHash = digest(innerInput)

    val outerInput = new Array[Byte](blockSize + innerHash.length)
    java.lang.System.arraycopy(outerPad, 0, outerInput, 0, blockSize)
    java.lang.System.arraycopy(innerHash, 0, outerInput, blockSize, innerHash.length)

    digest(outerInput)
  }
}

/** SHA-256, SHA-384 and SHA-512 (FIPS 180-4) in pure Scala. */
private[jwt] object Sha2 {
  private val K256: Array[Int] = Array(
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98,
    0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786,
    0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8,
    0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819,
    0xd6990624, 0xf40e3585, 0x106aa070, 0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a,
    0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7,
    0xc67178f2
  )

  private val K512: Array[Long] = Array(
    0x428a2f98d728ae22L, 0x7137449123ef65cdL, 0xb5c0fbcfec4d3b2fL, 0xe9b5dba58189dbbcL, 0x3956c25bf348b538L,
    0x59f111f1b605d019L, 0x923f82a4af194f9bL, 0xab1c5ed5da6d8118L, 0xd807aa98a3030242L, 0x12835b0145706fbeL,
    0x243185be4ee4b28cL, 0x550c7dc3d5ffb4e2L, 0x72be5d74f27b896fL, 0x80deb1fe3b1696b1L, 0x9bdc06a725c71235L,
    0xc19bf174cf692694L, 0xe49b69c19ef14ad2L, 0xefbe4786384f25e3L, 0x0fc19dc68b8cd5b5L, 0x240ca1cc77ac9c65L,
    0x2de92c6f592b0275L, 0x4a7484aa6ea6e483L, 0x5cb0a9dcbd41fbd4L, 0x76f988da831153b5L, 0x983e5152ee66dfabL,
    0xa831c66d2db43210L, 0xb00327c898fb213fL, 0xbf597fc7beef0ee4L, 0xc6e00bf33da88fc2L, 0xd5a79147930aa725L,
    0x06ca6351e003826fL, 0x142929670a0e6e70L, 0x27b70a8546d22ffcL, 0x2e1b21385c26c926L, 0x4d2c6dfc5ac42aedL,
    0x53380d139d95b3dfL, 0x650a73548baf63deL, 0x766a0abb3c77b2a8L, 0x81c2c92e47edaee6L, 0x92722c851482353bL,
    0xa2bfe8a14cf10364L, 0xa81a664bbc423001L, 0xc24b8b70d0f89791L, 0xc76c51a30654be30L, 0xd192e819d6ef5218L,
    0xd69906245565a910L, 0xf40e35855771202aL, 0x106aa07032bbd1b8L, 0x19a4c116b8d2d0c8L, 0x1e376c085141ab53L,
    0x2748774cdf8eeb99L, 0x34b0bcb5e19b48a8L, 0x391c0cb3c5c95a63L, 0x4ed8aa4ae3418acbL, 0x5b9cca4f7763e373L,
    0x682e6ff3d6b2b8a3L, 0x748f82ee5defb2fcL, 0x78a5636f43172f60L, 0x84c87814a1f0ab72L, 0x8cc702081a6439ecL,
    0x90befffa23631e28L, 0xa4506cebde82bde9L, 0xbef9a3f7b2c67915L, 0xc67178f2e372532bL, 0xca273eceea26619cL,
    0xd186b8c721c0c207L, 0xeada7dd6cde0eb1eL, 0xf57d4f7fee6ed178L, 0x06f067aa72176fbaL, 0x0a637dc5a2c898a6L,
    0x113f9804bef90daeL, 0x1b710b35131c471bL, 0x28db77f523047d84L, 0x32caab7b40c72493L, 0x3c9ebe0a15c9bebcL,
    0x431d67c49c100d4cL, 0x4cc5d4becb3e42b6L, 0x597f299cfc657e2aL, 0x5fcb6fab3ad6faecL, 0x6c44198c4a475817L
  )

  def sha256(data: Array[Byte]): Array[Byte] = {
    val hash = Array(0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19)
    val w    = new Array[Int](64)
    val body = pad256(data)

    var offset = 0
    while (offset < body.length) {
      var t = 0
      while (t < 16) {
        val base = offset + (t * 4)
        w(t) = ((body(base) & 0xff) << 24) |
          ((body(base + 1) & 0xff) << 16) |
          ((body(base + 2) & 0xff) << 8) |
          (body(base + 3) & 0xff)
        t += 1
      }
      while (t < 64) {
        w(t) = smallSigma1(w(t - 2)) + w(t - 7) + smallSigma0(w(t - 15)) + w(t - 16)
        t += 1
      }

      var a = hash(0)
      var b = hash(1)
      var c = hash(2)
      var d = hash(3)
      var e = hash(4)
      var f = hash(5)
      var g = hash(6)
      var h = hash(7)

      t = 0
      while (t < 64) {
        val t1 = h + bigSigma1(e) + ch(e, f, g) + K256(t) + w(t)
        val t2 = bigSigma0(a) + maj(a, b, c)
        h = g
        g = f
        f = e
        e = d + t1
        d = c
        c = b
        b = a
        a = t1 + t2
        t += 1
      }

      hash(0) += a
      hash(1) += b
      hash(2) += c
      hash(3) += d
      hash(4) += e
      hash(5) += f
      hash(6) += g
      hash(7) += h

      offset += 64
    }

    val result = new Array[Byte](32)
    var index  = 0
    while (index < hash.length) {
      writeInt(hash(index), result, index * 4)
      index += 1
    }
    result
  }

  def sha384(data: Array[Byte]): Array[Byte] = {
    val hash = Array(
      0xcbbb9d5dc1059ed8L, 0x629a292a367cd507L, 0x9159015a3070dd17L, 0x152fecd8f70e5939L, 0x67332667ffc00b31L,
      0x8eb44a8768581511L, 0xdb0c2e0d64f98fa7L, 0x47b5481dbefa4fa4L
    )
    digest512(data, hash, 48)
  }

  def sha512(data: Array[Byte]): Array[Byte] = {
    val hash = Array(
      0x6a09e667f3bcc908L, 0xbb67ae8584caa73bL, 0x3c6ef372fe94f82bL, 0xa54ff53a5f1d36f1L, 0x510e527fade682d1L,
      0x9b05688c2b3e6c1fL, 0x1f83d9abfb41bd6bL, 0x5be0cd19137e2179L
    )
    digest512(data, hash, 64)
  }

  private def digest512(data: Array[Byte], initialHash: Array[Long], outputSize: Int): Array[Byte] = {
    val hash = initialHash.clone()
    val w    = new Array[Long](80)
    val body = pad512(data)

    var offset = 0
    while (offset < body.length) {
      var t = 0
      while (t < 16) {
        val base = offset + (t * 8)
        w(t) = readLong(body, base)
        t += 1
      }
      while (t < 80) {
        w(t) = smallSigma1(w(t - 2)) + w(t - 7) + smallSigma0(w(t - 15)) + w(t - 16)
        t += 1
      }

      var a = hash(0)
      var b = hash(1)
      var c = hash(2)
      var d = hash(3)
      var e = hash(4)
      var f = hash(5)
      var g = hash(6)
      var h = hash(7)

      t = 0
      while (t < 80) {
        val t1 = h + bigSigma1(e) + ch(e, f, g) + K512(t) + w(t)
        val t2 = bigSigma0(a) + maj(a, b, c)
        h = g
        g = f
        f = e
        e = d + t1
        d = c
        c = b
        b = a
        a = t1 + t2
        t += 1
      }

      hash(0) += a
      hash(1) += b
      hash(2) += c
      hash(3) += d
      hash(4) += e
      hash(5) += f
      hash(6) += g
      hash(7) += h

      offset += 128
    }

    val result = new Array[Byte](outputSize)
    var index  = 0
    while (index < outputSize / 8) {
      writeLong(hash(index), result, index * 8)
      index += 1
    }
    result
  }

  private def pad256(data: Array[Byte]): Array[Byte] = {
    val bitLength = data.length.toLong * 8L
    val padding   = (64 - ((data.length + 1 + 8) % 64)) % 64
    val result    = new Array[Byte](data.length + 1 + padding + 8)

    java.lang.System.arraycopy(data, 0, result, 0, data.length)
    result(data.length) = 0x80.toByte

    var index = 0
    while (index < 8) {
      result(result.length - 1 - index) = (bitLength >>> (index * 8)).toByte
      index += 1
    }
    result
  }

  private def pad512(data: Array[Byte]): Array[Byte] = {
    val bitLength = data.length.toLong * 8L
    val padding   = (128 - ((data.length + 1 + 16) % 128)) % 128
    val result    = new Array[Byte](data.length + 1 + padding + 16)

    java.lang.System.arraycopy(data, 0, result, 0, data.length)
    result(data.length) = 0x80.toByte

    var index = 0
    while (index < 8) {
      result(result.length - 1 - index) = (bitLength >>> (index * 8)).toByte
      index += 1
    }
    result
  }

  private def writeInt(value: Int, output: Array[Byte], offset: Int): Unit = {
    output(offset) = (value >>> 24).toByte
    output(offset + 1) = (value >>> 16).toByte
    output(offset + 2) = (value >>> 8).toByte
    output(offset + 3) = value.toByte
  }

  private def writeLong(value: Long, output: Array[Byte], offset: Int): Unit = {
    output(offset) = (value >>> 56).toByte
    output(offset + 1) = (value >>> 48).toByte
    output(offset + 2) = (value >>> 40).toByte
    output(offset + 3) = (value >>> 32).toByte
    output(offset + 4) = (value >>> 24).toByte
    output(offset + 5) = (value >>> 16).toByte
    output(offset + 6) = (value >>> 8).toByte
    output(offset + 7) = value.toByte
  }

  private def readLong(input: Array[Byte], offset: Int): Long =
    ((input(offset).toLong & 0xffL) << 56) |
      ((input(offset + 1).toLong & 0xffL) << 48) |
      ((input(offset + 2).toLong & 0xffL) << 40) |
      ((input(offset + 3).toLong & 0xffL) << 32) |
      ((input(offset + 4).toLong & 0xffL) << 24) |
      ((input(offset + 5).toLong & 0xffL) << 16) |
      ((input(offset + 6).toLong & 0xffL) << 8) |
      (input(offset + 7).toLong & 0xffL)

  private def rotateRight(value: Int, bits: Int): Int = (value >>> bits) | (value << (32 - bits))

  private def rotateRight(value: Long, bits: Int): Long = (value >>> bits) | (value << (64 - bits))

  private def ch(x: Int, y: Int, z: Int): Int = (x & y) ^ (~x & z)

  private def ch(x: Long, y: Long, z: Long): Long = (x & y) ^ (~x & z)

  private def maj(x: Int, y: Int, z: Int): Int = (x & y) ^ (x & z) ^ (y & z)

  private def maj(x: Long, y: Long, z: Long): Long = (x & y) ^ (x & z) ^ (y & z)

  private def bigSigma0(value: Int): Int = rotateRight(value, 2) ^ rotateRight(value, 13) ^ rotateRight(value, 22)

  private def bigSigma1(value: Int): Int = rotateRight(value, 6) ^ rotateRight(value, 11) ^ rotateRight(value, 25)

  private def smallSigma0(value: Int): Int = rotateRight(value, 7) ^ rotateRight(value, 18) ^ (value >>> 3)

  private def smallSigma1(value: Int): Int = rotateRight(value, 17) ^ rotateRight(value, 19) ^ (value >>> 10)

  private def bigSigma0(value: Long): Long =
    rotateRight(value, 28) ^ rotateRight(value, 34) ^ rotateRight(value, 39)

  private def bigSigma1(value: Long): Long =
    rotateRight(value, 14) ^ rotateRight(value, 18) ^ rotateRight(value, 41)

  private def smallSigma0(value: Long): Long = rotateRight(value, 1) ^ rotateRight(value, 8) ^ (value >>> 7)

  private def smallSigma1(value: Long): Long = rotateRight(value, 19) ^ rotateRight(value, 61) ^ (value >>> 6)
}
