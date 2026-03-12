package zio.blocks.schema

import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

/**
 * A lightweight representation of schema structure for pattern matching in
 * search optics.
 *
 * SchemaRepr captures the shape of a type without runtime bindings, enabling
 * structural matching against DynamicValue instances.
 */
sealed trait SchemaRepr {

  /**
   * Renders this schema representation as a parseable string.
   *
   * Examples:
   *   - `Nominal("Person")` → `"Person"`
   *   - `Primitive("string")` → `"string"`
   *   - `Record(...)` → `"record { name: string, age: int }"`
   *   - `Variant(...)` → `"variant { Left: int, Right: string }"`
   *   - `Sequence(...)` → `"list(string)"`
   *   - `Map(...)` → `"map(string, int)"`
   *   - `Optional(...)` → `"option(Person)"`
   *   - `Wildcard` → `"_"`
   */
  override def toString: String = SchemaRepr.render(this)
}

object SchemaRepr {

  /**
   * A named type reference (e.g., `Person`, `Address`).
   */
  final case class Nominal(name: String) extends SchemaRepr

  /**
   * A primitive type (e.g., `string`, `int`, `boolean`).
   */
  final case class Primitive(name: String) extends SchemaRepr

  /**
   * A structural record type with named fields.
   */
  final case class Record(fields: Vector[(String, SchemaRepr)]) extends SchemaRepr

  /**
   * A structural variant (sum) type with named cases.
   */
  final case class Variant(cases: Vector[(String, SchemaRepr)]) extends SchemaRepr

  /**
   * A sequence type (list, set, array).
   */
  final case class Sequence(element: SchemaRepr) extends SchemaRepr

  /**
   * A map type with key and value schemas.
   */
  final case class Map(key: SchemaRepr, value: SchemaRepr) extends SchemaRepr

  /**
   * An optional type.
   */
  final case class Optional(inner: SchemaRepr) extends SchemaRepr

  /**
   * Matches any type.
   */
  case object Wildcard extends SchemaRepr

  /**
   * Renders a SchemaRepr to its string representation.
   */
  def render(repr: SchemaRepr): String = {
    val sb = new StringBuilder
    renderTo(sb, repr)
    sb.toString
  }

  private def renderTo(sb: StringBuilder, repr: SchemaRepr): Unit = repr match {
    case Nominal(name)   => sb.append(name)
    case Primitive(name) => sb.append(name)
    case Record(fields)  =>
      sb.append("record { ")
      var first = true
      fields.foreach { case (name, fieldRepr) =>
        if (!first) sb.append(", ")
        first = false
        sb.append(name).append(": ")
        renderTo(sb, fieldRepr)
      }
      sb.append(" }")
    case Variant(cases) =>
      sb.append("variant { ")
      var first = true
      cases.foreach { case (name, caseRepr) =>
        if (!first) sb.append(", ")
        first = false
        sb.append(name).append(": ")
        renderTo(sb, caseRepr)
      }
      sb.append(" }")
    case Sequence(element) =>
      sb.append("list(")
      renderTo(sb, element)
      sb.append(')')
    case Map(key, value) =>
      sb.append("map(")
      renderTo(sb, key)
      sb.append(", ")
      renderTo(sb, value)
      sb.append(')')
    case Optional(inner) =>
      sb.append("option(")
      renderTo(sb, inner)
      sb.append(')')
    case Wildcard => sb.append('_')
  }

  // Schema Definitions, Manual derivation for Scala 2 compatibility

  // Helper schema for (String, SchemaRepr) tuples used in Record and Variant fields
  private implicit lazy val stringSchemaReprTupleSchema: Schema[(String, SchemaRepr)] = new Schema(
    reflect = new Reflect.Record[Binding, (String, SchemaRepr)](
      fields = Vector(
        Schema[String].reflect.asTerm("_1"),
        Reflect.Deferred(() => schemaReprSchema.reflect).asTerm("_2")
      ),
      typeId = TypeId.of[(String, SchemaRepr)],
      recordBinding = new Binding.Record(
        constructor = new Constructor[(String, SchemaRepr)] {
          def usedRegisters: RegisterOffset                                          = 2
          def construct(in: Registers, offset: RegisterOffset): (String, SchemaRepr) =
            (in.getObject(offset + 0).asInstanceOf[String], in.getObject(offset + 1).asInstanceOf[SchemaRepr])
        },
        deconstructor = new Deconstructor[(String, SchemaRepr)] {
          def usedRegisters: RegisterOffset                                                       = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: (String, SchemaRepr)): Unit = {
            out.setObject(offset + 0, in._1)
            out.setObject(offset + 1, in._2)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val nominalSchema: Schema[Nominal] = new Schema(
    reflect = new Reflect.Record[Binding, Nominal](
      fields = Vector(
        Schema[String].reflect.asTerm("name")
      ),
      typeId = TypeId.of[Nominal],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Nominal] {
          def usedRegisters: RegisterOffset                             = 1
          def construct(in: Registers, offset: RegisterOffset): Nominal =
            Nominal(in.getObject(offset + 0).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[Nominal] {
          def usedRegisters: RegisterOffset                                          = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Nominal): Unit =
            out.setObject(offset + 0, in.name)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val primitiveSchema: Schema[Primitive] = new Schema(
    reflect = new Reflect.Record[Binding, Primitive](
      fields = Vector(
        Schema[String].reflect.asTerm("name")
      ),
      typeId = TypeId.of[Primitive],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Primitive] {
          def usedRegisters: RegisterOffset                               = 1
          def construct(in: Registers, offset: RegisterOffset): Primitive =
            Primitive(in.getObject(offset + 0).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[Primitive] {
          def usedRegisters: RegisterOffset                                            = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Primitive): Unit =
            out.setObject(offset + 0, in.name)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val recordSchema: Schema[Record] = new Schema(
    reflect = new Reflect.Record[Binding, Record](
      fields = Vector(
        Reflect.Deferred(() => Schema[Vector[(String, SchemaRepr)]].reflect).asTerm("fields")
      ),
      typeId = TypeId.of[Record],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Record] {
          def usedRegisters: RegisterOffset                            = 1
          def construct(in: Registers, offset: RegisterOffset): Record =
            Record(in.getObject(offset + 0).asInstanceOf[Vector[(String, SchemaRepr)]])
        },
        deconstructor = new Deconstructor[Record] {
          def usedRegisters: RegisterOffset                                         = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Record): Unit =
            out.setObject(offset + 0, in.fields)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val variantSchema: Schema[Variant] = new Schema(
    reflect = new Reflect.Record[Binding, Variant](
      fields = Vector(
        Reflect.Deferred(() => Schema[Vector[(String, SchemaRepr)]].reflect).asTerm("cases")
      ),
      typeId = TypeId.of[Variant],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Variant] {
          def usedRegisters: RegisterOffset                             = 1
          def construct(in: Registers, offset: RegisterOffset): Variant =
            Variant(in.getObject(offset + 0).asInstanceOf[Vector[(String, SchemaRepr)]])
        },
        deconstructor = new Deconstructor[Variant] {
          def usedRegisters: RegisterOffset                                          = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Variant): Unit =
            out.setObject(offset + 0, in.cases)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val sequenceSchema: Schema[Sequence] = new Schema(
    reflect = new Reflect.Record[Binding, Sequence](
      fields = Vector(
        Reflect.Deferred(() => schemaReprSchema.reflect).asTerm("element")
      ),
      typeId = TypeId.of[Sequence],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Sequence] {
          def usedRegisters: RegisterOffset                              = 1
          def construct(in: Registers, offset: RegisterOffset): Sequence =
            Sequence(in.getObject(offset + 0).asInstanceOf[SchemaRepr])
        },
        deconstructor = new Deconstructor[Sequence] {
          def usedRegisters: RegisterOffset                                           = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Sequence): Unit =
            out.setObject(offset + 0, in.element)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val mapSchema: Schema[Map] = new Schema(
    reflect = new Reflect.Record[Binding, Map](
      fields = Vector(
        Reflect.Deferred(() => schemaReprSchema.reflect).asTerm("key"),
        Reflect.Deferred(() => schemaReprSchema.reflect).asTerm("value")
      ),
      typeId = TypeId.of[Map],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Map] {
          def usedRegisters: RegisterOffset                         = 2
          def construct(in: Registers, offset: RegisterOffset): Map =
            Map(
              in.getObject(offset + 0).asInstanceOf[SchemaRepr],
              in.getObject(offset + 1).asInstanceOf[SchemaRepr]
            )
        },
        deconstructor = new Deconstructor[Map] {
          def usedRegisters: RegisterOffset                                      = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Map): Unit = {
            out.setObject(offset + 0, in.key)
            out.setObject(offset + 1, in.value)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val optionalSchema: Schema[Optional] = new Schema(
    reflect = new Reflect.Record[Binding, Optional](
      fields = Vector(
        Reflect.Deferred(() => schemaReprSchema.reflect).asTerm("inner")
      ),
      typeId = TypeId.of[Optional],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Optional] {
          def usedRegisters: RegisterOffset                              = 1
          def construct(in: Registers, offset: RegisterOffset): Optional =
            Optional(in.getObject(offset + 0).asInstanceOf[SchemaRepr])
        },
        deconstructor = new Deconstructor[Optional] {
          def usedRegisters: RegisterOffset                                           = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Optional): Unit =
            out.setObject(offset + 0, in.inner)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val wildcardSchema: Schema[Wildcard.type] = new Schema(
    reflect = new Reflect.Record[Binding, Wildcard.type](
      fields = Vector.empty,
      typeId = TypeId.of[Wildcard.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Wildcard.type](Wildcard),
        deconstructor = new ConstantDeconstructor[Wildcard.type]
      ),
      modifiers = Vector.empty
    )
  )

  // Schema for SchemaRepr sealed trait
  implicit lazy val schemaReprSchema: Schema[SchemaRepr] = new Schema(
    reflect = new Reflect.Variant[Binding, SchemaRepr](
      cases = Vector(
        nominalSchema.reflect.asTerm("Nominal"),
        primitiveSchema.reflect.asTerm("Primitive"),
        recordSchema.reflect.asTerm("Record"),
        variantSchema.reflect.asTerm("Variant"),
        sequenceSchema.reflect.asTerm("Sequence"),
        mapSchema.reflect.asTerm("Map"),
        optionalSchema.reflect.asTerm("Optional"),
        wildcardSchema.reflect.asTerm("Wildcard")
      ),
      typeId = TypeId.of[SchemaRepr],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[SchemaRepr] {
          def discriminate(a: SchemaRepr): Int = a match {
            case _: Nominal       => 0
            case _: Primitive     => 1
            case _: Record        => 2
            case _: Variant       => 3
            case _: Sequence      => 4
            case _: Map           => 5
            case _: Optional      => 6
            case _: Wildcard.type => 7
          }
        },
        matchers = Matchers(
          new Matcher[Nominal] {
            def downcastOrNull(a: Any): Nominal = a match {
              case x: Nominal => x
              case _          => null.asInstanceOf[Nominal]
            }
          },
          new Matcher[Primitive] {
            def downcastOrNull(a: Any): Primitive = a match {
              case x: Primitive => x
              case _            => null.asInstanceOf[Primitive]
            }
          },
          new Matcher[Record] {
            def downcastOrNull(a: Any): Record = a match {
              case x: Record => x
              case _         => null.asInstanceOf[Record]
            }
          },
          new Matcher[Variant] {
            def downcastOrNull(a: Any): Variant = a match {
              case x: Variant => x
              case _          => null.asInstanceOf[Variant]
            }
          },
          new Matcher[Sequence] {
            def downcastOrNull(a: Any): Sequence = a match {
              case x: Sequence => x
              case _           => null.asInstanceOf[Sequence]
            }
          },
          new Matcher[Map] {
            def downcastOrNull(a: Any): Map = a match {
              case x: Map => x
              case _      => null.asInstanceOf[Map]
            }
          },
          new Matcher[Optional] {
            def downcastOrNull(a: Any): Optional = a match {
              case x: Optional => x
              case _           => null.asInstanceOf[Optional]
            }
          },
          new Matcher[Wildcard.type] {
            def downcastOrNull(a: Any): Wildcard.type = a match {
              case x: Wildcard.type => x
              case _                => null.asInstanceOf[Wildcard.type]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}
