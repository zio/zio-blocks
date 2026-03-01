package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

/**
 * Schema instances for migration types enabling full serialization.
 *
 * This file provides Schema instances for:
 *   - [[Resolved]] and all its variants (expressions)
 *   - [[MigrationAction]] and all its variants (actions)
 *   - [[DynamicMigration]] (the serializable migration container)
 *
 * With these schemas, migrations can be:
 *   - Serialized to JSON, Protobuf, or any format with a codec
 *   - Stored in schema registries or databases
 *   - Transmitted over the network
 *   - Reconstructed without reflection or code generation
 *
 * Manual derivation is used for Scala 2 compatibility. In Scala 3, these could
 * be derived using `Schema.derived`.
 */
object MigrationSchemas {

  // ═══════════════════════════════════════════════════════════════════════════
  // Resolved Expression Schemas
  // ═══════════════════════════════════════════════════════════════════════════

  private lazy val literalSchema: Schema[Resolved.Literal] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.Literal](
      fields = Vector(
        Schema[DynamicValue].reflect.asTerm("value")
      ),
      typeId = TypeId.of[Resolved.Literal],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.Literal] {
          def usedRegisters: RegisterOffset                                      = 1
          def construct(in: Registers, offset: RegisterOffset): Resolved.Literal =
            Resolved.Literal(in.getObject(offset + 0).asInstanceOf[DynamicValue])
        },
        deconstructor = new Deconstructor[Resolved.Literal] {
          def usedRegisters: RegisterOffset                                                   = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.Literal): Unit =
            out.setObject(offset + 0, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val identitySchema: Schema[Resolved.Identity.type] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.Identity.type](
      fields = Vector.empty,
      typeId = TypeId.of[Resolved.Identity.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Resolved.Identity.type](Resolved.Identity),
        deconstructor = new ConstantDeconstructor[Resolved.Identity.type]
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val fieldAccessSchema: Schema[Resolved.FieldAccess] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.FieldAccess](
      fields = Vector(
        Schema[String].reflect.asTerm("fieldName"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("inner")
      ),
      typeId = TypeId.of[Resolved.FieldAccess],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.FieldAccess] {
          def usedRegisters: RegisterOffset                                          = 2
          def construct(in: Registers, offset: RegisterOffset): Resolved.FieldAccess =
            Resolved.FieldAccess(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[Resolved.FieldAccess] {
          def usedRegisters: RegisterOffset                                                       = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.FieldAccess): Unit = {
            out.setObject(offset + 0, in.fieldName)
            out.setObject(offset + 1, in.inner)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val opticAccessSchema: Schema[Resolved.OpticAccess] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.OpticAccess](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("path"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("inner")
      ),
      typeId = TypeId.of[Resolved.OpticAccess],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.OpticAccess] {
          def usedRegisters: RegisterOffset                                          = 2
          def construct(in: Registers, offset: RegisterOffset): Resolved.OpticAccess =
            Resolved.OpticAccess(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[Resolved.OpticAccess] {
          def usedRegisters: RegisterOffset                                                       = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.OpticAccess): Unit = {
            out.setObject(offset + 0, in.path)
            out.setObject(offset + 1, in.inner)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val defaultValueSchema: Schema[Resolved.DefaultValue] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.DefaultValue](
      fields = Vector(
        Schema[Either[String, DynamicValue]].reflect.asTerm("defaultDynamic")
      ),
      typeId = TypeId.of[Resolved.DefaultValue],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.DefaultValue] {
          def usedRegisters: RegisterOffset                                           = 1
          def construct(in: Registers, offset: RegisterOffset): Resolved.DefaultValue =
            Resolved.DefaultValue(in.getObject(offset + 0).asInstanceOf[Either[String, DynamicValue]])
        },
        deconstructor = new Deconstructor[Resolved.DefaultValue] {
          def usedRegisters: RegisterOffset                                                        = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.DefaultValue): Unit =
            out.setObject(offset + 0, in.defaultDynamic)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val convertSchema: Schema[Resolved.Convert] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.Convert](
      fields = Vector(
        Schema[String].reflect.asTerm("fromTypeName"),
        Schema[String].reflect.asTerm("toTypeName"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("inner")
      ),
      typeId = TypeId.of[Resolved.Convert],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.Convert] {
          def usedRegisters: RegisterOffset                                      = 3
          def construct(in: Registers, offset: RegisterOffset): Resolved.Convert =
            Resolved.Convert(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[Resolved.Convert] {
          def usedRegisters: RegisterOffset                                                   = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.Convert): Unit = {
            out.setObject(offset + 0, in.fromTypeName)
            out.setObject(offset + 1, in.toTypeName)
            out.setObject(offset + 2, in.inner)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val concatSchema: Schema[Resolved.Concat] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.Concat](
      fields = Vector(
        Reflect.Deferred(() => Schema.vector(resolvedSchema).reflect).asTerm("parts"),
        Schema[String].reflect.asTerm("separator")
      ),
      typeId = TypeId.of[Resolved.Concat],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.Concat] {
          def usedRegisters: RegisterOffset                                     = 2
          def construct(in: Registers, offset: RegisterOffset): Resolved.Concat =
            Resolved.Concat(
              in.getObject(offset + 0).asInstanceOf[Vector[Resolved]],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[Resolved.Concat] {
          def usedRegisters: RegisterOffset                                                  = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.Concat): Unit = {
            out.setObject(offset + 0, in.parts)
            out.setObject(offset + 1, in.separator)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val splitStringSchema: Schema[Resolved.SplitString] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.SplitString](
      fields = Vector(
        Schema[String].reflect.asTerm("separator"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("inner")
      ),
      typeId = TypeId.of[Resolved.SplitString],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.SplitString] {
          def usedRegisters: RegisterOffset                                          = 2
          def construct(in: Registers, offset: RegisterOffset): Resolved.SplitString =
            Resolved.SplitString(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[Resolved.SplitString] {
          def usedRegisters: RegisterOffset                                                       = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.SplitString): Unit = {
            out.setObject(offset + 0, in.separator)
            out.setObject(offset + 1, in.inner)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val wrapSomeSchema: Schema[Resolved.WrapSome] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.WrapSome](
      fields = Vector(
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("inner")
      ),
      typeId = TypeId.of[Resolved.WrapSome],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.WrapSome] {
          def usedRegisters: RegisterOffset                                       = 1
          def construct(in: Registers, offset: RegisterOffset): Resolved.WrapSome =
            Resolved.WrapSome(in.getObject(offset + 0).asInstanceOf[Resolved])
        },
        deconstructor = new Deconstructor[Resolved.WrapSome] {
          def usedRegisters: RegisterOffset                                                    = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.WrapSome): Unit =
            out.setObject(offset + 0, in.inner)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val unwrapOptionSchema: Schema[Resolved.UnwrapOption] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.UnwrapOption](
      fields = Vector(
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("inner"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("fallback")
      ),
      typeId = TypeId.of[Resolved.UnwrapOption],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.UnwrapOption] {
          def usedRegisters: RegisterOffset                                           = 2
          def construct(in: Registers, offset: RegisterOffset): Resolved.UnwrapOption =
            Resolved.UnwrapOption(
              in.getObject(offset + 0).asInstanceOf[Resolved],
              in.getObject(offset + 1).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[Resolved.UnwrapOption] {
          def usedRegisters: RegisterOffset                                                        = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.UnwrapOption): Unit = {
            out.setObject(offset + 0, in.inner)
            out.setObject(offset + 1, in.fallback)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val composeSchema: Schema[Resolved.Compose] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.Compose](
      fields = Vector(
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("outer"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("inner")
      ),
      typeId = TypeId.of[Resolved.Compose],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.Compose] {
          def usedRegisters: RegisterOffset                                      = 2
          def construct(in: Registers, offset: RegisterOffset): Resolved.Compose =
            Resolved.Compose(
              in.getObject(offset + 0).asInstanceOf[Resolved],
              in.getObject(offset + 1).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[Resolved.Compose] {
          def usedRegisters: RegisterOffset                                                   = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.Compose): Unit = {
            out.setObject(offset + 0, in.outer)
            out.setObject(offset + 1, in.inner)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val failSchema: Schema[Resolved.Fail] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.Fail](
      fields = Vector(
        Schema[String].reflect.asTerm("message")
      ),
      typeId = TypeId.of[Resolved.Fail],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.Fail] {
          def usedRegisters: RegisterOffset                                   = 1
          def construct(in: Registers, offset: RegisterOffset): Resolved.Fail =
            Resolved.Fail(in.getObject(offset + 0).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[Resolved.Fail] {
          def usedRegisters: RegisterOffset                                                = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.Fail): Unit =
            out.setObject(offset + 0, in.message)
        }
      ),
      modifiers = Vector.empty
    )
  )

  // Schema for (String, Resolved) tuple - needed for Construct fields
  private lazy val stringResolvedTupleSchema: Schema[(String, Resolved)] = new Schema(
    reflect = new Reflect.Record[Binding, (String, Resolved)](
      fields = Vector(
        Schema[String].reflect.asTerm("_1"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("_2")
      ),
      typeId = TypeId.of[(String, Resolved)],
      recordBinding = new Binding.Record(
        constructor = new Constructor[(String, Resolved)] {
          def usedRegisters: RegisterOffset                                        = 2
          def construct(in: Registers, offset: RegisterOffset): (String, Resolved) =
            (in.getObject(offset + 0).asInstanceOf[String], in.getObject(offset + 1).asInstanceOf[Resolved])
        },
        deconstructor = new Deconstructor[(String, Resolved)] {
          def usedRegisters: RegisterOffset                                                     = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: (String, Resolved)): Unit = {
            out.setObject(offset + 0, in._1)
            out.setObject(offset + 1, in._2)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val constructSchema: Schema[Resolved.Construct] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.Construct](
      fields = Vector(
        Reflect.Deferred(() => Schema.vector(stringResolvedTupleSchema).reflect).asTerm("fields")
      ),
      typeId = TypeId.of[Resolved.Construct],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.Construct] {
          def usedRegisters: RegisterOffset                                        = 1
          def construct(in: Registers, offset: RegisterOffset): Resolved.Construct =
            Resolved.Construct(in.getObject(offset + 0).asInstanceOf[Vector[(String, Resolved)]])
        },
        deconstructor = new Deconstructor[Resolved.Construct] {
          def usedRegisters: RegisterOffset                                                     = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.Construct): Unit =
            out.setObject(offset + 0, in.fields)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val constructSeqSchema: Schema[Resolved.ConstructSeq] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.ConstructSeq](
      fields = Vector(
        Reflect.Deferred(() => Schema.vector(resolvedSchema).reflect).asTerm("elements")
      ),
      typeId = TypeId.of[Resolved.ConstructSeq],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.ConstructSeq] {
          def usedRegisters: RegisterOffset                                           = 1
          def construct(in: Registers, offset: RegisterOffset): Resolved.ConstructSeq =
            Resolved.ConstructSeq(in.getObject(offset + 0).asInstanceOf[Vector[Resolved]])
        },
        deconstructor = new Deconstructor[Resolved.ConstructSeq] {
          def usedRegisters: RegisterOffset                                                        = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.ConstructSeq): Unit =
            out.setObject(offset + 0, in.elements)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val headSchema: Schema[Resolved.Head] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.Head](
      fields = Vector(
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("inner")
      ),
      typeId = TypeId.of[Resolved.Head],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.Head] {
          def usedRegisters: RegisterOffset                                   = 1
          def construct(in: Registers, offset: RegisterOffset): Resolved.Head =
            Resolved.Head(in.getObject(offset + 0).asInstanceOf[Resolved])
        },
        deconstructor = new Deconstructor[Resolved.Head] {
          def usedRegisters: RegisterOffset                                                = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.Head): Unit =
            out.setObject(offset + 0, in.inner)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val joinStringsSchema: Schema[Resolved.JoinStrings] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.JoinStrings](
      fields = Vector(
        Schema[String].reflect.asTerm("separator"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("inner")
      ),
      typeId = TypeId.of[Resolved.JoinStrings],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.JoinStrings] {
          def usedRegisters: RegisterOffset                                          = 2
          def construct(in: Registers, offset: RegisterOffset): Resolved.JoinStrings =
            Resolved.JoinStrings(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[Resolved.JoinStrings] {
          def usedRegisters: RegisterOffset                                                       = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.JoinStrings): Unit = {
            out.setObject(offset + 0, in.separator)
            out.setObject(offset + 1, in.inner)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val coalesceSchema: Schema[Resolved.Coalesce] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.Coalesce](
      fields = Vector(
        Reflect.Deferred(() => Schema.vector(resolvedSchema).reflect).asTerm("alternatives")
      ),
      typeId = TypeId.of[Resolved.Coalesce],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.Coalesce] {
          def usedRegisters: RegisterOffset                                       = 1
          def construct(in: Registers, offset: RegisterOffset): Resolved.Coalesce =
            Resolved.Coalesce(in.getObject(offset + 0).asInstanceOf[Vector[Resolved]])
        },
        deconstructor = new Deconstructor[Resolved.Coalesce] {
          def usedRegisters: RegisterOffset                                                    = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.Coalesce): Unit =
            out.setObject(offset + 0, in.alternatives)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val getOrElseSchema: Schema[Resolved.GetOrElse] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.GetOrElse](
      fields = Vector(
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("primary"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("fallback")
      ),
      typeId = TypeId.of[Resolved.GetOrElse],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.GetOrElse] {
          def usedRegisters: RegisterOffset                                        = 2
          def construct(in: Registers, offset: RegisterOffset): Resolved.GetOrElse =
            Resolved.GetOrElse(
              in.getObject(offset + 0).asInstanceOf[Resolved],
              in.getObject(offset + 1).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[Resolved.GetOrElse] {
          def usedRegisters: RegisterOffset                                                     = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.GetOrElse): Unit = {
            out.setObject(offset + 0, in.primary)
            out.setObject(offset + 1, in.fallback)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val rootAccessSchema: Schema[Resolved.RootAccess] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.RootAccess](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("path")
      ),
      typeId = TypeId.of[Resolved.RootAccess],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.RootAccess] {
          def usedRegisters: RegisterOffset                                         = 1
          def construct(in: Registers, offset: RegisterOffset): Resolved.RootAccess =
            Resolved.RootAccess(in.getObject(offset + 0).asInstanceOf[DynamicOptic])
        },
        deconstructor = new Deconstructor[Resolved.RootAccess] {
          def usedRegisters: RegisterOffset                                                      = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.RootAccess): Unit =
            out.setObject(offset + 0, in.path)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val atSchema: Schema[Resolved.At] = new Schema(
    reflect = new Reflect.Record[Binding, Resolved.At](
      fields = Vector(
        Schema[Int].reflect.asTerm("index"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("inner")
      ),
      typeId = TypeId.of[Resolved.At],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Resolved.At] {
          def usedRegisters: RegisterOffset                                 = RegisterOffset(ints = 1, objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Resolved.At =
            Resolved.At(
              in.getInt(offset),
              in.getObject(offset).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[Resolved.At] {
          def usedRegisters: RegisterOffset                                              = RegisterOffset(ints = 1, objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Resolved.At): Unit = {
            out.setInt(offset, in.index)
            out.setObject(offset, in.inner)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  // Schema for Resolved sealed trait
  implicit lazy val resolvedSchema: Schema[Resolved] = new Schema(
    reflect = new Reflect.Variant[Binding, Resolved](
      cases = Vector(
        literalSchema.reflect.asTerm("Literal"),
        identitySchema.reflect.asTerm("Identity"),
        fieldAccessSchema.reflect.asTerm("FieldAccess"),
        opticAccessSchema.reflect.asTerm("OpticAccess"),
        defaultValueSchema.reflect.asTerm("DefaultValue"),
        convertSchema.reflect.asTerm("Convert"),
        concatSchema.reflect.asTerm("Concat"),
        splitStringSchema.reflect.asTerm("SplitString"),
        wrapSomeSchema.reflect.asTerm("WrapSome"),
        unwrapOptionSchema.reflect.asTerm("UnwrapOption"),
        composeSchema.reflect.asTerm("Compose"),
        failSchema.reflect.asTerm("Fail"),
        constructSchema.reflect.asTerm("Construct"),
        constructSeqSchema.reflect.asTerm("ConstructSeq"),
        headSchema.reflect.asTerm("Head"),
        joinStringsSchema.reflect.asTerm("JoinStrings"),
        coalesceSchema.reflect.asTerm("Coalesce"),
        getOrElseSchema.reflect.asTerm("GetOrElse"),
        rootAccessSchema.reflect.asTerm("RootAccess"),
        atSchema.reflect.asTerm("At")
      ),
      typeId = TypeId.of[Resolved],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Resolved] {
          def discriminate(a: Resolved): Int = if (a == null) -1
          else
            a match {
              case _: Resolved.Literal      => 0
              case Resolved.Identity        => 1
              case _: Resolved.FieldAccess  => 2
              case _: Resolved.OpticAccess  => 3
              case _: Resolved.DefaultValue => 4
              case _: Resolved.Convert      => 5
              case _: Resolved.Concat       => 6
              case _: Resolved.SplitString  => 7
              case _: Resolved.WrapSome     => 8
              case _: Resolved.UnwrapOption => 9
              case _: Resolved.Compose      => 10
              case _: Resolved.Fail         => 11
              case _: Resolved.Construct    => 12
              case _: Resolved.ConstructSeq => 13
              case _: Resolved.Head         => 14
              case _: Resolved.JoinStrings  => 15
              case _: Resolved.Coalesce     => 16
              case _: Resolved.GetOrElse    => 17
              case _: Resolved.RootAccess   => 18
              case _: Resolved.At           => 19
            }
        },
        matchers = Matchers(
          new Matcher[Resolved.Literal] {
            def downcastOrNull(a: Any): Resolved.Literal = a match {
              case x: Resolved.Literal => x
              case _                   => null.asInstanceOf[Resolved.Literal]
            }
          },
          new Matcher[Resolved.Identity.type] {
            def downcastOrNull(a: Any): Resolved.Identity.type = a match {
              case Resolved.Identity => Resolved.Identity
              case _                 => null.asInstanceOf[Resolved.Identity.type]
            }
          },
          new Matcher[Resolved.FieldAccess] {
            def downcastOrNull(a: Any): Resolved.FieldAccess = a match {
              case x: Resolved.FieldAccess => x
              case _                       => null.asInstanceOf[Resolved.FieldAccess]
            }
          },
          new Matcher[Resolved.OpticAccess] {
            def downcastOrNull(a: Any): Resolved.OpticAccess = a match {
              case x: Resolved.OpticAccess => x
              case _                       => null.asInstanceOf[Resolved.OpticAccess]
            }
          },
          new Matcher[Resolved.DefaultValue] {
            def downcastOrNull(a: Any): Resolved.DefaultValue = a match {
              case x: Resolved.DefaultValue => x
              case _                        => null.asInstanceOf[Resolved.DefaultValue]
            }
          },
          new Matcher[Resolved.Convert] {
            def downcastOrNull(a: Any): Resolved.Convert = a match {
              case x: Resolved.Convert => x
              case _                   => null.asInstanceOf[Resolved.Convert]
            }
          },
          new Matcher[Resolved.Concat] {
            def downcastOrNull(a: Any): Resolved.Concat = a match {
              case x: Resolved.Concat => x
              case _                  => null.asInstanceOf[Resolved.Concat]
            }
          },
          new Matcher[Resolved.SplitString] {
            def downcastOrNull(a: Any): Resolved.SplitString = a match {
              case x: Resolved.SplitString => x
              case _                       => null.asInstanceOf[Resolved.SplitString]
            }
          },
          new Matcher[Resolved.WrapSome] {
            def downcastOrNull(a: Any): Resolved.WrapSome = a match {
              case x: Resolved.WrapSome => x
              case _                    => null.asInstanceOf[Resolved.WrapSome]
            }
          },
          new Matcher[Resolved.UnwrapOption] {
            def downcastOrNull(a: Any): Resolved.UnwrapOption = a match {
              case x: Resolved.UnwrapOption => x
              case _                        => null.asInstanceOf[Resolved.UnwrapOption]
            }
          },
          new Matcher[Resolved.Compose] {
            def downcastOrNull(a: Any): Resolved.Compose = a match {
              case x: Resolved.Compose => x
              case _                   => null.asInstanceOf[Resolved.Compose]
            }
          },
          new Matcher[Resolved.Fail] {
            def downcastOrNull(a: Any): Resolved.Fail = a match {
              case x: Resolved.Fail => x
              case _                => null.asInstanceOf[Resolved.Fail]
            }
          },
          new Matcher[Resolved.Construct] {
            def downcastOrNull(a: Any): Resolved.Construct = a match {
              case x: Resolved.Construct => x
              case _                     => null.asInstanceOf[Resolved.Construct]
            }
          },
          new Matcher[Resolved.ConstructSeq] {
            def downcastOrNull(a: Any): Resolved.ConstructSeq = a match {
              case x: Resolved.ConstructSeq => x
              case _                        => null.asInstanceOf[Resolved.ConstructSeq]
            }
          },
          new Matcher[Resolved.Head] {
            def downcastOrNull(a: Any): Resolved.Head = a match {
              case x: Resolved.Head => x
              case _                => null.asInstanceOf[Resolved.Head]
            }
          },
          new Matcher[Resolved.JoinStrings] {
            def downcastOrNull(a: Any): Resolved.JoinStrings = a match {
              case x: Resolved.JoinStrings => x
              case _                       => null.asInstanceOf[Resolved.JoinStrings]
            }
          },
          new Matcher[Resolved.Coalesce] {
            def downcastOrNull(a: Any): Resolved.Coalesce = a match {
              case x: Resolved.Coalesce => x
              case _                    => null.asInstanceOf[Resolved.Coalesce]
            }
          },
          new Matcher[Resolved.GetOrElse] {
            def downcastOrNull(a: Any): Resolved.GetOrElse = a match {
              case x: Resolved.GetOrElse => x
              case _                     => null.asInstanceOf[Resolved.GetOrElse]
            }
          },
          new Matcher[Resolved.RootAccess] {
            def downcastOrNull(a: Any): Resolved.RootAccess = a match {
              case x: Resolved.RootAccess => x
              case _                      => null.asInstanceOf[Resolved.RootAccess]
            }
          },
          new Matcher[Resolved.At] {
            def downcastOrNull(a: Any): Resolved.At = a match {
              case x: Resolved.At => x
              case _              => null.asInstanceOf[Resolved.At]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  // ═══════════════════════════════════════════════════════════════════════════
  // MigrationAction Schemas
  // ═══════════════════════════════════════════════════════════════════════════

  private lazy val addFieldSchema: Schema[MigrationAction.AddField] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.AddField](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("default")
      ),
      typeId = TypeId.of[MigrationAction.AddField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.AddField] {
          def usedRegisters: RegisterOffset                                              = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.AddField =
            MigrationAction.AddField(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.AddField] {
          def usedRegisters: RegisterOffset                                                           = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.AddField): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
            out.setObject(offset + 2, in.default)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val dropFieldSchema: Schema[MigrationAction.DropField] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.DropField](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("defaultForReverse")
      ),
      typeId = TypeId.of[MigrationAction.DropField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.DropField] {
          def usedRegisters: RegisterOffset                                               = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.DropField =
            MigrationAction.DropField(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.DropField] {
          def usedRegisters: RegisterOffset                                                            = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.DropField): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
            out.setObject(offset + 2, in.defaultForReverse)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val renameSchema: Schema[MigrationAction.Rename] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.Rename](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("from"),
        Schema[String].reflect.asTerm("to")
      ),
      typeId = TypeId.of[MigrationAction.Rename],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.Rename] {
          def usedRegisters: RegisterOffset                                            = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.Rename =
            MigrationAction.Rename(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.Rename] {
          def usedRegisters: RegisterOffset                                                         = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.Rename): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.from)
            out.setObject(offset + 2, in.to)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val transformValueSchema: Schema[MigrationAction.TransformValue] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformValue](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("transform"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("reverseTransform")
      ),
      typeId = TypeId.of[MigrationAction.TransformValue],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformValue] {
          def usedRegisters: RegisterOffset                                                    = 4
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformValue =
            MigrationAction.TransformValue(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Resolved],
              in.getObject(offset + 3).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformValue] {
          def usedRegisters: RegisterOffset                                                                 = 4
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformValue): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
            out.setObject(offset + 2, in.transform)
            out.setObject(offset + 3, in.reverseTransform)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val mandateSchema: Schema[MigrationAction.Mandate] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.Mandate](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("default")
      ),
      typeId = TypeId.of[MigrationAction.Mandate],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.Mandate] {
          def usedRegisters: RegisterOffset                                             = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.Mandate =
            MigrationAction.Mandate(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.Mandate] {
          def usedRegisters: RegisterOffset                                                          = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.Mandate): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
            out.setObject(offset + 2, in.default)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val optionalizeSchema: Schema[MigrationAction.Optionalize] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.Optionalize](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName")
      ),
      typeId = TypeId.of[MigrationAction.Optionalize],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.Optionalize] {
          def usedRegisters: RegisterOffset                                                 = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.Optionalize =
            MigrationAction.Optionalize(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.Optionalize] {
          def usedRegisters: RegisterOffset                                                              = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.Optionalize): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val changeTypeSchema: Schema[MigrationAction.ChangeType] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.ChangeType](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("converter"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("reverseConverter")
      ),
      typeId = TypeId.of[MigrationAction.ChangeType],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.ChangeType] {
          def usedRegisters: RegisterOffset                                                = 4
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.ChangeType =
            MigrationAction.ChangeType(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Resolved],
              in.getObject(offset + 3).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.ChangeType] {
          def usedRegisters: RegisterOffset                                                             = 4
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.ChangeType): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
            out.setObject(offset + 2, in.converter)
            out.setObject(offset + 3, in.reverseConverter)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val renameCaseSchema: Schema[MigrationAction.RenameCase] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.RenameCase](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("from"),
        Schema[String].reflect.asTerm("to")
      ),
      typeId = TypeId.of[MigrationAction.RenameCase],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.RenameCase] {
          def usedRegisters: RegisterOffset                                                = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.RenameCase =
            MigrationAction.RenameCase(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.RenameCase] {
          def usedRegisters: RegisterOffset                                                             = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.RenameCase): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.from)
            out.setObject(offset + 2, in.to)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val transformCaseSchema: Schema[MigrationAction.TransformCase] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformCase](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("caseName"),
        Reflect.Deferred(() => Schema.vector(migrationActionSchema).reflect).asTerm("caseActions")
      ),
      typeId = TypeId.of[MigrationAction.TransformCase],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformCase] {
          def usedRegisters: RegisterOffset                                                   = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformCase =
            MigrationAction.TransformCase(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Vector[MigrationAction]]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformCase] {
          def usedRegisters: RegisterOffset                                                                = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformCase): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.caseName)
            out.setObject(offset + 2, in.caseActions)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val transformFieldSchema: Schema[MigrationAction.TransformField] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformField](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        Reflect.Deferred(() => Schema.vector(migrationActionSchema).reflect).asTerm("fieldActions")
      ),
      typeId = TypeId.of[MigrationAction.TransformField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformField] {
          def usedRegisters: RegisterOffset                                                    = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformField =
            MigrationAction.TransformField(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Vector[MigrationAction]]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformField] {
          def usedRegisters: RegisterOffset                                                                 = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformField): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
            out.setObject(offset + 2, in.fieldActions)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val transformEachElementSchema: Schema[MigrationAction.TransformEachElement] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformEachElement](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        Reflect.Deferred(() => Schema.vector(migrationActionSchema).reflect).asTerm("elementActions")
      ),
      typeId = TypeId.of[MigrationAction.TransformEachElement],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformEachElement] {
          def usedRegisters: RegisterOffset                                                          = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformEachElement =
            MigrationAction.TransformEachElement(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Vector[MigrationAction]]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformEachElement] {
          def usedRegisters: RegisterOffset                                                                       = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformEachElement): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
            out.setObject(offset + 2, in.elementActions)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val transformEachMapValueSchema: Schema[MigrationAction.TransformEachMapValue] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformEachMapValue](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        Reflect.Deferred(() => Schema.vector(migrationActionSchema).reflect).asTerm("valueActions")
      ),
      typeId = TypeId.of[MigrationAction.TransformEachMapValue],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformEachMapValue] {
          def usedRegisters: RegisterOffset                                                           = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformEachMapValue =
            MigrationAction.TransformEachMapValue(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Vector[MigrationAction]]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformEachMapValue] {
          def usedRegisters: RegisterOffset                                                                        = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformEachMapValue): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
            out.setObject(offset + 2, in.valueActions)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val transformElementsSchema: Schema[MigrationAction.TransformElements] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformElements](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("elementTransform"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("reverseTransform")
      ),
      typeId = TypeId.of[MigrationAction.TransformElements],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformElements] {
          def usedRegisters: RegisterOffset                                                       = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformElements =
            MigrationAction.TransformElements(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[Resolved],
              in.getObject(offset + 2).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformElements] {
          def usedRegisters: RegisterOffset                                                                    = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformElements): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.elementTransform)
            out.setObject(offset + 2, in.reverseTransform)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val transformKeysSchema: Schema[MigrationAction.TransformKeys] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformKeys](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("keyTransform"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("reverseTransform")
      ),
      typeId = TypeId.of[MigrationAction.TransformKeys],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformKeys] {
          def usedRegisters: RegisterOffset                                                   = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformKeys =
            MigrationAction.TransformKeys(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[Resolved],
              in.getObject(offset + 2).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformKeys] {
          def usedRegisters: RegisterOffset                                                                = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformKeys): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.keyTransform)
            out.setObject(offset + 2, in.reverseTransform)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val transformValuesSchema: Schema[MigrationAction.TransformValues] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformValues](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("valueTransform"),
        Reflect.Deferred(() => resolvedSchema.reflect).asTerm("reverseTransform")
      ),
      typeId = TypeId.of[MigrationAction.TransformValues],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformValues] {
          def usedRegisters: RegisterOffset                                                     = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformValues =
            MigrationAction.TransformValues(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[Resolved],
              in.getObject(offset + 2).asInstanceOf[Resolved]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformValues] {
          def usedRegisters: RegisterOffset                                                                  = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformValues): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.valueTransform)
            out.setObject(offset + 2, in.reverseTransform)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  // Schema for MigrationAction sealed trait
  implicit lazy val migrationActionSchema: Schema[MigrationAction] = new Schema(
    reflect = new Reflect.Variant[Binding, MigrationAction](
      cases = Vector(
        addFieldSchema.reflect.asTerm("AddField"),
        dropFieldSchema.reflect.asTerm("DropField"),
        renameSchema.reflect.asTerm("Rename"),
        transformValueSchema.reflect.asTerm("TransformValue"),
        mandateSchema.reflect.asTerm("Mandate"),
        optionalizeSchema.reflect.asTerm("Optionalize"),
        changeTypeSchema.reflect.asTerm("ChangeType"),
        renameCaseSchema.reflect.asTerm("RenameCase"),
        transformCaseSchema.reflect.asTerm("TransformCase"),
        transformElementsSchema.reflect.asTerm("TransformElements"),
        transformKeysSchema.reflect.asTerm("TransformKeys"),
        transformValuesSchema.reflect.asTerm("TransformValues"),
        transformFieldSchema.reflect.asTerm("TransformField"),
        transformEachElementSchema.reflect.asTerm("TransformEachElement"),
        transformEachMapValueSchema.reflect.asTerm("TransformEachMapValue")
      ),
      typeId = TypeId.of[MigrationAction],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MigrationAction] {
          def discriminate(a: MigrationAction): Int = a match {
            case _: MigrationAction.AddField              => 0
            case _: MigrationAction.DropField             => 1
            case _: MigrationAction.Rename                => 2
            case _: MigrationAction.TransformValue        => 3
            case _: MigrationAction.Mandate               => 4
            case _: MigrationAction.Optionalize           => 5
            case _: MigrationAction.ChangeType            => 6
            case _: MigrationAction.RenameCase            => 7
            case _: MigrationAction.TransformCase         => 8
            case _: MigrationAction.TransformElements     => 9
            case _: MigrationAction.TransformKeys         => 10
            case _: MigrationAction.TransformValues       => 11
            case _: MigrationAction.TransformField        => 12
            case _: MigrationAction.TransformEachElement  => 13
            case _: MigrationAction.TransformEachMapValue => 14
          }
        },
        matchers = Matchers(
          new Matcher[MigrationAction.AddField] {
            def downcastOrNull(a: Any): MigrationAction.AddField = a match {
              case x: MigrationAction.AddField => x
              case _                           => null.asInstanceOf[MigrationAction.AddField]
            }
          },
          new Matcher[MigrationAction.DropField] {
            def downcastOrNull(a: Any): MigrationAction.DropField = a match {
              case x: MigrationAction.DropField => x
              case _                            => null.asInstanceOf[MigrationAction.DropField]
            }
          },
          new Matcher[MigrationAction.Rename] {
            def downcastOrNull(a: Any): MigrationAction.Rename = a match {
              case x: MigrationAction.Rename => x
              case _                         => null.asInstanceOf[MigrationAction.Rename]
            }
          },
          new Matcher[MigrationAction.TransformValue] {
            def downcastOrNull(a: Any): MigrationAction.TransformValue = a match {
              case x: MigrationAction.TransformValue => x
              case _                                 => null.asInstanceOf[MigrationAction.TransformValue]
            }
          },
          new Matcher[MigrationAction.Mandate] {
            def downcastOrNull(a: Any): MigrationAction.Mandate = a match {
              case x: MigrationAction.Mandate => x
              case _                          => null.asInstanceOf[MigrationAction.Mandate]
            }
          },
          new Matcher[MigrationAction.Optionalize] {
            def downcastOrNull(a: Any): MigrationAction.Optionalize = a match {
              case x: MigrationAction.Optionalize => x
              case _                              => null.asInstanceOf[MigrationAction.Optionalize]
            }
          },
          new Matcher[MigrationAction.ChangeType] {
            def downcastOrNull(a: Any): MigrationAction.ChangeType = a match {
              case x: MigrationAction.ChangeType => x
              case _                             => null.asInstanceOf[MigrationAction.ChangeType]
            }
          },
          new Matcher[MigrationAction.RenameCase] {
            def downcastOrNull(a: Any): MigrationAction.RenameCase = a match {
              case x: MigrationAction.RenameCase => x
              case _                             => null.asInstanceOf[MigrationAction.RenameCase]
            }
          },
          new Matcher[MigrationAction.TransformCase] {
            def downcastOrNull(a: Any): MigrationAction.TransformCase = a match {
              case x: MigrationAction.TransformCase => x
              case _                                => null.asInstanceOf[MigrationAction.TransformCase]
            }
          },
          new Matcher[MigrationAction.TransformElements] {
            def downcastOrNull(a: Any): MigrationAction.TransformElements = a match {
              case x: MigrationAction.TransformElements => x
              case _                                    => null.asInstanceOf[MigrationAction.TransformElements]
            }
          },
          new Matcher[MigrationAction.TransformKeys] {
            def downcastOrNull(a: Any): MigrationAction.TransformKeys = a match {
              case x: MigrationAction.TransformKeys => x
              case _                                => null.asInstanceOf[MigrationAction.TransformKeys]
            }
          },
          new Matcher[MigrationAction.TransformValues] {
            def downcastOrNull(a: Any): MigrationAction.TransformValues = a match {
              case x: MigrationAction.TransformValues => x
              case _                                  => null.asInstanceOf[MigrationAction.TransformValues]
            }
          },
          new Matcher[MigrationAction.TransformField] {
            def downcastOrNull(a: Any): MigrationAction.TransformField = a match {
              case x: MigrationAction.TransformField => x
              case _                                 => null.asInstanceOf[MigrationAction.TransformField]
            }
          },
          new Matcher[MigrationAction.TransformEachElement] {
            def downcastOrNull(a: Any): MigrationAction.TransformEachElement = a match {
              case x: MigrationAction.TransformEachElement => x
              case _                                       => null.asInstanceOf[MigrationAction.TransformEachElement]
            }
          },
          new Matcher[MigrationAction.TransformEachMapValue] {
            def downcastOrNull(a: Any): MigrationAction.TransformEachMapValue = a match {
              case x: MigrationAction.TransformEachMapValue => x
              case _                                        => null.asInstanceOf[MigrationAction.TransformEachMapValue]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  // ═══════════════════════════════════════════════════════════════════════════
  // DynamicMigration Schema
  // ═══════════════════════════════════════════════════════════════════════════

  implicit lazy val dynamicMigrationSchema: Schema[DynamicMigration] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicMigration](
      fields = Vector(
        Reflect.Deferred(() => Schema.vector(migrationActionSchema).reflect).asTerm("actions")
      ),
      typeId = TypeId.of[DynamicMigration],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicMigration] {
          def usedRegisters: RegisterOffset                                      = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicMigration =
            DynamicMigration(in.getObject(offset + 0).asInstanceOf[Vector[MigrationAction]])
        },
        deconstructor = new Deconstructor[DynamicMigration] {
          def usedRegisters: RegisterOffset                                                   = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicMigration): Unit =
            out.setObject(offset + 0, in.actions)
        }
      ),
      modifiers = Vector.empty
    )
  )
}
