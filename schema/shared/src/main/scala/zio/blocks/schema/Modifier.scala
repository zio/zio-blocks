package zio.blocks.schema

import scala.annotation.meta.field
import scala.annotation.StaticAnnotation
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

/**
 * A sealed trait that represents a modifier used to annotate terms or reflect
 * values. Modifiers are used to provide metadata or additional configuration
 * associated with terms or reflect values.
 */
sealed trait Modifier extends StaticAnnotation

object Modifier {

  /**
   * `Term` represents a sealed trait for modifiers that annotate terms: record
   * fields or variant cases.
   *
   * The following are the known subtypes of `Term`:
   *   - `transient`: Used to indicate that a field should not be persisted or
   *     serialized.
   *   - `rename`: Used to specify a new name for a term, typically useful in
   *     serialization scenarios.
   *   - `alias`: Provides an alternative name (alias) for a term.
   *   - `config`: Represents a key-value pair for attaching additional
   *     configuration metadata to terms.
   */
  sealed trait Term extends Modifier

  /**
   * A modifier that marks a term (such as a field) as transient.
   */
  @field case class transient() extends Term

  /**
   * A modifier used to specify a new name for a term.
   *
   * @param name
   *   The new name to apply to the term.
   */
  @field case class rename(name: String) extends Term

  /**
   * A modifier representing an alias for a term.
   *
   * @param name
   *   The alias name for the term.
   */
  @field case class alias(name: String) extends Term

  /**
   * Represents a sealed trait for modifiers that annotate reflect values.
   */
  sealed trait Reflect extends Modifier

  /**
   * A configuration key-value pair, which can be attached to any type of
   * reflect values. The convention for keys is `<format>.<property>`. For
   * example, `protobuf.field-id` is a key that specifies the field id for a
   * protobuf format.
   */
  @field case class config(key: String, value: String) extends Term with Reflect

  implicit lazy val transientSchema: Schema[transient] = new Schema(
    reflect = new Reflect.Record[Binding, transient](
      fields = Vector.empty,
      typeId = TypeId.of[transient],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[transient](transient()),
        deconstructor = new ConstantDeconstructor[transient]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val renameSchema: Schema[rename] = new Schema(
    reflect = new Reflect.Record[Binding, rename](
      fields = Vector(
        Schema[String].reflect.asTerm("name")
      ),
      typeId = TypeId.of[rename],
      recordBinding = new Binding.Record(
        constructor = new Constructor[rename] {
          def usedRegisters: RegisterOffset                            = 1
          def construct(in: Registers, offset: RegisterOffset): rename =
            rename(in.getObject(offset + 0).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[rename] {
          def usedRegisters: RegisterOffset                                         = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: rename): Unit =
            out.setObject(offset + 0, in.name)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val aliasSchema: Schema[alias] = new Schema(
    reflect = new Reflect.Record[Binding, alias](
      fields = Vector(
        Schema[String].reflect.asTerm("name")
      ),
      typeId = TypeId.of[alias],
      recordBinding = new Binding.Record(
        constructor = new Constructor[alias] {
          def usedRegisters: RegisterOffset                           = 1
          def construct(in: Registers, offset: RegisterOffset): alias =
            alias(in.getObject(offset + 0).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[alias] {
          def usedRegisters: RegisterOffset                                        = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: alias): Unit =
            out.setObject(offset + 0, in.name)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val configSchema: Schema[config] = new Schema(
    reflect = new Reflect.Record[Binding, config](
      fields = Vector(
        Schema[String].reflect.asTerm("key"),
        Schema[String].reflect.asTerm("value")
      ),
      typeId = TypeId.of[config],
      recordBinding = new Binding.Record(
        constructor = new Constructor[config] {
          def usedRegisters: RegisterOffset                            = 2
          def construct(in: Registers, offset: RegisterOffset): config =
            config(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[config] {
          def usedRegisters: RegisterOffset                                         = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: config): Unit = {
            out.setObject(offset + 0, in.key)
            out.setObject(offset + 1, in.value)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val termSchema: Schema[Term] = new Schema(
    reflect = new Reflect.Variant[Binding, Term](
      cases = Vector(
        transientSchema.reflect.asTerm("transient"),
        renameSchema.reflect.asTerm("rename"),
        aliasSchema.reflect.asTerm("alias"),
        configSchema.reflect.asTerm("config")
      ),
      typeId = TypeId.of[Term],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Term] {
          def discriminate(a: Term): Int = a match {
            case _: transient => 0
            case _: rename    => 1
            case _: alias     => 2
            case _: config    => 3
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
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val reflectSchema: Schema[Reflect] = new Schema(
    reflect = new Reflect.Variant[Binding, Reflect](
      cases = Vector(
        configSchema.reflect.asTerm("config")
      ),
      typeId = TypeId.of[Reflect],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Reflect] {
          def discriminate(a: Reflect): Int = a match {
            case _: config => 0
          }
        },
        matchers = Matchers(
          new Matcher[config] {
            def downcastOrNull(a: Any): config = a match {
              case x: config => x
              case _         => null.asInstanceOf[config]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val schema: Schema[Modifier] = new Schema(
    reflect = new Reflect.Variant[Binding, Modifier](
      cases = Vector(
        transientSchema.reflect.asTerm("transient"),
        renameSchema.reflect.asTerm("rename"),
        aliasSchema.reflect.asTerm("alias"),
        configSchema.reflect.asTerm("config")
      ),
      typeId = TypeId.of[Modifier],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Modifier] {
          def discriminate(a: Modifier): Int = a match {
            case _: transient => 0
            case _: rename    => 1
            case _: alias     => 2
            case _: config    => 3
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
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}
