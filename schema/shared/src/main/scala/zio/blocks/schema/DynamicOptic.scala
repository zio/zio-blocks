package zio.blocks.schema

import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

case class DynamicOptic(nodes: IndexedSeq[DynamicOptic.Node]) {
  import DynamicOptic.Node

  def apply(that: DynamicOptic): DynamicOptic = new DynamicOptic(nodes ++ that.nodes)

  def apply[F[_, _], A](reflect: Reflect[F, A]): Option[Reflect[F, ?]] = reflect.get(this)

  def field(name: String): DynamicOptic = new DynamicOptic(nodes :+ Node.Field(name))

  def caseOf(name: String): DynamicOptic = new DynamicOptic(nodes :+ Node.Case(name))

  def at(index: Int): DynamicOptic = new DynamicOptic(nodes :+ Node.AtIndex(index))

  def atIndices(indices: Int*): DynamicOptic = new DynamicOptic(nodes :+ Node.AtIndices(indices))

  def atKey[K](key: K)(implicit schema: Schema[K]): DynamicOptic =
    new DynamicOptic(nodes :+ Node.AtMapKey(schema.toDynamicValue(key)))

  def atKeys[K](keys: K*)(implicit schema: Schema[K]): DynamicOptic =
    new DynamicOptic(nodes :+ Node.AtMapKeys(keys.map(schema.toDynamicValue)))

  def elements: DynamicOptic = new DynamicOptic(nodes :+ Node.Elements)

  def mapKeys: DynamicOptic = new DynamicOptic(nodes :+ Node.MapKeys)

  def mapValues: DynamicOptic = new DynamicOptic(nodes :+ Node.MapValues)

  def wrapped: DynamicOptic = new DynamicOptic(nodes :+ Node.Wrapped)

  override lazy val toString: String = {
    val sb  = new StringBuilder
    val len = nodes.length
    var idx = 0
    while (idx < len) {
      nodes(idx) match {
        case Node.Field(name)    => sb.append('.').append(name)
        case Node.Case(name)     => sb.append(".when[").append(name).append(']')
        case Node.AtIndex(index) => sb.append(".at(").append(index).append(')')
        case Node.AtMapKey(_)    => sb.append(".atKey(<key>)")
        case Node.AtIndices(_)   => sb.append(".atIndices(<indices>)")
        case Node.AtMapKeys(_)   => sb.append(".atKeys(<keys>)")
        case Node.Elements       => sb.append(".each")
        case Node.MapKeys        => sb.append(".eachKey")
        case Node.MapValues      => sb.append(".eachValue")
        case Node.Wrapped        => sb.append(".wrapped")
      }
      idx += 1
    }
    if (sb.isEmpty) "."
    else sb.toString
  }
}

object DynamicOptic {
  val root: DynamicOptic = new DynamicOptic(Vector.empty)

  val elements: DynamicOptic = new DynamicOptic(Vector(Node.Elements))

  val mapKeys: DynamicOptic = new DynamicOptic(Vector(Node.MapKeys))

  val mapValues: DynamicOptic = new DynamicOptic(Vector(Node.MapValues))

  val wrapped: DynamicOptic = new DynamicOptic(Vector(Node.Wrapped))

  sealed trait Node

  object Node {
    case class Field(name: String) extends Node

    case class Case(name: String) extends Node

    case class AtIndex(index: Int) extends Node

    case class AtMapKey(key: DynamicValue) extends Node

    case class AtIndices(index: Seq[Int]) extends Node

    case class AtMapKeys(keys: Seq[DynamicValue]) extends Node

    case object Elements extends Node

    case object MapKeys extends Node

    case object MapValues extends Node

    case object Wrapped extends Node

    // Schema instances - manually written for Scala 2 compatibility
    // For easier testing in Scala 3, you can use schema.derive for all schemas.
    // example - `implicit lazy val dynamicOpticSchema: Schema[DynamicOptic] = Schema.derived`

    // Schemas for case objects
    implicit lazy val elementsSchema: Schema[Elements.type] = new Schema(
      reflect = new Reflect.Record[Binding, Elements.type](
        fields = Vector.empty,
        typeId = TypeId.of[Elements.type],
        recordBinding = new Binding.Record(
          constructor = new ConstantConstructor[Elements.type](Elements),
          deconstructor = new ConstantDeconstructor[Elements.type]
        ),
        modifiers = Vector.empty
      )
    )

    implicit lazy val mapKeysSchema: Schema[MapKeys.type] = new Schema(
      reflect = new Reflect.Record[Binding, MapKeys.type](
        fields = Vector.empty,
        typeId = TypeId.of[MapKeys.type],
        recordBinding = new Binding.Record(
          constructor = new ConstantConstructor[MapKeys.type](MapKeys),
          deconstructor = new ConstantDeconstructor[MapKeys.type]
        ),
        modifiers = Vector.empty
      )
    )

    implicit lazy val mapValuesSchema: Schema[MapValues.type] = new Schema(
      reflect = new Reflect.Record[Binding, MapValues.type](
        fields = Vector.empty,
        typeId = TypeId.of[MapValues.type],
        recordBinding = new Binding.Record(
          constructor = new ConstantConstructor[MapValues.type](MapValues),
          deconstructor = new ConstantDeconstructor[MapValues.type]
        ),
        modifiers = Vector.empty
      )
    )

    implicit lazy val wrappedSchema: Schema[Wrapped.type] = new Schema(
      reflect = new Reflect.Record[Binding, Wrapped.type](
        fields = Vector.empty,
        typeId = TypeId.of[Wrapped.type],
        recordBinding = new Binding.Record(
          constructor = new ConstantConstructor[Wrapped.type](Wrapped),
          deconstructor = new ConstantDeconstructor[Wrapped.type]
        ),
        modifiers = Vector.empty
      )
    )

    // Schemas for case classes
    implicit lazy val fieldSchema: Schema[Field] = new Schema(
      reflect = new Reflect.Record[Binding, Field](
        fields = Vector(
          Schema[String].reflect.asTerm("name")
        ),
        typeId = TypeId.of[Field],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Field] {
            def usedRegisters: RegisterOffset                           = 1
            def construct(in: Registers, offset: RegisterOffset): Field =
              Field(in.getObject(offset + 0).asInstanceOf[String])
          },
          deconstructor = new Deconstructor[Field] {
            def usedRegisters: RegisterOffset                                        = 1
            def deconstruct(out: Registers, offset: RegisterOffset, in: Field): Unit =
              out.setObject(offset + 0, in.name)
          }
        ),
        modifiers = Vector.empty
      )
    )

    implicit lazy val caseSchema: Schema[Case] = new Schema(
      reflect = new Reflect.Record[Binding, Case](
        fields = Vector(
          Schema[String].reflect.asTerm("name")
        ),
        typeId = TypeId.of[Case],
        recordBinding = new Binding.Record(
          constructor = new Constructor[Case] {
            def usedRegisters: RegisterOffset                          = 1
            def construct(in: Registers, offset: RegisterOffset): Case =
              Case(in.getObject(offset + 0).asInstanceOf[String])
          },
          deconstructor = new Deconstructor[Case] {
            def usedRegisters: RegisterOffset                                       = 1
            def deconstruct(out: Registers, offset: RegisterOffset, in: Case): Unit =
              out.setObject(offset + 0, in.name)
          }
        ),
        modifiers = Vector.empty
      )
    )

    implicit lazy val atIndexSchema: Schema[AtIndex] = new Schema(
      reflect = new Reflect.Record[Binding, AtIndex](
        fields = Vector(
          Schema[Int].reflect.asTerm("index")
        ),
        typeId = TypeId.of[AtIndex],
        recordBinding = new Binding.Record(
          constructor = new Constructor[AtIndex] {
            def usedRegisters: RegisterOffset                             = 1
            def construct(in: Registers, offset: RegisterOffset): AtIndex =
              AtIndex(in.getInt(offset + 0))
          },
          deconstructor = new Deconstructor[AtIndex] {
            def usedRegisters: RegisterOffset                                          = 1
            def deconstruct(out: Registers, offset: RegisterOffset, in: AtIndex): Unit =
              out.setInt(offset + 0, in.index)
          }
        ),
        modifiers = Vector.empty
      )
    )

    implicit lazy val atMapKeySchema: Schema[AtMapKey] = new Schema(
      reflect = new Reflect.Record[Binding, AtMapKey](
        fields = Vector(
          Schema[DynamicValue].reflect.asTerm("key")
        ),
        typeId = TypeId.of[AtMapKey],
        recordBinding = new Binding.Record(
          constructor = new Constructor[AtMapKey] {
            def usedRegisters: RegisterOffset                              = 1
            def construct(in: Registers, offset: RegisterOffset): AtMapKey =
              AtMapKey(in.getObject(offset + 0).asInstanceOf[DynamicValue])
          },
          deconstructor = new Deconstructor[AtMapKey] {
            def usedRegisters: RegisterOffset                                           = 1
            def deconstruct(out: Registers, offset: RegisterOffset, in: AtMapKey): Unit =
              out.setObject(offset + 0, in.key)
          }
        ),
        modifiers = Vector.empty
      )
    )

    implicit lazy val atIndicesSchema: Schema[AtIndices] = new Schema(
      reflect = new Reflect.Record[Binding, AtIndices](
        fields = Vector(
          Schema[Seq[Int]].reflect.asTerm("index")
        ),
        typeId = TypeId.of[AtIndices],
        recordBinding = new Binding.Record(
          constructor = new Constructor[AtIndices] {
            def usedRegisters: RegisterOffset                               = 1
            def construct(in: Registers, offset: RegisterOffset): AtIndices =
              AtIndices(in.getObject(offset + 0).asInstanceOf[Seq[Int]])
          },
          deconstructor = new Deconstructor[AtIndices] {
            def usedRegisters: RegisterOffset                                            = 1
            def deconstruct(out: Registers, offset: RegisterOffset, in: AtIndices): Unit =
              out.setObject(offset + 0, in.index)
          }
        ),
        modifiers = Vector.empty
      )
    )

    implicit lazy val atMapKeysSchema: Schema[AtMapKeys] = new Schema(
      reflect = new Reflect.Record[Binding, AtMapKeys](
        fields = Vector(
          Schema[Seq[DynamicValue]].reflect.asTerm("keys")
        ),
        typeId = TypeId.of[AtMapKeys],
        recordBinding = new Binding.Record(
          constructor = new Constructor[AtMapKeys] {
            def usedRegisters: RegisterOffset                               = 1
            def construct(in: Registers, offset: RegisterOffset): AtMapKeys =
              AtMapKeys(in.getObject(offset + 0).asInstanceOf[Seq[DynamicValue]])
          },
          deconstructor = new Deconstructor[AtMapKeys] {
            def usedRegisters: RegisterOffset                                            = 1
            def deconstruct(out: Registers, offset: RegisterOffset, in: AtMapKeys): Unit =
              out.setObject(offset + 0, in.keys)
          }
        ),
        modifiers = Vector.empty
      )
    )

    // Schema for Node sealed trait
    implicit lazy val schema: Schema[Node] = new Schema(
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
              case _: Field          => 0
              case _: Case           => 1
              case _: AtIndex        => 2
              case _: AtMapKey       => 3
              case _: AtIndices      => 4
              case _: AtMapKeys      => 5
              case _: Elements.type  => 6
              case _: MapKeys.type   => 7
              case _: MapValues.type => 8
              case _: Wrapped.type   => 9
            }
          },
          matchers = Matchers(
            new Matcher[Field] {
              def downcastOrNull(a: Any): Field = a match {
                case x: Field => x
                case _        => null.asInstanceOf[Field]
              }
            },
            new Matcher[Case] {
              def downcastOrNull(a: Any): Case = a match {
                case x: Case => x
                case _       => null.asInstanceOf[Case]
              }
            },
            new Matcher[AtIndex] {
              def downcastOrNull(a: Any): AtIndex = a match {
                case x: AtIndex => x
                case _          => null.asInstanceOf[AtIndex]
              }
            },
            new Matcher[AtMapKey] {
              def downcastOrNull(a: Any): AtMapKey = a match {
                case x: AtMapKey => x
                case _           => null.asInstanceOf[AtMapKey]
              }
            },
            new Matcher[AtIndices] {
              def downcastOrNull(a: Any): AtIndices = a match {
                case x: AtIndices => x
                case _            => null.asInstanceOf[AtIndices]
              }
            },
            new Matcher[AtMapKeys] {
              def downcastOrNull(a: Any): AtMapKeys = a match {
                case x: AtMapKeys => x
                case _            => null.asInstanceOf[AtMapKeys]
              }
            },
            new Matcher[Elements.type] {
              def downcastOrNull(a: Any): Elements.type = a match {
                case x: Elements.type => x
                case _                => null.asInstanceOf[Elements.type]
              }
            },
            new Matcher[MapKeys.type] {
              def downcastOrNull(a: Any): MapKeys.type = a match {
                case x: MapKeys.type => x
                case _               => null.asInstanceOf[MapKeys.type]
              }
            },
            new Matcher[MapValues.type] {
              def downcastOrNull(a: Any): MapValues.type = a match {
                case x: MapValues.type => x
                case _                 => null.asInstanceOf[MapValues.type]
              }
            },
            new Matcher[Wrapped.type] {
              def downcastOrNull(a: Any): Wrapped.type = a match {
                case x: Wrapped.type => x
                case _               => null.asInstanceOf[Wrapped.type]
              }
            }
          )
        ),
        modifiers = Vector.empty
      )
    )

  }

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
