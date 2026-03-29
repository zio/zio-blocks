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

import java.util.concurrent.atomic.AtomicReference

/**
 * Global tracer that works without any setup ceremony. Stores spans in an
 * in-memory ring buffer by default. Install a real TracerProvider later via
 * `install` to wire production exporters.
 */
object GlobalTracer {
  private val ref: AtomicReference[TracerProvider] = new AtomicReference[TracerProvider](null)
  private val spanStore: InMemorySpanProcessor     = new InMemorySpanProcessor()

  private lazy val defaultProvider: TracerProvider =
    TracerProvider.builder
      .addSpanProcessor(spanStore)
      .build()

  /** Returns a Tracer for the given instrumentation scope name. */
  def get(name: String = "default"): Tracer = {
    val provider = ref.get()
    if (provider != null) provider.get(name)
    else defaultProvider.get(name)
  }

  /** Replaces the default TracerProvider with a user-configured one. */
  def install(provider: TracerProvider): Unit = ref.set(provider)

  /** Reverts to the default in-memory TracerProvider. */
  def uninstall(): Unit = ref.set(null)

  /** Returns spans collected by the default in-memory processor. */
  def collectedSpans: List[SpanData] = spanStore.collectedSpans

  /** Clears all collected spans from the in-memory processor. */
  def clearSpans(): Unit = spanStore.clear()
}
