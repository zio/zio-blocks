package zio.blocks.schema

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._
import scala.collection.immutable.ArraySeq

sealed trait Optic[F[_, _], S, A] { self =>
  def structure: Reflect[F, S]

  def focus: Reflect[F, A]

  // Compose this optic with a lens:
  def apply[B](that: Lens[F, A, B]): Optic[F, S, B]

  // Compose this optic with a prism:
  def apply[B <: A](that: Prism[F, A, B]): Optic[F, S, B]

  // Compose this optic with an optional:
  def apply[B](that: Optional[F, A, B]): Optic[F, S, B]

  // Compose this optic with a traversal:
  def apply[B](that: Traversal[F, A, B]): Traversal[F, S, B]

  def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S

  def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optic[G, S, A]

  def noBinding: Optic[NoBinding, S, A]

  final def listValues[B](implicit ev: A <:< List[B], F1: HasBinding[F], F2: FromBinding[F]): Traversal[F, S, B] = {
    import Reflect.Extractors.List

    val list = self.asSub[List[B]]
    list.focus match {
      case List(element) => list(Traversal.listValues(element))
      case _             => sys.error("Expected List")
    }
  }

  final def vectorValues[B](implicit ev: A <:< Vector[B], F1: HasBinding[F], F2: FromBinding[F]): Traversal[F, S, B] = {
    import Reflect.Extractors.Vector

    val vector = self.asSub[Vector[B]]
    vector.focus match {
      case Vector(element) => vector(Traversal.vectorValues(element))
      case _               => sys.error("Expected Vector")
    }
  }

  final def setValues[B](implicit ev: A <:< Set[B], F1: HasBinding[F], F2: FromBinding[F]): Traversal[F, S, B] = {
    import Reflect.Extractors.Set

    val set = self.asSub[Set[B]]
    set.focus match {
      case Set(element) => set(Traversal.setValues(element))
      case _            => sys.error("Expected Set")
    }
  }

  final def arrayValues[B](implicit ev: A <:< Array[B], F1: HasBinding[F], F2: FromBinding[F]): Traversal[F, S, B] = {
    import Reflect.Extractors.Array

    val array = self.asSub[Array[B]]
    array.focus match {
      case Array(element) => array(Traversal.arrayValues(element))
      case _              => sys.error("Expected Array")
    }
  }

  final def asSub[B](implicit ev: A <:< B): Optic[F, S, B] = self.asInstanceOf[Optic[F, S, B]]

  override def hashCode: Int = linearized.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case other: Optic[_, _, _] => other.linearized.equals(linearized)
    case _                     => false
  }

  private[schema] def linearized: ArraySeq[Leaf[F, _, _]]
}

object Optic {
  type Bound[S, A] = Optic[Binding, S, A]
}

private[schema] sealed trait Leaf[F[_, _], S, A] extends Optic[F, S, A] {
  override private[schema] def linearized: ArraySeq[Leaf[F, S, A]] = ArraySeq(this)
}

sealed trait Lens[F[_, _], S, A] extends Optic[F, S, A] {
  def get(s: S)(implicit F: HasBinding[F]): A

  def replace(s: S, a: A)(implicit F: HasBinding[F]): S

  // Compose this lens with a lens:
  override def apply[B](that: Lens[F, A, B]): Lens[F, S, B] = Lens(this, that)

  // Compose this lens with a prism:
  override def apply[B <: A](that: Prism[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this lens with an optional:
  override def apply[B](that: Optional[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this lens with a traversal:
  override def apply[B](that: Traversal[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  override def refineBinding[G[_, _]](f: RefineBinding[F, G]): Lens[G, S, A]

  override def noBinding: Lens[NoBinding, S, A] = refineBinding(RefineBinding.noBinding())
}

object Lens {
  type Bound[S, A] = Lens[Binding, S, A]

  def apply[F[_, _], S, A](parent: Reflect.Record[F, S], child: Term[F, S, A]): Lens[F, S, A] = {
    require((parent ne null) && (child ne null))
    new LensImpl(ArraySeq(parent), ArraySeq(child))
  }

  def apply[F[_, _], S, T, A](first: Lens[F, S, T], second: Lens[F, T, A]): Lens[F, S, A] = {
    require((first ne null) && (second ne null))
    val lens1 = first.asInstanceOf[LensImpl[F, S, A]]
    val lens2 = second.asInstanceOf[LensImpl[F, S, A]]
    new LensImpl(lens1.parents ++ lens2.parents, lens1.childs ++ lens2.childs)
  }

  private[schema] case class LensImpl[F[_, _], S, A](
    parents: ArraySeq[Reflect.Record[F, S]],
    childs: ArraySeq[Term[F, S, A]]
  ) extends Lens[F, S, A]
      with Leaf[F, S, A] {
    private[this] var bindings: Array[LensBinding]  = null
    private[this] var usedRegisters: RegisterOffset = RegisterOffset.Zero

    private[this] def init(implicit F: HasBinding[F]): Unit = {
      var offset   = RegisterOffset.Zero
      val len      = parents.length
      val bindings = new Array[LensBinding](len)
      var idx      = 0
      while (idx < len) {
        val parent = parents(idx)
        bindings(idx) = new LensBinding(
          deconstructor = F.deconstructor(parent.recordBinding).asInstanceOf[Deconstructor[Any]],
          constructor = F.constructor(parent.recordBinding).asInstanceOf[Constructor[Any]],
          register = parent
            .registers(parent.fields.indexWhere {
              val childName = childs(idx).name
              x => x.name == childName
            })
            .asInstanceOf[Register[Any]],
          offset = offset
        )
        offset = RegisterOffset.add(offset, parent.usedRegisters)
        idx += 1
      }
      this.bindings = bindings
      this.usedRegisters = offset
    }

    override def get(s: S)(implicit F: HasBinding[F]): A = {
      if ((bindings eq null) || (usedRegisters == RegisterOffset.Zero)) init
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var idx       = 0
      while (idx < len) {
        val binding = bindings(idx)
        val offset  = binding.offset
        binding.deconstructor.deconstruct(registers, offset, x)
        x = binding.register.get(registers, offset)
        idx += 1
      }
      x.asInstanceOf[A]
    }

    override def replace(s: S, a: A)(implicit F: HasBinding[F]): S = {
      if ((bindings eq null) || (usedRegisters == RegisterOffset.Zero)) init
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var idx       = 0
      while (idx < len) {
        val binding = bindings(idx)
        val offset  = binding.offset
        binding.deconstructor.deconstruct(registers, offset, x)
        if (idx < len) x = binding.register.get(registers, offset)
        idx += 1
      }
      x = a
      while (idx > 0) {
        idx -= 1
        val binding = bindings(idx)
        val offset  = binding.offset
        binding.register.set(registers, offset, x)
        x = binding.constructor.construct(registers, offset)
      }
      x.asInstanceOf[S]
    }

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = {
      if ((bindings eq null) || (usedRegisters == RegisterOffset.Zero)) init
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var idx       = 0
      while (idx < len) {
        val binding = bindings(idx)
        val offset  = binding.offset
        binding.deconstructor.deconstruct(registers, offset, x)
        x = binding.register.get(registers, offset)
        idx += 1
      }
      x = f(x.asInstanceOf[A])
      while (idx > 0) {
        idx -= 1
        val binding = bindings(idx)
        val offset  = binding.offset
        binding.register.set(registers, offset, x)
        x = binding.constructor.construct(registers, offset)
      }
      x.asInstanceOf[S]
    }

    override def refineBinding[G[_, _]](f: RefineBinding[F, G]): Lens[G, S, A] =
      new LensImpl(parents.map(_.refineBinding(f)), childs.map(_.refineBinding(f)))

    override def structure: Reflect[F, S] = parents(0)

    override def focus: Reflect[F, A] = childs(childs.length - 1).value

    override def hashCode: Int = parents.hashCode ^ childs.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: LensImpl[_, _, _] => other.parents.equals(parents) && other.childs.equals(childs)
      case _                        => false
    }
  }

  private[schema] case class LensBinding(
    deconstructor: Deconstructor[Any],
    constructor: Constructor[Any],
    register: Register[Any],
    offset: RegisterOffset
  )
}

sealed trait Prism[F[_, _], S, A <: S] extends Optic[F, S, A] {
  def getOption(s: S)(implicit F: HasBinding[F]): Option[A]

  def reverseGet(a: A): S

  def replace(s: S, a: A)(implicit F: HasBinding[F]): S

  def replaceOption(s: S, a: A)(implicit F: HasBinding[F]): Option[S]

  // Compose this prism with a prism:
  override def apply[B <: A](that: Prism[F, A, B]): Prism[F, S, B] = Prism(this, that)

  // Compose this prism with a lens:
  override def apply[B](that: Lens[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this prism with an optional:
  override def apply[B](that: Optional[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this prism with a traversal:
  override def apply[B](that: Traversal[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  override def refineBinding[G[_, _]](f: RefineBinding[F, G]): Prism[G, S, A]

  override def noBinding: Prism[NoBinding, S, A] = refineBinding(RefineBinding.noBinding())
}

object Prism {
  type Bound[S, A <: S] = Prism[Binding, S, A]

  def apply[F[_, _], S, A <: S](parent: Reflect.Variant[F, S], child: Term[F, S, A]): Prism[F, S, A] = {
    require((parent ne null) && (child ne null))
    new PrismImpl(parent, parent, child)
  }

  def apply[F[_, _], S, T <: S, A <: T](first: Prism[F, S, T], second: Prism[F, T, A]): Prism[F, S, A] = {
    require((first ne null) && (second ne null))
    val prism1 = first.asInstanceOf[PrismImpl[F, _, _, _]]
    val prism2 = second.asInstanceOf[PrismImpl[F, _, _, _]]
    new PrismImpl(
      prism1.structure.asInstanceOf[Reflect.Variant[F, S]],
      prism2.parent.asInstanceOf[Reflect.Variant[F, T]],
      prism2.child.asInstanceOf[Term[F, T, A]]
    )
  }

  private[schema] case class PrismImpl[F[_, _], S, T <: S, A <: T](
    structure: Reflect.Variant[F, S],
    parent: Reflect.Variant[F, T],
    child: Term[F, T, A]
  ) extends Prism[F, S, A]
      with Leaf[F, S, A] {
    private[this] var matcher: Matcher[A] = null

    private def init(implicit F: HasBinding[F]): Unit = matcher = F
      .matchers(parent.variantBinding)
      .apply(parent.cases.indexWhere {
        val name = child.name
        x => x.name == name
      })
      .asInstanceOf[Matcher[A]]

    def focus: Reflect[F, A] = child.value

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = {
      if (matcher eq null) init
      val a = matcher.downcastOrNull(s)
      if (a != null) new Some(a)
      else None
    }

    def reverseGet(a: A): S = a

    def replace(s: S, a: A)(implicit F: HasBinding[F]): S = {
      if (matcher eq null) init
      if (matcher.downcastOrNull(s) != null) a
      else s
    }

    def replaceOption(s: S, a: A)(implicit F: HasBinding[F]): Option[S] = {
      if (matcher eq null) init
      if (matcher.downcastOrNull(s) != null) new Some(a)
      else None
    }

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = {
      if (matcher eq null) init
      val a = matcher.downcastOrNull(s)
      if (a != null) f(a)
      else s
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Prism[G, S, A] =
      new PrismImpl(structure.refineBinding(f), parent.refineBinding(f), child.refineBinding(f))

    override def hashCode: Int = structure.hashCode ^ child.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: PrismImpl[_, _, _, _] => other.structure.equals(structure) && other.child.equals(child)
      case _                            => false
    }
  }
}

sealed trait Optional[F[_, _], S, A] extends Optic[F, S, A] {
  def getOption(s: S)(implicit F: HasBinding[F]): Option[A]

  def replace(s: S, a: A)(implicit F: HasBinding[F]): S

  def replaceOption(s: S, a: A)(implicit F: HasBinding[F]): Option[S]

  // Compose this optional with a lens:
  override def apply[B](that: Lens[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this optional with a prism:
  override def apply[B <: A](that: Prism[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this optional with an optional:
  override def apply[B](that: Optional[F, A, B]): Optional[F, S, B] = Optional(this, that)

  // Compose this optional with a traversal:
  override def apply[B](that: Traversal[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  override def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A]

  override def noBinding: Optional[NoBinding, S, A] = refineBinding(RefineBinding.noBinding())
}

object Optional {
  type Bound[S, A] = Optional[Binding, S, A]

  def apply[F[_, _], S, T, A](first: Optional[F, S, T], second: Lens[F, T, A]): Optional[F, S, A] =
    apply(first.linearized, second.linearized)

  def apply[F[_, _], S, T, A <: T](first: Optional[F, S, T], second: Prism[F, T, A]): Optional[F, S, A] =
    apply(first.linearized, second.linearized)

  def apply[F[_, _], S, T, A](first: Optional[F, S, T], second: Optional[F, T, A]): Optional[F, S, A] =
    apply(first.linearized, second.linearized)

  def apply[F[_, _], S, T, A <: T](first: Lens[F, S, T], second: Prism[F, T, A]): Optional[F, S, A] =
    apply(first.linearized, second.linearized)

  def apply[F[_, _], S, T, A](first: Lens[F, S, T], second: Optional[F, T, A]): Optional[F, S, A] =
    apply(first.linearized, second.linearized)

  def apply[F[_, _], S, T <: S, A](first: Prism[F, S, T], second: Lens[F, T, A]): Optional[F, S, A] =
    apply(first.linearized, second.linearized)

  def apply[F[_, _], S, T <: S, A](first: Prism[F, S, T], second: Optional[F, T, A]): Optional[F, S, A] =
    apply(first.linearized, second.linearized)

  private[this] def apply[F[_, _], S, A](
    leafs1: ArraySeq[Leaf[F, _, _]],
    leafs2: ArraySeq[Leaf[F, _, _]]
  ): Optional[F, S, A] =
    new OptionalImpl((leafs1.last, leafs2.head) match {
      case (lens1: Lens[_, _, _], lens2: Lens[_, _, _]) =>
        val lens = Lens.apply(lens1.asInstanceOf[Lens[F, Any, Any]], lens2.asInstanceOf[Lens[F, Any, Any]])
        (leafs1.init :+ lens.asInstanceOf[Leaf[F, _, _]]) ++ leafs2.tail
      case (prism1: Prism[_, _, _], prism2: Prism[_, _, _]) =>
        val prism = Prism.apply(prism1.asInstanceOf[Prism[F, Any, Any]], prism2.asInstanceOf[Prism[F, Any, Any]])
        (leafs1.init :+ prism.asInstanceOf[Leaf[F, _, _]]) ++ leafs2.tail
      case _ =>
        leafs1 ++ leafs2
    })

  private[schema] case class OptionalImpl[F[_, _], S, A](leafs: ArraySeq[Leaf[F, _, _]]) extends Optional[F, S, A] {
    def structure: Reflect[F, S] = leafs(0).structure.asInstanceOf[Reflect[F, S]]

    def focus: Reflect[F, A] = leafs(leafs.length - 1).focus.asInstanceOf[Reflect[F, A]]

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = {
      var x: Any = s
      val len    = leafs.length
      var idx    = 0
      while (idx < len) {
        val leaf = leafs(idx)
        if (leaf.isInstanceOf[Lens.LensImpl[F, _, _]]) {
          x = leaf.asInstanceOf[Lens[F, Any, Any]].get(x)
        } else {
          x = leaf.asInstanceOf[Prism[F, Any, Any]].getOption(x) match {
            case Some(v) => v
            case _       => return None
          }
        }
        idx += 1
      }
      new Some(x.asInstanceOf[A])
    }

    def replace(s: S, a: A)(implicit F: HasBinding[F]): S = {
      var idx = leafs.length
      idx -= 1
      val last = leafs(idx)
      var g =
        if (last.isInstanceOf[Lens.LensImpl[F, _, _]]) {
          val lens = last.asInstanceOf[Lens[F, Any, Any]]
          (x: Any) => lens.replace(x, a)
        } else {
          val prism = last.asInstanceOf[Prism[F, Any, Any]]
          (x: Any) => prism.replace(x, a)
        }
      while (idx > 0) {
        idx -= 1
        val leaf = leafs(idx).asInstanceOf[Leaf[F, Any, Any]]
        val h    = g
        g = (x: Any) => leaf.modify(x, h)
      }
      g(s).asInstanceOf[S]
    }

    def replaceOption(s: S, a: A)(implicit F: HasBinding[F]): Option[S] =
      if (getOption(s) ne None) new Some(replace(s, a))
      else None

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = {
      var g   = f.asInstanceOf[Any => Any]
      var idx = leafs.length
      while (idx > 0) {
        idx -= 1
        val leaf = leafs(idx).asInstanceOf[Leaf[F, Any, Any]]
        val h    = g
        g = (x: Any) => leaf.modify(x, h)
      }
      g(s).asInstanceOf[S]
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Optional[G, S, A] =
      new OptionalImpl(leafs.map(_.refineBinding(f).asInstanceOf[Leaf[G, _, _]]))

    private[schema] def linearized: ArraySeq[Leaf[F, _, _]] = leafs
  }
}

sealed trait Traversal[F[_, _], S, A] extends Optic[F, S, A] { self =>
  def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z

  // Compose this traversal with a lens:
  override def apply[B](that: Lens[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  // Compose this traversal with a prism:
  override def apply[B <: A](that: Prism[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  // Compose this traversal with an optional:
  override def apply[B](that: Optional[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  // Compose this traversal with a traversal:
  override def apply[B](that: Traversal[F, A, B]): Traversal[F, S, B] = Traversal(this, that)

  override def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A]

  override def noBinding: Traversal[NoBinding, S, A] = refineBinding(RefineBinding.noBinding())
}

object Traversal {
  type Bound[S, A] = Traversal[Binding, S, A]

  def apply[F[_, _], S, T, A](first: Traversal[F, S, T], second: Traversal[F, T, A]): Traversal[F, S, A] =
    apply(first.linearized, second.linearized)

  def apply[F[_, _], S, T, A](first: Traversal[F, S, T], second: Lens[F, T, A]): Traversal[F, S, A] =
    apply(first.linearized, second.linearized)

  def apply[F[_, _], S, T, A <: T](first: Traversal[F, S, T], second: Prism[F, T, A]): Traversal[F, S, A] =
    apply(first.linearized, second.linearized)

  def apply[F[_, _], S, T, A](first: Traversal[F, S, T], second: Optional[F, T, A]): Traversal[F, S, A] =
    apply(first.linearized, second.linearized)

  def apply[F[_, _], S, T, A](first: Lens[F, S, T], second: Traversal[F, T, A]): Traversal[F, S, A] =
    apply(first.linearized, second.linearized)

  def apply[F[_, _], S, T <: S, A](first: Prism[F, S, T], second: Traversal[F, T, A]): Traversal[F, S, A] =
    apply(first.linearized, second.linearized)

  def apply[F[_, _], S, T, A](first: Optional[F, S, T], second: Traversal[F, T, A]): Traversal[F, S, A] =
    apply(first.linearized, second.linearized)

  private[this] def apply[F[_, _], S, A](
    leafs1: ArraySeq[Leaf[F, _, _]],
    leafs2: ArraySeq[Leaf[F, _, _]]
  ): Traversal[F, S, A] =
    new TraversalMixed((leafs1.last, leafs2.head) match {
      case (lens1: Lens[_, _, _], lens2: Lens[_, _, _]) =>
        val lens = Lens.apply(lens1.asInstanceOf[Lens[F, Any, Any]], lens2.asInstanceOf[Lens[F, Any, Any]])
        (leafs1.init :+ lens.asInstanceOf[Leaf[F, _, _]]) ++ leafs2.tail
      case (prism1: Prism[_, _, _], prism2: Prism[_, _, _]) =>
        val prism = Prism.apply(prism1.asInstanceOf[Prism[F, Any, Any]], prism2.asInstanceOf[Prism[F, Any, Any]])
        (leafs1.init :+ prism.asInstanceOf[Leaf[F, _, _]]) ++ leafs2.tail
      case _ =>
        leafs1 ++ leafs2
    })

  def arrayValues[F[_, _], A](reflect: Reflect[F, A])(implicit F: FromBinding[F]): Traversal[F, Array[A], A] = {
    require(reflect ne null)
    new SeqValues(Reflect.array(reflect))
  }

  def listValues[F[_, _], A](reflect: Reflect[F, A])(implicit F: FromBinding[F]): Traversal[F, List[A], A] = {
    require(reflect ne null)
    new SeqValues(Reflect.list(reflect))
  }

  def mapKeys[F[_, _], Key, Value, M[_, _]](map: Reflect.Map[F, Key, Value, M]): Traversal[F, M[Key, Value], Key] = {
    require(map ne null)
    new MapKeys(map)
  }

  def mapValues[F[_, _], Key, Value, M[_, _]](
    map: Reflect.Map[F, Key, Value, M]
  ): Traversal[F, M[Key, Value], Value] = {
    require(map ne null)
    new MapValues(map)
  }

  def seqValues[F[_, _], A, C[_]](seq: Reflect.Sequence[F, A, C]): Traversal[F, C[A], A] = {
    require(seq ne null)
    new SeqValues(seq)
  }

  def setValues[F[_, _], A](reflect: Reflect[F, A])(implicit F: FromBinding[F]): Traversal[F, Set[A], A] = {
    require(reflect ne null)
    new SeqValues(Reflect.set(reflect))
  }

  def vectorValues[F[_, _], A](reflect: Reflect[F, A])(implicit F: FromBinding[F]): Traversal[F, Vector[A], A] = {
    require(reflect ne null)
    new SeqValues(Reflect.vector(reflect))
  }

  private[schema] case class SeqValues[F[_, _], A, C[_]](seq: Reflect.Sequence[F, A, C])
      extends Traversal[F, C[A], A]
      with Leaf[F, C[A], A] {
    def structure: Reflect[F, C[A]] = seq

    def focus: Reflect[F, A] = seq.element

    def fold[Z](s: C[A])(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z = {
      val deconstructor = F.seqDeconstructor(seq.seqBinding)
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[C] =>
          val len = indexed.length(s)
          var idx = 0
          indexed.elementType(s) match {
            case _: RegisterType.Boolean.type =>
              val ss = s.asInstanceOf[C[Boolean]]
              val sf = f.asInstanceOf[(Z, Boolean) => Z]
              var z  = zero
              while (idx < len) {
                z = sf(z, indexed.booleanAt(ss, idx))
                idx += 1
              }
              z
            case _: RegisterType.Byte.type =>
              val ss = s.asInstanceOf[C[Byte]]
              val sf = f.asInstanceOf[(Z, Byte) => Z]
              var z  = zero
              while (idx < len) {
                z = sf(z, indexed.byteAt(ss, idx))
                idx += 1
              }
              z
            case _: RegisterType.Short.type =>
              val ss = s.asInstanceOf[C[Short]]
              val sf = f.asInstanceOf[(Z, Short) => Z]
              var z  = zero
              while (idx < len) {
                z = sf(z, indexed.shortAt(ss, idx))
                idx += 1
              }
              z
            case _: RegisterType.Int.type =>
              zero match {
                case zi: Int =>
                  val ss     = s.asInstanceOf[C[Int]]
                  val sf     = f.asInstanceOf[(Int, Int) => Int]
                  var z: Int = zi
                  while (idx < len) {
                    z = sf(z, indexed.intAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  val ss      = s.asInstanceOf[C[Int]]
                  val sf      = f.asInstanceOf[(Long, Int) => Long]
                  var z: Long = zl
                  while (idx < len) {
                    z = sf(z, indexed.intAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  val ss        = s.asInstanceOf[C[Int]]
                  val sf        = f.asInstanceOf[(Double, Int) => Double]
                  var z: Double = zd
                  while (idx < len) {
                    z = sf(z, indexed.intAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  val ss = s.asInstanceOf[C[Int]]
                  val sf = f.asInstanceOf[(Z, Int) => Z]
                  var z  = zero
                  while (idx < len) {
                    z = sf(z, indexed.intAt(ss, idx))
                    idx += 1
                  }
                  z
              }
            case _: RegisterType.Long.type =>
              zero match {
                case zi: Int =>
                  val ss     = s.asInstanceOf[C[Long]]
                  val sf     = f.asInstanceOf[(Int, Long) => Int]
                  var z: Int = zi
                  while (idx < len) {
                    z = sf(z, indexed.longAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  val ss      = s.asInstanceOf[C[Long]]
                  val sf      = f.asInstanceOf[(Long, Long) => Long]
                  var z: Long = zl
                  while (idx < len) {
                    z = sf(z, indexed.longAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  val ss        = s.asInstanceOf[C[Long]]
                  val sf        = f.asInstanceOf[(Double, Long) => Double]
                  var z: Double = zd
                  while (idx < len) {
                    z = sf(z, indexed.longAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  val ss = s.asInstanceOf[C[Long]]
                  val sf = f.asInstanceOf[(Z, Long) => Z]
                  var z  = zero
                  while (idx < len) {
                    z = sf(z, indexed.longAt(ss, idx))
                    idx += 1
                  }
                  z
              }
            case _: RegisterType.Double.type =>
              zero match {
                case zi: Int =>
                  val ss     = s.asInstanceOf[C[Double]]
                  val sf     = f.asInstanceOf[(Int, Double) => Int]
                  var z: Int = zi
                  while (idx < len) {
                    z = sf(z, indexed.doubleAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  val ss      = s.asInstanceOf[C[Double]]
                  val sf      = f.asInstanceOf[(Long, Double) => Long]
                  var z: Long = zl
                  while (idx < len) {
                    z = sf(z, indexed.doubleAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  val ss        = s.asInstanceOf[C[Double]]
                  val sf        = f.asInstanceOf[(Double, Double) => Double]
                  var z: Double = zd
                  while (idx < len) {
                    z = sf(z, indexed.doubleAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  val ss = s.asInstanceOf[C[Double]]
                  val sf = f.asInstanceOf[(Z, Double) => Z]
                  var z  = zero
                  while (idx < len) {
                    z = sf(z, indexed.doubleAt(ss, idx))
                    idx += 1
                  }
                  z
              }
            case _: RegisterType.Char.type =>
              val ss = s.asInstanceOf[C[Char]]
              val sf = f.asInstanceOf[(Z, Char) => Z]
              var z  = zero
              while (idx < len) {
                z = sf(z, indexed.charAt(ss, idx))
                idx += 1
              }
              z
            case _ =>
              var z = zero
              while (idx < len) {
                z = f(z, indexed.objectAt(s, idx))
                idx += 1
              }
              z
          }
        case _ =>
          val it = deconstructor.deconstruct(s)
          var z  = zero
          while (it.hasNext) z = f(z, it.next())
          z
      }
    }

    def modify(s: C[A], f: A => A)(implicit F: HasBinding[F]): C[A] = {
      val constructor   = F.seqConstructor(seq.seqBinding)
      val deconstructor = F.seqDeconstructor(seq.seqBinding)
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[C] =>
          val len = indexed.length(s)
          indexed.elementType(s) match {
            case _: RegisterType.Boolean.type =>
              val builder = constructor.newBooleanBuilder(len)
              val ss      = s.asInstanceOf[C[Boolean]]
              val sf      = f.asInstanceOf[Boolean => Boolean]
              var idx     = 0
              while (idx < len) {
                constructor.addBoolean(builder, sf(indexed.booleanAt(ss, idx)))
                idx += 1
              }
              constructor.resultBoolean(builder).asInstanceOf[C[A]]
            case _: RegisterType.Byte.type =>
              val builder = constructor.newByteBuilder(len)
              val ss      = s.asInstanceOf[C[Byte]]
              val sf      = f.asInstanceOf[Byte => Byte]
              var idx     = 0
              while (idx < len) {
                constructor.addByte(builder, sf(indexed.byteAt(ss, idx)))
                idx += 1
              }
              constructor.resultByte(builder).asInstanceOf[C[A]]
            case _: RegisterType.Short.type =>
              val builder = constructor.newShortBuilder(len)
              val ss      = s.asInstanceOf[C[Short]]
              val sf      = f.asInstanceOf[Short => Short]
              var idx     = 0
              while (idx < len) {
                constructor.addShort(builder, sf(indexed.shortAt(ss, idx)))
                idx += 1
              }
              constructor.resultShort(builder).asInstanceOf[C[A]]
            case _: RegisterType.Int.type =>
              val builder = constructor.newIntBuilder(len)
              val ss      = s.asInstanceOf[C[Int]]
              val sf      = f.asInstanceOf[Int => Int]
              var idx     = 0
              while (idx < len) {
                constructor.addInt(builder, sf(indexed.intAt(ss, idx)))
                idx += 1
              }
              constructor.resultInt(builder).asInstanceOf[C[A]]
            case _: RegisterType.Long.type =>
              val builder = constructor.newLongBuilder(len)
              val ss      = s.asInstanceOf[C[Long]]
              val sf      = f.asInstanceOf[Long => Long]
              var idx     = 0
              while (idx < len) {
                constructor.addLong(builder, sf(indexed.longAt(ss, idx)))
                idx += 1
              }
              constructor.resultLong(builder).asInstanceOf[C[A]]
            case _: RegisterType.Float.type =>
              val builder = constructor.newFloatBuilder(len)
              val ss      = s.asInstanceOf[C[Float]]
              val sf      = f.asInstanceOf[Float => Float]
              var idx     = 0
              while (idx < len) {
                constructor.addFloat(builder, sf(indexed.floatAt(ss, idx)))
                idx += 1
              }
              constructor.resultFloat(builder).asInstanceOf[C[A]]
            case _: RegisterType.Double.type =>
              val builder = constructor.newDoubleBuilder(len)
              val ss      = s.asInstanceOf[C[Double]]
              val sf      = f.asInstanceOf[Double => Double]
              var idx     = 0
              while (idx < len) {
                constructor.addDouble(builder, sf(indexed.doubleAt(ss, idx)))
                idx += 1
              }
              constructor.resultDouble(builder).asInstanceOf[C[A]]
            case _: RegisterType.Char.type =>
              val builder = constructor.newCharBuilder(len)
              val ss      = s.asInstanceOf[C[Char]]
              val sf      = f.asInstanceOf[Char => Char]
              var idx     = 0
              while (idx < len) {
                constructor.addChar(builder, sf(indexed.charAt(ss, idx)))
                idx += 1
              }
              constructor.resultChar(builder).asInstanceOf[C[A]]
            case _ =>
              val builder = constructor.newObjectBuilder[A](len)
              var idx     = 0
              while (idx < len) {
                constructor.addObject(builder, f(indexed.objectAt(s, idx)))
                idx += 1
              }
              constructor.resultObject(builder)
          }
        case _ =>
          val builder = constructor.newObjectBuilder[A]()
          val it      = deconstructor.deconstruct(s)
          while (it.hasNext) constructor.addObject(builder, f(it.next()))
          constructor.resultObject(builder)
      }
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): SeqValues[G, A, C] = new SeqValues(seq.refineBinding(f))

    override def hashCode: Int = seq.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: SeqValues[_, _, _] => other.seq.equals(seq)
      case _                         => false
    }
  }

  private[schema] case class MapKeys[F[_, _], Key, Value, M[_, _]](map: Reflect.Map[F, Key, Value, M])
      extends Traversal[F, M[Key, Value], Key]
      with Leaf[F, M[Key, Value], Key] {
    def structure: Reflect[F, M[Key, Value]] = map

    def focus: Reflect[F, Key] = map.key

    def fold[Z](s: M[Key, Value])(zero: Z, f: (Z, Key) => Z)(implicit F: HasBinding[F]): Z = {
      val deconstructor = map.mapDeconstructor
      val it            = deconstructor.deconstruct(s)
      var z             = zero
      while (it.hasNext) z = f(z, deconstructor.getKey(it.next()))
      z
    }

    def modify(s: M[Key, Value], f: Key => Key)(implicit F: HasBinding[F]): M[Key, Value] = {
      val deconstructor = map.mapDeconstructor
      val constructor   = map.mapConstructor
      val builder       = constructor.newObjectBuilder[Key, Value]()
      val it            = deconstructor.deconstruct(s)
      while (it.hasNext) {
        val next = it.next()
        constructor.addObject(builder, f(deconstructor.getKey(next)), deconstructor.getValue(next))
      }
      constructor.resultObject(builder)
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): MapKeys[G, Key, Value, M] = new MapKeys(map.refineBinding(f))

    override def hashCode: Int = map.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: MapKeys[_, _, _, _] => other.map.equals(map)
      case _                          => false
    }
  }

  private[schema] case class MapValues[F[_, _], Key, Value, M[_, _]](map: Reflect.Map[F, Key, Value, M])
      extends Traversal[F, M[Key, Value], Value]
      with Leaf[F, M[Key, Value], Value] {
    def structure: Reflect[F, M[Key, Value]] = map

    def focus: Reflect[F, Value] = map.value

    def fold[Z](s: M[Key, Value])(zero: Z, f: (Z, Value) => Z)(implicit F: HasBinding[F]): Z = {
      val deconstructor = map.mapDeconstructor
      val it            = deconstructor.deconstruct(s)
      var z             = zero
      while (it.hasNext) z = f(z, deconstructor.getValue(it.next()))
      z
    }

    def modify(s: M[Key, Value], f: Value => Value)(implicit F: HasBinding[F]): M[Key, Value] = {
      val deconstructor = map.mapDeconstructor
      val constructor   = F.mapConstructor(map.mapBinding)
      val builder       = constructor.newObjectBuilder[Key, Value]()
      val it            = deconstructor.deconstruct(s)
      while (it.hasNext) {
        val next = it.next()
        constructor.addObject(builder, deconstructor.getKey(next), f(deconstructor.getValue(next)))
      }
      constructor.resultObject(builder)
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): MapValues[G, Key, Value, M] =
      new MapValues(map.refineBinding(f))

    override def hashCode: Int = map.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: MapValues[_, _, _, _] => other.map.equals(map)
      case _                            => false
    }
  }

  private[schema] case class TraversalMixed[F[_, _], S, A](leafs: ArraySeq[Leaf[F, _, _]]) extends Traversal[F, S, A] {
    def structure: Reflect[F, S] = leafs(0).structure.asInstanceOf[Reflect[F, S]]

    def focus: Reflect[F, A] = leafs(leafs.length - 1).focus.asInstanceOf[Reflect[F, A]]

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z = {
      var g   = f.asInstanceOf[(Any, Any) => Any]
      var idx = leafs.length
      while (idx > 0) {
        idx -= 1
        val leaf = leafs(idx)
        val h    = g
        if (leaf.isInstanceOf[Lens.LensImpl[F, _, _]]) {
          val lens = leaf.asInstanceOf[Lens[F, Any, Any]]
          g = (z: Any, t: Any) => h(z, lens.get(t))
        } else if (leaf.isInstanceOf[Prism.PrismImpl[F, _, _, _]]) {
          val prism = leaf.asInstanceOf[Prism[F, Any, Any]]
          g = (z: Any, t: Any) =>
            prism.getOption(t) match {
              case Some(a) => h(z, a)
              case _       => z
            }
        } else {
          val traversal = leaf.asInstanceOf[Traversal[F, Any, Any]]
          g = (z: Any, t: Any) => traversal.fold(t)(z, h)
        }
      }
      g(zero, s).asInstanceOf[Z]
    }

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = {
      var g   = f.asInstanceOf[Any => Any]
      var idx = leafs.length
      while (idx > 0) {
        idx -= 1
        val leaf = leafs(idx).asInstanceOf[Leaf[F, Any, Any]]
        val h    = g
        g = (x: Any) => leaf.modify(x, h)
      }
      g(s).asInstanceOf[S]
    }

    def refineBinding[G[_, _]](f: RefineBinding[F, G]): Traversal[G, S, A] =
      new TraversalMixed(leafs.map(_.refineBinding(f).asInstanceOf[Leaf[G, _, _]]))

    private[schema] def linearized: ArraySeq[Leaf[F, _, _]] = leafs
  }
}
