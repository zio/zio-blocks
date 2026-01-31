package zio.blocks.schema.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

// In Scala 2, we are manually deriving the Schema.
// In Scala 3, these are derived using Schema.derived.

trait DynamicPatchCompanionVersionSpecific {

  import DynamicPatch._

  implicit lazy val stringOpInsertSchema: Schema[StringOp.Insert] = {
    import zio.blocks.schema.binding.RegisterOffset

    new Schema(
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
  }

  implicit lazy val stringOpDeleteSchema: Schema[StringOp.Delete] = {
    import zio.blocks.schema.binding.RegisterOffset

    new Schema(
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
              StringOp.Delete(
                in.getInt(offset),
                in.getInt(RegisterOffset.incrementFloatsAndInts(offset))
              )
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
  }

  implicit lazy val stringOpAppendSchema: Schema[StringOp.Append] = {
    import zio.blocks.schema.binding.RegisterOffset

    new Schema(
      reflect = new Reflect.Record[Binding, StringOp.Append](
        fields = Vector(
          Schema[String].reflect.asTerm("text")
        ),
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
  }

  implicit lazy val stringOpModifySchema: Schema[StringOp.Modify] = {
    import zio.blocks.schema.binding.RegisterOffset

    new Schema(
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
  }

  implicit lazy val stringOpSchema: Schema[StringOp] = new Schema(
    reflect = new Reflect.Variant[Binding, StringOp](
      cases = Vector(
        stringOpInsertSchema.reflect.asTerm("Insert"),
        stringOpDeleteSchema.reflect.asTerm("Delete"),
        stringOpAppendSchema.reflect.asTerm("Append"),
        stringOpModifySchema.reflect.asTerm("Modify")
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
  implicit lazy val primitiveOpIntDeltaSchema: Schema[PrimitiveOp.IntDelta] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.IntDelta](
        fields = Vector(Schema[Int].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.IntDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.IntDelta] {
            def usedRegisters: RegisterOffset                                          = RegisterOffset(ints = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.IntDelta =
              PrimitiveOp.IntDelta(in.getInt(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.IntDelta] {
            def usedRegisters: RegisterOffset                                                       = RegisterOffset(ints = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.IntDelta): Unit =
              out.setInt(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val primitiveOpLongDeltaSchema: Schema[PrimitiveOp.LongDelta] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.LongDelta](
        fields = Vector(Schema[Long].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.LongDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.LongDelta] {
            def usedRegisters: RegisterOffset                                           = RegisterOffset(longs = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.LongDelta =
              PrimitiveOp.LongDelta(in.getLong(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.LongDelta] {
            def usedRegisters: RegisterOffset                                                        = RegisterOffset(longs = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.LongDelta): Unit =
              out.setLong(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val primitiveOpDoubleDeltaSchema: Schema[PrimitiveOp.DoubleDelta] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.DoubleDelta](
        fields = Vector(Schema[Double].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.DoubleDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.DoubleDelta] {
            def usedRegisters: RegisterOffset                                             = RegisterOffset(doubles = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.DoubleDelta =
              PrimitiveOp.DoubleDelta(in.getDouble(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.DoubleDelta] {
            def usedRegisters: RegisterOffset                                                          = RegisterOffset(doubles = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.DoubleDelta): Unit =
              out.setDouble(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val primitiveOpFloatDeltaSchema: Schema[PrimitiveOp.FloatDelta] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.FloatDelta](
        fields = Vector(Schema[Float].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.FloatDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.FloatDelta] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(floats = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.FloatDelta =
              PrimitiveOp.FloatDelta(in.getFloat(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.FloatDelta] {
            def usedRegisters: RegisterOffset                                                         = RegisterOffset(floats = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.FloatDelta): Unit =
              out.setFloat(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val primitiveOpShortDeltaSchema: Schema[PrimitiveOp.ShortDelta] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.ShortDelta](
        fields = Vector(Schema[Short].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.ShortDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.ShortDelta] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(shorts = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.ShortDelta =
              PrimitiveOp.ShortDelta(in.getShort(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.ShortDelta] {
            def usedRegisters: RegisterOffset                                                         = RegisterOffset(shorts = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.ShortDelta): Unit =
              out.setShort(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val primitiveOpByteDeltaSchema: Schema[PrimitiveOp.ByteDelta] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.ByteDelta](
        fields = Vector(Schema[Byte].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.ByteDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.ByteDelta] {
            def usedRegisters: RegisterOffset                                           = RegisterOffset(bytes = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.ByteDelta =
              PrimitiveOp.ByteDelta(in.getByte(offset))
          },
          deconstructor = new Deconstructor[PrimitiveOp.ByteDelta] {
            def usedRegisters: RegisterOffset                                                        = RegisterOffset(bytes = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.ByteDelta): Unit =
              out.setByte(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val primitiveOpBigIntDeltaSchema: Schema[PrimitiveOp.BigIntDelta] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.BigIntDelta](
        fields = Vector(Schema[BigInt].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.BigIntDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.BigIntDelta] {
            def usedRegisters: RegisterOffset                                             = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.BigIntDelta =
              PrimitiveOp.BigIntDelta(in.getObject(offset).asInstanceOf[BigInt])
          },
          deconstructor = new Deconstructor[PrimitiveOp.BigIntDelta] {
            def usedRegisters: RegisterOffset                                                          = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.BigIntDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val primitiveOpBigDecimalDeltaSchema: Schema[PrimitiveOp.BigDecimalDelta] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.BigDecimalDelta](
        fields = Vector(Schema[BigDecimal].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.BigDecimalDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.BigDecimalDelta] {
            def usedRegisters: RegisterOffset                                                 = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.BigDecimalDelta =
              PrimitiveOp.BigDecimalDelta(in.getObject(offset).asInstanceOf[BigDecimal])
          },
          deconstructor = new Deconstructor[PrimitiveOp.BigDecimalDelta] {
            def usedRegisters: RegisterOffset                                                              = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.BigDecimalDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val primitiveOpStringEditSchema: Schema[PrimitiveOp.StringEdit] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
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
  }

  implicit lazy val primitiveOpInstantDeltaSchema: Schema[PrimitiveOp.InstantDelta] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.InstantDelta](
        fields = Vector(Schema[java.time.Duration].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.InstantDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.InstantDelta] {
            def usedRegisters: RegisterOffset                                              = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.InstantDelta =
              PrimitiveOp.InstantDelta(in.getObject(offset).asInstanceOf[java.time.Duration])
          },
          deconstructor = new Deconstructor[PrimitiveOp.InstantDelta] {
            def usedRegisters: RegisterOffset                                                           = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.InstantDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val primitiveOpDurationDeltaSchema: Schema[PrimitiveOp.DurationDelta] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.DurationDelta](
        fields = Vector(Schema[java.time.Duration].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.DurationDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.DurationDelta] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.DurationDelta =
              PrimitiveOp.DurationDelta(in.getObject(offset).asInstanceOf[java.time.Duration])
          },
          deconstructor = new Deconstructor[PrimitiveOp.DurationDelta] {
            def usedRegisters: RegisterOffset                                                            = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.DurationDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val primitiveOpLocalDateDeltaSchema: Schema[PrimitiveOp.LocalDateDelta] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.LocalDateDelta](
        fields = Vector(Schema[java.time.Period].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.LocalDateDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.LocalDateDelta] {
            def usedRegisters: RegisterOffset                                                = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.LocalDateDelta =
              PrimitiveOp.LocalDateDelta(in.getObject(offset).asInstanceOf[java.time.Period])
          },
          deconstructor = new Deconstructor[PrimitiveOp.LocalDateDelta] {
            def usedRegisters: RegisterOffset                                                             = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.LocalDateDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val primitiveOpLocalDateTimeDeltaSchema: Schema[PrimitiveOp.LocalDateTimeDelta] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.LocalDateTimeDelta](
        fields = Vector(
          Schema[java.time.Period].reflect.asTerm("periodDelta"),
          Schema[java.time.Duration].reflect.asTerm("durationDelta")
        ),
        typeId = TypeId.of[PrimitiveOp.LocalDateTimeDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.LocalDateTimeDelta] {
            def usedRegisters: RegisterOffset                                                    = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.LocalDateTimeDelta =
              PrimitiveOp.LocalDateTimeDelta(
                in.getObject(offset).asInstanceOf[java.time.Period],
                in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[java.time.Duration]
              )
          },
          deconstructor = new Deconstructor[PrimitiveOp.LocalDateTimeDelta] {
            def usedRegisters: RegisterOffset                                                                 = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.LocalDateTimeDelta): Unit = {
              out.setObject(offset, in.periodDelta)
              out.setObject(RegisterOffset.incrementObjects(offset), in.durationDelta)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val primitiveOpPeriodDeltaSchema: Schema[PrimitiveOp.PeriodDelta] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, PrimitiveOp.PeriodDelta](
        fields = Vector(Schema[java.time.Period].reflect.asTerm("delta")),
        typeId = TypeId.of[PrimitiveOp.PeriodDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[PrimitiveOp.PeriodDelta] {
            def usedRegisters: RegisterOffset                                             = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): PrimitiveOp.PeriodDelta =
              PrimitiveOp.PeriodDelta(in.getObject(offset).asInstanceOf[java.time.Period])
          },
          deconstructor = new Deconstructor[PrimitiveOp.PeriodDelta] {
            def usedRegisters: RegisterOffset                                                          = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: PrimitiveOp.PeriodDelta): Unit =
              out.setObject(offset, in.delta)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val primitiveOpSchema: Schema[PrimitiveOp] = new Schema(
    reflect = new Reflect.Variant[Binding, PrimitiveOp](
      cases = Vector(
        primitiveOpIntDeltaSchema.reflect.asTerm("IntDelta"),
        primitiveOpLongDeltaSchema.reflect.asTerm("LongDelta"),
        primitiveOpDoubleDeltaSchema.reflect.asTerm("DoubleDelta"),
        primitiveOpFloatDeltaSchema.reflect.asTerm("FloatDelta"),
        primitiveOpShortDeltaSchema.reflect.asTerm("ShortDelta"),
        primitiveOpByteDeltaSchema.reflect.asTerm("ByteDelta"),
        primitiveOpBigIntDeltaSchema.reflect.asTerm("BigIntDelta"),
        primitiveOpBigDecimalDeltaSchema.reflect.asTerm("BigDecimalDelta"),
        primitiveOpStringEditSchema.reflect.asTerm("StringEdit"),
        primitiveOpInstantDeltaSchema.reflect.asTerm("InstantDelta"),
        primitiveOpDurationDeltaSchema.reflect.asTerm("DurationDelta"),
        primitiveOpLocalDateDeltaSchema.reflect.asTerm("LocalDateDelta"),
        primitiveOpLocalDateTimeDeltaSchema.reflect.asTerm("LocalDateTimeDelta"),
        primitiveOpPeriodDeltaSchema.reflect.asTerm("PeriodDelta")
      ),
      typeId = TypeId.of[PrimitiveOp],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[PrimitiveOp] {
          def discriminate(a: PrimitiveOp): Int = a match {
            case _: PrimitiveOp.IntDelta           => 0
            case _: PrimitiveOp.LongDelta          => 1
            case _: PrimitiveOp.DoubleDelta        => 2
            case _: PrimitiveOp.FloatDelta         => 3
            case _: PrimitiveOp.ShortDelta         => 4
            case _: PrimitiveOp.ByteDelta          => 5
            case _: PrimitiveOp.BigIntDelta        => 6
            case _: PrimitiveOp.BigDecimalDelta    => 7
            case _: PrimitiveOp.StringEdit         => 8
            case _: PrimitiveOp.InstantDelta       => 9
            case _: PrimitiveOp.DurationDelta      => 10
            case _: PrimitiveOp.LocalDateDelta     => 11
            case _: PrimitiveOp.LocalDateTimeDelta => 12
            case _: PrimitiveOp.PeriodDelta        => 13
          }
        },
        matchers = Matchers(
          new Matcher[PrimitiveOp.IntDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.IntDelta = a match {
              case x: PrimitiveOp.IntDelta => x
              case _                       => null.asInstanceOf[PrimitiveOp.IntDelta]
            }
          },
          new Matcher[PrimitiveOp.LongDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.LongDelta = a match {
              case x: PrimitiveOp.LongDelta => x
              case _                        => null.asInstanceOf[PrimitiveOp.LongDelta]
            }
          },
          new Matcher[PrimitiveOp.DoubleDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.DoubleDelta = a match {
              case x: PrimitiveOp.DoubleDelta => x
              case _                          => null.asInstanceOf[PrimitiveOp.DoubleDelta]
            }
          },
          new Matcher[PrimitiveOp.FloatDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.FloatDelta = a match {
              case x: PrimitiveOp.FloatDelta => x
              case _                         => null.asInstanceOf[PrimitiveOp.FloatDelta]
            }
          },
          new Matcher[PrimitiveOp.ShortDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.ShortDelta = a match {
              case x: PrimitiveOp.ShortDelta => x
              case _                         => null.asInstanceOf[PrimitiveOp.ShortDelta]
            }
          },
          new Matcher[PrimitiveOp.ByteDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.ByteDelta = a match {
              case x: PrimitiveOp.ByteDelta => x
              case _                        => null.asInstanceOf[PrimitiveOp.ByteDelta]
            }
          },
          new Matcher[PrimitiveOp.BigIntDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.BigIntDelta = a match {
              case x: PrimitiveOp.BigIntDelta => x
              case _                          => null.asInstanceOf[PrimitiveOp.BigIntDelta]
            }
          },
          new Matcher[PrimitiveOp.BigDecimalDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.BigDecimalDelta = a match {
              case x: PrimitiveOp.BigDecimalDelta => x
              case _                              => null.asInstanceOf[PrimitiveOp.BigDecimalDelta]
            }
          },
          new Matcher[PrimitiveOp.StringEdit] {
            def downcastOrNull(a: Any): PrimitiveOp.StringEdit = a match {
              case x: PrimitiveOp.StringEdit => x
              case _                         => null.asInstanceOf[PrimitiveOp.StringEdit]
            }
          },
          new Matcher[PrimitiveOp.InstantDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.InstantDelta = a match {
              case x: PrimitiveOp.InstantDelta => x
              case _                           => null.asInstanceOf[PrimitiveOp.InstantDelta]
            }
          },
          new Matcher[PrimitiveOp.DurationDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.DurationDelta = a match {
              case x: PrimitiveOp.DurationDelta => x
              case _                            => null.asInstanceOf[PrimitiveOp.DurationDelta]
            }
          },
          new Matcher[PrimitiveOp.LocalDateDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.LocalDateDelta = a match {
              case x: PrimitiveOp.LocalDateDelta => x
              case _                             => null.asInstanceOf[PrimitiveOp.LocalDateDelta]
            }
          },
          new Matcher[PrimitiveOp.LocalDateTimeDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.LocalDateTimeDelta = a match {
              case x: PrimitiveOp.LocalDateTimeDelta => x
              case _                                 => null.asInstanceOf[PrimitiveOp.LocalDateTimeDelta]
            }
          },
          new Matcher[PrimitiveOp.PeriodDelta] {
            def downcastOrNull(a: Any): PrimitiveOp.PeriodDelta = a match {
              case x: PrimitiveOp.PeriodDelta => x
              case _                          => null.asInstanceOf[PrimitiveOp.PeriodDelta]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val seqOpInsertSchema: Schema[SeqOp.Insert] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, SeqOp.Insert](
        fields = Vector(
          Schema[Int].reflect.asTerm("index"),
          Schema[Chunk[DynamicValue]].reflect.asTerm("values")
        ),
        typeId = TypeId.of[SeqOp.Insert],
        recordBinding = new Binding.Record(
          constructor = new Constructor[SeqOp.Insert] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(ints = 1, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): SeqOp.Insert =
              SeqOp.Insert(in.getInt(offset), in.getObject(offset).asInstanceOf[Chunk[DynamicValue]])
          },
          deconstructor = new Deconstructor[SeqOp.Insert] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(ints = 1, objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: SeqOp.Insert): Unit = {
              out.setInt(offset, in.index)
              out.setObject(offset, in.values)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val seqOpAppendSchema: Schema[SeqOp.Append] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, SeqOp.Append](
        fields = Vector(Schema[Chunk[DynamicValue]].reflect.asTerm("values")),
        typeId = TypeId.of[SeqOp.Append],
        recordBinding = new Binding.Record(
          constructor = new Constructor[SeqOp.Append] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): SeqOp.Append =
              SeqOp.Append(in.getObject(offset).asInstanceOf[Chunk[DynamicValue]])
          },
          deconstructor = new Deconstructor[SeqOp.Append] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: SeqOp.Append): Unit =
              out.setObject(offset, in.values)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val seqOpDeleteSchema: Schema[SeqOp.Delete] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, SeqOp.Delete](
        fields = Vector(
          Schema[Int].reflect.asTerm("index"),
          Schema[Int].reflect.asTerm("count")
        ),
        typeId = TypeId.of[SeqOp.Delete],
        recordBinding = new Binding.Record(
          constructor = new Constructor[SeqOp.Delete] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(ints = 2)
            def construct(in: Registers, offset: RegisterOffset): SeqOp.Delete =
              SeqOp.Delete(in.getInt(offset), in.getInt(RegisterOffset.incrementFloatsAndInts(offset)))
          },
          deconstructor = new Deconstructor[SeqOp.Delete] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(ints = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: SeqOp.Delete): Unit = {
              out.setInt(offset, in.index)
              out.setInt(RegisterOffset.incrementFloatsAndInts(offset), in.count)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val seqOpModifySchema: Schema[SeqOp.Modify] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, SeqOp.Modify](
        fields = Vector(
          Schema[Int].reflect.asTerm("index"),
          Reflect.Deferred(() => operationSchema.reflect).asTerm("op")
        ),
        typeId = TypeId.of[SeqOp.Modify],
        recordBinding = new Binding.Record(
          constructor = new Constructor[SeqOp.Modify] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(ints = 1, objects = 1)
            def construct(in: Registers, offset: RegisterOffset): SeqOp.Modify =
              SeqOp.Modify(in.getInt(offset), in.getObject(offset).asInstanceOf[Operation])
          },
          deconstructor = new Deconstructor[SeqOp.Modify] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(ints = 1, objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: SeqOp.Modify): Unit = {
              out.setInt(offset, in.index)
              out.setObject(offset, in.op)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val seqOpSchema: Schema[SeqOp] = new Schema(
    reflect = new Reflect.Variant[Binding, SeqOp](
      cases = Vector(
        seqOpInsertSchema.reflect.asTerm("Insert"),
        seqOpAppendSchema.reflect.asTerm("Append"),
        seqOpDeleteSchema.reflect.asTerm("Delete"),
        Reflect.Deferred(() => seqOpModifySchema.reflect).asTerm("Modify")
      ),
      typeId = TypeId.of[SeqOp],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[SeqOp] {
          def discriminate(a: SeqOp): Int = a match {
            case _: SeqOp.Insert => 0
            case _: SeqOp.Append => 1
            case _: SeqOp.Delete => 2
            case _: SeqOp.Modify => 3
          }
        },
        matchers = Matchers(
          new Matcher[SeqOp.Insert] {
            def downcastOrNull(a: Any): SeqOp.Insert = a match {
              case x: SeqOp.Insert => x
              case _               => null.asInstanceOf[SeqOp.Insert]
            }
          },
          new Matcher[SeqOp.Append] {
            def downcastOrNull(a: Any): SeqOp.Append = a match {
              case x: SeqOp.Append => x
              case _               => null.asInstanceOf[SeqOp.Append]
            }
          },
          new Matcher[SeqOp.Delete] {
            def downcastOrNull(a: Any): SeqOp.Delete = a match {
              case x: SeqOp.Delete => x
              case _               => null.asInstanceOf[SeqOp.Delete]
            }
          },
          new Matcher[SeqOp.Modify] {
            def downcastOrNull(a: Any): SeqOp.Modify = a match {
              case x: SeqOp.Modify => x
              case _               => null.asInstanceOf[SeqOp.Modify]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val mapOpAddSchema: Schema[MapOp.Add] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, MapOp.Add](
        fields = Vector(
          Schema[DynamicValue].reflect.asTerm("key"),
          Schema[DynamicValue].reflect.asTerm("value")
        ),
        typeId = TypeId.of[MapOp.Add],
        recordBinding = new Binding.Record(
          constructor = new Constructor[MapOp.Add] {
            def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): MapOp.Add =
              MapOp.Add(
                in.getObject(offset).asInstanceOf[DynamicValue],
                in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[DynamicValue]
              )
          },
          deconstructor = new Deconstructor[MapOp.Add] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: MapOp.Add): Unit = {
              out.setObject(offset, in.key)
              out.setObject(RegisterOffset.incrementObjects(offset), in.value)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val mapOpRemoveSchema: Schema[MapOp.Remove] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, MapOp.Remove](
        fields = Vector(Schema[DynamicValue].reflect.asTerm("key")),
        typeId = TypeId.of[MapOp.Remove],
        recordBinding = new Binding.Record(
          constructor = new Constructor[MapOp.Remove] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): MapOp.Remove =
              MapOp.Remove(in.getObject(offset).asInstanceOf[DynamicValue])
          },
          deconstructor = new Deconstructor[MapOp.Remove] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: MapOp.Remove): Unit =
              out.setObject(offset, in.key)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val mapOpModifySchema: Schema[MapOp.Modify] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, MapOp.Modify](
        fields = Vector(
          Schema[DynamicValue].reflect.asTerm("key"),
          Reflect.Deferred(() => dynamicPatchSchema.reflect).asTerm("patch")
        ),
        typeId = TypeId.of[MapOp.Modify],
        recordBinding = new Binding.Record(
          constructor = new Constructor[MapOp.Modify] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): MapOp.Modify =
              MapOp.Modify(
                in.getObject(offset).asInstanceOf[DynamicValue],
                in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[DynamicPatch]
              )
          },
          deconstructor = new Deconstructor[MapOp.Modify] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: MapOp.Modify): Unit = {
              out.setObject(offset, in.key)
              out.setObject(RegisterOffset.incrementObjects(offset), in.patch)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val mapOpSchema: Schema[MapOp] = new Schema(
    reflect = new Reflect.Variant[Binding, MapOp](
      cases = Vector(
        mapOpAddSchema.reflect.asTerm("Add"),
        mapOpRemoveSchema.reflect.asTerm("Remove"),
        Reflect.Deferred(() => mapOpModifySchema.reflect).asTerm("Modify")
      ),
      typeId = TypeId.of[MapOp],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MapOp] {
          def discriminate(a: MapOp): Int = a match {
            case _: MapOp.Add    => 0
            case _: MapOp.Remove => 1
            case _: MapOp.Modify => 2
          }
        },
        matchers = Matchers(
          new Matcher[MapOp.Add] {
            def downcastOrNull(a: Any): MapOp.Add = a match {
              case x: MapOp.Add => x
              case _            => null.asInstanceOf[MapOp.Add]
            }
          },
          new Matcher[MapOp.Remove] {
            def downcastOrNull(a: Any): MapOp.Remove = a match {
              case x: MapOp.Remove => x
              case _               => null.asInstanceOf[MapOp.Remove]
            }
          },
          new Matcher[MapOp.Modify] {
            def downcastOrNull(a: Any): MapOp.Modify = a match {
              case x: MapOp.Modify => x
              case _               => null.asInstanceOf[MapOp.Modify]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val operationSetSchema: Schema[Operation.Set] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, Operation.Set](
        fields = Vector(Schema[DynamicValue].reflect.asTerm("value")),
        typeId = TypeId.of[Operation.Set],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Operation.Set] {
            def usedRegisters: RegisterOffset                                   = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Operation.Set =
              Operation.Set(in.getObject(offset).asInstanceOf[DynamicValue])
          },
          deconstructor = new Deconstructor[Operation.Set] {
            def usedRegisters: RegisterOffset                                                = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Operation.Set): Unit =
              out.setObject(offset, in.value)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val operationPrimitiveDeltaSchema: Schema[Operation.PrimitiveDelta] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, Operation.PrimitiveDelta](
        fields = Vector(primitiveOpSchema.reflect.asTerm("op")),
        typeId = TypeId.of[Operation.PrimitiveDelta],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Operation.PrimitiveDelta] {
            def usedRegisters: RegisterOffset                                              = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Operation.PrimitiveDelta =
              Operation.PrimitiveDelta(in.getObject(offset).asInstanceOf[PrimitiveOp])
          },
          deconstructor = new Deconstructor[Operation.PrimitiveDelta] {
            def usedRegisters: RegisterOffset                                                           = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Operation.PrimitiveDelta): Unit =
              out.setObject(offset, in.op)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val operationSequenceEditSchema: Schema[Operation.SequenceEdit] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, Operation.SequenceEdit](
        fields = Vector(Reflect.Deferred(() => Schema[Vector[SeqOp]].reflect).asTerm("ops")),
        typeId = TypeId.of[Operation.SequenceEdit],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Operation.SequenceEdit] {
            def usedRegisters: RegisterOffset                                            = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Operation.SequenceEdit =
              Operation.SequenceEdit(in.getObject(offset).asInstanceOf[Vector[SeqOp]])
          },
          deconstructor = new Deconstructor[Operation.SequenceEdit] {
            def usedRegisters: RegisterOffset                                                         = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Operation.SequenceEdit): Unit =
              out.setObject(offset, in.ops)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val operationMapEditSchema: Schema[Operation.MapEdit] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, Operation.MapEdit](
        fields = Vector(Reflect.Deferred(() => Schema[Vector[MapOp]].reflect).asTerm("ops")),
        typeId = TypeId.of[Operation.MapEdit],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Operation.MapEdit] {
            def usedRegisters: RegisterOffset                                       = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Operation.MapEdit =
              Operation.MapEdit(in.getObject(offset).asInstanceOf[Vector[MapOp]])
          },
          deconstructor = new Deconstructor[Operation.MapEdit] {
            def usedRegisters: RegisterOffset                                                    = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Operation.MapEdit): Unit =
              out.setObject(offset, in.ops)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val operationPatchSchema: Schema[Operation.Patch] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, Operation.Patch](
        fields = Vector(Reflect.Deferred(() => dynamicPatchSchema.reflect).asTerm("patch")),
        typeId = TypeId.of[Operation.Patch],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Operation.Patch] {
            def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Operation.Patch =
              Operation.Patch(in.getObject(offset).asInstanceOf[DynamicPatch])
          },
          deconstructor = new Deconstructor[Operation.Patch] {
            def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Operation.Patch): Unit =
              out.setObject(offset, in.patch)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val operationSchema: Schema[Operation] = new Schema(
    reflect = new Reflect.Variant[Binding, Operation](
      cases = Vector(
        operationSetSchema.reflect.asTerm("Set"),
        operationPrimitiveDeltaSchema.reflect.asTerm("PrimitiveDelta"),
        operationSequenceEditSchema.reflect.asTerm("SequenceEdit"),
        operationMapEditSchema.reflect.asTerm("MapEdit"),
        Reflect.Deferred(() => operationPatchSchema.reflect).asTerm("Patch")
      ),
      typeId = TypeId.of[Operation],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Operation] {
          def discriminate(a: Operation): Int = a match {
            case _: Operation.Set            => 0
            case _: Operation.PrimitiveDelta => 1
            case _: Operation.SequenceEdit   => 2
            case _: Operation.MapEdit        => 3
            case _: Operation.Patch          => 4
          }
        },
        matchers = Matchers(
          new Matcher[Operation.Set] {
            def downcastOrNull(a: Any): Operation.Set = a match {
              case x: Operation.Set => x
              case _                => null.asInstanceOf[Operation.Set]
            }
          },
          new Matcher[Operation.PrimitiveDelta] {
            def downcastOrNull(a: Any): Operation.PrimitiveDelta = a match {
              case x: Operation.PrimitiveDelta => x
              case _                           => null.asInstanceOf[Operation.PrimitiveDelta]
            }
          },
          new Matcher[Operation.SequenceEdit] {
            def downcastOrNull(a: Any): Operation.SequenceEdit = a match {
              case x: Operation.SequenceEdit => x
              case _                         => null.asInstanceOf[Operation.SequenceEdit]
            }
          },
          new Matcher[Operation.MapEdit] {
            def downcastOrNull(a: Any): Operation.MapEdit = a match {
              case x: Operation.MapEdit => x
              case _                    => null.asInstanceOf[Operation.MapEdit]
            }
          },
          new Matcher[Operation.Patch] {
            def downcastOrNull(a: Any): Operation.Patch = a match {
              case x: Operation.Patch => x
              case _                  => null.asInstanceOf[Operation.Patch]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val dynamicPatchOpSchema: Schema[DynamicPatchOp] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, DynamicPatchOp](
        fields = Vector(
          Schema[DynamicOptic].reflect.asTerm("path"),
          Reflect.Deferred(() => operationSchema.reflect).asTerm("operation")
        ),
        typeId = TypeId.of[DynamicPatchOp],
        recordBinding = new Binding.Record(
          constructor = new Constructor[DynamicPatchOp] {
            def usedRegisters: RegisterOffset                                    = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): DynamicPatchOp =
              DynamicPatchOp(
                in.getObject(offset).asInstanceOf[DynamicOptic],
                in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[Operation]
              )
          },
          deconstructor = new Deconstructor[DynamicPatchOp] {
            def usedRegisters: RegisterOffset                                                 = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicPatchOp): Unit = {
              out.setObject(offset, in.path)
              out.setObject(RegisterOffset.incrementObjects(offset), in.operation)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )
  }

  implicit lazy val dynamicPatchSchema: Schema[DynamicPatch] = {
    import zio.blocks.schema.binding.RegisterOffset
    new Schema(
      reflect = new Reflect.Record[Binding, DynamicPatch](
        fields = Vector(Reflect.Deferred(() => Schema[Vector[DynamicPatchOp]].reflect).asTerm("ops")),
        typeId = TypeId.of[DynamicPatch],
        recordBinding = new Binding.Record(
          constructor = new Constructor[DynamicPatch] {
            def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): DynamicPatch =
              DynamicPatch(in.getObject(offset).asInstanceOf[Vector[DynamicPatchOp]])
          },
          deconstructor = new Deconstructor[DynamicPatch] {
            def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicPatch): Unit =
              out.setObject(offset, in.ops)
          }
        ),
        modifiers = Vector.empty
      )
    )
  }
}
