package issueandverifyjwts

import zio.blocks.jwt._
import java.nio.charset.StandardCharsets.UTF_8

/**
 * How to Issue and Verify JWTs: Step 2 — Verifying a Token
 *
 * Verifies the token with issuer, expectedAudience and a fixed clock, and reads
 * the custom titles claim.
 *
 * Run with: cd jwt-examples && sbt "runMain
 * issueandverifyjwts.Step2VerifyTokenExample"
 */
object Step2VerifyTokenExample extends App {

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

  val verified: Either[JwtError, JwtClaims] =
    Jwt.decode(token, secret, Algorithm.HS256, catalogOptions)

  println(verified)
  println(verified.map(_.extra.get("titles")))
}
