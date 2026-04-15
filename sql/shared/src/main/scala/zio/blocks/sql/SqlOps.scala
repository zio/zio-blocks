package zio.blocks.sql

object SqlOps {

  def query[A](frag: Frag)(using con: DbCon, codec: DbCodec[A]): List[A] = {
    val sqlStr = frag.sql(con.dialect)
    val start  = System.nanoTime()
    try {
      val ps = con.connection.prepareStatement(sqlStr)
      try {
        writeParams(ps.paramWriter, frag.queryParams)
        val rs = ps.executeQuery()
        try {
          val reader  = rs.reader
          val builder = List.newBuilder[A]
          var count   = 0
          while (rs.next()) {
            builder += codec.readValue(reader, 1)
            count += 1
          }
          val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
          con.logger.onSuccess(SqlLogger.SuccessEvent(sqlStr, frag.queryParams, duration, count))
          builder.result()
        } finally rs.close()
      } finally ps.close()
    } catch {
      case e: Throwable =>
        val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
        con.logger.onError(SqlLogger.ErrorEvent(sqlStr, frag.queryParams, duration, e))
        throw e
    }
  }

  def queryLimit[A](frag: Frag, limit: Int)(using con: DbCon, codec: DbCodec[A]): List[A] = {
    val sqlStr = frag.sql(con.dialect)
    val start  = System.nanoTime()
    try {
      val ps = con.connection.prepareStatement(sqlStr)
      try {
        writeParams(ps.paramWriter, frag.queryParams)
        val rs = ps.executeQuery()
        try {
          val reader  = rs.reader
          val builder = List.newBuilder[A]
          var count   = 0
          while (count < limit && rs.next()) {
            builder += codec.readValue(reader, 1)
            count += 1
          }
          val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
          con.logger.onSuccess(SqlLogger.SuccessEvent(sqlStr, frag.queryParams, duration, count))
          builder.result()
        } finally rs.close()
      } finally ps.close()
    } catch {
      case e: Throwable =>
        val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
        con.logger.onError(SqlLogger.ErrorEvent(sqlStr, frag.queryParams, duration, e))
        throw e
    }
  }

  def queryOne[A](frag: Frag)(using con: DbCon, codec: DbCodec[A]): Option[A] = {
    val sqlStr = frag.sql(con.dialect)
    val start  = System.nanoTime()
    try {
      val ps = con.connection.prepareStatement(sqlStr)
      try {
        writeParams(ps.paramWriter, frag.queryParams)
        val rs = ps.executeQuery()
        try {
          val result   = if (rs.next()) Some(codec.readValue(rs.reader, 1)) else None
          val count    = if (result.isDefined) 1 else 0
          val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
          con.logger.onSuccess(SqlLogger.SuccessEvent(sqlStr, frag.queryParams, duration, count))
          result
        } finally rs.close()
      } finally ps.close()
    } catch {
      case e: Throwable =>
        val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
        con.logger.onError(SqlLogger.ErrorEvent(sqlStr, frag.queryParams, duration, e))
        throw e
    }
  }

  def update(frag: Frag)(using con: DbCon): Int = {
    val sqlStr = frag.sql(con.dialect)
    val start  = System.nanoTime()
    try {
      val ps = con.connection.prepareStatement(sqlStr)
      try {
        writeParams(ps.paramWriter, frag.queryParams)
        val count    = ps.executeUpdate()
        val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
        con.logger.onSuccess(SqlLogger.SuccessEvent(sqlStr, frag.queryParams, duration, count))
        count
      } finally ps.close()
    } catch {
      case e: Throwable =>
        val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
        con.logger.onError(SqlLogger.ErrorEvent(sqlStr, frag.queryParams, duration, e))
        throw e
    }
  }

  private[sql] def writeParams(writer: DbParamWriter, params: IndexedSeq[DbValue]): Unit = {
    var i = 0
    while (i < params.length) {
      val idx = i + 1
      params(i) match {
        case DbValue.DbNull             => writer.setNull(idx, 0)
        case DbValue.DbInt(v)           => writer.setInt(idx, v)
        case DbValue.DbLong(v)          => writer.setLong(idx, v)
        case DbValue.DbDouble(v)        => writer.setDouble(idx, v)
        case DbValue.DbFloat(v)         => writer.setFloat(idx, v)
        case DbValue.DbBoolean(v)       => writer.setBoolean(idx, v)
        case DbValue.DbString(v)        => writer.setString(idx, v)
        case DbValue.DbBigDecimal(v)    => writer.setBigDecimal(idx, v.bigDecimal)
        case DbValue.DbBytes(v)         => writer.setBytes(idx, v)
        case DbValue.DbShort(v)         => writer.setShort(idx, v)
        case DbValue.DbByte(v)          => writer.setByte(idx, v)
        case DbValue.DbChar(v)          => writer.setString(idx, v.toString)
        case DbValue.DbLocalDate(v)     => writer.setLocalDate(idx, v)
        case DbValue.DbLocalDateTime(v) => writer.setLocalDateTime(idx, v)
        case DbValue.DbLocalTime(v)     => writer.setLocalTime(idx, v)
        case DbValue.DbInstant(v)       => writer.setInstant(idx, v)
        case DbValue.DbDuration(v)      => writer.setDuration(idx, v)
        case DbValue.DbUUID(v)          => writer.setUUID(idx, v)
      }
      i += 1
    }
  }
}
