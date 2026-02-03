package zio.blocks.schema

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.util.control.NoStackTrace

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

  def modifyOption(s: S, f: A => A): Option[S]

  def modifyOrFail(s: S, f: A => A): Either[OpticCheck, S]

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

  final def ===(that: A)(implicit schema: Schema[A]): SchemaExpr[S, Boolean] =
    SchemaExpr.Relational(SchemaExpr.Optic(this), SchemaExpr.Literal(that, schema), SchemaExpr.RelationalOperator.Equal)

  final def ===(that: Optic[S, A]): SchemaExpr[S, Boolean] =
    SchemaExpr.Relational(SchemaExpr.Optic(this), SchemaExpr.Optic(that), SchemaExpr.RelationalOperator.Equal)

  final def >(that: Optic[S, A]): SchemaExpr[S, Boolean] =
    SchemaExpr.Relational(SchemaExpr.Optic(this), SchemaExpr.Optic(that), SchemaExpr.RelationalOperator.GreaterThan)

  final def >(that: A)(implicit schema: Schema[A]): SchemaExpr[S, Boolean] = SchemaExpr.Relational(
    SchemaExpr.Optic(this),
    SchemaExpr.Literal(that, schema),
    SchemaExpr.RelationalOperator.GreaterThan
  )

  final def >=(that: Optic[S, A]): SchemaExpr[S, Boolean] = SchemaExpr.Relational(
    SchemaExpr.Optic(this),
    SchemaExpr.Optic(that),
    SchemaExpr.RelationalOperator.GreaterThanOrEqual
  )

  final def >=(that: A)(implicit schema: Schema[A]): SchemaExpr[S, Boolean] = SchemaExpr.Relational(
    SchemaExpr.Optic(this),
    SchemaExpr.Literal(that, schema),
    SchemaExpr.RelationalOperator.GreaterThanOrEqual
  )

  final def <(that: Optic[S, A]): SchemaExpr[S, Boolean] =
    SchemaExpr.Relational(SchemaExpr.Optic(this), SchemaExpr.Optic(that), SchemaExpr.RelationalOperator.LessThan)

  final def <(that: A)(implicit schema: Schema[A]): SchemaExpr[S, Boolean] = SchemaExpr.Relational(
    SchemaExpr.Optic(this),
    SchemaExpr.Literal(that, schema),
    SchemaExpr.RelationalOperator.LessThan
  )

  final def <=(that: Optic[S, A]): SchemaExpr[S, Boolean] =
    SchemaExpr.Relational(SchemaExpr.Optic(this), SchemaExpr.Optic(that), SchemaExpr.RelationalOperator.LessThanOrEqual)

  final def <=(that: A)(implicit schema: Schema[A]): SchemaExpr[S, Boolean] = SchemaExpr.Relational(
    SchemaExpr.Optic(this),
    SchemaExpr.Literal(that, schema),
    SchemaExpr.RelationalOperator.LessThanOrEqual
  )

  final def !=(that: Optic[S, A]): SchemaExpr[S, Boolean] =
    SchemaExpr.Relational(SchemaExpr.Optic(this), SchemaExpr.Optic(that), SchemaExpr.RelationalOperator.NotEqual)

  final def !=(that: A)(implicit schema: Schema[A]): SchemaExpr[S, Boolean] =
    SchemaExpr.Relational(
      SchemaExpr.Optic(this),
      SchemaExpr.Literal(that, schema),
      SchemaExpr.RelationalOperator.NotEqual
    )

  final def &&(that: Optic[S, A])(implicit ev: A =:= Boolean): SchemaExpr[S, Boolean] = SchemaExpr.Logical(
    SchemaExpr.Optic(this.asEquivalent[Boolean]),
    SchemaExpr.Optic(that.asEquivalent[Boolean]),
    SchemaExpr.LogicalOperator.And
  )

  final def &&(that: Boolean)(implicit ev: A =:= Boolean): SchemaExpr[S, Boolean] =
    SchemaExpr.Logical(
      SchemaExpr.Optic(this.asEquivalent[Boolean]),
      SchemaExpr.Literal(that, Schema[Boolean]),
      SchemaExpr.LogicalOperator.And
    )

  final def ||(that: Optic[S, A])(implicit ev: A =:= Boolean): SchemaExpr[S, Boolean] = SchemaExpr.Logical(
    SchemaExpr.Optic(this.asEquivalent[Boolean]),
    SchemaExpr.Optic(that.asEquivalent[Boolean]),
    SchemaExpr.LogicalOperator.Or
  )

  final def ||(that: Boolean)(implicit ev: A =:= Boolean): SchemaExpr[S, Boolean] =
    SchemaExpr.Logical(
      SchemaExpr.Optic(this.asEquivalent[Boolean]),
      SchemaExpr.Literal(that, Schema[Boolean]),
      SchemaExpr.LogicalOperator.Or
    )

  final def unary_!(implicit ev: A =:= Boolean): SchemaExpr[S, Boolean] =
    SchemaExpr.Not(SchemaExpr.Optic(this.asEquivalent[Boolean]))

  final def concat(that: String)(implicit ev: A =:= String): SchemaExpr[S, String] =
    SchemaExpr.StringConcat(SchemaExpr.Optic(this.asEquivalent[String]), SchemaExpr.Literal(that, Schema[String]))

  final def matches(that: String)(implicit ev: A =:= String): SchemaExpr[S, Boolean] =
    SchemaExpr.StringRegexMatch(SchemaExpr.Literal(that, Schema[String]), SchemaExpr.Optic(this.asEquivalent[String]))

  final def +(that: A)(implicit isNumeric: IsNumeric[A]): SchemaExpr[S, A] = SchemaExpr.Arithmetic(
    SchemaExpr.Optic(this),
    SchemaExpr.Literal(that, isNumeric.schema),
    SchemaExpr.ArithmeticOperator.Add,
    isNumeric
  )

  final def -(that: A)(implicit isNumeric: IsNumeric[A]): SchemaExpr[S, A] = SchemaExpr.Arithmetic(
    SchemaExpr.Optic(this),
    SchemaExpr.Literal(that, isNumeric.schema),
    SchemaExpr.ArithmeticOperator.Subtract,
    isNumeric
  )

  final def *(that: A)(implicit isNumeric: IsNumeric[A]): SchemaExpr[S, A] = SchemaExpr.Arithmetic(
    SchemaExpr.Optic(this),
    SchemaExpr.Literal(that, isNumeric.schema),
    SchemaExpr.ArithmeticOperator.Multiply,
    isNumeric
  )

  final def length(implicit ev: A =:= String): SchemaExpr[S, Int] =
    SchemaExpr.StringLength(SchemaExpr.Optic(this.asEquivalent[String]))

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
    val lens1 = first.asInstanceOf[LensImpl[?, ?]]
    val lens2 = second.asInstanceOf[LensImpl[?, ?]]
    new LensImpl(lens1.sources ++ lens2.sources, lens1.focusTerms ++ lens2.focusTerms)
  }

  private[schema] case class LensImpl[S, A](
    sources: Array[Reflect.Record.Bound[?]],
    focusTerms: Array[Term.Bound[?, ?]]
  ) extends Lens[S, A] {
    private[this] var bindings: Array[LensBinding]  = null
    private[this] var usedRegisters: RegisterOffset = 0L

    private[this] def init(): Unit = {
      var offset   = 0L
      val len      = sources.length
      val bindings = new Array[LensBinding](len)
      var idx      = 0
      while (idx < len) {
        val source        = sources(idx)
        val focusTermName = focusTerms(idx).name
        bindings(idx) = new LensBinding(
          deconstructor = source.deconstructor.asInstanceOf[Deconstructor[Any]],
          constructor = source.constructor.asInstanceOf[Constructor[Any]],
          register = source.registers(source.fieldIndexByName(focusTermName)),
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
      if (bindings eq null) init()
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
      if (bindings eq null) init()
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
      if (bindings eq null) init()
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

    def modifyOption(s: S, f: A => A): Option[S] = new Some(modify(s, f))

    def modifyOrFail(s: S, f: A => A): Either[OpticCheck, S] = new Right(modify(s, f))

    lazy val toDynamic: DynamicOptic =
      new DynamicOptic(ArraySeq.unsafeWrapArray(focusTerms.map(term => new DynamicOptic.Node.Field(term.name))))

    override def toString: String = {
      val path = focusTerms.map(term => s".${term.name}").mkString
      s"Lens(_$path)"
    }

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: LensImpl[?, ?] =>
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
    val prism1 = first.asInstanceOf[PrismImpl[?, ?]]
    val prism2 = second.asInstanceOf[PrismImpl[?, ?]]
    new PrismImpl(prism1.sources ++ prism2.sources, prism1.focusTerms ++ prism2.focusTerms)
  }

  private[schema] case class PrismImpl[S, A <: S](
    sources: Array[Reflect.Variant.Bound[?]],
    focusTerms: Array[Term.Bound[?, ?]]
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
        matchers(idx) = source.matchers.apply(source.caseIndexByName(focusTermName))
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
        if (x == null) return new Some(toOpticCheck(idx, lastX))
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

    def modifyOption(s: S, f: A => A): Option[S] = {
      val len    = matchers.length
      var x: Any = s
      var idx    = 0
      while (idx < len) {
        x = matchers(idx).downcastOrNull(x)
        if (x == null) return None
        idx += 1
      }
      new Some(f(x.asInstanceOf[A]))
    }

    def modifyOrFail(s: S, f: A => A): Either[OpticCheck, S] = {
      val len    = matchers.length
      var x: Any = s
      var idx    = 0
      while (idx < len) {
        val lastX = x
        x = matchers(idx).downcastOrNull(x)
        if (x == null) return new Left(toOpticCheck(idx, lastX))
        idx += 1
      }
      new Right(f(x.asInstanceOf[A]))
    }

    private[this] def toOpticCheck(idx: Int, lastX: Any): OpticCheck = {
      val actualCaseIdx  = discriminators(idx).discriminate(lastX)
      val actualCase     = sources(idx).cases(actualCaseIdx).name
      val focusTermName  = focusTerms(idx).name
      val unexpectedCase = new OpticCheck.UnexpectedCase(focusTermName, actualCase, toDynamic, toDynamic(idx), lastX)
      new OpticCheck(new ::(unexpectedCase, Nil))
    }

    lazy val toDynamic: DynamicOptic =
      new DynamicOptic(ArraySeq.unsafeWrapArray(focusTerms.map(term => new DynamicOptic.Node.Case(term.name))))

    override def toString: String = {
      val path = focusTerms.map(term => s".when[${term.name}]").mkString
      s"Prism(_$path)"
    }

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: PrismImpl[?, ?] =>
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

  def replaceOrFail(s: S, a: A): Either[OpticCheck, S]

  def apply[B](that: Lens[A, B]): Optional[S, B] = Optional(this, that)

  def apply[B <: A](that: Prism[A, B]): Optional[S, B] = Optional(this, that)

  def apply[B](that: Optional[A, B]): Optional[S, B] = Optional(this, that)

  def apply[B](that: Traversal[A, B]): Traversal[S, B] = Traversal(this, that)
}

object Optional {
  def apply[S, T, A](first: Optional[S, T], second: Lens[T, A]): Optional[S, A] = {
    require((first ne null) && (second ne null))
    val optional1 = first.asInstanceOf[OptionalImpl[?, ?]]
    val lens2     = second.asInstanceOf[Lens.LensImpl[?, ?]]
    new OptionalImpl(
      optional1.sources ++ lens2.sources,
      optional1.focusTerms ++ lens2.focusTerms,
      optional1.params ++ new Array[Any](lens2.sources.length)
    )
  }

  def apply[S, T, A <: T](first: Optional[S, T], second: Prism[T, A]): Optional[S, A] = {
    require((first ne null) && (second ne null))
    val optional1 = first.asInstanceOf[OptionalImpl[?, ?]]
    val prism2    = second.asInstanceOf[Prism.PrismImpl[?, ?]]
    new OptionalImpl(
      optional1.sources ++ prism2.sources,
      optional1.focusTerms ++ prism2.focusTerms,
      optional1.params ++ new Array[Any](prism2.sources.length)
    )
  }

  def apply[S, T, A](first: Optional[S, T], second: Optional[T, A]): Optional[S, A] = {
    require((first ne null) && (second ne null))
    val optional1 = first.asInstanceOf[OptionalImpl[?, ?]]
    val optional2 = second.asInstanceOf[OptionalImpl[?, ?]]
    new OptionalImpl(
      optional1.sources ++ optional2.sources,
      optional1.focusTerms ++ optional2.focusTerms,
      optional1.params ++ optional2.params
    )
  }

  def apply[S, T, A <: T](first: Lens[S, T], second: Prism[T, A]): Optional[S, A] = {
    require((first ne null) && (second ne null))
    val lens1  = first.asInstanceOf[Lens.LensImpl[?, ?]]
    val prism2 = second.asInstanceOf[Prism.PrismImpl[?, ?]]
    new OptionalImpl(
      lens1.sources ++ prism2.sources,
      lens1.focusTerms ++ prism2.focusTerms,
      new Array[Any](lens1.sources.length + prism2.sources.length)
    )
  }

  def apply[S, T, A](first: Lens[S, T], second: Optional[T, A]): Optional[S, A] = {
    require((first ne null) && (second ne null))
    val lens1     = first.asInstanceOf[Lens.LensImpl[?, ?]]
    val optional2 = second.asInstanceOf[OptionalImpl[?, ?]]
    new OptionalImpl(
      lens1.sources ++ optional2.sources,
      lens1.focusTerms ++ optional2.focusTerms,
      new Array[Any](lens1.sources.length) ++ optional2.params
    )
  }

  def apply[S, T <: S, A](first: Prism[S, T], second: Lens[T, A]): Optional[S, A] = {
    require((first ne null) && (second ne null))
    val prism1 = first.asInstanceOf[Prism.PrismImpl[?, ?]]
    val lens2  = second.asInstanceOf[Lens.LensImpl[?, ?]]
    new OptionalImpl(
      prism1.sources ++ lens2.sources,
      prism1.focusTerms ++ lens2.focusTerms,
      new Array[Any](prism1.sources.length + lens2.sources.length)
    )
  }
  def apply[S, T <: S, A](first: Prism[S, T], second: Optional[T, A]): Optional[S, A] = {
    require((first ne null) && (second ne null))
    val prism1    = first.asInstanceOf[Prism.PrismImpl[?, ?]]
    val optional2 = second.asInstanceOf[OptionalImpl[?, ?]]
    new OptionalImpl(
      prism1.sources ++ optional2.sources,
      prism1.focusTerms ++ optional2.focusTerms,
      new Array[Any](prism1.sources.length) ++ optional2.params
    )
  }

  def at[A, C[_]](seq: Reflect.Sequence.Bound[A, C], index: Int): Optional[C[A], A] = {
    require((seq ne null) && index >= 0)
    new OptionalImpl(Array(seq), Array(seq.element.asTerm("at")), Array[Any](index))
  }

  def atKey[K, V, M[_, _]](map: Reflect.Map.Bound[K, V, M], key: K): Optional[M[K, V], V] = {
    require(map ne null)
    new OptionalImpl(Array(map), Array(map.value.asTerm("atKey")), Array[Any](key))
  }

  def wrapped[A, B](wrapper: Reflect.Wrapper.Bound[A, B]): Optional[A, B] = {
    require(wrapper ne null)
    new OptionalImpl(Array(wrapper), Array(wrapper.wrapped.asTerm("wrapped")), Array[Any](null))
  }

  private[schema] case class OptionalImpl[S, A](
    sources: Array[Reflect.Bound[?]],
    focusTerms: Array[Term.Bound[?, ?]],
    params: Array[Any]
  ) extends Optional[S, A] {
    private[this] var bindings: Array[OpticBinding] = null
    private[this] var usedRegisters: RegisterOffset = 0L

    type Key
    type Value
    type Map[_, _]
    type Elem
    type Col[_]
    type Wrapping
    type Wrapped

    private[this] def init(): Unit = {
      var offset   = 0L
      val len      = sources.length
      val bindings = new Array[OpticBinding](len)
      var idx      = 0
      while (idx < len) {
        val focusTermName = focusTerms(idx).name
        sources(idx) match {
          case record: Reflect.Record.Bound[?] =>
            bindings(idx) = new LensBinding(
              deconstructor = record.deconstructor.asInstanceOf[Deconstructor[Any]],
              constructor = record.constructor.asInstanceOf[Constructor[Any]],
              register = record.registers(record.fieldIndexByName(focusTermName)),
              offset = offset
            )
            offset = RegisterOffset.add(offset, record.usedRegisters)
          case variant: Reflect.Variant.Bound[?] =>
            bindings(idx) = new PrismBinding(
              matcher = variant.matchers.apply(variant.caseIndexByName(focusTermName)),
              discriminator = variant.discriminator.asInstanceOf[Discriminator[Any]]
            )
          case wrapper: Reflect.Wrapper.Bound[Wrapping, Wrapped] @scala.unchecked =>
            bindings(idx) = new WrappedBinding(
              wrap = wrapper.binding.wrap,
              unwrap = wrapper.binding.unwrap
            )
          case sequence: Reflect.Sequence.Bound[Elem, Col] @scala.unchecked =>
            bindings(idx) = new AtBinding(
              seqDeconstructor = sequence.seqDeconstructor,
              seqConstructor = sequence.seqConstructor,
              index = params(idx).asInstanceOf[Int],
              elemClassTag = sequence.elemClassTag
            )
          case source =>
            val map = source.asInstanceOf[Reflect.Map.Bound[Key, Value, Map]]
            bindings(idx) = new AtKeyBinding(
              mapDeconstructor = map.mapDeconstructor,
              mapConstructor = map.mapConstructor,
              keySchema = map.key,
              key = params(idx).asInstanceOf[Key]
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
      if (bindings eq null) init()
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
          case prismBinding: PrismBinding =>
            val lastX = x
            x = prismBinding.matcher.downcastOrNull(x)
            if (x == null) {
              val actualCaseIdx  = prismBinding.discriminator.discriminate(lastX)
              val actualCase     = sources(idx).asInstanceOf[Reflect.Variant.Bound[Any]].cases(actualCaseIdx).name
              val focusTermName  = focusTerms(idx).name
              val unexpectedCase =
                new OpticCheck.UnexpectedCase(focusTermName, actualCase, toDynamic, toDynamic(idx), lastX)
              return new Some(new OpticCheck(new ::(unexpectedCase, Nil)))
            }
          case wrapperBinding: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
            try {
              x = wrapperBinding.unwrap(x.asInstanceOf[Wrapping])
            } catch {
              case error: SchemaError =>
                val wrappingError = new OpticCheck.WrappingError(toDynamic, toDynamic(idx), error)
                return new Some(new OpticCheck(new ::(wrappingError, Nil)))
            }
          case atBinding: AtBinding[Col] @scala.unchecked =>
            val deconstructor = atBinding.seqDeconstructor
            val col           = x.asInstanceOf[Col[A]]
            deconstructor match {
              case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
                val colSize = indexed.size(col)
                val colIdx  = atBinding.index
                if (colSize <= colIdx) {
                  val sequenceIndexOutOfBounds =
                    new OpticCheck.SequenceIndexOutOfBounds(toDynamic, toDynamic(idx), colIdx, colSize)
                  return new Some(new OpticCheck(new ::(sequenceIndexOutOfBounds, Nil)))
                }
                indexed.elementType(col) match {
                  case _: RegisterType.Boolean.type => x = indexed.booleanAt(x.asInstanceOf[Col[Boolean]], colIdx)
                  case _: RegisterType.Byte.type    => x = indexed.byteAt(x.asInstanceOf[Col[Byte]], colIdx)
                  case _: RegisterType.Char.type    => x = indexed.charAt(x.asInstanceOf[Col[Char]], colIdx)
                  case _: RegisterType.Short.type   => x = indexed.shortAt(x.asInstanceOf[Col[Short]], colIdx)
                  case _: RegisterType.Float.type   => x = indexed.floatAt(x.asInstanceOf[Col[Float]], colIdx)
                  case _: RegisterType.Int.type     => x = indexed.intAt(x.asInstanceOf[Col[Int]], colIdx)
                  case _: RegisterType.Double.type  => x = indexed.doubleAt(x.asInstanceOf[Col[Double]], colIdx)
                  case _: RegisterType.Long.type    => x = indexed.longAt(x.asInstanceOf[Col[Long]], colIdx)
                  case _                            => x = indexed.objectAt(x.asInstanceOf[Col[AnyRef]], colIdx)
                }
              case _ =>
                val it      = deconstructor.deconstruct(col)
                val colIdx  = atBinding.index
                var currIdx = 0
                while (currIdx < colIdx && it.hasNext) {
                  it.next(): Unit
                  currIdx += 1
                }
                if (currIdx == colIdx && it.hasNext) x = it.next()
                else {
                  val sequenceIndexOutOfBounds =
                    new OpticCheck.SequenceIndexOutOfBounds(toDynamic, toDynamic(idx), colIdx, currIdx)
                  return new Some(new OpticCheck(new ::(sequenceIndexOutOfBounds, Nil)))
                }
            }
          case binding =>
            val atKeyBinding  = binding.asInstanceOf[AtKeyBinding[Key, Map]]
            val deconstructor = atKeyBinding.mapDeconstructor
            val key           = atKeyBinding.key
            deconstructor.get(x.asInstanceOf[Map[Key, Value]], key) match {
              case Some(value) =>
                x = value
              case _ =>
                return new Some(new OpticCheck(new ::(new OpticCheck.MissingKey(toDynamic, toDynamic(idx), key), Nil)))
            }
        }
        idx += 1
      }
      None
    }

    def getOption(s: S): Option[A] = {
      if (bindings eq null) init()
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
          case prismBinding: PrismBinding =>
            x = prismBinding.matcher.downcastOrNull(x)
            if (x == null) return None
          case wrapperBinding: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
            try {
              x = wrapperBinding.unwrap(x.asInstanceOf[Wrapping])
            } catch {
              case _: SchemaError => return None
            }
          case atBinding: AtBinding[Col] @scala.unchecked =>
            val deconstructor = atBinding.seqDeconstructor
            val col           = x.asInstanceOf[Col[A]]
            deconstructor match {
              case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
                val colSize = indexed.size(col)
                val colIdx  = atBinding.index
                if (colSize <= colIdx) return None
                indexed.elementType(col) match {
                  case _: RegisterType.Boolean.type => x = indexed.booleanAt(x.asInstanceOf[Col[Boolean]], colIdx)
                  case _: RegisterType.Byte.type    => x = indexed.byteAt(x.asInstanceOf[Col[Byte]], colIdx)
                  case _: RegisterType.Char.type    => x = indexed.charAt(x.asInstanceOf[Col[Char]], colIdx)
                  case _: RegisterType.Short.type   => x = indexed.shortAt(x.asInstanceOf[Col[Short]], colIdx)
                  case _: RegisterType.Float.type   => x = indexed.floatAt(x.asInstanceOf[Col[Float]], colIdx)
                  case _: RegisterType.Int.type     => x = indexed.intAt(x.asInstanceOf[Col[Int]], colIdx)
                  case _: RegisterType.Double.type  => x = indexed.doubleAt(x.asInstanceOf[Col[Double]], colIdx)
                  case _: RegisterType.Long.type    => x = indexed.longAt(x.asInstanceOf[Col[Long]], colIdx)
                  case _                            => x = indexed.objectAt(x.asInstanceOf[Col[AnyRef]], colIdx)
                }
              case _ =>
                val it      = deconstructor.deconstruct(col)
                val colIdx  = atBinding.index
                var currIdx = 0
                while (currIdx < colIdx && it.hasNext) {
                  it.next(): Unit
                  currIdx += 1
                }
                if (currIdx == colIdx && it.hasNext) x = it.next()
                else return None
            }
          case binding =>
            val atKeyBinding  = binding.asInstanceOf[AtKeyBinding[Key, Map]]
            val deconstructor = atKeyBinding.mapDeconstructor
            deconstructor.get(x.asInstanceOf[Map[Key, Value]], atKeyBinding.key) match {
              case Some(value) => x = value
              case _           => return None
            }
        }
        idx += 1
      }
      new Some(x.asInstanceOf[A])
    }

    def replace(s: S, a: A): S = {
      if (bindings eq null) init()
      try modifyRecursive(Registers(usedRegisters), 0, s, _ => a).asInstanceOf[S]
      catch {
        case _: OpticCheckBuilder => s
      }
    }

    def replaceOption(s: S, a: A): Option[S] = {
      if (bindings eq null) init()
      try {
        var success = false
        val x       = modifyRecursive(
          Registers(usedRegisters),
          0,
          s,
          _ => {
            success = true
            a
          }
        )
        if (success) new Some(x.asInstanceOf[S])
        else None
      } catch {
        case _: OpticCheckBuilder => None
      }
    }

    def replaceOrFail(s: S, a: A): Either[OpticCheck, S] = {
      if (bindings eq null) init()
      try {
        var success = false
        val x       = modifyRecursive(
          Registers(usedRegisters),
          0,
          s,
          _ => {
            success = true
            a
          }
        )
        if (success) new Right(x.asInstanceOf[S])
        else new Left(check(s).get)
      } catch {
        case ocb: OpticCheckBuilder => new Left(ocb.toOpticCheck())
      }
    }

    def modify(s: S, f: A => A): S = {
      if (bindings eq null) init()
      try modifyRecursive(Registers(usedRegisters), 0, s, f).asInstanceOf[S]
      catch {
        case _: OpticCheckBuilder => s
      }
    }

    private[this] def modifyRecursive(registers: Registers, idx: Int, x: Any, f: A => A): Any =
      bindings(idx) match {
        case lensBinding: LensBinding =>
          val offset = lensBinding.offset
          lensBinding.deconstructor.deconstruct(registers, offset, x)
          var x1 = lensBinding.register.get(registers, offset)
          if (idx + 1 == bindings.length) x1 = f(x1.asInstanceOf[A])
          else x1 = modifyRecursive(registers, idx + 1, x1, f)
          lensBinding.register.set(registers, offset, x1)
          lensBinding.constructor.construct(registers, offset)
        case prismBinding: PrismBinding =>
          val x1 = prismBinding.matcher.downcastOrNull(x)
          if (x1 == null) x
          else if (idx + 1 == bindings.length) f(x1.asInstanceOf[A])
          else modifyRecursive(registers, idx + 1, x1, f)
        case wrapperBinding: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
          val x1 =
            try wrapperBinding.unwrap(x.asInstanceOf[Wrapping])
            catch {
              case error: SchemaError => throw toOpticCheckBuilder(idx, error)
            }
          try
            wrapperBinding.wrap({
              if (idx + 1 == bindings.length) f(x1.asInstanceOf[A])
              else modifyRecursive(registers, idx + 1, x1, f)
            }.asInstanceOf[Wrapped])
          catch {
            case error: SchemaError => throw toOpticCheckBuilder(idx, error)
          }
        case atBinding: AtBinding[Col] @scala.unchecked =>
          val deconstructor = atBinding.seqDeconstructor
          val constructor   = atBinding.seqConstructor
          val colIdx        = atBinding.index
          val col           = x.asInstanceOf[Col[A]]
          if (idx + 1 == bindings.length)
            modifySeqAt(deconstructor, constructor, col, f, colIdx, atBinding.elemClassTag)
          else {
            val sizeHint =
              deconstructor match {
                case indexed: SeqDeconstructor.SpecializedIndexed[Col] => indexed.size(col)
                case _                                                 => 8
              }
            implicit val classTag: ClassTag[Any] = atBinding.elemClassTag.asInstanceOf[ClassTag[Any]]
            val builder                          = constructor.newBuilder[Any](sizeHint)
            val it                               = deconstructor.deconstruct(col)
            var currIdx                          = 0
            while (it.hasNext) {
              constructor.add(
                builder, {
                  val value = it.next()
                  if (currIdx != colIdx) value
                  else modifyRecursive(registers, idx + 1, value, f)
                }
              )
              currIdx += 1
            }
            constructor.result(builder)
          }
        case binding =>
          val atKeyBinding  = binding.asInstanceOf[AtKeyBinding[Key, Map]]
          val deconstructor = atKeyBinding.mapDeconstructor
          val constructor   = atKeyBinding.mapConstructor
          val map           = x.asInstanceOf[Map[Key, Any]]
          val key           = atKeyBinding.key
          deconstructor.get(map, key) match {
            case Some(value) =>
              constructor.updated(
                map,
                key,
                if (idx + 1 == bindings.length) f(value.asInstanceOf[A])
                else modifyRecursive(registers, idx + 1, value, f)
              )
            case _ => map
          }
      }

    private[this] def modifySeqAt(
      deconstructor: SeqDeconstructor[Col],
      constructor: SeqConstructor[Col],
      s: Col[A],
      f: A => A,
      colIdx: Int,
      elemClassTag: ClassTag[?]
    ): Col[A] = {
      implicit val classTag: ClassTag[A] = elemClassTag.asInstanceOf[ClassTag[A]]
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
          val size    = indexed.size(s)
          val builder = constructor.newBuilder[A](size)
          var idx     = 0
          while (idx < size) {
            constructor.add(
              builder, {
                val value = indexed.objectAt(s, idx)
                if (idx == colIdx) f(value)
                else value
              }
            )
            idx += 1
          }
          constructor.result(builder)
        case _ =>
          val builder = constructor.newBuilder[A]()
          val it      = deconstructor.deconstruct(s)
          var currIdx = -1
          while (it.hasNext)
            constructor.add(
              builder, {
                currIdx += 1
                val value = it.next()
                if (currIdx != colIdx) value
                else f(value)
              }
            )
          constructor.result(builder)
      }
    }

    def modifyOption(s: S, f: A => A): Option[S] = {
      if (bindings eq null) init()
      try {
        var success = false
        val x       = modifyRecursive(
          Registers(usedRegisters),
          0,
          s,
          a => {
            success = true
            f(a)
          }
        )
        if (success) new Some(x.asInstanceOf[S])
        else None
      } catch {
        case _: OpticCheckBuilder => None
      }
    }

    def modifyOrFail(s: S, f: A => A): Either[OpticCheck, S] = {
      if (bindings eq null) init()
      try {
        var success = false
        val x       = modifyRecursive(
          Registers(usedRegisters),
          0,
          s,
          a => {
            success = true
            f(a)
          }
        )
        if (success) new Right(x.asInstanceOf[S])
        else new Left(check(s).get)
      } catch {
        case ocb: OpticCheckBuilder => new Left(ocb.toOpticCheck())
      }
    }

    private[this] def toOpticCheckBuilder(idx: Int, error: SchemaError): OpticCheckBuilder =
      new OpticCheckBuilder(toOpticCheck =
        () => new OpticCheck(new ::(new OpticCheck.WrappingError(toDynamic, toDynamic(idx), error), Nil))
      )

    lazy val toDynamic: DynamicOptic = new DynamicOptic({
      if (bindings eq null) init()
      val nodes = Vector.newBuilder[DynamicOptic.Node]
      val len   = bindings.length
      var idx   = 0
      while (idx < len) {
        nodes.addOne {
          bindings(idx) match {
            case _: LensBinding =>
              new DynamicOptic.Node.Field(focusTerms(idx).name)
            case _: PrismBinding =>
              new DynamicOptic.Node.Case(focusTerms(idx).name)
            case _: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
              DynamicOptic.Node.Wrapped
            case at: AtBinding[Col] @scala.unchecked =>
              new DynamicOptic.Node.AtIndex(at.index)
            case binding =>
              val atKeyBinding = binding.asInstanceOf[AtKeyBinding[Key, Map]]
              new DynamicOptic.Node.AtMapKey(atKeyBinding.keySchema.toDynamicValue(atKeyBinding.key))
          }
        }
        idx += 1
      }
      nodes.result()
    })

    override def toString: String = {
      if (bindings eq null) init()
      val sb  = new StringBuilder
      val len = bindings.length
      var idx = 0
      while (idx < len) {
        bindings(idx) match {
          case _: LensBinding =>
            sb.append('.').append(focusTerms(idx).name)
          case _: PrismBinding =>
            sb.append(".when[").append(focusTerms(idx).name).append(']')
          case _: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
            sb.append(".wrapped[").append(focus.typeId.name).append(']')
          case at: AtBinding[Col] @scala.unchecked =>
            sb.append(".at(").append(at.index).append(')')
          case _ =>
            sb.append(".atKey(<key>)")
        }
        idx += 1
      }
      s"Optional(_${sb.toString})"
    }

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(params.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: OptionalImpl[?, ?] =>
        java.util.Arrays.equals(other.sources.asInstanceOf[Array[AnyRef]], sources.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.focusTerms.asInstanceOf[Array[AnyRef]], focusTerms.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.params.asInstanceOf[Array[AnyRef]], params.asInstanceOf[Array[AnyRef]])
      case _ => false
    }
  }
}

sealed trait Traversal[S, A] extends Optic[S, A] { self =>
  def fold[Z](s: S)(zero: Z, f: (Z, A) => Z): Z

  def reduceOrFail(s: S)(f: (A, A) => A): Either[OpticCheck, A] = {
    var one     = false
    val reduced = fold[A](s)(
      null.asInstanceOf[A],
      (acc, a) => {
        if (one) f(acc, a)
        else {
          one = true
          a
        }
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
  def atIndices[A, C[_]](seq: Reflect.Sequence.Bound[A, C], indices: Seq[Int]): Traversal[C[A], A] = {
    require((seq ne null) && (indices ne null) && indices.nonEmpty)
    val sortedIndices = indices.toArray
    java.util.Arrays.sort(sortedIndices)
    var prev = sortedIndices(0)
    require(prev >= 0)
    var idx = 1
    while (idx < sortedIndices.length) {
      val curr = sortedIndices(idx)
      require(prev < curr)
      prev = curr
      idx += 1
    }
    new TraversalImpl(Array(seq), Array(seq.element.asTerm("atIndices")), Array[Any](sortedIndices))
  }

  def atKeys[K, V, M[_, _]](map: Reflect.Map.Bound[K, V, M], keys: Seq[K]): Traversal[M[K, V], V] = {
    require((map ne null) && (keys ne null) && keys.nonEmpty)
    new TraversalImpl(Array(map), Array(map.value.asTerm("atKeys")), Array[Any](keys))
  }

  def apply[S, T, A](first: Traversal[S, T], second: Traversal[T, A]): Traversal[S, A] = {
    val traversal1 = first.asInstanceOf[TraversalImpl[?, ?]]
    val traversal2 = second.asInstanceOf[TraversalImpl[?, ?]]
    new TraversalImpl(
      traversal1.sources ++ traversal2.sources,
      traversal1.focusTerms ++ traversal2.focusTerms,
      traversal1.params ++ traversal2.params
    )
  }

  def apply[S, T, A](first: Traversal[S, T], second: Lens[T, A]): Traversal[S, A] = {
    val traversal1 = first.asInstanceOf[TraversalImpl[?, ?]]
    val lens2      = second.asInstanceOf[Lens.LensImpl[?, ?]]
    new TraversalImpl(
      traversal1.sources ++ lens2.sources,
      traversal1.focusTerms ++ lens2.focusTerms,
      traversal1.params ++ new Array[Any](lens2.sources.length)
    )
  }

  def apply[S, T, A <: T](first: Traversal[S, T], second: Prism[T, A]): Traversal[S, A] = {
    val traversal1 = first.asInstanceOf[TraversalImpl[?, ?]]
    val prism2     = second.asInstanceOf[Prism.PrismImpl[?, ?]]
    new TraversalImpl(
      traversal1.sources ++ prism2.sources,
      traversal1.focusTerms ++ prism2.focusTerms,
      traversal1.params ++ new Array[Any](prism2.sources.length)
    )
  }

  def apply[S, T, A](first: Traversal[S, T], second: Optional[T, A]): Traversal[S, A] = {
    val traversal1 = first.asInstanceOf[TraversalImpl[?, ?]]
    val optional2  = second.asInstanceOf[Optional.OptionalImpl[?, ?]]
    new TraversalImpl(
      traversal1.sources ++ optional2.sources,
      traversal1.focusTerms ++ optional2.focusTerms,
      traversal1.params ++ optional2.params
    )
  }

  def apply[S, T, A](first: Lens[S, T], second: Traversal[T, A]): Traversal[S, A] = {
    val lens1      = first.asInstanceOf[Lens.LensImpl[?, ?]]
    val traversal2 = second.asInstanceOf[TraversalImpl[?, ?]]
    new TraversalImpl(
      lens1.sources ++ traversal2.sources,
      lens1.focusTerms ++ traversal2.focusTerms,
      new Array[Any](lens1.sources.length) ++ traversal2.params
    )
  }

  def apply[S, T <: S, A](first: Prism[S, T], second: Traversal[T, A]): Traversal[S, A] = {
    val prism1     = first.asInstanceOf[Prism.PrismImpl[?, ?]]
    val traversal2 = second.asInstanceOf[TraversalImpl[?, ?]]
    new TraversalImpl(
      prism1.sources ++ traversal2.sources,
      prism1.focusTerms ++ traversal2.focusTerms,
      new Array[Any](prism1.sources.length) ++ traversal2.params
    )
  }

  def apply[S, T, A](first: Optional[S, T], second: Traversal[T, A]): Traversal[S, A] = {
    val optional1  = first.asInstanceOf[Optional.OptionalImpl[?, ?]]
    val traversal2 = second.asInstanceOf[TraversalImpl[?, ?]]
    new TraversalImpl(
      optional1.sources ++ traversal2.sources,
      optional1.focusTerms ++ traversal2.focusTerms,
      optional1.params ++ traversal2.params
    )
  }

  def listValues[A](reflect: Reflect.Bound[A]): Traversal[List[A], A] = {
    require(reflect ne null)
    seqValues(Reflect.list(reflect))
  }

  def mapKeys[K, V, M[_, _]](map: Reflect.Map.Bound[K, V, M]): Traversal[M[K, V], K] = {
    require(map ne null)
    new TraversalImpl(Array(map), Array(map.key.asTerm("key")), Array[Any](null))
  }

  def mapValues[K, V, M[_, _]](map: Reflect.Map.Bound[K, V, M]): Traversal[M[K, V], V] = {
    require(map ne null)
    new TraversalImpl(Array(map), Array(map.value.asTerm("value")), Array[Any](null))
  }

  def seqValues[A, C[_]](seq: Reflect.Sequence.Bound[A, C]): Traversal[C[A], A] = {
    require(seq ne null)
    new TraversalImpl(Array(seq), Array(seq.element.asTerm("element")), Array[Any](null))
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
    sources: Array[Reflect.Bound[?]],
    focusTerms: Array[Term.Bound[?, ?]],
    params: Array[Any]
  ) extends Traversal[S, A] {
    private[this] var bindings: Array[OpticBinding] = null
    private[this] var usedRegisters: RegisterOffset = 0L

    type Key
    type Value
    type Map[_, _]
    type Elem
    type Col[_]
    type Wrapping
    type Wrapped

    private[this] def init(): Unit = {
      var offset   = 0L
      val len      = sources.length
      val bindings = new Array[OpticBinding](len)
      var idx      = 0
      while (idx < len) {
        val focusTermName = focusTerms(idx).name
        sources(idx) match {
          case record: Reflect.Record.Bound[?] =>
            bindings(idx) = new LensBinding(
              deconstructor = record.deconstructor.asInstanceOf[Deconstructor[Any]],
              constructor = record.constructor.asInstanceOf[Constructor[Any]],
              register = record.registers(record.fieldIndexByName(focusTermName)),
              offset = offset
            )
            offset = RegisterOffset.add(offset, record.usedRegisters)
          case variant: Reflect.Variant.Bound[?] =>
            bindings(idx) = new PrismBinding(
              matcher = variant.matchers.apply(variant.caseIndexByName(focusTermName)),
              discriminator = variant.discriminator.asInstanceOf[Discriminator[Any]]
            )
          case wrapper: Reflect.Wrapper.Bound[Wrapping, Wrapped] @scala.unchecked =>
            bindings(idx) = new WrappedBinding(
              wrap = wrapper.binding.wrap,
              unwrap = wrapper.binding.unwrap
            )
          case sequence: Reflect.Sequence.Bound[Elem, Col] @scala.unchecked =>
            if (focusTermName == "at") {
              bindings(idx) = new AtBinding[Col](
                seqDeconstructor = sequence.seqDeconstructor,
                seqConstructor = sequence.seqConstructor,
                index = params(idx).asInstanceOf[Int],
                elemClassTag = sequence.elemClassTag
              )
            } else if (focusTermName == "atIndices") {
              bindings(idx) = new AtIndicesBinding[Col](
                seqDeconstructor = sequence.seqDeconstructor,
                seqConstructor = sequence.seqConstructor,
                indices = params(idx).asInstanceOf[Array[Int]],
                elemClassTag = sequence.elemClassTag
              )
            } else {
              bindings(idx) = new SeqBinding[Col](
                seqDeconstructor = sequence.seqDeconstructor,
                seqConstructor = sequence.seqConstructor,
                elemClassTag = sequence.elemClassTag
              )
            }
          case source =>
            val map = source.asInstanceOf[Reflect.Map.Bound[Key, Value, Map]]
            if (focusTermName == "atKey") {
              bindings(idx) = new AtKeyBinding[Key, Map](
                mapDeconstructor = map.mapDeconstructor,
                mapConstructor = map.mapConstructor,
                keySchema = map.key,
                key = params(idx).asInstanceOf[Key]
              )
            } else if (focusTermName == "atKeys") {
              bindings(idx) = new AtKeysBinding[Key, Map](
                mapDeconstructor = map.mapDeconstructor,
                mapConstructor = map.mapConstructor,
                keySchema = map.key,
                keys = params(idx).asInstanceOf[Seq[Key]]
              )
            } else if (focusTermName == "key") {
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
      if (bindings eq null) init()
      val errors = List.newBuilder[OpticCheck.Single]
      checkRecursive(Registers(usedRegisters), 0, s, errors)
      errors.result() match {
        case errs: ::[OpticCheck.Single] => new Some(new OpticCheck(errs))
        case _                           => None
      }
    }

    private[this] def checkRecursive(
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
          if (idx + 1 != bindings.length) checkRecursive(registers, idx + 1, x1, errors)
        case prismBinding: PrismBinding =>
          val x1 = prismBinding.matcher.downcastOrNull(x)
          if (x1 == null) {
            val actualCaseIdx = prismBinding.discriminator.discriminate(x)
            val actualCase    = sources(idx).asInstanceOf[Reflect.Variant.Bound[Any]].cases(actualCaseIdx).name
            val focusTermName = focusTerms(idx).name
            errors.addOne(new OpticCheck.UnexpectedCase(focusTermName, actualCase, toDynamic, toDynamic(idx), x))
          } else if (idx + 1 != bindings.length) checkRecursive(registers, idx + 1, x1, errors)
        case wrapperBinding: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
          try {
            val x1 = wrapperBinding.unwrap(x.asInstanceOf[Wrapping])
            if (idx + 1 != bindings.length) checkRecursive(registers, idx + 1, x1, errors)
          } catch {
            case error: SchemaError =>
              errors.addOne(new OpticCheck.WrappingError(toDynamic, toDynamic(idx), error))
          }
        case atBinding: AtBinding[Col] @scala.unchecked =>
          val deconstructor = atBinding.seqDeconstructor
          val col           = x.asInstanceOf[Col[A]]
          deconstructor match {
            case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
              val colSize = indexed.size(col)
              val colIdx  = atBinding.index
              if (colSize <= colIdx) {
                errors.addOne(new OpticCheck.SequenceIndexOutOfBounds(toDynamic, toDynamic(idx), colIdx, colSize))
              } else if (idx + 1 != bindings.length) {
                checkRecursive(registers, idx + 1, indexed.objectAt(col, colIdx), errors)
              }
            case _ =>
              val it      = deconstructor.deconstruct(col)
              val colIdx  = atBinding.index
              var currIdx = 0
              while (currIdx < colIdx && it.hasNext) {
                it.next(): Unit
                currIdx += 1
              }
              if (currIdx != colIdx || !it.hasNext) {
                errors.addOne(new OpticCheck.SequenceIndexOutOfBounds(toDynamic, toDynamic(idx), colIdx, currIdx))
              } else if (idx + 1 != bindings.length) checkRecursive(registers, idx + 1, it.next(), errors)
          }
        case atKeyBinding: AtKeyBinding[Key, Map] @scala.unchecked =>
          val deconstructor = atKeyBinding.mapDeconstructor
          val key           = atKeyBinding.key
          deconstructor.get(x.asInstanceOf[Map[Key, Value]], key) match {
            case Some(value) => if (idx + 1 != bindings.length) checkRecursive(registers, idx + 1, value, errors)
            case _           => errors.addOne(new OpticCheck.MissingKey(toDynamic, toDynamic(idx), key))
          }
        case atIndicesBinding: AtIndicesBinding[Col] @scala.unchecked =>
          val deconstructor = atIndicesBinding.seqDeconstructor
          val col           = x.asInstanceOf[Col[A]]
          deconstructor match {
            case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
              val colSize    = indexed.size(col)
              val indices    = atIndicesBinding.indices
              var indicesIdx = 0
              while (indicesIdx < indices.length) {
                val colIdx = indices(indicesIdx)
                if (colSize <= colIdx) {
                  errors.addOne(new OpticCheck.SequenceIndexOutOfBounds(toDynamic, toDynamic(idx), colIdx, colSize))
                } else if (idx + 1 != bindings.length) {
                  checkRecursive(registers, idx + 1, indexed.objectAt(col, colIdx), errors)
                }
                indicesIdx += 1
              }
            case _ =>
              val it                  = deconstructor.deconstruct(col)
              val indices             = atIndicesBinding.indices
              var currIdx, indicesIdx = 0
              while (indicesIdx < indices.length) {
                val colIdx = indices(indicesIdx)
                while (currIdx < colIdx && it.hasNext) {
                  it.next(): Unit
                  currIdx += 1
                }
                if (currIdx != colIdx || !it.hasNext) {
                  errors.addOne(new OpticCheck.SequenceIndexOutOfBounds(toDynamic, toDynamic(idx), colIdx, currIdx))
                } else if (idx + 1 != bindings.length) checkRecursive(registers, idx + 1, it.next(), errors)
                indicesIdx += 1
              }
          }
        case atKeysBinding: AtKeysBinding[Key, Map] @scala.unchecked =>
          atKeysBinding.keys.foreach {
            val deconstructor = atKeysBinding.mapDeconstructor
            key =>
              deconstructor.get(x.asInstanceOf[Map[Key, Value]], key) match {
                case Some(value) => if (idx + 1 != bindings.length) checkRecursive(registers, idx + 1, value, errors)
                case _           => errors.addOne(new OpticCheck.MissingKey(toDynamic, toDynamic(idx), key))
              }
          }
        case seqBinding: SeqBinding[Col] @scala.unchecked =>
          val deconstructor = seqBinding.seqDeconstructor
          val it            = deconstructor.deconstruct(x.asInstanceOf[Col[Elem]])
          if (it.isEmpty) errors.addOne(new OpticCheck.EmptySequence(toDynamic, toDynamic(idx)))
          else if (idx + 1 != bindings.length) {
            while (it.hasNext) checkRecursive(registers, idx + 1, it.next(), errors)
          }
        case mapKeyBinding: MapKeyBinding[Map] @scala.unchecked =>
          val deconstructor = mapKeyBinding.mapDeconstructor
          val it            = deconstructor.deconstruct(x.asInstanceOf[Map[Key, Value]])
          if (it.isEmpty) errors.addOne(new OpticCheck.EmptyMap(toDynamic, toDynamic(idx)))
          else if (idx + 1 != bindings.length) {
            while (it.hasNext) checkRecursive(registers, idx + 1, deconstructor.getKey(it.next()), errors)
          }
        case binding =>
          val mapValueBinding = binding.asInstanceOf[MapValueBinding[Map]]
          val deconstructor   = mapValueBinding.mapDeconstructor
          val it              = deconstructor.deconstruct(x.asInstanceOf[Map[Key, Value]])
          if (it.isEmpty) errors.addOne(new OpticCheck.EmptyMap(toDynamic, toDynamic(idx)))
          else if (idx + 1 != bindings.length) {
            while (it.hasNext) checkRecursive(registers, idx + 1, deconstructor.getValue(it.next()), errors)
          }
      }

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z): Z = {
      if (bindings eq null) init()
      foldRecursive(Registers(usedRegisters), 0, s, zero, f)
    }

    private[this] def foldRecursive[Z](registers: Registers, idx: Int, x: Any, zero: Z, f: (Z, A) => Z): Z =
      bindings(idx) match {
        case lensBinding: LensBinding =>
          val offset = lensBinding.offset
          lensBinding.deconstructor.deconstruct(registers, offset, x)
          val x1 = lensBinding.register.get(registers, offset)
          if (idx + 1 == bindings.length) f(zero, x1.asInstanceOf[A])
          else foldRecursive(registers, idx + 1, x1, zero, f)
        case prismBinding: PrismBinding =>
          val x1 = prismBinding.matcher.downcastOrNull(x)
          if (x1 == null) zero
          else if (idx + 1 == bindings.length) f(zero, x1.asInstanceOf[A])
          else foldRecursive(registers, idx + 1, x1, zero, f)
        case wrapperBinding: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
          try {
            val x1 = wrapperBinding.unwrap(x.asInstanceOf[Wrapping])
            if (idx + 1 == bindings.length) f(zero, x1.asInstanceOf[A])
            else foldRecursive(registers, idx + 1, x1, zero, f)
          } catch {
            case _: SchemaError => zero
          }
        case atBinding: AtBinding[Col] @scala.unchecked =>
          val deconstructor = atBinding.seqDeconstructor
          val col           = x.asInstanceOf[Col[A]]
          deconstructor match {
            case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
              val colSize = indexed.size(col)
              val colIdx  = atBinding.index
              if (colSize <= colIdx) zero
              else if (idx + 1 == bindings.length) {
                f(
                  zero,
                  (
                    indexed.elementType(col) match {
                      case _: RegisterType.Boolean.type => indexed.booleanAt(col.asInstanceOf[Col[Boolean]], colIdx)
                      case _: RegisterType.Byte.type    => indexed.byteAt(col.asInstanceOf[Col[Byte]], colIdx)
                      case _: RegisterType.Char.type    => indexed.charAt(col.asInstanceOf[Col[Char]], colIdx)
                      case _: RegisterType.Short.type   => indexed.shortAt(col.asInstanceOf[Col[Short]], colIdx)
                      case _: RegisterType.Float.type   => indexed.floatAt(col.asInstanceOf[Col[Float]], colIdx)
                      case _: RegisterType.Int.type     => indexed.intAt(col.asInstanceOf[Col[Int]], colIdx)
                      case _: RegisterType.Double.type  => indexed.doubleAt(col.asInstanceOf[Col[Double]], colIdx)
                      case _: RegisterType.Long.type    => indexed.longAt(col.asInstanceOf[Col[Long]], colIdx)
                      case _                            => indexed.objectAt(col, colIdx)
                    }
                  ).asInstanceOf[A]
                )
              } else foldRecursive(registers, idx + 1, indexed.objectAt(col, colIdx), zero, f)
            case _ =>
              val it      = deconstructor.deconstruct(col)
              val colIdx  = atBinding.index
              var currIdx = 0
              while (currIdx < colIdx && it.hasNext) {
                it.next(): Unit
                currIdx += 1
              }
              if (currIdx != colIdx || !it.hasNext) zero
              else {
                val value = it.next()
                if (idx + 1 == bindings.length) f(zero, value)
                else foldRecursive(registers, idx + 1, value, zero, f)
              }
          }
        case atKeyBinding: AtKeyBinding[Key, Map] @scala.unchecked =>
          val deconstructor = atKeyBinding.mapDeconstructor
          val key           = atKeyBinding.key
          deconstructor.get(x.asInstanceOf[Map[Key, Value]], key) match {
            case Some(value) =>
              if (idx + 1 == bindings.length) f(zero, value.asInstanceOf[A])
              else foldRecursive(registers, idx + 1, value, zero, f)
            case _ =>
              zero
          }
        case atIndicesBinding: AtIndicesBinding[Col] @scala.unchecked =>
          val deconstructor = atIndicesBinding.seqDeconstructor
          val indices       = atIndicesBinding.indices
          val col           = x.asInstanceOf[Col[A]]
          if (idx + 1 == bindings.length) foldAtIndices(indices, deconstructor, col, zero, f)
          else {
            var z          = zero
            var indicesIdx = 0
            deconstructor match {
              case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
                val colSize = indexed.size(col)
                while (indicesIdx < indices.length) {
                  val colIdx = indices(indicesIdx)
                  if (colSize > colIdx) z = foldRecursive(registers, idx + 1, indexed.objectAt(col, colIdx), z, f)
                  indicesIdx += 1
                }
              case _ =>
                val it      = deconstructor.deconstruct(col)
                var currIdx = 0
                while (indicesIdx < indices.length) {
                  val colIdx = indices(indicesIdx)
                  while (currIdx < colIdx && it.hasNext) {
                    it.next(): Unit
                    currIdx += 1
                  }
                  if (currIdx == colIdx && it.hasNext) {
                    z = foldRecursive(registers, idx + 1, it.next(), z, f)
                    currIdx += 1
                  }
                  indicesIdx += 1
                }
            }
            z
          }
        case atKeysBinding: AtKeysBinding[Key, Map] @scala.unchecked =>
          var z             = zero
          val deconstructor = atKeysBinding.mapDeconstructor
          val m             = x.asInstanceOf[Map[Key, Value]]
          if (idx + 1 == bindings.length) {
            atKeysBinding.keys.foreach { key =>
              deconstructor.get(m, key) match {
                case Some(value) => z = f(z, value.asInstanceOf[A])
                case _           =>
              }
            }
          } else {
            atKeysBinding.keys.foreach { key =>
              deconstructor.get(m, key) match {
                case Some(value) => z = foldRecursive(registers, idx + 1, value, z, f)
                case _           =>
              }
            }
          }
          z
        case seqBinding: SeqBinding[Col] @scala.unchecked =>
          val deconstructor = seqBinding.seqDeconstructor
          if (idx + 1 == bindings.length) foldSeq(deconstructor, x.asInstanceOf[Col[A]], zero, f)
          else {
            val it = deconstructor.deconstruct(x.asInstanceOf[Col[Elem]])
            var z  = zero
            while (it.hasNext) z = foldRecursive(registers, idx + 1, it.next(), z, f)
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
            while (it.hasNext) z = foldRecursive(registers, idx + 1, deconstructor.getKey(it.next()), z, f)
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
            while (it.hasNext) z = foldRecursive(registers, idx + 1, deconstructor.getValue(it.next()), z, f)
          }
          z
      }

    private[this] def foldAtIndices[Z](
      indices: Array[Int],
      deconstructor: SeqDeconstructor[Col],
      x: Col[A],
      zero: Z,
      f: (Z, A) => Z
    ): Z =
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
          val colSize    = indexed.size(x)
          var indicesIdx = 0
          indexed.elementType(x) match {
            case _: RegisterType.Int.type =>
              val col = x.asInstanceOf[Col[Int]]
              zero match {
                case zi: Int =>
                  var z: Int = zi
                  val sf     = f.asInstanceOf[(Int, Int) => Int]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.intAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  var z: Long = zl
                  val sf      = f.asInstanceOf[(Long, Int) => Long]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.intAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  var z: Double = zd
                  val sf        = f.asInstanceOf[(Double, Int) => Double]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.intAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  var z  = zero
                  val sf = f.asInstanceOf[(Z, Int) => Z]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.intAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z
              }
            case _: RegisterType.Long.type =>
              val col = x.asInstanceOf[Col[Long]]
              zero match {
                case zi: Int =>
                  var z: Int = zi
                  val sf     = f.asInstanceOf[(Int, Long) => Int]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.longAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  var z: Long = zl
                  val sf      = f.asInstanceOf[(Long, Long) => Long]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.longAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  var z: Double = zd
                  val sf        = f.asInstanceOf[(Double, Long) => Double]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.longAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  var z  = zero
                  val sf = f.asInstanceOf[(Z, Long) => Z]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.longAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z
              }
            case _: RegisterType.Double.type =>
              val col = x.asInstanceOf[Col[Double]]
              zero match {
                case zi: Int =>
                  var z: Int = zi
                  val sf     = f.asInstanceOf[(Int, Double) => Int]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.doubleAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  var z: Long = zl
                  val sf      = f.asInstanceOf[(Long, Double) => Long]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.doubleAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  var z: Double = zd
                  val sf        = f.asInstanceOf[(Double, Double) => Double]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.doubleAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  var z  = zero
                  val sf = f.asInstanceOf[(Z, Double) => Z]
                  while (indicesIdx < indices.length) {
                    val colIdx = indices(indicesIdx)
                    if (colSize > colIdx) z = sf(z, indexed.doubleAt(col, colIdx))
                    indicesIdx += 1
                  }
                  z
              }
            case _: RegisterType.Boolean.type =>
              val col = x.asInstanceOf[Col[Boolean]]
              val sf  = f.asInstanceOf[(Z, Boolean) => Z]
              var z   = zero
              while (indicesIdx < indices.length) {
                val colIdx = indices(indicesIdx)
                if (colSize > colIdx) z = sf(z, indexed.booleanAt(col, colIdx))
                indicesIdx += 1
              }
              z
            case _: RegisterType.Byte.type =>
              val col = x.asInstanceOf[Col[Byte]]
              val sf  = f.asInstanceOf[(Z, Byte) => Z]
              var z   = zero
              while (indicesIdx < indices.length) {
                val colIdx = indices(indicesIdx)
                if (colSize > colIdx) z = sf(z, indexed.byteAt(col, colIdx))
                indicesIdx += 1
              }
              z
            case _: RegisterType.Short.type =>
              var z   = zero
              val col = x.asInstanceOf[Col[Short]]
              val sf  = f.asInstanceOf[(Z, Short) => Z]
              while (indicesIdx < indices.length) {
                val colIdx = indices(indicesIdx)
                if (colSize > colIdx) z = sf(z, indexed.shortAt(col, colIdx))
                indicesIdx += 1
              }
              z
            case _: RegisterType.Float.type =>
              var z   = zero
              val col = x.asInstanceOf[Col[Float]]
              val sf  = f.asInstanceOf[(Z, Float) => Z]
              while (indicesIdx < indices.length) {
                val colIdx = indices(indicesIdx)
                if (colSize > colIdx) z = sf(z, indexed.floatAt(col, colIdx))
                indicesIdx += 1
              }
              z
            case _: RegisterType.Char.type =>
              var z   = zero
              val col = x.asInstanceOf[Col[Char]]
              val sf  = f.asInstanceOf[(Z, Char) => Z]
              while (indicesIdx < indices.length) {
                val colIdx = indices(indicesIdx)
                if (colSize > colIdx) z = sf(z, indexed.charAt(col, colIdx))
                indicesIdx += 1
              }
              z
            case _ =>
              var z = zero
              while (indicesIdx < indices.length) {
                val colIdx = indices(indicesIdx)
                if (colSize > colIdx) z = f(z, indexed.objectAt(x, colIdx))
                indicesIdx += 1
              }
              z
          }
        case _ =>
          val it                  = deconstructor.deconstruct(x)
          var z                   = zero
          var currIdx, indicesIdx = 0
          while (indicesIdx < indices.length) {
            val colIdx = indices(indicesIdx)
            while (currIdx < colIdx && it.hasNext) {
              it.next(): Unit
              currIdx += 1
            }
            if (currIdx == colIdx && it.hasNext) {
              z = f(z, it.next())
              currIdx += 1
            }
            indicesIdx += 1
          }
          z
      }

    private[this] def foldSeq[Z](deconstructor: SeqDeconstructor[Col], x: Col[A], zero: Z, f: (Z, A) => Z): Z =
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
          val size    = indexed.size(x)
          var currIdx = 0
          indexed.elementType(x) match {
            case _: RegisterType.Int.type =>
              val col = x.asInstanceOf[Col[Int]]
              zero match {
                case zi: Int =>
                  val sf     = f.asInstanceOf[(Int, Int) => Int]
                  var z: Int = zi
                  while (currIdx < size) {
                    z = sf(z, indexed.intAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  val sf      = f.asInstanceOf[(Long, Int) => Long]
                  var z: Long = zl
                  while (currIdx < size) {
                    z = sf(z, indexed.intAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  val sf        = f.asInstanceOf[(Double, Int) => Double]
                  var z: Double = zd
                  while (currIdx < size) {
                    z = sf(z, indexed.intAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  val sf = f.asInstanceOf[(Z, Int) => Z]
                  var z  = zero
                  while (currIdx < size) {
                    z = sf(z, indexed.intAt(col, currIdx))
                    currIdx += 1
                  }
                  z
              }
            case _: RegisterType.Long.type =>
              val col = x.asInstanceOf[Col[Long]]
              zero match {
                case zi: Int =>
                  val sf     = f.asInstanceOf[(Int, Long) => Int]
                  var z: Int = zi
                  while (currIdx < size) {
                    z = sf(z, indexed.longAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  val sf      = f.asInstanceOf[(Long, Long) => Long]
                  var z: Long = zl
                  while (currIdx < size) {
                    z = sf(z, indexed.longAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  val sf        = f.asInstanceOf[(Double, Long) => Double]
                  var z: Double = zd
                  while (currIdx < size) {
                    z = sf(z, indexed.longAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  val sf = f.asInstanceOf[(Z, Long) => Z]
                  var z  = zero
                  while (currIdx < size) {
                    z = sf(z, indexed.longAt(col, currIdx))
                    currIdx += 1
                  }
                  z
              }
            case _: RegisterType.Double.type =>
              val col = x.asInstanceOf[Col[Double]]
              zero match {
                case zi: Int =>
                  val sf     = f.asInstanceOf[(Int, Double) => Int]
                  var z: Int = zi
                  while (currIdx < size) {
                    z = sf(z, indexed.doubleAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zl: Long =>
                  val sf      = f.asInstanceOf[(Long, Double) => Long]
                  var z: Long = zl
                  while (currIdx < size) {
                    z = sf(z, indexed.doubleAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case zd: Double =>
                  val sf        = f.asInstanceOf[(Double, Double) => Double]
                  var z: Double = zd
                  while (currIdx < size) {
                    z = sf(z, indexed.doubleAt(col, currIdx))
                    currIdx += 1
                  }
                  z.asInstanceOf[Z]
                case _ =>
                  val sf = f.asInstanceOf[(Z, Double) => Z]
                  var z  = zero
                  while (currIdx < size) {
                    z = sf(z, indexed.doubleAt(col, currIdx))
                    currIdx += 1
                  }
                  z
              }
            case _: RegisterType.Boolean.type =>
              val col = x.asInstanceOf[Col[Boolean]]
              val sf  = f.asInstanceOf[(Z, Boolean) => Z]
              var z   = zero
              while (currIdx < size) {
                z = sf(z, indexed.booleanAt(col, currIdx))
                currIdx += 1
              }
              z
            case _: RegisterType.Byte.type =>
              val col = x.asInstanceOf[Col[Byte]]
              val sf  = f.asInstanceOf[(Z, Byte) => Z]
              var z   = zero
              while (currIdx < size) {
                z = sf(z, indexed.byteAt(col, currIdx))
                currIdx += 1
              }
              z
            case _: RegisterType.Short.type =>
              val col = x.asInstanceOf[Col[Short]]
              val sf  = f.asInstanceOf[(Z, Short) => Z]
              var z   = zero
              while (currIdx < size) {
                z = sf(z, indexed.shortAt(col, currIdx))
                currIdx += 1
              }
              z
            case _: RegisterType.Float.type =>
              val col = x.asInstanceOf[Col[Float]]
              val sf  = f.asInstanceOf[(Z, Float) => Z]
              var z   = zero
              while (currIdx < size) {
                z = sf(z, indexed.floatAt(col, currIdx))
                currIdx += 1
              }
              z
            case _: RegisterType.Char.type =>
              val col = x.asInstanceOf[Col[Char]]
              val sf  = f.asInstanceOf[(Z, Char) => Z]
              var z   = zero
              while (currIdx < size) {
                z = sf(z, indexed.charAt(col, currIdx))
                currIdx += 1
              }
              z
            case _ =>
              var z = zero
              while (currIdx < size) {
                z = f(z, indexed.objectAt(x, currIdx))
                currIdx += 1
              }
              z
          }
        case _ =>
          val it = deconstructor.deconstruct(x)
          var z  = zero
          while (it.hasNext) z = f(z, it.next())
          z
      }

    def modify(s: S, f: A => A): S = {
      if (bindings eq null) init()
      try modifyRecursive(Registers(usedRegisters), 0, s, f).asInstanceOf[S]
      catch {
        case _: OpticCheckBuilder => s
      }
    }

    private[this] def modifyRecursive(registers: Registers, idx: Int, x: Any, f: A => A): Any =
      bindings(idx) match {
        case lensBinding: LensBinding =>
          val offset = lensBinding.offset
          lensBinding.deconstructor.deconstruct(registers, offset, x)
          var x1 = lensBinding.register.get(registers, offset)
          if (idx + 1 == bindings.length) x1 = f(x1.asInstanceOf[A])
          else x1 = modifyRecursive(registers, idx + 1, x1, f)
          lensBinding.register.set(registers, offset, x1)
          lensBinding.constructor.construct(registers, offset)
        case prismBinding: PrismBinding =>
          val x1 = prismBinding.matcher.downcastOrNull(x)
          if (x1 == null) x
          else if (idx + 1 == bindings.length) f(x1.asInstanceOf[A])
          else modifyRecursive(registers, idx + 1, x1, f)
        case wrapperBinding: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
          val x1 =
            try wrapperBinding.unwrap(x.asInstanceOf[Wrapping])
            catch {
              case error: SchemaError => throw toOpticCheckBuilder(idx, error)
            }
          try
            wrapperBinding.wrap({
              if (idx + 1 == bindings.length) f(x1.asInstanceOf[A])
              else modifyRecursive(registers, idx + 1, x1, f)
            }.asInstanceOf[Wrapped])
          catch {
            case error: SchemaError => throw toOpticCheckBuilder(idx, error)
          }
        case atBinding: AtBinding[Col] @scala.unchecked =>
          val deconstructor = atBinding.seqDeconstructor
          val constructor   = atBinding.seqConstructor
          val colIdx        = atBinding.index
          val col           = x.asInstanceOf[Col[A]]
          if (idx + 1 == bindings.length)
            modifySeqAt(deconstructor, constructor, col, f, colIdx, atBinding.elemClassTag)
          else {
            val sizeHint =
              deconstructor match {
                case indexed: SeqDeconstructor.SpecializedIndexed[Col] => indexed.size(col)
                case _                                                 => 8
              }
            implicit val classTag: ClassTag[Any] = atBinding.elemClassTag.asInstanceOf[ClassTag[Any]]
            val builder                          = constructor.newBuilder[Any](sizeHint)
            val it                               = deconstructor.deconstruct(col)
            var currIdx                          = 0
            while (it.hasNext) {
              constructor.add(
                builder, {
                  val value = it.next()
                  if (currIdx != colIdx) value
                  else modifyRecursive(registers, idx + 1, value, f)
                }
              )
              currIdx += 1
            }
            constructor.result(builder)
          }
        case atKeyBinding: AtKeyBinding[Key, Map] @scala.unchecked =>
          val deconstructor = atKeyBinding.mapDeconstructor
          val constructor   = atKeyBinding.mapConstructor
          val map           = x.asInstanceOf[Map[Key, Any]]
          val key           = atKeyBinding.key
          deconstructor.get(map, key) match {
            case Some(value) =>
              constructor.updated(
                map,
                key,
                if (idx + 1 == bindings.length) f(value.asInstanceOf[A])
                else modifyRecursive(registers, idx + 1, value, f)
              )
            case _ => map
          }
        case atIndicesBinding: AtIndicesBinding[Col] @scala.unchecked =>
          val deconstructor = atIndicesBinding.seqDeconstructor
          val constructor   = atIndicesBinding.seqConstructor
          val indices       = atIndicesBinding.indices
          val col           = x.asInstanceOf[Col[A]]
          if (idx + 1 == bindings.length)
            modifySeqAtIndices(indices, deconstructor, constructor, col, f, atIndicesBinding.elemClassTag)
          else {
            val sizeHint =
              deconstructor match {
                case indexed: SeqDeconstructor.SpecializedIndexed[Col] => indexed.size(col)
                case _                                                 => 8
              }
            val builder             = constructor.newBuilder[Any](sizeHint)
            val it                  = deconstructor.deconstruct(col)
            var colIdx              = indices(0)
            var currIdx, indicesIdx = 0
            while (it.hasNext) {
              constructor.add(
                builder, {
                  val value = it.next()
                  if (currIdx != colIdx) value
                  else {
                    indicesIdx += 1
                    if (indicesIdx < indices.length) colIdx = indices(indicesIdx)
                    modifyRecursive(registers, idx + 1, value, f)
                  }
                }
              )
              currIdx += 1
            }
            constructor.result(builder)
          }
        case atKeysBinding: AtKeysBinding[Key, Map] @scala.unchecked =>
          val deconstructor = atKeysBinding.mapDeconstructor
          val constructor   = atKeysBinding.mapConstructor
          var map           = x.asInstanceOf[Map[Key, Any]]
          if (idx + 1 == bindings.length) {
            atKeysBinding.keys.foreach { key =>
              deconstructor.get(map, key) match {
                case Some(value) => map = constructor.updated(map, key, f(value.asInstanceOf[A]))
                case _           =>
              }
            }
          } else {
            atKeysBinding.keys.foreach { key =>
              deconstructor.get(map, key) match {
                case Some(value) => map = constructor.updated(map, key, modifyRecursive(registers, idx + 1, value, f))
                case _           =>
              }
            }
          }
          map
        case seqBinding: SeqBinding[Col] @scala.unchecked =>
          val deconstructor = seqBinding.seqDeconstructor
          val constructor   = seqBinding.seqConstructor
          val col           = x.asInstanceOf[Col[A]]
          if (idx + 1 == bindings.length) modifySeq(deconstructor, constructor, col, f, seqBinding.elemClassTag)
          else {
            val sizeHint =
              deconstructor match {
                case indexed: SeqDeconstructor.SpecializedIndexed[Col] => indexed.size(col)
                case _                                                 => 8
              }
            implicit val classTag: ClassTag[Any] = seqBinding.elemClassTag.asInstanceOf[ClassTag[Any]]
            val builder                          = constructor.newBuilder[Any](sizeHint)
            val it                               = deconstructor.deconstruct(col)
            while (it.hasNext) constructor.add(builder, modifyRecursive(registers, idx + 1, it.next(), f))
            constructor.result(builder)
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
                modifyRecursive(registers, idx + 1, deconstructor.getKey(next), f),
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
                modifyRecursive(registers, idx + 1, deconstructor.getValue(next), f)
              )
            }
            constructor.resultObject(builder)
          }
      }

    private[this] def modifySeqAt(
      deconstructor: SeqDeconstructor[Col],
      constructor: SeqConstructor[Col],
      x: Col[A],
      f: A => A,
      colIdx: Int,
      elemClassTag: ClassTag[?]
    ): Col[A] = {
      implicit val classTag: ClassTag[A] = elemClassTag.asInstanceOf[ClassTag[A]]
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
          val size    = indexed.size(x)
          val builder = constructor.newBuilder[A](size)
          var currIdx = 0
          while (currIdx < size) {
            constructor.add(
              builder, {
                val value = indexed.objectAt(x, currIdx)
                if (currIdx != colIdx) value
                else f(value)
              }
            )
            currIdx += 1
          }
          constructor.result(builder)
        case _ =>
          val builder = constructor.newBuilder[A]()
          val it      = deconstructor.deconstruct(x)
          var currIdx = -1
          while (it.hasNext)
            constructor.add(
              builder, {
                currIdx += 1
                val value = it.next()
                if (currIdx != colIdx) value
                else f(value)
              }
            )
          constructor.result(builder)
      }
    }

    private[this] def modifySeqAtIndices(
      indices: Array[Int],
      deconstructor: SeqDeconstructor[Col],
      constructor: SeqConstructor[Col],
      x: Col[A],
      f: A => A,
      elemClassTag: ClassTag[?]
    ): Col[A] = {
      implicit val classTag: ClassTag[A] = elemClassTag.asInstanceOf[ClassTag[A]]
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
          val size                = indexed.size(x)
          var colIdx              = indices(0)
          var currIdx, indicesIdx = 0
          val builder             = constructor.newBuilder[A](size)
          while (currIdx < size) {
            constructor.add(
              builder, {
                val value = indexed.objectAt(x, currIdx)
                if (currIdx != colIdx) value
                else {
                  indicesIdx += 1
                  if (indicesIdx < indices.length) colIdx = indices(indicesIdx)
                  f(value)
                }
              }
            )
            currIdx += 1
          }
          constructor.result(builder)
        case _ =>
          val builder             = constructor.newBuilder[A]()
          val it                  = deconstructor.deconstruct(x)
          var colIdx              = indices(0)
          var currIdx, indicesIdx = 0
          while (it.hasNext) {
            constructor.add(
              builder, {
                val value = it.next()
                if (currIdx != colIdx) value
                else {
                  indicesIdx += 1
                  if (indicesIdx < indices.length) colIdx = indices(indicesIdx)
                  f(value)
                }
              }
            )
            currIdx += 1
          }
          constructor.result(builder)
      }
    }

    private[this] def modifySeq(
      deconstructor: SeqDeconstructor[Col],
      constructor: SeqConstructor[Col],
      x: Col[A],
      f: A => A,
      elemClassTag: ClassTag[?]
    ): Col[A] = {
      implicit val classTag: ClassTag[A] = elemClassTag.asInstanceOf[ClassTag[A]]
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
          val size    = indexed.size(x)
          val builder = constructor.newBuilder[A](size)
          var currIdx = 0
          while (currIdx < size) {
            constructor.add(builder, f(indexed.objectAt(x, currIdx)))
            currIdx += 1
          }
          constructor.result(builder)
        case _ =>
          val builder = constructor.newBuilder[A]()
          val it      = deconstructor.deconstruct(x)
          while (it.hasNext) constructor.add(builder, f(it.next()))
          constructor.result(builder)
      }
    }

    def modifyOption(s: S, f: A => A): Option[S] = {
      if (bindings eq null) init()
      try {
        var modified = false
        val x        = modifyRecursive(
          Registers(usedRegisters),
          0,
          s,
          (a: A) => {
            modified = true
            f(a)
          }
        )
        if (modified) new Some(x.asInstanceOf[S])
        else None
      } catch {
        case _: OpticCheckBuilder => None
      }
    }

    def modifyOrFail(s: S, f: A => A): Either[OpticCheck, S] = {
      if (bindings eq null) init()
      try {
        var modified = false
        val x        = modifyRecursive(
          Registers(usedRegisters),
          0,
          s,
          (a: A) => {
            modified = true
            f(a)
          }
        )
        if (modified) new Right(x.asInstanceOf[S])
        else new Left(check(s).get)
      } catch {
        case ocb: OpticCheckBuilder => new Left(ocb.toOpticCheck())
      }
    }

    private[this] def toOpticCheckBuilder(idx: Int, error: SchemaError): OpticCheckBuilder =
      new OpticCheckBuilder(toOpticCheck =
        () => new OpticCheck(new ::(new OpticCheck.WrappingError(toDynamic, toDynamic(idx), error), Nil))
      )

    lazy val toDynamic: DynamicOptic = new DynamicOptic({
      if (bindings eq null) init()
      val nodes = Vector.newBuilder[DynamicOptic.Node]
      val len   = bindings.length
      var idx   = 0
      while (idx < len) {
        nodes.addOne {
          bindings(idx) match {
            case _: LensBinding =>
              new DynamicOptic.Node.Field(focusTerms(idx).name)
            case _: PrismBinding =>
              new DynamicOptic.Node.Case(focusTerms(idx).name)
            case _: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
              DynamicOptic.Node.Wrapped
            case at: AtBinding[Col] @scala.unchecked =>
              new DynamicOptic.Node.AtIndex(at.index)
            case atKey: AtKeyBinding[Key, Map] @scala.unchecked =>
              new DynamicOptic.Node.AtMapKey(atKey.keySchema.toDynamicValue(atKey.key))
            case atIndices: AtIndicesBinding[Col] @scala.unchecked =>
              new DynamicOptic.Node.AtIndices(ArraySeq.unsafeWrapArray(atIndices.indices))
            case atKeys: AtKeysBinding[Key, Map] @scala.unchecked =>
              new DynamicOptic.Node.AtMapKeys(atKeys.keys.map(atKeys.keySchema.toDynamicValue))
            case _: SeqBinding[Col] @scala.unchecked =>
              DynamicOptic.Node.Elements
            case _: MapKeyBinding[Map] @scala.unchecked =>
              DynamicOptic.Node.MapKeys
            case _ =>
              DynamicOptic.Node.MapValues
          }
        }
        idx += 1
      }
      nodes.result()
    })

    override def toString: String = {
      if (bindings eq null) init()
      val sb  = new StringBuilder
      val len = bindings.length
      var idx = 0
      while (idx < len) {
        bindings(idx) match {
          case _: LensBinding =>
            sb.append('.').append(focusTerms(idx).name)
          case _: PrismBinding =>
            sb.append(".when[").append(focusTerms(idx).name).append(']')
          case _: WrappedBinding[Wrapping, Wrapped] @scala.unchecked =>
            sb.append(".wrapped[").append(focus.typeId.name).append(']')
          case at: AtBinding[Col] @scala.unchecked =>
            sb.append(".at(").append(at.index).append(')')
          case _: AtKeyBinding[Key, Map] @scala.unchecked =>
            sb.append(".atKey(<key>)")
          case _: AtIndicesBinding[Col] @scala.unchecked =>
            sb.append(".atIndices(<indices>)")
          case _: AtKeysBinding[Key, Map] @scala.unchecked =>
            sb.append(".atKeys(<keys>)")
          case _: SeqBinding[Col] @scala.unchecked =>
            sb.append(".each")
          case _: MapKeyBinding[Map] @scala.unchecked =>
            sb.append(".eachKey")
          case _ =>
            sb.append(".eachValue")
        }
        idx += 1
      }
      s"Traversal(_${sb.toString})"
    }

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(params.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: TraversalImpl[?, ?] =>
        java.util.Arrays.equals(other.sources.asInstanceOf[Array[AnyRef]], sources.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.focusTerms.asInstanceOf[Array[AnyRef]], focusTerms.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.params.asInstanceOf[Array[AnyRef]], params.asInstanceOf[Array[AnyRef]])
      case _ => false
    }
  }
}

private[schema] sealed trait OpticBinding

private[schema] case class LensBinding(
  offset: RegisterOffset,
  deconstructor: Deconstructor[Any],
  constructor: Constructor[Any],
  register: Register[Any]
) extends OpticBinding

private[schema] case class PrismBinding(
  matcher: Matcher[Any],
  discriminator: Discriminator[Any]
) extends OpticBinding

private[schema] case class SeqBinding[C[_]](
  seqDeconstructor: SeqDeconstructor[C],
  seqConstructor: SeqConstructor[C],
  elemClassTag: ClassTag[?]
) extends OpticBinding

private[schema] case class MapKeyBinding[M[_, _]](
  mapDeconstructor: MapDeconstructor[M],
  mapConstructor: MapConstructor[M]
) extends OpticBinding

private[schema] case class MapValueBinding[M[_, _]](
  mapDeconstructor: MapDeconstructor[M],
  mapConstructor: MapConstructor[M]
) extends OpticBinding

private[schema] case class AtBinding[C[_]](
  seqDeconstructor: SeqDeconstructor[C],
  seqConstructor: SeqConstructor[C],
  index: Int,
  elemClassTag: ClassTag[?]
) extends OpticBinding

private[schema] case class AtKeyBinding[K, M[_, _]](
  mapDeconstructor: MapDeconstructor[M],
  mapConstructor: MapConstructor[M],
  keySchema: Reflect.Bound[K],
  key: K
) extends OpticBinding

private[schema] case class AtIndicesBinding[C[_]](
  seqDeconstructor: SeqDeconstructor[C],
  seqConstructor: SeqConstructor[C],
  indices: Array[Int],
  elemClassTag: ClassTag[?]
) extends OpticBinding

private[schema] case class AtKeysBinding[K, M[_, _]](
  mapDeconstructor: MapDeconstructor[M],
  mapConstructor: MapConstructor[M],
  keySchema: Reflect.Bound[K],
  keys: Seq[K]
) extends OpticBinding

private[schema] case class WrappedBinding[A, B](
  wrap: B => A,
  unwrap: A => B
) extends OpticBinding

private[schema] case class OpticCheckBuilder(toOpticCheck: () => OpticCheck) extends Exception with NoStackTrace
