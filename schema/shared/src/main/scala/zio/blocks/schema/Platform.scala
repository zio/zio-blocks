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
 * Platform detection for zio-blocks-schema.
 *
 * This trait provides compile-time information about the current runtime
 * platform. Some features (like structural types) require reflection APIs that
 * are only available on the JVM.
 *
 * Platform-specific implementations are provided in:
 *   - jvm/src/main/scala/zio/blocks/schema/PlatformSpecific.scala
 *   - js/src/main/scala/zio/blocks/schema/PlatformSpecific.scala
 */
trait Platform {

  /** Whether this is the JVM platform */
  def isJVM: Boolean

  /** Whether this is the JavaScript platform */
  def isJS: Boolean

  /** Human-readable name of the platform */
  def name: String

  /** Translates Internationalized Domain Name to ASCII representation */
  def idnToAscii(idn: String): Option[String]

  /** Whether reflection APIs are available on this platform */
  final def supportsReflection: Boolean = isJVM
}

object Platform extends PlatformSpecific
