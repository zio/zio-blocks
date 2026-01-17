package zio.blocks.typeid

/**
 * The primary type for identifying Scala types and type constructors. TypeId[A]
 * preserves the compile-time type information while providing a rich runtime
 * representation of the type's structure.
 */
sealed trait TypeId[A <: AnyKind] {
  def name: String
  def owner: Owner
  def typeParams: List[TypeParam]
  def defKind: TypeDefKind
  def parents: List[TypeRepr]
  def selfType: Option[TypeRepr]
  def aliasedTo: Option[TypeRepr]      // For type aliases
  def representation: Option[TypeRepr] // For opaque types
  def annotations: List[Annotation]

  // Derived properties
  final def arity: Int = typeParams.size

  final def fullName: String =
    if (owner.isRoot) name
    else s"${owner.asString}.$name"

  final def isProperType: Boolean      = arity == 0
  final def isTypeConstructor: Boolean = arity > 0

  final def isClass: Boolean    = defKind.isInstanceOf[TypeDefKind.Class]
  final def isTrait: Boolean    = defKind.isInstanceOf[TypeDefKind.Trait]
  final def isObject: Boolean   = defKind == TypeDefKind.Object
  final def isEnum: Boolean     = defKind.isInstanceOf[TypeDefKind.Enum]
  final def isAlias: Boolean    = defKind == TypeDefKind.TypeAlias
  final def isOpaque: Boolean   = defKind.isInstanceOf[TypeDefKind.OpaqueType]
  final def isAbstract: Boolean = defKind == TypeDefKind.AbstractType

  final def isSealed: Boolean = defKind match {
    case TypeDefKind.Trait(isSealed, _) => isSealed
    case TypeDefKind.Enum(_)            => true
    case _                              => false
  }

  final def isCaseClass: Boolean = defKind match {
    case TypeDefKind.Class(_, _, isCase, _) => isCase
    case _                                  => false
  }

  final def isValueClass: Boolean = defKind match {
    case TypeDefKind.Class(_, _, _, isValue) => isValue
    case _                                   => false
  }

  final def isSubtypeOf(other: TypeId[_]): Boolean =
    Subtyping.isSubtype(TypeRepr.Ref(this), TypeRepr.Ref(other))

  final def isSupertypeOf(other: TypeId[_]): Boolean =
    Subtyping.isSubtype(TypeRepr.Ref(other), TypeRepr.Ref(this))

  final def isEquivalentTo(other: TypeId[_]): Boolean =
    Subtyping.isEquivalent(TypeRepr.Ref(this), TypeRepr.Ref(other))

  override def equals(other: Any): Boolean = other match {
    case that: TypeId[_] => TypeId.structurallyEqual(this, that)
    case _               => false
  }

  override def hashCode(): Int = TypeId.structuralHash(this)

  def copy(
    name: String = this.name,
    owner: Owner = this.owner,
    typeParams: List[TypeParam] = this.typeParams,
    defKind: TypeDefKind = this.defKind,
    parents: List[TypeRepr] = this.parents,
    selfType: Option[TypeRepr] = this.selfType,
    aliasedTo: Option[TypeRepr] = this.aliasedTo,
    representation: Option[TypeRepr] = this.representation,
    annotations: List[Annotation] = this.annotations
  ): TypeId[A] = TypeId.make(
    name,
    owner,
    typeParams,
    defKind,
    parents,
    selfType,
    aliasedTo,
    representation,
    annotations
  )
}

object TypeId extends TypeIdVersionSpecific {
  // Runtime registry for mapping TypeSelect paths to TypeIds
  private val typeRegistry = scala.collection.mutable.Map[String, TypeId[_]]()

  // Register a TypeId for runtime lookup
  private[typeid] def register(id: TypeId[_]): Unit = {
    val key = s"${id.owner.asString}.${id.name}"
    typeRegistry.synchronized {
      typeRegistry.put(key, id)
    }
  }

  // Lookup a TypeId by full name
  private[typeid] def lookup(fullName: String): Option[TypeId[_]] =
    typeRegistry.synchronized {
      typeRegistry.get(fullName)
    }

  private final class Impl[A <: AnyKind](
    val name: String,
    val owner: Owner,
    val typeParams: List[TypeParam],
    val defKind: TypeDefKind,
    val parents: List[TypeRepr],
    val selfType: Option[TypeRepr],
    val aliasedTo: Option[TypeRepr],
    val representation: Option[TypeRepr],
    val annotations: List[Annotation]
  ) extends TypeId[A] {
    override def hashCode: Int             = structuralHash(this)
    override def equals(obj: Any): Boolean = obj match {
      case that: TypeId[_] => structurallyEqual(this, that)
      case _               => false
    }
    override def toString: String = fullName
  }

  def nominal[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    defKind: TypeDefKind,
    parents: List[TypeRepr] = Nil,
    selfType: Option[TypeRepr] = None,
    annotations: List[Annotation] = Nil
  ): TypeId[A] = {
    val id = new Impl[A](
      name,
      owner,
      typeParams,
      defKind,
      parents,
      selfType,
      None,
      None,
      annotations
    )
    // Register for runtime TypeSelect resolution
    register(id)
    id
  }

  private[typeid] def make[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    defKind: TypeDefKind,
    parents: List[TypeRepr],
    selfType: Option[TypeRepr],
    aliasedTo: Option[TypeRepr],
    representation: Option[TypeRepr],
    annotations: List[Annotation]
  ): TypeId[A] = {
    val id = new Impl[A](
      name,
      owner,
      typeParams,
      defKind,
      parents,
      selfType,
      aliasedTo,
      representation,
      annotations
    )
    // Register for runtime TypeSelect resolution
    register(id)
    id
  }

  def nominal[A <: AnyKind](
    name: String,
    owner: String,
    typeParams: List[TypeParam]
  ): TypeId[A] = nominal(
    name,
    Owner.parse(owner),
    typeParams,
    TypeDefKind.Class(false, false, false, false),
    Nil,
    None,
    Nil
  )

  def alias[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    aliased: TypeRepr,
    annotations: List[Annotation] = Nil
  ): TypeId[A] = {
    val id = new Impl[A](
      name,
      owner,
      typeParams,
      TypeDefKind.TypeAlias,
      Nil,
      None,
      Some(aliased),
      None,
      annotations
    )
    // Register for runtime TypeSelect resolution
    register(id)
    id
  }

  def opaque[A <: AnyKind](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    representation: TypeRepr,
    publicBounds: TypeBounds = TypeBounds.empty,
    annotations: List[Annotation] = Nil
  ): TypeId[A] = {
    val id = new Impl[A](
      name,
      owner,
      typeParams,
      TypeDefKind.OpaqueType(publicBounds),
      Nil,
      None,
      None,
      Some(representation),
      annotations
    )
    // Register for runtime TypeSelect resolution
    register(id)
    id
  }

  def structurallyEqual(a: TypeId[_], b: TypeId[_]): Boolean = {
    // Normalize both TypeIds by expanding aliases
    val aNorm = TypeNormalization.normalize(TypeRepr.Ref(a))
    val bNorm = TypeNormalization.normalize(TypeRepr.Ref(b))
    TypeEquality.areEqual(aNorm, bNorm)
  }

  def structuralHash(id: TypeId[_]): Int = {
    // Normalize the TypeId by expanding aliases
    val normalized = TypeNormalization.normalize(TypeRepr.Ref(id))
    TypeEquality.structuralHash(normalized)
  }

  // Standard Library Types
  val Unit: TypeId[scala.Unit] =
    nominal("Unit", Owner.parse("scala"), Nil, TypeDefKind.Class(false, false, false, true))
  val Boolean: TypeId[scala.Boolean] =
    nominal("Boolean", Owner.parse("scala"), Nil, TypeDefKind.Class(false, false, false, true))
  val Byte: TypeId[scala.Byte] =
    nominal("Byte", Owner.parse("scala"), Nil, TypeDefKind.Class(false, false, false, true))
  val Char: TypeId[scala.Char] =
    nominal("Char", Owner.parse("scala"), Nil, TypeDefKind.Class(false, false, false, true))
  val Short: TypeId[scala.Short] =
    nominal("Short", Owner.parse("scala"), Nil, TypeDefKind.Class(false, false, false, true))
  val Int: TypeId[scala.Int]   = nominal("Int", Owner.parse("scala"), Nil, TypeDefKind.Class(false, false, false, true))
  val Long: TypeId[scala.Long] =
    nominal("Long", Owner.parse("scala"), Nil, TypeDefKind.Class(false, false, false, true))
  val Float: TypeId[scala.Float] =
    nominal("Float", Owner.parse("scala"), Nil, TypeDefKind.Class(false, false, false, true))
  val Double: TypeId[scala.Double] =
    nominal("Double", Owner.parse("scala"), Nil, TypeDefKind.Class(false, false, false, true))
  val String: TypeId[Predef.String] =
    nominal("String", Owner.parse("java.lang"), Nil, TypeDefKind.Class(false, false, false, false))

  val Any: TypeId[scala.Any]       = nominal("Any", Owner.parse("scala"), Nil, TypeDefKind.Class(false, true, false, false))
  val AnyRef: TypeId[scala.AnyRef] = nominal(
    "AnyRef",
    Owner.parse("scala"),
    Nil,
    TypeDefKind.Class(false, true, false, false),
    parents = scala.List(TypeRepr.Ref(Any))
  )
  val AnyVal: TypeId[scala.AnyVal] = nominal(
    "AnyVal",
    Owner.parse("scala"),
    Nil,
    TypeDefKind.Class(false, true, false, false),
    parents = scala.List(TypeRepr.Ref(Any))
  )
  val Nothing: TypeId[scala.Nothing] =
    nominal("Nothing", Owner.parse("scala"), Nil, TypeDefKind.Class(true, true, false, false))
  val Null: TypeId[scala.Null] =
    nominal("Null", Owner.parse("scala"), Nil, TypeDefKind.Class(true, false, false, false))

  val BigInt: TypeId[scala.math.BigInt] =
    nominal("BigInt", Owner.parse("scala.math"), Nil, TypeDefKind.Class(false, false, false, false))
  val BigDecimal: TypeId[scala.math.BigDecimal] =
    nominal("BigDecimal", Owner.parse("scala.math"), Nil, TypeDefKind.Class(false, false, false, false))

  val UUID: TypeId[java.util.UUID] =
    nominal("UUID", Owner.parse("java.util"), Nil, TypeDefKind.Class(false, false, false, false))
  val Currency: TypeId[java.util.Currency] =
    nominal("Currency", Owner.parse("java.util"), Nil, TypeDefKind.Class(false, false, false, false))

  // java.time
  val DayOfWeek: TypeId[java.time.DayOfWeek] =
    nominal("DayOfWeek", Owner.parse("java.time"), Nil, TypeDefKind.Enum(Nil))
  val Duration: TypeId[java.time.Duration] =
    nominal("Duration", Owner.parse("java.time"), Nil, TypeDefKind.Class(false, false, false, false))
  val Instant: TypeId[java.time.Instant] =
    nominal("Instant", Owner.parse("java.time"), Nil, TypeDefKind.Class(false, false, false, false))
  val LocalDate: TypeId[java.time.LocalDate] =
    nominal("LocalDate", Owner.parse("java.time"), Nil, TypeDefKind.Class(false, false, false, false))
  val LocalDateTime: TypeId[java.time.LocalDateTime] =
    nominal("LocalDateTime", Owner.parse("java.time"), Nil, TypeDefKind.Class(false, false, false, false))
  val LocalTime: TypeId[java.time.LocalTime] =
    nominal("LocalTime", Owner.parse("java.time"), Nil, TypeDefKind.Class(false, false, false, false))
  val Month: TypeId[java.time.Month]       = nominal("Month", Owner.parse("java.time"), Nil, TypeDefKind.Enum(Nil))
  val MonthDay: TypeId[java.time.MonthDay] =
    nominal("MonthDay", Owner.parse("java.time"), Nil, TypeDefKind.Class(false, false, false, false))
  val OffsetDateTime: TypeId[java.time.OffsetDateTime] =
    nominal("OffsetDateTime", Owner.parse("java.time"), Nil, TypeDefKind.Class(false, false, false, false))
  val OffsetTime: TypeId[java.time.OffsetTime] =
    nominal("OffsetTime", Owner.parse("java.time"), Nil, TypeDefKind.Class(false, false, false, false))
  val Period: TypeId[java.time.Period] =
    nominal("Period", Owner.parse("java.time"), Nil, TypeDefKind.Class(false, false, false, false))
  val Year: TypeId[java.time.Year] =
    nominal("Year", Owner.parse("java.time"), Nil, TypeDefKind.Class(false, false, false, false))
  val YearMonth: TypeId[java.time.YearMonth] =
    nominal("YearMonth", Owner.parse("java.time"), Nil, TypeDefKind.Class(false, false, false, false))
  val ZoneId: TypeId[java.time.ZoneId] =
    nominal("ZoneId", Owner.parse("java.time"), Nil, TypeDefKind.Class(false, true, false, false))
  val ZoneOffset: TypeId[java.time.ZoneOffset] =
    nominal("ZoneOffset", Owner.parse("java.time"), Nil, TypeDefKind.Class(false, false, false, false))
  val ZonedDateTime: TypeId[java.time.ZonedDateTime] =
    nominal("ZonedDateTime", Owner.parse("java.time"), Nil, TypeDefKind.Class(false, false, false, false))
  val DynamicValue: TypeId[Any] =
    nominal("DynamicValue", Owner.parse("zio.blocks.schema"), Nil, TypeDefKind.Class(false, false, true, false))

  lazy val OptionTypeId: TypeId[Nothing] = {
    val tparam = TypeParam("A", 0, Variance.Covariant, TypeBounds.empty)
    nominal(
      "Option",
      Owner.parse("scala"),
      scala.List(tparam),
      TypeDefKind.Class(isFinal = false, isAbstract = true, isCase = false, isValue = false)
    )
  }

  object Option {
    def apply[A](param: TypeId[A]): TypeId[Option[A]] =
      alias(
        "Option",
        Owner.parse("scala"),
        Nil,
        TypeRepr.Applied(TypeRepr.Ref(OptionTypeId), scala.List(TypeRepr.Ref(param)))
      )
    def Some[A](param: TypeId[A]): TypeId[scala.Some[A]] = {
      val tparam = TypeParam("A", 0, Variance.Covariant, TypeBounds.exact(TypeRepr.Ref(param)))
      nominal("Some", Owner.parse("scala"), scala.List(tparam), TypeDefKind.Class(false, false, true, false))
    }
    val None: TypeId[scala.None.type] = nominal("None", Owner.parse("scala"), Nil, TypeDefKind.Object)
  }

  lazy val ListTypeId: TypeId[Nothing] = {
    val tparam = TypeParam("A", 0, Variance.Covariant, TypeBounds.empty)
    nominal(
      "List",
      Owner.parse("scala.collection.immutable"),
      scala.List(tparam),
      TypeDefKind.Class(isFinal = false, isAbstract = true, isCase = false, isValue = false)
    )
  }

  lazy val Function1TypeId: TypeId[Nothing] = {
    val tparam1 = TypeParam("T1", 0, Variance.Contravariant, TypeBounds.empty)
    val tparam2 = TypeParam("R", 1, Variance.Covariant, TypeBounds.empty)
    nominal(
      "Function1",
      Owner.parse("scala"),
      scala.List(tparam1, tparam2),
      TypeDefKind.Class(isFinal = false, isAbstract = true, isCase = false, isValue = false)
    )
  }

  object List {
    def apply[A](param: TypeId[A]): TypeId[scala.List[A]] =
      alias(
        "List",
        Owner.parse("scala.collection.immutable"),
        Nil,
        TypeRepr.Applied(TypeRepr.Ref(ListTypeId), scala.List(TypeRepr.Ref(param)))
      )
  }

  lazy val VectorTypeId: TypeId[Nothing] = {
    val tparam = TypeParam("A", 0, Variance.Covariant, TypeBounds.empty)
    nominal(
      "Vector",
      Owner.parse("scala.collection.immutable"),
      scala.List(tparam),
      TypeDefKind.Class(isFinal = true, isAbstract = false, isCase = false, isValue = false)
    )
  }

  object Vector {
    def apply[A](param: TypeId[A]): TypeId[scala.Vector[A]] =
      alias(
        "Vector",
        Owner.parse("scala.collection.immutable"),
        Nil,
        TypeRepr.Applied(TypeRepr.Ref(VectorTypeId), scala.List(TypeRepr.Ref(param)))
      )
  }

  lazy val SetTypeId: TypeId[Nothing] = {
    val tparam = TypeParam("A", 0, Variance.Covariant, TypeBounds.empty)
    nominal("Set", Owner.parse("scala.collection.immutable"), scala.List(tparam), TypeDefKind.Trait(false, Nil))
  }

  object Set {
    def apply[A](param: TypeId[A]): TypeId[scala.collection.immutable.Set[A]] =
      alias(
        "Set",
        Owner.parse("scala.collection.immutable"),
        Nil,
        TypeRepr.Applied(TypeRepr.Ref(SetTypeId), scala.List(TypeRepr.Ref(param)))
      )
  }

  lazy val MapTypeId: TypeId[Nothing] = {
    val kparam = TypeParam("K", 0, Variance.Invariant, TypeBounds.empty)
    val vparam = TypeParam("V", 1, Variance.Covariant, TypeBounds.empty)
    nominal("Map", Owner.parse("scala.collection.immutable"), scala.List(kparam, vparam), TypeDefKind.Trait(false, Nil))
  }

  object Map {
    def apply[K, V](k: TypeId[K], v: TypeId[V]): TypeId[scala.collection.immutable.Map[K, V]] =
      alias(
        "Map",
        Owner.parse("scala.collection.immutable"),
        Nil,
        TypeRepr.Applied(TypeRepr.Ref(MapTypeId), scala.List(TypeRepr.Ref(k), TypeRepr.Ref(v)))
      )
  }

  lazy val EitherTypeId: TypeId[Nothing] = {
    val lParam = TypeParam("L", 0, Variance.Covariant, TypeBounds.empty)
    val rParam = TypeParam("R", 1, Variance.Covariant, TypeBounds.empty)
    nominal(
      "Either",
      Owner.parse("scala.util"),
      scala.List(lParam, rParam),
      TypeDefKind.Class(isFinal = false, isAbstract = true, isCase = false, isValue = false)
    )
  }

  object Either {
    def apply[A, B](a: TypeId[A], b: TypeId[B]): TypeId[scala.util.Either[A, B]] =
      alias(
        "Either",
        Owner.parse("scala.util"),
        Nil,
        TypeRepr.Applied(TypeRepr.Ref(EitherTypeId), scala.List(TypeRepr.Ref(a), TypeRepr.Ref(b)))
      )
    def Left[A, B](a: TypeId[A]): TypeId[scala.util.Left[A, B]] = {
      val aParam = TypeParam("A", 0, Variance.Covariant, TypeBounds.exact(TypeRepr.Ref(a)))
      nominal("Left", Owner.parse("scala.util"), scala.List(aParam), TypeDefKind.Class(false, false, true, false))
    }
    def Right[A, B](b: TypeId[B]): TypeId[scala.util.Right[A, B]] = {
      val bParam = TypeParam("B", 0, Variance.Covariant, TypeBounds.exact(TypeRepr.Ref(b)))
      nominal("Right", Owner.parse("scala.util"), scala.List(bParam), TypeDefKind.Class(false, false, true, false))
    }
  }

  lazy val TryTypeId: TypeId[Nothing] = {
    val tparam = TypeParam("T", 0, Variance.Covariant, TypeBounds.empty)
    nominal(
      "Try",
      Owner.parse("scala.util"),
      scala.List(tparam),
      TypeDefKind.Class(isFinal = false, isAbstract = true, isCase = false, isValue = false)
    )
  }

  object Try {
    def apply[T](param: TypeId[T]): TypeId[scala.util.Try[T]] =
      alias(
        "Try",
        Owner.parse("scala.util"),
        Nil,
        TypeRepr.Applied(TypeRepr.Ref(TryTypeId), scala.List(TypeRepr.Ref(param)))
      )
    def Success[A](param: TypeId[A]): TypeId[scala.util.Success[A]] = {
      val tparam = TypeParam("A", 0, Variance.Covariant, TypeBounds.exact(TypeRepr.Ref(param)))
      nominal("Success", Owner.parse("scala.util"), scala.List(tparam), TypeDefKind.Class(false, false, true, false))
    }
    def Failure(): TypeId[scala.util.Failure[Nothing]] =
      nominal("Failure", Owner.parse("scala.util"), Nil, TypeDefKind.Class(false, false, true, false))
  }

  object Tuple {
    val Empty: TypeId[EmptyTuple] = nominal("EmptyTuple", Owner.parse("scala"), Nil, TypeDefKind.Object)

    lazy val TupleConsTypeId: TypeId[Nothing] = {
      val hParam = TypeParam("H", 0, Variance.Covariant, TypeBounds.empty)
      val tParam = TypeParam(
        "T",
        1,
        Variance.Covariant,
        TypeBounds.upper(TypeRepr.Ref(nominal("Tuple", Owner.parse("scala"), Nil, TypeDefKind.Trait(false, Nil))))
      )
      nominal(
        "*:",
        Owner.parse("scala"),
        scala.List(hParam, tParam),
        TypeDefKind.Class(isFinal = true, isAbstract = false, isCase = true, isValue = false)
      )
    }

    def apply(elems: List[TypeId[_]]): TypeId[Any] =
      alias(
        s"Tuple${elems.size}",
        Owner.parse("scala"),
        Nil,
        TypeRepr.Tuple(elems.map(e => zio.blocks.typeid.TypeRepr.TupleElement(Some(e.name), TypeRepr.Ref(e))))
      )
  }

  // Extractors
  object Nominal {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam], TypeDefKind, List[TypeRepr])] =
      if (id.aliasedTo.isEmpty && id.representation.isEmpty)
        Some((id.name, id.owner, id.typeParams, id.defKind, id.parents))
      else None
  }

  object Alias {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam], TypeRepr)] =
      id.aliasedTo.map(a => (id.name, id.owner, id.typeParams, a))
  }

  object Opaque {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[TypeParam], TypeRepr, TypeBounds)] =
      (id.defKind, id.representation) match {
        case (TypeDefKind.OpaqueType(bounds), Some(repr)) =>
          Some((id.name, id.owner, id.typeParams, repr, bounds))
        case _ => None
      }
  }

  object Sealed {
    def unapply(id: TypeId[?]): Option[(String, List[TypeRepr])] =
      id.defKind match {
        case TypeDefKind.Trait(true, subtypes) => Some((id.name, subtypes))
        case _                                 => None
      }
  }

  object Enum {
    def unapply(id: TypeId[?]): Option[(String, Owner, List[EnumCaseInfo])] =
      id.defKind match {
        case TypeDefKind.Enum(cases) => Some((id.name, id.owner, cases))
        case _                       => None
      }
  }
}
