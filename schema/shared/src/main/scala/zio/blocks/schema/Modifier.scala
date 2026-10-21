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

package zio.blocks.schema

import zio.blocks.chunk.Chunk
import scala.annotation.meta.field
import scala.annotation.StaticAnnotation
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

/**
 * A `Modifier` is metadata on a schema, or on one of its fields or cases, that
 * codecs and other derivations read: it renames a field in JSON, accepts an old
 * name, leaves a field out, or carries format-specific configuration. Modifiers
 * are plain data with schemas of their own, so they can be serialized together
 * with a schema.
 *
 * Every modifier is also an annotation. `Schema.derived` reads
 * [[Modifier.Term]] annotations on fields and cases, and [[Modifier.Reflect]]
 * annotations on the type. To leave the type unannotated, add them with
 * `Schema#modifier` for the type, or with `DerivationBuilder#modifier` or
 * `Deriver#withModifier` for one derivation.
 *
 * Which modifiers a codec reads depends on its format: the JSON codec reads all
 * the built-in ones except `id` and `config`, while the Avro and MessagePack
 * codecs, for example, ignore `transient`, `rename` and `alias`.
 *
 * {{{
 * case class Book(
 *   @Modifier.rename("name") title: String,
 *   @Modifier.alias("published") year: Int,
 *   @Modifier.transient() notes: String = ""
 * )
 * object Book {
 *   implicit val schema: Schema[Book] = Schema.derived
 * }
 *
 * val json = Book("Zionomicon", 2021, "signed copy").toJsonString
 * // {"name":"Zionomicon","year":2021}
 * val book = """{"name":"Zionomicon","published":2021}""".fromJson[Book]
 * // Right(Book(Zionomicon,2021,))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/modifier/ Modifier]]
 */
sealed trait Modifier extends StaticAnnotation

/** The built-in modifiers and their schemas. */
object Modifier {

  /**
   * Modifiers of a record field or a variant case: `transient`,
   * `encodeTransient`, `rename`, `alias`, `id` and `config`.
   */
  sealed trait Term extends Modifier

  /**
   * Makes the JSON codec leave a field out when it encodes. The TOON and BSON
   * codecs also read it; the Avro, MessagePack, Thrift, CSV, XML and YAML
   * codecs ignore it. When the JSON codec decodes, it treats the field's key
   * like an unknown key (skipped, or rejected when extra fields are rejected),
   * and the field gets its default value.
   *
   * The field needs a default value unless it is an `Option` or a collection;
   * otherwise `Schema.derived` fails to compile.
   */
  @field case class transient() extends Term

  /**
   * Leaves a field out when the JSON codec encodes, but still reads it when it
   * decodes. Like `transient`, it needs a default value unless the field is an
   * `Option` or a collection.
   */
  @field case class encodeTransient() extends Term

  /**
   * Makes the JSON codec use `name` for a field or a case instead of its Scala
   * name, both when encoding and when decoding: the Scala name is no longer
   * accepted, so add an `alias` to accept it too. It takes precedence over
   * `fieldNaming` and `caseNaming`.
   */
  @field case class rename(name: String) extends Term

  /**
   * A modifier that explicitly marks a term as the primary key field. Used by
   * `Repo.derived` to resolve ambiguity when multiple fields share the same
   * type or when auto-detection would be inconclusive.
   */
  @field case class id() extends Term

  /**
   * Makes the JSON codec also accept `name` for a field or a case when
   * decoding. Encoding still uses the main name: the `rename` name, or the
   * Scala name after any naming strategy. A field or case can have several
   * aliases.
   */
  @field case class alias(name: String) extends Term

  /**
   * Modifiers of a type, that is, of a schema node: `config`, `discriminator`,
   * `noExtraFields`, `fieldNaming` and `caseNaming`.
   */
  sealed trait Reflect extends Modifier

  /**
   * Makes the JSON codec of a variant write the case name in the field `name`
   * of the case's object, as in `{"type":"Hardcover"}`, instead of wrapping the
   * case in an object keyed by its name. It takes precedence over the
   * discriminator setting of the deriver.
   */
  case class discriminator(name: String) extends Reflect

  /**
   * Makes the JSON codec of a record reject keys that match no field, as
   * `JsonCodecDeriver.withRejectExtraFields(true)` does for every record.
   */
  case class noExtraFields() extends Reflect

  /**
   * Makes the JSON codec of a record map its field names with the naming
   * strategy `strategy`: `identity`, `snake_case`, `camelCase`, `kebab-case` or
   * `PascalCase`. Another value makes the derivation of the codec throw an
   * `IllegalArgumentException`. A field with a `rename` keeps that name.
   */
  case class fieldNaming(strategy: String) extends Reflect

  /**
   * Makes the JSON codec of a variant map its case names with the naming
   * strategy `strategy`, one of the values that `fieldNaming` accepts. A case
   * with a `rename` keeps that name.
   */
  case class caseNaming(strategy: String) extends Reflect

  /**
   * A configuration key-value pair, which can be attached to any type of
   * reflect values. The convention for keys is `<format>.<property>`. For
   * example, `protobuf.field-id` is a key that specifies the field id for a
   * protobuf format.
   */
  @field case class config(key: String, value: String) extends Term with Reflect

  /** The schema of `transient`. */
  implicit lazy val transientSchema: Schema[transient] = new Schema(
    reflect = new Reflect.Record[Binding, transient](
      fields = Chunk.empty,
      typeId = TypeId.of[transient],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[transient](transient()),
        deconstructor = new ConstantDeconstructor[transient]
      ),
      modifiers = Chunk.empty
    )
  )

  /** The schema of `encodeTransient`. */
  implicit lazy val encodeTransientSchema: Schema[encodeTransient] = new Schema(
    reflect = new Reflect.Record[Binding, encodeTransient](
      fields = Chunk.empty,
      typeId = TypeId.of[encodeTransient],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[encodeTransient](encodeTransient()),
        deconstructor = new ConstantDeconstructor[encodeTransient]
      ),
      modifiers = Chunk.empty
    )
  )

  /** The schema of `rename`. */
  implicit lazy val renameSchema: Schema[rename] = new Schema(
    reflect = new Reflect.Record[Binding, rename](
      fields = Chunk.single(Schema[String].reflect.asTerm("name")),
      typeId = TypeId.of[rename],
      recordBinding = new Binding.Record(
        constructor = new Constructor[rename] {
          def usedRegisters: RegisterOffset                            = 1
          def construct(in: Registers, offset: RegisterOffset): rename =
            new rename(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[rename] {
          def usedRegisters: RegisterOffset                                         = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: rename): Unit =
            out.setObject(offset, in.name)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /** The schema of `id`. */
  implicit lazy val idSchema: Schema[id] = new Schema(
    reflect = new Reflect.Record[Binding, id](
      fields = Chunk.empty,
      typeId = TypeId.of[id],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[id](id()),
        deconstructor = new ConstantDeconstructor[id]
      ),
      modifiers = Chunk.empty
    )
  )

  /** The schema of `alias`. */
  implicit lazy val aliasSchema: Schema[alias] = new Schema(
    reflect = new Reflect.Record[Binding, alias](
      fields = Chunk.single(Schema[String].reflect.asTerm("name")),
      typeId = TypeId.of[alias],
      recordBinding = new Binding.Record(
        constructor = new Constructor[alias] {
          def usedRegisters: RegisterOffset                           = 1
          def construct(in: Registers, offset: RegisterOffset): alias =
            new alias(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[alias] {
          def usedRegisters: RegisterOffset                                        = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: alias): Unit =
            out.setObject(offset, in.name)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /** The schema of `discriminator`. */
  implicit lazy val discriminatorSchema: Schema[discriminator] = new Schema(
    reflect = new Reflect.Record[Binding, discriminator](
      fields = Chunk.single(Schema[String].reflect.asTerm("name")),
      typeId = TypeId.of[discriminator],
      recordBinding = new Binding.Record(
        constructor = new Constructor[discriminator] {
          def usedRegisters: RegisterOffset                                   = 1
          def construct(in: Registers, offset: RegisterOffset): discriminator =
            new discriminator(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[discriminator] {
          def usedRegisters: RegisterOffset                                                = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: discriminator): Unit =
            out.setObject(offset, in.name)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /** The schema of `noExtraFields`. */
  implicit lazy val noExtraFieldsSchema: Schema[noExtraFields] = new Schema(
    reflect = new Reflect.Record[Binding, noExtraFields](
      fields = Chunk.empty,
      typeId = TypeId.of[noExtraFields],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[noExtraFields](noExtraFields()),
        deconstructor = new ConstantDeconstructor[noExtraFields]
      ),
      modifiers = Chunk.empty
    )
  )

  /** The schema of `fieldNaming`. */
  implicit lazy val fieldNamingSchema: Schema[fieldNaming] = new Schema(
    reflect = new Reflect.Record[Binding, fieldNaming](
      fields = Chunk.single(Schema[String].reflect.asTerm("strategy")),
      typeId = TypeId.of[fieldNaming],
      recordBinding = new Binding.Record(
        constructor = new Constructor[fieldNaming] {
          def usedRegisters: RegisterOffset                                 = 1
          def construct(in: Registers, offset: RegisterOffset): fieldNaming =
            new fieldNaming(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[fieldNaming] {
          def usedRegisters: RegisterOffset                                              = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: fieldNaming): Unit =
            out.setObject(offset, in.strategy)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /** The schema of `caseNaming`. */
  implicit lazy val caseNamingSchema: Schema[caseNaming] = new Schema(
    reflect = new Reflect.Record[Binding, caseNaming](
      fields = Chunk.single(Schema[String].reflect.asTerm("strategy")),
      typeId = TypeId.of[caseNaming],
      recordBinding = new Binding.Record(
        constructor = new Constructor[caseNaming] {
          def usedRegisters: RegisterOffset                                = 1
          def construct(in: Registers, offset: RegisterOffset): caseNaming =
            new caseNaming(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[caseNaming] {
          def usedRegisters: RegisterOffset                                             = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: caseNaming): Unit =
            out.setObject(offset, in.strategy)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /** The schema of `config`. */
  implicit lazy val configSchema: Schema[config] = new Schema(
    reflect = new Reflect.Record[Binding, config](
      fields = Chunk(
        Schema[String].reflect.asTerm("key"),
        Schema[String].reflect.asTerm("value")
      ),
      typeId = TypeId.of[config],
      recordBinding = new Binding.Record(
        constructor = new Constructor[config] {
          def usedRegisters: RegisterOffset                            = 2
          def construct(in: Registers, offset: RegisterOffset): config =
            new config(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[config] {
          def usedRegisters: RegisterOffset                                         = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: config): Unit = {
            out.setObject(offset, in.key)
            out.setObject(offset + 1, in.value)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /** The schema of [[Modifier.Term]], a variant of the term modifiers. */
  implicit lazy val termSchema: Schema[Term] = new Schema(
    reflect = new Reflect.Variant[Binding, Term](
      cases = Chunk(
        transientSchema.reflect.asTerm("transient"),
        renameSchema.reflect.asTerm("rename"),
        aliasSchema.reflect.asTerm("alias"),
        configSchema.reflect.asTerm("config"),
        encodeTransientSchema.reflect.asTerm("encodeTransient"),
        idSchema.reflect.asTerm("id")
      ),
      typeId = TypeId.of[Term],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Term] {
          def discriminate(a: Term): Int = a match {
            case _: transient       => 0
            case _: rename          => 1
            case _: alias           => 2
            case _: config          => 3
            case _: encodeTransient => 4
            case _: id              => 5
          }
        },
        matchers = Matchers(
          new Matcher[transient] {
            def downcastOrNull(a: Any): transient = a match {
              case x: transient => x
              case _            => null.asInstanceOf[transient]
            }
          },
          new Matcher[rename] {
            def downcastOrNull(a: Any): rename = a match {
              case x: rename => x
              case _         => null.asInstanceOf[rename]
            }
          },
          new Matcher[alias] {
            def downcastOrNull(a: Any): alias = a match {
              case x: alias => x
              case _        => null.asInstanceOf[alias]
            }
          },
          new Matcher[config] {
            def downcastOrNull(a: Any): config = a match {
              case x: config => x
              case _         => null.asInstanceOf[config]
            }
          },
          new Matcher[encodeTransient] {
            def downcastOrNull(a: Any): encodeTransient = a match {
              case x: encodeTransient => x
              case _                  => null.asInstanceOf[encodeTransient]
            }
          },
          new Matcher[id] {
            def downcastOrNull(a: Any): id = a match {
              case x: id => x
              case _     => null.asInstanceOf[id]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  /** The schema of [[Modifier.Reflect]], a variant of the type modifiers. */
  implicit lazy val reflectSchema: Schema[Reflect] = new Schema(
    reflect = new Reflect.Variant[Binding, Reflect](
      cases = Chunk(
        configSchema.reflect.asTerm("config"),
        discriminatorSchema.reflect.asTerm("discriminator"),
        noExtraFieldsSchema.reflect.asTerm("noExtraFields"),
        fieldNamingSchema.reflect.asTerm("fieldNaming"),
        caseNamingSchema.reflect.asTerm("caseNaming")
      ),
      typeId = TypeId.of[Reflect],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Reflect] {
          def discriminate(a: Reflect): Int = a match {
            case _: config        => 0
            case _: discriminator => 1
            case _: noExtraFields => 2
            case _: fieldNaming   => 3
            case _: caseNaming    => 4
          }
        },
        matchers = Matchers(
          new Matcher[config] {
            def downcastOrNull(a: Any): config = a match {
              case x: config => x
              case _         => null.asInstanceOf[config]
            }
          },
          new Matcher[discriminator] {
            def downcastOrNull(a: Any): discriminator = a match {
              case x: discriminator => x
              case _                => null.asInstanceOf[discriminator]
            }
          },
          new Matcher[noExtraFields] {
            def downcastOrNull(a: Any): noExtraFields = a match {
              case x: noExtraFields => x
              case _                => null.asInstanceOf[noExtraFields]
            }
          },
          new Matcher[fieldNaming] {
            def downcastOrNull(a: Any): fieldNaming = a match {
              case x: fieldNaming => x
              case _              => null.asInstanceOf[fieldNaming]
            }
          },
          new Matcher[caseNaming] {
            def downcastOrNull(a: Any): caseNaming = a match {
              case x: caseNaming => x
              case _             => null.asInstanceOf[caseNaming]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  /** The schema of [[Modifier]], a variant of all built-in modifiers. */
  implicit lazy val schema: Schema[Modifier] = new Schema(
    reflect = new Reflect.Variant[Binding, Modifier](
      cases = Chunk(
        transientSchema.reflect.asTerm("transient"),
        renameSchema.reflect.asTerm("rename"),
        aliasSchema.reflect.asTerm("alias"),
        configSchema.reflect.asTerm("config"),
        encodeTransientSchema.reflect.asTerm("encodeTransient"),
        discriminatorSchema.reflect.asTerm("discriminator"),
        noExtraFieldsSchema.reflect.asTerm("noExtraFields"),
        fieldNamingSchema.reflect.asTerm("fieldNaming"),
        caseNamingSchema.reflect.asTerm("caseNaming"),
        idSchema.reflect.asTerm("id")
      ),
      typeId = TypeId.of[Modifier],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Modifier] {
          def discriminate(a: Modifier): Int = a match {
            case _: transient       => 0
            case _: rename          => 1
            case _: alias           => 2
            case _: config          => 3
            case _: encodeTransient => 4
            case _: discriminator   => 5
            case _: noExtraFields   => 6
            case _: fieldNaming     => 7
            case _: caseNaming      => 8
            case _: id              => 9
          }
        },
        matchers = Matchers(
          new Matcher[transient] {
            def downcastOrNull(a: Any): transient = a match {
              case x: transient => x
              case _            => null.asInstanceOf[transient]
            }
          },
          new Matcher[rename] {
            def downcastOrNull(a: Any): rename = a match {
              case x: rename => x
              case _         => null.asInstanceOf[rename]
            }
          },
          new Matcher[alias] {
            def downcastOrNull(a: Any): alias = a match {
              case x: alias => x
              case _        => null.asInstanceOf[alias]
            }
          },
          new Matcher[config] {
            def downcastOrNull(a: Any): config = a match {
              case x: config => x
              case _         => null.asInstanceOf[config]
            }
          },
          new Matcher[encodeTransient] {
            def downcastOrNull(a: Any): encodeTransient = a match {
              case x: encodeTransient => x
              case _                  => null.asInstanceOf[encodeTransient]
            }
          },
          new Matcher[discriminator] {
            def downcastOrNull(a: Any): discriminator = a match {
              case x: discriminator => x
              case _                => null.asInstanceOf[discriminator]
            }
          },
          new Matcher[noExtraFields] {
            def downcastOrNull(a: Any): noExtraFields = a match {
              case x: noExtraFields => x
              case _                => null.asInstanceOf[noExtraFields]
            }
          },
          new Matcher[fieldNaming] {
            def downcastOrNull(a: Any): fieldNaming = a match {
              case x: fieldNaming => x
              case _              => null.asInstanceOf[fieldNaming]
            }
          },
          new Matcher[caseNaming] {
            def downcastOrNull(a: Any): caseNaming = a match {
              case x: caseNaming => x
              case _             => null.asInstanceOf[caseNaming]
            }
          },
          new Matcher[id] {
            def downcastOrNull(a: Any): id = a match {
              case x: id => x
              case _     => null.asInstanceOf[id]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )
}
