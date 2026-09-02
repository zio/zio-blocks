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
 * Pure byte-level ECDSA DER <-> P1363 conversion.
 *
 * Single-sourced for JVM and JS so that strict validation (oversized checks,
 * non-minimal encodings, malformed lengths) cannot drift between platforms.
 *
 * ES256 uses 32-byte components (P-256), ES384 uses 48 (P-384), ES512 uses 66
 * (P-521, ceil(521/8)=66).
 */
private[jwt] object EcdsaDer {

  def derToP1363(der: Array[Byte], componentSize: Int): Array[Byte] = {
    if (der.length < 8 || der(0) != 0x30.toByte) throw new IllegalArgumentException("invalid DER ECDSA signature")
    var index                                = 1
    val (sequenceLength, sequenceLengthSize) = readDerLengthStrict(der, index)
    index += sequenceLengthSize
    val sequenceEnd = index + sequenceLength
    if (sequenceEnd != der.length) throw new IllegalArgumentException("invalid DER ECDSA signature length")
    val (r, afterR) = readDerIntegerStrict(der, index)
    val (s, afterS) = readDerIntegerStrict(der, afterR)
    if (afterS != sequenceEnd) throw new IllegalArgumentException("invalid DER ECDSA signature length")
    val result      = new Array[Byte](componentSize * 2)
    val normalizedR = normalizeComponentStrict(r, componentSize)
    val normalizedS = normalizeComponentStrict(s, componentSize)
    System.arraycopy(normalizedR, 0, result, 0, componentSize)
    System.arraycopy(normalizedS, 0, result, componentSize, componentSize)
    result
  }

  def p1363ToDer(p1363: Array[Byte], componentSize: Int): Array[Byte] = {
    if (p1363.length != componentSize * 2) throw new IllegalArgumentException("invalid P1363 ECDSA signature length")
    // P-521 top bits must be zero; reject out-of-range scalars as malformed signature
    if (componentSize == 66) {
      if ((p1363(0) & 0xfe) != 0 || (p1363(66) & 0xfe) != 0)
        throw new IllegalArgumentException("invalid P-521 ECDSA signature: unused high bits must be zero")
      // also reject zero components
      var rZero = true
      var sZero = true
      var i     = 0
      while (i < 66 && (rZero || sZero)) {
        if (p1363(i) != 0) rZero = false
        if (p1363(66 + i) != 0) sZero = false
        i += 1
      }
      if (rZero || sZero) throw new IllegalArgumentException("invalid ECDSA signature: zero component")
    } else {
      // generic zero check for other sizes
      var rZero = true
      var sZero = true
      var i     = 0
      while (i < componentSize && (rZero || sZero)) {
        if (p1363(i) != 0) rZero = false
        if (p1363(componentSize + i) != 0) sZero = false
        i += 1
      }
      if (rZero || sZero) throw new IllegalArgumentException("invalid ECDSA signature: zero component")
    }
    val r = new Array[Byte](componentSize)
    val s = new Array[Byte](componentSize)
    System.arraycopy(p1363, 0, r, 0, componentSize)
    System.arraycopy(p1363, componentSize, s, 0, componentSize)
    val encodedR      = encodeDerInteger(r)
    val encodedS      = encodeDerInteger(s)
    val contentLength = encodedR.length + encodedS.length
    val lengthBytes   = encodeDerLength(contentLength)
    val result        = new Array[Byte](1 + lengthBytes.length + contentLength)
    result(0) = 0x30.toByte
    System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length)
    System.arraycopy(encodedR, 0, result, 1 + lengthBytes.length, encodedR.length)
    System.arraycopy(encodedS, 0, result, 1 + lengthBytes.length + encodedR.length, encodedS.length)
    result
  }

  private def readDerIntegerStrict(input: Array[Byte], offset: Int): (Array[Byte], Int) = {
    if (offset >= input.length || input(offset) != 0x02.toByte)
      throw new IllegalArgumentException("invalid DER integer tag")
    val (length, lengthSize) = readDerLengthStrict(input, offset + 1)
    val start                = offset + 1 + lengthSize
    val end                  = start + length
    if (length <= 0 || end > input.length) throw new IllegalArgumentException("invalid DER integer length")
    val value = new Array[Byte](length)
    System.arraycopy(input, start, value, 0, length)
    // Reject non-minimal integer encoding:
    // - value == 0x00 is allowed (represents 0)
    // - leading 0x00 must be followed by high bit set, otherwise unnecessary
    // - otherwise first byte high bit set without leading 0x00 would be negative -> reject
    if (value.length > 1 && value(0) == 0.toByte) {
      if ((value(1) & 0x80) == 0)
        throw new IllegalArgumentException("non-minimal DER integer: unnecessary leading zero")
    } else if ((value(0) & 0x80) != 0) {
      throw new IllegalArgumentException("invalid DER integer: negative value")
    }
    // Reject integer that is all zeros but length >1 (non-minimal zero)
    if (value.length > 1) {
      var allZero = true
      var i       = 0
      while (i < value.length && allZero) {
        if (value(i) != 0) allZero = false
        i += 1
      }
      if (allZero) throw new IllegalArgumentException("non-minimal DER integer: redundant zero encoding")
    }
    (value, end)
  }

  private def readDerLengthStrict(input: Array[Byte], offset: Int): (Int, Int) = {
    if (offset >= input.length) throw new IllegalArgumentException("missing DER length")
    val first = input(offset) & 0xff
    if ((first & 0x80) == 0) (first, 1)
    else {
      val byteCount = first & 0x7f
      if (byteCount == 0 || byteCount > 4 || offset + byteCount >= input.length)
        throw new IllegalArgumentException("invalid DER length encoding")
      // Leading zero in length bytes is non-minimal
      if ((input(offset + 1) & 0xff) == 0) throw new IllegalArgumentException("non-minimal DER length")
      var length = 0
      var index  = 0
      while (index < byteCount) {
        length = (length << 8) | (input(offset + 1 + index) & 0xff)
        index += 1
      }
      // Minimal encoding: length must require byteCount bytes; reject if it could fit in fewer
      val minimalBytes = {
        var v = length
        var n = 0
        while (v > 0) { n += 1; v >>>= 8 }
        if (n == 0) 1 else n
      }
      if (byteCount != minimalBytes) throw new IllegalArgumentException("non-minimal DER length encoding")
      if (length < 128) throw new IllegalArgumentException("non-minimal DER length: use short form")
      if (length <= 0) throw new IllegalArgumentException("invalid DER length")
      (length, byteCount + 1)
    }
  }

  private def normalizeComponentStrict(component: Array[Byte], componentSize: Int): Array[Byte] = {
    val magnitude = trimLeadingZeros(component)
    // Reject zero scalar? ECDSA r/s must not be zero; treat as invalid token
    if (magnitude.length == 0) throw new IllegalArgumentException("invalid ECDSA component: zero value")
    if (magnitude.length > componentSize)
      throw new IllegalArgumentException("DER integer exceeds expected ECDSA component size")
    val result = new Array[Byte](componentSize)
    System.arraycopy(magnitude, 0, result, componentSize - magnitude.length, magnitude.length)
    // P-521 (ES512) has only 521 bits in 66 bytes; top 7 bits of first byte must be zero
    if (componentSize == 66 && (result(0) & 0xfe) != 0)
      throw new IllegalArgumentException("invalid P-521 ECDSA component: unused high bits must be zero")
    result
  }

  private def encodeDerInteger(component: Array[Byte]): Array[Byte] = {
    val magnitude         = trimLeadingZeros(component)
    val positiveMagnitude =
      if (magnitude.isEmpty) Array(0.toByte)
      else if ((magnitude(0) & 0x80) != 0) {
        val prefixed = new Array[Byte](magnitude.length + 1)
        System.arraycopy(magnitude, 0, prefixed, 1, magnitude.length)
        prefixed
      } else magnitude
    val lengthBytes = encodeDerLength(positiveMagnitude.length)
    val result      = new Array[Byte](1 + lengthBytes.length + positiveMagnitude.length)
    result(0) = 0x02.toByte
    System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length)
    System.arraycopy(positiveMagnitude, 0, result, 1 + lengthBytes.length, positiveMagnitude.length)
    result
  }

  private def encodeDerLength(length: Int): Array[Byte] =
    if (length < 0) throw new IllegalArgumentException("negative DER length")
    else if (length < 128) Array(length.toByte)
    else {
      var value       = length
      var bytesNeeded = 0
      while (value > 0) {
        bytesNeeded += 1
        value = value >>> 8
      }
      val result = new Array[Byte](bytesNeeded + 1)
      result(0) = (0x80 | bytesNeeded).toByte
      var index     = bytesNeeded
      var remaining = length
      while (index > 0) {
        result(index) = (remaining & 0xff).toByte
        remaining = remaining >>> 8
        index -= 1
      }
      result
    }

  private def trimLeadingZeros(bytes: Array[Byte]): Array[Byte] = {
    var index = 0
    while (index < bytes.length && bytes(index) == 0.toByte) index += 1
    val size = bytes.length - index
    if (size <= 0) Array.emptyByteArray
    else {
      val result = new Array[Byte](size)
      System.arraycopy(bytes, index, result, 0, size)
      result
    }
  }
}
