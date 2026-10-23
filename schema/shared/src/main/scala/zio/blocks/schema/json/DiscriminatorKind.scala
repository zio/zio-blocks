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

package zio.blocks.schema.json

/**
 * How a derived [[JsonCodec]] tells the cases of a sealed trait or enum apart,
 * set with [[JsonCodecDeriver#withDiscriminatorKind]]. A
 * `@Modifier.discriminator("name")` on the type overrides it with
 * `Field("name")`. Either way, a type whose cases are all objects without
 * fields encodes as plain strings while
 * [[JsonCodecDeriver#withEnumValuesAsStrings]] is on (the default), and the
 * cases of a nested sealed trait are named by their own leaf names.
 *
 * {{{
 * import zio.blocks.schema.json._
 *
 * sealed trait Format
 * object Format {
 *   case class Ebook(sizeMb: Int)    extends Format
 *   case class Paperback(pages: Int) extends Format
 *   implicit val schema: Schema[Format] = Schema.derived
 * }
 *
 * val format: Format = Format.Paperback(600)
 * def codec(kind: DiscriminatorKind) = Format.schema.derive(JsonCodecDeriver.withDiscriminatorKind(kind))
 *
 * codec(DiscriminatorKind.Key).encodeToString(format)           // {"Paperback":{"pages":600}}
 * codec(DiscriminatorKind.Field("type")).encodeToString(format) // {"type":"Paperback","pages":600}
 * codec(DiscriminatorKind.None).encodeToString(format)          // {"pages":600}
 * }}}
 */
sealed trait DiscriminatorKind

/** The three ways to encode the case of a sealed trait or enum. */
object DiscriminatorKind {

  /**
   * Writes the case name in a field of the case's own object, first:
   * `{"type": "Paperback", "pages": 600}`. The decoder finds the field anywhere
   * in the object. It applies only when every case is a record (or a sealed
   * trait of records); otherwise the codec falls back to [[Key]]. With
   * `withRejectExtraFields`, the discriminator field is not an extra field.
   *
   * Beware that parsing may be too slow for deeply nested or recursive data
   * structures when the discriminator field is placed at the end of the JSON
   * object.
   *
   * Don't use it for untrusted input that should be parsed to deeply nested or
   * recursive data structures to avoid potential security vulnerabilities of
   * DoS/DoW attacks.
   *
   * @param name
   *   The name of the discriminator field to be added to the JSON object. This
   *   field value will contain the subtype identifier as its value.
   */
  case class Field(name: String) extends DiscriminatorKind

  /**
   * Wraps the encoded case in an object with one key, the case name:
   * `{"Paperback": {"pages": 600}}`. The default. It works for every kind of
   * case; the decoder reads only the first key, and a key that names no case
   * fails with `illegal discriminator`.
   */
  case object Key extends DiscriminatorKind

  /**
   * Writes the case as it is, with no name: `{"pages": 600}`. The decoder tries
   * the cases in declaration order and returns the first that decodes, so a
   * case whose fields are a subset of a later case's, extra fields being
   * ignored, hides that later case. When no case decodes, the error is
   * `expected a variant value`, without the cases' own errors.
   *
   * Beware that parsing can be too slow for large hierarchies of subtypes or
   * deeply nested or recursive data structures.
   *
   * Don't use it for untrusted input that should be parsed into deeply nested
   * or recursive data structures to avoid potential security vulnerabilities of
   * DoS/DoW attacks.
   */
  case object None extends DiscriminatorKind
}
