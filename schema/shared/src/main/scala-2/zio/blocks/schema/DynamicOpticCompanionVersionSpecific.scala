package zio.blocks.schema

import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

trait DynamicOpticCompanionVersionSpecific {

  import DynamicOptic.Node

  // Schema instances - manually written for Scala 2 compatibility
  // For easier testing in Scala 3, you can use schema.derive for all schemas.
  // example - `implicit lazy val dynamicOpticSchema: Schema[DynamicOptic] = Schema.derived`

  // Schemas for case objects
  implicit lazy val elementsSchema: Schema[Node.Elements.type] = new Schema(
    reflect = new Reflect.Record[Binding, Node.Elements.type](
      fields = Vector.empty,
      typeId = TypeId.of[Node.Elements.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Node.Elements.type](Node.Elements),
        deconstructor = new ConstantDeconstructor[Node.Elements.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val mapKeysSchema: Schema[Node.MapKeys.type] = new Schema(
    reflect = new Reflect.Record[Binding, Node.MapKeys.type](
      fields = Vector.empty,
      typeId = TypeId.of[Node.MapKeys.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Node.MapKeys.type](Node.MapKeys),
        deconstructor = new ConstantDeconstructor[Node.MapKeys.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val mapValuesSchema: Schema[Node.MapValues.type] = new Schema(
    reflect = new Reflect.Record[Binding, Node.MapValues.type](
      fields = Vector.empty,
      typeId = TypeId.of[Node.MapValues.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Node.MapValues.type](Node.MapValues),
        deconstructor = new ConstantDeconstructor[Node.MapValues.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val wrappedSchema: Schema[Node.Wrapped.type] = new Schema(
    reflect = new Reflect.Record[Binding, Node.Wrapped.type](
      fields = Vector.empty,
      typeId = TypeId.of[Node.Wrapped.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Node.Wrapped.type](Node.Wrapped),
        deconstructor = new ConstantDeconstructor[Node.Wrapped.type]
      ),
      modifiers = Vector.empty
    )
  )

  // Schemas for case classes
  implicit lazy val fieldSchema: Schema[Node.Field] = new Schema(
    reflect = new Reflect.Record[Binding, Node.Field](
      fields = Vector(
        Schema[String].reflect.asTerm("name")
      ),
      typeId = TypeId.of[Node.Field],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Node.Field] {
          def usedRegisters: RegisterOffset                                = 1
          def construct(in: Registers, offset: RegisterOffset): Node.Field =
            Node.Field(in.getObject(offset + 0).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[Node.Field] {
          def usedRegisters: RegisterOffset                                             = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Node.Field): Unit =
            out.setObject(offset + 0, in.name)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val caseSchema: Schema[Node.Case] = new Schema(
    reflect = new Reflect.Record[Binding, Node.Case](
      fields = Vector(
        Schema[String].reflect.asTerm("name")
      ),
      typeId = TypeId.of[Node.Case],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Node.Case] {
          def usedRegisters: RegisterOffset                               = 1
          def construct(in: Registers, offset: RegisterOffset): Node.Case =
            Node.Case(in.getObject(offset + 0).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[Node.Case] {
          def usedRegisters: RegisterOffset                                            = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Node.Case): Unit =
            out.setObject(offset + 0, in.name)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val atIndexSchema: Schema[Node.AtIndex] = new Schema(
    reflect = new Reflect.Record[Binding, Node.AtIndex](
      fields = Vector(
        Schema[Int].reflect.asTerm("index")
      ),
      typeId = TypeId.of[Node.AtIndex],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Node.AtIndex] {
          def usedRegisters: RegisterOffset                                  = 1
          def construct(in: Registers, offset: RegisterOffset): Node.AtIndex =
            Node.AtIndex(in.getInt(offset + 0))
        },
        deconstructor = new Deconstructor[Node.AtIndex] {
          def usedRegisters: RegisterOffset                                               = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Node.AtIndex): Unit =
            out.setInt(offset + 0, in.index)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val atMapKeySchema: Schema[Node.AtMapKey] = new Schema(
    reflect = new Reflect.Record[Binding, Node.AtMapKey](
      fields = Vector(
        Schema[DynamicValue].reflect.asTerm("key")
      ),
      typeId = TypeId.of[Node.AtMapKey],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Node.AtMapKey] {
          def usedRegisters: RegisterOffset                                   = 1
          def construct(in: Registers, offset: RegisterOffset): Node.AtMapKey =
            Node.AtMapKey(in.getObject(offset + 0).asInstanceOf[DynamicValue])
        },
        deconstructor = new Deconstructor[Node.AtMapKey] {
          def usedRegisters: RegisterOffset                                                = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Node.AtMapKey): Unit =
            out.setObject(offset + 0, in.key)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val atIndicesSchema: Schema[Node.AtIndices] = new Schema(
    reflect = new Reflect.Record[Binding, Node.AtIndices](
      fields = Vector(
        Schema[Seq[Int]].reflect.asTerm("index")
      ),
      typeId = TypeId.of[Node.AtIndices],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Node.AtIndices] {
          def usedRegisters: RegisterOffset                                    = 1
          def construct(in: Registers, offset: RegisterOffset): Node.AtIndices =
            Node.AtIndices(in.getObject(offset + 0).asInstanceOf[Seq[Int]])
        },
        deconstructor = new Deconstructor[Node.AtIndices] {
          def usedRegisters: RegisterOffset                                                 = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Node.AtIndices): Unit =
            out.setObject(offset + 0, in.index)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val atMapKeysSchema: Schema[Node.AtMapKeys] = new Schema(
    reflect = new Reflect.Record[Binding, Node.AtMapKeys](
      fields = Vector(
        Schema[Seq[DynamicValue]].reflect.asTerm("keys")
      ),
      typeId = TypeId.of[Node.AtMapKeys],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Node.AtMapKeys] {
          def usedRegisters: RegisterOffset                                    = 1
          def construct(in: Registers, offset: RegisterOffset): Node.AtMapKeys =
            Node.AtMapKeys(in.getObject(offset + 0).asInstanceOf[Seq[DynamicValue]])
        },
        deconstructor = new Deconstructor[Node.AtMapKeys] {
          def usedRegisters: RegisterOffset                                                 = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Node.AtMapKeys): Unit =
            out.setObject(offset + 0, in.keys)
        }
      ),
      modifiers = Vector.empty
    )
  )

  // Schema for Node sealed trait
  implicit lazy val nodeSchema: Schema[Node] = new Schema(
    reflect = new Reflect.Variant[Binding, Node](
      cases = Vector(
        fieldSchema.reflect.asTerm("Field"),
        caseSchema.reflect.asTerm("Case"),
        atIndexSchema.reflect.asTerm("AtIndex"),
        atMapKeySchema.reflect.asTerm("AtMapKey"),
        atIndicesSchema.reflect.asTerm("AtIndices"),
        atMapKeysSchema.reflect.asTerm("AtMapKeys"),
        elementsSchema.reflect.asTerm("Elements"),
        mapKeysSchema.reflect.asTerm("MapKeys"),
        mapValuesSchema.reflect.asTerm("MapValues"),
        wrappedSchema.reflect.asTerm("Wrapped")
      ),
      typeId = TypeId.of[Node],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[Node] {
          def discriminate(a: Node): Int = a match {
            case _: Node.Field          => 0
            case _: Node.Case           => 1
            case _: Node.AtIndex        => 2
            case _: Node.AtMapKey       => 3
            case _: Node.AtIndices      => 4
            case _: Node.AtMapKeys      => 5
            case _: Node.Elements.type  => 6
            case _: Node.MapKeys.type   => 7
            case _: Node.MapValues.type => 8
            case _: Node.Wrapped.type   => 9
          }
        },
        matchers = Matchers(
          new Matcher[Node.Field] {
            def downcastOrNull(a: Any): Node.Field = a match {
              case x: Node.Field => x
              case _             => null.asInstanceOf[Node.Field]
            }
          },
          new Matcher[Node.Case] {
            def downcastOrNull(a: Any): Node.Case = a match {
              case x: Node.Case => x
              case _            => null.asInstanceOf[Node.Case]
            }
          },
          new Matcher[Node.AtIndex] {
            def downcastOrNull(a: Any): Node.AtIndex = a match {
              case x: Node.AtIndex => x
              case _               => null.asInstanceOf[Node.AtIndex]
            }
          },
          new Matcher[Node.AtMapKey] {
            def downcastOrNull(a: Any): Node.AtMapKey = a match {
              case x: Node.AtMapKey => x
              case _                => null.asInstanceOf[Node.AtMapKey]
            }
          },
          new Matcher[Node.AtIndices] {
            def downcastOrNull(a: Any): Node.AtIndices = a match {
              case x: Node.AtIndices => x
              case _                 => null.asInstanceOf[Node.AtIndices]
            }
          },
          new Matcher[Node.AtMapKeys] {
            def downcastOrNull(a: Any): Node.AtMapKeys = a match {
              case x: Node.AtMapKeys => x
              case _                 => null.asInstanceOf[Node.AtMapKeys]
            }
          },
          new Matcher[Node.Elements.type] {
            def downcastOrNull(a: Any): Node.Elements.type = a match {
              case x: Node.Elements.type => x
              case _                     => null.asInstanceOf[Node.Elements.type]
            }
          },
          new Matcher[Node.MapKeys.type] {
            def downcastOrNull(a: Any): Node.MapKeys.type = a match {
              case x: Node.MapKeys.type => x
              case _                    => null.asInstanceOf[Node.MapKeys.type]
            }
          },
          new Matcher[Node.MapValues.type] {
            def downcastOrNull(a: Any): Node.MapValues.type = a match {
              case x: Node.MapValues.type => x
              case _                      => null.asInstanceOf[Node.MapValues.type]
            }
          },
          new Matcher[Node.Wrapped.type] {
            def downcastOrNull(a: Any): Node.Wrapped.type = a match {
              case x: Node.Wrapped.type => x
              case _                    => null.asInstanceOf[Node.Wrapped.type]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )

  // Schema for DynamicOptic
  implicit lazy val schema: Schema[DynamicOptic] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicOptic](
      fields = Vector(
        Schema[IndexedSeq[Node]].reflect.asTerm("nodes")
      ),
      typeId = TypeId.of[DynamicOptic],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicOptic] {
          def usedRegisters: RegisterOffset                                  = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicOptic =
            DynamicOptic(in.getObject(offset + 0).asInstanceOf[IndexedSeq[Node]])
        },
        deconstructor = new Deconstructor[DynamicOptic] {
          def usedRegisters: RegisterOffset                                               = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicOptic): Unit =
            out.setObject(offset + 0, in.nodes)
        }
      ),
      modifiers = Vector.empty
    )
  )
}
