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
 * Settings for encoding TOON, passed to the `encode` and `encodeToString`
 * overloads of [[ToonCodec]] that take a config. The companion object
 * `WriterConfig` is the default configuration; each `with` method returns a
 * copy with one setting changed. The indentation step applies to every codec;
 * the delimiter, key folding, flatten depth and discriminator field apply only
 * to the `DynamicValue` codec, `Schema[DynamicValue].derive(ToonFormat)`. A
 * codec derived for your own type takes its delimiter and discriminator from
 * [[ToonCodecDeriver]] instead.
 *
 * {{{
 * import zio.blocks.schema._
 *
 * case class Port(number: Int)
 * object Port {
 *   implicit val schema: Schema[Port] = Schema.derived
 * }
 * case class Service(name: String, port: Port)
 * object Service {
 *   implicit val schema: Schema[Service] = Schema.derived
 * }
 *
 * Service.schema.derive(ToonFormat).encodeToString(Service("web", Port(8080)), WriterConfig.withIndent(4))
 * // name: web
 * // port:
 * //     number: 8080
 * }}}
 *
 * @param indent
 *   the number of spaces per indentation level (default: 2)
 * @param delimiter
 *   the delimiter for inline arrays and table rows written by the
 *   `DynamicValue` codec (default: Comma); derived codecs ignore it and use
 *   [[ToonCodecDeriver.withDelimiter]]
 * @param keyFolding
 *   whether the `DynamicValue` codec folds chains of single-field records into
 *   dotted keys (default: Off)
 * @param flattenDepth
 *   the maximum number of keys that key folding joins into one dotted key
 *   (default: Int.MaxValue); it has no effect while key folding is off
 * @param discriminatorField
 *   the field in which the `DynamicValue` codec writes a variant's case name
 *   (default: None, which writes the case name as a key)
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/toon/ TOON]]
 */
class WriterConfig private (
  val indent: Int,
  val delimiter: Delimiter,
  val keyFolding: KeyFolding,
  val flattenDepth: Int,
  val discriminatorField: Option[String]
) extends Serializable {

  /**
   * Returns a copy that writes `indent` spaces per indentation level. With `0`
   * nested lines are not indented.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `indent` is negative
   */
  def withIndent(indent: Int): WriterConfig = {
    if (indent < 0) throw new IllegalArgumentException("'indent' should be not less than 0")
    copy(indent = indent)
  }

  /**
   * Returns a copy whose `DynamicValue` codec separates inline array values,
   * table rows and table header fields with `delimiter`. A derived codec
   * ignores it; set its delimiter with [[ToonCodecDeriver.withDelimiter]].
   */
  def withDelimiter(delimiter: Delimiter): WriterConfig =
    copy(delimiter = delimiter)

  /**
   * Returns a copy whose `DynamicValue` codec folds chains of single-field
   * records into one dotted key ([[KeyFolding.Safe]]) or writes them as
   * indented blocks ([[KeyFolding.Off]], the default). A derived codec ignores
   * it.
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class Port(number: Int)
   * object Port {
   *   implicit val schema: Schema[Port] = Schema.derived
   * }
   * case class Net(port: Port)
   * object Net {
   *   implicit val schema: Schema[Net] = Schema.derived
   * }
   * case class Deployment(app: String, net: Net, replicas: Int)
   * object Deployment {
   *   implicit val schema: Schema[Deployment] = Schema.derived
   * }
   *
   * val value   = Deployment.schema.toDynamicValue(Deployment("web", Net(Port(8080)), 3))
   * val dynamic = Schema[DynamicValue].derive(ToonFormat)
   *
   * dynamic.encodeToString(value, WriterConfig.withKeyFolding(KeyFolding.Safe))
   * // app: web
   * // net.port.number: 8080
   * // replicas: 3
   * }}}
   */
  def withKeyFolding(keyFolding: KeyFolding): WriterConfig =
    copy(keyFolding = keyFolding)

  /**
   * Returns a copy that joins at most `flattenDepth` keys into one dotted key
   * when key folding is on: with `2`, `net.port.number` is written as
   * `net.port:` with `number` indented under it. Values below `2` turn folding
   * off.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `flattenDepth` is negative
   */
  def withFlattenDepth(flattenDepth: Int): WriterConfig = {
    if (flattenDepth < 0) throw new IllegalArgumentException("'flattenDepth' should be not less than 0")
    copy(flattenDepth = flattenDepth)
  }

  /**
   * Returns a copy whose `DynamicValue` codec writes a variant as its case name
   * in the field `discriminatorField`, followed by the case's fields (a value
   * that is not a record goes in a field named `value`). With `None`, the
   * default, it writes the case name as a key with the value indented under it.
   * A derived codec ignores it; set its discriminator with
   * [[ToonCodecDeriver.withDiscriminatorKind]].
   *
   * {{{
   * import zio.blocks.schema._
   *
   * sealed trait Payment
   * case class Card(last4: String) extends Payment
   * case class Invoice(days: Int) extends Payment
   * object Payment {
   *   implicit val schema: Schema[Payment] = Schema.derived
   * }
   *
   * val value   = Payment.schema.toDynamicValue(Invoice(30))
   * val dynamic = Schema[DynamicValue].derive(ToonFormat)
   *
   * dynamic.encodeToString(value, WriterConfig.withDiscriminatorField(Some("method")))
   * // method: Invoice
   * // days: 30
   * }}}
   */
  def withDiscriminatorField(discriminatorField: Option[String]): WriterConfig =
    copy(discriminatorField = discriminatorField)

  private[this] def copy(
    indent: Int = indent,
    delimiter: Delimiter = delimiter,
    keyFolding: KeyFolding = keyFolding,
    flattenDepth: Int = flattenDepth,
    discriminatorField: Option[String] = discriminatorField
  ): WriterConfig =
    new WriterConfig(
      indent = indent,
      delimiter = delimiter,
      keyFolding = keyFolding,
      flattenDepth = flattenDepth,
      discriminatorField = discriminatorField
    )
}

/**
 * The default writer configuration: indentation step 2, comma delimiter, key
 * folding off, unlimited flatten depth and no discriminator field. Start from
 * it to build another one, as in `WriterConfig.withIndent(4)`.
 */
object WriterConfig
    extends WriterConfig(
      indent = 2,
      delimiter = Delimiter.Comma,
      keyFolding = KeyFolding.Off,
      flattenDepth = Int.MaxValue,
      discriminatorField = None
    )
