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

package zio.blocks.projection.testing

import zio.*
import zio.blocks.chunk.Chunk
import zio.blocks.projection.{EntityPath, FieldUpdate, ProjectionStore}
import zio.blocks.schema.{DynamicValue, PrimitiveValue, Schema}
import zio.blocks.sql.SqlNameMapper

class InMemoryProjectionStore[A: Schema: EntityPath] private (
  private val mapRef: Ref[Map[String, A]],
  private val seqRef: Ref[Long],
  private val hashRef: Ref[Option[String]]
) extends ProjectionStore[A] {

  private val schema: Schema[A]                    = summon[Schema[A]]
  private val entityPath: EntityPath[A]            = summon[EntityPath[A]]
  private val idField: String                      = entityPath.entityIdField
  private val snakeToOriginal: Map[String, String] =
    schema.reflect.asRecord.map { record =>
      record.fields.map(f => SqlNameMapper.SnakeCase(f.name) -> f.name).toMap
    }
      .getOrElse(Map.empty)

  private def dynamicValueToString(dv: DynamicValue): String = dv match {
    case DynamicValue.Primitive(pv) => primitiveToString(pv)
    case DynamicValue.Null          => ""
    case other                      => other.toString
  }

  private def primitiveToString(pv: PrimitiveValue): String = pv match {
    case PrimitiveValue.String(s)         => s
    case PrimitiveValue.Int(v)            => v.toString
    case PrimitiveValue.Long(v)           => v.toString
    case PrimitiveValue.Double(v)         => v.toString
    case PrimitiveValue.Float(v)          => v.toString
    case PrimitiveValue.Boolean(v)        => v.toString
    case PrimitiveValue.Short(v)          => v.toString
    case PrimitiveValue.Byte(v)           => v.toString
    case PrimitiveValue.Char(v)           => v.toString
    case PrimitiveValue.BigInt(v)         => v.toString
    case PrimitiveValue.BigDecimal(v)     => v.toString
    case PrimitiveValue.UUID(v)           => v.toString
    case PrimitiveValue.Instant(v)        => v.toString
    case PrimitiveValue.LocalDate(v)      => v.toString
    case PrimitiveValue.LocalDateTime(v)  => v.toString
    case PrimitiveValue.LocalTime(v)      => v.toString
    case PrimitiveValue.Duration(v)       => v.toString
    case PrimitiveValue.DayOfWeek(v)      => v.toString
    case PrimitiveValue.Month(v)          => v.toString
    case PrimitiveValue.MonthDay(v)       => v.toString
    case PrimitiveValue.OffsetDateTime(v) => v.toString
    case PrimitiveValue.OffsetTime(v)     => v.toString
    case PrimitiveValue.Period(v)         => v.toString
    case PrimitiveValue.Year(v)           => v.toString
    case PrimitiveValue.YearMonth(v)      => v.toString
    case PrimitiveValue.ZoneId(v)         => v.toString
    case PrimitiveValue.ZoneOffset(v)     => v.toString
    case PrimitiveValue.ZonedDateTime(v)  => v.toString
    case PrimitiveValue.Currency(v)       => v.toString
    case _                                => pv.toString
  }

  private def extractId(a: A): String = {
    val dv = schema.toDynamicValue(a)
    dv match {
      case DynamicValue.Record(fields) =>
        fields
          .find(_._1 == idField)
          .map(_._2)
          .map(dynamicValueToString)
          .getOrElse(throw new RuntimeException(s"Id field '$idField' not found in $dv"))
      case _ =>
        throw new RuntimeException(s"Expected Record for $a but got $dv")
    }
  }

  private def anyToDynamicValue(v: Any): DynamicValue = v match {
    case null                         => DynamicValue.Null
    case None                         => DynamicValue.Null
    case Some(x)                      => anyToDynamicValue(x)
    case s: String                    => DynamicValue.Primitive(PrimitiveValue.String(s))
    case i: Int                       => DynamicValue.Primitive(PrimitiveValue.Int(i))
    case l: Long                      => DynamicValue.Primitive(PrimitiveValue.Long(l))
    case d: Double                    => DynamicValue.Primitive(PrimitiveValue.Double(d))
    case f: Float                     => DynamicValue.Primitive(PrimitiveValue.Float(f))
    case b: java.lang.Boolean         => DynamicValue.Primitive(PrimitiveValue.Boolean(b.booleanValue))
    case b: Boolean                   => DynamicValue.Primitive(PrimitiveValue.Boolean(b))
    case by: Byte                     => DynamicValue.Primitive(PrimitiveValue.Byte(by))
    case sh: Short                    => DynamicValue.Primitive(PrimitiveValue.Short(sh))
    case c: Char                      => DynamicValue.Primitive(PrimitiveValue.Char(c))
    case bd: BigDecimal               => DynamicValue.Primitive(PrimitiveValue.BigDecimal(bd))
    case jbd: java.math.BigDecimal    => DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(jbd)))
    case bi: BigInt                   => DynamicValue.Primitive(PrimitiveValue.BigInt(bi))
    case jbi: java.math.BigInteger    => DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(jbi)))
    case inst: java.time.Instant      => DynamicValue.Primitive(PrimitiveValue.Instant(inst))
    case ld: java.time.LocalDate      => DynamicValue.Primitive(PrimitiveValue.LocalDate(ld))
    case ldt: java.time.LocalDateTime => DynamicValue.Primitive(PrimitiveValue.LocalDateTime(ldt))
    case lt: java.time.LocalTime      => DynamicValue.Primitive(PrimitiveValue.LocalTime(lt))
    case dur: java.time.Duration      => DynamicValue.Primitive(PrimitiveValue.Duration(dur))
    case uuid: java.util.UUID         => DynamicValue.Primitive(PrimitiveValue.UUID(uuid))
    case other                        => DynamicValue.Primitive(PrimitiveValue.String(other.toString))
  }

  private def getLong(dv: DynamicValue): Long = dv match {
    case DynamicValue.Primitive(pv) =>
      pv match {
        case PrimitiveValue.Long(v)       => v
        case PrimitiveValue.Int(v)        => v.toLong
        case PrimitiveValue.Short(v)      => v.toLong
        case PrimitiveValue.Byte(v)       => v.toLong
        case PrimitiveValue.Double(v)     => v.toLong
        case PrimitiveValue.Float(v)      => v.toLong
        case PrimitiveValue.BigInt(v)     => v.toLong
        case PrimitiveValue.BigDecimal(v) => v.toLong
        case PrimitiveValue.String(s)     => s.toLongOption.getOrElse(0L)
        case _                            => 0L
      }
    case _ => 0L
  }

  private def convertLongToOriginalType(orig: DynamicValue, value: Long): DynamicValue =
    orig match {
      case DynamicValue.Primitive(pv) =>
        pv match {
          case _: PrimitiveValue.Long       => DynamicValue.Primitive(PrimitiveValue.Long(value))
          case _: PrimitiveValue.Int        => DynamicValue.Primitive(PrimitiveValue.Int(value.toInt))
          case _: PrimitiveValue.Short      => DynamicValue.Primitive(PrimitiveValue.Short(value.toShort))
          case _: PrimitiveValue.Byte       => DynamicValue.Primitive(PrimitiveValue.Byte(value.toByte))
          case _: PrimitiveValue.Double     => DynamicValue.Primitive(PrimitiveValue.Double(value.toDouble))
          case _: PrimitiveValue.Float      => DynamicValue.Primitive(PrimitiveValue.Float(value.toFloat))
          case _: PrimitiveValue.BigInt     => DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(value)))
          case _: PrimitiveValue.BigDecimal => DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(value)))
          case _                            => DynamicValue.Primitive(PrimitiveValue.Long(value))
        }
      case _ => DynamicValue.Primitive(PrimitiveValue.Long(value))
    }

  private def incDV(dv: DynamicValue, by: Long): DynamicValue = dv match {
    case DynamicValue.Primitive(pv) =>
      pv match {
        case PrimitiveValue.Long(v)       => DynamicValue.Primitive(PrimitiveValue.Long(v + by))
        case PrimitiveValue.Int(v)        => DynamicValue.Primitive(PrimitiveValue.Int((v + by).toInt))
        case PrimitiveValue.Short(v)      => DynamicValue.Primitive(PrimitiveValue.Short((v + by).toShort))
        case PrimitiveValue.Byte(v)       => DynamicValue.Primitive(PrimitiveValue.Byte((v + by).toByte))
        case PrimitiveValue.Double(v)     => DynamicValue.Primitive(PrimitiveValue.Double(v + by.toDouble))
        case PrimitiveValue.Float(v)      => DynamicValue.Primitive(PrimitiveValue.Float((v + by).toFloat))
        case PrimitiveValue.BigInt(v)     => DynamicValue.Primitive(PrimitiveValue.BigInt(v + BigInt(by)))
        case PrimitiveValue.BigDecimal(v) => DynamicValue.Primitive(PrimitiveValue.BigDecimal(v + BigDecimal(by)))
        case _                            => DynamicValue.Primitive(PrimitiveValue.Long(by))
      }
    case _ => dv
  }

  private def decDV(dv: DynamicValue, by: Long): DynamicValue = incDV(dv, -by)

  private def applyUpdatesToRecord(
    record: DynamicValue,
    updates: Chunk[FieldUpdate]
  ): Either[zio.blocks.schema.SchemaError, DynamicValue] =
    record match {
      case DynamicValue.Record(fields) =>
        var current = fields
        updates.foreach {
          case FieldUpdate.Set(field, value) =>
            val orig  = snakeToOriginal.getOrElse(field, field)
            val dvVal = anyToDynamicValue(value)
            val idx   = current.indexWhere(_._1 == orig)
            if (idx >= 0) current = current.updated(idx, (orig, dvVal))
            else current = current :+ (orig -> dvVal)
          case FieldUpdate.Increment(field, by) =>
            val orig = snakeToOriginal.getOrElse(field, field)
            val idx  = current.indexWhere(_._1 == orig)
            if (idx >= 0) {
              val cur = current(idx)._2
              val nv  = incDV(cur, by)
              current = current.updated(idx, (orig, nv))
            }
          case FieldUpdate.Decrement(field, by) =>
            val orig = snakeToOriginal.getOrElse(field, field)
            val idx  = current.indexWhere(_._1 == orig)
            if (idx >= 0) {
              val cur = current(idx)._2
              val nv  = decDV(cur, by)
              current = current.updated(idx, (orig, nv))
            }
          case FieldUpdate.Max(field, value) =>
            val orig = snakeToOriginal.getOrElse(field, field)
            val idx  = current.indexWhere(_._1 == orig)
            if (idx >= 0) {
              val cur     = current(idx)._2
              val curLong = getLong(cur)
              if (curLong < value) {
                val nv = convertLongToOriginalType(cur, value)
                current = current.updated(idx, (orig, nv))
              }
            }
          case FieldUpdate.Min(field, value) =>
            val orig = snakeToOriginal.getOrElse(field, field)
            val idx  = current.indexWhere(_._1 == orig)
            if (idx >= 0) {
              val cur     = current(idx)._2
              val curLong = getLong(cur)
              if (curLong > value) {
                val nv = convertLongToOriginalType(cur, value)
                current = current.updated(idx, (orig, nv))
              }
            }
        }
        Right(DynamicValue.Record(current))
      case other =>
        Left(zio.blocks.schema.SchemaError(s"Expected Record but got $other"))
    }

  private def applyUpdatesToA(
    cur: A,
    updates: Chunk[FieldUpdate]
  ): Either[zio.blocks.schema.SchemaError, A] = {
    val dv = schema.toDynamicValue(cur)
    applyUpdatesToRecord(dv, updates).flatMap(schema.fromDynamicValue)
  }

  // ---------------------------------------------------------------------------
  // ProjectionStore implementation
  // ---------------------------------------------------------------------------

  def insert(a: A): Task[Unit] =
    ZIO.attempt(extractId(a)).flatMap { id =>
      mapRef.modify { m =>
        if (m.contains(id)) (Left(new RuntimeException(s"Entity $id already exists")), m)
        else (Right(()), m + (id -> a))
      }.flatMap {
        case Left(e)  => ZIO.fail(e)
        case Right(_) => ZIO.unit
      }
    }

  def upsert(a: A): Task[Unit] =
    ZIO.attempt(extractId(a)).flatMap { id =>
      mapRef.update(_ + (id -> a))
    }

  private def defaultDynamicValueFor(
    reflect: zio.blocks.schema.Reflect[?, ?],
    entityId: String,
    isIdField: Boolean
  ): DynamicValue =
    if (isIdField) {
      DynamicValue.Primitive(PrimitiveValue.String(entityId))
    } else if (reflect.isOption || reflect.isMaybe) {
      DynamicValue.Null
    } else {
      reflect.asPrimitive match {
        case Some(p) =>
          p.primitiveType match {
            case _: zio.blocks.schema.PrimitiveType.Int        => DynamicValue.Primitive(PrimitiveValue.Int(0))
            case _: zio.blocks.schema.PrimitiveType.Long       => DynamicValue.Primitive(PrimitiveValue.Long(0L))
            case _: zio.blocks.schema.PrimitiveType.Short      => DynamicValue.Primitive(PrimitiveValue.Short(0.toShort))
            case _: zio.blocks.schema.PrimitiveType.Byte       => DynamicValue.Primitive(PrimitiveValue.Byte(0.toByte))
            case _: zio.blocks.schema.PrimitiveType.Double     => DynamicValue.Primitive(PrimitiveValue.Double(0d))
            case _: zio.blocks.schema.PrimitiveType.Float      => DynamicValue.Primitive(PrimitiveValue.Float(0f))
            case _: zio.blocks.schema.PrimitiveType.Boolean    => DynamicValue.Primitive(PrimitiveValue.Boolean(false))
            case _: zio.blocks.schema.PrimitiveType.Char       => DynamicValue.Primitive(PrimitiveValue.Char(' '))
            case _: zio.blocks.schema.PrimitiveType.String     => DynamicValue.Primitive(PrimitiveValue.String(""))
            case _: zio.blocks.schema.PrimitiveType.BigDecimal =>
              DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(0)))
            case _: zio.blocks.schema.PrimitiveType.BigInt => DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(0)))
            case _: zio.blocks.schema.PrimitiveType.UUID   =>
              DynamicValue.Primitive(PrimitiveValue.UUID(new java.util.UUID(0L, 0L)))
            case _: zio.blocks.schema.PrimitiveType.Instant =>
              DynamicValue.Primitive(PrimitiveValue.Instant(java.time.Instant.EPOCH))
            case _: zio.blocks.schema.PrimitiveType.LocalDate =>
              DynamicValue.Primitive(PrimitiveValue.LocalDate(java.time.LocalDate.ofEpochDay(0)))
            case _: zio.blocks.schema.PrimitiveType.LocalDateTime =>
              DynamicValue.Primitive(
                PrimitiveValue.LocalDateTime(java.time.LocalDateTime.ofEpochSecond(0, 0, java.time.ZoneOffset.UTC))
              )
            case _: zio.blocks.schema.PrimitiveType.LocalTime =>
              DynamicValue.Primitive(PrimitiveValue.LocalTime(java.time.LocalTime.MIDNIGHT))
            case _: zio.blocks.schema.PrimitiveType.Duration =>
              DynamicValue.Primitive(PrimitiveValue.Duration(java.time.Duration.ZERO))
            case _ => DynamicValue.Primitive(PrimitiveValue.String(""))
          }
        case None =>
          // Wrapper (Id) or enumeration or unknown: try to unwrap
          reflect.asWrapperUnknown match {
            case Some(w) => defaultDynamicValueFor(w.wrapper.wrapped, entityId, isIdField = false)
            case None    =>
              if (reflect.isEnumeration) DynamicValue.Primitive(PrimitiveValue.String(""))
              else DynamicValue.Null
          }
      }
    }

  private def createDefaultForId(entityId: String): Either[zio.blocks.schema.SchemaError, A] =
    schema.reflect.asRecord match {
      case Some(record) =>
        val fields = record.fields.map { f =>
          val isId = f.name == idField
          val dv   = defaultDynamicValueFor(f.value, entityId, isId)
          (f.name, dv)
        }
        schema.fromDynamicValue(DynamicValue.Record(Chunk.fromIterable(fields)))
      case None => Left(zio.blocks.schema.SchemaError(s"Entity $schema is not a record"))
    }

  def updateFields(entityId: String, updates: Chunk[FieldUpdate]): Task[Unit] =
    if (updates.isEmpty) ZIO.unit
    else
      mapRef.modify { m =>
        m.get(entityId) match {
          case Some(cur) =>
            applyUpdatesToA(cur, updates) match {
              case Right(next) => (Right(()): Either[Throwable, Unit], m + (entityId -> next))
              case Left(e)     => (Left(new RuntimeException(e.message)): Either[Throwable, Unit], m)
            }
          case None =>
            createDefaultForId(entityId) match {
              case Right(defA) =>
                applyUpdatesToA(defA, updates) match {
                  case Right(next) => (Right(()): Either[Throwable, Unit], m + (entityId -> next))
                  case Left(e)     => (Left(new RuntimeException(e.message)): Either[Throwable, Unit], m)
                }
              case Left(e) => (Left(new RuntimeException(e.message)): Either[Throwable, Unit], m)
            }
        }
      }.flatMap {
        case Right(_) => ZIO.unit
        case Left(e)  => ZIO.fail(e)
      }

  def delete(entityId: String): Task[Unit] =
    mapRef.update(_ - entityId)

  def truncate: Task[Unit] =
    mapRef.set(Map.empty)

  def findById(entityId: String): Task[Option[A]] =
    mapRef.get.map(_.get(entityId))

  def getLastProcessedSeq: Task[Long] =
    seqRef.get

  def updateLastProcessedSeq(seq: Long): Task[Unit] =
    seqRef.set(seq)

  def getSchemaHash: Task[Option[String]] =
    hashRef.get

  def updateSchemaHash(hash: String): Task[Unit] =
    hashRef.set(Some(hash))
}

object InMemoryProjectionStore {

  def make[A: Schema: EntityPath]: Task[InMemoryProjectionStore[A]] =
    for {
      map  <- Ref.make(Map.empty[String, A])
      seq  <- Ref.make(0L)
      hash <- Ref.make(Option.empty[String])
    } yield new InMemoryProjectionStore[A](map, seq, hash)

  def makeWithRefs[A: Schema: EntityPath](
    map: Map[String, A] = Map.empty[String, A],
    seq: Long = 0L,
    hash: Option[String] = None
  ): Task[InMemoryProjectionStore[A]] =
    for {
      mapRef  <- Ref.make(map)
      seqRef  <- Ref.make(seq)
      hashRef <- Ref.make(hash)
    } yield new InMemoryProjectionStore[A](mapRef, seqRef, hashRef)
}
