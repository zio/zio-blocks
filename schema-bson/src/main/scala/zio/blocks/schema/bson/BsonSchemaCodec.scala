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

package zio.blocks.schema.bson

import zio.blocks.schema._

/**
 * Derives a [[BsonCodec]] from a schema with a [[BsonSchemaCodec.Config]]: how
 * sealed traits are stored, how their case names are written, and whether
 * unknown fields fail decoding. It does the same as
 * `Schema#derive(BsonCodecDeriver)` with the matching `BsonCodecDeriver.with*`
 * calls; use whichever reads better, for example to keep one `Config` value for
 * several types.
 *
 * {{{
 * import org.bson.BsonDocument
 * import zio.blocks.schema._
 * import zio.blocks.schema.bson.BsonSchemaCodec.{Config, SumTypeHandling}
 *
 * sealed trait Payment
 * case class Card(last4: String)   extends Payment
 * case class Voucher(code: String) extends Payment
 * object Payment {
 *   implicit val schema: Schema[Payment] = Schema.derived
 * }
 *
 * val config = Config.withSumTypeHandling(SumTypeHandling.DiscriminatorField("kind"))
 * val codec  = BsonSchemaCodec.bsonCodec(Payment.schema, config)
 * val stored = codec.encoder.toBsonValue(Card("4242")).asDocument().toJson
 * // {"last4": "4242", "kind": "Card"}
 * val read = codec.decoder.fromBsonValue(BsonDocument.parse("""{"kind": "Voucher", "code": "SPRING"}"""))
 * // Right(Voucher(SPRING))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/bson/ BSON Codec Module]]
 */
object BsonSchemaCodec {

  /**
   * A function from a case name of a sealed trait, such as `Card`, to the name
   * stored in BSON. Field names are not affected.
   */
  type TermMapping = String => String

  /**
   * How a codec stores a value of a sealed trait. `Option` is a sealed trait
   * too, so the setting applies to `Option` fields as well, which are stored as
   * a sub-document (or, for `None` under [[SumTypeHandling.NoDiscriminator]],
   * the string `"None"`) rather than as the value or `null` (a known bug).
   * `Modifier.discriminator` on a trait overrides the setting for that trait.
   */
  sealed trait SumTypeHandling

  /** The ways a codec can store a value of a sealed trait. */
  object SumTypeHandling {

    /**
     * Stores a case as a document with one field, named after the case, whose
     * value is the encoded case: `Card("4242")` becomes
     * `{"Card": {"last4": "4242"}}` and a case object `{"Cash": {}}`. Decoding
     * reads the first field and fails with `Unknown case name: <name>` for a
     * name it does not know. It is the default.
     */
    case object WrapperWithClassNameField extends SumTypeHandling

    /**
     * Stores a case as its own document plus a string field `name` that holds
     * the case name: `{"last4": "4242", "kind": "Card"}`. `toBsonValue` adds
     * the field after the case's fields and `encode` writes it first. Decoding
     * fails with `Missing or invalid discriminator field: <name>` (from
     * `fromBsonValue`) or `Missing discriminator field: <name>` (from `decode`)
     * when the field is absent or not a string.
     */
    final case class DiscriminatorField(name: String) extends SumTypeHandling

    /**
     * Stores a case as its encoded value alone, and a case object as a string
     * holding its name. Decoding tries the cases in the order of the schema's
     * cases and keeps the first that decodes, so with extra fields ignored, a
     * document that has the fields of two cases decodes as the earlier one.
     */
    case object NoDiscriminator extends SumTypeHandling
  }

  /**
   * The settings of a derived BSON codec. Start from the [[Config]] object,
   * which holds the defaults, and change it with the `with*` methods.
   *
   * @param sumTypeHandling
   *   How sealed traits are stored. Default:
   *   [[SumTypeHandling.WrapperWithClassNameField]].
   * @param classNameMapping
   *   Renames the case names of sealed traits, for example `_.toLowerCase`.
   *   `Modifier.caseNaming` on a trait and `Modifier.rename` on a case take
   *   precedence. Default: `identity`.
   * @param ignoreExtraFields
   *   When `true` (the default), decoding skips document fields that match no
   *   field of the case class. When `false`, such a field fails decoding with
   *   `Invalid extra field.` and its path, as `Modifier.noExtraFields` does for
   *   one type.
   * @param useNativeObjectId
   *   Has no effect (a known bug). An `org.bson.types.ObjectId` field whose
   *   schema is [[ObjectIdSupport.objectIdSchema]] is stored as a BSON
   *   `OBJECT_ID` whatever this setting is. Default: `false`.
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/built-in-codecs/bson/ BSON Codec Module]]
   */
  class Config private (
    val sumTypeHandling: SumTypeHandling,
    val classNameMapping: TermMapping,
    val ignoreExtraFields: Boolean,
    val useNativeObjectId: Boolean
  ) {

    /** Returns a copy that stores sealed traits as `sumTypeHandling` says. */
    def withSumTypeHandling(sumTypeHandling: SumTypeHandling): Config =
      copy(sumTypeHandling = sumTypeHandling)

    /** Returns a copy that renames case names with `classNameMapping`. */
    def withClassNameMapping(classNameMapping: TermMapping): Config =
      copy(classNameMapping = classNameMapping)

    /**
     * Returns a copy that skips unknown document fields when `true`, or fails
     * on them when `false`.
     */
    def withIgnoreExtraFields(ignoreExtraFields: Boolean): Config =
      copy(ignoreExtraFields = ignoreExtraFields)

    /**
     * Returns a copy with `useNativeObjectId` set, which has no effect (a known
     * bug); [[ObjectIdSupport.objectIdSchema]] alone makes an `ObjectId` a BSON
     * `OBJECT_ID`.
     */
    def withNativeObjectId(useNativeObjectId: Boolean): Config =
      copy(useNativeObjectId = useNativeObjectId)

    private[this] def copy(
      sumTypeHandling: SumTypeHandling = sumTypeHandling,
      classNameMapping: TermMapping = classNameMapping,
      ignoreExtraFields: Boolean = ignoreExtraFields,
      useNativeObjectId: Boolean = useNativeObjectId
    ): Config =
      new Config(sumTypeHandling, classNameMapping, ignoreExtraFields, useNativeObjectId)
  }

  /**
   * The default settings: [[SumTypeHandling.WrapperWithClassNameField]], case
   * names unchanged, unknown fields ignored, and `useNativeObjectId = false`.
   */
  object Config
      extends Config(
        sumTypeHandling = SumTypeHandling.WrapperWithClassNameField,
        classNameMapping = identity,
        ignoreExtraFields = true,
        useNativeObjectId = false
      )

  /**
   * Derives a codec with `config` and returns its encoder. Throws
   * `UnsupportedOperationException` for a type BSON does not support, such as a
   * map whose keys are not strings or a `DynamicValue`.
   */
  def bsonEncoder[A](schema: Schema[A], config: Config): BsonEncoder[A] =
    bsonCodec(schema, config).encoder

  /** Derives a codec with the default [[Config]] and returns its encoder. */
  def bsonEncoder[A](schema: Schema[A]): BsonEncoder[A] = bsonEncoder(schema, Config)

  /**
   * Derives a codec with `config` and returns its decoder. Throws
   * `UnsupportedOperationException` for a type BSON does not support, such as a
   * map whose keys are not strings or a `DynamicValue`.
   */
  def bsonDecoder[A](schema: Schema[A], config: Config): BsonDecoder[A] =
    bsonCodec(schema, config).decoder

  /** Derives a codec with the default [[Config]] and returns its decoder. */
  def bsonDecoder[A](schema: Schema[A]): BsonDecoder[A] = bsonDecoder(schema, Config)

  /**
   * Derives a codec with `config`, the same as `schema.derive` with a
   * [[BsonCodecDeriver]] configured the same way. Throws
   * `UnsupportedOperationException` for a type BSON does not support, such as a
   * map whose keys are not strings or a `DynamicValue`.
   */
  def bsonCodec[A](schema: Schema[A], config: Config): BsonCodec[A] =
    schema.derive(new BsonCodecDeriver(config))

  /**
   * Derives a codec with the default [[Config]], the same as
   * `schema.derive(BsonCodecDeriver)`.
   */
  def bsonCodec[A](schema: Schema[A]): BsonCodec[A] = schema.derive(BsonCodecDeriver)

  /**
   * Names for some built-in codecs of [[BsonCodec]], kept for source
   * compatibility; prefer the [[BsonCodec]] members.
   */
  object Codecs {

    /** The same as [[BsonCodec.unit]]. */
    val unitCodec: BsonCodec[Unit] = BsonCodec.unit

    /** The same as [[BsonCodec.scalaBigInt]]. */
    val bigIntCodec: BsonCodec[BigInt] = BsonCodec.scalaBigInt

    /** The same as [[BsonCodec.scalaBigDecimal]]. */
    val bigDecimalCodec: BsonCodec[BigDecimal] = BsonCodec.scalaBigDecimal

    /**
     * Returns the built-in codec of a primitive type, the one a derived codec
     * uses for it, such as [[BsonCodec.int]] for `PrimitiveType.Int`.
     */
    def primitiveCodec[A](primitiveType: PrimitiveType[A]): BsonCodec[A] =
      BsonCodecDeriver.primitiveCodec(primitiveType)
  }
}
