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

package zio.blocks.schema.toon

/**
 * Chooses how a codec derived with [[ToonCodecDeriver]] records which case of a
 * sealed trait or enum a value is. Set it with
 * [[ToonCodecDeriver.withDiscriminatorKind]]; the default is
 * [[DiscriminatorKind.Key]]. A sealed trait whose cases all have no fields is
 * written as the bare case name, such as `Low`, whatever the setting, unless
 * [[ToonCodecDeriver.withEnumValuesAsStrings]] is `false`. For
 * `case class Rect(width: Double, height: Double) extends Shape`:
 *   - [[DiscriminatorKind.Key]] writes `Rect:` followed by the indented fields;
 *   - [[DiscriminatorKind.Field]] with `"kind"` writes `kind: Rect` followed by
 *     the fields at the same level;
 *   - [[DiscriminatorKind.None]] writes only the fields, and decoding tries
 *     each case in declaration order until one decodes.
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/toon/ TOON]]
 */
sealed trait DiscriminatorKind

/**
 * The cases of `DiscriminatorKind`: `DiscriminatorKind.Field`,
 * `DiscriminatorKind.Key` and `DiscriminatorKind.None`.
 */
object DiscriminatorKind {

  /**
   * Writes the case name in a field called `name`, followed by the case's own
   * fields at the same level, such as `kind: Rect`. Decoding fails with
   * `Missing discriminator field: kind` when the field is absent and with
   * `Unknown variant case: Square` for a name no case has.
   *
   * It applies when every case is a record; otherwise the deriver uses [[Key]]
   * for that type. The decoder buffers every line of the value and reads them
   * again once it has found the field, so nested variants of this kind are read
   * once per level of nesting; keep that in mind for deeply nested untrusted
   * input.
   *
   * Two known bugs: a case object (a case with no fields) is written as nothing
   * at all, not as `kind: Idle`, so decoding it fails with
   * `Missing discriminator field`; and a variant stored in a record field that
   * other fields follow is written with its fields at the record's level, so
   * decoding it takes the following fields too and fails.
   *
   * @param name
   *   the name of the field that holds the case name
   */
  case class Field(name: String) extends DiscriminatorKind

  /**
   * Writes the case name as a key with the case's fields indented under it,
   * such as `Rect:` followed by `width: 2` and `height: 3.5` one level deeper.
   * The default. Decoding fails with `Unknown variant case: Square` for a key
   * no case has.
   */
  case object Key extends DiscriminatorKind

  /**
   * Writes only the case's fields, with no case name. Decoding tries the cases
   * in declaration order and returns the first one that decodes, so a value of
   * a later case comes back as an earlier case whenever the earlier case can
   * read its fields: with extra fields skipped, `Big(1, 2)` written as `x: 1`
   * and `y: 2` decodes as `Small(1)` when `Small(x: Int)` comes first. When no
   * case decodes, it fails with `expected a variant value`, which hides why
   * each case failed. Each attempt reads the input again, which is slow for
   * many cases or deeply nested values; keep that in mind for untrusted input.
   *
   * A known bug: a variant stored in a record field that other fields follow is
   * written with its fields at the record's level, so decoding it takes the
   * following fields too and fails.
   */
  case object None extends DiscriminatorKind
}
