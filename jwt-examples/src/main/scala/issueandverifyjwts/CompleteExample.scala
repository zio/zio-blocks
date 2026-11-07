package issueandverifyjwts

import zio.blocks.jwt._
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyPairGenerator

/**
 * How to Issue and Verify JWTs: Putting It Together — Complete Example
 *
 * The auth service issues an RSA-signed token, the catalog API reads it from an
 * Authorization header and verifies it with tightened limits and a fixed clock,
 * and every failure from the guide is printed.
 *
 * Run with: cd jwt-examples && sbt "runMain issueandverifyjwts.CompleteExample"
 */
object CompleteExample extends App {

  // 2026-01-01T00:00:00Z. A fixed "now" keeps every result reproducible.
  val now: Long = 1767225600L

  def report(result: Either[JwtError, JwtClaims]): Unit = result match {
    case Right(claims) => println(s"accepted: sub=${claims.sub.getOrElse("-")}")
    case Left(error)   => println(s"rejected: ${error.getClass.getSimpleName}: ${error.message}")
  }

  def signOrThrow(claims: JwtClaims, key: Array[Byte], algorithm: Algorithm): String =
    Jwt.sign(claims, key, algorithm).fold(e => throw e, t => t)

  // Asymmetric algorithms need a platform crypto backend, installed once at startup.
  JvmJwtCryptoBackend.install()

  // The bookstore auth service holds the private key and issues tokens.
  val rsaGenerator = KeyPairGenerator.getInstance("RSA")
  rsaGenerator.initialize(2048)
  val authServiceKeys = rsaGenerator.generateKeyPair()

  val privateKeyDer: Array[Byte] = authServiceKeys.getPrivate.getEncoded // stays in the auth service
  val publicKeyDer: Array[Byte]  = authServiceKeys.getPublic.getEncoded  // shared with the catalog API

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

  val token: String = signOrThrow(claims, privateKeyDer, Algorithm.RS256)

  // The catalog API reads the token from the Authorization header and verifies it.
  def bearerToken(headerValue: String): Option[String] = {
    val scheme = "Bearer "
    if (headerValue.regionMatches(true, 0, scheme, 0, scheme.length))
      Some(headerValue.substring(scheme.length).trim)
    else None
  }

  val catalogOptions = JwtDecodeOptions(
    issuer = Some("bookstore-auth"),
    expectedAudience = Some("catalog-api"),
    nowSeconds = Some(now + 60),
    limits = JwtLimits(
      maxTokenChars = 2048,
      maxSegmentChars = 1024,
      maxJsonChars = 1024,
      maxDepth = 4,
      maxFields = 32,
      maxArrayElements = 32
    )
  )

  def verify(bearer: String, options: JwtDecodeOptions = catalogOptions): Either[JwtError, JwtClaims] =
    Jwt.decode(bearer, publicKeyDer, Algorithm.RS256, options)

  val authorization = s"Bearer $token"

  println("-- valid token from the Authorization header")
  bearerToken(authorization) match {
    case Some(bearer) =>
      val verified = verify(bearer)
      report(verified)
      println(verified.map(_.extra.get("titles")))
    case None => println("rejected: no bearer token")
  }

  // Every failure from the guide.
  def decodeSegment(segment: String): String =
    Base64Url.decode(segment).map(bytes => new String(bytes, UTF_8)).fold(_.message, identity)

  val segments = token.split('.')

  println("-- tampered payload")
  val forgedPayload = Base64Url.encode(decodeSegment(segments(1)).replace("reader-1024", "reader-0007").getBytes(UTF_8))
  report(verify(s"${segments(0)}.$forgedPayload.${segments(2)}"))

  println("-- signature from a different key pair")
  val strangerKeys = rsaGenerator.generateKeyPair()
  report(Jwt.decode(token, strangerKeys.getPublic.getEncoded, Algorithm.RS256, catalogOptions))

  println("-- verifier expects a different algorithm")
  report(Jwt.decode(token, publicKeyDer, Algorithm.RS512, catalogOptions))

  println("-- algorithm confusion: HS256 signed with the public key bytes")
  report(verify(signOrThrow(claims.copy(sub = Some("reader-0007")), publicKeyDer, Algorithm.HS256)))

  println("-- wrong audience, missing audience, wrong issuer")
  report(verify(token, catalogOptions.copy(expectedAudience = Some("orders-api"))))
  report(verify(signOrThrow(claims.copy(aud = None), privateKeyDer, Algorithm.RS256)))
  report(verify(token, catalogOptions.copy(issuer = Some("partner-auth"))))

  println("-- expired, within clock skew, beyond clock skew")
  report(verify(token, catalogOptions.copy(nowSeconds = Some(now + 7200))))
  val withSkew = catalogOptions.copy(clockSkewSeconds = 60L)
  report(verify(token, withSkew.copy(nowSeconds = Some(now + 3600 + 30))))
  report(verify(token, withSkew.copy(nowSeconds = Some(now + 3600 + 61))))

  println("-- not yet valid, then valid")
  val preOrderToken = signOrThrow(claims.copy(nbf = Some(now + 600)), privateKeyDer, Algorithm.RS256)
  report(verify(preOrderToken, catalogOptions.copy(nowSeconds = Some(now))))
  report(verify(preOrderToken, catalogOptions.copy(nowSeconds = Some(now + 600))))

  println("-- hostile tokens")
  report(verify("a" * 10000))
  val deepHeader = """{"alg":"RS256","x":""" + ("[" * 64) + ("]" * 64) + "}"
  report(verify(s"${Base64Url.encode(deepHeader.getBytes(UTF_8))}.${segments(1)}.${segments(2)}"))
  val noneHeader = Base64Url.encode("""{"alg":"none","typ":"JWT"}""".getBytes(UTF_8))
  report(verify(s"$noneHeader.${segments(1)}."))
  report(verify(s"$noneHeader.${segments(1)}.${segments(2)}"))
}
