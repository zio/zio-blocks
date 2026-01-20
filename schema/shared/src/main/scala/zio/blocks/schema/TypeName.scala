/*
 * Copyright 2023 ZIO Blocks Maintainers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema

import scala.collection.immutable.ArraySeq

final case class TypeName[A](namespace: Namespace, name: String, params: Seq[TypeName[?]] = Nil)

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
