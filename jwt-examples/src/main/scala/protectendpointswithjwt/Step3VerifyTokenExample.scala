package protectendpointswithjwt

import java.nio.charset.StandardCharsets.UTF_8
import zio.blocks.jwt._

/**
 * How to Protect an Endpoint with JWT Bearer Tokens: Step 3 — Verifying the
 * Token
 *
 * Verifies each test token with `Jwt.decode`, checks issuer, audience and
 * expiry against a fixed clock, requires `sub` and `exp`, and turns the claims
 * into a `Caller`.
 *
 * Run with: cd jwt-examples && sbt "runMain
 * protectendpointswithjwt.Step3VerifyTokenExample"
 */
object Step3VerifyTokenExample {

  final case class Caller(subject: String, roles: Set[String])

  type Verifier = String => Either[JwtError, Caller]

  def rolesOf(claims: JwtClaims): Set[String] =
    claims.extra.get("roles") match {
      case Some(JwtValue.Arr(items)) => items.collect { case JwtValue.Str(role) => role }.toSet
      case _                         => Set.empty
    }

  def toCaller(claims: JwtClaims): Either[JwtError, Caller] =
    (claims.sub, claims.exp) match {
      case (_, None)          => Left(JwtError.MissingClaim("exp"))
      case (None, _)          => Left(JwtError.MissingClaim("sub"))
      case (Some(subject), _) => Right(Caller(subject, rolesOf(claims)))
    }

  def jwtVerifier(key: Array[Byte], options: JwtDecodeOptions): Verifier =
    token => Jwt.decode(token, key, Algorithm.HS256, options).flatMap(toCaller)

  val verify: Verifier = jwtVerifier(
    TestTokens.secret.getBytes(UTF_8),
    JwtDecodeOptions(
      issuer = Some("bookstore-auth"),
      expectedAudience = Some("bookstore-api"),
      clockSkewSeconds = 30,
      nowSeconds = Some(TestTokens.now + 60)
    )
  )

  def report(name: String, token: String): Unit =
    verify(token) match {
      case Right(caller) => println(s"$name: accepted $caller")
      case Left(error)   => println(s"$name: rejected ${error.getClass.getSimpleName}: ${error.message}")
    }

  def main(args: Array[String]): Unit = {
    report("reader", TestTokens.reader)
    report("admin", TestTokens.admin)
    report("expired", TestTokens.expired)
    report("other audience", TestTokens.otherAudience)
    report("forged", TestTokens.forged)
    report("never expires", TestTokens.neverExpires)
  }
}
