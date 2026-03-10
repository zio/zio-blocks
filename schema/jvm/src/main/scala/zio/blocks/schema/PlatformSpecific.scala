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

package zio.blocks.schema

import java.net.IDN
import scala.util.control.NonFatal

/**
 * JVM-specific platform implementation.
 */
trait PlatformSpecific extends Platform {
  override val isJVM: Boolean = true
  override val isJS: Boolean  = false
  override val name: String   = "JVM"

  def idnToAscii(idn: String): Option[String] =
    try new Some(IDN.toASCII(idn, IDN.USE_STD3_ASCII_RULES))
    catch {
      case err if NonFatal(err) => None
    }
}
