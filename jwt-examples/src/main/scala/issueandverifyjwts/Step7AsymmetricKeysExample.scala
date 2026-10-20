package issueandverifyjwts

import zio.blocks.jwt._
import java.nio.charset.StandardCharsets.UTF_8
import java.security.KeyPairGenerator
import java.util.Base64

/**
 * How to Issue and Verify JWTs: Step 7 — Signing With an Asymmetric Key Pair
 *
 * Signs with RSA and Ed25519 private keys, verifies with the public keys,
 * rejects an algorithm-confusion token, and loads a public key from PEM text.
 *
 * Run with: cd jwt-examples && sbt "runMain
 * issueandverifyjwts.Step7AsymmetricKeysExample"
 */
object Step7AsymmetricKeysExample extends App {

  // 2026-01-01T00:00:00Z. A fixed "now" keeps every result reproducible.
  val now: Long = 1767225600L

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

  val catalogOptions = JwtDecodeOptions(
    issuer = Some("bookstore-auth"),
    expectedAudience = Some("catalog-api"),
    nowSeconds = Some(now + 60)
  )

  JvmJwtCryptoBackend.install()

  val rsaGenerator = KeyPairGenerator.getInstance("RSA")
  rsaGenerator.initialize(2048)
  val authServiceKeys = rsaGenerator.generateKeyPair()

  val privateKeyDer: Array[Byte] = authServiceKeys.getPrivate.getEncoded // stays in the auth service
  val publicKeyDer: Array[Byte]  = authServiceKeys.getPublic.getEncoded  // shared with the catalog API

  println(authServiceKeys.getPrivate.getFormat)
  println(authServiceKeys.getPublic.getFormat)

  val rsaToken: String = Jwt.sign(claims, privateKeyDer, Algorithm.RS256).fold(e => throw e, t => t)

  val strangerKeys = rsaGenerator.generateKeyPair()

  report(Jwt.decode(rsaToken, publicKeyDer, Algorithm.RS256, catalogOptions))
  report(Jwt.decode(rsaToken, strangerKeys.getPublic.getEncoded, Algorithm.RS256, catalogOptions))

  val confusedToken: String =
    Jwt.sign(claims.copy(sub = Some("reader-0007")), publicKeyDer, Algorithm.HS256).fold(e => throw e, t => t)

  report(Jwt.decode(confusedToken, publicKeyDer, Algorithm.RS256, catalogOptions))

  val edKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

  val edToken: String = Jwt.sign(claims, edKeys.getPrivate.getEncoded, Algorithm.EdDSA).fold(e => throw e, t => t)

  report(Jwt.decode(edToken, edKeys.getPublic.getEncoded, Algorithm.EdDSA, catalogOptions))

  def pemToDer(pem: String): Array[Byte] =
    Base64.getMimeDecoder.decode(pem.linesIterator.filterNot(_.startsWith("-----")).mkString)

  val publicKeyPem =
    "-----BEGIN PUBLIC KEY-----\n" +
      Base64.getMimeEncoder(64, "\n".getBytes(UTF_8)).encodeToString(publicKeyDer) +
      "\n-----END PUBLIC KEY-----\n"

  report(Jwt.decode(rsaToken, pemToDer(publicKeyPem), Algorithm.RS256, catalogOptions))
}
