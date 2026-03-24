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

/**
 * Internal storage for scoped log annotations. Annotations are key-value pairs
 * that automatically attach to all log records within a `log.annotated` block.
 *
 * Uses ThreadLocal for thread-safe, fiber-unaware scoping on JVM. Will be
 * replaced with a platform-specific implementation when cross-compiling for JS.
 */
private[otel] object LogAnnotations {
  private val storage: ThreadLocal[Map[String, String]] = new ThreadLocal[Map[String, String]] {
    override def initialValue(): Map[String, String] = Map.empty
  }

  def get(): Map[String, String] = storage.get()

  def scoped[A](annotations: Map[String, String])(f: => A): A = {
    val prev = storage.get()
    storage.set(prev ++ annotations)
    try f
    finally storage.set(prev)
  }
}
