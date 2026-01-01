package zio.blocks.schema

import zio.blocks.typeid.*
import scala.collection.immutable.ArraySeq
import scala.language.implicitConversions

final case class TypeName[A](id: TypeId[A], params: Seq[TypeName[?]] = Nil) {
  def name: String         = id.name
  def namespace: Namespace = {
    val pkgs = id.owner.segments.collect { case Owner.Package(n) => n }
    val vs   = id.owner.segments.collect { case Owner.Term(n) => n }
    Namespace(pkgs, vs)
  }
}

object TypeName {
  private val pkgScala                    = Owner(List(Owner.Package("scala")))
  private val pkgJavaLang                 = Owner(List(Owner.Package("java"), Owner.Package("lang")))
  private val pkgJavaTime                 = Owner(List(Owner.Package("java"), Owner.Package("time")))
  private val pkgJavaUtil                 = Owner(List(Owner.Package("java"), Owner.Package("util")))
  private val pkgZioBlocksSchema          = Owner(List(Owner.Package("zio"), Owner.Package("blocks"), Owner.Package("schema")))
  private val pkgScalaCollectionImmutable = Owner(
    List(Owner.Package("scala"), Owner.Package("collection"), Owner.Package("immutable"))
  )

  def apply[A](namespace: Namespace, name: String, params: Seq[TypeName[?]]): TypeName[A] = {
    val segments   = namespace.packages.map(Owner.Package(_)) ++ namespace.values.map(Owner.Term(_))
    val typeParams = params.zipWithIndex.map { case (p, i) => TypeParam(p.name, i) }
    new TypeName(TypeId.nominal[A](name, Owner(segments.toList), typeParams.toList), params)
  }

  def apply[A](namespace: Namespace, name: String): TypeName[A] =
    apply(namespace, name, Nil)

  implicit def typeIdToTypeName[A](id: TypeId[A]): TypeName[A] = TypeName(id)
  implicit def typeNameToTypeId[A](tn: TypeName[A]): TypeId[A] = tn.id

  val unit: TypeId[Unit]                             = TypeId.nominal[Unit]("Unit", pkgScala, Nil)
  val boolean: TypeId[Boolean]                       = TypeId.nominal[Boolean]("Boolean", pkgScala, Nil)
  val byte: TypeId[Byte]                             = TypeId.nominal[Byte]("Byte", pkgScala, Nil)
  val short: TypeId[Short]                           = TypeId.nominal[Short]("Short", pkgScala, Nil)
  val int: TypeId[Int]                               = TypeId.nominal[Int]("Int", pkgScala, Nil)
  val long: TypeId[Long]                             = TypeId.nominal[Long]("Long", pkgScala, Nil)
  val float: TypeId[Float]                           = TypeId.nominal[Float]("Float", pkgScala, Nil)
  val double: TypeId[Double]                         = TypeId.nominal[Double]("Double", pkgScala, Nil)
  val char: TypeId[Char]                             = TypeId.nominal[Char]("Char", pkgScala, Nil)
  val string: TypeId[String]                         = TypeId.nominal[String]("String", pkgJavaLang, Nil)
  val bigInt: TypeId[BigInt]                         = TypeId.nominal[BigInt]("BigInt", pkgScala, Nil)
  val bigDecimal: TypeId[BigDecimal]                 = TypeId.nominal[BigDecimal]("BigDecimal", pkgScala, Nil)
  val dayOfWeek: TypeId[java.time.DayOfWeek]         = TypeId.nominal[java.time.DayOfWeek]("DayOfWeek", pkgJavaTime, Nil)
  val duration: TypeId[java.time.Duration]           = TypeId.nominal[java.time.Duration]("Duration", pkgJavaTime, Nil)
  val instant: TypeId[java.time.Instant]             = TypeId.nominal[java.time.Instant]("Instant", pkgJavaTime, Nil)
  val localDate: TypeId[java.time.LocalDate]         = TypeId.nominal[java.time.LocalDate]("LocalDate", pkgJavaTime, Nil)
  val localDateTime: TypeId[java.time.LocalDateTime] =
    TypeId.nominal[java.time.LocalDateTime]("LocalDateTime", pkgJavaTime, Nil)
  val localTime: TypeId[java.time.LocalTime]           = TypeId.nominal[java.time.LocalTime]("LocalTime", pkgJavaTime, Nil)
  val month: TypeId[java.time.Month]                   = TypeId.nominal[java.time.Month]("Month", pkgJavaTime, Nil)
  val monthDay: TypeId[java.time.MonthDay]             = TypeId.nominal[java.time.MonthDay]("MonthDay", pkgJavaTime, Nil)
  val offsetDateTime: TypeId[java.time.OffsetDateTime] =
    TypeId.nominal[java.time.OffsetDateTime]("OffsetDateTime", pkgJavaTime, Nil)
  val offsetTime: TypeId[java.time.OffsetTime]       = TypeId.nominal[java.time.OffsetTime]("OffsetTime", pkgJavaTime, Nil)
  val period: TypeId[java.time.Period]               = TypeId.nominal[java.time.Period]("Period", pkgJavaTime, Nil)
  val year: TypeId[java.time.Year]                   = TypeId.nominal[java.time.Year]("Year", pkgJavaTime, Nil)
  val yearMonth: TypeId[java.time.YearMonth]         = TypeId.nominal[java.time.YearMonth]("YearMonth", pkgJavaTime, Nil)
  val zoneId: TypeId[java.time.ZoneId]               = TypeId.nominal[java.time.ZoneId]("ZoneId", pkgJavaTime, Nil)
  val zoneOffset: TypeId[java.time.ZoneOffset]       = TypeId.nominal[java.time.ZoneOffset]("ZoneOffset", pkgJavaTime, Nil)
  val zonedDateTime: TypeId[java.time.ZonedDateTime] =
    TypeId.nominal[java.time.ZonedDateTime]("ZonedDateTime", pkgJavaTime, Nil)
  val currency: TypeId[java.util.Currency] = TypeId.nominal[java.util.Currency]("Currency", pkgJavaUtil, Nil)
  val uuid: TypeId[java.util.UUID]         = TypeId.nominal[java.util.UUID]("UUID", pkgJavaUtil, Nil)
  val none: TypeId[None.type]              = TypeId.nominal[None.type]("None", pkgScala, Nil)
  val dynamicValue: TypeId[DynamicValue]   = TypeId.nominal[DynamicValue]("DynamicValue", pkgZioBlocksSchema, Nil)

  private val A = TypeParam("A", 0)
  private val K = TypeParam("K", 0)
  private val V = TypeParam("V", 1)

  def some[A](element: TypeId[A]): TypeId[Some[A]] =
    TypeId.nominal[Some[A]]("Some", pkgScala, List(A))
  def option[A](element: TypeId[A]): TypeId[Option[A]] =
    TypeId.nominal[Option[A]]("Option", pkgScala, List(A))
  def list[A](element: TypeId[A]): TypeId[List[A]] =
    TypeId.nominal[List[A]]("List", pkgScalaCollectionImmutable, List(A))
  def map[K, V](key: TypeId[K], value: TypeId[V]): TypeId[Map[K, V]] =
    TypeId.nominal[Map[K, V]]("Map", pkgScalaCollectionImmutable, List(K, V))
  def set[A](element: TypeId[A]): TypeId[Set[A]] =
    TypeId.nominal[Set[A]]("Set", pkgScalaCollectionImmutable, List(A))
  def vector[A](element: TypeId[A]): TypeId[Vector[A]] =
    TypeId.nominal[Vector[A]]("Vector", pkgScalaCollectionImmutable, List(A))
  def arraySeq[A](element: TypeId[A]): TypeId[ArraySeq[A]] =
    TypeId.nominal[ArraySeq[A]]("ArraySeq", pkgScalaCollectionImmutable, List(A))
  def indexedSeq[A](element: TypeId[A]): TypeId[IndexedSeq[A]] =
    TypeId.nominal[IndexedSeq[A]]("IndexedSeq", pkgScalaCollectionImmutable, List(A))
  def seq[A](element: TypeId[A]): TypeId[Seq[A]] =
    TypeId.nominal[Seq[A]]("Seq", pkgScalaCollectionImmutable, List(A))
}
