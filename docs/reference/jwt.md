---
id: jwt
title: "JWT"
---

`zio-blocks-jwt` is a zero-dependency, cross-platform (JVM + Scala.js) JWT library for Scala 2.13 and Scala 3.

## Getting Started

Add the dependency to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-jwt" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies += "dev.zio" %% "zio-blocks-next-jwt" % "@VERSION@"
```

HMAC algorithms (`HS256`, `HS384`, `HS512`) work out of the box with the built-in pure Scala backend. For RSA, RSA-PSS, ECDSA and EdDSA, install a platform backend **once** at application startup:

```scala mdoc:compile-only
import zio.blocks.jwt._
JvmJwtCryptoBackend.install()
```

For Scala.js (Node.js) use `JsJwtCryptoBackend.install()` instead (available on the JS platform with `jwtJS`).

The installed backend is global. To use a different backend for one call, pass it in `JwtSignOptions(backend = Some(...))` or `JwtDecodeOptions(backend = Some(...))`.

## Signing

```scala mdoc:compile-only
import zio.blocks.jwt._
val key    = "0123456789ABCDEF0123456789ABCDEF".getBytes("UTF-8") // 32 bytes for HS256 (JWA minimum)
val claims = JwtClaims(sub = Some("user-123"), iss = Some("my-app"))
val token: Either[JwtError, String] = Jwt.sign(claims, key, Algorithm.HS256)
```

## Decoding and Verifying

```scala mdoc:compile-only
import zio.blocks.jwt._
val key    = "0123456789ABCDEF0123456789ABCDEF".getBytes("UTF-8")
val claims = JwtClaims(sub = Some("user-123"), iss = Some("my-app"))
val token  = Jwt.sign(claims, key, Algorithm.HS256).getOrElse("")
val result: Either[JwtError, JwtClaims] =
  Jwt.decode(token, key, Algorithm.HS256, clockSkewSeconds = 30L, issuer = Some("my-app"))
```

`Jwt.decode` checks the token in this order: size limits, the three-segment structure, the header (including its `alg`), that the header's `alg` equals the `alg` argument, the signature, and only then the claims (`exp`, `nbf`, `iss`, `aud`). A tampered payload is therefore reported as `InvalidSignature`, never as a claim error.

Time checks:

- `exp` and `nbf` are only checked when present. A token without `exp` never expires; check `claims.exp.isDefined` yourself if your service requires it.
- A token is expired when `now - clockSkewSeconds > exp`, and not yet valid when `now + clockSkewSeconds < nbf`.
- `now` is `System.currentTimeMillis() / 1000` unless `JwtDecodeOptions(nowSeconds = Some(t))` fixes it, which is useful in tests.

`Jwt.decodeWithoutVerification(token)` returns the header and claims **without checking the signature or any claim**. Use it only to inspect a token; never base an access decision on its output. `Jwt.decodeUnsafe` is an older name for the same operation.

## Claims

`JwtClaims` models the RFC 7519 registered claims plus arbitrary extra claims:

| Field   | Type                                  | RFC claim |
|---------|---------------------------------------|-----------|
| `iss`   | `Option[String]`                      | Issuer    |
| `sub`   | `Option[String]`                      | Subject   |
| `aud`   | `Option[JwtAudience]`                 | Audience  |
| `exp`   | `Option[Long]`                        | Expiration (Unix seconds) |
| `nbf`   | `Option[Long]`                        | Not Before (Unix seconds) |
| `iat`   | `Option[Long]`                        | Issued At (Unix seconds)  |
| `jti`   | `Option[String]`                      | JWT ID    |
| `extra` | `Map[String, JwtValue]`                 | Custom claims, preserving JSON type |

`exp`/`nbf`/`iat` are `NumericDate` per RFC 7519 §2: JSON numbers (integer, fractional, exponent) truncated toward zero to seconds, and rejected with `InvalidClaim` if negative or out of `Long` range.

### Extra claims

`JwtValue` is the public JSON ADT for `extra` (and for `JwtClaims` round-trip):

```scala mdoc:compile-only
import zio.blocks.jwt._
import zio.blocks.chunk.Chunk
val claims = JwtClaims(
  sub   = Some("user-123"),
  extra = Map(
    "role"   -> JwtValue.Str("admin"),
    "level"  -> JwtValue.Num("3"),
    "active" -> JwtValue.Bool(true),
    "meta"   -> JwtValue.Null,
    "tags"   -> JwtValue.Arr(Chunk(JwtValue.Str("a"), JwtValue.Num("1"))),
    "profile"-> JwtValue.Obj(Map("age" -> JwtValue.Num("30"), "nested" -> JwtValue.Obj(Map("k" -> JwtValue.Str("v")))))
  )
)
```

Objects and arrays nest arbitrarily and are preserved exactly; no values are dropped during parsing. Do not put registered claim names (`iss`, `sub`, `aud`, `exp`, `nbf`, `iat`, `jti`) in `extra`; use the dedicated fields.

### Audience

Per RFC 7519 §4.1.3, `aud` may be a single string or an array. A JSON `null` `aud` in a token is treated as absent. Arrays must contain only strings; an array with any non-string element is rejected with `InvalidClaim("aud", "array must contain only strings")`:

```scala mdoc:compile-only
import zio.blocks.jwt._
import zio.blocks.chunk.Chunk
val c1 = JwtClaims(aud = Some(JwtAudience.Single("my-service")))
val c2 = JwtClaims(aud = Some(JwtAudience.Multiple(Chunk("service-a", "service-b"))))
val c3 = JwtClaims(aud = None) // absent
```

When `expectedAudience` is set in `JwtDecodeOptions`, `aud` must be present and equal (or, for an array, contain) the expected value. A missing `aud` yields `MissingClaim("aud")`, and a mismatch yields `InvalidClaim("aud", "expected <expected> but found <actual>")`. When `expectedAudience` is not set, `aud` is only parsed, never checked.

### Limits and Options

Resource limits are conservative and checked before allocation:

```scala mdoc:compile-only
import zio.blocks.jwt._
val limits = JwtLimits(maxTokenChars = 8192, maxSegmentChars = 4096, maxJsonChars = 8192, maxDepth = 32, maxFields = 512, maxArrayElements = 512)
val decodeOpts = JwtDecodeOptions(
  issuer = Some("my-app"),
  expectedAudience = Some("my-service"),
  clockSkewSeconds = 30L,
  limits = limits,
  nowSeconds = Some(1700000000L)
)
val signOpts = JwtSignOptions(limits = limits)
val key = "0123456789ABCDEF0123456789ABCDEF".getBytes("UTF-8")
val claims = JwtClaims(sub = Some("user-123"))
val token = Jwt.sign(claims, key, Algorithm.HS256, signOpts).getOrElse("")
Jwt.sign(claims, key, Algorithm.HS256, signOpts)
Jwt.decode(token, key, Algorithm.HS256, decodeOpts)
```

On decode, the size limits run before any parsing, and the header's JSON limits (`maxDepth`, `maxFields`, `maxArrayElements`) run before the signature is checked. The payload is parsed only after the signature verifies.

`JwtHeader` also carries `kid` and `typ` (default `"JWT"`):

```scala mdoc:compile-only
import zio.blocks.jwt._
val hdr = JwtHeader(Algorithm.HS256, typ = "JWT", kid = Some("key-1"))
val key = "0123456789ABCDEF0123456789ABCDEF".getBytes("UTF-8")
val claims = JwtClaims(sub = Some("user-123"))
Jwt.sign(claims, key, Algorithm.HS256, hdr)
```

`kid` is written and parsed but never used to select a key, and `typ` is not checked on decode.

## Supported Algorithms and Key Strength

| Algorithm | JVM | JS (Node.js) | Pure Scala | Key minima / curve |
|-----------|-----|--------------|-----------|--------------------|
| HS256     | ✓   | ✓            | ✓         | 32 bytes |
| HS384     | ✓   | ✓            | ✓         | 48 bytes |
| HS512     | ✓   | ✓            | ✓         | 64 bytes |
| RS256     | ✓   | ✓            | —         | RSA ≥2048 bits (checked on JVM only) |
| RS384     | ✓   | ✓            | —         | RSA ≥2048 bits (checked on JVM only) |
| RS512     | ✓   | ✓            | —         | RSA ≥2048 bits (checked on JVM only) |
| PS256     | ✓   | —            | —         | RSA ≥2048 bits, PSS |
| PS384     | ✓   | —            | —         | RSA ≥2048 bits, PSS |
| PS512     | ✓   | —            | —         | RSA ≥2048 bits, PSS |
| ES256     | ✓   | ✓            | —         | P-256 (`prime256v1`, checked on JVM only) |
| ES384     | ✓   | ✓            | —         | P-384 (`secp384r1`, checked on JVM only) |
| ES512     | ✓   | ✓            | —         | P-521 (`secp521r1`, 66-byte components, checked on JVM only) |
| EdDSA     | ✓   | —            | —         | Ed25519 |

HMAC keys shorter than the minimum length for their algorithm are rejected with `InvalidKey` (not `UnsupportedAlgorithm` or `InvalidToken`) on every backend. On the JVM, RSA keys under 2048 bits and EC keys on the wrong curve (e.g. `ES256` with `secp384r1`) are also rejected with `InvalidKey`; the Node.js backend does not check RSA key size or EC curves.

Query what a backend supports at runtime:

```scala mdoc
import zio.blocks.jwt._
JvmJwtCryptoBackend.supportedAlgorithms // Set[Algorithm]
```

`UnsupportedAlgorithm` is returned when the backend does not support the algorithm: any asymmetric algorithm when no platform backend is installed, `PS*`/`EdDSA` on JS (Node), or a token whose `alg` header is not a known algorithm (such as `FOO` or `none`).

In a browser or other JavaScript runtime without Node's `crypto` module, `JsJwtCryptoBackend` returns `UnsupportedAlgorithm` for `RS*`/`ES*`; HMAC algorithms still work.

### Key Formats

Every `key` argument is an `Array[Byte]`:

| Algorithm family     | Signing key (`Jwt.sign`)        | Verification key (`Jwt.decode`)              |
|----------------------|---------------------------------|----------------------------------------------|
| `HS*`                | Raw secret bytes                | The same secret bytes                        |
| `RS*`, `PS*`         | RSA private key, PKCS#8 DER     | RSA public key, X.509 SubjectPublicKeyInfo DER |
| `ES*`                | EC private key, PKCS#8 DER      | EC public key, X.509 SubjectPublicKeyInfo DER  |
| `EdDSA`              | Ed25519 private key, PKCS#8 DER | Ed25519 public key, X.509 SubjectPublicKeyInfo DER |

On the JVM, `java.security.PrivateKey.getEncoded` returns PKCS#8 DER and `PublicKey.getEncoded` returns X.509 DER. The module does not parse PEM or JWK: for a PEM file, drop the `-----BEGIN ...-----`/`-----END ...-----` lines and Base64-decode the rest. Only `BEGIN PRIVATE KEY` and `BEGIN PUBLIC KEY` PEM files contain these formats; convert PKCS#1 or SEC1 keys first. A key in the wrong format is rejected with `InvalidKey`.

## Error Handling

All errors are represented as `JwtError` subtypes (no stack traces). Limits and validation map to distinct cases:

| Error                | Meaning                                      |
|----------------------|----------------------------------------------|
| `InvalidToken`       | Malformed structure (not three segments, empty segment), invalid Base64URL, or invalid JSON |
| `ExpiredToken`       | `exp` is in the past (with `clockSkewSeconds` and `nowSeconds`) |
| `NotYetValid`        | `nbf` is in the future                       |
| `InvalidSignature`   | Signature did not verify (after `alg` check, before claims parsing) |
| `UnsupportedAlgorithm` | Backend does not support the algorithm, or the `alg` header is unknown (`FOO`, `none`) |
| `MissingClaim`       | Required claim absent (`alg`, `iss` when `issuer` is set, `aud` when `expectedAudience` is set) |
| `InvalidClaim`       | A claim has the wrong type (`iss`/`sub` not a string, `exp` not a number, `aud` array with non-strings), or `iss`/`aud` does not match `issuer`/`expectedAudience` |
| `AlgorithmMismatch`  | On `decode`, the token's `alg` header differs from the `alg` argument; on `sign`, `header.alg` differs from the `alg` argument |
| `InvalidKey`         | Key bytes are malformed, in the wrong format, or too weak (short HMAC secret; on JVM, RSA under 2048 bits or wrong EC curve) |
| `TokenTooLarge`      | Compact token > `maxTokenChars` |
| `SegmentTooLarge`    | A segment > `maxSegmentChars` |
| `JsonTooLarge`       | Decoded JSON > `maxJsonChars` |
| `TooDeep`            | JSON depth > `maxDepth` |
| `TooManyFields`      | Object fields > `maxFields` |
| `TooManyElements`    | Array elements > `maxArrayElements` |

## Base64URL Encoding

`Base64Url` is a public object that encodes/decodes with no padding (`=`), per RFC 7515 §2 and RFC 4648 §5. Tokens containing `=` are rejected with `InvalidToken`; length `mod 4 == 1` and non-Base64Url chars (`+`, `/`, `!`) are rejected; trailing bits for 2- or 3-char tails must be zero (e.g. `AB` / `ABC` are rejected).

## Limitations

- No JWK or JWKS support, and no key lookup by `kid`.
- No JWE: tokens are signed, not encrypted, so anyone holding a token can read its claims.
- The module is new and has no binary-compatibility (MiMa) baseline yet; expect API changes.

## See Also

- [How to Issue and Verify JWTs](../guides/issue-and-verify-jwts.md): sign and verify with HMAC, RSA and EdDSA keys, fix the clock in tests, load PEM keys, and reject hostile tokens.
- [How to Protect an Endpoint with JWT Bearer Tokens](../guides/protect-endpoints-with-jwt.md): read the bearer token from a request, verify it, and answer 401 or 403 correctly.
