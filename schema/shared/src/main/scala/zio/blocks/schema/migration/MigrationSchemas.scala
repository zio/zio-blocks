package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset._
import zio.blocks.typeid.TypeId

/**
 * Schema instances for migration types, enabling serialization/deserialization
 * of [[DynamicMigration]], [[MigrationAction]], and [[DynamicSchemaExpr]].
 *
 * These schemas are hand-written for Scala 2 compatibility and to ensure
 * optimal serialization behavior.
 */
object MigrationSchemas {

  // ==================== DynamicSchemaExpr Schemas ====================

  implicit lazy val literalSchema: Schema[DynamicSchemaExpr.Literal] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.Literal](
      fields = Vector(
        Schema[DynamicValue].reflect.asTerm("value")
      ),
      typeId = TypeId.of[DynamicSchemaExpr.Literal],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicSchemaExpr.Literal] {
          def usedRegisters: RegisterOffset                                               = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicSchemaExpr.Literal =
            DynamicSchemaExpr.Literal(in.getObject(offset + 0).asInstanceOf[DynamicValue])
        },
        deconstructor = new Deconstructor[DynamicSchemaExpr.Literal] {
          def usedRegisters: RegisterOffset                                                            = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicSchemaExpr.Literal): Unit =
            out.setObject(offset + 0, in.value)
        }
      )
    )
  )

  implicit lazy val pathExprSchema: Schema[DynamicSchemaExpr.Path] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.Path](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("optic")
      ),
      typeId = TypeId.of[DynamicSchemaExpr.Path],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicSchemaExpr.Path] {
          def usedRegisters: RegisterOffset                                            = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicSchemaExpr.Path =
            DynamicSchemaExpr.Path(in.getObject(offset + 0).asInstanceOf[DynamicOptic])
        },
        deconstructor = new Deconstructor[DynamicSchemaExpr.Path] {
          def usedRegisters: RegisterOffset                                                         = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicSchemaExpr.Path): Unit =
            out.setObject(offset + 0, in.optic)
        }
      )
    )
  )

  implicit lazy val defaultValueExprSchema: Schema[DynamicSchemaExpr.DefaultValue.type] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.DefaultValue.type](
      fields = Vector.empty,
      typeId = TypeId.of[DynamicSchemaExpr.DefaultValue.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[DynamicSchemaExpr.DefaultValue.type](DynamicSchemaExpr.DefaultValue),
        deconstructor = new ConstantDeconstructor[DynamicSchemaExpr.DefaultValue.type]
      )
    )
  )

  implicit lazy val resolvedDefaultSchema: Schema[DynamicSchemaExpr.ResolvedDefault] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.ResolvedDefault](
      fields = Vector(
        Schema[DynamicValue].reflect.asTerm("value")
      ),
      typeId = TypeId.of[DynamicSchemaExpr.ResolvedDefault],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicSchemaExpr.ResolvedDefault] {
          def usedRegisters: RegisterOffset                                                       = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicSchemaExpr.ResolvedDefault =
            DynamicSchemaExpr.ResolvedDefault(in.getObject(offset + 0).asInstanceOf[DynamicValue])
        },
        deconstructor = new Deconstructor[DynamicSchemaExpr.ResolvedDefault] {
          def usedRegisters: RegisterOffset                                                                    = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicSchemaExpr.ResolvedDefault): Unit =
            out.setObject(offset + 0, in.value)
        }
      )
    )
  )

  implicit lazy val notExprSchema: Schema[DynamicSchemaExpr.Not] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.Not](
      fields = Vector(
        new Reflect.Deferred(() => dynamicSchemaExprSchema.reflect).asTerm("expr")
      ),
      typeId = TypeId.of[DynamicSchemaExpr.Not],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicSchemaExpr.Not] {
          def usedRegisters: RegisterOffset                                           = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicSchemaExpr.Not =
            DynamicSchemaExpr.Not(in.getObject(offset + 0).asInstanceOf[DynamicSchemaExpr])
        },
        deconstructor = new Deconstructor[DynamicSchemaExpr.Not] {
          def usedRegisters: RegisterOffset                                                        = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicSchemaExpr.Not): Unit =
            out.setObject(offset + 0, in.expr)
        }
      )
    )
  )

  implicit lazy val logicalOperatorAndSchema: Schema[DynamicSchemaExpr.LogicalOperator.And.type] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.LogicalOperator.And.type](
      fields = Vector.empty,
      typeId = TypeId.of[DynamicSchemaExpr.LogicalOperator.And.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor(DynamicSchemaExpr.LogicalOperator.And),
        deconstructor = new ConstantDeconstructor
      )
    )
  )

  implicit lazy val logicalOperatorOrSchema: Schema[DynamicSchemaExpr.LogicalOperator.Or.type] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.LogicalOperator.Or.type](
      fields = Vector.empty,
      typeId = TypeId.of[DynamicSchemaExpr.LogicalOperator.Or.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor(DynamicSchemaExpr.LogicalOperator.Or),
        deconstructor = new ConstantDeconstructor
      )
    )
  )

  implicit lazy val logicalOperatorSchema: Schema[DynamicSchemaExpr.LogicalOperator] = new Schema(
    reflect = new Reflect.Variant[Binding, DynamicSchemaExpr.LogicalOperator](
      cases = Vector(
        logicalOperatorAndSchema.reflect.asTerm("And"),
        logicalOperatorOrSchema.reflect.asTerm("Or")
      ),
      typeId = TypeId.of[DynamicSchemaExpr.LogicalOperator],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[DynamicSchemaExpr.LogicalOperator] {
          def discriminate(a: DynamicSchemaExpr.LogicalOperator): Int = a match {
            case DynamicSchemaExpr.LogicalOperator.And => 0
            case DynamicSchemaExpr.LogicalOperator.Or  => 1
          }
        },
        matchers = Matchers(
          new Matcher[DynamicSchemaExpr.LogicalOperator.And.type] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.LogicalOperator.And.type = a match {
              case x: DynamicSchemaExpr.LogicalOperator.And.type => x
              case _                                             => null.asInstanceOf[DynamicSchemaExpr.LogicalOperator.And.type]
            }
          },
          new Matcher[DynamicSchemaExpr.LogicalOperator.Or.type] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.LogicalOperator.Or.type = a match {
              case x: DynamicSchemaExpr.LogicalOperator.Or.type => x
              case _                                            => null.asInstanceOf[DynamicSchemaExpr.LogicalOperator.Or.type]
            }
          }
        )
      )
    )
  )

  implicit lazy val logicalExprSchema: Schema[DynamicSchemaExpr.Logical] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.Logical](
      fields = Vector(
        new Reflect.Deferred(() => dynamicSchemaExprSchema.reflect).asTerm("left"),
        new Reflect.Deferred(() => dynamicSchemaExprSchema.reflect).asTerm("right"),
        logicalOperatorSchema.reflect.asTerm("operator")
      ),
      typeId = TypeId.of[DynamicSchemaExpr.Logical],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicSchemaExpr.Logical] {
          def usedRegisters: RegisterOffset                                               = 3
          def construct(in: Registers, offset: RegisterOffset): DynamicSchemaExpr.Logical =
            DynamicSchemaExpr.Logical(
              in.getObject(offset + 0).asInstanceOf[DynamicSchemaExpr],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr],
              in.getObject(offset + 2).asInstanceOf[DynamicSchemaExpr.LogicalOperator]
            )
        },
        deconstructor = new Deconstructor[DynamicSchemaExpr.Logical] {
          def usedRegisters: RegisterOffset                                                            = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicSchemaExpr.Logical): Unit = {
            out.setObject(offset + 0, in.left)
            out.setObject(offset + 1, in.right)
            out.setObject(offset + 2, in.operator)
          }
        }
      )
    )
  )

  implicit lazy val relationalOperatorSchema: Schema[DynamicSchemaExpr.RelationalOperator] = {
    import DynamicSchemaExpr.RelationalOperator._

    val ltSchema: Schema[LessThan.type] = new Schema(
      reflect = new Reflect.Record[Binding, LessThan.type](
        fields = Vector.empty,
        typeId = TypeId.of[DynamicSchemaExpr.RelationalOperator.LessThan.type],
        recordBinding = new Binding.Record(
          constructor = new ConstantConstructor(LessThan),
          deconstructor = new ConstantDeconstructor
        )
      )
    )

    val lteSchema: Schema[LessThanOrEqual.type] = new Schema(
      reflect = new Reflect.Record[Binding, LessThanOrEqual.type](
        fields = Vector.empty,
        typeId = TypeId.of[DynamicSchemaExpr.RelationalOperator.LessThanOrEqual.type],
        recordBinding = new Binding.Record(
          constructor = new ConstantConstructor(LessThanOrEqual),
          deconstructor = new ConstantDeconstructor
        )
      )
    )

    val gtSchema: Schema[GreaterThan.type] = new Schema(
      reflect = new Reflect.Record[Binding, GreaterThan.type](
        fields = Vector.empty,
        typeId = TypeId.of[DynamicSchemaExpr.RelationalOperator.GreaterThan.type],
        recordBinding = new Binding.Record(
          constructor = new ConstantConstructor(GreaterThan),
          deconstructor = new ConstantDeconstructor
        )
      )
    )

    val gteSchema: Schema[GreaterThanOrEqual.type] = new Schema(
      reflect = new Reflect.Record[Binding, GreaterThanOrEqual.type](
        fields = Vector.empty,
        typeId = TypeId.of[DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual.type],
        recordBinding = new Binding.Record(
          constructor = new ConstantConstructor(GreaterThanOrEqual),
          deconstructor = new ConstantDeconstructor
        )
      )
    )

    val eqSchema: Schema[Equal.type] = new Schema(
      reflect = new Reflect.Record[Binding, Equal.type](
        fields = Vector.empty,
        typeId = TypeId.of[DynamicSchemaExpr.RelationalOperator.Equal.type],
        recordBinding = new Binding.Record(
          constructor = new ConstantConstructor(Equal),
          deconstructor = new ConstantDeconstructor
        )
      )
    )

    val neqSchema: Schema[NotEqual.type] = new Schema(
      reflect = new Reflect.Record[Binding, NotEqual.type](
        fields = Vector.empty,
        typeId = TypeId.of[DynamicSchemaExpr.RelationalOperator.NotEqual.type],
        recordBinding = new Binding.Record(
          constructor = new ConstantConstructor(NotEqual),
          deconstructor = new ConstantDeconstructor
        )
      )
    )

    new Schema(
      reflect = new Reflect.Variant[Binding, DynamicSchemaExpr.RelationalOperator](
        cases = Vector(
          ltSchema.reflect.asTerm("LessThan"),
          lteSchema.reflect.asTerm("LessThanOrEqual"),
          gtSchema.reflect.asTerm("GreaterThan"),
          gteSchema.reflect.asTerm("GreaterThanOrEqual"),
          eqSchema.reflect.asTerm("Equal"),
          neqSchema.reflect.asTerm("NotEqual")
        ),
        typeId = TypeId.of[DynamicSchemaExpr.RelationalOperator],
        variantBinding = new Binding.Variant(
          discriminator = new Discriminator[DynamicSchemaExpr.RelationalOperator] {
            def discriminate(a: DynamicSchemaExpr.RelationalOperator): Int = a match {
              case LessThan           => 0
              case LessThanOrEqual    => 1
              case GreaterThan        => 2
              case GreaterThanOrEqual => 3
              case Equal              => 4
              case NotEqual           => 5
            }
          },
          matchers = Matchers(
            new Matcher[LessThan.type] {
              def downcastOrNull(a: Any): LessThan.type = a match {
                case x: LessThan.type => x
                case _                => null.asInstanceOf[LessThan.type]
              }
            },
            new Matcher[LessThanOrEqual.type] {
              def downcastOrNull(a: Any): LessThanOrEqual.type = a match {
                case x: LessThanOrEqual.type => x
                case _                       => null.asInstanceOf[LessThanOrEqual.type]
              }
            },
            new Matcher[GreaterThan.type] {
              def downcastOrNull(a: Any): GreaterThan.type = a match {
                case x: GreaterThan.type => x
                case _                   => null.asInstanceOf[GreaterThan.type]
              }
            },
            new Matcher[GreaterThanOrEqual.type] {
              def downcastOrNull(a: Any): GreaterThanOrEqual.type = a match {
                case x: GreaterThanOrEqual.type => x
                case _                          => null.asInstanceOf[GreaterThanOrEqual.type]
              }
            },
            new Matcher[Equal.type] {
              def downcastOrNull(a: Any): Equal.type = a match {
                case x: Equal.type => x
                case _             => null.asInstanceOf[Equal.type]
              }
            },
            new Matcher[NotEqual.type] {
              def downcastOrNull(a: Any): NotEqual.type = a match {
                case x: NotEqual.type => x
                case _                => null.asInstanceOf[NotEqual.type]
              }
            }
          )
        )
      )
    )
  }

  implicit lazy val relationalExprSchema: Schema[DynamicSchemaExpr.Relational] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.Relational](
      fields = Vector(
        new Reflect.Deferred(() => dynamicSchemaExprSchema.reflect).asTerm("left"),
        new Reflect.Deferred(() => dynamicSchemaExprSchema.reflect).asTerm("right"),
        relationalOperatorSchema.reflect.asTerm("operator")
      ),
      typeId = TypeId.of[DynamicSchemaExpr.Relational],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicSchemaExpr.Relational] {
          def usedRegisters: RegisterOffset                                                  = 3
          def construct(in: Registers, offset: RegisterOffset): DynamicSchemaExpr.Relational =
            DynamicSchemaExpr.Relational(
              in.getObject(offset + 0).asInstanceOf[DynamicSchemaExpr],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr],
              in.getObject(offset + 2).asInstanceOf[DynamicSchemaExpr.RelationalOperator]
            )
        },
        deconstructor = new Deconstructor[DynamicSchemaExpr.Relational] {
          def usedRegisters: RegisterOffset                                                               = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicSchemaExpr.Relational): Unit = {
            out.setObject(offset + 0, in.left)
            out.setObject(offset + 1, in.right)
            out.setObject(offset + 2, in.operator)
          }
        }
      )
    )
  )

  implicit lazy val arithmeticOperatorSchema: Schema[DynamicSchemaExpr.ArithmeticOperator] = {
    import DynamicSchemaExpr.ArithmeticOperator._

    val addSchema: Schema[Add.type] = new Schema(
      reflect = new Reflect.Record[Binding, Add.type](
        fields = Vector.empty,
        typeId = TypeId.of[DynamicSchemaExpr.ArithmeticOperator.Add.type],
        recordBinding = new Binding.Record(
          constructor = new ConstantConstructor(Add),
          deconstructor = new ConstantDeconstructor
        )
      )
    )

    val subSchema: Schema[Subtract.type] = new Schema(
      reflect = new Reflect.Record[Binding, Subtract.type](
        fields = Vector.empty,
        typeId = TypeId.of[DynamicSchemaExpr.ArithmeticOperator.Subtract.type],
        recordBinding = new Binding.Record(
          constructor = new ConstantConstructor(Subtract),
          deconstructor = new ConstantDeconstructor
        )
      )
    )

    val mulSchema: Schema[Multiply.type] = new Schema(
      reflect = new Reflect.Record[Binding, Multiply.type](
        fields = Vector.empty,
        typeId = TypeId.of[DynamicSchemaExpr.ArithmeticOperator.Multiply.type],
        recordBinding = new Binding.Record(
          constructor = new ConstantConstructor(Multiply),
          deconstructor = new ConstantDeconstructor
        )
      )
    )

    new Schema(
      reflect = new Reflect.Variant[Binding, DynamicSchemaExpr.ArithmeticOperator](
        cases = Vector(
          addSchema.reflect.asTerm("Add"),
          subSchema.reflect.asTerm("Subtract"),
          mulSchema.reflect.asTerm("Multiply")
        ),
        typeId = TypeId.of[DynamicSchemaExpr.ArithmeticOperator],
        variantBinding = new Binding.Variant(
          discriminator = new Discriminator[DynamicSchemaExpr.ArithmeticOperator] {
            def discriminate(a: DynamicSchemaExpr.ArithmeticOperator): Int = a match {
              case Add      => 0
              case Subtract => 1
              case Multiply => 2
            }
          },
          matchers = Matchers(
            new Matcher[Add.type] {
              def downcastOrNull(a: Any): Add.type = a match {
                case x: Add.type => x
                case _           => null.asInstanceOf[Add.type]
              }
            },
            new Matcher[Subtract.type] {
              def downcastOrNull(a: Any): Subtract.type = a match {
                case x: Subtract.type => x
                case _                => null.asInstanceOf[Subtract.type]
              }
            },
            new Matcher[Multiply.type] {
              def downcastOrNull(a: Any): Multiply.type = a match {
                case x: Multiply.type => x
                case _                => null.asInstanceOf[Multiply.type]
              }
            }
          )
        )
      )
    )
  }

  implicit lazy val arithmeticExprSchema: Schema[DynamicSchemaExpr.Arithmetic] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.Arithmetic](
      fields = Vector(
        new Reflect.Deferred(() => dynamicSchemaExprSchema.reflect).asTerm("left"),
        new Reflect.Deferred(() => dynamicSchemaExprSchema.reflect).asTerm("right"),
        arithmeticOperatorSchema.reflect.asTerm("operator")
      ),
      typeId = TypeId.of[DynamicSchemaExpr.Arithmetic],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicSchemaExpr.Arithmetic] {
          def usedRegisters: RegisterOffset                                                  = 3
          def construct(in: Registers, offset: RegisterOffset): DynamicSchemaExpr.Arithmetic =
            DynamicSchemaExpr.Arithmetic(
              in.getObject(offset + 0).asInstanceOf[DynamicSchemaExpr],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr],
              in.getObject(offset + 2).asInstanceOf[DynamicSchemaExpr.ArithmeticOperator]
            )
        },
        deconstructor = new Deconstructor[DynamicSchemaExpr.Arithmetic] {
          def usedRegisters: RegisterOffset                                                               = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicSchemaExpr.Arithmetic): Unit = {
            out.setObject(offset + 0, in.left)
            out.setObject(offset + 1, in.right)
            out.setObject(offset + 2, in.operator)
          }
        }
      )
    )
  )

  implicit lazy val stringConcatSchema: Schema[DynamicSchemaExpr.StringConcat] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.StringConcat](
      fields = Vector(
        new Reflect.Deferred(() => dynamicSchemaExprSchema.reflect).asTerm("left"),
        new Reflect.Deferred(() => dynamicSchemaExprSchema.reflect).asTerm("right")
      ),
      typeId = TypeId.of[DynamicSchemaExpr.StringConcat],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicSchemaExpr.StringConcat] {
          def usedRegisters: RegisterOffset                                                    = 2
          def construct(in: Registers, offset: RegisterOffset): DynamicSchemaExpr.StringConcat =
            DynamicSchemaExpr.StringConcat(
              in.getObject(offset + 0).asInstanceOf[DynamicSchemaExpr],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[DynamicSchemaExpr.StringConcat] {
          def usedRegisters: RegisterOffset                                                                 = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicSchemaExpr.StringConcat): Unit = {
            out.setObject(offset + 0, in.left)
            out.setObject(offset + 1, in.right)
          }
        }
      )
    )
  )

  implicit lazy val stringLengthSchema: Schema[DynamicSchemaExpr.StringLength] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.StringLength](
      fields = Vector(
        new Reflect.Deferred(() => dynamicSchemaExprSchema.reflect).asTerm("expr")
      ),
      typeId = TypeId.of[DynamicSchemaExpr.StringLength],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicSchemaExpr.StringLength] {
          def usedRegisters: RegisterOffset                                                    = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicSchemaExpr.StringLength =
            DynamicSchemaExpr.StringLength(in.getObject(offset + 0).asInstanceOf[DynamicSchemaExpr])
        },
        deconstructor = new Deconstructor[DynamicSchemaExpr.StringLength] {
          def usedRegisters: RegisterOffset                                                                 = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicSchemaExpr.StringLength): Unit =
            out.setObject(offset + 0, in.expr)
        }
      )
    )
  )

  implicit lazy val coercePrimitiveSchema: Schema[DynamicSchemaExpr.CoercePrimitive] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.CoercePrimitive](
      fields = Vector(
        new Reflect.Deferred(() => dynamicSchemaExprSchema.reflect).asTerm("expr"),
        Schema[String].reflect.asTerm("targetType")
      ),
      typeId = TypeId.of[DynamicSchemaExpr.CoercePrimitive],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicSchemaExpr.CoercePrimitive] {
          def usedRegisters: RegisterOffset                                                       = 2
          def construct(in: Registers, offset: RegisterOffset): DynamicSchemaExpr.CoercePrimitive =
            DynamicSchemaExpr.CoercePrimitive(
              in.getObject(offset + 0).asInstanceOf[DynamicSchemaExpr],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[DynamicSchemaExpr.CoercePrimitive] {
          def usedRegisters: RegisterOffset                                                                    = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicSchemaExpr.CoercePrimitive): Unit = {
            out.setObject(offset + 0, in.expr)
            out.setObject(offset + 1, in.targetType)
          }
        }
      )
    )
  )

  /** Schema for the DynamicSchemaExpr sealed trait */
  implicit lazy val dynamicSchemaExprSchema: Schema[DynamicSchemaExpr] = new Schema(
    reflect = new Reflect.Variant[Binding, DynamicSchemaExpr](
      cases = Vector(
        literalSchema.reflect.asTerm("Literal"),
        pathExprSchema.reflect.asTerm("Path"),
        defaultValueExprSchema.reflect.asTerm("DefaultValue"),
        resolvedDefaultSchema.reflect.asTerm("ResolvedDefault"),
        notExprSchema.reflect.asTerm("Not"),
        logicalExprSchema.reflect.asTerm("Logical"),
        relationalExprSchema.reflect.asTerm("Relational"),
        arithmeticExprSchema.reflect.asTerm("Arithmetic"),
        stringConcatSchema.reflect.asTerm("StringConcat"),
        stringLengthSchema.reflect.asTerm("StringLength"),
        coercePrimitiveSchema.reflect.asTerm("CoercePrimitive")
      ),
      typeId = TypeId.of[DynamicSchemaExpr],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[DynamicSchemaExpr] {
          def discriminate(a: DynamicSchemaExpr): Int = a match {
            case _: DynamicSchemaExpr.Literal         => 0
            case _: DynamicSchemaExpr.Path            => 1
            case DynamicSchemaExpr.DefaultValue       => 2
            case _: DynamicSchemaExpr.ResolvedDefault => 3
            case _: DynamicSchemaExpr.Not             => 4
            case _: DynamicSchemaExpr.Logical         => 5
            case _: DynamicSchemaExpr.Relational      => 6
            case _: DynamicSchemaExpr.Arithmetic      => 7
            case _: DynamicSchemaExpr.StringConcat    => 8
            case _: DynamicSchemaExpr.StringLength    => 9
            case _: DynamicSchemaExpr.CoercePrimitive => 10
          }
        },
        matchers = Matchers(
          new Matcher[DynamicSchemaExpr.Literal] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.Literal = a match {
              case x: DynamicSchemaExpr.Literal => x
              case _                            => null.asInstanceOf[DynamicSchemaExpr.Literal]
            }
          },
          new Matcher[DynamicSchemaExpr.Path] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.Path = a match {
              case x: DynamicSchemaExpr.Path => x
              case _                         => null.asInstanceOf[DynamicSchemaExpr.Path]
            }
          },
          new Matcher[DynamicSchemaExpr.DefaultValue.type] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.DefaultValue.type = a match {
              case x: DynamicSchemaExpr.DefaultValue.type => x
              case _                                      => null.asInstanceOf[DynamicSchemaExpr.DefaultValue.type]
            }
          },
          new Matcher[DynamicSchemaExpr.ResolvedDefault] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.ResolvedDefault = a match {
              case x: DynamicSchemaExpr.ResolvedDefault => x
              case _                                    => null.asInstanceOf[DynamicSchemaExpr.ResolvedDefault]
            }
          },
          new Matcher[DynamicSchemaExpr.Not] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.Not = a match {
              case x: DynamicSchemaExpr.Not => x
              case _                        => null.asInstanceOf[DynamicSchemaExpr.Not]
            }
          },
          new Matcher[DynamicSchemaExpr.Logical] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.Logical = a match {
              case x: DynamicSchemaExpr.Logical => x
              case _                            => null.asInstanceOf[DynamicSchemaExpr.Logical]
            }
          },
          new Matcher[DynamicSchemaExpr.Relational] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.Relational = a match {
              case x: DynamicSchemaExpr.Relational => x
              case _                               => null.asInstanceOf[DynamicSchemaExpr.Relational]
            }
          },
          new Matcher[DynamicSchemaExpr.Arithmetic] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.Arithmetic = a match {
              case x: DynamicSchemaExpr.Arithmetic => x
              case _                               => null.asInstanceOf[DynamicSchemaExpr.Arithmetic]
            }
          },
          new Matcher[DynamicSchemaExpr.StringConcat] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.StringConcat = a match {
              case x: DynamicSchemaExpr.StringConcat => x
              case _                                 => null.asInstanceOf[DynamicSchemaExpr.StringConcat]
            }
          },
          new Matcher[DynamicSchemaExpr.StringLength] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.StringLength = a match {
              case x: DynamicSchemaExpr.StringLength => x
              case _                                 => null.asInstanceOf[DynamicSchemaExpr.StringLength]
            }
          },
          new Matcher[DynamicSchemaExpr.CoercePrimitive] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.CoercePrimitive = a match {
              case x: DynamicSchemaExpr.CoercePrimitive => x
              case _                                    => null.asInstanceOf[DynamicSchemaExpr.CoercePrimitive]
            }
          }
        )
      )
    )
  )

  // ==================== MigrationAction Schemas ====================

  implicit lazy val addFieldSchema: Schema[MigrationAction.AddField] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.AddField](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("name"),
        dynamicSchemaExprSchema.reflect.asTerm("default")
      ),
      typeId = TypeId.of[MigrationAction.AddField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.AddField] {
          def usedRegisters: RegisterOffset                                              = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.AddField =
            MigrationAction.AddField(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.AddField] {
          def usedRegisters: RegisterOffset                                                           = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.AddField): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.name)
            out.setObject(offset + 2, in.default)
          }
        }
      )
    )
  )

  implicit lazy val dropFieldSchema: Schema[MigrationAction.DropField] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.DropField](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("name"),
        dynamicSchemaExprSchema.reflect.asTerm("defaultForReverse")
      ),
      typeId = TypeId.of[MigrationAction.DropField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.DropField] {
          def usedRegisters: RegisterOffset                                               = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.DropField =
            MigrationAction.DropField(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.DropField] {
          def usedRegisters: RegisterOffset                                                            = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.DropField): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.name)
            out.setObject(offset + 2, in.defaultForReverse)
          }
        }
      )
    )
  )

  implicit lazy val renameFieldSchema: Schema[MigrationAction.RenameField] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.RenameField](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("from"),
        Schema[String].reflect.asTerm("to")
      ),
      typeId = TypeId.of[MigrationAction.RenameField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.RenameField] {
          def usedRegisters: RegisterOffset                                                 = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.RenameField =
            MigrationAction.RenameField(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.RenameField] {
          def usedRegisters: RegisterOffset                                                              = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.RenameField): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.from)
            out.setObject(offset + 2, in.to)
          }
        }
      )
    )
  )

  implicit lazy val transformValueSchema: Schema[MigrationAction.TransformValue] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformValue](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        dynamicSchemaExprSchema.reflect.asTerm("transform"),
        dynamicSchemaExprSchema.reflect.asTerm("reverseTransform")
      ),
      typeId = TypeId.of[MigrationAction.TransformValue],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformValue] {
          def usedRegisters: RegisterOffset                                                    = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformValue =
            MigrationAction.TransformValue(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr],
              in.getObject(offset + 2).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformValue] {
          def usedRegisters: RegisterOffset                                                                 = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformValue): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.transform)
            out.setObject(offset + 2, in.reverseTransform)
          }
        }
      )
    )
  )

  implicit lazy val mandateSchema: Schema[MigrationAction.Mandate] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.Mandate](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        dynamicSchemaExprSchema.reflect.asTerm("default")
      ),
      typeId = TypeId.of[MigrationAction.Mandate],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.Mandate] {
          def usedRegisters: RegisterOffset                                             = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.Mandate =
            MigrationAction.Mandate(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.Mandate] {
          def usedRegisters: RegisterOffset                                                          = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.Mandate): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.default)
          }
        }
      )
    )
  )

  implicit lazy val optionalizeSchema: Schema[MigrationAction.Optionalize] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.Optionalize](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at")
      ),
      typeId = TypeId.of[MigrationAction.Optionalize],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.Optionalize] {
          def usedRegisters: RegisterOffset                                                 = 1
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.Optionalize =
            MigrationAction.Optionalize(in.getObject(offset + 0).asInstanceOf[DynamicOptic])
        },
        deconstructor = new Deconstructor[MigrationAction.Optionalize] {
          def usedRegisters: RegisterOffset                                                              = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.Optionalize): Unit =
            out.setObject(offset + 0, in.at)
        }
      )
    )
  )

  implicit lazy val changeTypeSchema: Schema[MigrationAction.ChangeType] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.ChangeType](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        dynamicSchemaExprSchema.reflect.asTerm("converter"),
        dynamicSchemaExprSchema.reflect.asTerm("reverseConverter")
      ),
      typeId = TypeId.of[MigrationAction.ChangeType],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.ChangeType] {
          def usedRegisters: RegisterOffset                                                = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.ChangeType =
            MigrationAction.ChangeType(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr],
              in.getObject(offset + 2).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.ChangeType] {
          def usedRegisters: RegisterOffset                                                             = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.ChangeType): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.converter)
            out.setObject(offset + 2, in.reverseConverter)
          }
        }
      )
    )
  )

  implicit lazy val joinSchema: Schema[MigrationAction.Join] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.Join](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[Vector[DynamicOptic]].reflect.asTerm("sourcePaths"),
        dynamicSchemaExprSchema.reflect.asTerm("combiner"),
        dynamicSchemaExprSchema.reflect.asTerm("splitter")
      ),
      typeId = TypeId.of[MigrationAction.Join],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.Join] {
          def usedRegisters: RegisterOffset                                          = 4
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.Join =
            MigrationAction.Join(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[Vector[DynamicOptic]],
              in.getObject(offset + 2).asInstanceOf[DynamicSchemaExpr],
              in.getObject(offset + 3).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.Join] {
          def usedRegisters: RegisterOffset                                                       = 4
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.Join): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.sourcePaths)
            out.setObject(offset + 2, in.combiner)
            out.setObject(offset + 3, in.splitter)
          }
        }
      )
    )
  )

  implicit lazy val splitSchema: Schema[MigrationAction.Split] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.Split](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[Vector[DynamicOptic]].reflect.asTerm("targetPaths"),
        dynamicSchemaExprSchema.reflect.asTerm("splitter"),
        dynamicSchemaExprSchema.reflect.asTerm("combiner")
      ),
      typeId = TypeId.of[MigrationAction.Split],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.Split] {
          def usedRegisters: RegisterOffset                                           = 4
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.Split =
            MigrationAction.Split(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[Vector[DynamicOptic]],
              in.getObject(offset + 2).asInstanceOf[DynamicSchemaExpr],
              in.getObject(offset + 3).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.Split] {
          def usedRegisters: RegisterOffset                                                        = 4
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.Split): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.targetPaths)
            out.setObject(offset + 2, in.splitter)
            out.setObject(offset + 3, in.combiner)
          }
        }
      )
    )
  )

  implicit lazy val renameCaseSchema: Schema[MigrationAction.RenameCase] = new Schema(
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
      )
    )
  )

  implicit lazy val transformCaseSchema: Schema[MigrationAction.TransformCase] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformCase](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("caseName"),
        new Reflect.Deferred(() => Schema[Vector[MigrationAction]].reflect).asTerm("actions")
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
            out.setObject(offset + 2, in.actions)
          }
        }
      )
    )
  )

  implicit lazy val transformElementsSchema: Schema[MigrationAction.TransformElements] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformElements](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        dynamicSchemaExprSchema.reflect.asTerm("transform"),
        dynamicSchemaExprSchema.reflect.asTerm("reverseTransform")
      ),
      typeId = TypeId.of[MigrationAction.TransformElements],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformElements] {
          def usedRegisters: RegisterOffset                                                       = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformElements =
            MigrationAction.TransformElements(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr],
              in.getObject(offset + 2).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformElements] {
          def usedRegisters: RegisterOffset                                                                    = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformElements): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.transform)
            out.setObject(offset + 2, in.reverseTransform)
          }
        }
      )
    )
  )

  implicit lazy val transformKeysSchema: Schema[MigrationAction.TransformKeys] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformKeys](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        dynamicSchemaExprSchema.reflect.asTerm("transform"),
        dynamicSchemaExprSchema.reflect.asTerm("reverseTransform")
      ),
      typeId = TypeId.of[MigrationAction.TransformKeys],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformKeys] {
          def usedRegisters: RegisterOffset                                                   = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformKeys =
            MigrationAction.TransformKeys(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr],
              in.getObject(offset + 2).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformKeys] {
          def usedRegisters: RegisterOffset                                                                = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformKeys): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.transform)
            out.setObject(offset + 2, in.reverseTransform)
          }
        }
      )
    )
  )

  implicit lazy val transformValuesSchema: Schema[MigrationAction.TransformValues] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformValues](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        dynamicSchemaExprSchema.reflect.asTerm("transform"),
        dynamicSchemaExprSchema.reflect.asTerm("reverseTransform")
      ),
      typeId = TypeId.of[MigrationAction.TransformValues],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformValues] {
          def usedRegisters: RegisterOffset                                                     = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformValues =
            MigrationAction.TransformValues(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr],
              in.getObject(offset + 2).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformValues] {
          def usedRegisters: RegisterOffset                                                                  = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformValues): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.transform)
            out.setObject(offset + 2, in.reverseTransform)
          }
        }
      )
    )
  )

  implicit lazy val identityActionSchema: Schema[MigrationAction.Identity.type] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.Identity.type](
      fields = Vector.empty,
      typeId = TypeId.of[MigrationAction.Identity.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor(MigrationAction.Identity),
        deconstructor = new ConstantDeconstructor
      )
    )
  )

  /** Schema for the MigrationAction sealed trait */
  implicit lazy val migrationActionSchema: Schema[MigrationAction] = new Schema(
    reflect = new Reflect.Variant[Binding, MigrationAction](
      cases = Vector(
        addFieldSchema.reflect.asTerm("AddField"),
        dropFieldSchema.reflect.asTerm("DropField"),
        renameFieldSchema.reflect.asTerm("RenameField"),
        transformValueSchema.reflect.asTerm("TransformValue"),
        mandateSchema.reflect.asTerm("Mandate"),
        optionalizeSchema.reflect.asTerm("Optionalize"),
        changeTypeSchema.reflect.asTerm("ChangeType"),
        joinSchema.reflect.asTerm("Join"),
        splitSchema.reflect.asTerm("Split"),
        renameCaseSchema.reflect.asTerm("RenameCase"),
        transformCaseSchema.reflect.asTerm("TransformCase"),
        transformElementsSchema.reflect.asTerm("TransformElements"),
        transformKeysSchema.reflect.asTerm("TransformKeys"),
        transformValuesSchema.reflect.asTerm("TransformValues"),
        identityActionSchema.reflect.asTerm("Identity")
      ),
      typeId = TypeId.of[MigrationAction],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MigrationAction] {
          def discriminate(a: MigrationAction): Int = a match {
            case _: MigrationAction.AddField          => 0
            case _: MigrationAction.DropField         => 1
            case _: MigrationAction.RenameField       => 2
            case _: MigrationAction.TransformValue    => 3
            case _: MigrationAction.Mandate           => 4
            case _: MigrationAction.Optionalize       => 5
            case _: MigrationAction.ChangeType        => 6
            case _: MigrationAction.Join              => 7
            case _: MigrationAction.Split             => 8
            case _: MigrationAction.RenameCase        => 9
            case _: MigrationAction.TransformCase     => 10
            case _: MigrationAction.TransformElements => 11
            case _: MigrationAction.TransformKeys     => 12
            case _: MigrationAction.TransformValues   => 13
            case MigrationAction.Identity             => 14
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
          new Matcher[MigrationAction.RenameField] {
            def downcastOrNull(a: Any): MigrationAction.RenameField = a match {
              case x: MigrationAction.RenameField => x
              case _                              => null.asInstanceOf[MigrationAction.RenameField]
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
          new Matcher[MigrationAction.Join] {
            def downcastOrNull(a: Any): MigrationAction.Join = a match {
              case x: MigrationAction.Join => x
              case _                       => null.asInstanceOf[MigrationAction.Join]
            }
          },
          new Matcher[MigrationAction.Split] {
            def downcastOrNull(a: Any): MigrationAction.Split = a match {
              case x: MigrationAction.Split => x
              case _                        => null.asInstanceOf[MigrationAction.Split]
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
          new Matcher[MigrationAction.Identity.type] {
            def downcastOrNull(a: Any): MigrationAction.Identity.type = a match {
              case x: MigrationAction.Identity.type => x
              case _                                => null.asInstanceOf[MigrationAction.Identity.type]
            }
          }
        )
      )
    )
  )

  // ==================== DynamicMigration Schema ====================

  /** Schema for DynamicMigration */
  implicit lazy val dynamicMigrationSchema: Schema[DynamicMigration] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicMigration](
      fields = Vector(
        Schema[Vector[MigrationAction]].reflect.asTerm("actions")
      ),
      typeId = TypeId.of[DynamicMigration],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicMigration] {
          def usedRegisters: RegisterOffset                                      = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicMigration =
            new DynamicMigration(in.getObject(offset + 0).asInstanceOf[Vector[MigrationAction]])
        },
        deconstructor = new Deconstructor[DynamicMigration] {
          def usedRegisters: RegisterOffset                                                   = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicMigration): Unit =
            out.setObject(offset + 0, in.actions)
        }
      )
    )
  )
}
