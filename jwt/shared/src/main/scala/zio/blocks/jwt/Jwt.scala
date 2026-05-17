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

import scala.collection.mutable

trait JwtCryptoBackend {
  def sign(data: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Array[Byte]]

  def verify(data: Array[Byte], signature: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Boolean]
}

object JwtCrypto {
  var backend: JwtCryptoBackend = SharedJwtCryptoBackend
}

object Jwt {
  def sign(
    claims: JwtClaims,
    key: Array[Byte],
    alg: Algorithm
  ): Either[JwtError, String] =
    sign(claims, key, alg, JwtHeader(alg))

  def sign(
    claims: JwtClaims,
    key: Array[Byte],
    alg: Algorithm,
    header: JwtHeader
  ): Either[JwtError, String] =
    if (header.alg != alg) Left(JwtError.AlgorithmMismatch(alg.name, header.alg.name))
    else {
      val headerSegment  = Base64Url.encode(JwtText.encodeUtf8(JwtHeader.render(header)))
      val payloadSegment = Base64Url.encode(JwtText.encodeUtf8(JwtClaims.render(claims)))
      val signingInput   = headerSegment + "." + payloadSegment

      JwtCrypto.backend
        .sign(JwtText.encodeUtf8(signingInput), key, alg)
        .map(signature => signingInput + "." + Base64Url.encode(signature))
    }

  def decode(
    token: String,
    key: Array[Byte],
    alg: Algorithm,
    clockSkewSeconds: Long = 0L,
    issuer: Option[String] = None
  ): Either[JwtError, JwtClaims] =
    for {
      parts     <- splitCompact(token)
      header    <- JwtHeader.parse(parts.headerSegment)
      _         <- validateAlgorithm(alg, header.alg)
      claims    <- JwtClaims.parse(parts.payloadSegment)
      signature <- Base64Url.decode(parts.signatureSegment)
      verified  <- JwtCrypto.backend.verify(JwtText.encodeUtf8(parts.signingInput), signature, key, alg)
      _         <- if (verified) Right(()) else Left(JwtError.InvalidSignature(alg.name))
      _         <- validateClaims(claims, clockSkewSeconds, issuer)
    } yield claims

  def decodeUnsafe(token: String): Either[JwtError, (JwtHeader, JwtClaims)] =
    for {
      parts  <- splitCompact(token)
      header <- JwtHeader.parse(parts.headerSegment)
      claims <- JwtClaims.parse(parts.payloadSegment)
    } yield (header, claims)

  private[this] def splitCompact(token: String): Either[JwtError, TokenParts] = {
    val firstDot  = token.indexOf('.')
    val secondDot = if (firstDot >= 0) token.indexOf('.', firstDot + 1) else -1

    if (firstDot <= 0 || secondDot <= firstDot + 1 || secondDot == token.length - 1 || token.indexOf('.', secondDot + 1) >= 0)
      Left(JwtError.InvalidToken("JWT compact form must contain exactly three segments"))
    else {
      val headerSegment    = token.substring(0, firstDot)
      val payloadSegment   = token.substring(firstDot + 1, secondDot)
      val signatureSegment = token.substring(secondDot + 1)
      Right(TokenParts(headerSegment, payloadSegment, signatureSegment))
    }
  }

  private[this] def validateAlgorithm(expected: Algorithm, found: Algorithm): Either[JwtError, Unit] =
    if (expected == found) Right(())
    else Left(JwtError.AlgorithmMismatch(expected.name, found.name))

  private[this] def validateClaims(
    claims: JwtClaims,
    clockSkewSeconds: Long,
    issuer: Option[String]
  ): Either[JwtError, Unit] = {
    val now = System.currentTimeMillis() / 1000L

    claims.exp match {
      case Some(exp) if now - clockSkewSeconds > exp => Left(JwtError.ExpiredToken(exp, now))
      case _                                         =>
        claims.nbf match {
          case Some(nbf) if now + clockSkewSeconds < nbf => Left(JwtError.NotYetValid(nbf, now))
          case _                                         =>
            issuer match {
              case Some(expectedIssuer) =>
                claims.iss match {
                  case Some(foundIssuer) if foundIssuer == expectedIssuer => Right(())
                  case Some(foundIssuer) => Left(JwtError.InvalidToken(s"issuer mismatch: expected $expectedIssuer but found $foundIssuer"))
                  case None              => Left(JwtError.MissingClaim("iss"))
                }
              case None => Right(())
            }
        }
    }
  }

  private final case class TokenParts(headerSegment: String, payloadSegment: String, signatureSegment: String) {
    val signingInput: String = headerSegment + "." + payloadSegment
  }
}

private[jwt] object JwtText {
  def encodeUtf8(value: String): Array[Byte] = value.getBytes("UTF-8")

  def decodeUtf8(bytes: Array[Byte]): String = new String(bytes, "UTF-8")
}

private[jwt] object JwtJson {
  sealed trait Value
  case class StringValue(value: String) extends Value
  case class NumberValue(value: String) extends Value
  case class BooleanValue(value: Boolean) extends Value
  case object NullValue extends Value

  def parseObject(input: String): Either[JwtError, Map[String, Value]] = {
    val parser = new Parser(input)
    parser.parseObject()
  }

  def requiredString(fields: Map[String, Value], key: String): Either[JwtError, String] =
    fields.get(key) match {
      case Some(StringValue(value)) => Right(value)
      case Some(_)                  => Left(JwtError.InvalidToken(s"claim '$key' must be a string"))
      case None                     => Left(JwtError.MissingClaim(key))
    }

  def optionalString(fields: Map[String, Value], key: String): Either[JwtError, Option[String]] =
    fields.get(key) match {
      case Some(StringValue(value)) => Right(Some(value))
      case Some(NullValue)          => Right(None)
      case Some(_)                  => Left(JwtError.InvalidToken(s"claim '$key' must be a string"))
      case None                     => Right(None)
    }

  def optionalLong(fields: Map[String, Value], key: String): Either[JwtError, Option[Long]] =
    fields.get(key) match {
      case Some(NumberValue(value)) =>
        parseLong(value).map(number => Some(number))
      case Some(NullValue)          => Right(None)
      case Some(_)                  => Left(JwtError.InvalidToken(s"claim '$key' must be an integer number"))
      case None                     => Right(None)
    }

  def renderField(key: String, value: Value): String = quote(key) + ":" + renderValue(value)

  def quote(value: String): String = {
    val builder = new java.lang.StringBuilder(value.length + 2)
    builder.append('"')

    var index = 0
    while (index < value.length) {
      value.charAt(index) match {
        case '"' => builder.append("\\\"")
        case '\\' => builder.append("\\\\")
        case '\b' => builder.append("\\b")
        case '\f' => builder.append("\\f")
        case '\n' => builder.append("\\n")
        case '\r' => builder.append("\\r")
        case '\t' => builder.append("\\t")
        case char if char < ' ' =>
          builder.append("\\u")
          val hex = Integer.toHexString(char.toInt)
          var padding = hex.length
          while (padding < 4) {
            builder.append('0')
            padding += 1
          }
          builder.append(hex)
        case char => builder.append(char)
      }
      index += 1
    }

    builder.append('"').toString
  }

  private[this] def renderValue(value: Value): String = value match {
    case StringValue(text)   => quote(text)
    case NumberValue(number) => number
    case BooleanValue(bool)  => if (bool) "true" else "false"
    case NullValue           => "null"
  }

  private[this] def parseLong(value: String): Either[JwtError, Long] =
    if (value.isEmpty) Left(JwtError.InvalidToken("expected integer number"))
    else {
      var index = 0
      if (value.charAt(0) == '-') index = 1

      if (index == value.length) Left(JwtError.InvalidToken("expected integer number"))
      else {
        while (index < value.length && value.charAt(index).isDigit) index += 1
        if (index != value.length) Left(JwtError.InvalidToken("expected integer number"))
        else {
          try Right(java.lang.Long.parseLong(value))
          catch {
            case _: NumberFormatException => Left(JwtError.InvalidToken(s"invalid integer number: $value"))
          }
        }
      }
    }

  private final class Parser(input: String) {
    private[this] val length = input.length
    private[this] var index  = 0

    def parseObject(): Either[JwtError, Map[String, Value]] =
      try {
        skipWhitespace()
        expect('{')
        skipWhitespace()

        val fields = mutable.LinkedHashMap.empty[String, Value]
        var done   = false

        if (peek('}')) {
          index += 1
          done = true
        }

        while (!done) {
          val key = parseString()
          skipWhitespace()
          expect(':')
          skipWhitespace()
          parseValue() match {
            case Some(value) => fields.update(key, value)
            case None        => ()
          }
          skipWhitespace()

          if (peek(',')) {
            index += 1
            skipWhitespace()
          } else if (peek('}')) {
            index += 1
            done = true
          } else fail("expected ',' or '}'")
        }

        skipWhitespace()
        if (index != length) fail("unexpected trailing content")
        Right(fields.toMap)
      } catch {
        case error: JwtError => Left(error)
      }

    private[this] def parseValue(): Option[Value] = {
      if (index >= length) fail("unexpected end of input")

      input.charAt(index) match {
        case '"' => Some(StringValue(parseString()))
        case '{' => skipNested('{', '}'); None
        case '[' => skipNested('[', ']'); None
        case 't' => consumeLiteral("true"); Some(BooleanValue(true))
        case 'f' => consumeLiteral("false"); Some(BooleanValue(false))
        case 'n' => consumeLiteral("null"); Some(NullValue)
        case '-' | '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' => Some(NumberValue(parseNumber()))
        case _ => fail("invalid JSON value")
      }
    }

    private[this] def parseString(): String = {
      expect('"')
      val builder = new java.lang.StringBuilder

      while (index < length) {
        val ch = input.charAt(index)
        index += 1

        if (ch == '"') return builder.toString
        else if (ch == '\\') {
          if (index >= length) fail("unterminated escape sequence")
          input.charAt(index) match {
            case '"' => builder.append('"'); index += 1
            case '\\' => builder.append('\\'); index += 1
            case '/' => builder.append('/'); index += 1
            case 'b' => builder.append('\b'); index += 1
            case 'f' => builder.append('\f'); index += 1
            case 'n' => builder.append('\n'); index += 1
            case 'r' => builder.append('\r'); index += 1
            case 't' => builder.append('\t'); index += 1
            case 'u' =>
              index += 1
              if (index + 4 > length) fail("invalid unicode escape")
              val codePoint = parseHex(input.substring(index, index + 4))
              builder.append(codePoint.toChar)
              index += 4
            case _ => fail("invalid escape sequence")
          }
        } else {
          if (ch < ' ') fail("unescaped control character")
          builder.append(ch)
        }
      }

      fail("unterminated string")
    }

    private[this] def parseNumber(): String = {
      val start = index

      if (input.charAt(index) == '-') index += 1
      parseDigits(requireAtLeastOne = true)

      if (index < length && input.charAt(index) == '.') {
        index += 1
        parseDigits(requireAtLeastOne = true)
      }

      if (index < length && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
        index += 1
        if (index < length && (input.charAt(index) == '+' || input.charAt(index) == '-')) index += 1
        parseDigits(requireAtLeastOne = true)
      }

      input.substring(start, index)
    }

    private[this] def parseDigits(requireAtLeastOne: Boolean): Unit = {
      val start = index
      while (index < length && input.charAt(index).isDigit) index += 1
      if (requireAtLeastOne && start == index) fail("invalid number")
    }

    private[this] def consumeLiteral(literal: String): Unit = {
      if (index + literal.length > length || input.substring(index, index + literal.length) != literal)
        fail(s"expected '$literal'")
      index += literal.length
    }

    private[this] def skipNested(open: Char, close: Char): Unit = {
      var depth            = 0
      var inString         = false
      var escaping         = false

      while (index < length) {
        val ch = input.charAt(index)
        index += 1

        if (inString) {
          if (escaping) escaping = false
          else if (ch == '\\') escaping = true
          else if (ch == '"') inString = false
        } else if (ch == '"') inString = true
        else if (ch == open) depth += 1
        else if (ch == close) {
          depth -= 1
          if (depth == 0) return
        }
      }

      fail(s"unterminated '$open' structure")
    }

    private[this] def parseHex(value: String): Int = {
      try Integer.parseInt(value, 16)
      catch {
        case _: NumberFormatException => fail("invalid unicode escape")
      }
    }

    private[this] def peek(expected: Char): Boolean = index < length && input.charAt(index) == expected

    private[this] def expect(expected: Char): Unit =
      if (index >= length || input.charAt(index) != expected) fail(s"expected '$expected'")
      else index += 1

    private[this] def skipWhitespace(): Unit =
      while (index < length && input.charAt(index).isWhitespace) index += 1

    private[this] def fail(reason: String): Nothing = throw JwtError.InvalidToken(reason)
  }
}

private[jwt] object SharedJwtCryptoBackend extends JwtCryptoBackend {
  def sign(data: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Array[Byte]] = alg match {
    case Algorithm.HS256 => Right(HmacSha2.hmacSha256(key, data))
    case Algorithm.HS384 => Right(HmacSha2.hmacSha384(key, data))
    case Algorithm.HS512 => Right(HmacSha2.hmacSha512(key, data))
    case _               => Left(JwtError.UnsupportedAlgorithm(alg.name))
  }

  def verify(data: Array[Byte], signature: Array[Byte], key: Array[Byte], alg: Algorithm): Either[JwtError, Boolean] =
    sign(data, key, alg).map(expected => constantTimeEquals(expected, signature))

  private[this] def constantTimeEquals(left: Array[Byte], right: Array[Byte]): Boolean = {
    var diff = left.length ^ right.length
    val size = math.min(left.length, right.length)
    var index = 0
    while (index < size) {
      diff |= (left(index) ^ right(index)) & 0xff
      index += 1
    }
    diff == 0
  }
}

private[jwt] object HmacSha2 {
  def hmacSha256(key: Array[Byte], data: Array[Byte]): Array[Byte] = hmac(key, data, 64, Sha2.sha256)

  def hmacSha384(key: Array[Byte], data: Array[Byte]): Array[Byte] = hmac(key, data, 128, Sha2.sha384)

  def hmacSha512(key: Array[Byte], data: Array[Byte]): Array[Byte] = hmac(key, data, 128, Sha2.sha512)

  private[this] def hmac(
    key: Array[Byte],
    data: Array[Byte],
    blockSize: Int,
    digest: Array[Byte] => Array[Byte]
  ): Array[Byte] = {
    val normalizedKey =
      if (key.length > blockSize) digest(key)
      else key.clone()

    val keyBlock = new Array[Byte](blockSize)
    java.lang.System.arraycopy(normalizedKey, 0, keyBlock, 0, math.min(normalizedKey.length, blockSize))

    val innerPad = new Array[Byte](blockSize)
    val outerPad = new Array[Byte](blockSize)

    var index = 0
    while (index < blockSize) {
      innerPad(index) = (keyBlock(index) ^ 0x36).toByte
      outerPad(index) = (keyBlock(index) ^ 0x5c).toByte
      index += 1
    }

    val innerInput = new Array[Byte](blockSize + data.length)
    java.lang.System.arraycopy(innerPad, 0, innerInput, 0, blockSize)
    java.lang.System.arraycopy(data, 0, innerInput, blockSize, data.length)
    val innerHash = digest(innerInput)

    val outerInput = new Array[Byte](blockSize + innerHash.length)
    java.lang.System.arraycopy(outerPad, 0, outerInput, 0, blockSize)
    java.lang.System.arraycopy(innerHash, 0, outerInput, blockSize, innerHash.length)

    digest(outerInput)
  }
}

private[jwt] object Sha2 {
  private[this] val K256: Array[Int] = Array(
    0x428a2f98,
    0x71374491,
    0xb5c0fbcf,
    0xe9b5dba5,
    0x3956c25b,
    0x59f111f1,
    0x923f82a4,
    0xab1c5ed5,
    0xd807aa98,
    0x12835b01,
    0x243185be,
    0x550c7dc3,
    0x72be5d74,
    0x80deb1fe,
    0x9bdc06a7,
    0xc19bf174,
    0xe49b69c1,
    0xefbe4786,
    0x0fc19dc6,
    0x240ca1cc,
    0x2de92c6f,
    0x4a7484aa,
    0x5cb0a9dc,
    0x76f988da,
    0x983e5152,
    0xa831c66d,
    0xb00327c8,
    0xbf597fc7,
    0xc6e00bf3,
    0xd5a79147,
    0x06ca6351,
    0x14292967,
    0x27b70a85,
    0x2e1b2138,
    0x4d2c6dfc,
    0x53380d13,
    0x650a7354,
    0x766a0abb,
    0x81c2c92e,
    0x92722c85,
    0xa2bfe8a1,
    0xa81a664b,
    0xc24b8b70,
    0xc76c51a3,
    0xd192e819,
    0xd6990624,
    0xf40e3585,
    0x106aa070,
    0x19a4c116,
    0x1e376c08,
    0x2748774c,
    0x34b0bcb5,
    0x391c0cb3,
    0x4ed8aa4a,
    0x5b9cca4f,
    0x682e6ff3,
    0x748f82ee,
    0x78a5636f,
    0x84c87814,
    0x8cc70208,
    0x90befffa,
    0xa4506ceb,
    0xbef9a3f7,
    0xc67178f2
  )

  private[this] val K512: Array[Long] = Array(
    0x428a2f98d728ae22L,
    0x7137449123ef65cdL,
    0xb5c0fbcfec4d3b2fL,
    0xe9b5dba58189dbbcL,
    0x3956c25bf348b538L,
    0x59f111f1b605d019L,
    0x923f82a4af194f9bL,
    0xab1c5ed5da6d8118L,
    0xd807aa98a3030242L,
    0x12835b0145706fbeL,
    0x243185be4ee4b28cL,
    0x550c7dc3d5ffb4e2L,
    0x72be5d74f27b896fL,
    0x80deb1fe3b1696b1L,
    0x9bdc06a725c71235L,
    0xc19bf174cf692694L,
    0xe49b69c19ef14ad2L,
    0xefbe4786384f25e3L,
    0x0fc19dc68b8cd5b5L,
    0x240ca1cc77ac9c65L,
    0x2de92c6f592b0275L,
    0x4a7484aa6ea6e483L,
    0x5cb0a9dcbd41fbd4L,
    0x76f988da831153b5L,
    0x983e5152ee66dfabL,
    0xa831c66d2db43210L,
    0xb00327c898fb213fL,
    0xbf597fc7beef0ee4L,
    0xc6e00bf33da88fc2L,
    0xd5a79147930aa725L,
    0x06ca6351e003826fL,
    0x142929670a0e6e70L,
    0x27b70a8546d22ffcL,
    0x2e1b21385c26c926L,
    0x4d2c6dfc5ac42aedL,
    0x53380d139d95b3dfL,
    0x650a73548baf63deL,
    0x766a0abb3c77b2a8L,
    0x81c2c92e47edaee6L,
    0x92722c851482353bL,
    0xa2bfe8a14cf10364L,
    0xa81a664bbc423001L,
    0xc24b8b70d0f89791L,
    0xc76c51a30654be30L,
    0xd192e819d6ef5218L,
    0xd69906245565a910L,
    0xf40e35855771202aL,
    0x106aa07032bbd1b8L,
    0x19a4c116b8d2d0c8L,
    0x1e376c085141ab53L,
    0x2748774cdf8eeb99L,
    0x34b0bcb5e19b48a8L,
    0x391c0cb3c5c95a63L,
    0x4ed8aa4ae3418acbL,
    0x5b9cca4f7763e373L,
    0x682e6ff3d6b2b8a3L,
    0x748f82ee5defb2fcL,
    0x78a5636f43172f60L,
    0x84c87814a1f0ab72L,
    0x8cc702081a6439ecL,
    0x90befffa23631e28L,
    0xa4506cebde82bde9L,
    0xbef9a3f7b2c67915L,
    0xc67178f2e372532bL,
    0xca273eceea26619cL,
    0xd186b8c721c0c207L,
    0xeada7dd6cde0eb1eL,
    0xf57d4f7fee6ed178L,
    0x06f067aa72176fbaL,
    0x0a637dc5a2c898a6L,
    0x113f9804bef90daeL,
    0x1b710b35131c471bL,
    0x28db77f523047d84L,
    0x32caab7b40c72493L,
    0x3c9ebe0a15c9bebcL,
    0x431d67c49c100d4cL,
    0x4cc5d4becb3e42b6L,
    0x597f299cfc657e2aL,
    0x5fcb6fab3ad6faecL,
    0x6c44198c4a475817L
  )

  def sha256(data: Array[Byte]): Array[Byte] = {
    val hash = Array(0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19)
    val w    = new Array[Int](64)
    val body = pad256(data)

    var offset = 0
    while (offset < body.length) {
      var t = 0
      while (t < 16) {
        val base = offset + (t * 4)
        w(t) = ((body(base) & 0xff) << 24) |
          ((body(base + 1) & 0xff) << 16) |
          ((body(base + 2) & 0xff) << 8) |
          (body(base + 3) & 0xff)
        t += 1
      }
      while (t < 64) {
        w(t) = smallSigma1(w(t - 2)) + w(t - 7) + smallSigma0(w(t - 15)) + w(t - 16)
        t += 1
      }

      var a = hash(0)
      var b = hash(1)
      var c = hash(2)
      var d = hash(3)
      var e = hash(4)
      var f = hash(5)
      var g = hash(6)
      var h = hash(7)

      t = 0
      while (t < 64) {
        val t1 = h + bigSigma1(e) + ch(e, f, g) + K256(t) + w(t)
        val t2 = bigSigma0(a) + maj(a, b, c)
        h = g
        g = f
        f = e
        e = d + t1
        d = c
        c = b
        b = a
        a = t1 + t2
        t += 1
      }

      hash(0) += a
      hash(1) += b
      hash(2) += c
      hash(3) += d
      hash(4) += e
      hash(5) += f
      hash(6) += g
      hash(7) += h

      offset += 64
    }

    val result = new Array[Byte](32)
    var index  = 0
    while (index < hash.length) {
      writeInt(hash(index), result, index * 4)
      index += 1
    }
    result
  }

  def sha384(data: Array[Byte]): Array[Byte] = {
    val hash = Array(
      0xcbbb9d5dc1059ed8L,
      0x629a292a367cd507L,
      0x9159015a3070dd17L,
      0x152fecd8f70e5939L,
      0x67332667ffc00b31L,
      0x8eb44a8768581511L,
      0xdb0c2e0d64f98fa7L,
      0x47b5481dbefa4fa4L
    )
    digest512(data, hash, 48)
  }

  def sha512(data: Array[Byte]): Array[Byte] = {
    val hash = Array(
      0x6a09e667f3bcc908L,
      0xbb67ae8584caa73bL,
      0x3c6ef372fe94f82bL,
      0xa54ff53a5f1d36f1L,
      0x510e527fade682d1L,
      0x9b05688c2b3e6c1fL,
      0x1f83d9abfb41bd6bL,
      0x5be0cd19137e2179L
    )
    digest512(data, hash, 64)
  }

  private[this] def digest512(data: Array[Byte], initialHash: Array[Long], outputSize: Int): Array[Byte] = {
    val hash = initialHash.clone()
    val w    = new Array[Long](80)
    val body = pad512(data)

    var offset = 0
    while (offset < body.length) {
      var t = 0
      while (t < 16) {
        val base = offset + (t * 8)
        w(t) = readLong(body, base)
        t += 1
      }
      while (t < 80) {
        w(t) = smallSigma1(w(t - 2)) + w(t - 7) + smallSigma0(w(t - 15)) + w(t - 16)
        t += 1
      }

      var a = hash(0)
      var b = hash(1)
      var c = hash(2)
      var d = hash(3)
      var e = hash(4)
      var f = hash(5)
      var g = hash(6)
      var h = hash(7)

      t = 0
      while (t < 80) {
        val t1 = h + bigSigma1(e) + ch(e, f, g) + K512(t) + w(t)
        val t2 = bigSigma0(a) + maj(a, b, c)
        h = g
        g = f
        f = e
        e = d + t1
        d = c
        c = b
        b = a
        a = t1 + t2
        t += 1
      }

      hash(0) += a
      hash(1) += b
      hash(2) += c
      hash(3) += d
      hash(4) += e
      hash(5) += f
      hash(6) += g
      hash(7) += h

      offset += 128
    }

    val result = new Array[Byte](outputSize)
    var index  = 0
    while (index < outputSize / 8) {
      writeLong(hash(index), result, index * 8)
      index += 1
    }
    result
  }

  private[this] def pad256(data: Array[Byte]): Array[Byte] = {
    val bitLength = data.length.toLong * 8L
    val padding   = (64 - ((data.length + 1 + 8) % 64)) % 64
    val result    = new Array[Byte](data.length + 1 + padding + 8)

    java.lang.System.arraycopy(data, 0, result, 0, data.length)
    result(data.length) = 0x80.toByte

    var index = 0
    while (index < 8) {
      result(result.length - 1 - index) = (bitLength >>> (index * 8)).toByte
      index += 1
    }
    result
  }

  private[this] def pad512(data: Array[Byte]): Array[Byte] = {
    val bitLength = data.length.toLong * 8L
    val padding   = (128 - ((data.length + 1 + 16) % 128)) % 128
    val result    = new Array[Byte](data.length + 1 + padding + 16)

    java.lang.System.arraycopy(data, 0, result, 0, data.length)
    result(data.length) = 0x80.toByte

    var index = 0
    while (index < 8) {
      result(result.length - 1 - index) = (bitLength >>> (index * 8)).toByte
      index += 1
    }
    result
  }

  private[this] def writeInt(value: Int, output: Array[Byte], offset: Int): Unit = {
    output(offset) = (value >>> 24).toByte
    output(offset + 1) = (value >>> 16).toByte
    output(offset + 2) = (value >>> 8).toByte
    output(offset + 3) = value.toByte
  }

  private[this] def writeLong(value: Long, output: Array[Byte], offset: Int): Unit = {
    output(offset) = (value >>> 56).toByte
    output(offset + 1) = (value >>> 48).toByte
    output(offset + 2) = (value >>> 40).toByte
    output(offset + 3) = (value >>> 32).toByte
    output(offset + 4) = (value >>> 24).toByte
    output(offset + 5) = (value >>> 16).toByte
    output(offset + 6) = (value >>> 8).toByte
    output(offset + 7) = value.toByte
  }

  private[this] def readLong(input: Array[Byte], offset: Int): Long =
    ((input(offset).toLong & 0xffL) << 56) |
      ((input(offset + 1).toLong & 0xffL) << 48) |
      ((input(offset + 2).toLong & 0xffL) << 40) |
      ((input(offset + 3).toLong & 0xffL) << 32) |
      ((input(offset + 4).toLong & 0xffL) << 24) |
      ((input(offset + 5).toLong & 0xffL) << 16) |
      ((input(offset + 6).toLong & 0xffL) << 8) |
      (input(offset + 7).toLong & 0xffL)

  private[this] def rotateRight(value: Int, bits: Int): Int = (value >>> bits) | (value << (32 - bits))

  private[this] def rotateRight(value: Long, bits: Int): Long = (value >>> bits) | (value << (64 - bits))

  private[this] def ch(x: Int, y: Int, z: Int): Int = (x & y) ^ (~x & z)

  private[this] def ch(x: Long, y: Long, z: Long): Long = (x & y) ^ (~x & z)

  private[this] def maj(x: Int, y: Int, z: Int): Int = (x & y) ^ (x & z) ^ (y & z)

  private[this] def maj(x: Long, y: Long, z: Long): Long = (x & y) ^ (x & z) ^ (y & z)

  private[this] def bigSigma0(value: Int): Int = rotateRight(value, 2) ^ rotateRight(value, 13) ^ rotateRight(value, 22)

  private[this] def bigSigma1(value: Int): Int = rotateRight(value, 6) ^ rotateRight(value, 11) ^ rotateRight(value, 25)

  private[this] def smallSigma0(value: Int): Int = rotateRight(value, 7) ^ rotateRight(value, 18) ^ (value >>> 3)

  private[this] def smallSigma1(value: Int): Int = rotateRight(value, 17) ^ rotateRight(value, 19) ^ (value >>> 10)

  private[this] def bigSigma0(value: Long): Long = rotateRight(value, 28) ^ rotateRight(value, 34) ^ rotateRight(value, 39)

  private[this] def bigSigma1(value: Long): Long = rotateRight(value, 14) ^ rotateRight(value, 18) ^ rotateRight(value, 41)

  private[this] def smallSigma0(value: Long): Long = rotateRight(value, 1) ^ rotateRight(value, 8) ^ (value >>> 7)

  private[this] def smallSigma1(value: Long): Long = rotateRight(value, 19) ^ rotateRight(value, 61) ^ (value >>> 6)
}
