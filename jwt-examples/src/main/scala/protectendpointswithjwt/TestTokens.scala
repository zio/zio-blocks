package protectendpointswithjwt

import java.nio.charset.StandardCharsets.UTF_8
import zio.blocks.chunk.Chunk
import zio.blocks.jwt._

/**
 * How to Protect an Endpoint with JWT Bearer Tokens — test tokens
 *
 * Plays the auth service: signs the tokens that the examples send to the
 * bookstore API. Every token is issued at a fixed time, so the results are the
 * same on every run.
 */
object TestTokens {

  // 2026-01-01T00:00:00Z. The API verifies tokens one minute later.
  val now: Long = 1767225600L

  // Sample data only. A real HS256 secret is at least 32 random bytes, loaded from a secret store.
  val secret: String = "sample-bookstore-secret-32-bytes"

  // Registered claims (iss, sub, aud, iat, exp) go in their own fields, never in `extra`.
  def issue(
    subject: String,
    roles: List[String],
    audience: String = "bookstore-api",
    expiresAt: Option[Long] = Some(now + 3600),
    key: String = secret
  ): String = {
    val claims = JwtClaims(
      iss = Some("bookstore-auth"),
      sub = Some(subject),
      aud = Some(JwtAudience.single(audience)),
      iat = Some(now),
      exp = expiresAt,
      extra = Map("roles" -> JwtValue.Arr(Chunk.fromIterable(roles.map(role => JwtValue.Str(role)))))
    )
    Jwt.sign(claims, key.getBytes(UTF_8), Algorithm.HS256).fold(error => throw error, token => token)
  }

  val reader: String        = issue("reader-1024", List("reader"))
  val admin: String         = issue("staff-0007", List("reader", "admin"))
  val expired: String       = issue("reader-1024", List("reader"), expiresAt = Some(now - 600))
  val otherAudience: String = issue("reader-1024", List("reader"), audience = "partner-api")
  val forged: String        = issue("staff-0007", List("admin"), key = "attacker-guessed-secret-32-bytes")
  val neverExpires: String  = issue("reader-1024", List("reader"), expiresAt = None)
}
