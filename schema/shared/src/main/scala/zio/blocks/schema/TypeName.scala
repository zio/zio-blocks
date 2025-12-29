package zio.blocks.schema

import scala.collection.immutable.ArraySeq

final case class TypeName[A](namespace: Namespace, name: String, params: Seq[TypeName[?]] = Nil) {

  /**
   * Returns a simple string representation of this TypeName, suitable for use
   * in structural type names. For primitives, returns just the simple name. For
   * parameterized types, includes the parameters.
   */
  def toSimpleName: String =
    if (params.isEmpty) name
    else s"$name[${params.map(_.toSimpleName).mkString(",")}]"
}

object TypeName {

  /**
   * Creates a normalized structural type name from a sequence of (fieldName,
   * typeNameString) pairs. Fields are sorted alphabetically by name to ensure
   * consistent naming.
   *
   * Format: {field1:Type1,field2:Type2,...}
   *
   * Example:
   * {{{
   * TypeName.structural(Seq("name" -> "String", "age" -> "Int"))
   * // => TypeName with name "{age:Int,name:String}"
   * }}}
   */
  def structural[A](fields: Seq[(String, String)]): TypeName[A] = {
    val sortedFieldStrs = fields.sortBy(_._1).map { case (n, t) => s"$n:$t" }.mkString(",")
    new TypeName[A](Namespace.empty, s"{$sortedFieldStrs}", Nil)
  }

  /**
   * Creates a normalized structural type name from a sequence of (fieldName,
   * TypeName) pairs. Fields are sorted alphabetically by name to ensure
   * consistent naming. Uses the simple name representation of each TypeName.
   *
   * Format: {field1:Type1,field2:Type2,...}
   */
  def structuralFromTypeNames[A](fields: Seq[(String, TypeName[?])]): TypeName[A] =
    structural(fields.map { case (n, tn) => (n, tn.toSimpleName) })

  /**
   * Creates a variant (union) type name from a sequence of case names. Each
   * case is represented as {Tag:CaseName}.
   *
   * Format: {Tag:Case1}|{Tag:Case2}|...
   *
   * Example:
   * {{{
   * TypeName.variant(Seq("Success", "Failure"))
   * // => TypeName with name "{Tag:Failure}|{Tag:Success}"
   * }}}
   */
  def variant[A](cases: Seq[String]): TypeName[A] = {
    val sortedCases = cases.sorted.map(n => s"{Tag:$n}").mkString("|")
    new TypeName[A](Namespace.empty, sortedCases, Nil)
  }

  /**
   * Creates a tagged case type name for a single case in a variant.
   *
   * Format: {Tag:CaseName}
   */
  def taggedCase[A](name: String): TypeName[A] =
    new TypeName[A](Namespace.empty, s"{Tag:$name}", Nil)

  /**
   * Creates an empty structural type name (for case objects or empty case
   * classes).
   *
   * Format: {}
   */
  def emptyStructural[A]: TypeName[A] =
    new TypeName[A](Namespace.empty, "{}", Nil)

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

  def arraySeq[A](element: TypeName[A]): TypeName[ArraySeq[A]] =
    _arraySeq.copy(params = Seq(element)).asInstanceOf[TypeName[ArraySeq[A]]]

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
