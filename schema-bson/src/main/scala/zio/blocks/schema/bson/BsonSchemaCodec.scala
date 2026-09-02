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

object BsonSchemaCodec {

  type TermMapping = String => String

  sealed trait SumTypeHandling

  object SumTypeHandling {

    /**
     * Wrapper with class name as field:
     */
    case object WrapperWithClassNameField extends SumTypeHandling

    /**
     * Discriminator field approach:
     */
    final case class DiscriminatorField(name: String) extends SumTypeHandling

    /**
     * No discriminator - encodes variant directly without wrapper or
     */
    case object NoDiscriminator extends SumTypeHandling
  }

  /**
   * Configuration for the BSON schema codec.
   * @param sumTypeHandling
   *   The handling of sum types.
   * @param classNameMapping
   *   The mapping of class names.
   * @param ignoreExtraFields
   *   If true (default), extra fields in BSON documents are silently ignored
   *   during decoding. If false, decoding will fail with an error when
   *   encountering unknown fields.
   * @param useNativeObjectId
   *   If true, org.bson.types.ObjectId will be encoded/decoded using BSON's
   *   native ObjectId type (BsonType.OBJECT_ID). If false, ObjectId will be
   *   treated as a regular wrapper and encoded as a string. Default is false.
   *   Note: When using ObjectIdSupport.objectIdSchema, this is automatically
   *   detected regardless of this setting.
   */
  class Config private (
    val sumTypeHandling: SumTypeHandling,
    val classNameMapping: TermMapping,
    val ignoreExtraFields: Boolean,
    val useNativeObjectId: Boolean
  ) {

    def withSumTypeHandling(sumTypeHandling: SumTypeHandling): Config =
      copy(sumTypeHandling = sumTypeHandling)

    def withClassNameMapping(classNameMapping: TermMapping): Config =
      copy(classNameMapping = classNameMapping)

    def withIgnoreExtraFields(ignoreExtraFields: Boolean): Config =
      copy(ignoreExtraFields = ignoreExtraFields)

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

  object Config
      extends Config(
        sumTypeHandling = SumTypeHandling.WrapperWithClassNameField,
        classNameMapping = identity,
        ignoreExtraFields = true,
        useNativeObjectId = false
      )

  /** Derives a BSON encoder using the shared schema derivation framework. */
  def bsonEncoder[A](schema: Schema[A], config: Config): BsonEncoder[A] =
    bsonCodec(schema, config).encoder

  def bsonEncoder[A](schema: Schema[A]): BsonEncoder[A] = bsonEncoder(schema, Config)

  /** Derives a BSON decoder using the shared schema derivation framework. */
  def bsonDecoder[A](schema: Schema[A], config: Config): BsonDecoder[A] =
    bsonCodec(schema, config).decoder

  def bsonDecoder[A](schema: Schema[A]): BsonDecoder[A] = bsonDecoder(schema, Config)

  /** Compatibility facade for [[BsonCodecDeriver]]. */
  def bsonCodec[A](schema: Schema[A], config: Config): BsonCodec[A] =
    schema.derive(new BsonCodecDeriver(config))

  def bsonCodec[A](schema: Schema[A]): BsonCodec[A] = schema.derive(BsonCodecDeriver)

  /** Runtime-codec aliases retained for source compatibility. */
  object Codecs {
    val unitCodec: BsonCodec[Unit]             = BsonCodec.unit
    val bigIntCodec: BsonCodec[BigInt]         = BsonCodec.scalaBigInt
    val bigDecimalCodec: BsonCodec[BigDecimal] = BsonCodec.scalaBigDecimal

    def primitiveCodec[A](primitiveType: PrimitiveType[A]): BsonCodec[A] =
      BsonCodecDeriver.primitiveCodec(primitiveType)
  }
}
