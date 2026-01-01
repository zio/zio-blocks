package zio.blocks.typeid

/**
 * Identity of a type or type constructor.
 * 
 * TypeId captures the full identity of a Scala type, including:
 * - Its name and where it's defined (owner)
 * - Its type parameters (for type constructors)
 * - Whether it's a nominal type, type alias, or opaque type
 * 
 * TypeId is phantom-typed by the type it represents, providing compile-time safety.
 * 
 * @tparam A The type this TypeId represents (can be a type constructor like `List`)
 */
sealed trait TypeId[A] {
  /** The simple name of the type (e.g., "Int", "List", "Person") */
  def name: String
  
  /** Where this type is defined (package path and enclosing types/objects) */
  def owner: Owner
  
  /** Type parameters for type constructors (empty for proper types) */
  def typeParams: List[TypeParam]
  
  /** Number of type parameters (0 for proper types, 1+ for type constructors) */
  final def arity: Int = typeParams.size
  
  /** Fully qualified name (e.g., "scala.Int", "zio.blocks.schema.Person") */
  final def fullName: String =
    if (owner.isEmpty) name
    else owner.asString + "." + name
  
  /** Check if this is a proper type (arity == 0) */
  final def isProperType: Boolean = arity == 0
  
  /** Check if this is a type constructor (arity > 0) */
  final def isTypeConstructor: Boolean = arity > 0
  
  override def toString: String = fullName
}

object TypeId extends TypeIdVersionSpecific {
  
  // ============================================================================
  // Private implementations
  // ============================================================================
  
  private final case class NominalImpl[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam]
  ) extends TypeId[A]
  
  private final case class AliasImpl[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    aliased: TypeRepr
  ) extends TypeId[A]
  
  private final case class OpaqueImpl[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    representation: TypeRepr
  ) extends TypeId[A]

  private final case class AppliedImpl[A](
    ctor: TypeId[_],
    args: List[TypeId[_]]
  ) extends TypeId[A] {
    def name: String = ctor.name
    def owner: Owner = ctor.owner
    def typeParams: List[TypeParam] = ctor.typeParams
    override def toString: String = s"${ctor.fullName}[${args.mkString(", ")}]"
  }
  
  // ============================================================================
  // Constructors
  // ============================================================================
  
  /** Create a TypeId for a nominal type (class, trait, object) */
  def nominal[A](name: String, owner: Owner, typeParams: List[TypeParam] = Nil): TypeId[A] =
    NominalImpl(name, owner, typeParams)
  
  /** Create a TypeId for a type alias */
  def alias[A](name: String, owner: Owner, typeParams: List[TypeParam], aliased: TypeRepr): TypeId[A] =
    AliasImpl(name, owner, typeParams, aliased)
  
  /** Create a TypeId for an opaque type */
  def opaque[A](name: String, owner: Owner, typeParams: List[TypeParam], representation: TypeRepr): TypeId[A] =
    OpaqueImpl(name, owner, typeParams, representation)

  /** Create a TypeId for an applied type (generic instantiation) */
  def applied[A](ctor: TypeId[_], args: List[TypeId[_]]): TypeId[A] =
    AppliedImpl(ctor, args)
  
  // ============================================================================
  // Pattern matching support
  // ============================================================================
  
  object Nominal {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam])] = id match {
      case impl: NominalImpl[_] => Some((impl.name, impl.owner, impl.typeParams))
      case _                    => None
    }
  }
  
  object Alias {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeRepr)] = id match {
      case impl: AliasImpl[_] => Some((impl.name, impl.owner, impl.typeParams, impl.aliased))
      case _                  => None
    }
  }

  object Applied {
    def unapply(id: TypeId[_]): Option[(TypeId[_], List[TypeId[_]])] = id match {
      case impl: AppliedImpl[_] => Some((impl.ctor, impl.args))
      case _                    => None
    }
  }
  
  object Opaque {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeRepr)] = id match {
      case impl: OpaqueImpl[_] => Some((impl.name, impl.owner, impl.typeParams, impl.representation))
      case _                   => None
    }
  }
  
  // ============================================================================
  // Primitive TypeIds
  // ============================================================================
  
  val unit: TypeId[Unit]           = nominal("Unit", Owner.scala)
  val boolean: TypeId[Boolean]     = nominal("Boolean", Owner.scala)
  val byte: TypeId[Byte]           = nominal("Byte", Owner.scala)
  val short: TypeId[Short]         = nominal("Short", Owner.scala)
  val int: TypeId[Int]             = nominal("Int", Owner.scala)
  val long: TypeId[Long]           = nominal("Long", Owner.scala)
  val float: TypeId[Float]         = nominal("Float", Owner.scala)
  val double: TypeId[Double]       = nominal("Double", Owner.scala)
  val char: TypeId[Char]           = nominal("Char", Owner.scala)
  val string: TypeId[String]       = nominal("String", Owner.scala)
  val bigInt: TypeId[BigInt]       = nominal("BigInt", Owner.scala)
  val bigDecimal: TypeId[BigDecimal] = nominal("BigDecimal", Owner.scala)
  
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
  
  // Collection type constructors (arity > 0)
  private val A = TypeParam("A", 0)
  private val K = TypeParam("K", 0)
  private val V = TypeParam("V", 1)
  
  val option: TypeId[Option[_]]          = nominal("Option", Owner.scala, List(A))
  val some: TypeId[Some[_]]              = nominal("Some", Owner.scala, List(A))
  val none: TypeId[None.type]            = nominal("None", Owner.scala)
  val list: TypeId[List[_]]              = nominal("List", Owner.scalaCollectionImmutable, List(A))
  val vector: TypeId[Vector[_]]          = nominal("Vector", Owner.scalaCollectionImmutable, List(A))
  val set: TypeId[Set[_]]                = nominal("Set", Owner.scalaCollectionImmutable, List(A))
  val map: TypeId[Map[_, _]]             = nominal("Map", Owner.scalaCollectionImmutable, List(K, V))
  val indexedSeq: TypeId[IndexedSeq[_]]  = nominal("IndexedSeq", Owner.scalaCollectionImmutable, List(A))
  val seq: TypeId[Seq[_]]                = nominal("Seq", Owner.scalaCollectionImmutable, List(A))

  // ============================================================================
  // Applied type constructors - create TypeId for parameterized types
  // ============================================================================
  
  /** Create a TypeId for Option[A] from a TypeId[A] */
  def option[A](element: TypeId[A]): TypeId[Option[A]] =
    applied(option, List(element))
  
  /** Create a TypeId for Some[A] from a TypeId[A] */
  def some[A](element: TypeId[A]): TypeId[Some[A]] =
    applied(some, List(element))
  
  /** Create a TypeId for List[A] from a TypeId[A] */
  def list[A](element: TypeId[A]): TypeId[List[A]] =
    applied(list, List(element))
  
  /** Create a TypeId for Vector[A] from a TypeId[A] */
  def vector[A](element: TypeId[A]): TypeId[Vector[A]] =
    applied(vector, List(element))
  
  /** Create a TypeId for Set[A] from a TypeId[A] */
  def set[A](element: TypeId[A]): TypeId[Set[A]] =
    applied(set, List(element))
  
  /** Create a TypeId for IndexedSeq[A] from a TypeId[A] */
  def indexedSeq[A](element: TypeId[A]): TypeId[IndexedSeq[A]] =
    applied(indexedSeq, List(element))
  
  /** Create a TypeId for Seq[A] from a TypeId[A] */
  def seq[A](element: TypeId[A]): TypeId[Seq[A]] =
    applied(seq, List(element))
  
  /** Create a TypeId for Map[K, V] from TypeId[K] and TypeId[V] */
  def map[K, V](key: TypeId[K], value: TypeId[V]): TypeId[Map[K, V]] =
    applied(map, List(key, value))
}
