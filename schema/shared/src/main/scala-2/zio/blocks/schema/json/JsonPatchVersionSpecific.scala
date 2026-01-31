package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{Reflect, Schema}
import zio.blocks.schema.binding.{
  Binding,
  Constructor,
  Deconstructor,
  Discriminator,
  Matcher,
  Matchers,
  RegisterOffset,
  Registers
}
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

/**
 * Scala 2 manual schema implementations for JsonPatch inner types.
 *
 * These mirror the Schema.derived definitions in the Scala 3 version but are
 * constructed manually since Scala 2 lacks the necessary macro support.
 */

// ─────────────────────────────────────────────────────────────────────────────
// StringOp schemas
// ─────────────────────────────────────────────────────────────────────────────

private[json] trait StringOpVersionSpecific {
  import JsonPatch.StringOp

  private lazy val insertSchema: Schema[StringOp.Insert] = new Schema(
    reflect = new Reflect.Record[Binding, StringOp.Insert](
      fields = Vector(
        Schema[Int].reflect.asTerm("index"),
        Schema[String].reflect.asTerm("text")
      ),
      typeId = TypeId.of[StringOp.Insert],
      recordBinding = new Binding.Record(
        constructor = new Constructor[StringOp.Insert] {
          def usedRegisters: RegisterOffset                                     = RegisterOffset(ints = 1, objects = 1)
          def construct(in: Registers, offset: RegisterOffset): StringOp.Insert =
            StringOp.Insert(in.getInt(offset), in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[StringOp.Insert] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(ints = 1, objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: StringOp.Insert): Unit = {
            out.setInt(offset, in.index)
            out.setObject(offset, in.text)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val deleteSchema: Schema[StringOp.Delete] = new Schema(
    reflect = new Reflect.Record[Binding, StringOp.Delete](
      fields = Vector(
        Schema[Int].reflect.asTerm("index"),
        Schema[Int].reflect.asTerm("length")
      ),
      typeId = TypeId.of[StringOp.Delete],
      recordBinding = new Binding.Record(
        constructor = new Constructor[StringOp.Delete] {
          def usedRegisters: RegisterOffset                                     = RegisterOffset(ints = 2)
          def construct(in: Registers, offset: RegisterOffset): StringOp.Delete =
            StringOp.Delete(in.getInt(offset), in.getInt(RegisterOffset.incrementFloatsAndInts(offset)))
        },
        deconstructor = new Deconstructor[StringOp.Delete] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(ints = 2)
          def deconstruct(out: Registers, offset: RegisterOffset, in: StringOp.Delete): Unit = {
            out.setInt(offset, in.index)
            out.setInt(RegisterOffset.incrementFloatsAndInts(offset), in.length)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val appendSchema: Schema[StringOp.Append] = new Schema(
    reflect = new Reflect.Record[Binding, StringOp.Append](
      fields = Vector(Schema[String].reflect.asTerm("text")),
      typeId = TypeId.of[StringOp.Append],
      recordBinding = new Binding.Record(
        constructor = new Constructor[StringOp.Append] {
          def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): StringOp.Append =
            StringOp.Append(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[StringOp.Append] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: StringOp.Append): Unit =
            out.setObject(offset, in.text)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val modifySchema: Schema[StringOp.Modify] = new Schema(
    reflect = new Reflect.Record[Binding, StringOp.Modify](
      fields = Vector(
        Schema[Int].reflect.asTerm("index"),
        Schema[Int].reflect.asTerm("length"),
        Schema[String].reflect.asTerm("text")
      ),
      typeId = TypeId.of[StringOp.Modify],
      recordBinding = new Binding.Record(
        constructor = new Constructor[StringOp.Modify] {
          def usedRegisters: RegisterOffset                                     = RegisterOffset(ints = 2, objects = 1)
          def construct(in: Registers, offset: RegisterOffset): StringOp.Modify =
            StringOp.Modify(
              in.getInt(offset),
              in.getInt(RegisterOffset.incrementFloatsAndInts(offset)),
              in.getObject(offset).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[StringOp.Modify] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(ints = 2, objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: StringOp.Modify): Unit = {
            out.setInt(offset, in.index)
            out.setInt(RegisterOffset.incrementFloatsAndInts(offset), in.length)
            out.setObject(offset, in.text)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val schema: Schema[StringOp] = new Schema(
    reflect = new Reflect.Variant[Binding, StringOp](
      cases = Vector(
        insertSchema.reflect.asTerm("Insert"),
        deleteSchema.reflect.asTerm("Delete"),
        appendSchema.reflect.asTerm("Append"),
        modifySchema.reflect.asTerm("Modify")
      ),
      typeId = TypeId.of[StringOp],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[StringOp] {
          def discriminate(a: StringOp): Int = a match {
            case _: StringOp.Insert => 0
            case _: StringOp.Delete => 1
            case _: StringOp.Append => 2
            case _: StringOp.Modify => 3
          }
        },
        matchers = Matchers(
          new Matcher[StringOp.Insert] {
            def downcastOrNull(a: Any): StringOp.Insert = a match {
              case x: StringOp.Insert => x
              case _                  => null.asInstanceOf[StringOp.Insert]
            }
          },
          new Matcher[StringOp.Delete] {
            def downcastOrNull(a: Any): StringOp.Delete = a match {
              case x: StringOp.Delete => x
              case _                  => null.asInstanceOf[StringOp.Delete]
            }
          },
          new Matcher[StringOp.Append] {
            def downcastOrNull(a: Any): StringOp.Append = a match {
              case x: StringOp.Append => x
              case _                  => null.asInstanceOf[StringOp.Append]
            }
          },
          new Matcher[StringOp.Modify] {
            def downcastOrNull(a: Any): StringOp.Modify = a match {
              case x: StringOp.Modify => x
              case _                  => null.asInstanceOf[StringOp.Modify]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// PrimitiveOp schemas
// ─────────────────────────────────────────────────────────────────────────────

private[json] trait PrimitiveOpVersionSpecific {
  import JsonPatch.{PrimitiveOp, StringOp}

  private lazy val numberDeltaSchema: Schema[PrimitiveOp.NumberDelta] = new Schema(
    reflect = new Reflect.Record[Binding, PrimitiveOp.NumberDelta](
      fields = Vector(Schema[BigDecimal].reflect.asTerm("delta")),
      typeId = TypeId.of[PrimitiveOp.NumberDelta],
      recordBinding = new Binding.Record(
        constructor = new Constructor[PrimitiveOp.NumberDelta] {
          def usedRegisters: RegisterOffset                                             = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.NumberDelta =
            PrimitiveOp.NumberDelta(in.getObject(offset).asInstanceOf[BigDecimal])
        },
        deconstructor = new Deconstructor[PrimitiveOp.NumberDelta] {
          def usedRegisters: RegisterOffset                                                          = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.NumberDelta): Unit =
            out.setObject(offset, in.delta)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val stringEditSchema: Schema[PrimitiveOp.StringEdit] = new Schema(
    reflect = new Reflect.Record[Binding, PrimitiveOp.StringEdit](
      fields = Vector(Schema[Vector[StringOp]].reflect.asTerm("ops")),
      typeId = TypeId.of[PrimitiveOp.StringEdit],
      recordBinding = new Binding.Record(
        constructor = new Constructor[PrimitiveOp.StringEdit] {
          def usedRegisters: RegisterOffset                                            = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.StringEdit =
            PrimitiveOp.StringEdit(in.getObject(offset).asInstanceOf[Vector[StringOp]])
        },
        deconstructor = new Deconstructor[PrimitiveOp.StringEdit] {
          def usedRegisters: RegisterOffset                                                         = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.StringEdit): Unit =
            out.setObject(offset, in.ops)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val schema: Schema[PrimitiveOp] = new Schema(
    reflect = new Reflect.Variant[Binding, PrimitiveOp](
      cases = Vector(
        numberDeltaSchema.reflect.asTerm("NumberDelta"),
        stringEditSchema.reflect.asTerm("StringEdit")
      ),
      typeId = TypeId.of[PrimitiveOp],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[PrimitiveOp] {
          def discriminate(a: PrimitiveOp): Int = a match {
            case _: PrimitiveOp.NumberDelta => 0
            case _: PrimitiveOp.StringEdit  => 1
          }
        },
        matchers = Matchers(
          new Matcher[PrimitiveOp.NumberDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.NumberDelta = a match {
              case x: PrimitiveOp.NumberDelta => x
              case _                          => null.asInstanceOf[PrimitiveOp.NumberDelta]
            }
          },
          new Matcher[PrimitiveOp.StringEdit] {
            def downcastOrNull(a: Any): PrimitiveOp.StringEdit = a match {
              case x: PrimitiveOp.StringEdit => x
              case _                         => null.asInstanceOf[PrimitiveOp.StringEdit]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// ArrayOp schemas
// ─────────────────────────────────────────────────────────────────────────────

private[json] trait ArrayOpVersionSpecific {
  import JsonPatch.{ArrayOp, Op}

  private lazy val insertSchema: Schema[ArrayOp.Insert] = new Schema(
    reflect = new Reflect.Record[Binding, ArrayOp.Insert](
      fields = Vector(
        Schema[Int].reflect.asTerm("index"),
        Schema[Chunk[Json]].reflect.asTerm("values")
      ),
      typeId = TypeId.of[ArrayOp.Insert],
      recordBinding = new Binding.Record(
        constructor = new Constructor[ArrayOp.Insert] {
          def usedRegisters: RegisterOffset                                    = RegisterOffset(ints = 1, objects = 1)
          def construct(in: Registers, offset: RegisterOffset): ArrayOp.Insert =
            ArrayOp.Insert(in.getInt(offset), in.getObject(offset).asInstanceOf[Chunk[Json]])
        },
        deconstructor = new Deconstructor[ArrayOp.Insert] {
          def usedRegisters: RegisterOffset                                                 = RegisterOffset(ints = 1, objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: ArrayOp.Insert): Unit = {
            out.setInt(offset, in.index)
            out.setObject(offset, in.values)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val appendSchema: Schema[ArrayOp.Append] = new Schema(
    reflect = new Reflect.Record[Binding, ArrayOp.Append](
      fields = Vector(Schema[Chunk[Json]].reflect.asTerm("values")),
      typeId = TypeId.of[ArrayOp.Append],
      recordBinding = new Binding.Record(
        constructor = new Constructor[ArrayOp.Append] {
          def usedRegisters: RegisterOffset                                    = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): ArrayOp.Append =
            ArrayOp.Append(in.getObject(offset).asInstanceOf[Chunk[Json]])
        },
        deconstructor = new Deconstructor[ArrayOp.Append] {
          def usedRegisters: RegisterOffset                                                 = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: ArrayOp.Append): Unit =
            out.setObject(offset, in.values)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val deleteSchema: Schema[ArrayOp.Delete] = new Schema(
    reflect = new Reflect.Record[Binding, ArrayOp.Delete](
      fields = Vector(
        Schema[Int].reflect.asTerm("index"),
        Schema[Int].reflect.asTerm("count")
      ),
      typeId = TypeId.of[ArrayOp.Delete],
      recordBinding = new Binding.Record(
        constructor = new Constructor[ArrayOp.Delete] {
          def usedRegisters: RegisterOffset                                    = RegisterOffset(ints = 2)
          def construct(in: Registers, offset: RegisterOffset): ArrayOp.Delete =
            ArrayOp.Delete(in.getInt(offset), in.getInt(RegisterOffset.incrementFloatsAndInts(offset)))
        },
        deconstructor = new Deconstructor[ArrayOp.Delete] {
          def usedRegisters: RegisterOffset                                                 = RegisterOffset(ints = 2)
          def deconstruct(out: Registers, offset: RegisterOffset, in: ArrayOp.Delete): Unit = {
            out.setInt(offset, in.index)
            out.setInt(RegisterOffset.incrementFloatsAndInts(offset), in.count)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val modifySchema: Schema[ArrayOp.Modify] = new Schema(
    reflect = new Reflect.Record[Binding, ArrayOp.Modify](
      fields = Vector(
        Schema[Int].reflect.asTerm("index"),
        new Reflect.Deferred(() => Op.schema.reflect).asTerm("op")
      ),
      typeId = TypeId.of[ArrayOp.Modify],
      recordBinding = new Binding.Record(
        constructor = new Constructor[ArrayOp.Modify] {
          def usedRegisters: RegisterOffset                                    = RegisterOffset(ints = 1, objects = 1)
          def construct(in: Registers, offset: RegisterOffset): ArrayOp.Modify =
            ArrayOp.Modify(in.getInt(offset), in.getObject(offset).asInstanceOf[Op])
        },
        deconstructor = new Deconstructor[ArrayOp.Modify] {
          def usedRegisters: RegisterOffset                                                 = RegisterOffset(ints = 1, objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: ArrayOp.Modify): Unit = {
            out.setInt(offset, in.index)
            out.setObject(offset, in.op)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val schema: Schema[ArrayOp] = new Schema(
    reflect = new Reflect.Variant[Binding, ArrayOp](
      cases = Vector(
        insertSchema.reflect.asTerm("Insert"),
        appendSchema.reflect.asTerm("Append"),
        deleteSchema.reflect.asTerm("Delete"),
        modifySchema.reflect.asTerm("Modify")
      ),
      typeId = TypeId.of[ArrayOp],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[ArrayOp] {
          def discriminate(a: ArrayOp): Int = a match {
            case _: ArrayOp.Insert => 0
            case _: ArrayOp.Append => 1
            case _: ArrayOp.Delete => 2
            case _: ArrayOp.Modify => 3
          }
        },
        matchers = Matchers(
          new Matcher[ArrayOp.Insert] {
            def downcastOrNull(a: Any): ArrayOp.Insert = a match {
              case x: ArrayOp.Insert => x
              case _                 => null.asInstanceOf[ArrayOp.Insert]
            }
          },
          new Matcher[ArrayOp.Append] {
            def downcastOrNull(a: Any): ArrayOp.Append = a match {
              case x: ArrayOp.Append => x
              case _                 => null.asInstanceOf[ArrayOp.Append]
            }
          },
          new Matcher[ArrayOp.Delete] {
            def downcastOrNull(a: Any): ArrayOp.Delete = a match {
              case x: ArrayOp.Delete => x
              case _                 => null.asInstanceOf[ArrayOp.Delete]
            }
          },
          new Matcher[ArrayOp.Modify] {
            def downcastOrNull(a: Any): ArrayOp.Modify = a match {
              case x: ArrayOp.Modify => x
              case _                 => null.asInstanceOf[ArrayOp.Modify]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// ObjectOp schemas
// ─────────────────────────────────────────────────────────────────────────────

private[json] trait ObjectOpVersionSpecific {
  import JsonPatch.ObjectOp

  private lazy val addSchema: Schema[ObjectOp.Add] = new Schema(
    reflect = new Reflect.Record[Binding, ObjectOp.Add](
      fields = Vector(
        Schema[String].reflect.asTerm("key"),
        Json.schema.reflect.asTerm("value")
      ),
      typeId = TypeId.of[ObjectOp.Add],
      recordBinding = new Binding.Record(
        constructor = new Constructor[ObjectOp.Add] {
          def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 2)
          def construct(in: Registers, offset: RegisterOffset): ObjectOp.Add =
            ObjectOp.Add(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[Json]
            )
        },
        deconstructor = new Deconstructor[ObjectOp.Add] {
          def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 2)
          def deconstruct(out: Registers, offset: RegisterOffset, in: ObjectOp.Add): Unit = {
            out.setObject(offset, in.key)
            out.setObject(RegisterOffset.incrementObjects(offset), in.value)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val removeSchema: Schema[ObjectOp.Remove] = new Schema(
    reflect = new Reflect.Record[Binding, ObjectOp.Remove](
      fields = Vector(Schema[String].reflect.asTerm("key")),
      typeId = TypeId.of[ObjectOp.Remove],
      recordBinding = new Binding.Record(
        constructor = new Constructor[ObjectOp.Remove] {
          def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): ObjectOp.Remove =
            ObjectOp.Remove(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[ObjectOp.Remove] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: ObjectOp.Remove): Unit =
            out.setObject(offset, in.key)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val modifySchema: Schema[ObjectOp.Modify] = new Schema(
    reflect = new Reflect.Record[Binding, ObjectOp.Modify](
      fields = Vector(
        Schema[String].reflect.asTerm("key"),
        JsonPatch.schema.reflect.asTerm("patch")
      ),
      typeId = TypeId.of[ObjectOp.Modify],
      recordBinding = new Binding.Record(
        constructor = new Constructor[ObjectOp.Modify] {
          def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 2)
          def construct(in: Registers, offset: RegisterOffset): ObjectOp.Modify =
            ObjectOp.Modify(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[JsonPatch]
            )
        },
        deconstructor = new Deconstructor[ObjectOp.Modify] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 2)
          def deconstruct(out: Registers, offset: RegisterOffset, in: ObjectOp.Modify): Unit = {
            out.setObject(offset, in.key)
            out.setObject(RegisterOffset.incrementObjects(offset), in.patch)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val schema: Schema[ObjectOp] = new Schema(
    reflect = new Reflect.Variant[Binding, ObjectOp](
      cases = Vector(
        addSchema.reflect.asTerm("Add"),
        removeSchema.reflect.asTerm("Remove"),
        modifySchema.reflect.asTerm("Modify")
      ),
      typeId = TypeId.of[ObjectOp],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[ObjectOp] {
          def discriminate(a: ObjectOp): Int = a match {
            case _: ObjectOp.Add    => 0
            case _: ObjectOp.Remove => 1
            case _: ObjectOp.Modify => 2
          }
        },
        matchers = Matchers(
          new Matcher[ObjectOp.Add] {
            def downcastOrNull(a: Any): ObjectOp.Add = a match {
              case x: ObjectOp.Add => x
              case _               => null.asInstanceOf[ObjectOp.Add]
            }
          },
          new Matcher[ObjectOp.Remove] {
            def downcastOrNull(a: Any): ObjectOp.Remove = a match {
              case x: ObjectOp.Remove => x
              case _                  => null.asInstanceOf[ObjectOp.Remove]
            }
          },
          new Matcher[ObjectOp.Modify] {
            def downcastOrNull(a: Any): ObjectOp.Modify = a match {
              case x: ObjectOp.Modify => x
              case _                  => null.asInstanceOf[ObjectOp.Modify]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// Op schemas (top-level operation)
// ─────────────────────────────────────────────────────────────────────────────

private[json] trait OpVersionSpecific {
  import JsonPatch.{ArrayOp, ObjectOp, Op, PrimitiveOp}

  private lazy val setSchema: Schema[Op.Set] = new Schema(
    reflect = new Reflect.Record[Binding, Op.Set](
      fields = Vector(Json.schema.reflect.asTerm("value")),
      typeId = TypeId.of[Op.Set],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Op.Set] {
          def usedRegisters: RegisterOffset                            = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Op.Set =
            Op.Set(in.getObject(offset).asInstanceOf[Json])
        },
        deconstructor = new Deconstructor[Op.Set] {
          def usedRegisters: RegisterOffset                                         = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Op.Set): Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val primitiveDeltaSchema: Schema[Op.PrimitiveDelta] = new Schema(
    reflect = new Reflect.Record[Binding, Op.PrimitiveDelta](
      fields = Vector(PrimitiveOp.schema.reflect.asTerm("op")),
      typeId = TypeId.of[Op.PrimitiveDelta],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Op.PrimitiveDelta] {
          def usedRegisters: RegisterOffset                                       = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Op.PrimitiveDelta =
            Op.PrimitiveDelta(in.getObject(offset).asInstanceOf[PrimitiveOp])
        },
        deconstructor = new Deconstructor[Op.PrimitiveDelta] {
          def usedRegisters: RegisterOffset                                                    = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Op.PrimitiveDelta): Unit =
            out.setObject(offset, in.op)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val arrayEditSchema: Schema[Op.ArrayEdit] = new Schema(
    reflect = new Reflect.Record[Binding, Op.ArrayEdit](
      fields = Vector(Schema[Vector[ArrayOp]].reflect.asTerm("ops")),
      typeId = TypeId.of[Op.ArrayEdit],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Op.ArrayEdit] {
          def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Op.ArrayEdit =
            Op.ArrayEdit(in.getObject(offset).asInstanceOf[Vector[ArrayOp]])
        },
        deconstructor = new Deconstructor[Op.ArrayEdit] {
          def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Op.ArrayEdit): Unit =
            out.setObject(offset, in.ops)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val objectEditSchema: Schema[Op.ObjectEdit] = new Schema(
    reflect = new Reflect.Record[Binding, Op.ObjectEdit](
      fields = Vector(Schema[Vector[ObjectOp]].reflect.asTerm("ops")),
      typeId = TypeId.of[Op.ObjectEdit],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Op.ObjectEdit] {
          def usedRegisters: RegisterOffset                                   = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Op.ObjectEdit =
            Op.ObjectEdit(in.getObject(offset).asInstanceOf[Vector[ObjectOp]])
        },
        deconstructor = new Deconstructor[Op.ObjectEdit] {
          def usedRegisters: RegisterOffset                                                = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Op.ObjectEdit): Unit =
            out.setObject(offset, in.ops)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val nestedSchema: Schema[Op.Nested] = new Schema(
    reflect = new Reflect.Record[Binding, Op.Nested](
      fields = Vector(JsonPatch.schema.reflect.asTerm("patch")),
      typeId = TypeId.of[Op.Nested],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Op.Nested] {
          def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Op.Nested =
            Op.Nested(in.getObject(offset).asInstanceOf[JsonPatch])
        },
        deconstructor = new Deconstructor[Op.Nested] {
          def usedRegisters: RegisterOffset                                            = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Op.Nested): Unit =
            out.setObject(offset, in.patch)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val schema: Schema[Op] = new Schema(
    reflect = new Reflect.Variant[Binding, Op](
      cases = Vector(
        setSchema.reflect.asTerm("Set"),
        primitiveDeltaSchema.reflect.asTerm("PrimitiveDelta"),
        arrayEditSchema.reflect.asTerm("ArrayEdit"),
        objectEditSchema.reflect.asTerm("ObjectEdit"),
        nestedSchema.reflect.asTerm("Nested")
      ),
      typeId = TypeId.of[Op],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Op] {
          def discriminate(a: Op): Int = a match {
            case _: Op.Set            => 0
            case _: Op.PrimitiveDelta => 1
            case _: Op.ArrayEdit      => 2
            case _: Op.ObjectEdit     => 3
            case _: Op.Nested         => 4
          }
        },
        matchers = Matchers(
          new Matcher[Op.Set] {
            def downcastOrNull(a: Any): Op.Set = a match {
              case x: Op.Set => x
              case _         => null.asInstanceOf[Op.Set]
            }
          },
          new Matcher[Op.PrimitiveDelta] {
            def downcastOrNull(a: Any): Op.PrimitiveDelta = a match {
              case x: Op.PrimitiveDelta => x
              case _                    => null.asInstanceOf[Op.PrimitiveDelta]
            }
          },
          new Matcher[Op.ArrayEdit] {
            def downcastOrNull(a: Any): Op.ArrayEdit = a match {
              case x: Op.ArrayEdit => x
              case _               => null.asInstanceOf[Op.ArrayEdit]
            }
          },
          new Matcher[Op.ObjectEdit] {
            def downcastOrNull(a: Any): Op.ObjectEdit = a match {
              case x: Op.ObjectEdit => x
              case _                => null.asInstanceOf[Op.ObjectEdit]
            }
          },
          new Matcher[Op.Nested] {
            def downcastOrNull(a: Any): Op.Nested = a match {
              case x: Op.Nested => x
              case _            => null.asInstanceOf[Op.Nested]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}
