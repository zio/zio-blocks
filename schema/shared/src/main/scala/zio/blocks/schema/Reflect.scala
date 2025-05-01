package zio.blocks.schema

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding.Binding
import zio.blocks.schema.binding._

import scala.collection.immutable.ArraySeq

sealed trait Reflect[F[_, _], A] extends Reflectable[A] { self =>
  protected def inner: Any

  type NodeBinding <: BindingType

  def examples(implicit F: HasBinding[F]): Seq[A]

  def examples(value: A, values: A*)(implicit F: HasBinding[F]): Reflect[F, A]

  def asTerm[S](name: String): Term[F, S, A] = Term(name, this, Doc.Empty, Nil)

  def asRecord: Option[Reflect.Record[F, A]] =
    self match {
      case record: Reflect.Record[F, A] @scala.unchecked => Some(record)
      case _                                             => None
    }

  def asVariant: Option[Reflect.Variant[F, A]] =
    self match {
      case variant: Reflect.Variant[F, A] @scala.unchecked => Some(variant)
      case _                                               => None
    }

  def asDynamic: Option[Reflect.Dynamic[F]] =
    self match {
      case dynamic: Reflect.Dynamic[F] @scala.unchecked => Some(dynamic)
      case _                                            => None
    }

  def asPrimitive: Option[Reflect.Primitive[F, A]] =
    self match {
      case primitive: Reflect.Primitive[F, A] @scala.unchecked => Some(primitive)
      case _                                                   => None
    }

  def binding(implicit F: HasBinding[F]): Binding[NodeBinding, A]

  def defaultValue(value: => A)(implicit F: HasBinding[F]): Reflect[F, A]

  def doc(value: Doc): Reflect[F, A]

  def doc(value: String): Reflect[F, A] = doc(Doc.Text(value))

  override def equals(obj: Any): Boolean = obj match {
    case that: Reflect[_, _] => (this eq that) || inner == that.inner
    case _                   => false
  }

  final def get[B](optic: Optic[F, A, B]): Option[Reflect[F, B]] =
    get(optic.toDynamic).map(_.asInstanceOf[Reflect[F, B]])

  final def get(dynamic: DynamicOptic): Option[Reflect[F, _]] = {
    val list = dynamic.nodes.toList

    def loop(current: Reflect[F, _], optic: List[DynamicOptic.Node]): Option[Reflect[F, _]] =
      optic match {
        case Nil => Some(current)
        case head :: tail =>
          head match {
            case DynamicOptic.Node.Field(name) =>
              current.asRecord.flatMap { record =>
                record.fieldByName(name).flatMap { term =>
                  loop(term.value, tail)
                }
              }
            case DynamicOptic.Node.Case(name) =>
              current.asVariant.flatMap { variant =>
                variant.caseByName(name).flatMap { term =>
                  loop(term.value, tail)
                }
              }
            case DynamicOptic.Node.Elements =>
              current match {
                case sequence: Reflect.Sequence[F, _, _] @scala.unchecked =>
                  loop(sequence.element, tail)
                case _ => None
              }
            case DynamicOptic.Node.MapKeys =>
              current match {
                case map: Reflect.Map[F, _, _, _] @scala.unchecked =>
                  loop(map.key, tail)
                case _ => None
              }
            case DynamicOptic.Node.MapValues =>
              current match {
                case map: Reflect.Map[F, _, _, _] @scala.unchecked =>
                  loop(map.value, tail)
                case _ => None
              }
          }
      }

    loop(this, list).map(_.asInstanceOf[Reflect[F, A]])
  }

  override def hashCode: Int = inner.hashCode

  def noBinding: Reflect[NoBinding, A] = refineBinding(RefineBinding.noBinding())

  def refineBinding[G[_, _]](f: RefineBinding[F, G]): Reflect[G, A]

  final def updated[B](optic: Optic[F, A, B])(f: Reflect[F, B] => Reflect[F, B]): Option[Reflect[F, A]] =
    updated(optic.toDynamic)(new Reflect.Updater[F] {
      def update[A](reflect: Reflect[F, A]): Reflect[F, A] =
        f(reflect.asInstanceOf[Reflect[F, B]]).asInstanceOf[Reflect[F, A]]
    })

  final def updated[B](dynamic: DynamicOptic)(f: Reflect.Updater[F]): Option[Reflect[F, A]] = {
    val list = dynamic.nodes.toList

    def loop(current: Reflect[F, _], optic: List[DynamicOptic.Node]): Option[Reflect[F, _]] =
      optic match {
        case Nil => Some(f.update(current.asInstanceOf[Reflect[F, B]]))
        case head :: tail =>
          head match {
            case DynamicOptic.Node.Field(name) =>
              current.asRecord.flatMap { record =>
                record.modifyField(name)(new Term.Updater[F] {
                  def update[S, A](input: Term[F, S, A]): Term[F, S, A] =
                    input.copy(value =
                      loop(input.value, tail).get.asInstanceOf[Reflect[F, A]]
                    ) // TODO: Fix these gets by modifying Updater to return option
                })
              }
            case DynamicOptic.Node.Case(name) =>
              current.asVariant.flatMap { variant =>
                variant.modifyCase(name)(new Term.Updater[F] {
                  def update[S, A](input: Term[F, S, A]): Term[F, S, A] =
                    input.copy(value = loop(input.value, tail).get.asInstanceOf[Reflect[F, A]])
                })
              }
            case DynamicOptic.Node.Elements =>
              current match {
                case sequence: Reflect.Sequence[F, A, _] @scala.unchecked =>
                  Some(sequence.copy(element = loop(sequence.element, tail).get.asInstanceOf[Reflect[F, A]]))
                case _ => None
              }
            case DynamicOptic.Node.MapKeys =>
              current match {
                case map: Reflect.Map[F, A, _, _] @scala.unchecked =>
                  Some(map.copy(key = loop(map.key, tail).get.asInstanceOf[Reflect[F, A]]))
                case _ => None
              }
            case DynamicOptic.Node.MapValues =>
              current match {
                case map: Reflect.Map[F, _, A, _] @scala.unchecked =>
                  Some(map.copy(value = loop(map.value, tail).get.asInstanceOf[Reflect[F, A]]))
                case _ => None
              }
          }
      }

    loop(this, list).map(_.asInstanceOf[Reflect[F, A]])
  }
}

object Reflect {
  type Bound[A] = Reflect[Binding, A]

  trait Updater[F[_, _]] {
    def update[A](reflect: Reflect[F, A]): Reflect[F, A]
  }

  case class Record[F[_, _], A](
    fields: Seq[Term[F, A, ?]],
    typeName: TypeName[A],
    recordBinding: F[BindingType.Record, A],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Record] = Nil
  ) extends Reflect[F, A] { self =>
    protected def inner: Any = (fields, typeName, doc, modifiers)

    type NodeBinding = BindingType.Record

    def doc(value: Doc): Record[F, A] = copy(doc = value)

    def defaultValue(value: => A)(implicit F: HasBinding[F]): Record[F, A] =
      copy(recordBinding = F.updateBinding(recordBinding, _.defaultValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[A] = binding.examples

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Record[F, A] =
      copy(recordBinding = F.updateBinding(recordBinding, _.examples(value, values: _*)))

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Record, A] = F.binding(recordBinding)

    def constructor(implicit F: HasBinding[F]): Constructor[A] = F.constructor(recordBinding)

    def deconstructor(implicit F: HasBinding[F]): Deconstructor[A] = F.deconstructor(recordBinding)

    def fieldByName(name: String): Option[Term[F, A, ?]] = fields.find(_.name == name)

    def lensByIndex(index: Int): Lens[F, A, ?] = Lens(self, fields(index))

    def lensByName(name: String): Option[Lens[F, A, ?]] = fieldByName(name).map(Lens(self, _))

    val length: Int = fields.length

    def modifyField(name: String)(f: Term.Updater[F]): Option[Record[F, A]] = {
      val i = fields.indexWhere(_.name == name)
      if (i >= 0) {
        val newFields = fields.updated(i, f.update(fields(i)))
        Some(Record(newFields, typeName, recordBinding, doc, modifiers))
      } else None
    }

    def registerByName(name: String): Option[Register[?]] = {
      val i = fields.indexWhere(_.name == name)
      if (i >= 0) Some(registers(i))
      else None
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Record[G, A] =
      Record(fields.map(_.refineBinding(f)), typeName, f(recordBinding), doc, modifiers)

    val registers: IndexedSeq[Register[?]] = {
      val registers      = new Array[Register[?]](length)
      var registerOffset = RegisterOffset.Zero
      var i              = 0
      fields.foreach { term =>
        term.value match {
          case Reflect.Primitive(primType, _, _, _, _) =>
            primType match {
              case PrimitiveType.Unit =>
                registers(i) = Register.Unit
              case _: PrimitiveType.Boolean =>
                registers(i) = Register.Boolean(RegisterOffset.getBytes(registerOffset))
                registerOffset = RegisterOffset.incrementBooleansAndBytes(registerOffset)
              case _: PrimitiveType.Byte =>
                registers(i) = Register.Byte(RegisterOffset.getBytes(registerOffset))
                registerOffset = RegisterOffset.incrementBooleansAndBytes(registerOffset)
              case _: PrimitiveType.Char =>
                registers(i) = Register.Char(RegisterOffset.getBytes(registerOffset))
                registerOffset = RegisterOffset.incrementCharsAndShorts(registerOffset)
              case _: PrimitiveType.Short =>
                registers(i) = Register.Short(RegisterOffset.getBytes(registerOffset))
                registerOffset = RegisterOffset.incrementCharsAndShorts(registerOffset)
              case _: PrimitiveType.Float =>
                registers(i) = Register.Float(RegisterOffset.getBytes(registerOffset))
                registerOffset = RegisterOffset.incrementFloatsAndInts(registerOffset)
              case _: PrimitiveType.Int =>
                registers(i) = Register.Int(RegisterOffset.getBytes(registerOffset))
                registerOffset = RegisterOffset.incrementFloatsAndInts(registerOffset)
              case _: PrimitiveType.Double =>
                registers(i) = Register.Double(RegisterOffset.getBytes(registerOffset))
                registerOffset = RegisterOffset.incrementDoublesAndLongs(registerOffset)
              case _: PrimitiveType.Long =>
                registers(i) = Register.Long(RegisterOffset.getBytes(registerOffset))
                registerOffset = RegisterOffset.incrementDoublesAndLongs(registerOffset)
              case _ =>
                registers(i) = Register.Object(RegisterOffset.getObjects(registerOffset))
                registerOffset = RegisterOffset.incrementObjects(registerOffset)
            }
          case _ =>
            registers(i) = Register.Object(RegisterOffset.getObjects(registerOffset))
            registerOffset = RegisterOffset.incrementObjects(registerOffset)
        }
        i += 1
      }
      ArraySeq.unsafeWrapArray(registers)
    }

    val usedRegisters: RegisterOffset = registers.foldLeft(RegisterOffset.Zero) { (acc, register) =>
      RegisterOffset.add(acc, register.usedRegisters)
    }
  }

  object Record {
    type Bound[A] = Record[Binding, A]
  }

  case class Variant[F[_, _], A](
    cases: Seq[Term[F, A, ? <: A]],
    typeName: TypeName[A],
    variantBinding: F[BindingType.Variant, A],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Variant] = Nil
  ) extends Reflect[F, A] {
    protected def inner: Any = (cases, typeName, doc, modifiers)

    type NodeBinding = BindingType.Variant

    def doc(value: Doc): Variant[F, A] = copy(doc = value)

    def defaultValue(value: => A)(implicit F: HasBinding[F]): Variant[F, A] =
      copy(variantBinding = F.updateBinding(variantBinding, _.defaultValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[A] = binding.examples

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Variant[F, A] =
      copy(variantBinding = F.updateBinding(variantBinding, _.examples(value, values: _*)))

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Variant, A] = F.binding(variantBinding)

    def caseByName(name: String): Option[Term[F, A, ? <: A]] = cases.find(_.name == name)

    def discriminator(implicit F: HasBinding[F]): Discriminator[A] = F.discriminator(variantBinding)

    def matchers(implicit F: HasBinding[F]): Matchers[A] = F.matchers(variantBinding)

    def modifyCase(name: String)(f: Term.Updater[F]): Option[Variant[F, A]] = {
      val i = cases.indexWhere(_.name == name)
      if (i >= 0) {
        val newCases = cases.updated(i, f.update(cases(i)))
        Some(Variant(newCases, typeName, variantBinding, doc, modifiers))
      } else None
    }

    def prismByIndex(index: Int): Prism[F, A, ? <: A] = Prism(this, cases(index))

    def prismByName(name: String): Option[Prism[F, A, ? <: A]] = caseByName(name).map(Prism(this, _))

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Variant[G, A] =
      Variant(cases.map(_.refineBinding(f)), typeName, f(variantBinding), doc, modifiers)
  }

  object Variant {
    type Bound[A] = Variant[Binding, A]
  }

  case class Sequence[F[_, _], A, C[_]](
    element: Reflect[F, A],
    seqBinding: F[BindingType.Seq[C], C[A]],
    typeName: TypeName[C[A]],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Seq] = Nil
  ) extends Reflect[F, C[A]] {
    protected def inner: Any = (element, typeName, doc, modifiers)

    type NodeBinding = BindingType.Seq[C]

    def doc(value: Doc): Sequence[F, A, C] = copy(doc = value)

    def defaultValue(value: => C[A])(implicit F: HasBinding[F]): Sequence[F, A, C] =
      copy(seqBinding = F.updateBinding(seqBinding, _.defaultValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[C[A]] = binding.examples

    def examples(value: C[A], values: C[A]*)(implicit F: HasBinding[F]): Sequence[F, A, C] =
      copy(seqBinding = F.updateBinding(seqBinding, _.examples(value, values: _*)))

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Seq[C], C[A]] = F.binding(seqBinding)

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Sequence[G, A, C] =
      Sequence(element.refineBinding(f), f(seqBinding), typeName, doc, modifiers)

    def seqConstructor(implicit F: HasBinding[F]): SeqConstructor[C] = F.seqConstructor(seqBinding)

    def seqDeconstructor(implicit F: HasBinding[F]): SeqDeconstructor[C] = F.seqDeconstructor(seqBinding)

    def values: Traversal[F, C[A], A] = Traversal.seqValues(this)
  }

  object Sequence {
    type Bound[A, C[_]] = Sequence[Binding, A, C]
  }

  case class Map[F[_, _], Key, Value, M[_, _]](
    key: Reflect[F, Key],
    value: Reflect[F, Value],
    mapBinding: F[BindingType.Map[M], M[Key, Value]],
    typeName: TypeName[M[Key, Value]],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Map] = Nil
  ) extends Reflect[F, M[Key, Value]] {
    protected def inner: Any = (key, value, typeName, doc, modifiers)

    type NodeBinding = BindingType.Map[M]

    def doc(value: Doc): Map[F, Key, Value, M] = copy(doc = value)

    def defaultValue(value: => M[Key, Value])(implicit F: HasBinding[F]): Map[F, Key, Value, M] =
      copy(mapBinding = F.updateBinding(mapBinding, _.defaultValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[M[Key, Value]] = binding.examples

    def examples(value: M[Key, Value], values: M[Key, Value]*)(implicit F: HasBinding[F]): Map[F, Key, Value, M] =
      copy(mapBinding = F.updateBinding(mapBinding, _.examples(value, values: _*)))

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Map[M], M[Key, Value]] = F.binding(mapBinding)

    def mapConstructor(implicit F: HasBinding[F]): MapConstructor[M] = F.mapConstructor(mapBinding)

    def mapDeconstructor(implicit F: HasBinding[F]): MapDeconstructor[M] = F.mapDeconstructor(mapBinding)

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Map[G, Key, Value, M] =
      Map(key.refineBinding(f), value.refineBinding(f), f(mapBinding), typeName, doc, modifiers)

    def keys: Traversal[F, M[Key, Value], Key] = Traversal.mapKeys(this)

    def values: Traversal[F, M[Key, Value], Value] = Traversal.mapValues(this)
  }

  object Map {
    type Bound[K, V, M[_, _]] = Map[Binding, K, V, M]
  }

  case class Dynamic[F[_, _]](
    dynamicBinding: F[BindingType.Dynamic, DynamicValue],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Dynamic] = Nil
  ) extends Reflect[F, DynamicValue] {
    protected def inner: Any = (modifiers, modifiers, doc)

    type NodeBinding = BindingType.Dynamic

    def doc(value: Doc): Dynamic[F] = copy(doc = value)

    def defaultValue(value: => DynamicValue)(implicit F: HasBinding[F]): Dynamic[F] =
      copy(dynamicBinding = F.updateBinding(dynamicBinding, _.defaultValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[DynamicValue] = binding.examples

    def examples(value: DynamicValue, values: DynamicValue*)(implicit F: HasBinding[F]): Dynamic[F] =
      copy(dynamicBinding = F.updateBinding(dynamicBinding, _.examples(value, values: _*)))

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Dynamic, DynamicValue] = F.binding(dynamicBinding)

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Reflect[G, DynamicValue] =
      Dynamic(f(dynamicBinding), doc, modifiers)
  }

  case class Primitive[F[_, _], A](
    primitiveType: PrimitiveType[A],
    primitiveBinding: F[BindingType.Primitive, A],
    typeName: TypeName[A],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Primitive] = Nil
  ) extends Reflect[F, A] { self =>
    protected def inner: Any = (primitiveType, typeName, doc, modifiers)

    type NodeBinding = BindingType.Primitive

    def doc(value: Doc): Primitive[F, A] = copy(doc = value)

    def defaultValue(value: => A)(implicit F: HasBinding[F]): Primitive[F, A] =
      copy(primitiveBinding = F.updateBinding(primitiveBinding, _.defaultValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[A] = binding.examples

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Primitive[F, A] =
      copy(primitiveBinding = F.updateBinding(primitiveBinding, _.examples(value, values: _*)))

    def binding(implicit F: HasBinding[F]): Binding.Primitive[A] = F.primitive(primitiveBinding)

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Primitive[G, A] =
      Primitive(primitiveType, f(primitiveBinding), typeName, doc, modifiers)
  }

  case class Deferred[F[_, _], A](_value: () => Reflect[F, A]) extends Reflect[F, A] {
    protected def inner: Any = value.inner

    final lazy val value: Reflect[F, A] = _value()

    type NodeBinding = value.NodeBinding

    def doc(value: Doc): Deferred[F, A] = copy(_value = () => _value().doc(value))

    def defaultValue(value: => A)(implicit F: HasBinding[F]): Deferred[F, A] =
      copy(_value = () => _value().defaultValue(value)(F))

    def examples(implicit F: HasBinding[F]): Seq[A] = value.examples

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Deferred[F, A] =
      copy(_value = () => _value().examples(value, values: _*))

    def binding(implicit F: HasBinding[F]): Binding[NodeBinding, A] = value.binding

    def modifiers: Seq[Modifier] = value.modifiers

    def doc: Doc = value.doc

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Reflect[G, A] = {
      val v = visited.get
      if (v.containsKey(this)) value.asInstanceOf[Reflect[G, A]] // exit from recursion
      else {
        v.put(this, ())
        try value.refineBinding(f)
        finally v.remove(this)
      }
    }

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
  }

  def unit[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Unit] =
    Primitive(PrimitiveType.Unit, F.fromBinding(Binding.Primitive.unit), TypeName.unit, Doc.Empty, Nil)

  def boolean[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Boolean] =
    Primitive(
      PrimitiveType.Boolean(Validation.None),
      F.fromBinding(Binding.Primitive.boolean),
      TypeName.boolean,
      Doc.Empty,
      Nil
    )

  def byte[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Byte] =
    Primitive(PrimitiveType.Byte(Validation.None), F.fromBinding(Binding.Primitive.byte), TypeName.byte, Doc.Empty, Nil)

  def short[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Short] =
    Primitive(
      PrimitiveType.Short(Validation.None),
      F.fromBinding(Binding.Primitive.short),
      TypeName.short,
      Doc.Empty,
      Nil
    )

  def int[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Int] =
    Primitive(
      PrimitiveType.Int(Validation.None),
      F.fromBinding(Binding.Primitive.int),
      TypeName.int,
      Doc.Empty,
      Nil
    )

  def long[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Long] =
    Primitive(
      PrimitiveType.Long(Validation.None),
      F.fromBinding(Binding.Primitive.long),
      TypeName.long,
      Doc.Empty,
      Nil
    )

  def float[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Float] =
    Primitive(
      PrimitiveType.Float(Validation.None),
      F.fromBinding(Binding.Primitive.float),
      TypeName.float,
      Doc.Empty,
      Nil
    )

  def double[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Double] =
    Primitive(
      PrimitiveType.Double(Validation.None),
      F.fromBinding(Binding.Primitive.double),
      TypeName.double,
      Doc.Empty,
      Nil
    )

  def char[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Char] =
    Primitive(
      PrimitiveType.Char(Validation.None),
      F.fromBinding(Binding.Primitive.char),
      TypeName.char,
      Doc.Empty,
      Nil
    )

  def string[F[_, _]](implicit F: FromBinding[F]): Reflect[F, String] =
    Primitive(
      PrimitiveType.String(Validation.None),
      F.fromBinding(Binding.Primitive.string),
      TypeName.string,
      Doc.Empty,
      Nil
    )

  def bigInt[F[_, _]](implicit F: FromBinding[F]): Reflect[F, BigInt] =
    Primitive(
      PrimitiveType.BigInt(Validation.None),
      F.fromBinding(Binding.Primitive.bigInt),
      TypeName.bigInt,
      Doc.Empty,
      Nil
    )

  def bigDecimal[F[_, _]](implicit F: FromBinding[F]): Reflect[F, BigDecimal] =
    Primitive(
      PrimitiveType.BigDecimal(Validation.None),
      F.fromBinding(Binding.Primitive.bigDecimal),
      TypeName.bigDecimal,
      Doc.Empty,
      Nil
    )

  def dayOfWeek[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.DayOfWeek] =
    Primitive(
      PrimitiveType.DayOfWeek(Validation.None),
      F.fromBinding(Binding.Primitive.dayOfWeek),
      TypeName.dayOfWeek,
      Doc.Empty,
      Nil
    )

  def duration[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Duration] =
    Primitive(
      PrimitiveType.Duration(Validation.None),
      F.fromBinding(Binding.Primitive.duration),
      TypeName.duration,
      Doc.Empty,
      Nil
    )

  def instant[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Instant] =
    Primitive(
      PrimitiveType.Instant(Validation.None),
      F.fromBinding(Binding.Primitive.instant),
      TypeName.instant,
      Doc.Empty,
      Nil
    )

  def localDate[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.LocalDate] =
    Primitive(
      PrimitiveType.LocalDate(Validation.None),
      F.fromBinding(Binding.Primitive.localDate),
      TypeName.localDate,
      Doc.Empty,
      Nil
    )

  def localDateTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.LocalDateTime] =
    Primitive(
      PrimitiveType.LocalDateTime(Validation.None),
      F.fromBinding(Binding.Primitive.localDateTime),
      TypeName.localDateTime,
      Doc.Empty,
      Nil
    )

  def localTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.LocalTime] =
    Primitive(
      PrimitiveType.LocalTime(Validation.None),
      F.fromBinding(Binding.Primitive.localTime),
      TypeName.localTime,
      Doc.Empty,
      Nil
    )

  def month[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Month] =
    Primitive(
      PrimitiveType.Month(Validation.None),
      F.fromBinding(Binding.Primitive.month),
      TypeName.month,
      Doc.Empty,
      Nil
    )

  def monthDay[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.MonthDay] =
    Primitive(
      PrimitiveType.MonthDay(Validation.None),
      F.fromBinding(Binding.Primitive.monthDay),
      TypeName.monthDay,
      Doc.Empty,
      Nil
    )

  def offsetDateTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.OffsetDateTime] =
    Primitive(
      PrimitiveType.OffsetDateTime(Validation.None),
      F.fromBinding(Binding.Primitive.offsetDateTime),
      TypeName.offsetDateTime,
      Doc.Empty,
      Nil
    )

  def offsetTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.OffsetTime] =
    Primitive(
      PrimitiveType.OffsetTime(Validation.None),
      F.fromBinding(Binding.Primitive.offsetTime),
      TypeName.offsetTime,
      Doc.Empty,
      Nil
    )

  def period[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Period] =
    Primitive(
      PrimitiveType.Period(Validation.None),
      F.fromBinding(Binding.Primitive.period),
      TypeName.period,
      Doc.Empty,
      Nil
    )

  def year[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Year] =
    Primitive(
      PrimitiveType.Year(Validation.None),
      F.fromBinding(Binding.Primitive.year),
      TypeName.year,
      Doc.Empty,
      Nil
    )

  def yearMonth[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.YearMonth] =
    Primitive(
      PrimitiveType.YearMonth(Validation.None),
      F.fromBinding(Binding.Primitive.yearMonth),
      TypeName.yearMonth,
      Doc.Empty,
      Nil
    )

  def zoneId[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.ZoneId] =
    Primitive(
      PrimitiveType.ZoneId(Validation.None),
      F.fromBinding(Binding.Primitive.zoneId),
      TypeName.zoneId,
      Doc.Empty,
      Nil
    )

  def zoneOffset[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.ZoneOffset] =
    Primitive(
      PrimitiveType.ZoneOffset(Validation.None),
      F.fromBinding(Binding.Primitive.zoneOffset),
      TypeName.zoneOffset,
      Doc.Empty,
      Nil
    )

  def zonedDateTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.ZonedDateTime] =
    Primitive(
      PrimitiveType.ZonedDateTime(Validation.None),
      F.fromBinding(Binding.Primitive.zonedDateTime),
      TypeName.zonedDateTime,
      Doc.Empty,
      Nil
    )

  def currency[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.util.Currency] =
    Primitive(
      PrimitiveType.Currency(Validation.None),
      F.fromBinding(Binding.Primitive.currency),
      TypeName.currency,
      Doc.Empty,
      Nil
    )

  def uuid[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.util.UUID] =
    Primitive(
      PrimitiveType.UUID(Validation.None),
      F.fromBinding(Binding.Primitive.uuid),
      TypeName.uuid,
      Doc.Empty,
      Nil
    )

  def dynamic[F[_, _]](implicit F: FromBinding[F]): Dynamic[F] =
    Dynamic(F.fromBinding(Binding.Dynamic()), Doc.Empty, Nil)

  def set[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, Set] =
    Sequence(element, F.fromBinding(Binding.Seq.set), TypeName.set[A], Doc.Empty, Nil)

  def list[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, List] =
    Sequence(element, F.fromBinding(Binding.Seq.list), TypeName.list[A], Doc.Empty, Nil)

  def vector[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, Vector] =
    Sequence(element, F.fromBinding(Binding.Seq.vector), TypeName.vector[A], Doc.Empty, Nil)

  def array[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, Array] =
    Sequence(element, F.fromBinding(Binding.Seq.array), TypeName.array[A], Doc.Empty, Nil)

  def map[F[_, _], A, B](key: Reflect[F, A], value: Reflect[F, B])(implicit
    F: FromBinding[F]
  ): Map[F, A, B, collection.immutable.Map] =
    Map(key, value, F.fromBinding(Binding.Map.map), TypeName.map[A, B], Doc.Empty, Nil)

  def some[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Record[F, Some[A]] =
    Record(
      Seq(Term("value", element, Doc.Empty, Nil)),
      TypeName.some[A],
      F.fromBinding(Binding.Record.some[A]),
      Doc.Empty,
      Nil
    )

  def none[F[_, _]](implicit F: FromBinding[F]): Record[F, None.type] =
    Record(Nil, TypeName.none, F.fromBinding(Binding.Record.none), Doc.Empty, Nil)

  def option[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Variant[F, Option[A]] =
    Variant(
      Seq(
        Term("None", none, Doc.Empty, Nil),
        Term("Some", some[F, A](element), Doc.Empty, Nil)
      ),
      TypeName.option,
      F.fromBinding(Binding.Variant.option),
      Doc.Empty,
      Nil
    )

  def left[F[_, _], A, B](element: Reflect[F, A])(implicit F: FromBinding[F]): Record[F, Left[A, B]] =
    Record(
      Seq(Term("value", element, Doc.Empty, Nil)),
      TypeName.left,
      F.fromBinding(Binding.Record.left),
      Doc.Empty,
      Nil
    )

  def right[F[_, _], A, B](element: Reflect[F, B])(implicit F: FromBinding[F]): Record[F, Right[A, B]] =
    Record(
      Seq(Term("value", element, Doc.Empty, Nil)),
      TypeName.right,
      F.fromBinding(Binding.Record.right),
      Doc.Empty,
      Nil
    )

  def either[F[_, _], L, R](l: Reflect[F, L], r: Reflect[F, R])(implicit
    F: FromBinding[F]
  ): Variant[F, scala.Either[L, R]] =
    Variant(
      Seq(
        Term("Left", left(l), Doc.Empty, Nil),
        Term("Right", right(r), Doc.Empty, Nil)
      ),
      TypeName.either,
      F.fromBinding(Binding.Variant.either),
      Doc.Empty,
      Nil
    )

  def tuple2[F[_, _], A, B](_1: Reflect[F, A], _2: Reflect[F, B])(implicit F: FromBinding[F]): Record[F, (A, B)] =
    Record(
      Seq(Term("_1", _1, Doc.Empty, Nil), Term("_2", _2, Doc.Empty, Nil)),
      TypeName.tuple2,
      F.fromBinding(Binding.Record.tuple2),
      Doc.Empty,
      Nil
    )

  def tuple3[F[_, _], A, B, C](_1: Reflect[F, A], _2: Reflect[F, B], _3: Reflect[F, C])(implicit
    F: FromBinding[F]
  ): Record[F, (A, B, C)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil)
      ),
      TypeName.tuple3,
      F.fromBinding(Binding.Record.tuple3),
      Doc.Empty,
      Nil
    )

  def tuple4[F[_, _], A, B, C, D](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil)
      ),
      TypeName.tuple4,
      F.fromBinding(Binding.Record.tuple4),
      Doc.Empty,
      Nil
    )

  def tuple5[F[_, _], A, B, C, D, E](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil)
      ),
      TypeName.tuple5,
      F.fromBinding(Binding.Record.tuple5),
      Doc.Empty,
      Nil
    )

  def tuple6[F[_, _], A, B, C, D, E, G](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil)
      ),
      TypeName.tuple6,
      F.fromBinding(Binding.Record.tuple6),
      Doc.Empty,
      Nil
    )

  def tuple7[F[_, _], A, B, C, D, E, G, H](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G],
    _7: Reflect[F, H]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G, H)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil),
        Term("_7", _7, Doc.Empty, Nil)
      ),
      TypeName.tuple7,
      F.fromBinding(Binding.Record.tuple7),
      Doc.Empty,
      Nil
    )

  def tuple8[F[_, _], A, B, C, D, E, G, H, I](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G],
    _7: Reflect[F, H],
    _8: Reflect[F, I]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G, H, I)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil),
        Term("_7", _7, Doc.Empty, Nil),
        Term("_8", _8, Doc.Empty, Nil)
      ),
      TypeName.tuple8,
      F.fromBinding(Binding.Record.tuple8),
      Doc.Empty,
      Nil
    )

  def tuple9[F[_, _], A, B, C, D, E, G, H, I, J](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G],
    _7: Reflect[F, H],
    _8: Reflect[F, I],
    _9: Reflect[F, J]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G, H, I, J)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil),
        Term("_7", _7, Doc.Empty, Nil),
        Term("_8", _8, Doc.Empty, Nil),
        Term("_9", _9, Doc.Empty, Nil)
      ),
      TypeName.tuple9,
      F.fromBinding(Binding.Record.tuple9),
      Doc.Empty,
      Nil
    )

  def tuple10[F[_, _], A, B, C, D, E, G, H, I, J, K](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G],
    _7: Reflect[F, H],
    _8: Reflect[F, I],
    _9: Reflect[F, J],
    _10: Reflect[F, K]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G, H, I, J, K)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil),
        Term("_7", _7, Doc.Empty, Nil),
        Term("_8", _8, Doc.Empty, Nil),
        Term("_9", _9, Doc.Empty, Nil),
        Term("_10", _10, Doc.Empty, Nil)
      ),
      TypeName.tuple10,
      F.fromBinding(Binding.Record.tuple10),
      Doc.Empty,
      Nil
    )

  def tuple11[F[_, _], A, B, C, D, E, G, H, I, J, K, L](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G],
    _7: Reflect[F, H],
    _8: Reflect[F, I],
    _9: Reflect[F, J],
    _10: Reflect[F, K],
    _11: Reflect[F, L]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G, H, I, J, K, L)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil),
        Term("_7", _7, Doc.Empty, Nil),
        Term("_8", _8, Doc.Empty, Nil),
        Term("_9", _9, Doc.Empty, Nil),
        Term("_10", _10, Doc.Empty, Nil),
        Term("_11", _11, Doc.Empty, Nil)
      ),
      TypeName.tuple11,
      F.fromBinding(Binding.Record.tuple11),
      Doc.Empty,
      Nil
    )

  def tuple12[F[_, _], A, B, C, D, E, G, H, I, J, K, L, M](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G],
    _7: Reflect[F, H],
    _8: Reflect[F, I],
    _9: Reflect[F, J],
    _10: Reflect[F, K],
    _11: Reflect[F, L],
    _12: Reflect[F, M]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G, H, I, J, K, L, M)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil),
        Term("_7", _7, Doc.Empty, Nil),
        Term("_8", _8, Doc.Empty, Nil),
        Term("_9", _9, Doc.Empty, Nil),
        Term("_10", _10, Doc.Empty, Nil),
        Term("_11", _11, Doc.Empty, Nil),
        Term("_12", _12, Doc.Empty, Nil)
      ),
      TypeName.tuple12,
      F.fromBinding(Binding.Record.tuple12),
      Doc.Empty,
      Nil
    )

  def tuple13[F[_, _], A, B, C, D, E, G, H, I, J, K, L, M, N](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G],
    _7: Reflect[F, H],
    _8: Reflect[F, I],
    _9: Reflect[F, J],
    _10: Reflect[F, K],
    _11: Reflect[F, L],
    _12: Reflect[F, M],
    _13: Reflect[F, N]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G, H, I, J, K, L, M, N)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil),
        Term("_7", _7, Doc.Empty, Nil),
        Term("_8", _8, Doc.Empty, Nil),
        Term("_9", _9, Doc.Empty, Nil),
        Term("_10", _10, Doc.Empty, Nil),
        Term("_11", _11, Doc.Empty, Nil),
        Term("_12", _12, Doc.Empty, Nil),
        Term("_13", _13, Doc.Empty, Nil)
      ),
      TypeName.tuple13,
      F.fromBinding(Binding.Record.tuple13),
      Doc.Empty,
      Nil
    )

  def tuple14[F[_, _], A, B, C, D, E, G, H, I, J, K, L, M, N, O](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G],
    _7: Reflect[F, H],
    _8: Reflect[F, I],
    _9: Reflect[F, J],
    _10: Reflect[F, K],
    _11: Reflect[F, L],
    _12: Reflect[F, M],
    _13: Reflect[F, N],
    _14: Reflect[F, O]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G, H, I, J, K, L, M, N, O)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil),
        Term("_7", _7, Doc.Empty, Nil),
        Term("_8", _8, Doc.Empty, Nil),
        Term("_9", _9, Doc.Empty, Nil),
        Term("_10", _10, Doc.Empty, Nil),
        Term("_11", _11, Doc.Empty, Nil),
        Term("_12", _12, Doc.Empty, Nil),
        Term("_13", _13, Doc.Empty, Nil),
        Term("_14", _14, Doc.Empty, Nil)
      ),
      TypeName.tuple14,
      F.fromBinding(Binding.Record.tuple14),
      Doc.Empty,
      Nil
    )

  def tuple15[F[_, _], A, B, C, D, E, G, H, I, J, K, L, M, N, O, P](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G],
    _7: Reflect[F, H],
    _8: Reflect[F, I],
    _9: Reflect[F, J],
    _10: Reflect[F, K],
    _11: Reflect[F, L],
    _12: Reflect[F, M],
    _13: Reflect[F, N],
    _14: Reflect[F, O],
    _15: Reflect[F, P]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G, H, I, J, K, L, M, N, O, P)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil),
        Term("_7", _7, Doc.Empty, Nil),
        Term("_8", _8, Doc.Empty, Nil),
        Term("_9", _9, Doc.Empty, Nil),
        Term("_10", _10, Doc.Empty, Nil),
        Term("_11", _11, Doc.Empty, Nil),
        Term("_12", _12, Doc.Empty, Nil),
        Term("_13", _13, Doc.Empty, Nil),
        Term("_14", _14, Doc.Empty, Nil),
        Term("_15", _15, Doc.Empty, Nil)
      ),
      TypeName.tuple15,
      F.fromBinding(Binding.Record.tuple15),
      Doc.Empty,
      Nil
    )

  def tuple16[F[_, _], A, B, C, D, E, G, H, I, J, K, L, M, N, O, P, Q](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G],
    _7: Reflect[F, H],
    _8: Reflect[F, I],
    _9: Reflect[F, J],
    _10: Reflect[F, K],
    _11: Reflect[F, L],
    _12: Reflect[F, M],
    _13: Reflect[F, N],
    _14: Reflect[F, O],
    _15: Reflect[F, P],
    _16: Reflect[F, Q]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G, H, I, J, K, L, M, N, O, P, Q)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil),
        Term("_7", _7, Doc.Empty, Nil),
        Term("_8", _8, Doc.Empty, Nil),
        Term("_9", _9, Doc.Empty, Nil),
        Term("_10", _10, Doc.Empty, Nil),
        Term("_11", _11, Doc.Empty, Nil),
        Term("_12", _12, Doc.Empty, Nil),
        Term("_13", _13, Doc.Empty, Nil),
        Term("_14", _14, Doc.Empty, Nil),
        Term("_15", _15, Doc.Empty, Nil),
        Term("_16", _16, Doc.Empty, Nil)
      ),
      TypeName.tuple16,
      F.fromBinding(Binding.Record.tuple16),
      Doc.Empty,
      Nil
    )

  def tuple17[F[_, _], A, B, C, D, E, G, H, I, J, K, L, M, N, O, P, Q, R](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G],
    _7: Reflect[F, H],
    _8: Reflect[F, I],
    _9: Reflect[F, J],
    _10: Reflect[F, K],
    _11: Reflect[F, L],
    _12: Reflect[F, M],
    _13: Reflect[F, N],
    _14: Reflect[F, O],
    _15: Reflect[F, P],
    _16: Reflect[F, Q],
    _17: Reflect[F, R]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G, H, I, J, K, L, M, N, O, P, Q, R)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil),
        Term("_7", _7, Doc.Empty, Nil),
        Term("_8", _8, Doc.Empty, Nil),
        Term("_9", _9, Doc.Empty, Nil),
        Term("_10", _10, Doc.Empty, Nil),
        Term("_11", _11, Doc.Empty, Nil),
        Term("_12", _12, Doc.Empty, Nil),
        Term("_13", _13, Doc.Empty, Nil),
        Term("_14", _14, Doc.Empty, Nil),
        Term("_15", _15, Doc.Empty, Nil),
        Term("_16", _16, Doc.Empty, Nil),
        Term("_17", _17, Doc.Empty, Nil)
      ),
      TypeName.tuple17,
      F.fromBinding(Binding.Record.tuple17),
      Doc.Empty,
      Nil
    )

  def tuple18[F[_, _], A, B, C, D, E, G, H, I, J, K, L, M, N, O, P, Q, R, S](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G],
    _7: Reflect[F, H],
    _8: Reflect[F, I],
    _9: Reflect[F, J],
    _10: Reflect[F, K],
    _11: Reflect[F, L],
    _12: Reflect[F, M],
    _13: Reflect[F, N],
    _14: Reflect[F, O],
    _15: Reflect[F, P],
    _16: Reflect[F, Q],
    _17: Reflect[F, R],
    _18: Reflect[F, S]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G, H, I, J, K, L, M, N, O, P, Q, R, S)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil),
        Term("_7", _7, Doc.Empty, Nil),
        Term("_8", _8, Doc.Empty, Nil),
        Term("_9", _9, Doc.Empty, Nil),
        Term("_10", _10, Doc.Empty, Nil),
        Term("_11", _11, Doc.Empty, Nil),
        Term("_12", _12, Doc.Empty, Nil),
        Term("_13", _13, Doc.Empty, Nil),
        Term("_14", _14, Doc.Empty, Nil),
        Term("_15", _15, Doc.Empty, Nil),
        Term("_16", _16, Doc.Empty, Nil),
        Term("_17", _17, Doc.Empty, Nil),
        Term("_18", _18, Doc.Empty, Nil)
      ),
      TypeName.tuple18,
      F.fromBinding(Binding.Record.tuple18),
      Doc.Empty,
      Nil
    )

  def tuple19[F[_, _], A, B, C, D, E, G, H, I, J, K, L, M, N, O, P, Q, R, S, T](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G],
    _7: Reflect[F, H],
    _8: Reflect[F, I],
    _9: Reflect[F, J],
    _10: Reflect[F, K],
    _11: Reflect[F, L],
    _12: Reflect[F, M],
    _13: Reflect[F, N],
    _14: Reflect[F, O],
    _15: Reflect[F, P],
    _16: Reflect[F, Q],
    _17: Reflect[F, R],
    _18: Reflect[F, S],
    _19: Reflect[F, T]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil),
        Term("_7", _7, Doc.Empty, Nil),
        Term("_8", _8, Doc.Empty, Nil),
        Term("_9", _9, Doc.Empty, Nil),
        Term("_10", _10, Doc.Empty, Nil),
        Term("_11", _11, Doc.Empty, Nil),
        Term("_12", _12, Doc.Empty, Nil),
        Term("_13", _13, Doc.Empty, Nil),
        Term("_14", _14, Doc.Empty, Nil),
        Term("_15", _15, Doc.Empty, Nil),
        Term("_16", _16, Doc.Empty, Nil),
        Term("_17", _17, Doc.Empty, Nil),
        Term("_18", _18, Doc.Empty, Nil),
        Term("_19", _19, Doc.Empty, Nil)
      ),
      TypeName.tuple19,
      F.fromBinding(Binding.Record.tuple19),
      Doc.Empty,
      Nil
    )

  def tuple20[F[_, _], A, B, C, D, E, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G],
    _7: Reflect[F, H],
    _8: Reflect[F, I],
    _9: Reflect[F, J],
    _10: Reflect[F, K],
    _11: Reflect[F, L],
    _12: Reflect[F, M],
    _13: Reflect[F, N],
    _14: Reflect[F, O],
    _15: Reflect[F, P],
    _16: Reflect[F, Q],
    _17: Reflect[F, R],
    _18: Reflect[F, S],
    _19: Reflect[F, T],
    _20: Reflect[F, U]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil),
        Term("_7", _7, Doc.Empty, Nil),
        Term("_8", _8, Doc.Empty, Nil),
        Term("_9", _9, Doc.Empty, Nil),
        Term("_10", _10, Doc.Empty, Nil),
        Term("_11", _11, Doc.Empty, Nil),
        Term("_12", _12, Doc.Empty, Nil),
        Term("_13", _13, Doc.Empty, Nil),
        Term("_14", _14, Doc.Empty, Nil),
        Term("_15", _15, Doc.Empty, Nil),
        Term("_16", _16, Doc.Empty, Nil),
        Term("_17", _17, Doc.Empty, Nil),
        Term("_18", _18, Doc.Empty, Nil),
        Term("_19", _19, Doc.Empty, Nil),
        Term("_20", _20, Doc.Empty, Nil)
      ),
      TypeName.tuple20,
      F.fromBinding(Binding.Record.tuple20),
      Doc.Empty,
      Nil
    )

  def tuple21[F[_, _], A, B, C, D, E, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G],
    _7: Reflect[F, H],
    _8: Reflect[F, I],
    _9: Reflect[F, J],
    _10: Reflect[F, K],
    _11: Reflect[F, L],
    _12: Reflect[F, M],
    _13: Reflect[F, N],
    _14: Reflect[F, O],
    _15: Reflect[F, P],
    _16: Reflect[F, Q],
    _17: Reflect[F, R],
    _18: Reflect[F, S],
    _19: Reflect[F, T],
    _20: Reflect[F, U],
    _21: Reflect[F, V]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil),
        Term("_7", _7, Doc.Empty, Nil),
        Term("_8", _8, Doc.Empty, Nil),
        Term("_9", _9, Doc.Empty, Nil),
        Term("_10", _10, Doc.Empty, Nil),
        Term("_11", _11, Doc.Empty, Nil),
        Term("_12", _12, Doc.Empty, Nil),
        Term("_13", _13, Doc.Empty, Nil),
        Term("_14", _14, Doc.Empty, Nil),
        Term("_15", _15, Doc.Empty, Nil),
        Term("_16", _16, Doc.Empty, Nil),
        Term("_17", _17, Doc.Empty, Nil),
        Term("_18", _18, Doc.Empty, Nil),
        Term("_19", _19, Doc.Empty, Nil),
        Term("_20", _20, Doc.Empty, Nil),
        Term("_21", _21, Doc.Empty, Nil)
      ),
      TypeName.tuple21,
      F.fromBinding(Binding.Record.tuple21),
      Doc.Empty,
      Nil
    )

  def tuple22[F[_, _], A, B, C, D, E, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W](
    _1: Reflect[F, A],
    _2: Reflect[F, B],
    _3: Reflect[F, C],
    _4: Reflect[F, D],
    _5: Reflect[F, E],
    _6: Reflect[F, G],
    _7: Reflect[F, H],
    _8: Reflect[F, I],
    _9: Reflect[F, J],
    _10: Reflect[F, K],
    _11: Reflect[F, L],
    _12: Reflect[F, M],
    _13: Reflect[F, N],
    _14: Reflect[F, O],
    _15: Reflect[F, P],
    _16: Reflect[F, Q],
    _17: Reflect[F, R],
    _18: Reflect[F, S],
    _19: Reflect[F, T],
    _20: Reflect[F, U],
    _21: Reflect[F, V],
    _22: Reflect[F, W]
  )(implicit F: FromBinding[F]): Record[F, (A, B, C, D, E, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W)] =
    Record(
      ArraySeq(
        Term("_1", _1, Doc.Empty, Nil),
        Term("_2", _2, Doc.Empty, Nil),
        Term("_3", _3, Doc.Empty, Nil),
        Term("_4", _4, Doc.Empty, Nil),
        Term("_5", _5, Doc.Empty, Nil),
        Term("_6", _6, Doc.Empty, Nil),
        Term("_7", _7, Doc.Empty, Nil),
        Term("_8", _8, Doc.Empty, Nil),
        Term("_9", _9, Doc.Empty, Nil),
        Term("_10", _10, Doc.Empty, Nil),
        Term("_11", _11, Doc.Empty, Nil),
        Term("_12", _12, Doc.Empty, Nil),
        Term("_13", _13, Doc.Empty, Nil),
        Term("_14", _14, Doc.Empty, Nil),
        Term("_15", _15, Doc.Empty, Nil),
        Term("_16", _16, Doc.Empty, Nil),
        Term("_17", _17, Doc.Empty, Nil),
        Term("_18", _18, Doc.Empty, Nil),
        Term("_19", _19, Doc.Empty, Nil),
        Term("_20", _20, Doc.Empty, Nil),
        Term("_21", _21, Doc.Empty, Nil),
        Term("_22", _22, Doc.Empty, Nil)
      ),
      TypeName.tuple22,
      F.fromBinding(Binding.Record.tuple22),
      Doc.Empty,
      Nil
    )

  object Extractors {
    object List {
      def unapply[F[_, _], A](reflect: Reflect[F, List[A]]): Option[Reflect[F, A]] =
        reflect match {
          case Sequence(element, _, tn, _, _) if tn == TypeName.list => Some(element)
          case _                                                     => None
        }
    }

    object Vector {
      def unapply[F[_, _], A](reflect: Reflect[F, Vector[A]]): Option[Reflect[F, A]] =
        reflect match {
          case Sequence(element, _, tn, _, _) if tn == TypeName.vector => Some(element)
          case _                                                       => None
        }
    }

    object Set {
      def unapply[F[_, _], A](reflect: Reflect[F, Set[A]]): Option[Reflect[F, A]] =
        reflect match {
          case Sequence(element, _, tn, _, _) if tn == TypeName.set => Some(element)
          case _                                                    => None
        }
    }

    object Array {
      def unapply[F[_, _], A](reflect: Reflect[F, Array[A]]): Option[Reflect[F, A]] =
        reflect match {
          case Sequence(element, _, tn, _, _) if tn == TypeName.array => Some(element)
          case _                                                      => None
        }
    }

    object Option {
      def unapply[F[_, _], A](reflect: Reflect[F, Option[A]]): Option[Reflect[F, A]] =
        reflect match {
          case Variant(noneTerm :: someTerm :: Nil, tn, _, _, _) if tn == TypeName.option =>
            (noneTerm, someTerm) match {
              case (Term("None", _, _, _), Term("Some", element, _, _)) => Some(element.asInstanceOf[Reflect[F, A]])
              case _                                                    => None
            }
          case _ => None
        }
    }

    object Either {
      def unapply[F[_, _], L, R](reflect: Reflect[F, scala.Either[L, R]]): Option[(Reflect[F, L], Reflect[F, R])] =
        reflect match {
          case Variant(leftTerm :: rightTerm :: Nil, tn, _, _, _) if tn == TypeName.either =>
            (leftTerm, rightTerm) match {
              case (Term("Left", left, _, _), Term("Right", right, _, _)) =>
                Some((left.asInstanceOf[Reflect[F, L]], right.asInstanceOf[Reflect[F, R]]))
              case _ => None
            }
          case _ => None
        }
    }
  }
}
