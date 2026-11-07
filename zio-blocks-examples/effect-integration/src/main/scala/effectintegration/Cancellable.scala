package effectintegration

import zio.blocks.async.{Async, Completer, Pollable}

// The `cancellable` helper from "How to Wrap Callback and Future APIs": an `Async` whose
// cancellation calls the function that `register` returns.
object Cancellable {
  def cancellable[A](register: Completer[A] => (() => Unit)): Async[A] =
    new Pollable[A] {
      private val result     = new Completer[A]
      private val cancelCall = register(result)

      def poll(onComplete: Runnable): Async[A] = {
        val polled = result.poll(onComplete)
        // While pending, answer with this Pollable rather than the Completer, so
        // the driver keeps waiting on us and our cancel() is the one it calls.
        if (polled.asInstanceOf[AnyRef] eq result) this else polled
      }

      override def cancel(): Unit = cancelCall()
    }
}
