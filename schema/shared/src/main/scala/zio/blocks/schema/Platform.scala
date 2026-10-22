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
 * Facts about the platform that `zio-blocks-schema` runs on, the JVM or
 * Scala.js, and the operations that differ between them. The [[Platform]]
 * object is the instance for the current platform; the schema macros read it to
 * reject structural types where reflection is missing.
 */
trait Platform {

  /** Whether this is the JVM. */
  def isJVM: Boolean

  /** Whether this is Scala.js. */
  def isJS: Boolean

  /** The name of the platform: `JVM` or `JS`. */
  def name: String

  /**
   * Converts the internationalized domain name `idn` to its ASCII form, as JSON
   * Schema's `hostname` format check needs. On the JVM it uses
   * `java.net.IDN.toASCII` with the STD3 ASCII rules and returns `None` when
   * that rejects the name; on Scala.js it returns the name unchanged.
   */
  def idnToAscii(idn: String): Option[String]

  /**
   * Whether reflection is available, which structural types need: deriving
   * their schemas and bindings, `ToStructural`, and `Into` to or from a
   * structural type. It is true on the JVM only.
   */
  final def supportsReflection: Boolean = isJVM
}

/** The [[Platform]] that this build runs on. */
object Platform extends PlatformSpecific
