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

package zio.blocks.sql

import zio.blocks.schema.*

/**
 * Describes one column of a [[Table]] for DDL: its name, a representative
 * [[DbValue]] that `SqlDialect#typeName` maps to the column type (such as
 * `DbValue.DbInt(0)` for `INTEGER`), and whether it accepts `NULL`.
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/table-metadata/ TableMetadata]]
 */
final case class ColumnMeta(name: String, dbValue: DbValue, nullable: Boolean)

/**
 * How `Table.derived` turns a type name into a table name when the type has no
 * `@Modifier.config("sql.table_name", ...)` annotation.
 *
 * {{{
 * val singular = TableNamingPolicy.Singular.defaultName("OrderLine") // order_line
 * val plural   = TableNamingPolicy.Plural.defaultName("Category")    // categories
 * val custom   = TableNamingPolicy.Custom("shop_" + _.toLowerCase).defaultName("Product")
 * // shop_product
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/table/ Table]]
 */
sealed trait TableNamingPolicy {

  /** Returns the table name for the type named `typeName`. */
  def defaultName(typeName: String): String
}

/** The predefined [[TableNamingPolicy]] values. */
object TableNamingPolicy {

  /** The type name in snake case: `OrderLine` becomes `order_line`. */
  case object Singular extends TableNamingPolicy {

    /** Returns `typeName` in snake case. */
    def defaultName(typeName: String): String = SqlNameMapper.SnakeCase(typeName)
  }

  /**
   * The type name in snake case with an English plural ending: `Category`
   * becomes `categories`, `Box` becomes `boxes`, and `Person` becomes
   * `persons`.
   */
  case object Plural extends TableNamingPolicy {

    /** Returns `typeName` in snake case with a plural ending. */
    def defaultName(typeName: String): String =
      TableNamingPolicy.pluralize(SqlNameMapper.SnakeCase(typeName))
  }

  /** Applies `f` to the type name as written, such as `OrderLine`. */
  final case class Custom(f: String => String) extends TableNamingPolicy {

    /** Returns `f(typeName)`. */
    def defaultName(typeName: String): String = f(typeName)
  }

  /** Adds an English plural ending to `s`, as [[Plural]] uses it. */
  private[sql] def pluralize(s: String): String =
    if (s.isEmpty) s
    else if (s.endsWith("s") || s.endsWith("x") || s.endsWith("ch") || s.endsWith("sh") || s.endsWith("zz"))
      s + "es"
    else if (s.endsWith("iz")) s.dropRight(1) + "zzes"
    else if (s.endsWith("z")) s + "es"
    else if (s.endsWith("y") && s.length > 1 && !isVowel(s.charAt(s.length - 2))) s.dropRight(1) + "ies"
    else s + "s"

  private def isVowel(c: Char): Boolean = "aeiouAEIOU".indexOf(c) >= 0
}

/**
 * Computes the column metadata that `Table.derived` puts in a [[Table]].
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/table-metadata/ TableMetadata]]
 */
object TableMetadata {

  /**
   * Returns one [[ColumnMeta]] per primitive leaf of `schema`, named with
   * `columnNameMapper` or `@Modifier.rename`. `Option` and `Maybe` fields are
   * nullable, `@Modifier.transient` fields are skipped, an enum of case objects
   * is a text column, and a nested case class field becomes
   * `<field>_<subfield>` columns. A collection or a sealed trait with fields
   * throws `UnsupportedOperationException`.
   *
   * The nested and collection cases disagree with the row codec, which stores
   * such fields as one JSON column, a known bug in 0.0.56.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * case class Customer(customerId: Int, nickname: Option[String])
   * object Customer { implicit val schema: Schema[Customer] = Schema.derived }
   *
   * val columns = TableMetadata.columnsFor(Customer.schema)
   * // Vector(ColumnMeta(customer_id,DbInt(0),false), ColumnMeta(nickname,DbString(),true))
   * }}}
   */
  def columnsFor[A](
    schema: Schema[A],
    columnNameMapper: SqlNameMapper = SqlNameMapper.SnakeCase
  ): IndexedSeq[ColumnMeta] =
    fromReflect(schema.reflect, columnNameMapper)

  private def fromReflect[F[_, _]](
    reflect: Reflect[F, ?],
    columnNameMapper: SqlNameMapper,
    forcedNullable: Boolean = false,
    prefix: String = ""
  ): IndexedSeq[ColumnMeta] =
    if (reflect.isOption || reflect.isMaybe)
      reflect.optionInnerType.toIndexedSeq
        .flatMap(inner => fromReflect(inner, columnNameMapper, forcedNullable = true, prefix = prefix))
    else {
      reflect.asRecord match {
        case Some(record) =>
          record.fields.flatMap { field =>
            val isTransient = field.modifiers.exists(_.isInstanceOf[Modifier.transient])
            if (isTransient) IndexedSeq.empty
            else {
              val renamed    = field.modifiers.collectFirst { case m: Modifier.rename => m.name }
              val fieldName  = renamed.getOrElse(columnNameMapper(field.name))
              val nextPrefix = if (prefix.isEmpty) fieldName else s"${prefix}_${fieldName}"
              fromReflect(field.value, columnNameMapper, forcedNullable = forcedNullable, prefix = nextPrefix)
            }
          }

        case None =>
          IndexedSeq(ColumnMeta(columnName(prefix), dbValueFor(reflect), forcedNullable))
      }
    }

  private def columnName(prefix: String): String =
    if (prefix.nonEmpty) prefix else "value"

  private def dbValueFor[F[_, _]](reflect: Reflect[F, ?]): DbValue =
    reflect.asPrimitive match {
      case Some(primitive) =>
        primitive.primitiveType match {
          case PrimitiveType.Unit             => DbValue.DbString("")
          case _: PrimitiveType.Boolean       => DbValue.DbBoolean(false)
          case _: PrimitiveType.Byte          => DbValue.DbByte(0)
          case _: PrimitiveType.Short         => DbValue.DbShort(0)
          case _: PrimitiveType.Int           => DbValue.DbInt(0)
          case _: PrimitiveType.Long          => DbValue.DbLong(0L)
          case _: PrimitiveType.Float         => DbValue.DbFloat(0f)
          case _: PrimitiveType.Double        => DbValue.DbDouble(0d)
          case _: PrimitiveType.Char          => DbValue.DbChar(' ')
          case _: PrimitiveType.String        => DbValue.DbString("")
          case _: PrimitiveType.BigDecimal    => DbValue.DbBigDecimal(BigDecimal(0))
          case _: PrimitiveType.Duration      => DbValue.DbDuration(java.time.Duration.ZERO)
          case _: PrimitiveType.Instant       => DbValue.DbInstant(java.time.Instant.EPOCH)
          case _: PrimitiveType.LocalDate     => DbValue.DbLocalDate(java.time.LocalDate.ofEpochDay(0))
          case _: PrimitiveType.LocalDateTime =>
            DbValue.DbLocalDateTime(java.time.LocalDateTime.ofEpochSecond(0, 0, java.time.ZoneOffset.UTC))
          case _: PrimitiveType.LocalTime => DbValue.DbLocalTime(java.time.LocalTime.MIDNIGHT)
          case _: PrimitiveType.UUID      => DbValue.DbUUID(new java.util.UUID(0L, 0L))
          case other                      =>
            throw new UnsupportedOperationException(
              s"Typed DDL does not support primitive type: ${other.getClass.getSimpleName}"
            )
        }

      case None if reflect.isEnumeration =>
        DbValue.DbString("")

      case None if reflect.asWrapperUnknown.isDefined =>
        dbValueFor(reflect.asWrapperUnknown.get.wrapper.wrapped)

      case _ =>
        throw new UnsupportedOperationException(
          s"Typed DDL does not support reflect node ${reflect.typeId}"
        )
    }
}
