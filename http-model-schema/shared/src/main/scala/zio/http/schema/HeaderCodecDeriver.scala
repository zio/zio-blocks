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

package zio.http.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.SchemaError
import zio.http.{Headers, HeadersBuilder}

/**
 * The [[zio.blocks.schema.derive.Deriver]] behind `DefaultHeaderFormat`. It
 * builds the [[HeaderCodec]] of a schema with the field and naming rules that
 * `HeaderCodec` lists, and throws `UnsupportedOperationException` when a record
 * has a field it cannot encode, such as a nested record or a `Map`. Pass it to
 * `Schema#deriving` to replace the codec of a type with an `instance` override.
 */
object HeaderCodecDeriver
    extends ParamCodecDeriver[Headers, HeadersBuilder, HeaderCodec](
      "HeaderCodec",
      (name, raw, cause) => HeaderError.Malformed(name, raw, cause).message
    ) {

  protected def fieldKey(fieldName: String): String = {
    val key = toHeaderName(fieldName)
    // The trusted write path skips per-operation name validation, so every
    // wire name is checked once here, at derivation time, with the same
    // exception `HeadersBuilder.add` would throw on every encode.
    Headers.validateNameOrThrow(key)
    key
  }

  protected def readField(input: Headers, key: String): Option[Chunk[String]] = {
    val all = input.rawGetAll(key)
    if (all.isEmpty) None else Some(all)
  }

  protected def writeField(output: HeadersBuilder, key: String, value: String): Unit =
    output.add(key, value)

  /**
   * Precomputes the stored (lower-case) form of the wire name once, so the
   * per-operation scan never lower-cases. ASCII-only lower-casing matches
   * `Locale.ROOT` exactly on valid header names, which [[fieldKey]] enforces.
   */
  override protected def readKey(fieldKey: String): String = asciiLower(fieldKey)

  override protected def writeKey(fieldKey: String): String = asciiLower(fieldKey)

  /**
   * Scans the entries for the precomputed lower-cased name without the validate
   * + lower-case + `Chunk` work of [[Headers.rawGetAll]]: the same
   * case-insensitive first-value-wins rule, with no allocation.
   */
  override protected def readFieldFirst(input: Headers, lowerKey: String): String = {
    var i = 0
    while (i < input.size) {
      if (input.nameAt(i) == lowerKey) return input.rawValueAt(i)
      i += 1
    }
    null
  }

  /**
   * Appends via the trusted builder path: the name is the precomputed,
   * derivation-time validated lower-cased form, so per-operation name
   * validation and lower-casing are skipped while CR/LF value validation stays.
   * Emitted names are byte-identical to [[writeField]].
   */
  override protected def writeFieldTrusted(output: HeadersBuilder, key: String, value: String): Unit =
    output.addTrustedName(key, value)

  protected def makeCodec[A](
    encodeFn: (A, HeadersBuilder) => Unit,
    decodeFn: Headers => Either[SchemaError, A]
  ): HeaderCodec[A] =
    new HeaderCodec[A] with ParamCodecDeriver.Derived {
      def encode(value: A, output: HeadersBuilder): Unit = encodeFn(value, output)
      def decode(input: Headers): Either[SchemaError, A] = decodeFn(input)
    }

  protected def makeCustomizedCodec[A](
    encodeFn: (A, HeadersBuilder) => Unit,
    decodeFn: Headers => Either[SchemaError, A]
  ): HeaderCodec[A] =
    new HeaderCodec[A] with ParamCodecDeriver.Derived with ParamCodecDeriver.Customized {
      def encode(value: A, output: HeadersBuilder): Unit = encodeFn(value, output)
      def decode(input: Headers): Either[SchemaError, A] = decodeFn(input)
    }

  protected def freshOutput(): HeadersBuilder = HeadersBuilder.make()

  protected def finishEntries(output: HeadersBuilder): Chunk[(String, String)] =
    output.build().toChunk

  protected def syntheticInput(rawValues: Option[Chunk[String]]): Headers = {
    val builder = HeadersBuilder.make()
    rawValues.foreach { values =>
      var index = 0
      while (index < values.length) {
        builder.add(topLevelKey, values(index))
        index += 1
      }
    }
    builder.build()
  }

  protected def isTopLevelKey(key: String): Boolean = key.equalsIgnoreCase(topLevelKey)

  private def toHeaderName(fieldName: String): String = {
    val builder = new StringBuilder
    var index   = 0
    while (index < fieldName.length) {
      val char = fieldName.charAt(index)
      if (index > 0 && char.isUpper) builder.append('-')
      builder.append(char.toUpper)
      index += 1
    }
    builder.toString()
  }

  /**
   * Lower-cases ASCII upper-case letters, returning the input unchanged when
   * there is nothing to change. Valid header names hold ASCII token characters
   * only (enforced by [[fieldKey]] validation), so this matches `Locale.ROOT`
   * lower-casing exactly, on every platform and locale.
   */
  private def asciiLower(name: String): String = {
    var index = 0
    while (index < name.length) {
      val char = name.charAt(index)
      if (char >= 'A' && char <= 'Z') {
        val chars = name.toCharArray
        var j     = index
        while (j < chars.length) {
          val c = chars(j)
          if (c >= 'A' && c <= 'Z') chars(j) = (c + 32).toChar
          j += 1
        }
        return new String(chars)
      }
      index += 1
    }
    name
  }
}
