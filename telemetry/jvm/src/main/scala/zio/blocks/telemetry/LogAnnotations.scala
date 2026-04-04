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

/**
 * Internal storage for scoped log annotations. Annotations are key-value pairs
 * that automatically attach to all log records within a `log.annotated` block.
 *
 * Uses ScopedValue for scoped bindings (virtual-thread-friendly) with
 * ThreadLocal fallback for reads outside a scoped block.
 */
private[telemetry] object LogAnnotations {
  @volatile private var everUsed: Boolean                   = false
  private val scopedVal: ScopedValue[Map[String, String]]   = ScopedValue.newInstance()
  private val threadLocal: ThreadLocal[Map[String, String]] = new ThreadLocal[Map[String, String]] {
    override def initialValue(): Map[String, String] = Map.empty
  }

  def get(): Map[String, String] =
    if (!everUsed) Map.empty
    else if (scopedVal.isBound) scopedVal.get()
    else threadLocal.get()

  def scoped[A](annotations: Map[String, String])(f: => A): A = {
    everUsed = true
    val merged            = get() ++ annotations
    var result: A         = null.asInstanceOf[A]
    var thrown: Throwable = null
    ScopedValue
      .where(scopedVal, merged)
      .run(() =>
        try result = f
        catch { case t: Throwable => thrown = t }
      )
    if (thrown != null) throw thrown
    result
  }
}
