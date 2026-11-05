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

package zio.blocks.schema.yaml

import zio.blocks.schema.{Schema, SchemaError}

/**
 * Extension methods that encode and decode YAML with the YAML codec of a type's
 * `Schema` (`Schema#getInstance(YamlFormat)`): `toYaml`, `toYamlString` and
 * `toYamlBytes` on values, and `fromYaml` on strings. Import `YamlSyntax._` to
 * use them.
 *
 * {{{
 * import zio.blocks.schema._
 * import YamlSyntax._
 *
 * case class Alert(level: String, threshold: Double)
 * object Alert {
 *   implicit val schema: Schema[Alert] = Schema.derived
 * }
 *
 * Alert("warn", 0.8).toYamlString
 * // level: warn
 * // threshold: 0.8
 *
 * "level: crit\nthreshold: 0.95\n".fromYaml[Alert] // Right(Alert(crit,0.95))
 * }}}
 */
object YamlSyntax {

  /**
   * Adds `toYaml`, `toYamlString` and `toYamlBytes` to a value whose type has a
   * `Schema`.
   */
  implicit class YamlEncoderOps[A](private val value: A) extends AnyVal {

    /** Encodes this value as a [[Yaml]] tree. */
    def toYaml(implicit schema: Schema[A]): Yaml = schema.getInstance(YamlFormat).encodeValue(value)

    /** Encodes this value as YAML text, written with `YamlOptions.default`. */
    def toYamlString(implicit schema: Schema[A]): String =
      YamlWriter.write(schema.getInstance(YamlFormat).encodeValue(value))

    /** Encodes this value as the UTF-8 bytes of its YAML text. */
    def toYamlBytes(implicit schema: Schema[A]): Array[Byte] =
      YamlWriter.writeToBytes(schema.getInstance(YamlFormat).encodeValue(value))
  }

  /** Adds `fromYaml` to a string of YAML text. */
  implicit class YamlStringOps(private val s: String) extends AnyVal {

    /**
     * Decodes this YAML text as an `A`, returning `Left` with the path of the
     * failing value on failure.
     */
    def fromYaml[A](implicit schema: Schema[A]): Either[SchemaError, A] = schema.getInstance(YamlFormat).decode(s)
  }
}
