package zio.blocks.openapi.discriminator

import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, Constructor, Deconstructor, Registers}
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

final case class Discriminator(
  propertyName: String,
  mapping: Map[String, String] = Map.empty
)

object Discriminator {
  implicit val schema: Schema[Discriminator] = new Schema(
    reflect = new Reflect.Record[Binding, Discriminator](
      fields = Vector(
        Schema[String].reflect.asTerm("propertyName"),
        Schema[Map[String, String]].reflect.asTerm("mapping")
      ),
      typeId = TypeId.of[Discriminator],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Discriminator] {
          def usedRegisters: RegisterOffset                                   = binding.RegisterOffset(objects = 2)
          def construct(in: Registers, offset: RegisterOffset): Discriminator =
            Discriminator(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(offset + binding.RegisterOffset(objects = 1)).asInstanceOf[Map[String, String]]
            )
        },
        deconstructor = new Deconstructor[Discriminator] {
          def usedRegisters: RegisterOffset                                                = binding.RegisterOffset(objects = 2)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Discriminator): Unit = {
            out.setObject(offset, in.propertyName)
            out.setObject(offset + binding.RegisterOffset(objects = 1), in.mapping)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )
}
