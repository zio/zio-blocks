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
}
