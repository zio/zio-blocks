package zio.blocks.schema

import zio.blocks.chunk.ChunkBuilder
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._
import zio.blocks.chunk.Chunk
import zio.blocks.typeid.{Owner, TypeId, TypeRepr}
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.reflect.ClassTag

sealed trait Reflect[F[_, _], A] extends Reflectable[A] { self =>
  protected def inner: Any

  final type Structure = A

  type NodeBinding <: BindingType

  def metadata: F[NodeBinding, A]

  def asDynamic: Option[Reflect.Dynamic[F]] = None

  def asMap(implicit ev: IsMap[A]): Option[Reflect.Map[F, ev.Key, ev.Value, ev.Map]] = None

  def asMapUnknown: Option[Reflect.Map.Unknown[F]] = None

  def asPrimitive: Option[Reflect.Primitive[F, A]] = None

  def asRecord: Option[Reflect.Record[F, A]] = None

  def asSequence(implicit ev: IsCollection[A]): Option[Reflect.Sequence[F, ev.Elem, ev.Collection]] = None

  def asSequenceUnknown: Option[Reflect.Sequence.Unknown[F]] = None

  def asTerm[S](name: String): Term[F, S, A] = new Term(name, this)

  def asVariant: Option[Reflect.Variant[F, A]] = None

  def asWrapperUnknown: Option[Reflect.Wrapper.Unknown[F]] = None

  def binding(implicit F: HasBinding[F]): Binding[NodeBinding, A]

  def examples(implicit F: HasBinding[F]): Seq[A]

  def examples(value: A, values: A*)(implicit F: HasBinding[F]): Reflect[F, A]

  def defaultValue(value: => A)(implicit F: HasBinding[F]): Reflect[F, A]

  def doc(value: Doc): Reflect[F, A]

  def doc(value: String): Reflect[F, A] = doc(Doc.Text(value))

  override def equals(obj: Any): Boolean = obj match {
    case that: Reflect[?, ?] => (this eq that) || inner == that.inner
    case _                   => false
  }

  def fromDynamicValue(value: DynamicValue)(implicit F: HasBinding[F]): Either[SchemaError, A] =
    fromDynamicValue(value, Nil)

  private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
    F: HasBinding[F]
  ): Either[SchemaError, A]

  def get[B](optic: Optic[A, B]): Option[Reflect[F, B]] = get(optic.toDynamic).asInstanceOf[Option[Reflect[F, B]]]

  def get(dynamic: DynamicOptic): Option[Reflect[F, ?]] = {
    @tailrec
    def loop(current: Reflect[F, ?], idx: Int): Option[Reflect[F, ?]] =
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
            case _: DynamicOptic.Node.AtIndex | _: DynamicOptic.Node.AtIndices | _: DynamicOptic.Node.Elements.type =>
              current.asSequenceUnknown match {
                case Some(unknown) => unknown.sequence.element
                case _             => return None
              }
            case _: DynamicOptic.Node.Wrapped.type =>
              current.asWrapperUnknown match {
                case Some(unknown) => unknown.wrapper.wrapped
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

    loop(this, 0)
  }

  def getDefaultValue(implicit F: HasBinding[F]): Option[A]

  override def hashCode: Int = inner.hashCode

  def isDynamic: Boolean = false

  def isMap: Boolean = false

  def isPrimitive: Boolean = false

  def isRecord: Boolean = false

  def isSequence: Boolean = false

  def isVariant: Boolean = false

  def isWrapper: Boolean = false

  def isCollection: Boolean = isSequence || isMap

  def isOption: Boolean = isVariant && {
    val variant = asVariant.get
    val tid     = typeId
    val cases   = variant.cases
    tid.owner == Owner.fromPackagePath("scala") && tid.name == "Option" &&
    cases.length == 2 && cases(1).name == "Some"
  }

  def isEnumeration: Boolean = isVariant && asVariant.get.cases.forall { case_ =>
    val caseReflect = case_.value
    caseReflect.asRecord.exists(_.fields.isEmpty) ||
    caseReflect.isEnumeration
  }

  def optionInnerType: Option[Reflect[F, ?]] =
    if (!isOption) None
    else asVariant.get.cases(1).value.asRecord.map(_.fields(0).value)

  def modifiers: Seq[Modifier.Reflect]

  def modifier(modifier: Modifier.Reflect): Reflect[F, A]

  def modifiers(modifiers: Iterable[Modifier.Reflect]): Reflect[F, A]

  def nodeType: Reflect.Type { type NodeBinding = self.NodeBinding }

  lazy val noBinding: Reflect[NoBinding, A] = transform(DynamicOptic.root, ReflectTransformer.noBinding()).force

  def toDynamicValue(value: A)(implicit F: HasBinding[F]): DynamicValue

  def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Reflect[G, A]]

  def typeId: TypeId[A]

  def typeId(value: TypeId[A]): Reflect[F, A]

  def updated[B](optic: Optic[A, B])(f: Reflect[F, B] => Reflect[F, B]): Option[Reflect[F, A]] =
    updated(optic.toDynamic)(new Reflect.Updater[F] {
      def update[C](reflect: Reflect[F, C]): Reflect[F, C] =
        f(reflect.asInstanceOf[Reflect[F, B]]).asInstanceOf[Reflect[F, C]]
    })

  def updated[B](dynamic: DynamicOptic)(f: Reflect.Updater[F]): Option[Reflect[F, A]] = {
    def loop(current: Reflect[F, ?], idx: Int): Option[Reflect[F, ?]] =
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
          case _: DynamicOptic.Node.AtIndex | _: DynamicOptic.Node.AtIndices | _: DynamicOptic.Node.Elements.type =>
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
          case _: DynamicOptic.Node.Wrapped.type =>
            current.asWrapperUnknown match {
              case Some(unknown) =>
                val wrapper = unknown.wrapper
                loop(wrapper.wrapped, idx + 1) match {
                  case Some(element) =>
                    new Some(wrapper.copy(wrapped = element.asInstanceOf[Reflect[F, unknown.Wrapped]]))
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

  def aspect[Min >: A, Max <: A](aspect: SchemaAspect[Min, Max, F]): Reflect[F, A] = aspect(self)

  def aspect[B, Min >: B, Max <: B](optic: Optic[A, B], aspect: SchemaAspect[Min, Max, F]): Reflect[F, A] =
    self.updated[B](optic)(aspect(_)).getOrElse(self)
}

object Reflect {

  /**
   * A [[Reflect]] with runtime bindings, capable of constructing and
   * deconstructing values.
   */
  type Bound[A] = Reflect[Binding, A]

  /**
   * A [[Reflect]] without runtime bindings, used for structural inspection and
   * validation.
   *
   * `Unbound` reflects retain all schema metadata (type names, documentation,
   * validations) but lack the runtime machinery to construct or deconstruct
   * actual values. They are used by [[DynamicSchema]] for runtime validation of
   * [[DynamicValue]] instances.
   */
  type Unbound[A] = Reflect[NoBinding, A]

  sealed trait Type {
    type NodeBinding <: BindingType
  }

  object Type {
    case object Record extends Type {
      type NodeBinding = BindingType.Record
    }

    case object Variant extends Type {
      type NodeBinding = BindingType.Variant
    }

    case class Sequence[C[_]]() extends Type {
      type NodeBinding = BindingType.Seq[C]
    }

    case class Map[M[_, _]]() extends Type {
      type NodeBinding = BindingType.Map[M]
    }

    case object Dynamic extends Type {
      type NodeBinding = BindingType.Dynamic
    }

    case object Primitive extends Type {
      type NodeBinding = BindingType.Primitive
    }

    case class Wrapper[A, B]() extends Type {
      type NodeBinding = BindingType.Wrapper[A, B]
    }
  }

  trait Updater[F[_, _]] {
    def update[A](reflect: Reflect[F, A]): Reflect[F, A]
  }

  case class Record[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    recordBinding: F[BindingType.Record, A],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Reflect] = Nil,
    storedDefaultValue: Option[DynamicValue] = None,
    storedExamples: collection.immutable.Seq[DynamicValue] = Nil
  ) extends Reflect[F, A] { self =>
    private[this] val fieldValues      = fields.map(_.value).toArray
    private[this] val fieldIndexByName = new StringToIntMap(fields.length) {
      fields.foreach {
        var idx = 0
        term =>
          put(term.name, idx)
          idx += 1
      }
    }

    protected def inner: Any = (fields, typeId, doc, modifiers)

    type NodeBinding = BindingType.Record

    def doc(value: Doc): Record[F, A] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[A] =
      storedDefaultValue.flatMap(dv => fromDynamicValue(dv).toOption)

    def defaultValue(value: => A)(implicit F: HasBinding[F]): Record[F, A] =
      copy(storedDefaultValue = Some(toDynamicValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[A] =
      storedExamples.flatMap(dv => fromDynamicValue(dv).toOption)

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Record[F, A] =
      copy(storedExamples = (value +: values).map(toDynamicValue))

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Record, A] = F.binding(recordBinding)

    def constructor(implicit F: HasBinding[F]): Constructor[A] = F.constructor(recordBinding)

    def deconstructor(implicit F: HasBinding[F]): Deconstructor[A] = F.deconstructor(recordBinding)

    def fieldByName(name: String): Option[Term[F, A, ?]] = {
      val idx = fieldIndexByName.get(name)
      if (idx >= 0) new Some(fields(idx))
      else None
    }

    private[schema] def fieldIndexByName(name: String): Int = fieldIndexByName.get(name)

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
          val len         = fields.length
          var fieldIdx    = 0
          while (fieldIdx < len) {
            val kv   = fields(fieldIdx)
            val name = kv._1
            val idx  = fieldIndexByName.get(name)
            if (idx >= 0) {
              val fieldValue = fieldValues(idx)
              if (fieldValue ne null) {
                fieldValues(idx) = null
                fieldValue.fromDynamicValue(kv._2, new DynamicOptic.Node.Field(name) :: trace) match {
                  case Right(value) => this.registers(idx).set(registers, 0, value)
                  case Left(error)  => addError(error)
                }
              } else addError(SchemaError.duplicatedField(trace, name))
            }
            fieldIdx += 1
          }
          var idx = 0
          while (idx < fieldValues.length) {
            if (fieldValues(idx) ne null) addError(SchemaError.missingField(trace, this.fields(idx).name))
            idx += 1
          }
          if (error.isDefined) new Left(error.get)
          else new Right(constructor.construct(registers, 0))
        case _ => new Left(SchemaError.expectationMismatch(trace, "Expected a record"))
      }

    def lensByName[B](name: String): Option[Lens[A, B]] = lensByIndex(fieldIndexByName.get(name))

    def lensByIndex[B](index: Int): Option[Lens[A, B]] =
      if (index >= 0 && index < fields.length) {
        new Some(Lens(this.asInstanceOf[Reflect.Record.Bound[A]], fields(index).asInstanceOf[Term.Bound[A, B]]))
      } else None

    def metadata: F[NodeBinding, A] = recordBinding

    def modifier(modifier: Modifier.Reflect): Record[F, A] = copy(modifiers = modifiers :+ modifier)

    def modifiers(modifiers: Iterable[Modifier.Reflect]): Record[F, A] = copy(modifiers = this.modifiers ++ modifiers)

    def modifyField(name: String)(f: Term.Updater[F]): Option[Record[F, A]] = {
      val idx = fieldIndexByName.get(name)
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
      deconstructor.deconstruct(registers, 0, value)
      val len    = this.registers.length
      val fields = ChunkBuilder.make[(String, DynamicValue)](len)
      var idx    = 0
      while (idx < len) {
        val field    = this.fields(idx)
        val register = this.registers(idx)
        fields.addOne(
          (
            field.name,
            field.value
              .asInstanceOf[Reflect[F, field.Focus]]
              .toDynamicValue(register.get(registers, 0).asInstanceOf[field.Focus])
          )
        )
        idx += 1
      }
      new DynamicValue.Record(fields.result())
    }

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Record[G, A]] =
      for {
        fields <- Lazy.foreach(fields)(_.transform(path, Term.Type.Record, f))
        record <-
          f.transformRecord(path, fields, typeId, recordBinding, doc, modifiers, storedDefaultValue, storedExamples)
      } yield record

    lazy val registers: IndexedSeq[Register[Any]] = ArraySeq.unsafeWrapArray(Record.registers(fieldValues))

    lazy val usedRegisters: RegisterOffset = Record.usedRegisters(
      registers.asInstanceOf[ArraySeq[Register[Any]]].unsafeArray.asInstanceOf[Array[Register[Any]]]
    )

    def typeId(value: TypeId[A]): Record[F, A] = copy(typeId = value)

    def nodeType: Reflect.Type.Record.type = Reflect.Type.Record

    override def asRecord: Option[Reflect.Record[F, A]] = new Some(this)

    override def isRecord: Boolean = true

    override def toString: String = ReflectPrinter.printRecord(this)
  }

  object Record {
    type Bound[A] = Record[Binding, A]

    def registers[F[_, _]](reflects: Array[Reflect[F, ?]]): Array[Register[Any]] = {
      var offset    = 0L
      val registers = new Array[Register[?]](reflects.length)
      var idx       = 0
      reflects.foreach { fieldValue =>
        unwrapToPrimitiveTypeOption(fieldValue) match {
          case Some(primitiveType) =>
            primitiveType match {
              case PrimitiveType.Unit =>
                registers(idx) = Register.Unit
              case _: PrimitiveType.Boolean =>
                registers(idx) = new Register.Boolean(offset)
                offset = RegisterOffset.incrementBooleansAndBytes(offset)
              case _: PrimitiveType.Byte =>
                registers(idx) = new Register.Byte(offset)
                offset = RegisterOffset.incrementBooleansAndBytes(offset)
              case _: PrimitiveType.Char =>
                registers(idx) = new Register.Char(offset)
                offset = RegisterOffset.incrementCharsAndShorts(offset)
              case _: PrimitiveType.Short =>
                registers(idx) = new Register.Short(offset)
                offset = RegisterOffset.incrementCharsAndShorts(offset)
              case _: PrimitiveType.Float =>
                registers(idx) = new Register.Float(offset)
                offset = RegisterOffset.incrementFloatsAndInts(offset)
              case _: PrimitiveType.Int =>
                registers(idx) = new Register.Int(offset)
                offset = RegisterOffset.incrementFloatsAndInts(offset)
              case _: PrimitiveType.Double =>
                registers(idx) = new Register.Double(offset)
                offset = RegisterOffset.incrementDoublesAndLongs(offset)
              case _: PrimitiveType.Long =>
                registers(idx) = new Register.Long(offset)
                offset = RegisterOffset.incrementDoublesAndLongs(offset)
              case _ =>
                registers(idx) = new Register.Object(offset)
                offset = RegisterOffset.incrementObjects(offset)
            }
          case _ =>
            registers(idx) = new Register.Object(offset)
            offset = RegisterOffset.incrementObjects(offset)
        }
        idx += 1
      }
      registers.asInstanceOf[Array[Register[Any]]]
    }

    def usedRegisters(registers: Array[Register[Any]]): RegisterOffset = {
      var offset = 0L
      var idx    = 0
      while (idx < registers.length) {
        offset = RegisterOffset.add(registers(idx).usedRegisters, offset)
        idx += 1
      }
      offset
    }
  }

  case class Variant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ? <: A]],
    typeId: TypeId[A],
    variantBinding: F[BindingType.Variant, A],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Reflect] = Nil,
    storedDefaultValue: Option[DynamicValue] = None,
    storedExamples: collection.immutable.Seq[DynamicValue] = Nil
  ) extends Reflect[F, A] {
    private[this] val caseIndexByName = new StringToIntMap(cases.length) {
      cases.foreach {
        var idx = 0
        term =>
          put(term.name, idx)
          idx += 1
      }
    }

    protected def inner: Any = (cases, typeId, doc, modifiers)

    type NodeBinding = BindingType.Variant

    def doc(value: Doc): Variant[F, A] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[A] =
      storedDefaultValue.flatMap(dv => fromDynamicValue(dv).toOption)

    def defaultValue(value: => A)(implicit F: HasBinding[F]): Variant[F, A] =
      copy(storedDefaultValue = Some(toDynamicValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[A] =
      storedExamples.flatMap(dv => fromDynamicValue(dv).toOption)

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Variant[F, A] =
      copy(storedExamples = (value +: values).map(toDynamicValue))

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Variant, A] = F.binding(variantBinding)

    def caseByName(name: String): Option[Term[F, A, ? <: A]] = {
      val idx = caseIndexByName.get(name)
      if (idx >= 0) new Some(cases(idx))
      else None
    }

    private[schema] def caseIndexByName(name: String): Int = caseIndexByName.get(name)

    def discriminator(implicit F: HasBinding[F]): Discriminator[A] = F.discriminator(variantBinding)

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, A] =
      value match {
        case DynamicValue.Variant(discriminator, value) =>
          val idx = caseIndexByName.get(discriminator)
          if (idx >= 0) {
            val case_ = cases(idx)
            case_.value
              .asInstanceOf[Reflect[F, A]]
              .fromDynamicValue(value, new DynamicOptic.Node.Case(case_.name) :: trace)
          } else new Left(SchemaError.unknownCase(trace, discriminator))
        case _ => new Left(SchemaError.expectationMismatch(trace, "Expected a variant"))
      }

    def matchers(implicit F: HasBinding[F]): Matchers[A] = F.matchers(variantBinding)

    def metadata: F[NodeBinding, A] = variantBinding

    def modifier(modifier: Modifier.Reflect): Variant[F, A] = copy(modifiers = modifiers :+ modifier)

    def modifiers(modifiers: Iterable[Modifier.Reflect]): Variant[F, A] = copy(modifiers = this.modifiers ++ modifiers)

    def modifyCase(name: String)(f: Term.Updater[F]): Option[Variant[F, A]] = {
      val idx = caseIndexByName.get(name)
      if (idx >= 0) {
        f.update(cases(idx)) match {
          case Some(case_) => new Some(copy(cases = cases.updated(idx, case_)))
          case _           => None
        }
      } else None
    }

    def prismByName[B <: A](name: String): Option[Prism[A, B]] = prismByIndex(caseIndexByName.get(name))

    def prismByIndex[B <: A](index: Int): Option[Prism[A, B]] =
      if (index >= 0 && index < cases.length) {
        new Some(Prism(this.asInstanceOf[Reflect.Variant.Bound[A]], cases(index).asInstanceOf[Term.Bound[A, B]]))
      } else None

    def toDynamicValue(value: A)(implicit F: HasBinding[F]): DynamicValue = {
      val case_ = cases(discriminator.discriminate(value))
      new DynamicValue.Variant(case_.name, case_.value.asInstanceOf[Reflect[F, A]].toDynamicValue(value))
    }

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Variant[G, A]] =
      for {
        cases   <- Lazy.foreach(cases)(_.transform(path, Term.Type.Variant, f))
        variant <-
          f.transformVariant(path, cases, typeId, variantBinding, doc, modifiers, storedDefaultValue, storedExamples)
      } yield variant

    def typeId(value: TypeId[A]): Variant[F, A] = copy(typeId = value)

    def nodeType: Reflect.Type.Variant.type = Reflect.Type.Variant

    override def asVariant: Option[Reflect.Variant[F, A]] = new Some(this)

    override def isVariant: Boolean = true

    override def toString: String = ReflectPrinter.printVariant(this)
  }

  object Variant {
    type Bound[A] = Variant[Binding, A]
  }

  case class Sequence[F[_, _], A, C[_]](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    seqBinding: F[BindingType.Seq[C], C[A]],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Reflect] = Nil,
    storedDefaultValue: Option[DynamicValue] = None,
    storedExamples: collection.immutable.Seq[DynamicValue] = Nil
  ) extends Reflect[F, C[A]] { self =>
    require(element ne null)

    protected def inner: Any = (element, typeId, doc, modifiers)

    type NodeBinding = BindingType.Seq[C]

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Seq[C], C[A]] = F.binding(seqBinding)

    def doc(value: Doc): Sequence[F, A, C] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[C[A]] =
      storedDefaultValue.flatMap(dv => fromDynamicValue(dv).toOption)

    def defaultValue(value: => C[A])(implicit F: HasBinding[F]): Sequence[F, A, C] =
      copy(storedDefaultValue = Some(toDynamicValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[C[A]] =
      storedExamples.flatMap(dv => fromDynamicValue(dv).toOption)

    def examples(value: C[A], values: C[A]*)(implicit F: HasBinding[F]): Sequence[F, A, C] =
      copy(storedExamples = (value +: values).map(toDynamicValue))

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, C[A]] = {
      var error: Option[SchemaError] = None

      def addError(e: SchemaError): Unit = error = error.map(_ ++ e).orElse(Some(e))

      value match {
        case DynamicValue.Sequence(elements) =>
          val seqTrace                       = DynamicOptic.Node.Elements :: trace
          val constructor                    = seqConstructor
          var idx                            = -1
          implicit val classTag: ClassTag[A] = elemClassTag
          val builder                        = constructor.newBuilder[A](elements.size)
          elements.foreach { elem =>
            idx += 1
            element.fromDynamicValue(elem, new DynamicOptic.Node.AtIndex(idx) :: seqTrace) match {
              case Right(value) => constructor.add(builder, value)
              case Left(error)  => addError(error)
            }
          }
          if (error.isDefined) new Left(error.get)
          else new Right(constructor.result(builder))
        case _ => new Left(SchemaError.expectationMismatch(trace, "Expected a sequence"))
      }
    }

    def metadata: F[NodeBinding, C[A]] = seqBinding

    def modifier(modifier: Modifier.Reflect): Sequence[F, A, C] = copy(modifiers = modifiers :+ modifier)

    def modifiers(modifiers: Iterable[Modifier.Reflect]): Sequence[F, A, C] =
      copy(modifiers = this.modifiers ++ modifiers)

    def toDynamicValue(value: C[A])(implicit F: HasBinding[F]): DynamicValue = {
      val iterator = seqDeconstructor.deconstruct(value)
      val builder  = ChunkBuilder.make[DynamicValue](seqDeconstructor.size(value))
      while (iterator.hasNext) builder.addOne(element.toDynamicValue(iterator.next()))
      new DynamicValue.Sequence(builder.result())
    }

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Sequence[G, A, C]] =
      for {
        element  <- element.transform(path(DynamicOptic.elements), f)
        sequence <-
          f.transformSequence(path, element, typeId, seqBinding, doc, modifiers, storedDefaultValue, storedExamples)
      } yield sequence

    def seqConstructor(implicit F: HasBinding[F]): SeqConstructor[C] = F.seqConstructor(seqBinding)

    def seqDeconstructor(implicit F: HasBinding[F]): SeqDeconstructor[C] = F.seqDeconstructor(seqBinding)

    def elemClassTag: ClassTag[A] = element.typeId.classTag.asInstanceOf[ClassTag[A]]

    def typeId(value: TypeId[C[A]]): Sequence[F, A, C] = copy(typeId = value)

    def nodeType: Reflect.Type.Sequence[C] = new Reflect.Type.Sequence

    override def asSequence(implicit ev: IsCollection[C[A]]): Option[Reflect.Sequence[F, ev.Elem, ev.Collection]] =
      new Some(this.asInstanceOf[Reflect.Sequence[F, ev.Elem, ev.Collection]])

    override def asSequenceUnknown: Option[Reflect.Sequence.Unknown[F]] = new Some(new Reflect.Sequence.Unknown[F] {
      def sequence: Reflect.Sequence[F, ElementType, CollectionType] =
        self.asInstanceOf[Reflect.Sequence[F, ElementType, CollectionType]]
    })

    override def isSequence: Boolean = true

    override def toString: String = ReflectPrinter.printSequence(this)
  }

  object Sequence {
    type Bound[A, C[_]] = Sequence[Binding, A, C]

    trait Unknown[F[_, _]] {
      type CollectionType[_]
      type ElementType

      def sequence: Reflect.Sequence[F, ElementType, CollectionType]
    }
  }

  case class Map[F[_, _], K, V, M[_, _]](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    mapBinding: F[BindingType.Map[M], M[K, V]],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Reflect] = Nil,
    storedDefaultValue: Option[DynamicValue] = None,
    storedExamples: collection.immutable.Seq[DynamicValue] = Nil
  ) extends Reflect[F, M[K, V]] { self =>
    require((key ne null) && (value ne null))

    protected def inner: Any = (key, value, typeId, doc, modifiers)

    type NodeBinding = BindingType.Map[M]

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Map[M], M[K, V]] = F.binding(mapBinding)

    def doc(value: Doc): Map[F, K, V, M] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[M[K, V]] =
      storedDefaultValue.flatMap(dv => fromDynamicValue(dv).toOption)

    def defaultValue(value: => M[K, V])(implicit F: HasBinding[F]): Map[F, K, V, M] =
      copy(storedDefaultValue = Some(toDynamicValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[M[K, V]] =
      storedExamples.flatMap(dv => fromDynamicValue(dv).toOption)

    def examples(value: M[K, V], values: M[K, V]*)(implicit F: HasBinding[F]): Map[F, K, V, M] =
      copy(storedExamples = (value +: values).map(toDynamicValue))

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, M[K, V]] = {
      var error: Option[SchemaError] = None

      def addError(e: SchemaError): Unit = error = error.map(_ ++ e).orElse(Some(e))

      value match {
        case DynamicValue.Map(elements) =>
          val keyTrace    = DynamicOptic.Node.MapKeys :: trace
          val valueTrace  = DynamicOptic.Node.MapValues :: trace
          val constructor = mapConstructor
          val builder     = constructor.newObjectBuilder[K, V](elements.size)
          elements.foreach { case (key, value) =>
            this.key.fromDynamicValue(key, keyTrace) match {
              case Right(keyValue) =>
                this.value.fromDynamicValue(value, new DynamicOptic.Node.AtMapKey(key) :: valueTrace) match {
                  case Right(valueValue) => constructor.addObject(builder, keyValue, valueValue)
                  case Left(error)       => addError(error)
                }
              case Left(error) => addError(error)
            }
          }
          if (error.isDefined) new Left(error.get)
          else new Right(constructor.resultObject(builder))
        case _ => new Left(SchemaError.expectationMismatch(trace, "Expected a map"))
      }
    }

    def mapConstructor(implicit F: HasBinding[F]): MapConstructor[M] = F.mapConstructor(mapBinding)

    def mapDeconstructor(implicit F: HasBinding[F]): MapDeconstructor[M] = F.mapDeconstructor(mapBinding)

    def metadata: F[NodeBinding, M[K, V]] = mapBinding

    def modifier(modifier: Modifier.Reflect): Map[F, K, V, M] = copy(modifiers = modifiers :+ modifier)

    def modifiers(modifiers: Iterable[Modifier.Reflect]): Map[F, K, V, M] =
      copy(modifiers = this.modifiers ++ modifiers)

    def toDynamicValue(value: M[K, V])(implicit F: HasBinding[F]): DynamicValue = {
      val deconstructor = mapDeconstructor
      val it            = deconstructor.deconstruct(value)
      val builder       = ChunkBuilder.make[(DynamicValue, DynamicValue)](deconstructor.size(value))
      while (it.hasNext) {
        val next = it.next()
        builder.addOne(
          (this.key.toDynamicValue(deconstructor.getKey(next)), this.value.toDynamicValue(deconstructor.getValue(next)))
        )
      }
      new DynamicValue.Map(builder.result())
    }

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Map[G, K, V, M]] =
      for {
        key   <- key.transform(path(DynamicOptic.mapKeys), f)
        value <- value.transform(path(DynamicOptic.mapValues), f)
        map   <-
          f.transformMap(path, key, value, typeId, mapBinding, doc, modifiers, storedDefaultValue, storedExamples)
      } yield map

    def typeId(value: TypeId[M[K, V]]): Map[F, K, V, M] = copy(typeId = value)

    def nodeType: Reflect.Type.Map[M] = new Reflect.Type.Map

    override def asMap(implicit ev: IsMap[M[K, V]]): Option[Reflect.Map[F, ev.Key, ev.Value, ev.Map]] =
      new Some(this.asInstanceOf[Reflect.Map[F, ev.Key, ev.Value, ev.Map]])

    override def asMapUnknown: Option[Reflect.Map.Unknown[F]] = new Some(new Reflect.Map.Unknown[F] {
      def map: Reflect.Map[F, KeyType, ValueType, MapType] =
        self.asInstanceOf[Reflect.Map[F, KeyType, ValueType, MapType]]
    })

    override def isMap: Boolean = true

    override def toString: String = ReflectPrinter.printMap(this)
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
    typeId: TypeId[DynamicValue] = TypeId.of[DynamicValue],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Reflect] = Nil,
    storedDefaultValue: Option[DynamicValue] = None,
    storedExamples: collection.immutable.Seq[DynamicValue] = Nil
  ) extends Reflect[F, DynamicValue] {
    protected def inner: Any = (modifiers, doc)

    type NodeBinding = BindingType.Dynamic

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Dynamic, DynamicValue] = F.binding(dynamicBinding)

    def doc(value: Doc): Dynamic[F] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[DynamicValue] = storedDefaultValue

    def defaultValue(value: => DynamicValue)(implicit F: HasBinding[F]): Dynamic[F] =
      copy(storedDefaultValue = Some(value))

    def examples(implicit F: HasBinding[F]): Seq[DynamicValue] = storedExamples

    def examples(value: DynamicValue, values: DynamicValue*)(implicit F: HasBinding[F]): Dynamic[F] =
      copy(storedExamples = value +: values)

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, DynamicValue] = new Right(value)

    def metadata: F[NodeBinding, DynamicValue] = dynamicBinding

    def modifier(modifier: Modifier.Reflect): Dynamic[F] = copy(modifiers = modifiers :+ modifier)

    def modifiers(modifiers: Iterable[Modifier.Reflect]): Dynamic[F] = copy(modifiers = this.modifiers ++ modifiers)

    def toDynamicValue(value: DynamicValue)(implicit F: HasBinding[F]): DynamicValue = value

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Dynamic[G]] =
      for {
        dynamic <-
          f.transformDynamic(path, typeId, dynamicBinding, doc, modifiers, storedDefaultValue, storedExamples)
      } yield dynamic

    def typeId(value: TypeId[DynamicValue]): Dynamic[F] = copy(typeId = value)

    def nodeType: Reflect.Type.Dynamic.type = Reflect.Type.Dynamic

    override def asDynamic: Option[Reflect.Dynamic[F]] = new Some(this)

    override def isDynamic: Boolean = true

    override def toString: String = ReflectPrinter.sdlTypeName(typeId)
  }

  object Dynamic {
    type Bound = Dynamic[Binding]
  }

  case class Primitive[F[_, _], A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    primitiveBinding: F[BindingType.Primitive, A],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Reflect] = Nil,
    storedDefaultValue: Option[DynamicValue] = None,
    storedExamples: collection.immutable.Seq[DynamicValue] = Nil
  ) extends Reflect[F, A] { self =>
    protected def inner: Any = (primitiveType, typeId, doc, modifiers)

    type NodeBinding = BindingType.Primitive

    def binding(implicit F: HasBinding[F]): Binding.Primitive[A] = F.primitive(primitiveBinding)

    def doc(value: Doc): Primitive[F, A] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[A] =
      storedDefaultValue.flatMap(dv => primitiveType.fromDynamicValue(dv, Nil).toOption)

    def defaultValue(value: => A)(implicit F: HasBinding[F]): Primitive[F, A] =
      copy(storedDefaultValue = Some(primitiveType.toDynamicValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[A] =
      storedExamples.flatMap(dv => primitiveType.fromDynamicValue(dv, Nil).toOption)

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Primitive[F, A] =
      copy(storedExamples = (value +: values).map(primitiveType.toDynamicValue))

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, A] = primitiveType.fromDynamicValue(value, trace)

    def metadata: F[NodeBinding, A] = primitiveBinding

    def modifier(modifier: Modifier.Reflect): Primitive[F, A] = copy(modifiers = modifiers :+ modifier)

    def modifiers(modifiers: Iterable[Modifier.Reflect]): Primitive[F, A] =
      copy(modifiers = this.modifiers ++ modifiers)

    def toDynamicValue(value: A)(implicit F: HasBinding[F]): DynamicValue = primitiveType.toDynamicValue(value)

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Primitive[G, A]] =
      for {
        primitive <- f.transformPrimitive(
                       path,
                       primitiveType,
                       typeId,
                       primitiveBinding,
                       doc,
                       modifiers,
                       storedDefaultValue,
                       storedExamples
                     )
      } yield primitive

    def typeId(value: TypeId[A]): Primitive[F, A] = copy(typeId = value)

    def nodeType: Reflect.Type.Primitive.type = Reflect.Type.Primitive

    override def asPrimitive: Option[Reflect.Primitive[F, A]] = new Some(this)

    override def isPrimitive: Boolean = true

    override def toString: String = ReflectPrinter.printPrimitive(this)
  }

  object Primitive {
    type Bound[A] = Primitive[Binding, A]
  }

  case class Wrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    wrapperBinding: F[BindingType.Wrapper[A, B], A],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Reflect] = Nil,
    storedDefaultValue: Option[DynamicValue] = None,
    storedExamples: collection.immutable.Seq[DynamicValue] = Nil
  ) extends Reflect[F, A] { self =>
    require((wrapped ne null) && (typeId ne null), "Wrapper requires non-null wrapped and typeId")
    protected def inner: Any = (wrapped, typeId, doc, modifiers)

    def underlyingPrimitiveType: Option[PrimitiveType[A]] =
      PrimitiveType.fromTypeId(typeId)

    type NodeBinding = BindingType.Wrapper[A, B]

    def binding(implicit F: HasBinding[F]): Binding.Wrapper[A, B] = F.wrapper(wrapperBinding)

    def doc(value: Doc): Wrapper[F, A, B] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[A] =
      storedDefaultValue.flatMap(dv => fromDynamicValue(dv).toOption)

    def defaultValue(value: => A)(implicit F: HasBinding[F]): Wrapper[F, A, B] =
      copy(storedDefaultValue = Some(toDynamicValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[A] =
      storedExamples.flatMap(dv => fromDynamicValue(dv).toOption)

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Wrapper[F, A, B] =
      copy(storedExamples = (value +: values).map(toDynamicValue))

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, A] =
      wrapped.fromDynamicValue(value, trace) match {
        case Right(unwrapped) =>
          try Right(binding.wrap(unwrapped))
          catch {
            case error: SchemaError => Left(error)
            case other: Throwable   => Left(SchemaError.validationFailed(other.getMessage))
          }
        case left => left.asInstanceOf[Either[SchemaError, A]]
      }

    def metadata: F[NodeBinding, A] = wrapperBinding

    def modifier(modifier: Modifier.Reflect): Wrapper[F, A, B] = copy(modifiers = modifiers :+ modifier)

    def modifiers(modifiers: Iterable[Modifier.Reflect]): Wrapper[F, A, B] =
      copy(modifiers = this.modifiers ++ modifiers)

    def toDynamicValue(value: A)(implicit F: HasBinding[F]): DynamicValue = {
      val unwrapped = binding.unwrap(value)
      wrapped.toDynamicValue(unwrapped)
    }

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Wrapper[G, A, B]] =
      for {
        wrapped <- wrapped.transform(path, f)
        wrapper <- f.transformWrapper(
                     path,
                     wrapped,
                     typeId,
                     wrapperBinding,
                     doc,
                     modifiers,
                     storedDefaultValue,
                     storedExamples
                   )
      } yield wrapper

    def typeId(value: TypeId[A]): Wrapper[F, A, B] = copy(typeId = value)

    override def asWrapperUnknown: Option[Reflect.Wrapper.Unknown[F]] = new Some(new Reflect.Wrapper.Unknown[F] {
      def wrapper: Reflect.Wrapper[F, Wrapping, Wrapped] = self.asInstanceOf[Reflect.Wrapper[F, Wrapping, Wrapped]]
    })

    override def isWrapper: Boolean = true

    override def toString: String = ReflectPrinter.printWrapper(this)

    def nodeType: Reflect.Type.Wrapper[A, B] = new Reflect.Type.Wrapper
  }

  object Wrapper {
    type Bound[A, B] = Wrapper[Binding, A, B]

    trait Unknown[F[_, _]] {
      type Wrapping
      type Wrapped

      def wrapper: Reflect.Wrapper[F, Wrapping, Wrapped]
    }
  }

  case class Deferred[F[_, _], A](
    _value: () => Reflect[F, A],
    _typeId: Option[TypeId[A]] = None,
    private val deferredDefaultValue: Option[() => A] = None,
    private val deferredExamples: collection.immutable.Seq[() => A] = Nil
  ) extends Reflect[F, A] { self =>
    protected def inner: Any = value.inner

    final lazy val value: Reflect[F, A] = _value()

    type NodeBinding = value.NodeBinding

    def binding(implicit F: HasBinding[F]): Binding[NodeBinding, A] = value.binding

    def doc(value: Doc): Deferred[F, A] = copy(_value = () => _value().doc(value))

    def getDefaultValue(implicit F: HasBinding[F]): Option[A] =
      deferredDefaultValue.map(_()).orElse(value.getDefaultValue)

    def defaultValue(dv: => A)(implicit F: HasBinding[F]): Deferred[F, A] =
      copy(deferredDefaultValue = Some(() => dv))

    def examples(implicit F: HasBinding[F]): Seq[A] =
      if (deferredExamples.nonEmpty) deferredExamples.map(_())
      else value.examples

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Deferred[F, A] =
      copy(deferredExamples = ((() => value) +: values.map(v => () => v)))

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, A] = this.value.fromDynamicValue(value, trace)

    def metadata: F[NodeBinding, A] = value.metadata

    def modifiers: Seq[Modifier.Reflect] = value.modifiers

    def modifier(modifier: Modifier.Reflect): Deferred[F, A] = copy(_value = () => value.modifier(modifier))

    def modifiers(modifiers: Iterable[Modifier.Reflect]): Deferred[F, A] =
      copy(_value = () => value.modifiers(modifiers))

    def doc: Doc = value.doc

    def toDynamicValue(value: A)(implicit F: HasBinding[F]): DynamicValue = this.value.toDynamicValue(value)

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Reflect[G, A]] =
      Lazy {
        val c      = cache.get
        val key    = new IdentityTuple(this, f)
        val cached = c.get(key)
        if (cached ne null) cached.asInstanceOf[Reflect[G, A]]
        else {
          val result = Deferred(() => value.transform(path, f).force, _typeId, deferredDefaultValue, deferredExamples)
          c.put(key, result)
          result
        }
      }

    def typeId: TypeId[A] = _typeId.getOrElse {
      val v = visited.get
      if (v.containsKey(this)) {
        // Cycle detected - create a placeholder TypeId to break recursion
        TypeId.nominal[A]("<deferred-cycle>", Owner.Root)
      } else {
        v.put(this, ())
        try value.typeId
        finally v.remove(this)
      }
    }

    def typeId(newTypeId: TypeId[A]): Deferred[F, A] = copy(_typeId = Some(newTypeId))

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
      case that: Reflect[?, ?] =>
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

    override def asDynamic: Option[Reflect.Dynamic[F]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asDynamic
        finally v.remove(this)
      }
    }

    override def asMap(implicit ev: IsMap[A]): Option[Reflect.Map[F, ev.Key, ev.Value, ev.Map]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asMap(ev)
        finally v.remove(this)
      }
    }

    override def asMapUnknown: Option[Reflect.Map.Unknown[F]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asMapUnknown
        finally v.remove(this)
      }
    }

    override def asPrimitive: Option[Reflect.Primitive[F, A]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asPrimitive
        finally v.remove(this)
      }
    }

    override def asRecord: Option[Reflect.Record[F, A]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asRecord
        finally v.remove(this)
      }
    }

    override def asSequence(implicit ev: IsCollection[A]): Option[Reflect.Sequence[F, ev.Elem, ev.Collection]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asSequence(ev)
        finally v.remove(this)
      }
    }

    override def asSequenceUnknown: Option[Reflect.Sequence.Unknown[F]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asSequenceUnknown
        finally v.remove(this)
      }
    }

    override def asVariant: Option[Reflect.Variant[F, A]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asVariant
        finally v.remove(this)
      }
    }

    override def asWrapperUnknown: Option[Reflect.Wrapper.Unknown[F]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asWrapperUnknown
        finally v.remove(this)
      }
    }

    override def isDynamic: Boolean = {
      val v = visited.get
      if (v.containsKey(this)) false // exit from recursion
      else {
        v.put(this, ())
        try value.isDynamic
        finally v.remove(this)
      }
    }

    override def isMap: Boolean = {
      val v = visited.get
      if (v.containsKey(this)) false // exit from recursion
      else {
        v.put(this, ())
        try value.isMap
        finally v.remove(this)
      }
    }

    override def isPrimitive: Boolean = {
      val v = visited.get
      if (v.containsKey(this)) false // exit from recursion
      else {
        v.put(this, ())
        try value.isPrimitive
        finally v.remove(this)
      }
    }

    override def isRecord: Boolean = {
      val v = visited.get
      if (v.containsKey(this)) false // exit from recursion
      else {
        v.put(this, ())
        try value.isRecord
        finally v.remove(this)
      }
    }

    override def isSequence: Boolean = {
      val v = visited.get
      if (v.containsKey(this)) false // exit from recursion
      else {
        v.put(this, ())
        try value.isSequence
        finally v.remove(this)
      }
    }

    override def isVariant: Boolean = {
      val v = visited.get
      if (v.containsKey(this)) false // exit from recursion
      else {
        v.put(this, ())
        try value.isVariant
        finally v.remove(this)
      }
    }

    override def isWrapper: Boolean = {
      val v = visited.get
      if (v.containsKey(this)) false // exit from recursion
      else {
        v.put(this, ())
        try value.isWrapper
        finally v.remove(this)
      }
    }

    private[this] val visited =
      new ThreadLocal[java.util.IdentityHashMap[AnyRef, Unit]] {
        override def initialValue: java.util.IdentityHashMap[AnyRef, Unit] = new java.util.IdentityHashMap
      }

    private[this] val cache =
      new ThreadLocal[java.util.HashMap[IdentityTuple, AnyRef]] {
        override def initialValue: java.util.HashMap[IdentityTuple, AnyRef] = new java.util.HashMap
      }

    def nodeType = value.nodeType

    override def toString: String = {
      val v = visited.get
      if (v.containsKey(this)) s"deferred => ${typeId}"
      else {
        v.put(this, ())
        try value.toString
        finally v.remove(this)
      }
    }
  }

  private class IdentityTuple(val v1: AnyRef, val v2: AnyRef) {
    override def equals(obj: Any): Boolean = obj match {
      case that: IdentityTuple => (this.v1 eq that.v1) && (this.v2 eq that.v2)
      case _                   => false
    }

    override def hashCode(): Int =
      System.identityHashCode(v1) * 31 + System.identityHashCode(v2)
  }

  object Deferred {
    type Bound[A] = Deferred[Binding, A]
  }

  def unit[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Unit] = primitive(PrimitiveType.Unit)

  def boolean[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Boolean] =
    primitive(new PrimitiveType.Boolean(Validation.None))

  def byte[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Byte] = primitive(new PrimitiveType.Byte(Validation.None))

  def short[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Short] =
    primitive(new PrimitiveType.Short(Validation.None))

  def int[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Int] = primitive(new PrimitiveType.Int(Validation.None))

  def long[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Long] = primitive(new PrimitiveType.Long(Validation.None))

  def float[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Float] =
    primitive(new PrimitiveType.Float(Validation.None))

  def double[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Double] =
    primitive(new PrimitiveType.Double(Validation.None))

  def char[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Char] = primitive(new PrimitiveType.Char(Validation.None))

  def string[F[_, _]](implicit F: FromBinding[F]): Reflect[F, String] =
    primitive(new PrimitiveType.String(Validation.None))

  def bigInt[F[_, _]](implicit F: FromBinding[F]): Reflect[F, BigInt] =
    primitive(new PrimitiveType.BigInt(Validation.None))

  def bigDecimal[F[_, _]](implicit F: FromBinding[F]): Reflect[F, BigDecimal] =
    primitive(new PrimitiveType.BigDecimal(Validation.None))

  def dayOfWeek[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.DayOfWeek] =
    primitive(new PrimitiveType.DayOfWeek(Validation.None))

  def duration[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Duration] =
    primitive(new PrimitiveType.Duration(Validation.None))

  def instant[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Instant] =
    primitive(new PrimitiveType.Instant(Validation.None))

  def localDate[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.LocalDate] =
    primitive(new PrimitiveType.LocalDate(Validation.None))

  def localDateTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.LocalDateTime] =
    primitive(new PrimitiveType.LocalDateTime(Validation.None))

  def localTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.LocalTime] =
    primitive(new PrimitiveType.LocalTime(Validation.None))

  def month[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Month] =
    primitive(new PrimitiveType.Month(Validation.None))

  def monthDay[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.MonthDay] =
    primitive(new PrimitiveType.MonthDay(Validation.None))

  def offsetDateTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.OffsetDateTime] =
    primitive(new PrimitiveType.OffsetDateTime(Validation.None))

  def offsetTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.OffsetTime] =
    primitive(new PrimitiveType.OffsetTime(Validation.None))

  def period[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Period] =
    primitive(new PrimitiveType.Period(Validation.None))

  private[this] def primitive[F[_, _], A](primitiveType: PrimitiveType[A])(implicit F: FromBinding[F]): Reflect[F, A] =
    new Primitive(primitiveType, primitiveType.typeId, F.fromBinding(primitiveType.binding))

  def year[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Year] =
    primitive(new PrimitiveType.Year(Validation.None))

  def yearMonth[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.YearMonth] =
    primitive(new PrimitiveType.YearMonth(Validation.None))

  def zoneId[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.ZoneId] =
    primitive(new PrimitiveType.ZoneId(Validation.None))

  def zoneOffset[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.ZoneOffset] =
    primitive(new PrimitiveType.ZoneOffset(Validation.None))

  def zonedDateTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.ZonedDateTime] =
    primitive(new PrimitiveType.ZonedDateTime(Validation.None))

  def currency[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.util.Currency] =
    primitive(new PrimitiveType.Currency(Validation.None))

  def uuid[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.util.UUID] =
    primitive(new PrimitiveType.UUID(Validation.None))

  def dynamic[F[_, _]](implicit F: FromBinding[F]): Dynamic[F] = new Dynamic(F.fromBinding(Binding.Dynamic()))

  private[this] def some[F[_, _], A <: AnyRef](
    element: Reflect[F, A]
  )(implicit F: FromBinding[F]): Record[F, Some[A]] = {
    val typeId = TypeId.applied[Some[A]](
      TypeId.some,
      TypeRepr.Ref(element.typeId)
    )
    new Record(
      Vector(new Term("value", element)),
      typeId,
      F.fromBinding(Binding.Record.some)
    )
  }

  private[this] def someDouble[F[_, _]](
    element: Reflect[F, Double]
  )(implicit F: FromBinding[F]): Record[F, Some[Double]] =
    new Record(
      Vector(new Term("value", element)),
      TypeId.of[Some[Double]],
      F.fromBinding(Binding.Record.someDouble)
    )

  private[this] def someLong[F[_, _]](element: Reflect[F, Long])(implicit F: FromBinding[F]): Record[F, Some[Long]] =
    new Record(
      Vector(new Term("value", element)),
      TypeId.of[Some[Long]],
      F.fromBinding(Binding.Record.someLong)
    )

  private[this] def someFloat[F[_, _]](element: Reflect[F, Float])(implicit F: FromBinding[F]): Record[F, Some[Float]] =
    new Record(
      Vector(new Term("value", element)),
      TypeId.of[Some[Float]],
      F.fromBinding(Binding.Record.someFloat)
    )

  private[this] def someInt[F[_, _]](element: Reflect[F, Int])(implicit F: FromBinding[F]): Record[F, Some[Int]] =
    new Record(
      Vector(new Term("value", element)),
      TypeId.of[Some[Int]],
      F.fromBinding(Binding.Record.someInt)
    )

  private[this] def someChar[F[_, _]](element: Reflect[F, Char])(implicit F: FromBinding[F]): Record[F, Some[Char]] =
    new Record(
      Vector(new Term("value", element)),
      TypeId.of[Some[Char]],
      F.fromBinding(Binding.Record.someChar)
    )

  private[this] def someShort[F[_, _]](element: Reflect[F, Short])(implicit F: FromBinding[F]): Record[F, Some[Short]] =
    new Record(
      Vector(new Term("value", element)),
      TypeId.of[Some[Short]],
      F.fromBinding(Binding.Record.someShort)
    )

  private[this] def someBoolean[F[_, _]](
    element: Reflect[F, Boolean]
  )(implicit F: FromBinding[F]): Record[F, Some[Boolean]] =
    new Record(
      Vector(new Term("value", element)),
      TypeId.of[Some[Boolean]],
      F.fromBinding(Binding.Record.someBoolean)
    )

  private[this] def someByte[F[_, _]](element: Reflect[F, Byte])(implicit F: FromBinding[F]): Record[F, Some[Byte]] =
    new Record(
      Vector(new Term("value", element)),
      TypeId.of[Some[Byte]],
      F.fromBinding(Binding.Record.someByte)
    )

  private[this] def someUnit[F[_, _]](element: Reflect[F, Unit])(implicit F: FromBinding[F]): Record[F, Some[Unit]] =
    new Record(
      Vector(new Term("value", element)),
      TypeId.of[Some[Unit]],
      F.fromBinding(Binding.Record.someUnit)
    )

  private[this] def none[F[_, _]](implicit F: FromBinding[F]): Record[F, None.type] =
    new Record(Vector(), TypeId.none, F.fromBinding(Binding.Record.none))

  def option[F[_, _], A <: AnyRef](element: Reflect[F, A])(implicit F: FromBinding[F]): Variant[F, Option[A]] = {
    val typeId = TypeId.applied[Option[A]](
      TypeId.option,
      TypeRepr.Ref(element.typeId)
    )
    new Variant(
      Vector(new Term("None", none), new Term("Some", some(element))),
      typeId,
      F.fromBinding(Binding.Variant.option)
    )
  }

  def optionDouble[F[_, _]](element: Reflect[F, Double])(implicit F: FromBinding[F]): Variant[F, Option[Double]] =
    new Variant(
      Vector(new Term("None", none), new Term("Some", someDouble(element))),
      TypeId.of[Option[Double]],
      F.fromBinding(Binding.Variant.option)
    )

  def optionLong[F[_, _]](element: Reflect[F, Long])(implicit F: FromBinding[F]): Variant[F, Option[Long]] =
    new Variant(
      Vector(new Term("None", none), new Term("Some", someLong(element))),
      TypeId.of[Option[Long]],
      F.fromBinding(Binding.Variant.option)
    )

  def optionFloat[F[_, _]](element: Reflect[F, Float])(implicit F: FromBinding[F]): Variant[F, Option[Float]] =
    new Variant(
      Vector(new Term("None", none), new Term("Some", someFloat(element))),
      TypeId.of[Option[Float]],
      F.fromBinding(Binding.Variant.option)
    )

  def optionInt[F[_, _]](element: Reflect[F, Int])(implicit F: FromBinding[F]): Variant[F, Option[Int]] =
    new Variant(
      Vector(new Term("None", none), new Term("Some", someInt(element))),
      TypeId.of[Option[Int]],
      F.fromBinding(Binding.Variant.option)
    )

  def optionChar[F[_, _]](element: Reflect[F, Char])(implicit F: FromBinding[F]): Variant[F, Option[Char]] =
    new Variant(
      Vector(new Term("None", none), new Term("Some", someChar(element))),
      TypeId.of[Option[Char]],
      F.fromBinding(Binding.Variant.option)
    )

  def optionShort[F[_, _]](element: Reflect[F, Short])(implicit F: FromBinding[F]): Variant[F, Option[Short]] =
    new Variant(
      Vector(new Term("None", none), new Term("Some", someShort(element))),
      TypeId.of[Option[Short]],
      F.fromBinding(Binding.Variant.option)
    )

  def optionBoolean[F[_, _]](element: Reflect[F, Boolean])(implicit F: FromBinding[F]): Variant[F, Option[Boolean]] =
    new Variant(
      Vector(new Term("None", none), new Term("Some", someBoolean(element))),
      TypeId.of[Option[Boolean]],
      F.fromBinding(Binding.Variant.option)
    )

  def optionByte[F[_, _]](element: Reflect[F, Byte])(implicit F: FromBinding[F]): Variant[F, Option[Byte]] =
    new Variant(
      Vector(new Term("None", none), new Term("Some", someByte(element))),
      TypeId.of[Option[Byte]],
      F.fromBinding(Binding.Variant.option)
    )

  def optionUnit[F[_, _]](element: Reflect[F, Unit])(implicit F: FromBinding[F]): Variant[F, Option[Unit]] =
    new Variant(
      Vector(new Term("None", none), new Term("Some", someUnit(element))),
      TypeId.of[Option[Unit]],
      F.fromBinding(Binding.Variant.option)
    )

  private[this] def left[F[_, _], A, B](element: Reflect[F, A])(implicit F: FromBinding[F]): Record[F, Left[A, B]] = {
    val typeId = TypeId.applied[Left[A, B]](
      TypeId.nominal[Left[?, ?]]("Left", Owner.fromPackagePath("scala.util")),
      TypeRepr.Ref(element.typeId)
    )
    new Record(Vector(new Term("value", element)), typeId, F.fromBinding(Binding.Record.left))
  }

  private[this] def right[F[_, _], A, B](element: Reflect[F, B])(implicit F: FromBinding[F]): Record[F, Right[A, B]] = {
    val typeId = TypeId.applied[Right[A, B]](
      TypeId.nominal[Right[?, ?]]("Right", Owner.fromPackagePath("scala.util")),
      TypeRepr.Ref(element.typeId)
    )
    new Record(Vector(new Term("value", element)), typeId, F.fromBinding(Binding.Record.right))
  }

  def either[F[_, _], A, B](
    l: Reflect[F, A],
    r: Reflect[F, B]
  )(implicit F: FromBinding[F]): Variant[F, Either[A, B]] = {
    val typeId = TypeId.applied[Either[A, B]](
      TypeId.either,
      TypeRepr.Ref(l.typeId),
      TypeRepr.Ref(r.typeId)
    )
    new Variant(
      Vector(new Term("Left", left[F, A, B](l)), new Term("Right", right[F, A, B](r))),
      typeId,
      F.fromBinding(Binding.Variant.either)
    )
  }

  def set[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, Set] = {
    val typeId = TypeId.applied[Set[A]](
      TypeId.set,
      TypeRepr.Ref(element.typeId)
    )
    new Sequence(element, typeId, F.fromBinding(Binding.Seq.set))
  }

  def list[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, List] = {
    val typeId = TypeId.applied[List[A]](
      TypeId.list,
      TypeRepr.Ref(element.typeId)
    )
    new Sequence(element, typeId, F.fromBinding(Binding.Seq.list))
  }

  def vector[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, Vector] = {
    val typeId = TypeId.applied[Vector[A]](
      TypeId.vector,
      TypeRepr.Ref(element.typeId)
    )
    new Sequence(element, typeId, F.fromBinding(Binding.Seq.vector))
  }

  def indexedSeq[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, IndexedSeq] = {
    val typeId = TypeId.applied[IndexedSeq[A]](
      TypeId.indexedSeq,
      TypeRepr.Ref(element.typeId)
    )
    new Sequence(element, typeId, F.fromBinding(Binding.Seq.indexedSeq))
  }

  def seq[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, Seq] = {
    val typeId = TypeId.applied[Seq[A]](
      TypeId.seq,
      TypeRepr.Ref(element.typeId)
    )
    new Sequence(element, typeId, F.fromBinding(Binding.Seq.seq))
  }

  def chunk[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, Chunk] = {
    val typeId = TypeId.applied[Chunk[A]](
      TypeId.chunk,
      TypeRepr.Ref(element.typeId)
    )
    new Sequence(element, typeId, F.fromBinding(Binding.Seq.chunk))
  }

  def map[F[_, _], K, V](key: Reflect[F, K], value: Reflect[F, V])(implicit
    F: FromBinding[F]
  ): Map[F, K, V, collection.immutable.Map] = {
    val typeId = TypeId.applied[collection.immutable.Map[K, V]](
      TypeId.map,
      TypeRepr.Ref(key.typeId),
      TypeRepr.Ref(value.typeId)
    )
    new Map(key, value, typeId, F.fromBinding(Binding.Map.map))
  }

  object Extractors {
    object List {
      def unapply[F[_, _], A](reflect: Reflect[F, List[A]]): Option[Reflect[F, A]] =
        reflect.asSequenceUnknown.collect {
          case x if x.sequence.typeId.name == "List" && x.sequence.typeId.owner == TypeId.list.owner =>
            x.sequence.element.asInstanceOf[Reflect[F, A]]
        }
    }

    object Vector {
      def unapply[F[_, _], A](reflect: Reflect[F, Vector[A]]): Option[Reflect[F, A]] =
        reflect.asSequenceUnknown.collect {
          case x if x.sequence.typeId.name == "Vector" && x.sequence.typeId.owner == TypeId.vector.owner =>
            x.sequence.element.asInstanceOf[Reflect[F, A]]
        }
    }

    object Set {
      def unapply[F[_, _], A](reflect: Reflect[F, Set[A]]): Option[Reflect[F, A]] =
        reflect.asSequenceUnknown.collect {
          case x if x.sequence.typeId.name == "Set" && x.sequence.typeId.owner == TypeId.set.owner =>
            x.sequence.element.asInstanceOf[Reflect[F, A]]
        }
    }
  }

  private[schema] def unwrapToPrimitiveTypeOption[F[_, _], A](reflect: Reflect[F, A]): Option[PrimitiveType[A]] =
    if (reflect.isWrapper) {
      reflect.asWrapperUnknown.get.wrapper.underlyingPrimitiveType.asInstanceOf[Option[PrimitiveType[A]]]
    } else reflect.asPrimitive.map(_.primitiveType)

  private class StringToIntMap(size: Int) {
    private[this] val mask   = (Integer.highestOneBit(size | 1) << 2) - 1
    private[this] val keys   = new Array[String](mask + 1)
    private[this] val values = new Array[Int](mask + 1)

    def put(key: String, value: Int): Unit = {
      var idx             = key.hashCode & mask
      var currKey: String = null
      while ({
        currKey = keys(idx)
        (currKey ne null) && !currKey.equals(key)
      }) idx = (idx + 1) & mask
      keys(idx) = key
      values(idx) = value
    }

    def get(key: String): Int = {
      var idx             = key.hashCode & mask
      var currKey: String = null
      while ({
        currKey = keys(idx)
        (currKey ne null) && !currKey.equals(key)
      }) idx = (idx + 1) & mask
      if (currKey eq null) -1
      else values(idx)
    }
  }
}
