/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.jwt

/**
 * A JWS signing algorithm from RFC 7518 (and EdDSA from RFC 8037), named by the
 * `alg` field of a token's header.
 *
 * The caller passes one to `Jwt.sign` and `Jwt.decode`, and `decode` accepts a
 * token only when its header names the same algorithm. Which algorithms work
 * depends on the [[JwtCryptoBackend]]: the built-in default supports the `HS*`
 * algorithms, `JvmJwtCryptoBackend` all 13, and `JsJwtCryptoBackend` the `HS*`,
 * `RS*` and `ES*` ones. The `HS*` algorithms take a shared secret; the others
 * sign with a private key in PKCS#8 DER form and verify with a public key in
 * X.509 SubjectPublicKeyInfo DER form. There is no `none` algorithm.
 *
 * {{{
 * Algorithm.fromString("RS256") // Some(RS256)
 * Algorithm.fromString("none")  // None
 * Algorithm.ES256.name          // ES256
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/jwt/#supported-algorithms-and-key-strength JWT]]
 */
sealed trait Algorithm extends Product with Serializable {

  /** The algorithm identifier string used in the JWT header `alg` field. */
  def name: String
}

/**
 * The 13 algorithms this module knows. Use [[Algorithm.fromString]] to look one
 * up by its `alg` name.
 */
object Algorithm {

  /**
   * HMAC with SHA-256 (RFC 7518 §3.2). The key is a shared secret of at least
   * 32 bytes; a shorter one gives `InvalidKey`.
   */
  case object HS256 extends Algorithm {

    /** Returns `"HS256"`. */
    val name: String = "HS256"
  }

  /**
   * HMAC with SHA-384. The key is a shared secret of at least 48 bytes.
   */
  case object HS384 extends Algorithm {

    /** Returns `"HS384"`. */
    val name: String = "HS384"
  }

  /**
   * HMAC with SHA-512. The key is a shared secret of at least 64 bytes.
   */
  case object HS512 extends Algorithm {

    /** Returns `"HS512"`. */
    val name: String = "HS512"
  }

  /**
   * RSASSA-PKCS1-v1_5 with SHA-256 (RFC 7518 §3.3). Signs with an RSA private
   * key and verifies with its public key. `JvmJwtCryptoBackend` rejects keys
   * under 2048 bits with `InvalidKey`; `JsJwtCryptoBackend` does not check the
   * key size.
   */
  case object RS256 extends Algorithm {

    /** Returns `"RS256"`. */
    val name: String = "RS256"
  }

  /**
   * RSASSA-PKCS1-v1_5 with SHA-384, with the same keys and checks as `RS256`.
   */
  case object RS384 extends Algorithm {

    /** Returns `"RS384"`. */
    val name: String = "RS384"
  }

  /**
   * RSASSA-PKCS1-v1_5 with SHA-512, with the same keys and checks as `RS256`.
   */
  case object RS512 extends Algorithm {

    /** Returns `"RS512"`. */
    val name: String = "RS512"
  }

  /**
   * RSASSA-PSS with SHA-256, MGF1 with SHA-256 and a 32-byte salt (RFC 7518
   * §3.5). Needs `JvmJwtCryptoBackend`, which rejects keys under 2048 bits; the
   * other backends return `UnsupportedAlgorithm`.
   */
  case object PS256 extends Algorithm {

    /** Returns `"PS256"`. */
    val name: String = "PS256"
  }

  /**
   * RSASSA-PSS with SHA-384, MGF1 with SHA-384 and a 48-byte salt, with the
   * same keys and backend as `PS256`.
   */
  case object PS384 extends Algorithm {

    /** Returns `"PS384"`. */
    val name: String = "PS384"
  }

  /**
   * RSASSA-PSS with SHA-512, MGF1 with SHA-512 and a 64-byte salt, with the
   * same keys and backend as `PS256`.
   */
  case object PS512 extends Algorithm {

    /** Returns `"PS512"`. */
    val name: String = "PS512"
  }

  /**
   * ECDSA on the P-256 curve with SHA-256 (RFC 7518 §3.4); the signature is the
   * 64-byte `r || s`. `JvmJwtCryptoBackend` rejects a key on another curve with
   * `InvalidKey`; `JsJwtCryptoBackend` does not check the curve.
   */
  case object ES256 extends Algorithm {

    /** Returns `"ES256"`. */
    val name: String = "ES256"
  }

  /**
   * ECDSA on the P-384 curve with SHA-384; the signature is the 96-byte
   * `r || s`. The curve is checked as for `ES256`.
   */
  case object ES384 extends Algorithm {

    /** Returns `"ES384"`. */
    val name: String = "ES384"
  }

  /**
   * ECDSA on the P-521 curve with SHA-512; the signature is the 132-byte
   * `r || s`. The curve is checked as for `ES256`.
   */
  case object ES512 extends Algorithm {

    /** Returns `"ES512"`. */
    val name: String = "ES512"
  }

  /**
   * EdDSA with Ed25519 keys (RFC 8037). Needs `JvmJwtCryptoBackend`, which
   * rejects an Ed448 key with `InvalidKey`; the other backends return
   * `UnsupportedAlgorithm`.
   */
  case object EdDSA extends Algorithm {

    /** Returns `"EdDSA"`. */
    val name: String = "EdDSA"
  }

  /**
   * The 13 algorithms, in the order `HS*`, `RS*`, `PS*`, `ES*`, then `EdDSA`.
   */
  val all: List[Algorithm] =
    List(HS256, HS384, HS512, RS256, RS384, RS512, PS256, PS384, PS512, ES256, ES384, ES512, EdDSA)

  private val byName: Map[String, Algorithm] = all.map(alg => alg.name -> alg).toMap

  /**
   * Returns the algorithm whose `name` is `s`, or `None` for any other string.
   * The match is case-sensitive: `"hs256"` and `"none"` give `None`.
   */
  def fromString(s: String): Option[Algorithm] = byName.get(s)
}
