package issueandverifyjwts

import zio.blocks.jwt._
import java.nio.charset.StandardCharsets.UTF_8

/**
 * How to Issue and Verify JWTs: Step 4 — Seeing Every Failure
 *
 * Tampers with the payload and verifies with the wrong secret, algorithm,
 * audience and issuer.
 *
 * Run with: cd jwt-examples && sbt "runMain
 * issueandverifyjwts.Step4FailuresExample"
 */
object Step4FailuresExample extends App {

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

  val segments = token.split('.')

  def decodeSegment(segment: String): String =
    Base64Url.decode(segment).map(bytes => new String(bytes, UTF_8)).fold(_.message, identity)

  val catalogOptions = JwtDecodeOptions(
    issuer = Some("bookstore-auth"),
    expectedAudience = Some("catalog-api"),
    nowSeconds = Some(now + 60)
  )

  val forgedPayload = Base64Url.encode(decodeSegment(segments(1)).replace("reader-1024", "reader-0007").getBytes(UTF_8))
  val tampered      = s"${segments(0)}.$forgedPayload.${segments(2)}"

  report(Jwt.decode(tampered, secret, Algorithm.HS256, catalogOptions))

  val otherSecret: Array[Byte] = "another-service-secret-32-bytes!".getBytes(UTF_8)

  report(Jwt.decode(token, otherSecret, Algorithm.HS256, catalogOptions))

  report(Jwt.decode(token, secret, Algorithm.HS512, catalogOptions))

  val noAudienceToken: String = Jwt.sign(claims.copy(aud = None), secret, Algorithm.HS256).fold(e => throw e, t => t)

  report(Jwt.decode(token, secret, Algorithm.HS256, catalogOptions.copy(expectedAudience = Some("orders-api"))))
  report(Jwt.decode(noAudienceToken, secret, Algorithm.HS256, catalogOptions))

  report(Jwt.decode(token, secret, Algorithm.HS256, catalogOptions.copy(issuer = Some("partner-auth"))))
}
