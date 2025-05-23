package zio.blocks.schema

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._
import scala.collection.mutable

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

  def apply[B](that: Lens[A, B]): Optic[S, B]

  def apply[B <: A](that: Prism[A, B]): Optic[S, B]

  def apply[B](that: Optional[A, B]): Optic[S, B]

  def apply[B](that: Traversal[A, B]): Traversal[S, B]

  def check(s: S): Option[OpticCheck]

  def modify(s: S, f: A => A): S

  def modifyOption(s: S, f: A => A): Option[S] = {
    var modified = false
    val result = modify(
      s,
      (a: A) => {
        modified = true
        f(a)
      }
    )
    if (modified) Some(result)
    else None
  }

  def modifyOrFail(s: S, f: A => A): Either[OpticCheck, S] = {
    var modified = false
    val result = modify(
      s,
      (a: A) => {
        modified = true
        f(a)
      }
    )
    if (modified) new Right(result)
    else new Left(check(s).get)
  }

  def toDynamic: DynamicOptic

  private[schema] def toDynamic(index: Int): DynamicOptic = new DynamicOptic(toDynamic.nodes.take(index + 1))

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
}

sealed trait Lens[S, A] extends Optic[S, A] {
  def get(s: S): A

  def replace(s: S, a: A): S

  def apply[B](that: Lens[A, B]): Lens[S, B] = Lens(this, that)

  def apply[B <: A](that: Prism[A, B]): Optional[S, B] = Optional(this, that)

  def apply[B](that: Optional[A, B]): Optional[S, B] = Optional(this, that)

  def apply[B](that: Traversal[A, B]): Traversal[S, B] = Traversal(this, that)
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
  ) extends Lens[S, A] {
    private[this] var bindings: Array[LensBinding]  = null
    private[this] var usedRegisters: RegisterOffset = RegisterOffset.Zero

    {
      var offset   = RegisterOffset.Zero
      val len      = sources.length
      val bindings = new Array[LensBinding](len)
      var idx      = 0
      while (idx < len) {
        val source        = sources(idx)
        val focusTermName = focusTerms(idx).name
        bindings(idx) = new LensBinding(
          deconstructor = source.deconstructor.asInstanceOf[Deconstructor[Any]],
          constructor = source.constructor.asInstanceOf[Constructor[Any]],
          register = source.registers(source.fields.indexWhere(_.name == focusTermName)).asInstanceOf[Register[Any]],
          offset = offset
        )
        offset = RegisterOffset.add(offset, source.usedRegisters)
        idx += 1
      }
      this.usedRegisters = offset
      this.bindings = bindings
    }

    def source: Reflect.Bound[S] = sources(0).asInstanceOf[Reflect.Bound[S]]

    def focus: Reflect.Bound[A] = focusTerms(focusTerms.length - 1).value.asInstanceOf[Reflect.Bound[A]]

    def check(s: S): None.type = None

    def get(s: S): A = {
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

    def replace(s: S, a: A): S = {
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

    lazy val toDynamic: DynamicOptic = DynamicOptic(focusTerms.map(term => DynamicOptic.Node.Field(term.name)).toVector)

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
      case Some(a) => new Right(a)
      case _       => new Left(check(s).get)
    }

  def reverseGet(a: A): S

  def replace(s: S, a: A): S

  def replaceOption(s: S, a: A): Option[S]

  def replaceOrFail(s: S, a: A): Either[OpticCheck, S] =
    replaceOption(s, a) match {
      case Some(s) => new Right(s)
      case _       => new Left(check(s).get)
    }

  def apply[B <: A](that: Prism[A, B]): Prism[S, B] = Prism(this, that)

  def apply[B](that: Lens[A, B]): Optional[S, B] = Optional(this, that)

  def apply[B](that: Optional[A, B]): Optional[S, B] = Optional(this, that)

  def apply[B](that: Traversal[A, B]): Traversal[S, B] = Traversal(this, that)
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
  ) extends Prism[S, A] {
    private[this] var matchers: Array[Matcher[Any]]             = new Array[Matcher[Any]](sources.length)
    private[this] var discriminators: Array[Discriminator[Any]] = new Array[Discriminator[Any]](sources.length)

    {
      val len            = sources.length
      val matchers       = new Array[Matcher[Any]](len)
      val discriminators = new Array[Discriminator[Any]](len)
      var idx            = 0
      while (idx < len) {
        val source        = sources(idx)
        val focusTermName = focusTerms(idx).name
        matchers(idx) = source.matchers.apply(source.cases.indexWhere(_.name == focusTermName))
        discriminators(idx) = source.discriminator.asInstanceOf[Discriminator[Any]]
        idx += 1
      }
      this.discriminators = discriminators
      this.matchers = matchers
    }

    def source: Reflect.Bound[S] = sources(0).asInstanceOf[Reflect.Bound[S]]

    def focus: Reflect.Bound[A] = focusTerms(focusTerms.length - 1).value.asInstanceOf[Reflect.Bound[A]]

    def check(s: S): Option[OpticCheck] = {
      val len    = matchers.length
      var x: Any = s
      var idx    = 0
      while (idx < len) {
        val lastX = x
        x = matchers(idx).downcastOrNull(x)
        if (x == null) {
          val actualCaseIdx  = discriminators(idx).discriminate(lastX)
          val actualCase     = sources(idx).cases(actualCaseIdx).name
          val focusTermName  = focusTerms(idx).name
          val unexpectedCase = OpticCheck.UnexpectedCase(focusTermName, actualCase, toDynamic, toDynamic(idx), lastX)
          return new Some(new OpticCheck(::(unexpectedCase, Nil)))
        }
        idx += 1
      }
      None
    }

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

    lazy val toDynamic: DynamicOptic = DynamicOptic(focusTerms.map(term => DynamicOptic.Node.Case(term.name)).toVector)

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
      case Some(a) => new Right(a)
      case _       => new Left(check(s).get)
    }

  def replace(s: S, a: A): S

  def replaceOption(s: S, a: A): Option[S]

  def replaceOrFail(s: S, a: A): Either[OpticCheck, S] =
    replaceOption(s, a) match {
      case Some(s) => new Right(s)
      case _       => new Left(check(s).get)
    }

  def apply[B](that: Lens[A, B]): Optional[S, B] = Optional(this, that)

  def apply[B <: A](that: Prism[A, B]): Optional[S, B] = Optional(this, that)

  def apply[B](that: Optional[A, B]): Optional[S, B] = Optional(this, that)

  def apply[B](that: Traversal[A, B]): Traversal[S, B] = Traversal(this, that)
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
  ) extends Optional[S, A] {
    private[this] var bindings: Array[OpticBinding] = null
    private[this] var usedRegisters: RegisterOffset = RegisterOffset.Zero

    {
      val len      = sources.length
      val bindings = new Array[OpticBinding](len)
      var offset   = RegisterOffset.Zero
      var idx      = 0
      while (idx < len) {
        val source        = sources(idx)
        val focusTermName = focusTerms(idx).name
        if (source.isInstanceOf[Reflect.Record.Bound[_]]) {
          val record = source.asInstanceOf[Reflect.Record.Bound[_]]
          bindings(idx) = new LensBinding(
            deconstructor = record.deconstructor.asInstanceOf[Deconstructor[Any]],
            constructor = record.constructor.asInstanceOf[Constructor[Any]],
            register = record.registers(record.fields.indexWhere(_.name == focusTermName)).asInstanceOf[Register[Any]],
            offset = offset
          )
          offset = RegisterOffset.add(offset, record.usedRegisters)
        } else {
          val variant = source.asInstanceOf[Reflect.Variant.Bound[_]]
          bindings(idx) = new PrismBinding(
            matcher = variant.matchers.apply(variant.cases.indexWhere(_.name == focusTermName)),
            discriminator = variant.discriminator.asInstanceOf[Discriminator[Any]]
          )
        }
        idx += 1
      }
      this.usedRegisters = offset
      this.bindings = bindings
    }

    def source: Reflect.Bound[S] = sources(0).asInstanceOf[Reflect.Bound[S]]

    def focus: Reflect.Bound[A] = focusTerms(focusTerms.length - 1).value.asInstanceOf[Reflect.Bound[A]]

    def check(s: S): Option[OpticCheck] = {
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var idx       = 0
      while (idx < len) {
        bindings(idx) match {
          case lensBinding: LensBinding =>
            val offset = lensBinding.offset
            lensBinding.deconstructor.deconstruct(registers, offset, x)
            x = lensBinding.register.get(registers, offset)
          case binding =>
            val prismBinding = binding.asInstanceOf[PrismBinding]
            val lastX        = x
            x = prismBinding.matcher.downcastOrNull(x)
            if (x == null) {
              val actualCaseIdx = prismBinding.discriminator.discriminate(lastX)
              val actualCase    = sources(idx).asInstanceOf[Reflect.Variant.Bound[Any]].cases(actualCaseIdx).name
              val focusTermName = focusTerms(idx).name
              val unexpectedCase =
                OpticCheck.UnexpectedCase(focusTermName, actualCase, toDynamic, toDynamic(idx), lastX)
              return new Some(new OpticCheck(::(unexpectedCase, Nil)))
            }
        }
        idx += 1
      }
      None
    }

    def getOption(s: S): Option[A] = {
      val registers = Registers(usedRegisters)
      var x: Any    = s
      val len       = bindings.length
      var idx       = 0
      while (idx < len) {
        bindings(idx) match {
          case lensBinding: LensBinding =>
            val offset = lensBinding.offset
            lensBinding.deconstructor.deconstruct(registers, offset, x)
            x = lensBinding.register.get(registers, offset)
          case binding =>
            x = binding.asInstanceOf[PrismBinding].matcher.downcastOrNull(x)
            if (x == null) return None
        }
        idx += 1
      }
      new Some(x.asInstanceOf[A])
    }

    lazy val toDynamic: DynamicOptic = DynamicOptic {
      val nodes = Vector.newBuilder[DynamicOptic.Node]
      val len   = bindings.length
      var idx   = 0
      while (idx < len) {
        val binding       = bindings(idx)
        val focusTermName = focusTerms(idx).name
        nodes.addOne {
          if (binding.isInstanceOf[LensBinding]) DynamicOptic.Node.Field(focusTermName)
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
        bindings(idx) match {
          case lensBinding: LensBinding =>
            val offset = lensBinding.offset
            lensBinding.deconstructor.deconstruct(registers, offset, x)
            if (idx <= len) x = lensBinding.register.get(registers, offset)
          case binding =>
            x = binding.asInstanceOf[PrismBinding].matcher.downcastOrNull(x)
            if (x == null) return s
        }
        idx += 1
      }
      x = a
      while (idx > 0) {
        idx -= 1
        bindings(idx) match {
          case lensBinding: LensBinding =>
            val offset = lensBinding.offset
            lensBinding.register.set(registers, offset, x)
            x = lensBinding.constructor.construct(registers, offset)
          case _ =>
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
        bindings(idx) match {
          case lensBinding: LensBinding =>
            val offset = lensBinding.offset
            lensBinding.deconstructor.deconstruct(registers, offset, x)
            if (idx <= len) x = lensBinding.register.get(registers, offset)
          case binding =>
            x = binding.asInstanceOf[PrismBinding].matcher.downcastOrNull(x)
            if (x == null) return None
        }
        idx += 1
      }
      x = a
      while (idx > 0) {
        idx -= 1
        bindings(idx) match {
          case lensBinding: LensBinding =>
            val offset = lensBinding.offset
            lensBinding.register.set(registers, offset, x)
            x = lensBinding.constructor.construct(registers, offset)
          case _ =>
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
        bindings(idx) match {
          case lensBinding: LensBinding =>
            val offset = lensBinding.offset
            lensBinding.deconstructor.deconstruct(registers, offset, x)
            x = lensBinding.register.get(registers, offset)
          case binding =>
            x = binding.asInstanceOf[PrismBinding].matcher.downcastOrNull(x)
            if (x == null) return s
        }
        idx += 1
      }
      x = f(x.asInstanceOf[A])
      while (idx > 0) {
        idx -= 1
        bindings(idx) match {
          case lensBinding: LensBinding =>
            val offset = lensBinding.offset
            lensBinding.register.set(registers, offset, x)
            x = lensBinding.constructor.construct(registers, offset)
          case _ =>
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

  def reduceOrFail(s: S)(f: (A, A) => A): Either[OpticCheck, A] = {
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
    if (one) new Right(reduced)
    else new Left(check(s).get)
  }

  def apply[B](that: Lens[A, B]): Traversal[S, B] = Traversal(this, that)

  def apply[B <: A](that: Prism[A, B]): Traversal[S, B] = Traversal(this, that)

  def apply[B](that: Optional[A, B]): Traversal[S, B] = Traversal(this, that)

  def apply[B](that: Traversal[A, B]): Traversal[S, B] = Traversal(this, that)
}

object Traversal {
  def apply[S, T, A](first: Traversal[S, T], second: Traversal[T, A]): Traversal[S, A] = {
    val traversal1 = first.asInstanceOf[TraversalImpl[_, _]]
    val traversal2 = second.asInstanceOf[TraversalImpl[_, _]]
    new TraversalImpl(traversal1.sources ++ traversal2.sources, traversal1.focusTerms ++ traversal2.focusTerms)
  }

  def apply[S, T, A](first: Traversal[S, T], second: Lens[T, A]): Traversal[S, A] = {
    val traversal1 = first.asInstanceOf[TraversalImpl[_, _]]
    val lens2      = second.asInstanceOf[Lens.LensImpl[_, _]]
    new TraversalImpl(traversal1.sources ++ lens2.sources, traversal1.focusTerms ++ lens2.focusTerms)
  }

  def apply[S, T, A <: T](first: Traversal[S, T], second: Prism[T, A]): Traversal[S, A] = {
    val traversal1 = first.asInstanceOf[TraversalImpl[_, _]]
    val prism2     = second.asInstanceOf[Prism.PrismImpl[_, _]]
    new TraversalImpl(traversal1.sources ++ prism2.sources, traversal1.focusTerms ++ prism2.focusTerms)
  }

  def apply[S, T, A](first: Traversal[S, T], second: Optional[T, A]): Traversal[S, A] = {
    val traversal1 = first.asInstanceOf[TraversalImpl[_, _]]
    val optional2  = second.asInstanceOf[Optional.OptionalImpl[_, _]]
    new TraversalImpl(traversal1.sources ++ optional2.sources, traversal1.focusTerms ++ optional2.focusTerms)
  }

  def apply[S, T, A](first: Lens[S, T], second: Traversal[T, A]): Traversal[S, A] = {
    val lens1      = first.asInstanceOf[Lens.LensImpl[_, _]]
    val traversal2 = second.asInstanceOf[TraversalImpl[_, _]]
    new TraversalImpl(lens1.sources ++ traversal2.sources, lens1.focusTerms ++ traversal2.focusTerms)
  }

  def apply[S, T <: S, A](first: Prism[S, T], second: Traversal[T, A]): Traversal[S, A] = {
    val prism1     = first.asInstanceOf[Prism.PrismImpl[_, _]]
    val traversal2 = second.asInstanceOf[TraversalImpl[_, _]]
    new TraversalImpl(prism1.sources ++ traversal2.sources, prism1.focusTerms ++ traversal2.focusTerms)
  }

  def apply[S, T, A](first: Optional[S, T], second: Traversal[T, A]): Traversal[S, A] = {
    val optional1  = first.asInstanceOf[Optional.OptionalImpl[_, _]]
    val traversal2 = second.asInstanceOf[TraversalImpl[_, _]]
    new TraversalImpl(optional1.sources ++ traversal2.sources, optional1.focusTerms ++ traversal2.focusTerms)
  }

  def arrayValues[A](reflect: Reflect.Bound[A]): Traversal[Array[A], A] = {
    require(reflect ne null)
    seqValues(Reflect.array(reflect))
  }

  def listValues[A](reflect: Reflect.Bound[A]): Traversal[List[A], A] = {
    require(reflect ne null)
    seqValues(Reflect.list(reflect))
  }

  def mapKeys[Key, Value, M[_, _]](map: Reflect.Map.Bound[Key, Value, M]): Traversal[M[Key, Value], Key] = {
    require(map ne null)
    new TraversalImpl(Array(map), Array(map.key.asTerm("key")))
  }

  def mapValues[Key, Value, M[_, _]](map: Reflect.Map.Bound[Key, Value, M]): Traversal[M[Key, Value], Value] = {
    require(map ne null)
    new TraversalImpl(Array(map), Array(map.value.asTerm("value")))
  }

  def seqValues[A, C[_]](seq: Reflect.Sequence.Bound[A, C]): Traversal[C[A], A] = {
    require(seq ne null)
    new TraversalImpl(Array(seq), Array(seq.element.asTerm("element")))
  }

  def setValues[A](reflect: Reflect.Bound[A]): Traversal[Set[A], A] = {
    require(reflect ne null)
    seqValues(Reflect.set(reflect))
  }

  def vectorValues[A](reflect: Reflect.Bound[A]): Traversal[Vector[A], A] = {
    require(reflect ne null)
    seqValues(Reflect.vector(reflect))
  }

  private[schema] case class TraversalImpl[S, A](
    sources: Array[Reflect.Bound[_]],
    focusTerms: Array[Term.Bound[_, _]]
  ) extends Traversal[S, A] {
    private[this] var bindings: Array[OpticBinding] = null
    private[this] var usedRegisters: RegisterOffset = RegisterOffset.Zero

    type Key
    type Value
    type Map[_, _]
    type Elem
    type Col[_]

    {
      val len      = sources.length
      val bindings = new Array[OpticBinding](len)
      var offset   = RegisterOffset.Zero
      var idx      = 0
      while (idx < len) {
        val focusTermName = focusTerms(idx).name
        sources(idx) match {
          case record: Reflect.Record.Bound[_] =>
            bindings(idx) = new LensBinding(
              deconstructor = record.deconstructor.asInstanceOf[Deconstructor[Any]],
              constructor = record.constructor.asInstanceOf[Constructor[Any]],
              register =
                record.registers(record.fields.indexWhere(_.name == focusTermName)).asInstanceOf[Register[Any]],
              offset = offset
            )
            offset = RegisterOffset.add(offset, record.usedRegisters)
          case variant: Reflect.Variant.Bound[_] =>
            bindings(idx) = new PrismBinding(
              matcher = variant.matchers.apply(variant.cases.indexWhere(_.name == focusTermName)),
              discriminator = variant.discriminator.asInstanceOf[Discriminator[Any]]
            )
          case sequence: Reflect.Sequence.Bound[Elem, Col] @scala.unchecked =>
            bindings(idx) = new SeqBinding[Col](
              seqDeconstructor = sequence.seqDeconstructor,
              seqConstructor = sequence.seqConstructor
            )
          case source =>
            val map = source.asInstanceOf[Reflect.Map.Bound[Key, Value, Map]]
            if (focusTermName == "key") {
              bindings(idx) = new MapKeyBinding[Map](
                mapDeconstructor = map.mapDeconstructor,
                mapConstructor = map.mapConstructor
              )
            } else {
              bindings(idx) = new MapValueBinding[Map](
                mapDeconstructor = map.mapDeconstructor,
                mapConstructor = map.mapConstructor
              )
            }
        }
        idx += 1
      }
      this.usedRegisters = offset
      this.bindings = bindings
    }

    def source: Reflect.Bound[S] = sources(0).asInstanceOf[Reflect.Bound[S]]

    def focus: Reflect.Bound[A] = focusTerms(focusTerms.length - 1).value.asInstanceOf[Reflect.Bound[A]]

    def check(s: S): Option[OpticCheck] = {
      val errors = List.newBuilder[OpticCheck.Single]
      checkRec(Registers(usedRegisters), 0, s, errors)
      errors.result() match {
        case errs: ::[OpticCheck.Single] => new Some(OpticCheck(errs))
        case _                           => None
      }
    }

    private[this] def checkRec(
      registers: Registers,
      idx: Int,
      x: Any,
      errors: mutable.Builder[OpticCheck.Single, List[OpticCheck.Single]]
    ): Unit =
      bindings(idx) match {
        case lensBinding: LensBinding =>
          val offset = lensBinding.offset
          lensBinding.deconstructor.deconstruct(registers, offset, x)
          val x1 = lensBinding.register.get(registers, offset)
          if (idx + 1 != bindings.length) checkRec(registers, idx + 1, x1, errors)
        case prismBinding: PrismBinding =>
          val x1 = prismBinding.matcher.downcastOrNull(x)
          if (x1 == null) {
            val actualCaseIdx = prismBinding.discriminator.discriminate(x)
            val actualCase    = sources(idx).asInstanceOf[Reflect.Variant.Bound[Any]].cases(actualCaseIdx).name
            val focusTermName = focusTerms(idx).name
            errors.addOne(new OpticCheck.UnexpectedCase(focusTermName, actualCase, toDynamic, toDynamic(idx), x))
          } else if (idx + 1 != bindings.length) checkRec(registers, idx + 1, x1, errors)
        case seqBinding: SeqBinding[Col] @scala.unchecked =>
          val deconstructor = seqBinding.seqDeconstructor
          val it            = deconstructor.deconstruct(x.asInstanceOf[Col[Elem]])
          if (it.isEmpty) errors.addOne(new OpticCheck.EmptySequence(toDynamic, toDynamic(idx), x))
          else if (idx + 1 != bindings.length) {
            while (it.hasNext) checkRec(registers, idx + 1, it.next(), errors)
          }
        case mapKeyBinding: MapKeyBinding[Map] @scala.unchecked =>
          val deconstructor = mapKeyBinding.mapDeconstructor
          val it            = deconstructor.deconstruct(x.asInstanceOf[Map[Key, Value]])
          if (it.isEmpty) errors.addOne(new OpticCheck.EmptyMap(toDynamic, toDynamic(idx), x))
          else if (idx + 1 != bindings.length) {
            while (it.hasNext) checkRec(registers, idx + 1, deconstructor.getKey(it.next()), errors)
          }
        case mapValueBinding: MapValueBinding[Map] @scala.unchecked =>
          val deconstructor = mapValueBinding.mapDeconstructor
          val it            = deconstructor.deconstruct(x.asInstanceOf[Map[Key, Value]])
          if (it.isEmpty) errors.addOne(new OpticCheck.EmptyMap(toDynamic, toDynamic(idx), x))
          else if (idx + 1 != bindings.length) {
            while (it.hasNext) checkRec(registers, idx + 1, deconstructor.getValue(it.next()), errors)
          }
      }

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z): Z = foldRec(Registers(usedRegisters), 0, s, zero, f)

    private[this] def foldRec[Z](registers: Registers, idx: Int, x: Any, zero: Z, f: (Z, A) => Z): Z =
      bindings(idx) match {
        case lensBinding: LensBinding =>
          val offset = lensBinding.offset
          lensBinding.deconstructor.deconstruct(registers, offset, x)
          val x1 = lensBinding.register.get(registers, offset)
          if (idx + 1 == bindings.length) f(zero, x1.asInstanceOf[A])
          else foldRec(registers, idx + 1, x1, zero, f)
        case prismBinding: PrismBinding =>
          val x1 = prismBinding.matcher.downcastOrNull(x)
          if (x1 == null) zero
          else if (idx + 1 == bindings.length) f(zero, x1.asInstanceOf[A])
          else foldRec(registers, idx + 1, x1, zero, f)
        case seqBinding: SeqBinding[Col] @scala.unchecked =>
          val deconstructor = seqBinding.seqDeconstructor
          if (idx + 1 == bindings.length) foldCol(deconstructor, x.asInstanceOf[Col[A]], zero, f)
          else {
            val it = deconstructor.deconstruct(x.asInstanceOf[Col[Elem]])
            var z  = zero
            while (it.hasNext) z = foldRec(registers, idx + 1, it.next(), z, f)
            z
          }
        case mapKeyBinding: MapKeyBinding[Map] @scala.unchecked =>
          val deconstructor = mapKeyBinding.mapDeconstructor
          var z             = zero
          if (idx + 1 == bindings.length) {
            val it = deconstructor.deconstruct(x.asInstanceOf[Map[A, Value]])
            while (it.hasNext) z = f(z, deconstructor.getKey(it.next()))
          } else {
            val it = deconstructor.deconstruct(x.asInstanceOf[Map[Key, Value]])
            while (it.hasNext) z = foldRec(registers, idx + 1, deconstructor.getKey(it.next()), z, f)
          }
          z
        case mapValueBinding: MapValueBinding[Map] @scala.unchecked =>
          val deconstructor = mapValueBinding.mapDeconstructor
          var z             = zero
          if (idx + 1 == bindings.length) {
            val it = deconstructor.deconstruct(x.asInstanceOf[Map[Key, A]])
            while (it.hasNext) z = f(z, deconstructor.getValue(it.next()))
          } else {
            val it = deconstructor.deconstruct(x.asInstanceOf[Map[Key, Value]])
            while (it.hasNext) z = foldRec(registers, idx + 1, deconstructor.getValue(it.next()), z, f)
          }
          z
      }

    private[this] def foldCol[Z](deconstructor: SeqDeconstructor[Col], s: Col[A], zero: Z, f: (Z, A) => Z): Z =
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
          val len = indexed.length(s)
          var idx = 0
          indexed.elementType(s) match {
            case _: RegisterType.Boolean.type =>
              val ss = s.asInstanceOf[Col[Boolean]]
              val sf = f.asInstanceOf[(Z, Boolean) => Z]
              var z  = zero
              while (idx < len) {
                z = sf(z, indexed.booleanAt(ss, idx))
                idx += 1
              }
              z
            case _: RegisterType.Byte.type =>
              val ss = s.asInstanceOf[Col[Byte]]
              val sf = f.asInstanceOf[(Z, Byte) => Z]
              var z  = zero
              while (idx < len) {
                z = sf(z, indexed.byteAt(ss, idx))
                idx += 1
              }
              z
            case _: RegisterType.Short.type =>
              val ss = s.asInstanceOf[Col[Short]]
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
                  val ss     = s.asInstanceOf[Col[Int]]
                  val sf     = f.asInstanceOf[(Int, Int) => Int]
                  var z: Int = zi
                  while (idx < len) {
                    z = sf(z, indexed.intAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  val ss      = s.asInstanceOf[Col[Int]]
                  val sf      = f.asInstanceOf[(Long, Int) => Long]
                  var z: Long = zl
                  while (idx < len) {
                    z = sf(z, indexed.intAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  val ss        = s.asInstanceOf[Col[Int]]
                  val sf        = f.asInstanceOf[(Double, Int) => Double]
                  var z: Double = zd
                  while (idx < len) {
                    z = sf(z, indexed.intAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  val ss = s.asInstanceOf[Col[Int]]
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
                  val ss     = s.asInstanceOf[Col[Long]]
                  val sf     = f.asInstanceOf[(Int, Long) => Int]
                  var z: Int = zi
                  while (idx < len) {
                    z = sf(z, indexed.longAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  val ss      = s.asInstanceOf[Col[Long]]
                  val sf      = f.asInstanceOf[(Long, Long) => Long]
                  var z: Long = zl
                  while (idx < len) {
                    z = sf(z, indexed.longAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  val ss        = s.asInstanceOf[Col[Long]]
                  val sf        = f.asInstanceOf[(Double, Long) => Double]
                  var z: Double = zd
                  while (idx < len) {
                    z = sf(z, indexed.longAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  val ss = s.asInstanceOf[Col[Long]]
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
                  val ss     = s.asInstanceOf[Col[Double]]
                  val sf     = f.asInstanceOf[(Int, Double) => Int]
                  var z: Int = zi
                  while (idx < len) {
                    z = sf(z, indexed.doubleAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  val ss      = s.asInstanceOf[Col[Double]]
                  val sf      = f.asInstanceOf[(Long, Double) => Long]
                  var z: Long = zl
                  while (idx < len) {
                    z = sf(z, indexed.doubleAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  val ss        = s.asInstanceOf[Col[Double]]
                  val sf        = f.asInstanceOf[(Double, Double) => Double]
                  var z: Double = zd
                  while (idx < len) {
                    z = sf(z, indexed.doubleAt(ss, idx))
                    idx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  val ss = s.asInstanceOf[Col[Double]]
                  val sf = f.asInstanceOf[(Z, Double) => Z]
                  var z  = zero
                  while (idx < len) {
                    z = sf(z, indexed.doubleAt(ss, idx))
                    idx += 1
                  }
                  z
              }
            case _: RegisterType.Char.type =>
              val ss = s.asInstanceOf[Col[Char]]
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

    def modify(s: S, f: A => A): S = modifyRec(Registers(usedRegisters), 0, s, f).asInstanceOf[S]

    private[this] def modifyRec(registers: Registers, idx: Int, x: Any, f: A => A): Any =
      bindings(idx) match {
        case lensBinding: LensBinding =>
          val offset = lensBinding.offset
          lensBinding.deconstructor.deconstruct(registers, offset, x)
          var x1 = lensBinding.register.get(registers, offset)
          if (idx + 1 == bindings.length) x1 = f(x1.asInstanceOf[A])
          else x1 = modifyRec(registers, idx + 1, x1, f)
          lensBinding.register.set(registers, offset, x1)
          lensBinding.constructor.construct(registers, offset)
        case prismBinding: PrismBinding =>
          val x1 = prismBinding.matcher.downcastOrNull(x)
          if (x1 == null) x
          else if (idx + 1 == bindings.length) f(x1.asInstanceOf[A])
          else modifyRec(registers, idx + 1, x1, f)
        case seqBinding: SeqBinding[Col] @scala.unchecked =>
          val deconstructor = seqBinding.seqDeconstructor
          val constructor   = seqBinding.seqConstructor
          if (idx + 1 == bindings.length) modifySeq(deconstructor, constructor, x.asInstanceOf[Col[A]], f)
          else {
            val builder = constructor.newObjectBuilder[Any]()
            val it      = deconstructor.deconstruct(x.asInstanceOf[Col[Any]])
            while (it.hasNext) constructor.addObject(builder, modifyRec(registers, idx + 1, it.next(), f))
            constructor.resultObject(builder)
          }
        case mapKeyBinding: MapKeyBinding[Map] @scala.unchecked =>
          val deconstructor = mapKeyBinding.mapDeconstructor
          val constructor   = mapKeyBinding.mapConstructor
          if (idx + 1 == bindings.length) {
            val builder = constructor.newObjectBuilder[A, Value]()
            val it      = deconstructor.deconstruct(x.asInstanceOf[Map[A, Value]])
            while (it.hasNext) {
              val next = it.next()
              constructor.addObject(builder, f(deconstructor.getKey(next)), deconstructor.getValue(next))
            }
            constructor.resultObject(builder)
          } else {
            val builder = constructor.newObjectBuilder[Any, Value]()
            val it      = deconstructor.deconstruct(x.asInstanceOf[Map[Any, Value]])
            while (it.hasNext) {
              val next = it.next()
              constructor.addObject(
                builder,
                modifyRec(registers, idx + 1, deconstructor.getKey(next), f),
                deconstructor.getValue(next)
              )
            }
            constructor.resultObject(builder)
          }
        case mapValueBinding: MapValueBinding[Map] @scala.unchecked =>
          val deconstructor = mapValueBinding.mapDeconstructor
          val constructor   = mapValueBinding.mapConstructor
          if (idx + 1 == bindings.length) {
            val builder = constructor.newObjectBuilder[Key, A]()
            val it      = deconstructor.deconstruct(x.asInstanceOf[Map[Key, A]])
            while (it.hasNext) {
              val next = it.next()
              constructor.addObject(builder, deconstructor.getKey(next), f(deconstructor.getValue(next)))
            }
            constructor.resultObject(builder)
          } else {
            val builder = constructor.newObjectBuilder[Key, Any]()
            val it      = deconstructor.deconstruct(x.asInstanceOf[Map[Key, Any]])
            while (it.hasNext) {
              val next = it.next()
              constructor.addObject(
                builder,
                deconstructor.getKey(next),
                modifyRec(registers, idx + 1, deconstructor.getValue(next), f)
              )
            }
            constructor.resultObject(builder)
          }
      }

    private[this] def modifySeq(
      deconstructor: SeqDeconstructor[Col],
      constructor: SeqConstructor[Col],
      s: Col[A],
      f: A => A
    ): Col[A] =
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
          val len = indexed.length(s)
          indexed.elementType(s) match {
            case _: RegisterType.Boolean.type =>
              val builder = constructor.newBooleanBuilder(len)
              val ss      = s.asInstanceOf[Col[Boolean]]
              val sf      = f.asInstanceOf[Boolean => Boolean]
              var idx     = 0
              while (idx < len) {
                constructor.addBoolean(builder, sf(indexed.booleanAt(ss, idx)))
                idx += 1
              }
              constructor.resultBoolean(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Byte.type =>
              val builder = constructor.newByteBuilder(len)
              val ss      = s.asInstanceOf[Col[Byte]]
              val sf      = f.asInstanceOf[Byte => Byte]
              var idx     = 0
              while (idx < len) {
                constructor.addByte(builder, sf(indexed.byteAt(ss, idx)))
                idx += 1
              }
              constructor.resultByte(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Short.type =>
              val builder = constructor.newShortBuilder(len)
              val ss      = s.asInstanceOf[Col[Short]]
              val sf      = f.asInstanceOf[Short => Short]
              var idx     = 0
              while (idx < len) {
                constructor.addShort(builder, sf(indexed.shortAt(ss, idx)))
                idx += 1
              }
              constructor.resultShort(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Int.type =>
              val builder = constructor.newIntBuilder(len)
              val ss      = s.asInstanceOf[Col[Int]]
              val sf      = f.asInstanceOf[Int => Int]
              var idx     = 0
              while (idx < len) {
                constructor.addInt(builder, sf(indexed.intAt(ss, idx)))
                idx += 1
              }
              constructor.resultInt(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Long.type =>
              val builder = constructor.newLongBuilder(len)
              val ss      = s.asInstanceOf[Col[Long]]
              val sf      = f.asInstanceOf[Long => Long]
              var idx     = 0
              while (idx < len) {
                constructor.addLong(builder, sf(indexed.longAt(ss, idx)))
                idx += 1
              }
              constructor.resultLong(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Float.type =>
              val builder = constructor.newFloatBuilder(len)
              val ss      = s.asInstanceOf[Col[Float]]
              val sf      = f.asInstanceOf[Float => Float]
              var idx     = 0
              while (idx < len) {
                constructor.addFloat(builder, sf(indexed.floatAt(ss, idx)))
                idx += 1
              }
              constructor.resultFloat(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Double.type =>
              val builder = constructor.newDoubleBuilder(len)
              val ss      = s.asInstanceOf[Col[Double]]
              val sf      = f.asInstanceOf[Double => Double]
              var idx     = 0
              while (idx < len) {
                constructor.addDouble(builder, sf(indexed.doubleAt(ss, idx)))
                idx += 1
              }
              constructor.resultDouble(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Char.type =>
              val builder = constructor.newCharBuilder(len)
              val ss      = s.asInstanceOf[Col[Char]]
              val sf      = f.asInstanceOf[Char => Char]
              var idx     = 0
              while (idx < len) {
                constructor.addChar(builder, sf(indexed.charAt(ss, idx)))
                idx += 1
              }
              constructor.resultChar(builder).asInstanceOf[Col[A]]
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

    lazy val toDynamic: DynamicOptic = DynamicOptic {
      val nodes = Vector.newBuilder[DynamicOptic.Node]
      val len   = bindings.length
      var idx   = 0
      while (idx < len) {
        nodes.addOne {
          bindings(idx) match {
            case _: LensBinding                         => DynamicOptic.Node.Field(focusTerms(idx).name)
            case _: PrismBinding                        => DynamicOptic.Node.Case(focusTerms(idx).name)
            case _: SeqBinding[Col] @scala.unchecked    => DynamicOptic.Node.Elements
            case _: MapKeyBinding[Map] @scala.unchecked => DynamicOptic.Node.MapKeys
            case _                                      => DynamicOptic.Node.MapValues
          }
        }
        idx += 1
      }
      nodes.result()
    }

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: TraversalImpl[_, _] =>
        java.util.Arrays.equals(other.sources.asInstanceOf[Array[AnyRef]], sources.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.focusTerms.asInstanceOf[Array[AnyRef]], focusTerms.asInstanceOf[Array[AnyRef]])
      case _ => false
    }
  }
}

private[schema] sealed trait OpticBinding

private[schema] case class LensBinding(
  offset: RegisterOffset = RegisterOffset.Zero,
  deconstructor: Deconstructor[Any] = null,
  constructor: Constructor[Any] = null,
  register: Register[Any] = null
) extends OpticBinding

private[schema] case class PrismBinding(
  matcher: Matcher[Any] = null,
  discriminator: Discriminator[Any] = null
) extends OpticBinding

private[schema] case class SeqBinding[C[_]](
  seqDeconstructor: SeqDeconstructor[C] = null,
  seqConstructor: SeqConstructor[C] = null
) extends OpticBinding

private[schema] case class MapKeyBinding[M[_, _]](
  mapDeconstructor: MapDeconstructor[M] = null,
  mapConstructor: MapConstructor[M] = null
) extends OpticBinding

private[schema] case class MapValueBinding[M[_, _]](
  mapDeconstructor: MapDeconstructor[M] = null,
  mapConstructor: MapConstructor[M] = null
) extends OpticBinding
