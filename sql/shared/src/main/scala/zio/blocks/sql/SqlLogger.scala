package zio.blocks.sql

import java.time.Duration

trait SqlLogger {
  def onSuccess(event: SqlLogger.SuccessEvent): Unit
  def onError(event: SqlLogger.ErrorEvent): Unit
}

object SqlLogger {

  final case class SuccessEvent(
    sql: String,
    params: IndexedSeq[DbValue],
    duration: Duration,
    rowCount: Int
  )

  final case class ErrorEvent(
    sql: String,
    params: IndexedSeq[DbValue],
    duration: Duration,
    error: Throwable
  )

  val noop: SqlLogger = new SqlLogger {
    def onSuccess(event: SuccessEvent): Unit = ()
    def onError(event: ErrorEvent): Unit     = ()
  }
}
