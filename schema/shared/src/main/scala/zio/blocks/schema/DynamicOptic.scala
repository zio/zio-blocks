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

package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.json.JsonCodec
import zio.blocks.typeid.TypeId

case class DynamicOptic(nodes: IndexedSeq[DynamicOptic.Node]) {
  import DynamicOptic.Node

  def apply(that: DynamicOptic): DynamicOptic = new DynamicOptic(nodes ++ that.nodes)

  def apply[F[_, _], A](reflect: Reflect[F, A]): Option[Reflect[F, ?]] = reflect.get(this)

  def field(name: String): DynamicOptic = new DynamicOptic(nodes.appended(new Node.Field(name)))

  def caseOf(name: String): DynamicOptic = new DynamicOptic(nodes.appended(new Node.Case(name)))

  def at(index: Int): DynamicOptic = new DynamicOptic(nodes.appended(new Node.AtIndex(index)))

  def atIndices(indices: Int*): DynamicOptic = new DynamicOptic(nodes.appended(new Node.AtIndices(indices)))

  def atKey[K](key: K)(implicit schema: Schema[K]): DynamicOptic =
    new DynamicOptic(nodes.appended(new Node.AtMapKey(schema.toDynamicValue(key))))

  def atKeys[K](keys: K*)(implicit schema: Schema[K]): DynamicOptic =
    new DynamicOptic(nodes.appended(new Node.AtMapKeys(keys.map(schema.toDynamicValue))))

  def elements: DynamicOptic = new DynamicOptic(nodes.appended(Node.Elements))

  def mapKeys: DynamicOptic = new DynamicOptic(nodes.appended(Node.MapKeys))

  def mapValues: DynamicOptic = new DynamicOptic(nodes.appended(Node.MapValues))

  def wrapped: DynamicOptic = new DynamicOptic(nodes.appended(Node.Wrapped))

  /**
   * Appends a search node that matches all values of the specified nominal
   * type.
   */
  def search[A](implicit typeId: TypeId[A]): DynamicOptic =
    new DynamicOptic(nodes :+ Node.TypeSearch(typeId))

  /**
   * Appends a search node that matches all values matching the specified schema
   * pattern.
   */
  def searchSchema(schemaRepr: SchemaRepr): DynamicOptic =
    new DynamicOptic(nodes :+ Node.SchemaSearch(schemaRepr))

  override lazy val toString: String = {
    val sb = new java.lang.StringBuilder
    DynamicOptic.renderString(sb, nodes)
    sb.toString
  }

  /**
   * Renders this optic path using Scala-style method syntax (e.g.,
   * `.when[Case]`, `.each`, `.atKey(<key>)`). This is used for typed Optic
   * error messages, as opposed to `toString` which uses the compact
   * interpolator syntax.
   */
  lazy val toScalaString: String = {
    val sb = new java.lang.StringBuilder
    DynamicOptic.renderScalaString(sb, nodes)
    sb.toString
  }

}

object DynamicOptic {
  private[schema] def renderString(sb: java.lang.StringBuilder, nodes: IndexedSeq[DynamicOptic.Node]): Unit = {
    val len = nodes.length
    if (len == 0) sb.append('.')
    else {
      var idx = 0
      while (idx < len) {
        nodes(idx) match {
          case f: Node.Field      => sb.append('.').append(f.name)
          case c: Node.Case       => sb.append('<').append(c.name).append('>')
          case ai: Node.AtIndex   => sb.append('[').append(ai.index).append(']')
          case ai: Node.AtIndices =>
            sb.append('[')
            val indices = ai.index
            val idxLen  = indices.length
            var i       = 0
            while (i < idxLen) {
              if (i > 0) sb.append(',')
              sb.append(indices(i))
              i += 1
            }
            sb.append(']')
          case amk: Node.AtMapKey =>
            sb.append('{')
            renderDynamicValue(sb, amk.key)
            sb.append('}')
          case amk: Node.AtMapKeys =>
            sb.append('{')
            val keys   = amk.keys
            val keyLen = keys.length
            var i      = 0
            while (i < keyLen) {
              if (i > 0) sb.append(", ")
              renderDynamicValue(sb, keys(i))
              i += 1
            }
            sb.append('}')
          case _: Node.Elements.type  => sb.append("[*]")
          case _: Node.MapKeys.type   => sb.append("{*:}")
          case _: Node.MapValues.type => sb.append("{*}")
          case _: Node.Wrapped.type   => sb.append(".~")
          case ts: Node.TypeSearch    => sb.append('#').append(ts.typeId.name)
          case ss: Node.SchemaSearch  => sb.append('#').append(ss.schemaRepr.toString)
        }
        idx += 1
      }
    }
  }

  private[schema] def renderScalaString(sb: java.lang.StringBuilder, nodes: IndexedSeq[DynamicOptic.Node]): Unit = {
    val len = nodes.length
    if (len == 0) sb.append('.')
    else {
      var idx = 0
      while (idx < len) {
        nodes(idx) match {
          case f: Node.Field      => sb.append('.').append(f.name)
          case c: Node.Case       => sb.append(".when[").append(c.name).append(']')
          case ai: Node.AtIndex   => sb.append(".at(").append(ai.index).append(')')
          case ai: Node.AtIndices =>
            sb.append(".atIndices(")
            val indices = ai.index
            val idxLen  = indices.length
            var i       = 0
            while (i < idxLen) {
              if (i > 0) sb.append(", ")
              sb.append(indices(i))
              i += 1
            }
            sb.append(')')
          case amk: Node.AtMapKey =>
            sb.append(".atKey(")
            renderDynamicValue(sb, amk.key)
            sb.append(')')
          case amk: Node.AtMapKeys =>
            sb.append(".atKeys(")
            val keys   = amk.keys
            val keyLen = keys.length
            var i      = 0
            while (i < keyLen) {
              if (i > 0) sb.append(", ")
              renderDynamicValue(sb, keys(i))
              i += 1
            }
            sb.append(')')
          case _: Node.Elements.type  => sb.append(".each")
          case _: Node.MapKeys.type   => sb.append(".eachKey")
          case _: Node.MapValues.type => sb.append(".eachValue")
          case _: Node.Wrapped.type   => sb.append(".wrapped")
          case ts: Node.TypeSearch    => sb.append(".search[").append(ts.typeId.name).append(']')
          case ss: Node.SchemaSearch  => sb.append(".searchSchema(").append(ss.schemaRepr.toString).append(')')
        }
        idx += 1
      }
    }
  }

  private[this] def renderDynamicValue(sb: java.lang.StringBuilder, value: DynamicValue): Unit =
    value match {
      case pv: DynamicValue.Primitive =>
        pv.value match {
          case v: PrimitiveValue.String =>
            val s = v.value
            sb.append('"')
            var i = 0
            while (i < s.length) {
              s.charAt(i) match {
                case '\n' => sb.append("\\n")
                case '\t' => sb.append("\\t")
                case '\r' => sb.append("\\r")
                case '"'  => sb.append("\\\"")
                case '\\' => sb.append("\\\\")
                case c    => sb.append(c)
              }
              i += 1
            }
            sb.append('"')
          case v: PrimitiveValue.Boolean => sb.append(v.value)
          case v: PrimitiveValue.Char    =>
            sb.append('\'')
            v.value match {
              case '\n' => sb.append("\\\\n")
              case '\t' => sb.append("\\\\t")
              case '\r' => sb.append("\\\\r")
              case '\'' => sb.append("\\\\'")
              case '\\' => sb.append("\\\\\\\\")
              case char => sb.append(char)
            }
            sb.append('\'')
          case v: PrimitiveValue.Byte       => sb.append(v.value)
          case v: PrimitiveValue.Short      => sb.append(v.value)
          case v: PrimitiveValue.Int        => sb.append(v.value)
          case v: PrimitiveValue.Long       => sb.append(v.value)
          case v: PrimitiveValue.Float      => sb.append(JsonCodec.floatCodec.encodeToString(v.value))
          case v: PrimitiveValue.Double     => sb.append(JsonCodec.doubleCodec.encodeToString(v.value))
          case v: PrimitiveValue.BigInt     => sb.append(JsonCodec.bigIntCodec.encodeToString(v.value))
          case v: PrimitiveValue.BigDecimal => sb.append(JsonCodec.bigDecimalCodec.encodeToString(v.value))
          case _                            => sb.append(pv.toString)
        }
      case _ => sb.append(value.toString)
    }

  val root: DynamicOptic = new DynamicOptic(Chunk.empty)

  val elements: DynamicOptic = new DynamicOptic(Chunk.single(Node.Elements))

  val mapKeys: DynamicOptic = new DynamicOptic(Chunk.single(Node.MapKeys))

  val mapValues: DynamicOptic = new DynamicOptic(Chunk.single(Node.MapValues))

  val wrapped: DynamicOptic = new DynamicOptic(Chunk.single(Node.Wrapped))

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

    /**
     * Search for all values matching a nominal type by TypeId.
     */
    case class TypeSearch(typeId: TypeId[?]) extends Node

    /**
     * Search for all values matching a structural schema pattern.
     */
    case class SchemaSearch(schemaRepr: SchemaRepr) extends Node
  }

  // Schema Definitions, Manual derivation for Scala 2 compatability
  // In Scala 3, You can derive Schema using `Schema.derive`

  // Schema for case objects
  implicit lazy val elementsSchema: Schema[Node.Elements.type] = new Schema(
    reflect = new Reflect.Record[Binding, Node.Elements.type](
      fields = Chunk.empty,
      typeId = TypeId.of[Node.Elements.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Node.Elements.type](Node.Elements),
        deconstructor = new ConstantDeconstructor[Node.Elements.type]
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val mapKeysSchema: Schema[Node.MapKeys.type] = new Schema(
    reflect = new Reflect.Record[Binding, Node.MapKeys.type](
      fields = Chunk.empty,
      typeId = TypeId.of[Node.MapKeys.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Node.MapKeys.type](Node.MapKeys),
        deconstructor = new ConstantDeconstructor[Node.MapKeys.type]
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val mapValuesSchema: Schema[Node.MapValues.type] = new Schema(
    reflect = new Reflect.Record[Binding, Node.MapValues.type](
      fields = Chunk.empty,
      typeId = TypeId.of[Node.MapValues.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Node.MapValues.type](Node.MapValues),
        deconstructor = new ConstantDeconstructor[Node.MapValues.type]
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val wrappedSchema: Schema[Node.Wrapped.type] = new Schema(
    reflect = new Reflect.Record[Binding, Node.Wrapped.type](
      fields = Chunk.empty,
      typeId = TypeId.of[Node.Wrapped.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Node.Wrapped.type](Node.Wrapped),
        deconstructor = new ConstantDeconstructor[Node.Wrapped.type]
      ),
      modifiers = Chunk.empty
    )
  )

  // Schemas for case classes
  implicit lazy val fieldSchema: Schema[Node.Field] = new Schema(
    reflect = new Reflect.Record[Binding, Node.Field](
      fields = Chunk.single(Schema[String].reflect.asTerm("name")),
      typeId = TypeId.of[Node.Field],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Node.Field] {
          def usedRegisters: RegisterOffset                                = 1
          def construct(in: Registers, offset: RegisterOffset): Node.Field =
            new Node.Field(in.getObject(offset + 0).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[Node.Field] {
          def usedRegisters: RegisterOffset                                             = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Node.Field): Unit =
            out.setObject(offset + 0, in.name)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val caseSchema: Schema[Node.Case] = new Schema(
    reflect = new Reflect.Record[Binding, Node.Case](
      fields = Chunk.single(Schema[String].reflect.asTerm("name")),
      typeId = TypeId.of[Node.Case],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Node.Case] {
          def usedRegisters: RegisterOffset                               = 1
          def construct(in: Registers, offset: RegisterOffset): Node.Case =
            new Node.Case(in.getObject(offset + 0).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[Node.Case] {
          def usedRegisters: RegisterOffset                                            = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Node.Case): Unit =
            out.setObject(offset + 0, in.name)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val atIndexSchema: Schema[Node.AtIndex] = new Schema(
    reflect = new Reflect.Record[Binding, Node.AtIndex](
      fields = Chunk.single(Schema[Int].reflect.asTerm("index")),
      typeId = TypeId.of[Node.AtIndex],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Node.AtIndex] {
          def usedRegisters: RegisterOffset                                  = 1
          def construct(in: Registers, offset: RegisterOffset): Node.AtIndex =
            new Node.AtIndex(in.getInt(offset + 0))
        },
        deconstructor = new Deconstructor[Node.AtIndex] {
          def usedRegisters: RegisterOffset                                               = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Node.AtIndex): Unit =
            out.setInt(offset + 0, in.index)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val atMapKeySchema: Schema[Node.AtMapKey] = new Schema(
    reflect = new Reflect.Record[Binding, Node.AtMapKey](
      fields = Chunk.single(Schema[DynamicValue].reflect.asTerm("key")),
      typeId = TypeId.of[Node.AtMapKey],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Node.AtMapKey] {
          def usedRegisters: RegisterOffset                                   = 1
          def construct(in: Registers, offset: RegisterOffset): Node.AtMapKey =
            new Node.AtMapKey(in.getObject(offset + 0).asInstanceOf[DynamicValue])
        },
        deconstructor = new Deconstructor[Node.AtMapKey] {
          def usedRegisters: RegisterOffset                                                = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Node.AtMapKey): Unit =
            out.setObject(offset + 0, in.key)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val atIndicesSchema: Schema[Node.AtIndices] = new Schema(
    reflect = new Reflect.Record[Binding, Node.AtIndices](
      fields = Chunk.single(Schema[Seq[Int]].reflect.asTerm("index")),
      typeId = TypeId.of[Node.AtIndices],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Node.AtIndices] {
          def usedRegisters: RegisterOffset                                    = 1
          def construct(in: Registers, offset: RegisterOffset): Node.AtIndices =
            new Node.AtIndices(in.getObject(offset + 0).asInstanceOf[Seq[Int]])
        },
        deconstructor = new Deconstructor[Node.AtIndices] {
          def usedRegisters: RegisterOffset                                                 = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Node.AtIndices): Unit =
            out.setObject(offset + 0, in.index)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val atMapKeysSchema: Schema[Node.AtMapKeys] = new Schema(
    reflect = new Reflect.Record[Binding, Node.AtMapKeys](
      fields = Chunk.single(Schema[Seq[DynamicValue]].reflect.asTerm("keys")),
      typeId = TypeId.of[Node.AtMapKeys],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Node.AtMapKeys] {
          def usedRegisters: RegisterOffset                                    = 1
          def construct(in: Registers, offset: RegisterOffset): Node.AtMapKeys =
            new Node.AtMapKeys(in.getObject(offset + 0).asInstanceOf[Seq[DynamicValue]])
        },
        deconstructor = new Deconstructor[Node.AtMapKeys] {
          def usedRegisters: RegisterOffset                                                 = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Node.AtMapKeys): Unit =
            out.setObject(offset + 0, in.keys)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val typeSearchSchema: Schema[Node.TypeSearch] = new Schema(
    reflect = new Reflect.Record[Binding, Node.TypeSearch](
      fields = Chunk.single(
        Schema[TypeId[?]].reflect.asTerm("typeId")
      ),
      typeId = TypeId.of[Node.TypeSearch],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Node.TypeSearch] {
          def usedRegisters: RegisterOffset                                     = 1
          def construct(in: Registers, offset: RegisterOffset): Node.TypeSearch =
            Node.TypeSearch(in.getObject(offset + 0).asInstanceOf[TypeId[?]])
        },
        deconstructor = new Deconstructor[Node.TypeSearch] {
          def usedRegisters: RegisterOffset                                                  = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Node.TypeSearch): Unit =
            out.setObject(offset + 0, in.typeId)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schemaSearchSchema: Schema[Node.SchemaSearch] = new Schema(
    reflect = new Reflect.Record[Binding, Node.SchemaSearch](
      fields = Chunk.single(
        Schema[SchemaRepr].reflect.asTerm("schemaRepr")
      ),
      typeId = TypeId.of[Node.SchemaSearch],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Node.SchemaSearch] {
          def usedRegisters: RegisterOffset                                       = 1
          def construct(in: Registers, offset: RegisterOffset): Node.SchemaSearch =
            Node.SchemaSearch(in.getObject(offset + 0).asInstanceOf[SchemaRepr])
        },
        deconstructor = new Deconstructor[Node.SchemaSearch] {
          def usedRegisters: RegisterOffset                                                    = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Node.SchemaSearch): Unit =
            out.setObject(offset + 0, in.schemaRepr)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // Schema for Node sealed trait
  implicit lazy val nodeSchema: Schema[Node] = new Schema(
    reflect = new Reflect.Variant[Binding, Node](
      cases = Chunk(
        fieldSchema.reflect.asTerm("Field"),
        caseSchema.reflect.asTerm("Case"),
        atIndexSchema.reflect.asTerm("AtIndex"),
        atMapKeySchema.reflect.asTerm("AtMapKey"),
        atIndicesSchema.reflect.asTerm("AtIndices"),
        atMapKeysSchema.reflect.asTerm("AtMapKeys"),
        elementsSchema.reflect.asTerm("Elements"),
        mapKeysSchema.reflect.asTerm("MapKeys"),
        mapValuesSchema.reflect.asTerm("MapValues"),
        wrappedSchema.reflect.asTerm("Wrapped"),
        typeSearchSchema.reflect.asTerm("TypeSearch"),
        schemaSearchSchema.reflect.asTerm("SchemaSearch")
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
            case _: Node.TypeSearch     => 10
            case _: Node.SchemaSearch   => 11
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
          },
          new Matcher[Node.TypeSearch] {
            def downcastOrNull(a: Any): Node.TypeSearch = a match {
              case x: Node.TypeSearch => x
              case _                  => null.asInstanceOf[Node.TypeSearch]
            }
          },
          new Matcher[Node.SchemaSearch] {
            def downcastOrNull(a: Any): Node.SchemaSearch = a match {
              case x: Node.SchemaSearch => x
              case _                    => null.asInstanceOf[Node.SchemaSearch]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  // Schema for DynamicOptic
  implicit lazy val schema: Schema[DynamicOptic] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicOptic](
      fields = Chunk.single(Schema[IndexedSeq[Node]].reflect.asTerm("nodes")),
      typeId = TypeId.of[DynamicOptic],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicOptic] {
          def usedRegisters: RegisterOffset                                  = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicOptic =
            new DynamicOptic(in.getObject(offset + 0).asInstanceOf[IndexedSeq[Node]])
        },
        deconstructor = new Deconstructor[DynamicOptic] {
          def usedRegisters: RegisterOffset                                               = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicOptic): Unit =
            out.setObject(offset + 0, in.nodes)
        }
      ),
      modifiers = Chunk.empty
    )
  )
}
