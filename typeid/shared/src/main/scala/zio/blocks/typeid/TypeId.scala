package zio.blocks.typeid

/**
 * Identity of a type or type constructor, phantom-typed by `A`.
 * 
 * `TypeId` provides a robust way to identify Scala types at runtime while
 * preserving type information through phantom types. It supports:
 * 
 * - '''Nominal types''': Regular classes, traits, and objects
 * - '''Type aliases''': Named type definitions
 * - '''Opaque types''': Scala 3 opaque type aliases
 * 
 * The phantom type parameter `A` allows type-safe operations on TypeIds
 * without runtime overhead.
 * 
 * == Example Usage ==
 * {{{
 * // Macro-derived TypeId
 * val listId: TypeId[List] = TypeId.derive[List]
 * 
 * // Manual construction
 * val intId: TypeId[Int] = TypeId.nominal[Int](
 *   name = "Int",
 *   owner = Owner.scala,
 *   typeParams = Nil
 * )
 * }}}
 * 
 * @tparam A The type this TypeId represents (phantom type)
 */
sealed trait TypeId[A] extends Product with Serializable { self =>

  /**
   * The simple name of the type (without package/owner prefix).
   */
  def name: String

  /**
   * The ownership chain - where this type is defined.
   */
  def owner: Owner

  /**
   * The type parameters declared by this type.
   */
  def typeParams: List[TypeParam]

  /**
   * Documentation associated with this type, if any.
   */
  def documentation: Option[String]

  /**
   * The arity (number of type parameters) of this type.
   * 
   * @return 0 for proper types, >= 1 for type constructors
   */
  final def arity: Int = typeParams.size

  /**
   * Whether this is a proper type (not a type constructor).
   */
  final def isProperType: Boolean = arity == 0

  /**
   * Whether this is a type constructor.
   */
  final def isTypeConstructor: Boolean = arity > 0

  /**
   * The fully qualified name including the owner path.
   * 
   * @return e.g., "scala.collection.immutable.List"
   */
  final def fullName: String =
    if (owner.isRoot) name
    else s"${owner.asString}.$name"

  /**
   * Returns a copy of this TypeId with updated documentation.
   */
  def withDocumentation(doc: String): TypeId[A]

  /**
   * Returns a copy of this TypeId with the documentation removed.
   */
  def withoutDocumentation: TypeId[A]

  /**
   * Whether this TypeId represents a nominal type.
   */
  def isNominal: Boolean = false

  /**
   * Whether this TypeId represents a type alias.
   */
  def isAlias: Boolean = false

  /**
   * Whether this TypeId represents an opaque type.
   */
  def isOpaque: Boolean = false

  /**
   * Attempts to cast this TypeId to a more specific type parameter.
   * This is unsafe and should only be used when you have external
   * verification of the type relationship.
   */
  final def asInstanceOf_![B]: TypeId[B] = 
    this.asInstanceOf[TypeId[B]]
}

object TypeId extends TypeIdVersionSpecific {

  // ============================================================================
  // Nominal Types
  // ============================================================================

  /**
   * Implementation of TypeId for nominal types (regular classes, traits, objects).
   */
  private[typeid] final case class Nominal[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    documentation: Option[String] = None
  ) extends TypeId[A] {
    override def isNominal: Boolean = true

    def withDocumentation(doc: String): TypeId[A] = 
      copy(documentation = Some(doc))

    def withoutDocumentation: TypeId[A] = 
      copy(documentation = None)

    override def toString: String = 
      s"TypeId.Nominal($fullName${if (typeParams.nonEmpty) s"[${typeParams.map(_.name).mkString(", ")}]" else ""})"
  }

  // ============================================================================
  // Type Aliases
  // ============================================================================

  /**
   * Implementation of TypeId for type aliases.
   * 
   * @param aliased The type representation this alias expands to
   */
  private[typeid] final case class Alias[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    aliased: TypeRepr,
    documentation: Option[String] = None
  ) extends TypeId[A] {
    override def isAlias: Boolean = true

    def withDocumentation(doc: String): TypeId[A] = 
      copy(documentation = Some(doc))

    def withoutDocumentation: TypeId[A] = 
      copy(documentation = None)

    override def toString: String = 
      s"TypeId.Alias($fullName = $aliased)"
  }

  // ============================================================================
  // Opaque Types
  // ============================================================================

  /**
   * Implementation of TypeId for opaque types (Scala 3).
   * 
   * @param representation The underlying type representation
   */
  private[typeid] final case class Opaque[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    representation: TypeRepr,
    documentation: Option[String] = None
  ) extends TypeId[A] {
    override def isOpaque: Boolean = true

    def withDocumentation(doc: String): TypeId[A] = 
      copy(documentation = Some(doc))

    def withoutDocumentation: TypeId[A] = 
      copy(documentation = None)

    override def toString: String = 
      s"TypeId.Opaque($fullName)"
  }

  // ============================================================================
  // Smart Constructors
  // ============================================================================

  /**
   * Creates a TypeId for a nominal type (class, trait, or object).
   * 
   * @param name       The simple name of the type
   * @param owner      The ownership chain (package/containing types)
   * @param typeParams The type parameters declared by this type
   * @return A TypeId representing the nominal type
   */
  def nominal[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil
  ): TypeId[A] = Nominal(name, owner, typeParams, None)

  /**
   * Creates a TypeId for a type alias.
   * 
   * @param name       The name of the alias
   * @param owner      The ownership chain
   * @param typeParams The type parameters
   * @param aliased    The type this alias expands to
   * @return A TypeId representing the type alias
   */
  def alias[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    aliased: TypeRepr
  ): TypeId[A] = Alias(name, owner, typeParams, aliased, None)

  /**
   * Creates a TypeId for an opaque type.
   * 
   * @param name           The name of the opaque type
   * @param owner          The ownership chain
   * @param typeParams     The type parameters
   * @param representation The underlying representation type
   * @return A TypeId representing the opaque type
   */
  def opaque[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    representation: TypeRepr
  ): TypeId[A] = Opaque(name, owner, typeParams, representation, None)

  // ============================================================================
  // Pattern Matching Support
  // ============================================================================

  /**
   * Extractor for nominal types.
   */
  object Nominal {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam])] = id match {
      case n: TypeId.Nominal[?] => Some((n.name, n.owner, n.typeParams))
      case _                    => None
    }
  }

  /**
   * Extractor for type aliases.
   */
  object Alias {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam], TypeRepr)] = id match {
      case a: TypeId.Alias[?] => Some((a.name, a.owner, a.typeParams, a.aliased))
      case _                  => None
    }
  }

  /**
   * Extractor for opaque types.
   */
  object Opaque {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam], TypeRepr)] = id match {
      case o: TypeId.Opaque[?] => Some((o.name, o.owner, o.typeParams, o.representation))
      case _                   => None
    }
  }

  // ============================================================================
  // Built-in TypeIds for Common Scala Types
  // ============================================================================

  // Primitive types
  val unit: TypeId[Unit]       = nominal[Unit]("Unit", Owner.scala)
  val boolean: TypeId[Boolean] = nominal[Boolean]("Boolean", Owner.scala)
  val byte: TypeId[Byte]       = nominal[Byte]("Byte", Owner.scala)
  val short: TypeId[Short]     = nominal[Short]("Short", Owner.scala)
  val int: TypeId[Int]         = nominal[Int]("Int", Owner.scala)
  val long: TypeId[Long]       = nominal[Long]("Long", Owner.scala)
  val float: TypeId[Float]     = nominal[Float]("Float", Owner.scala)
  val double: TypeId[Double]   = nominal[Double]("Double", Owner.scala)
  val char: TypeId[Char]       = nominal[Char]("Char", Owner.scala)
  val string: TypeId[String]   = nominal[String]("String", Owner.javaLang)

  // Numeric types
  val bigInt: TypeId[BigInt]         = nominal[BigInt]("BigInt", Owner.scala)
  val bigDecimal: TypeId[BigDecimal] = nominal[BigDecimal]("BigDecimal", Owner.scala)

  // Java time types
  val dayOfWeek: TypeId[java.time.DayOfWeek]           = nominal("DayOfWeek", Owner.javaTime)
  val duration: TypeId[java.time.Duration]             = nominal("Duration", Owner.javaTime)
  val instant: TypeId[java.time.Instant]               = nominal("Instant", Owner.javaTime)
  val localDate: TypeId[java.time.LocalDate]           = nominal("LocalDate", Owner.javaTime)
  val localDateTime: TypeId[java.time.LocalDateTime]   = nominal("LocalDateTime", Owner.javaTime)
  val localTime: TypeId[java.time.LocalTime]           = nominal("LocalTime", Owner.javaTime)
  val month: TypeId[java.time.Month]                   = nominal("Month", Owner.javaTime)
  val monthDay: TypeId[java.time.MonthDay]             = nominal("MonthDay", Owner.javaTime)
  val offsetDateTime: TypeId[java.time.OffsetDateTime] = nominal("OffsetDateTime", Owner.javaTime)
  val offsetTime: TypeId[java.time.OffsetTime]         = nominal("OffsetTime", Owner.javaTime)
  val period: TypeId[java.time.Period]                 = nominal("Period", Owner.javaTime)
  val year: TypeId[java.time.Year]                     = nominal("Year", Owner.javaTime)
  val yearMonth: TypeId[java.time.YearMonth]           = nominal("YearMonth", Owner.javaTime)
  val zoneId: TypeId[java.time.ZoneId]                 = nominal("ZoneId", Owner.javaTime)
  val zoneOffset: TypeId[java.time.ZoneOffset]         = nominal("ZoneOffset", Owner.javaTime)
  val zonedDateTime: TypeId[java.time.ZonedDateTime]   = nominal("ZonedDateTime", Owner.javaTime)

  // Java util types
  val currency: TypeId[java.util.Currency] = nominal("Currency", Owner.javaUtil)
  val uuid: TypeId[java.util.UUID]         = nominal("UUID", Owner.javaUtil)

  // Common type constructors
  private val A = TypeParam("A", 0)
  private val B = TypeParam("B", 1)

  val option: TypeId[Option[_]]    = nominal[Option[_]]("Option", Owner.scala, List(A.covariant))
  val some: TypeId[Some[_]]        = nominal[Some[_]]("Some", Owner.scala, List(A.covariant))
  val none: TypeId[None.type]      = nominal[None.type]("None", Owner.scala)
  val list: TypeId[List[_]]        = nominal[List[_]]("List", Owner.scalaCollectionImmutable, List(A.covariant))
  val vector: TypeId[Vector[_]]    = nominal[Vector[_]]("Vector", Owner.scalaCollectionImmutable, List(A.covariant))
  val set: TypeId[Set[_]]          = nominal[Set[_]]("Set", Owner.scalaCollectionImmutable, List(A))
  val map: TypeId[Map[_, _]]       = nominal[Map[_, _]]("Map", Owner.scalaCollectionImmutable, List(A, B.covariant))
  val seq: TypeId[Seq[_]]          = nominal[Seq[_]]("Seq", Owner.scalaCollectionImmutable, List(A.covariant))
  val indexedSeq: TypeId[IndexedSeq[_]] = nominal[IndexedSeq[_]]("IndexedSeq", Owner.scalaCollectionImmutable, List(A.covariant))

  // ============================================================================
  // Convenience Factory Methods for Applied Types
  // ============================================================================

  /**
   * Creates a TypeId for Some[A] with the element type.
   */
  def some[A](element: TypeId[A]): TypeId[Some[A]] =
    nominal[Some[A]]("Some", Owner.scala, Nil)

  /**
   * Creates a TypeId for Option[A] with the element type.
   */
  def option[A](element: TypeId[A]): TypeId[Option[A]] =
    nominal[Option[A]]("Option", Owner.scala, Nil)

  /**
   * Creates a TypeId for List[A] with the element type.
   */
  def list[A](element: TypeId[A]): TypeId[List[A]] =
    nominal[List[A]]("List", Owner.scalaCollectionImmutable, Nil)

  /**
   * Creates a TypeId for Vector[A] with the element type.
   */
  def vector[A](element: TypeId[A]): TypeId[Vector[A]] =
    nominal[Vector[A]]("Vector", Owner.scalaCollectionImmutable, Nil)

  /**
   * Creates a TypeId for Set[A] with the element type.
   */
  def set[A](element: TypeId[A]): TypeId[Set[A]] =
    nominal[Set[A]]("Set", Owner.scalaCollectionImmutable, Nil)

  /**
   * Creates a TypeId for Map[K, V] with key and value types.
   */
  def map[K, V](key: TypeId[K], value: TypeId[V]): TypeId[Map[K, V]] =
    nominal[Map[K, V]]("Map", Owner.scalaCollectionImmutable, Nil)

  /**
   * Creates a TypeId for Seq[A] with the element type.
   */
  def seq[A](element: TypeId[A]): TypeId[Seq[A]] =
    nominal[Seq[A]]("Seq", Owner.scalaCollectionImmutable, Nil)

  /**
   * Creates a TypeId for IndexedSeq[A] with the element type.
   */
  def indexedSeq[A](element: TypeId[A]): TypeId[IndexedSeq[A]] =
    nominal[IndexedSeq[A]]("IndexedSeq", Owner.scalaCollectionImmutable, Nil)

  /**
   * Creates a TypeId for scala.collection.immutable.ArraySeq[A].
   */
  def arraySeq[A](element: TypeId[A]): TypeId[scala.collection.immutable.ArraySeq[A]] =
    nominal[scala.collection.immutable.ArraySeq[A]]("ArraySeq", Owner.scalaCollectionImmutable, Nil)
}
