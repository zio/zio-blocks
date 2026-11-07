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

/**
 * The [[Platform]] of this build's target, the JVM or Scala.js, which the
 * `Platform` object extends.
 */
trait PlatformSpecific extends Platform {
  override val isJVM: Boolean = false
  override val isJS: Boolean  = true
  override val name: String   = "JS"

  /**
   * Converts `idn` to its ASCII form for the `hostname` format check. On the
   * JVM it calls `java.net.IDN.toASCII` with the STD3 ASCII rules and returns
   * `None` when that rejects the name; on Scala.js there is no IDN translation,
   * and it returns `Some(idn)`.
   */
  def idnToAscii(idn: String): Option[String] = new Some(idn) // no IDN translation
}
