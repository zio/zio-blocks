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
 * Settings for decoding TOON, passed to the `decode` overloads of [[ToonCodec]]
 * that take a config. The companion object `ReaderConfig` is the default
 * configuration; each `with` method returns a copy with one setting changed.
 * The indentation step and strict mode apply to every codec; path expansion and
 * the discriminator field apply only to the `DynamicValue` codec,
 * `Schema[DynamicValue].derive(ToonFormat)`.
 *
 * Strict mode, on by default, rejects tabs in indentation, indentation that is
 * not a multiple of the step, blank lines between the items of an array,
 * unknown escapes in quoted strings such as `\q`, and path expansion conflicts.
 * A known bug: a quoted value in an inline array or a table row keeps an
 * unknown escape even in strict mode.
 *
 * {{{
 * import zio.blocks.schema._
 *
 * case class Limits(cpu: Int, memoryMb: Int)
 * object Limits {
 *   implicit val schema: Schema[Limits] = Schema.derived
 * }
 * case class Container(image: String, limits: Limits)
 * object Container {
 *   implicit val schema: Schema[Container] = Schema.derived
 * }
 *
 * val codec = Container.schema.derive(ToonFormat)
 * val input = "image: nginx\nlimits:\n   cpu: 2\n   memoryMb: 512"
 *
 * codec.decode(input)                            // Left(zio.blocks.schema.SchemaError: Indentation must be multiple of 2 spaces at: .limits)
 * codec.decode(input, ReaderConfig.withIndent(3)) // Right(Container(nginx,Limits(2,512)))
 * }}}
 *
 * @param indent
 *   the number of spaces per indentation level (default: 2)
 * @param delimiter
 *   the initial delimiter (default: Comma); it has no effect on decoding
 *   arrays, because the reader takes each array's delimiter from its header
 * @param strict
 *   whether to apply the strict-mode checks (default: true)
 * @param expandPaths
 *   whether the `DynamicValue` codec reads dotted keys as nested records
 *   (default: Off)
 * @param discriminatorField
 *   the field from which the `DynamicValue` codec reads a variant's case name
 *   (default: None, which reads every record as a record)
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/toon/ TOON]]
 */
class ReaderConfig private (
  val indent: Int,
  val delimiter: Delimiter,
  val strict: Boolean,
  val expandPaths: PathExpansion,
  val discriminatorField: Option[String]
) extends Serializable {

  /**
   * Returns a copy that expects `indent` spaces per indentation level.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `indent` is negative
   */
  def withIndent(indent: Int): ReaderConfig = {
    if (indent < 0) throw new IllegalArgumentException("'indent' should be not less than 0")
    copy(indent = indent)
  }

  /**
   * Returns a copy with `delimiter` as the initial delimiter. It has no effect
   * on decoding arrays: the reader takes each array's delimiter from its
   * header, where the writer records any delimiter other than the comma (a
   * known bug loses the tab of a top-level sequence's header).
   */
  def withDelimiter(delimiter: Delimiter): ReaderConfig = copy(delimiter = delimiter)

  /**
   * Returns a copy that applies the strict checks (`true`, the default) or
   * accepts input that breaks them (`false`): with `false`, a tab in the
   * indentation counts as one space, the depth is the number of spaces divided
   * by the step, blank lines inside arrays are skipped, an unknown escape is
   * kept as written, and the later value wins a path expansion conflict.
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class Probe(path: String, intervalSec: Int)
   * object Probe {
   *   implicit val schema: Schema[Probe] = Schema.derived
   * }
   * case class Check(name: String, probe: Probe)
   * object Check {
   *   implicit val schema: Schema[Check] = Schema.derived
   * }
   *
   * val codec = Check.schema.derive(ToonFormat)
   * val input = "name: health\nprobe:\n   path: /ready\n   intervalSec: 10"
   *
   * codec.decode(input, ReaderConfig.withStrict(false)) // Right(Check(health,Probe(/ready,10)))
   * }}}
   */
  def withStrict(strict: Boolean): ReaderConfig = copy(strict = strict)

  /**
   * Returns a copy that makes the `DynamicValue` codec read dotted keys as
   * nested records ([[PathExpansion.Safe]]) or as plain keys
   * ([[PathExpansion.Off]], the default). A derived codec ignores it, so decode
   * folded input as a `DynamicValue` first and convert it with
   * `Schema#fromDynamicValue`.
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class Db(port: Int)
   * object Db {
   *   implicit val schema: Schema[Db] = Schema.derived
   * }
   * case class Settings(app: String, db: Db)
   * object Settings {
   *   implicit val schema: Schema[Settings] = Schema.derived
   * }
   *
   * val dynamic = Schema[DynamicValue].derive(ToonFormat)
   * val config  = ReaderConfig.withExpandPaths(PathExpansion.Safe)
   *
   * dynamic.decode("app: billing\ndb.port: 5432", config).flatMap(Settings.schema.fromDynamicValue)
   * // Right(Settings(billing,Db(5432)))
   * }}}
   */
  def withExpandPaths(expandPaths: PathExpansion): ReaderConfig = copy(expandPaths = expandPaths)

  /**
   * Returns a copy that makes the `DynamicValue` codec read a record holding
   * the field `discriminatorField` with a string value as a variant: the
   * field's value becomes the case name and the other fields its value (a lone
   * field named `value` becomes the value itself). With `None`, the default,
   * every record stays a record. A derived codec ignores it; set its
   * discriminator with [[ToonCodecDeriver.withDiscriminatorKind]].
   */
  def withDiscriminatorField(discriminatorField: Option[String]): ReaderConfig =
    copy(discriminatorField = discriminatorField)

  private[this] def copy(
    indent: Int = indent,
    delimiter: Delimiter = delimiter,
    strict: Boolean = strict,
    expandPaths: PathExpansion = expandPaths,
    discriminatorField: Option[String] = discriminatorField
  ): ReaderConfig =
    new ReaderConfig(
      indent = indent,
      delimiter = delimiter,
      strict = strict,
      expandPaths = expandPaths,
      discriminatorField = discriminatorField
    )
}

/**
 * The default reader configuration: indentation step 2, comma delimiter, strict
 * mode on, path expansion off and no discriminator field. Start from it to
 * build another one, as in `ReaderConfig.withStrict(false)`.
 */
object ReaderConfig
    extends ReaderConfig(
      indent = 2,
      delimiter = Delimiter.Comma,
      strict = true,
      expandPaths = PathExpansion.Off,
      discriminatorField = None
    )
