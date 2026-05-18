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

import zio.test._

object JwtJvmSpec extends ZIOSpecDefault {

  JvmJwtCryptoBackend.install()

  private val testClaims: JwtClaims = JwtClaims(sub = Some("jvm-test-subject"))

  private val rsaKeyPair: java.security.KeyPair      = genRsaKeyPair
  private val wrongRsaKeyPair: java.security.KeyPair = genRsaKeyPair

  private val ecKeyPair256: java.security.KeyPair      = genEcKeyPair("secp256r1")
  private val wrongEcKeyPair256: java.security.KeyPair = genEcKeyPair("secp256r1")
  private val ecKeyPair384: java.security.KeyPair      = genEcKeyPair("secp384r1")
  private val wrongEcKeyPair384: java.security.KeyPair = genEcKeyPair("secp384r1")
  private val ecKeyPair512: java.security.KeyPair      = genEcKeyPair("secp521r1")
  private val wrongEcKeyPair512: java.security.KeyPair = genEcKeyPair("secp521r1")

  private val edKeyPair: java.security.KeyPair      = genEdKeyPair
  private val wrongEdKeyPair: java.security.KeyPair = genEdKeyPair

  private def genRsaKeyPair: java.security.KeyPair = {
    val kpg = java.security.KeyPairGenerator.getInstance("RSA")
    kpg.initialize(2048)
    kpg.generateKeyPair()
  }

  private def genEcKeyPair(curve: String): java.security.KeyPair = {
    val kpg = java.security.KeyPairGenerator.getInstance("EC")
    kpg.initialize(new java.security.spec.ECGenParameterSpec(curve))
    kpg.generateKeyPair()
  }

  private def genEdKeyPair: java.security.KeyPair = {
    val kpg = java.security.KeyPairGenerator.getInstance("Ed25519")
    kpg.generateKeyPair()
  }

  private def tamperSignature(token: String): String = {
    val parts    = token.split('.')
    val sigBytes = Base64Url.decode(parts(2)).fold(_ => Array.emptyByteArray, identity).clone()
    if (sigBytes.nonEmpty) sigBytes(0) = (sigBytes(0) ^ 0xff).toByte
    parts(0) + "." + parts(1) + "." + Base64Url.encode(sigBytes)
  }

  private def roundtripTest(
    alg: Algorithm,
    privateKey: Array[Byte],
    publicKey: Array[Byte]
  ): TestResult = {
    val result = Jwt.sign(testClaims, privateKey, alg).flatMap(t =>
      Jwt.decode(t, publicKey, alg)
    )
    assertTrue(result.map(_.sub) == Right(testClaims.sub))
  }

  private def wrongKeyTest(
    alg: Algorithm,
    privateKey: Array[Byte],
    wrongPublicKey: Array[Byte]
  ): TestResult = {
    val result = Jwt.sign(testClaims, privateKey, alg).flatMap(t =>
      Jwt.decode(t, wrongPublicKey, alg)
    )
    assertTrue(result match {
      case Left(_: JwtError.InvalidSignature) => true
      case _                                  => false
    })
  }

  private def tamperedTest(
    alg: Algorithm,
    privateKey: Array[Byte],
    publicKey: Array[Byte]
  ): TestResult = {
    val result = Jwt
      .sign(testClaims, privateKey, alg)
      .map(tamperSignature)
      .flatMap(t => Jwt.decode(t, publicKey, alg))
    assertTrue(result.isLeft)
  }

  private def algSuite(
    name: String,
    alg: Algorithm,
    privateKey: Array[Byte],
    publicKey: Array[Byte],
    wrongPublicKey: Array[Byte]
  ): Spec[TestEnvironment, Any] =
    suite(name)(
      test("sign and decode roundtrip") { roundtripTest(alg, privateKey, publicKey) },
      test("wrong public key returns Left") { wrongKeyTest(alg, privateKey, wrongPublicKey) },
      test("tampered signature returns Left") { tamperedTest(alg, privateKey, publicKey) }
    )

  def spec: Spec[TestEnvironment, Any] = suite("JwtJvmSpec")(
    suite("RSA PKCS1v15")(
      algSuite(
        "RS256",
        Algorithm.RS256,
        rsaKeyPair.getPrivate.getEncoded,
        rsaKeyPair.getPublic.getEncoded,
        wrongRsaKeyPair.getPublic.getEncoded
      ),
      algSuite(
        "RS384",
        Algorithm.RS384,
        rsaKeyPair.getPrivate.getEncoded,
        rsaKeyPair.getPublic.getEncoded,
        wrongRsaKeyPair.getPublic.getEncoded
      ),
      algSuite(
        "RS512",
        Algorithm.RS512,
        rsaKeyPair.getPrivate.getEncoded,
        rsaKeyPair.getPublic.getEncoded,
        wrongRsaKeyPair.getPublic.getEncoded
      )
    ),
    suite("RSA-PSS")(
      algSuite(
        "PS256",
        Algorithm.PS256,
        rsaKeyPair.getPrivate.getEncoded,
        rsaKeyPair.getPublic.getEncoded,
        wrongRsaKeyPair.getPublic.getEncoded
      ),
      algSuite(
        "PS384",
        Algorithm.PS384,
        rsaKeyPair.getPrivate.getEncoded,
        rsaKeyPair.getPublic.getEncoded,
        wrongRsaKeyPair.getPublic.getEncoded
      ),
      algSuite(
        "PS512",
        Algorithm.PS512,
        rsaKeyPair.getPrivate.getEncoded,
        rsaKeyPair.getPublic.getEncoded,
        wrongRsaKeyPair.getPublic.getEncoded
      )
    ),
    suite("ECDSA")(
      algSuite(
        "ES256 (secp256r1)",
        Algorithm.ES256,
        ecKeyPair256.getPrivate.getEncoded,
        ecKeyPair256.getPublic.getEncoded,
        wrongEcKeyPair256.getPublic.getEncoded
      ),
      algSuite(
        "ES384 (secp384r1)",
        Algorithm.ES384,
        ecKeyPair384.getPrivate.getEncoded,
        ecKeyPair384.getPublic.getEncoded,
        wrongEcKeyPair384.getPublic.getEncoded
      ),
      algSuite(
        "ES512 (secp521r1)",
        Algorithm.ES512,
        ecKeyPair512.getPrivate.getEncoded,
        ecKeyPair512.getPublic.getEncoded,
        wrongEcKeyPair512.getPublic.getEncoded
      )
    ),
    suite("EdDSA Ed25519")(
      algSuite(
        "EdDSA",
        Algorithm.EdDSA,
        edKeyPair.getPrivate.getEncoded,
        edKeyPair.getPublic.getEncoded,
        wrongEdKeyPair.getPublic.getEncoded
      )
    )
  )
}
