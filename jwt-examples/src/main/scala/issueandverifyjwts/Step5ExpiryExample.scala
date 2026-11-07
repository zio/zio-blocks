package issueandverifyjwts

import zio.blocks.jwt._
import java.nio.charset.StandardCharsets.UTF_8

/**
 * How to Issue and Verify JWTs: Step 5 — Testing Expiry Without Waiting
 *
 * Moves the clock to show ExpiredToken, clock skew, NotYetValid, and a token
 * without exp.
 *
 * Run with: cd jwt-examples && sbt "runMain
 * issueandverifyjwts.Step5ExpiryExample"
 */
object Step5ExpiryExample extends App {

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

  report(Jwt.decode(token, secret, Algorithm.HS256, catalogOptions.copy(nowSeconds = Some(now + 7200))))

  val expiresAt = now + 3600
  val withSkew  = catalogOptions.copy(clockSkewSeconds = 60L)

  report(Jwt.decode(token, secret, Algorithm.HS256, withSkew.copy(nowSeconds = Some(expiresAt + 30))))
  report(Jwt.decode(token, secret, Algorithm.HS256, withSkew.copy(nowSeconds = Some(expiresAt + 61))))

  val preOrderToken: String =
    Jwt.sign(claims.copy(nbf = Some(now + 600)), secret, Algorithm.HS256).fold(e => throw e, t => t)

  report(Jwt.decode(preOrderToken, secret, Algorithm.HS256, catalogOptions.copy(nowSeconds = Some(now))))
  report(Jwt.decode(preOrderToken, secret, Algorithm.HS256, catalogOptions.copy(nowSeconds = Some(now + 600))))

  val foreverToken: String = Jwt.sign(claims.copy(exp = None), secret, Algorithm.HS256).fold(e => throw e, t => t)

  report(
    Jwt.decode(
      foreverToken,
      secret,
      Algorithm.HS256,
      catalogOptions.copy(nowSeconds = Some(now + 10L * 365 * 24 * 3600))
    )
  )
}
