# JWT

`zio-blocks-jwt` is a zero-dependency, cross-platform (JVM + Scala.js) JWT library for Scala 2.13 and Scala 3.

## Getting Started

Add the dependency to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-jwt" % "<version>"
```

Install a platform backend **once** at application startup:

```scala
import zio.blocks.jwt._

// JVM
JvmJwtCryptoBackend.install()

// Scala.js (Node.js)
JsJwtCryptoBackend.install()
```

## Signing

```scala
val key    = "your-256-bit-secret".getBytes("UTF-8")
val claims = JwtClaims(sub = Some("user-123"), iss = Some("my-app"))

val token: Either[JwtError, String] =
  Jwt.sign(claims, key, Algorithm.HS256)
```

## Decoding and Verifying

```scala
val result: Either[JwtError, JwtClaims] =
  Jwt.decode(token, key, Algorithm.HS256, clockSkewSeconds = 30L, issuer = Some("my-app"))
```

## Claims

`JwtClaims` models the RFC 7519 registered claims plus arbitrary extra claims:

| Field   | Type                                  | RFC claim |
|---------|---------------------------------------|-----------|
| `iss`   | `Option[String]`                      | Issuer    |
| `sub`   | `Option[String]`                      | Subject   |
| `aud`   | `Option[Either[String, List[String]]]`| Audience  |
| `exp`   | `Option[Long]`                        | Expiration (Unix seconds) |
| `nbf`   | `Option[Long]`                        | Not Before (Unix seconds) |
| `iat`   | `Option[Long]`                        | Issued At (Unix seconds)  |
| `jti`   | `Option[String]`                      | JWT ID    |
| `extra` | `Map[String, JwtJson.Value]`          | Custom claims, preserving JSON type |

### Extra claims

Non-reserved claims are preserved with their original JSON type:

```scala
val claims = JwtClaims(
  sub   = Some("user-123"),
  extra = Map(
    "role"  -> JwtJson.StringValue("admin"),
    "level" -> JwtJson.NumberValue("3"),
    "active"-> JwtJson.BooleanValue(true)
  )
)
```

### Audience

Per RFC 7519 §4.1.3, `aud` may be a single string or an array:

```scala
// single principal
val c1 = JwtClaims(aud = Some(Left("my-service")))

// multiple principals
val c2 = JwtClaims(aud = Some(Right(List("service-a", "service-b"))))
```

## Supported Algorithms

| Algorithm | JVM | JS (Node.js) | Pure Scala |
|-----------|-----|--------------|-----------|
| HS256     | ✓   | ✓            | ✓         |
| HS384     | ✓   | ✓            | ✓         |
| HS512     | ✓   | ✓            | ✓         |
| RS256     | ✓   | ✓            | —         |
| RS384     | ✓   | ✓            | —         |
| RS512     | ✓   | ✓            | —         |
| PS256     | ✓   | —            | —         |
| PS384     | ✓   | —            | —         |
| PS512     | ✓   | —            | —         |
| ES256     | ✓   | ✓            | —         |
| ES384     | ✓   | ✓            | —         |
| ES512     | ✓   | ✓            | —         |
| EdDSA     | ✓   | —            | —         |

Query what a backend supports at runtime:

```scala
JvmJwtCryptoBackend.supportedAlgorithms  // Set[Algorithm]
```

## Error Handling

All errors are represented as `JwtError` subtypes (no stack traces):

| Error                | Meaning                                      |
|----------------------|----------------------------------------------|
| `InvalidToken`       | Malformed structure or bad claim value       |
| `ExpiredToken`       | `exp` is in the past                         |
| `NotYetValid`        | `nbf` is in the future                       |
| `InvalidSignature`   | Signature did not verify                     |
| `UnsupportedAlgorithm` | Backend does not support the algorithm     |
| `MissingClaim`       | Required claim absent                        |
| `AlgorithmMismatch`  | `alg` header differs from requested algorithm|

## Base64URL Encoding

`Base64Url` (package-private) encodes/decodes with no padding (`=`), per RFC 7515 §2.
Tokens containing `=` characters are rejected with `InvalidToken`.
