package example.minimal

import golem.runtime.annotations.agentImplementation

import scala.concurrent.Future

@agentImplementation()
final class StatefulCounterImpl(private val state: CounterState) extends StatefulCounter {
  private var count: Int = state.initialCount

  override def increment(): Future[Int] =
    Future.successful {
      count += 1
      count
    }

  override def current(): Future[Int] =
    Future.successful(count)
}

@agentImplementation()
final class StatefulCallerImpl(private val state: CounterState) extends StatefulCaller {
  private val counter = StatefulCounter.get(state)

  override def remoteIncrement(): Future[Int] =
    counter.increment()
}
