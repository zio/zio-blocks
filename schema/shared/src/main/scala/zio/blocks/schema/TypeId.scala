package zio.blocks.schema

import scala.collection.immutable.ArraySeq

/**
 * A type identifier that provides a complete model of Scala's type system.
 *
 * TypeId replaces the simpler TypeName with a richer representation that
 * supports:
 *   - Nominal types (classes, traits, objects)
 *   - Applied types (type constructors with arguments like List[Int])
 *   - Type aliases (transparent, expand to underlying)
 *   - Opaque types (not equal to their representation)
 *   - Structural types (refinement types with members)
 *   - Intersection types (A & B)
 *   - Union types (A | B)
 *   - Function types (A => B)
 *   - Tuple types ((A, B, C))
 *   - Singleton types (x.type)
 *   - Type parameters (T in List[T])
 *
 * @tparam A
 *   The Scala type this TypeId represents
 */
sealed trait TypeId[A] {

  /**
   * The underlying type representation.
   */
  def repr: TypeRepr

  /**
   * The simple name of this type.
   */
  def name: String = repr.name

  /**
   * The fully qualified name of this type.
   */
  def fullName: String = repr.fullName

  /**
   * Checks if this type is a subtype of another type.
   */
  def isSubtypeOf(other: TypeId[_]): Boolean =
    TypeId.isSubtypeOf(this.repr, other.repr)

  /**
   * Converts to TypeName for backward compatibility.
   */
  def toTypeName: TypeName[A] = TypeId.toTypeName(repr)

  override def equals(obj: Any): Boolean = obj match {
    case other: TypeId[_] => TypeId.structurallyEqual(this.repr, other.repr)
    case _                => false
  }

  override def hashCode(): Int = TypeId.structuralHash(repr)

  override def toString: String = fullName
}

object TypeId {

  // TypeId variants for different type forms

  final case class Nominal[A](repr: TypeRepr.Nominal) extends TypeId[A]

  final case class Applied[A](repr: TypeRepr.Applied) extends TypeId[A]

  final case class Alias[A](repr: TypeRepr.Alias) extends TypeId[A]

  final case class Opaque[A](repr: TypeRepr.Opaque) extends TypeId[A]

  final case class Structural[A](repr: TypeRepr.Structural) extends TypeId[A]

  final case class Intersection[A](repr: TypeRepr.Intersection) extends TypeId[A]

  final case class Union[A](repr: TypeRepr.Union) extends TypeId[A]

  final case class Function[A](repr: TypeRepr.Function) extends TypeId[A]

  final case class Tuple[A](repr: TypeRepr.Tuple) extends TypeId[A]

  final case class Singleton[A](repr: TypeRepr.Singleton) extends TypeId[A]

  final case class TypeParam[A](repr: TypeRepr.TypeParam) extends TypeId[A]

  /**
   * Creates a TypeId from a TypeRepr.
   */
  def fromRepr[A](r: TypeRepr): TypeId[A] = (r match {
    case r: TypeRepr.Nominal      => Nominal(r)
    case r: TypeRepr.Applied      => Applied(r)
    case r: TypeRepr.Alias        => Alias(r)
    case r: TypeRepr.Opaque       => Opaque(r)
    case r: TypeRepr.Structural   => Structural(r)
    case r: TypeRepr.Intersection => Intersection(r)
    case r: TypeRepr.Union        => Union(r)
    case r: TypeRepr.Function     => Function(r)
    case r: TypeRepr.Tuple        => Tuple(r)
    case r: TypeRepr.Singleton    => Singleton(r)
    case r: TypeRepr.TypeParam    => TypeParam(r)
  }).asInstanceOf[TypeId[A]]

  /**
   * Creates a nominal TypeId for a type in a given package with a simple name.
   */
  def nominal[A](owner: Owner, name: String, parents: Seq[TypeRepr] = Nil): TypeId[A] =
    Nominal(TypeRepr.Nominal(owner, name, parents))

  /**
   * Creates an applied TypeId for a type constructor with arguments.
   */
  def applied[A](constructor: TypeRepr, args: Seq[TypeRepr]): TypeId[A] =
    Applied(TypeRepr.Applied(constructor, args))

  // Pre-defined TypeIds for primitive types
  val unit: TypeId[Unit]             = nominal(Owner.scala, "Unit")
  val boolean: TypeId[Boolean]       = nominal(Owner.scala, "Boolean")
  val byte: TypeId[Byte]             = nominal(Owner.scala, "Byte")
  val short: TypeId[Short]           = nominal(Owner.scala, "Short")
  val int: TypeId[Int]               = nominal(Owner.scala, "Int")
  val long: TypeId[Long]             = nominal(Owner.scala, "Long")
  val float: TypeId[Float]           = nominal(Owner.scala, "Float")
  val double: TypeId[Double]         = nominal(Owner.scala, "Double")
  val char: TypeId[Char]             = nominal(Owner.scala, "Char")
  val string: TypeId[String]         = nominal(Owner.scala, "String")
  val bigInt: TypeId[BigInt]         = nominal(Owner.scala, "BigInt")
  val bigDecimal: TypeId[BigDecimal] = nominal(Owner.scala, "BigDecimal")

  val dynamicValue: TypeId[DynamicValue] = nominal(Owner.zioBlocksSchema, "DynamicValue")

  // java.time types
  val dayOfWeek: TypeId[java.time.DayOfWeek]           = nominal(Owner.javaTime, "DayOfWeek")
  val duration: TypeId[java.time.Duration]             = nominal(Owner.javaTime, "Duration")
  val instant: TypeId[java.time.Instant]               = nominal(Owner.javaTime, "Instant")
  val localDate: TypeId[java.time.LocalDate]           = nominal(Owner.javaTime, "LocalDate")
  val localDateTime: TypeId[java.time.LocalDateTime]   = nominal(Owner.javaTime, "LocalDateTime")
  val localTime: TypeId[java.time.LocalTime]           = nominal(Owner.javaTime, "LocalTime")
  val month: TypeId[java.time.Month]                   = nominal(Owner.javaTime, "Month")
  val monthDay: TypeId[java.time.MonthDay]             = nominal(Owner.javaTime, "MonthDay")
  val offsetDateTime: TypeId[java.time.OffsetDateTime] = nominal(Owner.javaTime, "OffsetDateTime")
  val offsetTime: TypeId[java.time.OffsetTime]         = nominal(Owner.javaTime, "OffsetTime")
  val period: TypeId[java.time.Period]                 = nominal(Owner.javaTime, "Period")
  val year: TypeId[java.time.Year]                     = nominal(Owner.javaTime, "Year")
  val yearMonth: TypeId[java.time.YearMonth]           = nominal(Owner.javaTime, "YearMonth")
  val zoneId: TypeId[java.time.ZoneId]                 = nominal(Owner.javaTime, "ZoneId")
  val zoneOffset: TypeId[java.time.ZoneOffset]         = nominal(Owner.javaTime, "ZoneOffset")
  val zonedDateTime: TypeId[java.time.ZonedDateTime]   = nominal(Owner.javaTime, "ZonedDateTime")

  // java.util types
  val currency: TypeId[java.util.Currency] = nominal(Owner.javaUtil, "Currency")
  val uuid: TypeId[java.util.UUID]         = nominal(Owner.javaUtil, "UUID")

  // None
  val none: TypeId[None.type] = nominal(Owner.scala, "None")

  // Type constructors for collections
  private[this] val _some: TypeRepr.Nominal       = TypeRepr.Nominal(Owner.scala, "Some")
  private[this] val _option: TypeRepr.Nominal     = TypeRepr.Nominal(Owner.scala, "Option")
  private[this] val _list: TypeRepr.Nominal       = TypeRepr.Nominal(Owner.scalaCollectionImmutable, "List")
  private[this] val _map: TypeRepr.Nominal        = TypeRepr.Nominal(Owner.scalaCollectionImmutable, "Map")
  private[this] val _set: TypeRepr.Nominal        = TypeRepr.Nominal(Owner.scalaCollectionImmutable, "Set")
  private[this] val _vector: TypeRepr.Nominal     = TypeRepr.Nominal(Owner.scalaCollectionImmutable, "Vector")
  private[this] val _arraySeq: TypeRepr.Nominal   = TypeRepr.Nominal(Owner.scalaCollectionImmutable, "ArraySeq")
  private[this] val _indexedSeq: TypeRepr.Nominal = TypeRepr.Nominal(Owner.scalaCollectionImmutable, "IndexedSeq")
  private[this] val _seq: TypeRepr.Nominal        = TypeRepr.Nominal(Owner.scalaCollectionImmutable, "Seq")

  def some[A](element: TypeId[A]): TypeId[Some[A]] =
    Applied(TypeRepr.Applied(_some, Seq(element.repr)))

  def option[A](element: TypeId[A]): TypeId[Option[A]] =
    Applied(TypeRepr.Applied(_option, Seq(element.repr)))

  def list[A](element: TypeId[A]): TypeId[List[A]] =
    Applied(TypeRepr.Applied(_list, Seq(element.repr)))

  def map[K, V](key: TypeId[K], value: TypeId[V]): TypeId[Map[K, V]] =
    Applied(TypeRepr.Applied(_map, Seq(key.repr, value.repr)))

  def set[A](element: TypeId[A]): TypeId[Set[A]] =
    Applied(TypeRepr.Applied(_set, Seq(element.repr)))

  def vector[A](element: TypeId[A]): TypeId[Vector[A]] =
    Applied(TypeRepr.Applied(_vector, Seq(element.repr)))

  def arraySeq[A](element: TypeId[A]): TypeId[ArraySeq[A]] =
    Applied(TypeRepr.Applied(_arraySeq, Seq(element.repr)))

  def indexedSeq[A](element: TypeId[A]): TypeId[IndexedSeq[A]] =
    Applied(TypeRepr.Applied(_indexedSeq, Seq(element.repr)))

  def seq[A](element: TypeId[A]): TypeId[Seq[A]] =
    Applied(TypeRepr.Applied(_seq, Seq(element.repr)))

  /**
   * Converts a TypeRepr to a TypeName for backward compatibility.
   */
  def toTypeName[A](repr: TypeRepr): TypeName[A] = {
    def loop(r: TypeRepr): TypeName[_] = r match {
      case TypeRepr.Nominal(owner, name, _) =>
        new TypeName(new Namespace(owner.packages, owner.values), name, Nil)
      case TypeRepr.Applied(constructor, args) =>
        val base = loop(constructor)
        base.copy(params = args.map(loop))
      case TypeRepr.Alias(_, _, underlying) =>
        loop(underlying)
      case TypeRepr.Opaque(owner, name, _) =>
        new TypeName(new Namespace(owner.packages, owner.values), name, Nil)
      case TypeRepr.Structural(members) =>
        new TypeName(new Namespace(Nil, Nil), s"{${members.map(_.name).mkString("; ")}}", Nil)
      case TypeRepr.Intersection(types) =>
        new TypeName(new Namespace(Nil, Nil), "&", types.map(loop))
      case TypeRepr.Union(types) =>
        new TypeName(new Namespace(Nil, Nil), "|", types.map(loop))
      case TypeRepr.Function(params, result) =>
        val paramReprs = params.map(loop)
        val resultRepr = loop(result)
        new TypeName(new Namespace("scala" :: Nil, Nil), s"Function${params.size}", paramReprs :+ resultRepr)
      case TypeRepr.Tuple(elements) =>
        val elemReprs = elements.map(loop)
        new TypeName(new Namespace("scala" :: Nil, Nil), s"Tuple${elements.size}", elemReprs)
      case TypeRepr.Singleton(owner, name, _) =>
        new TypeName(new Namespace(owner.packages, owner.values), name, Nil)
      case TypeRepr.TypeParam(name, _, _) =>
        new TypeName(new Namespace(Nil, Nil), name, Nil)
    }
    loop(repr).asInstanceOf[TypeName[A]]
  }

  /**
   * Creates a TypeId from a TypeName for backward compatibility.
   */
  def fromTypeName[A](typeName: TypeName[A]): TypeId[A] = {
    val owner = Owner(typeName.namespace.packages, typeName.namespace.values)
    val base  = TypeRepr.Nominal(owner, typeName.name)
    val repr  =
      if (typeName.params.isEmpty) base
      else TypeRepr.Applied(base, typeName.params.map(p => fromTypeName(p).repr))
    fromRepr(repr)
  }

  /**
   * Normalizes a TypeRepr by expanding aliases.
   */
  def normalize(repr: TypeRepr): TypeRepr = repr match {
    case TypeRepr.Alias(_, _, underlying)    => normalize(underlying)
    case TypeRepr.Applied(constructor, args) => TypeRepr.Applied(normalize(constructor), args.map(normalize))
    case TypeRepr.Intersection(types)        => TypeRepr.Intersection(types.map(normalize).sortBy(_.fullName))
    case TypeRepr.Union(types)               => TypeRepr.Union(types.map(normalize).sortBy(_.fullName))
    case TypeRepr.Tuple(elements)            => TypeRepr.Tuple(elements.map(normalize))
    case TypeRepr.Function(params, result)   => TypeRepr.Function(params.map(normalize), normalize(result))
    case TypeRepr.Structural(members)        => TypeRepr.Structural(members.sortBy(_.name))
    case other                               => other
  }

  /**
   * Compares two TypeReprs for structural equality after normalization. Type
   * aliases are expanded, but opaque types maintain their identity.
   */
  def structurallyEqual(a: TypeRepr, b: TypeRepr): Boolean = {
    val na = normalize(a)
    val nb = normalize(b)
    structurallyEqualNormalized(na, nb)
  }

  private def structurallyEqualNormalized(a: TypeRepr, b: TypeRepr): Boolean = (a, b) match {
    case (TypeRepr.Nominal(o1, n1, _), TypeRepr.Nominal(o2, n2, _)) =>
      n1 == n2 && o1 == o2
    case (TypeRepr.Applied(c1, args1), TypeRepr.Applied(c2, args2)) =>
      structurallyEqualNormalized(c1, c2) &&
      args1.size == args2.size &&
      args1.zip(args2).forall { case (a1, a2) => structurallyEqualNormalized(a1, a2) }
    case (TypeRepr.Opaque(o1, n1, _), TypeRepr.Opaque(o2, n2, _)) =>
      n1 == n2 && o1 == o2
    case (TypeRepr.Structural(m1), TypeRepr.Structural(m2)) =>
      m1.size == m2.size &&
      m1.zip(m2).forall { case (member1, member2) =>
        member1.name == member2.name && structurallyEqualNormalized(member1.tpe, member2.tpe)
      }
    case (TypeRepr.Intersection(t1), TypeRepr.Intersection(t2)) =>
      t1.size == t2.size &&
      t1.zip(t2).forall { case (a1, a2) => structurallyEqualNormalized(a1, a2) }
    case (TypeRepr.Union(t1), TypeRepr.Union(t2)) =>
      t1.size == t2.size &&
      t1.zip(t2).forall { case (a1, a2) => structurallyEqualNormalized(a1, a2) }
    case (TypeRepr.Function(p1, r1), TypeRepr.Function(p2, r2)) =>
      p1.size == p2.size &&
      p1.zip(p2).forall { case (a1, a2) => structurallyEqualNormalized(a1, a2) } &&
      structurallyEqualNormalized(r1, r2)
    case (TypeRepr.Tuple(e1), TypeRepr.Tuple(e2)) =>
      e1.size == e2.size &&
      e1.zip(e2).forall { case (a1, a2) => structurallyEqualNormalized(a1, a2) }
    case (TypeRepr.Singleton(o1, n1, _), TypeRepr.Singleton(o2, n2, _)) =>
      n1 == n2 && o1 == o2
    case (TypeRepr.TypeParam(n1, i1, b1), TypeRepr.TypeParam(n2, i2, b2)) =>
      n1 == n2 && i1 == i2 && boundsEqual(b1, b2)
    case _ => false
  }

  private def boundsEqual(b1: TypeBounds, b2: TypeBounds): Boolean =
    structurallyEqualNormalized(b1.lower, b2.lower) &&
      structurallyEqualNormalized(b1.upper, b2.upper)

  /**
   * Computes a stable hash code for a TypeRepr.
   */
  def structuralHash(repr: TypeRepr): Int = {
    val normalized = normalize(repr)
    structuralHashNormalized(normalized)
  }

  private def structuralHashNormalized(repr: TypeRepr): Int = repr match {
    case TypeRepr.Nominal(owner, name, _) =>
      var h = name.hashCode
      h = h * 31 + owner.hashCode
      h
    case TypeRepr.Applied(constructor, args) =>
      var h = structuralHashNormalized(constructor)
      args.foreach(arg => h = h * 31 + structuralHashNormalized(arg))
      h
    case TypeRepr.Opaque(owner, name, _) =>
      var h = "opaque".hashCode
      h = h * 31 + name.hashCode
      h = h * 31 + owner.hashCode
      h
    case TypeRepr.Structural(members) =>
      var h = "structural".hashCode
      members.foreach { m =>
        h = h * 31 + m.name.hashCode
        h = h * 31 + structuralHashNormalized(m.tpe)
      }
      h
    case TypeRepr.Intersection(types) =>
      var h = "&".hashCode
      types.foreach(t => h = h * 31 + structuralHashNormalized(t))
      h
    case TypeRepr.Union(types) =>
      var h = "|".hashCode
      types.foreach(t => h = h * 31 + structuralHashNormalized(t))
      h
    case TypeRepr.Function(params, result) =>
      var h = "=>".hashCode
      params.foreach(p => h = h * 31 + structuralHashNormalized(p))
      h = h * 31 + structuralHashNormalized(result)
      h
    case TypeRepr.Tuple(elements) =>
      var h = "Tuple".hashCode
      elements.foreach(e => h = h * 31 + structuralHashNormalized(e))
      h
    case TypeRepr.Singleton(owner, name, _) =>
      var h = "singleton".hashCode
      h = h * 31 + name.hashCode
      h = h * 31 + owner.hashCode
      h
    case TypeRepr.TypeParam(name, index, _) =>
      var h = "param".hashCode
      h = h * 31 + name.hashCode
      h = h * 31 + index
      h
    case TypeRepr.Alias(_, _, underlying) =>
      // Should not reach here after normalization, but handle anyway
      structuralHashNormalized(underlying)
  }

  /**
   * Checks if type `a` is a subtype of type `b` based on parent information.
   */
  def isSubtypeOf(a: TypeRepr, b: TypeRepr): Boolean =
    if (structurallyEqual(a, b)) true
    else
      a match {
        case TypeRepr.Nominal(_, _, parents) =>
          parents.exists(p => structurallyEqual(p, b) || isSubtypeOf(p, b))
        case TypeRepr.Applied(constructor, _) =>
          constructor match {
            case TypeRepr.Nominal(_, _, parents) =>
              parents.exists(p => structurallyEqual(p, b) || isSubtypeOf(p, b))
            case _ => false
          }
        case TypeRepr.Opaque(_, _, parents) =>
          parents.exists(p => structurallyEqual(p, b) || isSubtypeOf(p, b))
        case TypeRepr.Intersection(types) =>
          // A & B <: C if A <: C or B <: C
          types.exists(t => isSubtypeOf(t, b))
        case TypeRepr.Union(types) =>
          // A | B <: C if A <: C and B <: C
          types.forall(t => isSubtypeOf(t, b))
        case _ => false
      }
}

/**
 * Represents the owner/location of a type definition.
 */
final case class Owner(packages: Seq[String], values: Seq[String] = Nil) {

  /**
   * The full path as a sequence of segments.
   */
  val elements: Seq[String] = packages ++ values

  /**
   * The fully qualified path as a string.
   */
  def fullPath: String = elements.mkString(".")

  override def toString: String = fullPath
}

object Owner {
  val javaTime: Owner                 = Owner(Seq("java", "time"))
  val javaUtil: Owner                 = Owner(Seq("java", "util"))
  val scala: Owner                    = Owner(Seq("scala"))
  val scalaCollectionImmutable: Owner = Owner(Seq("scala", "collection", "immutable"))
  val zioBlocksSchema: Owner          = Owner(Seq("zio", "blocks", "schema"))

  def fromNamespace(ns: Namespace): Owner = Owner(ns.packages, ns.values)
}

/**
 * Type bounds for type parameters.
 */
final case class TypeBounds(lower: TypeRepr, upper: TypeRepr)

object TypeBounds {
  val empty: TypeBounds = TypeBounds(TypeRepr.Nothing, TypeRepr.Any)
}

/**
 * A member of a structural type.
 */
final case class TypeMember(name: String, tpe: TypeRepr)

/**
 * Variance of type parameters.
 */
sealed trait Variance

object Variance {
  case object Invariant     extends Variance
  case object Covariant     extends Variance
  case object Contravariant extends Variance
}

/**
 * Representation of a Scala type structure. This is the core data model for
 * TypeId.
 */
sealed trait TypeRepr {
  def name: String
  def fullName: String
}

object TypeRepr {

  /**
   * A nominal (named) type with an owner and simple name.
   */
  final case class Nominal(
    owner: Owner,
    name: String,
    parents: Seq[TypeRepr] = Nil
  ) extends TypeRepr {
    def fullName: String =
      if (owner.elements.isEmpty) name
      else s"${owner.fullPath}.$name"
  }

  /**
   * An applied type (type constructor with arguments).
   */
  final case class Applied(
    constructor: TypeRepr,
    args: Seq[TypeRepr]
  ) extends TypeRepr {
    def name: String     = s"${constructor.name}[${args.map(_.name).mkString(", ")}]"
    def fullName: String = s"${constructor.fullName}[${args.map(_.fullName).mkString(", ")}]"
  }

  /**
   * A type alias (transparent - expands to underlying type).
   */
  final case class Alias(
    owner: Owner,
    name: String,
    underlying: TypeRepr
  ) extends TypeRepr {
    def fullName: String =
      if (owner.elements.isEmpty) name
      else s"${owner.fullPath}.$name"
  }

  /**
   * An opaque type (not equal to underlying type).
   */
  final case class Opaque(
    owner: Owner,
    name: String,
    parents: Seq[TypeRepr] = Nil
  ) extends TypeRepr {
    def fullName: String =
      if (owner.elements.isEmpty) name
      else s"${owner.fullPath}.$name"
  }

  /**
   * A structural type (refinement type).
   */
  final case class Structural(members: Seq[TypeMember]) extends TypeRepr {
    def name: String     = s"{${members.map(m => s"${m.name}: ${m.tpe.name}").mkString("; ")}}"
    def fullName: String = s"{${members.map(m => s"${m.name}: ${m.tpe.fullName}").mkString("; ")}}"
  }

  /**
   * An intersection type (A & B).
   */
  final case class Intersection(types: Seq[TypeRepr]) extends TypeRepr {
    def name: String     = types.map(_.name).mkString(" & ")
    def fullName: String = types.map(_.fullName).mkString(" & ")
  }

  /**
   * A union type (A | B).
   */
  final case class Union(types: Seq[TypeRepr]) extends TypeRepr {
    def name: String     = types.map(_.name).mkString(" | ")
    def fullName: String = types.map(_.fullName).mkString(" | ")
  }

  /**
   * A function type.
   */
  final case class Function(
    params: Seq[TypeRepr],
    result: TypeRepr
  ) extends TypeRepr {
    def name: String =
      if (params.size == 1) s"${params.head.name} => ${result.name}"
      else s"(${params.map(_.name).mkString(", ")}) => ${result.name}"

    def fullName: String =
      if (params.size == 1) s"${params.head.fullName} => ${result.fullName}"
      else s"(${params.map(_.fullName).mkString(", ")}) => ${result.fullName}"
  }

  /**
   * A tuple type.
   */
  final case class Tuple(elements: Seq[TypeRepr]) extends TypeRepr {
    def name: String     = s"(${elements.map(_.name).mkString(", ")})"
    def fullName: String = s"(${elements.map(_.fullName).mkString(", ")})"
  }

  /**
   * A singleton type (e.g., x.type or object type).
   */
  final case class Singleton(
    owner: Owner,
    name: String,
    underlying: Option[TypeRepr] = None
  ) extends TypeRepr {
    def fullName: String =
      if (owner.elements.isEmpty) s"$name.type"
      else s"${owner.fullPath}.$name.type"
  }

  /**
   * A type parameter reference.
   */
  final case class TypeParam(
    name: String,
    index: Int,
    bounds: TypeBounds = TypeBounds.empty
  ) extends TypeRepr {
    def fullName: String = name
  }

  // Common type representations
  val Nothing: TypeRepr = Nominal(Owner.scala, "Nothing")
  val Any: TypeRepr     = Nominal(Owner.scala, "Any")
  val AnyRef: TypeRepr  = Nominal(Owner.scala, "AnyRef")
  val AnyVal: TypeRepr  = Nominal(Owner.scala, "AnyVal")
  val Unit: TypeRepr    = Nominal(Owner.scala, "Unit")
}
