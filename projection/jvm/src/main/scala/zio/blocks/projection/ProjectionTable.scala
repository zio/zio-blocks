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

package zio.blocks.projection

import zio.blocks.schema.*
import zio.blocks.sql.*

/**
 * Projection-local table metadata derivation.
 *
 * `zio.blocks.sql.Table.derived` derives DDL column metadata that matches
 * primitive and flat records but rejects sequence/map fields whose `DbCodec` is
 * a single JSON text column. Projections need those columns so that
 * [[FieldUpdate.Append]] can update a `List[A]` field atomically, so this
 * fallback mirrors the codec's column layout for sequence, map, nested-record
 * and variant fields.
 *
 * It is a fallback only: [[SQLiteProjectionStore]] keeps using `Table.derived`
 * whenever it succeeds, which preserves the existing DDL for all primitive
 * projections. The derived column names are verified against the actual codec
 * so a mismatch fails at construction instead of producing mis-bound SQL.
 */
private[projection] object ProjectionTable {

  def derived[A](implicit schema: Schema[A]): Table[A] = {
    val codec = schema.deriving(DbCodecDeriver).derive
    val name  = schema.reflect.modifiers.collectFirst { case Modifier.config("sql.table_name", value) =>
      value
    }.getOrElse(TableNamingPolicy.Singular.defaultName(schema.reflect.typeId.name))

    val columns = schema.reflect.asRecord match {
      case Some(record) => recordColumns(record, prefix = "", parentInlineAll = false)
      case None         =>
        throw new UnsupportedOperationException(
          s"ProjectionTable requires a record schema but got ${schema.reflect.typeId.name}"
        )
    }

    val expected = codec.columns
    val actual   = columns.map(_.name)
    if (actual != expected)
      throw new IllegalStateException(
        s"ProjectionTable columns $actual do not match DbCodec columns $expected for $name"
      )

    Table(name, codec, columns)
  }

  private def recordColumns[F[_, _]](
    record: Reflect.Record[F, ?],
    prefix: String,
    parentInlineAll: Boolean
  ): IndexedSeq[ColumnMeta] = {
    val inlineAll = parentInlineAll || record.modifiers.exists(isInlineFields)
    record.fields.flatMap(field => fieldColumns(field, prefix, inlineAll))
  }

  private def fieldColumns[F[_, _]](
    field: Term[F, ?, ?],
    prefix: String,
    inlineAll: Boolean
  ): IndexedSeq[ColumnMeta] = {
    val isTransient = field.modifiers.exists(_.isInstanceOf[Modifier.transient])
    if (isTransient) IndexedSeq.empty
    else {
      val fieldName = field.modifiers.collectFirst { case m: Modifier.rename => m.name }
        .getOrElse(SqlNameMapper.SnakeCase(field.name))
      val fullName = if (prefix.isEmpty) fieldName else s"${prefix}_$fieldName"
      val inline   = inlineAll || field.modifiers.exists(isInlineField)
      valueColumns(field.value, fullName, forcedNullable = false, inline)
    }
  }

  private def valueColumns[F[_, _]](
    reflect: Reflect[F, ?],
    name: String,
    forcedNullable: Boolean,
    inline: Boolean
  ): IndexedSeq[ColumnMeta] =
    if (reflect.isOption || reflect.isMaybe)
      reflect.optionInnerType.toIndexedSeq.flatMap(inner => valueColumns(inner, name, forcedNullable = true, inline))
    else
      reflect.asRecord match {
        case Some(record) if inline =>
          recordColumns(record, name, parentInlineAll = false)
        case Some(_) =>
          IndexedSeq(ColumnMeta(name, DbValue.DbString("{}"), forcedNullable))
        case None =>
          reflect.asPrimitive match {
            case Some(primitive) =>
              IndexedSeq(ColumnMeta(name, primitiveDefault(primitive.primitiveType), forcedNullable))
            case None if reflect.isEnumeration =>
              IndexedSeq(ColumnMeta(name, DbValue.DbString(""), forcedNullable))
            case None if reflect.asWrapperUnknown.isDefined =>
              valueColumns(reflect.asWrapperUnknown.get.wrapper.wrapped, name, forcedNullable, inline)
            case None if reflect.isSequence =>
              IndexedSeq(ColumnMeta(name, DbValue.DbString("[]"), forcedNullable))
            case None if reflect.isMap =>
              IndexedSeq(ColumnMeta(name, DbValue.DbString("{}"), forcedNullable))
            case None =>
              IndexedSeq(ColumnMeta(name, DbValue.DbString(""), forcedNullable))
          }
      }

  private def isInlineFields(modifier: Modifier): Boolean = modifier match {
    case m: Modifier.config => m.key == "sql.inline_fields" && m.value == "true"
    case _                  => false
  }

  private def isInlineField(modifier: Modifier): Boolean = modifier match {
    case m: Modifier.config => m.key == "sql.inline" && m.value == "true"
    case _                  => false
  }

  private def primitiveDefault(primitiveType: PrimitiveType[?]): DbValue = primitiveType match {
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
        s"ProjectionTable does not support primitive type: ${other.getClass.getSimpleName}"
      )
  }
}
