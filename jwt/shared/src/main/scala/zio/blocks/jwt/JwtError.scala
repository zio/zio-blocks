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
 * A failure that can occur during JWT signing or decoding.
 *
 * All subtypes extend [[scala.util.control.NoStackTrace]] to avoid capturing a
 * stack trace on the hot decoding path.
 */
sealed trait JwtError extends NoStackTrace {

  /** Human-readable description of the error. */
  def message: String

  override def getMessage: String = message
}

object JwtError {

  /** The token is structurally or semantically malformed. */
  case class InvalidToken(reason: String) extends JwtError {
    def message: String = s"Invalid JWT token: $reason"
  }

  /** The token's `exp` claim is in the past. */
  case class ExpiredToken(expiredAt: Long, now: Long) extends JwtError {
    def message: String = s"JWT token expired at $expiredAt (now: $now)"
  }

  /** The token's `nbf` claim is in the future. */
  case class NotYetValid(notBefore: Long, now: Long) extends JwtError {
    def message: String = s"JWT token is not valid before $notBefore (now: $now)"
  }

  /** Cryptographic signature verification failed. */
  case class InvalidSignature(alg: String) extends JwtError {
    def message: String = s"JWT signature verification failed for algorithm $alg"
  }

  /** The active [[JwtCryptoBackend]] does not support this algorithm. */
  case class UnsupportedAlgorithm(alg: String) extends JwtError {
    def message: String = s"Unsupported JWT algorithm: $alg"
  }

  /** A required claim was absent from the token. */
  case class MissingClaim(claim: String) extends JwtError {
    def message: String = s"Missing JWT claim: $claim"
  }

  /** A claim value is present but fails validation. */
  case class InvalidClaim(claim: String, reason: String) extends JwtError {
    def message: String = s"Invalid JWT claim '$claim': $reason"
  }

  /** The `alg` header does not match the algorithm the caller requested. */
  case class AlgorithmMismatch(expected: String, found: String) extends JwtError {
    def message: String = s"JWT algorithm mismatch: expected $expected but found $found"
  }

  /**
   * The supplied key is malformed, weak, or does not match the required
   * algorithm.
   */
  case class InvalidKey(reason: String) extends JwtError {
    def message: String = s"Invalid JWT key: $reason"
  }

  /** The compact token exceeds the configured maximum token length. */
  case class TokenTooLarge(size: Int, max: Int) extends JwtError {
    def message: String = s"JWT token too large: $size chars exceeds limit $max"
  }

  /** A single JWT segment exceeds the configured maximum segment length. */
  case class SegmentTooLarge(size: Int, max: Int) extends JwtError {
    def message: String = s"JWT segment too large: $size chars exceeds limit $max"
  }

  /**
   * The decoded JSON payload/header exceeds the configured maximum JSON length.
   */
  case class JsonTooLarge(size: Int, max: Int) extends JwtError {
    def message: String = s"JWT JSON too large: $size chars exceeds limit $max"
  }

  /** The JSON structure exceeds the configured maximum nesting depth. */
  case class TooDeep(depth: Int, maxDepth: Int) extends JwtError {
    def message: String = s"JWT JSON too deep: depth $depth exceeds limit $maxDepth"
  }

  /** The JSON object has too many fields. */
  case class TooManyFields(count: Int, max: Int) extends JwtError {
    def message: String = s"JWT JSON too many fields: $count exceeds limit $max"
  }

  /** The JSON array has too many elements. */
  case class TooManyElements(count: Int, max: Int) extends JwtError {
    def message: String = s"JWT JSON too many array elements: $count exceeds limit $max"
  }
}
