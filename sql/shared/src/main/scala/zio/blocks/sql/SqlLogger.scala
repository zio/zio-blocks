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
