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

import scala.scalajs.js.timers.setTimeout

/**
 * Scala.js platform helpers for the async runtime.
 *
 * JavaScript is single-threaded and has no blocking primitive, so the parker
 * cannot wait. Instead, each suspension gets one chance to complete
 * synchronously inside `poll`: if the underlying callback invokes `waker.run()`
 * before `poll` returns, [[park]] is a no-op and the runtime loops; otherwise
 * [[park]] throws. Code that observes truly asynchronous results on Scala.js
 * must consume the [[zio.blocks.async.Pollable]] surface directly rather than
 * calling `await`.
 */
private[async] object PlatformAsync {

  final val ReadyResumptionLimit: Int = 1024

  private var executionOwner: AnyRef        = null
  private var cancellationBatch: AnyRef     = null
  private var continuationScratch1: AnyRef  = null
  private var continuationScratch2: AnyRef  = null
  private var bracketCancellationDepth: Int = 0
  private var bracketPollDepth: Int         = 0

  def currentExecutionOwner: AnyRef    = executionOwner
  def currentCancellationBatch: AnyRef = cancellationBatch

  def enterBracketCancellation(): Int = {
    bracketCancellationDepth += 1
    bracketCancellationDepth
  }

  def enterBracketPoll(): Int = {
    bracketPollDepth += 1
    bracketPollDepth
  }

  def exitBracketCancellation(): Unit = bracketCancellationDepth -= 1

  def exitBracketPoll(): Unit = bracketPollDepth -= 1

  def takeContinuationScratch(): AnyRef = {
    val first = continuationScratch1
    if (first ne null) {
      continuationScratch1 = null
      first
    } else {
      val second = continuationScratch2
      continuationScratch2 = null
      second
    }
  }

  def releaseContinuationScratch(scratch: AnyRef): Unit =
    if (continuationScratch1 eq null) continuationScratch1 = scratch
    else if (continuationScratch2 eq null) continuationScratch2 = scratch

  def withCancellationBatch[A](batch: AnyRef)(body: => A): A = {
    val previous = cancellationBatch
    cancellationBatch = batch
    try body
    finally cancellationBatch = previous
  }

  def withExecutionOwner[A](owner: AnyRef)(body: => A): A = {
    val previous = executionOwner
    executionOwner = owner
    try body
    finally executionOwner = previous
  }

  def schedule(runnable: Runnable, forceMacrotask: Boolean): Unit = {
    val owner = currentExecutionOwner
    val batch = currentCancellationBatch
    val owned =
      if ((owner eq null) && (batch eq null)) runnable
      else
        new Runnable {
          def run(): Unit = withExecutionOwner(owner)(withCancellationBatch(batch)(runnable.run()))
        }
    if (forceMacrotask) {
      setTimeout(0.0)(owned.run())
      ()
    } else scala.scalajs.concurrent.JSExecutionContext.queue.execute(owned)
  }

  def newParker(): Parker = new JsParker

  private final class JsParker extends Parker {
    private var ready        = false
    val onComplete: Runnable = new Runnable { def run(): Unit = ready = true }

    def reset(): Unit = ready = false

    def park(): Unit =
      if (!ready)
        throw new IllegalStateException(
          "Async.block: suspension did not complete synchronously and JavaScript " +
            "cannot block. Drive the Pollable from a non-blocking entry point instead."
        )
  }
}
