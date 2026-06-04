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

package zio.blocks.async.internal

import zio.blocks.async.Waker

/**
 * Scala.js platform helpers for the async runtime.
 *
 * JavaScript is single-threaded and has no blocking primitive, so the parker
 * cannot wait. Instead, each suspension gets one chance to complete
 * synchronously inside `poll`: if the underlying callback invokes
 * `waker.wake()` before `poll` returns, [[park]] is a no-op and the runtime
 * loops; otherwise [[park]] throws. Code that observes truly asynchronous
 * results on Scala.js must consume the [[zio.blocks.async.Pollable]] surface
 * directly rather than calling `await`.
 */
private[async] object PlatformAsync {

  def newParker(): Parker = new JsParker

  private final class JsParker extends Parker {
    private var ready = false
    val waker: Waker  = new Waker {
      def wake(): Unit = ready = true
    }

    def reset(): Unit = ready = false

    def park(): Unit =
      if (!ready)
        throw new IllegalStateException(
          "Async.block: suspension did not complete synchronously and JavaScript " +
            "cannot block. Drive the Pollable from a non-blocking entry point instead."
        )
  }
}
