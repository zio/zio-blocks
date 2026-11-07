package issueandverifyjwts

import zio.blocks.jwt._
import java.nio.charset.StandardCharsets.UTF_8

/**
 * How to Issue and Verify JWTs: Step 6 — Rejecting Hostile Tokens
 *
 * Sends an oversized token, a deeply nested header and alg none tokens, and
 * tightens JwtLimits.
 *
 * Run with: cd jwt-examples && sbt "runMain
 * issueandverifyjwts.Step6HostileTokensExample"
 */
object Step6HostileTokensExample extends App {

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

  report(Jwt.decode("a" * 10000, secret, Algorithm.HS256, catalogOptions))

  val deepHeader = """{"alg":"HS256","x":""" + ("[" * 64) + ("]" * 64) + "}"
  val deepToken  = s"${Base64Url.encode(deepHeader.getBytes(UTF_8))}.${segments(1)}.${segments(2)}"

  report(Jwt.decode(deepToken, secret, Algorithm.HS256, catalogOptions))

  val noneHeader      = Base64Url.encode("""{"alg":"none","typ":"JWT"}""".getBytes(UTF_8))
  val unsignedToken   = s"$noneHeader.${segments(1)}."
  val noneWithGarbage = s"$noneHeader.${segments(1)}.${segments(2)}"

  report(Jwt.decode(unsignedToken, secret, Algorithm.HS256, catalogOptions))
  report(Jwt.decode(noneWithGarbage, secret, Algorithm.HS256, catalogOptions))

  val publicEndpointLimits = JwtLimits(
    maxTokenChars = 2048,
    maxSegmentChars = 1024,
    maxJsonChars = 1024,
    maxDepth = 4,
    maxFields = 32,
    maxArrayElements = 32
  )

  val publicOptions = catalogOptions.copy(limits = publicEndpointLimits)

  report(Jwt.decode(token, secret, Algorithm.HS256, publicOptions))
  report(Jwt.decode("a" * 4000, secret, Algorithm.HS256, publicOptions))
}
