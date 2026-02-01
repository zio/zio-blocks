package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, Schema}
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.Reflect
import zio.blocks.typeid.TypeId

sealed trait CaseAction {
  def reverse: CaseAction
}

object CaseAction {

  final case class Add(name: String, defaultValue: DynamicValue) extends CaseAction {
    def reverse: CaseAction = Remove(name, defaultValue)
  }

  final case class Remove(name: String, defaultForReverse: DynamicValue) extends CaseAction {
    def reverse: CaseAction = Add(name, defaultForReverse)
  }

  final case class Rename(from: String, to: String) extends CaseAction {
    def reverse: CaseAction = Rename(to, from)
  }

  def add(name: String, defaultValue: DynamicValue): CaseAction =
    Add(name, defaultValue)

  def remove(name: String, defaultForReverse: DynamicValue): CaseAction =
    Remove(name, defaultForReverse)

  def rename(from: String, to: String): CaseAction =
    Rename(from, to)

  implicit lazy val addSchema: Schema[Add] = new Schema(
    reflect = new Reflect.Record[Binding, Add](
      fields = Vector(
        Schema[String].reflect.asTerm("name"),
        Schema[DynamicValue].reflect.asTerm("defaultValue")
      ),
      typeId = TypeId.of[Add],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Add] {
          def usedRegisters: RegisterOffset                         = 2
          def construct(in: Registers, offset: RegisterOffset): Add =
            Add(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[DynamicValue]
            )
        },
        deconstructor = new Deconstructor[Add] {
          def usedRegisters: RegisterOffset                                      = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Add): Unit = {
            out.setObject(offset + 0, in.name)
            out.setObject(offset + 1, in.defaultValue)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val removeSchema: Schema[Remove] = new Schema(
    reflect = new Reflect.Record[Binding, Remove](
      fields = Vector(
        Schema[String].reflect.asTerm("name"),
        Schema[DynamicValue].reflect.asTerm("defaultForReverse")
      ),
      typeId = TypeId.of[Remove],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Remove] {
          def usedRegisters: RegisterOffset                            = 2
          def construct(in: Registers, offset: RegisterOffset): Remove =
            Remove(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[DynamicValue]
            )
        },
        deconstructor = new Deconstructor[Remove] {
          def usedRegisters: RegisterOffset                                         = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Remove): Unit = {
            out.setObject(offset + 0, in.name)
            out.setObject(offset + 1, in.defaultForReverse)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val renameSchema: Schema[Rename] = new Schema(
    reflect = new Reflect.Record[Binding, Rename](
      fields = Vector(
        Schema[String].reflect.asTerm("from"),
        Schema[String].reflect.asTerm("to")
      ),
      typeId = TypeId.of[Rename],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Rename] {
          def usedRegisters: RegisterOffset                            = 2
          def construct(in: Registers, offset: RegisterOffset): Rename =
            Rename(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[Rename] {
          def usedRegisters: RegisterOffset                                         = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Rename): Unit = {
            out.setObject(offset + 0, in.from)
            out.setObject(offset + 1, in.to)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val schema: Schema[CaseAction] = new Schema(
    reflect = new Reflect.Variant[Binding, CaseAction](
      cases = Vector(
        addSchema.reflect.asTerm("Add"),
        removeSchema.reflect.asTerm("Remove"),
        renameSchema.reflect.asTerm("Rename")
      ),
      typeId = TypeId.of[CaseAction],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[CaseAction] {
          def discriminate(a: CaseAction): Int = a match {
            case _: Add    => 0
            case _: Remove => 1
            case _: Rename => 2
          }
        },
        matchers = Matchers(
          new Matcher[Add] {
            def downcastOrNull(a: Any): Add = a match {
              case x: Add => x
              case _      => null.asInstanceOf[Add]
            }
          },
          new Matcher[Remove] {
            def downcastOrNull(a: Any): Remove = a match {
              case x: Remove => x
              case _         => null.asInstanceOf[Remove]
            }
          },
          new Matcher[Rename] {
            def downcastOrNull(a: Any): Rename = a match {
              case x: Rename => x
              case _         => null.asInstanceOf[Rename]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}
