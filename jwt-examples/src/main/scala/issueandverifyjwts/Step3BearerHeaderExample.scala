package issueandverifyjwts

import zio.blocks.jwt._
import java.nio.charset.StandardCharsets.UTF_8

/**
 * How to Issue and Verify JWTs: Step 3 — Reading the Token From an
 * Authorization Header
 *
 * Extracts the token from a Bearer header value and verifies it.
 *
 * Run with: cd jwt-examples && sbt "runMain
 * issueandverifyjwts.Step3BearerHeaderExample"
 */
object Step3BearerHeaderExample extends App {

  // 2026-01-01T00:00:00Z. A fixed "now" keeps every result reproducible.
  val now: Long = 1767225600L

  // Sample data only. A real HS256 secret is at least 32 random bytes, loaded from a secret store.
  val secret: Array[Byte] = "sample-bookstore-secret-32-bytes".getBytes(UTF_8)

  def report(result: Either[JwtError, JwtClaims]): Unit = result match {
    case Right(claims) => println(s"accepted: sub=${claims.sub.getOrElse("-")}")
    case Left(error)   => println(s"rejected: ${error.getClass.getSimpleName}: ${error.message}")
  }

  val claims = JwtClaims(
    iss = Some("bookstore-auth"),
    sub = Some("reader-1024"),
    aud = Some(JwtAudience.single("catalog-api")),
    iat = Some(now),
    exp = Some(now + 3600),
    extra = Map(
      "titles" -> JwtValue.Arr(
        JwtValue.Str("Zionomicon"),
        JwtValue.Str("Programming in Scala"),
        JwtValue.Str("Functional Programming in Scala")
      )
    )
  )

  val token: String = Jwt.sign(claims, secret, Algorithm.HS256) match {
    case Right(signed) => signed
    case Left(error)   => throw error
  }

  val catalogOptions = JwtDecodeOptions(
    issuer = Some("bookstore-auth"),
    expectedAudience = Some("catalog-api"),
    nowSeconds = Some(now + 60)
  )

  def bearerToken(headerValue: String): Option[String] = {
    val scheme = "Bearer "
    if (headerValue.regionMatches(true, 0, scheme, 0, scheme.length))
      Some(headerValue.substring(scheme.length).trim)
    else None
  }

  val authorization = s"Bearer $token"

  println(s"Authorization: $authorization")

  bearerToken(authorization) match {
    case Some(bearer) => report(Jwt.decode(bearer, secret, Algorithm.HS256, catalogOptions))
    case None         => println("rejected: no bearer token")
  }
}
