---
id: issue-and-verify-jwts
title: "How to Issue and Verify JWTs"
description: "Sign JWTs with HMAC, RSA or EdDSA keys, verify iss, aud and exp with a fixed clock, and reject tampered or hostile tokens."
keywords:
  - "Authentication"
  - "JSON Web Token"
  - "Token Verification"
  - "Jwt"
  - "JwtClaims"
  - "JwtDecodeOptions"
---

In this guide, we will issue a JSON Web Token (JWT) for a reader of a Scala bookstore and verify it the way an API would. By the end, you will have a small program that signs a token with `Jwt.sign`, checks it with `Jwt.decode`, and turns every kind of bad token into a typed `JwtError`.

The program plays both sides in memory, with no HTTP server and a fixed clock, so that every result below is the same on every run:

- the **bookstore auth service** issues tokens, and
- the **catalog API** verifies them before showing a reader their purchased books.

We start with a shared secret (HMAC), then make the token fail in every way we can think of, and end with the realistic setup: an RSA or EdDSA key pair, where only the auth service can sign.

## The Problem

A service that receives a token has to decide, from the string alone, whether to trust it. It must check that the signature is valid, that the token was signed with the algorithm it expects, that it was issued by the right party for the right audience, and that it has not expired. It must also refuse tokens that are built to waste its memory or CPU.

If you write this by hand, it is easy to get one of these checks wrong, and each mistake is a security hole. Here is what a first attempt with only the JDK often looks like:

```scala mdoc:compile-only
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

def handRolledVerify(token: String, secret: Array[Byte]): Option[String] = {
  val parts = token.split('.')
  if (parts.length != 3) None
  else {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec(secret, "HmacSHA256"))
    val signingInput = s"${parts(0)}.${parts(1)}".getBytes(UTF_8)
    val expected     = Base64.getUrlEncoder.withoutPadding.encodeToString(mac.doFinal(signingInput))
    if (expected == parts(2)) Some(new String(Base64.getUrlDecoder.decode(parts(1)), UTF_8))
    else None
  }
}
```

This code compiles and even works for a valid token, but it never reads the header's `alg`, compares signatures with a non-constant-time `==`, accepts tokens of any size, and returns raw JSON that still has to be parsed and checked for `exp`, `iss` and `aud`. Supporting RSA or ECDSA keys would add key parsing and signature format conversion on top.

The ZIO Blocks `jwt` module does these checks for us, in a fixed order, and returns a `JwtError` value that says exactly what was wrong.

## Prerequisites

Add the module to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-jwt" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies += "dev.zio" %% "zio-blocks-next-jwt" % "@VERSION@"
```

The module depends only on ZIO Blocks `chunk`. Its API is synchronous: no effect type, no I/O. You should know what a JWT is at a high level: three Base64URL segments, `header.payload.signature`. The [JWT reference](../reference/jwt.md) covers the full API.

Throughout the guide we use these imports, a fixed clock and a sample secret:

```scala mdoc:silent
import zio.blocks.jwt._
import java.nio.charset.StandardCharsets.UTF_8

// 2026-01-01T00:00:00Z. A fixed "now" keeps every result in this guide reproducible.
val now: Long = 1767225600L

// Sample data only. A real HS256 secret is at least 32 random bytes, loaded from a secret store.
val secret: Array[Byte] = "sample-bookstore-secret-32-bytes".getBytes(UTF_8)
```

We also add a tiny helper that prints the outcome of a verification. It is used for every check below:

```scala mdoc:silent
def report(result: Either[JwtError, JwtClaims]): Unit = result match {
  case Right(claims) => println(s"accepted: sub=${claims.sub.getOrElse("-")}")
  case Left(error)   => println(s"rejected: ${error.getClass.getSimpleName}: ${error.message}")
}
```

## Issuing a Token

The auth service describes the reader in a `JwtClaims` value. The registered claims have their own fields: `iss` (who issued the token), `sub` (who it is about), `aud` (who it is for), and `iat` and `exp` (issued-at and expiry, in seconds since the epoch). Anything else goes into `extra` as a `JwtValue`. Here we record the reader's purchased titles:

```scala mdoc:silent
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
```

`Jwt.sign` returns `Either[JwtError, String]`. It fails, for example, when the key is too short for the algorithm. `JwtError` extends `Throwable`, so we can throw it at startup if signing is impossible. Here is the token:

```scala mdoc
println(token)
```

The first two segments are only Base64URL-encoded JSON. `Base64Url.decode` turns them back into text:

```scala mdoc:silent
val segments = token.split('.')

def decodeSegment(segment: String): String =
  Base64Url.decode(segment).map(bytes => new String(bytes, UTF_8)).fold(_.message, identity)
```

```scala mdoc
println(decodeSegment(segments(0)))
println(decodeSegment(segments(1)))
```

:::warning
A JWT is **signed, not encrypted**. Anyone who holds the token can read every claim in it. Never put passwords, card numbers or other secrets in claims.
:::

## Verifying a Token

The catalog API verifies the token with the same secret and algorithm. `JwtDecodeOptions` says what else to check. `issuer` and `expectedAudience` must match the token's `iss` and `aud`. `nowSeconds` replaces the system clock, which we do here so that the expiry check gives the same answer on every run:

```scala mdoc:silent
val catalogOptions = JwtDecodeOptions(
  issuer = Some("bookstore-auth"),
  expectedAudience = Some("catalog-api"),
  nowSeconds = Some(now + 60)
)

val verified: Either[JwtError, JwtClaims] =
  Jwt.decode(token, secret, Algorithm.HS256, catalogOptions)
```

On success we get back the claims, and we can read the custom `titles` claim from `extra`:

```scala mdoc
println(verified)
println(verified.map(_.extra.get("titles")))
```

The algorithm argument is the one the **verifier** expects, not the one the token claims. The next sections show why that matters.

:::note
In production, leave `nowSeconds` unset (it defaults to `None`). The check then uses `System.currentTimeMillis() / 1000`.
:::

## Reading the Token From an Authorization Header

Over HTTP, a client usually sends the token in an `Authorization` header with the `Bearer` scheme. The module does not deal with headers, so the job is a string operation: take the header value, check the scheme, and pass what follows to `Jwt.decode`. The scheme name is case-insensitive, so we compare it that way:

```scala mdoc:silent
def bearerToken(headerValue: String): Option[String] = {
  val scheme = "Bearer "
  if (headerValue.regionMatches(true, 0, scheme, 0, scheme.length))
    Some(headerValue.substring(scheme.length).trim)
  else None
}

val authorization = s"Bearer $token"
```

```scala mdoc
println(s"Authorization: $authorization")

bearerToken(authorization) match {
  case Some(bearer) => report(Jwt.decode(bearer, secret, Algorithm.HS256, catalogOptions))
  case None         => println("rejected: no bearer token")
}
```

Wiring this into a real server's request pipeline is a separate topic. ZIO Blocks has no HTTP server of its own, so the code above is all the module needs from your framework.

## Seeing Every Failure

Every way a token can be wrong is a small edit to a string or to the decode options. We make them one at a time and look at the error each gives.

First, an attacker rewrites the payload to claim another reader's identity and keeps the original signature:

```scala mdoc:silent
val forgedPayload = Base64Url.encode(decodeSegment(segments(1)).replace("reader-1024", "reader-0007").getBytes(UTF_8))
val tampered      = s"${segments(0)}.$forgedPayload.${segments(2)}"
```

```scala mdoc
report(Jwt.decode(tampered, secret, Algorithm.HS256, catalogOptions))
```

The signature covers the header and payload, so any change breaks it. The payload is not even parsed: `Jwt.decode` checks the signature before it reads any claim.

A verifier configured with a different secret also rejects the token, with the same error:

```scala mdoc:silent
val otherSecret: Array[Byte] = "another-service-secret-32-bytes!".getBytes(UTF_8)
```

```scala mdoc
report(Jwt.decode(token, otherSecret, Algorithm.HS256, catalogOptions))
```

If the verifier expects a different algorithm from the one in the token's header, the token is rejected before any signature is computed:

```scala mdoc
report(Jwt.decode(token, secret, Algorithm.HS512, catalogOptions))
```

A token issued for another service fails the audience check, and a token with no `aud` at all fails it too, once `expectedAudience` is set:

```scala mdoc:silent
val noAudienceToken: String = Jwt.sign(claims.copy(aud = None), secret, Algorithm.HS256).fold(e => throw e, t => t)
```

```scala mdoc
report(Jwt.decode(token, secret, Algorithm.HS256, catalogOptions.copy(expectedAudience = Some("orders-api"))))
report(Jwt.decode(noAudienceToken, secret, Algorithm.HS256, catalogOptions))
```

The issuer check works the same way:

```scala mdoc
report(Jwt.decode(token, secret, Algorithm.HS256, catalogOptions.copy(issuer = Some("partner-auth"))))
```

`JwtError` is a sealed trait, so a verifier can match on these cases to decide what to log or return. An HTTP API usually answers all of them with `401 Unauthorized` and keeps the detail in its logs.

:::tip
`issuer` and `expectedAudience` are only checked when you set them. With the default `JwtDecodeOptions`, a token issued by anyone you share the key with, for any audience, is accepted. Set both whenever one key is used by more than one service.
:::

## Testing Expiry Without Waiting

Tests that wait an hour for a token to expire are not tests anyone runs. Because `nowSeconds` replaces the clock, we can move "now" instead. Our token expires at `now + 3600`. Verified at `now + 7200`, it is expired:

```scala mdoc
report(Jwt.decode(token, secret, Algorithm.HS256, catalogOptions.copy(nowSeconds = Some(now + 7200))))
```

Clocks on different machines drift, so `clockSkewSeconds` gives a grace period. A token counts as expired only when `now - clockSkewSeconds > exp`. With a 60-second skew, the token is still accepted 30 seconds after `exp` and rejected 61 seconds after:

```scala mdoc:silent
val expiresAt   = now + 3600
val withSkew    = catalogOptions.copy(clockSkewSeconds = 60L)
```

```scala mdoc
report(Jwt.decode(token, secret, Algorithm.HS256, withSkew.copy(nowSeconds = Some(expiresAt + 30))))
report(Jwt.decode(token, secret, Algorithm.HS256, withSkew.copy(nowSeconds = Some(expiresAt + 61))))
```

The skew also applies to `nbf` ("not before"). Suppose the bookstore issues a token for a pre-order that unlocks ten minutes from now. Before `nbf` it is not yet valid, and from `nbf` on it is accepted:

```scala mdoc:silent
val preOrderToken: String =
  Jwt.sign(claims.copy(nbf = Some(now + 600)), secret, Algorithm.HS256).fold(e => throw e, t => t)
```

```scala mdoc
report(Jwt.decode(preOrderToken, secret, Algorithm.HS256, catalogOptions.copy(nowSeconds = Some(now))))
report(Jwt.decode(preOrderToken, secret, Algorithm.HS256, catalogOptions.copy(nowSeconds = Some(now + 600))))
```

Finally, `exp` and `nbf` are only checked when they are present. A token without `exp` never expires:

```scala mdoc:silent
val foreverToken: String = Jwt.sign(claims.copy(exp = None), secret, Algorithm.HS256).fold(e => throw e, t => t)
```

```scala mdoc
report(Jwt.decode(foreverToken, secret, Algorithm.HS256, catalogOptions.copy(nowSeconds = Some(now + 10L * 365 * 24 * 3600))))
```

:::warning
The module does not require an `exp` claim. If your service must only accept tokens that expire, check `claims.exp.isDefined` yourself after a successful `Jwt.decode`.
:::

## Rejecting Hostile Tokens

A public endpoint receives tokens from anyone, including tokens built to exhaust memory or to trick the verifier. `Jwt.decode` checks sizes before it parses, and parses the header before it verifies the signature, so these checks run even when the attacker has no key. The limits live in `JwtLimits`, and the defaults are `maxTokenChars = 8192`, `maxSegmentChars = 4096`, `maxJsonChars = 8192`, `maxDepth = 32`, `maxFields = 512` and `maxArrayElements = 512`.

A token over `maxTokenChars` is rejected before anything else happens:

```scala mdoc
report(Jwt.decode("a" * 10000, secret, Algorithm.HS256, catalogOptions))
```

A header that nests arrays deeper than `maxDepth` is rejected while the header is parsed, before the signature is checked:

```scala mdoc:silent
val deepHeader = """{"alg":"HS256","x":""" + ("[" * 64) + ("]" * 64) + "}"
val deepToken  = s"${Base64Url.encode(deepHeader.getBytes(UTF_8))}.${segments(1)}.${segments(2)}"
```

```scala mdoc
report(Jwt.decode(deepToken, secret, Algorithm.HS256, catalogOptions))
```

The classic `alg: none` attack sends an unsigned token and hopes the verifier skips the signature check. Both of its forms are rejected, with an empty signature segment and with any other signature segment:

```scala mdoc:silent
val noneHeader      = Base64Url.encode("""{"alg":"none","typ":"JWT"}""".getBytes(UTF_8))
val unsignedToken   = s"$noneHeader.${segments(1)}."
val noneWithGarbage = s"$noneHeader.${segments(1)}.${segments(2)}"
```

```scala mdoc
report(Jwt.decode(unsignedToken, secret, Algorithm.HS256, catalogOptions))
report(Jwt.decode(noneWithGarbage, secret, Algorithm.HS256, catalogOptions))
```

`none` is not one of the module's `Algorithm` values, so no backend can accept it.

For a public endpoint, the defaults are generous. Our token is a few hundred characters with a payload two levels deep, so we can tighten every limit and still accept it:

```scala mdoc:silent
val publicEndpointLimits = JwtLimits(
  maxTokenChars = 2048,
  maxSegmentChars = 1024,
  maxJsonChars = 1024,
  maxDepth = 4,
  maxFields = 32,
  maxArrayElements = 32
)

val publicOptions = catalogOptions.copy(limits = publicEndpointLimits)
```

```scala mdoc
report(Jwt.decode(token, secret, Algorithm.HS256, publicOptions))
report(Jwt.decode("a" * 4000, secret, Algorithm.HS256, publicOptions))
```

## Signing With an Asymmetric Key Pair

With HMAC, every service that verifies tokens holds the secret, so every one of them could also issue tokens. The realistic setup uses a key pair instead: the auth service signs with a **private key** that never leaves it, and the catalog API verifies with the matching **public key**, which can be shared freely.

Asymmetric algorithms need a platform crypto backend. On the JVM, install it once at application startup. HMAC algorithms work without it:

```scala mdoc:silent
JvmJwtCryptoBackend.install()
```

We generate a 2048-bit RSA key pair in memory with the JDK's `KeyPairGenerator`. The module takes keys as DER bytes: the private key in **PKCS#8** format and the public key in **X.509** (SubjectPublicKeyInfo) format. That is exactly what the JDK's `getEncoded` returns:

```scala mdoc:silent
import java.security.KeyPairGenerator

val rsaGenerator = KeyPairGenerator.getInstance("RSA")
rsaGenerator.initialize(2048)
val authServiceKeys = rsaGenerator.generateKeyPair()

val privateKeyDer: Array[Byte] = authServiceKeys.getPrivate.getEncoded // stays in the auth service
val publicKeyDer: Array[Byte]  = authServiceKeys.getPublic.getEncoded  // shared with the catalog API
```

```scala mdoc
println(authServiceKeys.getPrivate.getFormat)
println(authServiceKeys.getPublic.getFormat)
```

Signing and verifying look the same as before, with `Algorithm.RS256` and a different key on each side:

```scala mdoc:silent
val rsaToken: String = Jwt.sign(claims, privateKeyDer, Algorithm.RS256).fold(e => throw e, t => t)

val strangerKeys = rsaGenerator.generateKeyPair()
```

```scala mdoc
report(Jwt.decode(rsaToken, publicKeyDer, Algorithm.RS256, catalogOptions))
report(Jwt.decode(rsaToken, strangerKeys.getPublic.getEncoded, Algorithm.RS256, catalogOptions))
```

The second check uses a public key from a different key pair, so the signature does not verify.

The public key is public, which opens the door to **algorithm confusion**: an attacker signs a forged token with HS256, using the public key's bytes as the HMAC secret, and hopes the verifier trusts the token's `alg` header. Because the verifier passes the algorithm it expects, the forged token is rejected before any key is used:

```scala mdoc:silent
val confusedToken: String =
  Jwt.sign(claims.copy(sub = Some("reader-0007")), publicKeyDer, Algorithm.HS256).fold(e => throw e, t => t)
```

```scala mdoc
report(Jwt.decode(confusedToken, publicKeyDer, Algorithm.RS256, catalogOptions))
```

The same pattern works for ECDSA (`ES256`, `ES384`, `ES512`) and EdDSA (Ed25519). Only the key generator and the algorithm change:

```scala mdoc:silent
val edKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

val edToken: String = Jwt.sign(claims, edKeys.getPrivate.getEncoded, Algorithm.EdDSA).fold(e => throw e, t => t)
```

```scala mdoc
report(Jwt.decode(edToken, edKeys.getPublic.getEncoded, Algorithm.EdDSA, catalogOptions))
```

On the JVM, the backend rejects RSA keys shorter than 2048 bits and EC keys on the wrong curve for the algorithm with `InvalidKey`. On Scala.js (Node.js), the backend supports HMAC, RSA and ECDSA but not PS256/384/512 or EdDSA, and it does not check RSA key size or EC curves.

### Loading Keys From PEM Files

Keys usually arrive as PEM files: Base64 text between `-----BEGIN ...-----` and `-----END ...-----` lines. The module takes DER bytes, and PEM is just DER in Base64, so the conversion is two steps: drop the marker lines and Base64-decode the rest.

```scala mdoc:silent
import java.util.Base64

def pemToDer(pem: String): Array[Byte] =
  Base64.getMimeDecoder.decode(pem.linesIterator.filterNot(_.startsWith("-----")).mkString)
```

To check it, we write our public key out as PEM, the way it would appear in a file, and read it back:

```scala mdoc:silent
val publicKeyPem =
  "-----BEGIN PUBLIC KEY-----\n" +
    Base64.getMimeEncoder(64, "\n".getBytes(UTF_8)).encodeToString(publicKeyDer) +
    "\n-----END PUBLIC KEY-----\n"
```

```scala mdoc
report(Jwt.decode(rsaToken, pemToDer(publicKeyPem), Algorithm.RS256, catalogOptions))
```

The marker line tells you the format inside, and only two of them match what the module expects:

| PEM marker                       | Format inside                 | Use as is? |
|----------------------------------|-------------------------------|------------|
| `BEGIN PRIVATE KEY`              | PKCS#8                        | Yes, for signing |
| `BEGIN PUBLIC KEY`               | X.509 SubjectPublicKeyInfo    | Yes, for verifying |
| `BEGIN RSA PRIVATE KEY`          | PKCS#1                        | No: convert with `openssl pkcs8 -topk8 -nocrypt -in key.pem` |
| `BEGIN EC PRIVATE KEY`           | SEC1                          | No: convert with `openssl pkcs8 -topk8 -nocrypt -in key.pem` |
| `BEGIN RSA PUBLIC KEY`           | PKCS#1                        | No: convert with `openssl rsa -RSAPublicKey_in -pubout -in key.pem` |
| `BEGIN ENCRYPTED PRIVATE KEY`    | Encrypted PKCS#8              | No: decrypt first |

A key in the wrong format is rejected with `InvalidKey`.

## What the Module Does Not Do

Before you rely on the module, know its limits:

- **No JWK or JWKS.** You cannot point it at an identity provider's key-set URL, and it does not pick a key by the header's `kid`. Fetch the provider's public key yourself, convert it to X.509 DER, and verify with it as shown above.
- **No encryption (JWE).** Tokens are signed only, so their claims are readable by anyone who holds them.
- **No required claims beyond what you configure.** `exp`, `nbf`, `iss` and `aud` are checked only when present or configured, and `typ`, `iat` and `jti` are never checked.
- **A new API.** The module is new and has no binary-compatibility baseline yet, so expect API changes between releases.

`Jwt.decodeWithoutVerification` reads a token's header and claims **without checking its signature**. Use it only to inspect a token, for example while debugging. Its output is whatever the sender wrote, so never use it to make an access decision.

## Putting It Together

This example combines everything from the guide in one program: the auth service issues an RSA-signed token, the catalog API reads it from an `Authorization` header and verifies it with tightened limits and a fixed clock, and every failure from the guide is printed:

```scala mdoc:embed:jwt-examples/src/main/scala/issueandverifyjwts/CompleteExample.scala
```

## Running the Examples

All examples in this guide have runnable Scala files in the `jwt-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/jwt-examples
```

### Issue a Token

This example signs the reader's claims with HS256 and decodes the header and payload to show that they are plain JSON.

<details>
  <summary>jwt-examples/src/main/scala/issueandverifyjwts/Step1IssueTokenExample.scala</summary>

```scala mdoc:embed:jwt-examples/src/main/scala/issueandverifyjwts/Step1IssueTokenExample.scala:show-line-numbers
```

</details>

Run it from the `jwt-examples` directory:

```bash
sbt "runMain issueandverifyjwts.Step1IssueTokenExample"
```

### Verify a Token

This example verifies the token with `issuer`, `expectedAudience` and a fixed clock, and reads the custom `titles` claim.

<details>
  <summary>jwt-examples/src/main/scala/issueandverifyjwts/Step2VerifyTokenExample.scala</summary>

```scala mdoc:embed:jwt-examples/src/main/scala/issueandverifyjwts/Step2VerifyTokenExample.scala:show-line-numbers
```

</details>

Run it from the `jwt-examples` directory:

```bash
sbt "runMain issueandverifyjwts.Step2VerifyTokenExample"
```

### Read the Token From an Authorization Header

This example extracts the token from a `Bearer` header value and verifies it.

<details>
  <summary>jwt-examples/src/main/scala/issueandverifyjwts/Step3BearerHeaderExample.scala</summary>

```scala mdoc:embed:jwt-examples/src/main/scala/issueandverifyjwts/Step3BearerHeaderExample.scala:show-line-numbers
```

</details>

Run it from the `jwt-examples` directory:

```bash
sbt "runMain issueandverifyjwts.Step3BearerHeaderExample"
```

### See Every Failure

This example tampers with the payload and verifies with the wrong secret, algorithm, audience and issuer.

<details>
  <summary>jwt-examples/src/main/scala/issueandverifyjwts/Step4FailuresExample.scala</summary>

```scala mdoc:embed:jwt-examples/src/main/scala/issueandverifyjwts/Step4FailuresExample.scala:show-line-numbers
```

</details>

Run it from the `jwt-examples` directory:

```bash
sbt "runMain issueandverifyjwts.Step4FailuresExample"
```

### Test Expiry Without Waiting

This example moves the clock to show `ExpiredToken`, clock skew, `NotYetValid`, and a token without `exp`.

<details>
  <summary>jwt-examples/src/main/scala/issueandverifyjwts/Step5ExpiryExample.scala</summary>

```scala mdoc:embed:jwt-examples/src/main/scala/issueandverifyjwts/Step5ExpiryExample.scala:show-line-numbers
```

</details>

Run it from the `jwt-examples` directory:

```bash
sbt "runMain issueandverifyjwts.Step5ExpiryExample"
```

### Reject Hostile Tokens

This example sends an oversized token, a deeply nested header and `alg: none` tokens, and tightens `JwtLimits`.

<details>
  <summary>jwt-examples/src/main/scala/issueandverifyjwts/Step6HostileTokensExample.scala</summary>

```scala mdoc:embed:jwt-examples/src/main/scala/issueandverifyjwts/Step6HostileTokensExample.scala:show-line-numbers
```

</details>

Run it from the `jwt-examples` directory:

```bash
sbt "runMain issueandverifyjwts.Step6HostileTokensExample"
```

### Sign With an Asymmetric Key Pair

This example signs with RSA and Ed25519 private keys, verifies with the public keys, rejects an algorithm-confusion token, and loads a public key from PEM text.

<details>
  <summary>jwt-examples/src/main/scala/issueandverifyjwts/Step7AsymmetricKeysExample.scala</summary>

```scala mdoc:embed:jwt-examples/src/main/scala/issueandverifyjwts/Step7AsymmetricKeysExample.scala:show-line-numbers
```

</details>

Run it from the `jwt-examples` directory:

```bash
sbt "runMain issueandverifyjwts.Step7AsymmetricKeysExample"
```

### The Complete Example

The complete example from "Putting It Together" runs everything in one program:

```bash
sbt "runMain issueandverifyjwts.CompleteExample"
```

### Compile All Examples

To compile every example in the project without running any of them, run this from the `jwt-examples` directory:

```bash
sbt compile
```

## Going Further

- The [JWT reference](../reference/jwt.md) lists every algorithm, key size rule, limit and `JwtError` case.
- Use `JwtHeader(alg, kid = Some("..."))` with the `Jwt.sign` overload that takes a header to label which key signed a token. The module writes `kid` but does not use it to choose a key, so the verifier picks the key itself.
- [How to Protect an Endpoint with JWT Bearer Tokens](./protect-endpoints-with-jwt.md) wires the verification into an `http-model` endpoint that reads the `Authorization` header and answers 401 or 403.
