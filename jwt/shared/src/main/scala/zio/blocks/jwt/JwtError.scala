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

sealed trait JwtError extends NoStackTrace {
  def message: String

  override def getMessage: String = message
}

object JwtError {
  case class InvalidToken(reason: String) extends JwtError {
    def message: String = s"Invalid JWT token: $reason"
  }

  case class ExpiredToken(expiredAt: Long, now: Long) extends JwtError {
    def message: String = s"JWT token expired at $expiredAt (now: $now)"
  }

  case class NotYetValid(notBefore: Long, now: Long) extends JwtError {
    def message: String = s"JWT token is not valid before $notBefore (now: $now)"
  }

  case class InvalidSignature(alg: String) extends JwtError {
    def message: String = s"JWT signature verification failed for algorithm $alg"
  }

  case class UnsupportedAlgorithm(alg: String) extends JwtError {
    def message: String = s"Unsupported JWT algorithm: $alg"
  }

  case class MissingClaim(claim: String) extends JwtError {
    def message: String = s"Missing JWT claim: $claim"
  }

  case class AlgorithmMismatch(expected: String, found: String) extends JwtError {
    def message: String = s"JWT algorithm mismatch: expected $expected but found $found"
  }
}
