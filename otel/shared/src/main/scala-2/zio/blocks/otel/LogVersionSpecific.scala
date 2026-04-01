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

package zio.blocks.otel

import scala.language.experimental.macros

private[otel] trait LogVersionSpecific { self: log.type =>

  def trace(message: String, enrichments: Any*): Unit = macro LogMacros.traceImpl

  def debug(message: String, enrichments: Any*): Unit = macro LogMacros.debugImpl

  def info(message: String, enrichments: Any*): Unit = macro LogMacros.infoImpl

  def warn(message: String, enrichments: Any*): Unit = macro LogMacros.warnImpl

  def error(message: String, enrichments: Any*): Unit = macro LogMacros.errorImpl

  def fatal(message: String, enrichments: Any*): Unit = macro LogMacros.fatalImpl

  // Rate-limited: log every N invocations per call site

  def traceEvery(every: Int, message: String, enrichments: Any*): Unit = macro LogMacros.traceEveryImpl

  def debugEvery(every: Int, message: String, enrichments: Any*): Unit = macro LogMacros.debugEveryImpl

  def infoEvery(every: Int, message: String, enrichments: Any*): Unit = macro LogMacros.infoEveryImpl

  def warnEvery(every: Int, message: String, enrichments: Any*): Unit = macro LogMacros.warnEveryImpl

  def errorEvery(every: Int, message: String, enrichments: Any*): Unit = macro LogMacros.errorEveryImpl

  def fatalEvery(every: Int, message: String, enrichments: Any*): Unit = macro LogMacros.fatalEveryImpl

  // Rate-limited: log at most once per interval (millis) per call site

  def traceAtMost(intervalMillis: Long, message: String, enrichments: Any*): Unit = macro LogMacros.traceAtMostImpl

  def debugAtMost(intervalMillis: Long, message: String, enrichments: Any*): Unit = macro LogMacros.debugAtMostImpl

  def infoAtMost(intervalMillis: Long, message: String, enrichments: Any*): Unit = macro LogMacros.infoAtMostImpl

  def warnAtMost(intervalMillis: Long, message: String, enrichments: Any*): Unit = macro LogMacros.warnAtMostImpl

  def errorAtMost(intervalMillis: Long, message: String, enrichments: Any*): Unit = macro LogMacros.errorAtMostImpl

  def fatalAtMost(intervalMillis: Long, message: String, enrichments: Any*): Unit = macro LogMacros.fatalAtMostImpl
}
