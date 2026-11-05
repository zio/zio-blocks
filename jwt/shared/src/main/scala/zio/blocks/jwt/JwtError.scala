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

import scala.util.control.NoStackTrace

/**
 * Why a token could not be signed or decoded: `Jwt.sign`, `Jwt.decode`,
 * `Jwt.decodeWithoutVerification` and the `parse` methods return it as a
 * `Left`.
 *
 * Match on the case to choose a response, for example to tell an expired token
 * (`ExpiredToken`) from a forged one (`InvalidSignature`). `JwtError` is a
 * `Throwable` that extends [[scala.util.control.NoStackTrace]], so it records
 * no stack trace, can be thrown as it is, and its `getMessage` is its
 * `message`. Messages quote parts of the token, so do not send them to the
 * client as they are.
 *
 * {{{
 * val key = "demo-secret-do-not-use-0123456789".getBytes("UTF-8")
 *
 * Jwt.decode("not-a-token", key, Algorithm.HS256) match {
 *   case Left(JwtError.InvalidToken(reason)) => reason
 *   case other                               => other.toString
 * }
 * // JWT must have exactly three dot-separated segments
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/jwt/#error-handling JWT]]
 */
sealed trait JwtError extends NoStackTrace {

  /**
   * Returns a description of the error, starting with the case's fixed text.
   */
  def message: String

  override def getMessage: String = message
}

/** The cases of [[JwtError]], one per kind of failure. */
object JwtError {

  /**
   * The token is malformed: not three non-empty dot-separated segments, a
   * segment that is not valid Base64URL, a header or payload that is not a JSON
   * object, or invalid JSON. `reason` names the problem, such as "JWT must have
   * exactly three dot-separated segments" or "invalid base64url character". The
   * platform backends also return it for some backend failures they cannot
   * classify; on Scala.js that includes a malformed private key passed to
   * `sign`, where `InvalidKey` is expected (a known bug).
   */
  case class InvalidToken(reason: String) extends JwtError {

    /** Returns "Invalid JWT token: " followed by `reason`. */
    def message: String = s"Invalid JWT token: $reason"
  }

  /**
   * `Jwt.decode` found the token expired: `now - clockSkewSeconds > exp`.
   * `expiredAt` is the token's `exp` and `now` the time the check used, both in
   * seconds since the Unix epoch.
   */
  case class ExpiredToken(expiredAt: Long, now: Long) extends JwtError {

    /**
     * Returns "JWT token expired at " followed by `expiredAt` and `now`, as in
     * "JWT token expired at 1000 (now: 1001)".
     */
    def message: String = s"JWT token expired at $expiredAt (now: $now)"
  }

  /**
   * `Jwt.decode` found the token not valid yet: `now + clockSkewSeconds < nbf`.
   * `notBefore` is the token's `nbf` and `now` the time the check used.
   */
  case class NotYetValid(notBefore: Long, now: Long) extends JwtError {

    /**
     * Returns "JWT token is not valid before " followed by `notBefore` and
     * `now`, as in "JWT token is not valid before 1000 (now: 999)".
     */
    def message: String = s"JWT token is not valid before $notBefore (now: $now)"
  }

  /**
   * The signature does not match the token for the key and algorithm; then
   * `alg` is the algorithm's name. When the backend rejects the signature bytes
   * themselves (an ECDSA signature of the wrong length, an RSA signature of the
   * wrong size on the JVM), `alg` holds the backend's reason instead, a known
   * bug, as in "JWT signature verification failed for algorithm invalid P1363
   * ECDSA signature length". On Scala.js, signing an `ES*` token with a key on
   * a larger curve also returns it.
   */
  case class InvalidSignature(alg: String) extends JwtError {

    /**
     * Returns "JWT signature verification failed for algorithm " followed by
     * `alg`.
     */
    def message: String = s"JWT signature verification failed for algorithm $alg"
  }

  /**
   * The algorithm cannot be used: the token's header names one that
   * `Algorithm.fromString` does not know (`alg` is the raw value, such as
   * `"none"`), or the backend does not support it (the built-in default for
   * every algorithm but `HS*`, `JsJwtCryptoBackend` for `PS*` and `EdDSA`, or
   * for `RS*` and `ES*` when Node's `crypto` module is missing).
   */
  case class UnsupportedAlgorithm(alg: String) extends JwtError {

    /** Returns "Unsupported JWT algorithm: " followed by `alg`. */
    def message: String = s"Unsupported JWT algorithm: $alg"
  }

  /**
   * A required field is absent: `alg` in the header, `iss` when
   * [[JwtDecodeOptions]] sets `issuer`, or `aud` when it sets
   * `expectedAudience`. An `iss` or `aud` that is JSON `null` counts as absent;
   * an `alg` that is `null` gives `InvalidClaim("alg", "must be a string")`.
   */
  case class MissingClaim(claim: String) extends JwtError {

    /** Returns "Missing JWT claim: " followed by `claim`. */
    def message: String = s"Missing JWT claim: $claim"
  }

  /**
   * A claim or header field is present but not valid: the wrong JSON type
   * (`reason` "must be a string", "must be a number", "must be a string or
   * array of strings" or "array must contain only strings"), a NumericDate that
   * is negative ("must be non-negative") or too large ("NumericDate out of
   * range: " and the raw number), or an `iss` or `aud` that does not match
   * [[JwtDecodeOptions]] ("expected notes-api but found billing-api").
   */
  case class InvalidClaim(claim: String, reason: String) extends JwtError {

    /**
     * Returns "Invalid JWT claim '" followed by `claim`, "': " and `reason`.
     */
    def message: String = s"Invalid JWT claim '$claim': $reason"
  }

  /**
   * The header's algorithm is not the one the caller passed: in `Jwt.decode`,
   * the token's `alg` (`found`) differs from the `alg` argument (`expected`);
   * in `Jwt.sign`, `header.alg` (`found`) differs from the `alg` argument.
   */
  case class AlgorithmMismatch(expected: String, found: String) extends JwtError {

    /**
     * Returns "JWT algorithm mismatch: expected " followed by `expected`, " but
     * found " and `found`.
     */
    def message: String = s"JWT algorithm mismatch: expected $expected but found $found"
  }

  /**
   * The key cannot be used for the algorithm: an `HS*` secret shorter than 32,
   * 48 or 64 bytes, or, with `JvmJwtCryptoBackend`, a key that is not in the
   * expected DER form or not of the algorithm's type, an RSA key under 2048
   * bits, an EC key on the wrong curve, or an Ed448 key for `EdDSA`. `reason`
   * comes from the backend, as in "HMAC key too short: 5 bytes, minimum 32
   * bytes required for HS256" (the JVM backend writes `HmacSHA256` for the
   * algorithm).
   */
  case class InvalidKey(reason: String) extends JwtError {

    /** Returns "Invalid JWT key: " followed by `reason`. */
    def message: String = s"Invalid JWT key: $reason"
  }

  /**
   * The compact token is longer than `JwtLimits.maxTokenChars`; returned by
   * `Jwt.sign` and `Jwt.decode`.
   */
  case class TokenTooLarge(size: Int, max: Int) extends JwtError {

    /**
     * Returns "JWT token too large: " followed by `size`, " chars exceeds limit
     * " and `max`.
     */
    def message: String = s"JWT token too large: $size chars exceeds limit $max"
  }

  /**
   * One of the three segments is longer than `JwtLimits.maxSegmentChars`;
   * returned by `Jwt.sign`, `Jwt.decode` and the `parse` methods.
   */
  case class SegmentTooLarge(size: Int, max: Int) extends JwtError {

    /**
     * Returns "JWT segment too large: " followed by `size`, " chars exceeds
     * limit " and `max`.
     */
    def message: String = s"JWT segment too large: $size chars exceeds limit $max"
  }

  /**
   * The header or payload JSON is longer than `JwtLimits.maxJsonChars`;
   * returned by `Jwt.sign`, `Jwt.decode` and the `parse` methods.
   */
  case class JsonTooLarge(size: Int, max: Int) extends JwtError {

    /**
     * Returns "JWT JSON too large: " followed by `size`, " chars exceeds limit
     * " and `max`.
     */
    def message: String = s"JWT JSON too large: $size chars exceeds limit $max"
  }

  /**
   * The header or payload JSON nests deeper than `JwtLimits.maxDepth`; returned
   * on decode and parse, not by `Jwt.sign` (a known bug).
   */
  case class TooDeep(depth: Int, maxDepth: Int) extends JwtError {

    /**
     * Returns "JWT JSON too deep: depth " followed by `depth`, " exceeds limit
     * " and `maxDepth`.
     */
    def message: String = s"JWT JSON too deep: depth $depth exceeds limit $maxDepth"
  }

  /**
   * A JSON object in the header or payload has more fields than
   * `JwtLimits.maxFields`; `count` is the limit plus one. Returned on decode
   * and parse, not by `Jwt.sign` (a known bug).
   */
  case class TooManyFields(count: Int, max: Int) extends JwtError {

    /**
     * Returns "JWT JSON too many fields: " followed by `count`, " exceeds limit
     * " and `max`.
     */
    def message: String = s"JWT JSON too many fields: $count exceeds limit $max"
  }

  /**
   * A JSON array in the header or payload has more elements than
   * `JwtLimits.maxArrayElements`; `count` is the limit plus one. Returned on
   * decode and parse, not by `Jwt.sign` (a known bug).
   */
  case class TooManyElements(count: Int, max: Int) extends JwtError {

    /**
     * Returns "JWT JSON too many array elements: " followed by `count`, "
     * exceeds limit " and `max`.
     */
    def message: String = s"JWT JSON too many array elements: $count exceeds limit $max"
  }
}
