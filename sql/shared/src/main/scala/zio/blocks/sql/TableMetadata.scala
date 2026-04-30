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

final case class ColumnMeta(name: String, dbValue: DbValue, nullable: Boolean)

sealed trait TableNamingPolicy {
  def defaultName(typeName: String): String
}

object TableNamingPolicy {
  case object Singular extends TableNamingPolicy {
    def defaultName(typeName: String): String = SqlNameMapper.SnakeCase(typeName)
  }

  case object Plural extends TableNamingPolicy {
    def defaultName(typeName: String): String =
      TableNamingPolicy.pluralize(SqlNameMapper.SnakeCase(typeName))
  }

  final case class Custom(f: String => String) extends TableNamingPolicy {
    def defaultName(typeName: String): String = f(typeName)
  }

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

object TableMetadata {
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
