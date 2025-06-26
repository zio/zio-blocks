package zio.blocks.schema

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._
import scala.collection.immutable.ArraySeq
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
    if (modified) new Some(result)
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

  final def arraySeqValues[B](implicit ev: A =:= ArraySeq[B]): Traversal[S, B] = {
    import Reflect.Extractors.ArraySeq

    val arraySeq = self.asEquivalent[ArraySeq[B]]
    arraySeq.focus match {
      case ArraySeq(element) => arraySeq(Traversal.arraySeqValues(element))
      case _                 => sys.error("Expected ArraySeq")
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

  final def &&(that: Boolean)(implicit schema: Schema[A], ev: A =:= Boolean): SchemaExpr[S, Boolean] =
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

  final def ||(that: Boolean)(implicit schema: Schema[A], ev: A =:= Boolean): SchemaExpr[S, Boolean] =
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

    private[this] def init(): Unit = {
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
          register = source.registers(source.fieldIndexByName(focusTermName)).asInstanceOf[Register[Any]],
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

    lazy val toDynamic: DynamicOptic =
      new DynamicOptic(focusTerms.map(term => new DynamicOptic.Node.Field(term.name)).toVector)

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
        if (x == null) {
          val actualCaseIdx = discriminators(idx).discriminate(lastX)
          val actualCase    = sources(idx).cases(actualCaseIdx).name
          val focusTermName = focusTerms(idx).name
          val unexpectedCase =
            new OpticCheck.UnexpectedCase(focusTermName, actualCase, toDynamic, toDynamic(idx), lastX)
          return new Some(new OpticCheck(new ::(unexpectedCase, Nil)))
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

    lazy val toDynamic: DynamicOptic =
      new DynamicOptic(focusTerms.map(term => new DynamicOptic.Node.Case(term.name)).toVector)

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
    new OptionalImpl(
      optional1.sources ++ lens2.sources,
      optional1.focusTerms ++ lens2.focusTerms,
      optional1.params ++ new Array[Any](lens2.sources.length)
    )
  }

  def apply[S, T, A <: T](first: Optional[S, T], second: Prism[T, A]): Optional[S, A] = {
    val optional1 = first.asInstanceOf[OptionalImpl[_, _]]
    val prism2    = second.asInstanceOf[Prism.PrismImpl[_, _]]
    new OptionalImpl(
      optional1.sources ++ prism2.sources,
      optional1.focusTerms ++ prism2.focusTerms,
      optional1.params ++ new Array[Any](prism2.sources.length)
    )
  }

  def apply[S, T, A](first: Optional[S, T], second: Optional[T, A]): Optional[S, A] = {
    val optional1 = first.asInstanceOf[OptionalImpl[_, _]]
    val optional2 = second.asInstanceOf[OptionalImpl[_, _]]
    new OptionalImpl(
      optional1.sources ++ optional2.sources,
      optional1.focusTerms ++ optional2.focusTerms,
      optional1.params ++ optional2.params
    )
  }

  def apply[S, T, A <: T](first: Lens[S, T], second: Prism[T, A]): Optional[S, A] = {
    val lens1  = first.asInstanceOf[Lens.LensImpl[_, _]]
    val prism2 = second.asInstanceOf[Prism.PrismImpl[_, _]]
    new OptionalImpl(
      lens1.sources ++ prism2.sources,
      lens1.focusTerms ++ prism2.focusTerms,
      new Array[Any](lens1.sources.length + prism2.sources.length)
    )
  }

  def apply[S, T, A](first: Lens[S, T], second: Optional[T, A]): Optional[S, A] = {
    val lens1     = first.asInstanceOf[Lens.LensImpl[_, _]]
    val optional2 = second.asInstanceOf[OptionalImpl[_, _]]
    new OptionalImpl(
      lens1.sources ++ optional2.sources,
      lens1.focusTerms ++ optional2.focusTerms,
      new Array[Any](lens1.sources.length) ++ optional2.params
    )
  }

  def apply[S, T <: S, A](first: Prism[S, T], second: Lens[T, A]): Optional[S, A] = {
    val prism1 = first.asInstanceOf[Prism.PrismImpl[_, _]]
    val lens2  = second.asInstanceOf[Lens.LensImpl[_, _]]
    new OptionalImpl(
      prism1.sources ++ lens2.sources,
      prism1.focusTerms ++ lens2.focusTerms,
      new Array[Any](prism1.sources.length + lens2.sources.length)
    )
  }
  def apply[S, T <: S, A](first: Prism[S, T], second: Optional[T, A]): Optional[S, A] = {
    val prism1    = first.asInstanceOf[Prism.PrismImpl[_, _]]
    val optional2 = second.asInstanceOf[OptionalImpl[_, _]]
    new OptionalImpl(
      prism1.sources ++ optional2.sources,
      prism1.focusTerms ++ optional2.focusTerms,
      new Array[Any](prism1.sources.length) ++ optional2.params
    )
  }

  def at[A, C[_]](seq: Reflect.Sequence.Bound[A, C], index: Int): Optional[C[A], A] =
    new OptionalImpl(Array(seq), Array(seq.element.asTerm("at")), Array[Any](index))

  def atKey[Key, Value, M[_, _]](map: Reflect.Map.Bound[Key, Value, M], key: Key): Optional[M[Key, Value], Value] =
    new OptionalImpl(Array(map), Array(map.value.asTerm("atKey")), Array[Any](key))

  private[schema] case class OptionalImpl[S, A](
    sources: Array[Reflect.Bound[_]],
    focusTerms: Array[Term.Bound[_, _]],
    params: Array[Any]
  ) extends Optional[S, A] {
    private[this] var bindings: Array[OpticBinding] = null
    private[this] var usedRegisters: RegisterOffset = RegisterOffset.Zero

    type Key
    type Value
    type Map[_, _]
    type Elem
    type Col[_]

    private[this] def init(): Unit = {
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
              register = record.registers(record.fieldIndexByName(focusTermName)).asInstanceOf[Register[Any]],
              offset = offset
            )
            offset = RegisterOffset.add(offset, record.usedRegisters)
          case variant: Reflect.Variant.Bound[_] =>
            bindings(idx) = new PrismBinding(
              matcher = variant.matchers.apply(variant.caseIndexByName(focusTermName)),
              discriminator = variant.discriminator.asInstanceOf[Discriminator[Any]]
            )
          case sequence: Reflect.Sequence.Bound[Elem, Col] @scala.unchecked =>
            bindings(idx) = new AtBinding(
              seqDeconstructor = sequence.seqDeconstructor,
              seqConstructor = sequence.seqConstructor,
              index = params(idx).asInstanceOf[Int]
            )
          case source =>
            val map = source.asInstanceOf[Reflect.Map.Bound[Key, Value, Map]]
            bindings(idx) = new AtKeyBinding(
              mapDeconstructor = map.mapDeconstructor,
              mapConstructor = map.mapConstructor,
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
              val actualCaseIdx = prismBinding.discriminator.discriminate(lastX)
              val actualCase    = sources(idx).asInstanceOf[Reflect.Variant.Bound[Any]].cases(actualCaseIdx).name
              val focusTermName = focusTerms(idx).name
              val unexpectedCase =
                new OpticCheck.UnexpectedCase(focusTermName, actualCase, toDynamic, toDynamic(idx), lastX)
              return new Some(new OpticCheck(new ::(unexpectedCase, Nil)))
            }
          case atBinding: AtBinding[Col] @scala.unchecked =>
            val deconstructor = atBinding.seqDeconstructor
            val col           = x.asInstanceOf[Col[A]]
            deconstructor match {
              case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
                val colLen = indexed.length(col)
                val colIdx = atBinding.index
                if (colLen <= colIdx) {
                  val sequenceIndexOutOfBounds =
                    new OpticCheck.SequenceIndexOutOfBounds(toDynamic, toDynamic(idx), colIdx, colLen)
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
              case Some(v) =>
                x = v
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
          case atBinding: AtBinding[Col] @scala.unchecked =>
            val deconstructor = atBinding.seqDeconstructor
            val col           = x.asInstanceOf[Col[A]]
            deconstructor match {
              case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
                val colLen = indexed.length(col)
                val colIdx = atBinding.index
                if (colLen <= colIdx) return None
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
              case Some(v) => x = v
              case _       => return None
            }
        }
        idx += 1
      }
      new Some(x.asInstanceOf[A])
    }

    lazy val toDynamic: DynamicOptic = new DynamicOptic({
      if (bindings eq null) init()
      val nodes = Vector.newBuilder[DynamicOptic.Node]
      val len   = bindings.length
      var idx   = 0
      while (idx < len) {
        nodes.addOne {
          bindings(idx) match {
            case _: LensBinding                      => new DynamicOptic.Node.Field(focusTerms(idx).name)
            case _: PrismBinding                     => new DynamicOptic.Node.Case(focusTerms(idx).name)
            case at: AtBinding[Col] @scala.unchecked => new DynamicOptic.Node.AtIndex(at.index)
            case binding                             => new DynamicOptic.Node.AtMapKey[Key](binding.asInstanceOf[AtKeyBinding[Key, Map]].key)
          }
        }
        idx += 1
      }
      nodes.result()
    })

    def replace(s: S, a: A): S = {
      if (bindings eq null) init()
      modifyRec(Registers(usedRegisters), 0, s, _ => a).asInstanceOf[S]
    }

    def replaceOption(s: S, a: A): Option[S] = {
      if (bindings eq null) init()
      var success = false
      val x = modifyRec(
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
    }

    def modify(s: S, f: A => A): S = {
      if (bindings eq null) init()
      modifyRec(Registers(usedRegisters), 0, s, f).asInstanceOf[S]
    }

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
        case atBinding: AtBinding[Col] @scala.unchecked =>
          val deconstructor = atBinding.seqDeconstructor
          val constructor   = atBinding.seqConstructor
          val colIdx        = atBinding.index
          if (idx + 1 == bindings.length) modifySeqAt(deconstructor, constructor, x.asInstanceOf[Col[A]], f, colIdx)
          else {
            val builder = constructor.newObjectBuilder[Any]()
            val it      = deconstructor.deconstruct(x.asInstanceOf[Col[Any]])
            var currIdx = 0
            while (it.hasNext) {
              constructor.addObject(
                builder, {
                  val value = it.next()
                  if (currIdx == colIdx) modifyRec(registers, idx + 1, value, f)
                  else value
                }
              )
              currIdx += 1
            }
            constructor.resultObject(builder)
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
                else modifyRec(registers, idx + 1, value, f)
              )
            case _ => map
          }
      }

    private[this] def modifySeqAt(
      deconstructor: SeqDeconstructor[Col],
      constructor: SeqConstructor[Col],
      s: Col[A],
      f: A => A,
      colIdx: Int
    ): Col[A] =
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
          val len = indexed.length(s)
          indexed.elementType(s) match {
            case _: RegisterType.Boolean.type =>
              val builder = constructor.newBooleanBuilder(len)
              var idx     = 0
              while (idx < len) {
                constructor.addBoolean(
                  builder, {
                    val value = indexed.booleanAt(s.asInstanceOf[Col[Boolean]], idx)
                    if (idx == colIdx) f.asInstanceOf[Boolean => Boolean](value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultBoolean(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Byte.type =>
              val builder = constructor.newByteBuilder(len)
              var idx     = 0
              while (idx < len) {
                constructor.addByte(
                  builder, {
                    val value = indexed.byteAt(s.asInstanceOf[Col[Byte]], idx)
                    if (idx == colIdx) f.asInstanceOf[Byte => Byte](value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultByte(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Short.type =>
              val builder = constructor.newShortBuilder(len)
              var idx     = 0
              while (idx < len) {
                constructor.addShort(
                  builder, {
                    val value = indexed.shortAt(s.asInstanceOf[Col[Short]], idx)
                    if (idx == colIdx) f.asInstanceOf[Short => Short](value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultShort(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Int.type =>
              val builder = constructor.newIntBuilder(len)
              var idx     = 0
              while (idx < len) {
                constructor.addInt(
                  builder, {
                    val value = indexed.intAt(s.asInstanceOf[Col[Int]], idx)
                    if (idx == colIdx) f.asInstanceOf[Int => Int](value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultInt(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Long.type =>
              val builder = constructor.newLongBuilder(len)
              var idx     = 0
              while (idx < len) {
                constructor.addLong(
                  builder, {
                    val value = indexed.longAt(s.asInstanceOf[Col[Long]], idx)
                    if (idx == colIdx) f.asInstanceOf[Long => Long](value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultLong(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Float.type =>
              val builder = constructor.newFloatBuilder(len)
              var idx     = 0
              while (idx < len) {
                constructor.addFloat(
                  builder, {
                    val value = indexed.floatAt(s.asInstanceOf[Col[Float]], idx)
                    if (idx == colIdx) f.asInstanceOf[Float => Float](value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultFloat(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Double.type =>
              val builder = constructor.newDoubleBuilder(len)
              var idx     = 0
              while (idx < len) {
                constructor.addDouble(
                  builder, {
                    val value = indexed.doubleAt(s.asInstanceOf[Col[Double]], idx)
                    if (idx == colIdx) f.asInstanceOf[Double => Double](value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultDouble(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Char.type =>
              val builder = constructor.newCharBuilder(len)
              var idx     = 0
              while (idx < len) {
                constructor.addChar(
                  builder, {
                    val value = indexed.charAt(s.asInstanceOf[Col[Char]], idx)
                    if (idx == colIdx) f.asInstanceOf[Char => Char](value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultChar(builder).asInstanceOf[Col[A]]
            case _ =>
              val builder = constructor.newObjectBuilder[A](len)
              var idx     = 0
              while (idx < len) {
                constructor.addObject(
                  builder, {
                    val value = indexed.objectAt(s, idx)
                    if (idx == colIdx) f(value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultObject(builder)
          }
        case _ =>
          val builder = constructor.newObjectBuilder[A]()
          val it      = deconstructor.deconstruct(s)
          var currIdx = -1
          while (it.hasNext)
            constructor.addObject(
              builder, {
                currIdx += 1
                val value = it.next()
                if (currIdx == colIdx) f(value)
                else value
              }
            )
          constructor.resultObject(builder)
      }

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(params.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: OptionalImpl[_, _] =>
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
    var one = false
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
  def apply[S, T, A](first: Traversal[S, T], second: Traversal[T, A]): Traversal[S, A] = {
    val traversal1 = first.asInstanceOf[TraversalImpl[_, _]]
    val traversal2 = second.asInstanceOf[TraversalImpl[_, _]]
    new TraversalImpl(
      traversal1.sources ++ traversal2.sources,
      traversal1.focusTerms ++ traversal2.focusTerms,
      traversal1.params ++ traversal2.params
    )
  }

  def apply[S, T, A](first: Traversal[S, T], second: Lens[T, A]): Traversal[S, A] = {
    val traversal1 = first.asInstanceOf[TraversalImpl[_, _]]
    val lens2      = second.asInstanceOf[Lens.LensImpl[_, _]]
    new TraversalImpl(
      traversal1.sources ++ lens2.sources,
      traversal1.focusTerms ++ lens2.focusTerms,
      traversal1.params ++ new Array[Any](lens2.sources.length)
    )
  }

  def apply[S, T, A <: T](first: Traversal[S, T], second: Prism[T, A]): Traversal[S, A] = {
    val traversal1 = first.asInstanceOf[TraversalImpl[_, _]]
    val prism2     = second.asInstanceOf[Prism.PrismImpl[_, _]]
    new TraversalImpl(
      traversal1.sources ++ prism2.sources,
      traversal1.focusTerms ++ prism2.focusTerms,
      traversal1.params ++ new Array[Any](prism2.sources.length)
    )
  }

  def apply[S, T, A](first: Traversal[S, T], second: Optional[T, A]): Traversal[S, A] = {
    val traversal1 = first.asInstanceOf[TraversalImpl[_, _]]
    val optional2  = second.asInstanceOf[Optional.OptionalImpl[_, _]]
    new TraversalImpl(
      traversal1.sources ++ optional2.sources,
      traversal1.focusTerms ++ optional2.focusTerms,
      traversal1.params ++ optional2.params
    )
  }

  def apply[S, T, A](first: Lens[S, T], second: Traversal[T, A]): Traversal[S, A] = {
    val lens1      = first.asInstanceOf[Lens.LensImpl[_, _]]
    val traversal2 = second.asInstanceOf[TraversalImpl[_, _]]
    new TraversalImpl(
      lens1.sources ++ traversal2.sources,
      lens1.focusTerms ++ traversal2.focusTerms,
      new Array[Any](lens1.sources.length) ++ traversal2.params
    )
  }

  def apply[S, T <: S, A](first: Prism[S, T], second: Traversal[T, A]): Traversal[S, A] = {
    val prism1     = first.asInstanceOf[Prism.PrismImpl[_, _]]
    val traversal2 = second.asInstanceOf[TraversalImpl[_, _]]
    new TraversalImpl(
      prism1.sources ++ traversal2.sources,
      prism1.focusTerms ++ traversal2.focusTerms,
      new Array[Any](prism1.sources.length) ++ traversal2.params
    )
  }

  def apply[S, T, A](first: Optional[S, T], second: Traversal[T, A]): Traversal[S, A] = {
    val optional1  = first.asInstanceOf[Optional.OptionalImpl[_, _]]
    val traversal2 = second.asInstanceOf[TraversalImpl[_, _]]
    new TraversalImpl(
      optional1.sources ++ traversal2.sources,
      optional1.focusTerms ++ traversal2.focusTerms,
      optional1.params ++ traversal2.params
    )
  }

  def arraySeqValues[A](reflect: Reflect.Bound[A]): Traversal[ArraySeq[A], A] = seqValues(Reflect.arraySeq(reflect))

  def arrayValues[A](reflect: Reflect.Bound[A]): Traversal[Array[A], A] = seqValues(Reflect.array(reflect))

  def listValues[A](reflect: Reflect.Bound[A]): Traversal[List[A], A] = seqValues(Reflect.list(reflect))

  def mapKeys[Key, Value, M[_, _]](map: Reflect.Map.Bound[Key, Value, M]): Traversal[M[Key, Value], Key] =
    new TraversalImpl(Array(map), Array(map.key.asTerm("key")), Array[Any](null))

  def mapValues[Key, Value, M[_, _]](map: Reflect.Map.Bound[Key, Value, M]): Traversal[M[Key, Value], Value] =
    new TraversalImpl(Array(map), Array(map.value.asTerm("value")), Array[Any](null))

  def seqValues[A, C[_]](seq: Reflect.Sequence.Bound[A, C]): Traversal[C[A], A] =
    new TraversalImpl(Array(seq), Array(seq.element.asTerm("element")), Array[Any](null))

  def setValues[A](reflect: Reflect.Bound[A]): Traversal[Set[A], A] = seqValues(Reflect.set(reflect))

  def vectorValues[A](reflect: Reflect.Bound[A]): Traversal[Vector[A], A] = seqValues(Reflect.vector(reflect))

  private[schema] case class TraversalImpl[S, A](
    sources: Array[Reflect.Bound[_]],
    focusTerms: Array[Term.Bound[_, _]],
    params: Array[Any]
  ) extends Traversal[S, A] {
    private[this] var bindings: Array[OpticBinding] = null
    private[this] var usedRegisters: RegisterOffset = RegisterOffset.Zero

    type Key
    type Value
    type Map[_, _]
    type Elem
    type Col[_]

    private[this] def init(): Unit = {
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
              register = record.registers(record.fieldIndexByName(focusTermName)).asInstanceOf[Register[Any]],
              offset = offset
            )
            offset = RegisterOffset.add(offset, record.usedRegisters)
          case variant: Reflect.Variant.Bound[_] =>
            bindings(idx) = new PrismBinding(
              matcher = variant.matchers.apply(variant.caseIndexByName(focusTermName)),
              discriminator = variant.discriminator.asInstanceOf[Discriminator[Any]]
            )
          case sequence: Reflect.Sequence.Bound[Elem, Col] @scala.unchecked =>
            if (focusTermName == "at") {
              bindings(idx) = new AtBinding[Col](
                seqDeconstructor = sequence.seqDeconstructor,
                seqConstructor = sequence.seqConstructor,
                index = params(idx).asInstanceOf[Int]
              )
            } else {
              bindings(idx) = new SeqBinding[Col](
                seqDeconstructor = sequence.seqDeconstructor,
                seqConstructor = sequence.seqConstructor
              )
            }
          case source =>
            val map = source.asInstanceOf[Reflect.Map.Bound[Key, Value, Map]]
            if (focusTermName == "atKey") {
              bindings(idx) = new AtKeyBinding[Key, Map](
                mapDeconstructor = map.mapDeconstructor,
                mapConstructor = map.mapConstructor,
                key = params(idx).asInstanceOf[Key]
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
      checkRec(Registers(usedRegisters), 0, s, errors)
      errors.result() match {
        case errs: ::[OpticCheck.Single] => new Some(new OpticCheck(errs))
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
        case atBinding: AtBinding[Col] @scala.unchecked =>
          val deconstructor = atBinding.seqDeconstructor
          val col           = x.asInstanceOf[Col[A]]
          deconstructor match {
            case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
              val colLen = indexed.length(col)
              val colIdx = atBinding.index
              if (colLen <= colIdx) {
                errors.addOne(new OpticCheck.SequenceIndexOutOfBounds(toDynamic, toDynamic(idx), colIdx, colLen))
              } else if (idx + 1 != bindings.length) {
                checkRec(registers, idx + 1, indexed.objectAt(x.asInstanceOf[Col[AnyRef]], colIdx), errors)
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
              } else if (idx + 1 != bindings.length) checkRec(registers, idx + 1, it.next(), errors)
          }
        case atKeyBinding: AtKeyBinding[Key, Map] @scala.unchecked =>
          val deconstructor = atKeyBinding.mapDeconstructor
          val key           = atKeyBinding.key
          deconstructor.get(x.asInstanceOf[Map[Key, Value]], key) match {
            case None    => errors.addOne(new OpticCheck.MissingKey(toDynamic, toDynamic(idx), key))
            case Some(v) => if (idx + 1 != bindings.length) checkRec(registers, idx + 1, v, errors)
          }
        case seqBinding: SeqBinding[Col] @scala.unchecked =>
          val deconstructor = seqBinding.seqDeconstructor
          val it            = deconstructor.deconstruct(x.asInstanceOf[Col[Elem]])
          if (it.isEmpty) errors.addOne(new OpticCheck.EmptySequence(toDynamic, toDynamic(idx)))
          else if (idx + 1 != bindings.length) {
            while (it.hasNext) checkRec(registers, idx + 1, it.next(), errors)
          }
        case mapKeyBinding: MapKeyBinding[Map] @scala.unchecked =>
          val deconstructor = mapKeyBinding.mapDeconstructor
          val it            = deconstructor.deconstruct(x.asInstanceOf[Map[Key, Value]])
          if (it.isEmpty) errors.addOne(new OpticCheck.EmptyMap(toDynamic, toDynamic(idx)))
          else if (idx + 1 != bindings.length) {
            while (it.hasNext) checkRec(registers, idx + 1, deconstructor.getKey(it.next()), errors)
          }
        case mapValueBinding: MapValueBinding[Map] @scala.unchecked =>
          val deconstructor = mapValueBinding.mapDeconstructor
          val it            = deconstructor.deconstruct(x.asInstanceOf[Map[Key, Value]])
          if (it.isEmpty) errors.addOne(new OpticCheck.EmptyMap(toDynamic, toDynamic(idx)))
          else if (idx + 1 != bindings.length) {
            while (it.hasNext) checkRec(registers, idx + 1, deconstructor.getValue(it.next()), errors)
          }
      }

    def fold[Z](s: S)(zero: Z, f: (Z, A) => Z): Z = {
      if (bindings eq null) init()
      foldRec(Registers(usedRegisters), 0, s, zero, f)
    }

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
        case atBinding: AtBinding[Col] @scala.unchecked =>
          val deconstructor = atBinding.seqDeconstructor
          val col           = x.asInstanceOf[Col[A]]
          deconstructor match {
            case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
              val colLen = indexed.length(col)
              val colIdx = atBinding.index
              if (colLen <= colIdx) zero
              else if (idx + 1 == bindings.length) {
                f(
                  zero,
                  (
                    indexed.elementType(col) match {
                      case _: RegisterType.Boolean.type => indexed.booleanAt(x.asInstanceOf[Col[Boolean]], colIdx)
                      case _: RegisterType.Byte.type    => indexed.byteAt(x.asInstanceOf[Col[Byte]], colIdx)
                      case _: RegisterType.Char.type    => indexed.charAt(x.asInstanceOf[Col[Char]], colIdx)
                      case _: RegisterType.Short.type   => indexed.shortAt(x.asInstanceOf[Col[Short]], colIdx)
                      case _: RegisterType.Float.type   => indexed.floatAt(x.asInstanceOf[Col[Float]], colIdx)
                      case _: RegisterType.Int.type     => indexed.intAt(x.asInstanceOf[Col[Int]], colIdx)
                      case _: RegisterType.Double.type  => indexed.doubleAt(x.asInstanceOf[Col[Double]], colIdx)
                      case _: RegisterType.Long.type    => indexed.longAt(x.asInstanceOf[Col[Long]], colIdx)
                      case _                            => indexed.objectAt(x.asInstanceOf[Col[AnyRef]], colIdx)
                    }
                  ).asInstanceOf[A]
                )
              } else foldRec(registers, idx + 1, indexed.objectAt(x.asInstanceOf[Col[AnyRef]], colIdx), zero, f)
            case _ =>
              val it      = deconstructor.deconstruct(col)
              val colIdx  = atBinding.index
              var currIdx = 0
              while (currIdx < colIdx && it.hasNext) {
                it.next(): Unit
                currIdx += 1
              }
              if (currIdx != colIdx || !it.hasNext) zero
              else if (idx + 1 == bindings.length) f(zero, it.next())
              else foldRec(registers, idx + 1, it.next(), zero, f)
          }
        case atKeyBinding: AtKeyBinding[Key, Map] @scala.unchecked =>
          val deconstructor = atKeyBinding.mapDeconstructor
          val key           = atKeyBinding.key
          deconstructor.get(x.asInstanceOf[Map[Key, Value]], key) match {
            case None => zero
            case Some(v) =>
              if (idx + 1 == bindings.length) f(zero, v.asInstanceOf[A])
              else foldRec(registers, idx + 1, v, zero, f)
          }
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

    def modify(s: S, f: A => A): S = {
      if (bindings eq null) init()
      modifyRec(Registers(usedRegisters), 0, s, f).asInstanceOf[S]
    }

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
        case atBinding: AtBinding[Col] @scala.unchecked =>
          val deconstructor = atBinding.seqDeconstructor
          val constructor   = atBinding.seqConstructor
          val colIdx        = atBinding.index
          if (idx + 1 == bindings.length) modifySeqAt(deconstructor, constructor, x.asInstanceOf[Col[A]], f, colIdx)
          else {
            val builder = constructor.newObjectBuilder[Any]()
            val it      = deconstructor.deconstruct(x.asInstanceOf[Col[Any]])
            var currIdx = 0
            while (it.hasNext) {
              constructor.addObject(
                builder, {
                  val value = it.next()
                  if (currIdx == colIdx) modifyRec(registers, idx + 1, value, f)
                  else value
                }
              )
              currIdx += 1
            }
            constructor.resultObject(builder)
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
                else modifyRec(registers, idx + 1, value, f)
              )
            case _ => map
          }
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

    private[this] def modifySeqAt(
      deconstructor: SeqDeconstructor[Col],
      constructor: SeqConstructor[Col],
      s: Col[A],
      f: A => A,
      colIdx: Int
    ): Col[A] =
      deconstructor match {
        case indexed: SeqDeconstructor.SpecializedIndexed[Col] =>
          val len = indexed.length(s)
          indexed.elementType(s) match {
            case _: RegisterType.Boolean.type =>
              val builder = constructor.newBooleanBuilder(len)
              var idx     = 0
              while (idx < len) {
                constructor.addBoolean(
                  builder, {
                    val value = indexed.booleanAt(s.asInstanceOf[Col[Boolean]], idx)
                    if (idx == colIdx) f.asInstanceOf[Boolean => Boolean](value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultBoolean(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Byte.type =>
              val builder = constructor.newByteBuilder(len)
              var idx     = 0
              while (idx < len) {
                constructor.addByte(
                  builder, {
                    val value = indexed.byteAt(s.asInstanceOf[Col[Byte]], idx)
                    if (idx == colIdx) f.asInstanceOf[Byte => Byte](value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultByte(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Short.type =>
              val builder = constructor.newShortBuilder(len)
              var idx     = 0
              while (idx < len) {
                constructor.addShort(
                  builder, {
                    val value = indexed.shortAt(s.asInstanceOf[Col[Short]], idx)
                    if (idx == colIdx) f.asInstanceOf[Short => Short](value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultShort(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Int.type =>
              val builder = constructor.newIntBuilder(len)
              var idx     = 0
              while (idx < len) {
                constructor.addInt(
                  builder, {
                    val value = indexed.intAt(s.asInstanceOf[Col[Int]], idx)
                    if (idx == colIdx) f.asInstanceOf[Int => Int](value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultInt(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Long.type =>
              val builder = constructor.newLongBuilder(len)
              var idx     = 0
              while (idx < len) {
                constructor.addLong(
                  builder, {
                    val value = indexed.longAt(s.asInstanceOf[Col[Long]], idx)
                    if (idx == colIdx) f.asInstanceOf[Long => Long](value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultLong(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Float.type =>
              val builder = constructor.newFloatBuilder(len)
              var idx     = 0
              while (idx < len) {
                constructor.addFloat(
                  builder, {
                    val value = indexed.floatAt(s.asInstanceOf[Col[Float]], idx)
                    if (idx == colIdx) f.asInstanceOf[Float => Float](value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultFloat(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Double.type =>
              val builder = constructor.newDoubleBuilder(len)
              var idx     = 0
              while (idx < len) {
                constructor.addDouble(
                  builder, {
                    val value = indexed.doubleAt(s.asInstanceOf[Col[Double]], idx)
                    if (idx == colIdx) f.asInstanceOf[Double => Double](value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultDouble(builder).asInstanceOf[Col[A]]
            case _: RegisterType.Char.type =>
              val builder = constructor.newCharBuilder(len)
              var idx     = 0
              while (idx < len) {
                constructor.addChar(
                  builder, {
                    val value = indexed.charAt(s.asInstanceOf[Col[Char]], idx)
                    if (idx == colIdx) f.asInstanceOf[Char => Char](value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultChar(builder).asInstanceOf[Col[A]]
            case _ =>
              val builder = constructor.newObjectBuilder[A](len)
              var idx     = 0
              while (idx < len) {
                constructor.addObject(
                  builder, {
                    val value = indexed.objectAt(s, idx)
                    if (idx == colIdx) f(value)
                    else value
                  }
                )
                idx += 1
              }
              constructor.resultObject(builder)
          }
        case _ =>
          val builder = constructor.newObjectBuilder[A]()
          val it      = deconstructor.deconstruct(s)
          var currIdx = -1
          while (it.hasNext)
            constructor.addObject(
              builder, {
                currIdx += 1
                val value = it.next()
                if (currIdx == colIdx) f(value)
                else value
              }
            )
          constructor.resultObject(builder)
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

    lazy val toDynamic: DynamicOptic = new DynamicOptic({
      if (bindings eq null) init()
      val nodes = Vector.newBuilder[DynamicOptic.Node]
      val len   = bindings.length
      var idx   = 0
      while (idx < len) {
        nodes.addOne {
          bindings(idx) match {
            case _: LensBinding                                 => new DynamicOptic.Node.Field(focusTerms(idx).name)
            case _: PrismBinding                                => new DynamicOptic.Node.Case(focusTerms(idx).name)
            case at: AtBinding[Col] @scala.unchecked            => new DynamicOptic.Node.AtIndex(at.index)
            case atKey: AtKeyBinding[Key, Map] @scala.unchecked => new DynamicOptic.Node.AtMapKey[Key](atKey.key)
            case _: SeqBinding[Col] @scala.unchecked            => DynamicOptic.Node.Elements
            case _: MapKeyBinding[Map] @scala.unchecked         => DynamicOptic.Node.MapKeys
            case _                                              => DynamicOptic.Node.MapValues
          }
        }
        idx += 1
      }
      nodes.result()
    })

    override def hashCode: Int = java.util.Arrays.hashCode(sources.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(focusTerms.asInstanceOf[Array[AnyRef]]) ^
      java.util.Arrays.hashCode(params.asInstanceOf[Array[AnyRef]])

    override def equals(obj: Any): Boolean = obj match {
      case other: TraversalImpl[_, _] =>
        java.util.Arrays.equals(other.sources.asInstanceOf[Array[AnyRef]], sources.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.focusTerms.asInstanceOf[Array[AnyRef]], focusTerms.asInstanceOf[Array[AnyRef]]) &&
        java.util.Arrays.equals(other.params.asInstanceOf[Array[AnyRef]], params.asInstanceOf[Array[AnyRef]])
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

private[schema] case class AtBinding[C[_]](
  seqDeconstructor: SeqDeconstructor[C] = null,
  seqConstructor: SeqConstructor[C] = null,
  index: Int = 0
) extends OpticBinding

private[schema] case class AtKeyBinding[K, M[K, _]](
  mapDeconstructor: MapDeconstructor[M] = null,
  mapConstructor: MapConstructor[M] = null,
  key: K = null.asInstanceOf[K]
) extends OpticBinding
