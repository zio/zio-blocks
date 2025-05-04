package zio.blocks.schema

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._

/**
 * Represents an optic that provides a generic interface for traversing,
 * selecting, and updating data structures in a functional way. The `Optic`
 * trait is parameterized by the source type `S`, and the focus type `A`.
 *
 * The optic can operate over various its types such as lens, prism, optional,
 * and traversal, and supports composition of them.
 *
 * @tparam S
 *   The source type from which data is accessed or modified.
 * @tparam A
 *   The focus type or target type of this optic.
 */
sealed trait Optic[S, A] { self =>
  def source: Reflect.Bound[S]

  def focus: Reflect.Bound[A]

  // Compose this optic with a lens:
  def apply[B](that: Lens[A, B]): Optic[S, B]

  // Compose this optic with a prism:
  def apply[B <: A](that: Prism[A, B]): Optic[S, B]

  // Compose this optic with an optional:
  def apply[B](that: Optional[A, B]): Optic[S, B]

  // Compose this optic with a traversal:
  def apply[B](that: Traversal[A, B]): Traversal[S, B]

  def check(s: S): Option[OpticCheck]

  def modify(s: S, f: A => A): S

  def modifyOption(s: S, f: A => A): Option[S] = {
    var modified = false
    var wrapper = (a: A) => {
      modified = true
      f(a)
    }

    val result = modify(s, wrapper)

    if (modified) Some(result)
    else None
  }

  def modifyOrFail(s: S, f: A => A): Either[OpticCheck, S] = {
    var modified = false
    var wrapper = (a: A) => {
      modified = true
      f(a)
    }

    val result = modify(s, wrapper)

    if (modified) Right(result)
    else Left(check(s).get)
  }

  def toDynamic: DynamicOptic

  final def listValues[B](implicit ev: A =:= List[B]): Traversal[S, B] = {
    import Reflect.Extractors.List

    val list = self.asEquivalent[List[B]]
    list.focus match {
      case List(element) => list(Traversal.listValues(element))
      case _             => sys.error("Expected List")
    }
  }

  final def vectorValues[B](implicit ev: A =:= Vector[B]): Traversal[S, B] = {
    import Reflect.Extractors.Vector

    val vector = self.asEquivalent[Vector[B]]
    vector.focus match {
      case Vector(element) => vector(Traversal.vectorValues(element))
      case _               => sys.error("Expected Vector")
    }
  }

  final def setValues[B](implicit ev: A =:= Set[B]): Traversal[S, B] = {
    import Reflect.Extractors.Set

    val set = self.asEquivalent[Set[B]]
    set.focus match {
      case Set(element) => set(Traversal.setValues(element))
      case _            => sys.error("Expected Set")
    }
  }

  final def arrayValues[B](implicit ev: A =:= Array[B]): Traversal[S, B] = {
    import Reflect.Extractors.Array

    val array = self.asEquivalent[Array[B]]
    array.focus match {
      case Array(element) => array(Traversal.arrayValues(element))
      case _              => sys.error("Expected Array")
    }
  }

  final def asEquivalent[B](implicit ev: A =:= B): Optic[S, B] = self.asInstanceOf[Optic[S, B]]

  override def hashCode: Int = java.util.Arrays.hashCode(leafs.asInstanceOf[Array[AnyRef]])

  override def equals(obj: Any): Boolean = obj match {
    case other: Optic[_, _] =>
      java.util.Arrays.equals(other.leafs.asInstanceOf[Array[AnyRef]], leafs.asInstanceOf[Array[AnyRef]])
    case _ => false
  }

  private[schema] def leafs: Array[Leaf[_, _]]
}

private[schema] sealed trait Leaf[S, A] extends Optic[S, A] {
  override private[schema] def leafs: Array[Leaf[_, _]] = Array(this)
}

sealed trait Lens[S, A] extends Optic[S, A] {
  def get(s: S): A

  def replace(s: S, a: A): S

  final def check(s: S): None.type = None

  // Compose this lens with a lens:
  override def apply[B](that: Lens[A, B]): Lens[S, B] = Lens(this, that)

  // Compose this lens with a prism:
  override def apply[B <: A](that: Prism[A, B]): Optional[S, B] = Optional(this, that)

  // Compose this lens with an optional:
  override def apply[B](that: Optional[A, B]): Optional[S, B] = Optional(this, that)

  // Compose this lens with a traversal:
  override def apply[B](that: Traversal[A, B]): Traversal[S, B] = Traversal(this, that)
}

object Lens {
  def apply[S, A](source: Reflect.Record.Bound[S], focusTerm: Term.Bound[S, A]): Lens[S, A] = {
    require((source ne null) && (focusTerm ne null))
    new LensImpl(Array(source), Array(focusTerm))
  }

  def apply[S, T, A](first: Lens[S, T], second: Lens[T, A]): Lens[S, A] = {
    require((first ne null) && (second ne null))
    val lens1 = first.asInstanceOf[LensImpl[_, _]]
    val lens2 = second.asInstanceOf[LensImpl[_, _]]
    new LensImpl(lens1.sources ++ lens2.sources, lens1.focusTerms ++ lens2.focusTerms)
  }

  private[schema] case class LensImpl[S, A](
    sources: Array[Reflect.Record.Bound[_]],
    focusTerms: Array[Term.Bound[_, _]]
  ) extends Lens[S, A]
      with Leaf[S, A] {
    private[this] var bindings: Array[OpticBinding] = null
    private[this] var usedRegisters: RegisterOffset = RegisterOffset.Zero

    {
      var offset   = RegisterOffset.Zero
      val len      = sources.length
      val bindings = new Array[OpticBinding](len)
      var idx      = 0
      while (idx < len) {
        val source = sources(idx)
        bindings(idx) = new OpticBinding(
          matcher = null,
          deconstructor = source.deconstructor.asInstanceOf[Deconstructor[Any]],
          constructor = source.constructor.asInstanceOf[Constructor[Any]],
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

    override def get(s: S): A = {
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

    override def replace(s: S, a: A): S = {
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

    def modify(s: S, f: A => A): S = {
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
    override def source: Reflect.Bound[S] = sources(0).asInstanceOf[Reflect.Bound[S]]

    override def focus: Reflect.Bound[A] = focusTerms(focusTerms.length - 1).value.asInstanceOf[Reflect.Bound[A]]

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: LensImpl[_, _] =>
        java.util.Arrays.equals(other.sources.asInstanceOf[Array[AnyRef]], sources.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.focusTerms.asInstanceOf[Array[AnyRef]], focusTerms.asInstanceOf[Array[AnyRef]])
      case _ => false
    }
  }
}

sealed trait Prism[S, A <: S] extends Optic[S, A] {
  def getOption(s: S): Option[A]

  def getOrFail(s: S): Either[OpticCheck, A] =
    getOption(s) match {
      case Some(a) => Right(a)
      case None    => Left(check(s).get)
    }

  def reverseGet(a: A): S

  def replace(s: S, a: A): S

  def replaceOption(s: S, a: A): Option[S]

  def replaceOrFail(s: S, a: A): Either[OpticCheck, S] =
    replaceOption(s, a) match {
      case Some(s) => Right(s)
      case None    => Left(check(s).get)
    }

  // Compose this prism with a prism:
  override def apply[B <: A](that: Prism[A, B]): Prism[S, B] = Prism(this, that)

  // Compose this prism with a lens:
  override def apply[B](that: Lens[A, B]): Optional[S, B] = Optional(this, that)

  // Compose this prism with an optional:
  override def apply[B](that: Optional[A, B]): Optional[S, B] = Optional(this, that)

  // Compose this prism with a traversal:
  override def apply[B](that: Traversal[A, B]): Traversal[S, B] = Traversal(this, that)
}

object Prism {
  def apply[S, A <: S](source: Reflect.Variant.Bound[S], focusTerm: Term.Bound[S, A]): Prism[S, A] = {
    require((source ne null) && (focusTerm ne null))
    new PrismImpl(Array(source), Array(focusTerm))
  }

  def apply[S, T <: S, A <: T](first: Prism[S, T], second: Prism[T, A]): Prism[S, A] = {
    require((first ne null) && (second ne null))
    val prism1 = first.asInstanceOf[PrismImpl[_, _]]
    val prism2 = second.asInstanceOf[PrismImpl[_, _]]
    new PrismImpl(prism1.sources ++ prism2.sources, prism1.focusTerms ++ prism2.focusTerms)
  }

  private[schema] case class PrismImpl[S, A <: S](
    sources: Array[Reflect.Variant.Bound[_]],
    focusTerms: Array[Term.Bound[_, _]]
  ) extends Prism[S, A]
      with Leaf[S, A] {
    private[this] var matchers: Array[Matcher[Any]]             = null
    private[this] var discriminators: Array[Discriminator[Any]] = null

    {
      val len            = sources.length
      val matchers       = new Array[Matcher[Any]](len)
      val discriminators = new Array[Discriminator[Any]](len)
      var idx            = 0
      while (idx < len) {
        val source    = sources(idx)
        val focusTerm = focusTerms(idx)
        matchers(idx) = source.matchers
          .apply(source.cases.indexWhere {
            val name = focusTerm.name
            x => x.name == name
          })
        discriminators(idx) = source.discriminator.asInstanceOf[Discriminator[Any]]
        idx += 1
      }
      this.discriminators = discriminators
      this.matchers = matchers
    }

    def check(s: S): Option[OpticCheck] = {
      val len    = matchers.length
      var x: Any = s
      var idx    = 0
      while (idx < len) {
        val lastX = x
        x = matchers(idx).downcastOrNull(x)
        if (x == null) {
          val expectedIdx  = discriminators(idx).discriminate(lastX)
          val expectedCase = sources(idx).cases(expectedIdx).name
          return Some(
            OpticCheck.unexpectedCase(
              focusTerms(idx).name,
              expectedCase,
              toDynamic,
              DynamicOptic(focusTerms.take(idx + 1).map(term => DynamicOptic.Node.Case(term.name)).toVector),
              lastX
            )
          )
        }
        idx += 1
      }
      None
    }

    def source: Reflect.Bound[S] = sources(0).asInstanceOf[Reflect.Bound[S]]

    def focus: Reflect.Bound[A] = focusTerms(focusTerms.length - 1).value.asInstanceOf[Reflect.Bound[A]]

    def getOption(s: S): Option[A] = {
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

    def replace(s: S, a: A): S = {
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

    def replaceOption(s: S, a: A): Option[S] = {
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

    def modify(s: S, f: A => A): S = {
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

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: PrismImpl[_, _] =>
        java.util.Arrays.equals(other.sources.asInstanceOf[Array[AnyRef]], sources.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.focusTerms.asInstanceOf[Array[AnyRef]], focusTerms.asInstanceOf[Array[AnyRef]])
      case _ => false
    }
  }
}

sealed trait Optional[S, A] extends Optic[S, A] {
  def getOption(s: S): Option[A]

  def getOrFail(s: S): Either[OpticCheck, A] =
    getOption(s) match {
      case Some(a) => Right(a)
      case None    => Left(check(s).get)
    }

  def replace(s: S, a: A): S

  def replaceOption(s: S, a: A): Option[S]

  def replaceOrFail(s: S, a: A): Either[OpticCheck, S] =
    replaceOption(s, a) match {
      case Some(s) => Right(s)
      case None    => Left(check(s).get)
    }

  // Compose this optional with a lens:
  override def apply[B](that: Lens[A, B]): Optional[S, B] = Optional(this, that)

  // Compose this optional with a prism:
  override def apply[B <: A](that: Prism[A, B]): Optional[S, B] = Optional(this, that)

  // Compose this optional with an optional:
  override def apply[B](that: Optional[A, B]): Optional[S, B] = Optional(this, that)

  // Compose this optional with a traversal:
  override def apply[B](that: Traversal[A, B]): Traversal[S, B] = Traversal(this, that)

}

object Optional {
  def apply[S, T, A](first: Optional[S, T], second: Lens[T, A]): Optional[S, A] = {
    val optional1 = first.asInstanceOf[OptionalImpl[_, _]]
    val lens2     = second.asInstanceOf[Lens.LensImpl[_, _]]
    new OptionalImpl(optional1.sources ++ lens2.sources, optional1.focusTerms ++ lens2.focusTerms)
  }

  def apply[S, T, A <: T](first: Optional[S, T], second: Prism[T, A]): Optional[S, A] = {
    val optional1 = first.asInstanceOf[OptionalImpl[_, _]]
    val prism2    = second.asInstanceOf[Prism.PrismImpl[_, _]]
    new OptionalImpl(optional1.sources ++ prism2.sources, optional1.focusTerms ++ prism2.focusTerms)
  }

  def apply[S, T, A](first: Optional[S, T], second: Optional[T, A]): Optional[S, A] = {
    val optional1 = first.asInstanceOf[OptionalImpl[_, _]]
    val optional2 = second.asInstanceOf[OptionalImpl[_, _]]
    new OptionalImpl(optional1.sources ++ optional2.sources, optional1.focusTerms ++ optional2.focusTerms)
  }

  def apply[S, T, A <: T](first: Lens[S, T], second: Prism[T, A]): Optional[S, A] = {
    val lens1  = first.asInstanceOf[Lens.LensImpl[_, _]]
    val prism2 = second.asInstanceOf[Prism.PrismImpl[_, _]]
    new OptionalImpl(lens1.sources ++ prism2.sources, lens1.focusTerms ++ prism2.focusTerms)
  }

  def apply[S, T, A](first: Lens[S, T], second: Optional[T, A]): Optional[S, A] = {
    val lens1     = first.asInstanceOf[Lens.LensImpl[_, _]]
    val optional2 = second.asInstanceOf[OptionalImpl[_, _]]
    new OptionalImpl(lens1.sources ++ optional2.sources, lens1.focusTerms ++ optional2.focusTerms)
  }

  def apply[S, T <: S, A](first: Prism[S, T], second: Lens[T, A]): Optional[S, A] = {
    val prism1 = first.asInstanceOf[Prism.PrismImpl[_, _]]
    val lens2  = second.asInstanceOf[Lens.LensImpl[_, _]]
    new OptionalImpl(prism1.sources ++ lens2.sources, prism1.focusTerms ++ lens2.focusTerms)
  }
  def apply[S, T <: S, A](first: Prism[S, T], second: Optional[T, A]): Optional[S, A] = {
    val prism1    = first.asInstanceOf[Prism.PrismImpl[_, _]]
    val optional2 = second.asInstanceOf[OptionalImpl[_, _]]
    new OptionalImpl(prism1.sources ++ optional2.sources, prism1.focusTerms ++ optional2.focusTerms)
  }

  private[schema] case class OptionalImpl[S, A](
    sources: Array[Reflect.Bound[_]],
    focusTerms: Array[Term.Bound[_, _]]
  ) extends Optional[S, A]
      with Leaf[S, A] {
    private[this] var bindings: Array[OpticBinding] = null
    private[this] var usedRegisters: RegisterOffset = RegisterOffset.Zero

    {
      val len      = sources.length
      val bindings = new Array[OpticBinding](len)
      var offset   = RegisterOffset.Zero
      var idx      = 0
      while (idx < len) {
        val source    = sources(idx)
        val focusTerm = focusTerms(idx)
        if (source.isInstanceOf[Reflect.Record.Bound[_]]) {
          val record = source.asInstanceOf[Reflect.Record.Bound[_]]
          bindings(idx) = new OpticBinding(
            deconstructor = record.deconstructor.asInstanceOf[Deconstructor[Any]],
            constructor = record.constructor.asInstanceOf[Constructor[Any]],
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
          val variant = source.asInstanceOf[Reflect.Variant.Bound[_]]
          bindings(idx) = OpticBinding(
            matcher = variant.matchers
              .apply(variant.cases.indexWhere {
                val name = focusTerm.name
                x => x.name == name
              }),
            discriminator = variant.discriminator.asInstanceOf[Discriminator[Any]]
          )
        }
        idx += 1
      }
      this.usedRegisters = offset
      this.bindings = bindings
    }

    def check(s: S): Option[OpticCheck] = {
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var idx       = 0
      while (idx < len) {
        val lastX   = x
        val binding = bindings(idx)
        if (binding.matcher eq null) {
          val offset = binding.offset
          binding.deconstructor.deconstruct(registers, offset, x)
          x = binding.register.get(registers, offset)
        } else {
          x = binding.matcher.downcastOrNull(x)
          if (x == null) {
            val expectedIdx  = binding.discriminator.discriminate(lastX)
            val expectedCase = sources(idx).asInstanceOf[Reflect.Variant.Bound[_]].cases(expectedIdx).name
            return Some(
              OpticCheck.unexpectedCase(
                focusTerms(idx).name,
                expectedCase,
                toDynamic,
                DynamicOptic(
                  focusTerms
                    .take(idx + 1)
                    .zipWithIndex
                    .map { case (term, index) =>
                      if (bindings(index).matcher eq null) DynamicOptic.Node.Field(term.name)
                      else DynamicOptic.Node.Case(term.name)
                    }
                    .toVector
                ),
                lastX
              )
            )
          }
        }
        idx += 1
      }
      None
    }

    def source: Reflect.Bound[S] = sources(0).asInstanceOf[Reflect.Bound[S]]

    def focus: Reflect.Bound[A] = focusTerms(focusTerms.length - 1).value.asInstanceOf[Reflect.Bound[A]]

    def getOption(s: S): Option[A] = {
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
          if (source.isInstanceOf[Reflect.Record.Bound[_]]) DynamicOptic.Node.Field(focusTermName)
          else DynamicOptic.Node.Case(focusTermName)
        }
        idx += 1
      }
      nodes.result()
    }

    def replace(s: S, a: A): S = {
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

    def replaceOption(s: S, a: A): Option[S] = {
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

    def modify(s: S, f: A => A): S = {
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

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: OptionalImpl[_, _] =>
        java.util.Arrays.equals(other.sources.asInstanceOf[Array[AnyRef]], sources.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.focusTerms.asInstanceOf[Array[AnyRef]], focusTerms.asInstanceOf[Array[AnyRef]])
      case _ => false
    }
  }
}

sealed trait Traversal[S, A] extends Optic[S, A] { self =>
  def fold[Z](s: S)(zero: Z, f: (Z, A) => Z): Z

  def reduce(s: S)(f: (A, A) => A): Either[OpticCheck, A] = {
    var one = false

    val reduced = fold[A](s)(
      null.asInstanceOf[A],
      (acc, a) => {
        if (!one) {
          one = true
          a
        } else f(acc, a)
      }
    )

    if (one) Right(reduced)
    else Left(check(s).get)
  }

  // Compose this traversal with a lens:
  override def apply[B](that: Lens[A, B]): Traversal[S, B] = Traversal(this, that)

  // Compose this traversal with a prism:
  override def apply[B <: A](that: Prism[A, B]): Traversal[S, B] = Traversal(this, that)

  // Compose this traversal with an optional:
  override def apply[B](that: Optional[A, B]): Traversal[S, B] = Traversal(this, that)

  // Compose this traversal with a traversal:
  override def apply[B](that: Traversal[A, B]): Traversal[S, B] = Traversal(this, that)

}

object Traversal {
  def apply[S, T, A](first: Traversal[S, T], second: Traversal[T, A]): Traversal[S, A] =
    apply(first.leafs, second.leafs)

  def apply[S, T, A](first: Traversal[S, T], second: Lens[T, A]): Traversal[S, A] =
    apply(first.leafs, second.leafs)

  def apply[S, T, A <: T](first: Traversal[S, T], second: Prism[T, A]): Traversal[S, A] =
    apply(first.leafs, second.leafs)

  def apply[S, T, A](first: Traversal[S, T], second: Optional[T, A]): Traversal[S, A] =
    apply(first.leafs, second.leafs)

  def apply[S, T, A](first: Lens[S, T], second: Traversal[T, A]): Traversal[S, A] =
    apply(first.leafs, second.leafs)

  def apply[S, T <: S, A](first: Prism[S, T], second: Traversal[T, A]): Traversal[S, A] =
    apply(first.leafs, second.leafs)

  def apply[S, T, A](first: Optional[S, T], second: Traversal[T, A]): Traversal[S, A] =
    apply(first.leafs, second.leafs)

  private[this] def apply[S, A](
    leafs1: Array[Leaf[_, _]],
    leafs2: Array[Leaf[_, _]]
  ): Traversal[S, A] =
    new TraversalMixed((leafs1.last, leafs2.head) match {
      case (lens1: Lens[_, _], lens2: Lens[_, _]) =>
        val lens = Lens.apply(lens1.asInstanceOf[Lens[Any, Any]], lens2.asInstanceOf[Lens[Any, Any]])
        (leafs1.init :+ lens.asInstanceOf[Leaf[_, _]]) ++ leafs2.tail
      case (prism1: Prism[_, _], prism2: Prism[_, _]) =>
        val prism = Prism.apply(prism1.asInstanceOf[Prism[Any, Any]], prism2.asInstanceOf[Prism[Any, Any]])
        (leafs1.init :+ prism.asInstanceOf[Leaf[_, _]]) ++ leafs2.tail
      case _ =>
        leafs1 ++ leafs2
    })

  def arrayValues[A](reflect: Reflect.Bound[A]): Traversal[Array[A], A] = {
    require(reflect ne null)
    new SeqValues(Reflect.array(reflect))
  }

  def listValues[A](reflect: Reflect.Bound[A]): Traversal[List[A], A] = {
    require(reflect ne null)
    new SeqValues(Reflect.list(reflect))
  }

  def mapKeys[Key, Value, M[_, _]](map: Reflect.Map.Bound[Key, Value, M]): Traversal[M[Key, Value], Key] = {
    require(map ne null)
    new MapKeys(map)
  }

  def mapValues[Key, Value, M[_, _]](
    map: Reflect.Map.Bound[Key, Value, M]
  ): Traversal[M[Key, Value], Value] = {
    require(map ne null)
    new MapValues(map)
  }

  def seqValues[A, C[_]](seq: Reflect.Sequence.Bound[A, C]): Traversal[C[A], A] = {
    require(seq ne null)
    new SeqValues(seq)
  }

  def setValues[A](reflect: Reflect.Bound[A]): Traversal[Set[A], A] = {
    require(reflect ne null)
    new SeqValues(Reflect.set(reflect))
  }

  def vectorValues[A](reflect: Reflect.Bound[A]): Traversal[Vector[A], A] = {
    require(reflect ne null)
    new SeqValues(Reflect.vector(reflect))
  }

  private[schema] case class SeqValues[A, C[_]](source: Reflect.Sequence.Bound[A, C])
      extends Traversal[C[A], A]
      with Leaf[C[A], A] {
    def check(s: C[A]): Option[OpticCheck] = {
      val deconstructor = source.seqDeconstructor

      val iterator = deconstructor.deconstruct(s)

      if (iterator.hasNext) None
      else Some(OpticCheck.emptySequence(toDynamic, DynamicOptic.root, s))
    }

    def focus: Reflect.Bound[A] = source.element

    def fold[Z](s: C[A])(zero: Z, f: (Z, A) => Z): Z = {
      val deconstructor = source.seqDeconstructor
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

    def modify(s: C[A], f: A => A): C[A] = {
      val constructor   = source.seqConstructor
      val deconstructor = source.seqDeconstructor
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

    override def hashCode: Int = source.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: SeqValues[_, _] => other.source.equals(source)
      case _                      => false
    }
  }

  private[schema] case class MapKeys[Key, Value, M[_, _]](source: Reflect.Map.Bound[Key, Value, M])
      extends Traversal[M[Key, Value], Key]
      with Leaf[M[Key, Value], Key] {
    def check(s: M[Key, Value]): Option[OpticCheck] = {
      val deconstructor = source.mapDeconstructor
      val iterator      = deconstructor.deconstruct(s)
      if (iterator.hasNext) None
      else Some(OpticCheck.emptyMap(toDynamic, DynamicOptic.root, s))
    }

    def focus: Reflect.Bound[Key] = source.key

    def fold[Z](s: M[Key, Value])(zero: Z, f: (Z, Key) => Z): Z = {
      val deconstructor = source.mapDeconstructor
      val it            = deconstructor.deconstruct(s)
      var z             = zero
      while (it.hasNext) z = f(z, deconstructor.getKey(it.next()))
      z
    }

    def modify(s: M[Key, Value], f: Key => Key): M[Key, Value] = {
      val deconstructor = source.mapDeconstructor
      val constructor   = source.mapConstructor
      val builder       = constructor.newObjectBuilder[Key, Value]()
      val it            = deconstructor.deconstruct(s)
      while (it.hasNext) {
        val next = it.next()
        constructor.addObject(builder, f(deconstructor.getKey(next)), deconstructor.getValue(next))
      }
      constructor.resultObject(builder)
    }

    override lazy val toDynamic: DynamicOptic = DynamicOptic(Vector(DynamicOptic.Node.MapKeys))

    override def hashCode: Int = source.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: MapKeys[_, _, _] => other.source.equals(source)
      case _                       => false
    }
  }

  private[schema] case class MapValues[Key, Value, M[_, _]](source: Reflect.Map.Bound[Key, Value, M])
      extends Traversal[M[Key, Value], Value]
      with Leaf[M[Key, Value], Value] {
    def focus: Reflect.Bound[Value] = source.value

    def check(s: M[Key, Value]): Option[OpticCheck] = {
      val deconstructor = source.mapDeconstructor
      val iterator      = deconstructor.deconstruct(s)
      if (iterator.hasNext) None
      else Some(OpticCheck.emptyMap(toDynamic, DynamicOptic.root, s))
    }

    def fold[Z](s: M[Key, Value])(zero: Z, f: (Z, Value) => Z): Z = {
      val deconstructor = source.mapDeconstructor
      val it            = deconstructor.deconstruct(s)
      var z             = zero
      while (it.hasNext) z = f(z, deconstructor.getValue(it.next()))
      z
    }

    def modify(s: M[Key, Value], f: Value => Value): M[Key, Value] = {
      val deconstructor = source.mapDeconstructor
      val constructor   = source.mapConstructor
      val builder       = constructor.newObjectBuilder[Key, Value]()
      val it            = deconstructor.deconstruct(s)
      while (it.hasNext) {
        val next = it.next()
        constructor.addObject(builder, deconstructor.getKey(next), f(deconstructor.getValue(next)))
      }
      constructor.resultObject(builder)
    }

    override lazy val toDynamic: DynamicOptic = DynamicOptic(Vector(DynamicOptic.Node.MapValues))

    override def hashCode: Int = source.hashCode

    override def equals(obj: Any): Boolean = obj match {
      case other: MapValues[_, _, _] => other.source.equals(source)
      case _                         => false
    }
  }

  private[schema] case class TraversalMixed[S, A](leafs: Array[Leaf[_, _]]) extends Traversal[S, A] {
    def check(s: S): Option[OpticCheck] = {
      var xs    = Vector[Any](s)
      var check = Option.empty[OpticCheck]

      var idx = 0
      while (check.isEmpty && idx < leafs.length) {
        val leaf = leafs(idx)
        if (leaf.isInstanceOf[Lens.LensImpl[_, _]]) {
          val lens = leaf.asInstanceOf[Lens.LensImpl[Any, Any]]
          xs = xs.map(x => lens.get(x))
        } else if (leaf.isInstanceOf[Prism.PrismImpl[_, _]]) {
          val prism = leaf.asInstanceOf[Prism.PrismImpl[Any, Any]]
          xs = xs.flatMap { x =>
            prism.getOption(x) match {
              case Some(a) => Vector(a)
              case None =>
                check = prism.check(x)

                Vector.empty[Any]
            }
          }
        } else if (leaf.isInstanceOf[Traversal[_, _]]) {
          val traversal = leaf.asInstanceOf[Traversal[Any, Any]]
          xs = xs.flatMap { x =>
            check = traversal.check(x)

            if (check.isEmpty) {
              traversal.fold[Vector[Any]](x)(Vector.empty[Any], (acc, a) => acc :+ a)
            } else {
              Vector.empty[Any]
            }
          }
        }
        idx += 1
      }

      check.map { check =>
        // Attach the right prefix paths to the error message:
        leafs.take(idx - 1).foldRight(check) { (optic, acc) =>
          acc.shift(optic.toDynamic)
        }
      }
    }

    def source: Reflect.Bound[S] = leafs(0).source.asInstanceOf[Reflect.Bound[S]]

    def focus: Reflect.Bound[A] = leafs(leafs.length - 1).focus.asInstanceOf[Reflect.Bound[A]]

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z): Z = {
      var g   = f.asInstanceOf[(Any, Any) => Any]
      var idx = leafs.length
      while (idx > 0) {
        idx -= 1
        val leaf = leafs(idx)
        val h    = g
        if (leaf.isInstanceOf[Lens.LensImpl[_, _]]) {
          val lens = leaf.asInstanceOf[Lens.LensImpl[Any, Any]]
          g = (z: Any, t: Any) => h(z, lens.get(t))
        } else if (leaf.isInstanceOf[Prism.PrismImpl[_, _]]) {
          val prism = leaf.asInstanceOf[Prism.PrismImpl[Any, Any]]
          g = (z: Any, t: Any) =>
            prism.getOption(t) match {
              case Some(a) => h(z, a)
              case _       => z
            }
        } else if (leaf.isInstanceOf[Optional.OptionalImpl[_, _]]) {
          val optional = leaf.asInstanceOf[Optional.OptionalImpl[Any, Any]]
          g = (z: Any, t: Any) =>
            optional.getOption(t) match {
              case Some(a) => h(z, a)
              case _       => z
            }
        } else {
          val traversal = leaf.asInstanceOf[Traversal[Any, Any]]
          g = (z: Any, t: Any) => traversal.fold(t)(z, h)
        }
      }
      g(zero, s).asInstanceOf[Z]
    }

    def modify(s: S, f: A => A): S = {
      var g   = f.asInstanceOf[Any => Any]
      var idx = leafs.length
      while (idx > 0) {
        idx -= 1
        val leaf = leafs(idx).asInstanceOf[Leaf[Any, Any]]
        val h    = g
        g = (x: Any) => leaf.modify(x, h)
      }
      g(s).asInstanceOf[S]
    }

    override lazy val toDynamic: DynamicOptic = DynamicOptic(leafs.flatMap(_.toDynamic.nodes).toVector)
  }
}

private[schema] case class OpticBinding(
  offset: RegisterOffset = RegisterOffset.Zero,
  deconstructor: Deconstructor[Any] = null,
  constructor: Constructor[Any] = null,
  register: Register[Any] = null,
  matcher: Matcher[Any] = null,
  discriminator: Discriminator[Any] = null
)
