package zio.blocks.typeid

import scala.language.experimental.macros

/**
 * Represents the identity of a type or type constructor.
 *
 * TypeId provides rich type identity information including:
 *   - The type's name
 *   - The owner (package/class/object where it's defined)
 *   - Type parameters (for type constructors)
 *   - Classification (nominal, alias, or opaque)
 *   - Parent types and self type
 *   - Annotations
 *
 * The phantom type parameter `A` ensures type safety when working with TypeId
 * values. In Scala 2, use existential types like `List[_]` or `Map[_, _]` for
 * type constructors.
 *
 * @tparam A
 *   The type (or type constructor) this TypeId represents
 */
sealed trait TypeId[A] {
  def name: String
  def owner: Owner
  def typeParams: List[TypeParam]
  def defKind: TypeDefKind
  def parents: List[TypeRepr]
  def selfType: Option[TypeRepr]
  def aliasedTo: Option[TypeRepr]
  def representation: Option[TypeRepr]
  def annotations: List[Annotation]

  final def arity: Int = typeParams.size

  final def fullName: String =
    if (owner.isRoot) name
    else s"${owner.asString}.$name"

  final def isProperType: Boolean = arity == 0
  final def isTypeConstructor: Boolean = arity > 0

  final def isClass: Boolean = defKind.isInstanceOf[TypeDefKind.Class]
  final def isTrait: Boolean = defKind.isInstanceOf[TypeDefKind.Trait]
  final def isObject: Boolean = defKind.isInstanceOf[TypeDefKind.Object]
  final def isEnum: Boolean = defKind.isInstanceOf[TypeDefKind.Enum]
  final def isAlias: Boolean = defKind == TypeDefKind.TypeAlias
  final def isOpaque: Boolean = defKind.isInstanceOf[TypeDefKind.OpaqueType]
  final def isAbstract: Boolean = defKind == TypeDefKind.AbstractType

  final def isSealed: Boolean = defKind match {
    case TypeDefKind.Trait(isSealed, _, _) => isSealed
    case _                                 => false
  }

  final def isCaseClass: Boolean = defKind match {
    case TypeDefKind.Class(_, _, isCase, _, _) => isCase
    case _                                     => false
  }

  final def isValueClass: Boolean = defKind match {
    case TypeDefKind.Class(_, _, _, isValue, _) => isValue
    case _                                      => false
  }

  final def enumCases: List[EnumCaseInfo] = defKind match {
    case TypeDefKind.Enum(cases, _) => cases
    case _                          => Nil
  }

  final def knownSubtypes: List[TypeRepr] = defKind match {
    case TypeDefKind.Trait(_, subtypes, _) => subtypes
    case _                                 => Nil
  }

  final def isTuple: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala") && norm.name.startsWith("Tuple")
  }

  final def isProduct: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala") && norm.name.startsWith("Product")
  }

  final def isSum: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala") && (norm.name == "Either" || norm.name == "Option")
  }

  final def isEither: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala.util") && norm.name == "Either"
  }

  final def isOption: Boolean = {
    val norm = TypeId.normalize(this)
    norm.owner == Owner.fromPackagePath("scala") && norm.name == "Option"
  }

  def isSubtypeOf(other: TypeId[_]): Boolean = {
    if (TypeId.structurallyEqual(this, other)) return true

    TypeId.checkParents(this.parents, other, Set(this.fullName))
  }

  def isSupertypeOf(other: TypeId[_]): Boolean = other.isSubtypeOf(this)

  def isEquivalentTo(other: TypeId[_]): Boolean =
    this.isSubtypeOf(other) && other.isSubtypeOf(this)

  override def equals(other: Any): Boolean = other match {
    case that: TypeId[_] => TypeId.structurallyEqual(this, that)
    case _               => false
  }

  override def hashCode(): Int = TypeId.structuralHash(this)

  override def toString: String = {
    val paramStr = if (typeParams.isEmpty) "" else typeParams.map(_.name).mkString("[", ", ", "]")
    val kindStr = if (aliasedTo.isDefined) "alias"
    else if (representation.isDefined) "opaque"
    else "nominal"
    s"TypeId.$kindStr($fullName$paramStr)"
  }
}

object TypeId {

  private final case class Impl[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam],
    defKind: TypeDefKind,
    parents: List[TypeRepr],
    selfType: Option[TypeRepr],
    aliasedTo: Option[TypeRepr],
    representation: Option[TypeRepr],
    annotations: List[Annotation]
  ) extends TypeId[A]

  def nominal[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    defKind: TypeDefKind = TypeDefKind.Unknown,
    parents: List[TypeRepr] = Nil,
    selfType: Option[TypeRepr] = None,
    annotations: List[Annotation] = Nil
  ): TypeId[A] = Impl[A](
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

  def alias[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    aliased: TypeRepr,
    annotations: List[Annotation] = Nil
  ): TypeId[A] = Impl[A](
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

  def opaque[A](
    name: String,
    owner: Owner,
    typeParams: List[TypeParam] = Nil,
    representation: TypeRepr,
    publicBounds: TypeBounds = TypeBounds.Unbounded,
    annotations: List[Annotation] = Nil
  ): TypeId[A] = Impl[A](
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

  object Nominal {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeDefKind, List[TypeRepr])] =
      if (id.aliasedTo.isEmpty && id.representation.isEmpty)
        Some((id.name, id.owner, id.typeParams, id.defKind, id.parents))
      else None
  }

  object Alias {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeRepr)] =
      id.aliasedTo.map(a => (id.name, id.owner, id.typeParams, a))
  }

  object Opaque {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[TypeParam], TypeRepr, TypeBounds)] =
      (id.defKind, id.representation) match {
        case (TypeDefKind.OpaqueType(bounds), Some(repr)) =>
          Some((id.name, id.owner, id.typeParams, repr, bounds))
        case _ => None
      }
  }

  object Sealed {
    def unapply(id: TypeId[_]): Option[(String, List[TypeRepr])] =
      id.defKind match {
        case TypeDefKind.Trait(true, subtypes, _) => Some((id.name, subtypes))
        case _                                    => None
      }
  }

  object Enum {
    def unapply(id: TypeId[_]): Option[(String, Owner, List[EnumCaseInfo])] =
      id.defKind match {
        case TypeDefKind.Enum(cases, _) => Some((id.name, id.owner, cases))
        case _                          => None
      }
  }

  def derived[A]: TypeId[A] = macro TypeIdMacros.derivedImpl[A]

  def normalize(id: TypeId[_]): TypeId[_] = id.aliasedTo match {
    case Some(TypeRepr.Ref(aliased))                      => normalize(aliased)
    case Some(TypeRepr.Applied(TypeRepr.Ref(aliased), _)) => normalize(aliased)
    case _                                                => id
  }

  def structurallyEqual(a: TypeId[_], b: TypeId[_]): Boolean = {
    val normA = normalize(a)
    val normB = normalize(b)

    if (normA.isOpaque && normB.isOpaque) {
      normA.fullName == normB.fullName && normA.typeParams == normB.typeParams
    } else if (normA.isOpaque || normB.isOpaque) {
      false
    } else {
      normA.fullName == normB.fullName && normA.typeParams == normB.typeParams
    }
  }

  def structuralHash(id: TypeId[_]): Int = {
    val norm = normalize(id)
    if (norm.isOpaque) {
      ("opaque", norm.fullName, norm.typeParams).hashCode()
    } else {
      (norm.fullName, norm.typeParams).hashCode()
    }
  }

  private def checkParents(parents: List[TypeRepr], target: TypeId[_], visited: Set[String]): Boolean =
    parents.exists { parent =>
      parent match {
        case TypeRepr.Ref(id) =>
          if (visited.contains(id.fullName)) false
          else if (structurallyEqual(id, target)) true
          else checkParents(id.parents, target, visited + id.fullName)
        case TypeRepr.Applied(TypeRepr.Ref(id), _) =>
          if (visited.contains(id.fullName)) false
          else if (id.fullName == target.fullName) true
          else checkParents(id.parents, target, visited + id.fullName)
        case _ => false
      }
    }

  implicit val unit: TypeId[Unit]             = nominal[Unit]("Unit", Owner.scala)
  implicit val boolean: TypeId[Boolean]       = nominal[Boolean]("Boolean", Owner.scala)
  implicit val byte: TypeId[Byte]             = nominal[Byte]("Byte", Owner.scala)
  implicit val short: TypeId[Short]           = nominal[Short]("Short", Owner.scala)
  implicit val int: TypeId[Int]               = nominal[Int]("Int", Owner.scala)
  implicit val long: TypeId[Long]             = nominal[Long]("Long", Owner.scala)
  implicit val float: TypeId[Float]           = nominal[Float]("Float", Owner.scala)
  implicit val double: TypeId[Double]         = nominal[Double]("Double", Owner.scala)
  implicit val char: TypeId[Char]             = nominal[Char]("Char", Owner.scala)
  implicit val string: TypeId[String]         = nominal[String]("String", Owner.javaLang)
  implicit val bigInt: TypeId[BigInt]         = nominal[BigInt]("BigInt", Owner.scala)
  implicit val bigDecimal: TypeId[BigDecimal] = nominal[BigDecimal]("BigDecimal", Owner.scala)

  implicit val option: TypeId[Option[_]] = nominal[Option[_]]("Option", Owner.scala, List(TypeParam.A))
  implicit val some: TypeId[Some[_]]     = nominal[Some[_]]("Some", Owner.scala, List(TypeParam.A))
  implicit val none: TypeId[None.type]   = nominal[None.type]("None", Owner.scala)
  implicit val list: TypeId[List[_]]     = nominal[List[_]]("List", Owner.scalaCollectionImmutable, List(TypeParam.A))
  implicit val vector: TypeId[Vector[_]] =
    nominal[Vector[_]]("Vector", Owner.scalaCollectionImmutable, List(TypeParam.A))
  implicit val set: TypeId[Set[_]]    = nominal[Set[_]]("Set", Owner.scalaCollectionImmutable, List(TypeParam.A))
  implicit val map: TypeId[Map[_, _]] =
    nominal[Map[_, _]]("Map", Owner.scalaCollectionImmutable, List(TypeParam.K, TypeParam.V))
  implicit val either: TypeId[Either[_, _]] =
    nominal[Either[_, _]]("Either", Owner.scala, List(TypeParam.A, TypeParam.B))

  implicit val dayOfWeek: TypeId[java.time.DayOfWeek]         = nominal[java.time.DayOfWeek]("DayOfWeek", Owner.javaTime)
  implicit val duration: TypeId[java.time.Duration]           = nominal[java.time.Duration]("Duration", Owner.javaTime)
  implicit val instant: TypeId[java.time.Instant]             = nominal[java.time.Instant]("Instant", Owner.javaTime)
  implicit val localDate: TypeId[java.time.LocalDate]         = nominal[java.time.LocalDate]("LocalDate", Owner.javaTime)
  implicit val localDateTime: TypeId[java.time.LocalDateTime] =
    nominal[java.time.LocalDateTime]("LocalDateTime", Owner.javaTime)
  implicit val localTime: TypeId[java.time.LocalTime]           = nominal[java.time.LocalTime]("LocalTime", Owner.javaTime)
  implicit val month: TypeId[java.time.Month]                   = nominal[java.time.Month]("Month", Owner.javaTime)
  implicit val monthDay: TypeId[java.time.MonthDay]             = nominal[java.time.MonthDay]("MonthDay", Owner.javaTime)
  implicit val offsetDateTime: TypeId[java.time.OffsetDateTime] =
    nominal[java.time.OffsetDateTime]("OffsetDateTime", Owner.javaTime)
  implicit val offsetTime: TypeId[java.time.OffsetTime]       = nominal[java.time.OffsetTime]("OffsetTime", Owner.javaTime)
  implicit val period: TypeId[java.time.Period]               = nominal[java.time.Period]("Period", Owner.javaTime)
  implicit val year: TypeId[java.time.Year]                   = nominal[java.time.Year]("Year", Owner.javaTime)
  implicit val yearMonth: TypeId[java.time.YearMonth]         = nominal[java.time.YearMonth]("YearMonth", Owner.javaTime)
  implicit val zoneId: TypeId[java.time.ZoneId]               = nominal[java.time.ZoneId]("ZoneId", Owner.javaTime)
  implicit val zoneOffset: TypeId[java.time.ZoneOffset]       = nominal[java.time.ZoneOffset]("ZoneOffset", Owner.javaTime)
  implicit val zonedDateTime: TypeId[java.time.ZonedDateTime] =
    nominal[java.time.ZonedDateTime]("ZonedDateTime", Owner.javaTime)

  implicit val currency: TypeId[java.util.Currency] = nominal[java.util.Currency]("Currency", Owner.javaUtil)
  implicit val uuid: TypeId[java.util.UUID]         = nominal[java.util.UUID]("UUID", Owner.javaUtil)

  private[typeid] object Owner {
    val scala: zio.blocks.typeid.Owner                    = zio.blocks.typeid.Owner.fromPackagePath("scala")
    val scalaCollectionImmutable: zio.blocks.typeid.Owner = zio.blocks.typeid.Owner.fromPackagePath("scala.collection.immutable")
    val javaLang: zio.blocks.typeid.Owner                 = zio.blocks.typeid.Owner.fromPackagePath("java.lang")
    val javaTime: zio.blocks.typeid.Owner                 = zio.blocks.typeid.Owner.fromPackagePath("java.time")
    val javaUtil: zio.blocks.typeid.Owner                 = zio.blocks.typeid.Owner.fromPackagePath("java.util")
  }
}
