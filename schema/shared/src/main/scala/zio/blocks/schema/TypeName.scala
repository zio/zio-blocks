package zio.blocks.schema

import zio.blocks.typeid.TypeId
import zio.blocks.typeid.Owner

/**
 * DEPRECATED: TypeName is replaced by TypeId. This file provides backward
 * compatibility during migration.
 *
 * TypeName is now an alias for TypeId. All new code should use TypeId directly.
 *
 * @deprecated
 *   Use zio.blocks.typeid.TypeId instead
 */
@deprecated("Use zio.blocks.typeid.TypeId instead", "2.0.0")
object TypeNameCompat {
  type TypeName[A] = TypeId[A]

  // Re-export from TypeId
  val unit: TypeId[Unit]             = TypeId.unit
  val boolean: TypeId[Boolean]       = TypeId.boolean
  val byte: TypeId[Byte]             = TypeId.byte
  val short: TypeId[Short]           = TypeId.short
  val int: TypeId[Int]               = TypeId.int
  val long: TypeId[Long]             = TypeId.long
  val float: TypeId[Float]           = TypeId.float
  val double: TypeId[Double]         = TypeId.double
  val char: TypeId[Char]             = TypeId.char
  val string: TypeId[String]         = TypeId.string
  val bigInt: TypeId[BigInt]         = TypeId.bigInt
  val bigDecimal: TypeId[BigDecimal] = TypeId.bigDecimal

  // Java time types
  val dayOfWeek: TypeId[java.time.DayOfWeek]           = TypeId.dayOfWeek
  val duration: TypeId[java.time.Duration]             = TypeId.duration
  val instant: TypeId[java.time.Instant]               = TypeId.instant
  val localDate: TypeId[java.time.LocalDate]           = TypeId.localDate
  val localDateTime: TypeId[java.time.LocalDateTime]   = TypeId.localDateTime
  val localTime: TypeId[java.time.LocalTime]           = TypeId.localTime
  val month: TypeId[java.time.Month]                   = TypeId.month
  val monthDay: TypeId[java.time.MonthDay]             = TypeId.monthDay
  val offsetDateTime: TypeId[java.time.OffsetDateTime] = TypeId.offsetDateTime
  val offsetTime: TypeId[java.time.OffsetTime]         = TypeId.offsetTime
  val period: TypeId[java.time.Period]                 = TypeId.period
  val year: TypeId[java.time.Year]                     = TypeId.year
  val yearMonth: TypeId[java.time.YearMonth]           = TypeId.yearMonth
  val zoneId: TypeId[java.time.ZoneId]                 = TypeId.zoneId
  val zoneOffset: TypeId[java.time.ZoneOffset]         = TypeId.zoneOffset
  val zonedDateTime: TypeId[java.time.ZonedDateTime]   = TypeId.zonedDateTime

  // Java util types
  val currency: TypeId[java.util.Currency] = TypeId.currency
  val uuid: TypeId[java.util.UUID]         = TypeId.uuid

  // None type
  val none: TypeId[None.type] = TypeId.none

  // DynamicValue - needs to be defined in typeid
  val dynamicValue: TypeId[DynamicValue] = TypeId.nominal[DynamicValue]("DynamicValue", Owner.zioBlocksSchema)

  // Factory methods for parameterized types
  def some[A](element: TypeId[A]): TypeId[Some[A]]                                    = TypeId.some(element)
  def option[A](element: TypeId[A]): TypeId[Option[A]]                                = TypeId.option(element)
  def list[A](element: TypeId[A]): TypeId[List[A]]                                    = TypeId.list(element)
  def map[K, V](key: TypeId[K], value: TypeId[V]): TypeId[Map[K, V]]                  = TypeId.map(key, value)
  def set[A](element: TypeId[A]): TypeId[Set[A]]                                      = TypeId.set(element)
  def vector[A](element: TypeId[A]): TypeId[Vector[A]]                                = TypeId.vector(element)
  def arraySeq[A](element: TypeId[A]): TypeId[scala.collection.immutable.ArraySeq[A]] = TypeId.arraySeq(element)
  def indexedSeq[A](element: TypeId[A]): TypeId[IndexedSeq[A]]                        = TypeId.indexedSeq(element)
  def seq[A](element: TypeId[A]): TypeId[Seq[A]]                                      = TypeId.seq(element)

  // Factory for creating TypeId from Namespace-like data (for migration)
  def apply[A](namespace: Namespace, name: String, params: Seq[TypeId[_]] = Nil): TypeId[A] = {
    val owner = namespaceToOwner(namespace)
    TypeId.nominal[A](name, owner, Nil)
  }

  private def namespaceToOwner(ns: Namespace): Owner =
    Owner(ns.packages.toList.map(Owner.Package(_)) ++ ns.values.toList.map(Owner.Term(_)))
}

/**
 * DEPRECATED: Namespace is replaced by Owner. This class provides backward
 * compatibility during migration.
 *
 * @deprecated
 *   Use zio.blocks.typeid.Owner instead
 */
@deprecated("Use zio.blocks.typeid.Owner instead", "2.0.0")
final case class Namespace(packages: Seq[String], values: Seq[String] = Nil) {
  val elements: Seq[String] = packages ++ values

  def toOwner: Owner =
    Owner(packages.toList.map(Owner.Package(_)) ++ values.toList.map(Owner.Term(_)))
}

@deprecated("Use zio.blocks.typeid.Owner instead", "2.0.0")
object Namespace {
  private[schema] val javaTime: Namespace                 = new Namespace("java" :: "time" :: Nil)
  private[schema] val javaUtil: Namespace                 = new Namespace("java" :: "util" :: Nil)
  private[schema] val scala: Namespace                    = new Namespace("scala" :: Nil)
  private[schema] val scalaCollectionImmutable: Namespace = new Namespace("scala" :: "collection" :: "immutable" :: Nil)
  private[schema] val zioBlocksSchema: Namespace          = new Namespace("zio" :: "blocks" :: "schema" :: Nil)
}

// Type alias for gradual migration - TypeName is now TypeId
@deprecated("Use zio.blocks.typeid.TypeId instead", "2.0.0")
final case class TypeName[A](namespace: Namespace, name: String, params: Seq[TypeName[?]] = Nil) {

  /**
   * Convert to the new TypeId representation.
   */
  def toTypeId: TypeId[A] = {
    val owner = namespace.toOwner
    TypeId.nominal[A](name, owner, Nil)
  }
}

@deprecated("Use zio.blocks.typeid.TypeId instead", "2.0.0")
object TypeName {
  val unit: TypeName[Unit] = new TypeName(Namespace.scala, "Unit")

  val boolean: TypeName[Boolean] = new TypeName(Namespace.scala, "Boolean")

  val byte: TypeName[Byte] = new TypeName(Namespace.scala, "Byte")

  val short: TypeName[Short] = new TypeName(Namespace.scala, "Short")

  val int: TypeName[Int] = new TypeName(Namespace.scala, "Int")

  val long: TypeName[Long] = new TypeName(Namespace.scala, "Long")

  val float: TypeName[Float] = new TypeName(Namespace.scala, "Float")

  val double: TypeName[Double] = new TypeName(Namespace.scala, "Double")

  val char: TypeName[Char] = new TypeName(Namespace.scala, "Char")

  val string: TypeName[String] = new TypeName(Namespace.scala, "String")

  val bigInt: TypeName[BigInt] = new TypeName(Namespace.scala, "BigInt")

  val bigDecimal: TypeName[BigDecimal] = new TypeName(Namespace.scala, "BigDecimal")

  val dayOfWeek: TypeName[java.time.DayOfWeek] = new TypeName(Namespace.javaTime, "DayOfWeek")

  val duration: TypeName[java.time.Duration] = new TypeName(Namespace.javaTime, "Duration")

  val instant: TypeName[java.time.Instant] = new TypeName(Namespace.javaTime, "Instant")

  val localDate: TypeName[java.time.LocalDate] = new TypeName(Namespace.javaTime, "LocalDate")

  val localDateTime: TypeName[java.time.LocalDateTime] = new TypeName(Namespace.javaTime, "LocalDateTime")

  val localTime: TypeName[java.time.LocalTime] = new TypeName(Namespace.javaTime, "LocalTime")

  val month: TypeName[java.time.Month] = new TypeName(Namespace.javaTime, "Month")

  val monthDay: TypeName[java.time.MonthDay] = new TypeName(Namespace.javaTime, "MonthDay")

  val offsetDateTime: TypeName[java.time.OffsetDateTime] = new TypeName(Namespace.javaTime, "OffsetDateTime")

  val offsetTime: TypeName[java.time.OffsetTime] = new TypeName(Namespace.javaTime, "OffsetTime")

  val period: TypeName[java.time.Period] = new TypeName(Namespace.javaTime, "Period")

  val year: TypeName[java.time.Year] = new TypeName(Namespace.javaTime, "Year")

  val yearMonth: TypeName[java.time.YearMonth] = new TypeName(Namespace.javaTime, "YearMonth")

  val zoneId: TypeName[java.time.ZoneId] = new TypeName(Namespace.javaTime, "ZoneId")

  val zoneOffset: TypeName[java.time.ZoneOffset] = new TypeName(Namespace.javaTime, "ZoneOffset")

  val zonedDateTime: TypeName[java.time.ZonedDateTime] = new TypeName(Namespace.javaTime, "ZonedDateTime")

  val currency: TypeName[java.util.Currency] = new TypeName(Namespace.javaUtil, "Currency")

  val uuid: TypeName[java.util.UUID] = new TypeName(Namespace.javaUtil, "UUID")

  val none: TypeName[None.type] = new TypeName(Namespace.scala, "None")

  val dynamicValue: TypeName[DynamicValue] = new TypeName(Namespace.zioBlocksSchema, "DynamicValue")

  def some[A](element: TypeName[A]): TypeName[Some[A]] =
    _some.copy(params = Seq(element)).asInstanceOf[TypeName[Some[A]]]

  def option[A](element: TypeName[A]): TypeName[Option[A]] =
    _option.copy(params = Seq(element)).asInstanceOf[TypeName[Option[A]]]

  def list[A](element: TypeName[A]): TypeName[List[A]] =
    _list.copy(params = Seq(element)).asInstanceOf[TypeName[List[A]]]

  def map[K, V](key: TypeName[K], value: TypeName[V]): TypeName[Map[K, V]] =
    _map.copy(params = Seq(key, value)).asInstanceOf[TypeName[Map[K, V]]]

  def set[A](element: TypeName[A]): TypeName[Set[A]] = _set.copy(params = Seq(element)).asInstanceOf[TypeName[Set[A]]]

  def vector[A](element: TypeName[A]): TypeName[Vector[A]] =
    _vector.copy(params = Seq(element)).asInstanceOf[TypeName[Vector[A]]]

  def arraySeq[A](element: TypeName[A]): TypeName[scala.collection.immutable.ArraySeq[A]] =
    _arraySeq.copy(params = Seq(element)).asInstanceOf[TypeName[scala.collection.immutable.ArraySeq[A]]]

  def indexedSeq[A](element: TypeName[A]): TypeName[IndexedSeq[A]] =
    _indexedSeq.copy(params = Seq(element)).asInstanceOf[TypeName[IndexedSeq[A]]]

  def seq[A](element: TypeName[A]): TypeName[Seq[A]] =
    _seq.copy(params = Seq(element)).asInstanceOf[TypeName[Seq[A]]]

  private[this] val _some       = new TypeName(Namespace.scala, "Some")
  private[this] val _option     = new TypeName(Namespace.scala, "Option")
  private[this] val _list       = new TypeName(Namespace.scalaCollectionImmutable, "List")
  private[this] val _map        = new TypeName(Namespace.scalaCollectionImmutable, "Map")
  private[this] val _set        = new TypeName(Namespace.scalaCollectionImmutable, "Set")
  private[this] val _vector     = new TypeName(Namespace.scalaCollectionImmutable, "Vector")
  private[this] val _arraySeq   = new TypeName(Namespace.scalaCollectionImmutable, "ArraySeq")
  private[this] val _indexedSeq = new TypeName(Namespace.scalaCollectionImmutable, "IndexedSeq")
  private[this] val _seq        = new TypeName(Namespace.scalaCollectionImmutable, "Seq")
}
