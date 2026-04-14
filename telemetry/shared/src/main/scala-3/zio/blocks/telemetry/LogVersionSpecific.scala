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

package zio.blocks.telemetry

private[telemetry] trait LogVersionSpecific { self: log.type =>

  inline def trace(inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logImpl('self, 'message, 'enrichments, '{ Severity.Trace }) }

  inline def debug(inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logImpl('self, 'message, 'enrichments, '{ Severity.Debug }) }

  inline def info(inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logImpl('self, 'message, 'enrichments, '{ Severity.Info }) }

  inline def warn(inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logImpl('self, 'message, 'enrichments, '{ Severity.Warn }) }

  inline def error(inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logImpl('self, 'message, 'enrichments, '{ Severity.Error }) }

  inline def fatal(inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logImpl('self, 'message, 'enrichments, '{ Severity.Fatal }) }

  // Rate-limited: log every N invocations per call site

  inline def traceEvery(every: Int, inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logEveryImpl('self, 'every, 'message, 'enrichments, '{ Severity.Trace }) }

  inline def debugEvery(every: Int, inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logEveryImpl('self, 'every, 'message, 'enrichments, '{ Severity.Debug }) }

  inline def infoEvery(every: Int, inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logEveryImpl('self, 'every, 'message, 'enrichments, '{ Severity.Info }) }

  inline def warnEvery(every: Int, inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logEveryImpl('self, 'every, 'message, 'enrichments, '{ Severity.Warn }) }

  inline def errorEvery(every: Int, inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logEveryImpl('self, 'every, 'message, 'enrichments, '{ Severity.Error }) }

  inline def fatalEvery(every: Int, inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logEveryImpl('self, 'every, 'message, 'enrichments, '{ Severity.Fatal }) }

  // Rate-limited: log at most once per interval (millis) per call site

  inline def traceAtMost(intervalMillis: Long, inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logAtMostImpl('self, 'intervalMillis, 'message, 'enrichments, '{ Severity.Trace }) }

  inline def debugAtMost(intervalMillis: Long, inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logAtMostImpl('self, 'intervalMillis, 'message, 'enrichments, '{ Severity.Debug }) }

  inline def infoAtMost(intervalMillis: Long, inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logAtMostImpl('self, 'intervalMillis, 'message, 'enrichments, '{ Severity.Info }) }

  inline def warnAtMost(intervalMillis: Long, inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logAtMostImpl('self, 'intervalMillis, 'message, 'enrichments, '{ Severity.Warn }) }

  inline def errorAtMost(intervalMillis: Long, inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logAtMostImpl('self, 'intervalMillis, 'message, 'enrichments, '{ Severity.Error }) }

  inline def fatalAtMost(intervalMillis: Long, inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logAtMostImpl('self, 'intervalMillis, 'message, 'enrichments, '{ Severity.Fatal }) }
}
