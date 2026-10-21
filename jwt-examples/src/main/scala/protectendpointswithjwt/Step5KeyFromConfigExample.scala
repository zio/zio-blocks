package protectendpointswithjwt

import java.nio.charset.StandardCharsets.UTF_8
import zio.blocks.config.{Config, ConfigSource, Secret}
import zio.blocks.jwt._
import zio.blocks.schema.Schema

import Step3VerifyTokenExample.{Verifier, jwtVerifier}

/**
 * How to Protect an Endpoint with JWT Bearer Tokens: Step 5 — Loading the Key
 * From Configuration
 *
 * Loads the issuer, the audience and the HMAC secret with `Config.load`, keeps
 * the secret in a `Secret` so it never shows up in logs, and refuses to start
 * with a secret shorter than HS256 needs.
 *
 * Run with: cd jwt-examples && sbt "runMain
 * protectendpointswithjwt.Step5KeyFromConfigExample"
 */
object Step5KeyFromConfigExample {

  final case class AuthConfig(issuer: String, audience: String, hmacSecret: Secret)

  object AuthConfig {
    implicit val schema: Schema[AuthConfig] = Schema.derived
  }

  def verifierFrom(config: AuthConfig, nowSeconds: Option[Long] = None): Either[String, Verifier] = {
    val key = Secret.unwrap(config.hmacSecret).getBytes(UTF_8)
    if (key.length < 32) Left(s"hmacSecret has ${key.length} bytes; HS256 needs at least 32")
    else
      Right(
        jwtVerifier(
          key,
          JwtDecodeOptions(
            issuer = Some(config.issuer),
            expectedAudience = Some(config.audience),
            clockSkewSeconds = 30,
            nowSeconds = nowSeconds
          )
        )
      )
  }

  // In production these values come from the environment or a config file; see the typed config guide.
  val settings: Map[String, String] = Map(
    "issuer"     -> "bookstore-auth",
    "audience"   -> "bookstore-api",
    "hmacSecret" -> TestTokens.secret
  )

  def startWith(settings: Map[String, String]): Unit =
    Config.load[AuthConfig](ConfigSource.fromMap(settings, "env")) match {
      case Left(errors)  => errors.foreach(error => println(s"cannot start: ${error.message}"))
      case Right(config) =>
        println(s"loaded $config")
        verifierFrom(config, nowSeconds = Some(TestTokens.now + 60)) match {
          case Left(problem) => println(s"cannot start: $problem")
          case Right(verify) => println(s"reader token: ${verify(TestTokens.reader)}")
        }
    }

  def main(args: Array[String]): Unit = {
    startWith(settings)
    startWith(settings - "hmacSecret")
    startWith(settings.updated("hmacSecret", "too-short"))
  }
}
