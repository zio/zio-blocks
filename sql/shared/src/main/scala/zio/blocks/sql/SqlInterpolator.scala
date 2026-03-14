package zio.blocks.sql

import java.time._
import java.util.UUID

trait DbParam[A] {
  def toDbValue(value: A): DbValue
}

object DbParam {
  def apply[A](implicit p: DbParam[A]): DbParam[A] = p

  given DbParam[Int] with {
    def toDbValue(v: Int): DbValue = DbValue.DbInt(v)
  }

  given DbParam[Long] with {
    def toDbValue(v: Long): DbValue = DbValue.DbLong(v)
  }

  given DbParam[Double] with {
    def toDbValue(v: Double): DbValue = DbValue.DbDouble(v)
  }

  given DbParam[Float] with {
    def toDbValue(v: Float): DbValue = DbValue.DbFloat(v)
  }

  given DbParam[Boolean] with {
    def toDbValue(v: Boolean): DbValue = DbValue.DbBoolean(v)
  }

  given DbParam[String] with {
    def toDbValue(v: String): DbValue = DbValue.DbString(v)
  }

  given DbParam[Short] with {
    def toDbValue(v: Short): DbValue = DbValue.DbShort(v)
  }

  given DbParam[Byte] with {
    def toDbValue(v: Byte): DbValue = DbValue.DbByte(v)
  }

  given DbParam[BigDecimal] with {
    def toDbValue(v: BigDecimal): DbValue = DbValue.DbBigDecimal(v)
  }

  given dbParamBytes: DbParam[Array[Byte]] with {
    def toDbValue(v: Array[Byte]): DbValue = DbValue.DbBytes(v)
  }

  given DbParam[LocalDate] with {
    def toDbValue(v: LocalDate): DbValue = DbValue.DbLocalDate(v)
  }

  given DbParam[LocalDateTime] with {
    def toDbValue(v: LocalDateTime): DbValue = DbValue.DbLocalDateTime(v)
  }

  given DbParam[LocalTime] with {
    def toDbValue(v: LocalTime): DbValue = DbValue.DbLocalTime(v)
  }

  given DbParam[Instant] with {
    def toDbValue(v: Instant): DbValue = DbValue.DbInstant(v)
  }

  given DbParam[Duration] with {
    def toDbValue(v: Duration): DbValue = DbValue.DbDuration(v)
  }

  given DbParam[UUID] with {
    def toDbValue(v: UUID): DbValue = DbValue.DbUUID(v)
  }

  given DbParam[DbValue] with {
    def toDbValue(v: DbValue): DbValue = v
  }

  given [A](using inner: DbParam[A]): DbParam[Option[A]] with {
    def toDbValue(v: Option[A]): DbValue = v match {
      case Some(a) => inner.toDbValue(a)
      case None    => DbValue.DbNull
    }
  }
}

extension (sc: StringContext) {
  def sql(args: DbValue*): Frag =
    Frag(sc.parts.toIndexedSeq, args.toIndexedSeq)
}

given dbParamToDbValue[A](using p: DbParam[A]): Conversion[A, DbValue] = p.toDbValue(_)
