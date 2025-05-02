package zio.blocks.schema

import zio.blocks.schema.Lens.LensImpl
import zio.blocks.schema.Prism.PrismImpl
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._

/**
 * Represents an optic that provides a generic interface for traversing,
 * selecting, and updating data structures in a functional way. The `Optic`
 * trait is parameterized by a binding type constructor `F[_, _]`, the source
 * type `S`, and the focus type `A`.
 *
 * The optic can operate over various its types such as lens, prism, optional,
 * and traversal, and supports composition of them.
 *
 * @tparam F
 *   The type of the binding applied.
 * @tparam S
 *   The source type from which data is accessed or modified.
 * @tparam A
 *   The focus type or target type of this optic.
 */
sealed trait Optic[F[_, _], S, A] { self =>
  def source: Reflect[F, S]

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

  def toDynamic: DynamicOptic

  def transform[G[_, _]](f: ReflectTransformer[F, G]): Lazy[Optic[G, S, A]]

  def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Optic[G, S, A]]

  def noBinding: Optic[NoBinding, S, A] = transform(DynamicOptic.root, ReflectTransformer.noBinding()).force

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

  override def hashCode: Int = java.util.Arrays.hashCode(leafs.asInstanceOf[Array[AnyRef]])

  override def equals(obj: Any): Boolean = obj match {
    case other: Optic[_, _, _] =>
      java.util.Arrays.equals(other.leafs.asInstanceOf[Array[AnyRef]], leafs.asInstanceOf[Array[AnyRef]])
    case _ => false
  }

  private[schema] def leafs: Array[Leaf[F, _, _]]
}

object Optic {
  type Bound[S, A] = Optic[Binding, S, A]
}

private[schema] sealed trait Leaf[F[_, _], S, A] extends Optic[F, S, A] {
  override private[schema] def leafs: Array[Leaf[F, _, _]] = Array(this)
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

  override def transform[G[_, _]](f: ReflectTransformer[F, G]): Lazy[Lens[G, S, A]] = transform(DynamicOptic.root, f)

  override def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Lens[G, S, A]]

  override def noBinding: Lens[NoBinding, S, A] =
    transform[NoBinding](DynamicOptic.root, ReflectTransformer.noBinding()).force
}

object Lens {
  type Bound[S, A] = Lens[Binding, S, A]

  def apply[F[_, _], S, A](source: Reflect.Record[F, S], focusTerm: Term[F, S, A]): Lens[F, S, A] = {
    require((source ne null) && (focusTerm ne null))
    new LensImpl(Array(source), Array(focusTerm))
  }

  def apply[F[_, _], S, T, A](first: Lens[F, S, T], second: Lens[F, T, A]): Lens[F, S, A] = {
    require((first ne null) && (second ne null))
    val lens1 = first.asInstanceOf[LensImpl[F, _, _]]
    val lens2 = second.asInstanceOf[LensImpl[F, _, _]]
    new LensImpl(lens1.sources ++ lens2.sources, lens1.focusTerms ++ lens2.focusTerms)
  }

  private[schema] case class LensImpl[F[_, _], S, A](
    sources: Array[Reflect.Record[F, _]],
    focusTerms: Array[Term[F, _, _]]
  ) extends Lens[F, S, A]
      with Leaf[F, S, A] {
    private[this] var bindings: Array[OpticBinding] = null
    private[this] var usedRegisters: RegisterOffset = RegisterOffset.Zero

    private[this] def init(implicit F: HasBinding[F]): Unit = {
      var offset   = RegisterOffset.Zero
      val len      = sources.length
      val bindings = new Array[OpticBinding](len)
      var idx      = 0
      while (idx < len) {
        val source = sources(idx)
        bindings(idx) = new OpticBinding(
          matcher = null,
          deconstructor = F.deconstructor(source.recordBinding).asInstanceOf[Deconstructor[Any]],
          constructor = F.constructor(source.recordBinding).asInstanceOf[Constructor[Any]],
          register = source
            .registers(source.fields.indexWhere {
              val focusTermName = focusTerms(idx).name
              x => x.name == focusTermName
            })
            .asInstanceOf[Register[Any]],
          offset = offset
        )
        offset = RegisterOffset.add(offset, source.usedRegisters)
        idx += 1
      }
      this.usedRegisters = offset
      this.bindings = bindings
    }

    override def get(s: S)(implicit F: HasBinding[F]): A = {
      if (bindings eq null) init
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
      if (bindings eq null) init
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
      if (bindings eq null) init
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

    override lazy val toDynamic: DynamicOptic =
      DynamicOptic(focusTerms.map(focusTerm => DynamicOptic.Node.Field(focusTerm.name)).toVector)

    override def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Lens[G, S, A]] =
      for {
        sources    <- Lazy.collectAll(sources.map(_.transform(path, f)))
        focusTerms <- Lazy.collectAll(focusTerms.map(_.transform(path, Term.Type.Record, f)))
      } yield new LensImpl(sources, focusTerms)

    override def source: Reflect[F, S] = sources(0).asInstanceOf[Reflect[F, S]]

    override def focus: Reflect[F, A] = focusTerms(focusTerms.length - 1).value.asInstanceOf[Reflect[F, A]]

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: LensImpl[_, _, _] =>
        java.util.Arrays.equals(other.sources.asInstanceOf[Array[AnyRef]], sources.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.focusTerms.asInstanceOf[Array[AnyRef]], focusTerms.asInstanceOf[Array[AnyRef]])
      case _ => false
    }
  }
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

  override def transform[G[_, _]](f: ReflectTransformer[F, G]): Lazy[Prism[G, S, A]] = transform(DynamicOptic.root, f)

  override def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Prism[G, S, A]]

  override def noBinding: Prism[NoBinding, S, A] = transform(DynamicOptic.root, ReflectTransformer.noBinding()).force
}

object Prism {
  type Bound[S, A <: S] = Prism[Binding, S, A]

  def apply[F[_, _], S, A <: S](source: Reflect.Variant[F, S], focusTerm: Term[F, S, A]): Prism[F, S, A] = {
    require((source ne null) && (focusTerm ne null))
    new PrismImpl(Array(source), Array(focusTerm))
  }

  def apply[F[_, _], S, T <: S, A <: T](first: Prism[F, S, T], second: Prism[F, T, A]): Prism[F, S, A] = {
    require((first ne null) && (second ne null))
    val prism1 = first.asInstanceOf[PrismImpl[F, _, _]]
    val prism2 = second.asInstanceOf[PrismImpl[F, _, _]]
    new PrismImpl(prism1.sources ++ prism2.sources, prism1.focusTerms ++ prism2.focusTerms)
  }

  private[schema] case class PrismImpl[F[_, _], S, A <: S](
    sources: Array[Reflect.Variant[F, _]],
    focusTerms: Array[Term[F, _, _]]
  ) extends Prism[F, S, A]
      with Leaf[F, S, A] {
    private[this] var matchers: Array[Matcher[Any]] = null

    private def init(implicit F: HasBinding[F]): Unit = {
      val len      = sources.length
      val matchers = new Array[Matcher[Any]](len)
      var idx      = 0
      while (idx < len) {
        val source    = sources(idx)
        val focusTerm = focusTerms(idx)
        matchers(idx) = F
          .matchers(source.variantBinding)
          .apply(source.cases.indexWhere {
            val name = focusTerm.name
            x => x.name == name
          })
        idx += 1
      }
      this.matchers = matchers
    }

    def source: Reflect[F, S] = sources(0).asInstanceOf[Reflect[F, S]]

    def focus: Reflect[F, A] = focusTerms(focusTerms.length - 1).value.asInstanceOf[Reflect[F, A]]

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = {
      if (matchers eq null) init
      val len    = matchers.length
      var x: Any = s
      var idx    = 0
      while (idx < len) {
        x = matchers(idx).downcastOrNull(x)
        if (x == null) return None
        idx += 1
      }
      new Some(x.asInstanceOf[A])
    }

    override lazy val toDynamic: DynamicOptic =
      DynamicOptic(focusTerms.map(focusTerm => DynamicOptic.Node.Case(focusTerm.name)).toVector)

    def reverseGet(a: A): S = a

    def replace(s: S, a: A)(implicit F: HasBinding[F]): S = {
      if (matchers eq null) init
      val len    = matchers.length
      var x: Any = s
      var idx    = 0
      while (idx < len) {
        x = matchers(idx).downcastOrNull(x)
        if (x == null) return s
        idx += 1
      }
      a
    }

    def replaceOption(s: S, a: A)(implicit F: HasBinding[F]): Option[S] = {
      if (matchers eq null) init
      val len    = matchers.length
      var x: Any = s
      var idx    = 0
      while (idx < len) {
        x = matchers(idx).downcastOrNull(x)
        if (x == null) return None
        idx += 1
      }
      new Some(a)
    }

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = {
      if (matchers eq null) init
      val len    = matchers.length
      var x: Any = s
      var idx    = 0
      while (idx < len) {
        x = matchers(idx).downcastOrNull(x)
        if (x == null) return s
        idx += 1
      }
      f(x.asInstanceOf[A])
    }

    override def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Prism[G, S, A]] =
      for {
        sources    <- Lazy.collectAll(sources.map(_.transform(path, f)))
        focusTerms <- Lazy.collectAll(focusTerms.map(_.transform(path, Term.Type.Variant, f)))
      } yield new PrismImpl(sources, focusTerms)

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: PrismImpl[_, _, _] =>
        java.util.Arrays.equals(other.sources.asInstanceOf[Array[AnyRef]], sources.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.focusTerms.asInstanceOf[Array[AnyRef]], focusTerms.asInstanceOf[Array[AnyRef]])
      case _ => false
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

  override def transform[G[_, _]](f: ReflectTransformer[F, G]): Lazy[Optional[G, S, A]] =
    transform(DynamicOptic.root, f)

  override def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Optional[G, S, A]]

  override def noBinding: Optional[NoBinding, S, A] = transform(DynamicOptic.root, ReflectTransformer.noBinding()).force
}

object Optional {
  type Bound[S, A] = Optional[Binding, S, A]

  def apply[F[_, _], S, T, A](first: Optional[F, S, T], second: Lens[F, T, A]): Optional[F, S, A] = {
    val optional1 = first.asInstanceOf[OptionalImpl[F, _, _]]
    val lens2     = second.asInstanceOf[Lens.LensImpl[F, _, _]]
    new OptionalImpl(optional1.sources ++ lens2.sources, optional1.focusTerms ++ lens2.focusTerms)
  }

  def apply[F[_, _], S, T, A <: T](first: Optional[F, S, T], second: Prism[F, T, A]): Optional[F, S, A] = {
    val optional1 = first.asInstanceOf[OptionalImpl[F, _, _]]
    val prism2    = second.asInstanceOf[Prism.PrismImpl[F, _, _]]
    new OptionalImpl(optional1.sources ++ prism2.sources, optional1.focusTerms ++ prism2.focusTerms)
  }

  def apply[F[_, _], S, T, A](first: Optional[F, S, T], second: Optional[F, T, A]): Optional[F, S, A] = {
    val optional1 = first.asInstanceOf[OptionalImpl[F, _, _]]
    val optional2 = second.asInstanceOf[OptionalImpl[F, _, _]]
    new OptionalImpl(optional1.sources ++ optional2.sources, optional1.focusTerms ++ optional2.focusTerms)
  }

  def apply[F[_, _], S, T, A <: T](first: Lens[F, S, T], second: Prism[F, T, A]): Optional[F, S, A] = {
    val lens1  = first.asInstanceOf[Lens.LensImpl[F, _, _]]
    val prism2 = second.asInstanceOf[Prism.PrismImpl[F, _, _]]
    new OptionalImpl(lens1.sources ++ prism2.sources, lens1.focusTerms ++ prism2.focusTerms)
  }

  def apply[F[_, _], S, T, A](first: Lens[F, S, T], second: Optional[F, T, A]): Optional[F, S, A] = {
    val lens1     = first.asInstanceOf[Lens.LensImpl[F, _, _]]
    val optional2 = second.asInstanceOf[OptionalImpl[F, _, _]]
    new OptionalImpl(lens1.sources ++ optional2.sources, lens1.focusTerms ++ optional2.focusTerms)
  }

  def apply[F[_, _], S, T <: S, A](first: Prism[F, S, T], second: Lens[F, T, A]): Optional[F, S, A] = {
    val prism1 = first.asInstanceOf[Prism.PrismImpl[F, _, _]]
    val lens2  = second.asInstanceOf[Lens.LensImpl[F, _, _]]
    new OptionalImpl(prism1.sources ++ lens2.sources, prism1.focusTerms ++ lens2.focusTerms)
  }
  def apply[F[_, _], S, T <: S, A](first: Prism[F, S, T], second: Optional[F, T, A]): Optional[F, S, A] = {
    val prism1    = first.asInstanceOf[Prism.PrismImpl[F, _, _]]
    val optional2 = second.asInstanceOf[OptionalImpl[F, _, _]]
    new OptionalImpl(prism1.sources ++ optional2.sources, prism1.focusTerms ++ optional2.focusTerms)
  }

  private[schema] case class OptionalImpl[F[_, _], S, A](
    sources: Array[Reflect[F, _]],
    focusTerms: Array[Term[F, _, _]]
  ) extends Optional[F, S, A]
      with Leaf[F, S, A] {
    private[this] var bindings: Array[OpticBinding] = null
    private[this] var usedRegisters: RegisterOffset = RegisterOffset.Zero

    private[this] def init(implicit F: HasBinding[F]): Unit = {
      val len      = sources.length
      val bindings = new Array[OpticBinding](len)
      var offset   = RegisterOffset.Zero
      var idx      = 0
      while (idx < len) {
        val source    = sources(idx)
        val focusTerm = focusTerms(idx)
        if (source.isInstanceOf[Reflect.Record[F, _]]) {
          val record = source.asInstanceOf[Reflect.Record[F, _]]
          bindings(idx) = new OpticBinding(
            deconstructor = F.deconstructor(record.recordBinding).asInstanceOf[Deconstructor[Any]],
            constructor = F.constructor(record.recordBinding).asInstanceOf[Constructor[Any]],
            register = record
              .registers(record.fields.indexWhere {
                val focusTermName = focusTerm.name
                x => x.name == focusTermName
              })
              .asInstanceOf[Register[Any]],
            offset = offset
          )
          offset = RegisterOffset.add(offset, record.usedRegisters)

        } else {
          val variant = source.asInstanceOf[Reflect.Variant[F, _]]
          bindings(idx) = OpticBinding(
            matcher = F
              .matchers(variant.variantBinding)
              .apply(variant.cases.indexWhere {
                val name = focusTerm.name
                x => x.name == name
              })
          )
        }
        idx += 1
      }
      this.usedRegisters = offset
      this.bindings = bindings
    }

    def source: Reflect[F, S] = sources(0).asInstanceOf[Reflect[F, S]]

    def focus: Reflect[F, A] = focusTerms(focusTerms.length - 1).value.asInstanceOf[Reflect[F, A]]

    def getOption(s: S)(implicit F: HasBinding[F]): Option[A] = {
      if (bindings eq null) init
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var idx       = 0
      while (idx < len) {
        val binding = bindings(idx)
        if (binding.matcher eq null) {
          val offset = binding.offset
          binding.deconstructor.deconstruct(registers, offset, x)
          x = binding.register.get(registers, offset)
        } else {
          x = binding.matcher.downcastOrNull(x)
          if (x == null) return None
        }
        idx += 1
      }
      new Some(x.asInstanceOf[A])
    }

    override lazy val toDynamic: DynamicOptic = DynamicOptic {
      val nodes = Vector.newBuilder[DynamicOptic.Node]
      val len   = sources.length
      var idx   = 0
      while (idx < len) {
        val source        = sources(idx)
        val focusTermName = focusTerms(idx).name
        nodes.addOne {
          if (source.isInstanceOf[Reflect.Record[F, _]]) DynamicOptic.Node.Field(focusTermName)
          else DynamicOptic.Node.Case(focusTermName)
        }
        idx += 1
      }
      nodes.result()
    }

    def replace(s: S, a: A)(implicit F: HasBinding[F]): S = {
      if (bindings eq null) init
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var idx       = 0
      while (idx < len) {
        val binding = bindings(idx)
        if (binding.matcher eq null) {
          val offset = binding.offset
          binding.deconstructor.deconstruct(registers, offset, x)
          if (idx < len) x = binding.register.get(registers, offset)
        } else {
          x = binding.matcher.downcastOrNull(x)
          if (x == null) return s
        }
        idx += 1
      }
      x = a
      while (idx > 0) {
        idx -= 1
        val binding = bindings(idx)
        if (binding.matcher eq null) {
          val offset = binding.offset
          binding.register.set(registers, offset, x)
          x = binding.constructor.construct(registers, offset)
        }
      }
      x.asInstanceOf[S]
    }

    def replaceOption(s: S, a: A)(implicit F: HasBinding[F]): Option[S] = {
      if (bindings eq null) init
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var idx       = 0
      while (idx < len) {
        val binding = bindings(idx)
        if (binding.matcher eq null) {
          val offset = binding.offset
          binding.deconstructor.deconstruct(registers, offset, x)
          if (idx < len) x = binding.register.get(registers, offset)
        } else {
          x = binding.matcher.downcastOrNull(x)
          if (x == null) return None
        }
        idx += 1
      }
      x = a
      while (idx > 0) {
        idx -= 1
        val binding = bindings(idx)
        if (binding.matcher eq null) {
          val offset = binding.offset
          binding.register.set(registers, offset, x)
          x = binding.constructor.construct(registers, offset)
        }
      }
      new Some(x.asInstanceOf[S])
    }

    def modify(s: S, f: A => A)(implicit F: HasBinding[F]): S = {
      if (bindings eq null) init
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var idx       = 0
      while (idx < len) {
        val binding = bindings(idx)
        if (binding.matcher eq null) {
          val offset = binding.offset
          binding.deconstructor.deconstruct(registers, offset, x)
          x = binding.register.get(registers, offset)
        } else {
          x = binding.matcher.downcastOrNull(x)
          if (x == null) return s
        }
        idx += 1
      }
      x = f(x.asInstanceOf[A])
      while (idx > 0) {
        idx -= 1
        val binding = bindings(idx)
        if (binding.matcher eq null) {
          val offset = binding.offset
          binding.register.set(registers, offset, x)
          x = binding.constructor.construct(registers, offset)
        }
      }
      x.asInstanceOf[S]
    }

    override def transform[G[_, _]](f: ReflectTransformer[F, G]): Lazy[Optional[G, S, A]] =
      transform(DynamicOptic.root, f)

    override def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Optional[G, S, A]] =
      for {
        sources <- Lazy.collectAll(sources.map(_.transform(path, f)))
        focusTerms <- Lazy.collectAll(sources.zip(focusTerms).map { case (source, term) =>
                        term.transform(path, if (source.isRecord) Term.Type.Record else Term.Type.Variant, f)
                      })
      } yield new OptionalImpl(sources, focusTerms)

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: OptionalImpl[_, _, _] =>
        java.util.Arrays.equals(other.sources.asInstanceOf[Array[AnyRef]], sources.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.focusTerms.asInstanceOf[Array[AnyRef]], focusTerms.asInstanceOf[Array[AnyRef]])
      case _ => false
    }
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

  override def transform[G[_, _]](f: ReflectTransformer[F, G]): Lazy[Traversal[G, S, A]] =
    transform(DynamicOptic.root, f)

  override def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Traversal[G, S, A]]

  override def noBinding: Traversal[NoBinding, S, A] =
    transform(DynamicOptic.root, ReflectTransformer.noBinding()).force
}

object Traversal {
  type Bound[S, A] = Traversal[Binding, S, A]

  def apply[F[_, _], S, T, A](first: Traversal[F, S, T], second: Traversal[F, T, A]): Traversal[F, S, A] =
    apply(first.leafs, second.leafs)

  def apply[F[_, _], S, T, A](first: Traversal[F, S, T], second: Lens[F, T, A]): Traversal[F, S, A] =
    apply(first.leafs, second.leafs)

  def apply[F[_, _], S, T, A <: T](first: Traversal[F, S, T], second: Prism[F, T, A]): Traversal[F, S, A] =
    apply(first.leafs, second.leafs)

  def apply[F[_, _], S, T, A](first: Traversal[F, S, T], second: Optional[F, T, A]): Traversal[F, S, A] =
    apply(first.leafs, second.leafs)

  def apply[F[_, _], S, T, A](first: Lens[F, S, T], second: Traversal[F, T, A]): Traversal[F, S, A] =
    apply(first.leafs, second.leafs)

  def apply[F[_, _], S, T <: S, A](first: Prism[F, S, T], second: Traversal[F, T, A]): Traversal[F, S, A] =
    apply(first.leafs, second.leafs)

  def apply[F[_, _], S, T, A](first: Optional[F, S, T], second: Traversal[F, T, A]): Traversal[F, S, A] =
    apply(first.leafs, second.leafs)

  private[this] def apply[F[_, _], S, A](
    leafs1: Array[Leaf[F, _, _]],
    leafs2: Array[Leaf[F, _, _]]
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

  private[schema] case class SeqValues[F[_, _], A, C[_]](source: Reflect.Sequence[F, A, C])
      extends Traversal[F, C[A], A]
      with Leaf[F, C[A], A] {
    def focus: Reflect[F, A] = source.element

    def fold[Z](s: C[A])(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z = {
      val deconstructor = F.seqDeconstructor(source.seqBinding)
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
      val constructor   = F.seqConstructor(source.seqBinding)
      val deconstructor = F.seqDeconstructor(source.seqBinding)
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

    override lazy val toDynamic: DynamicOptic = DynamicOptic(Vector(DynamicOptic.Node.Elements))

    override def transform[G[_, _]](f: ReflectTransformer[F, G]): Lazy[Traversal[G, C[A], A]] =
      transform(DynamicOptic.root, f)

    override def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[SeqValues[G, A, C]] =
      for {
        source <- source.transform(path, f)
      } yield new SeqValues(source)

    override def hashCode: Int = source.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: SeqValues[_, _, _] => other.source.equals(source)
      case _                         => false
    }
  }

  private[schema] case class MapKeys[F[_, _], Key, Value, M[_, _]](source: Reflect.Map[F, Key, Value, M])
      extends Traversal[F, M[Key, Value], Key]
      with Leaf[F, M[Key, Value], Key] {
    def focus: Reflect[F, Key] = source.key

    def fold[Z](s: M[Key, Value])(zero: Z, f: (Z, Key) => Z)(implicit F: HasBinding[F]): Z = {
      val deconstructor = F.mapDeconstructor(source.mapBinding)
      val it            = deconstructor.deconstruct(s)
      var z             = zero
      while (it.hasNext) z = f(z, deconstructor.getKey(it.next()))
      z
    }

    def modify(s: M[Key, Value], f: Key => Key)(implicit F: HasBinding[F]): M[Key, Value] = {
      val deconstructor = F.mapDeconstructor(source.mapBinding)
      val constructor   = F.mapConstructor(source.mapBinding)
      val builder       = constructor.newObjectBuilder[Key, Value]()
      val it            = deconstructor.deconstruct(s)
      while (it.hasNext) {
        val next = it.next()
        constructor.addObject(builder, f(deconstructor.getKey(next)), deconstructor.getValue(next))
      }
      constructor.resultObject(builder)
    }

    override lazy val toDynamic: DynamicOptic = DynamicOptic(Vector(DynamicOptic.Node.MapKeys))

    override def transform[G[_, _]](f: ReflectTransformer[F, G]): Lazy[Traversal[G, M[Key, Value], Key]] =
      transform(DynamicOptic.root, f)

    override def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[MapKeys[G, Key, Value, M]] =
      for {
        source <- source.transform(path, f)
      } yield new MapKeys(source)

    override def hashCode: Int = source.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: MapKeys[_, _, _, _] => other.source.equals(source)
      case _                          => false
    }
  }

  private[schema] case class MapValues[F[_, _], Key, Value, M[_, _]](source: Reflect.Map[F, Key, Value, M])
      extends Traversal[F, M[Key, Value], Value]
      with Leaf[F, M[Key, Value], Value] {
    def focus: Reflect[F, Value] = source.value

    def fold[Z](s: M[Key, Value])(zero: Z, f: (Z, Value) => Z)(implicit F: HasBinding[F]): Z = {
      val deconstructor = F.mapDeconstructor(source.mapBinding)
      val it            = deconstructor.deconstruct(s)
      var z             = zero
      while (it.hasNext) z = f(z, deconstructor.getValue(it.next()))
      z
    }

    def modify(s: M[Key, Value], f: Value => Value)(implicit F: HasBinding[F]): M[Key, Value] = {
      val deconstructor = F.mapDeconstructor(source.mapBinding)
      val constructor   = F.mapConstructor(source.mapBinding)
      val builder       = constructor.newObjectBuilder[Key, Value]()
      val it            = deconstructor.deconstruct(s)
      while (it.hasNext) {
        val next = it.next()
        constructor.addObject(builder, deconstructor.getKey(next), f(deconstructor.getValue(next)))
      }
      constructor.resultObject(builder)
    }

    override lazy val toDynamic: DynamicOptic = DynamicOptic(Vector(DynamicOptic.Node.MapValues))

    override def transform[G[_, _]](f: ReflectTransformer[F, G]): Lazy[Traversal[G, M[Key, Value], Value]] =
      transform(DynamicOptic.root, f)

    override def transform[G[_, _]](
      path: DynamicOptic,
      f: ReflectTransformer[F, G]
    ): Lazy[MapValues[G, Key, Value, M]] =
      for {
        source <- source.transform(path, f)
      } yield new MapValues(source)

    override def hashCode: Int = source.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: MapValues[_, _, _, _] => other.source.equals(source)
      case _                            => false
    }
  }

  private[schema] case class TraversalMixed[F[_, _], S, A](leafs: Array[Leaf[F, _, _]]) extends Traversal[F, S, A] {
    def source: Reflect[F, S] = leafs(0).source.asInstanceOf[Reflect[F, S]]

    def focus: Reflect[F, A] = leafs(leafs.length - 1).focus.asInstanceOf[Reflect[F, A]]

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z)(implicit F: HasBinding[F]): Z = {
      var g   = f.asInstanceOf[(Any, Any) => Any]
      var idx = leafs.length
      while (idx > 0) {
        idx -= 1
        val leaf = leafs(idx)
        val h    = g
        if (leaf.isInstanceOf[Lens.LensImpl[F, _, _]]) {
          val lens = leaf.asInstanceOf[Lens.LensImpl[F, Any, Any]]
          g = (z: Any, t: Any) => h(z, lens.get(t))
        } else if (leaf.isInstanceOf[Prism.PrismImpl[F, _, _]]) {
          val prism = leaf.asInstanceOf[Prism.PrismImpl[F, Any, Any]]
          g = (z: Any, t: Any) =>
            prism.getOption(t) match {
              case Some(a) => h(z, a)
              case _       => z
            }
        } else if (leaf.isInstanceOf[Optional.OptionalImpl[F, _, _]]) {
          val optional = leaf.asInstanceOf[Optional.OptionalImpl[F, Any, Any]]
          g = (z: Any, t: Any) =>
            optional.getOption(t) match {
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

    override lazy val toDynamic: DynamicOptic = DynamicOptic(leafs.flatMap(_.toDynamic.nodes).toVector)

    override def transform[G[_, _]](f: ReflectTransformer[F, G]): Lazy[Traversal[G, S, A]] =
      transform(DynamicOptic.root, f)

    override def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Traversal[G, S, A]] =
      for {
        leafs <- Lazy.collectAll(leafs.map(_.transform(path, f)))
      } yield new TraversalMixed(leafs.map(_.asInstanceOf[Leaf[G, _, _]]))
  }
}

private[schema] case class OpticBinding(
  offset: RegisterOffset = RegisterOffset.Zero,
  deconstructor: Deconstructor[Any] = null,
  constructor: Constructor[Any] = null,
  register: Register[Any] = null,
  matcher: Matcher[Any] = null
)
