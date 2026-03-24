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

private[otel] trait LogVersionSpecific { self: log.type =>

  inline def trace(inline message: String, inline enrichments: Any*): Unit =
    inline if (GlobalLogState.COMPILE_MIN_LEVEL <= 1) // Severity.Trace.number = 1
      traceImpl(message, enrichments*)

  private inline def traceImpl(inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logImpl('self, 'message, 'enrichments, '{ Severity.Trace }) }

  inline def debug(inline message: String, inline enrichments: Any*): Unit =
    inline if (GlobalLogState.COMPILE_MIN_LEVEL <= 5) // Severity.Debug.number = 5
      debugImpl(message, enrichments*)

  private inline def debugImpl(inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logImpl('self, 'message, 'enrichments, '{ Severity.Debug }) }

  inline def info(inline message: String, inline enrichments: Any*): Unit =
    inline if (GlobalLogState.COMPILE_MIN_LEVEL <= 9) // Severity.Info.number = 9
      infoImpl(message, enrichments*)

  private inline def infoImpl(inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logImpl('self, 'message, 'enrichments, '{ Severity.Info }) }

  inline def warn(inline message: String, inline enrichments: Any*): Unit =
    inline if (GlobalLogState.COMPILE_MIN_LEVEL <= 13) // Severity.Warn.number = 13
      warnImpl(message, enrichments*)

  private inline def warnImpl(inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logImpl('self, 'message, 'enrichments, '{ Severity.Warn }) }

  inline def error(inline message: String, inline enrichments: Any*): Unit =
    inline if (GlobalLogState.COMPILE_MIN_LEVEL <= 17) // Severity.Error.number = 17
      errorImpl(message, enrichments*)

  private inline def errorImpl(inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logImpl('self, 'message, 'enrichments, '{ Severity.Error }) }

  inline def fatal(inline message: String, inline enrichments: Any*): Unit =
    inline if (GlobalLogState.COMPILE_MIN_LEVEL <= 21) // Severity.Fatal.number = 21
      fatalImpl(message, enrichments*)

  private inline def fatalImpl(inline message: String, inline enrichments: Any*): Unit =
    ${ LogMacros.logImpl('self, 'message, 'enrichments, '{ Severity.Fatal }) }
}
