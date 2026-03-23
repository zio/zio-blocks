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

package zio.blocks.openapi.discriminator

import zio.blocks.chunk.ChunkMap
import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, Constructor, Deconstructor, Registers}
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

final case class Discriminator(
  propertyName: String,
  mapping: ChunkMap[String, String] = ChunkMap.empty
)

object Discriminator {
  implicit val schema: Schema[Discriminator] = new Schema(
    reflect = new Reflect.Record[Binding, Discriminator](
      fields = Vector(
        Schema[String].reflect.asTerm("propertyName"),
        Schema[ChunkMap[String, String]].reflect.asTerm("mapping")
      ),
      typeId = TypeId.of[Discriminator],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Discriminator] {
          def usedRegisters: RegisterOffset                                   = binding.RegisterOffset(objects = 2)
          def construct(in: Registers, offset: RegisterOffset): Discriminator =
            Discriminator(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(offset + binding.RegisterOffset(objects = 1)).asInstanceOf[ChunkMap[String, String]]
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
