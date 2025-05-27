package zio.blocks.schema

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding.Binding
import zio.blocks.schema.binding._
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq

sealed trait Reflect[F[_, _], A] extends Reflectable[A] { self =>
  protected def inner: Any

  final type Structure = A

  type NodeBinding <: BindingType
  type ModifierType <: Modifier

  def metadata: F[NodeBinding, A]

  def asDynamic: Option[Reflect.Dynamic[F]] =
    this match {
      case dynamic: Reflect.Dynamic[F] @scala.unchecked => new Some(dynamic)
      case deferred: Reflect.Deferred[F, _]             => deferred.value.asDynamic
      case _                                            => None
    }

  def asMap(implicit ev: IsMap[A]): Option[Reflect.Map[F, ev.Key, ev.Value, ev.Map]] =
    this match {
      case map: Reflect.Map[F, ev.Key, ev.Value, ev.Map] @scala.unchecked => new Some(map)
      case deferred: Reflect.Deferred[F, _]                               => deferred.value.asMap
      case _                                                              => None
    }

  def asMapUnknown: Option[Reflect.Map.Unknown[F]] =
    this match {
      case _: Reflect.Map[F, _, _, _] @scala.unchecked =>
        new Some(new Reflect.Map.Unknown[F] {
          def map: Reflect.Map[F, KeyType, ValueType, MapType] =
            self.asInstanceOf[Reflect.Map[F, KeyType, ValueType, MapType]]
        })
      case deferred: Reflect.Deferred[F, _] => deferred.value.asMapUnknown
      case _                                => None
    }

  def asPrimitive: Option[Reflect.Primitive[F, A]] =
    this match {
      case primitive: Reflect.Primitive[F, A] @scala.unchecked => new Some(primitive)
      case deferred: Reflect.Deferred[F, _]                    => deferred.value.asPrimitive
      case _                                                   => None
    }

  def asRecord: Option[Reflect.Record[F, A]] =
    this match {
      case record: Reflect.Record[F, A] @scala.unchecked => new Some(record)
      case deferred: Reflect.Deferred[F, _]              => deferred.value.asRecord
      case _                                             => None
    }

  def asSequence(implicit ev: IsCollection[A]): Option[Reflect.Sequence[F, ev.Elem, ev.Collection]] =
    this match {
      case sequence: Reflect.Sequence[F, ev.Elem, ev.Collection] @scala.unchecked => new Some(sequence)
      case deferred: Reflect.Deferred[F, _]                                       => deferred.value.asSequence
      case _                                                                      => None
    }

  def asSequenceUnknown: Option[Reflect.Sequence.Unknown[F]] =
    this match {
      case _: Reflect.Sequence[F, _, _] @scala.unchecked =>
        new Some(new Reflect.Sequence.Unknown[F] {
          def sequence: Reflect.Sequence[F, ElementType, CollectionType] =
            self.asInstanceOf[Reflect.Sequence[F, ElementType, CollectionType]]
        })
      case deferred: Reflect.Deferred[F, _] => deferred.value.asSequenceUnknown
      case _                                => None
    }

  def asTerm[S](name: String): Term[F, S, A] = new Term(name, this, Doc.Empty, Nil)

  def asVariant: Option[Reflect.Variant[F, A]] =
    this match {
      case variant: Reflect.Variant[F, A] @scala.unchecked => new Some(variant)
      case deferred: Reflect.Deferred[F, _]                => deferred.value.asVariant
      case _                                               => None
    }

  def binding(implicit F: HasBinding[F]): Binding[NodeBinding, A]

  def examples(implicit F: HasBinding[F]): Seq[A]

  def examples(value: A, values: A*)(implicit F: HasBinding[F]): Reflect[F, A]

  def defaultValue(value: => A)(implicit F: HasBinding[F]): Reflect[F, A]

  def doc(value: Doc): Reflect[F, A]

  def doc(value: String): Reflect[F, A] = doc(Doc.Text(value))

  override def equals(obj: Any): Boolean = obj match {
    case that: Reflect[_, _] => (this eq that) || inner == that.inner
    case _                   => false
  }

  def fromDynamicValue(value: DynamicValue)(implicit F: HasBinding[F]): Either[SchemaError, A] =
    fromDynamicValue(value, Nil)

  private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
    F: HasBinding[F]
  ): Either[SchemaError, A]

  def get[B](optic: Optic[A, B]): Option[Reflect[F, B]] =
    get(optic.toDynamic).asInstanceOf[Option[Reflect[F, B]]]

  def get(dynamic: DynamicOptic): Option[Reflect[F, _]] = {
    @tailrec
    def loop(current: Reflect[F, _], idx: Int): Option[Reflect[F, _]] =
      if (idx == dynamic.nodes.length) new Some(current)
      else {
        loop(
          dynamic.nodes(idx) match {
            case DynamicOptic.Node.Field(name) =>
              current.asRecord match {
                case Some(record) =>
                  val fieldIdx = record.fieldIndexByName(name)
                  if (fieldIdx >= 0) record.fields(fieldIdx).value
                  else return None
                case _ => return None
              }
            case DynamicOptic.Node.Case(name) =>
              current.asVariant match {
                case Some(variant) =>
                  val caseIdx = variant.caseIndexByName(name)
                  if (caseIdx >= 0) variant.cases(caseIdx).value
                  else return None
                case _ => return None
              }
            case DynamicOptic.Node.Elements =>
              current.asSequenceUnknown match {
                case Some(unknown) => unknown.sequence.element
                case _             => return None
              }
            case node =>
              current.asMapUnknown match {
                case Some(unknown) =>
                  if (node == DynamicOptic.Node.MapKeys) unknown.map.key
                  else unknown.map.value
                case _ => return None
              }
          },
          idx + 1
        )
      }

    loop(this, 0).asInstanceOf[Option[Reflect[F, A]]]
  }

  def getDefaultValue(implicit F: HasBinding[F]): Option[A]

  override def hashCode: Int = inner.hashCode

  def isDynamic: Boolean =
    this match {
      case _: Reflect.Dynamic[_]     => true
      case d: Reflect.Deferred[_, _] => d.value.isDynamic
      case _                         => false
    }

  def isMap: Boolean =
    this match {
      case _: Reflect.Map[_, _, _, _] => true
      case d: Reflect.Deferred[_, _]  => d.value.isMap
      case _                          => false
    }

  def isPrimitive: Boolean =
    this match {
      case _: Reflect.Primitive[_, _] => true
      case d: Reflect.Deferred[_, _]  => d.value.isPrimitive
      case _                          => false
    }

  def isRecord: Boolean =
    this match {
      case _: Reflect.Record[_, _]   => true
      case d: Reflect.Deferred[_, _] => d.value.isRecord
      case _                         => false
    }

  def isSequence: Boolean =
    this match {
      case _: Reflect.Sequence[_, _, _] => true
      case d: Reflect.Deferred[_, _]    => d.value.isSequence
      case _                            => false
    }

  def isVariant: Boolean =
    this match {
      case _: Reflect.Variant[_, _]  => true
      case d: Reflect.Deferred[_, _] => d.value.isVariant
      case _                         => false
    }

  def modifiers: Seq[ModifierType]

  def modifier(modifier: ModifierType): Reflect[F, A]

  def modifiers(modifiers: Iterable[ModifierType]): Reflect[F, A]

  def nodeType: Reflect.Type { type NodeBinding = self.NodeBinding; type ModifierType = self.ModifierType }

  lazy val noBinding: Reflect[NoBinding, A] = transform(DynamicOptic.root, ReflectTransformer.noBinding()).force

  def toDynamicValue(value: A)(implicit F: HasBinding[F]): DynamicValue

  def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Reflect[G, A]]

  def updated[B](optic: Optic[A, B])(f: Reflect[F, B] => Reflect[F, B]): Option[Reflect[F, A]] =
    updated(optic.toDynamic)(new Reflect.Updater[F] {
      def update[C](reflect: Reflect[F, C]): Reflect[F, C] =
        f(reflect.asInstanceOf[Reflect[F, B]]).asInstanceOf[Reflect[F, C]]
    })

  def updated[B](dynamic: DynamicOptic)(f: Reflect.Updater[F]): Option[Reflect[F, A]] = {
    def loop(current: Reflect[F, _], idx: Int): Option[Reflect[F, _]] =
      if (idx == dynamic.nodes.length) new Some(f.update(current.asInstanceOf[Reflect[F, B]]))
      else {
        dynamic.nodes(idx) match {
          case DynamicOptic.Node.Field(name) =>
            current.asRecord match {
              case Some(record) =>
                record.modifyField(name)(new Term.Updater[F] {
                  def update[S, C](input: Term[F, S, C]): Option[Term[F, S, C]] =
                    loop(input.value, idx + 1) match {
                      case Some(value) => new Some(input.copy(value = value.asInstanceOf[Reflect[F, C]]))
                      case _           => None
                    }
                })
              case _ => None
            }
          case DynamicOptic.Node.Case(name) =>
            current.asVariant match {
              case Some(variant) =>
                variant.modifyCase(name)(new Term.Updater[F] {
                  def update[S, C](input: Term[F, S, C]): Option[Term[F, S, C]] =
                    loop(input.value, idx + 1) match {
                      case Some(value) => new Some(input.copy(value = value.asInstanceOf[Reflect[F, C]]))
                      case _           => None
                    }
                })
              case _ => None
            }
          case DynamicOptic.Node.Elements =>
            current.asSequenceUnknown match {
              case Some(unknown) =>
                val sequence = unknown.sequence
                loop(sequence.element, idx + 1) match {
                  case Some(element) =>
                    new Some(sequence.copy(element = element.asInstanceOf[Reflect[F, unknown.ElementType]]))
                  case _ => None
                }
              case _ => None
            }
          case node =>
            current.asMapUnknown match {
              case Some(unknown) =>
                val map = unknown.map
                if (node == DynamicOptic.Node.MapKeys) {
                  loop(map.key, idx + 1) match {
                    case Some(key) => new Some(map.copy(key = key.asInstanceOf[Reflect[F, unknown.KeyType]]))
                    case _         => None
                  }
                } else {
                  loop(map.value, idx + 1) match {
                    case Some(value) => new Some(map.copy(value = value.asInstanceOf[Reflect[F, unknown.ValueType]]))
                    case _           => None
                  }
                }
              case _ => None
            }
        }
      }

    loop(this, 0).asInstanceOf[Option[Reflect[F, A]]]
  }
}

object Reflect {
  type Bound[A] = Reflect[Binding, A]

  sealed trait Type {
    type ModifierType <: Modifier
    type NodeBinding <: BindingType
  }

  object Type {
    case object Record extends Type {
      type ModifierType = Modifier.Record
      type NodeBinding  = BindingType.Record
    }

    case object Variant extends Type {
      type ModifierType = Modifier.Variant
      type NodeBinding  = BindingType.Variant
    }

    case class Sequence[C[_]]() extends Type {
      type ModifierType = Modifier.Seq
      type NodeBinding  = BindingType.Seq[C]
    }

    case class Map[M[_, _]]() extends Type {
      type ModifierType = Modifier.Map
      type NodeBinding  = BindingType.Map[M]
    }

    case object Dynamic extends Type {
      type ModifierType = Modifier.Dynamic
      type NodeBinding  = BindingType.Dynamic
    }

    case object Primitive extends Type {
      type ModifierType = Modifier.Primitive
      type NodeBinding  = BindingType.Primitive
    }
  }

  trait Updater[F[_, _]] {
    def update[A](reflect: Reflect[F, A]): Reflect[F, A]
  }

  case class Record[F[_, _], A](
    fields: Seq[Term[F, A, ?]],
    typeName: TypeName[A],
    recordBinding: F[BindingType.Record, A],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Record] = Vector()
  ) extends Reflect[F, A] { self =>
    private[this] val fieldValues = fields.map(_.value).toArray
    private[this] val fieldIndexByName = new java.util.HashMap[String, Int] {
      fields.foreach {
        var i = 0
        term =>
          put(term.name, i)
          i += 1
      }
    }

    protected def inner: Any = (fields, typeName, doc, modifiers)

    type NodeBinding  = BindingType.Record
    type ModifierType = Modifier.Record

    def doc(value: Doc): Record[F, A] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[A] = F.binding(recordBinding).defaultValue.map(_())

    def defaultValue(value: => A)(implicit F: HasBinding[F]): Record[F, A] =
      copy(recordBinding = F.updateBinding(recordBinding, _.defaultValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[A] = binding.examples

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Record[F, A] =
      copy(recordBinding = F.updateBinding(recordBinding, _.examples(value, values: _*)))

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Record, A] = F.binding(recordBinding)

    def constructor(implicit F: HasBinding[F]): Constructor[A] = F.constructor(recordBinding)

    def deconstructor(implicit F: HasBinding[F]): Deconstructor[A] = F.deconstructor(recordBinding)

    private[schema] def fieldIndexByName(name: String): Int = fieldIndexByName.getOrDefault(name, -1)

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, A] =
      value match {
        case DynamicValue.Record(fields) =>
          var error: Option[SchemaError] = None

          def addError(e: SchemaError): Unit = error = error.map(_ ++ e).orElse(Some(e))

          val fieldValues = this.fieldValues.clone
          val constructor = this.constructor
          val registers   = Registers(constructor.usedRegisters)
          fields.foreach { case (name, value) =>
            val idx = fieldIndexByName.getOrDefault(name, -1)
            if (idx >= 0) {
              val fieldValue = fieldValues(idx)
              if (fieldValue ne null) {
                fieldValues(idx) = null
                fieldValue.fromDynamicValue(value, DynamicOptic.Node.Field(name) :: trace) match {
                  case Right(value) =>
                    this.registers(idx).asInstanceOf[Register[Any]].set(registers, RegisterOffset.Zero, value)
                  case Left(error) =>
                    addError(error)
                }
              } else addError(SchemaError.duplicatedField(trace, name))
            }
          }
          var idx = 0
          while (idx < this.fields.length) {
            if (fieldValues(idx) ne null) {
              val name = this.fields(idx).name
              addError(SchemaError.missingField(trace, name))
            }
            idx += 1
          }
          if (error.isDefined) new Left(error.get)
          else new Right(constructor.construct(registers, RegisterOffset.Zero))
        case _ =>
          new Left(SchemaError.invalidType(trace, "Expected a record"))
      }

    def lensByName[B](name: String): Option[Lens[A, B]] = {
      val idx = fieldIndexByName.getOrDefault(name, -1)
      if (idx >= 0) {
        new Some(Lens(this.asInstanceOf[Reflect.Record.Bound[A]], fields(idx).asInstanceOf[Term.Bound[A, B]]))
      } else None
    }

    def metadata: F[NodeBinding, A] = recordBinding

    def modifier(modifier: Modifier.Record): Record[F, A] = copy(modifiers = modifiers :+ modifier)

    def modifiers(modifiers: Iterable[Modifier.Record]): Record[F, A] = copy(modifiers = this.modifiers ++ modifiers)

    def modifyField(name: String)(f: Term.Updater[F]): Option[Record[F, A]] = {
      val idx = fieldIndexByName.getOrDefault(name, -1)
      if (idx >= 0) {
        f.update(fields(idx)) match {
          case Some(field) => new Some(copy(fields = fields.updated(idx, field)))
          case _           => None
        }
      } else None
    }

    def toDynamicValue(value: A)(implicit F: HasBinding[F]): DynamicValue = {
      val deconstructor = this.deconstructor
      val registers     = Registers(deconstructor.usedRegisters)
      deconstructor.deconstruct(registers, RegisterOffset.Zero, value)
      val builder = Vector.newBuilder[(String, DynamicValue)]
      var idx     = 0
      while (idx < this.registers.length) {
        val field                                 = fields(idx)
        val register                              = this.registers(idx)
        val fieldReflect: Reflect[F, field.Focus] = field.value.asInstanceOf[Reflect[F, field.Focus]]
        val value =
          fieldReflect.toDynamicValue(register.get(registers, RegisterOffset.Zero).asInstanceOf[field.Focus])
        builder.addOne((field.name, value))
        idx += 1
      }
      new DynamicValue.Record(builder.result())
    }

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Record[G, A]] =
      for {
        fields <- Lazy.foreach(fields.toVector)(_.transform(path, Term.Type.Record, f))
        record <- f.transformRecord(path, fields, typeName, recordBinding, doc, modifiers)
      } yield record

    val registers: IndexedSeq[Register[?]] = {
      val registers      = new Array[Register[?]](fieldValues.length)
      var registerOffset = RegisterOffset.Zero
      var idx            = 0
      fieldValues.foreach { fieldValue =>
        fieldValue match {
          case Reflect.Primitive(PrimitiveType.Unit, _, _, _, _) =>
            registers(idx) = Register.Unit
          case Reflect.Primitive(_: PrimitiveType.Boolean, _, _, _, _) =>
            registers(idx) = Register.Boolean(RegisterOffset.getBytes(registerOffset))
            registerOffset = RegisterOffset.incrementBooleansAndBytes(registerOffset)
          case Reflect.Primitive(_: PrimitiveType.Byte, _, _, _, _) =>
            registers(idx) = Register.Byte(RegisterOffset.getBytes(registerOffset))
            registerOffset = RegisterOffset.incrementBooleansAndBytes(registerOffset)
          case Reflect.Primitive(_: PrimitiveType.Char, _, _, _, _) =>
            registers(idx) = Register.Char(RegisterOffset.getBytes(registerOffset))
            registerOffset = RegisterOffset.incrementCharsAndShorts(registerOffset)
          case Reflect.Primitive(_: PrimitiveType.Short, _, _, _, _) =>
            registers(idx) = Register.Short(RegisterOffset.getBytes(registerOffset))
            registerOffset = RegisterOffset.incrementCharsAndShorts(registerOffset)
          case Reflect.Primitive(_: PrimitiveType.Float, _, _, _, _) =>
            registers(idx) = Register.Float(RegisterOffset.getBytes(registerOffset))
            registerOffset = RegisterOffset.incrementFloatsAndInts(registerOffset)
          case Reflect.Primitive(_: PrimitiveType.Int, _, _, _, _) =>
            registers(idx) = Register.Int(RegisterOffset.getBytes(registerOffset))
            registerOffset = RegisterOffset.incrementFloatsAndInts(registerOffset)
          case Reflect.Primitive(_: PrimitiveType.Double, _, _, _, _) =>
            registers(idx) = Register.Double(RegisterOffset.getBytes(registerOffset))
            registerOffset = RegisterOffset.incrementDoublesAndLongs(registerOffset)
          case Reflect.Primitive(_: PrimitiveType.Long, _, _, _, _) =>
            registers(idx) = Register.Long(RegisterOffset.getBytes(registerOffset))
            registerOffset = RegisterOffset.incrementDoublesAndLongs(registerOffset)
          case _ =>
            registers(idx) = Register.Object(RegisterOffset.getObjects(registerOffset))
            registerOffset = RegisterOffset.incrementObjects(registerOffset)
        }
        idx += 1
      }
      ArraySeq.unsafeWrapArray(registers)
    }

    val usedRegisters: RegisterOffset = registers.foldLeft(RegisterOffset.Zero) { (acc, register) =>
      RegisterOffset.add(acc, register.usedRegisters)
    }

    def nodeType: Reflect.Type.Record.type = Reflect.Type.Record
  }

  object Record {
    type Bound[A] = Record[Binding, A]
  }

  case class Variant[F[_, _], A](
    cases: Seq[Term[F, A, ? <: A]],
    typeName: TypeName[A],
    variantBinding: F[BindingType.Variant, A],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Variant] = Vector()
  ) extends Reflect[F, A] {
    private[this] val caseIndexByName = new java.util.HashMap[String, Int] {
      cases.foreach {
        var i = 0
        term =>
          put(term.name, i)
          i += 1
      }
    }

    protected def inner: Any = (cases, typeName, doc, modifiers)

    type NodeBinding  = BindingType.Variant
    type ModifierType = Modifier.Variant

    def doc(value: Doc): Variant[F, A] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[A] = F.binding(variantBinding).defaultValue.map(_())

    def defaultValue(value: => A)(implicit F: HasBinding[F]): Variant[F, A] =
      copy(variantBinding = F.updateBinding(variantBinding, _.defaultValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[A] = binding.examples

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Variant[F, A] =
      copy(variantBinding = F.updateBinding(variantBinding, _.examples(value, values: _*)))

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Variant, A] = F.binding(variantBinding)

    private[schema] def caseIndexByName(name: String): Int = caseIndexByName.getOrDefault(name, -1)

    def discriminator(implicit F: HasBinding[F]): Discriminator[A] = F.discriminator(variantBinding)

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, A] =
      value match {
        case DynamicValue.Variant(discriminator, value) =>
          val idx = caseIndexByName(discriminator)
          if (idx >= 0) {
            val case_ = cases(idx)
            case_.value.asInstanceOf[Reflect[F, A]].fromDynamicValue(value, DynamicOptic.Node.Case(case_.name) :: trace)
          } else new Left(SchemaError.unknownCase(trace, discriminator))
        case _ => new Left(SchemaError.invalidType(trace, "Expected a variant"))
      }

    def matchers(implicit F: HasBinding[F]): Matchers[A] = F.matchers(variantBinding)

    def metadata: F[NodeBinding, A] = variantBinding

    def modifier(modifier: Modifier.Variant): Variant[F, A] = copy(modifiers = modifiers :+ modifier)

    def modifiers(modifiers: Iterable[Modifier.Variant]): Variant[F, A] = copy(modifiers = this.modifiers ++ modifiers)

    def modifyCase(name: String)(f: Term.Updater[F]): Option[Variant[F, A]] = {
      val idx = caseIndexByName.getOrDefault(name, -1)
      if (idx >= 0) {
        f.update(cases(idx)) match {
          case Some(case_) => new Some(copy(cases = cases.updated(idx, case_)))
          case _           => None
        }
      } else None
    }

    def prismByName[B <: A](name: String): Option[Prism[A, B]] = {
      val idx = caseIndexByName.getOrDefault(name, -1)
      if (idx >= 0) {
        new Some(Prism(this.asInstanceOf[Reflect.Variant.Bound[A]], cases(idx).asInstanceOf[Term.Bound[A, B]]))
      } else None
    }

    def toDynamicValue(value: A)(implicit F: HasBinding[F]): DynamicValue = {
      val idx        = discriminator.discriminate(value)
      val downcasted = matchers.matchers(idx).downcastOrNull(value)
      val case_      = cases(idx)
      DynamicValue.Variant(case_.name, case_.value.asInstanceOf[Reflect[F, downcasted.type]].toDynamicValue(downcasted))
    }

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Variant[G, A]] =
      for {
        cases   <- Lazy.foreach(cases.toVector)(_.transform(path, Term.Type.Variant, f))
        variant <- f.transformVariant(path, cases, typeName, variantBinding, doc, modifiers)
      } yield variant

    def nodeType: Reflect.Type.Variant.type = Reflect.Type.Variant
  }

  object Variant {
    type Bound[A] = Variant[Binding, A]
  }

  case class Sequence[F[_, _], A, C[_]](
    element: Reflect[F, A],
    seqBinding: F[BindingType.Seq[C], C[A]],
    typeName: TypeName[C[A]],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Seq] = Vector()
  ) extends Reflect[F, C[A]] { self =>
    protected def inner: Any = (element, typeName, doc, modifiers)

    type NodeBinding  = BindingType.Seq[C]
    type ModifierType = Modifier.Seq

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Seq[C], C[A]] = F.binding(seqBinding)

    def doc(value: Doc): Sequence[F, A, C] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[C[A]] = F.binding(seqBinding).defaultValue.map(_())

    def defaultValue(value: => C[A])(implicit F: HasBinding[F]): Sequence[F, A, C] =
      copy(seqBinding = F.updateBinding(seqBinding, _.defaultValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[C[A]] = binding.examples

    def examples(value: C[A], values: C[A]*)(implicit F: HasBinding[F]): Sequence[F, A, C] =
      copy(seqBinding = F.updateBinding(seqBinding, _.examples(value, values: _*)))

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, C[A]] = {
      var error: Option[SchemaError] = None

      def addError(e: SchemaError): Unit = error = error.map(_ ++ e).orElse(Some(e))

      value match {
        case DynamicValue.Sequence(elements) =>
          val seqTrace    = DynamicOptic.Node.Elements :: trace
          val constructor = seqConstructor
          element match {
            case Reflect.Primitive(_: PrimitiveType.Boolean, _, _, _, _) =>
              val builder = constructor.newBooleanBuilder(elements.size)
              elements.foreach { elem =>
                element.fromDynamicValue(elem, seqTrace) match {
                  case Right(value) => constructor.addBoolean(builder, value.asInstanceOf[Boolean])
                  case Left(error)  => addError(error)
                }
              }
              if (error.isDefined) new Left(error.get)
              else new Right(constructor.resultBoolean(builder).asInstanceOf[C[A]])
            case Reflect.Primitive(_: PrimitiveType.Byte, _, _, _, _) =>
              val builder = constructor.newByteBuilder(elements.size)
              elements.foreach { elem =>
                element.fromDynamicValue(elem, seqTrace) match {
                  case Right(value) => constructor.addByte(builder, value.asInstanceOf[Byte])
                  case Left(error)  => addError(error)
                }
              }
              if (error.isDefined) new Left(error.get)
              else new Right(constructor.resultByte(builder).asInstanceOf[C[A]])
            case Reflect.Primitive(_: PrimitiveType.Char, _, _, _, _) =>
              val builder = constructor.newCharBuilder(elements.size)
              elements.foreach { elem =>
                element.fromDynamicValue(elem, seqTrace) match {
                  case Right(value) => constructor.addChar(builder, value.asInstanceOf[Char])
                  case Left(error)  => addError(error)
                }
              }
              if (error.isDefined) new Left(error.get)
              else new Right(constructor.resultChar(builder).asInstanceOf[C[A]])
            case Reflect.Primitive(_: PrimitiveType.Short, _, _, _, _) =>
              val builder = constructor.newShortBuilder(elements.size)
              elements.foreach { elem =>
                element.fromDynamicValue(elem, seqTrace) match {
                  case Right(value) => constructor.addShort(builder, value.asInstanceOf[Short])
                  case Left(error)  => addError(error)
                }
              }
              if (error.isDefined) new Left(error.get)
              else new Right(constructor.resultShort(builder).asInstanceOf[C[A]])
            case Reflect.Primitive(_: PrimitiveType.Int, _, _, _, _) =>
              val builder = constructor.newIntBuilder(elements.size)
              elements.foreach { elem =>
                element.fromDynamicValue(elem, seqTrace) match {
                  case Right(value) => constructor.addInt(builder, value.asInstanceOf[Int])
                  case Left(error)  => addError(error)
                }
              }
              if (error.isDefined) new Left(error.get)
              else new Right(constructor.resultInt(builder).asInstanceOf[C[A]])
            case Reflect.Primitive(_: PrimitiveType.Long, _, _, _, _) =>
              val builder = constructor.newLongBuilder(elements.size)
              elements.foreach { elem =>
                element.fromDynamicValue(elem, seqTrace) match {
                  case Right(value) => constructor.addLong(builder, value.asInstanceOf[Long])
                  case Left(error)  => addError(error)
                }
              }
              if (error.isDefined) new Left(error.get)
              else new Right(constructor.resultLong(builder).asInstanceOf[C[A]])
            case Reflect.Primitive(_: PrimitiveType.Float, _, _, _, _) =>
              val builder = constructor.newFloatBuilder(elements.size)
              elements.foreach { elem =>
                element.fromDynamicValue(elem, seqTrace) match {
                  case Right(value) => constructor.addFloat(builder, value.asInstanceOf[Float])
                  case Left(error)  => addError(error)
                }
              }
              if (error.isDefined) new Left(error.get)
              else new Right(constructor.resultFloat(builder).asInstanceOf[C[A]])
            case Reflect.Primitive(_: PrimitiveType.Double, _, _, _, _) =>
              val builder = constructor.newDoubleBuilder(elements.size)
              elements.foreach { elem =>
                element.fromDynamicValue(elem, seqTrace) match {
                  case Right(value) => constructor.addDouble(builder, value.asInstanceOf[Double])
                  case Left(error)  => addError(error)
                }
              }
              if (error.isDefined) new Left(error.get)
              else new Right(constructor.resultDouble(builder).asInstanceOf[C[A]])
            case _ =>
              val builder = constructor.newObjectBuilder[A](elements.size)
              elements.foreach { elem =>
                element.fromDynamicValue(elem, seqTrace) match {
                  case Right(value) => constructor.addObject(builder, value)
                  case Left(error)  => addError(error)
                }
              }
              if (error.isDefined) new Left(error.get)
              else new Right(constructor.resultObject(builder))
          }
        case _ =>
          new Left(SchemaError.invalidType(trace, "Expected a sequence"))
      }
    }

    def metadata: F[NodeBinding, C[A]] = seqBinding

    def modifier(modifier: Modifier.Seq): Sequence[F, A, C] = copy(modifiers = modifiers :+ modifier)

    def modifiers(modifiers: Iterable[Modifier.Seq]): Sequence[F, A, C] = copy(modifiers = this.modifiers ++ modifiers)

    def toDynamicValue(value: C[A])(implicit F: HasBinding[F]): DynamicValue = {
      val iterator = seqDeconstructor.deconstruct(value)
      val builder  = Vector.newBuilder[DynamicValue]
      while (iterator.hasNext) builder.addOne(element.toDynamicValue(iterator.next()))
      DynamicValue.Sequence(builder.result())
    }

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Sequence[G, A, C]] =
      for {
        element  <- element.transform(path(DynamicOptic.elements), f)
        sequence <- f.transformSequence(path, element, typeName, seqBinding, doc, modifiers)
      } yield sequence

    def seqConstructor(implicit F: HasBinding[F]): SeqConstructor[C] = F.seqConstructor(seqBinding)

    def seqDeconstructor(implicit F: HasBinding[F]): SeqDeconstructor[C] = F.seqDeconstructor(seqBinding)

    def nodeType: Reflect.Type.Sequence[C] = Reflect.Type.Sequence[C]()
  }

  object Sequence {
    type Bound[A, C[_]] = Sequence[Binding, A, C]

    trait Unknown[F[_, _]] {
      type CollectionType[_]
      type ElementType

      def sequence: Reflect.Sequence[F, ElementType, CollectionType]
    }
  }

  case class Map[F[_, _], Key, Value, M[_, _]](
    key: Reflect[F, Key],
    value: Reflect[F, Value],
    mapBinding: F[BindingType.Map[M], M[Key, Value]],
    typeName: TypeName[M[Key, Value]],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Map] = Vector()
  ) extends Reflect[F, M[Key, Value]] { self =>
    protected def inner: Any = (key, value, typeName, doc, modifiers)

    type NodeBinding  = BindingType.Map[M]
    type ModifierType = Modifier.Map

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Map[M], M[Key, Value]] = F.binding(mapBinding)

    def doc(value: Doc): Map[F, Key, Value, M] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[M[Key, Value]] = F.binding(mapBinding).defaultValue.map(_())

    def defaultValue(value: => M[Key, Value])(implicit F: HasBinding[F]): Map[F, Key, Value, M] =
      copy(mapBinding = F.updateBinding(mapBinding, _.defaultValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[M[Key, Value]] = binding.examples

    def examples(value: M[Key, Value], values: M[Key, Value]*)(implicit F: HasBinding[F]): Map[F, Key, Value, M] =
      copy(mapBinding = F.updateBinding(mapBinding, _.examples(value, values: _*)))

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, M[Key, Value]] = {
      var error: Option[SchemaError] = None

      def addError(e: SchemaError): Unit = error = error.map(_ ++ e).orElse(Some(e))

      value match {
        case DynamicValue.Map(elements) =>
          val keyTrace    = DynamicOptic.Node.MapKeys :: trace
          val valueTrace  = DynamicOptic.Node.MapValues :: trace
          val constructor = mapConstructor
          val builder     = constructor.newObjectBuilder[Key, Value](elements.size)
          elements.foreach { case (key, value) =>
            this.key.fromDynamicValue(key, keyTrace) match {
              case Right(keyValue) =>
                this.value.fromDynamicValue(value, valueTrace) match {
                  case Right(valueValue) => constructor.addObject(builder, keyValue, valueValue)
                  case Left(error)       => addError(error)
                }
              case Left(error) => addError(error)
            }
          }
          if (error.isDefined) new Left(error.get)
          else new Right(constructor.resultObject(builder))
        case _ =>
          new Left(SchemaError.invalidType(trace, "Expected a map"))
      }
    }

    def mapConstructor(implicit F: HasBinding[F]): MapConstructor[M] = F.mapConstructor(mapBinding)

    def mapDeconstructor(implicit F: HasBinding[F]): MapDeconstructor[M] = F.mapDeconstructor(mapBinding)

    def metadata: F[NodeBinding, M[Key, Value]] = mapBinding

    def modifier(modifier: Modifier.Map): Map[F, Key, Value, M] = copy(modifiers = modifiers :+ modifier)

    def modifiers(modifiers: Iterable[Modifier.Map]): Map[F, Key, Value, M] =
      copy(modifiers = this.modifiers ++ modifiers)

    def toDynamicValue(value: M[Key, Value])(implicit F: HasBinding[F]): DynamicValue = {
      val deconstructor = mapDeconstructor
      val it            = deconstructor.deconstruct(value)
      val builder       = Vector.newBuilder[(DynamicValue, DynamicValue)]
      while (it.hasNext) {
        val next = it.next()
        builder.addOne(
          (this.key.toDynamicValue(deconstructor.getKey(next)), this.value.toDynamicValue(deconstructor.getValue(next)))
        )
      }
      DynamicValue.Map(builder.result())
    }

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Map[G, Key, Value, M]] =
      for {
        key   <- key.transform(path(DynamicOptic.mapKeys), f)
        value <- value.transform(path(DynamicOptic.mapValues), f)
        map   <- f.transformMap(path, key, value, typeName, mapBinding, doc, modifiers)
      } yield map

    def nodeType: Reflect.Type.Map[M] = Reflect.Type.Map[M]()
  }

  object Map {
    type Bound[K, V, M[_, _]] = Map[Binding, K, V, M]

    trait Unknown[F[_, _]] {
      type MapType[_, _]
      type KeyType
      type ValueType

      def map: Reflect.Map[F, KeyType, ValueType, MapType]
    }
  }

  case class Dynamic[F[_, _]](
    dynamicBinding: F[BindingType.Dynamic, DynamicValue],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Dynamic] = Vector()
  ) extends Reflect[F, DynamicValue] {
    protected def inner: Any = (modifiers, modifiers, doc)

    type NodeBinding  = BindingType.Dynamic
    type ModifierType = Modifier.Dynamic

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Dynamic, DynamicValue] = F.binding(dynamicBinding)

    def doc(value: Doc): Dynamic[F] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[DynamicValue] =
      F.binding(dynamicBinding).defaultValue.map(_())

    def defaultValue(value: => DynamicValue)(implicit F: HasBinding[F]): Dynamic[F] =
      copy(dynamicBinding = F.updateBinding(dynamicBinding, _.defaultValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[DynamicValue] = binding.examples

    def examples(value: DynamicValue, values: DynamicValue*)(implicit F: HasBinding[F]): Dynamic[F] =
      copy(dynamicBinding = F.updateBinding(dynamicBinding, _.examples(value, values: _*)))

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, DynamicValue] = new Right(value)

    def metadata: F[NodeBinding, DynamicValue] = dynamicBinding

    def modifier(modifier: ModifierType): Dynamic[F] = copy(modifiers = modifiers :+ modifier)

    def modifiers(modifiers: Iterable[ModifierType]): Dynamic[F] = copy(modifiers = this.modifiers ++ modifiers)

    def toDynamicValue(value: DynamicValue)(implicit F: HasBinding[F]): DynamicValue = value

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Dynamic[G]] =
      for {
        dynamic <- f.transformDynamic(path, dynamicBinding, doc, modifiers)
      } yield dynamic

    def nodeType: Reflect.Type.Dynamic.type = Reflect.Type.Dynamic
  }

  object Dynamic {
    type Bound = Dynamic[Binding]
  }

  case class Primitive[F[_, _], A](
    primitiveType: PrimitiveType[A],
    primitiveBinding: F[BindingType.Primitive, A],
    typeName: TypeName[A],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Primitive] = Vector()
  ) extends Reflect[F, A] { self =>
    protected def inner: Any = (primitiveType, typeName, doc, modifiers)

    type NodeBinding  = BindingType.Primitive
    type ModifierType = Modifier.Primitive

    def binding(implicit F: HasBinding[F]): Binding.Primitive[A] = F.primitive(primitiveBinding)

    def doc(value: Doc): Primitive[F, A] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[A] = F.binding(primitiveBinding).defaultValue.map(_())

    def defaultValue(value: => A)(implicit F: HasBinding[F]): Primitive[F, A] =
      copy(primitiveBinding = F.updateBinding(primitiveBinding, _.defaultValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[A] = binding.examples

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Primitive[F, A] =
      copy(primitiveBinding = F.updateBinding(primitiveBinding, _.examples(value, values: _*)))

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, A] = primitiveType.fromDynamicValue(value, trace)

    def metadata: F[NodeBinding, A] = primitiveBinding

    def modifier(modifier: Modifier.Primitive): Primitive[F, A] = copy(modifiers = modifiers :+ modifier)

    def modifiers(modifiers: Iterable[Modifier.Primitive]): Primitive[F, A] =
      copy(modifiers = this.modifiers ++ modifiers)

    def toDynamicValue(value: A)(implicit F: HasBinding[F]): DynamicValue = primitiveType.toDynamicValue(value)

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Primitive[G, A]] =
      for {
        primitive <- f.transformPrimitive(path, primitiveType, typeName, primitiveBinding, doc, modifiers)
      } yield primitive

    def nodeType: Reflect.Type.Primitive.type = Reflect.Type.Primitive
  }

  object Primitive {
    type Bound[A] = Primitive[Binding, A]
  }

  case class Deferred[F[_, _], A](_value: () => Reflect[F, A]) extends Reflect[F, A] { self =>
    protected def inner: Any = value.inner

    final lazy val value: Reflect[F, A] = _value()

    type NodeBinding  = value.NodeBinding
    type ModifierType = value.ModifierType

    def binding(implicit F: HasBinding[F]): Binding[NodeBinding, A] = value.binding

    def doc(value: Doc): Deferred[F, A] = copy(_value = () => _value().doc(value))

    def getDefaultValue(implicit F: HasBinding[F]): Option[A] = value.getDefaultValue

    def defaultValue(value: => A)(implicit F: HasBinding[F]): Deferred[F, A] =
      copy(_value = () => _value().defaultValue(value)(F))

    def examples(implicit F: HasBinding[F]): Seq[A] = value.examples

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Deferred[F, A] =
      copy(_value = () => _value().examples(value, values: _*))

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, A] = this.value.fromDynamicValue(value, trace)

    def metadata: F[NodeBinding, A] = value.metadata

    def modifiers: Seq[ModifierType] = value.modifiers

    def modifier(modifier: ModifierType): Deferred[F, A] = copy(_value = () => value.modifier(modifier))

    def modifiers(modifiers: Iterable[ModifierType]): Deferred[F, A] = copy(_value = () => value.modifiers(modifiers))

    def doc: Doc = value.doc

    def toDynamicValue(value: A)(implicit F: HasBinding[F]): DynamicValue = this.value.toDynamicValue(value)

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Reflect[G, A]] =
      Lazy[Lazy[Reflect[G, A]]] {
        val v = visited.get
        if (v.containsKey(this)) Lazy(value.asInstanceOf[Reflect[G, A]]) // exit from recursion
        else {
          for {
            _      <- Lazy(v.put(this, ()))
            result <- value.transform(path, f).ensuring(Lazy(v.remove(this)))
          } yield result
        }
      }.flatten

    override def hashCode: Int = {
      val v = visited.get
      if (v.containsKey(this)) 0 // exit from recursion
      else {
        v.put(this, ())
        try inner.hashCode
        finally v.remove(this)
      }
    }

    override def equals(obj: Any): Boolean = obj match {
      case that: Reflect[_, _] =>
        (this eq that) || {
          val v = visited.get
          if (v.containsKey(this)) true // exit from recursion
          else {
            v.put(this, ())
            try inner == that.inner
            finally v.remove(this)
          }
        }
      case _ => false
    }

    private[this] val visited =
      new ThreadLocal[java.util.IdentityHashMap[AnyRef, Unit]] {
        override def initialValue: java.util.IdentityHashMap[AnyRef, Unit] =
          new java.util.IdentityHashMap[AnyRef, Unit](1)
      }

    def nodeType = value.nodeType
  }

  object Deferred {
    type Bound[A] = Deferred[Binding, A]
  }

  def unit[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Unit] =
    new Primitive(PrimitiveType.Unit, F.fromBinding(Binding.Primitive.unit), TypeName.unit, Doc.Empty, Nil)

  def boolean[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Boolean] =
    new Primitive(
      PrimitiveType.Boolean(Validation.None),
      F.fromBinding(Binding.Primitive.boolean),
      TypeName.boolean,
      Doc.Empty,
      Nil
    )

  def byte[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Byte] =
    new Primitive(
      PrimitiveType.Byte(Validation.None),
      F.fromBinding(Binding.Primitive.byte),
      TypeName.byte,
      Doc.Empty,
      Nil
    )

  def short[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Short] =
    new Primitive(
      PrimitiveType.Short(Validation.None),
      F.fromBinding(Binding.Primitive.short),
      TypeName.short,
      Doc.Empty,
      Nil
    )

  def int[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Int] =
    new Primitive(
      PrimitiveType.Int(Validation.None),
      F.fromBinding(Binding.Primitive.int),
      TypeName.int,
      Doc.Empty,
      Nil
    )

  def long[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Long] =
    new Primitive(
      PrimitiveType.Long(Validation.None),
      F.fromBinding(Binding.Primitive.long),
      TypeName.long,
      Doc.Empty,
      Nil
    )

  def float[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Float] =
    new Primitive(
      PrimitiveType.Float(Validation.None),
      F.fromBinding(Binding.Primitive.float),
      TypeName.float,
      Doc.Empty,
      Nil
    )

  def double[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Double] =
    new Primitive(
      PrimitiveType.Double(Validation.None),
      F.fromBinding(Binding.Primitive.double),
      TypeName.double,
      Doc.Empty,
      Nil
    )

  def char[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Char] =
    new Primitive(
      PrimitiveType.Char(Validation.None),
      F.fromBinding(Binding.Primitive.char),
      TypeName.char,
      Doc.Empty,
      Nil
    )

  def string[F[_, _]](implicit F: FromBinding[F]): Reflect[F, String] =
    new Primitive(
      PrimitiveType.String(Validation.None),
      F.fromBinding(Binding.Primitive.string),
      TypeName.string,
      Doc.Empty,
      Nil
    )

  def bigInt[F[_, _]](implicit F: FromBinding[F]): Reflect[F, BigInt] =
    new Primitive(
      PrimitiveType.BigInt(Validation.None),
      F.fromBinding(Binding.Primitive.bigInt),
      TypeName.bigInt,
      Doc.Empty,
      Nil
    )

  def bigDecimal[F[_, _]](implicit F: FromBinding[F]): Reflect[F, BigDecimal] =
    new Primitive(
      PrimitiveType.BigDecimal(Validation.None),
      F.fromBinding(Binding.Primitive.bigDecimal),
      TypeName.bigDecimal,
      Doc.Empty,
      Nil
    )

  def dayOfWeek[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.DayOfWeek] =
    new Primitive(
      PrimitiveType.DayOfWeek(Validation.None),
      F.fromBinding(Binding.Primitive.dayOfWeek),
      TypeName.dayOfWeek,
      Doc.Empty,
      Nil
    )

  def duration[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Duration] =
    new Primitive(
      PrimitiveType.Duration(Validation.None),
      F.fromBinding(Binding.Primitive.duration),
      TypeName.duration,
      Doc.Empty,
      Nil
    )

  def instant[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Instant] =
    new Primitive(
      PrimitiveType.Instant(Validation.None),
      F.fromBinding(Binding.Primitive.instant),
      TypeName.instant,
      Doc.Empty,
      Nil
    )

  def localDate[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.LocalDate] =
    new Primitive(
      PrimitiveType.LocalDate(Validation.None),
      F.fromBinding(Binding.Primitive.localDate),
      TypeName.localDate,
      Doc.Empty,
      Nil
    )

  def localDateTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.LocalDateTime] =
    new Primitive(
      PrimitiveType.LocalDateTime(Validation.None),
      F.fromBinding(Binding.Primitive.localDateTime),
      TypeName.localDateTime,
      Doc.Empty,
      Nil
    )

  def localTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.LocalTime] =
    new Primitive(
      PrimitiveType.LocalTime(Validation.None),
      F.fromBinding(Binding.Primitive.localTime),
      TypeName.localTime,
      Doc.Empty,
      Nil
    )

  def month[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Month] =
    new Primitive(
      PrimitiveType.Month(Validation.None),
      F.fromBinding(Binding.Primitive.month),
      TypeName.month,
      Doc.Empty,
      Nil
    )

  def monthDay[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.MonthDay] =
    new Primitive(
      PrimitiveType.MonthDay(Validation.None),
      F.fromBinding(Binding.Primitive.monthDay),
      TypeName.monthDay,
      Doc.Empty,
      Nil
    )

  def offsetDateTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.OffsetDateTime] =
    new Primitive(
      PrimitiveType.OffsetDateTime(Validation.None),
      F.fromBinding(Binding.Primitive.offsetDateTime),
      TypeName.offsetDateTime,
      Doc.Empty,
      Nil
    )

  def offsetTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.OffsetTime] =
    new Primitive(
      PrimitiveType.OffsetTime(Validation.None),
      F.fromBinding(Binding.Primitive.offsetTime),
      TypeName.offsetTime,
      Doc.Empty,
      Nil
    )

  def period[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Period] =
    new Primitive(
      PrimitiveType.Period(Validation.None),
      F.fromBinding(Binding.Primitive.period),
      TypeName.period,
      Doc.Empty,
      Nil
    )

  def year[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Year] =
    new Primitive(
      PrimitiveType.Year(Validation.None),
      F.fromBinding(Binding.Primitive.year),
      TypeName.year,
      Doc.Empty,
      Nil
    )

  def yearMonth[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.YearMonth] =
    new Primitive(
      PrimitiveType.YearMonth(Validation.None),
      F.fromBinding(Binding.Primitive.yearMonth),
      TypeName.yearMonth,
      Doc.Empty,
      Nil
    )

  def zoneId[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.ZoneId] =
    new Primitive(
      PrimitiveType.ZoneId(Validation.None),
      F.fromBinding(Binding.Primitive.zoneId),
      TypeName.zoneId,
      Doc.Empty,
      Nil
    )

  def zoneOffset[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.ZoneOffset] =
    new Primitive(
      PrimitiveType.ZoneOffset(Validation.None),
      F.fromBinding(Binding.Primitive.zoneOffset),
      TypeName.zoneOffset,
      Doc.Empty,
      Nil
    )

  def zonedDateTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.ZonedDateTime] =
    new Primitive(
      PrimitiveType.ZonedDateTime(Validation.None),
      F.fromBinding(Binding.Primitive.zonedDateTime),
      TypeName.zonedDateTime,
      Doc.Empty,
      Nil
    )

  def currency[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.util.Currency] =
    new Primitive(
      PrimitiveType.Currency(Validation.None),
      F.fromBinding(Binding.Primitive.currency),
      TypeName.currency,
      Doc.Empty,
      Nil
    )

  def uuid[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.util.UUID] =
    new Primitive(
      PrimitiveType.UUID(Validation.None),
      F.fromBinding(Binding.Primitive.uuid),
      TypeName.uuid,
      Doc.Empty,
      Nil
    )

  def dynamic[F[_, _]](implicit F: FromBinding[F]): Dynamic[F] =
    new Dynamic(F.fromBinding(Binding.Dynamic()), Doc.Empty, Nil)

  def set[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, Set] =
    new Sequence(element, F.fromBinding(Binding.Seq.set), TypeName.set[A], Doc.Empty, Nil)

  def list[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, List] =
    new Sequence(element, F.fromBinding(Binding.Seq.list), TypeName.list[A], Doc.Empty, Nil)

  def vector[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, Vector] =
    new Sequence(element, F.fromBinding(Binding.Seq.vector), TypeName.vector[A], Doc.Empty, Nil)

  def array[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, Array] =
    new Sequence(element, F.fromBinding(Binding.Seq.array), TypeName.array[A], Doc.Empty, Nil)

  def map[F[_, _], A, B](key: Reflect[F, A], value: Reflect[F, B])(implicit
    F: FromBinding[F]
  ): Map[F, A, B, collection.immutable.Map] =
    new Map(key, value, F.fromBinding(Binding.Map.map), TypeName.map[A, B], Doc.Empty, Nil)

  object Extractors {
    object List {
      def unapply[F[_, _], A](reflect: Reflect[F, List[A]]): Option[Reflect[F, A]] =
        reflect match {
          case Sequence(element, _, tn, _, _) if tn == TypeName.list => new Some(element)
          case _                                                     => None
        }
    }

    object Vector {
      def unapply[F[_, _], A](reflect: Reflect[F, Vector[A]]): Option[Reflect[F, A]] =
        reflect match {
          case Sequence(element, _, tn, _, _) if tn == TypeName.vector => new Some(element)
          case _                                                       => None
        }
    }

    object Set {
      def unapply[F[_, _], A](reflect: Reflect[F, Set[A]]): Option[Reflect[F, A]] =
        reflect match {
          case Sequence(element, _, tn, _, _) if tn == TypeName.set => new Some(element)
          case _                                                    => None
        }
    }

    object Array {
      def unapply[F[_, _], A](reflect: Reflect[F, Array[A]]): Option[Reflect[F, A]] =
        reflect match {
          case Sequence(element, _, tn, _, _) if tn == TypeName.array => new Some(element)
          case _                                                      => None
        }
    }
  }
}
