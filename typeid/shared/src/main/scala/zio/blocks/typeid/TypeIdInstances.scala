/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
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

package zio.blocks.typeid

/**
 * Predefined ids for common types: the primitive types, `String`, `BigInt`,
 * `BigDecimal`, the standard collections, `Option`, `Either`, `Chunk`, the
 * `java.time` types, `UUID` and `Currency`. The companion of [[TypeId]] extends
 * this trait, so the instances are found by implicit search without an import,
 * and are reachable as `TypeId.int`, `TypeId.list` and so on. A type
 * constructor such as `List` has an id with type parameters and no type
 * arguments.
 *
 * Most of these ids record no parents and have kind [[TypeDefKind.Unknown]];
 * `String` is a final class with `CharSequence`, `Comparable` and
 * `java.io.Serializable` as parents.
 */
trait TypeIdInstances {
  import TypeIdOps.Owners._

  /**
   * The id of `java.lang.CharSequence`, an unsealed trait.
   */
  implicit val charSequence: TypeId[CharSequence] =
    TypeId.nominal[CharSequence]("CharSequence", javaLang, defKind = TypeDefKind.Trait(isSealed = false))

  /**
   * The id of the type constructor `java.lang.Comparable`, with one invariant
   * type parameter `T`.
   */
  implicit val comparable: TypeId[Comparable[_]] =
    TypeId.nominal[Comparable[_]]("Comparable", javaLang, List(TypeParam("T", 0, Variance.Invariant)))

  /**
   * The id of `java.io.Serializable`, an unsealed trait.
   */
  implicit val serializable: TypeId[java.io.Serializable] =
    TypeId.nominal[java.io.Serializable]("Serializable", javaIo, defKind = TypeDefKind.Trait(isSealed = false))

  /**
   * The id of `scala.Unit`.
   */
  implicit val unit: TypeId[Unit] = TypeId.nominal[Unit]("Unit", TypeIdOps.Owners.scala)

  /**
   * The id of `scala.Boolean`.
   */
  implicit val boolean: TypeId[Boolean] = TypeId.nominal[Boolean]("Boolean", TypeIdOps.Owners.scala)

  /**
   * The id of `scala.Byte`.
   */
  implicit val byte: TypeId[Byte] = TypeId.nominal[Byte]("Byte", TypeIdOps.Owners.scala)

  /**
   * The id of `scala.Short`.
   */
  implicit val short: TypeId[Short] = TypeId.nominal[Short]("Short", TypeIdOps.Owners.scala)

  /**
   * The id of `scala.Int`.
   */
  implicit val int: TypeId[Int] = TypeId.nominal[Int]("Int", TypeIdOps.Owners.scala)

  /**
   * The id of `scala.Long`.
   */
  implicit val long: TypeId[Long] = TypeId.nominal[Long]("Long", TypeIdOps.Owners.scala)

  /**
   * The id of `scala.Float`.
   */
  implicit val float: TypeId[Float] = TypeId.nominal[Float]("Float", TypeIdOps.Owners.scala)

  /**
   * The id of `scala.Double`.
   */
  implicit val double: TypeId[Double] = TypeId.nominal[Double]("Double", TypeIdOps.Owners.scala)

  /**
   * The id of `scala.Char`.
   */
  implicit val char: TypeId[Char] = TypeId.nominal[Char]("Char", TypeIdOps.Owners.scala)

  /**
   * The id of `java.lang.String`, a final class whose parents are
   * `CharSequence`, `Comparable` and `java.io.Serializable`.
   */
  implicit val string: TypeId[String] = TypeId.nominal[String](
    "String",
    javaLang,
    defKind = TypeDefKind.Class(
      isFinal = true,
      isAbstract = false,
      isCase = false,
      isValue = false,
      bases = List(
        TypeRepr.Ref(charSequence),
        TypeRepr.Ref(comparable.asInstanceOf[TypeId[_]]),
        TypeRepr.Ref(serializable)
      )
    )
  )

  /**
   * The id of `scala.BigInt`.
   */
  implicit val bigInt: TypeId[BigInt] = TypeId.nominal[BigInt]("BigInt", TypeIdOps.Owners.scala)

  /**
   * The id of `scala.BigDecimal`.
   */
  implicit val bigDecimal: TypeId[BigDecimal] = TypeId.nominal[BigDecimal]("BigDecimal", TypeIdOps.Owners.scala)

  /**
   * The id of the type constructor `scala.Option`, with one covariant type
   * parameter `A`.
   */
  implicit val option: TypeId[Option[_]] =
    TypeId.nominal[Option[_]]("Option", TypeIdOps.Owners.scala, List(TypeParam.A))

  /**
   * The id of the type constructor `scala.Some`, with one covariant type
   * parameter `A`.
   */
  implicit val some: TypeId[Some[_]] = TypeId.nominal[Some[_]]("Some", TypeIdOps.Owners.scala, List(TypeParam.A))

  /**
   * The id of `scala.None`, named `None`.
   */
  implicit val none: TypeId[None.type] = TypeId.nominal[None.type]("None", TypeIdOps.Owners.scala)

  /**
   * The id of the type constructor `scala.collection.immutable.List`, with one
   * covariant type parameter `A`.
   */
  implicit val list: TypeId[List[_]] = TypeId.nominal[List[_]]("List", scalaCollectionImmutable, List(TypeParam.A))

  /**
   * The id of the type constructor `scala.collection.immutable.Vector`, with
   * one covariant type parameter `A`.
   */
  implicit val vector: TypeId[Vector[_]] =
    TypeId.nominal[Vector[_]]("Vector", scalaCollectionImmutable, List(TypeParam.A))

  /**
   * The id of the type constructor `scala.collection.immutable.Set`, with one
   * invariant type parameter `A`.
   */
  implicit val set: TypeId[Set[_]] =
    TypeId.nominal[Set[_]]("Set", scalaCollectionImmutable, List(TypeParam("A", 0, Variance.Invariant)))

  /**
   * The id of the type constructor `scala.collection.immutable.Seq`, with one
   * covariant type parameter `A`.
   */
  implicit val seq: TypeId[Seq[_]] = TypeId.nominal[Seq[_]]("Seq", scalaCollectionImmutable, List(TypeParam.A))

  /**
   * The id of the type constructor `scala.collection.immutable.IndexedSeq`,
   * with one covariant type parameter `A`.
   */
  implicit val indexedSeq: TypeId[IndexedSeq[_]] =
    TypeId.nominal[IndexedSeq[_]]("IndexedSeq", scalaCollectionImmutable, List(TypeParam.A))

  /**
   * The id of the type constructor `scala.collection.immutable.Map`, with an
   * invariant type parameter `K` and a covariant type parameter `V`.
   */
  implicit val map: TypeId[Map[_, _]] =
    TypeId.nominal[Map[_, _]]("Map", scalaCollectionImmutable, List(TypeParam.K, TypeParam.V))

  /**
   * The id of the type constructor `scala.util.Either`, with covariant type
   * parameters `A` and `B`.
   */
  implicit val either: TypeId[Either[_, _]] =
    TypeId.nominal[Either[_, _]]("Either", scalaUtil, List(TypeParam.A, TypeParam.B))

  /**
   * The id of the type constructor `scala.Array`, with one invariant type
   * parameter `T`.
   */
  implicit val array: TypeId[Array[_]] =
    TypeId.nominal[Array[_]]("Array", TypeIdOps.Owners.scala, List(TypeParam("T", 0, Variance.Invariant)))

  /**
   * The id of the type constructor `scala.collection.immutable.ArraySeq`, with
   * one covariant type parameter `A`.
   */
  implicit val arraySeq: TypeId[_root_.scala.collection.immutable.ArraySeq[_]] =
    TypeId
      .nominal[_root_.scala.collection.immutable.ArraySeq[_]]("ArraySeq", scalaCollectionImmutable, List(TypeParam.A))

  /**
   * The id of the type constructor `zio.blocks.chunk.ChunkMap`, with an
   * invariant type parameter `K` and a covariant type parameter `V`.
   */
  implicit val chunkMap: TypeId[zio.blocks.chunk.ChunkMap[_, _]] =
    TypeId.nominal[zio.blocks.chunk.ChunkMap[_, _]]("ChunkMap", zioBlocksChunk, List(TypeParam.K, TypeParam.V))

  /**
   * The id of the type constructor `zio.blocks.chunk.Chunk`, with one covariant
   * type parameter `A`.
   */
  implicit val chunk: TypeId[zio.blocks.chunk.Chunk[_]] =
    TypeId.nominal[zio.blocks.chunk.Chunk[_]]("Chunk", zioBlocksChunk, List(TypeParam.A))

  /**
   * The id of `java.time.DayOfWeek`.
   */
  implicit val dayOfWeek: TypeId[java.time.DayOfWeek] = TypeId.nominal[java.time.DayOfWeek]("DayOfWeek", javaTime)

  /**
   * The id of `java.time.Duration`.
   */
  implicit val duration: TypeId[java.time.Duration] = TypeId.nominal[java.time.Duration]("Duration", javaTime)

  /**
   * The id of `java.time.Instant`.
   */
  implicit val instant: TypeId[java.time.Instant] = TypeId.nominal[java.time.Instant]("Instant", javaTime)

  /**
   * The id of `java.time.LocalDate`.
   */
  implicit val localDate: TypeId[java.time.LocalDate] = TypeId.nominal[java.time.LocalDate]("LocalDate", javaTime)

  /**
   * The id of `java.time.LocalDateTime`.
   */
  implicit val localDateTime: TypeId[java.time.LocalDateTime] =
    TypeId.nominal[java.time.LocalDateTime]("LocalDateTime", javaTime)

  /**
   * The id of `java.time.LocalTime`.
   */
  implicit val localTime: TypeId[java.time.LocalTime] = TypeId.nominal[java.time.LocalTime]("LocalTime", javaTime)

  /**
   * The id of `java.time.Month`.
   */
  implicit val month: TypeId[java.time.Month] = TypeId.nominal[java.time.Month]("Month", javaTime)

  /**
   * The id of `java.time.MonthDay`.
   */
  implicit val monthDay: TypeId[java.time.MonthDay] = TypeId.nominal[java.time.MonthDay]("MonthDay", javaTime)

  /**
   * The id of `java.time.OffsetDateTime`.
   */
  implicit val offsetDateTime: TypeId[java.time.OffsetDateTime] =
    TypeId.nominal[java.time.OffsetDateTime]("OffsetDateTime", javaTime)

  /**
   * The id of `java.time.OffsetTime`.
   */
  implicit val offsetTime: TypeId[java.time.OffsetTime] = TypeId.nominal[java.time.OffsetTime]("OffsetTime", javaTime)

  /**
   * The id of `java.time.Period`.
   */
  implicit val period: TypeId[java.time.Period] = TypeId.nominal[java.time.Period]("Period", javaTime)

  /**
   * The id of `java.time.Year`.
   */
  implicit val year: TypeId[java.time.Year] = TypeId.nominal[java.time.Year]("Year", javaTime)

  /**
   * The id of `java.time.YearMonth`.
   */
  implicit val yearMonth: TypeId[java.time.YearMonth] = TypeId.nominal[java.time.YearMonth]("YearMonth", javaTime)

  /**
   * The id of `java.time.ZoneId`.
   */
  implicit val zoneId: TypeId[java.time.ZoneId] = TypeId.nominal[java.time.ZoneId]("ZoneId", javaTime)

  /**
   * The id of `java.time.ZoneOffset`.
   */
  implicit val zoneOffset: TypeId[java.time.ZoneOffset] = TypeId.nominal[java.time.ZoneOffset]("ZoneOffset", javaTime)

  /**
   * The id of `java.time.ZonedDateTime`.
   */
  implicit val zonedDateTime: TypeId[java.time.ZonedDateTime] =
    TypeId.nominal[java.time.ZonedDateTime]("ZonedDateTime", javaTime)

  /**
   * The id of `java.util.Currency`.
   */
  implicit val currency: TypeId[java.util.Currency] = TypeId.nominal[java.util.Currency]("Currency", javaUtil)

  /**
   * The id of `java.util.UUID`.
   */
  implicit val uuid: TypeId[java.util.UUID] = TypeId.nominal[java.util.UUID]("UUID", javaUtil)
}

/**
 * The members of [[TypeIdInstances]] on a standalone object, such as
 * `TypeIdInstances.int`, for use without the [[TypeId]] companion.
 */
object TypeIdInstances extends TypeIdInstances
